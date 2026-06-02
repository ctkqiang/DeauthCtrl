package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密媒体条目数据类
 *
 * 表示保险箱中一条已加密存储的媒体记录。
 *
 * @property id 唯一标识符（Unix 毫秒时间戳）
 * @property type 媒体类型："photo"（照片）、"video"（视频）、"audio"（录音）
 * @property fileName 加密存储文件名（{timestamp}.enc）
 * @property timestamp 创建时间戳（毫秒）
 * @property sizeBytes 原始明文文件大小（字节）
 */
data class SecureMediaEntry(
    val id: Long,
    val type: String,
    val fileName: String,
    val timestamp: Long,
    val sizeBytes: Long,
)

/**
 * AES-256-GCM 加密媒体管理器
 *
 * 实现照片/视频/语音的密码保护加密存储。
 * 使用行业标准 AES-256-GCM 认证加密模式，同时提供机密性和完整性校验。
 *
 * ## 加密方案
 * - **密钥派生**: PBKDF2WithHmacSHA256，10,000 次迭代，256-bit 输出
 * - **认证加密**: AES/GCM/NoPadding，128-bit GCM 认证标签
 * - **随机盐**: 16 字节 SecureRandom，每次加密独立生成
 * - **随机 IV**: 12 字节 SecureRandom（GCM 推荐长度）
 *
 * ## .enc 文件结构（二进制）
 * ```
 * [16 bytes: Salt] [12 bytes: IV] [N bytes: AES-GCM Ciphertext + 16 bytes GCM Tag]
 * ```
 *
 * ## 元数据存储
 * meta.json 使用管道分隔的纯文本格式，每行一条记录：
 * ```
 * id|type|fileName|timestamp|sizeBytes
 * ```
 *
 * ## 安全保证
 * - 密码不存储在设备上（每次操作由用户输入）
 * - 相同密码 + 相同明文 → 不同的密文（随机盐 + 随机 IV）
 * - GCM 标签在解密时自动校验（篡改检测）
 * - 原始明文文件加密后立即删除
 */
class SecureMediaManager(private val context: Context) {

    /** 加密文件存储目录（app 内部私有存储） */
    private val storageDir = File(context.filesDir, "secure_media")

    /** 元数据索引文件（管道分隔文本） */
    private val metaFile = File(storageDir, "meta.json")

    /** 内存中的媒体条目列表 */
    private val entries = mutableListOf<SecureMediaEntry>()

    init {
        storageDir.mkdirs()
        loadMeta()
    }

    /**
     * 从 meta.json 加载媒体索引到内存
     *
     * 格式: id|type|fileName|timestamp|sizeBytes
     */
    private fun loadMeta() {
        if (!metaFile.exists()) return
        try {
            val json = metaFile.readText()
            if (json.isNotBlank()) {
                val items = json.lines().filter { it.isNotBlank() }.map { line ->
                    val p = line.split("|")
                    SecureMediaEntry(p[0].toLong(), p[1], p[2], p[3].toLong(), p[4].toLong())
                }
                entries.addAll(items)
            }
        } catch (_: Exception) {}
    }

    /** 将内存中的媒体索引持久化到 meta.json */
    private fun saveMeta() {
        val json = entries.joinToString("\n") {
            "${it.id}|${it.type}|${it.fileName}|${it.timestamp}|${it.sizeBytes}"
        }
        metaFile.writeText(json)
    }

    /**
     * 获取所有媒体条目（按时间倒序，最新在前）
     *
     * @return 已排序的媒体条目列表
     */
    fun getEntries(): List<SecureMediaEntry> = entries.sortedByDescending { it.timestamp }

    /**
     * AES-256-GCM 加密并保存文件
     *
     * 1. 读取明文文件全部字节
     * 2. 生成 16 字节随机盐 + 12 字节随机 IV
     * 3. PBKDF2 派生 256-bit AES 密钥
     * 4. GCM 模式加密
     * 5. 写入 [Salt][IV][Ciphertext] 到 .enc 文件
     * 6. 删除原始明文文件
     *
     * @param sourceFile 原始明文文件
     * @param passphrase 用户密码（不存储，仅用于本次加密）
     * @param type 媒体类型标签
     * @return 加密成功后返回 SecureMediaEntry，失败返回 null
     */
    fun encryptAndSave(sourceFile: File, passphrase: String, type: String): SecureMediaEntry? {
        try {
            val plainBytes = sourceFile.readBytes()
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(plainBytes)

            val entry = SecureMediaEntry(
                id = System.currentTimeMillis(),
                type = type,
                fileName = "${System.currentTimeMillis()}.enc",
                timestamp = System.currentTimeMillis(),
                sizeBytes = plainBytes.size.toLong(),
            )

            val outFile = File(storageDir, entry.fileName)
            FileOutputStream(outFile).use { fos ->
                fos.write(salt)
                fos.write(iv)
                fos.write(ciphertext)
            }

            entries.add(entry)
            saveMeta()
            sourceFile.delete()
            return entry
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 解密媒体文件到临时目录
     *
     * 1. 读取 .enc 文件 → 拆分 Salt + IV + Ciphertext
     * 2. 从密码派生密钥
     * 3. GCM 解密（密码错误或文件损坏 → 返回 null）
     * 4. 写入临时明文文件（cacheDir，扩展名根据 type 还原）
     *
     * @param entry 要解密的媒体条目
     * @param passphrase 用户密码
     * @return 成功返回临时明文文件，密码错误/篡改返回 null
     */
    fun decryptToTemp(entry: SecureMediaEntry, passphrase: String): File? {
        try {
            val encFile = File(storageDir, entry.fileName)
            if (!encFile.exists()) return null

            val allBytes = encFile.readBytes()
            val salt = allBytes.copyOfRange(0, 16)
            val iv = allBytes.copyOfRange(16, 28)
            val ciphertext = allBytes.copyOfRange(28, allBytes.size)

            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plainBytes = cipher.doFinal(ciphertext)

            val ext = when (entry.type) {
                "photo" -> ".jpg"
                "video" -> ".mp4"
                "audio" -> ".m4a"
                else -> ".bin"
            }
            val tempFile = File(context.cacheDir, "decrypt_${entry.id}$ext")
            tempFile.writeBytes(plainBytes)
            return tempFile
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * 删除加密媒体条目
     *
     * 同时删除 .enc 文件和索引记录。
     *
     * @param entry 要删除的条目
     */
    fun delete(entry: SecureMediaEntry) {
        File(storageDir, entry.fileName).delete()
        entries.removeAll { it.id == entry.id }
        saveMeta()
    }

    /**
     * PBKDF2 密钥派生
     *
     * PBKDF2WithHmacSHA256, 10,000 次迭代, 输出 256-bit AES 密钥。
     *
     * @param passphrase 用户密码
     * @param salt 16 字节随机盐
     * @return AES-256 SecretKeySpec
     */
    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 10_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * 创建用于 MediaRecorder 录音的临时文件
     *
     * 文件位于 cacheDir/temp_media/，扩展名 .m4a。
     */
    fun createTempAudioFile(): File {
        val dir = File(context.cacheDir, "temp_media")
        dir.mkdirs()
        return File(dir, "recording_${System.currentTimeMillis()}.m4a")
    }
}
