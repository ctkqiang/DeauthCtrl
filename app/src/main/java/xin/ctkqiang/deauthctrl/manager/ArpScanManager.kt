package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * ARP 表条目数据类
 *
 * 表示局域网内一台已发现设备的网络信息。
 *
 * @property ip IPv4 地址（如 "192.168.1.105"）
 * @property mac MAC 地址（如 "D0:22:12:AB:CD:EF"），可能为空字符串（无 root 权限时无法读取 ARP 表）
 * @property hostname DNS 反向解析主机名（如 "galaxy-s24.local"），可能为空
 * @property latency 探测时延，目前仅用于标记设备是否可达（"up" 或空字符串）
 */
data class ArpEntry(
    val ip: String,
    val mac: String,
    val hostname: String,
    val latency: String,
)

/**
 * 局域网 ARP 设备发现管理器
 *
 * 通过 Ping Sweep（子网扫描）+ /proc/net/arp 读取相结合的方式发现局域网设备。
 * 专为 Android 10+ 设计：/proc/net/arp 在较新系统上受限，因此 Ping Sweep 作为主要发现机制。
 *
 * ## 工作流程
 * 1. 获取本机 WiFi IP 地址（192.168.x.x 格式）
 * 2. 对 /24 子网的全部 254 个 IP 并发执行 Ping 探测
 * 3. 同时尝试读取 /proc/net/arp 缓存获取 MAC 地址（需要 root 或旧版 Android）
 * 4. 对可达主机尝试 DNS 反向解析获取主机名
 * 5. 通过回调实时通知 UI 新发现的设备
 *
 * ## 并发策略
 * - 使用 Kotlin 协程 `async` 对 254 个 IP 并发 ping
 * - 使用 ConcurrentHashMap 保证多协程写入安全
 * - 回调通过 withContext(Dispatchers.Main) 切换到主线程
 */
class ArpScanManager {

    /**
     * 扫描运行状态
     *
     * @Volatile 跨协程/线程可见
     */
    @Volatile var isRunning = false
        private set

    /** 当前扫描的协程 Job */
    private var job: Job? = null

    /**
     * 执行 ARP 设备扫描
     *
     * 完整流程：获取本机 IP → 并发 Ping /24 子网 → 解析 ARP 表 → DNS 反查 → 实时回调。
     *
     * @param callback 每发现一台设备时回调（主线程），包含 IP/MAC/主机名信息
     * @param onComplete 扫描完全结束时的回调（主线程）
     */
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

    /**
     * 对单个 IP 执行可达性探测
     *
     * 优先使用系统 ping 命令（成功率更高），失败时回退到 InetAddress.isReachable()。
     *
     * @param ip 目标 IPv4 地址
     * @return true 表示 ICMP Echo Reply 已收到
     */
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

    /**
     * 读取系统 ARP 缓存表
     *
     * 通过执行 `cat /proc/net/arp` 命令读取内核 ARP 缓存。
     * 注意：Android 10+ 对 /proc/net 的访问受限，此方法可能返回空结果。
     *
     * @return Map<IP地址, MAC地址>，过滤掉 00:00:00:00:00:00（不完整条目）
     */
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

    /**
     * 获取本机局域网 IP 地址
     *
     * 遍历所有网络接口，寻找第一个 192.168.x.x 的 IPv4 地址。
     *
     * @return IPv4 地址字符串，若未连接任何局域网则返回 null
     */
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

    /**
     * 停止扫描
     *
     * 取消协程 Job，设置运行标志为 false。
     */
    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }
}
