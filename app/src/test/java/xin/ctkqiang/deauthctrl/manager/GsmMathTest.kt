package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GSM/LTE 频点换算 —— 纯 JVM 单测(3GPP 标准值) */
class GsmMathTest {

    @Test
    fun gsm900_downlink() {
        // ARFCN 1 → 下行 935.2 MHz(3GPP TS 05.05)
        assertEquals(935.2, GsmMath.gsmDownMhz(1)!!, 0.001)
        // ARFCN 62 → 947.4 MHz
        assertEquals(947.4, GsmMath.gsmDownMhz(62)!!, 0.001)
        // ARFCN 124 → 959.8 MHz
        assertEquals(959.8, GsmMath.gsmDownMhz(124)!!, 0.001)
    }

    @Test
    fun dcs1800_downlink() {
        // ARFCN 512 → 下行 1805.2 MHz
        assertEquals(1805.2, GsmMath.gsmDownMhz(512)!!, 0.001)
        // ARFCN 700 → 1842.8 MHz(1805.2 + 0.2×188)
        assertEquals(1842.8, GsmMath.gsmDownMhz(700)!!, 0.001)
    }

    @Test
    fun gsm850_downlink() {
        // ARFCN 128 → 下行 869.2 MHz
        assertEquals(869.2, GsmMath.gsmDownMhz(128)!!, 0.001)
    }

    @Test
    fun pcs1900_downlink() {
        // PCS 与 DCS 共享 ARFCN 512–810 —— 显式选段:PCS 512 → 下行 1930.2 MHz
        assertEquals(1930.2, GsmMath.GsmBand.PCS1900.downMhz(512), 0.001)
        assertEquals(1850.2, GsmMath.GsmBand.PCS1900.upMhz(512), 0.001)
    }

    @Test
    fun overlapArfcn512_prefersDcsByConvention() {
        // 自动识别在重叠区按通行约定归 DCS-1800
        assertEquals(GsmMath.GsmBand.DCS1800, GsmMath.gsmBandOf(512))
    }

    @Test
    fun invalidArfcn_isNull() {
        assertNull(GsmMath.gsmDownMhz(0))
        assertNull(GsmMath.gsmDownMhz(1000))
    }

    @Test
    fun gsmArfcn_roundTrip() {
        assertEquals(1, GsmMath.gsmArfcnOf(935.2))
        assertEquals(512, GsmMath.gsmArfcnOf(1805.2))
        assertEquals(128, GsmMath.gsmArfcnOf(869.2))
    }

    @Test
    fun lteBand3_earfcn1300() {
        // Band 3:EARFCN 1300 → 1815.0 MHz
        val (band, mhz) = GsmMath.lteBandOf(1300)!!
        assertEquals(3, band.band)
        assertEquals(1815.0, mhz, 0.001)
    }

    @Test
    fun lteBand40_earfcn38750() {
        // Band 40 TDD:EARFCN 38750 → 2310.0 MHz
        val (band, mhz) = GsmMath.lteBandOf(38750)!!
        assertEquals(40, band.band)
        assertEquals(2310.0, mhz, 0.001)
        assertTrue(band.tdd)
    }

    @Test
    fun lteBandOfMhz_identifies() {
        assertEquals(3, GsmMath.lteBandOfMhz(1815.0)!!.first.band)
        assertEquals(1, GsmMath.lteBandOfMhz(2140.0)!!.first.band)
        assertEquals(40, GsmMath.lteBandOfMhz(2350.0)!!.first.band)
    }

    @Test
    fun taDistance() {
        assertEquals(550, GsmMath.taDistanceMeters(1, lte = false))
        assertEquals(78, GsmMath.taDistanceMeters(1, lte = true))
        assertEquals(1100, GsmMath.taDistanceMeters(2, lte = false))
    }

    @Test
    fun formatHelpers() {
        assertEquals("935.2 MHz", GsmMath.formatMhz(935.2))
        assertEquals("550 m", GsmMath.formatDistance(550))
        assertEquals("1.1 km", GsmMath.formatDistance(1100))
    }
}
