package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** SNMP 探测端口 */
const val SNMP_PORT = 161

/** 单次请求的 UDP 读取超时：2 秒 */
const val SNMP_TIMEOUT_MS = 2_000

/** 并发探测数：同一时刻最多 8 个 UDP 请求在途 */
const val SNMP_CONCURRENCY = 8

/** 单次扫描的目标地址上限：防止误输入超大网段拖垮设备与耗时 */
const val SNMP_MAX_HOSTS = 1024

/** system 组 OID —— 设备厂商 / 型号识别的真实来源 */
const val OID_SYS_DESCR = "1.3.6.1.2.1.1.1.0"
const val OID_SYS_OBJECT_ID = "1.3.6.1.2.1.1.2.0"
const val OID_SYS_NAME = "1.3.6.1.2.1.1.5.0"

/** 一次 GetRequest 同时读取的三个 system 组变量 */
val SNMP_SYSTEM_OIDS: List<String> = listOf(OID_SYS_DESCR, OID_SYS_OBJECT_ID, OID_SYS_NAME)

/**
 * 默认社区串字典
 *
 * 打印机、路由器、交换机、UPS、NAS 等设备常保留出厂默认社区串。
 * 列表只是候选字典，界面可编辑；设备是否响应完全取决于真实配置。
 */
val DEFAULT_SNMP_COMMUNITIES: List<String> = listOf(
    "public",
    "private",
    "community",
    "snmp",
    "admin",
    "read",
    "readonly",
)

/**
 * 一条真实的 SNMP 响应
 *
 * @property ip 响应设备地址
 * @property community 成功使用的社区串（设备真实接受的值）
 * @property sysDescr sysDescr.0 原文，通常包含厂商、型号、固件版本等自述信息
 * @property sysName sysName.0 原文，设备配置的主机名
 * @property sysObjectID sysObjectID.0 原文，厂商私有 MIB 标识（点分 OID）
 * @property raw 原始应答报文的十六进制，保留真实返回作为证据
 */
data class SnmpResult(
    val ip: String,
    val community: String,
    val sysDescr: String,
    val sysName: String,
    val sysObjectID: String,
    val raw: String,
)

/**
 * SNMP 扫描状态
 *
 * @property scanning 是否正在探测
 * @property progress 已完成比例 0..1
 * @property done 已完成探测数（IP × 社区串）
 * @property total 本轮总探测数
 * @property results 已确认的真实响应，按 IP 升序、保持社区串字典顺序
 * @property error 目标格式、社区串为空等可见错误；单包超时属于正常无响应，不记为错误
 */
data class SnmpState(
    val scanning: Boolean = false,
    val progress: Float = 0f,
    val done: Int = 0,
    val total: Int = 0,
    val results: List<SnmpResult> = emptyList(),
    val error: String? = null,
)

private const val SNMP_RESPONSE_BUFFER = 4096
private const val SNMP_RAW_HEX_MAX_BYTES = 2048

/**
 * SNMP 社区串扫描器（onesixtyone 等价）
 *
 * ## 探测方式
 * 对目标 IP × 社区串的每一组组合发送一条 SNMP v2c GetRequest（UDP 161），
 * 同包读取 sysDescr.0 / sysObjectID.0 / sysName.0。收到 error-status = 0
 * 且 varbind 匹配的应答，才登记为 [SnmpResult]。
 *
 * - 目标：单个 IPv4 地址，或 CIDR 网段（如 192.168.1.0/24）；网段上限
 *   [SNMP_MAX_HOSTS] 个地址，/30 及更小前缀跳过网络地址与广播地址；
 * - 并发：[SNMP_CONCURRENCY] 个请求为一批，批内并行、批间串行；
 * - 超时：单包 [SNMP_TIMEOUT_MS]，不做重试；
 * - 校验：request-id 必须与本次请求一致，应答社区串必须回显请求值，
 *   避免把迟到的其他应答误记到当前目标上。
 *
 * ## 诚实边界
 * 只能发现「启用了 SNMP v1/v2c、使用字典中某个社区串、并允许读取 system 组」
 * 的设备。没有响应时无法区分原因：社区串不对、SNMP 关闭、ACL / 防火墙拦截、
 * 仅开启 v3，都会表现为超时。sysDescr 等字段全部来自设备真实返回，读不到
 * 时记为 "--"，绝不推测厂商或型号。
 *
 * 请只扫描已获授权的网络。
 */
@Suppress("MissingPermission")
class SnmpManager {

    private val _state = MutableStateFlow(SnmpState())

    /** 扫描状态流，供 ViewModel / 界面订阅 */
    val state: StateFlow<SnmpState> = _state

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    /** 在途 UDP socket，stop 时统一关闭以立即解除 receive 阻塞 */
    private val activeSockets: MutableSet<DatagramSocket> =
        Collections.synchronizedSet(HashSet<DatagramSocket>())

    /** request-id 从 1 递增，取低 31 位保证为正数 */
    private val requestIdSeq = AtomicInteger(1)

    /** 扫描代数锁：stop / 新一轮 scan 都会推进代数，旧协程的迟到发布自动失效 */
    private val generationLock = Any()
    private var generation = 0

    /**
     * 启动一轮扫描
     *
     * 先取消上一轮扫描（不保留其进度），再按目标 × 社区串生成探测矩阵。
     * 输入非法时只把错误写入 [SnmpState.error]，不抛异常。
     */
    fun scan(target: String, communities: List<String>) {
        val targets = try {
            expandTargets(target)
        } catch (e: IllegalArgumentException) {
            stop()
            _state.value = SnmpState(error = e.message ?: "目标格式无效")
            return
        }

        val dictionary = communities
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (dictionary.isEmpty()) {
            stop()
            _state.value = SnmpState(error = "至少输入一个社区串")
            return
        }

        stop()

        val generationToken = beginGeneration()
        val pairs = ArrayList<Pair<String, String>>(targets.size * dictionary.size)
        targets.forEach { ip ->
            dictionary.forEach { community -> pairs.add(ip to community) }
        }
        _state.value = SnmpState(scanning = true, total = pairs.size)

        val scanScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = scanScope
        job = scanScope.launch {
            if (!isCurrentGeneration(generationToken)) return@launch
            var done = 0
            val found = ArrayList<SnmpResult>()
            try {
                for (chunk in pairs.chunked(SNMP_CONCURRENCY)) {
                    if (!isActive || !isCurrentGeneration(generationToken)) break
                    val batch = chunk
                        .map { (ip, community) -> async { probe(ip, community) } }
                        .awaitAll()
                    done += chunk.size
                    batch.filterNotNull().toCollection(found)
                    publish(generationToken, found, done, pairs.size, scanning = true)
                }
                publish(generationToken, found, done, pairs.size, scanning = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                publish(
                    generationToken,
                    found,
                    done,
                    pairs.size,
                    scanning = false,
                    error = "扫描异常: ${describe(e)}",
                )
            }
        }
    }

    /** 停止当前扫描；已发现的结果保留，socket 立即关闭 */
    fun stop() {
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null
        closeActiveSockets()
        synchronized(generationLock) {
            generation += 1
            val current = _state.value
            if (current.scanning) _state.value = current.copy(scanning = false)
        }
    }

    /** 清空结果、进度与错误 */
    fun reset() {
        stop()
        _state.value = SnmpState()
    }

    /** ViewModel 回收时的兜底关闭 */
    fun shutdown() = stop()

    // ── 单次探测 ────────────────────────────────────────────────────────────

    /**
     * 探测一个 (IP, 社区串)
     *
     * @return 有效响应；超时、格式异常、error-status 非 0、社区串/request-id
     *         不匹配、三个 system 变量全缺时均返回 null
     */
    private fun probe(ip: String, community: String): SnmpResult? {
        val socket = DatagramSocket()
        activeSockets.add(socket)
        return try {
            socket.soTimeout = SNMP_TIMEOUT_MS
            val requestId = nextRequestId()
            val payload = BerCodec.encodeGetRequest(community, requestId, SNMP_SYSTEM_OIDS)
            val address = InetAddress.getByName(ip)
            socket.send(DatagramPacket(payload, payload.size, address, SNMP_PORT))

            val buffer = ByteArray(SNMP_RESPONSE_BUFFER)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            val data = buffer.copyOfRange(0, packet.length)

            val response = BerCodec.decodeResponse(data) ?: return null
            if (!response.isResponse) return null
            if (response.version != BerCodec.SNMP_VERSION_V1 &&
                response.version != BerCodec.SNMP_VERSION_V2C
            ) {
                return null
            }
            if (response.requestId != requestId.toLong()) return null
            if (!response.community.equals(community, ignoreCase = true)) return null
            if (response.errorStatus != 0) return null

            val byOid = response.varbinds.associateBy { it.oid }
            val sysDescr = byOid[OID_SYS_DESCR]?.display
            val sysObjectID = byOid[OID_SYS_OBJECT_ID]?.display
            val sysName = byOid[OID_SYS_NAME]?.display
            // 三个系统变量全缺：不是可用的 system 组响应，不登记
            if (sysDescr.isNullOrBlank() && sysObjectID.isNullOrBlank() && sysName.isNullOrBlank()) {
                return null
            }

            SnmpResult(
                ip = ip,
                community = community,
                sysDescr = sysDescr?.takeIf { it.isNotBlank() } ?: "--",
                sysName = sysName?.takeIf { it.isNotBlank() } ?: "--",
                sysObjectID = sysObjectID?.takeIf { it.isNotBlank() } ?: "--",
                raw = BerCodec.toHex(data, SNMP_RAW_HEX_MAX_BYTES),
            )
        } catch (_: Exception) {
            // 超时、网络不可达、报文损坏都是扫描中的正常现象，不逐条上报
            null
        } finally {
            activeSockets.remove(socket)
            runCatching { socket.close() }
        }
    }

    private fun nextRequestId(): Int {
        val next = requestIdSeq.incrementAndGet() and 0x7FFFFFFF
        return if (next == 0) 1 else next
    }

    private fun beginGeneration(): Int = synchronized(generationLock) {
        generation += 1
        generation
    }

    private fun isCurrentGeneration(token: Int): Boolean =
        synchronized(generationLock) { token == generation }

    private fun publish(
        token: Int,
        found: List<SnmpResult>,
        done: Int,
        total: Int,
        scanning: Boolean,
        error: String? = null,
    ) {
        // 检查与写入放在同一把锁内：stop / 新一轮 scan 之后，旧协程的迟到状态不会覆盖新状态
        synchronized(generationLock) {
            if (token != generation) return
            _state.value = SnmpState(
                scanning = scanning,
                progress = if (total <= 0) 0f else done.toFloat() / total,
                done = done,
                total = total,
                results = sortedResults(found),
                error = error,
            )
        }
    }

    private fun sortedResults(results: List<SnmpResult>): List<SnmpResult> =
        results.sortedBy { parseIpv4(it.ip) ?: Long.MAX_VALUE }

    private fun closeActiveSockets() {
        synchronized(activeSockets) {
            activeSockets.forEach { socket -> runCatching { socket.close() } }
            activeSockets.clear()
        }
    }

    private fun describe(error: Exception): String =
        error.message?.let { "${error.javaClass.simpleName}: $it" }
            ?: error.javaClass.simpleName

    // ── 目标解析 ────────────────────────────────────────────────────────────

    /**
     * 展开目标：单 IP 原样规范化，CIDR 展开为可用主机地址
     *
     * 只支持 IPv4 字面量与 CIDR，不做 DNS 解析（避免把域名扫描变成不可控目标）。
     */
    private fun expandTargets(target: String): List<String> {
        val text = target.trim()
        require(text.isNotEmpty()) { "请输入目标 IP 或网段" }
        if (!text.contains('/')) {
            val single = parseIpv4(text)
                ?: throw IllegalArgumentException("IP 格式无效: $text")
            return listOf(formatIpv4(single))
        }
        return expandCidr(text)
    }

    private fun expandCidr(target: String): List<String> {
        val parts = target.split('/')
        require(parts.size == 2) { "网段格式应为 a.b.c.d/前缀" }
        val base = parseIpv4(parts[0])
            ?: throw IllegalArgumentException("网段地址无效: ${parts[0]}")
        val prefix = parts[1].toIntOrNull()
            ?: throw IllegalArgumentException("前缀长度无效: ${parts[1]}")
        require(prefix in 0..32) { "前缀长度应在 0..32: $prefix" }

        val hostBits = 32 - prefix
        val addressCount = 1L shl hostBits
        require(addressCount <= SNMP_MAX_HOSTS) {
            "网段过大: $addressCount 个地址，上限 $SNMP_MAX_HOSTS；请使用更小的网段"
        }

        val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl hostBits) and 0xFFFFFFFFL
        val network = base and mask
        return if (prefix >= 31) {
            // /31、/32 没有网络地址 / 广播地址语义，全部纳入
            List(addressCount.toInt()) { index -> formatIpv4(network + index) }
        } else {
            // 常规网段跳过网络地址与广播地址
            (1L until addressCount - 1L).map { offset -> formatIpv4(network + offset) }
        }
    }

    private fun parseIpv4(text: String): Long? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            value = (value shl 8) or octet.toLong()
        }
        return value
    }

    private fun formatIpv4(value: Long): String =
        "${(value ushr 24) and 0xFF}.${(value ushr 16) and 0xFF}." +
            "${(value ushr 8) and 0xFF}.${value and 0xFF}"
}
