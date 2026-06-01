package xin.ctkqiang.deauthctrl.manager

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

/**
 * 对讲机已发现的设备数据类
 *
 * @property ip IP 地址
 * @property name 设备名称
 * @property lastSeen 最后一次收到广播的时间戳
 */
data class WalkiePeer(
    val ip: String,
    val name: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val isTalking: Boolean = false,
)

/**
 * WiFi 局域网对讲机管理器
 *
 * 实现基于 UDP 的设备发现 + PCM 音频实时流传输，模拟 walkie-talkie（对讲机）功能。
 * 所有通信仅限于同一 WiFi 局域网内的设备，不经过外网。
 *
 * ## 工作原理
 * 1. **设备发现**：每台设备在 UDP 9999 端口广播自己的设备名和 IP
 * 2. **音频采集**：按住 PTT 按钮时，AudioRecord 从麦克风采集 PCM 音频（8000Hz, 16-bit, 单声道）
 * 3. **音频传输**：将 20ms 音频帧（320 字节）通过 UDP 单播发送到每个已发现的设备（端口 10000）
 * 4. **音频播放**：每台设备在端口 10000 上监听，收到音频包后通过 AudioTrack 实时播放
 *
 * ## 技术参数
 * - 采样率：8000 Hz（电话音质，低带宽）
 * - 编码：PCM 16-bit，单声道
 * - 帧大小：320 字节（20ms 音频）
 * - 发现端口：UDP 9999（广播）
 * - 音频端口：UDP 10000（单播）
 * - 发现间隔：2 秒
 * - 延迟：约 40-60ms（采集 20ms + 网络 20-40ms）
 *
 * ## 线程模型
 * - 发现广播线程：每 2 秒发送一次广播
 * - 发现监听线程：持续监听 UDP 9999
 * - 音频采集线程：AudioRecord.read() 阻塞读取
 * - 音频播放线程：AudioTrack.write() 阻塞写入
 * - 音频接收线程：DatagramSocket.receive() 阻塞接收
 *
 * ## 权限要求
 * - RECORD_AUDIO：音频录制
 * - INTERNET：UDP 网络通信（AndroidManifest 已声明）
 */
class WalkieTalkieManager {

    /** 设备名称，默认为 Build.MODEL（如 "Pixel 8"） */
    var deviceName: String = Build.MODEL

    /** 对讲机运行状态 */
    @Volatile var isRunning = false
        private set

    /** PTT 按钮按下状态（true = 正在发送音频） */
    @Volatile var isTalking = false
        private set

    /** 已发现的局域网对讲设备 */
    val peers = ConcurrentHashMap<String, WalkiePeer>()

    private var discoverSocket: DatagramSocket? = null
    private var audioSendSocket: DatagramSocket? = null
    private var audioRecvSocket: DatagramSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val jitterBuffer = ConcurrentLinkedQueue<ByteArray>()

    /** 音频格式：8000Hz, 16-bit, 单声道, PCM */
    private val sampleRate = 8000
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    /** 20ms 音频帧 = 8000 * 2 * 0.02 = 320 字节 */
    private val frameSize = 320

    /**
     * 启动对讲机服务
     *
     * 同时启动：
     * - 发现广播线程（每 2 秒广播设备名 + IP）
     * - 发现监听线程（接收其他设备的广播）
     * - 音频接收线程（监听 UDP 10000 端口）
     * - 音频播放线程（消费 jitterBuffer 队列中的音频帧）
     *
     * @return true 表示所有服务已启动
     */
    fun start(): Boolean {
        if (isRunning) return true
        try {
            discoverSocket = DatagramSocket(9999).apply { broadcast = true; reuseAddress = true }
            audioSendSocket = DatagramSocket()
            audioRecvSocket = DatagramSocket(10000).apply { reuseAddress = true }

            initAudioPlayer()
            isRunning = true

            thread(name = "ptt-discover-send") { discoverLoop() }
            thread(name = "ptt-discover-listen") { discoverListen() }
            thread(name = "ptt-audio-recv") { audioReceiveLoop() }
            thread(name = "ptt-audio-play") { audioPlayLoop() }
        } catch (e: Exception) {
            stop()
            return false
        }
        return true
    }

    /**
     * 开始说话（PTT 按下）
     *
     * 初始化 AudioRecord 并启动音频采集+发送线程。
     * 采集的每帧音频通过 UDP 发送到所有已发现的设备。
     */
    fun startTalk() {
        if (!isRunning || isTalking) return
        isTalking = true
        try {
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, audioFormat)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, AudioFormat.CHANNEL_IN_MONO, audioFormat,
                maxOf(minBuf, frameSize * 2),
            )
            audioRecord?.startRecording()
            thread(name = "ptt-audio-send") { audioSendLoop() }
        } catch (e: Exception) {
            isTalking = false
        }
    }

    /**
     * 停止说话（PTT 释放）
     *
     * 停止 AudioRecord 采集并释放资源。
     */
    fun stopTalk() {
        isTalking = false
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    /**
     * 停止对讲机服务
     *
     * 关闭所有 Socket、释放 AudioRecord/AudioTrack 资源。
     */
    fun stop() {
        isRunning = false
        isTalking = false
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
        try { discoverSocket?.close() } catch (_: Exception) {}
        try { audioSendSocket?.close() } catch (_: Exception) {}
        try { audioRecvSocket?.close() } catch (_: Exception) {}
        audioRecord = null; audioTrack = null
        discoverSocket = null; audioSendSocket = null; audioRecvSocket = null
        jitterBuffer.clear(); peers.clear()
    }

    // ─── 设备发现 ────────────────────────────────────────

    /** 每 2 秒广播设备名 + IP */
    private fun discoverLoop() {
        val localIp = getLocalIp() ?: return
        val talking = if (isTalking) "1" else "0"
        val msg = "DEAUTHCTRL_PTT:$deviceName:$localIp:$talking".toByteArray()
        while (isRunning) {
            try {
                val packet = DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), 9999)
                discoverSocket?.send(packet)
            } catch (_: Exception) {}
            Thread.sleep(2000)
        }
    }

    /** 监听其他设备的发现广播 */
    private fun discoverListen() {
        val buf = ByteArray(256)
        while (isRunning) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                discoverSocket?.receive(packet)
                val data = String(packet.data, 0, packet.length)
                if (data.startsWith("DEAUTHCTRL_PTT:")) {
                    val parts = data.removePrefix("DEAUTHCTRL_PTT:").split(":")
                    if (parts.size >= 2) {
                        val name = parts[0]; val ip = parts[1]
                        val talking = parts.getOrElse(2) { "0" } == "1"
                        if (ip != getLocalIp()) peers[ip] = WalkiePeer(ip, name, isTalking = talking)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ─── 音频发送（PTT 按下时） ──────────────────────────

    /** 从 AudioRecord 读取音频帧 → UDP 单播到所有 peer */
    private fun audioSendLoop() {
        val buf = ByteArray(frameSize)
        while (isTalking && isRunning) {
            val n = audioRecord?.read(buf, 0, buf.size) ?: -1
            if (n <= 0) continue
            peers.values.forEach { peer ->
                try {
                    val packet = DatagramPacket(buf, n, InetAddress.getByName(peer.ip), 10000)
                    audioSendSocket?.send(packet)
                } catch (_: Exception) {}
            }
        }
    }

    // ─── 音频接收 ───────────────────────────────────────

    /** 从 UDP 10000 接收音频帧 → 放入 jitterBuffer */
    private fun audioReceiveLoop() {
        val buf = ByteArray(frameSize)
        while (isRunning) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                audioRecvSocket?.receive(packet)
                val frame = packet.data.copyOf(packet.length)
                jitterBuffer.offer(frame)
                // 限制缓冲大小防止延迟累积（最多保留 5 帧 = 100ms）
                while (jitterBuffer.size > 5) jitterBuffer.poll()
            } catch (_: Exception) {}
        }
    }

    /** 从 jitterBuffer 取出音频帧 → AudioTrack 播放 */
    private fun audioPlayLoop() {
        while (isRunning) {
            val frame = jitterBuffer.poll()
            if (frame != null) {
                audioTrack?.write(frame, 0, frame.size)
            } else {
                Thread.sleep(10)
            }
        }
    }

    /** 初始化 AudioTrack 播放器（8000Hz, 16-bit, 单声道） */
    private fun initAudioPlayer() {
        try {
            audioTrack?.stop(); audioTrack?.release()
        } catch (_: Exception) {}
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(android.media.AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(audioFormat)
                .setChannelMask(channelConfig)
                .build())
            .setBufferSizeInBytes(maxOf(minBuf, frameSize * 4))
            .build()
        audioTrack?.play()
    }

    /** 获取本机局域网 IP */
    private fun getLocalIp(): String? {
        try {
            NetworkInterface.getNetworkInterfaces().toList().forEach { iface ->
                iface.inetAddresses.toList().forEach { addr ->
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress && addr.hostAddress?.startsWith("192.168.") == true) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
