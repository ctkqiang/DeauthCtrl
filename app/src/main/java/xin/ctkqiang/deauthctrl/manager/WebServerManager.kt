package xin.ctkqiang.deauthctrl.manager

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

class WebServerManager {

    private var serverSocket: ServerSocket? = null
    @Volatile var isRunning = false
        private set
    private var htmlContent: ByteArray = ByteArray(0)
    private var port: Int = 8080

    fun start(html: String, port: Int = 8080): Boolean {
        stop()
        this.port = port
        htmlContent = html.toByteArray(Charsets.UTF_8)
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    Thread { serve(client) }.start()
                }
            } catch (_: Exception) {
            } finally {
                isRunning = false
            }
        }.start()
        return true
    }

    private fun serve(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = client.getOutputStream()
            val line = reader.readLine() ?: return

            val (method, path) = line.split(" ").let { it[0] to it.getOrElse(1) { "/" } }

            if (method == "GET") {
                val status = if (path == "/" || path == "/index.html") "200 OK" else "404 Not Found"
                val body = if (path == "/" || path == "/index.html") htmlContent else "<h1>404</h1>".toByteArray()
                val response = buildString {
                    append("HTTP/1.1 $status\r\n")
                    append("Content-Type: text/html; charset=UTF-8\r\n")
                    append("Content-Length: ${body.size}\r\n")
                    append("Connection: close\r\n")
                    append("Server: DeauthCtrl/1.0\r\n")
                    append("\r\n")
                }
                writer.write(response.toByteArray())
                writer.write(body)
                writer.flush()
            }
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
