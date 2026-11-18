package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.AdField
import xin.ctkqiang.deauthctrl.manager.AdStructureDecoder
import xin.ctkqiang.deauthctrl.manager.BondManager
import xin.ctkqiang.deauthctrl.manager.BondState
import xin.ctkqiang.deauthctrl.manager.NusLineEnding
import xin.ctkqiang.deauthctrl.manager.NusState
import xin.ctkqiang.deauthctrl.manager.NusTerminalManager

/**
 * Nordic 工作台 ViewModel —— 持有 NUS 终端与绑定管理器，StateFlow 原样透传
 *
 * 三个标签页共用一份 ViewModel：
 * - 广告解析：纯 Kotlin 解码器，无 Android 依赖；
 * - NUS 终端：连接 / 断开 / 发送全部由 [NusTerminalManager] 负责；
 * - 绑定管理：列表与解绑由 [BondManager] 负责。
 *
 * 页面离开时通过 DisposableEffect 调用 [disconnect]，onCleared 再兜底销毁两个管理器。
 */
class NordicBenchViewModel(application: Application) : AndroidViewModel(application) {

    private val nusManager = NusTerminalManager(application)
    private val bondManager = BondManager(application)

    /** NUS 终端状态 */
    val nusState: StateFlow<NusState> = nusManager.state

    /** 已配对设备状态 */
    val bondState: StateFlow<BondState> = bondManager.state

    /**
     * 解码一段十六进制广播数据
     *
     * @return null 表示十六进制格式非法；空输入返回空列表
     */
    fun decodeAdvertising(hex: String): List<AdField>? =
        AdStructureDecoder.decodeHexText(hex)

    /**
     * 连接 NUS 目标
     *
     * @return false 表示 MAC / 权限 / 蓝牙开关校验失败或 connectGatt 调用失败
     */
    fun connect(mac: String): Boolean = nusManager.connect(mac)

    fun disconnect() = nusManager.disconnect()

    /** 发送文本；@return 请求是否被系统接受 */
    fun send(text: String, ending: NusLineEnding): Boolean =
        nusManager.send(text, ending)

    fun clearTerminal() = nusManager.clearTerminal()

    fun clearNusLog() = nusManager.clearLog()

    /** 重新读取系统已配对设备 */
    fun refreshBonds() = bondManager.refresh()

    /** 解绑指定设备；@return 反射调用是否返回成功 */
    fun removeBond(address: String): Boolean = bondManager.removeBond(address)

    override fun onCleared() {
        nusManager.destroy()
        bondManager.destroy()
        super.onCleared()
    }
}
