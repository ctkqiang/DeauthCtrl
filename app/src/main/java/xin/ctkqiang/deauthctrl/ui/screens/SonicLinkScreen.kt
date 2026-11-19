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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.SonicLinkManager
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.LogHeader
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.SonicLinkViewModel

/** 报文输入上限（符号数），避免一次生成过长的 PCM */
private const val SONIC_TEXT_LIMIT = 64

/**
 * 声波传讯 —— DTMF 调制解调数据链
 *
 * TX：输入 0-9 / A-D / * / # / 空格，叠成双音正弦经扬声器播放，进度来自播放头；
 * RX：麦克风连续采集，每 40ms 做 8 频点 Goertzel，连续 3 帧同符号才确认，
 * 解码文本逐行落到 received。
 *
 * 页面离开时通过 DisposableEffect 释放 AudioTrack / AudioRecord。
 */
@Composable
fun SonicLinkScreen(back: () -> Unit) {
    val vm: SonicLinkViewModel = viewModel()
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    var text by remember { mutableStateOf("") }

    // 离开页面：停止发送并释放麦克风
    DisposableEffect(Unit) {
        onDispose { vm.stopAll() }
    }

    // 发送前预览：真正会发出的符号 / 会被跳过的字符
    val sendable = remember(text) { SonicLinkManager.sanitize(text) }
    val skipped = remember(text) { SonicLinkManager.unsupportedCount(text) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.sonic_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            state.error?.let { message ->
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Danger.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
                ) {
                    Text(
                        "[!] $message",
                        fontFamily = Sans, fontSize = 9.sp, color = Danger,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            // ── 发射 ──────────────────────────────────────────────────────
            TermPanel(stringResource(R.string.sonic_panel_tx)) {
                TermInput(
                    stringResource(R.string.sonic_message_label),
                    text,
                    placeholder = stringResource(R.string.sonic_message_placeholder),
                ) { input ->
                    text = input.take(SONIC_TEXT_LIMIT)
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.sonic_sendable_count, sendable.length),
                        fontFamily = Mono, fontSize = 9.sp,
                        color = if (sendable.isEmpty()) Gray else Gold,
                    )
                    Text(
                        stringResource(R.string.sonic_skipped_count, skipped),
                        fontFamily = Mono, fontSize = 9.sp, color = Gray,
                    )
                }
                if (text.isNotEmpty() && sendable.isEmpty()) {
                    Text(
                        stringResource(R.string.sonic_no_sendable),
                        fontFamily = Sans, fontSize = 9.sp, color = Warn,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sonic_current_symbol), fontFamily = Sans, fontSize = 10.sp, color = Red)
                        Text(
                            if (state.sending) {
                                stringResource(R.string.sonic_sending, (state.sendProgress * 100).toInt())
                            } else {
                                stringResource(R.string.sonic_standby)
                            },
                            fontFamily = Mono, fontSize = 9.sp,
                            color = if (state.sending) TacGreen else Gray,
                        )
                    }
                    Surface(
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = SurfaceAlt,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Text(
                            state.currentSymbol.ifBlank { "--" },
                            fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = Gold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                if (state.sending) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().height(6.dp)
                            .clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp))
                            .background(SurfaceAlt),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(state.sendProgress.coerceIn(0f, 1f)).height(6.dp)
                                .clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp))
                                .background(TacGreen),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    running = state.sending,
                    runLabel = stringResource(R.string.sonic_btn_start_send),
                    stopLabel = stringResource(R.string.sonic_btn_stop_send),
                ) {
                    haptic.hud()
                    if (state.sending) vm.stopSending() else vm.send(text)
                }
                Text(
                    stringResource(R.string.sonic_tx_desc),
                    fontFamily = Sans, fontSize = 8.sp, color = Gray.copy(alpha = 0.85f),
                    lineHeight = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── 接收 ──────────────────────────────────────────────────────
            TermPanel(stringResource(R.string.sonic_panel_rx)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sonic_listen_status), fontFamily = Sans, fontSize = 10.sp, color = Red)
                        Text(
                            if (state.running) {
                                stringResource(R.string.sonic_listening, state.rxSymbol.ifBlank { "--" })
                            } else {
                                stringResource(R.string.sonic_not_listening)
                            },
                            fontFamily = Mono, fontSize = 9.sp,
                            color = if (state.running) TacGreen else Gray,
                        )
                    }
                    Text(
                        if (state.running) "${(state.rxLevel * 100).toInt()}%" else "--",
                        fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                // 相对电平条：32 段，越靠右越接近告警色；仅表示相对 dBFS
                val level = if (state.running) state.rxLevel.coerceIn(0f, 1f) else 0f
                Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                    val segments = 32
                    val gap = 2.dp.toPx()
                    val segW = (size.width - gap * (segments - 1)) / segments
                    for (i in 0 until segments) {
                        val active = level >= (i + 1).toFloat() / segments
                        val color = when {
                            !active -> SurfaceAlt
                            i >= segments - 4 -> Danger
                            i >= segments - 8 -> Warn
                            else -> TacGreen
                        }
                        drawRect(
                            color = color,
                            topLeft = Offset(i * (segW + gap), 0f),
                            size = Size(segW, size.height),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    running = state.running,
                    runLabel = stringResource(R.string.sonic_btn_start_rx),
                    stopLabel = stringResource(R.string.sonic_btn_stop_rx),
                ) {
                    haptic.hud()
                    if (state.running) vm.stopReceiving() else vm.startReceiving()
                }
                Spacer(Modifier.height(8.dp))
                LogHeader(stringResource(R.string.sonic_log_header), showClear = true) { vm.clearReceived() }
                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, BorderDim, CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                        .padding(10.dp),
                ) {
                    val lines = state.received.takeLast(24)
                    if (lines.isEmpty() && state.currentLine.isEmpty()) {
                        Text(
                            if (state.running) {
                                stringResource(R.string.sonic_rx_waiting)
                            } else {
                                stringResource(R.string.sonic_rx_idle)
                            },
                            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
                        )
                    } else {
                        lines.forEach { line ->
                            Text(
                                "> $line",
                                fontFamily = Mono, fontSize = 11.sp, color = White,
                                lineHeight = 15.sp,
                            )
                        }
                        if (state.currentLine.isNotEmpty()) {
                            Text(
                                "> ${state.currentLine}▊",
                                fontFamily = Mono, fontSize = 11.sp, color = Gold,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.sonic_rx_desc),
                    fontFamily = Sans, fontSize = 8.sp, color = Gray.copy(alpha = 0.85f),
                    lineHeight = 12.sp,
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── 字符表 ────────────────────────────────────────────────────
            TermPanel(stringResource(R.string.sonic_panel_chart)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.sonic_chart_axis_corner),
                        fontFamily = Sans, fontSize = 8.sp, color = Gray,
                        modifier = Modifier.width(56.dp),
                    )
                    SonicLinkManager.COL_FREQS.forEach { freq ->
                        Text(
                            "${freq.toInt()}",
                            fontFamily = Mono, fontSize = 9.sp, color = Gray,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                        )
                    }
                }
                SonicLinkManager.GRID.forEachIndexed { index, symbols ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${SonicLinkManager.ROW_FREQS[index].toInt()} Hz",
                            fontFamily = Mono, fontSize = 9.sp, color = Gray,
                            modifier = Modifier.width(56.dp),
                        )
                        symbols.forEach { symbol ->
                            Surface(
                                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                color = SurfaceBg,
                                border = BorderStroke(1.dp, BorderDim),
                            ) {
                                Text(
                                    symbol.toString(),
                                    fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    color = Gold, textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.sonic_chart_legend),
                    fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.sonic_chart_charset),
                    fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp,
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── 诚实局限 ──────────────────────────────────────────────────
            Surface(
                Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Star(Warn, 9.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.sonic_honesty_title),
                            fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Warn,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.sonic_honesty_range),
                        fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
                    )
                    Text(
                        stringResource(R.string.sonic_honesty_rate),
                        fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
                    )
                    Text(
                        stringResource(R.string.sonic_honesty_insecure),
                        fontFamily = Sans, fontSize = 9.sp, color = Danger, lineHeight = 13.sp,
                    )
                    Text(
                        stringResource(R.string.sonic_honesty_hardware),
                        fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
