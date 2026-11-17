package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.SerialMonitorManager
import xin.ctkqiang.deauthctrl.manager.SerialMonitorState
import xin.ctkqiang.deauthctrl.manager.UsbSerialDevice

/** 串口监视 ViewModel —— 持有 [SerialMonitorManager] */
class SerialMonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = SerialMonitorManager(application)
    val state: StateFlow<SerialMonitorState> = manager.state

    fun start() = manager.start()
    fun stop() = manager.stop()
    fun connect(device: UsbSerialDevice) = manager.connect(device)
    fun disconnect() = manager.disconnect()
    fun sendLine(text: String) = manager.sendLine(text)
    fun setParams(baud: Int, dataBits: Int, stopBits: Int, parity: Int) = manager.setParams(baud, dataBits, stopBits, parity)
    fun setHexMode(on: Boolean) = manager.setHexMode(on)
    fun clearLog() = manager.clearLog()

    override fun onCleared() {
        super.onCleared()
        manager.stop()
    }
}
