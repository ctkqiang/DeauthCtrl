package xin.ctkqiang.deauthctrl.manager

/**
 * nmap MAC 前缀注册表解析器(纯逻辑核,无 Android 依赖)
 *
 * 数据源:`assets/ble/nmap-mac-prefixes.txt`(Nmap 项目生成,
 * 原始数据来自 IEEE 注册局 OUI 数据库)。同一厂商可能持有多个前缀,
 * 前缀长度分为三档:
 * - 6 位十六进制:OUI(组织唯一标识,最常见)
 * - 7 位十六进制:MA-M(中型块)
 * - 9 位十六进制:MA-S(小型块,36 位前缀)
 *
 * ## 最长前缀优先
 *
 * 同一个 MAC 地址可能同时命中一个 6 位前缀和一个更长的 9 位前缀,
 * 且两者属于不同厂商。长度越长的前缀分配越晚、越精确,
 * 因此查找顺序固定为 9 → 7 → 6,先命中即返回。
 *
 * ## 能力边界(UI 与免责声明必须一致)
 *
 * 厂商名来自公开 OUI 数据库,只表示网卡芯片的注册厂商,
 * 不代表设备品牌(成品设备可能贴牌销售,或由代工厂注册 MAC)。
 * UI 不得把 vendor 名当作"设备制造商"断言。
 */
object VendorPrefixes {

    /** 一条解析后的注册表行:前缀(大写十六进制,无分隔符)+ 厂商名 */
    data class Entry(val prefix: String, val vendor: String)

    /**
     * 解析一行注册表文本。
     *
     * @param line 原始行,允许 CRLF
     * @return 合法条目;注释(`#`)、空行、前缀长度不属于 {6,7,9}、
     *         前缀含非十六进制字符、缺少厂商名的行返回 null。
     *         厂商名保留内部空格(按空白切分,limit=2)。
     */
    fun parseLine(line: String): Entry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        if (parts.size < 2) return null
        val rawPrefix = parts[0]
        val vendor = parts[1].trim()
        if (vendor.isEmpty()) return null
        val prefix = normalizePrefix(rawPrefix) ?: return null
        return Entry(prefix, vendor)
    }

    /**
     * 前缀规范化:剥掉 `:` / `-` / 空格等常见分隔符,转大写,
     * 校验每一位都是十六进制且长度属于 {6,7,9},否则返回 null。
     */
    fun normalizePrefix(raw: String): String? {
        val compact = raw.replace(":", "").replace("-", "").replace(" ", "").uppercase()
        if (compact.length !in intArrayOf(6, 7, 9)) return null
        if (!compact.all { it in "0123456789ABCDEF" }) return null
        return compact
    }

    /**
     * 把一个 MAC 地址归一化成无分隔符的大写十六进制串,
     * 供 [lookup] 做前缀匹配。非法输入返回 null(不抛出,
     * 调用方把 null 当"未知厂商"处理即可)。
     */
    fun normalizeMac(raw: String): String? {
        val compact = raw.replace(":", "").replace("-", "").replace(".", "")
            .replace(" ", "").uppercase()
        if (compact.length < 6 || !compact.all { it in "0123456789ABCDEF" }) return null
        return compact
    }

    /**
     * 把解析好的条目打成 O(1) 查找索引(前缀 -> 厂商名)。
     * 扫描器热路径(每个 BLE 广播回调都可能查一次)必须用索引,
     * 禁止在列表上线性扫。
     */
    fun buildIndex(entries: List<Entry>): Map<String, String> =
        entries.associate { it.prefix to it.vendor }

    /**
     * 按 9 → 7 → 6 的优先级在 [index] 里查找 MAC 对应的厂商。
     *
     * @param index [buildIndex] 产出的索引
     * @param mac 任意常见拼写的 MAC 地址(`AA:BB:CC:DD:EE:FF` / `aabb.ccdd.eeff` 等)
     * @return 厂商名;MAC 非法或表中无匹配时返回 null
     */
    fun lookup(index: Map<String, String>, mac: String): String? {
        val compact = normalizeMac(mac) ?: return null
        // 按 9 → 7 → 6 依次尝试,最长前缀优先。
        for (len in intArrayOf(9, 7, 6)) {
            if (compact.length < len) continue
            index[compact.substring(0, len)]?.let { return it }
        }
        return null
    }
}
