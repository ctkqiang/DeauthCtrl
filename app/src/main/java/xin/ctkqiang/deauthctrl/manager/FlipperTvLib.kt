package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Flipper 万能遥控库解析器 + 按键克隆库
 *
 * tv.ir 来源: Flipper Zero 开源固件(flipperdevices/flipperzero-firmware,
 * applications/main/infrared/resources/infrared/assets/tv.ir, 公开的万能遥控数据库)。
 * 该库为"泛洪"设计: 同名按钮(如 Power)聚合了数百个品牌的候选信号,
 * 按键时依次轮发直到电视响应 —— 本移植保持同一语义。
 */
object FlipperTvLib {

    /** 一个候选红外信号 */
    data class IrButton(
        val name: String,      // Power / Vol_up / Vol_dn / Ch_next / Ch_prev / Mute
        val protocol: String,  // NEC / NECext / Samsung32 / RC5 / SIRC / SIRC20 / RC6 / RC5X / 其他
        val address: Int,
        val command: Int,
    )

    /** 协议 → 本机是否支持发射 */
    val SUPPORTED_PROTOCOLS = setOf("NEC", "NECext", "Samsung32", "RC5", "RC5X", "RC6", "SIRC", "SIRC20")

    /** 解析 tv.ir(输入流会关闭): 按按钮名分组的候选信号表 */
    fun parse(stream: java.io.InputStream): Map<String, List<IrButton>> {
        val result = LinkedHashMap<String, MutableList<IrButton>>()
        var name = ""
        var protocol = ""
        var address = 0
        var command = 0
        stream.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                when {
                    line.startsWith("name:") -> name = line.removePrefix("name:").trim()
                    line.startsWith("protocol:") -> protocol = line.removePrefix("protocol:").trim()
                    line.startsWith("address:") -> address = parseHexBytes(line.removePrefix("address:"))
                    line.startsWith("command:") -> command = parseHexBytes(line.removePrefix("command:"))
                    line == "#" -> {
                        if (name.isNotBlank() && protocol.isNotBlank()) {
                            result.getOrPut(name) { mutableListOf() }
                                .add(IrButton(name, protocol, address, command))
                        }
                        name = ""; protocol = ""; address = 0; command = 0
                    }
                }
            }
        }
        // 收尾(无末尾 # 时)
        if (name.isNotBlank() && protocol.isNotBlank()) {
            result.getOrPut(name) { mutableListOf() }.add(IrButton(name, protocol, address, command))
        }
        return result
    }

    /** "01 DF 00 00" → 按小端字节序折叠为 Int(取前 4 字节) */
    private fun parseHexBytes(s: String): Int {
        val bytes = s.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull(16) }
        var v = 0
        for (i in bytes.indices.take(4)) v = v or ((bytes[i] and 0xFF) shl (8 * i))
        return v
    }

    // ─── 按键克隆库(个人遥控) ───

    /** 一条个人按键记录 */
    data class CloneKey(val name: String, val protocol: String, val address: Int, val command: Int, val rawUs: String)

    /** 一个个人遥控 */
    data class CloneRemote(val name: String, val keys: List<CloneKey>)

    private const val PREFS = "ir_clone_remotes"

    fun loadRemotes(context: Context): List<CloneRemote> = runCatching {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("remotes", "[]") ?: "[]"
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val keysArr = o.getJSONArray("keys")
                val keys = buildList {
                    for (j in 0 until keysArr.length()) {
                        val k = keysArr.getJSONObject(j)
                        add(CloneKey(
                            name = k.getString("name"),
                            protocol = k.getString("protocol"),
                            address = k.getInt("address"),
                            command = k.getInt("command"),
                            rawUs = k.optString("rawUs", ""),
                        ))
                    }
                }
                add(CloneRemote(o.getString("name"), keys))
            }
        }
    }.getOrDefault(emptyList())

    fun saveRemotes(context: Context, remotes: List<CloneRemote>) {
        val arr = JSONArray()
        remotes.forEach { r ->
            val o = JSONObject()
            o.put("name", r.name)
            val keysArr = JSONArray()
            r.keys.forEach { k ->
                val ko = JSONObject()
                ko.put("name", k.name)
                ko.put("protocol", k.protocol)
                ko.put("address", k.address)
                ko.put("command", k.command)
                ko.put("rawUs", k.rawUs)
                keysArr.put(ko)
            }
            o.put("keys", keysArr)
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("remotes", arr.toString()).apply()
    }
}
