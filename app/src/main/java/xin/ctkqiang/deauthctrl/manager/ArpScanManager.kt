package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.util.concurrent.ConcurrentHashMap
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class ArpEntry(
    val ip: String,
    val mac: String,
    val hostname: String,
    val latency: String,
)

class ArpScanManager {

    @Volatile var isRunning = false
        private set
    private var job: Job? = null

    fun scan(callback: (ArpEntry) -> Unit, onComplete: () -> Unit) {
        stop()
        isRunning = true
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val localIp = getLocalIp() ?: run { onComplete(); return@launch }
                val prefix = localIp.substringBeforeLast(".")
                val result = ConcurrentHashMap<String, ArpEntry>()

                val arpCache = readArpCache()
                arpCache.forEach { (ip, mac) -> result[ip] = ArpEntry(ip, mac, "", "") }

                val jobs = (1..254).map { host ->
                    async {
                        val ip = "$prefix.$host"
                        val reachable = ping(ip)
                        if (reachable) {
                            val mac = arpCache[ip] ?: ""
                            val hostname = try { InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip } ?: "" } catch (_: Exception) { "" }
                            val entry = ArpEntry(ip, mac, hostname, if (mac.isEmpty() && hostname.isEmpty()) "" else "up")
                            result.putIfAbsent(ip, entry)
                            withContext(Dispatchers.Main) { callback(entry) }
                        }
                    }
                }
                jobs.awaitAll()
            } finally {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    private fun ping(ip: String): Boolean {
        try {
            val proc = Runtime.getRuntime().exec("ping -c 1 -W 2 $ip")
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var reachable = false
            reader.forEachLine { if (it.contains("1 received") || it.contains("1 packets received")) reachable = true }
            reader.close()
            proc.waitFor()
            if (reachable) return true
        } catch (_: Exception) {}

        return try { InetAddress.getByName(ip).isReachable(1500) } catch (_: Exception) { false }
    }

    private fun readArpCache(): Map<String, String> {
        val cache = mutableMapOf<String, String>()
        try {
            BufferedReader(InputStreamReader(Runtime.getRuntime().exec("cat /proc/net/arp").inputStream)).use { reader ->
                reader.readLine()
                reader.forEachLine { line ->
                    val p = line.trim().split("\\s+".toRegex())
                    if (p.size >= 4 && p[3] != "00:00:00:00:00:00") cache[p[0]] = p[3].uppercase()
                }
            }
        } catch (_: Exception) {}
        return cache
    }

    private fun getLocalIp(): String? {
        try {
            NetworkInterface.getNetworkInterfaces().toList().forEach { iface ->
                iface.inetAddresses.toList().forEach { addr ->
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.hostAddress?.startsWith("192.168.") == true) {
                        return addr.hostAddress
                    }
                }
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
