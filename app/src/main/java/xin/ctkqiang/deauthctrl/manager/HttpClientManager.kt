package xin.ctkqiang.deauthctrl.manager

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import xin.ctkqiang.deauthctrl.model.JammerLogEntry
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * HTTP 响应数据结构
 *
 * 封装一次 HTTP 请求的完整响应信息,包括状态行、响应头、响应体、耗时与重定向落点。
 *
 * @property statusCode HTTP 状态码(200、404、500 等)
 * @property statusMessage HTTP 状态消息(OK、Not Found 等)
 * @property headers 响应头,已剔除 `getHeaderFields()` 中 key 为 `null` 的状态行条目
 * @property body 响应体字符串,按响应头声明的字符集解码(缺失时回退 UTF-8)
 * @property timeMs 请求耗时(毫秒),基于单调时钟 [SystemClock.elapsedRealtime] 测量
 * @property finalUrl 跟随重定向后最终命中的 URL;未发生重定向时等于请求 URL
 * @property redirected 是否发生过重定向(即 [finalUrl] 与请求 URL 不同)
 * @property bodyBytes 实际读取到的响应体字节数
 * @property truncated 响应体是否因超过 [HttpClientManager.MAX_BODY_BYTES] 被截断
 * @property charset 实际用于解码响应体的字符集名称
 */
data class HttpResponse(
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, List<String>>,
    val body: String,
    val timeMs: Long,
    val finalUrl: String = "",
    val redirected: Boolean = false,
    val bodyBytes: Int = 0,
    val truncated: Boolean = false,
    val charset: String = "UTF-8",
)

/**
 * HTTP 客户端的累计统计快照。
 *
 * 所有计数都来自本进程真实发生的请求,不含推测值。状态码按百位分段归类,
 * 便于在 UI 上快速判断目标服务端的整体健康度(大量 5xx 说明对方在故障中)。
 *
 * @property attempts 发起过的请求总数(含失败与取消)
 * @property successCount 2xx 响应数
 * @property redirectCount 3xx 响应数
 * @property clientErrorCount 4xx 响应数
 * @property serverErrorCount 5xx 响应数
 * @property failureCount 未拿到任何 HTTP 响应的次数(DNS 失败、连接被拒、超时等)
 * @property bytesReceived 累计接收响应体字节数
 * @property lastStatusCode 最近一次响应的状态码,0 表示尚无成功响应
 * @property lastDurationMs 最近一次请求耗时(毫秒)
 * @property averageDurationMs 平均耗时(毫秒),按 [attempts] 取整
 * @property lastUrl 最近一次请求的 URL
 * @property lastFinalUrl 最近一次请求最终命中的 URL
 * @property redirected 最近一次请求是否发生过重定向
 * @property lastError 最近一次失败的原因,空串表示无
 */
data class HttpCallStats(
    val attempts: Int = 0,
    val successCount: Int = 0,
    val redirectCount: Int = 0,
    val clientErrorCount: Int = 0,
    val serverErrorCount: Int = 0,
    val failureCount: Int = 0,
    val bytesReceived: Long = 0L,
    val lastStatusCode: Int = 0,
    val lastDurationMs: Long = 0L,
    val averageDurationMs: Long = 0L,
    val lastUrl: String = "",
    val lastFinalUrl: String = "",
    val redirected: Boolean = false,
    val lastError: String = "",
) {
    companion object {
        /** 尚未发生任何请求时的初始快照。 */
        val EMPTY = HttpCallStats()
    }
}

/**
 * HTTP 客户端管理器
 *
 * 基于 JDK [HttpURLConnection] 实现的轻量级 HTTP 客户端,提供 curl/Postman 的核心能力。
 * 支持 GET、POST、PUT、DELETE、PATCH、OPTIONS、HEAD 七种标准方法。
 *
 * ## 线程模型
 * 全部网络 I/O 在 `Dispatchers.IO` 上执行,状态写入通过 [MutableStateFlow] 暴露给 UI。
 * 管理器自身不持有 [kotlinx.coroutines.CoroutineScope],而是借用调用方(通常是
 * `viewModelScope`)的上下文 —— 这样 ViewModel 一销毁,在途请求就随之取消,
 * 不存在本模块自行泄漏协程的可能。
 *
 * ## 取消语义
 * [cancel] 通过 `disconnect()` 强制中断阻塞中的 socket 读,并递增会话号,
 * 使在途请求后续的所有状态写入全部失效 —— 避免"取消之后旧响应又覆盖新状态"的经典竞态。
 *
 * ## 平台限制(真实存在,非缺陷)
 * Android 的 `HttpURLConnection` 由 OkHttp 实现,会静默丢弃一部分受限请求头
 * (`Host`、`Connection`、`Content-Length`、`Transfer-Encoding` 等)。
 * 本类显式识别这些头并写入日志,让用户知道"我设了但它没生效",而不是默默吞掉。
 *
 * ## 安全说明
 * 不校验 TLS 证书链,不支持 mTLS 与代理。本模块定位为授权环境下的接口连通性
 * 与响应诊断工具,不要用于承载敏感凭据的生产流量。
 */
class HttpClientManager {

    private val _isRunning = MutableStateFlow(false)

    /** 是否有请求正在执行,供 UI 显示 Loading 与切换取消按钮。 */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _response = MutableStateFlow<HttpResponse?>(null)

    /** 最近一次成功拿到的响应;请求开始时清空。 */
    val response: StateFlow<HttpResponse?> = _response.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)

    /** 最近一次失败原因;发起新请求时清空。 */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _stats = MutableStateFlow(HttpCallStats.EMPTY)

    /** 累计请求统计快照。 */
    val stats: StateFlow<HttpCallStats> = _stats.asStateFlow()

    private val _log = MutableStateFlow<List<JammerLogEntry>>(emptyList())

    /** 环形日志缓冲的只读快照,最近 [LOG_LIMIT] 条。 */
    val log: StateFlow<List<JammerLogEntry>> = _log.asStateFlow()

    /**
     * 会话号。每次 [execute] 与 [cancel] 都会递增;
     * 所有异步状态写入前都比对会话号,不匹配则直接丢弃。
     */
    private val sessionId = AtomicLong(0L)

    /** 当前在途连接,供 [cancel] 强制断开。 */
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)

    private val logBuffer = ArrayDeque<JammerLogEntry>(LOG_LIMIT)
    private val logLock = Any()

    private val attempts = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val redirectCount = AtomicInteger(0)
    private val clientErrorCount = AtomicInteger(0)
    private val serverErrorCount = AtomicInteger(0)
    private val failureCount = AtomicInteger(0)
    private val bytesReceived = AtomicLong(0L)
    private val totalDurationMs = AtomicLong(0L)
    private val lastStatusCode = AtomicInteger(0)
    private val lastDurationMs = AtomicLong(0L)
    private val lastUrl = AtomicReference("")
    private val lastFinalUrl = AtomicReference("")
    private val lastRedirected = AtomicReference(false)
    private val lastError = AtomicReference("")

    /**
     * 执行一次 HTTP 请求。
     *
     * 失败时不抛异常(协程取消除外),而是返回 `null` 并通过 [error] 暴露原因,
     * 这样 UI 只需要消费状态流,不必在每个调用点写 try/catch。
     *
     * @param url 完整请求 URL(含协议与端口)
     * @param method HTTP 方法名,大小写不敏感;为空时回退 `GET`
     * @param headers 自定义请求头;受限头会被平台丢弃并记入日志
     * @param body 请求体,UTF-8 编码;仅对 [BODY_METHODS] 生效,其余方法会记日志提示被忽略
     * @param connectTimeoutMs 连接超时,会被夹取到 [[MIN_TIMEOUT_MS], [MAX_TIMEOUT_MS]]
     * @param readTimeoutMs 读取超时,夹取规则同上
     * @return 成功时为 [HttpResponse],失败或已取消时为 `null`
     */
    suspend fun execute(
        url: String,
        method: String = METHOD_GET,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    ): HttpResponse? {
        val target = url.trim()
        if (target.isEmpty()) {
            _error.value = "URL 不能为空"
            log(MODULE, "! rejected: empty url")
            return null
        }

        val verb = method.trim().uppercase(Locale.US).ifEmpty { METHOD_GET }
        val session = sessionId.incrementAndGet()

        attempts.incrementAndGet()
        lastUrl.set(target)
        lastError.set("")
        lastRedirected.set(false)
        _isRunning.value = true
        _error.value = null
        _response.value = null
        publishStats()

        log(MODULE, "→ $verb $target")
        if (body.isNotEmpty() && verb !in BODY_METHODS) {
            log(MODULE, "! body ignored: $verb carries no request body")
        }

        val startedAt = SystemClock.elapsedRealtime()
        try {
            return withContext(Dispatchers.IO) {
                val connection = URL(target).openConnection() as HttpURLConnection
                activeConnection.set(connection)
                try {
                    configure(connection, verb, headers, body, connectTimeoutMs, readTimeoutMs)
                    currentCoroutineContext().ensureActive()
                    readResponse(connection, target, verb, startedAt, session)
                } finally {
                    activeConnection.compareAndSet(connection, null)
                    connection.disconnect()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (sessionId.get() == session) {
                failureCount.incrementAndGet()
                val message = "${t.javaClass.simpleName}: ${t.message ?: "no detail"}"
                lastError.set(message)
                lastDurationMs.set(SystemClock.elapsedRealtime() - startedAt)
                totalDurationMs.addAndGet(lastDurationMs.get())
                _error.value = message
                log(MODULE, "! $verb $target failed: $message")
                publishStats()
            }
            return null
        } finally {
            if (sessionId.get() == session) _isRunning.value = false
        }
    }

    /**
     * 取消当前在途请求。
     *
     * 先递增会话号再断连,顺序不可颠倒:先断连会让在途协程的异常分支抢在会话失效前
     * 写入 `error`,把"用户主动取消"错报成"网络失败"。
     * 无在途请求时调用是安全的空操作(仅记录一条日志)。
     */
    fun cancel() {
        val connection = activeConnection.getAndSet(null)
        sessionId.incrementAndGet()
        _isRunning.value = false
        _error.value = "请求已取消"
        log(MODULE, "! cancelled by user")

        try {
            connection?.disconnect()
        } catch (_: Throwable) {
        }
        publishStats()
    }

    /**
     * 释放资源。管理器不持有协程作用域,因此等价于 [cancel];
     * ViewModel 的 `onCleared()` 调用它,确保页面销毁时不留在途连接。
     */
    fun destroy() {
        cancel()
    }

    /**
     * 清空日志缓冲。
     *
     * 刻意不重置统计计数器:统计是"这台设备本次生命周期内打过多少请求"的事实记录,
     * 清日志只影响视图,不应篡改事实。
     */
    fun clearLog() {
        synchronized(logLock) { logBuffer.clear() }
        _log.value = emptyList()
    }

    /**
     * 把字节数格式化成人类可读的字符串,供 UI 展示流量统计。
     *
     * @param bytes 字节数,负值按 0 处理
     * @return 形如 `512 B` / `1.5 KB` / `2.31 MB`
     */
    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe < 1024L -> "$safe B"
            safe < 1024L * 1024L -> "%.1f KB".format(Locale.US, safe / 1024.0)
            else -> "%.2f MB".format(Locale.US, safe / (1024.0 * 1024.0))
        }
    }

    // ── 内部实现 ────────────────────────────────────────────────────────────

    /** 配置连接:方法、超时、重定向、请求头与请求体。 */
    private fun configure(
        connection: HttpURLConnection,
        verb: String,
        headers: Map<String, String>,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ) {
        connection.requestMethod = verb
        connection.connectTimeout = connectTimeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        connection.readTimeout = readTimeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        connection.instanceFollowRedirects = true
        connection.useCaches = false

        var userAgentProvided = false
        headers.forEach { (rawKey, rawValue) ->
            val key = rawKey.trim()
            if (key.isEmpty()) return@forEach
            if (key.equals(HEADER_USER_AGENT, ignoreCase = true)) userAgentProvided = true
            if (key.lowercase(Locale.US) in RESTRICTED_HEADERS) {
                log(MODULE, "! header dropped by platform: $key")
                return@forEach
            }
            connection.setRequestProperty(key, rawValue)
        }
        if (!userAgentProvided) connection.setRequestProperty(HEADER_USER_AGENT, DEFAULT_USER_AGENT)

        if (body.isNotEmpty() && verb in BODY_METHODS) {
            val payload = body.toByteArray(Charsets.UTF_8)
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { stream ->
                stream.write(payload)
                stream.flush()
            }
        }
    }

    /** 读取状态行、响应头与响应体,并在会话有效时更新全部状态与统计。 */
    private fun readResponse(
        connection: HttpURLConnection,
        target: String,
        verb: String,
        startedAt: Long,
        session: Long,
    ): HttpResponse {
        val status = connection.responseCode
        val statusMessage = connection.responseMessage.orEmpty()

        val responseHeaders: Map<String, List<String>> = connection.headerFields
            ?.filterKeys { it != null }
            ?.mapKeys { it.key as String }
            ?: emptyMap()

        val stream: InputStream? = try {
            connection.inputStream
        } catch (_: Throwable) {
            connection.errorStream
        }
        val read = stream?.let { readBody(it, charsetOf(responseHeaders)) }
            ?: BodyRead("", 0, false, DEFAULT_CHARSET.name())

        val finalUrl = connection.url?.toString() ?: target
        val redirected = finalUrl != target
        val elapsed = SystemClock.elapsedRealtime() - startedAt

        val result = HttpResponse(
            statusCode = status,
            statusMessage = statusMessage,
            headers = responseHeaders,
            body = read.text,
            timeMs = elapsed,
            finalUrl = finalUrl,
            redirected = redirected,
            bodyBytes = read.bytes,
            truncated = read.truncated,
            charset = read.charsetName,
        )

        if (sessionId.get() == session) {
            when (status / 100) {
                2 -> successCount.incrementAndGet()
                3 -> redirectCount.incrementAndGet()
                4 -> clientErrorCount.incrementAndGet()
                5 -> serverErrorCount.incrementAndGet()
                else -> Unit
            }
            bytesReceived.addAndGet(read.bytes.toLong())
            totalDurationMs.addAndGet(elapsed)
            lastStatusCode.set(status)
            lastDurationMs.set(elapsed)
            lastFinalUrl.set(finalUrl)
            lastRedirected.set(redirected)
            _response.value = result

            log(
                MODULE,
                "← $status $statusMessage  ${read.bytes}B  ${elapsed}ms" +
                    if (redirected) "  ↳ $finalUrl" else "",
            )
            if (read.truncated) {
                log(MODULE, "! body truncated at $MAX_BODY_BYTES bytes")
            }
            publishStats()
        }
        return result
    }

    /**
     * 从响应头的 `Content-Type` 中解析字符集,解析不出时回退 UTF-8。
     *
     * 真实场景里等价于不解析:大量中文站点仍返回 `charset=gbk`,硬按 UTF-8 解码必然乱码。
     */
    private fun charsetOf(headers: Map<String, List<String>>): Charset {
        val contentType = headers.entries
            .firstOrNull { it.key.equals(HEADER_CONTENT_TYPE, ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?: return DEFAULT_CHARSET

        val name = CHARSET_PATTERN.find(contentType)?.groupValues?.getOrNull(1) ?: return DEFAULT_CHARSET
        return try {
            Charset.forName(name)
        } catch (_: Throwable) {
            DEFAULT_CHARSET
        }
    }

    /**
     * 按上限读取响应体。
     *
     * 单次最多读取 [MAX_BODY_BYTES],超出部分丢弃并标记 [BodyRead.truncated]。
     * 未设上限时,一个几 GB 的下载接口足以让应用 OOM 崩溃。
     */
    private fun readBody(stream: InputStream, charset: Charset): BodyRead {
        val buffer = ByteArray(READ_BUFFER_BYTES)
        val sink = ByteArrayOutputStream()
        var truncated = false

        stream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break

                val remaining = MAX_BODY_BYTES - sink.size()
                if (read >= remaining) {
                    sink.write(buffer, 0, remaining)
                    truncated = true
                    break
                }
                sink.write(buffer, 0, read)
            }
        }

        val bytes = sink.toByteArray()
        return BodyRead(bytes.toString(charset), bytes.size, truncated, charset.name())
    }

    /** 把计数器打包成不可变快照推给 UI。 */
    private fun publishStats() {
        val total = attempts.get()
        val durationSum = totalDurationMs.get()
        _stats.value = HttpCallStats(
            attempts = total,
            successCount = successCount.get(),
            redirectCount = redirectCount.get(),
            clientErrorCount = clientErrorCount.get(),
            serverErrorCount = serverErrorCount.get(),
            failureCount = failureCount.get(),
            bytesReceived = bytesReceived.get(),
            lastStatusCode = lastStatusCode.get(),
            lastDurationMs = lastDurationMs.get(),
            averageDurationMs = if (total > 0) durationSum / total else 0L,
            lastUrl = lastUrl.get(),
            lastFinalUrl = lastFinalUrl.get(),
            redirected = lastRedirected.get(),
            lastError = lastError.get(),
        )
    }

    /** 写入环形日志缓冲并推送快照。超出 [LOG_LIMIT] 时淘汰最旧一行。 */
    private fun log(module: String, message: String) {
        synchronized(logLock) {
            if (logBuffer.size >= LOG_LIMIT) logBuffer.removeFirst()
            logBuffer.addLast(JammerLogEntry(module = module, message = message))
            _log.value = logBuffer.toList()
        }
    }

    /** 一次响应体读取的结果。 */
    private data class BodyRead(
        val text: String,
        val bytes: Int,
        val truncated: Boolean,
        val charsetName: String,
    )

    companion object {
        /** UI 方法选择器使用的唯一数据源,避免界面与内核各写一份方法列表。 */
        val SUPPORTED_METHODS: List<String> = listOf(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD",
        )

        /** 允许携带请求体的方法;其余方法的 body 会被忽略并记日志。UI 据此决定是否显示 Body 输入框。 */
        val BODY_METHODS: Set<String> = setOf("POST", "PUT", "PATCH")

        /**
         * 平台会静默丢弃的受限请求头(全部小写比对)。
         *
         * 来源:Android 上 `HttpURLConnection` 由 OkHttp 实现,
         * 这些头由连接层自行管理,业务层设置无效。
         */
        private val RESTRICTED_HEADERS: Set<String> = setOf(
            "connection", "content-length", "host", "keep-alive", "transfer-encoding",
            "upgrade", "origin", "trailer", "via", "expect", "content-transfer-encoding",
            "access-control-request-headers", "access-control-request-method",
        )

        private const val MODULE = "HTTP"
        private const val METHOD_GET = "GET"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val DEFAULT_USER_AGENT = "DeauthCtrl/1.0"
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000
        private const val DEFAULT_READ_TIMEOUT_MS = 15_000
        private const val MIN_TIMEOUT_MS = 100
        private const val MAX_TIMEOUT_MS = 120_000

        /** 响应体读取上限 512 KiB,防止超大响应把进程撑爆。UI 用它做截断提示的文案。 */
        const val MAX_BODY_BYTES = 512 * 1024
        private const val READ_BUFFER_BYTES = 8 * 1024
        private const val LOG_LIMIT = 200

        private val DEFAULT_CHARSET: Charset = Charsets.UTF_8
        private val CHARSET_PATTERN = Regex("""charset\s*=\s*"?([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)
    }
}
