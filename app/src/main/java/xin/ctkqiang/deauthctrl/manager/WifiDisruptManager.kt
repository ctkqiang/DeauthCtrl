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

    fun scanNetworks() {
        if (_isFlooding.value) {
            _scanState.value = ScanState.Error("泛洪进行中，无法扫描")
            return
        }
        _scanState.value = ScanState.Scanning

        scope.launch(Dispatchers.Default) {
            try {
                val cached = wifiManager.scanResults
                    ?.filter { it.SSID.isNotBlank() }
                    ?.distinctBy { it.BSSID }
                    ?.sortedByDescending { it.level }
                    ?.map { it.toWifiNetwork() }
                    ?: emptyList()

                if (cached.isNotEmpty()) {
                    _scanState.value = ScanState.Results(cached)
                }

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
                        tryStartHotspot(targetSsid)
                    } else {
                        tryStopHotspot()
                    }

                    _hotspotResult.value = HotspotResult.Progress(cycle + 1, totalCycles.toInt())
                    delay(toggleIntervalMs)
                    cycle++
                }

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

    private var hotspotCallback: android.net.wifi.WifiManager.LocalOnlyHotspotCallback? = null

    private fun tryStartHotspot(ssid: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                hotspotCallback = object : android.net.wifi.WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: android.net.wifi.WifiManager.LocalOnlyHotspotReservation?) {
                    }

                    override fun onStopped() {
                    }

                    override fun onFailed(reason: Int) {
                    }
                }
                wifiManager.startLocalOnlyHotspot(
                    hotspotCallback!!,
                    null,
                )
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun tryStopHotspot() {
        try {
            hotspotCallback = null
        } catch (_: Exception) {
        }
    }

    private fun ScanResult.toWifiNetwork(): WifiNetwork = WifiNetwork(
        ssid = SSID.ifBlank { "<hidden>" },
        bssid = BSSID,
        signalStrength = level,
        frequency = frequency,
        capabilities = capabilities,
    )
}