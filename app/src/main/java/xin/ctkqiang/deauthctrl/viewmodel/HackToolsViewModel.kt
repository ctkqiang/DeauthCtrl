package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.*

class RevShellViewModel : ViewModel() {
    private val manager = RevShellManager()
    private val _isRunning = MutableStateFlow(false); val isRunning: StateFlow<Boolean> = _isRunning
    private val _connected = MutableStateFlow(false); val connected: StateFlow<Boolean> = _connected
    private val _logs = MutableStateFlow<List<ShellLog>>(emptyList()); val logs: StateFlow<List<ShellLog>> = _logs
    private val _port = MutableStateFlow("4444"); val port: StateFlow<String> = _port
    private val _cmd = MutableStateFlow(""); val cmd: StateFlow<String> = _cmd
    init { manager.onLog = { l -> _logs.value = _logs.value + l; _connected.value = manager.isConnected } }
    fun setPort(p: String) { _port.value = p }
    fun setCmd(c: String) { _cmd.value = c }
    fun start() { _logs.value = emptyList(); manager.start(_port.value.toIntOrNull() ?: 4444); _isRunning.value = true }
    fun send() { val c = _cmd.value; if (c.isNotBlank()) { manager.send(c); _cmd.value = "" } }
    fun stop() { manager.stop(); _isRunning.value = false; _connected.value = false }
    override fun onCleared() { super.onCleared(); manager.stop() }
}

class PayloadViewModel : ViewModel() {
    private val gen = PayloadGenManager()
    private val _ip = MutableStateFlow("10.0.0.1"); val ip: StateFlow<String> = _ip
    private val _port = MutableStateFlow("4444"); val port: StateFlow<String> = _port
    private val _payloads = MutableStateFlow<List<Payload>>(emptyList()); val payloads: StateFlow<List<Payload>> = _payloads
    fun setIp(i: String) { _ip.value = i }
    fun setPort(p: String) { _port.value = p }
    fun generate() { _payloads.value = gen.generate(_ip.value.trim(), _port.value.trim()) }
}

class DirBruteViewModel : ViewModel() {
    private val manager = DirBruteManager()
    private val _isRunning = MutableStateFlow(false); val isRunning: StateFlow<Boolean> = _isRunning
    private val _url = MutableStateFlow("https://example.com"); val url: StateFlow<String> = _url
    private val _results = MutableStateFlow<List<BruteResult>>(emptyList()); val results: StateFlow<List<BruteResult>> = _results
    private val _progress = MutableStateFlow(0 to 0); val progress: StateFlow<Pair<Int, Int>> = _progress
    fun setUrl(u: String) { _url.value = u }
    fun start() { _results.value = emptyList(); manager.start(_url.value.trim(), { r -> _results.value = _results.value + r }, { d, t -> _progress.value = d to t }, { _isRunning.value = false }); _isRunning.value = true }
    fun stop() { manager.stop(); _isRunning.value = false }
    override fun onCleared() { super.onCleared(); manager.stop() }
}

class CVESearchViewModel : ViewModel() {
    private val manager = CVESearchManager()
    private val _isRunning = MutableStateFlow(false); val isRunning: StateFlow<Boolean> = _isRunning
    private val _query = MutableStateFlow(""); val query: StateFlow<String> = _query
    private val _results = MutableStateFlow<List<CVEResult>>(emptyList()); val results: StateFlow<List<CVEResult>> = _results
    private val _status = MutableStateFlow(""); val status: StateFlow<String> = _status
    fun setQuery(q: String) { _query.value = q }
    fun search() { _results.value = emptyList(); _status.value = ""; manager.search(_query.value.trim(), { r -> _results.value = _results.value + r }, { s -> _status.value = s }, { _isRunning.value = false }); _isRunning.value = true }
    fun stop() { manager.stop(); _isRunning.value = false }
    override fun onCleared() { super.onCleared(); manager.stop() }
}
