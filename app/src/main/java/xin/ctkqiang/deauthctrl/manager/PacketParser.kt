package xin.ctkqiang.deauthctrl.manager

/**
 * 单个 IPv4 数据包的解析结果 —— 纯 Kotlin 数据类,不依赖 Android,可直接 JVM 单测。
 *
 * 字段全部来自报文字段本身;解析不到的可选字段为 null / -1,绝不编造。
 *
 * @param version        IP 版本,本解析器恒为 4
 * @param ihl            IPv4 头长度(字节,含选项)
 * @param ttl            生存时间
 * @param ipTotalLen     IPv4 总长度(头 + 载荷,字节)
 * @param srcIp          源 IP(点分十进制)
 * @param dstIp          目的 IP(点分十进制)
 * @param protocol       "TCP" / "UDP" / "ICMP" / "IGMP" / "GRE" / "SCTP" / "P<协议号>"
 * @param protocolNumber IPv4 协议号(6/17/1 等,供过滤与详情树使用)
 * @param srcPort        传输层源端口;无端口协议为 0
 * @param dstPort        传输层目的端口;无端口协议为 0
 * @param tcpFlags       TCP 标志缩写(SYN,ACK,FIN,RST,PSH 顺序,逗号分隔);非 TCP 为空串
 * @param tcpSeq         TCP 序列号;非 TCP 为 -1
 * @param tcpAck         TCP 确认号;非 TCP 为 -1
 * @param tcpWindow      TCP 窗口字段;非 TCP 为 -1
 * @param udpLength      UDP 长度字段(含 8 字节头);非 UDP 为 -1
 * @param dnsName        解析到的 DNS 查询名(仅请求且 QDCOUNT > 0 时)
 * @param dnsType        DNS 查询类型名(A/AAAA/CNAME/...)
 * @param dnsClass       DNS 查询类(IN 等)
 * @param payloadOffset  传输层载荷在包内的起始偏移;无法确定时为 -1
 * @param info           可读摘要:DNS 查询、TLS SNI、HTTP 首行等;解析不到时给协议名
 * @param domain         从 DNS / TLS SNI / HTTP Host 提取的域名;提取不到为 null
 * @param hexBytes       原始报文字节的前 64 字节(用于 pcap 风格 HEX 转储)
 */
data class ParsedPacket(
    val version: Int,
    val ihl: Int,
    val ttl: Int,
    val ipTotalLen: Int,
    val srcIp: String,
    val dstIp: String,
    val protocol: String,
    val protocolNumber: Int,
    val srcPort: Int,
    val dstPort: Int,
    val tcpFlags: String,
    val tcpSeq: Long,
    val tcpAck: Long,
    val tcpWindow: Int,
    val udpLength: Int,
    val dnsName: String?,
    val dnsType: String?,
    val dnsClass: String?,
    val payloadOffset: Int,
    val info: String,
    val domain: String?,
    val hexBytes: ByteArray,
)

/** HEX 转储保留的最大字节数 —— 只要前 64 字节,页面明确标注「共 N 字节」 */
const val HEX_PREVIEW_BYTES = 64

/**
 * 解析一个 IPv4 数据包
 *
 * 只做只读解析,不做任何转发决策。调用方保证 [buf] 中从 0 开始至少有 [len]
 * 字节可读(通常来自 TUN 的一次 read);包不完整、版本不对或头长度非法时返回 null。
 *
 * ## 支持的可读摘要
 * - UDP/TCP 目的端口 53:DNS 查询名 / 类型 / 类(QNAME + QTYPE + QCLASS);
 * - TCP 80 端口:HTTP 请求方法 + Host + 路径;
 * - TCP 非 53 端口且首字节 0x16:TLS ClientHello 的 SNI 域名;
 * - ICMP:type / code。
 *
 * 所有字段都来自报文本身,解析失败时退回协议名,绝不编造域名。
 */
fun parseIpv4Packet(buf: ByteArray, len: Int): ParsedPacket? {
    if (len < 20 || buf.size < len) return null

    val version = (buf[0].toInt() ushr 4) and 0x0F
    if (version != 4) return null

    val ihl = (buf[0].toInt() and 0x0F) * 4
    if (ihl < 20 || ihl > len) return null

    val totalLen = u16b(buf, 2)
    if (totalLen < ihl || totalLen > len) return null

    val ttl = u8b(buf, 8)
    val protoNum = u8b(buf, 9)
    val protocol = protocolName(protoNum)
    val srcIp = ipText(buf, 12)
    val dstIp = ipText(buf, 16)

    var srcPort = 0
    var dstPort = 0
    var flags = ""
    var seq = -1L
    var ack = -1L
    var window = -1
    var udpLen = -1
    var payloadOffset = ihl
    var info = protocol
    var dnsName: String? = null
    var dnsType: String? = null
    var dnsClass: String? = null
    var domain: String? = null

    when (protoNum) {
        6 -> {
            if (totalLen >= ihl + 20) {
                srcPort = u16b(buf, ihl)
                dstPort = u16b(buf, ihl + 2)
                seq = u32b(buf, ihl + 4)
                ack = u32b(buf, ihl + 8)
                window = u16b(buf, ihl + 14)
                flags = tcpFlagsText(u8b(buf, ihl + 13))
                val dataOffset = (u8b(buf, ihl + 12) ushr 4) * 4
                val payloadStart = ihl + dataOffset
                if (dataOffset >= 20 && payloadStart <= totalLen) {
                    payloadOffset = payloadStart
                    val payload = buf.copyOfRange(payloadStart, totalLen)
                    val summary = describeTcp(srcPort, dstPort, payload)
                    info = summary.info
                    domain = summary.domain
                    dnsName = summary.dnsName
                    dnsType = summary.dnsType
                    dnsClass = summary.dnsClass
                } else {
                    info = "TCP"
                }
            }
        }
        17 -> {
            if (totalLen >= ihl + 8) {
                srcPort = u16b(buf, ihl)
                dstPort = u16b(buf, ihl + 2)
                udpLen = u16b(buf, ihl + 4)
                payloadOffset = ihl + 8
                val payloadEnd = if (udpLen >= 8) minOf(ihl + udpLen, totalLen) else totalLen
                val payload = if (payloadOffset <= payloadEnd) {
                    buf.copyOfRange(payloadOffset, payloadEnd)
                } else {
                    ByteArray(0)
                }
                val summary = describeUdp(srcPort, dstPort, payload)
                info = summary.info
                domain = summary.domain
                dnsName = summary.dnsName
                dnsType = summary.dnsType
                dnsClass = summary.dnsClass
            }
        }
        1 -> {
            if (totalLen >= ihl + 8) {
                info = "ICMP type ${u8b(buf, ihl)} code ${u8b(buf, ihl + 1)}"
                payloadOffset = ihl + 8
            } else if (totalLen >= ihl + 2) {
                info = "ICMP type ${u8b(buf, ihl)} code ${u8b(buf, ihl + 1)}"
                payloadOffset = ihl + 2
            }
        }
        else -> info = "IPv4 协议号 $protoNum"
    }

    return ParsedPacket(
        version = version,
        ihl = ihl,
        ttl = ttl,
        ipTotalLen = totalLen,
        srcIp = srcIp,
        dstIp = dstIp,
        protocol = protocol,
        protocolNumber = protoNum,
        srcPort = srcPort,
        dstPort = dstPort,
        tcpFlags = flags,
        tcpSeq = seq,
        tcpAck = ack,
        tcpWindow = window,
        udpLength = udpLen,
        dnsName = dnsName,
        dnsType = dnsType,
        dnsClass = dnsClass,
        payloadOffset = payloadOffset,
        info = info,
        domain = domain,
        hexBytes = buf.copyOfRange(0, minOf(totalLen, HEX_PREVIEW_BYTES)),
    )
}

private fun protocolName(protoNum: Int): String = when (protoNum) {
    1 -> "ICMP"
    2 -> "IGMP"
    6 -> "TCP"
    17 -> "UDP"
    47 -> "GRE"
    132 -> "SCTP"
    else -> "P$protoNum"
}

// ─── 传输层摘要 ──────────────────────────────────────────────────────────────

/** 传输层摘要结果 —— info 给人看,domain/dns* 给聚合与详情树用 */
private class TransportSummary(
    val info: String,
    val domain: String? = null,
    val dnsName: String? = null,
    val dnsType: String? = null,
    val dnsClass: String? = null,
)

private fun describeTcp(srcPort: Int, dstPort: Int, payload: ByteArray): TransportSummary {
    // TCP DNS:载荷前 2 字节是大端长度前缀,真正的 DNS 报文从 +2 开始
    if (dstPort == 53) {
        val q = if (payload.size > 2) parseDnsQuestion(payload, 2, payload.size) else null
        return if (q != null) {
            TransportSummary("DNS ${q.type} ${q.name}", q.name, q.name, q.type, q.dnsClass)
        } else {
            TransportSummary("DNS 查询")
        }
    }
    if (srcPort == 53) return TransportSummary("DNS 响应")
    if (dstPort == 80) {
        describeHttp(payload)?.let { return it }
    }
    describeTls(payload)?.let { return it }
    return TransportSummary("TCP")
}

private fun describeUdp(srcPort: Int, dstPort: Int, payload: ByteArray): TransportSummary {
    if (dstPort == 53) {
        val q = parseDnsQuestion(payload, 0, payload.size)
        return if (q != null) {
            TransportSummary("DNS ${q.type} ${q.name}", q.name, q.name, q.type, q.dnsClass)
        } else {
            TransportSummary("DNS 查询")
        }
    }
    if (srcPort == 53) return TransportSummary("DNS 响应")
    val label = when (dstPort) {
        67, 68 -> "DHCP"
        123 -> "NTP"
        137, 138 -> "NetBIOS"
        1900 -> "SSDP"
        5353 -> "mDNS"
        else -> "UDP"
    }
    return TransportSummary(label)
}

// ─── DNS ────────────────────────────────────────────────────────────────────

private class DnsQuestion(
    val name: String,
    val type: String,
    val dnsClass: String,
)

/**
 * 解析 DNS 问题的 QNAME / QTYPE / QCLASS
 *
 * 跳过 12 字节 DNS 头,按标签序列读域名;遇到压缩指针(0xC0)直接停止,
 * 不再跟随。报文截断时返回 null。只解析第一条问题。
 */
private fun parseDnsQuestion(data: ByteArray, start: Int, end: Int): DnsQuestion? {
    if (start < 0 || end > data.size || start + 12 > end) return null
    val qdCount = u16b(data, start + 4)
    if (qdCount <= 0) return null

    var pos = start + 12
    val name = StringBuilder()
    var guard = 0
    while (pos < end && guard < 128) {
        guard++
        val b = data[pos].toInt() and 0xFF
        if (b == 0) {
            pos++
            break
        }
        if ((b and 0xC0) == 0xC0) {
            pos += 2
            break
        }
        val labelLen = b and 0x3F
        if (labelLen == 0 || pos + 1 + labelLen > end) break
        val label = String(data, pos + 1, labelLen, Charsets.US_ASCII)
            .filter { it.code in 32..126 }
        if (name.isNotEmpty()) name.append('.')
        name.append(label)
        pos += 1 + labelLen
    }
    if (name.isEmpty()) return null
    val qtype = if (pos + 2 <= end) u16b(data, pos) else 0
    val qclass = if (pos + 4 <= end) u16b(data, pos + 2) else 1
    return DnsQuestion(name.toString(), dnsTypeName(qtype), dnsClassName(qclass))
}

private fun dnsTypeName(type: Int): String = when (type) {
    1 -> "A"
    2 -> "NS"
    5 -> "CNAME"
    6 -> "SOA"
    12 -> "PTR"
    15 -> "MX"
    16 -> "TXT"
    28 -> "AAAA"
    33 -> "SRV"
    35 -> "NAPTR"
    64 -> "SVCB"
    65 -> "HTTPS"
    255 -> "ANY"
    else -> "TYPE$type"
}

private fun dnsClassName(cls: Int): String = when (cls) {
    1 -> "IN"
    3 -> "CH"
    4 -> "HS"
    255 -> "ANY"
    else -> "CLASS$cls"
}

// ─── TLS SNI ────────────────────────────────────────────────────────────────

/**
 * 从 TCP 载荷里提取 TLS ClientHello 的 SNI
 *
 * 结构:5 字节记录头 → 4 字节握手头 → 2 字节 client_version → 32 字节 random
 * → session_id → cipher_suites → compression → extensions → server_name(0x0000)。
 * 每一步都做边界检查;识别为 TLS 但取不到 SNI 时返回「TLS ClientHello」。
 */
private fun describeTls(payload: ByteArray): TransportSummary? {
    if (payload.size < 9) return null
    if (u8b(payload, 0) != 0x16) return null
    val major = u8b(payload, 1)
    val minor = u8b(payload, 2)
    if (major != 0x03 || minor !in 0x01..0x04) return null
    if (u8b(payload, 5) != 0x01) return TransportSummary("TLS 握手")

    var pos = 9 // 记录头 5 + 握手头 4
    if (pos + 2 + 32 > payload.size) return TransportSummary("TLS ClientHello")
    pos += 2 + 32

    if (pos + 1 > payload.size) return TransportSummary("TLS ClientHello")
    pos += 1 + u8b(payload, pos)

    if (pos + 2 > payload.size) return TransportSummary("TLS ClientHello")
    pos += 2 + u16b(payload, pos)

    if (pos + 1 > payload.size) return TransportSummary("TLS ClientHello")
    pos += 1 + u8b(payload, pos)

    if (pos + 2 > payload.size) return TransportSummary("TLS ClientHello")
    val extTotal = u16b(payload, pos)
    pos += 2
    val extEnd = minOf(pos + extTotal, payload.size)

    while (pos + 4 <= extEnd) {
        val extType = u16b(payload, pos)
        val extLen = u16b(payload, pos + 2)
        pos += 4
        if (pos + extLen > extEnd) break
        if (extType == 0x0000) {
            var p = pos
            if (p + 2 > pos + extLen) return TransportSummary("TLS ClientHello")
            val listLen = u16b(payload, p)
            p += 2
            val listEnd = minOf(p + listLen, pos + extLen)
            while (p + 3 <= listEnd) {
                val nameType = u8b(payload, p)
                val nameLen = u16b(payload, p + 1)
                p += 3
                if (p + nameLen > listEnd) break
                if (nameType == 0) {
                    val host = String(payload, p, nameLen, Charsets.US_ASCII).trim()
                    if (host.isNotEmpty()) {
                        return TransportSummary("TLS SNI: $host", domain = host)
                    }
                }
                p += nameLen
            }
            return TransportSummary("TLS ClientHello")
        }
        pos += extLen
    }
    return TransportSummary("TLS ClientHello")
}

// ─── HTTP ───────────────────────────────────────────────────────────────────

private val HTTP_METHODS = setOf("GET", "POST", "PUT", "HEAD", "DELETE", "OPTIONS", "PATCH", "CONNECT", "TRACE")

/** 解析 HTTP 请求首行与 Host 头;不是 HTTP 请求时返回 null */
private fun describeHttp(payload: ByteArray): TransportSummary? {
    if (payload.size < 8) return null
    val text = String(payload, 0, minOf(payload.size, 2048), Charsets.ISO_8859_1)
    val headEnd = text.indexOf("\r\n\r\n").let { if (it >= 0) it else text.indexOf("\n\n") }
    val head = if (headEnd >= 0) text.substring(0, headEnd) else text
    val lines = head.split("\r\n", "\n")
    if (lines.isEmpty()) return null
    val parts = lines[0].trim().split(' ')
    if (parts.size < 2 || parts[0] !in HTTP_METHODS) return null
    val method = parts[0]
    val path = parts[1]
    var host = ""
    for (line in lines) {
        if (line.startsWith("Host:", ignoreCase = true)) {
            host = line.substring(5).trim()
            break
        }
    }
    val target = if (host.isNotEmpty()) host + path else path
    val domain = host.substringBefore(':').ifBlank { null }
    return TransportSummary("HTTP $method ${target.take(64)}", domain = domain)
}

// ─── 字节工具 ────────────────────────────────────────────────────────────────

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun u8b(b: ByteArray, i: Int): Int = b[i].toInt() and 0xFF

private fun u16b(b: ByteArray, i: Int): Int =
    ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

private fun u32b(b: ByteArray, i: Int): Long =
    ((b[i].toLong() and 0xFF) shl 24) or
        ((b[i + 1].toLong() and 0xFF) shl 16) or
        ((b[i + 2].toLong() and 0xFF) shl 8) or
        (b[i + 3].toLong() and 0xFF)

private fun ipText(b: ByteArray, offset: Int): String =
    "${u8b(b, offset)}.${u8b(b, offset + 1)}.${u8b(b, offset + 2)}.${u8b(b, offset + 3)}"

/** TCP 标志统一按 SYN,ACK,FIN,RST,PSH 顺序输出,与 Wireshark 习惯一致 */
private fun tcpFlagsText(flagByte: Int): String {
    val parts = ArrayList<String>(5)
    if (flagByte and 0x02 != 0) parts.add("SYN")
    if (flagByte and 0x10 != 0) parts.add("ACK")
    if (flagByte and 0x01 != 0) parts.add("FIN")
    if (flagByte and 0x04 != 0) parts.add("RST")
    if (flagByte and 0x08 != 0) parts.add("PSH")
    return parts.joinToString(",")
}
