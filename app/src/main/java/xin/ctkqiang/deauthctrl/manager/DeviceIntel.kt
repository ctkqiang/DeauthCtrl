package xin.ctkqiang.deauthctrl.manager

import androidx.annotation.StringRes
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

/**
 * 一条设备指纹命中结果。
 *
 * @property category 分类:黑客工具 / 开发板 / 物联网 / 可穿戴 / 音频 / 手机 / 电脑 / 未知
 * @property label 具体型号或品牌(能到型号就到型号,做不到就到品类)
 * @property confidence 启发式置信度(0..1),不是校准概率,UI 不应直接当百分比展示
 * @property basis 判定依据:命中的公开指纹字段与关键词,便于人工复核
 */
data class IntelHit(
    val category: String,
    val label: String,
    val confidence: Float,
    val basis: String,
)

/**
 * 设备情报指纹库 —— 纯 Kotlin 分类器,无 Android 依赖,可直接跑 JVM 单测。
 *
 * 输入约定:
 * - WiFi 扫描:name = SSID,bssidOrMac = BSSID,serviceUuids = 空,manufacturerId = null
 * - BLE 扫描:name = 广播名,bssidOrMac = MAC,serviceUuids = 广播/扫描响应中的服务 UUID,
 *   manufacturerId = 蓝牙厂商标识符(Company Identifier)
 *
 * 判定优先级:黑客工具 > 开发板 > 物联网 > 可穿戴 / 音频 > 手机 / 电脑。
 * 只收录公开、可复核的高置信指纹;拿不准一律返回 null(未知),绝不编造。
 *
 * 数据纪律:
 * - 厂商标识只说明芯片/模组厂商,不代表整机品牌;
 * - 名称匹配是启发式线索,不是证据;
 * - bssidOrMac 目前仅保留接口:不内置不完整的 OUI 表,避免用半可靠数据下结论。
 */
object DeviceIntel {

    const val CATEGORY_HACKER = "黑客工具"
    const val CATEGORY_DEV_BOARD = "开发板"
    const val CATEGORY_IOT = "物联网"
    const val CATEGORY_WEARABLE = "可穿戴"
    const val CATEGORY_AUDIO = "音频"
    const val CATEGORY_PHONE = "手机"
    const val CATEGORY_COMPUTER = "电脑"
    const val CATEGORY_UNKNOWN = "未知"

    // 蓝牙 SIG 公开厂商标识符(Company Identifier)
    private const val MANUFACTURER_APPLE = 0x004C
    private const val MANUFACTURER_NORDIC = 0x0059
    private const val MANUFACTURER_ESPRESSIF = 0x02E5

    /** 名称关键词规则(品牌/型号) */
    private data class NameRule(
        val keyword: String,
        @StringRes val labelRes: Int,
        @StringRes val noteRes: Int,
        val wholeWord: Boolean = false,
    )

    private val WEARABLE_RULES = listOf(
        NameRule("apple watch", R.string.mgr_intel_label_apple_watch, R.string.mgr_intel_note_apple_watch),
        NameRule("galaxy watch", R.string.mgr_intel_label_galaxy_watch, R.string.mgr_intel_note_galaxy_watch),
        NameRule("mi band", R.string.mgr_intel_label_mi_band, R.string.mgr_intel_note_mi_band),
        NameRule("miband", R.string.mgr_intel_label_mi_band, R.string.mgr_intel_note_miband),
        NameRule("amazfit", R.string.mgr_intel_label_amazfit, R.string.mgr_intel_note_amazfit),
        NameRule("huawei watch", R.string.mgr_intel_label_huawei_watch, R.string.mgr_intel_note_huawei_watch),
    )

    private val AUDIO_RULES = listOf(
        NameRule("airpods", R.string.mgr_intel_label_airpods, R.string.mgr_intel_note_airpods),
        NameRule("galaxy buds", R.string.mgr_intel_label_galaxy_buds, R.string.mgr_intel_note_galaxy_buds),
        NameRule("redmi buds", R.string.mgr_intel_label_redmi_buds, R.string.mgr_intel_note_redmi_buds),
    )

    private val IOT_RULES = listOf(
        NameRule("sonoff", R.string.mgr_intel_label_sonoff, R.string.mgr_intel_note_sonoff),
        NameRule("tuya", R.string.mgr_intel_label_tuya, R.string.mgr_intel_note_tuya),
        NameRule("yeelight", R.string.mgr_intel_label_yeelight, R.string.mgr_intel_note_yeelight),
        NameRule("aqara", R.string.mgr_intel_label_aqara, R.string.mgr_intel_note_aqara),
        NameRule("switchbot", R.string.mgr_intel_label_switchbot, R.string.mgr_intel_note_switchbot),
        NameRule("tp-link", R.string.mgr_intel_label_tp_link, R.string.mgr_intel_note_tp_link),
        NameRule(
            "ring",
            R.string.mgr_intel_label_ring,
            R.string.mgr_intel_note_ring,
            wholeWord = true,
        ),
        NameRule(
            "tile",
            R.string.mgr_intel_label_tile,
            R.string.mgr_intel_note_tile,
            wholeWord = true,
        ),
    )

    /**
     * 识别一台设备。
     *
     * @param name WiFi SSID 或 BLE 广播名
     * @param bssidOrMac BSSID / 蓝牙 MAC(接口保留,当前不参与判定)
     * @param serviceUuids BLE 广播/扫描响应中的服务 UUID,空列表表示 WiFi 或未知
     * @param manufacturerId 蓝牙厂商标识符,WiFi 扫描传 null
     * @return 命中项;未知返回 null
     */
    fun classify(
        name: String?,
        bssidOrMac: String?,
        serviceUuids: List<String>,
        manufacturerId: Int?,
    ): IntelHit? {
        val displayName = name?.trim().orEmpty()
        val lower = displayName.lowercase()
        // BLE 上下文:厂商 ID 与服务 UUID 只会出现在 BLE 广播数据里
        val isBle = manufacturerId != null || serviceUuids.isNotEmpty()

        // ---------- 1. 黑客工具(最高优先级)----------
        if (lower.contains("flipper")) {
            return IntelHit(
                CATEGORY_HACKER,
                "Flipper Zero",
                0.9f,
                L10n.get(R.string.mgr_intel_basis_flipper),
            )
        }

        if (lower.contains("pwned")) {
            return IntelHit(
                CATEGORY_HACKER,
                "Deauther Watch / DSTIKE",
                0.95f,
                L10n.get(R.string.mgr_intel_basis_pwned),
            )
        }

        if (lower.startsWith("pineapple_") || lower.startsWith("hak5")) {
            return IntelHit(
                CATEGORY_HACKER,
                "WiFi Pineapple",
                0.9f,
                L10n.get(R.string.mgr_intel_basis_pineapple),
            )
        }

        // "OMG" 是普通英文缩写,只有存在 BLE 广播数据时才按低置信线索处理
        if (isBle && lower.contains("omg")) {
            return IntelHit(
                CATEGORY_HACKER,
                "O.MG Cable",
                0.6f,
                L10n.get(R.string.mgr_intel_basis_omg),
            )
        }

        // ---------- 2. 开发板 ----------
        val espByName = lower.contains("esp32") ||
            lower.contains("esp8266") ||
            lower.contains("esp_")
        if (manufacturerId == MANUFACTURER_ESPRESSIF || espByName) {
            val basis = if (manufacturerId == MANUFACTURER_ESPRESSIF) {
                L10n.get(R.string.mgr_intel_basis_espressif)
            } else {
                L10n.get(R.string.mgr_intel_basis_esp_name)
            }
            return IntelHit(CATEGORY_DEV_BOARD, "ESP32 / ESP8266", 0.85f, basis)
        }

        val nordicByName = lower.contains("nrf") || lower.contains("nordic_")
        if (manufacturerId == MANUFACTURER_NORDIC || nordicByName) {
            val basis = if (manufacturerId == MANUFACTURER_NORDIC) {
                L10n.get(R.string.mgr_intel_basis_nordic)
            } else {
                L10n.get(R.string.mgr_intel_basis_nordic_name)
            }
            return IntelHit(CATEGORY_DEV_BOARD, "Nordic nRF", 0.85f, basis)
        }

        if (lower.contains("arduino")) {
            return IntelHit(
                CATEGORY_DEV_BOARD,
                "Arduino",
                0.8f,
                L10n.get(R.string.mgr_intel_basis_arduino),
            )
        }

        // ---------- 3. 物联网 ----------
        matchRule(lower, IOT_RULES)?.let { rule ->
            return IntelHit(
                CATEGORY_IOT,
                L10n.get(rule.labelRes),
                0.75f,
                L10n.get(R.string.mgr_intel_basis_keyword, rule.keyword, L10n.get(rule.noteRes)),
            )
        }

        // ---------- 4. 可穿戴 / 音频(常见消费设备,低危)----------
        matchRule(lower, WEARABLE_RULES)?.let { rule ->
            return IntelHit(
                CATEGORY_WEARABLE,
                L10n.get(rule.labelRes),
                0.8f,
                L10n.get(R.string.mgr_intel_basis_keyword, rule.keyword, L10n.get(rule.noteRes)),
            )
        }

        matchRule(lower, AUDIO_RULES)?.let { rule ->
            return IntelHit(
                CATEGORY_AUDIO,
                L10n.get(rule.labelRes),
                0.8f,
                L10n.get(R.string.mgr_intel_basis_keyword, rule.keyword, L10n.get(rule.noteRes)),
            )
        }

        // ---------- 5. 手机 / 电脑(最低优先级)----------
        if (lower.contains("raspberry")) {
            return IntelHit(
                CATEGORY_COMPUTER,
                "Raspberry Pi",
                0.9f,
                L10n.get(R.string.mgr_intel_basis_raspberry),
            )
        }

        // 厂商 ID 无法区分 iPhone / iPad / Mac / 配件,因此只标到"苹果设备"
        if (manufacturerId == MANUFACTURER_APPLE) {
            return IntelHit(
                CATEGORY_PHONE,
                L10n.get(R.string.mgr_intel_label_apple_device),
                0.7f,
                L10n.get(R.string.mgr_intel_basis_apple),
            )
        }

        // 手机型号(如 "Galaxy S24")属于弱指纹,不作为依据,避免误报
        return null
    }

    private fun matchRule(lowerName: String, rules: List<NameRule>): NameRule? =
        rules.firstOrNull { rule ->
            if (rule.wholeWord) containsWholeWord(lowerName, rule.keyword)
            else lowerName.contains(rule.keyword)
        }

    /**
     * 短词按字母边界匹配,避免 "Springfield" / "Strings" 这类普通单词误命中 "ring"。
     * 数字与连字符视为边界,因此 "Ring-123" 仍可命中。
     */
    private fun containsWholeWord(haystack: String, word: String): Boolean =
        Regex("(?<![a-z])${Regex.escape(word)}(?![a-z])").containsMatchIn(haystack)
}
