package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import xin.ctkqiang.deauthctrl.manager.WifiDisruptManager
import xin.ctkqiang.deauthctrl.model.HotspotResult
import xin.ctkqiang.deauthctrl.model.ScanState
import xin.ctkqiang.deauthctrl.model.WifiNetwork
import kotlinx.coroutines.flow.StateFlow

class WifiDisruptViewModel(application: Application) : AndroidViewModel(application) {

    val manager = WifiDisruptManager(application)

    val scanState: StateFlow<ScanState> = manager.scanState
    val hotspotResult: StateFlow<HotspotResult?> = manager.hotspotResult
    val isFlooding: StateFlow<Boolean> = manager.isFlooding

    private var targetSsid: String = ""
    private var durationSeconds: Int = 10

    fun scanNetworks() = manager.scanNetworks()

    fun selectTarget(network: WifiNetwork) {
        targetSsid = network.ssid
    }

    fun setDuration(seconds: Int) {
        durationSeconds = seconds.coerceIn(1, 60)
    }

    fun getTargetSsid(): String = targetSsid
    fun getDuration(): Int = durationSeconds

    fun startFlood() {
        if (targetSsid.isBlank()) {
            manager.scanNetworks()
            return
        }
        manager.startFlood(targetSsid, durationSeconds)
    }

    fun stopFlood() = manager.stopFlood()

    override fun onCleared() {
        super.onCleared()
        manager.destroy()
    }
}