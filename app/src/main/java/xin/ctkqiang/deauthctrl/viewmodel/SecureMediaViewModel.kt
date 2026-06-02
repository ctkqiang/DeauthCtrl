package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import android.media.MediaRecorder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xin.ctkqiang.deauthctrl.manager.SecureMediaEntry
import xin.ctkqiang.deauthctrl.manager.SecureMediaManager
import java.io.File

/**
 * 加密媒体保险箱 ViewModel
 *
 * 管理密码保护相册/摄像/录音的完整生命周期。
 * 继承 AndroidViewModel 以获取 Context（ContentResolver 读文件、MediaRecorder 录音）。
 *
 * ## 状态流
 * - isRecording: 是否正在录音（MediaRecorder 活跃）
 * - isLocked: 保险箱锁定状态（需密码解锁）
 * - entries: 已加密的媒体条目列表
 * - decryptedFile: 最近解密后的临时明文文件（用于查看/播放），null=未解密
 * - error: 最近一次操作的错误信息
 * - passphrase: 当前输入的密码（双向绑定）
 *
 * ## 录音参数
 * MediaRecorder → MPEG-4 容器 + AAC-LC 编码, 44100Hz, 128kbps
 */
class SecureMediaViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = SecureMediaManager(application)
    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    private val _entries = MutableStateFlow(manager.getEntries())
    val entries: StateFlow<List<SecureMediaEntry>> = _entries
    private val _decryptedFile = MutableStateFlow<File?>(null)
    val decryptedFile: StateFlow<File?> = _decryptedFile
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked
    private val _passphrase = MutableStateFlow("")
    val passphrase: StateFlow<String> = _passphrase

    /** 设置密码（双向绑定） */
    fun setPassphrase(p: String) { _passphrase.value = p }

    /**
     * 验证密码并解锁保险箱
     *
     * 用输入的密码尝试解密第一条媒体条目来验证密码正确性。
     * 若保险箱为空（无任何媒体），直接解锁。
     * 密码错误 → 设置 error = "密码错误"。
     */
    fun unlock() {
        val pwd = _passphrase.value.trim()
        if (pwd.isEmpty()) { _error.value = "请输入密码"; return }
        val entries = manager.getEntries()
        if (entries.isEmpty()) {
            _isLocked.value = false
            _error.value = null
            return
        }
        val test = manager.decryptToTemp(entries.first(), pwd)
        if (test != null) {
            _isLocked.value = false
            _error.value = null
            test.delete()
        } else {
            _error.value = "密码错误"
        }
    }

    /**
     * 加密并导入媒体文件
     *
     * 从 Content URI（系统相机/文件选择器返回）读取文件内容，
     * 写入临时文件，然后用当前密码加密保存。
     *
     * @param uri content:// URI
     * @param type 媒体类型（photo/video/audio）
     */
    fun importAndEncrypt(uri: Uri, type: String) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val input = ctx.contentResolver.openInputStream(uri) ?: return@launch
                val tmpFile = File(ctx.cacheDir, "import_${System.currentTimeMillis()}")
                tmpFile.outputStream().use { input.copyTo(it) }
                input.close()

                val pwd = _passphrase.value.trim()
                val entry = withContext(Dispatchers.IO) { manager.encryptAndSave(tmpFile, pwd, type) }
                if (entry != null) {
                    _entries.value = manager.getEntries()
                    _error.value = null
                } else {
                    _error.value = "加密失败"
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    /**
     * 开始录音
     *
     * MediaRecorder 配置：MIC 音源 → MPEG-4 容器 → AAC 编码 → 44100Hz → 128kbps。
     * 录音文件写入临时 m4a 文件，停止后自动加密保存。
     */
    fun startRecording() {
        try {
            currentAudioFile = manager.createTempAudioFile()
            val path = currentAudioFile!!.absolutePath
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(getApplication())
            } else {
                MediaRecorder()
            }
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(path)
                prepare()
                start()
            }
            _isRecording.value = true
        } catch (e: Exception) {
            _error.value = "录音启动失败: ${e.message}"
        }
    }

    /**
     * 停止录音并加密保存
     *
     * 停止 MediaRecorder → 释放资源 → 用当前密码加密录音文件 → 加入媒体列表。
     */
    fun stopRecording() {
        try {
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
            _isRecording.value = false

            currentAudioFile?.let { file ->
                viewModelScope.launch {
                    val pwd = _passphrase.value.trim()
                    val entry = withContext(Dispatchers.IO) { manager.encryptAndSave(file, pwd, "audio") }
                    if (entry != null) {
                        _entries.value = manager.getEntries()
                    } else {
                        _error.value = "加密保存失败"
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * 解密媒体条目到临时文件
     *
     * 解密成功后 decryptedFile 自动更新，UI 层可据此打开图片/视频/音频。
     *
     * @param entry 要解密的条目
     */
    fun decrypt(entry: SecureMediaEntry) {
        val pwd = _passphrase.value.trim()
        val file = manager.decryptToTemp(entry, pwd)
        if (file != null) {
            _decryptedFile.value = file
        } else {
            _error.value = "解密失败 — 密码错误或文件损坏"
        }
    }

    /** 删除加密媒体条目 */
    fun delete(entry: SecureMediaEntry) {
        manager.delete(entry)
        _entries.value = manager.getEntries()
        _decryptedFile.value = null
    }

    /** 清除解密临时文件（查看完毕后调用） */
    fun clearDecrypted() { _decryptedFile.value?.delete(); _decryptedFile.value = null }

    override fun onCleared() {
        super.onCleared()
        try { mediaRecorder?.release() } catch (_: Exception) {}
        _decryptedFile.value?.delete()
    }
}
