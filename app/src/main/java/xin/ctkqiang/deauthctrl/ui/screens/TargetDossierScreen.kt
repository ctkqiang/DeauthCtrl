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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.manager.RadarTarget
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
import xin.ctkqiang.deauthctrl.viewmodel.RadarViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 目标档案页 —— 单台设备全量技术资料
 *
 * 从雷达目标列表点入。数据取自真实扫描结果：
 * - WiFi：BSSID/SSID/频率/信道/带宽/中心频率/安全协议/原始 capabilities
 * - 蓝牙：MAC/设备大类/小类/链路类型/绑定状态
 * - 通用：厂商 OUI、距离、方位、SNR、底噪、首末发现时间、命中次数
 * - 图表：RSSI 历史趋势（Canvas 磷光线图，含 min/avg/max 参考线）
 * - 战术评估：按信号强度与距离推算接近程度
 */
@Composable
fun TargetDossierScreen(vm: RadarViewModel, targetId: String, back: () -> Unit) {
    val targets by vm.targets.collectAsState()
    val target = targets.firstOrNull { it.id == targetId }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("目标档案 · TARGET DOSSIER", back)
        if (target == null) {
            EmptyHint("目标已丢失 —— 设备离开探测范围或雷达已停止")
        } else {
            Column(
                Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
            ) {
                IdentityBlock(target)
                Spacer(Modifier.height(8.dp))
                SignalTelemetry(target)
                Spacer(Modifier.height(8.dp))
                SignalHistoryChart(target)
                Spacer(Modifier.height(8.dp))
                if (target.type == "wifi") WifiRfBlock(target) else BtBlock(target)
                Spacer(Modifier.height(8.dp))
                TemporalBlock(target)
                Spacer(Modifier.height(8.dp))
                ThreatAssessment(target)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** 身份块 —— 类型徽章 + 名称 + 关键标识 */
@Composable
private fun IdentityBlock(t: RadarTarget) {
    val badgeColor = if (t.type == "wifi") Warn else TacGreen
    Surface(
        Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.7f)),
                ) {
                    Text(
                        if (t.type == "wifi") "WIFI" else "BLUETOOTH",
                        fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = badgeColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
        }
    }
}

/** 实时遥测块 —— RSSI 大读数 + 信号条 + 距离/方位/SNR */
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
            Text("SNR ${t.snrDb}dB", fontFamily = Mono, fontSize = 10.sp, color = Gray, modifier = Modifier.padding(bottom = 4.dp))
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
            TelemetryCell("距离", "%.1f m".format(t.distanceM))
            TelemetryCell("方位", "%03d°".format(t.angleDeg.toInt()))
            TelemetryCell("底噪", "${t.noiseDbm}dBm")
            TelemetryCell("命中", "${t.hitCount} 次")
        }
    }
}

@Composable
private fun TelemetryCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacGreen)
        Text(label, fontFamily = Sans, fontSize = 8.sp, color = Gray)
    }
}

/** RSSI 历史趋势图 —— Canvas 磷光线图 + 网格 + min/avg/max */
@Composable
private fun SignalHistoryChart(t: RadarTarget) {
    val history = t.history
    val lineColor = rssiColor(t.rssi)
    TermPanel("信号历史 · RSSI TREND (${history.size} 样本)") {
        if (history.size < 2) {
            Text("样本积累中 —— 每个扫描周期追加一个采样点", fontFamily = Sans, fontSize = 10.sp, color = Gray)
        } else {
            val stats = remember(history) {
                val rssiList = history.map { it.rssi }
                Triple(rssiList.minOrNull() ?: 0, rssiList.average().toFloat(), rssiList.maxOrNull() ?: 0)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MIN ${stats.first}dBm", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                Text("AVG %d dBm".format(stats.second.toInt()), fontFamily = Mono, fontSize = 9.sp, color = Gold)
                Text("MAX ${stats.third}dBm", fontFamily = Mono, fontSize = 9.sp, color = Gray)
            }
            Spacer(Modifier.height(6.dp))
            val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val lo = -100f; val hi = -30f // 纵轴 dB 范围
                fun yOf(db: Int): Float = size.height * (1f - (db - lo) / (hi - lo))
                val stepX = size.width / (history.size - 1).coerceAtLeast(1)

                // 网格：每 10dB 一条横线
                for (db in -90 downTo -40 step 10) {
                    val y = yOf(db)
                    drawLine(Gray.copy(alpha = 0.15f), Offset(0f, y), Offset(size.width, y), 0.7f)
                }
                // 中线（-65dB）略亮
                drawLine(Gold.copy(alpha = 0.25f), Offset(0f, yOf(-65)), Offset(size.width, yOf(-65)), 0.9f)

                val pts = history.mapIndexed { i, s -> Offset(i * stepX, yOf(s.rssi)) }

                // 磷光层：粗低透明度
                val glowPath = Path().apply {
                    pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                }
                drawPath(glowPath, lineColor.copy(alpha = 0.22f), style = Stroke(width = 5f))
                // 主线：细高亮
                drawPath(glowPath, lineColor, style = Stroke(width = 1.4f))

                // 末点呼吸光点
                val last = pts.last()
                drawCircle(lineColor.copy(alpha = 0.25f), 7f, last)
                drawCircle(White.copy(alpha = 0.9f), 2.2f, last)

                // 时间轴首末标签
                val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
                val m0 = textMeasurer.measure(fmt.format(Date(history.first().timeMs)), TextStyle(fontFamily = Mono, fontSize = 8.sp, color = Gray))
                val m1 = textMeasurer.measure(fmt.format(Date(history.last().timeMs)), TextStyle(fontFamily = Mono, fontSize = 8.sp, color = Gray))
                drawText(m0, topLeft = Offset(0f, size.height - m0.size.height))
                drawText(m1, topLeft = Offset(size.width - m1.size.width, size.height - m1.size.height))
            }
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

/** 战术评估块 —— 由信号/距离推演的接近程度分级 */
@Composable
private fun ThreatAssessment(t: RadarTarget) {
    val level: Triple<String, androidx.compose.ui.graphics.Color, String> = when {
        t.distanceM < 8f || t.rssi > -45 -> Triple(
            "高危接近", Danger,
            "目标信号极强 / 估算距离 ${"%.1f".format(t.distanceM)}m，位于贴身范围内，建议立即甄别身份",
        )
        t.distanceM < 25f || t.rssi > -65 -> Triple(
            "中等接近", Warn,
            "目标位于 ${"%.1f".format(t.distanceM)}m 附近，信号稳定，持续跟踪其移动轨迹",
        )
        else -> Triple(
            "远端目标", TacGreen,
            "目标距本机较远（${"%.1f".format(t.distanceM)}m），属环境背景设备，维持常规监视",
        )
    }
    TermPanel("战术评估 · THREAT ASSESSMENT") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Star(level.second, 8.dp)
            Spacer(Modifier.width(8.dp))
            Text(level.first, fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = level.second)
        }
        Spacer(Modifier.height(6.dp))
        Text(level.third, fontFamily = Sans, fontSize = 10.sp, color = Gray, lineHeight = 15.sp)
        if (t.type == "wifi" && (t.security.startsWith("开放") || t.security.startsWith("WEP"))) {
            Spacer(Modifier.height(6.dp))
            Text(
                "⚠ 该网络加密等级过低（${t.security}），流量可被明文嗅探",
                fontFamily = Sans, fontSize = 10.sp, color = Warn, fontWeight = FontWeight.Bold,
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

private fun rssiColor(rssi: Int): androidx.compose.ui.graphics.Color = when {
    rssi > -50 -> Danger
    rssi > -70 -> Warn
    else -> TacGreen
}

private fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "不足 1 秒"
    ms < 60_000 -> "${ms / 1000} 秒"
    else -> "${ms / 60_000} 分 ${ms % 60_000 / 1000} 秒"
}
