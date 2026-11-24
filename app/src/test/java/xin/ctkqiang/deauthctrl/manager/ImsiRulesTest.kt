package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** IMSI 捕捉器检测规则 —— 纯 JVM 单测 */
class ImsiRulesTest {

    private fun s(rat: Int, lac: Int, cid: Long, dbm: Int, t: Long) = ImsiRules.CellSample(rat, lac, cid, dbm, t)

    @Test
    fun downgrade4gTo2g_isHigh() {
        val events = ImsiRules.downgradeEvents(s(2, 100, 1L, -85, 0), s(0, 999, 2L, -60, 10_000))
        assertEquals(1, events.size)
        assertEquals(ImsiRules.Risk.HIGH, events[0].risk)
    }

    @Test
    fun downgrade4gTo3g_isMedium() {
        val events = ImsiRules.downgradeEvents(s(2, 100, 1L, -85, 0), s(1, 100, 1L, -80, 10_000))
        assertEquals(ImsiRules.Risk.MEDIUM, events[0].risk)
    }

    @Test
    fun noDowngrade_whenUpgrading() {
        assertTrue(ImsiRules.downgradeEvents(s(0, 1, 1L, -80, 0), s(2, 1, 1L, -80, 10_000)).isEmpty())
    }

    @Test
    fun lacJump_mediumWhenSignalStrong() {
        val e = ImsiRules.lacJumpEvent(s(2, 100, 1L, -80, 0), s(2, 555, 9L, -60, 10_000))
        assertEquals(ImsiRules.Risk.MEDIUM, e!!.risk)
    }

    @Test
    fun lacJump_nullWhenSameLac() {
        assertNull(ImsiRules.lacJumpEvent(s(2, 100, 1L, -80, 0), s(2, 100, 9L, -60, 10_000)))
    }

    @Test
    fun signalSurge_detectsAndIgnoresSlowWindow() {
        val e = ImsiRules.signalSurgeEvent(s(2, 100, 1L, -95, 0), s(2, 100, 1L, -70, 5_000))
        assertEquals(ImsiRules.Risk.MEDIUM, e!!.risk)
        assertNull(ImsiRules.signalSurgeEvent(s(2, 100, 1L, -95, 0), s(2, 100, 1L, -70, 500_000)))
    }

    @Test
    fun alertLevel_escalates() {
        val high = listOf(ImsiRules.Event("x", "y", ImsiRules.Risk.HIGH))
        assertEquals(ImsiRules.Risk.HIGH, ImsiRules.alertLevel(high))
        val twoMed = listOf(
            ImsiRules.Event("a", "b", ImsiRules.Risk.MEDIUM),
            ImsiRules.Event("c", "d", ImsiRules.Risk.MEDIUM),
        )
        assertEquals(ImsiRules.Risk.MEDIUM, ImsiRules.alertLevel(twoMed))
        assertEquals(ImsiRules.Risk.LOW, ImsiRules.alertLevel(emptyList()))
    }
}
