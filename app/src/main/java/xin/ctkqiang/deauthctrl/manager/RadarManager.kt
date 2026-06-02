package xin.ctkqiang.deauthctrl.manager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 雷达扫描目标数据类
 *
 * 表示 PPI 雷达视图中一个已探测到的无线设备（WiFi AP 或蓝牙设备）。
 *
 * @property id 唯一标识符（WiFi = BSSID / 蓝牙 = MAC 地址）
 * @property name 设备显示名（WiFi = SSID / 蓝牙 = 设备名或 "(BT Device)"）
 * @property type 设备类型："wifi" 或 "bt"
 * @property rssi 接收信号强度指示（dBm），范围约 -100（极弱）至 -20（极强）
 * @property distanceM 基于 RSSI 自由空间路径损耗公式估算的距离（米），范围 0.3-100m
 * @property angleDeg 伪方位角（0-360°），基于 MAC 地址哈希值生成，同一设备多次扫描角度保持稳定
 * @property channel WiFi 信道号（如 "CH6"）或蓝牙频段标签（如 "2.4GHz"）
 * @property vendor WiFi 加密方式缩写（如 "WPA2-PSK"）或空字符串
 */
data class RadarTarget(
    val id: String,
    val name: String,
    val type: String,
    val rssi: Int,
    val distanceM: Float,
    val angleDeg: Float,
    val channel: String = "",
    val vendor: String = "",
)

/**
 * WiFi/蓝牙双模设备雷达扫描管理器
 *
 * 同时扫描附近 WiFi 接入点和蓝牙设备，通过 RSSI 信号强度估算距离，
 * 使用伪随机角度模拟方位角（无定向天线硬件时），输出 PPI 雷达视图所需数据。
 *
 * ## RSSI → 距离估算（对数距离路径损耗模型）
 * ```
 * distance = 10 ^ ((txPower - rssi) / (10 * n))
 * ```
 * 参数：
 * - txPower = -40 dBm (WiFi) / -59 dBm (BLE 1m 参考发射功率)
 * - n = 2.5（室内环境路径损耗指数：自由空间=2.0，办公室=3.0，工厂=2.0）
 * - 输出钳制在 0.3m ~ 100m
 *
 * ## 伪方位角生成
 * 无定向天线硬件时，取 MAC 地址后 6 位十六进制 → hashCode & 0x7FFFFFFF → % 360，
 * 确保同一设备的方位角在多次扫描间保持稳定（用于雷达视图定位，不影响实际方位）。
 *
 * ## 扫描机制
 * - **WiFi**: WifiManager.startScan() + SCAN_RESULTS_AVAILABLE_ACTION 广播接收
 * - **蓝牙**: BluetoothAdapter.startDiscovery() + ACTION_FOUND 广播接收
 * - **扫描间隔**: 每 3 秒一个完整周期（WiFi 扫描 → 等 2s → 蓝牙发现 → 等 1s）
 *
 * ## 硬件要求
 * - WiFi 扫描：需要位置服务开启（Android 8+） + ACCESS_FINE_LOCATION 权限
 * - 蓝牙发现：需要 BLUETOOTH_SCAN 权限（Android 12+）或 ACCESS_FINE_LOCATION（旧版）
 * - 若权限不足，对应扫描静默失败，不影响另一模式的扫描
 *
 * ## 线程模型
 * - 主线程: BroadcastReceiver.onReceive() 回调
 * - 后台协程 (Dispatchers.IO): 定时触发扫描的 while 循环
 * - ConcurrentHashMap: 线程安全的设备表（BroadcastReceiver + UI 轮询并发访问）
 */
class RadarManager(private val context: Context) {

    /** WiFi 管理器（系统服务） */
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /** 蓝牙管理器（系统服务，Android 4.3+），用于获取 BluetoothAdapter */
    private val btManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    /** 蓝牙适配器，null 表示设备不支持蓝牙 */
    private val btAdapter: BluetoothAdapter? = btManager?.adapter

    /**
     * 已探测设备表
     *
     * Key = MAC/BSSID，ConcurrentHashMap 保证 BroadcastReceiver（主线程）与 UI 轮询（IO 线程）并发安全。
     */
    val targets = ConcurrentHashMap<String, RadarTarget>()

    /** 雷达运行状态，@Volatile 确保跨线程可见 */
    @Volatile var isRunning = false; private set

    /** 后台扫描循环协程 Job */
    private var scanJob: Job? = null

    /**
     * WiFi 扫描结果广播接收器
     *
     * 接收 WifiManager.SCAN_RESULTS_AVAILABLE_ACTION 广播，
     * 直接遍历所有 scanResults 并更新 targets（不检查 EXTRA_RESULTS_UPDATED，
     * 因为该标志在部分设备/ROM 上不可靠）。
     * 每次收到广播时全量覆写对应 BSSID 的条目。
     */
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            try {
                val results: List<ScanResult> = wifiManager.scanResults ?: return
                results.forEach { r ->
                    val dist = rssiToDistance(r.level, -40f)
                    targets[r.BSSID] = RadarTarget(
                        id = r.BSSID, name = r.SSID.ifBlank { "<HIDDEN>" }, type = "wifi",
                        rssi = r.level, distanceM = dist, angleDeg = pseudoAngle(r.BSSID),
                        channel = "CH${channelFromFreq(r.frequency)}", vendor = r.capabilities.take(10),
                    )
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * 蓝牙设备发现广播接收器
     *
     * 接收 BluetoothDevice.ACTION_FOUND 广播，
     * 提取 BluetoothDevice 对象、信号强度（EXTRA_RSSI）和 MAC 地址。
     * 使用非弃用的 getParcelableExtra(name, Class) 重载（替代已弃用的泛型版本）。
     */
    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            try {
                val device: BluetoothDevice? = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                val rssi = intent?.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)?.toInt() ?: return
                if (rssi == Short.MIN_VALUE.toInt()) return
                val addr = device?.address ?: return
                val dist = rssiToDistance(rssi, -59f)
                targets[addr] = RadarTarget(
                    id = addr, name = device.name ?: "(BT Device)", type = "bt",
                    rssi = rssi, distanceM = dist, angleDeg = pseudoAngle(addr), channel = "2.4GHz",
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * 启动双模雷达扫描
     *
     * 执行步骤：
     * 1. 清空已有设备列表
     * 2. 注册 WiFi 扫描结果广播接收器
     * 3. 注册蓝牙设备发现广播接收器
     * 4. 发起首次蓝牙设备发现
     * 5. 启动后台协程循环（WiFi scan → 2s → BT discovery → 1s）
     *
     * 所有可能因权限不足导致的异常均静默捕获，不影响另一模式。
     */
    fun start() {
        isRunning = true; targets.clear()
        try { context.registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) } catch (_: Exception) {}
        try { context.registerReceiver(btReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND)) } catch (_: Exception) {}
        try { btAdapter?.startDiscovery() } catch (_: Exception) {}
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try { wifiManager.startScan() } catch (_: Exception) {}
                delay(2000)
                try { btAdapter?.startDiscovery() } catch (_: Exception) {}
                delay(1000)
            }
        }
    }

    /**
     * 停止双模雷达扫描
     *
     * 取消后台协程 → 注销广播接收器（静默忽略"未注册"异常）→ 取消蓝牙发现。
     */
    fun stop() {
        isRunning = false; scanJob?.cancel()
        try { context.unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
        try { context.unregisterReceiver(btReceiver) } catch (_: Exception) {}
        try { btAdapter?.cancelDiscovery() } catch (_: Exception) {}
    }

    /**
     * RSSI 信号强度 → 估算距离（米）
     *
     * 对数距离路径损耗模型：d = 10^((txP - rssi) / (10n))
     * - n = 2.5（室内混合环境路径损耗指数）
     * - 输出钳制在 0.3m ~ 100m
     *
     * @param rssi 接收信号强度指示（dBm），负数
     * @param txPower 1 米处参考 RSSI（dBm），WiFi=-40, BLE=-59
     * @return 估算距离（米）
     */
    private fun rssiToDistance(rssi: Int, txPower: Float): Float {
        val ratio = (txPower - rssi) / (10f * 2.5f)
        return Math.pow(10.0, ratio.toDouble()).toFloat().coerceIn(0.3f, 100f)
    }

    /**
     * MAC 地址 → 伪方位角（0-360°）
     *
     * 取 MAC 地址后 6 位十六进制字符 → hashCode → 按位与 0x7FFFFFFF（去符号）→ 取模 360。
     * 同一设备多次扫描角度不变，不同设备分散分布。
     *
     * @param mac MAC 地址字符串（格式 "AA:BB:CC:DD:EE:FF"）
     * @return 伪方位角（0-359 度）
     */
    private fun pseudoAngle(mac: String): Float {
        val clean = mac.replace(":", "").takeLast(6)
        return (clean.hashCode() and 0x7FFFFFFF) % 360f
    }

    /**
     * WiFi 频率（MHz） → 信道号
     *
     * - 2.4GHz: (freq - 2412) / 5 + 1 → 信道 1-14
     * - 5GHz:   (freq - 5180) / 5 + 36 → 信道 36-165
     * - 其他:   返回 0
     *
     * @param freq 中心频率（MHz），来自 ScanResult.frequency
     * @return WiFi 信道号
     */
    private fun channelFromFreq(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5180..5825 -> (freq - 5180) / 5 + 36
        else -> 0
    }
}
