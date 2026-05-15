package xin.ctkqiang.deauthctrl.model

/**
 * Represents a nearby Wi-Fi network discovered via WifiManager scan.
 */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int,   // dBm, e.g. -40 (strong) to -90 (weak)
    val frequency: Int,         // MHz, e.g. 2412, 5180
    val capabilities: String,   // e.g. "[WPA2-PSK-CCMP][ESS]"
)

/**
 * Hotspot operation result status.
 */
sealed class HotspotResult {
    data class Created(val ssid: String) : HotspotResult()
    data class Progress(val cycle: Int, val totalCycles: Int) : HotspotResult()
    object Completed : HotspotResult()
    data class Error(val message: String) : HotspotResult()
}

/**
 * Wi-Fi scan state.
 */
sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Results(val networks: List<WifiNetwork>) : ScanState()
    data class Error(val message: String) : ScanState()
}
