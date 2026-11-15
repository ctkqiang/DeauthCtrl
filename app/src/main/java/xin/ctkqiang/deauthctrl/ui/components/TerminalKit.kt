package xin.ctkqiang.deauthctrl.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ─── 终端风格调色板 ──────────────────────────────────────────────────────────
//
// 全应用唯一一份颜色定义。设计语言：中国红 + 纯黑终端，既保留红客工具的
// 视觉身份，又满足可读性要求。
//
// 对比度纪律：
// - 正文必须用 White / Red / Gray，三者与 SurfaceBg 的对比度都 ≥ 4.5:1
// - RedDim 只用于装饰（分隔线、占位图标），不允许承载需要被读到的文字

/** 主色 —— 标题、按钮、强调文字、运行状态 */
val Red = Color(0xFFFF0000)

/** 亮红 —— 运行中名称、hover 高亮 */
val RedBright = Color(0xFFFF4444)

/** 暗红 —— 仅装饰用途，不可用于正文 */
val RedDim = Color(0xFF991111)

/** 正文白 */
val White = Color(0xFFEEEEEE)

/** 次要文字灰 —— 与 SurfaceBg 对比度约 5.6:1 */
val Gray = Color(0xFF8A8A8A)

/** 三级文字灰 —— 仅用于装饰性文字 */
val GrayDark = Color(0xFF555555)

/** 页面最底层背景 */
val Dark = Color(0xFF000000)

/** 卡片与面板底色 */
val SurfaceBg = Color(0xFF0A0A0A)

/** 输入框与图标底 */
val SurfaceAlt = Color(0xFF151515)

/** 暗色边框与分隔线 */
val BorderDim = Color(0xFF232323)

/** 全应用统一等宽字体（中文自动回退系统 CJK 字体） */
val Mono = FontFamily.Monospace

// ─── 页面骨架 ────────────────────────────────────────────────────────────────

/**
 * 终端风格页面头部
 *
 * 所有详情页统一的标题栏：左侧「‹ 返回」按钮、中间页面标题、
 * 右侧终端提示符 `root@deauth:~#` 与闪烁光标。
 *
 * @param title 页面标题（如 "BLE 泛洪引擎"）
 * @param back 返回回调
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
            shape = RoundedCornerShape(7.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, Red.copy(alpha = 0.45f)),
        ) {
            Text("‹ 返回", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
        Text("root@deauth:~#", fontFamily = Mono, fontSize = 10.sp, color = Gray)
        Text("▊", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red.copy(alpha = cursorAlpha))
    }
}

/**
 * 区域标签
 *
 * 分区标题：红色「▸」前缀 + 白色文字 + 延伸至右边缘的细线。
 * 主页用它做功能分区标题，详情页用它做区块标题。
 */
@Composable
fun SectionLabel(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("▸ ", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Red)
        Text(text, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = White)
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f).height(1.dp).background(BorderDim))
    }
}

/**
 * 终端面板
 *
 * 带标题栏的卡片式面板：红色标题条（红点 + 标题）+ 内容区。
 * 圆角 8dp，比旧版 4dp 更柔和，标题与内容之间层次清晰。
 */
@Composable
fun TermPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceBg)
            .border(1.dp, BorderDim, RoundedCornerShape(8.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Red.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).clip(RoundedCornerShape(2.dp)).background(Red))
            Spacer(Modifier.width(7.dp))
            Text(title, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red)
        }
        Column(Modifier.padding(12.dp)) { content() }
    }
}

// ─── 输入与按钮 ──────────────────────────────────────────────────────────────

/**
 * 终端风格输入框
 *
 * 顶部红色标签 + 圆角输入框。输入时光标为红色，占位文字灰色。
 */
@Composable
fun TermInput(label: String, value: String, placeholder: String = "", modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    Column(modifier) {
        Text(label, fontFamily = Mono, fontSize = 11.sp, color = Red, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = White),
            cursorBrush = SolidColor(Red),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(SurfaceAlt)
                .border(1.dp, BorderDim, RoundedCornerShape(7.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontFamily = Mono, fontSize = 12.sp, color = Gray.copy(alpha = 0.5f))
                }
                inner()
            },
        )
    }
}

/**
 * 主操作按钮（二态）
 *
 * 详情页的主操作入口，两种形态：
 * - 空闲：实心红底白字，显示「启动」类文案
 * - 运行中：透明底红字 + 呼吸边框 + 脉冲圆点，显示「停止」类文案
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

    // 运行中时边框呼吸，暗示"正在进行中"
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
        shape = RoundedCornerShape(8.dp),
        color = if (running) Red.copy(alpha = 0.07f) else Red,
        border = BorderStroke(1.5.dp, if (running) Red.copy(alpha = borderAlpha) else Red),
    ) {
        Row(
            Modifier.padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (running) {
                RunDot(active = true, size = 6.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (running) stopLabel else runLabel, fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White)
        }
    }
}

/** 次级操作按钮 —— 透明底红框红字，用于「选择文件」「清空」等次要动作 */
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
        shape = RoundedCornerShape(7.dp),
        color = Red.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Red.copy(alpha = 0.4f)),
    ) {
        Text(text, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), textAlign = TextAlign.Center)
    }
}

/**
 * 选项芯片
 *
 * 单选配置项（BLE 载荷、循环模式、功率档位、HTTP 方法）。
 * 选中态：微红底 + 红字红框；未选中：透明底灰字暗框。
 */
@Composable
fun SelectChip(selected: Boolean, text: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = RoundedCornerShape(6.dp),
        color = if (selected) Red.copy(alpha = 0.14f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) Red.copy(alpha = 0.7f) else BorderDim),
    ) {
        Text(text, fontFamily = Mono, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) Red else Gray, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

// ─── 状态指示 ────────────────────────────────────────────────────────────────

/**
 * 运行状态脉冲圆点
 *
 * 运行中：双层光晕呼吸动画；停止：静态暗红点。
 * 用于卡片状态指示、面板标题、主按钮。
 */
@Composable
fun RunDot(active: Boolean, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    if (!active) {
        Box(modifier.size(size).clip(CircleShape).background(RedDim.copy(alpha = 0.4f)))
        return
    }
    val pulse = rememberInfiniteTransition()
    val alpha by pulse.animateFloat(0.3f, 1f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    Box(modifier.size(size * 2.6f), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 2.6f).clip(CircleShape).background(Red.copy(alpha = alpha * 0.18f)))
        Box(Modifier.size(size * 1.6f).clip(CircleShape).background(Red.copy(alpha = alpha * 0.42f)))
        Box(Modifier.size(size).clip(CircleShape).background(Red.copy(alpha = 0.85f + 0.15f * alpha)))
    }
}

/** 键值统计芯片 —— 状态面板内竖排「值 / 标签」 */
@Composable
fun StatChip(label: String, value: String, active: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(value, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (active) Red else Gray)
        Spacer(Modifier.height(1.dp))
        Text(label, fontFamily = Mono, fontSize = 9.sp, color = Gray)
    }
}

/** 统计行 —— 面板内「标签 … 值」两端对齐行 */
@Composable
fun PanelStat(label: String, value: String, valueColor: Color = White) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Mono, fontSize = 11.sp, color = Gray)
        Text(value, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

// ─── 日志与提示 ──────────────────────────────────────────────────────────────

/**
 * 日志区头部
 *
 * 红点 + 「实时日志」标题 + 右侧可选「清空」按钮。
 */
@Composable
fun LogHeader(title: String = "实时日志", showClear: Boolean = false, onClear: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(2.dp)).background(Red))
        Spacer(Modifier.width(7.dp))
        Text(title, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.weight(1f))
        if (showClear) {
            GhostButton("清空") { onClear() }
        }
    }
}

/** 空状态提示 —— 灰底卡片 + 红色「›」引导符，告诉用户下一步该做什么 */
@Composable
fun EmptyHint(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp).clip(RoundedCornerShape(8.dp))
            .background(SurfaceBg).border(1.dp, BorderDim, RoundedCornerShape(8.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("›", fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Red)
        Spacer(Modifier.width(8.dp))
        Text(text, fontFamily = Mono, fontSize = 11.sp, color = Gray)
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
 * 主页功能模块卡片
 *
 * 左：圆角图标底（运行中微红发光）；中：名称（运行中带脉冲圆点）+ 中文描述；
 * 右：单个操作位 —— 「启动 / 停止」二态按钮，或「进入 ›」。
 *
 * ## 动画
 * - 入场：delayMs 延迟后 fade + 上滑（级联节奏）
 * - 按压：spring 弹性缩放，卡片与右侧按钮使用独立交互源互不联动
 * - 状态切换：边框 / 背景 / 文字颜色 350ms 平滑过渡
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
        val border by animateColorAsState(if (running) Red.copy(alpha = 0.55f) else BorderDim, tween(350))
        val bg by animateColorAsState(if (running) Red.copy(alpha = 0.055f) else SurfaceBg, tween(350))
        val nameColor by animateColorAsState(if (running) RedBright else White, tween(350))
        val descColor by animateColorAsState(if (running) Red.copy(alpha = 0.55f) else Gray, tween(350))
        val iconTint by animateColorAsState(if (running) Red else Gray, tween(350))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .scale(cardScale)
                .clickable(interactionSource = cardInteraction, indication = LocalIndication.current) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .padding(vertical = 5.dp),
            shape = RoundedCornerShape(10.dp),
            color = bg,
            border = BorderStroke(1.dp, border),
        ) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (running) Red.copy(alpha = 0.16f) else SurfaceAlt)
                        .border(1.dp, if (running) Red.copy(alpha = 0.5f) else BorderDim, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = name, tint = iconTint, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = nameColor)
                        if (running) {
                            Spacer(Modifier.width(7.dp))
                            RunDot(active = true, size = 6.dp)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(desc, fontFamily = Mono, fontSize = 10.5.sp, color = descColor)
                }
                Spacer(Modifier.width(10.dp))
                if (onToggle == null) {
                    Surface(
                        modifier = Modifier.scale(actionScale).clickable(interactionSource = actionInteraction, indication = LocalIndication.current) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()
                        },
                        shape = RoundedCornerShape(7.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Text("进入 ›", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gray, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                } else {
                    Surface(
                        modifier = Modifier.scale(actionScale).clickable(interactionSource = actionInteraction, indication = LocalIndication.current) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                        },
                        shape = RoundedCornerShape(7.dp),
                        color = if (running) Color.Transparent else Red,
                        border = BorderStroke(1.dp, if (running) Red.copy(alpha = 0.5f) else Red),
                    ) {
                        Text(if (running) "停止" else "启动", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                    }
                }
            }
        }
    }
}
