package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

data class PortResult(
    val port: Int,
    val state: String,
    val service: String,
    val banner: String = "",
)

class PortScannerManager {

    private val commonPorts = mapOf(
        21 to "ftp", 22 to "ssh", 23 to "telnet", 25 to "smtp", 53 to "domain",
        80 to "http", 110 to "pop3", 111 to "rpcbind", 135 to "msrpc", 139 to "netbios-ssn",
        143 to "imap", 443 to "https", 445 to "microsoft-ds", 993 to "imaps", 995 to "pop3s",
        1723 to "pptp", 3306 to "mysql", 3389 to "ms-wbt-server", 5432 to "postgresql",
        5900 to "vnc", 6379 to "redis", 8080 to "http-proxy", 8443 to "https-alt",
        27017 to "mongodb", 5000 to "upnp", 5222 to "xmpp", 4444 to "meterpreter",
        7547 to "cwmp", 8888 to "http-alt", 9000 to "php-fpm", 9090 to "cassandra",
        9200 to "elasticsearch", 11211 to "memcached", 27015 to "steam",
    )

    @Volatile var isRunning = false
        private set
    private var job: Job? = null

    fun scan(
        host: String,
        ports: List<Int> = commonPorts.keys.toList(),
        timeoutMs: Int = 800,
        onResult: (PortResult) -> Unit,
        onProgress: (Int, Int) -> Unit,
        onComplete: () -> Unit,
    ) {
        stop()
        isRunning = true
        var scanned = 0
        val total = ports.size

        job = CoroutineScope(Dispatchers.IO).launch {
            for (port in ports) {
                if (!isRunning) break
                val result = checkPort(host, port, timeoutMs)
                scanned++
                withContext(Dispatchers.Main) {
                    onResult(result)
                    onProgress(scanned, total)
                }
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    private fun checkPort(host: String, port: Int, timeout: Int): PortResult {
        val service = commonPorts[port] ?: "unknown"
        return try {
            val sock = Socket()
            sock.connect(InetSocketAddress(host, port), timeout)
            val state = "open"
            var banner = ""
            try {
                sock.soTimeout = 500
                val input = sock.getInputStream()
                val buf = ByteArray(256)
                val n = input.read(buf)
                if (n > 0) banner = String(buf, 0, n).trim().replace("\n", " ").replace("\r", "").take(60)
            } catch (_: Exception) {}
            sock.close()
            PortResult(port, state, service, banner)
        } catch (_: Exception) {
            PortResult(port, "closed", service)
        }
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }
}
