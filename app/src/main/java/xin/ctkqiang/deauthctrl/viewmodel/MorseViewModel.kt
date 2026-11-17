package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.MorseManager
import xin.ctkqiang.deauthctrl.manager.MorseState

/** 摩尔斯码 ViewModel —— 持有 [MorseManager] */
class MorseViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = MorseManager(application)
    val state: StateFlow<MorseState> = manager.state

    fun send(text: String, wpm: Int, mode: MorseManager.MorseSendMode) = manager.send(text, wpm, mode)
    fun stop() = manager.stop()
    fun key(symbol: Char) = manager.keyElement(symbol)
    fun keyCharEnd() = manager.keyCharEnd()
    fun keyWordEnd() = manager.keyWordEnd()
    fun keyClear() = manager.keyClear()
    fun pendingCode(): String = manager.pendingCodeText()
}
