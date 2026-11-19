package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import xin.ctkqiang.deauthctrl.model.HotspotResult
import xin.ctkqiang.deauthctrl.model.ScanState
import xin.ctkqiang.deauthctrl.model.WifiNetwork
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

/**
 * 本机热点轮换运行期统计快照。
 *
 * 字段语义严格对应 Android 公共 API 的真实动作，不再把"循环次数"当成
 * "干扰了多少个网络"：
 *
 * @property cycles       已尝试的热点启停周期数（attempted）
 * @property totalCycles  本次计划尝试的周期数
 * @property uptimeMs     本次 start 起累计运行毫秒数
 * @property startCount   真正收到 onStarted 并拿到 reservation 的次数（succeeded）
 * @property stopCount    主动关闭热点句柄的次数
 * @property failedCount  启动失败次数（渠道占用 / 模式冲突 / 权限 / 超时）
 * @property actualSsid   最近一次真实创建的热点 SSID（API 30+ 为目标 SSID，26-29 为系统随机值）
 * @property lastError    最近一次失败或平台限制说明
 */
data class WifiDisruptStats(
    val cycles: Long,
    val totalCycles: Long,
    val uptimeMs: Long,
    val startCount: Long,
    val stopCount: Long,
    val failedCount: Long = 0L,
    val actualSsid: String? = null,
    val lastError: String? = null,
)

/** 本机热点状态机：IDLE → STARTING → ACTIVE → STOPPING → IDLE */
enum class HotspotPhase { IDLE, STARTING, ACTIVE, STOPPING }

/**
 * 本机 SoftAP 信标轮换管理器（真实 AP，非帧注入）
 *
 * ## 能力边界（UI 文案必须与此一致）
 * 未 root 的 stock Android 没有 CAP_NET_RAW，AF_PACKET raw socket 被 SELinux 拒绝，
 * 公共 API 也不提供 monitor mode / 802.11 帧注入。因此本模块**不会**发送
 * deauth / disassoc / beacon 帧，**不会**断开任何客户端，也**不能**改变目标 AP 的
 * BSSID / 信道 / 加密。
 *
 * 唯一真实动作是：周期性创建**本机** LocalOnlyHotspot 并在 onStarted 后保持
 * 秒级时间再关闭。API 30+ 可通过 [SoftApConfiguration] 指定 SSID；API 26-29
 * 只能创建系统随机 SSID 的热点。启动/关闭热点可能短暂中断本机当前 Wi-Fi。
 *
 * 旧实现的 40-80ms 启停与 LOHS 的"每个应用同一时刻只允许一个未完成请求"模型
 * 冲突，会抛 IllegalStateException 并被吞掉，导致统计造假。现在改为显式状态机：
 * 用 [suspendCancellableCoroutine] 等 onStarted / onFailed，停止后等 onStopped
 * 或最多 [STOP_TIMEOUT_MS] 再开始下一轮；每次 start 分配 generation，
 * 晚到的 onStarted 只接受匹配的 generation，否则立即 close 句柄。
 *
 * ## 线程模型
 * - 主协程 [scope] 在 [Dispatchers.Main]，负责 StateFlow 发布
 * - 扫描/热点轮换在 [Dispatchers.Default]，避免阻塞 UI
 * - LOHS 回调在 Main Looper（API 26-29）或主线程 Executor（API 30+）
 *
 * @param context 应用上下文
 */
class WifiDisruptManager(private val context: Context) {

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** 进程内共享的能力探测快照：UI 用于显示"支持 / 仅本机 SoftAP / 不支持" */
    private val capabilityProbe = CapabilityProbe.get(context)
    val capabilities: StateFlow<DeviceCapabilities> = capabilityProbe.capabilities

    /** 真实注入引擎 —— root 门控的 aireplay-ng/nexutil 驱动;无 root 设备不可启动 */
    val deauth = WifiDeauthEngine.get(context)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _hotspotResult = MutableStateFlow<HotspotResult?>(null)
    val hotspotResult: StateFlow<HotspotResult?> = _hotspotResult.asStateFlow()

    private val _isFlooding = MutableStateFlow(false)
    val isFlooding: StateFlow<Boolean> = _isFlooding.asStateFlow()

    private val _stats = MutableStateFlow(WifiDisruptStats(0, 0, 0, 0, 0))
    val stats: StateFlow<WifiDisruptStats> = _stats.asStateFlow()

    private val _hotspotPhase = MutableStateFlow(HotspotPhase.IDLE)
    val hotspotPhase: StateFlow<HotspotPhase> = _hotspotPhase.asStateFlow()

    /** 运行时是否真的能指定 SSID：API 30+ 且系统提供 SoftApConfiguration 接口 */
    @Volatile
    private var ssidConfigUnavailable = false

    val ssidConfigurable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !ssidConfigUnavailable

    private var floodJob: Job? = null
    private var scanJob: Job? = null

    // ─── Hotspot 状态（generation + 原子句柄，防止晚到回调复活热点） ────────────

    private val reservationRef =
        AtomicReference<WifiManager.LocalOnlyHotspotReservation?>(null)
    private val generation = AtomicLong(0L)
    private val startCount = AtomicLong(0)
    private val stopCount = AtomicLong(0)
    private val failedCount = AtomicLong(0)
    private val startTimeMs = AtomicLong(0L)
    private val lastErrorRef = AtomicReference<String?>(null)
    private val actualSsidRef = AtomicReference<String?>(null)

    /** 停止时等待 onStopped 的信号；每次 start 重建 */
    @Volatile
    private var stopSignal: CompletableDeferred<Unit>? = null

    // ─── 扫描 ───────────────────────────────────────────────────────────────────

    /**
     * 扫描附近 WiFi 网络。
     *
     * 错误分类严格区分：
     * - 定位总开关关闭 → 明确提示"请打开系统定位"（Android 10+ 即使用户已授予
     *   位置权限，关闭定位总开关后 startScan 也会失败或只返回缓存）
     * - SecurityException → 用 checkSelfPermission 精确列出缺失的权限名
     * - startScan 返回 false 且定位已开 → 系统扫描节流，建议稍后重试
     */
    fun scanNetworks() {
        if (_isFlooding.value) {
            _scanState.value = ScanState.Error(L10n.get(R.string.mgr_wifid_err_flood_scan))
            return
        }
        scanJob?.cancel()

        if (!isLocationServiceEnabled()) {
            _scanState.value = ScanState.Error(L10n.get(R.string.mgr_wifid_err_location_off_scan))
            return
        }

        _scanState.value = ScanState.Scanning
        capabilityProbe.refreshCheap()

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
                } catch (_: SecurityException) {
                    _scanState.value = ScanState.Error(
                        describeWifiSecurityFailure(L10n.get(R.string.mgr_wifid_action_wifi_scan)),
                    )
                    return@launch
                }

                if (success) {
                    delay(SCAN_WAIT_MS)
                    val fresh = readScanResults()
                    if (fresh.isNotEmpty()) {
                        _scanState.value = ScanState.Results(fresh)
                    } else if (cached.isEmpty()) {
                        _scanState.value = if (isLocationServiceEnabled()) {
                            ScanState.Error(L10n.get(R.string.mgr_wifid_err_no_networks))
                        } else {
                            ScanState.Error(L10n.get(R.string.mgr_wifid_err_location_off_retry))
                        }
                    }
                } else if (cached.isEmpty()) {
                    _scanState.value = if (isLocationServiceEnabled()) {
                        ScanState.Error(L10n.get(R.string.mgr_wifid_err_scan_throttled))
                    } else {
                        ScanState.Error(L10n.get(R.string.mgr_wifid_err_location_off_retry))
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: SecurityException) {
                _scanState.value = ScanState.Error(
                    describeWifiSecurityFailure(L10n.get(R.string.mgr_wifid_action_wifi_scan)),
                )
            } catch (e: Throwable) {
                _scanState.value = ScanState.Error(L10n.get(R.string.mgr_wifid_err_scan, e.message ?: e.javaClass.simpleName))
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

    // ─── 本机热点轮换 ───────────────────────────────────────────────────────────

    /**
     * 启动本机热点 SSID 轮换。
     *
     * @param targetSsid       API 30+ 生效的目标 SSID；API 26-29 会明确提示系统
     *                         只创建随机 SSID 热点，不假装"邪恶双子"
     * @param durationSeconds  持续秒数（1..60）
     * @param toggleIntervalMs 每个热点保持时长；内部钳制到 [MIN_HOLD_MS]..[MAX_HOLD_MS]，
     *                         旧的 40-80ms 节奏与 LOHS 单请求模型冲突，已删除
     */
    fun startFlood(
        targetSsid: String,
        durationSeconds: Int = DEFAULT_DURATION_SEC,
        toggleIntervalMs: Long = DEFAULT_HOLD_MS,
    ) {
        if (_isFlooding.value) {
            _hotspotResult.value = HotspotResult.Error(L10n.get(R.string.mgr_wifid_err_flood_running))
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            _hotspotResult.value = HotspotResult.Error(L10n.get(R.string.mgr_wifid_err_api_level))
            return
        }
        if (!wifiManager.isWifiEnabled) {
            _hotspotResult.value = HotspotResult.Error(L10n.get(R.string.mgr_wifid_err_wifi_off))
            return
        }
        if (targetSsid.isBlank()) {
            _hotspotResult.value = HotspotResult.Error(L10n.get(R.string.mgr_wifid_err_ssid_empty))
            return
        }
        if (!isLocationServiceEnabled()) {
            // LOHS 本身不强制定位，但 Wi-Fi 栈在定位关闭时常拒绝扫描/热点；先给出可操作提示
            _hotspotResult.value = HotspotResult.Error(L10n.get(R.string.mgr_wifid_err_location_off_hotspot))
            return
        }

        capabilityProbe.refreshCheap()

        _isFlooding.value = true
        _hotspotResult.value = null
        _hotspotPhase.value = HotspotPhase.IDLE
        startCount.set(0L)
        stopCount.set(0L)
        failedCount.set(0L)
        lastErrorRef.set(null)
        actualSsidRef.set(null)
        startTimeMs.set(System.currentTimeMillis())

        val holdMs = toggleIntervalMs.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)
        val durMs = durationSeconds.coerceIn(1, MAX_DURATION_SEC) * 1000L
        val totalCycles = (durMs / holdMs).coerceAtLeast(1L)

        floodJob = scope.launch(Dispatchers.Default) {
            runHotspotRotation(targetSsid, holdMs, totalCycles)
        }
    }

    fun stopFlood() {
        _isFlooding.value = false
        // 使所有在途请求的 generation 失效：晚到的 onStarted 会立即 close 句柄
        generation.incrementAndGet()
        floodJob?.cancel()
        floodJob = null
        if (stopReservation()) stopCount.incrementAndGet()
        _hotspotPhase.value = HotspotPhase.IDLE
        publishStats(
            attempted = _stats.value.cycles,
            total = _stats.value.totalCycles,
            succeeded = _stats.value.startCount,
            failed = _stats.value.failedCount,
            lastError = lastErrorRef.get(),
            actualSsid = actualSsidRef.get(),
        )
    }

    fun destroy() {
        stopFlood()
        stopReservation()
        scope.cancel()
    }

    private suspend fun CoroutineScope.runHotspotRotation(
        targetSsid: String,
        holdMs: Long,
        totalCycles: Long,
    ) {
        var attempted = 0L
        var succeeded = 0L
        var failed = 0L

        try {
            // API 26-29 无法指定 SSID：先如实告知，避免用户以为在伪造目标 AP
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                lastErrorRef.set(L10n.get(R.string.mgr_wifid_err_no_ssid_api))
                _hotspotResult.value = HotspotResult.Error(lastErrorRef.get()!!)
            }

            while (attempted < totalCycles && isActive && _isFlooding.value) {
                attempted++
                when (val outcome = startHotspotAndAwait(targetSsid)) {
                    is StartOutcome.Started -> {
                        succeeded++
                        startCount.set(succeeded)
                        actualSsidRef.set(outcome.actualSsid)
                        _hotspotResult.value =
                            HotspotResult.Created(outcome.actualSsid ?: L10n.get(R.string.mgr_wifid_random_ssid))
                        publishStats(
                            attempted = attempted,
                            total = totalCycles,
                            succeeded = succeeded,
                            failed = failed,
                            lastError = lastErrorRef.get(),
                            actualSsid = outcome.actualSsid,
                        )
                        _hotspotResult.value =
                            HotspotResult.Progress(attempted.toInt(), totalCycles.toInt())

                        delay(holdMs)
                        stopHotspotAndAwait()
                        stopCount.incrementAndGet()
                    }

                    is StartOutcome.Failed -> {
                        failed++
                        failedCount.set(failed)
                        lastErrorRef.set(outcome.message)
                        // onFailed 后保留错误文案，下一轮 start 才覆盖；不立即发 Progress
                        _hotspotResult.value = HotspotResult.Error(outcome.message)
                        publishStats(
                            attempted = attempted,
                            total = totalCycles,
                            succeeded = succeeded,
                            failed = failed,
                            lastError = outcome.message,
                            actualSsid = actualSsidRef.get(),
                        )
                        delay(FAILURE_BACKOFF_MS)
                    }
                }
            }

            // Completed 仅在真正有过成功 onStarted 时发出，绝不无条件宣布"完成"
            _hotspotResult.value = if (succeeded > 0) {
                HotspotResult.Completed
            } else {
                HotspotResult.Error(
                    lastErrorRef.get() ?: L10n.get(R.string.mgr_wifid_err_no_hotspot),
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            lastErrorRef.set(L10n.get(R.string.mgr_wifid_err_rotation, e.message ?: e.javaClass.simpleName))
            _hotspotResult.value = HotspotResult.Error(L10n.get(R.string.mgr_wifid_err_rotation, e.message ?: e.javaClass.simpleName))
        } finally {
            stopReservation()
            _hotspotPhase.value = HotspotPhase.IDLE
            _isFlooding.value = false
            publishStats(
                attempted = attempted,
                total = totalCycles,
                succeeded = succeeded,
                failed = failed,
                lastError = lastErrorRef.get(),
                actualSsid = actualSsidRef.get(),
            )
        }
    }

    // ─── Hotspot 状态机 ─────────────────────────────────────────────────────────

    private sealed class StartOutcome {
        data class Started(val actualSsid: String?) : StartOutcome()
        data class Failed(val message: String) : StartOutcome()
    }

    /**
     * 调起本机热点并等待 onStarted / onFailed。
     *
     * - API 30+：[SoftApConfiguration] 指定 [targetSsid] 与 2.4GHz 频段
     * - API 26-29：公共 API 不接受 SSID，只能创建系统随机 SSID 热点
     * - 超时/取消后让 generation 失效，晚到的 onStarted 会立即 close 句柄
     */
    private suspend fun startHotspotAndAwait(targetSsid: String): StartOutcome {
        val myGeneration = generation.incrementAndGet()
        val stoppedSignal = CompletableDeferred<Unit>()
        stopSignal = stoppedSignal
        _hotspotPhase.value = HotspotPhase.STARTING

        val result = withTimeoutOrNull(START_TIMEOUT_MS) {
            suspendCancellableCoroutine<StartOutcome> { cont ->
                val callback = object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(
                        reservation: WifiManager.LocalOnlyHotspotReservation?,
                    ) {
                        val stale = myGeneration != generation.get() ||
                            !_isFlooding.value || !cont.isActive
                        if (stale) {
                            // 会话已停止 / 已超时：立即释放，绝不把句柄丢在原地
                            closeQuietly(reservation)
                            _hotspotPhase.value = HotspotPhase.IDLE
                            if (cont.isActive) {
                                cont.resume(
                                    StartOutcome.Failed(L10n.get(R.string.mgr_wifid_err_stale_callback)),
                                )
                            }
                            return
                        }
                        reservationRef.set(reservation)
                        _hotspotPhase.value = HotspotPhase.ACTIVE
                        val ssid = actualSsidOf(reservation)
                        actualSsidRef.set(ssid)
                        cont.resume(StartOutcome.Started(ssid))
                    }

                    override fun onStopped() {
                        if (!stoppedSignal.isCompleted) stoppedSignal.complete(Unit)
                        if (_hotspotPhase.value == HotspotPhase.STOPPING) {
                            _hotspotPhase.value = HotspotPhase.IDLE
                        }
                    }

                    override fun onFailed(reason: Int) {
                        _hotspotPhase.value = HotspotPhase.IDLE
                        if (cont.isActive) {
                            cont.resume(StartOutcome.Failed(hotspotFailureMessage(reason)))
                        }
                    }
                }

                try {
                    val configured = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        startWithSoftApConfiguration(targetSsid, callback)
                    } else {
                        false
                    }
                    if (!configured) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // 如实记录：本次没能应用目标 SSID，系统会创建随机 SSID 热点
                            lastErrorRef.set(L10n.get(R.string.mgr_wifid_err_softap_unavailable))
                        }
                        @Suppress("DEPRECATION")
                        wifiManager.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
                    }
                } catch (_: SecurityException) {
                    _hotspotPhase.value = HotspotPhase.IDLE
                    if (cont.isActive) {
                        cont.resume(StartOutcome.Failed(describeWifiSecurityFailure(L10n.get(R.string.mgr_wifid_action_start_hotspot))))
                    }
                } catch (t: Throwable) {
                    _hotspotPhase.value = HotspotPhase.IDLE
                    if (cont.isActive) {
                        cont.resume(
                            StartOutcome.Failed(
                                L10n.get(
                                    R.string.mgr_wifid_err_start_failed,
                                    t.javaClass.simpleName,
                                    t.message ?: "",
                                ),
                            ),
                        )
                    }
                }
            }
        }

        if (result == null) {
            // 超时：让本次 generation 失效；若 onStarted 晚到会被 stale 分支 close
            generation.incrementAndGet()
            _hotspotPhase.value = HotspotPhase.IDLE
            return StartOutcome.Failed(
                L10n.get(R.string.mgr_wifid_err_start_timeout, START_TIMEOUT_MS),
            )
        }
        return result
    }

    /**
     * 通过反射调用 API 30+ 的 `SoftApConfiguration` + `startLocalOnlyHotspotWithConfiguration`。
     *
     * ## 为什么用反射
     * 本仓库编译所用的 android-34 SDK stub 只包含 `SoftApConfiguration` 外壳，
     * 缺少 `Builder`、`BAND_2GHZ` 以及 `WifiManager.startLocalOnlyHotspotWithConfiguration`
     * （android-36 stub 才补全）。直接引用会编译失败；反射在 Android 11+ 真机上
     * 仍能调用同一公共 API，不改变运行时行为。
     *
     * `setBand` 是 API 31+ 方法，失败不致命：交由系统选择频段。
     *
     * @return true 表示 start 请求已交给系统；false 表示当前 ROM/stub 不提供该接口，
     *         调用方必须退回随机 SSID 的旧接口，不能假装已指定目标 SSID。
     */
    private fun startWithSoftApConfiguration(
        targetSsid: String,
        callback: WifiManager.LocalOnlyHotspotCallback,
    ): Boolean {
        // 只有"类/方法确实不存在"才返回 false（调用方退回随机 SSID）；
        // 调用过程抛出的 SecurityException 等原样上抛，由调用方精确归因。
        val builderClass = try {
            Class.forName("android.net.wifi.SoftApConfiguration\$Builder")
        } catch (_: ClassNotFoundException) {
            ssidConfigUnavailable = true
            return false
        }
        val builder = builderClass.getDeclaredConstructor().newInstance()
        builderClass.getMethod("setSsid", String::class.java).invoke(builder, targetSsid)
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
            ssidConfigUnavailable = true
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
     * 关闭当前热点并等待 onStopped（最多 [STOP_TIMEOUT_MS]）。
     *
     * `getAndSet(null)` 保证重复调用幂等；停止后等待回调或退避再开始下一轮，
     * 避免"上一请求未完成就发下一次 startLocalOnlyHotspot"。
     */
    private suspend fun stopHotspotAndAwait() {
        val reservation = reservationRef.getAndSet(null)
        if (reservation == null) {
            _hotspotPhase.value = HotspotPhase.IDLE
            return
        }
        _hotspotPhase.value = HotspotPhase.STOPPING
        val signal = stopSignal
        try {
            reservation.close()
        } catch (_: Throwable) {
            // 关闭失败不阻塞主流程，句柄已被 getAndSet 移除
        }
        withTimeoutOrNull(STOP_TIMEOUT_MS) { signal?.await() }
        _hotspotPhase.value = HotspotPhase.IDLE
        stopSignal = null
    }

    /** 幂等释放热点句柄；返回是否真的关闭了一个句柄 */
    private fun stopReservation(): Boolean {
        val reservation = reservationRef.getAndSet(null) ?: return false
        closeQuietly(reservation)
        return true
    }

    private fun closeQuietly(reservation: WifiManager.LocalOnlyHotspotReservation?) {
        if (reservation == null) return
        try {
            reservation.close()
        } catch (_: Throwable) {
            // 关闭失败不阻塞主流程
        }
    }

    /** 只读取系统回调里的真实 SSID，绝不把用户输入的 targetSsid 当成"已创建" */
    private fun actualSsidOf(
        reservation: WifiManager.LocalOnlyHotspotReservation?,
    ): String? {
        if (reservation == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                reservation.softApConfiguration?.ssid
            } else {
                @Suppress("DEPRECATION")
                reservation.wifiConfiguration?.SSID
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun hotspotFailureMessage(reason: Int): String = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL ->
            L10n.get(R.string.mgr_wifid_hotspot_no_channel, reason)
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE ->
            L10n.get(R.string.mgr_wifid_hotspot_incompatible, reason)
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED ->
            L10n.get(R.string.mgr_wifid_hotspot_tethering, reason)
        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC ->
            L10n.get(R.string.mgr_wifid_hotspot_generic, reason)
        else -> L10n.get(R.string.mgr_wifid_hotspot_unknown, reason)
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

    /**
     * SecurityException 精确归因：逐项 checkSelfPermission，列出真实缺失的权限名。
     * 不再把所有 SecurityException 都归为 CHANGE_WIFI_STATE。
     */
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
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CHANGE_WIFI_STATE,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add("CHANGE_WIFI_STATE")
            }
        }
        return if (missing.isEmpty()) {
            L10n.get(R.string.mgr_wifid_security_no_missing, action)
        } else {
            L10n.get(R.string.mgr_wifid_security_missing, action, missing.joinToString(" / "))
        }
    }

    // ─── 统计 ───────────────────────────────────────────────────────────────────

    private fun publishStats(
        attempted: Long,
        total: Long,
        succeeded: Long,
        failed: Long,
        lastError: String?,
        actualSsid: String?,
    ) {
        val uptime = maxOf(0L, System.currentTimeMillis() - startTimeMs.get())
        _stats.value = WifiDisruptStats(
            cycles = attempted,
            totalCycles = total,
            uptimeMs = uptime,
            startCount = succeeded,
            stopCount = stopCount.get(),
            failedCount = failed,
            actualSsid = actualSsid,
            lastError = lastError,
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

    companion object {
        const val SCAN_WAIT_MS = 1200L
        const val DEFAULT_DURATION_SEC = 10
        const val MAX_DURATION_SEC = 60

        /** 单个热点最短保持时长：低于 LOHS 完成一次 onStarted/onStopped 的时间没有意义 */
        const val MIN_HOLD_MS = 5_000L

        /** 单个热点最长保持时长 */
        const val MAX_HOLD_MS = 15_000L

        /** 默认单个热点保持时长 */
        const val DEFAULT_HOLD_MS = 5_000L

        private const val START_TIMEOUT_MS = 5_000L
        private const val STOP_TIMEOUT_MS = 500L
        private const val FAILURE_BACKOFF_MS = 500L

        /** AOSP SoftApConfiguration.BAND_2GHZ 的数值；编译 stub 未提供该常量 */
        private const val SOFT_AP_BAND_2GHZ = 1

        /** UI 可据此跳转系统定位开关（定位关闭时扫描必然失败） */
        fun locationSettingsIntent(): Intent =
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
