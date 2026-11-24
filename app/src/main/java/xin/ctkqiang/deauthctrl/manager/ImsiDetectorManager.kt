package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * IMSI 捕捉器监视 —— 轮询服务小区,经 [ImsiRules] 打分并告警。
 * 事件落日志(内存 100 条);高危时 risk 置 HIGH 供 UI 告警。
 */
class ImsiDetectorManager(context: Context) {

    data class State(
        val monitoring: Boolean = false,
        val rat: String = "--",
        val lac: Int = 0,
        val cid: Long = 0,
        val dbm: Int = 0,
        val events: List<String> = emptyList(),
        val risk: ImsiRules.Risk = ImsiRules.Risk.LOW,
        val samples: Int = 0,
    )

    private val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var prev: ImsiRules.CellSample? = null

    private fun ratOf(info: CellInfo): Int = when (info) {
        is CellInfoGsm -> 0
        is CellInfoWcdma -> 1
        is CellInfoLte -> 2
        is CellInfoNr -> 3
        else -> 2
    }

    /** 一次采样:读注册小区,跑规则,追加事件 */
    fun sample() {
        val t = tm ?: return
        val cells = runCatching { t.allCellInfo ?: emptyList<CellInfo>() }.getOrElse { emptyList() }
        val reg = cells.firstOrNull { it.isRegistered } ?: return
        val (lac, cid) = when (reg) {
            is CellInfoGsm -> (reg.cellIdentity as CellIdentityGsm).lac to (reg.cellIdentity as CellIdentityGsm).cid.toLong()
            is CellInfoLte -> (reg.cellIdentity as CellIdentityLte).tac to (reg.cellIdentity as CellIdentityLte).ci.toLong()
            is CellInfoWcdma -> (reg.cellIdentity as CellIdentityWcdma).lac to (reg.cellIdentity as CellIdentityWcdma).cid.toLong()
            is CellInfoNr -> if (Build.VERSION.SDK_INT >= 29) (reg.cellIdentity as CellIdentityNr).tac to (reg.cellIdentity as CellIdentityNr).nci else 0 to 0L
            else -> 0 to 0L
        }
        val dbm = runCatching { (reg.cellSignalStrength as CellSignalStrength).dbm }.getOrDefault(0)
        val cur = ImsiRules.CellSample(ratOf(reg), lac, cid, dbm, System.currentTimeMillis())
        val events = mutableListOf<ImsiRules.Event>()
        prev?.let { p ->
            events += ImsiRules.downgradeEvents(p, cur)
            ImsiRules.lacJumpEvent(p, cur)?.let { events += it }
            ImsiRules.signalSurgeEvent(p, cur)?.let { events += it }
        }
        prev = cur
        val risk = ImsiRules.alertLevel(events)
        _state.update { st ->
            val lines = events.map { "[${it.type}] ${it.detail}" }
            st.copy(
                rat = ImsiRules.ratName(cur.rat),
                lac = cur.lacTac,
                cid = cur.cid,
                dbm = cur.dbm,
                risk = if (risk != ImsiRules.Risk.LOW) risk else st.risk.coerceAtLeast(ImsiRules.Risk.LOW),
                events = (st.events + lines).takeLast(100),
                samples = st.samples + 1,
            )
        }
    }

    fun reset() {
        prev = null
        _state.value = State(monitoring = _state.value.monitoring)
    }
}
