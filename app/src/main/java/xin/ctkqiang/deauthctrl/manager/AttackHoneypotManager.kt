package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.net.Socket

/**
 * AnyIntruder 蜜罐 —— 本机监听端口,捕获扫描/攻击尝试。
 * 手机无需 root:监听高位端口即可;收到连接后读首包,
 * 与攻击特征表匹配(SSH 探测 / HTTP 漏洞路径 / 目录扫描)。
 */
class AttackHoneypotManager {

    data class Attempt(
        val ip: String,
        val port: Int,
        val signature: String,
        val timeMs: Long,
    )

    data class State(
        val running: Boolean = false,
        val port: Int = 8088,
        val attempts: List<Attempt> = emptyList(),
        val byIp: Map<String, Int> = emptyMap(),
        val topSignatures: List<Pair<String, Int>> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    @Volatile private var stopFlag = false
    private var server: ServerSocket? = null

    /** 攻击特征表:首包包含即命中 */
    val SIGNATURES = listOf(
        "SSH-" to "SSH 探测",
        "GET /admin" to "HTTP 管理路径扫描",
        "GET /.env" to "HTTP 敏感文件扫描",
        "GET /phpmyadmin" to "HTTP 数据库扫描",
        "GET /wp-login" to "HTTP 登录爆破尝试",
        "GET /cgi-bin" to "HTTP CGI 扫描",
        "POST /cgi-bin" to "HTTP CGI 扫描",
        "GET /shell" to "WebShell 扫描",
        "POST /login" to "HTTP 登录爆破尝试",
        "GET /config" to "HTTP 配置扫描",
        "OPTIONS /" to "HTTP 方法探测",
        "TRACE /" to "HTTP XST 探测",
        "MSSG" to "恶意载荷扫描",
        "GET /actuator" to "框架漏洞扫描",
        "HEAD /" to "HTTP 探测",
    )

    fun classify(firstBytes: String): String? {
        for ((sig, label) in SIGNATURES) {
            if (firstBytes.contains(sig, ignoreCase = true)) return label
        }
        if (firstBytes.isNotBlank()) return "未知探测"
        return null
    }

    suspend fun start(port: Int) = withContext(Dispatchers.IO) {
        stopFlag = false
        _state.update { it.copy(running = true, port = port) }
        runCatching {
            server = ServerSocket(port)
            while (!stopFlag) {
                val sock = server?.accept() ?: break
                handle(sock)
            }
        }
        _state.update { it.copy(running = false) }
    }

    private fun handle(sock: Socket) {
        try {
            sock.soTimeout = 3000
            val buf = ByteArray(256)
            val n = sock.getInputStream().read(buf)
            val text = String(buf, 0, n.coerceAtLeast(0), Charsets.UTF_8).trim()
            val sig = classify(text) ?: return
            val ip = sock.inetAddress?.hostAddress ?: "?"
            val attempt = Attempt(ip, sock.port, sig, System.currentTimeMillis())
            _state.update {
                val byIp = it.byIp.toMutableMap().apply { put(ip, (get(ip) ?: 0) + 1) }
                val sigs = (it.attempts + attempt).groupBy { a -> a.signature }.map { (k, v) -> k to v.size }
                    .sortedByDescending { p -> p.second }.take(8)
                it.copy(attempts = (it.attempts + attempt).takeLast(200), byIp = byIp, topSignatures = sigs)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { sock.close() }
        }
    }

    fun stop() {
        stopFlag = true
        runCatching { server?.close() }
        _state.update { it.copy(running = false) }
    }

    fun clear() {
        _state.update { it.copy(attempts = emptyList(), byIp = emptyMap(), topSignatures = emptyList()) }
    }
}
