package xin.ctkqiang.deauthctrl.manager

import java.security.SecureRandom
import kotlin.math.ln

/**
 * 密码生成器 —— SecureRandom 高熵随机
 *
 * 三种字符集:十六进制 / Base64 / 字母数字符号。熵值按
 * `bits = length × log2(charsetSize)` 计算,是理论上限而非
 * 可读性评估;密码强弱最终取决于目标系统的口令策略。
 */
object KeyGen {

    enum class Mode(val charset: String) {
        HEX("0123456789abcdef"),
        BASE64("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"),
        MIXED("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+[]{};:,.<>?"),
    }

    private val random = SecureRandom()

    fun generate(mode: Mode, length: Int): String {
        val sb = StringBuilder(length)
        repeat(length) { sb.append(mode.charset[random.nextInt(mode.charset.length)]) }
        return sb.toString()
    }

    fun entropyBits(mode: Mode, length: Int): Double = length * (ln(mode.charset.length.toDouble()) / ln(2.0))
}
