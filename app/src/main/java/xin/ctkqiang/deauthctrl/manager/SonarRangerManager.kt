package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 声呐测距状态
 *
 * 所有数值都来自扬声器播放的 chirp 与 AudioRecord 的真实采样,
 * 再经本地 FFT 匹配滤波计算;没有模拟数据或补值。
 *
 * @property running 是否正在连续测量
 * @property distanceM EMA 平滑后的距离(米);本轮无有效回波时保留上次有效值,
 *                     由 [noEcho] / [outOfRange] 标记本次读数不可用
 * @property confidence 最近一次有效回波的峰显著性 0-1(峰值相对相关本底的 z 分数
 *                     与峰值/均值比合成),不代表物理精度
 * @property history 最近 40 次有效测量(经 EMA 平滑)的距离,用于火花线
 * @property noEcho 最近一个周期没有找到满足门限的回波
 * @property outOfRange 最近一个周期的最强回波超过 8m 量程
 * @property peakRatio 最近周期回波峰值 / 相关序列平均绝对值
 * @property directLagMs 直达声(扬声器到麦克风)在录音中的时延,用作 0m 时基
 * @property cycles 已完成的测量周期数
 * @property validCycles 其中得到有效回波的周期数
 * @property lastRawDistanceM 最近周期未经 EMA 的原始距离估计
 * @property mediaVolumePercent 当前媒体音量百分比(系统返回值;读取不到为 null)
 * @property lastMeasuredAtMs 最近周期完成时的系统时间
 * @property error 采集 / 播放错误;正常为 null
 */
data class SonarState(
    val running: Boolean = false,
    val distanceM: Float? = null,
    val confidence: Float = 0f,
    val history: List<Float> = emptyList(),
    val noEcho: Boolean = false,
    val outOfRange: Boolean = false,
    val peakRatio: Float = 0f,
    val directLagMs: Float? = null,
    val cycles: Int = 0,
    val validCycles: Int = 0,
    val lastRawDistanceM: Float? = null,
    val mediaVolumePercent: Int? = null,
    val lastMeasuredAtMs: Long = 0L,
    val error: String? = null,
)

/**
 * 声呐测距 —— 扬声器 chirp + 麦克风回波匹配滤波
 *
 * ## 发射
 * 预生成 60ms 线性扫频 chirp:频率 3000Hz → 8000Hz,汉宁窗包络,
 * 44.1kHz / 16bit / 单声道 PCM。每 1.5 秒经 AudioTrack(STREAM_MUSIC /
 * USAGE_MEDIA,音轨增益拉满)播放一次。
 *
 * ## 接收与匹配滤波
 * 每轮测量前重新 startRecording() 以清空录音缓冲;先录 40ms 本底再打 chirp,
 * 随后读取 0.5s(22050 样本)。录音补零到 32768 点,与预生成的 chirp 模板
 * (同样补零到 32768 点)各做一次 radix-2 FFT(位反转 + 蝶形),
 * 频域共轭相乘后 IFFT,得到互相关序列:
 *
 *     corr[n] = Σ r[n+l] · c[l]
 *
 * 回波相对直达声的时延 = (回波峰位置 - 直达声峰位置) / 44100 秒,
 * 距离 = 时延 × 343 / 2。
 *
 * ## 为什么以直达声为 0m 时基
 * 扬声器与麦克风在同一台手机上,声音一出扬声器就会被麦克风直接收到。
 * 但 Android 的播放 / 录音管线各有几十毫秒延迟,若直接用回波峰的绝对
 * 下标换算,系统延迟会被当成数米距离。因此把每轮最前面的强相关峰
 * (扬声器→麦克风直达声)当作 0m 时基,只测量它之后的回波。
 * 若 AEC 把直达声抹掉、或蓝牙耳机导致麦克风收不到直达声,本管理器
 * 会报「未捕获直达声」,而不是给出编造的距离。
 *
 * ## 峰判定
 * 在直达声之后、距离 0.2-8m 对应的搜索窗内取绝对值最大的相关峰:
 * - 峰值 > 3 × 相关序列平均绝对值(题设门限);
 * - 峰值相对相关本底的 z 分数 ≥ 6(抑制纯噪声的偶发峰);
 * - 归一化相关 ρ ≥ 0.0005(排除数值本底);
 * 三者同时满足才报告。超过 8m 的峰显示超量程,没有峰显示无回波。
 *
 * ## 资源与线程
 * 单线程串行执行「播放 → 录音 → FFT → 判定 → 等待」;每轮结束调用
 * AudioRecord.stop() 清空缓冲。stop() 通过 AudioRecord.stop() /
 * AudioTrack.stop() 解除阻塞,generation 计数防止快速启停时旧线程
 * 回写过期状态。
 *
 * ## 诚实边界
 * 测的是扬声器可听频段的声学回波,受扬声器功率、麦克风灵敏度、手机壳、
 * 握持方向、房间混响与系统音频处理(AEC/AGC/降噪)影响。0.2-8m 只是
 * 典型可靠范围,不是硬指标;软表面、斜角度、强噪声下会测不到。
 * 距离是峰值时延的估计值,不是激光测距。
 */
class SonarRangerManager(private val context: Context) {

    private val _state = MutableStateFlow(SonarState())
    val state: StateFlow<SonarState> = _state

    /** RECORD_AUDIO 是否已授权(权限由应用网关统一申请,这里只读取结果) */
    val hasRecordPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    @Volatile private var stopFlag = false
    @Volatile private var trackRef: AudioTrack? = null
    @Volatile private var recordRef: AudioRecord? = null
    private val generation = AtomicInteger(0)

    // ─── 预生成 chirp(PCM 与匹配滤波模板) ───

    /** 实际播放的 16bit PCM,含汉宁窗包络 */
    private val chirpPcm: ShortArray = buildChirpPcm()

    /** 归一化到 ±1 的模板,与播放波形一致 */
    private val chirpTemplate: FloatArray = FloatArray(CHIRP_SAMPLES) { chirpPcm[it] / 32768f }

    private val templateRe = FloatArray(FFT_SIZE)
    private val templateIm = FloatArray(FFT_SIZE)
    private var templateEnergy = 0f
    private var templateReady = false

    // FFT 查表在首次使用时由采集线程初始化,避免占用主线程
    private val bitRev: IntArray by lazy {
        IntArray(FFT_SIZE) { i -> Integer.reverse(i) ushr (32 - LOG2_FFT) }
    }
    private val cosTable: FloatArray by lazy {
        FloatArray(FFT_SIZE / 2) { i -> cos(-2.0 * PI * i / FFT_SIZE).toFloat() }
    }
    private val sinTable: FloatArray by lazy {
        FloatArray(FFT_SIZE / 2) { i -> sin(-2.0 * PI * i / FFT_SIZE).toFloat() }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 生命周期
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 启动连续测量
     *
     * 权限缺失、设备无麦克风、媒体音量为 0 时立即写入 error,不启动音频线程;
     * 重复调用无副作用。
     */
    fun start() {
        if (_state.value.running) return
        if (!hasRecordPermission) {
            _state.update {
                it.copy(error = L10n.get(R.string.mgr_sonar_error_no_record_permission))
            }
            return
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            _state.update { it.copy(error = L10n.get(R.string.mgr_sonar_error_no_mic)) }
            return
        }
        val volume = mediaVolumePercent()
        if (volume == 0) {
            _state.update {
                it.copy(
                    error = L10n.get(R.string.mgr_sonar_error_volume_zero),
                    mediaVolumePercent = 0,
                )
            }
            return
        }
        stopFlag = false
        val myGen = generation.incrementAndGet()
        _state.update {
            it.copy(
                running = true,
                distanceM = null,
                confidence = 0f,
                history = emptyList(),
                noEcho = false,
                outOfRange = false,
                peakRatio = 0f,
                directLagMs = null,
                cycles = 0,
                validCycles = 0,
                lastRawDistanceM = null,
                mediaVolumePercent = volume,
                lastMeasuredAtMs = 0L,
                error = null,
            )
        }
        thread(name = "sonar-ranger") { runLoop(myGen) }
    }

    /**
     * 停止测量并释放扬声器 / 麦克风
     *
     * AudioRecord.stop() 与 AudioTrack.stop() 会解除阻塞中的 read / write;
     * 线程在 finally 中释放资源。重复调用无副作用。
     */
    fun stop() {
        stopFlag = true
        generation.incrementAndGet()
        runCatching { trackRef?.pause() }
        runCatching { trackRef?.flush() }
        runCatching { trackRef?.stop() }
        runCatching { recordRef?.stop() }
        _state.update {
            it.copy(
                running = false,
                confidence = 0f,
                noEcho = false,
                outOfRange = false,
                directLagMs = null,
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 采集循环
    // ══════════════════════════════════════════════════════════════════════

    @Suppress("MissingPermission")
    private fun runLoop(myGen: Int) {
        var track: AudioTrack? = null
        var record: AudioRecord? = null
        var aec: AcousticEchoCanceler? = null
        var agc: AutomaticGainControl? = null
        var ns: NoiseSuppressor? = null
        try {
            val trk = createTrack()
            track = trk
            trackRef = trk

            val rec = createRecord()
                ?: throw IllegalStateException(L10n.get(R.string.mgr_sonar_error_record_init))
            record = rec
            recordRef = rec

            // 关闭系统音频效果:AEC 会主动抵消扬声器发出的 chirp 及其回波,
            // AGC / 降噪则会改变回波幅度。部分机型不支持单独关闭,失败时忽略。
            aec = runCatching { AcousticEchoCanceler.create(rec.audioSessionId) }.getOrNull()
            runCatching { aec?.setEnabled(false) }
            agc = runCatching { AutomaticGainControl.create(rec.audioSessionId) }.getOrNull()
            runCatching { agc?.setEnabled(false) }
            ns = runCatching { NoiseSuppressor.create(rec.audioSessionId) }.getOrNull()
            runCatching { ns?.setEnabled(false) }

            prepareTemplate()

            val samples = ShortArray(RECORD_SAMPLES)
            val recRe = FloatArray(FFT_SIZE)
            val recIm = FloatArray(FFT_SIZE)
            val corrRe = FloatArray(FFT_SIZE)
            val corrIm = FloatArray(FFT_SIZE)

            var emaDistance: Float? = null
            var emaConfidence = 0f
            var validCycles = 0
            var cycles = 0
            val history = ArrayList<Float>(HISTORY_SIZE)

            runCatching { trk.play() }

            while (!stopFlag && myGen == generation.get()) {
                val cycleStart = SystemClock.elapsedRealtime()

                // 每轮重新开始录音:清空上一轮残留在缓冲里的回声与静音
                rec.startRecording()
                if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw IllegalStateException(L10n.get(R.string.mgr_sonar_error_record_start))
                }
                // 先录一小段本底,再打 chirp;直达声与回波都落在随后的 0.5s 窗内
                Thread.sleep(RECORD_PREROLL_MS)
                if (stopFlag || myGen != generation.get()) break

                runCatching { trk.play() }
                val written = trk.write(chirpPcm, 0, chirpPcm.size, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) {
                    throw IllegalStateException(
                        L10n.get(R.string.mgr_sonar_error_speaker_write, written),
                    )
                }

                var read = 0
                while (read < RECORD_SAMPLES && !stopFlag && myGen == generation.get()) {
                    val n = rec.read(samples, read, RECORD_SAMPLES - read)
                    if (n < 0) {
                        throw IllegalStateException(L10n.get(R.string.mgr_sonar_error_mic_read, n))
                    }
                    if (n == 0) break
                    read += n
                }
                runCatching { rec.stop() }
                if (stopFlag || myGen != generation.get()) break
                if (read < RECORD_SAMPLES) {
                    throw IllegalStateException(
                        L10n.get(R.string.mgr_sonar_error_mic_read_short, read, RECORD_SAMPLES),
                    )
                }

                val measurement = analyze(samples, recRe, recIm, corrRe, corrIm)
                cycles++
                val directLagMs = measurement.directIdx.toFloat() / SAMPLE_RATE * 1000f
                val volume = mediaVolumePercent()
                val now = System.currentTimeMillis()

                when {
                    measurement.directRho < MIN_DIRECT_RHO -> {
                        // 没有直达声:可能是 AEC 抹掉了、媒体音量过低、麦克风被遮挡,
                        // 或音频被路由到蓝牙设备。此时任何「距离」都不可信,直接报错。
                        _state.update {
                            it.copy(
                                noEcho = true,
                                outOfRange = false,
                                confidence = 0f,
                                peakRatio = 0f,
                                directLagMs = directLagMs,
                                cycles = cycles,
                                validCycles = validCycles,
                                lastRawDistanceM = null,
                                mediaVolumePercent = volume,
                                lastMeasuredAtMs = now,
                                error = L10n.get(R.string.mgr_sonar_error_direct_missing),
                            )
                        }
                    }

                    measurement.echoIdx < 0 -> {
                        _state.update {
                            it.copy(
                                noEcho = true,
                                outOfRange = false,
                                confidence = 0f,
                                peakRatio = measurement.peakRatio,
                                directLagMs = directLagMs,
                                cycles = cycles,
                                validCycles = validCycles,
                                lastRawDistanceM = null,
                                mediaVolumePercent = volume,
                                lastMeasuredAtMs = now,
                                error = null,
                            )
                        }
                    }

                    measurement.rawDistanceM > MAX_RANGE_M -> {
                        _state.update {
                            it.copy(
                                noEcho = false,
                                outOfRange = true,
                                confidence = 0f,
                                peakRatio = measurement.peakRatio,
                                directLagMs = directLagMs,
                                cycles = cycles,
                                validCycles = validCycles,
                                lastRawDistanceM = measurement.rawDistanceM,
                                mediaVolumePercent = volume,
                                lastMeasuredAtMs = now,
                                error = null,
                            )
                        }
                    }

                    else -> {
                        val previous = emaDistance
                        val smoothed = if (previous == null) {
                            measurement.rawDistanceM
                        } else {
                            previous * (1f - EMA_ALPHA) + measurement.rawDistanceM * EMA_ALPHA
                        }
                        emaDistance = smoothed
                        emaConfidence = if (validCycles == 0) {
                            measurement.confidence
                        } else {
                            emaConfidence * (1f - EMA_ALPHA) + measurement.confidence * EMA_ALPHA
                        }
                        validCycles++
                        history.add(smoothed)
                        if (history.size > HISTORY_SIZE) history.removeAt(0)
                        _state.update {
                            it.copy(
                                distanceM = smoothed,
                                confidence = emaConfidence,
                                history = history.toList(),
                                noEcho = false,
                                outOfRange = false,
                                peakRatio = measurement.peakRatio,
                                directLagMs = directLagMs,
                                cycles = cycles,
                                validCycles = validCycles,
                                lastRawDistanceM = measurement.rawDistanceM,
                                mediaVolumePercent = volume,
                                lastMeasuredAtMs = now,
                                error = null,
                            )
                        }
                    }
                }

                val elapsed = SystemClock.elapsedRealtime() - cycleStart
                val remaining = MEASURE_INTERVAL_MS - elapsed
                if (remaining > 0 && !stopFlag && myGen == generation.get()) {
                    Thread.sleep(remaining)
                }
            }
        } catch (se: SecurityException) {
            if (!stopFlag && myGen == generation.get()) {
                _state.update {
                    it.copy(
                        running = false,
                        confidence = 0f,
                        noEcho = false,
                        outOfRange = false,
                        error = L10n.get(R.string.mgr_sonar_error_no_record_permission),
                    )
                }
            }
        } catch (t: Throwable) {
            if (!stopFlag && myGen == generation.get()) {
                _state.update {
                    it.copy(
                        running = false,
                        confidence = 0f,
                        noEcho = false,
                        outOfRange = false,
                        error = t.message ?: t.javaClass.simpleName,
                    )
                }
            }
        } finally {
            runCatching { aec?.release() }
            runCatching { agc?.release() }
            runCatching { ns?.release() }
            runCatching { record?.stop() }
            runCatching { record?.release() }
            runCatching { track?.pause() }
            runCatching { track?.flush() }
            runCatching { track?.stop() }
            runCatching { track?.release() }
            // 只有当前代次的线程才能清空引用,避免快速重启时清掉新线程的资源句柄
            if (myGen == generation.get()) {
                recordRef = null
                trackRef = null
                _state.update {
                    it.copy(
                        running = false,
                        confidence = 0f,
                        noEcho = false,
                        outOfRange = false,
                        directLagMs = null,
                    )
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 匹配滤波与峰值判定
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 单轮相关计算
     *
     * 录音补零到 [FFT_SIZE],与模板的频谱做共轭相乘后 IFFT,得到
     * `corr[n] = Σ r[n+l]·c[l]`。先找直达声峰(0m 时基),再在
     * 直达声之后搜索回波峰。
     */
    private fun analyze(
        samples: ShortArray,
        recRe: FloatArray,
        recIm: FloatArray,
        corrRe: FloatArray,
        corrIm: FloatArray,
    ): SonarMeasurement {
        // 录音归一化 → 频谱
        var energy = 0.0
        for (i in 0 until RECORD_SAMPLES) {
            val v = samples[i] / 32768f
            recRe[i] = v
            energy += v.toDouble() * v
        }
        for (i in RECORD_SAMPLES until FFT_SIZE) recRe[i] = 0f
        recIm.fill(0f)
        fft(recRe, recIm)

        // 频域共轭乘:R · conj(C)
        for (k in 0 until FFT_SIZE) {
            val rr = recRe[k]
            val ri = recIm[k]
            val cr = templateRe[k]
            val ci = templateIm[k]
            corrRe[k] = rr * cr + ri * ci
            corrIm[k] = ri * cr - rr * ci
        }
        ifft(corrRe, corrIm)

        val norm = sqrt(energy * templateEnergy.toDouble()).toFloat()

        // ── 直达声峰:搜索前 400ms,它是每轮最先到达的强相关峰 ──
        val directLimit = minOf(RECORD_SAMPLES, DIRECT_MAX_LAG_SAMPLES)
        var directIdx = 0
        var directVal = -1f
        for (i in 0 until directLimit) {
            val v = abs(corrRe[i])
            if (v > directVal) {
                directVal = v
                directIdx = i
            }
        }
        val directRho = if (norm > 1e-9f) directVal / norm else 0f
        if (directRho < MIN_DIRECT_RHO) {
            return SonarMeasurement(
                directIdx = directIdx,
                directRho = directRho,
                echoIdx = -1,
                echoRho = 0f,
                peakRatio = 0f,
                rawDistanceM = -1f,
                confidence = 0f,
            )
        }

        // ── 回波峰:从直达声 + 0.2m 最小间距开始,到录音末尾 ──
        val searchStart = directIdx + MIN_LAG_SAMPLES
        val searchEnd = RECORD_SAMPLES - 1
        if (searchStart >= searchEnd) {
            return SonarMeasurement(directIdx, directRho, -1, 0f, 0f, -1f, 0f)
        }

        var echoIdx = -1
        var echoAbs = -1f
        var signedSum = 0.0
        var absSum = 0.0
        var sqSum = 0.0
        var count = 0
        for (i in searchStart..searchEnd) {
            val v = corrRe[i]
            val a = abs(v)
            signedSum += v.toDouble()
            absSum += a.toDouble()
            sqSum += v.toDouble() * v
            count++
            if (a > echoAbs) {
                echoAbs = a
                echoIdx = i
            }
        }

        val meanAbs = if (count > 0) (absSum / count).toFloat() else 0f
        val mean = if (count > 0) signedSum / count else 0.0
        val variance = if (count > 0) sqSum / count - mean * mean else 0.0
        val sigma = sqrt(variance.coerceAtLeast(0.0)).toFloat()
        val ratio = if (meanAbs > 1e-9f) echoAbs / meanAbs else 0f
        val zScore = if (sigma > 1e-9f) {
            (echoAbs - abs(mean).toFloat()) / sigma
        } else {
            0f
        }
        val echoRho = if (norm > 1e-9f) echoAbs / norm else 0f

        // 题设门限:峰值 > 3 × 相关序列平均绝对值;另加统计门限抑制纯噪声偶发峰
        val peakOk = echoAbs > 0f &&
            ratio > PEAK_RATIO_GATE &&
            zScore >= MIN_PEAK_Z &&
            echoRho >= MIN_ECHO_RHO
        if (!peakOk) {
            return SonarMeasurement(
                directIdx = directIdx,
                directRho = directRho,
                echoIdx = -1,
                echoRho = echoRho,
                peakRatio = ratio,
                rawDistanceM = -1f,
                confidence = 0f,
            )
        }

        val relLag = echoIdx - directIdx
        val rawDistance = relLag.toFloat() / SAMPLE_RATE * SPEED_OF_SOUND_MPS / 2f
        if (rawDistance < MIN_RANGE_M) {
            return SonarMeasurement(directIdx, directRho, -1, echoRho, ratio, -1f, 0f)
        }
        // 峰显著性 = 均值比分量 + z 分数分量
        val ratioConfidence = (1f - PEAK_RATIO_GATE * meanAbs / echoAbs).coerceIn(0f, 1f)
        val zConfidence = ((zScore - MIN_PEAK_Z) / (Z_CONF_FULL - MIN_PEAK_Z)).coerceIn(0f, 1f)
        val confidence = ratioConfidence * 0.5f + zConfidence * 0.5f
        return SonarMeasurement(
            directIdx = directIdx,
            directRho = directRho,
            echoIdx = echoIdx,
            echoRho = echoRho,
            peakRatio = ratio,
            rawDistanceM = rawDistance,
            confidence = confidence,
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 音频设备
    // ══════════════════════════════════════════════════════════════════════

    /** 创建 44.1kHz / 16bit / 单声道流式 AudioTrack,并把音轨增益拉到最大 */
    private fun createTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) throw IllegalStateException(L10n.get(R.string.mgr_sonar_error_no_pcm_output))
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf, chirpPcm.size * 2 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            throw IllegalStateException(L10n.get(R.string.mgr_sonar_error_speaker_init))
        }
        // 只拉满音轨增益,不改系统媒体音量:后者由用户自己控制并在 UI 中显示
        runCatching { track.setVolume(AudioTrack.getMaxVolume()) }
        return track
    }

    /**
     * 创建 44.1kHz / 16bit / 单声道 AudioRecord
     *
     * API 24+ 优先 UNPROCESSED(绕过 AEC / AGC / 降噪),失败回退 MIC。
     * 返回 null 表示设备不支持该采样率或初始化失败。
     */
    @Suppress("MissingPermission")
    private fun createRecord(): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return null
        val bufferBytes = maxOf(minBuf, RECORD_SAMPLES * 2 * 2)
        val sources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intArrayOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)
        } else {
            intArrayOf(MediaRecorder.AudioSource.MIC)
        }
        for (source in sources) {
            val rec = runCatching {
                AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes,
                )
            }.getOrNull() ?: continue
            if (rec.state == AudioRecord.STATE_INITIALIZED) {
                return rec
            }
            runCatching { rec.release() }
        }
        return null
    }

    /** 当前媒体音量百分比(0-100);系统读不到时返回 null,UI 显示 "--" */
    private fun mediaVolumePercent(): Int? = runCatching {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return@runCatching null
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return@runCatching null
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        (current * 100f / max).roundToInt().coerceIn(0, 100)
    }.getOrNull()

    // ══════════════════════════════════════════════════════════════════════
    // chirp 生成与 FFT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 生成 60ms 线性扫频 chirp
     *
     * 瞬时频率 f(t) = f0 + (f1-f0)·t/T,相位为频率对时间的积分;
     * 叠加汉宁窗包络以抑制起止爆音与频谱泄漏。
     */
    private fun buildChirpPcm(): ShortArray {
        val out = ShortArray(CHIRP_SAMPLES)
        val durationS = CHIRP_MS / 1000.0
        val sweep = (CHIRP_END_HZ - CHIRP_START_HZ).toDouble()
        for (i in 0 until CHIRP_SAMPLES) {
            val t = i.toDouble() / SAMPLE_RATE
            val phase = 2.0 * PI * (CHIRP_START_HZ * t + sweep * t * t / (2.0 * durationS))
            val envelope = 0.5 - 0.5 * cos(2.0 * PI * i / (CHIRP_SAMPLES - 1))
            val v = CHIRP_AMPLITUDE * envelope * sin(phase)
            out[i] = (v * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** 预计算 chirp 模板的 32768 点频谱与能量(只做一次) */
    @Synchronized
    private fun prepareTemplate() {
        if (templateReady) return
        templateRe.fill(0f)
        templateIm.fill(0f)
        var energy = 0.0
        for (i in 0 until CHIRP_SAMPLES) {
            val v = chirpTemplate[i]
            templateRe[i] = v
            energy += v.toDouble() * v
        }
        fft(templateRe, templateIm)
        templateEnergy = energy.toFloat()
        templateReady = true
    }

    /**
     * radix-2 迭代 FFT(Cooley-Tukey,位反转 + 蝶形)
     *
     * 旋转因子查表;正向变换不做 1/N 归一化,逆变换负责缩放。
     */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = FFT_SIZE
        // 位反转置换
        for (i in 0 until n) {
            val j = bitRev[i]
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        // 蝶形运算
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val step = n / len
            var base = 0
            while (base < n) {
                for (k in 0 until half) {
                    val wRe = cosTable[k * step]
                    val wIm = sinTable[k * step]
                    val a = base + k
                    val b = a + half
                    val vRe = re[b] * wRe - im[b] * wIm
                    val vIm = re[b] * wIm + im[b] * wRe
                    re[b] = re[a] - vRe
                    im[b] = im[a] - vIm
                    re[a] += vRe
                    im[a] += vIm
                }
                base += len
            }
            len = len shl 1
        }
    }

    /** IFFT = conj(FFT(conj(X))) / N */
    private fun ifft(re: FloatArray, im: FloatArray) {
        for (i in re.indices) im[i] = -im[i]
        fft(re, im)
        val inv = 1f / re.size
        for (i in re.indices) {
            re[i] *= inv
            im[i] = -im[i] * inv
        }
    }

    /** 单轮测量结果;echoIdx < 0 表示没有满足门限的回波 */
    private data class SonarMeasurement(
        val directIdx: Int,
        val directRho: Float,
        val echoIdx: Int,
        val echoRho: Float,
        val peakRatio: Float,
        val rawDistanceM: Float,
        val confidence: Float,
    )

    companion object {
        /** 采样率 —— 播放与录音统一 44.1kHz */
        const val SAMPLE_RATE = 44100

        /** chirp 扫频范围与时长 */
        const val CHIRP_START_HZ = 3000
        const val CHIRP_END_HZ = 8000
        const val CHIRP_MS = 60
        const val CHIRP_SAMPLES = SAMPLE_RATE * CHIRP_MS / 1000

        /** 每轮录音 0.5s(22050 样本),补零到 32768 点做 FFT */
        const val RECORD_MS = 500
        const val RECORD_SAMPLES = SAMPLE_RATE * RECORD_MS / 1000
        const val FFT_SIZE = 32768

        /** 连续测量周期 */
        const val MEASURE_INTERVAL_MS = 1500L

        /** 历史火花线保留的有效测量数 */
        const val HISTORY_SIZE = 40

        /** 距离 EMA 平滑系数 */
        const val EMA_ALPHA = 0.5f

        /** 20°C 干燥空气中的声速(m/s) */
        const val SPEED_OF_SOUND_MPS = 343f

        /** 报告量程 */
        const val MIN_RANGE_M = 0.2f
        const val MAX_RANGE_M = 8f

        /** 0.2m 与 8m 对应的回波相对时延(样本);最小值向上取整 */
        val MIN_LAG_SAMPLES: Int = ceil(
            MIN_RANGE_M * 2.0 * SAMPLE_RATE / SPEED_OF_SOUND_MPS,
        ).toInt()
        val MAX_LAG_SAMPLES: Int =
            (MAX_RANGE_M * 2.0 * SAMPLE_RATE / SPEED_OF_SOUND_MPS).toInt()

        /** FFT 长度 32768 = 2^15 */
        private const val LOG2_FFT = 15

        /** chirp 峰值幅度(0-1),留出扬声器/录音链路余量 */
        private const val CHIRP_AMPLITUDE = 0.70

        /** 先录本底再打 chirp 的前置时长 */
        private const val RECORD_PREROLL_MS = 40L

        /** 直达声归一化相关的最低要求;低于它认为没有捕捉到 chirp 直达声 */
        private const val MIN_DIRECT_RHO = 0.05f

        /** 回波归一化相关的最低要求,排除数值本底 */
        private const val MIN_ECHO_RHO = 0.0005f

        /** 题设峰值/均值比门限 */
        private const val PEAK_RATIO_GATE = 3f

        /** 回波峰相对相关本底的最低 z 分数,抑制纯噪声偶发峰 */
        private const val MIN_PEAK_Z = 6f

        /** z 分数达到该值时置信度饱和为 1 */
        private const val Z_CONF_FULL = 30f

        /** 直达声搜索上限:超过它仍未找到强相关峰,视为未捕获直达声 */
        private const val DIRECT_MAX_LAG_MS = 400
        private val DIRECT_MAX_LAG_SAMPLES = SAMPLE_RATE * DIRECT_MAX_LAG_MS / 1000
    }
}
