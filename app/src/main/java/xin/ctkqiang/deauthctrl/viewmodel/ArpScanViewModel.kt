package xin.ctkqiang.deauthctrl.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.ArpEntry
import xin.ctkqiang.deauthctrl.manager.ArpScanManager
import xin.ctkqiang.deauthctrl.manager.ArpScanStats
import xin.ctkqiang.deauthctrl.model.JammerLogEntry

/**
 * ARP 扫描 ViewModel
 *
 * 本类**只做委托**，不自己持有任何与扫描有关的状态：
 * 运行状态、设备列表、错误、统计、日志全部直接暴露 [ArpScanManager] 的 `StateFlow`，
 * 保证 UI 看到的永远是内核的真实状态，不会出现"两个真相"。
 *
 * 相比旧实现，这里删掉了 `MutableStateFlow` 与回调拼装：
 * 旧版用 `manager.scan(callback, onComplete)` 在一个局部 `mutableListOf` 上累积结果，
 * 一旦 ViewModel 被销毁而回调仍在触发，就会往已经没人订阅的流里写数据；
 * 现在结果由内核的 `ConcurrentHashMap` 统一持有并排序，ViewModel 只订阅。
 *
 * ## 暴露给 UI 的状态流
 * - [isRunning] 扫描是否进行中（来自内核）
 * - [devices]   已发现设备列表，按 IP 第四段升序（来自内核）
 * - [error]     最近一次错误（来自内核）
 * - [stats]     扫描快照：网段/本机 IP/进度/可达数/耗时等（来自内核）
 * - [log]       环形缓冲日志，上限 [ArpScanManager] 的 `LOG_LIMIT` 条（来自内核）
 */
class ArpScanViewModel : ViewModel() {

    /** 内核。UI 需要 `LATENCY_UNKNOWN` 等常量时直接引用其伴生对象。 */
    val manager = ArpScanManager()

    val isRunning: StateFlow<Boolean> = manager.isRunning
    val devices: StateFlow<List<ArpEntry>> = manager.devices
    val error: StateFlow<String?> = manager.error
    val stats: StateFlow<ArpScanStats> = manager.stats
    val log: StateFlow<List<JammerLogEntry>> = manager.log

    /**
     * 执行一轮 /24 网段扫描。
     *
     * 内核会先取消上一轮，再同步校验本机 IP 与网段前缀；
     * 校验失败时不会启动协程，而是把原因写入 [error]（例如"未获取到局域网 IPv4 地址"），
     * UI 读 [error] 即可展示，这里不重复包装。
     */
    fun scan() {
        manager.scan()
    }

    /** 停止扫描。内核会取消协程并冻结耗时统计。 */
    fun stop() {
        manager.stop()
    }

    /** 只清空日志。刻意不重置计数器，避免统计面板在扫描中途突然归零。 */
    fun clearLog() {
        manager.clearLog()
    }

    /** 把毫秒时长格式化为 `mm:ss.S`。 */
    fun formatElapsed(ms: Long): String = manager.formatElapsed(ms)

    override fun onCleared() {
        super.onCleared()
        // destroy 会 stop + 释放协程作用域，避免 254 个探测协程在 ViewModel 销毁后仍存活
        manager.destroy()
    }
}
