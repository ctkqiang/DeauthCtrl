package xin.ctkqiang.deauthctrl.manager

import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import java.io.ByteArrayOutputStream
import java.math.BigInteger

/**
 * 一个已解析的 BER TLV 节点
 *
 * @property tag 单字节标签（SNMP 使用的类型均为单字节标签）
 * @property value 内容字节，长度头已剥离
 */
class BerTlv(val tag: Int, val value: ByteArray) {

    /** 把 [value] 当作连续的 TLV 流解析（仅对 SEQUENCE / PDU 等构造类型有意义） */
    fun children(): List<BerTlv> = BerCodec.parseAll(value)

    /** 按有符号二进制补码解释为 Long */
    fun asLong(): Long = BerCodec.decodeInteger(value)

    /** 按 UTF-8 解释为文本（不保证可打印） */
    fun asString(): String = value.toString(Charsets.UTF_8)

    /** 按 OBJECT IDENTIFIER 解释为点分 OID */
    fun asOid(): String = BerCodec.decodeOid(value)

    /** 十六进制展示 */
    fun asHex(): String = BerCodec.toHex(value)

    override fun toString(): String =
        "BerTlv(tag=0x${tag.toString(16)}, len=${value.size})"
}

/**
 * SNMP 变量绑定
 *
 * @property oid 变量 OID
 * @property tag 值类型标签（0x02 INTEGER / 0x04 OCTET STRING / 0x06 OID / 0x40 IpAddress 等）
 * @property display 按类型解码后的可读值；不可打印的 OCTET STRING 退回十六进制
 * @property raw 原始值字节，保留真实返回作为证据
 */
class SnmpVarbind(
    val oid: String,
    val tag: Int,
    val display: String,
    val raw: ByteArray,
)

/**
 * SNMP v1/v2c 响应
 *
 * 对应报文结构：
 * SEQUENCE { INTEGER version, OCTET STRING community,
 *            A2 PDU { request-id, error-status, error-index,
 *                     varbind list { SEQUENCE { OID, value } ... } } }
 */
data class SnmpResponse(
    val version: Int,
    val community: String,
    val pduTag: Int,
    val requestId: Long,
    val errorStatus: Int,
    val errorIndex: Int,
    val varbinds: List<SnmpVarbind>,
) {
    /** 是否为 GetResponse（A2） */
    val isResponse: Boolean get() = pduTag == BerCodec.TAG_GET_RESPONSE

    /** error-status 的可读解释；0 表示成功 */
    val errorText: String?
        get() = when (errorStatus) {
            0 -> null
            1 -> "tooBig"
            2 -> "noSuchName"
            3 -> "badValue"
            4 -> "readOnly"
            5 -> "genErr"
            6 -> "noAccess"
            7 -> "wrongType"
            8 -> "wrongLength"
            9 -> "wrongEncoding"
            10 -> "wrongValue"
            11 -> "noCreation"
            12 -> "inconsistentValue"
            13 -> "resourceUnavailable"
            14 -> "commitFailed"
            15 -> "undoFailed"
            16 -> "authorizationError"
            17 -> "notWritable"
            18 -> "inconsistentName"
            else -> L10n.get(R.string.mgr_ber_unknown_error_code, errorStatus)
        }
}

/**
 * BER 编解码器（DER 兼容子集）—— 纯 Kotlin，无 Android 依赖
 *
 * 仅覆盖 SNMP v1/v2c GetRequest / GetResponse 报文所需的类型：
 * INTEGER / OCTET STRING / NULL / OID / SEQUENCE 以及上下文构造类型 A0、A2。
 *
 * ## 编码规则
 * - TLV：单字节 tag + 长度 + 内容；
 * - 长度：< 128 用单字节，否则 0x81 / 0x82 前缀 + 大端长度（最多 4 字节）；
 * - INTEGER：有符号二进制补码，按最短表示输出；
 * - OID：前两节编码为 40 * first + second（作为首个 base-128 子标识符），
 *   其后每个节点按 base-128 编码（每字节 7 位，最高位为续位标记）。
 *
 * ## 诚实边界
 * 这不是完整的 BER 实现：不支持不定长、构造类型、长标签号、BER 的
 * constructed OCTET STRING 等。SNMP 报文用不到这些结构；遇到不支持或
 * 被截断的数据直接返回 null / 空列表，不做任何猜测。
 */
object BerCodec {

    /** INTEGER（有符号） */
    const val TAG_INTEGER = 0x02

    /** OCTET STRING */
    const val TAG_OCTET_STRING = 0x04

    /** NULL */
    const val TAG_NULL = 0x05

    /** OBJECT IDENTIFIER */
    const val TAG_OID = 0x06

    /** SEQUENCE（构造类型） */
    const val TAG_SEQUENCE = 0x30

    /** IpAddress（应用类型 0） */
    const val TAG_IP_ADDRESS = 0x40

    /** Counter32（应用类型 1） */
    const val TAG_COUNTER32 = 0x41

    /** Gauge32 / Unsigned32（应用类型 2） */
    const val TAG_GAUGE32 = 0x42

    /** TimeTicks（应用类型 3） */
    const val TAG_TIME_TICKS = 0x43

    /** Counter64（应用类型 6） */
    const val TAG_COUNTER64 = 0x46

    /** GetRequest PDU（上下文构造类型 0） */
    const val TAG_GET_REQUEST = 0xA0

    /** GetNextRequest PDU（上下文构造类型 1） */
    const val TAG_GET_NEXT_REQUEST = 0xA1

    /** GetResponse PDU（上下文构造类型 2） */
    const val TAG_GET_RESPONSE = 0xA2

    /** SetRequest PDU（上下文构造类型 3） */
    const val TAG_SET_REQUEST = 0xA3

    /** SNMP v1 报文版本号 */
    const val SNMP_VERSION_V1 = 0

    /** SNMP v2c 报文版本号 */
    const val SNMP_VERSION_V2C = 1

    // ── 编码 ────────────────────────────────────────────────────────────────

    /** 编码长度头：< 128 单字节；否则高位置 1 的字节数前缀 + 大端长度 */
    fun encodeLength(length: Int): ByteArray {
        require(length >= 0) { "BER 长度不能为负" }
        return when {
            length < 0x80 -> byteArrayOf(length.toByte())
            length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
            length <= 0xFFFF -> byteArrayOf(
                0x82.toByte(),
                (length ushr 8).toByte(),
                length.toByte(),
            )
            length <= 0xFFFFFF -> byteArrayOf(
                0x83.toByte(),
                (length ushr 16).toByte(),
                (length ushr 8).toByte(),
                length.toByte(),
            )
            else -> byteArrayOf(
                0x84.toByte(),
                (length ushr 24).toByte(),
                (length ushr 16).toByte(),
                (length ushr 8).toByte(),
                length.toByte(),
            )
        }
    }

    /** 编码一个 TLV：单字节 tag + 长度 + 内容 */
    fun encodeTlv(tag: Int, value: ByteArray): ByteArray {
        require(tag in 0..0xFF) { "BER 标签超出单字节范围: $tag" }
        val length = encodeLength(value.size)
        val out = ByteArray(1 + length.size + value.size)
        out[0] = tag.toByte()
        length.copyInto(out, 1)
        value.copyInto(out, 1 + length.size)
        return out
    }

    /** INTEGER 编解码都按有符号补码处理；这里输出最短字节表示 */
    fun encodeInteger(value: Long): ByteArray {
        // 先固定 8 字节大端补码，再裁掉左侧冗余的符号字节
        val full = ByteArray(8) { index -> (value shr ((7 - index) * 8)).toByte() }
        var start = 0
        while (start < 7) {
            val current = full[start]
            val next = full[start + 1]
            val redundantPositive = current == 0.toByte() && (next.toInt() and 0x80) == 0
            val redundantNegative = current == (-1).toByte() && (next.toInt() and 0x80) != 0
            if (redundantPositive || redundantNegative) start++ else break
        }
        return encodeTlv(TAG_INTEGER, full.copyOfRange(start, full.size))
    }

    /** INTEGER 便捷重载 */
    fun encodeInteger(value: Int): ByteArray = encodeInteger(value.toLong())

    /** OCTET STRING（UTF-8 文本） */
    fun encodeOctetString(text: String): ByteArray =
        encodeTlv(TAG_OCTET_STRING, text.toByteArray(Charsets.UTF_8))

    /** OCTET STRING（原始字节，例如社区串按字节传递） */
    fun encodeOctetString(bytes: ByteArray): ByteArray =
        encodeTlv(TAG_OCTET_STRING, bytes)

    /** NULL —— 值为空 */
    fun encodeNull(): ByteArray = byteArrayOf(TAG_NULL.toByte(), 0x00)

    /** OBJECT IDENTIFIER：前两节合并，其余 base-128 */
    fun encodeOid(oid: String): ByteArray {
        val arcs = oid.trim().removePrefix(".").split('.').map { arc ->
            arc.toLongOrNull() ?: throw IllegalArgumentException("OID 含非数字节点: $oid")
        }
        require(arcs.size >= 2) { "OID 至少需要两节: $oid" }
        require(arcs.all { it >= 0L }) { "OID 节点不能为负: $oid" }
        require(arcs[0] in 0L..2L) { "OID 首节必须在 0..2: $oid" }
        if (arcs[0] < 2L) require(arcs[1] < 40L) { "OID 第二节必须在 0..39: $oid" }

        val body = ByteArrayOutputStream()
        body.write(encodeBase128(arcs[0] * 40 + arcs[1]))
        for (index in 2 until arcs.size) {
            body.write(encodeBase128(arcs[index]))
        }
        return encodeTlv(TAG_OID, body.toByteArray())
    }

    /** 把多个已编码片段拼成一个 SEQUENCE */
    fun encodeSequence(vararg parts: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        parts.forEach { body.write(it) }
        return encodeTlv(TAG_SEQUENCE, body.toByteArray())
    }

    /**
     * 编码完整的 SNMP GetRequest 报文
     *
     * 结构：SEQUENCE { version, community, A0 { request-id, 0, 0, SEQUENCE { SEQUENCE { OID, NULL } ... } } }
     * 默认 v2c；传 [SNMP_VERSION_V1] 可生成 v1 报文。
     */
    fun encodeGetRequest(
        community: String,
        requestId: Int,
        oids: List<String>,
        version: Int = SNMP_VERSION_V2C,
    ): ByteArray {
        require(oids.isNotEmpty()) { "GetRequest 至少需要一个 OID" }
        require(community.isNotEmpty()) { "社区串不能为空" }
        require(community.length <= 255) { "社区串过长（上限 255 字节）" }

        val varbinds = oids.map { oid -> encodeSequence(encodeOid(oid), encodeNull()) }
        val varbindList = encodeSequence(*varbinds.toTypedArray())

        val pduBody = ByteArrayOutputStream()
        pduBody.write(encodeInteger(requestId))
        pduBody.write(encodeInteger(0)) // error-status
        pduBody.write(encodeInteger(0)) // error-index
        pduBody.write(varbindList)
        val pdu = encodeTlv(TAG_GET_REQUEST, pduBody.toByteArray())

        val message = ByteArrayOutputStream()
        message.write(encodeInteger(version))
        message.write(encodeOctetString(community))
        message.write(pdu)
        return encodeTlv(TAG_SEQUENCE, message.toByteArray())
    }

    /** base-128：低 7 位一组，除最后一字节外最高位置 1 */
    private fun encodeBase128(value: Long): ByteArray {
        require(value >= 0) { "base-128 值不能为负" }
        if (value == 0L) return byteArrayOf(0)
        val bytes = ArrayList<Byte>(10)
        var remaining = value
        bytes.add((remaining and 0x7F).toByte())
        remaining = remaining ushr 7
        while (remaining > 0L) {
            bytes.add(0, ((remaining and 0x7F) or 0x80).toByte())
            remaining = remaining ushr 7
        }
        return bytes.toByteArray()
    }

    // ── 解码 ────────────────────────────────────────────────────────────────

    /** 解析报文中的第一个 TLV；数据不完整或结构不支持时返回 null */
    fun parse(data: ByteArray): BerTlv? = parseTlv(data, 0, data.size)?.tlv

    /** 解析连续的 TLV 流；遇到非法数据即停止，返回已成功解析的前缀 */
    fun parseAll(data: ByteArray): List<BerTlv> {
        val nodes = ArrayList<BerTlv>()
        var position = 0
        while (position < data.size) {
            val parsed = parseTlv(data, position, data.size) ?: break
            nodes.add(parsed.tlv)
            position = parsed.next
        }
        return nodes
    }

    /** 解析一个 TLV；[offset] 为起始位置，[limit] 为可读上界 */
    private fun parseTlv(data: ByteArray, offset: Int, limit: Int): ParsedTlv? {
        if (offset < 0 || offset >= limit) return null
        val tag = data[offset].toInt() and 0xFF
        // 长标签号（低 5 位全 1）不在支持范围内
        if ((tag and 0x1F) == 0x1F) return null

        var position = offset + 1
        if (position >= limit) return null
        val first = data[position].toInt() and 0xFF
        position++

        val length: Int = if ((first and 0x80) == 0) {
            first
        } else {
            val count = first and 0x7F
            // count = 0 表示不定长（BER），SNMP 不使用；超过 4 字节视为异常
            if (count == 0 || count > 4 || position + count > limit) return null
            var assembled = 0
            repeat(count) {
                assembled = (assembled shl 8) or (data[position].toInt() and 0xFF)
                position++
            }
            if (assembled < 0) return null
            assembled
        }

        if (length < 0 || position + length > limit) return null
        return ParsedTlv(
            tlv = BerTlv(tag, data.copyOfRange(position, position + length)),
            next = position + length,
        )
    }

    private data class ParsedTlv(val tlv: BerTlv, val next: Int)

    /** 有符号补码 → Long；超过 8 字节时只保留低 8 字节（SNMP 字段不会这么长） */
    fun decodeInteger(bytes: ByteArray): Long {
        if (bytes.isEmpty()) return 0L
        val data = if (bytes.size > 8) bytes.copyOfRange(bytes.size - 8, bytes.size) else bytes
        var value = if ((data[0].toInt() and 0x80) != 0) -1L else 0L
        for (byte in data) {
            value = (value shl 8) or (byte.toLong() and 0xFF)
        }
        return value
    }

    /** 无符号大整数文本（Counter32 / Gauge32 / TimeTicks / Counter64） */
    fun decodeUnsignedText(bytes: ByteArray): String =
        if (bytes.isEmpty()) "0" else BigInteger(1, bytes).toString()

    /** 点分 OID；截断的 base-128 或溢出时返回空串 */
    fun decodeOid(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val subIdentifiers = ArrayList<Long>()
        var value = 0L
        var pending = false
        for (byte in bytes) {
            val raw = byte.toInt() and 0xFF
            if (value > (Long.MAX_VALUE shr 7)) return ""
            value = (value shl 7) or (raw and 0x7F).toLong()
            pending = true
            if ((raw and 0x80) == 0) {
                subIdentifiers.add(value)
                value = 0L
                pending = false
            }
        }
        if (pending || subIdentifiers.isEmpty()) return ""

        val first = subIdentifiers[0]
        val builder = StringBuilder()
        when {
            first < 40 -> builder.append(0).append('.').append(first)
            first < 80 -> builder.append(1).append('.').append(first - 40)
            else -> builder.append(2).append('.').append(first - 80)
        }
        for (index in 1 until subIdentifiers.size) {
            builder.append('.').append(subIdentifiers[index])
        }
        return builder.toString()
    }

    /** OCTET STRING → 可读文本；含控制符或非法 UTF-8 时退回十六进制 */
    fun printableText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        var end = bytes.size
        while (end > 0 && bytes[end - 1] == 0.toByte()) end--
        if (end == 0) return ""
        val text = bytes.copyOfRange(0, end).toString(Charsets.UTF_8)
        val hasReplacement = text.indexOf('�') >= 0
        val hasControl = text.any { ch ->
            ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r'
        }
        return if (hasReplacement || hasControl) toHex(bytes) else text
    }

    /** 按值类型解码为可读字符串 */
    fun decodeValue(tlv: BerTlv): String = when (tlv.tag) {
        TAG_INTEGER -> decodeInteger(tlv.value).toString()
        TAG_OCTET_STRING -> printableText(tlv.value)
        TAG_NULL -> "NULL"
        TAG_OID -> decodeOid(tlv.value)
        TAG_IP_ADDRESS -> if (tlv.value.size == 4) {
            tlv.value.joinToString(".") { byte -> (byte.toInt() and 0xFF).toString() }
        } else {
            toHex(tlv.value)
        }
        TAG_COUNTER32, TAG_GAUGE32, TAG_TIME_TICKS, TAG_COUNTER64 ->
            decodeUnsignedText(tlv.value)
        else -> toHex(tlv.value)
    }

    /**
     * 十六进制展示（小写、无分隔）
     *
     * @param maxBytes 最多展示的原始字节数，超出部分以 "..." 标记，避免超长报文撑爆界面
     */
    fun toHex(bytes: ByteArray, maxBytes: Int = 2048): String {
        if (bytes.isEmpty()) return ""
        val count = minOf(bytes.size, maxBytes)
        val builder = StringBuilder(count * 2 + 3)
        for (index in 0 until count) {
            val value = bytes[index].toInt() and 0xFF
            if (value < 0x10) builder.append('0')
            builder.append(value.toString(16))
        }
        if (count < bytes.size) builder.append("...")
        return builder.toString()
    }

    // ── SNMP 报文解析 ───────────────────────────────────────────────────────

    /**
     * 解析 SNMP v1/v2c 响应
     *
     * 只接受 GetResponse / GetNextResponse / SetResponse 这类带
     * request-id / error-status / error-index / varbind list 的 PDU；
     * v1 Trap 等结构不同，直接返回 null。
     */
    fun decodeResponse(data: ByteArray): SnmpResponse? {
        val root = parse(data) ?: return null
        if (root.tag != TAG_SEQUENCE) return null

        val message = root.children()
        if (message.size < 3) return null
        val versionNode = message[0]
        val communityNode = message[1]
        val pduNode = message[2]
        if (versionNode.tag != TAG_INTEGER) return null
        if (communityNode.tag != TAG_OCTET_STRING) return null
        if (pduNode.tag != TAG_GET_RESPONSE &&
            pduNode.tag != TAG_GET_NEXT_REQUEST &&
            pduNode.tag != TAG_SET_REQUEST
        ) {
            return null
        }

        val pdu = pduNode.children()
        if (pdu.size < 4) return null
        if (pdu[0].tag != TAG_INTEGER) return null
        if (pdu[1].tag != TAG_INTEGER) return null
        if (pdu[2].tag != TAG_INTEGER) return null
        if (pdu[3].tag != TAG_SEQUENCE) return null

        val varbinds = ArrayList<SnmpVarbind>()
        for (sequence in pdu[3].children()) {
            if (sequence.tag != TAG_SEQUENCE) return null
            val pair = sequence.children()
            if (pair.size < 2) return null
            if (pair[0].tag != TAG_OID) return null
            varbinds.add(
                SnmpVarbind(
                    oid = decodeOid(pair[0].value),
                    tag = pair[1].tag,
                    display = decodeValue(pair[1]),
                    raw = pair[1].value,
                )
            )
        }

        return SnmpResponse(
            version = decodeInteger(versionNode.value).toInt(),
            community = communityNode.value.toString(Charsets.UTF_8),
            pduTag = pduNode.tag,
            requestId = decodeInteger(pdu[0].value),
            errorStatus = decodeInteger(pdu[1].value).toInt(),
            errorIndex = decodeInteger(pdu[2].value).toInt(),
            varbinds = varbinds,
        )
    }
}
