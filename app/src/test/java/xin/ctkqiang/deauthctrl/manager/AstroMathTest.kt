package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** NOAA 日出日落 / 月相 —— 纯 JVM 单测 */
class AstroMathTest {

    @Test
    fun sunriseSunset_beijingSummerSolstice() {
        // 北京(39.90, 116.41)2024-06-21 夏至:日出 ≈ 04:46,日落 ≈ 19:46(UTC+8)
        val s = AstroMath.sunTimes(39.90, 116.41, 2024, 6, 21, tzOffsetHours = 8.0)
        assertTrue("sunrise=${s.sunriseMinutes}", s.sunriseMinutes in 270.0..300.0)   // 04:30–05:00
        assertTrue("sunset=${s.sunsetMinutes}", s.sunsetMinutes in 1170.0..1200.0)    // 19:30–20:00
        assertTrue("daylen=${s.dayLengthMinutes}", s.dayLengthMinutes in 870.0..930.0) // 夏至约 15h
    }

    @Test
    fun sunriseSunset_symmetricDay() {
        // 春分日(2024-03-20)北京:日出日落约对称于正午,白昼 ≈ 12h
        val s = AstroMath.sunTimes(39.90, 116.41, 2024, 3, 20, tzOffsetHours = 8.0)
        assertTrue("daylen=${s.dayLengthMinutes}", s.dayLengthMinutes in 700.0..740.0)
    }

    @Test
    fun moonPhase_newMoonOnEclipseDay() {
        // 2024-04-08 日全食 = 新月,相位 ≈ 0(或 ≈ 1)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2024, Calendar.APRIL, 8, 18, 0, 0)
        val p = AstroMath.moonPhase(cal.timeInMillis)
        assertTrue("phase=$p", p < 0.03 || p > 0.97)
    }

    @Test
    fun moonIllumination_fullAndNew() {
        assertEquals(1.0, AstroMath.moonIllumination(0.5), 0.001)
        assertEquals(0.0, AstroMath.moonIllumination(0.0), 0.001)
        assertTrue(AstroMath.moonIllumination(0.25) in 0.4..0.6) // 上弦约半亮
    }

    @Test
    fun formatClock() {
        assertEquals("04:46", AstroMath.formatClock(286.0))
        assertEquals("19:46", AstroMath.formatClock(1186.0))
        assertEquals("--:--", AstroMath.formatClock(-1.0))
    }
}
