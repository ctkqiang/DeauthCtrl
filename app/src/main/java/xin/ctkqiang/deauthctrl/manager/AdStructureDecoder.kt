package xin.ctkqiang.deauthctrl.manager

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
                    name = "长度字段异常",
                    typeHex = "--",
                    valueHex = "",
                    decoded = "[!] 偏移 $index 处声明长度 ${length}B，但其后没有 AD 类型字节，数据在此截断",
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
            "$base；[!] 声明数据 ${declaredDataLength}B，实际仅剩 ${actualDataLength}B，已截断"
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
            "该类型已收录但本页未展开字段结构 —— 原始值 ${data.toAdHex().ifEmpty { "(空)" }}"
        } else {
            "未知 AD 类型 0x%02X —— 原始值 ${data.toAdHex().ifEmpty { "(空)" }}".format(type)
        }
    }

    // ─── 各字段解码 ──────────────────────────────────────────────────────────

    private fun decodeFlags(data: ByteArray): String {
        if (data.isEmpty()) return "标志字节缺失"
        val bits = data[0].toInt() and 0xFF
        val parts = buildList {
            if (bits and 0x01 != 0) add("LE 限定发现模式")
            if (bits and 0x02 != 0) add("LE 通用发现模式")
            add(if (bits and 0x04 != 0) "BR/EDR 不支持（LE 专用）" else "BR/EDR 可用")
            if (bits and 0x08 != 0) add("LE 与 BR/EDR 同时支持（控制器）")
            if (bits and 0x10 != 0) add("LE 与 BR/EDR 同时支持（主机）")
        }
        val reserved = bits and 0xE0
        val suffix = if (reserved != 0) "；[!] 保留位 0x%02X 非零".format(reserved) else ""
        return "0x%02X：%s%s".format(bits, parts.joinToString(" + "), suffix)
    }

    private fun decodeUuid16List(data: ByteArray): String {
        if (data.isEmpty()) return "UUID 列表为空"
        if (data.size % 2 != 0) {
            return "数据长度 ${data.size}B 不是 2 的整数倍，末尾 ${data.size % 2}B 无法解析"
        }
        val values = mutableListOf<String>()
        var offset = 0
        while (offset + 1 < data.size) {
            val value = readU16(data, offset)
            val name = SERVICE_UUIDS_16[value]
            values += "0x%04X".format(value) + if (name != null) "（$name）" else ""
            offset += 2
        }
        return values.joinToString(" · ")
    }

    private fun decodeUuid32List(data: ByteArray): String {
        if (data.isEmpty()) return "UUID 列表为空"
        if (data.size % 4 != 0) {
            return "数据长度 ${data.size}B 不是 4 的整数倍，末尾 ${data.size % 4}B 无法解析"
        }
        val values = mutableListOf<String>()
        var offset = 0
        while (offset + 3 < data.size) {
            val value = readU32(data, offset)
            val alias = if (value in 0..0xFFFF) SERVICE_UUIDS_16[value] else null
            values += "0x%08X".format(value) + if (alias != null) "（16-bit 别名：$alias）" else ""
            offset += 4
        }
        return values.joinToString(" · ")
    }

    private fun decodeUuid128List(data: ByteArray): String {
        if (data.isEmpty()) return "UUID 列表为空"
        if (data.size % 16 != 0) {
            return "数据长度 ${data.size}B 不是 16 的整数倍，末尾 ${data.size % 16}B 无法解析"
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
        if (data.isEmpty()) return "名称为空"
        val raw = String(data, Charsets.UTF_8)
        val replacement = raw.count { it == '�' }
        val clean = raw.filter { ch ->
            ch == '\n' || ch == '\r' || ch == '\t' || !ch.isISOControl()
        }
        val suffix = if (replacement > 0) "；[!] 含 $replacement 个非 UTF-8 替换字符" else ""
        return "\"$clean\"（${data.size}B UTF-8）$suffix"
    }

    private fun decodeTxPower(data: ByteArray): String {
        if (data.isEmpty()) return "功率字节缺失"
        // Byte → Int 保留符号位，得到真实 dBm
        val dbm = data[0].toInt()
        return "$dbm dBm（有符号 8-bit）"
    }

    private fun decodeClassOfDevice(data: ByteArray): String {
        if (data.size < 3) return "数据不足 3B（Class of Device 为 24-bit）"
        val cod = readU24(data, 0)
        val majorDevice = (cod shr 8) and 0x1F
        val serviceBits = cod shr 13
        val services = buildList {
            if (serviceBits and 0x001 != 0) add("受限发现")
            if (serviceBits and 0x002 != 0) add("定位")
            if (serviceBits and 0x004 != 0) add("网络")
            if (serviceBits and 0x008 != 0) add("渲染")
            if (serviceBits and 0x010 != 0) add("捕获")
            if (serviceBits and 0x020 != 0) add("对象传输")
            if (serviceBits and 0x040 != 0) add("音频")
            if (serviceBits and 0x080 != 0) add("电话")
            if (serviceBits and 0x100 != 0) add("信息")
        }
        val deviceName = MAJOR_DEVICE_CLASSES[majorDevice] ?: "未收录设备类"
        val serviceText = if (services.isEmpty()) "无" else services.joinToString("/")
        return "0x%06X：主要设备类 %s；服务类 %s".format(cod, deviceName, serviceText)
    }

    private fun decodeConnectionInterval(data: ByteArray): String {
        if (data.size < 4) return "数据不足 4B（需要最小 / 最大连接间隔各 2B）"
        val min = readU16(data, 0)
        val max = readU16(data, 2)
        return "最小 %.2f ms（0x%04X） · 最大 %.2f ms（0x%04X） · 单位 1.25 ms"
            .format(min * 1.25, min, max * 1.25, max)
    }

    private fun decodeAppearance(data: ByteArray): String {
        if (data.size < 2) return "数据不足 2B（Appearance 为 16-bit）"
        val code = readU16(data, 0)
        val name = APPEARANCES[code]
        return if (name != null) {
            "0x%04X · %s".format(code, name)
        } else {
            "0x%04X · 未收录外观码".format(code)
        }
    }

    private fun decodeAdvertisingInterval(data: ByteArray): String {
        if (data.size < 2) return "数据不足 2B（Advertising Interval 为 16-bit）"
        val raw = readU16(data, 0)
        return "0x%04X · %.2f ms（单位 0.625 ms）".format(raw, raw * 0.625)
    }

    private fun decodeLeDeviceAddress(data: ByteArray): String {
        if (data.size < 6) return "数据不足 6B（LE 设备地址为 6B）"
        // 广播中地址按小端排列，展示时反转为常见的 AA:BB:CC:DD:EE:FF
        val address = data.copyOfRange(0, 6).reversedArray()
            .joinToString(":") { "%02X".format(it) }
        val typeText = when {
            data.size < 7 -> "地址类型缺失"
            (data[6].toInt() and 0xFF) == 0 -> "公共地址"
            (data[6].toInt() and 0xFF) == 1 -> "随机地址"
            else -> "未知地址类型 ${data[6].toInt() and 0xFF}"
        }
        return "$address · $typeText"
    }

    private fun decodeLeRole(data: ByteArray): String {
        if (data.isEmpty()) return "角色字节缺失"
        return when (data[0].toInt() and 0xFF) {
            0 -> "仅 Peripheral（从机）"
            1 -> "仅 Central（主机）"
            2 -> "偏好 Peripheral"
            3 -> "偏好 Central"
            else -> "未收录角色值 ${data[0].toInt() and 0xFF}"
        }
    }

    private fun decodeServiceData16(data: ByteArray): String {
        if (data.size < 2) return "数据不足 2B（缺少 16-bit 服务 UUID）"
        val uuid = readU16(data, 0)
        val payload = data.copyOfRange(2, data.size)
        val name = SERVICE_UUIDS_16[uuid]
        val uuidText = "0x%04X".format(uuid) + if (name != null) "（$name）" else ""
        return "UUID $uuidText · 数据 ${payload.toAdHex().ifEmpty { "(空)" }}${textSuffix(payload)}"
    }

    private fun decodeServiceData32(data: ByteArray): String {
        if (data.size < 4) return "数据不足 4B（缺少 32-bit 服务 UUID）"
        val uuid = readU32(data, 0)
        val payload = data.copyOfRange(4, data.size)
        val alias = if (uuid in 0..0xFFFF) SERVICE_UUIDS_16[uuid] else null
        val uuidText = "0x%08X".format(uuid) + if (alias != null) "（16-bit 别名：$alias）" else ""
        return "UUID $uuidText · 数据 ${payload.toAdHex().ifEmpty { "(空)" }}${textSuffix(payload)}"
    }

    private fun decodeServiceData128(data: ByteArray): String {
        if (data.size < 16) return "数据不足 16B（缺少 128-bit 服务 UUID）"
        val uuid = describeUuid128(uuid128FromLe(data, 0))
        val payload = data.copyOfRange(16, data.size)
        return "UUID $uuid · 数据 ${payload.toAdHex().ifEmpty { "(空)" }}${textSuffix(payload)}"
    }

    private fun decodeUri(data: ByteArray): String {
        if (data.isEmpty()) return "URI 为空"
        val text = String(data, Charsets.UTF_8)
        return "\"$text\"（${data.size}B UTF-8）"
    }

    private fun decodeManufacturerData(data: ByteArray): String {
        if (data.size < 2) return "数据不足 2B（缺少公司 ID）"
        val companyId = readU16(data, 0)
        val company = COMPANY_IDS[companyId] ?: "未收录公司 ID"
        val payload = data.copyOfRange(2, data.size)
        return "公司 ID 0x%04X · %s · 数据 %s%s".format(
            companyId,
            company,
            payload.toAdHex().ifEmpty { "(空)" },
            textSuffix(payload),
        )
    }

    // ─── 编号表 ──────────────────────────────────────────────────────────────

    private fun typeName(type: Int): String = TYPE_NAMES[type] ?: "未知 AD 类型"

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
            val name = short.toIntOrNull(16)?.let { SERVICE_UUIDS_16[it] }
            return if (name != null) {
                "$uuid（16-bit 别名 0x$short：$name）"
            } else {
                "$uuid（16-bit 别名 0x$short）"
            }
        }
        return uuid
    }

    private fun textSuffix(payload: ByteArray): String {
        val preview = printableText(payload)
        return if (preview.isNotEmpty()) " · 文本 \"$preview\"" else ""
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
    private val SERVICE_UUIDS_16: Map<Int, String> = mapOf(
        0x1800 to "Generic Access 通用访问",
        0x1801 to "Generic Attribute 通用属性",
        0x1802 to "Immediate Alert 即时告警",
        0x1803 to "Link Loss 链路丢失",
        0x1804 to "Tx Power 发射功率",
        0x1805 to "Current Time 当前时间",
        0x1808 to "Glucose 血糖",
        0x1809 to "Health Thermometer 体温计",
        0x180A to "Device Information 设备信息",
        0x180D to "Heart Rate 心率",
        0x180F to "Battery 电池",
        0x1810 to "Blood Pressure 血压",
        0x1811 to "Alert Notification 告警通知",
        0x1812 to "HID 人机接口",
        0x1814 to "Running Speed and Cadence 跑步速度与步频",
        0x1816 to "Cycling Speed and Cadence 骑行速度与踏频",
        0x1818 to "Cycling Power 骑行功率",
        0x1819 to "Location and Navigation 定位与导航",
        0x181A to "Environmental Sensing 环境传感",
        0x1826 to "Fitness Machine 健身器械",
        0x1843 to "Volume Control 音量控制",
        0x1846 to "Coordinated Set Identification 协调集合标识",
        0x1848 to "Media Control 媒体控制",
        0xFFE0 to "厂商自定义串口服务区（常见 FFE0）",
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
    private val APPEARANCES: Map<Int, String> = mapOf(
        0x0000 to "未知",
        0x0040 to "手机",
        0x0080 to "电脑",
        0x00C0 to "手表",
        0x0100 to "时钟",
        0x0140 to "显示器",
        0x0180 to "遥控器",
        0x01C0 to "眼镜",
        0x0200 to "标签",
        0x0240 to "钥匙扣",
        0x0280 to "媒体播放器",
        0x02C0 to "条码扫描器",
        0x0300 to "温度计",
        0x0340 to "心率传感器",
        0x0380 to "血压计",
        0x03C0 to "HID 人机接口设备",
        0x0400 to "血糖仪",
        0x0440 to "跑步 / 步行传感器",
        0x0480 to "骑行传感器",
        0x04C0 to "控制设备",
        0x0500 to "网络设备",
        0x0540 to "传感器",
        0x0580 to "灯具",
        0x05C0 to "风扇",
        0x0600 to "HVAC 暖通设备",
        0x0640 to "空调",
        0x0680 to "加湿器",
        0x06C0 to "加热器",
        0x0700 to "门禁",
        0x0740 to "电动设备",
        0x0780 to "电源设备",
        0x07C0 to "光源",
        0x0800 to "窗帘",
        0x0840 to "音频接收器",
        0x0841 to "耳机",
        0x0842 to "扬声器",
        0x0843 to "耳麦",
        0x0880 to "音频源",
        0x0900 to "机动车",
        0x0940 to "家用电器",
        0x0980 to "可穿戴音频设备",
        0x09C0 to "飞行器",
        0x0A00 to "视听设备",
        0x0AC0 to "游戏设备",
        0x0B00 to "标牌",
    )

    /** Class of Device 主要设备类（bits 8-12） */
    private val MAJOR_DEVICE_CLASSES: Map<Int, String> = mapOf(
        0x00 to "杂项",
        0x01 to "计算机",
        0x02 to "手机",
        0x03 to "局域网 / 网络接入点",
        0x04 to "音频 / 视频",
        0x05 to "外设",
        0x06 to "成像",
        0x07 to "可穿戴",
        0x08 to "玩具",
        0x09 to "健康",
        0x1F to "未分类",
    )
}
