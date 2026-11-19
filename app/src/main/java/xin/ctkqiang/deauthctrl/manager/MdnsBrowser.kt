package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.net.wifi.WifiManager
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.random.Random

/**
 * mDNS 服务发现 —— 局域网内 Bonjour/Avahi 服务浏览器
 *
 * 阶段 1:查询 `_services._dns-sd._udp.local` 枚举全部服务类型;
 * 阶段 2:对每类服务查 PTR 拿实例名;阶段 3:查 SRV/TXT/A 拿主机、端口、
 * 附加文本与 IPv4 地址。多播组 224.0.0.251:5353,响应在 deadline 内
 * 持续归并。发现的都是局域网里真实广播的服务,读不到就是没有。
 */
class MdnsBrowser(context: Context) {

    data class Service(
        val name: String,
        val type: String,
        val host: String,
        val port: Int,
        val txt: List<String>,
        val addresses: List<String>,
    )

    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    /** 阻塞式扫描;调用方需在 IO 线程执行,结果经 [onFound] 实时回调 */
    fun browse(timeoutMs: Int = 9000, onFound: (Service) -> Unit) {
        val socket = java.net.MulticastSocket(null)
        socket.reuseAddress = true
        socket.broadcast = true
        socket.soTimeout = 200
        socket.bind(InetSocketAddress(5353))
        val group = InetAddress.getByName("224.0.0.251")
        runCatching { socket.joinGroup(InetSocketAddress(group, 5353), null) }

        val lock = wifi?.createMulticastLock("deauthctrl-mdns")?.apply { setReferenceCounted(false); acquire() }
        try {
            val services = mutableSetOf<String>()
            val ptrs = mutableSetOf<String>()
            val srvMap = mutableMapOf<String, Pair<String, Int>>()
            val txtMap = mutableMapOf<String, List<String>>()
            val addrMap = mutableMapOf<String, MutableList<String>>()
            val queried = mutableSetOf<String>()
            val seenTypes = mutableSetOf<String>()
            val reported = mutableSetOf<String>()

            send(socket, buildQuery("_services._dns-sd._udp.local", PTR))
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(2048)

            while (System.currentTimeMillis() < deadline) {
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(pkt)
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                }
                val parsed = parse(buf, pkt.length)
                parsed.forEach { (name, type, data) ->
                    when (type) {
                        PTR -> if (name == "_services._dns-sd._udp.local") {
                            if (services.add(data)) queried.add(data)
                        } else {
                            if (ptrs.add(data) && !queried.contains(name)) {
                                queried.add(name)
                                send(socket, buildQuery(name, PTR))
                            }
                        }
                        SRV -> {
                            val parts = data.split(" ")
                            if (parts.size >= 3) {
                                srvMap[name] = parts[1] to (parts[2].toIntOrNull() ?: 0)
                                if (!queried.contains(name)) {
                                    queried.add(name)
                                    send(socket, buildQuery(name, TXT))
                                    send(socket, buildQuery(name, A))
                                }
                            }
                        }
                        TXT -> txtMap[name] = data.split("|")
                        A -> addrMap.getOrPut(name) { mutableListOf() }.add(data)
                    }
                    if (name.endsWith(".local")) seenTypes.add(name.substringAfter("_").substringBefore("._"))
                }
                // 阶段 1 枚举完成后,为每个新服务类型补发 PTR 查询
                services.forEach { s -> if (!queried.contains(s)) { queried.add(s); send(socket, buildQuery(s, PTR)) } }
                // 组装已完整(有 SRV)且未上报过的服务 —— 每台设备只回调一次
                srvMap.forEach { (name, hostPort) ->
                    if (reported.add(name)) {
                        val txt = txtMap[name] ?: emptyList()
                        val addrs = addrMap[name] ?: emptyList()
                        onFound(
                            Service(
                                name = name,
                                type = name.substringAfter("._").substringBefore(".local"),
                                host = hostPort.first,
                                port = hostPort.second,
                                txt = txt,
                                addresses = addrs,
                            ),
                        )
                    }
                }
            }
        } finally {
            runCatching { socket.leaveGroup(InetSocketAddress(group, 5353), null) }
            runCatching { socket.close() }
            lock?.release()
        }
    }

    /** 复用 DnsResolver 的报文构造,查询类型固定 PTR/SRV/TXT/A */
    private fun buildQuery(name: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream(64)
        val id = Random.nextInt(0, 65536)
        out.write((id shr 8) and 0xFF); out.write(id and 0xFF)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x01)
        for (i in 0 until 6) out.write(0x00)
        name.trimEnd('.').split('.').forEach { label ->
            val b = label.toByteArray(Charsets.UTF_8)
            val len = b.size.coerceAtMost(63)
            out.write(len)
            out.write(b, 0, len)
        }
        out.write(0x00)
        out.write((type shr 8) and 0xFF); out.write(type and 0xFF)
        out.write(0x00); out.write(0x01)
        return out.toByteArray()
    }

    /** 极简 mDNS 响应解析:返回 (名称, 类型, 文本数据) 三元组 */
    private fun parse(buf: ByteArray, length: Int): List<Triple<String, Int, String>> {
        if (length < 12) return emptyList()
        val out = mutableListOf<Triple<String, Int, String>>()
        var pos = 12
        // 跳过问题段
        val qd = ((buf[4].toInt() and 0xFF) shl 8) or (buf[5].toInt() and 0xFF)
        repeat(qd) { pos = skipName(buf, pos); pos += 4 }
        val an = ((buf[6].toInt() and 0xFF) shl 8) or (buf[7].toInt() and 0xFF)
        repeat(an) {
            val (name, nameEnd) = readName(buf, pos)
            pos = nameEnd
            val type = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
            pos += 2
            pos += 2 // class
            pos += 4 // ttl
            val rdLen = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
            pos += 2
            val end = pos + rdLen
            val data = when (type) {
                PTR, CNAME, NS -> readName(buf, pos).first
                SRV -> {
                    val p0 = pos + 4
                    val target = readName(buf, p0).first
                    val port = ((buf[pos + 2].toInt() and 0xFF) shl 8) or (buf[pos + 3].toInt() and 0xFF)
                    "$target $port"
                }
                TXT -> {
                    val sb = StringBuilder()
                    var p = pos
                    while (p < end) {
                        val l = buf[p].toInt() and 0xFF
                        if (sb.isNotEmpty()) sb.append("|")
                        sb.append(String(buf, p + 1, l, Charsets.UTF_8))
                        p += l + 1
                    }
                    sb.toString()
                }
                A -> if (rdLen == 4) InetAddress.getByAddress(buf.copyOfRange(pos, end)).hostAddress else "--"
                AAAA -> if (rdLen == 16) InetAddress.getByAddress(buf.copyOfRange(pos, end)).hostAddress else "--"
                else -> "--"
            }
            out.add(Triple(name, type, data))
            pos = end
        }
        return out
    }

    private fun skipName(buf: ByteArray, start: Int): Int {
        var p = start
        while (p < buf.size) {
            val len = buf[p].toInt() and 0xFF
            if (len == 0) return p + 1
            if (len and 0xC0 == 0xC0) return p + 2
            p += len + 1
        }
        return p
    }

    private fun readName(buf: ByteArray, start: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var p = start
        var jumped = false
        var endPos = start
        while (p < buf.size) {
            val len = buf[p].toInt() and 0xFF
            if (len == 0) { if (!jumped) endPos = p + 1; p++; break }
            if (len and 0xC0 == 0xC0) {
                if (!jumped) endPos = p + 2
                p = ((len and 0x3F) shl 8) or (buf[p + 1].toInt() and 0xFF)
                jumped = true
                continue
            }
            labels.add(String(buf, p + 1, len, Charsets.UTF_8))
            p += len + 1
        }
        return labels.joinToString(".") to if (jumped) endPos else p
    }

    private fun send(socket: DatagramSocket, data: ByteArray) {
        runCatching {
            socket.send(DatagramPacket(data, data.size, InetAddress.getByName("224.0.0.251"), 5353))
        }
    }

    private companion object {
        const val PTR = 12
        const val SRV = 33
        const val TXT = 16
        const val A = 1
        const val AAAA = 28
        const val CNAME = 5
        const val NS = 2
    }
}
