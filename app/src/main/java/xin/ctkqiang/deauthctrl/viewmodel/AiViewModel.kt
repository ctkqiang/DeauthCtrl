package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xin.ctkqiang.deauthctrl.manager.LlmSession
import xin.ctkqiang.deauthctrl.manager.LocalLlmManager

/**
 * 离线 AI 终端 ViewModel —— 模型下载 / 会话生命周期 / 流式对话
 *
 * 引擎加载与推理全在单线程 IO 调度器上执行(LiteRT-LM 引擎非线程安全);
 * 流式回调经 viewModelScope 回主线程更新 UI。
 */
class AiViewModel(app: Application) : AndroidViewModel(app) {

    data class ChatMsg(val role: String, val text: String, val streaming: Boolean = false)

    data class State(
        val archSupported: Boolean = true,
        val ramSufficient: Boolean = true,
        val modelId: String = LocalLlmManager.MODELS.first().id,
        val downloaded: Boolean = false,
        val downloading: Boolean = false,
        val downloadBytes: Long = 0L,
        val downloadTotal: Long = LocalLlmManager.MODELS.first().sizeBytes,
        val initializing: Boolean = false,
        val ready: Boolean = false,
        val messages: List<ChatMsg> = emptyList(),
        val streaming: Boolean = false,
        val input: String = "",
        val maxTokens: Int = 512,
        val temperature: Double? = null,
        val error: String? = null,
        val info: String? = null,
    )

    private val manager = LocalLlmManager(app)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var session: LlmSession? = null
    private var downloadJob: Job? = null

    // 引擎单线程调度器:加载/建会话/清理串行执行
    private val engineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val currentSpec get() = LocalLlmManager.MODELS.first { it.id == _state.value.modelId }

    init {
        val s = _state.value
        _state.value = s.copy(
            archSupported = manager.isArchSupported,
            ramSufficient = manager.isRamSufficient,
            downloaded = manager.isDownloaded(currentSpec),
        )
    }

    fun selectModel(id: String) {
        val s = _state.value
        if (s.modelId == id || s.downloading || s.streaming) return
        session?.close()
        session = null
        val spec = LocalLlmManager.MODELS.first { it.id == id }
        _state.value = s.copy(
            modelId = id,
            downloaded = manager.isDownloaded(spec),
            downloadTotal = spec.sizeBytes,
            downloadBytes = 0L,
            ready = false,
            messages = emptyList(),
            error = null,
        )
        ensureSession()
    }

    /** 页面进入:模型已在本地则自动加载引擎 */
    fun ensureSession() {
        val s = _state.value
        if (s.ready || s.initializing || !s.downloaded || !s.archSupported) return
        _state.value = s.copy(initializing = true, error = null)
        viewModelScope.launch {
            val spec = LocalLlmManager.MODELS.first { it.id == _state.value.modelId }
            runCatching { withContext(engineDispatcher) { manager.createSession(spec, s.maxTokens, s.temperature) } }
                .onSuccess {
                    session = it
                    _state.value = _state.value.copy(initializing = false, ready = true, info = null)
                }
                .onFailure {
                    _state.value = _state.value.copy(initializing = false, ready = false, error = it.message ?: "load failed")
                }
        }
    }

    fun startDownload() {
        val s = _state.value
        if (s.downloading || s.downloaded) return
        _state.value = s.copy(downloading = true, downloadBytes = 0L, error = null)
        val spec = LocalLlmManager.MODELS.first { it.id == s.modelId }
        downloadJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    manager.download(spec) { done, total ->
                        _state.value = _state.value.copy(downloadBytes = done, downloadTotal = total)
                    }
                }
            }.onSuccess {
                _state.value = _state.value.copy(downloading = false, downloaded = true)
                ensureSession()
            }.onFailure {
                _state.value = _state.value.copy(downloading = false, error = it.message ?: "download failed")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = _state.value.copy(downloading = false)
    }

    fun deleteModel() {
        session?.close()
        session = null
        manager.delete(currentSpec)
        _state.value = _state.value.copy(downloaded = false, ready = false, messages = emptyList(), downloadBytes = 0L)
    }

    fun setInput(v: String) { _state.value = _state.value.copy(input = v) }

    fun setMaxTokens(v: Int) {
        _state.value = _state.value.copy(maxTokens = v)
        if (_state.value.ready) rebuildSession()
    }

    fun setTemperature(v: Double?) {
        _state.value = _state.value.copy(temperature = v)
        if (_state.value.ready) rebuildSession()
    }

    private fun rebuildSession() {
        val s = _state.value
        session?.close()
        session = null
        _state.value = s.copy(ready = false, initializing = true)
        viewModelScope.launch {
            val spec = LocalLlmManager.MODELS.first { it.id == _state.value.modelId }
            runCatching { withContext(engineDispatcher) { manager.createSession(spec, s.maxTokens, s.temperature) } }
                .onSuccess {
                    session = it
                    _state.value = _state.value.copy(initializing = false, ready = true)
                }
                .onFailure {
                    _state.value = _state.value.copy(initializing = false, error = it.message ?: "rebuild failed")
                }
        }
    }

    /** 发送消息:先落用户气泡,再流式拼接助手回复 */
    fun send() {
        val s = _state.value
        val prompt = s.input.trim()
        if (prompt.isEmpty() || s.streaming || !s.ready) return
        _state.value = s.copy(
            input = "",
            messages = s.messages + ChatMsg("user", prompt),
            streaming = true,
            error = null,
        )
        viewModelScope.launch {
            val current = session ?: run { _state.value = _state.value.copy(streaming = false); return@launch }
            val builder = StringBuilder()
            current.send(
                prompt,
                onPartial = { partial ->
                    builder.clear()
                    builder.append(partial)
                    val base = _state.value.messages
                    _state.value = _state.value.copy(
                        messages = base.filterNot { it.streaming } + ChatMsg("assistant", builder.toString(), streaming = true),
                    )
                },
                onDone = {
                    _state.value = _state.value.copy(
                        messages = _state.value.messages.filterNot { it.streaming } +
                            if (builder.isEmpty()) emptyList() else listOf(ChatMsg("assistant", builder.toString())),
                        streaming = false,
                    )
                },
                onError = { msg ->
                    _state.value = _state.value.copy(
                        messages = _state.value.messages.filterNot { it.streaming },
                        streaming = false,
                        error = msg,
                    )
                },
            )
            // onDone/onError 回调线程不确定,兜底再收敛一次流式状态
            if (_state.value.streaming) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages.filterNot { it.streaming } +
                        if (builder.isEmpty()) emptyList() else listOf(ChatMsg("assistant", builder.toString())),
                    streaming = false,
                )
            }
        }
    }

    fun stop() {
        session?.stop()
    }

    /** 清空对话历史:重建会话 */
    fun clearChat() {
        rebuildSession()
        _state.value = _state.value.copy(messages = emptyList())
    }

    override fun onCleared() {
        session?.close()
        session = null
    }
}
