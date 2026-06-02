package xin.ctkqiang.deauthctrl.manager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
 * 表示雷达视图中一个已探测到的无线设备（WiFi AP 或蓝牙设备）。
 *
 * @property id 唯一标识符（WiFi=BSSID, 蓝牙=MAC地址）
 * @property name 设备显示名（WiFi=SSID, 蓝牙=设备名或"(未知)"）
 * @property type 设备类型："wifi" 或 "bt"
 * @property rssi 接收信号强度指示（dBm），范围约 -100（极弱）至 -20（极强）
 * @property distanceM 基于 RSSI 自由空间路径损耗公式估算的距离（米）
 * @property angleDeg 伪方位角（0-360°），基于 MAC 地址哈希值生成，同一设备保持相对稳定
 * @property channel WiFi 信道号或蓝牙频段标签
 * @property vendor WiFi 加密方式（WPA2/WPA3等）或空字符串
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
 * WiFi/蓝牙设备雷达扫描管理器
 *
 * 同时扫描附近 WiFi AP 和蓝牙设备，通过 RSSI 信号强度估算距离，
 * 使用伪随机角度模拟方位（无定向天线硬件时），输出 PPI 雷达视图所需数据。
 *
 * ## RSSI → 距离估算
 * 自由空间路径损耗简化公式（对数距离路径损耗模型）：
 *   distance = 10 ^ ((txPower - rssi) / (10 * n))
 * 参数：
 *   txPower = -40 dBm (WiFi) / -59 dBm (BLE 默认 1m 参考发射功率)
 *   n = 2.5（室内环境路径损耗指数，自由空间=2.0, 办公室=3.0）
 *   输出钳制在 0.3m ~ 100m
 *
 * ## 伪方位角生成
 * 无定向天线硬件时，基于 MAC 地址后 6 位十六进制的 hashCode 取模 360，
 * 确保同一设备的方位角在多次扫描间保持稳定（用于雷达视图定位）。
 *
 * ## 扫描机制
 * - WiFi: WifiManager.startScan() + SCAN_RESULTS_AVAILABLE_ACTION 广播接收
 * - 蓝牙: BluetoothAdapter.startDiscovery() + ACTION_FOUND 广播接收
 * - 扫描间隔: 每 3 秒触发一次 WiFi 扫描 + 蓝牙发现
 *
 * ## 线程模型
 * - 主线程: BroadcastReceiver 回调
 * - 后台协程: 定时触发扫描循环（Dispatchers.IO）
 * - ConcurrentHashMap: 线程安全的设备表，避免 BroadcastReceiver 与 UI 轮询冲突
 */
class RadarManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val btAdapter = BluetoothAdapter.getDefaultAdapter()

    /** 已探测设备表，Key=MAC/BSSID，ConcurrentHashMap 线程安全 */
    val targets = ConcurrentHashMap<String, RadarTarget>()

    /** 雷达运行状态，@Volatile 确保跨线程可见 */
    @Volatile var isRunning = false
        private set

    /** 后台扫描循环协程 Job */
    private var scanJob: Job? = null

    /**
     * WiFi 扫描结果广播接收器
     *
     * 接收 WifiManager.SCAN_RESULTS_AVAILABLE_ACTION 广播，
     * 遍历 scanResults 列表，对每个 AP 计算距离和伪方位角并更新 targets。
     */
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) != true) return
            val results = wifiManager.scanResults ?: return
            results.forEach { r ->
                val dist = rssiToDistance(r.level, -40f)
                targets[r.BSSID] = RadarTarget(
                    id = r.BSSID, name = r.SSID.ifBlank { "<隐藏>" }, type = "wifi",
                    rssi = r.level, distanceM = dist,
                    angleDeg = pseudoAngle(r.BSSID), channel = "CH${channelFromFreq(r.frequency)}",
                    vendor = r.capabilities.take(12),
                )
            }
        }
    }

    /**
     * 蓝牙设备发现广播接收器
     *
     * 接收 BluetoothDevice.ACTION_FOUND 广播，
     * 提取设备名、MAC 地址和 RSSI（来自 EXTRA_RSSI），更新 targets。
     */
    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
            if (rssi == Short.MIN_VALUE.toInt()) return
            val dist = rssiToDistance(rssi, -59f)
            val addr = device.address ?: return
            targets[addr] = RadarTarget(
                id = addr, name = device.name ?: "(未知)", type = "bt",
                rssi = rssi, distanceM = dist,
                angleDeg = pseudoAngle(addr), channel = "2.4GHz",
            )
        }
    }

    /**
     * 启动雷达扫描
     *
     * 注册 WiFi 和蓝牙广播接收器，启动蓝牙发现，在协程中循环触发扫描。
     */
    fun start() {
        isRunning = true; targets.clear()
        try { context.registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) } catch (_: Exception) {}
        try { context.registerReceiver(btReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND)) } catch (_: Exception) {}
        try { btAdapter?.startDiscovery() } catch (_: Exception) {}
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try { wifiManager.startScan() } catch (_: Exception) {}
                delay(3000)
                try { btAdapter?.startDiscovery() } catch (_: Exception) {}
            }
        }
    }

    /**
     * 停止雷达扫描
     *
     * 取消协程，注销广播接收器，取消蓝牙发现。
     */
    fun stop() {
        isRunning = false; scanJob?.cancel()
        try { context.unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
        try { context.unregisterReceiver(btReceiver) } catch (_: Exception) {}
        btAdapter?.cancelDiscovery()
    }

    /**
     * RSSI 信号强度 → 估算距离（米）
     *
     * 对数距离路径损耗模型：d = 10^((txP - rssi) / (10n))
     * 输出钳制在 0.3m ~ 100m。
     *
     * @param rssi 接收信号强度（dBm）
     * @param txPower 1 米处参考 RSSI（dBm），WiFi=-40, BLE=-59
     */
    private fun rssiToDistance(rssi: Int, txPower: Float): Float {
        val ratio = (txPower - rssi) / (10f * 2.5f)
        return Math.pow(10.0, ratio.toDouble()).toFloat().coerceIn(0.3f, 100f)
    }

    /**
     * MAC 地址 → 伪方位角（0-360°）
     *
     * 取 MAC 后 6 位十六进制 → hashCode → 取模 360。
     * 同一设备多次扫描角度不变，用于雷达视图定位。
     */
    private fun pseudoAngle(mac: String): Float {
        val clean = mac.replace(":", "").takeLast(6)
        return (clean.hashCode() and 0x7FFFFFFF) % 360f
    }

    /**
     * 频率（MHz） → WiFi 信道号
     *
     * 2.4GHz: (freq - 2412) / 5 + 1  → 信道 1-14
     * 5GHz:   (freq - 5180) / 5 + 36 → 信道 36-165
     */
    private fun channelFromFreq(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5180..5825 -> (freq - 5180) / 5 + 36
        else -> 0
    }
}
