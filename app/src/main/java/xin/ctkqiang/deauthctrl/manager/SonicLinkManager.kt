package xin.ctkqiang.deauthctrl.manager

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 声波数据链状态
 *
 * @param sending       TX 是否正在播放
 * @param sendProgress  TX 播放进度 0..1（来自 AudioTrack 播放头，不是估算）
 * @param running       RX 是否正在监听
 * @param received      已换行提交的解码文本（保留最近 100 行）
 * @param currentSymbol TX 播放头当前所在符号，间隔帧保持上一个符号
 * @param rxSymbol      RX 最近一次确认的符号
 * @param currentLine   RX 尚未换行的解码缓冲
 * @param rxLevel       RX 麦克风相对电平 0..1（20log10(rms/32768) 映射到 -60..0 dBFS）
 * @param error         最近一次错误（中文），成功启动新任务时清空
 */
data class SonicState(
    val sending: Boolean = false,
    val sendProgress: Float = 0f,
    val running: Boolean = false,
    val received: List<String> = emptyList(),
    val currentSymbol: String = "",
    val rxSymbol: String = "",
    val currentLine: String = "",
    val rxLevel: Float = 0f,
    val error: String? = null,
)

/**
 * 声波数据链 —— DTMF（ITU-T Q.23）调制 / 解调
 *
 * ## 调制（TX）
 * 标准 DTMF 双音表：行频 697 / 770 / 852 / 941 Hz，列频 1209 / 1336 / 1477 / 1633 Hz，
 * 共 16 个符号（1-9、*、0、#、A-D）。每个符号由「行频正弦 + 列频正弦」叠加成
 * 44.1kHz / 16bit / 单声道 PCM，符号时长 120ms，符号间隔 40ms，用 AudioTrack
 * 流式播放；发送进度直接读取播放头位置。
 *
 * 文本处理：小写自动转大写；只发送 0-9、A-D、*、# 与空格，空格以 240ms 静音表示，
 * 其余字符一律跳过（不做字母扩展或转义）。
 *
 * ## 解调（RX）
 * AudioRecord 以 44.1kHz / 16bit / 单声道连续采集，每 40ms（1764 采样）为一帧：
 * 1. 计算本帧 RMS，低于静音门限则判为符号间隔；
 * 2. 按 RMS 归一化（自动增益）后加汉宁窗；
 * 3. 对 8 个 DTMF 频率各跑一次 Goertzel，取行频最强 + 列频最强；
 * 4. 行/列最强值必须超过绝对门限且领先本组次强值一定倍数，才认为是有效双音；
 * 5. 连续 3 帧同一符号才确认（约 120ms，抗噪），静音用于重置候选并判定边界。
 *
 * 该实现不处理拨号音、DTMF twist 校准、回声消除；外放对麦克风、安静环境 1-3 米内
 * 可用，强噪声/混响下会漏码。声波无加密，任何能听见的设备都可解码。
 */
class SonicLinkManager {

    private val _state = MutableStateFlow(SonicState())
    val state: StateFlow<SonicState> = _state

    // ─── 发射资源 ───
    @Volatile private var stopSend = false
    @Volatile private var trackRef: AudioTrack? = null

    // ─── 接收资源 ───
    @Volatile private var stopReceive = false
    @Volatile private var recordRef: AudioRecord? = null

    // ─── RX 解码状态（仅接收线程访问；lineBuffer 另有同步保护） ───
    private val lineBuffer = StringBuilder()
    private var candidateSymbol: Char = 0.toChar()
    private var candidateFrames = 0
    private var symbolConfirmed = false
    private var silenceFrames = 0
    private var spaceInserted = false
    /** 最近一次确认双音时的 RMS，用于相对静音门限（抗环境底噪） */
    private var lastToneRms = 0f
    private val normBuf = FloatArray(FRAME_SIZE)
    private val hann = FloatArray(FRAME_SIZE) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (FRAME_SIZE - 1))).toFloat()
    }

    // ══════════════════════════════════════════════════════════════════════
    // 发射
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 发送一段文本（异步；立即返回，进度通过 [state] 透传）
     *
     * 只认识 0-9、A-D、*、# 与空格；其它字符被跳过。若过滤后没有任何符号，
     * 直接写入 error，不启动音频线程。
     */
    fun send(text: String) {
        if (_state.value.sending) return
        val elements = buildTxSequence(text)
        if (elements.isEmpty()) {
            _state.update {
                it.copy(error = L10n.get(R.string.mgr_sonic_error_no_symbols))
            }
            return
        }
        stopSend = false
        _state.update {
            it.copy(sending = true, sendProgress = 0f, currentSymbol = "", error = null)
        }
        thread(name = "sonic-tx") {
            var track: AudioTrack? = null
            try {
                // 先整体生成 PCM，再流式写入；播放头负责真实进度
                val plan = buildTxPlan(elements)
                track = createAudioTrack()
                trackRef = track
                track.play()

                var written = 0
                while (written < plan.pcm.size && !stopSend) {
                    val chunk = minOf(WRITE_CHUNK, plan.pcm.size - written)
                    val n = track.write(plan.pcm, written, chunk, AudioTrack.WRITE_BLOCKING)
                    if (n <= 0) {
                        throw IllegalStateException(L10n.get(R.string.mgr_sonic_error_track_write, n))
                    }
                    written += n
                    publishTxProgress(track, plan)
                }
                // 等待缓冲区里的最后一段真正播放完
                while (!stopSend && playbackFrames(track) < plan.totalFrames) {
                    publishTxProgress(track, plan)
                    Thread.sleep(20L)
                }
                publishTxProgress(track, plan)
            } catch (t: Throwable) {
                if (!stopSend) {
                    _state.update { it.copy(error = t.message ?: t.javaClass.simpleName) }
                }
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.flush() }
                runCatching { track?.release() }
                trackRef = null
                _state.update { it.copy(sending = false, sendProgress = 0f, currentSymbol = "") }
            }
        }
    }

    /** 停止发送：标记停止并让阻塞中的 AudioTrack.write 立即返回 */
    fun stopSending() {
        stopSend = true
        runCatching { trackRef?.stop() }
    }

    /** 把播放头换算成进度与当前符号 */
    private fun publishTxProgress(track: AudioTrack, plan: TxPlan) {
        val head = playbackFrames(track)
        val progress = if (plan.totalFrames <= 0) 1f else {
            (head.toFloat() / plan.totalFrames.toFloat()).coerceIn(0f, 1f)
        }
        val mark = plan.marks.lastOrNull { head >= it.startFrame }
        _state.update {
            it.copy(sendProgress = progress, currentSymbol = mark?.label ?: it.currentSymbol)
        }
    }

    /** AudioTrack 播放头（帧数，处理 32 位回绕） */
    private fun playbackFrames(track: AudioTrack): Long =
        track.playbackHeadPosition.toLong() and 0xFFFFFFFFL

    /** 生成整段 PCM 与「播放头 -> 符号」时间标记 */
    private fun buildTxPlan(elements: List<TxElement>): TxPlan {
        var total = 0
        for (e in elements) {
            total += (if (e.symbol == null) SPACE_FRAMES else TONE_FRAMES) + GAP_FRAMES
        }
        val pcm = ShortArray(total)
        val marks = ArrayList<TxMark>(elements.size)
        var pos = 0
        for (e in elements) {
            val start = pos
            val len = if (e.symbol == null) SPACE_FRAMES else TONE_FRAMES
            if (e.symbol != null) {
                // 首尾 5ms 渐入渐出，避免切换时的爆音
                val fade = (SAMPLE_RATE / 200).coerceAtMost(len / 4)
                for (i in 0 until len) {
                    val env = when {
                        i < fade -> i.toDouble() / fade
                        i >= len - fade -> (len - i).toDouble() / fade
                        else -> 1.0
                    }
                    val t = i.toDouble() / SAMPLE_RATE
                    // 双音叠加：每个分量 0.25 满幅，峰值不超过 0.5
                    val v = 0.25 * sin(2.0 * PI * e.rowFreq * t) +
                        0.25 * sin(2.0 * PI * e.colFreq * t)
                    pcm[pos + i] = (v * env * 32767.0).toInt().toShort()
                }
            }
            pos += len
            marks.add(TxMark(start, e.label))
            pos += GAP_FRAMES
        }
        return TxPlan(pcm, total, marks)
    }

    /** 文本 -> 发送元素序列（小写转大写，空格=静音，其它字符跳过） */
    private fun buildTxSequence(text: String): List<TxElement> {
        val out = ArrayList<TxElement>()
        for (raw in text) {
            if (out.size >= MAX_TX_SYMBOLS) break
            val c = raw.uppercaseChar()
            if (c == ' ') {
                out.add(TxElement(null, L10n.get(R.string.mgr_sonic_symbol_space)))
                continue
            }
            val freq = frequenciesFor(c) ?: continue
            out.add(TxElement(c, c.toString(), freq.first, freq.second))
        }
        return out
    }

    /** 创建流式 AudioTrack（44.1kHz / 16bit / 单声道） */
    private fun createAudioTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) throw IllegalStateException(L10n.get(R.string.mgr_sonic_error_no_pcm_output))
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
            .setBufferSizeInBytes(maxOf(minBuf, WRITE_CHUNK * 2 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            throw IllegalStateException(L10n.get(R.string.mgr_sonic_error_track_init))
        }
        return track
    }

    // ══════════════════════════════════════════════════════════════════════
    // 接收
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 开始监听（异步；RECORD_AUDIO 已由应用网关统一申请）
     *
     * 初始化失败、权限缺失、麦克风被占用都会写入 error，不会抛出到调用方。
     */
    @Suppress("MissingPermission")
    fun startReceiving() {
        if (_state.value.running) return
        stopReceive = false
        resetDecoder()
        _state.update {
            it.copy(running = true, error = null, rxLevel = 0f, rxSymbol = "", currentLine = "")
        }
        thread(name = "sonic-rx") {
            var record: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                if (minBuf <= 0) throw IllegalStateException(L10n.get(R.string.mgr_sonic_error_no_mic_capture))
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, FRAME_SIZE * 2 * 4),
                )
                recordRef = record
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException(L10n.get(R.string.mgr_sonic_error_mic_init))
                }
                record.startRecording()

                val frame = ShortArray(FRAME_SIZE)
                while (!stopReceive) {
                    // 阻塞式读满一整帧（40ms）
                    var read = 0
                    while (read < FRAME_SIZE && !stopReceive) {
                        val n = record.read(frame, read, FRAME_SIZE - read)
                        if (n < 0) {
                            throw IllegalStateException(L10n.get(R.string.mgr_sonic_error_mic_read, n))
                        }
                        if (n == 0) break
                        read += n
                    }
                    if (stopReceive) break
                    if (read < FRAME_SIZE) continue
                    processFrame(frame)
                }
            } catch (se: SecurityException) {
                _state.update {
                    it.copy(error = L10n.get(R.string.mgr_sonic_error_no_record_permission))
                }
            } catch (t: Throwable) {
                if (!stopReceive) {
                    _state.update { it.copy(error = t.message ?: t.javaClass.simpleName) }
                }
            } finally {
                runCatching { record?.stop() }
                runCatching { record?.release() }
                recordRef = null
                commitLine()
                _state.update { it.copy(running = false, rxLevel = 0f, rxSymbol = "") }
            }
        }
    }

    /** 停止监听：标记停止并解除 AudioRecord.read 的阻塞 */
    fun stopReceiving() {
        stopReceive = true
        runCatching { recordRef?.stop() }
    }

    /** 页面销毁时统一释放发送 / 接收资源 */
    fun stopAll() {
        stopSending()
        stopReceiving()
    }

    /** 清空已收文本与未换行缓冲 */
    fun clearReceived() {
        resetDecoder()
        _state.update { it.copy(received = emptyList(), currentLine = "") }
    }

    // ─── 单帧解码 ───

    /**
     * 处理一帧（40ms）PCM
     *
     * 静音帧用于重置候选符号，并在持续静音达到阈值时补空格 / 换行；
     * 有声帧归一化后跑 8 个 Goertzel，双音有效且连续 3 帧一致才落一个符号。
     */
    private fun processFrame(frame: ShortArray) {
        var sumSq = 0.0
        for (v in frame) sumSq += v.toDouble() * v
        val rms = sqrt(sumSq / frame.size).toFloat()
        val level = levelOf(rms)

        // ── 静音：符号边界 ──
        // 绝对门限之外再加相对门限：环境底噪高于绝对门限时，
        // 只要当前帧明显低于最近一次双音的 RMS，仍判为符号间隔。
        val silent = rms < SILENCE_RMS ||
            (lastToneRms > 0f && rms < lastToneRms * TONE_GAP_RATIO)
        if (silent) {
            silenceFrames++
            candidateSymbol = 0.toChar()
            candidateFrames = 0
            symbolConfirmed = false
            if (!spaceInserted && silenceFrames >= SPACE_MIN_FRAMES) {
                // 240ms 的空格会跨越 4-7 帧（帧对齐抖动），首个越过阈值的帧补一个空格
                spaceInserted = true
                appendSpace()
            } else if (silenceFrames >= LINE_GAP_FRAMES) {
                // 600ms 以上静音视为报文换行
                commitLine()
            }
            _state.update { it.copy(rxLevel = level) }
            return
        }
        silenceFrames = 0
        spaceInserted = false

        // ── 自动增益：按本帧 RMS 归一化，再乘汉宁窗 ──
        val invRms = 1f / rms
        for (i in 0 until FRAME_SIZE) normBuf[i] = frame[i] * invRms * hann[i]

        // ── 8 个频率各跑一次 Goertzel，分别取行 / 列最强 ──
        var bestRow = 0
        var bestRowMag = 0f
        var secondRowMag = 0f
        for (r in 0..3) {
            val mag = goertzel(normBuf, ROW_FREQS[r])
            if (mag > bestRowMag) {
                secondRowMag = bestRowMag
                bestRowMag = mag
                bestRow = r
            } else if (mag > secondRowMag) {
                secondRowMag = mag
            }
        }
        var bestCol = 0
        var bestColMag = 0f
        var secondColMag = 0f
        for (c in 0..3) {
            val mag = goertzel(normBuf, COL_FREQS[c])
            if (mag > bestColMag) {
                secondColMag = bestColMag
                bestColMag = mag
                bestCol = c
            } else if (mag > secondColMag) {
                secondColMag = mag
            }
        }

        // 行、列都必须有明确最强频点；单音 / 噪声会被这里拒绝
        val rowOk = bestRowMag >= MIN_TONE_MAG &&
            bestRowMag >= secondRowMag * GROUP_DOMINANCE
        val colOk = bestColMag >= MIN_TONE_MAG &&
            bestColMag >= secondColMag * GROUP_DOMINANCE
        if (!rowOk || !colOk) {
            // 说话声 / 环境噪声：不确认符号，但也不打断正在累积的候选，
            // 允许 120ms 符号里偶发 1-2 个坏帧
            _state.update { it.copy(rxLevel = level) }
            return
        }

        val symbol = GRID[bestRow][bestCol]
        lastToneRms = rms
        if (symbol == candidateSymbol) {
            candidateFrames++
        } else {
            candidateSymbol = symbol
            candidateFrames = 1
            symbolConfirmed = false
        }
        // 连续 3 帧（约 120ms）同一符号才确认，抗噪
        if (candidateFrames >= CONFIRM_FRAMES && !symbolConfirmed) {
            symbolConfirmed = true
            appendSymbol(symbol)
        }
        _state.update { it.copy(rxLevel = level) }
    }

    /** 单频点 Goertzel 幅度（已补偿汉宁窗 0.5 相干增益） */
    private fun goertzel(samples: FloatArray, freq: Double): Float {
        val n = samples.size
        val w = 2.0 * PI * freq / SAMPLE_RATE
        val coeff = 2.0 * cos(w)
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until n) {
            val s0 = samples[i] + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        if (power <= 0.0) return 0f
        return (sqrt(power) * 2.0 / n / HANN_GAIN).toFloat()
    }

    /** 原始 RMS 映射为 0..1 的相对电平（-60..0 dBFS） */
    private fun levelOf(rms: Float): Float {
        if (rms <= 0.5f) return 0f
        val db = 20.0 * log10((rms / 32768.0).coerceAtLeast(1e-7))
        return ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
    }

    // ─── 解码文本缓冲 ───

    private fun appendSymbol(symbol: Char) {
        val overflow = synchronized(lineBuffer) {
            lineBuffer.append(symbol)
            lineBuffer.length >= MAX_LINE_CHARS
        }
        if (overflow) {
            commitLine()
        } else {
            _state.update {
                it.copy(currentLine = bufferText(), rxSymbol = symbol.toString())
            }
        }
    }

    private fun appendSpace() {
        val text = synchronized(lineBuffer) {
            if (lineBuffer.isEmpty() || lineBuffer.last() == ' ') return
            lineBuffer.append(' ')
            lineBuffer.toString()
        }
        _state.update { it.copy(currentLine = text) }
    }

    /** 把当前行提交到 received（空缓冲时无操作） */
    private fun commitLine() {
        val line = synchronized(lineBuffer) {
            if (lineBuffer.isEmpty()) return
            val s = lineBuffer.toString()
            lineBuffer.setLength(0)
            s
        }
        _state.update {
            it.copy(received = (it.received + line).takeLast(MAX_LINES), currentLine = "")
        }
    }

    private fun bufferText(): String = synchronized(lineBuffer) { lineBuffer.toString() }

    private fun resetDecoder() {
        synchronized(lineBuffer) { lineBuffer.setLength(0) }
        candidateSymbol = 0.toChar()
        candidateFrames = 0
        symbolConfirmed = false
        silenceFrames = 0
        spaceInserted = false
        lastToneRms = 0f
    }

    // ─── 内部数据类 ───

    private data class TxElement(
        val symbol: Char?,
        val label: String,
        val rowFreq: Double = 0.0,
        val colFreq: Double = 0.0,
    )

    private data class TxMark(val startFrame: Int, val label: String)

    private data class TxPlan(
        val pcm: ShortArray,
        val totalFrames: Int,
        val marks: List<TxMark>,
    )

    companion object {
        /** 采样率 —— 发送与接收统一使用 44.1kHz */
        const val SAMPLE_RATE = 44100

        /** 接收帧长 40ms = 1764 采样 */
        const val FRAME_MS = 40
        const val FRAME_SIZE = SAMPLE_RATE * FRAME_MS / 1000

        /** 发送符号 120ms、间隔 40ms */
        const val TONE_MS = 120
        const val GAP_MS = 40
        const val TONE_FRAMES = SAMPLE_RATE * TONE_MS / 1000
        const val GAP_FRAMES = SAMPLE_RATE * GAP_MS / 1000

        /** 空格 = 240ms 静音（6 帧对齐） */
        const val SPACE_FRAMES = 6

        /** 连续 3 帧（约 120ms）同一符号才确认 */
        const val CONFIRM_FRAMES = 3

        /** 静音门限（16bit PCM 的 RMS 值） */
        const val SILENCE_RMS = 450f

        /** 有效双音的最低归一化幅度 */
        const val MIN_TONE_MAG = 0.18f

        /** 本组最强频点相对次强频点的最小领先倍数 */
        const val GROUP_DOMINANCE = 1.8f

        /** 相对静音门限：当前帧 RMS 低于最近双音的该比例即视为间隔 */
        private const val TONE_GAP_RATIO = 0.15f

        /** 汉宁窗相干增益 */
        const val HANN_GAIN = 0.5

        /** 持续静音达到 4 帧（约 160ms）补一个空格 */
        private const val SPACE_MIN_FRAMES = 4

        /** 持续静音达到 15 帧（约 600ms）结束当前行 */
        private const val LINE_GAP_FRAMES = 15

        private const val MAX_LINE_CHARS = 64
        private const val MAX_LINES = 100
        private const val MAX_TX_SYMBOLS = 128
        private const val WRITE_CHUNK = 2048

        /** ITU-T DTMF 行频（Hz） */
        val ROW_FREQS = doubleArrayOf(697.0, 770.0, 852.0, 941.0)

        /** ITU-T DTMF 列频（Hz） */
        val COL_FREQS = doubleArrayOf(1209.0, 1336.0, 1477.0, 1633.0)

        /** 行 × 列 -> 符号（标准电话键盘，第四列为 A-D） */
        val GRID: List<List<Char>> = listOf(
            listOf('1', '2', '3', 'A'),
            listOf('4', '5', '6', 'B'),
            listOf('7', '8', '9', 'C'),
            listOf('*', '0', '#', 'D'),
        )

        /** 查符号对应的行频 / 列频；不支持的字符返回 null */
        fun frequenciesFor(symbol: Char): Pair<Double, Double>? {
            val c = symbol.uppercaseChar()
            for (r in GRID.indices) {
                val col = GRID[r].indexOf(c)
                if (col >= 0) return ROW_FREQS[r] to COL_FREQS[col]
            }
            return null
        }

        /** 过滤出真正会被发送的符号（小写转大写，空格保留） */
        fun sanitize(text: String): String = buildString {
            for (raw in text) {
                val c = raw.uppercaseChar()
                if (c == ' ' || frequenciesFor(c) != null) append(c)
            }
        }

        /** 统计会被跳过的字符数 */
        fun unsupportedCount(text: String): Int = text.count { raw ->
            val c = raw.uppercaseChar()
            c != ' ' && frequenciesFor(c) == null
        }
    }
}
