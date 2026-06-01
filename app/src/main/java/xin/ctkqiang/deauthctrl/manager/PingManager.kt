package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

data class PingResult(
    val sequence: Int,
    val ip: String,
    val ttl: Int,
    val timeMs: Float,
    val bytes: Int,
)

class PingManager {

    @Volatile var isRunning = false
        private set
    private var job: Job? = null

    fun start(
        host: String,
        count: Int = 0,
        interval: Long = 200,
        onResult: (PingResult) -> Unit,
        onStats: (sent: Int, received: Int, loss: Int, min: Float, avg: Float, max: Float) -> Unit,
        onComplete: () -> Unit,
    ) {
        stop()
        isRunning = true
        job = CoroutineScope(Dispatchers.IO).launch {
            var seq = 0
            var sent = 0
            var recv = 0
            val times = mutableListOf<Float>()

            try {
                while (isRunning && (count == 0 || seq < count)) {
                    seq++
                    val result = pingOnce(host, seq, interval)
                    sent++
                    if (result != null) {
                        recv++
                        times.add(result.timeMs)
                        withContext(Dispatchers.Main) { onResult(result) }
                    }
                    withContext(Dispatchers.Main) {
                        onStats(
                            sent, recv,
                            if (sent > 0) ((sent - recv) * 100 / sent) else 0,
                            times.minOrNull() ?: 0f,
                            if (times.isNotEmpty()) times.average().toFloat() else 0f,
                            times.maxOrNull() ?: 0f,
                        )
                    }
                    delay(interval)
                }
            } finally {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    private fun pingOnce(host: String, seq: Int, timeout: Long): PingResult? {
        try {
            val cmd = "ping -c 1 -W ${(timeout / 1000).coerceAtLeast(1)} $host"
            val proc = Runtime.getRuntime().exec(cmd)
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val errReader = BufferedReader(InputStreamReader(proc.errorStream))
            val output = reader.readText() + errReader.readText()
            reader.close(); errReader.close()
            proc.waitFor()

            val timeMatch = Regex("time[=<](\\d+\\.?\\d*)\\s*ms").find(output)
            val ttlMatch = Regex("ttl[=<](\\d+)").find(output)
            val bytesMatch = Regex("(\\d+)\\s*bytes").find(output)

            if (timeMatch != null) {
                return PingResult(
                    sequence = seq,
                    ip = host,
                    ttl = ttlMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                    timeMs = timeMatch.groupValues[1].toFloat(),
                    bytes = bytesMatch?.groupValues?.get(1)?.toIntOrNull() ?: 64,
                )
            }
        } catch (_: Exception) {}
        return null
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }
}
