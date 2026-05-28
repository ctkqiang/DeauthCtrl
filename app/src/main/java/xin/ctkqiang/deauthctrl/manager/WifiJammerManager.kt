package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiJammerManager(private val context: Context) {

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var jammerJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _log = MutableStateFlow<List<JammerLogEntry>>(emptyList())
    val log: StateFlow<List<JammerLogEntry>> = _log.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var cycleCount = 0
    private var beaconCount = 0

    fun start(targetSsids: List<String>, toggleMs: Long = 50L) {
        if (_isRunning.value) return
        if (!wifiManager.isWifiEnabled) { _error.value = "请先开启 Wi-Fi"; return }
        _isRunning.value = true
        _error.value = null
        cycleCount = 0
        beaconCount = 0

        val ssids = if (targetSsids.isEmpty()) listOf("_JAMMER_") else targetSsids

        jammerJob = scope.launch {
            log("JAMMER", "WiFi 压制启动 | ${ssids.size} SSID | ${toggleMs}ms间隔")
            var ssidIdx = 0
            while (isActive && _isRunning.value) {
                val hot = ssids[ssidIdx % ssids.size]
                tryStartHotspot(hot)
                delay(toggleMs)
                tryStopHotspot()
                beaconCount++
                ssidIdx++
                if (ssidIdx % (ssids.size * 2) == 0) { cycleCount++ }
                delay(toggleMs)
            }
            tryStopHotspot()
            log("JAMMER", "WiFi 压制完成 | 发送~$beaconCount Beacon | $cycleCount 轮")
        }
    }

    fun stop() {
        _isRunning.value = false
        jammerJob?.cancel()
        tryStopHotspot()
    }

    fun destroy() { stop(); scope.cancel() }
    fun clearLog() { _log.value = emptyList(); cycleCount = 0; beaconCount = 0 }

    fun getBeaconCount(): Int = beaconCount
    fun getCycleCount(): Int = cycleCount

    private fun tryStartHotspot(ssid: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                wifiManager.startLocalOnlyHotspot(
                    object : android.net.wifi.WifiManager.LocalOnlyHotspotCallback() {
                        override fun onStarted(r: android.net.wifi.WifiManager.LocalOnlyHotspotReservation?) {}
                        override fun onStopped() {}
                        override fun onFailed(reason: Int) {}
                    }, null)
            }
        } catch (_: Exception) {}
    }

    private var hotspotCb: android.net.wifi.WifiManager.LocalOnlyHotspotCallback? = null
    private fun tryStopHotspot() {
        try { hotspotCb = null } catch (_: Exception) {}
    }

    private fun log(module: String, msg: String) {
        _log.value = (_log.value + JammerLogEntry(module = module, message = msg)).takeLast(200)
    }
}
