package xin.ctkqiang.deauthctrl.mirroring.client

import android.util.Log
import kotlinx.coroutines.*
import xin.ctkqiang.deauthctrl.mirroring.model.TouchEvent
import xin.ctkqiang.deauthctrl.mirroring.model.TouchProtocol
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.Socket

/**
 * 客户端触控输入发送器。
 *
 * 通过 TCP 连接到主机 [InputReceiver] 端口（默认 10087），
 * 将 [TouchEvent] 序列化为文本行发送。
 */
class TouchInputSender {

    companion object {
        private const val TAG = "TouchInputSender"
    }

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    @Volatile var isConnected = false
        private set

    /** 连接到主机的输入接收端口 */
    suspend fun connect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val s = Socket(host, port)
            s.tcpNoDelay = true
            socket = s
            writer = BufferedWriter(OutputStreamWriter(s.getOutputStream()))
            isConnected = true
            Log.d(TAG, "触控通道已连接到 $host:$port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "触控连接失败", e)
            false
        }
    }

    /** 发送触摸事件到主机（非阻塞，IO 线程） */
    fun send(event: TouchEvent) {
        try {
            val line = TouchProtocol.encode(event)
            writer?.apply {
                write(line)
                newLine()
                flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "发送触摸事件失败", e)
        }
    }

    fun disconnect() {
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        socket = null
        isConnected = false
    }
}
