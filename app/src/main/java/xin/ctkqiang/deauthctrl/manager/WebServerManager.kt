package xin.ctkqiang.deauthctrl.manager

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.model.JammerLogEntry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 嵌入式 HTTP 服务器运行快照。
 *
 * 每个字段都取自本进程真实发生的计数，不含任何推测或夸大：
 * - [totalRequests]     成功解析出请求行的连接数
 * - [served200]         命中受管页面并返回 200 的次数
 * - [notFound404]       路径未命中返回 404 的次数
 * - [rejectedMethod]    非 GET/HEAD 方法被拒次数（405）
 * - [badRequests]       请求行畸形/超长/读超时被拒次数（400、414）
 * - [handlerErrors]     读写与解析过程中抛出异常的次数
 * - [activeConnections] 当前仍持有套接字、尚未归位的连接数
 * - [requestsPerMinute] 以本次会话 uptime 换算的每分钟请求数
 * - [uptimeMs]          本次会话已运行毫秒数；停止后冻结为最终值
 * - [lastError]         最近一次异常描述，供 UI 直接展示
 */
data class WebServerStats(
    val totalRequests: Int = 0,
    val served200: Int = 0,
    val notFound404: Int = 0,
    val rejectedMethod: Int = 0,
    val badRequests: Int = 0,
    val handlerErrors: Int = 0,
    val activeConnections: Int = 0,
    val requestsPerMinute: Float = 0f,
    val uptimeMs: Long = 0L,
    val lastError: String? = null,
)

/**
 * 轻量级嵌入式 HTTP 服务器管理器
 *
 * ## 能力边界（务必先读）
 * 本模块是**局域网静态页面托管器**，工作方式是：在本机（通常是个人热点）某个
 * **非特权端口**上监听 TCP，把内存中的一段 HTML 原样返回给发起 GET 的同网段客户端。
 * 它做的是"把网页发给手机热点里的其他人"这一件事。
 *
 * 需要明确的边界：
 * - 只响应 `GET` / `HEAD`，其余方法一律 405，不解析请求体，不接受上传
 * - 无 TLS：全程明文 HTTP，任何同网段嗅探者都能看到内容
 * - 无鉴权：谁连上热点拿到 IP，谁就能取到页面
 * - 不做目录遍历：只托管内存里那一个页面，路径经归一化后按白名单匹配
 * - **不能**绑定小于 1024 的端口。非 root 的 Android 应用没有
 *   `CAP_NET_BIND_SERVICE`，绑定 80 会直接 `EACCES` 失败，因此默认端口取 8080。
 *   这里会把绑定失败如实上报给调用方，而不是假装"已启动"。
 *
 * ## 线程与资源模型
 * - 计数器全部为原子类型，可被 IO 调度器与主线程并发读写
 * - [ServerSocket] 与页面字节数组存放于 [AtomicReference]，`getAndSet` 保证幂等释放
 * - 所有在途连接记录在 [clients]（并发 Set），[stop] 会主动关闭它们，
 *   不存在"停止后连接仍挂在 accept 队列"的悬空路径
 * - accept 循环设了 `soTimeout`，即使底层 `close()` 未能立刻中断阻塞，
 *   循环也会在超时后重新检查运行标志，避免线程永久卡死
 * - 日志使用 [ArrayDeque] 环形缓冲 + 锁，天然限制内存上限，不再依赖 `takeLast`
 */
class WebServerManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var acceptJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private val _log = MutableStateFlow<List<JammerLogEntry>>(emptyList())
    val log: StateFlow<List<JammerLogEntry>> = _log.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _stats = MutableStateFlow(WebServerStats())
    val stats: StateFlow<WebServerStats> = _stats.asStateFlow()

    /** 当前（或最近一次）会话实际绑定的端口，供上层拼装访问 URL */
    val port: Int get() = boundPort.get()

    private val serverSocket = AtomicReference<ServerSocket?>(null)
    private val htmlContent = AtomicReference<ByteArray>(ByteArray(0))
    private val boundPort = AtomicInteger(DEFAULT_PORT)

    private val clients: MutableSet<Socket> =
        Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

    private val totalRequests = AtomicInteger(0)
    private val served200 = AtomicInteger(0)
    private val notFound404 = AtomicInteger(0)
    private val rejectedMethod = AtomicInteger(0)
    private val badRequests = AtomicInteger(0)
    private val handlerErrors = AtomicInteger(0)
    private val activeConnections = AtomicInteger(0)

    private val sessionStartMs = AtomicLong(0L)
    private val sessionEndMs = AtomicLong(0L)

    private val logBuffer = ArrayDeque<JammerLogEntry>(LOG_LIMIT)
    private val logLock = Any()

    /**
     * 启动 HTTP 服务器。
     *
     * 调用前会先 [stop] 掉既有实例，避免端口被自己占用。
     * 端口会被钳制到 [PORT_MIN]..[PORT_MAX]；越界一律退回 [DEFAULT_PORT]。
     *
     * @param html 要托管的 HTML 全文（UTF-8 编码后缓存）
     * @param port 监听端口，默认 [DEFAULT_PORT]（8080，非特权端口）
     * @return true 表示已成功 bind 并开始 accept；false 表示绑定失败，
     *         具体原因可从 [error] 读取
     */
    fun start(html: String, port: Int = DEFAULT_PORT): Boolean {
        stop()

        val safePort = if (port in PORT_MIN..PORT_MAX) port else DEFAULT_PORT
        boundPort.set(safePort)
        htmlContent.set(html.toByteArray(Charsets.UTF_8))
        resetCounters()
        _error.value = null

        val socket = try {
            ServerSocket().apply {
                reuseAddress = true
                soTimeout = ACCEPT_TIMEOUT_MS
                bind(InetSocketAddress(safePort), BACKLOG)
            }
        } catch (t: Throwable) {
            val message = "bind failed on port $safePort (${t.javaClass.simpleName})"
            _error.value = message
            log("SERVER", message)
            return false
        }

        serverSocket.set(socket)
        sessionStartMs.set(SystemClock.elapsedRealtime())
        _isRunning.value = true
        log("SERVER", "listening on 0.0.0.0:$safePort, payload=${htmlContent.get().size} bytes")

        acceptJob = scope.launch { acceptLoop(socket) }
        publishStats()
        return true
    }

    /**
     * 停止服务器。
     *
     * 顺序为：置停止标志 → 取消 accept 任务 → 关闭监听套接字 → 关闭所有在途连接。
     * 已在处理中的请求会拿到 `SocketException` 后自然退出，不会留下半截响应。
     */
    fun stop() {
        val socket = serverSocket.getAndSet(null)
        val wasRunning = _isRunning.value

        _isRunning.value = false
        acceptJob?.cancel()
        acceptJob = null

        try {
            socket?.close()
        } catch (_: Throwable) {
        }
        closeAllClients()

        if (wasRunning || socket != null) {
            if (sessionStartMs.get() > 0L) sessionEndMs.set(SystemClock.elapsedRealtime())
            log("SERVER", "stopped, active connections cleared")
        }
        publishStats()
    }

    /** 释放协程作用域。ViewModel 销毁时调用，之后本实例不可复用。 */
    fun destroy() {
        stop()
        scope.cancel()
    }

    /** 清空日志并把所有计数器归零，用于开始新一轮观察。 */
    fun clearLog() {
        synchronized(logLock) { logBuffer.clear() }
        _log.value = emptyList()
        resetCounters()
        publishStats()
    }

    /** 把毫秒时长格式化为 `hh:mm:ss`。 */
    fun formatUptime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0L)
        return "%02d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
    }

    // ── 内部实现 ────────────────────────────────────────────────────────────

    private suspend fun acceptLoop(socket: ServerSocket) {
        try {
            while (currentCoroutineContext().isActive && _isRunning.value) {
                val client = try {
                    socket.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
                track(client)
                scope.launch { serve(client) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _error.value = "accept loop error: ${t.javaClass.simpleName}"
            log("SERVER", "accept loop terminated: ${t.javaClass.simpleName}")
        } finally {
            _isRunning.value = false
            publishStats()
        }
    }

    private fun serve(client: Socket) {
        try {
            client.soTimeout = READ_TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val writer = client.getOutputStream()

            val requestLine = reader.readLine()
            if (requestLine.isNullOrBlank()) {
                badRequests.incrementAndGet()
                log("HTTP", "empty request line, dropped")
                return
            }
            if (requestLine.length > MAX_REQUEST_LINE) {
                badRequests.incrementAndGet()
                writeResponse(writer, STATUS_414, CONTENT_TYPE_HTML, BODY_414, includeBody = false)
                log("HTTP", "request line too long (${requestLine.length} chars) -> 414")
                return
            }

            // 请求头必须整体读完，否则短连接关闭时客户端可能收到 RST
            drainHeaders(reader)

            val parts = requestLine.split(' ')
            if (parts.size < 3) {
                badRequests.incrementAndGet()
                writeResponse(writer, STATUS_400, CONTENT_TYPE_HTML, BODY_400, includeBody = true)
                log("HTTP", "malformed request line -> 400")
                return
            }

            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = normalizePath(rawPath)
            val includeBody = method != "HEAD"
            totalRequests.incrementAndGet()

            if (method != "GET" && method != "HEAD") {
                rejectedMethod.incrementAndGet()
                writeResponse(writer, STATUS_405, CONTENT_TYPE_HTML, BODY_405, includeBody = includeBody)
                log("HTTP", "$method $rawPath -> 405")
            } else if (path in SERVED_PATHS) {
                val body = htmlContent.get()
                served200.incrementAndGet()
                writeResponse(writer, STATUS_200, CONTENT_TYPE_HTML, body, includeBody = includeBody)
                log("HTTP", "$method $rawPath -> 200 (${body.size} bytes)")
            } else {
                notFound404.incrementAndGet()
                writeResponse(writer, STATUS_404, CONTENT_TYPE_HTML, BODY_404, includeBody = includeBody)
                log("HTTP", "$method $rawPath -> 404")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SocketTimeoutException) {
            if (_isRunning.value) {
                badRequests.incrementAndGet()
                log("HTTP", "read timeout, connection dropped")
            }
        } catch (t: Throwable) {
            // 停止服务器时我们会主动关闭在途连接，那不是"处理失败"，不要污染统计
            if (_isRunning.value) {
                handlerErrors.incrementAndGet()
                _error.value = "request handling failed: ${t.javaClass.simpleName}"
                log("HTTP", "handler error: ${t.javaClass.simpleName}")
            } else {
                log("HTTP", "connection closed during shutdown")
            }
        } finally {
            release(client)
        }
    }

    /**
     * 把请求头读完（丢弃内容，本服务器不使用任何头部）。
     * 行数设上限，避免恶意客户端用无限头部耗尽连接资源。
     */
    private fun drainHeaders(reader: BufferedReader) {
        var guard = 0
        while (guard++ < MAX_HEADER_LINES) {
            val line = reader.readLine() ?: return
            if (line.isEmpty()) return
        }
    }

    /** 去掉查询串与锚点，并保证以 `/` 开头，便于按白名单精确匹配。 */
    private fun normalizePath(raw: String): String {
        val trimmed = raw.substringBefore('?').substringBefore('#')
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    private fun writeResponse(
        writer: OutputStream,
        status: String,
        contentType: String,
        body: ByteArray,
        includeBody: Boolean,
    ) {
        writer.write(buildResponseHeader(status, contentType, body.size).toByteArray(Charsets.US_ASCII))
        if (includeBody && body.isNotEmpty()) writer.write(body)
        writer.flush()
    }

    private fun track(client: Socket) {
        clients.add(client)
        activeConnections.set(clients.size)
    }

    private fun release(client: Socket) {
        clients.remove(client)
        try {
            client.close()
        } catch (_: Throwable) {
        }
        activeConnections.set(clients.size)
        publishStats()
    }

    private fun closeAllClients() {
        clients.forEach { socket ->
            try {
                socket.close()
            } catch (_: Throwable) {
            }
        }
        clients.clear()
        activeConnections.set(0)
    }

    private fun resetCounters() {
        totalRequests.set(0)
        served200.set(0)
        notFound404.set(0)
        rejectedMethod.set(0)
        badRequests.set(0)
        handlerErrors.set(0)
        sessionEndMs.set(0L)
    }

    private fun publishStats() {
        val start = sessionStartMs.get()
        val running = _isRunning.value
        val uptime = when {
            start <= 0L -> 0L
            running -> SystemClock.elapsedRealtime() - start
            else -> (sessionEndMs.get() - start).coerceAtLeast(0L)
        }
        val total = totalRequests.get()
        _stats.value = WebServerStats(
            totalRequests = total,
            served200 = served200.get(),
            notFound404 = notFound404.get(),
            rejectedMethod = rejectedMethod.get(),
            badRequests = badRequests.get(),
            handlerErrors = handlerErrors.get(),
            activeConnections = activeConnections.get(),
            requestsPerMinute = if (uptime > 0L) total * 60_000f / uptime else 0f,
            uptimeMs = uptime,
            lastError = _error.value,
        )
    }

    private fun log(module: String, message: String) {
        val entry = JammerLogEntry(module = module, message = message)
        val snapshot: List<JammerLogEntry>
        synchronized(logLock) {
            if (logBuffer.size >= LOG_LIMIT) logBuffer.pollFirst()
            logBuffer.addLast(entry)
            snapshot = logBuffer.toList()
        }
        _log.value = snapshot
    }

    companion object {
        /** 默认监听端口。小于 1024 的端口在非 root 设备上无法绑定，故取 8080 */
        const val DEFAULT_PORT = 8080

        /** 允许绑定的端口下界（1013 以下需 CAP_NET_BIND_SERVICE） */
        const val PORT_MIN = 1024

        /** 允许绑定的端口上界 */
        const val PORT_MAX = 65535

        private const val ACCEPT_TIMEOUT_MS = 500
        private const val READ_TIMEOUT_MS = 5_000
        private const val MAX_REQUEST_LINE = 8 * 1024
        private const val MAX_HEADER_LINES = 64
        private const val BACKLOG = 32
        private const val LOG_LIMIT = 200

        // 以下响应相关常量对外公开：界面的"服务器信息"面板要用它们渲染真实的响应头，
        // 不能各自再抄一份字面量 —— 抄一份就意味着服务器改了 banner 或状态文案时，
        // 界面还会继续显示旧值。
        /** Server 头的内容 */
        const val SERVER_BANNER = "DeauthCtrl/1.0"
        /** 成功响应的 Content-Type */
        const val CONTENT_TYPE_HTML = "text/html; charset=UTF-8"

        /** 成功 */
        const val STATUS_200 = "200 OK"
        /** 请求行/头部格式错误 */
        const val STATUS_400 = "400 Bad Request"
        /** 路径不在白名单内（本服务器只服务 / 、/index.html、/index.htm） */
        const val STATUS_404 = "404 Not Found"
        /** 非 GET / HEAD 方法 */
        const val STATUS_405 = "405 Method Not Allowed"
        /** 请求行超长 */
        const val STATUS_414 = "414 URI Too Long"

        /**
         * 构造完整的响应头块
         *
         * [writeResponse] 与界面的「服务器信息」面板共用这一段，保证面板上展示的字段
         * 与线上真正发出的逐字节一致。放在伴生对象里，界面才能直接调用而不必持有实例。
         *
         * 界面原先手抄了一份，缺掉 `Content-Length` / `Cache-Control` / `Connection`
         * 三个字段，还把状态行写死成 `200 OK` —— 本服务器实际还会发出
         * 400 / 404 / 405 / 414，界面却在任何情况下都声称返回成功。
         *
         * @param status 状态行内容，取 [STATUS_200] 等常量
         * @param contentType Content-Type 值，通常为 [CONTENT_TYPE_HTML]
         * @param contentLength 响应体字节数
         * @return 含结尾空行的完整头部文本，换行为 CRLF
         */
        fun buildResponseHeader(status: String, contentType: String, contentLength: Int): String = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(contentLength).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("Server: ").append(SERVER_BANNER).append("\r\n")
            append("\r\n")
        }

        private val SERVED_PATHS = setOf("/", "/index.html", "/index.htm")

        private val BODY_400 = "<h1>400 Bad Request</h1>".toByteArray(Charsets.UTF_8)
        private val BODY_404 = "<h1>404 Not Found</h1>".toByteArray(Charsets.UTF_8)
        private val BODY_405 = "<h1>405 Method Not Allowed</h1>".toByteArray(Charsets.UTF_8)
        private val BODY_414 = "<h1>414 URI Too Long</h1>".toByteArray(Charsets.UTF_8)
    }
}
