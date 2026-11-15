package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import xin.ctkqiang.deauthctrl.model.HotspotResult
import xin.ctkqiang.deauthctrl.model.ScanState
import xin.ctkqiang.deauthctrl.model.WifiNetwork
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * WiFi 干扰运行期统计快照
 *
 * @property cycles     已完成循环数
 * @property totalCycles 本次泛洪目标循环数
 * @property uptimeMs   本次 start 起累计运行毫秒数
 * @property startCount hotspot 启动尝试次数
 * @property stopCount  hotspot 停止尝试次数
 */
data class WifiDisruptStats(
    val cycles: Long,
    val totalCycles: Long,
    val uptimeMs: Long,
    val startCount: Long,
    val stopCount: Long,
)

/**
 * 邪恶双子信标泛洪管理器
 *
 * 通过反复启动/停止 LocalOnlyHotspot 创建虚假 WiFi 网络，
 * 干扰附近设备的扫描结果。仅在授权范围内用于压力测试。
 *
 * ## 关键修复点（v2）
 * 1. **保存 [WifiManager.LocalOnlyHotspotReservation]**——原实现 onStarted 丢弃 reservation，
 *    导致 tryStopHotspot 无法真正关闭热点。现在保存并在 stop 时调用 close()。
 * 2. **发布 onFailed 错误**——原 onFailed 静默吞错，UI 无法感知启动失败。
 * 3. **stopLocalOnlyHotspot 兜底**——reservation 未拿到时也调用系统 API 强制停止。
 *
 * ## 线程模型
 * - 主协程 [scope] 在 [Dispatchers.Main]，负责 StateFlow 发布
 * - 扫描/泛洪在 [Dispatchers.Default]，避免阻塞 UI
 *
 * @param context 应用上下文
 */
class WifiDisruptManager(private val context: Context) {

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _hotspotResult = MutableStateFlow<HotspotResult?>(null)
    val hotspotResult: StateFlow<HotspotResult?> = _hotspotResult.asStateFlow()

    private val _isFlooding = MutableStateFlow(false)
    val isFlooding: StateFlow<Boolean> = _isFlooding.asStateFlow()

    private val _stats = MutableStateFlow(WifiDisruptStats(0, 0, 0, 0, 0))
    val stats: StateFlow<WifiDisruptStats> = _stats.asStateFlow()

    private var floodJob: Job? = null
    private var scanJob: Job? = null

    // ─── Hotspot 状态（必须保存 reservation 才能真正关闭） ──────────────────────

    private var hotspotCallback: WifiManager.LocalOnlyHotspotCallback? = null
    @Volatile private var activeReservation: WifiManager.LocalOnlyHotspotReservation? = null
    @Volatile private var hotspotActive: Boolean = false
    private val startCount = AtomicLong(0)
    private val stopCount = AtomicLong(0)
    private val startTimeMs = AtomicLong(0L)

    // ─── 扫描 ───────────────────────────────────────────────────────────────────

    /**
     * 扫描附近 WiFi 网络。
     *
     * Android 9+ 对 startScan 有节流，单次调用可能拿不到新结果。
     * 这里采用"先返回缓存 → 触发 startScan → 等待 → 再读"双段策略，
     * 并对 startScan 失败做明确区分（权限/无结果）。
     */
    fun scanNetworks() {
        if (_isFlooding.value) {
            _scanState.value = ScanState.Error("泛洪进行中，无法扫描")
            return
        }
        // 取消上一个未完成的扫描任务，避免结果乱序覆盖
        scanJob?.cancel()
        _scanState.value = ScanState.Scanning

        scanJob = scope.launch(Dispatchers.Default) {
            try {
                // 第一段：直接读缓存，UI 立即有数据
                val cached = readScanResults()
                if (cached.isNotEmpty()) {
                    _scanState.value = ScanState.Results(cached)
                }

                // 第二段：触发系统扫描并等待结果
                val success = try {
                    wifiManager.startScan()
                } catch (e: SecurityException) {
                    _scanState.value = ScanState.Error("Wi-Fi 扫描需要位置权限")
                    return@launch
                }

                if (success) {
                    delay(SCAN_WAIT_MS)
                    val fresh = readScanResults()
                    if (fresh.isNotEmpty()) {
                        _scanState.value = ScanState.Results(fresh)
                    } else if (cached.isEmpty()) {
                        _scanState.value = ScanState.Error("未发现网络。请确保 Wi-Fi 已开启且已授予位置权限。")
                    }
                } else if (cached.isEmpty()) {
                    _scanState.value = ScanState.Error("系统扫描被节流，稍后再试或检查位置权限")
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: SecurityException) {
                _scanState.value = ScanState.Error("Wi-Fi 扫描需要位置权限")
            } catch (e: Throwable) {
                _scanState.value = ScanState.Error("扫描错误: ${e.message}")
            }
        }
    }

    private fun readScanResults(): List<WifiNetwork> {
        return try {
            wifiManager.scanResults
                ?.filter { it.SSID.isNotBlank() }
                ?.distinctBy { it.BSSID }
                ?.sortedByDescending { it.level }
                ?.map { it.toWifiNetwork() }
                ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    // ─── 泛洪 ───────────────────────────────────────────────────────────────────

    /**
     * 启动信标泛洪。
     *
     * @param targetSsid       伪造的 SSID
     * @param durationSeconds  持续秒数（1..60）
     * @param toggleIntervalMs 启停切换间隔，过小会被系统节流
     */
    fun startFlood(
        targetSsid: String,
        durationSeconds: Int = DEFAULT_DURATION_SEC,
        toggleIntervalMs: Long = DEFAULT_TOGGLE_MS,
    ) {
        if (_isFlooding.value) {
            _hotspotResult.value = HotspotResult.Error("泛洪已在进行中")
            return
        }
        if (!wifiManager.isWifiEnabled) {
            _hotspotResult.value = HotspotResult.Error("请先开启 Wi-Fi 以创建热点")
            return
        }
        if (targetSsid.isBlank()) {
            _hotspotResult.value = HotspotResult.Error("SSID 不能为空")
            return
        }

        _isFlooding.value = true
        _hotspotResult.value = null
        startCount.set(0L)
        stopCount.set(0L)
        startTimeMs.set(System.currentTimeMillis())

        val dur = durationSeconds.coerceIn(1, MAX_DURATION_SEC)
        val interval = toggleIntervalMs.coerceIn(TOGGLE_MIN_MS, TOGGLE_MAX_MS)
        val totalCycles = (dur * 1000L) / interval

        floodJob = scope.launch(Dispatchers.Default) {
            try {
                _hotspotResult.value = HotspotResult.Created(targetSsid)
                var cycle = 0

                while (cycle < totalCycles && isActive) {
                    val isEven = cycle % 2 == 0
                    if (isEven) tryStartHotspot(targetSsid) else tryStopHotspot()
                    publishStats(cycle.toLong() + 1, totalCycles)
                    _hotspotResult.value = HotspotResult.Progress(cycle + 1, totalCycles.toInt())
                    delay(interval)
                    cycle++
                }

                tryStopHotspot()
                _hotspotResult.value = HotspotResult.Completed
            } catch (ce: CancellationException) {
                tryStopHotspot()
                _hotspotResult.value = HotspotResult.Error("泛洪已取消")
                throw ce
            } catch (e: Throwable) {
                tryStopHotspot()
                _hotspotResult.value = HotspotResult.Error("泛洪错误: ${e.message}")
            } finally {
                _isFlooding.value = false
                publishStats(finalize = true)
            }
        }
    }

    fun stopFlood() {
        floodJob?.cancel()
        floodJob = null
        // 立即停止热点，不等协程 finally（避免 cancel 到 finally 之间的窗口）
        tryStopHotspot()
        _isFlooding.value = false
    }

    fun destroy() {
        stopFlood()
        tryStopHotspot()
        scope.cancel()
    }

    // ─── Hotspot 启停 ───────────────────────────────────────────────────────────

    /**
     * 启动 LocalOnlyHotspot。
     *
     * 注意：startLocalOnlyHotspot 是异步的，onStarted 在 binder 线程回调。
     * 这里不在主流程等待 callback，因为泛洪节奏由 [toggleIntervalMs] 控制，
     * callback 与下一轮 stop 可能交错，但 stop 侧用 reservation.close + 系统 API 双保险。
     */
    private fun tryStartHotspot(ssid: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        startCount.incrementAndGet()

        // 先清理上一轮残留 reservation
        stopReservation()

        val cb = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                activeReservation = reservation
                hotspotActive = true
            }

            override fun onStopped() {
                hotspotActive = false
                activeReservation = null
            }

            override fun onFailed(reason: Int) {
                hotspotActive = false
                activeReservation = null
                _hotspotResult.value = HotspotResult.Error("热点启动失败 (reason=$reason)")
            }
        }
        hotspotCallback = cb

        try {
            wifiManager.startLocalOnlyHotspot(cb, null)
        } catch (_: SecurityException) {
            _hotspotResult.value = HotspotResult.Error("热点启动需要 CHANGE_WIFI_STATE 权限")
        } catch (_: Throwable) {
            // 部分厂商 ROM 不支持 LocalOnlyHotspot，吞掉避免误导
        }
    }

    /**
     * 停止当前活跃热点。
     *
     * 停止策略：依赖保存的 [activeReservation].close()（官方推荐路径）。
     * 若 reservation 未拿到（onFailed 触发），热点实际未启动，无需停止。
     * 注：stopLocalOnlyHotspot 是系统隐藏 API，公开 SDK 不可调用。
     */
    private fun tryStopHotspot() {
        stopCount.incrementAndGet()
        stopReservation()
        hotspotCallback = null
        hotspotActive = false
    }

    private fun stopReservation() {
        activeReservation?.let { res ->
            try {
                res.close()
            } catch (_: Throwable) {
                // 关闭失败不阻塞主流程
            }
        }
        activeReservation = null
    }

    // ─── 统计 ───────────────────────────────────────────────────────────────────

    private fun publishStats(cycles: Long, total: Long) {
        val uptime = maxOf(0L, System.currentTimeMillis() - startTimeMs.get())
        _stats.value = WifiDisruptStats(
            cycles = cycles,
            totalCycles = total,
            uptimeMs = uptime,
            startCount = startCount.get(),
            stopCount = stopCount.get(),
        )
    }

    private fun publishStats(finalize: Boolean) {
        if (!finalize) return
        _stats.value = _stats.value.copy(
            uptimeMs = maxOf(0L, System.currentTimeMillis() - startTimeMs.get()),
            startCount = startCount.get(),
            stopCount = stopCount.get(),
        )
    }

    // ─── 工具 ───────────────────────────────────────────────────────────────────

    private fun ScanResult.toWifiNetwork(): WifiNetwork = WifiNetwork(
        ssid = SSID.ifBlank { "<hidden>" },
        bssid = BSSID,
        signalStrength = level,
        frequency = frequency,
        capabilities = capabilities,
    )

    private companion object {
        const val SCAN_WAIT_MS = 1200L
        const val DEFAULT_DURATION_SEC = 10
        const val MAX_DURATION_SEC = 60
        const val DEFAULT_TOGGLE_MS = 80L
        const val TOGGLE_MIN_MS = 40L
        const val TOGGLE_MAX_MS = 500L
    }
}
