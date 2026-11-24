package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.MainsWireDetector
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.PanelStat
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.MainsWireViewModel

/**
 * 墙内电线探测 —— 工频(50/60Hz)声学特征(麦克风拾音)
 *
 * 麦克风实时采样 → Goertzel 检测工频基波与谐波 → 与整帧 RMS 的比值
 * 经自适应基线归一为 0-100 电平。靠近有负载的带电线路时电平与告警
 * 会明显抬升;探测的是 50/60Hz 特征,不是透视,空载线路不可见。
 */
@Composable
fun MainsWireScreen(back: () -> Unit) {
    val vm: MainsWireViewModel = viewModel()
    val state by vm.state.collectAsState()

    // 进入页面即开始采集,离开页面释放麦克风;按钮保留手动启停
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }

    val levelColor = when {
        !state.running -> Gray
        state.alert -> Danger
        state.level >= 80 -> Warn
        else -> TacGreen
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.mains_title), back)
        Column(
            Modifier.fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            // ── 控制 ──────────────────────────────────────────
            TermPanel(stringResource(R.string.mains_panel_control)) {
                PanelStat(
                    stringResource(R.string.mains_label_status),
                    if (state.running) stringResource(R.string.mains_status_running) else stringResource(R.string.mains_status_stopped),
                    if (state.running) TacGreen else Gray,
                )
                PanelStat(stringResource(R.string.mains_label_mains_mode), stringResource(R.string.mains_mains_mode_value, state.freqMode, state.freqMode * 2), Gold)
                PanelStat(stringResource(R.string.mains_label_capture_params), stringResource(R.string.mains_capture_params_value, state.sampleRateHz, state.frameSamples), White)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectChip(state.freqMode == 50, stringResource(R.string.mains_chip_50hz)) { vm.setFreqMode(50) }
                    SelectChip(state.freqMode == 60, stringResource(R.string.mains_chip_60hz)) { vm.setFreqMode(60) }
                }
                Text(
                    stringResource(R.string.mains_mode_note),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    running = state.running,
                    runLabel = stringResource(R.string.mains_start),
                    stopLabel = stringResource(R.string.mains_stop),
                ) {
                    if (state.running) vm.stop() else vm.start()
                }
                state.error?.let {
                    Text(
                        "[!] $it",
                        fontFamily = Sans,
                        fontSize = 9.sp,
                        color = Danger,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (state.running && state.calibrating) {
                    Text(
                        stringResource(R.string.mains_calibrating),
                        fontFamily = Sans,
                        fontSize = 9.sp,
                        color = Gold,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }

            // ── 实时电平 ──────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.mains_panel_live_level)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "LEVEL",
                        fontFamily = Sans,
                        fontSize = 10.sp,
                        color = Gray,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${state.level}",
                        fontFamily = Mono,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = levelColor,
                    )
                    Text(" / 100", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .height(30.dp)
                        .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, BorderDim, CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)),
                ) {
                    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                        val segs = 24
                        val gap = 2.dp.toPx()
                        val segW = ((size.width - gap * (segs - 1)) / segs).coerceAtLeast(1f)
                        val lit = (state.level / 100f * segs).roundToInt().coerceIn(0, segs)
                        for (i in 0 until segs) {
                            drawRect(
                                color = if (i < lit) levelColor else BorderDim.copy(alpha = 0.35f),
                                topLeft = Offset(i * (segW + gap), 0f),
                                size = Size(segW, size.height),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                PanelStat(
                    stringResource(R.string.mains_label_strength),
                    "%.3f".format(state.strength),
                    if (state.alert) Danger else Gold,
                )
                PanelStat(
                    stringResource(R.string.mains_label_baseline),
                    if (state.calibrating) stringResource(R.string.mains_baseline_calibrating) else "${state.baselineLevel} / 100",
                    Gray,
                )
                PanelStat(stringResource(R.string.mains_label_frame_rms), state.rmsDb?.let { "%.1f dBFS".format(it) } ?: "--", White)
                PanelStat(
                    stringResource(R.string.mains_label_fundamental, state.freqMode),
                    state.fundDb?.let { "%.1f dBFS".format(it) } ?: "--",
                    White,
                )
                PanelStat(
                    stringResource(R.string.mains_label_harmonic, state.freqMode * 2),
                    state.harmDb?.let { "%.1f dBFS".format(it) } ?: "--",
                    White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.mains_strength_note),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 12.sp,
                )
            }

            // ── 告警横幅 ──────────────────────────────────────
            if (state.alert) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Danger.copy(alpha = 0.14f),
                    border = BorderStroke(1.5.dp, Danger.copy(alpha = 0.75f)),
                ) {
                    Text(
                        stringResource(R.string.mains_alert_live_wire),
                        fontFamily = Sans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Danger,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            // ── 历史火花线 ────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.mains_panel_history, MainsWireDetector.HISTORY_SIZE)) {
                if (state.history.size < 2) {
                    EmptyHint(stringResource(R.string.mains_empty_history))
                } else {
                    val history = state.history
                    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                        // 5 条等分水平网格
                        for (i in 0..4) {
                            val y = size.height * i / 4f
                            drawLine(
                                Gray.copy(alpha = 0.12f),
                                Offset(0f, y),
                                Offset(size.width, y),
                                0.7f,
                            )
                        }
                        val stepX = size.width / (history.size - 1).coerceAtLeast(1)
                        val pts = history.mapIndexed { i, v ->
                            Offset(
                                i * stepX,
                                size.height * (1f - v.coerceIn(0, 100) / 100f),
                            )
                        }
                        val path = Path().apply {
                            pts.forEachIndexed { i, p ->
                                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                            }
                        }
                        // 磷光层 + 主线
                        drawPath(path, levelColor.copy(alpha = 0.22f), style = Stroke(width = 5f))
                        drawPath(path, levelColor, style = Stroke(width = 1.4f))
                        val last = pts.last()
                        drawCircle(levelColor.copy(alpha = 0.30f), 6f, last)
                        drawCircle(White.copy(alpha = 0.9f), 2f, last)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "MIN ${history.minOrNull() ?: 0}",
                            fontFamily = Mono,
                            fontSize = 8.sp,
                            color = Gray,
                        )
                        Text(
                            stringResource(R.string.mains_current_level, state.level),
                            fontFamily = Mono,
                            fontSize = 8.sp,
                            color = levelColor,
                        )
                        Text(
                            "MAX ${history.maxOrNull() ?: 0}",
                            fontFamily = Mono,
                            fontSize = 8.sp,
                            color = Gray,
                        )
                    }
                }
            }

            // ── 使用说明 ──────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.mains_panel_how_to)) {
                MainsBullet(stringResource(R.string.mains_bullet_baseline))
                MainsBullet(stringResource(R.string.mains_bullet_move))
                MainsBullet(stringResource(R.string.mains_bullet_load))
                MainsBullet(stringResource(R.string.mains_bullet_crosscheck))
                MainsBullet(stringResource(R.string.mains_bullet_grip))
            }

            // ── 原理与边界 ────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.mains_panel_limits)) {
                Text(
                    stringResource(R.string.mains_limit_detects),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = White,
                    lineHeight = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.mains_limit_mic),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.mains_limit_no_load),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Warn,
                    lineHeight = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.mains_limit_raw),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 12.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 说明条目 —— 菱形箭头 + 正文,保持终端 HUD 的排版节奏 */
@Composable
private fun MainsBullet(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("▸", fontFamily = Mono, fontSize = 9.sp, color = Gold)
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontFamily = Sans,
            fontSize = 9.sp,
            color = White,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
