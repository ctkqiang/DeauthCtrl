package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * 一台响应 NBSTAT 的主机
 *
 * @property ip     目标 IPv4 地址(来自本轮扫描的目标列表,不是报文内容)
 * @property mac    MAC 地址;优先取自 NBSTAT 响应末尾的节点统计区(Unit ID),
 *                  缺失时尝试读取系统 ARP 缓存;两者都读不到时为空串,界面显示 "--"
 * @property names  过滤后的名称表,每项 = (类型语义, 名字),
 *                  例如 ("<00> 工作站", "DESKTOP-7Q2K")。只保留 <00>/<03>/<20>/<1D>
 * @property rawHex 本次响应的完整原始报文(大写十六进制),供人工复核
 */
data class NetBiosHost(
    val ip: String,
    val mac: String,
    val names: List<Pair<String, String>>,
    val rawHex: String,
) {
    /** 工作站名 —— 类型 <00> */
    val hostName: String? get() = nameOf("<00>")

    /** 信使 / 登录用户 —— 类型 <03> */
    val userName: String? get() = nameOf("<03>")

    /** 文件服务名 —— 类型 <20> */
    val fileService: String? get() = nameOf("<20>")

    /** 主浏览器名 —— 类型 <1D> */
    val masterBrowser: String? get() = nameOf("<1D>")

    /** OUI 厂商;地址缺失或表外时分别为 "--" / "未知厂商" */
    val vendor: String get() = if (mac.isBlank()) "--" else NbtScanManager.vendorOf(mac)

    private fun nameOf(semanticPrefix: String): String? =
        names.firstOrNull { it.first.startsWith(semanticPrefix) }
            ?.second
            ?.takeIf { it.isNotBlank() }
}

/**
 * NetBIOS 名称扫描页面状态
 *
 * @property scanning 本轮扫描是否仍在进行
 * @property progress 已完成的目标数(含无响应目标)
 * @property total    本轮目标总数
 * @property hosts    已响应的主机,按 IP 升序
 * @property target   本轮实际扫描范围(网段收敛后的人类可读标签)
 * @property note     目标收敛等附加说明;无则为 null
 * @property error    最近一次错误(中文),新一轮扫描开始时清空
 */
data class NetBiosState(
    val scanning: Boolean = false,
    val progress: Int = 0,
    val total: Int = 0,
    val hosts: List<NetBiosHost> = emptyList(),
    val target: String = "",
    val note: String? = null,
    val error: String? = null,
)

/** 目标格式无效时的统一错误文案 */
private const val INVALID_TARGET =
    "目标格式无效 —— 请输入单个 IPv4(如 192.168.1.10)或 CIDR 网段(如 192.168.1.0/24)"

/** 设备没有可用的局域网 IPv4 地址 */
private const val NO_LAN_ADDRESS =
    "未检测到局域网 IPv4 地址 —— 请先连接 Wi-Fi 或热点后重试"

/**
 * NetBIOS 名称扫描 —— nbtscan 的 Kotlin 等价实现
 *
 * ## 协议
 * 对每个目标向 UDP 137 发送一个 50 字节的 NBSTAT 查询:
 * - 12 字节 NBNS 头:随机事务 ID、flags 0x0110、QDCOUNT 1、其余计数 0;
 * - 34 字节 QNAME:0x20(长度)+ "CK"('*' 的一级编码)+ 30 × 0x41(15 个 0x00)+ 0x00;
 * - QTYPE 0x0021(NBSTAT)、QCLASS 0x0001(IN)。
 *
 * 响应从 12 字节头之后开始:压缩名(通常 0xC00C)+ TYPE/CLASS/TTL/RDLENGTH(10 字节),
 * RDATA 首字节是名字数量,随后每个名字 18 字节(15 字节名字 + 1 字节类型 + 2 字节 flags);
 * 名字表之后若仍有字节,则是节点统计区,前 6 字节即该机 MAC。
 * 只提取 <00>(工作站)/ <03>(信使/用户)/ <20>(文件服务)/ <1D>(主浏览器)四类。
 *
 * ## 扫描与并发
 * - 单个 IP 只查询该地址;CIDR 的 /25-/32 按实际主机范围枚举;
 * - /24 及更大的网段收敛为输入地址所在的 /24,扫描 1-254(界面用 note 明示);
 * - 每个目标 200 ms 超时(从发出查询到收到首个合法响应);
 * - 固定 8 线程的线程池,一次最多 8 个目标在途。
 *
 * ## 诚实边界
 * - 只有运行 Windows / 启用 NetBIOS over TCP/IP 的设备会响应 UDP 137;
 *   现代 Windows 10/11 默认可能关闭 NetBIOS,所以结果少、甚至为零都是正常现象;
 * - 防火墙可能直接丢弃 137/udp,扫描不到不代表主机不存在;
 * - MAC 只来自响应统计区或系统 ARP 缓存,两者都没有时如实留空(界面显示 "--");
 * - 厂商识别使用内置的常用 OUI 前 3 字节表,它是小型离线子集而非权威数据库,
 *   未收录地址显示「未知厂商」;
 * - 所有结果都来自真实报文,不做任何模拟、补值或推断。
 */
class NbtScanManager {

    private val _state = MutableStateFlow(NetBiosState())
    val state: StateFlow<NetBiosState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 固定 8 线程的探测线程池 —— 与 nbtscan 的并发语义一致 */
    private val pool: ExecutorService = Executors.newFixedThreadPool(MAX_CONCURRENCY) { runnable ->
        Thread(runnable, "nbt-scan").apply { isDaemon = true }
    }
    private val probeDispatcher: ExecutorCoroutineDispatcher = pool.asCoroutineDispatcher()

    /** 会话号:每次 start / stop 递增,旧会话的迟到结果一律丢弃 */
    @Volatile
    private var session = 0

    private var scanJob: Job? = null

    /**
     * 启动一轮扫描(会取消上一轮)。
     *
     * @return false 表示目标不合法或本机没有局域网地址,原因在 [state.error]
     */
    fun start(target: String): Boolean {
        // 先取消上一轮,再校验新目标 —— 校验失败时旧扫描不能继续在后台跑
        scanJob?.cancel()
        scanJob = null
        val current = ++session

        val plan = buildPlan(target)
        if (plan == null) {
            _state.value = NetBiosState(error = INVALID_TARGET)
            return false
        }
        if (!hasLanIpv4()) {
            _state.value = NetBiosState(error = NO_LAN_ADDRESS)
            return false
        }

        _state.value = NetBiosState(
            scanning = true,
            progress = 0,
            total = plan.ips.size,
            hosts = emptyList(),
            target = plan.label,
            note = plan.note,
        )
        scanJob = scope.launch { runScan(plan, current) }
        return true
    }

    /** 停止扫描 —— 已完成部分的结果保留,不再写入任何迟到数据 */
    fun stop() {
        session++
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(scanning = false) }
    }

    /** ViewModel 销毁时调用:停止扫描并释放线程池 */
    fun destroy() {
        stop()
        scope.cancel()
        runCatching { probeDispatcher.close() }
        runCatching { pool.shutdownNow() }
    }

    // ── 扫描主流程 ────────────────────────────────────────────────────────────

    private suspend fun runScan(plan: TargetPlan, ownSession: Int) {
        val found = ConcurrentHashMap<String, NetBiosHost>()
        val finished = AtomicInteger(0)

        try {
            coroutineScope {
                plan.ips.forEach { ip ->
                    // 固定线程池 + 阻塞式 UDP 探测:8 个线程天然限流
                    launch(probeDispatcher) {
                        val host = runCatching { queryHost(ip) }.getOrNull()
                        if (session != ownSession) return@launch
                        if (host != null) found[ip] = host
                        val done = finished.incrementAndGet()
                        _state.update { current ->
                            if (!current.scanning || session != ownSession) {
                                current
                            } else {
                                current.copy(progress = done, hosts = sortHosts(found.values))
                            }
                        }
                    }
                }
            }

            if (session != ownSession) return

            // 响应没有统计区时,MAC 只能尝试系统 ARP 缓存(Android 10+ 通常不可读)
            val arp = readArpCache()
            if (arp.isNotEmpty()) {
                for ((ip, host) in found) {
                    if (host.mac.isNotBlank()) continue
                    val mac = arp[ip] ?: continue
                    found[ip] = host.copy(mac = mac)
                }
            }

            _state.update { current ->
                if (session != ownSession) {
                    current
                } else {
                    current.copy(
                        scanning = false,
                        progress = plan.ips.size,
                        hosts = sortHosts(found.values),
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (session == ownSession) {
                _state.update {
                    it.copy(
                        scanning = false,
                        error = "扫描失败: ${t.message ?: t.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    /**
     * 查询单个目标。
     *
     * 每个目标使用独立的临时 UDP 端口和随机事务 ID;超时从查询发出时开始计算,
     * 收到事务 ID 不匹配 / 非响应报文会继续等待剩余时间,而不是把它当成结果。
     */
    @Suppress("MissingPermission")
    private fun queryHost(ip: String): NetBiosHost? {
        val transactionId = Random.nextInt(0x10000)
        val query = buildQuery(transactionId)
        val address = InetAddress.getByName(ip)

        DatagramSocket().use { socket ->
            socket.soTimeout = TARGET_TIMEOUT_MS.toInt()
            socket.send(DatagramPacket(query, query.size, address, NBT_PORT))

            val buffer = ByteArray(MAX_RESPONSE_BYTES)
            val deadline = System.currentTimeMillis() + TARGET_TIMEOUT_MS
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) return null
                socket.soTimeout = remaining.toInt().coerceAtLeast(1)

                val response = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(response)
                } catch (_: SocketTimeoutException) {
                    return null
                }

                val parsed = parseResponse(buffer, response.length, transactionId) ?: continue
                return NetBiosHost(
                    ip = ip,
                    mac = parsed.mac.orEmpty(),
                    names = parsed.names,
                    rawHex = toHex(buffer, response.length),
                )
            }
        }
        return null
    }

    // ── NBSTAT 报文 ────────────────────────────────────────────────────────────

    /** 构造 50 字节 NBSTAT 查询 */
    private fun buildQuery(transactionId: Int): ByteArray {
        val packet = ByteArray(QUERY_SIZE)
        packet[0] = (transactionId ushr 8).toByte()
        packet[1] = transactionId.toByte()
        packet[2] = 0x01
        packet[3] = 0x10                       // flags: RD = 1
        packet[5] = 0x01                       // QDCOUNT = 1
        // ANCOUNT / NSCOUNT / ARCOUNT 保持 0
        packet[12] = 0x20                      // 编码名长度 = 32
        packet[13] = 'C'.code.toByte()         // '*' (0x2A) 的高半字节 2 -> 'C'
        packet[14] = 'K'.code.toByte()         // '*' 的低半字节 A -> 'K'
        for (i in 0 until 30) {
            packet[15 + i] = 'A'.code.toByte() // 其余 15 个名称字节编码为 0x00 -> "AA"
        }
        packet[45] = 0x00                      // 名称结束符
        packet[47] = 0x21                      // QTYPE = NBSTAT(0x0021)
        packet[49] = 0x01                      // QCLASS = IN(0x0001)
        return packet
    }

    /** 解析 NBSTAT 响应;结构不完整或不是 NBSTAT 答案时返回 null */
    private fun parseResponse(
        buffer: ByteArray,
        length: Int,
        expectedTransactionId: Int,
    ): ParsedNbt? {
        if (length < DNS_HEADER_SIZE) return null
        if (u16(buffer, 0) != expectedTransactionId) return null       // 事务 ID 必须匹配
        val flags = u16(buffer, 2)
        if ((flags and 0x8000) == 0) return null                       // QR = 0 是查询
        if (u16(buffer, 6) == 0) return null                           // ANCOUNT = 0

        var offset = skipDnsName(buffer, DNS_HEADER_SIZE, length) ?: return null
        if (offset + RR_HEADER_SIZE > length) return null
        val rrType = u16(buffer, offset)
        val rrClass = u16(buffer, offset + 2)
        val rdLength = u16(buffer, offset + 8)
        offset += RR_HEADER_SIZE

        if (rrType != TYPE_NBSTAT || rrClass != CLASS_IN) return null
        if (rdLength < 1 || offset + rdLength > length) return null

        val end = offset + rdLength
        val nameCount = buffer[offset].toInt() and 0xFF
        var cursor = offset + 1
        val names = ArrayList<Pair<String, String>>(nameCount)

        for (index in 0 until nameCount) {
            if (cursor + NAME_ENTRY_SIZE > end) break
            val name = decodeName(buffer, cursor)
            val typeByte = buffer[cursor + NAME_LENGTH].toInt() and 0xFF
            nameSemantics(typeByte)?.let { semantics ->
                if (name.isNotBlank()) names += semantics to name
            }
            cursor += NAME_ENTRY_SIZE
        }

        // 名字表之后是节点统计区,开头 6 字节为 Unit ID(MAC);没有统计区则为 null
        val mac = if (end - cursor >= MAC_SIZE) macFrom(buffer, cursor) else null
        return ParsedNbt(names = names, mac = mac)
    }

    /** 跳过 DNS 名称(标签序列或 2 字节压缩指针),返回其后偏移 */
    private fun skipDnsName(buffer: ByteArray, start: Int, length: Int): Int? {
        var offset = start
        while (offset < length) {
            val label = buffer[offset].toInt() and 0xFF
            if (label == 0x00) return offset + 1
            if ((label and 0xC0) == 0xC0) {
                return if (offset + 2 <= length) offset + 2 else null
            }
            offset += 1 + label
        }
        return null
    }

    /** 15 字节 NetBIOS 名称 → 可读字符串(去尾部空格与填充的 0x00) */
    private fun decodeName(buffer: ByteArray, offset: Int): String {
        var end = offset + NAME_LENGTH
        for (i in offset until offset + NAME_LENGTH) {
            if (buffer[i].toInt() == 0) {
                end = i
                break
            }
        }
        return String(buffer, offset, end - offset, Charsets.ISO_8859_1)
            .trim { it <= ' ' }
    }

    /** 名称类型字节 → 筛选用的类型语义;不在四类之内返回 null */
    private fun nameSemantics(typeByte: Int): String? = when (typeByte) {
        0x00 -> "<00> 工作站"
        0x03 -> "<03> 信使/用户"
        0x20 -> "<20> 文件服务"
        0x1D -> "<1D> 主浏览器"
        else -> null
    }

    /** 统计区前 6 字节 → MAC;全零或多播地址视为无效 */
    private fun macFrom(buffer: ByteArray, offset: Int): String? {
        if (offset + MAC_SIZE > buffer.size) return null
        val octets = IntArray(MAC_SIZE) { buffer[offset + it].toInt() and 0xFF }
        if (octets.all { it == 0 }) return null
        if ((octets[0] and 0x01) != 0) return null
        return octets.joinToString(":") { String.format(Locale.US, "%02X", it) }
    }

    private fun u16(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or
            (buffer[offset + 1].toInt() and 0xFF)

    private fun toHex(buffer: ByteArray, length: Int): String {
        val builder = StringBuilder(length * 2)
        for (i in 0 until length) {
            val value = buffer[i].toInt() and 0xFF
            builder.append(HEX_DIGITS[value ushr 4])
            builder.append(HEX_DIGITS[value and 0x0F])
        }
        return builder.toString()
    }

    // ── 目标解析 ──────────────────────────────────────────────────────────────

    /** 一轮扫描的目标计划 */
    private data class TargetPlan(
        val ips: List<String>,
        val label: String,
        val note: String?,
    )

    /**
     * 解析目标输入。
     *
     * - 单个 IPv4 → 只扫描该地址;
     * - /25 - /32 → 按实际主机范围枚举(/31、/32 不排除端点);
     * - /24 及更大 → 收敛为输入地址所在的 /24,扫描 1-254。
     *   本模块的定位是 nbtscan 的常用用法,一次几千个目标(如 /16)
     *   在 200 ms 超时下也要数分钟,因此刻意不做大网段全量扫描。
     */
    private fun buildPlan(raw: String): TargetPlan? {
        val input = raw.trim()
        if (input.isEmpty()) return null

        val slash = input.indexOf('/')
        if (slash < 0) {
            val octets = parseIpv4(input) ?: return null
            val ip = octets.joinToString(".")
            return TargetPlan(ips = listOf(ip), label = ip, note = null)
        }

        val addressPart = input.substring(0, slash).trim()
        val prefixPart = input.substring(slash + 1).trim()
        val octets = parseIpv4(addressPart) ?: return null
        val prefix = prefixPart.toIntOrNull() ?: return null
        if (prefix !in 0..32) return null

        if (prefix <= 24) {
            val base = "${octets[0]}.${octets[1]}.${octets[2]}"
            return TargetPlan(
                ips = (1..254).map { "$base.$it" },
                label = "$base.0/24",
                note = if (prefix < 24) {
                    "输入前缀为 /$prefix,已按 /24 收敛,只扫描 $base.1-254"
                } else {
                    null
                },
            )
        }

        val mask = -1 shl (32 - prefix)
        val network = ipv4ToInt(octets) and mask
        val blockSize = 1 shl (32 - prefix)
        val first = if (prefix <= 30) network + 1 else network
        val last = if (prefix <= 30) network + blockSize - 2 else network + blockSize - 1
        return TargetPlan(
            ips = (first..last).map { intToIpv4(it) },
            label = "${intToIpv4(network)}/$prefix",
            note = null,
        )
    }

    private fun parseIpv4(text: String): IntArray? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (i in 0..3) {
            val part = parts[i]
            if (part.isEmpty() || part.length > 3) return null
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            octets[i] = value
        }
        return octets
    }

    private fun ipv4ToInt(octets: IntArray): Int =
        (octets[0] shl 24) or (octets[1] shl 16) or (octets[2] shl 8) or octets[3]

    private fun intToIpv4(value: Int): String =
        "${(value ushr 24) and 0xFF}.${(value ushr 16) and 0xFF}." +
            "${(value ushr 8) and 0xFF}.${value and 0xFF}"

    private fun sortHosts(hosts: Collection<NetBiosHost>): List<NetBiosHost> =
        hosts.toList().sortedBy { ipOrder(it.ip) }

    private fun ipOrder(ip: String): Long {
        val octets = parseIpv4(ip) ?: return 0L
        return (octets[0].toLong() shl 24) or
            (octets[1].toLong() shl 16) or
            (octets[2].toLong() shl 8) or
            octets[3].toLong()
    }

    // ── 本机网络与 ARP 缓存 ────────────────────────────────────────────────────

    /** 是否存在可用的局域网 IPv4(跳过回环与 169.254.x.x 链路本地地址) */
    private fun hasLanIpv4(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { iface ->
            iface.isUp && !iface.isLoopback && iface.inetAddresses.toList().any { address ->
                address is Inet4Address &&
                    !address.isLoopbackAddress &&
                    !address.hostAddress.orEmpty().startsWith(APIPA_PREFIX)
            }
        }
    }.getOrDefault(false)

    /**
     * 读取系统 ARP 缓存 —— 只作为 MAC 的补充来源。
     *
     * Android 10+ 对 `/proc/net` 的访问受限,未 root 的设备通常得到空表,
     * 这是系统策略下的正常结果,不是错误。
     */
    @Suppress("MissingPermission")
    private fun readArpCache(): Map<String, String> {
        val cache = HashMap<String, String>()
        runCatching {
            ProcessBuilder(ARP_READER, ARP_PATH)
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .use { reader ->
                    reader.readLine()
                    reader.forEachLine { line ->
                        val parts = line.trim().split(WHITESPACE)
                        if (parts.size >= 4) {
                            val mac = parts[3].uppercase(Locale.US)
                            if (mac != INCOMPLETE_MAC) cache[parts[0]] = mac
                        }
                    }
                }
        }
        return cache
    }

    /** 响应解析的中间结果 */
    private data class ParsedNbt(
        val names: List<Pair<String, String>>,
        val mac: String?,
    )

    companion object {
        const val NBT_PORT = 137

        /** 每个目标的硬超时(毫秒) */
        const val TARGET_TIMEOUT_MS = 200L

        /** 固定并发路数 */
        const val MAX_CONCURRENCY = 8

        /** NBSTAT 查询固定 50 字节 */
        const val QUERY_SIZE = 50

        private const val MAX_RESPONSE_BYTES = 1500
        private const val DNS_HEADER_SIZE = 12
        private const val RR_HEADER_SIZE = 10
        private const val NAME_LENGTH = 15
        private const val NAME_ENTRY_SIZE = 18
        private const val MAC_SIZE = 6
        private const val TYPE_NBSTAT = 0x0021
        private const val CLASS_IN = 0x0001
        private const val HEX_DIGITS = "0123456789ABCDEF"

        private const val APIPA_PREFIX = "169.254."
        private const val ARP_READER = "cat"
        private const val ARP_PATH = "/proc/net/arp"
        private const val INCOMPLETE_MAC = "00:00:00:00:00:00"
        private val WHITESPACE = Regex("\\s+")

        const val UNKNOWN_VENDOR = "未知厂商"

        /**
         * MAC 前 3 字节(OUI)→ 厂商。
         *
         * 内置表是雷达模块所用常见厂商表的同一份数据,并补充了 Windows 环境
         * 更常见的整机 / 虚拟化厂商;它只是离线小表,表外地址如实显示未知厂商。
         */
        fun vendorOf(mac: String): String {
            val hex = mac.filter { it.isLetterOrDigit() }.uppercase(Locale.US)
            if (hex.length < 6) return UNKNOWN_VENDOR
            val oui = "${hex.substring(0, 2)}:${hex.substring(2, 4)}:${hex.substring(4, 6)}"
            return OUI_VENDORS[oui] ?: UNKNOWN_VENDOR
        }

        private val OUI_VENDORS = mapOf(
            "FC:FB:FB" to "Apple", "F0:18:98" to "Apple", "A4:83:E7" to "Apple",
            "D0:81:7A" to "Apple", "78:CA:39" to "Apple", "AC:BC:32" to "Apple",
            "F4:F1:5A" to "Apple", "8C:85:90" to "Apple", "34:36:3B" to "HUAWEI",
            "E0:24:7F" to "HUAWEI", "20:64:DE" to "HUAWEI", "50:87:B8" to "HUAWEI",
            "D4:6A:6A" to "HUAWEI", "78:02:F8" to "Xiaomi", "28:6C:07" to "Xiaomi",
            "8C:DE:F9" to "Xiaomi", "64:CC:2E" to "Xiaomi", "50:64:2B" to "Xiaomi",
            "8C:F5:A3" to "Samsung", "34:31:C4" to "Samsung", "90:00:DB" to "Samsung",
            "CC:3A:61" to "Samsung", "EC:9F:0D" to "Samsung", "04:8D:38" to "Samsung",
            "A4:C3:F0" to "Intel", "3C:A0:67" to "Intel", "58:FB:84" to "Intel",
            "BC:DD:C2" to "Sony", "7C:70:DB" to "Sony", "3C:77:E6" to "Sony",
            "40:23:43" to "Sony", "E0:E0:C2" to "Espressif", "24:0A:C4" to "Espressif",
            "DC:4F:22" to "Espressif", "A0:20:A6" to "Espressif", "30:AE:A4" to "Espressif",
            "B8:27:EB" to "Raspberry Pi", "DC:A6:32" to "Raspberry Pi",
            "E4:5F:01" to "Raspberry Pi", "D8:3A:DD" to "Raspberry Pi", "28:CD:C1" to "Raspberry Pi",
            "C4:93:D9" to "Lenovo", "00:DB:DF" to "Lenovo", "54:EE:75" to "Wistron",
            "F0:76:1C" to "HP", "00:1B:78" to "HP", "54:60:09" to "Google",
            "F4:F5:E8" to "Google", "DC:A9:04" to "Google", "A4:77:33" to "Google",
            "00:50:F2" to "Microsoft", "0C:5B:8F" to "Microsoft",
            "00:23:CC" to "Nintendo", "E4:CE:8F" to "Nintendo", "7C:BB:8A" to "Nintendo",
            "50:DC:E7" to "Amazon", "A0:02:DC" to "Amazon", "F0:27:2D" to "Amazon",
            "C8:3A:35" to "Tenda", "50:C7:BF" to "TP-Link", "94:C1:50" to "TP-Link",
            "C0:4A:00" to "TP-Link", "F4:F2:6D" to "TP-Link", "AC:84:C6" to "TP-Link",
            "E4:6F:13" to "TP-Link", "18:D6:C7" to "TP-Link", "D8:07:B6" to "TP-Link",
            "70:4F:57" to "TP-Link", "00:12:F3" to "Texas Instruments",
            // 整机厂商(Windows / 办公网络常见)
            "00:14:22" to "Dell", "B8:2A:72" to "Dell", "D4:BE:D9" to "Dell",
            "18:03:73" to "Dell", "00:1D:09" to "Dell",
            "00:1B:FC" to "ASUS", "04:D4:C4" to "ASUS", "2C:56:DC" to "ASUS",
            "3C:D9:2B" to "HP", "00:25:B3" to "HP", "00:1E:0B" to "HP",
            "00:1B:21" to "Intel", "00:16:76" to "Intel", "00:E0:4C" to "Realtek",
            "00:14:6C" to "Netgear", "20:4E:7F" to "Netgear", "A0:40:A0" to "Netgear",
            "00:1B:11" to "D-Link", "1C:7E:E5" to "D-Link", "00:1E:58" to "D-Link",
            // 虚拟化 / 虚拟机(排查域内虚拟主机时常见)
            "00:0C:29" to "VMware", "00:50:56" to "VMware", "00:05:69" to "VMware",
            "08:00:27" to "VirtualBox", "52:54:00" to "QEMU",
            "00:15:5D" to "Microsoft (Hyper-V)", "00:1C:42" to "Parallels",
        )
    }
}
