package xin.ctkqiang.deauthctrl.manager

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 导航纯计算:球面距离 / 方位角 / 格式化(纯 JVM 可单测) */
object NavMath {

    const val EARTH_RADIUS_M = 6_371_000.0

    /** Haversine 球面距离(米) */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /** 方位角(度,0=北,顺时针) */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** 距离文案:米级 / 千米级 */
    fun formatDistance(m: Double): String =
        if (m < 1000) "%.0f m".format(m) else "%.2f km".format(m / 1000)

    /** 方位文案:八方位 + 角度 */
    fun formatBearing(deg: Double): String {
        val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = ((deg + 22.5) / 45.0).toInt() % 8
        return "%s %.0f°".format(dirs[idx], deg)
    }
}
