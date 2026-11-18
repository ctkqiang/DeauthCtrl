package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.GnssSkyManager
import xin.ctkqiang.deauthctrl.manager.GnssSkyState

/**
 * GNSS 星座天图 ViewModel —— 持有 [GnssSkyManager],StateFlow 原样透传
 *
 * start/stop 由页面 DisposableEffect 调用;onCleared 兜底注销系统回调,
 * 防止 Activity 结束时 GNSS 状态回调仍持有本管理器。
 */
class GnssSkyViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = GnssSkyManager(application)

    val state: StateFlow<GnssSkyState> = manager.state

    fun start() = manager.start()

    fun stop() = manager.stop()

    override fun onCleared() {
        manager.stop()
        super.onCleared()
    }
}
