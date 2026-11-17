package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.RadarManager
import xin.ctkqiang.deauthctrl.manager.RadarTarget

/** 一次新目标探测事件 —— UI 收到后触发触觉警报 */
data class RadarContactEvent(val timeMs: Long, val count: Int, val names: List<String>)

/**
 * 设备雷达 ViewModel
 *
 * 职责：
 * 1. 管理 WiFi/蓝牙扫描（RadarManager），每 500ms 轮询刷新目标表
 * 2. 真实罗盘航向 —— 旋转矢量传感器（陀螺仪+地磁+加速度计融合），
 *    输出经低通平滑的航向/俯仰/横滚角；扫描盘随真机转动实时旋转
 * 3. 新目标探测事件流 —— 首次发现某设备时发布 [RadarContactEvent]，
 *    由 UI 层决定是否触发触觉警报（受设置页开关控制）
 */
class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = RadarManager(application)
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _targets = MutableStateFlow<List<RadarTarget>>(emptyList())
    val targets: StateFlow<List<RadarTarget>> = _targets

    /** 航向角 0-360°（0=正北）—— 磁北为基准，有定位时修正为真北 */
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading

    /** 俯仰角 —— 屏幕立起为正 */
    private val _pitch = MutableStateFlow(0f)
    val pitch: StateFlow<Float> = _pitch

    /** 横滚角 —— 右倾为正 */
    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll

    /** 罗盘传感器是否在线 */
    private val _compassOnline = MutableStateFlow(false)
    val compassOnline: StateFlow<Boolean> = _compassOnline

    /** 北向基准：磁北 / 真北 */
    private val _northRef = MutableStateFlow("磁北")
    val northRef: StateFlow<String> = _northRef

    /** 新目标探测事件（一次性事件，timeMs 变化即代表新一轮） */
    private val _contact = MutableStateFlow<RadarContactEvent?>(null)
    val contact: StateFlow<RadarContactEvent?> = _contact

    private var pollJob: Job? = null
    private var sensorRegistered = false

    // 真北修正用磁偏角（度）；无定位时保持磁北
    @Volatile private var declinationDeg = 0f

    // ─── 罗盘传感器融合 ────────────────────────────────────────────────────────

    private val sensorListener = object : SensorEventListener {
        private val rotationMatrix = FloatArray(9)
        private val deviceMatrix = FloatArray(9)
        private val orientation = FloatArray(3)
        private var smoothedHeading = 0f
        private var hasSample = false

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            // 重映射到设备坐标：绕 X 轴 +90°，让屏幕朝向用户时航向指向正前方
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X, SensorManager.AXIS_Z,
                deviceMatrix,
            )
            SensorManager.getOrientation(deviceMatrix, orientation)
            val rawHeading = ((Math.toDegrees(orientation[0].toDouble()) + 360f) % 360f).toFloat()
            val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()

            // 航向低通平滑 + 0/360 跨越走最短弧，避免北向抖动
            if (!hasSample) { smoothedHeading = rawHeading; hasSample = true }
            else {
                var delta = rawHeading - smoothedHeading
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                smoothedHeading = (smoothedHeading + delta * 0.25f + 360f) % 360f
            }
            _heading.value = (smoothedHeading - declinationDeg + 360f) % 360f
            _pitch.value = pitch
            _roll.value = roll
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /** 启动罗盘传感器 —— 雷达页进入时调用；离开时 [stopCompass] */
    @Suppress("DEPRECATION")
    fun startCompass() {
        if (sensorRegistered) return
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: run {
            _compassOnline.value = false
            return
        }
        try {
            // 有定位时用真实磁偏角修正到真北（GeomagneticField 需要 WGS84 坐标）
            updateDeclination()
            sensorRegistered = sensorManager.registerListener(
                sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME,
            )
            _compassOnline.value = sensorRegistered
        } catch (_: Exception) {
            _compassOnline.value = false
        }
    }

    /** 停止罗盘传感器 */
    fun stopCompass() {
        if (!sensorRegistered) return
        try { sensorManager.unregisterListener(sensorListener) } catch (_: Exception) {}
        sensorRegistered = false
        _compassOnline.value = false
    }

    /** 从最近一次定位推算当地磁偏角，把磁北修正为真北 */
    @Suppress("MissingPermission")
    private fun updateDeclination() {
        try {
            val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val loc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: return
            declinationDeg = GeomagneticField(
                loc.latitude.toFloat(), loc.longitude.toFloat(), loc.altitude.toFloat(),
                System.currentTimeMillis(),
            ).declination
            _northRef.value = "真北"
        } catch (_: Exception) { _northRef.value = "磁北" }
    }

    // ─── 扫描与轮询 ───────────────────────────────────────────────────────────

    /** 启动雷达扫描 + 后台轮询 + 新目标探测 */
    fun start() {
        manager.start()
        _isRunning.value = true
        pollJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val list = manager.targets.values.toList()
                _targets.value = list
                val fresh = manager.drainNewContacts()
                if (fresh.isNotEmpty()) {
                    val names = list.filter { it.id in fresh }.map { it.name }
                    _contact.value = RadarContactEvent(System.currentTimeMillis(), fresh.size, names)
                }
                manager.onTick()
                delay(500)
            }
        }
    }

    /** 停止雷达扫描 */
    fun stop() {
        manager.stop()
        pollJob?.cancel()
        _isRunning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopCompass()
        manager.stop()
        pollJob?.cancel()
    }
}
