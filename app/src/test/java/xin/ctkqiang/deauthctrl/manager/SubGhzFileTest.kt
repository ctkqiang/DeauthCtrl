package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Flipper .sub 解析/生成 + 频段提示 —— 纯 JVM 单测 */
class SubGhzFileTest {

    @Test
    fun parse_rawFile() {
        val text = """
            Filetype: Flipper SubGhz RAW File
            Version: 1
            Frequency: 433920000
            Preset: FuriHalSubGhzPresetOok650Async
            Protocol: RAW
            RAW_Data: 1000 -1000 1000 -1000 1000 -1000
        """.trimIndent()
        val p = SubGhzFile.parse(text)
        assertEquals(433_920_000L, p!!.frequencyHz)
        assertEquals("RAW", p.protocol)
        assertEquals("FuriHalSubGhzPresetOok650Async", p.preset)
        assertEquals(listOf(1000, -1000, 1000, -1000, 1000, -1000), p.rawData)
    }

    @Test
    fun parse_staticKeyFile() {
        val text = """
            Filetype: Flipper SubGhz Key File
            Version: 1
            Frequency: 433920000
            Preset: FuriHalSubGhzPresetOok650Async
            Protocol: Princeton
            Bit: 24
            Key: 00 00 00 00 00 1E 53 B0
            TE: 400
        """.trimIndent()
        val p = SubGhzFile.parse(text)
        assertEquals("Princeton", p!!.protocol)
        assertEquals(24, p.bit)
        assertEquals("00000000001E53B0", p.keyHex)
        assertEquals(400, p.te)
        assertTrue(p.rawData.isEmpty())
    }

    @Test
    fun parse_rejectsInvalid() {
        assertNull(SubGhzFile.parse("hello world"))
        assertNull(SubGhzFile.parse("Frequency: 433920000")) // 缺 Protocol
    }

    @Test
    fun buildRaw_roundTrips() {
        val built = SubGhzFile.buildRaw(433_920_000L, "FuriHalSubGhzPresetOok650Async", listOf(500, -500))
        val p = SubGhzFile.parse(built)
        assertEquals(433_920_000L, p!!.frequencyHz)
        assertEquals(listOf(500, -500), p.rawData)
        assertTrue(built.startsWith("Filetype: Flipper SubGhz RAW File"))
    }

    @Test
    fun buildPrinceton_formatsKey() {
        val built = SubGhzFile.buildPrinceton(433_920_000L, "FuriHalSubGhzPresetOok650Async", 24, "001e 53b0", 400)
        assertTrue(built.contains("Protocol: Princeton"))
        assertTrue(built.contains("Bit: 24"))
        assertTrue(built.contains("Key: 00 1E 53 B0"))
        assertTrue(built.contains("TE: 400"))
        val p = SubGhzFile.parse(built)
        assertEquals("001E53B0", p!!.keyHex)
    }

    @Test
    fun bandLabel_checksLegality() {
        assertTrue(SubGhzFile.bandLabel(433_920_000L).contains("433.92"))
        assertTrue(SubGhzFile.bandLabel(2_400_000_000L).contains("300–928"))
        assertTrue(SubGhzFile.bandLabel(100_000_000L).contains("300–928"))
    }
}
