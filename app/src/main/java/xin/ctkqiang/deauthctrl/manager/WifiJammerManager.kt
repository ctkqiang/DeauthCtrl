package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import xin.ctkqiang.deauthctrl.model.JammerLogEntry
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

sealed class JammerScanState {
    object Idle : JammerScanState()
    object Scanning : JammerScanState()
    data class Results(val networks: List<JammerWifiNetwork>) : JammerScanState()
    data class Error(val message: String) : JammerScanState()
}

data class JammerWifiNetwork(
    val ssid: String, val bssid: String, val signalStrength: Int,
    val frequency: Int, val channel: Int, val security: String,
)

/**
 * 本机热点循环器运行快照。
 *
 * 字段含义严格对应 Android 公共 API 的真实语义，不含任何夸大：
 * - [cycles]           完整遍历一轮全部 SSID 的次数
 * - [hotspotStarts]    成功调起 LocalOnlyHotspot 的次数
 * - [hotspotFailures]  启动失败次数（渠道占用 / 模式冲突 / 策略禁止）
 * - [cyclesPerMinute]  每分钟完成轮数，用于观察设备限流
 * - [lastError]        最近一次失败原因，供 UI 直接展示
 */
data class WifiJammerStats(
    val cycles: Int = 0,
    val hotspotStarts: Int = 0,
    val hotspotFailures: Int = 0,
    val activeTargets: Int = 0,
    val uptimeMs: Long = 0L,
    val cyclesPerMinute: Float = 0f,
    val lastError: String? = null,
)

/**
 * WiFi 压制引擎（本机热点循环器）
 *
 * ## 能力边界（务必先读）
 * 未 root 的 Android 无法进入监听模式，也无法通过 `AF_PACKET` 原始套接字注入
 * 802.11 管理帧，因此**不能**向第三方 AP 或客户端发送 deauth / disassoc 帧，
 * 也**不能**干扰他人已建立的连接。
 *
 * 本模块唯一真实动作是反复创建并销毁**本机** LocalOnlyHotspot，并在 API 30+
 * 把 SSID 依次设为扫描到的目标名称（API 26-29 公共 API 只能创建系统随机 SSID
 * 热点，UI/日志会如实说明）。宏观效果是：本机在无线环境里周期性出现同名
 * 热点，手机自身射频被占用、功耗上升、WiFi 连接可能被短暂打断。
 *
 * 任何"干扰了 N 个网络"的说法都是不成立的。UI 与日志只陈述上述事实。
 * 即使 SSID 与目标相同，也只是本机真实 SoftAP 的信标，不会改变目标 AP 的
 * BSSID/信道/加密，也不会断开任何客户端。
 *
 * ## 线程与资源模型
 * - 所有计数器为原子类型，可被 binder 回调线程、Default 调度器与主线程并发访问
 * - 热点句柄存放于 [AtomicReference]，`getAndSet(null)` 保证释放幂等
 * - [startHotspotAndAwait] 会**等待** `onStarted`/`onFailed` 再返回，
 *   避免"发完就走"造成的句柄泄漏与回调错配
 * - 会话结束后通过 `finally` 兜底释放，`isRunning` 不存在卡死路径
 */
class WifiJammerManager(private val context: Context) {

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    /** 启动门闩:扫描阶段与热点循环全程占用,防止多路 runCycleLoop 并发抢同一个 LOHS 会话 */
    private val startGate = java.util.concurrent.atomic.AtomicBoolean(false)
    private var jammerJob: Job? = null

    /** 进程内共享能力快照：本模块只可能是"本机 SoftAP"，不是帧注入 */
    private val capabilityProbe = CapabilityProbe.get(context)
    val capabilities: StateFlow<DeviceCapabilities> = capabilityProbe.capabilities

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private val _scanState = MutableStateFlow<JammerScanState>(JammerScanState.Idle)
    val scanState: StateFlow<JammerScanState> = _scanState.asStateFlow()
    private val _log = MutableStateFlow<List<JammerLogEntry>>(emptyList())
    val log: StateFlow<List<JammerLogEntry>> = _log.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _targetCount = MutableStateFlow(0)
    val targetCount: StateFlow<Int> = _targetCount.asStateFlow()
    private val _stats = MutableStateFlow(WifiJammerStats())
    val stats: StateFlow<WifiJammerStats> = _stats.asStateFlow()

    private val cycles = AtomicInteger(0)
    private val hotspotStarts = AtomicInteger(0)
    private val hotspotFailures = AtomicInteger(0)
    private val sessionStartMs = AtomicLong(0L)
    private val reservation = AtomicReference<WifiManager.LocalOnlyHotspotReservation?>(null)

    /** 等待 onStopped 的信号：每次 start 重建，停止后最多等 [STOP_TIMEOUT_MS] */
    @Volatile
    private var stopSignal: CompletableDeferred<Unit>? = null

    /** 运行时是否真的能指定 SSID：API 30+ 且系统提供 SoftApConfiguration 接口 */
    @Volatile
    private var ssidConfigUnavailable = false

    val ssidConfigurable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !ssidConfigUnavailable

    private val logBuffer = ArrayDeque<JammerLogEntry>(LOG_LIMIT)
    private val logLock = Any()

    /**
     * 扫描附近网络并启动热点循环。
     *
     * @param toggleMs  每次热点保持时长，内部钳制到 [TOGGLE_MIN_MS]..[TOGGLE_MAX_MS]
     * @param maxCycles 完成多少轮后自动停止；`0` 表示不限制
     */
    fun scanAndStart(toggleMs: Long = DEFAULT_TOGGLE_MS, maxCycles: Int = 0) {
        // 扫描阶段 _isRunning 尚未置 true(要到热点循环才置),单独用原子门闩防重复启动
        if (!startGate.compareAndSet(false, true)) {
            _error.value = L10n.get(R.string.mgr_wifij_busy)
            return
        }
        if (!wifiManager.isWifiEnabled) {
            startGate.set(false)
            _error.value = "WiFi is off, enable WLAN first"
            log("SCAN", "abort: WiFi is disabled")
            return
        }
        if (!isLocationServiceEnabled()) {
            startGate.set(false)
            _error.value = L10n.get(R.string.mgr_wifij_location_off)
            _scanState.value = JammerScanState.Error(
                L10n.get(R.string.mgr_wifij_location_off_detail),
            )
            log("SCAN", "abort: system location service is disabled")
            return
        }
        _scanState.value = JammerScanState.Scanning
        _error.value = null
        capabilityProbe.refreshCheap()
        log("SCAN", "starting WiFi scan ...")

        scope.launch {
            val cached = readScanResults()

            // startScan() 返回 false 表示系统节流，和 SecurityException 是两种不同原因
            var scanThrew = false
            val scanAccepted = try {
                wifiManager.startScan()
            } catch (_: SecurityException) {
                scanThrew = true
                false
            }

            if (!scanAccepted) {
                _scanState.value = JammerScanState.Error(
                    when {
                        !isLocationServiceEnabled() ->
                            L10n.get(R.string.mgr_wifij_location_off_retry)
                        scanThrew ->
                            L10n.get(
                                R.string.mgr_wifij_scan_rejected_prefix,
                                describeWifiSecurityFailure(
                                    L10n.get(R.string.mgr_wifij_action_wifi_scan),
                                ),
                            )
                        else ->
                            L10n.get(R.string.mgr_wifij_scan_throttled)
                    }
                )
                log("SCAN", "abort: scan rejected (permission/platform throttle)")
                return@launch
            }
            delay(SCAN_SETTLE_MS)

            val fresh = readScanResults()
            val all = (fresh.ifEmpty { cached }).distinctBy { it.bssid }
            if (all.isEmpty()) {
                _scanState.value = JammerScanState.Error("no WiFi networks found")
                log("SCAN", "abort: no networks found")
                return@launch
            }

            _scanState.value = JammerScanState.Results(all)
            _targetCount.value = all.size
            val ssids = all.map { it.ssid }.distinct()
            log("SCAN", "found ${all.size} networks / ${ssids.size} unique SSIDs")

            jammerJob = launch {
                runCycleLoop(ssids, toggleMs.coerceIn(TOGGLE_MIN_MS, TOGGLE_MAX_MS), maxCycles)
            }
        }
    }

    /**
     * 热点循环主循环。
     *
     * 每轮：等待热点起来 → 保持 [toggleMs] → 释放 → 记账。由于必须等待系统回调，
     * 实际轮速受限于设备调起热点的耗时，这是平台约束而非节流开关。
     */
    private suspend fun CoroutineScope.runCycleLoop(ssids: List<String>, toggleMs: Long, maxCycles: Int) {
        _isRunning.value = true
        cycles.set(0)
        hotspotStarts.set(0)
        hotspotFailures.set(0)
        sessionStartMs.set(SystemClock.elapsedRealtime())
        publishStats()

        val total = ssids.size
        val limitLabel = if (maxCycles > 0) maxCycles.toString() else "unlimited"
        log("JAMMER", "cycling local hotspot over $total SSIDs | hold=${toggleMs}ms | maxCycles=$limitLabel")

        var index = 0
        var lastPublishMs = 0L

        try {
            while (isActive && _isRunning.value) {
                if (total <= 0) {
                    _error.value = L10n.get(R.string.mgr_wifij_no_ssid)
                    break
                }
                val ssid = ssids[index % total]
                if (startHotspotAndAwait(ssid)) {
                    hotspotStarts.incrementAndGet()
                    if (!ssidConfigurable) {
                        log("HOTSPOT", L10n.get(R.string.mgr_wifij_log_random_ssid))
                    } else {
                        log("HOTSPOT", "started SSID=$ssid")
                    }
                    delay(toggleMs)
                    stopHotspotAndAwait()
                } else {
                    delay(FAILURE_BACKOFF_MS)
                }

                index++
                if (total > 0 && index % total == 0) {
                    val done = cycles.incrementAndGet()
                    log(
                        "JAMMER",
                        "cycle $done done | starts=${hotspotStarts.get()} | failed=${hotspotFailures.get()}"
                    )
                    if (maxCycles > 0 && done >= maxCycles) {
                        log("JAMMER", "--max-cycles=$maxCycles reached, stopping")
                        break
                    }
                }

                val now = SystemClock.elapsedRealtime()
                if (now - lastPublishMs >= STATS_PUBLISH_INTERVAL_MS) {
                    publishStats()
                    lastPublishMs = now
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            val detail = t.message ?: t.javaClass.simpleName
            _error.value = "cycle loop aborted: $detail"
            log("JAMMER", "aborted: $detail")
        } finally {
            releaseHotspot()
            _isRunning.value = false
            startGate.set(false) // 释放启动门闩,允许下一次完整会话
            publishStats()
        }
    }

    /**
     * 调起本机热点并等待系统回调，返回是否成功。
     *
     * - API 30+ 用 [SoftApConfiguration] 真正应用目标 SSID；API 26-29 公共 API
     *   只能创建系统随机 SSID 热点，日志会如实说明
     * - 回调使用主 Looper / 主线程 Executor；不再传入 null（Default 线程没有 Looper）
     * - 若协程已取消、超时或会话已停止，晚到的 onStarted 会**立即关闭**句柄
     */
    private suspend fun startHotspotAndAwait(ssid: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            _error.value = "LocalOnlyHotspot requires Android 8.0 (API 26) or newer"
            return false
        }

        val started = withTimeoutOrNull(START_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val stoppedSignal = CompletableDeferred<Unit>()
                stopSignal = stoppedSignal

                val callback = object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation?) {
                        if (!cont.isActive || !_isRunning.value) {
                            closeQuietly(r)
                            return
                        }
                        reservation.set(r)
                        cont.resume(true)
                    }

                    override fun onStopped() {
                        // 不在这里清空 reservation:旧请求的 onStopped 可能在新请求 onStarted 之后到达,
                        // 无条件 set(null) 会把新热点句柄丢掉,导致 stop() 之后热点仍在
                        if (!stoppedSignal.isCompleted) stoppedSignal.complete(Unit)
                    }

                    override fun onFailed(reason: Int) {
                        hotspotFailures.incrementAndGet()
                        val message = hotspotFailureMessage(reason)
                        _error.value = message
                        log("HOTSPOT", "start failed: $message")
                        if (cont.isActive) cont.resume(false)
                    }
                }

                try {
                    val configured = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        startWithSoftApConfiguration(ssid, callback)
                    } else {
                        false
                    }
                    if (!configured) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            ssidConfigUnavailable = true
                            log(
                                "HOTSPOT",
                                L10n.get(R.string.mgr_wifij_softap_fallback, ssid),
                            )
                        }
                        @Suppress("DEPRECATION")
                        wifiManager.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
                    }
                } catch (_: SecurityException) {
                    val message = describeWifiSecurityFailure(
                        L10n.get(R.string.mgr_wifij_action_hotspot_start),
                    )
                    _error.value = message
                    hotspotFailures.incrementAndGet()
                    log("HOTSPOT", message)
                    if (cont.isActive) cont.resume(false)
                } catch (t: Throwable) {
                    val message = "hotspot start error: ${t.javaClass.simpleName}: ${t.message}"
                    _error.value = message
                    hotspotFailures.incrementAndGet()
                    log("HOTSPOT", message)
                    if (cont.isActive) cont.resume(false)
                }
            }
        }

        if (started == null) {
            hotspotFailures.incrementAndGet()
            _error.value = L10n.get(R.string.mgr_wifij_start_timeout, START_TIMEOUT_MS)
            log("HOTSPOT", _error.value.orEmpty())
            return false
        }
        return started
    }

    /**
     * 通过反射调用 API 30+ 的 `SoftApConfiguration` + `startLocalOnlyHotspotWithConfiguration`。
     *
     * 本仓库编译所用的 android-34 SDK stub 缺少 `SoftApConfiguration.Builder` /
     * `BAND_2GHZ` / `startLocalOnlyHotspotWithConfiguration`（android-36 才补全），
     * 直接引用无法编译；反射在 Android 11+ 真机上调用同一公共 API。
     * `setBand` 是 API 31+，失败则交给系统选频段。
     *
     * @return true 表示请求已交给系统；false 表示接口不可用，调用方应退回
     *         随机 SSID 的旧接口，并在日志中如实说明目标 SSID 未应用。
     */
    private fun startWithSoftApConfiguration(
        ssid: String,
        callback: WifiManager.LocalOnlyHotspotCallback,
    ): Boolean {
        // 只有"类/方法确实不存在"才返回 false（调用方退回随机 SSID）；
        // 调用过程抛出的 SecurityException 等原样上抛，由调用方精确归因。
        val builderClass = try {
            Class.forName("android.net.wifi.SoftApConfiguration\$Builder")
        } catch (_: ClassNotFoundException) {
            return false
        }
        val builder = builderClass.getDeclaredConstructor().newInstance()
        builderClass.getMethod("setSsid", String::class.java).invoke(builder, ssid)
        // AOSP 中 SoftApConfiguration.BAND_2GHZ == 1；编译 stub 缺该常量
        runCatching {
            builderClass.getMethod("setBand", Integer.TYPE)
                .invoke(builder, SOFT_AP_BAND_2GHZ)
        }
        val config = builderClass.getMethod("build").invoke(builder)
        val configClass = Class.forName("android.net.wifi.SoftApConfiguration")
        val method = try {
            WifiManager::class.java.getMethod(
                "startLocalOnlyHotspotWithConfiguration",
                configClass,
                java.util.concurrent.Executor::class.java,
                WifiManager.LocalOnlyHotspotCallback::class.java,
            )
        } catch (_: NoSuchMethodException) {
            return false
        }
        try {
            method.invoke(wifiManager, config, ContextCompat.getMainExecutor(context), callback)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // 反射调用会把真实异常包一层，这里解包保证 SecurityException 归因准确
            when (val cause = e.cause) {
                is SecurityException -> throw cause
                is RuntimeException -> throw cause
                null -> throw IllegalStateException("startLocalOnlyHotspotWithConfiguration failed")
                else -> throw IllegalStateException(cause)
            }
        }
        return true
    }

    /**
     * 关闭热点并等待 onStopped（最多 [STOP_TIMEOUT_MS]），确保下一次
     * startLocalOnlyHotspot 不会撞上"上一请求未完成"。
     */
    private suspend fun stopHotspotAndAwait() {
        val r = reservation.getAndSet(null) ?: return
        val signal = stopSignal
        closeQuietly(r)
        withTimeoutOrNull(STOP_TIMEOUT_MS) { signal?.await() }
        stopSignal = null
    }

    /**
     * 释放热点。`getAndSet(null)` 使重复调用安全，也让"停止"与"回调到达"并发时
     * 不会有第二个持有者漏掉句柄。
     */
    private fun releaseHotspot() {
        closeQuietly(reservation.getAndSet(null))
    }

    // ─── 权限 / 定位 ────────────────────────────────────────────────────────────

    private fun isLocationServiceEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm.isLocationEnabled
            } else {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: Throwable) {
            false
        }
    }

    /** SecurityException 精确归因，不再统一写成 "permission denied" 或误归 CHANGE_WIFI_STATE */
    private fun describeWifiSecurityFailure(action: String): String {
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add("NEARBY_WIFI_DEVICES")
            }
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add("ACCESS_FINE_LOCATION")
            }
        }
        return if (missing.isEmpty()) {
            L10n.get(R.string.mgr_wifij_rejected_unknown, action)
        } else {
            L10n.get(
                R.string.mgr_wifij_rejected_missing,
                action,
                missing.joinToString(" / "),
            )
        }
    }

    private fun closeQuietly(r: WifiManager.LocalOnlyHotspotReservation?) {
        if (r == null) return
        try {
            r.close()
        } catch (_: Throwable) {
        }
    }

    private fun hotspotFailureMessage(reason: Int): String = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "no free channel (reason=$reason)"
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE ->
            "incompatible mode, disconnect from current AP (reason=$reason)"
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED ->
            "tethering disallowed by device policy (reason=$reason)"
        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC -> "generic failure (reason=$reason)"
        else -> "unknown failure (reason=$reason)"
    }

    private fun readScanResults(): List<JammerWifiNetwork> = try {
        wifiManager.scanResults
            ?.filter { !it.SSID.isNullOrBlank() }
            ?.distinctBy { it.BSSID ?: it.SSID }
            ?.sortedByDescending { it.level }
            ?.map { it.toNetwork() }
            ?: emptyList()
    } catch (_: SecurityException) {
        emptyList()
    }

    fun stop() {
        _isRunning.value = false
        startGate.set(false)
        jammerJob?.cancel()
        jammerJob = null
        releaseHotspot()
        _scanState.value = JammerScanState.Idle
        _targetCount.value = 0
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    fun clearLog() {
        synchronized(logLock) { logBuffer.clear() }
        _log.value = emptyList()
        cycles.set(0)
        hotspotStarts.set(0)
        hotspotFailures.set(0)
        publishStats()
    }

    fun formatUptime(ms: Long): String {
        val totalSeconds = ms / 1000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun publishStats() {
        val start = sessionStartMs.get()
        val uptime = if (start > 0L) SystemClock.elapsedRealtime() - start else 0L
        val done = cycles.get()
        _stats.value = WifiJammerStats(
            cycles = done,
            hotspotStarts = hotspotStarts.get(),
            hotspotFailures = hotspotFailures.get(),
            activeTargets = _targetCount.value,
            uptimeMs = uptime,
            cyclesPerMinute = if (uptime > 0L) done * 60_000f / uptime else 0f,
            lastError = _error.value,
        )
    }

    private fun log(module: String, message: String) {
        val entry = JammerLogEntry(module = module, message = message)
        val snapshot: List<JammerLogEntry>
        synchronized(logLock) {
            if (logBuffer.size >= LOG_LIMIT) logBuffer.pollFirst()
            logBuffer.addLast(entry)
            snapshot = logBuffer.toList()
        }
        _log.value = snapshot
    }

    private fun ScanResult.toNetwork() = JammerWifiNetwork(
        ssid = SSID?.ifBlank { "<hidden>" } ?: "<hidden>",
        bssid = BSSID ?: "<unknown>",
        signalStrength = level,
        frequency = frequency,
        channel = freqToCh(frequency),
        security = parseSec(capabilities),
    )

    private fun freqToCh(f: Int): Int = when (f) {
        2412 -> 1; 2417 -> 2; 2422 -> 3; 2427 -> 4; 2432 -> 5; 2437 -> 6
        2442 -> 7; 2447 -> 8; 2452 -> 9; 2457 -> 10; 2462 -> 11; 2467 -> 12
        2472 -> 13; 2484 -> 14
        5180 -> 36; 5200 -> 40; 5220 -> 44; 5240 -> 48
        5260 -> 52; 5280 -> 56; 5300 -> 60; 5320 -> 64
        5500 -> 100; 5520 -> 104; 5540 -> 108; 5560 -> 112
        5580 -> 116; 5600 -> 120; 5620 -> 124; 5640 -> 128
        5660 -> 132; 5680 -> 136; 5700 -> 140; 5720 -> 144
        5745 -> 149; 5765 -> 153; 5785 -> 157; 5805 -> 161; 5825 -> 165
        else -> 0
    }

    private fun parseSec(cap: String): String = when {
        cap.contains("WPA3") || cap.contains("SAE") -> "WPA3"
        cap.contains("WPA2") -> "WPA2"
        cap.contains("WPA") -> "WPA"
        cap.contains("WEP") -> "WEP"
        cap.contains("OWE") -> "OWE"
        cap.isNotBlank() && cap != "[ESS]" -> "WPA/WPA2"
        else -> "OPEN"
    }

    companion object {
        /**
         * 单次热点保持时长的下界（毫秒）。
         *
         * LOHS 是异步 Binder 调用：start→onStarted→close→onStopped 本身就需要
         * 数百毫秒到数秒，旧的 60-300ms 节奏必然撞上"同一时刻只允许一个未完成
         * 请求"并吞掉异常。现在最短保持 5 秒。
         */
        const val TOGGLE_MIN_MS = 5_000L

        /** 单次热点保持时长的上界（毫秒） */
        const val TOGGLE_MAX_MS = 15_000L

        /** 默认单次热点保持时长（毫秒） */
        const val DEFAULT_TOGGLE_MS = 5_000L

        /** 允许 UI 设置的最大轮数上界 */
        const val MAX_CYCLES_LIMIT = 10_000

        private const val SCAN_SETTLE_MS = 1500L
        private const val FAILURE_BACKOFF_MS = 500L
        private const val START_TIMEOUT_MS = 5_000L
        private const val STOP_TIMEOUT_MS = 500L
        private const val STATS_PUBLISH_INTERVAL_MS = 500L

        /** AOSP SoftApConfiguration.BAND_2GHZ 的数值；编译 stub 未提供该常量 */
        private const val SOFT_AP_BAND_2GHZ = 1
        private const val LOG_LIMIT = 200
    }
}
