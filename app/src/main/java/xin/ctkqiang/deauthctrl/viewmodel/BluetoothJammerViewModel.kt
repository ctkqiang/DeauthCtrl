package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import xin.ctkqiang.deauthctrl.manager.BluetoothJammerManager
import xin.ctkqiang.deauthctrl.manager.BluetoothJammerStats
import xin.ctkqiang.deauthctrl.manager.DiscoveredDevice
import xin.ctkqiang.deauthctrl.model.JammerLogEntry
import kotlinx.coroutines.flow.StateFlow

/**
 * 蓝牙压制 ViewModel
 *
 * 持有 [BluetoothJammerManager]，暴露运行状态、设备列表、日志、统计（v2 新增）。
 *
 * @param application 应用上下文
 */
class BluetoothJammerViewModel(application: Application) : AndroidViewModel(application) {
    val manager = BluetoothJammerManager(application)
    val log: StateFlow<List<JammerLogEntry>> = manager.log
    val isRunning: StateFlow<Boolean> = manager.isRunning
    val error: StateFlow<String?> = manager.error
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = manager.discoveredDevices
    val discoveryCount: StateFlow<Int> = manager.discoveryCount
    val stats: StateFlow<BluetoothJammerStats> = manager.stats

    fun start() = manager.start()
    fun stop() = manager.stop()
    fun clearLog() = manager.clearLog()

    override fun onCleared() { super.onCleared(); manager.destroy() }
}
