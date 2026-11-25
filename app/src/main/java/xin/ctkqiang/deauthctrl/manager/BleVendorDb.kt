package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import java.io.InputStream

/**
 * BLE / 网络设备的 MAC 厂商识别库
 *
 * 包装 [VendorPrefixes] 的资产加载与索引缓存:
 * - 数据源 `assets/ble/nmap-mac-prefixes.txt`(52,091 行,
 *   Nmap 项目生成,IEEE OUI 公开数据,随包携带归属说明)
 * - 首次查询时懒加载,之后常驻内存(约 2~3 MB 索引,可接受)
 *
 * ## 线程模型
 *
 * 加载发生在调用线程(资产读取是一次性 IO,后续查询纯内存)。
 * 扫描器回调线程直接调用 [vendorOfMac] 是安全的:
 * 索引构建完成后 `Map` 只读,无锁竞争。
 *
 * ## 能力边界(UI 与免责声明必须一致)
 *
 * 返回的是网卡芯片注册厂商,不是成品设备品牌。
 * UI 侧应措辞为"MAC 厂商"或"注册厂商",不得写成"制造商"。
 */
object BleVendorDb {

    @Volatile
    private var index: Map<String, String>? = null

    /** 注册表资产路径 */
    const val ASSET_PATH = "ble/nmap-mac-prefixes.txt"

    /**
     * 查询 MAC 对应的注册厂商。
     *
     * @param mac 任意常见拼写;非法输入返回 null
     * @return 厂商名;资产缺失、未加载成功或无匹配时返回 null,
     *         不抛出 —— 扫描 UI 把 null 显示为"未知厂商"。
     */
    fun vendorOfMac(context: Context, mac: String): String? {
        val map = index ?: load(context) ?: return null
        return VendorPrefixes.lookup(map, mac)
    }

    /**
     * 从资产流加载并缓存索引。加载失败返回 null,
     * 下次调用会重试(资产可能在文件级被外部替换)。
     */
    private fun load(context: Context): Map<String, String>? {
        return try {
            context.assets.open(ASSET_PATH).use { stream ->
                index = parse(stream)
            }
            index
        } catch (e: Exception) {
            // 资产读取失败(缺文件 / IO 错误)不致命:
            // 设备行照常显示,仅厂商名一列缺失。
            null
        }
    }

    /**
     * 逐行解析注册表并构建索引。供 JVM 单测直接喂合成数据,
     * 避免依赖 Android AssetManager。
     */
    fun parse(stream: InputStream): Map<String, String> {
        val entries = stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { VendorPrefixes.parseLine(it) }.toList()
        }
        return VendorPrefixes.buildIndex(entries)
    }

    /** 仅供单元测试重置缓存 */
    fun clearForTesting() {
        index = null
    }
}
