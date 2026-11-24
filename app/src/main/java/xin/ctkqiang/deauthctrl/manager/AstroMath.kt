package xin.ctkqiang.deauthctrl.manager

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 天文纯计算:NOAA 日出日落算法 + 月相(纯 JVM 可单测)。
 * 输出为当地时钟分钟数(带时区偏移入参)。
 */
object AstroMath {

    data class SunTimes(
        val sunriseMinutes: Double,
        val sunsetMinutes: Double,
    ) {
        /** 白昼时长(分钟) */
        val dayLengthMinutes: Double get() = sunsetMinutes - sunriseMinutes
    }

    const val ZENITH_CIVIL = 96.0       // 民用晨昏蒙影
    const val ZENITH_SUN = 90.833       // 日出日落

    private fun norm(v: Double): Double = ((v % 360.0) + 360.0) % 360.0

    private fun sunTimeFor(
        lat: Double,
        lon: Double,
        year: Int,
        month: Int,
        day: Int,
        zenith: Double,
        rising: Boolean,
        tzOffsetHours: Double,
    ): Double {
        // 一年中的第几天(1-365)
        val n1 = Math.floor(275.0 * month / 9).toInt()
        val n2 = Math.floor((month + 9) / 12.0).toInt()
        val n3 = (1 + Math.floor((year - 4 * Math.floor(year / 4.0) + 2) / 3.0)).toInt()
        val n = n1 - (n2 * n3) + day - 30

        val lngHour = lon / 15.0
        val t = n + (if (rising) 6.0 - lngHour else 18.0 - lngHour) / 24.0
        val m = (0.9856 * t) - 3.289
        var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2 * m))) + 282.634
        l = norm(l)
        var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
        ra = norm(ra)
        val lQuadrant = (Math.floor(l / 90.0)) * 90.0
        val raQuadrant = (Math.floor(ra / 90.0)) * 90.0
        ra = (ra + (lQuadrant - raQuadrant)) / 15.0
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(Math.toRadians(zenith)) - (sinDec * sin(Math.toRadians(lat)))) /
            (cosDec * cos(Math.toRadians(lat)))
        if (cosH > 1.0 || cosH < -1.0) return -1.0 // 极昼/极夜
        val h = if (rising) 360.0 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))
        val hHour = h / 15.0
        val tLocal = hHour + ra - (0.06571 * t) - 6.622
        var ut = tLocal - lngHour
        ut = norm(ut)
        return ((ut + tzOffsetHours) * 60.0 + 24 * 60) % (24 * 60)
    }

    /** 当地时钟分钟:日出/日落;极昼极夜返回 -1 */
    fun sunTimes(lat: Double, lon: Double, year: Int, month: Int, day: Int, tzOffsetHours: Double): SunTimes =
        SunTimes(
            sunriseMinutes = sunTimeFor(lat, lon, year, month, day, ZENITH_SUN, rising = true, tzOffsetHours),
            sunsetMinutes = sunTimeFor(lat, lon, year, month, day, ZENITH_SUN, rising = false, tzOffsetHours),
        )

    /** 月相 0..1(0=新月,0.5=满月),基于朔望月 29.530588 天,参考新月 2000-01-06 18:14 UTC */
    fun moonPhase(utcMillis: Long): Double {
        val days = (utcMillis - 947_182_440_000L) / 86_400_000.0
        val p = (days % 29.530588) / 29.530588
        return if (p < 0) p + 1 else p
    }

    /** 月面照明比例(近似) */
    fun moonIllumination(phase: Double): Double = (1.0 - kotlin.math.abs(2 * phase - 1))

    fun phaseName(phase: Double): String = when {
        phase < 0.03 || phase >= 0.97 -> "新月"
        phase < 0.22 -> "娥眉月"
        phase < 0.28 -> "上弦月"
        phase < 0.47 -> "盈凸月"
        phase < 0.53 -> "满月"
        phase < 0.72 -> "亏凸月"
        phase < 0.78 -> "下弦月"
        else -> "残月"
    }

    /** 分钟数 → HH:MM */
    fun formatClock(minutes: Double): String {
        if (minutes < 0) return "--:--"
        val m = minutes.toInt() % (24 * 60)
        return "%02d:%02d".format(m / 60, m % 60)
    }
}
