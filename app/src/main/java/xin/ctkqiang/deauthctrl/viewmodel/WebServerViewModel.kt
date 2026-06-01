package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.WebServerManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Web 服务器 ViewModel
 *
 * 管理嵌入式 HTTP 服务器的生命周期和状态，提供文件加载和服务器启停功能。
 * 继承 AndroidViewModel 以获取 Application Context（用于 ContentResolver 读取文件）。
 *
 * ## 默认行为
 * - 启动时携带预置的 "Hello World" HTML 页面（无需用户选择文件即可直接启动）
 * - 自动检测本机局域网 IP（遍历 NetworkInterface 查找 192.168.x.x）
 * - 默认监听 80 端口（HTTP 标准端口）
 *
 * ## 状态流
 * - isRunning: 服务器运行状态
 * - serverUrl: 当前服务器 URL（如 "http://192.168.1.5"），空字符串表示未运行
 * - error: 错误信息，null 表示无错误
 * - fileName: 当前托管的文件名，默认为 "index.html (默认)"
 */
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

    /** 当前 HTML 内容，默认值为内嵌的 Hello World 页面 */
    private var htmlContent = """
<!DOCTYPE html>
<html lang="zh">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>DeauthCtrl</title><style>body{background:#0a0a0a;color:#e00;font-family:monospace;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;flex-direction:column}h1{font-size:2em;text-shadow:0 0 20px #f00}p{color:#666}</style></head>
<body><h1>Hello World</h1><p>DeauthCtrl v1.0 &mdash; 哪吒网络安全</p><p><code>Server is running.</code></p></body>
</html>""".trimIndent()

    /**
     * 从 URI 加载 HTML 文件
     *
     * 通过 ContentResolver 打开用户选择的文件 URI，读取全部文本内容。
     *
     * @param uri 由文件选择器返回的 content:// URI
     */
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

    /**
     * 启动 HTTP 服务器
     *
     * 获取本机 IP 地址，调用 WebServerManager.start() 启动服务器。
     * 启动前无需检查 htmlContent 是否为空（已内置默认页面）。
     */
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

    /**
     * 停止 HTTP 服务器
     *
     * 关闭 ServerSocket，清空 URL 状态。
     */
    fun stopServer() {
        manager.stop()
        _isRunning.value = false
        _serverUrl.value = ""
    }

    /**
     * 获取本机局域网 IPv4 地址
     *
     * 遍历所有网络接口，返回第一个 192.168.x.x 的非回环地址。
     *
     * @return IPv4 地址字符串，若未连接局域网则返回默认值 "192.168.43.1"（常见热点 IP）
     */
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
