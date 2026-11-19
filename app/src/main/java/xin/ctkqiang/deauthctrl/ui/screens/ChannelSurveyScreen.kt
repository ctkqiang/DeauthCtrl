package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.ChanAp
import xin.ctkqiang.deauthctrl.manager.ChannelSurvey
import xin.ctkqiang.deauthctrl.manager.CongestionLevel
import xin.ctkqiang.deauthctrl.manager.WifiBand
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.PanelStat
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.ChannelSurveyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WiFi 信道占用热力图 —— 被动扫描的信道拥堵分析
 *
 * 数据链路:ChannelSurveyManager 每 3 秒触发一次系统扫描,把 scanResults
 * 按信道聚合为 2.4 GHz(1-14)与 5 GHz(仅出现的常用信道)两组。柱条高度 =
 * 该信道可见 AP 数;颜色按拥堵分级:0-2 空闲 / 3-5 拥挤 / 6+ 严重;
 * 点选任意信道展开该信道的 AP 明细(SSID / RSSI / 安全类型)。
 *
 * 数据诚实性:所有数值来自 WifiManager.scanResults 的真实扫描快照,不做
 * 插值、模拟或美化;无数据处显示 "--"。只能看到主动广播 Beacon 的 AP,
 * 隐藏 SSID 不计入;信道占用是快照,不是实时频谱。
 */
@Composable
fun ChannelSurveyScreen(back: () -> Unit) {
    val vm: ChannelSurveyViewModel = viewModel()
    val state by vm.state.collectAsState()
    var selectedChannel by remember { mutableStateOf<Int?>(null) }

    // 进入页面即开始巡检;离开页面停止扫描循环
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val updatedText = if (state.updatedAtMs > 0L) {
        timeFmt.format(Date(state.updatedAtMs))
    } else {
        "--:--:--"
    }

    val band24 = state.bands.firstOrNull { it.band == WifiBand.BAND_24 }
    val band5 = state.bands.firstOrNull { it.band == WifiBand.BAND_5 }
    val totalAps = state.bands.sumOf { it.apTotal }
    val selected24 = band24?.channels?.firstOrNull { it.channel == selectedChannel }
    val selected5 = band5?.channels?.firstOrNull { it.channel == selectedChannel }
    val toggleSelection: (Int) -> Unit = { ch ->
        selectedChannel = if (selectedChannel == ch) null else ch
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.chsurvey_title), back)

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            // ── 顶部状态条:扫描状态 / 可见 AP 总数 / 更新时间 ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.scanning) stringResource(R.string.chsurvey_status_scanning)
                        else stringResource(R.string.chsurvey_status_stopped),
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.scanning) TacGreen else Gray,
                    )
                    Spacer(Modifier.width(9.dp))
                    Box(Modifier.width(1.dp).height(11.dp).background(BorderDim))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        stringResource(R.string.chsurvey_visible_aps, totalAps),
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        color = Gold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.chsurvey_updated, updatedText),
                        fontFamily = Mono,
                        fontSize = 9.sp,
                        color = Gray,
                    )
                }
            }

            // ── 系统节流提示:数据仍是真实扫描结果,只是可能滞后数秒 ──
            if (state.scanning && state.throttled) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                    color = Warn.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Warn.copy(alpha = 0.45f)),
                ) {
                    Text(
                        stringResource(R.string.chsurvey_throttled),
                        fontFamily = Sans,
                        fontSize = 9.sp,
                        color = Warn,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }

            // ── 权限 / 定位 / Wi-Fi 开关 / SecurityException 告警 ──
            state.error?.let { err ->
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                    color = Danger.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
                ) {
                    Text(
                        "[!] $err",
                        fontFamily = Sans,
                        fontSize = 9.sp,
                        color = Danger,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }

            // ── 扫描控制 ──
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.chsurvey_panel_control)) {
                PanelStat(
                    stringResource(R.string.chsurvey_stat_state),
                    if (state.scanning) stringResource(R.string.chsurvey_running)
                    else stringResource(R.string.chsurvey_status_stopped),
                    if (state.scanning) TacGreen else Gray,
                )
                PanelStat(stringResource(R.string.chsurvey_stat_interval), "3000 ms", White)
                PanelStat(stringResource(R.string.chsurvey_stat_last_update), updatedText, Gold)
                Spacer(Modifier.height(7.dp))
                ActionButton(
                    running = state.scanning,
                    runLabel = stringResource(R.string.chsurvey_btn_start),
                    stopLabel = stringResource(R.string.chsurvey_btn_stop),
                ) {
                    if (state.scanning) vm.stop() else vm.start()
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.chsurvey_passive_note),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray.copy(alpha = 0.9f),
                    lineHeight = 12.sp,
                )
            }

            // ── 2.4 GHz 信道柱状图 ──
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.chsurvey_panel_24)) {
                val channels24 = band24?.channels.orEmpty()
                if (channels24.isEmpty()) {
                    EmptyHint(
                        when {
                            state.error != null -> stringResource(R.string.chsurvey_empty_no_data)
                            !state.scanning -> stringResource(R.string.chsurvey_empty_stopped)
                            else -> stringResource(R.string.chsurvey_empty_waiting)
                        },
                    )
                } else {
                    ChannelChart24(
                        channels = channels24,
                        selected = selectedChannel,
                        onSelect = toggleSelection,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LegendDot(TacGreen, stringResource(R.string.chsurvey_legend_idle))
                        LegendDot(Warn, stringResource(R.string.chsurvey_legend_crowded))
                        LegendDot(Danger, stringResource(R.string.chsurvey_legend_severe))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.chsurvey_chart_note),
                        fontFamily = Sans,
                        fontSize = 8.sp,
                        color = Gray,
                        lineHeight = 12.sp,
                    )
                    Text(
                        stringResource(R.string.chsurvey_click_hint),
                        fontFamily = Sans,
                        fontSize = 8.sp,
                        color = Gray,
                        lineHeight = 12.sp,
                    )
                    selected24?.let { ChannelDetailPanel(it) }
                }
            }

            // ── 5 GHz 信道条 ──
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.chsurvey_panel_5)) {
                val channels5 = band5?.channels.orEmpty()
                if (channels5.isEmpty()) {
                    EmptyHint(
                        when {
                            band5 == null && !state.scanning ->
                                stringResource(R.string.chsurvey_5_empty_stopped)
                            band5 == null -> stringResource(R.string.chsurvey_empty_waiting)
                            else -> stringResource(R.string.chsurvey_5_empty_none)
                        },
                    )
                } else {
                    Band5List(
                        channels = channels5,
                        selected = selectedChannel,
                        onSelect = toggleSelection,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        stringResource(R.string.chsurvey_5_note),
                        fontFamily = Sans,
                        fontSize = 8.sp,
                        color = Gray,
                        lineHeight = 12.sp,
                    )
                    selected5?.let { ChannelDetailPanel(it) }
                }
            }

            // ── 数据说明与诚实边界 ──
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Star(Warn, 9.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.chsurvey_panel_data_notes),
                            fontFamily = Sans,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Warn,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        stringResource(R.string.chsurvey_note1),
                        stringResource(R.string.chsurvey_note2),
                        stringResource(R.string.chsurvey_note3),
                        stringResource(R.string.chsurvey_note4),
                        stringResource(R.string.chsurvey_note5),
                        stringResource(R.string.chsurvey_note6),
                        stringResource(R.string.chsurvey_note7),
                    ).forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp)) {
                            Text("·", fontFamily = Mono, fontSize = 8.sp, color = Gray)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                line,
                                fontFamily = Sans,
                                fontSize = 8.sp,
                                color = Gray,
                                lineHeight = 12.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ─── 2.4 GHz 柱状图 Canvas ───────────────────────────────────────────────────

/**
 * 2.4 GHz 信道柱状图
 *
 * 横轴固定 1-14 信道,纵轴为可见 AP 数(按本轮最大值归一)。
 * 每根柱条顶部的数字是该信道 AP 数,柱条下方是信道号与最强 RSSI。
 * 1 / 6 / 11 的信道号用金色标识(20 MHz 下互不重叠)。
 * 点击任意横向槽位选中/取消该信道,由外层展开 AP 明细。
 */
@Composable
private fun ChannelChart24(
    channels: List<ChannelSurvey>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier
            .fillMaxWidth()
            .height(176.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val slotWidth = size.width / 14f
                    if (slotWidth <= 0f) return@detectTapGestures
                    val index = (offset.x / slotWidth).toInt().coerceIn(0, 13)
                    onSelect(index + 1)
                }
            },
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val byChannel = channels.associateBy { it.channel }
        val maxAp = (channels.maxOfOrNull { it.apCount } ?: 0).coerceAtLeast(1)
        val slot = size.width / 14f
        val countArea = 14.dp.toPx()
        val labelArea = 28.dp.toPx()
        val plotTop = countArea
        val plotBottom = (size.height - labelArea).coerceAtLeast(plotTop + 1f)
        val barWidth = slot * 0.56f
        val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())

        for (ch in 1..14) {
            val data = byChannel[ch]
            val count = data?.apCount ?: 0
            val left = (ch - 1) * slot
            val barLeft = left + (slot - barWidth) / 2f
            val color = congestionColor(data?.congestion ?: CongestionLevel.IDLE)

            // 选中信道:整槽淡青底 + 金色描边
            if (selected == ch) {
                drawRect(
                    color = Red.copy(alpha = 0.08f),
                    topLeft = Offset(left, 0f),
                    size = Size(slot, size.height),
                )
                drawRect(
                    color = Gold.copy(alpha = 0.55f),
                    topLeft = Offset(left + 0.5.dp.toPx(), 0.5.dp.toPx()),
                    size = Size(slot - 1.dp.toPx(), size.height - 1.dp.toPx()),
                    style = Stroke(1.dp.toPx()),
                )
            }

            // 信道基线
            drawLine(
                color = BorderDim,
                start = Offset(left + 2.dp.toPx(), plotBottom),
                end = Offset(left + slot - 2.dp.toPx(), plotBottom),
                strokeWidth = 1.dp.toPx(),
            )

            // 柱条:高度 = 该信道 AP 数 / 本轮最大 AP 数
            val barHeight = if (count > 0) {
                ((plotBottom - plotTop) * count.toFloat() / maxAp)
                    .coerceAtLeast(3.dp.toPx())
            } else {
                0f
            }
            if (count > 0) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(barLeft, plotBottom - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            }

            // AP 数标注(0 也用灰色标在基线上方,保持逐信道可读)
            val countStyle = TextStyle(
                fontFamily = Mono,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (count > 0) color else Gray.copy(alpha = 0.55f),
            )
            val countText = textMeasurer.measure(count.toString(), countStyle)
            val countTop = if (count > 0) {
                (plotBottom - barHeight - countText.size.height - 1.dp.toPx()).coerceAtLeast(0f)
            } else {
                plotBottom - countText.size.height - 2.dp.toPx()
            }
            drawText(
                countText,
                topLeft = Offset(left + (slot - countText.size.width) / 2f, countTop),
            )

            // 信道号:1 / 6 / 11 金色标识互不重叠信道
            val chStyle = TextStyle(
                fontFamily = Mono,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (ch == 1 || ch == 6 || ch == 11) Gold else Gray,
            )
            val chText = textMeasurer.measure(ch.toString(), chStyle)
            drawText(
                chText,
                topLeft = Offset(left + (slot - chText.size.width) / 2f, plotBottom + 4.dp.toPx()),
            )

            // 最强 RSSI(dBm);无 AP 显示 "--"
            val rssi = data?.strongestRssi
            val rssiStyle = TextStyle(
                fontFamily = Mono,
                fontSize = 8.sp,
                color = rssi?.let { rssiColor(it) } ?: Gray.copy(alpha = 0.55f),
            )
            val rssiText = textMeasurer.measure(rssi?.toString() ?: "--", rssiStyle)
            drawText(
                rssiText,
                topLeft = Offset(left + (slot - rssiText.size.width) / 2f, plotBottom + 16.dp.toPx()),
            )
        }
    }
}

// ─── 5 GHz 信道条 ────────────────────────────────────────────────────────────

/** 5 GHz 信道列表:只显示本次扫描出现过的信道,条长按 AP 数归一 */
@Composable
private fun Band5List(
    channels: List<ChannelSurvey>,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    val maxAp = (channels.maxOfOrNull { it.apCount } ?: 0).coerceAtLeast(1)
    channels.forEach { ch ->
        val color = congestionColor(ch.congestion)
        val isSelected = selected == ch.channel

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onSelect(ch.channel) }
                .background(if (isSelected) Red.copy(alpha = 0.08f) else Color.Transparent)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(62.dp)) {
                Text(
                    "CH ${ch.channel}",
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Gold else White,
                )
                Text(
                    "${ch.frequencyMhz} MHz",
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = Gray,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(9.dp)
                    .clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp))
                    .background(SurfaceAlt),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ch.apCount.toFloat() / maxAp)
                        .fillMaxHeight()
                        .clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp))
                        .background(color),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.width(70.dp), horizontalAlignment = Alignment.End) {
                Text(
                    "${ch.apCount} AP",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(
                    ch.strongestRssi?.let { "$it dBm" } ?: "--",
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = Gray,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(BorderDim.copy(alpha = 0.6f)))
    }
}

// ─── 信道明细 ────────────────────────────────────────────────────────────────

/** 单信道 AP 明细:最强/平均 RSSI、安全分布、逐 AP 的 SSID / BSSID / 信号 / 安全 */
@Composable
private fun ChannelDetailPanel(channel: ChannelSurvey) {
    Spacer(Modifier.height(8.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceAlt,
        border = BorderStroke(1.dp, BorderDim),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Star(Gold, 8.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(
                        R.string.chsurvey_channel_title,
                        channel.channel,
                        channel.frequencyMhz,
                    ),
                    fontFamily = Sans,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(channel.congestion.labelRes),
                    fontFamily = Sans,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = congestionColor(channel.congestion),
                )
            }
            Spacer(Modifier.height(5.dp))
            PanelStat(stringResource(R.string.chsurvey_stat_visible_aps), "${channel.apCount}")
            PanelStat(
                stringResource(R.string.chsurvey_stat_strongest_rssi),
                channel.strongestRssi?.let { "$it dBm" } ?: "--",
            )
            PanelStat(
                stringResource(R.string.chsurvey_stat_avg_rssi),
                channel.averageRssi?.let { "$it dBm" } ?: "--",
            )
            val sec = channel.securityCounts
            Text(
                stringResource(
                    R.string.chsurvey_security_dist,
                    sec.open,
                    sec.wep,
                    sec.wpa2,
                    sec.wpa3,
                    sec.other,
                ),
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Gray,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(
                stringResource(R.string.chsurvey_ap_details, channel.apCount),
                fontFamily = Sans,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Red,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            )
            if (channel.aps.isEmpty()) {
                Text(
                    stringResource(R.string.chsurvey_no_visible_ap),
                    fontFamily = Sans,
                    fontSize = 9.sp,
                    color = Gray,
                )
            } else {
                channel.aps.forEachIndexed { index, ap ->
                    if (index > 0) {
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(BorderDim))
                    }
                    ApRow(ap)
                }
            }
        }
    }
}

/** 单个 AP 明细行:SSID + RSSI,下一行 BSSID + 安全类型 */
@Composable
private fun ApRow(ap: ChanAp) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ap.ssid,
                fontFamily = Sans,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${ap.rssi} dBm",
                fontFamily = Mono,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = rssiColor(ap.rssi),
            )
        }
        Text(
            "${ap.bssid} · ${stringResource(ap.security.labelRes)}",
            fontFamily = Mono,
            fontSize = 8.sp,
            color = Gray,
        )
    }
}

// ─── 图例与小工具 ────────────────────────────────────────────────────────────

/** 图例圆点 + 中文说明 */
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, fontFamily = Sans, fontSize = 8.sp, color = Gray)
    }
}

/** 拥堵分级 → 颜色:0-2 空闲 TacGreen / 3-5 拥挤 Warn / 6+ 严重 Danger */
private fun congestionColor(level: CongestionLevel): Color = when (level) {
    CongestionLevel.IDLE -> TacGreen
    CongestionLevel.CROWDED -> Warn
    CongestionLevel.SEVERE -> Danger
}

/** RSSI 着色(仅表达强/中/弱,不改变数值本身) */
private fun rssiColor(rssi: Int): Color = when {
    rssi >= 0 -> Gray      // 系统未给出有效值(0)时按未知处理
    rssi >= -60 -> TacGreen
    rssi >= -75 -> Warn
    else -> Danger
}
