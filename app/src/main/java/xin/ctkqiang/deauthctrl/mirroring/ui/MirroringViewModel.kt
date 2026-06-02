package xin.ctkqiang.deauthctrl.mirroring.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.mirroring.client.MediaCodecDecoder
import xin.ctkqiang.deauthctrl.mirroring.client.NsdDiscoveryManager
import xin.ctkqiang.deauthctrl.mirroring.client.TouchInputSender
import xin.ctkqiang.deauthctrl.mirroring.client.VideoStreamClient
import xin.ctkqiang.deauthctrl.mirroring.host.MirroringHostService
import xin.ctkqiang.deauthctrl.mirroring.host.VideoStreamServer
import xin.ctkqiang.deauthctrl.mirroring.model.MirroringDevice
import xin.ctkqiang.deauthctrl.mirroring.model.TouchEvent

/** 镜像连接状态 */
sealed class MirroringState {
    data object Idle : MirroringState()
    data object HostPreparing : MirroringState()
    data object HostLive : MirroringState()
    data object ClientScanning : MirroringState()
    data class ClientConnecting(val device: MirroringDevice) : MirroringState()
    data class ClientLive(val device: MirroringDevice) : MirroringState()
    data class Error(val message: String) : MirroringState()
}

class MirroringViewModel(application: Application) : AndroidViewModel(application) {

    // --- 核心状态 ---
    private val _state = MutableStateFlow<MirroringState>(MirroringState.Idle)
    val state: StateFlow<MirroringState> = _state

    val hostVideoPort = VideoStreamServer.PORT
    val discoveryManager = NsdDiscoveryManager(application)

    private val _connectedDevice = MutableStateFlow<MirroringDevice?>(null)
    val connectedDevice: StateFlow<MirroringDevice?> = _connectedDevice

    val videoClient = VideoStreamClient()
    val decoder = MediaCodecDecoder()
    private val touchSender = TouchInputSender()

    // --- Host ---

    fun startHostPrep() {
        val ctx = getApplication<Application>()
        ctx.startForegroundService(
            Intent(ctx, MirroringHostService::class.java).apply {
                action = MirroringHostService.ACTION_PREPARE
            }
        )
        _state.value = MirroringState.HostPreparing
    }

    fun startHostCapture(resultCode: Int, data: Intent) {
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, MirroringHostService::class.java).apply {
                action = MirroringHostService.ACTION_START_CAPTURE
                putExtra(MirroringHostService.EXTRA_RESULT_CODE, resultCode)
                putExtra(MirroringHostService.EXTRA_DATA, data)
            }
        )
        _state.value = MirroringState.HostLive
        // NSD 广播让客户端发现
        discoveryManager.registerHost(hostVideoPort, 10087)
    }

    fun stopHost() {
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, MirroringHostService::class.java).apply {
                action = MirroringHostService.ACTION_STOP
            }
        )
        discoveryManager.unregisterHost()
        _state.value = MirroringState.Idle
    }

    // --- Client ---

    fun startScanning() {
        _state.value = MirroringState.ClientScanning
        discoveryManager.startDiscovery()
    }

    fun stopScanning() {
        discoveryManager.stopDiscovery()
        if (_state.value is MirroringState.ClientScanning) {
            _state.value = MirroringState.Idle
        }
    }

    fun connectToDevice(device: MirroringDevice) {
        _state.value = MirroringState.ClientConnecting(device)
        viewModelScope.launch {
            try {
                val ok = videoClient.connect(device.hostAddress, device.videoPort)
                if (!ok) {
                    _state.value = MirroringState.Error("无法连接到 ${device.name}")
                    return@launch
                }
                touchSender.connect(device.hostAddress, device.inputPort)
                _connectedDevice.value = device
                _state.value = MirroringState.ClientLive(device)
            } catch (e: Exception) {
                _state.value = MirroringState.Error(e.message ?: "连接失败")
            }
        }
    }

    fun startDecoding(surface: android.view.Surface) {
        decoder.configure(surface)
        videoClient.startReading { frameData -> decoder.feedFrame(frameData) }
    }

    fun sendTouch(action: Int, x: Float, y: Float, pointerId: Int = 0) {
        touchSender.send(TouchEvent(action = action, x = x, y = y, pointerId = pointerId))
    }

    fun disconnectClient() {
        videoClient.disconnect()
        decoder.release()
        touchSender.disconnect()
        _connectedDevice.value = null
        _state.value = MirroringState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        disconnectClient()
        discoveryManager.stopDiscovery()
    }
}
