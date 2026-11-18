package xin.ctkqiang.deauthctrl.ui.screens

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        TerminalHeader("目标档案 · TARGET DOSSIER", back)
        if (target == null) {
            EmptyHint("目标已丢失 —— 设备离开探测范围或雷达已停止")
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
                    "厂商: ${t.vendor} · OUI ${t.vendorOui}",
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
                        "设备情报: ${hit.label} · 置信 ${(hit.confidence * 100).toInt()}%",
                        fontFamily = Mono, fontSize = 10.sp,
                        color = if (hit.category == "黑客工具") Danger else Gold,
                    )
                }
                Text(
                    "依据: ${hit.basis}",
                    fontFamily = Sans, fontSize = 8.sp, color = Gray,
                    modifier = Modifier.padding(start = 12.dp, top = 1.dp),
                )
            }
            if (t.type == "bt" && t.alias.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Star(Gold, 6.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("别名: ${t.alias}", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                }
            }
        }
    }
}

/** 快照横幅 —— 雷达运行态说明数据实时性 */
@Composable
private fun SnapshotBanner(t: RadarTarget, running: Boolean) {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    Surface(
        Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
        color = if (running) TacGreen.copy(alpha = 0.08f) else Gold.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (running) TacGreen.copy(alpha = 0.5f) else Gold.copy(alpha = 0.5f)),
    ) {
        Text(
            if (running) "● 实时 · 最后更新 ${fmt.format(Date(t.lastSeenMs))}"
            else "◌ 雷达已停止 · 以下为 ${fmt.format(Date(t.lastSeenMs))} 快照;返回雷达页可继续采集",
            fontFamily = Mono, fontSize = 9.sp,
            color = if (running) TacGreen else Gold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** 实时遥测块 —— RSSI 大读数 + 信号条 + 距离/方位/SNR + 趋势箭头 */
@Composable
private fun SignalTelemetry(t: RadarTarget) {
    TermPanel("实时遥测 · LIVE TELEMETRY") {
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
                    slope > 0.5f -> "接近 ↑ %+.1f dB/s".format(slope) to TacGreen
                    slope < -0.5f -> "远离 ↓ %+.1f dB/s".format(slope) to Warn
                    else -> "稳定 → ±0.5 dB/s" to Gray
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
            TelemetryCell("距离(估)", "~%.1f m".format(t.distanceM))
            TelemetryCell("方位(估)", "~%03d°".format(t.angleDeg.toInt()))
            TelemetryCell("底噪(估)", "%ddBm".format(t.noiseDbm))
            TelemetryCell("命中", "%d 次".format(t.hitCount))
        }
        Text(
            "~ 为路径损耗估算(${if (t.txPowerDbm != null) "广播 PTX ${t.txPowerDbm}dBm" else "假定 PTX"})· 方位为 MAC 哈希估计 · 底噪为 -92dBm 常量假设",
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
        title = "信号历史 · RSSI TREND (${history.size} 样本)",
        values = history.map { it.rssi.toFloat() },
        lo = -100f, hi = -30f, unit = "dBm",
        color = rssiColor(t.rssi),
        labels = history,
    )
    Spacer(Modifier.height(8.dp))
    TrendChart(
        title = "距离趋势 · DISTANCE TREND",
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
            Text("样本积累中 —— 每个扫描周期追加一个采样点", fontFamily = Sans, fontSize = 10.sp, color = Gray)
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
    TermPanel("命中频率 · HITS PER MINUTE (最近 10 分钟)") {
        val buckets = t.hitBuckets
        if (buckets.isEmpty()) {
            Text("样本积累中 —— 每次扫描命中计入当前分钟桶", fontFamily = Sans, fontSize = 10.sp, color = Gray)
        } else {
            val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
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
                val cur = textMeasurer.measure("${buckets.last()} 次/分", TextStyle(fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TacGreen))
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

    TermPanel("定位推演 · BEARING INFERENCE") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (chipLabel, chipColor) = when (e?.state) {
                LocState.ESTIMATED -> "估计" to TacGreen
                LocState.COLLECTING -> "采样中" to Gold
                LocState.AMBIGUOUS -> "多径歧义" to Warn
                LocState.STALE -> "过期" to Gray
                else -> "不可用" to Gray
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
                if (!compassOn) "罗盘离线" else "旋转寻向 · 非 AoA",
                fontFamily = Sans, fontSize = 8.sp, color = Gray,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))

        if (e == null) {
            Text(
                "尚未进行方位推演 —— 在雷达页聚焦该目标并缓慢旋转机身一整圈以上",
                fontFamily = Sans, fontSize = 10.sp, color = Gray, lineHeight = 15.sp,
            )
            if (t.type == "wifi") {
                Spacer(Modifier.height(4.dp))
                Text(
                    "WiFi 扫描受系统节流,样本率低,可能长期处于采样中;BLE 设备更适合推演",
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
                    "已覆盖 %d/36 方位格 · 已旋转 %.1f 圈 · 至少 0.75 圈且覆盖 45%%".format((e.coverage * 36).toInt(), e.rotations),
                    fontFamily = Sans, fontSize = 9.sp, color = Gold,
                )
                Spacer(Modifier.height(6.dp))
            }
            InfoRow("样本", "%d · 覆盖 %d/36 · 圈 %.1f".format(e.sampleCount, (e.coverage * 36).toInt(), e.rotations))
            InfoRow("峰形", "对比 %.1f dB · FWHM %d° · 双瓣比 %.2f · 置信 %.2f".format(e.contrastDb, e.fwhmDeg.toInt(), e.secondLobeRatio, e.confidence))
            if (!e.distanceM.isNaN()) {
                InfoRow("距离估计", "%.1f m · 1σ ±%.1f m".format(e.distanceM, e.sigmaDistanceM))
                InfoRow("距离模型", if (e.distanceModelAssumed) "假定 PTX · n=2.5(非测量)" else "广播 PTX · n=2.5")
            }
            if (e.sweepCount > 0 && !e.eastM.isNaN()) {
                InfoRow("位置融合", "东 %.1f m · 北 %.1f m · %d 圈样本".format(e.eastM, e.northM, e.sweepCount))
                InfoRow("置信椭圆", "50%% %.1f×%.1f m · 90%% %.1f×%.1f m".format(
                    e.ellipseSemiMajorM * 1.1774f / 2.1459f, e.ellipseSemiMinorM * 1.1774f / 2.1459f,
                    e.ellipseSemiMajorM, e.ellipseSemiMinorM,
                ))
            }
            if (e.state == LocState.STALE) {
                InfoRow("最后采样", "%ds 前".format(e.ageMs / 1000))
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
            "方法:旋转机身,记录航向×RSSI 方向图并取功率峰 —— 属天线方向性推演,不是 AoA/测向;" +
                "距离由 RSSI 路径损耗模型给出;多径反射会造成双峰歧义。所有输出均为估计,误差随样本增多收敛。",
            fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 13.sp,
        )
    }
}

/** 方位玫瑰图 —— 极坐标方向图,北朝上 */
@Composable
private fun BearingRoseChart(e: BearingEstimate, heading: Float) {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val R = minOf(cx, cy) - 20.dp.toPx()
        val lo = (e.profileDb.filterIndexed { i, _ -> e.profileValid[i] }.minOrNull() ?: -100f) - 5f
        val hi = (e.profileDb.filterIndexed { i, _ -> e.profileValid[i] }.maxOrNull() ?: -30f) + 3f
        fun radOf(v: Float): Float = R * (0.15f + 0.85f * ((v - lo) / (hi - lo).coerceAtLeast(0.01f)))

        // 极网格 3 圈
        for (s in listOf(0.33f, 0.66f, 1f)) {
            drawCircle(Gray.copy(alpha = 0.18f), R * s, Offset(cx, cy), style = Stroke(0.7f))
        }
        // 方位字:北朝上,屏幕角 = compassDeg - 90
        listOf("北" to 0f, "东" to 90f, "南" to 180f, "西" to 270f).forEach { (ch, deg) ->
            val a = Math.toRadians((deg - 90).toDouble()).toFloat()
            val m = textMeasurer.measure(ch, TextStyle(fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gray.copy(alpha = 0.8f)))
            drawText(m, topLeft = Offset(cx + R * 1.06f * cos(a) - m.size.width / 2f, cy + R * 1.06f * sin(a) - m.size.height / 2f))
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
            drawLine(Gold.copy(alpha = 0.9f), Offset(cx, cy), Offset(cx + R * cos(a), cy + R * sin(a)), 1.6f)
            val tag = textMeasurer.measure(
                "θ̂ %03d° ± %d°".format(e.bearingDeg.toInt(), e.sectorHalfWidthDeg.toInt()),
                TextStyle(fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold),
            )
            drawText(tag, topLeft = Offset(cx - tag.size.width / 2f, cy - R - 12.dp.toPx()))
        }
        // 机头方向小三角
        val hA = Math.toRadians((heading - 90).toDouble()).toFloat()
        val tri = Path().apply {
            moveTo(cx + R * 0.12f * cos(hA), cy + R * 0.12f * sin(hA))
            lineTo(cx + 5f * cos(hA + PI.toFloat() / 2), cy + 5f * sin(hA + PI.toFloat() / 2))
            lineTo(cx + 5f * cos(hA - PI.toFloat() / 2), cy + 5f * sin(hA - PI.toFloat() / 2))
            close()
        }
        drawPath(tri, White.copy(alpha = 0.9f))
        // 底注
        val note = textMeasurer.measure("径向 = 平滑 RSSI(相对)· 世界方位,北朝上", TextStyle(fontFamily = Mono, fontSize = 7.sp, color = Gray))
        drawText(note, topLeft = Offset(cx - note.size.width / 2f, size.height - note.size.height))
    }
}

/** 航向×RSSI 调制谱 —— 直角坐标分箱散点 + 峰带 */
@Composable
private fun HeadingRssiProfileChart(e: BearingEstimate) {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
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
        val note = textMeasurer.measure("航向×RSSI 调制谱 · 平滑后", TextStyle(fontFamily = Mono, fontSize = 7.sp, color = Gray))
        drawText(note, topLeft = Offset(0f, size.height - note.size.height))
    }
}

/** 位置融合图 —— 俯视直角坐标,中心=本机,格距 25m */
@Composable
private fun PositionFixPlot(e: BearingEstimate) {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val R = minOf(cx, cy) - 16.dp.toPx()
        val step = R / 4f
        // 垫子格 25m
        for (i in -4..4) {
            drawLine(Gray.copy(alpha = 0.10f), Offset(cx + i * step, cy - R), Offset(cx + i * step, cy + R), 0.6f)
            drawLine(Gray.copy(alpha = 0.10f), Offset(cx - R, cy + i * step), Offset(cx + R, cy + i * step), 0.6f)
        }
        // 距离环 25/50/75/100
        for (s in listOf(0.25f, 0.5f, 0.75f, 1f)) {
            drawCircle(Gray.copy(alpha = 0.2f), R * s, Offset(cx, cy), style = Stroke(0.7f))
        }
        // 北向箭头
        drawLine(White.copy(alpha = 0.6f), Offset(cx, cy - R * 0.12f), Offset(cx, cy - R * 0.98f), 1.4f)
        val arrow = Path().apply { moveTo(cx, cy - R); lineTo(cx - 5f, cy - R + 8f); lineTo(cx + 5f, cy - R + 8f); close() }
        drawPath(arrow, White.copy(alpha = 0.6f))

        if (!e.eastM.isNaN() && !e.northM.isNaN()) {
            val px = cx + (e.eastM / 100f * R)
            val py = cy - (e.northM / 100f * R)
            // 方位扇形(浅金)
            val aDeg = Math.toDegrees(atan2(e.eastM, e.northM).toDouble()).toFloat()
            drawArc(
                Gold.copy(alpha = 0.06f),
                startAngle = aDeg - 90f - e.sectorHalfWidthDeg,
                sweepAngle = e.sectorHalfWidthDeg * 2f,
                useCenter = true,
                topLeft = Offset(cx - R, cy - R),
                size = Size(R * 2f, R * 2f),
            )
            // 融合点金十字
            val cross = 7.dp.toPx()
            drawLine(Gold.copy(alpha = 0.95f), Offset(px - cross, py), Offset(px + cross, py), 1.8f)
            drawLine(Gold.copy(alpha = 0.95f), Offset(px, py - cross), Offset(px, py + cross), 1.8f)
            drawCircle(Gold.copy(alpha = 0.5f), 4.dp.toPx(), Offset(px, py))
            // 置信椭圆
            val aSemi = (e.ellipseSemiMajorM / 100f * R).coerceAtLeast(3f)
            val bSemi = (e.ellipseSemiMinorM / 100f * R).coerceAtLeast(3f)
            withTransform({
                translate(px, py)
                rotate(Math.toRadians(-e.ellipseAngleDeg.toDouble()).toFloat(), pivot = Offset.Zero)
            }) {
                drawOval(Gold.copy(alpha = 0.5f), topLeft = Offset(-aSemi, -bSemi), size = Size(aSemi * 2f, bSemi * 2f), style = Stroke(1.2f))
            }
            // d±σ 虚线弧
            if (!e.distanceM.isNaN()) {
                val dIn = ((e.distanceM - e.sigmaDistanceM) / 100f * R).coerceAtLeast(0f)
                val dOut = ((e.distanceM + e.sigmaDistanceM) / 100f * R).coerceAtMost(R)
                listOf(dIn, dOut).forEach { rr ->
                    drawCircle(Gold.copy(alpha = 0.3f), rr, Offset(cx, cy), style = Stroke(1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                }
            }
        } else {
            val note = textMeasurer.measure("多圈融合未就绪 —— 至少完整旋转一圈", TextStyle(fontFamily = Sans, fontSize = 9.sp, color = Gray))
            drawText(note, topLeft = Offset(cx - note.size.width / 2f, cy))
        }
        val note2 = textMeasurer.measure("格距 25m · 椭圆为估计误差,非测量精度", TextStyle(fontFamily = Mono, fontSize = 7.sp, color = Gray))
        drawText(note2, topLeft = Offset(cx - note2.size.width / 2f, size.height - note2.size.height))
    }
}

/** BLE 广播详情面板 —— 真实 ScanRecord 字段,缺失标「—」 */
@Composable
private fun BleAdvPanel(t: RadarTarget) {
    val adv = t.bleAdv
    TermPanel("BLE 广播详情 · ADVERTISING DATA") {
        if (adv == null || !adv.hasRecord) {
            Text("未捕获到广播包 —— 目标可能由经典蓝牙发现上报", fontFamily = Sans, fontSize = 10.sp, color = Gray)
        } else {
            InfoRow("观测方式", if (t.observedVia == "ble") "BLE 扫描" else "经典发现(合并)")
            InfoRow("发射功率 TX", if (adv.txPowerLevelDbm != Int.MIN_VALUE) "${adv.txPowerLevelDbm} dBm" else "未广播")
            InfoRow("广播标志", if (adv.advFlags >= 0) "0x%02X".format(adv.advFlags) else "未广播")
            InfoRow("厂商 ID", if (adv.manufacturerId >= 0) "0x%04X".format(adv.manufacturerId) else "—")
            if (adv.serviceUuids.isNotEmpty()) {
                InfoRow("服务 UUID", "${adv.serviceUuids.size} 个")
                adv.serviceUuids.take(8).forEach { u ->
                    Text("   · $u", fontFamily = Mono, fontSize = 8.sp, color = White)
                }
                if (adv.serviceUuids.size > 8) Text("   · … 另有 ${adv.serviceUuids.size - 8} 个", fontFamily = Mono, fontSize = 8.sp, color = Gray)
            }
            adv.manufacturerData.entries.take(4).forEach { e ->
                InfoRow("厂商数据", "0x%04X · ${e.value}".format(e.key))
            }
            adv.serviceData.entries.take(4).forEach { e ->
                InfoRow("服务数据", "${e.key} · ${e.value}")
            }
            Text(
                "广播字段为原始捕获值;字段缺失表示该广播包未携带",
                fontFamily = Sans, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** WiFi 射频与安全块 */
@Composable
private fun WifiRfBlock(t: RadarTarget) {
    TermPanel("射频参数 · RF PARAMETERS") {
        InfoRow("BSSID", t.id)
        InfoRow("频率", if (t.frequencyMhz > 0) "${t.frequencyMhz} MHz" else "--")
        InfoRow("信道", t.channel.ifEmpty { "--" })
        InfoRow("带宽", t.channelWidth.ifEmpty { "--" })
        InfoRow("中心频率0", if (t.centerFreq0Mhz > 0) "${t.centerFreq0Mhz} MHz" else "--")
        InfoRow("中心频率1", if (t.centerFreq1Mhz > 0) "${t.centerFreq1Mhz} MHz" else "--")
    }
    Spacer(Modifier.height(8.dp))
    TermPanel("安全协议 · SECURITY") {
        InfoRow("加密", t.security.ifEmpty { "--" })
        InfoRow("capabilities", t.capabilitiesRaw.ifEmpty { "--" })
    }
}

/** 蓝牙参数块 */
@Composable
private fun BtBlock(t: RadarTarget) {
    TermPanel("蓝牙参数 · BLUETOOTH PARAMETERS") {
        InfoRow("MAC 地址", t.address)
        InfoRow("设备大类", t.deviceClassMajor.ifEmpty { "--" })
        InfoRow("设备小类", t.deviceClassDetail.ifEmpty { "--" })
        InfoRow("链路类型", t.linkType.ifEmpty { "--" })
        InfoRow("绑定状态", t.bondState.ifEmpty { "--" })
        InfoRow("频段", t.channel)
    }
}

/** 时间轴块 */
@Composable
private fun TemporalBlock(t: RadarTarget) {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    TermPanel("时间轴 · TIMELINE") {
        InfoRow("首次发现", fmt.format(Date(t.firstSeenMs)))
        InfoRow("最后发现", fmt.format(Date(t.lastSeenMs)))
        InfoRow("存活时长", formatDuration(t.lastSeenMs - t.firstSeenMs))
    }
}

/** 战术评估块 —— 与雷达页共用同一套威胁分级推演标准 */
@Composable
private fun ThreatAssessment(t: RadarTarget) {
    val level = radarThreatTier(t)
    TermPanel("战术评估 · THREAT ASSESSMENT") {
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
                "[!] 该网络加密等级过低（${t.security}），流量可被明文嗅探",
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
            Text("数据来源 · PROVENANCE", fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (t.type == "wifi")
                    "SSID/BSSID/频率/信道/带宽/安全: WifiManager ScanResult 原始返回值。" +
                        "距离: RSSI 路径损耗模型估计(假定 PTX -40dBm, n=2.5),非测量。" +
                        "方位: MAC 哈希占位估计,手机无 AoA 硬件;旋转寻向推演见定位面板。" +
                        "底噪: -92dBm 常量假设。厂商: 内置 OUI 表,未命中显示未知。"
                else
                    "MAC/名称/类别/绑定/链路: BluetoothDevice 原始返回值。" +
                        "广播数据: ScanRecord 原始捕获(未携带字段显示「—」)。" +
                        "距离: RSSI 路径损耗模型估计(广播 PTX 或假定 -59dBm, n=2.5),非测量。" +
                        "方位: MAC 哈希占位估计;旋转寻向推演见定位面板。",
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

private fun rssiColor(rssi: Int): Color = when {
    rssi > -50 -> Danger
    rssi > -70 -> Warn
    else -> TacGreen
}

private fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "不足 1 秒"
    ms < 60_000 -> "${ms / 1000} 秒"
    else -> "${ms / 60_000} 分 ${ms % 60_000 / 1000} 秒"
}
