package xin.ctkqiang.deauthctrl.ui.screens

import android.app.ActivityManager
import android.app.Application
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xin.ctkqiang.deauthctrl.mirroring.model.MirroringDevice
import xin.ctkqiang.deauthctrl.mirroring.ui.DashboardScreen
import xin.ctkqiang.deauthctrl.mirroring.ui.MirroringState
import xin.ctkqiang.deauthctrl.mirroring.ui.MirroringViewModel
import xin.ctkqiang.deauthctrl.mirroring.ui.RemoteViewerScreen
import xin.ctkqiang.deauthctrl.ui.components.*
import xin.ctkqiang.deauthctrl.viewmodel.*

// ─── 路由名常量 ──────────────────────────────────────────────────────────────

private const val ROUTE_HOME = "home"
private const val ROUTE_MIRRORING = "mirroring"
private const val ROUTE_REMOTE_VIEWER = "remote_viewer"
private const val ROUTE_ABOUT = "about"

/** 免责声明偏好文件名与"已接受"键 —— 首次启动展示法律警告，接受后不再打扰 */
private const val PREFS_NAME = "deauthctrl_prefs"
private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"

/**
 * 应用主路由入口 Composable
 *
 * 整个应用的根组件，负责：
 * 1. 首次启动法律免责声明（接受后持久化）
 * 2. 启动序列播放（BootSequence → 主界面）
 * 3. 页面路由导航（AnimatedContent 实现 21 个页面的过渡动画）
 * 4. 全局 ScanlineOverlay 叠加
 *
 * ## 路由系统
 * `navigate(target)` 进入目标页并把当前页压栈；`goBack()` 弹栈回上一屏。
 * 系统返回键通过 [BackHandler] 接管：详情页逐级回退，只有主页才交还系统。
 */
@Composable
fun MainScreen(
    bleVm: BleSpamViewModel, wifiVm: WifiDisruptViewModel,
    btVm: BluetoothJammerViewModel, wjVm: WifiJammerViewModel,
    wsVm: WebServerViewModel, arpVm: ArpScanViewModel,
    httpVm: HttpClientViewModel, pingVm: PingViewModel,
    blescanVm: BleScannerViewModel, portscanVm: PortScannerViewModel,
    walkieVm: WalkieTalkieViewModel, radarVm: RadarViewModel,
    ftVm: FileTransferViewModel, vaultVm: SecureMediaViewModel,
    revVm: RevShellViewModel, payloadVm: PayloadViewModel,
    bruteVm: DirBruteViewModel, cveVm: CVESearchViewModel,
    mirrorVm: MirroringViewModel,
) {
    // 首次启动免责声明：接受后写入偏好，之后启动不再弹出。
    // 静态预览拿不到 SharedPreferences 时视为已接受，让预览直达主界面。
    val context = LocalContext.current
    val prefs = remember { runCatching { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }.getOrNull() }
    var disclaimerAccepted by remember { mutableStateOf(prefs?.getBoolean(KEY_DISCLAIMER_ACCEPTED, false) ?: true) }

    var boot by remember { mutableStateOf(true) }
    var selectedMirrorDevice by remember { mutableStateOf<MirroringDevice?>(null) }

    var screen by remember { mutableStateOf(ROUTE_HOME) }
    val backStack = remember { mutableStateListOf<String>() }

    /** 进入目标路由，先把当前路由压栈；重复进入同一路由不压栈 */
    val navigate: (String) -> Unit = { target ->
        if (target != screen) {
            backStack.add(screen)
            screen = target
        }
    }

    /** 返回上一屏；栈空则回主页 */
    val goBack: () -> Unit = { screen = backStack.removeLastOrNull() ?: ROUTE_HOME }

    BackHandler(enabled = screen != ROUTE_HOME) { goBack() }

    Box(Modifier.fillMaxSize().background(Dark)) {
        if (!disclaimerAccepted) {
            DisclaimerDialog(onAccept = {
                prefs?.edit()?.putBoolean(KEY_DISCLAIMER_ACCEPTED, true)?.apply()
                disclaimerAccepted = true
            })
        }
        if (boot) {
            BootSequence(onComplete = { boot = false })
        } else {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it / 5 })
                        .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { -it / 5 })
                },
            ) { current ->
                when (current) {
                    ROUTE_HOME -> HomeScreen(bleVm, wifiVm, btVm, wjVm, wsVm, arpVm, httpVm, pingVm, blescanVm, portscanVm, walkieVm, radarVm, ftVm, vaultVm, revVm, payloadVm, bruteVm, cveVm, mirrorVm = mirrorVm, nav = navigate)
                    ROUTE_MIRRORING -> DashboardScreen(vm = mirrorVm, onConnectDevice = { d -> selectedMirrorDevice = d; navigate(ROUTE_REMOTE_VIEWER) }, back = goBack)
                    ROUTE_REMOTE_VIEWER -> RemoteViewerScreen(vm = mirrorVm, device = selectedMirrorDevice, back = goBack)
                    "ble" -> BleDetailScreen(bleVm, back = goBack)
                    "btjam" -> BtJamDetailScreen(btVm, back = goBack)
                    "wifi" -> WifiDetailScreen(wifiVm, back = goBack)
                    "wifijam" -> WifiJamDetailScreen(wjVm, back = goBack)
                    "webserver" -> WebServerDetailScreen(wsVm, back = goBack)
                    "arp" -> ArpDetailScreen(arpVm, back = goBack)
                    "http" -> HttpClientScreen(httpVm, back = goBack)
                    "ping" -> PingScreen(pingVm, back = goBack)
                    "blescan" -> BleScannerScreen(blescanVm, back = goBack)
                    "portscan" -> PortScannerScreen(portscanVm, back = goBack)
                    "walkie" -> WalkieTalkieScreen(walkieVm, back = goBack)
                    "radar" -> RadarScreen(radarVm, back = goBack)
                    "filetransfer" -> FileTransferScreen(ftVm, back = goBack)
                    "vault" -> SecureVaultScreen(vaultVm, back = goBack)
                    "revshell" -> RevShellScreen(revVm, back = goBack)
                    "payload" -> PayloadScreen(payloadVm, back = goBack)
                    "brute" -> DirBruteScreen(bruteVm, back = goBack)
                    "cve" -> CVEScreen(cveVm, back = goBack)
                    ROUTE_ABOUT -> AboutDetailScreen(back = goBack)
                }
            }
        }
        if (!boot) {
            TacticalGrid()
            ScanlineOverlay(lineSpacing = 4.dp, alpha = 0.02f)
        }
    }
}

/**
 * 全息战术网格 —— 全屏微弱方格参考线
 *
 * 全息战术终端的底纹。一次绘制，随内容重绘但成本极低。
 */
@Composable
private fun TacticalGrid(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val step = 30.dp.toPx()
        val line = Color(0xFF0E1B2E)
        var x = 0f
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), 0.6f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 0.6f)
            y += step
        }
        // 十字准星中心标记（极淡）
        val cx = size.width / 2f; val cy = size.height / 2f
        drawCircle(line.copy(alpha = 0.8f), 1.5f, Offset(cx, cy))
        drawLine(line.copy(alpha = 0.6f), Offset(cx - 6.dp.toPx(), cy), Offset(cx + 6.dp.toPx(), cy), 0.8f)
        drawLine(line.copy(alpha = 0.6f), Offset(cx, cy - 6.dp.toPx()), Offset(cx, cy + 6.dp.toPx()), 0.8f)
    }
}

// ─── 实时系统遥测 ────────────────────────────────────────────────────────────
//
// 这里显示的每一项都取自系统 API，没有任何估算或模拟：
//   下行 / 上行  设备级收发速率 —— TrafficStats 累计值相邻采样差 ÷ 真实经过时间
//   内存           ActivityManager.MemoryInfo 的可用量与总物理内存
//   网卡           ConnectivityManager 当前活跃网络的实际接口名
//
// 读不到某一项时显示 "--"，而不是编造一个数字。

private const val TELEMETRY_INTERVAL_MS = 1_500L
private const val TRAFFIC_UNSUPPORTED = -1L
private const val TELEMETRY_PLACEHOLDER = "--"
private const val KIBIBYTE = 1024L
private const val MEBIBYTE = KIBIBYTE * 1024L
private const val GIBIBYTE = MEBIBYTE * 1024L

/** 一帧系统遥测快照 */
@Immutable
private data class SysTelemetry(
    val rxBytesPerSec: Long = 0L,
    val txBytesPerSec: Long = 0L,
    val memAvailBytes: Long = 0L,
    val memTotalBytes: Long = 0L,
    val iface: String = "",
    val trafficSupported: Boolean = true,
) {
    companion object {
        val EMPTY = SysTelemetry(trafficSupported = false)

        fun formatRate(bytesPerSec: Long): String = when {
            bytesPerSec >= MEBIBYTE -> String.format(java.util.Locale.US, "%.1fMB/s", bytesPerSec / MEBIBYTE.toDouble())
            bytesPerSec >= KIBIBYTE -> String.format(java.util.Locale.US, "%.1fKB/s", bytesPerSec / KIBIBYTE.toDouble())
            else -> "${bytesPerSec}B/s"
        }

        fun formatMemory(availBytes: Long, totalBytes: Long): String =
            if (totalBytes <= 0L) TELEMETRY_PLACEHOLDER
            else String.format(java.util.Locale.US, "%.1f/%.1fGB", availBytes / GIBIBYTE.toDouble(), totalBytes / GIBIBYTE.toDouble())
    }
}

/** 取当前活跃网络的实际接口名（开启热点时通常是 ap0 / swlan0，硬编码 wlan0 在现场就是错的） */
private fun activeInterfaceName(connectivity: ConnectivityManager?): String {
    val manager = connectivity ?: return ""
    val network = manager.activeNetwork ?: return ""
    return runCatching { manager.getLinkProperties(network)?.interfaceName }.getOrNull().orEmpty()
}

/** 以固定间隔采样真实系统遥测；随调用方 Composable 生命周期自动启停 */
@Composable
private fun rememberSystemTelemetry(intervalMs: Long = TELEMETRY_INTERVAL_MS): SysTelemetry {
    val context = LocalContext.current
    val telemetry by produceState(initialValue = SysTelemetry.EMPTY, context, intervalMs) {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)

        var previousRx = TrafficStats.getTotalRxBytes()
        var previousTx = TrafficStats.getTotalTxBytes()
        var previousAt = SystemClock.elapsedRealtime()

        while (true) {
            delay(intervalMs)
            // 静态预览（Layoutlib）读不到系统遥测时跳过本帧，保留上一帧读数
            runCatching {
                val rx = TrafficStats.getTotalRxBytes()
                val tx = TrafficStats.getTotalTxBytes()
                val nowAt = SystemClock.elapsedRealtime()
                val elapsedMs = (nowAt - previousAt).coerceAtLeast(1L)

                val supported = rx != TRAFFIC_UNSUPPORTED && tx != TRAFFIC_UNSUPPORTED && previousRx != TRAFFIC_UNSUPPORTED
                val rxRate = if (supported) ((rx - previousRx).coerceAtLeast(0L) * 1000L / elapsedMs) else 0L
                val txRate = if (supported) ((tx - previousTx).coerceAtLeast(0L) * 1000L / elapsedMs) else 0L

                if (supported) {
                    previousRx = rx
                    previousTx = tx
                }
                previousAt = nowAt

                val memory = ActivityManager.MemoryInfo()
                activityManager?.getMemoryInfo(memory)

                value = SysTelemetry(
                    rxBytesPerSec = rxRate,
                    txBytesPerSec = txRate,
                    memAvailBytes = memory.availMem,
                    memTotalBytes = memory.totalMem,
                    iface = activeInterfaceName(connectivity),
                    trafficSupported = supported,
                )
            }
        }
    }
    return telemetry
}

/** 实时状态栏 —— 四格真实系统指标，数值为电光青 */
@Composable
fun StatusBar() {
    val telemetry = rememberSystemTelemetry()

    Surface(shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp), color = SurfaceBg, border = BorderStroke(1.dp, BorderDim)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TelemetryCell("下行", if (telemetry.trafficSupported) SysTelemetry.formatRate(telemetry.rxBytesPerSec) else TELEMETRY_PLACEHOLDER)
            TelemetryCell("上行", if (telemetry.trafficSupported) SysTelemetry.formatRate(telemetry.txBytesPerSec) else TELEMETRY_PLACEHOLDER)
            TelemetryCell("内存", SysTelemetry.formatMemory(telemetry.memAvailBytes, telemetry.memTotalBytes))
            TelemetryCell("网卡", telemetry.iface.ifEmpty { TELEMETRY_PLACEHOLDER })
        }
    }
}

/** 状态栏单项 —— 电光青数值在上、中文标签在下 */
@Composable
private fun TelemetryCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacGreen)
        Spacer(Modifier.height(1.dp))
        Text(label, fontFamily = Sans, fontSize = 9.sp, color = Gray)
    }
}

// ─── 主页模块表 ──────────────────────────────────────────────────────────────

/** 主页卡片的一项模块描述：名称 / 中文描述 / 路由 / 图标 / 运行态 / 启停动作 */
private class HomeEntry(
    val name: String,
    val desc: String,
    val route: String,
    val running: Boolean,
    val toggle: (() -> Unit)?,
    val icon: ImageVector,
)

/** 主页分区 */
private class HomeGroup(val label: String, val routes: List<String>)

/** 卡片入场级联的步长与级数上限（240ms 内完成首屏级联，不阻塞阅读） */
private const val CASCADE_STEP_MS = 40L
private const val CASCADE_MAX_STEPS = 6

/**
 * 主屏幕 / 功能入口页
 *
 * ## 信息层次（自上而下）
 * 1. 全息设备核心 + 「去认证控制」大标题 + 全息终端标识
 * 2. [StatusBar] —— 真实系统遥测
 * 3. **运行中** 分区 —— 仅在有模块运行时出现，当前状态第一眼可见
 * 4. 功能分区：侦察 / 网络 / 无线 / 工具（每张卡片带专属图标）
 * 5. 页脚
 */
@Composable
fun HomeScreen(
    bleVm: BleSpamViewModel, wifiVm: WifiDisruptViewModel,
    btVm: BluetoothJammerViewModel, wjVm: WifiJammerViewModel,
    wsVm: WebServerViewModel, arpVm: ArpScanViewModel,
    httpVm: HttpClientViewModel, pingVm: PingViewModel,
    blescanVm: BleScannerViewModel, portscanVm: PortScannerViewModel,
    walkieVm: WalkieTalkieViewModel, radarVm: RadarViewModel,
    ftVm: FileTransferViewModel, vaultVm: SecureMediaViewModel,
    revVm: RevShellViewModel, payloadVm: PayloadViewModel,
    bruteVm: DirBruteViewModel, cveVm: CVESearchViewModel,
    mirrorVm: MirroringViewModel,
    nav: (String) -> Unit,
) {
    val br by bleVm.isRunning.collectAsState()
    val wr by wifiVm.isFlooding.collectAsState()
    val btr by btVm.isRunning.collectAsState()
    val wjr by wjVm.isRunning.collectAsState()
    val wsr by wsVm.isRunning.collectAsState()
    val arpr by arpVm.isRunning.collectAsState()
    val httpr by httpVm.isRunning.collectAsState()
    val pingr by pingVm.isRunning.collectAsState()
    val blesr by blescanVm.isRunning.collectAsState()
    val portr by portscanVm.isRunning.collectAsState()
    val walkier by walkieVm.isRunning.collectAsState()
    val radarr by radarVm.isRunning.collectAsState()
    val ftr by ftVm.isRunning.collectAsState()
    val revr by revVm.isRunning.collectAsState()
    val bruter by bruteVm.isRunning.collectAsState()
    val cver by cveVm.isRunning.collectAsState()
    val mirrorState by mirrorVm.state.collectAsState()
    val mirrorr = mirrorState is MirroringState.HostLive

    // 每次重组都重建：启停闭包捕获的是当帧运行态，缓存成 remember 会读到过期状态
    val entries = listOf(
        HomeEntry("ARP 扫描", "扫描局域网在线设备", "arp", arpr, { arpVm.scan() }, Icons.Outlined.Router),
        HomeEntry("BLE 扫描", "嗅探附近低功耗蓝牙设备", "blescan", blesr, { if (blesr) blescanVm.stop() else blescanVm.start() }, Icons.Outlined.BluetoothSearching),
        HomeEntry("端口扫描", "扫描目标主机开放端口", "portscan", portr, { if (portr) portscanVm.stop() else portscanVm.start() }, Icons.Outlined.GridView),
        HomeEntry("设备雷达", "雷达图定位附近设备", "radar", radarr, { if (radarr) radarVm.stop() else radarVm.start() }, Icons.Outlined.Radar),
        HomeEntry("CVE 搜索", "查询公开漏洞数据库", "cve", cver, null, Icons.Outlined.Search),

        HomeEntry("WEB 服务", "本机搭建网页服务器", "webserver", wsr, { if (wsr) wsVm.stopServer() else wsVm.startServer() }, Icons.Outlined.Language),
        HomeEntry("HTTP 请求", "发送自定义 HTTP 请求", "http", httpr, null, Icons.Outlined.Http),
        HomeEntry("目录爆破", "枚举网站目录结构", "brute", bruter, { if (bruter) bruteVm.stop() else bruteVm.start() }, Icons.Outlined.ManageSearch),
        HomeEntry("PING 泛洪", "持续探测目标主机", "ping", pingr, { if (pingr) pingVm.stop() else pingVm.start() }, Icons.Outlined.NetworkPing),

        HomeEntry("BLE 泛洪", "发送伪造蓝牙广播包", "ble", br, { if (br) bleVm.stopSpam() else bleVm.startSpam() }, Icons.Outlined.Bluetooth),
        HomeEntry("WIFI 干扰", "伪造 WiFi 信标帧干扰", "wifi", wr, { if (wr) wifiVm.stopFlood() else wifiVm.scanNetworks() }, Icons.Outlined.Wifi),
        HomeEntry("蓝牙压制", "全频段蓝牙压制", "btjam", btr, { if (btr) btVm.stop() else btVm.start() }, Icons.Outlined.BluetoothDisabled),
        HomeEntry("WIFI 压制", "自动压制附近所有网络", "wifijam", wjr, { if (wjr) wjVm.stop() else wjVm.start() }, Icons.Outlined.WifiOff),

        HomeEntry("对讲机", "局域网实时语音通话", "walkie", walkier, { if (walkier) walkieVm.stop() else walkieVm.start() }, Icons.Outlined.Mic),
        HomeEntry("文件快传", "局域网设备互传文件", "filetransfer", ftr, { if (ftr) ftVm.stop() else ftVm.start() }, Icons.Outlined.SwapHoriz),
        HomeEntry("屏幕镜像", "远程查看与控制屏幕", "mirroring", mirrorr, null, Icons.Outlined.Cast),
        HomeEntry("加密保险箱", "加密照片·视频·录音", "vault", false, null, Icons.Outlined.Lock),
        HomeEntry("Payload 生成", "生成反弹 Shell 载荷", "payload", false, null, Icons.Outlined.Terminal),
        HomeEntry("反弹 Shell", "监听反弹 Shell 连接", "revshell", revr, { if (revr) revVm.stop() else revVm.start() }, Icons.Outlined.Lan),
    )

    val groups = listOf(
        HomeGroup("侦察", listOf("arp", "blescan", "portscan", "radar", "cve")),
        HomeGroup("网络", listOf("webserver", "http", "brute", "ping")),
        HomeGroup("无线", listOf("ble", "wifi", "btjam", "wifijam")),
        HomeGroup("工具", listOf("walkie", "filetransfer", "mirroring", "vault", "payload", "revshell")),
    )

    val entriesByRoute = entries.associateBy { it.route }
    val activeEntries = entries.filter { it.running }
    val haptic = LocalHapticFeedback.current

    // 主页用 LazyColumn 而非 verticalScroll：19 张卡片懒加载，
    // 滚动与遥测刷新时只组合可见项，避免整页重组导致的卡顿
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // ─── 全息核心头部 ─────────────────────────────────────────
        item(key = "header") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                StarBadge(68.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "去认证控制",
                    fontFamily = Sans,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                    color = White,
                )
                Spacer(Modifier.height(2.dp))
                GlitchText("全息战术终端", glitchIntervalMs = 3200, style = TextStyle(fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp), modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(2.dp))
                Text(
                    "操作员: 哪吒 · 全息终端 · 加密信道: AES-256",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = Gray,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        item(key = "status") { StatusBar() }
        item(key = "status_gap") { Spacer(Modifier.height(18.dp)) }

        if (activeEntries.isNotEmpty()) {
            item(key = "running_header") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    PulseStar(active = true, size = 7.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("执行中 · ${activeEntries.size} 项任务", fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacGreen)
                }
            }
            items(activeEntries, key = { "running_" + it.route }) { entry ->
                ModuleCard(entry.name, entry.desc, entry.running, entry.icon, delayMs = 0L, onClick = { nav(entry.route) }, onToggle = entry.toggle)
            }
            item(key = "running_gap") { Spacer(Modifier.height(8.dp)) }
        }

        groups.forEach { group ->
            item(key = "label_" + group.label) { SectionLabel(group.label) }
            group.routes.mapNotNull { entriesByRoute[it] }.forEachIndexed { index, entry ->
                item(key = "card_" + entry.route) {
                    ModuleCard(
                        name = entry.name,
                        desc = entry.desc,
                        running = entry.running,
                        icon = entry.icon,
                        delayMs = index.coerceAtMost(CASCADE_MAX_STEPS) * CASCADE_STEP_MS,
                        onClick = { nav(entry.route) },
                        onToggle = entry.toggle,
                    )
                }
            }
            item(key = "group_gap_" + group.label) { Spacer(Modifier.height(6.dp)) }
        }

        item(key = "footer") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Star(Gold, 7.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("中国红客", fontFamily = Sans, fontSize = 11.sp, color = Gray)
                    Spacer(Modifier.width(10.dp))
                    Star(Gold, 7.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("国产自主", fontFamily = Sans, fontSize = 11.sp, color = Gray)
                    Spacer(Modifier.width(10.dp))
                    Star(Gold, 7.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("安全可控", fontFamily = Sans, fontSize = 11.sp, color = Gray)
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.padding(bottom = 4.dp),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Red.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.45f)),
                ) {
                    Text(
                        "关 于",
                        fontFamily = Sans,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Red,
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                nav("about")
                            }
                            .padding(horizontal = 26.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

// ─── Android Studio 预览 ──────────────────────────────────────────────────────

/** 状态栏预览 */
@Preview(name = "状态栏", showBackground = true, backgroundColor = 0xFF060B14, widthDp = 393)
@Composable
private fun StatusBarPreview() {
    PreviewTheme {
        Column(Modifier.background(Dark).fillMaxSize().padding(16.dp)) {
            StatusBar()
        }
    }
}

/** 主页预览 —— 完整 19 模块 + 运行态卡片 */
@Preview(name = "主页", showBackground = true, backgroundColor = 0xFF060B14, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun HomeScreenPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vms = remember(app) { if (app != null) createPreviewVms(app) else null }
        if (vms == null) {
            PreviewFallback("主页")
        } else {
            HomeScreen(
                vms.bleVm, vms.wifiVm, vms.btVm, vms.wjVm, vms.wsVm, vms.arpVm,
                vms.httpVm, vms.pingVm, vms.blescanVm, vms.portscanVm, vms.walkieVm,
                vms.radarVm, vms.ftVm, vms.vaultVm, vms.revVm, vms.payloadVm,
                vms.bruteVm, vms.cveVm, mirrorVm = vms.mirrorVm, nav = {},
            )
        }
    }
}

/** 主路由预览 —— 免责声明 → 启动自检 → 主页完整流程 */
@Preview(name = "主路由完整流程", showBackground = true, backgroundColor = 0xFF060B14, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun MainScreenPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vms = remember(app) { if (app != null) createPreviewVms(app) else null }
        if (vms == null) {
            PreviewFallback("主路由")
        } else {
            MainScreen(
                vms.bleVm, vms.wifiVm, vms.btVm, vms.wjVm, vms.wsVm, vms.arpVm,
                vms.httpVm, vms.pingVm, vms.blescanVm, vms.portscanVm, vms.walkieVm,
                vms.radarVm, vms.ftVm, vms.vaultVm, vms.revVm, vms.payloadVm,
                vms.bruteVm, vms.cveVm, mirrorVm = vms.mirrorVm,
            )
        }
    }
}
