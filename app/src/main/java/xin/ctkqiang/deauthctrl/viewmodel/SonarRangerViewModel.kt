package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.SonarRangerManager
import xin.ctkqiang.deauthctrl.manager.SonarState

/**
 * 声呐测距 ViewModel —— 持有 [SonarRangerManager]
 *
 * 管理器只在页面存活期间工作:页面进入时 start,离开时 stop 释放
 * AudioTrack 与 AudioRecord;onCleared 再兜底一次,避免 ViewModel
 * 被回收时扬声器 / 麦克风仍被占用。
 */
class SonarRangerViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = SonarRangerManager(application)

    val state: StateFlow<SonarState> = manager.state

    fun start() = manager.start()

    fun stop() = manager.stop()

    override fun onCleared() {
        manager.stop()
        super.onCleared()
    }
}
