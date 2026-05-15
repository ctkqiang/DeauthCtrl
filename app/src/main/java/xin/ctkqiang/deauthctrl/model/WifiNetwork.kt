package xin.ctkqiang.deauthctrl.model

/**
 * 附近 Wi-Fi 网络信息
 *
 * @param ssid 网络名称 (可为空，隐藏 SSID 时为空字符串)
 * @param bssid BSSID (MAC 地址)
 * @param signalStrength 信号强度 (dBm)，-40 极强，-90 极弱
 * @param frequency 频率 (MHz)，如 2412、5180
 * @param channel 信道号 (由频率推算)
 * @param capabilities 安全能力字符串，如 "[WPA2-PSK-CCMP][ESS]"
 * @param band 频段: "2.4 GHz" 或 "5 GHz"
 * @param security 解析后的安全类型: WPA3/WPA2/WPA/WEP/OPEN
 */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int,
    val frequency: Int,
    val capabilities: String,
    val channel: Int = frequencyToChannel(frequency),
    val band: String = if (frequency > 4000) "5 GHz" else "2.4 GHz",
    val security: String = parseSecurity(capabilities),
) {
    companion object {
        /** 频率转信道号 (2.4 GHz = 1-14, 5 GHz = 36-165) */
        fun frequencyToChannel(freq: Int): Int = when (freq) {
            2412 -> 1; 2417 -> 2; 2422 -> 3; 2427 -> 4; 2432 -> 5
            2437 -> 6; 2442 -> 7; 2447 -> 8; 2452 -> 9; 2457 -> 10
            2462 -> 11; 2467 -> 12; 2472 -> 13; 2484 -> 14
            5180 -> 36; 5200 -> 40; 5220 -> 44; 5240 -> 48
            5260 -> 52; 5280 -> 56; 5300 -> 60; 5320 -> 64
            5500 -> 100; 5520 -> 104; 5540 -> 108; 5560 -> 112
            5580 -> 116; 5600 -> 120; 5620 -> 124; 5640 -> 128
            5660 -> 132; 5680 -> 136; 5700 -> 140; 5720 -> 144
            5745 -> 149; 5765 -> 153; 5785 -> 157; 5805 -> 161; 5825 -> 165
            else -> 0
        }

        /** 解析安全能力字符串，提取最高级别加密 */
        fun parseSecurity(cap: String): String = when {
            cap.contains("WPA3") -> "WPA3"
            cap.contains("WPA2") -> "WPA2"
            cap.contains("WPA") -> "WPA"
            cap.contains("WEP") -> "WEP"
            cap.contains("OWE") -> "OWE"
            cap.contains("SAE") -> "WPA3"
            cap.isNotBlank() && cap != "[ESS]" -> "WPA/WPA2"
            else -> "OPEN"
        }
    }
}

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
