package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Test

/** 补给剩余天数与告警等级 —— 纯 JVM 单测 */
class RationMathTest {

    @Test
    fun waterDays_baseline() {
        // 9L / 2 人 / 人均 3L = 1.5 天
        assertEquals(1.5, RationMath.waterDaysRemaining(9.0, 2), 0.001)
    }

    @Test
    fun foodDays_baseline() {
        // 13200 kcal / 2 人 / 2200 = 3 天
        assertEquals(3.0, RationMath.foodDaysRemaining(13200.0, 2), 0.001)
    }

    @Test
    fun zeroStockOrPersons_isZeroDays() {
        assertEquals(0.0, RationMath.waterDaysRemaining(0.0, 2), 0.001)
        assertEquals(0.0, RationMath.waterDaysRemaining(9.0, 0), 0.001)
        assertEquals(0.0, RationMath.foodDaysRemaining(0.0, 3), 0.001)
    }

    @Test
    fun status_thresholds() {
        assertEquals(RationMath.Status.CRITICAL, RationMath.status(0.5))
        assertEquals(RationMath.Status.CRITICAL, RationMath.status(1.0))
        assertEquals(RationMath.Status.WATCH, RationMath.status(1.5))
        assertEquals(RationMath.Status.WATCH, RationMath.status(3.0))
        assertEquals(RationMath.Status.OK, RationMath.status(3.5))
    }

    @Test
    fun formatDays() {
        assertEquals("1.5 d", RationMath.formatDays(1.5))
        assertEquals("120 d", RationMath.formatDays(120.0))
    }
}
