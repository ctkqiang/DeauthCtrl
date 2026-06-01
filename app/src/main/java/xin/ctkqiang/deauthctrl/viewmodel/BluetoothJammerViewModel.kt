package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import xin.ctkqiang.deauthctrl.manager.BluetoothJammerManager
import xin.ctkqiang.deauthctrl.manager.DiscoveredDevice
import xin.ctkqiang.deauthctrl.model.JammerLogEntry
import kotlinx.coroutines.flow.StateFlow

class BluetoothJammerViewModel(application: Application) : AndroidViewModel(application) {
    val manager = BluetoothJammerManager(application)
    val log: StateFlow<List<JammerLogEntry>> = manager.log
    val isRunning: StateFlow<Boolean> = manager.isRunning
    val error: StateFlow<String?> = manager.error
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = manager.discoveredDevices
    val discoveryCount: StateFlow<Int> = manager.discoveryCount

    fun start() = manager.start()
    fun stop() = manager.stop()
    fun clearLog() = manager.clearLog()

    override fun onCleared() { super.onCleared(); manager.destroy() }
}