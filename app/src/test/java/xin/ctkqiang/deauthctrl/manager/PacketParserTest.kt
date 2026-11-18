package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [parseIpv4Packet] 的 JVM 单测 —— 全部使用手工构造的 IPv4 报文
 *
 * 覆盖:IPv4 头字段、TCP flags/Seq/Ack/Window、UDP、DNS A/AAAA 查询、
 * TLS SNI、HTTP Host、ICMP、截断包与非法版本、64 字节 HEX 截断。
 * 解析器是纯 Kotlin,不依赖 Android,因此可直接在 test/ 下运行。
 */
class PacketParserTest {

    // ─── 报文构造工具 ────────────────────────────────────────────────────────

    private val localIp = byteArrayOf(10, 8, 0, 1)
    private val remoteIp = byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)

    private fun ipv4(src: ByteArray, dst: ByteArray, protocol: Int, payload: ByteArray): ByteArray {
        val total = 20 + payload.size
        val packet = ByteArray(total)
        packet[0] = 0x45 // IPv4, IHL = 5
        packet[1] = 0x00
        putU16(packet, 2, total)
        putU16(packet, 4, 0)
        putU16(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = protocol.toByte()
        putU16(packet, 10, 0)
        System.arraycopy(src, 0, packet, 12, 4)
        System.arraycopy(dst, 0, packet, 16, 4)
        // 校验和不参与解析,保持 0
        System.arraycopy(payload, 0, packet, 20, payload.size)
        return packet
    }

    private fun tcpSegment(
        srcPort: Int,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        window: Int = 65535,
        payload: ByteArray = ByteArray(0),
    ): ByteArray {
        val segment = ByteArray(20 + payload.size)
        putU16(segment, 0, srcPort)
        putU16(segment, 2, dstPort)
        putU32(segment, 4, seq)
        putU32(segment, 8, ack)
        segment[12] = (5 shl 4).toByte()
        segment[13] = flags.toByte()
        putU16(segment, 14, window)
        putU16(segment, 16, 0)
        putU16(segment, 18, 0)
        System.arraycopy(payload, 0, segment, 20, payload.size)
        return segment
    }

    private fun udpSegment(srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val segment = ByteArray(8 + payload.size)
        putU16(segment, 0, srcPort)
        putU16(segment, 2, dstPort)
        putU16(segment, 4, segment.size)
        putU16(segment, 6, 0)
        System.arraycopy(payload, 0, segment, 8, payload.size)
        return segment
    }

    private fun dnsQuery(name: String, type: Int, cls: Int = 1): ByteArray {
        val out = ArrayList<Byte>()
        fun push(value: Int) = out.add((value and 0xFF).toByte())
        push(0x12); push(0x34) // ID
        push(0x01); push(0x00) // flags: 标准查询 + RD
        push(0x00); push(0x01) // QDCOUNT = 1
        push(0x00); push(0x00) // ANCOUNT
        push(0x00); push(0x00) // NSCOUNT
        push(0x00); push(0x00) // ARCOUNT
        name.split('.').forEach { label ->
            push(label.length)
            label.toByteArray(Charsets.US_ASCII).forEach { push(it.toInt()) }
        }
        push(0)
        push((type ushr 8) and 0xFF); push(type and 0xFF)
        push((cls ushr 8) and 0xFF); push(cls and 0xFF)
        return out.toByteArray()
    }

    /** 构造最小 TLS ClientHello(带 server_name 扩展) */
    private fun tlsClientHello(host: String): ByteArray {
        val hostBytes = host.toByteArray(Charsets.US_ASCII)

        // server_name 扩展内容:listLen(2) + nameType(1) + nameLen(2) + name
        val sniBody = ArrayList<Byte>()
        fun s(value: Int) = sniBody.add((value and 0xFF).toByte())
        s(((hostBytes.size + 3) ushr 8) and 0xFF)
        s((hostBytes.size + 3) and 0xFF)
        s(0)
        s((hostBytes.size ushr 8) and 0xFF)
        s(hostBytes.size and 0xFF)
        hostBytes.forEach { s(it.toInt()) }

        val extensions = ArrayList<Byte>()
        fun e(value: Int) = extensions.add((value and 0xFF).toByte())
        e(0x00); e(0x00) // ext type: server_name
        e((sniBody.size ushr 8) and 0xFF); e(sniBody.size and 0xFF)
        sniBody.forEach { extensions.add(it) }

        val handshake = ArrayList<Byte>()
        fun h(value: Int) = handshake.add((value and 0xFF).toByte())
        h(0x03); h(0x03) // client_version TLS 1.2
        repeat(32) { h(it) } // random
        h(0x00) // session_id_len
        h(0x00); h(0x02); h(0x13); h(0x01) // cipher_suites: TLS_AES_128_GCM_SHA256
        h(0x01); h(0x00) // compression: null
        h((extensions.size ushr 8) and 0xFF); h(extensions.size and 0xFF)
        extensions.forEach { h(it.toInt()) }

        val record = ArrayList<Byte>()
        fun r(value: Int) = record.add((value and 0xFF).toByte())
        r(0x16); r(0x03); r(0x01) // handshake record, TLS 1.0
        val recordLen = handshake.size + 4
        r((recordLen ushr 8) and 0xFF); r(recordLen and 0xFF)
        r(0x01) // ClientHello
        r((handshake.size ushr 16) and 0xFF)
        r((handshake.size ushr 8) and 0xFF)
        r(handshake.size and 0xFF)
        handshake.forEach { r(it.toInt()) }
        return record.toByteArray()
    }

    private fun putU16(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = ((value ushr 8) and 0xFF).toByte()
        dst[offset + 1] = (value and 0xFF).toByte()
    }

    private fun putU32(dst: ByteArray, offset: Int, value: Long) {
        dst[offset] = ((value ushr 24) and 0xFF).toByte()
        dst[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        dst[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        dst[offset + 3] = (value and 0xFF).toByte()
    }

    // ─── 用例 ────────────────────────────────────────────────────────────────

    @Test
    fun `IPv4 头字段解析`() {
        val segment = tcpSegment(50000, 443, 0, 0, 0x02)
        val packet = ipv4(localIp, remoteIp, 6, segment)
        val parsed = parseIpv4Packet(packet, packet.size)!!

        assertEquals(4, parsed.version)
        assertEquals(20, parsed.ihl)
        assertEquals(64, parsed.ttl)
        assertEquals(packet.size, parsed.ipTotalLen)
        assertEquals("10.8.0.1", parsed.srcIp)
        assertEquals("93.184.216.34", parsed.dstIp)
        assertEquals("TCP", parsed.protocol)
        assertEquals(6, parsed.protocolNumber)
        assertEquals(50000, parsed.srcPort)
        assertEquals(443, parsed.dstPort)
        assertEquals(40, parsed.payloadOffset)
        assertEquals(40, parsed.hexBytes.size)
    }

    @Test
    fun `TCP flags 与 Seq Ack Window 解析`() {
        val segment = tcpSegment(
            srcPort = 49152,
            dstPort = 443,
            seq = 0x11223344L,
            ack = 0x55667788L,
            flags = 0x02 or 0x10 or 0x08, // SYN + ACK + PSH
            window = 29200,
        )
        val packet = ipv4(localIp, remoteIp, 6, segment)
        val parsed = parseIpv4Packet(packet, packet.size)!!

        assertEquals("SYN,ACK,PSH", parsed.tcpFlags)
        assertEquals(0x11223344L, parsed.tcpSeq)
        assertEquals(0x55667788L, parsed.tcpAck)
        assertEquals(29200, parsed.tcpWindow)
    }

    @Test
    fun `UDP 字段与 DNS A 查询解析`() {
        val dns = dnsQuery("example.com", 1)
        val segment = udpSegment(40000, 53, dns)
        val packet = ipv4(localIp, remoteIp, 17, segment)
        val parsed = parseIpv4Packet(packet, packet.size)!!

        assertEquals("UDP", parsed.protocol)
        assertEquals(17, parsed.protocolNumber)
        assertEquals(40000, parsed.srcPort)
        assertEquals(53, parsed.dstPort)
        assertEquals(segment.size, parsed.udpLength)
        assertEquals(28, parsed.payloadOffset)
        assertEquals("example.com", parsed.dnsName)
        assertEquals("A", parsed.dnsType)
        assertEquals("IN", parsed.dnsClass)
        assertEquals("DNS A example.com", parsed.info)
        assertEquals("example.com", parsed.domain)
    }

    @Test
    fun `DNS AAAA 查询类型解析`() {
        val dns = dnsQuery("ipv6.example.com", 28)
        val segment = udpSegment(40001, 53, dns)
        val packet = ipv4(localIp, remoteIp, 17, segment)
        val parsed = parseIpv4Packet(packet, packet.size)!!

        assertEquals("AAAA", parsed.dnsType)
        assertEquals("DNS AAAA ipv6.example.com", parsed.info)
        assertEquals("ipv6.example.com", parsed.domain)
    }

    @Test
    fun `TLS SNI 提取`() {
        val hello = tlsClientHello("secure.example.com")
        val segment = tcpSegment(50000, 443, 1, 1, 0x18, payload = hello)
        val packet = ipv4(localIp, remoteIp, 6, segment)
        val parsed = parseIpv4Packet(packet, packet.size)!!

        assertEquals("TCP", parsed.protocol)
        assertEquals("TLS SNI: secure.example.com", parsed.info)
        assertEquals("secure.example.com", parsed.domain)
    }

    @Test
    fun `HTTP Host 与路径提取`() {
        val body = "GET /path?q=1 HTTP/1.1\r\nHost: example.com\r\nUser-Agent: test\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)
        val segment = tcpSegment(50001, 80, 2, 2, 0x18, payload = body)
        val packet = ipv4(localIp, remoteIp, 6, segment)
        val parsed = parseIpv4Packet(packet, packet.size)!!

        assertEquals("HTTP GET example.com/path?q=1", parsed.info)
        assertEquals("example.com", parsed.domain)
    }

    @Test
    fun `ICMP 解析`() {
        val icmp = byteArrayOf(8, 0, 0, 0, 0, 1, 0, 1)
        val packet = ipv4(localIp, remoteIp, 1, icmp)
        val parsed = parseIpv4Packet(packet, packet.size)!!

        assertEquals("ICMP", parsed.protocol)
        assertEquals(1, parsed.protocolNumber)
        assertEquals("ICMP type 8 code 0", parsed.info)
        assertEquals(0, parsed.srcPort)
        assertEquals(0, parsed.dstPort)
        assertEquals(28, parsed.payloadOffset)
    }

    @Test
    fun `截断包返回 null`() {
        val segment = tcpSegment(50000, 443, 0, 0, 0x02)
        val packet = ipv4(localIp, remoteIp, 6, segment)

        // 少于 20 字节
        assertNull(parseIpv4Packet(packet, 19))
        // 缓冲区比声明长度短
        assertNull(parseIpv4Packet(packet.copyOf(30), 30))
        // 头部声明总长 1000,实际只给了 40
        val lying = packet.copyOf()
        putU16(lying, 2, 1000)
        assertNull(parseIpv4Packet(lying, lying.size))
    }

    @Test
    fun `非 IPv4 版本返回 null`() {
        val packet = ByteArray(40)
        packet[0] = 0x60 // IPv6 版本号
        assertNull(parseIpv4Packet(packet, packet.size))
    }

    @Test
    fun `HEX 预览最多 64 字节`() {
        val payload = ByteArray(100) { (it and 0xFF).toByte() }
        val segment = udpSegment(40002, 9999, payload)
        val packet = ipv4(localIp, remoteIp, 17, segment)
        val parsed = parseIpv4Packet(packet, packet.size)!!

        assertEquals(packet.size, parsed.ipTotalLen)
        assertEquals(64, parsed.hexBytes.size)
        assertTrue(parsed.hexBytes.contentEquals(packet.copyOfRange(0, 64)))
    }

    @Test
    fun `TCP DNS 长度前缀解析`() {
        val dns = dnsQuery("tcp.example.com", 1)
        val framed = ByteArray(2 + dns.size)
        putU16(framed, 0, dns.size)
        System.arraycopy(dns, 0, framed, 2, dns.size)
        val segment = tcpSegment(50002, 53, 3, 3, 0x18, payload = framed)
        val packet = ipv4(localIp, remoteIp, 6, segment)
        val parsed = parseIpv4Packet(packet, packet.size)!!

        assertEquals("DNS A tcp.example.com", parsed.info)
        assertEquals("tcp.example.com", parsed.domain)
    }

    @Test
    fun `空载荷 TCP 不产生域名`() {
        val segment = tcpSegment(50003, 443, 4, 4, 0x10)
        val packet = ipv4(localIp, remoteIp, 6, segment)
        val parsed = parseIpv4Packet(packet, packet.size)!!

        assertEquals("TCP", parsed.info)
        assertNull(parsed.domain)
        assertNotEquals(0, parsed.hexBytes.size)
    }
}
