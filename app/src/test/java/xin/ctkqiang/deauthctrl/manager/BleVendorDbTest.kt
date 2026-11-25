package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * OUI 资产完整性回归测试。
 *
 * 证明两件事:
 * 1. 随包携带的 `ble/nmap-mac-prefixes.txt` 能被 [BleVendorDb.parse]
 *    完整解析(5 万条以上,而不是解析中途崩溃或返回空表);
 * 2. 真实查询能命中已知前缀(Xerox 的 00:00:00 是 OUI 数据库的
 *    固定成员,自 1990 年代起一直存在)。
 *
 * 若资产在构建中被剥离或改坏,此测试第一个报警。
 */
@RunWith(RobolectricTestRunner::class)
class BleVendorDbTest {

    @Test
    fun `bundled asset parses to a large index`() {
        val context = RuntimeEnvironment.getApplication()
        val stream = context.assets.open(BleVendorDb.ASSET_PATH)
        val index = BleVendorDb.parse(stream)
        assertTrue("注册表应解析出 5 万条以上前缀,实际 ${index.size}",
            index.size > 50_000)
    }

    @Test
    fun `xerox prefix is resolvable from bundled asset`() {
        val context = RuntimeEnvironment.getApplication()
        val vendor = BleVendorDb.vendorOfMac(context, "00:00:00:11:22:33")
        assertNotNull("00:00:00 前缀必须可查", vendor)
        assertEquals("Xerox", vendor)
    }
}
