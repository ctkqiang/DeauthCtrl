package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject
import org.json.JSONArray

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
                if (q.isEmpty()) { onStatus("Please enter a search query"); onComplete(); return@launch }

                if (q.uppercase().startsWith("CVE-")) {
                    searchById(q, onResult, onStatus)
                } else {
                    searchByKeyword(q, onResult, onStatus)
                }
            } catch (e: Exception) { onStatus("Error: ${e.message}") }
            finally { withContext(Dispatchers.Main) { onComplete() } }
        }
    }

    private suspend fun searchById(cveId: String, onResult: (CVEResult) -> Unit, onStatus: (String) -> Unit) {
        val apiUrl = "https://cve.circl.lu/api/id/$cveId"
        onStatus("Fetching $cveId...")
        val body = httpGet(apiUrl)
        if (body == null) { onStatus("Network error — check connection"); return }
        if (body.startsWith("{") && body.contains("\"id\"")) {
            val json = JSONObject(body)
            val id = json.optString("id", cveId)
            val desc = json.optString("summary", "").take(300)
            val cvss = json.optJSONObject("cvss")?.optDouble("score")?.toFloat()
            val published = json.optString("Published", "").take(10)
            onResult(CVEResult(id, desc, cvss, published))
            onStatus("OK")
        } else { onStatus("CVE not found") }
    }

    private suspend fun searchByKeyword(keyword: String, onResult: (CVEResult) -> Unit, onStatus: (String) -> Unit) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val apiUrl = "https://cve.circl.lu/api/search/$encoded"
        onStatus("Searching...")
        val body = httpGet(apiUrl)
        if (body == null) { onStatus("Network error — check connection"); return }
        if (body.startsWith("{") && body.contains("\"data\"")) {
            val json = JSONObject(body)
            val data = json.optJSONArray("data")
            if (data != null && data.length() > 0) {
                val count = minOf(data.length(), 25)
                for (i in 0 until count) {
                    val item = data.getJSONObject(i)
                    val id = item.optString("id", "?")
                    val desc = item.optString("summary", "").take(250)
                    val cvss = item.optJSONObject("cvss")?.optDouble("score")?.toFloat()
                    val published = item.optString("Published", "").take(10)
                    withContext(Dispatchers.Main) { onResult(CVEResult(id, desc, cvss, published)) }
                }
                onStatus("Found ${data.length()} results (showing $count)")
            } else onStatus("No results found for \"$keyword\"")
        } else onStatus("No results found")
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000; conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "DeauthCtrl/1.0")
            val code = conn.responseCode
            if (code !in 200..299) { conn.disconnect(); return null }
            val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.disconnect()
            body
        } catch (e: Exception) { null }
    }

    fun stop() { isRunning = false; job?.cancel(); job = null }
}
