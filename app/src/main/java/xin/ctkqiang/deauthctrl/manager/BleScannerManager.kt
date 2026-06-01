package xin.ctkqiang.deauthctrl.manager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE 设备数据类
 *
 * 封装一台低功耗蓝牙设备的完整扫描信息，包括实时 RSSI 历史记录用于绘制信号图谱。
 *
 * @property address 蓝牙 MAC 地址（如 "D0:22:12:AB:CD:EF"）
 * @property name 设备名称（来自广播数据或已配对名称），未知设备显示"(未知)"
 * @property rssi 最新信号强度（dBm），范围通常在 -100（极弱）到 -20（极强）之间
 * @property rssiHistory RSSI 历史值列表（最近 50 次扫描结果），用于绘制实时信号走势图
 * @property manufacturerData 厂商自定义数据，Key 为厂商 ID（16 位），Value 为十六进制字符串
 * @property services 广播的 GATT 服务 UUID 列表（截取最后 8 位十六进制）
 * @property txPower 发射功率（dBm），来自广播数据，可能为 Int.MIN_VALUE（未广播）
 * @property deviceType 设备类型推断结果（基于服务 UUID 或设备名称匹配），空字符串表示未知
 * @property lastSeen 最后一次发现该设备的时间戳（System.currentTimeMillis()）
 */
data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val rssiHistory: MutableList<Int> = mutableListOf(),
    val manufacturerData: Map<Int, String> = emptyMap(),
    val services: List<String> = emptyList(),
    val txPower: Int = Int.MIN_VALUE,
    val deviceType: String = "",
    val lastSeen: Long = System.currentTimeMillis(),
)

/**
 * BLE 低功耗蓝牙嗅探管理器
 *
 * 使用 Android 原生 BluetoothLeScanner API 进行被动 BLE 广播嗅探。
 * 类似于 Nordic nRF Connect 的扫描功能，在终端风格的 UI 中实时展示设备信息与 RSSI 图谱。
 *
 * ## 核心功能
 * - 被动扫描（不发起连接，仅监听广播包）
 * - 实时记录每个设备的 RSSI 历史（最多保留 50 个数据点）
 * - 解析广播数据：服务 UUID、厂商自定义数据、发射功率
 * - 基于广播内容和设备名称自动推断设备类型（Apple、Samsung、Xiaomi、Health 设备等）
 * - 线程安全的设备列表管理（ConcurrentHashMap）
 * - 200ms 轮询间隔将设备列表推送到 UI 层
 *
 * ## 权限要求
 * - Android 12+ (API 31+): BLUETOOTH_SCAN 权限
 * - Android 11- (API 30-): ACCESS_FINE_LOCATION 权限 + GPS 需开启
 *
 * ## Android BLE 扫描背景
 * Android BLE 扫描基于 HCI LE Advertising Report 事件。
 * 一个设备可能在同一扫描周期内被多次报告（每次广播事件），
 * 每次报告的 RSSI 可能因距离、障碍物、天线方向等因素波动 5-15 dBm。
 */
class BleScannerManager(private val context: Context) {

    /** 系统蓝牙适配器，可能为 null（设备不支持蓝牙） */
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    /** BLE 扫描器实例，从 BluetoothAdapter 获取 */
    private var scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner

    /**
     * 扫描运行状态
     *
     * @Volatile 确保 stop() 在 UI 线程调用后，扫描回调能感知状态变更
     */
    @Volatile var isRunning = false
        private set

    /** 已发现设备表，Key 为 MAC 地址，ConcurrentHashMap 保证多线程读写安全 */
    val devices = ConcurrentHashMap<String, BleDevice>()

    /**
     * BLE 扫描回调
     *
     * 每次收到广播包时触发。处理逻辑：
     * 1. 提取设备名（优先使用已配对名称，其次广播名）
     * 2. 解析 GATT 服务 UUID 列表
     * 3. 解析厂商自定义数据（SparseArray → Map<厂商ID, 十六进制>）
     * 4. 提取发射功率（txPower）
     * 5. 基于服务 UUID 和设备名推断设备类型
     * 6. 若设备已存在：追加 RSSI 到历史记录，更新其他属性
     *    若设备不存在：创建新条目并初始化 RSSI 历史
     *
     * onScanFailed: 扫描失败时自动设置 isRunning = false
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            val record = result.scanRecord
            val rssi = result.rssi
            val addr = dev.address ?: return
            val name = dev.name ?: record?.deviceName ?: "(未知)"
            val services = record?.serviceUuids?.map { it.uuid.toString().takeLast(8) } ?: emptyList()
            val mfrData = record?.manufacturerSpecificData
            val mfr = mutableMapOf<Int, String>()
            if (mfrData != null) for (i in 0 until mfrData.size()) mfr[mfrData.keyAt(i)] = bytesToHex(mfrData.valueAt(i))
            val txPower = record?.txPowerLevel ?: Int.MIN_VALUE
            val type = when {
                services.any { it.contains("1809") } -> "Health Thermometer"
                services.any { it.contains("180A") } -> "Device Info"
                services.any { it.contains("180F") } -> "Battery"
                services.any { it.contains("1810") } -> "Blood Pressure"
                name.contains("AirPods") || name.contains("AirTag") -> "Apple"
                name.contains("Galaxy") || name.contains("Buds") -> "Samsung"
                name.contains("Mi ") -> "Xiaomi"
                else -> ""
            }

            val existing = devices[addr]
            if (existing != null) {
                existing.rssiHistory.add(rssi)
                if (existing.rssiHistory.size > 50) existing.rssiHistory.removeAt(0)
                devices[addr] = existing.copy(
                    rssi = rssi, name = if (name != "(未知)") name else existing.name,
                    services = services, manufacturerData = mfr,
                    txPower = txPower, deviceType = type,
                    lastSeen = System.currentTimeMillis(),
                )
            } else {
                val history = mutableListOf(rssi)
                devices[addr] = BleDevice(
                    address = addr, name = name, rssi = rssi,
                    rssiHistory = history, services = services,
                    manufacturerData = mfr, txPower = txPower,
                    deviceType = type,
                )
            }
        }

        /** BLE 扫描失败回调（权限不足、硬件错误、扫描器忙等） */
        override fun onScanFailed(errorCode: Int) { isRunning = false }
    }

    /**
     * 启动 BLE 扫描
     *
     * 清空已有设备列表，调用 BluetoothLeScanner.startScan() 开始监听广播包。
     * 在非 root 设备上，Android 系统会将扫描结果节流（throttle）以节省电量。
     */
    fun start() {
        devices.clear()
        isRunning = true
        scanner?.startScan(scanCallback)
    }

    /**
     * 停止 BLE 扫描
     *
     * 调用 BluetoothLeScanner.stopScan() 停止硬件扫描。
     * 设备列表保留在内存中，可继续查看已发现的设备。
     */
    fun stop() {
        isRunning = false
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
    }

    /**
     * 将字节数组转换为十六进制大写字符串
     *
     * 用于显示厂商自定义数据（如 "A4FF02C0"）。
     *
     * @param bytes 原始字节数组
     * @return 十六进制大写字符串，无分隔符
     */
    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }
}
