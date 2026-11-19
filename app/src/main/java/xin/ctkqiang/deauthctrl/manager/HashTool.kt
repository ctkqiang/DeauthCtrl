package xin.ctkqiang.deauthctrl.manager

import java.io.InputStream
import java.security.MessageDigest

/**
 * 哈希工坊 —— 文本与文件的摘要计算
 *
 * SHA-256 / SHA-1 / MD5 三档,块读流式消化,大文件不吃内存。
 * MD5/SHA-1 仅用于校验与兼容性用途,不用于安全断言。
 */
object HashTool {

    val ALGORITHMS = listOf("SHA-256", "SHA-1", "MD5")

    fun digestText(algorithm: String, text: String): String =
        hex(MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8)))

    fun digestStream(algorithm: String, input: InputStream, onProgress: ((Long) -> Unit)? = null): String {
        val md = MessageDigest.getInstance(algorithm)
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
            total += n
            onProgress?.invoke(total)
        }
        return hex(md.digest())
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
