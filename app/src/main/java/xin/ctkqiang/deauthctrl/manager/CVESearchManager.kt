package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

data class CVEResult(val id: String, val description: String, val cvss: Float?, val published: String)

class CVESearchManager {
    @Volatile var isRunning = false; private set
    private var job: Job? = null

    fun search(query: String, onResult: (CVEResult) -> Unit, onStatus: (String) -> Unit, onComplete: () -> Unit) {
        stop()
        isRunning = true
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val q = query.trim()
                if (q.isEmpty()) { onStatus("请输入搜索关键词"); onComplete(); return@launch }

                if (q.uppercase().startsWith("CVE-")) searchById(q, onResult, onStatus)
                else searchByKeyword(q, onResult, onStatus)
            } catch (e: Exception) { onStatus("错误: ${e.message}") }
            finally { withContext(Dispatchers.Main) { onComplete() } }
        }
    }

    private suspend fun searchById(cveId: String, onResult: (CVEResult) -> Unit, onStatus: (String) -> Unit) {
        onStatus("查询 $cveId ...")
        val url = "https://services.nvd.nist.gov/rest/json/cves/2.0?cveId=$cveId"
        val body = httpGet(url) ?: run { onStatus("网络不通 — 请检查 WiFi/数据连接"); return }
        parseNvdResponse(body, onResult, onStatus)
    }

    private suspend fun searchByKeyword(keyword: String, onResult: (CVEResult) -> Unit, onStatus: (String) -> Unit) {
        onStatus("搜索 \"$keyword\" ...")
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://services.nvd.nist.gov/rest/json/cves/2.0?keywordSearch=$encoded&resultsPerPage=20"
        val body = httpGet(url) ?: run { onStatus("网络不通 — 请检查 WiFi/数据连接"); return }
        parseNvdResponse(body, onResult, onStatus)
    }

    private suspend fun parseNvdResponse(body: String, onResult: (CVEResult) -> Unit, onStatus: (String) -> Unit) {
        try {
            val root = JSONObject(body)
            val vulns = root.optJSONArray("vulnerabilities")
            if (vulns == null || vulns.length() == 0) { onStatus("未找到结果"); return }
            val count = minOf(vulns.length(), 20)
            for (i in 0 until count) {
                val cve = vulns.getJSONObject(i).optJSONObject("cve") ?: continue
                val id = cve.optString("id", "?")
                val descArr = cve.optJSONArray("descriptions")
                val desc = if (descArr != null && descArr.length() > 0) descArr.getJSONObject(0).optString("value", "").take(250) else ""
                val cvss = cve.optJSONObject("metrics")?.optJSONObject("cvssMetricV31")
                    ?.optJSONArray("cvssData")?.optJSONObject(0)?.optDouble("baseScore")?.toFloat()
                    ?: cve.optJSONObject("metrics")?.optJSONObject("cvssMetricV30")
                    ?.optJSONArray("cvssData")?.optJSONObject(0)?.optDouble("baseScore")?.toFloat()
                val published = cve.optString("published", "").take(10)
                withContext(Dispatchers.Main) { onResult(CVEResult(id, desc, cvss, published)) }
            }
            onStatus("找到 ${vulns.length()} 条结果")
        } catch (e: Exception) { onStatus("解析失败: ${e.message}") }
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000; conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "DeauthCtrl/1.0")
            if (conn.responseCode !in 200..299) { conn.disconnect(); return null }
            val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.disconnect(); body
        } catch (e: Exception) { null }
    }

    fun stop() { isRunning = false; job?.cancel(); job = null }
}
