package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.BleDevice
import xin.ctkqiang.deauthctrl.manager.BleScannerManager

class BleScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = BleScannerManager(application)
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var scanJob: Job? = null

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
