package xin.ctkqiang.deauthctrl.ui.screens

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

private val R = Color(0xFFFF0000)
private val R2 = Color(0xFFCC0000)
private val R3 = Color(0xFF880000)
private val W = Color(0xFFEEEEEE)
private val G = Color(0xFF666666)
private val BG = Color(0xFF010101)
private val S = Color(0xFF080808)
private val D = Color(0xFF2A2A2A)
private val M = FontFamily.Monospace

@Composable
fun MainScreen(
    bleVm: BleSpamViewModel, wifiVm: WifiDisruptViewModel,
    btVm: BluetoothJammerViewModel, wjVm: WifiJammerViewModel,
) {
    var screen by remember { mutableStateOf("home") }
    var showBoot by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(BG)) {
        MatrixRainBackground(columnCount = 28, charAlpha = 0.07f)

        if (showBoot) {
            BootSequence(onComplete = { showBoot = false })
        } else {
            Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
                when (screen) {
                    "home" -> HomeScreen(bleVm, wifiVm, btVm, wjVm) { screen = it }
                    "ble" -> BleDetailScreen(bleVm) { screen = "home" }
                    "btjam" -> BtJamDetailScreen(btVm) { screen = "home" }
                    "wifi" -> WifiDetailScreen(wifiVm) { screen = "home" }
                    "wifijam" -> WifiJamDetailScreen(wjVm) { screen = "home" }
                    "about" -> AboutDetailScreen { screen = "home" }
                }
            }
        }

        ScanlineOverlay(lineSpacing = 2.dp, alpha = 0.06f, color = R)
    }
}


@Composable
fun LiveStatsBar() {
    var tick by remember { mutableIntStateOf(0) }
    val rng = remember { KRandom(7) }
    LaunchedEffect(Unit) {
        while (true) { delay(1800); tick++ }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0000))
            .border(1.dp, R.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("数据包:${(8000 + rng.nextInt(3000) + tick * 37).toString().padStart(4)}", fontFamily = M, fontSize = 9.sp, color = R.copy(alpha = 0.6f))
        Text("接口:wlan0", fontFamily = M, fontSize = 9.sp, color = R.copy(alpha = 0.6f))
        Text("CPU:${(8 + tick % 24)}%", fontFamily = M, fontSize = 9.sp, color = R.copy(alpha = 0.6f))
        Text("内存:${(300 + tick * 7 % 200)}MB", fontFamily = M, fontSize = 9.sp, color = R.copy(alpha = 0.6f))
    }
}

@Composable
fun HomeScreen(
    bleVm: BleSpamViewModel, wifiVm: WifiDisruptViewModel,
    btVm: BluetoothJammerViewModel, wjVm: WifiJammerViewModel,
    nav: (String) -> Unit,
) {
    val br by bleVm.isRunning.collectAsState(); val wr by wifiVm.isFlooding.collectAsState()
    val btr by btVm.isRunning.collectAsState(); val wjr by wjVm.isRunning.collectAsState()
    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(530); blink = !blink } }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        GlitchText(
            text = "DEAUTHCTRL",
            glitchIntervalMs = 3500,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "v1.0${if (blink) "_" else " "} | 哪吒网络安全",
            fontFamily = M, fontSize = 9.sp, color = G,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(6.dp))
        AsciiDivider()
        Spacer(Modifier.height(4.dp))

        LiveStatsBar()
        Spacer(Modifier.height(8.dp))

        FuncCard(
            nav = { nav("ble") }, name = "BLE 泛洪",
            desc = "BLE 广告协议泛洪攻击", running = br,
            onToggle = { if (br) bleVm.stopSpam() else bleVm.startSpam() },
        )
        FuncCard(
            nav = { nav("wifi") }, name = "WIFI 干扰",
            desc = "邪恶双子信标泛洪", running = wr,
            onToggle = { if (wr) wifiVm.stopFlood() else wifiVm.scanNetworks() },
        )
        FuncCard(
            nav = { nav("btjam") }, name = "蓝牙压制",
            desc = "全频段蓝牙攻击", running = btr,
            onToggle = { if (btr) btVm.stop() else btVm.start() },
        )
        FuncCard(
            nav = { nav("wifijam") }, name = "WIFI 压制",
            desc = "自动扫描并压制附近 SSID", running = wjr,
            onToggle = { if (wjr) wjVm.stop() else wjVm.start() },
        )

        Spacer(Modifier.height(6.dp))
        AsciiDivider()

        DataStream(modifier = Modifier.padding(vertical = 6.dp))
        AsciiDivider()

        Spacer(Modifier.weight(1f))

        Text(
            "中国红客  |  国产自主  |  安全可控",
            fontFamily = M, fontSize = 9.sp, color = G,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "[ ABOUT ]",
            fontFamily = M, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = R,
            modifier = Modifier.clickable { nav("about") }.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(4.dp))
    }
}


@Composable
fun FuncCard(
    nav: () -> Unit, name: String, desc: String,
    running: Boolean, onToggle: () -> Unit,
) {
    val borderColor = if (running) R.copy(alpha = 0.7f) else R.copy(alpha = 0.15f)
    val bg = if (running) Color(0xFF0A0000) else S

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { nav() }
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(2.dp),
        color = bg,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseDot(if (running) R else R.copy(alpha = 0.25f), size = 10.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    name,
                    fontFamily = M, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (running) R else W,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    modifier = Modifier.clickable { onToggle() },
                    shape = RoundedCornerShape(2.dp),
                    color = if (running) R.copy(alpha = 0.08f) else R,
                    border = BorderStroke(1.dp, if (running) R.copy(alpha = 0.3f) else R),
                ) {
                    Text(
                        if (running) "停止" else "启动",
                        fontFamily = M, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (running) R else Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "  $desc",
                fontFamily = M, fontSize = 10.sp, color = if (running) R.copy(alpha = 0.5f) else G,
            )
        }
    }
}


@Composable
fun DetailHeader(title: String, back: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color(0xFF0A0000))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "[ 返回 ]", fontFamily = M, fontSize = 11.sp,
                color = R, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { back() },
            )
            Spacer(Modifier.width(12.dp))
            Text(title, fontFamily = M, fontSize = 12.sp, color = W, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            PulseDot(R, size = 7.dp)
        }
        AsciiDivider()
    }
}


@Composable
fun BleDetailScreen(vm: BleSpamViewModel, back: () -> Unit) {
    val log by vm.log.collectAsState(); val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    var profile by remember { mutableStateOf(vm.getCurrentProfile()) }
    var interval by remember { mutableFloatStateOf(vm.getCurrentInterval().toFloat()) }
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize().background(BG)) {
        DetailHeader("BLE 泛洪引擎", back)
        if (running) DataStream()

        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Text("选择载荷:", fontFamily = M, fontSize = 10.sp, color = G)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                vm.getProfiles().forEach { p ->
                    val sel = p == profile
                    Surface(
                        modifier = Modifier.clickable { profile = p; vm.setProfile(p) },
                        shape = RoundedCornerShape(2.dp),
                        color = if (sel) R.copy(alpha = 0.15f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (sel) R else D),
                    ) {
                        Text(p.displayName, fontFamily = M, fontSize = 10.sp, color = if (sel) R else G, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            Text("间隔 ${interval.toLong()}ms", fontFamily = M, fontSize = 10.sp, color = G)
            Slider(
                value = interval, onValueChange = { interval = it; vm.setInterval(interval.toLong()) },
                valueRange = 20f..100f, steps = 7,
                colors = SliderDefaults.colors(thumbColor = R, activeTrackColor = R, inactiveTrackColor = D),
            )
            Spacer(Modifier.height(6.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().clickable { if (running) vm.stopSpam() else vm.startSpam() },
                shape = RoundedCornerShape(2.dp),
                color = if (running) Color.Transparent else R,
                border = BorderStroke(1.dp, R),
            ) {
                Text(
                    if (running) "中止" else "执行",
                    fontFamily = M, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (running) R else Color.White,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }

            error?.let { Text("! $it", fontFamily = M, fontSize = 10.sp, color = R) }
            if (running && log.isNotEmpty()) {
                val ok = log.count { it.success }
                Text("发送 $ok  |  ~${ok * 1000L / maxOf(1, System.currentTimeMillis() - log.first().timestamp)} 包/秒", fontFamily = M, fontSize = 10.sp, color = W)
            }

            Spacer(Modifier.height(4.dp))
            AsciiDivider()

            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (log.isEmpty()) item { Text("等待数据...", fontFamily = M, fontSize = 10.sp, color = G, modifier = Modifier.padding(vertical = 20.dp)) }
                items(log.reversed()) { e ->
                    Text(
                        "#${e.index.toString().padStart(4, '0')}  ${tf.format(Date(e.timestamp))}  ${e.profile.displayName.take(12).padEnd(12)}  ${if (e.success) "OK" else "FAIL"}",
                        fontFamily = M, fontSize = 9.sp, color = if (e.success) W else R,
                    )
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

    Column(Modifier.fillMaxSize().background(BG)) {
        DetailHeader("蓝牙压制引擎", back)
        if (running) DataStream()

        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("BLE泛洪: 开", fontFamily = M, fontSize = 9.sp, color = R)
                Text("蓝牙查询: 开", fontFamily = M, fontSize = 9.sp, color = R)
                Text("设备: $count", fontFamily = M, fontSize = 9.sp, color = W)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { if (running) vm.stop() else vm.start() },
                shape = RoundedCornerShape(2.dp),
                color = if (running) Color.Transparent else R,
                border = BorderStroke(1.dp, R),
            ) {
                Text(
                    if (running) "中止压制" else "执行压制",
                    fontFamily = M, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (running) R else Color.White,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }
            error?.let { Text("! $it", fontFamily = M, fontSize = 10.sp, color = R) }
            if (running) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = R, trackColor = D)
            }
            if (devices.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("发现的设备", fontFamily = M, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = R)
                LazyColumn(Modifier.fillMaxWidth().height(100.dp)) {
                    items(devices.take(8)) { d ->
                        Text("  ${d.name.take(24).padEnd(24)}  ${d.address}  [${d.type}]", fontFamily = M, fontSize = 9.sp, color = W)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            AsciiDivider()
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(log.takeLast(100).reversed()) { e ->
                    Text("[${e.module}] ${tf.format(Date(e.timestamp))} ${e.message}", fontFamily = M, fontSize = 9.sp, color = W)
                }
            }
        }
    }
}


@Composable
fun WifiDetailScreen(vm: WifiDisruptViewModel, back: () -> Unit) {
    val ss by vm.scanState.collectAsState(); val hr by vm.hotspotResult.collectAsState()
    val fl by vm.isFlooding.collectAsState()
    var sel by remember { mutableStateOf<WifiNetwork?>(null) }
    var dur by remember { mutableIntStateOf(vm.getDuration()) }

    Column(Modifier.fillMaxSize().background(BG)) {
        DetailHeader("WIFI 干扰引擎", back)
        if (fl) DataStream()

        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { vm.scanNetworks() },
                shape = RoundedCornerShape(2.dp), color = R,
            ) {
                Text("扫描网络", fontFamily = M, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(4.dp))

            when (val s = ss) {
                is ScanState.Scanning -> Text("扫描 2.4/5GHz...", fontFamily = M, fontSize = 10.sp, color = W)
                is ScanState.Results -> {
                    Text("发现 ${s.networks.size} 个网络 -- 选择目标:", fontFamily = M, fontSize = 10.sp, color = G)
                    LazyColumn(Modifier.fillMaxWidth().weight(0.5f)) {
                        items(s.networks, key = { it.bssid }) { n ->
                            val on = sel?.bssid == n.bssid
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { sel = n; vm.selectTarget(n) },
                                color = if (on) R.copy(alpha = 0.08f) else Color.Transparent,
                            ) {
                                Row(Modifier.padding(vertical = 3.dp, horizontal = 4.dp)) {
                                    Text(if (on) ">" else " ", fontFamily = M, fontSize = 12.sp, color = R, modifier = Modifier.width(14.dp))
                                    Text(n.ssid.ifBlank { "<hidden>" }.take(20).padEnd(20), fontFamily = M, fontSize = 12.sp, color = if (on) W else G, modifier = Modifier.weight(1f))
                                    Text("CH${n.channel.toString().padStart(2)} ${n.signalStrength}dBm ${n.security}", fontFamily = M, fontSize = 9.sp, color = G)
                                }
                            }
                        }
                    }
                }
                is ScanState.Error -> Text(s.message, fontFamily = M, fontSize = 10.sp, color = R)
                else -> {}
            }

            if (sel != null) {
                Text("目标: ${sel!!.ssid}  |  duration: ${dur}s", fontFamily = M, fontSize = 11.sp, color = W)
                Slider(value = dur.toFloat(), onValueChange = { dur = it.toInt().coerceIn(1, 60) }, valueRange = 1f..60f, steps = 14, colors = SliderDefaults.colors(thumbColor = R, activeTrackColor = R, inactiveTrackColor = D))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { vm.setDuration(dur); if (fl) vm.stopFlood() else vm.startFlood() },
                    shape = RoundedCornerShape(2.dp),
                    color = if (fl) Color.Transparent else R,
                    border = BorderStroke(1.dp, R),
                ) {
                    Text(if (fl) "中止泛洪" else "执行泛洪", fontFamily = M, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (fl) R else Color.White, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
                }
            }

            when (val r = hr) {
                is HotspotResult.Progress -> { LinearProgressIndicator(progress = { r.cycle.toFloat() / r.totalCycles }, modifier = Modifier.fillMaxWidth(), color = R, trackColor = D); Text("cycle ${r.cycle}/${r.totalCycles}", fontFamily = M, fontSize = 9.sp, color = W) }
                is HotspotResult.Completed -> Text("泛洪完成", fontFamily = M, fontSize = 10.sp, color = R)
                is HotspotResult.Error -> Text(r.message, fontFamily = M, fontSize = 10.sp, color = R)
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

    Column(Modifier.fillMaxSize().background(BG)) {
        DetailHeader("WIFI 压制引擎", back)
        if (running) DataStream()

        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Text("切换间隔 ${ms.toLong()}ms", fontFamily = M, fontSize = 10.sp, color = G)
            Slider(value = ms, onValueChange = { ms = it; vm.setInterval(ms.toLong()) }, valueRange = 60f..300f, steps = 11, colors = SliderDefaults.colors(thumbColor = R, activeTrackColor = R, inactiveTrackColor = D))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { if (running) vm.stop() else vm.start() },
                shape = RoundedCornerShape(2.dp),
                color = if (running) Color.Transparent else R,
                border = BorderStroke(1.dp, R),
            ) {
                Text(if (running) "中止压制" else "执行压制", fontFamily = M, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (running) R else Color.White, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
            }
            error?.let { Text("! $it", fontFamily = M, fontSize = 10.sp, color = R) }
            if (running) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = R, trackColor = D); Text("目标 $tc  |  beacon ${vm.getBeaconCount()}  |  cycles ${vm.getCycleCount()}", fontFamily = M, fontSize = 10.sp, color = W) }
            when (val s = ss) {
                is JammerScanState.Scanning -> Text("扫描中...", fontFamily = M, fontSize = 10.sp, color = W)
                is JammerScanState.Results -> {
                    Text("压制 ${s.networks.size} 个SSID:", fontFamily = M, fontSize = 10.sp, color = R, fontWeight = FontWeight.Bold)
                    LazyColumn(Modifier.fillMaxWidth().weight(0.4f)) { items(s.networks.take(12)) { n -> Text("  ${n.ssid.ifBlank { "<hidden>" }.take(26).padEnd(26)}  CH${n.channel.toString().padStart(2)}  ${n.signalStrength}dBm  ${n.security}", fontFamily = M, fontSize = 9.sp, color = W) } }
                }
                is JammerScanState.Error -> Text(s.message, fontFamily = M, fontSize = 10.sp, color = R)
                else -> {}
            }
            Spacer(Modifier.height(2.dp))
            AsciiDivider()
            LazyColumn(Modifier.fillMaxWidth().weight(0.5f)) { items(log.takeLast(80).reversed()) { e -> Text("[${e.module}] ${tf.format(Date(e.timestamp))} ${e.message}", fontFamily = M, fontSize = 9.sp, color = W) } }
        }
    }
}


@Composable
fun AboutDetailScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BG)) {
        DetailHeader("关于", back)
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                """
╔══════════════════════════════╗
║  ██████╗ ███████╗ █████╗    ║
║  ██╔══██╗██╔═══╝██╔══██╗   ║
║  ██║  ██║█████╗ ███████║   ║
║  ██║  ██║██╔══╝ ██╔══██║   ║
║  ██████╔╝██████╗██║  ██║   ║
║  ╚═════╝ ╚═════╝╚═╝  ╚═╝   ║
╚══════════════════════════════╝""".trimIndent(),
                fontFamily = M, fontSize = 8.sp, color = R,
            )
            Spacer(Modifier.height(14.dp))
            Text("作者   钟智强 (ctkqiang)", fontFamily = M, fontSize = 12.sp, color = W)
            Text("代号    哪吒网络安全", fontFamily = M, fontSize = 12.sp, color = W)
            Text("邮箱    ctkqiang@dingtalk.com", fontFamily = M, fontSize = 12.sp, color = W)
            Text("仓库     gitcode.com/ctkqiang_sr/DeauthCtrl", fontFamily = M, fontSize = 12.sp, color = W)
            Text("架构     MVVM + StateFlow + Jetpack Compose", fontFamily = M, fontSize = 12.sp, color = W)
            Spacer(Modifier.height(12.dp))
            Text("中国红客  |  哪吒网络安全  |  国产自主  |  安全可控", fontFamily = M, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = R)
        }
    }
}
