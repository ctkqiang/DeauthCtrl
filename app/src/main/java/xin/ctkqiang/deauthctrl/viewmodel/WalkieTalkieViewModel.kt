package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.WalkiePeer
import xin.ctkqiang.deauthctrl.manager.WalkieTalkieManager

/**
 * WiFi 对讲机 ViewModel
 *
 * 管理局域网 PTT（Push-to-Talk）对讲机功能的状态。
 *
 * ## 状态流
 * - isRunning: 对讲机服务是否已启动（监听 + 广播 + 音频通道）
 * - isTalking: PTT 按钮是否被按住（正在录音+发送）
 * - peers: 已发现的局域网对讲设备列表
 * - error: 错误信息
 */
class WalkieTalkieViewModel : ViewModel() {

    private val manager = WalkieTalkieManager()
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _isTalking = MutableStateFlow(false)
    val isTalking: StateFlow<Boolean> = _isTalking
    private val _peers = MutableStateFlow<List<WalkiePeer>>(emptyList())
    val peers: StateFlow<List<WalkiePeer>> = _peers
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _isLive = MutableStateFlow(false)
    val isLive: StateFlow<Boolean> = _isLive
    private val _liveCountdown = MutableStateFlow(10)
    val liveCountdown: StateFlow<Int> = _liveCountdown

    private var pollJob: Job? = null
    private var liveJob: Job? = null

    /**
     * 启动对讲机服务
     *
     * 启动 UDP 发现 + 音频通道，每 500ms 轮询设备列表和通话状态。
     */
    fun start() {
        if (manager.start()) {
            _isRunning.value = true
            _error.value = null
            pollJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    delay(500)
                    _peers.value = manager.peers.values.toList()
                    _isTalking.value = manager.isTalking
                }
            }
        } else {
            _error.value = "对讲机服务启动失败"
        }
    }

    /** PTT 按钮按下 — 开始说话 */
    fun startTalk() { if (!_isLive.value) manager.startTalk() }
    /** PTT 按钮释放 — 停止说话 */
    fun stopTalk() { if (!_isLive.value) manager.stopTalk() }

    /**
     * 免提模式 — 自动连续发送 10 秒
     *
     * 开启后自动开始说话，倒计时 10 秒后自动停止。
     * 再次点击可提前终止。
     */
    fun toggleLive() {
        if (_isLive.value) {
            _isLive.value = false
            liveJob?.cancel()
            manager.stopTalk()
        } else {
            _isLive.value = true
            _liveCountdown.value = 10
            manager.startTalk()
            liveJob = CoroutineScope(Dispatchers.Main).launch {
                while (_liveCountdown.value > 0) {
                    delay(1000)
                    _liveCountdown.value -= 1
                }
                _isLive.value = false
                manager.stopTalk()
            }
        }
    }

    /**
     * 停止对讲机服务
     *
     * 关闭所有 Socket 和音频资源。
     */
    fun stop() {
        manager.stop()
        pollJob?.cancel()
        _isRunning.value = false
        _isTalking.value = false
        _peers.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        manager.stop()
        pollJob?.cancel()
    }
}
