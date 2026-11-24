package xin.ctkqiang.deauthctrl.manager

import com.google.ar.core.ArCoreApk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 深度换算 / 置信度过滤 / 坐标索引 / 热力图色带 —— 纯 JVM 单测 */
class DepthMathTest {

    @Test
    fun mmToMeters_converts() {
        assertEquals(1.5f, DepthMath.mmToMeters(1500), 0.001f)
        assertEquals(0.25f, DepthMath.mmToMeters(250), 0.001f)
    }

    @Test
    fun formatMeters_formats() {
        assertEquals("1.42 m", DepthMath.formatMeters(1420))
        assertEquals("0.50 m", DepthMath.formatMeters(500))
        assertEquals("250 m", DepthMath.formatMeters(250_000))
    }

    @Test
    fun uvToIndex_mapsAndClamps() {
        assertEquals(0, DepthMath.uvToIndex(0f, 0f, 160, 120))
        assertEquals(160 * 120 - 1, DepthMath.uvToIndex(1f, 1f, 160, 120))
        assertEquals(1 * 160 + 2, DepthMath.uvToIndex(2f / 160f, 1f / 120f, 160, 120))
        assertEquals(0, DepthMath.uvToIndex(-1f, -1f, 160, 120)) // 钳位
    }

    @Test
    fun filterByConfidence_zeroesLowConfidence() {
        val depth = shortArrayOf(1000, 2000, 3000)
        val conf = byteArrayOf(120, 50, 0)
        val out = DepthMath.filterByConfidence(depth, conf, minConf = 100)
        assertEquals(1000, out[0].toInt())
        assertEquals(0, out[1].toInt())
        assertEquals(0, out[2].toInt())
    }

    @Test
    fun filterByConfidence_handlesSizeMismatch() {
        val depth = shortArrayOf(1000, 2000, 3000, 4000)
        val conf = byteArrayOf(120)
        val out = DepthMath.filterByConfidence(depth, conf)
        assertEquals(4, out.size)
        assertEquals(1000, out[0].toInt())
        assertEquals(0, out[1].toInt())
    }

    @Test
    fun centerMedian_returnsMedianIgnoringZeros() {
        val w = 5
        val h = 5
        val depth = ShortArray(w * h)
        // 中心 3×3 有效值:1000 / 1500 / 2000,中值 1500
        depth[2 * w + 2] = 1000
        depth[3 * w + 2] = 1500
        depth[3 * w + 3] = 2000
        assertEquals(1500, DepthMath.centerMedianMm(depth, w, h)!!.toInt())
    }

    @Test
    fun centerMedian_nullWhenAllInvalid() {
        assertNull(DepthMath.centerMedianMm(ShortArray(25), 5, 5))
        assertNull(DepthMath.centerMedianMm(ShortArray(4), 2, 2)) // 过小
    }

    @Test
    fun buildHeatmap_mapsColorsAndTransparent() {
        val w = 8
        val h = 6
        val depth = ShortArray(w * h)
        depth[0] = 500              // 0.5 m → 红
        depth[w * 5 + 7] = 6000     // 6 m → 蓝
        val heat = DepthMath.buildHeatmap(depth, w, h, 8, 6)
        assertEquals(8 * 6, heat.size)
        assertEquals(DepthMath.colorForMm(500), heat[0])
        assertEquals(DepthMath.colorForMm(6000), heat[5 * 8 + 7])
        assertEquals(0, heat[1]) // 无效像素透明
    }

    @Test
    fun colorForMm_bands() {
        assertEquals(0xFFEC407A.toInt(), DepthMath.colorForMm(500))
        assertEquals(0xFFFF9800.toInt(), DepthMath.colorForMm(1500))
        assertEquals(0xFFFFEB3B.toInt(), DepthMath.colorForMm(2500))
        assertEquals(0xFF00E676.toInt(), DepthMath.colorForMm(4000))
        assertEquals(0xFF00B0FF.toInt(), DepthMath.colorForMm(9000))
    }

    // ─── 能力状态映射 ────────────────────────────────────────────────────────

    @Test
    fun mapArAvailability_mapsAllStates() {
        assertEquals(DepthCapability.CHECKING, DepthMath.mapArAvailability(ArCoreApk.Availability.SUPPORTED_INSTALLED))
        assertEquals(DepthCapability.NOT_SUPPORTED, DepthMath.mapArAvailability(ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE))
        // 其余全部 → AR_MISSING(缺服务 / 过旧 / 未知)
        assertEquals(DepthCapability.AR_MISSING, DepthMath.mapArAvailability(ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED))
        assertEquals(DepthCapability.AR_MISSING, DepthMath.mapArAvailability(ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD))
        assertEquals(DepthCapability.AR_MISSING, DepthMath.mapArAvailability(ArCoreApk.Availability.UNKNOWN_ERROR))
        assertEquals(DepthCapability.AR_MISSING, DepthMath.mapArAvailability(ArCoreApk.Availability.UNKNOWN_CHECKING))
        assertEquals(DepthCapability.AR_MISSING, DepthMath.mapArAvailability(ArCoreApk.Availability.UNKNOWN_TIMED_OUT))
    }

    @Test
    fun mapDepthSupport_maps() {
        assertEquals(DepthCapability.AVAILABLE, DepthMath.mapDepthSupport(true))
        assertEquals(DepthCapability.NOT_SUPPORTED, DepthMath.mapDepthSupport(false))
    }
}
