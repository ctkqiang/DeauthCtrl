package xin.ctkqiang.deauthctrl.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult as BleScanResult
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
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/** 一次信号采样 —— 档案页趋势图的原始数据；timestampNanos 与传感器同源,供方位推演对齐 */
data class RssiSample(
    val timeMs: Long,
    val rssi: Int,
    val timestampNanos: Long = 0L,
    val approxTime: Boolean = false,
)

/**
 * BLE 广播包字段快照 —— 只拷贝值,绝不持有 ScanRecord/ScanResult 对象
 *
 * sentinel 约定: txPowerLevelDbm=Int.MIN_VALUE / advFlags=-1 / manufacturerId=-1
 * 均表示广播中该字段缺失;hasRecord=false 表示从未收到过广播包(经典发现路径)。
 */
data class BleAdv(
    val hasRecord: Boolean = false,
    val txPowerLevelDbm: Int = Int.MIN_VALUE,
    val advFlags: Int = -1,
    val serviceUuids: List<String> = emptyList(),
    val serviceData: Map<String, String> = emptyMap(),
    val manufacturerData: Map<Int, String> = emptyMap(),
    val manufacturerId: Int = -1,
    val deviceNameAdv: String = "",
    val updatedAtMs: Long = 0L,
)

/**
 * 雷达目标 —— 覆盖 WiFi / 蓝牙可见的全部技术字段
 *
 * 每一个字段都取自系统 API 真实返回值（ScanResult / BluetoothDevice / ScanRecord），
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
    val bonded: Boolean = false,      // 已绑定/绑定中 —— 雷达页按友方设备处理:蓝色高亮、不告警不震动
    val linkType: String = "",        // BR/EDR / BLE / 双模
    val alias: String = "",           // 配对别名（BluetoothDevice.getAlias 真实返回值,可为空）
    // ── BLE 广播详情（仅 ble 扫描路径捕获,经典相位不擦除已捕获字段） ──
    val bleAdv: BleAdv? = null,
    val observedVia: String = "classic", // "ble" | "classic" —— 档案页据此区分 BLE/BT 徽章
    // ── 距离模型元数据 ──
    val txPowerDbm: Int? = null,      // 仅 BLE 广播真实存在时赋值;null=假定参考功率
    val distanceAssumed: Boolean = true, // txPowerDbm==null 时为 true,界面标注「假定 PTX」
    // ── 通用 ──
    val vendor: String = "",          // OUI 厂商（内置常见厂商表，未命中为 未知厂商）
    val vendorOui: String = "",       // 地址前 3 字节
    val noiseDbm: Int = -92,          // 底噪估计值
    val snrDb: Int = 0,               // 信噪比 = rssi - noise
    val firstSeenMs: Long = 0,
    val lastSeenMs: Long = 0,
    val hitCount: Int = 0,            // 累计扫描命中次数
    val history: List<RssiSample> = emptyList(), // 最近 N 次信号采样（档案页图表）
    val hitBuckets: List<Int> = emptyList(),     // 最近 10 分钟每分钟命中数（档案页频率图）
    val hitBucketStartMs: Long = 0L,
    /** RPA 反匿名化:命中 IRK 的掩码展示("ABCD…WXYZ");null = 未解析/非 RPA */
    val rpaResolved: String? = null,
)

/** 信号历史保留长度 —— 约 2 分钟 @ 3s 扫描周期 */
private const val HISTORY_MAX = 90

/** 命中频率桶数(分钟粒度) —— 档案页命中频率图横轴 */
private const val HIT_BUCKET_MAX = 10

/**
 * RSSI 路径损耗距离估计 —— 公共函数,档案页/雷达页共用同一公式,禁止各处复制。
 *
 * 模型: d = 10^((refDbm - rssi) / (10·n)),n 为路径损耗指数(默认 2.5 室内)。
 * 输出钳制在 0.3..100m。这是模型估计,不是测量距离 —— 界面必须标注。
 */
fun rssiDistanceEstimateM(rssi: Int, refDbm: Float, pathLossExp: Float = 2.5f): Float =
    Math.pow(10.0, ((refDbm - rssi) / (10f * pathLossExp)).toDouble()).toFloat().coerceIn(0.3f, 100f)

/** 最近若干采样 RSSI 中位数 —— 距离估计用,抗单次扫描抖动;历史已含新样本,恒非空 */
private fun medianRssi(history: List<RssiSample>): Int {
    val tail = history.takeLast(5).map { it.rssi }.sorted()
    return tail[tail.size / 2]
}

class RadarManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val btManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val targets = ConcurrentHashMap<String, RadarTarget>()

    /** 本轮轮询内新发现的目标 id —— ViewModel 取走用于触觉警报 */
    private val newContacts = ConcurrentLinkedQueue<String>()

    @Volatile var isRunning = false; private set

    /** 用户提供的 IRK 列表(反匿名化旋转地址用) */
    @Volatile var irks: List<ByteArray> = emptyList()

    private var scanJob: Job? = null
    @Volatile private var btDiscoveryActive = false

    /** 扫描相位 —— BLE 扫描与经典发现互斥,分时轮换避免互相打断 */
    @Volatile private var phase = PHASE_BLE
    /** 经典发现相位的窗口终点 */
    @Volatile private var phaseEndAt = 0L
    @Volatile private var bleScanning = false
    /** BLE 相位窗口终点 */
    @Volatile private var bleScanEndAt = 0L
    /** BLE 扫描失败后的重试时间点 */
    @Volatile private var bleRetryAt = 0L

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
                        addBtTarget(
                            device,
                            addr,
                            device?.name ?: L10n.get(R.string.mgr_radar_placeholder_bt),
                            rssi,
                            source = "classic",
                        )
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> { btDiscoveryActive = true }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { btDiscoveryActive = false }
                }
            } catch (_: Exception) {}
        }
    }

    /** WiFi 扫描结果时间戳缓存 —— 广播与轮询协程会重复注入同一份 scanResults,按时间戳去重 */
    private val wifiSeenTs = HashMap<String, Long>()

    /** 读-改-写全程持锁:BLE 回调(binder 线程)/经典发现(主线程)/扫描协程(IO)并发写同一目标 */
    @Synchronized
    private fun addWifiTarget(r: ScanResult) {
        val now = System.currentTimeMillis()
        val prev = targets[r.BSSID]
        // 同一扫描结果(相同 BSSID + 相同系统时间戳)不重复计数 ——
        // SCAN_RESULTS_AVAILABLE 广播与轮询缓存注入会把同一份缓存各播一遍
        val ts = runCatching { r.timestamp }.getOrDefault(0L)
        if (ts > 0 && wifiSeenTs[r.BSSID] == ts) return
        if (ts > 0) wifiSeenTs[r.BSSID] = ts
        val isNew = prev == null
        // ScanResult.timestamp 单位微秒(API 23+),与 SensorEvent.timestamp 同基准;
        // 拿不到时回退单调时钟并标 approx,该样本只进距离统计不进方位直方图
        val bootUs = runCatching { r.timestamp }.getOrDefault(0L)
        val nanos = if (bootUs > 0) bootUs * 1000 else android.os.SystemClock.elapsedRealtimeNanos()
        val approx = bootUs <= 0
        val history = (prev?.history.orEmpty() + RssiSample(now, r.level, nanos, approx)).takeLast(HISTORY_MAX)
        val noise = -92
        val buckets = advanceHitBuckets(prev, now)
        targets[r.BSSID] = RadarTarget(
            id = r.BSSID,
            name = r.SSID.ifBlank { L10n.get(R.string.mgr_radar_hidden_ssid) },
            type = "wifi",
            rssi = r.level,
            distanceM = rssiDistanceEstimateM(medianRssi(history), WIFI_REF_DBM, WIFI_PATH_LOSS),
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
            hitBuckets = buckets.first,
            hitBucketStartMs = buckets.second,
        )
        if (isNew) newContacts.add(r.BSSID)
    }

    @Synchronized
    private fun addBtTarget(
        device: BluetoothDevice?,
        addr: String,
        name: String,
        rssi: Int,
        source: String,                 // "ble" | "classic"
        freshAdv: BleAdv? = null,
        timestampNanos: Long = 0L,
    ) {
        val now = System.currentTimeMillis()
        val prev = targets[addr]
        val isNew = prev == null
        // 经典发现相位不得擦掉 BLE 相位已捕获的广播字段:新值覆盖,缺失沿用旧值
        val adv = (freshAdv ?: prev?.bleAdv)
        val history = (prev?.history.orEmpty() + RssiSample(now, rssi, timestampNanos, approxTime = timestampNanos <= 0)).takeLast(HISTORY_MAX)
        val noise = -92
        val cls = runCatching { device?.bluetoothClass }.getOrNull()
        // 距离模型:BLE 广播真实携带发射功率时用它;经典相位无新广播时继承旧 PTX(否则模型来回跳)
        val ptxDbm = freshAdv?.txPowerLevelDbm?.takeIf { it in -40..10 } ?: prev?.txPowerDbm
        val refDbm = ptxDbm?.toFloat() ?: if (source == "classic") BT_CLASSIC_REF_DBM else BLE_ASSUMED_REF_DBM
        // RPA 反匿名化:地址命中任一 IRK 即标记(btrpa-scan 技术)
        val rpaResolved = runCatching {
            if (irks.isNotEmpty() && RpaResolver.isRpa(addr)) {
                RpaResolver.resolve(addr, irks)?.let { RpaResolver.maskIrk(it) }
            } else null
        }.getOrNull()
        // 绑定状态粘滞:经典发现相位没有 BLE 相位的信息来源,不能把已绑定目标打回陌生
        val bonded = (device?.bondState == BluetoothDevice.BOND_BONDED || device?.bondState == BluetoothDevice.BOND_BONDING) || prev?.bonded == true
        // 名称优先级:有效新名 > 广播名 > 旧名 > 占位名;经典发现的 "(BT设备)" 永不覆盖好名字
        val effectiveName = when {
            name.isNotBlank() && !name.startsWith("(") -> name
            !adv?.deviceNameAdv.isNullOrBlank() -> adv!!.deviceNameAdv
            !prev?.name.isNullOrBlank() && !prev!!.name.startsWith("(") -> prev.name
            else -> name
        }
        targets[addr] = RadarTarget(
            id = addr,
            name = effectiveName,
            type = "bt",
            rssi = rssi,
            distanceM = rssiDistanceEstimateM(medianRssi(history), refDbm, btEnvPathLoss),
            angleDeg = pseudoAngle(addr),
            channel = "2.4GHz",
            address = addr,
            deviceClassMajor = majorClassName(cls),
            deviceClassDetail = minorClassName(cls),
            bondState = bondStateName(device?.bondState ?: BluetoothDevice.BOND_NONE),
            bonded = bonded,
            linkType = linkTypeName(runCatching { device?.type }.getOrNull()),
            alias = runCatching { device?.alias }.getOrNull()?.takeIf { it.isNotBlank() } ?: prev?.alias.orEmpty(),
            bleAdv = adv,
            observedVia = if (adv != null) "ble" else prev?.observedVia ?: "classic",
            txPowerDbm = ptxDbm,
            distanceAssumed = ptxDbm == null,
            vendor = ouiVendor(addr),
            vendorOui = addr.take(8),
            noiseDbm = noise,
            snrDb = rssi - noise,
            firstSeenMs = prev?.firstSeenMs ?: now,
            lastSeenMs = now,
            hitCount = (prev?.hitCount ?: 0) + 1,
            history = history,
            hitBuckets = advanceHitBuckets(prev, now).first,
            hitBucketStartMs = advanceHitBuckets(prev, now).second,
            rpaResolved = rpaResolved ?: prev?.rpaResolved,
        )
        if (isNew) newContacts.add(addr)
    }

    /** 最近 10 分钟每分钟命中桶 —— 档案页命中频率图的数据源 */
    private fun advanceHitBuckets(prev: RadarTarget?, now: Long): Pair<List<Int>, Long> {
        val start = now / 60_000L * 60_000L
        if (prev == null || prev.hitBucketStartMs <= 0) return listOf(1) to start
        val delta = ((start - prev.hitBucketStartMs) / 60_000L).toInt()
        if (delta <= 0) {
            val cur = if (prev.hitBuckets.isEmpty()) listOf(0) else prev.hitBuckets
            return (cur.dropLast(1) + (cur.last() + 1)).takeLast(HIT_BUCKET_MAX) to prev.hitBucketStartMs
        }
        val kept = prev.hitBuckets.takeLast((HIT_BUCKET_MAX - delta).coerceAtLeast(0))
        val zeros = List((delta - 1).coerceAtMost(HIT_BUCKET_MAX - 1)) { 0 }
        return (kept + zeros + 1).takeLast(HIT_BUCKET_MAX) to start
    }

    /** 取走本轮新出现的目标 id 列表（一次性消费） */
    fun drainNewContacts(): List<String> {
        val out = mutableListOf<String>()
        while (true) { out.add(newContacts.poll() ?: break) }
        return out
    }

    /**
     * 清理过期目标 —— 由 ViewModel 每 500ms 调用。
     *
     * BLE 设备会轮换随机 MAC,不清理会让目标表与定位状态无界增长,
     * 雷达/地图/档案页永久显示幽灵设备。只删超龄目标;调用方(ViewModel)
     * 拿到表后同步裁剪定位引擎的 alive 集合。
     */
    fun pruneStale(maxAgeMs: Long = 60_000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val itr = targets.entries.iterator()
        while (itr.hasNext()) {
            if (itr.next().value.lastSeenMs < cutoff) itr.remove()
        }
        wifiSeenTs.keys.retainAll { targets.containsKey(it) }
    }

    // ─── 连接动作(雷达弹窗:连接 WiFi / 配对蓝牙) ────────────────────────────

    /** WiFi 连接请求回调必须持有引用 —— 被 GC 则系统回收连接 */
    private val wifiConnectCallback = java.util.concurrent.atomic.AtomicReference<android.net.ConnectivityManager.NetworkCallback?>(null)

    /**
     * 连接雷达发现的目标 WiFi 网络。
     *
     * API 29+:WifiNetworkSpecifier —— 系统弹窗让用户确认后建立连接,
     * 应用拿不到密码明文之外的任何信息,无需 root;WPA3/WPA2/WPA/开放
     * 都走标准路径,OWE 由系统自动协商。WEP 系统直连不支持,如实报错。
     * API 28-:WifiConfiguration + enableNetwork(已废弃但仍可用)。
     *
     * @return null 表示已发起;非 null 为失败原因
     */
    @SuppressLint("MissingPermission")
    fun requestWifiConnect(ssid: String, security: String, passphrase: String): String? {
        val name = ssid.trim()
        if (name.isEmpty()) return L10n.get(R.string.mgr_radar_connect_bad_ssid)
        val sec = security.uppercase()
        return if (Build.VERSION.SDK_INT >= 29) {
            val builder = android.net.wifi.WifiNetworkSpecifier.Builder().setSsid(name)
            val spec = try {
                when {
                    sec.contains("WPA3") -> builder.setWpa3Passphrase(passphrase)
                    sec.contains("WPA2") || sec.contains("WPA") -> builder.setWpa2Passphrase(passphrase)
                    sec.contains("WEP") -> return L10n.get(R.string.mgr_radar_connect_wep_unsupported)
                    else -> Unit // 开放 / OWE
                }
                builder.build()
            } catch (e: IllegalArgumentException) {
                return e.message ?: L10n.get(R.string.mgr_radar_connect_bad_pass)
            }
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val request = android.net.NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(spec)
                .build()
            wifiConnectCallback.set(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {}
                override fun onUnavailable() { wifiConnectCallback.set(null) }
            })
            wifiConnectCallback.set(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {}
                override fun onUnavailable() { wifiConnectCallback.set(null) }
            })
            // 崩溃修复:requestNetwork 在缺少定位权限/定位关闭/系统拒绝时抛
            // SecurityException(Android 10+ 对 WifiNetworkSpecifier 的硬性要求),
            // 必须捕获并如实返回原因,而不是让应用崩掉
            return try {
                cm.requestNetwork(request, wifiConnectCallback.get()!!)
                null
            } catch (e: SecurityException) {
                wifiConnectCallback.set(null)
                L10n.get(R.string.mgr_radar_connect_perm)
            } catch (e: Exception) {
                wifiConnectCallback.set(null)
                e.message ?: L10n.get(R.string.mgr_radar_connect_failed)
            }
        } else {
            @Suppress("DEPRECATION")
            val conf = android.net.wifi.WifiConfiguration().apply {
                SSID = "\"$name\""
                when {
                    sec.contains("WPA3") || sec.contains("WPA2") || sec.contains("WPA") -> {
                        preSharedKey = "\"$passphrase\""
                        allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK)
                    }
                    sec.contains("WEP") -> {
                        wepKeys[0] = "\"$passphrase\""
                        allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
                        allowedAuthAlgorithms.set(android.net.wifi.WifiConfiguration.AuthAlgorithm.OPEN)
                    }
                    else -> allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
                }
            }
            val netId = try { wifiManager.addNetwork(conf) } catch (_: Exception) { -1 }
            if (netId < 0) return L10n.get(R.string.mgr_radar_connect_failed)
            val ok = wifiManager.enableNetwork(netId, true) && wifiManager.reconnect()
            if (!ok) return L10n.get(R.string.mgr_radar_connect_failed)
            null
        }
    }

    /**
     * 配对雷达发现的蓝牙/BLE 目标 —— 双方设备弹出系统配对请求。
     * 已绑定直接成功;目标需处于可发现/可连接状态。这是标准系统配对,
     * 不是静默连接(Android 无静默配对通道,如实说明)。
     *
     * @return null 表示已发起或已绑定;非 null 为失败原因
     */
    @SuppressLint("MissingPermission")
    fun pairDevice(addr: String): String? {
        val adapter = btManager?.adapter ?: return L10n.get(R.string.mgr_radar_err_bt_unavailable)
        val device = try {
            adapter.getRemoteDevice(addr)
        } catch (_: Exception) {
            return L10n.get(R.string.mgr_radar_err_bad_address, addr)
        }
        if (device.bondState == BluetoothDevice.BOND_BONDED) return null
        val ok = try { device.createBond() } catch (_: Exception) { false }
        return if (ok) null else L10n.get(R.string.mgr_radar_pair_failed)
    }

    /**
     * 配对弹窗消息 —— 让目标设备屏幕上弹出系统配对请求。
     *
     * 真实机制: 系统配对弹窗显示的是**发起方的蓝牙名称** —— 先把本机蓝牙名
     * 临时改成自定义消息, 再对目标发起 createBond(), 目标(处于可配对状态时)
     * 会看到 "「消息」想与你配对" 的系统弹窗; 60 秒后自动恢复本机原蓝牙名。
     *
     * 局限(UI 必须如实呈现): 仅对处于可发现/可配对状态的经典蓝牙设备有效;
     * 这是系统标准配对弹窗, 不是自定义 UI; 目标拒绝配对则弹窗消失。
     *
     * @return null 表示已发起; 非 null 为失败原因
     */
    @SuppressLint("MissingPermission")
    fun sendPairingPopup(addr: String, message: String): String? {
        val adapter = btManager?.adapter ?: return L10n.get(R.string.mgr_radar_err_bt_unavailable)
        val device = runCatching { adapter.getRemoteDevice(addr) }.getOrNull()
            ?: return L10n.get(R.string.mgr_radar_err_bad_address, addr)
        val msg = message.trim().take(60)
        if (msg.isEmpty()) return L10n.get(R.string.mgr_radar_err_empty_message)
        val originalName = runCatching { adapter.name }.getOrNull()
        val renameOk = runCatching { adapter.setName(msg) }.isSuccess
        if (!renameOk) return L10n.get(R.string.mgr_radar_err_rename_failed)
        val bonded = runCatching { device.createBond() }.getOrDefault(false)
        if (!bonded) {
            runCatching { originalName?.let { adapter.setName(it) } }
            return L10n.get(R.string.mgr_radar_err_pair_failed)
        }
        // 60 秒后恢复本机蓝牙名(配对弹窗生命周期已过)
        val restoreName = originalName
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching { restoreName?.let { adapter.setName(it) } }
        }, 60_000L)
        return null
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

        // 扫描相位机由 onTick 驱动:先跑 BLE 相位,窗口结束换班经典发现
        phase = PHASE_BLE
        phaseEndAt = 0L
        bleScanEndAt = 0L
        bleRetryAt = 0L

        scanJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val scanStarted = wifiManager.startScan()
                    if (!scanStarted) { delay(2000) }
                } catch (_: Exception) {}
                // 无论系统是否接受新扫描,都读取缓存结果注入目标表 ——
                // 部分 ROM(MIUI 等)对前台应用也做 WiFi 扫描节流,startScan 被
                // 静默拒绝时只靠 SCAN_RESULTS_AVAILABLE 广播会收不到 WiFi 目标
                runCatching { wifiManager.scanResults.orEmpty() }
                    .getOrDefault(emptyList())
                    .forEach { r -> addWifiTarget(r) }
                delay(3000)
            }
        }
    }

    /** BLE 扫描回调 —— 结果直接进目标表;失败退避,防止节拍内密集重试触发系统限流 */
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: BleScanResult) {
            try {
                val device = result.device ?: return
                val rssi = result.rssi
                val addr = device.address ?: return
                val rec = result.scanRecord
                val name = device.name ?: rec?.deviceName ?: L10n.get(R.string.mgr_radar_placeholder_ble)
                // result.timestampNanos 与 SensorEvent.timestamp 同一基准;<=0 时回退并标 approx
                val nanos = result.timestampNanos.takeIf { it > 0 } ?: android.os.SystemClock.elapsedRealtimeNanos()
                addBtTarget(device, addr, name, rssi, source = "ble", freshAdv = result.toBleAdv(), timestampNanos = nanos)
            } catch (_: Exception) {}
        }

        override fun onScanFailed(errorCode: Int) {
            bleScanning = false
            bleRetryAt = System.currentTimeMillis() + BLE_FAIL_BACKOFF_MS
        }
    }

    /**
     * ScanRecord → BleAdv 快照 —— 只拷贝值,绝不持有 ScanRecord 对象。
     * 各字段缺失时用 sentinel 表示;API 29+ 才有的 solicitation 字段低版本不读。
     */
    private fun BleScanResult.toBleAdv(): BleAdv {
        val rec = scanRecord ?: return BleAdv()
        val mfr = mutableMapOf<Int, String>()
        val mfrData = rec.manufacturerSpecificData
        if (mfrData != null) {
            for (i in 0 until mfrData.size()) {
                if (mfr.size >= 8) break
                mfr[mfrData.keyAt(i)] = bytesToHex(mfrData.valueAt(i), 64)
            }
        }
        val svcData = mutableMapOf<String, String>()
        rec.serviceData?.forEach { uuid, bytes -> if (svcData.size < 8) svcData[uuid.toString()] = bytesToHex(bytes, 32) }
        return BleAdv(
            hasRecord = true,
            txPowerLevelDbm = rec.txPowerLevel,
            advFlags = rec.advertiseFlags,
            serviceUuids = rec.serviceUuids?.map { it.uuid.toString() }?.distinct()?.take(16).orEmpty(),
            serviceData = svcData,
            manufacturerData = mfr,
            manufacturerId = mfr.keys.firstOrNull() ?: -1,
            deviceNameAdv = rec.deviceName.orEmpty(),
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    /**
     * BLE 相位:扫满一个窗口后让位;扫描器每次现取,不缓存 ——
     * 蓝牙关闭时 bluetoothLeScanner 返回 null,构造期缓存会把 null 永久固化
     * (蓝牙之后打开也不会恢复),参见 BleScannerManager 的同款注释。
     */
    private fun tickBlePhase() {
        val now = System.currentTimeMillis()
        if (bleScanning) {
            if (now >= bleScanEndAt) {
                stopBleScan()
                switchToDiscovery()
            }
            return
        }
        val scanner = btManager?.adapter?.bluetoothLeScanner
        if (scanner != null && now >= bleRetryAt) {
            try {
                scanner.startScan(bleScanCallback)
                bleScanning = true
                bleScanEndAt = now + BLE_SCAN_WINDOW_MS
                return
            } catch (_: Exception) {
                bleScanning = false
            }
        }
        // 扫不动(无扫描器 / 失败退避中)且相位已到期:换班经典发现
        if (now >= phaseEndAt) switchToDiscovery()
    }

    /**
     * 经典发现相位:空闲即拉起发现;窗口到期且发现已结束才让位给 BLE。
     * 发现结束得早(设备少)且窗口余量充足时重新拉起一轮,提高覆盖。
     */
    private fun tickDiscoveryPhase() {
        val now = System.currentTimeMillis()
        if (btDiscoveryActive) return // 等 ACTION_DISCOVERY_FINISHED 自然结束,不与 BLE 抢硬件
        if (now + DISCOVERY_RESTART_MIN_MS < phaseEndAt) {
            tickScope.launch { startBtDiscovery() }
        } else {
            phase = PHASE_BLE
            bleScanEndAt = 0L
            bleRetryAt = 0L
        }
    }

    private fun switchToDiscovery() {
        phase = PHASE_DISCOVERY
        phaseEndAt = System.currentTimeMillis() + DISCOVERY_WINDOW_MS
    }

    private fun stopBleScan() {
        if (!bleScanning) return
        bleScanning = false
        runCatching { btManager?.adapter?.bluetoothLeScanner?.stopScan(bleScanCallback) }
    }

    private fun startBtDiscovery() {
        try {
            val adapter = btManager?.adapter ?: return
            if (adapter.isDiscovering) {
                btDiscoveryActive = true
                return
            }
            btDiscoveryActive = adapter.startDiscovery()
        } catch (_: Exception) {}
    }

    private val tickScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * 每 500ms 被 ViewModel 轮询一次,推进扫描相位机。
     *
     * Android 框架层不允许经典发现(startDiscovery)与 BLE 扫描(startScan)
     * 同时运行 —— 启动其中一个会中止另一个。原先两者在 500ms 节拍里抢跑
     * 互相打断,蓝牙/BLE 设备基本进不了目标表;现在按相位分时轮换:
     * BLE 扫 10s → 经典发现 13s → BLE 扫 10s → …
     */
    fun onTick() {
        if (!isRunning) return
        when (phase) {
            PHASE_BLE -> tickBlePhase()
            else -> tickDiscoveryPhase()
        }
    }

    fun stop() {
        isRunning = false; scanJob?.cancel()
        try { context.unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
        try { context.unregisterReceiver(btReceiver) } catch (_: Exception) {}
        try { btManager?.adapter?.cancelDiscovery() } catch (_: Exception) {}
        stopBleScan()
        btDiscoveryActive = false
    }

    // ─── 解析工具 ─────────────────────────────────────────────────────────────

    /**
     * 地址哈希派生假方位角 —— 手机硬件无 AoA 能力，方位为稳定估计值而非测量值。
     * 仅在没有方位推演结果(RssiLocalizationEngine)时作为占位,界面标注「占位」。
     */
    private fun pseudoAngle(mac: String): Float {
        val clean = mac.replace(":", "").takeLast(6)
        return (clean.hashCode() and 0x7FFFFFFF) % 360f
    }

    /** 字节数组转十六进制大写字符串,超长截断 —— 广播数据展示用 */
    private fun bytesToHex(bytes: ByteArray, maxBytes: Int): String =
        bytes.take(maxBytes).joinToString("") { "%02X".format(it) }

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
        BluetoothClass.Device.Major.COMPUTER -> L10n.get(R.string.mgr_radar_class_computer)
        BluetoothClass.Device.Major.PHONE -> L10n.get(R.string.mgr_radar_class_phone)
        BluetoothClass.Device.Major.IMAGING -> L10n.get(R.string.mgr_radar_class_imaging)
        BluetoothClass.Device.Major.AUDIO_VIDEO -> L10n.get(R.string.mgr_radar_class_audio_video)
        BluetoothClass.Device.Major.WEARABLE -> L10n.get(R.string.mgr_radar_class_wearable)
        BluetoothClass.Device.Major.NETWORKING -> L10n.get(R.string.mgr_radar_class_network)
        BluetoothClass.Device.Major.HEALTH -> L10n.get(R.string.mgr_radar_class_health)
        BluetoothClass.Device.Major.PERIPHERAL -> L10n.get(R.string.mgr_radar_class_peripheral)
        BluetoothClass.Device.Major.TOY -> L10n.get(R.string.mgr_radar_class_toy)
        BluetoothClass.Device.Major.UNCATEGORIZED -> L10n.get(R.string.mgr_radar_class_uncategorized)
        else -> ""
    }

    @Suppress("DEPRECATION")
    private fun minorClassName(cls: BluetoothClass?): String {
        val minor = cls?.deviceClass ?: return ""
        val major = cls.majorDeviceClass
        return when (major) {
            BluetoothClass.Device.Major.PHONE -> when (minor and 0xFC) {
                BluetoothClass.Device.PHONE_SMART -> L10n.get(R.string.mgr_radar_minor_smartphone)
                BluetoothClass.Device.PHONE_CELLULAR -> L10n.get(R.string.mgr_radar_minor_cellular)
                BluetoothClass.Device.PHONE_CORDLESS -> L10n.get(R.string.mgr_radar_minor_cordless)
                BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY -> L10n.get(R.string.mgr_radar_minor_modem_gateway)
                else -> ""
            }
            BluetoothClass.Device.Major.COMPUTER -> when (minor and 0xFC) {
                BluetoothClass.Device.COMPUTER_DESKTOP -> L10n.get(R.string.mgr_radar_minor_desktop)
                BluetoothClass.Device.COMPUTER_LAPTOP -> L10n.get(R.string.mgr_radar_minor_laptop)
                BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA -> L10n.get(R.string.mgr_radar_minor_handheld)
                BluetoothClass.Device.COMPUTER_WEARABLE -> L10n.get(R.string.mgr_radar_minor_wearable_computer)
                else -> ""
            }
            BluetoothClass.Device.Major.AUDIO_VIDEO -> when (minor and 0xFC) {
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> L10n.get(R.string.mgr_radar_minor_headphones)
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> L10n.get(R.string.mgr_radar_minor_loudspeaker)
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> L10n.get(R.string.mgr_radar_minor_headset)
                BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> L10n.get(R.string.mgr_radar_minor_car_audio)
                else -> ""
            }
            BluetoothClass.Device.Major.WEARABLE -> when (minor and 0xFC) {
                BluetoothClass.Device.WEARABLE_WRIST_WATCH -> L10n.get(R.string.mgr_radar_minor_wrist_watch)
                BluetoothClass.Device.WEARABLE_GLASSES -> L10n.get(R.string.mgr_radar_minor_glasses)
                else -> ""
            }
            else -> ""
        }
    }

    private fun bondStateName(state: Int): String = when (state) {
        BluetoothDevice.BOND_BONDED -> L10n.get(R.string.mgr_radar_bond_bonded)
        BluetoothDevice.BOND_BONDING -> L10n.get(R.string.mgr_radar_bond_bonding)
        else -> L10n.get(R.string.mgr_radar_bond_none)
    }

    private fun linkTypeName(type: Int?): String = when (type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> L10n.get(R.string.mgr_radar_link_classic)
        BluetoothDevice.DEVICE_TYPE_LE -> L10n.get(R.string.mgr_radar_link_le)
        BluetoothDevice.DEVICE_TYPE_DUAL -> L10n.get(R.string.mgr_radar_link_dual)
        BluetoothDevice.DEVICE_TYPE_UNKNOWN -> L10n.get(R.string.mgr_radar_link_unknown)
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
        return OUI_VENDORS[oui] ?: L10n.get(R.string.mgr_radar_vendor_unknown)
    }

    companion object {
        /** 扫描相位:Ble 扫描与经典发现互斥(Android 框架层),分时轮换 */
        private const val PHASE_BLE = 0
        private const val PHASE_DISCOVERY = 1

        // ── 距离模型参数(与 RssiLocalizationEngine/档案页共用同一口径) ──
        /** WiFi:1m 参考功率 -40dBm,室内路径损耗指数 2.5 —— 近距离展开充分,远距收敛 100m */
        const val WIFI_REF_DBM = -40f
        const val WIFI_PATH_LOSS = 2.5f
        /**
         * 蓝牙/BLE:路径损耗指数 2.0(近场传播)。
         * 原先经典与 BLE 共用 -59dBm@1m + n=2.5,-70..-90dBm 的目标全部挤在
         * 0.3..5m 一小团 —— 雷达盘上看全部堆在圆心。分参考功率 + 降指数后,
         * -85dBm ≈ 30m 量级,与真实蓝牙感知距离吻合,盘面分布展开。
         */
        const val BT_PATH_LOSS = 2.0f
        /** 当前蓝牙路径损耗环境(设置页可切):自由空间 2.0 / 室外 2.2 / 室内 3.0(btrpa-scan 同款预设) */
        @Volatile
        var btEnvPathLoss: Float = BT_PATH_LOSS
        /** 经典蓝牙(Class 2 发射功率约 4dBm)1m 参考功率 */
        const val BT_CLASSIC_REF_DBM = -55f
        /** BLE 广播缺 TX Power 字段时的假定 1m 参考功率 */
        const val BLE_ASSUMED_REF_DBM = -59f

        /** BLE 相位窗口:一个相位内连续扫描时长 */
        private const val BLE_SCAN_WINDOW_MS = 10_000L
        /** 经典发现相位窗口:覆盖一轮完整 inquiry(~12s)加余量 */
        private const val DISCOVERY_WINDOW_MS = 13_000L
        /** 发现提前结束且窗口余量大于该值才重启一轮,避免拖长相位 */
        private const val DISCOVERY_RESTART_MIN_MS = 6_000L
        /** BLE 扫描失败后的重试退避,防止节拍内密集重试触发系统限流 */
        private const val BLE_FAIL_BACKOFF_MS = 2_000L

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
