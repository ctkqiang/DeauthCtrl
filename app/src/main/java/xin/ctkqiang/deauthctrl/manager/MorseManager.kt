package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/** 摩尔斯发送状态 */
data class MorseState(
    val sending: Boolean = false,
    val progress: Float = 0f,      // 0..1
    val currentChar: String = "",  // 正在发送的字符
    val decoded: String = "",      // 手动打键实时解码结果
    val torchAvailable: Boolean = false,
    val error: String? = null,
)

/**
 * 摩尔斯码引擎 —— 声音蜂鸣 + 手电筒静默模式 + 双向翻译
 *
 * 标准国际摩尔斯:dot=1 单位 / dash=3 单位 / 元素间 1 单位 / 字符间 3 单位 / 词间 7 单位;
 * 单位时长由 WPM 决定(Paris 标准: WPM 5 = 240ms 单位)。
 * 静默模式用 Camera2 手电筒闪光打码(需 CAMERA 权限与闪光灯硬件)。
 */
class MorseManager(private val context: Context) {

    private val _state = MutableStateFlow(MorseState())
    val state: StateFlow<MorseState> = _state

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val torchCameraId: String? = runCatching {
        cameraManager?.cameraIdList?.firstOrNull { id ->
            cameraManager?.getCameraCharacteristics(id)
                ?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    init {
        _state.value = _state.value.copy(torchAvailable = torchCameraId != null)
    }

    @Volatile private var stopRequested = false

    /** 单位时长(ms):Paris 标准 WPM */
    fun unitMs(wpm: Int): Long = (1200 / wpm.coerceIn(1, 60)).toLong()

    /** 发送文本(阻塞线程执行,调用方自行起线程) */
    fun send(text: String, wpm: Int, mode: MorseSendMode) {
        if (_state.value.sending) return
        stopRequested = false
        val unit = unitMs(wpm)
        val upper = text.uppercase()
        val chars = upper.mapNotNull { MORSE[it] ?: MORSE[' '] }
        val total = chars.sumOf { it.length + if (it == " ") 0 else 1 } // 粗略进度单元
        _state.value = _state.value.copy(sending = true, progress = 0f, error = null)
        thread(name = "morse-send") {
            try {
                var done = 0
                for ((ci, ch) in upper.withIndex()) {
                    if (stopRequested) break
                    val code = MORSE[ch] ?: continue
                    _state.value = _state.value.copy(currentChar = ch.toString())
                    for (sym in code) {
                        if (stopRequested) break
                        emit(sym == '.', unit, mode)
                        done++
                        _state.value = _state.value.copy(progress = done.toFloat() / total.toFloat().coerceAtLeast(1f))
                        silence(unit, mode)
                    }
                    // 字符间隔 3 单位(减去元素间已隔的 1 单位)
                    silence(unit * 2, mode)
                    done++
                    // 词间隔再补 4 单位
                    if (ch == ' ') silence(unit * 4, mode)
                    _state.value = _state.value.copy(progress = done.toFloat() / total.toFloat().coerceAtLeast(1f))
                    if (ci % 8 == 0) Thread.yield()
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message)
            } finally {
                torchOff()
                _state.value = _state.value.copy(sending = false, currentChar = "")
            }
        }
    }

    /** 停止发送 */
    fun stop() {
        stopRequested = true
        torchOff()
    }

    // ─── 打键解码 ───

    private val pendingCode = StringBuilder()
    private var wordGap = false

    /** 手动打键:按下/松开一个元素(点=短按,划=长按由 UI 判定;或直接给符号) */
    fun keyElement(symbol: Char) {
        if (symbol == '.') pendingCode.append('.')
        else if (symbol == '-') pendingCode.append('-')
    }

    /** 结束当前字符:解码并附加到输出 */
    fun keyCharEnd() {
        if (pendingCode.isEmpty()) return
        val decoded = MORSE_REVERSE[pendingCode.toString()]
        if (decoded != null) {
            _state.value = _state.value.copy(decoded = _state.value.decoded + decoded)
        }
        pendingCode.clear()
    }

    /** 词间隔(空格) */
    fun keyWordEnd() {
        keyCharEnd()
        _state.value = _state.value.copy(decoded = _state.value.decoded + " ")
    }

    /** 清空解码结果 */
    fun keyClear() {
        pendingCode.clear()
        _state.value = _state.value.copy(decoded = "")
    }

    /** 当前未完成的打键码(UI 显示用) */
    fun pendingCodeText(): String = pendingCode.toString()

    // ─── 输出硬件 ───

    private var audioTrack: AudioTrack? = null
    private val toneFreq = 800.0

    private fun emit(on: Boolean, unit: Long, mode: MorseSendMode) {
        val duration = if (on) unit * 3 else unit
        val useSound = mode == MorseSendMode.SOUND || mode == MorseSendMode.BOTH
        val useTorch = mode == MorseSendMode.TORCH || mode == MorseSendMode.BOTH
        if (useSound) tone(duration)
        if (useTorch) torch(true)
        Thread.sleep(duration)
        if (useTorch) torch(false)
        if (useSound) silenceTone()
    }

    private fun silence(unit: Long, mode: MorseSendMode) {
        Thread.sleep(unit)
    }

    private fun tone(durationMs: Long) {
        runCatching {
            val sampleRate = 44100
            val n = (sampleRate * durationMs / 1000).toInt().coerceAtLeast(1)
            val buf = ShortArray(n)
            for (i in 0 until n) {
                buf[i] = (sin(2 * PI * toneFreq * i / sampleRate) * 12000).toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(buf.size * 2)
                .build()
            audioTrack = track
            track.play()
            track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
        }
    }

    private fun silenceTone() {
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
    }

    private fun torch(on: Boolean) {
        runCatching {
            cameraManager?.setTorchMode(torchCameraId!!, on)
        }
    }

    private fun torchOff() {
        runCatching { torch(false) }
    }

    enum class MorseSendMode { SOUND, TORCH, BOTH }

    companion object {
        val MORSE: Map<Char, String> = mapOf(
            'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
            'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
            'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
            'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
            'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
            'Z' to "--..",
            '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
            '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
            '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '!' to "-.-.--",
            '/' to "-..-.", '=' to "-...-", '+' to ".-.-.", '-' to "-....-",
            ' ' to " ",
        )
        val MORSE_REVERSE: Map<String, Char> = MORSE.filterKeys { it != ' ' }.entries.associate { it.value to it.key }
    }
}
