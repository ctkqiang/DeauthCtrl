package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.PdrHomingManager
import xin.ctkqiang.deauthctrl.manager.PdrState

/**
 * 步测归航 ViewModel —— 持有 [PdrHomingManager],StateFlow 原样透传
 *
 * 页面用 attach / detach 管理传感器可见期,start / pause / reset 管理推演进程;
 * onCleared 时无条件释放传感器,避免页面销毁后仍占着计步器与旋转矢量。
 */
class PdrHomingViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = PdrHomingManager(application)

    val state: StateFlow<PdrState> = manager.state

    fun attach() = manager.attach()
    fun detach() = manager.detach()
    fun start() = manager.start()
    fun pause() = manager.pause()
    fun reset() = manager.reset()
    fun setStepLen(meters: Float) = manager.setStepLen(meters)

    override fun onCleared() {
        super.onCleared()
        manager.shutdown()
    }
}
