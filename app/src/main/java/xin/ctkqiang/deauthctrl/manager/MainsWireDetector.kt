package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 墙内电线探测器状态
 *
 * 全部字段来自 AudioRecord 实时采样与本地 Goertzel 计算,没有任何模拟或补值;
 * 设备不支持时保持 null / 0,由 UI 显示「--」或「不可用」。
 *
 * @property running 是否正在采集
 * @property level 0-100 归一化工频特征电平(强度取对数压缩,便于低幅度可见)
 * @property freqMode 工频模式:50 或 60(Hz)
 * @property alert 工频强度是否超过自适应基线(2.5 倍进入 / 1.8 倍退出)
 * @property rmsDb 整帧 RMS 电平(dBFS,满刻度 0)
 * @property baselineLevel 前 2 秒环境基线(与 level 同一标尺)
 * @property error 采集错误;正常为 null
 * @property calibrating 是否仍在建立基线(前 2 秒)
 * @property strength 工频特征强度 = 基波与谐波合成幅度 / 整帧 RMS
 * @property fundDb 当前模式基波分量幅度(dBFS)
 * @property harmDb 当前模式谐波分量幅度(dBFS)
 * @property history 最近 60 帧的 level,用于火花线
 */
data class MainsState(
    val running: Boolean = false,
    val level: Int = 0,
    val freqMode: Int = 50,
    val alert: Boolean = false,
    val rmsDb: Float? = null,
    val baselineLevel: Int = 0,
    val error: String? = null,
    val calibrating: Boolean = true,
    val strength: Float = 0f,
    val fundDb: Float? = null,
    val harmDb: Float? = null,
    val history: List<Int> = emptyList(),
    val updatedAtMs: Long = 0L,
    val sampleRateHz: Int = MainsWireDetector.SAMPLE_RATE,
    val frameSamples: Int = MainsWireDetector.FRAME_SAMPLES,
)

/**
 * 墙内电线探测器 —— 工频(50/60Hz)声学 / 电磁特征检测
 *
 * ## 原理
 * AudioRecord 以 44100Hz 单声道采集,每 2048 样本(约 46ms)为一帧;
 * 对当前模式的基波(50 或 60Hz)与谐波(100 或 120Hz)分别做 Goertzel
 * 单频检测。基波与谐波的合成幅度除以整帧 RMS,得到「工频特征强度」:
 * 靠近有负载的带电线路时,该比值会明显高于环境本底。
 *
 * ## 基线自适应与迟滞
 * - 启动后前 2 秒的强度均值作为环境基线(基线下限 0.002,防止极静环境误报)
 * - 强度 > 基线 * 2.5 → 进入告警
 * - 强度 < 基线 * 1.8 → 退出告警
 * - 切换 50/60Hz 模式会清空基线并重新校准
 *
 * ## 诚实边界
 * 手机没有可直接测量墙内电场的传感器。这里测的是麦克风能捕捉到的
 * 工频声学嗡鸣,以及手机模拟前端对工频电磁场的拾取(可能被解调为音频)。
 * 它不是透视:无负载线路不产生可测特征,探测不到不代表没有电线。
 * 手机麦克风低频响应普遍较差,系统降噪/自动增益也可能削弱 50Hz 分量,
 * 因此读数只适合同一台设备上做相对比较。优先使用 UNPROCESSED 音源
 * 以避开系统音频处理(API 24+,失败则回退 MIC)。
 *
 * ## 线程模型
 * 单个采集线程阻塞读取 AudioRecord;StateFlow 每帧更新一次。
 * stop() 通过 AudioRecord.stop() 解除 read 阻塞,generation 计数防止
 * 快速停止/重启时旧线程回写过期状态。
 */
class MainsWireDetector(private val context: Context) {

    private val _state = MutableStateFlow(MainsState())
    val state: StateFlow<MainsState> = _state

    /** RECORD_AUDIO 是否已授权(权限由应用网关统一申请,这里只读取结果) */
    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    @Volatile private var stopFlag = false
    @Volatile private var recordRef: AudioRecord? = null
    private val generation = AtomicInteger(0)

    /** 汉宁窗 —— 非整数 bin 下抑制频谱泄漏;窗和用于幅度归一 */
    private val window = FloatArray(FRAME_SAMPLES) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (FRAME_SAMPLES - 1))).toFloat()
    }
    private val windowSum: Float = window.sum()

    // Goertzel 系数只与频率有关,预先算好,每帧不再做三角函数
    private val coeff50 = goertzelCoeff(50f)
    private val coeff100 = goertzelCoeff(100f)
    private val coeff60 = goertzelCoeff(60f)
    private val coeff120 = goertzelCoeff(120f)

    /**
     * 启动采集
     *
     * 幂等;重复调用无副作用。权限缺失或硬件不可用时立即写入 error。
     */
    @Suppress("MissingPermission")
    fun start() {
        if (_state.value.running) return
        if (!hasPermission) {
            _state.update { it.copy(error = "缺少麦克风权限(RECORD_AUDIO)") }
            return
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            _state.update { it.copy(error = "此设备没有麦克风,无法进行工频探测") }
            return
        }
        stopFlag = false
        val myGen = generation.incrementAndGet()
        _state.update {
            it.copy(
                running = true,
                level = 0,
                alert = false,
                rmsDb = null,
                baselineLevel = 0,
                calibrating = true,
                strength = 0f,
                fundDb = null,
                harmDb = null,
                history = emptyList(),
                error = null,
                updatedAtMs = System.currentTimeMillis(),
            )
        }
        thread(name = "mains-wire") { runLoop(myGen) }
    }

    /**
     * 停止采集并释放麦克风
     *
     * AudioRecord.stop() 会解除阻塞中的 read(),线程在 finally 中释放资源。
     */
    fun stop() {
        stopFlag = true
        // 让旧采集线程的收尾更新失效,避免快速重启后 running 被旧线程改回 false
        generation.incrementAndGet()
        runCatching { recordRef?.stop() }
        _state.update {
            it.copy(
                running = false,
                alert = false,
                level = 0,
                strength = 0f,
                rmsDb = null,
                fundDb = null,
                harmDb = null,
                baselineLevel = 0,
                calibrating = true,
            )
        }
    }

    /**
     * 切换工频模式
     *
     * @param mode 50 或 60;其他值按 50 处理
     */
    fun setFreqMode(mode: Int) {
        val m = if (mode == 60) 60 else 50
        if (m == _state.value.freqMode) return
        _state.update {
            it.copy(
                freqMode = m,
                calibrating = true,
                baselineLevel = 0,
                alert = false,
            )
        }
    }

    // ─── 采集线程 ─────────────────────────────────────────────

    private fun runLoop(myGen: Int) {
        var record: AudioRecord? = null
        try {
            val rec = createRecord()
            if (rec == null) {
                if (myGen == generation.get()) {
                    _state.update {
                        it.copy(
                            running = false,
                            error = "麦克风初始化失败(设备不支持 44100Hz 采集,或被其他应用占用)",
                        )
                    }
                }
                return
            }
            record = rec
            recordRef = rec
            rec.startRecording()

            val samples = ShortArray(FRAME_SAMPLES)
            val history = ArrayList<Int>(HISTORY_SIZE)

            // 基线累计(前 2 秒);切换模式时重置
            var baseline: Float? = null
            var baselineSum = 0.0
            var baselineCount = 0
            var baselineStartMs = SystemClock.elapsedRealtime()
            var alertNow = false
            var lastMode = _state.value.freqMode
            var consecutiveErrors = 0

            while (!stopFlag && myGen == generation.get()) {
                // 阻塞读满一帧(2048 样本);read 可能分多次返回
                var read = 0
                while (read < FRAME_SAMPLES && !stopFlag) {
                    val n = rec.read(samples, read, FRAME_SAMPLES - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < FRAME_SAMPLES) {
                    if (stopFlag) break
                    consecutiveErrors++
                    if (consecutiveErrors >= 50) throw IllegalStateException("麦克风读取持续失败")
                    Thread.sleep(10)
                    continue
                }
                consecutiveErrors = 0

                val now = SystemClock.elapsedRealtime()
                val mode = _state.value.freqMode
                if (mode != lastMode) {
                    // 50/60Hz 切换:重新校准基线,旧告警作废
                    lastMode = mode
                    baseline = null
                    baselineSum = 0.0
                    baselineCount = 0
                    baselineStartMs = now
                    alertNow = false
                }
                val coeffFund = if (mode == 60) coeff60 else coeff50
                val coeffHarm = if (mode == 60) coeff120 else coeff100

                val fundamental = goertzelAmplitude(samples, FRAME_SAMPLES, coeffFund)
                val harmonic = goertzelAmplitude(samples, FRAME_SAMPLES, coeffHarm)
                val rms = frameRms(samples, FRAME_SAMPLES)

                // 工频特征强度:窄带分量合成幅度 / 整帧 RMS
                val strength = if (rms > 1e-5f) {
                    sqrt(fundamental * fundamental + harmonic * harmonic) / rms
                } else {
                    0f
                }
                val level = strengthToLevel(strength)

                if (baseline == null) {
                    baselineSum += strength.toDouble()
                    baselineCount++
                    if (now - baselineStartMs >= BASELINE_MS && baselineCount >= 8) {
                        // 基线下限 0.002:极静环境的自噪声会把阈值压得过低,导致持续误报
                        baseline = (baselineSum / baselineCount)
                            .toFloat()
                            .coerceAtLeast(MIN_BASELINE)
                    }
                }
                val base = baseline
                // 迟滞:> 2.5 倍基线进入告警,< 1.8 倍基线退出
                alertNow = when {
                    base == null -> false
                    alertNow -> strength > base * ALERT_EXIT_FACTOR
                    else -> strength > base * ALERT_ENTER_FACTOR
                }

                history.add(level)
                if (history.size > HISTORY_SIZE) history.removeAt(0)
                val historyCopy = history.toList()

                val baselineLevel = strengthToLevel(base ?: 0f)
                val rmsDb = toDb(rms)
                val fundDb = toDb(fundamental)
                val harmDb = toDb(harmonic)

                _state.update {
                    it.copy(
                        running = true,
                        level = level,
                        alert = alertNow,
                        rmsDb = rmsDb,
                        baselineLevel = baselineLevel,
                        calibrating = base == null,
                        strength = strength,
                        fundDb = fundDb,
                        harmDb = harmDb,
                        history = historyCopy,
                        updatedAtMs = System.currentTimeMillis(),
                        error = null,
                    )
                }
            }
        } catch (t: Throwable) {
            if (myGen == generation.get()) {
                _state.update {
                    it.copy(
                        running = false,
                        alert = false,
                        error = "采集失败: ${t.message ?: t.javaClass.simpleName}",
                    )
                }
            }
        } finally {
            runCatching { record?.stop() }
            runCatching { record?.release() }
            recordRef = null
            if (myGen == generation.get()) {
                _state.update {
                    it.copy(
                        running = false,
                        alert = false,
                        level = 0,
                        strength = 0f,
                        rmsDb = null,
                        fundDb = null,
                        harmDb = null,
                    )
                }
            }
        }
    }

    // ─── 音频初始化 ───────────────────────────────────────────

    /**
     * 创建 AudioRecord
     *
     * API 24+ 优先 UNPROCESSED(关闭降噪/AGC,保留低频),失败再回退 MIC;
     * minSdk 23 上直接使用 MIC。
     */
    @Suppress("MissingPermission")
    private fun createRecord(): AudioRecord? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            openRecord(MediaRecorder.AudioSource.UNPROCESSED)?.let { return it }
        }
        return openRecord(MediaRecorder.AudioSource.MIC)
    }

    @Suppress("MissingPermission")
    private fun openRecord(source: Int): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return null
        val rec = runCatching {
            AudioRecord(
                source,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, FRAME_SAMPLES * 2 * 2),
            )
        }.getOrNull() ?: return null
        return if (rec.state == AudioRecord.STATE_INITIALIZED) {
            rec
        } else {
            runCatching { rec.release() }
            null
        }
    }

    // ─── 信号计算 ─────────────────────────────────────────────

    /** Goertzel 系数:2 * cos(2π f / fs) */
    private fun goertzelCoeff(freqHz: Float): Float =
        (2.0 * cos(2.0 * PI * freqHz / SAMPLE_RATE)).toFloat()

    /**
     * Goertzel 单频幅度
     *
     * 输入样本归一化到 ±1 后加汉宁窗;返回正弦幅度(满刻度 1.0)。
     * 非整数 bin 直接使用角频率,避免频率量化误差。
     */
    private fun goertzelAmplitude(samples: ShortArray, n: Int, coeff: Float): Float {
        var s1 = 0f
        var s2 = 0f
        for (i in 0 until n) {
            val s0 = (samples[i] / 32768f) * window[i] + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        if (windowSum <= 0f) return 0f
        // 加窗后 DFT 幅度 ≈ A * sum(w) / 2
        return 2f * sqrt(power.coerceAtLeast(0f)) / windowSum
    }

    /** 整帧 RMS(样本归一化到 ±1) */
    private fun frameRms(samples: ShortArray, n: Int): Float {
        var sumSq = 0.0
        for (i in 0 until n) {
            val v = samples[i] / 32768.0
            sumSq += v * v
        }
        return sqrt(sumSq / n).toFloat()
    }

    /** 幅度 → dBFS(-120..0) */
    private fun toDb(amplitude: Float): Float =
        (20.0 * log10(amplitude.coerceAtLeast(1e-6f).toDouble()))
            .toFloat()
            .coerceIn(-120f, 0f)

    /**
     * 工频强度 → 0-100 电平
     *
     * 线性映射会让环境本底与弱工频分量都挤在 0-2 附近;这里按 20·log10
     * 压缩:强度 0.001 记为 0,强度 1.0(0 dB)记为 100,跨度 60dB。
     * 只影响显示,告警判定仍使用原始强度与基线的倍数关系。
     */
    private fun strengthToLevel(strength: Float): Int {
        if (strength <= 0f) return 0
        val ratio = (strength / LEVEL_REF_STRENGTH).coerceAtLeast(1e-6f)
        val db = 20.0 * log10(ratio.toDouble())
        return ((db / LEVEL_SPAN_DB) * 100.0).roundToInt().coerceIn(0, 100)
    }

    companion object {
        /** 麦克风采样率 */
        const val SAMPLE_RATE = 44100

        /** 每帧样本数(约 46ms) */
        const val FRAME_SAMPLES = 2048

        /** 火花线保留的 level 点数 */
        const val HISTORY_SIZE = 60

        /** 基线校准时长 */
        const val BASELINE_MS = 2000L

        /** 告警进入倍数 */
        const val ALERT_ENTER_FACTOR = 2.5f

        /** 告警退出倍数(迟滞) */
        const val ALERT_EXIT_FACTOR = 1.8f

        /** 基线下限,防止极静环境误报 */
        const val MIN_BASELINE = 0.002f

        /** 电平显示的对数参考强度:0.001 记为 0 */
        const val LEVEL_REF_STRENGTH = 0.001f

        /** 电平显示的满量程跨度(dB):0.001 → 1.0 */
        const val LEVEL_SPAN_DB = 60.0
    }
}
