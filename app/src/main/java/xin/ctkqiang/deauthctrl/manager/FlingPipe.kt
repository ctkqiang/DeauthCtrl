package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** fling 管道的当前工作模式 */
enum class FlingMode(val label: String) {
    IDLE("空闲"),
    RX("接收"),
    TX("发送"),
}

/** 日志级别 —— 只影响界面着色，不改变日志内容 */
enum class FlingLogLevel { INFO, OK, ERROR }

/** 一条 fling 日志 */
data class FlingLogEntry(
    val timeMs: Long,
    val text: String,
    val level: FlingLogLevel = FlingLogLevel.INFO,
)

/**
 * fling 管道状态 —— 由 [FlingPipe] 的 StateFlow 透传
 *
 * [mode] 空闲 / 接收 / 发送；[running] 是否有任务在运行；
 * [bytes] 本次传输已读写的字节数；[totalBytes] 发送端已知的总字节数（未知为 0）；
 * [ephemeralPort] 接收端请求端口 0 时由内核实际分配的端口；
 * [localPort] 接收端实际监听端口；[peer] 当前 / 最近一次连接的对端；
 * [previewText] 接收内容前 4 KB 的文本快照；[savedFile] 落盘文件路径（未开启为 null）。
 */
data class FlingState(
    val mode: FlingMode = FlingMode.IDLE,
    val running: Boolean = false,
    val bytes: Long = 0L,
    val totalBytes: Long = 0L,
    val log: List<FlingLogEntry> = emptyList(),
    val ephemeralPort: Int? = null,
    val localPort: Int? = null,
    val peer: String? = null,
    val previewText: String = "",
    val previewTruncated: Boolean = false,
    val savedFile: String? = null,
    val error: String? = null,
)

/**
 * fling 兼容裸 TCP 管道
 *
 * 语义对齐 rjek/fling 1.1-3（https://github.com/rjek/fling）：
 * 裸字节流、无长度前缀、无校验、无加密；连接关闭（EOF）即传输结束；
 * 一次一连接。发送端把文本或文件字节原样写入 Socket 后关闭，
 * 接收端 accept 后把流读入内存预览（前 4 KB）并按需写入私有目录文件。
 *
 * ## 线程模型
 * 所有阻塞 Socket 操作都在本类自己的 IO 协程作用域内执行：
 * 接收循环在一个协程里串行 accept（因此一次只处理一个连接），
 * 每次发送在独立协程里完成，进度与日志通过 StateFlow 推送。
 *
 * ## 端口
 * 接收端 port = 0 时由内核分配临时端口，实际端口见 [FlingState.localPort] /
 * [FlingState.ephemeralPort]，需要告知发送方。
 */
@Suppress("MissingPermission")
class FlingPipe(private val saveDir: File? = null) {

    private val _state = MutableStateFlow(FlingState())
    val state: StateFlow<FlingState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serverSocket: ServerSocket? = null
    private var receiveJob: Job? = null
    private var activeClient: Socket? = null

    /** 会话代号：停止 / 重新启动后旧协程的 finally 不会覆盖新会话状态 */
    @Volatile private var sessionId = 0

    /** 正在主动停止接收 —— 用于区分「用户停止」与「真实接收错误」 */
    @Volatile private var stopping = false

    // ─── 接收端 ─────────────────────────────────────────────────────────────

    /**
     * 启动接收端。
     *
     * @param port 监听端口；0 表示交给内核分配临时端口
     * @param saveToFile 是否把收到的完整字节写入应用私有目录（[saveDir]）
     */
    fun startReceive(port: Int = 0, saveToFile: Boolean = false) {
        if (_state.value.running) {
            appendLog("已有传输任务运行，无法启动接收", FlingLogLevel.ERROR)
            return
        }
        if (port !in 0..65535) {
            fail("端口无效：$port（接收端允许 0-65535）")
            return
        }

        stopping = false
        val session = ++sessionId

        receiveJob = scope.launch {
            val server = try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
            } catch (t: Throwable) {
                val message = "监听端口 $port 失败：${t.message ?: t.javaClass.simpleName}"
                _state.update { it.copy(mode = FlingMode.IDLE, running = false, error = message) }
                appendLog(message, FlingLogLevel.ERROR)
                return@launch
            }

            serverSocket = server
            _state.update {
                it.copy(
                    mode = FlingMode.RX,
                    running = true,
                    bytes = 0,
                    totalBytes = 0,
                    ephemeralPort = if (port == 0) server.localPort else null,
                    localPort = server.localPort,
                    peer = null,
                    previewText = "",
                    previewTruncated = false,
                    savedFile = null,
                    error = null,
                )
            }
            appendLog(
                "接收端已监听 0.0.0.0:${server.localPort}" +
                    if (port == 0) "（内核临时端口）" else "",
            )

            try {
                while (isActive) {
                    val client = try {
                        server.accept()
                    } catch (_: Throwable) {
                        break
                    }
                    if (!isActive) {
                        runCatching { client.close() }
                        break
                    }
                    handleClient(client, saveToFile)
                }
            } finally {
                runCatching { server.close() }
                if (serverSocket === server) serverSocket = null
                if (session == sessionId) {
                    _state.update { it.copy(mode = FlingMode.IDLE, running = false) }
                    appendLog("接收端已停止")
                }
            }
        }
    }

    /** 停止接收端；正在读取的连接会被关闭。发送任务运行时不干扰它。 */
    fun stopReceive() {
        val wasRunning = _state.value.running && _state.value.mode == FlingMode.RX
        if (!wasRunning) return
        // 保持 stopping=true 直到下一次启动，避免主动关闭连接被误报为接收错误
        stopping = true
        sessionId++
        runCatching { activeClient?.close() }
        activeClient = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        receiveJob?.cancel()
        receiveJob = null
        _state.update { it.copy(mode = FlingMode.IDLE, running = false) }
        appendLog("接收端已停止")
    }

    /**
     * 处理单个连接：裸读字节 → 预览前 4 KB → 按需写文件 → EOF 结束。
     */
    private fun handleClient(client: Socket, saveToFile: Boolean) {
        activeClient = client
        val peer = "${client.inetAddress?.hostAddress ?: "--"}:${client.port}"
        appendLog("连接建立：$peer")
        _state.update {
            it.copy(
                bytes = 0,
                peer = peer,
                previewText = "",
                previewTruncated = false,
                savedFile = null,
                error = null,
            )
        }

        var total = 0L
        val preview = ByteArrayOutputStream()
        var file: File? = null
        var output: FileOutputStream? = null
        var lastPublish = 0L

        try {
            if (saveToFile) {
                val dir = saveDir
                if (dir == null) {
                    appendLog("写文件不可用：未配置保存目录，本次仅保留内存预览", FlingLogLevel.ERROR)
                } else {
                    if (!dir.exists()) dir.mkdirs()
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    file = File(dir, "fling-$stamp.bin")
                    output = FileOutputStream(file)
                }
            }

            client.getInputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue

                    total += read
                    output?.write(buffer, 0, read)

                    if (preview.size() < PREVIEW_LIMIT) {
                        val remain = PREVIEW_LIMIT - preview.size()
                        preview.write(buffer, 0, minOf(read, remain))
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastPublish >= PROGRESS_INTERVAL_MS) {
                        lastPublish = now
                        publishReceive(total, preview, total > PREVIEW_LIMIT)
                    }
                }
            }

            output?.flush()
            _state.update {
                it.copy(
                    bytes = total,
                    totalBytes = total,
                    previewText = sanitize(String(preview.toByteArray(), Charsets.UTF_8)),
                    previewTruncated = total > PREVIEW_LIMIT,
                    savedFile = file?.absolutePath,
                    error = null,
                )
            }
            appendLog(
                "接收完成：$total 字节" +
                    if (file != null) " → ${file.absolutePath}" else "（仅内存预览，未落盘）",
                FlingLogLevel.OK,
            )
        } catch (t: Throwable) {
            if (stopping) {
                appendLog("接收已中止：$peer", FlingLogLevel.INFO)
            } else {
                val message = "接收中断：${t.message ?: t.javaClass.simpleName}"
                _state.update { it.copy(error = message) }
                appendLog(message, FlingLogLevel.ERROR)
            }
        } finally {
            runCatching { output?.close() }
            runCatching { client.close() }
            if (activeClient === client) activeClient = null
        }
    }

    /** 接收进度快照（每 100ms 最多一次，避免高频重组） */
    private fun publishReceive(total: Long, preview: ByteArrayOutputStream, truncated: Boolean) {
        _state.update {
            it.copy(
                bytes = total,
                previewText = sanitize(String(preview.toByteArray(), Charsets.UTF_8)),
                previewTruncated = truncated,
            )
        }
    }

    // ─── 发送端 ─────────────────────────────────────────────────────────────

    /** 发送一段文本（UTF-8 原样字节） */
    fun sendText(host: String, port: Int, text: String) {
        val data = text.toByteArray(Charsets.UTF_8)
        send(host, port, "文本 ${data.size} B", data.size.toLong()) {
            ByteArrayInputStream(data)
        }
    }

    /**
     * 发送任意字节流。
     *
     * [openStream] 在 IO 线程上调用，返回 null 视为打开失败。
     * 连接建立后按 8 KB 块原样写入，写完 flush 并关闭连接（即 EOF），
     * 不追加任何长度前缀或尾部标记。
     */
    fun send(
        host: String,
        port: Int,
        label: String,
        totalBytes: Long,
        openStream: () -> InputStream?,
    ) {
        val cleanHost = host.trim()
        when {
            _state.value.running -> {
                appendLog("已有传输任务运行，本次发送被拒绝", FlingLogLevel.ERROR)
                return
            }
            cleanHost.isEmpty() -> {
                fail("接收端 IP 为空")
                return
            }
            port !in 1..65535 -> {
                fail("端口无效：$port（发送端允许 1-65535）")
                return
            }
        }

        _state.update {
            it.copy(
                mode = FlingMode.TX,
                running = true,
                bytes = 0,
                totalBytes = totalBytes.coerceAtLeast(0L),
                peer = "$cleanHost:$port",
                previewText = "",
                previewTruncated = false,
                savedFile = null,
                error = null,
            )
        }
        appendLog("发送开始：$label → $cleanHost:$port")

        scope.launch {
            var sent = 0L
            try {
                val input = openStream() ?: throw IOException("无法打开待发送内容")
                input.use { stream ->
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(cleanHost, port), CONNECT_TIMEOUT_MS)
                        appendLog("已连接：$cleanHost:$port")
                        socket.getOutputStream().use { out ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var lastPublish = 0L
                            while (true) {
                                val read = stream.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue

                                out.write(buffer, 0, read)
                                sent += read

                                val now = System.currentTimeMillis()
                                if (now - lastPublish >= PROGRESS_INTERVAL_MS) {
                                    lastPublish = now
                                    _state.update { it.copy(bytes = sent) }
                                }
                            }
                            out.flush()
                        }
                    }
                }

                _state.update {
                    it.copy(
                        bytes = sent,
                        totalBytes = if (totalBytes > 0L) totalBytes else sent,
                        error = null,
                    )
                }
                appendLog("发送完成：$sent 字节 → $cleanHost:$port", FlingLogLevel.OK)
            } catch (t: Throwable) {
                val message = "发送失败：${t.message ?: t.javaClass.simpleName}"
                _state.update { it.copy(error = message) }
                appendLog(message, FlingLogLevel.ERROR)
            } finally {
                _state.update { it.copy(mode = FlingMode.IDLE, running = false) }
            }
        }
    }

    // ─── 公共辅助 ───────────────────────────────────────────────────────────

    /** 停止全部传输并释放协程作用域（ViewModel onCleared 调用） */
    fun destroy() {
        stopReceive()
        scope.cancel()
    }

    private fun fail(message: String) {
        _state.update { it.copy(error = message) }
        appendLog(message, FlingLogLevel.ERROR)
    }

    private fun appendLog(text: String, level: FlingLogLevel = FlingLogLevel.INFO) {
        val entry = FlingLogEntry(System.currentTimeMillis(), text, level)
        _state.update { it.copy(log = (it.log + entry).takeLast(MAX_LOG_LINES)) }
    }

    /** 把不可打印控制字符替换为 "·"，保留换行 / 回车 / 制表符 */
    private fun sanitize(text: String): String = buildString(text.length) {
        for (ch in text) {
            append(
                if (ch.isISOControl() && ch != '\n' && ch != '\r' && ch != '\t') '·' else ch,
            )
        }
    }

    companion object {
        /** 单次读写缓冲 8 KB */
        const val BUFFER_SIZE = 8192

        /** 内存中保留的接收预览上限 4 KB（界面要求显示前 4 KB） */
        const val PREVIEW_LIMIT = 4096

        /** 日志环形缓冲上限 */
        const val MAX_LOG_LINES = 200

        /** 连接超时 5 秒 */
        const val CONNECT_TIMEOUT_MS = 5000

        /** 进度推送节流 100ms */
        const val PROGRESS_INTERVAL_MS = 100L
    }
}
