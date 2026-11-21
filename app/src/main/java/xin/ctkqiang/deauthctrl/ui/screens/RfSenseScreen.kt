package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.RfSenseEngine
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TacticalStatStrip
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.RadarViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 穿墙感知 —— WiFi RSSI 扰动检测(商用 WiFi 感知基线技术)
 *
 * 人体活动改变多径传播,RSSI 出现短时方差/振荡。对每个 AP 的 RSSI
 * 做 30s 窗口统计聚合为活动指数,四级状态:静止/微动/活动/强活动。
 * 活动状态切换可触发触觉告警。
 *
 * ## 诚实边界(界面必须呈现)
 * 这是 RSSI 层级的扰动检测 —— 只能回答「有没有人在动」;
 * 真·CSI 感知(DensePose 路线,重建姿态)需要 root + 特定芯片
 * (Atheros CSI Tool / Intel 5300 / Nexmon CSI),现代手机无此通道。
 */
@Composable
fun RfSenseScreen(vm: RadarViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val targets by vm.targets.collectAsState()
    val haptic = LocalHapticFeedback.current
    val engine = remember { RfSenseEngine() }
    var state by remember { mutableStateOf(RfSenseEngine.RfSenseState()) }
    var alarmOn by remember { mutableStateOf(true) }
    // 波形历史:活动指数 60 点(1Hz)
    val wave = remember { mutableStateListOf<Float>() }
    val eventLog = remember { mutableStateListOf<Pair<Long, String>>() }
    var lastLevel by remember { mutableStateOf("calm") }

    DisposableEffect(Unit) {
        vm.acquireScan()
        onDispose { vm.releaseScan() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            state = engine.ingest(targets, System.currentTimeMillis())
            wave.add(0, state.motionIndex)
            if (wave.size > 60) wave.removeAt(wave.size - 1)
            if (state.level != lastLevel) {
                val now = System.currentTimeMillis()
                eventLog.add(0, now to state.level)
                if (eventLog.size > 30) eventLog.removeAt(eventLog.size - 1)
                if (alarmOn && (state.level == "motion" || state.level == "strong")) {
                    haptic.hud(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }
                lastLevel = state.level
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.rfsense_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            val levelColor = when (state.level) {
                "strong" -> Danger
                "motion" -> Warn
                "faint" -> Gold
                else -> TacGreen
            }
            val levelLabel = when (state.level) {
                "strong" -> stringResource(R.string.rfsense_strong)
                "motion" -> stringResource(R.string.rfsense_motion)
                "faint" -> stringResource(R.string.rfsense_faint)
                else -> stringResource(R.string.rfsense_calm)
            }
            TacticalStatStrip(
                listOf(
                    levelLabel to stringResource(R.string.rfsense_level),
                    "%.2f".format(state.motionIndex) to stringResource(R.string.rfsense_index),
                    (state.strongestAp.take(8).ifBlank { "--" }) to stringResource(R.string.rfsense_top_ap),
                    "${state.samples}" to stringResource(R.string.rfsense_aps),
                ),
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionButton(
                    running = running,
                    runLabel = stringResource(R.string.rfsense_btn_start),
                    stopLabel = stringResource(R.string.rfsense_btn_stop),
                    modifier = Modifier.weight(1f),
                ) {
                    haptic.hud()
                    if (running) vm.stop() else vm.start()
                }
                Surface(
                    modifier = Modifier.clickable { haptic.hud(); alarmOn = !alarmOn },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = if (alarmOn) Danger.copy(alpha = 0.12f) else SurfaceBg,
                    border = BorderStroke(1.dp, if (alarmOn) Danger.copy(alpha = 0.6f) else BorderDim),
                ) {
                    Text(stringResource(R.string.rfsense_alarm), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (alarmOn) Danger else Gray, modifier = Modifier.padding(horizontal = 9.dp, vertical = 12.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 活动指数波形(60s) ──
            Box(
                Modifier.fillMaxWidth().height(140.dp)
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .background(Color(0xFF02090F))
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    // 阈值参考线
                    val th = size.height * 0.40f
                    drawLine(Gray.copy(alpha = 0.4f), Offset(0f, size.height - th), Offset(size.width, size.height - th), 1f)
                    if (wave.size >= 2) {
                        val path = Path()
                        for (i in wave.indices) {
                            val x = size.width * (1f - i / 60f)
                            val y = size.height * (1f - wave[i].coerceIn(0f, 1f))
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, levelColor.copy(alpha = 0.16f))
                        drawPath(path, levelColor, style = Stroke(2f, cap = StrokeCap.Round))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            // ── 各 AP 扰动条 ──
            Text(stringResource(R.string.rfsense_ap_panel), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
            Spacer(Modifier.height(4.dp))
            if (state.perAp.isEmpty()) {
                EmptyHint(stringResource(R.string.rfsense_empty))
            } else {
                state.perAp.take(8).forEach { ap ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ap.name.take(14).ifBlank { ap.bssid }, fontFamily = Mono, fontSize = 8.sp, color = White, modifier = Modifier.width(120.dp))
                        Box(Modifier.weight(1f).height(5.dp).clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp)).background(SurfaceAlt)) {
                            Box(
                                Modifier.fillMaxWidth((ap.varDb / 8f).coerceIn(0.02f, 1f)).height(5.dp)
                                    .clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp))
                                    .background(if (ap.varDb > 5f) Danger else if (ap.varDb > 2.5f) Warn else TacGreen),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("%.1f".format(ap.varDb), fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── 事件日志 ──
            Text(stringResource(R.string.rfsense_log), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Warn)
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    if (eventLog.isEmpty()) {
                        Text(stringResource(R.string.rfsense_log_empty), fontFamily = Sans, fontSize = 8.sp, color = Gray, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        eventLog.take(8).forEach { (ts, level) ->
                            val label = when (level) {
                                "strong" -> stringResource(R.string.rfsense_strong)
                                "motion" -> stringResource(R.string.rfsense_motion)
                                "faint" -> stringResource(R.string.rfsense_faint)
                                else -> stringResource(R.string.rfsense_calm)
                            }
                            Text(
                                "${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ts))} ▸ $label",
                                fontFamily = Mono, fontSize = 8.sp,
                                color = if (level == "strong" || level == "motion") Danger else Gray,
                                modifier = Modifier.padding(vertical = 1.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.rfsense_honest), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            Spacer(Modifier.height(10.dp))
        }
    }
}
