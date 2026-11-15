package xin.ctkqiang.deauthctrl.manager

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import xin.ctkqiang.deauthctrl.model.JammerLogEntry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * ARP 表条目数据类
 *
 * 表示局域网内一台已发现设备的网络信息。
 *
 * @property ip IPv4 地址（如 "192.168.1.105"）
 * @property mac MAC 地址（如 "D0:22:12:AB:CD:EF"）。
 *           仅当能读到 `/proc/net/arp` 时才有值；Android 10+ 对 `/proc/net` 的读取
 *           已受限，未 root 的设备上该字段大概率是空字符串，这是真实限制而非缺陷。
 * @property hostname DNS 反向解析得到的主机名（如 "galaxy-s24.local"），解析失败为空
 * @property latency 实测往返时延，形如 "12.4 ms"；未测到时为 [ArpScanManager.LATENCY_UNKNOWN]
 */
data class ArpEntry(
    val ip: String,
    val mac: String,
    val hostname: String,
    val latency: String,
)

/**
 * 一次 ARP 扫描会话的运行快照。
 *
 * 所有数字都来自本次扫描真实发生的计数，没有任何推测值：
 * - [subnet]           本次扫描的网段（形如 `192.168.1.0/24`），未开始为空
 * - [localIp]          本机参与扫描的 IPv4 地址
 * - [hostCount]        计划探测的主机数（/24 网段为 254）
 * - [hostsScanned]     已完成探测的主机数，随扫描推进递增，用于绘制进度
 * - [hostsReachable]   探测判定为在线的主机数
 * - [arpCacheHits]     从内核 ARP 缓存直接读到的条目数（未 root 时通常为 0）
 * - [hostnameResolved] 反向 DNS 成功解析出主机名的数量
 * - [deviceCount]      最终列表中的设备条目数（含仅来自 ARP 缓存的条目）
 * - [elapsedMs]        本次扫描耗时；扫描结束后冻结为最终值
 * - [lastError]        最近一次错误描述，供 UI 直接展示
 */
data class ArpScanStats(
    val subnet: String = "",
    val localIp: String = "",
    val hostCount: Int = 0,
    val hostsScanned: Int = 0,
    val hostsReachable: Int = 0,
    val arpCacheHits: Int = 0,
    val hostnameResolved: Int = 0,
    val deviceCount: Int = 0,
    val elapsedMs: Long = 0L,
    val lastError: String? = null,
)

/**
 * 局域网设备发现管理器（Ping Sweep + ARP 缓存）
 *
 * ## 它到底做什么
 * 这是一台**资产清点工具**：对本机所在 /24 网段逐台做可达性探测，把在线主机连同
 * MAC、主机名、往返时延列出来。它只读不写——不发送构造帧，不修改任何设备状态。
 *
 * ## 两级探测策略（真实可用性优先）
 * 1. **ICMP**：调用系统 `ping -c 1 -W 1`。Android 上 `/system/bin/ping` 自带
 *    `net_raw` 能力，普通应用无需 root 即可使用，这也是成功率最高的手段。
 * 2. **TCP 探活回退**：ICMP 被网络策略丢弃时，依次尝试 [TCP_FALLBACK_PORTS] 的
 *    连接。收到 `ConnectException`（RST）同样证明**主机在线**，只是该端口未监听，
 *    因此也判定为可达；只有超时/无路由才继续换端口。
 *
 * 之所以不用 `InetAddress.isReachable()` 作为回退：它在非 root 下会退化成对
 * TCP 7 端口（echo）的连接，而该端口在现代系统上几乎必然关闭，实际等于永远返回
 * false。因此这里显式做 TCP 探活，行为可预期。
 *
 * ## 能力边界（务必先读）
 * - **只做 ICMP/TCP 可达性 + ARP 缓存读取**，不做 SNMP、不做 NetBIOS、不做 mDNS
 * - **不做厂商识别**：OUI 到厂商的映射需要离线数据库，本模块不内置，MAC 只给原值
 * - **MAC 大概率拿不到**：`/proc/net/arp` 在 Android 10+ 受限，无 root 时返回空，
 *   界面上会显示 `(pending)`，这是系统限制而不是扫描失败
 * - **不伪装、不隐身**：Ping Sweep 是主动探测，目标主机与网络 IDS 都能看到
 * - 探测并发受 [MAX_CONCURRENT_PROBES] 约束，不是"无上限泛洪"
 *
 * ## 线程与生命周期模型
 * - 所有状态都是 `StateFlow`，由本类唯一持有，UI 层只订阅不修改
 * - 阻塞式探测跑在 IO 调度器上，并用信号量限制同时在途的探测数，
 *   避免 254 个 `ping` 进程同时拉起把设备的内存与文件描述符打满
 * - [stop] / [destroy] 会真正取消协程；会话号 [sessionId] 保证被取消的旧会话
 *   即使在 `finally` 中晚一步执行，也**无法**覆写新一轮扫描的状态
 * - 日志用 [ArrayDeque] 环形缓冲 + 锁，内存占用有硬上限
 */
class ArpScanManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _devices = MutableStateFlow<List<ArpEntry>>(emptyList())
    val devices: StateFlow<List<ArpEntry>> = _devices.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _stats = MutableStateFlow(ArpScanStats())
    val stats: StateFlow<ArpScanStats> = _stats.asStateFlow()

    private val _log = MutableStateFlow<List<JammerLogEntry>>(emptyList())
    val log: StateFlow<List<JammerLogEntry>> = _log.asStateFlow()

    /**
     * 会话号。每次 [scan] 递增，异步流程只允许写入自己的会话状态。
     * 没有它就会出现经典的竞态：`stop()` 后立刻 `scan()`，旧协程的 finally
     * 晚一步执行，把新一轮扫描的 `isRunning` 又置回 false。
     */
    private val sessionId = AtomicLong(0L)

    private val hostsScanned = AtomicInteger(0)
    private val hostsReachable = AtomicInteger(0)
    private val arpCacheHits = AtomicInteger(0)
    private val hostnameResolved = AtomicInteger(0)

    private val sessionStartMs = AtomicLong(0L)
    private val sessionEndMs = AtomicLong(0L)

    private val subnetLabel = MutableStateFlow("")
    private val localIpLabel = MutableStateFlow("")

    private val logBuffer = ArrayDeque<JammerLogEntry>(LOG_LIMIT)
    private val logLock = Any()

    /**
     * 开始一轮 /24 网段扫描。
     *
     * 会先 [stop] 掉上一轮，再同步完成前置检查（本机 IP、网段前缀）。
     * 前置检查失败时立即返回 `false` 并写入 [error]，不会启动空跑的协程。
     *
     * @return true 表示探测已开始；false 表示前置条件不满足，原因见 [error]
     */
    fun scan(): Boolean {
        stop()
        val session = sessionId.incrementAndGet()

        _devices.value = emptyList()
        _error.value = null
        resetCounters()
        sessionStartMs.set(SystemClock.elapsedRealtime())
        sessionEndMs.set(0L)

        val localIp = getLocalIp()
        if (localIp == null) {
            _isRunning.value = false
            _error.value = "未获取到局域网 IPv4 地址 — 请先连接 WiFi 或开启热点"
            localIpLabel.value = ""
            subnetLabel.value = ""
            log("ARP", "aborted: no site-local IPv4 address")
            publishStats()
            return false
        }

        val prefix = localIp.substringBeforeLast('.', missingDelimiterValue = "")
        if (prefix.isEmpty()) {
            _isRunning.value = false
            _error.value = "无法从 $localIp 推导网段前缀"
            log("ARP", "aborted: cannot derive subnet prefix from $localIp")
            publishStats()
            return false
        }

        localIpLabel.value = localIp
        subnetLabel.value = "$prefix.0/24"
        _isRunning.value = true
        log("ARP", "sweep start: $prefix.0/24 from $localIp, $HOST_COUNT hosts, concurrency=$MAX_CONCURRENT_PROBES")

        scanJob = scope.launch { runScan(prefix, session) }
        return true
    }

    /**
     * 停止扫描。
     *
     * 取消协程并冻结耗时统计。被取消的会话不会再写入任何状态。
     */
    fun stop() {
        val wasRunning = _isRunning.value
        _isRunning.value = false
        scanJob?.cancel()
        scanJob = null
        if (wasRunning) {
            if (sessionStartMs.get() > 0L) sessionEndMs.set(SystemClock.elapsedRealtime())
            publishStats()
        }
    }

    /** 释放协程作用域。ViewModel 销毁时调用，之后本实例不可复用。 */
    fun destroy() {
        stop()
        scope.cancel()
    }

    /**
     * 只清空日志缓冲区。
     *
     * 刻意**不**重置计数器：扫描进行中被清空日志时，统计面板必须继续反映真实值，
     * 否则界面会出现"扫描到一半数字突然归零"的错误观感。
     */
    fun clearLog() {
        synchronized(logLock) { logBuffer.clear() }
        _log.value = emptyList()
    }

    /** 把毫秒时长格式化为 `mm:ss.S`，用于展示扫描耗时。 */
    fun formatElapsed(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        return "%02d:%02d.%d".format(Locale.US, safe / 60_000, (safe % 60_000) / 1000, (safe % 1000) / 100)
    }

    // ── 内部实现 ────────────────────────────────────────────────────────────

    private suspend fun runScan(prefix: String, session: Long) {
        try {
            val arpCache = readArpCache()
            val found = ConcurrentHashMap<String, ArpEntry>()

            arpCache.forEach { (ip, mac) ->
                if (ip.startsWith("$prefix.")) found[ip] = ArpEntry(ip, mac, "", LATENCY_UNKNOWN)
            }
            arpCacheHits.set(found.size)
            if (found.isNotEmpty()) {
                publishDevices(found)
                log("ARP", "arp cache: ${found.size} entr${if (found.size == 1) "y" else "ies"} reused")
            } else {
                log("ARP", "arp cache empty or unreadable (no root on Android 10+ is expected)")
            }

            coroutineScope {
                val probeGate = Semaphore(MAX_CONCURRENT_PROBES)
                val dnsGate = Semaphore(MAX_CONCURRENT_REVERSE_DNS)

                val probes = (HOST_MIN..HOST_MAX).map { host ->
                    async {
                        if (!currentCoroutineContext().isActive) return@async
                        val ip = "$prefix.$host"

                        val result = probeGate.withPermit { probe(ip) }
                        hostsScanned.incrementAndGet()

                        if (!result.reachable) {
                            publishStats(session)
                            return@async
                        }

                        hostsReachable.incrementAndGet()
                        val mac = arpCache[ip] ?: ""
                        val hostname = dnsGate.withPermit {
                            withTimeoutOrNull(REVERSE_DNS_TIMEOUT_MS) { resolveHostname(ip) } ?: ""
                        }
                        if (hostname.isNotEmpty()) hostnameResolved.incrementAndGet()

                        val entry = ArpEntry(ip, mac, hostname, result.formatLatency())
                        found[ip] = entry
                        publishDevices(found, session)
                        log("ARP", "host up: $ip  mac=${mac.ifEmpty { "-" }}  host=${hostname.ifEmpty { "-" }}  rtt=${entry.latency}")
                        publishStats(session)
                    }
                }
                probes.awaitAll()
            }

            if (sessionId.get() == session) {
                val cachedOnly = found.values.count { it.latency == LATENCY_UNKNOWN }
                log(
                    "ARP",
                    "sweep done: reachable ${hostsReachable.get()}, cached-only $cachedOnly, " +
                        "hostnames ${hostnameResolved.get()}, scanned ${hostsScanned.get()}/${HOST_COUNT}",
                )
            }
        } catch (e: CancellationException) {
            log("ARP", "sweep cancelled by user after ${hostsScanned.get()}/${HOST_COUNT} hosts")
            throw e
        } catch (t: Throwable) {
            if (sessionId.get() == session) {
                _error.value = "扫描失败: ${t.javaClass.simpleName}"
                log("ARP", "sweep failed: ${t.javaClass.simpleName}")
            }
        } finally {
            if (sessionId.get() == session) {
                _isRunning.value = false
                if (sessionStartMs.get() > 0L) sessionEndMs.set(SystemClock.elapsedRealtime())
                if (_error.value == null && hostsReachable.get() == 0) {
                    _error.value = "未发现在线设备 — 请确认已连接 WiFi 且与目标处于同一网段"
                }
                publishStats(session)
            }
        }
    }

    /**
     * 单主机可达性探测：先 ICMP，失败再 TCP 探活。
     *
     * ICMP 阶段包了 [PING_HARD_TIMEOUT_MS] 超时。注意 `runPing` 是阻塞调用，
     * 超时只是让协程不再等待它，底层 ping 进程仍会在 `-W` 到期后自行退出，
     * 其返回值被丢弃——这是可接受的代价，换来的是扫描不会被单个异常主机拖死。
     */
    private suspend fun probe(ip: String): ProbeResult {
        val icmp = withTimeoutOrNull(PING_HARD_TIMEOUT_MS) { runPing(ip) }
        if (icmp != null && icmp.reachable) return icmp
        return runTcpProbe(ip)
    }

    /**
     * 调用系统 ping 做 ICMP 探测。
     *
     * 实现要点：合并 stderr，避免 ping 的警告输出写满管道缓冲导致进程永久阻塞；
     * 读完输出流后再 `waitFor()`，此时进程已退出，调用会立刻返回；
     * `finally` 中无条件 `destroy()`，任何异常路径都不会留下僵尸进程。
     */
    private fun runPing(ip: String): ProbeResult {
        val startedAt = SystemClock.elapsedRealtime()
        var process: Process? = null
        return try {
            process = ProcessBuilder(PING_BIN, "-c", "1", "-W", PING_TIMEOUT_SEC, ip)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()

            val received = RECEIVED_PATTERN.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            if (received <= 0) {
                ProbeResult(false, null)
            } else {
                val rtt = RTT_PATTERN.find(output)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                    ?: (SystemClock.elapsedRealtime() - startedAt).toDouble()
                ProbeResult(true, rtt)
            }
        } catch (_: Throwable) {
            ProbeResult(false, null)
        } finally {
            process?.destroy()
        }
    }

    /**
     * TCP 探活回退。
     *
     * 关键判断：`ConnectException` 代表对端回了 RST，**主机在线**，只是该端口没开，
     * 因此同样算可达。只有 `SocketTimeoutException` 或其它异常才认为该端口不通，
     * 继续尝试下一个端口。
     */
    private fun runTcpProbe(ip: String): ProbeResult {
        for (port in TCP_FALLBACK_PORTS) {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), TCP_PROBE_TIMEOUT_MS)
                }
                return ProbeResult(true, (SystemClock.elapsedRealtime() - startedAt).toDouble())
            } catch (_: ConnectException) {
                return ProbeResult(true, (SystemClock.elapsedRealtime() - startedAt).toDouble())
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Throwable) {
                continue
            }
        }
        return ProbeResult(false, null)
    }

    /** 反向 DNS 解析主机名；解析结果等于 IP 本身时视为失败，返回空串。 */
    private fun resolveHostname(ip: String): String = try {
        InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip } ?: ""
    } catch (_: Throwable) {
        ""
    }

    /**
     * 读取系统 ARP 缓存表。
     *
     * Android 10+ 对 `/proc/net` 的访问受限，此方法在未 root 的设备上通常返回空表，
     * 属于系统安全策略下的正常结果，调用方需要容忍。
     *
     * @return Map&lt;IP, MAC&gt;，已过滤 `00:00:00:00:00:00` 这类不完整条目
     */
    private fun readArpCache(): Map<String, String> {
        val cache = mutableMapOf<String, String>()
        try {
            ProcessBuilder(ARP_CACHE_CMD_READER, ARP_CACHE_PATH)
                .redirectErrorStream(true)
                .start()
                .inputStream
                .let(::InputStreamReader)
                .let(::BufferedReader)
                .use { reader ->
                    reader.readLine()
                    reader.forEachLine { line ->
                        val parts = line.trim().split(WHITESPACE)
                        if (parts.size >= 4 && parts[3] != INCOMPLETE_MAC) {
                            cache[parts[0]] = parts[3].uppercase(Locale.US)
                        }
                    }
                }
        } catch (_: Throwable) {
        }
        return cache
    }

    /**
     * 获取本机局域网 IPv4 地址。
     *
     * 跳过回环、未启用接口与 169.254.x.x 链路本地地址（APIPA，不可路由），
     * 并优先返回无线接口（`wlan*` / `ap*` / `swlan*`），因为热点场景下
     * 真正的扫描出口通常是无线网卡而不是蜂窝接口。
     */
    private fun getLocalIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val candidates = mutableListOf<Pair<String, String>>()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses?.toList().orEmpty()) {
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                    val host = addr.hostAddress ?: continue
                    if (host.startsWith(APIPA_PREFIX)) continue
                    candidates += iface.name.lowercase(Locale.US) to host
                }
            }
            val preferred = candidates.firstOrNull { (name, _) -> WIRELESS_IFACE_HINTS.any { name.contains(it) } }
            (preferred ?: candidates.firstOrNull())?.second
        } catch (_: Throwable) {
            null
        }
    }

    private fun publishDevices(found: Map<String, ArpEntry>, session: Long = sessionId.get()) {
        if (sessionId.get() != session) return
        _devices.value = found.values.sortedWith(DEVICE_ORDER)
    }

    private fun publishStats(session: Long = sessionId.get()) {
        if (sessionId.get() != session) return
        val start = sessionStartMs.get()
        val running = _isRunning.value
        val elapsed = when {
            start <= 0L -> 0L
            running -> SystemClock.elapsedRealtime() - start
            else -> (sessionEndMs.get() - start).coerceAtLeast(0L)
        }
        _stats.value = ArpScanStats(
            subnet = subnetLabel.value,
            localIp = localIpLabel.value,
            hostCount = if (subnetLabel.value.isEmpty()) 0 else HOST_COUNT,
            hostsScanned = hostsScanned.get(),
            hostsReachable = hostsReachable.get(),
            arpCacheHits = arpCacheHits.get(),
            hostnameResolved = hostnameResolved.get(),
            deviceCount = _devices.value.size,
            elapsedMs = elapsed,
            lastError = _error.value,
        )
    }

    private fun resetCounters() {
        hostsScanned.set(0)
        hostsReachable.set(0)
        arpCacheHits.set(0)
        hostnameResolved.set(0)
        _stats.value = ArpScanStats()
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

    /** 单主机探测结果。[latencyMs] 为 null 表示未测得往返时延。 */
    private data class ProbeResult(val reachable: Boolean, val latencyMs: Double?) {
        fun formatLatency(): String =
            latencyMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: LATENCY_UNKNOWN
    }

    companion object {
        /** 扫描主机的下界（跳过网络地址 `.0`） */
        const val HOST_MIN = 1

        /** 扫描主机的上界（跳过广播地址 `.255`） */
        const val HOST_MAX = 254

        /** /24 网段的可探测主机总数 */
        const val HOST_COUNT = HOST_MAX - HOST_MIN + 1

        /** 未测得往返时延时的占位符，UI 直接展示该字符串 */
        const val LATENCY_UNKNOWN = "—"

        private const val PING_BIN = "ping"
        private const val PING_TIMEOUT_SEC = "1"
        private const val PING_HARD_TIMEOUT_MS = 3_000L

        /** 同时在途的探测上限：254 个 ping 进程同时拉起会打满内存与文件描述符 */
        private const val MAX_CONCURRENT_PROBES = 32

        /** 同时在途的反向 DNS 上限：解析依赖外部 DNS，给太高的并发只会一起超时 */
        private const val MAX_CONCURRENT_REVERSE_DNS = 8
        private const val REVERSE_DNS_TIMEOUT_MS = 1_200L
        private const val TCP_PROBE_TIMEOUT_MS = 400

        /** TCP 探活候选端口：HTTP/HTTPS/SSH/ADB-over-WiFi/iOS 同步 */
        private val TCP_FALLBACK_PORTS = intArrayOf(80, 443, 22, 5555, 62078)

        private const val ARP_CACHE_CMD_READER = "cat"
        private const val ARP_CACHE_PATH = "/proc/net/arp"
        private const val INCOMPLETE_MAC = "00:00:00:00:00:00"
        private const val APIPA_PREFIX = "169.254."
        private val WIRELESS_IFACE_HINTS = listOf("wlan", "ap", "swlan", "wifi")
        private val WHITESPACE = Regex("\\s+")

        private const val LOG_LIMIT = 200

        private val RECEIVED_PATTERN = Regex("""(\d+)\s+(?:packets\s+)?received""")
        private val RTT_PATTERN = Regex("""time[=<]\s*([\d.]+)\s*ms""")

        /** 同一 /24 网段内按第四段数值升序：.2 排在 .100 之前 */
        private val DEVICE_ORDER = compareBy<ArpEntry> { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
    }
}
