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
import androidx.compose.runtime.remember
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
import xin.ctkqiang.deauthctrl.manager.SEISMO_DEFAULT_THRESHOLD
import xin.ctkqiang.deauthctrl.manager.SEISMO_MAX_EVENTS
import xin.ctkqiang.deauthctrl.manager.SEISMO_MAX_THRESHOLD
import xin.ctkqiang.deauthctrl.manager.SEISMO_MIN_THRESHOLD
import xin.ctkqiang.deauthctrl.manager.SEISMO_WAVEFORM_CAPACITY
import xin.ctkqiang.deauthctrl.manager.SeismoEvent
import xin.ctkqiang.deauthctrl.manager.SeismoState
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
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
import xin.ctkqiang.deauthctrl.viewmodel.SeismographViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 震动事件记录页 —— SEISMOGRAPH
 *
 * ## 数据来源(全部为真实返回值)
 * - 三轴加速度:TYPE_ACCELEROMETER,SENSOR_DELAY_GAME;
 * - 净加速度 = 合加速度 - EMA 重力基准(alpha 0.01),单位 m/s²;
 * - 事件峰值 / 时间 / 持续时长均由传感器样本时间戳直接得出。
 *
 * ## 画面
 * 140dp 滚动波形:中线为 0,上下虚线为 ±阈值,超阈值段变红;
 * 下方是最近 1 秒峰值、阈值滑条与事件日志。
 *
 * ## 诚实边界
 * 这是消费级 MEMS 加速度计,只能感知明显震动,不是专业地震仪,
 * 对远程地震不敏感;没有样本时显示「--」,绝不编造。
 */
@Composable
fun SeismographScreen(back: () -> Unit) {
    val vm: SeismographViewModel = viewModel()
    val state by vm.state.collectAsState()

    // 页面可见期持有加速度计;离开时无条件释放,避免后台耗电
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.shutdown() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("震动监视 · SEISMOGRAPH", back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            SeismoWarningStrip(state)

            // ── 滚动波形 ──────────────────────────────────────────────────
            TermPanel("波形 · WAVEFORM") {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Dark.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, BorderDim),
                ) {
                    SeismoWaveCanvas(state, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(5.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "满幅 ±%.1f m/s²".format(seismoFullScale(state)),
                        fontFamily = Mono, fontSize = 8.sp, color = Gray,
                    )
                    Text(
                        "阈值 %.1f m/s²".format(state.threshold),
                        fontFamily = Mono, fontSize = 8.sp, color = Gold,
                    )
                    Text(
                        "${state.waveform.size} / $SEISMO_WAVEFORM_CAPACITY 样本",
                        fontFamily = Mono, fontSize = 8.sp, color = Gray,
                    )
                }
                if (state.waveform.isEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (state.running) {
                            "等待加速度计首个样本 ..."
                        } else {
                            "监视未启动 —— 点击下方「启动监视」"
                        },
                        fontFamily = Sans, fontSize = 8.sp, color = Gray,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 峰值 + 阈值滑条 ───────────────────────────────────────────
            PeakPanel(state) { vm.setThreshold(it) }

            Spacer(Modifier.height(8.dp))

            ActionButton(
                running = state.running,
                runLabel = "启动监视",
                stopLabel = "停止监视",
            ) {
                if (state.running) vm.stop() else vm.start()
            }

            Spacer(Modifier.height(8.dp))

            // ── 事件日志 ──────────────────────────────────────────────────
            EventLogPanel(state) { vm.clearEvents() }

            Spacer(Modifier.height(8.dp))

            // ── 诚实说明 ──────────────────────────────────────────────────
            SeismoHonestyPanel()

            Spacer(Modifier.height(10.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 波形 Canvas
// ══════════════════════════════════════════════════════════════════════════

/** 波形纵向半幅(m/s²):至少容纳 2.5 倍阈值,并为实际峰值留 25% 余量 */
private fun seismoFullScale(state: SeismoState): Float {
    var peak = 0f
    state.waveform.forEach { sample ->
        val value = abs(sample.net)
        if (value > peak) peak = value
    }
    return maxOf(state.threshold * 2.5f, peak * 1.25f, 0.6f)
}

/**
 * 滚动波形
 *
 * 最新样本贴右边缘,向右滚动;相邻样本线段凡任一端超过阈值即整段画红。
 * 中线为 0,±阈值画琥珀虚线;四角锁定框沿用全息 HUD 语言。
 */
@Composable
private fun SeismoWaveCanvas(state: SeismoState, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val midY = h / 2f
        val full = seismoFullScale(state)
        val usableHalf = (h / 2f - 6.dp.toPx()).coerceAtLeast(1f)
        val yScale = usableHalf / full
        val thresholdY = (state.threshold * yScale).coerceAtMost(usableHalf)

        // 背景纵向刻度
        for (k in 1 until 6) {
            val x = w * k / 6f
            drawLine(BorderDim.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, h), 1f)
        }

        // 零中线
        drawLine(BorderDim.copy(alpha = 0.9f), Offset(0f, midY), Offset(w, midY), 1f)

        // ±阈值虚线
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)
        drawLine(
            Warn.copy(alpha = 0.45f),
            Offset(0f, midY - thresholdY),
            Offset(w, midY - thresholdY),
            1.dp.toPx(),
            pathEffect = dash,
        )
        drawLine(
            Warn.copy(alpha = 0.45f),
            Offset(0f, midY + thresholdY),
            Offset(w, midY + thresholdY),
            1.dp.toPx(),
            pathEffect = dash,
        )

        // 波形:最新点固定在右边缘,样本不足时从右侧向左铺设
        val samples = state.waveform
        val stepX = w / (SEISMO_WAVEFORM_CAPACITY - 1).toFloat()
        if (samples.size >= 2) {
            val startX = w - (samples.size - 1) * stepX
            var prevX = startX
            var prevY = midY - samples[0].net * yScale
            for (i in 1 until samples.size) {
                val x = startX + i * stepX
                val y = midY - samples[i].net * yScale
                val over = abs(samples[i - 1].net) > state.threshold || abs(samples[i].net) > state.threshold
                drawLine(
                    color = if (over) Danger else TacGreen,
                    start = Offset(prevX, prevY),
                    end = Offset(x, y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                prevX = x
                prevY = y
            }
            drawCircle(
                color = if (abs(samples.last().net) > state.threshold) Danger else TacGreen,
                radius = 2.4.dp.toPx(),
                center = Offset(prevX, prevY),
            )
        }

        drawTacticalCorners(TacGreen.copy(alpha = 0.22f), size, 9.dp.toPx(), 1.dp.toPx())
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 读数与阈值
// ══════════════════════════════════════════════════════════════════════════

/**
 * 当前读数 + 阈值滑条
 *
 * 峰值取最近 50 个样本窗口的 |净加速度| 最大值(状态流节流值,非插值;
 * 请求 50Hz 时约 1 秒);瞬时值是状态流中最后一个真实样本。
 * 滑条 0.2-3.0 m/s²,0.1 步进。
 */
@Composable
private fun PeakPanel(state: SeismoState, onThreshold: (Float) -> Unit) {
    val latest = state.waveform.lastOrNull()?.net
    val hasSample = state.waveform.isNotEmpty()

    TermPanel("当前读数 · PEAK") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("最近 50 样本峰值", fontFamily = Sans, fontSize = 10.sp, color = Gray)
                Text(
                    if (state.running && hasSample) "%.2f m/s²".format(state.peakCurrent) else "--",
                    fontFamily = Mono,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        !state.running || !hasSample -> Gray
                        state.peakCurrent > state.threshold -> Danger
                        state.peakCurrent > state.threshold * 0.5f -> Warn
                        else -> TacGreen
                    },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (state.running && latest != null) "瞬时 %+.2f m/s²".format(latest) else "瞬时 --",
                    fontFamily = Mono, fontSize = 10.sp, color = Gold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("告警阈值", fontFamily = Sans, fontSize = 10.sp, color = Gray)
                Text(
                    "%.1f m/s²".format(state.threshold),
                    fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (state.eventActive) "事件中 · 峰值 %.2f".format(state.eventPeak) else "无进行中事件",
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = if (state.eventActive) Danger else Gray,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Slider(
            value = state.threshold,
            onValueChange = onThreshold,
            valueRange = SEISMO_MIN_THRESHOLD..SEISMO_MAX_THRESHOLD,
            steps = 27,
            colors = SliderDefaults.colors(
                thumbColor = Red,
                activeTrackColor = Red,
                inactiveTrackColor = SurfaceAlt,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0.2", fontFamily = Mono, fontSize = 8.sp, color = Gray)
            Text("0.1 m/s² 步进", fontFamily = Mono, fontSize = 8.sp, color = Gray)
            Text("3.0", fontFamily = Mono, fontSize = 8.sp, color = Gray)
        }

        Spacer(Modifier.height(3.dp))
        Text(
            "阈值 %.1f m/s² 为默认值,可按环境噪声调整;阈值以内视为环境噪声,不产生事件。".format(
                SEISMO_DEFAULT_THRESHOLD,
            ),
            fontFamily = Sans, fontSize = 8.sp, color = Gray.copy(alpha = 0.85f), lineHeight = 11.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (state.running && state.gravityBaseline > 0f) {
                "重力基准 %.2f m/s²(EMA 估计) · 传感器 %s".format(
                    state.gravityBaseline,
                    state.sensorName.ifBlank { "--" },
                )
            } else {
                "重力基准 -- · 传感器 " + state.sensorName.ifBlank { "--" }
            },
            fontFamily = Mono, fontSize = 8.sp, color = Gray,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 事件日志
// ══════════════════════════════════════════════════════════════════════════

/** 事件日志 —— 最新在前,最多 50 条;进行中事件单独提示 */
@Composable
private fun EventLogPanel(state: SeismoState, onClear: () -> Unit) {
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    TermPanel("事件日志 · EVENTS") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "已记录 ${state.events.size} / $SEISMO_MAX_EVENTS · 最新在上",
                fontFamily = Sans, fontSize = 9.sp, color = Gray,
                modifier = Modifier.weight(1f),
            )
            GhostButton("清空") { onClear() }
        }

        if (state.eventActive) {
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                color = Warn.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Warn.copy(alpha = 0.40f)),
            ) {
                Text(
                    "[!] 事件进行中 · 当前峰值 %.2f m/s²".format(state.eventPeak),
                    fontFamily = Mono, fontSize = 9.sp, color = Warn,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }

        Spacer(Modifier.height(5.dp))
        if (state.events.isEmpty()) {
            EmptyHint("暂无震动事件 —— 峰值超过阈值的震动会在这里留下时间、峰值与持续时长")
        } else {
            state.events.forEach { event ->
                SeismoEventRow(event, clock.format(Date(event.wallTimeMs)))
            }
        }
    }
}

/** 单条事件:时间 / 峰值 / 持续时长,峰值越大颜色越危险 */
@Composable
private fun SeismoEventRow(event: SeismoEvent, clockText: String) {
    val accent = when {
        event.peak >= 2f -> Danger
        event.peak >= 1f -> Warn
        else -> Gold
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Star(accent, 6.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            clockText,
            fontFamily = Mono, fontSize = 10.sp, color = Gray,
            modifier = Modifier.width(58.dp),
        )
        Text(
            "峰值 %.2f m/s²".format(event.peak),
            fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent,
            modifier = Modifier.weight(1f),
        )
        Text(
            "持续 ${formatSeismoDuration(event.durationMs)}",
            fontFamily = Mono, fontSize = 9.sp, color = White,
        )
    }
}

/** 持续时长展示:0ms 代表事件只跨一个采样间隔,不伪装成精确时长 */
private fun formatSeismoDuration(ms: Long): String = when {
    ms <= 0L -> "< 50 ms"
    ms < 1000L -> "$ms ms"
    else -> "%.1f s".format(ms / 1000f)
}

// ══════════════════════════════════════════════════════════════════════════
// 告警与说明
// ══════════════════════════════════════════════════════════════════════════

/** 告警条 —— 硬件缺失 / 注册失败时给出明确原因,不静默降级 */
@Composable
private fun SeismoWarningStrip(state: SeismoState) {
    val error = state.error?.removePrefix("[!] ")?.trim().orEmpty()
    val items = mutableListOf<Pair<Color, String>>()

    if (!state.hasAccelerometer && !error.contains("加速度计")) {
        items += Danger to "本机未提供加速度计(TYPE_ACCELEROMETER) —— 震动监视不可用"
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
                "[!] $text",
                fontFamily = Sans,
                fontSize = 10.sp,
                color = color,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

/** 诚实说明 —— 传感器精度、去重力盲区与事件判定边界 */
@Composable
private fun SeismoHonestyPanel() {
    TermPanel("精度声明 · HONESTY") {
        Text(
            "手机加速度计是消费级 16 位 MEMS 传感器,采样请求为 SENSOR_DELAY_GAME(系统通常约 50Hz),"
                + "噪声本底、带宽与动态范围都远不如专业地震仪。本页只能可靠捕捉明显震动 —— "
                + "敲门、脚步、桌面被碰、机械振动;对远距离地震不敏感。",
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "净加速度 = 三轴合加速度 - 重力基准。重力基准用 alpha 0.01 的指数滑动平均跟踪,"
                + "缓慢倾斜会被当作零震动,这是去重力算法的固有盲区;合加速度与手机朝向无关。",
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "事件峰值、发生时间与持续时长全部来自传感器真实样本;持续时长为首次与末次越阈样本之差,"
                + "单样本事件显示「< 50 ms」。事件需连续 400ms 低于阈值才判定结束,避免一次震动被切碎。",
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "把手机刚性固定在硬桌面或地面灵敏度最高;放在软垫、衣物上会吸收高频振动。"
                + "读数不足时页面显示「--」,绝不编造;阈值 0.5 m/s² 只是默认值,嘈杂环境应上调。",
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
    }
}
