package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.NbtScanManager
import xin.ctkqiang.deauthctrl.manager.NetBiosState

/**
 * NetBIOS 名称扫描 ViewModel —— 持有 [NbtScanManager],StateFlow 原样透传
 *
 * 扫描由页面按钮触发,不随页面进入自动开始(避免误扫整个网段);
 * 页面离开时通过 DisposableEffect 调用 stop,onCleared 再兜底 destroy,
 * 取消探测协程并释放固定线程池。
 */
class NetBiosViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = NbtScanManager()

    val state: StateFlow<NetBiosState> = manager.state

    /**
     * 启动一轮扫描
     *
     * @return false 表示目标格式无效 / 本机没有局域网地址,原因在 state.error
     */
    fun scan(target: String): Boolean = manager.start(target)

    /** 停止当前扫描(已完成部分的结果保留) */
    fun stop() = manager.stop()

    override fun onCleared() {
        manager.destroy()
        super.onCleared()
    }
}
