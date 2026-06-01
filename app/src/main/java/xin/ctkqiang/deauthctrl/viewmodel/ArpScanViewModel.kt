package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.ArpEntry
import xin.ctkqiang.deauthctrl.manager.ArpScanManager

class ArpScanViewModel : ViewModel() {

    private val manager = ArpScanManager()
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _devices = MutableStateFlow<List<ArpEntry>>(emptyList())
    val devices: StateFlow<List<ArpEntry>> = _devices
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

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
