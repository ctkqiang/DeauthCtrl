package xin.ctkqiang.deauthctrl.manager

import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.charset.StandardCharsets

/** 一张标签的完整读取结果 */
data class NfcTagInfo(
    val uid: String,
    val techs: List<String>,
    val ndefRecords: List<String>,
    val sectorCount: Int,
    val writable: Boolean,
    val readMs: Long,
)

/**
 * NFC / RFID 标签工作台
 *
 * 用前台分发（enableReaderMode）在页面打开期间持续读卡：
 * - 支持的技术清单（NfcA/B/F/V、MifareClassic、MifareUltralight、NDEF）；
 * - UID + NDEF 记录内容 + Mifare Classic 扇区数/可写性；
 * - 写入 NDEF 文本（NTAG / Ultralight 等可写标签）。
 *
 * 说明：Mifare Classic 等加密卡无法"克隆"—— 扇区密钥由厂商烧写，
 * 未授权的密钥穷举既不合法也不可行。本模块支持的是可自由读写的
 * NTAG/Ultralight 类标签的数据镜像与备份。
 */
class NfcManager(private val context: Context) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    val supported: Boolean get() = adapter != null

    val enabled: Boolean get() = adapter?.isEnabled == true

    private val _lastTag = MutableStateFlow<NfcTagInfo?>(null)
    val lastTag: StateFlow<NfcTagInfo?> = _lastTag

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var pendingWrite: String? = null

    /** 页面打开时调用：开启前台读卡分发 */
    fun startReader(activity: Activity) {
        val a = adapter ?: return
        if (!a.isEnabled) {
            _message.value = L10n.get(R.string.mgr_nfc_disabled)
            return
        }
        val options = android.os.Bundle()
        // 读卡期间不完全接管 Intent，避免打扰其他 NFC 应用
        a.enableReaderMode(
            activity,
            { tag -> handleTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            options,
        )
    }

    fun stopReader(activity: Activity) {
        runCatching { adapter?.disableReaderMode(activity) }
    }

    /** 下次读到标签时写入该文本 */
    fun queueWrite(text: String) {
        pendingWrite = text
        _message.value = L10n.get(R.string.mgr_nfc_write_ready)
    }

    private fun handleTag(tag: Tag) {
        val uid = tag.id.joinToString(":") { "%02X".format(it) }
        val techs = tag.techList.map { shortName(it) }
        val ndefRecords = mutableListOf<String>()
        var sectors = 0
        var writable = false

        // 读取 NDEF 内容
        runCatching {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                val msg = ndef.ndefMessage
                msg?.records?.forEach { r ->
                    val payload = String(r.payload.copyOfRange(if (r.payload.size > 3) 3 else 0, r.payload.size), StandardCharsets.UTF_8)
                    ndefRecords.add("${tnfName(r.tnf)}: $payload")
                }
                writable = ndef.isWritable
                // 待写队列
                pendingWrite?.let { text ->
                    val rec = NdefRecord.createTextRecord("zh", text)
                    ndef.writeNdefMessage(NdefMessage(rec))
                    _message.value = L10n.get(R.string.mgr_nfc_write_success, text)
                    pendingWrite = null
                }
                ndef.close()
            }
        }

        // Mifare Classic 扇区数（只读不碰数据，需要密钥）
        runCatching {
            MifareClassic.get(tag)?.let { mc ->
                mc.connect()
                sectors = mc.sectorCount
                mc.close()
            }
        }

        // Ultralight 可写性
        runCatching {
            MifareUltralight.get(tag)?.let { mu ->
                mu.connect()
                if (!writable) writable = true
                mu.close()
            }
        }

        _lastTag.value = NfcTagInfo(
            uid = uid,
            techs = techs,
            ndefRecords = ndefRecords,
            sectorCount = sectors,
            writable = writable,
            readMs = System.currentTimeMillis(),
        )
        if (ndefRecords.isEmpty() && pendingWrite == null) {
            _message.value = L10n.get(R.string.mgr_nfc_uid_read, uid)
        }
    }

    private fun shortName(full: String): String = full.substringAfterLast(".")

    private fun tnfName(tnf: Short): String = when (tnf.toInt()) {
        0x01 -> "TEXT"
        0x02 -> "URI"
        0x03 -> "EXTERNAL"
        0x04 -> "MIME"
        else -> "OTHER"
    }

    /** 供权限页 / 设置引导：打开系统 NFC 设置 */
    fun openNfcSettings() {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_NFC_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
