package xin.ctkqiang.deauthctrl.manager

import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 事件判定默认阈值(m/s²) —— 约等于桌面被轻敲时的净加速度峰值 */
const val SEISMO_DEFAULT_THRESHOLD = 0.5f

/** 阈值可调下限(m/s²) —— 再低会被传感器噪声本底淹没 */
const val SEISMO_MIN_THRESHOLD = 0.2f

/** 阈值可调上限(m/s²) —— 只保留强冲击事件 */
const val SEISMO_MAX_THRESHOLD = 3.0f

/** 波形缓冲容量(样本) —— 请求 50Hz 时约覆盖最近 12 秒 */
const val SEISMO_WAVEFORM_CAPACITY = 600

/** 事件日志上限(条) */
const val SEISMO_MAX_EVENTS = 50

/**
 * 单个波形样本
 *
 * @param timeMs 采样时刻(CLOCK_ELAPSED_REALTIME,毫秒;单调递增,用于计算持续时长)
 * @param net    去重力后的净加速度(m/s²,三轴合加速度减去本地重力基准)
 */
data class SeismoSample(val timeMs: Long, val net: Float)

/**
 * 一次震动事件
 *
 * @param wallTimeMs 事件首次越过阈值的系统时钟时刻(展示用)
 * @param peak       本次事件内出现的最大 |净加速度|(m/s²,实测值)
 * @param durationMs 首次与末次越过阈值的样本时间差(毫秒);单样本事件为 0
 */
data class SeismoEvent(val wallTimeMs: Long, val peak: Float, val durationMs: Long)

/**
 * 地震仪状态
 *
 * @param running        是否正在监听加速度计
 * @param waveform       最近 [SEISMO_WAVEFORM_CAPACITY] 个样本(旧 → 新)
 * @param events         事件日志(最新在前,最多 [SEISMO_MAX_EVENTS] 条)
 * @param threshold      当前事件阈值(m/s²)
 * @param peakCurrent    最近 50 个样本内的 |净加速度| 峰值(m/s²,请求 50Hz 时约 1 秒)
 * @param error          硬失败原因;传感器缺失单独由 [hasAccelerometer] 表达
 * @param hasAccelerometer 本机是否提供 TYPE_ACCELEROMETER
 * @param sensorName     系统返回的传感器名称
 * @param gravityBaseline 本地重力幅值基准(m/s²,EMA 估计)
 * @param eventActive    是否有事件正在阈值之上(尚未满足结束静默门限)
 * @param eventPeak      进行中事件的峰值(m/s²);无事件时为 0
 */
data class SeismoState(
    val running: Boolean = false,
    val waveform: List<SeismoSample> = emptyList(),
    val events: List<SeismoEvent> = emptyList(),
    val threshold: Float = SEISMO_DEFAULT_THRESHOLD,
    val peakCurrent: Float = 0f,
    val error: String? = null,
    val hasAccelerometer: Boolean = true,
    val sensorName: String = "",
    val gravityBaseline: Float = 0f,
    val eventActive: Boolean = false,
    val eventPeak: Float = 0f,
)

/**
 * 震动事件记录 —— 消费级加速度计的「地震仪」
 *
 * ## 原理
 * - 监听 TYPE_ACCELEROMETER,请求 SENSOR_DELAY_GAME(系统通常下发约 50Hz);
 * - 三轴合加速度 magnitude = sqrt(x² + y² + z²),与手机朝向无关;
 * - 去重力:用 alpha 0.01 的指数滑动平均跟踪 magnitude 的长期基线(约为 9.8 m/s²),
 *   净加速度 net = magnitude - 基线。首次样本只做基线,输出 0;
 * - 波形:最近 [SEISMO_WAVEFORM_CAPACITY] 个样本(采样时刻 + 净加速度);
 * - 事件: |net| 超过阈值即开始记录峰值;连续 [EVENT_QUIET_GATE_MS] 毫秒低于阈值
 *   才判定结束,并把「时间 / 峰值 / 持续时长」追加进日志(最多 50 条)。
 *
 * ## 诚实边界
 * 手机加速度计是 16 位消费级 MEMS 器件,噪声本底、带宽与动态范围都远逊于
 * 专业地震仪。它只能可靠感知明显震动(敲门、脚步、机械振动、桌面被碰),
 * 对远距离地震基本不敏感。所有读数均来自传感器真实返回值,没有样本时页面上
 * 显示「--」,绝不编造;持续时长为离散样本时间差,单样本事件记为 0ms。
 *
 * ## 线程与生命周期
 * 传感器回调与 start / stop 都在主线程;状态流按 [EMIT_INTERVAL_MS] 节流,
 * 避免 50Hz 回调驱动整页 50Hz 重组。页面 DisposableEffect 负责 start / shutdown。
 */
class SeismographManager(context: Context) {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _state = MutableStateFlow(
        SeismoState(
            hasAccelerometer = accelerometer != null,
            sensorName = accelerometer?.name.orEmpty(),
        ),
    )
    val state: StateFlow<SeismoState> = _state

    // ─── 波形环形缓冲(旧 → 新) ────────────────────────────────────────────
    private val waveform = ArrayDeque<SeismoSample>(SEISMO_WAVEFORM_CAPACITY)

    // ─── 传感器与去重力 ────────────────────────────────────────────────────
    private var registered = false
    private var gravityInitialized = false
    private var gravityBaseline = 0f

    // ─── 事件进行态 ────────────────────────────────────────────────────────
    private var eventActive = false
    private var eventStartMs = 0L
    private var eventLastAboveMs = 0L
    private var eventWallTimeMs = 0L
    private var eventPeak = 0f

    /** 状态流节流时间戳(CLOCK_ELAPSED_REALTIME) */
    private var lastEmitMs = 0L

    // ══════════════════════════════════════════════════════════════════════
    // 传感器监听
    // ══════════════════════════════════════════════════════════════════════

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!_state.value.running) return
            val values = event.values
            if (values.size < 3) return

            val x = values[0]
            val y = values[1]
            val z = values[2]
            val magnitude = sqrt(x * x + y * y + z * z)
            if (!magnitude.isFinite() || magnitude <= 0f) return

            // 首次样本只建立重力基线,输出 0,避免开页瞬间的假事件
            if (!gravityInitialized) {
                gravityBaseline = magnitude
                gravityInitialized = true
            }
            // 去重力:EMA 跟踪本地重力幅值(alpha 0.01,50Hz 下时间常数约 2 秒)
            gravityBaseline += (magnitude - gravityBaseline) * GRAVITY_ALPHA
            val net = magnitude - gravityBaseline

            val nowElapsed = SystemClock.elapsedRealtime()
            val nowWall = System.currentTimeMillis()

            waveform.addLast(SeismoSample(nowElapsed, net))
            while (waveform.size > SEISMO_WAVEFORM_CAPACITY) waveform.removeFirst()

            updateEvent(net, nowElapsed, nowWall)

            // 节流发布:50Hz 回调 → 约 25Hz 状态流,波形仍是全量样本
            if (nowElapsed - lastEmitMs >= EMIT_INTERVAL_MS) {
                lastEmitMs = nowElapsed
                publish()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // ══════════════════════════════════════════════════════════════════════
    // 对外生命周期
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 开始监听
     *
     * 每次启动视为一次新会话:清空波形与进行中事件,事件日志保留。
     * 无加速度计或注册失败时写入 error 并保持停止态。
     */
    @Suppress("MissingPermission")
    @Synchronized
    fun start() {
        if (_state.value.running) return
        val sm = sensorManager
        val sensor = accelerometer
        if (sm == null || sensor == null) {
            _state.value = _state.value.copy(
                running = false,
                hasAccelerometer = false,
                sensorName = "",
                error = L10n.get(R.string.mgr_seismo_no_accelerometer),
            )
            return
        }

        // 新会话:波形与进行态归零;重力基线与节流时间戳在首个样本重建
        waveform.clear()
        gravityInitialized = false
        gravityBaseline = 0f
        eventActive = false
        eventPeak = 0f
        eventStartMs = 0L
        eventLastAboveMs = 0L
        eventWallTimeMs = 0L
        lastEmitMs = 0L

        registered = runCatching {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }.getOrDefault(false)
        if (!registered) {
            _state.value = _state.value.copy(
                running = false,
                hasAccelerometer = true,
                sensorName = sensor.name,
                error = L10n.get(R.string.mgr_seismo_register_failed),
            )
            return
        }

        _state.value = _state.value.copy(
            running = true,
            waveform = emptyList(),
            peakCurrent = 0f,
            error = null,
            hasAccelerometer = true,
            sensorName = sensor.name,
            gravityBaseline = 0f,
            eventActive = false,
            eventPeak = 0f,
        )
    }

    /**
     * 停止监听
     *
     * 若停止瞬间仍有事件在阈值之上,按已有的真实时间戳补录一条事件,
     * 不让半截事件丢失;波形保留供页面查看(下次 start 时清空)。
     */
    @Synchronized
    fun stop() {
        if (registered) {
            runCatching { sensorManager?.unregisterListener(listener) }
            registered = false
        }

        if (eventActive) {
            val duration = (eventLastAboveMs - eventStartMs).coerceAtLeast(0L)
            val record = SeismoEvent(eventWallTimeMs, eventPeak, duration)
            eventActive = false
            eventPeak = 0f
            _state.update {
                it.copy(
                    events = (listOf(record) + it.events).take(SEISMO_MAX_EVENTS),
                    eventActive = false,
                    eventPeak = 0f,
                )
            }
        }

        gravityInitialized = false
        _state.value = _state.value.copy(
            running = false,
            waveform = waveform.toList(),
            peakCurrent = 0f,
            eventActive = false,
            eventPeak = 0f,
        )
    }

    /** ViewModel 销毁兜底:无条件停止监听并释放传感器 */
    @Synchronized
    fun shutdown() {
        stop()
    }

    /**
     * 调整事件阈值(0.2-3.0 m/s²,吸附到 0.1 档位)
     *
     * 只影响之后的事件判定,不重算已有事件。
     */
    @Synchronized
    fun setThreshold(value: Float) {
        val snapped = (value.coerceIn(SEISMO_MIN_THRESHOLD, SEISMO_MAX_THRESHOLD) * 10f)
            .roundToInt() / 10f
        if (abs(snapped - _state.value.threshold) < 0.0001f) return
        _state.update { it.copy(threshold = snapped) }
    }

    /** 清空事件日志(进行中的事件不受影响) */
    @Synchronized
    fun clearEvents() {
        _state.update { it.copy(events = emptyList()) }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 事件判定与状态发布
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 逐样本更新事件状态
     *
     * 越过阈值即开始(或延续)事件;低于阈值后必须连续静默 [EVENT_QUIET_GATE_MS]
     * 才结束,避免一次震动被阈值抖动切成多条碎片记录。
     */
    private fun updateEvent(net: Float, nowElapsed: Long, nowWall: Long) {
        val threshold = _state.value.threshold
        val absolute = abs(net)

        if (absolute > threshold) {
            if (!eventActive) {
                eventActive = true
                eventStartMs = nowElapsed
                eventWallTimeMs = nowWall
                eventPeak = absolute
            } else if (absolute > eventPeak) {
                eventPeak = absolute
            }
            eventLastAboveMs = nowElapsed
            return
        }

        if (!eventActive) return
        if (nowElapsed - eventLastAboveMs < EVENT_QUIET_GATE_MS) return

        val duration = (eventLastAboveMs - eventStartMs).coerceAtLeast(0L)
        val record = SeismoEvent(eventWallTimeMs, eventPeak, duration)
        eventActive = false
        eventPeak = 0f
        _state.update {
            it.copy(
                events = (listOf(record) + it.events).take(SEISMO_MAX_EVENTS),
                eventActive = false,
                eventPeak = 0f,
            )
        }
    }

    /** 状态发布:波形全量 + 滚动窗口峰值 + 重力基准 + 事件进行态 */
    private fun publish() {
        val rollingPeak = rollingPeak()
        _state.update {
            it.copy(
                waveform = waveform.toList(),
                peakCurrent = rollingPeak,
                gravityBaseline = gravityBaseline,
                eventActive = eventActive,
                eventPeak = eventPeak,
            )
        }
    }

    /** 最近 [ROLLING_PEAK_SAMPLES] 个样本内的 |净加速度| 峰值 */
    private fun rollingPeak(): Float {
        var peak = 0f
        val size = waveform.size
        val from = (size - ROLLING_PEAK_SAMPLES).coerceAtLeast(0)
        for (i in from until size) {
            val value = abs(waveform[i].net)
            if (value > peak) peak = value
        }
        return peak
    }

    private companion object {
        /** 重力基线 EMA 系数 */
        const val GRAVITY_ALPHA = 0.01f
        /** 事件结束所需的连续低于阈值时长(毫秒) */
        const val EVENT_QUIET_GATE_MS = 400L
        /** 状态流发布节流间隔(毫秒) */
        const val EMIT_INTERVAL_MS = 40L
        /** 滚动峰值窗口(样本数) —— 50Hz 下约 1 秒 */
        const val ROLLING_PEAK_SAMPLES = 50
    }
}
