package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.DEFAULT_SCOPE_CHANNELS
import xin.ctkqiang.deauthctrl.manager.LogicScopeManager
import xin.ctkqiang.deauthctrl.manager.SCOPE_MAX_CHANNELS
import xin.ctkqiang.deauthctrl.manager.ScopeState

/**
 * 逻辑示波 ViewModel —— 持有 [LogicScopeManager]，状态与通道选择原样透传
 *
 * 通道选择只在停止状态下可改：运行中改变选择需要重启 BLE 扫描线程，Android 对
 * startScan 有 30 秒内最多 5 次的节流，频繁切换反而会引入系统级失败。这里把
 * 「先停止再切换」做成显式约束，界面上同步提示。
 *
 * start / shutdown 由页面 DisposableEffect 调用；onCleared 兜底停止所有通道线程，
 * 防止 ViewModel 回收后麦克风或 BLE 扫描仍在后台运行。
 */
class LogicScopeViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = LogicScopeManager(application)

    val state: StateFlow<ScopeState> = manager.state

    /** 用户选择的通道（默认 BLE 脉冲 + 音频） */
    private val _selected = MutableStateFlow(DEFAULT_SCOPE_CHANNELS)
    val selected: StateFlow<Set<String>> = _selected

    /** 同屏通道上限，界面提示用 */
    val maxChannels: Int = SCOPE_MAX_CHANNELS

    /**
     * 切换一个通道的选中状态
     *
     * 运行中直接忽略（界面另有提示）；至少保留 1 个通道，最多 [SCOPE_MAX_CHANNELS] 个。
     */
    fun toggleChannel(id: String) {
        if (state.value.running) return
        val current = _selected.value
        _selected.value = when {
            id in current -> if (current.size <= 1) current else current - id
            current.size >= SCOPE_MAX_CHANNELS -> current
            else -> current + id
        }
    }

    /** 启动采集：使用当前选择；若选择为空则回到默认通道，保证 start 永远有效 */
    fun start() {
        val selection = if (_selected.value.isEmpty()) DEFAULT_SCOPE_CHANNELS else _selected.value
        _selected.value = selection
        manager.start(selection)
    }

    fun stop() = manager.stop()

    /** 页面销毁 / ViewModel 回收：停止并释放所有通道资源 */
    fun shutdown() = manager.shutdown()

    /** 清空轨迹与累计计数 */
    fun clear() = manager.clear()

    /**
     * 把一段真实红外发射脉冲图案喂给示波器（微秒、奇偶交替开/关，首值为开）
     *
     * 预留给主线程在 IrRemoteManager.transmitRaw 成功后调用；运行且选中红外通道时生效。
     */
    fun submitIrPattern(patternUs: List<Int>, label: String = "红外发射") =
        manager.submitIrPattern(patternUs, label)

    override fun onCleared() {
        manager.shutdown()
        super.onCleared()
    }
}
