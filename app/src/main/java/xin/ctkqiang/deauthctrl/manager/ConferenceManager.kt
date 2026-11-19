package xin.ctkqiang.deauthctrl.manager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.min
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

/** 会议流源类型 */
enum class ConfStreamType(val beacon: String, @StringRes val labelRes: Int) {
    NONE("none", R.string.mgr_conf_stream_none),
    CAMERA_FRONT("front", R.string.mgr_conf_stream_front),
    CAMERA_BACK("back", R.string.mgr_conf_stream_back),
    SCREEN("screen", R.string.mgr_conf_stream_screen),
}

/** 会议成员（含自己） */
data class ConfMember(
    val id: String,
    val name: String,
    val ip: String,
    val streamType: String,
    val talking: Boolean,
    val lastSeenMs: Long,
)

/**
 * 接收端每成员统计 —— 用于黑屏自诊。
 * [lastFrameAgeMs] 在每次采样时计算；-1 表示从未收到完整帧。
 */
data class MemberRxStats(
    val memberId: String,
    val rxFragments: Long = 0,
    val rxFrames: Long = 0,
    val keyFrames: Long = 0,
    val decodedFrames: Long = 0,
    val droppedFrames: Long = 0,
    val missingFragments: Long = 0,
    val configFrames: Long = 0,
    val lastFrameAtMs: Long = 0,
    val lastFrameAgeMs: Long = -1,
    val decoderState: String = "未收到视频数据",
    val lastError: String? = null,
    val surfaceAttached: Boolean = false,
    val requestIdrCount: Long = 0,
)

/** 发送端统计 —— 只有编码器真正产出并发出帧才会增长 */
data class SenderTxStats(
    val frames: Long = 0,
    val fragments: Long = 0,
    val failures: Long = 0,
    val keyFrames: Long = 0,
    val configBroadcasts: Long = 0,
    val requestedKeyFrames: Long = 0,
    val lastFrameAtMs: Long = 0,
    val encoderLabel: String = "",
)

/**
 * 局域网战术会议 —— 无互联网多方视频 + 语音
 *
 * 自建协议，不依赖 WebRTC/外网服务器，一切流量只在同一 WiFi 内：
 *
 * ## 成员发现
 * UDP 9998 定向广播 + 受限广播 + 发现组播 `CONF|id|name|streamType|talking`（每 2s 一发，6s 超时离线）
 *
 * ## 视频（每方一路，所有人同时共享）
 * - 源：Camera2（前/后置，直通编码器 Surface）或 MediaProjection 屏幕
 * - H.264 Baseline 640×480@15fps 800kbps 硬件编码
 * - UDP 组播 239.1.2.3:10091 + 逐成员单播兜底（14B 头：8B 源 ID / frameId / 片号 / 总片数 / 标志）
 * - 接收端按 sourceId 独立乱序重组 + 独立 MediaCodec 解码到各成员 Surface
 * - CONFIG（SPS/PPS）单包发送并在每个 IDR 前与每 2s 重播，迟到者也能建解码器
 * - 接收端 2s 无完整帧时经 UDP 10092 单播 REQ_IDR，发送端请求编码器立即出关键帧
 *
 * ## 语音（PTT 半双工，防啸叫）
 * 按住说话：8kHz PCM 20ms 帧单播到每个成员（UDP 10001）；
 * 接收端抖动缓冲 + AudioTrack 播放，收到音频即点亮「说话中」指示。
 *
 * ## 平台限制（必须在 UI 如实呈现）
 * - UDP 不保证送达；丢包/乱序/AP 组播抑制会导致花屏或黑屏，REQ_IDR 只能兜底；
 * - AP 开启客户端隔离时组播与单播都无法穿透，应用层无绕过方法；
 * - 屏幕共享每次都需要用户授权；Android 14+ 每个授权会话只能 createVirtualDisplay 一次。
 */
class ConferenceManager(private val context: Context) {

    // ─── 状态流 ───
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _members = MutableStateFlow<List<ConfMember>>(emptyList())
    val members: StateFlow<List<ConfMember>> = _members

    private val _myStream = MutableStateFlow(ConfStreamType.NONE)
    val myStream: StateFlow<ConfStreamType> = _myStream

    private val _talking = MutableStateFlow(false)
    val talking: StateFlow<Boolean> = _talking

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _videoStats = MutableStateFlow(L10n.get(R.string.mgr_conf_video_idle))
    val videoStats: StateFlow<String> = _videoStats

    /** 接收端：每个成员的重组/解码统计，每秒刷新 */
    private val _rxStats = MutableStateFlow<Map<String, MemberRxStats>>(emptyMap())
    val memberRxStats: StateFlow<Map<String, MemberRxStats>> = _rxStats

    /** 发送端：本机编码器真实发包统计 */
    private val _txStats = MutableStateFlow(SenderTxStats())
    val senderTxStats: StateFlow<SenderTxStats> = _txStats

    /** 本机 ID —— 首次生成后持久化（6 字符） */
    val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("deauthctrl_prefs", Context.MODE_PRIVATE)
        prefs.getString(KEY_CONF_ID, null) ?: run {
            val id = (1..6).map { "0123456789ABCDEF"[kotlin.random.Random.nextInt(16)] }.joinToString("")
            prefs.edit().putString(KEY_CONF_ID, id).apply()
            id
        }
    }

    /** 写入 UDP 头的定宽源 ID（8B，右补 NUL）—— 与 6 字符 deviceId 解耦，避免 arraycopy 越界 */
    private val wireId: ByteArray by lazy {
        deviceId.padEnd(ID_FIELD_LEN, '\u0000').take(ID_FIELD_LEN).toByteArray(Charsets.US_ASCII)
    }

    /** 操作员呼号 —— 由 UI 从设置注入 */
    @Volatile var deviceName: String = "operator"

    // ─── 成员表 ───
    private val memberMap = ConcurrentHashMap<String, ConfMember>()
    private val idrLastRequest = ConcurrentHashMap<String, Long>()

    // ─── 网络 ───
    @Volatile private var beaconSocket: MulticastSocket? = null
    @Volatile private var videoSendSocket: MulticastSocket? = null
    @Volatile private var videoRecvSocket: MulticastSocket? = null
    @Volatile private var controlSocket: DatagramSocket? = null
    @Volatile private var audioSendSocket: DatagramSocket? = null
    @Volatile private var audioRecvSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var wifiInterface: NetworkInterface? = null
    @Volatile private var wifiLinkProperties: LinkProperties? = null
    private val networkLock = Any()

    @Volatile private var stopFlag = false
    @Volatile private var sessionGeneration = 0

    // ─── 编码器（摄像头或屏幕） ───
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    @Volatile private var codec: MediaCodec? = null
    private var codecThread: HandlerThread? = null
    @Volatile private var codecSurface: Surface? = null
    @Volatile private var screenProjection: MediaProjection? = null
    /** 当前授权会话是否已 createVirtualDisplay(一次授权只能创建一次) */
    @Volatile private var projectionVdUsed = false
    private var virtualDisplay: VirtualDisplay? = null
    @Volatile private var encoderGen = 0
    @Volatile private var requestedStream = ConfStreamType.NONE
    @Volatile private var encoderLabel = ""
    @Volatile private var cachedCsd0: ByteArray? = null
    @Volatile private var cachedCsd1: ByteArray? = null
    private var frameIdGen = 0
    private val frameIdLock = Any()

    // 发送统计
    private val txFrames = AtomicLong(0)
    private val txFragments = AtomicLong(0)
    private val txFailures = AtomicLong(0)
    private val txKeyFrames = AtomicLong(0)
    private val txConfigBroadcasts = AtomicLong(0)
    private val txRequestedKeyFrames = AtomicLong(0)
    @Volatile private var txLastFrameAtMs = 0L
    @Volatile private var lastManualKeyFrameAtMs = 0L
    @Volatile private var orphanVideoPackets = 0L

    // ─── 解码槽位（按归一化后的成员 ID 索引） ───
    private val slots = ConcurrentHashMap<String, DecoderSlot>()

    // ─── 音频 ───
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val jitter = ConcurrentLinkedQueue<ByteArray>()
    @Volatile private var talkingDown = false

    init {
        activeManager = this
        takePendingProjection()
    }

    // ══════════════════════════════════════════════════════════════════════
    // 生命周期（会话代际：旧线程在 stop 后无法继续使用新 socket）
    // ══════════════════════════════════════════════════════════════════════

    /** 进入会议：beacon + 视频组播/单播收发 + 控制通道 + 音频全部就绪 */
    fun start() {
        if (_running.value) return
        activeManager = this
        stopFlag = false
        sessionGeneration++
        val gen = sessionGeneration
        resetTxCounters()
        _error.value = null
        _videoStats.value = L10n.get(R.string.mgr_conf_video_preparing)
        try {
            connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            synchronized(networkLock) { bindNetworkLocked() }
            bindAudioSockets()
            initAudioPlayer()

            memberMap.clear()
            idrLastRequest.clear()
            memberMap[deviceId] = ConfMember(
                deviceId, deviceName, "本机", _myStream.value.beacon, _talking.value, System.currentTimeMillis(),
            )
            publishMembers()

            registerNetworkCallback()
            _running.value = true

            thread(name = "conf-beacon-send") { beaconLoop(gen) }
            thread(name = "conf-beacon-listen") { beaconListen(gen) }
            thread(name = "conf-video-recv") { videoReceiveLoop(gen) }
            thread(name = "conf-control-recv") { controlReceiveLoop(gen) }
            thread(name = "conf-csd-repeat") { csdRepeatLoop(gen) }
            thread(name = "conf-audio-recv") { audioReceiveLoop(gen) }
            thread(name = "conf-audio-play") { audioPlayLoop(gen) }
            thread(name = "conf-housekeep") { housekeepLoop(gen) }

            _videoStats.value = L10n.get(R.string.mgr_conf_video_no_source)
            takePendingProjection()
        } catch (t: Throwable) {
            _error.value = L10n.get(R.string.mgr_conf_err_start_failed, t.message.orEmpty())
            stop()
        }
    }

    fun stop() {
        stopFlag = true
        sessionGeneration++
        _running.value = false
        val projection = screenProjection
        screenProjection = null
        stopEncoder()
        runCatching { projection?.stop() }
        requestedStream = ConfStreamType.NONE
        unregisterNetworkCallback()
        synchronized(networkLock) {
            runCatching { beaconSocket?.close() }
            runCatching { videoSendSocket?.close() }
            runCatching { videoRecvSocket?.close() }
            runCatching { controlSocket?.close() }
            runCatching { audioSendSocket?.close() }
            runCatching { audioRecvSocket?.close() }
            runCatching { multicastLock?.release() }
            beaconSocket = null
            videoSendSocket = null
            videoRecvSocket = null
            controlSocket = null
            audioSendSocket = null
            audioRecvSocket = null
            multicastLock = null
        }
        runCatching { audioRecord?.stop(); audioRecord?.release() }
        runCatching { audioTrack?.stop(); audioTrack?.release() }
        audioRecord = null
        audioTrack = null
        talkingDown = false
        slots.values.forEach { it.release() }
        slots.clear()
        memberMap.clear()
        idrLastRequest.clear()
        _members.value = emptyList()
        _talking.value = false
        _myStream.value = ConfStreamType.NONE
        _rxStats.value = emptyMap()
        _txStats.value = SenderTxStats()
        _videoStats.value = L10n.get(R.string.mgr_conf_video_off)
        if (activeManager === this) activeManager = null
    }

    // ══════════════════════════════════════════════════════════════════════
    // 流源切换 —— _myStream 只在编码器真正启动后才对外广播
    // ══════════════════════════════════════════════════════════════════════

    /** 切换本机流源：关闭 / 前置 / 后置 / 屏幕。屏幕源仅表达意图，等授权交接后再启动。 */
    fun setStream(type: ConfStreamType) {
        if (!_running.value && type != ConfStreamType.NONE) {
            _error.value = L10n.get(R.string.mgr_conf_err_not_running_stream)
            return
        }
        if (type == requestedStream) return
        // 离开屏幕源:释放旧 MediaProjection —— 一个授权会话只能 createVirtualDisplay 一次,
        // 不释放的话再次选「屏幕」必然失败(先置 null 再 stop,避免 onStop 回调改写新意图)
        if (type != ConfStreamType.SCREEN && screenProjection != null) {
            val old = screenProjection
            screenProjection = null
            projectionVdUsed = false
            runCatching { old?.stop() }
        }
        requestedStream = type
        stopEncoder()
        _myStream.value = ConfStreamType.NONE
        _error.value = null
        when (type) {
            ConfStreamType.NONE -> _videoStats.value = L10n.get(R.string.mgr_conf_video_off)
            ConfStreamType.CAMERA_FRONT -> {
                _videoStats.value = L10n.get(R.string.mgr_conf_video_starting_front)
                startCameraEncoder(CameraCharacteristics.LENS_FACING_FRONT)
            }
            ConfStreamType.CAMERA_BACK -> {
                _videoStats.value = L10n.get(R.string.mgr_conf_video_starting_back)
                startCameraEncoder(CameraCharacteristics.LENS_FACING_BACK)
            }
            ConfStreamType.SCREEN -> {
                if (screenProjection == null) {
                    _videoStats.value = L10n.get(R.string.mgr_conf_video_wait_screen_auth)
                } else {
                    _videoStats.value = L10n.get(R.string.mgr_conf_video_creating_screen)
                    startScreenEncoder()
                }
            }
        }
    }

    private fun failStream(gen: Int, message: String) {
        if (gen != encoderGen) return
        _error.value = message
        _videoStats.value = L10n.get(R.string.mgr_conf_video_with_message, message)
        requestedStream = ConfStreamType.NONE
        _myStream.value = ConfStreamType.NONE
        stopEncoder()
    }

    // ─── 摄像头 ───
    @SuppressLint("MissingPermission")
    private fun startCameraEncoder(facing: Int) {
        val gen = encoderGen
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (manager == null) {
                failStream(gen, L10n.get(R.string.mgr_conf_err_camera_unavailable))
                return
            }
            cameraManager = manager
            val camId = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
            } ?: manager.cameraIdList.firstOrNull()
            if (camId == null) {
                failStream(gen, L10n.get(R.string.mgr_conf_err_no_camera))
                return
            }

            val encHandler = Handler(ensureEncoderThread().looper)
            manager.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    if (gen != encoderGen || !_running.value) {
                        runCatching { cam.close() }
                        return
                    }
                    cameraDevice = cam
                    val inputSurface = startEncoderSurface(gen)
                    if (inputSurface == null) {
                        runCatching { cam.close() }
                        if (cameraDevice === cam) cameraDevice = null
                        return
                    }
                    runCatching {
                        cam.createCaptureSession(listOf(inputSurface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                if (gen != encoderGen) {
                                    runCatching { session.close() }
                                    return
                                }
                                try {
                                    val request = cam.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                        addTarget(inputSurface)
                                    }
                                    session.setRepeatingRequest(request.build(), null, encHandler)
                                    encoderLabel = if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                                        L10n.get(R.string.mgr_conf_encoder_front_camera)
                                    } else {
                                        L10n.get(R.string.mgr_conf_encoder_back_camera)
                                    }
                                    _myStream.value = if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                                        ConfStreamType.CAMERA_FRONT
                                    } else {
                                        ConfStreamType.CAMERA_BACK
                                    }
                                    _videoStats.value = L10n.get(
                                        R.string.mgr_conf_video_wait_first_frame,
                                        encoderLabel,
                                    )
                                    _error.value = null
                                } catch (t: Throwable) {
                                    runCatching { session.close() }
                                    failStream(gen, L10n.get(R.string.mgr_conf_err_camera_session, t.message.orEmpty()))
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                runCatching { session.close() }
                                failStream(gen, L10n.get(R.string.mgr_conf_err_camera_session_generic))
                            }
                        }, encHandler)
                    }.onFailure { failStream(gen, L10n.get(R.string.mgr_conf_err_camera_session_msg, it.message.orEmpty())) }
                }

                override fun onDisconnected(cam: CameraDevice) {
                    runCatching { cam.close() }
                    if (cameraDevice === cam) cameraDevice = null
                    if (gen == encoderGen) {
                        // 相机被系统/其他应用抢占后不会再有帧，必须回退状态，
                        // 否则信标仍宣称有前置/后置画面，对端只能看到一个黑格。
                        requestedStream = ConfStreamType.NONE
                        _myStream.value = ConfStreamType.NONE
                        _videoStats.value = L10n.get(R.string.mgr_conf_video_camera_disconnected)
                    }
                }

                override fun onError(cam: CameraDevice, error: Int) {
                    runCatching { cam.close() }
                    if (cameraDevice === cam) cameraDevice = null
                    failStream(gen, L10n.get(R.string.mgr_conf_err_camera_error, error))
                }
            }, encHandler)
        } catch (t: Throwable) {
            failStream(gen, L10n.get(R.string.mgr_conf_err_camera_start, t.message.orEmpty()))
        }
    }

    private fun ensureEncoderThread(): HandlerThread {
        val existing = codecThread
        if (existing != null && existing.isAlive) return existing
        return HandlerThread("conf-enc").apply { start() }.also { codecThread = it }
    }

    // ─── 屏幕 ───

    /**
     * ConferenceHostService 在 getMediaProjection 成功后经 [handoffScreenProjection] 调用。
     * 先注册 MediaProjection.Callback（Android 14+ 硬性要求），再按需创建 VirtualDisplay。
     */
    private fun onScreenProjectionReady(projection: MediaProjection) {
        val previous = screenProjection
        screenProjection = projection
        projectionVdUsed = false // 新授权会话,尚未创建 VirtualDisplay
        if (previous !== null && previous !== projection) runCatching { previous.stop() }
        registerProjectionCallback(projection)
        if (!_running.value) {
            _videoStats.value = L10n.get(R.string.mgr_conf_video_screen_ready_join)
            return
        }
        if (requestedStream == ConfStreamType.SCREEN) {
            startScreenEncoder()
        } else {
            _videoStats.value = L10n.get(R.string.mgr_conf_video_screen_ready_select)
        }
    }

    private fun registerProjectionCallback(projection: MediaProjection) {
        runCatching {
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    if (screenProjection !== projection) return
                    onScreenCaptureStopped(L10n.get(R.string.mgr_conf_video_screen_ended))
                }

                override fun onCapturedContentResize(width: Int, height: Int) {
                    // Android 14+ 一个授权会话只能 createVirtualDisplay 一次；
                    // 无法无损重建采集尺寸，只能提示重新授权。
                    if (screenProjection !== projection) return
                    _videoStats.value = L10n.get(R.string.mgr_conf_video_screen_size, width, height)
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    private fun onScreenCaptureStopped(reason: String) {
        screenProjection = null
        stopEncoder()
        requestedStream = ConfStreamType.NONE
        _myStream.value = ConfStreamType.NONE
        _videoStats.value = L10n.get(R.string.mgr_conf_video_with_message, reason)
    }

    private fun startScreenEncoder() {
        val projection = screenProjection
        if (projection == null) {
            _videoStats.value = L10n.get(R.string.mgr_conf_video_wait_screen_auth)
            return
        }
        // Android 一个 MediaProjection 授权只能 createVirtualDisplay 一次;
        // 已用过的会话直接提示重新授权,而不是先失败一次再恢复
        if (projectionVdUsed) {
            screenProjection = null
            projectionVdUsed = false
            runCatching { projection.stop() }
            _error.value = L10n.get(R.string.mgr_conf_err_reauth_used)
            _videoStats.value = L10n.get(R.string.mgr_conf_video_reauth_needed)
            requestedStream = ConfStreamType.NONE
            _myStream.value = ConfStreamType.NONE
            return
        }
        val gen = encoderGen
        val inputSurface = startEncoderSurface(gen) ?: return
        try {
            val dpi = context.resources.displayMetrics.densityDpi
            virtualDisplay = projection.createVirtualDisplay(
                "ConfScreen", VIDEO_WIDTH, VIDEO_HEIGHT, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, null,
            )
            if (virtualDisplay == null) {
                failStream(gen, L10n.get(R.string.mgr_conf_err_screen_create))
                return
            }
            projectionVdUsed = true
            encoderLabel = L10n.get(R.string.mgr_conf_encoder_screen)
            _myStream.value = ConfStreamType.SCREEN
            _videoStats.value = L10n.get(
                R.string.mgr_conf_video_wait_first_frame,
                encoderLabel,
            )
            _error.value = null
        } catch (t: Throwable) {
            val hint = if (t is IllegalStateException) {
                L10n.get(R.string.mgr_conf_reauth_hint, t.message.orEmpty())
            } else {
                t.message.orEmpty()
            }
            failStream(gen, L10n.get(R.string.mgr_conf_err_screen_failed, hint))
        }
    }

    // ─── 编码器公共部分 ───

    /**
     * 创建 H.264 编码器并回调其输入 Surface。
     * Surface 输入默认不写 KEY_COLOR_FORMAT；个别编码器 configure 拒绝时再补该键并用新实例重试。
     */
    private fun startEncoderSurface(gen: Int): Surface? {
        return try {
            val encHandler = Handler(ensureEncoderThread().looper)
            val c = try {
                createEncoder(withColorFormat = false)
            } catch (_: Throwable) {
                createEncoder(withColorFormat = true)
            }
            codec = c
            c.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}

                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    // 快速切源时 stopEncoder 可能与本回调并发，MediaCodec 的任何调用都可能抛
                    // IllegalStateException；回调线程未捕获异常会直接杀掉进程，这里统一兜底。
                    try {
                        if (gen != encoderGen) {
                            runCatching { codec.releaseOutputBuffer(index, false) }
                            return
                        }
                        if (info.size <= 0) {
                            runCatching { codec.releaseOutputBuffer(index, false) }
                            return
                        }
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer == null) {
                            runCatching { codec.releaseOutputBuffer(index, false) }
                            return
                        }
                        val data = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.get(data, 0, info.size)
                        val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        val isIdr = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        if (!isConfig && isIdr) broadcastCachedConfig()
                        broadcastFrame(data, isConfig, isIdr)
                        runCatching { codec.releaseOutputBuffer(index, false) }
                    } catch (_: Throwable) {
                        if (gen == encoderGen) {
                            runCatching { codec.releaseOutputBuffer(index, false) }
                        }
                    }
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    // 编码器死掉必须复位流状态,否则信标继续宣称有画面,同源按钮还被去重吞掉
                    if (gen == encoderGen) {
                        failStream(gen, L10n.get(R.string.mgr_conf_err_encoder_error, e.message.orEmpty()))
                    }
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    if (gen != encoderGen) return
                    try {
                        val csd0 = format.getByteBuffer("csd-0") ?: return
                        val csd1 = format.getByteBuffer("csd-1") ?: return
                        cachedCsd0 = ByteArray(csd0.remaining()).also { csd0.get(it) }
                        cachedCsd1 = ByteArray(csd1.remaining()).also { csd1.get(it) }
                        broadcastCachedConfig()
                    } catch (t: Throwable) {
                        _error.value = L10n.get(R.string.mgr_conf_err_csd, t.message.orEmpty())
                    }
                }
            }, encHandler)

            val surface = c.createInputSurface()
            c.start()
            codecSurface = surface
            surface
        } catch (t: Throwable) {
            failStream(gen, L10n.get(R.string.mgr_conf_err_encoder_start, t.message.orEmpty()))
            null
        }
    }

    private fun createEncoder(withColorFormat: Boolean): MediaCodec {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            setInteger(MediaFormat.KEY_LATENCY, 1)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            if (withColorFormat) {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            }
        }
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        return try {
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            c
        } catch (t: Throwable) {
            runCatching { c.release() }
            throw t
        }
    }

    /** 只停止编码链路，不动服务持有的 MediaProjection（离开会议时由 stop() 显式停止） */
    private fun stopEncoder() {
        encoderGen++
        releaseEncoderResources()
    }

    private fun releaseEncoderResources() {
        runCatching { cameraDevice?.close() }
        cameraDevice = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { codecSurface?.release() }
        codecSurface = null
        runCatching { codecThread?.quitSafely() }
        codecThread = null
        cachedCsd0 = null
        cachedCsd1 = null
        encoderLabel = ""
    }

    /** 请求编码器立即产出关键帧（REQ_IDR 控制通道 / 本机重试） */
    private fun requestKeyFrame(): Boolean = runCatching {
        val c = codec ?: return@runCatching false
        c.setParameters(Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        })
        true
    }.getOrDefault(false)

    // ══════════════════════════════════════════════════════════════════════
    // 分片发送（组播 + 逐成员单播兜底）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 把一帧拆成 ≤1200B 的分片。
     * 头 14B：sourceId(8B ASCII) | frameId(2B BE) | 片号(1B) | 总片数(1B) | 标志(1B) | 保留(1B)
     * 标志位：0x01 CONFIG(CSD) 0x02 IDR 0x04 末片 0x08 帧起点
     */
    private fun broadcastFrame(data: ByteArray, isConfig: Boolean, isIdr: Boolean) {
        val sock = videoSendSocket
        if (sock == null) {
            txFailures.incrementAndGet()
            return
        }
        val maxPayload = ConfProtocol.MAX_FRAG - HEADER_LEN
        val fragCount = ((data.size + maxPayload - 1) / maxPayload).coerceAtLeast(1)
        if (fragCount > 255) {
            _error.value = L10n.get(R.string.mgr_conf_err_frame_too_large, data.size)
            txFailures.incrementAndGet()
            return
        }
        val frameId = synchronized(frameIdLock) { frameIdGen++ and 0xFFFF }
        val group = runCatching { InetAddress.getByName(VIDEO_GROUP) }.getOrNull()
        val peerAddresses = memberMap.values.asSequence()
            .filter { it.id != deviceId && it.ip != "本机" }
            .mapNotNull { runCatching { InetAddress.getByName(it.ip) }.getOrNull() }
            .distinctBy { it.hostAddress }
            .toList()
        var failures = 0
        try {
            // 分片与协议头由 ConfProtocol.fragmentFrame 构造 —— 与传输模拟测试共用同一代码路径
            val packets = ConfProtocol.fragmentFrame(data, wireId, frameId, isConfig, isIdr)
            for ((fragIdx, packet) in packets.withIndex()) {
                if (group != null) {
                    try {
                        sock.send(DatagramPacket(packet, packet.size, group, VIDEO_PORT))
                    } catch (_: Throwable) {
                        failures++
                    }
                }
                // 很多 AP 会抑制组播/IGMP snooping 失效；逐成员单播兜底，接收端按 frameId+片号去重
                for (peer in peerAddresses) {
                    try {
                        sock.send(DatagramPacket(packet, packet.size, peer, VIDEO_PORT))
                    } catch (_: Throwable) {
                        failures++
                    }
                }
                if (fragIdx % FRAG_PACE == FRAG_PACE - 1) runCatching { Thread.sleep(1) }
            }
            txFragments.addAndGet(fragCount.toLong())
            if (failures > 0) txFailures.addAndGet(failures.toLong())
            if (isConfig) {
                txConfigBroadcasts.incrementAndGet()
            } else {
                txFrames.incrementAndGet()
                if (isIdr) txKeyFrames.incrementAndGet()
                txLastFrameAtMs = System.currentTimeMillis()
                if (txFrames.get() == 1L) {
                    _videoStats.value = L10n.get(R.string.mgr_conf_video_first_frame_sent, encoderLabel)
                }
            }
        } catch (t: Throwable) {
            txFailures.incrementAndGet()
            _error.value = L10n.get(R.string.mgr_conf_err_video_send, t.message.orEmpty())
        }
    }

    /** CONFIG 作为单包发送（SPS+PPS 通常 < 100B，不分片） */
    private fun broadcastCachedConfig() {
        val sps = cachedCsd0 ?: return
        val pps = cachedCsd1 ?: return
        val payload = ByteArray(2 + sps.size + 2 + pps.size)
        writeShortBE(payload, 0, sps.size)
        System.arraycopy(sps, 0, payload, 2, sps.size)
        writeShortBE(payload, 2 + sps.size, pps.size)
        System.arraycopy(pps, 0, payload, 4 + sps.size, pps.size)
        broadcastFrame(payload, isConfig = true, isIdr = false)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 视频接收
    // ══════════════════════════════════════════════════════════════════════

    private fun videoReceiveLoop(gen: Int) {
        val buf = ByteArray(2048)
        while (isSessionActive(gen)) {
            try {
                val sock = videoRecvSocket
                if (sock == null) {
                    Thread.sleep(200)
                    continue
                }
                val packet = DatagramPacket(buf, buf.size)
                sock.receive(packet)
                if (packet.length < HEADER_LEN) continue
                // 归一化源 ID:写入端 8B 定宽、右侧 NUL 填充,必须与服务端 6 字符 id 对齐
                val header = ConfProtocol.decodeHeader(packet.data) ?: continue
                val sourceId = header.sourceId
                if (sourceId.isEmpty() || sourceId == deviceId) continue

                val payload = packet.data.copyOfRange(HEADER_LEN, packet.length)

                // 只给已知成员建解码槽:垃圾/伪造源 ID 会无限创建 HandlerThread 槽位。
                // 视频先于信标到达时丢弃该帧 —— 2s 内 CSD 重播 + REQ_IDR 自愈
                if (!memberMap.containsKey(sourceId)) {
                    orphanVideoPackets++
                    continue
                }
                val slot = slots.computeIfAbsent(sourceId) { DecoderSlot(it) }
                if (slot.isClosed()) {
                    slots.remove(sourceId, slot)
                    continue
                }
                val complete = slot.onFragment(header.frameId, header.fragIdx, header.fragCount, header.flags, payload) ?: continue
                if (complete.isConfig) slot.postConfig(complete.data) else slot.postFrame(complete.data, complete.isIdr)
                if (!memberMap.containsKey(sourceId)) orphanVideoPackets++
            } catch (_: Throwable) {
                if (isSessionActive(gen)) Thread.sleep(50)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 成员发现（定向广播 + 受限广播 + 发现组播）
    // ══════════════════════════════════════════════════════════════════════

    private fun beaconLoop(gen: Int) {
        while (isSessionActive(gen)) {
            try {
                val msg = "CONF|$deviceId|${deviceName.replace("|", "_")}|${_myStream.value.beacon}|${if (_talking.value) 1 else 0}"
                val bytes = msg.toByteArray(Charsets.UTF_8)
                val sock = beaconSocket
                if (sock != null) {
                    for (target in beaconTargets()) {
                        runCatching { sock.send(DatagramPacket(bytes, bytes.size, target, BEACON_PORT)) }
                    }
                }
            } catch (_: Throwable) {
            }
            Thread.sleep(2000)
        }
    }

    private fun beaconListen(gen: Int) {
        val buf = ByteArray(512)
        while (isSessionActive(gen)) {
            try {
                val sock = beaconSocket
                if (sock == null) {
                    Thread.sleep(200)
                    continue
                }
                val packet = DatagramPacket(buf, buf.size)
                sock.receive(packet)
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                if (!text.startsWith("CONF|")) continue
                val parts = text.split("|")
                if (parts.size < 5) continue
                val id = normalizeId(parts[1])
                if (id.isEmpty() || id == deviceId) continue
                val name = parts[2]
                val stream = parts[3]
                val talking = parts[4] == "1"
                val ip = packet.address?.hostAddress ?: continue
                val isNew = memberMap.put(
                    id,
                    ConfMember(id, name, ip, stream, talking, System.currentTimeMillis()),
                ) == null
                // 新成员到达立即推送到 UI，不再等 housekeep 的 1s 轮询
                if (isNew) publishMembers()
            } catch (_: Throwable) {
                if (isSessionActive(gen)) Thread.sleep(50)
            }
        }
    }

    /** 周期性维护：清理超时成员 + 推送成员表 + 释放无流槽位 + 刷新收发统计 + 关键帧重请求 */
    private fun housekeepLoop(gen: Int) {
        while (isSessionActive(gen)) {
            try {
                val now = System.currentTimeMillis()
                memberMap.entries.removeIf { it.key != deviceId && now - it.value.lastSeenMs > MEMBER_TIMEOUT_MS }
                memberMap[deviceId] = ConfMember(
                    deviceId, deviceName, "本机", _myStream.value.beacon, _talking.value, now,
                )
                publishMembers()

                // 成员无视频流且 6s 没有完整帧 → 释放解码槽。
                // 不能再仅凭 beacon 缺失就每秒删槽：广播可能被 AP 抑制而视频组播仍通，
                // 那样会把正在解码的槽反复重建（黑屏/花屏的直接原因之一）。
                slots.keys.toList().forEach { id ->
                    val slot = slots[id] ?: return@forEach
                    val m = memberMap[id]
                    val lastFrameAt = slot.lastDeliveredAtMs()
                    val noRecentFrames = lastFrameAt == 0L || now - lastFrameAt > MEMBER_TIMEOUT_MS
                    if (noRecentFrames && (m == null || m.streamType == ConfStreamType.NONE.beacon)) {
                        slots.remove(id)?.release()
                    }
                }

                refreshRxStats(now)
                maybeRequestKeyFrames(now)

                _txStats.value = SenderTxStats(
                    frames = txFrames.get(),
                    fragments = txFragments.get(),
                    failures = txFailures.get(),
                    keyFrames = txKeyFrames.get(),
                    configBroadcasts = txConfigBroadcasts.get(),
                    requestedKeyFrames = txRequestedKeyFrames.get(),
                    lastFrameAtMs = txLastFrameAtMs,
                    encoderLabel = encoderLabel,
                )
                if (txFrames.get() > 0 && _myStream.value != ConfStreamType.NONE && encoderLabel.isNotEmpty()) {
                    _videoStats.value = L10n.get(
                        R.string.mgr_conf_video_frames_sent,
                        encoderLabel,
                        txFrames.get(),
                    )
                }
            } catch (_: Throwable) {
            }
            Thread.sleep(1000)
        }
    }

    private fun publishMembers() {
        _members.value = memberMap.values.sortedBy { it.name }
    }

    private fun refreshRxStats(now: Long) {
        val stats = HashMap<String, MemberRxStats>()
        memberMap.values.forEach { member ->
            if (member.id == deviceId) return@forEach
            val slot = slots[member.id]
            if (slot != null) {
                stats[member.id] = slot.snapshotStats(now)
            } else if (member.streamType != ConfStreamType.NONE.beacon) {
                stats[member.id] = MemberRxStats(memberId = member.id, decoderState = "已连接，未收到视频数据")
            }
        }
        _rxStats.value = stats
    }

    private fun maybeRequestKeyFrames(now: Long) {
        val sock = controlSocket ?: return
        memberMap.values.forEach { member ->
            if (member.id == deviceId) return@forEach
            if (member.streamType == ConfStreamType.NONE.beacon) return@forEach
            val slot = slots[member.id]
            // 槽位存在但还没挂 Surface 时不发请求；槽位不存在说明连分片都没收到，
            // 仍请求一次关键帧（2s 限流）尝试把链路拉起来。
            if (slot != null && !slot.hasSurface()) return@forEach
            val lastFrameAt = slot?.lastDeliveredAtMs() ?: 0L
            if (lastFrameAt != 0L && now - lastFrameAt < IDR_REQUEST_AFTER_MS) {
                idrLastRequest.remove(member.id)
                return@forEach
            }
            val lastRequest = idrLastRequest[member.id] ?: 0L
            if (now - lastRequest < IDR_REQUEST_INTERVAL_MS) return@forEach
            runCatching {
                val msg = "REQ_IDR|$deviceId".toByteArray(Charsets.UTF_8)
                sock.send(DatagramPacket(msg, msg.size, InetAddress.getByName(member.ip), CONTROL_PORT))
                idrLastRequest[member.id] = now
                slot?.noteIdrRequest()
            }
        }
    }

    /** 接收端关键帧请求通道：只接受 REQ_IDR，并对请求做 300ms 去抖 */
    private fun controlReceiveLoop(gen: Int) {
        val buf = ByteArray(128)
        while (isSessionActive(gen)) {
            try {
                val sock = controlSocket
                if (sock == null) {
                    Thread.sleep(200)
                    continue
                }
                val packet = DatagramPacket(buf, buf.size)
                sock.receive(packet)
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                if (!text.startsWith("REQ_IDR")) continue
                val now = System.currentTimeMillis()
                if (now - lastManualKeyFrameAtMs < 300) continue
                lastManualKeyFrameAtMs = now
                if (requestKeyFrame()) txRequestedKeyFrames.incrementAndGet()
            } catch (_: Throwable) {
                if (isSessionActive(gen)) Thread.sleep(50)
            }
        }
    }

    /** 迟到者/丢包者恢复：CSD 每 2s 重播一次（IDR 前还会额外重播一次） */
    private fun csdRepeatLoop(gen: Int) {
        while (isSessionActive(gen)) {
            try {
                if (_myStream.value != ConfStreamType.NONE && cachedCsd0 != null && cachedCsd1 != null) {
                    broadcastCachedConfig()
                }
            } catch (_: Throwable) {
            }
            Thread.sleep(CSD_REPEAT_MS)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 音频（PTT）
    // ══════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    fun talkDown() {
        if (talkingDown || !_running.value) return
        talkingDown = true
        _talking.value = true
        val gen = sessionGeneration
        thread(name = "conf-talk-send") {
            var record: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC, 8000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2,
                )
                audioRecord = record
                record.startRecording()
                val frame = ByteArray(320)
                while (talkingDown && isSessionActive(gen)) {
                    val n = record.read(frame, 0, frame.size)
                    if (n <= 0) continue
                    val peers = memberMap.values.filter { it.id != deviceId }
                    peers.forEach { peer ->
                        runCatching {
                            audioSendSocket?.send(DatagramPacket(frame, 0, n, InetAddress.getByName(peer.ip), AUDIO_PORT))
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { record?.stop() }
                runCatching { record?.release() }
                audioRecord = null
                talkingDown = false
                _talking.value = false
            }
        }
    }

    fun talkUp() {
        talkingDown = false
        runCatching { audioRecord?.stop() }
        _talking.value = false
    }

    private fun audioReceiveLoop(gen: Int) {
        val buf = ByteArray(512)
        while (isSessionActive(gen)) {
            try {
                val sock = audioRecvSocket
                if (sock == null) {
                    Thread.sleep(200)
                    continue
                }
                val packet = DatagramPacket(buf, buf.size)
                sock.receive(packet)
                if (packet.length <= 0) continue
                val data = packet.data.copyOfRange(0, packet.length)
                if (jitter.size < 12) jitter.add(data) // 缓冲上限，防止积压延迟
                val ip = packet.address?.hostAddress
                memberMap.values.firstOrNull { it.ip == ip }?.let { m ->
                    memberMap[m.id] = m.copy(talking = true, lastSeenMs = System.currentTimeMillis())
                }
            } catch (_: Throwable) {
                if (isSessionActive(gen)) Thread.sleep(50)
            }
        }
    }

    private fun audioPlayLoop(gen: Int) {
        while (isSessionActive(gen)) {
            try {
                val frame = jitter.poll()
                if (frame == null) {
                    Thread.sleep(10)
                    continue
                }
                audioTrack?.write(frame, 0, frame.size)
            } catch (_: Throwable) {
                Thread.sleep(10)
            }
        }
    }

    private fun initAudioPlayer() {
        val minBuf = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(8000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            minBuf, AudioTrack.MODE_STREAM, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        audioTrack?.play()
    }

    // ══════════════════════════════════════════════════════════════════════
    // 解码槽位（UI 侧调用）
    // ══════════════════════════════════════════════════════════════════════

    /** 成员画面 Surface 附加 —— 由每格 TextureView 提供；主线程只提交任务 */
    fun attachMemberSurface(memberId: String, texture: SurfaceTexture) {
        val id = normalizeId(memberId)
        if (id.isEmpty() || id == deviceId) return
        slots.computeIfAbsent(id) { DecoderSlot(it) }.attach(texture)
    }

    fun detachMemberSurface(memberId: String) {
        val id = normalizeId(memberId)
        if (id.isEmpty()) return
        slots[id]?.detach()
    }

    // ══════════════════════════════════════════════════════════════════════
    // 网络绑定（Wi-Fi 接口 / TTL / MulticastLock 重取 / 定向广播）
    // ══════════════════════════════════════════════════════════════════════

    /** 首次入会：解析 Wi-Fi 接口、取组播锁、创建全部收发 socket（reuse 先于 bind） */
    private fun bindNetworkLocked() {
        val resolved = resolveWifiNetwork()
        wifiInterface = resolved?.first ?: primaryInterface()
        wifiLinkProperties = resolved?.second ?: activeLinkProperties()
        acquireMulticastLockLocked()
        videoRecvSocket = createVideoReceiver(wifiInterface)
        videoSendSocket = createVideoSender(wifiInterface)
        beaconSocket = createBeaconSocket(wifiInterface)
        controlSocket = createBoundSocket(CONTROL_PORT)
        // 端口绑定失败不是“暂时没画面”而是本机网络能力问题，必须如实提示，
        // 否则 UI 只会一直显示等待成员/等待画面。
        if (resolved == null) {
            _error.value = L10n.get(R.string.mgr_conf_err_wifi_unresolved)
        } else if (videoRecvSocket == null || beaconSocket == null) {
            _error.value = L10n.get(R.string.mgr_conf_err_port_bind)
        }
    }

    private fun bindAudioSockets() {
        audioSendSocket = createBoundSocket(0)
        audioRecvSocket = createBoundSocket(AUDIO_PORT)
    }

    /**
     * Wi-Fi 重连/VPN 切换时重建 socket 并重新 join。
     * 先创建新 socket 再关闭旧的，接收线程会在 SocketException 后自动读到新字段。
     */
    private fun onNetworkChanged() {
        if (!_running.value) return
        synchronized(networkLock) {
            if (!_running.value) return
            val resolved = resolveWifiNetwork()
            wifiInterface = resolved?.first ?: wifiInterface
            wifiLinkProperties = resolved?.second ?: activeLinkProperties()
            acquireMulticastLockLocked()

            val oldRecv = videoRecvSocket
            val newRecv = createVideoReceiver(wifiInterface)
            if (newRecv != null) {
                videoRecvSocket = newRecv
                runCatching { oldRecv?.close() }
            }

            val oldSend = videoSendSocket
            val newSend = createVideoSender(wifiInterface)
            if (newSend != null) {
                videoSendSocket = newSend
                runCatching { oldSend?.close() }
            }

            val oldBeacon = beaconSocket
            val newBeacon = createBeaconSocket(wifiInterface)
            if (newBeacon != null) {
                beaconSocket = newBeacon
                runCatching { oldBeacon?.close() }
            }
        }
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onNetworkChanged()
            override fun onLost(network: Network) = onNetworkChanged()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = onNetworkChanged()
        }
        networkCallback = callback
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    private fun unregisterNetworkCallback() {
        val cm = connectivityManager
        val callback = networkCallback
        networkCallback = null
        if (cm != null && callback != null) runCatching { cm.unregisterNetworkCallback(callback) }
    }

    /**
     * 只选 Wi-Fi 传输（而不是枚举里第一个可用接口）：
     * 开移动数据/VPN/热点时，rmnet_data0 / tun0 / p2p0 都会排在 wlan0 前面。
     */
    @Suppress("DEPRECATION")
    private fun resolveWifiNetwork(): Pair<NetworkInterface, LinkProperties>? {
        val cm = connectivityManager ?: return null
        return runCatching {
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                val lp = cm.getLinkProperties(network) ?: continue
                val v4 = lp.linkAddresses.asSequence()
                    .map { it.address }
                    .firstOrNull { it is Inet4Address && !it.isLoopbackAddress } ?: continue
                val iface = NetworkInterface.getByInetAddress(v4) ?: continue
                if (iface.isUp && iface.supportsMulticast()) return@runCatching iface to lp
            }
            null
        }.getOrNull()
    }

    private fun activeLinkProperties(): LinkProperties? = runCatching {
        val cm = connectivityManager ?: return@runCatching null
        cm.getLinkProperties(cm.activeNetwork)
    }.getOrNull()

    private fun primaryInterface(): NetworkInterface? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
            .firstOrNull { iface ->
                iface.inetAddresses.toList().any { !it.isLoopbackAddress && it is Inet4Address }
            }
    }.getOrNull()

    private fun acquireMulticastLockLocked() {
        runCatching { multicastLock?.release() }
        multicastLock = runCatching {
            (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("conf-lock")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }.getOrNull()
    }

    private fun createBoundSocket(port: Int): DatagramSocket? = runCatching {
        DatagramSocket(null as SocketAddress?).apply {
            reuseAddress = true
            bind(InetSocketAddress(port))
        }
    }.getOrNull()

    private fun createVideoReceiver(iface: NetworkInterface?): MulticastSocket? = runCatching {
        MulticastSocket(null as SocketAddress?).apply {
            reuseAddress = true
            bind(InetSocketAddress(VIDEO_PORT))
            iface?.let { runCatching { setNetworkInterface(it) } }
            joinGroup(InetSocketAddress(InetAddress.getByName(VIDEO_GROUP), VIDEO_PORT), iface)
        }
    }.getOrNull()

    private fun createVideoSender(iface: NetworkInterface?): MulticastSocket? = runCatching {
        MulticastSocket().apply {
            setTimeToLive(1)
            // Android 的 MulticastSocket 只暴露 setLoopbackMode(disable)：
            // false = 允许本机回环，便于单机自测
            runCatching { setLoopbackMode(false) }
            iface?.let { runCatching { setNetworkInterface(it) } }
        }
    }.getOrNull()

    /**
     * 发现通道：同一 socket 既收广播也收发现组播。
     * AP 不转发 255.255.255.255 时，子网定向广播 / 组播仍有机会送达。
     */
    private fun createBeaconSocket(iface: NetworkInterface?): MulticastSocket? = runCatching {
        MulticastSocket(null as SocketAddress?).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(BEACON_PORT))
            iface?.let { runCatching { setNetworkInterface(it) } }
            runCatching {
                joinGroup(InetSocketAddress(InetAddress.getByName(DISCOVERY_GROUP), BEACON_PORT), iface)
            }
        }
    }.getOrNull()

    private fun beaconTargets(): List<InetAddress> {
        val targets = ArrayList<InetAddress>(3)
        directedBroadcastAddress()?.let { targets.add(it) }
        runCatching { InetAddress.getByName("255.255.255.255") }
            .getOrNull()?.let { if (it !in targets) targets.add(it) }
        runCatching { InetAddress.getByName(DISCOVERY_GROUP) }
            .getOrNull()?.let { if (it !in targets) targets.add(it) }
        return targets
    }

    /** broadcast = ip or (0xFFFFFFFF shl (32 - prefix)) */
    private fun directedBroadcastAddress(): InetAddress? {
        val lp = wifiLinkProperties ?: return null
        val link = lp.linkAddresses.firstOrNull {
            it.address is Inet4Address && !it.address.isLoopbackAddress
        } ?: return null
        val v4 = link.address as Inet4Address
        val prefix = link.prefixLength
        if (prefix <= 0 || prefix >= 32) return null
        val raw = v4.address
        var ip = 0
        for (b in raw) ip = (ip shl 8) or (b.toInt() and 0xFF)
        val broadcast = ip or (-1 shl (32 - prefix)).inv()
        val bytes = byteArrayOf(
            ((broadcast ushr 24) and 0xFF).toByte(),
            ((broadcast ushr 16) and 0xFF).toByte(),
            ((broadcast ushr 8) and 0xFF).toByte(),
            (broadcast and 0xFF).toByte(),
        )
        return runCatching { InetAddress.getByAddress(bytes) }.getOrNull()
    }

    private fun isSessionActive(gen: Int): Boolean = !stopFlag && gen == sessionGeneration

    private fun resetTxCounters() {
        txFrames.set(0)
        txFragments.set(0)
        txFailures.set(0)
        txKeyFrames.set(0)
        txConfigBroadcasts.set(0)
        txRequestedKeyFrames.set(0)
        txLastFrameAtMs = 0L
        lastManualKeyFrameAtMs = 0L
        encoderLabel = ""
        cachedCsd0 = null
        cachedCsd1 = null
        orphanVideoPackets = 0L
        _txStats.value = SenderTxStats()
    }

    companion object {
        private const val KEY_CONF_ID = "conf_device_id"
        private const val BEACON_PORT = 9998
        private const val DISCOVERY_GROUP = "239.255.42.98"
        private const val VIDEO_GROUP = "239.1.2.3"
        private const val VIDEO_PORT = 10091
        private const val CONTROL_PORT = 10092
        private const val AUDIO_PORT = 10001
        private const val VIDEO_WIDTH = 640
        private const val VIDEO_HEIGHT = 480
        private const val VIDEO_BITRATE = 800_000
        private const val VIDEO_FPS = 15
        private const val MAX_FRAG = ConfProtocol.MAX_FRAG
        private const val HEADER_LEN = ConfProtocol.HEADER_LEN
        private const val ID_FIELD_LEN = 8
        private const val MEMBER_TIMEOUT_MS = 6000L
        private const val CSD_REPEAT_MS = 2000L
        private const val IDR_REQUEST_AFTER_MS = 2000L
        private const val IDR_REQUEST_INTERVAL_MS = 2000L
        private const val FRAG_PACE = 12
        private const val FLAG_CONFIG = ConfProtocol.FLAG_CONFIG
        private const val FLAG_IDR = ConfProtocol.FLAG_IDR
        private const val FLAG_LAST = ConfProtocol.FLAG_LAST
        private const val FLAG_FRAME_START = ConfProtocol.FLAG_FRAME_START

        @Volatile private var activeManager: ConferenceManager? = null
        @Volatile private var pendingProjection: MediaProjection? = null

        /**
         * ConferenceHostService 在 getMediaProjection 成功后调用：
         * 把 MediaProjection 的所有权显式交给当前会议实例（而不是静态 holder）。
         */
        fun handoffScreenProjection(projection: MediaProjection) {
            val manager = activeManager
            if (manager != null) manager.onScreenProjectionReady(projection) else pendingProjection = projection
        }

        /** 去掉写入端补的 NUL 与空白：成员表/信标/槽位统一使用归一化后的 ID */
        internal fun normalizeId(raw: String): String = ConfProtocol.normalizeId(raw)
    }

    private fun takePendingProjection() {
        val projection = pendingProjection ?: return
        pendingProjection = null
        onScreenProjectionReady(projection)
    }

    private fun writeShortBE(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = ((value shr 8) and 0xFF).toByte()
        dst[offset + 1] = (value and 0xFF).toByte()
    }
}

// ─── 槽位内部使用的有界常量 ───
private const val MAX_QUEUE_FRAMES = 30
private const val FRAME_TIMEOUT_MS = 1500L

/** 协议层类型别名 —— 实现已提取到 ConfProtocol(生产与单元测试共用同一份) */
private typealias DeliveredFrame = ConfProtocol.DeliveredFrame
private typealias FrameAssembler = ConfProtocol.FrameAssembler

private class QueuedFrame(val data: ByteArray, val isIdr: Boolean)

/**
 * 单个成员的视频重组 + 解码器。
 *
 * 线程模型：
 * - [onFragment] 由唯一的 UDP 收包线程调用，只做纯 CPU 重组，绝不解码；
 * - attach/detach/postConfig/postFrame 全部投递到槽位自己的 HandlerThread，
 *   解码器创建、喂帧、输出释放都在该线程串行执行；
 * - [snapshotStats] 由 housekeep 线程每秒读取一次，仅读 volatile 字段。
 */
private class DecoderSlot(private val sourceId: String) {

    private val assembler = FrameAssembler()
    private val worker = HandlerThread("conf-dec-${sourceId.take(8)}").apply { start() }
    private val handler = Handler(worker.looper)
    private val queueLock = Any()
    private val queue = ArrayDeque<QueuedFrame>()
    private var drainScheduled = false

    @Volatile private var closed = false
    @Volatile private var surfaceAttached = false
    @Volatile private var decoderState = "等待视频数据"
    @Volatile private var lastError: String? = null
    @Volatile private var lastDeliveredAtMs = 0L
    @Volatile private var rxFragments = 0L
    @Volatile private var rxFrames = 0L
    @Volatile private var keyFrames = 0L
    @Volatile private var configFrames = 0L
    @Volatile private var decodedFrames = 0L
    @Volatile private var droppedFrames = 0L
    @Volatile private var idrRequests = 0L

    // 以下只在槽位线程访问
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var csd0: ByteArray? = null
    private var csd1: ByteArray? = null
    private var csdFingerprint = 0
    private var hasCsd = false
    private var presentationUs = 0L

    fun isClosed(): Boolean = closed

    fun hasSurface(): Boolean = surfaceAttached

    fun lastDeliveredAtMs(): Long = lastDeliveredAtMs

    fun noteIdrRequest() {
        idrRequests++
    }

    fun attach(texture: SurfaceTexture) {
        if (closed) return
        surfaceAttached = true
        // SurfaceTexture 属于主线程；先设默认缓冲尺寸再交给槽位线程创建 Surface
        runCatching { texture.setDefaultBufferSize(640, 480) }
        handler.post {
            if (closed) return@post
            releaseCodecLocked()
            val newSurface = runCatching { Surface(texture) }.getOrNull()
            if (newSurface == null) {
                surfaceAttached = false
                decoderState = "画面创建失败"
                return@post
            }
            val old = surface
            surface = newSurface
            runCatching { old?.release() }
            decoderState = "画面已绑定，等待参数集"
            tryConfigureLocked()
        }
    }

    fun detach() {
        surfaceAttached = false
        synchronized(queueLock) { queue.clear() }
        handler.post {
            releaseCodecLocked()
            runCatching { surface?.release() }
            surface = null
            decoderState = "画面已解绑"
        }
    }

    /**
     * 收包线程入口：把分片交给重组器。
     * 返回 null 表示尚未齐片；返回完整帧后由调用方按整帧 flags 路由 CONFIG / 视频帧。
     */
    fun onFragment(
        frameId: Int,
        fragIdx: Int,
        fragCount: Int,
        flags: Int,
        payload: ByteArray,
    ): DeliveredFrame? {
        if (closed) return null
        rxFragments++
        val delivered = assembler.onFragment(frameId, fragIdx, fragCount, flags, payload) ?: return null
        rxFrames++
        // CONFIG(CSD) 每 2s 重播,不算「收到视频帧」——否则无视频流的成员被重播永久续命,
        // housekeep 的释放判定与 REQ_IDR 节流全部失效
        if (!delivered.isConfig) lastDeliveredAtMs = System.currentTimeMillis()
        if (delivered.isIdr) keyFrames++
        return delivered
    }

    fun postConfig(payload: ByteArray) {
        if (closed) return
        configFrames++
        handler.post { if (!closed) onConfigLocked(payload) }
    }

    fun postFrame(data: ByteArray, isIdr: Boolean) {
        if (closed) return
        synchronized(queueLock) {
            if (queue.size >= MAX_QUEUE_FRAMES) {
                // 队列满：优先丢最旧的 P 帧，绝不先丢 IDR
                var dropIdx = -1
                for (i in queue.indices) {
                    if (!queue[i].isIdr) {
                        dropIdx = i
                        break
                    }
                }
                if (dropIdx < 0) dropIdx = 0
                if (queue.isNotEmpty()) queue.removeAt(dropIdx)
                droppedFrames++
            }
            queue.addLast(QueuedFrame(data, isIdr))
            if (!drainScheduled) {
                drainScheduled = true
                handler.post { drainQueue() }
            }
        }
    }

    fun snapshotStats(nowMs: Long): MemberRxStats = MemberRxStats(
        memberId = sourceId,
        rxFragments = rxFragments,
        rxFrames = rxFrames,
        keyFrames = keyFrames,
        decodedFrames = decodedFrames,
        droppedFrames = droppedFrames,
        missingFragments = assembler.missingFragments,
        configFrames = configFrames,
        lastFrameAtMs = lastDeliveredAtMs,
        lastFrameAgeMs = if (lastDeliveredAtMs == 0L) -1L else (nowMs - lastDeliveredAtMs).coerceAtLeast(0L),
        decoderState = decoderState,
        lastError = lastError,
        surfaceAttached = surfaceAttached,
        requestIdrCount = idrRequests,
    )

    fun release() {
        closed = true
        surfaceAttached = false
        synchronized(queueLock) { queue.clear() }
        handler.post {
            releaseCodecLocked()
            runCatching { surface?.release() }
            surface = null
            runCatching { worker.quitSafely() }
        }
    }

    // ─── 以下只在槽位线程执行 ───

    private fun drainQueue() {
        while (true) {
            val item = synchronized(queueLock) {
                val next = if (queue.isEmpty()) null else queue.removeAt(0)
                if (next == null) drainScheduled = false
                next
            } ?: return
            if (closed) return
            tryConfigureLocked()
            val c = codec ?: continue
            val inputIdx = try {
                c.dequeueInputBuffer(0)
            } catch (t: Throwable) {
                failCodec(L10n.get(R.string.mgr_conf_err_input_buffer, t.message.orEmpty()))
                continue
            }
            if (inputIdx < 0) {
                droppedFrames++
                continue
            }
            try {
                val buf = c.getInputBuffer(inputIdx)
                if (buf == null) {
                    droppedFrames++
                    continue
                }
                buf.clear()
                buf.put(item.data)
                presentationUs += 66_000L // 15fps 名义帧间隔
                c.queueInputBuffer(inputIdx, 0, item.data.size, presentationUs, 0)
                drainOutputLocked(c)
                if (decoderState != "解码中") decoderState = "解码中"
            } catch (t: Throwable) {
                failCodec(L10n.get(R.string.mgr_conf_err_decode, t.message.orEmpty()))
            }
        }
    }

    private fun drainOutputLocked(c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = try {
                c.dequeueOutputBuffer(info, 0)
            } catch (t: Throwable) {
                failCodec(L10n.get(R.string.mgr_conf_err_decode_output, t.message.orEmpty()))
                return
            }
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
            if (outIdx < 0) return
            runCatching { c.releaseOutputBuffer(outIdx, true) }
            decodedFrames++
        }
    }

    /** 解析 CONFIG：payload = [csd0len 2B][csd0][csd1len 2B][csd1] */
    private fun onConfigLocked(payload: ByteArray) {
        if (payload.size < 4) return
        val len0 = u16(payload, 0)
        if (len0 <= 0 || 2 + len0 + 2 > payload.size) return
        val len1 = u16(payload, 2 + len0)
        if (len1 <= 0 || 2 + len0 + 2 + len1 > payload.size) return
        val sps = payload.copyOfRange(2, 2 + len0)
        val pps = payload.copyOfRange(2 + len0 + 2, 2 + len0 + 2 + len1)
        val fingerprint = (sps.contentHashCode() * 31) xor pps.contentHashCode()
        // 首次收到参数集不算“变化”；之后只要 CSD 变了就必须清队列并重建解码器，
        // 即使此刻 codec 还是 null，也不能把旧流的排队帧喂给新参数集。
        val changed = hasCsd && fingerprint != csdFingerprint
        csd0 = sps
        csd1 = pps
        csdFingerprint = fingerprint
        hasCsd = true
        if (changed) {
            // 摄像头↔屏幕切换导致 CSD 变化：丢弃旧流排队帧并重建解码器，
            // 否则会用旧参数集解码新流，表现为持续花屏/黑屏。
            synchronized(queueLock) { queue.clear() }
            releaseCodecLocked()
            decoderState = "参数集变化，重建解码器"
        }
        tryConfigureLocked()
    }

    private fun tryConfigureLocked() {
        if (closed) return
        val target = surface
        if (target == null) {
            decoderState = "等待画面绑定"
            return
        }
        val sps = csd0
        val pps = csd1
        if (sps == null || pps == null) {
            decoderState = "等待参数集(SPS/PPS)"
            return
        }
        if (codec != null) return
        var created: MediaCodec? = null
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 640, 480).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(sps))
                setByteBuffer("csd-1", ByteBuffer.wrap(pps))
            }
            val dec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            created = dec
            dec.configure(format, target, null, 0)
            dec.start()
            codec = dec
            decoderState = "解码器就绪"
            lastError = null
        } catch (t: Throwable) {
            // configure/start 失败:释放刚创建的实例 —— 否则资源紧张时每帧重试泄漏一个 MediaCodec
            created?.let { runCatching { it.release() } }
            decoderState = "解码器创建失败"
            lastError = L10n.get(R.string.mgr_conf_err_decoder_create, t.message.orEmpty())
        }
    }

    private fun releaseCodecLocked() {
        val c = codec
        codec = null
        runCatching { c?.stop() }
        runCatching { c?.release() }
    }

    private fun failCodec(message: String) {
        lastError = message
        decoderState = "解码错误"
        releaseCodecLocked()
    }

    private fun u16(src: ByteArray, offset: Int): Int =
        ((src[offset].toInt() and 0xFF) shl 8) or (src[offset + 1].toInt() and 0xFF)
}
