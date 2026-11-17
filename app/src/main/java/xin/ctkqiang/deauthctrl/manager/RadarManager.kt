package xin.ctkqiang.deauthctrl.manager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/** 一次信号采样 —— 档案页趋势图的原始数据 */
data class RssiSample(val timeMs: Long, val rssi: Int)

/**
 * 雷达目标 —— 覆盖 WiFi / 蓝牙可见的全部技术字段
 *
 * 每一个字段都取自系统 API 真实返回值（ScanResult / BluetoothDevice），
 * 读不到的留空字符串或 0，绝不编造。档案页按 type 分支展示全部字段。
 */
data class RadarTarget(
    val id: String,
    val name: String,
    val type: String,                 // "wifi" | "bt"
    val rssi: Int,
    val distanceM: Float,
    val angleDeg: Float,
    // ── WiFi 射频参数（仅 wifi） ──
    val channel: String = "",         // "CH6"
    val frequencyMhz: Int = 0,
    val channelWidth: String = "",    // "20MHz" / "40MHz" / "80MHz" / "160MHz"…
    val centerFreq0Mhz: Int = 0,
    val centerFreq1Mhz: Int = 0,
    // ── WiFi 安全参数 ──
    val capabilitiesRaw: String = "", // 原始 capabilities 字符串
    val security: String = "",        // 解析结果：WPA3 / WPA2 / WPA / WEP / OWE / 开放
    // ── 蓝牙参数（仅 bt） ──
    val address: String = "",
    val deviceClassMajor: String = "",// 设备大类：电话 / 计算机 / 音频…
    val deviceClassDetail: String = "",// 大类内的具体小类（如 "Smartphone"）
    val bondState: String = "",       // 未绑定 / 绑定中 / 已绑定
    val linkType: String = "",        // BR/EDR / BLE / 双模
    // ── 通用 ──
    val vendor: String = "",          // OUI 厂商（内置常见厂商表，未命中为 未知厂商）
    val vendorOui: String = "",       // 地址前 3 字节
    val noiseDbm: Int = -92,          // 底噪估计值
    val snrDb: Int = 0,               // 信噪比 = rssi - noise
    val firstSeenMs: Long = 0,
    val lastSeenMs: Long = 0,
    val hitCount: Int = 0,            // 累计扫描命中次数
    val history: List<RssiSample> = emptyList(), // 最近 N 次信号采样（档案页图表）
)

/** 信号历史保留长度 —— 约 2 分钟 @ 3s 扫描周期 */
private const val HISTORY_MAX = 90

class RadarManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val btManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val btAdapter: BluetoothAdapter? = btManager?.adapter

    val targets = ConcurrentHashMap<String, RadarTarget>()

    /** 本轮轮询内新发现的目标 id —— ViewModel 取走用于触觉警报 */
    private val newContacts = ConcurrentLinkedQueue<String>()

    @Volatile var isRunning = false; private set

    private var scanJob: Job? = null
    @Volatile private var btDiscoveryActive = false

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            try {
                val results: List<ScanResult> = wifiManager.scanResults ?: return
                results.forEach { r -> addWifiTarget(r) }
            } catch (_: Exception) {}
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        @Suppress("DEPRECATION")
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        if (rssi == Short.MIN_VALUE.toInt()) return
                        val addr = device?.address ?: return
                        addBtTarget(device, addr, device?.name ?: "(BT设备)", rssi)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> { btDiscoveryActive = true }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { btDiscoveryActive = false }
                }
            } catch (_: Exception) {}
        }
    }

    private fun addWifiTarget(r: ScanResult) {
        val now = System.currentTimeMillis()
        val prev = targets[r.BSSID]
        val isNew = prev == null
        val history = (prev?.history.orEmpty() + RssiSample(now, r.level)).takeLast(HISTORY_MAX)
        val noise = -92
        targets[r.BSSID] = RadarTarget(
            id = r.BSSID,
            name = r.SSID.ifBlank { "<隐藏SSID>" },
            type = "wifi",
            rssi = r.level,
            distanceM = rssiToDistance(r.level, -40f),
            angleDeg = pseudoAngle(r.BSSID),
            channel = "CH${channelFromFreq(r.frequency)}",
            frequencyMhz = r.frequency,
            channelWidth = channelWidthName(r),
            centerFreq0Mhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) r.centerFreq0 else 0,
            centerFreq1Mhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) r.centerFreq1 else 0,
            capabilitiesRaw = r.capabilities,
            security = parseSecurity(r.capabilities),
            vendor = ouiVendor(r.BSSID),
            vendorOui = r.BSSID.take(8),
            noiseDbm = noise,
            snrDb = r.level - noise,
            firstSeenMs = prev?.firstSeenMs ?: now,
            lastSeenMs = now,
            hitCount = (prev?.hitCount ?: 0) + 1,
            history = history,
        )
        if (isNew) newContacts.add(r.BSSID)
    }

    private fun addBtTarget(device: BluetoothDevice, addr: String, name: String, rssi: Int) {
        val now = System.currentTimeMillis()
        val prev = targets[addr]
        val isNew = prev == null
        val history = (prev?.history.orEmpty() + RssiSample(now, rssi)).takeLast(HISTORY_MAX)
        val noise = -92
        val cls = runCatching { device.bluetoothClass }.getOrNull()
        targets[addr] = RadarTarget(
            id = addr,
            name = name,
            type = "bt",
            rssi = rssi,
            distanceM = rssiToDistance(rssi, -59f),
            angleDeg = pseudoAngle(addr),
            channel = "2.4GHz",
            address = addr,
            deviceClassMajor = majorClassName(cls),
            deviceClassDetail = minorClassName(cls),
            bondState = bondStateName(device.bondState),
            linkType = linkTypeName(runCatching { device.type }.getOrNull()),
            vendor = ouiVendor(addr),
            vendorOui = addr.take(8),
            noiseDbm = noise,
            snrDb = rssi - noise,
            firstSeenMs = prev?.firstSeenMs ?: now,
            lastSeenMs = now,
            hitCount = (prev?.hitCount ?: 0) + 1,
            history = history,
        )
        if (isNew) newContacts.add(addr)
    }

    /** 取走本轮新出现的目标 id 列表（一次性消费） */
    fun drainNewContacts(): List<String> {
        val out = mutableListOf<String>()
        while (true) { out.add(newContacts.poll() ?: break) }
        return out
    }

    fun start() {
        isRunning = true; targets.clear(); newContacts.clear(); btDiscoveryActive = false

        try {
            val wifiFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            val btFilter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(wifiReceiver, wifiFilter, ContextCompat.RECEIVER_EXPORTED)
                context.registerReceiver(btReceiver, btFilter, ContextCompat.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(wifiReceiver, wifiFilter)
                context.registerReceiver(btReceiver, btFilter)
            }
        } catch (_: Exception) {}

        // kick off first BT discovery (must run on main thread)
        CoroutineScope(Dispatchers.Main).launch { startBtDiscovery() }

        scanJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val scanStarted = wifiManager.startScan()
                    if (!scanStarted) { delay(5000); continue }
                } catch (_: Exception) {}
                delay(3000)
            }
        }
    }

    private fun startBtDiscovery() {
        try {
            val adapter = btAdapter ?: return
            if (adapter.isDiscovering) {
                btDiscoveryActive = true
                return
            }
            btDiscoveryActive = adapter.startDiscovery()
        } catch (_: Exception) {}
    }

    fun onTick() {
        if (!isRunning) return
        CoroutineScope(Dispatchers.Main).launch { startBtDiscovery() }
    }

    fun stop() {
        isRunning = false; scanJob?.cancel()
        try { context.unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
        try { context.unregisterReceiver(btReceiver) } catch (_: Exception) {}
        try { btAdapter?.cancelDiscovery() } catch (_: Exception) {}
        btDiscoveryActive = false
    }

    // ─── 解析工具 ─────────────────────────────────────────────────────────────

    private fun rssiToDistance(rssi: Int, txPower: Float): Float {
        val ratio = (txPower - rssi) / (10f * 2.5f)
        return Math.pow(10.0, ratio.toDouble()).toFloat().coerceIn(0.3f, 100f)
    }

    /** 地址哈希派生假方位角 —— 手机硬件无 AoA 能力，方位为稳定估计值而非测量值 */
    private fun pseudoAngle(mac: String): Float {
        val clean = mac.replace(":", "").takeLast(6)
        return (clean.hashCode() and 0x7FFFFFFF) % 360f
    }

    private fun channelFromFreq(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5180..5825 -> (freq - 5180) / 5 + 36
        else -> 0
    }

    @Suppress("DEPRECATION")
    private fun channelWidthName(r: ScanResult): String = when (r.channelWidth) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> "20MHz"
        ScanResult.CHANNEL_WIDTH_40MHZ -> "40MHz"
        ScanResult.CHANNEL_WIDTH_80MHZ -> "80MHz"
        ScanResult.CHANNEL_WIDTH_160MHZ -> "160MHz"
        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80MHz"
        else -> ""
    }

    /** 从 capabilities 字符串解析安全协议 —— 按强度倒序取最高项 */
    private fun parseSecurity(cap: String): String {
        val auth = when {
            cap.contains("WPA3") -> "WPA3"
            cap.contains("WPA2") -> "WPA2"
            cap.contains("WPA") -> "WPA"
            cap.contains("WEP") -> "WEP"
            cap.contains("OWE") -> "OWE"
            else -> "开放"
        }
        val extras = buildList {
            if (cap.contains("SAE")) add("SAE")
            if (cap.contains("PSK")) add("PSK")
            if (cap.contains("EAP")) add("802.1X")
            if (cap.contains("OWE")) add("OWE")
            if (cap.contains("ESS")) add("ESS")
            if (cap.contains("IBSS")) add("IBSS")
        }
        return if (extras.isEmpty()) auth else "$auth (${extras.joinToString("/")})"
    }

    @Suppress("DEPRECATION")
    private fun majorClassName(cls: BluetoothClass?): String = when (cls?.majorDeviceClass) {
        BluetoothClass.Device.Major.COMPUTER -> "计算机"
        BluetoothClass.Device.Major.PHONE -> "电话"
        BluetoothClass.Device.Major.IMAGING -> "影像设备"
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "音频/视频"
        BluetoothClass.Device.Major.WEARABLE -> "可穿戴"
        BluetoothClass.Device.Major.NETWORKING -> "网络设备"
        BluetoothClass.Device.Major.HEALTH -> "健康设备"
        BluetoothClass.Device.Major.PERIPHERAL -> "外设"
        BluetoothClass.Device.Major.TOY -> "玩具"
        BluetoothClass.Device.Major.UNCATEGORIZED -> "未分类"
        else -> ""
    }

    @Suppress("DEPRECATION")
    private fun minorClassName(cls: BluetoothClass?): String {
        val minor = cls?.deviceClass ?: return ""
        val major = cls.majorDeviceClass
        return when (major) {
            BluetoothClass.Device.Major.PHONE -> when (minor and 0xFC) {
                BluetoothClass.Device.PHONE_SMART -> "智能手机"
                BluetoothClass.Device.PHONE_CELLULAR -> "蜂窝电话"
                BluetoothClass.Device.PHONE_CORDLESS -> "无绳电话"
                BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY -> "调制解调器/网关"
                else -> ""
            }
            BluetoothClass.Device.Major.COMPUTER -> when (minor and 0xFC) {
                BluetoothClass.Device.COMPUTER_DESKTOP -> "台式机"
                BluetoothClass.Device.COMPUTER_LAPTOP -> "笔记本"
                BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA -> "掌上电脑/PDA"
                BluetoothClass.Device.COMPUTER_WEARABLE -> "可穿戴计算机"
                else -> ""
            }
            BluetoothClass.Device.Major.AUDIO_VIDEO -> when (minor and 0xFC) {
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "耳机"
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "音箱"
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "头戴式耳机"
                BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> "车载音响"
                else -> ""
            }
            BluetoothClass.Device.Major.WEARABLE -> when (minor and 0xFC) {
                BluetoothClass.Device.WEARABLE_WRIST_WATCH -> "智能手表"
                BluetoothClass.Device.WEARABLE_GLASSES -> "智能眼镜"
                else -> ""
            }
            else -> ""
        }
    }

    private fun bondStateName(state: Int): String = when (state) {
        BluetoothDevice.BOND_BONDED -> "已绑定"
        BluetoothDevice.BOND_BONDING -> "绑定中"
        else -> "未绑定"
    }

    private fun linkTypeName(type: Int?): String = when (type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "BR/EDR 经典"
        BluetoothDevice.DEVICE_TYPE_LE -> "BLE 低功耗"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "双模"
        BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "未知"
        else -> ""
    }

    /**
     * OUI 厂商表（内置常见厂商前 3 字节）
     *
     * 表外地址显示「未知厂商」，同时保留 OUI 前缀供人工查证。
     */
    private fun ouiVendor(mac: String): String {
        val oui = mac.replace(":", "").take(6).uppercase()
            .chunked(2).joinToString(":")
        return OUI_VENDORS[oui] ?: "未知厂商"
    }

    companion object {
        private val OUI_VENDORS = mapOf(
            "FC:FB:FB" to "Apple", "F0:18:98" to "Apple", "A4:83:E7" to "Apple",
            "D0:81:7A" to "Apple", "78:CA:39" to "Apple", "AC:BC:32" to "Apple",
            "F4:F1:5A" to "Apple", "8C:85:90" to "Apple", "34:36:3B" to "HUAWEI",
            "E0:24:7F" to "HUAWEI", "20:64:DE" to "HUAWEI", "50:87:B8" to "HUAWEI",
            "D4:6A:6A" to "HUAWEI", "78:02:F8" to "Xiaomi", "28:6C:07" to "Xiaomi",
            "8C:DE:F9" to "Xiaomi", "64:CC:2E" to "Xiaomi", "50:64:2B" to "Xiaomi",
            "8C:F5:A3" to "Samsung", "34:31:C4" to "Samsung", "90:00:DB" to "Samsung",
            "CC:3A:61" to "Samsung", "EC:9F:0D" to "Samsung", "04:8D:38" to "Samsung",
            "A4:C3:F0" to "Intel", "3C:A0:67" to "Intel", "58:FB:84" to "Intel",
            "BC:DD:C2" to "Sony", "7C:70:DB" to "Sony", "3C:77:E6" to "Sony",
            "40:23:43" to "Sony", "E0:E0:C2" to "Espressif", "24:0A:C4" to "Espressif",
            "DC:4F:22" to "Espressif", "A0:20:A6" to "Espressif", "30:AE:A4" to "Espressif",
            "B8:27:EB" to "Raspberry Pi", "DC:A6:32" to "Raspberry Pi",
            "E4:5F:01" to "Raspberry Pi", "D8:3A:DD" to "Raspberry Pi", "28:CD:C1" to "Raspberry Pi",
            "C4:93:D9" to "Lenovo", "00:DB:DF" to "Lenovo", "54:EE:75" to "Wistron",
            "F0:76:1C" to "HP", "00:1B:78" to "HP", "54:60:09" to "Google",
            "F4:F5:E8" to "Google", "DC:A9:04" to "Google", "A4:77:33" to "Google",
            "00:50:F2" to "Microsoft", "0C:5B:8F" to "Microsoft",
            "00:23:CC" to "Nintendo", "E4:CE:8F" to "Nintendo", "7C:BB:8A" to "Nintendo",
            "50:DC:E7" to "Amazon", "A0:02:DC" to "Amazon", "F0:27:2D" to "Amazon",
            "C8:3A:35" to "Tenda", "50:C7:BF" to "TP-Link", "94:C1:50" to "TP-Link",
            "C0:4A:00" to "TP-Link", "F4:F2:6D" to "TP-Link", "AC:84:C6" to "TP-Link",
            "E4:6F:13" to "TP-Link", "18:D6:C7" to "TP-Link", "D8:07:B6" to "TP-Link",
            "70:4F:57" to "TP-Link", "00:12:F3" to "Texas Instruments",
        )
    }
}
