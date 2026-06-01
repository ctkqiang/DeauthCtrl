package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.PortResult
import xin.ctkqiang.deauthctrl.manager.PortScannerManager

/**
 * 端口扫描 ViewModel
 *
 * 管理 Nmap 风格 TCP 端口扫描的状态。默认目标为 192.168.1.1（常见网关地址）。
 *
 * ## 状态流
 * - isRunning: 扫描运行状态
 * - host: 目标主机 IP 或域名
 * - results: 所有扫描结果（含开放和关闭端口）
 * - scanned: 已扫描端口数
 * - total: 总端口数
 * - error: 错误信息
 */
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

    /** 设置目标主机地址 */
    fun setHost(h: String) { _host.value = h }

    /**
     * 开始端口扫描
     *
     * 清空历史结果，使用默认端口列表（40+ 常见端口）和 800ms 超时参数执行扫描。
     * 结果按扫描顺序依次追加到列表。
     */
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

    /**
     * 停止端口扫描
     *
     * 取消协程 Job，设置运行标志为 false。
     */
    fun stop() {
        manager.stop()
        _isRunning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        manager.stop()
    }
}
