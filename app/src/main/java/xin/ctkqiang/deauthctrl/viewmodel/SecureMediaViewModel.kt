package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
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
    private val _passphrase = MutableStateFlow("deauthctrl")
    val passphrase: StateFlow<String> = _passphrase
    private val _pendingCameraUri = MutableStateFlow<Uri?>(null)
    val pendingCameraUri: StateFlow<Uri?> = _pendingCameraUri

    fun setPassphrase(p: String) { _passphrase.value = p }

    fun createPhotoIntent(): Intent {
        val ctx = getApplication<Application>()
        val file = File(ctx.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        _pendingCameraUri.value = uri
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { putExtra(MediaStore.EXTRA_OUTPUT, uri); addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    }

    fun createVideoIntent(): Intent {
        val ctx = getApplication<Application>()
        val file = File(ctx.cacheDir, "video_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        _pendingCameraUri.value = uri
        return Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply { putExtra(MediaStore.EXTRA_OUTPUT, uri); addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    }

    fun onMediaCaptured() {
        val uri = _pendingCameraUri.value ?: return
        _pendingCameraUri.value = null
        val ctx = getApplication<Application>()
        val path = uri.path ?: return
        val file = File(path)
        if (!file.exists() || file.length() == 0L) { _error.value = "文件为空"; return }
        val type = if (file.name.contains("photo")) "photo" else "video"
        encryptFile(file, type)
    }

    fun encryptFile(file: File, type: String) {
        viewModelScope.launch {
            val pwd = _passphrase.value.trim()
            val entry = withContext(Dispatchers.IO) { manager.encryptAndSave(file, pwd, type) }
            if (entry != null) { _entries.value = manager.getEntries(); _error.value = null }
            else _error.value = "加密失败"
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val input = ctx.contentResolver.openInputStream(uri) ?: return@launch
                val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)) else null
                } ?: uri.lastPathSegment ?: "file"
                val type = when { name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg") -> "photo"
                    name.endsWith(".mp4") || name.endsWith(".mov") -> "video"
                    else -> "audio" }
                val tmp = File(ctx.cacheDir, name)
                tmp.outputStream().use { input.copyTo(it) }; input.close()
                encryptFile(tmp, type)
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun startRecording() {
        try {
            currentAudioFile = manager.createTempAudioFile()
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) MediaRecorder(getApplication()) else MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100); setAudioEncodingBitRate(128000)
                setOutputFile(currentAudioFile!!.absolutePath)
                prepare(); start()
            }
            _isRecording.value = true
        } catch (e: Exception) { _error.value = "录音失败: ${e.message}" }
    }

    fun stopRecording() {
        try { mediaRecorder?.apply { stop(); release() } } catch (_: Exception) {}
        mediaRecorder = null; _isRecording.value = false
        currentAudioFile?.let { encryptFile(it, "audio") }
    }

    fun decrypt(entry: SecureMediaEntry) {
        val file = manager.decryptToTemp(entry, _passphrase.value.trim())
        if (file != null) _decryptedFile.value = file
        else _error.value = "密码错误或文件损坏"
    }

    fun delete(entry: SecureMediaEntry) { manager.delete(entry); _entries.value = manager.getEntries(); _decryptedFile.value = null }
    fun clearDecrypted() { _decryptedFile.value?.delete(); _decryptedFile.value = null }

    override fun onCleared() {
        super.onCleared()
        try { mediaRecorder?.release() } catch (_: Exception) {}
        _decryptedFile.value?.delete()
    }
}
