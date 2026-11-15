package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import xin.ctkqiang.deauthctrl.manager.JammerScanState
import xin.ctkqiang.deauthctrl.manager.WifiJammerManager
import xin.ctkqiang.deauthctrl.manager.WifiJammerStats
import xin.ctkqiang.deauthctrl.model.JammerLogEntry
import kotlinx.coroutines.flow.StateFlow

/**
 * WiFi 压制引擎 ViewModel
 *
 * 仅做三件事：缓存 UI 侧参数、把参数转发给 [WifiJammerManager]、把 Manager 的
 * StateFlow 原样暴露给 Compose。不含任何业务逻辑，保证页面重建后状态不丢。
 */
class WifiJammerViewModel(application: Application) : AndroidViewModel(application) {

    val manager = WifiJammerManager(application)
    val log: StateFlow<List<JammerLogEntry>> = manager.log
    val isRunning: StateFlow<Boolean> = manager.isRunning
    val scanState: StateFlow<JammerScanState> = manager.scanState
    val error: StateFlow<String?> = manager.error
    val targetCount: StateFlow<Int> = manager.targetCount
    val stats: StateFlow<WifiJammerStats> = manager.stats

    private var intervalMs = WifiJammerManager.DEFAULT_TOGGLE_MS
    private var maxCycles = 0

    fun setInterval(ms: Long) {
        intervalMs = ms.coerceIn(WifiJammerManager.TOGGLE_MIN_MS, WifiJammerManager.TOGGLE_MAX_MS)
    }

    fun getInterval() = intervalMs

    /** @param cycles 完成后自动停止的轮数，`0` 表示不限制 */
    fun setMaxCycles(cycles: Int) {
        maxCycles = cycles.coerceIn(0, WifiJammerManager.MAX_CYCLES_LIMIT)
    }

    fun getMaxCycles() = maxCycles

    fun start() = manager.scanAndStart(intervalMs, maxCycles)

    fun stop() = manager.stop()

    fun clearLog() = manager.clearLog()

    fun formatUptime(ms: Long) = manager.formatUptime(ms)

    override fun onCleared() {
        super.onCleared()
        manager.destroy()
    }
}
