package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import xin.ctkqiang.deauthctrl.manager.TerminalManager
import xin.ctkqiang.deauthctrl.terminal.ShellMode
import xin.ctkqiang.deauthctrl.terminal.TerminalSpecs

/**
 * 本地终端 ViewModel。
 *
 * 持有唯一的 [TerminalManager]（配置变更时由 ViewModel 保留，进程内同一
 * 时刻至多一个 shell 会话）。渲染由 Termux 的 `TerminalView` 内部完成，
 * 本类只暴露生命周期状态：状态机、标题、复制模式、铃声事件。
 *
 * [isRunning] 供主页入口的在线指示灯使用（Starting 也算在线，避免
 * “连接中”时指示灯闪烁不定）。
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    /** 终端内核；UI 通过它读取会话、绑定视图。 */
    val manager = TerminalManager(application)

    /** 会话状态机。 */
    val status: StateFlow<TerminalManager.Status> = manager.status

    /**
     * 会话是否在线。
     *
     * Running / Starting 都视为在线；主页入口据此点亮指示灯，
     * 快捷开关在“挂断”与“连接”之间切换。
     */
    val isRunning: StateFlow<Boolean> = manager.status
        .map { it is TerminalManager.Status.Running || it is TerminalManager.Status.Starting }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 终端标题（OSC 0/2）。 */
    val title: StateFlow<String?> = manager.title

    /** 文本选择 / 复制模式是否激活。 */
    val copyMode: StateFlow<Boolean> = manager.copyMode

    /** 终端响铃事件。 */
    val bell: SharedFlow<Unit> = manager.bell

    /** 设备是否有可用的 su。 */
    val rootAvailable: Boolean get() = manager.rootAvailable

    /** 启动会话（默认沿用上次模式，首次为 LOCAL）。 */
    fun start() = start(manager.mode)

    /** 以指定模式启动会话；重复调用会先终止旧会话。 */
    fun start(mode: ShellMode) {
        // 先确保 HOME 存在；native 层 chdir 失败时自身会回退到 /，
        // 这里只是让“家目录 + profile”路径在最常见场景下可用。
        runCatching { TerminalSpecs.homeDir(getApplication()).mkdirs() }
        manager.start(mode)
    }

    /** 终止会话（SIGKILL；未 spawn 的会话直接废弃）。 */
    fun stop() {
        manager.stop()
    }

    /** 清空屏幕与回滚，并向 shell 发送 Ctrl-L 重绘。 */
    fun clear() {
        manager.clear()
    }

    /** 向 shell 发送文本（UTF-8）。 */
    fun send(text: String) {
        manager.send(text)
    }

    override fun onCleared() {
        manager.dispose()
        super.onCleared()
    }
}
