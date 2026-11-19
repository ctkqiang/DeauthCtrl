package xin.ctkqiang.deauthctrl.ui.screens

import android.location.GnssStatus
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.GnssSat
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
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.GnssSkyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** CN0 弱信号门限(dB-Hz):低于该值在明细里标 [弱] 并加琥珀外环 */
private const val WEAK_CN0_DB_HZ = 25f

/**
 * GNSS 星座天图 —— 系统 GnssStatus 真实卫星数据的天空投影
 *
 * 天顶图以北朝上做等距方位投影:r = R × (90° − 仰角) / 90°,
 * 屏幕角 = 方位角(正北 0°,顺时针),x = cx + r·sin(az),y = cy − r·cos(az)。
 * 每颗星按星座着色:GPS=Gold / 伽利略=TacGreen / 格洛纳斯=Warn /
 * 北斗=Red / 准天顶=White / 其他=Gray;实心且带白芯 = 参与本次定位解算,
 * 空心 = 已跟踪但未参与。仰角 < 5°(或角度无效)的卫星不画在盘上,
 * 以小字列在盘外,避免贴着地平线圈挤成一团。
 *
 * 数据诚实性:方位角、仰角、CN0、usedInFix 全部取自系统回调,页面不做
 * 任何推算或美化 —— 读不到就显示 "--"。
 */
@Composable
fun GnssSkyScreen(back: () -> Unit) {
    val vm: GnssSkyViewModel = viewModel()
    val state by vm.state.collectAsState()

    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }

    val satellites = state.satellites
    // 盘面卫星:仰角 ≥ 5° 且方位/仰角均为有效浮点
    val onSky = satellites.filter { it.isPlottable() }
    // 环外卫星:低仰角或角度无效 —— 不投影到盘面,只在小字表里列出真实值
    val offSky = satellites.filter { !it.isPlottable() }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val weakBadge = stringResource(R.string.gnss_weak_badge)
    val updatedText = if (state.updatedAtMs > 0L) {
        timeFmt.format(Date(state.updatedAtMs))
    } else {
        "--:--:--"
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.gnss_title), back)

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            // ── 顶部状态条:定位开关 / 参与定位 / 总星数 / 更新时间 ──
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.gpsEnabled) stringResource(R.string.gnss_location_on) else stringResource(R.string.gnss_location_off),
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.gpsEnabled) TacGreen else Warn,
                    )
                    Spacer(Modifier.width(9.dp))
                    Box(Modifier.width(1.dp).height(11.dp).background(BorderDim))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        stringResource(R.string.gnss_fix_summary, state.fixCount, satellites.size),
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        color = Gold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.gnss_updated, updatedText), fontFamily = Mono, fontSize = 9.sp, color = Gray)
                }
            }

            // ── 权限 / 定位开关 / 回调注册失败的告警 ──
            state.error?.let { err ->
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                    color = Danger.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
                ) {
                    Text(
                        stringResource(R.string.gnss_error_line, err),
                        fontFamily = Sans,
                        fontSize = 10.sp,
                        color = Danger,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }

            // ── 天顶投影盘 ──
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.gnss_sky_panel_title)) {
                SkyPlot(onSky, Modifier.fillMaxWidth().height(280.dp))
                Spacer(Modifier.height(7.dp))
                Text(
                    stringResource(R.string.gnss_projection_note),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 12.sp,
                )
                if (offSky.isNotEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        stringResource(R.string.gnss_offsky_title),
                        fontFamily = Sans,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Warn,
                    )
                    Spacer(Modifier.height(3.dp))
                    offSky.forEach { sat ->
                        Text(
                            stringResource(
                                R.string.gnss_offsky_line,
                                sat.constellationName,
                                sat.svid,
                                degText(sat.elevationDeg),
                                degText(sat.azimuthDeg),
                                cn0Text(sat, weakBadge),
                            ),
                            fontFamily = Mono,
                            fontSize = 8.sp,
                            color = Gray,
                        )
                    }
                }
            }

            // ── 星座图例 ──
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.gnss_legend_title)) {
                ConstellationLegend()
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SatDot(TacGreen, filled = true)
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.gnss_legend_filled), fontFamily = Sans, fontSize = 8.sp, color = Gray)
                    Spacer(Modifier.width(14.dp))
                    SatDot(Gray, filled = false)
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.gnss_legend_hollow), fontFamily = Sans, fontSize = 8.sp, color = Gray)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.gnss_cn0_note, WEAK_CN0_DB_HZ.toInt()),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                )
            }

            // ── 卫星明细 ──
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.gnss_sat_detail_title, satellites.size)) {
                if (satellites.isEmpty()) {
                    EmptyHint(
                        if (state.error != null) stringResource(R.string.gnss_empty_error)
                        else stringResource(R.string.gnss_empty_waiting),
                    )
                } else {
                    Text(
                        stringResource(R.string.gnss_format_hint),
                        fontFamily = Sans,
                        fontSize = 8.sp,
                        color = Gray,
                    )
                    Spacer(Modifier.height(4.dp))
                    satellites.forEach { sat -> SatelliteRow(sat) }
                }
            }

            // ── 数据来源与诚实说明 ──
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.gnss_data_note, WEAK_CN0_DB_HZ.toInt()),
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Gray,
                lineHeight = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ─── 天顶投影 Canvas ─────────────────────────────────────────────────────────

/**
 * 天顶投影盘
 *
 * 北朝上、东南西北顺时针;同心环对应仰角 0°/30°/60°,圆心为 90° 天顶。
 * 每颗卫星按 (方位角, 仰角) 投影:
 *   r = R × (90° − el) / 90°
 *   x = cx + r × sin(az),y = cy − r × cos(az)
 */
@Composable
private fun SkyPlot(satellites: List<GnssSat>, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val ringHint = stringResource(R.string.gnss_ring_hint)

    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        // 预留四周空间给 N/E/S/W 方位字
        val radius = (minOf(size.width, size.height) / 2f - 18.dp.toPx()).coerceAtLeast(1f)

        // 盘底 + 地平线圈(仰角 0°)
        drawCircle(SurfaceAlt, radius, center)
        drawCircle(Gold.copy(alpha = 0.40f), radius, center, style = Stroke(1.3.dp.toPx()))

        // 仰角 30° / 60° 同心环;90° 天顶为圆心小点
        listOf(30f, 60f).forEach { el ->
            val r = radius * (90f - el) / 90f
            drawCircle(BorderDim, r, center, style = Stroke(1.dp.toPx()))
        }
        drawCircle(Gold.copy(alpha = 0.75f), 1.8.dp.toPx(), center)

        // 十字线:北-南 / 东-西
        drawLine(BorderDim, Offset(cx - radius, cy), Offset(cx + radius, cy), 1f)
        drawLine(BorderDim, Offset(cx, cy - radius), Offset(cx, cy + radius), 1f)

        // 30° 一格的方位刻度(正北/东/南/西加长)
        for (deg in 0 until 360 step 30) {
            val a = Math.toRadians(deg.toDouble())
            val inner = if (deg % 90 == 0) radius * 0.94f else radius * 0.97f
            drawLine(
                BorderDim,
                Offset(cx + inner * sin(a).toFloat(), cy - inner * cos(a).toFloat()),
                Offset(cx + radius * sin(a).toFloat(), cy - radius * cos(a).toFloat()),
                1f,
            )
        }

        // 罗盘方位字(北朝上)
        val dirStyle = TextStyle(
            fontFamily = Sans,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Gray,
        )
        listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (label, deg) ->
            val a = Math.toRadians(deg.toDouble())
            val labelRadius = radius + 9.dp.toPx()
            val measured = textMeasurer.measure(label, dirStyle)
            drawText(
                measured,
                topLeft = Offset(
                    cx + labelRadius * sin(a).toFloat() - measured.size.width / 2f,
                    cy - labelRadius * cos(a).toFloat() - measured.size.height / 2f,
                ),
            )
        }

        // 环含义提示(左上角,不遮挡盘面)
        val hint = textMeasurer.measure(
            ringHint,
            TextStyle(fontFamily = Mono, fontSize = 7.sp, color = Gray.copy(alpha = 0.75f)),
        )
        drawText(hint, topLeft = Offset(4.dp.toPx(), 4.dp.toPx()))

        // 卫星投影
        satellites.forEach { sat ->
            val az = Math.toRadians(sat.azimuthDeg.toDouble())
            val el = sat.elevationDeg.coerceIn(0f, 90f)
            val r = radius * (90f - el) / 90f
            val x = cx + r * sin(az).toFloat()
            val y = cy - r * cos(az).toFloat()
            val dotCenter = Offset(x, y)
            val color = constellationColor(sat.constellation)
            val dotRadius = 4.dp.toPx()

            if (sat.usedInFix) {
                // 参与解算:实心 + 白芯
                drawCircle(color, dotRadius, dotCenter)
                drawCircle(Color.White, 1.5.dp.toPx(), dotCenter)
            } else {
                // 已跟踪未参与:空心
                drawCircle(color.copy(alpha = 0.85f), dotRadius, dotCenter, style = Stroke(1.3.dp.toPx()))
            }

            // 弱信号:额外一圈琥珀细环
            val cn0 = sat.cn0DbHz
            if (cn0 != null && cn0 < WEAK_CN0_DB_HZ) {
                drawCircle(
                    Warn.copy(alpha = 0.55f),
                    dotRadius + 2.5.dp.toPx(),
                    dotCenter,
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
    }
}

// ─── 明细行 / 图例 ───────────────────────────────────────────────────────────

/** 单颗卫星明细:星座色点 / 编号 / 仰角 / 方位角 / CN0 / 参与定位标记 */
@Composable
private fun SatelliteRow(sat: GnssSat) {
    val color = constellationColor(sat.constellation)
    val weak = sat.cn0DbHz != null && sat.cn0DbHz < WEAK_CN0_DB_HZ

    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SatDot(color, filled = sat.usedInFix)
        Spacer(Modifier.width(7.dp))
        Text(
            stringResource(R.string.gnss_sat_name, sat.constellationName, sat.svid),
            fontFamily = Sans,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = White,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.gnss_el, degText(sat.elevationDeg)),
            fontFamily = Mono,
            fontSize = 9.sp,
            color = Gray,
            modifier = Modifier.width(48.dp),
        )
        Text(
            stringResource(R.string.gnss_az, degText(sat.azimuthDeg)),
            fontFamily = Mono,
            fontSize = 9.sp,
            color = Gray,
            modifier = Modifier.width(52.dp),
        )
        Text(
            stringResource(R.string.gnss_cn0, cn0ValueText(sat)),
            fontFamily = Mono,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                sat.cn0DbHz == null -> Gray
                weak -> Warn
                else -> TacGreen
            },
            modifier = Modifier.width(62.dp),
        )
        Box(Modifier.width(24.dp)) {
            if (weak) {
                Text(stringResource(R.string.gnss_weak_badge), fontFamily = Sans, fontSize = 8.sp, color = Warn)
            }
        }
        Text(
            if (sat.usedInFix) "●" else "○",
            fontFamily = Mono,
            fontSize = 9.sp,
            color = if (sat.usedInFix) TacGreen else Gray,
        )
    }
}

/** 星座颜色图例 —— 与盘面/明细的颜色映射一一对应 */
@Composable
private fun ConstellationLegend() {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(stringResource(R.string.gnss_legend_gps), Gold)
            LegendItem(stringResource(R.string.gnss_legend_galileo), TacGreen)
            LegendItem(stringResource(R.string.gnss_legend_glonass), Warn)
            LegendItem(stringResource(R.string.gnss_legend_beidou), Red)
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(stringResource(R.string.gnss_legend_qzss), White)
            LegendItem(stringResource(R.string.gnss_legend_sbas), Gray)
            LegendItem(stringResource(R.string.gnss_legend_irnss), Gray)
            LegendItem(stringResource(R.string.gnss_legend_unknown), Gray)
        }
    }
}

@Composable
private fun LegendItem(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SatDot(color, filled = true)
        Spacer(Modifier.width(5.dp))
        Text(name, fontFamily = Sans, fontSize = 9.sp, color = Gray)
    }
}

/** 星座色点:实心 = 参与解算,空心 = 仅跟踪 */
@Composable
private fun SatDot(color: Color, filled: Boolean) {
    val base = Modifier.size(7.dp).clip(CircleShape)
    Box(
        if (filled) base.background(color)
        else base.border(1.dp, color, CircleShape),
    )
}

// ─── 文本与小工具 ────────────────────────────────────────────────────────────

/** 星座类型 → 天图颜色(GPS=Gold / 伽利略=TacGreen / 格洛纳斯=Warn / 北斗=Red / 准天顶=White / 其他=Gray) */
private fun constellationColor(type: Int): Color = when (type) {
    GnssStatus.CONSTELLATION_GPS -> Gold
    GnssStatus.CONSTELLATION_GALILEO -> TacGreen
    GnssStatus.CONSTELLATION_GLONASS -> Warn
    GnssStatus.CONSTELLATION_BEIDOU -> Red
    GnssStatus.CONSTELLATION_QZSS -> White
    else -> Gray
}

/** 是否绘制到天顶盘:仰角 ≥ 5° 且方位/仰角为有效浮点 */
private fun GnssSat.isPlottable(): Boolean =
    elevationDeg.isFinite() && azimuthDeg.isFinite() && elevationDeg >= 5f

/** 角度文本;系统给出无效浮点时显示 "--",不臆造 */
private fun degText(value: Float): String =
    if (value.isFinite()) "%.0f°".format(value) else "--"

/** CN0 数值文本;系统回报 0/无效时显示 "--" */
private fun cn0ValueText(sat: GnssSat): String =
    sat.cn0DbHz?.let { "%.1f".format(it) } ?: "--"

/** CN0 文本(含弱信号标记);用于环外小字表 */
private fun cn0Text(sat: GnssSat, weakLabel: String): String {
    val cn0 = sat.cn0DbHz ?: return "--"
    return if (cn0 < WEAK_CN0_DB_HZ) "%.1f".format(cn0) + " " + weakLabel else "%.1f".format(cn0)
}
