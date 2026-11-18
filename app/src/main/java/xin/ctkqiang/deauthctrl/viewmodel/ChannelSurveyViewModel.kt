package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.ChanSurveyState
import xin.ctkqiang.deauthctrl.manager.ChannelSurveyManager

/**
 * WiFi 信道占用 ViewModel —— 持有 [ChannelSurveyManager],StateFlow 原样透传
 *
 * start/stop 由页面 DisposableEffect 调用;onCleared 兜底停止扫描循环,
 * 防止 ViewModel 被回收后扫描协程仍持有 Context。
 */
class ChannelSurveyViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = ChannelSurveyManager(application)

    val state: StateFlow<ChanSurveyState> = manager.state

    fun start() = manager.start()

    fun stop() = manager.stop()

    override fun onCleared() {
        manager.stop()
        super.onCleared()
    }
}
