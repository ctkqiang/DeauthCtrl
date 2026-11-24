package xin.ctkqiang.deauthctrl.manager

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import java.io.File

/** 扇区破解结果 */
data class MfSectorResult(
    val sector: Int,
    val keyA: String? = null,
    val keyB: String? = null,
    /** 该扇区全部块(含 trailer)的 16 字节内容;仅密钥命中时非空 */
    val blocks: List<ByteArray> = emptyList(),
)

/** 全卡 dump 结果 */
data class MfClassicDump(
    val uid: String,
    val size: Int,
    val sectors: List<MfSectorResult>,
    val readMs: Long,
) {
    /** 命中密钥的扇区数 */
    val crackedCount: Int get() = sectors.count { it.keyA != null || it.keyB != null }
}

/**
 * Mifare Classic 破解工作台 —— 公钥(厂商默认密钥)字典逐扇区认证 + 全卡 dump。
 *
 * ## 边界
 * - 字典全部来自公开的 mf_classic_dict 默认密钥表(Mifare Classic 出厂默认 /
 *   NFC Forum / MAD 等),**仅用于自有卡或授权测试**;
 * - 未知密钥的扇区不做在线穷举(非法且不可行),如实标为锁定;
 * - 读卡在 reader mode 回调线程内完成,Tag 会话结束自动释放。
 */
class MfClassicManager(private val context: Context) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    val supported: Boolean get() = adapter != null
    val enabled: Boolean get() = adapter?.isEnabled == true

    /** 待破解标签(reader mode 回调暂存) */
    @Volatile var pending: MfClassicDump? = null

    fun startReader(activity: Activity, onDump: (MfClassicDump) -> Unit) {
        val a = adapter ?: return
        if (!a.isEnabled) return
        a.enableReaderMode(
            activity,
            { tag -> handleTag(tag, onDump) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            android.os.Bundle(),
        )
    }

    fun stopReader(activity: Activity) {
        runCatching { adapter?.disableReaderMode(activity) }
    }

    private fun handleTag(tag: Tag, onDump: (MfClassicDump) -> Unit) {
        val mc = MifareClassic.get(tag) ?: return
        val uid = tag.id.joinToString(":") { "%02X".format(it) }
        val size = runCatching { mc.size }.getOrDefault(0)
        val sectorCount = runCatching { mc.sectorCount }.getOrDefault(0)
        val results = mutableListOf<MfSectorResult>()
        runCatching {
            mc.connect()
            for (sector in 0 until sectorCount) {
                results.add(crackSector(mc, sector))
            }
            mc.close()
        }
        val dump = MfClassicDump(uid, size, results, System.currentTimeMillis())
        pending = dump
        onDump(dump)
    }

    /** 单扇区:字典密钥逐个尝试 A/B,命中后读出全部块 */
    private fun crackSector(mc: MifareClassic, sector: Int): MfSectorResult {
        for (keyHex in MfKeys.DICTIONARY) {
            val key = MfKeys.toBytes(keyHex)
            val authA = runCatching {
                mc.authenticateSectorWithKeyA(sector, key)
                true
            }.getOrDefault(false)
            if (authA) return readSector(mc, sector, keyA = keyHex)
            val authB = runCatching {
                mc.authenticateSectorWithKeyB(sector, key)
                true
            }.getOrDefault(false)
            if (authB) return readSector(mc, sector, keyB = keyHex)
        }
        return MfSectorResult(sector)
    }

    private fun readSector(mc: MifareClassic, sector: Int, keyA: String? = null, keyB: String? = null): MfSectorResult {
        val blocks = mutableListOf<ByteArray>()
        val first = mc.sectorToBlock(sector)
        val count = if (sector < 32) 4 else 16
        for (i in 0 until count) {
            runCatching { blocks.add(mc.readBlock(first + i)) }
        }
        return MfSectorResult(sector, keyA, keyB, blocks)
    }

    /** 导出 .bin 全卡镜像 + .txt 密钥报告到应用外置目录 */
    fun export(dump: MfClassicDump): File {
        val dir = File(context.getExternalFilesDir(null), "flipper")
        dir.mkdirs()
        val name = "mf_${dump.uid.replace(":", "")}_${System.currentTimeMillis() % 100000}"
        val bin = File(dir, "$name.bin")
        val bytes = ByteArray(dump.size)
        dump.sectors.forEach { s ->
            // 块偏移:Mifare Classic 前 32 扇区各 4 块,其后(4K)各 16 块
            val firstBlock = if (s.sector < 32) s.sector * 4 else 128 + (s.sector - 32) * 16
            s.blocks.forEachIndexed { i, block ->
                val offset = (firstBlock + i) * 16
                if (offset + 16 <= bytes.size) System.arraycopy(block, 0, bytes, offset, 16)
            }
        }
        bin.writeBytes(bytes)
        val txt = File(dir, "$name.txt")
        txt.writeText(buildString {
            appendLine("UID: ${dump.uid}")
            appendLine("Size: ${dump.size} bytes")
            appendLine("Cracked: ${dump.crackedCount}/${dump.sectors.size} sectors")
            dump.sectors.forEach { s ->
                appendLine(
                    "Sector %02d: A=%s B=%s".format(
                        s.sector,
                        s.keyA ?: "--------",
                        s.keyB ?: "--------",
                    ),
                )
            }
        })
        return bin
    }
}

/** 公钥字典与十六进制换算(纯函数,可单测) */
object MfKeys {

    /** 公开默认密钥表(mf_classic_dict 子集:出厂默认 / NFC Forum / MAD) */
    val DICTIONARY = listOf(
        "FFFFFFFFFFFF", "A0A1A2A3A4A5", "D3F7D3F7D3F7", "000000000000",
        "B0B1B2B3B4B5", "AABBCCDDEEFF", "714C5C886E97", "587EE5F9350F",
        "A0478CC39091", "533CB6C723F6", "8FD0A4F256E9", "4D3A99C351DD",
        "1A982C7E459A", "0123456789AB",
    )

    /** "AABBCCDDEEFF" → ByteArray(6);非法字符/长度不符返回空 */
    fun toBytes(hex: String): ByteArray {
        val s = hex.filter { it != ' ' && it != ':' }
        if (s.length % 2 != 0) return ByteArray(0)
        return runCatching {
            ByteArray(s.length / 2) { i ->
                s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }.getOrDefault(ByteArray(0))
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }
}
