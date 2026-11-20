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
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.HttpClientManager
import xin.ctkqiang.deauthctrl.manager.JammerScanState
import xin.ctkqiang.deauthctrl.manager.AdStructureDecoder
import xin.ctkqiang.deauthctrl.manager.DeviceIntel
import xin.ctkqiang.deauthctrl.manager.WifiDeauthEngine
import xin.ctkqiang.deauthctrl.manager.ArpEntry
import xin.ctkqiang.deauthctrl.manager.ArpScanManager
import xin.ctkqiang.deauthctrl.manager.GnssSat
import xin.ctkqiang.deauthctrl.manager.GsmTowerManager
import xin.ctkqiang.deauthctrl.manager.TowerCell
import xin.ctkqiang.deauthctrl.manager.GnssSkyManager
import xin.ctkqiang.deauthctrl.manager.RadarManager
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
import xin.ctkqiang.deauthctrl.ui.components.TacticalStatStrip
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
import kotlin.math.roundToInt
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
        TerminalHeader(stringResource(R.string.det_title_ble_flood), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                TermPanel(stringResource(R.string.det_panel_adv_payload)) {
                    Text(stringResource(R.string.det_label_forged_profile), fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(bottom = 5.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.getProfiles().forEach { p ->
                            SelectChip(selected = p == profile, text = stringResource(p.displayNameRes)) {
                                profile = p
                                vm.setProfile(p)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.det_label_cycle_mode), fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(bottom = 5.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.getCycleModes().forEach { m ->
                            SelectChip(selected = m == cycleMode, text = stringResource(m.labelRes)) {
                                cycleMode = m
                                vm.setCycleMode(m)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                TermPanel(stringResource(R.string.det_panel_tx_params)) {
                    Text(stringResource(R.string.det_label_tx_power), fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(bottom = 5.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.getTxPowerLevels().forEach { p ->
                            SelectChip(selected = p == txPower, text = stringResource(p.labelRes)) {
                                txPower = p
                                vm.setTxPower(p)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.det_label_tx_interval), fontFamily = Sans, fontSize = 11.sp, color = Gray)
                        Text("${interval.toLong()}ms", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                    }
                    Slider(interval, { interval = it; vm.setInterval(interval.toLong()) }, valueRange = 20f..100f, steps = 7, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = Track))
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TermInput(stringResource(R.string.det_label_max_packets), maxPacketsText, placeholder = stringResource(R.string.det_hint_leave_blank_unlimited), modifier = Modifier.weight(1f)) { v ->
                            maxPacketsText = v.filter { it.isDigit() }
                            vm.setMaxPackets(maxPacketsText.toLongOrNull() ?: -1L)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.det_hint_blank_unlimited_short), fontFamily = Sans, fontSize = 10.sp, color = Gray)
                    }
                }

                Spacer(Modifier.height(8.dp))

                ActionButton(running, stringResource(R.string.det_btn_run_flood), stringResource(R.string.det_btn_stop_flood)) {
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
    TermPanel(stringResource(R.string.det_panel_run_stats)) {
        val upSec = stats.uptimeMs / 1000
        LiveStat(stringResource(R.string.det_stat_sent), "${stats.sent}")
        LiveStat(stringResource(R.string.det_stat_failed), "${stats.failed}", if (stats.failed > 0) Danger else White)
        LiveStat(stringResource(R.string.det_stat_total_attempts), "${stats.total}")
        LiveStat(stringResource(R.string.det_stat_success_rate), "%.1f%%".format(stats.successRate * 100), if (stats.successRate > 0.5f) White else Danger)
        LiveStat(stringResource(R.string.det_stat_pps), "%.1f pps".format(stats.pps))
        LiveStat(stringResource(R.string.det_stat_uptime), "%02d:%02d".format(upSec / 60, upSec % 60))
        if (stats.failRateRecent > 0.3f) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.det_warn_recent_fail_rate, stats.failRateRecent * 100), fontFamily = Mono, fontSize = 10.sp, color = Danger)
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
        if (log.isEmpty()) item { HintBreathing(stringResource(R.string.det_hint_ble_flood_log)) }
        items(log.asReversed()) { e ->
            LogLine(
                tf.format(Date(e.timestamp)),
                "${stringResource(e.profile.displayNameRes)}  ${if (e.success) stringResource(R.string.det_value_success) else stringResource(R.string.det_stat_failed)}  #${e.index.toString().padStart(5, '0')}",
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
        TerminalHeader(stringResource(R.string.det_title_bt_jam), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                TermPanel(stringResource(R.string.det_panel_status)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatChip(stringResource(R.string.det_chip_ble_flood), if (running) stringResource(R.string.det_state_running) else stringResource(R.string.det_state_stopped), running)
                        StatChip(stringResource(R.string.det_chip_device_discovery), if (running) stringResource(R.string.det_state_running) else stringResource(R.string.det_state_stopped), running)
                    }
                }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, stringResource(R.string.det_btn_run_suppress), stringResource(R.string.det_btn_stop_suppress)) {
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
            Text(stringResource(R.string.det_label_devices_found), fontFamily = Sans, fontSize = 10.sp, color = Gray)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.det_fmt_device_count, count), fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
        }
        if (stats.blePackets > 0 || stats.inquiryCycles > 0) {
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.det_panel_run_stats)) {
                LiveStat(stringResource(R.string.det_stat_ble_packets), "${stats.blePackets}")
                LiveStat(stringResource(R.string.det_stat_scan_cycles), "${stats.inquiryCycles}")
                LiveStat(stringResource(R.string.det_stat_devices_found), "${stats.devicesFound}")
                LiveStat(stringResource(R.string.det_stat_uptime), stringResource(R.string.det_fmt_uptime_seconds, stats.uptimeMs / 1000))
            }
        }
        if (devices.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.det_panel_nearby_devices, devices.size)) {
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
        if (log.isEmpty()) item { HintBreathing(stringResource(R.string.det_hint_bt_jam_log)) }
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
fun WifiDetailScreen(vm: WifiDisruptViewModel, radarVm: RadarViewModel, initialBssid: String? = null, back: () -> Unit) {
    val ss by vm.scanState.collectAsState(); val fl by vm.isFlooding.collectAsState()
    var sel by remember { mutableStateOf<WifiNetwork?>(null) }
    var dur by remember { mutableIntStateOf(vm.getDuration()) }
    var toggle by remember { mutableFloatStateOf(vm.getToggleInterval().toFloat()) }
    val haptic = LocalHapticFeedback.current

    // 进入页面自动扫描附近网络(仅被动扫描;干扰泛洪仍需手动执行)
    LaunchedEffect(Unit) { if (ss is ScanState.Idle) vm.scanNetworks() }
    // 雷达弹窗「去攻击」跳转:扫描结果一到就自动选中目标 BSSID
    LaunchedEffect(ss, initialBssid) {
        if (initialBssid == null || sel != null) return@LaunchedEffect
        val r = ss as? ScanState.Results ?: return@LaunchedEffect
        r.networks.firstOrNull { it.bssid == initialBssid }?.let {
            sel = it
            vm.selectTarget(it)
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.det_title_wifi_disrupt), back)
        PageEnter(locked = fl) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                ActionButton(running = ss is ScanState.Scanning, runLabel = stringResource(R.string.det_btn_scan_networks), stopLabel = stringResource(R.string.det_btn_scanning)) {
                    haptic.hud()
                    vm.scanNetworks()
                }
                Spacer(Modifier.height(8.dp))

                when (val s = ss) {
                    is ScanState.Scanning -> Text(stringResource(R.string.det_msg_scanning_bands), fontFamily = Sans, fontSize = 12.sp, color = White)
                    is ScanState.Results -> {
                        val sorted = s.networks.sortedByDescending { it.signalStrength }
                        Text(stringResource(R.string.det_msg_networks_found, sorted.size), fontFamily = Sans, fontSize = 11.sp, color = Gray)
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(Modifier.fillMaxWidth().height(220.dp)) {
                            items(sorted, key = { it.bssid }) { n ->
                                val on = sel?.bssid == n.bssid
                                val barColor = if (n.signalStrength > -50) Danger else if (n.signalStrength > -70) Warn else TacGreenDim
                                Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); sel = n; vm.selectTarget(n) }, color = if (on) Red.copy(alpha = 0.08f) else Color.Transparent) {
                                    Row(Modifier.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (on) "▸" else " ", fontFamily = Mono, fontSize = 13.sp, color = Red, modifier = Modifier.width(18.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(n.ssid.ifBlank { stringResource(R.string.det_hidden_ssid) }.take(18), fontFamily = Mono, fontSize = 12.sp, color = if (on) White else Gray)
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
                        TermPanel(stringResource(R.string.det_panel_flood_params)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.det_label_target_network), fontFamily = Sans, fontSize = 11.sp, color = Gray)
                                Text(sel?.ssid ?: "-", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.det_label_disrupt_duration), fontFamily = Sans, fontSize = 11.sp, color = Gray)
                                Text("${dur}s", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                            }
                            Slider(dur.toFloat(), { dur = it.toInt().coerceIn(1, 60) }, valueRange = 1f..60f, steps = 14, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = Track))
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.det_label_toggle_interval), fontFamily = Sans, fontSize = 11.sp, color = Gray)
                                Text("${toggle.toLong()}ms", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                            }
                            Slider(toggle, { toggle = it; vm.setToggleInterval(toggle.toLong()) }, valueRange = 40f..500f, steps = 22, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = Track))
                        }
                        Spacer(Modifier.height(8.dp))
                        ActionButton(fl, stringResource(R.string.det_btn_run_disrupt), stringResource(R.string.det_btn_stop_disrupt)) {
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
                DeauthInjectionPanel(vm, radarVm, preselectBssid = sel?.bssid, preselectChannel = sel?.channel)
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
    radarVm: RadarViewModel,
    preselectBssid: String?,
    preselectChannel: Int?,
) {
    val session by vm.deauthSession.collectAsState()
    val haptic = LocalHapticFeedback.current
    var bssid by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("64") }
    // 设备选择器:点选雷达扫描结果,免手输 BSSID/客户端 MAC
    var pickFor by remember { mutableStateOf<String?>(null) } // "bssid" | "client" | null
    var startError by remember { mutableStateOf<String?>(null) }

    // 扫描列表选中目标后自动回填 BSSID/信道(用户仍可手改)
    LaunchedEffect(preselectBssid, preselectChannel) {
        if (bssid.isBlank() && preselectBssid != null) bssid = preselectBssid
        if (channel.isBlank() && preselectChannel != null) channel = preselectChannel.toString()
    }

    Spacer(Modifier.height(8.dp))
    TermPanel(stringResource(R.string.det_panel_deauth_injection)) {
        Text(
            stringResource(R.string.det_deauth_requires_root),
            fontFamily = Sans, fontSize = 8.sp, color = Warn, lineHeight = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickNavChip(stringResource(R.string.det_btn_probe_capability), Gold, Modifier.weight(1f)) {
                haptic.hud()
                vm.probeDeauth()
            }
            if (session.running) {
                QuickNavChip(stringResource(R.string.det_btn_stop_injection), Danger, Modifier.weight(1f)) {
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
                    stringResource(
                        R.string.det_deauth_missing,
                        if (!p.aireplay) stringResource(R.string.det_deauth_missing_aireplay) else "",
                        if (!p.monitorSupported) stringResource(R.string.det_deauth_missing_monitor) else "",
                    ),
                    fontFamily = Sans, fontSize = 8.sp, color = Warn,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TermInput(stringResource(R.string.det_label_target_bssid), bssid, placeholder = "AA:BB:CC:DD:EE:FF", modifier = Modifier.weight(1f)) { bssid = it; startError = null }
            Spacer(Modifier.width(6.dp))
            PickerButton { pickFor = "bssid" }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TermInput(stringResource(R.string.det_label_channel), channel, placeholder = stringResource(R.string.det_hint_channel_example), modifier = Modifier.weight(1f)) { channel = it }
            TermInput(stringResource(R.string.det_label_client_mac), client, placeholder = stringResource(R.string.det_hint_blank_broadcast), modifier = Modifier.weight(1.6f)) { client = it }
            PickerButton { pickFor = "client" }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TermInput(stringResource(R.string.det_label_frames_per_round), count, placeholder = stringResource(R.string.det_hint_persistent_loop), modifier = Modifier.weight(1f)) { count = it }
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
                    if (session.running) stringResource(R.string.det_btn_injecting, session.framesSent) else stringResource(R.string.det_btn_start_injection),
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
                if (session.running && session.phase == "被动嗅探") stringResource(R.string.det_btn_stop_sniffing, session.framesSent)
                else stringResource(R.string.det_btn_passive_sniff),
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
            stringResource(R.string.det_deauth_disclaimer),
            fontFamily = Sans, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.75f), lineHeight = 11.sp,
        )
    }

    // ── 设备选择器:点选雷达扫描结果,免手输 BSSID / 客户端 MAC ──
    pickFor?.let { field ->
        RadarDevicePickerDialog(
            vm = radarVm,
            title = stringResource(R.string.det_picker_title),
            typeFilter = if (field == "bssid") "wifi" else "all",
            onPick = { t ->
                when (field) {
                    "bssid" -> {
                        bssid = t.id
                        if (t.channel.isNotEmpty()) channel = t.channel.removePrefix("CH")
                    }
                    else -> client = if (t.type == "wifi") t.id else t.address
                }
                startError = null
                pickFor = null
            },
            onDismiss = { pickFor = null },
        )
    }
}

/** 「选择」按钮 —— 打开雷达设备选择器,替代手输地址 */
@Composable
private fun PickerButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = Gold.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.55f)),
    ) {
        Text(
            stringResource(R.string.picker_pick),
            fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = Gold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
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
                Text(stringResource(R.string.det_fmt_cycle, r.cycle, r.totalCycles), fontFamily = Sans, fontSize = 10.sp, color = White, modifier = Modifier.padding(top = 3.dp))
            }
            is HotspotResult.Completed -> Text(stringResource(R.string.det_msg_flood_complete), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(top = 6.dp))
            is HotspotResult.Error -> ErrorBanner(r.message)
            else -> {}
        }

        if (fl || stats.startCount > 0 || stats.stopCount > 0) {
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.det_panel_run_stats)) {
                LiveStat(stringResource(R.string.det_stat_cycle), "${stats.cycles}/${stats.totalCycles}")
                LiveStat(stringResource(R.string.det_stat_hotspot_starts), "${stats.startCount}")
                LiveStat(stringResource(R.string.det_stat_hotspot_stops), "${stats.stopCount}")
                LiveStat(stringResource(R.string.det_stat_uptime), stringResource(R.string.det_fmt_uptime_seconds, stats.uptimeMs / 1000))
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
        TerminalHeader(stringResource(R.string.det_title_wifi_jam), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermPanel(stringResource(R.string.det_panel_config)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.det_label_channel_switch_interval), fontFamily = Sans, fontSize = 11.sp, color = Gray)
                        Text("${ms.toLong()}ms", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                    }
                    Slider(ms, { ms = it; vm.setInterval(ms.toLong()) }, valueRange = 60f..300f, steps = 11, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = Track))
                }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, stringResource(R.string.det_btn_run_suppress), stringResource(R.string.det_btn_stop_suppress)) {
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
                Text(stringResource(R.string.det_fmt_jam_progress, tc, stats.cycles, stats.hotspotStarts, stats.hotspotFailures), fontFamily = Sans, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 3.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        when (val s = ss) {
            is JammerScanState.Scanning -> Text(stringResource(R.string.det_msg_scanning_targets), fontFamily = Sans, fontSize = 12.sp, color = White)
            is JammerScanState.Results -> {
                Text(stringResource(R.string.det_msg_targets_found, s.networks.size), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(0.4f)) {
                    items(s.networks.take(12)) { n ->
                        LogLine("CH${n.channel.toString().padStart(2)}", "${n.bssid.take(17).padEnd(17)}  ${n.ssid.ifBlank { stringResource(R.string.det_hidden_ssid) }.take(22)}  ${n.signalStrength}dBm", White)
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
            if (log.isEmpty()) item { HintBreathing(stringResource(R.string.det_hint_bt_jam_log)) }
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
        TerminalHeader(stringResource(R.string.det_title_web_server), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                TermPanel(stringResource(R.string.det_panel_web_file)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fileName, fontFamily = Mono, fontSize = 12.sp, color = White, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        GhostButton(stringResource(R.string.det_btn_choose_file)) { filePicker.launch(arrayOf("text/html")) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, stringResource(R.string.det_btn_run_server), stringResource(R.string.det_btn_stop_server)) {
                    haptic.hud()
                    if (running) vm.stopServer() else vm.startServer()
                }
                error?.let { ErrorBanner(it) }

                AnimatedVisibility(running) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                        Spacer(Modifier.height(8.dp))
                        TermPanel(stringResource(R.string.det_panel_server_info)) {
                            PanelStat(stringResource(R.string.det_label_listen_address), host)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.det_label_response_headers), fontFamily = Sans, fontSize = 10.sp, color = Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            headerFields.forEach { field ->
                                val shown = if (field.startsWith(CONTENT_LENGTH_PREFIX)) {
                                    stringResource(R.string.det_fmt_content_length, CONTENT_LENGTH_PREFIX)
                                } else {
                                    field
                                }
                                Text("  $shown", fontFamily = Mono, fontSize = 11.sp, color = if (field.startsWith("HTTP/")) White else Gray)
                            }
                            Text("  " + stringResource(R.string.det_fmt_other_status, otherStatuses), fontFamily = Sans, fontSize = 10.sp, color = Gray)
                            Spacer(Modifier.height(8.dp))
                            AsciiDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(stringResource(R.string.det_label_browser_url), fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(bottom = 2.dp))
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
    TermPanel(stringResource(R.string.det_panel_run_stats)) {
        LiveStat(stringResource(R.string.det_stat_total_requests), "${stats.totalRequests}")
        LiveStat(stringResource(R.string.det_stat_success_responses), "${stats.served200}")
        LiveStat(stringResource(R.string.det_stat_not_found), "${stats.notFound404}")
        LiveStat(stringResource(R.string.det_stat_rejected_method), "${stats.rejectedMethod}")
        LiveStat(stringResource(R.string.det_stat_bad_requests), "${stats.badRequests}")
        LiveStat(stringResource(R.string.det_stat_handler_errors), "${stats.handlerErrors}")
        LiveStat(stringResource(R.string.det_stat_active_connections), "${stats.activeConnections}")
        LiveStat(stringResource(R.string.det_stat_requests_per_minute), "%.1f".format(stats.requestsPerMinute))
        LiveStat(stringResource(R.string.det_stat_uptime), vm.formatUptime(stats.uptimeMs))
        LiveStat(stringResource(R.string.det_stat_status), if (running) stringResource(R.string.det_state_listening) else stringResource(R.string.det_state_stopped), if (running) White else Gray)
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
        HintBreathing(stringResource(R.string.det_hint_web_log))
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
fun ArpDetailScreen(vm: ArpScanViewModel, initialIp: String? = null, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current

    // 进入页面自动扫描局域网,离开自动停止(被动扫描,无破坏性)
    DisposableEffect(Unit) {
        if (!vm.isRunning.value) vm.scan()
        onDispose { vm.stop() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.det_title_arp_scan), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                ActionButton(running, stringResource(R.string.det_btn_run_scan), stringResource(R.string.det_btn_cancel_scan)) {
                    haptic.hud()
                    if (running) vm.stop() else vm.scan()
                }

                error?.let { ErrorBanner(it) }
                Spacer(Modifier.height(6.dp))
                ArpStatStrip(vm, running)

                // 高频数据区独立订阅：扫描期间探测进度/统计/设备/日志每秒多次刷新
                ArpLivePanel(vm, running = running, preselectIp = initialIp)
                if (!running) ArpIdleHint(vm)
            }
        }
    }
}

/** ARP 战术遥测条 —— 雷达同款视觉:进度/设备/网段/状态 */
@Composable
private fun ArpStatStrip(vm: ArpScanViewModel, running: Boolean) {
    val stats by vm.stats.collectAsState()
    val devices by vm.devices.collectAsState()
    TacticalStatStrip(
        listOf(
            "${stats.hostsScanned}/${stats.hostCount}" to stringResource(R.string.tact_strip_progress),
            "${devices.size}" to stringResource(R.string.tact_strip_devices),
            stats.subnet.ifBlank { "--" } to stringResource(R.string.tact_strip_subnet),
            (if (running) stringResource(R.string.tact_strip_on) else stringResource(R.string.tact_strip_off)) to stringResource(R.string.tact_strip_scan),
        ),
    )
}

/** BLE 嗅探战术遥测条 —— 设备数/最强信号/状态 */
@Composable
private fun BleStatStrip(vm: BleScannerViewModel, running: Boolean) {
    val devices by vm.devices.collectAsState()
    TacticalStatStrip(
        listOf(
            "${devices.size}" to stringResource(R.string.tact_strip_devices),
            (devices.maxOfOrNull { it.rssi }?.let { "${it}dBm" } ?: "--") to stringResource(R.string.tact_strip_strongest),
            (if (running) stringResource(R.string.tact_strip_on) else stringResource(R.string.tact_strip_off)) to stringResource(R.string.tact_strip_sniff),
        ),
    )
}

/** 端口扫描战术遥测条 —— 进度/开放端口/状态 */
@Composable
private fun PortStatStrip(vm: PortScannerViewModel, running: Boolean) {
    val results by vm.results.collectAsState()
    val scanned by vm.scanned.collectAsState()
    val total by vm.total.collectAsState()
    val open = results.count { it.state == "open" }
    TacticalStatStrip(
        listOf(
            "$scanned/$total" to stringResource(R.string.tact_strip_progress),
            "$open" to stringResource(R.string.tact_strip_open),
            (if (running) stringResource(R.string.tact_strip_on) else stringResource(R.string.tact_strip_off)) to stringResource(R.string.tact_strip_scan),
        ),
    )
}

/** 对讲机战术遥测条 —— 节点/说话中/直播/链路 */
@Composable
private fun WalkieStatStrip(vm: WalkieTalkieViewModel, running: Boolean) {
    val peers by vm.peers.collectAsState()
    val link by vm.link.collectAsState()
    val live by vm.isLive.collectAsState()
    TacticalStatStrip(
        listOf(
            "${peers.size}" to stringResource(R.string.tact_strip_nodes),
            "${peers.count { it.isTalking }}" to stringResource(R.string.tact_strip_talking),
            (if (live) stringResource(R.string.tact_strip_on) else stringResource(R.string.tact_strip_off)) to stringResource(R.string.tact_strip_live),
            (link.info?.rssiDbm?.let { "${it}dBm" } ?: "--") to stringResource(R.string.tact_strip_link),
        ),
    )
}

/** ARP 扫描实时面板 —— 独立订阅进度、统计、设备与日志;preselectIp 自动选中踢出目标 */
@Composable
private fun ArpLivePanel(vm: ArpScanViewModel, running: Boolean, preselectIp: String? = null) {    val devices by vm.devices.collectAsState()
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
    // 雷达同网弹窗「踢出网络」跳转:设备列表一到就自动选中目标 IP
    LaunchedEffect(devices, preselectIp) {
        if (kickTarget == null && preselectIp != null) {
            devices.firstOrNull { it.ip == preselectIp }?.let { kickTarget = it.ip to it.hostname }
        }
    }
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
            Text(stringResource(R.string.det_fmt_hosts_probed, stats.hostsScanned, stats.hostCount), fontFamily = Sans, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 3.dp))
        }

        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.det_panel_scan_stats)) {
            LiveStat(stringResource(R.string.det_stat_subnet), stats.subnet.ifEmpty { "—" })
            LiveStat(stringResource(R.string.det_stat_local_ip), stats.localIp.ifEmpty { "—" })
            LiveStat(stringResource(R.string.det_stat_probed), "${stats.hostsScanned} / ${stats.hostCount}")
            LiveStat(stringResource(R.string.det_stat_hosts_online), "${stats.hostsReachable}", if (stats.hostsReachable > 0) White else Gray)
            LiveStat(stringResource(R.string.det_stat_arp_cache_hits), "${stats.arpCacheHits}")
            LiveStat(stringResource(R.string.det_stat_reverse_dns), "${stats.hostnameResolved}")
            LiveStat(stringResource(R.string.det_stat_devices_found), "${stats.deviceCount}")
            LiveStat(stringResource(R.string.det_stat_elapsed), vm.formatElapsed(stats.elapsedMs))
            LiveStat(stringResource(R.string.det_stat_status), if (running) stringResource(R.string.det_state_scanning) else stringResource(R.string.det_state_idle), if (running) White else Gray)
        }

        if (devices.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.det_panel_devices_found, devices.size)) {
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
                                    "${d.mac.ifEmpty { stringResource(R.string.det_mac_pending) }} · ${d.hostname.ifEmpty { stringResource(R.string.det_hostname_unknown) }}",
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
                                Text(stringResource(R.string.det_btn_kick), fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Danger, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
                if (devices.size > ARP_DEVICE_RENDER_LIMIT) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.det_fmt_unrendered_devices, devices.size - ARP_DEVICE_RENDER_LIMIT), fontFamily = Sans, fontSize = 10.sp, color = Gray)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
        Spacer(Modifier.height(4.dp))
        if (log.isEmpty()) {
            HintBreathing(stringResource(R.string.det_hint_arp_log))
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
                        Text(stringResource(R.string.det_dialog_kick_title, ip), fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Danger)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.det_label_hostname, host.ifBlank { stringResource(R.string.det_unknown) }), fontFamily = Mono, fontSize = 9.sp, color = Gray)
                        Spacer(Modifier.height(10.dp))
                        TermInput(stringResource(R.string.det_label_gateway_ip), kickGateway, placeholder = stringResource(R.string.det_hint_gateway_example)) { kickGateway = it }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.det_arp_kick_mechanism),
                            fontFamily = Sans, fontSize = 8.5.sp, color = Gray, lineHeight = 13.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        kickResult?.let { Text(it, fontFamily = Sans, fontSize = 9.sp, color = if (it.startsWith("[!]")) Danger else TacGreen) }
                        if (deauthSession.running && deauthSession.phase == "ARP 踢人") {
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.det_fmt_poisoning_frames, deauthSession.framesSent), fontFamily = Mono, fontSize = 9.sp, color = Gold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                modifier = Modifier.weight(1f).clickable { kickHaptic.hud(); kickTarget = null },
                                shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, BorderDim),
                            ) {
                                Text(stringResource(R.string.det_btn_cancel), fontFamily = Sans, fontSize = 11.sp, color = Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                            }
                            Surface(
                                modifier = Modifier.weight(1f).clickable {
                                    kickHaptic.hud()
                                    if (deauthSession.running) {
                                        deauth.stop()
                                        kickResult = kickContext.getString(R.string.det_msg_kick_stopped)
                                    } else {
                                        kickResult = deauth.startArpKick(ip, kickGateway.trim(), 0, 200L)
                                            ?.let { "[!] $it" }
                                            ?: kickContext.getString(R.string.det_msg_kick_started)
                                    }
                                },
                                shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                                color = Danger.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, Danger.copy(alpha = 0.55f)),
                            ) {
                                Text(
                                    if (deauthSession.running && deauthSession.phase == "ARP 踢人") stringResource(R.string.det_btn_stop_poisoning) else stringResource(R.string.det_btn_start_kick),
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
        HintBreathing(stringResource(R.string.det_hint_arp_idle))
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
        TerminalHeader(stringResource(R.string.det_title_ble_scanner), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                ActionButton(running, stringResource(R.string.det_btn_run_sniff), stringResource(R.string.det_btn_stop_sniff)) {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                error?.let { ErrorBanner(it) }
                Spacer(Modifier.height(6.dp))
                BleStatStrip(vm, running)

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
                Text(stringResource(R.string.det_fmt_devices_found, devices.size), fontFamily = Sans, fontSize = 11.sp, color = White)
                Text(
                    stringResource(R.string.det_fmt_strongest, devices.maxOfOrNull { it.rssi }?.let { "${it}dBm" } ?: "--"),
                    fontFamily = Mono, fontSize = 9.sp, color = Gold,
                )
            }
        }

        if (!running && devices.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            HintBreathing(stringResource(R.string.det_hint_ble_scan_idle))
        }

        if (selDev != null) {
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.det_panel_device_detail, selDev.name)) {
                if (selDev.isDroneRemoteId) {
                    PanelStat(stringResource(R.string.det_label_drone), stringResource(R.string.det_value_drone_remote_id))
                    Spacer(Modifier.height(4.dp))
                }
                PanelStat(stringResource(R.string.det_label_address), selDev.address)
                PanelStat(stringResource(R.string.det_label_tx_power_stat), if (selDev.txPower > Int.MIN_VALUE) "${selDev.txPower}dBm" else stringResource(R.string.det_unknown))
                PanelStat(stringResource(R.string.det_label_device_type), selDev.deviceType.ifEmpty { stringResource(R.string.det_unknown) })
                if (selDev.services.isNotEmpty()) {
                    PanelStat(stringResource(R.string.det_label_services), selDev.services.joinToString(", "))
                }
                if (selDev.manufacturerData.isNotEmpty()) {
                    PanelStat(stringResource(R.string.det_label_manufacturer_data), selDev.manufacturerData.entries.joinToString { "${it.key}: ${it.value}" })
                }

                if (selDev.rssiHistory.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.det_label_rssi_trend), fontFamily = Sans, fontSize = 10.sp, color = RedDim, modifier = Modifier.padding(bottom = 3.dp))
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
                    Text(stringResource(R.string.det_fmt_rssi_minmax, selDev.rssiHistory.minOrNull() ?: 0, selDev.rssiHistory.maxOrNull() ?: 0, selDev.rssi), fontFamily = Sans, fontSize = 9.sp, color = Gray, modifier = Modifier.padding(top = 2.dp))
                }
                // ── Wireshark 式原始广播十六进制 ──
                if (selDev.advBytesHex.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.det_label_raw_adv), fontFamily = Sans, fontSize = 10.sp, color = RedDim, modifier = Modifier.padding(bottom = 3.dp))
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
                        stringResource(R.string.det_raw_adv_note),
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
                        Text(stringResource(R.string.det_label_ad_decode), fontFamily = Sans, fontSize = 10.sp, color = RedDim, modifier = Modifier.padding(bottom = 3.dp))
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
        Text(stringResource(R.string.det_fmt_devices_found_tap, devices.size), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
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
        TerminalHeader(stringResource(R.string.det_title_ping), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput(stringResource(R.string.det_label_target_host), host, placeholder = stringResource(R.string.det_hint_host_example)) { vm.setHost(it) }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, stringResource(R.string.det_btn_run_probe), stringResource(R.string.det_btn_stop_probe)) {
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
            HintBreathing(stringResource(R.string.det_hint_ping_idle))
        }

        if (sent > 0) {
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.det_panel_stats)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatChip(stringResource(R.string.det_stat_sent_count), "$sent")
                    StatChip(stringResource(R.string.det_stat_received_count), "$recv")
                    StatChip(stringResource(R.string.det_stat_loss), "${loss}%", loss == 0)
                    StatChip(stringResource(R.string.det_stat_min), "${min}ms")
                    StatChip(stringResource(R.string.det_stat_avg), "${avg}ms")
                    StatChip(stringResource(R.string.det_stat_max), "${max}ms")
                }
            }
        }

        if (results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.det_fmt_probe_results, host), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
            Spacer(Modifier.height(4.dp))
            LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(results, key = { it.sequence }) { r ->
                    LogLine("seq=${r.sequence}", stringResource(R.string.det_fmt_ping_reply, r.bytes, r.ip, r.ttl, r.timeMs), if (r.timeMs > 500) Danger else if (r.timeMs > 100) RedDim else White)
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
        TerminalHeader(stringResource(R.string.det_title_http_client), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                TermInput(stringResource(R.string.det_label_request_url), url, placeholder = "https://example.com") { vm.setUrl(it) }

                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.det_label_request_method), fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(bottom = 5.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    HttpClientManager.SUPPORTED_METHODS.forEach { m ->
                        SelectChip(selected = m == verb, text = m) { vm.setMethod(m) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TermInput(stringResource(R.string.det_label_request_headers), headers, placeholder = stringResource(R.string.det_hint_headers_placeholder)) { vm.setHeaders(it) }

                if (verb in HttpClientManager.BODY_METHODS) {
                    Spacer(Modifier.height(8.dp))
                    TermInput(stringResource(R.string.det_label_request_body), body) { vm.setBody(it) }
                }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, stringResource(R.string.det_btn_send_request), stringResource(R.string.det_btn_cancel_request)) {
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
                    Text(stringResource(R.string.det_fmt_response_status, r.statusCode, r.timeMs), fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (r.statusCode < 400) White else Danger,
                    )
                    PanelStat(stringResource(R.string.det_label_response_size), vm.formatBytes(r.bodyBytes.toLong()))
                    PanelStat(stringResource(R.string.det_label_charset), r.charset)
                    if (r.redirected && r.finalUrl.isNotEmpty()) {
                        PanelStat(stringResource(R.string.det_label_redirect_target), r.finalUrl, Gray)
                    }
                    if (r.truncated) {
                        Text(stringResource(R.string.det_warn_body_truncated, vm.formatBytes(HttpClientManager.MAX_BODY_BYTES.toLong())), fontFamily = Sans, fontSize = 10.sp, color = Danger, modifier = Modifier.padding(top = 3.dp))
                    }

                    if (r.headers.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        TermPanel(stringResource(R.string.det_panel_response_headers, r.headers.size)) {
                            r.headers.entries.sortedBy { it.key.lowercase(Locale.US) }.forEach { (k, v) ->
                                Text("$k: ${v.joinToString(", ")}", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.det_label_response_body), fontFamily = Sans, fontSize = 10.sp, color = RedDim, modifier = Modifier.padding(bottom = 4.dp))
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = BODY_VIEWER_MAX_HEIGHT)
                            .background(SurfaceBg)
                            .border(1.dp, Track, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                            .padding(10.dp),
                    ) {
                        item { SelectionContainer { Text(r.body.ifEmpty { stringResource(R.string.det_empty_body) }, fontFamily = Mono, fontSize = 10.sp, color = White) } }
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
    TermPanel(stringResource(R.string.det_panel_request_stats)) {
        LiveStat(stringResource(R.string.det_stat_total_requests), "${stats.attempts}")
        LiveStat(stringResource(R.string.det_stat_success_2xx), "${stats.successCount}")
        LiveStat(stringResource(R.string.det_stat_redirect_3xx), "${stats.redirectCount}")
        LiveStat(stringResource(R.string.det_stat_client_error_4xx), "${stats.clientErrorCount}")
        LiveStat(stringResource(R.string.det_stat_server_error_5xx), "${stats.serverErrorCount}")
        LiveStat(stringResource(R.string.det_stat_failed), "${stats.failureCount}", if (stats.failureCount > 0) Danger else White)
        LiveStat(stringResource(R.string.det_stat_bytes_received), vm.formatBytes(stats.bytesReceived))
        LiveStat(stringResource(R.string.det_stat_avg_duration), "${stats.averageDurationMs} ms")
        LiveStat(stringResource(R.string.det_stat_last_status), if (stats.lastStatusCode == 0) "-" else "${stats.lastStatusCode}")
        LiveStat(stringResource(R.string.det_stat_last_duration), "${stats.lastDurationMs} ms")
        if (stats.lastError.isNotEmpty()) LiveStat(stringResource(R.string.det_stat_last_error), stats.lastError, Danger)
        if (stats.redirected && stats.lastFinalUrl.isNotEmpty()) LiveStat(stringResource(R.string.det_stat_last_redirect), stats.lastFinalUrl, Gray)
        LiveStat(stringResource(R.string.det_stat_status), if (running) stringResource(R.string.det_state_running) else stringResource(R.string.det_state_idle), if (running) White else Gray)
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
        HintBreathing(stringResource(R.string.det_hint_http_log))
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
fun PortScannerScreen(vm: PortScannerViewModel, initialHost: String? = null, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val host by vm.host.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current

    // 雷达同网弹窗「扫端口」跳转:目标 IP 自动填入
    LaunchedEffect(Unit) {
        if (host.isBlank()) initialHost?.let { vm.setHost(it) }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.det_title_port_scan), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput(stringResource(R.string.det_label_target_host), host, placeholder = stringResource(R.string.det_hint_target_ip_example)) { vm.setHost(it) }
                Spacer(Modifier.height(8.dp))
                ActionButton(running, stringResource(R.string.det_btn_run_scan), stringResource(R.string.det_btn_cancel_scan)) {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                error?.let { ErrorBanner(it) }
                Spacer(Modifier.height(6.dp))
                PortStatStrip(vm, running)

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
            Text(stringResource(R.string.det_fmt_ports_scanned, scanned, total, open.size), fontFamily = Sans, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 3.dp))
        }

        if (!running && results.isEmpty() && error == null) {
            Spacer(Modifier.height(8.dp))
            HintBreathing(stringResource(R.string.det_hint_port_scan_idle))
        }

        if (results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.det_fmt_scan_report, host), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                item {
                    TermPanel(stringResource(R.string.det_fmt_scan_report, host)) {
                        if (open.isEmpty()) Text(stringResource(R.string.det_msg_no_open_ports), fontFamily = Sans, fontSize = 11.sp, color = Gray)
                        open.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${p.port}/tcp", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.width(72.dp))
                                Text(stringResource(R.string.det_state_open), fontFamily = Sans, fontSize = 10.sp, color = Red, modifier = Modifier.width(44.dp))
                                Text(p.service, fontFamily = Mono, fontSize = 11.sp, color = White)
                            }
                            if (p.banner.isNotEmpty()) Text("  └ ${p.banner}", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.det_fmt_scan_summary, total, open.size), fontFamily = Sans, fontSize = 10.sp, color = Gray)
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
        TerminalHeader(stringResource(R.string.det_title_walkie), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize()) {
                // ── 滚动区:整页滚动,PTT 圆盘固定在底栏外 ──
                Column(
                    Modifier.weight(1f).navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp).padding(top = 12.dp),
                ) {
                    ActionButton(running, stringResource(R.string.det_btn_run_walkie), stringResource(R.string.det_btn_stop_walkie)) {
                        haptic.hud()
                        if (running) vm.stop() else vm.start()
                    }
                    error?.let { ErrorBanner(it) }
                    Spacer(Modifier.height(6.dp))
                    WalkieStatStrip(vm, running)
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
                                if (isLive) stringResource(R.string.det_btn_live_active, countdown) else stringResource(R.string.det_btn_live_mode),
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
                                        if (active) stringResource(R.string.det_state_transmitting) else stringResource(R.string.det_btn_push_to_talk),
                                        fontFamily = Sans, fontSize = if (active) 15.sp else 14.sp, fontWeight = FontWeight.Black,
                                        color = if (active) White else Red,
                                    )
                                    Text("PTT", fontFamily = Mono, fontSize = 8.sp, color = if (active) White.copy(alpha = 0.8f) else Gray)
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.det_hint_ptt),
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
    TermPanel(stringResource(R.string.det_panel_local_link), Modifier.padding(top = 8.dp)) {
        if (info == null) {
            Text(stringResource(R.string.det_msg_wifi_unavailable), fontFamily = Sans, fontSize = 10.sp, color = Gray)
        } else if (!info.connected) {
            Text(stringResource(R.string.det_msg_wifi_disconnected), fontFamily = Sans, fontSize = 10.sp, color = Gray)
        } else {
            fun rssiColor(level: Int?): Color = when (level) {
                3, 4 -> TacGreen
                2 -> Warn
                1, 0 -> Danger
                else -> Gray
            }
            WalkieInfoRow("SSID", info.ssid ?: stringResource(R.string.det_value_ssid_hidden))
            WalkieInfoRow("BSSID", info.bssid ?: stringResource(R.string.det_value_invisible))
            WalkieInfoRow(stringResource(R.string.det_label_local_ip_gateway), "${info.localIp ?: "—"} · ${info.gatewayIp ?: "—"}")
            WalkieInfoRow(
                stringResource(R.string.det_label_signal_strength),
                if (info.rssiDbm != null) "${info.rssiDbm} dBm · ${walkieQuality(info.signalLevel)}" else stringResource(R.string.det_value_not_provided),
            )
            WalkieInfoRow(stringResource(R.string.det_label_channel_frequency), "CH${info.channel ?: "—"} · ${info.frequencyMhz ?: "—"} MHz")
            val rate = when {
                info.txLinkSpeedMbps != null && info.rxLinkSpeedMbps != null -> "TX ${info.txLinkSpeedMbps} · RX ${info.rxLinkSpeedMbps} Mbps"
                info.linkSpeedMbps != null -> "${info.linkSpeedMbps} Mbps"
                else -> stringResource(R.string.det_value_not_provided)
            }
            WalkieInfoRow(stringResource(R.string.det_label_negotiated_rate), rate)
            WalkieInfoRow(stringResource(R.string.det_label_wifi_standard), info.wifiStandard ?: stringResource(R.string.det_value_not_provided))
            if (info.rssiDbm != null) {
                // 信号条:RSSI -100..-30 → 0..5 格
                val bars = ((info.rssiDbm + 100f) / 70f * 5f).toInt().coerceIn(0, 5)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▮".repeat(bars) + "▯".repeat(5 - bars), fontFamily = Mono, fontSize = 11.sp, color = rssiColor(info.signalLevel))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.det_hint_local_ap_measured), fontFamily = Sans, fontSize = 7.5.sp, color = Gray)
                }
            }
        }
    }
}

/** 链路质量文字 —— 与信号条同一 RSSI 分档口径 */
@Composable
private fun walkieQuality(level: Int?): String = when (level) {
    4 -> stringResource(R.string.det_quality_excellent)
    3 -> stringResource(R.string.det_quality_good)
    2 -> stringResource(R.string.det_quality_fair)
    1 -> stringResource(R.string.det_quality_weak)
    0 -> stringResource(R.string.det_quality_very_weak)
    else -> "—"
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
        TermPanel(stringResource(R.string.det_panel_online_nodes, peers.size)) {
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
                                Text(stringResource(R.string.det_tag_gateway), fontFamily = Sans, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = TacGreen, modifier = Modifier.padding(end = 4.dp))
                            }
                            Text(p.ip, fontFamily = Mono, fontSize = 10.sp, color = Gray)
                            if (p.isTalking) {
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.det_state_talking), fontFamily = Sans, fontSize = 9.sp, color = Red, fontWeight = FontWeight.Bold)
                            }
                        }
                        // 链路质量行:RTT 为本机实测,自报字段明确标注
                        Spacer(Modifier.height(2.dp))
                        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val rttText = when {
                                p.rttEwmaMs != null -> {
                                    val c = when { p.rttEwmaMs <= 30f -> TacGreen; p.rttEwmaMs <= 80f -> Warn; else -> Danger }
                                    Text(
                                        stringResource(R.string.det_fmt_echo, p.rttEwmaMs.toInt()) + (p.jitterMs?.let { stringResource(R.string.det_fmt_jitter, it.toInt()) } ?: "") + (p.lossPct?.let { stringResource(R.string.det_fmt_loss, it) } ?: ""),
                                        fontFamily = Mono, fontSize = 8.sp, color = c,
                                    )
                                }
                                p.rttStatus == WalkieRttStatus.NoEcho -> Text(stringResource(R.string.det_msg_echo_no_response), fontFamily = Mono, fontSize = 8.sp, color = Danger)
                                else -> Text(stringResource(R.string.det_msg_echo_measuring), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                            }
                            Spacer(Modifier.weight(1f))
                            if (p.selfRssiDbm != null || p.selfFrequencyMhz != null) {
                                Text(
                                    stringResource(R.string.det_fmt_self_reported, p.selfRssiDbm?.let { "$it dBm" } ?: "—", p.selfFrequencyMhz?.let { "$it MHz" } ?: "—"),
                                    fontFamily = Mono, fontSize = 8.sp, color = Gold,
                                )
                            }
                        }
                    }
                    }
                }
            }
            Text(
                stringResource(R.string.det_peers_metrics_note),
                fontFamily = Sans, fontSize = 7.sp, color = Gray.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    } else if (running) {
        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.det_panel_online_nodes, 0)) {
            Text(stringResource(R.string.det_msg_no_peers), fontFamily = Sans, fontSize = 10.sp, color = Gray)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 设备雷达
// ══════════════════════════════════════════════════════════════════════════

/** 目标威胁分级 —— 雷达页实时标记与档案页战术评估共用同一套推演标准 */
data class RadarThreatTier(val label: String, val color: Color, val desc: String, val key: String = "")

/**
 * 雷达固定类别色 —— 与队伍主题彻底解耦。
 *
 * 此前 blip 用 Danger/TacGreen(主题色),切到红队后 WiFi 红、蓝牙也红、
 * 队友节点也红,盘面一片同色。现在四个类别各占一个固定色,任何队伍下互不撞色:
 * WiFi 红(敌性) / BLE 青 / 经典蓝牙绿 / 已绑定友方蓝。网格节点仍用主题金
 * (队伍身份色),靠菱形形状与圆形 blip 区分。档案页身份徽章复用同一套颜色。
 */
internal val WIFI_BLIP = Color(0xFFFF4D5E)
internal val BLE_BLIP = Color(0xFF00E5FF)
internal val BT_BLIP = Color(0xFF00E676)
internal val FRIENDLY_BLIP = Color(0xFF4D9FFF)
/** 基站层固定色:紫 —— 与 blip(红/青/绿/蓝)、同网(琥珀)、网格(金)全部错开 */
private val CELL_TOWER_COLOR = Color(0xFFB388FF)
private val TIER_HIGH = Color(0xFFFF4D5E)
private val TIER_MID = Color(0xFFFFB547)
private val TIER_FAR = Color(0xFF6AD18A)

/** 目标 → 类别色:友方优先,其次 WiFi/BLE/经典蓝牙 */
internal fun radarBlipColor(t: RadarTarget): Color = when {
    t.bonded -> FRIENDLY_BLIP
    t.type == "wifi" -> WIFI_BLIP
    t.observedVia == "ble" -> BLE_BLIP
    else -> BT_BLIP
}

/** 类型过滤匹配 —— BT 与 BLE 是两条独立观测路径,筛选必须分开;设备选择器复用 */
internal fun matchesTypeFilter(t: RadarTarget, filter: String): Boolean = when (filter) {
    "wifi" -> t.type == "wifi"
    "ble" -> t.type == "bt" && t.observedVia == "ble"
    "bt" -> t.type == "bt" && t.observedVia != "ble"
    else -> true
}

/**
 * 按信号强度与估算距离推演目标威胁分级
 *
 * 距离由 RSSI 路径损耗模型估算，非测量值（手机无测距硬件）；
 * 分级阈值与档案页历史逻辑一致：贴身/强信号 → 高危，近距/稳定 → 中等，其余 → 远端背景。
 * 已绑定设备一律友方 —— 无论多近都不算敌对目标。
 */
fun radarThreatTier(t: RadarTarget): RadarThreatTier = when {
    t.bonded -> RadarThreatTier(
        "友方设备", FRIENDLY_BLIP,
        "已绑定/已连接的已知设备,视为非敌对目标 —— 雷达上蓝色高亮,不触发触觉警报",
        key = "friendly",
    )
    t.distanceM < 8f || t.rssi > -45 -> RadarThreatTier(
        "高危接近", TIER_HIGH,
        "目标信号极强 / 估算距离 ${"%.1f".format(t.distanceM)}m，位于贴身范围内，建议立即甄别身份",
        key = "high",
    )
    t.distanceM < 25f || t.rssi > -65 -> RadarThreatTier(
        "中等接近", TIER_MID,
        "目标位于 ${"%.1f".format(t.distanceM)}m 附近，信号稳定，持续跟踪其移动轨迹",
        key = "mid",
    )
    else -> RadarThreatTier(
        "远端目标", TIER_FAR,
        "目标距本机较远（${"%.1f".format(t.distanceM)}m），属环境背景设备，维持常规监视",
        key = "far",
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
    arpVm: ArpScanViewModel,
    back: () -> Unit,
    radarAlertOn: Boolean,
    onSelectTarget: (String) -> Unit,
    onOpenNetmap: () -> Unit,
    onOpenDb: () -> Unit,
    onDeauth: (RadarTarget) -> Unit,
    onScanPorts: (String) -> Unit,
    onSsh: (String) -> Unit,
    onKick: (String) -> Unit,
) {
    val running by vm.isRunning.collectAsState()
    val haptic = LocalHapticFeedback.current

    // 过滤状态：类型 + 关键词 + 聚焦目标
    var typeFilter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var focusedId by remember { mutableStateOf<String?>(null) }
    // 卫星层开关(GNSS 实例在下方 context 声明之后构造)
    var satelliteOn by remember { mutableStateOf(true) }
    // 同网设备层:ARP Ping Sweep 发现本机所在网段的在线主机,叠加到雷达外环 —— 默认开启
    var lanOn by remember { mutableStateOf(true) }
    val lanDevices by arpVm.devices.collectAsState()
    val lanScanning by arpVm.isRunning.collectAsState()
    LaunchedEffect(lanOn) {
        if (lanOn) {
            // 每 60s 重扫一遍网段:新上线主机自动进环
            while (true) {
                if (!arpVm.isRunning.value) arpVm.scan()
                delay(60_000L)
            }
        } else {
            arpVm.stop()
        }
    }
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

    // 基站层:本机注册小区 + 邻区(TelephonyManager.allCellInfo 真实数据) —— 默认开启
    var towersOn by remember { mutableStateOf(true) }
    val gsmManager = remember { GsmTowerManager(context) }
    val towerState by gsmManager.state.collectAsState()
    LaunchedEffect(towersOn) {
        if (towersOn) {
            while (true) {
                runCatching { gsmManager.refresh() }
                delay(15_000L)
            }
        }
    }

    // 聚焦目标距离振动：越近振幅越大、节奏越快；越远渐弱；超出雷达量程静默。
    // 友方(已绑定)设备不震动 —— 非敌对目标不该被触觉警报骚扰
    LaunchedEffect(focusedId, radarAlertOn) {
        while (true) {
            val t = focusedId?.let { id -> vm.targets.value.firstOrNull { it.id == id } }
            if (t == null || !radarAlertOn || t.bonded || t.distanceM > 100f) {
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
        TerminalHeader(stringResource(R.string.det_title_radar), back)
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
                    ActionButton(running, stringResource(R.string.det_btn_run_radar), stringResource(R.string.det_btn_stop_radar), modifier = Modifier.weight(1f)) {
                        haptic.hud()
                        if (running) vm.stop() else vm.start()
                    }
                    QuickNavChip(stringResource(R.string.det_btn_network_topology), Gold, Modifier.width(62.dp)) { haptic.hud(); onOpenNetmap() }
                    QuickNavChip(stringResource(R.string.det_btn_signal_db), Gold, Modifier.width(62.dp)) { haptic.hud(); onOpenDb() }
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
                            Text(stringResource(R.string.det_warn_location_off), fontFamily = Sans, fontSize = 10.sp, color = Warn, modifier = Modifier.weight(1f))
                            Text(
                                stringResource(R.string.det_btn_open_settings),
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
                        lanHosts = if (lanOn) lanDevices else emptyList(),
                        towers = if (towersOn) towerState.towers else emptyList(),
                        onTapDot = { id -> haptic.hud(); focusedId = id },
                        onTapEmpty = { focusedId = null },
                        onOpenDossier = { id -> haptic.hud(); onSelectTarget(id) },
                        onDeauth = onDeauth,
                        onScanPorts = onScanPorts,
                        onSsh = onSsh,
                        onKick = onKick,
                        modifier = Modifier.width(disc).height(disc + 34.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                MetalDetectStrip(vm)
                Spacer(Modifier.height(6.dp))
                TacticalStatusStrip(vm, walkieVm, dbVm)
                Spacer(Modifier.height(6.dp))
                // ── 筛选行:等宽分段控件 + 卫星/同网开关,搜索独占下一行 ──
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadarFilterBar(
                        typeFilter = typeFilter,
                        onTypeFilter = { typeFilter = it; focusedId = null },
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
                            if (gnssState.satellites.isEmpty()) stringResource(R.string.det_sat_none)
                            else stringResource(R.string.det_fmt_satellites, gnssState.satellites.size),
                            fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = if (satelliteOn) Gold else Gray,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 9.dp),
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    // 同网设备层开关:ARP Ping Sweep 发现本网段在线主机,叠加到雷达外环
                    Surface(
                        modifier = Modifier.clickable {
                            haptic.hud()
                            lanOn = !lanOn
                        },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = if (lanOn) Warn.copy(alpha = 0.14f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (lanOn) Warn.copy(alpha = 0.6f) else BorderDim),
                    ) {
                        Text(
                            if (lanScanning) stringResource(R.string.det_lan_scanning)
                            else stringResource(R.string.det_fmt_lan, lanDevices.size),
                            fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = if (lanOn) Warn else Gray,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 9.dp),
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    // 基站层开关:注册小区 + 邻区(紫三角标记)
                    Surface(
                        modifier = Modifier.clickable {
                            haptic.hud()
                            towersOn = !towersOn
                        },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = if (towersOn) CELL_TOWER_COLOR.copy(alpha = 0.14f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (towersOn) CELL_TOWER_COLOR.copy(alpha = 0.6f) else BorderDim),
                    ) {
                        Text(
                            stringResource(R.string.det_fmt_towers, towerState.towers.size),
                            fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = if (towersOn) CELL_TOWER_COLOR else Gray,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 9.dp),
                        )
                    }
                }
                // ── 搜索行:独占整行,⌕ 前缀 ──
                Spacer(Modifier.height(5.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it; focusedId = null },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = White),
                    cursorBrush = SolidColor(Gold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, BorderDim, CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⌕", fontFamily = Mono, fontSize = 11.sp, color = Gold.copy(alpha = 0.7f))
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(stringResource(R.string.det_hint_filter_device_name), fontFamily = Sans, fontSize = 10.sp, color = Gray.copy(alpha = 0.5f))
                                }
                                inner()
                            }
                        }
                    },
                )
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
                    Text(stringResource(R.string.det_dialog_pair_title, t.name.take(16)), fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Spacer(Modifier.height(4.dp))
                    Text(t.address, fontFamily = Mono, fontSize = 9.sp, color = Gray)
                    Spacer(Modifier.height(10.dp))
                    TermInput(stringResource(R.string.det_label_popup_message), pairMsg, placeholder = stringResource(R.string.det_hint_popup_message)) { pairMsg = it }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.det_pair_mechanism),
                        fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    pairResult?.let { Text(it, fontFamily = Sans, fontSize = 9.sp, color = if (it.startsWith("[!]")) Danger else TacGreen) }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            modifier = Modifier.weight(1f).clickable { haptic.hud(); pairTarget = null },
                            shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, BorderDim),
                        ) {
                            Text(stringResource(R.string.det_btn_cancel), fontFamily = Sans, fontSize = 11.sp, color = Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                        }
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                haptic.hud()
                                if (pairMsg.isBlank()) {
                                    xin.ctkqiang.deauthctrl.ui.components.StickerBus.show(context.getString(R.string.det_sticker_enter_message))
                                    pairResult = context.getString(R.string.det_error_message_empty)
                                } else {
                                    pairResult = vm.sendPairingPopup(t.address, pairMsg)?.let { "[!] $it" }
                                        ?: context.getString(R.string.det_msg_pair_sent)
                                }
                            },
                            shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                            color = Red.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Red.copy(alpha = 0.55f)),
                        ) {
                            Text(stringResource(R.string.det_btn_send_popup), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
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
    val context = LocalContext.current
    val lastRefresh by dbVm.lastRefresh.collectAsState()
    val lastInserted by dbVm.lastInserted.collectAsState()
    // 闪现消息:(触发时间, 文案) —— 所有反馈走同一套 1.6s 自动消失计时器
    var flash by remember { mutableStateOf<Pair<Long, String>?>(null) }
    LaunchedEffect(lastRefresh) {
        if (lastRefresh != null) flash = System.currentTimeMillis() to context.getString(R.string.det_fmt_stored, lastInserted)
    }
    LaunchedEffect(flash) {
        if (flash != null) { delay(1600); flash = null }
    }
    QuickNavChip(
        label = flash?.second ?: stringResource(R.string.det_btn_snapshot),
        color = if (flash != null) TacGreen else Gold,
        modifier = Modifier.width(52.dp),
        onClick = {
            haptic.hud()
            if (vm.targets.value.isEmpty()) {
                flash = System.currentTimeMillis() to context.getString(R.string.det_msg_no_targets)
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

    val high = targets.count { radarThreatTier(it).key == "high" }
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
            CompactStat(stringResource(R.string.det_compact_targets), "${targets.size}", TacGreen, Modifier.weight(1f))
            StatDivider()
            CompactStat(stringResource(R.string.det_compact_high_risk), "$high", Danger, Modifier.weight(1f))
            StatDivider()
            CompactStat(stringResource(R.string.det_compact_nodes), if (walkieRunning) "${peers.size}" else "—", Gold, Modifier.weight(1f))
            StatDivider()
            CompactStat(stringResource(R.string.det_compact_intel), "$dbTotal", White, Modifier.weight(1f))
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
                Text(stringResource(R.string.det_panel_metal_detect), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent)
                Spacer(Modifier.weight(1f))
                val status = when {
                    m == null -> stringResource(R.string.det_metal_unavailable)
                    !m.calibrated -> stringResource(R.string.det_fmt_calibrating, (m.calProgress * 100).toInt())
                    anomaly -> stringResource(R.string.det_metal_anomaly)
                    else -> stringResource(R.string.det_metal_stable)
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
                stringResource(R.string.det_metal_note),
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
                Text(stringResource(R.string.det_panel_mesh_nodes), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold)
                Spacer(Modifier.width(8.dp))
                if (running) {
                    Text(stringResource(R.string.det_fmt_online, peers.size), fontFamily = Mono, fontSize = 9.sp, color = TacGreen)
                    if (talking) {
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.det_state_transmitting), fontFamily = Sans, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Red)
                    }
                } else {
                    Text(stringResource(R.string.det_state_not_running), fontFamily = Mono, fontSize = 9.sp, color = Gray)
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
                        stringResource(R.string.det_btn_tap_to_start),
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
                Text(stringResource(R.string.det_fmt_heading, northRef), fontFamily = Sans, fontSize = 7.sp, color = Gray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%+d°".format(pitch.toInt()), fontFamily = Mono, fontSize = 11.sp, color = Gold)
                Text(stringResource(R.string.det_label_pitch), fontFamily = Sans, fontSize = 8.sp, color = Gray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%+d°".format(roll.toInt()), fontFamily = Mono, fontSize = 11.sp, color = Gold)
                Text(stringResource(R.string.det_label_roll), fontFamily = Sans, fontSize = 8.sp, color = Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (online) stringResource(R.string.det_compass_online) else stringResource(R.string.det_compass_offline), fontFamily = Mono, fontSize = 8.sp, color = if (online) TacGreen else Danger)
                Text(stringResource(R.string.det_compass_sensor), fontFamily = Mono, fontSize = 8.sp, color = Gray)
            }
        }
    }
}

/** 雷达过滤条 —— 类型芯片 + 名称搜索 */
@Composable
private fun RadarFilterBar(
    typeFilter: String,
    onTypeFilter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 等宽分段控件:四个片均分宽度,选中段类别色高亮 + ◆ 指示 —— 不再与搜索框挤一行
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        // BT(经典发现)与 BLE(广播扫描)分开筛 —— 两条独立观测路径,共用「bt」类型但来源不同
        listOf(
            "all" to stringResource(R.string.det_filter_all),
            "wifi" to "WIFI",
            "ble" to "BLE",
            "bt" to "BT",
        ).forEach { (key, label) ->
            val selected = typeFilter == key
            val selColor = when (key) {
                "wifi" -> WIFI_BLIP
                "ble" -> BLE_BLIP
                "bt" -> BT_BLIP
                else -> Red
            }
            Surface(
                modifier = Modifier.weight(1f).clickable { onTypeFilter(key) },
                shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                color = if (selected) selColor.copy(alpha = 0.16f) else SurfaceBg,
                border = BorderStroke(1.dp, if (selected) selColor.copy(alpha = 0.8f) else BorderDim),
            ) {
                Row(
                    Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) {
                        Star(selColor, 5.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        label,
                        fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (selected) selColor else Gray,
                    )
                }
            }
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
 *
 * ## 自动量程
 * 量程按可见目标分布自动缩放(10/25/50/100m 挡位,迟滞防抖,1s 平滑动画):
 * 外圈没有设备就放大盘面,把目标展开到真实相对位置上 —— 无需手动 +/- 缩放。
 * 距离环刻度随量程同步重标注。
 *
 * ## 点触交互
 * 点击 blip(半径 20dp 命中区)聚焦目标并弹出技术详情卡(贴 blip 右侧,
 * 放不下自动翻到左侧);点击空白处关闭弹窗并取消聚焦。
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
    lanHosts: List<ArpEntry>,
    towers: List<TowerCell>,
    onScanPorts: (String) -> Unit,
    onSsh: (String) -> Unit,
    onKick: (String) -> Unit,
    onTapDot: (String) -> Unit,
    onTapEmpty: () -> Unit,
    onOpenDossier: (String) -> Unit,
    onDeauth: (RadarTarget) -> Unit,
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
    val ctx = LocalContext.current

    // ── 自动量程:按可见目标分布换挡,迟滞防抖,1s 平滑动画 ──
    val rangeM = remember { Animatable(100f) }
    LaunchedEffect(typeFilter, query) {
        while (true) {
            delay(1200)
            val vis = targetsState.value.filter {
                matchesTypeFilter(it, typeFilter) && (query.isBlank() || it.name.contains(query, ignoreCase = true))
            }
            val dmax = vis.maxOfOrNull { it.distanceM } ?: 0f
            val desired = when {
                vis.isEmpty() -> 25f
                dmax <= 8f -> 10f
                dmax <= 20f -> 25f
                dmax <= 45f -> 50f
                else -> 100f
            }
            val cur = rangeM.targetValue
            // 迟滞:目标分布显著超出/缩进当前量程才换挡,RSSI 抖动不会来回缩放
            if (desired < cur * 0.45f || desired > cur * 0.85f) {
                rangeM.animateTo(desired, tween(1000, easing = FastOutSlowInEasing))
            }
        }
    }

    // ── 点触弹窗状态:(目标 id, 盘面本地锚点) —— 锚点随目标移动在 draw 阶段微调 ──
    var popupAnchor by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    // 同网主机弹窗:(ArpEntry, 本地锚点) —— 与目标弹窗互斥
    var popupLan by remember { mutableStateOf<Pair<ArpEntry, Offset>?>(null) }
    // 基站弹窗:(TowerCell, 本地锚点)
    var popupCell by remember { mutableStateOf<Pair<TowerCell, Offset>?>(null) }
    // 弹窗在窗口坐标系定位:记录本 Box 的窗口偏移与自身尺寸
    var boxPosInWindow by remember { mutableStateOf(IntOffset.Zero) }
    var scopeSize by remember { mutableStateOf(IntSize.Zero) }
    // 聚焦切换(列表/点触)后弹窗跟随:聚焦目标不再是弹窗目标时关闭弹窗
    LaunchedEffect(focusedId) {
        if (popupAnchor != null && popupAnchor!!.first != focusedId) popupAnchor = null
    }
    // 同网主机下线(下一轮扫描不再出现)则关闭其弹窗
    LaunchedEffect(lanHosts) {
        if (popupLan != null && lanHosts.none { it.ip == popupLan!!.first.ip }) popupLan = null
    }
    // 基站消失则关闭其弹窗
    LaunchedEffect(towers) {
        if (popupCell != null && towers.none { it.cid == popupCell!!.first.cid }) popupCell = null
    }
    val friendlyTagText = stringResource(R.string.det_popup_friendly)
    val cardinalLabels = listOf(
        stringResource(R.string.det_cardinal_n) to 0f,
        stringResource(R.string.det_cardinal_e) to 90f,
        stringResource(R.string.det_cardinal_s) to 180f,
        stringResource(R.string.det_cardinal_w) to 270f,
    )
    val scopeModeScan = stringResource(R.string.det_scope_mode_scan)
    val scopeModeIdle = stringResource(R.string.det_scope_mode_idle)
    val scopeSweepFmt = ctx.getString(R.string.det_fmt_scope_sweep)
    val scopeHeadingFmt = ctx.getString(R.string.det_fmt_scope_heading)
    val scopeStatus2Fmt = ctx.getString(R.string.det_fmt_scope_status2)
    val meshLegendText = stringResource(R.string.det_legend_mesh_nodes)
    val lanLegendFmt = ctx.getString(R.string.det_lan_legend)
    val cellLegendFmt = ctx.getString(R.string.det_tower_legend)
    val satelliteTagFmt = ctx.getString(R.string.det_fmt_satellites)
    val bearingCollectingFmt = ctx.getString(R.string.det_fmt_bearing_collecting)

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
    // 记录时就用「当前生效位置」:有方位推演结果用估计方位,否则哈希占位方位。
    // 距离按米记录 —— 自动量程缩放时轨迹随盘面一起缩放,不留错位余晖
    val trails = remember { mutableStateMapOf<String, List<TrailPoint>>() }
    LaunchedEffect(Unit) {
        snapshotFlow { vm.targets.value }.collect { targets ->
            if (targets.isEmpty()) return@collect
            val locNow = vm.localizations.value
            val current = targets.associate { t ->
                val e = locNow[t.id]
                val locked = e != null && e.state == LocState.ESTIMATED && e.confidence >= 0.45f && !e.bearingDeg.isNaN()
                val angle = if (locked) e.bearingDeg else t.angleDeg
                val dist = if (locked) e.distanceM else t.distanceM
                t.id to TrailPoint(angle, dist)
            }
            // 弹窗目标消失(过期清理)则关闭弹窗
            if (popupAnchor != null && current[popupAnchor!!.first] == null) popupAnchor = null
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
            .border(1.dp, BorderDim, CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp))
            .onGloballyPositioned { boxPosInWindow = it.positionInWindow().round() }
            .onSizeChanged { scopeSize = it },
    ) {
        // ═══ 静态盘面层:背景辉光 / 距离环 / 网格 / 距离标签 ═══
        // drawWithCache 缓存几何与 Path —— 扫描帧循环不再重绘这些元素
        Canvas(
            Modifier.fillMaxSize().drawWithCache {
                val g = TacGreen
                // 状态带高度用占位文本测量(行高与内容无关),盘面几何与动态层一致
                val statusStyle = TextStyle(fontFamily = Mono, fontSize = 8.5.sp, color = g.copy(alpha = 0.86f))
                val maxW = (size.width - 12.dp.toPx()).toInt().coerceAtLeast(1)
                val bandH = textMeasurer.measure(
                    String.format(scopeStatus2Fmt, "", 0, 0, 0, 0),
                    statusStyle,
                    constraints = Constraints(maxWidth = maxW),
                ).size.height * 2 + 3.dp.toPx() * 2
                val fieldH = (size.height - bandH).coerceAtLeast(1f)
                val cx = size.width / 2f
                val cy = fieldH / 2f
                val r = minOf(cx, cy) * 0.90f

                // 磷光管面:中心微亮、向边缘渐暗的径向辉光
                val glowBrush = Brush.radialGradient(
                    listOf(g.copy(alpha = 0.060f), g.copy(alpha = 0.018f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r * 1.4f,
                )
                // 网格 Path 一次构建,重绘只走 drawPath
                val gridPath = Path()
                val gridStep = r / 4f
                var gx = cx
                while (gx <= cx + r) {
                    gridPath.moveTo(gx, cy - r); gridPath.lineTo(gx, cy + r)
                    gridPath.moveTo(cx - (gx - cx), cy - r); gridPath.lineTo(cx - (gx - cx), cy + r)
                    gx += gridStep
                }
                var gy = cy
                while (gy <= cy + r) {
                    gridPath.moveTo(cx - r, gy); gridPath.lineTo(cx + r, gy)
                    gridPath.moveTo(cx - r, cy - (gy - cy)); gridPath.lineTo(cx + r, cy - (gy - cy))
                    gy += gridStep
                }
                val clipOval = Path().apply { addOval(Rect(Offset(cx - r, cy - r), Size(r * 2f, r * 2f))) }

                onDrawBehind {
                    drawRect(glowBrush)
                    // 距离环:外环最亮,层次向外递增(刻度文字由动态层按当前量程绘制)
                    listOf(0.25f, 0.5f, 0.75f, 1f).forEachIndexed { i, s ->
                        drawCircle(g.copy(alpha = 0.15f + i * 0.06f), r * s, Offset(cx, cy), style = Stroke(if (i == 3) 1.8f else 0.7f))
                    }
                    // 十字基线(极淡,不抢光束)
                    drawLine(g.copy(alpha = 0.11f), Offset(cx - r, cy), Offset(cx + r, cy), 0.7f)
                    drawLine(g.copy(alpha = 0.11f), Offset(cx, cy - r), Offset(cx, cy + r), 0.7f)
                    clipPath(clipOval) {
                        drawPath(gridPath, g.copy(alpha = 0.10f), style = Stroke(0.6f))
                    }
                }
            },
        ) {}

        // ═══ 动态帧层:扫描光束 / 目标 / 罗盘 / 状态带 —— 每帧重绘 ═══
        Canvas(
            Modifier.fillMaxSize().pointerInput(typeFilter, query, focusedId, running) {
                detectTapGestures { tap ->
                    val targetsNow = targetsState.value
                    val vis = radarVisibleTargets(targetsNow, typeFilter, query, focusedId, 40)
                    // 与绘制阶段同一套几何:复算状态带高 → 盘面中心/半径 → 命中检测
                    val locNow = locState.value
                    val statusStyleTap = TextStyle(fontFamily = Mono, fontSize = 8.5.sp, color = TacGreen.copy(alpha = 0.86f))
                    val maxWTap = (size.width - 12.dp.toPx()).toInt().coerceAtLeast(1)
                    val l1Tap = textMeasurer.measure(
                        (if (running) scopeModeScan else scopeModeIdle) + "   " + String.format(scopeSweepFmt, sweepDeg.toInt() % 360) + "   " + String.format(scopeHeadingFmt, headingState.value.toInt() % 360),
                        statusStyleTap,
                        constraints = Constraints(maxWidth = maxWTap),
                    )
                    val l2Tap = textMeasurer.measure(
                        String.format(scopeStatus2Fmt, "", 0, 0, 0, 0),
                        statusStyleTap,
                        constraints = Constraints(maxWidth = maxWTap),
                    )
                    val bandHTap = l1Tap.size.height + l2Tap.size.height + 3.dp.toPx() * 2
                    val fieldHTap = (size.height - bandHTap).coerceAtLeast(1f)
                    val cxTap = size.width / 2f
                    val cyTap = fieldHTap / 2f
                    val rTap = minOf(cxTap, cyTap) * 0.90f
                    val rngTap = rangeM.value
                    var hit: RadarTarget? = null
                    var hitX = 0f
                    var hitY = 0f
                    var best = Float.MAX_VALUE
                    for (t in vis) {
                        val e = locNow[t.id]
                        val locked = e != null && e.state == LocState.ESTIMATED && e.confidence >= 0.45f && !e.bearingDeg.isNaN()
                        val effAngle = if (locked) e.bearingDeg else t.angleDeg
                        val dist = ((if (locked) e.distanceM else t.distanceM) / rngTap).coerceIn(0.06f, 0.94f)
                        val ta = Math.toRadians((effAngle - headingState.value - 90.0).toDouble()).toFloat()
                        val x = cxTap + dist * rTap * cos(ta)
                        val y = cyTap + dist * rTap * sin(ta)
                        val d = (tap - Offset(x, y)).getDistance()
                        if (d <= 20.dp.toPx() && d < best) { best = d; hit = t; hitX = x; hitY = y }
                    }
                    if (hit != null) {
                        popupAnchor = hit!!.id to Offset(hitX, hitY)
                        popupLan = null
                        onTapDot(hit!!.id)
                    } else {
                        // 同网主机命中:琥珀方框标记在外环(距离未知,按 IP 哈希散开)
                        var lanHit: ArpEntry? = null
                        var lanX = 0f
                        var lanY = 0f
                        var lanBest = Float.MAX_VALUE
                        val lanR = rTap * 0.82f
                        for (h in lanHosts) {
                            val pa = (h.ip.hashCode() and 0x7FFFFFFF) % 360f
                            val paRad = Math.toRadians((pa - headingState.value - 90.0).toDouble()).toFloat()
                            val hx = cxTap + lanR * cos(paRad)
                            val hy = cyTap + lanR * sin(paRad)
                            val d = (tap - Offset(hx, hy)).getDistance()
                            if (d <= 16.dp.toPx() && d < lanBest) { lanBest = d; lanHit = h; lanX = hx; lanY = hy }
                        }
                        var cellHit: TowerCell? = null
                        var cellX = 0f
                        var cellY = 0f
                        var cellBest = Float.MAX_VALUE
                        for (tw in towers) {
                            val pa = (tw.cid.hashCode() and 0x7FFFFFFF) % 360f
                            val dist01 = ((tw.distanceEstimateM ?: 900f) / rngTap).coerceIn(0.10f, 0.94f)
                            val paRad = Math.toRadians((pa - headingState.value - 90.0).toDouble()).toFloat()
                            val hx = cxTap + dist01 * rTap * cos(paRad)
                            val hy = cyTap + dist01 * rTap * sin(paRad)
                            val d = (tap - Offset(hx, hy)).getDistance()
                            if (d <= 15.dp.toPx() && d < cellBest) { cellBest = d; cellHit = tw; cellX = hx; cellY = hy }
                        }
                        if (lanHit != null) {
                            popupAnchor = null
                            popupLan = lanHit!! to Offset(lanX, lanY)
                            popupCell = null
                        } else if (cellHit != null) {
                            popupAnchor = null
                            popupLan = null
                            popupCell = cellHit!! to Offset(cellX, cellY)
                        } else {
                            popupAnchor = null
                            popupLan = null
                            popupCell = null
                            onTapEmpty()
                        }
                    }
                }
            },
        ) {
            val g = TacGreen
            // draw 阶段读取:任何高频更新只触发重绘,绝不重组组合树
            val targets = targetsState.value
            val heading = headingState.value
            val peers = peersState.value
            val satellites = satState?.value?.satellites.orEmpty()
            // 当前量程 —— 自动缩放动画逐帧推进,绘制直接用当前值
            val rng = rangeM.value
            // ── 过滤后的可见目标：聚焦目标永远显示，其余按类型/关键词过滤;绘制上限 40 防卡顿 ──
            val visibleTargets = radarVisibleTargets(targets, typeFilter, query, focusedId, 40)
            // ── 底部状态带:先测量两行文字,再反推盘面几何 —— 文字绝不裁切 ──
            val loc = locState.value
            val statusStyle = TextStyle(fontFamily = Mono, fontSize = 8.5.sp, color = g.copy(alpha = 0.86f))
            val maxW = (size.width - 12.dp.toPx()).toInt().coerceAtLeast(1)
            val l1 = textMeasurer.measure(
                (if (running) scopeModeScan else scopeModeIdle) + "   " + String.format(scopeSweepFmt, sweepDeg.toInt() % 360) + "   " + String.format(scopeHeadingFmt, heading.toInt() % 360),
                statusStyle,
                constraints = Constraints(maxWidth = maxW),
            )
            val estCount = loc.values.count { it.state == LocState.ESTIMATED }
            val l2 = textMeasurer.measure(
                String.format(scopeStatus2Fmt, "%.0fm".format(rng), visibleTargets.size, targets.size, estCount, peers.size),
                statusStyle,
                constraints = Constraints(maxWidth = maxW),
            )
            val bandPad = 3.dp.toPx()
            val bandH = l1.size.height + l2.size.height + bandPad * 2
            val fieldH = (size.height - bandH).coerceAtLeast(1f)
            val cx = size.width / 2f
            val cy = fieldH / 2f
            val r = minOf(cx, cy) * 0.90f

            // ── 噪点闪烁(更细更淡:底盘噪感,不抢目标) ──
            noise.forEachIndexed { i, (nx, ny) ->
                val a = ((sin(i * 1.7f + sweepDeg * 0.35f) + 1f) / 2f) * 0.045f
                drawCircle(g.copy(alpha = a), 0.7f, Offset(nx * size.width, ny * size.height))
            }

            // ── 方位刻度：随真实罗盘航向旋转，刻度永远指向真实方位 ──
            // 屏幕角 = 世界角 - 航向 - 90°：世界 0°(北) 在机头(屏顶)方向时为 -90°(屏顶)
            for (deg in 0 until 360 step 10) {
                val a = Math.toRadians((deg - heading - 90.0).toDouble()).toFloat()
                val major = deg % 30 == 0
                val inner = r * (if (major) 0.93f else 0.965f)
                drawLine(
                    g.copy(alpha = if (major) 0.55f else 0.22f),
                    Offset(cx + inner * cos(a), cy + inner * sin(a)),
                    Offset(cx + r * cos(a), cy + r * sin(a)),
                    if (major) 1.4f else 0.5f,
                )
            }
            // 罗盘方位字:北东南西固定在真实方位,随陀螺仪反向旋转(带辉光描边,更醒目)
            cardinalLabels.forEach { (ch, deg) ->
                val a = Math.toRadians((deg - heading - 90.0).toDouble()).toFloat()
                val lr = r * 0.83f
                val m = cachedMeasure("cardinal", ch, TextStyle(fontFamily = Sans, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = g.copy(alpha = 0.92f)))
                val mg = cachedMeasure("cardinal_glow", ch, TextStyle(fontFamily = Sans, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = g.copy(alpha = 0.22f)))
                val base = Offset(cx + lr * cos(a) - m.size.width / 2f, cy + lr * sin(a) - m.size.height / 2f)
                drawText(mg, topLeft = base + Offset(-1.dp.toPx(), 0f))
                drawText(mg, topLeft = base + Offset(1.dp.toPx(), 0f))
                drawText(mg, topLeft = base + Offset(0f, -1.dp.toPx()))
                drawText(mg, topLeft = base + Offset(0f, 1.dp.toPx()))
                drawText(m, topLeft = base)
            }

            // ── 距离环刻度:按当前量程实时重标注(自动缩放时随盘面一起变) ──
            listOf(0.25f, 0.5f, 0.75f).forEach { s ->
                val v = rng * s
                val lab = if (v >= 10f) "%.0f".format(v) else "%.1f".format(v)
                val tag = cachedMeasure("grid", lab, TextStyle(fontFamily = Mono, fontSize = 7.5.sp, color = g.copy(alpha = 0.5f)))
                drawText(tag, topLeft = Offset(cx + r * s - tag.size.width / 2f, cy + 2.dp.toPx()))
            }

            // ── 机头航向指针：固定在屏幕顶部，指向设备当前朝向 ──
            val bow = Path().apply {
                moveTo(cx, cy - r * 0.99f)
                lineTo(cx - 7.dp.toPx(), cy - r * 0.80f)
                lineTo(cx + 7.dp.toPx(), cy - r * 0.80f)
                close()
            }
            drawPath(bow, Gold.copy(alpha = 0.95f))
            drawPath(bow, White.copy(alpha = 0.65f), style = Stroke(1f))

            // 磁北参考虚线：从中心指向当前航向的反向（即北向方位）
            val northRad = Math.toRadians((-heading - 90.0).toDouble()).toFloat()
            drawLine(
                g.copy(alpha = 0.22f),
                Offset(cx, cy),
                Offset(cx + r * 0.35f * cos(northRad), cy + r * 0.35f * sin(northRad)),
                1f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
            )

            val sweepRad = Math.toRadians(sweepDeg.toDouble()).toFloat()

            // ── 磷光尾迹:14 层窄扇形(4° 间隔),余晖清晰可见 ──
            for (i in 0 until 14) {
                val startDeg = sweepDeg - (i + 1) * 4f
                drawArc(
                    g.copy(alpha = (0.16f - i * 0.011f).coerceAtLeast(0.02f)),
                    startAngle = startDeg,
                    sweepAngle = 3.2f,
                    useCenter = true,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2f, r * 2f),
                )
            }

            // ── 主光束:多层窄扇形逼近指数衰减 —— 前缘锐利亮核,后缘快速衰减 ──
            listOf(
                Triple(2.0f, 0.38f, 0f),
                Triple(5.0f, 0.20f, 2f),
                Triple(9.0f, 0.10f, 7f),
                Triple(16f, 0.04f, 16f),
            ).forEach { (width, alpha, back) ->
                drawArc(
                    g.copy(alpha = alpha),
                    startAngle = sweepDeg - back - width,
                    sweepAngle = width,
                    useCenter = true,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2f, r * 2f),
                )
            }

            // ── 目标-中心连线（数据链）：随罗盘航向同步旋转,颜色跟随类别色 ──
            if (running || targets.isNotEmpty()) {
                visibleTargets.forEach { t ->
                    val e = loc[t.id]
                    val locked = e != null && e.state == LocState.ESTIMATED && e.confidence >= 0.45f && !e.bearingDeg.isNaN()
                    val effAngle = if (locked) e.bearingDeg else t.angleDeg
                    val dist = ((if (locked) e.distanceM else t.distanceM) / rng).coerceIn(0.06f, 0.94f)
                    val tr = dist * r
                    val ta = Math.toRadians((effAngle - heading - 90.0).toDouble()).toFloat()
                    drawLine(radarBlipColor(t).copy(alpha = 0.12f), Offset(cx, cy), Offset(cx + tr * cos(ta), cy + tr * sin(ta)), 0.8f)
                }
            }

            // ── 扫描针:8 层拖影 + 细亮针 + 针尖辉光 ──
            val tipX = cx + r * cos(sweepRad)
            val tipY = cy + r * sin(sweepRad)
            for (i in 1..8) {
                val backRad = sweepRad - i * 0.028f
                drawLine(
                    g.copy(alpha = (0.55f - i * 0.062f).coerceAtLeast(0.05f)),
                    Offset(cx, cy),
                    Offset(cx + r * cos(backRad), cy + r * sin(backRad)),
                    (3.4f - i * 0.35f).coerceAtLeast(0.5f),
                )
            }
            drawLine(g.copy(alpha = 0.30f), Offset(cx, cy), Offset(tipX, tipY), 4.5f)
            drawLine(g.copy(alpha = 0.95f), Offset(cx, cy), Offset(tipX, tipY), 1.5f)
            drawCircle(g.copy(alpha = 0.60f), 5.dp.toPx(), Offset(tipX, tipY))
            drawCircle(Color(0xFFE8F4FF).copy(alpha = 0.95f), 2.dp.toPx(), Offset(tipX, tipY))

            // ── 全局声呐 ping：每 1.8 秒一发扩散环 ──
            val ping = ((sweepDeg / 360f) * 2f) % 1f
            drawCircle(g.copy(alpha = (1f - ping) * 0.30f), r * ping, Offset(cx, cy), style = Stroke(1.2f))

            // ── 开机辉光：启动后扩散 ──
            if (powered) {
                val glow = (((sweepDeg / 360f) * 6f) % 1f)
                drawCircle(g.copy(alpha = (1f - glow) * 0.22f), r * glow, Offset(cx, cy), style = Stroke(4f))
            }

            // ── 目标 blip：磷光轨迹 + 扫过点亮 + 命中白闪 + 锁定框 + 编号 ──
            // 类别固定色(WiFi 红 / BLE 青 / 经典 BT 绿 / 友方蓝),与队伍主题解耦;
            // 友方(已绑定)画静止双环 + 蓝框角,不脉动不震动 —— 一眼识别非敌对目标
            if (running || targets.isNotEmpty()) {
                visibleTargets.forEachIndexed { i, t ->
                    // 方位推演置信达标时用估计方位/距离替换哈希占位(位置仍为估计值)
                    val e = loc[t.id]
                    val locked = e != null && e.state == LocState.ESTIMATED && e.confidence >= 0.45f && !e.bearingDeg.isNaN()
                    val effAngle = if (locked) e.bearingDeg else t.angleDeg
                    val dist = ((if (locked) e.distanceM else t.distanceM) / rng).coerceIn(0.06f, 0.94f)
                    val tr = dist * r
                    // 目标方位为世界坐标，绘制时减去航向与 90° 屏幕偏置 —— 转动机身，盘面目标随之旋转
                    val ta = Math.toRadians((effAngle - heading - 90.0).toDouble()).toFloat()
                    val x = cx + tr * cos(ta)
                    val y = cy + tr * sin(ta)

                    // 光束扫过窗口 ±50° 内最亮,扫过后磷光余晖 —— 用目标屏幕角比较,航向旋转时点亮时机仍精确
                    var d = sweepDeg - (t.angleDeg - heading - 90f)
                    while (d > 180f) d -= 360f
                    while (d < -180f) d += 360f
                    val isFocused = t.id == focusedId
                    // 聚焦模式下非聚焦目标大幅降噪淡出，聚焦目标恒定高亮
                    val bright = ((1f - abs(d) / 50f).coerceIn(0.10f, 1f)) * (if (focusedId == null || isFocused) 1f else 0.16f)
                    val blip = radarBlipColor(t)
                    val friendly = t.bonded

                    // 磷光轨迹：历史位置渐隐余晖（同样跟随航向旋转;距离按米记录,随量程缩放）
                    trails[t.id]?.dropLast(1)?.forEachIndexed { k, pt ->
                        val pta = Math.toRadians((pt.angleDeg - heading - 90.0).toDouble()).toFloat()
                        val pr = (pt.distM / rng).coerceIn(0f, 1f)
                        val pxa = cx + pr * r * cos(pta)
                        val pya = cy + pr * r * sin(pta)
                        drawCircle(blip.copy(alpha = (0.05f + k * 0.045f) * (if (isFocused || focusedId == null) 1f else 0.16f)), 1.6.dp.toPx(), Offset(pxa, pya))
                    }

                    // 醒目的 blip:紧凑光晕 + 亮核(光晕收小,目标更锐)
                    drawCircle(blip.copy(alpha = bright * 0.26f), 10.dp.toPx(), Offset(x, y))
                    drawCircle(blip.copy(alpha = bright * 0.55f), 5.5.dp.toPx(), Offset(x, y))

                    if (friendly) {
                        // 友方标记:静止双环(绝不脉动) + 蓝框角 + FRIENDLY 标签 —— 非敌对目标持续高亮
                        drawCircle(blip.copy(alpha = bright * 0.45f), 9.dp.toPx(), Offset(x, y), style = Stroke(1.4f))
                        drawCircle(blip.copy(alpha = bright * 0.22f), 14.dp.toPx(), Offset(x, y), style = Stroke(1f))
                        val fs = 30.dp.toPx()
                        withTransform({ translate(x - fs / 2f, y - fs / 2f) }) {
                            drawTacticalCorners(
                                blip.copy(alpha = bright * 0.85f),
                                Size(fs, fs),
                                len = 7.dp.toPx(),
                                stroke = 1.3.dp.toPx(),
                            )
                        }
                        val fTag = cachedMeasure("friendly", friendlyTagText, TextStyle(fontFamily = Mono, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = blip.copy(alpha = bright)))
                        drawText(fTag, topLeft = Offset(x - fTag.size.width / 2f, y - 24.dp.toPx()))
                    } else {
                        // 未知目标双脉动扩散环
                        val rippleBase = ((sweepDeg / 360f) * 2f + i * 0.37f) % 1f
                        listOf(0f, 0.5f).forEach { off ->
                            val ph = (rippleBase + off) % 1f
                            val rr = 3.dp.toPx() + 30.dp.toPx() * ph
                            drawCircle(blip.copy(alpha = (1f - ph) * 0.26f * bright), rr, Offset(x, y), style = Stroke(4f))
                            drawCircle(blip.copy(alpha = (1f - ph) * 0.95f * bright), rr, Offset(x, y), style = Stroke(1.4f))
                        }
                    }

                    // 核心点 + 白芯 + 十字准星
                    drawCircle(blip.copy(alpha = bright), 3.2.dp.toPx(), Offset(x, y))
                    drawCircle(White.copy(alpha = bright * 0.9f), 1.4.dp.toPx(), Offset(x, y))
                    val cross = 8.dp.toPx()
                    drawLine(blip.copy(alpha = bright * 0.85f), Offset(x - cross, y), Offset(x + cross, y), 1.2f)
                    drawLine(blip.copy(alpha = bright * 0.85f), Offset(x, y - cross), Offset(x, y + cross), 1.2f)

                    // ── 高危接近警示:红色双环 + 「!」标签(与档案页同一套威胁分级;友方不在此列) ──
                    val tier = radarThreatTier(t)
                    if (tier.key == "high") {
                        val warnR = 22.dp.toPx() + 3.dp.toPx() * sin(sweepDeg * 0.07f + i)
                        drawCircle(TIER_HIGH.copy(alpha = bright * 0.85f), warnR, Offset(x, y), style = Stroke(2.2f))
                        drawCircle(TIER_HIGH.copy(alpha = bright * 0.5f), warnR + 4.dp.toPx(), Offset(x, y), style = Stroke(1.2f))
                        val bang = cachedMeasure("bang", "!", TextStyle(fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TIER_HIGH))
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

                    // 点触弹窗锚点跟随:目标移动超过 3px 时同步弹窗位置(只在变化时写状态,不产生每帧重组)
                    val anchor = popupAnchor
                    if (anchor != null && anchor.first == t.id) {
                        val dx = x - anchor.second.x
                        val dy = y - anchor.second.y
                        if (dx * dx + dy * dy > 9f) popupAnchor = t.id to Offset(x, y)
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
                        mk = Offset(cx + (worldDist / rng * r) * cos(ma), cy + (worldDist / rng * r) * sin(ma))
                    } else {
                        mk = Offset(cx + r * cos(bRad), cy + r * sin(bRad))
                    }
                    val mkColor = color
                    drawLine(mkColor.copy(alpha = 0.9f), Offset(mk.x - 6.dp.toPx(), mk.y), Offset(mk.x + 6.dp.toPx(), mk.y), 1.6f)
                    drawLine(mkColor.copy(alpha = 0.9f), Offset(mk.x, mk.y - 6.dp.toPx()), Offset(mk.x, mk.y + 6.dp.toPx()), 1.6f)
                    drawCircle(mkColor.copy(alpha = 0.6f), 3.5.dp.toPx(), mk)
                    if (hasFix && foc.sweepCount > 0) {
                        // 置信椭圆(90%,旋转至椭圆主轴;世界角 → 屏幕角同一映射)
                        val aSemi = (foc.ellipseSemiMajorM / rng * r).coerceAtLeast(3f)
                        val bSemi = (foc.ellipseSemiMinorM / rng * r).coerceAtLeast(3f)
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
                        String.format(bearingCollectingFmt, (foc.coverage * 100).toInt()),
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
                    // 节点名标签(仅说话中常显,其余扫过时显示)—— 屏幕角比较,航向旋转时不偏移
                    val paScreen = ((pa - heading - 90f) % 360f + 360f) % 360f
                    val showTag = p.isTalking || abs(((sweepDeg - paScreen + 540f) % 360f) - 180f) < 40f
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
                    meshLegendText,
                    TextStyle(fontFamily = Mono, fontSize = 7.5.sp, color = Gold.copy(alpha = 0.8f)),
                )
                drawText(legend, topLeft = Offset(8.dp.toPx(), 8.dp.toPx()))
            }

            // ── 同网主机层:ARP Ping Sweep 发现的本网段在线主机 ──
            // 距离/方位未知(无 RSSI),按 IP 哈希散在固定外环上,琥珀方框与圆形 blip 区分;
            // 点触方框弹出 IP/主机名/MAC/时延详情
            if (lanHosts.isNotEmpty()) {
                val lanR = r * 0.82f
                drawCircle(
                    Warn.copy(alpha = 0.30f),
                    lanR, Offset(cx, cy),
                    style = Stroke(0.8f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 7f))),
                )
                lanHosts.sortedBy { it.ip }.forEachIndexed { i, h ->
                    val pa = (h.ip.hashCode() and 0x7FFFFFFF) % 360f
                    val paRad = Math.toRadians((pa - heading - 90.0).toDouble()).toFloat()
                    val x = cx + lanR * cos(paRad)
                    val y = cy + lanR * sin(paRad)
                    // 方框标记:双相扩散细方框 + 实心小方
                    val ph = ((sweepDeg / 360f) * 2f + i * 0.29f) % 1f
                    val half = 3.dp.toPx() + 9.dp.toPx() * ph
                    drawRect(
                        Warn.copy(alpha = (1f - ph) * 0.5f),
                        topLeft = Offset(x - half, y - half),
                        size = Size(half * 2f, half * 2f),
                        style = Stroke(1.2f),
                    )
                    val s = 3.4.dp.toPx()
                    drawRect(
                        Warn.copy(alpha = 0.95f),
                        topLeft = Offset(x - s, y - s),
                        size = Size(s * 2f, s * 2f),
                        style = Stroke(1.5f),
                    )
                    // IP 标签:少量主机常显,多主机光束扫过时显示
                    val paScreen = ((pa - heading - 90f) % 360f + 360f) % 360f
                    val showTag = lanHosts.size <= 8 || abs(((sweepDeg - paScreen + 540f) % 360f) - 180f) < 40f
                    if (showTag) {
                        val tag = cachedMeasure("lan", h.ip, TextStyle(fontFamily = Mono, fontSize = 7.5.sp, color = Warn.copy(alpha = 0.95f)))
                        drawText(tag, topLeft = Offset(x - tag.size.width / 2f, y + 7.dp.toPx()))
                    }
                    // 弹窗锚点跟随
                    val anchor = popupLan
                    if (anchor != null && anchor.first.ip == h.ip) {
                        val dx = x - anchor.second.x
                        val dy = y - anchor.second.y
                        if (dx * dx + dy * dy > 9f) popupLan = h to Offset(x, y)
                    }
                }
                val lanLegend = cachedMeasure(
                    "lanlegend",
                    String.format(lanLegendFmt, lanHosts.size),
                    TextStyle(fontFamily = Mono, fontSize = 7.5.sp, color = Warn.copy(alpha = 0.85f)),
                )
                drawText(lanLegend, topLeft = Offset(8.dp.toPx(), 28.dp.toPx()))
            }

            // ── 基站层:注册小区 + 邻区(紫三角) ──
            // 距离 = RSRP 路径损耗粗估;方位无硬件可测,按 CID 哈希散开(占位)并如实标注
            if (towers.isNotEmpty()) {
                towers.sortedByDescending { it.isRegistered }.forEach { tw ->
                    val pa = (tw.cid.hashCode() and 0x7FFFFFFF) % 360f
                    val dist01 = ((tw.distanceEstimateM ?: 900f) / rng).coerceIn(0.10f, 0.94f)
                    val tr = dist01 * r
                    val paRad = Math.toRadians((pa - heading - 90.0).toDouble()).toFloat()
                    val x = cx + tr * cos(paRad)
                    val y = cy + tr * sin(paRad)
                    val s = 5.5.dp.toPx()
                    val tri = Path().apply {
                        moveTo(x, y - s)
                        lineTo(x - s * 0.9f, y + s * 0.75f)
                        lineTo(x + s * 0.9f, y + s * 0.75f)
                        close()
                    }
                    if (tw.isRegistered) {
                        drawPath(tri, CELL_TOWER_COLOR.copy(alpha = 0.9f))
                        drawPath(tri, White.copy(alpha = 0.55f), style = Stroke(1f))
                        // 注册小区常显标签:类型 + 信号
                        val sig = tw.rsrpDbm ?: tw.rssiDbm
                        val tag = cachedMeasure(
                            "cell",
                            "${tw.type}${sig?.let { " $it" } ?: ""}",
                            TextStyle(fontFamily = Mono, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = CELL_TOWER_COLOR),
                        )
                        drawText(tag, topLeft = Offset(x - tag.size.width / 2f, y + 8.dp.toPx()))
                    } else {
                        drawPath(tri, CELL_TOWER_COLOR.copy(alpha = 0.45f), style = Stroke(1.3f))
                    }
                    // 弹窗锚点跟随
                    val anchor = popupCell
                    if (anchor != null && anchor.first.cid == tw.cid) {
                        val dx = x - anchor.second.x
                        val dy = y - anchor.second.y
                        if (dx * dx + dy * dy > 9f) popupCell = tw to Offset(x, y)
                    }
                }
                val cellLegend = cachedMeasure(
                    "celllegend",
                    String.format(cellLegendFmt, towers.size),
                    TextStyle(fontFamily = Mono, fontSize = 7.5.sp, color = CELL_TOWER_COLOR.copy(alpha = 0.85f)),
                )
                drawText(cellLegend, topLeft = Offset(8.dp.toPx(), 44.dp.toPx()))
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
                    // 星座固定色:与 blip 类别色(红/青/绿/蓝)错开,外圈小点不与目标混淆
                    val color = when (sat.constellationName) {
                        "GPS" -> White
                        "北斗" -> Color(0xFFFF9100)
                        "伽利略" -> Color(0xFFB388FF)
                        "格洛纳斯" -> Color(0xFF9FA8B8)
                        else -> White
                    }
                    drawCircle(color.copy(alpha = 0.35f), 4.dp.toPx(), Offset(x, y))
                    drawCircle(color.copy(alpha = if (sat.usedInFix) 0.95f else 0.55f), 1.6.dp.toPx(), Offset(x, y))
                }
                val satTag = cachedMeasure(
                    "sat",
                    String.format(satelliteTagFmt, satellites.size),
                    TextStyle(fontFamily = Mono, fontSize = 7.5.sp, color = Gold.copy(alpha = 0.8f)),
                )
                drawText(satTag, topLeft = Offset(8.dp.toPx(), 20.dp.toPx()))
            }

            // ── 中心原点:微光环 + 呼吸光点 ──
            drawCircle(g.copy(alpha = 0.25f), 5.dp.toPx(), Offset(cx, cy))
            drawCircle(g.copy(alpha = 0.45f + 0.35f * sin(sweepDeg * 0.3f)), 2.dp.toPx(), Offset(cx, cy))

            // ── 底部 CRT 状态带(盘面下方实测预留区,两行,绝不裁切) ──
            drawLine(BorderDim.copy(alpha = 0.9f), Offset(0f, fieldH), Offset(size.width, fieldH), 1f)
            drawText(l1, topLeft = Offset((size.width - l1.size.width) / 2f, fieldH + bandPad))
            drawText(l2, topLeft = Offset((size.width - l2.size.width) / 2f, fieldH + bandPad + l1.size.height))
        }

        // ═══ 点触弹窗:窗口坐标系定位,贴 blip 右侧(放不下翻左侧),盘面外不裁切 ═══
        popupAnchor?.let { (id, anchor) ->
            RadarBlipPopup(
                vm = vm,
                targetId = id,
                anchorLocal = anchor,
                boxPosInWindow = boxPosInWindow,
                scopeSize = scopeSize,
                onOpenDossier = onOpenDossier,
                onDeauth = onDeauth,
            )
        }
        // ═══ 同网主机弹窗:与目标弹窗互斥 ═══
        popupLan?.let { (host, anchor) ->
            RadarLanPopup(
                host = host,
                anchorLocal = anchor,
                boxPosInWindow = boxPosInWindow,
                scopeSize = scopeSize,
                onScanPorts = onScanPorts,
                onSsh = onSsh,
                onKick = onKick,
            )
        }
        // ═══ 基站弹窗 ═══
        popupCell?.let { (cell, anchor) ->
            RadarTowerPopup(
                cell = cell,
                anchorLocal = anchor,
                boxPosInWindow = boxPosInWindow,
                scopeSize = scopeSize,
            )
        }
    }
}

/** 雷达可见目标:聚焦目标恒首位,其余按信号强度取前 limit 个 —— 绘制与点触命中共用 */
private fun radarVisibleTargets(
    targets: List<RadarTarget>,
    typeFilter: String,
    query: String,
    focusedId: String?,
    limit: Int,
): List<RadarTarget> {
    val filteredAll = targets.filter { t ->
        t.id == focusedId ||
            (matchesTypeFilter(t, typeFilter) && (query.isBlank() || t.name.contains(query, ignoreCase = true)))
    }
    return (focusedId?.let { id -> filteredAll.filter { it.id == id } }.orEmpty() +
        filteredAll.filter { it.id != focusedId }.sortedByDescending { it.rssi }.take(limit))
        .distinctBy { it.id }
}

/** 雷达目标磷光轨迹点（角度 + 米制距离,绘制时按当前量程缩放） */
private data class TrailPoint(val angleDeg: Float, val distM: Float)

/**
 * 点触 blip 弹窗 —— 技术详情 + 规格 + 与本机距离,贴在目标旁边实时刷新。
 *
 * 定位:优先贴目标右侧,右侧放不下翻到左侧;纵向上移小半卡高,
 * 再夹到窗口可视区内 —— 页面滚动/盘面位置变化时锚点随 Box 窗口坐标走。
 */
@Composable
private fun RadarBlipPopup(
    vm: RadarViewModel,
    targetId: String,
    anchorLocal: Offset,
    boxPosInWindow: IntOffset,
    scopeSize: IntSize,
    onOpenDossier: (String) -> Unit,
    onDeauth: (RadarTarget) -> Unit,
) {
    val targets by vm.targets.collectAsState()
    val locNow by vm.localizations.collectAsState()
    val t = targets.firstOrNull { it.id == targetId } ?: return
    val blip = radarBlipColor(t)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    val popupW = 236.dp
    val estH = 430.dp
    // 连接动作本地状态:密码 + 结果反馈
    var pass by remember { mutableStateOf("") }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var actionErr by remember { mutableStateOf(false) }
    // 成功文案在组合期取好(点击回调不是组合上下文,不能调 stringResource)
    val connectSentText = stringResource(R.string.det_msg_connect_sent)
    val pairSentText = stringResource(R.string.det_msg_pair_request_sent)
    val side = with(density) { 14.dp.toPx() }
    val cardW = with(density) { popupW.toPx() }
    val cardH = with(density) { estH.toPx() }
    val wPx = scopeSize.width.toFloat()
    // 横向:优先贴右侧;右侧放不下翻左侧;再不够夹进盘面
    var ax = anchorLocal.x + side
    if (ax + cardW > wPx - 8f) ax = anchorLocal.x - side - cardW
    ax = ax.coerceIn(8f, (wPx - cardW - 8f).coerceAtLeast(8f))
    // 纵向:锚点上移小半卡高,夹进窗口
    var ay = anchorLocal.y - cardH / 3f
    val winH = with(density) { (cfg.screenHeightDp.dp).toPx() }
    ay = ay.coerceIn(8f, (winH - cardH - 8f).coerceAtLeast(8f))
    val offset = IntOffset(
        (boxPosInWindow.x + ax).roundToInt(),
        (boxPosInWindow.y + ay).roundToInt(),
    )

    val wifi = t.type == "wifi"
    val tier = radarThreatTier(t)
    val typeLabel = when {
        t.bonded -> stringResource(R.string.det_popup_friendly)
        wifi -> "WIFI"
        t.observedVia == "ble" -> "BLE"
        else -> "BT"
    }

    Popup(alignment = Alignment.TopStart, offset = offset, onDismissRequest = {}) {
        Surface(
            shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
            color = Dark,
            border = BorderStroke(1.dp, blip.copy(alpha = 0.75f)),
            shadowElevation = 14.dp,
        ) {
            Column(Modifier.width(popupW).padding(10.dp)) {
                // 头部:类别徽章 + 名称 + 友方徽章
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                        color = blip.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, blip.copy(alpha = 0.7f)),
                    ) {
                        Text(
                            typeLabel,
                            fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                            color = blip, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(t.name.take(14), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 1, modifier = Modifier.weight(1f))
                    if (t.bonded) {
                        Surface(
                            shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                            color = FRIENDLY_BLIP.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, FRIENDLY_BLIP.copy(alpha = 0.6f)),
                        ) {
                            Text(stringResource(R.string.det_popup_friendly), fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = FRIENDLY_BLIP, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                // 距离突出行:大号距离 + 方位(估计/占位)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.det_info_distance), fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.padding(bottom = 2.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("%.1f m".format(t.distanceM), fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = blip)
                    Spacer(Modifier.width(8.dp))
                    val e = locNow[t.id]
                    val hasLoc = e != null && e.state == LocState.ESTIMATED && !e.bearingDeg.isNaN()
                    val rangeText = if (e != null && hasLoc) stringResource(R.string.det_fmt_range_estimated, e.distanceM, e.bearingDeg.toInt())
                    else stringResource(R.string.det_fmt_range_placeholder, t.distanceM, t.angleDeg.toInt())
                    Text(
                        rangeText,
                        fontFamily = Mono, fontSize = 7.sp, color = if (hasLoc) Gold else Gray,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Spacer(Modifier.height(5.dp))
                InlineInfoRow(stringResource(R.string.det_info_id), if (wifi) t.id else t.address)
                InlineInfoRow(stringResource(R.string.det_info_vendor), "${t.vendor} · OUI ${t.vendorOui}")
                InlineInfoRow(stringResource(R.string.det_info_signal), stringResource(R.string.det_fmt_signal_detail, t.rssi, t.snrDb, t.noiseDbm))
                if (wifi) {
                    InlineInfoRow(stringResource(R.string.det_info_radio), "${t.frequencyMhz}MHz · ${t.channel} · ${t.channelWidth}")
                    InlineInfoRow(stringResource(R.string.det_info_security), t.security.ifEmpty { "--" })
                } else {
                    InlineInfoRow(stringResource(R.string.det_info_class), "${t.deviceClassMajor}${if (t.deviceClassDetail.isNotEmpty()) " · ${t.deviceClassDetail}" else ""}")
                    InlineInfoRow(stringResource(R.string.det_info_link), stringResource(R.string.det_fmt_link_bond, t.linkType, t.bondState))
                    if (t.bleAdv?.hasRecord == true) {
                        InlineInfoRow(
                            stringResource(R.string.det_info_adv),
                            stringResource(
                                R.string.det_fmt_adv_info,
                                if (t.txPowerDbm != null) "${t.txPowerDbm}dBm" else "—",
                                t.bleAdv.serviceUuids.size,
                                if (t.bleAdv.manufacturerId >= 0) "0x%04X".format(t.bleAdv.manufacturerId) else "—",
                            ),
                        )
                    }
                }
                InlineInfoRow(stringResource(R.string.det_info_bearing), locLabel(locNow[t.id]))
                InlineInfoRow(
                    stringResource(R.string.det_info_threat),
                    stringResource(
                        when (tier.key) {
                            "high" -> R.string.det_threat_high
                            "mid" -> R.string.det_threat_mid
                            "friendly" -> R.string.det_threat_friendly
                            else -> R.string.det_threat_far
                        },
                    ),
                )
                Spacer(Modifier.height(6.dp))
                // ── 连接/攻击动作:WiFi 可连接可跳攻击,BT/BLE 走系统配对 ──
                if (wifi) {
                    BasicTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Mono, fontSize = 9.sp, color = White),
                        cursorBrush = SolidColor(Gold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                            .background(SurfaceAlt)
                            .border(1.dp, BorderDim, CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        decorationBox = { inner ->
                            if (pass.isEmpty()) {
                                Text(stringResource(R.string.det_ph_passphrase), fontFamily = Mono, fontSize = 8.sp, color = Gray.copy(alpha = 0.5f))
                            }
                            inner()
                        },
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                haptic.hud()
                                val err = vm.connectWifiTarget(t.name, t.security, pass)
                                actionErr = err != null
                                actionMsg = err ?: connectSentText
                                if (err == null) pass = ""
                            },
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = TacGreen.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.6f)),
                        ) {
                            Text(stringResource(R.string.det_popup_connect), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacGreen, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                        }
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                haptic.hud()
                                onDeauth(t)
                            },
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = Danger.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Danger.copy(alpha = 0.6f)),
                        ) {
                            Text(stringResource(R.string.det_popup_deauth), fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Danger, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            haptic.hud()
                            val err = vm.pairTarget(t.address)
                            actionErr = err != null
                            actionMsg = err ?: pairSentText
                        },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = TacGreen.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.6f)),
                    ) {
                        Text(stringResource(R.string.det_popup_pair), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacGreen, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                    }
                }
                actionMsg?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontFamily = Sans, fontSize = 8.sp, color = if (actionErr) Danger else TacGreen, lineHeight = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptic.hud()
                        onOpenDossier(t.id)
                    },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = Gold.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f)),
                ) {
                    Text(stringResource(R.string.det_popup_open_dossier), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.det_popup_close_hint), fontFamily = Mono, fontSize = 7.sp, color = Gray.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * 同网主机弹窗 —— ARP Ping Sweep 发现的局域网设备详情。
 * 距离与方位未知(无 RSSI),如实呈现;定位逻辑与目标弹窗相同。
 */
@Composable
private fun RadarLanPopup(
    host: ArpEntry,
    anchorLocal: Offset,
    boxPosInWindow: IntOffset,
    scopeSize: IntSize,
    onScanPorts: (String) -> Unit,
    onSsh: (String) -> Unit,
    onKick: (String) -> Unit,
) {
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val popupW = 216.dp
    val estH = 330.dp
    val side = with(density) { 14.dp.toPx() }
    val cardW = with(density) { popupW.toPx() }
    val cardH = with(density) { estH.toPx() }
    val wPx = scopeSize.width.toFloat()
    var ax = anchorLocal.x + side
    if (ax + cardW > wPx - 8f) ax = anchorLocal.x - side - cardW
    ax = ax.coerceIn(8f, (wPx - cardW - 8f).coerceAtLeast(8f))
    var ay = anchorLocal.y - cardH / 3f
    val winH = with(density) { (cfg.screenHeightDp.dp).toPx() }
    ay = ay.coerceIn(8f, (winH - cardH - 8f).coerceAtLeast(8f))
    val offset = IntOffset(
        (boxPosInWindow.x + ax).roundToInt(),
        (boxPosInWindow.y + ay).roundToInt(),
    )

    // IP 定位(免 Key ip-api.com;私有地址如实显示无地理信息)
    val arpManager = remember { ArpScanManager() }
    var geoText by remember { mutableStateOf<String?>(null) }
    var geoLoading by remember { mutableStateOf(false) }
    LaunchedEffect(host.ip) {
        geoLoading = true
        geoText = arpManager.geoLookup(host.ip)
        geoLoading = false
    }

    Popup(alignment = Alignment.TopStart, offset = offset, onDismissRequest = {}) {
        Surface(
            shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
            color = Dark,
            border = BorderStroke(1.dp, Warn.copy(alpha = 0.75f)),
            shadowElevation = 14.dp,
        ) {
            Column(Modifier.width(popupW).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                        color = Warn.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Warn.copy(alpha = 0.7f)),
                    ) {
                        Text(stringResource(R.string.det_lan_popup_title), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Warn, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(host.ip, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                InlineInfoRow(stringResource(R.string.det_lan_hostname), host.hostname.ifBlank { "--" })
                InlineInfoRow(stringResource(R.string.det_lan_mac), host.mac.ifBlank { "--" })
                InlineInfoRow(stringResource(R.string.det_lan_latency), host.latency)
                Spacer(Modifier.height(6.dp))
                // ── 定位结果行 ──
                when {
                    geoLoading -> Text(stringResource(R.string.det_lan_geo_loading), fontFamily = Mono, fontSize = 7.5.sp, color = Gray)
                    geoText != null -> Text("◎ $geoText", fontFamily = Mono, fontSize = 7.5.sp, color = TacGreen, lineHeight = 11.sp)
                    else -> Text(stringResource(R.string.det_lan_geo_private), fontFamily = Mono, fontSize = 7.5.sp, color = Gray, lineHeight = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                // ── 行动:扫端口 / SSH / 踢出网络 ──
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    LanActionButton(stringResource(R.string.det_lan_scan_ports), TacGreen, Modifier.weight(1f)) {
                        haptic.hud()
                        onScanPorts(host.ip)
                    }
                    LanActionButton(stringResource(R.string.det_lan_ssh), Gold, Modifier.weight(1f)) {
                        haptic.hud()
                        onSsh(host.ip)
                    }
                }
                Spacer(Modifier.height(5.dp))
                LanActionButton(stringResource(R.string.det_lan_kick), Danger, Modifier.fillMaxWidth()) {
                    haptic.hud()
                    onKick(host.ip)
                }
                Spacer(Modifier.height(5.dp))
                Text(stringResource(R.string.det_lan_hint), fontFamily = Sans, fontSize = 7.5.sp, color = Gray, lineHeight = 11.sp)
            }
        }
    }
}

/** 同网主机弹窗动作按钮 */
@Composable
private fun LanActionButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
    ) {
        Text(label, fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
    }
}

/**
 * 基站弹窗 —— 注册小区/邻区技术参数。距离为 RSRP 路径损耗粗估,
 * 方位手机无硬件可测(盘面角度是占位),全部如实标注。
 */
@Composable
private fun RadarTowerPopup(
    cell: TowerCell,
    anchorLocal: Offset,
    boxPosInWindow: IntOffset,
    scopeSize: IntSize,
) {
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    val popupW = 216.dp
    val estH = 200.dp
    val side = with(density) { 14.dp.toPx() }
    val cardW = with(density) { popupW.toPx() }
    val cardH = with(density) { estH.toPx() }
    val wPx = scopeSize.width.toFloat()
    var ax = anchorLocal.x + side
    if (ax + cardW > wPx - 8f) ax = anchorLocal.x - side - cardW
    ax = ax.coerceIn(8f, (wPx - cardW - 8f).coerceAtLeast(8f))
    var ay = anchorLocal.y - cardH / 3f
    val winH = with(density) { (cfg.screenHeightDp.dp).toPx() }
    ay = ay.coerceIn(8f, (winH - cardH - 8f).coerceAtLeast(8f))
    val offset = IntOffset(
        (boxPosInWindow.x + ax).roundToInt(),
        (boxPosInWindow.y + ay).roundToInt(),
    )

    val sig = cell.rsrpDbm ?: cell.rssiDbm

    Popup(alignment = Alignment.TopStart, offset = offset, onDismissRequest = {}) {
        Surface(
            shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
            color = Dark,
            border = BorderStroke(1.dp, CELL_TOWER_COLOR.copy(alpha = 0.75f)),
            shadowElevation = 14.dp,
        ) {
            Column(Modifier.width(popupW).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                        color = CELL_TOWER_COLOR.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, CELL_TOWER_COLOR.copy(alpha = 0.7f)),
                    ) {
                        Text(
                            if (cell.isRegistered) stringResource(R.string.det_tower_registered) else stringResource(R.string.det_tower_neighbor),
                            fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = CELL_TOWER_COLOR, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(cell.type, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                InlineInfoRow(stringResource(R.string.det_tower_signal), sig?.let { "$it dBm" } ?: "--")
                InlineInfoRow(stringResource(R.string.det_tower_distance), cell.distanceEstimateM?.let { "≈${"%.0f".format(it)}m" } ?: "--")
                InlineInfoRow(stringResource(R.string.det_tower_cell), "CID ${cell.cid} · TAC ${cell.tacLac}")
                InlineInfoRow(stringResource(R.string.det_tower_operator), cell.mccMnc.ifBlank { "--" })
                if (cell.bands.isNotEmpty()) {
                    InlineInfoRow(stringResource(R.string.det_tower_bands), cell.bands.joinToString("/") { "B$it" })
                }
                Spacer(Modifier.height(5.dp))
                Text(stringResource(R.string.det_tower_hint), fontFamily = Sans, fontSize = 7.5.sp, color = Gray, lineHeight = 11.sp)
            }
        }
    }
}

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
            (matchesTypeFilter(t, typeFilter) && (query.isBlank() || t.name.contains(query, ignoreCase = true)))
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Star(Gold, 8.dp)
            Spacer(Modifier.width(7.dp))
            Text(stringResource(R.string.det_fmt_target_list, filtered.size, targets.size), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red, maxLines = 1, modifier = Modifier.weight(1f))
            if (focusedId != null) {
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.det_tag_focus_denoise), fontFamily = Mono, fontSize = 8.sp, color = Gold)
            }
            Spacer(Modifier.width(6.dp))
            SnapshotChip(vm, dbVm)
        }
        Spacer(Modifier.height(4.dp))
        if (filtered.isEmpty()) {
            EmptyHint(if (targets.isEmpty()) stringResource(R.string.det_hint_radar_idle) else stringResource(R.string.det_hint_radar_no_match))
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
                            // 类别芯片:友方蓝 / WiFi 红 / BLE 青 / 经典 BT 绿 —— 与盘面 blip 同色
                            val typeLabel = when {
                                t.bonded -> stringResource(R.string.det_badge_friendly)
                                wifi -> "WIFI"
                                t.observedVia == "ble" -> "BLE"
                                else -> "BT"
                            }
                            Text(typeLabel, fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = radarBlipColor(t), modifier = Modifier.width(34.dp))
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
                                    if (hasLoc) stringResource(R.string.det_fmt_range_estimated, e.distanceM, e.bearingDeg.toInt())
                                    else stringResource(R.string.det_fmt_range_placeholder, t.distanceM, t.angleDeg.toInt()),
                                    fontFamily = Mono, fontSize = 8.sp,
                                    color = if (hasLoc) Gold else Gray,
                                )
                                // 威胁分级:与档案页战术评估共用同一套推演标准
                                val tier = radarThreatTier(t)
                                Text(
                                    stringResource(
                                        when (tier.key) {
                                            "high" -> R.string.det_threat_high
                                            "mid" -> R.string.det_threat_mid
                                            "friendly" -> R.string.det_threat_friendly
                                            else -> R.string.det_threat_far
                                        },
                                    ),
                                    fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = tier.color,
                                )
                            }
                        }
                        // ── 展开的内联技术详情：聚焦即降噪，详情就地展开 ──
                        if (expanded) {
                            Column(Modifier.padding(horizontal = 10.dp).padding(bottom = 8.dp)) {
                                InlineInfoRow(stringResource(R.string.det_info_id), if (wifi) t.id else t.address)
                                InlineInfoRow(stringResource(R.string.det_info_vendor), "${t.vendor} · OUI ${t.vendorOui}")
                                InlineInfoRow(stringResource(R.string.det_info_signal), stringResource(R.string.det_fmt_signal_detail, t.rssi, t.snrDb, t.noiseDbm))
                                if (wifi) {
                                    InlineInfoRow(stringResource(R.string.det_info_radio), "${t.frequencyMhz}MHz · ${t.channel} · ${t.channelWidth}")
                                    InlineInfoRow(stringResource(R.string.det_info_security), t.security.ifEmpty { "--" })
                                } else {
                                    InlineInfoRow(stringResource(R.string.det_info_class), "${t.deviceClassMajor}${if (t.deviceClassDetail.isNotEmpty()) " · ${t.deviceClassDetail}" else ""}")
                                    InlineInfoRow(stringResource(R.string.det_info_link), stringResource(R.string.det_fmt_link_bond, t.linkType, t.bondState))
                                    if (t.bleAdv?.hasRecord == true) {
                                        InlineInfoRow(
                                            stringResource(R.string.det_info_adv),
                                            stringResource(
                                                R.string.det_fmt_adv_info,
                                                if (t.txPowerDbm != null) "${t.txPowerDbm}dBm" else "—",
                                                t.bleAdv.serviceUuids.size,
                                                if (t.bleAdv.manufacturerId >= 0) "0x%04X".format(t.bleAdv.manufacturerId) else "—",
                                            ),
                                        )
                                    }
                                }
                                // 设备情报指纹
                                DeviceIntel.classify(t.name, t.id, t.bleAdv?.serviceUuids.orEmpty(), t.bleAdv?.manufacturerId)?.let { hit ->
                                    InlineInfoRow(stringResource(R.string.det_info_device_intel), stringResource(R.string.det_fmt_intel_confidence, hit.label, (hit.confidence * 100).toInt(), hit.basis))
                                }
                                // 方位推演状态行
                                InlineInfoRow(stringResource(R.string.det_info_bearing), locLabel(locNow[t.id]))
                                val pathLossN = if (wifi) RadarManager.WIFI_PATH_LOSS else RadarManager.BT_PATH_LOSS
                                InlineInfoRow(stringResource(R.string.det_info_distance_model), if (t.txPowerDbm != null) stringResource(R.string.det_fmt_range_model_tx, t.txPowerDbm, pathLossN) else stringResource(R.string.det_range_model_assumed, pathLossN))
                                InlineInfoRow(stringResource(R.string.det_info_tracking), stringResource(R.string.det_fmt_tracking, t.hitCount, SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(t.firstSeenMs))))
                                Spacer(Modifier.height(5.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { onPair(t) },
                                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                    color = Gold.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f)),
                                ) {
                                    Text(stringResource(R.string.det_btn_pair_popup), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
                                }
                                Spacer(Modifier.height(5.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        modifier = Modifier.weight(1f).clickable { onSelect(t.id) },
                                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                        color = Red.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                                    ) {
                                        Text(stringResource(R.string.det_btn_open_dossier), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f).clickable { onFocus(t.id) },
                                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                        color = Color.Transparent,
                                        border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f)),
                                    ) {
                                        Text(stringResource(R.string.det_btn_unfocus), fontFamily = Sans, fontSize = 10.sp, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (rows.size > MAX_RADAR_TRACK_ROWS) {
                Text(
                    stringResource(R.string.det_fmt_track_limit, MAX_RADAR_TRACK_ROWS, rows.size - MAX_RADAR_TRACK_ROWS),
                    fontFamily = Sans, fontSize = 8.sp, color = Gray,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** 方位推演状态文案 —— 列表展开行与档案页共用同一口径 */
@Composable
private fun locLabel(e: BearingEstimate?): String = when {
    e == null -> stringResource(R.string.det_loc_unavailable)
    e.state == LocState.ESTIMATED -> stringResource(
        R.string.det_fmt_loc_estimated,
        e.bearingDeg.toInt(), e.sectorHalfWidthDeg.toInt(), (e.confidence * 100).toInt(), e.sweepCount,
    )
    e.state == LocState.COLLECTING -> stringResource(R.string.det_fmt_loc_collecting, (e.coverage * 100).toInt())
    e.state == LocState.AMBIGUOUS && e.fwhmDeg >= 350f -> stringResource(R.string.det_loc_flat)
    e.state == LocState.AMBIGUOUS -> stringResource(R.string.det_loc_multipath)
    e.state == LocState.STALE -> stringResource(R.string.det_fmt_loc_stale, e.ageMs / 1000)
    else -> stringResource(R.string.det_loc_unavailable_short)
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
        TerminalHeader(stringResource(R.string.det_title_probe_monitor), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (running) stringResource(R.string.det_state_passive_watching) else stringResource(R.string.det_state_standby), fontFamily = Mono, fontSize = 10.sp, color = if (running) TacGreen else Gray, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.det_fmt_beacon_count, wifiTargets.size), fontFamily = Mono, fontSize = 10.sp, color = Gold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.det_probe_scope_note),
                fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            if (wifiTargets.isEmpty()) {
                EmptyHint(stringResource(R.string.det_hint_probe_wait))
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
                                ProbeField(stringResource(R.string.det_field_bssid_hex), t.id)
                                ProbeField(stringResource(R.string.det_field_vendor_oui), "${t.vendor} · ${t.vendorOui}")
                                ProbeField(stringResource(R.string.det_field_channel), "${t.channel} · ${t.frequencyMhz} MHz")
                                ProbeField(stringResource(R.string.det_field_width), t.channelWidth.ifEmpty { "--" })
                                ProbeField(stringResource(R.string.det_field_center_freq), "0: ${if (t.centerFreq0Mhz > 0) "${t.centerFreq0Mhz}MHz" else "--"} · 1: ${if (t.centerFreq1Mhz > 0) "${t.centerFreq1Mhz}MHz" else "--"}")
                                ProbeField(stringResource(R.string.det_field_capabilities), t.capabilitiesRaw.ifEmpty { "--" })
                                ProbeField(stringResource(R.string.det_field_security), t.security)
                                ProbeField(stringResource(R.string.det_field_rssi_snr), "${t.rssi}dBm / ${t.snrDb}dB")
                                ProbeField(stringResource(R.string.det_field_beacons), stringResource(R.string.det_fmt_beacons_seen, t.hitCount, SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(t.firstSeenMs))))
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
fun FileTransferScreen(vm: FileTransferViewModel, arpVm: ArpScanViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current
    var manualIp by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.sendFile(it, manualIp) } }
    // 同网主机:ARP Ping Sweep 结果点选,无需手输 IP
    val arpDevices by arpVm.devices.collectAsState()
    val arpRunning by arpVm.isRunning.collectAsState()
    LaunchedEffect(Unit) {
        if (arpDevices.isEmpty() && !arpRunning) arpVm.scan()
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.det_title_file_transfer), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                ActionButton(running, stringResource(R.string.det_btn_run_transfer), stringResource(R.string.det_btn_stop_transfer)) {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                error?.let { ErrorBanner(it) }

                Spacer(Modifier.height(6.dp))
                // ── 战术遥测条:本机/同网主机/已发送/状态 ──
                val sentCount by vm.sentCount.collectAsState()
                TacticalStatStrip(
                    listOf(
                        vm.localIp to stringResource(R.string.det_ft_local),
                        "${arpDevices.size}" to stringResource(R.string.tact_strip_devices),
                        "$sentCount" to stringResource(R.string.det_ft_sent),
                        (if (running) stringResource(R.string.tact_strip_on) else stringResource(R.string.tact_strip_off)) to stringResource(R.string.det_ft_state),
                    ),
                )
                Spacer(Modifier.height(6.dp))

                if (running) {
                    Text(stringResource(R.string.det_fmt_local_address, vm.localIp), fontFamily = Sans, fontSize = 10.sp, color = Gray)
                    // 高频数据区独立订阅：UDP 发现期间设备列表与发送计数持续刷新
                    FileTransferLivePanel(vm, arpDevices = arpDevices, arpRunning = arpRunning, onRescan = { arpVm.scan() }, manualIp = manualIp, onManualIp = { manualIp = it }, picker = picker)
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
    arpDevices: List<ArpEntry>,
    arpRunning: Boolean,
    onRescan: () -> Unit,
    manualIp: String,
    onManualIp: (String) -> Unit,
    picker: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Uri?>,
) {
    val peers by vm.peers.collectAsState()
    val sent by vm.sentCount.collectAsState()

    Column {
        if (peers.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            TermPanel(stringResource(R.string.det_panel_peers_found, peers.size)) {
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
            HintBreathing(stringResource(R.string.det_hint_no_transfer_peers))
            Spacer(Modifier.height(6.dp))
            // ── 同网主机:点选即填,无需手输 IP ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.det_ft_lan_hosts), fontFamily = Mono, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.weight(1f))
                Text(
                    stringResource(if (arpRunning) R.string.det_ft_scanning_lan else R.string.det_ft_scan_lan),
                    fontFamily = Mono, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Warn,
                    modifier = Modifier.clickable { if (!arpRunning) onRescan() }.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            if (arpDevices.isEmpty()) {
                Text(stringResource(R.string.det_ft_lan_empty), fontFamily = Sans, fontSize = 8.5.sp, color = Gray)
            } else {
                Surface(
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, BorderDim),
                ) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                        items(arpDevices.take(30), key = { it.ip }) { h ->
                            val on = manualIp == h.ip
                            Row(
                                Modifier.fillMaxWidth().clickable { onManualIp(h.ip) }.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (on) "▸" else " ", fontFamily = Mono, fontSize = 11.sp, color = Gold, modifier = Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(h.hostname.ifBlank { stringResource(R.string.det_ft_host) }.take(20), fontFamily = Sans, fontSize = 10.sp, color = if (on) White else Gray, maxLines = 1)
                                    Text(h.ip, fontFamily = Mono, fontSize = 8.5.sp, color = if (on) Gold else Gray)
                                }
                                Text(h.latency, fontFamily = Mono, fontSize = 7.5.sp, color = Gray)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TermInput(stringResource(R.string.det_ft_manual_ip), manualIp, placeholder = "192.168.x.x") { onManualIp(it) }
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { picker.launch(arrayOf("*/*")) },
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = Red.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Red.copy(alpha = 0.4f)),
        ) {
            Text(
                if (peers.isNotEmpty()) stringResource(R.string.det_btn_send_all) else if (manualIp.isNotBlank()) stringResource(R.string.det_btn_send_to, manualIp) else stringResource(R.string.det_btn_choose_file_send),
                fontFamily = Mono, fontSize = 13.sp, color = Red, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center,
            )
        }
        Text(stringResource(R.string.det_fmt_sent_files, sent), fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 文件快传收件面板 —— 独立订阅收到的文件 */
@Composable
private fun FileTransferInboxPanel(vm: FileTransferViewModel) {
    val received by vm.received.collectAsState()
    val ctx = LocalContext.current
    if (received != null) {
        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.det_panel_received_file)) {
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
                Text(stringResource(R.string.det_btn_open_file), fontFamily = Sans, fontSize = 11.sp, color = Red, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
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
        TerminalHeader(stringResource(R.string.det_title_secure_vault), back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput(stringResource(R.string.det_label_passphrase), pwd, placeholder = stringResource(R.string.det_hint_passphrase)) { vm.setPassphrase(it) }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(modifier = Modifier.weight(1f).clickable {
                        haptic.hud()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                            ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            camPermLauncher.launch(android.Manifest.permission.CAMERA)
                        } else vm.createPhotoIntent()?.let { photoLauncher.launch(it) }
                    }, shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = Red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Red.copy(alpha = 0.4f))) {
                        Text(stringResource(R.string.det_btn_photo), fontFamily = Sans, fontSize = 12.sp, color = Red, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    Surface(modifier = Modifier.weight(1f).clickable {
                        haptic.hud()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                            ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            camPermLauncher.launch(android.Manifest.permission.CAMERA)
                        } else vm.createVideoIntent()?.let { videoLauncher.launch(it) }
                    }, shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = Red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Red.copy(alpha = 0.4f))) {
                        Text(stringResource(R.string.det_btn_video), fontFamily = Sans, fontSize = 12.sp, color = Red, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    Surface(modifier = Modifier.weight(1f).clickable {
                        haptic.hud()
                        if (recording) { vm.stopRecording(); return@clickable }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                            ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        } else vm.startRecording()
                    }, shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = if (recording) Red else Red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Red.copy(alpha = if (recording) 0.8f else 0.4f))) {
                        Text(if (recording) stringResource(R.string.det_btn_stop) else stringResource(R.string.det_btn_record), fontFamily = Mono, fontSize = 12.sp, color = if (recording) White else Red, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Surface(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("*/*")) }, shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = Red.copy(alpha = 0.08f), border = BorderStroke(1.dp, Red.copy(alpha = 0.25f))) {
                    Text(stringResource(R.string.det_btn_import_encrypt), fontFamily = Sans, fontSize = 11.sp, color = RedDim, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
                }

                error?.let { ErrorBanner(it) }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.det_fmt_vault_files, entries.size), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(4.dp))

                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(entries) { e ->
                        Row(Modifier.fillMaxWidth().clickable { vm.decrypt(e) }.padding(vertical = 5.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (e.type == "photo") stringResource(R.string.det_tag_photo) else if (e.type == "video") stringResource(R.string.det_tag_video) else stringResource(R.string.det_tag_audio), fontFamily = Mono, fontSize = 10.sp, color = RedDim, modifier = Modifier.width(52.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.fileName, fontFamily = Mono, fontSize = 11.sp, color = White)
                                Text("${e.sizeBytes / 1024}KB", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                            }
                            GhostButton(stringResource(R.string.det_btn_delete)) { vm.delete(e) }
                        }
                    }
                }

                if (decrypted != null) {
                    Spacer(Modifier.height(6.dp))
                    TermPanel(stringResource(R.string.det_panel_decrypted)) {
                        Text("${decrypted!!.name} (${decrypted!!.length() / 1024}KB)", fontFamily = Mono, fontSize = 12.sp, color = White)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(modifier = Modifier.weight(1f).clickable {
                                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", decrypted!!)
                                ctx.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "*/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
                            }, shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = Red, border = BorderStroke(1.dp, Red)) {
                                Text(stringResource(R.string.det_btn_open), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                            Surface(modifier = Modifier.weight(1f).clickable { vm.clearDecrypted() }, shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = Color.Transparent, border = BorderStroke(1.dp, Red.copy(alpha = 0.3f))) {
                                Text(stringResource(R.string.det_btn_clear), fontFamily = Sans, fontSize = 12.sp, color = Red, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
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
        TerminalHeader(stringResource(R.string.det_title_rev_shell), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput(stringResource(R.string.det_label_listen_port), port, placeholder = stringResource(R.string.det_hint_port_example)) { vm.setPort(it) }
                Spacer(Modifier.height(8.dp))
                ActionButton(running, stringResource(R.string.det_btn_run_listen), stringResource(R.string.det_btn_stop_listen)) {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = if (connected) RedDim else Red, trackColor = Track)
                    Text(stringResource(R.string.det_fmt_listening, port, if (connected) stringResource(R.string.det_state_connected) else stringResource(R.string.det_state_waiting)), fontFamily = Mono, fontSize = 11.sp, color = if (connected) Red else White, modifier = Modifier.padding(top = 3.dp))
                }

                Spacer(Modifier.height(8.dp))
                // 高频数据区独立订阅：会话建立后日志持续流入
                RevShellLogPanel(vm, modifier = Modifier.weight(1f))
                Spacer(Modifier.height(6.dp))
                if (connected) {
                    TermInput(stringResource(R.string.det_label_command), cmd, placeholder = "whoami / id / ls -la") { vm.setCmd(it) }
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { vm.send() },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = Red.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Red.copy(alpha = 0.4f)),
                    ) {
                        Text(stringResource(R.string.det_btn_send_command), fontFamily = Sans, fontSize = 12.sp, color = Red, modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp), textAlign = TextAlign.Center)
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
    Text(stringResource(R.string.det_panel_session_log), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
    Spacer(Modifier.height(4.dp))
    LazyColumn(state = list, modifier = Modifier.fillMaxWidth().then(modifier)) {
        if (logs.isEmpty()) item { HintBreathing(stringResource(R.string.det_hint_revshell_log)) }
        items(logs) { l ->
            LogLine(
                if (l.direction == "in") stringResource(R.string.det_dir_received) else if (l.direction == "out") stringResource(R.string.det_dir_sent) else stringResource(R.string.det_dir_system),
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
        TerminalHeader(stringResource(R.string.det_title_payload), back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    TermInput(stringResource(R.string.det_label_target_ip), ip, modifier = Modifier.weight(1f)) { vm.setIp(it) }
                    Spacer(Modifier.width(8.dp))
                    TermInput(stringResource(R.string.det_label_port), port, modifier = Modifier.weight(1f)) { vm.setPort(it) }
                }
                Spacer(Modifier.height(8.dp))
                ActionButton(running = false, runLabel = stringResource(R.string.det_btn_generate_payloads), stopLabel = "") {
                    haptic.hud()
                    vm.generate()
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.det_fmt_payloads_generated, payloads.size, ip, port), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(payloads) { p ->
                        Spacer(Modifier.height(4.dp))
                        TermPanel("${p.name} (${p.lang})") {
                            SelectionContainer { Text(p.template, fontFamily = Mono, fontSize = 9.sp, color = White) }
                        }
                    }
                    if (payloads.isEmpty()) item { HintBreathing(stringResource(R.string.det_hint_payload_idle)) }
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
        TerminalHeader(stringResource(R.string.det_title_dir_brute), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput(stringResource(R.string.det_label_target_url), url, placeholder = "http://example.com") { vm.setUrl(it) }
                Spacer(Modifier.height(8.dp))
                ActionButton(running, stringResource(R.string.det_btn_run_enum), stringResource(R.string.det_btn_stop_enum)) {
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
            Text(stringResource(R.string.det_fmt_paths_tested, done, total, results.size), fontFamily = Sans, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 3.dp))
        }
        if (!running && results.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            HintBreathing(stringResource(R.string.det_hint_dirbrute_idle))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.det_fmt_hit_paths, results.size), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
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
        TerminalHeader(stringResource(R.string.det_title_cve), back)
        PageEnter(locked = running) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput(stringResource(R.string.det_label_search_keyword), query, placeholder = stringResource(R.string.det_hint_cve_query)) { vm.setQuery(it) }
                Spacer(Modifier.height(8.dp))
                ActionButton(running = running, runLabel = stringResource(R.string.det_btn_run_search), stopLabel = stringResource(R.string.det_btn_searching)) {
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
                    HintBreathing(stringResource(R.string.det_hint_cve_idle))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.det_fmt_search_results, results.size), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(results) { r ->
                        Spacer(Modifier.height(4.dp))
                        TermPanel("${r.id}  ${if (r.cvss != null) "CVSS ${"%.1f".format(r.cvss)}" else ""}") {
                            Text(r.description, fontFamily = Mono, fontSize = 10.sp, color = White)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.det_fmt_published, r.published), fontFamily = Sans, fontSize = 9.sp, color = Gray)
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
        val radarVm = if (app != null) previewViewModel { RadarViewModel(app) } else null
        if (vm == null || radarVm == null) PreviewFallback("WiFi 干扰引擎") else WifiDetailScreen(vm, radarVm, back = {})
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
        val arpVm = if (app != null) previewViewModel { ArpScanViewModel() } else null
        if (vm == null || walkieVm == null || dbVm == null || arpVm == null) PreviewFallback("设备雷达")
        else RadarScreen(vm, walkieVm, dbVm, arpVm, back = {}, radarAlertOn = true, onSelectTarget = {}, onOpenNetmap = {}, onOpenDb = {}, onDeauth = {}, onScanPorts = {}, onSsh = {}, onKick = {})
    }
}

@Preview(name = "文件快传", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun FileTransferPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vm = if (app != null) previewViewModel { FileTransferViewModel(app) } else null
        val arpVm = if (app != null) previewViewModel { ArpScanViewModel() } else null
        if (vm == null || arpVm == null) PreviewFallback("文件快传") else FileTransferScreen(vm, arpVm, back = {})
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
