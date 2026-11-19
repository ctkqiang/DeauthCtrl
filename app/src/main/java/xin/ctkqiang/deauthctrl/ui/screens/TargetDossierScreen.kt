package xin.ctkqiang.deauthctrl.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.DeviceIntel
import xin.ctkqiang.deauthctrl.manager.RadarTarget
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.BearingEstimate
import xin.ctkqiang.deauthctrl.viewmodel.LocState
import xin.ctkqiang.deauthctrl.viewmodel.RadarViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 目标档案页 —— 单台设备全量技术资料(v3)
 *
 * 区块顺序:身份 → 快照横幅 → 实时遥测 → 多图表(RSSI/距离/命中频率)
 * → 定位推演(玫瑰图/调制谱/位置图) → 射频详情(WiFi 或 BLE 广播) → 时间轴
 * → 战术评估 → 数据来源声明。
 *
 * 数据纪律:全部取自真实扫描结果;派生量(RSSI 距离/方位/底噪)逐项标注估计,
 * 拿不到的字段显示「--」,绝不编造。
 */
@Composable
fun TargetDossierScreen(vm: RadarViewModel, targetId: String, back: () -> Unit) {
    // 单目标实时流:内容去重,避免整页 2Hz 空重组
    val target by remember(targetId) { vm.targetFlow(targetId) }.collectAsState(
        initial = vm.targets.value.firstOrNull { it.id == targetId },
    )
    val running by vm.isRunning.collectAsState()

    // 保持罗盘可读:方位推演与玫瑰图需要航向(离开即释放)
    DisposableEffect(Unit) {
        vm.acquireCompass()
        onDispose { vm.releaseCompass() }
    }

    // 页面级持有扫描:档案页必须近实时 —— 从雷达页进入时扫描不断流,
    // 直接进入(如网络拓扑 → 档案)时自动拉起;离开由引用计数决定是否停止
    DisposableEffect(Unit) {
        vm.acquireScan()
        onDispose { vm.releaseScan() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.dossier_title), back)
        if (target == null) {
            EmptyHint(stringResource(R.string.dossier_target_lost))
        } else {
            val t = target!!
            Column(
                Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
            ) {
                IdentityBlock(t)
                Spacer(Modifier.height(8.dp))
                SnapshotBanner(t, running)
                Spacer(Modifier.height(8.dp))
                SignalTelemetry(t)
                Spacer(Modifier.height(8.dp))
                DossierChartsPanel(t)
                Spacer(Modifier.height(8.dp))
                LocalizationInferencePanel(vm, t, back)
                Spacer(Modifier.height(8.dp))
                if (t.type == "wifi") WifiRfBlock(t) else { BleAdvPanel(t); Spacer(Modifier.height(8.dp)); BtBlock(t) }
                Spacer(Modifier.height(8.dp))
                TemporalBlock(t)
                Spacer(Modifier.height(8.dp))
                ThreatAssessment(t)
                Spacer(Modifier.height(8.dp))
                ProvenanceFooter(t)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** 身份块 —— 类型徽章 + 名称 + 关键标识 */
@Composable
private fun IdentityBlock(t: RadarTarget) {
    // BLE 广播观测 → BLE 徽章;仅经典发现 → BT 徽章
    val badge = when {
        t.type == "wifi" -> "WIFI" to Warn
        t.observedVia == "ble" -> "BLE" to TacGreen
        else -> "BT" to Gold
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, badge.second.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                    color = badge.second.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, badge.second.copy(alpha = 0.7f)),
                ) {
                    Text(
                        badge.first,
                        fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = badge.second, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    t.name.take(22),
                    fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = White, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Star(Gold, 6.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (t.type == "wifi") t.id else t.address,
                    fontFamily = Mono, fontSize = 10.sp, color = Gold,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Star(Gold, 6.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.dossier_vendor_line, t.vendor, t.vendorOui),
                    fontFamily = Mono, fontSize = 10.sp, color = Gray,
                )
            }
            // 设备情报指纹:黑客工具/开发板/物联网识别(启发式,可能误报)
            DeviceIntel.classify(t.name, t.id, t.bleAdv?.serviceUuids.orEmpty(), t.bleAdv?.manufacturerId)?.let { hit ->
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Star(if (hit.category == "黑客工具") Danger else Gold, 6.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.dossier_intel_line, hit.label, (hit.confidence * 100).toInt()),
                        fontFamily = Mono, fontSize = 10.sp,
                        color = if (hit.category == "黑客工具") Danger else Gold,
                    )
                }
                Text(
                    stringResource(R.string.dossier_intel_basis, hit.basis),
                    fontFamily = Sans, fontSize = 8.sp, color = Gray,
                    modifier = Modifier.padding(start = 12.dp, top = 1.dp),
                )
            }
            if (t.type == "bt" && t.alias.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Star(Gold, 6.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dossier_alias, t.alias), fontFamily = Mono, fontSize = 10.sp, color = Gray)
                }
            }
        }
    }
}

/** 快照横幅 —— 雷达运行态说明数据实时性 */
@Composable
private fun SnapshotBanner(t: RadarTarget, running: Boolean) {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    val stamp = fmt.format(Date(t.lastSeenMs))
    Surface(
        Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
        color = if (running) TacGreen.copy(alpha = 0.08f) else Gold.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (running) TacGreen.copy(alpha = 0.5f) else Gold.copy(alpha = 0.5f)),
    ) {
        Text(
            if (running) stringResource(R.string.dossier_live_line, stamp)
            else stringResource(R.string.dossier_stopped_line, stamp),
            fontFamily = Mono, fontSize = 9.sp,
            color = if (running) TacGreen else Gold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** 实时遥测块 —— RSSI 大读数 + 信号条 + 距离/方位/SNR + 趋势箭头 */
@Composable
private fun SignalTelemetry(t: RadarTarget) {
    TermPanel(stringResource(R.string.dossier_panel_telemetry)) {
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = t.rssi,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "rssi",
            ) { r ->
                Text("${r}dBm", fontFamily = Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = rssiColor(t.rssi))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.padding(bottom = 4.dp)) {
                Text("SNR ${t.snrDb}dB", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                // 趋势箭头:RSSI 最近 10 样本线性斜率
                val slope = rssiSlope(t.history)
                val (label, color) = when {
                    slope > 0.5f -> stringResource(R.string.dossier_trend_near, slope) to TacGreen
                    slope < -0.5f -> stringResource(R.string.dossier_trend_away, slope) to Warn
                    else -> stringResource(R.string.dossier_trend_stable) to Gray
                }
                Text(label, fontFamily = Mono, fontSize = 8.sp, color = color)
            }
        }
        Spacer(Modifier.height(6.dp))
        // 信号强度条：-100dBm → -30dBm 映射 0-100%
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp)).background(SurfaceAlt),
        ) {
            Box(
                Modifier.fillMaxWidth(((t.rssi + 100f) / 70f).coerceIn(0.03f, 1f)).height(8.dp)
                    .clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp))
                    .background(rssiColor(t.rssi)),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TelemetryCell(stringResource(R.string.dossier_cell_distance), stringResource(R.string.dossier_value_distance, t.distanceM))
            TelemetryCell(stringResource(R.string.dossier_cell_bearing), stringResource(R.string.dossier_value_bearing, t.angleDeg.toInt()))
            TelemetryCell(stringResource(R.string.dossier_cell_noise), stringResource(R.string.dossier_value_noise, t.noiseDbm))
            TelemetryCell(stringResource(R.string.dossier_cell_hits), stringResource(R.string.dossier_value_hits, t.hitCount))
        }
        val ptx = t.txPowerDbm?.let { stringResource(R.string.dossier_ptx_broadcast, it) } ?: stringResource(R.string.dossier_ptx_assumed)
        Text(
            stringResource(R.string.dossier_telemetry_note, ptx),
            fontFamily = Sans, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TelemetryCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacGreen)
        Text(label, fontFamily = Sans, fontSize = 8.sp, color = Gray)
    }
}

/** RSSI 最近样本线性斜率(dB/s)—— 趋势箭头数据源 */
private fun rssiSlope(history: List<xin.ctkqiang.deauthctrl.manager.RssiSample>): Float {
    val pts = history.takeLast(10)
    if (pts.size < 5) return 0f
    val n = pts.size
    val meanX = pts.indices.average()
    val meanY = pts.map { it.rssi }.average()
    var num = 0.0; var den = 0.0
    pts.forEachIndexed { i, s ->
        num += (i - meanX) * (s.rssi - meanY)
        den += (i - meanX) * (i - meanX)
    }
    if (den <= 0.0) return 0f
    val perSample = (num / den)
    // 采样周期约 3s(WiFi)/不定(BLE),这里按 3s 折算为 dB/s 量级
    return (perSample / 3.0).toFloat()
}

/** 多图表面板 —— RSSI 趋势 / 距离趋势 / 命中频率 */
@Composable
private fun DossierChartsPanel(t: RadarTarget) {
    val history = t.history
    TrendChart(
        title = stringResource(R.string.dossier_chart_rssi_title, history.size),
        values = history.map { it.rssi.toFloat() },
        lo = -100f, hi = -30f, unit = "dBm",
        color = rssiColor(t.rssi),
        labels = history,
    )
    Spacer(Modifier.height(8.dp))
    TrendChart(
        title = stringResource(R.string.dossier_chart_distance_title),
        values = history.map { rssiDistanceOf(it.rssi, t) },
        lo = 0f, hi = 100f, unit = "m",
        color = Gold,
        labels = history,
    )
    Spacer(Modifier.height(8.dp))
    HitBucketsChart(t)
}

/** 按目标的距离模型(广播 PTX 或假定值)换算单样本距离 */
private fun rssiDistanceOf(rssi: Int, t: RadarTarget): Float {
    val ref = t.txPowerDbm?.toFloat() ?: if (t.type == "wifi") -40f else -59f
    return xin.ctkqiang.deauthctrl.manager.rssiDistanceEstimateM(rssi, ref)
}

/** 通用趋势折线图 —— 网格 + min/avg/max + 磷光线 + 末点呼吸光点 */
@Composable
private fun TrendChart(
    title: String,
    values: List<Float>,
    lo: Float,
    hi: Float,
    unit: String,
    color: Color,
    labels: List<xin.ctkqiang.deauthctrl.manager.RssiSample>,
) {
    TermPanel(title) {
        if (values.size < 2) {
            Text(stringResource(R.string.dossier_chart_accumulating_rssi), fontFamily = Sans, fontSize = 10.sp, color = Gray)
        } else {
            val stats = remember(values) {
                Triple(values.minOrNull() ?: 0f, values.average().toFloat(), values.maxOrNull() ?: 0f)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MIN %.0f$unit".format(stats.first), fontFamily = Mono, fontSize = 9.sp, color = Gray)
                Text("AVG %.0f$unit".format(stats.second), fontFamily = Mono, fontSize = 9.sp, color = Gold)
                Text("MAX %.0f$unit".format(stats.third), fontFamily = Mono, fontSize = 9.sp, color = Gray)
            }
            Spacer(Modifier.height(6.dp))
            val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                fun yOf(v: Float): Float = size.height * (1f - (v - lo) / (hi - lo))
                val stepX = size.width / (values.size - 1).coerceAtLeast(1)

                // 网格：5 等分横线
                for (i in 0..4) {
                    val v = lo + (hi - lo) * i / 4f
                    val y = yOf(v)
                    drawLine(Gray.copy(alpha = 0.15f), Offset(0f, y), Offset(size.width, y), 0.7f)
                }
                // 中线略亮
                drawLine(Gold.copy(alpha = 0.2f), Offset(0f, yOf((lo + hi) / 2f)), Offset(size.width, yOf((lo + hi) / 2f)), 0.9f)

                val pts = values.mapIndexed { i, v -> Offset(i * stepX, yOf(v)) }

                // 磷光层：粗低透明度
                val glowPath = Path().apply {
                    pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                }
                drawPath(glowPath, color.copy(alpha = 0.22f), style = Stroke(width = 5f))
                // 主线：细高亮
                drawPath(glowPath, color, style = Stroke(width = 1.4f))

                // 末点呼吸光点
                val last = pts.last()
                drawCircle(color.copy(alpha = 0.25f), 7f, last)
                drawCircle(White.copy(alpha = 0.9f), 2.2f, last)

                // 时间轴首末标签
                val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
                val m0 = textMeasurer.measure(fmt.format(Date(labels.first().timeMs)), TextStyle(fontFamily = Mono, fontSize = 8.sp, color = Gray))
                val m1 = textMeasurer.measure(fmt.format(Date(labels.last().timeMs)), TextStyle(fontFamily = Mono, fontSize = 8.sp, color = Gray))
                drawText(m0, topLeft = Offset(0f, size.height - m0.size.height))
                drawText(m1, topLeft = Offset(size.width - m1.size.width, size.height - m1.size.height))
            }
        }
    }
}

/** 命中频率柱状图 —— 最近 10 分钟每分钟命中数 */
@Composable
private fun HitBucketsChart(t: RadarTarget) {
    TermPanel(stringResource(R.string.dossier_panel_hits_chart)) {
        val buckets = t.hitBuckets
        if (buckets.isEmpty()) {
            Text(stringResource(R.string.dossier_chart_accumulating_hits), fontFamily = Sans, fontSize = 10.sp, color = Gray)
        } else {
            val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
            val currentHitsLabel = stringResource(R.string.dossier_hits_per_min, buckets.last())
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                val maxHits = (buckets.maxOrNull() ?: 1).coerceAtLeast(1)
                val barW = size.width / buckets.size
                buckets.forEachIndexed { i, hits ->
                    val h = (hits.toFloat() / maxHits) * (size.height - 16.dp.toPx())
                    val x = i * barW
                    drawRect(
                        if (i == buckets.size - 1) TacGreen.copy(alpha = 0.85f) else TacGreen.copy(alpha = 0.35f),
                        topLeft = Offset(x + barW * 0.2f, size.height - h),
                        size = Size(barW * 0.6f, h),
                    )
                    drawLine(Gray.copy(alpha = 0.1f), Offset(0f, size.height - h), Offset(size.width, size.height - h), 0.5f)
                }
                // 首末分钟标签
                val fmt = SimpleDateFormat("mm分", Locale.US)
                val first = textMeasurer.measure(fmt.format(Date(t.hitBucketStartMs)), TextStyle(fontFamily = Mono, fontSize = 7.sp, color = Gray))
                val lastL = textMeasurer.measure(fmt.format(Date(t.hitBucketStartMs + (buckets.size - 1) * 60_000L)), TextStyle(fontFamily = Mono, fontSize = 7.sp, color = Gray))
                drawText(first, topLeft = Offset(0f, size.height - first.size.height))
                drawText(lastL, topLeft = Offset(size.width - lastL.size.width, size.height - lastL.size.height))
                // 当前分钟值
                val cur = textMeasurer.measure(currentHitsLabel, TextStyle(fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TacGreen))
                drawText(cur, topLeft = Offset(size.width - cur.size.width, 0f))
            }
        }
    }
}

/**
 * 定位推演面板 —— 旋转寻向的完整数据视图
 *
 * 方法:旋转机身,记录航向×RSSI 方向图并取功率峰 —— 属天线方向性推演,
 * 不是 AoA/测向;距离由 RSSI 路径损耗模型给出。所有数值都是估计。
 */
@Composable
private fun LocalizationInferencePanel(vm: RadarViewModel, t: RadarTarget, back: () -> Unit) {
    val loc by vm.localizations.collectAsState()
    val heading by vm.heading.collectAsState()
    val compassOn by vm.compassOnline.collectAsState()
    val e = loc[t.id]

    TermPanel(stringResource(R.string.dossier_panel_bearing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (chipLabel, chipColor) = when (e?.state) {
                LocState.ESTIMATED -> stringResource(R.string.dossier_loc_estimated) to TacGreen
                LocState.COLLECTING -> stringResource(R.string.dossier_loc_collecting) to Gold
                LocState.AMBIGUOUS -> stringResource(R.string.dossier_loc_ambiguous) to Warn
                LocState.STALE -> stringResource(R.string.dossier_loc_stale) to Gray
                else -> stringResource(R.string.dossier_loc_unavailable) to Gray
            }
            Surface(
                shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                color = chipColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, chipColor.copy(alpha = 0.6f)),
            ) {
                Text(chipLabel, fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = chipColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (!compassOn) stringResource(R.string.dossier_compass_offline) else stringResource(R.string.dossier_rotational_only),
                fontFamily = Sans, fontSize = 8.sp, color = Gray,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))

        if (e == null) {
            Text(
                stringResource(R.string.dossier_bearing_empty),
                fontFamily = Sans, fontSize = 10.sp, color = Gray, lineHeight = 15.sp,
            )
            if (t.type == "wifi") {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.dossier_wifi_throttle_note),
                    fontFamily = Sans, fontSize = 9.sp, color = Warn,
                )
            }
        } else {
            // 置信条
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CutCornerShape(topStart = 2.dp, bottomEnd = 2.dp)).background(SurfaceAlt)) {
                Box(
                    Modifier.fillMaxWidth(e.confidence.coerceIn(0f, 1f)).height(5.dp)
                        .clip(CutCornerShape(topStart = 2.dp, bottomEnd = 2.dp))
                        .background(if (e.state == LocState.ESTIMATED) TacGreen else Gold),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (e.state == LocState.COLLECTING) {
                Text(
                    stringResource(R.string.dossier_coverage_status, (e.coverage * 36).toInt(), e.rotations),
                    fontFamily = Sans, fontSize = 9.sp, color = Gold,
                )
                Spacer(Modifier.height(6.dp))
            }
            InfoRow(
                stringResource(R.string.dossier_info_samples),
                stringResource(R.string.dossier_info_samples_value, e.sampleCount, (e.coverage * 36).toInt(), e.rotations),
            )
            InfoRow(
                stringResource(R.string.dossier_info_peak_shape),
                stringResource(R.string.dossier_info_peak_value, e.contrastDb, e.fwhmDeg.toInt(), e.secondLobeRatio, e.confidence),
            )
            if (!e.distanceM.isNaN()) {
                InfoRow(
                    stringResource(R.string.dossier_info_distance_est),
                    stringResource(R.string.dossier_info_distance_value, e.distanceM, e.sigmaDistanceM),
                )
                InfoRow(
                    stringResource(R.string.dossier_info_distance_model),
                    if (e.distanceModelAssumed) stringResource(R.string.dossier_distance_model_assumed)
                    else stringResource(R.string.dossier_distance_model_broadcast),
                )
            }
            if (e.sweepCount > 0 && !e.eastM.isNaN()) {
                InfoRow(
                    stringResource(R.string.dossier_info_position_fusion),
                    stringResource(R.string.dossier_info_position_value, e.eastM, e.northM, e.sweepCount),
                )
                InfoRow(
                    stringResource(R.string.dossier_info_confidence_ellipse),
                    stringResource(
                        R.string.dossier_info_ellipse_value,
                        e.ellipseSemiMajorM * 1.1774f / 2.1459f, e.ellipseSemiMinorM * 1.1774f / 2.1459f,
                        e.ellipseSemiMajorM, e.ellipseSemiMinorM,
                    ),
                )
            }
            if (e.state == LocState.STALE) {
                InfoRow(
                    stringResource(R.string.dossier_info_last_sample),
                    stringResource(R.string.dossier_info_last_sample_value, e.ageMs / 1000),
                )
            }
            Spacer(Modifier.height(8.dp))
            BearingRoseChart(e, heading)
            Spacer(Modifier.height(8.dp))
            HeadingRssiProfileChart(e)
            Spacer(Modifier.height(8.dp))
            PositionFixPlot(e)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.dossier_method_note),
            fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 13.sp,
        )
    }
}

/** 方位玫瑰图 —— 极坐标方向图,北朝上 */
@Composable
private fun BearingRoseChart(e: BearingEstimate, heading: Float) {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val north = stringResource(R.string.dossier_dir_north)
    val east = stringResource(R.string.dossier_dir_east)
    val south = stringResource(R.string.dossier_dir_south)
    val west = stringResource(R.string.dossier_dir_west)
    val bearingTag = stringResource(R.string.dossier_bearing_tag, e.bearingDeg.toInt(), e.sectorHalfWidthDeg.toInt())
    val roseNote = stringResource(R.string.dossier_rose_note)
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy) - 20.dp.toPx()
        val lo = (e.profileDb.filterIndexed { i, _ -> e.profileValid[i] }.minOrNull() ?: -100f) - 5f
        val hi = (e.profileDb.filterIndexed { i, _ -> e.profileValid[i] }.maxOrNull() ?: -30f) + 3f
        fun radOf(v: Float): Float = radius * (0.15f + 0.85f * ((v - lo) / (hi - lo).coerceAtLeast(0.01f)))

        // 极网格 3 圈
        for (s in listOf(0.33f, 0.66f, 1f)) {
            drawCircle(Gray.copy(alpha = 0.18f), radius * s, Offset(cx, cy), style = Stroke(0.7f))
        }
        // 方位字:北朝上,屏幕角 = compassDeg - 90
        listOf(north to 0f, east to 90f, south to 180f, west to 270f).forEach { (ch, deg) ->
            val a = Math.toRadians((deg - 90).toDouble()).toFloat()
            val m = textMeasurer.measure(ch, TextStyle(fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gray.copy(alpha = 0.8f)))
            drawText(m, topLeft = Offset(cx + radius * 1.06f * cos(a) - m.size.width / 2f, cy + radius * 1.06f * sin(a) - m.size.height / 2f))
        }
        // 各有效分箱楔形
        for (b in 0 until e.profileDb.size) {
            if (!e.profileValid[b]) continue
            val cDeg = b * 10f + 5f
            val a = Math.toRadians((cDeg - 90).toDouble()).toFloat()
            val rr = radOf(e.profileDb[b])
            val isPeak = abs(((cDeg - e.bearingDeg + 540f) % 360f) - 180f) <= 5f && !e.bearingDeg.isNaN()
            drawLine(
                (if (isPeak) Gold else TacGreen).copy(alpha = if (isPeak) 0.5f else 0.18f),
                Offset(cx, cy),
                Offset(cx + rr * cos(a), cy + rr * sin(a)),
                4f,
            )
        }
        // 估计方位射线
        if (!e.bearingDeg.isNaN()) {
            val a = Math.toRadians((e.bearingDeg - 90).toDouble()).toFloat()
            drawLine(Gold.copy(alpha = 0.9f), Offset(cx, cy), Offset(cx + radius * cos(a), cy + radius * sin(a)), 1.6f)
            val tag = textMeasurer.measure(
                bearingTag,
                TextStyle(fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold),
            )
            drawText(tag, topLeft = Offset(cx - tag.size.width / 2f, cy - radius - 12.dp.toPx()))
        }
        // 机头方向小三角
        val hA = Math.toRadians((heading - 90).toDouble()).toFloat()
        val tri = Path().apply {
            moveTo(cx + radius * 0.12f * cos(hA), cy + radius * 0.12f * sin(hA))
            lineTo(cx + 5f * cos(hA + PI.toFloat() / 2), cy + 5f * sin(hA + PI.toFloat() / 2))
            lineTo(cx + 5f * cos(hA - PI.toFloat() / 2), cy + 5f * sin(hA - PI.toFloat() / 2))
            close()
        }
        drawPath(tri, White.copy(alpha = 0.9f))
        // 底注
        val note = textMeasurer.measure(roseNote, TextStyle(fontFamily = Mono, fontSize = 7.sp, color = Gray))
        drawText(note, topLeft = Offset(cx - note.size.width / 2f, size.height - note.size.height))
    }
}

/** 航向×RSSI 调制谱 —— 直角坐标分箱散点 + 峰带 */
@Composable
private fun HeadingRssiProfileChart(e: BearingEstimate) {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val headingProfileNote = stringResource(R.string.dossier_heading_profile_note)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        fun xOf(deg: Float): Float = size.width * (deg / 360f)
        fun yOf(db: Float): Float = size.height * (1f - (db + 100f) / 70f)
        // 网格
        for (db in listOf(-90, -70, -50, -30)) {
            drawLine(Gray.copy(alpha = 0.15f), Offset(0f, yOf(db.toFloat())), Offset(size.width, yOf(db.toFloat())), 0.7f)
        }
        for (deg in listOf(0f, 90f, 180f, 270f, 360f)) {
            drawLine(Gray.copy(alpha = 0.12f), Offset(xOf(deg), 0f), Offset(xOf(deg), size.height), 0.7f)
        }
        // FWHM 竖带
        if (!e.bearingDeg.isNaN() && e.fwhmDeg > 0f) {
            val l = (e.bearingDeg - e.fwhmDeg / 2f + 360f) % 360f
            val rr = (e.bearingDeg + e.fwhmDeg / 2f) % 360f
            drawRect(Gold.copy(alpha = 0.10f), topLeft = Offset(xOf(l), 0f), size = Size(((rr - l + 360f) % 360f) / 360f * size.width, size.height))
        }
        // 分箱点:大小随样本数
        for (b in 0 until e.profileDb.size) {
            if (!e.profileValid[b]) continue
            val cDeg = b * 10f + 5f
            val r = 1.5.dp.toPx() + 0.5.dp.toPx() * minOf(e.binCounts[b], 6)
            drawCircle(TacGreen.copy(alpha = 0.85f), r, Offset(xOf(cDeg), yOf(e.profileDb[b])))
        }
        // 峰值虚线
        if (!e.bearingDeg.isNaN()) {
            drawLine(Gold.copy(alpha = 0.6f), Offset(xOf(e.bearingDeg), 0f), Offset(xOf(e.bearingDeg), size.height), 1f)
            val tag = textMeasurer.measure("θ̂", TextStyle(fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold))
            drawText(tag, topLeft = Offset(xOf(e.bearingDeg) + 2f, 0f))
        }
        val note = textMeasurer.measure(headingProfileNote, TextStyle(fontFamily = Mono, fontSize = 7.sp, color = Gray))
        drawText(note, topLeft = Offset(0f, size.height - note.size.height))
    }
}

/** 位置融合图 —— 俯视直角坐标,中心=本机,格距 25m */
@Composable
private fun PositionFixPlot(e: BearingEstimate) {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val fusionNotReady = stringResource(R.string.dossier_fusion_not_ready)
    val positionNote = stringResource(R.string.dossier_position_note)
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy) - 16.dp.toPx()
        val step = radius / 4f
        // 垫子格 25m
        for (i in -4..4) {
            drawLine(Gray.copy(alpha = 0.10f), Offset(cx + i * step, cy - radius), Offset(cx + i * step, cy + radius), 0.6f)
            drawLine(Gray.copy(alpha = 0.10f), Offset(cx - radius, cy + i * step), Offset(cx + radius, cy + i * step), 0.6f)
        }
        // 距离环 25/50/75/100
        for (s in listOf(0.25f, 0.5f, 0.75f, 1f)) {
            drawCircle(Gray.copy(alpha = 0.2f), radius * s, Offset(cx, cy), style = Stroke(0.7f))
        }
        // 北向箭头
        drawLine(White.copy(alpha = 0.6f), Offset(cx, cy - radius * 0.12f), Offset(cx, cy - radius * 0.98f), 1.4f)
        val arrow = Path().apply { moveTo(cx, cy - radius); lineTo(cx - 5f, cy - radius + 8f); lineTo(cx + 5f, cy - radius + 8f); close() }
        drawPath(arrow, White.copy(alpha = 0.6f))

        if (!e.eastM.isNaN() && !e.northM.isNaN()) {
            val px = cx + (e.eastM / 100f * radius)
            val py = cy - (e.northM / 100f * radius)
            // 方位扇形(浅金)
            val aDeg = Math.toDegrees(atan2(e.eastM, e.northM).toDouble()).toFloat()
            drawArc(
                Gold.copy(alpha = 0.06f),
                startAngle = aDeg - 90f - e.sectorHalfWidthDeg,
                sweepAngle = e.sectorHalfWidthDeg * 2f,
                useCenter = true,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
            )
            // 融合点金十字
            val cross = 7.dp.toPx()
            drawLine(Gold.copy(alpha = 0.95f), Offset(px - cross, py), Offset(px + cross, py), 1.8f)
            drawLine(Gold.copy(alpha = 0.95f), Offset(px, py - cross), Offset(px, py + cross), 1.8f)
            drawCircle(Gold.copy(alpha = 0.5f), 4.dp.toPx(), Offset(px, py))
            // 置信椭圆
            val aSemi = (e.ellipseSemiMajorM / 100f * radius).coerceAtLeast(3f)
            val bSemi = (e.ellipseSemiMinorM / 100f * radius).coerceAtLeast(3f)
            withTransform({
                translate(px, py)
                rotate(Math.toRadians(-e.ellipseAngleDeg.toDouble()).toFloat(), pivot = Offset.Zero)
            }) {
                drawOval(Gold.copy(alpha = 0.5f), topLeft = Offset(-aSemi, -bSemi), size = Size(aSemi * 2f, bSemi * 2f), style = Stroke(1.2f))
            }
            // d±σ 虚线弧
            if (!e.distanceM.isNaN()) {
                val dIn = ((e.distanceM - e.sigmaDistanceM) / 100f * radius).coerceAtLeast(0f)
                val dOut = ((e.distanceM + e.sigmaDistanceM) / 100f * radius).coerceAtMost(radius)
                listOf(dIn, dOut).forEach { rr ->
                    drawCircle(Gold.copy(alpha = 0.3f), rr, Offset(cx, cy), style = Stroke(1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                }
            }
        } else {
            val note = textMeasurer.measure(fusionNotReady, TextStyle(fontFamily = Sans, fontSize = 9.sp, color = Gray))
            drawText(note, topLeft = Offset(cx - note.size.width / 2f, cy))
        }
        val note2 = textMeasurer.measure(positionNote, TextStyle(fontFamily = Mono, fontSize = 7.sp, color = Gray))
        drawText(note2, topLeft = Offset(cx - note2.size.width / 2f, size.height - note2.size.height))
    }
}

/** BLE 广播详情面板 —— 真实 ScanRecord 字段,缺失标「—」 */
@Composable
private fun BleAdvPanel(t: RadarTarget) {
    val adv = t.bleAdv
    TermPanel(stringResource(R.string.dossier_panel_ble_adv)) {
        if (adv == null || !adv.hasRecord) {
            Text(stringResource(R.string.dossier_ble_no_adv), fontFamily = Sans, fontSize = 10.sp, color = Gray)
        } else {
            InfoRow(stringResource(R.string.dossier_info_observed_via), if (t.observedVia == "ble") stringResource(R.string.dossier_observed_ble_scan) else stringResource(R.string.dossier_observed_classic_merged))
            InfoRow(stringResource(R.string.dossier_info_tx_power), if (adv.txPowerLevelDbm != Int.MIN_VALUE) "${adv.txPowerLevelDbm} dBm" else stringResource(R.string.dossier_value_not_broadcast))
            InfoRow(stringResource(R.string.dossier_info_adv_flags), if (adv.advFlags >= 0) "0x%02X".format(adv.advFlags) else stringResource(R.string.dossier_value_not_broadcast))
            InfoRow(stringResource(R.string.dossier_info_manufacturer_id), if (adv.manufacturerId >= 0) "0x%04X".format(adv.manufacturerId) else "—")
            if (adv.serviceUuids.isNotEmpty()) {
                InfoRow(stringResource(R.string.dossier_info_service_uuid), stringResource(R.string.dossier_value_uuid_count, adv.serviceUuids.size))
                adv.serviceUuids.take(8).forEach { u ->
                    Text("   · $u", fontFamily = Mono, fontSize = 8.sp, color = White)
                }
                if (adv.serviceUuids.size > 8) Text("   · " + stringResource(R.string.dossier_uuid_more, adv.serviceUuids.size - 8), fontFamily = Mono, fontSize = 8.sp, color = Gray)
            }
            adv.manufacturerData.entries.take(4).forEach { e ->
                InfoRow(stringResource(R.string.dossier_info_manufacturer_data), "0x%04X · ${e.value}".format(e.key))
            }
            adv.serviceData.entries.take(4).forEach { e ->
                InfoRow(stringResource(R.string.dossier_info_service_data), "${e.key} · ${e.value}")
            }
            Text(
                stringResource(R.string.dossier_ble_adv_footnote),
                fontFamily = Sans, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** WiFi 射频与安全块 */
@Composable
private fun WifiRfBlock(t: RadarTarget) {
    TermPanel(stringResource(R.string.dossier_panel_rf)) {
        InfoRow("BSSID", t.id)
        InfoRow(stringResource(R.string.dossier_info_frequency), if (t.frequencyMhz > 0) "${t.frequencyMhz} MHz" else "--")
        InfoRow(stringResource(R.string.dossier_info_channel), t.channel.ifEmpty { "--" })
        InfoRow(stringResource(R.string.dossier_info_bandwidth), t.channelWidth.ifEmpty { "--" })
        InfoRow(stringResource(R.string.dossier_info_center_freq0), if (t.centerFreq0Mhz > 0) "${t.centerFreq0Mhz} MHz" else "--")
        InfoRow(stringResource(R.string.dossier_info_center_freq1), if (t.centerFreq1Mhz > 0) "${t.centerFreq1Mhz} MHz" else "--")
    }
    Spacer(Modifier.height(8.dp))
    TermPanel(stringResource(R.string.dossier_panel_security)) {
        InfoRow(stringResource(R.string.dossier_info_encryption), securityDisplay(t.security))
        InfoRow("capabilities", t.capabilitiesRaw.ifEmpty { "--" })
    }
}

/** 蓝牙参数块 */
@Composable
private fun BtBlock(t: RadarTarget) {
    TermPanel(stringResource(R.string.dossier_panel_bt)) {
        InfoRow(stringResource(R.string.dossier_info_mac_address), t.address)
        InfoRow(stringResource(R.string.dossier_info_device_class_major), t.deviceClassMajor.ifEmpty { "--" })
        InfoRow(stringResource(R.string.dossier_info_device_class_detail), t.deviceClassDetail.ifEmpty { "--" })
        InfoRow(stringResource(R.string.dossier_info_link_type), t.linkType.ifEmpty { "--" })
        InfoRow(stringResource(R.string.dossier_info_bond_state), t.bondState.ifEmpty { "--" })
        InfoRow(stringResource(R.string.dossier_info_band), t.channel)
    }
}

/** 时间轴块 */
@Composable
private fun TemporalBlock(t: RadarTarget) {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    TermPanel(stringResource(R.string.dossier_panel_timeline)) {
        InfoRow(stringResource(R.string.dossier_info_first_seen), fmt.format(Date(t.firstSeenMs)))
        InfoRow(stringResource(R.string.dossier_info_last_seen), fmt.format(Date(t.lastSeenMs)))
        InfoRow(stringResource(R.string.dossier_info_duration), formatDuration(LocalContext.current, t.lastSeenMs - t.firstSeenMs))
    }
}

/** 战术评估块 —— 与雷达页共用同一套威胁分级推演标准 */
@Composable
private fun ThreatAssessment(t: RadarTarget) {
    val level = radarThreatTier(t)
    TermPanel(stringResource(R.string.dossier_panel_threat)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Star(level.color, 8.dp)
            Spacer(Modifier.width(8.dp))
            Text(level.label, fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = level.color)
        }
        Spacer(Modifier.height(6.dp))
        Text(level.desc, fontFamily = Sans, fontSize = 10.sp, color = Gray, lineHeight = 15.sp)
        if (t.type == "wifi" && (t.security.startsWith("开放") || t.security.startsWith("WEP"))) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.dossier_warn_weak_encryption, securityDisplay(t.security)),
                fontFamily = Sans, fontSize = 10.sp, color = Warn, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 数据来源声明 —— 每个字段的真实出处与估计标注 */
@Composable
private fun ProvenanceFooter(t: RadarTarget) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, BorderDim),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(stringResource(R.string.dossier_panel_provenance), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (t.type == "wifi")
                    stringResource(R.string.dossier_provenance_wifi)
                else
                    stringResource(R.string.dossier_provenance_bt),
                fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 13.sp,
            )
        }
    }
}

/** 档案行 —— 等宽键值对 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontFamily = Mono, fontSize = 10.sp, color = Gray, modifier = Modifier.width(84.dp))
        Text(value, fontFamily = Mono, fontSize = 10.sp, color = White, modifier = Modifier.weight(1f))
    }
}

/** 安全类型显示映射 —— 数据值("开放" / "WEP"...)按界面语言显示,逻辑比较仍用数据值 */
@Composable
private fun securityDisplay(security: String): String = when {
    security.startsWith("开放") -> stringResource(R.string.dossier_sec_open)
    else -> security.ifEmpty { "--" }
}

private fun rssiColor(rssi: Int): Color = when {
    rssi > -50 -> Danger
    rssi > -70 -> Warn
    else -> TacGreen
}

private fun formatDuration(context: Context, ms: Long): String = when {
    ms < 1000 -> context.getString(R.string.dossier_duration_under_sec)
    ms < 60_000 -> context.getString(R.string.dossier_duration_seconds, ms / 1000)
    else -> context.getString(R.string.dossier_duration_minutes, ms / 60_000, ms % 60_000 / 1000)
}
