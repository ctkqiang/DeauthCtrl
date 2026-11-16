package xin.ctkqiang.deauthctrl.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.White
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

/** 详情页内容整体入场动画 —— 每页进入时轻微淡入上滑 */
@Composable
private fun PageEnter(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(visible = shown, enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 8 }) { content() }
}

/** 进度条未激活段底色 */
private val Track = Color(0xFF2A2A2A)

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
    val log by vm.log.collectAsState(); val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val stats by vm.stats.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var profile by remember { mutableStateOf(vm.getCurrentProfile()) }
    var interval by remember { mutableFloatStateOf(vm.getCurrentInterval().toFloat()) }
    var txPower by remember { mutableStateOf(vm.getCurrentTxPower()) }
    var cycleMode by remember { mutableStateOf(vm.getCurrentCycleMode()) }
    var maxPacketsText by remember {
        val cur = vm.getCurrentMaxPackets()
        mutableStateOf(if (cur < 0) "" else cur.toString())
    }
    val list = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(log.size) { if (log.isNotEmpty()) list.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("BLE 泛洪引擎", back)
        PageEnter {
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

                ActionButton(running, "启动泛洪", "停止泛洪") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stopSpam() else vm.startSpam()
                }
                error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(top = 4.dp)) }
                AnimatedVisibility(running) {
                    Column { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track) }
                }

                Spacer(Modifier.height(8.dp))

                TermPanel("运行统计") {
                    val upSec = stats.uptimeMs / 1000
                    PanelStat("已发送", "${stats.sent}")
                    PanelStat("失败", "${stats.failed}", if (stats.failed > 0) Red else White)
                    PanelStat("总尝试", "${stats.total}")
                    PanelStat("成功率", "%.1f%%".format(stats.successRate * 100), if (stats.successRate > 0.5f) White else Red)
                    PanelStat("发包速率", "%.1f pps".format(stats.pps))
                    PanelStat("运行时长", "%02d:%02d".format(upSec / 60, upSec % 60))
                    if (stats.failRateRecent > 0.3f) {
                        Spacer(Modifier.height(4.dp))
                        Text("[!] 近期失败率 %.0f%%，建议增大发送间隔".format(stats.failRateRecent * 100), fontFamily = Mono, fontSize = 10.sp, color = Red)
                    }
                }

                Spacer(Modifier.height(10.dp))
                LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
                Spacer(Modifier.height(4.dp))
                LazyColumn(state = list, modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    if (log.isEmpty()) item { EmptyHint("启动泛洪后，发包记录会实时显示在这里") }
                    items(log.reversed()) { e ->
                        LogLine(
                            tf.format(Date(e.timestamp)),
                            "${e.profile.displayName}  ${if (e.success) "成功" else "失败"}  #${e.index.toString().padStart(5, '0')}",
                            if (e.success) White else Red,
                        )
                    }
                }
            }
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
    val log by vm.log.collectAsState(); val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val devices by vm.discoveredDevices.collectAsState(); val count by vm.discoveryCount.collectAsState()
    val stats by vm.stats.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("蓝牙压制引擎", back)
        PageEnter {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                TermPanel("状态") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatChip("BLE 泛洪", if (running) "运行中" else "已停止", running)
                        StatChip("设备发现", if (running) "运行中" else "已停止", running)
                        StatChip("已发现设备", "$count 台")
                    }
                }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, "启动压制", "停止压制") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stop() else vm.start()
                }
                error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(top = 4.dp)) }
                AnimatedVisibility(running) {
                    Column { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track) }
                }

                if (running || stats.blePackets > 0 || stats.inquiryCycles > 0) {
                    Spacer(Modifier.height(8.dp))
                    TermPanel("运行统计") {
                        PanelStat("BLE 数据包", "${stats.blePackets}")
                        PanelStat("扫描轮次", "${stats.inquiryCycles}")
                        PanelStat("发现设备", "${stats.devicesFound}")
                        PanelStat("运行时长", "${stats.uptimeMs / 1000}秒")
                    }
                }

                AnimatedVisibility(devices.isNotEmpty()) {
                    Column {
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

                Spacer(Modifier.height(10.dp))
                LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.fillMaxWidth().height(180.dp)) {
                    if (log.isEmpty()) item { EmptyHint("启动压制后，运行日志会实时显示在这里") }
                    items(log.takeLast(100).reversed()) { e ->
                        LogLine(tf.format(Date(e.timestamp)), "[${e.module}] ${e.message}", White)
                    }
                }
            }
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
    val ss by vm.scanState.collectAsState(); val hr by vm.hotspotResult.collectAsState(); val fl by vm.isFlooding.collectAsState()
    val stats by vm.stats.collectAsState()
    var sel by remember { mutableStateOf<WifiNetwork?>(null) }
    var dur by remember { mutableIntStateOf(vm.getDuration()) }
    var toggle by remember { mutableFloatStateOf(vm.getToggleInterval().toFloat()) }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WiFi 干扰引擎", back)
        PageEnter {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                ActionButton(running = ss is ScanState.Scanning, runLabel = "扫描附近网络", stopLabel = "正在扫描…") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.scanNetworks()
                }
                Spacer(Modifier.height(8.dp))

                when (val s = ss) {
                    is ScanState.Scanning -> Text("正在扫描 2.4/5GHz 频段…", fontFamily = Sans, fontSize = 12.sp, color = White)
                    is ScanState.Results -> {
                        Text("发现 ${s.networks.size} 个网络 · 点击选择目标", fontFamily = Sans, fontSize = 11.sp, color = Gray)
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(Modifier.fillMaxWidth().height(200.dp)) {
                            items(s.networks, key = { it.bssid }) { n ->
                                val on = sel?.bssid == n.bssid
                                Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); sel = n; vm.selectTarget(n) }, color = if (on) Red.copy(alpha = 0.08f) else Color.Transparent) {
                                    Row(Modifier.padding(vertical = 5.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (on) "▸" else " ", fontFamily = Mono, fontSize = 13.sp, color = Red, modifier = Modifier.width(18.dp))
                                        Text(n.bssid.take(17).padEnd(17), fontFamily = Mono, fontSize = 11.sp, color = Gray)
                                        Text(n.ssid.ifBlank { "<隐藏>" }.take(18), fontFamily = Mono, fontSize = 12.sp, color = if (on) White else Gray, modifier = Modifier.weight(1f))
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
                        ActionButton(fl, "启动干扰", "停止干扰") {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.setDuration(dur)
                            vm.setToggleInterval(toggle.toLong())
                            if (fl) vm.stopFlood() else vm.startFlood()
                        }
                    }
                }

                when (val r = hr) {
                    is HotspotResult.Progress -> {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { r.cycle.toFloat() / r.totalCycles }, modifier = Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                        Text("周期 ${r.cycle}/${r.totalCycles}", fontFamily = Sans, fontSize = 10.sp, color = White, modifier = Modifier.padding(top = 3.dp))
                    }
                    is HotspotResult.Completed -> Text("泛洪完成", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(top = 6.dp))
                    is HotspotResult.Error -> Text("[!] ${r.message}", fontFamily = Mono, fontSize = 12.sp, color = Red, modifier = Modifier.padding(top = 6.dp))
                    else -> {}
                }

                if (fl || stats.startCount > 0 || stats.stopCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    TermPanel("运行统计") {
                        PanelStat("周期", "${stats.cycles}/${stats.totalCycles}")
                        PanelStat("热点启动", "${stats.startCount}")
                        PanelStat("热点停止", "${stats.stopCount}")
                        PanelStat("运行时长", "${stats.uptimeMs / 1000}秒")
                    }
                }
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
    val log by vm.log.collectAsState(); val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val ss by vm.scanState.collectAsState(); val tc by vm.targetCount.collectAsState()
    val stats by vm.stats.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var ms by remember { mutableFloatStateOf(vm.getInterval().toFloat()) }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WiFi 压制引擎", back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermPanel("配置") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("信道切换间隔", fontFamily = Sans, fontSize = 11.sp, color = Gray)
                        Text("${ms.toLong()}ms", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                    }
                    Slider(ms, { ms = it; vm.setInterval(ms.toLong()) }, valueRange = 60f..300f, steps = 11, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = Track))
                }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, "启动压制", "停止压制") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stop() else vm.start()
                }
                error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(top = 4.dp)) }
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
                    is JammerScanState.Error -> Text("[!] ${s.message}", fontFamily = Mono, fontSize = 12.sp, color = Red)
                    else -> {}
                }

                Spacer(Modifier.height(8.dp))
                LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(0.5f)) {
                    if (log.isEmpty()) item { EmptyHint("启动压制后，运行日志会实时显示在这里") }
                    items(log.takeLast(80).reversed()) { e ->
                        LogLine(tf.format(Date(e.timestamp)), "[${e.module}] ${e.message}", White)
                    }
                }
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
    val stats by vm.stats.collectAsState()
    val log by vm.log.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
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
        PageEnter {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                TermPanel("网页文件") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fileName, fontFamily = Mono, fontSize = 12.sp, color = White, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        GhostButton("选择文件") { filePicker.launch(arrayOf("text/html")) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, "启动服务器", "停止服务器") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stopServer() else vm.startServer()
                }
                error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(top = 4.dp)) }

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

                Spacer(Modifier.height(8.dp))
                TermPanel("运行统计") {
                    PanelStat("总请求", "${stats.totalRequests}")
                    PanelStat("成功响应", "${stats.served200}")
                    PanelStat("未找到", "${stats.notFound404}")
                    PanelStat("方法拒绝", "${stats.rejectedMethod}")
                    PanelStat("错误请求", "${stats.badRequests}")
                    PanelStat("处理异常", "${stats.handlerErrors}")
                    PanelStat("活动连接", "${stats.activeConnections}")
                    PanelStat("每分钟请求", "%.1f".format(stats.requestsPerMinute))
                    PanelStat("运行时长", vm.formatUptime(stats.uptimeMs))
                    PanelStat("状态", if (running) "监听中" else "已停止", if (running) White else Gray)
                }

                Spacer(Modifier.height(10.dp))
                LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
                Spacer(Modifier.height(4.dp))
                if (log.isEmpty()) {
                    EmptyHint("启动服务器后，访问日志会实时显示在这里")
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        log.takeLast(LOG_TAIL).reversed().forEach { e ->
                            LogLine(tf.format(Date(e.timestamp)), "[${e.module}] ${e.message}", White)
                        }
                    }
                }
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
    val devices by vm.devices.collectAsState()
    val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val stats by vm.stats.collectAsState()
    val log by vm.log.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("ARP 网络扫描", back)
        PageEnter {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(12.dp)) {
                ActionButton(running, "开始扫描", "取消扫描") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stop() else vm.scan()
                }

                error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(top = 4.dp)) }

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
                    PanelStat("网段", stats.subnet.ifEmpty { "—" })
                    PanelStat("本机地址", stats.localIp.ifEmpty { "—" })
                    PanelStat("已探测", "${stats.hostsScanned} / ${stats.hostCount}")
                    PanelStat("在线主机", "${stats.hostsReachable}", if (stats.hostsReachable > 0) White else Gray)
                    PanelStat("缓存命中", "${stats.arpCacheHits}")
                    PanelStat("反向解析", "${stats.hostnameResolved}")
                    PanelStat("发现设备", "${stats.deviceCount}")
                    PanelStat("耗时", vm.formatElapsed(stats.elapsedMs))
                    PanelStat("状态", if (running) "扫描中" else "空闲", if (running) White else Gray)
                }

                AnimatedVisibility(devices.isNotEmpty()) {
                    Column {
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
                }

                Spacer(Modifier.height(10.dp))
                LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
                Spacer(Modifier.height(4.dp))
                if (log.isEmpty()) {
                    EmptyHint("扫描开始后，内核日志会实时显示在这里")
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        log.takeLast(LOG_TAIL).reversed().forEach { e ->
                            LogLine(tf.format(Date(e.timestamp)), "[${e.module}] ${e.message}", White)
                        }
                    }
                }

                if (!running && devices.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    EmptyHint("点击上方按钮扫描局域网，在线设备会实时列出")
                }
            }
        }
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
    val devices by vm.devices.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current
    var selected by remember { mutableStateOf<String?>(null) }
    val selDev = devices.find { it.address == selected }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("BLE 嗅探扫描", back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                ActionButton(running, "开始嗅探", "停止嗅探") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stop() else vm.start()
                }
                error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(top = 4.dp)) }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                    Text("已发现 ${devices.size} 台设备", fontFamily = Sans, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 3.dp))
                }

                if (!running && devices.isEmpty() && error == null) {
                    Spacer(Modifier.height(8.dp))
                    EmptyHint("点击上方按钮开始嗅探，附近 BLE 设备会实时出现")
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
                            Box(Modifier.fillMaxWidth().height(60.dp).background(Color(0xFF020202)).border(0.5.dp, Track, RoundedCornerShape(4.dp))) {
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
                            Text("最低:${selDev.rssiHistory.minOrNull() ?: 0}  最高:${selDev.rssiHistory.maxOrNull() ?: 0}  当前:${selDev.rssi}", fontFamily = Sans, fontSize = 9.sp, color = Gray, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("已发现 ${devices.size} 台设备 · 点击查看详情", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(devices) { d ->
                        val sel = d.address == selected
                        Surface(modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selected = if (sel) null else d.address }, color = if (sel) Red.copy(alpha = 0.06f) else Color.Transparent) {
                            Row(Modifier.padding(vertical = 5.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (sel) "▸" else " ", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.width(14.dp))
                                Text(d.name.take(20), fontFamily = Mono, fontSize = 11.sp, color = if (sel) Red else White, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
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
    val results by vm.results.collectAsState()
    val sent by vm.sent.collectAsState(); val recv by vm.received.collectAsState()
    val loss by vm.loss.collectAsState()
    val min by vm.min.collectAsState(); val avg by vm.avg.collectAsState(); val max by vm.max.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current
    val list = rememberLazyListState()
    LaunchedEffect(results.size) { if (results.isNotEmpty()) list.animateScrollToItem(results.size - 1) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("PING 泛洪", back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput("目标主机", host, placeholder = "例如 8.8.8.8 或 baidu.com") { vm.setHost(it) }

                Spacer(Modifier.height(8.dp))
                ActionButton(running, "开始探测", "停止探测") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stop() else vm.start()
                }

                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                }
                error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(top = 4.dp)) }

                if (!running && sent == 0 && error == null) {
                    Spacer(Modifier.height(8.dp))
                    EmptyHint("填入主机名或 IP 地址，点击上方按钮开始探测")
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
                        items(results) { r ->
                            LogLine("seq=${r.sequence}", "${r.bytes}b 来自 ${r.ip} · ttl=${r.ttl} · 耗时 ${r.timeMs}ms", if (r.timeMs > 500) Red else if (r.timeMs > 100) RedDim else White)
                        }
                    }
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
    val stats by vm.stats.collectAsState()
    val log by vm.log.collectAsState()
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current
    val verb = method.trim().uppercase(Locale.US)

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("HTTP 客户端", back)
        PageEnter {
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
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.cancel() else vm.send()
                }
                if (!running && url.isNotBlank()) {
                    Text(url.trim(), fontFamily = Mono, fontSize = 10.sp, color = White.copy(alpha = 0.5f), maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                }
                error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(top = 4.dp)) }

                resp?.let { r ->
                    Spacer(Modifier.height(10.dp))
                    AsciiDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("状态 ${r.statusCode} · 耗时 ${r.timeMs}ms", fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (r.statusCode < 400) White else Red,
                    )
                    PanelStat("响应大小", vm.formatBytes(r.bodyBytes.toLong()))
                    PanelStat("字符集", r.charset)
                    if (r.redirected && r.finalUrl.isNotEmpty()) {
                        PanelStat("重定向落点", r.finalUrl, Gray)
                    }
                    if (r.truncated) {
                        Text("[!] 响应体超过 ${vm.formatBytes(HttpClientManager.MAX_BODY_BYTES.toLong())}，已截断", fontFamily = Sans, fontSize = 10.sp, color = Red, modifier = Modifier.padding(top = 3.dp))
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
                            .border(1.dp, Track, RoundedCornerShape(6.dp))
                            .padding(10.dp),
                    ) {
                        item { SelectionContainer { Text(r.body.ifEmpty { "（空）" }, fontFamily = Mono, fontSize = 10.sp, color = White) } }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TermPanel("请求统计") {
                    PanelStat("总请求", "${stats.attempts}")
                    PanelStat("成功 2xx", "${stats.successCount}")
                    PanelStat("重定向 3xx", "${stats.redirectCount}")
                    PanelStat("客户端错误 4xx", "${stats.clientErrorCount}")
                    PanelStat("服务端错误 5xx", "${stats.serverErrorCount}")
                    PanelStat("失败", "${stats.failureCount}", if (stats.failureCount > 0) Red else White)
                    PanelStat("接收流量", vm.formatBytes(stats.bytesReceived))
                    PanelStat("平均耗时", "${stats.averageDurationMs} ms")
                    PanelStat("最近状态", if (stats.lastStatusCode == 0) "-" else "${stats.lastStatusCode}")
                    PanelStat("最近耗时", "${stats.lastDurationMs} ms")
                    if (stats.lastError.isNotEmpty()) PanelStat("最近错误", stats.lastError, Red)
                    if (stats.redirected && stats.lastFinalUrl.isNotEmpty()) PanelStat("最近落点", stats.lastFinalUrl, Gray)
                    PanelStat("状态", if (running) "运行中" else "空闲", if (running) White else Gray)
                }

                Spacer(Modifier.height(10.dp))
                LogHeader(showClear = log.isNotEmpty()) { vm.clearLog() }
                Spacer(Modifier.height(4.dp))
                if (log.isEmpty()) {
                    EmptyHint("发送请求后，传输日志会实时显示在这里")
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        log.takeLast(LOG_TAIL).reversed().forEach { e ->
                            LogLine(tf.format(Date(e.timestamp)), "[${e.module}] ${e.message}", White)
                        }
                    }
                }
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
    val results by vm.results.collectAsState()
    val scanned by vm.scanned.collectAsState()
    val total by vm.total.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current
    val open = results.filter { it.state == "open" }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("端口扫描", back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput("目标主机", host, placeholder = "例如 192.168.1.1") { vm.setHost(it) }
                Spacer(Modifier.height(8.dp))
                ActionButton(running, "开始扫描", "取消扫描") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stop() else vm.start()
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { scanned.toFloat() / maxOf(1, total) }, modifier = Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                    Text("已扫描 $scanned/$total · 开放 ${open.size}", fontFamily = Sans, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 3.dp))
                }

                error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(top = 4.dp)) }

                if (!running && results.isEmpty() && error == null) {
                    Spacer(Modifier.height(8.dp))
                    EmptyHint("输入目标主机后启动扫描，开放端口会实时列出")
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
    val peers by vm.peers.collectAsState()
    val error by vm.error.collectAsState()
    val isLive by vm.isLive.collectAsState()
    val countdown by vm.liveCountdown.collectAsState()
    val haptic = LocalHapticFeedback.current

    val active = talking || isLive
    val breathPulse = rememberInfiniteTransition()
    val breathAlpha by breathPulse.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse))

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("WiFi 对讲机", back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                ActionButton(running, "启动对讲", "停止对讲") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stop() else vm.start()
                }
                error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(top = 4.dp)) }

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
                                            .background(if (p.isTalking) Red.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(4.dp))
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

                Spacer(Modifier.weight(1f))

                if (running) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.toggleLive()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isLive) Red.copy(alpha = 0.15f) else Color.Transparent,
                        border = BorderStroke(1.5.dp, if (isLive) Gold else Red.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            if (isLive) "直播中 · ${countdown}s" else "直播模式",
                            fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (isLive) Red else Red.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 11.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(96.dp)
                            .pointerInput(isLive) {
                                detectTapGestures(
                                    onPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!isLive) vm.startTalk()
                                        tryAwaitRelease()
                                        if (!isLive) vm.stopTalk()
                                    },
                                )
                            }
                            .background(if (active) Red.copy(alpha = breathAlpha) else Red.copy(alpha = 0.12f))
                            .border(2.dp, if (active) Gold.copy(alpha = 0.95f) else Red.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (active) "●●● 发送中 ●●●" else "按住说话",
                            fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (active) White else Red,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 设备雷达
// ══════════════════════════════════════════════════════════════════════════

/** 设备雷达页 —— 军用 AN/APG 风格 PPI 雷达 + 磷光轨迹 + 罗盘标尺 + 目标识别 */
@Composable
fun RadarScreen(vm: RadarViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState(); val targets by vm.targets.collectAsState()
    val haptic = LocalHapticFeedback.current
    val sweep = rememberInfiniteTransition()
    val sweepAngle by sweep.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)))

    // 每完成一整圈扫描触发一次触觉反馈
    var prevAngle by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { sweepAngle }.collect { cur ->
            if (cur < prevAngle) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            prevAngle = cur
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("设备雷达", back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                ActionButton(running, "启动雷达", "停止雷达") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stop() else vm.start()
                }
                if (running) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF00FF00), trackColor = Track)
                }

                if (!running && targets.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    EmptyHint("点击上方按钮开始扫描，检测到的设备会标在雷达上")
                }

                Spacer(Modifier.height(8.dp))
                val scopeGreen = Color(0xFF00FF00)
                val scopeGreenDim = Color(0xFF004400)
                Box(Modifier.fillMaxWidth().weight(0.6f).background(Color(0xFF001100)).border(2.dp, Color(0xFF003300), RoundedCornerShape(6.dp))) {
                    Canvas(Modifier.fillMaxSize().padding(16.dp)) {
                        val cx = size.width / 2; val cy = size.height / 2
                        val maxR = minOf(cx, cy) * 0.82f
                        val g = scopeGreen

                        drawCircle(Color(0xFF001800), maxR, Offset(cx, cy))

                        listOf(0.25f, 0.5f, 0.75f, 1.0f).forEachIndexed { i, s ->
                            val r = maxR * s
                            drawCircle(g.copy(alpha = 0.25f), r, Offset(cx, cy), style = Stroke(if (i == 3) 1.2f else 0.6f))
                        }
                        drawLine(g.copy(alpha = 0.2f), Offset(cx - maxR, cy), Offset(cx + maxR, cy), 0.6f)
                        drawLine(g.copy(alpha = 0.2f), Offset(cx, cy - maxR), Offset(cx, cy + maxR), 0.6f)
                        for (deg in 0..350 step 10) {
                            val a = Math.toRadians(deg.toDouble()).toFloat()
                            val isMajor = deg % 30 == 0
                            val len = if (isMajor) 0.92f else 0.96f
                            drawLine(g.copy(alpha = if (isMajor) 0.35f else 0.15f), Offset(cx + maxR * len * cos(a), cy + maxR * len * sin(a)), Offset(cx + maxR * cos(a), cy + maxR * sin(a)), if (isMajor) 0.7f else 0.3f)
                        }

                        val sweepRad = Math.toRadians(sweepAngle.toDouble()).toFloat()
                        for (i in 0..8) {
                            val trailAngle = sweepRad - i * 0.025f
                            val trailAlpha = 0.55f - i * 0.06f
                            drawLine(g.copy(alpha = trailAlpha.coerceAtLeast(0.02f)), Offset(cx, cy), Offset(cx + maxR * cos(trailAngle), cy + maxR * sin(trailAngle)), (2.5f - i * 0.25f).coerceAtLeast(0.3f))
                        }
                        drawCircle(g.copy(alpha = 0.95f), 5.dp.toPx(), Offset(cx, cy))

                        if (running || targets.isNotEmpty()) {
                            targets.forEach { t ->
                                val r = ((t.distanceM / 100f).coerceIn(0.03f, 1f) * maxR).coerceAtLeast(3f)
                                val angleRad = Math.toRadians(t.angleDeg.toDouble()).toFloat()
                                val x = cx + r * cos(angleRad); val y = cy + r * sin(angleRad)
                                val isWifi = t.type == "wifi"
                                val dotColor = if (isWifi) Color.Red else Color.White
                                drawCircle(dotColor.copy(alpha = 0.15f), 10.dp.toPx(), Offset(x, y))
                                drawCircle(dotColor.copy(alpha = 0.4f), 6.dp.toPx(), Offset(x, y))
                                drawCircle(dotColor.copy(alpha = 0.9f), 3.5.dp.toPx(), Offset(x, y))
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp).background(Color(0xFF001100)).border(0.5.dp, Color(0xFF003300), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (running) "模式: 扫描中" else "模式: 待机", fontFamily = Mono, fontSize = 8.sp, color = scopeGreen)
                    Text("增益: 自动", fontFamily = Sans, fontSize = 8.sp, color = scopeGreen)
                    Text("量程: 100米", fontFamily = Sans, fontSize = 8.sp, color = scopeGreen)
                    Text("方位角: ${"%.0f".format(sweepAngle)}°", fontFamily = Mono, fontSize = 8.sp, color = scopeGreen)
                    Text("目标: ${targets.size}", fontFamily = Sans, fontSize = 8.sp, color = Color(0xFFFF8800))
                }

                Spacer(Modifier.height(6.dp))
                Text("目标列表 · ${targets.size}", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(targets.sortedByDescending { it.rssi }) { t ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (t.type == "wifi") "WIFI" else " BLE", fontFamily = Mono, fontSize = 8.sp, color = if (t.type == "wifi") Red else Color.White, modifier = Modifier.width(36.dp))
                            Text(t.name.take(14), fontFamily = Mono, fontSize = 10.sp, color = White, modifier = Modifier.weight(1f))
                            Text(" ${t.rssi}dBm".padStart(7), fontFamily = Mono, fontSize = 9.sp, color = if (t.rssi > -50) Red else if (t.rssi > -70) Color(0xFF00CC00) else Gray)
                            Text(" ${"%.1f".format(t.distanceM)}米".padStart(8), fontFamily = Mono, fontSize = 9.sp, color = Gray)
                            Text(" 方位${t.angleDeg.toInt().toString().padStart(3)}°".padStart(9), fontFamily = Mono, fontSize = 9.sp, color = scopeGreenDim)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 文件快传
// ══════════════════════════════════════════════════════════════════════════

/** 文件快传页 —— 局域网文件传输 */
@Composable
fun FileTransferScreen(vm: FileTransferViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState(); val peers by vm.peers.collectAsState()
    val received by vm.received.collectAsState(); val error by vm.error.collectAsState()
    val sent by vm.sentCount.collectAsState()
    val haptic = LocalHapticFeedback.current
    var manualIp by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.sendFile(it, manualIp) } }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("文件快传", back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                ActionButton(running, "启动快传", "停止快传") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stop() else vm.start()
                }
                error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(top = 4.dp)) }

                if (running) {
                    Spacer(Modifier.height(6.dp))
                    Text("本机地址: ${vm.localIp}", fontFamily = Sans, fontSize = 10.sp, color = Gray)
                    if (peers.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        TermPanel("已发现设备 (${peers.size})") {
                            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 100.dp)) {
                                items(peers) { p ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(p.name.take(16), fontFamily = Mono, fontSize = 10.sp, color = White, modifier = Modifier.weight(1f))
                                        Text(p.ip, fontFamily = Mono, fontSize = 10.sp, color = Gray)
                                    }
                                }
                            }
                        }
                    }
                    if (peers.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        EmptyHint("未发现设备 —— 输入对方 IP 手动发送")
                        Spacer(Modifier.height(6.dp))
                        TermInput("对方 IP", manualIp, placeholder = "192.168.x.x") { manualIp = it }
                    }

                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { picker.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(8.dp),
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

                if (received != null) {
                    Spacer(Modifier.height(8.dp))
                    val ctx = LocalContext.current
                    TermPanel("收到文件") {
                        Text("${received!!.name} (${received!!.length() / 1024}KB)", fontFamily = Mono, fontSize = 11.sp, color = White)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", received!!)
                                ctx.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "*/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
                            },
                            shape = RoundedCornerShape(7.dp),
                            color = Red.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Red.copy(alpha = 0.3f)),
                        ) {
                            Text("打开文件", fontFamily = Sans, fontSize = 11.sp, color = Red, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
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
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                            ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            camPermLauncher.launch(android.Manifest.permission.CAMERA)
                        } else vm.createPhotoIntent()?.let { photoLauncher.launch(it) }
                    }, shape = RoundedCornerShape(8.dp), color = Red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Red.copy(alpha = 0.4f))) {
                        Text("拍照", fontFamily = Sans, fontSize = 12.sp, color = Red, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    Surface(modifier = Modifier.weight(1f).clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                            ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            camPermLauncher.launch(android.Manifest.permission.CAMERA)
                        } else vm.createVideoIntent()?.let { videoLauncher.launch(it) }
                    }, shape = RoundedCornerShape(8.dp), color = Red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Red.copy(alpha = 0.4f))) {
                        Text("摄像", fontFamily = Sans, fontSize = 12.sp, color = Red, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    Surface(modifier = Modifier.weight(1f).clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (recording) { vm.stopRecording(); return@clickable }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                            ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        } else vm.startRecording()
                    }, shape = RoundedCornerShape(8.dp), color = if (recording) Red else Red.copy(alpha = 0.12f), border = BorderStroke(1.dp, Red.copy(alpha = if (recording) 0.8f else 0.4f))) {
                        Text(if (recording) "停止" else "录音", fontFamily = Mono, fontSize = 12.sp, color = if (recording) White else Red, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Surface(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("*/*")) }, shape = RoundedCornerShape(8.dp), color = Red.copy(alpha = 0.08f), border = BorderStroke(1.dp, Red.copy(alpha = 0.25f))) {
                    Text("导入文件加密", fontFamily = Sans, fontSize = 11.sp, color = RedDim, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
                }

                error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(top = 4.dp)) }
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
                            }, shape = RoundedCornerShape(7.dp), color = Red, border = BorderStroke(1.dp, Red)) {
                                Text("打开", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                            Surface(modifier = Modifier.weight(1f).clickable { vm.clearDecrypted() }, shape = RoundedCornerShape(7.dp), color = Color.Transparent, border = BorderStroke(1.dp, Red.copy(alpha = 0.3f))) {
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
    val logs by vm.logs.collectAsState(); val port by vm.port.collectAsState(); val cmd by vm.cmd.collectAsState()
    val haptic = LocalHapticFeedback.current
    val list = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) list.animateScrollToItem(logs.size - 1) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("反弹 Shell", back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput("监听端口", port, placeholder = "例如 4444") { vm.setPort(it) }
                Spacer(Modifier.height(8.dp))
                ActionButton(running, "开始监听", "停止监听") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stop() else vm.start()
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = if (connected) RedDim else Red, trackColor = Track)
                    Text("监听 0.0.0.0:$port · ${if (connected) "已连接" else "等待连接"}", fontFamily = Mono, fontSize = 11.sp, color = if (connected) Red else White, modifier = Modifier.padding(top = 3.dp))
                }

                Spacer(Modifier.height(8.dp))
                Text("会话日志", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(4.dp))
                LazyColumn(state = list, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (logs.isEmpty()) item { EmptyHint("等待连接建立，会话内容会显示在这里") }
                    items(logs) { l ->
                        LogLine(
                            if (l.direction == "in") "收到" else if (l.direction == "out") "发出" else "系统",
                            l.text,
                            when (l.direction) { "in" -> Color(0xFF00CC00); "out" -> Red; else -> Gray },
                        )
                    }
                }
                if (connected) {
                    Spacer(Modifier.height(6.dp))
                    TermInput("命令", cmd, placeholder = "whoami / id / ls -la") { vm.setCmd(it) }
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { vm.send() },
                        shape = RoundedCornerShape(8.dp),
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
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    if (payloads.isEmpty()) item { EmptyHint("填写目标 IP 与端口后点击生成，所有语言的载荷会在这里列出") }
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
    val results by vm.results.collectAsState()
    val (done, total) = vm.progress.collectAsState().value
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("目录爆破", back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput("目标 URL", url, placeholder = "http://example.com") { vm.setUrl(it) }
                Spacer(Modifier.height(8.dp))
                ActionButton(running, "开始枚举", "停止枚举") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stop() else vm.start()
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { done.toFloat() / maxOf(1, total) }, modifier = Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                    Text("已测试 $done/$total 个路径 · 命中 ${results.size}", fontFamily = Sans, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 3.dp))
                }
                if (!running && results.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    EmptyHint("输入目标 URL 后启动枚举，命中路径会实时列出")
                }
                Spacer(Modifier.height(8.dp))
                Text("命中路径 · ${results.size}", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(results) { r ->
                        LogLine(
                            "${r.status}",
                            "${r.path}  ${r.size}b  ${r.timeMs}ms",
                            if (r.status == 200) Red else if (r.status in 301..303) Color(0xFF00CC00) else White,
                        )
                    }
                }
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
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
                TermInput("搜索关键词", query, placeholder = "CVE 编号 / 厂商 / 产品名") { vm.setQuery(it) }
                Spacer(Modifier.height(8.dp))
                ActionButton(running = running, runLabel = "开始搜索", stopLabel = "搜索中…") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.search()
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Track)
                }
                if (status.isNotBlank()) Text(status, fontFamily = Mono, fontSize = 11.sp, color = White, modifier = Modifier.padding(top = 4.dp))
                if (!running && results.isEmpty() && status.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    EmptyHint("输入 CVE 编号、厂商或产品名后搜索")
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
// 关于
// ══════════════════════════════════════════════════════════════════════════

/** 信息行 —— 「标签 … 值」两端对齐 */
@Composable
private fun InfoRow(label: String, value: String) {
    PanelStat(label, value)
}

/**
 * 关于页面
 *
 * 显示应用与作者信息，终端命令风格排版。
 */
@Composable
fun AboutDetailScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("关于", back)
        PageEnter {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(14.dp)) {
                StarBadge(56.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
                Text(
                    "去认证控制",
                    fontFamily = Sans,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp,
                    color = White,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    "★ DEAUTHCTRL v1.0 ★",
                    fontFamily = Sans,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(14.dp))
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
                Text("  DeauthCtrl v1.0  |  中国红客  |  国产自主  |  安全可控", fontFamily = Sans, fontSize = 11.sp, color = White)
                Spacer(Modifier.height(6.dp))
                Text("$ whoami", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
                Text("  哪吒网络安全 (ctkqiang)", fontFamily = Sans, fontSize = 11.sp, color = White)
                Spacer(Modifier.height(14.dp))
                EmptyHint("本应用仅供授权安全研究与教育用途，请勿用于非法活动")
            }
        }
    }
}
