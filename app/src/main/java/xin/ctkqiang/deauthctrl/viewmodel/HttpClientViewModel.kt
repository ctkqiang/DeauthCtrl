package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.manager.HttpClientManager
import xin.ctkqiang.deauthctrl.manager.HttpResponse

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

    fun setUrl(u: String) { _url.value = u }
    fun setMethod(m: String) { _method.value = m }
    fun setHeaders(h: String) { _headers.value = h }
    fun setBody(b: String) { _body.value = b }

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
