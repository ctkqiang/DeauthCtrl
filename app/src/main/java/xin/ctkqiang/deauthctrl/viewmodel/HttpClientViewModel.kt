package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.manager.HttpClientManager
import xin.ctkqiang.deauthctrl.manager.HttpResponse

/**
 * HTTP 客户端 ViewModel
 *
 * 管理 curl 风格 HTTP 请求的状态和参数，支持 GET/POST/PUT/DELETE/OPTIONS/PATCH/HEAD 方法。
 * 用户在 UI 中设置 URL、请求方法、请求头和请求体，点击发送后异步执行 HTTP 请求。
 *
 * ## 请求头解析
 * 用户在 Header 输入框中输入多行文本（格式: "Key: Value"），
 * 发送时自动解析为 Map<String, String>。不符合格式的行被忽略。
 *
 * ## 状态流
 * - isRunning: 请求执行中标志（用于 UI Loading 状态）
 * - response: 最后一次请求的完整响应（状态码 + 响应头 + 响应体 + 耗时）
 * - error: 网络错误或异常信息，null 表示无错误
 * - url/method/headers/body: 用户输入的双向绑定状态
 */
class HttpClientViewModel : ViewModel() {

    private val client = HttpClientManager()
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _response = MutableStateFlow<HttpResponse?>(null)
    val response: StateFlow<HttpResponse?> = _response
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _url = MutableStateFlow("https://httpbin.org/get")
    val url: StateFlow<String> = _url
    private val _method = MutableStateFlow("GET")
    val method: StateFlow<String> = _method
    private val _headers = MutableStateFlow("")
    val headers: StateFlow<String> = _headers
    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body

    /** 设置请求 URL */
    fun setUrl(u: String) { _url.value = u }
    /** 设置 HTTP 方法（GET/POST/PUT/DELETE/OPTIONS/PATCH/HEAD） */
    fun setMethod(m: String) { _method.value = m }
    /** 设置请求头（多行文本，格式: "Key: Value"） */
    fun setHeaders(h: String) { _headers.value = h }
    /** 设置请求体（JSON/XML/表单等） */
    fun setBody(b: String) { _body.value = b }

    /**
     * 执行 HTTP 请求
     *
     * 解析用户输入的请求头 → 调用 HttpClientManager.execute() → 更新 response/error 状态。
     * 在 viewModelScope 中启动协程，ViewModel 销毁时自动取消未完成的请求。
     */
    fun send() {
        _isRunning.value = true
        _error.value = null
        _response.value = null
        viewModelScope.launch {
            try {
                val h = _headers.value.trim().lines()
                    .filter { it.contains(":") }
                    .associate { val (k, v) = it.split(":", limit = 2); k.trim() to v.trim() }
                val resp = client.execute(_url.value.trim(), _method.value, h, _body.value)
                _response.value = resp
            } catch (e: Exception) {
                _error.value = e.message ?: "请求失败"
            } finally {
                _isRunning.value = false
            }
        }
    }
}
