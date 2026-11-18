package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.SonicLinkManager
import xin.ctkqiang.deauthctrl.manager.SonicState

/**
 * 声波数据链 ViewModel —— 持有 [SonicLinkManager]
 *
 * 页面离开时调用 [stopAll]，确保 AudioTrack 与 AudioRecord 都被释放；
 * onCleared 再兜底一次，避免 ViewModel 被回收时麦克风仍占用。
 */
class SonicLinkViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = SonicLinkManager()
    val state: StateFlow<SonicState> = manager.state

    fun send(text: String) = manager.send(text)
    fun stopSending() = manager.stopSending()
    fun startReceiving() = manager.startReceiving()
    fun stopReceiving() = manager.stopReceiving()
    fun stopAll() = manager.stopAll()
    fun clearReceived() = manager.clearReceived()

    override fun onCleared() {
        super.onCleared()
        manager.stopAll()
    }
}
