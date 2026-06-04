package xin.ctkqiang.deauthctrl.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random as KRandom

// ─── Terminal-style color palette ───────────────────────────────────────────

val Red = Color(0xFFFF0000)
val RedDim = Color(0xFF880000)
val White = Color(0xFFEEEEEE)
val Gray = Color(0xFF777777)
val Dark = Color(0xFF000000)
val SurfaceBg = Color(0xFF060606)
val BorderDim = Color(0xFF1F1F1F)
val Mono = FontFamily.Monospace

// ─── System status bar ──────────────────────────────────────────────────────

/**
 * 系统状态栏
 *
 * 在顶栏下方显示 4 个实时指标（数据包计数、网络接口、CPU、内存）。
 * 数据包计数和 CPU/内存值每 1.5 秒自动更新一次（伪随机变化），
 * 模拟 Hollywood 风格的"实时监控面板"视觉效果。
 *
 * 布局为全宽 Row，子项通过 Stat 组件渲染为 "标签 值" 格式。
 * 背景为极深黑色（#020202），底部有暗色边框。
 */
@Composable
fun StatusBar() {
    var tick by remember { mutableIntStateOf(0) }
    val rng = remember { KRandom(7) }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }

    Row(
        Modifier.fillMaxWidth().background(Color(0xFF020202)).border(0.5.dp, BorderDim).padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat("数据包", (9000 + rng.nextInt(2000) + tick * 41).toString().padStart(4))
        Stat("接口", "wlan0")
        Stat("CPU", "${10 + tick % 22}%")
        Stat("内存", "${320 + tick * 11 % 180}MB")
    }
}

/**
 * 状态栏单项指标
 *
 * 在 StatusBar 中显示一个 "标签: 值" 格式的指标项。
 * 标签用灰色、值用暗红色，形成层次对比。
 *
 * @param label 指标标签（如 "数据包"、"CPU"）
 * @param value 指标值（如 "8942"、"12%"）
 */
@Composable
private fun Stat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", fontFamily = Mono, fontSize = 11.sp, color = Gray)
        Text(value, fontFamily = Mono, fontSize = 11.sp, color = RedDim)
    }
}

// ─── Animated module card ───────────────────────────────────────────────────

/**
 * 功能模块卡片
 *
 * 主屏幕上每个功能入口的展示卡片。支持渐入动画、按压缩放反馈、
 * 以及运行/停止状态驱动的实时颜色切换。
 *
 * 卡片外观：
 * - 未运行：暗色背景 + 暗红边框 + 灰色文字
 * - 运行中：微红背景 + 红色边框 + 红色文字 + 呼吸指点
 *
 * 按压时缩放至 95% 并触发触觉反馈（LongPress）。
 * 卡片整体点击进入详情页（onClick），右侧按钮启停模块（onToggle）。
 *
 * @param name 模块名称（如 "BLE 泛洪"）
 * @param desc 模块简短描述
 * @param running 当前运行状态
 * @param delayMs 入场延迟（毫秒），用于级联动画
 * @param onClick 点击卡片回调（通常导航到详情页）
 * @param onToggle 点击启停按钮回调
 */
@Composable
fun AnimatedCard(name: String, desc: String, running: Boolean, delayMs: Long, onClick: () -> Unit, onToggle: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f))

    LaunchedEffect(Unit) { delay(delayMs); visible = true }

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(400)) + slideInVertically(tween(450)) { it / 4 }) {
        val accent by animateColorAsState(if (running) Red.copy(alpha = 0.75f) else BorderDim, tween(400))
        val bg by animateColorAsState(if (running) Red.copy(alpha = 0.06f) else SurfaceBg, tween(400))
        val nameColor by animateColorAsState(if (running) Red else White, tween(400))
        val descColor by animateColorAsState(if (running) Red.copy(alpha = 0.5f) else Gray, tween(400))
        val dotColor by animateColorAsState(if (running) Red else RedDim.copy(alpha = 0.3f), tween(400))

        Surface(
            modifier = Modifier.fillMaxWidth().scale(scale).clickable { pressed = true; onClick(); pressed = false }.padding(vertical = 4.dp),
            shape = RoundedCornerShape(4.dp), color = bg, border = BorderStroke(1.5.dp, accent),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(dotColor))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = nameColor)
                    Text(desc, fontFamily = Mono, fontSize = 11.sp, color = descColor)
                }
                Surface(
                    modifier = Modifier.clickable { pressed = true; onToggle(); pressed = false },
                    shape = RoundedCornerShape(3.dp),
                    color = if (running) Color.Transparent else Red,
                    border = BorderStroke(1.5.dp, if (running) Red.copy(alpha = 0.3f) else Red),
                ) {
                    Text(if (running) "停止" else "启动", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (running) Red else White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp))
                }
            }
        }
    }
}

// ─── Terminal-style page header ─────────────────────────────────────────────

/**
 * 终端风格页面头部
 *
 * 所有详情页统一的标题栏组件。左侧显示 [ 返回 ] 按钮（红色），
 * 中间显示页面标题（白色），右侧显示终端提示符 `root@deauth:~#`。
 * 背景色为 SurfaceBg（#060606），底部有暗色边框。
 *
 * @param title 页面标题（如 "BLE 泛洪引擎"）
 * @param back 点击返回按钮时的回调，用于导航回上一页
 */
@Composable
fun TerminalHeader(title: String, back: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val cursorBlink = rememberInfiniteTransition()
    val cursorAlpha by cursorBlink.animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse))
    Column {
        Row(Modifier.fillMaxWidth().statusBarsPadding().background(SurfaceBg).border(0.5.dp, BorderDim).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("[ 返回 ]", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); back() })
            Spacer(Modifier.width(12.dp))
            Text(title, fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = White)
            Spacer(Modifier.weight(1f))
            Text("root@deauth:~#", fontFamily = Mono, fontSize = 11.sp, color = Gray)
            Text("_", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Red.copy(alpha = cursorAlpha))
        }
    }
}

// ─── Terminal text helpers ──────────────────────────────────────────────────

/** 终端提示符渲染器 — 显示 `root@deauth:~# ` 红色前缀 */
@Composable
fun TermPrompt(cmd: String) {
    Text("root@deauth:~# ", fontFamily = Mono, fontSize = 12.sp, color = Red, fontWeight = FontWeight.Bold)
}

/** 终端输出行渲染器 — 缩进两格显示输出文本 */
@Composable
fun TermOutput(text: String, color: Color = White) {
    Text("  $text", fontFamily = Mono, fontSize = 11.sp, color = color)
}

/** 终端行渲染器 — `$ prompt output` 格式，output 含 FAIL/ERROR 时红色 */
@Composable
fun TermLine(prompt: String, output: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text("$ ", fontFamily = Mono, fontSize = 12.sp, color = Red, fontWeight = FontWeight.Bold)
        Text("$prompt ", fontFamily = Mono, fontSize = 12.sp, color = White)
        Text(output, fontFamily = Mono, fontSize = 12.sp, color = if (output.contains("FAIL") || output.contains("ERROR")) Red else Gray)
    }
}

// ─── Terminal panel ─────────────────────────────────────────────────────────

/**
 * 终端面板组件
 *
 * 带标题栏的卡片式面板，使用 ┌─/└─ 字符模拟终端框线。
 * 标题栏为微红背景（Red 8% alpha），内容区为深黑背景（SurfaceBg）。
 * 顶部 ┌─ 标题 + 底部 └─ 形成完整框线视觉。
 *
 * @param title 面板标题（如 "载荷配置"、"统计"）
 * @param modifier Modifier 修饰符
 * @param content 面板内容（在 ColumnScope 中渲染，支持 Column 子布局）
 */
@Composable
fun TermPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().border(1.dp, BorderDim).background(SurfaceBg)) {
        Row(Modifier.fillMaxWidth().background(Red.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text("┌─ $title", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(10.dp)) { content() }
        Text("└─", fontFamily = Mono, fontSize = 11.sp, color = Red)
    }
}

// ─── Section label ──────────────────────────────────────────────────────────

/**
 * 区域标签
 *
 * 在详情页中标记一个逻辑区块的标题，使用暗红色粗体等宽字体。
 *
 * @param text 标签文字（如 "载荷配置"、"数据包日志"）
 */
@Composable
fun SectionLabel(text: String) {
    Text(text, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RedDim, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

// ─── Terminal input field ───────────────────────────────────────────────────

/**
 * 终端风格输入框
 *
 * 模拟命令行提示的文本输入组件，带有 `$ --label` 前缀标签。
 * 使用 BasicTextField 实现，支持占位符（placeholder）显示灰色提示文字。
 * 边框和背景使用暗色主题配色（SurfaceBg + BorderDim），与整体终端风格统一。
 *
 * @param label 输入框标签（如 "host"、"port"），显示为 `$ --label`
 * @param value 当前输入值
 * @param placeholder 占位提示文字（在输入为空时显示）
 * @param onValue 输入值变化回调
 */
@Composable
fun TermInput(label: String, value: String, placeholder: String = "", onValue: (String) -> Unit) {
    Text("$ --$label", fontFamily = Mono, fontSize = 11.sp, color = Red)
    BasicTextField(
        value = value, onValueChange = onValue,
        textStyle = TextStyle(fontFamily = Mono, fontSize = 12.sp, color = White),
        modifier = Modifier.fillMaxWidth().background(SurfaceBg).border(1.dp, BorderDim).padding(8.dp),
        decorationBox = { inner ->
            if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, fontFamily = Mono, fontSize = 12.sp, color = Gray.copy(alpha = 0.4f))
            inner()
        },
    )
}
