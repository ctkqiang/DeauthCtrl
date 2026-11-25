package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OUI 注册表解析器单测 —— 纯逻辑核(无 IO)。
 *
 * 证明:[VendorPrefixes] 的解析规则(注释/空行跳过、6/7/9 位前缀、
 * 厂商名内部空格保留)与查找规则(9 → 7 → 6 最长前缀优先、
 * MAC 拼写归一化)行为正确,不随资产更新回归。
 */
class VendorPrefixesTest {

    @Test
    fun `parseLine skips comments and blanks`() {
        assertNull(VendorPrefixes.parseLine(""))
        assertNull(VendorPrefixes.parseLine("   "))
        assertNull(VendorPrefixes.parseLine("# generated with make-mac-prefixes.pl"))
        assertNull(VendorPrefixes.parseLine("  # indented comment"))
    }

    @Test
    fun `parseLine keeps vendor names containing spaces`() {
        val e = VendorPrefixes.parseLine("001122  My Cool Vendor")
        assertEquals("001122", e?.prefix)
        assertEquals("My Cool Vendor", e?.vendor)
    }

    @Test
    fun `parseLine accepts 6 7 and 9 hex prefixes`() {
        assertEquals("000000", VendorPrefixes.parseLine("000000 X")?.prefix)
        assertEquals("1234567", VendorPrefixes.parseLine("1234567 Y")?.prefix)
        assertEquals("ABCDEF012", VendorPrefixes.parseLine("ABCDEF012 Z")?.prefix)
    }

    @Test
    fun `parseLine rejects bad prefixes`() {
        assertNull(VendorPrefixes.parseLine("12345 X"))       // 5 位
        assertNull(VendorPrefixes.parseLine("12345678 X"))    // 8 位
        assertNull(VendorPrefixes.parseLine("1234567890 X"))  // 10 位
        assertNull(VendorPrefixes.parseLine("12345G X"))      // 非十六进制
        assertNull(VendorPrefixes.parseLine("123456"))        // 缺厂商名
        assertNull(VendorPrefixes.parseLine("123456   "))     // 厂商名为空白
    }

    @Test
    fun `parseLine uppercases prefix but not vendor`() {
        val e = VendorPrefixes.parseLine("aabbcc  lower vendor")
        assertEquals("AABBCC", e?.prefix)
        assertEquals("lower vendor", e?.vendor)
    }

    @Test
    fun `normalizeMac strips common separators and uppercases`() {
        assertEquals("AABBCCDDEEFF", VendorPrefixes.normalizeMac("AA:BB:CC:DD:EE:FF"))
        assertEquals("AABBCCDDEEFF", VendorPrefixes.normalizeMac("aa-bb-cc-dd-ee-ff"))
        assertEquals("AABBCCDDEEFF", VendorPrefixes.normalizeMac("aabb.ccdd.eeff"))
        assertEquals("AABBCCDDEEFF", VendorPrefixes.normalizeMac("AA BB CC DD EE FF"))
    }

    @Test
    fun `normalizeMac rejects invalid input`() {
        assertNull(VendorPrefixes.normalizeMac("ZZ:BB:CC:DD:EE:FF"))
        assertNull(VendorPrefixes.normalizeMac("12345"))       // 不足 6 位
        assertNull(VendorPrefixes.normalizeMac(""))
        // 裸 6 位前缀本身是合法输入(可用来直接查 OUI 厂商)
        assertEquals("AABBCC", VendorPrefixes.normalizeMac("AA:BB:CC"))
    }

    @Test
    fun `lookup prefers longest prefix 9 then 7 then 6`() {
        val index = VendorPrefixes.buildIndex(
            listOf(
                VendorPrefixes.Entry("AABBCC", "SixVendor"),
                VendorPrefixes.Entry("AABBCCD", "SevenVendor"),
                VendorPrefixes.Entry("AABBCCDEF", "NineVendor"),
            )
        )
        // 三档前缀都命中同一 MAC:最长(9 位)优先
        assertEquals("NineVendor", VendorPrefixes.lookup(index, "AA:BB:CC:DE:F0:01"))
        // 只命中 7 位与 6 位:7 位优先
        assertEquals("SevenVendor", VendorPrefixes.lookup(index, "AA:BB:CC:D1:00:02"))
        // 只命中 6 位
        assertEquals("SixVendor", VendorPrefixes.lookup(index, "AA:BB:CC:00:00:03"))
    }

    @Test
    fun `lookup returns null for unknown or invalid mac`() {
        val index = VendorPrefixes.buildIndex(listOf(VendorPrefixes.Entry("AABBCC", "SixVendor")))
        assertNull(VendorPrefixes.lookup(index, "11:22:33:44:55:66"))
        assertNull(VendorPrefixes.lookup(index, "not-a-mac"))
    }

    @Test
    fun `buildIndex maps every prefix`() {
        val entries = listOf(
            VendorPrefixes.Entry("000000", "Xerox"),
            VendorPrefixes.Entry("AABBCC", "Foo"),
        )
        val index = VendorPrefixes.buildIndex(entries)
        assertEquals(2, index.size)
        assertEquals("Xerox", index["000000"])
        assertTrue("Xerox 的 00:00:00 前缀必须在真实资产中可查",
            index.containsKey("000000"))
    }
}
