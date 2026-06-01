package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP 响应数据结构
 *
 * 封装一次 HTTP 请求的完整响应信息，包括状态行、响应头、响应体、耗时。
 *
 * @property statusCode HTTP 状态码（200、404、500 等）
 * @property statusMessage HTTP 状态消息（OK、Not Found 等）
 * @property headers 响应头，Map<String, List<String>> 格式（与 HttpURLConnection.getHeaderFields() 一致）
 * @property body 响应体字符串，UTF-8 解码
 * @property timeMs 请求耗时（毫秒），从连接建立到响应读取完毕的总时间
 */
data class HttpResponse(
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, List<String>>,
    val body: String,
    val timeMs: Long,
)

/**
 * HTTP 客户端管理器
 *
 * 基于 JDK HttpURLConnection 实现的轻量级 HTTP 客户端，模拟 curl/Postman 的核心功能。
 * 支持 GET、POST、PUT、DELETE、PATCH、OPTIONS、HEAD 等标准 HTTP 方法。
 *
 * ## 核心特性
 * - 自定义请求头（默认携带 User-Agent: DeauthCtrl/1.0）
 * - POST/PUT/PATCH 请求携带 JSON/表单请求体
 * - 自动跟随 HTTP 重定向（instanceFollowRedirects = true）
 * - 连接超时和读取超时均为 15 秒
 * - 自动读取错误响应体（4xx/5xx 时的 errorStream）
 * - 基于 Kotlin 协程，在 Dispatchers.IO 上异步执行网络 I/O
 *
 * ## 安全限制
 * - 不验证 SSL/TLS 证书（生产环境慎用）
 * - 不支持双向认证（mTLS）
 * - 不支持代理配置
 */
class HttpClientManager {

    /**
     * 执行 HTTP 请求
     *
     * 在 Dispatchers.IO 线程池中同步执行网络 I/O，通过 withContext 挂起等待结果。
     * 自动计算请求耗时（从 connect 到 body 读取完毕）。
     *
     * @param url 完整的请求 URL（含协议和端口），例如 "https://httpbin.org/post"
     * @param method HTTP 方法名，大小写不敏感（内部不做转换，直接传给 HttpURLConnection）
     * @param headers 自定义请求头，Key-Value 映射。不传 User-Agent 时默认添加 DeauthCtrl/1.0
     * @param body 请求体字符串，仅 POST/PUT/PATCH 方法会写入输出流
     * @return HttpResponse 包含状态码、响应头、响应体和总耗时
     * @throws Exception 网络连接失败、DNS 解析失败、超时等异常由调用方处理
     */
    suspend fun execute(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true

            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (!headers.containsKey("User-Agent")) conn.setRequestProperty("User-Agent", "DeauthCtrl/1.0")

            if (body.isNotEmpty() && method in listOf("POST", "PUT", "PATCH")) {
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream).use { it.write(body); it.flush() }
            }

            val status = conn.responseCode
            val statusMsg = conn.responseMessage ?: ""
            val respHeaders = conn.headerFields ?: emptyMap()

            val input = try { conn.inputStream } catch (_: Exception) { conn.errorStream }
            val respBody = input?.let { BufferedReader(InputStreamReader(it)).readText() } ?: ""
            val elapsed = System.currentTimeMillis() - start

            HttpResponse(status, statusMsg, respHeaders, respBody, elapsed)
        } finally {
            conn.disconnect()
        }
    }
}
