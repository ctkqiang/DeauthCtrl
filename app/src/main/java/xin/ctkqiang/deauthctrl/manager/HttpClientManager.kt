package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, List<String>>,
    val body: String,
    val timeMs: Long,
)

class HttpClientManager {

    suspend fun execute(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true

            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (!headers.containsKey("User-Agent")) conn.setRequestProperty("User-Agent", "DeauthCtrl/1.0")

            if (body.isNotEmpty() && method in listOf("POST", "PUT", "PATCH")) {
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream).use { it.write(body); it.flush() }
            }

            val status = conn.responseCode
            val statusMsg = conn.responseMessage ?: ""
            val respHeaders = conn.headerFields ?: emptyMap()

            val input = try { conn.inputStream } catch (_: Exception) { conn.errorStream }
            val respBody = input?.let { BufferedReader(InputStreamReader(it)).readText() } ?: ""
            val elapsed = System.currentTimeMillis() - start

            HttpResponse(status, statusMsg, respHeaders, respBody, elapsed)
        } finally {
            conn.disconnect()
        }
    }
}
