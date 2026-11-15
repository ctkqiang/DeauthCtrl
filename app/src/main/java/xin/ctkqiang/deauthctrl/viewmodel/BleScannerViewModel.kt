package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
 * ## 失败如何到达界面
 * BLE 的失败分两种时机，两条路径都要覆盖，否则界面会停在"运行中但零设备"：
 * - **同步失败**（蓝牙未开、权限缺失、无扫描器）：[BleScannerManager.start] 直接返回原因
 * - **异步失败**（onScanFailed，如扫描过于频繁）：内核写入 `failure` 流，轮询协程取回
 *
 * ## 状态流
 * - isRunning: 扫描运行状态（只有真正开始扫描后才为 true）
 * - devices: 已发现设备列表（按 RSSI 降序，每 200ms 更新）
 * - error: 失败原因，非空时界面显示 `[!]` 提示
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
     * 先让内核做完整的启动前校验，只有内核确认扫描已交给系统才把 isRunning 置为 true。
     * 反过来的写法（先置 true 再看结果）会让失败静默成"扫描中，设备数 0"。
     */
    fun start() {
        _error.value = null
        _isRunning.value = false

        val failure = manager.start()
        if (failure != null) {
            _error.value = failure
            return
        }

        _isRunning.value = true
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                _devices.value = manager.devices.values.sortedByDescending { it.rssi }

                // 异步失败（onScanFailed）或内核已停止时，把界面状态同步回来
                manager.failure.value?.let { _error.value = it }
                if (!manager.isRunning) {
                    _isRunning.value = false
                    break
                }
            }
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
        scanJob = null
        _isRunning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        manager.stop()
        scanJob?.cancel()
        scanJob = null
    }

    private companion object {
        /** 设备列表推送间隔：200ms 足够跟手，又不会给 UI 线程造成重组压力 */
        const val POLL_INTERVAL_MS = 200L
    }
}
