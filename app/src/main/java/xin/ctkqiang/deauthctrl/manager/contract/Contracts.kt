package xin.ctkqiang.deauthctrl.manager.contract

import xin.ctkqiang.deauthctrl.manager.RadarTarget
import xin.ctkqiang.deauthctrl.manager.WalkiePeer
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager 接口契约层。
 *
 * 每个 Manager 的核心公共 API 在此定义为接口，便于：
 * - 单元测试（mock 实现）
 * - 未来替换实现（如 BluetoothJammer 的 BLE 5.0 vs 传统实现）
 * - ViewModel 依赖抽象而非具体类
 */

/** 雷达扫描管理器接口 */
interface IRadarManager {
    val targets: ConcurrentHashMap<String, RadarTarget>
    val isRunning: Boolean
    fun start()
    fun stop()
    fun onTick()
}

/** 对讲机管理器接口 */
interface IWalkieTalkieManager {
    var deviceName: String
    val isRunning: Boolean
    val isTalking: Boolean
    val peers: ConcurrentHashMap<String, WalkiePeer>
    fun start(): Boolean
    fun startTalk()
    fun stopTalk()
    fun stop()
}

/** BLE 泛洪管理器接口 */
interface IBleSpamManager {
    val isRunning: Boolean
    fun startSpam()
    fun stopSpam()
    fun getProfiles(): List<*>
    fun getCurrentProfile(): *
    fun setProfile(profile: Any)
    fun getCurrentInterval(): Long
    fun setInterval(ms: Long)
}

/** 蓝牙压制管理器接口 */
interface IBluetoothJammerManager {
    val isRunning: Boolean
    fun start()
    fun stop()
}

/** WiFi 压制管理器接口 */
interface IWifiJammerManager {
    val isRunning: Boolean
    fun start()
    fun stop()
}
