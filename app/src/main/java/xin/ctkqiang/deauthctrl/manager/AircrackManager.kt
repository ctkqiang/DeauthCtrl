package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aircrack 审计台 —— 附近 Wi-Fi 安全审计(免 root):
 * 加密协议识别 / WPS 标志 / 信号与信道 / 风险评分。
 * 手机上无监听模式 —— 诚实边界:审计评分,不破解。
 */
class AircrackManager(private val context: Context? = null) {

    data class ApRisk(
        val ssid: String,
        val bssid: String,
        val security: String,
        val wps: Boolean,
        val dbm: Int,
        val channel: Int,
        val score: Int,
        val advice: String,
    )

    private val wifi = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    /** 单 AP 评分(纯逻辑,可单测):开放/WEP=高危,WPA1/WPS=中危 */
    fun scoreAp(capabilities: String): Pair<String, Boolean> {
        val c = capabilities.uppercase()
        val wps = c.contains("WPS")
        val security = when {
            c.contains("WPA3") -> "WPA3"
            c.contains("WPA2") -> "WPA2"
            c.contains("WPA-") || c.contains("WPA ") -> "WPA"
            c.contains("WEP") -> "WEP"
            c.contains("OPEN") || c == "[ESS]" -> "OPEN"
            else -> "?"
        }
        return security to wps
    }

    fun riskOf(security: String, wps: Boolean): Pair<Int, String> = when {
        security == "OPEN" -> 95 to "开放网络:流量明文,任何人可接入嗅探"
        security == "WEP" -> 90 to "WEP 已被破解多年,分钟级可破"
        security == "WPA" -> 60 to "WPA1(TKIP)已过时,建议升级 WPA2/3"
        wps -> 50 to "WPS 开启:存在 PIN 暴力破解面"
        security == "WPA2" -> 20 to "WPA2 基本安全;禁 WPS、用强口令"
        security == "WPA3" -> 5 to "WPA3 当前最佳"
        else -> 30 to "未知加密"
    }

    /** 扫描附近 AP 并评分 */
    suspend fun scan(): List<ApRisk> = withContext(Dispatchers.IO) {
        val results = runCatching {
            @Suppress("MissingPermission")
            wifi?.scanResults ?: emptyList<ScanResult>()
        }.getOrElse { emptyList() }
        results.distinctBy { it.BSSID }.sortedBy { it.level }.map { r ->
            val (sec, wps) = scoreAp(r.capabilities)
            val (score, advice) = riskOf(sec, wps)
            ApRisk(
                ssid = r.SSID.ifBlank { "<hidden>" },
                bssid = r.BSSID,
                security = sec,
                wps = wps,
                dbm = r.level,
                channel = r.frequency,
                score = score,
                advice = advice,
            )
        }
    }
}
