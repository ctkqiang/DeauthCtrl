package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.BleMeshManager
import xin.ctkqiang.deauthctrl.manager.BleMeshState

/**
 * BLE 网格聊天 ViewModel —— 持有 [BleMeshManager]，StateFlow 原样透传
 *
 * 广播器与扫描器的生命周期完全由管理器负责：
 * 页面进入 / 离开通过 DisposableEffect 调用 start / stop；
 * onCleared 再兜底 destroy()，取消后台广播协程并收回广播会话。
 */
class BleMeshViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = BleMeshManager(application)

    val state: StateFlow<BleMeshState> = manager.state

    /**
     * 启动网格
     *
     * @return false 表示前置校验失败（权限 / 蓝牙开关 / 硬件不支持），原因在 state.error
     */
    fun start(): Boolean = manager.start()

    fun stop() = manager.stop()

    /**
     * 发送消息 —— 未启动或内容为空时返回 false，原因在 state.error
     */
    fun send(text: String): Boolean = manager.send(text)

    override fun onCleared() {
        manager.destroy()
        super.onCleared()
    }
}
