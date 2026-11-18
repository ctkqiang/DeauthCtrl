package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.BleGattManager
import xin.ctkqiang.deauthctrl.manager.BleGattState

/**
 * BLE GATT 工作台 ViewModel —— 持有 [BleGattManager]，StateFlow 原样透传
 *
 * 连接 / 断开 / 读 / 写 / 订阅全部由管理器负责；页面进入时不自动连接
 * （GATT 连接需要用户明确指定目标 MAC），离开页面时通过 DisposableEffect
 * 调用 [disconnect]，onCleared 再兜底 [BleGattManager.destroy]。
 */
class BleGattViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = BleGattManager(application)

    val state: StateFlow<BleGattState> = manager.state

    /**
     * 连接目标设备
     *
     * @return false 表示 MAC / 权限 / 蓝牙开关校验失败或 connectGatt 调用失败，
     *   原因在 state.error 与日志中
     */
    fun connect(mac: String): Boolean = manager.connect(mac)

    fun disconnect() = manager.disconnect()

    /** 读取特征值；结果通过 StateFlow 与服务表值回传 */
    fun read(serviceUuid: String, characteristicUuid: String) =
        manager.read(serviceUuid, characteristicUuid)

    /** 写入十六进制字节；@return 请求是否被系统接受 */
    fun write(serviceUuid: String, characteristicUuid: String, hex: String): Boolean =
        manager.write(serviceUuid, characteristicUuid, hex)

    /** 开启 / 关闭 CCCD 通知 */
    fun setNotify(serviceUuid: String, characteristicUuid: String, enable: Boolean) =
        manager.setNotify(serviceUuid, characteristicUuid, enable)

    fun clearLog() = manager.clearLog()

    override fun onCleared() {
        manager.destroy()
        super.onCleared()
    }
}
