package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.SeismoState
import xin.ctkqiang.deauthctrl.manager.SeismographManager

/**
 * 震动监视 ViewModel —— 持有 [SeismographManager],StateFlow 原样透传
 *
 * 页面用 start / shutdown 管理传感器可见期;阈值与日志清理直接透传。
 * onCleared 时无条件停止监听,避免页面销毁后加速度计仍在耗电。
 */
class SeismographViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = SeismographManager(application)

    val state: StateFlow<SeismoState> = manager.state

    fun start() = manager.start()
    fun stop() = manager.stop()
    fun shutdown() = manager.shutdown()
    fun setThreshold(value: Float) = manager.setThreshold(value)
    fun clearEvents() = manager.clearEvents()

    override fun onCleared() {
        super.onCleared()
        manager.shutdown()
    }
}
