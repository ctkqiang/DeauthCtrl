package xin.ctkqiang.deauthctrl.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xin.ctkqiang.deauthctrl.ui.screens.PreviewTheme

// ─── 全息战术 HUD · 设计系统 ─────────────────────────────────────────────────
//
// 视觉语言：全息青蓝 + 深海军蓝。青蓝色全息投影、斜切角面板、细发光边框、
// 扫描环与数据流，像一台握在手里的战术全息设备。
//
// 对比度纪律：
// - 正文用 White / Red / Gold，与 SurfaceBg 对比度均 ≥ 4.5:1
// - RedDim 只用于装饰，不允许承载需要被读到的文字
// - 错误 / 失败 / 告警一律使用 Danger，不使用主色青

/** 全息青 —— 主色：按钮、标题、强调、边框 */
val Red = Color(0xFF35C9FF)

/** 亮青 —— 运行中名称、告警强调 */
val RedBright = Color(0xFF7FE7FF)

/** 深青 —— 仅装饰用途 */
val RedDim = Color(0xFF0E5C7A)

/** 危险红 —— 错误 / 失败 / 危险状态 */
val Danger = Color(0xFFFF5A5A)

/** 琥珀橙 —— 警告 / 敌目标 */
val Warn = Color(0xFFFFB547)

/** 淡青 —— 关键数值、核心标记 */
val Gold = Color(0xFF9BD7FF)

/** 暗青 —— 装饰与未激活标记 */
val GoldDim = Color(0xFF2E5E7E)

/** 电光青 —— 运行状态、锁定框、实时数据 */
val TacGreen = Color(0xFF45E0FF)

/** 电光青暗 —— 弱信号 / 未激活 */
val TacGreenDim = Color(0xFF0E4E6E)

/** 正文白 */
val White = Color(0xFFE8F4FF)

/** 次要文字灰 */
val Gray = Color(0xFF7E93AD)

/** 三级文字灰 —— 仅装饰 */
val GrayDark = Color(0xFF4C6078)

/** 页面最底层背景 —— 深海军蓝 */
val Dark = Color(0xFF060B14)

/** 卡片与面板底色 —— 玻璃面板 */
val SurfaceBg = Color(0xFF0B1522)

/** 输入框与图标底 */
val SurfaceAlt = Color(0xFF101E30)

/** 暗色边框与分隔线 */
val BorderDim = Color(0xFF1B3A5C)

/** 全应用统一等宽字体 —— 数据、命令、日志 */
val Mono = FontFamily.Monospace

/** 中文界面字体 —— 标题、说明、按钮 */
val Sans = FontFamily.Default

// ─── 菱形 HUD 标记 ───────────────────────────────────────────────────────────

/**
 * 目标锁定框（四角括号）
 *
 * 战术 HUD 的核心符号：四角括号 = 目标已被锁定。
 * 用于运行中的模块卡片与雷达目标高亮。
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTacticalCorners(
    color: Color,
    size: Size,
    len: Float,
    stroke: Float,
) {
    val w = size.width; val h = size.height
    // 左上
    drawLine(color, Offset(0f, len), Offset(0f, 0f), stroke)
    drawLine(color, Offset(0f, 0f), Offset(len, 0f), stroke)
    // 右上
    drawLine(color, Offset(w - len, 0f), Offset(w, 0f), stroke)
    drawLine(color, Offset(w, 0f), Offset(w, len), stroke)
    // 左下
    drawLine(color, Offset(0f, h - len), Offset(0f, h), stroke)
    drawLine(color, Offset(0f, h), Offset(len, h), stroke)
    // 右下
    drawLine(color, Offset(w - len, h), Offset(w, h), stroke)
    drawLine(color, Offset(w, h), Offset(w, h - len), stroke)
}

/** 目标锁定框 Composable —— 四角括号叠加层，配合 [ModuleCard] 运行态使用 */
@Composable
fun TacticalCorners(color: Color, modifier: Modifier = Modifier, len: Dp = 9.dp, stroke: Dp = 1.5.dp) {
    Canvas(modifier) {
        drawTacticalCorners(color, size, len.toPx(), stroke.toPx())
    }
}

/**
 * 菱形 HUD 标记
 *
 * Canvas 手绘菱形（四顶点闭合 Path）：上下左右四个顶点连成瞄准标记，
 * 是全息界面里的状态指示、分区标记与分隔符。
 */
@Composable
fun Star(color: Color, starSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(starSize)) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        path.moveTo(cx, cy - r)
        path.lineTo(cx + r, cy)
        path.lineTo(cx, cy + r)
        path.lineTo(cx - r, cy)
        path.close()
        drawPath(path, color)
    }
}

/**
 * 全息设备核心
 *
 * 全息投影设备的核心图形：静态外环 + 缓慢旋转的虚线内环 +
 * 中心菱形 + 约 2.5 秒一轮向外扩散的波纹环。
 */
@Composable
fun StarBadge(size: Dp, modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition()
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)))
    val sweepDeg by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2500, easing = LinearEasing)))

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f
            val phase = (sweepDeg / 360f) % 1f
            // 全息玻璃底光
            drawCircle(Red.copy(alpha = 0.06f), r * 0.88f, c)
            // 静态外环
            drawCircle(Red.copy(alpha = 0.35f), r * 0.94f, c, style = Stroke(1.dp.toPx()))
            // 向外扩散的波纹环：约 2.5 秒一轮，越远越淡
            drawCircle(
                Red.copy(alpha = (1f - phase) * 0.40f),
                r * (0.30f + phase * 0.65f),
                c,
                style = Stroke(1.4.dp.toPx()),
            )
        }
        // 内圈虚线环缓慢旋转
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f
            drawCircle(
                Red.copy(alpha = 0.55f),
                r * 0.74f,
                c,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                ),
            )
        }
        Star(Gold, size * 0.52f)
    }
}

/**
 * 菱形脉冲指示 —— 运行状态指示
 *
 * 运行中：双层电光青光晕 + 菱形呼吸脉冲；停止：静态暗青菱形。
 */
@Composable
fun PulseStar(active: Boolean, size: Dp = 9.dp, modifier: Modifier = Modifier) {
    if (!active) {
        Star(GoldDim.copy(alpha = 0.55f), size, modifier)
        return
    }
    val pulse = rememberInfiniteTransition()
    val alpha by pulse.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    val scale by pulse.animateFloat(0.82f, 1.12f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse))

    Box(modifier.size(size * 2.4f), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 2.4f).graphicsLayer { this.alpha = alpha * 0.20f }.clip(CircleShape).background(TacGreen))
        Box(Modifier.size(size * 1.5f).graphicsLayer { this.alpha = alpha * 0.38f }.clip(CircleShape).background(TacGreen))
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            Star(TacGreen, size)
        }
    }
}

// ─── 页面骨架 ────────────────────────────────────────────────────────────────

/**
 * 终端风格页面头部
 *
 * 左侧「‹ 返回」、中间中文标题、右侧终端提示符与闪烁光标。
 * 终端提示符是这套 UI 里保留的最后一抹命令行血脉。
 */
@Composable
fun TerminalHeader(title: String, back: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val cursorBlink = rememberInfiniteTransition()
    val cursorAlpha by cursorBlink.animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse))
    val backInteraction = remember { MutableInteractionSource() }
    val backPressed by backInteraction.collectIsPressedAsState()
    val backScale by animateFloatAsState(if (backPressed) 0.92f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f))

    Row(
        Modifier.fillMaxWidth().statusBarsPadding().background(SurfaceBg)
            .border(0.5.dp, BorderDim).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.scale(backScale).clickable(interactionSource = backInteraction, indication = LocalIndication.current) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                back()
            },
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = Red.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Gold.copy(alpha = 0.45f)),
        ) {
            Text("‹ 返回", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
        Text("root@deauth:~#", fontFamily = Mono, fontSize = 10.sp, color = Gray)
        Text("▊", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold.copy(alpha = cursorAlpha))
    }
}

/**
 * 区域标签
 *
 * `▸ 侦察 ────`：前导箭头 + 白色粗体标题 + 延伸至右边缘的青色细线。
 */
@Composable
fun SectionLabel(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("▸ ", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Red)
        Text(text, fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = White)
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Red.copy(alpha = 0.35f)))
    }
}

/**
 * 终端面板
 *
 * 标题条：菱形标记 + 青字标题；内容区：深蓝玻璃底。面板是作战地图上的区块。
 */
@Composable
fun TermPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .background(SurfaceBg)
            .border(1.dp, BorderDim, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Red.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Star(Gold, 8.dp)
            Spacer(Modifier.width(7.dp))
            Text("▸ $title", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red)
        }
        Column(Modifier.padding(12.dp)) { content() }
    }
}

// ─── 输入与按钮 ──────────────────────────────────────────────────────────────

/**
 * 终端风格输入框
 *
 * 顶部中文标签 + 斜切角输入框。输入内容用等宽字体，光标淡青。
 */
@Composable
fun TermInput(label: String, value: String, placeholder: String = "", modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    Column(modifier) {
        Text(label, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = White),
            cursorBrush = SolidColor(Gold),
            modifier = Modifier
                .fillMaxWidth()
                .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                .background(SurfaceAlt)
                .border(1.dp, BorderDim, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontFamily = Sans, fontSize = 12.sp, color = Gray.copy(alpha = 0.5f))
                }
                inner()
            },
        )
    }
}

/**
 * 主操作按钮（二态）
 *
 * - 空闲：全息青底 + 亮青边 + 白字「▶ 启动」
 * - 运行中：青色微光底 + 呼吸亮边 + 菱形脉冲 + 青字「■ 停止」
 */
@Composable
fun ActionButton(
    running: Boolean,
    runLabel: String,
    stopLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.45f, stiffness = 520f))
    // 按下时文字随按钮轻微下沉，与缩放叠加
    val textShift by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 520f))

    val breath = rememberInfiniteTransition()
    val borderAlpha by breath.animateFloat(0.35f, 0.95f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse))

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
        color = if (running) TacGreen.copy(alpha = 0.08f) else Red,
        border = BorderStroke(1.5.dp, if (running) TacGreen.copy(alpha = borderAlpha) else Gold.copy(alpha = 0.9f)),
    ) {
        Row(
            Modifier.padding(vertical = 13.dp).graphicsLayer { translationY = textShift.dp.toPx() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (running) {
                PulseStar(active = true, size = 6.dp)
                Spacer(Modifier.width(8.dp))
                Text("■ $stopLabel", fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TacGreen)
            } else {
                Text("▶ $runLabel", fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = White)
            }
        }
    }
}

/** 次级操作按钮 —— 青边青字，用于「选择文件」「清空」等次要动作 */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f))

    Surface(
        modifier = modifier.scale(scale).clickable(interactionSource = interaction, indication = LocalIndication.current) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = Red.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Red.copy(alpha = 0.45f)),
    ) {
        Text(text, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), textAlign = TextAlign.Center)
    }
}

/**
 * 选项芯片
 *
 * 选中态：青底白字；未选中：透明底灰字暗框。
 */
@Composable
fun SelectChip(selected: Boolean, text: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    // 选中态切换用 200ms 颜色渐变，底 / 边 / 文字同步过渡
    val containerColor by animateColorAsState(if (selected) Red else Color.Transparent, tween(200))
    val borderColor by animateColorAsState(if (selected) Gold.copy(alpha = 0.7f) else BorderDim, tween(200))
    val contentColor by animateColorAsState(if (selected) White else Gray, tween(200))
    Surface(
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(text, fontFamily = Sans, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = contentColor, modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp))
    }
}

// ─── 状态指示 ────────────────────────────────────────────────────────────────

/** 键值统计芯片 —— 状态面板内竖排「值 / 标签」，值为电光青 */
@Composable
fun StatChip(label: String, value: String, active: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(value, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (active) TacGreen else Gray)
        Spacer(Modifier.height(1.dp))
        Text(label, fontFamily = Sans, fontSize = 9.sp, color = Gray)
    }
}

/** 统计行 —— 面板内「标签 … 值」两端对齐行 */
@Composable
fun PanelStat(label: String, value: String, valueColor: Color = White) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Sans, fontSize = 11.sp, color = Gray)
        Text(value, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

// ─── 日志与提示 ──────────────────────────────────────────────────────────────

/**
 * 日志区头部
 *
 * 菱形标记 + 「战地日志」标题 + 右侧可选「清空」按钮。
 */
@Composable
fun LogHeader(title: String = "战地日志", showClear: Boolean = false, onClear: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Star(Gold, 8.dp)
        Spacer(Modifier.width(7.dp))
        Text(title, fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.weight(1f))
        if (showClear) {
            GhostButton("清空") { onClear() }
        }
    }
}

/** 空状态提示 —— 深色斜切卡片 + 菱形标记 + 中文引导 */
@Composable
fun EmptyHint(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp).clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .background(SurfaceBg).border(1.dp, BorderDim, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Star(Gold, 9.dp)
        Spacer(Modifier.width(9.dp))
        Text(text, fontFamily = Sans, fontSize = 11.sp, color = Gray)
    }
}

/** 日志行 —— `[时间] 内容` 格式，时间灰色、内容按需着色 */
@Composable
fun LogLine(timestamp: String, text: String, textColor: Color = White) {
    Text(
        "[$timestamp]  $text",
        fontFamily = Mono,
        fontSize = 10.sp,
        color = textColor,
        modifier = Modifier.padding(vertical = 1.5.dp),
    )
}

// ─── 主页模块卡片 ────────────────────────────────────────────────────────────

/**
 * 运行态图标呼吸缩放
 *
 * 运行中：图标以 1.2 秒为周期在 1f ↔ 1.15f 之间循环缩放；
 * 停止时恒为 1f，不创建无限动画。
 */
@Composable
private fun iconBreathScale(running: Boolean): Float {
    if (!running) return 1f
    val breath = rememberInfiniteTransition()
    val scale by breath.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    )
    return scale
}

/**
 * 主页功能模块卡片
 *
 * 左：图标底（运行中电光青微光）；中：名称（运行中带菱形脉冲）+ 中文描述；
 * 右：「启动 / 停止」二态按钮或「进入 ›」。
 *
 * ## 动画
 * - 入场：delayMs 延迟后 fade + 上滑（级联节奏）
 * - 按压：spring 弹性缩放，卡片与右侧按钮使用独立交互源
 * - 状态切换：边框 / 背景 / 文字颜色 350ms 平滑过渡
 * - 运行态：图标 1.2 秒呼吸缩放，四角锁定框呼吸闪烁
 */
@Composable
fun ModuleCard(
    name: String,
    desc: String,
    running: Boolean,
    icon: ImageVector,
    delayMs: Long = 0L,
    onClick: () -> Unit,
    onToggle: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (delayMs > 0) delay(delayMs); visible = true }

    val haptic = LocalHapticFeedback.current
    val cardInteraction = remember { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(if (cardPressed) 0.975f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f))
    val actionInteraction = remember { MutableInteractionSource() }
    val actionPressed by actionInteraction.collectIsPressedAsState()
    val actionScale by animateFloatAsState(if (actionPressed) 0.92f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f))

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(350)) + slideInVertically(tween(400)) { it / 4 }) {
        val border by animateColorAsState(if (running) TacGreen.copy(alpha = 0.5f) else BorderDim, tween(350))
        val bg by animateColorAsState(if (running) TacGreen.copy(alpha = 0.05f) else SurfaceBg, tween(350))
        val descColor by animateColorAsState(if (running) TacGreenDim else Gray, tween(350))
        val iconTint by animateColorAsState(if (running) TacGreen else Gray, tween(350))
        val iconBreath = iconBreathScale(running)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .scale(cardScale)
                .clickable(interactionSource = cardInteraction, indication = LocalIndication.current) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .padding(vertical = 5.dp),
            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
            color = bg,
            border = BorderStroke(1.dp, border),
        ) {
            Box {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                        .background(if (running) TacGreen.copy(alpha = 0.14f) else SurfaceAlt)
                        .border(1.dp, if (running) TacGreen.copy(alpha = 0.5f) else BorderDim, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = name, tint = iconTint, modifier = Modifier.size(20.dp).graphicsLayer { scaleX = iconBreath; scaleY = iconBreath })
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = White)
                        if (running) {
                            Spacer(Modifier.width(7.dp))
                            PulseStar(active = true, size = 6.dp)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(desc, fontFamily = Sans, fontSize = 11.sp, color = descColor)
                }
                Spacer(Modifier.width(10.dp))
                if (onToggle == null) {
                    Surface(
                        modifier = Modifier.scale(actionScale).clickable(interactionSource = actionInteraction, indication = LocalIndication.current) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()
                        },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Text("进入 ›", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gray, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                } else {
                    Surface(
                        modifier = Modifier.scale(actionScale).clickable(interactionSource = actionInteraction, indication = LocalIndication.current) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                        },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = if (running) Color.Transparent else Red,
                        border = BorderStroke(1.dp, if (running) TacGreen.copy(alpha = 0.6f) else Gold.copy(alpha = 0.9f)),
                    ) {
                        Text(if (running) "中止" else "执行", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (running) TacGreen else White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                    }
                }
            }
            // 目标锁定框：运行中的模块 = 被锁定的目标
            if (running) {
                val lock = rememberInfiniteTransition()
                val lockAlpha by lock.animateFloat(0.35f, 0.95f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse))
                TacticalCorners(TacGreen.copy(alpha = lockAlpha), Modifier.matchParentSize(), len = 8.dp, stroke = 1.4.dp)
            }
            }
        }
    }
}

// ─── Android Studio 预览 ──────────────────────────────────────────────────────
//
// 组件级预览不依赖 ViewModel，静态预览（Layoutlib）即可直接渲染。
// 屏幕级预览见 ui/screens/MainScreen.kt 与 DetailScreens.kt 文件底部。

/** 全息核心与标记预览 */
@Preview(name = "全息核心与标记", showBackground = true, backgroundColor = 0xFF060B14)
@Composable
private fun BadgePreview() {
    PreviewTheme {
        Column(Modifier.background(Dark).fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            StarBadge(76.dp)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Star(Gold, 12.dp)
                Spacer(Modifier.width(12.dp))
                PulseStar(active = true, size = 10.dp)
                Spacer(Modifier.width(12.dp))
                PulseStar(active = false, size = 10.dp)
                Spacer(Modifier.width(12.dp))
                Star(Red, 12.dp)
                Spacer(Modifier.width(12.dp))
                Star(GoldDim, 12.dp)
            }
        }
    }
}

/** 页面头部预览 */
@Preview(name = "页面头部", showBackground = true, backgroundColor = 0xFF060B14, widthDp = 393)
@Composable
private fun HeaderPreview() {
    PreviewTheme {
        Column(Modifier.background(Dark).fillMaxSize()) {
            TerminalHeader("BLE 泛洪引擎", back = {})
            Spacer(Modifier.weight(1f))
        }
    }
}

/** 组件画廊预览 —— 按钮 / 卡片 / 面板 / 输入 / 日志一屏看全 */
@Preview(name = "组件画廊", showBackground = true, backgroundColor = 0xFF060B14, widthDp = 393, heightDp = 852)
@Composable
private fun KitPreview() {
    PreviewTheme {
        Column(Modifier.background(Dark).padding(14.dp).verticalScroll(rememberScrollState())) {
            SectionLabel("侦察")
            ModuleCard("ARP 扫描", "扫描局域网在线设备", running = false, icon = Icons.Outlined.Router, onClick = {})
            ModuleCard("BLE 扫描", "嗅探附近低功耗蓝牙设备", running = true, icon = Icons.Outlined.BluetoothSearching, onClick = {}, onToggle = {})
            ModuleCard("CVE 搜索", "查询公开漏洞数据库", running = false, icon = Icons.Outlined.Search, onClick = {})
            Spacer(Modifier.height(14.dp))
            ActionButton(running = false, runLabel = "启动泛洪", stopLabel = "停止泛洪") {}
            Spacer(Modifier.height(8.dp))
            ActionButton(running = true, runLabel = "启动泛洪", stopLabel = "停止泛洪") {}
            Spacer(Modifier.height(14.dp))
            TermPanel("运行统计") {
                PanelStat("已发送", "1024")
                PanelStat("失败", "3", valueColor = Danger)
                PanelStat("运行时长", "00:42", valueColor = Gold)
            }
            Spacer(Modifier.height(14.dp))
            TermInput("目标主机", value = "", placeholder = "例如 8.8.8.8 或 baidu.com") {}
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(selected = true, text = "低功率") {}
                SelectChip(selected = false, text = "中功率") {}
                SelectChip(selected = false, text = "高功率") {}
            }
            Spacer(Modifier.height(14.dp))
            LogHeader(showClear = true) {}
            LogLine("22:15:03", "BLE 泛洪已启动", Gold)
            LogLine("22:15:04", "数据包发送成功", TacGreen)
            LogLine("22:15:05", "数据包发送失败", Danger)
            Spacer(Modifier.height(14.dp))
            EmptyHint("点击上方按钮开始扫描，在线设备会实时列出")
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChip("发送", "128")
                StatChip("接收", "127")
                StatChip("丢包", "0%")
            }
        }
    }
}
