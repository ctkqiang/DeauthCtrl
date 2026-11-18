package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.manager.HardwareSelfCheck
import xin.ctkqiang.deauthctrl.manager.SelfCheckState

/**
 * 本机硬件自检 ViewModel
 *
 * 持有 [HardwareSelfCheck],把真实系统快照以 StateFlow 透传给页面。
 * 刷新读取包含 250ms 的 CPU 采样窗口,放在 viewModelScope 中执行,不阻塞主线程。
 */
class SelfCheckViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = HardwareSelfCheck(application)

    val state: StateFlow<SelfCheckState> = manager.state

    /** 触发一次完整自检(并发调用由 manager 内部去重) */
    fun refresh() {
        viewModelScope.launch { manager.refresh() }
    }
}
