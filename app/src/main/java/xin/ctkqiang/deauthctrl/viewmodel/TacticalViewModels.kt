package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import xin.ctkqiang.deauthctrl.manager.IrRemoteManager
import xin.ctkqiang.deauthctrl.manager.NfcManager
import xin.ctkqiang.deauthctrl.manager.NightVisionManager
import xin.ctkqiang.deauthctrl.manager.TacticalCameraManager
import xin.ctkqiang.deauthctrl.manager.TacticalMapManager
import xin.ctkqiang.deauthctrl.manager.VoiceSdrManager

/**
 * 战术模块 ViewModel 集合 —— 每个模块一个轻量 VM，
 * 持有对应 Manager 并随 Activity 生命周期存活（配置变更不丢状态）。
 */

class TacticalCameraViewModel(app: Application) : AndroidViewModel(app) {
    val manager = TacticalCameraManager(app)
    override fun onCleared() { manager.close() }
}

class VoiceSdrViewModel(app: Application) : AndroidViewModel(app) {
    val manager = VoiceSdrManager(app)
    override fun onCleared() { manager.stop() }
}

class IrRemoteViewModel(app: Application) : AndroidViewModel(app) {
    val manager = IrRemoteManager(app)
}

class NfcViewModel(app: Application) : AndroidViewModel(app) {
    val manager = NfcManager(app)
}

class NightVisionViewModel(app: Application) : AndroidViewModel(app) {
    val manager = NightVisionManager(app)
    override fun onCleared() { manager.stop() }
}

class TacticalMapViewModel(app: Application) : AndroidViewModel(app) {
    val manager = TacticalMapManager(app)
    override fun onCleared() { manager.stop() }
}
