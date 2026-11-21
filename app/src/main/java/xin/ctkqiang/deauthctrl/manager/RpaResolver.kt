package xin.ctkqiang.deauthctrl.manager

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * RPA 解析器 —— BLE 可解析私有地址(Resolvable Private Address)反匿名化
 *
 * 移植自 btrpa-scan 的核心技术:Bluetooth Core Specification Vol 3 Part H
 * §2.2.2 的 ah() 函数 —— hash = AES-128-ECB(IRK, padding(104bit 0) || prand)
 * 截取前 24 位,与地址中的 hash 段比对。命中即证明该旋转地址属于持有
 * 该 IRK 的设备(iOS/Android 设备默认开 RPA 轮换,普通扫描看到的是
 * 不断变化的一次性地址;拿到 IRK 后每一次出现都能被认出)。
 *
 * IRK 来源:设备配对时双方交换;Android 不公开读取本地 IRK 的 API ——
 * 应用接受用户提供的 IRK(从其他工具导出),如实说明。
 */
object RpaResolver {

    /** 判断地址是否为 RPA(prand 最高两位 = 01) */
    fun isRpa(address: String): Boolean {
        val a = parseAddress(address) ?: return false
        return (a[0].toInt() and 0xC0) == 0x40
    }

    /**
     * 用一组 IRK 解析地址;返回命中的 IRK(原始字节),未命中 null。
     */
    fun resolve(address: String, irks: List<ByteArray>): ByteArray? {
        val a = parseAddress(address) ?: return null
        if ((a[0].toInt() and 0xC0) != 0x40) return null
        // prand = 地址后 3 字节
        val prand = byteArrayOf(a[3], a[4], a[5])
        // r' = 104 位 0 padding || prand(16 字节)
        val rPrime = ByteArray(16)
        System.arraycopy(prand, 0, rPrime, 13, 3)
        // hash = 地址前 3 字节(最高 2 位是 prand 标记位,归零)
        val addrHash = byteArrayOf(
            (a[0].toInt() and 0x3F).toByte(), a[1], a[2],
        )
        for (irk in irks) {
            if (irk.size != 16) continue
            val computed = aes128Ecb(irk, rPrime) ?: continue
            if (computed[0] == addrHash[0] && computed[1] == addrHash[1] && computed[2] == addrHash[2]) {
                return irk
            }
        }
        return null
    }

    /** 解析 "AA:BB:.." / "AABB.." / "aa-bb-.." 为 6 字节;非法返回 null */
    fun parseAddress(address: String): ByteArray? {
        val hex = address.replace(":", "").replace("-", "").replace(" ", "").trim()
        if (hex.length != 12) return null
        return runCatching {
            ByteArray(6) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    private fun aes128Ecb(key: ByteArray, data: ByteArray): ByteArray? = runCatching {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        cipher.doFinal(data)
    }.getOrNull()

    /** 解析一行 IRK 文本:32 位十六进制(带/不带分隔符),非法返回 null */
    fun parseIrkText(text: String): ByteArray? {
        val hex = text.trim().removePrefix("0x").replace(":", "").replace("-", "").replace(" ", "")
        if (hex.length != 32) return null
        return runCatching {
            ByteArray(16) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }

    /** IRK 展示掩码:前 4 后 4 十六进制(btrpa-scan 同款) */
    fun maskIrk(irk: ByteArray): String {
        if (irk.size != 16) return "?"
        val hex = irk.joinToString("") { "%02x".format(it) }.uppercase()
        return hex.take(4) + "…" + hex.takeLast(4)
    }
}
