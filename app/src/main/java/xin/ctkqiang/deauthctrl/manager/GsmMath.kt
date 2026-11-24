package xin.ctkqiang.deauthctrl.manager

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 蜂窝频点纯计算:GSM ARFCN ↔ MHz / LTE EARFCN ↔ MHz / 频段识别。
 * 全部公式取自 3GPP TS 05.05 / TS 36.101,纯 JVM 可单测。
 */
object GsmMath {

    data class Band(val name: String, val arfcn: Int, val upMhz: Double?, val downMhz: Double?)

    /** GSM 频段定义:名称 / ARFCN 范围 / 上行 / 下行 */
    enum class GsmBand(
        val display: String,
        val arfcnMin: Int,
        val arfcnMax: Int,
        val upLow: Double,
        val upOffset: Int,
        val downOffset: Double,
    ) {
        GSM900("GSM-900", 1, 124, 890.0, 0, 45.0),
        GSM850("GSM-850", 128, 251, 824.2, 128, 45.0),
        DCS1800("DCS-1800", 512, 885, 1710.2, 512, 95.0),
        PCS1900("PCS-1900", 512, 810, 1850.2, 512, 80.0),
        ;

        /** 上行 MHz(ARFCN 必须在段内) */
        fun upMhz(arfcn: Int): Double = upLow + 0.2 * (arfcn - upOffset)

        /** 下行 MHz */
        fun downMhz(arfcn: Int): Double = upMhz(arfcn) + downOffset
    }

    /** 按 ARFCN 识别 GSM 频段(900/850 与 1800/1900 区间重叠,取更接近中点的段) */
    fun gsmBandOf(arfcn: Int): GsmBand? {
        val candidates = GsmBand.values().filter { arfcn in it.arfcnMin..it.arfcnMax }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()
        // 重叠区(512–810)按通行约定归 DCS-1800:取带宽更宽的段
        return candidates.maxByOrNull { it.arfcnMax - it.arfcnMin }
    }

    /** GSM ARFCN → 下行 MHz;不在任何段内返回 null */
    fun gsmDownMhz(arfcn: Int): Double? = gsmBandOf(arfcn)?.downMhz(arfcn)

    /** GSM 下行 MHz → 最近的 ARFCN(就近取整);不在任何段内返回 null */
    fun gsmArfcnOf(downMhz: Double): Int? {
        var best: Pair<Int, Double>? = null
        for (b in GsmBand.values()) {
            for (n in b.arfcnMin..b.arfcnMax) {
                val d = abs(b.downMhz(n) - downMhz)
                if (best == null || d < best.second) best = n to d
            }
        }
        return best?.first
    }

    /** LTE 频段定义(3GPP TS 36.101 常用段):名称 / EARFCN 下限 / EARFCN 偏移 / 下行低频 MHz / 双工 */
    data class LteBand(
        val band: Int,
        val name: String,
        val earfcnLow: Int,
        val earfcnOff: Int,
        val downLowMhz: Double,
        val tdd: Boolean = false,
    )

    val LTE_BANDS = listOf(
        LteBand(1, "FDD 2100", 0, 0, 2110.0),
        LteBand(3, "FDD 1800", 1200, 1200, 1805.0),
        LteBand(5, "FDD 850", 2400, 2400, 869.0),
        LteBand(7, "FDD 2600", 2750, 2750, 2620.0),
        LteBand(8, "FDD 900", 3450, 3450, 925.0),
        LteBand(20, "FDD 800", 6150, 6150, 791.0),
        LteBand(28, "FDD 700", 9210, 9210, 758.0),
        LteBand(38, "TDD 2600", 37750, 37750, 2570.0, tdd = true),
        LteBand(40, "TDD 2300", 38650, 38650, 2300.0, tdd = true),
        LteBand(41, "TDD 2500", 39650, 39650, 2496.0, tdd = true),
    )

    /** EARFCN → 频段(返回 band 与中心 MHz) */
    fun lteBandOf(earfcn: Int): Pair<LteBand, Double>? {
        for (b in LTE_BANDS) {
            val n = earfcn - b.earfcnOff
            // FDD:0..599 常规范围;TDD:0..~19400 上限宽,按 600/19400 判断
            val maxN = if (b.tdd) 499 else 599
            if (n in 0..maxN) {
                return b to b.downLowMhz + 0.1 * n
            }
        }
        return null
    }

    /** 中心 MHz → 最近 LTE 频段(下行范围内就近) */
    fun lteBandOfMhz(mhz: Double): Pair<LteBand, Double>? {
        var best: Triple<LteBand, Double, Double>? = null
        for (b in LTE_BANDS) {
            val maxN = if (b.tdd) 499 else 599
            for (n in 0..maxN) {
                val f = b.downLowMhz + 0.1 * n
                val d = abs(f - mhz)
                if (best == null || d < best.third) best = Triple(b, f, d)
            }
        }
        return best?.let { it.first to it.second }
    }

    /** 下行 MHz → GSM 频段名 + LTE 频段名(用于频谱对照) */
    fun bandLabelOf(downMhz: Double): String {
        val gsm = gsmArfcnOf(downMhz)?.let { gsmBandOf(it) }
        val lte = lteBandOfMhz(downMhz)
        val parts = mutableListOf<String>()
        gsm?.let { parts.add("${it.display} ARFCN ${gsmArfcnOf(downMhz)}") }
        lte?.let { parts.add("LTE B${it.first.band} (${it.first.name}) EARFCN ${(it.second - it.first.downLowMhz) / 0.1 + it.first.earfcnOff}".replace(".0 ", " ")) }
        return if (parts.isEmpty()) "未匹配已知频段" else parts.joinToString(" · ")
    }

    /** TA(时间提前量)距离估算:GSM/LTE 每 TA ≈ 550m(GSM)或 78m(LTE) */
    fun taDistanceMeters(ta: Int, lte: Boolean): Int = ta * if (lte) 78 else 550

    fun formatMhz(mhz: Double): String = "%.1f MHz".format(mhz)

    fun formatDistance(m: Int): String =
        if (m >= 1000) "%.1f km".format(m / 1000.0) else "$m m"
}
