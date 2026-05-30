package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xin.ctkqiang.deauthctrl.model.JammerLogEntry

sealed class JammerScanState {
    object Idle : JammerScanState()
    object Scanning : JammerScanState()
    data class Results(val networks: List<JammerWifiNetwork>) : JammerScanState()
    data class Error(val message: String) : JammerScanState()
}

data class JammerWifiNetwork(
    val ssid: String, val bssid: String, val signalStrength: Int,
    val frequency: Int, val channel: Int, val security: String,
)

class WifiJammerManager(private val context: Context) {

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var jammerJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private val _scanState = MutableStateFlow<JammerScanState>(JammerScanState.Idle)
    val scanState: StateFlow<JammerScanState> = _scanState.asStateFlow()
    private val _log = MutableStateFlow<List<JammerLogEntry>>(emptyList())
    val log: StateFlow<List<JammerLogEntry>> = _log.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _targetCount = MutableStateFlow(0)
    val targetCount: StateFlow<Int> = _targetCount.asStateFlow()

    private var cycleCount = 0
    private var beaconCount = 0
    private var hotspotReservation: android.net.wifi.WifiManager.LocalOnlyHotspotReservation? = null

    fun scanAndStart(toggleMs: Long = 80L) {
        if (_isRunning.value) return
        if (!wifiManager.isWifiEnabled) { _error.value = "wifi is off"; return }
        _scanState.value = JammerScanState.Scanning; _error.value = null
        log("SCAN", "starting WiFi scan...")

        scope.launch {
            val cached = wifiManager.scanResults
                ?.filter { it.SSID.isNotBlank() }?.distinctBy { it.BSSID }
                ?.sortedByDescending { it.level }?.map { it.toNetwork() } ?: emptyList()

            try { wifiManager.startScan(); delay(1500) } catch (_: SecurityException) {
                _scanState.value = JammerScanState.Error("location permission required for WiFi scan")
                return@launch
            }

            val fresh = wifiManager.scanResults
                ?.filter { it.SSID.isNotBlank() }?.distinctBy { it.BSSID }
                ?.sortedByDescending { it.level }?.map { it.toNetwork() } ?: emptyList()

            val all = (fresh.ifEmpty { cached }).distinctBy { it.bssid }
            if (all.isEmpty()) { _scanState.value = JammerScanState.Error("no WiFi networks found"); return@launch }

            _scanState.value = JammerScanState.Results(all)
            _targetCount.value = all.size
            val ssids = all.map { it.ssid }.distinct()
            log("SCAN", "found ${all.size} networks, ${ssids.size} unique SSIDs")

            startJamming(ssids, toggleMs.coerceIn(60, 300))
        }
    }

    private fun startJamming(ssids: List<String>, toggleMs: Long) {
        _isRunning.value = true; cycleCount = 0; beaconCount = 0
        jammerJob = scope.launch {
            log("JAMMER", "jamming ${ssids.size} SSIDs at ${toggleMs}ms toggle")
            var idx = 0
            while (_isRunning.value && ssids.isNotEmpty()) {
                val ssid = ssids[idx % ssids.size]
                toggleHotspot(ssid)
                delay(toggleMs)
                idx++
                if (idx % (maxOf(ssids.size, 1) * 2) == 0) {
                    cycleCount++; beaconCount += 2
                    if (cycleCount % 30 == 0) log("JAMMER", "beacon: $beaconCount cycles: $cycleCount")
                }
            }
            destroyHotspot()
            log("JAMMER", "complete | ~$beaconCount beacons | $cycleCount cycles | ${ssids.size} SSIDs")
        }
    }

    private fun toggleHotspot(ssid: String) {
        try {
            destroyHotspot()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                wifiManager.startLocalOnlyHotspot(object : android.net.wifi.WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(r: android.net.wifi.WifiManager.LocalOnlyHotspotReservation?) {
                        hotspotReservation = r
                    }
                    override fun onStopped() {}
                    override fun onFailed(reason: Int) {}
                }, null)
            }
        } catch (e: SecurityException) {
            _error.value = "permission denied for hotspot"
        } catch (_: Exception) {}
    }

    private fun destroyHotspot() {
        try { hotspotReservation?.close() } catch (_: Exception) {}
        hotspotReservation = null
    }

    fun stop() {
        _isRunning.value = false; jammerJob?.cancel()
        destroyHotspot()
        _scanState.value = JammerScanState.Idle; _targetCount.value = 0
    }

    fun destroy() { stop(); scope.cancel() }
    fun clearLog() { _log.value = emptyList(); cycleCount = 0; beaconCount = 0 }
    fun getBeaconCount() = beaconCount
    fun getCycleCount() = cycleCount

    private fun log(module: String, msg: String) {
        _log.value = (_log.value + JammerLogEntry(module = module, message = msg)).takeLast(200)
    }

    private fun ScanResult.toNetwork() = JammerWifiNetwork(
        ssid = SSID.ifBlank { "<hidden>" }, bssid = BSSID, signalStrength = level,
        frequency = frequency, channel = freqToCh(frequency), security = parseSec(capabilities),
    )

    private fun freqToCh(f: Int): Int = when (f) {
        2412->1; 2417->2; 2422->3; 2427->4; 2432->5; 2437->6; 2442->7; 2447->8
        2452->9; 2457->10; 2462->11; 2467->12; 2472->13; 2484->14
        5180->36; 5200->40; 5220->44; 5240->48; 5260->52; 5280->56; 5300->60; 5320->64
        5500->100; 5520->104; 5540->108; 5560->112; 5580->116; 5600->120; 5620->124; 5640->128
        5660->132; 5680->136; 5700->140; 5720->144; 5745->149; 5765->153; 5785->157; 5805->161; 5825->165
        else->0
    }

    private fun parseSec(cap: String): String = when {
        cap.contains("WPA3")||cap.contains("SAE")->"WPA3"
        cap.contains("WPA2")->"WPA2"; cap.contains("WPA")->"WPA"; cap.contains("WEP")->"WEP"
        cap.contains("OWE")->"OWE"
        cap.isNotBlank()&&cap!="[ESS]"->"WPA/WPA2"
        else->"OPEN"
    }
}
