package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 球面距离 / 方位角 / 格式化 —— 纯 JVM 单测 */
class NavMathTest {

    @Test
    fun haversine_beijingToShanghai() {
        // 北京(39.904, 116.407) → 上海(31.230, 121.474) ≈ 1067 km
        val d = NavMath.haversineMeters(39.904, 116.407, 31.230, 121.474)
        assertTrue("distance=$d", d in 1_055_000.0..1_075_000.0)
    }

    @Test
    fun haversine_zeroForSamePoint() {
        assertEquals(0.0, NavMath.haversineMeters(39.9, 116.4, 39.9, 116.4), 0.01)
    }

    @Test
    fun bearing_northAndEast() {
        // 正北
        assertEquals(0.0, NavMath.bearingDegrees(39.0, 116.0, 40.0, 116.0), 0.5)
        // 正东
        assertEquals(90.0, NavMath.bearingDegrees(39.0, 116.0, 39.0, 117.0), 1.0)
        // 北京 → 上海 ≈ 东南 160° 左右
        val b = NavMath.bearingDegrees(39.904, 116.407, 31.230, 121.474)
        assertTrue("bearing=$b", b in 150.0..170.0)
    }

    @Test
    fun formatDistance_metersAndKm() {
        assertEquals("500 m", NavMath.formatDistance(500.0))
        assertEquals("1.50 km", NavMath.formatDistance(1500.0))
    }

    @Test
    fun formatBearing_eightDirections() {
        assertTrue(NavMath.formatBearing(0.0).startsWith("N "))
        assertTrue(NavMath.formatBearing(45.0).startsWith("NE "))
        assertTrue(NavMath.formatBearing(90.0).startsWith("E "))
        assertTrue(NavMath.formatBearing(225.0).startsWith("SW "))
        assertTrue(NavMath.formatBearing(315.0).startsWith("NW "))
    }
}
