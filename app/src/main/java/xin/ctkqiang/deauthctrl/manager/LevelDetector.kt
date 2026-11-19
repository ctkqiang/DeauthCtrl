package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 全息水平仪 —— 重力矢量 → 横滚 / 俯仰角
 *
 * 优先用 TYPE_GRAVITY(软件融合后的重力,抖动小),缺失时回退
 * TYPE_ACCELEROMETER。低通滤波 + 校准零点,角度为设备本地姿态
 * 与水平面的夹角,传感器分辨率以设备实际硬件为准。
 */
class LevelDetector(context: Context) {

    data class LevelState(
        val running: Boolean = false,
        val rollDeg: Float = 0f,
        val pitchDeg: Float = 0f,
        val locked: Boolean = false,
        val calibrated: Boolean = false,
    )

    private val sensorManager = context.applicationContext.getSystemService(SensorManager::class.java)
    private val _state = MutableStateFlow(LevelState())
    val state: StateFlow<LevelState> = _state

    private var offsetRoll = 0f
    private var offsetPitch = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val roll = Math.toDegrees(atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat() - offsetRoll
            val pitch = Math.toDegrees(atan2(y.toDouble(), sqrt((x * x + z * z).toDouble()))).toFloat() - offsetPitch
            val s = _state.value
            // 低通滤波:新读数占 30%,平滑抖动
            val fRoll = s.rollDeg * 0.7f + roll * 0.3f
            val fPitch = s.pitchDeg * 0.7f + pitch * 0.3f
            val locked = kotlin.math.abs(fRoll) < 0.35f && kotlin.math.abs(fPitch) < 0.35f
            _state.value = s.copy(rollDeg = fRoll, pitchDeg = fPitch, locked = locked)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        if (_state.value.running) return
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        _state.value = _state.value.copy(running = true)
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
        _state.value = _state.value.copy(running = false)
    }

    /** 校准:把当前位置视为水平零点 */
    fun calibrate() {
        val s = _state.value
        offsetRoll = s.rollDeg
        offsetPitch = s.pitchDeg
        _state.value = s.copy(calibrated = true, rollDeg = 0f, pitchDeg = 0f)
    }
}
