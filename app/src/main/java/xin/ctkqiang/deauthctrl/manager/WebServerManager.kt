package xin.ctkqiang.deauthctrl.manager

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * 轻量级 HTTP 服务器管理器
 *
 * 基于 JDK ServerSocket 实现的嵌入式 HTTP/1.1 服务器，无需任何第三方依赖。
 * 专为 Android 热点环境设计，可在手机热点的局域网内提供 Web 页面托管服务。
 *
 * ## 核心功能
 * - 监听指定端口（默认 80），接受 HTTP GET 请求
 * - 将内存中的 HTML 内容以 UTF-8 编码返回给客户端
 * - 支持多线程并发处理：每个客户端连接在独立线程中处理
 * - 正确的 HTTP/1.1 响应头构建（Content-Type、Content-Length、Server）
 * - 404 处理：非根路径请求返回 404 页面
 * - 优雅停止：关闭 ServerSocket 并终止所有连接
 *
 * ## 安全边界
 * - 仅响应 GET 请求，忽略 POST/PUT/DELETE 等其他方法
 * - 不执行任何服务端脚本，纯静态 HTML 文件托管
 * - 建议仅在受信任的局域网（手机热点）中使用
 * - 不实现 SSL/TLS，所有通信为明文 HTTP
 *
 * ## 线程模型
 * - 主线程：accept() 阻塞等待客户端连接
 * - 工作线程：每 accept 一个连接即 new Thread() 处理
 * - isRunning 标志由 @Volatile 修饰，确保跨线程可见性
 */
class WebServerManager {

    /** 服务端套接字，null 表示未启动 */
    private var serverSocket: ServerSocket? = null

    /**
     * 服务器运行状态标志
     *
     * @Volatile 确保 stop() 在任意线程调用后，工作线程能立即感知状态变更
     */
    @Volatile var isRunning = false
        private set

    /** 当前托管的 HTML 内容（UTF-8 字节数组，避免每次请求重复编码） */
    private var htmlContent: ByteArray = ByteArray(0)

    /** 监听端口号，默认为 HTTP 标准端口 80 */
    private var port: Int = 8080

    /**
     * 启动 HTTP 服务器
     *
     * 将 HTML 字符串转为 UTF-8 字节数组缓存，创建 ServerSocket 并进入 accept 循环。
     * 调用前会自动停止已有的服务器实例（如果正在运行）。
     *
     * @param html 要托管的 HTML 页面完整内容，UTF-8 编码
     * @param port 监听端口号，默认 80。注意：端口 < 1024 在非 root 设备上可能无法绑定
     * @return true 表示服务器已成功启动并开始监听
     */
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

    /**
     * 处理单个 HTTP 客户端请求
     *
     * 解析 HTTP 请求行，提取方法和路径：
     * - GET / 或 GET /index.html → 返回 200 OK + HTML 内容
     * - 其他路径 → 返回 404 Not Found + 提示页面
     * - 非 GET 方法 → 不响应（直接关闭连接）
     *
     * 响应头包含：
     * - Content-Type: text/html; charset=UTF-8
     * - Content-Length: 准确字节数
     * - Connection: close（短连接，每次请求后断开）
     * - Server: DeauthCtrl/1.0（自定义标识）
     *
     * @param client 已 accept 的客户端套接字
     */
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

    /**
     * 停止 HTTP 服务器
     *
     * 设置运行标志为 false，关闭 ServerSocket 以中断 accept() 阻塞。
     * 已在处理中的客户端连接会自然完成。线程安全，可从任意线程调用。
     */
    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
