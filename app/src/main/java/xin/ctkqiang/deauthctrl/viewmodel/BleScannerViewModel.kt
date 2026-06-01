package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.BleDevice
import xin.ctkqiang.deauthctrl.manager.BleScannerManager

/**
 * BLE 嗅探扫描 ViewModel
 *
 * 管理低功耗蓝牙被动扫描的生命周期和设备列表状态。
 * 继承 AndroidViewModel 以获取 Context（用于 BluetoothAdapter 初始化）。
 *
 * ## 轮询机制
 * 启动扫描后，在后台协程中每 200ms 从 BleScannerManager 的设备表拉取最新数据，
 * 按 RSSI 降序排列（信号最强的设备排在最前面），推送到 UI。
 *
 * ## 状态流
 * - isRunning: 扫描运行状态
 * - devices: 已发现设备列表（按 RSSI 降序，每 200ms 更新）
 * - error: 错误信息
 */
class BleScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = BleScannerManager(application)
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** 后台轮询协程 Job */
    private var scanJob: Job? = null

    /**
     * 启动 BLE 扫描
     *
     * 启动 Android BLE 硬件扫描，启动后台轮询协程每 200ms 更新设备列表。
     * 设备列表按 RSSI 从强到弱排列（最强信号优先）。
     */
    fun start() {
        _isRunning.value = true
        _error.value = null
        try {
            manager.start()
            scanJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    delay(200)
                    val list = manager.devices.values.toList().sortedByDescending { it.rssi }
                    _devices.value = list
                }
            }
        } catch (e: Exception) {
            _error.value = e.message
            _isRunning.value = false
        }
    }

    /**
     * 停止 BLE 扫描
     *
     * 停止硬件扫描，取消后台轮询协程。
     */
    fun stop() {
        manager.stop()
        scanJob?.cancel()
        _isRunning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        manager.stop()
        scanJob?.cancel()
    }
}
