package xin.ctkqiang.deauthctrl.manager

import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

/**
 * 一条 AD Structure 的解析结果
 *
 * 对应 nRF Connect 广播详情里的四列展示：
 * @property name 字段中文名（如「Flags 标志」「完整本地名」）
 * @property typeHex AD 类型字节，形如 "0x09"
 * @property valueHex 数据区原始字节，空格分隔的大写十六进制；空数据为空串
 * @property decoded 按 Bluetooth Assigned Numbers 解码后的中文说明
 */
data class AdField(
    val name: String,
    val typeHex: String,
    val valueHex: String,
    val decoded: String,
)

/**
 * BLE 广播 AD Structure 解码器
 *
 * 纯 Kotlin 实现，不依赖 Android SDK，可直接在 JVM 单元测试中调用。
 *
 * ## 线格式
 * 广播数据是一串 AD Structure：
 * `[长度 1B][类型 1B][数据 …]`，长度字节包含类型字节本身；
 * 长度为 0 表示数据结束（其后的填充字节不再解析）。
 *
 * ## 数据纪律
 * - 只解码传入的真实字节，不补全、不推测缺失字段；
 * - 长度声明超过实际剩余字节时按实际长度截断，并在解码说明里明确标注；
 * - 16 / 32 位 UUID 按小端还原；128 位 UUID 整段反序后按 `8-4-4-4-12` 输出；
 * - 公司 ID 表只收录确认过的编号，未知编号如实显示为「未收录公司 ID」。
 *
 * ## 编号说明
 * 按 Bluetooth Assigned Numbers，0x1A 是 Advertising Interval、0x1B 是
 * LE Bluetooth Device Address；32-bit / 128-bit Service Data 是 0x20 / 0x21。
 * 部分第三方资料把 0x1A / 0x1B 误标为 32-bit Service Data，这里以官方编号为准。
 */
object AdStructureDecoder {

    /**
     * 解析完整广播数据
     *
     * @param advBytes 广播原始字节（不含前导长度）
     * @return 按出现顺序排列的字段列表；空输入返回空列表
     */
    fun decode(advBytes: ByteArray): List<AdField> {
        val fields = mutableListOf<AdField>()
        var index = 0
        while (index < advBytes.size) {
            val length = advBytes[index].toInt() and 0xFF
            // 长度 0 是广播数据的结束标记
            if (length == 0) break

            // 长度字节之后必须还有一个类型字节
            if (index + 1 >= advBytes.size) {
                fields += AdField(
                    name = L10n.get(R.string.mgr_ads_field_length_error),
                    typeHex = "--",
                    valueHex = "",
                    decoded = L10n.get(R.string.mgr_ads_err_length_no_type, index, length),
                )
                break
            }

            val type = advBytes[index + 1].toInt() and 0xFF
            val declaredDataLength = length - 1
            val available = advBytes.size - index - 2
            val truncated = declaredDataLength > available
            val actualDataLength = if (truncated) available else declaredDataLength
            val data = if (actualDataLength > 0) {
                advBytes.copyOfRange(index + 2, index + 2 + actualDataLength)
            } else {
                ByteArray(0)
            }

            fields += decodeField(
                type = type,
                data = data,
                truncated = truncated,
                declaredDataLength = declaredDataLength,
                actualDataLength = actualDataLength,
            )

            if (truncated) break
            index += 2 + actualDataLength
        }
        return fields
    }

    /** 便捷入口：十六进制文本 → 字段列表；格式非法返回 null */
    fun decodeHexText(input: String): List<AdField>? = parseHex(input)?.let { decode(it) }

    /**
     * 十六进制文本 → 字节
     *
     * 允许空格、冒号、短横线、逗号、点、下划线与 "0x" 前缀。
     * @return 格式非法（奇数位 / 非十六进制字符）返回 null
     */
    fun parseHex(input: String): ByteArray? {
        val cleaned = input.replace("0x", "", ignoreCase = true)
            .filterNot {
                it.isWhitespace() || it == ':' || it == '-' || it == ',' || it == '.' || it == '_'
            }
        if (cleaned.isEmpty()) return ByteArray(0)
        if (cleaned.length % 2 != 0) return null
        if (!cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /** 组装单个字段，并在截断时追加明确标注 */
    private fun decodeField(
        type: Int,
        data: ByteArray,
        truncated: Boolean,
        declaredDataLength: Int,
        actualDataLength: Int,
    ): AdField {
        val name = typeName(type)
        val base = decodeByType(type, data)
        val decoded = if (truncated) {
            L10n.get(R.string.mgr_ads_truncated, base, declaredDataLength, actualDataLength)
        } else {
            base
        }
        return AdField(
            name = name,
            typeHex = "0x%02X".format(type),
            valueHex = data.toAdHex(),
            decoded = decoded,
        )
    }

    /** AD 类型 → 解码说明；未收录的类型如实标注 */
    private fun decodeByType(type: Int, data: ByteArray): String = when (type) {
        0x01 -> decodeFlags(data)
        0x02, 0x03, 0x14 -> decodeUuid16List(data)
        0x04, 0x05, 0x1F -> decodeUuid32List(data)
        0x06, 0x07, 0x15 -> decodeUuid128List(data)
        0x08, 0x09 -> decodeLocalName(data)
        0x0A -> decodeTxPower(data)
        0x0D -> decodeClassOfDevice(data)
        0x12 -> decodeConnectionInterval(data)
        0x16 -> decodeServiceData16(data)
        0x19 -> decodeAppearance(data)
        0x1A -> decodeAdvertisingInterval(data)
        0x1B -> decodeLeDeviceAddress(data)
        0x1C -> decodeLeRole(data)
        0x20 -> decodeServiceData32(data)
        0x21 -> decodeServiceData128(data)
        0x24 -> decodeUri(data)
        0xFF -> decodeManufacturerData(data)
        else -> if (isKnownType(type)) {
            L10n.get(
                R.string.mgr_ads_known_unexpanded,
                data.toAdHex().ifEmpty { L10n.get(R.string.mgr_ads_empty) },
            )
        } else {
            L10n.get(
                R.string.mgr_ads_unknown_type,
                type,
                data.toAdHex().ifEmpty { L10n.get(R.string.mgr_ads_empty) },
            )
        }
    }

    // ─── 各字段解码 ──────────────────────────────────────────────────────────

    private fun decodeFlags(data: ByteArray): String {
        if (data.isEmpty()) return L10n.get(R.string.mgr_ads_err_flags_missing)
        val bits = data[0].toInt() and 0xFF
        val parts = buildList {
            if (bits and 0x01 != 0) add(L10n.get(R.string.mgr_ads_flag_limited))
            if (bits and 0x02 != 0) add(L10n.get(R.string.mgr_ads_flag_general))
            add(
                if (bits and 0x04 != 0) L10n.get(R.string.mgr_ads_flag_no_br_edr)
                else L10n.get(R.string.mgr_ads_flag_br_edr)
            )
            if (bits and 0x08 != 0) add(L10n.get(R.string.mgr_ads_flag_dual_controller))
            if (bits and 0x10 != 0) add(L10n.get(R.string.mgr_ads_flag_dual_host))
        }
        val reserved = bits and 0xE0
        val suffix = if (reserved != 0) L10n.get(R.string.mgr_ads_reserved_bits, reserved) else ""
        return L10n.get(R.string.mgr_ads_flags_line, bits, parts.joinToString(" + "), suffix)
    }

    private fun decodeUuid16List(data: ByteArray): String {
        if (data.isEmpty()) return L10n.get(R.string.mgr_ads_err_uuid_empty)
        if (data.size % 2 != 0) {
            return L10n.get(R.string.mgr_ads_err_len_not2, data.size, data.size % 2)
        }
        val values = mutableListOf<String>()
        var offset = 0
        while (offset + 1 < data.size) {
            val value = readU16(data, offset)
            val nameRes = SERVICE_UUIDS_16[value]
            values += "0x%04X".format(value) +
                if (nameRes != null) L10n.get(R.string.mgr_ads_paren, L10n.get(nameRes)) else ""
            offset += 2
        }
        return values.joinToString(" · ")
    }

    private fun decodeUuid32List(data: ByteArray): String {
        if (data.isEmpty()) return L10n.get(R.string.mgr_ads_err_uuid_empty)
        if (data.size % 4 != 0) {
            return L10n.get(R.string.mgr_ads_err_len_not4, data.size, data.size % 4)
        }
        val values = mutableListOf<String>()
        var offset = 0
        while (offset + 3 < data.size) {
            val value = readU32(data, offset)
            val aliasRes = if (value in 0..0xFFFF) SERVICE_UUIDS_16[value] else null
            values += "0x%08X".format(value) +
                if (aliasRes != null) L10n.get(R.string.mgr_ads_alias16, L10n.get(aliasRes)) else ""
            offset += 4
        }
        return values.joinToString(" · ")
    }

    private fun decodeUuid128List(data: ByteArray): String {
        if (data.isEmpty()) return L10n.get(R.string.mgr_ads_err_uuid_empty)
        if (data.size % 16 != 0) {
            return L10n.get(R.string.mgr_ads_err_len_not16, data.size, data.size % 16)
        }
        val values = mutableListOf<String>()
        var offset = 0
        while (offset + 15 < data.size) {
            values += describeUuid128(uuid128FromLe(data, offset))
            offset += 16
        }
        return values.joinToString(" · ")
    }

    private fun decodeLocalName(data: ByteArray): String {
        if (data.isEmpty()) return L10n.get(R.string.mgr_ads_err_name_empty)
        val raw = String(data, Charsets.UTF_8)
        val replacement = raw.count { it == '�' }
        val clean = raw.filter { ch ->
            ch == '\n' || ch == '\r' || ch == '\t' || !ch.isISOControl()
        }
        val suffix = if (replacement > 0) L10n.get(R.string.mgr_ads_bad_utf8, replacement) else ""
        return L10n.get(R.string.mgr_ads_local_name, clean, data.size, suffix)
    }

    private fun decodeTxPower(data: ByteArray): String {
        if (data.isEmpty()) return L10n.get(R.string.mgr_ads_err_tx_power_missing)
        // Byte → Int 保留符号位，得到真实 dBm
        val dbm = data[0].toInt()
        return L10n.get(R.string.mgr_ads_tx_power, dbm)
    }

    private fun decodeClassOfDevice(data: ByteArray): String {
        if (data.size < 3) return L10n.get(R.string.mgr_ads_err_cod_short)
        val cod = readU24(data, 0)
        val majorDevice = (cod shr 8) and 0x1F
        val serviceBits = cod shr 13
        val services = buildList {
            if (serviceBits and 0x001 != 0) add(L10n.get(R.string.mgr_ads_cod_service_limited))
            if (serviceBits and 0x002 != 0) add(L10n.get(R.string.mgr_ads_cod_service_positioning))
            if (serviceBits and 0x004 != 0) add(L10n.get(R.string.mgr_ads_cod_service_networking))
            if (serviceBits and 0x008 != 0) add(L10n.get(R.string.mgr_ads_cod_service_rendering))
            if (serviceBits and 0x010 != 0) add(L10n.get(R.string.mgr_ads_cod_service_capturing))
            if (serviceBits and 0x020 != 0) add(L10n.get(R.string.mgr_ads_cod_service_object_transfer))
            if (serviceBits and 0x040 != 0) add(L10n.get(R.string.mgr_ads_cod_service_audio))
            if (serviceBits and 0x080 != 0) add(L10n.get(R.string.mgr_ads_cod_service_telephony))
            if (serviceBits and 0x100 != 0) add(L10n.get(R.string.mgr_ads_cod_service_information))
        }
        val deviceName = MAJOR_DEVICE_CLASSES[majorDevice]?.let { L10n.get(it) }
            ?: L10n.get(R.string.mgr_ads_err_cod_unknown)
        val serviceText = if (services.isEmpty()) {
            L10n.get(R.string.mgr_ads_none)
        } else {
            services.joinToString("/")
        }
        return L10n.get(R.string.mgr_ads_cod_line, cod, deviceName, serviceText)
    }

    private fun decodeConnectionInterval(data: ByteArray): String {
        if (data.size < 4) return L10n.get(R.string.mgr_ads_err_conn_short)
        val min = readU16(data, 0)
        val max = readU16(data, 2)
        return L10n.get(R.string.mgr_ads_conn_interval, min * 1.25, min, max * 1.25, max)
    }

    private fun decodeAppearance(data: ByteArray): String {
        if (data.size < 2) return L10n.get(R.string.mgr_ads_err_appearance_short)
        val code = readU16(data, 0)
        val nameRes = APPEARANCES[code]
        return if (nameRes != null) {
            "0x%04X · %s".format(code, L10n.get(nameRes))
        } else {
            L10n.get(R.string.mgr_ads_appearance_unknown, code)
        }
    }

    private fun decodeAdvertisingInterval(data: ByteArray): String {
        if (data.size < 2) return L10n.get(R.string.mgr_ads_err_adv_interval_short)
        val raw = readU16(data, 0)
        return L10n.get(R.string.mgr_ads_adv_interval, raw, raw * 0.625)
    }

    private fun decodeLeDeviceAddress(data: ByteArray): String {
        if (data.size < 6) return L10n.get(R.string.mgr_ads_err_le_addr_short)
        // 广播中地址按小端排列，展示时反转为常见的 AA:BB:CC:DD:EE:FF
        val address = data.copyOfRange(0, 6).reversedArray()
            .joinToString(":") { "%02X".format(it) }
        val typeText = when {
            data.size < 7 -> L10n.get(R.string.mgr_ads_addr_type_missing)
            (data[6].toInt() and 0xFF) == 0 -> L10n.get(R.string.mgr_ads_addr_public)
            (data[6].toInt() and 0xFF) == 1 -> L10n.get(R.string.mgr_ads_addr_random)
            else -> L10n.get(R.string.mgr_ads_addr_type_unknown, data[6].toInt() and 0xFF)
        }
        return "$address · $typeText"
    }

    private fun decodeLeRole(data: ByteArray): String {
        if (data.isEmpty()) return L10n.get(R.string.mgr_ads_err_role_missing)
        return when (data[0].toInt() and 0xFF) {
            0 -> L10n.get(R.string.mgr_ads_role_peripheral_only)
            1 -> L10n.get(R.string.mgr_ads_role_central_only)
            2 -> L10n.get(R.string.mgr_ads_role_prefer_peripheral)
            3 -> L10n.get(R.string.mgr_ads_role_prefer_central)
            else -> L10n.get(R.string.mgr_ads_role_unknown, data[0].toInt() and 0xFF)
        }
    }

    private fun decodeServiceData16(data: ByteArray): String {
        if (data.size < 2) return L10n.get(R.string.mgr_ads_err_svcdata16_short)
        val uuid = readU16(data, 0)
        val payload = data.copyOfRange(2, data.size)
        val nameRes = SERVICE_UUIDS_16[uuid]
        val uuidText = "0x%04X".format(uuid) +
            if (nameRes != null) L10n.get(R.string.mgr_ads_paren, L10n.get(nameRes)) else ""
        return L10n.get(
            R.string.mgr_ads_svcdata,
            uuidText,
            payload.toAdHex().ifEmpty { L10n.get(R.string.mgr_ads_empty) },
            textSuffix(payload),
        )
    }

    private fun decodeServiceData32(data: ByteArray): String {
        if (data.size < 4) return L10n.get(R.string.mgr_ads_err_svcdata32_short)
        val uuid = readU32(data, 0)
        val payload = data.copyOfRange(4, data.size)
        val aliasRes = if (uuid in 0..0xFFFF) SERVICE_UUIDS_16[uuid] else null
        val uuidText = "0x%08X".format(uuid) +
            if (aliasRes != null) L10n.get(R.string.mgr_ads_alias16, L10n.get(aliasRes)) else ""
        return L10n.get(
            R.string.mgr_ads_svcdata,
            uuidText,
            payload.toAdHex().ifEmpty { L10n.get(R.string.mgr_ads_empty) },
            textSuffix(payload),
        )
    }

    private fun decodeServiceData128(data: ByteArray): String {
        if (data.size < 16) return L10n.get(R.string.mgr_ads_err_svcdata128_short)
        val uuid = describeUuid128(uuid128FromLe(data, 0))
        val payload = data.copyOfRange(16, data.size)
        return L10n.get(
            R.string.mgr_ads_svcdata,
            uuid,
            payload.toAdHex().ifEmpty { L10n.get(R.string.mgr_ads_empty) },
            textSuffix(payload),
        )
    }

    private fun decodeUri(data: ByteArray): String {
        if (data.isEmpty()) return L10n.get(R.string.mgr_ads_err_uri_empty)
        val text = String(data, Charsets.UTF_8)
        return L10n.get(R.string.mgr_ads_uri, text, data.size)
    }

    private fun decodeManufacturerData(data: ByteArray): String {
        if (data.size < 2) return L10n.get(R.string.mgr_ads_err_manufacturer_short)
        val companyId = readU16(data, 0)
        val company = COMPANY_IDS[companyId] ?: L10n.get(R.string.mgr_ads_err_company_unknown)
        val payload = data.copyOfRange(2, data.size)
        return L10n.get(
            R.string.mgr_ads_manufacturer_line,
            companyId,
            company,
            payload.toAdHex().ifEmpty { L10n.get(R.string.mgr_ads_empty) },
            textSuffix(payload),
        )
    }

    // ─── 编号表 ──────────────────────────────────────────────────────────────

    private fun typeName(type: Int): String =
        TYPE_NAMES[type] ?: L10n.get(R.string.mgr_ads_field_unknown_type)

    private fun isKnownType(type: Int): Boolean = TYPE_NAMES.containsKey(type)

    private fun readU16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU24(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16)

    private fun readU32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

    private fun uuid128FromLe(data: ByteArray, offset: Int): String {
        val standard = data.copyOfRange(offset, offset + 16).reversedArray()
        val hex = standard.joinToString("") { "%02X".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    /** 若 128-bit UUID 落在 Bluetooth Base UUID 上，补注对应的 16-bit 别名 */
    private fun describeUuid128(uuid: String): String {
        val upper = uuid.uppercase()
        if (upper.startsWith("0000") && upper.endsWith("-0000-1000-8000-00805F9B34FB")) {
            val short = upper.substring(4, 8)
            val nameRes = short.toIntOrNull(16)?.let { SERVICE_UUIDS_16[it] }
            return if (nameRes != null) {
                L10n.get(R.string.mgr_ads_uuid128_alias_named, uuid, short, L10n.get(nameRes))
            } else {
                L10n.get(R.string.mgr_ads_uuid128_alias, uuid, short)
            }
        }
        return uuid
    }

    private fun textSuffix(payload: ByteArray): String {
        val preview = printableText(payload)
        return if (preview.isNotEmpty()) {
            " · " + L10n.get(R.string.mgr_ads_text_suffix, preview)
        } else {
            ""
        }
    }

    /** 只在整段可按 UTF-8 解码且均为可打印字符时给出文本预览 */
    private fun printableText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return ""
        val printable = text.all { ch ->
            ch == '\n' || ch == '\r' || ch == '\t' || (!ch.isISOControl() && ch != '�')
        }
        if (!printable || text.isBlank()) return ""
        return text.take(48)
    }

    private fun ByteArray.toAdHex(): String = joinToString(" ") { "%02X".format(it) }

    // ─── 常量表 ──────────────────────────────────────────────────────────────

    private val TYPE_NAMES: Map<Int, String> = mapOf(
        0x01 to "Flags 标志",
        0x02 to "16-bit UUID 列表（不完整）",
        0x03 to "16-bit UUID 列表（完整）",
        0x04 to "32-bit UUID 列表（不完整）",
        0x05 to "32-bit UUID 列表（完整）",
        0x06 to "128-bit UUID 列表（不完整）",
        0x07 to "128-bit UUID 列表（完整）",
        0x08 to "短本地名",
        0x09 to "完整本地名",
        0x0A to "TX 发射功率",
        0x0D to "Class of Device 设备类别",
        0x0E to "Simple Pairing Hash C-192",
        0x0F to "Simple Pairing Randomizer R-192",
        0x10 to "Device ID",
        0x12 to "外设连接间隔范围",
        0x14 to "16-bit 服务请求 UUID 列表",
        0x15 to "128-bit 服务请求 UUID 列表",
        0x16 to "Service Data（16-bit UUID）",
        0x17 to "Public Target Address",
        0x18 to "Random Target Address",
        0x19 to "Appearance 外观",
        0x1A to "Advertising Interval 广播间隔",
        0x1B to "LE Bluetooth Device Address",
        0x1C to "LE Role",
        0x1D to "Simple Pairing Hash C-256",
        0x1E to "Simple Pairing Randomizer R-256",
        0x1F to "32-bit 服务请求 UUID 列表",
        0x20 to "Service Data（32-bit UUID）",
        0x21 to "Service Data（128-bit UUID）",
        0x24 to "URI",
        0x25 to "Indoor Positioning",
        0x26 to "Transport Discovery Data",
        0x27 to "LE Supported Features",
        0x28 to "Channel Map Update Indication",
        0x29 to "PB-ADV",
        0x2A to "Mesh Message",
        0x2B to "Mesh Beacon",
        0x2C to "BIGInfo",
        0x2D to "Broadcast Code",
        0x2E to "Resolvable Set Identifier",
        0x3D to "3D Information Data",
        0xFF to "厂商自定义数据",
    )

    /** 常用 16-bit UUID；0xFFA0-0xFFFF 为厂商自定义区 */
    private val SERVICE_UUIDS_16: Map<Int, Int> = mapOf(
        0x1800 to R.string.mgr_ads_svc_generic_access,
        0x1801 to R.string.mgr_ads_svc_generic_attribute,
        0x1802 to R.string.mgr_ads_svc_immediate_alert,
        0x1803 to R.string.mgr_ads_svc_link_loss,
        0x1804 to R.string.mgr_ads_svc_tx_power,
        0x1805 to R.string.mgr_ads_svc_current_time,
        0x1808 to R.string.mgr_ads_svc_glucose,
        0x1809 to R.string.mgr_ads_svc_health_thermometer,
        0x180A to R.string.mgr_ads_svc_device_information,
        0x180D to R.string.mgr_ads_svc_heart_rate,
        0x180F to R.string.mgr_ads_svc_battery,
        0x1810 to R.string.mgr_ads_svc_blood_pressure,
        0x1811 to R.string.mgr_ads_svc_alert_notification,
        0x1812 to R.string.mgr_ads_svc_hid,
        0x1814 to R.string.mgr_ads_svc_running_speed,
        0x1816 to R.string.mgr_ads_svc_cycling_speed,
        0x1818 to R.string.mgr_ads_svc_cycling_power,
        0x1819 to R.string.mgr_ads_svc_location_navigation,
        0x181A to R.string.mgr_ads_svc_environmental_sensing,
        0x1826 to R.string.mgr_ads_svc_fitness_machine,
        0x1843 to R.string.mgr_ads_svc_volume_control,
        0x1846 to R.string.mgr_ads_svc_coordinated_set,
        0x1848 to R.string.mgr_ads_svc_media_control,
        0xFFE0 to R.string.mgr_ads_svc_vendor_ffe0,
    )

    /** 公司 ID（Bluetooth SIG Assigned Numbers，小端传输）；只收录确认过的编号 */
    private val COMPANY_IDS: Map<Int, String> = mapOf(
        0x0000 to "Ericsson AB",
        0x0001 to "Intel Corp.",
        0x0002 to "IBM Corp.",
        0x0003 to "Toshiba Corp.",
        0x0006 to "Microsoft",
        0x000D to "Texas Instruments",
        0x000F to "Broadcom",
        0x004C to "Apple, Inc.",
        0x0059 to "Nordic Semiconductor ASA",
        0x0075 to "Samsung Electronics Co. Ltd.",
        0x00E0 to "Google",
        0x0157 to "Sony Corporation",
        0x0171 to "Amazon.com Services LLC",
        0x02E5 to "Espressif Systems",
    )

    /** 常见 Appearance 值；未收录的值如实显示码值 */
    private val APPEARANCES: Map<Int, Int> = mapOf(
        0x0000 to R.string.mgr_ads_app_unknown,
        0x0040 to R.string.mgr_ads_app_phone,
        0x0080 to R.string.mgr_ads_app_computer,
        0x00C0 to R.string.mgr_ads_app_watch,
        0x0100 to R.string.mgr_ads_app_clock,
        0x0140 to R.string.mgr_ads_app_display,
        0x0180 to R.string.mgr_ads_app_remote,
        0x01C0 to R.string.mgr_ads_app_eyewear,
        0x0200 to R.string.mgr_ads_app_tag,
        0x0240 to R.string.mgr_ads_app_keyring,
        0x0280 to R.string.mgr_ads_app_media_player,
        0x02C0 to R.string.mgr_ads_app_barcode_scanner,
        0x0300 to R.string.mgr_ads_app_thermometer,
        0x0340 to R.string.mgr_ads_app_heart_rate,
        0x0380 to R.string.mgr_ads_app_blood_pressure,
        0x03C0 to R.string.mgr_ads_app_hid,
        0x0400 to R.string.mgr_ads_app_glucose,
        0x0440 to R.string.mgr_ads_app_running,
        0x0480 to R.string.mgr_ads_app_cycling,
        0x04C0 to R.string.mgr_ads_app_control,
        0x0500 to R.string.mgr_ads_app_network,
        0x0540 to R.string.mgr_ads_app_sensor,
        0x0580 to R.string.mgr_ads_app_light_fixture,
        0x05C0 to R.string.mgr_ads_app_fan,
        0x0600 to R.string.mgr_ads_app_hvac,
        0x0640 to R.string.mgr_ads_app_air_conditioning,
        0x0680 to R.string.mgr_ads_app_humidifier,
        0x06C0 to R.string.mgr_ads_app_heater,
        0x0700 to R.string.mgr_ads_app_access_control,
        0x0740 to R.string.mgr_ads_app_motorized,
        0x0780 to R.string.mgr_ads_app_power,
        0x07C0 to R.string.mgr_ads_app_light_source,
        0x0800 to R.string.mgr_ads_app_window_covering,
        0x0840 to R.string.mgr_ads_app_audio_sink,
        0x0841 to R.string.mgr_ads_app_headset,
        0x0842 to R.string.mgr_ads_app_speaker,
        0x0843 to R.string.mgr_ads_app_earbuds,
        0x0880 to R.string.mgr_ads_app_audio_source,
        0x0900 to R.string.mgr_ads_app_vehicle,
        0x0940 to R.string.mgr_ads_app_home_appliance,
        0x0980 to R.string.mgr_ads_app_wearable_audio,
        0x09C0 to R.string.mgr_ads_app_aircraft,
        0x0A00 to R.string.mgr_ads_app_av_equipment,
        0x0AC0 to R.string.mgr_ads_app_gaming,
        0x0B00 to R.string.mgr_ads_app_signage,
    )

    /** Class of Device 主要设备类（bits 8-12） */
    private val MAJOR_DEVICE_CLASSES: Map<Int, Int> = mapOf(
        0x00 to R.string.mgr_ads_major_misc,
        0x01 to R.string.mgr_ads_major_computer,
        0x02 to R.string.mgr_ads_major_phone,
        0x03 to R.string.mgr_ads_major_lan,
        0x04 to R.string.mgr_ads_major_av,
        0x05 to R.string.mgr_ads_major_peripheral,
        0x06 to R.string.mgr_ads_major_imaging,
        0x07 to R.string.mgr_ads_major_wearable,
        0x08 to R.string.mgr_ads_major_toy,
        0x09 to R.string.mgr_ads_major_health,
        0x1F to R.string.mgr_ads_major_uncategorized,
    )
}
