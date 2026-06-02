package xin.ctkqiang.deauthctrl.mirroring.host

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedOutputStream
import java.net.ServerSocket

/**
 * TCP 视频流服务器。
 *
 * ## CSD 缓存
 * 编码器在启动时发送 CSD 帧（SPS/PPS，marker=0x01），
 * 这些帧被缓存并在每个新客户端连接时优先发送，
 * 确保解码器即使中途连接也能正确初始化。
 */
class VideoStreamServer {

    companion object {
        private const val TAG = "VideoStreamServer"
        const val PORT = 10086
    }

    private var serverSocket: ServerSocket? = null
    private var clientSocket: java.net.Socket? = null
    private var outStream: BufferedOutputStream? = null
    private var acceptJob: Job? = null
    private var hasClient = false

    /** 缓存的 CSD 帧（SPS/PPS），新客户端连接时重发 */
    private val csdFrames = mutableListOf<ByteArray>()

    @Volatile var isRunning = false
        private set

    var dataChannel: Channel<ByteArray>? = null

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true
        serverSocket = ServerSocket(PORT)
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                try {
                    val s = serverSocket!!.accept()
                    synchronized(this@VideoStreamServer) {
                        clientSocket?.close()
                        clientSocket = s
                        outStream = BufferedOutputStream(s.getOutputStream())
                        s.tcpNoDelay = true
                        // 先发送缓存的 CSD 帧
                        for (csd in csdFrames) writeFrame(csd)
                        hasClient = true
                    }
                    Log.d(TAG, "客户端已连接: ${s.inetAddress}，CSD 缓存=${csdFrames.size}")
                } catch (e: Exception) {
                    if (isRunning) Log.w(TAG, "accept", e)
                }
            }
        }
    }

    suspend fun pumpLoop() {
        while (dataChannel == null) delay(100)
        for (packet in dataChannel!!) {
            // 缓存 CSD 帧（packet[4] = 0x01）
            if (packet.size > 4 && packet[4] == ScreenCaptureManager.CSD_MARKER) {
                csdFrames.add(packet)
                if (csdFrames.size > 16) csdFrames.removeFirst()
            }
            synchronized(this) {
                if (hasClient && outStream != null) {
                    try { writeFrame(packet) } catch (e: Exception) {
                        hasClient = false
                        outStream = null
                        clientSocket?.close()
                        clientSocket = null
                    }
                }
            }
        }
    }

    private fun writeFrame(data: ByteArray) {
        val s = outStream ?: return
        s.write((data.size shr 24) and 0xFF)
        s.write((data.size shr 16) and 0xFF)
        s.write((data.size shr 8) and 0xFF)
        s.write(data.size and 0xFF)
        s.write(data)
        s.flush()
    }

    fun stop() {
        isRunning = false
        acceptJob?.cancel()
        try { outStream?.close() } catch (_: Exception) {}
        try { clientSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        outStream = null; clientSocket = null; serverSocket = null
        csdFrames.clear()
    }
}
