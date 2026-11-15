package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    /**
     * 扫描运行状态
     *
     * @Volatile 确保 stop() 在 UI 线程调用后，扫描回调能感知状态变更。
     * 只有 startScan() 真的被系统接受之后才置为 true —— 提前置 true 会让一次彻底
     * 失败的启动在界面上表现成"正在扫描但一台设备都没有"，这正是最难排查的形态。
     */
    @Volatile var isRunning = false
        private set

    /**
     * 异步失败原因
     *
     * onScanFailed 在 Binder 线程回调，不能直接改 UI 状态，用 StateFlow 交给
     * ViewModel 轮询取用。为 null 表示当前没有失败。
     */
    private val _failure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = _failure

    /** 已发现设备表，Key 为 MAC 地址，ConcurrentHashMap 保证多线程读写安全 */
    val devices = ConcurrentHashMap<String, BleDevice>()

    /** 扫描参数：界面要看实时 RSSI 波动，必须用 LOW_LATENCY；默认的 LOW_POWER 上报太稀 */
    private val scanSettings: ScanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

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

        /**
         * BLE 扫描失败回调
         *
         * errorCode 是唯一能定位原因的线索，必须翻译成可读文本送到界面上；
         * 同时把 isRunning 落回 false，否则界面会一直停在"运行中"。
         */
        override fun onScanFailed(errorCode: Int) {
            isRunning = false
            _failure.value = describeScanFailure(errorCode)
        }
    }

    /**
     * 启动 BLE 扫描
     *
     * 启动前依次校验：适配器存在 → 蓝牙已开启 → 扫描权限已授予 → 系统给出扫描器。
     * 任何一步不通过都会返回可读原因，而**不会**把 isRunning 置成 true。
     *
     * 扫描器每次都在这里重新获取，不做构造期缓存：蓝牙关闭时
     * BluetoothAdapter.bluetoothLeScanner 会返回 null，若在构造期把它存进字段，
     * 这个 null 就会跟随整个进程生命周期 —— 用户之后把蓝牙打开也不会恢复。
     *
     * @return null 表示已成功交给系统扫描；非 null 为失败原因（同时写入 [failure]）
     */
    fun start(): String? {
        if (isRunning) return null
        devices.clear()
        _failure.value = null

        // 走 BluetoothManager 取适配器；BluetoothAdapter.getDefaultAdapter() 已弃用
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return fail("此设备没有蓝牙适配器")
        if (!adapter.isEnabled) return fail("蓝牙未开启，请先在系统设置中打开蓝牙")
        if (!hasScanPermission()) return fail("缺少蓝牙扫描权限，请在系统设置中授予「附近的设备」权限")

        val leScanner = adapter.bluetoothLeScanner
            ?: return fail("系统未提供 BLE 扫描器，请关闭蓝牙后重新打开再试")

        return try {
            leScanner.startScan(null, scanSettings, scanCallback)
            isRunning = true
            null
        } catch (e: SecurityException) {
            // SecurityException 的 message 经常是 null，直接透传会让界面显示一条空错误
            fail("扫描权限被系统拒绝：${e.message ?: "请在系统设置中授予「附近的设备」权限"}")
        }
    }

    /**
     * 停止 BLE 扫描
     *
     * 调用 BluetoothLeScanner.stopScan() 停止硬件扫描。
     * 设备列表保留在内存中，可继续查看已发现的设备。
     */
    fun stop() {
        isRunning = false
        try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
    }

    // ── 内部辅助 ────────────────────────────────────────────────────────────

    /** 记录失败原因并返回它，让调用处可以 `return fail(...)` 一行收尾 */
    private fun fail(reason: String): String {
        isRunning = false
        _failure.value = reason
        return reason
    }

    /**
     * 扫描权限是否已授予
     *
     * Android 12 起蓝牙扫描有独立的 BLUETOOTH_SCAN；12 以下没有这条权限，
     * 由位置权限代偿（manifest 里的 neverForLocation 就是配套声明）。
     */
    private fun hasScanPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 把 ScanCallback 的错误码翻译成可读原因
     *
     * 这些错误码原先被整条丢弃，导致扫描失败时界面一片空白、无从排查。
     * 其中 SCAN_FAILED_SCANNING_TOO_FREQUENTLY 在 Android 8.1+ 上很常见：
     * 30 秒内反复启停扫描超过 5 次就会被系统拒绝。
     */
    private fun describeScanFailure(errorCode: Int): String = when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "扫描已在进行中，请先停止再重试"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "扫描回调注册失败，请关闭蓝牙再重新打开"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "系统内部错误，请关闭蓝牙再重新打开"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "此设备不支持 BLE 扫描"
        ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "蓝牙硬件资源不足，请关闭其他扫描类应用后重试"
        ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "扫描过于频繁，请等待约 30 秒后重试"
        else -> "扫描失败（系统错误码 $errorCode）"
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
