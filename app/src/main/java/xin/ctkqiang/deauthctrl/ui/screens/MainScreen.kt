package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.manager.*
import xin.ctkqiang.deauthctrl.model.*
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import xin.ctkqiang.deauthctrl.ui.components.*
import xin.ctkqiang.deauthctrl.viewmodel.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlin.random.Random as KRandom

private val Red = Color(0xFFFF0000)
private val RedDim = Color(0xFF880000)
private val White = Color(0xFFEEEEEE)
private val Gray = Color(0xFF777777)
private val Dark = Color(0xFF0A0A0A)
private val SurfaceBg = Color(0xFF0D0D0D)
private val BorderDim = Color(0xFF1F1F1F)
private val Mono = FontFamily.Monospace

/**
 * 应用主路由入口 Composable
 *
 * 整个应用的根组件，负责：
 * 1. 启动序列播放（BootSequence → 主界面）
 * 2. 页面路由导航（通过 AnimatedContent 实现 12 个页面的动画过渡）
 * 3. 全局 ScanlineOverlay 叠加（启动完成后持续显示）
 *
 * ## 路由系统
 * 基于字符串的简单路由（"home", "ble", "btjam", "wifi", "wifijam", "webserver",
 * "arp", "http", "ping", "blescan", "portscan", "about"）。
 * 页面切换使用 AnimatedContent，过渡动画为 fade + 水平滑入/滑出。
 *
 * ## 10 个 ViewModel 注入
 * 所有 ViewModel 由 MainActivity 通过 viewModel() 创建并注入到此组件，
 * 通过函数参数按需分发到各个详情页。
 *
 * @param bleVm BLE 泛洪攻击 ViewModel
 * @param wifiVm WiFi 干扰攻击 ViewModel
 * @param btVm 蓝牙压制攻击 ViewModel
 * @param wjVm WiFi 压制攻击 ViewModel
 * @param wsVm Web 服务器 ViewModel
 * @param arpVm ARP 扫描 ViewModel
 * @param httpVm HTTP 客户端 ViewModel
 * @param pingVm Ping 泛洪 ViewModel
 * @param blescanVm BLE 扫描 ViewModel
 * @param portscanVm 端口扫描 ViewModel
 */
@Composable
fun MainScreen(
    bleVm: BleSpamViewModel, wifiVm: WifiDisruptViewModel,
    btVm: BluetoothJammerViewModel, wjVm: WifiJammerViewModel,
    wsVm: WebServerViewModel, arpVm: ArpScanViewModel,
    httpVm: HttpClientViewModel, pingVm: PingViewModel,
    blescanVm: BleScannerViewModel, portscanVm: PortScannerViewModel,
) {
    var screen by remember { mutableStateOf("home") }
    var boot by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Dark)) {
        if (boot) {
            BootSequence(onComplete = { boot = false })
        } else {
            AnimatedContent(targetState = screen, transitionSpec = {
                (fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it / 5 })
                    .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { -it / 5 })
            }) { current ->
                when (current) {
                    "home" -> HomeScreen(bleVm, wifiVm, btVm, wjVm, wsVm, arpVm, httpVm, pingVm, blescanVm, portscanVm, nav = { screen = it })
                    "ble" -> BleDetailScreen(bleVm, back = { screen = "home" })
                    "btjam" -> BtJamDetailScreen(btVm, back = { screen = "home" })
                    "wifi" -> WifiDetailScreen(wifiVm, back = { screen = "home" })
                    "wifijam" -> WifiJamDetailScreen(wjVm, back = { screen = "home" })
                    "webserver" -> WebServerDetailScreen(wsVm, back = { screen = "home" })
                    "arp" -> ArpDetailScreen(arpVm, back = { screen = "home" })
                    "http" -> HttpClientScreen(httpVm, back = { screen = "home" })
                    "ping" -> PingScreen(pingVm, back = { screen = "home" })
                    "blescan" -> BleScannerScreen(blescanVm, back = { screen = "home" })
                    "portscan" -> PortScannerScreen(portscanVm, back = { screen = "home" })
                    "about" -> AboutDetailScreen(back = { screen = "home" })
                }
            }
        }
        if (!boot) ScanlineOverlay(lineSpacing = 3.dp, alpha = 0.035f)
    }
}

/**
 * 实时状态栏
 *
 * 模拟 htop/glances 系统监控的终端状态栏，显示 4 项"系统指标"。
 * 使用 Kotlin Random(seed=7) 确保每次重组时数值变化可预测且平滑。
 * 每 1.5 秒自动刷新 tick，各指标基于 tick 计算伪随机增量。
 *
 * 显示项：
 * - 数据包: 网络数据包计数（8000-13000 范围伪随机）
 * - 接口: 当前激活的无线接口（固定 wlan0）
 * - CPU: 伪 CPU 使用率（8%-30% 循环）
 * - 内存: 伪内存占用量（300-500MB 循环）
 */
@Composable
fun StatusBar() {
    var tick by remember { mutableIntStateOf(0) }
    val rng = remember { KRandom(7) }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }

    Row(
        Modifier.fillMaxWidth().background(Color(0xFF060606)).border(0.5.dp, BorderDim).padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat("数据包", (9000 + rng.nextInt(2000) + tick * 41).toString().padStart(4))
        Stat("接口", "wlan0")
        Stat("CPU", "${10 + tick % 22}%")
        Stat("内存", "${320 + tick * 11 % 180}MB")
    }
}

/**
 * 状态栏单项指标
 *
 * 在 StatusBar 中显示一个 "标签: 值" 格式的指标项。
 * 标签用灰色、值用暗红色，形成层次对比。
 *
 * @param label 指标标签（如 "数据包"、"CPU"）
 * @param value 指标值（如 "8942"、"12%"）
 */
@Composable
private fun Stat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", fontFamily = Mono, fontSize = 11.sp, color = Gray)
        Text(value, fontFamily = Mono, fontSize = 11.sp, color = RedDim)
    }
}

/**
 * 主屏幕 / 功能入口页
 *
 * 应用的核心导航页面，展示 10 个功能模块卡片，每个卡片可点击进入详情页或直接启停。
 * 采用可滚动 Column 布局，内容从上到下依次为：
 * 1. GlitchText 标题（DEAUTHCTRL）
 * 2. 版本号 + 标签行
 * 3. StatusBar（系统状态栏）
 * 4. 10 张 AnimatedCard（逐级延迟入场动画）
 * 5. ASCII 分隔线 + 标语 + 关于链接
 *
 * 每张卡片都绑定触觉反馈（HapticFeedbackType.LongPress）。
 * 所有 ViewModel 的运行状态通过 collectAsState() 实时反映在卡片颜色和文字上。
 */
@Composable
fun HomeScreen(
    bleVm: BleSpamViewModel, wifiVm: WifiDisruptViewModel,
    btVm: BluetoothJammerViewModel, wjVm: WifiJammerViewModel,
    wsVm: WebServerViewModel, arpVm: ArpScanViewModel,
    httpVm: HttpClientViewModel, pingVm: PingViewModel,
    blescanVm: BleScannerViewModel, portscanVm: PortScannerViewModel,
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
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp).verticalScroll(rememberScrollState())) {
        GlitchText("DEAUTHCTRL", style = TextStyle(fontSize = 28.sp), modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("$ v1.0  |  哪吒网络安全  |  fsociety", fontFamily = Mono, fontSize = 11.sp, color = Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(14.dp))
        StatusBar()
        Spacer(Modifier.height(16.dp))

        AnimatedCard("BLE 泛洪", "BLE 广告协议泛洪攻击", running = br, delayMs = 0, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav("ble") }, onToggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (br) bleVm.stopSpam() else bleVm.startSpam() })
        AnimatedCard("WIFI 干扰", "邪恶双子信标泛洪攻击", running = wr, delayMs = 80, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav("wifi") }, onToggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (wr) wifiVm.stopFlood() else wifiVm.scanNetworks() })
        AnimatedCard("蓝牙压制", "全频段蓝牙设备压制攻击", running = btr, delayMs = 160, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav("btjam") }, onToggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (btr) btVm.stop() else btVm.start() })
        AnimatedCard("WIFI 压制", "自动扫描并压制附近所有 SSID", running = wjr, delayMs = 240, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav("wifijam") }, onToggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (wjr) wjVm.stop() else wjVm.start() })
        AnimatedCard("WEB 服务", "热点 HTTP 服务器 — 托管 HTML 页面", running = wsr, delayMs = 320, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav("webserver") }, onToggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (wsr) wsVm.stopServer() else wsVm.startServer() })
        AnimatedCard("ARP 扫描", "局域网设备发现 — arp -a 扫描", running = arpr, delayMs = 400, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav("arp") }, onToggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); arpVm.scan() })
        AnimatedCard("HTTP 请求", "curl 客户端 — GET/POST/PUT/DELETE", running = httpr, delayMs = 480, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav("http") }, onToggle = {  })
        AnimatedCard("PING 泛洪", "无限 ping — 压力测试工具", running = pingr, delayMs = 560, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav("ping") }, onToggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (pingr) pingVm.stop() else pingVm.start() })
        AnimatedCard("BLE 扫描", "低功耗蓝牙嗅探 — RSSI 实时图谱", running = blesr, delayMs = 640,onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav("blescan") }, onToggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (blesr) blescanVm.stop() else blescanVm.start() })
        AnimatedCard("端口扫描", "nmap 风格 TCP 扫描 — 服务识别", running = portr, delayMs = 720, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav("portscan") }, onToggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (portr) portscanVm.stop() else portscanVm.start() })

        Spacer(Modifier.height(14.dp))
        AsciiDivider(modifier = Modifier.padding(vertical = 10.dp))
        Text("中国红客  |  国产自主  |  安全可控", fontFamily = Mono, fontSize = 11.sp, color = Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(6.dp))
        Text("[ 关于 ]", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RedDim, modifier = Modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav("about") }.align(Alignment.CenterHorizontally))
    }
}

/**
 * 带动画入场的功能模块卡片
 *
 * 主屏幕上的核心 UI 单元。每张卡片包含：
 * - 状态指示灯（红色脉冲 = 运行中，暗红 = 停止）
 * - 模块名称（Bold 等宽字体）
 * - 功能描述（次级灰色文字）
 * - 启动/停止按钮（红色实体 / 红色空心切换）
 *
 * ## 动画系统
 * - 入场动画：delayMs 延迟后以 fade + slide 动画显示
 * - 按压动画：spring 弹性缩放至 95%
 * - 颜色过渡：animateColorAsState 在运行/停止状态间平滑切换（400ms tween）
 *   - 边框、背景、名称颜色、描述颜色、指示灯颜色全部独立过渡
 *
 * @param name 模块名称（如 "BLE 泛洪"）
 * @param desc 功能描述（如 "BLE 广告协议泛洪攻击"）
 * @param running 当前运行状态，驱动颜色过渡和按钮文字
 * @param delayMs 入场延迟（毫秒），用于实现逐级错开的入场动画
 * @param onClick 点击卡片主体时的回调（导航到详情页）
 * @param onToggle 点击启停按钮时的回调（切换运行状态）
 */
@Composable
fun AnimatedCard(name: String, desc: String, running: Boolean, delayMs: Long, onClick: () -> Unit, onToggle: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f))

    LaunchedEffect(Unit) { delay(delayMs); visible = true }

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(400)) + slideInVertically(tween(450)) { it / 4 }) {
        val accent by animateColorAsState(if (running) Red.copy(alpha = 0.75f) else BorderDim, tween(400))
        val bg by animateColorAsState(if (running) Red.copy(alpha = 0.06f) else SurfaceBg, tween(400))
        val nameColor by animateColorAsState(if (running) Red else White, tween(400))
        val descColor by animateColorAsState(if (running) Red.copy(alpha = 0.5f) else Gray, tween(400))
        val dotColor by animateColorAsState(if (running) Red else RedDim.copy(alpha = 0.3f), tween(400))

        Surface(
            modifier = Modifier.fillMaxWidth().scale(scale).clickable { pressed = true; onClick(); pressed = false }.padding(vertical = 4.dp),
            shape = RoundedCornerShape(4.dp), color = bg, border = BorderStroke(1.5.dp, accent),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(dotColor))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = nameColor)
                    Text(desc, fontFamily = Mono, fontSize = 11.sp, color = descColor)
                }
                Surface(
                    modifier = Modifier.clickable { pressed = true; onToggle(); pressed = false },
                    shape = RoundedCornerShape(3.dp),
                    color = if (running) Color.Transparent else Red,
                    border = BorderStroke(1.5.dp, if (running) Red.copy(alpha = 0.3f) else Red),
                ) {
                    Text(if (running) "停止" else "启动", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp))
                }
            }
        }
    }
}

/**
 * 终端风格页面头部
 *
 * 所有详情页统一的标题栏组件。左侧显示 [ 返回 ] 按钮（红色），
 * 中间显示页面标题（白色），右侧显示终端提示符 `root@deauth:~#`。
 * 背景色为 SurfaceBg（#0D0D0D），底部有暗色边框。
 *
 * @param title 页面标题（如 "BLE 泛洪引擎"）
 * @param back 点击返回按钮时的回调，用于导航回上一页
 */
@Composable
fun TerminalHeader(title: String, back: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column {
        Row(Modifier.fillMaxWidth().statusBarsPadding().background(SurfaceBg).border(0.5.dp, BorderDim).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("[ 返回 ]", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); back() })
            Spacer(Modifier.width(12.dp))
            Text(title, fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = White)
            Spacer(Modifier.weight(1f))
            Text("root@deauth:~#", fontFamily = Mono, fontSize = 11.sp, color = Gray)
        }
    }
}

/** 终端提示符渲染器 — 显示 `root@deauth:~# ` 红色前缀 */
@Composable
fun TermPrompt(cmd: String) {
    Text("root@deauth:~# ", fontFamily = Mono, fontSize = 12.sp, color = Red, fontWeight = FontWeight.Bold)
}

/** 终端输出行渲染器 — 缩进两格显示输出文本 */
@Composable
fun TermOutput(text: String, color: Color = White) {
    Text("  $text", fontFamily = Mono, fontSize = 11.sp, color = color)
}

/** 终端行渲染器 — `$ prompt output` 格式，output 含 FAIL/ERROR 时红色 */
@Composable
fun TermLine(prompt: String, output: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text("$ ", fontFamily = Mono, fontSize = 12.sp, color = Red, fontWeight = FontWeight.Bold)
        Text("$prompt ", fontFamily = Mono, fontSize = 12.sp, color = White)
        Text(output, fontFamily = Mono, fontSize = 12.sp, color = if (output.contains("FAIL") || output.contains("ERROR")) Red else Gray)
    }
}

/**
 * 终端面板组件
 *
 * 带标题栏的卡片式面板，使用 ┌─/└─ 字符模拟终端框线。
 * 标题栏为微红背景（Red 8% alpha），内容区为深黑背景（SurfaceBg）。
 * 顶部 ┌─ 标题 + 底部 └─ 形成完整框线视觉。
 *
 * @param title 面板标题（如 "载荷配置"、"统计"）
 * @param modifier Modifier 修饰符
 * @param content 面板内容（在 ColumnScope 中渲染，支持 Column 子布局）
 */
@Composable
fun TermPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().border(1.dp, BorderDim).background(SurfaceBg)) {
        Row(Modifier.fillMaxWidth().background(Red.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text("┌─ $title", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(10.dp)) { content() }
        Text("└─", fontFamily = Mono, fontSize = 11.sp, color = Red)
    }
}

/**
 * 区域标签
 *
 * 在详情页中标记一个逻辑区块的标题，使用暗红色粗体等宽字体。
 *
 * @param text 标签文字（如 "载荷配置"、"数据包日志"）
 */
@Composable
fun SectionLabel(text: String) {
    Text(text, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RedDim, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

/**
 * BLE 泛洪攻击详情页
 *
 * BLE 广告协议泛洪工具的控制和监控界面。
 * 功能区块：
 * - 载荷配置面板（TermPanel）：选择广播载荷 Profile + 调整发包间隔（20-100ms）
 * - 执行/中止按钮：终端命令风格 `$ ./start_ble --flood` / `$ ./stop_ble`
 * - 实时统计：已发包数 + 每秒发包速率（pps）
 * - 数据包日志：`$ tail -f /var/log/ble_packets.log` 风格日志流
 *
 * 日志条目自动滚动到最新数据（animateScrollToItem(0)）。
 */
@Composable
fun BleDetailScreen(vm: BleSpamViewModel, back: () -> Unit) {
    val log by vm.log.collectAsState(); val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    var profile by remember { mutableStateOf(vm.getCurrentProfile()) }
    var interval by remember { mutableFloatStateOf(vm.getCurrentInterval().toFloat()) }
    val list = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(log.size) { if (log.isNotEmpty()) list.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("BLE 泛洪引擎", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            TermPanel("载荷配置") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.getProfiles().forEach { p ->
                        val sel = p == profile
                        Surface(modifier = Modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); profile = p; vm.setProfile(p) }, shape = RoundedCornerShape(3.dp), color = if (sel) Red.copy(alpha = 0.15f) else Color.Transparent, border = BorderStroke(1.dp, if (sel) Red.copy(alpha = 0.6f) else BorderDim)) {
                            Text(p.displayName, fontFamily = Mono, fontSize = 12.sp, color = if (sel) Red else Gray, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$ ", fontFamily = Mono, fontSize = 12.sp, color = Red, fontWeight = FontWeight.Bold)
                    Text("--interval ${interval.toLong()}ms", fontFamily = Mono, fontSize = 12.sp, color = White)
                }
                Slider(interval, { interval = it; vm.setInterval(interval.toLong()) }, valueRange = 20f..100f, steps = 7, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = BorderDim))
            }

            Spacer(Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (running) vm.stopSpam() else vm.startSpam() }, shape = RoundedCornerShape(3.dp), color = if (running) Color.Transparent else Red, border = BorderStroke(1.5.dp, if (running) Red.copy(alpha = 0.3f) else Red)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text("$ ", fontFamily = Mono, fontSize = 15.sp, color = if (running) Red else White, fontWeight = FontWeight.Bold)
                    Text(if (running) "./stop_ble" else "./start_ble --flood", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                }
            }
            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red) }
            if (running && log.isNotEmpty()) {
                val ok = log.count { it.success }
                Text("$ packets_sent=$ok  pps=${ok * 1000L / maxOf(1, System.currentTimeMillis() - log.first().timestamp)}", fontFamily = Mono, fontSize = 11.sp, color = White)
            }
            AnimatedVisibility(running) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim) }

            Spacer(Modifier.height(8.dp))
            AsciiDivider()
            Text("$ tail -f /var/log/ble_packets.log", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (log.isEmpty()) item { Text("$ waiting for packets...", fontFamily = Mono, fontSize = 11.sp, color = Gray, modifier = Modifier.padding(vertical = 16.dp)) }
                items(log.reversed()) { e ->
                    Text(
                        "[${tf.format(Date(e.timestamp))}]  ${e.profile.displayName.take(12).padEnd(12)}  ${if (e.success) "SENT" else "FAIL"}  #${e.index.toString().padStart(5, '0')}",
                        fontFamily = Mono, fontSize = 10.sp,
                        color = if (e.success) White else Red,
                    )
                }
            }
        }
    }
}

/**
 * 蓝牙压制攻击详情页
 *
 * 全频段蓝牙压制工具的监控界面。同时运行 BLE 泛洪 + 经典蓝牙发现。
 * 功能区块：
 * - 状态面板（TermPanel）：BLE泛洪/设备发现 开关状态 + 已发现设备计数
 * - 执行/中止按钮：`$ ./start_btjam --all-channels` / `$ ./stop_btjam`
 * - 附近设备列表（`$ hcitool scan --active` 风格）
 * - 运行日志（`$ journalctl -u btjam -f` 风格）
 */
@Composable
fun BtJamDetailScreen(vm: BluetoothJammerViewModel, back: () -> Unit) {
    val log by vm.log.collectAsState(); val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val devices by vm.discoveredDevices.collectAsState(); val count by vm.discoveryCount.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("蓝牙压制引擎", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            TermPanel("状态") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatusChip("BLE泛洪", true)
                    StatusChip("设备发现", true)
                    StatusChip("已发现", "$count 台")
                }
            }

            Spacer(Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (running) vm.stop() else vm.start() }, shape = RoundedCornerShape(3.dp), color = if (running) Color.Transparent else Red, border = BorderStroke(1.5.dp, if (running) Red.copy(alpha = 0.3f) else Red)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text("$ ", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                    Text(if (running) "./stop_btjam" else "./start_btjam --all-channels", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                }
            }
            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red) }
            AnimatedVisibility(running) { Column { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim) } }
            AnimatedVisibility(devices.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text("$ hcitool scan --active", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.fillMaxWidth().height(140.dp)) {
                        items(devices.take(10)) { d ->
                            Text("  ${d.name.take(26).padEnd(26)} ${d.address.padEnd(17)} [${d.type}]", fontFamily = Mono, fontSize = 10.sp, color = White)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            AsciiDivider()
            Text("$ journalctl -u btjam -f", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(log.takeLast(100).reversed()) { e ->
                    Text("[${tf.format(Date(e.timestamp))}]  [${e.module}]  ${e.message}", fontFamily = Mono, fontSize = 10.sp, color = White)
                }
            }
        }
    }
}

/** 布尔状态芯片 — 显示 "标签: ON"（红色）或 "标签: OFF"（灰色） */
@Composable
fun StatusChip(label: String, active: Boolean) = Text("$label: ${if (active) "ON" else "OFF"}", fontFamily = Mono, fontSize = 11.sp, color = if (active) Red else Gray)

@Composable
/** 键值状态芯片 — 显示 "标签: 值"（白色文字） */
@Composable
fun StatusChip(label: String, value: String) = Text("$label: $value", fontFamily = Mono, fontSize = 11.sp, color = White)

/**
 * WiFi 干扰攻击详情页
 *
 * 邪恶双子信标泛洪工具。功能区块：
 * - 扫描按钮：`$ airodump-ng wlan0` 风格，扫描附近 2.4/5GHz WiFi 网络
 * - 网络列表：BSSID + SSID + CH + dBm 显示（终端列对齐），选中目标
 * - 目标配置：duration 滑动条（1-60s），目标 SSID 显示
 * - 执行/中止按钮：`$ ./start_flood --deauth --duration=Ns`
 * - 进度显示：cycle N/total + LinearProgressIndicator
 */
@Composable
fun WifiDetailScreen(vm: WifiDisruptViewModel, back: () -> Unit) {
    val ss by vm.scanState.collectAsState(); val hr by vm.hotspotResult.collectAsState(); val fl by vm.isFlooding.collectAsState()
    var sel by remember { mutableStateOf<WifiNetwork?>(null) }
    var dur by remember { mutableIntStateOf(vm.getDuration()) }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WIFI 干扰引擎", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.scanNetworks() }, shape = RoundedCornerShape(3.dp), color = Red) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text("$ ", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = White)
                    Text("airodump-ng wlan0", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = White)
                }
            }
            Spacer(Modifier.height(8.dp))

            when (val s = ss) {
                is ScanState.Scanning -> Text("$ scanning 2.4/5GHz bands...", fontFamily = Mono, fontSize = 12.sp, color = White)
                is ScanState.Results -> {
                    Text("$ found ${s.networks.size} networks -- select target:", fontFamily = Mono, fontSize = 11.sp, color = Gray)
                    LazyColumn(Modifier.fillMaxWidth().weight(0.45f)) {
                        items(s.networks, key = { it.bssid }) { n ->
                            val on = sel?.bssid == n.bssid
                            Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); sel = n; vm.selectTarget(n) }, color = if (on) Red.copy(alpha = 0.08f) else Color.Transparent) {
                                Row(Modifier.padding(vertical = 4.dp, horizontal = 4.dp)) {
                                    Text(if (on) "▸" else " ", fontFamily = Mono, fontSize = 13.sp, color = Red, modifier = Modifier.width(18.dp))
                                    Text("${n.bssid.take(17).padEnd(17)}  ", fontFamily = Mono, fontSize = 11.sp, color = Gray)
                                    Text(n.ssid.ifBlank { "<HIDDEN>" }.take(18).padEnd(18), fontFamily = Mono, fontSize = 12.sp, color = if (on) White else Gray, modifier = Modifier.weight(1f))
                                    Text("CH${n.channel.toString().padStart(2)} ${n.signalStrength}dBm", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                                }
                            }
                        }
                    }
                }
                is ScanState.Error -> Text("[!] ${s.message}", fontFamily = Mono, fontSize = 12.sp, color = Red)
                else -> {}
            }

            AnimatedVisibility(sel != null) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text("$ target=${sel!!.ssid}  duration=${dur}s", fontFamily = Mono, fontSize = 12.sp, color = White)
                    Slider(dur.toFloat(), { dur = it.toInt().coerceIn(1, 60) }, valueRange = 1f..60f, steps = 14, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = BorderDim))
                    Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.setDuration(dur); if (fl) vm.stopFlood() else vm.startFlood() }, shape = RoundedCornerShape(3.dp), color = if (fl) Color.Transparent else Red, border = BorderStroke(1.5.dp, if (fl) Red.copy(alpha = 0.3f) else Red)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                            Text("$ ", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (fl) Red else White)
                            Text(if (fl) "./stop_flood" else "./start_flood --deauth --duration=${dur}s", fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (fl) Red else White)
                        }
                    }
                }
            }

            when (val r = hr) {
                is HotspotResult.Progress -> { Spacer(Modifier.height(4.dp)); LinearProgressIndicator(progress = { r.cycle.toFloat() / r.totalCycles }, modifier = Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim); Text("$ cycle ${r.cycle}/${r.totalCycles}", fontFamily = Mono, fontSize = 10.sp, color = White) }
                is HotspotResult.Completed -> Text("$ flood complete", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red)
                is HotspotResult.Error -> Text("[!] ${r.message}", fontFamily = Mono, fontSize = 12.sp, color = Red)
                else -> {}
            }
        }
    }
}

/**
 * WiFi 压制攻击详情页
 *
 * 自动扫描并交替压制附近所有 SSID。功能区块：
 * - 配置面板（TermPanel）：信道切换间隔 Slider（60-300ms）
 * - 执行/中止按钮：`$ ./start_wifijam --jam-all --aggressive`
 * - 实时压制统计：jamming N SSIDs + beacon 计数 + cycles 计数
 * - 目标网络列表：BSSID + SSID + CH + dBm
 * - 运行日志：`$ tail -f /var/log/wifijam.log` 风格
 */
@Composable
fun WifiJamDetailScreen(vm: WifiJammerViewModel, back: () -> Unit) {
    val log by vm.log.collectAsState(); val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val ss by vm.scanState.collectAsState(); val tc by vm.targetCount.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    var ms by remember { mutableFloatStateOf(vm.getInterval().toFloat()) }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WIFI 压制引擎", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            TermPanel("配置") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$ ", fontFamily = Mono, fontSize = 12.sp, color = Red, fontWeight = FontWeight.Bold)
                    Text("--channel-switch-interval ${ms.toLong()}ms", fontFamily = Mono, fontSize = 12.sp, color = White)
                }
                Slider(ms, { ms = it; vm.setInterval(ms.toLong()) }, valueRange = 60f..300f, steps = 11, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = BorderDim))
            }

            Spacer(Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (running) vm.stop() else vm.start() }, shape = RoundedCornerShape(3.dp), color = if (running) Color.Transparent else Red, border = BorderStroke(1.5.dp, if (running) Red.copy(alpha = 0.3f) else Red)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text("$ ", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                    Text(if (running) "./stop_wifijam" else "./start_wifijam --jam-all --aggressive", fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                }
            }
            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red) }
            AnimatedVisibility(running) {
                Column { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim); Text("$ jamming=$tc beacons=${vm.getBeaconCount()} cycles=${vm.getCycleCount()}", fontFamily = Mono, fontSize = 12.sp, color = White) }
            }

            when (val s = ss) {
                is JammerScanState.Scanning -> Text("$ scanning for targets...", fontFamily = Mono, fontSize = 12.sp, color = White)
                is JammerScanState.Results -> {
                    Spacer(Modifier.height(8.dp))
                    Text("$ targets_found=${s.networks.size}", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
                    LazyColumn(Modifier.fillMaxWidth().weight(0.4f)) {
                        items(s.networks.take(12)) { n ->
                            Text("  ${n.bssid.take(17).padEnd(17)}  ${n.ssid.ifBlank { "<HIDDEN>" }.take(22).padEnd(22)} CH${n.channel.toString().padStart(2)} ${n.signalStrength}dBm", fontFamily = Mono, fontSize = 10.sp, color = White)
                        }
                    }
                }
                is JammerScanState.Error -> Text("[!] ${s.message}", fontFamily = Mono, fontSize = 12.sp, color = Red)
                else -> {}
            }
            Spacer(Modifier.height(8.dp))
            AsciiDivider()
            Text("$ tail -f /var/log/wifijam.log", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(0.5f)) {
                items(log.takeLast(80).reversed()) { e ->
                    Text("[${tf.format(Date(e.timestamp))}]  [${e.module}]  ${e.message}", fontFamily = Mono, fontSize = 10.sp, color = White)
                }
            }
        }
    }
}

/**
 * 关于页面
 *
 * 显示应用和作者信息。使用 TermPanel 卡片 + 终端命令风格排版：
 * - `$ uname -a` → 系统标识行（DeauthCtrl v1.0 | 中国红客 | 国产自主 | 安全可控）
 * - `$ whoami` → 作者身份行（哪吒网络安全 / ctkqiang）
 * - TermPanel 含：作者、代号、邮箱、仓库、架构、许可 6 项信息
 */
@Composable
fun AboutDetailScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("关于", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(14.dp)) {
            TermPanel("系统信息") {
                InfoRow("作者", "钟智强 (ctkqiang)")
                InfoRow("代号", "哪吒网络安全")
                InfoRow("邮箱", "ctkqiang@dingtalk.com")
                InfoRow("仓库", "gitcode.com/ctkqiang_sr/DeauthCtrl")
                InfoRow("架构", "MVVM + StateFlow + Compose")
                InfoRow("许可", "授权安全研究")
            }

            Spacer(Modifier.height(14.dp))
            Text("$ uname -a", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
            Text("  DeauthCtrl v1.0  |  中国红客  |  国产自主  |  安全可控", fontFamily = Mono, fontSize = 11.sp, color = White)
            Spacer(Modifier.height(6.dp))
            Text("$ whoami", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
            Text("  哪吒网络安全 (ctkqiang)", fontFamily = Mono, fontSize = 11.sp, color = White)
        }
    }
}

/** 信息行 — 在关于页面中显示 "标签: 值" 格式的信息项 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("$label  ", fontFamily = Mono, fontSize = 13.sp, color = Gray, modifier = Modifier.width(70.dp))
        Text(value, fontFamily = Mono, fontSize = 13.sp, color = White)
    }
}

/**
 * Web 服务器详情页
 *
 * 嵌入式 HTTP 服务器管理界面。功能区块：
 * - HTML 文件选择面板：显示当前文件名 + [选择文件] 按钮（通过系统文件选择器选取 HTML）
 * - 启动/停止按钮：`$ ./start_webserver --port=80` / `$ ./stop_webserver`
 * - 服务器信息面板（运行后显示）：
 *   - `$ ifconfig wlan0` → IP 地址
 *   - `$ http://...` → HTTP 响应头模拟（200 OK、Content-Type、Server）
 *   - 浏览器访问 URL 提示
 */
@Composable
fun WebServerDetailScreen(vm: WebServerViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val url by vm.serverUrl.collectAsState()
    val error by vm.error.collectAsState()
    val fileName by vm.fileName.collectAsState()
    val haptic = LocalHapticFeedback.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { vm.loadFile(it) }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WEB 服务器", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            TermPanel("HTML 文件") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$ ", fontFamily = Mono, fontSize = 12.sp, color = Red, fontWeight = FontWeight.Bold)
                    Text(fileName, fontFamily = Mono, fontSize = 12.sp, color = White, modifier = Modifier.weight(1f))
                    Surface(modifier = Modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); filePicker.launch(arrayOf("text/html")) }, shape = RoundedCornerShape(3.dp), color = Red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Red.copy(alpha = 0.4f))) {
                        Text("选择文件", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (running) vm.stopServer() else vm.startServer() }, shape = RoundedCornerShape(3.dp), color = if (running) Color.Transparent else Red, border = BorderStroke(1.5.dp, if (running) Red.copy(alpha = 0.3f) else Red)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text("$ ", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                    Text(if (running) "./stop_webserver" else "./start_webserver --port=80", fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                }
            }
            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red) }

            AnimatedVisibility(running) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim)
                    Spacer(Modifier.height(8.dp))
                    TermPanel("服务器信息") {
                        Text("$ ifconfig wlan0", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
                        Text("  inet addr: ${url.replace("http://", "").replace(":80", "")}", fontFamily = Mono, fontSize = 12.sp, color = White)
                        Spacer(Modifier.height(8.dp))
                        Text("$ http://${url.replace("http://", "")}", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
                        Text("  HTTP/1.1 200 OK", fontFamily = Mono, fontSize = 12.sp, color = White)
                        Text("  Content-Type: text/html", fontFamily = Mono, fontSize = 12.sp, color = Gray)
                        Text("  Server: DeauthCtrl/1.0", fontFamily = Mono, fontSize = 12.sp, color = Gray)
                        Spacer(Modifier.height(8.dp))
                        AsciiDivider()
                        Text("  浏览器访问: $url", fontFamily = Mono, fontSize = 12.sp, color = White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * ARP 网络扫描详情页
 *
 * 局域网 Ping Sweep 设备发现界面。功能区块：
 * - 扫描按钮：`$ arp -a`（红色实体按钮，执行 /24 子网 Ping Sweep）
 * - 进度显示：`$ ping -c 1 192.168.x.0/24 ...`
 * - 设备列表面板（TermPanel）：
 *   - IP Address | MAC Address | Hostname 三列表头
 *   - 每行显示设备 IP（白色）、MAC（灰色或"(pending)"）、主机名（暗红或灰色）
 *   - 支持滚动，最多显示 400dp 高度
 * - 未发现设备时的提示信息
 */
@Composable
fun ArpDetailScreen(vm: ArpScanViewModel, back: () -> Unit) {
    val devices by vm.devices.collectAsState()
    val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("ARP 网络扫描", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.scan() }, shape = RoundedCornerShape(3.dp), color = Red) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text("$ ", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = White)
                    Text("arp -a", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = White)
                }
            }

            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red) }

            if (running) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim)
                Text("$ ping -c 1 192.168.x.0/24 ...", fontFamily = Mono, fontSize = 11.sp, color = White)
            }

            AnimatedVisibility(devices.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    AsciiDivider()
                    Text("$ arp -a", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))

                    TermPanel("发现 ${devices.size} 台设备") {
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            Text("IP Address".padEnd(16), fontFamily = Mono, fontSize = 10.sp, color = RedDim, fontWeight = FontWeight.Bold)
                            Text("MAC Address".padEnd(20), fontFamily = Mono, fontSize = 10.sp, color = RedDim, fontWeight = FontWeight.Bold)
                            Text("Hostname", fontFamily = Mono, fontSize = 10.sp, color = RedDim, fontWeight = FontWeight.Bold)
                        }
                        AsciiDivider(modifier = Modifier.padding(vertical = 2.dp))
                        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 400.dp)) {
                            items(devices) { d ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                    Text(d.ip.padEnd(16), fontFamily = Mono, fontSize = 10.sp, color = White)
                                    Text(d.mac.ifEmpty { "(pending)" }.padEnd(20), fontFamily = Mono, fontSize = 10.sp, color = if (d.mac.isNotEmpty()) Gray else RedDim.copy(alpha = 0.5f))
                                    Text(d.hostname.ifEmpty { d.latency }, fontFamily = Mono, fontSize = 10.sp, color = if (d.hostname.isNotEmpty()) RedDim else Gray)
                                }
                            }
                        }
                    }
                }
            }

            if (!running && devices.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("$ 点击上方 ping sweep 扫描局域网", fontFamily = Mono, fontSize = 11.sp, color = Gray)
            }
        }
    }
}

/**
 * BLE 嗅探扫描详情页
 *
 * 低功耗蓝牙被动扫描和 RSSI 图谱展示界面。功能区块：
 * - 扫描按钮：`$ hcitool lescan --duplicates` / `--stop`
 * - 设备列表：名称 + RSSI + MAC 地址，RSSI 按信号强度着
 * - 选中设备详情面板（TermPanel）：
 *   - 基本信息：ADDR、TX Power、设备类型
 *   - 服务 UUID 列表 + 厂商自定义数据（MFR）
 *   - **RSSI 实时走势图**（Canvas 绘制，60dp 高）：
 *     - X 轴 = 时间（最近 50 次扫描结果的索引）
 *     - Y 轴 = dBm 范围（-100 到 -20）
 *     - 红色折线图，Stroke 1.5f 线宽
 *     - 图表下方显示 min/max/cur 统计值
 */
@Composable
fun BleScannerScreen(vm: BleScannerViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val devices by vm.devices.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current
    var selected by remember { mutableStateOf<String?>(null) }
    val selDev = devices.find { it.address == selected }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("BLE 嗅探扫描", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (running) vm.stop() else vm.start() }, shape = RoundedCornerShape(3.dp), color = if (running) Color.Transparent else Red, border = BorderStroke(1.5.dp, if (running) Red.copy(alpha = 0.3f) else Red)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text("$ ", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                    Text(if (running) "hcitool lescan --stop" else "hcitool lescan --duplicates", fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                }
            }
            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red) }
            if (running) { Spacer(Modifier.height(6.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim); Text("$ devices_found=${devices.size}", fontFamily = Mono, fontSize = 11.sp, color = White) }

            if (selDev != null) {
                Spacer(Modifier.height(6.dp))
                TermPanel("设备详情: ${selDev.name}") {
                    Row(Modifier.fillMaxWidth()) {
                        Text("ADDR: ${selDev.address}   TX: ${if (selDev.txPower > Int.MIN_VALUE) "${selDev.txPower}dBm" else "N/A"}   类型: ${selDev.deviceType.ifEmpty { "未知" }}", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                    }
                    if (selDev.services.isNotEmpty()) Text("  服务: ${selDev.services.joinToString(", ")}", fontFamily = Mono, fontSize = 10.sp, color = RedDim)
                    if (selDev.manufacturerData.isNotEmpty()) Text("  MFR:  ${selDev.manufacturerData.entries.joinToString { "${it.key}: ${it.value}" }}", fontFamily = Mono, fontSize = 9.sp, color = Gray)

                    if (selDev.rssiHistory.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        Text("RSSI (dBm)", fontFamily = Mono, fontSize = 9.sp, color = RedDim)
                        Box(Modifier.fillMaxWidth().height(60.dp).background(Color(0xFF020202)).border(0.5.dp, BorderDim)) {
                            Canvas(Modifier.fillMaxSize()) {
                                val h = size.height; val w = size.width
                                val vals = selDev.rssiHistory
                                if (vals.size > 1) {
                                    val step = w / (vals.size - 1)
                                    val minR = -100f; val maxR = -20f; val range = maxR - minR
                                    val path = Path()
                                    vals.forEachIndexed { i, v ->
                                        val x = i.toFloat() * step
                                        val y = h - ((v.toFloat() - minR) / range * h).coerceIn(0f, h)
                                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                    }
                                    drawPath(path, Color.Red, style = Stroke(1.5f))
                                }
                            }
                        }
                        Text("  min:${selDev.rssiHistory.minOrNull() ?: 0}  max:${selDev.rssiHistory.maxOrNull() ?: 0}  cur:${selDev.rssi}", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            AsciiDivider()
            Text("$ devices_found=${devices.size}", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(devices) { d ->
                    val sel = d.address == selected
                    Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selected = if (sel) null else d.address }, color = if (sel) Red.copy(alpha = 0.06f) else Color.Transparent) {
                        Column(Modifier.padding(vertical = 2.dp, horizontal = 4.dp)) {
                            Row {
                                Text(if (sel) "▸" else " ", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.width(14.dp))
                                Text(d.name.take(20).padEnd(20), fontFamily = Mono, fontSize = 11.sp, color = if (sel) Red else White, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                Text(" ${d.rssi}dBm".padStart(7), fontFamily = Mono, fontSize = 10.sp, color = if (d.rssi > -50) RedDim else if (d.rssi > -70) White else Gray)
                                Text("  ${d.address}", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ping 泛洪详情页
 *
 * 持续 ICMP Ping 探测与压力测试界面。功能区块：
 * - 目标主机输入框（TermInput）
 * - 启动/停止按钮：`$ ping -c unlimited <host>` / `$ killall ping`
 * - 统计面板（TermPanel）：6 列等宽统计（发送/接收/丢包%/最小/平均/最大）
 * - 实时 Ping 结果日志：
 *   - `N bytes from <ip>: icmp_seq=X ttl=Y time=Zms`
 *   - 按 RTT 着色：>500ms 红色, >100ms 暗红, <100ms 白色
 *   - 自动滚动到最新结果
 */
@Composable
fun PingScreen(vm: PingViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val host by vm.host.collectAsState()
    val results by vm.results.collectAsState()
    val sent by vm.sent.collectAsState(); val recv by vm.received.collectAsState()
    val loss by vm.loss.collectAsState()
    val min by vm.min.collectAsState(); val avg by vm.avg.collectAsState(); val max by vm.max.collectAsState()
    val haptic = LocalHapticFeedback.current
    val list = rememberLazyListState()
    LaunchedEffect(results.size) { if (results.isNotEmpty()) list.animateScrollToItem(results.size - 1) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("PING 泛洪", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            TermInput("host", host) { vm.setHost(it) }

            Spacer(Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (running) vm.stop() else vm.start() }, shape = RoundedCornerShape(3.dp), color = if (running) Color.Transparent else Red, border = BorderStroke(1.5.dp, if (running) Red.copy(alpha = 0.3f) else Red)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text("$ ", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                    Text(if (running) "killall ping" else "ping -c unlimited $host", fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                }
            }

            if (running) { Spacer(Modifier.height(6.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim) }

            if (sent > 0) {
                Spacer(Modifier.height(6.dp))
                TermPanel("统计") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatCol("发送", "$sent")
                        StatCol("接收", "$recv")
                        StatCol("丢包", "${loss}%")
                        StatCol("最小", "${min}ms")
                        StatCol("平均", "${avg}ms")
                        StatCol("最大", "${max}ms")
                    }
                }
            }

            if (results.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                AsciiDivider()
                Text("$ ping $host", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
                LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(results) { r ->
                        Text("${r.bytes}b from ${r.ip}: icmp_seq=${r.sequence} ttl=${r.ttl} time=${r.timeMs}ms", fontFamily = Mono, fontSize = 10.sp, color = if (r.timeMs > 500) Red else if (r.timeMs > 100) RedDim else White)
                    }
                }
            }
        }
    }
}

/** Ping 统计列 — 垂直排列：值（红色粗体）+ 标签（灰色小字） */
@Composable
private fun StatCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Red)
        Text(label, fontFamily = Mono, fontSize = 9.sp, color = Gray)
    }
}

/**
 * HTTP 客户端详情页（curl/Postman 风格）
 *
 * 完整的 HTTP 请求构建和响应查看工具。功能区块：
 * - URL 输入框（TermInput）
 * - 方法选择器（GET/POST/PUT/DELETE/OPTIONS/PATCH/HEAD）— 水平滚动芯片
 * - Headers 输入框（多行文本，格式: "Key: Value"，每行一个）
 * - Body 输入框（仅 POST/PUT/PATCH 显示）
 * - 发送按钮：`$ curl -X <METHOD> <URL>`
 * - 响应区域：
 *   - HTTP 状态行 + 耗时
 *   - 响应头（折叠显示）
 *   - 响应体（可选中复制，SelectionContainer）
 */
@Composable
fun HttpClientScreen(vm: HttpClientViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val resp by vm.response.collectAsState()
    val error by vm.error.collectAsState()
    val url by vm.url.collectAsState()
    val method by vm.method.collectAsState()
    val headers by vm.headers.collectAsState()
    val body by vm.body.collectAsState()
    val haptic = LocalHapticFeedback.current
    val methods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("HTTP 客户端", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            TermInput("URL", url) { vm.setUrl(it) }

            Spacer(Modifier.height(6.dp))
            Text("$ --method", fontFamily = Mono, fontSize = 11.sp, color = Red)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                methods.forEach { m ->
                    val sel = m == method
                    Surface(modifier = Modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.setMethod(m) }, shape = RoundedCornerShape(2.dp), color = if (sel) Red.copy(alpha = 0.15f) else Color.Transparent, border = BorderStroke(1.dp, if (sel) Red else BorderDim)) {
                        Text(m, fontFamily = Mono, fontSize = 10.sp, color = if (sel) Red else Gray, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            TermInput("Headers", headers, placeholder = "Key: Value (每行一个)") { vm.setHeaders(it) }

            if (method in listOf("POST", "PUT", "PATCH")) {
                Spacer(Modifier.height(6.dp))
                TermInput("Body", body) { vm.setBody(it) }
            }

            Spacer(Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.send() }, shape = RoundedCornerShape(3.dp), color = if (running) RedDim else Red) {
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center) {
                    Text("$ curl -X $method", fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = White)
                    Text(" $url", fontFamily = Mono, fontSize = 12.sp, color = White.copy(alpha = 0.6f))
                }
            }

            if (running) { Spacer(Modifier.height(6.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim) }
            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red) }

            if (resp != null) {
                Spacer(Modifier.height(8.dp))
                AsciiDivider()
                Text("$ HTTP/${resp!!.statusCode} ${resp!!.statusMessage}  (${resp!!.timeMs}ms)", fontFamily = Mono, fontSize = 12.sp, color = if (resp!!.statusCode < 400) White else Red, fontWeight = FontWeight.Bold)

                val hdrText = resp!!.headers.entries.joinToString("\n") { (k, v) -> "  $k: ${v.joinToString(", ")}" }
                if (hdrText.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("$ response headers:", fontFamily = Mono, fontSize = 10.sp, color = RedDim)
                    Text(hdrText, fontFamily = Mono, fontSize = 9.sp, color = Gray)
                }

                Spacer(Modifier.height(4.dp))
                Text("$ response body:", fontFamily = Mono, fontSize = 10.sp, color = RedDim)
                LazyColumn(Modifier.fillMaxWidth().weight(1f).background(SurfaceBg).padding(8.dp)) {
                    item { SelectionContainer { Text(resp!!.body.ifEmpty { "(空)" }, fontFamily = Mono, fontSize = 10.sp, color = White) } }
                }
            }
        }
    }
}

/**
 * 终端风格输入字段
 *
 * 带 `$ --label` 前缀的文本输入框，背景为 SurfaceBg，边框为 BorderDim。
 * 支持 placeholder 占位文字（颜色为 Gray 40% alpha）。
 * 使用 BasicTextField 实现，无 Material 装饰，纯终端风格。
 *
 * @param label 标签名（如 "URL"、"host"、"Headers"）
 * @param value 当前输入值（双向绑定由调用方管理）
 * @param placeholder 占位提示文字，为空时不显示
 * @param onValue 值变更回调
 */
@Composable
fun TermInput(label: String, value: String, placeholder: String = "", onValue: (String) -> Unit) {
    Text("$ --$label", fontFamily = Mono, fontSize = 11.sp, color = Red)
    BasicTextField(
        value = value, onValueChange = onValue,
        textStyle = TextStyle(fontFamily = Mono, fontSize = 12.sp, color = White),
        modifier = Modifier.fillMaxWidth().background(SurfaceBg).border(1.dp, BorderDim).padding(8.dp),
        decorationBox = { inner ->
            if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, fontFamily = Mono, fontSize = 12.sp, color = Gray.copy(alpha = 0.4f))
            inner()
        },
    )
}

/**
 * 端口扫描详情页（Nmap 风格）
 *
 * TCP Connect 端口扫描工具。功能区块：
 * - 目标主机输入框（TermInput）
 * - 扫描按钮：`$ nmap -sT -T4 <host>` / `$ kill scan`
 * - 进度条 + 已扫描/开放计数
 * - Nmap 风格扫描报告（TermPanel）：
 *   - `Nmap 扫描报告: <host>`
 *   - 每行 `端口/tcp | 状态 | 服务名`
 *   - Banner 输出（`|_ banner: ...`）
 *   - 底部统计行：共扫描 N 端口，M 个开放
 */
@Composable
fun PortScannerScreen(vm: PortScannerViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val host by vm.host.collectAsState()
    val results by vm.results.collectAsState()
    val scanned by vm.scanned.collectAsState()
    val total by vm.total.collectAsState()
    val haptic = LocalHapticFeedback.current
    val open = results.filter { it.state == "open" }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("端口扫描", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            TermInput("host", host) { vm.setHost(it) }
            Spacer(Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (running) vm.stop() else vm.start() }, shape = RoundedCornerShape(3.dp), color = if (running) Color.Transparent else Red, border = BorderStroke(1.5.dp, if (running) Red.copy(alpha = 0.3f) else Red)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text("$ ", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                    Text(if (running) "kill scan" else "nmap -sT -T4 $host", fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
                }
            }
            if (running) { Spacer(Modifier.height(6.dp)); LinearProgressIndicator(progress = { scanned.toFloat() / maxOf(1, total) }, modifier = Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim); Text("$ scanned $scanned/$total  |  open: ${open.size}", fontFamily = Mono, fontSize = 11.sp, color = White) }
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(6.dp)); AsciiDivider()
                Text("$ nmap -sT $host", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    item {
                        TermPanel("Nmap 扫描报告: $host") {
                            if (open.isEmpty()) Text("  未发现开放端口", fontFamily = Mono, fontSize = 11.sp, color = Gray)
                            open.forEach { p ->
                                Text("${p.port}/tcp".padEnd(10) + p.state.padEnd(8) + p.service, fontFamily = Mono, fontSize = 11.sp, color = White)
                                if (p.banner.isNotEmpty()) Text("  |_ ${p.banner}", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                            }
                            Text("  Nmap done: $total ports scanned, ${open.size} open", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                        }
                    }
                }
            }
        }
    }
}
