package xin.ctkqiang.deauthctrl.manager

/**
 * Flipper Zero .sub 文件格式解析与生成(纯函数,可单测)。
 *
 * ## 格式(FlipperZero-firmware 公开文档)
 * RAW:
 *   Filetype: Flipper SubGhz RAW File
 *   Version: 1
 *   Frequency: 433920000
 *   Preset: FuriHalSubGhzPresetOok650Async
 *   Protocol: RAW
 *   RAW_Data: 1000 -1000 1000 -1000 ...   (微秒,正=发射,负=静默)
 *
 * 静态码(如 Princeton):
 *   Filetype: Flipper SubGhz Key File
 *   Version: 1
 *   Frequency: 433920000
 *   Preset: FuriHalSubGhzPresetOok650Async
 *   Protocol: Princeton
 *   Bit: 24
 *   Key: 00 00 00 00 00 1E 53 B0
 *   TE: 400
 *
 * 本对象只做文件互操作与学习 —— 手机无 Sub-GHz 射频硬件,
 * 发射需 Flipper / CC1101 等硬件。
 */
object SubGhzFile {

    data class Parsed(
        val fileType: String,
        val version: Int,
        val frequencyHz: Long,
        val preset: String,
        val protocol: String,
        val rawData: List<Int> = emptyList(),
        val bit: Int = 0,
        val keyHex: String = "",
        val te: Int = 0,
    )

    /** 常见合法频段(Hz)与标签 */
    private val BANDS = listOf(
        433_920_000L to "433.92 MHz (ISM · 遥控/门磁)",
        315_000_000L to "315 MHz (美/亚 ISM)",
        868_350_000L to "868.35 MHz (欧 ISM)",
        915_000_000L to "915 MHz (美 ISM)",
        390_000_000L to "390 MHz (日/亚 ISM)",
        310_000_000L to "310 MHz (日 ISM)",
    )

    /** 频率合法性:300–928 MHz 常见 Sub-GHz 范围,给出最近合法频段提示 */
    fun bandLabel(frequencyHz: Long): String {
        if (frequencyHz in 300_000_000..928_000_000) {
            val nearest = BANDS.minByOrNull { kotlin.math.abs(it.first - frequencyHz) }
            return nearest?.second ?: "Sub-GHz 范围内"
        }
        return "超出常见 Sub-GHz 范围 (300–928 MHz)"
    }

    /** 解析 .sub 文本;格式不符返回 null */
    fun parse(text: String): Parsed? {
        val kv = mutableMapOf<String, String>()
        var rawLine: String? = null
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            if (trimmed.startsWith("RAW_Data:")) {
                rawLine = trimmed.removePrefix("RAW_Data:").trim()
            } else {
                val idx = trimmed.indexOf(':')
                if (idx > 0) kv[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim()
            }
        }
        val freq = kv["Frequency"]?.toLongOrNull() ?: return null
        val protocol = kv["Protocol"] ?: return null
        val raw = rawLine?.split(Regex("\\s+"))?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        val key = kv["Key"]?.replace(" ", "")?.uppercase() ?: ""
        return Parsed(
            fileType = kv["Filetype"] ?: "",
            version = kv["Version"]?.toIntOrNull() ?: 0,
            frequencyHz = freq,
            preset = kv["Preset"] ?: "",
            protocol = protocol,
            rawData = raw,
            bit = kv["Bit"]?.toIntOrNull() ?: 0,
            keyHex = key,
            te = kv["TE"]?.toIntOrNull() ?: 0,
        )
    }

    /** 生成 RAW 文件 */
    fun buildRaw(frequencyHz: Long, preset: String, data: List<Int>): String = buildString {
        appendLine("Filetype: Flipper SubGhz RAW File")
        appendLine("Version: 1")
        appendLine("Frequency: $frequencyHz")
        appendLine("Preset: $preset")
        appendLine("Protocol: RAW")
        appendLine("RAW_Data: ${data.joinToString(" ")}")
    }

    /** 生成静态码(Princeton)文件;keyHex 形如 "001E53B0"(可带空格) */
    fun buildPrinceton(frequencyHz: Long, preset: String, bit: Int, keyHex: String, te: Int): String {
        val key = keyHex.filter { it != ' ' && it != ':' }
        val spaced = key.chunked(2).joinToString(" ").uppercase()
        return buildString {
            appendLine("Filetype: Flipper SubGhz Key File")
            appendLine("Version: 1")
            appendLine("Frequency: $frequencyHz")
            appendLine("Preset: $preset")
            appendLine("Protocol: Princeton")
            appendLine("Bit: $bit")
            appendLine("Key: $spaced")
            appendLine("TE: $te")
        }
    }
}
