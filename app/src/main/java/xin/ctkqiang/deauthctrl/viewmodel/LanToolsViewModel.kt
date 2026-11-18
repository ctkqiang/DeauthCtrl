package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.FlingPipe
import xin.ctkqiang.deauthctrl.manager.FlingState
import xin.ctkqiang.deauthctrl.manager.WolManager
import xin.ctkqiang.deauthctrl.manager.WolState
import java.io.File

/**
 * 局域网工具 ViewModel —— 持有 [WolManager] 与 [FlingPipe]，StateFlow 原样透传
 *
 * 本类只做委托与 URI 适配：
 * - WOL 的地址探测、魔术包构建与 UDP 发送全在 [WolManager]；
 * - fling 的监听、读写与日志全在 [FlingPipe]；
 * - 文件选择返回的 content:// URI 由本类转成 InputStream 交给管道，
 *   接收文件落在应用私有目录 files/fling/ 下。
 *
 * 页面 DisposableEffect 离开时调用 [stopReceive] 停止监听；
 * onCleared 再兜底释放两个管理器的协程作用域。
 */
class LanToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val wol = WolManager()

    private val fling = FlingPipe(File(application.filesDir, "fling"))

    val wolState: StateFlow<WolState> = wol.state
    val flingState: StateFlow<FlingState> = fling.state

    /** 重新探测本机局域网 IP 与定向广播地址（网络切换后调用） */
    fun refreshNetwork() = wol.refreshNetwork()

    /** 发送 WOL 魔术包；[alsoPort7] 为 true 时追加端口 7 */
    fun wake(mac: String, alsoPort7: Boolean) = wol.wake(mac, alsoPort7)

    /** 启动 fling 接收端；[port] = 0 时使用内核临时端口 */
    fun startReceive(port: Int, saveToFile: Boolean) = fling.startReceive(port, saveToFile)

    /** 停止 fling 接收端 */
    fun stopReceive() = fling.stopReceive()

    /** 发送文本（UTF-8 原样字节） */
    fun sendText(host: String, port: Int, text: String) = fling.sendText(host, port, text)

    /**
     * 发送用户选择的文件。
     *
     * 文件大小来自 ContentResolver 的 OpenableColumns.SIZE，读不到按 0 处理
     * （界面显示已发送字节，不显示百分比）；文件名取自 URI 末段。
     */
    fun sendFile(host: String, port: Int, uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        var size = 0L

        runCatching {
            val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            resolver.query(uri, columns, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)?.let { name = it }
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        }

        fling.send(host, port, "文件 $name", size) { resolver.openInputStream(uri) }
    }

    override fun onCleared() {
        wol.destroy()
        fling.destroy()
        super.onCleared()
    }
}
