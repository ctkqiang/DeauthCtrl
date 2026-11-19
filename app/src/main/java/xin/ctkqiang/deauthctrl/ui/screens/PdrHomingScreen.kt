package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.PDR_MAX_STEP_LEN_M
import xin.ctkqiang.deauthctrl.manager.PDR_MIN_STEP_LEN_M
import xin.ctkqiang.deauthctrl.manager.PdrState
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
import xin.ctkqiang.deauthctrl.ui.components.StatChip
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.drawTacticalCorners
import xin.ctkqiang.deauthctrl.viewmodel.PdrHomingViewModel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 步测死推算 + 面包屑归航页 —— PDR HOMING
 *
 * ## 数据来源(全部为真实返回值)
 * - 步数:硬件 TYPE_STEP_COUNTER 差值;无此传感器时回退 TYPE_STEP_DETECTOR。
 * - 航向:TYPE_ROTATION_VECTOR,重映射与低通与雷达页一致。
 * - 未授权 / 无传感器时明确显示「不可用」,不伪造位移。
 *
 * ## 画面
 * 顶部 280dp 航迹图:轨迹随航向旋转到机头朝上,当前点在画布中心,
 * 金色十字是起点,青白折线是本程路径;下方归航箭头给出「相对机头」
 * 的起点方向,距离与方位全部为死推算值并明确标注。
 */
@Composable
fun PdrHomingScreen(back: () -> Unit) {
    val vm: PdrHomingViewModel = viewModel()
    val state by vm.state.collectAsState()

    // 页面可见期持有航向传感器;离开时无条件释放,避免后台耗电
    DisposableEffect(Unit) {
        vm.attach()
        onDispose { vm.detach() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.pdr_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            PdrWarningStrip(state)

            // ── 航迹图 ────────────────────────────────────────────────────
            TermPanel(stringResource(R.string.pdr_map_panel)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Dark.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, BorderDim),
                ) {
                    PdrMapCanvas(state, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.pdr_trail_info, state.trail.size),
                    fontFamily = Mono, fontSize = 8.sp, color = Gray,
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── 归航指示 ──────────────────────────────────────────────────
            HomingPanel(state)

            Spacer(Modifier.height(8.dp))

            // ── 步长 + 控制 ───────────────────────────────────────────────
            StepLengthPanel(state) { vm.setStepLen(it) }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(
                    running = state.running,
                    runLabel = if (state.steps > 0L) {
                        stringResource(R.string.pdr_resume_steps)
                    } else {
                        stringResource(R.string.pdr_start_steps)
                    },
                    stopLabel = stringResource(R.string.pdr_pause_steps),
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.running) vm.pause() else vm.start()
                }
                GhostButton(stringResource(R.string.pdr_reset)) { vm.reset() }
            }

            Spacer(Modifier.height(8.dp))

            // ── 读数 ──────────────────────────────────────────────────────
            TelemetryPanel(state)

            Spacer(Modifier.height(8.dp))

            // ── 诚实说明 ──────────────────────────────────────────────────
            HonestyPanel()

            Spacer(Modifier.height(10.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 航迹图 Canvas
// ══════════════════════════════════════════════════════════════════════════

/**
 * 航迹图
 *
 * 当前点固定在画布中心;轨迹、网格、起点十字整体旋转 -heading,
 * 让机头方向始终朝上(无航向时保持北向朝上)。比例尺按本程轨迹与
 * 起点距离自动适配,10m 网格在缩得太小时按 2 倍粗化并在左下角标注实际格距。
 */
@Composable
private fun PdrMapCanvas(state: PdrState, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val context = LocalContext.current

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = size.minDimension / 2f
        val heading = state.headingDeg

        // 当前点 = 轨迹最后一点(尚无轨迹时为起点 0,0)
        val last = state.trail.lastOrNull()
        val curX = last?.first ?: 0f
        val curY = last?.second ?: 0f
        val homeDist = sqrt(curX * curX + curY * curY)

        // 世界坐标相对当前点的偏移;屏幕 y 轴向下,北(+y)映射为 -y
        var maxRelM = homeDist
        val relTrail = ArrayList<Offset>(state.trail.size)
        for (p in state.trail) {
            val wx = p.first - curX
            val wy = p.second - curY
            relTrail.add(Offset(wx, wy))
            val d = sqrt(wx * wx + wy * wy)
            if (d > maxRelM) maxRelM = d
        }

        val extentM = maxOf(20f, maxRelM)
        val scale = ((radius - 18.dp.toPx()) / extentM).coerceIn(0.02f, 6f)

        // 10m 网格;缩放到格距不足 6dp 时按 2 倍粗化,避免糊成一片
        var gridM = 10f
        while (gridM * scale < 6.dp.toPx() && gridM < 4000f) gridM *= 2f

        // 底色辉光与外圈
        drawCircle(TacGreen.copy(alpha = 0.05f), radius, center)

        withTransform({ rotate(-(heading ?: 0f), center) }) {
            val halfDiag = sqrt(w * w + h * h) / 2f
            val reachM = halfDiag / scale
            val kx0 = floor((curX - reachM) / gridM).toInt()
            val kx1 = ceil((curX + reachM) / gridM).toInt()
            val ky0 = floor((curY - reachM) / gridM).toInt()
            val ky1 = ceil((curY + reachM) / gridM).toInt()
            val gridColor = BorderDim.copy(alpha = 0.55f)

            for (k in kx0..kx1) {
                if (k == 0) continue
                val sx = center.x + (k * gridM - curX) * scale
                drawLine(gridColor, Offset(sx, center.y - halfDiag), Offset(sx, center.y + halfDiag), 1f)
            }
            for (k in ky0..ky1) {
                if (k == 0) continue
                val sy = center.y - (k * gridM - curY) * scale
                drawLine(gridColor, Offset(center.x - halfDiag, sy), Offset(center.x + halfDiag, sy), 1f)
            }

            // 世界坐标轴(过原点)稍亮,帮助辨认方向
            val ox = center.x + (0f - curX) * scale
            val oy = center.y - (0f - curY) * scale
            drawLine(Gold.copy(alpha = 0.16f), Offset(ox, center.y - halfDiag), Offset(ox, center.y + halfDiag), 1f)
            drawLine(Gold.copy(alpha = 0.16f), Offset(center.x - halfDiag, oy), Offset(center.x + halfDiag, oy), 1f)

            // 轨迹:青色底光 + 白色细线
            if (relTrail.size >= 2) {
                val path = Path()
                relTrail.forEachIndexed { index, p ->
                    val sx = center.x + p.x * scale
                    val sy = center.y - p.y * scale
                    if (index == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                drawPath(
                    path,
                    TacGreen.copy(alpha = 0.30f),
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    path,
                    White.copy(alpha = 0.92f),
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            // 起点 -> 当前位置的归航虚线
            val homeX = center.x - curX * scale
            val homeY = center.y + curY * scale
            if (homeDist > 0.5f) {
                drawLine(
                    color = Gold.copy(alpha = 0.30f),
                    start = center,
                    end = Offset(homeX, homeY),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                )
            }

            // 起点十字标记
            val crossLen = 7.dp.toPx()
            drawCircle(Gold.copy(alpha = 0.14f), 10.dp.toPx(), Offset(homeX, homeY))
            drawLine(Gold, Offset(homeX - crossLen, homeY), Offset(homeX + crossLen, homeY), 1.6.dp.toPx())
            drawLine(Gold, Offset(homeX, homeY - crossLen), Offset(homeX, homeY + crossLen), 1.6.dp.toPx())
            drawCircle(Gold, 2.4.dp.toPx(), Offset(homeX, homeY))
        }

        // 外圈 / 内圈 / 四角锁定框
        drawCircle(TacGreen.copy(alpha = 0.20f), radius - 2.dp.toPx(), center, style = Stroke(1.dp.toPx()))
        drawCircle(TacGreen.copy(alpha = 0.07f), radius * 0.62f, center, style = Stroke(1f))
        drawTacticalCorners(TacGreen.copy(alpha = 0.30f), size, 12.dp.toPx(), 1.2.dp.toPx())

        // 正北标记:地图旋转后仍指向世界正北
        val hRad = Math.toRadians((heading ?: 0f).toDouble())
        val northX = (-sin(hRad)).toFloat()
        val northY = (-cos(hRad)).toFloat()
        val nPos = center + Offset(northX, northY) * (radius - 15.dp.toPx())
        val nLabel = textMeasurer.measure(
            "N",
            TextStyle(fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold),
        )
        drawText(
            nLabel,
            topLeft = Offset(nPos.x - nLabel.size.width / 2f, nPos.y - nLabel.size.height / 2f),
        )

        // 左下:实际网格间距;右下:旋转模式
        val gridLabel = textMeasurer.measure(
            context.getString(R.string.pdr_grid_label, gridM.toInt()),
            TextStyle(fontFamily = Mono, fontSize = 8.sp, color = Gray),
        )
        drawText(gridLabel, topLeft = Offset(6.dp.toPx(), h - gridLabel.size.height - 5.dp.toPx()))

        val modeLabel = textMeasurer.measure(
            if (heading != null) {
                context.getString(R.string.pdr_heading_up)
            } else {
                context.getString(R.string.pdr_north_up)
            },
            TextStyle(
                fontFamily = Sans,
                fontSize = 8.sp,
                color = if (heading != null) TacGreen else Warn,
            ),
        )
        drawText(
            modeLabel,
            topLeft = Offset(w - modeLabel.size.width - 6.dp.toPx(), h - modeLabel.size.height - 5.dp.toPx()),
        )

        // 当前点:航向可用时画机头三角(旋转后永远朝上);不可用时画空心警示环
        if (heading != null) {
            val nose = 11.dp.toPx()
            val half = 6.dp.toPx()
            val tri = Path().apply {
                moveTo(center.x, center.y - nose)
                lineTo(center.x - half, center.y + half)
                lineTo(center.x + half, center.y + half)
                close()
            }
            drawCircle(TacGreen.copy(alpha = 0.16f), 13.dp.toPx(), center)
            drawPath(tri, TacGreen)
            drawPath(tri, White.copy(alpha = 0.85f), style = Stroke(1.dp.toPx()))
        } else {
            drawCircle(Warn.copy(alpha = 0.80f), 5.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
            drawCircle(Warn.copy(alpha = 0.25f), 10.dp.toPx(), center, style = Stroke(1.dp.toPx()))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 归航指示
// ══════════════════════════════════════════════════════════════════════════

/** 起点方向相对机头的夹角(度,右正左负);无方位或无航向时 null */
private fun relativeHomeAngle(state: PdrState): Float? {
    val bearing = state.homeBearingDeg ?: return null
    val heading = state.headingDeg ?: return null
    var delta = bearing - heading
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}

/**
 * 归航面板
 *
 * 大号箭头相对机头方向旋转:正上方 = 起点就在正前方。
 * 「距起点」是死推算位置到 (0,0) 的直线距离,不是 GNSS 测量值。
 */
@Composable
private fun HomingPanel(state: PdrState) {
    TermPanel(stringResource(R.string.pdr_homing_panel)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HomingArrow(state)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                val dist = state.homeDistanceM
                val bearing = state.homeBearingDeg
                val arrived = dist != null && dist < 2f
                val bearingText = bearing?.let { "%03.0f°".format(it) } ?: "--"
                val distanceText = when {
                    dist == null -> stringResource(R.string.pdr_home_distance_none)
                    arrived -> stringResource(R.string.pdr_home_arrived)
                    else -> stringResource(R.string.pdr_home_distance, "%.1f".format(dist))
                }
                Text(
                    stringResource(R.string.pdr_distance_bearing, distanceText, bearingText),
                    fontFamily = Sans,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        dist == null -> Gray
                        arrived -> TacGreen
                        else -> Gold
                    },
                )
                Spacer(Modifier.height(4.dp))

                val rel = relativeHomeAngle(state)
                val headingDeg = state.headingDeg
                val headingText = if (headingDeg != null) {
                    stringResource(R.string.pdr_nose_value, "%03.0f".format(headingDeg))
                } else {
                    stringResource(R.string.pdr_nose_none)
                }
                val relText = when {
                    rel == null -> stringResource(R.string.pdr_rel_none)
                    abs(rel) < 3f -> stringResource(R.string.pdr_rel_ahead)
                    rel > 0f -> stringResource(R.string.pdr_rel_right, "%.0f".format(abs(rel)))
                    else -> stringResource(R.string.pdr_rel_left, "%.0f".format(abs(rel)))
                }
                Text(
                    stringResource(R.string.pdr_heading_rel, headingText, relText),
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    color = White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.pdr_arrow_note),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray.copy(alpha = 0.85f),
                    lineHeight = 11.sp,
                )
                if (state.running && state.steps == 0L) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(R.string.pdr_waiting_first_step),
                        fontFamily = Sans,
                        fontSize = 8.sp,
                        color = Gray,
                    )
                }
            }
        }
    }
}

/** 大号归航箭头 —— 相对机头夹角旋转;不可用时画横杠,绝不伪造方向 */
@Composable
private fun HomingArrow(state: PdrState) {
    val rel = relativeHomeAngle(state)
    val dist = state.homeDistanceM
    val arrived = dist != null && dist < 2f
    val arrowColor = when {
        rel == null -> Gray
        arrived -> TacGreen
        else -> Gold
    }

    Canvas(Modifier.size(104.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f

        drawCircle(BorderDim, r - 1.dp.toPx(), center, style = Stroke(1.dp.toPx()))
        drawCircle(arrowColor.copy(alpha = 0.10f), r * 0.78f, center)

        // 机头基准:正上方短刻度
        drawLine(
            arrowColor.copy(alpha = 0.55f),
            Offset(center.x, center.y - r + 2.dp.toPx()),
            Offset(center.x, center.y - r * 0.72f),
            2.dp.toPx(),
        )

        if (rel != null) {
            withTransform({ rotate(rel, center) }) {
                val half = r * 0.24f
                val wingY = center.y + r * 0.16f
                val arrow = Path().apply {
                    moveTo(center.x, center.y - r * 0.62f)
                    lineTo(center.x - half, wingY)
                    lineTo(center.x - half * 0.34f, wingY)
                    lineTo(center.x, center.y + r * 0.46f)
                    lineTo(center.x + half * 0.34f, wingY)
                    lineTo(center.x + half, wingY)
                    close()
                }
                drawPath(arrow, arrowColor.copy(alpha = 0.22f))
                drawPath(arrow, arrowColor)
            }
        } else {
            // 方位不可用:一条横杠 + 灰环,不画箭头
            drawLine(
                Gray,
                Offset(center.x - r * 0.18f, center.y),
                Offset(center.x + r * 0.18f, center.y),
                2.dp.toPx(),
            )
        }

        drawCircle(arrowColor, 2.5.dp.toPx(), center)
        drawTacticalCorners(arrowColor.copy(alpha = 0.28f), size, 8.dp.toPx(), 1.dp.toPx())
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 步长 / 读数 / 告警 / 说明
// ══════════════════════════════════════════════════════════════════════════

/** 步长滑条 —— 0.50-1.00m,0.05m 步进,直接影响之后的每一步 */
@Composable
private fun StepLengthPanel(state: PdrState, onStepLen: (Float) -> Unit) {
    TermPanel(stringResource(R.string.pdr_step_panel)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.pdr_step_length_label), fontFamily = Sans, fontSize = 11.sp, color = Gray)
            Text("%.2f m".format(state.stepLenM), fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
        }
        Slider(
            value = state.stepLenM,
            onValueChange = onStepLen,
            valueRange = PDR_MIN_STEP_LEN_M..PDR_MAX_STEP_LEN_M,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = Red,
                activeTrackColor = Red,
                inactiveTrackColor = SurfaceAlt,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0.50 m", fontFamily = Mono, fontSize = 8.sp, color = Gray)
            Text(stringResource(R.string.pdr_step_increment), fontFamily = Mono, fontSize = 8.sp, color = Gray)
            Text("1.00 m", fontFamily = Mono, fontSize = 8.sp, color = Gray)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            stringResource(R.string.pdr_step_hint),
            fontFamily = Sans, fontSize = 8.sp, color = Gray.copy(alpha = 0.85f), lineHeight = 11.sp,
        )
    }
}

/** 实时读数 —— 步数 / 距离 / 航向 / 距起点,全部来自状态流 */
@Composable
private fun TelemetryPanel(state: PdrState) {
    TermPanel(stringResource(R.string.pdr_telemetry_panel)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatChip(stringResource(R.string.pdr_stat_steps), state.steps.toString(), active = state.steps > 0L)
            StatChip(stringResource(R.string.pdr_stat_distance), "%.1f m".format(state.distanceM), active = state.distanceM > 0f)
            StatChip(
                stringResource(R.string.pdr_stat_heading),
                state.headingDeg?.let { "%.0f°".format(it) } ?: "--",
                active = state.headingDeg != null,
            )
            StatChip(
                stringResource(R.string.pdr_stat_home),
                state.homeDistanceM?.let { "%.1f m".format(it) } ?: "--",
                active = state.homeDistanceM != null,
            )
        }
        Spacer(Modifier.height(5.dp))
        val stepSensorText = if (state.hasStepSensor) {
            if (state.stepSensorName.isBlank()) {
                stringResource(R.string.pdr_sensor_step_default)
            } else {
                state.stepSensorName
            }
        } else {
            stringResource(R.string.pdr_sensor_step_unavailable)
        }
        val headingSensorText = if (state.hasHeadingSensor) {
            stringResource(R.string.pdr_sensor_rotation)
        } else {
            stringResource(R.string.pdr_sensor_heading_unavailable)
        }
        Text(
            stringResource(R.string.pdr_sensor_line, stepSensorText, headingSensorText),
            fontFamily = Mono, fontSize = 8.sp, color = Gray,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.pdr_distance_note),
            fontFamily = Sans, fontSize = 8.sp, color = Gray.copy(alpha = 0.85f), lineHeight = 11.sp,
        )
    }
}

/** 告警条 —— 硬件缺失 / 权限 / 注册失败时给出明确原因,不静默降级 */
@Composable
private fun PdrWarningStrip(state: PdrState) {
    val error = state.error?.removePrefix("[!] ")?.trim().orEmpty()
    // manager 错误文本若已涵盖该项(精确相等,与语言无关),fallback 警告条不再重复
    val noStepMsg = stringResource(R.string.mgr_pdr_no_step_sensor)
    val permMsg = stringResource(R.string.mgr_pdr_activity_permission)
    val noRotMsg = stringResource(R.string.mgr_pdr_no_rotation_sensor)
    val items = mutableListOf<Pair<Color, String>>()

    if (!state.hasStepSensor && error != noStepMsg) {
        items += Danger to stringResource(R.string.pdr_warn_no_step_sensor)
    } else if (!state.stepPermissionGranted && error != permMsg) {
        items += Warn to stringResource(R.string.pdr_warn_activity_permission)
    }
    if (!state.hasHeadingSensor && error != noRotMsg) {
        items += Danger to stringResource(R.string.pdr_warn_no_heading_sensor)
    }
    if (error.isNotEmpty()) items += Danger to error

    items.forEach { (color, text) ->
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = color.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        ) {
            Text(
                stringResource(R.string.pdr_warning_prefix, text),
                fontFamily = Sans,
                fontSize = 10.sp,
                color = color,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

/** 诚实说明 —— 死推算的漂移边界与适用场景 */
@Composable
private fun HonestyPanel() {
    TermPanel(stringResource(R.string.pdr_honesty_panel)) {
        Text(
            stringResource(R.string.pdr_honesty_p1),
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.pdr_honesty_p2),
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.pdr_honesty_p3),
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
    }
}
