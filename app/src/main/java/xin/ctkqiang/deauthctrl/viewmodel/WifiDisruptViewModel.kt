package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import xin.ctkqiang.deauthctrl.manager.CapabilityProbe
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

    /** 真实注入引擎状态(root 门控) */
    val deauthSession = manager.deauth.session

    private var targetSsid: String = ""
    private var durationSeconds: Int = 10
    // LOHS 完成一次 start/stop 本身需要数秒;保持时长必须与 manager 的钳制一致,
    // 否则 UI 滑杆选的值会被静默吞掉(旧实现 40-500ms 全部被抬到 5s,滑杆纯装饰)
    private var toggleIntervalMs: Long = WifiDisruptManager.DEFAULT_HOLD_MS

    fun scanNetworks() = manager.scanNetworks()

    fun selectTarget(network: WifiNetwork) {
        targetSsid = network.ssid
    }

    fun setDuration(seconds: Int) {
        durationSeconds = seconds.coerceIn(1, 60)
    }

    /** 设置单个热点保持时长（毫秒）—— 与 manager 钳制范围一致,设置值真实生效 */
    fun setToggleInterval(ms: Long) {
        toggleIntervalMs = ms.coerceIn(WifiDisruptManager.MIN_HOLD_MS, WifiDisruptManager.MAX_HOLD_MS)
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

    // ─── 真实注入(root 门控) ───

    fun probeDeauth() {
        // 同步刷新全局能力快照(root/monitor/注入自检),注入面板的判定与之共享
        CapabilityProbe.get(getApplication()).refreshFull()
        manager.deauth.probeTools()
    }

    /** @return null 表示会话已启动;非 null 为失败原因(缺 root/工具/芯片不支持等) */
    fun startDeauth(bssid: String, channel: Int, clientMac: String?, count: Int, intervalMs: Long): String? =
        manager.deauth.start(bssid, channel, clientMac, count, intervalMs)

    fun stopDeauth() = manager.deauth.stop()

    /** 被动嗅探模式(信标/探测/去认证帧流) */
    fun startCapture(channel: Int): String? = manager.deauth.startCapture(channel)

    override fun onCleared() {
        super.onCleared()
        manager.destroy()
    }
}
