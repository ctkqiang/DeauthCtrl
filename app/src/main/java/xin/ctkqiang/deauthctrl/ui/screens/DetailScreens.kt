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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import xin.ctkqiang.deauthctrl.manager.HttpClientManager
import xin.ctkqiang.deauthctrl.manager.JammerScanState
import xin.ctkqiang.deauthctrl.manager.AdStructureDecoder
import xin.ctkqiang.deauthctrl.manager.DeviceIntel
import xin.ctkqiang.deauthctrl.manager.WifiDeauthEngine
import xin.ctkqiang.deauthctrl.manager.GnssSat
import xin.ctkqiang.deauthctrl.manager.GnssSkyManager
import xin.ctkqiang.deauthctrl.manager.RadarTarget
import xin.ctkqiang.deauthctrl.manager.WalkieRttStatus
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
import xin.ctkqiang.deauthctrl.viewmodel.BearingEstimate
import xin.ctkqiang.deauthctrl.viewmodel.BleScannerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BleSpamViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BluetoothJammerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.CVESearchViewModel
import xin.ctkqiang.deauthctrl.viewmodel.DbViewModel
import xin.ctkqiang.deauthctrl.viewmodel.DirBruteViewModel
import xin.ctkqiang.deauthctrl.viewmodel.FileTransferViewModel
import xin.ctkqiang.deauthctrl.viewmodel.HttpClientViewModel
import xin.ctkqiang.deauthctrl.viewmodel.LocState
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

                // ── 真实注入引擎:root + 监控模式芯片才可用,无则如实显示不可用 ──
                DeauthInjectionPanel(vm, preselectBssid = sel?.bssid, preselectChannel = sel?.channel)
            }
        }
    }
}

/**
 * 真实注入面板 —— root 门控的 aireplay-ng/nexutil 驱动
 *
 * 平台前提(不满足即不可用,面板如实呈现):
 * - root(su) + 支持监控模式的芯片/固件(Nexmon/QCACLD 类补丁)
 * - aircrack-ng 工具链(Android 移植版,如 kriswebdev/android_aircrack)
 * 无 root 的 stock Android 无法注入 802.11 管理帧(SELinux 拒绝 raw socket),
 * 面板只显示探测结果与安装指引,不假装可用。
 */
@Composable
private fun DeauthInjectionPanel(
    vm: WifiDisruptViewModel,
    preselectBssid: String?,
    preselectChannel: Int?,
) {
    val session by vm.deauthSession.collectAsState()
    val haptic = LocalHapticFeedback.current
    var bssid by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("64") }
    var startError by remember { mutableStateOf<String?>(null) }

    // 扫描列表选中目标后自动回填 BSSID/信道(用户仍可手改)
    LaunchedEffect(preselectBssid, preselectChannel) {
        if (bssid.isBlank() && preselectBssid != null) bssid = preselectBssid
        if (channel.isBlank() && preselectChannel != null) channel = preselectChannel.toString()
    }

    Spacer(Modifier.height(8.dp))
    TermPanel("真实注入 · DEAUTH INJECTION") {
        Text(
            "root + 监控模式芯片才能启用;无 root 的 stock Android 无法注入 802.11 管理帧",
            fontFamily = Sans, fontSize = 8.sp, color = Warn, lineHeight = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickNavChip("探测能力", Gold, Modifier.weight(1f)) {
                haptic.hud()
                vm.probeDeauth()
            }
            if (session.running) {
                QuickNavChip("■ 停止注入", Danger, Modifier.weight(1f)) {
                    haptic.hud()
                    vm.stopDeauth()
                }
            }
        }
        session.probe?.let { p ->
            Spacer(Modifier.height(5.dp))
            Text(p.detail, fontFamily = Mono, fontSize = 8.sp, color = if (p.rootGranted && p.monitorSupported) TacGreen else Gray)
            if (p.rootGranted && (!p.aireplay || !p.monitorSupported)) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "缺少:${if (!p.aireplay) " aireplay-ng" else ""}${if (!p.monitorSupported) " 监控模式固件(Nexmon/QCACLD)" else ""} —— 安装后重新探测",
                    fontFamily = Sans, fontSize = 8.sp, color = Warn,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        TermInput("目标 BSSID", bssid, placeholder = "AA:BB:CC:DD:EE:FF") { bssid = it; startError = null }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TermInput("信道", channel, placeholder = "如 6", modifier = Modifier.weight(1f)) { channel = it }
            TermInput("客户端 MAC(可选)", client, placeholder = "留空=广播全部", modifier = Modifier.weight(1.6f)) { client = it }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TermInput("每轮帧数", count, placeholder = "0=持续循环", modifier = Modifier.weight(1f)) { count = it }
            Surface(
                modifier = Modifier.weight(1f).clickable {
                    haptic.hud()
                    startError = vm.startDeauth(
                        bssid.trim(),
                        channel.trim().toIntOrNull() ?: 0,
                        client.trim().ifBlank { null },
                        count.trim().toIntOrNull() ?: 0,
                        0L,
                    )
                },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = if (session.running) TacGreen.copy(alpha = 0.1f) else Red.copy(alpha = 0.14f),
                border = BorderStroke(1.5.dp, if (session.running) TacGreen.copy(alpha = 0.7f) else Red.copy(alpha = 0.6f)),
            ) {
                Text(
                    if (session.running) "注入中 · ${session.framesSent} 帧" else "开始注入",
                    fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (session.running) TacGreen else Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 13.dp),
                )
            }
        }
        startError?.let {
            Spacer(Modifier.height(4.dp))
            Text("[!] $it", fontFamily = Sans, fontSize = 9.sp, color = Danger)
        }
        // ── 被动嗅探:同二进制 capture 模式,信标/探测/去认证帧流式日志 ──
        Spacer(Modifier.height(6.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clickable {
                haptic.hud()
                if (session.running) {
                    vm.stopDeauth()
                } else {
                    startError = vm.startCapture(channel.trim().toIntOrNull() ?: 0)
                }
            },
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = if (session.running && session.phase == "被动嗅探") Gold.copy(alpha = 0.1f) else Color.Transparent,
            border = BorderStroke(1.dp, if (session.running && session.phase == "被动嗅探") Gold.copy(alpha = 0.6f) else BorderDim),
        ) {
            Text(
                if (session.running && session.phase == "被动嗅探") "■ 停止嗅探 · ${session.framesSent} 帧"
                else "被动嗅探 · 信标/探测/去认证帧 (capture)",
                fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = if (session.running && session.phase == "被动嗅探") Gold else Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        // 会话日志(最近 12 行)
        if (session.log.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            session.log.takeLast(12).forEach { line ->
                Text(
                    "▸ ${line.text}",
                    fontFamily = Mono, fontSize = 7.5.sp, color = if (line.text.startsWith("✖")) Danger else Gray,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "注入会话由 su 驱动 aireplay-ng 执行;仅用于授权测试,目标必须是你拥有或获授权的网络",
            fontFamily = Sans, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.75f), lineHeight = 11.sp,
        )
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
    val kickHaptic = LocalHapticFeedback.current
    // ARP 踢人(root 门控 ARP 投毒):进程级引擎单例
    val kickContext = LocalContext.current
    val deauth = remember { WifiDeauthEngine.get(kickContext) }
    val deauthSession by deauth.session.collectAsState()
    var kickTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // (ip, hostname)
    var kickGateway by remember { mutableStateOf("") }
    var kickResult by remember { mutableStateOf<String?>(null) }
    // 网关默认值:本机 DHCP 网关
    LaunchedEffect(Unit) {
        if (kickGateway.isBlank()) {
            val wifi = kickContext.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val gw = wifi?.dhcpInfo?.gateway ?: 0
            if (gw != 0) {
                kickGateway = "%d.%d.%d.%d".format(
                    gw and 0xFF, (gw shr 8) and 0xFF, (gw shr 16) and 0xFF, (gw shr 24) and 0xFF,
                )
            }
        }
    }

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
                devices.take(ARP_DEVICE_RENDER_LIMIT).forEachIndexed { i, d ->
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                        color = SurfaceAlt.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, BorderDim.copy(alpha = 0.6f)),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("0${i + 1}".takeLast(2), fontFamily = Mono, fontSize = 9.sp, color = Gold, modifier = Modifier.width(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.ip, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                                Text(
                                    "${d.mac.ifEmpty { "MAC 获取中" }} · ${d.hostname.ifEmpty { "主机名未知" }}",
                                    fontFamily = Mono, fontSize = 8.sp,
                                    color = if (d.mac.isNotEmpty()) Gray else RedDim.copy(alpha = 0.6f),
                                )
                            }
                            // 在线状态点:有 MAC 即完成解析
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (d.mac.isNotEmpty()) TacGreen else Warn.copy(alpha = 0.6f)))
                            Spacer(Modifier.width(6.dp))
                            Text(d.latency.ifBlank { "…" }, fontFamily = Mono, fontSize = 9.sp, color = Gray, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                            Spacer(Modifier.width(6.dp))
                            // 踢下线:root 门控 ARP 投毒(网关缓存投毒,仅授权测试)
                            Surface(
                                modifier = Modifier.clickable {
                                    kickHaptic.hud()
                                    kickTarget = d.ip to d.hostname
                                    kickResult = null
                                },
                                shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                                color = Danger.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, Danger.copy(alpha = 0.4f)),
                            ) {
                                Text("踢", fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Danger, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                            }
                        }
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

        // ── ARP 踢人对话框 ──
        kickTarget?.let { (ip, host) ->
            androidx.compose.ui.window.Dialog(onDismissRequest = { kickTarget = null }) {
                Surface(
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.6f)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("踢下线 · $ip", fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Danger)
                        Spacer(Modifier.height(4.dp))
                        Text("主机名: ${host.ifBlank { "未知" }}", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                        Spacer(Modifier.height(10.dp))
                        TermInput("网关 IP", kickGateway, placeholder = "如 192.168.1.1") { kickGateway = it }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "机制: 持续向目标广播伪造 ARP 应答(宣称网关在假 MAC),目标 ARP 缓存被投毒后上行流量黑洞,等效断网。\n\n" +
                                "前提与边界: 需要 root(部分设备 SELinux 拒绝 AF_PACKET 会如实报错);仅对动态 ARP 缓存客户端有效,启用防欺骗的设备免疫;停止后目标缓存过期(30-60s)自动恢复。这是主动干扰 —— 仅限你拥有或获授权的网络。",
                            fontFamily = Sans, fontSize = 8.5.sp, color = Gray, lineHeight = 13.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        kickResult?.let { Text(it, fontFamily = Sans, fontSize = 9.sp, color = if (it.startsWith("[!]")) Danger else TacGreen) }
                        if (deauthSession.running && deauthSession.phase == "ARP 踢人") {
                            Spacer(Modifier.height(4.dp))
                            Text("投毒中 · ${deauthSession.framesSent} 帧", fontFamily = Mono, fontSize = 9.sp, color = Gold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                modifier = Modifier.weight(1f).clickable { kickHaptic.hud(); kickTarget = null },
                                shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, BorderDim),
                            ) {
                                Text("取消", fontFamily = Sans, fontSize = 11.sp, color = Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                            }
                            Surface(
                                modifier = Modifier.weight(1f).clickable {
                                    kickHaptic.hud()
                                    if (deauthSession.running) {
                                        deauth.stop()
                                        kickResult = "已停止投毒 —— 目标缓存过期后自动恢复网络"
                                    } else {
                                        kickResult = deauth.startArpKick(ip, kickGateway.trim(), 0, 200L)
                                            ?.let { "[!] $it" }
                                            ?: "投毒已启动 —— 目标即将断网"
                                    }
                                },
                                shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                                color = Danger.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, Danger.copy(alpha = 0.55f)),
                            ) {
                                Text(
                                    if (deauthSession.running && deauthSession.phase == "ARP 踢人") "停止投毒" else "开始踢人 ▸",
                                    fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = Danger, textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 9.dp),
                                )
                            }
                        }
                    }
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
                if (selDev.isDroneRemoteId) {
                    PanelStat("[!] 无人机", "Remote-ID 广播检测 (ASTM F3411)")
                    Spacer(Modifier.height(4.dp))
                }
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
                // ── Wireshark 式原始广播十六进制 ──
                if (selDev.advBytesHex.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("原始广播 · RAW ADVERTISEMENT (HEX)", fontFamily = Sans, fontSize = 10.sp, color = RedDim, modifier = Modifier.padding(bottom = 3.dp))
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = Color(0xFF04121C),
                        border = BorderStroke(0.5.dp, Track),
                    ) {
                        SelectionContainer {
                            Text(
                                selDev.advBytesHex,
                                fontFamily = Mono, fontSize = 8.sp, color = TacGreen, lineHeight = 12.sp,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                    Text(
                        "AD Structure: Length · Type · Value;广播字段与 Android BLE 栈解码值一致,可直接对照分析",
                        fontFamily = Sans, fontSize = 7.5.sp, color = Gray, modifier = Modifier.padding(top = 2.dp),
                    )
                    // ── nRF Connect 式逐字段解码 ──
                    Spacer(Modifier.height(6.dp))
                    val adFields = remember(selDev.advBytesHex) {
                        runCatching {
                            AdStructureDecoder.decode(selDev.advBytesHex.split(" ").filter { it.length == 2 }.mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray())
                        }.getOrDefault(emptyList())
                    }
                    if (adFields.isNotEmpty()) {
                        Text("AD 字段解码 · nRF Connect 风格", fontFamily = Sans, fontSize = 10.sp, color = RedDim, modifier = Modifier.padding(bottom = 3.dp))
                        adFields.forEach { f ->
                            Column(Modifier.padding(vertical = 2.dp)) {
                                Text("▸ ${f.name} [${f.typeHex}]", fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = White)
                                Text("   ${f.decoded}", fontFamily = Mono, fontSize = 7.5.sp, color = Gray, lineHeight = 11.sp)
                            }
                        }
                    }
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
                        Text((if (d.isDroneRemoteId) "[DRONE] " else "") + d.name.take(20), fontFamily = Mono, fontSize = 11.sp, color = if (sel) Red else if (d.isDroneRemoteId) Gold else White, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
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
 * 对讲机详情页(v3)
 *
 * 局域网 PTT 实时语音 + 射频遥测。功能区块：
 * - 启动/停止按钮 + 本机 WiFi 链路遥测(SSID/频率/信道/速率/RSSI)
 * - 在线设备列表(说话中高亮 + 本机实测 UDP 回显 RTT/丢包 + 对端自报链路)
 * - 直播模式开关 + 固定底栏的按住说话大按钮(滚动不影响按压)
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

    // 页面可见期间 1Hz 刷新本机链路遥测(与对讲运行状态无关)
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) { vm.refreshLocalLink() }
            delay(1000)
        }
    }
    // 按住 PTT 途中页面被销毁(导航/退场):必须补发停止,否则麦克风与 UDP 音频循环悬挂
    DisposableEffect(Unit) {
        onDispose { vm.stopTalk() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WiFi 对讲机", back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize()) {
                // ── 滚动区:整页滚动,PTT 圆盘固定在底栏外 ──
                Column(
                    Modifier.weight(1f).navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp).padding(top = 12.dp),
                ) {
                    ActionButton(running, "执行对讲", "停止对讲") {
                        haptic.hud()
                        if (running) vm.stop() else vm.start()
                    }
                    error?.let { ErrorBanner(it) }
                    // 本机 WiFi 链路遥测(对讲跑在 WiFi LAN 上,链路质量即通话质量上限)
                    WalkieLinkPanel(vm)
                    // 高频数据区独立订阅：对讲期间在线设备与说话状态持续刷新
                    WalkiePeersPanel(vm, running = running)
                    Spacer(Modifier.height(10.dp))
                }

                if (running) {
                    // ── 固定底栏:直播开关 + PTT 圆盘 + 提示 ──
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = 12.dp).padding(bottom = 12.dp),
                    ) {
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
                    }
                }
            }
        }
    }
}

/**
 * 本机链路遥测面板 —— 对讲机跑在 WiFi LAN 上,链路质量即通话质量上限
 *
 * 全部取自 WifiManager.connectionInfo 真实返回值;对端节点的信号无法从
 * 本机测量(UDP 发现不携带对端 RSSI),节点面板只显示对端「自报」链路。
 */
@Composable
private fun WalkieLinkPanel(vm: WalkieTalkieViewModel) {
    val link by vm.link.collectAsState()
    val info = link.info
    TermPanel("本机链路 · RF LINK", Modifier.padding(top = 8.dp)) {
        if (info == null) {
            Text("WiFi 服务不可用", fontFamily = Sans, fontSize = 10.sp, color = Gray)
        } else if (!info.connected) {
            Text("WiFi 未连接 —— 热点模式下本机无上游链路可测", fontFamily = Sans, fontSize = 10.sp, color = Gray)
        } else {
            fun rssiColor(level: Int?): Color = when (level) {
                3, 4 -> TacGreen
                2 -> Warn
                1, 0 -> Danger
                else -> Gray
            }
            fun quality(level: Int?): String = when (level) {
                4 -> "优"; 3 -> "良"; 2 -> "中"; 1 -> "弱"; 0 -> "极弱"; else -> "—"
            }
            WalkieInfoRow("SSID", info.ssid ?: "不可见(定位未开启)")
            WalkieInfoRow("BSSID", info.bssid ?: "不可见")
            WalkieInfoRow("本机 IP · 网关", "${info.localIp ?: "—"} · ${info.gatewayIp ?: "—"}")
            WalkieInfoRow(
                "信号强度",
                if (info.rssiDbm != null) "${info.rssiDbm} dBm · ${quality(info.signalLevel)}" else "系统未提供",
            )
            WalkieInfoRow("信道 · 频率", "CH${info.channel ?: "—"} · ${info.frequencyMhz ?: "—"} MHz")
            val rate = when {
                info.txLinkSpeedMbps != null && info.rxLinkSpeedMbps != null -> "TX ${info.txLinkSpeedMbps} · RX ${info.rxLinkSpeedMbps} Mbps"
                info.linkSpeedMbps != null -> "${info.linkSpeedMbps} Mbps"
                else -> "系统未提供"
            }
            WalkieInfoRow("协商速率", rate)
            WalkieInfoRow("Wi-Fi 标准", info.wifiStandard ?: "系统未提供")
            if (info.rssiDbm != null) {
                // 信号条:RSSI -100..-30 → 0..5 格
                val bars = ((info.rssiDbm + 100f) / 70f * 5f).toInt().coerceIn(0, 5)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▮".repeat(bars) + "▯".repeat(5 - bars), fontFamily = Mono, fontSize = 11.sp, color = rssiColor(info.signalLevel))
                    Spacer(Modifier.width(6.dp))
                    Text("(本机 ↔ AP 实测)", fontFamily = Sans, fontSize = 7.5.sp, color = Gray)
                }
            }
        }
    }
}

/**
 * 对讲机链路行 —— 等宽键值对(标签更宽,容纳「本机 IP · 网关」类标签)
 */
@Composable
private fun WalkieInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontFamily = Mono, fontSize = 10.sp, color = Gray, modifier = Modifier.width(98.dp))
        Text(value, fontFamily = Mono, fontSize = 10.sp, color = White, modifier = Modifier.weight(1f))
    }
}

/**
 * 对讲机在线设备面板 —— 独立订阅设备流与说话状态
 *
 * 每节点两行:第一行名称/IP/说话中/网关标记;第二行链路质量 ——
 * RTT/抖动/丢包为本机实测(UDP 回显),自报字段为对端上报其自身链路。
 * 对端相对本机的 RSSI/距离在无 root 设备上不可得,不展示、不编造。
 */
@Composable
private fun WalkiePeersPanel(vm: WalkieTalkieViewModel, running: Boolean) {
    val peers by vm.peers.collectAsState()
    val link by vm.link.collectAsState()
    if (running && peers.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        TermPanel("在线节点 (${peers.size})") {
            peers.forEachIndexed { i, p ->
                // key(p.ip):列表重排时整体重建入场状态,避免 remember 复位后动画永不重放
                androidx.compose.runtime.key(p.ip) {
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { delay(i * 80L); visible = true }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(300)) + androidx.compose.animation.slideInHorizontally(tween(350)) { it / 3 },
                    ) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .background(if (p.isTalking) Red.copy(alpha = 0.12f) else Color.Transparent, CutCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (p.isTalking) "● " else "○ ", fontFamily = Mono, fontSize = 10.sp, color = if (p.isTalking) Red else Gray)
                            Text(p.name.take(16), fontFamily = Mono, fontSize = 11.sp, color = if (p.isTalking) Red else White, fontWeight = if (p.isTalking) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            if (link.info?.gatewayIp == p.ip) {
                                Text("网关", fontFamily = Sans, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = TacGreen, modifier = Modifier.padding(end = 4.dp))
                            }
                            Text(p.ip, fontFamily = Mono, fontSize = 10.sp, color = Gray)
                            if (p.isTalking) {
                                Spacer(Modifier.width(6.dp))
                                Text("说话中", fontFamily = Sans, fontSize = 9.sp, color = Red, fontWeight = FontWeight.Bold)
                            }
                        }
                        // 链路质量行:RTT 为本机实测,自报字段明确标注
                        Spacer(Modifier.height(2.dp))
                        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val rttText = when {
                                p.rttEwmaMs != null -> {
                                    val c = when { p.rttEwmaMs <= 30f -> TacGreen; p.rttEwmaMs <= 80f -> Warn; else -> Danger }
                                    Text(
                                        "回显 ${p.rttEwmaMs.toInt()}ms" + (p.jitterMs?.let { " · 抖动 ${it.toInt()}ms" } ?: "") + (p.lossPct?.let { " · 丢包 $it%" } ?: ""),
                                        fontFamily = Mono, fontSize = 8.sp, color = c,
                                    )
                                }
                                p.rttStatus == WalkieRttStatus.NoEcho -> Text("回显无响应(旧版本/弱网/已离开)", fontFamily = Mono, fontSize = 8.sp, color = Danger)
                                else -> Text("回显测量中…", fontFamily = Mono, fontSize = 8.sp, color = Gray)
                            }
                            Spacer(Modifier.weight(1f))
                            if (p.selfRssiDbm != null || p.selfFrequencyMhz != null) {
                                Text(
                                    "自报 ${p.selfRssiDbm?.let { "$it dBm" } ?: "—"} · ${p.selfFrequencyMhz?.let { "$it MHz" } ?: "—"}",
                                    fontFamily = Mono, fontSize = 8.sp, color = Gold,
                                )
                            }
                        }
                    }
                    }
                }
            }
            Text(
                "RTT/抖动/丢包 = 本机实测(UDP 回显);「自报」= 对端上报其自身↔AP 链路;" +
                    "对端相对本机的 RSSI 在无 root 设备上不可得",
                fontFamily = Sans, fontSize = 7.sp, color = Gray.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    } else if (running) {
        Spacer(Modifier.height(8.dp))
        TermPanel("在线节点 (0)") {
            Text("未发现在线节点 —— 请确认对端在同一 WiFi 且已启动对讲", fontFamily = Sans, fontSize = 10.sp, color = Gray)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 设备雷达
// ══════════════════════════════════════════════════════════════════════════

/** 目标威胁分级 —— 雷达页实时标记与档案页战术评估共用同一套推演标准 */
data class RadarThreatTier(val label: String, val color: Color, val desc: String)

/**
 * 按信号强度与估算距离推演目标威胁分级
 *
 * 距离由 RSSI 路径损耗模型估算，非测量值（手机无测距硬件）；
 * 分级阈值与档案页历史逻辑一致：贴身/强信号 → 高危，近距/稳定 → 中等，其余 → 远端背景。
 */
fun radarThreatTier(t: RadarTarget): RadarThreatTier = when {
    t.distanceM < 8f || t.rssi > -45 -> RadarThreatTier(
        "高危接近", Danger,
        "目标信号极强 / 估算距离 ${"%.1f".format(t.distanceM)}m，位于贴身范围内，建议立即甄别身份",
    )
    t.distanceM < 25f || t.rssi > -65 -> RadarThreatTier(
        "中等接近", Warn,
        "目标位于 ${"%.1f".format(t.distanceM)}m 附近，信号稳定，持续跟踪其移动轨迹",
    )
    else -> RadarThreatTier(
        "远端目标", TacGreen,
        "目标距本机较远（${"%.1f".format(t.distanceM)}m），属环境背景设备，维持常规监视",
    )
}

/**
 * 设备雷达页 —— 磷光 PPI 雷达 + 战术仪表盘(v3)
 *
 * ## 布局
 * 整页单一纵向滚动流(不再是「只有目标列表内部滚」):命令行(雷达开关+网络拓扑/信号库)
 * → 罗盘 → 大圆盘(按屏宽动态定尺,封顶 440dp,底部状态带盘内实测预留绝不裁切)
 * → 金属探测条 → 单行遥测 → 筛选 → 目标列表(随页面滚动,上限 30 行) → 网格节点。
 * SSH 入口已移除 —— 终端不属于雷达页。
 *
 * ## 圆盘(Canvas draw 阶段读取状态,零重组)
 * - 渐变扫描光束 + 8 层磷光尾迹扇形;目标扫过点亮、扫后磷光余晖
 * - 高危接近目标红色警示环 + 「!」(与档案页同一套威胁分级)
 * - 垫子坐标网格:25m 方格 + 轴距标签,便于协调定位;圆心即本机
 * - 方位推演叠加:聚焦目标显示估计方位扇形/位置标记/置信椭圆(RssiLocalizationEngine)
 * - 真实罗盘:旋转矢量传感器驱动,盘面随真机转动;顶部固定三角为机头航向指针
 * - 背景噪点 + 开机辉光 + 声呐 ping;新目标触觉警报(受设置页开关控制)
 *
 * ## 面板
 * - [MetalDetectStrip] 磁力计金属探测(相对基线异常,非距离测量)
 * - [TacticalStatusStrip] 单行遥测:目标/高危/节点/情报库
 * - [RadarTrackPanel] 威胁分级标签 + 方位推演行 + 情报入库快照
 * - [MeshNodesPanel] 对讲机节点 + 微型 PTT
 */
@Composable
fun RadarScreen(
    vm: RadarViewModel,
    walkieVm: WalkieTalkieViewModel,
    dbVm: DbViewModel,
    back: () -> Unit,
    radarAlertOn: Boolean,
    onSelectTarget: (String) -> Unit,
    onOpenNetmap: () -> Unit,
    onOpenDb: () -> Unit,
) {
    val running by vm.isRunning.collectAsState()
    val haptic = LocalHapticFeedback.current

    // 过滤状态：类型 + 关键词 + 聚焦目标
    var typeFilter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var focusedId by remember { mutableStateOf<String?>(null) }
    // 卫星层开关(GNSS 实例在下方 context 声明之后构造)
    var satelliteOn by remember { mutableStateOf(true) }
    // 配对弹窗:目标 + 消息 + 结果反馈
    var pairTarget by remember { mutableStateOf<RadarTarget?>(null) }
    var pairMsg by remember { mutableStateOf("") }
    var pairResult by remember { mutableStateOf<String?>(null) }

    // 进入页面启动罗盘传感器，离开时释放（扫描可独立继续运行）
    DisposableEffect(Unit) {
        vm.acquireCompass()
        onDispose { vm.releaseCompass() }
    }

    // 页面级持有扫描:与档案页/网络拓扑共享引用计数 —— 进入档案页过渡期扫描不中断,
    // 最后一个持有者离开才停止(主页开关显式启停的扫描不受页面接管)
    DisposableEffect(Unit) {
        vm.acquireScan()
        onDispose { vm.releaseScan() }
    }

    // 新目标触觉警报：首次发现某设备时震动一次
    LaunchedEffect(radarAlertOn) {
        vm.contact.collect { event ->
            if (event != null && radarAlertOn) {
                haptic.hud(HapticFeedbackType.LongPress)
            }
        }
    }

    // 卫星层:GNSS 星座叠加到雷达盘(真实 GnssStatus 解算)
    val context = LocalContext.current
    val gnss = remember { GnssSkyManager(context) }
    val gnssState by gnss.state.collectAsState()
    DisposableEffect(Unit) {
        gnss.start()
        onDispose { gnss.stop() }
    }

    // 聚焦目标距离振动：越近振幅越大、节奏越快；越远渐弱；超出雷达量程静默
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
            // 整页唯一纵向滚动流:面板随页面滚动,圆盘按屏宽动态定尺
            Column(
                Modifier.fillMaxSize().navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                // ── 命令行:雷达开关 + 网络拓扑/信号库(SSH 不属于雷达页,已移除) ──
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionButton(running, "执行雷达", "停止雷达", modifier = Modifier.weight(1f)) {
                        haptic.hud()
                        if (running) vm.stop() else vm.start()
                    }
                    QuickNavChip("网络拓扑", Gold, Modifier.width(62.dp)) { haptic.hud(); onOpenNetmap() }
                    QuickNavChip("信号库", Gold, Modifier.width(62.dp)) { haptic.hud(); onOpenDb() }
                }
                // 定位关闭横幅:WiFi 目标此时不可能进雷达,给出原因与快捷入口
                if (!locationOn) {
                    Spacer(Modifier.height(6.dp))
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
                Spacer(Modifier.height(6.dp))
                CompassStrip(vm)
                Spacer(Modifier.height(6.dp))
                // ── 雷达圆盘:动态尺寸 = min(可用宽度, 440dp);高度加状态带余量,盘面更大且文字不裁切 ──
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val disc = minOf(maxWidth, RADAR_DISC_MAX)
                    RadarScope(
                        vm,
                        walkieVm,
                        gnss = if (satelliteOn) gnss else null,
                        running = running,
                        typeFilter = typeFilter,
                        query = query,
                        focusedId = focusedId,
                        modifier = Modifier.width(disc).height(disc + 34.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                MetalDetectStrip(vm)
                Spacer(Modifier.height(6.dp))
                TacticalStatusStrip(vm, walkieVm, dbVm)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadarFilterBar(
                        typeFilter = typeFilter,
                        onTypeFilter = { typeFilter = it; focusedId = null },
                        query = query,
                        onQuery = { query = it; focusedId = null },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    // 卫星层开关
                    Surface(
                        modifier = Modifier.clickable {
                            haptic.hud()
                            satelliteOn = !satelliteOn
                        },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = if (satelliteOn) Gold.copy(alpha = 0.14f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (satelliteOn) Gold.copy(alpha = 0.6f) else BorderDim),
                    ) {
                        Text(
                            if (gnssState.satellites.isEmpty()) "卫星 0 · 室内?"
                            else "卫星 ${gnssState.satellites.size}",
                            fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = if (satelliteOn) Gold else Gray,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                RadarTrackPanel(
                    vm,
                    dbVm,
                    typeFilter = typeFilter,
                    query = query,
                    focusedId = focusedId,
                    onFocus = { id -> focusedId = if (focusedId == id) null else id },
                    onSelect = { id -> haptic.hud(); onSelectTarget(id) },
                    onPair = { t -> pairTarget = t; pairMsg = ""; pairResult = null },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                MeshNodesPanel(walkieVm)
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ── 配对弹窗对话框:消息 = 对端系统弹窗上显示的本机蓝牙名 ──
    pairTarget?.let { t ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { pairTarget = null }) {
            Surface(
                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("配对弹窗 · 目标 ${t.name.take(16)}", fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Spacer(Modifier.height(4.dp))
                    Text(t.address, fontFamily = Mono, fontSize = 9.sp, color = Gray)
                    Spacer(Modifier.height(10.dp))
                    TermInput("弹窗消息(≤60 字)", pairMsg, placeholder = "将显示在对方的系统配对弹窗上") { pairMsg = it }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "机制: 本机蓝牙名临时改为消息 → 发起标准配对请求。仅对处于可发现/可配对状态的设备有效,是系统弹窗而非自定义 UI。",
                        fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    pairResult?.let { Text(it, fontFamily = Sans, fontSize = 9.sp, color = if (it.startsWith("已")) TacGreen else Danger) }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            modifier = Modifier.weight(1f).clickable { haptic.hud(); pairTarget = null },
                            shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, BorderDim),
                        ) {
                            Text("取消", fontFamily = Sans, fontSize = 11.sp, color = Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                        }
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                haptic.hud()
                                if (pairMsg.isBlank()) {
                                    xin.ctkqiang.deauthctrl.ui.components.StickerBus.show("先输入弹窗消息!")
                                    pairResult = "[!] 消息不能为空"
                                } else {
                                    pairResult = vm.sendPairingPopup(t.address, pairMsg)?.let { "[!] $it" }
                                        ?: "已发起配对请求 —— 对方屏幕将出现弹窗(60 秒后自动恢复本机蓝牙名)"
                                }
                            },
                            shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                            color = Red.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Red.copy(alpha = 0.55f)),
                        ) {
                            Text("发起弹窗 ▸", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 雷达圆盘直径上限 —— 超宽屏(平板)封顶,避免列表被推得太远 */
private val RADAR_DISC_MAX = 440.dp

/** 目标列表渲染行数上限 —— 整页滚动后列表去 Lazy 化,防止数百目标卡死页面 */
private const val MAX_RADAR_TRACK_ROWS = 30

/**
 * 情报入库快照芯片 —— 位于目标列表头部右侧
 *
 * 把当前雷达目标快照 INSERT 进信号情报库（[DbViewModel.refresh]），
 * 按钮短暂闪现入库条数作为反馈；无目标时提示「无目标」。
 * 只入库雷达来源;BLE 嗅探 / WiFi 网络来源由信号库页统一采集。
 */
@Composable
private fun SnapshotChip(vm: RadarViewModel, dbVm: DbViewModel) {
    val haptic = LocalHapticFeedback.current
    val lastRefresh by dbVm.lastRefresh.collectAsState()
    val lastInserted by dbVm.lastInserted.collectAsState()
    // 闪现消息:(触发时间, 文案) —— 所有反馈走同一套 1.6s 自动消失计时器
    var flash by remember { mutableStateOf<Pair<Long, String>?>(null) }
    LaunchedEffect(lastRefresh) {
        if (lastRefresh != null) flash = System.currentTimeMillis() to "+$lastInserted 已入库"
    }
    LaunchedEffect(flash) {
        if (flash != null) { delay(1600); flash = null }
    }
    QuickNavChip(
        label = flash?.second ?: "入库",
        color = if (flash != null) TacGreen else Gold,
        modifier = Modifier.width(52.dp),
        onClick = {
            haptic.hud()
            if (vm.targets.value.isEmpty()) {
                flash = System.currentTimeMillis() to "无目标"
            } else {
                dbVm.refresh(vm.targets.value, emptyList(), emptyList())
            }
        },
    )
}

/** 快捷入口芯片 —— 斜切角小按钮,等宽字 */
@Composable
private fun QuickNavChip(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            label,
            fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            color = color, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 7.dp),
        )
    }
}

/**
 * 战术遥测单行条 —— 2×2 卡片瘦身为一行四格
 *
 * 随各模块轮询实时刷新:雷达目标 / 高危接近 / 网格节点 / 情报库。
 * 节点未运行时显示「—」,不编造在线数。
 */
@Composable
private fun TacticalStatusStrip(
    vm: RadarViewModel,
    walkieVm: WalkieTalkieViewModel,
    dbVm: DbViewModel,
) {
    val targets by vm.targets.collectAsState()
    val peers by walkieVm.peers.collectAsState()
    val walkieRunning by walkieVm.isRunning.collectAsState()
    val counts by dbVm.counts.collectAsState()

    val high = targets.count { radarThreatTier(it).label == "高危接近" }
    val dbTotal = counts.values.sum()

    Surface(
        Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, BorderDim),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactStat("目标", "${targets.size}", TacGreen, Modifier.weight(1f))
            StatDivider()
            CompactStat("高危", "$high", Danger, Modifier.weight(1f))
            StatDivider()
            CompactStat("节点", if (walkieRunning) "${peers.size}" else "—", Gold, Modifier.weight(1f))
            StatDivider()
            CompactStat("情报", "$dbTotal", White, Modifier.weight(1f))
        }
    }
}

/** 单行遥测格 —— 数值 + 标签横排 */
@Composable
private fun CompactStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(value, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.width(4.dp))
        Text(label, fontFamily = Sans, fontSize = 7.5.sp, color = Gray, maxLines = 1)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(12.dp).background(BorderDim))
    Spacer(Modifier.width(8.dp))
}

/**
 * 金属探测条 —— 磁力计相对基线异常
 *
 * 点击重新校准基线。anomaly 只表示「附近磁场显著变化」(金属靠近/磁铁/环境),
 * 不是距离测量,也无法定位金属方向 —— 文案如实标注。
 */
@Composable
private fun MetalDetectStrip(vm: RadarViewModel) {
    val metalState by vm.metal.collectAsState()
    val m = metalState // delegated 属性无法 smart cast,先落本地
    val haptic = LocalHapticFeedback.current
    val anomaly = m?.anomaly == true
    val accent = if (anomaly) Danger else TacGreen

    Surface(
        Modifier.fillMaxWidth().clickable {
            haptic.hud()
            vm.resetMetalBaseline()
        },
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, if (anomaly) Danger.copy(alpha = 0.55f) else BorderDim),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Star(accent, 7.dp)
                Spacer(Modifier.width(6.dp))
                Text("金属探测 · MAG", fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent)
                Spacer(Modifier.weight(1f))
                val status = when {
                    m == null -> "磁力计不可用"
                    !m.calibrated -> "校准中 ${(m.calProgress * 100).toInt()}%"
                    anomaly -> "磁场异常"
                    else -> "环境稳定"
                }
                val statusColor = when {
                    m == null -> Gray
                    !m.calibrated -> Gold
                    anomaly -> Danger
                    else -> TacGreen
                }
                Text(status, fontFamily = Sans, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = statusColor)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (m == null) "--.-" else "%6.1f".format(m.microTesla),
                    fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (anomaly) Danger else White,
                )
                Spacer(Modifier.width(4.dp))
                Text("µT", fontFamily = Sans, fontSize = 8.sp, color = Gray)
                Spacer(Modifier.width(10.dp))
                // 相对基线偏移条:满幅 ±10µT,异常区间描红
                val delta01 = if (m == null) 0.5f else ((m.deltaUt / 10f).coerceIn(-1f, 1f) + 1f) / 2f
                Canvas(Modifier.weight(1f).height(8.dp)) {
                    val trackH = size.height * 0.5f
                    drawRoundRect(Gray.copy(alpha = 0.25f), topLeft = Offset(0f, size.height / 2f - trackH / 2f), size = Size(size.width, trackH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f))
                    val cx = size.width * delta01
                    drawLine(accent.copy(alpha = 0.9f), Offset(cx, 0f), Offset(cx, size.height), 2f)
                    drawCircle(accent, 3f, Offset(cx, size.height / 2f))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (m == null) "Δ --.- µT" else "Δ %+.1f µT".format(m.deltaUt),
                    fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    color = if (anomaly) Danger else Gray,
                )
            }
            Text(
                "相对磁场异常检测 · 非距离测量 · 点击重新校准",
                fontFamily = Sans, fontSize = 7.sp, color = Gray.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 对讲机网格节点面板 —— 局域网 PTT 节点的实时状态与快捷操作(页面末端)
 *
 * 未运行时压缩为单行(一键启动);运行中显示在线节点(名称/IP/说话中)
 * 与微型 PTT 圆钮(按住说话,松开停止,免提模式期间按钮无效)。
 * 节点位置不画进扫描盘:UDP 发现不携带 RSSI,无距离数据可推演,不编造坐标。
 */
@Composable
private fun MeshNodesPanel(vm: WalkieTalkieViewModel) {
    val running by vm.isRunning.collectAsState()
    val talking by vm.isTalking.collectAsState()
    val isLive by vm.isLive.collectAsState()
    val peers by vm.peers.collectAsState()
    val haptic = LocalHapticFeedback.current
    val active = talking || isLive

    Surface(
        Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, if (running) Gold.copy(alpha = 0.45f) else BorderDim),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Star(Gold, 7.dp)
                Spacer(Modifier.width(6.dp))
                Text("网格节点", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold)
                Spacer(Modifier.width(8.dp))
                if (running) {
                    Text("${peers.size} 在线", fontFamily = Mono, fontSize = 9.sp, color = TacGreen)
                    if (talking) {
                        Spacer(Modifier.width(6.dp))
                        Text("发送中", fontFamily = Sans, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Red)
                    }
                } else {
                    Text("未运行", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                }
                Spacer(Modifier.weight(1f))
                if (running) {
                    // 微型 PTT 圆钮 —— 与对讲机页相同的按住说话手势
                    Box(
                        Modifier
                            .size(32.dp)
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
                            .background(if (active) Red.copy(alpha = 0.85f) else Red.copy(alpha = 0.12f))
                            .border(1.5.dp, if (active) Gold else Red.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "PTT",
                            fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                            color = if (active) White else Red,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "■",
                        fontFamily = Mono, fontSize = 12.sp, color = Gray,
                        modifier = Modifier.clickable {
                            haptic.hud()
                            vm.stop()
                        },
                    )
                } else {
                    Text(
                        "点击启动 ▸",
                        fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacGreen,
                        modifier = Modifier.clickable {
                            haptic.hud()
                            vm.start()
                        },
                    )
                }
            }
            // 节点芯片条:横向滚动,说话中的节点红底脉动标识
            if (running && peers.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    peers.take(8).forEach { p ->
                        Surface(
                            shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                            color = if (p.isTalking) Red.copy(alpha = 0.12f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (p.isTalking) Red.copy(alpha = 0.6f) else BorderDim),
                        ) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (p.isTalking) "●" else "○", fontFamily = Mono, fontSize = 7.sp, color = if (p.isTalking) Red else Gray)
                                Spacer(Modifier.width(4.dp))
                                Text(p.name.take(10), fontFamily = Mono, fontSize = 8.sp, color = if (p.isTalking) Red else White)
                                Spacer(Modifier.width(4.dp))
                                Text(p.ip, fontFamily = Mono, fontSize = 7.sp, color = Gray)
                            }
                        }
                    }
                }
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
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
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
    walkieVm: WalkieTalkieViewModel,
    gnss: GnssSkyManager?,
    running: Boolean,
    typeFilter: String,
    query: String,
    focusedId: String?,
    modifier: Modifier = Modifier,
) {
    // draw 阶段读取 targets/heading/localizations/peers/卫星:更新只触发重绘,不触发重组
    val targetsState = vm.targets.collectAsState()
    val headingState = vm.heading.collectAsState()
    val peersState = walkieVm.peers.collectAsState()
    val locState = vm.localizations.collectAsState()
    val satState = gnss?.state?.collectAsState() // gnss 在 RadarScreen 层 remember, 参数 null 时组合不成立(设计如此)
    val textMeasurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current

    // 文本测量缓存:罗盘字/网格标签/目标编号/图例等固定文本只测一次;
    // 每目标每帧 measure 是雷达卡顿的主因之一
    val textCache = remember { HashMap<String, androidx.compose.ui.text.TextLayoutResult>() }
    fun cachedMeasure(key: String, text: String, style: TextStyle): androidx.compose.ui.text.TextLayoutResult {
        if (textCache.size > 96) textCache.clear()
        return textCache.getOrPut("$key|$text") { textMeasurer.measure(text, style) }
    }

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
    // 记录时就用「当前生效位置」:有方位推演结果用估计方位,否则哈希占位方位
    val trails = remember { mutableStateMapOf<String, List<TrailPoint>>() }
    LaunchedEffect(Unit) {
        snapshotFlow { vm.targets.value }.collect { targets ->
            if (targets.isEmpty()) return@collect
            val locNow = vm.localizations.value
            val current = targets.associate { t ->
                val e = locNow[t.id]
                val locked = e != null && e.state == LocState.ESTIMATED && e.confidence >= 0.45f && !e.bearingDeg.isNaN()
                val angle = if (locked) e.bearingDeg else t.angleDeg
                val dist = if (locked) (e.distanceM / 100f).coerceIn(0.06f, 0.94f) else (t.distanceM / 100f).coerceIn(0.06f, 0.94f)
                t.id to TrailPoint(angle, dist)
            }
            val merged = HashMap<String, List<TrailPoint>>()
            trails.forEach { (id, hist) ->
                val cur = current[id]
                merged[id] = if (cur != null) (hist + cur).takeLast(8) else hist.takeLast(3)
            }
            current.forEach { (id, pt) -> merged.putIfAbsent(id, listOf(pt)) }
            trails.clear()
            trails.putAll(merged)
        }
    }

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
            val g = TacGreen
            // draw 阶段读取:任何高频更新只触发重绘,绝不重组组合树
            val targets = targetsState.value
            val heading = headingState.value
            val peers = peersState.value
            val satellites = satState?.value?.satellites.orEmpty()
            // ── 过滤后的可见目标：聚焦目标永远显示，其余按类型/关键词过滤;绘制上限 40 防卡顿 ──
            val filteredAll = targets.filter { t ->
                t.id == focusedId ||
                    ((typeFilter == "all" || t.type == typeFilter) &&
                        (query.isBlank() || t.name.contains(query, ignoreCase = true)))
            }
            val visibleTargets = (focusedId?.let { id -> filteredAll.filter { it.id == id } }.orEmpty() +
                filteredAll.filter { it.id != focusedId }.sortedByDescending { it.rssi }.take(40))
                .distinctBy { it.id }
            val focusedTarget = focusedId?.let { id -> targets.firstOrNull { it.id == id } }
            // ── 底部状态带:先测量两行文字,再反推盘面几何 —— 文字绝不裁切 ──
            val loc = locState.value
            val statusStyle = TextStyle(fontFamily = Mono, fontSize = 8.5.sp, color = g.copy(alpha = 0.86f))
            val maxW = (size.width - 12.dp.toPx()).toInt().coerceAtLeast(1)
            val l1 = textMeasurer.measure(
                (if (running) "模式: 扫描" else "模式: 待机") + "   扫描: %03d°".format(sweepDeg.toInt() % 360) + "   航向: %03d°".format(heading.toInt() % 360),
                statusStyle,
                constraints = Constraints(maxWidth = maxW),
            )
            val estCount = loc.values.count { it.state == LocState.ESTIMATED }
            val l2 = textMeasurer.measure(
                "量程: 100m   目标: ${visibleTargets.size}/${targets.size}   定位: $estCount   节点: ${peers.size}",
                statusStyle,
                constraints = Constraints(maxWidth = maxW),
            )
            val bandPad = 3.dp.toPx()
            val bandH = l1.size.height + l2.size.height + bandPad * 2
            val fieldH = (size.height - bandH).coerceAtLeast(1f)
            val cx = size.width / 2f
            val cy = fieldH / 2f
            val r = minOf(cx, cy) * 0.90f

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

            // ── 垫子坐标网格:25m 方格,裁剪进圆内 —— 屏幕基准的协调垫,方便报位 ──
            // 格距 = r/4(量程 100m → 每格 25m);网格固定于盘面,目标随罗盘在其上旋转
            val gridStep = r / 4f
            clipPath(Path().apply { addOval(Rect(Offset(cx - r, cy - r), Size(r * 2f, r * 2f))) }) {
                var gx = cx
                while (gx <= cx + r) {
                    drawLine(g.copy(alpha = 0.10f), Offset(gx, cy - r), Offset(gx, cy + r), 0.6f)
                    drawLine(g.copy(alpha = 0.10f), Offset(cx - (gx - cx), cy - r), Offset(cx - (gx - cx), cy + r), 0.6f)
                    gx += gridStep
                }
                var gy = cy
                while (gy <= cy + r) {
                    drawLine(g.copy(alpha = 0.10f), Offset(cx - r, gy), Offset(cx + r, gy), 0.6f)
                    drawLine(g.copy(alpha = 0.10f), Offset(cx - r, cy - (gy - cy)), Offset(cx + r, cy - (gy - cy)), 0.6f)
                    gy += gridStep
                }
                // 环距标签(沿 +E 轴):25m/50m/75m
                for (m in listOf(25f, 50f, 75f)) {
                    val tr = r * m / 100f
                    val tag = cachedMeasure("grid", "${m.toInt()}", TextStyle(fontFamily = Mono, fontSize = 7.sp, color = g.copy(alpha = 0.45f)))
                    drawText(tag, topLeft = Offset(cx + tr - tag.size.width / 2f, cy + 2.dp.toPx()))
                }
            }

            // ── 方位刻度：随真实罗盘航向旋转，刻度永远指向真实方位 ──
            // 屏幕角 = 世界角 - 航向 - 90°：世界 0°(北) 在机头(屏顶)方向时为 -90°(屏顶)
            for (deg in 0 until 360 step 10) {
                val a = Math.toRadians((deg - heading - 90.0).toDouble()).toFloat()
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
                val a = Math.toRadians((deg - heading - 90.0).toDouble()).toFloat()
                val lr = r * 0.83f
                val m = cachedMeasure("cardinal", ch, TextStyle(fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = g.copy(alpha = 0.9f)))
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
            val northRad = Math.toRadians((-heading - 90.0).toDouble()).toFloat()
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
                    val e = loc[t.id]
                    val locked = e != null && e.state == LocState.ESTIMATED && e.confidence >= 0.45f && !e.bearingDeg.isNaN()
                    val effAngle = if (locked) e.bearingDeg else t.angleDeg
                    val dist = ((if (locked) e.distanceM else t.distanceM) / 100f).coerceIn(0.06f, 0.94f)
                    val tr = dist * r
                    val ta = Math.toRadians((effAngle - heading - 90.0).toDouble()).toFloat()
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
                    // 方位推演置信达标时用估计方位/距离替换哈希占位(位置仍为估计值)
                    val e = loc[t.id]
                    val locked = e != null && e.state == LocState.ESTIMATED && e.confidence >= 0.45f && !e.bearingDeg.isNaN()
                    val effAngle = if (locked) e.bearingDeg else t.angleDeg
                    val dist = ((if (locked) e.distanceM else t.distanceM) / 100f).coerceIn(0.06f, 0.94f)
                    val tr = dist * r
                    // 目标方位为世界坐标，绘制时减去航向与 90° 屏幕偏置 —— 转动机身，盘面目标随之旋转
                    val ta = Math.toRadians((effAngle - heading - 90.0).toDouble()).toFloat()
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
                        val pta = Math.toRadians((pt.angleDeg - heading - 90.0).toDouble()).toFloat()
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

                    // ── 高危接近警示:红色双环 + 「!」标签(与档案页同一套威胁分级) ──
                    val tier = radarThreatTier(t)
                    if (tier.label == "高危接近") {
                        val warnR = 22.dp.toPx() + 3.dp.toPx() * sin(sweepDeg * 0.07f + i)
                        drawCircle(Danger.copy(alpha = bright * 0.85f), warnR, Offset(x, y), style = Stroke(2.2f))
                        drawCircle(Danger.copy(alpha = bright * 0.5f), warnR + 4.dp.toPx(), Offset(x, y), style = Stroke(1.2f))
                        val bang = cachedMeasure("bang", "!", TextStyle(fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Danger))
                        drawText(bang, topLeft = Offset(x + warnR, y - warnR))
                    }

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
                        val tag = cachedMeasure(
                            "focus",
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
                    val tag = cachedMeasure("num", "${i + 1}", TextStyle(fontFamily = Mono, fontSize = 9.sp, color = blip.copy(alpha = bright)))
                    drawText(tag, topLeft = Offset(x + 10.dp.toPx(), y - 18.dp.toPx()))
                }
            }

            // ── 聚焦目标方位推演叠加:扇形/方位线/位置标记/置信椭圆 ──
            // 全部来自 RssiLocalizationEngine(旋转寻向估计),非 AoA 测量
            val foc = focusedId?.let { id -> loc[id] }
            if (focusedId != null && foc != null) {
                if (foc.state == LocState.ESTIMATED && !foc.bearingDeg.isNaN()) {
                    val bRad = Math.toRadians((foc.bearingDeg - heading - 90.0).toDouble()).toFloat()
                    val color = if (foc.state == LocState.AMBIGUOUS) Warn else Gold
                    val confA = 0.05f + 0.14f * foc.confidence.coerceIn(0f, 1f)
                    // 方位扇形(±半宽)与 FWHM 边界虚线
                    val halfDeg = foc.sectorHalfWidthDeg.coerceIn(8f, 80f)
                    drawArc(
                        color.copy(alpha = confA),
                        startAngle = Math.toDegrees(bRad.toDouble()).toFloat() - halfDeg,
                        sweepAngle = halfDeg * 2f,
                        useCenter = true,
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                    )
                    val fwhmRad = Math.toRadians((foc.fwhmDeg / 2f).toDouble()).toFloat()
                    listOf(fwhmRad, -fwhmRad).forEach { off ->
                        val a2 = bRad + off
                        drawLine(
                            color.copy(alpha = 0.45f),
                            Offset(cx + r * 0.3f * cos(a2), cy + r * 0.3f * sin(a2)),
                            Offset(cx + r * cos(a2), cy + r * sin(a2)),
                            1f,
                        )
                    }
                    // 方位线(中心 → 估计方位)
                    drawLine(color.copy(alpha = 0.55f), Offset(cx, cy), Offset(cx + r * cos(bRad), cy + r * sin(bRad)), 1.2f)
                    // 位置标记(多圈 GLS 融合点)与置信椭圆 —— 无融合时退化为方位线端点
                    // ENU 世界坐标 → 屏幕:与方位标签同一映射(世界方位角 - 航向 - 90°)
                    val hasFix = !foc.eastM.isNaN() && !foc.northM.isNaN()
                    val mk: Offset
                    if (hasFix) {
                        val worldBearing = Math.toDegrees(atan2(foc.eastM.toDouble(), foc.northM.toDouble())).toFloat()
                        val worldDist = sqrt(foc.eastM * foc.eastM + foc.northM * foc.northM)
                        val ma = Math.toRadians((worldBearing - heading - 90.0).toDouble()).toFloat()
                        mk = Offset(cx + (worldDist / 100f * r) * cos(ma), cy + (worldDist / 100f * r) * sin(ma))
                    } else {
                        mk = Offset(cx + r * cos(bRad), cy + r * sin(bRad))
                    }
                    val mkColor = color
                    drawLine(mkColor.copy(alpha = 0.9f), Offset(mk.x - 6.dp.toPx(), mk.y), Offset(mk.x + 6.dp.toPx(), mk.y), 1.6f)
                    drawLine(mkColor.copy(alpha = 0.9f), Offset(mk.x, mk.y - 6.dp.toPx()), Offset(mk.x, mk.y + 6.dp.toPx()), 1.6f)
                    drawCircle(mkColor.copy(alpha = 0.6f), 3.5.dp.toPx(), mk)
                    if (hasFix && foc.sweepCount > 0) {
                        // 置信椭圆(90%,旋转至椭圆主轴;世界角 → 屏幕角同一映射)
                        val aSemi = (foc.ellipseSemiMajorM / 100f * r).coerceAtLeast(3f)
                        val bSemi = (foc.ellipseSemiMinorM / 100f * r).coerceAtLeast(3f)
                        val phi = Math.toRadians((foc.ellipseAngleDeg - heading - 90.0).toDouble()).toFloat()
                        withTransform({
                            translate(mk.x, mk.y)
                            rotate(phi, pivot = Offset.Zero)
                        }) {
                            drawOval(
                                mkColor.copy(alpha = 0.5f),
                                topLeft = Offset(-aSemi, -bSemi),
                                size = Size(aSemi * 2f, bSemi * 2f),
                                style = Stroke(1.2f),
                            )
                        }
                    }
                } else if (foc.state == LocState.COLLECTING) {
                    // 采样中:圆盘底部提示旋转采集
                    val hint = cachedMeasure(
                        "hint",
                        "方位推演采样中 · 覆盖 %d%% · 请缓慢旋转机身".format((foc.coverage * 100).toInt()),
                        TextStyle(fontFamily = Sans, fontSize = 9.sp, color = Gold.copy(alpha = 0.9f)),
                    )
                    drawText(hint, topLeft = Offset(cx - hint.size.width / 2f, cy + r * 0.62f))
                }
            }

            // ── 网格节点环:同 App 对讲机节点 ──
            // UDP 发现不携带 RSSI,距离未知 —— 节点画在固定中环上按 IP 哈希伪角分布,
            // 环本身即「距离未知」的语义;用方形涟漪 + 菱形标记,与圆形 blip 的目标
            // (WiFi/BLE)一眼区分,不用红/蓝/主题色(金色=网络身份色)
            if (peers.isNotEmpty()) {
                val meshR = r * 0.68f
                drawCircle(Gold.copy(alpha = 0.10f), meshR, Offset(cx, cy), style = Stroke(0.8f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 7f))))
                peers.sortedBy { it.ip }.forEachIndexed { i, p ->
                    val pa = (p.ip.hashCode() and 0x7FFFFFFF) % 360f
                    val paRad = Math.toRadians((pa - heading - 90.0).toDouble()).toFloat()
                    val x = cx + meshR * cos(paRad)
                    val y = cy + meshR * sin(paRad)
                    // 方形涟漪(双相扩散方框)
                    val ph = ((sweepDeg / 360f) * 2f + i * 0.31f) % 1f
                    listOf(0f, 0.5f).forEach { off ->
                        val p2 = (ph + off) % 1f
                        val half = 4.dp.toPx() + 13.dp.toPx() * p2
                        drawRect(
                            Gold.copy(alpha = (1f - p2) * 0.45f),
                            topLeft = Offset(x - half, y - half),
                            size = Size(half * 2f, half * 2f),
                            style = Stroke(1.4f),
                        )
                    }
                    // 菱形标记(45° 方框);说话中实心
                    withTransform({
                        translate(x, y)
                        rotate(45f, pivot = Offset.Zero)
                    }) {
                        val s = if (p.isTalking) 5.dp.toPx() else 4.dp.toPx()
                        drawRect(
                            if (p.isTalking) Gold else Gold.copy(alpha = 0.85f),
                            topLeft = Offset(-s, -s),
                            size = Size(s * 2f, s * 2f),
                            style = if (p.isTalking) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.6f),
                        )
                    }
                    // 节点名标签(仅说话中常显,其余扫过时显示)
                    val showTag = p.isTalking || abs(((sweepDeg - pa + 540f) % 360f) - 180f) < 40f
                    if (showTag) {
                        val tag = cachedMeasure(
                            "peer",
                            "◈ ${p.name.take(8)}",
                            TextStyle(fontFamily = Mono, fontSize = 8.sp, color = Gold.copy(alpha = 0.95f)),
                        )
                        drawText(tag, topLeft = Offset(x - tag.size.width / 2f, y + 9.dp.toPx()))
                    }
                }
                // 图例:左上角
                val legend = cachedMeasure(
                    "legend",
                    "◈ 同网节点(距离未知)",
                    TextStyle(fontFamily = Mono, fontSize = 7.5.sp, color = Gold.copy(alpha = 0.8f)),
                )
                drawText(legend, topLeft = Offset(8.dp.toPx(), 8.dp.toPx()))
            }

            // ── 卫星层:GNSS 星座叠加(仰角→半径, 方位→屏幕角, 与目标同一罗盘映射) ──
            // 数据来自系统 GnssStatus 真实解算;卫星是外圈金色小点+细环,与目标 blip 明显区分
            if (satellites.isNotEmpty()) {
                satellites.forEach { sat ->
                    val el = sat.elevationDeg.coerceIn(0f, 90f)
                    val az = sat.azimuthDeg
                    val sr = r * (1f - el / 90f) * 0.92f
                    val sa = Math.toRadians((az - heading - 90.0).toDouble()).toFloat()
                    val x = cx + sr * cos(sa)
                    val y = cy + sr * sin(sa)
                    val color = when (sat.constellationName) {
                        "GPS" -> Gold
                        "北斗" -> Danger
                        "伽利略" -> TacGreen
                        "格洛纳斯" -> Warn
                        else -> White
                    }
                    drawCircle(color.copy(alpha = 0.35f), 4.dp.toPx(), Offset(x, y))
                    drawCircle(color.copy(alpha = if (sat.usedInFix) 0.95f else 0.55f), 1.6.dp.toPx(), Offset(x, y))
                }
                val satTag = cachedMeasure(
                    "sat",
                    "卫星 ${satellites.size}",
                    TextStyle(fontFamily = Mono, fontSize = 7.5.sp, color = Gold.copy(alpha = 0.8f)),
                )
                drawText(satTag, topLeft = Offset(8.dp.toPx(), 20.dp.toPx()))
            }

            // ── 中心原点（呼吸） ──
            drawCircle(g.copy(alpha = 0.45f + 0.35f * sin(sweepDeg * 0.3f)), 2.dp.toPx(), Offset(cx, cy))

            // ── 底部 CRT 状态带(盘面下方实测预留区,两行,绝不裁切) ──
            drawLine(BorderDim.copy(alpha = 0.9f), Offset(0f, fieldH), Offset(size.width, fieldH), 1f)
            drawText(l1, topLeft = Offset((size.width - l1.size.width) / 2f, fieldH + bandPad))
            drawText(l2, topLeft = Offset((size.width - l2.size.width) / 2f, fieldH + bandPad + l1.size.height))
        }
    }
}

/** 雷达目标磷光轨迹点（角度 + 归一化距离） */
private data class TrailPoint(val angleDeg: Float, val dist01: Float)

/** 雷达目标列表面板 —— 随整页滚动(非内嵌滚动),上限 30 行保护,聚焦目标强制包含 */
@Composable
private fun RadarTrackPanel(
    vm: RadarViewModel,
    dbVm: DbViewModel,
    typeFilter: String,
    query: String,
    focusedId: String?,
    onFocus: (String) -> Unit,
    onSelect: (String) -> Unit,
    onPair: (RadarTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targets by vm.targets.collectAsState()
    val locState = vm.localizations.collectAsState()
    val filtered = targets.filter { t ->
        t.id == focusedId ||
            ((typeFilter == "all" || t.type == typeFilter) &&
                (query.isBlank() || t.name.contains(query, ignoreCase = true)))
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Star(Gold, 8.dp)
            Spacer(Modifier.width(7.dp))
            Text("目标列表 · ${filtered.size}/${targets.size}", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red, maxLines = 1, modifier = Modifier.weight(1f))
            if (focusedId != null) {
                Spacer(Modifier.width(6.dp))
                Text("聚焦 · 降噪", fontFamily = Mono, fontSize = 8.sp, color = Gold)
            }
            Spacer(Modifier.width(6.dp))
            SnapshotChip(vm, dbVm)
        }
        Spacer(Modifier.height(4.dp))
        if (filtered.isEmpty()) {
            EmptyHint(if (targets.isEmpty()) "启动雷达后，检测到的设备会亮在扫描盘上" else "过滤条件下没有匹配设备")
        } else {
            // 类型前缀 + 去重:BLE 扫描与经典发现可能瞬时上报同一地址
            val rows = filtered.sortedByDescending { it.rssi }.distinctBy { "${it.type}_${it.id}" }
            val shown = buildList {
                focusedId?.let { id -> rows.firstOrNull { it.id == id }?.let(::add) }
                addAll(rows.take(MAX_RADAR_TRACK_ROWS).filter { it.id != focusedId })
            }
            val locNow = locState.value
            shown.forEach { t ->
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
                            Text(if (wifi) "WIFI" else if (t.observedVia == "ble") "BLE" else "BT", fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (wifi) Danger else TacGreen, modifier = Modifier.width(34.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.name.take(16), fontFamily = Sans, fontSize = 11.sp, color = White)
                                    // 设备情报指纹:黑客工具/开发板/物联网识别(启发式,可能误报)
                                    DeviceIntel.classify(t.name, t.id, t.bleAdv?.serviceUuids.orEmpty(), t.bleAdv?.manufacturerId)?.let { hit ->
                                        Spacer(Modifier.width(5.dp))
                                        val badgeColor = when (hit.category) {
                                            "黑客工具" -> Danger
                                            "开发板" -> Gold
                                            else -> Warn
                                        }
                                        Surface(
                                            shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                                            color = badgeColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
                                        ) {
                                            Text(hit.label, fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = badgeColor, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }
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
                                // 方位行:有推演结果显示「估计」,占位方位标「占位」一眼可辨
                                val e = locNow[t.id]
                                val hasLoc = e != null && e.state == LocState.ESTIMATED && !e.bearingDeg.isNaN()
                                Text(
                                    if (hasLoc) "%.1fm · 估计 %03d°".format(e.distanceM, e.bearingDeg.toInt())
                                    else "%.1fm · %03d° · 占位".format(t.distanceM, t.angleDeg.toInt()),
                                    fontFamily = Mono, fontSize = 8.sp,
                                    color = if (hasLoc) Gold else Gray,
                                )
                                // 威胁分级:与档案页战术评估共用同一套推演标准
                                val tier = radarThreatTier(t)
                                Text(tier.label, fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = tier.color)
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
                                    if (t.bleAdv?.hasRecord == true) {
                                        InlineInfoRow("广播", "TX ${if (t.txPowerDbm != null) "${t.txPowerDbm}dBm" else "—"} · UUID ${t.bleAdv.serviceUuids.size} 个 · 厂商ID ${if (t.bleAdv.manufacturerId >= 0) "0x%04X".format(t.bleAdv.manufacturerId) else "—"}")
                                    }
                                }
                                // 设备情报指纹
                                DeviceIntel.classify(t.name, t.id, t.bleAdv?.serviceUuids.orEmpty(), t.bleAdv?.manufacturerId)?.let { hit ->
                                    InlineInfoRow("设备情报", "${hit.label} · 置信 ${(hit.confidence * 100).toInt()}% · ${hit.basis}")
                                }
                                // 方位推演状态行
                                InlineInfoRow("方位推演", locLabel(locNow[t.id]))
                                InlineInfoRow("距离模型", if (t.txPowerDbm != null) "广播 ${t.txPowerDbm}dBm · n=2.5" else "假定 PTX · n=2.5(非测量)")
                                InlineInfoRow("跟踪", "命中 ${t.hitCount} 次 · 首见 ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(t.firstSeenMs))}")
                                Spacer(Modifier.height(5.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { onPair(t) },
                                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                    color = Gold.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f)),
                                ) {
                                    Text("配对弹窗消息 ▸", fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
                                }
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
            if (rows.size > MAX_RADAR_TRACK_ROWS) {
                Text(
                    "仅显示信号最强的 $MAX_RADAR_TRACK_ROWS 个 · 另有 ${rows.size - MAX_RADAR_TRACK_ROWS} 个未列出,可用上方筛选缩小范围",
                    fontFamily = Sans, fontSize = 8.sp, color = Gray,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** 方位推演状态文案 —— 列表展开行与档案页共用同一口径 */
private fun locLabel(e: BearingEstimate?): String = when {
    e == null -> "不可用（罗盘离线或样本不足）"
    e.state == LocState.ESTIMATED -> "%03d° ± %d° · 置信 %d%% · %d 圈".format(e.bearingDeg.toInt(), e.sectorHalfWidthDeg.toInt(), (e.confidence * 100).toInt(), e.sweepCount)
    e.state == LocState.COLLECTING -> "采样中 %d%% · 请缓慢旋转机身".format((e.coverage * 100).toInt())
    e.state == LocState.AMBIGUOUS && e.fwhmDeg >= 350f -> "方向图平坦,调制不足,方位不可信"
    e.state == LocState.AMBIGUOUS -> "多径多峰,方位不可靠"
    e.state == LocState.STALE -> "过期,最后更新 %ds 前".format(e.ageMs / 1000)
    else -> "不可用"
}

/** 内联详情行 —— 标签灰、数值白，等宽小字 */
@Composable
private fun InlineInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.width(38.dp))
        Text(value, fontFamily = Mono, fontSize = 8.sp, color = White, modifier = Modifier.weight(1f))
    }
}


/**
 * Probe 监视页 —— 被动 WiFi 帧字段监视 + 十六进制查看
 *
 * 复用雷达扫描数据(共享 RadarViewModel),按 Wireshark 式字段表展示:
 * BSSID 十六进制、SSID、信道/频率/中心频率/带宽、capabilities 原始字符串、
 * 安全解析、厂商 OUI。这是信标级被动观察 —— 无 root 的 Android 无法捕获
 * 全帧/数据帧,页面如实标注观察范围。
 */
@Composable
fun ProbeMonitorScreen(vm: RadarViewModel, back: () -> Unit) {
    val targets by vm.targets.collectAsState()
    val running by vm.isRunning.collectAsState()
    val haptic = LocalHapticFeedback.current
    var selectedId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        vm.acquireScan()
        onDispose { vm.releaseScan() }
    }

    val wifiTargets = targets.filter { it.type == "wifi" }.sortedByDescending { it.rssi }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("Probe 监视 · FRAME MONITOR", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (running) "● 被动监视中" else "○ 待机", fontFamily = Mono, fontSize = 10.sp, color = if (running) TacGreen else Gray, modifier = Modifier.weight(1f))
                    Text("信标帧 ${wifiTargets.size} 个", fontFamily = Mono, fontSize = 10.sp, color = Gold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "观察范围: 信标/探测响应帧的管理字段(WifiManager 扫描结果)。无 root 的 Android 无法捕获数据帧与全帧字节;如需全量抓包需 root + 外接网卡。",
                fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            if (wifiTargets.isEmpty()) {
                EmptyHint("等待 WiFi 帧 —— 打开定位服务后信标自动进入")
            } else {
                wifiTargets.take(60).forEach { t ->
                    val expanded = t.id == selectedId
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { haptic.hud(); selectedId = if (expanded) null else t.id },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = if (expanded) TacGreen.copy(alpha = 0.06f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (expanded) Gold.copy(alpha = 0.7f) else BorderDim),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.ssidHex().take(17), fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.weight(1f))
                                Text("${t.rssi}dBm", fontFamily = Mono, fontSize = 10.sp, color = if (t.rssi > -50) Danger else if (t.rssi > -70) Warn else Gray)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text("SSID: ${t.name} · ${t.channel} · ${t.frequencyMhz}MHz · ${t.channelWidth} · ${t.security}", fontFamily = Mono, fontSize = 8.5.sp, color = White)
                            if (expanded) {
                                Spacer(Modifier.height(6.dp))
                                // Wireshark 式字段表
                                ProbeField("BSSID (hex)", t.id)
                                ProbeField("Vendor OUI", "${t.vendor} · ${t.vendorOui}")
                                ProbeField("Channel", "${t.channel} · ${t.frequencyMhz} MHz")
                                ProbeField("Width", t.channelWidth.ifEmpty { "--" })
                                ProbeField("CenterFreq", "0: ${if (t.centerFreq0Mhz > 0) "${t.centerFreq0Mhz}MHz" else "--"} · 1: ${if (t.centerFreq1Mhz > 0) "${t.centerFreq1Mhz}MHz" else "--"}")
                                ProbeField("Capabilities", t.capabilitiesRaw.ifEmpty { "--" })
                                ProbeField("Security", t.security)
                                ProbeField("RSSI/SNR", "${t.rssi}dBm / ${t.snrDb}dB")
                                ProbeField("Beacons", "${t.hitCount} 次 · 首见 ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(t.firstSeenMs))}")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Probe 字段行 —— Wireshark 式键值 */
@Composable
private fun ProbeField(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.width(96.dp))
        Text(value, fontFamily = Mono, fontSize = 8.sp, color = White, modifier = Modifier.weight(1f))
    }
}

/** BSSID 十六进制形式(帧字段视图) */
private fun RadarTarget.ssidHex(): String = id.uppercase()

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
        val walkieVm = if (app != null) previewViewModel { WalkieTalkieViewModel(app) } else null
        val dbVm = if (app != null) previewViewModel { DbViewModel(app) } else null
        if (vm == null || walkieVm == null || dbVm == null) PreviewFallback("设备雷达")
        else RadarScreen(vm, walkieVm, dbVm, back = {}, radarAlertOn = true, onSelectTarget = {}, onOpenNetmap = {}, onOpenDb = {})
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
