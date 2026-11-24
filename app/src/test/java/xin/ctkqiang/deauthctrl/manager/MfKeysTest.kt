package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mifare Classic 默认密钥字典与十六进制换算 —— 纯 JVM 单测 */
class MfKeysTest {

    @Test
    fun dictionary_containsKnownDefaults() {
        // mf_classic_dict 中必有出厂默认 / NFC Forum / MAD
        assertTrue(MfKeys.DICTIONARY.contains("FFFFFFFFFFFF"))
        assertTrue(MfKeys.DICTIONARY.contains("A0A1A2A3A4A5"))
        assertTrue(MfKeys.DICTIONARY.contains("D3F7D3F7D3F7"))
        assertTrue(MfKeys.DICTIONARY.contains("000000000000"))
    }

    @Test
    fun toBytes_converts6ByteKeys() {
        val bytes = MfKeys.toBytes("AABBCCDDEEFF")
        assertEquals(6, bytes.size)
        assertEquals(0xAA.toByte(), bytes[0])
        assertEquals(0xFF.toByte(), bytes[5])
    }

    @Test
    fun toBytes_handlesSeparatorsAndInvalid() {
        assertEquals("AABB".let { MfKeys.toBytes("AA:BB").size }, 2)
        assertTrue(MfKeys.toBytes("ZZZZ").isEmpty())
        assertTrue(MfKeys.toBytes("ABC").isEmpty()) // 奇数长度
    }

    @Test
    fun toHex_roundTrips() {
        val bytes = MfKeys.toBytes("D3F7D3F7D3F7")
        assertEquals("D3F7D3F7D3F7", MfKeys.toHex(bytes))
    }
}
