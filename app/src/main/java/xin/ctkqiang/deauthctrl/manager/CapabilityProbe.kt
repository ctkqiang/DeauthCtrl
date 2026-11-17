package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import xin.ctkqiang.deauthctrl.terminal.TerminalSpecs
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** 单项能力探测状态：绿 / 黄 / 红 三档由 UI 映射 */
enum class CapabilityStatus {
    /** 尚未探测，不能据此宣称支持或拒绝 */
    Unknown,

    /** 本机已验证可用 */
    Supported,

    /** 平台/硬件不支持，detail 给出具体缺项 */
    Unsupported,

    /** 权限缺失，与"硬件不支持"区分开 */
    PermissionDenied,
}

/**
 * root 授权状态。
 *
 * 注意：`su` 文件存在（[TerminalSpecs.findSu]）不等于已授权。Magisk 在文件存在但
 * 用户拒绝授权时 still 会返回一个可执行文件，因此必须真正执行 `su -c id` 并解析
 * `uid=0` 才能判定 [Granted]。
 */
enum class RootStatus { Unknown, NoRoot, Denied, Granted }

/** 一条能力探测结果：状态 + 给人看的原因（缺什么就写什么，不写笼统的"失败"） */
data class CapabilityCheck(
    val status: CapabilityStatus,
    val detail: String,
)

/**
 * 设备能力矩阵快照。
 *
 * UI 应把 [CapabilityStatus.Supported] 显示为绿色、仅本机 SoftAP/BLE 广播的
 * 降级能力显示为黄色、[CapabilityStatus.Unsupported] 显示为红色并展示 [CapabilityCheck.detail]。
 * 红/黄时不应把"执行 deauth/注入"按钮呈现为可用。
 */
data class DeviceCapabilities(
    val apiLevel: Int = Build.VERSION.SDK_INT,
    val model: String = Build.MODEL ?: "unknown",
    val hardware: String = Build.HARDWARE ?: "unknown",
    val root: RootStatus = RootStatus.Unknown,
    val rootDetail: String = "尚未执行 root 探测",
    /** 本机 LocalOnlyHotspot 能否创建（真实 SoftAP） */
    val localOnlyHotspot: CapabilityCheck =
        CapabilityCheck(CapabilityStatus.Unknown, "尚未探测"),
    /** 仅本机 SoftAP：能创建热点，但不等于伪造第三方 AP / 帧注入 */
    val pureSoftAp: CapabilityCheck =
        CapabilityCheck(CapabilityStatus.Unknown, "尚未探测"),
    /** BLE advertising 是否可用 */
    val bleAdvertising: CapabilityCheck =
        CapabilityCheck(CapabilityStatus.Unknown, "尚未探测"),
    /** 是否支持多组 advertising set（受控制器限制，最多 4-5 组） */
    val bleMultipleAdvertisement: Boolean = false,
    /** legacy advertising 最大数据长度（通常 31 字节，含 2 字节 company ID / 头部） */
    val bleMaxAdvertisingDataLength: Int = 0,
    /** 802.11 monitor mode（需要 root + 特定芯片/固件） */
    val wifiMonitorMode: CapabilityCheck =
        CapabilityCheck(CapabilityStatus.Unsupported, "未检测：需要 root + 特定芯片/固件"),
    /** 802.11 帧注入 / deauth（需要 root + monitor mode + 用户态工具自检通过） */
    val wifiFrameInjection: CapabilityCheck =
        CapabilityCheck(CapabilityStatus.Unsupported, "未检测：需要 root + 特定芯片/固件"),
    /** 系统定位总开关。Android 要求开启定位才能扫描 Wi-Fi */
    val locationServiceEnabled: Boolean = false,
    /** API 30+ 才允许给 LocalOnlyHotspot 指定 SSID */
    val ssidConfigurable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
    val checkedAtMs: Long = 0L,
) {
    /**
     * 只有 root、monitor mode、帧注入自检全部通过，才可解锁"外部工具模式"
     * （前台服务通过 `su -c` 启动用户自行安装的 aircrack-ng / mdk4 等）。
     * 未通过时 UI 不得显示"可注入 / 可 deauth"。
     */
    val externalToolModeUnlocked: Boolean
        get() = root == RootStatus.Granted &&
            wifiMonitorMode.status == CapabilityStatus.Supported &&
            wifiFrameInjection.status == CapabilityStatus.Supported
}

/**
 * 设备能力探测器
 *
 * 目的：把"设备/平台不支持"与"模块有 bug"区分开，避免 UI 在普通 stock Android
 * 上给用户"可执行 deauth / 帧注入"的假象。
 *
 * ## 探测项
 * 1. root：真正执行 `su -c id` 并解析 uid=0，区分 [RootStatus.NoRoot] /
 *    [RootStatus.Denied] / [RootStatus.Granted]
 * 2. BLE：advertiser + isMultipleAdvertisementSupported + LE 最大广播长度
 * 3. Wi-Fi monitor / 帧注入：仅 root 下检查 nexutil / iw dev / con_mode /
 *    aireplay-ng --test，每一项独立超时
 * 4. 定位总开关
 * 5. LOHS：真正尝试 startLocalOnlyHotspot 并在 onStarted 后立即 close
 *
 * ## 线程与超时
 * - 所有探测在 [Dispatchers.IO] 执行，每项命令 1.5s 超时，超时即杀进程
 * - [refreshCheap] 只做无副作用的廉价检查（BLE/定位），可在任意页面调用
 * - [refreshFull] 会触发 `su`（可能弹 Magisk 授权）与 `nexutil -m2`
 *   （可能把芯片切到 monitor 模式），只能由用户在设置页显式触发
 *
 * ## 单例
 * 同一进程共享一份探测结果，避免多个页面各弹一次 root 授权。
 */
class CapabilityProbe private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _capabilities = MutableStateFlow(DeviceCapabilities())
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

    @Volatile private var probing = false
    @Volatile private var lastCheapProbeMs = 0L

    /** 只做 BLE / 定位等无副作用检查；不会触发 root 授权弹窗 */
    fun refreshCheap() {
        if (System.currentTimeMillis() - lastCheapProbeMs < CHEAP_RECHECK_MS) return
        launchProbe(full = false)
    }

    /** 完整探测：root + monitor/注入 + LOHS。可能弹 root 授权，需要用户显式触发 */
    fun refreshFull() {
        launchProbe(full = true)
    }

    /** 供设置页挂起等待一次完整探测结果 */
    suspend fun probeNow(full: Boolean = false): DeviceCapabilities {
        val caps = probe(full)
        _capabilities.value = caps
        if (full) lastCheapProbeMs = System.currentTimeMillis()
        return caps
    }

    private fun launchProbe(full: Boolean) {
        if (probing) return
        probing = true
        scope.launch {
            try {
                val caps = probe(full)
                _capabilities.value = caps
                lastCheapProbeMs = System.currentTimeMillis()
            } catch (_: Throwable) {
                // 探测失败不阻塞业务：保留上一份快照
            } finally {
                probing = false
            }
        }
    }

    /**
     * 执行一次探测。
     *
     * @param full false 时只做廉价检查，root/monitor/LOHS 保持 [CapabilityStatus.Unknown]，
     *             调用方不能把 Unknown 当成"支持"。
     */
    suspend fun probe(full: Boolean = false): DeviceCapabilities = withContext(Dispatchers.IO) {
        // 廉价刷新必须保留上一轮完整探测的 root/monitor/注入结果，
        // 否则 refreshCheap 会把已确认的能力打回 Unknown，UI 随之闪烁。
        var caps = _capabilities.value.copy(
            apiLevel = Build.VERSION.SDK_INT,
            model = Build.MODEL ?: "unknown",
            hardware = Build.HARDWARE ?: "unknown",
            checkedAtMs = System.currentTimeMillis(),
            locationServiceEnabled = probeLocationEnabled(),
        )
        caps = probeBle(caps)
        if (!full) return@withContext caps

        val suPath = TerminalSpecs.findSu()
        val (rootStatus, rootDetail) = probeRoot(suPath)
        val (monitor, injection) = probeWifiInjection(suPath, rootStatus)
        val hotspot = probeLocalOnlyHotspot()
        caps.copy(
            root = rootStatus,
            rootDetail = rootDetail,
            localOnlyHotspot = hotspot,
            pureSoftAp = hotspot.copy(
                detail = "仅本机 SoftAP：不会改变目标 AP 的 BSSID/信道/加密，也不会断开任何客户端" +
                    if (hotspot.status == CapabilityStatus.Supported) {
                        "；启动热点可能中断本机 Wi-Fi"
                    } else ""
            ),
            wifiMonitorMode = monitor,
            wifiFrameInjection = injection,
        )
    }

    // ─── root ─────────────────────────────────────────────────────────────────

    private suspend fun probeRoot(suPath: String?): Pair<RootStatus, String> {
        if (suPath == null) {
            return RootStatus.NoRoot to
                "未找到 su：stock Android 普通应用没有 CAP_NET_RAW，AF_PACKET 被 SELinux 拒绝，不提供 monitor mode / 帧注入"
        }
        val result = runCommand(listOf(suPath, "-c", "id"), ITEM_TIMEOUT_MS)
        if (result.timedOut) {
            return RootStatus.Denied to
                "su 存在但 ${ITEM_TIMEOUT_MS}ms 内无响应（可能正在等待授权或被策略拒绝）"
        }
        val granted = result.exitCode == 0 &&
            Regex("""\buid=0\b""").containsMatchIn(result.output)
        return if (granted) {
            RootStatus.Granted to "su 已授权：${result.output.lineSequence().firstOrNull()?.trim().orEmpty()}"
        } else {
            RootStatus.Denied to "su 存在但未授权（exit=${result.exitCode}）${result.output.take(80).trim()}"
        }
    }

    // ─── Wi-Fi monitor / 帧注入 ──────────────────────────────────────────────

    private suspend fun probeWifiInjection(
        suPath: String?,
        root: RootStatus,
    ): Pair<CapabilityCheck, CapabilityCheck> {
        if (root != RootStatus.Granted || suPath == null) {
            val reason = when (root) {
                RootStatus.NoRoot ->
                    "当前系统没有 root：stock Android 不支持 802.11 monitor mode / 帧注入"
                RootStatus.Denied ->
                    "su 未授权：无法检查 monitor mode / 帧注入"
                else ->
                    "未完成 root 探测：monitor mode / 帧注入状态未知"
            }
            val check = CapabilityCheck(CapabilityStatus.Unsupported, reason)
            return check to check
        }

        val details = mutableListOf<String>()

        // 1) nexutil 是否存在
        val which = runRoot(suPath, "command -v nexutil")
        val hasNexutil = which.exitCode == 0 && which.output.isNotBlank()
        details += if (hasNexutil) "nexutil 已安装" else "未找到 nexutil"

        // 2) nexutil -m2 返回码（注意：会把 Nexmon 芯片切到 monitor 模式）
        val nexMon = if (hasNexutil) {
            runRoot(suPath, "nexutil -m2")
        } else {
            CommandResult(exitCode = -1, output = "", timedOut = false)
        }
        val nexmonSupported = hasNexutil && nexMon.exitCode == 0
        details += if (hasNexutil) "nexutil -m2 exit=${nexMon.exitCode}" else "跳过 nexutil -m2"

        // 3) iw dev 是否已有 monitor 接口
        val iw = runRoot(suPath, "iw dev 2>/dev/null")
        val iwMonitor = iw.output.contains("type monitor")
        details += if (iwMonitor) "iw dev 发现 type monitor" else "iw dev 未发现 monitor 接口"

        // 4) QCACLD 的 con_mode
        val conMode = runRoot(suPath, "cat /sys/module/wlan/parameters/con_mode 2>/dev/null")
        val conModeMonitor = conMode.output.trim() == "4"
        details += if (conModeMonitor) {
            "con_mode=4（QCACLD monitor）"
        } else {
            "con_mode=${conMode.output.trim().ifBlank { "不可读" }}"
        }

        // 5) aireplay-ng 注入自检
        val aireplay = runRoot(suPath, "aireplay-ng --test wlan0 2>&1")
        val injectionSupported = aireplay.output.contains("Injection is working", ignoreCase = true)
        details += if (injectionSupported) {
            "aireplay-ng --test：Injection is working"
        } else {
            "aireplay-ng --test 未确认注入（工具未安装或芯片/固件不支持）"
        }

        val summary = details.joinToString("；")
        val monitorSupported = nexmonSupported || iwMonitor || conModeMonitor
        return CapabilityCheck(
            if (monitorSupported) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            summary,
        ) to CapabilityCheck(
            if (injectionSupported) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            summary,
        )
    }

    // ─── LOHS ────────────────────────────────────────────────────────────────

    /**
     * 尝试创建一次 LocalOnlyHotspot 并在 onStarted 后立即 close。
     *
     * 注意：系统同一时刻只允许一个未完成的 LOHS 请求，因此若热点模块正在运行，
     * 本探测会失败（detail 会写明）。设置页应在没有运行热点时调用。
     */
    suspend fun probeLocalOnlyHotspot(): CapabilityCheck = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return@withContext CapabilityCheck(
                CapabilityStatus.Unsupported,
                "LocalOnlyHotspot 需要 Android 8.0 (API 26)+",
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext CapabilityCheck(
                CapabilityStatus.PermissionDenied,
                "缺少 NEARBY_WIFI_DEVICES（Android 13+ 创建热点必需）",
            )
        }
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return@withContext CapabilityCheck(CapabilityStatus.Unsupported, "无 WifiManager")
        val wifiEnabled = runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)
        if (!wifiEnabled) {
            return@withContext CapabilityCheck(CapabilityStatus.Unsupported, "Wi-Fi 未开启")
        }

        val result = withTimeoutOrNull(HOTSPOT_PROBE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val callback = object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                        runCatching { reservation?.close() }
                        if (cont.isActive) {
                            cont.resume(
                                CapabilityCheck(
                                    CapabilityStatus.Supported,
                                    "本机热点可创建；同一时刻只允许一个未完成请求",
                                )
                            )
                        }
                    }

                    override fun onFailed(reason: Int) {
                        if (cont.isActive) {
                            cont.resume(
                                CapabilityCheck(
                                    CapabilityStatus.Unsupported,
                                    "启动失败 reason=$reason（渠道占用/模式冲突/策略禁止）",
                                )
                            )
                        }
                    }
                }
                try {
                    wifiManager.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
                } catch (se: SecurityException) {
                    if (cont.isActive) {
                        cont.resume(
                            CapabilityCheck(
                                CapabilityStatus.PermissionDenied,
                                "SecurityException：缺少权限或厂商 ROM 禁止 LocalOnlyHotspot",
                            )
                        )
                    }
                } catch (t: Throwable) {
                    if (cont.isActive) {
                        cont.resume(
                            CapabilityCheck(
                                CapabilityStatus.Unsupported,
                                "启动异常 ${t.javaClass.simpleName}: ${t.message}",
                            )
                        )
                    }
                }
            }
        }
        result ?: CapabilityCheck(
            CapabilityStatus.Unknown,
            "热点探测超时（${HOTSPOT_PROBE_TIMEOUT_MS}ms），可能正被其他模块占用",
        )
    }

    // ─── BLE / 定位 ──────────────────────────────────────────────────────────

    private fun probeBle(base: DeviceCapabilities): DeviceCapabilities {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
            ?: return base.copy(
                bleAdvertising = CapabilityCheck(CapabilityStatus.Unsupported, "本机没有蓝牙适配器"),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return base.copy(
                    bleAdvertising = CapabilityCheck(
                        CapabilityStatus.PermissionDenied,
                        "缺少 BLUETOOTH_CONNECT，无法读取蓝牙状态",
                    ),
                )
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return base.copy(
                    bleAdvertising = CapabilityCheck(
                        CapabilityStatus.PermissionDenied,
                        "缺少 BLUETOOTH_ADVERTISE，无法启动 BLE 广播",
                    ),
                )
            }
        }
        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (!enabled) {
            return base.copy(
                bleAdvertising = CapabilityCheck(CapabilityStatus.Unsupported, "蓝牙未开启"),
            )
        }
        val advertiser = runCatching { adapter.bluetoothLeAdvertiser }.getOrNull()
            ?: return base.copy(
                bleAdvertising = CapabilityCheck(
                    CapabilityStatus.Unsupported,
                    "控制器不提供 BLE 广播（BluetoothLeAdvertiser=null）",
                ),
            )
        @Suppress("UNUSED_VARIABLE")
        val advertiserReady = advertiser != null
        val multiple = runCatching { adapter.isMultipleAdvertisementSupported }.getOrDefault(false)
        val maxLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { adapter.leMaximumAdvertisingDataLength }.getOrDefault(0)
        } else {
            0
        }
        return base.copy(
            bleAdvertising = CapabilityCheck(
                CapabilityStatus.Supported,
                "可创建 BLE 广播会话；空口间隔最低约 100ms，不能伪造广播 MAC",
            ),
            bleMultipleAdvertisement = multiple,
            bleMaxAdvertisingDataLength = maxLength,
        )
    }

    private fun probeLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm.isLocationEnabled
            } else {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        }.getOrDefault(false)
    }

    // ─── 命令执行 ────────────────────────────────────────────────────────────

    private data class CommandResult(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean,
    )

    private suspend fun runRoot(suPath: String, command: String): CommandResult =
        runCommand(listOf(suPath, "-c", command), ITEM_TIMEOUT_MS)

    private suspend fun runCommand(
        command: List<String>,
        timeoutMs: Long,
    ): CommandResult = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                CommandResult(exitCode = -1, output = "", timedOut = true)
            } else {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                CommandResult(exitCode = process.exitValue(), output = output, timedOut = false)
            }
        }.getOrElse {
            CommandResult(
                exitCode = -1,
                output = it.message ?: it.javaClass.simpleName,
                timedOut = false,
            )
        }
    }

    fun destroy() {
        scope.cancel()
    }

    companion object {
        /** 单项命令探测超时 */
        private const val ITEM_TIMEOUT_MS = 1_500L

        /** LOHS 回调等待上限（系统调起热点通常比 shell 命令慢） */
        private const val HOTSPOT_PROBE_TIMEOUT_MS = 3_000L

        /** 廉价检查的最小间隔，避免每次进入页面都读蓝牙/定位 */
        private const val CHEAP_RECHECK_MS = 15_000L

        @Volatile
        private var instance: CapabilityProbe? = null

        /** 进程内共享单例：多个模块共用一份能力快照与 root 授权结果 */
        fun get(context: Context): CapabilityProbe =
            instance ?: synchronized(this) {
                instance ?: CapabilityProbe(context.applicationContext).also { instance = it }
            }
    }
}
