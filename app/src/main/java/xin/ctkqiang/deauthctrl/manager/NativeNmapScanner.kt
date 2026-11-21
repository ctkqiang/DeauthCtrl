package xin.ctkqiang.deauthctrl.manager

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 原生 nmap 引擎 —— 移植 nmap 源码的三大内核,纯 Kotlin 实现:
 *
 * 1. **端口表**:[NmapPorts] 移植 nmap-services 的高频端口-服务映射
 * 2. **服务指纹**:[ServiceProbes] 端口 banner 与 nmap service-probes
 *    的版本探测正则(覆盖 SSH/HTTP/FTP/SMTP/MySQL/Redis 等主流服务)
 * 3. **时序模板**:-T0..-T5 映射为并发度/连接超时(与 nmap 语义一致)
 * 4. **输出格式**:Nmap 式 "PORT STATE SERVICE" 逐行输出
 *
 * 诚实边界:这是 nmap 算法层的原生移植;未包含 nmap 的 SYN 扫描
 * (无 root 时 Android 无 raw socket)、OS 指纹库与全部 600+ 服务
 * 签名 —— 覆盖 top 端口 + 主流服务指纹。
 */
class NativeNmapScanner {

    data class ScanResult(
        val port: Int,
        val state: String,      // open / closed / filtered
        val service: String,    // nmap-services 服务名
        val version: String = "", // 指纹版本(可为空)
    )

    /**
     * nmap 式扫描:top-N 端口并行 TCP connect,开放端口抓 banner 做
     * 服务指纹;逐行回调 "PORT STATE SERVICE VERSION"。
     */
    suspend fun scan(
        host: String,
        topN: Int,
        timing: Int, // 0..5
        onLine: (String) -> Unit,
        onPort: (ScanResult) -> Unit,
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        val concurrency = when (timing) {
            0, 1 -> 8
            2 -> 24
            3, 4 -> 64
            else -> 128
        }
        val connectTimeout = when (timing) {
            0 -> 8000
            1 -> 4000
            2 -> 3000
            else -> 2000
        }
        val bannerTimeout = 2500
        val ports = NmapPorts.topPorts(topN)
        onLine("Starting native nmap engine on $host ($topN ports, -T$timing, $concurrency workers)")
        val results = coroutineScope {
            val gate = Semaphore(concurrency)
            ports.map { (port, svc) ->
                async {
                    gate.withPermit {
                        val open = tcpConnect(host, port, connectTimeout)
                        if (!open) return@async null
                        onLine("Discovered open port $port/tcp")
                        val banner = grabBanner(host, port, bannerTimeout)
                        val version = ServiceProbes.match(port, banner)
                        val result = ScanResult(port, "open", svc, version)
                        onLine("${port}/tcp open $svc${if (version.isNotEmpty()) " $version" else ""}")
                        result
                    }
                }
            }.awaitAll().filterNotNull().sortedBy { it.port }
        }
        onLine("Nmap-style scan done: ${results.size} open port(s)")
        results
    }

    private fun tcpConnect(host: String, port: Int, timeoutMs: Int): Boolean = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    }.getOrDefault(false)

    /** 连接成功后读首行 banner(服务指纹用) */
    private fun grabBanner(host: String, port: Int, timeoutMs: Int): String = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = timeoutMs
            // 部分服务等客户端先开口(HTTP/SMTP 等主动问候,直接读即可)
            runCatching {
                BufferedReader(InputStreamReader(s.getInputStream())).use { it.readLine() }
            }.getOrNull() ?: ""
        }
    }.getOrDefault("")
}

/**
 * nmap service-probes 移植 —— 主流服务指纹(正则匹配 banner)
 */
object ServiceProbes {
    fun match(port: Int, banner: String): String {
        if (banner.isBlank()) return ""
        val b = banner.trim()
        return when (port) {
            22 -> regex(b, "SSH-([0-9.]+)[- ]?([^\\s]*)") { m -> "OpenSSH ${m.groupValues[1]}" }
            80, 8080, 8000, 8888, 8008, 8081, 3000, 5000 -> httpFingerprint(b)
            443, 8443 -> "https"
            21 -> regex(b, "220[- ](.*)") { m -> "ftp ${m.groupValues[1].take(40)}" }
            25, 587 -> regex(b, "220[- ](.*)") { m -> "smtp ${m.groupValues[1].take(40)}" }
            110 -> regex(b, "\\+OK.*") { "pop3" }
            143 -> regex(b, "\\* OK.*") { "imap" }
            3306 -> mysqlFingerprint(b)
            5432 -> "postgresql"
            6379 -> "redis"
            27017 -> "mongodb"
            554 -> regex(b, "RTSP/1.0 200.*Server: *([^\\r\\n]+)") { m -> "rtsp ${m.groupValues[1].take(30)}" }
            23 -> "telnet"
            445, 139 -> "smb"
            53 -> "domain"
            3389 -> "ms-wbt-server"
            119 -> regex(b, "200.*NNTP.*") { "nntp" }
            161 -> "snmp"
            else -> ""
        }
    }

    /** MySQL 握手版本 */
    private fun mysqlFingerprint(b: String): String {
        // 握手包第 6 字节起为版本串(二进制)—— 不可打印字符过滤
        val clean = b.filter { it.isLetterOrDigit() || it in "._- " }.trim()
        return if (clean.length > 3 && b.firstOrNull()?.code == 10) "mysql $clean" else "mysql"
    }

    /** HTTP Server 头指纹 */
    private fun httpFingerprint(b: String): String =
        Regex("(?i)server: *([^\\r\\n]+)").find(b)?.groupValues?.get(1)?.trim()?.take(60) ?: "http"

    private inline fun regex(input: String, pattern: String, crossinline out: (MatchResult) -> String): String =
        Regex(pattern, setOf(RegexOption.IGNORE_CASE)).find(input)?.let { runCatching { out(it) }.getOrDefault("") } ?: ""
}
