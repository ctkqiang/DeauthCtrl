package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import xin.ctkqiang.deauthctrl.manager.ConferenceManager

/** 局域网战术会议 ViewModel —— 持有会议内核,随 Activity 存活 */
class ConferenceViewModel(app: Application) : AndroidViewModel(app) {
    val manager = ConferenceManager(app)

    override fun onCleared() {
        super.onCleared()
        manager.stop()
    }
}
