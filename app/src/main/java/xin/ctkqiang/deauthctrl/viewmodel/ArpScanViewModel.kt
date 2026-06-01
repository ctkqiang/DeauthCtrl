package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.ArpEntry
import xin.ctkqiang.deauthctrl.manager.ArpScanManager

/**
 * ARP 扫描 ViewModel
 *
 * 管理局域网设备发现的完整状态，通过 ArpScanManager 执行 Ping Sweep 扫描。
 * 设备列表按 IP 地址最后一个字节升序排列。
 *
 * ## 状态流
 * - isRunning: 扫描运行状态
 * - devices: 已发现设备列表（按 IP 升序排列，实时更新）
 * - error: 错误信息（扫描结束但未发现设备时显示提示）
 */
class ArpScanViewModel : ViewModel() {

    private val manager = ArpScanManager()
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _devices = MutableStateFlow<List<ArpEntry>>(emptyList())
    val devices: StateFlow<List<ArpEntry>> = _devices
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * 执行 ARP 扫描
     *
     * 清空设备列表，通过回调实时接收发现的设备并追加到列表。
     * 设备按 IP 地址第四段数字升序排列（如 .1, .100, .105, .120）。
     * 扫描完成后若未发现任何设备，设置 error 提示用户确认 WiFi 连接状态。
     */
    fun scan() {
        _isRunning.value = true
        _error.value = null
        _devices.value = emptyList()
        val temp = mutableListOf<ArpEntry>()

        manager.scan(
            callback = { entry ->
                temp.add(entry)
                _devices.value = temp.sortedBy { it.ip.split(".").last().toIntOrNull() ?: 0 }
            },
            onComplete = {
                _isRunning.value = false
                if (_devices.value.isEmpty()) _error.value = "未发现设备 — 请确认已连接 WiFi"
            },
        )
    }

    override fun onCleared() {
        super.onCleared()
        manager.stop()
    }
}
