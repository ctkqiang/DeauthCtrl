package xin.ctkqiang.deauthctrl.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileDescriptor
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * 页面上的一条抓包记录 —— 全部字段来自 TUN 实际读到的报文
 *
 * @param id     会话内单调递增的包序号
 * @param timeMs 读取到该包的本机时间
 * @param parsed 解析结果(IP / TCP / UDP / DNS 字段与 hex 预览)
 */
data class PacketEntry(
    val id: Long,
    val timeMs: Long,
    val parsed: ParsedPacket,
)

/** 名称 + 次数 —— Top 域名 / Top 对端 IP 的统一结构 */
data class NamedCount(val name: String, val count: Long)

/**
 * 双向会话聚合 —— 按 (srcIp, dstIp) 无序对归并,去包与回包算同一会话
 *
 * @param endpointA    字典序较小的一端
 * @param endpointB    另一端
 * @param packets      会话内包数
 * @param bytes        会话内 IPv4 总长度之和
 * @param protocols    协议 -> 包数(TCP/UDP/ICMP/...)
 * @param lastActiveMs 最近一包的本机时间
 * @param domains      会话内观察到的去重域名(DNS QNAME / TLS SNI / HTTP Host)
 */
data class SessionSummary(
    val endpointA: String,
    val endpointB: String,
    val packets: Long,
    val bytes: Long,
    val protocols: Map<String, Long>,
    val lastActiveMs: Long,
    val domains: List<String>,
)

/**
 * 流量监视状态
 *
 * @param running        服务是否在抓包
 * @param packets        最新在前的环形包列表(最多 [PacketCaptureService.RING_CAPACITY] 条)
 * @param totalPackets   本次会话累计包数
 * @param totalBytes     本次会话累计字节数(按 IPv4 总长度求和)
 * @param domains        本次会话观察到的唯一域名集合(TLS SNI / DNS / HTTP Host)
 * @param domainCounts   按包数排序的域名统计(Top 10,供汇总面板)
 * @param peerCounts     按包数排序的对端 IP 统计(Top 10)
 * @param protocolCounts 全量协议分布(TCP/UDP/ICMP/... -> 包数)
 * @param sessions       双向会话聚合,按最近活动倒序
 * @param sessionCount   会话总数(可能大于 [sessions] 的展示上限)
 * @param error          硬失败原因;正常时为 null
 */
data class PacketMonitorState(
    val running: Boolean = false,
    val packets: List<PacketEntry> = emptyList(),
    val totalPackets: Long = 0L,
    val totalBytes: Long = 0L,
    val domains: Set<String> = emptySet(),
    val domainCounts: List<NamedCount> = emptyList(),
    val peerCounts: List<NamedCount> = emptyList(),
    val protocolCounts: Map<String, Long> = emptyMap(),
    val sessions: List<SessionSummary> = emptyList(),
    val sessionCount: Int = 0,
    val error: String? = null,
)

/**
 * 本机流量监视服务 —— VpnService + 用户态转发
 *
 * ## 工作方式
 * 1. [Builder] 建立本地 TUN:地址 10.8.0.1/32,路由 0.0.0.0/0(仅 IPv4),
 *    MTU 1500;系统把本机所有 IPv4 流量交给本进程。
 * 2. 读线程从 TUN 逐包读取,按 IPv4 总长度字段切分,交给 [parseIpv4Packet] 解析;
 *    解析结果写入 [state] 的环形列表(最多 500 条),供页面展示。
 * 3. 为不断网,按流(源端口 + 目的 IP + 目的端口)做用户态转发:
 *    - UDP:每个流一个 [DatagramSocket],回包重新封装 IPv4+UDP 写回 TUN;
 *    - TCP:每个流一个 [Socket],在用户态应答 SYN-ACK、转发载荷、回写数据与 FIN。
 *    - 转发 socket 全部先 [protect],避免自身流量再次进入 VPN 形成回环。
 * 4. ICMP 与 IPv6 不转发:本项目只申请 IPv4 路由,IPv6 流量走系统直连;
 *    ICMP 只能在列表中看到请求,没有回包(ping 不通是预期行为)。
 *
 * ## 线程模型
 * - 一个 TUN 读线程(单线程解析与记录发布);
 * - 每个 TCP / UDP 流各自的连接 / 读线程,共享一个 TUN 写锁;
 * - 一个清理线程,每 5 秒回收空闲超过 60 秒的流。
 *
 * ## 诚实边界
 * 这是用户态 TCP 代理的简化实现:不做乱序重排、不做窗口/拥塞控制、
 * TCP 选项未协商,极端网络条件下个别连接可能重传或超时;HTTPS 内容加密,
 * 只能看到 SNI 域名;无法看到同网络其他设备的流量(需要 root + ARP 欺骗)。
 * 所有统计都来自真实报文,读不到时页面显示「--」,绝不编造。
 */
class PacketCaptureService : VpnService() {

    /** TUN 的唯一持有者 —— close() 它就是系统拆除 VPN 的触发点 */
    private var tun: ParcelFileDescriptor? = null
    private val tunWriteLock = Any()

    @Volatile
    private var running = false

    private var captureThread: Thread? = null
    private var cleanerThread: Thread? = null

    /** 停机自管道:写入 1 字节即可唤醒 poll 中的读线程,避免卡在阻塞 read */
    private var stopPipe: Array<FileDescriptor>? = null
    private val stopLock = Any()
    @Volatile
    private var stopping = false

    private val tcpFlows = ConcurrentHashMap<String, TcpFlow>()
    private val udpFlows = ConcurrentHashMap<String, UdpFlow>()

    // ─── 页面状态(读线程单写,锁保护快照) ────────────────────────────────
    private val ringLock = Any()
    private val ring = ArrayDeque<PacketEntry>()
    private var totalPackets = 0L
    private var totalBytes = 0L
    private var packetId = 0L
    private var lastPublishMs = 0L

    // 会话与聚合统计(与 ring 共用 ringLock;只在读线程写)
    private val sessions = LinkedHashMap<String, SessionAgg>()
    private val domains = LinkedHashSet<String>()
    private val domainCounts = LinkedHashMap<String, Long>()
    private val peerCounts = LinkedHashMap<String, Long>()
    private val protocolCounts = LinkedHashMap<String, Long>()

    /** 单个双向会话的可变累加器 */
    private class SessionAgg(val endpointA: String, val endpointB: String) {
        var packets = 0L
        var bytes = 0L
        var lastActiveMs = 0L
        val protocols = LinkedHashMap<String, Long>()
        val domains = LinkedHashSet<String>()
    }

    // ══════════════════════════════════════════════════════════════════════
    // 生命周期
    // ══════════════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // 通知栏「停止」:先在本进程内立即拆隧道,再让系统回收服务
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundSafely()
        if (!running) startCapture()
        return START_STICKY
    }

    /** 其他 VPN 抢占了 VPN 通道 —— 必须让出,网络随即恢复直连 */
    override fun onRevoke() {
        stopCapture()
        stopSelf()
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        stopCapture()
        instance = null
        _state.value = _state.value.copy(running = false)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
        }
        super.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════════════
    // TUN 建立与读取
    // ══════════════════════════════════════════════════════════════════════

    private fun startCapture() {
        if (running) return

        // 新一轮启动:重置停机标志并创建自管道
        stopping = false
        stopPipe = runCatching { Os.pipe() }.getOrElse {
            failAndStop("[!] 停机管道创建失败 —— ${it.javaClass.simpleName}")
            return
        }

        val builder = Builder()
            .setSession(SESSION_NAME)
            .addAddress(LOCAL_IP_TEXT, 32)
            .addRoute("0.0.0.0", 0)
            .setMtu(MTU)

        val pfd = runCatching { builder.establish() }.getOrElse {
            closeStopPipe()
            failAndStop("[!] VPN 建立异常 —— ${it.javaClass.simpleName}: ${it.message ?: ""}")
            return
        }
        if (pfd == null) {
            closeStopPipe()
            failAndStop("[!] VPN 建立失败 —— 未授权或已有其他 VPN 在运行")
            return
        }

        tun = pfd

        // 新会话:清空上一轮统计、会话聚合与去重域名,但保留启动阶段写入的错误
        val startupError = _state.value.error
        synchronized(ringLock) {
            ring.clear()
            totalPackets = 0L
            totalBytes = 0L
            packetId = 0L
            lastPublishMs = 0L
            sessions.clear()
            domains.clear()
            domainCounts.clear()
            peerCounts.clear()
            protocolCounts.clear()
        }
        _state.value = PacketMonitorState(running = true, error = startupError)

        running = true
        val pipe = stopPipe
        captureThread = Thread({ readLoop(pfd, pipe) }, "packet-capture-read").apply {
            isDaemon = true
            start()
        }
        cleanerThread = Thread({ cleanLoop() }, "packet-flow-cleaner").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * TUN 读取循环(基于 poll,不直接阻塞在阻塞 read 上)
     *
     * 旧实现直接在 TUN fd 上做阻塞 read:停止时从另一个线程 close fd 并不能
     * 可靠唤醒正在进行的 read,读线程会一直攥着内核 file 引用,VPN 接口因此
     * 迟迟不拆除 —— 表现就是「点了停止但 VPN 取消不掉」。
     *
     * 现在 poll 同时等待 TUN 与停机自管道:[stopCapture] 写 1 字节即可唤醒,
     * 并且每 [POLL_TIMEOUT_MS] 还会兜底检查一次 running。线程必定退出,fd 才
     * 能真正释放,系统才会摘掉 VPN 图标。
     */
    private fun readLoop(pfd: ParcelFileDescriptor, pipe: Array<FileDescriptor>?) {
        val stopFd = pipe?.getOrNull(0)
        if (stopFd == null) {
            if (running) failAndStop("[!] 停机管道缺失 —— 抓包已停止")
            return
        }
        val buffer = ByteArray(READ_BUFFER_SIZE)
        val pollFds = arrayOf(
            StructPollfd().apply {
                fd = pfd.fileDescriptor
                events = OsConstants.POLLIN.toShort()
            },
            StructPollfd().apply {
                fd = stopFd
                events = OsConstants.POLLIN.toShort()
            },
        )
        var failure: String? = null

        while (running) {
            pollFds[0].revents = 0
            pollFds[1].revents = 0
            val ready = try {
                Os.poll(pollFds, POLL_TIMEOUT_MS)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EINTR) continue
                failure = "TUN 轮询失败 —— ${e.message}"
                break
            }
            if (ready < 0) continue
            if (pollFds[1].revents.toInt() and OsConstants.POLLIN != 0) break // 主动停止
            if (pollFds[0].revents.toInt() and
                (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0
            ) {
                failure = "TUN 设备已关闭"
                break
            }
            if (pollFds[0].revents.toInt() and OsConstants.POLLIN == 0) continue

            val read = try {
                Os.read(pfd.fileDescriptor, buffer, 0, buffer.size)
            } catch (e: Exception) {
                // EINTR / EAGAIN 只是「这一轮没数据」;其他异常视为 TUN 读失败
                if (e is ErrnoException &&
                    (e.errno == OsConstants.EINTR || e.errno == OsConstants.EAGAIN)
                ) {
                    continue
                }
                failure = "TUN 读取失败 —— ${e.message ?: e.javaClass.simpleName}"
                break
            }
            if (read <= 0) continue
            processRead(buffer, read)
        }

        if (failure != null && running) {
            // 非用户主动停止:必须把隧道一并拆掉,否则会留下「僵尸 VPN」
            failAndStop("[!] $failure —— 抓包已停止")
        }
    }

    /**
     * 按 IPv4 总长度字段切分一次 read 得到的若干包(TUN 通常一次一包)
     *
     * 不完整的尾部直接丢弃,等下一轮 read 补齐。
     */
    private fun processRead(buffer: ByteArray, length: Int) {
        var offset = 0
        while (offset + 20 <= length) {
            val version = (buffer[offset].toInt() ushr 4) and 0x0F
            if (version != 4) break
            val totalLen = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                (buffer[offset + 3].toInt() and 0xFF)
            if (totalLen < 20 || offset + totalLen > length) break
            val packet = buffer.copyOfRange(offset, offset + totalLen)
            runCatching { handlePacket(packet) }
            offset += totalLen
        }
    }

    private fun handlePacket(packet: ByteArray) {
        val parsed = parseIpv4Packet(packet, packet.size) ?: return
        recordPacket(parsed)
        if (!running) return
        runCatching {
            when (parsed.protocol) {
                "TCP" -> handleTcpPacket(packet)
                "UDP" -> handleUdpPacket(packet)
                else -> Unit // ICMP / GRE / SCTP 等无用户态转发能力,只记录
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 记录与状态发布
    // ══════════════════════════════════════════════════════════════════════

    private fun recordPacket(parsed: ParsedPacket) {
        synchronized(ringLock) {
            packetId += 1
            val entry = PacketEntry(
                id = packetId,
                timeMs = System.currentTimeMillis(),
                parsed = parsed,
            )
            ring.addFirst(entry)
            while (ring.size > RING_CAPACITY) ring.removeLast()
            totalPackets += 1
            totalBytes += parsed.ipTotalLen
            updateAggregatesLocked(parsed)
        }
        publishThrottled()
    }

    /**
     * 更新会话 / 域名 / 对端 / 协议聚合 —— 调用方必须持有 [ringLock]
     *
     * 会话按 (srcIp, dstIp) 双向归并:两端排序后作为 key,去包与回包进同一会话。
     * 所有 map 都有上限,长时间抓包不会无限增长;超限时淘汰计数最小/最久未活动的项。
     */
    private fun updateAggregatesLocked(parsed: ParsedPacket) {
        val now = System.currentTimeMillis()
        val proto = parsed.protocol
        protocolCounts[proto] = (protocolCounts[proto] ?: 0L) + 1

        val peer = parsed.dstIp
        peerCounts[peer] = (peerCounts[peer] ?: 0L) + 1
        if (peerCounts.size > MAX_PEER_STATS) {
            peerCounts.entries.minByOrNull { it.value }?.let { peerCounts.remove(it.key) }
        }

        val domain = parsed.domain
        if (domain != null) {
            domains.add(domain)
            domainCounts[domain] = (domainCounts[domain] ?: 0L) + 1
            if (domainCounts.size > MAX_DOMAINS) {
                domainCounts.entries.minByOrNull { it.value }?.let {
                    domainCounts.remove(it.key)
                    domains.remove(it.key)
                }
            }
        }

        val (a, b) = if (parsed.srcIp <= parsed.dstIp) {
            parsed.srcIp to parsed.dstIp
        } else {
            parsed.dstIp to parsed.srcIp
        }
        val key = "$a|$b"
        val agg = sessions[key] ?: SessionAgg(a, b).also {
            if (sessions.size >= MAX_SESSIONS) {
                sessions.entries.minByOrNull { e -> e.value.lastActiveMs }
                    ?.let { e -> sessions.remove(e.key) }
            }
            sessions[key] = it
        }
        agg.packets += 1
        agg.bytes += parsed.ipTotalLen
        agg.lastActiveMs = now
        agg.protocols[proto] = (agg.protocols[proto] ?: 0L) + 1
        if (domain != null && agg.domains.size < MAX_SESSION_DOMAINS) agg.domains.add(domain)
    }

    private fun publishThrottled() = publish(force = false)

    /** 发布快照 —— 一个锁内同时取包列表与聚合统计,保证同一时刻的数据一致 */
    private fun publish(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPublishMs < PUBLISH_INTERVAL_MS) return
        lastPublishMs = now
        val snapshot = synchronized(ringLock) {
            PacketSnapshot(
                packets = ring.toList(),
                totalPackets = totalPackets,
                totalBytes = totalBytes,
                domains = domains.toSet(),
                domainCounts = topCounts(domainCounts, TOP_DOMAIN_LIMIT),
                peerCounts = topCounts(peerCounts, TOP_PEER_LIMIT),
                protocolCounts = protocolCounts.toMap(),
                sessions = sessions.values
                    .sortedByDescending { it.lastActiveMs }
                    .take(MAX_SESSIONS_PUBLISHED)
                    .map { agg ->
                        SessionSummary(
                            endpointA = agg.endpointA,
                            endpointB = agg.endpointB,
                            packets = agg.packets,
                            bytes = agg.bytes,
                            protocols = agg.protocols.toMap(),
                            lastActiveMs = agg.lastActiveMs,
                            domains = agg.domains.toList(),
                        )
                    },
                sessionCount = sessions.size,
            )
        }
        _state.value = _state.value.copy(
            running = running,
            packets = snapshot.packets,
            totalPackets = snapshot.totalPackets,
            totalBytes = snapshot.totalBytes,
            domains = snapshot.domains,
            domainCounts = snapshot.domainCounts,
            peerCounts = snapshot.peerCounts,
            protocolCounts = snapshot.protocolCounts,
            sessions = snapshot.sessions,
            sessionCount = snapshot.sessionCount,
        )
    }

    /** map 按次数倒序取前 limit 条;次数相同按名称排序,保证展示稳定 */
    private fun topCounts(source: Map<String, Long>, limit: Int): List<NamedCount> =
        source.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { NamedCount(it.key, it.value) }

    /** 清空页面列表、聚合统计与本次会话计数 */
    private fun clearPacketRing() {
        synchronized(ringLock) {
            ring.clear()
            totalPackets = 0L
            totalBytes = 0L
            packetId = 0L
            sessions.clear()
            domains.clear()
            domainCounts.clear()
            peerCounts.clear()
            protocolCounts.clear()
        }
        _state.value = _state.value.copy(
            packets = emptyList(),
            totalPackets = 0L,
            totalBytes = 0L,
            domains = emptySet(),
            domainCounts = emptyList(),
            peerCounts = emptyList(),
            protocolCounts = emptyMap(),
            sessions = emptyList(),
            sessionCount = 0,
        )
    }

    private fun failAndStop(message: String) {
        stopCapture()
        _state.value = _state.value.copy(running = false, error = message)
        stopSelf()
    }

    /**
     * 幂等停机 —— 任意线程、任意次数调用都安全
     *
     * 顺序是硬性的:
     * 1. 写自管道唤醒 poll 的读线程;
     * 2. 关闭 TUN fd —— 系统据此拆除 VPN 隧道、摘掉状态栏 VPN 图标;
     * 3. join 读线程,确认它已释放对 fd 的最后引用;
     * 4. 最后才拆除 TCP/UDP 转发表并推送 running = false。
     *
     * 这样既不依赖 close() 去打断某个阻塞 read(内核语义不保证),也不会有
     * 线程在服务销毁后继续转发。
     */
    private fun stopCapture() {
        synchronized(stopLock) {
            if (stopping) return
            stopping = true
        }
        running = false

        // 1) 唤醒读线程,并关闭 TUN fd —— 隧道与系统 VPN 图标随之消失
        signalStopPipe()
        val pfd = tun
        tun = null
        runCatching { pfd?.close() }

        // 2) 等读线程真正退出(daemon + 超时兜底,绝不死等)
        val reader = captureThread
        captureThread = null
        if (reader != null && reader !== Thread.currentThread()) {
            runCatching { reader.join(CAPTURE_JOIN_TIMEOUT_MS) }
        }
        closeStopPipe()

        // 3) 再拆转发线程与流表
        val cleaner = cleanerThread
        cleanerThread = null
        runCatching { cleaner?.interrupt() }
        if (cleaner != null && cleaner !== Thread.currentThread()) {
            runCatching { cleaner.join(CLEANER_JOIN_TIMEOUT_MS) }
        }
        closeAllFlows()

        // 4) 状态翻 false 并推送最后一帧
        publish(force = true)
        _state.value = _state.value.copy(running = false)
    }

    /** 停止信号 —— 写 1 字节到自管道,读线程的 poll 立即返回 */
    private fun signalStopPipe() {
        val writeFd = stopPipe?.getOrNull(1) ?: return
        runCatching { Os.write(writeFd, WAKE_BYTES, 0, 1) }
    }

    private fun closeStopPipe() {
        val pipe = stopPipe ?: return
        stopPipe = null
        pipe.forEach { fd -> runCatching { Os.close(fd) } }
    }

    /** 关闭全部转发流;socket 一律先于流表清空关闭,避免残留转发线程 */
    private fun closeAllFlows() {
        val tcpSnapshot = tcpFlows.values.toList()
        tcpFlows.clear()
        tcpSnapshot.forEach { flow ->
            flow.closed = true
            runCatching { flow.socket?.close() }
            runCatching { flow.readerThread?.interrupt() }
        }
        val udpSnapshot = udpFlows.values.toList()
        udpFlows.clear()
        udpSnapshot.forEach { flow ->
            flow.closed = true
            runCatching { flow.socket.close() }
        }
    }

    /** 发布用的不可变快照 —— 避免在锁外看到撕裂的聚合数据 */
    private class PacketSnapshot(
        val packets: List<PacketEntry>,
        val totalPackets: Long,
        val totalBytes: Long,
        val domains: Set<String>,
        val domainCounts: List<NamedCount>,
        val peerCounts: List<NamedCount>,
        val protocolCounts: Map<String, Long>,
        val sessions: List<SessionSummary>,
        val sessionCount: Int,
    )

    // ══════════════════════════════════════════════════════════════════════
    // TCP 用户态转发
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 单个 TCP 流
     *
     * 客户端(10.8.0.1)的序列号从 SYN 的 seq+1 开始累计;服务端序列号由本进程
     * 选一个随机 ISN。pending 缓存连接建立前客户端抢先发来的载荷(上限 64KB)。
     */
    private class TcpFlow(
        val key: String,
        val clientPort: Int,
        val remoteIp: Int,
        val remotePort: Int,
        initialClientSeq: Long,
    ) {
        @Volatile var clientSeq: Long = (initialClientSeq + 1) and SEQ_MASK
        @Volatile var serverSeq: Long = ((System.nanoTime() / 1000L) and SEQ_MASK)
        @Volatile var lastActiveMs: Long = SystemClock.elapsedRealtime()
        @Volatile var connected = false
        @Volatile var clientFin = false
        @Volatile var closed = false
        var socket: Socket? = null
        var output: OutputStream? = null
        var readerThread: Thread? = null
        val pending = ArrayDeque<ByteArray>()
        var pendingBytes = 0
    }

    private fun handleTcpPacket(packet: ByteArray) {
        val ihl = (u8(packet, 0) and 0x0F) * 4
        if (packet.size < ihl + 20) return

        val srcPort = u16(packet, ihl)
        val dstPort = u16(packet, ihl + 2)
        val remoteIp = u32(packet, 16).toInt()
        val seq = u32(packet, ihl + 4)
        val flagByte = u8(packet, ihl + 13)
        val dataOffset = (u8(packet, ihl + 12) ushr 4) * 4
        if (dataOffset < 20 || ihl + dataOffset > packet.size) return
        val payload = packet.copyOfRange(ihl + dataOffset, packet.size)
        val key = "$srcPort:$remoteIp:$dstPort"

        // 新连接:SYN(不带 ACK)。转发的 socket 在独立线程里连接,避免卡住读循环。
        if (flagByte and TCP_SYN != 0 && flagByte and TCP_ACK == 0) {
            if (tcpFlows.containsKey(key)) return
            if (tcpFlows.size >= MAX_TCP_FLOWS) {
                sendRst(packet, ihl)
                return
            }
            val flow = TcpFlow(key, srcPort, remoteIp, dstPort, seq)
            tcpFlows[key] = flow
            Thread({ connectAndHandshake(flow) }, "tcp-connect-$srcPort").apply {
                isDaemon = true
                start()
            }
            return
        }

        val flow = tcpFlows[key]
        if (flow == null) {
            // 没见过的流(可能 SYN 被丢):回 RST,让客户端尽快重连
            if (flagByte and TCP_RST == 0) sendRst(packet, ihl)
            return
        }

        var closeAfter = false
        synchronized(flow) {
            if (flow.closed) {
                closeAfter = true
                return@synchronized
            }
            flow.lastActiveMs = SystemClock.elapsedRealtime()

            if (flagByte and TCP_RST != 0) {
                closeAfter = true
                return@synchronized
            }

            if (payload.isNotEmpty()) {
                if (flow.connected) {
                    runCatching {
                        flow.output?.write(payload)
                        flow.output?.flush()
                    }
                } else if (flow.pendingBytes + payload.size <= MAX_PENDING_BYTES) {
                    flow.pending.addLast(payload)
                    flow.pendingBytes += payload.size
                }
                flow.clientSeq = (flow.clientSeq + payload.size) and SEQ_MASK
                sendTcp(flow, TCP_ACK, flow.serverSeq, flow.clientSeq, EMPTY_BYTES)
            }

            if (flagByte and TCP_FIN != 0) {
                flow.clientSeq = (flow.clientSeq + 1) and SEQ_MASK
                sendTcp(flow, TCP_ACK, flow.serverSeq, flow.clientSeq, EMPTY_BYTES)
                flow.clientFin = true
                runCatching { flow.socket?.shutdownOutput() }
            }
        }
        if (closeAfter) closeTcpFlow(flow)
    }

    /** 连接远端并完成用户态三次握手;失败时给客户端回 RST */
    private fun connectAndHandshake(flow: TcpFlow) {
        if (!running) {
            closeTcpFlow(flow)
            return
        }
        val socket = Socket()
        val connected = runCatching {
            if (!protect(socket)) return@runCatching false
            socket.tcpNoDelay = true
            socket.connect(
                InetSocketAddress(InetAddress.getByAddress(ipBytes(flow.remoteIp)), flow.remotePort),
                CONNECT_TIMEOUT_MS,
            )
            true
        }.getOrDefault(false)

        if (!connected) {
            runCatching { socket.close() }
            synchronized(flow) {
                sendTcp(flow, TCP_RST or TCP_ACK, 0L, flow.clientSeq, EMPTY_BYTES)
                flow.closed = true
            }
            closeTcpFlow(flow)
            return
        }

        synchronized(flow) {
            if (flow.closed || !running) {
                runCatching { socket.close() }
                flow.closed = true
                return
            }
            val out = runCatching { socket.getOutputStream() }.getOrNull()
            if (out == null) {
                runCatching { socket.close() }
                flow.closed = true
                return
            }
            flow.socket = socket
            flow.output = out
            flow.connected = true

            sendTcp(flow, TCP_SYN or TCP_ACK, flow.serverSeq, flow.clientSeq, EMPTY_BYTES)
            flow.serverSeq = (flow.serverSeq + 1) and SEQ_MASK

            while (flow.pending.isNotEmpty()) {
                val chunk = flow.pending.removeFirst()
                flow.pendingBytes = (flow.pendingBytes - chunk.size).coerceAtLeast(0)
                runCatching {
                    flow.output?.write(chunk)
                    flow.output?.flush()
                }
            }
            if (flow.clientFin) runCatching { flow.socket?.shutdownOutput() }
        }

        val reader = Thread({ readFromRemote(flow) }, "tcp-read-${flow.clientPort}")
        reader.isDaemon = true
        flow.readerThread = reader
        reader.start()
    }

    /** 读远端回包 → 封装 TCP 段写回 TUN;EOF 时发 FIN,收尾后回收流 */
    private fun readFromRemote(flow: TcpFlow) {
        val socket = flow.socket
        if (socket == null) {
            closeTcpFlow(flow)
            return
        }
        val input: InputStream = runCatching { socket.getInputStream() }.getOrElse {
            closeTcpFlow(flow)
            return
        }
        val buffer = ByteArray(RELAY_BUFFER_SIZE)
        while (running && !flow.closed) {
            val n = runCatching { input.read(buffer) }.getOrDefault(-1)
            if (n <= 0) break
            val payload = buffer.copyOfRange(0, n)
            synchronized(flow) {
                if (flow.closed) return@synchronized
                flow.lastActiveMs = SystemClock.elapsedRealtime()
                sendTcp(flow, TCP_ACK or TCP_PSH, flow.serverSeq, flow.clientSeq, payload)
                flow.serverSeq = (flow.serverSeq + n) and SEQ_MASK
            }
        }
        synchronized(flow) {
            if (!flow.closed) {
                flow.closed = true
                sendTcp(flow, TCP_FIN or TCP_ACK, flow.serverSeq, flow.clientSeq, EMPTY_BYTES)
                flow.serverSeq = (flow.serverSeq + 1) and SEQ_MASK
            }
        }
        closeTcpFlow(flow)
    }

    private fun closeTcpFlow(flow: TcpFlow) {
        flow.closed = true
        runCatching { flow.socket?.close() }
        runCatching { flow.readerThread?.interrupt() }
        if (tcpFlows[flow.key] === flow) tcpFlows.remove(flow.key)
    }

    /** 组装并写回一个来自远端的 TCP 段 */
    private fun sendTcp(flow: TcpFlow, flags: Int, seq: Long, ack: Long, payload: ByteArray) {
        val seg = buildTcpSegment(flow.remotePort, flow.clientPort, seq, ack, flags, payload)
        writeTransportChecksum(seg, flow.remoteIp, LOCAL_IP, 6, 16)
        writeToTun(buildIpv4Packet(flow.remoteIp, LOCAL_IP, 6, seg))
    }

    /** 对未知流回 RST —— 源/目的地址与端口取原始包的反向 */
    private fun sendRst(packet: ByteArray, ihl: Int) {
        if (packet.size < ihl + 20) return
        val srcPort = u16(packet, ihl + 2)
        val dstPort = u16(packet, ihl)
        val srcIp = u32(packet, 16).toInt()
        val dstIp = u32(packet, 12).toInt()
        val clientSeq = u32(packet, ihl + 4)
        val flagByte = u8(packet, ihl + 13)
        val dataOffset = (u8(packet, ihl + 12) ushr 4) * 4
        val payloadLen = (packet.size - ihl - dataOffset).coerceAtLeast(0)
        val ack = if (flagByte and TCP_SYN != 0) {
            (clientSeq + 1) and SEQ_MASK
        } else {
            (clientSeq + payloadLen) and SEQ_MASK
        }
        val seg = buildTcpSegment(srcPort, dstPort, 0L, ack, TCP_RST or TCP_ACK, EMPTY_BYTES)
        writeTransportChecksum(seg, srcIp, dstIp, 6, 16)
        writeToTun(buildIpv4Packet(srcIp, dstIp, 6, seg))
    }

    // ══════════════════════════════════════════════════════════════════════
    // UDP 用户态转发
    // ══════════════════════════════════════════════════════════════════════

    /** 单个 UDP 流 —— 一个远端地址 + 端口一个 DatagramSocket,回包原路封装 */
    private class UdpFlow(
        val key: String,
        val clientPort: Int,
        val remoteIp: Int,
        val remotePort: Int,
        val remote: InetAddress,
        val socket: DatagramSocket,
    ) {
        @Volatile var lastActiveMs: Long = SystemClock.elapsedRealtime()
        @Volatile var closed = false
    }

    private fun handleUdpPacket(packet: ByteArray) {
        val ihl = (u8(packet, 0) and 0x0F) * 4
        if (packet.size < ihl + 8) return
        val srcPort = u16(packet, ihl)
        val dstPort = u16(packet, ihl + 2)
        val remoteIp = u32(packet, 16).toInt()
        val udpLen = u16(packet, ihl + 4)
        val end = if (udpLen >= 8) minOf(ihl + udpLen, packet.size) else packet.size
        if (ihl + 8 > end) return
        val payload = packet.copyOfRange(ihl + 8, end)
        val key = "$srcPort:$remoteIp:$dstPort"

        var flow = udpFlows[key]
        if (flow == null) {
            if (udpFlows.size >= MAX_UDP_FLOWS) return
            val created = createUdpFlow(key, srcPort, remoteIp, dstPort) ?: return
            val raced = udpFlows.putIfAbsent(key, created)
            if (raced != null) {
                runCatching { created.socket.close() }
                flow = raced
            } else {
                startUdpReceiver(created)
                flow = created
            }
        }
        val active = flow ?: return
        active.lastActiveMs = SystemClock.elapsedRealtime()
        runCatching {
            active.socket.send(
                DatagramPacket(payload, payload.size, active.remote, active.remotePort),
            )
        }
    }

    /** 建立受 protect 保护的 UDP socket;保护失败则不转发,避免流量回环 */
    private fun createUdpFlow(
        key: String,
        clientPort: Int,
        remoteIp: Int,
        remotePort: Int,
    ): UdpFlow? {
        return runCatching {
            val socket = DatagramSocket()
            if (!protect(socket)) {
                socket.close()
                null
            } else {
                socket.soTimeout = FLOW_IDLE_TIMEOUT_MS.toInt()
                val remote = InetAddress.getByAddress(ipBytes(remoteIp))
                UdpFlow(key, clientPort, remoteIp, remotePort, remote, socket)
            }
        }.getOrNull()
    }

    private fun startUdpReceiver(flow: UdpFlow) {
        val thread = Thread({
            val buffer = ByteArray(RELAY_BUFFER_SIZE)
            while (running && !flow.closed) {
                val packet = DatagramPacket(buffer, buffer.size)
                val received = runCatching { flow.socket.receive(packet) }.isSuccess
                if (!received) break // 60 秒无回包超时 / socket 被关闭
                flow.lastActiveMs = SystemClock.elapsedRealtime()
                val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                runCatching { sendUdpBack(flow, data) }
            }
            runCatching { flow.socket.close() }
            if (udpFlows[flow.key] === flow) udpFlows.remove(flow.key)
        }, "udp-relay-${flow.clientPort}")
        thread.isDaemon = true
        thread.start()
    }

    private fun sendUdpBack(flow: UdpFlow, payload: ByteArray) {
        if (payload.size + 8 > 65535) return
        val seg = ByteArray(8 + payload.size)
        writeU16(seg, 0, flow.remotePort)
        writeU16(seg, 2, flow.clientPort)
        writeU16(seg, 4, seg.size)
        writeU16(seg, 6, 0)
        System.arraycopy(payload, 0, seg, 8, payload.size)
        writeTransportChecksum(seg, flow.remoteIp, LOCAL_IP, 17, 6)
        writeToTun(buildIpv4Packet(flow.remoteIp, LOCAL_IP, 17, seg))
    }

    /** 每 5 秒回收空闲超过 60 秒的流,防止转发表无限增长 */
    private fun cleanLoop() {
        while (running) {
            try {
                Thread.sleep(CLEAN_INTERVAL_MS)
            } catch (_: InterruptedException) {
                break
            }
            if (!running) break
            val now = SystemClock.elapsedRealtime()
            tcpFlows.values.toList().forEach { flow ->
                if (now - flow.lastActiveMs > FLOW_IDLE_TIMEOUT_MS) closeTcpFlow(flow)
            }
            udpFlows.values.toList().forEach { flow ->
                if (now - flow.lastActiveMs > FLOW_IDLE_TIMEOUT_MS) {
                    flow.closed = true
                    runCatching { flow.socket.close() }
                    if (udpFlows[flow.key] === flow) udpFlows.remove(flow.key)
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 报文构造与校验和
    // ══════════════════════════════════════════════════════════════════════

    private fun buildIpv4Packet(
        srcIp: Int,
        dstIp: Int,
        protocol: Int,
        segment: ByteArray,
    ): ByteArray {
        val total = 20 + segment.size
        val packet = ByteArray(total)
        packet[0] = 0x45 // IPv4, IHL = 5
        packet[1] = 0
        writeU16(packet, 2, total)
        writeU16(packet, 4, 0)
        writeU16(packet, 6, 0x4000) // DF
        packet[8] = 64 // TTL
        packet[9] = protocol.toByte()
        writeU16(packet, 10, 0)
        writeIp(packet, 12, srcIp)
        writeIp(packet, 16, dstIp)
        writeU16(packet, 10, checksum16(packet, 0, 20))
        System.arraycopy(segment, 0, packet, 20, segment.size)
        return packet
    }

    private fun buildTcpSegment(
        srcPort: Int,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        payload: ByteArray,
    ): ByteArray {
        val seg = ByteArray(20 + payload.size)
        writeU16(seg, 0, srcPort)
        writeU16(seg, 2, dstPort)
        writeU32(seg, 4, seq)
        writeU32(seg, 8, ack)
        seg[12] = (5 shl 4).toByte() // data offset = 5 → 20 字节头
        seg[13] = flags.toByte()
        writeU16(seg, 14, 0xFFFF) // 窗口 65535
        writeU16(seg, 16, 0)
        writeU16(seg, 18, 0)
        System.arraycopy(payload, 0, seg, 20, payload.size)
        return seg
    }

    /** 伪首部 + 传输层校验和;UDP 结果为 0 时写 0xFFFF(RFC 768) */
    private fun writeTransportChecksum(
        segment: ByteArray,
        srcIp: Int,
        dstIp: Int,
        protocol: Int,
        checksumOffset: Int,
    ) {
        writeU16(segment, checksumOffset, 0)
        var value = transportChecksum(srcIp, dstIp, protocol, segment)
        if (protocol == 17 && value == 0) value = 0xFFFF
        writeU16(segment, checksumOffset, value)
    }

    private fun transportChecksum(
        srcIp: Int,
        dstIp: Int,
        protocol: Int,
        segment: ByteArray,
    ): Int {
        var sum = 0L
        sum += ((srcIp ushr 16) and 0xFFFF).toLong()
        sum += (srcIp and 0xFFFF).toLong()
        sum += ((dstIp ushr 16) and 0xFFFF).toLong()
        sum += (dstIp and 0xFFFF).toLong()
        sum += protocol.toLong()
        sum += segment.size.toLong()
        var i = 0
        while (i + 1 < segment.size) {
            sum += (((segment[i].toInt() and 0xFF) shl 8) or
                (segment[i + 1].toInt() and 0xFF)).toLong()
            i += 2
        }
        if (i < segment.size) sum += ((segment[i].toInt() and 0xFF) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun checksum16(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += (((data[i].toInt() and 0xFF) shl 8) or
                (data[i + 1].toInt() and 0xFF)).toLong()
            i += 2
        }
        if (i < end) sum += ((data[i].toInt() and 0xFF) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    /**
     * 写回一个 IPv4 包到 TUN
     *
     * 直接走 [Os.write] 而不是 FileOutputStream:fd 只由 [ParcelFileDescriptor]
     * 持有,停机时关闭只有一个所有者,不会出现流与 PFD 重复关闭同一 fd 的问题。
     */
    private fun writeToTun(packet: ByteArray) {
        val pfd = tun ?: return
        runCatching {
            synchronized(tunWriteLock) {
                Os.write(pfd.fileDescriptor, packet, 0, packet.size)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 通知
    // ══════════════════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "流量监视",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "VpnService 本机流量抓包与统计"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startForegroundSafely() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ 必须显式声明前台服务类型,specialUse 与 Manifest 声明一致
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
            .onFailure {
                _state.value = _state.value.copy(
                    error = "[!] 前台通知启动失败(检查 Manifest 前台服务类型/权限)—— " +
                        it.javaClass.simpleName,
                )
            }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, PacketCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val piStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val piOpen = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("流量监视运行中")
            .setContentText("VpnService 本机抓包 · 点按查看")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(piOpen)
            .addAction(0, "停止", piStop)
            .build()
    }

    // ══════════════════════════════════════════════════════════════════════
    // 字节工具
    // ══════════════════════════════════════════════════════════════════════

    private fun u8(b: ByteArray, i: Int): Int = b[i].toInt() and 0xFF

    private fun u16(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, i: Int): Long =
        ((b[i].toLong() and 0xFF) shl 24) or
            ((b[i + 1].toLong() and 0xFF) shl 16) or
            ((b[i + 2].toLong() and 0xFF) shl 8) or
            (b[i + 3].toLong() and 0xFF)

    private fun ipBytes(ip: Int): ByteArray = byteArrayOf(
        ((ip ushr 24) and 0xFF).toByte(),
        ((ip ushr 16) and 0xFF).toByte(),
        ((ip ushr 8) and 0xFF).toByte(),
        (ip and 0xFF).toByte(),
    )

    private fun writeU16(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = ((value ushr 8) and 0xFF).toByte()
        dst[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeU32(dst: ByteArray, offset: Int, value: Long) {
        dst[offset] = ((value ushr 24) and 0xFF).toByte()
        dst[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        dst[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        dst[offset + 3] = (value and 0xFF).toByte()
    }

    private fun writeIp(dst: ByteArray, offset: Int, ip: Int) {
        dst[offset] = ((ip ushr 24) and 0xFF).toByte()
        dst[offset + 1] = ((ip ushr 16) and 0xFF).toByte()
        dst[offset + 2] = ((ip ushr 8) and 0xFF).toByte()
        dst[offset + 3] = (ip and 0xFF).toByte()
    }

    // ══════════════════════════════════════════════════════════════════════
    // 全局状态(companion:服务不在时页面也能安全读取空状态)
    // ══════════════════════════════════════════════════════════════════════

    companion object {
        const val CHANNEL_ID = "packet_monitor"
        const val NOTIFICATION_ID = 9101
        const val ACTION_STOP = "xin.ctkqiang.deauthctrl.PACKET_MONITOR_STOP"

        private const val SESSION_NAME = "DeauthCtrl Packet Monitor"
        private const val LOCAL_IP_TEXT = "10.8.0.1"
        private const val LOCAL_IP = 0x0A080001 // 10.8.0.1
        private const val MTU = 1500
        private const val READ_BUFFER_SIZE = 4096
        private const val RELAY_BUFFER_SIZE = 4096
        private const val RING_CAPACITY = 500
        private const val MAX_DOMAINS = 2000
        private const val PUBLISH_INTERVAL_MS = 150L
        private const val FLOW_IDLE_TIMEOUT_MS = 60_000L
        private const val CLEAN_INTERVAL_MS = 5_000L
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val MAX_TCP_FLOWS = 128
        private const val MAX_UDP_FLOWS = 256
        private const val MAX_PENDING_BYTES = 64 * 1024

        /** poll 超时兜底:即使自管道信号丢失,读线程也最多 400ms 后检查 running */
        private const val POLL_TIMEOUT_MS = 400
        private const val CAPTURE_JOIN_TIMEOUT_MS = 700L
        private const val CLEANER_JOIN_TIMEOUT_MS = 300L

        /** 聚合上限 —— 防止长时间抓包把内存撑爆 */
        private const val MAX_PEER_STATS = 512
        private const val MAX_SESSIONS = 512
        private const val MAX_SESSION_DOMAINS = 24
        private const val MAX_SESSIONS_PUBLISHED = 100
        private const val TOP_DOMAIN_LIMIT = 10
        private const val TOP_PEER_LIMIT = 10

        private val SEQ_MASK = 0xFFFFFFFFL
        private val TCP_FIN = 0x01
        private val TCP_SYN = 0x02
        private val TCP_RST = 0x04
        private val TCP_PSH = 0x08
        private val TCP_ACK = 0x10
        private val EMPTY_BYTES = ByteArray(0)
        private val WAKE_BYTES = byteArrayOf(1)

        @Volatile
        var instance: PacketCaptureService? = null
            private set

        private val _state = MutableStateFlow(PacketMonitorState())

        /** 页面观察的全局状态;服务未启动时为空闲态 */
        val state: StateFlow<PacketMonitorState> = _state

        val isRunning: Boolean get() = _state.value.running

        /**
         * 立即停止 —— 同一进程内先直接让实例拆隧道并翻状态,再调用系统
         * stopService 回收 ServiceRecord。
         *
         * 两件事都做是刻意的:实例直调保证「点停止后 VPN 立刻消失」,
         * stopService 兜底保证即使实例引用丢失也能清掉服务。
         */
        fun requestStop(context: Context) {
            val app = context.applicationContext
            instance?.let {
                it.stopCapture()
                it.stopSelf()
            }
            runCatching {
                app.stopService(Intent(app, PacketCaptureService::class.java))
            }
        }

        /** 清空包列表、会话聚合与本次会话计数 */
        fun clearPackets() {
            instance?.clearPacketRing()
            _state.value = _state.value.copy(
                packets = emptyList(),
                totalPackets = 0L,
                totalBytes = 0L,
                domains = emptySet(),
                domainCounts = emptyList(),
                peerCounts = emptyList(),
                protocolCounts = emptyMap(),
                sessions = emptyList(),
                sessionCount = 0,
            )
        }
    }
}
