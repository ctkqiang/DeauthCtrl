package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xin.ctkqiang.deauthctrl.manager.DnsResolver
import xin.ctkqiang.deauthctrl.manager.DnsType
import xin.ctkqiang.deauthctrl.manager.HashTool
import xin.ctkqiang.deauthctrl.manager.KeyGen
import xin.ctkqiang.deauthctrl.manager.LevelDetector
import xin.ctkqiang.deauthctrl.manager.MdnsBrowser
import xin.ctkqiang.deauthctrl.manager.QrTool
import xin.ctkqiang.deauthctrl.manager.TlsInspector
import xin.ctkqiang.deauthctrl.manager.WhoisClient

/**
 * 新增战术工具 ViewModel 集合 —— DNS / mDNS / TLS 证书 / Whois /
 * 哈希 / 二维码 / 密码生成 / 水平仪。网络类操作全部走 IO 线程,
 * 状态经 StateFlow 回流;离开页面时正在进行的任务自动取消。
 */

// ─── DNS 工坊 ────────────────────────────────────────────────────────────────

class DnsViewModel(app: Application) : AndroidViewModel(app) {
    data class State(
        val domain: String = "",
        val type: Int = DnsType.A,
        val server: String = "",
        val servers: List<String> = emptyList(),
        val result: DnsResolver.Result? = null,
        val running: Boolean = false,
        val error: String? = null,
    )

    private val resolver = DnsResolver()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        // 系统当前链路 DNS 优先,读不到回退公共解析器
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val systemDns = runCatching { connectivity.activeNetwork?.let { connectivity.getLinkProperties(it)?.dnsServers } }
            .getOrNull()?.map { it.hostAddress }?.filterNotNull() ?: emptyList()
        val servers = (systemDns + listOf("223.5.5.5", "8.8.8.8", "1.1.1.1")).distinct()
        _state.value = _state.value.copy(servers = servers, server = servers.firstOrNull() ?: "223.5.5.5")
    }

    fun setDomain(v: String) { _state.value = _state.value.copy(domain = v, error = null) }
    fun setType(t: Int) { _state.value = _state.value.copy(type = t, error = null) }
    fun setServer(s: String) { _state.value = _state.value.copy(server = s, error = null) }

    fun query() {
        val s = _state.value
        if (s.domain.isBlank() || s.running) return
        _state.value = s.copy(running = true, error = null, result = null)
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { resolver.querySync(s.server, s.domain.trim(), s.type) } }
            _state.value = r.fold(
                onSuccess = { _state.value.copy(running = false, result = it) },
                onFailure = { _state.value.copy(running = false, error = it.message ?: "query failed") },
            )
        }
    }
}

// ─── mDNS 服务发现 ───────────────────────────────────────────────────────────

class MdnsViewModel(app: Application) : AndroidViewModel(app) {
    data class State(
        val services: List<MdnsBrowser.Service> = emptyList(),
        val running: Boolean = false,
        val error: String? = null,
    )

    private val browser = MdnsBrowser(app)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var job: Job? = null

    fun start() {
        if (_state.value.running) return
        _state.value = _state.value.copy(running = true, services = emptyList(), error = null)
        job = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    browser.browse(onFound = { svc ->
                        _state.value = _state.value.copy(services = (_state.value.services + svc).distinctBy { it.name })
                    })
                }
            }.onFailure { _state.value = _state.value.copy(error = it.message ?: "browse failed") }
            _state.value = _state.value.copy(running = false)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(running = false)
    }
}

// ─── TLS 证书检查 ────────────────────────────────────────────────────────────

class TlsViewModel(app: Application) : AndroidViewModel(app) {
    data class State(
        val host: String = "",
        val port: String = "443",
        val result: TlsInspector.ChainResult? = null,
        val running: Boolean = false,
        val error: String? = null,
    )

    private val inspector = TlsInspector()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setHost(v: String) { _state.value = _state.value.copy(host = v, error = null) }
    fun setPort(v: String) { _state.value = _state.value.copy(port = v.filter { it.isDigit() }.take(5), error = null) }

    fun inspect() {
        val s = _state.value
        val port = s.port.toIntOrNull()
        if (s.host.isBlank() || port == null || s.running) return
        _state.value = s.copy(running = true, error = null, result = null)
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { inspector.inspect(s.host.trim(), port) } }
            _state.value = r.fold(
                onSuccess = { _state.value.copy(running = false, result = it) },
                onFailure = { _state.value.copy(running = false, error = it.message ?: "inspect failed") },
            )
        }
    }
}

// ─── Whois 查询 ──────────────────────────────────────────────────────────────

class WhoisViewModel(app: Application) : AndroidViewModel(app) {
    data class State(
        val domain: String = "",
        val result: WhoisClient.Result? = null,
        val keyLines: List<String> = emptyList(),
        val showRaw: Boolean = false,
        val running: Boolean = false,
        val error: String? = null,
    )

    private val client = WhoisClient()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setDomain(v: String) { _state.value = _state.value.copy(domain = v, error = null) }
    fun toggleRaw() { _state.value = _state.value.copy(showRaw = !_state.value.showRaw) }

    fun query() {
        val s = _state.value
        if (s.domain.isBlank() || s.running) return
        _state.value = s.copy(running = true, error = null, result = null, keyLines = emptyList())
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { client.querySync(s.domain.trim()) } }
            _state.value = r.fold(
                onSuccess = { _state.value.copy(running = false, result = it, keyLines = WhoisClient.extractKeyLines(it.raw)) },
                onFailure = { _state.value.copy(running = false, error = it.message ?: "whois failed") },
            )
        }
    }
}

// ─── 哈希工坊 ────────────────────────────────────────────────────────────────

class HashViewModel(app: Application) : AndroidViewModel(app) {
    data class State(
        val algorithm: String = HashTool.ALGORITHMS.first(),
        val text: String = "",
        val textDigest: String = "",
        val fileDigest: String = "",
        val fileProgress: Long = 0L,
        val fileError: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setAlgorithm(a: String) {
        val s = _state.value
        _state.value = s.copy(algorithm = a, textDigest = if (s.text.isNotEmpty()) HashTool.digestText(a, s.text) else "")
    }

    fun setText(v: String) {
        _state.value = _state.value.copy(text = v, textDigest = if (v.isEmpty()) "" else HashTool.digestText(_state.value.algorithm, v))
    }

    fun hashFile(uri: Uri) {
        _state.value = _state.value.copy(fileDigest = "", fileProgress = 0L, fileError = null)
        viewModelScope.launch {
            val app = getApplication<Application>()
            runCatching {
                withContext(Dispatchers.IO) {
                    val input = app.contentResolver.openInputStream(uri) ?: error("cannot open file")
                    input.use { HashTool.digestStream(_state.value.algorithm, it) { total -> _state.value = _state.value.copy(fileProgress = total) } }
                }
            }.fold(
                onSuccess = { _state.value = _state.value.copy(fileDigest = it) },
                onFailure = { _state.value = _state.value.copy(fileError = it.message ?: "hash failed") },
            )
        }
    }
}

// ─── 二维码工坊 ──────────────────────────────────────────────────────────────

class QrViewModel(app: Application) : AndroidViewModel(app) {
    data class State(
        val mode: String = "text",
        val text: String = "",
        val url: String = "",
        val wifiSsid: String = "",
        val wifiPass: String = "",
        val wifiSecurity: String = "WPA",
        val cardName: String = "",
        val cardPhone: String = "",
        val cardEmail: String = "",
        val payload: String = "",
        val bitmap: Bitmap? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setMode(m: String) { _state.value = _state.value.copy(mode = m).let { rebuild(it) } }

    fun update(block: (State) -> State) { _state.value = rebuild(block(_state.value)) }

    /** 依据模式拼 payload 并重渲矩阵 */
    private fun rebuild(s: State): State {
        val payload = when (s.mode) {
            "url" -> s.url.trim()
            "wifi" -> if (s.wifiSsid.isBlank()) "" else QrTool.wifiPayload(s.wifiSsid.trim(), s.wifiPass, s.wifiSecurity)
            "vcard" -> if (s.cardName.isBlank()) "" else QrTool.vcardPayload(s.cardName.trim(), s.cardPhone.trim(), s.cardEmail.trim())
            else -> s.text
        }
        val bitmap = if (payload.isBlank()) null else QrTool.render(payload)
        return s.copy(payload = payload, bitmap = bitmap)
    }
}

// ─── 密码生成器 ──────────────────────────────────────────────────────────────

class KeyGenViewModel(app: Application) : AndroidViewModel(app) {
    data class State(
        val mode: KeyGen.Mode = KeyGen.Mode.MIXED,
        val length: Int = 20,
        val output: String = "",
        val entropyBits: Double = 0.0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init { regenerate() }

    fun setMode(m: KeyGen.Mode) { _state.value = _state.value.copy(mode = m); regenerate() }

    fun setLength(l: Int) {
        _state.value = _state.value.copy(length = l.coerceIn(8, 64))
        regenerate()
    }

    fun regenerate() {
        val s = _state.value
        _state.value = s.copy(output = KeyGen.generate(s.mode, s.length), entropyBits = KeyGen.entropyBits(s.mode, s.length))
    }
}

// ─── 全息水平仪 ──────────────────────────────────────────────────────────────

class LevelViewModel(app: Application) : AndroidViewModel(app) {
    val detector = LevelDetector(app)
    val state: StateFlow<LevelDetector.LevelState> = detector.state

    fun start() = detector.start()
    fun stop() = detector.stop()
    fun calibrate() = detector.calibrate()

    override fun onCleared() { detector.stop() }
}
