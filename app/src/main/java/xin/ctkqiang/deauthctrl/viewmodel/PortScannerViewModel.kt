package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.PortResult
import xin.ctkqiang.deauthctrl.manager.PortScannerManager

class PortScannerViewModel : ViewModel() {

    private val manager = PortScannerManager()
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _host = MutableStateFlow("192.168.1.1")
    val host: StateFlow<String> = _host
    private val _results = MutableStateFlow<List<PortResult>>(emptyList())
    val results: StateFlow<List<PortResult>> = _results
    private val _scanned = MutableStateFlow(0)
    val scanned: StateFlow<Int> = _scanned
    private val _total = MutableStateFlow(0)
    val total: StateFlow<Int> = _total
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setHost(h: String) { _host.value = h }

    fun start() {
        _isRunning.value = true; _error.value = null
        _results.value = emptyList(); _scanned.value = 0
        manager.scan(
            host = _host.value.trim(),
            onResult = { _results.value = _results.value + it },
            onProgress = { s, t -> _scanned.value = s; _total.value = t },
            onComplete = { _isRunning.value = false },
        )
    }

    fun stop() {
        manager.stop()
        _isRunning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        manager.stop()
    }
}
