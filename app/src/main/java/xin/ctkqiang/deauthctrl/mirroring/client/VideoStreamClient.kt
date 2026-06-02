package xin.ctkqiang.deauthctrl.mirroring.client

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedInputStream
import java.io.IOException
import java.net.Socket

/**
 * TCP 客户端：连接到主机接收 H.264 视频字节流。
 *
 * ## 协议
 * 每帧以 4 字节大端序长度前缀开头，后面跟着完整 H.264 NAL 单元数据。
 * 持续读取直到连接断开或手动停止。
 */
class VideoStreamClient {

    companion object {
        private const val TAG = "VideoStreamClient"
    }

    private var socket: Socket? = null
    private var inputStream: BufferedInputStream? = null
    private var readJob: Job? = null

    private val _status = MutableStateFlow(ClientStatus.DISCONNECTED)
    val status: StateFlow<ClientStatus> = _status

    enum class ClientStatus { DISCONNECTED, CONNECTING, CONNECTED }

    /** 连接到主机的视频流端口 */
    suspend fun connect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        _status.value = ClientStatus.CONNECTING
        try {
            val s = Socket(host, port)
            s.tcpNoDelay = true      // 禁用 Nagle 算法，减少延迟
            s.soTimeout = 0           // 无限超时 — 由协程取消控制
            socket = s
            inputStream = BufferedInputStream(s.getInputStream())
            _status.value = ClientStatus.CONNECTED
            Log.d(TAG, "已连接到 $host:$port")
            true
        } catch (e: IOException) {
            Log.e(TAG, "连接失败: $host:$port", e)
            _status.value = ClientStatus.DISCONNECTED
            false
        }
    }

    /**
     * 启动读取循环。
     *
     * @param onFrame 每接收到一帧数据时回调（在 IO 线程上执行）
     */
    fun startReading(onFrame: (ByteArray) -> Unit) {
        readJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val stream = inputStream ?: return@launch
                val lenBuf = ByteArray(4)
                while (isActive) {
                    // 读取 4 字节长度前缀（大端序）
                    var bytesRead = 0
                    while (bytesRead < 4) {
                        val n = stream.read(lenBuf, bytesRead, 4 - bytesRead)
                        if (n < 0) throw IOException("流已结束")
                        bytesRead += n
                    }
                    val frameLen = ((lenBuf[0].toInt() and 0xFF) shl 24) or
                        ((lenBuf[1].toInt() and 0xFF) shl 16) or
                        ((lenBuf[2].toInt() and 0xFF) shl 8) or
                        (lenBuf[3].toInt() and 0xFF)

                    // 读取帧数据
                    val frameData = ByteArray(frameLen)
                    var offset = 0
                    while (offset < frameLen) {
                        val n = stream.read(frameData, offset, frameLen - offset)
                        if (n < 0) throw IOException("流已结束")
                        offset += n
                    }
                    onFrame(frameData)
                }
            } catch (e: IOException) {
                if (isActive) Log.w(TAG, "读取循环异常", e)
            } finally {
                _status.value = ClientStatus.DISCONNECTED
            }
        }
    }

    fun disconnect() {
        readJob?.cancel()
        try { inputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inputStream = null
        socket = null
        _status.value = ClientStatus.DISCONNECTED
    }
}
