package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import xin.ctkqiang.deauthctrl.manager.WifiDisruptManager
import xin.ctkqiang.deauthctrl.manager.WifiDisruptStats
import xin.ctkqiang.deauthctrl.model.HotspotResult
import xin.ctkqiang.deauthctrl.model.ScanState
import xin.ctkqiang.deauthctrl.model.WifiNetwork
import kotlinx.coroutines.flow.StateFlow

/**
 * WiFi 干扰 ViewModel
 *
 * 持有 [WifiDisruptManager]，暴露：
 * - 扫描状态 [scanState]
 * - 热点操作结果 [hotspotResult]
 * - 泛洪运行标志 [isFlooding]
 * - 运行统计 [stats]（v2 新增）
 *
 * UI 本地缓存 targetSsid / duration / toggleInterval，保证 Compose 重组后可回填。
 *
 * @param application 应用上下文
 */
class WifiDisruptViewModel(application: Application) : AndroidViewModel(application) {

    val manager = WifiDisruptManager(application)

    val scanState: StateFlow<ScanState> = manager.scanState
    val hotspotResult: StateFlow<HotspotResult?> = manager.hotspotResult
    val isFlooding: StateFlow<Boolean> = manager.isFlooding
    val stats: StateFlow<WifiDisruptStats> = manager.stats

    private var targetSsid: String = ""
    private var durationSeconds: Int = 10
    private var toggleIntervalMs: Long = 80L

    fun scanNetworks() = manager.scanNetworks()

    fun selectTarget(network: WifiNetwork) {
        targetSsid = network.ssid
    }

    fun setDuration(seconds: Int) {
        durationSeconds = seconds.coerceIn(1, 60)
    }

    /** 设置热点启停切换间隔（毫秒），影响泛洪节奏 */
    fun setToggleInterval(ms: Long) {
        toggleIntervalMs = ms.coerceIn(40L, 500L)
    }

    fun getTargetSsid(): String = targetSsid
    fun getDuration(): Int = durationSeconds
    fun getToggleInterval(): Long = toggleIntervalMs

    fun startFlood() {
        if (targetSsid.isBlank()) {
            manager.scanNetworks()
            return
        }
        manager.startFlood(targetSsid, durationSeconds, toggleIntervalMs)
    }

    fun stopFlood() = manager.stopFlood()

    override fun onCleared() {
        super.onCleared()
        manager.destroy()
    }
}
