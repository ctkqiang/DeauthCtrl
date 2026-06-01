package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 端口扫描结果
 *
 * 表示一次 TCP 端口探测的完整结果。
 *
 * @property port 端口号（1-65535）
 * @property state 端口状态："open"（连接成功）或 "closed"（连接被拒绝/超时）
 * @property service 识别出的服务名（基于 IANA 常见端口映射表），如 "http"、"ssh"、"mysql"
 * @property banner 服务端 Banner 信息（连接后前 60 字符），用于识别服务版本。空字符串表示未获取到 Banner
 */
data class PortResult(
    val port: Int,
    val state: String,
    val service: String,
    val banner: String = "",
)

/**
 * TCP 端口扫描管理器（Nmap 风格）
 *
 * 通过 TCP Connect 方式对目标主机的常见端口进行扫描，模拟 `nmap -sT` 的基本功能。
 * 支持服务识别（基于端口号映射）和 Banner 抓取（连接后读取服务欢迎信息）。
 *
 * ## 实现原理
 * - TCP Connect Scan：对每个端口创建 Socket 并尝试 connect()，成功即为 "open"
 * - 服务识别：基于 IANA 常见端口映射表（80→http、22→ssh 等 40+ 常见端口）
 * - Banner 抓取：连接成功后设置 500ms 读取超时，尝试从输入流读取服务 Banner
 * - 串行扫描：逐端口依次探测（避免触发目标主机防火墙/IDS 的并发扫描检测）
 * - 800ms 连接超时：在可用性和扫描速度之间平衡
 *
 * ## 扫描端口列表
 * 包括 21(f tp)、22(ssh)、23(telnet)、25(smtp)、53(dns)、80(http)、110(pop3)、
 * 135(rpc)、139/445(SMB)、143(imap)、443(https)、3306(mysql)、3389(rdp)、
 * 5432(postgres)、5900(vnc)、6379(redis)、8080(proxy)、8443(https-alt)、
 * 27017(mongo)、9200(elasticsearch) 等 40+ 常见端口。
 *
 * ## 使用限制
 * - 非 root 设备无法发送原始 TCP SYN 包（使用 connect 扫描，端口状态记录在系统日志中）
 * - 扫描速度受限于串行执行（800ms/端口 × 40端口 ≈ 32秒）
 * - 防火墙可能丢弃 RST 包导致超时误报 "closed"
 * - Banner 获取依赖目标服务在连接后主动发送欢迎信息
 */
class PortScannerManager {

    /**
     * IANA 常见端口 → 服务名映射表
     *
     * 用于快速识别开放端口上运行的服务类型。
     */
    private val commonPorts = mapOf(
        21 to "ftp", 22 to "ssh", 23 to "telnet", 25 to "smtp", 53 to "domain",
        80 to "http", 110 to "pop3", 111 to "rpcbind", 135 to "msrpc", 139 to "netbios-ssn",
        143 to "imap", 443 to "https", 445 to "microsoft-ds", 993 to "imaps", 995 to "pop3s",
        1723 to "pptp", 3306 to "mysql", 3389 to "ms-wbt-server", 5432 to "postgresql",
        5900 to "vnc", 6379 to "redis", 8080 to "http-proxy", 8443 to "https-alt",
        27017 to "mongodb", 5000 to "upnp", 5222 to "xmpp", 4444 to "meterpreter",
        7547 to "cwmp", 8888 to "http-alt", 9000 to "php-fpm", 9090 to "cassandra",
        9200 to "elasticsearch", 11211 to "memcached", 27015 to "steam",
    )

    /** 扫描运行状态 */
    @Volatile var isRunning = false
        private set

    /** 当前扫描协程 */
    private var job: Job? = null

    /**
     * 执行端口扫描
     *
     * 按顺序扫描 ports 列表中的每个端口，每次扫描完成后通过回调实时通知 UI。
     *
     * @param host 目标主机 IP 或域名
     * @param ports 要扫描的端口列表，默认使用 commonPorts 的 KeySet（40+ 常见端口）
     * @param timeoutMs 每个端口的连接超时时间（毫秒），默认 800ms
     * @param onResult 单个端口扫描完成时的回调（主线程）
     * @param onProgress 进度回调（主线程），参数为 (已扫描数, 总数)
     * @param onComplete 全部扫描完成时的回调（主线程）
     */
    fun scan(
        host: String,
        ports: List<Int> = commonPorts.keys.toList(),
        timeoutMs: Int = 800,
        onResult: (PortResult) -> Unit,
        onProgress: (Int, Int) -> Unit,
        onComplete: () -> Unit,
    ) {
        stop()
        isRunning = true
        var scanned = 0
        val total = ports.size

        job = CoroutineScope(Dispatchers.IO).launch {
            for (port in ports) {
                if (!isRunning) break
                val result = checkPort(host, port, timeoutMs)
                scanned++
                withContext(Dispatchers.Main) {
                    onResult(result)
                    onProgress(scanned, total)
                }
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    /**
     * 探测单个 TCP 端口
     *
     * 创建 Socket 并尝试连接目标主机的指定端口：
     * - connect() 成功 = 端口开放 → 尝试读取 Banner
     * - connect() 失败/超时 = 端口关闭或不可达
     *
     * Banner 抓取：
     * - 连接后设置 soTimeout = 500ms 的读取超时
     * - 读取最多 256 字节的服务端响应
     * - 去除换行符并截取前 60 个字符（适应 UI 显示）
     *
     * @param host 目标主机
     * @param port 端口号
     * @param timeout 连接超时（毫秒）
     * @return PortResult 包含端口状态、服务名和 Banner
     */
    private fun checkPort(host: String, port: Int, timeout: Int): PortResult {
        val service = commonPorts[port] ?: "unknown"
        return try {
            val sock = Socket()
            sock.connect(InetSocketAddress(host, port), timeout)
            val state = "open"
            var banner = ""
            try {
                sock.soTimeout = 500
                val input = sock.getInputStream()
                val buf = ByteArray(256)
                val n = input.read(buf)
                if (n > 0) banner = String(buf, 0, n).trim().replace("\n", " ").replace("\r", "").take(60)
            } catch (_: Exception) {}
            sock.close()
            PortResult(port, state, service, banner)
        } catch (_: Exception) {
            PortResult(port, "closed", service)
        }
    }

    /**
     * 停止扫描
     *
     * 取消协程，设置运行标志为 false。已建立的连接会自然超时关闭。
     */
    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }
}
