package xin.ctkqiang.deauthctrl.manager

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * NFC 卡模拟服务 —— HostApduService
 *
 * 当钱包里设置了活跃卡，本服务在 NFC 读卡器靠近时以 ISO 14443-4 (ISO-DEP)
 * APDU 协议应答：
 * - SELECT AID（00A40400…）→ 0x9000 成功
 * - GET DATA（00CA0000…）→ 返回卡片数据（名称 + 数据 hex）
 * - 其余指令 → 0x6A00（不支持）
 *
 * 使用前需在设置页的「NFC 工作台」→ 钱包中：添加卡片 → 设为活跃。
 * 模拟的是自定义 APDU 卡，适用于授权研究场景的读卡器对接；
 * 无法模拟 MIFARE Classic 门禁卡或支付卡（见 CardWalletManager 说明）。
 */
class CardEmulationService : HostApduService() {

    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        if (apdu.size < 4) return UNKNOWN_CMD
        val cla = apdu[0].toInt() and 0xFF
        val ins = apdu[1].toInt() and 0xFF

        return try {
            when {
                // SELECT (by AID): 00 A4 04 00 Lc AID
                cla == 0x00 && ins == 0xA4 -> SELECT_OK
                // GET DATA: 00 CA 00 00 00
                cla == 0x00 && ins == 0xCA -> {
                    val card = CardWalletManager(this).activeCard()
                    if (card == null) {
                        byteArrayOf(0x6A.toByte(), 0x82.toByte())
                    } else {
                        val payload = ("DEAUTHCTRL|${card.name}|${card.dataHex}")
                            .toByteArray(Charsets.US_ASCII)
                        byteArrayOf(0x90.toByte(), 0x00.toByte()) + payload
                    }
                }
                else -> UNKNOWN_CMD
            }
        } catch (t: Throwable) {
            Log.w(TAG, "APDU 处理失败", t)
            UNKNOWN_CMD
        }
    }

    override fun onDeactivated(reason: Int) {
        // 读卡器移开：无需清理状态,活跃卡数据来自钱包持久层
    }

    companion object {
        private const val TAG = "CardEmulation"
        private val SELECT_OK = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val UNKNOWN_CMD = byteArrayOf(0x6A.toByte(), 0x00.toByte())
    }
}
