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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import xin.ctkqiang.deauthctrl.manager.MainsWireDetector
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.PanelStat
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.MainsWireViewModel

/**
 * 墙内电线探测 —— 工频(50/60Hz)电磁 / 声学特征
 *
 * 麦克风实时采样 → Goertzel 检测工频基波与谐波 → 与整帧 RMS 的比值
 * 经自适应基线归一为 0-100 电平。靠近有负载的带电线路时电平与告警
 * 会明显抬升;探测的是 50/60Hz 特征,不是透视,空载线路不可见。
 */
@Composable
fun MainsWireScreen(back: () -> Unit) {
    val vm: MainsWireViewModel = viewModel()
    val state by vm.state.collectAsState()

    // 进入页面即开始采集,离开页面释放麦克风;按钮保留手动启停
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }

    val levelColor = when {
        !state.running -> Gray
        state.alert -> Danger
        state.level >= 80 -> Warn
        else -> TacGreen
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("电线探测 · MAINS FINDER", back)
        Column(
            Modifier.fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            // ── 控制 ──────────────────────────────────────────
            TermPanel("探测控制 · DETECTOR") {
                PanelStat(
                    "状态",
                    if (state.running) "探测中" else "已停止",
                    if (state.running) TacGreen else Gray,
                )
                PanelStat("工频模式", "${state.freqMode} Hz · 谐波 ${state.freqMode * 2} Hz", Gold)
                PanelStat("采集参数", "${state.sampleRateHz} Hz · ${state.frameSamples} 样本/帧", White)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectChip(state.freqMode == 50, "50 Hz 工频") { vm.setFreqMode(50) }
                    SelectChip(state.freqMode == 60, "60 Hz 工频") { vm.setFreqMode(60) }
                }
                Text(
                    "切换模式会清空基线并重新校准 2 秒。中国 / 欧洲为 50 Hz,北美 / 日本部分地区为 60 Hz。",
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    running = state.running,
                    runLabel = "启动探测",
                    stopLabel = "停止探测",
                ) {
                    if (state.running) vm.stop() else vm.start()
                }
                state.error?.let {
                    Text(
                        "[!] $it",
                        fontFamily = Sans,
                        fontSize = 9.sp,
                        color = Danger,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (state.running && state.calibrating) {
                    Text(
                        "基线校准中 —— 保持手机静止 2 秒并远离墙面,先采集当前环境本底。",
                        fontFamily = Sans,
                        fontSize = 9.sp,
                        color = Gold,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }

            // ── 实时电平 ──────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel("实时电平 · LIVE LEVEL") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "LEVEL",
                        fontFamily = Sans,
                        fontSize = 10.sp,
                        color = Gray,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${state.level}",
                        fontFamily = Mono,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = levelColor,
                    )
                    Text(" / 100", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .height(30.dp)
                        .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, BorderDim, CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)),
                ) {
                    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                        val segs = 24
                        val gap = 2.dp.toPx()
                        val segW = ((size.width - gap * (segs - 1)) / segs).coerceAtLeast(1f)
                        val lit = (state.level / 100f * segs).roundToInt().coerceIn(0, segs)
                        for (i in 0 until segs) {
                            drawRect(
                                color = if (i < lit) levelColor else BorderDim.copy(alpha = 0.35f),
                                topLeft = Offset(i * (segW + gap), 0f),
                                size = Size(segW, size.height),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                PanelStat(
                    "工频特征强度",
                    "%.3f".format(state.strength),
                    if (state.alert) Danger else Gold,
                )
                PanelStat(
                    "环境基线",
                    if (state.calibrating) "校准中" else "${state.baselineLevel} / 100",
                    Gray,
                )
                PanelStat("整帧 RMS", state.rmsDb?.let { "%.1f dBFS".format(it) } ?: "--", White)
                PanelStat(
                    "${state.freqMode} Hz 基波",
                    state.fundDb?.let { "%.1f dBFS".format(it) } ?: "--",
                    White,
                )
                PanelStat(
                    "${state.freqMode * 2} Hz 谐波",
                    state.harmDb?.let { "%.1f dBFS".format(it) } ?: "--",
                    White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "特征强度 = 基波与谐波合成幅度 / 整帧 RMS;电平条按 20·log10 压缩到 0-100,便于观察弱分量。",
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 12.sp,
                )
            }

            // ── 告警横幅 ──────────────────────────────────────
            if (state.alert) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Danger.copy(alpha = 0.14f),
                    border = BorderStroke(1.5.dp, Danger.copy(alpha = 0.75f)),
                ) {
                    Text(
                        "[!] 检测到强工频电磁场 —— 墙内可能有带电线路(仅探测有负载回路)",
                        fontFamily = Sans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Danger,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            // ── 历史火花线 ────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel("历史火花线 · HISTORY (最近 ${MainsWireDetector.HISTORY_SIZE} 帧)") {
                if (state.history.size < 2) {
                    EmptyHint("样本积累中 —— 启动后每帧电平会在此滚动绘制")
                } else {
                    val history = state.history
                    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                        // 5 条等分水平网格
                        for (i in 0..4) {
                            val y = size.height * i / 4f
                            drawLine(
                                Gray.copy(alpha = 0.12f),
                                Offset(0f, y),
                                Offset(size.width, y),
                                0.7f,
                            )
                        }
                        val stepX = size.width / (history.size - 1).coerceAtLeast(1)
                        val pts = history.mapIndexed { i, v ->
                            Offset(
                                i * stepX,
                                size.height * (1f - v.coerceIn(0, 100) / 100f),
                            )
                        }
                        val path = Path().apply {
                            pts.forEachIndexed { i, p ->
                                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                            }
                        }
                        // 磷光层 + 主线
                        drawPath(path, levelColor.copy(alpha = 0.22f), style = Stroke(width = 5f))
                        drawPath(path, levelColor, style = Stroke(width = 1.4f))
                        val last = pts.last()
                        drawCircle(levelColor.copy(alpha = 0.30f), 6f, last)
                        drawCircle(White.copy(alpha = 0.9f), 2f, last)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "MIN ${history.minOrNull() ?: 0}",
                            fontFamily = Mono,
                            fontSize = 8.sp,
                            color = Gray,
                        )
                        Text(
                            "当前 ${state.level}",
                            fontFamily = Mono,
                            fontSize = 8.sp,
                            color = levelColor,
                        )
                        Text(
                            "MAX ${history.maxOrNull() ?: 0}",
                            fontFamily = Mono,
                            fontSize = 8.sp,
                            color = Gray,
                        )
                    }
                }
            }

            // ── 使用说明 ──────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel("使用说明 · HOW TO") {
                MainsBullet("启动后先远离墙面 2 秒,等基线校准完成;基线代表当前环境本底。")
                MainsBullet("把手机背面 / 麦克风贴近墙面约 2-5 厘米,沿墙面缓慢移动,观察电平与火花线变化。")
                MainsBullet("被测线路必须带负载:开灯、充电或接入用电器。空载线路没有电流,不产生可测工频特征。")
                MainsBullet("发现峰值后在该点做十字交叉复测,峰值连线的走向才更接近线路路径。")
                MainsBullet("手机壳、手部与金属物体会改变声场与电场分布;测量时尽量保持同一握持姿势。")
            }

            // ── 原理与边界 ────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel("原理与边界 · LIMITS") {
                Text(
                    "探测的是麦克风捕捉到的 50/60 Hz 基波与谐波(声学嗡鸣 + 手机模拟前端的电磁拾取)," +
                        "属于电磁 / 声学特征检测,不是透视,不能看见墙内线路。",
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = White,
                    lineHeight = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "手机麦克风对 50 Hz 的低频响应普遍较差,系统降噪 / 自动增益也可能削弱该分量," +
                        "不同机型的灵敏度差异很大;读数只适合同一台设备上做相对比较。",
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "无负载(空载、开关断开、断电器)的线路几乎不产生可测工频场 —— 探测不到不代表墙内没有电线。",
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Warn,
                    lineHeight = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "所有读数来自 AudioRecord 实时采样与本地 Goertzel 计算,未做任何模拟或补值;读不到时显示「--」。",
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 12.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 说明条目 —— 菱形箭头 + 正文,保持终端 HUD 的排版节奏 */
@Composable
private fun MainsBullet(text: String) {
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
            color = White,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
