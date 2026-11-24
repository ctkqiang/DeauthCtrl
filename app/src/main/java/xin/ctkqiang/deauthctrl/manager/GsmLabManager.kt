package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * GSM 实验室 —— 蜂窝情报:SIM / 服务小区 / 邻区 / 信号与 TA 测距。
 * 全部数据来自 TelephonyManager,无网络依赖;频段换算走 [GsmMath]。
 */
class GsmLabManager(private val context: Context) {

    data class SimInfo(
        val operator: String,
        val mccMnc: String,
        val networkType: String,
        val roaming: Boolean,
    )

    data class CellEntry(
        val type: String,
        val id: String,
        val band: String,
        val arfcn: Int,
        val dbm: Int,
        val asu: Int,
        val rsrp: Int? = null,
        val rsrq: Int? = null,
        val snr: Int? = null,
        val ta: Int? = null,
        val taDistM: Int? = null,
        val registered: Boolean = false,
    )

    data class State(
        val sim: SimInfo = SimInfo("--", "--", "--", false),
        val serving: CellEntry? = null,
        val neighbors: List<CellEntry> = emptyList(),
        val permissionMissing: Boolean = false,
    )

    private val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private fun networkTypeLabel(): String {
        val t = tm ?: return "--"
        val n = runCatching { t.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
            .let { if (it == TelephonyManager.NETWORK_TYPE_UNKNOWN) runCatching { t.voiceNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN) else it }
        return when (n) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> "3G HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2.5G EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
            TelephonyManager.NETWORK_TYPE_GSM -> "2G GSM"
            TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B -> "CDMA"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
            else -> "UNKNOWN"
        }
    }

    @Suppress("DEPRECATION")
    fun refresh() {
        val t = tm
        if (t == null) {
            _state.update { it.copy(permissionMissing = true) }
            return
        }
        val sim = runCatching {
            SimInfo(
                operator = t.simOperatorName ?: t.networkOperatorName ?: "--",
                mccMnc = (t.simOperator ?: "--").ifBlank { "--" },
                networkType = networkTypeLabel(),
                roaming = t.isNetworkRoaming,
            )
        }.getOrElse { SimInfo("--", "--", "--", false) }

        val cells = runCatching { t.allCellInfo ?: emptyList<CellInfo>() }.getOrElse { emptyList() }
        val entries = cells.mapNotNull { toEntry(it) }
        _state.update {
            it.copy(
                sim = sim,
                serving = entries.firstOrNull { e -> e.registered } ?: entries.firstOrNull(),
                neighbors = entries,
            )
        }
    }

    private fun toEntry(info: CellInfo): CellEntry? = runCatching {
        when (info) {
            is CellInfoLte -> {
                val id = info.cellIdentity as CellIdentityLte
                val sig = info.cellSignalStrength as CellSignalStrengthLte
                val earfcn = id.earfcn
                val band = GsmMath.lteBandOf(earfcn)?.let { "B${it.first.band} ${it.first.name}" } ?: "EARFCN $earfcn"
                val ta = if (Build.VERSION.SDK_INT >= 30) runCatching { sig.timingAdvance }.getOrNull() else null
                CellEntry(
                    type = "4G LTE", id = "CID ${id.ci} · TAC ${id.tac}", band = band, arfcn = earfcn,
                    dbm = sig.dbm, asu = sig.asuLevel, rsrp = sig.rsrp, rsrq = sig.rsrq, snr = sig.rssnr,
                    ta = ta, taDistM = ta?.let { GsmMath.taDistanceMeters(it, lte = true) },
                    registered = info.isRegistered,
                )
            }
            is CellInfoGsm -> {
                val id = info.cellIdentity as CellIdentityGsm
                val sig = info.cellSignalStrength as CellSignalStrengthGsm
                val arfcn = id.arfcn
                val band = GsmMath.gsmBandOf(arfcn)?.display ?: "ARFCN $arfcn"
                val ta = if (Build.VERSION.SDK_INT >= 30) runCatching { sig.timingAdvance }.getOrNull() else null
                CellEntry(
                    type = "2G GSM", id = "CID ${id.cid} · LAC ${id.lac}", band = band, arfcn = arfcn,
                    dbm = sig.dbm, asu = sig.asuLevel,
                    ta = ta, taDistM = ta?.let { GsmMath.taDistanceMeters(it, lte = false) },
                    registered = info.isRegistered,
                )
            }
            is CellInfoWcdma -> {
                val id = info.cellIdentity as CellIdentityWcdma
                val sig = info.cellSignalStrength as CellSignalStrengthWcdma
                CellEntry(
                    type = "3G WCDMA", id = "CID ${id.cid} · LAC ${id.lac}", band = "UARFCN ${id.uarfcn}",
                    arfcn = id.uarfcn, dbm = sig.dbm, asu = sig.asuLevel, registered = info.isRegistered,
                )
            }
            is CellInfoNr -> {
                if (Build.VERSION.SDK_INT < 29) null
                else {
                    val id = info.cellIdentity as CellIdentityNr
                    val sig = info.cellSignalStrength as CellSignalStrengthNr
                    CellEntry(
                        type = "5G NR", id = "NCI ${id.nci} · TAC ${id.tac}", band = "NR-ARFCN ${id.nrarfcn}",
                        arfcn = id.nrarfcn, dbm = sig.dbm, asu = sig.asuLevel,
                        rsrp = sig.csiRsrp, rsrq = sig.csiRsrq, snr = sig.csiSinr,
                        registered = info.isRegistered,
                    )
                }
            }
            else -> null
        }
    }.getOrNull()
}
