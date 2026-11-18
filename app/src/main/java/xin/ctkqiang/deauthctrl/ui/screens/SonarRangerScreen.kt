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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.manager.SonarRangerManager
import xin.ctkqiang.deauthctrl.manager.SonarState
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.GoldDim
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.PanelStat
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.StarBadge
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.SonarRangerViewModel
import kotlin.math.roundToInt

/**
 * 声呐测距 —— 扬声器 chirp + 麦克风回波匹配滤波
 *
 * 页面进入即开始连续测量,离开时释放 AudioTrack / AudioRecord。
 * 大读数显示 EMA 平滑后的距离;本轮无回波或超量程时显示 "--",
 * 不会把上一轮的值冒充当前测量。下方遥测展示直达声时延、峰值/均值比、
 * 媒体音量等真实系统返回值,读不到时显示 "--"。
 */
@Composable
fun SonarRangerScreen(back: () -> Unit) {
    val vm: SonarRangerViewModel = viewModel()
    val state by vm.state.collectAsState()

    // 进入页面启动测量,离开页面停止并释放音频设备;onCleared 再兜底
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("声呐测距 · SONAR", back)
        Column(
            Modifier.fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            state.error?.let { message ->
                SonarErrorBanner(message)
                Spacer(Modifier.height(8.dp))
            }

            SonarReadoutPanel(
                state = state,
                onToggle = { if (state.running) vm.stop() else vm.start() },
            )
            Spacer(Modifier.height(8.dp))
            SonarHistoryPanel(state)
            Spacer(Modifier.height(8.dp))
            SonarTelemetryPanel(state)
            Spacer(Modifier.height(8.dp))
            SonarHonestyPanel()
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ─── 主读数 ─────────────────────────────────────────────────────────────────

/** 距离大读数 + 置信度条 + 启停按钮 */
@Composable
private fun SonarReadoutPanel(state: SonarState, onToggle: () -> Unit) {
    val distance = state.distanceM
    // 提升为局部 val,保证跨模块属性也能安全智能转换
    val error = state.error
    val reading: String
    val readingColor: Color
    val statusText: String
    val statusColor: Color

    when {
        !state.running -> {
            reading = "--"
            readingColor = Gray
            statusText = if (error != null) "异常停止 —— 见上方告警" else "已停止 · 点击下方按钮启动"
            statusColor = if (error != null) Danger else Gray
        }

        error != null -> {
            reading = "--"
            readingColor = Danger
            statusText = error
            statusColor = Danger
        }

        state.outOfRange -> {
            reading = ">8.0"
            readingColor = Warn
            statusText = ">8m(超量程) —— 回波超出手机扬声器可靠量程"
            statusColor = Warn
        }

        state.noEcho -> {
            reading = "--"
            readingColor = Warn
            statusText = "无回波(吸音环境/角度不对)"
            statusColor = Warn
        }

        distance != null -> {
            reading = "%.1f".format(distance)
            readingColor = TacGreen
            statusText = "回波锁定 · 已累计 ${state.validCycles} 次有效测量"
            statusColor = TacGreen
        }

        else -> {
            reading = "--"
            readingColor = Gold
            statusText = "测量中 —— 首轮回波约 1.5 秒"
            statusColor = Gold
        }
    }

    TermPanel("距离读数 · RANGE") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DISTANCE / m", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    color = SurfaceAlt,
                    border = BorderStroke(1.dp, BorderDim),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            reading,
                            fontFamily = Mono,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = readingColor,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "m",
                            fontFamily = Mono,
                            fontSize = 10.sp,
                            color = Gray,
                            modifier = Modifier.padding(bottom = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    statusText,
                    fontFamily = Sans,
                    fontSize = 9.sp,
                    color = statusColor,
                    lineHeight = 13.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            // 运行中的全息核心;停止时只留静态菱形,避免无意义的动画
            Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                if (state.running) {
                    StarBadge(56.dp)
                } else {
                    Star(GoldDim, 18.dp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("置信度 CONFIDENCE", fontFamily = Sans, fontSize = 9.sp, color = Gray)
            Text(
                if (state.confidence > 0f) "${(state.confidence * 100f).toInt()}%" else "--",
                fontFamily = Mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = sonarConfidenceColor(state.confidence),
            )
        }
        Spacer(Modifier.height(4.dp))
        SonarConfidenceBar(
            confidence = state.confidence,
            active = state.running && state.error == null && !state.noEcho && !state.outOfRange,
        )
        Spacer(Modifier.height(10.dp))
        ActionButton(running = state.running, runLabel = "启动测距", stopLabel = "停止测距") {
            onToggle()
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "每 1.5 秒发一次 60ms 线性扫频 chirp(3-8kHz);" +
                "距离 = 回波相对直达声的时延 × 343m/s ÷ 2。",
            fontFamily = Sans,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 12.sp,
        )
    }
}

/** 置信度条 —— 32 段,按置信度从左到右点亮 */
@Composable
private fun SonarConfidenceBar(confidence: Float, active: Boolean) {
    val level = if (active) confidence.coerceIn(0f, 1f) else 0f
    val litColor = sonarConfidenceColor(level)
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val segments = 32
        val gap = 2.dp.toPx()
        val segWidth = ((size.width - gap * (segments - 1)) / segments).coerceAtLeast(1f)
        val lit = (level * segments).roundToInt().coerceIn(0, segments)
        for (i in 0 until segments) {
            drawRect(
                color = if (i < lit) litColor else SurfaceAlt,
                topLeft = Offset(i * (segWidth + gap), 0f),
                size = Size(segWidth, size.height),
            )
        }
    }
}

/** 置信度配色:高=电光青,中=琥珀,低=红,0=灰 */
private fun sonarConfidenceColor(confidence: Float): Color = when {
    confidence >= 0.66f -> TacGreen
    confidence >= 0.33f -> Warn
    confidence > 0f -> Danger
    else -> Gray
}

// ─── 历史火花线 ─────────────────────────────────────────────────────────────

/** 最近 40 次有效距离的火花线;无回波 / 超量程周期不进入历史 */
@Composable
private fun SonarHistoryPanel(state: SonarState) {
    TermPanel("回波历史 · HISTORY (最近 ${SonarRangerManager.HISTORY_SIZE} 次)") {
        if (state.history.size < 2) {
            EmptyHint("有效回波不足 —— 历史曲线会随每次有效测量滚动绘制")
        } else {
            val history = state.history
            Canvas(Modifier.fillMaxWidth().height(84.dp)) {
                // 5 条等分水平网格,纵轴对应 0.2-8m
                for (i in 0..4) {
                    val y = size.height * i / 4f
                    drawLine(
                        Gray.copy(alpha = 0.14f),
                        Offset(0f, y),
                        Offset(size.width, y),
                        0.7f,
                    )
                }
                val minM = SonarRangerManager.MIN_RANGE_M
                val maxM = SonarRangerManager.MAX_RANGE_M
                val span = (maxM - minM).coerceAtLeast(0.001f)
                val stepX = size.width / (history.size - 1).coerceAtLeast(1)
                val points = history.mapIndexed { index, value ->
                    val t = ((value - minM) / span).coerceIn(0f, 1f)
                    Offset(index * stepX, size.height * (1f - t))
                }
                val path = Path().apply {
                    points.forEachIndexed { index, point ->
                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                }
                // 磷光层 + 主线 + 最新点
                drawPath(path, TacGreen.copy(alpha = 0.20f), style = Stroke(width = 5f))
                drawPath(path, TacGreen, style = Stroke(width = 1.6f))
                val last = points.last()
                drawCircle(TacGreen.copy(alpha = 0.35f), 6f, last)
                drawCircle(White.copy(alpha = 0.9f), 2f, last)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "MIN ${"%.1f".format(history.minOrNull() ?: 0f)} m",
                    fontFamily = Mono, fontSize = 8.sp, color = Gray,
                )
                Text(
                    "CUR ${"%.1f".format(history.last())} m",
                    fontFamily = Mono, fontSize = 8.sp, color = TacGreen,
                )
                Text(
                    "MAX ${"%.1f".format(history.maxOrNull() ?: 0f)} m",
                    fontFamily = Mono, fontSize = 8.sp, color = Gray,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "纵轴 0.2-8m,只绘制得到有效回波的测量;无回波 / 超量程周期不进入历史。",
                fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp,
            )
        }
    }
}

// ─── 遥测 ───────────────────────────────────────────────────────────────────

/** 真实系统返回值与算法参数;读不到的字段显示 "--" */
@Composable
private fun SonarTelemetryPanel(state: SonarState) {
    val volume = state.mediaVolumePercent
    TermPanel("遥测 · TELEMETRY") {
        PanelStat(
            "状态",
            if (state.running) "连续测量中" else "已停止",
            if (state.running) TacGreen else Gray,
        )
        PanelStat(
            "测量周期",
            "${SonarRangerManager.MEASURE_INTERVAL_MS} ms",
            White,
        )
        PanelStat(
            "扫频 chirp",
            "${SonarRangerManager.CHIRP_START_HZ / 1000f} - " +
                "${SonarRangerManager.CHIRP_END_HZ / 1000f} kHz · ${SonarRangerManager.CHIRP_MS} ms",
            Gold,
        )
        PanelStat(
            "采样 / FFT",
            "${SonarRangerManager.SAMPLE_RATE} Hz · ${SonarRangerManager.FFT_SIZE} 点",
            White,
        )
        PanelStat(
            "直达声时延",
            state.directLagMs?.let { "%.1f ms".format(it) } ?: "--",
            Gold,
        )
        PanelStat(
            "峰值 / 均值",
            if (state.peakRatio > 0f) "%.1f×".format(state.peakRatio) else "--",
            if (state.peakRatio > 0f) TacGreen else Gray,
        )
        PanelStat(
            "有效 / 总周期",
            "${state.validCycles} / ${state.cycles}",
            White,
        )
        PanelStat(
            "媒体音量",
            volume?.let { "$it%" } ?: "--",
            sonarVolumeColor(volume),
        )
        PanelStat("声速折算", "343 m/s · 20°C", Gray)
        if (volume != null && volume < 60) {
            Spacer(Modifier.height(4.dp))
            Text(
                "[!] 媒体音量偏低 —— 扬声器功率不足会明显缩短量程,建议调到 80% 以上",
                fontFamily = Sans, fontSize = 9.sp, color = Warn, lineHeight = 13.sp,
            )
        }
    }
}

/** 媒体音量配色:0=危险,偏低=琥珀,足够=电光青,未知=灰 */
private fun sonarVolumeColor(percent: Int?): Color = when {
    percent == null -> Gray
    percent <= 0 -> Danger
    percent < 60 -> Warn
    else -> TacGreen
}

// ─── 诚实说明 ───────────────────────────────────────────────────────────────

/** 精度声明与物理边界,全部为可验证的工程事实 */
@Composable
private fun SonarHonestyPanel() {
    TermPanel("诚实局限 · HONESTY") {
        SonarBullet(
            "声速按 343m/s(20°C 干燥空气)折算;温度每变化 10°C,声速约变 6m/s,距离约有 1.7% 误差。",
            White,
        )
        SonarBullet(
            "扬声器与麦克风距离极近 —— 直达声被当作 0m 时基,只测直达声之后的回波;" +
                "系统回声消除(AEC)若把直达声抹掉,本页会报「未捕获直达声」,不会编造距离。",
            Gray,
        )
        SonarBullet(
            "多径反射、软表面(窗帘/沙发/吸音板)与斜角度会让回波变弱甚至消失;" +
                "测墙、门、桌面等大平面效果最好。",
            Gray,
        )
        SonarBullet(
            "受手机扬声器功率与 0.5s 录音窗限制,可靠量程约 0.2-8m;" +
                "超过 8m 显示「>8m(超量程)」,强吸音环境显示「无回波(吸音环境/角度不对)」。",
            Gray,
        )
        SonarBullet(
            "蓝牙耳机 / 外接音箱会引入数百毫秒延迟,且麦克风收不到直达声 —— 请使用本机扬声器。",
            Warn,
        )
        SonarBullet(
            "读数是峰值时延的估计值,不是激光测距;适合判断「前方是否有障碍 / 大概多远」," +
                "不适合精密测量。所有读数来自真实采样与本地 FFT 计算,读不到时显示「--」。",
            Warn,
        )
    }
}

/** 说明条目 —— 菱形箭头 + 正文 */
@Composable
private fun SonarBullet(text: String, color: Color) {
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
            color = color,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 错误横幅 —— 失败原因明确展示,不静默降级 */
@Composable
private fun SonarErrorBanner(message: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = Danger.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
    ) {
        Text(
            "[!] $message",
            fontFamily = Sans,
            fontSize = 9.sp,
            color = Danger,
            lineHeight = 13.sp,
            modifier = Modifier.padding(10.dp),
        )
    }
}
