package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.manager.*
import xin.ctkqiang.deauthctrl.model.*
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

@Composable
fun MainScreen(
    bleVm: BleSpamViewModel, wifiVm: WifiDisruptViewModel,
    btVm: BluetoothJammerViewModel, wjVm: WifiJammerViewModel,
) {
    var screen by remember { mutableStateOf("home") }
    var boot by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Dark)) {
        if (boot) {
            BootSequence(onComplete = { boot = false })
        } else {
            AnimatedContent(targetState = screen, transitionSpec = {
                (fadeIn(animationSpec = tween(200)) + slideInHorizontally(animationSpec = tween(250)) { it / 4 })
                    .togetherWith(fadeOut(animationSpec = tween(150)) + slideOutHorizontally(animationSpec = tween(200)) { -it / 4 })
            }) { current ->
                when (current) {
                    "home" -> HomeScreen(bleVm, wifiVm, btVm, wjVm, nav = { screen = it })
                    "ble" -> BleDetailScreen(bleVm, back = { screen = "home" })
                    "btjam" -> BtJamDetailScreen(btVm, back = { screen = "home" })
                    "wifi" -> WifiDetailScreen(wifiVm, back = { screen = "home" })
                    "wifijam" -> WifiJamDetailScreen(wjVm, back = { screen = "home" })
                    "about" -> AboutDetailScreen(back = { screen = "home" })
                }
            }
        }
        if (!boot) ScanlineOverlay(lineSpacing = 3.dp, alpha = 0.035f)
    }
}

@Composable
fun StatusBar() {
    var tick by remember { mutableIntStateOf(0) }
    val rng = remember { KRandom(7) }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }

    Row(
        Modifier.fillMaxWidth().background(Color(0xFF060606)).border(0.5.dp, BorderDim).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat("数据包", (9000 + rng.nextInt(2000) + tick * 41).toString().padStart(4))
        Stat("接口", "wlan0")
        Stat("CPU", "${10 + tick % 22}%")
        Stat("内存", "${320 + tick * 11 % 180}MB")
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", fontFamily = Mono, fontSize = 9.sp, color = Gray)
        Text(value, fontFamily = Mono, fontSize = 9.sp, color = RedDim)
    }
}

@Composable
fun HomeScreen(
    bleVm: BleSpamViewModel, wifiVm: WifiDisruptViewModel,
    btVm: BluetoothJammerViewModel, wjVm: WifiJammerViewModel,
    nav: (String) -> Unit,
) {
    val br by bleVm.isRunning.collectAsState()
    val wr by wifiVm.isFlooding.collectAsState()
    val btr by btVm.isRunning.collectAsState()
    val wjr by wjVm.isRunning.collectAsState()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
        GlitchText("DEAUTHCTRL", style = TextStyle(fontSize = 22.sp), modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("v1.0  |  哪吒网络安全  |  fsociety", fontFamily = Mono, fontSize = 9.sp, color = Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
        StatusBar()
        Spacer(Modifier.height(12.dp))

        AnimatedCard("BLE 泛洪", "BLE 广告协议泛洪攻击", running = br, delayMs = 0,  onClick = { nav("ble") }, onToggle = { if (br) bleVm.stopSpam() else bleVm.startSpam() })
        AnimatedCard("WIFI 干扰", "邪恶双子信标泛洪攻击", running = wr, delayMs = 60, onClick = { nav("wifi") }, onToggle = { if (wr) wifiVm.stopFlood() else wifiVm.scanNetworks() })
        AnimatedCard("蓝牙压制", "全频段蓝牙设备压制攻击", running = btr, delayMs = 120, onClick = { nav("btjam") }, onToggle = { if (btr) btVm.stop() else btVm.start() })
        AnimatedCard("WIFI 压制", "自动扫描并压制附近所有 SSID", running = wjr, delayMs = 180, onClick = { nav("wifijam") }, onToggle = { if (wjr) wjVm.stop() else wjVm.start() })

        AsciiDivider(modifier = Modifier.padding(vertical = 8.dp))

        Spacer(Modifier.weight(1f))
        Text("中国红客  |  国产自主  |  安全可控", fontFamily = Mono, fontSize = 9.sp, color = Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(4.dp))
        Text("[ 关于 ]", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedDim, modifier = Modifier.clickable { nav("about") }.align(Alignment.CenterHorizontally))
    }
}

@Composable
fun AnimatedCard(name: String, desc: String, running: Boolean, delayMs: Long, onClick: () -> Unit, onToggle: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f))

    LaunchedEffect(Unit) { delay(delayMs); visible = true }

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)) + slideInVertically(tween(350)) { it / 3 }) {
        val accent by animateColorAsState(if (running) Red.copy(alpha = 0.65f) else BorderDim, tween(300))
        val bg by animateColorAsState(if (running) Red.copy(alpha = 0.04f) else SurfaceBg, tween(300))
        val nameColor by animateColorAsState(if (running) Red else White, tween(300))
        val descColor by animateColorAsState(if (running) Red.copy(alpha = 0.45f) else Gray, tween(300))
        val dotColor by animateColorAsState(if (running) Red else RedDim.copy(alpha = 0.4f), tween(300))

        Surface(
            modifier = Modifier.fillMaxWidth().scale(scale).clickable { onClick() }.padding(vertical = 3.dp),
            shape = RoundedCornerShape(3.dp), color = bg, border = BorderStroke(1.dp, accent),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(dotColor))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = nameColor)
                    Text(desc, fontFamily = Mono, fontSize = 10.sp, color = descColor)
                }
                Surface(
                    modifier = Modifier.clickable { pressed = true; onToggle(); pressed = false },
                    shape = RoundedCornerShape(2.dp),
                    color = if (running) Color.Transparent else Red,
                    border = BorderStroke(1.dp, if (running) Red.copy(alpha = 0.25f) else Red),
                ) {
                    Text(if (running) "停止" else "启动", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp))
                }
            }
        }
    }
}

@Composable
fun TerminalHeader(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().background(SurfaceBg).border(0.5.dp, BorderDim).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("[ 返回 ]", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.clickable { back() })
        Spacer(Modifier.width(10.dp))
        Text(title, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White)
        Spacer(Modifier.weight(1f))
        Text("root@deauth:~#", fontFamily = Mono, fontSize = 9.sp, color = Gray)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedDim, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
fun BleDetailScreen(vm: BleSpamViewModel, back: () -> Unit) {
    val log by vm.log.collectAsState(); val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    var profile by remember { mutableStateOf(vm.getCurrentProfile()) }
    var interval by remember { mutableFloatStateOf(vm.getCurrentInterval().toFloat()) }
    val list = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) list.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("BLE 泛洪引擎", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            SectionLabel("载荷配置")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                vm.getProfiles().forEach { p ->
                    val sel = p == profile
                    Surface(modifier = Modifier.clickable { profile = p; vm.setProfile(p) }, shape = RoundedCornerShape(2.dp), color = if (sel) Red.copy(alpha = 0.12f) else Color.Transparent, border = BorderStroke(1.dp, if (sel) Red.copy(alpha = 0.5f) else BorderDim)) {
                        Text(p.displayName, fontFamily = Mono, fontSize = 10.sp, color = if (sel) Red else Gray, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("间隔 ", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                Text("${interval.toLong()}ms", fontFamily = Mono, fontSize = 10.sp, color = White)
            }
            Slider(interval, { interval = it; vm.setInterval(interval.toLong()) }, valueRange = 20f..100f, steps = 7, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = BorderDim))

            Spacer(Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable { if (running) vm.stopSpam() else vm.startSpam() }, shape = RoundedCornerShape(2.dp), color = if (running) Color.Transparent else Red, border = BorderStroke(1.dp, if (running) Red.copy(alpha = 0.3f) else Red)) {
                Text(if (running) "中止攻击" else "执行攻击", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
            }
            error?.let { Text("! $it", fontFamily = Mono, fontSize = 10.sp, color = Red) }
            if (running && log.isNotEmpty()) {
                val ok = log.count { it.success }
                Text("已发送 $ok 包  |  ~${ok * 1000L / maxOf(1, System.currentTimeMillis() - log.first().timestamp)} pps", fontFamily = Mono, fontSize = 10.sp, color = White)
            }
            AnimatedVisibility(running && log.isNotEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim) }

            Spacer(Modifier.height(6.dp))
            AsciiDivider()
            SectionLabel("数据包日志")
            LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (log.isEmpty()) item { Text("等待数据...", fontFamily = Mono, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(vertical = 16.dp)) }
                items(log.reversed()) { e ->
                    Text("#${e.index.toString().padStart(4, '0')}  ${tf.format(Date(e.timestamp))}  ${e.profile.displayName.take(12).padEnd(12)}  ${if (e.success) "OK" else "FAIL"}", fontFamily = Mono, fontSize = 9.sp, color = if (e.success) White else Red)
                }
            }
        }
    }
}

@Composable
fun BtJamDetailScreen(vm: BluetoothJammerViewModel, back: () -> Unit) {
    val log by vm.log.collectAsState(); val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val devices by vm.discoveredDevices.collectAsState(); val count by vm.discoveryCount.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("蓝牙压制引擎", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatusChip("BLE 泛洪", true)
                StatusChip("设备发现", true)
                StatusChip("已发现", "$count 台")
            }
            Spacer(Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable { if (running) vm.stop() else vm.start() }, shape = RoundedCornerShape(2.dp), color = if (running) Color.Transparent else Red, border = BorderStroke(1.dp, if (running) Red.copy(alpha = 0.3f) else Red)) {
                Text(if (running) "中止压制" else "执行压制", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
            }
            error?.let { Text("! $it", fontFamily = Mono, fontSize = 10.sp, color = Red) }
            AnimatedVisibility(running) { Column { Spacer(Modifier.height(6.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim) } }
            AnimatedVisibility(devices.isNotEmpty()) {
                Column {
                    SectionLabel("附近设备")
                    LazyColumn(Modifier.fillMaxWidth().height(120.dp)) { items(devices.take(10)) { d -> Text("  ${d.name.take(28).padEnd(28)} ${d.address}  [${d.type}]", fontFamily = Mono, fontSize = 9.sp, color = White) } }
                }
            }
            Spacer(Modifier.height(6.dp))
            AsciiDivider()
            SectionLabel("运行日志")
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) { items(log.takeLast(100).reversed()) { e -> Text("[${e.module}] ${tf.format(Date(e.timestamp))} ${e.message}", fontFamily = Mono, fontSize = 9.sp, color = White) } }
        }
    }
}

@Composable
fun StatusChip(label: String, active: Boolean) = Text("$label: ${if (active) "开" else "关"}", fontFamily = Mono, fontSize = 9.sp, color = if (active) Red else Gray)

@Composable
fun StatusChip(label: String, value: String) = Text("$label: $value", fontFamily = Mono, fontSize = 9.sp, color = White)

@Composable
fun WifiDetailScreen(vm: WifiDisruptViewModel, back: () -> Unit) {
    val ss by vm.scanState.collectAsState(); val hr by vm.hotspotResult.collectAsState(); val fl by vm.isFlooding.collectAsState()
    var sel by remember { mutableStateOf<WifiNetwork?>(null) }
    var dur by remember { mutableIntStateOf(vm.getDuration()) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WIFI 干扰引擎", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            Surface(modifier = Modifier.fillMaxWidth().clickable { vm.scanNetworks() }, shape = RoundedCornerShape(2.dp), color = Red) {
                Text("扫描附近网络", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(6.dp))

            when (val s = ss) {
                is ScanState.Scanning -> Text("正在扫描 2.4/5GHz...", fontFamily = Mono, fontSize = 10.sp, color = White)
                is ScanState.Results -> {
                    SectionLabel("发现 ${s.networks.size} 个网络 — 选择目标")
                    LazyColumn(Modifier.fillMaxWidth().weight(0.45f)) {
                        items(s.networks, key = { it.bssid }) { n ->
                            val on = sel?.bssid == n.bssid
                            Surface(modifier = Modifier.fillMaxWidth().clickable { sel = n; vm.selectTarget(n) }, color = if (on) Red.copy(alpha = 0.06f) else Color.Transparent) {
                                Row(Modifier.padding(vertical = 3.dp, horizontal = 4.dp)) {
                                    Text(if (on) "▸" else " ", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.width(16.dp))
                                    Text(n.ssid.ifBlank { "<隐藏>" }.take(22).padEnd(22), fontFamily = Mono, fontSize = 11.sp, color = if (on) White else Gray, modifier = Modifier.weight(1f))
                                    Text("CH${n.channel.toString().padStart(2)} ${n.signalStrength}dBm", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                                }
                            }
                        }
                    }
                }
                is ScanState.Error -> Text(s.message, fontFamily = Mono, fontSize = 10.sp, color = Red)
                else -> {}
            }

            AnimatedVisibility(sel != null) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    Text("目标: ${sel!!.ssid}  |  持续 ${dur}s", fontFamily = Mono, fontSize = 11.sp, color = White)
                    Slider(dur.toFloat(), { dur = it.toInt().coerceIn(1, 60) }, valueRange = 1f..60f, steps = 14, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = BorderDim))
                    Surface(modifier = Modifier.fillMaxWidth().clickable { vm.setDuration(dur); if (fl) vm.stopFlood() else vm.startFlood() }, shape = RoundedCornerShape(2.dp), color = if (fl) Color.Transparent else Red, border = BorderStroke(1.dp, if (fl) Red.copy(alpha = 0.3f) else Red)) {
                        Text(if (fl) "中止泛洪" else "开始泛洪", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (fl) Red else White, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
                    }
                }
            }

            when (val r = hr) {
                is HotspotResult.Progress -> { LinearProgressIndicator(progress = { r.cycle.toFloat() / r.totalCycles }, modifier = Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim); Text("周期 ${r.cycle}/${r.totalCycles}", fontFamily = Mono, fontSize = 9.sp, color = White) }
                is HotspotResult.Completed -> Text("泛洪完成", fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Red)
                is HotspotResult.Error -> Text(r.message, fontFamily = Mono, fontSize = 10.sp, color = Red)
                else -> {}
            }
        }
    }
}

@Composable
fun WifiJamDetailScreen(vm: WifiJammerViewModel, back: () -> Unit) {
    val log by vm.log.collectAsState(); val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val ss by vm.scanState.collectAsState(); val tc by vm.targetCount.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    var ms by remember { mutableFloatStateOf(vm.getInterval().toFloat()) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WIFI 压制引擎", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("信道切换间隔 ", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                Text("${ms.toLong()}ms", fontFamily = Mono, fontSize = 10.sp, color = White)
            }
            Slider(ms, { ms = it; vm.setInterval(ms.toLong()) }, valueRange = 60f..300f, steps = 11, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = BorderDim))

            Surface(modifier = Modifier.fillMaxWidth().clickable { if (running) vm.stop() else vm.start() }, shape = RoundedCornerShape(2.dp), color = if (running) Color.Transparent else Red, border = BorderStroke(1.dp, if (running) Red.copy(alpha = 0.3f) else Red)) {
                Text(if (running) "中止压制" else "执行压制", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
            }
            error?.let { Text("! $it", fontFamily = Mono, fontSize = 10.sp, color = Red) }
            AnimatedVisibility(running) {
                Column { Spacer(Modifier.height(6.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = BorderDim); Text("压制 $tc 个SSID  |  beacon ${vm.getBeaconCount()}  |  周期 ${vm.getCycleCount()}", fontFamily = Mono, fontSize = 10.sp, color = White) }
            }

            when (val s = ss) {
                is JammerScanState.Scanning -> Text("扫描中...", fontFamily = Mono, fontSize = 10.sp, color = White)
                is JammerScanState.Results -> {
                    SectionLabel("目标网络 (${s.networks.size})")
                    LazyColumn(Modifier.fillMaxWidth().weight(0.4f)) { items(s.networks.take(12)) { n -> Text("  ${n.ssid.ifBlank { "<隐藏>" }.take(28).padEnd(28)} CH${n.channel.toString().padStart(2)} ${n.signalStrength}dBm", fontFamily = Mono, fontSize = 9.sp, color = White) } }
                }
                is JammerScanState.Error -> Text(s.message, fontFamily = Mono, fontSize = 10.sp, color = Red)
                else -> {}
            }
            Spacer(Modifier.height(6.dp))
            AsciiDivider()
            SectionLabel("运行日志")
            LazyColumn(Modifier.fillMaxWidth().weight(0.5f)) { items(log.takeLast(80).reversed()) { e -> Text("[${e.module}] ${tf.format(Date(e.timestamp))} ${e.message}", fontFamily = Mono, fontSize = 9.sp, color = White) } }
        }
    }
}

@Composable
fun AboutDetailScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("关于", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(14.dp)) {
            Text("DEAUTHCTRL", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Red)
            Text("去认证控制系统 v1.0", fontFamily = Mono, fontSize = 11.sp, color = Gray)
            Spacer(Modifier.height(14.dp))
            AsciiDivider()
            Spacer(Modifier.height(14.dp))

            InfoRow("作者", "钟智强 (ctkqiang)")
            InfoRow("代号", "哪吒网络安全")
            InfoRow("邮箱", "ctkqiang@dingtalk.com")
            InfoRow("仓库", "gitcode.com/ctkqiang_sr/DeauthCtrl")
            InfoRow("架构", "MVVM + StateFlow + Jetpack Compose")
            InfoRow("许可", "仅供授权安全研究使用")

            Spacer(Modifier.height(14.dp))
            AsciiDivider()
            Spacer(Modifier.height(10.dp))
            Text("中国红客  |  哪吒网络安全  |  国产自主  |  安全可控", fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label  ", fontFamily = Mono, fontSize = 11.sp, color = Gray, modifier = Modifier.width(60.dp))
        Text(value, fontFamily = Mono, fontSize = 11.sp, color = White)
    }
}
