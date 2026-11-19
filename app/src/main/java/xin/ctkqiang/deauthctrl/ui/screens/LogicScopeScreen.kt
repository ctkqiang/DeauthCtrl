package xin.ctkqiang.deauthctrl.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.IrRemoteManager
import xin.ctkqiang.deauthctrl.manager.SCOPE_CAPACITY
import xin.ctkqiang.deauthctrl.manager.SCOPE_MAX_CHANNELS
import xin.ctkqiang.deauthctrl.manager.SCOPE_WINDOW_MS
import xin.ctkqiang.deauthctrl.manager.ScopeChannel
import xin.ctkqiang.deauthctrl.manager.ScopePoint
import xin.ctkqiang.deauthctrl.manager.ScopeState
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.drawTacticalCorners
import xin.ctkqiang.deauthctrl.viewmodel.LogicScopeViewModel

/**
 * 逻辑示波 —— 多通道射频 / 传感器波形
 *
 * ## 画面
 * 300dp 主 Canvas：8×6 示波器网格、10 秒滚动窗口、每通道一条轨迹。
 * BLE / WiFi / 蜂窝 / 红外按采样保持画成方波脉冲，音频画成模拟包络曲线。
 * 左上角图例与通道颜色一一对应：BLE=TacGreen、WiFi=Red、蜂窝=Gold、
 * 音频=White、红外=Warn。
 *
 * ## 数据来源（全部为真实系统返回值）
 * - BLE：BleScannerManager 的设备表快照，事件时刻 = 系统广播回调 lastSeen；
 * - WiFi：WifiManager.startScan + scanResults 的最强 AP RSSI；
 * - 蜂窝：GsmTowerManager 注册小区 RSRP / RSSI；
 * - 音频：AudioRecord 16kHz 单声道，每 100ms 帧的 RMS 包络；
 * - 红外：IrRemoteManager.transmitRaw 的脉冲图案经 submitIrPattern 注入。
 *
 * ## 诚实边界
 * 脉冲通道显示的是系统上报的扫描事件时刻与信号强度，不是空口物理波形；
 * 音频通道 16kHz 带宽、10Hz 包络；示波器语义是工程调试可视化。
 * 没有数据时显示「--」，绝不插值或编造。
 */
@Composable
fun LogicScopeScreen(back: () -> Unit) {
    val vm: LogicScopeViewModel = viewModel()
    // 红外通道:订阅 IrRemoteManager 最近一次真实发射的脉冲序列
    val irContext = LocalContext.current
    val irManager = remember { IrRemoteManager(irContext) }
    val lastIr by irManager.lastPattern.collectAsState()
    LaunchedEffect(lastIr) {
        lastIr?.let { pattern ->
            vm.submitIrPattern(
                pattern.toList(),
                irContext.getString(R.string.lscope_ir_pattern_segments, pattern.size),
            )
        }
    }

    val state by vm.state.collectAsState()
    val selected by vm.selected.collectAsState()

    // 进入页面即按当前选择启动采集；离开时无条件停止，避免麦克风 / BLE 扫描驻留后台
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.shutdown() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.lscope_title), back)
        Column(
            Modifier.fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            ScopeWarningStrip(state)

            // ── 通道选择 ──────────────────────────────────────────────────
            TermPanel(stringResource(R.string.lscope_channels_panel)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ScopeChannel.values().forEach { channel ->
                        ScopeChannelChip(
                            channel = channel,
                            selected = channel.id in selected,
                            locked = state.running,
                        ) { vm.toggleChannel(channel.id) }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    if (state.running) {
                        stringResource(R.string.lscope_channels_locked)
                    } else {
                        stringResource(R.string.lscope_channels_multi_hint, SCOPE_MAX_CHANNELS)
                    },
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = if (state.running) Warn else Gray,
                    lineHeight = 11.sp,
                )
            }

            Spacer(Modifier.height(8.dp))

            ActionButton(
                running = state.running,
                runLabel = stringResource(R.string.lscope_start_capture),
                stopLabel = stringResource(R.string.lscope_stop_capture),
            ) {
                if (state.running) vm.stop() else vm.start()
            }

            Spacer(Modifier.height(8.dp))

            // ── 主示波器 ──────────────────────────────────────────────────
            TermPanel(stringResource(R.string.lscope_waveform_panel)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Dark.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, BorderDim),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        ScopeCanvas(state, selected, Modifier.fillMaxSize())
                        ScopeLegend(
                            state = state,
                            selected = selected,
                            modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
                        )
                        Row(
                            Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "-${SCOPE_WINDOW_MS / 1000} s",
                                fontFamily = Mono, fontSize = 8.sp, color = Gray,
                            )
                            Text(stringResource(R.string.lscope_now), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.lscope_window_info, SCOPE_WINDOW_MS / 1000),
                        fontFamily = Mono, fontSize = 8.sp, color = Gray,
                    )
                    Text(
                        stringResource(R.string.lscope_capacity_points, SCOPE_CAPACITY),
                        fontFamily = Mono, fontSize = 8.sp, color = Gray,
                    )
                    Text(
                        stringResource(
                            R.string.lscope_points_cached,
                            state.channels.values.sumOf { it.size },
                        ),
                        fontFamily = Mono, fontSize = 8.sp, color = Gray,
                    )
                }
                if (state.running && state.channels.values.all { it.isEmpty() }) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(R.string.lscope_waiting_first_sample),
                        fontFamily = Sans, fontSize = 8.sp, color = Gray,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 通道读数 ──────────────────────────────────────────────────
            TermPanel(stringResource(R.string.lscope_readout_panel)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            R.string.lscope_active_channels,
                            state.activeChannels.size,
                            selected.size,
                        ),
                        fontFamily = Sans, fontSize = 9.sp, color = Gray,
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton(stringResource(R.string.lscope_clear_trace)) { vm.clear() }
                }
                Spacer(Modifier.height(5.dp))
                val shown = ScopeChannel.values().filter { it.id in selected }
                shown.forEachIndexed { index, channel ->
                    ScopeReadoutRow(channel, state)
                    if (index != shown.lastIndex) Spacer(Modifier.height(5.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            ScopeHonestyPanel()

            Spacer(Modifier.height(10.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 通道颜色与选择
// ══════════════════════════════════════════════════════════════════════════

/** 通道配色：与图例、轨迹、读数一一对应，只用于区分通道 */
private fun scopeColor(channel: ScopeChannel): Color = when (channel) {
    ScopeChannel.BLE -> TacGreen
    ScopeChannel.WIFI -> Red
    ScopeChannel.CELL -> Gold
    ScopeChannel.AUDIO -> White
    ScopeChannel.IR -> Warn
}

/** 通道显示名资源：由 ScopeChannel.labelRes 提供，界面语言切换时同步更新 */
private fun scopeChannelLabelRes(channel: ScopeChannel): Int = channel.labelRes

/**
 * 通道芯片
 *
 * 选中 = 通道色底 + 通道色边 + 白字；未选中 = 透明底 + 暗边 + 灰字。
 * 运行中仅降低未选中的对比度（选择本身被 ViewModel 锁定），不做假禁用。
 */
@Composable
private fun ScopeChannelChip(
    channel: ScopeChannel,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val color = scopeColor(channel)
    val contentColor = when {
        selected -> White
        locked -> Gray.copy(alpha = 0.55f)
        else -> Gray
    }
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = if (selected) color.copy(alpha = 0.14f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) color.copy(alpha = 0.75f) else BorderDim),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Star(if (selected) color else Gray.copy(alpha = if (locked) 0.5f else 1f), 6.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(scopeChannelLabelRes(channel)),
                fontFamily = Sans,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 示波器 Canvas
// ══════════════════════════════════════════════════════════════════════════

/** 左上角图例：只列出当前选择中的通道；有数据的通道全亮，无数据的通道压暗 */
@Composable
private fun ScopeLegend(
    state: ScopeState,
    selected: Set<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ScopeChannel.values().forEach { channel ->
            if (channel.id !in selected) return@forEach
            val hasData = state.channels[channel.id]?.isNotEmpty() == true
            Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Star(
                    if (hasData) scopeColor(channel) else scopeColor(channel).copy(alpha = 0.35f),
                    6.dp,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    stringResource(scopeChannelLabelRes(channel)),
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = if (hasData) scopeColor(channel) else Gray.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/**
 * 主示波器画布
 *
 * - 网格：8 列 × 6 行，50% 幅度处一条虚线参考；
 * - 时间轴：最新时刻贴右边缘，窗口 10 秒，10Hz 状态刷新推动轨迹滚动；
 * - 脉冲通道：相邻事件之间「先保持电平、再跳变」画成方波；
 * - 音频通道：相邻 RMS 点直接连线，形成模拟包络曲线；
 * - 越窗的点不画，未来时间戳（红外回放尚未到达的边沿）也不画。
 */
@Composable
private fun ScopeCanvas(
    state: ScopeState,
    selected: Set<String>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // 示波器网格：8 列 × 6 行
        for (col in 0..8) {
            val x = w * col / 8f
            drawLine(BorderDim.copy(alpha = 0.30f), Offset(x, 0f), Offset(x, h), 1f)
        }
        for (row in 0..6) {
            val y = h * row / 6f
            drawLine(BorderDim.copy(alpha = 0.30f), Offset(0f, y), Offset(w, y), 1f)
        }

        // 50% 幅度参考虚线
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
        drawLine(
            BorderDim.copy(alpha = 0.55f),
            Offset(0f, h / 2f),
            Offset(w, h / 2f),
            1f,
            pathEffect = dash,
        )

        val now = System.currentTimeMillis()
        val windowStart = now - SCOPE_WINDOW_MS
        val pad = 4.dp.toPx()
        val usable = (h - pad * 2f).coerceAtLeast(1f)
        val windowMs = SCOPE_WINDOW_MS.toFloat()

        fun xOf(timeMs: Long): Float = w * (1f - (now - timeMs).toFloat() / windowMs)
        fun yOf(value01: Float): Float = h - pad - value01.coerceIn(0f, 1f) * usable

        // 音频模拟曲线最后画，压在最上层；其余通道为方波脉冲
        val drawOrder = ScopeChannel.values().filter { it != ScopeChannel.AUDIO } + ScopeChannel.AUDIO
        for (channel in drawOrder) {
            if (channel.id !in selected) continue
            val points = state.channels[channel.id].orEmpty()
            if (points.isEmpty()) continue

            val firstVisible = points.indexOfFirst { it.timeMs >= windowStart }
            // 全部点都滚出窗口：不再绘制
            if (firstVisible < 0 && points.last().timeMs < windowStart) continue
            // 额外带上前一个窗口外样本，保证左边缘的保持电平连续
            val from = if (firstVisible > 0) firstVisible - 1 else 0
            val color = scopeColor(channel)
            val stroke = 1.5.dp.toPx()

            if (channel == ScopeChannel.AUDIO) {
                for (i in (from + 1) until points.size) {
                    val a = points[i - 1]
                    val b = points[i]
                    if (b.timeMs > now) continue
                    drawLine(
                        color,
                        Offset(xOf(a.timeMs), yOf(a.value01)),
                        Offset(xOf(b.timeMs), yOf(b.value01)),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            } else {
                for (i in (from + 1) until points.size) {
                    val a = points[i - 1]
                    val b = points[i]
                    if (b.timeMs > now) continue
                    val x1 = xOf(a.timeMs)
                    val x2 = xOf(b.timeMs)
                    val y1 = yOf(a.value01)
                    val y2 = yOf(b.value01)
                    // 采样保持：先保持 a 的电平，再在 b 时刻跳变到新电平
                    drawLine(color, Offset(x1, y1), Offset(x2, y1), stroke)
                    drawLine(color, Offset(x2, y1), Offset(x2, y2), stroke)
                }
            }

            val latest = points.lastOrNull { it.timeMs in windowStart..now }
            if (latest != null) {
                drawCircle(color, 2.dp.toPx(), Offset(xOf(latest.timeMs), yOf(latest.value01)))
            }
        }

        drawTacticalCorners(TacGreen.copy(alpha = 0.20f), size, 9.dp.toPx(), 1.dp.toPx())
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 读数与提示
// ══════════════════════════════════════════════════════════════════════════

/** 单通道读数：最新归一化幅度、还原工程值、来源标签、事件计数与运行状态 */
@Composable
private fun ScopeReadoutRow(channel: ScopeChannel, state: ScopeState) {
    val points = state.channels[channel.id].orEmpty()
    val latest = points.lastOrNull()
    val color = scopeColor(channel)
    val active = state.running && channel.id in state.activeChannels
    val context = LocalContext.current
    val channelLabel = stringResource(scopeChannelLabelRes(channel))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = SurfaceAlt.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, BorderDim.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Star(color, 7.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    channelLabel,
                    fontFamily = Sans,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        active -> stringResource(R.string.lscope_state_capturing)
                        state.running -> stringResource(R.string.lscope_state_idle)
                        else -> stringResource(R.string.lscope_state_stopped)
                    },
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = if (active) TacGreen else Gray,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.lscope_event_count, state.eventCounts[channel.id] ?: 0L),
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = Gray,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    latest?.let { "%.0f%%".format(it.value01 * 100f) } ?: "--",
                    fontFamily = Mono,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (latest != null) color else Gray,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (latest != null) {
                        stringResource(
                            R.string.lscope_readout_value,
                            rawValueText(context, channel, latest),
                            latest.label ?: stringResource(R.string.lscope_no_label),
                            elapsedText(context, System.currentTimeMillis() - latest.timeMs),
                        )
                    } else {
                        stringResource(R.string.lscope_no_sample_waiting, channelLabel)
                    },
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 11.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 归一化幅度按通道公式还原为工程读数（反向映射，用于人工核对；量程边界不伪造精确值） */
private fun rawValueText(context: Context, channel: ScopeChannel, point: ScopePoint): String = when (channel) {
    ScopeChannel.BLE, ScopeChannel.WIFI -> when {
        point.value01 <= 0f -> "≤ -100 dBm"
        point.value01 >= 1f -> "≥ -20 dBm"
        else -> "%.0f dBm".format(point.value01 * 80f - 100f)
    }
    ScopeChannel.CELL -> when {
        point.value01 <= 0f -> "≤ -140 dBm"
        point.value01 >= 1f -> "≥ -40 dBm"
        else -> "%.0f dBm".format(point.value01 * 100f - 140f)
    }
    ScopeChannel.AUDIO -> when {
        point.value01 <= 0f -> "≤ -80 dBFS"
        point.value01 >= 1f -> "≥ 0 dBFS"
        else -> "%.0f dBFS".format(point.value01 * 80f - 80f)
    }
    ScopeChannel.IR -> if (point.value01 > 0.5f) {
        context.getString(R.string.lscope_ir_carrier)
    } else {
        context.getString(R.string.lscope_ir_gap)
    }
}

/** 距最新样本的时长；仅用于「这读数有多新」，不参与任何计算 */
private fun elapsedText(context: Context, ageMs: Long): String = when {
    ageMs < 0L -> context.getString(R.string.lscope_age_just_now)
    ageMs < 1_000L -> context.getString(R.string.lscope_age_ms_ago, ageMs)
    ageMs < 60_000L -> context.getString(
        R.string.lscope_age_sec_ago,
        "%.1f".format(ageMs / 1000f),
    )
    else -> context.getString(R.string.lscope_age_min_ago, ageMs / 60_000L)
}

/**
 * 告警条
 *
 * 致命错误（没选通道）用 Danger；通道级提示（权限缺失、扫描节流、音频占用等）
 * 用 Warn，并标注所属通道，避免把所有问题揉成一句无法定位的「失败」。
 */
@Composable
private fun ScopeWarningStrip(state: ScopeState) {
    val items = mutableListOf<Pair<Color, String>>()

    val fatal = state.error?.trim().orEmpty()
    if (fatal.isNotEmpty()) items += Danger to fatal
    ScopeChannel.values().forEach { channel ->
        val note = state.channelNotes[channel.id]?.trim().orEmpty()
        if (note.isNotEmpty()) {
            val label = stringResource(scopeChannelLabelRes(channel))
            items += Warn to stringResource(R.string.lscope_channel_note, label, note)
        }
    }

    items.forEach { (color, text) ->
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = color.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        ) {
            Text(
                stringResource(R.string.lscope_warning_prefix, text),
                fontFamily = Sans,
                fontSize = 10.sp,
                color = color,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 诚实说明
// ══════════════════════════════════════════════════════════════════════════

/** 诚实说明 —— 指出「事件脉冲 ≠ 空口波形」「RMS 包络 ≠ 原始音频」等语义边界 */
@Composable
private fun ScopeHonestyPanel() {
    TermPanel(stringResource(R.string.lscope_honesty_panel)) {
        Text(
            stringResource(R.string.lscope_honesty_p1),
            fontFamily = Sans,
            fontSize = 9.sp,
            color = Gray,
            lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.lscope_honesty_p2),
            fontFamily = Mono,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.lscope_honesty_p3),
            fontFamily = Sans,
            fontSize = 9.sp,
            color = Gray,
            lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.lscope_honesty_p4),
            fontFamily = Sans,
            fontSize = 9.sp,
            color = Gray,
            lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.lscope_honesty_p5),
            fontFamily = Sans,
            fontSize = 9.sp,
            color = Gray,
            lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.lscope_honesty_p6),
            fontFamily = Sans,
            fontSize = 9.sp,
            color = Gray.copy(alpha = 0.85f),
            lineHeight = 13.sp,
        )
    }
}
