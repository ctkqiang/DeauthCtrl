package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.SshClientManager

/**
 * SSH 客户端 ViewModel —— 持有唯一 [SshClientManager],
 * 暴露连接状态、终端网格与屏幕版本号,并把表单字段透传给内核。
 */
class SshClientViewModel : ViewModel() {

    val manager = SshClientManager()

    val status: StateFlow<SshClientManager.Status> = manager.status
    val screenVersion: StateFlow<Long> = manager.screenVersion

    val host: StateFlow<String> = manager.host
    val port: StateFlow<String> = manager.port
    val username: StateFlow<String> = manager.username
    val password: StateFlow<String> = manager.password

    fun connect() = manager.connect()
    fun disconnect() = manager.disconnect()
    fun send(text: String) = manager.send(text)
    fun sendBytes(bytes: ByteArray) = manager.sendBytes(bytes)
    fun resize(cols: Int, rows: Int) = manager.resize(cols, rows)
    fun clear() = manager.clear()

    override fun onCleared() {
        super.onCleared()
        manager.disconnect()
    }
}
