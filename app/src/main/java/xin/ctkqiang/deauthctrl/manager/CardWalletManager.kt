package xin.ctkqiang.deauthctrl.manager

import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 钱包中的一张虚拟卡 */
data class VirtualCard(
    val id: String,
    val name: String,
    val dataHex: String, // 卡片自定义数据（读卡器 GET DATA 时返回）
    val createdMs: Long,
)

/**
 * NFC 卡模拟钱包 —— Host Card Emulation
 *
 * 手机作为 NFC 卡被读卡器读取：存储多张虚拟卡，选择一张作为活跃卡，
 * [CardEmulationService]（HostApduService）按活跃卡数据应答 ISO 14443-4
 * APDU 指令。
 *
 * ## 诚实的能力边界
 * - HCE 只能模拟 **ISO-DEP (ISO 14443-4) APDU 卡** —— 适用于自定义
 *   读卡器与安全研究场景（SELECT AID / GET DATA 应答）；
 * - **无法模拟** MIFARE Classic 门禁卡 / 普通 UID 卡（需要厂商烧写的
 *   密钥与安全元件 eSE，普通手机没有）；
 * - **无法模拟**银行卡/公交卡（需要发卡行密钥体系）。
 */
class CardWalletManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("deauthctrl_prefs", Context.MODE_PRIVATE)

    private val _cards = MutableStateFlow(loadCards())
    val cards: StateFlow<List<VirtualCard>> = _cards

    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE_CARD, null))
    val activeId: StateFlow<String?> = _activeId

    /** 添加一张卡 */
    fun addCard(name: String, dataHex: String): VirtualCard {
        val card = VirtualCard(
            id = java.util.UUID.randomUUID().toString().take(8),
            name = name.ifBlank { L10n.get(R.string.mgr_card_default_name, _cards.value.size + 1) },
            dataHex = dataHex.ifBlank { "" },
            createdMs = System.currentTimeMillis(),
        )
        _cards.value = _cards.value + card
        persist()
        return card
    }

    fun removeCard(id: String) {
        _cards.value = _cards.value.filterNot { it.id == id }
        if (_activeId.value == id) _activeId.value = null
        persist()
    }

    /** 设为活跃卡 —— HCE 服务即时读取 */
    fun setActive(id: String?) {
        _activeId.value = id
        prefs.edit().putString(KEY_ACTIVE_CARD, id).apply()
    }

    /** HCE 服务调用：取活跃卡 */
    fun activeCard(): VirtualCard? {
        val id = prefs.getString(KEY_ACTIVE_CARD, null) ?: return null
        return loadCards().firstOrNull { it.id == id }
    }

    private fun loadCards(): List<VirtualCard> {
        val json = prefs.getString(KEY_CARDS, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VirtualCard(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    dataHex = o.optString("data"),
                    createdMs = o.optLong("created"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persist() {
        val arr = JSONArray()
        _cards.value.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("data", c.dataHex)
                put("created", c.createdMs)
            })
        }
        prefs.edit().putString(KEY_CARDS, arr.toString()).apply()
        // 活跃卡即时写入,供 HCE 服务进程内读取
        prefs.edit().putString(KEY_ACTIVE_CARD, _activeId.value).apply()
    }

    companion object {
        const val CARD_AID = "F0010203040506"
        private const val KEY_CARDS = "wallet_cards"
        private const val KEY_ACTIVE_CARD = "wallet_active_card"
    }
}
