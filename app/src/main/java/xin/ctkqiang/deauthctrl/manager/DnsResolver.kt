package xin.ctkqiang.deauthctrl.manager

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.random.Random

/** DNS 记录类型 —— 数值与展示标签的单一出处 */
object DnsType {
    const val A = 1
    const val NS = 2
    const val CNAME = 5
    const val SOA = 6
    const val PTR = 12
    const val MX = 15
    const val TXT = 16
    const val AAAA = 28

    fun label(type: Int): String = when (type) {
        A -> "A"
        NS -> "NS"
        CNAME -> "CNAME"
        SOA -> "SOA"
        PTR -> "PTR"
        MX -> "MX"
        TXT -> "TXT"
        AAAA -> "AAAA"
        else -> "TYPE$type"
    }
}

/**
 * 手写 DNS 查询 —— 零依赖构造与解析 DNS 报文
 *
 * 报文按 RFC 1035 手工组装(头 + 问题段),响应解析支持名称压缩指针
 * (0xC0)。A/AAAA/CNAME/NS/MX/TXT/SOA/PTR 全部落为可读文本。
 */
class DnsResolver {

    data class Answer(val name: String, val type: Int, val ttl: Long, val data: String)

    data class Result(val server: String, val queryName: String, val answers: List<Answer>, val elapsedMs: Long)

    /** 同步查询一次;调用方需在 IO 线程执行 */
    fun querySync(server: String, name: String, type: Int, timeoutMs: Int = 4000): Result {
        val socket = DatagramSocket()
        socket.soTimeout = timeoutMs
        try {
            val id = Random.nextInt(0, 65536)
            val query = buildQuery(id, name, type)
            val started = System.currentTimeMillis()
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
            val buf = ByteArray(4096)
            val pkt = DatagramPacket(buf, buf.size)
            socket.receive(pkt)
            val elapsed = System.currentTimeMillis() - started
            return Result(server, name, parse(buf, pkt.length), elapsed)
        } finally {
            runCatching { socket.close() }
        }
    }

    /** 组装查询报文:头(12B) + 问题段,名称不压缩 */
    private fun buildQuery(id: Int, name: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream(64)
        // 头:ID + 标志(RD=1) + QDCOUNT=1,其余全零
        out.write((id shr 8) and 0xFF); out.write(id and 0xFF)
        out.write(0x01); out.write(0x00)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        // 问题:标签序列 + 根终止 + QTYPE + QCLASS=IN
        name.trimEnd('.').split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.UTF_8)
            val len = bytes.size.coerceAtMost(63)
            out.write(len)
            out.write(bytes, 0, len)
        }
        out.write(0x00)
        out.write((type shr 8) and 0xFF); out.write(type and 0xFF)
        out.write(0x00); out.write(0x01)
        return out.toByteArray()
    }

    /** 解析响应:跳过问题段,读出全部答案记录 */
    private fun parse(buf: ByteArray, length: Int): List<Answer> {
        if (length < 12) return emptyList()
        val reader = Reader(buf, length)
        reader.pos = 12
        // 跳过问题段(名称 + 4B)
        val qdCount = ((buf[4].toInt() and 0xFF) shl 8) or (buf[5].toInt() and 0xFF)
        repeat(qdCount) {
            reader.readName()
            reader.pos += 4
        }
        val anCount = ((buf[6].toInt() and 0xFF) shl 8) or (buf[7].toInt() and 0xFF)
        val answers = mutableListOf<Answer>()
        repeat(anCount) {
            val name = reader.readName()
            val type = reader.u16()
            reader.pos += 2 // class
            val ttl = reader.u32()
            val rdLen = reader.u16()
            val data = reader.readData(type, rdLen)
            answers.add(Answer(name, type, ttl, data))
        }
        return answers
    }

    /** 缓冲区游标读取器 —— 支持 0xC0 名称压缩指针 */
    private class Reader(private val buf: ByteArray, private val length: Int) {
        var pos = 0

        fun u16(): Int = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF).also { pos += 2 }

        fun u32(): Long =
            ((buf[pos].toLong() and 0xFF) shl 24) or ((buf[pos + 1].toLong() and 0xFF) shl 16) or
                ((buf[pos + 2].toLong() and 0xFF) shl 8) or (buf[pos + 3].toLong() and 0xFF).also { pos += 4 }

        fun readName(): String {
            val labels = mutableListOf<String>()
            var jumped = false
            var jumpBack = 0
            while (pos < length) {
                val len = buf[pos].toInt() and 0xFF
                if (len == 0) { pos++; break }
                if (len and 0xC0 == 0xC0) {
                    if (!jumped) jumpBack = pos + 2
                    pos = ((len and 0x3F) shl 8) or (buf[pos + 1].toInt() and 0xFF)
                    jumped = true
                    continue
                }
                labels.add(String(buf, pos + 1, len, Charsets.UTF_8))
                pos += len + 1
            }
            if (jumped && jumpBack > 0) pos = jumpBack
            return labels.joinToString(".")
        }

        fun readData(type: Int, rdLen: Int): String {
            val end = pos + rdLen
            val data = when (type) {
                DnsType.A -> if (rdLen == 4) InetAddress.getByAddress(buf.copyOfRange(pos, end)).hostAddress else "--"
                DnsType.AAAA -> if (rdLen == 16) InetAddress.getByAddress(buf.copyOfRange(pos, end)).hostAddress else "--"
                DnsType.CNAME, DnsType.NS, DnsType.PTR -> readName()
                DnsType.MX -> {
                    val pref = u16()
                    "$pref ${readName()}"
                }
                DnsType.TXT -> {
                    val sb = StringBuilder()
                    var p = pos
                    while (p < end) {
                        val l = buf[p].toInt() and 0xFF
                        sb.append(String(buf, p + 1, l, Charsets.UTF_8))
                        p += l + 1
                    }
                    sb.toString()
                }
                DnsType.SOA -> "${readName()} ${readName()} ${u32()} ${u32()} ${u32()} ${u32()} ${u32()}"
                else -> "--"
            }
            pos = end
            return data
        }
    }
}
