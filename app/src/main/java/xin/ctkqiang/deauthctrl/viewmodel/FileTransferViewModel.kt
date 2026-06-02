package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.FileTransferManager
import xin.ctkqiang.deauthctrl.manager.SendPeer
import java.io.File

/**
 * 文件快传 ViewModel
 *
 * 管理局域网文件传输的状态，继承 AndroidViewModel 以获取 Context（ContentResolver 读文件）。
 *
 * ## 状态流
 * - isRunning: 文件传输服务运行状态
 * - peers: 已发现的局域网设备列表
 * - received: 最近接收到的文件（用于 UI 显示），null=无新文件
 * - error: 错误信息
 * - sentCount: 已成功发送的文件计数
 */
class FileTransferViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = FileTransferManager()
    private val _isRunning = MutableStateFlow(false); val isRunning: StateFlow<Boolean> = _isRunning
    private val _peers = MutableStateFlow<List<SendPeer>>(emptyList()); val peers: StateFlow<List<SendPeer>> = _peers
    private val _received = MutableStateFlow<File?>(null); val received: StateFlow<File?> = _received
    private val _error = MutableStateFlow<String?>(null); val error: StateFlow<String?> = _error
    private val _sentCount = MutableStateFlow(0); val sentCount: StateFlow<Int> = _sentCount
    private var pollJob: Job? = null
    val localIp: String get() = manager.localIpAddress ?: "未知"

    init {
        manager.deviceName = android.os.Build.MODEL
        manager.onFileReceived = { _received.value = it }
    }

    /** 启动服务 + 每 500ms 轮询设备列表 */
    fun start() {
        val dir = File(getApplication<Application>().filesDir, "ft_received")
        if (manager.start(dir)) {
            _isRunning.value = true; _error.value = null
            pollJob = CoroutineScope(Dispatchers.IO).launch { while (isActive) { _peers.value = manager.peers.values.toList(); delay(500) } }
        } else _error.value = "启动失败 — 端口可能被占用"
    }

    /** 发送文件到所有已发现设备 + 手动IP */
    fun sendFile(uri: Uri, manualIp: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val input = ctx.contentResolver.openInputStream(uri) ?: return@launch
                val fileName = ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)) else null
                } ?: uri.lastPathSegment ?: "file"
                val tmp = File(ctx.cacheDir, fileName)
                tmp.outputStream().use { input.copyTo(it) }; input.close()

                val targetIps = if (manualIp.isNotBlank()) listOf(manualIp.trim()) else manager.peers.values.map { it.ip }
                if (targetIps.isEmpty()) { _error.value = "未发现设备，请输入对方IP"; return@launch }
                var ok = 0; val total = targetIps.size
                targetIps.forEach { ip -> if (manager.sendFile(tmp, ip)) ok++ }
                withContext(Dispatchers.Main) {
                    _sentCount.value = ok
                    if (ok == 0) _error.value = "发送失败 — 检查对方IP和网络"
                    else _error.value = null
                }
                tmp.delete()
            } catch (e: Exception) { _error.value = e.message }
        }
    }

    /** 停止服务 */
    fun stop() { manager.stop(); pollJob?.cancel(); _isRunning.value = false }
    /** 清除已接收文件状态 */
    fun clearReceived() { _received.value = null }
    override fun onCleared() { super.onCleared(); manager.stop(); pollJob?.cancel() }
}
