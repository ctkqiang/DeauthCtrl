package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.FlipperTvLib
import xin.ctkqiang.deauthctrl.manager.IrRemoteManager

/** 万能遥控泛洪状态 */
data class FloodState(
    val running: Boolean = false,
    val button: String = "",      // 正在泛洪的按钮名
    val index: Int = 0,           // 当前候选序号(1-based)
    val total: Int = 0,
    val protocol: String = "",    // 当前候选协议
    val skipped: Int = 0,         // 因协议不支持跳过的候选数
)

/** Flipper 万能遥控 + 按键克隆库 ViewModel */
class FlipperTvViewModel(application: Application) : AndroidViewModel(application) {

    private val ir = IrRemoteManager(application)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var floodJob: Job? = null

    private val _flood = MutableStateFlow(FloodState())
    val flood: StateFlow<FloodState> = _flood

    private val _lastTransmit = MutableStateFlow<String?>(null)
    val lastTransmit: StateFlow<String?> = _lastTransmit

    /** tv.ir 解析结果(懒加载,进程内缓存) */
    val tvButtons: Map<String, List<FlipperTvLib.IrButton>> by lazy {
        runCatching {
            getApplication<Application>().assets.open("ir/tv.ir").use { FlipperTvLib.parse(it) }
        }.getOrDefault(emptyMap())
    }

    /** 单键发射(克隆库/单步用);返回协议是否支持 */
    fun transmit(protocol: String, address: Int, command: Int): Boolean = when (protocol) {
        "NEC" -> { ir.transmitNec(address and 0xFF, command and 0xFF); true }
        "NECext" -> { ir.transmitNecExtended(address and 0xFFFF, command and 0xFF); true }
        "Samsung32" -> { ir.transmitSamsung32(address, command); true }
        "RC5" -> { ir.transmitRc5(address and 0x1F, command and 0x3F); true }
        "RC5X" -> { ir.transmitRc5x(address, command); true }
        "RC6" -> { ir.transmitRc6(address, command); true }
        "SIRC" -> { ir.transmitSony(command and 0x7F, address and 0x1F, 12); true }
        "SIRC20" -> { ir.transmitSirc20(address and 0x1FFF, command and 0x7F); true }
        else -> false
    }

    /**
     * 泛洪: 依次发射某按钮的全部候选码(250ms 间隔),像 Flipper TV 万能遥控一样
     * 逐个尝试直到电视响应;再次点击同按钮停止。
     */
    fun flood(buttonName: String) {
        val cur = _flood.value
        if (cur.running && cur.button == buttonName) {
            floodJob?.cancel()
            _flood.value = FloodState(running = false)
            return
        }
        val candidates = tvButtons[buttonName].orEmpty()
        if (candidates.isEmpty()) {
            _lastTransmit.value = L10n.get(R.string.vm_flipper_no_candidates)
            return
        }
        floodJob?.cancel()
        floodJob = scope.launch {
            var skipped = 0
            candidates.forEachIndexed { i, b ->
                _flood.value = FloodState(running = true, button = buttonName, index = i + 1, total = candidates.size, protocol = b.protocol, skipped = skipped)
                if (transmit(b.protocol, b.address, b.command)) {
                    _lastTransmit.value = L10n.get(
                        R.string.vm_flipper_attempt,
                        i + 1,
                        candidates.size,
                        b.protocol,
                        "0x%04X:%02X".format(b.address and 0xFFFF, b.command and 0xFF),
                    )
                } else {
                    skipped++
                }
                delay(250)
            }
            _lastTransmit.value = L10n.get(R.string.vm_flipper_flood_done)
            _flood.value = FloodState(running = false)
        }
    }

    fun stopFlood() {
        floodJob?.cancel()
        _flood.value = FloodState(running = false)
    }
}
