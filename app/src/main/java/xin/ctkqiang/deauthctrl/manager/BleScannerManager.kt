package xin.ctkqiang.deauthctrl.manager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import java.util.concurrent.ConcurrentHashMap

data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val rssiHistory: MutableList<Int> = mutableListOf(),
    val manufacturerData: Map<Int, String> = emptyMap(),
    val services: List<String> = emptyList(),
    val txPower: Int = Int.MIN_VALUE,
    val deviceType: String = "",
    val lastSeen: Long = System.currentTimeMillis(),
)

class BleScannerManager(private val context: Context) {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner
    @Volatile var isRunning = false
        private set

    val devices = ConcurrentHashMap<String, BleDevice>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            val record = result.scanRecord
            val rssi = result.rssi
            val addr = dev.address ?: return
            val name = dev.name ?: record?.deviceName ?: "(未知)"
            val services = record?.serviceUuids?.map { it.uuid.toString().takeLast(8) } ?: emptyList()
            val mfrData = record?.manufacturerSpecificData
            val mfr = mutableMapOf<Int, String>()
            if (mfrData != null) for (i in 0 until mfrData.size()) mfr[mfrData.keyAt(i)] = bytesToHex(mfrData.valueAt(i))
            val txPower = record?.txPowerLevel ?: Int.MIN_VALUE
            val type = when {
                services.any { it.contains("180") } -> "Health"
                services.any { it.contains("180A") } -> "Device Info"
                services.any { it.contains("180F") } -> "Battery"
                services.any { it.contains("1810") } -> "Blood Pressure"
                name.contains("AirPods") || name.contains("AirTag") -> "Apple"
                name.contains("Galaxy") || name.contains("Buds") -> "Samsung"
                name.contains("Mi ") -> "Xiaomi"
                else -> ""
            }

            val existing = devices[addr]
            if (existing != null) {
                existing.rssiHistory.add(rssi)
                if (existing.rssiHistory.size > 50) existing.rssiHistory.removeAt(0)
                devices[addr] = existing.copy(
                    rssi = rssi, name = if (name != "(未知)") name else existing.name,
                    services = services, manufacturerData = mfr,
                    txPower = txPower, deviceType = type,
                    lastSeen = System.currentTimeMillis(),
                )
            } else {
                val history = mutableListOf(rssi)
                devices[addr] = BleDevice(
                    address = addr, name = name, rssi = rssi,
                    rssiHistory = history, services = services,
                    manufacturerData = mfr, txPower = txPower,
                    deviceType = type,
                )
            }
        }

        override fun onScanFailed(errorCode: Int) { isRunning = false }
    }

    fun start() {
        devices.clear()
        isRunning = true
        scanner?.startScan(scanCallback)
    }

    fun stop() {
        isRunning = false
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }
}
