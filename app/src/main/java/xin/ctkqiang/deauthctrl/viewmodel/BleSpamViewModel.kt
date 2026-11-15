package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import xin.ctkqiang.deauthctrl.manager.BleSpamManager
import xin.ctkqiang.deauthctrl.manager.BleSpamStats
import xin.ctkqiang.deauthctrl.manager.BleCycleMode
import xin.ctkqiang.deauthctrl.manager.BleTxPower
import xin.ctkqiang.deauthctrl.model.BleAdvertLogEntry
import xin.ctkqiang.deauthctrl.model.BlePayloadProfile
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * BLE 泛洪攻击 ViewModel
 *
 * 作为 UI 与 [BleSpamManager] 之间的不可变中间层，
 * 主要职责：
 * 1. 暴露 Manager 的 StateFlow 给 Compose 订阅
 * 2. 持久化 UI 选择项（profile/interval/txPower/cycleMode/maxPackets）
 *   以便 Compose 重组后仍能恢复显示值
 * 3. 将 UI 调用转发到 Manager，运行态变更无需手写线程切换
 *
 * Manager 自身在 Main 协程调度下安全更新 StateFlow，
 * 故 ViewModel 直接转发即可，无需额外 withContext。
 *
 * @param application 应用上下文，用于构造 [BleSpamManager]
 */
class BleSpamViewModel(application: Application) : AndroidViewModel(application) {

    val manager = BleSpamManager(application)

    val log: StateFlow<List<BleAdvertLogEntry>> = manager.log
    val isRunning: StateFlow<Boolean> = manager.isRunning
    val error: StateFlow<String?> = manager.error
    val stats: StateFlow<BleSpamStats> = manager.stats
    val activeProfile: StateFlow<BlePayloadProfile> = manager.activeProfile

    // ─── UI 本地缓存值（Compose 重组后回填用） ──────────────────────────────

    private var selectedProfile = BlePayloadProfile.ALL
    private var intervalMs = 30L
    private var selectedTxPower = BleTxPower.HIGH
    private var selectedCycleMode = BleCycleMode.SEQUENCE
    private var selectedMaxPackets = -1L

    // ─── Profile ────────────────────────────────────────────────────────────

    fun getProfiles(): List<BlePayloadProfile> = BlePayloadProfile.entries.toList()

    fun setProfile(profile: BlePayloadProfile) {
        selectedProfile = profile
        manager.setProfile(profile)
    }

    fun getCurrentProfile(): BlePayloadProfile = selectedProfile

    // ─── Interval ───────────────────────────────────────────────────────────

    fun setInterval(intervalMs: Long) {
        this.intervalMs = intervalMs
        manager.setInterval(intervalMs)
    }

    fun getCurrentInterval(): Long = intervalMs

    // ─── TX 功率 ─────────────────────────────────────────────────────────────

    fun getTxPowerLevels(): List<BleTxPower> = BleTxPower.entries.toList()

    fun setTxPower(power: BleTxPower) {
        selectedTxPower = power
        manager.setTxPower(power)
    }

    fun getCurrentTxPower(): BleTxPower = selectedTxPower

    // ─── 循环模式 ────────────────────────────────────────────────────────────

    fun getCycleModes(): List<BleCycleMode> = BleCycleMode.entries.toList()

    fun setCycleMode(mode: BleCycleMode) {
        selectedCycleMode = mode
        manager.setCycleMode(mode)
    }

    fun getCurrentCycleMode(): BleCycleMode = selectedCycleMode

    // ─── 最大发包数 ───────────────────────────────────────────────────────────

    fun setMaxPackets(max: Long) {
        selectedMaxPackets = max
        manager.setMaxPackets(max)
    }

    fun getCurrentMaxPackets(): Long = selectedMaxPackets

    // ─── 运行控制 ────────────────────────────────────────────────────────────

    fun startSpam() = manager.start()
    fun stopSpam() = manager.stop()
    fun clearLog() = manager.clearLog()

    override fun onCleared() {
        super.onCleared()
        manager.destroy()
    }
}
