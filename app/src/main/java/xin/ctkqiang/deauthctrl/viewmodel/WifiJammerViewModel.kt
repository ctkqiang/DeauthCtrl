package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import xin.ctkqiang.deauthctrl.manager.JammerScanState
import xin.ctkqiang.deauthctrl.manager.WifiJammerManager
import xin.ctkqiang.deauthctrl.model.JammerLogEntry
import kotlinx.coroutines.flow.StateFlow

class WifiJammerViewModel(application: Application) : AndroidViewModel(application) {
    val manager = WifiJammerManager(application)
    val log: StateFlow<List<JammerLogEntry>> = manager.log
    val isRunning: StateFlow<Boolean> = manager.isRunning
    val scanState: StateFlow<JammerScanState> = manager.scanState
    val error: StateFlow<String?> = manager.error
    val targetCount: StateFlow<Int> = manager.targetCount

    private var intervalMs = 80L
    fun setInterval(ms: Long) { intervalMs = ms.coerceIn(60, 300) }
    fun getInterval() = intervalMs
    fun start() { manager.scanAndStart(intervalMs) }
    fun stop() = manager.stop()
    fun clearLog() = manager.clearLog()
    fun getBeaconCount() = manager.getBeaconCount()
    fun getCycleCount() = manager.getCycleCount()
    override fun onCleared() { super.onCleared(); manager.destroy() }
}