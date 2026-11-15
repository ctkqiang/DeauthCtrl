package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.manager.HttpCallStats
import xin.ctkqiang.deauthctrl.manager.HttpClientManager
import xin.ctkqiang.deauthctrl.manager.HttpResponse
import xin.ctkqiang.deauthctrl.model.JammerLogEntry
import java.util.Locale

/**
 * HTTP 客户端 ViewModel —— 对 [HttpClientManager] 的纯委托层。
 *
 * ## 职责边界
 * 只在两个地方持有状态：
 * 1. **用户输入**（`url` / `method` / `headers` / `body`）—— 这是纯粹的界面态，
 *    内核不需要知道用户在输入框里敲了什么，直到点下发送。
 * 2. **转发**内核的运行时状态（`isRunning` / `response` / `error` / `stats` / `log`）。
 *
 * 运行时状态**不再**在 ViewModel 里另建一份 `MutableStateFlow`。历史上这里曾自己维护
 * `_isRunning` / `_response`，与内核各持一份，导致"取消后内核已复位、界面还停在 loading"
 * 这类双真相源不一致；现在只有一个真相源，界面永远与内核同频。
 *
 * ## 生命周期
 * [HttpClientManager] 自身不持有协程作用域，请求跑在 `viewModelScope` 上。
 * 因此页面销毁 → ViewModel 清理 → 在途请求自动取消，不会残留线程或连接。
 * [onCleared] 里额外调一次 [HttpClientManager.destroy]，兜住"用户没点取消就退出"的场景，
 * 确保阻塞中的 socket 读被 `disconnect()` 立刻打断，而不是等超时自然结束。
 */
class HttpClientViewModel : ViewModel() {

    private val manager = HttpClientManager()

    // ── 内核运行时状态：只转发，不复制 ──────────────────────────────────────

    /** 是否有请求正在执行，用于界面 loading 与"发送/取消"按钮的切换。 */
    val isRunning: StateFlow<Boolean> = manager.isRunning

    /** 最近一次成功拿到的响应；发起新请求时会被内核置空。 */
    val response: StateFlow<HttpResponse?> = manager.response

    /** 最近一次失败原因，`null` 表示当前无错误。 */
    val error: StateFlow<String?> = manager.error

    /** 本机本次生命周期内的累计请求统计快照。 */
    val stats: StateFlow<HttpCallStats> = manager.stats

    /** 环形日志缓冲快照，最新的在末尾。 */
    val log: StateFlow<List<JammerLogEntry>> = manager.log

    // ── 用户输入态：仅界面持有 ──────────────────────────────────────────────

    private val _url = MutableStateFlow(DEFAULT_URL)

    /** 请求 URL 输入框内容。 */
    val url: StateFlow<String> = _url

    private val _method = MutableStateFlow(DEFAULT_METHOD)

    /** 当前选中的 HTTP 方法。 */
    val method: StateFlow<String> = _method

    private val _headers = MutableStateFlow("")

    /** 请求头多行文本，格式 `Key: Value`。 */
    val headers: StateFlow<String> = _headers

    private val _body = MutableStateFlow("")

    /** 请求体原文，仅对 [HttpClientManager.BODY_METHODS] 中的方法生效。 */
    val body: StateFlow<String> = _body

    /** 设置请求 URL。 */
    fun setUrl(u: String) { _url.value = u }

    /** 设置 HTTP 方法（建议取自 [HttpClientManager.SUPPORTED_METHODS]，大小写不敏感）。 */
    fun setMethod(m: String) { _method.value = m }

    /** 设置请求头多行文本。 */
    fun setHeaders(h: String) { _headers.value = h }

    /** 设置请求体。 */
    fun setBody(b: String) { _body.value = b }

    /**
     * 发起请求。
     *
     * 重入保护：内核已在执行时直接返回，避免连点两次把在途请求的会话号顶掉
     * （那会让第一次请求的结果被静默丢弃）。
     *
     * 非体方法（GET/HEAD/…）不发送 body：内核虽然会记一条日志提示被忽略，
     * 但这里提前过滤可以让网络层拿到干净的语义，也省掉一条无意义的告警。
     */
    fun send() {
        if (manager.isRunning.value) return
        val verb = _method.value.trim().uppercase(Locale.US)
        viewModelScope.launch {
            manager.execute(
                url = _url.value,
                method = verb,
                headers = parseHeaders(_headers.value),
                body = if (verb in HttpClientManager.BODY_METHODS) _body.value else "",
            )
        }
    }

    /** 取消在途请求；无请求时是安全空操作。 */
    fun cancel() = manager.cancel()

    /** 清空日志缓冲（不影响累计统计）。 */
    fun clearLog() = manager.clearLog()

    /** 把字节数格式化为人可读文本，供统计面板展示流量。 */
    fun formatBytes(bytes: Long): String = manager.formatBytes(bytes)

    /**
     * 把多行文本解析为请求头 Map。
     *
     * 规则：
     * - 空行、以 `#` 开头的注释行、不含 `:` 的行 → 忽略
     * - 冒号前的部分即头名，为空则忽略
     * - 冒号后全部内容即头值（头值里再出现冒号不会截断，例如 `Referer: https://x`）
     * - 同名头重复出现时后者覆盖前者，与 curl 的 `-H` 行为一致
     *
     * 用 [LinkedHashMap] 保序，保证发出去的请求头顺序与用户书写顺序一致，
     * 便于对着抓包结果逐行核对。
     */
    private fun parseHeaders(raw: String): Map<String, String> {
        val parsed = LinkedHashMap<String, String>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val separator = trimmed.indexOf(':')
            if (separator <= 0) return@forEach
            val key = trimmed.substring(0, separator).trim()
            if (key.isEmpty()) return@forEach
            parsed[key] = trimmed.substring(separator + 1).trim()
        }
        return parsed
    }

    override fun onCleared() {
        manager.destroy()
        super.onCleared()
    }

    companion object {
        /** 默认请求地址：一个回显请求内容的公共端点，便于首次打开就能验证链路。 */
        private const val DEFAULT_URL = "https://httpbin.org/get"

        private const val DEFAULT_METHOD = "GET"
    }
}
