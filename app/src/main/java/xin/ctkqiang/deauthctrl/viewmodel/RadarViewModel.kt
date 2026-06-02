package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.RadarManager
import xin.ctkqiang.deauthctrl.manager.RadarTarget

/**
 * 设备雷达 ViewModel
 *
 * 管理 WiFi/蓝牙设备雷达扫描的状态，每 500ms 轮询 targets 表更新 UI。
 * 继承 AndroidViewModel 以获取 Context（用于 WifiManager 和 BluetoothAdapter 初始化）。
 *
 * ## 状态流
 * - isRunning: 雷达扫描运行状态
 * - targets: 已探测设备列表（每 500ms 全体刷新）
 */
class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = RadarManager(application)
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _targets = MutableStateFlow<List<RadarTarget>>(emptyList())
    val targets: StateFlow<List<RadarTarget>> = _targets
    private var pollJob: Job? = null

    /** 启动雷达扫描 + 后台轮询 */
    fun start() {
        manager.start()
        _isRunning.value = true
        pollJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                _targets.value = manager.targets.values.toList()
                manager.onTick()
                delay(500)
            }
        }
    }

    /** 停止雷达扫描 */
    fun stop() {
        manager.stop()
        pollJob?.cancel()
        _isRunning.value = false
    }

    override fun onCleared() { super.onCleared(); manager.stop(); pollJob?.cancel() }
}
