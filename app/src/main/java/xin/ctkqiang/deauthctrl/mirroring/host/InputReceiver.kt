package xin.ctkqiang.deauthctrl.mirroring.host

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket

/**
 * TCP server that receives touch event coordinates from the connected client.
 *
 * Each line from the client is parsed as a [TouchEvent] via [TouchProtocol.decode]
 * and forwarded to [TouchInputInjector.injectTouch].
 */
class InputReceiver(
    private val injector: TouchInputInjector,
) {

    companion object {
        private const val TAG = "InputReceiver"
        const val PORT = 10087
    }

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile var isRunning = false
        private set

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        serverSocket = ServerSocket(PORT)
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isRunning) {
                try {
                    val socket = serverSocket!!.accept()
                    Log.d(TAG, "Input client connected: ${socket.inetAddress}")
                    launch { handleClient(socket) }
                } catch (e: Exception) {
                    if (isRunning) Log.w(TAG, "Accept interrupted", e)
                }
            }
        }
    }

    private suspend fun handleClient(socket: java.net.Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            var line: String?
            while (isRunning) {
                line = reader.readLine() ?: break
                val event = xin.ctkqiang.deauthctrl.mirroring.model.TouchProtocol.decode(line)
                withContext(Dispatchers.Main) { injector.injectTouch(event) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Input client disconnected", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        acceptJob?.cancel()
        serverSocket?.close()
        serverSocket = null
    }
}
