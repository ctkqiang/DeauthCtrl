package xin.ctkqiang.deauthctrl.manager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

data class RadarTarget(
    val id: String,
    val name: String,
    val type: String,
    val rssi: Int,
    val distanceM: Float,
    val angleDeg: Float,
    val channel: String = "",
    val vendor: String = "",
)

class RadarManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val btManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val btAdapter: BluetoothAdapter? = btManager?.adapter

    val targets = ConcurrentHashMap<String, RadarTarget>()

    @Volatile var isRunning = false; private set

    private var scanJob: Job? = null
    @Volatile private var btDiscoveryActive = false

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            try {
                val results: List<ScanResult> = wifiManager.scanResults ?: return
                results.forEach { r -> addWifiTarget(r) }
            } catch (_: Exception) {}
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        @Suppress("DEPRECATION")
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        if (rssi == Short.MIN_VALUE.toInt()) return
                        val addr = device?.address ?: return
                        addBtTarget(addr, device?.name ?: "(BT Device)", rssi)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        btDiscoveryActive = true
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        btDiscoveryActive = false
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun addWifiTarget(r: ScanResult) {
        val dist = rssiToDistance(r.level, -40f)
        targets[r.BSSID] = RadarTarget(
            id = r.BSSID, name = r.SSID.ifBlank { "<HIDDEN>" }, type = "wifi",
            rssi = r.level, distanceM = dist, angleDeg = pseudoAngle(r.BSSID),
            channel = "CH${channelFromFreq(r.frequency)}", vendor = r.capabilities.take(10),
        )
    }

    private fun addBtTarget(addr: String, name: String, rssi: Int) {
        val dist = rssiToDistance(rssi, -59f)
        targets[addr] = RadarTarget(
            id = addr, name = name, type = "bt",
            rssi = rssi, distanceM = dist, angleDeg = pseudoAngle(addr), channel = "2.4GHz",
        )
    }

    fun start() {
        isRunning = true; targets.clear(); btDiscoveryActive = false

        try {
            val wifiFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            val btFilter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(wifiReceiver, wifiFilter, ContextCompat.RECEIVER_EXPORTED)
                context.registerReceiver(btReceiver, btFilter, ContextCompat.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(wifiReceiver, wifiFilter)
                context.registerReceiver(btReceiver, btFilter)
            }
        } catch (_: Exception) {}

        // kick off first BT discovery (must run on main thread)
        CoroutineScope(Dispatchers.Main).launch { startBtDiscovery() }

        scanJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val scanStarted = wifiManager.startScan()
                    if (!scanStarted) { delay(5000); continue }
                } catch (_: Exception) {}
                delay(3000)
            }
        }
    }

    private fun startBtDiscovery() {
        try {
            val adapter = btAdapter ?: return
            if (adapter.isDiscovering) {
                btDiscoveryActive = true
                return
            }
            btDiscoveryActive = adapter.startDiscovery()
        } catch (_: Exception) {}
    }

    fun onTick() {
        if (!isRunning) return
        CoroutineScope(Dispatchers.Main).launch { startBtDiscovery() }
    }

    fun stop() {
        isRunning = false; scanJob?.cancel()
        try { context.unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
        try { context.unregisterReceiver(btReceiver) } catch (_: Exception) {}
        try { btAdapter?.cancelDiscovery() } catch (_: Exception) {}
        btDiscoveryActive = false
    }

    private fun rssiToDistance(rssi: Int, txPower: Float): Float {
        val ratio = (txPower - rssi) / (10f * 2.5f)
        return Math.pow(10.0, ratio.toDouble()).toFloat().coerceIn(0.3f, 100f)
    }

    private fun pseudoAngle(mac: String): Float {
        val clean = mac.replace(":", "").takeLast(6)
        return (clean.hashCode() and 0x7FFFFFFF) % 360f
    }

    private fun channelFromFreq(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5180..5825 -> (freq - 5180) / 5 + 36
        else -> 0
    }
}
