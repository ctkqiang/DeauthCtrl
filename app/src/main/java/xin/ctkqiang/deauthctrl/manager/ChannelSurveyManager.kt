package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 单个 AP 的扫描快照 —— 所有字段直接取自 ScanResult,不做任何推测 */
data class ChanAp(
    val ssid: String,
    val bssid: String,
    val rssi: Int,             // ScanResult.level,单位 dBm
    val frequencyMhz: Int,     // ScanResult.frequency,单位 MHz
    val security: ChanSecurity,
)

/** 由 Beacon capabilities 字符串解析出的安全类型 */
enum class ChanSecurity(@StringRes val labelRes: Int) {
    OPEN(R.string.mgr_chsurvey_security_open),
    WEP(R.string.mgr_chsurvey_security_wep),
    WPA2(R.string.mgr_chsurvey_security_wpa2),
    WPA3(R.string.mgr_chsurvey_security_wpa3),
    OTHER(R.string.mgr_chsurvey_security_other),
}

/** 单信道聚合结果 —— 由 ScanResult 按信道归并生成 */
data class ChannelSurvey(
    val channel: Int,
    val frequencyMhz: Int,
    val aps: List<ChanAp> = emptyList(),
) {
    /** 该信道可见 AP 数(隐藏 SSID 不计入) */
    val apCount: Int get() = aps.size

    /** 最强 RSSI(dBm);无 AP 时为 null,界面显示 "--" */
    val strongestRssi: Int? get() = aps.maxOfOrNull { it.rssi }

    /** 平均 RSSI(dBm);无 AP 时为 null */
    val averageRssi: Int?
        get() = if (aps.isEmpty()) {
            null
        } else {
            (aps.sumOf { it.rssi }.toDouble() / aps.size).roundToInt()
        }

    /** 安全类型分布 */
    val securityCounts: SecurityDistribution
        get() = SecurityDistribution(
            open = aps.count { it.security == ChanSecurity.OPEN },
            wep = aps.count { it.security == ChanSecurity.WEP },
            wpa2 = aps.count { it.security == ChanSecurity.WPA2 },
            wpa3 = aps.count { it.security == ChanSecurity.WPA3 },
            other = aps.count { it.security == ChanSecurity.OTHER },
        )

    /** 拥堵分级:0-2 空闲 / 3-5 拥挤 / 6+ 严重 */
    val congestion: CongestionLevel get() = congestionOf(apCount)
}

/** 单信道安全类型计数 */
data class SecurityDistribution(
    val open: Int = 0,
    val wep: Int = 0,
    val wpa2: Int = 0,
    val wpa3: Int = 0,
    val other: Int = 0,
) {
    val total: Int get() = open + wep + wpa2 + wpa3 + other
}

/** 信道拥堵分级 —— 只由可见 AP 数决定,不涉及空口利用率 */
enum class CongestionLevel(@StringRes val labelRes: Int) {
    IDLE(R.string.mgr_chsurvey_congestion_idle),      // 0-2 个 AP
    CROWDED(R.string.mgr_chsurvey_congestion_crowded),   // 3-5 个 AP
    SEVERE(R.string.mgr_chsurvey_congestion_severe),    // 6+ 个 AP
}

/** AP 数 → 拥堵分级 */
fun congestionOf(apCount: Int): CongestionLevel = when {
    apCount <= 2 -> CongestionLevel.IDLE
    apCount <= 5 -> CongestionLevel.CROWDED
    else -> CongestionLevel.SEVERE
}

/** 频段 */
enum class WifiBand(val label: String) {
    BAND_24("2.4 GHz"),
    BAND_5("5 GHz"),
}

/** 一个频段的信道聚合结果 */
data class BandSurvey(
    val band: WifiBand,
    val channels: List<ChannelSurvey> = emptyList(),
) {
    val apTotal: Int get() = channels.sumOf { it.apCount }
}

/** 信道占用页面状态 —— 由 [ChannelSurveyManager] 的 StateFlow 透传 */
data class ChanSurveyState(
    val bands: List<BandSurvey> = emptyList(),
    val scanning: Boolean = false,
    val updatedAtMs: Long = 0L,
    val error: String? = null,
    /** startScan 返回 false:本轮读取的是系统缓存,数据可能滞后数秒 */
    val throttled: Boolean = false,
)

/** 5 GHz 常见信道(36-165 之间步进 4 的标准信道集合) */
private val FIVE_GHZ_CHANNELS = setOf(
    36, 40, 44, 48, 52, 56, 60, 64,
    100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144,
    149, 153, 157, 161, 165,
)

/**
 * WiFi 信道占用巡检管理器 —— 被动扫描结果的按信道聚合
 *
 * ## 数据来源与边界
 * 每 3 秒调用一次 [WifiManager.startScan],等待系统写入后读取
 * [WifiManager.scanResults],把可见 AP 按信道归并成 2.4 GHz / 5 GHz 两组:
 * AP 数、最强 RSSI、平均 RSSI、安全类型分布。所有数值都是系统的真实返回值,
 * 不插值、不模拟;读不到的位置由界面显示 "--"。
 *
 * 只能看到主动广播 Beacon 的 AP —— 隐藏 SSID 的 AP 不出现在 scanResults 中,
 * 因此不计入统计。信道占用表达的是「可见 AP 数」,不是实时频谱或空口利用率。
 *
 * ## 权限与开关
 * Android 9+ 要求授予 ACCESS_FINE_LOCATION 且打开系统定位总开关,
 * 否则 startScan 返回 false / scanResults 为空。权限已在应用网关统一申请,
 * 本类只检查并在不满足时给出明确 error,不主动请求。
 *
 * ## 节流
 * 系统对前台应用的 startScan 做节流(通常 3-5 秒一轮)。startScan 返回 false
 * 时继续读取缓存结果,并把 [ChanSurveyState.throttled] 置 true 供界面如实提示。
 */
class ChannelSurveyManager(private val context: Context) {

    private val _state = MutableStateFlow(ChanSurveyState())
    val state: StateFlow<ChanSurveyState> = _state

    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var scope: CoroutineScope? = null

    /** ACCESS_FINE_LOCATION 是否已授予(Android 9+ 扫描结果的硬前提) */
    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * 启动信道巡检(幂等:重复调用不会创建第二个扫描循环)。
     *
     * 启动后即使权限/定位暂时不满足也会保持循环:每轮重新检查,系统状态
     * 恢复后自动产出真实数据,无需重新进入页面。
     */
    fun start() {
        if (scope != null) return

        val wm = wifiManager
        if (wm == null) {
            _state.value = ChanSurveyState(error = L10n.get(R.string.mgr_chsurvey_error_no_service))
            return
        }

        _state.value = _state.value.copy(scanning = true, error = null, throttled = false)
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        s.launch { scanLoop(wm) }
    }

    /**
     * 停止巡检并保留最后一帧快照 —— 界面带「最近更新」时间戳,
     * 数值仍然是某一次真实扫描的结果,不是伪造的实时数据。
     */
    fun stop() {
        scope?.cancel()
        scope = null
        _state.value = _state.value.copy(scanning = false, throttled = false, error = null)
    }

    /**
     * 扫描主循环:每轮 startScan → 等待系统写入 → 聚合 scanResults。
     *
     * 权限 / 定位开关 / Wi-Fi 开关每轮都重新检查,用户中途改变系统状态时
     * 会立即反映到 error,并且继续重试。
     */
    @Suppress("MissingPermission")
    private suspend fun scanLoop(wm: WifiManager) {
        while (currentCoroutineContext().isActive) {
            val cycleStart = System.currentTimeMillis()

            val gate = precheck(wm)
            if (gate != null) {
                _state.value = _state.value.copy(scanning = true, error = gate, throttled = false)
                delay(SCAN_INTERVAL_MS)
                continue
            }

            // startScan 可能被系统节流返回 false;这不是错误,继续读缓存结果。
            // SecurityException / 其它异常单独归因,不把失败伪装成节流。
            var scanError: String? = null
            val started = runCatching { wm.startScan() }.getOrElse { t ->
                scanError = if (t is SecurityException) {
                    securityMessage()
                } else {
                    L10n.get(
                        R.string.mgr_chsurvey_scan_start_failed,
                        t.message ?: t.javaClass.simpleName,
                    )
                }
                false
            }

            // 等系统把新一轮扫描写入 scanResults;被节流时读到的是上一轮缓存
            delay(SCAN_WAIT_MS)

            val results = runCatching { wm.scanResults.orEmpty() }
                .onFailure { t ->
                    if (scanError == null) {
                        scanError = if (t is SecurityException) {
                            securityMessage()
                        } else {
                            L10n.get(
                                R.string.mgr_chsurvey_read_results_failed,
                                t.message ?: t.javaClass.simpleName,
                            )
                        }
                    }
                }
                .getOrNull()

            if (results != null && scanError == null) {
                _state.value = ChanSurveyState(
                    bands = aggregate(results),
                    scanning = true,
                    updatedAtMs = System.currentTimeMillis(),
                    error = null,
                    throttled = !started,
                )
            } else {
                // 扫描被拒绝 / 结果读取失败:保留上一帧真实快照与时间戳,只发布错误
                _state.value = _state.value.copy(
                    scanning = true,
                    error = scanError ?: L10n.get(R.string.mgr_chsurvey_no_results),
                    throttled = false,
                )
            }

            val elapsed = System.currentTimeMillis() - cycleStart
            delay((SCAN_INTERVAL_MS - elapsed).coerceAtLeast(0L))
        }
    }

    /** 每轮扫描前的真实前提检查;返回 null 表示可以扫描 */
    private fun precheck(wm: WifiManager): String? = when {
        !hasPermission -> L10n.get(R.string.mgr_chsurvey_error_permission)
        !locationEnabled() -> L10n.get(R.string.mgr_chsurvey_error_location_off)
        !runCatching { wm.isWifiEnabled }.getOrDefault(false) ->
            L10n.get(R.string.mgr_chsurvey_error_wifi_off)
        else -> null
    }

    /**
     * 把一帧 scanResults 聚合成两个频段。
     *
     * - 隐藏 SSID(SSID 为空串)不计入;同一 BSSID 只算一个 AP。
     * - 2.4 GHz 固定输出信道 1-14(含 0 AP 的信道),5 GHz 只输出有 AP 的信道。
     * - 6 GHz 与非常用 5 GHz 信道不在本页统计范围内。
     */
    @Suppress("MissingPermission")
    private fun aggregate(results: List<ScanResult>): List<BandSurvey> {
        val grouped = HashMap<WifiBand, HashMap<Int, MutableList<ChanAp>>>()
        results
            .filter { it.SSID?.isNotBlank() == true }
            .distinctBy { it.BSSID }
            .forEach { r ->
                val band = bandOf(r.frequency) ?: return@forEach
                val channel = channelOf(r.frequency) ?: return@forEach
                if (band == WifiBand.BAND_5 && channel !in FIVE_GHZ_CHANNELS) return@forEach

                val ap = ChanAp(
                    ssid = r.SSID,
                    bssid = r.BSSID ?: "--",
                    rssi = r.level,
                    frequencyMhz = r.frequency,
                    security = classifySecurity(r.capabilities),
                )
                grouped.getOrPut(band) { HashMap() }
                    .getOrPut(channel) { ArrayList() }
                    .add(ap)
            }

        val band24 = BandSurvey(
            band = WifiBand.BAND_24,
            channels = (1..14).map { ch ->
                ChannelSurvey(
                    channel = ch,
                    frequencyMhz = frequencyOfChannel(ch),
                    aps = grouped[WifiBand.BAND_24]?.get(ch)
                        ?.sortedByDescending { it.rssi }
                        .orEmpty(),
                )
            },
        )

        val map5 = grouped[WifiBand.BAND_5].orEmpty()
        val band5 = BandSurvey(
            band = WifiBand.BAND_5,
            channels = map5.keys.sorted().map { ch ->
                ChannelSurvey(
                    channel = ch,
                    frequencyMhz = frequencyOfChannel(ch),
                    aps = map5[ch].orEmpty().sortedByDescending { it.rssi },
                )
            },
        )

        return listOf(band24, band5)
    }

    /** 频率(MHz) → 频段 */
    private fun bandOf(frequencyMhz: Int): WifiBand? = when {
        frequencyMhz in 2400..2500 -> WifiBand.BAND_24
        frequencyMhz in 4900..5900 -> WifiBand.BAND_5
        else -> null
    }

    /**
     * 频率(MHz) → 信道号。
     *
     * 2.4 GHz:2412 + 5 × (n − 1);信道 14 的实际中心频率为 2484 MHz。
     * 5 GHz:5000 + 5 × n(n = 36..165),例:信道 36 = 5180 MHz。
     * 未知频率返回 null,不猜测。
     */
    private fun channelOf(frequencyMhz: Int): Int? = when {
        frequencyMhz == 2484 -> 14
        frequencyMhz in 2412..2472 -> (frequencyMhz - 2412) / 5 + 1
        frequencyMhz in 5000..5900 -> (frequencyMhz - 5000) / 5
        else -> null
    }

    /** 信道号 → 中心频率(MHz),与 [channelOf] 互逆 */
    private fun frequencyOfChannel(channel: Int): Int =
        if (channel <= 14) {
            if (channel == 14) 2484 else 2412 + (channel - 1) * 5
        } else {
            5000 + channel * 5
        }

    /**
     * Beacon capabilities → 安全类型。
     *
     * WPA3/SAE 优先判断;WPA2/WPA3 混合模式记为 WPA3;OWE 是加密开放网络,
     * 与 WPA1 等无法归入四类的都记入「其他」;WEP 单独计数;其余视为开放。
     */
    private fun classifySecurity(capabilities: String): ChanSecurity = when {
        capabilities.contains("SAE", ignoreCase = true) ||
            capabilities.contains("WPA3", ignoreCase = true) -> ChanSecurity.WPA3

        capabilities.contains("OWE", ignoreCase = true) -> ChanSecurity.OTHER

        capabilities.contains("WPA2", ignoreCase = true) ||
            capabilities.contains("RSN", ignoreCase = true) -> ChanSecurity.WPA2

        capabilities.contains("WEP", ignoreCase = true) -> ChanSecurity.WEP

        capabilities.contains("WPA", ignoreCase = true) -> ChanSecurity.OTHER

        else -> ChanSecurity.OPEN
    }

    /** 读取定位总开关的真实状态(API 28+ 用总开关,旧版本用 Provider) */
    private fun locationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm.isLocationEnabled
            } else {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        }.getOrDefault(false)
    }

    /** SecurityException 精确归因:列出真实缺失的权限名 */
    private fun securityMessage(): String {
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add("ACCESS_FINE_LOCATION")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add("NEARBY_WIFI_DEVICES")
            }
        }
        return if (missing.isEmpty()) {
            L10n.get(R.string.mgr_chsurvey_rejected_unknown)
        } else {
            L10n.get(R.string.mgr_chsurvey_rejected_missing, missing.joinToString(" / "))
        }
    }

    companion object {
        /** 扫描节拍:3 秒一轮,与系统前台节流的上限匹配 */
        const val SCAN_INTERVAL_MS = 3_000L

        /** startScan 之后等待系统写入 scanResults 的时间 */
        const val SCAN_WAIT_MS = 1_200L
    }
}
