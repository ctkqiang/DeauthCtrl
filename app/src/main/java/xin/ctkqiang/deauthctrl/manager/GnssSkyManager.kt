package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 单颗 GNSS 卫星 —— 所有字段均为系统 GnssStatus 回调的真实上报值
 *
 * 方位角/仰角/CN0 不做任何平滑、插值或模拟;系统回报无效值(CN0 为 0 等)
 * 时置 null,由界面显示 "--",绝不编造。
 */
data class GnssSat(
    val constellation: Int,        // GnssStatus.CONSTELLATION_* 原始类型
    val constellationName: String, // 中文星座名
    val svid: Int,                 // 卫星编号(PRN / 槽位号)
    val cn0DbHz: Float?,           // 载噪比;null = 系统未给出有效值
    val azimuthDeg: Float,         // 方位角,正北 0°,顺时针 0-360
    val elevationDeg: Float,       // 仰角,地平线 0°,天顶 90°
    val usedInFix: Boolean,        // 是否参与本次定位解算
)

/** GNSS 天图页面状态 —— 由 [GnssSkyManager] 的 StateFlow 透传 */
data class GnssSkyState(
    val satellites: List<GnssSat> = emptyList(),
    val fixCount: Int = 0,
    val updatedAtMs: Long = 0L,
    val gpsEnabled: Boolean = false,
    val error: String? = null,
)

/**
 * GNSS 星座天图管理器 —— LocationManager.registerGnssStatusCallback 的真实卫星数据
 *
 * - 回调里逐颗读取 getConstellationType / getSvid / getCn0DbHz /
 *   getAzimuthDegrees / getElevationDegrees / usedInFix,直接组 List 发射;
 *   卫星数量少(通常 10-40 颗),无需节流或缓存。
 * - 定位总开关没有可订阅的广播面,用 2 秒轮询 isLocationEnabled(API 28+)
 *   或 GPS Provider(API 24-27)得到真实开关状态。
 * - 需要 ACCESS_FINE_LOCATION(应用网关已统一申请);本类只检查、不请求。
 * - Android 7.0(API 24)以下无 GNSS 状态回调,start() 直接给出明确错误。
 */
class GnssSkyManager(private val context: Context) {

    private val _state = MutableStateFlow(GnssSkyState())
    val state: StateFlow<GnssSkyState> = _state

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var callback: GnssStatus.Callback? = null
    private var scope: CoroutineScope? = null

    /** 注册回调失败的原因;轮询定位开关时不能被误清除 */
    private var callbackError: String? = null

    /** 最近一次回调星数为空的原因(室内/遮挡);有星后自动清除 */
    private var lastEmptyReason: String? = null

    init {
        // 首帧状态条也显示真实定位开关,避免页面启动前误报「定位 关」
        locationManager?.let { lm ->
            _state.value = GnssSkyState(gpsEnabled = gpsSwitchOn(lm))
        }
    }

    /** ACCESS_FINE_LOCATION 是否已授予 */
    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /** 注册 GNSS 状态回调(幂等:重复调用不会重复注册) */
    fun start() {
        if (scope != null) return

        if (!hasPermission) {
            _state.value = GnssSkyState(
                gpsEnabled = locationManager?.let { gpsSwitchOn(it) } ?: false,
                error = L10n.get(R.string.mgr_gnss_error_permission),
            )
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            _state.value = GnssSkyState(
                gpsEnabled = locationManager?.let { gpsSwitchOn(it) } ?: false,
                error = L10n.get(R.string.mgr_gnss_error_legacy),
            )
            return
        }
        val lm = locationManager
        if (lm == null) {
            _state.value = GnssSkyState(error = L10n.get(R.string.mgr_gnss_no_service))
            return
        }

        callbackError = null
        val gpsOn = gpsSwitchOn(lm)
        _state.value = GnssSkyState(
            gpsEnabled = gpsOn,
            error = if (gpsOn) null else L10n.get(R.string.mgr_gnss_error_gps_off),
        )

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        registerCallback(lm)

        // 定位总开关的实时状态:2 秒轮询一次,开关变化时同步到 UI
        s.launch {
            while (isActive) {
                val on = gpsSwitchOn(lm)
                val cur = _state.value
                val newError = callbackError ?: if (on) null else L10n.get(R.string.mgr_gnss_error_gps_off)
                if (cur.gpsEnabled != on || cur.error != newError) {
                    _state.value = cur.copy(gpsEnabled = on, error = newError)
                }
                delay(2_000L)
            }
        }
    }

    /** 注销回调并停止轮询;再次进入页面时 start() 会重新注册 */
    fun stop() {
        scope?.cancel()
        scope = null

        val lm = locationManager
        val cb = callback
        if (lm != null && cb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { lm.unregisterGnssStatusCallback(cb) }
        }
        callback = null
        callbackError = null

        // 清空卫星列表:避免下次进入页面时先闪出一帧过期的天图数据
        _state.value = GnssSkyState(gpsEnabled = _state.value.gpsEnabled)
    }

    /**
     * 注册系统 GNSS 状态回调
     *
     * 回调绑定主线程 Handler:onSatelliteStatusChanged 直接更新 StateFlow,
     * 保证 UI 读取到的一直是最后一次系统上报的整帧卫星列表(不拼接旧数据)。
     */
    @Suppress("MissingPermission")
    private fun registerCallback(lm: LocationManager) {
        val cb = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val on = gpsSwitchOn(lm)
                val sats = parse(status)
                if (sats.isEmpty()) {
                    lastEmptyReason = L10n.get(R.string.mgr_gnss_empty_reason)
                } else {
                    lastEmptyReason = null
                }
                _state.value = GnssSkyState(
                    satellites = sats,
                    fixCount = sats.count { it.usedInFix },
                    updatedAtMs = System.currentTimeMillis(),
                    gpsEnabled = on,
                    error = when {
                        !on -> L10n.get(R.string.mgr_gnss_error_gps_off)
                        callbackError != null -> callbackError
                        sats.isEmpty() -> lastEmptyReason
                        else -> null
                    },
                )
            }
        }
        callback = cb

        val ok = runCatching {
            // 双参重载(带 Handler)自 API 30 才存在;30 以下用单参版本(系统回调线程)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.registerGnssStatusCallback(cb, Handler(Looper.getMainLooper()))
            } else {
                @Suppress("DEPRECATION")
                lm.registerGnssStatusCallback(cb)
            }
        }.getOrElse { t ->
            callbackError = L10n.get(
                R.string.mgr_gnss_register_failed,
                t.message ?: t.javaClass.simpleName,
            )
            false
        }
        if (!ok) {
            if (callbackError == null) {
                callbackError = L10n.get(R.string.mgr_gnss_register_rejected)
            }
            _state.value = _state.value.copy(error = callbackError)
        }
    }

    /** 读取定位总开关的真实状态(API 28+ 用总开关,旧版本用 GPS Provider) */
    @Suppress("MissingPermission")
    private fun gpsSwitchOn(lm: LocationManager): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }
    }.getOrDefault(false)

    /** 把一帧 GnssStatus 转成不可变卫星列表;按「参与解算 → 星座 → 编号」排序 */
    private fun parse(status: GnssStatus): List<GnssSat> {
        val count = status.satelliteCount
        val out = ArrayList<GnssSat>(count)
        for (i in 0 until count) {
            val type = status.getConstellationType(i)
            val cn0 = status.getCn0DbHz(i)
            out += GnssSat(
                constellation = type,
                constellationName = constellationName(type),
                svid = status.getSvid(i),
                cn0DbHz = if (cn0.isFinite() && cn0 > 0f) cn0 else null,
                azimuthDeg = status.getAzimuthDegrees(i),
                elevationDeg = status.getElevationDegrees(i),
                usedInFix = status.usedInFix(i),
            )
        }
        return out.sortedWith(
            compareByDescending<GnssSat> { if (it.usedInFix) 1 else 0 }
                .thenBy { constellationRank(it.constellation) }
                .thenBy { it.svid },
        )
    }
}

/** GnssStatus 星座类型 → 中文名 */
private fun constellationName(type: Int): String = when (type) {
    GnssStatus.CONSTELLATION_GPS -> "GPS"
    GnssStatus.CONSTELLATION_GALILEO -> "伽利略"
    GnssStatus.CONSTELLATION_GLONASS -> "格洛纳斯"
    GnssStatus.CONSTELLATION_BEIDOU -> "北斗"
    GnssStatus.CONSTELLATION_QZSS -> "准天顶"
    GnssStatus.CONSTELLATION_SBAS -> "星基增强"
    // GnssStatus.CONSTELLATION_IRNSS 自 API 29 才公开,数值固定为 7;
    // 用字面量避免低版本设备解析常量字段
    7 -> "印度区域"
    else -> "未知"
}

/** 列表排序用星座序号(GPS → 伽利略 → 格洛纳斯 → 北斗 → 准天顶 → SBAS → IRNSS → 未知) */
private fun constellationRank(type: Int): Int = when (type) {
    GnssStatus.CONSTELLATION_GPS -> 0
    GnssStatus.CONSTELLATION_GALILEO -> 1
    GnssStatus.CONSTELLATION_GLONASS -> 2
    GnssStatus.CONSTELLATION_BEIDOU -> 3
    GnssStatus.CONSTELLATION_QZSS -> 4
    GnssStatus.CONSTELLATION_SBAS -> 5
    7 -> 6 // IRNSS
    else -> 7
}
