package xin.ctkqiang.deauthctrl.manager

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 通缉令 —— 完全独立的公开执法通缉数据功能,与情报中心零耦合
 *
 * ## 数据源(官方公开、无认证)
 * - FBI Wanted API(美国,JSON)
 * - Interpol Red Notices(全球 194 成员国,JSON —— 部分地区被 Akamai
 *   边缘封锁,返回 403;换网络后自动恢复,状态如实上报 UI)
 * - ICE Most Wanted(美国国土安全调查局,HTML 卡片解析)
 * - NCA Most Wanted(英国国家犯罪局,HTML 卡片解析)
 * - HKPF 通缉悬赏(香港警务处,官方 XML)
 * - PDRM 通缉公告(马来西亚皇家警察,官方搜索 API)
 * - OpenSanctions Wanted 聚合(开放数据项目镜像官方名单,NDJSON:
 *   EU Europol / 西班牙 / 荷兰 / 德国 BKA / 香港廉署 / 美特勤局 / 菲律宾,
 *   每条带官方 sourceUrl)
 *
 * ## 地理打点
 * 国籍/出生地/描述文本匹配内置国家表得到经纬度,
 * 供世界地图绘制通缉对象关联国家圆点 —— 无任何地理编码 API。
 * 国家表与情报中心各自持有(独立功能不做共享依赖)。
 */
class WantedManager {

    data class WantedSource(val id: String, val name: String, val url: String, val kind: String)

    data class WantedItem(
        val id: String,
        val sourceId: String,
        val title: String,
        val link: String,
        val desc: String,
        val timeMs: Long,
        val lat: Double? = null,
        val lon: Double? = null,
        val locName: String? = null,
    )

    /** 单源拉取结果快照 —— UI 如实呈现哪个源失败、失败原因(如 403) */
    data class SourcePull(val sourceId: String, val ok: Boolean, val count: Int, val error: String? = null)

    companion object {
        val SOURCES = listOf(
            WantedSource("fbi", "FBI", "https://api.fbi.gov/@wanted?pageSize=50", "json"),
            WantedSource("interpol", "INTERPOL", "https://ws-public.interpol.int/notices/v1/red?resultPerPage=50", "json"),
            WantedSource("ice", "ICE", "https://www.ice.gov/most-wanted", "html"),
            WantedSource("nca", "NCA", "https://www.nationalcrimeagency.gov.uk/most-wanted-search", "html"),
            WantedSource("hkgov", "HKPF", "https://www.police.gov.hk/info/appeals_public/wanted_persons/w_sc.xml", "hkxml"),
            WantedSource("pdrm", "PDRM", "https://www.rmp.gov.my/api/public/search?q=dikehendaki&source=content&type=article&per_page=50", "pdrm"),
            WantedSource("os", "OS", "https://data.opensanctions.org/datasets/latest/", "ftm"),
        )

        /** OpenSanctions 通缉数据集(每个 ~100KB NDJSON,官方名单镜像,带 sourceUrl;第三项=国家默认坐标) */
        private val OS_DATASETS = listOf(
            Triple("eu_europol_wanted", "EUROPOL", null),
            Triple("es_cnp_wanted", "ES", 40.46 to -3.75),
            Triple("nl_most_wanted", "NL", 52.13 to 5.29),
            Triple("de_bka_wanted", "DE", 51.17 to 10.45),
            Triple("hk_icac_wanted", "HK-ICAC", 22.32 to 114.17),
            Triple("us_ss_wanted", "US-SS", 38.9 to -77.04),
            Triple("ph_most_wanted", "PH", 12.88 to 121.77),
        )
    }

    /** 并行拉取全部源,合并去重按时间倒序;单源失败不拖垮整体,状态单独上报 */
    suspend fun refresh(): Pair<List<WantedItem>, List<SourcePull>> = withContext(Dispatchers.IO) {
        val perSource = coroutineScope {
            SOURCES.map { src ->
                async {
                    runCatching { fetchSource(src) }.fold(
                        onSuccess = { Triple(src, it, null) },
                        onFailure = { Triple(src, emptyList(), it.message?.take(80) ?: "failed") },
                    )
                }
            }.awaitAll()
        }
        val items = perSource.flatMap { it.second }.distinctBy { it.id }.sortedByDescending { it.timeMs }
        items to perSource.map { (src, list, err) -> SourcePull(src.id, err == null, list.size, err) }
    }

    /** 单源拉取:按 kind 分发解析 */
    private fun fetchSource(src: WantedSource): List<WantedItem> = when (src.kind) {
        "json" -> if (src.id == "fbi") parseFbi(src) else parseInterpol(src)
        "html" -> if (src.id == "ice") parseIce(src) else parseNca(src)
        "hkxml" -> parseHk(src)
        "pdrm" -> parsePdrm(src)
        else -> parseFtm(src)
    }

    /**
     * FBI Wanted API:title / nationality / place_of_birth / reward_text,
     * details 字段带官方详情页链接(部分条目为站内相对路径,拼接官网前缀)。
     */
    private fun parseFbi(src: WantedSource): List<WantedItem> {
        val raw = httpGet(src.url) ?: return emptyList()
        val out = mutableListOf<WantedItem>()
        runCatching {
            val arr = JSONObject(raw).optJSONArray("items") ?: return@runCatching
            for (i in 0 until minOf(arr.length(), 50)) {
                val o = arr.getJSONObject(i)
                val t = o.optString("title", "").ifBlank { "Wanted" }
                val nat = o.optJSONArray("nationality")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList()
                val pob = o.optString("place_of_birth", "")
                val reward = o.optString("reward_text", "")
                val desc = listOf(
                    o.optString("description", "").take(200),
                    if (nat.isNotEmpty()) "国籍: ${nat.joinToString(", ")}" else "",
                    if (pob.isNotBlank()) "出生地: $pob" else "",
                    if (reward.isNotBlank()) "赏金: $reward" else "",
                ).filter { it.isNotBlank() }.joinToString(" · ")
                val details = o.optString("details", "")
                val link = if (details.startsWith("http")) details
                else "https://www.fbi.gov/wanted/" + (o.optString("path", "").ifBlank { o.optString("uid", "") })
                val time = parseTime(o.optString("publication", "").ifBlank { o.optString("modified", "") })
                buildItem(src, "FBI 通缉 · $t".take(200), link, desc, time)?.let(out::add)
            }
        }
        return out
    }

    /**
     * Interpol Red Notices API:_embedded.notices 数组,
     * 详情链接取 _links.self.href(官方页面)。
     */
    private fun parseInterpol(src: WantedSource): List<WantedItem> {
        val raw = httpGet(src.url, browserHeaders = true) ?: return emptyList()
        val out = mutableListOf<WantedItem>()
        runCatching {
            val notices = JSONObject(raw).optJSONObject("_embedded")?.optJSONArray("notices") ?: return@runCatching
            for (i in 0 until minOf(notices.length(), 50)) {
                val o = notices.getJSONObject(i)
                val name = (o.optString("forename", "") + " " + o.optString("name", "")).trim()
                if (name.isBlank()) continue
                val nat = o.optJSONArray("nationalities")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList()
                val pob = o.optString("place_of_birth", "")
                val desc = listOf(
                    if (nat.isNotEmpty()) "国籍: ${nat.joinToString(", ")}" else "",
                    if (pob.isNotBlank()) "出生地: $pob" else "",
                    "生日: " + o.optString("date_of_birth", ""),
                ).filter { it.isNotBlank() }.joinToString(" · ")
                val link = o.optJSONObject("_links")?.optJSONObject("self")?.optString("href", "") ?: ""
                if (link.isEmpty()) continue
                buildItem(src, "Red Notice · $name".take(200), link, desc, System.currentTimeMillis())?.let(out::add)
            }
        }
        return out
    }

    /** ICE 卡片正则:链接 / 姓名 / 简介(站点 HTML 的 <p> 常不闭合,兼容 </div> 结尾) / 罪名 */
    private val ICE_CARD = Regex(
        """usa-card[^>]*>\s*<a href="(https://www\.ice\.gov/most-wanted/[^"]+)"[^>]*>.*?<h2[^>]*>\s*([^<]+?)\s*</h2>(?:.*?<p>\s*([^<]+?)\s*(?:</p>|</div>))?(?:.*?<span class="usa-tag">\s*(?:Wanted for:\s*)?([^<]+?)\s*</span>)?""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** NCA 卡片正则:链接 / 姓名 / 简介 */
    private val NCA_CARD = Regex(
        """<div class="item [^"]*"[^>]*>.*?<a href="(/most-wanted/[^"]+)">.*?<h3>([^<]+)</h3>.*?<p>([^<]+?)</p>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** ICE Most Wanted:服务端渲染卡片列表;按 <li 分块逐卡解析,避免缺闭合标签时跨卡吞并 */
    private fun parseIce(src: WantedSource): List<WantedItem> {
        val html = httpGet(src.url) ?: return emptyList()
        return html.split("<li").mapNotNull { chunk ->
            ICE_CARD.find("<li$chunk")?.let { m ->
                val name = m.groupValues[2].cleanName()
                if (name.isEmpty()) null
                else {
                    val charges = m.groupValues[4].trim().ifBlank { "" }
                    val desc = listOf(
                        m.groupValues[3].trim().take(220),
                        if (charges.isNotEmpty()) "罪名: $charges" else "",
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    buildItem(src, "ICE 通缉 · $name".take(200), m.groupValues[1], desc, 0L)
                }
            }
        }.toList()
    }

    /** NCA Most Wanted:搜索页服务端渲染,正则抽取姓名/简介 */
    private fun parseNca(src: WantedSource): List<WantedItem> {
        val html = httpGet(src.url) ?: return emptyList()
        return NCA_CARD.findAll(html).mapNotNull { m ->
            val name = m.groupValues[2].cleanName()
            if (name.isEmpty()) return@mapNotNull null
            buildItem(
                src,
                "NCA 通缉 · $name".take(200),
                "https://www.nationalcrimeagency.gov.uk" + m.groupValues[1],
                m.groupValues[3].trim().take(220),
                0L,
            )
        }.toList()
    }

    /** HTML 文本整理:剥标签、压空白、去省略号 */
    private fun String.cleanName(): String =
        replace("…", "").replace(Regex("\\s+"), " ").trim().trimEnd(',')

    /**
     * 香港警务处通缉悬赏官方 XML(w_sc.xml):案件含姓名列表 / 悬赏 /
     * 案情描述 / 详情链接 / 修订日期,简体中文。
     */
    private fun parseHk(src: WantedSource): List<WantedItem> {
        val xml = httpGet(src.url) ?: return emptyList()
        val out = mutableListOf<WantedItem>()
        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(xml.reader())
            var inCase = false
            var caseId = ""
            var caseTitle = ""
            var reward = ""
            var other = ""
            var urlSuffix = ""
            var revised = ""
            val names = mutableListOf<String>()
            var text: String? = null
            fun flushCase() {
                if (caseId.isEmpty()) return
                val nameStr = names.filter { it.isNotBlank() }.distinct().joinToString(" / ")
                val title = listOf("HK 通缉", nameStr, caseTitle).filter { it.isNotBlank() }.joinToString(" · ").take(200)
                val desc = listOf(
                    if (caseTitle.isNotBlank()) "案件: $caseTitle" else "",
                    if (reward.isNotBlank()) reward else "",
                    other.stripHtml().take(220),
                ).filter { it.isNotBlank() }.joinToString(" · ")
                val link = "https://www.police.gov.hk/ppp_sc/06_appeals_public/wanted/" +
                    urlSuffix.ifBlank { "detail.html?id=$caseId" }
                buildItem(src, title, link, desc, parseTime(revised))?.let(out::add)
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (event) {
                    XmlPullParser.START_TAG -> when {
                        name == "case" -> {
                            inCase = true
                            caseId = parser.getAttributeValue(null, "id").orEmpty()
                            caseTitle = parser.getAttributeValue(null, "title").orEmpty()
                            names.clear(); reward = ""; other = ""; urlSuffix = ""; revised = ""
                        }
                        inCase && (name == "name" || name == "reward" || name == "other" ||
                            name == "url" || name == "revisedDate") -> text = ""
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> text?.let { text = it + parser.text }
                    XmlPullParser.END_TAG -> when {
                        name == "case" && inCase -> { flushCase(); inCase = false }
                        inCase && name == "name" -> names.add(text.orEmpty().trim())
                        inCase && name == "reward" -> reward = text.orEmpty().trim()
                        inCase && name == "other" -> other = text.orEmpty()
                        inCase && name == "url" -> urlSuffix = text.orEmpty().trim()
                        inCase && name == "revisedDate" -> revised = text.orEmpty().trim()
                    }
                }
                event = parser.next()
            }
        }
        return out
    }

    /**
     * OpenSanctions 通缉数据集(NDJSON 每行一个实体):
     * caption=姓名,properties.sourceUrl=官方页面,notes=描述。
     */
    private fun parseFtm(src: WantedSource): List<WantedItem> {
        val out = mutableListOf<WantedItem>()
        for ((ds, label, fallback) in OS_DATASETS) {
            runCatching {
                val raw = httpGet(src.url + ds + "/entities.ftm.json") ?: return@runCatching
                raw.lineSequence().take(60).forEach { line ->
                    runCatching {
                        val o = JSONObject(line)
                        val caption = o.optString("caption", "").ifBlank { return@runCatching }
                        val p = o.optJSONObject("properties") ?: return@runCatching
                        // 官方页面优先;无 sourceUrl 的数据集(如菲律宾,来源为政府 PDF)
                        // 退回 OpenSanctions 实体详情页
                        val link = p.optJSONArray("sourceUrl")?.optString(0).orEmpty().ifBlank {
                            "https://www.opensanctions.org/entities/${o.optString("id", "")}/"
                        }
                        if (link.isEmpty()) return@runCatching
                        val nat = p.optJSONArray("nationality")?.let { a ->
                            (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
                        } ?: emptyList()
                        val notes = p.optJSONArray("notes")?.optString(0) ?: ""
                        val desc = listOf(
                            if (nat.isNotEmpty()) "国籍: ${nat.joinToString(", ")}" else "",
                            notes.take(200),
                        ).filter { it.isNotBlank() }.joinToString(" · ")
                        buildItem(
                            src,
                            "$label 通缉 · $caption".take(200),
                            link,
                            desc,
                            parseTime(o.optString("last_seen", "")),
                            fallback?.first,
                            fallback?.second,
                        )?.let(out::add)
                    }
                }
            }
        }
        return out.distinctBy { it.id }
    }

    /** HTML 文本整理:剥标签、压空白(通缉描述文本) */
    private fun String.stripHtml(): String =
        replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * PDRM(马来西亚皇家警察)官方搜索 API:q=dikehendaki 的通缉相关公告。
     * 官网前端是 SPA,但后端搜索接口直接返回 JSON(标题/摘要/链接/时间)。
     * 全部条目地理打点马来西亚。
     */
    private fun parsePdrm(src: WantedSource): List<WantedItem> {
        val raw = httpGet(src.url) ?: return emptyList()
        val out = mutableListOf<WantedItem>()
        runCatching {
            val items = JSONObject(raw).optJSONArray("items") ?: return@runCatching
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                val title = o.optString("title", "").ifBlank { continue }
                val link = o.optString("url", "")
                if (link.isEmpty()) continue
                buildItem(
                    src,
                    "PDRM · $title".take(200),
                    link,
                    o.optString("excerpt", "").take(220),
                    parseTime(o.optString("indexed_at", "")),
                    4.21,
                    101.98,
                )?.let(out::add)
            }
        }
        return out
    }

    /** HTTP GET 文本;失败抛异常由调用方容错(连接 8s / 读取 12s) */
    private fun httpGet(url: String, browserHeaders: Boolean = false): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 12_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) DeauthCtrl/1.0")
            if (browserHeaders) {
                conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                conn.setRequestProperty("Referer", "https://www.interpol.int/")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** 组装条目:标题+描述文本做国家地理打点;无命中时用数据集默认国家坐标 */
    private fun buildItem(
        src: WantedSource,
        title: String,
        link: String,
        desc: String,
        timeMs: Long,
        fallbackLat: Double? = null,
        fallbackLon: Double? = null,
    ): WantedItem? {
        if (link.isEmpty()) return null
        val (lat, lon, loc) = locate(title + " " + desc)
        val id = "w${(src.id + "|" + link).hashCode()}"
        return WantedItem(
            id = id,
            sourceId = src.id,
            title = title,
            link = link,
            desc = desc,
            timeMs = if (timeMs > 0) timeMs else System.currentTimeMillis(),
            lat = lat ?: fallbackLat,
            lon = lon ?: fallbackLon,
            locName = loc,
        )
    }

    /** 时间解析:ISO8601(FBI publication;其余源无时间字段则用当前时间) */
    private fun parseTime(raw: String): Long {
        val s = raw.trim()
        if (s.isEmpty()) return 0L
        listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
        ).forEach { fmt ->
            runCatching {
                return SimpleDateFormat(fmt, Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(s)?.time ?: 0L
            }
        }
        return 0L
    }

    /**
     * 内置国家/形容词别名 → 经纬度表(零 API 地理打点)。
     * 词边界正则匹配:国籍字段是 "Russian Federation" 而非 "Russia" 也能命中。
     */
    private fun locate(text: String): Triple<Double?, Double?, String?> {
        val lower = text.lowercase()
        var best: Pair<String, Pair<Double, Double>>? = null
        for ((name, c) in COUNTRY_TABLE) {
            val pattern = Regex("\\b" + Regex.escape(name) + "\\b")
            if (pattern.containsMatchIn(lower)) {
                if (best == null || name.length > best!!.first.length) best = name to c
            }
        }
        return if (best == null) Triple(null, null, null)
        else Triple(best!!.second.first, best!!.second.second, best!!.first)
    }

    private val COUNTRY_TABLE = mapOf(
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
        // ── 中美洲/加勒比(ICE 通缉对象常见国籍) ──
        "honduras" to (15.2 to -86.24), "el salvador" to (13.79 to -88.9), "guatemala" to (15.78 to -90.23),
        "belize" to (17.19 to -88.5), "nicaragua" to (12.87 to -85.21), "dominican republic" to (18.74 to -70.16),
        "jamaica" to (18.11 to -77.3), "haiti" to (18.97 to -72.28), "trinidad" to (10.69 to -61.22),
        "guyana" to (4.86 to -58.93), "suriname" to (3.92 to -56.03),
        // ── 形容词/别名(国籍字段常用形式,与名词同坐标) ──
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
        "austrian" to (47.52 to 14.55), "hungarian" to (47.16 to 19.5), "romanian" to (45.94 to 24.97),
        "serbian" to (44.02 to 21.01), "croatian" to (45.1 to 15.2), "bulgarian" to (42.73 to 25.49),
        "pakistani" to (30.38 to 69.35), "afghan" to (33.94 to 67.71), "iraqi" to (33.2 to 43.68),
        "libyan" to (26.34 to 17.27), "sudanese" to (15.5 to 30.22), "ethiopian" to (9.15 to 40.49),
        "somali" to (5.15 to 46.2), "kenyan" to (-0.02 to 37.91), "yemeni" to (15.55 to 48.52),
        "qatari" to (25.35 to 51.18), "emirati" to (23.42 to 53.85), "kuwaiti" to (29.31 to 47.48),
        "jordanian" to (31.24 to 36.77), "moroccan" to (31.79 to -7.09), "algerian" to (28.03 to 1.66),
        "tunisian" to (36.81 to 10.18), "kazakh" to (48.02 to 66.92), "uzbek" to (41.38 to 64.59),
        "belarusian" to (53.71 to 27.95), "moldovan" to (47.41 to 28.37), "georgian" to (42.32 to 43.36),
        "armenian" to (40.07 to 45.04), "azerbaijani" to (40.14 to 47.58),
        "honduran" to (15.2 to -86.24), "salvadoran" to (13.79 to -88.9), "guatemalan" to (15.78 to -90.23),
    )
}
