package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.PingManager
import xin.ctkqiang.deauthctrl.manager.PingResult

/**
 * Ping 泛洪 ViewModel
 *
 * 管理持续 ICMP Ping 探测的状态，支持无限发包和实时统计。
 * 默认目标为 8.8.8.8（Google Public DNS），用户可自定义目标 IP。
 *
 * ## 状态流
 * - isRunning: Ping 探测运行状态
 * - host: 目标主机（IP 或域名）
 * - results: Ping 探测结果列表（按时间顺序排列，最新在末尾）
 * - sent/received/loss: 发包统计（发送数/接收数/丢包率百分比）
 * - min/avg/max: RTT 统计（最小/平均/最大往返时延，单位 ms）
 * - error: 错误信息
 */
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

    /** 设置目标主机地址 */
    fun setHost(h: String) { _host.value = h }

    /**
     * 开始持续 Ping 探测
     *
     * 清空历史记录和统计信息，以 count=0（无限）、interval=150ms 参数启动 PingManager。
     * 结果通过回调实时追加到 _results 列表，统计信息每 150ms 更新一次。
     */
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

    /**
     * 停止 Ping 探测
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
