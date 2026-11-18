package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 默认步长(米) —— 仅为成人均值,页面允许 0.50-1.00m 自校准 */
const val PDR_DEFAULT_STEP_LEN_M = 0.75f

/** 步长可调下限(米) */
const val PDR_MIN_STEP_LEN_M = 0.50f

/** 步长可调上限(米) */
const val PDR_MAX_STEP_LEN_M = 1.00f

/**
 * 步测死推算状态
 *
 * 坐标系:x = 东,y = 北(单位米),起点固定为 (0,0)。
 * [trail] 保存每一步推进后的世界坐标,最多 2000 点。
 * 所有读数均来自硬件计步器与旋转矢量传感器;读不到时为 null / 空,
 * 绝不用估算值冒充实测。
 */
data class PdrState(
    /** 是否正在计步(暂停 / 离页后为 false,已有轨迹保留) */
    val running: Boolean = false,
    /** 本程累计步数(来自硬件计步器,非加速度计猜测) */
    val steps: Long = 0L,
    /** 本程累计距离(米)= 每步步长累加,步长调参只影响后续步伐 */
    val distanceM: Float = 0f,
    /** 当前航向(0-360°,磁北基准,低通平滑);无旋转矢量时 null */
    val headingDeg: Float? = null,
    /** 死推算轨迹(世界坐标,米,东/北),最多 2000 点 */
    val trail: List<Pair<Float, Float>> = emptyList(),
    /** 从当前位置指回起点的方位角(0-360°);原点处无方位,为 null */
    val homeBearingDeg: Float? = null,
    /** 当前位置到起点的直线距离(米,死推算值,非 GPS 测量) */
    val homeDistanceM: Float? = null,
    /** 当前步长设置(米) */
    val stepLenM: Float = PDR_DEFAULT_STEP_LEN_M,
    /** 本机是否存在计步传感器(TYPE_STEP_COUNTER 或 TYPE_STEP_DETECTOR) */
    val hasStepSensor: Boolean = false,
    /** 实际使用的计步传感器名称(系统返回值) */
    val stepSensorName: String = "",
    /** Android 10+ 读取计步传感器的「身体活动」权限是否已授予 */
    val stepPermissionGranted: Boolean = true,
    /** 本机是否存在旋转矢量传感器(航向来源) */
    val hasHeadingSensor: Boolean = false,
    /** 硬失败原因;硬件缺失 / 未授权属于可用性,不写在这里 */
    val error: String? = null,
)

/**
 * 步测死推算 + 面包屑归航
 *
 * ## 原理
 * 只使用两类真实传感器:
 * - TYPE_STEP_COUNTER:累计步数,取相邻事件差值得出新增步数;
 *   设备无此传感器时回退 TYPE_STEP_DETECTOR(每个事件 = 一步)。
 * - TYPE_ROTATION_VECTOR:航向。重映射与雷达页完全一致:
 *   getRotationMatrixFromVector → remapCoordinateSystem(AXIS_X, AXIS_Z)
 *   → getOrientation → heading = (degrees(azimuth) + 360) % 360。
 *
 * 每一步按当前航向推进:x += stepLen * sin(航向), y += stepLen * cos(航向)。
 * 航向低通使用最短弧、alpha 0.25,避免 0/360 附近抖动。
 *
 * ## 诚实边界
 * 死推算误差随距离累积(百米级通常 5-10%),转弯、手持晃动会放大;
 * 无计步 / 无航向 / 未授权时只显示「不可用」,不编造位移。
 *
 * ## 生命周期
 * [attach] / [detach] 对应页面进入 / 离开:attach 注册航向传感器(暂停时
 * 罗盘仍实时),detach 无条件断开工步与航向。计步器只在 [start] 后注册,
 * [pause] 立即摘下,避免把暂停期间的步数算进本程。
 */
class PdrHomingManager(context: Context) {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepSensor = stepCounterSensor ?: stepDetectorSensor
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _state = MutableStateFlow(
        PdrState(
            stepLenM = PDR_DEFAULT_STEP_LEN_M,
            hasStepSensor = stepSensor != null,
            stepSensorName = stepSensor?.name.orEmpty(),
            stepPermissionGranted = hasActivityRecognitionPermission(),
            hasHeadingSensor = rotationSensor != null,
        ),
    )
    val state: StateFlow<PdrState> = _state

    // ─── 死推算位置(世界坐标,米;东 = +x,北 = +y) ────────────────────────
    private var posX = 0f
    private var posY = 0f
    private var stepCount = 0L
    private var distanceAccum = 0f

    /** 是否已经开过一程(用于区分「全新启动」与「暂停后续算」) */
    private var sessionStarted = false

    // ─── 传感器生命周期 ────────────────────────────────────────────────────
    private var attached = false
    private var headingRegistered = false
    private var stepRegistered = false

    /** 计步器重新注册后的首个事件只做基线,不计入本程 */
    private var awaitingStepBaseline = true
    private var lastStepCounter = Float.NaN

    // ─── 航向平滑 ──────────────────────────────────────────────────────────
    private var smoothedHeading = 0f
    private var hasHeadingSample = false

    /** 供步进推进使用的最新平滑航向(不依赖状态流节流后的值) */
    private var currentHeadingDeg: Float? = null
    private var lastHeadingEmitMs = 0L

    // ══════════════════════════════════════════════════════════════════════
    // 传感器监听
    // ══════════════════════════════════════════════════════════════════════

    /** 航向监听 —— 重映射与低通照抄雷达页 */
    private val headingListener = object : SensorEventListener {
        private val rotationMatrix = FloatArray(9)
        private val deviceMatrix = FloatArray(9)
        private val orientation = FloatArray(3)

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR || event.values.size < 3) return
            runCatching {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                // 重映射到设备坐标:屏幕朝向用户时航向指向正前方
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_X, SensorManager.AXIS_Z,
                    deviceMatrix,
                )
                SensorManager.getOrientation(deviceMatrix, orientation)
                val rawHeading = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()

                // 低通平滑:0/360 跨越走最短弧,alpha 0.25
                if (!hasHeadingSample) {
                    smoothedHeading = rawHeading
                    hasHeadingSample = true
                } else {
                    var delta = rawHeading - smoothedHeading
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    smoothedHeading = (smoothedHeading + delta * HEADING_ALPHA + 360f) % 360f
                }
                currentHeadingDeg = smoothedHeading

                // 状态流按 80ms 节流:50Hz 传感器不应让整页 50Hz 重组
                val now = System.currentTimeMillis()
                if (_state.value.headingDeg == null || now - lastHeadingEmitMs >= HEADING_EMIT_INTERVAL_MS) {
                    lastHeadingEmitMs = now
                    _state.value = _state.value.copy(headingDeg = smoothedHeading)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /** 计步监听 —— 累计计步器取差值,步骤检测器每事件一步 */
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.isEmpty()) return
            when (event.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> onCounterEvent(event.values[0])
                Sensor.TYPE_STEP_DETECTOR -> if (event.values[0] >= 0.5f) onStepDetected()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // ══════════════════════════════════════════════════════════════════════
    // 对外生命周期
    // ══════════════════════════════════════════════════════════════════════

    /** 页面进入:注册航向传感器(暂停态也保持罗盘实时) */
    @Synchronized
    fun attach() {
        if (attached) return
        attached = true
        refreshAvailability()
        registerHeading()
    }

    /** 页面离开:断开所有传感器并暂停计步;轨迹与步数保留 */
    @Synchronized
    fun detach() {
        if (!attached) return
        attached = false
        stopStepSensor()
        stopHeading()
        currentHeadingDeg = null
        _state.value = _state.value.copy(running = false, headingDeg = null)
    }

    /** ViewModel 销毁兜底:无条件释放传感器 */
    @Synchronized
    fun shutdown() {
        attached = false
        stopStepSensor()
        stopHeading()
        currentHeadingDeg = null
        _state.value = _state.value.copy(running = false, headingDeg = null)
    }

    /**
     * 启动 / 继续步测
     *
     * 全新一程会把起点记为 (0,0);暂停后再次启动则从断点续算,
     * 不会把暂停期间走过的步数算进来(重新注册后首个计步事件只做基线)。
     * 计步器缺失或 Android 10+ 未授予「身体活动」权限时拒绝启动并给出原因。
     */
    @Synchronized
    fun start() {
        if (_state.value.running) return
        if (!attached) attach()
        refreshAvailability()

        val sensor = stepSensor
        if (sensorManager == null || sensor == null) {
            _state.value = _state.value.copy(
                running = false,
                error = "[!] 本机未提供计步传感器(TYPE_STEP_COUNTER / TYPE_STEP_DETECTOR 均不可用) —— 步测不可用",
            )
            return
        }
        if (!hasActivityRecognitionPermission()) {
            _state.value = _state.value.copy(
                running = false,
                stepPermissionGranted = false,
                error = "[!] Android 10+ 读取计步传感器需要「身体活动」权限,当前未授予 —— 授予后重新进入本页",
            )
            return
        }

        if (!sessionStarted) {
            // 全新一程:起点 (0,0);轨迹先放一个起点,便于从原点画折线
            posX = 0f
            posY = 0f
            stepCount = 0L
            distanceAccum = 0f
            sessionStarted = true
            _state.value = _state.value.copy(
                steps = 0L,
                distanceM = 0f,
                trail = listOf(0f to 0f),
                homeDistanceM = if (currentHeadingDeg != null) 0f else null,
                homeBearingDeg = null,
            )
        }

        awaitingStepBaseline = true
        lastStepCounter = Float.NaN
        registerHeading()
        registerStepSensor()
        if (!stepRegistered) return

        _state.value = _state.value.copy(
            running = true,
            stepPermissionGranted = true,
            hasStepSensor = true,
            hasHeadingSensor = rotationSensor != null,
            error = if (rotationSensor == null) {
                "[!] 无旋转矢量传感器 —— 航向不可用,步数与距离仍可计数,但位置无法推算"
            } else {
                null
            },
        )
    }

    /** 暂停:只摘计步器,航向继续刷新;已累计的轨迹与读数全部保留 */
    @Synchronized
    fun pause() {
        stopStepSensor()
        if (_state.value.running) _state.value = _state.value.copy(running = false)
    }

    /** 重置本程:清空轨迹 / 步数 / 距离,回到待启动状态 */
    @Synchronized
    fun reset() {
        stopStepSensor()
        sessionStarted = false
        posX = 0f
        posY = 0f
        stepCount = 0L
        distanceAccum = 0f
        awaitingStepBaseline = true
        lastStepCounter = Float.NaN
        refreshAvailability()
        _state.value = _state.value.copy(
            running = false,
            steps = 0L,
            distanceM = 0f,
            trail = emptyList(),
            homeBearingDeg = null,
            homeDistanceM = null,
            error = null,
        )
    }

    /**
     * 调整步长(0.50-1.00m,吸附到 0.05m 档位)
     *
     * 只影响之后的步伐,不重算已走轨迹 —— 历史每一步都按当时的步长记录。
     */
    @Synchronized
    fun setStepLen(meters: Float) {
        val snapped = (meters.coerceIn(PDR_MIN_STEP_LEN_M, PDR_MAX_STEP_LEN_M) * STEP_LEN_SNAP).roundToInt() / STEP_LEN_SNAP
        if (abs(snapped - _state.value.stepLenM) < 0.0001f) return
        _state.value = _state.value.copy(stepLenM = snapped)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 传感器注册 / 释放
    // ══════════════════════════════════════════════════════════════════════

    private fun registerHeading() {
        if (headingRegistered) return
        val sm = sensorManager ?: return
        val sensor = rotationSensor ?: return
        headingRegistered = runCatching {
            sm.registerListener(headingListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }.getOrDefault(false)
    }

    private fun stopHeading() {
        if (!headingRegistered) return
        runCatching { sensorManager?.unregisterListener(headingListener) }
        headingRegistered = false
    }

    @SuppressLint("MissingPermission")
    private fun registerStepSensor() {
        if (stepRegistered) return
        val sm = sensorManager ?: return
        val sensor = stepSensor ?: return
        stepRegistered = runCatching {
            sm.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_UI)
        }.getOrDefault(false)
        if (!stepRegistered) {
            _state.value = _state.value.copy(
                running = false,
                error = "[!] 计步传感器注册失败 —— 系统可能限制了传感器访问",
            )
        }
    }

    private fun stopStepSensor() {
        if (!stepRegistered) return
        runCatching { sensorManager?.unregisterListener(stepListener) }
        stepRegistered = false
    }

    private fun refreshAvailability() {
        _state.value = _state.value.copy(
            hasStepSensor = stepSensor != null,
            stepSensorName = stepSensor?.name.orEmpty(),
            stepPermissionGranted = hasActivityRecognitionPermission(),
            hasHeadingSensor = rotationSensor != null,
        )
    }

    /**
     * Android 10+ 计步传感器需要「身体活动」权限;旧系统不需要。
     * 这里只查询、不请求 —— 运行时授权由应用网关统一处理。
     */
    private fun hasActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED

    // ══════════════════════════════════════════════════════════════════════
    // 步数 → 位置
    // ══════════════════════════════════════════════════════════════════════

    /** TYPE_STEP_COUNTER:累计值,相邻差值即新增步数;复位(变负)时重建基线 */
    private fun onCounterEvent(value: Float) {
        if (!_state.value.running) return
        if (awaitingStepBaseline || lastStepCounter.isNaN()) {
            lastStepCounter = value
            awaitingStepBaseline = false
            return
        }
        val delta = (value - lastStepCounter).toInt()
        lastStepCounter = value
        if (delta <= 0) return
        advanceBy(delta)
    }

    /** TYPE_STEP_DETECTOR:每个事件代表一步 */
    private fun onStepDetected() {
        if (!_state.value.running) return
        advanceBy(1)
    }

    /**
     * 按当前航向推进 [count] 步
     *
     * 航向缺失时只累计步数与距离,绝不假设方向、绝不移动位置;
     * 此时距起点保持「不可用」(null)。
     */
    private fun advanceBy(count: Int) {
        if (count <= 0) return
        val s = _state.value
        if (!s.running) return

        val stepLen = s.stepLenM
        val heading = currentHeadingDeg ?: s.headingDeg
        var x = posX
        var y = posY
        var walked = 0f
        val nextTrail = s.trail.toMutableList()
        if (nextTrail.isEmpty()) nextTrail.add(x to y)

        if (heading != null) {
            val rad = Math.toRadians(heading.toDouble())
            // 显式 toFloat:避免 Float × Double 的隐式数值提升歧义
            val dx = stepLen * sin(rad).toFloat()
            val dy = stepLen * cos(rad).toFloat()
            repeat(count) {
                x += dx
                y += dy
                walked += stepLen
                nextTrail.add(x to y)
            }
        } else {
            walked = stepLen * count
        }

        posX = x
        posY = y
        stepCount += count
        distanceAccum += walked

        // 轨迹最多 2000 点:超限从头部丢弃(起点十字与轨迹数据分离,不会丢标)
        if (nextTrail.size > MAX_TRAIL_POINTS) {
            nextTrail.subList(0, nextTrail.size - MAX_TRAIL_POINTS).clear()
        }

        val homeDist = sqrt(posX * posX + posY * posY)
        val homeBearing = if (heading != null && homeDist > 0.05f) {
            ((Math.toDegrees(atan2((-posX).toDouble(), (-posY).toDouble())) + 360.0) % 360.0).toFloat()
        } else {
            null
        }

        _state.value = _state.value.copy(
            steps = stepCount,
            distanceM = distanceAccum,
            trail = nextTrail.toList(),
            homeDistanceM = if (heading != null) homeDist else null,
            homeBearingDeg = homeBearing,
        )
    }

    private companion object {
        /** 航向低通系数 —— 与雷达页一致 */
        const val HEADING_ALPHA = 0.25f
        /** 航向状态流节流间隔 */
        const val HEADING_EMIT_INTERVAL_MS = 80L
        /** 轨迹点数上限 */
        const val MAX_TRAIL_POINTS = 2000
        /** 步长吸附档位:1 / 0.05 */
        const val STEP_LEN_SNAP = 20f
    }
}
