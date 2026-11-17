package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.math.min

/** 会议流源类型 */
enum class ConfStreamType(val beacon: String, val label: String) {
    NONE("none", "关闭"),
    CAMERA_FRONT("front", "前置摄"),
    CAMERA_BACK("back", "后置摄"),
    SCREEN("screen", "屏幕"),
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
 * 局域网战术会议 —— 无互联网多方视频 + 语音
 *
 * 自建协议,不依赖 WebRTC/外网服务器,一切流量只在同一 WiFi 内:
 *
 * ## 成员发现
 * UDP 9998 广播 `CONF|id|name|streamType|talking`（每 2s 一发,6s 超时离线）
 *
 * ## 视频（每方一路,所有人同时共享）
 * - 源：Camera2（前/后置,直通编码器 Surface）或 MediaProjection 屏幕
 * - H.264 Baseline 640×480@15fps 800kbps 硬件编码
 * - UDP 组播 239.1.2.3:10091 分片传输（14B 头:sourceId/frameId/片号/标志）
 * - 接收端按 sourceId 独立重组 + 独立 MediaCodec 解码到各成员 Surface
 * - 编码器每 2s 强制 IDR；接收端仅从 IDR 起组帧,丢包等下一关键帧自愈
 *
 * ## 语音（PTT 半双工,防啸叫）
 * 按住说话：8kHz PCM 20ms 帧单播到每个成员（UDP 10001）；
 * 接收端抖动缓冲 + AudioTrack 播放,收到音频即点亮"说话中"指示。
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

    private val _videoStats = MutableStateFlow("视频: 未开始")
    val videoStats: StateFlow<String> = _videoStats

    /** 本机 ID —— 首次生成后持久化 */
    val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("deauthctrl_prefs", Context.MODE_PRIVATE)
        prefs.getString(KEY_CONF_ID, null) ?: run {
            val id = (1..6).map { "0123456789ABCDEF"[kotlin.random.Random.nextInt(16)] }.joinToString("")
            prefs.edit().putString(KEY_CONF_ID, id).apply()
            id
        }
    }

    /** 操作员呼号 —— 由 UI 从设置注入 */
    @Volatile var deviceName: String = "operator"

    // ─── 成员表 ───
    private val memberMap = ConcurrentHashMap<String, ConfMember>()

    // ─── 网络 ───
    private var beaconSocket: DatagramSocket? = null
    private var videoSendSocket: MulticastSocket? = null
    private var videoRecvSocket: MulticastSocket? = null
    private var audioSendSocket: DatagramSocket? = null
    private var audioRecvSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Volatile private var stopFlag = false

    // ─── 编码器（摄像头或屏幕） ───
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var codec: MediaCodec? = null
    private var codecThread: HandlerThread? = null
    private var codecSurface: Surface? = null
    private var screenProjection: MediaProjection? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    @Volatile private var frameIdGen: Int = 0

    // ─── 解码槽位 ───
    private val slots = ConcurrentHashMap<String, DecoderSlot>()

    // ─── 音频 ───
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val jitter = ConcurrentLinkedQueue<ByteArray>()
    @Volatile private var talkingDown = false

    // ══════════════════════════════════════════════════════════════════════
    // 生命周期
    // ══════════════════════════════════════════════════════════════════════

    /** 进入会议：beacon + 视频组播收发 + 音频接收全部就绪 */
    fun start() {
        if (_running.value) return
        stopFlag = false
        try {
            beaconSocket = DatagramSocket(BEACON_PORT).apply { broadcast = true; reuseAddress = true }
            videoSendSocket = MulticastSocket()
            videoRecvSocket = MulticastSocket(VIDEO_PORT).apply { reuseAddress = true }
            videoRecvSocket!!.joinGroup(java.net.InetSocketAddress(InetAddress.getByName(VIDEO_GROUP), VIDEO_PORT), primaryInterface())
            audioSendSocket = DatagramSocket()
            audioRecvSocket = DatagramSocket(AUDIO_PORT).apply { reuseAddress = true }
            multicastLock = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("conf-lock").apply { setReferenceCounted(false); acquire() }

            initAudioPlayer()
            _running.value = true

            thread(name = "conf-beacon-send") { beaconLoop() }
            thread(name = "conf-beacon-listen") { beaconListen() }
            thread(name = "conf-video-recv") { videoReceiveLoop() }
            thread(name = "conf-audio-recv") { audioReceiveLoop() }
            thread(name = "conf-audio-play") { audioPlayLoop() }
            thread(name = "conf-housekeep") { housekeepLoop() }
        } catch (t: Throwable) {
            _error.value = "会议启动失败: ${t.message}"
            stop()
        }
    }

    fun stop() {
        stopFlag = true
        _running.value = false
        setStream(ConfStreamType.NONE)
        runCatching { beaconSocket?.close() }
        runCatching { videoSendSocket?.close() }
        runCatching { videoRecvSocket?.close() }
        runCatching { audioSendSocket?.close() }
        runCatching { audioRecvSocket?.close() }
        runCatching { multicastLock?.release() }
        runCatching { audioRecord?.stop(); audioRecord?.release() }
        runCatching { audioTrack?.stop(); audioTrack?.release() }
        slots.values.forEach { it.release() }
        slots.clear()
        memberMap.clear()
        _members.value = emptyList()
        _talking.value = false
        _myStream.value = ConfStreamType.NONE
        beaconSocket = null; videoSendSocket = null; videoRecvSocket = null
        audioSendSocket = null; audioRecvSocket = null
    }

    // ══════════════════════════════════════════════════════════════════════
    // 流源切换
    // ══════════════════════════════════════════════════════════════════════

    /** 切换本机流源：关闭 / 前置 / 后置 / 屏幕 */
    fun setStream(type: ConfStreamType) {
        _myStream.value = type
        stopEncoder()
        when (type) {
            ConfStreamType.NONE -> _videoStats.value = "视频: 已关闭"
            ConfStreamType.CAMERA_FRONT -> startCameraEncoder(CameraCharacteristics.LENS_FACING_FRONT)
            ConfStreamType.CAMERA_BACK -> startCameraEncoder(CameraCharacteristics.LENS_FACING_BACK)
            ConfStreamType.SCREEN -> startScreenEncoder()
        }
    }

    // ─── 编码器公共部分 ───
    @Suppress("MissingPermission")
    private fun startCameraEncoder(facing: Int) {
        try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = cameraManager!!.cameraIdList.firstOrNull {
                cameraManager!!.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
            } ?: cameraManager!!.cameraIdList.firstOrNull() ?: run {
                _error.value = "未检测到相机"
                _myStream.value = ConfStreamType.NONE
                return
            }
            cameraManager!!.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    cameraDevice = cam
                    startEncoderSurface { surface ->
                        cam.createCaptureSession(listOf(surface), object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: android.hardware.camera2.CameraCaptureSession) {
                                val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                    addTarget(surface)
                                }
                                s.setRepeatingRequest(req.build(), null, codecThread?.let { Handler(it.looper) })
                                _videoStats.value = "视频: 摄像头 ${if (facing == CameraCharacteristics.LENS_FACING_FRONT) "前置" else "后置"} 640×480@15"
                            }

                            override fun onConfigureFailed(s: android.hardware.camera2.CameraCaptureSession) {
                                _error.value = "相机编码会话失败"
                            }
                        }, codecThread?.let { Handler(it.looper) })
                    }
                }

                override fun onDisconnected(cam: CameraDevice) { cam.close(); cameraDevice = null }
                override fun onError(cam: CameraDevice, error: Int) {
                    cam.close(); cameraDevice = null
                    _error.value = "相机错误 $error"
                }
            }, codecThread?.let { Handler(it.looper) } ?: run {
                // 编码器线程由 startEncoderSurface 创建；此处先用主线程兜底
                Handler(context.mainLooper)
            })
        } catch (t: Throwable) {
            _error.value = "相机启动失败: ${t.message}"
            _myStream.value = ConfStreamType.NONE
        }
    }

    private fun startScreenEncoder() {
        val projection = screenProjectionHolder
        if (projection == null) {
            _videoStats.value = "视频: 等待屏幕授权…"
            return
        }
        screenProjection = projection
        startEncoderSurface { surface ->
            val dpi = context.resources.displayMetrics.densityDpi
            virtualDisplay = projection.createVirtualDisplay(
                "ConfScreen", VIDEO_WIDTH, VIDEO_HEIGHT, dpi,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null,
            )
            _videoStats.value = "视频: 屏幕 640×480@15"
        }
    }

    /**
     * 启动 H.264 编码器并回调其输入 Surface。
     * 输出经分片后组播；CSD（SPS/PPS）作为 CONFIG 分片先行广播。
     */
    private fun startEncoderSurface(onSurface: (Surface) -> Unit) {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                }
            }
            val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec = c
            codecThread = HandlerThread("conf-enc").apply { start() }

            c.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}

                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    if (info.size <= 0) {
                        codec.releaseOutputBuffer(index, false)
                        return
                    }
                    val buf = codec.getOutputBuffer(index) ?: run {
                        codec.releaseOutputBuffer(index, false)
                        return
                    }
                    val data = ByteArray(info.size)
                    buf.position(info.offset)
                    buf.get(data, 0, info.size)
                    val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    val isIdr = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                    broadcastFrame(data, isConfig, isIdr)
                    codec.releaseOutputBuffer(index, false)
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    _error.value = "编码器错误: ${e.message}"
                }

                override fun onOutputFormatChanged(codec: MediaCodec, fmt: MediaFormat) {
                    // 提取 CSD（SPS/PPS）并作为 CONFIG 帧广播
                    try {
                        val csd0 = fmt.getByteBuffer("csd-0") ?: return
                        val csd1 = fmt.getByteBuffer("csd-1") ?: return
                        val sps = ByteArray(csd0.remaining()).also { csd0.get(it) }
                        val pps = ByteArray(csd1.remaining()).also { csd1.get(it) }
                        // payload: [csd0len 2B][csd0][csd1len 2B][csd1]
                        val payload = ByteArray(2 + sps.size + 2 + pps.size)
                        writeShortBE(payload, 0, sps.size)
                        System.arraycopy(sps, 0, payload, 2, sps.size)
                        writeShortBE(payload, 2 + sps.size, pps.size)
                        System.arraycopy(pps, 0, payload, 4 + sps.size, pps.size)
                        broadcastFrame(payload, isConfig = true, isIdr = false)
                    } catch (t: Throwable) {
                        _error.value = "CSD 提取失败: ${t.message}"
                    }
                }
            }, Handler(codecThread!!.looper))

            val surface = c.createInputSurface()
            c.start()
            codecSurface = surface
            onSurface(surface)
        } catch (t: Throwable) {
            _error.value = "编码器启动失败: ${t.message}"
            _myStream.value = ConfStreamType.NONE
        }
    }

    private fun stopEncoder() {
        runCatching { cameraDevice?.close() }
        cameraDevice = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { screenProjection?.stop() }
        screenProjection = null
        screenProjectionHolder = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        codecSurface = null
        runCatching { codecThread?.quitSafely() }
        codecThread = null
    }

    // ══════════════════════════════════════════════════════════════════════
    // 分片广播
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 把一帧拆成 ≤1300B 的分片组播。
     * 头 14B：sourceId(8B ASCII) | frameId(2B BE) | 片号(1B) | 总片数(1B) | 标志(1B) | 保留(1B)
     * 标志位：0x01 CONFIG(CSD) 0x02 IDR 0x04 末片
     */
    private fun broadcastFrame(data: ByteArray, isConfig: Boolean, isIdr: Boolean) {
        val sock = videoSendSocket ?: return
        val maxPayload = MAX_FRAG - HEADER_LEN
        val fragCount = ((data.size + maxPayload - 1) / maxPayload).coerceAtLeast(1)
        if (fragCount > 255) {
            _error.value = "帧过大 ${data.size}B,放弃发送"
            return
        }
        val frameId = (frameIdGen++ and 0xFFFF)
        try {
            for (fragIdx in 0 until fragCount) {
                val off = fragIdx * maxPayload
                val len = min(maxPayload, data.size - off)
                val packet = ByteArray(HEADER_LEN + len)
                val src = deviceId.toByteArray(Charsets.US_ASCII)
                System.arraycopy(src, 0, packet, 0, 8)
                writeShortBE(packet, 8, frameId)
                packet[10] = fragIdx.toByte()
                packet[11] = fragCount.toByte()
                var flags = 0
                if (isConfig) flags = flags or 0x01
                if (isIdr) flags = flags or 0x02
                if (fragIdx == fragCount - 1) flags = flags or 0x04
                packet[12] = flags.toByte()
                System.arraycopy(data, off, packet, HEADER_LEN, len)
                sock.send(DatagramPacket(packet, packet.size, InetAddress.getByName(VIDEO_GROUP), VIDEO_PORT))
            }
        } catch (t: Throwable) {
            _error.value = "视频发送失败: ${t.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 视频接收
    // ══════════════════════════════════════════════════════════════════════

    private fun videoReceiveLoop() {
        val buf = ByteArray(2048)
        while (!stopFlag) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                videoRecvSocket?.receive(packet) ?: continue
                if (packet.length < HEADER_LEN) continue
                val sourceId = String(packet.data, 0, 8, Charsets.US_ASCII).trim()
                if (sourceId == deviceId || sourceId.isEmpty()) continue // 忽略自己的流

                val flags = packet.data[12].toInt() and 0xFF
                val fragCount = packet.data[11].toInt() and 0xFF
                val fragIdx = packet.data[10].toInt() and 0xFF
                val frameId = ((packet.data[8].toInt() and 0xFF) shl 8) or (packet.data[9].toInt() and 0xFF)
                val payload = packet.data.copyOfRange(HEADER_LEN, packet.length)

                val slot = slots.getOrPut(sourceId) { DecoderSlot(sourceId) }
                val complete = slot.onFragment(frameId, fragIdx, fragCount, flags, payload)

                if (complete != null) {
                    if ((flags and 0x01) != 0) {
                        slot.onConfig(complete)
                    } else {
                        slot.onFrame(complete)
                    }
                }
            } catch (t: Throwable) {
                if (!stopFlag) Thread.sleep(50)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 成员发现
    // ══════════════════════════════════════════════════════════════════════

    private fun beaconLoop() {
        val sock = beaconSocket
        while (!stopFlag) {
            try {
                val msg = "CONF|$deviceId|${deviceName.replace("|", "_")}|${_myStream.value.beacon}|${if (_talking.value) 1 else 0}"
                val bytes = msg.toByteArray(Charsets.UTF_8)
                sock?.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), BEACON_PORT))
            } catch (_: Throwable) {}
            Thread.sleep(2000)
        }
    }

    private fun beaconListen() {
        val buf = ByteArray(512)
        while (!stopFlag) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                beaconSocket?.receive(packet) ?: continue
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                if (!text.startsWith("CONF|")) continue
                val parts = text.split("|")
                if (parts.size < 5) continue
                val id = parts[1]
                if (id == deviceId) continue // 忽略自己
                val name = parts[2]
                val stream = parts[3]
                val talking = parts[4] == "1"
                val ip = packet.address?.hostAddress ?: continue
                memberMap[id] = ConfMember(id, name, ip, stream, talking, System.currentTimeMillis())
            } catch (_: Throwable) {
                if (!stopFlag) Thread.sleep(50)
            }
        }
    }

    /** 周期性维护：清理超时成员 + 推送成员表 + 释放无流槽位 */
    private fun housekeepLoop() {
        while (!stopFlag) {
            val now = System.currentTimeMillis()
            memberMap.entries.removeIf { now - it.value.lastSeenMs > MEMBER_TIMEOUT_MS }
            // 本机也进入成员表（streamType 实时）
            memberMap[deviceId] = ConfMember(deviceId, deviceName, "本机", _myStream.value.beacon, _talking.value, now)
            _members.value = memberMap.values.sortedBy { it.name }
            // 成员无视频流 → 释放解码槽
            slots.keys.toList().forEach { id ->
                val m = memberMap[id]
                if (m == null || m.streamType == "none") {
                    slots.remove(id)?.release()
                }
            }
            Thread.sleep(1000)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 音频（PTT）
    // ══════════════════════════════════════════════════════════════════════

    @Suppress("MissingPermission")
    fun talkDown() {
        if (talkingDown || !_running.value) return
        talkingDown = true
        _talking.value = true
        thread(name = "conf-talk-send") {
            var record: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                record = AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
                audioRecord = record
                record.startRecording()
                val frame = ByteArray(320)
                while (talkingDown && !stopFlag) {
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

    private fun audioReceiveLoop() {
        val buf = ByteArray(512)
        while (!stopFlag) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                audioRecvSocket?.receive(packet) ?: continue
                if (packet.length <= 0) continue
                val data = packet.data.copyOfRange(0, packet.length)
                if (jitter.size < 12) jitter.add(data) // 缓冲上限,防止积压延迟
                // 说话指示:记录来源
                val ip = packet.address?.hostAddress
                memberMap.values.firstOrNull { it.ip == ip }?.let { m ->
                    memberMap[m.id] = m.copy(talking = true, lastSeenMs = System.currentTimeMillis())
                }
            } catch (_: Throwable) {
                if (!stopFlag) Thread.sleep(50)
            }
        }
    }

    private fun audioPlayLoop() {
        while (!stopFlag) {
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

    /** 成员画面 Surface 附加 —— 由每格 TextureView 提供 */
    fun attachMemberSurface(memberId: String, texture: SurfaceTexture) {
        val slot = slots.getOrPut(memberId) { DecoderSlot(memberId) }
        slot.attach(texture)
    }

    fun detachMemberSurface(memberId: String) {
        slots[memberId]?.detach()
    }

    private fun primaryInterface(): NetworkInterface? =
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .firstOrNull { iface ->
                    iface.isUp && !iface.isLoopback && iface.inetAddresses.toList()
                        .any { !it.isLoopbackAddress && it is java.net.Inet4Address }
                }
        }.getOrNull()

    companion object {
        private const val KEY_CONF_ID = "conf_device_id"
        private const val BEACON_PORT = 9998
        private const val VIDEO_GROUP = "239.1.2.3"
        private const val VIDEO_PORT = 10091
        private const val AUDIO_PORT = 10001
        private const val VIDEO_WIDTH = 640
        private const val VIDEO_HEIGHT = 480
        private const val VIDEO_BITRATE = 800_000
        private const val VIDEO_FPS = 15
        private const val MAX_FRAG = 1300
        private const val HEADER_LEN = 14
        private const val MEMBER_TIMEOUT_MS = 6000L

        /**
         * 屏幕授权桥：ConferenceHostService 在拿到 MediaProjection 后写入，
         * ConferenceManager 在 setStream(SCREEN) 时读取并消费。
         */
        @Volatile var screenProjectionHolder: MediaProjection? = null
    }

    private fun writeShortBE(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = ((value shr 8) and 0xFF).toByte()
        dst[offset + 1] = (value and 0xFF).toByte()
    }
}

/**
 * 单个成员的视频重组 + 解码器
 *
 * 重组规则：
 * - 新 IDR 到来清空旧缓冲（丢失的旧帧不再等待）；
 * - 非 IDR 帧按 frameId 收片，齐片交付，缺片丢弃（下个 IDR 自愈）；
 * - CONFIG 帧（CSD）到达后与已附加的 Surface 一起触发解码器创建。
 */
private class DecoderSlot(private val sourceId: String) {

    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var csd0: ByteArray? = null
    private var csd1: ByteArray? = null
    private val frags = HashMap<Int, ByteArray?>()
    private var currentFrameId = -1
    private var expectedCount = 0
    private var receivedCount = 0
    private var presentationUs = 0L

    fun attach(texture: SurfaceTexture) {
        runCatching { texture.setDefaultBufferSize(640, 480) }
        surface = Surface(texture)
        tryConfigure()
    }

    fun detach() {
        runCatching { surface?.release() }
        surface = null
    }

    /** 解析 CONFIG 帧：payload = [csd0len 2B][csd0][csd1len 2B][csd1] */
    fun onConfig(payload: ByteArray) {
        if (payload.size < 4) return
        val len0 = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        csd0 = payload.copyOfRange(2, 2 + len0)
        val len1Off = 2 + len0
        if (payload.size < len1Off + 2) return
        val len1 = ((payload[len1Off].toInt() and 0xFF) shl 8) or (payload[len1Off + 1].toInt() and 0xFF)
        csd1 = payload.copyOfRange(len1Off + 2, len1Off + 2 + len1)
        tryConfigure()
    }

    fun onFragment(
        frameId: Int,
        fragIdx: Int,
        fragCount: Int,
        flags: Int,
        payload: ByteArray,
    ): ByteArray? {
        if (fragCount == 0) return null
        // IDR 帧开始 → 清空旧重组缓冲
        if ((flags and 0x02) != 0) {
            frags.clear()
            currentFrameId = frameId
            expectedCount = fragCount
            receivedCount = 0
        } else if (frameId != currentFrameId) {
            return null // 非当前帧的残片
        }
        if (fragIdx >= fragCount || fragCount != expectedCount) return null
        if (frags.containsKey(fragIdx)) return null
        frags[fragIdx] = payload
        receivedCount++
        if (receivedCount < expectedCount) return null
        // 齐片组装
        val total = frags.values.sumOf { it?.size ?: 0 }
        if (frags.values.any { it == null }) {
            frags.clear()
            return null
        }
        val out = ByteArray(total)
        var off = 0
        for (i in 0 until fragCount) {
            val p = frags[i]!!
            System.arraycopy(p, 0, out, off, p.size)
            off += p.size
        }
        frags.clear()
        return out
    }

    fun onFrame(data: ByteArray) {
        val c = codec ?: return
        try {
            val idx = c.dequeueInputBuffer(20_000)
            if (idx < 0) return
            val buf = c.getInputBuffer(idx) ?: return
            buf.clear()
            buf.put(data)
            presentationUs += 66_000 // 15fps 名义帧间隔
            c.queueInputBuffer(idx, 0, data.size, presentationUs, 0)
            // 同步输出
            val info = MediaCodec.BufferInfo()
            while (true) {
                val outIdx = c.dequeueOutputBuffer(info, 0)
                if (outIdx < 0) break
                c.releaseOutputBuffer(outIdx, true)
            }
        } catch (_: Throwable) {}
    }

    private fun tryConfigure() {
        if (codec != null || surface == null || csd0 == null || csd1 == null) return
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 640, 480).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
                setByteBuffer("csd-1", ByteBuffer.wrap(csd1))
            }
            val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.configure(format, surface, null, 0)
            c.start()
            codec = c
        } catch (_: Throwable) {
            codec = null
        }
    }

    fun release() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { surface?.release() }
        codec = null
        surface = null
        frags.clear()
    }
}
