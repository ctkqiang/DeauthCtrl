package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import xin.ctkqiang.deauthctrl.manager.BleSpamManager
import xin.ctkqiang.deauthctrl.model.BleAdvertLogEntry
import xin.ctkqiang.deauthctrl.model.BlePayloadProfile
import kotlinx.coroutines.flow.StateFlow

class BleSpamViewModel(application: Application) : AndroidViewModel(application) {

    val manager = BleSpamManager(application)

    val log: StateFlow<List<BleAdvertLogEntry>> = manager.log
    val isRunning: StateFlow<Boolean> = manager.isRunning
    val error: StateFlow<String?> = manager.error

    private var selectedProfile = BlePayloadProfile.ALL
    private var intervalMs = 30L

    fun getProfiles(): List<BlePayloadProfile> = BlePayloadProfile.entries.toList()

    fun setProfile(profile: BlePayloadProfile) {
        selectedProfile = profile
        manager.setProfile(profile)
    }

    fun setInterval(intervalMs: Long) {
        this.intervalMs = intervalMs
        manager.setInterval(intervalMs)
    }

    fun getCurrentProfile(): BlePayloadProfile = selectedProfile
    fun getCurrentInterval(): Long = intervalMs

    fun startSpam() {
        manager.start()
    }

    fun stopSpam() {
        manager.stop()
    }

    fun clearLog() = manager.clearLog()

    override fun onCleared() {
        super.onCleared()
        manager.destroy()
    }
}