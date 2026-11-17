package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.GsmTowerManager
import xin.ctkqiang.deauthctrl.manager.GsmTowerState

/** 基站监视 ViewModel —— 持有 [GsmTowerManager],暴露真实小区数据 */
class GsmTowerViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = GsmTowerManager(application)
    val state: StateFlow<GsmTowerState> = manager.state

    fun refresh() = manager.refresh()
}
