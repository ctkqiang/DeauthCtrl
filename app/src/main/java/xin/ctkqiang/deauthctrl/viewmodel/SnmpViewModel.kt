package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.DEFAULT_SNMP_COMMUNITIES
import xin.ctkqiang.deauthctrl.manager.SnmpManager
import xin.ctkqiang.deauthctrl.manager.SnmpState

/**
 * SNMP 社区串扫描 ViewModel —— 持有 [SnmpManager]，扫描状态原样透传
 *
 * 目标与社区串字典由界面直接编辑；[start] 把当前输入交给管理器，
 * 管理器内部先取消上一轮扫描再启动新的一轮。目标是单 IP 或 CIDR 网段，
 * 社区串以逗号分隔（兼容中文逗号、分号与换行）。
 *
 * start / stop 由页面按钮调用；页面 DisposableEffect 在离开时调用 [stop]，
 * [onCleared] 兜底关闭所有在途 UDP socket，防止 ViewModel 回收后仍有
 * 2 秒超时的探测线程驻留。
 */
class SnmpViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = SnmpManager()

    /** 扫描状态：scanning / progress / results / error */
    val state: StateFlow<SnmpState> = manager.state

    private val _target = MutableStateFlow("")

    /** 目标输入框内容 */
    val target: StateFlow<String> = _target

    private val _communitiesText =
        MutableStateFlow(DEFAULT_SNMP_COMMUNITIES.joinToString(", "))

    /** 社区串输入框内容（逗号分隔的原始文本） */
    val communitiesText: StateFlow<String> = _communitiesText

    fun setTarget(value: String) {
        _target.value = value
    }

    fun setCommunities(value: String) {
        _communitiesText.value = value
    }

    /** 恢复默认社区串字典（仍可继续编辑） */
    fun useDefaultCommunities() {
        _communitiesText.value = DEFAULT_SNMP_COMMUNITIES.joinToString(", ")
    }

    /** 启动扫描；空目标 / 空社区串由管理器写入可读错误 */
    fun start() {
        manager.scan(_target.value, parseCommunities(_communitiesText.value))
    }

    fun stop() = manager.stop()

    /** 清空上一轮结果、进度与错误 */
    fun clear() = manager.reset()

    /** 逗号 / 中文逗号 / 分号 / 换行分隔，去空白并去重 */
    private fun parseCommunities(text: String): List<String> =
        text.split(',', '，', ';', '；', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    override fun onCleared() {
        manager.shutdown()
        super.onCleared()
    }
}
