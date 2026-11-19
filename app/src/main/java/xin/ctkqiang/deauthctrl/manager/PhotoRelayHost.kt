package xin.ctkqiang.deauthctrl.manager

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.MainActivity
import xin.ctkqiang.deauthctrl.R
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random

/** 收到的一张战术照片 —— JPEG 原始字节 + 元数据 */
data class ReceivedPhoto(
    val id: Long,
    val from: String,
    val width: Int,
    val height: Int,
    val jpeg: ByteArray,
    val receivedMs: Long,
    val savedToGallery: Boolean = false,
)

/** 在线的照片接收台(其他设备) */
data class PhotoPeer(
    val id: String,
    val name: String,
    val ip: String,
    val lastSeenMs: Long,
    /** TCP 接收端口 —— 10088 被占用时对方会回退到随机端口,必须按信标声明值连接 */
    val port: Int = PhotoRelayHost.TCP_PORT,
)

/** 一次发送目标(自动发现或手动指定) */
data class PhotoTarget(
    val ip: String,
    val port: Int = PhotoRelayHost.TCP_PORT,
    val id: String = ip,
    val name: String = ip,
)

/** 单个目标的发送结果 —— UI 据此显示「送达 x/n」而不是假成功 */
data class SendResult(
    val ip: String,
    val ok: Boolean,
    val detail: String,
)

/**
 * 发现状态机 —— UI 按阶段显示,不再只有一个 online 布尔。
 */
sealed class DiscoveryState {
    data object Idle : DiscoveryState()
    data class Searching(val stage: String, val elapsedMs: Long, val found: Int) : DiscoveryState()
    data class Found(val count: Int) : DiscoveryState()
    data class Exhausted(val lastSweepAtMs: Long, val reason: String) : DiscoveryState()
}

/** 每层发现的独立状态(mDNS / UDP / 缓存 / 网段扫描) */
data class DiscoveryLayers(
    val mdns: String = L10n.get(R.string.mgr_relay_layer_not_started),
    val udp: String = L10n.get(R.string.mgr_relay_layer_not_started),
    val cacheProbe: String = L10n.get(R.string.mgr_relay_layer_not_probed),
    val subnetScan: String = L10n.get(R.string.mgr_relay_layer_not_begun),
    val scanned: Int = 0,
    val scanTotal: Int = 0,
)

/**
 * 战术照片中继主机 —— 应用进程级单例
 *
 * 接收台(TCP)与发现栈(NSD + UDP 广播 + 缓存/网段探测)随应用进程存活,
 * 进程被杀后停止 —— 没有前台服务,不承诺常开。
 *
 * 发现分三层,任何一层成功即可发现对端:
 * 1. **NSD/mDNS**: 注册 `_deauthphoto._tcp.`,TXT 携带 id/name/port;
 * 2. **UDP 广播**: 9997 端口广播 `PHOTO|id|name|tcpPort|v`,并按网卡定向广播;
 * 3. **回退探测**: 历史 peer 单播 + /24 网段 TCP 扫描,只把「收到 PHOTO 身份回应」
 *    的设备计入在线,避免 DHCP 换 IP 后误连。
 *
 * 平台限制(如实告知用户): UDP 广播/mDNS 不保证被 AP 转发;AP 客户端隔离或 VPN
 * 会让广播、mDNS、TCP 扫描与手动 IP 全部失效,应用层无法绕过。
 */
class PhotoRelayHost private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences("deauthctrl_prefs", Context.MODE_PRIVATE)

    /** 本机中继 ID —— 首次生成后持久化 */
    val deviceId: String = prefs.getString(KEY_ID, null) ?: run {
        val id = (1..6).map { "0123456789ABCDEF"[Random.nextInt(16)] }.joinToString("")
        prefs.edit().putString(KEY_ID, id).apply()
        id
    }

    /** 本机呼号 —— 相机页进入时注入 */
    @Volatile var deviceName: String = "operator"

    private val _peers = MutableStateFlow<List<PhotoPeer>>(emptyList())
    val peers: StateFlow<List<PhotoPeer>> = _peers

    private val _received = MutableStateFlow<List<ReceivedPhoto>>(emptyList())
    val received: StateFlow<List<ReceivedPhoto>> = _received

    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online

    /** 实际 TCP 监听端口:10088 被占时回退随机端口 */
    private val _serverPort = MutableStateFlow(TCP_PORT)
    val serverPort: StateFlow<Int> = _serverPort

    private val _discovery = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discovery: StateFlow<DiscoveryState> = _discovery

    private val _layers = MutableStateFlow(DiscoveryLayers())
    val layers: StateFlow<DiscoveryLayers> = _layers

    private val peerMap = ConcurrentHashMap<String, PhotoPeer>()

    // ─── 生命周期状态 ───
    @Volatile private var stopFlag = false
    @Volatile private var started = false
    @Volatile private var stoppedExplicitly = false
    private val startLock = Any()
    private var startRetryIndex = 0

    @Volatile private var beaconSocket: DatagramSocket? = null
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var beaconNow = false
    @Volatile private var discoveryActive = false
    @Volatile private var discoveryStartedAt = 0L

    @Volatile private var clientPool = Executors.newFixedThreadPool(4)

    // ─── NSD ───
    private val nsdManager by lazy {
        appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }
    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile private var discoveryListener: NsdManager.DiscoveryListener? = null
    @Volatile private var nsdDiscovering = false
    private val resolveQueue = ArrayDeque<String>()
    private val resolveLock = Any()
    private val pendingServices = Collections.synchronizedMap(LinkedHashMap<String, NsdServiceInfo>())
    private val servicePeerIds = ConcurrentHashMap<String, String>()
    @Volatile private var resolving = false

    // ─── MulticastLock ───
    @Volatile private var multicastLock: WifiManager.MulticastLock? = null

    // ─── 网络回调 ───
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleDiscoveryRestart()
        override fun onLost(network: Network) = scheduleDiscoveryRestart()
    }
    @Volatile private var lastNetworkRestartAt = 0L

    // ─── 回退探测 ───
    private var cachedPeerCount = 0
    @Volatile private var scanRunning = false
    @Volatile private var scanCancelled = false
    private var scanExecutor: java.util.concurrent.ExecutorService? = null
    private var lastScanAttemptAt = 0L
    private val scannedCount = AtomicInteger(0)
    private val lastReplyAt = ConcurrentHashMap<String, Long>()

    /** 启动常开中继(幂等):绑定失败按 1s/5s/15s 退避重试 */
    fun start() {
        synchronized(startLock) {
            if (started) return
            stoppedExplicitly = false
            stopFlag = false
            createNotificationChannel()

            val beacon = try {
                // reuseAddress 必须在 bind 之前设置,否则第二次绑定不会生效
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(BEACON_PORT))
                    soTimeout = 800
                }
            } catch (t: Throwable) {
                scheduleRetry(L10n.get(R.string.mgr_relay_err_udp_bind, BEACON_PORT, t.message ?: t.javaClass.simpleName))
                return
            }

            val server = try {
                try {
                    ServerSocket(TCP_PORT)
                } catch (_: Throwable) {
                    // 10088 被其它应用占用 —— 回退随机端口并通过信标声明实际端口
                    ServerSocket(0)
                }
            } catch (t: Throwable) {
                runCatching { beacon.close() }
                scheduleRetry(L10n.get(R.string.mgr_relay_err_tcp_bind, t.message ?: t.javaClass.simpleName))
                return
            }

            beaconSocket = beacon
            serverSocket = server
            if (clientPool.isShutdown) clientPool = Executors.newFixedThreadPool(4)
            val port = server.localPort
            _serverPort.value = port
            _online.value = true
            started = true
            startRetryIndex = 0
            updateLayer {
                it.copy(udp = L10n.get(R.string.mgr_relay_udp_running, BEACON_PORT, port))
            }

            thread(name = "photo-beacon-send", isDaemon = true) { beaconLoop(beacon) }
            thread(name = "photo-beacon-listen", isDaemon = true) { beaconListen(beacon) }
            thread(name = "photo-tcp-accept", isDaemon = true) { acceptLoop(server) }
            thread(name = "photo-housekeeper", isDaemon = true) { housekeeperLoop() }
            registerNsd(port)
            registerNetworkCallback()
            beaconNow = true
        }
    }

    /** 启动失败:关闭已创建资源,按 1s/5s/15s 退避重试 */
    private fun scheduleRetry(reason: String) {
        _online.value = false
        updateLayer { it.copy(udp = L10n.get(R.string.mgr_relay_udp_unavailable, reason)) }
        val delays = longArrayOf(1000L, 5000L, 15000L)
        val delayMs = delays[startRetryIndex.coerceIn(0, delays.lastIndex)]
        if (startRetryIndex < delays.lastIndex) startRetryIndex++
        thread(name = "photo-retry", isDaemon = true) {
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                return@thread
            }
            if (!stoppedExplicitly) start()
        }
    }

    /** 幂等停止(仅显式调用;页面离开只调 stopDiscovery) */
    fun stop() {
        val beacon: DatagramSocket?
        val server: ServerSocket?
        synchronized(startLock) {
            stoppedExplicitly = true
            if (!started && beaconSocket == null && serverSocket == null) return
            stopFlag = true
            started = false
            beacon = beaconSocket
            server = serverSocket
            beaconSocket = null
            serverSocket = null
            _online.value = false
        }
        stopDiscovery()
        runCatching { beacon?.close() }
        runCatching { server?.close() }
        runCatching { registrationListener?.let { nsdManager?.unregisterService(it) } }
        registrationListener = null
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        }
        cancelSubnetScan()
        clientPool.shutdownNow()
        peerMap.clear()
        _peers.value = emptyList()
        updateLayer {
            DiscoveryLayers(
                mdns = L10n.get(R.string.mgr_relay_layer_stopped),
                udp = L10n.get(R.string.mgr_relay_layer_stopped),
            )
        }
        _discovery.value = DiscoveryState.Idle
    }

    fun isStarted(): Boolean = started

    /** 相机页进入时调用:注入呼号,立即发一次信标 */
    fun touch(name: String) {
        val trimmed = name.ifBlank { "operator" }
        val changed = trimmed != deviceName
        deviceName = trimmed
        if (!started) start()
        _online.value = serverSocket?.isBound == true
        if (changed || started) beaconNow = true
    }

    // ══════════════════════════════════════════════════════════════════════
    // 发送:4 字节大端长度前缀 + JSON,接收端返回 2 字节 OK 作为送达确认
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 发送到指定 IP/端口。失败自动重试 1 次(600ms 退避),仍失败则立即单播重探。
     * 返回结构化结果,由调用方聚合 —— 多目标全失败不会再显示「已发送」。
     */
    fun sendTo(
        ip: String,
        jpeg: ByteArray,
        senderName: String,
        width: Int,
        height: Int,
        port: Int = TCP_PORT,
    ): SendResult {
        // minSdk 23:必须用 android.util.Base64(java.util.Base64 需要 API 26)
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val payload = buildString {
            append("{\"type\":\"photo\",\"from\":\"")
            append(senderName.replace("\"", "\\\"").replace("\\", "\\\\"))
            append("\",\"w\":").append(width)
            append(",\"h\":").append(height)
            append(",\"b64\":\"").append(b64).append("\"}")
        }
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_PAYLOAD) {
            return SendResult(ip, false, L10n.get(R.string.mgr_relay_send_too_large, bytes.size / 1024))
        }

        var lastError = L10n.get(R.string.mgr_relay_unknown_error)
        repeat(2) { attempt ->
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(ip, port), 4000)
                    sock.soTimeout = 1500
                    // 注意:不能 use{} 关闭这条输出流 —— Socket 任一方向流 close() 都会关闭整个 socket,
                    // 后面的 OK 回执就读不到了。只 flush,由外层 use 统一关闭。
                    val out = DataOutputStream(sock.getOutputStream())
                    out.writeInt(bytes.size)
                    out.write(bytes)
                    out.flush()
                    // 应用层送达确认:没有 OK 回执说明对端没落盘,不能算已发送
                    val ack = ByteArray(2)
                    val n = sock.getInputStream().read(ack)
                    if (n != 2 || String(ack, StandardCharsets.US_ASCII) != "OK") {
                        throw java.io.IOException(L10n.get(R.string.mgr_relay_err_no_ack))
                    }
                }
                return SendResult(ip, true, L10n.get(R.string.mgr_relay_send_ok, jpeg.size / 1024, ip))
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
                if (attempt == 0) {
                    try {
                        Thread.sleep(600)
                    } catch (_: InterruptedException) {
                        return SendResult(ip, false, L10n.get(R.string.mgr_relay_send_cancelled))
                    }
                }
            }
        }
        // 失败目标立即单播重探,便于对方下一次出现在在线列表
        sendQuery(ip)
        return SendResult(ip, false, L10n.get(R.string.mgr_relay_send_failed, lastError))
    }

    // ══════════════════════════════════════════════════════════════════════
    // 发现:页面激活时启动,进程级 housekeeper 独立清理过期 peer
    // ══════════════════════════════════════════════════════════════════════

    /** 相机页进入时调用:获取 MulticastLock、启动 mDNS、探测缓存、立即广播 */
    fun startDiscovery() {
        if (!started) start()
        discoveryActive = true
        discoveryStartedAt = SystemClock.elapsedRealtime()
        lastScanAttemptAt = 0L
        scanCancelled = false
        acquireMulticastLock()
        startNsdDiscovery()
        probeCachedPeers()
        beaconNow = true
        updateLayer {
            it.copy(
                udp = if (started) {
                    L10n.get(R.string.mgr_relay_udp_running, BEACON_PORT, _serverPort.value)
                } else {
                    L10n.get(R.string.mgr_relay_udp_unavailable_retrying)
                },
            )
        }
        publishDiscovery(SystemClock.elapsedRealtime())
    }

    /** 相机页离开时调用:释放 MulticastLock、停止 mDNS 搜索与扫描 */
    fun stopDiscovery() {
        discoveryActive = false
        stopNsdDiscovery()
        releaseMulticastLock()
        cancelSubnetScan()
        _discovery.value = DiscoveryState.Idle
    }

    /** 「重新搜索」:清空阶段状态、绕过扫描节流、重新发 PHOTO? */
    fun rescan() {
        startDiscovery()
        lastScanAttemptAt = 0L
        scanRunning = false
        cancelSubnetScan()
        updateLayer {
            it.copy(
                cacheProbe = L10n.get(R.string.mgr_relay_cache_probing),
                subnetScan = L10n.get(R.string.mgr_relay_scan_preparing),
            )
        }
        probeCachedPeers()
        maybeStartSubnetScan(SystemClock.elapsedRealtime(), force = true)
    }

    private fun beaconMessage(): String =
        "PHOTO|$deviceId|${sanitizeName(deviceName)}|${_serverPort.value}|2"

    private fun sanitizeName(raw: String): String {
        val cleaned = raw.replace('|', '_').replace('\n', '_').replace('\r', '_').trim()
        // 信标总长受 512 字节缓冲限制,呼号最多 32 字节
        val bytes = cleaned.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size <= 32) return cleaned
        var end = cleaned.length
        while (end > 0 && cleaned.substring(0, end).toByteArray(StandardCharsets.UTF_8).size > 32) end--
        return cleaned.substring(0, end)
    }

    private fun beaconLoop(sock: DatagramSocket) {
        var nextAt = 0L
        while (!stopFlag && !sock.isClosed) {
            try {
                val now = SystemClock.elapsedRealtime()
                if (beaconNow || now >= nextAt) {
                    val immediate = beaconNow
                    beaconNow = false
                    sendBeacon(sock)
                    // 周期信标 2s + 0..300ms 抖动,避免多机同拍碰撞
                    nextAt = now + 2000 + if (immediate) 0 else Random.nextLong(0, 301)
                }
                Thread.sleep(80)
            } catch (_: InterruptedException) {
                break
            } catch (_: Throwable) {
                if (!stopFlag) Thread.sleep(200)
            }
        }
    }

    private fun sendBeacon(sock: DatagramSocket) {
        val bytes = beaconMessage().toByteArray(StandardCharsets.UTF_8)
        val targets = mutableListOf<InetAddress>()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                nif.interfaceAddresses.forEach { ia ->
                    val b = ia.broadcast
                    if (b is Inet4Address) targets.add(b)
                }
            }
        }
        targets.add(runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull() ?: return)
        val sent = targets.distinctBy { it.hostAddress }
        var ok = 0
        sent.forEach { addr ->
            val packet = DatagramPacket(bytes, bytes.size, addr, BEACON_PORT)
            if (runCatching { sock.send(packet) }.isSuccess) ok++
        }
        updateLayer {
            it.copy(
                udp = L10n.get(
                    R.string.mgr_relay_udp_running_directed,
                    BEACON_PORT,
                    ok,
                    _serverPort.value,
                ),
            )
        }
    }

    /** 单播查询:让指定 IP 立即回信标(缓存探测/扫描候选/发送失败重探共用) */
    private fun sendQuery(ip: String) {
        val sock = beaconSocket ?: return
        val target = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return
        val bytes = "PHOTO?|$deviceId".toByteArray(StandardCharsets.UTF_8)
        runCatching { sock.send(DatagramPacket(bytes, bytes.size, target, BEACON_PORT)) }
    }

    private fun beaconListen(sock: DatagramSocket) {
        val buf = ByteArray(512)
        while (!stopFlag && !sock.isClosed) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                sock.receive(packet)
                val text = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8).trim()
                handleBeacon(text, packet.address, packet.port)
            } catch (_: SocketTimeoutException) {
                // 1s 超时只为让循环及时响应 stopFlag/housekeeper
            } catch (_: Throwable) {
                if (!stopFlag) Thread.sleep(50)
            }
        }
    }

    private fun handleBeacon(text: String, address: InetAddress?, srcPort: Int) {
        if (!text.startsWith("PHOTO")) return
        val parts = text.split("|")
        if (parts[0] == "PHOTO?") {
            // 对端询问:单播回发本机信标(每源 1 次/秒限速)
            val askId = parts.getOrNull(1).orEmpty()
            if (askId == deviceId) return
            val ip = address?.hostAddress ?: return
            replyBeacon(ip)
            return
        }
        if (parts.size < 3) return
        val id = parts[1].trim()
        if (id.isEmpty() || id == deviceId) return
        val name = parts[2].trim().take(32).ifBlank { L10n.get(R.string.mgr_relay_peer_default_name) }
        val port = parts.getOrNull(3)?.toIntOrNull()?.takeIf { it in 1..65535 } ?: TCP_PORT
        val ip = address?.hostAddress ?: return
        val isNew = peerMap[id] == null
        upsertPeer(id, name, ip, port)
        if (isNew) beaconNow = true // 对方第一次出现:立刻回信标,实现双向快速发现
    }

    private fun replyBeacon(ip: String) {
        val now = SystemClock.elapsedRealtime()
        val last = lastReplyAt[ip] ?: 0L
        if (now - last < 1000L) return
        lastReplyAt[ip] = now
        val sock = beaconSocket ?: return
        val target = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return
        val bytes = beaconMessage().toByteArray(StandardCharsets.UTF_8)
        runCatching { sock.send(DatagramPacket(bytes, bytes.size, target, BEACON_PORT)) }
    }

    private fun upsertPeer(id: String, name: String, ip: String, port: Int) {
        val now = System.currentTimeMillis()
        peerMap[id] = PhotoPeer(id, name, ip, now, port)
        cachePeer()
        publishPeers()
    }

    private fun publishPeers() {
        _peers.value = peerMap.values.sortedWith(compareBy({ it.name }, { it.id }))
    }

    private fun cachePeer() {
        runCatching {
            val arr = org.json.JSONArray()
            peerMap.values.take(CACHE_MAX).forEach { p ->
                arr.put(
                    org.json.JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("ip", p.ip)
                        put("port", p.port)
                        put("lastSeen", p.lastSeenMs)
                    },
                )
            }
            prefs.edit().putString(KEY_PEER_CACHE, arr.toString()).apply()
        }
    }

    private fun readCachedPeers(): List<PhotoPeer> {
        val raw = prefs.getString(KEY_PEER_CACHE, null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val seen = o.optLong("lastSeen")
                if (System.currentTimeMillis() - seen > CACHE_TTL_MS) return@mapNotNull null
                PhotoPeer(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    ip = o.optString("ip"),
                    lastSeenMs = seen,
                    port = o.optInt("port", TCP_PORT),
                )
            }.filter { it.id.isNotBlank() && it.ip.isNotBlank() }.take(CACHE_MAX)
        }.getOrDefault(emptyList())
    }

    /** 独立 housekeeper:不依赖收包,每秒清理 TTL 并发布状态 */
    private fun housekeeperLoop() {
        while (!stopFlag) {
            try {
                val nowWall = System.currentTimeMillis()
                val before = peerMap.size
                peerMap.entries.removeIf { nowWall - it.value.lastSeenMs > PEER_TTL_MS }
                if (peerMap.size != before) publishPeers()
                publishDiscovery(SystemClock.elapsedRealtime())
                maybeStartSubnetScan(SystemClock.elapsedRealtime())
                purgeExpiredPhotos(nowWall)
                Thread.sleep(1000)
            } catch (_: InterruptedException) {
                break
            } catch (_: Throwable) {
                if (!stopFlag) Thread.sleep(500)
            }
        }
    }

    /**
     * 照片 TTL:接收的照片只存于应用私有目录,超时自动删除内存条目与文件 ——
     * 除非用户已主动下载保存到相册(保存过的永久保留)。
     */
    private fun purgeExpiredPhotos(nowWall: Long) {
        val photos = _received.value
        if (photos.none { !it.savedToGallery && nowWall - it.receivedMs > PHOTO_TTL_MS }) return
        val dir = File(appContext.filesDir, "photo_relay")
        _received.update { list ->
            list.mapNotNull { p ->
                if (!p.savedToGallery && nowWall - p.receivedMs > PHOTO_TTL_MS) {
                    runCatching { File(dir, "rx_${p.id}.jpg").delete() }
                    null
                } else p
            }
        }
    }

    /**
     * 下载照片到系统相册(Pictures/DeauthCtrl) —— 用户主动保存的照片不受 TTL 清理。
     * @return 是否保存成功
     */
    fun savePhotoToGallery(id: Long): Boolean {
        val photo = _received.value.firstOrNull { it.id == id } ?: return false
        val src = File(appContext.filesDir, "photo_relay/rx_${id}.jpg")
        if (!src.exists()) return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "deauth_${id}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DeauthCtrl")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = appContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: return false
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                appContext.contentResolver.update(uri, values, null, null)
            }
            _received.update { list -> list.map { if (it.id == id) it.copy(savedToGallery = true) else it } }
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun publishDiscovery(nowElapsed: Long) {
        if (!discoveryActive) return
        val count = peerMap.size
        if (count > 0) {
            _discovery.value = DiscoveryState.Found(count)
            return
        }
        val elapsed = (nowElapsed - discoveryStartedAt).coerceAtLeast(0)
        _discovery.value = if (elapsed >= EXHAUSTED_AFTER_MS) {
            DiscoveryState.Exhausted(
                System.currentTimeMillis(),
                L10n.get(R.string.mgr_relay_exhausted_reason),
            )
        } else {
            DiscoveryState.Searching(stageOf(), elapsed, 0)
        }
    }

    private fun stageOf(): String = when {
        peerMap.isNotEmpty() -> L10n.get(R.string.mgr_relay_stage_found, peerMap.size)
        scanRunning -> L10n.get(R.string.mgr_relay_stage_subnet_scan)
        cachedPeerCount > 0 -> L10n.get(R.string.mgr_relay_stage_cache_probe)
        nsdDiscovering -> L10n.get(R.string.mgr_relay_stage_mdns)
        else -> L10n.get(R.string.mgr_relay_stage_udp)
    }

    private fun updateLayer(block: (DiscoveryLayers) -> DiscoveryLayers) {
        while (true) {
            val cur = _layers.value
            val next = block(cur)
            if (_layers.compareAndSet(cur, next)) return
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // NSD / mDNS
    // ══════════════════════════════════════════════════════════════════════

    private fun registerNsd(port: Int) {
        val manager = nsdManager ?: run {
            updateLayer { it.copy(mdns = L10n.get(R.string.mgr_relay_mdns_no_nsd)) }
            return
        }
        runCatching {
            val info = NsdServiceInfo().apply {
                serviceName = "dpc-$deviceId"
                serviceType = NSD_TYPE
                setPort(port)
                setAttribute("id", deviceId)
                setAttribute("name", sanitizeName(deviceName))
                setAttribute("port", port.toString())
                setAttribute("v", "2")
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    updateLayer { it.copy(mdns = L10n.get(R.string.mgr_relay_mdns_registered, serviceInfo.serviceName)) }
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    updateLayer { it.copy(mdns = L10n.get(R.string.mgr_relay_mdns_register_failed, errorCode)) }
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            registrationListener = listener
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            updateLayer { it.copy(mdns = L10n.get(R.string.mgr_relay_mdns_registering)) }
        }.onFailure { e ->
            updateLayer { it.copy(mdns = L10n.get(R.string.mgr_relay_mdns_unavailable, e.message ?: e.javaClass.simpleName)) }
        }
    }

    private fun startNsdDiscovery() {
        val manager = nsdManager ?: return
        if (nsdDiscovering) return
        runCatching {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    nsdDiscovering = true
                    updateLayer { it.copy(mdns = L10n.get(R.string.mgr_relay_mdns_searching)) }
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceType == NSD_TYPE) enqueueResolve(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val peerId = servicePeerIds.remove(serviceInfo.serviceName) ?: return
                    peerMap.remove(peerId)?.let { publishPeers() }
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    nsdDiscovering = false
                    updateLayer { it.copy(mdns = L10n.get(R.string.mgr_relay_mdns_stopped)) }
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    nsdDiscovering = false
                    updateLayer { it.copy(mdns = L10n.get(R.string.mgr_relay_mdns_unavailable_code, errorCode)) }
                    runCatching { manager.stopServiceDiscovery(this) }
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    nsdDiscovering = false
                }
            }
            discoveryListener = listener
            manager.discoverServices(NSD_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            updateLayer { it.copy(mdns = L10n.get(R.string.mgr_relay_mdns_starting)) }
        }.onFailure { e ->
            nsdDiscovering = false
            updateLayer { it.copy(mdns = L10n.get(R.string.mgr_relay_mdns_start_failed, e.message ?: e.javaClass.simpleName)) }
        }
    }

    private fun stopNsdDiscovery() {
        val manager = nsdManager
        val listener = discoveryListener
        if (manager != null && listener != null) {
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
        nsdDiscovering = false
        synchronized(resolveLock) {
            resolveQueue.clear()
        }
        pendingServices.clear()
    }

    /** resolve 串行队列 —— 同时只允许一个 resolve,避免 FAILURE_ALREADY_ACTIVE */
    private fun enqueueResolve(info: NsdServiceInfo) {
        pendingServices[info.serviceName] = info
        synchronized(resolveLock) {
            if (!resolveQueue.contains(info.serviceName)) resolveQueue.addLast(info.serviceName)
        }
        pumpResolve()
    }

    private fun pumpResolve() {
        val manager = nsdManager ?: return
        val name: String
        synchronized(resolveLock) {
            if (resolving) return
            name = resolveQueue.removeFirstOrNull() ?: return
            resolving = true
        }
        val info = pendingServices[name]
        if (info == null) {
            resolving = false
            pumpResolve()
            return
        }
        runCatching {
            manager.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        resolving = false
                        pumpResolve()
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        handleResolvedService(serviceInfo)
                        resolving = false
                        pumpResolve()
                    }
                },
            )
        }.onFailure {
            resolving = false
            pumpResolve()
        }
    }

    @Suppress("DEPRECATION")
    private fun handleResolvedService(info: NsdServiceInfo) {
        val ip = info.host?.hostAddress ?: return
        val id = String(info.attributes["id"] ?: ByteArray(0), StandardCharsets.UTF_8).trim()
        if (id == deviceId) return
        val name = String(info.attributes["name"] ?: ByteArray(0), StandardCharsets.UTF_8).trim()
        val peerId = id.ifBlank { "nsd-$ip-${info.port}" }
        val port = info.port.takeIf { it > 0 } ?: TCP_PORT
        servicePeerIds[info.serviceName] = peerId
        upsertPeer(peerId, name.ifBlank { L10n.get(R.string.mgr_relay_peer_default_name) }, ip, port)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 回退:缓存单播 + /24 网段扫描
    // ══════════════════════════════════════════════════════════════════════

    private fun probeCachedPeers() {
        val cached = readCachedPeers()
        cachedPeerCount = cached.size
        updateLayer {
            it.copy(
                cacheProbe = if (cached.isEmpty()) {
                    L10n.get(R.string.mgr_relay_cache_none)
                } else {
                    L10n.get(R.string.mgr_relay_cache_count, cached.size)
                },
            )
        }
        cached.forEach { p ->
            thread(name = "photo-cache-probe", isDaemon = true) {
                sendQuery(p.ip)
                // TCP 只算候选;是否在线必须等 PHOTO 身份回应(避免 DHCP 换 IP 误连)
                tryTcp(p.ip, p.port, 300)
            }
        }
    }

    private fun maybeStartSubnetScan(nowElapsed: Long, force: Boolean = false) {
        if (!discoveryActive || peerMap.isNotEmpty() || scanRunning) return
        if (!force && nowElapsed - discoveryStartedAt < SCAN_DELAY_MS) return
        if (!force && nowElapsed - lastScanAttemptAt < SCAN_REPEAT_MS) return
        if (vpnActive()) {
            updateLayer { it.copy(subnetScan = L10n.get(R.string.mgr_relay_scan_vpn_skip)) }
            return
        }
        val subnets = localSubnets()
        if (subnets.isEmpty()) {
            updateLayer { it.copy(subnetScan = L10n.get(R.string.mgr_relay_scan_no_network)) }
            return
        }
        val hosts = subnets.take(2)
            .flatMap { (address, _) -> hostRange24(address) }
            .distinct()
            .take(512)
        if (hosts.isEmpty()) return

        scanRunning = true
        scanCancelled = false
        lastScanAttemptAt = nowElapsed
        scannedCount.set(0)
        updateLayer { it.copy(subnetScan = L10n.get(R.string.mgr_relay_scan_progress_start, hosts.size), scanned = 0, scanTotal = hosts.size) }
        _discovery.value = DiscoveryState.Searching(
            L10n.get(R.string.mgr_relay_stage_scan_progress, hosts.size),
            (nowElapsed - discoveryStartedAt).coerceAtLeast(0),
            0,
        )

        val executor = Executors.newFixedThreadPool(16)
        scanExecutor = executor
        val semaphore = Semaphore(16)
        hosts.forEach { ip ->
            executor.execute {
                if (scanCancelled) return@execute
                semaphore.acquire()
                try {
                    // TCP 成功只算候选,发送 PHOTO? 等对方回信标后才计入 peers
                    if (tryTcp(ip, TCP_PORT, 300)) sendQuery(ip)
                } finally {
                    semaphore.release()
                }
                val done = scannedCount.incrementAndGet()
                if (done % 16 == 0 || done == hosts.size) {
                    updateLayer {
                        it.copy(scanned = done, subnetScan = L10n.get(R.string.mgr_relay_scan_progress, done, hosts.size))
                    }
                }
            }
        }
        executor.shutdown()
        thread(name = "photo-scan-watch", isDaemon = true) {
            runCatching { executor.awaitTermination(60, TimeUnit.SECONDS) }
            scanRunning = false
            updateLayer {
                it.copy(
                    subnetScan = L10n.get(R.string.mgr_relay_scan_done, scannedCount.get(), hosts.size),
                )
            }
        }
    }

    private fun cancelSubnetScan() {
        scanCancelled = true
        scanRunning = false
        runCatching { scanExecutor?.shutdownNow() }
        scanExecutor = null
    }

    @SuppressLint("MissingPermission")
    private fun localSubnets(): List<Pair<Inet4Address, Int>> {
        return runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return emptyList()
            val caps = cm.getNetworkCapabilities(network) ?: return emptyList()
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                return emptyList()
            }
            val lp = cm.getLinkProperties(network) ?: return emptyList()
            lp.linkAddresses.mapNotNull { la ->
                val addr = la.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) addr to la.prefixLength else null
            }
        }.getOrDefault(emptyList())
    }

    private fun vpnActive(): Boolean = runCatching {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false)
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)

    /** 仅扫本机 /24(与计划一致,总上限 512 地址) */
    private fun hostRange24(base: Inet4Address): List<String> {
        val o = base.address
        val prefix = "${o[0].toInt() and 0xFF}.${o[1].toInt() and 0xFF}.${o[2].toInt() and 0xFF}"
        return (1..254).map { "$prefix.$it" }
    }

    private fun tryTcp(ip: String, port: Int, timeoutMs: Int): Boolean = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, port), timeoutMs)
            true
        }
    }.getOrDefault(false)

    // ══════════════════════════════════════════════════════════════════════
    // 接收:线程池限流 + 10s 读超时 + 分配前长度校验
    // ══════════════════════════════════════════════════════════════════════

    private fun acceptLoop(server: ServerSocket) {
        while (!stopFlag && !server.isClosed) {
            try {
                val client = server.accept()
                clientPool.execute { handleClient(client) }
            } catch (_: Throwable) {
                if (!stopFlag) Thread.sleep(100)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            var stored = false
            // 不 use{} 关闭输入流:socket 任一方向流 close() 都会关闭整个 socket,
            // 回执必须还在同一条 socket 上写。读超时/断开由 catch + finally 处理。
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val len = input.readInt()
            // 长度校验发生在分配之前,恶意超长连接不会 OOM
            if (len <= 0 || len > MAX_PAYLOAD) return
            val bytes = ByteArray(len)
            input.readFully(bytes)
            stored = parseAndStore(String(bytes, StandardCharsets.UTF_8))
            if (stored) {
                val out = DataOutputStream(socket.getOutputStream())
                out.write("OK".toByteArray(StandardCharsets.US_ASCII))
                out.flush()
            }
        } catch (_: Exception) {
            // 超时/半包/对端断开 —— 静默结束,不影响接收线程
        } finally {
            runCatching { socket.close() }
        }
    }

    /** 极简 JSON 解析(载荷结构自产自销);返回是否成功落库 */
    private fun parseAndStore(json: String): Boolean {
        fun field(key: String): String =
            Regex("\"$key\":\"(.*?)\"").find(json)?.groupValues?.get(1) ?: ""

        fun intField(key: String): Int =
            Regex("\"$key\":(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val b64 = field("b64")
        if (b64.isEmpty()) return false
        val jpeg = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return false
        val photo = ReceivedPhoto(
            id = System.currentTimeMillis(),
            from = field("from").ifBlank { L10n.get(R.string.mgr_relay_unknown_sender) },
            width = intField("w"),
            height = intField("h"),
            jpeg = jpeg,
            receivedMs = System.currentTimeMillis(),
        )
        runCatching {
            val dir = File(appContext.filesDir, "photo_relay")
            dir.mkdirs()
            File(dir, "rx_${photo.id}.jpg").writeBytes(jpeg)
        }
        // StateFlow.update 是 CAS 原子更新:并发接收照片时不丢条目(读-改-写非原子会互相覆盖)
        _received.update { (listOf(photo) + it).takeLast(30) }
        notifyPhoto(photo)
        return true
    }

    /** 系统通知:大图样式,点击直达相机页并打开该照片 */
    private fun notifyPhoto(photo: ReceivedPhoto) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return // 未授权通知:照片仍已接收与保存,仅静默
            }
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_PHOTO_ID, photo.id)
            }
            val pi = PendingIntent.getActivity(
                appContext,
                photo.id.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val bmp = runCatching { BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size) }.getOrNull()
            val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle(L10n.get(R.string.mgr_relay_notif_title, photo.from))
                .setContentText(
                    L10n.get(
                        R.string.mgr_relay_notif_text,
                        photo.width,
                        photo.height,
                        photo.jpeg.size / 1024,
                    ),
                )
                .setAutoCancel(true)
                .setContentIntent(pi)
            if (bmp != null) {
                builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp))
            }
            getNotificationManager().notify(photo.id.toInt(), builder.build())
        } catch (_: Throwable) {
        }
    }

    /** 取某张照片(通知点击后相机页定位用) */
    fun photoById(id: Long): ReceivedPhoto? = _received.value.firstOrNull { it.id == id }

    private fun getNotificationManager(): NotificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createNotificationChannel() {
        // minSdk 23:NotificationChannel 是 API 26 类,必须做版本保护
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val ch = NotificationChannel(
                CHANNEL_ID,
                L10n.get(R.string.mgr_relay_notif_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            getNotificationManager().createNotificationChannel(ch)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // MulticastLock / 网络变化
    // ══════════════════════════════════════════════════════════════════════

    private fun acquireMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) return
            val wm = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wm.createMulticastLock("photo-disc").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { e ->
            updateLayer { it.copy(udp = L10n.get(R.string.mgr_relay_udp_multicast_unavailable, e.message ?: e.javaClass.simpleName)) }
        }
    }

    private fun releaseMulticastLock() {
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
    }

    private fun registerNetworkCallback() {
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
        }
    }

    /** Wi-Fi 重连后重新获取 MulticastLock、重启 mDNS、重新广播 */
    private fun scheduleDiscoveryRestart() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNetworkRestartAt < 2000L) return
        lastNetworkRestartAt = now
        thread(name = "photo-net-restart", isDaemon = true) {
            try {
                Thread.sleep(800)
            } catch (_: InterruptedException) {
                return@thread
            }
            if (stopFlag || !discoveryActive) return@thread
            releaseMulticastLock()
            acquireMulticastLock()
            stopNsdDiscovery()
            startNsdDiscovery()
            beaconNow = true
        }
    }

    companion object {
        const val BEACON_PORT = 9997
        const val TCP_PORT = 10088
        const val EXTRA_PHOTO_ID = "photo_relay_photo_id"

        private const val NSD_TYPE = "_deauthphoto._tcp."
        private const val KEY_ID = "photo_relay_id"
        private const val CHANNEL_ID = "photo_relay_channel"
        private const val MAX_PAYLOAD = 16 * 1024 * 1024

        private const val PEER_TTL_MS = 8_000L
        /** 接收照片 TTL:超时未下载自动删除;用户主动保存到相册后永久保留 */
        private const val PHOTO_TTL_MS = 10L * 60 * 1000
        private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val CACHE_MAX = 16
        private const val KEY_PEER_CACHE = "photo_peer_cache"
        private const val SCAN_DELAY_MS = 6_000L
        private const val SCAN_REPEAT_MS = 30_000L
        private const val EXHAUSTED_AFTER_MS = 8_000L

        @Volatile private var instance: PhotoRelayHost? = null

        /**
         * 进程级单例 —— 9997/10088 只能绑定一份。
         * 用 applicationContext 构造,构造后 start();失败会自行退避重试。
         */
        fun get(context: Context): PhotoRelayHost {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: PhotoRelayHost(context.applicationContext).also {
                    instance = it
                    it.start()
                }
            }
        }
    }
}
