package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.PingManager
import xin.ctkqiang.deauthctrl.manager.PingResult

class PingViewModel : ViewModel() {

    private val manager = PingManager()
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _host = MutableStateFlow("8.8.8.8")
    val host: StateFlow<String> = _host
    private val _results = MutableStateFlow<List<PingResult>>(emptyList())
    val results: StateFlow<List<PingResult>> = _results
    private val _sent = MutableStateFlow(0)
    val sent: StateFlow<Int> = _sent
    private val _received = MutableStateFlow(0)
    val received: StateFlow<Int> = _received
    private val _loss = MutableStateFlow(0)
    val loss: StateFlow<Int> = _loss
    private val _min = MutableStateFlow(0f)
    val min: StateFlow<Float> = _min
    private val _avg = MutableStateFlow(0f)
    val avg: StateFlow<Float> = _avg
    private val _max = MutableStateFlow(0f)
    val max: StateFlow<Float> = _max
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setHost(h: String) { _host.value = h }

    fun start() {
        _isRunning.value = true
        _error.value = null
        _results.value = emptyList()
        _sent.value = 0; _received.value = 0; _loss.value = 0
        _min.value = 0f; _avg.value = 0f; _max.value = 0f

        manager.start(
            host = _host.value.trim(),
            count = 0,
            interval = 150,
            onResult = { _results.value = _results.value + it },
            onStats = { s, r, l, mn, av, mx ->
                _sent.value = s; _received.value = r; _loss.value = l
                _min.value = mn; _avg.value = av; _max.value = mx
            },
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
