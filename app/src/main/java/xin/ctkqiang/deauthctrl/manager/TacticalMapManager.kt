package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos
import kotlin.math.sin

/** 一个战术标记点 —— id 单调递增,删除/渲染都以 id 为键 */
data class TacticalMarker(
    val id: Long,
    val label: String,
    val lat: Double,
    val lon: Double,
    val createdMs: Long,
)

/**
 * 定位状态 —— UI 按四态提示:
 * 1. 未授权(permissionGranted=false)
 * 2. 系统定位关闭(gpsEnabled=false && networkEnabled=false)
 * 3. 等待首定位(!hasFix)
 * 4. 已有 fix(hasFix=true,fixAgeMs/accuracyM 可读)
 */
data class LocationStatus(
    val permissionGranted: Boolean = false,
    val fineGranted: Boolean = false,
    val gpsEnabled: Boolean = false,
    val networkEnabled: Boolean = false,
    val hasFix: Boolean = false,
    val fixAgeMs: Long = 0L,
    val accuracyM: Float = 0f,
)

/**
 * 战术地图数据源 —— GPS 定位 + 标记点管理 + 夜战滤镜
 *
 * 引用计数:战图与网图共享同一个 ViewModel/Manager。AnimatedContent 过渡期
 * 旧页面 onDispose 的 release() 不会摘掉新页面仍在使用的定位监听,归零才真正 stop。
 * [start]/[stop] 保留为 acquire/release 的别名,兼容仍按旧 API 调用的页面。
 */
class TacticalMapManager(context: Context) {

    private val appContext = context.applicationContext

    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location

    private val _markers = MutableStateFlow<List<TacticalMarker>>(emptyList())
    val markers: StateFlow<List<TacticalMarker>> = _markers

    /** 兼容 NetMapScreen 的旧布尔读数(仅表示 GPS provider 是否启用) */
    private val _gpsOn = MutableStateFlow(false)
    val gpsOn: StateFlow<Boolean> = _gpsOn

    private val _status = MutableStateFlow(LocationStatus())
    val status: StateFlow<LocationStatus> = _status

    /** 航向(0-360,低通平滑);无传感器或无有效值时 null,不伪造 */
    private val _heading = MutableStateFlow<Float?>(null)
    val heading: StateFlow<Float?> = _heading

    /** 在线瓦片是否可达 —— UI 显示「在线 / 仅缓存」 */
    private val _networkOnline = MutableStateFlow(true)
    val networkOnline: StateFlow<Boolean> = _networkOnline

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var consumers = 0
    private var registered = false
    private var networkCallbackRegistered = false
    private var nextMarkerId = 1L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val statusTicker = object : Runnable {
        override fun run() {
            publishStatus()
            if (registered) mainHandler.postDelayed(this, 1000)
        }
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            addLocation(loc)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) { publishStatus() }
        override fun onProviderDisabled(provider: String) { publishStatus() }
    }

    // ─── 航向传感器(复用雷达页的低通平滑/0-360 处理) ───
    private val sensorListener = object : SensorEventListener {
        private val rotationMatrix = FloatArray(9)
        private val deviceMatrix = FloatArray(9)
        private val orientation = FloatArray(3)
        private var smoothedHeading = 0f
        private var hasSample = false

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            // 屏幕朝向用户时航向指向正前方
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X, SensorManager.AXIS_Z,
                deviceMatrix,
            )
            SensorManager.getOrientation(deviceMatrix, orientation)
            val rawHeading = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
            if (!hasSample) {
                smoothedHeading = rawHeading
                hasSample = true
            } else {
                var delta = rawHeading - smoothedHeading
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                smoothedHeading = (smoothedHeading + delta * 0.25f + 360f) % 360f
            }
            _heading.value = smoothedHeading
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _networkOnline.value = true }
        override fun onLost(network: Network) { _networkOnline.value = currentNetworkAvailable() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _networkOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 引用计数生命周期
    // ══════════════════════════════════════════════════════════════════════

    @Synchronized
    fun acquire() {
        consumers++
        if (consumers == 1) startInternal()
    }

    @Synchronized
    fun release() {
        consumers = (consumers - 1).coerceAtLeast(0)
        if (consumers == 0) stopInternal()
    }

    /** 兼容旧调用:等价于 acquire() */
    fun start() = acquire()

    /** 兼容旧调用:等价于 release() */
    fun stop() = release()

    /** ViewModel onCleared 兜底:无论引用计数,立即停止 */
    @Synchronized
    fun forceStop() {
        consumers = 0
        stopInternal()
    }

    /** Lifecycle ON_RESUME / 权限返回:重查权限并重新注册(不改变引用计数) */
    @Synchronized
    fun reacquireIfActive() {
        if (consumers == 0) {
            publishStatus()
            return
        }
        if (!hasPermission()) {
            publishStatus()
            return
        }
        stopInternal(keepConsumers = true)
        startInternal()
    }

    private fun startInternal() {
        publishStatus()
        if (!hasPermission()) {
            _error.value = "未授权定位权限"
            return
        }
        _error.value = null
        // last-known 按 Location.time 取最新,不再「GPS 先到就算数」
        val providers = buildList {
            if (fineGranted()) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        providers.forEach { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }
                .getOrNull()
                ?.let { addLocation(it) }
        }

        if (fineGranted() && providerEnabled(LocationManager.GPS_PROVIDER)) {
            runCatching {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, listener)
            }.onFailure {
                // 不再 runCatching 吞掉:写进 error,UI 可诊断
                _error.value = "GPS 定位注册失败: ${it.message ?: it.javaClass.simpleName}"
            }
        }
        if (providerEnabled(LocationManager.NETWORK_PROVIDER)) {
            runCatching {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 5f, listener)
            }.onFailure {
                _error.value = "网络定位注册失败: ${it.message ?: it.javaClass.simpleName}"
            }
        }
        if (!fineGranted() && providerEnabled(LocationManager.GPS_PROVIDER)) {
            // 仅粗定位:GPS provider 需要 FINE,不能注册;只提示一次
            _error.value = "仅授予粗略定位 —— 使用网络定位,精度较低"
        }

        registerHeading()
        registerNetworkCallback()
        registered = true
        mainHandler.removeCallbacks(statusTicker)
        mainHandler.post(statusTicker)
        publishStatus()
    }

    private fun stopInternal(keepConsumers: Boolean = false) {
        registered = false
        mainHandler.removeCallbacks(statusTicker)
        runCatching { locationManager.removeUpdates(listener) }
        runCatching { sensorManager?.unregisterListener(sensorListener) }
        _heading.value = null
        if (!keepConsumers) {
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        publishStatus()
    }

    private fun registerHeading() {
        val sm = sensorManager ?: run {
            _heading.value = null
            return
        }
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: run {
            _heading.value = null
            return
        }
        runCatching { sm.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME) }
            .onFailure { _heading.value = null }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        val cm = connectivityManager ?: return
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
            _networkOnline.value = currentNetworkAvailable()
        }
    }

    @SuppressLint("MissingPermission")
    private fun currentNetworkAvailable(): Boolean = runCatching {
        val cm = connectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)

    private fun hasPermission(): Boolean = fineGranted() || coarseGranted()

    private fun fineGranted(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun coarseGranted(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun providerEnabled(provider: String): Boolean =
        runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)

    private fun publishStatus() {
        val fine = fineGranted()
        val coarse = coarseGranted()
        val permission = fine || coarse
        val gpsEnabled = permission && providerEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = permission && providerEnabled(LocationManager.NETWORK_PROVIDER)
        val loc = _location.value
        val age = loc?.let { (System.currentTimeMillis() - it.time).coerceAtLeast(0L) } ?: 0L
        val accuracy = if (loc?.hasAccuracy() == true) loc.accuracy else 0f
        _status.value = LocationStatus(
            permissionGranted = permission,
            fineGranted = fine,
            gpsEnabled = gpsEnabled,
            networkEnabled = networkEnabled,
            hasFix = loc != null,
            fixAgeMs = age,
            accuracyM = accuracy,
        )
        _gpsOn.value = gpsEnabled
    }

    private fun addLocation(loc: Location) {
        val current = _location.value
        // 只接受更新的 fix,避免网络定位旧点覆盖 GPS 新点
        if (current == null || loc.time > current.time) {
            _location.value = loc
        }
        publishStatus()
    }

    // ══════════════════════════════════════════════════════════════════════
    // 标记点
    // ══════════════════════════════════════════════════════════════════════

    /** 长按地图投放标记:默认标签「标记 %02d」,id 单调递增不重复 */
    fun addMarker(label: String, lat: Double, lon: Double) {
        val id = nextMarkerId++
        val fallback = "标记 %02d".format(id)
        _markers.value = (
            _markers.value + TacticalMarker(
                id = id,
                label = label.ifBlank { fallback },
                lat = lat,
                lon = lon,
                createdMs = System.currentTimeMillis(),
            )
            ).takeLast(50)
    }

    fun removeMarker(m: TacticalMarker) {
        _markers.value = _markers.value.filterNot { it.id == m.id }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 夜战滤镜 —— 可读性重写
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 夜战暗色滤镜:先降饱和(保留亮度层次),再做绿色通道增益与对比。
     * 与旧矩阵不同,道路(高亮度)仍明显亮于绿地,水面保持偏暗,不再压成同一档绿。
     * 未加载瓦片由 TilesOverlay 的 loadingBackgroundColor 单独暗色化,不套用该滤镜。
     */
    fun nightFilter(): ColorFilter {
        val saturation = ColorMatrix().apply { setSaturation(0.25f) }
        val greenTone = ColorMatrix(
            floatArrayOf(
                0.30f, 0.34f, 0.16f, 0f, -12f,
                0.24f, 0.92f, 0.20f, 0f, -10f,
                0.08f, 0.22f, 0.40f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        saturation.postConcat(greenTone)
        return ColorMatrixColorFilter(saturation)
    }
}

/**
 * 自机战术 overlay —— 单个稳定实例,不随定位 tick 重建。
 *
 * 绘制内容:
 * - 16dp 半对角线的青色战术菱形(带深色描边)
 * - 精度圈(仅 hasAccuracy && accuracy in 3..5000m,最小 8px)
 * - 航向线(优先 ROTATION_VECTOR;无传感器时回退 hasBearing && speed>0.5)
 * - 菱形旁「本机」标签
 *
 * 两者都没有航向数据时不画航向线 —— 不伪造方向。
 */
class TacticalSelfOverlay(
    context: Context,
    private val locationProvider: () -> Location?,
    private val headingProvider: () -> Float?,
) : Overlay() {

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCC45E0FF.toInt()
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xFF06121A.toInt()
    }
    private val accuracyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0x5545E0FF
    }
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFF45E0FF.toInt()
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8FFF8.toInt()
        textSize = 10f * scaledDensity
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        val loc = locationProvider() ?: return
        val point = Point()
        projection.toPixels(GeoPoint(loc.latitude, loc.longitude), point)
        val cx = point.x.toFloat()
        val cy = point.y.toFloat()

        // 精度圈:只在合理区间绘制,避免 0m/上万米时糊满屏
        if (loc.hasAccuracy() && loc.accuracy in 3f..5000f) {
            val radius = projection
                .metersToPixels(loc.accuracy, loc.latitude, projection.zoomLevel)
                .coerceAtLeast(8f)
            canvas.drawCircle(cx, cy, radius, accuracyPaint)
        }

        // 航向线:传感器优先,回退 GPS bearing;都没有就不画
        val heading = headingProvider()
            ?: if (loc.hasBearing() && loc.speed > 0.5f) loc.bearing else null
        if (heading != null) {
            val rad = Math.toRadians(heading.toDouble())
            val length = 26f * density
            canvas.drawLine(
                cx, cy,
                (cx + sin(rad).toFloat() * length),
                (cy - cos(rad).toFloat() * length),
                headingPaint,
            )
        }

        // 战术菱形(16dp 半对角线)
        val half = 16f * density
        val path = Path().apply {
            moveTo(cx, cy - half)
            lineTo(cx + half, cy)
            lineTo(cx, cy + half)
            lineTo(cx - half, cy)
            close()
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)

        // 「本机」标签
        canvas.drawText(
            "本机",
            cx + half + 4f * density,
            cy + labelPaint.textSize * 0.35f,
            labelPaint,
        )
    }
}
