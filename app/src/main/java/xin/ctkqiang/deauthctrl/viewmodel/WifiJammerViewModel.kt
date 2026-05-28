package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import xin.ctkqiang.deauthctrl.manager.JammerLogEntry
import xin.ctkqiang.deauthctrl.manager.WifiJammerManager
import kotlinx.coroutines.flow.StateFlow

class WifiJammerViewModel(application: Application) : AndroidViewModel(application) {
    val manager = WifiJammerManager(application)
    val log: StateFlow<List<JammerLogEntry>> = manager.log
    val isRunning: StateFlow<Boolean> = manager.isRunning
    val error: StateFlow<String?> = manager.error

    private var targets: List<String> = emptyList()
    private var intervalMs = 50L

    fun setTargets(ssids: List<String>) { targets = ssids }
    fun setInterval(ms: Long) { intervalMs = ms.coerceIn(20, 200) }

    fun start() { manager.start(targets, intervalMs) }
    fun stop() = manager.stop()
    fun clearLog() = manager.clearLog()
    fun getBeaconCount() = manager.getBeaconCount()
    fun getCycleCount() = manager.getCycleCount()

    override fun onCleared() { super.onCleared(); manager.destroy() }
}
