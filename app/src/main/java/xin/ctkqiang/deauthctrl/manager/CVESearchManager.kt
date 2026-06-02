package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CVEResult(val id: String, val description: String, val cvss: Float?, val published: String)

class CVESearchManager {
    @Volatile var isRunning = false; private set
    private var job: Job? = null

    fun search(query: String, onResult: (CVEResult) -> Unit, onProgress: (String) -> Unit, onComplete: () -> Unit) {
        stop()
        isRunning = true
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val apiUrl = "https://cve.circl.lu/api/search/$encoded"
                onProgress("Searching CVE database...")
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "DeauthCtrl/1.0")
                val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                conn.disconnect()
                val json = org.json.JSONObject(body)
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    for (i in 0 until minOf(data.length(), 30)) {
                        val item = data.getJSONObject(i)
                        val id = item.optString("id", "?")
                        val desc = item.optString("summary", "").take(200)
                        val cvss = item.optJSONObject("cvss")?.optDouble("score")?.toFloat()
                        val published = item.optString("Published", "").take(10)
                        withContext(Dispatchers.Main) { onResult(CVEResult(id, desc, cvss, published)) }
                    }
                    onProgress("Found ${data.length()} results")
                } else onProgress("No results found")
            } catch (e: Exception) { onProgress("Error: ${e.message}") }
            finally { withContext(Dispatchers.Main) { onComplete() } }
        }
    }

    fun stop() { isRunning = false; job?.cancel(); job = null }
}
