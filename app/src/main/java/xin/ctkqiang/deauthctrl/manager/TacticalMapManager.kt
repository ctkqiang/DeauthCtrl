package xin.ctkqiang.deauthctrl.manager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 一个战术标记点 */
data class TacticalMarker(
    val label: String,
    val lat: Double,
    val lon: Double,
    val createdMs: Long,
)

/**
 * 战术地图数据源 —— GPS 定位 + 标记点管理
 *
 * 渲染层使用 osmdroid（OpenStreetMap 开源库）。本类只负责：
 * 1. 订阅系统定位（GPS / 网络混合，最新一次已知位置即时可用）；
 * 2. 维护作战标记点列表（长按地图投放）。
 */
class TacticalMapManager(context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location

    private val _markers = MutableStateFlow<List<TacticalMarker>>(emptyList())
    val markers: StateFlow<List<TacticalMarker>> = _markers

    private val _gpsOn = MutableStateFlow(false)
    val gpsOn: StateFlow<Boolean> = _gpsOn

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            _location.value = loc
            _gpsOn.value = true
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) { _gpsOn.value = true }
        override fun onProviderDisabled(provider: String) { _gpsOn.value = false }
    }

    /** 开始定位：先取最后已知位置，再注册更新 */
    @SuppressLint("MissingPermission")
    fun start() {
        runCatching {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.let { _location.value = it }
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?.let { if (_location.value == null) _location.value = it }
        }
        runCatching {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, listener)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 5f, listener)
            _gpsOn.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }
    }

    fun stop() {
        runCatching { locationManager.removeUpdates(listener) }
    }

    /** 长按地图投放标记 */
    fun addMarker(label: String, lat: Double, lon: Double) {
        _markers.value = (_markers.value + TacticalMarker(label.ifBlank { "标记 ${_markers.value.size + 1}" }, lat, lon, System.currentTimeMillis())).takeLast(50)
    }

    fun removeMarker(m: TacticalMarker) {
        _markers.value = _markers.value.filterNot { it.createdMs == m.createdMs }
    }

    /** 夜战暗色滤镜 —— 地图瓦片压低亮度并染上军绿色调 */
    fun nightFilter(): ColorFilter {
        // 亮度降至 45%，绿色通道保留更多：像增强管荧光屏
        val matrix = ColorMatrix(
            floatArrayOf(
                0.10f, 0.10f, 0.10f, 0f, 4f,
                0.12f, 0.38f, 0.12f, 0f, 6f,
                0.06f, 0.06f, 0.20f, 0f, 4f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        return ColorMatrixColorFilter(matrix)
    }
}
