package xin.ctkqiang.deauthctrl.ui.screens

import android.app.Application
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.random.Random
import kotlin.math.abs
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import xin.ctkqiang.deauthctrl.manager.HttpClientManager
import xin.ctkqiang.deauthctrl.manager.JammerScanState
import xin.ctkqiang.deauthctrl.manager.WebServerManager
import xin.ctkqiang.deauthctrl.model.HotspotResult
import xin.ctkqiang.deauthctrl.model.ScanState
import xin.ctkqiang.deauthctrl.model.WifiNetwork
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.AsciiDivider
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.LogHeader
import xin.ctkqiang.deauthctrl.ui.components.LogLine
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.PanelStat
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.RedDim
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.StarBadge
import xin.ctkqiang.deauthctrl.ui.components.StatChip
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TacGreenDim
import xin.ctkqiang.deauthctrl.ui.components.TacticalCorners
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.drawTacticalCorners
import xin.ctkqiang.deauthctrl.ui.components.proximityVibrate
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.ArpScanViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BleScannerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BleSpamViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BluetoothJammerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.CVESearchViewModel
import xin.ctkqiang.deauthctrl.viewmodel.DirBruteViewModel
import xin.ctkqiang.deauthctrl.viewmodel.FileTransferViewModel
import xin.ctkqiang.deauthctrl.viewmodel.HttpClientViewModel
import xin.ctkqiang.deauthctrl.viewmodel.PayloadViewModel
import xin.ctkqiang.deauthctrl.viewmodel.PingViewModel
import xin.ctkqiang.deauthctrl.viewmodel.PortScannerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.RadarViewModel
import xin.ctkqiang.deauthctrl.viewmodel.RevShellViewModel
import xin.ctkqiang.deauthctrl.viewmodel.SecureMediaViewModel
import xin.ctkqiang.deauthctrl.viewmodel.SettingsViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WalkieTalkieViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WebServerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WifiDisruptViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WifiJammerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 详情页内容整体入场动画 —— 每页进入时轻微淡入上滑
 *
 * [locked] 为 true 时页面四角显示全息青呼吸锁定框：
 * 任务执行中的页面 = 被全息 HUD 锁定的目标。
 */
@Composable
private fun PageEnter(locked: Boolean = false, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(visible = shown, enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 8 }) {
        Box {
            content()
            if (locked) {
                val lock = rememberInfiniteTransition()
                val lockAlpha by lock.animateFloat(0.3f, 0.9f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse))
                TacticalCorners(TacGreen.copy(alpha = lockAlpha), Modifier.matchParentSize(), len = 12.dp, stroke = 1.6.dp)
            }
        }
    }
}

// ─── 详情页通用动效 ──────────────────────────────────────────────────────────

/**
 * 错误提示条
 *
 * 错误出现时横向抖动 0.4 秒：失败信号不该安静地躺在页面里。
 * 位移只写在绘制层，不触发布局或重组。
 */
@Composable
private fun ErrorBanner(msg: String) {
    val shake = remember { Animatable(0f) }
    val amplitude = with(LocalDensity.current) { 6.dp.toPx() }
    LaunchedEffect(msg) {
        shake.snapTo(0f)
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 400
                0f at 0
                (-amplitude) at 45
                amplitude at 90
                (-amplitude * 0.8f) at 140
                amplitude * 0.55f at 195
                (-amplitude * 0.3f) at 255
                amplitude * 0.15f at 315
            },
        )
    }
    Text(
        "[!] $msg",
        fontFamily = Sans,
        fontSize = 11.sp,
        color = Danger,
        modifier = Modifier.padding(top = 4.dp).graphicsLayer { translationX = shake.value },
    )
}

/**
 * 实时统计行 —— 数值变化时淡入切换
 *
 * 与 [PanelStat] 版式一致，仅用于运行中高频刷新的统计面板；
 * 静态信息面板继续用 [PanelStat]，不为一次性数据付动画成本。
 */
@Composable
private fun LiveStat(label: String, value: String, valueColor: Color = White) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = Sans, fontSize = 11.sp, color = Gray)
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "live_stat_value",
        ) { v ->
            Text(v, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

/**
 * 呼吸空状态提示 —— 等待态也有生命感
 *
 * alpha 在 0.85..1 之间 1.6 秒往复；值读取发生在绘制层，不引起重组。
 */
@Composable
private fun HintBreathing(text: String) {
    val breath = rememberInfiniteTransition(label = "empty_hint_breath")
    val alpha = breath.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "empty_hint_alpha",
    )
    Box(Modifier.graphicsLayer { this.alpha = alpha.value }) {
        EmptyHint(text)
    }
}

/** 进度条未激活段底色 —— 深海军蓝 */
private val Track = Color(0xFF0E1B2E)

/** 日志渲染上限，与内核环形缓冲配合，避免一次性渲染全部条目 */
private const val LOG_TAIL = 60

// ══════════════════════════════════════════════════════════════════════════
// BLE 泛洪引擎
// ══════════════════════════════════════════════════════════════════════════

/**
 * BLE 泛洪攻击详情页
 *
 * 功能区块：
 * - 广播载荷：选择伪造的协议 Profile + 循环模式
 * - 发送参数：功率档位、发送间隔、最大发包数
 * - 主操作按钮 + 运行统计 + 实时日志
 */
@Composable
fun BleDetailScreen(vm: BleSpamViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    var profile by remember { mutableStateOf(vm.getCurrentProfile()) }
    var interval by remember { mutableFloatStateOf(vm.getCurrentInterval().toFloat()) }
    var txPower by remember { mutableStateOf(vm.getCurrentTxPower()) }
    var cycleMode by remember { mutableStateOf(vm.getCurrentCycleMode()) }
    var maxPacketsText by remember {
        val cur = vm.getCurrentMaxPackets()
        mutableStateOf(if (cur < 0) "" else cur.toString())
    }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("BLE 泛洪引擎", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                TermPanel("广播载荷") {
                    Text("伪造协议", fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(bottom = 5.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.getProfiles().forEach { p ->
                            SelectChip(selected = p == profile, text = p.displayName) {
                                profile = p
                                vm.setProfile(p)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("循环模式", fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(bottom = 5.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.getCycleModes().forEach { m ->
                            SelectChip(selected = m == cycleMode, text = m.displayName) {
                                cycleMode = m
                                vm.setCycleMode(m)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                TermPanel("发送参数") {
                    Text("功率档位", fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(bottom = 5.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.getTxPowerLevels().forEach { p ->
                            SelectChip(selected = p == txPower, text = p.displayName) {
                                txPower = p
                                vm.setTxPower(p)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("发送间隔", fontFamily = Sans, fontSize = 11.sp, color = Gray)
                        Text("${interval.toLong()}ms", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                    }
                    Slider(interval, { interval = it; vm.setInterval(interval.toLong()) }, valueRange = 20f..100f, steps = 7, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = Track))
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TermInput("最大发包数", maxPacketsText, placeholder = "留空表示不限", modifier = Modifier.weight(1f)) { v ->
                            maxPacketsText = v.filter { it.isDigit() }
                            vm.setMaxPackets(maxPacketsText.toLongOrNull() ?: -1L)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("（留空=不限）", fontFamily = Sans, fontSize = 10.sp, color = Gray)
                    }
                }

                Spacer(Modifier.height(8.dp))

                ActionButton(running, "执行泛洪", "停止泛洪") {
                    haptic.hud()
                    if (running) vm.stopSpam() else vm.startSpam()
                }
                error?.let { ErrorBanner(it) }
                AnimatedVisibility(running) {
                    Column { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track) }
                }

                // 统计与日志各自独立订阅高频状态：发包期间每 20-100ms 刷新一次，
                // 隔离后只有这两个面板重组，配置区与按钮不受影响
                BleStatsPanel(vm)
                BleLogPanel(vm)
            }
        }
    }
}

/** BLE 运行统计面板 —— 独立订阅 stats，高频刷新只重组自身 */
@Composable
private fun BleStatsPanel(vm: BleSpamViewModel) {
    val stats by vm.stats.collectAsState()
    Spacer(Modifier.height(8.dp))
    TermPanel("运行统计") {
        val upSec = stats.uptimeMs / 1000
        LiveStat("已发送", "${stats.sent}")
        LiveStat("失败", "${stats.failed}", if (stats.failed > 0) Danger else White)
        LiveStat("总尝试", "${stats.total}")
        LiveStat("成功率", "%.1f%%".format(stats.successRate * 100), if (stats.successRate > 0.5f) White else Danger)
        LiveStat("发包速率", "%.1f pps".format(stats.pps))
        LiveStat("运行时长", "%02d:%02d".format(upSec / 60, upSec % 60))
        if (stats.failRateRecent > 0.3f) {
            Spacer(Modifier.height(4.dp))
            Text("[!] 近期失败率 %.0f%%，建议增大发送间隔".format(stats.failRateRecent * 100), fontFamily = Mono, fontSize = 10.sp, color = Danger)
        }
    }
}

/** BLE 发包日志面板 —— 独立订阅 log，高频追加只重组自身；滚动用非动画定位避免堆积 */
@Composable
private fun BleLogPanel(vm: BleSpamViewModel) {
    val log by vm.log.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val list = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) list.scrollToItem(0) }
    Spacer(Modifier.height(10.dp))
    LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
    Spacer(Modifier.height(4.dp))
    LazyColumn(state = list, modifier = Modifier.fillMaxWidth().height(200.dp)) {
        if (log.isEmpty()) item { HintBreathing("启动泛洪后，发包记录会实时显示在这里") }
        items(log.asReversed()) { e ->
            LogLine(
                tf.format(Date(e.timestamp)),
                "${e.profile.displayName}  ${if (e.success) "成功" else "失败"}  #${e.index.toString().padStart(5, '0')}",
                if (e.success) White else Danger,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 蓝牙压制引擎
// ══════════════════════════════════════════════════════════════════════════

/**
 * 蓝牙压制攻击详情页
 *
 * 同时运行 BLE 泛洪 + 经典蓝牙设备发现。
 * 功能区块：状态面板 / 主操作按钮 / 运行统计 / 附近设备 / 实时日志。
 */
@Composable
fun BtJamDetailScreen(vm: BluetoothJammerViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("蓝牙压制引擎", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                TermPanel("状态") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatChip("BLE 泛洪", if (running) "运行中" else "已停止", running)
                        StatChip("设备发现", if (running) "运行中" else "已停止", running)
                    }
                }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, "执行压制", "停止压制") {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                error?.let { ErrorBanner(it) }
                AnimatedVisibility(running) {
                    Column { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track) }
                }

                // 高频数据区独立订阅：设备发现与统计每秒多次刷新，隔离后只重组这两个面板
                BtJamLivePanel(vm)
                BtJamLogPanel(vm)
            }
        }
    }
}

/** 蓝牙压制实时面板 —— 独立订阅设备与统计 */
@Composable
private fun BtJamLivePanel(vm: BluetoothJammerViewModel) {
    val devices by vm.discoveredDevices.collectAsState()
    val count by vm.discoveryCount.collectAsState()
    val stats by vm.stats.collectAsState()
    Column {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().background(SurfaceBg, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)).border(1.dp, BorderDim, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)).padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("已发现设备", fontFamily = Sans, fontSize = 10.sp, color = Gray)
            Spacer(Modifier.width(6.dp))
            Text("$count 台", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
        }
        if (stats.blePackets > 0 || stats.inquiryCycles > 0) {
            Spacer(Modifier.height(8.dp))
            TermPanel("运行统计") {
                LiveStat("BLE 数据包", "${stats.blePackets}")
                LiveStat("扫描轮次", "${stats.inquiryCycles}")
                LiveStat("发现设备", "${stats.devicesFound}")
                LiveStat("运行时长", "${stats.uptimeMs / 1000}秒")
            }
        }
        if (devices.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TermPanel("附近设备 (${devices.size})") {
                LazyColumn(Modifier.fillMaxWidth().height(140.dp)) {
                    items(devices.take(10)) { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(d.name.take(16), fontFamily = Mono, fontSize = 10.sp, color = White, modifier = Modifier.weight(1f))
                            Text(d.address, fontFamily = Mono, fontSize = 9.sp, color = Gray)
                            Text(" [${d.type}]", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                        }
                    }
                }
            }
        }
    }
}

/** 蓝牙压制日志面板 —— 独立订阅 log */
@Composable
private fun BtJamLogPanel(vm: BluetoothJammerViewModel) {
    val log by vm.log.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Spacer(Modifier.height(10.dp))
    LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
    Spacer(Modifier.height(4.dp))
    LazyColumn(Modifier.fillMaxWidth().height(180.dp)) {
        if (log.isEmpty()) item { HintBreathing("启动压制后，运行日志会实时显示在这里") }
        items(log.asReversed()) { e ->
            LogLine(tf.format(Date(e.timestamp)), "[${e.module}] ${e.message}", White)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// WiFi 干扰引擎
// ══════════════════════════════════════════════════════════════════════════

/**
 * WiFi 干扰攻击详情页
 *
 * 伪造信标帧泛洪工具。功能区块：
 * - 扫描按钮：扫描附近 2.4/5GHz WiFi 网络并选择目标
 * - 泛洪参数：时长、切换间隔
 * - 主操作按钮 + 周期进度 + 运行统计
 */
@Composable
fun WifiDetailScreen(vm: WifiDisruptViewModel, back: () -> Unit) {
    val ss by vm.scanState.collectAsState(); val fl by vm.isFlooding.collectAsState()
    var sel by remember { mutableStateOf<WifiNetwork?>(null) }
    var dur by remember { mutableIntStateOf(vm.getDuration()) }
    var toggle by remember { mutableFloatStateOf(vm.getToggleInterval().toFloat()) }
    val haptic = LocalHapticFeedback.current

    // 进入页面自动扫描附近网络(仅被动扫描;干扰泛洪仍需手动执行)
    LaunchedEffect(Unit) { if (ss is ScanState.Idle) vm.scanNetworks() }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WiFi 干扰引擎", back)
        PageEnter(locked = fl) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                ActionButton(running = ss is ScanState.Scanning, runLabel = "扫描附近网络", stopLabel = "正在扫描…") {
                    haptic.hud()
                    vm.scanNetworks()
                }
                Spacer(Modifier.height(8.dp))

                when (val s = ss) {
                    is ScanState.Scanning -> Text("正在扫描 2.4/5GHz 频段…", fontFamily = Sans, fontSize = 12.sp, color = White)
                    is ScanState.Results -> {
                        val sorted = s.networks.sortedByDescending { it.signalStrength }
                        Text("发现 ${sorted.size} 个网络 · 按信号排序 · 点击选择目标", fontFamily = Sans, fontSize = 11.sp, color = Gray)
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(Modifier.fillMaxWidth().height(220.dp)) {
                            items(sorted, key = { it.bssid }) { n ->
                                val on = sel?.bssid == n.bssid
                                val barColor = if (n.signalStrength > -50) Danger else if (n.signalStrength > -70) Warn else TacGreenDim
                                Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); sel = n; vm.selectTarget(n) }, color = if (on) Red.copy(alpha = 0.08f) else Color.Transparent) {
                                    Row(Modifier.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (on) "▸" else " ", fontFamily = Mono, fontSize = 13.sp, color = Red, modifier = Modifier.width(18.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(n.ssid.ifBlank { "<隐藏>" }.take(18), fontFamily = Mono, fontSize = 12.sp, color = if (on) White else Gray)
                                            Text("${n.bssid} · ${n.security} · ${n.band}", fontFamily = Mono, fontSize = 8.sp, color = Gray.copy(alpha = 0.8f))
                                        }
                                        // 信号强度条
                                        Box(Modifier.width(36.dp).height(3.dp).clip(CutCornerShape(topStart = 2.dp, bottomEnd = 2.dp)).background(SurfaceAlt)) {
                                            Box(
                                                Modifier.fillMaxWidth(((n.signalStrength + 100f) / 80f).coerceIn(0.06f, 1f)).height(3.dp)
                                                    .clip(CutCornerShape(topStart = 2.dp, bottomEnd = 2.dp)).background(barColor),
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text("CH${n.channel.toString().padStart(2)} ${n.signalStrength}dBm", fontFamily = Mono, fontSize = 10.sp, color = if (n.signalStrength > -50) RedDim else if (n.signalStrength > -70) White else Gray)
                                    }
                                }
                            }
                        }
                    }
                    is ScanState.Error -> ErrorBanner(s.message)
                    else -> {}
                }

                AnimatedVisibility(sel != null) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        TermPanel("泛洪参数") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("目标网络", fontFamily = Sans, fontSize = 11.sp, color = Gray)
                                Text(sel?.ssid ?: "-", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("干扰时长", fontFamily = Sans, fontSize = 11.sp, color = Gray)
                                Text("${dur}s", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                            }
                            Slider(dur.toFloat(), { dur = it.toInt().coerceIn(1, 60) }, valueRange = 1f..60f, steps = 14, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = Track))
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("切换间隔", fontFamily = Sans, fontSize = 11.sp, color = Gray)
                                Text("${toggle.toLong()}ms", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                            }
                            Slider(toggle, { toggle = it; vm.setToggleInterval(toggle.toLong()) }, valueRange = 40f..500f, steps = 22, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = Track))
                        }
                        Spacer(Modifier.height(8.dp))
                        ActionButton(fl, "执行干扰", "停止干扰") {
                            haptic.hud()
                            vm.setDuration(dur)
                            vm.setToggleInterval(toggle.toLong())
                            if (fl) vm.stopFlood() else vm.startFlood()
                        }
                    }
                }

                // 高频数据区独立订阅：泛洪期间周期进度与统计持续刷新
                WifiLivePanel(vm, fl = fl)
            }
        }
    }
}

/** WiFi 干扰实时面板 —— 独立订阅周期进度与运行统计 */
@Composable
private fun WifiLivePanel(vm: WifiDisruptViewModel, fl: Boolean) {
    val hr by vm.hotspotResult.collectAsState()
    val stats by vm.stats.collectAsState()

    Column {
        when (val r = hr) {
            is HotspotResult.Progress -> {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { r.cycle.toFloat() / r.totalCycles }, modifier = Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                Text("周期 ${r.cycle}/${r.totalCycles}", fontFamily = Sans, fontSize = 10.sp, color = White, modifier = Modifier.padding(top = 3.dp))
            }
            is HotspotResult.Completed -> Text("泛洪完成", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(top = 6.dp))
            is HotspotResult.Error -> ErrorBanner(r.message)
            else -> {}
        }

        if (fl || stats.startCount > 0 || stats.stopCount > 0) {
            Spacer(Modifier.height(8.dp))
            TermPanel("运行统计") {
                LiveStat("周期", "${stats.cycles}/${stats.totalCycles}")
                LiveStat("热点启动", "${stats.startCount}")
                LiveStat("热点停止", "${stats.stopCount}")
                LiveStat("运行时长", "${stats.uptimeMs / 1000}秒")
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// WiFi 压制引擎
// ══════════════════════════════════════════════════════════════════════════

/**
 * WiFi 压制攻击详情页
 *
 * 自动扫描并交替压制附近所有 SSID。
 * 功能区块：信道切换间隔配置 / 主操作按钮 / 目标列表 / 实时日志。
 */
@Composable
fun WifiJamDetailScreen(vm: WifiJammerViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    var ms by remember { mutableFloatStateOf(vm.getInterval().toFloat()) }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WiFi 压制引擎", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermPanel("配置") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("信道切换间隔", fontFamily = Sans, fontSize = 11.sp, color = Gray)
                        Text("${ms.toLong()}ms", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                    }
                    Slider(ms, { ms = it; vm.setInterval(ms.toLong()) }, valueRange = 60f..300f, steps = 11, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = Track))
                }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, "执行压制", "停止压制") {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                error?.let { ErrorBanner(it) }

                // 高频数据区独立订阅：压制期间目标计数/轮次统计/扫描结果/日志均高频刷新
                WifiJamLivePanel(vm, running = running, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** WiFi 压制实时面板 —— 独立订阅统计与扫描结果，日志分栏展示 */
@Composable
private fun WifiJamLivePanel(vm: WifiJammerViewModel, running: Boolean, modifier: Modifier = Modifier) {
    val ss by vm.scanState.collectAsState()
    val tc by vm.targetCount.collectAsState()
    val stats by vm.stats.collectAsState()
    val log by vm.log.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier) {
        AnimatedVisibility(running) {
            Column {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                Text("压制目标 ${tc} 个 · 轮次 ${stats.cycles} · 启动 ${stats.hotspotStarts} · 失败 ${stats.hotspotFailures}", fontFamily = Sans, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 3.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        when (val s = ss) {
            is JammerScanState.Scanning -> Text("正在扫描目标…", fontFamily = Sans, fontSize = 12.sp, color = White)
            is JammerScanState.Results -> {
                Text("发现目标 ${s.networks.size} 个", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(0.4f)) {
                    items(s.networks.take(12)) { n ->
                        LogLine("CH${n.channel.toString().padStart(2)}", "${n.bssid.take(17).padEnd(17)}  ${n.ssid.ifBlank { "<隐藏>" }.take(22)}  ${n.signalStrength}dBm", White)
                    }
                }
            }
            is JammerScanState.Error -> ErrorBanner(s.message)
            else -> {}
        }

        Spacer(Modifier.height(8.dp))
        LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(0.5f)) {
            if (log.isEmpty()) item { HintBreathing("启动压制后，运行日志会实时显示在这里") }
            items(log.asReversed()) { e ->
                LogLine(tf.format(Date(e.timestamp)), "[${e.module}] ${e.message}", White)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// WEB 服务器
// ══════════════════════════════════════════════════════════════════════════

/** 响应头里 Content-Length 字段的前缀 —— 面板中把它的值换成占位说明 */
private const val CONTENT_LENGTH_PREFIX = "Content-Length:"

/**
 * Web 服务器详情页
 *
 * 嵌入式 HTTP 服务器管理界面。功能区块：
 * - HTML 文件选择
 * - 启动/停止按钮
 * - 服务器信息（监听地址、实际响应头、浏览器访问地址）
 * - 运行统计 + 实时日志
 */
@Composable
fun WebServerDetailScreen(vm: WebServerViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val url by vm.serverUrl.collectAsState()
    val error by vm.error.collectAsState()
    val fileName by vm.fileName.collectAsState()
    val haptic = LocalHapticFeedback.current

    // 响应头结构取自服务器实际使用的构造函数，界面与服务器不再各写一份
    val headerFields = remember {
        WebServerManager.buildResponseHeader(
            WebServerManager.STATUS_200,
            WebServerManager.CONTENT_TYPE_HTML,
            contentLength = 0,
        ).lineSequence().filter { it.isNotEmpty() }.toList()
    }
    val otherStatuses = remember {
        listOf(
            WebServerManager.STATUS_400, WebServerManager.STATUS_404,
            WebServerManager.STATUS_405, WebServerManager.STATUS_414,
        ).joinToString(" / ") { it.substringBefore(' ') }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { vm.loadFile(it) }
    }

    val port = WebServerManager.DEFAULT_PORT
    val host = url.substringAfter("http://", "").substringBefore(':')

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WEB 服务器", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                TermPanel("网页文件") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fileName, fontFamily = Mono, fontSize = 12.sp, color = White, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        GhostButton("选择文件") { filePicker.launch(arrayOf("text/html")) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, "执行服务器", "停止服务器") {
                    haptic.hud()
                    if (running) vm.stopServer() else vm.startServer()
                }
                error?.let { ErrorBanner(it) }

                AnimatedVisibility(running) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                        Spacer(Modifier.height(8.dp))
                        TermPanel("服务器信息") {
                            PanelStat("监听地址", host)
                            Spacer(Modifier.height(8.dp))
                            Text("响应头（服务器实际发出的字段）", fontFamily = Sans, fontSize = 10.sp, color = Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            headerFields.forEach { field ->
                                val shown = if (field.startsWith(CONTENT_LENGTH_PREFIX)) {
                                    "$CONTENT_LENGTH_PREFIX <随页面大小变化>"
                                } else {
                                    field
                                }
                                Text("  $shown", fontFamily = Mono, fontSize = 11.sp, color = if (field.startsWith("HTTP/")) White else Gray)
                            }
                            Text("  其余状态: $otherStatuses", fontFamily = Sans, fontSize = 10.sp, color = Gray)
                            Spacer(Modifier.height(8.dp))
                            AsciiDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("浏览器访问地址", fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(bottom = 2.dp))
                            Text(url, fontFamily = Mono, fontSize = 12.sp, color = White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 高频数据区独立订阅：访问期间请求统计与访问日志持续刷新
                WebServerStatsPanel(vm, running = running)
                WebServerLogPanel(vm)
            }
        }
    }
}

/** WEB 服务器统计面板 —— 独立订阅请求统计 */
@Composable
private fun WebServerStatsPanel(vm: WebServerViewModel, running: Boolean) {
    val stats by vm.stats.collectAsState()
    Spacer(Modifier.height(8.dp))
    TermPanel("运行统计") {
        LiveStat("总请求", "${stats.totalRequests}")
        LiveStat("成功响应", "${stats.served200}")
        LiveStat("未找到", "${stats.notFound404}")
        LiveStat("方法拒绝", "${stats.rejectedMethod}")
        LiveStat("错误请求", "${stats.badRequests}")
        LiveStat("处理异常", "${stats.handlerErrors}")
        LiveStat("活动连接", "${stats.activeConnections}")
        LiveStat("每分钟请求", "%.1f".format(stats.requestsPerMinute))
        LiveStat("运行时长", vm.formatUptime(stats.uptimeMs))
        LiveStat("状态", if (running) "监听中" else "已停止", if (running) White else Gray)
    }
}

/** WEB 服务器日志面板 —— 独立订阅访问日志 */
@Composable
private fun WebServerLogPanel(vm: WebServerViewModel) {
    val log by vm.log.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Spacer(Modifier.height(10.dp))
    LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
    Spacer(Modifier.height(4.dp))
    if (log.isEmpty()) {
        HintBreathing("启动服务器后，访问日志会实时显示在这里")
    } else {
        Column(Modifier.fillMaxWidth()) {
            log.asReversed().take(LOG_TAIL).forEach { e ->
                LogLine(tf.format(Date(e.timestamp)), "[${e.module}] ${e.message}", White)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// ARP 网络扫描
// ══════════════════════════════════════════════════════════════════════════

/** ARP 设备列表单次渲染的最大行数；超出部分只影响显示，统计数字仍为全量 */
private const val ARP_DEVICE_RENDER_LIMIT = 128

/**
 * ARP 网络扫描详情页
 *
 * 局域网 Ping Sweep 设备发现界面。功能区块：
 * - 扫描 / 取消按钮 + 进度条
 * - 扫描统计面板
 * - 发现设备列表（IP / MAC / 主机名三列）
 * - 实时日志
 */
@Composable
fun ArpDetailScreen(vm: ArpScanViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current

    // 进入页面自动扫描局域网,离开自动停止(被动扫描,无破坏性)
    DisposableEffect(Unit) {
        if (!vm.isRunning.value) vm.scan()
        onDispose { vm.stop() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("ARP 网络扫描", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                ActionButton(running, "执行扫描", "取消扫描") {
                    haptic.hud()
                    if (running) vm.stop() else vm.scan()
                }

                error?.let { ErrorBanner(it) }

                // 高频数据区独立订阅：扫描期间探测进度/统计/设备/日志每秒多次刷新
                ArpLivePanel(vm, running = running)
                if (!running) ArpIdleHint(vm)
            }
        }
    }
}

/** ARP 扫描实时面板 —— 独立订阅进度、统计、设备与日志 */
@Composable
private fun ArpLivePanel(vm: ArpScanViewModel, running: Boolean) {
    val devices by vm.devices.collectAsState()
    val stats by vm.stats.collectAsState()
    val log by vm.log.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column {
        if (running) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { stats.hostsScanned.toFloat() / stats.hostCount.coerceAtLeast(1).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = Red,
                trackColor = Track,
            )
            Text("已探测 ${stats.hostsScanned}/${stats.hostCount} 台主机", fontFamily = Sans, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 3.dp))
        }

        Spacer(Modifier.height(8.dp))
        TermPanel("扫描统计") {
            LiveStat("网段", stats.subnet.ifEmpty { "—" })
            LiveStat("本机地址", stats.localIp.ifEmpty { "—" })
            LiveStat("已探测", "${stats.hostsScanned} / ${stats.hostCount}")
            LiveStat("在线主机", "${stats.hostsReachable}", if (stats.hostsReachable > 0) White else Gray)
            LiveStat("缓存命中", "${stats.arpCacheHits}")
            LiveStat("反向解析", "${stats.hostnameResolved}")
            LiveStat("发现设备", "${stats.deviceCount}")
            LiveStat("耗时", vm.formatElapsed(stats.elapsedMs))
            LiveStat("状态", if (running) "扫描中" else "空闲", if (running) White else Gray)
        }

        if (devices.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TermPanel("发现 ${devices.size} 台设备") {
                Row(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
                    Text("IP 地址".padEnd(18), fontFamily = Mono, fontSize = 10.sp, color = RedDim, fontWeight = FontWeight.Bold)
                    Text("MAC 地址".padEnd(20), fontFamily = Mono, fontSize = 10.sp, color = RedDim, fontWeight = FontWeight.Bold)
                    Text("主机名", fontFamily = Sans, fontSize = 10.sp, color = RedDim, fontWeight = FontWeight.Bold)
                }
                AsciiDivider(modifier = Modifier.padding(vertical = 2.dp))
                devices.take(ARP_DEVICE_RENDER_LIMIT).forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(d.ip.padEnd(18), fontFamily = Mono, fontSize = 10.sp, color = White)
                        Text(d.mac.ifEmpty { "(获取中)" }.padEnd(20), fontFamily = Mono, fontSize = 10.sp, color = if (d.mac.isNotEmpty()) Gray else RedDim.copy(alpha = 0.5f))
                        Text(d.hostname.ifEmpty { d.latency }, fontFamily = Mono, fontSize = 10.sp, color = if (d.hostname.isNotEmpty()) RedDim else Gray)
                    }
                }
                if (devices.size > ARP_DEVICE_RENDER_LIMIT) {
                    Spacer(Modifier.height(4.dp))
                    Text("… 另有 ${devices.size - ARP_DEVICE_RENDER_LIMIT} 台未渲染（仅影响显示，统计数字仍为全量）", fontFamily = Sans, fontSize = 10.sp, color = Gray)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
        Spacer(Modifier.height(4.dp))
        if (log.isEmpty()) {
            HintBreathing("扫描开始后，战地日志会实时显示在这里")
        } else {
            Column(Modifier.fillMaxWidth()) {
                log.asReversed().take(LOG_TAIL).forEach { e ->
                    LogLine(tf.format(Date(e.timestamp)), "[${e.module}] ${e.message}", White)
                }
            }
        }
    }
}

/** ARP 空闲提示 —— 独立订阅设备列表，仅空闲态触发 */
@Composable
private fun ArpIdleHint(vm: ArpScanViewModel) {
    val devices by vm.devices.collectAsState()
    if (devices.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        HintBreathing("执行上方任务扫描局域网，在线设备会实时列出")
    }
}

// ══════════════════════════════════════════════════════════════════════════
// BLE 嗅探扫描
// ══════════════════════════════════════════════════════════════════════════

/**
 * BLE 嗅探扫描详情页
 *
 * 低功耗蓝牙被动扫描与 RSSI 图谱展示界面。功能区块：
 * - 嗅探启动 / 停止按钮
 * - 选中设备详情（地址、发射功率、类型、服务、厂商数据）
 * - RSSI 实时走势图（Canvas 绘制）
 * - 设备列表
 */
@Composable
fun BleScannerScreen(vm: BleScannerViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current
    var selected by remember { mutableStateOf<String?>(null) }

    // 进入页面自动开始嗅探,离开自动停止 —— 被动扫描,无破坏性
    DisposableEffect(Unit) {
        if (!vm.isRunning.value) vm.start()
        onDispose { vm.stop() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("BLE 嗅探扫描", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                ActionButton(running, "执行嗅探", "停止嗅探") {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                error?.let { ErrorBanner(it) }

                // 高频数据区独立订阅：嗅探期间设备列表与 RSSI 每秒多次刷新
                BleScannerLivePanel(vm, running = running, selected = selected, onSelect = { selected = it }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** BLE 嗅探实时面板 —— 独立订阅设备流，包含进度、详情与列表 */
@Composable
private fun BleScannerLivePanel(
    vm: BleScannerViewModel,
    running: Boolean,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val devices by vm.devices.collectAsState()
    val selDev = devices.find { it.address == selected }

    Column(modifier) {
        if (running) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track)
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已发现 ${devices.size} 台设备", fontFamily = Sans, fontSize = 11.sp, color = White)
                Text(
                    "最强 ${devices.maxOfOrNull { it.rssi }?.let { "${it}dBm" } ?: "--"}",
                    fontFamily = Mono, fontSize = 9.sp, color = Gold,
                )
            }
        }

        if (!running && devices.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            HintBreathing("执行上方任务开始嗅探，附近 BLE 设备会实时出现")
        }

        if (selDev != null) {
            Spacer(Modifier.height(8.dp))
            TermPanel("设备详情 · ${selDev.name}") {
                PanelStat("地址", selDev.address)
                PanelStat("发射功率", if (selDev.txPower > Int.MIN_VALUE) "${selDev.txPower}dBm" else "未知")
                PanelStat("设备类型", selDev.deviceType.ifEmpty { "未知" })
                if (selDev.services.isNotEmpty()) {
                    PanelStat("服务", selDev.services.joinToString(", "))
                }
                if (selDev.manufacturerData.isNotEmpty()) {
                    PanelStat("厂商数据", selDev.manufacturerData.entries.joinToString { "${it.key}: ${it.value}" })
                }

                if (selDev.rssiHistory.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text("信号强度走势 (dBm)", fontFamily = Sans, fontSize = 10.sp, color = RedDim, modifier = Modifier.padding(bottom = 3.dp))
                    Box(Modifier.fillMaxWidth().height(60.dp).background(Color(0xFF04121C)).border(0.5.dp, Track, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))) {
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
                                drawPath(path, TacGreen, style = Stroke(1.5f))
                            }
                        }
                    }
                    Text("最低:${selDev.rssiHistory.minOrNull() ?: 0}  最高:${selDev.rssiHistory.maxOrNull() ?: 0}  当前:${selDev.rssi}", fontFamily = Sans, fontSize = 9.sp, color = Gray, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("已发现 ${devices.size} 台设备 · 点击查看详情", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(devices, key = { it.address }) { d ->
                val sel = d.address == selected
                val barColor = if (d.rssi > -50) Danger else if (d.rssi > -70) Warn else TacGreenDim
                Surface(modifier = Modifier.fillMaxWidth().clickable { onSelect(if (sel) null else d.address) }, color = if (sel) Red.copy(alpha = 0.06f) else Color.Transparent) {
                    Row(Modifier.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (sel) "▸" else " ", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.width(14.dp))
                        Text(d.name.take(20), fontFamily = Mono, fontSize = 11.sp, color = if (sel) Red else White, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        // 信号强度条
                        Box(Modifier.width(36.dp).height(3.dp).clip(CutCornerShape(topStart = 2.dp, bottomEnd = 2.dp)).background(SurfaceAlt)) {
                            Box(
                                Modifier.fillMaxWidth(((d.rssi + 100f) / 80f).coerceIn(0.06f, 1f)).height(3.dp)
                                    .clip(CutCornerShape(topStart = 2.dp, bottomEnd = 2.dp)).background(barColor),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(" ${d.rssi}dBm".padStart(7), fontFamily = Mono, fontSize = 10.sp, color = if (d.rssi > -50) RedDim else if (d.rssi > -70) White else Gray)
                        Text("  ${d.address}", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// PING 泛洪
// ══════════════════════════════════════════════════════════════════════════

/**
 * Ping 泛洪详情页
 *
 * 持续 ICMP Ping 探测与压力测试界面。功能区块：
 * - 目标主机输入 + 启动/停止按钮
 * - 统计面板（发送/接收/丢包/最小/平均/最大）
 * - 实时 Ping 结果列表（按 RTT 着色，自动滚动）
 */
@Composable
fun PingScreen(vm: PingViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val host by vm.host.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("PING 泛洪", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput("目标主机", host, placeholder = "例如 8.8.8.8 或 baidu.com") { vm.setHost(it) }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, "执行探测", "停止探测") {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }

                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                }
                error?.let { ErrorBanner(it) }

                // 高频数据区独立订阅：探测期间每秒多次返回结果，隔离后只重组统计与列表
                PingLivePanel(vm, running = running, host = host, error = error)
            }
        }
    }
}

/** Ping 实时面板 —— 独立订阅结果与统计 */
@Composable
private fun PingLivePanel(vm: PingViewModel, running: Boolean, host: String, error: String?) {
    val results by vm.results.collectAsState()
    val sent by vm.sent.collectAsState(); val recv by vm.received.collectAsState()
    val loss by vm.loss.collectAsState()
    val min by vm.min.collectAsState(); val avg by vm.avg.collectAsState(); val max by vm.max.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(results.size) { if (results.isNotEmpty()) list.scrollToItem(results.size - 1) }

    Column {
        if (!running && sent == 0 && error == null) {
            Spacer(Modifier.height(8.dp))
            HintBreathing("填入目标后执行上方任务，探测结果实时返回")
        }

        if (sent > 0) {
            Spacer(Modifier.height(8.dp))
            TermPanel("统计") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatChip("发送", "$sent")
                    StatChip("接收", "$recv")
                    StatChip("丢包", "${loss}%", loss == 0)
                    StatChip("最小", "${min}ms")
                    StatChip("平均", "${avg}ms")
                    StatChip("最大", "${max}ms")
                }
            }
        }

        if (results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("探测结果 · $host", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
            Spacer(Modifier.height(4.dp))
            LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(results, key = { it.sequence }) { r ->
                    LogLine("seq=${r.sequence}", "${r.bytes}b 来自 ${r.ip} · ttl=${r.ttl} · 耗时 ${r.timeMs}ms", if (r.timeMs > 500) Danger else if (r.timeMs > 100) RedDim else White)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// HTTP 客户端
// ══════════════════════════════════════════════════════════════════════════

/** 响应体查看器的最大高度 */
private val BODY_VIEWER_MAX_HEIGHT = 320.dp

/**
 * HTTP 客户端详情页（curl 风格）
 *
 * 完整的 HTTP 请求构建与响应诊断工具。功能区块：
 * - 请求面板：URL、方法、请求头、请求体
 * - 主操作按钮：发送 / 取消
 * - 响应区域：状态行、响应头、响应体（可选中复制）
 * - 请求统计 + 实时日志
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
    val verb = method.trim().uppercase(Locale.US)

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("HTTP 客户端", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                TermInput("请求地址", url, placeholder = "https://example.com") { vm.setUrl(it) }

                Spacer(Modifier.height(8.dp))
                Text("请求方法", fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(bottom = 5.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    HttpClientManager.SUPPORTED_METHODS.forEach { m ->
                        SelectChip(selected = m == verb, text = m) { vm.setMethod(m) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TermInput("请求头", headers, placeholder = "Key: Value（# 开头为注释行）") { vm.setHeaders(it) }

                if (verb in HttpClientManager.BODY_METHODS) {
                    Spacer(Modifier.height(8.dp))
                    TermInput("请求体", body) { vm.setBody(it) }
                }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, "发送请求", "取消请求") {
                    haptic.hud()
                    if (running) vm.cancel() else vm.send()
                }
                if (!running && url.isNotBlank()) {
                    Text(url.trim(), fontFamily = Mono, fontSize = 10.sp, color = White.copy(alpha = 0.5f), maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                }
                error?.let { ErrorBanner(it) }

                resp?.let { r ->
                    Spacer(Modifier.height(10.dp))
                    AsciiDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("状态 ${r.statusCode} · 耗时 ${r.timeMs}ms", fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (r.statusCode < 400) White else Danger,
                    )
                    PanelStat("响应大小", vm.formatBytes(r.bodyBytes.toLong()))
                    PanelStat("字符集", r.charset)
                    if (r.redirected && r.finalUrl.isNotEmpty()) {
                        PanelStat("重定向落点", r.finalUrl, Gray)
                    }
                    if (r.truncated) {
                        Text("[!] 响应体超过 ${vm.formatBytes(HttpClientManager.MAX_BODY_BYTES.toLong())}，已截断", fontFamily = Sans, fontSize = 10.sp, color = Danger, modifier = Modifier.padding(top = 3.dp))
                    }

                    if (r.headers.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        TermPanel("响应头 (${r.headers.size})") {
                            r.headers.entries.sortedBy { it.key.lowercase(Locale.US) }.forEach { (k, v) ->
                                Text("$k: ${v.joinToString(", ")}", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("响应体", fontFamily = Sans, fontSize = 10.sp, color = RedDim, modifier = Modifier.padding(bottom = 4.dp))
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = BODY_VIEWER_MAX_HEIGHT)
                            .background(SurfaceBg)
                            .border(1.dp, Track, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                            .padding(10.dp),
                    ) {
                        item { SelectionContainer { Text(r.body.ifEmpty { "（空）" }, fontFamily = Mono, fontSize = 10.sp, color = White) } }
                    }
                }

                // 高频数据区独立订阅：传输期间统计与日志持续刷新
                HttpStatsPanel(vm, running = running)
                HttpLogPanel(vm)
            }
        }
    }
}

/** HTTP 请求统计面板 —— 独立订阅累计统计 */
@Composable
private fun HttpStatsPanel(vm: HttpClientViewModel, running: Boolean) {
    val stats by vm.stats.collectAsState()
    Spacer(Modifier.height(8.dp))
    TermPanel("请求统计") {
        LiveStat("总请求", "${stats.attempts}")
        LiveStat("成功 2xx", "${stats.successCount}")
        LiveStat("重定向 3xx", "${stats.redirectCount}")
        LiveStat("客户端错误 4xx", "${stats.clientErrorCount}")
        LiveStat("服务端错误 5xx", "${stats.serverErrorCount}")
        LiveStat("失败", "${stats.failureCount}", if (stats.failureCount > 0) Danger else White)
        LiveStat("接收流量", vm.formatBytes(stats.bytesReceived))
        LiveStat("平均耗时", "${stats.averageDurationMs} ms")
        LiveStat("最近状态", if (stats.lastStatusCode == 0) "-" else "${stats.lastStatusCode}")
        LiveStat("最近耗时", "${stats.lastDurationMs} ms")
        if (stats.lastError.isNotEmpty()) LiveStat("最近错误", stats.lastError, Danger)
        if (stats.redirected && stats.lastFinalUrl.isNotEmpty()) LiveStat("最近落点", stats.lastFinalUrl, Gray)
        LiveStat("状态", if (running) "运行中" else "空闲", if (running) White else Gray)
    }
}

/** HTTP 传输日志面板 —— 独立订阅日志 */
@Composable
private fun HttpLogPanel(vm: HttpClientViewModel) {
    val log by vm.log.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Spacer(Modifier.height(10.dp))
    LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
    Spacer(Modifier.height(4.dp))
    if (log.isEmpty()) {
        HintBreathing("发送请求后，传输日志会实时显示在这里")
    } else {
        Column(Modifier.fillMaxWidth()) {
            log.asReversed().take(LOG_TAIL).forEach { e ->
                LogLine(tf.format(Date(e.timestamp)), "[${e.module}] ${e.message}", White)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 端口扫描
// ══════════════════════════════════════════════════════════════════════════

/**
 * 端口扫描详情页
 *
 * TCP Connect 端口扫描工具。功能区块：
 * - 目标主机输入 + 启动/停止按钮 + 进度
 * - 扫描报告（开放端口 + 服务 + Banner）
 */
@Composable
fun PortScannerScreen(vm: PortScannerViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val host by vm.host.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("端口扫描", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput("目标主机", host, placeholder = "例如 192.168.1.1") { vm.setHost(it) }
                Spacer(Modifier.height(8.dp))
                ActionButton(running, "执行扫描", "取消扫描") {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                error?.let { ErrorBanner(it) }

                // 高频数据区独立订阅：扫描期间进度与结果持续刷新
                PortScanLivePanel(vm, running = running, host = host, error = error)
            }
        }
    }
}

/** 端口扫描实时面板 —— 独立订阅进度与结果 */
@Composable
private fun PortScanLivePanel(vm: PortScannerViewModel, running: Boolean, host: String, error: String?) {
    val results by vm.results.collectAsState()
    val scanned by vm.scanned.collectAsState()
    val total by vm.total.collectAsState()
    val open = results.filter { it.state == "open" }

    Column {
        if (running) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { scanned.toFloat() / maxOf(1, total) }, modifier = Modifier.fillMaxWidth(), color = Red, trackColor = Track)
            Text("已扫描 $scanned/$total · 开放 ${open.size}", fontFamily = Sans, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 3.dp))
        }

        if (!running && results.isEmpty() && error == null) {
            Spacer(Modifier.height(8.dp))
            HintBreathing("输入目标主机后启动扫描，开放端口会实时列出")
        }

        if (results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("扫描报告 · $host", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                item {
                    TermPanel("扫描报告 · $host") {
                        if (open.isEmpty()) Text("未发现开放端口", fontFamily = Sans, fontSize = 11.sp, color = Gray)
                        open.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${p.port}/tcp", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.width(72.dp))
                                Text("开放", fontFamily = Sans, fontSize = 10.sp, color = Red, modifier = Modifier.width(44.dp))
                                Text(p.service, fontFamily = Mono, fontSize = 11.sp, color = White)
                            }
                            if (p.banner.isNotEmpty()) Text("  └ ${p.banner}", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("共扫描 $total 个端口，${open.size} 个开放", fontFamily = Sans, fontSize = 10.sp, color = Gray)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// WiFi 对讲机
// ══════════════════════════════════════════════════════════════════════════

/**
 * 对讲机详情页
 *
 * 局域网 PTT 实时语音。功能区块：
 * - 启动/停止按钮 + 在线设备列表（说话中高亮）
 * - 直播模式开关
 * - 按住说话按钮（按住收音，松开停止）
 */
@Composable
fun WalkieTalkieScreen(vm: WalkieTalkieViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val talking by vm.isTalking.collectAsState()
    val error by vm.error.collectAsState()
    val isLive by vm.isLive.collectAsState()
    val countdown by vm.liveCountdown.collectAsState()
    val haptic = LocalHapticFeedback.current

    val active = talking || isLive

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WiFi 对讲机", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                ActionButton(running, "执行对讲", "停止对讲") {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                error?.let { ErrorBanner(it) }

                // 高频数据区独立订阅：对讲期间在线设备与说话状态持续刷新
                WalkiePeersPanel(vm, running = running)

                Spacer(Modifier.weight(1f))

                if (running) {
                    // ── 直播模式开关 ──
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            haptic.hud()
                            vm.toggleLive()
                        },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = if (isLive) Red.copy(alpha = 0.15f) else Color.Transparent,
                        border = BorderStroke(1.5.dp, if (isLive) Gold else Red.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            if (isLive) "直播中 · 自动发送 ${countdown}s" else "直播模式 · 免按自动发送",
                            fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = if (isLive) Red else Red.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 9.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // ── 圆形 PTT 大按钮:战术罗盘式 ──
                    val breathPulse = rememberInfiniteTransition()
                    val breathAlpha by breathPulse.animateFloat(0.35f, 0.95f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse))
                    val ripplePhase by breathPulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)))
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).size(150.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 发送中扩散波纹环
                        if (active) {
                            Canvas(Modifier.fillMaxSize()) {
                                val c = Offset(size.width / 2f, size.height / 2f)
                                val maxR = size.minDimension / 2f
                                drawCircle(Red.copy(alpha = 0.15f), maxR, c)
                                drawCircle(Red.copy(alpha = (1f - ripplePhase) * 0.55f), maxR * (0.45f + ripplePhase * 0.55f), c, style = Stroke(3f))
                                drawCircle(Gold.copy(alpha = (1f - ripplePhase) * 0.35f), maxR * (0.45f + ripplePhase * 0.55f), c, style = Stroke(7f))
                                // 外圈刻度(战术罗盘感)
                                for (i in 0 until 12) {
                                    val a = Math.toRadians(i * 30.0)
                                    drawLine(
                                        Red.copy(alpha = 0.5f),
                                        Offset(c.x + maxR * 0.86f * kotlin.math.cos(a).toFloat(), c.y + maxR * 0.86f * kotlin.math.sin(a).toFloat()),
                                        Offset(c.x + maxR * 0.95f * kotlin.math.cos(a).toFloat(), c.y + maxR * 0.95f * kotlin.math.sin(a).toFloat()),
                                        2f,
                                    )
                                }
                            }
                        }
                        // 圆盘本体
                        Box(
                            modifier = Modifier
                                .size(if (active) 138.dp else 130.dp)
                                .pointerInput(isLive) {
                                    detectTapGestures(
                                        onPress = {
                                            haptic.hud()
                                            if (!isLive) vm.startTalk()
                                            tryAwaitRelease()
                                            if (!isLive) vm.stopTalk()
                                        },
                                    )
                                }
                                .clip(CircleShape)
                                .background(if (active) Red.copy(alpha = breathAlpha) else Red.copy(alpha = 0.10f))
                                .border(2.dp, if (active) Gold.copy(alpha = 0.95f) else Red.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Star(if (active) White else Red, 14.dp)
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    if (active) "发送中" else "按住说话",
                                    fontFamily = Sans, fontSize = if (active) 15.sp else 14.sp, fontWeight = FontWeight.Black,
                                    color = if (active) White else Red,
                                )
                                Text("PTT", fontFamily = Mono, fontSize = 8.sp, color = if (active) White.copy(alpha = 0.8f) else Gray)
                            }
                        }
                    }
                    Text(
                        "按住圆盘发送语音 · 松开停止 · 直播模式免按",
                        fontFamily = Sans, fontSize = 9.sp, color = Gray,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/** 对讲机在线设备面板 —— 独立订阅设备流与说话状态 */
@Composable
private fun WalkiePeersPanel(vm: WalkieTalkieViewModel, running: Boolean) {
    val peers by vm.peers.collectAsState()
    if (running && peers.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        TermPanel("在线设备 (${peers.size})") {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 130.dp)) {
                itemsIndexed(peers) { i, p ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { delay(i * 80L); visible = true }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(300)) + androidx.compose.animation.slideInHorizontally(tween(350)) { it / 3 },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .background(if (p.isTalking) Red.copy(alpha = 0.12f) else Color.Transparent, CutCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (p.isTalking) "⬤ " else "○ ", fontFamily = Mono, fontSize = 10.sp, color = if (p.isTalking) Red else Gray)
                            Text(p.name.take(16), fontFamily = Mono, fontSize = 11.sp, color = if (p.isTalking) Red else White, fontWeight = if (p.isTalking) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            Text(p.ip, fontFamily = Mono, fontSize = 10.sp, color = Gray)
                            if (p.isTalking) {
                                Spacer(Modifier.width(6.dp))
                                Text("说话中", fontFamily = Sans, fontSize = 9.sp, color = Red, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 设备雷达
// ══════════════════════════════════════════════════════════════════════════

/**
 * 设备雷达页 —— 磷光 PPI 雷达 + 真实罗盘
 *
 * 全部视觉动画运行在 Canvas 绘制层（draw 阶段读取状态，零重组）：
 * - 渐变扫描光束 + 8 层磷光尾迹扇形，光束前沿亮线
 * - 目标被光束扫过时点亮、扫过后留下磷光余晖（真雷达的灵魂）
 * - 红色目标 blip 高亮醒目：大光晕 + 双脉动环 + 亮核十字（敌目标优先辨识）
 * - 真实罗盘：旋转矢量传感器（陀螺仪+地磁融合）驱动，盘面随真机转动，
 *   北东南西方位永远指向真实方向；顶部固定三角为机头航向指针
 * - 背景噪点闪烁 + 开机辉光扩散 + 声呐 ping 环
 * - 设备过滤：类型芯片（全部/WIFI/BLE）+ 名称搜索 + 聚焦锁定
 * - 新目标出现触发触觉警报（受设置页开关控制）
 */
@Composable
fun RadarScreen(
    vm: RadarViewModel,
    back: () -> Unit,
    radarAlertOn: Boolean,
    onSelectTarget: (String) -> Unit,
) {
    val running by vm.isRunning.collectAsState()
    val haptic = LocalHapticFeedback.current

    // 过滤状态：类型 + 关键词 + 聚焦目标
    var typeFilter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var focusedId by remember { mutableStateOf<String?>(null) }

    // 进入页面启动罗盘传感器，离开时释放（扫描可独立继续运行）
    DisposableEffect(Unit) {
        vm.startCompass()
        onDispose { vm.stopCompass() }
    }

    // 进入页面自动启动雷达扫描,离开自动停止(被动扫描,无破坏性)
    DisposableEffect(Unit) {
        if (!vm.isRunning.value) vm.start()
        onDispose { vm.stop() }
    }

    // 新目标触觉警报：首次发现某设备时震动一次
    LaunchedEffect(radarAlertOn) {
        vm.contact.collect { event ->
            if (event != null && radarAlertOn) {
                haptic.hud(HapticFeedbackType.LongPress)
            }
        }
    }

    // 聚焦目标距离振动：越近振幅越大、节奏越快；越远渐弱；超出雷达量程静默
    val context = LocalContext.current
    LaunchedEffect(focusedId, radarAlertOn) {
        while (true) {
            val t = focusedId?.let { id -> vm.targets.value.firstOrNull { it.id == id } }
            if (t == null || !radarAlertOn || t.distanceM > 100f) {
                delay(1200)
                continue
            }
            // 距离 100m → 轻微 0.12 振幅 / 2.6s 间隔；贴脸 0m → 满幅 1.0 / 0.4s 急促
            val strength = ((100f - t.distanceM) / 100f).coerceIn(0f, 1f)
            proximityVibrate(context, amplitude01 = 0.12f + 0.88f * strength)
            delay((2600f - strength * 2200f).coerceAtLeast(350f).toLong())
        }
    }

    // 战术雷达站:竖屏布局 —— 上方大扫描盘撑满屏宽,目标列表在下方
    // 定位开关轮询:Android 9+ 定位关闭时 WiFi 扫描结果为空(BLE 不受影响)
    val locationOn by produceState(initialValue = true) {
        val lm = context.applicationContext.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
        while (true) {
            value = runCatching { lm?.isLocationEnabled == true }.getOrDefault(true)
            delay(2000)
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("设备雷达", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
                ActionButton(running, "执行雷达", "停止雷达") {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                // 定位关闭横幅:WiFi 目标此时不可能进雷达,给出原因与快捷入口
                if (!locationOn) {
                    Spacer(Modifier.height(5.dp))
                    Surface(
                        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                        color = Warn.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Warn.copy(alpha = 0.5f)),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("定位服务关闭 —— WiFi 目标不可见(BLE 不受影响)", fontFamily = Sans, fontSize = 10.sp, color = Warn, modifier = Modifier.weight(1f))
                            Text(
                                "打开设置 ▸",
                                fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold,
                                modifier = Modifier.clickable {
                                    haptic.hud()
                                    runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                CompassStrip(vm)
                Spacer(Modifier.height(5.dp))
                RadarScope(
                    vm,
                    running = running,
                    typeFilter = typeFilter,
                    query = query,
                    focusedId = focusedId,
                    modifier = Modifier.fillMaxWidth().weight(1.25f),
                )
                Spacer(Modifier.height(5.dp))
                RadarFilterBar(
                    typeFilter = typeFilter,
                    onTypeFilter = { typeFilter = it; focusedId = null },
                    query = query,
                    onQuery = { query = it; focusedId = null },
                )
                Spacer(Modifier.height(5.dp))
                RadarTrackPanel(
                    vm,
                    typeFilter = typeFilter,
                    query = query,
                    focusedId = focusedId,
                    onFocus = { id -> focusedId = if (focusedId == id) null else id },
                    onSelect = { id -> haptic.hud(); onSelectTarget(id) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

/** 罗盘航姿条 —— 真实传感器读数：航向/俯仰/横滚/北基准/传感器状态 */
@Composable
private fun CompassStrip(vm: RadarViewModel) {
    val heading by vm.heading.collectAsState()
    val pitch by vm.pitch.collectAsState()
    val roll by vm.roll.collectAsState()
    val online by vm.compassOnline.collectAsState()
    val northRef by vm.northRef.collectAsState()

    Surface(
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, BorderDim),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "H ${heading.toInt().toString().padStart(3, '0')}°",
                    fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacGreen,
                )
                Text("航向 · $northRef", fontFamily = Sans, fontSize = 7.sp, color = Gray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%+d°".format(pitch.toInt()), fontFamily = Mono, fontSize = 11.sp, color = Gold)
                Text("俯仰", fontFamily = Sans, fontSize = 8.sp, color = Gray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%+d°".format(roll.toInt()), fontFamily = Mono, fontSize = 11.sp, color = Gold)
                Text("横滚", fontFamily = Sans, fontSize = 8.sp, color = Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (online) "陀螺仪+地磁 · 融合在线" else "传感器离线", fontFamily = Mono, fontSize = 8.sp, color = if (online) TacGreen else Danger)
                Text("旋转矢量 · ROTATION_VECTOR", fontFamily = Mono, fontSize = 8.sp, color = Gray)
            }
        }
    }
}

/** 雷达过滤条 —— 类型芯片 + 名称搜索 */
@Composable
private fun RadarFilterBar(
    typeFilter: String,
    onTypeFilter: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "全部", "wifi" to "WIFI", "bt" to "BLE").forEach { (key, label) ->
                Surface(
                    modifier = Modifier.clickable { onTypeFilter(key) },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = if (typeFilter == key) Red.copy(alpha = 0.16f) else SurfaceBg,
                    border = BorderStroke(1.dp, if (typeFilter == key) Red.copy(alpha = 0.7f) else BorderDim),
                ) {
                    Text(
                        label,
                        fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (typeFilter == key) Red else Gray,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = White),
                cursorBrush = SolidColor(Gold),
                modifier = Modifier
                    .weight(1f)
                    .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                    .background(SurfaceAlt)
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("筛选设备名…", fontFamily = Sans, fontSize = 10.sp, color = Gray.copy(alpha = 0.5f))
                    }
                    inner()
                },
            )
        }
    }
}

/**
 * 雷达扫描盘 —— 全动画 Canvas
 *
 * 扫描针由 withFrameNanos 帧循环驱动（Choreographer 逐帧回调）：
 * 物理上保证持续旋转 —— 启动 100°/s（3.6 秒一圈），待机 25°/s 慢转。
 * 所有随时间变化的值都在 draw 阶段读取：扫描期间零重组。
 * 视觉层：渐变光束 + 8 层磷光尾迹 + 针尾拖影 + 目标磷光轨迹 +
 * 目标-中心连线 + 扫过命中白闪 + 锁定框 + 声呐 ping + 噪点 + 罗盘。
 */
@Composable
private fun RadarScope(
    vm: RadarViewModel,
    running: Boolean,
    typeFilter: String,
    query: String,
    focusedId: String?,
    modifier: Modifier = Modifier,
) {
    // draw 阶段读取 targets/heading：更新只触发重绘，不触发重组
    val targets by vm.targets.collectAsState()
    val heading by vm.heading.collectAsState()
    val textMeasurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current

    // ── 帧循环驱动扫描角：不依赖 InfiniteTransition，任何环境必然旋转 ──
    var sweepDeg by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) { last = now; return@withFrameNanos }
                val dtMs = (now - last) / 1_000_000f
                last = now
                val speed = if (running) 100f else 25f // 度/秒
                val next = (sweepDeg + dtMs / 1000f * speed) % 360f
                if (next < sweepDeg && running) {
                    // 每完成一整圈扫描触发一次轻触觉反馈
                    haptic.hud(HapticFeedbackType.TextHandleMove)
                }
                sweepDeg = next
            }
        }
    }

    // ── 目标磷光轨迹：最近 8 个历史位置（目标移动留下的余晖） ──
    val trails = remember { mutableStateMapOf<String, List<TrailPoint>>() }
    LaunchedEffect(targets) {
        val current = targets.associate { t -> t.id to TrailPoint(t.angleDeg, (t.distanceM / 100f).coerceIn(0.06f, 0.94f)) }
        val merged = HashMap<String, List<TrailPoint>>()
        trails.forEach { (id, hist) ->
            val cur = current[id]
            merged[id] = if (cur != null) (hist + cur).takeLast(8) else hist.takeLast(3)
        }
        current.forEach { (id, pt) -> merged.putIfAbsent(id, listOf(pt)) }
        trails.clear()
        trails.putAll(merged)
    }

    // ── 过滤后的可见目标：聚焦目标永远显示，其余按类型/关键词过滤 ──
    val visibleTargets = targets.filter { t ->
        t.id == focusedId ||
            ((typeFilter == "all" || t.type == typeFilter) &&
                (query.isBlank() || t.name.contains(query, ignoreCase = true)))
    }
    val focusedTarget = focusedId?.let { id -> targets.firstOrNull { it.id == id } }

    // 开机辉光：启动后从中心向外扩散一次
    var powered by remember { mutableStateOf(false) }
    LaunchedEffect(running) { if (running) powered = true }

    // 背景噪点：固定位置 + 相位闪烁
    val noise = remember { List(80) { Random.nextFloat() to Random.nextFloat() } }

    Box(
        modifier
            .clip(CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp))
            .background(Dark)
            .border(1.dp, BorderDim, CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = minOf(cx, cy) * 0.88f
            val g = TacGreen

            // ── 噪点闪烁 ──
            noise.forEachIndexed { i, (nx, ny) ->
                val a = ((sin(i * 1.7f + sweepDeg * 0.35f) + 1f) / 2f) * 0.09f
                drawCircle(g.copy(alpha = a), 1f, Offset(nx * size.width, ny * size.height))
            }

            // ── 距离环 + 十字基线 ──
            listOf(0.25f, 0.5f, 0.75f, 1f).forEachIndexed { i, s ->
                drawCircle(g.copy(alpha = 0.26f), r * s, Offset(cx, cy), style = Stroke(if (i == 3) 1.6f else 0.7f))
            }
            drawLine(g.copy(alpha = 0.15f), Offset(cx - r, cy), Offset(cx + r, cy), 0.7f)
            drawLine(g.copy(alpha = 0.15f), Offset(cx, cy - r), Offset(cx, cy + r), 0.7f)

            // ── 方位刻度：随真实罗盘航向旋转，刻度永远指向真实方位 ──
            for (deg in 0 until 360 step 10) {
                val a = Math.toRadians((deg - heading).toDouble()).toFloat()
                val major = deg % 30 == 0
                val inner = r * (if (major) 0.93f else 0.965f)
                drawLine(
                    g.copy(alpha = if (major) 0.5f else 0.2f),
                    Offset(cx + inner * cos(a), cy + inner * sin(a)),
                    Offset(cx + r * cos(a), cy + r * sin(a)),
                    if (major) 1.2f else 0.5f,
                )
            }
            // 罗盘方位字：北东南西固定在真实方位，随陀螺仪反向旋转
            listOf("北" to 0f, "东" to 90f, "南" to 180f, "西" to 270f).forEach { (ch, deg) ->
                val a = Math.toRadians((deg - heading).toDouble()).toFloat()
                val lr = r * 0.83f
                val m = textMeasurer.measure(ch, TextStyle(fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = g.copy(alpha = 0.9f)))
                drawText(m, topLeft = Offset(cx + lr * cos(a) - m.size.width / 2f, cy + lr * sin(a) - m.size.height / 2f))
            }

            // ── 机头航向指针：固定在屏幕顶部，指向设备当前朝向 ──
            val bow = Path().apply {
                moveTo(cx, cy - r * 0.99f)
                lineTo(cx - 7.dp.toPx(), cy - r * 0.80f)
                lineTo(cx + 7.dp.toPx(), cy - r * 0.80f)
                close()
            }
            drawPath(bow, Gold.copy(alpha = 0.95f))

            // 磁北参考细线：从中心指向当前航向的反向（即北向方位）
            val northRad = Math.toRadians((-heading).toDouble()).toFloat()
            drawLine(g.copy(alpha = 0.18f), Offset(cx, cy), Offset(cx + r * 0.35f * cos(northRad), cy + r * 0.35f * sin(northRad)), 0.9f)

            val sweepRad = Math.toRadians(sweepDeg.toDouble()).toFloat()

            // ── 磷光尾迹：8 层扇形 ──
            for (i in 0 until 8) {
                val startDeg = sweepDeg - (i + 1) * 7f
                drawArc(
                    g.copy(alpha = (0.09f - i * 0.01f).coerceAtLeast(0.015f)),
                    startAngle = startDeg,
                    sweepAngle = 5.5f,
                    useCenter = true,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2f, r * 2f),
                )
            }

            // ── 主光束：渐变扇形（中心透亮、边缘衰减） ──
            val beam = Brush.sweepGradient(
                0f to g.copy(alpha = 0f),
                0.35f to g.copy(alpha = 0.10f),
                0.65f to g.copy(alpha = 0.30f),
                1f to g.copy(alpha = 0f),
                center = Offset(cx, cy),
            )
            drawArc(
                brush = beam,
                startAngle = sweepDeg - 30f,
                sweepAngle = 60f,
                useCenter = true,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
            )

            // ── 目标-中心连线（数据链）：随罗盘航向同步旋转 ──
            if (running || targets.isNotEmpty()) {
                visibleTargets.forEach { t ->
                    val dist = (t.distanceM / 100f).coerceIn(0.06f, 0.94f)
                    val tr = dist * r
                    val ta = Math.toRadians((t.angleDeg - heading).toDouble()).toFloat()
                    val blip = if (t.type == "wifi") Danger else TacGreen
                    drawLine(blip.copy(alpha = 0.12f), Offset(cx, cy), Offset(cx + tr * cos(ta), cy + tr * sin(ta)), 0.8f)
                }
            }

            // ── 扫描针：光晕针 + 细亮针 + 针尾拖影 + 针尖光点 ──
            val tipX = cx + r * cos(sweepRad)
            val tipY = cy + r * sin(sweepRad)
            for (i in 1..5) {
                val backRad = sweepRad - i * 0.035f
                drawLine(
                    g.copy(alpha = (0.5f - i * 0.09f).coerceAtLeast(0.05f)),
                    Offset(cx, cy),
                    Offset(cx + r * cos(backRad), cy + r * sin(backRad)),
                    (3f - i * 0.5f).coerceAtLeast(0.6f),
                )
            }
            drawLine(g.copy(alpha = 0.35f), Offset(cx, cy), Offset(tipX, tipY), 4f)
            drawLine(g.copy(alpha = 0.95f), Offset(cx, cy), Offset(tipX, tipY), 1.6f)
            drawCircle(g.copy(alpha = 0.55f), 4.dp.toPx(), Offset(tipX, tipY))
            drawCircle(Color(0xFFE8F4FF).copy(alpha = 0.95f), 1.8.dp.toPx(), Offset(tipX, tipY))

            // ── 全局声呐 ping：每 1.8 秒一发扩散环 ──
            val ping = ((sweepDeg / 360f) * 2f) % 1f
            drawCircle(g.copy(alpha = (1f - ping) * 0.30f), r * ping, Offset(cx, cy), style = Stroke(1.2f))

            // ── 开机辉光：启动后扩散 ──
            if (powered) {
                val glow = (((sweepDeg / 360f) * 6f) % 1f)
                drawCircle(g.copy(alpha = (1f - glow) * 0.22f), r * glow, Offset(cx, cy), style = Stroke(4f))
            }

            // ── 目标 blip：磷光轨迹 + 扫过点亮 + 命中白闪 + 锁定框 + 编号 ──
            // 红点（WiFi）刻意放大加强：三层光晕 + 双脉动环 + 白芯十字，一眼锁定敌目标
            if (running || targets.isNotEmpty()) {
                visibleTargets.forEachIndexed { i, t ->
                    val dist = (t.distanceM / 100f).coerceIn(0.06f, 0.94f)
                    val tr = dist * r
                    // 目标方位为世界坐标，绘制时减去航向 —— 转动机身，盘面目标随之旋转
                    val ta = Math.toRadians((t.angleDeg - heading).toDouble()).toFloat()
                    val x = cx + tr * cos(ta)
                    val y = cy + tr * sin(ta)

                    // 光束扫过窗口 ±50° 内最亮，扫过后磷光余晖
                    var d = sweepDeg - t.angleDeg
                    while (d > 180f) d -= 360f
                    while (d < -180f) d += 360f
                    val isFocused = t.id == focusedId
                    // 聚焦模式下非聚焦目标大幅降噪淡出，聚焦目标恒定高亮
                    val bright = ((1f - abs(d) / 50f).coerceIn(0.10f, 1f)) * (if (focusedId == null || isFocused) 1f else 0.16f)
                    val blip = if (t.type == "wifi") Danger else TacGreen

                    // 磷光轨迹：历史位置渐隐余晖（同样跟随航向旋转）
                    trails[t.id]?.dropLast(1)?.forEachIndexed { k, pt ->
                        val pta = Math.toRadians((pt.angleDeg - heading).toDouble()).toFloat()
                        val pxa = cx + pt.dist01 * r * cos(pta)
                        val pya = cy + pt.dist01 * r * sin(pta)
                        drawCircle(blip.copy(alpha = (0.05f + k * 0.045f) * (if (isFocused || focusedId == null) 1f else 0.16f)), 1.6.dp.toPx(), Offset(pxa, pya))
                    }

                    // 醒目的红色 blip：大光晕 + 双脉动扩散环 + 亮核
                    drawCircle(blip.copy(alpha = bright * 0.30f), 14.dp.toPx(), Offset(x, y))
                    drawCircle(blip.copy(alpha = bright * 0.55f), 7.dp.toPx(), Offset(x, y))

                    val rippleBase = ((sweepDeg / 360f) * 2f + i * 0.37f) % 1f
                    listOf(0f, 0.5f).forEach { off ->
                        val ph = (rippleBase + off) % 1f
                        val rr = 3.dp.toPx() + 30.dp.toPx() * ph
                        drawCircle(blip.copy(alpha = (1f - ph) * 0.30f * bright), rr, Offset(x, y), style = Stroke(6f))
                        drawCircle(blip.copy(alpha = (1f - ph) * 0.95f * bright), rr, Offset(x, y), style = Stroke(1.8f))
                    }

                    // 核心点 + 白芯 + 十字准星
                    drawCircle(blip.copy(alpha = bright), 3.2.dp.toPx(), Offset(x, y))
                    drawCircle(White.copy(alpha = bright * 0.9f), 1.4.dp.toPx(), Offset(x, y))
                    val cross = 8.dp.toPx()
                    drawLine(blip.copy(alpha = bright * 0.85f), Offset(x - cross, y), Offset(x + cross, y), 1.2f)
                    drawLine(blip.copy(alpha = bright * 0.85f), Offset(x, y - cross), Offset(x, y + cross), 1.2f)

                    // 命中白闪：光束扫过瞬间的白色爆闪
                    if (bright > 0.85f) {
                        val flash = (bright - 0.85f) / 0.15f
                        drawCircle(White.copy(alpha = flash * 0.9f), 8.dp.toPx(), Offset(x, y))
                        drawLine(White.copy(alpha = flash * 0.8f), Offset(x - 11.dp.toPx(), y), Offset(x + 11.dp.toPx(), y), 1.4f)
                        drawLine(White.copy(alpha = flash * 0.8f), Offset(x, y - 11.dp.toPx()), Offset(x, y + 11.dp.toPx()), 1.4f)
                    }

                    // 聚焦目标：恒定金色锁定框 + 名称/RSSI/距离标签
                    if (isFocused) {
                        val lockSize = 42.dp.toPx()
                        withTransform({
                            translate(x - lockSize / 2f, y - lockSize / 2f)
                        }) {
                            drawTacticalCorners(
                                Gold.copy(alpha = 0.95f),
                                Size(lockSize, lockSize),
                                len = 10.dp.toPx(),
                                stroke = 1.8.dp.toPx(),
                            )
                        }
                        val tag = textMeasurer.measure(
                            "${t.name.take(12)} · ${t.rssi}dBm · %.1fm".format(t.distanceM),
                            TextStyle(fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold),
                        )
                        drawText(tag, topLeft = Offset(x - tag.size.width / 2f, y + 24.dp.toPx()))
                    } else if (bright > 0.55f) {
                        // 普通目标锁定框：光束扫过时弹出四角括号（全息 HUD 锁定）
                        val lockSize = 26.dp.toPx()
                        withTransform({
                            translate(x - lockSize / 2f, y - lockSize / 2f)
                        }) {
                            drawTacticalCorners(
                                blip.copy(alpha = (bright - 0.55f) / 0.45f),
                                Size(lockSize, lockSize),
                                len = 7.dp.toPx(),
                                stroke = 1.3.dp.toPx(),
                            )
                        }
                    }

                    // 编号标签
                    val tag = textMeasurer.measure("${i + 1}", TextStyle(fontFamily = Mono, fontSize = 9.sp, color = blip.copy(alpha = bright)))
                    drawText(tag, topLeft = Offset(x + 10.dp.toPx(), y - 18.dp.toPx()))
                }
            }

            // ── 中心原点（呼吸） ──
            drawCircle(g.copy(alpha = 0.45f + 0.35f * sin(sweepDeg * 0.3f)), 2.dp.toPx(), Offset(cx, cy))

            // ── 底部 CRT 状态行（画布内绘制，随扫描刷新且零重组） ──
            val statusY = size.height - 8.dp.toPx()
            val modeTxt = if (running) "模式: 扫描" else "模式: 待机"
            val azTxt = "扫描: %03d°".format(sweepDeg.toInt() % 360)
            val hdTxt = "航向: %03d°".format(heading.toInt() % 360)
            val status = textMeasurer.measure(
                "$modeTxt   $azTxt   $hdTxt   量程: 100m   目标: ${visibleTargets.size}/${targets.size}",
                TextStyle(fontFamily = Mono, fontSize = 9.sp, color = g.copy(alpha = 0.85f)),
            )
            drawText(status, topLeft = Offset((size.width - status.size.width) / 2f, statusY))
        }
    }
}

/** 雷达目标磷光轨迹点（角度 + 归一化距离） */
private data class TrailPoint(val angleDeg: Float, val dist01: Float)

/** 雷达目标列表面板 —— 过滤 + 聚焦降噪 + 点选展开内联技术详情 */
@Composable
private fun RadarTrackPanel(
    vm: RadarViewModel,
    typeFilter: String,
    query: String,
    focusedId: String?,
    onFocus: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targets by vm.targets.collectAsState()
    val filtered = targets.filter { t ->
        t.id == focusedId ||
            ((typeFilter == "all" || t.type == typeFilter) &&
                (query.isBlank() || t.name.contains(query, ignoreCase = true)))
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Star(Gold, 8.dp)
            Spacer(Modifier.width(7.dp))
            Text("目标列表 · ${filtered.size}/${targets.size}", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
            Spacer(Modifier.weight(1f))
            if (focusedId != null) {
                Text("聚焦模式 · 降噪中", fontFamily = Mono, fontSize = 8.sp, color = Gold)
            }
        }
        Spacer(Modifier.height(4.dp))
        if (filtered.isEmpty()) {
            EmptyHint(if (targets.isEmpty()) "启动雷达后，检测到的设备会亮在扫描盘上" else "过滤条件下没有匹配设备")
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(
                    // 类型前缀 + 去重:BLE 扫描与经典发现可能瞬时上报同一地址,
                    // 重复 key 会让 Compose 抛 "Key was already used" 崩溃
                    filtered.sortedByDescending { it.rssi }.distinctBy { "${it.type}_${it.id}" },
                    key = { "${it.type}_${it.id}" },
                ) { t ->
                    val wifi = t.type == "wifi"
                    val barColor = if (t.rssi > -50) Danger else if (t.rssi > -70) Warn else TacGreenDim
                    val expanded = t.id == focusedId
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onFocus(t.id) },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = if (expanded) TacGreen.copy(alpha = 0.06f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (expanded) Gold.copy(alpha = 0.7f) else BorderDim),
                    ) {
                        Column {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (wifi) "WIFI" else "BLE", fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (wifi) Danger else TacGreen, modifier = Modifier.width(34.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.name.take(16), fontFamily = Sans, fontSize = 11.sp, color = White)
                                    Spacer(Modifier.height(3.dp))
                                    // 信号强度条
                                    Box(Modifier.fillMaxWidth(0.72f).height(3.dp).clip(CutCornerShape(topStart = 2.dp, bottomEnd = 2.dp)).background(SurfaceAlt)) {
                                        Box(
                                            Modifier.fillMaxWidth(((t.rssi + 100f) / 80f).coerceIn(0.06f, 1f)).height(3.dp)
                                                .clip(CutCornerShape(topStart = 2.dp, bottomEnd = 2.dp)).background(barColor),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${t.rssi}dBm", fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (t.rssi > -50) Danger else if (t.rssi > -70) Warn else Gray)
                                    Text("%.1fm · %03d°".format(t.distanceM, t.angleDeg.toInt()), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                                }
                            }
                            // ── 展开的内联技术详情：聚焦即降噪，详情就地展开 ──
                            if (expanded) {
                                Column(Modifier.padding(horizontal = 10.dp).padding(bottom = 8.dp)) {
                                    InlineInfoRow("标识", if (wifi) t.id else t.address)
                                    InlineInfoRow("厂商", "${t.vendor} · OUI ${t.vendorOui}")
                                    InlineInfoRow("信号", "RSSI ${t.rssi}dBm · SNR ${t.snrDb}dB · 底噪 ${t.noiseDbm}dBm")
                                    if (wifi) {
                                        InlineInfoRow("射频", "${t.frequencyMhz}MHz · ${t.channel} · ${t.channelWidth}")
                                        InlineInfoRow("安全", t.security.ifEmpty { "--" })
                                    } else {
                                        InlineInfoRow("类别", "${t.deviceClassMajor}${if (t.deviceClassDetail.isNotEmpty()) " · ${t.deviceClassDetail}" else ""}")
                                        InlineInfoRow("链路", "${t.linkType} · 绑定: ${t.bondState}")
                                    }
                                    InlineInfoRow("跟踪", "命中 ${t.hitCount} 次 · 首见 ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(t.firstSeenMs))}")
                                    Spacer(Modifier.height(5.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            modifier = Modifier.weight(1f).clickable { onSelect(t.id) },
                                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                            color = Red.copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                                        ) {
                                            Text("打开完整档案 ▸", fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
                                        }
                                        Surface(
                                            modifier = Modifier.weight(1f).clickable { onFocus(t.id) },
                                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                            color = Color.Transparent,
                                            border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f)),
                                        ) {
                                            Text("取消聚焦", fontFamily = Sans, fontSize = 10.sp, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 内联详情行 —— 标签灰、数值白，等宽小字 */
@Composable
private fun InlineInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.width(38.dp))
        Text(value, fontFamily = Mono, fontSize = 8.sp, color = White, modifier = Modifier.weight(1f))
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 文件快传
// ══════════════════════════════════════════════════════════════════════════

/** 文件快传页 —— 局域网文件传输 */
@Composable
fun FileTransferScreen(vm: FileTransferViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current
    var manualIp by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.sendFile(it, manualIp) } }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("文件快传", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                ActionButton(running, "执行快传", "停止快传") {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                error?.let { ErrorBanner(it) }

                if (running) {
                    Spacer(Modifier.height(6.dp))
                    Text("本机地址: ${vm.localIp}", fontFamily = Sans, fontSize = 10.sp, color = Gray)
                    // 高频数据区独立订阅：UDP 发现期间设备列表与发送计数持续刷新
                    FileTransferLivePanel(vm, manualIp = manualIp, onManualIp = { manualIp = it }, picker = picker)
                }

                FileTransferInboxPanel(vm)
            }
        }
    }
}

/** 文件快传实时面板 —— 独立订阅设备发现与发送计数 */
@Composable
private fun FileTransferLivePanel(
    vm: FileTransferViewModel,
    manualIp: String,
    onManualIp: (String) -> Unit,
    picker: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Uri?>,
) {
    val peers by vm.peers.collectAsState()
    val sent by vm.sentCount.collectAsState()

    Column {
        if (peers.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            TermPanel("已发现设备 (${peers.size})") {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 100.dp)) {
                    items(peers, key = { it.ip }) { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name.take(16), fontFamily = Mono, fontSize = 10.sp, color = White, modifier = Modifier.weight(1f))
                            Text(p.ip, fontFamily = Mono, fontSize = 10.sp, color = Gray)
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(6.dp))
            HintBreathing("未发现目标 —— 输入对方 IP 手动投递")
            Spacer(Modifier.height(6.dp))
            TermInput("对方 IP", manualIp, placeholder = "192.168.x.x") { onManualIp(it) }
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { picker.launch(arrayOf("*/*")) },
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = Red.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Red.copy(alpha = 0.4f)),
        ) {
            Text(
                if (peers.isNotEmpty()) "发送文件 → 所有设备" else if (manualIp.isNotBlank()) "发送文件 → $manualIp" else "选择文件发送",
                fontFamily = Mono, fontSize = 13.sp, color = Red, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center,
            )
        }
        Text("已发送: $sent 个文件", fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 文件快传收件面板 —— 独立订阅收到的文件 */
@Composable
private fun FileTransferInboxPanel(vm: FileTransferViewModel) {
    val received by vm.received.collectAsState()
    val ctx = LocalContext.current
    if (received != null) {
        Spacer(Modifier.height(8.dp))
        TermPanel("收到文件") {
            Text("${received!!.name} (${received!!.length() / 1024}KB)", fontFamily = Mono, fontSize = 11.sp, color = White)
            Spacer(Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", received!!)
                    ctx.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "*/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
                },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = Red.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, Red.copy(alpha = 0.3f)),
            ) {
                Text("打开文件", fontFamily = Sans, fontSize = 11.sp, color = Red, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 加密保险箱
// ══════════════════════════════════════════════════════════════════════════

/** 加密保险箱 —— 拍摄/录音自动加密，输入密码查看解密内容 */
@Composable
fun SecureVaultScreen(vm: SecureMediaViewModel, back: () -> Unit) {
    val entries by vm.entries.collectAsState(); val recording by vm.isRecording.collectAsState()
    val decrypted by vm.decryptedFile.collectAsState(); val error by vm.error.collectAsState()
    val pwd by vm.passphrase.collectAsState()
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onMediaCaptured() }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onMediaCaptured() }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importFile(it) } }
    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.createPhotoIntent()?.let { photoLauncher.launch(it) }
    }
    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("加密保险箱", back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput("密码", pwd, placeholder = "加密/解密密码") { vm.setPassphrase(it) }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(modifier = Modifier.weight(1f).clickable {
                        haptic.hud()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                            ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            camPermLauncher.launch(android.Manifest.permission.CAMERA)
                        } else vm.createPhotoIntent()?.let { photoLauncher.launch(it) }
                    }, shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = Red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Red.copy(alpha = 0.4f))) {
                        Text("拍照", fontFamily = Sans, fontSize = 12.sp, color = Red, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    Surface(modifier = Modifier.weight(1f).clickable {
                        haptic.hud()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                            ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            camPermLauncher.launch(android.Manifest.permission.CAMERA)
                        } else vm.createVideoIntent()?.let { videoLauncher.launch(it) }
                    }, shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = Red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Red.copy(alpha = 0.4f))) {
                        Text("摄像", fontFamily = Sans, fontSize = 12.sp, color = Red, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    Surface(modifier = Modifier.weight(1f).clickable {
                        haptic.hud()
                        if (recording) { vm.stopRecording(); return@clickable }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                            ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        } else vm.startRecording()
                    }, shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = if (recording) Red else Red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Red.copy(alpha = if (recording) 0.8f else 0.4f))) {
                        Text(if (recording) "停止" else "录音", fontFamily = Mono, fontSize = 12.sp, color = if (recording) White else Red, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Surface(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("*/*")) }, shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = Red.copy(alpha = 0.08f), border = BorderStroke(1.dp, Red.copy(alpha = 0.25f))) {
                    Text("导入文件加密", fontFamily = Sans, fontSize = 11.sp, color = RedDim, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
                }

                error?.let { ErrorBanner(it) }
                Spacer(Modifier.height(8.dp))
                Text("保险库文件 · ${entries.size} 个", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(4.dp))

                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(entries) { e ->
                        Row(Modifier.fillMaxWidth().clickable { vm.decrypt(e) }.padding(vertical = 5.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (e.type == "photo") "[图片]" else if (e.type == "video") "[视频]" else "[音频]", fontFamily = Mono, fontSize = 10.sp, color = RedDim, modifier = Modifier.width(52.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.fileName, fontFamily = Mono, fontSize = 11.sp, color = White)
                                Text("${e.sizeBytes / 1024}KB", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                            }
                            GhostButton("删除") { vm.delete(e) }
                        }
                    }
                }

                if (decrypted != null) {
                    Spacer(Modifier.height(6.dp))
                    TermPanel("解密成功") {
                        Text("${decrypted!!.name} (${decrypted!!.length() / 1024}KB)", fontFamily = Mono, fontSize = 12.sp, color = White)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(modifier = Modifier.weight(1f).clickable {
                                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", decrypted!!)
                                ctx.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "*/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
                            }, shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = Red, border = BorderStroke(1.dp, Red)) {
                                Text("打开", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                            Surface(modifier = Modifier.weight(1f).clickable { vm.clearDecrypted() }, shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = Color.Transparent, border = BorderStroke(1.dp, Red.copy(alpha = 0.3f))) {
                                Text("清除", fontFamily = Sans, fontSize = 12.sp, color = Red, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 反弹 Shell
// ══════════════════════════════════════════════════════════════════════════

/** 反弹 Shell 监听器 —— nc 风格监听 + 远程命令交互 */
@Composable
fun RevShellScreen(vm: RevShellViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState(); val connected by vm.connected.collectAsState()
    val port by vm.port.collectAsState(); val cmd by vm.cmd.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("反弹 Shell", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput("监听端口", port, placeholder = "例如 4444") { vm.setPort(it) }
                Spacer(Modifier.height(8.dp))
                ActionButton(running, "执行监听", "停止监听") {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = if (connected) RedDim else Red, trackColor = Track)
                    Text("监听 0.0.0.0:$port · ${if (connected) "已连接" else "等待连接"}", fontFamily = Mono, fontSize = 11.sp, color = if (connected) Red else White, modifier = Modifier.padding(top = 3.dp))
                }

                Spacer(Modifier.height(8.dp))
                // 高频数据区独立订阅：会话建立后日志持续流入
                RevShellLogPanel(vm, modifier = Modifier.weight(1f))
                Spacer(Modifier.height(6.dp))
                if (connected) {
                    TermInput("命令", cmd, placeholder = "whoami / id / ls -la") { vm.setCmd(it) }
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { vm.send() },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = Red.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Red.copy(alpha = 0.4f)),
                    ) {
                        Text("发送命令", fontFamily = Sans, fontSize = 12.sp, color = Red, modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** 反弹 Shell 会话日志 —— 独立订阅会话流，自动滚动用非动画定位避免堆积 */
@Composable
private fun RevShellLogPanel(vm: RevShellViewModel, modifier: Modifier = Modifier) {
    val logs by vm.logs.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) list.scrollToItem(logs.size - 1) }
    Text("会话日志", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
    Spacer(Modifier.height(4.dp))
    LazyColumn(state = list, modifier = Modifier.fillMaxWidth().then(modifier)) {
        if (logs.isEmpty()) item { HintBreathing("等待目标上线，会话内容会显示在这里") }
        items(logs) { l ->
            LogLine(
                if (l.direction == "in") "收到" else if (l.direction == "out") "发出" else "系统",
                l.text,
                when (l.direction) { "in" -> TacGreen; "out" -> Red; else -> Gray },
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Payload 生成器
// ══════════════════════════════════════════════════════════════════════════

/** Payload 生成器 —— 一键生成多语言反弹 Shell 载荷 */
@Composable
fun PayloadScreen(vm: PayloadViewModel, back: () -> Unit) {
    val ip by vm.ip.collectAsState(); val port by vm.port.collectAsState()
    val payloads by vm.payloads.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("Payload 生成器", back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    TermInput("目标 IP", ip, modifier = Modifier.weight(1f)) { vm.setIp(it) }
                    Spacer(Modifier.width(8.dp))
                    TermInput("端口", port, modifier = Modifier.weight(1f)) { vm.setPort(it) }
                }
                Spacer(Modifier.height(8.dp))
                ActionButton(running = false, runLabel = "生成全部载荷", stopLabel = "") {
                    haptic.hud()
                    vm.generate()
                }
                Spacer(Modifier.height(8.dp))
                Text("已生成 ${payloads.size} 个载荷 · 目标 $ip:$port", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(payloads) { p ->
                        Spacer(Modifier.height(4.dp))
                        TermPanel("${p.name} (${p.lang})") {
                            SelectionContainer { Text(p.template, fontFamily = Mono, fontSize = 9.sp, color = White) }
                        }
                    }
                    if (payloads.isEmpty()) item { HintBreathing("填写目标 IP 与端口后点击生成，所有语言的载荷会在这里列出") }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 目录爆破
// ══════════════════════════════════════════════════════════════════════════

/** 目录爆破 —— gobuster 风格 HTTP 目录枚举 */
@Composable
fun DirBruteScreen(vm: DirBruteViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState(); val url by vm.url.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("目录爆破", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput("目标 URL", url, placeholder = "http://example.com") { vm.setUrl(it) }
                Spacer(Modifier.height(8.dp))
                ActionButton(running, "执行枚举", "停止枚举") {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                // 高频数据区独立订阅：枚举期间进度与命中路径持续刷新
                DirBruteLivePanel(vm, running = running)
            }
        }
    }
}

/** 目录爆破实时面板 —— 独立订阅进度与命中结果 */
@Composable
private fun DirBruteLivePanel(vm: DirBruteViewModel, running: Boolean) {
    val results by vm.results.collectAsState()
    val (done, total) = vm.progress.collectAsState().value

    Column {
        if (running) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { done.toFloat() / maxOf(1, total) }, modifier = Modifier.fillMaxWidth(), color = Red, trackColor = Track)
            Text("已测试 $done/$total 个路径 · 命中 ${results.size}", fontFamily = Sans, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 3.dp))
        }
        if (!running && results.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            HintBreathing("输入目标 URL 后启动枚举，命中路径会实时列出")
        }
        Spacer(Modifier.height(8.dp))
        Text("命中路径 · ${results.size}", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(results, key = { it.path }) { r ->
                LogLine(
                    "${r.status}",
                    "${r.path}  ${r.size}b  ${r.timeMs}ms",
                    if (r.status == 200) Red else if (r.status in 301..303) Warn else White,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// CVE 搜索
// ══════════════════════════════════════════════════════════════════════════

/** CVE 搜索 —— 漏洞数据库查询 */
@Composable
fun CVEScreen(vm: CVESearchViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState(); val query by vm.query.collectAsState()
    val results by vm.results.collectAsState(); val status by vm.status.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("CVE 搜索", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput("搜索关键词", query, placeholder = "CVE 编号 / 厂商 / 产品名") { vm.setQuery(it) }
                Spacer(Modifier.height(8.dp))
                ActionButton(running = running, runLabel = "执行搜索", stopLabel = "搜索中…") {
                    haptic.hud()
                    vm.search()
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                }
                if (status.isNotBlank()) Text(status, fontFamily = Mono, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 4.dp))
                if (!running && results.isEmpty() && status.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    HintBreathing("输入 CVE 编号、厂商或产品名后搜索")
                }
                Spacer(Modifier.height(8.dp))
                Text("搜索结果 · ${results.size}", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(results) { r ->
                        Spacer(Modifier.height(4.dp))
                        TermPanel("${r.id}  ${if (r.cvss != null) "CVSS ${"%.1f".format(r.cvss)}" else ""}") {
                            Text(r.description, fontFamily = Mono, fontSize = 10.sp, color = White)
                            Spacer(Modifier.height(4.dp))
                            Text("发布时间: ${r.published}", fontFamily = Sans, fontSize = 9.sp, color = Gray)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 设置 · 关于 —— 已迁移至 SettingsScreen.kt（操作员偏好 + 队伍主题 + 触觉 + 系统信息）
// ══════════════════════════════════════════════════════════════════════════

// ─── Android Studio 预览 ──────────────────────────────────────────────────────
//
// 每个详情页一个预览入口。真机预览（Preview on device）模式下 ViewModel
// 可正常构造，静态预览降级为提示卡片。
// 统一参数：Pixel 屏幕尺寸 + 中文环境 + 夜幕黑画布。

@Preview(name = "BLE 泛洪引擎", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun BleDetailPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vm = if (app != null) previewViewModel { BleSpamViewModel(app) } else null
        if (vm == null) PreviewFallback("BLE 泛洪引擎") else BleDetailScreen(vm, back = {})
    }
}

@Preview(name = "蓝牙压制引擎", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun BtJamDetailPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vm = if (app != null) previewViewModel { BluetoothJammerViewModel(app) } else null
        if (vm == null) PreviewFallback("蓝牙压制引擎") else BtJamDetailScreen(vm, back = {})
    }
}

@Preview(name = "WiFi 干扰引擎", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun WifiDetailPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vm = if (app != null) previewViewModel { WifiDisruptViewModel(app) } else null
        if (vm == null) PreviewFallback("WiFi 干扰引擎") else WifiDetailScreen(vm, back = {})
    }
}

@Preview(name = "WiFi 压制引擎", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun WifiJamDetailPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vm = if (app != null) previewViewModel { WifiJammerViewModel(app) } else null
        if (vm == null) PreviewFallback("WiFi 压制引擎") else WifiJamDetailScreen(vm, back = {})
    }
}

@Preview(name = "WEB 服务器", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun WebServerPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vm = if (app != null) previewViewModel { WebServerViewModel(app) } else null
        if (vm == null) PreviewFallback("WEB 服务器") else WebServerDetailScreen(vm, back = {})
    }
}

@Preview(name = "ARP 网络扫描", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun ArpPreview() {
    PreviewTheme {
        val vm = previewViewModel { ArpScanViewModel() }
        if (vm == null) PreviewFallback("ARP 网络扫描") else ArpDetailScreen(vm, back = {})
    }
}

@Preview(name = "BLE 嗅探扫描", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun BleScannerPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vm = if (app != null) previewViewModel { BleScannerViewModel(app) } else null
        if (vm == null) PreviewFallback("BLE 嗅探扫描") else BleScannerScreen(vm, back = {})
    }
}

@Preview(name = "PING 泛洪", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun PingPreview() {
    PreviewTheme {
        val vm = previewViewModel { PingViewModel() }
        if (vm == null) PreviewFallback("PING 泛洪") else PingScreen(vm, back = {})
    }
}

@Preview(name = "HTTP 客户端", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun HttpClientPreview() {
    PreviewTheme {
        val vm = previewViewModel { HttpClientViewModel() }
        if (vm == null) PreviewFallback("HTTP 客户端") else HttpClientScreen(vm, back = {})
    }
}

@Preview(name = "端口扫描", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun PortScannerPreview() {
    PreviewTheme {
        val vm = previewViewModel { PortScannerViewModel() }
        if (vm == null) PreviewFallback("端口扫描") else PortScannerScreen(vm, back = {})
    }
}

@Preview(name = "WiFi 对讲机", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun WalkieTalkiePreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vm = if (app != null) previewViewModel { WalkieTalkieViewModel(app) } else null
        if (vm == null) PreviewFallback("WiFi 对讲机") else WalkieTalkieScreen(vm, back = {})
    }
}

@Preview(name = "设备雷达", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun RadarPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vm = if (app != null) previewViewModel { RadarViewModel(app) } else null
        if (vm == null) PreviewFallback("设备雷达") else RadarScreen(vm, back = {}, radarAlertOn = true, onSelectTarget = {})
    }
}

@Preview(name = "文件快传", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun FileTransferPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vm = if (app != null) previewViewModel { FileTransferViewModel(app) } else null
        if (vm == null) PreviewFallback("文件快传") else FileTransferScreen(vm, back = {})
    }
}

@Preview(name = "加密保险箱", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun SecureVaultPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vm = if (app != null) previewViewModel { SecureMediaViewModel(app) } else null
        if (vm == null) PreviewFallback("加密保险箱") else SecureVaultScreen(vm, back = {})
    }
}

@Preview(name = "反弹 Shell", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun RevShellPreview() {
    PreviewTheme {
        val vm = previewViewModel { RevShellViewModel() }
        if (vm == null) PreviewFallback("反弹 Shell") else RevShellScreen(vm, back = {})
    }
}

@Preview(name = "Payload 生成器", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun PayloadPreview() {
    PreviewTheme {
        val vm = previewViewModel { PayloadViewModel() }
        if (vm == null) PreviewFallback("Payload 生成器") else PayloadScreen(vm, back = {})
    }
}

@Preview(name = "目录爆破", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun DirBrutePreview() {
    PreviewTheme {
        val vm = previewViewModel { DirBruteViewModel() }
        if (vm == null) PreviewFallback("目录爆破") else DirBruteScreen(vm, back = {})
    }
}

@Preview(name = "CVE 搜索", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun CVEPreview() {
    PreviewTheme {
        val vm = previewViewModel { CVESearchViewModel() }
        if (vm == null) PreviewFallback("CVE 搜索") else CVEScreen(vm, back = {})
    }
}

@Preview(name = "操作员设置", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun SettingsPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vm = if (app != null) previewViewModel { SettingsViewModel(app) } else null
        if (vm == null) PreviewFallback("操作员设置") else SettingsScreen(vm, back = {})
    }
}
