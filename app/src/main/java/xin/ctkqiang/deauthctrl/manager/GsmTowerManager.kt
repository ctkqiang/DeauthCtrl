package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 一座蜂窝基站的真实测量值(全部取自系统 CellInfo,读不到的字段为 null,绝不编造) */
data class TowerCell(
    val type: String,          // LTE / NR / WCDMA / GSM
    val isRegistered: Boolean,
    val cid: Long,
    val tacLac: Int,
    val pci: Int? = null,
    val mccMnc: String = "",
    val rsrpDbm: Int? = null,  // LTE/NR 参考信号接收功率
    val rsrqDb: Int? = null,
    val rssiDbm: Int? = null,  // GSM/WCDMA 信号强度
    val asu: Int? = null,
    val signalLevel: Int? = null, // 0-4 系统分级
    val bands: List<Int> = emptyList(), // 频段号(API 30+,LTE/NR)
    val timingAdvance: Int? = null,     // NR 的 TA(API 34+ 部分设备提供;LTE TA 无公开 API)
    val distanceEstimateM: Float? = null, // RSRP 路径损耗粗估(±数百米级,标注非测量)
)

/** 基站页面状态 */
data class GsmTowerState(
    val towers: List<TowerCell> = emptyList(),
    val operator: String = "",
    val networkType: String = "",
    val error: String? = null,
    val updatedAtMs: Long = 0L,
)

/**
 * GSM 基站监视器 —— TelephonyManager.allCellInfo 的真实邻区数据
 *
 * 注册小区 + 全部邻区,按 RSRP 排序;「最近基站」= 信号最强的注册/邻区。
 * 距离没有公开测量通道(LTE TA 不是 public API):用 RSRP 路径损耗模型给出
 * 数百米量级的粗估并明确标注;方位角手机无硬件测量,不提供、不编造。
 */
class GsmTowerManager(private val context: Context) {

    private val _state = MutableStateFlow(GsmTowerState())
    val state: StateFlow<GsmTowerState> = _state

    private val telephony: TelephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    /** 权限是否齐备(精细定位 + 电话状态) */
    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)

    /** 刷新一次真实小区信息 */
    @Suppress("MissingPermission")
    fun refresh() {
        if (!hasPermission) {
            _state.value = GsmTowerState(error = "缺少权限:需要「位置信息」与「电话状态」授权")
            return
        }
        try {
            val cells = telephony.allCellInfo
            if (cells.isNullOrEmpty()) {
                _state.value = GsmTowerState(error = "系统未返回小区信息 —— 飞行模式下无蜂窝信号", operator = operatorName(), networkType = networkTypeName())
                return
            }
            val towers = cells.mapNotNull { c -> parse(c) }.sortedWith(
                compareByDescending<TowerCell> { it.isRegistered }.thenByDescending { it.rsrpDbm ?: it.rssiDbm ?: -999 },
            )
            _state.value = GsmTowerState(
                towers = towers,
                operator = operatorName(),
                networkType = networkTypeName(),
                updatedAtMs = System.currentTimeMillis(),
            )
        } catch (t: Throwable) {
            _state.value = GsmTowerState(error = "读取小区信息失败: ${t.message}", operator = operatorName(), networkType = networkTypeName())
        }
    }

    @Suppress("MissingPermission")
    private fun parse(c: CellInfo): TowerCell? = when (c) {
        is CellInfoLte -> {
            val id = c.cellIdentity as android.telephony.CellIdentityLte
            val sig = c.cellSignalStrength as CellSignalStrengthLte
            TowerCell(
                type = "LTE",
                isRegistered = c.isRegistered,
                cid = id.ci.toLong(),
                tacLac = id.tac,
                pci = id.pci,
                mccMnc = runCatching { id.mccString + "-" + id.mncString }.getOrDefault(""),
                rsrpDbm = sig.rsrp.takeIf { it in -140..-40 },
                rsrqDb = sig.rsrq.takeIf { it in -20..0 },
                rssiDbm = sig.rssi.takeIf { it in -140..-40 },
                asu = sig.asuLevel.takeIf { it >= 0 },
                signalLevel = sig.level.takeIf { it in 0..4 },
                bands = if (Build.VERSION.SDK_INT >= 30) runCatching { id.bands?.toList().orEmpty() }.getOrDefault(emptyList()) else emptyList(),
                distanceEstimateM = sig.rsrp.takeIf { it in -140..-40 }?.let { rssiDistanceEstimateM(it, -35f, 3.0f) },
            )
        }
        is CellInfoNr -> {
            val id = c.cellIdentity as android.telephony.CellIdentityNr
            val sig = c.cellSignalStrength as CellSignalStrengthNr
            TowerCell(
                type = "NR",
                isRegistered = c.isRegistered,
                cid = id.nci,
                tacLac = id.tac,
                pci = id.pci,
                mccMnc = runCatching { id.mccString + "-" + id.mncString }.getOrDefault(""),
                rsrpDbm = sig.ssRsrp.takeIf { it in -140..-40 },
                rsrqDb = sig.ssRsrq.takeIf { it in -40..0 },
                asu = sig.asuLevel.takeIf { it >= 0 },
                signalLevel = sig.level.takeIf { it in 0..4 },
                bands = if (Build.VERSION.SDK_INT >= 34) runCatching { id.bands?.toList().orEmpty() }.getOrDefault(emptyList()) else emptyList(),
                distanceEstimateM = sig.ssRsrp.takeIf { it in -140..-40 }?.let { rssiDistanceEstimateM(it, -35f, 3.0f) },
            )
        }
        is CellInfoWcdma -> {
            val id = c.cellIdentity as android.telephony.CellIdentityWcdma
            val sig = c.cellSignalStrength as CellSignalStrengthWcdma
            TowerCell(
                type = "WCDMA",
                isRegistered = c.isRegistered,
                cid = id.cid.toLong(),
                tacLac = id.lac,
                pci = id.psc,
                mccMnc = runCatching { id.mccString + "-" + id.mncString }.getOrDefault(""),
                rssiDbm = sig.dbm.takeIf { it in -140..-40 },
                asu = sig.asuLevel.takeIf { it >= 0 },
                signalLevel = sig.level.takeIf { it in 0..4 },
                distanceEstimateM = sig.dbm.takeIf { it in -140..-40 }?.let { rssiDistanceEstimateM(it, -35f, 3.0f) },
            )
        }
        is CellInfoGsm -> {
            val id = c.cellIdentity as android.telephony.CellIdentityGsm
            val sig = c.cellSignalStrength as CellSignalStrengthGsm
            TowerCell(
                type = "GSM",
                isRegistered = c.isRegistered,
                cid = id.cid.toLong(),
                tacLac = id.lac,
                mccMnc = runCatching { id.mccString + "-" + id.mncString }.getOrDefault(""),
                rssiDbm = sig.dbm.takeIf { it in -140..-40 },
                asu = sig.asuLevel.takeIf { it >= 0 },
                signalLevel = sig.level.takeIf { it in 0..4 },
                distanceEstimateM = sig.dbm.takeIf { it in -140..-40 }?.let { rssiDistanceEstimateM(it, -35f, 3.0f) },
            )
        }
        else -> null
    }

    @Suppress("MissingPermission")
    private fun operatorName(): String = runCatching { telephony.networkOperatorName ?: "未知运营商" }.getOrDefault("未知运营商")

    private fun networkTypeName(): String = when (runCatching { telephony.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA -> "3G HSPA"
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G EDGE/GPRS"
        TelephonyManager.NETWORK_TYPE_GSM -> "2G GSM"
        else -> "未知制式"
    }
}
