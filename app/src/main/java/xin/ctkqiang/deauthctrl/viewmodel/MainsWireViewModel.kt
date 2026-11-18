package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.MainsState
import xin.ctkqiang.deauthctrl.manager.MainsWireDetector

/**
 * 墙内电线探测 ViewModel
 *
 * 持有 [MainsWireDetector],把采集状态以 StateFlow 透传给页面。
 * 页面销毁时停止麦克风采集,避免后台占用。
 */
class MainsWireViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = MainsWireDetector(application)
    val state: StateFlow<MainsState> = manager.state

    fun start() = manager.start()
    fun stop() = manager.stop()
    fun setFreqMode(mode: Int) = manager.setFreqMode(mode)

    override fun onCleared() {
        super.onCleared()
        manager.stop()
    }
}
