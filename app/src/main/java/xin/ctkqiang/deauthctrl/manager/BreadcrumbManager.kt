package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * 足迹面包屑 —— GPS 自动记录行进轨迹(距离/时间双触发),
 * 落盘 JSON 行文件(崩溃不丢),支持逐步原路返回导航。
 */
class BreadcrumbManager(private val context: Context) {

    data class Waypoint(val lat: Double, val lon: Double, val timeMs: Long)

    data class State(
        val logging: Boolean = false,
        val waypoints: List<Waypoint> = emptyList(),
        val walkedMeters: Double = 0.0,
        val permissionMissing: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val file = File(context.getExternalFilesDir(null), "breadcrumbs.jsonl")
    private var lastFix: Waypoint? = null
    private var lastLogMs = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            val wp = Waypoint(loc.latitude, loc.longitude, System.currentTimeMillis())
            val s = _state.value
            val prev = s.waypoints.lastOrNull()
            val dist = prev?.let { NavMath.haversineMeters(it.lat, it.lon, wp.lat, wp.lon) } ?: 0.0
            // 距离 5 米 或 间隔 10 秒 才记录
            if (prev == null || dist >= 5.0 || System.currentTimeMillis() - lastLogMs >= 10_000) {
                lastLogMs = System.currentTimeMillis()
                file.appendText("%.7f,%.7f,%d\n".format(wp.lat, wp.lon, wp.timeMs))
                _state.update {
                    it.copy(
                        waypoints = it.waypoints + wp,
                        walkedMeters = it.walkedMeters + dist,
                    )
                }
            }
            lastFix = wp
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    fun start(): Boolean {
        val lm = this.lm ?: return false
        if (lm.getProvider(LocationManager.GPS_PROVIDER) == null) return false
        return runCatching {
            @Suppress("MissingPermission")
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4_000L, 5f, listener)
            _state.update { it.copy(logging = true, permissionMissing = false) }
            true
        }.getOrDefault(false)
    }

    fun stop() {
        runCatching { lm?.removeUpdates(listener) }
        _state.update { it.copy(logging = false) }
    }

    fun seedFromLastKnown() {
        runCatching {
            @Suppress("MissingPermission")
            val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) lastFix = Waypoint(loc.latitude, loc.longitude, System.currentTimeMillis())
        }
    }

    /** 原路返回:弹出最后一个足迹点(导航目标) */
    fun popLast(): Boolean {
        val s = _state.value
        if (s.waypoints.size <= 1) return false
        _state.update { it.copy(waypoints = it.waypoints.dropLast(1)) }
        return true
    }

    /** 相对最新足迹点的方位与距离 */
    fun backtrack(): Pair<Double, Double>? {
        val last = _state.value.waypoints.lastOrNull() ?: return null
        val fix = lastFix ?: return null
        val dist = NavMath.haversineMeters(fix.lat, fix.lon, last.lat, last.lon)
        val bearing = NavMath.bearingDegrees(fix.lat, fix.lon, last.lat, last.lon)
        return bearing to dist
    }

    fun clear() {
        stop()
        _state.value = State()
        lastFix = null
        file.delete()
    }
}
