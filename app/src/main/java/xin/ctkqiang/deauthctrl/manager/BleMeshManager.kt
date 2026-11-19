package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.LinkedHashMap
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

/**
 * 网格节点 —— 一次广播包即节点存在的证明
 *
 * @property id       对方短 ID（6 位大写字母数字）
 * @property rssi     最近一次收到该 ID 广播时的信号值（dBm，系统原始上报）
 * @property lastSeenMs 最近一次收到广播的系统时间戳
 * @property hops     该广播包已经过的中继跳数（0 = 直连听到；>0 = 经其他节点转发）
 */
data class BleMeshPeer(
    val id: String,
    val rssi: Int,
    val lastSeenMs: Long,
    val hops: Int,
)

/**
 * 网格消息 —— 本机发送或从广播中解出的消息
 *
 * @property sender  发送者短 ID
 * @property text    可见文本（已按广播载荷字节预算截断）
 * @property timeMs  本机收到 / 发出该消息的时间戳
 * @property hops    消息到达本机经过的中继跳数（本机发送恒为 0）
 * @property rssi    收到广播时的信号值；本机发送为 null（没有可测量值，界面显示 "--"）
 * @property isMine  是否本机发出
 */
data class BleMeshMessage(
    val sender: String,
    val text: String,
    val timeMs: Long,
    val hops: Int,
    val rssi: Int?,
    val isMine: Boolean,
)

/**
 * BLE 网格聊天页面状态
 *
 * @property running       广播 + 扫描是否在运行
 * @property peers         最近 8 秒内听到广播的节点（按 lastSeenMs 降序）
 * @property messages      消息列表，最新在前，最多保留 50 条
 * @property relayedCount  本机实际中继播发成功的消息条数
 * @property receivedCount 本次会话收到的远端消息总数（超过 50 条后列表截断但计数继续）
 * @property myId          本机短 ID（随机生成并持久化，不来自任何硬件标识）
 * @property payloadBudget 当前厂商数据载荷预算（字节），首次失败后自动收缩并记住
 * @property error         最近一次错误（中文），下次成功发送 / 启动时清空
 * @property notice        最近一次发送的截断提示
 */
data class BleMeshState(
    val running: Boolean = false,
    val peers: List<BleMeshPeer> = emptyList(),
    val messages: List<BleMeshMessage> = emptyList(),
    val relayedCount: Int = 0,
    val receivedCount: Int = 0,
    val myId: String = "",
    val payloadBudget: Int = 27,
    val error: String? = null,
    val notice: String? = null,
)

/** 广播数据里的协议前缀 */
private const val MESH_PREFIX = "MESH|"

/**
 * 厂商数据 company ID —— 0xFFFF 是 Bluetooth SIG 保留给测试用途的 ID，
 * 这里只用它作为自定义网格的标签，不冒用任何真实厂商。
 */
private const val MESH_COMPANY_ID = 0xFFFF

/** 短 ID 字母表：大写字母 + 数字，避免小写与易混淆符号 */
private const val SENDER_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

/** 短 ID 校验：必须 6 位大写字母数字 */
private val SENDER_ID_RE = Regex("^[A-Z0-9]{6}$")

/** 连续空白压缩 */
private val MESH_WHITESPACE_RE = Regex("\\s+")

/** 与本应用其他模块共用偏好文件；键名带 ble_mesh 前缀避免冲突 */
private const val MESH_PREF_NAME = "deauthctrl_prefs"
private const val MESH_KEY_MY_ID = "ble_mesh_id"
private const val MESH_KEY_SEQ = "ble_mesh_seq"

/**
 * BLE 网格中继聊天 —— 无 WiFi、无基站的逐跳消息网络
 *
 * ## 工作原理
 * 一台设备同时使用两个 BLE 角色：
 * - [BluetoothLeAdvertiser]：把当前消息编码进厂商自定义广播数据，连续广播一段时间；
 * - [BluetoothLeScanner]：持续监听附近广播，解出别人的消息并据此发现节点。
 *
 * ## 线上格式（UTF-8，放在厂商数据 payload 内）
 * `MESH|<sender6>|<seq>|<ttl>|<msg>`
 * - sender6：6 位大写字母数字的短 ID；
 * - seq：1..9999 循环自增序号，与 sender 组成去重键；
 * - ttl：剩余跳数，新消息为 [INITIAL_TTL]（2）；
 * - msg：可见文本，按载荷字节预算裁剪。
 *
 * ## 广播包容量（诚实约束）
 * 传统 BLE 广播包总共 31 字节。厂商自定义 AD 结构本身占 4 字节
 * （长度 + 类型 + 2 字节 company ID），多数控制器还会自动附带 3 字节 Flags，
 * 因此 payload 实际只有约 24 字节；协议头 `MESH|ID|seq|ttl|` 就占 16-19 字节，
 * 留给文本的通常只有 5-8 字节（ASCII 5-8 字，中文 1-3 字）。
 * 这里从 27 字节预算开始，遇到 [AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE]
 * 时按 3 字节步长收缩并记住工作值，绝不假装 18 个中文字符能塞进 31 字节。
 *
 * ## 中继与风暴控制
 * - 按 (sender, seq) 去重，LRU 1000 条；
 * - 收到 ttl > 0 的消息时，把 ttl 减 1 后重新广播一次；
 * - 每条消息在本机最多中继一次（relayed LRU），ttl 归零不再转发；
 * - 中继不改写原文：若本机载荷放不下完整原文，宁可不中继，也不截断后转发。
 *
 * ## 明确的局限
 * - 全程明文广播，范围内任何 BLE 扫描器都能读取，没有加密与鉴权；
 * - 没有送达回执，广播发出即结束；
 * - 同时只能跑一个广播会话，发送与中继共用队列，速率约 3 秒/条；
 * - 节点 ID 是随机生成的，不与任何硬件标识绑定。
 *
 * 权限（应用网关统一申请，本类只检查、不请求）：
 * Android 12+ 需要 BLUETOOTH_SCAN / BLUETOOTH_ADVERTISE / BLUETOOTH_CONNECT；
 * Android 11- 扫描需要 ACCESS_FINE_LOCATION。
 */
@Suppress("MissingPermission")
class BleMeshManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(MESH_PREF_NAME, Context.MODE_PRIVATE)

    /** 本机短 ID —— 随机生成后持久化，跨页面重进保持不变 */
    val myId: String = loadOrCreateId()

    private val _state = MutableStateFlow(
        BleMeshState(myId = myId, payloadBudget = DEFAULT_PAYLOAD_BUDGET),
    )
    val state: StateFlow<BleMeshState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ─── 广播串行化：传统控制器同一时刻只有一个广播会话 ───
    private val advLock = Any()
    private var activeAdvertiser: BluetoothLeAdvertiser? = null
    private var activeCallback: AdvertiseCallback? = null
    private var txChannel: Channel<TxRequest>? = null
    private var txJob: Job? = null

    // ─── 扫描与维护 ───
    private var scanRetryJob: Job? = null
    private var maintenanceJob: Job? = null

    // ─── 去重：按 (sender, seq) 记住已处理消息 ───
    private val seenLock = Any()
    private val seen = LruSet(SEEN_CAPACITY)

    // ─── 中继去重：每条消息最多中继一次 ───
    private val relayLock = Any()
    private val relayed = LruSet(SEEN_CAPACITY)

    // ─── 节点表：短 ID -> 最近一次广播信息 ───
    private val peerLock = Any()
    private val peersById = LinkedHashMap<String, PeerEntry>()

    @Volatile private var lastPeerPublishMs = 0L

    // ─── 序号：持久化自增，1..9999 循环 ───
    private val seqLock = Any()
    private var seqValue: Int = prefs.getInt(MESH_KEY_SEQ, SEQ_MIN).coerceIn(SEQ_MIN, SEQ_MAX)

    // ══════════════════════════════════════════════════════════════════════
    // 生命周期
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 启动广播与扫描（同步完成前置校验）
     *
     * 依次校验权限 → 适配器 → 蓝牙开关 → 广播器 / 扫描器。
     * 任一步失败都写入 error 并返回 false，不会出现「界面显示运行中但什么都没做」。
     *
     * @return true 表示广播队列与扫描都已启动
     */
    fun start(): Boolean {
        if (_state.value.running) return true

        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            publishError(
                L10n.get(
                    R.string.mgr_mesh_err_permissions,
                    missing.joinToString(L10n.get(R.string.mgr_mesh_perm_sep)) { permissionLabel(it) },
                ),
            )
            return false
        }

        val adapter = adapterOrNull()
        if (adapter == null) {
            publishError(L10n.get(R.string.mgr_mesh_err_no_adapter))
            return false
        }
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) {
            publishError(L10n.get(R.string.mgr_mesh_err_bt_off))
            return false
        }
        if (runCatching { adapter.bluetoothLeAdvertiser }.getOrNull() == null) {
            publishError(L10n.get(R.string.mgr_mesh_err_no_advertiser))
            return false
        }
        if (runCatching { adapter.bluetoothLeScanner }.getOrNull() == null) {
            publishError(L10n.get(R.string.mgr_mesh_err_no_scanner))
            return false
        }

        lastPeerPublishMs = 0L
        synchronized(peerLock) { peersById.clear() }
        _state.update {
            it.copy(
                running = true,
                peers = emptyList(),
                error = null,
                notice = null,
                payloadBudget = it.payloadBudget.coerceIn(MIN_PAYLOAD_BUDGET, DEFAULT_PAYLOAD_BUDGET),
            )
        }
        launchTxWorker()
        startScanInternal()
        startMaintenance()
        return true
    }

    /** 停止广播与扫描；已收消息保留，节点表清空 */
    fun stop() {
        val wasRunning = _state.value.running
        _state.update { it.copy(running = false, peers = emptyList(), notice = null) }
        if (!wasRunning) {
            stopActiveAdvertising()
            return
        }

        scanRetryJob?.cancel()
        scanRetryJob = null
        maintenanceJob?.cancel()
        maintenanceJob = null
        txJob?.cancel()
        txJob = null
        txChannel?.close()
        txChannel = null

        runCatching { adapterOrNull()?.bluetoothLeScanner?.stopScan(scanCallback) }
        stopActiveAdvertising()
        synchronized(peerLock) { peersById.clear() }
    }

    /** ViewModel 回收时调用：停止一切并取消后台协程 */
    fun destroy() {
        stop()
        scope.cancel()
    }

    // ══════════════════════════════════════════════════════════════════════
    // 发送
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 发送一条消息
     *
     * 文本先过滤控制符与分隔符「|」，再截断到 [MAX_MESSAGE_CHARS] 18 字，
     * 最后按当前广播载荷预算做 UTF-8 字节裁剪。文本不会被加密或签名。
     *
     * @return true 表示消息已进入广播队列（不保证其他节点收到）
     */
    fun send(raw: String): Boolean {
        if (!_state.value.running) {
            publishError(L10n.get(R.string.mgr_mesh_err_not_running))
            return false
        }
        val channel = txChannel
        if (channel == null) {
            publishError(L10n.get(R.string.mgr_mesh_err_queue_not_ready))
            return false
        }

        val cleaned = sanitize(raw)
        if (cleaned.isEmpty()) {
            publishError(L10n.get(R.string.mgr_mesh_err_empty))
            return false
        }
        val clipped = if (cleaned.length > MAX_MESSAGE_CHARS) cleaned.take(MAX_MESSAGE_CHARS) else cleaned
        val truncatedByChars = cleaned.length > MAX_MESSAGE_CHARS

        val seq = nextSeq()
        val budget = _state.value.payloadBudget
        val built = buildPayload(myId, seq, INITIAL_TTL, clipped, budget)
        if (built == null) {
            publishError(L10n.get(R.string.mgr_mesh_err_header_budget, budget))
            return false
        }

        val request = TxRequest(
            sender = myId,
            seq = seq,
            ttl = INITIAL_TTL,
            text = built.text,
            rounds = SEND_ROUNDS,
            isRelay = false,
        )
        if (!channel.trySend(request).isSuccess) {
            publishError(L10n.get(R.string.mgr_mesh_err_queue_closed))
            return false
        }

        val notes = ArrayList<String>(2)
        if (truncatedByChars) notes += L10n.get(R.string.mgr_mesh_notice_truncated_chars, MAX_MESSAGE_CHARS)
        if (built.truncated) notes += L10n.get(R.string.mgr_mesh_notice_truncated_bytes, built.text.length)
        val note = if (notes.isEmpty()) null else notes.joinToString(L10n.get(R.string.mgr_mesh_notice_sep))

        // 本机回声：广播没有回执，这条只代表「已排队 / 已广播」，不代表送达
        synchronized(seenLock) { seen["$myId#$seq"] = true }
        _state.update {
            it.copy(
                messages = (
                    listOf(BleMeshMessage(myId, built.text, System.currentTimeMillis(), 0, null, true)) +
                        it.messages
                    ).take(MAX_MESSAGES),
                notice = note,
                error = null,
            )
        }
        return true
    }

    // ══════════════════════════════════════════════════════════════════════
    // 接收与中继
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 解析一个厂商数据 payload
     *
     * 格式不合法、发送者是自己、ttl 超出范围都直接丢弃。
     * 合法消息先更新节点表，再按 (sender, seq) 去重；重复包只刷新节点 RSSI。
     */
    private fun handlePacket(payload: ByteArray, rssi: Int) {
        if (!_state.value.running) return

        val text = runCatching { String(payload, Charsets.UTF_8) }.getOrNull() ?: return
        if (!text.startsWith(MESH_PREFIX)) return

        val parts = text.split("|", limit = 5)
        if (parts.size != 5 || parts[0] != "MESH") return

        val sender = parts[1]
        if (!SENDER_ID_RE.matches(sender)) return
        if (sender == myId) return

        val seq = parts[2].toLongOrNull() ?: return
        val ttl = parts[3].toIntOrNull() ?: return
        if (ttl < 0 || ttl > INITIAL_TTL) return

        val message = parts[4]
        if (message.isEmpty()) return

        val hops = INITIAL_TTL - ttl
        val now = System.currentTimeMillis()
        updatePeer(sender, rssi, hops, now)

        val key = "$sender#$seq"
        val fresh = synchronized(seenLock) {
            if (seen.containsKey(key)) {
                false
            } else {
                seen[key] = true
                true
            }
        }
        if (!fresh) return

        _state.update {
            it.copy(
                messages = (
                    listOf(BleMeshMessage(sender, message, now, hops, rssi, false)) + it.messages
                    ).take(MAX_MESSAGES),
                receivedCount = it.receivedCount + 1,
            )
        }

        if (ttl > 0) enqueueRelay(sender, seq, ttl, message)
    }

    /**
     * 把一条消息加入中继队列（ttl 减 1）
     *
     * 防风暴：按 (sender, seq) 记录已中继集合，每条消息在本机只中继一次。
     * 若本机载荷放不下完整原文，直接放弃这条中继 —— 不会截断后转发，
     * 避免把一条被悄悄改写的消息继续放大。
     */
    private fun enqueueRelay(sender: String, seq: Long, ttl: Int, message: String) {
        val relayKey = "$sender#$seq"
        synchronized(relayLock) {
            if (relayed.containsKey(relayKey)) return
            relayed[relayKey] = true
        }

        val built = buildPayload(sender, seq, ttl - 1, message, _state.value.payloadBudget) ?: return
        if (built.truncated) return

        val channel = txChannel ?: return
        channel.trySend(
            TxRequest(
                sender = sender,
                seq = seq,
                ttl = ttl - 1,
                text = built.text,
                rounds = RELAY_ROUNDS,
                isRelay = true,
            ),
        )
    }

    // ─── 节点表 ───

    private fun updatePeer(id: String, rssi: Int, hops: Int, now: Long) {
        synchronized(peerLock) {
            peersById[id] = PeerEntry(rssi, now, hops)
            if (peersById.size > MAX_PEERS) {
                val oldestKey = peersById.entries.minByOrNull { it.value.lastSeenMs }?.key
                if (oldestKey != null) peersById.remove(oldestKey)
            }
        }
        publishPeersThrottled(now)
    }

    /** 扫描回调可能每秒触发多次，节点列表最多每 500ms 推送一次 */
    private fun publishPeersThrottled(now: Long) {
        if (now - lastPeerPublishMs < PEER_PUBLISH_INTERVAL_MS) return
        lastPeerPublishMs = now
        publishPeers(now)
    }

    /** 清理 8 秒未再广播的节点，并把剩余节点按最近出现时间排序后推给 UI */
    private fun publishPeers(now: Long) {
        val snapshot: List<BleMeshPeer>
        synchronized(peerLock) {
            val iterator = peersById.entries.iterator()
            while (iterator.hasNext()) {
                if (now - iterator.next().value.lastSeenMs > PEER_TTL_MS) iterator.remove()
            }
            snapshot = peersById.entries
                .sortedByDescending { it.value.lastSeenMs }
                .map { BleMeshPeer(it.key, it.value.rssi, it.value.lastSeenMs, it.value.hops) }
        }
        _state.update { if (it.peers == snapshot) it else it.copy(peers = snapshot) }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 扫描
    // ══════════════════════════════════════════════════════════════════════

    private val scanSettings: ScanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!_state.value.running) return
            val record = result.scanRecord ?: return
            val payload = record.manufacturerSpecificData?.get(MESH_COMPANY_ID) ?: return
            handlePacket(payload, result.rssi)
        }

        override fun onScanFailed(errorCode: Int) {
            if (!_state.value.running) return
            publishError(describeScanFailure(errorCode))
            scheduleScanRestart()
        }
    }

    /** 启动扫描；失败时写入 error 并安排重试 */
    private fun startScanInternal() {
        val adapter = adapterOrNull() ?: return
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) return
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val scanner = runCatching { adapter.bluetoothLeScanner }.getOrNull()
        if (scanner == null) {
            publishError(L10n.get(R.string.mgr_mesh_err_no_scanner_generic))
            scheduleScanRestart()
            return
        }
        try {
            scanner.startScan(null, scanSettings, scanCallback)
        } catch (e: SecurityException) {
            publishError(
                L10n.get(
                    R.string.mgr_mesh_err_scan_permission,
                    e.message ?: L10n.get(R.string.mgr_mesh_err_scan_permission_hint),
                ),
            )
        } catch (t: Throwable) {
            publishError(L10n.get(R.string.mgr_mesh_err_scan_start, t.message ?: t.javaClass.simpleName))
            scheduleScanRestart()
        }
    }

    /** 扫描失败后 10 秒重试（SCAN_FAILED_SCANNING_TOO_FREQUENTLY 需要冷却时间） */
    private fun scheduleScanRestart() {
        scanRetryJob?.cancel()
        scanRetryJob = scope.launch {
            delay(SCAN_RESTART_DELAY_MS)
            if (_state.value.running) startScanInternal()
        }
    }

    /** 每 2 秒清理一次过期的在线节点（即使没有新广播，列表也会自己掉线） */
    private fun startMaintenance() {
        maintenanceJob?.cancel()
        maintenanceJob = scope.launch {
            while (isActive) {
                delay(MAINTENANCE_INTERVAL_MS)
                if (!_state.value.running) break
                publishPeers(System.currentTimeMillis())
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 广播队列（单线程串行）
    // ══════════════════════════════════════════════════════════════════════

    private fun launchTxWorker() {
        txJob?.cancel()
        txChannel?.close()
        val channel = Channel<TxRequest>(Channel.UNLIMITED)
        txChannel = channel
        txJob = scope.launch {
            try {
                for (request in channel) {
                    if (!_state.value.running) break
                    val sent = runTransaction(request)
                    if (sent && request.isRelay) {
                        _state.update { it.copy(relayedCount = it.relayedCount + 1) }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                publishError(L10n.get(R.string.mgr_mesh_err_broadcast_task, t.message ?: t.javaClass.simpleName))
            } finally {
                stopActiveAdvertising()
            }
        }
    }

    /**
     * 执行一条消息的广播事务
     *
     * 本机消息广播 [SEND_ROUNDS] 轮（每轮 1 秒），中继只广播 1 轮。
     * @return 是否至少成功启动过一轮广播
     */
    private suspend fun runTransaction(request: TxRequest): Boolean {
        var success = false
        repeat(request.rounds) {
            if (!_state.value.running) return success
            if (!advertiseOneRound(request)) return success
            success = true
            delay(ROUND_GAP_MS)
        }
        return success
    }

    /**
     * 广播一轮：启动会话 → 保持 1 秒 → 停止
     *
     * 失败处理：
     * - DATA_TOO_LARGE：按 3 字节步长收缩载荷预算后立即用同一轮重试；
     * - 其他失败：退避 1 秒重试，最多 [MAX_ADVERTISE_ATTEMPTS] 次。
     */
    private suspend fun advertiseOneRound(request: TxRequest): Boolean {
        var failedAttempts = 0
        while (true) {
            if (!_state.value.running) return false

            val budget = _state.value.payloadBudget
            val built = buildPayload(request.sender, request.seq, request.ttl, request.text, budget)
            if (built == null) {
                publishError(L10n.get(R.string.mgr_mesh_err_header_fit, budget))
                return false
            }
            if (built.text.isEmpty() && request.text.isNotEmpty()) {
                publishError(L10n.get(R.string.mgr_mesh_err_no_char, budget))
                return false
            }
            // 中继不改写原文：放不下完整原文就放弃这条中继
            if (request.isRelay && built.truncated) return false
            if (!request.isRelay && built.truncated) {
                _state.update {
                    it.copy(notice = L10n.get(R.string.mgr_mesh_notice_budget_tightened, budget, built.text.length))
                }
            }

            when (val code = awaitAdvertise(built.bytes)) {
                RESULT_OK -> {
                    delay(ADVERTISE_ROUND_MS)
                    stopActiveAdvertising()
                    return true
                }

                AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> {
                    stopActiveAdvertising()
                    val nextBudget = budget - PAYLOAD_BUDGET_STEP
                    if (nextBudget < MIN_PAYLOAD_BUDGET) {
                        publishError(L10n.get(R.string.mgr_mesh_err_data_too_large))
                        return false
                    }
                    _state.update { it.copy(payloadBudget = nextBudget) }
                }

                else -> {
                    stopActiveAdvertising()
                    failedAttempts++
                    if (failedAttempts >= MAX_ADVERTISE_ATTEMPTS) {
                        publishError(advertiseFailureText(code))
                        return false
                    }
                    delay(ADVERTISE_RETRY_BACKOFF_MS)
                }
            }
        }
    }

    /**
     * 启动一个广播会话并等待系统回调（3 秒超时兜底）
     *
     * @return [RESULT_OK] 或错误码（AdvertiseCallback.ADVERTISE_FAILED_* 或内部负数码）
     */
    private suspend fun awaitAdvertise(payload: ByteArray): Int {
        val adapter = adapterOrNull() ?: return RESULT_NO_ADAPTER
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) return RESULT_NO_ADAPTER
        val advertiser = runCatching { adapter.bluetoothLeAdvertiser }.getOrNull()
            ?: return RESULT_NO_ADVERTISER

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addManufacturerData(MESH_COMPANY_ID, payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val result = CompletableDeferred<Int>()
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                result.complete(RESULT_OK)
            }

            override fun onStartFailure(errorCode: Int) {
                result.complete(errorCode)
            }
        }

        synchronized(advLock) {
            activeAdvertiser = advertiser
            activeCallback = callback
        }
        try {
            advertiser.startAdvertising(settings, data, callback)
        } catch (e: SecurityException) {
            clearActiveCallback(callback)
            return RESULT_PERMISSION
        } catch (e: IllegalArgumentException) {
            // 部分控制器在 startAdvertising 入口就校验长度，直接抛 IllegalArgumentException；
            // 映射成 DATA_TOO_LARGE，让上层按步长收缩载荷后重试。
            clearActiveCallback(callback)
            return AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE
        } catch (t: Throwable) {
            clearActiveCallback(callback)
            return RESULT_INTERNAL
        }

        val code = withTimeoutOrNull(ADVERTISE_CALLBACK_TIMEOUT_MS) { result.await() } ?: RESULT_TIMEOUT

        // stop() 可能恰好发生在 startAdvertising 与回调之间：若已不在运行，
        // 立刻收回这个会话，避免留下一个静态消息无限广播的孤儿会话。
        if (!_state.value.running) {
            runCatching { advertiser.stopAdvertising(callback) }
            clearActiveCallback(callback)
            return RESULT_TIMEOUT
        }

        if (code != RESULT_OK) {
            // 失败或超时都要确保这个 callback 对应的会话被收回，
            // 否则超时后迟到的 onStartSuccess 会让广播一直开着。
            runCatching { advertiser.stopAdvertising(callback) }
            clearActiveCallback(callback)
        }
        return code
    }

    private fun clearActiveCallback(callback: AdvertiseCallback) {
        synchronized(advLock) {
            if (activeCallback === callback) {
                activeAdvertiser = null
                activeCallback = null
            }
        }
    }

    /** 停止当前活跃广播会话（幂等） */
    private fun stopActiveAdvertising() {
        val advertiser: BluetoothLeAdvertiser?
        val callback: AdvertiseCallback?
        synchronized(advLock) {
            advertiser = activeAdvertiser
            callback = activeCallback
            activeAdvertiser = null
            activeCallback = null
        }
        if (advertiser == null || callback == null) return
        runCatching { advertiser.stopAdvertising(callback) }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 编码与辅助
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 构造线上 payload
     *
     * 协议头使用 ASCII，文本按 UTF-8 逐字符累计字节数，超过预算时在字符边界截断
     * （不会切出半个多字节字符）。
     */
    private fun buildPayload(
        sender: String,
        seq: Long,
        ttl: Int,
        text: String,
        budget: Int,
    ): BuiltPayload? {
        val prefix = "$MESH_PREFIX$sender|$seq|$ttl|"
        val prefixBytes = prefix.toByteArray(Charsets.UTF_8)
        if (prefixBytes.size > budget) return null

        val body = StringBuilder(text.length)
        var used = prefixBytes.size
        var truncated = false
        for (ch in text) {
            val charBytes = ch.toString().toByteArray(Charsets.UTF_8)
            if (used + charBytes.size > budget) {
                truncated = true
                break
            }
            body.append(ch)
            used += charBytes.size
        }

        val bodyBytes = body.toString().toByteArray(Charsets.UTF_8)
        val bytes = ByteArray(prefixBytes.size + bodyBytes.size)
        System.arraycopy(prefixBytes, 0, bytes, 0, prefixBytes.size)
        System.arraycopy(bodyBytes, 0, bytes, prefixBytes.size, bodyBytes.size)
        return BuiltPayload(bytes, body.toString(), truncated)
    }

    /**
     * 过滤不支持字符：控制符与分隔符「|」替换为空格，压缩连续空白后去首尾。
     * 不做大小写转换，不改写正常标点与中文。
     */
    private fun sanitize(raw: String): String {
        val builder = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch == '|' -> builder.append(' ')
                Character.isISOControl(ch) -> builder.append(' ')
                else -> builder.append(ch)
            }
        }
        return MESH_WHITESPACE_RE.replace(builder.toString(), " ").trim()
    }

    private fun nextSeq(): Long = synchronized(seqLock) {
        val next = if (seqValue >= SEQ_MAX) SEQ_MIN else seqValue + 1
        seqValue = next
        prefs.edit().putInt(MESH_KEY_SEQ, next).apply()
        next.toLong()
    }

    private fun loadOrCreateId(): String {
        val saved = prefs.getString(MESH_KEY_MY_ID, null)
        if (saved != null && SENDER_ID_RE.matches(saved)) return saved
        val random = SecureRandom()
        val generated = buildString(6) {
            repeat(6) { append(SENDER_ID_ALPHABET[random.nextInt(SENDER_ID_ALPHABET.length)]) }
        }
        prefs.edit().putString(MESH_KEY_MY_ID, generated).apply()
        return generated
    }

    private fun adapterOrNull(): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private fun missingPermissions(): List<String> {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun publishError(message: String) {
        _state.update { it.copy(error = message) }
    }

    // ─── 内部数据类 ───

    private class PeerEntry(
        var rssi: Int,
        var lastSeenMs: Long,
        var hops: Int,
    )

    private data class TxRequest(
        val sender: String,
        val seq: Long,
        val ttl: Int,
        val text: String,
        val rounds: Int,
        val isRelay: Boolean,
    )

    private data class BuiltPayload(
        val bytes: ByteArray,
        val text: String,
        val truncated: Boolean,
    )

    /** 简单的 LRU 集合：容量满时淘汰最久未插入的键 */
    private class LruSet(capacity: Int) :
        LinkedHashMap<String, Boolean>(capacity, 0.75f, true) {
        private val maxEntries = capacity

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > maxEntries
    }

    companion object {
        /** 新消息的初始 TTL（最多再经 2 跳） */
        const val INITIAL_TTL = 2

        /** 用户可见文本的字符上限；实际还要受广播字节预算约束 */
        const val MAX_MESSAGE_CHARS = 18

        /** 本机消息广播轮数（每轮 1 秒） */
        private const val SEND_ROUNDS = 3

        /** 中继只广播 1 轮，降低空口占用 */
        private const val RELAY_ROUNDS = 1

        /** 每轮广播保持时长 */
        private const val ADVERTISE_ROUND_MS = 1_000L

        /** 两轮广播之间的槽位回收时间 */
        private const val ROUND_GAP_MS = 150L

        /** 广播启动失败后的退避时间 */
        private const val ADVERTISE_RETRY_BACKOFF_MS = 1_000L

        /** 每轮最多尝试次数（含首次） */
        private const val MAX_ADVERTISE_ATTEMPTS = 3

        /** 等待 AdvertiseCallback 的上限 */
        private const val ADVERTISE_CALLBACK_TIMEOUT_MS = 3_000L

        /** 厂商数据载荷的起始预算（31B 理论值，失败后自动收缩） */
        private const val DEFAULT_PAYLOAD_BUDGET = 27

        /** 载荷预算下限：至少要放得下最长的协议头 */
        private const val MIN_PAYLOAD_BUDGET = 19

        /** 每次 DATA_TOO_LARGE 收缩的字节数 */
        private const val PAYLOAD_BUDGET_STEP = 3

        /** 消息列表最多保留条数 */
        private const val MAX_MESSAGES = 50

        /** 去重 / 中继记录容量 */
        private const val SEEN_CAPACITY = 1_000

        /** 节点表上限 */
        private const val MAX_PEERS = 64

        /** 节点 8 秒无广播即视为离线 */
        private const val PEER_TTL_MS = 8_000L

        /** 节点列表推送节流间隔 */
        private const val PEER_PUBLISH_INTERVAL_MS = 500L

        /** 过期节点清理周期 */
        private const val MAINTENANCE_INTERVAL_MS = 2_000L

        /** 扫描失败后的重试间隔 */
        private const val SCAN_RESTART_DELAY_MS = 10_000L

        /** 序号范围：与 1000 条 LRU 拉开距离，循环时不会误判为重复 */
        private const val SEQ_MIN = 1
        private const val SEQ_MAX = 9_999

        private const val RESULT_OK = 0
        private const val RESULT_TIMEOUT = -1
        private const val RESULT_PERMISSION = -2
        private const val RESULT_NO_ADAPTER = -3
        private const val RESULT_NO_ADVERTISER = -4
        private const val RESULT_INTERNAL = -5
    }
}

/** 权限名 → 本地化标签 */
private fun permissionLabel(permission: String): String = when (permission) {
    Manifest.permission.BLUETOOTH_SCAN -> L10n.get(R.string.mgr_mesh_perm_scan)
    Manifest.permission.BLUETOOTH_ADVERTISE -> L10n.get(R.string.mgr_mesh_perm_advertise)
    Manifest.permission.BLUETOOTH_CONNECT -> L10n.get(R.string.mgr_mesh_perm_connect)
    Manifest.permission.ACCESS_FINE_LOCATION -> L10n.get(R.string.mgr_mesh_perm_location)
    else -> permission
}

/** ScanCallback 错误码 → 可读原因 */
private fun describeScanFailure(errorCode: Int): String = when (errorCode) {
    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> L10n.get(R.string.mgr_mesh_scan_already_started)
    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> L10n.get(R.string.mgr_mesh_scan_reg_failed)
    ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> L10n.get(R.string.mgr_mesh_scan_internal)
    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> L10n.get(R.string.mgr_mesh_scan_unsupported)
    ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> L10n.get(R.string.mgr_mesh_scan_no_resources)
    ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> L10n.get(R.string.mgr_mesh_scan_too_frequent)
    else -> L10n.get(R.string.mgr_mesh_scan_failed_code, errorCode)
}

/** AdvertiseCallback 错误码 → 可读原因 */
private fun advertiseFailureText(code: Int): String = when (code) {
    AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> L10n.get(R.string.mgr_mesh_adv_data_too_large)
    AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> L10n.get(R.string.mgr_mesh_adv_too_many)
    AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> L10n.get(R.string.mgr_mesh_adv_already_started)
    AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> L10n.get(R.string.mgr_mesh_adv_internal)
    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> L10n.get(R.string.mgr_mesh_adv_unsupported)
    -1 -> L10n.get(R.string.mgr_mesh_adv_timeout)
    -2 -> L10n.get(R.string.mgr_mesh_adv_no_permission)
    -3 -> L10n.get(R.string.mgr_mesh_adv_no_adapter)
    -4 -> L10n.get(R.string.mgr_mesh_adv_no_advertiser)
    -5 -> L10n.get(R.string.mgr_mesh_adv_exception)
    else -> L10n.get(R.string.mgr_mesh_adv_failed_code, code)
}
