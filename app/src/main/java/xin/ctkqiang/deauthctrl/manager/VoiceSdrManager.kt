package xin.ctkqiang.deauthctrl.manager

import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** 一帧频谱快照 —— 2048 个频点幅度（dBFS） + 峰值信息 */
data class SpectrumFrame(
    val bins: FloatArray,       // 0-22.05kHz 共 2048 点，dBFS（-120..0）
    val peakHz: Float,
    val peakDb: Float,
    val rmsDb: Float,
    val noiseFloorDb: Float,    // 噪声底估计(bin 幅度中位数)
    val timeMs: Long,
)

/**
 * 语音频谱分析器 —— SDR 风格实时频谱
 *
 * 麦克风 44.1kHz/16bit 单声道采集 → 4096 点汉宁窗 → radix-2 FFT
 * → 2048 个频点（分辨率 ≈10.77Hz，覆盖 0-22.05kHz）→ dBFS。
 * 手机麦克风采样率上限约 48kHz，因此可观测频段为音频全频段
 * （语音基频、环境噪声、超声波 20kHz 以上的部分取决于硬件，
 * 22.05kHz 是奈奎斯特理论上限）。
 */
class VoiceSdrManager(private val context: Context) {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _frame = MutableStateFlow<SpectrumFrame?>(null)
    val frame: StateFlow<SpectrumFrame?> = _frame

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** 频段模式:0=全频段(默认) 1=超声聚焦(15-22.05kHz) */
    private val _band = MutableStateFlow(0)
    val band: StateFlow<Int> = _band

    /**
     * 超声告警:18-22kHz 频段能量显著高于噪声底时置 true(带迟滞)。
     * 该频段正是电子器件 coil whine / 超声源的特征区;消费级麦克风在 20kHz+
     * 衰减严重,可用性取决于硬件 —— 告警为启发式,不是鉴定。
     */
    private val _ultrasonicAlarm = MutableStateFlow(false)
    val ultrasonicAlarm: StateFlow<Boolean> = _ultrasonicAlarm

    fun setBand(band: Int) { _band.value = if (band == 1) 1 else 0 }

    @Volatile private var stop = false
    @Volatile private var recordRef: AudioRecord? = null
    private var threadRef: Thread? = null

    @Suppress("MissingPermission")
    fun start() {
        if (_running.value) return
        stop = false
        _running.value = true
        threadRef = thread(name = "sdr-fft") {
            var record: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val bufferSize = maxOf(minBuf, FFT_SIZE * 2)
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize,
                )
                recordRef = record
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    _error.value = L10n.get(R.string.mgr_sdr_mic_init_failed)
                    return@thread
                }
                record.startRecording()

                val samples = ShortArray(FFT_SIZE)
                val re = FloatArray(FFT_SIZE)
                val im = FloatArray(FFT_SIZE)
                val window = hannWindow(FFT_SIZE)

                while (!stop) {
                    // 阻塞式整块读取（2 的幂样本数保证 FFT 对齐）
                    var read = 0
                    while (read < FFT_SIZE && !stop) {
                        val n = record.read(samples, read, FFT_SIZE - read)
                        if (n <= 0) break
                        read += n
                    }
                    if (read < FFT_SIZE) continue

                    for (i in 0 until FFT_SIZE) {
                        val v = samples[i] * window[i]
                        re[i] = v
                        im[i] = 0f
                    }
                    fft(re, im)

                    val bins = FloatArray(BINS)
                    var peakDb = -140f
                    var peakHz = 0f
                    var sumSq = 0.0
                    for (i in 0 until BINS) {
                        val mag = sqrt(re[i] * re[i] + im[i] * im[i])
                        val db = (20f * log10((mag / FFT_SIZE / 32768f).coerceAtLeast(1e-9f))).toFloat()
                        bins[i] = db.coerceIn(-120f, 0f)
                        sumSq += (mag * mag).toDouble()
                        if (db > peakDb) {
                            peakDb = db
                            peakHz = i * SAMPLE_RATE.toFloat() / FFT_SIZE
                        }
                    }
                    val rmsDb = (20f * log10((sqrt(sumSq / BINS) / FFT_SIZE / 32768f).coerceAtLeast(1e-9))).toFloat()
                    // 噪声底:bin 幅度的中位数(比平均值更抗峰值干扰)
                    val sorted = bins.copyOf().apply { sort() }
                    val noiseFloor = sorted[BINS / 2].coerceIn(-120f, 0f)
                    // 超声监测:18-22kHz 频段能量(18k/10.77≈1671 bin,22.05k=2048 全部)
                    val usLo = (18_000f / BIN_HZ).toInt().coerceIn(0, BINS - 1)
                    val usHi = BINS - 1
                    var usSum = 0f
                    for (i in usLo..usHi) usSum += bins[i]
                    val usMean = usSum / (usHi - usLo + 1)
                    val usAlarm = usMean > -78f && usMean > noiseFloor + 18f
                    if (usAlarm != _ultrasonicAlarm.value) _ultrasonicAlarm.value = usAlarm
                    _frame.value = SpectrumFrame(
                        bins = bins,
                        peakHz = peakHz,
                        peakDb = peakDb,
                        rmsDb = rmsDb.coerceIn(-120f, 0f),
                        noiseFloorDb = noiseFloor,
                        timeMs = System.currentTimeMillis(),
                    )
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: t.javaClass.simpleName
            } finally {
                runCatching { record?.stop() }
                runCatching { record?.release() }
                recordRef = null
                _running.value = false
            }
        }
    }

    fun stop() {
        stop = true
        // stop() 会解除 AudioRecord.read 的阻塞（interrupt 对 native read 无效）
        runCatching { recordRef?.stop() }
    }

    /** radix-2 迭代 FFT（Cooley-Tukey，位反转 + 蝶形） */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        // 位反转置换
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        // 蝶形运算
        var len = 2
        while (len <= n) {
            val ang = (-2.0 * PI / len).toFloat()
            val wLenRe = cos(ang)
            val wLenIm = sin(ang)
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until len / 2) {
                    val idxA = i + k
                    val idxB = i + k + len / 2
                    val uRe = re[idxA]
                    val uIm = im[idxA]
                    val vRe = re[idxB] * wRe - im[idxB] * wIm
                    val vIm = re[idxB] * wIm + im[idxB] * wRe
                    re[idxA] = uRe + vRe
                    im[idxA] = uIm + vIm
                    re[idxB] = uRe - vRe
                    im[idxB] = uIm - vIm
                    val nwRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nwRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun hannWindow(n: Int): FloatArray = FloatArray(n) { i ->
        (0.5f - 0.5f * cos(2.0 * PI * i / (n - 1))).toFloat()
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val FFT_SIZE = 4096
        const val BINS = FFT_SIZE / 2
        /** 频点分辨率 ≈ 44100/4096 ≈ 10.77Hz */
        const val BIN_HZ = SAMPLE_RATE.toFloat() / FFT_SIZE
    }
}
