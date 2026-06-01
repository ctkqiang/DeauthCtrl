package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.WebServerManager
import java.net.Inet4Address
import java.net.NetworkInterface

class WebServerViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = WebServerManager()
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _fileName = MutableStateFlow("index.html (默认)")
    val fileName: StateFlow<String> = _fileName
    private var htmlContent = """
<!DOCTYPE html>
<html lang="zh">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>DeauthCtrl</title><style>body{background:#0a0a0a;color:#e00;font-family:monospace;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;flex-direction:column}h1{font-size:2em;text-shadow:0 0 20px #f00}p{color:#666}</style></head>
<body><h1>Hello World</h1><p>DeauthCtrl v1.0 &mdash; 哪吒网络安全</p><p><code>Server is running.</code></p></body>
</html>""".trimIndent()

    fun loadFile(uri: android.net.Uri) {
        try {
            val ctx = getApplication<Application>()
            val input = ctx.contentResolver.openInputStream(uri)
            htmlContent = input?.bufferedReader()?.readText() ?: return
            _fileName.value = uri.lastPathSegment ?: "index.html"
            _error.value = null
            input?.close()
        } catch (e: Exception) {
            _error.value = "读取失败: ${e.message}"
        }
    }

    fun startServer() {
        val ip = getLocalIp()
        val port = 80
        if (manager.start(htmlContent, port)) {
            _isRunning.value = true
            _serverUrl.value = "http://$ip:$port"
            _error.value = null
        } else {
            _error.value = "服务启动失败"
        }
    }

    fun stopServer() {
        manager.stop()
        _isRunning.value = false
        _serverUrl.value = ""
    }

    private fun getLocalIp(): String {
        try {
            NetworkInterface.getNetworkInterfaces().toList().forEach { iface ->
                iface.inetAddresses.toList().forEach { addr ->
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: return@forEach
                        if (ip.startsWith("192.168.")) return ip
                    }
                }
            }
        } catch (_: Exception) {}
        return "192.168.43.1"
    }

    override fun onCleared() {
        super.onCleared()
        manager.stop()
    }
}
