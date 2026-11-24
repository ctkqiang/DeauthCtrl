package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 紧急信标 —— SOS 自动循环:闪光(手电)/ 声音(蜂鸣)/ 震动。
 * SOS = 三短 三长 三短;循环间隙 1.5s;可设自动停止;停止时必关手电。
 */
class BeaconManager(private val context: Context) {

    data class State(
        val running: Boolean = false,
        val cycles: Int = 0,
        val flash: Boolean = true,
        val sound: Boolean = true,
        val vibrate: Boolean = true,
        val autoStopMs: Long = 0L,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    @Volatile private var stopFlag = false
    private var worker: Thread? = null
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var torchId: String? = null
    private var tone: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    private fun torch(on: Boolean) = runCatching {
        val cm = cameraManager ?: return@runCatching
        val id = torchId ?: cm.cameraIdList.firstOrNull { cid ->
            runCatching {
                cm.getCameraCharacteristics(cid)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }.getOrDefault(false)
        }?.also { torchId = it }
        if (id != null) cm.setTorchMode(id, on)
    }

    private fun beep(on: Boolean) {
        if (!on) { tone?.release(); tone = null; return }
        if (tone == null) {
            tone = ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80)
        }
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200) }
    }

    private fun buzz(on: Boolean) {
        if (!on) return
        if (vibrator == null) {
            @Suppress("DEPRECATION")
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun signal(on: Boolean) {
        val s = _state.value
        if (s.flash) torch(on)
        if (s.sound) beep(on)
        if (s.vibrate) buzz(on)
    }

    private fun sleep(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }

    /** SOS 循环:三短(0.25s)三长(0.7s)三短,循环间 1.5s */
    private fun loop() {
        val startedAt = System.currentTimeMillis()
        while (!stopFlag) {
            val s = _state.value
            if (s.autoStopMs > 0 && System.currentTimeMillis() - startedAt >= s.autoStopMs) break
            repeat(3) { signal(true); sleep(250); signal(false); sleep(250) }
            repeat(3) { signal(true); sleep(700); signal(false); sleep(250) }
            repeat(3) { signal(true); sleep(250); signal(false); sleep(250) }
            _state.update { it.copy(cycles = it.cycles + 1) }
            sleep(1500)
        }
        signal(false)
        _state.update { it.copy(running = false) }
    }

    fun start() {
        if (_state.value.running) return
        stopFlag = false
        _state.update { it.copy(running = true) }
        worker = Thread({ loop() }, "beacon-sos").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        stopFlag = true
        worker?.join(1500)
        worker = null
        signal(false)
        _state.update { it.copy(running = false) }
    }

    fun toggleFlash() = _state.update { it.copy(flash = !it.flash) }
    fun toggleSound() = _state.update { it.copy(sound = !it.sound) }
    fun toggleVibrate() = _state.update { it.copy(vibrate = !it.vibrate) }
    fun setAutoStop(minutes: Int) = _state.update { it.copy(autoStopMs = minutes * 60_000L) }
    fun resetCycles() = _state.update { it.copy(cycles = 0) }

    fun release() {
        stop()
        torch(false)
        tone?.release(); tone = null
    }
}
