package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 情报中心 —— 免 API Key 的公开订阅源聚合
 *
 * ## 数据源(全部公开、无认证,已逐一验证可用)
 * - GitHub Advisory Database(Atom,需 Accept: application/atom+xml)
 * - HackerNews 安全话题(hNrss RSS)
 * - CISA 公告(RSS)+ CISA KEV 已知被利用漏洞清单(JSON,无需 NVD Key)
 * - The Hacker News / BleepingComputer(RSS)
 * - 先知安全社区(Atom,中文)/ FreeBuf(RSS,中文,部分网络可能受限,容错)
 * - 世界局势:Al Jazeera / NPR World(RSS,英文世界新闻)
 *
 * ## 语言纪律
 * 只保留英文与中文;俄语/阿拉伯语/韩语/泰语/印地语等条目按字符
 * 区间检测直接丢弃 —— 不是翻译,不是猜测。
 *
 * ## 地理打点
 * 标题/摘要中匹配内置国家与城市表(约 90 项)得到经纬度,
 * 供世界地图绘制「问题发生地」圆点 —— 无任何地理编码 API。
 */
class IntelFeedManager(private val context: Context) {

    data class FeedSource(
        val id: String,
        val name: String,
        val url: String,
        val category: String,     // "CVE" | "公告" | "新闻" | "中文" | "世界"
        val lang: String,         // "en" | "zh" | "en/zh"
        val kind: String = "rss", // "rss" | "atom" | "kev"
    )

    data class FeedItem(
        val id: String,
        val sourceId: String,
        val title: String,
        val link: String,
        val desc: String,
        val timeMs: Long,
        val lang: String,
        val lat: Double? = null,
        val lon: Double? = null,
        val locName: String? = null,
        val archived: Boolean = false,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("src", sourceId); put("t", title); put("l", link)
            put("d", desc); put("ts", timeMs); put("lang", lang)
            lat?.let { put("lat", it) }; lon?.let { put("lon", it) }
            locName?.let { put("loc", it) }
        }
    }

    companion object {
        val SOURCES = listOf(
            FeedSource("ghsa", "GitHub Advisory DB", "https://github.com/advisories.atom", "CVE", "en", "atom"),
            FeedSource("hn", "HackerNews · security", "https://hnrss.org/newest?q=security", "新闻", "en", "rss"),
            FeedSource("cisa", "CISA Advisories", "https://www.cisa.gov/cybersecurity-advisories/all.xml", "公告", "en", "rss"),
            FeedSource("kev", "CISA KEV · exploited CVEs", "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json", "CVE", "en", "kev"),
            FeedSource("thn", "The Hacker News", "https://feeds.feedburner.com/TheHackersNews", "新闻", "en", "rss"),
            FeedSource("bc", "BleepingComputer", "https://www.bleepingcomputer.com/feed/", "新闻", "en", "rss"),
            FeedSource("krebs", "Krebs on Security", "https://krebsonsecurity.com/feed/", "新闻", "en", "rss"),
            FeedSource("schneier", "Schneier on Security", "https://www.schneier.com/feed/atom/", "新闻", "en", "atom"),
            FeedSource("record", "The Record · RF News", "https://therecord.media/feed/", "新闻", "en", "rss"),
            FeedSource("darkreading", "Dark Reading", "https://www.darkreading.com/rss.xml", "新闻", "en", "rss"),
            FeedSource("secweek", "SecurityWeek", "https://www.securityweek.com/feed/", "新闻", "en", "rss"),
            FeedSource("exploitdb", "Exploit-DB · exploits", "https://www.exploit-db.com/rss.xml", "CVE", "en", "rss"),
            FeedSource("portswigger", "PortSwigger Research", "https://portswigger.net/research/rss", "新闻", "en", "rss"),
            FeedSource("sans", "SANS ISC", "https://isc.sans.edu/rssfeed.xml", "新闻", "en", "rss"),
            FeedSource("securityaffairs", "Security Affairs", "https://securityaffairs.com/feed", "新闻", "en", "rss"),
            FeedSource("rapid7", "Rapid7 Blog", "https://blog.rapid7.com/rss/", "新闻", "en", "rss"),
            FeedSource("talos", "Cisco Talos", "https://blog.talosintelligence.com/feed/", "新闻", "en", "rss"),
            FeedSource("malwarebytes", "Malwarebytes", "https://blog.malwarebytes.com/feed/", "新闻", "en", "rss"),
            FeedSource("unit42", "Unit 42 · PANW", "https://unit42.paloaltonetworks.com/feed/", "新闻", "en", "rss"),
            FeedSource("register", "The Register · security", "https://www.theregister.com/security/headlines.atom", "新闻", "en", "atom"),
            FeedSource("infosecmag", "Infosecurity Magazine", "https://www.infosecurity-magazine.com/feed/", "新闻", "en", "rss"),
            FeedSource("helpnet", "Help Net Security", "https://www.helpnetsecurity.com/feed/", "新闻", "en", "rss"),
            FeedSource("cso", "CSO Online", "https://www.csoonline.com/feed/", "新闻", "en", "rss"),
            FeedSource("gbhackers", "GBHackers", "https://gbhackers.com/feed/", "新闻", "en", "rss"),
            FeedSource("secblvd", "Security Boulevard", "https://securityboulevard.com/feed/", "新闻", "en", "rss"),
            FeedSource("secrss", "安全内参(中文)", "https://www.secrss.com/rss", "中文", "zh", "rss"),
            FeedSource("xz", "先知社区(中文)", "https://xz.aliyun.com/feed", "中文", "zh", "atom"),
            FeedSource("4hou", "嘶吼(中文)", "https://www.4hou.com/feed", "中文", "zh", "rss"),
            FeedSource("freebuf", "FreeBuf(中文)", "https://www.freebuf.com/feed", "中文", "zh", "rss"),
            FeedSource("anquanke", "安全客(中文)", "https://www.anquanke.com/rss", "中文", "zh", "rss"),
            FeedSource("aj", "Al Jazeera · world", "https://www.aljazeera.com/xml/rss/all.xml", "世界", "en", "rss"),
            FeedSource("npr", "NPR World", "https://feeds.npr.org/1004/rss.xml", "世界", "en", "rss"),
            FeedSource("france24", "France24 · world", "https://www.france24.com/en/rss", "世界", "en", "rss"),
            FeedSource("guardian", "The Guardian · world", "https://www.theguardian.com/world/rss", "世界", "en", "rss"),
            FeedSource("scmp", "SCMP · world", "https://www.scmp.com/rss/91/feed", "世界", "en", "rss"),
            FeedSource("dw", "DW · world", "https://rss.dw.com/rdf/rss-en-all", "世界", "en", "rss"),
        )

        private const val PREFS = "intel_feed"
        private const val KEY_SUBSCRIBED = "subscribed"
        private const val KEY_NOTIFIED = "notified_links"
        private const val KEY_NOTIFY_ON = "notify_enabled"
        private const val KEY_ARCHIVED = "archived"
        private const val NOTIFIED_CAP = 300
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ─── 订阅 ────────────────────────────────────────────────────────────────

    /** 已订阅源 id 集合(默认全部订阅) */
    fun subscribed(): Set<String> =
        prefs.getStringSet(KEY_SUBSCRIBED, SOURCES.map { it.id }.toSet()) ?: SOURCES.map { it.id }.toSet()

    fun setSubscribed(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_SUBSCRIBED, ids).apply()
    }

    fun isNotifyEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_ON, false)

    fun setNotifyEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_ON, on).apply()
    }

    // ─── 拉取 ────────────────────────────────────────────────────────────────

    /** 单源拉取结果快照 —— 订阅页展示每个源的实时状态(正常 ×n / 失败原因) */
    data class SourcePull(val sourceId: String, val ok: Boolean, val count: Int, val error: String? = null)

    /** 拉取全部已订阅源(并行,单源失败不拖垮整体);返回合并排序后的新条目 */
    suspend fun refresh(): List<FeedItem> = refreshDetailed().first

    /**
     * 拉取 + 每源状态:订阅页如实呈现哪个源失败、哪个源有多少条。
     *
     * 性能:10 个源**并行**拉取(原先串行,一个慢源拖垮全部;每源超时
     * 最长 20s,串行最坏 50s+ —— 这就是「拉新闻很慢」的根因)。
     * 超时收紧为连接 8s / 读取 12s。
     */
    suspend fun refreshDetailed(): Pair<List<FeedItem>, List<SourcePull>> = withContext(Dispatchers.IO) {
        val perSource = coroutineScope {
            SOURCES.filter { it.id in subscribed() }.map { src ->
                async {
                    runCatching { fetchSource(src) }.fold(
                        onSuccess = { items ->
                            Triple(SourcePull(src.id, true, items.size), items, src)
                        },
                        onFailure = { e ->
                            Triple(SourcePull(src.id, false, 0, e.message?.take(80)), emptyList(), src)
                        },
                    )
                }
            }.awaitAll()
        }
        val items = perSource.flatMap { it.second }
            .distinctBy { it.id }
            .sortedByDescending { it.timeMs }
        items to perSource.map { it.first }
    }

    /** 单源拉取:按 kind 分发解析;语言过滤 + 地理打点在解析后统一做 */
    private fun fetchSource(src: FeedSource): List<FeedItem> = when (src.kind) {
        "kev" -> parseKev(src)
        "atom" -> parseFeed(src, atom = true)
        else -> parseFeed(src, atom = false)
    }

    /** HTTP GET 文本;失败抛异常由调用方容错(超时收紧:连接 8s / 读取 12s) */
    private fun httpGet(url: String, accept: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 12_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 DeauthCtrl/1.0")
            accept?.let { conn.setRequestProperty("Accept", it) }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** RSS 2.0 / Atom 通用解析(XmlPullParser,按节点名自适应) */
    private fun parseFeed(src: FeedSource, atom: Boolean): List<FeedItem> {
        val xml = httpGet(src.url, if (atom) "application/atom+xml" else "application/rss+xml, text/xml;q=0.9")
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.reader())
        var title = ""
        var link = ""
        var desc = ""
        var time = 0L
        var inItem = false
        var text: String? = null
        val out = mutableListOf<FeedItem>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (event) {
                XmlPullParser.START_TAG -> when {
                    name == "item" || name == "entry" -> inItem = true
                    inItem && name == "link" -> {
                        // Atom <link href="..."/> 是属性而非文本 —— 必须读 href,否则
                        // GitHub Advisories/先知 等 Atom 源 link 恒空、条目全被丢弃
                        // (这就是「显示拉取成功却没有新闻」的根因之一)
                        link = parser.getAttributeValue(null, "href").orEmpty()
                        text = ""
                    }
                    inItem && (name == "title" || name == "description" ||
                        name == "summary" || name == "content" || name == "published" ||
                        name == "updated" || name == "pubDate") -> text = ""
                }
                XmlPullParser.TEXT -> text?.let { text = it + parser.text }
                XmlPullParser.END_TAG -> when {
                    (name == "item" || name == "entry") && inItem -> {
                        val t = title.stripHtml().take(200)
                        val l = link.trim().take(400)
                        if (t.isNotEmpty() && l.isNotEmpty()) {
                            buildItem(src, t, l, desc.stripHtml().take(600), time)?.let(out::add)
                        }
                        inItem = false; title = ""; link = ""; desc = ""; time = 0L
                    }
                    inItem && name == "title" -> title = text.orEmpty()
                    inItem && name == "link" -> if (link.isEmpty()) link = text.orEmpty()
                    inItem && (name == "description" || name == "summary" || name == "content") ->
                        desc = text.orEmpty()
                    inItem && (name == "published" || name == "updated" || name == "pubDate") ->
                        time = parseTime(text.orEmpty())
                }
            }
            event = parser.next()
        }
        return out
    }

    /**
     * 剥离 HTML 标签与常见实体、压空白 —— RSS 的 description 常带
     * <p>/<a> 等标签,标签尖括号会被语言检测当成「非拉丁字符」,
     * 让英文条目 other 占比超阈值被静默丢弃(根因之二)。
     */
    private fun String.stripHtml(): String = replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ").trim()

    /** CISA KEV JSON(已知被利用漏洞):标题=CVE·厂商·产品,链接=官方 NVD 详情页 */
    private fun parseKev(src: FeedSource): List<FeedItem> {
        val json = httpGet(src.url)
        val arr = JSONObject(json).getJSONArray("vulnerabilities")
        val out = mutableListOf<FeedItem>()
        val cap = minOf(arr.length(), 120)
        for (i in 0 until cap) {
            val v = arr.getJSONObject(i)
            val cve = v.optString("cveID", "")
            if (cve.isEmpty()) continue
            val title = "$cve · ${v.optString("vendorProject", "")} ${v.optString("product", "")}".trim()
            val ransomware = if (v.optBoolean("knownRansomwareCampaignUse", false)) " · 勒索组织在野利用" else ""
            val desc = (v.optString("shortDescription", "") + ransomware +
                if (v.has("requiredAction") && v.optString("requiredAction").isNotEmpty())
                    " 处置:${v.optString("requiredAction")}" else "").take(600)
            val time = parseDateOnly(v.optString("dateAdded", ""))
            buildItem(src, title, "https://nvd.nist.gov/vuln/detail/$cve", desc, time)?.let(out::add)
        }
        return out
    }

    /** 组装条目:语言过滤(仅英中)+ 地理打点 */
    private fun buildItem(src: FeedSource, title: String, link: String, desc: String, timeMs: Long): FeedItem? {
        val lang = detectLang(title + " " + desc) ?: return null
        val (lat, lon, loc) = locate(title + " " + desc)
        val id = "i${(src.id + "|" + link).hashCode()}"
        return FeedItem(
            id = id,
            sourceId = src.id,
            title = title,
            link = link,
            desc = desc,
            timeMs = if (timeMs > 0) timeMs else System.currentTimeMillis(),
            lang = lang,
            lat = lat,
            lon = lon,
            locName = loc,
        )
    }

    /** 时间解析:RFC822(pubDate)/ ISO8601(Atom published/updated)/ yyyy-MM-dd(KEV) */
    private fun parseTime(raw: String): Long {
        val s = raw.trim()
        if (s.isEmpty()) return 0L
        listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd",
        ).forEach { fmt ->
            runCatching {
                return SimpleDateFormat(fmt, Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(s)?.time ?: 0L
            }
        }
        return 0L
    }

    private fun parseDateOnly(s: String): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s.trim())?.time ?: 0L
    }.getOrDefault(0L)

    /**
     * 语言判定:中文=CJK 占比显著;含俄/阿/韩/泰/印地/希伯来/希腊字母
     * 的条目直接丢弃;否则英文(标点空格不再误判 —— 旧版把标点算
     * 「其他文字」,标点多的英文条目被静默丢弃,即「拉取成功却看不到新闻」)。
     */
    private fun detectLang(text: String): String? {
        if (text.isEmpty()) return "en"
        var cjk = 0
        var foreign = 0
        var total = 0
        for (ch in text) {
            total++
            when {
                ch in '一'..'鿿' -> cjk++
                // 西里尔/阿拉伯/希伯来/希腊/谚文/泰文/天城文/日文假名
                ch in 'Ѐ'..'ӿ' || ch in '؀'..'ۿ' ||
                    ch in '֐'..'׿' || ch in 'Ͱ'..'Ͽ' ||
                    ch in '가'..'힯' || ch in 'ᄀ'..'ᇿ' ||
                    ch in '฀'..'๿' || ch in 'ऀ'..'ॿ' ||
                    ch in '぀'..'ヿ' -> foreign++
            }
        }
        if (total == 0) return "en"
        if (cjk.toFloat() / total > 0.10f) return "zh"
        if (foreign.toFloat() / total > 0.05f) return null
        return "en"
    }

    /**
     * 内置国家/城市/形容词别名 → 经纬度表(零 API 地理打点)。
     * 词边界正则匹配:标题里是 "Ukrainian forces" 而不是 "Ukraine" ——
     * 新闻几乎全用形容词,只存名词会导致大量事件打不上点(地图看着是空的)。
     * "us" 这类短词靠 \b 避免命中 "just"/"use"。
     */
    private fun locate(text: String): Triple<Double?, Double?, String?> {
        val lower = text.lowercase()
        var best: Pair<String, Pair<Double, Double>>? = null
        for ((name, c) in GEO_TABLE) {
            val pattern = Regex("\\b" + Regex.escape(name) + "\\b")
            if (pattern.containsMatchIn(lower)) {
                if (best == null || name.length > best!!.first.length) best = name to c
            }
        }
        return if (best == null) Triple(null, null, null)
        else Triple(best!!.second.first, best!!.second.second, best!!.first)
    }

    // ─── 通知去重 ────────────────────────────────────────────────────────────

    /** 标记某链接已推送过;返回该链接是否是新条目 */
    @Synchronized
    fun markNotified(link: String): Boolean {
        val cur = prefs.getStringSet(KEY_NOTIFIED, emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = "i${link.hashCode()}"
        if (key in cur) return false
        cur.add(key)
        if (cur.size > NOTIFIED_CAP) {
            val drop = cur.take(cur.size - NOTIFIED_CAP)
            cur.removeAll(drop.toSet())
        }
        prefs.edit().putStringSet(KEY_NOTIFIED, cur).apply()
        return true
    }

    // ─── 本地缓存与存档(离线可读 / 重要情报钉住) ─────────────────────────────

    private val cacheFile get() = File(context.filesDir, "intel_cache.json")

    /** 全部条目落盘缓存(最新 400 条)—— 重启/断网时打开情报页立即有内容 */
    fun saveCache(items: List<FeedItem>) {
        runCatching {
            val arr = JSONArray()
            items.take(400).forEach { arr.put(it.toJson()) }
            cacheFile.writeText(arr.toString())
        }
    }

    /** 读取本地缓存;文件损坏/不存在返回空列表(不报错,联网刷新会重建) */
    fun loadCache(): List<FeedItem> = runCatching {
        val arr = JSONArray(cacheFile.readText())
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    FeedItem(
                        id = o.getString("id"),
                        sourceId = o.getString("src"),
                        title = o.getString("t"),
                        link = o.getString("l"),
                        desc = o.optString("d"),
                        timeMs = o.optLong("ts"),
                        lang = o.optString("lang", "en"),
                        lat = if (o.has("lat")) o.optDouble("lat") else null,
                        lon = if (o.has("lon")) o.optDouble("lon") else null,
                        locName = o.optString("loc").ifBlank { null },
                    ),
                )
            }
        }.sortedByDescending { it.timeMs }
    }.getOrDefault(emptyList())

    /** 已存档条目 id 集合(持久化) */
    fun archivedIds(): Set<String> = prefs.getStringSet(KEY_ARCHIVED, emptySet()) ?: emptySet()

    /** 切换存档状态,返回最新集合 */
    @Synchronized
    fun toggleArchived(id: String): Set<String> {
        val cur = prefs.getStringSet(KEY_ARCHIVED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (id in cur) cur.remove(id) else cur.add(id)
        prefs.edit().putStringSet(KEY_ARCHIVED, cur).apply()
        return cur
    }

    private val GEO_TABLE = mapOf(
        "gaza" to (31.5 to 34.47), "west bank" to (31.9 to 35.2), "jerusalem" to (31.77 to 35.22),
        "tel aviv" to (32.08 to 34.78), "israel" to (31.05 to 34.85), "palestine" to (31.9 to 35.2),
        "lebanon" to (33.85 to 35.86), "beirut" to (33.89 to 35.5), "syria" to (34.8 to 38.99),
        "damascus" to (33.51 to 36.29), "iraq" to (33.2 to 43.68), "baghdad" to (33.31 to 44.36),
        "iran" to (32.43 to 53.69), "tehran" to (35.69 to 51.39), "saudi arabia" to (23.89 to 45.08),
        "riyadh" to (24.71 to 46.68), "qatar" to (25.35 to 51.18), "uae" to (23.42 to 53.85),
        "kuwait" to (29.31 to 47.48), "oman" to (20.6 to 56.09), "bahrain" to (26.07 to 50.55),
        "yemen" to (15.55 to 48.52), "jordan" to (31.24 to 36.77), "egypt" to (26.82 to 30.8),
        "cairo" to (30.04 to 31.24), "libya" to (26.34 to 17.27), "tripoli" to (32.89 to 13.19),
        "tunis" to (36.81 to 10.18), "algeria" to (28.03 to 1.66), "algiers" to (36.75 to 3.06),
        "morocco" to (31.79 to -7.09), "sudan" to (15.5 to 30.22), "khartoum" to (15.5 to 32.56),
        "ethiopia" to (9.15 to 40.49), "somalia" to (5.15 to 46.2), "mogadishu" to (2.05 to 45.32),
        "nigeria" to (9.08 to 8.68), "lagos" to (6.52 to 3.38), "kenya" to (-0.02 to 37.91),
        "nairobi" to (-1.29 to 36.82), "south africa" to (-30.56 to 22.94), "johannesburg" to (-26.2 to 28.05),
        "ukraine" to (48.38 to 31.17), "kyiv" to (50.45 to 30.52), "kiev" to (50.45 to 30.52),
        "donetsk" to (48.02 to 37.8), "donbas" to (48.4 to 38.5), "crimea" to (45.3 to 34.3),
        "russia" to (55.75 to 37.62), "moscow" to (55.75 to 37.62), "belarus" to (53.71 to 27.95),
        "moldova" to (47.41 to 28.37), "georgia" to (42.32 to 43.36), "armenia" to (40.07 to 45.04),
        "azerbaijan" to (40.14 to 47.58), "turkey" to (38.96 to 35.24), "ankara" to (39.93 to 32.86),
        "istanbul" to (41.01 to 28.98), "greece" to (39.07 to 21.82), "poland" to (51.92 to 19.15),
        "warsaw" to (52.23 to 21.01), "germany" to (51.17 to 10.45), "berlin" to (52.52 to 13.4),
        "france" to (46.23 to 2.21), "paris" to (48.86 to 2.35), "united kingdom" to (51.51 to -0.13),
        "britain" to (51.51 to -0.13), "london" to (51.51 to -0.13), "italy" to (41.87 to 12.57),
        "rome" to (41.9 to 12.5), "spain" to (40.46 to -3.75), "madrid" to (40.42 to -3.7),
        "portugal" to (39.4 to -8.22), "netherlands" to (52.13 to 5.29), "belgium" to (50.5 to 4.47),
        "switzerland" to (46.82 to 8.23), "austria" to (47.52 to 14.55), "czech" to (49.82 to 15.47),
        "hungary" to (47.16 to 19.5), "romania" to (45.94 to 24.97), "bulgaria" to (42.73 to 25.49),
        "serbia" to (44.02 to 21.01), "croatia" to (45.1 to 15.2), "sweden" to (60.13 to 18.64),
        "norway" to (60.47 to 8.47), "finland" to (61.92 to 25.75), "denmark" to (56.26 to 9.5),
        "iceland" to (64.96 to -19.02), "ireland" to (53.41 to -8.24), "baltics" to (56.88 to 24.6),
        "estonia" to (58.6 to 25.01), "latvia" to (56.88 to 24.6), "lithuania" to (55.17 to 23.88),
        "united states" to (38.9 to -77.04), "washington" to (38.9 to -77.04), "new york" to (40.71 to -74.01),
        "california" to (36.78 to -119.42), "texas" to (31.97 to -99.9), "florida" to (27.66 to -81.52),
        "canada" to (56.13 to -106.35), "ottawa" to (45.42 to -75.7), "mexico" to (23.63 to -102.55),
        "mexico city" to (19.43 to -99.13), "brazil" to (-14.24 to -51.93), "brasilia" to (-15.79 to -47.88),
        "argentina" to (-38.42 to -63.62), "buenos aires" to (-34.6 to -58.38), "chile" to (-35.68 to -71.54),
        "santiago" to (-33.45 to -70.67), "peru" to (-9.19 to -75.02), "colombia" to (4.57 to -74.3),
        "venezuela" to (6.42 to -66.59), "caracas" to (10.48 to -66.9), "cuba" to (21.52 to -77.78),
        "havana" to (23.11 to -82.37), "china" to (35.86 to 104.2), "beijing" to (39.9 to 116.41),
        "shanghai" to (31.23 to 121.47), "shenzhen" to (22.54 to 114.06), "guangzhou" to (23.13 to 113.26),
        "hong kong" to (22.32 to 114.17), "macau" to (22.2 to 113.55), "taiwan" to (23.7 to 120.96),
        "taipei" to (25.03 to 121.57), "xinjiang" to (41.11 to 85.18), "tibet" to (31.69 to 88.16),
        "japan" to (36.2 to 138.25), "tokyo" to (35.68 to 139.69), "south korea" to (35.91 to 127.77),
        "seoul" to (37.57 to 126.98), "north korea" to (40.34 to 127.51), "pyongyang" to (39.04 to 125.76),
        "india" to (20.59 to 78.96), "new delhi" to (28.61 to 77.21), "pakistan" to (30.38 to 69.35),
        "islamabad" to (33.69 to 73.06), "afghanistan" to (33.94 to 67.71), "kabul" to (34.56 to 69.21),
        "bangladesh" to (23.68 to 90.36), "myanmar" to (21.91 to 95.96), "thailand" to (15.87 to 100.99),
        "bangkok" to (13.76 to 100.5), "vietnam" to (14.06 to 108.28), "hanoi" to (21.03 to 105.85),
        "indonesia" to (-0.79 to 113.92), "jakarta" to (-6.21 to 106.85), "malaysia" to (4.21 to 101.98),
        "kuala lumpur" to (3.14 to 101.69), "singapore" to (1.35 to 103.82), "philippines" to (12.88 to 121.77),
        "manila" to (14.6 to 120.98), "australia" to (-25.27 to 133.78), "canberra" to (-35.28 to 149.13),
        "sydney" to (-33.87 to 151.21), "new zealand" to (-40.9 to 174.89), "wellington" to (-41.29 to 174.78),
        "kazakhstan" to (48.02 to 66.92), "uzbekistan" to (41.38 to 64.59), "kyrgyzstan" to (41.2 to 74.77),
        "tajikistan" to (38.86 to 71.28), "turkmenistan" to (38.97 to 59.56), "mongolia" to (46.86 to 103.82),
        "hawaii" to (19.9 to -155.58), "alaska" to (63.59 to -154.49), "guam" to (13.44 to 144.79),
        "puerto rico" to (18.22 to -66.59), "panama" to (8.54 to -80.78), "costa rica" to (9.75 to -83.75),
        "ecuador" to (-1.83 to -78.18), "bolivia" to (-16.29 to -63.59), "paraguay" to (-23.44 to -58.44),
        "uruguay" to (-32.52 to -55.77), "monaco" to (43.74 to 7.42), "luxembourg" to (49.82 to 6.13),
        "malta" to (35.94 to 14.38), "cyprus" to (35.13 to 33.43), "slovakia" to (48.67 to 19.7),
        "slovenia" to (46.15 to 15.0), "albania" to (41.15 to 20.17), "montenegro" to (42.71 to 19.37),
        "bosnia" to (43.92 to 17.68), "macedonia" to (41.61 to 21.75), "kosovo" to (42.6 to 21.0),
        "reunion" to (-21.12 to 55.54), "mauritius" to (-20.35 to 57.55), "madagascar" to (-18.77 to 46.87),
        "zimbabwe" to (-19.02 to 29.15), "zambia" to (-13.13 to 27.85), "tanzania" to (-6.37 to 34.89),
        "uganda" to (1.37 to 32.29), "rwanda" to (-1.94 to 29.87), "cameroon" to (7.37 to 12.35),
        "senegal" to (14.5 to -14.45), "mali" to (17.57 to -3.99), "niger" to (17.61 to 8.08),
        "chad" to (15.45 to 18.73), "congo" to (-4.04 to 21.76), "angola" to (-11.2 to 17.87),
        "mozambique" to (-18.67 to 35.53), "botswana" to (-22.33 to 24.68), "namibia" to (-22.96 to 18.49),
        "ghana" to (7.95 to -1.02), "ivory coast" to (7.54 to -5.55), "burkina faso" to (12.24 to -1.56),
        "guinea" to (9.95 to -9.7), "sierra leone" to (8.46 to -11.78), "liberia" to (6.43 to -9.43),
        "mauritania" to (21.01 to -10.94), "djibouti" to (11.83 to 42.59), "eritrea" to (15.18 to 39.78),
        // ── 形容词/别名(新闻标题常用形式,与名词同坐标) ──
        "ukrainian" to (48.38 to 31.17), "chinese" to (35.86 to 104.2), "russian" to (55.75 to 37.62),
        "american" to (38.9 to -77.04), "u.s." to (38.9 to -77.04), "usa" to (38.9 to -77.04),
        "british" to (51.51 to -0.13), "french" to (46.23 to 2.21), "german" to (51.17 to 10.45),
        "japanese" to (36.2 to 138.25), "korean" to (35.91 to 127.77), "indian" to (20.59 to 78.96),
        "israeli" to (31.05 to 34.85), "iranian" to (32.43 to 53.69), "saudi" to (23.89 to 45.08),
        "turkish" to (38.96 to 35.24), "syrian" to (34.8 to 38.99), "lebanese" to (33.85 to 35.86),
        "palestinian" to (31.9 to 35.2), "taiwanese" to (23.7 to 120.96), "australian" to (-25.27 to 133.78),
        "canadian" to (56.13 to -106.35), "brazilian" to (-14.24 to -51.93), "mexican" to (23.63 to -102.55),
        "egyptian" to (26.82 to 30.8), "nigerian" to (9.08 to 8.68), "vietnamese" to (14.06 to 108.28),
        "indonesian" to (-0.79 to 113.92), "filipino" to (12.88 to 121.77), "thai" to (15.87 to 100.99),
        "polish" to (51.92 to 19.15), "dutch" to (52.13 to 5.29), "spanish" to (40.46 to -3.75),
        "italian" to (41.87 to 12.57), "swedish" to (60.13 to 18.64), "norwegian" to (60.47 to 8.47),
        "finnish" to (61.92 to 25.75), "greek" to (39.07 to 21.82), "swiss" to (46.82 to 8.23),
        "austrian" to (47.52 to 14.55), "czech" to (49.82 to 15.47), "hungarian" to (47.16 to 19.5),
        "romanian" to (45.94 to 24.97), "serbian" to (44.02 to 21.01), "croatian" to (45.1 to 15.2),
        "bulgarian" to (42.73 to 25.49), "pakistani" to (30.38 to 69.35), "afghan" to (33.94 to 67.71),
        "iraqi" to (33.2 to 43.68), "libyan" to (26.34 to 17.27), "sudanese" to (15.5 to 30.22),
        "ethiopian" to (9.15 to 40.49), "somali" to (5.15 to 46.2), "kenyan" to (-0.02 to 37.91),
        "yemeni" to (15.55 to 48.52), "qatari" to (25.35 to 51.18), "emirati" to (23.42 to 53.85),
        "kuwaiti" to (29.31 to 47.48), "jordanian" to (31.24 to 36.77), "moroccan" to (31.79 to -7.09),
        "algerian" to (28.03 to 1.66), "tunisian" to (36.81 to 10.18), "kazakh" to (48.02 to 66.92),
        "uzbek" to (41.38 to 64.59), "belarusian" to (53.71 to 27.95), "moldovan" to (47.41 to 28.37),
        "georgian" to (42.32 to 43.36), "armenian" to (40.07 to 45.04), "azerbaijani" to (40.14 to 47.58),
        "nordic" to (60.13 to 18.64), "middle east" to (29.3 to 42.6), "southeast asia" to (13.7 to 100.5),
        "central asia" to (41.38 to 64.59), "sub-saharan africa" to (1.0 to 17.0), "latin america" to (-14.0 to -60.0),
    )
}
