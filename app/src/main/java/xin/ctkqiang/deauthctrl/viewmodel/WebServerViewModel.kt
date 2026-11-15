package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import xin.ctkqiang.deauthctrl.manager.WebServerManager
import xin.ctkqiang.deauthctrl.manager.WebServerStats
import xin.ctkqiang.deauthctrl.model.JammerLogEntry
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Web 服务 ViewModel
 *
 * 本类**只做委托**，不持有任何与服务器本身有关的状态：
 * 运行状态、日志、错误、统计全部直接暴露 [WebServerManager] 的 `StateFlow`，
 * 保证 UI 看到的永远是内核的真实状态，不会出现"两个真相"。
 * ViewModel 自己只保留两项与"托管内容"有关的本地状态：[serverUrl] 与 [fileName]。
 *
 * 继承 `AndroidViewModel` 的唯一原因：需要 `ContentResolver` 去读用户选择的 `content://` 文件。
 *
 * ## 默认行为
 * - 内置一份 "Hello World" 页面，用户不选文件也能直接启动
 * - 自动探测本机局域网 IPv4（优先 192.168.x.x）
 * - 端口使用 [WebServerManager.DEFAULT_PORT]（8080）。**不是 80**：
 *   非 root 的 Android 应用没有 `CAP_NET_BIND_SERVICE`，绑定 80 必然 `EACCES`。
 *
 * ## 暴露给 UI 的状态流
 * - [isRunning] 服务器是否正在 accept（来自内核）
 * - [log]       环形缓冲日志（来自内核）
 * - [error]     最近一次错误（来自内核）
 * - [stats]     请求计数/连接数/每分钟请求数/uptime（来自内核）
 * - [serverUrl] 当前可访问的 URL，停止后为空串
 * - [fileName]  当前托管的文件名
 */
class WebServerViewModel(application: Application) : AndroidViewModel(application) {

    /** 内核。UI 需要 `DEFAULT_PORT` / `PORT_MIN` / `PORT_MAX` 等常量时直接引用其伴生对象。 */
    val manager = WebServerManager()

    val isRunning: StateFlow<Boolean> = manager.isRunning
    val log: StateFlow<List<JammerLogEntry>> = manager.log
    val stats: StateFlow<WebServerStats> = manager.stats

    /**
     * 本地错误：只描述"选择/读取 HTML 文件"这一类由 ViewModel 自己负责的失败。
     * 与内核的运行期错误分开持有，避免两个写者抢同一个 StateFlow。
     */
    private val _localError = MutableStateFlow<String?>(null)

    /**
     * 对外统一的错误通道：本地错误优先，其次才是内核的运行期错误。
     *
     * 两个来源都非空时显示本地错误，因为那是用户刚刚亲手触发的动作；
     * 读取成功后 [loadFile] 会清掉本地错误，于是自动回落到内核错误。
     */
    val error: StateFlow<String?> = combine(manager.error, _localError) { runtime, local ->
        local ?: runtime
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _fileName = MutableStateFlow(DEFAULT_FILE_NAME)
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    /** 当前待托管的 HTML。默认是内嵌页面，可被 [loadFile] 覆盖。 */
    private var htmlContent: String = DEFAULT_HTML

    /**
     * 从 URI 加载 HTML 文件。
     *
     * 用 `use {}` 保证流一定关闭；读取失败不抛出，只写 [error]，
     * 并**保留原来的页面内容**，避免用户选错文件后把好页面清空。
     *
     * @param uri 文件选择器返回的 `content://` URI
     */
    fun loadFile(uri: Uri) {
        try {
            val resolver = getApplication<Application>().contentResolver
            val text = resolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
            if (text.isNullOrBlank()) {
                _localError.value = "读取失败: 文件为空或无法打开"
                return
            }
            htmlContent = text
            _fileName.value = uri.lastPathSegment?.substringAfterLast('/') ?: DEFAULT_FILE_NAME
            _localError.value = null
        } catch (t: Throwable) {
            _localError.value = "读取失败: ${t.javaClass.simpleName}"
        }
    }

    /**
     * 启动服务器。
     *
     * [WebServerManager.start] 是同步绑定：绑定成功才返回 true，
     * 失败时 [error] 已由内核写好（例如 "bind failed on port 8080 (BindException)"），
     * 这里不再覆盖，避免用一句笼统的"启动失败"盖住真实原因。
     *
     * @param port 监听端口，默认 [WebServerManager.DEFAULT_PORT]；越界由内核钳制
     */
    fun startServer(port: Int = WebServerManager.DEFAULT_PORT) {
        if (!manager.start(htmlContent, port)) return
        _serverUrl.value = "http://${getLocalIp()}:${manager.port}"
    }

    /**
     * 停止服务器。
     *
     * 清空 [serverUrl]，使 UI 不再显示一个已经不可访问的地址。
     */
    fun stopServer() {
        manager.stop()
        _serverUrl.value = ""
    }

    /** 清空日志并把计数器归零。 */
    fun clearLog() = manager.clearLog()

    /** 把毫秒时长格式化为 `hh:mm:ss`。 */
    fun formatUptime(ms: Long): String = manager.formatUptime(ms)

    /**
     * 探测本机局域网 IPv4。
     *
     * 优先返回 192.168.x.x（个人热点与家用路由器最常见的网段），
     * 否则退回任意非回环 IPv4，最后才用 Android 热点的固定网关地址兜底。
     */
    private fun getLocalIp(): String {
        var fallback: String? = null
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return HOTSPOT_FALLBACK_IP
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                    val ip = addr.hostAddress ?: continue
                    if (ip.startsWith(LAN_PREFIX)) return ip
                    if (fallback == null) fallback = ip
                }
            }
        } catch (_: Throwable) {
        }
        return fallback ?: HOTSPOT_FALLBACK_IP
    }

    override fun onCleared() {
        super.onCleared()
        // destroy 会 stop + 释放协程作用域，避免 accept 循环的 Job 在 Activity 销毁后仍存活
        manager.destroy()
    }

    companion object {
        private const val DEFAULT_FILE_NAME = "index.html (默认)"
        private const val HOTSPOT_FALLBACK_IP = "192.168.43.1"
        private const val LAN_PREFIX = "192.168."

        private val DEFAULT_HTML = """
<!DOCTYPE html>
<html lang="zh">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>DeauthCtrl</title><style>body{background:#0a0a0a;color:#e00;font-family:monospace;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;flex-direction:column}h1{font-size:2em;text-shadow:0 0 20px #f00}p{color:#666}</style></head>
<body><h1>Hello World</h1><p>DeauthCtrl v1.0 &mdash; 哪吒网络安全</p><p><code>Server is running.</code></p></body>
</html>""".trimIndent()
    }
}
