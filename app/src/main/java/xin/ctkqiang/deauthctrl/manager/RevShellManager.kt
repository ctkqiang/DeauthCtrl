package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

data class ShellLog(val direction: String, val text: String, val timestamp: Long = System.currentTimeMillis())
class RevShellManager {
    @Volatile var isRunning = false; private set
    @Volatile var isConnected = false; private set
    private var serverSocket: ServerSocket? = null
    private var client: Socket? = null
    private var job: Job? = null
    var onLog: ((ShellLog) -> Unit)? = null

    fun start(port: Int) {
        stop()
        isRunning = true
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                onLog?.invoke(ShellLog("sys", "Listening on 0.0.0.0:$port"))
                client = serverSocket?.accept()
                isConnected = true
                val addr = client?.inetAddress?.hostAddress ?: "?"
                onLog?.invoke(ShellLog("sys", "Connection from $addr"))
                val reader = BufferedReader(InputStreamReader(client!!.getInputStream()))
                while (isRunning) {
                    val line = reader.readLine() ?: break
                    onLog?.invoke(ShellLog("in", line))
                }
            } catch (e: Exception) { if (isRunning) onLog?.invoke(ShellLog("sys", "Error: ${e.message}")) }
            finally { isConnected = false; isRunning = false }
        }
    }

    fun send(cmd: String) {
        try {
            val writer = OutputStreamWriter(client?.outputStream ?: return)
            writer.write("$cmd\n"); writer.flush()
            onLog?.invoke(ShellLog("out", cmd))
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning = false; isConnected = false; job?.cancel()
        try { client?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
    }
}
