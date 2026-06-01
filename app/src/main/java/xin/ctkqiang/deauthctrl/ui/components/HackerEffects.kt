package xin.ctkqiang.deauthctrl.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * CRT 扫描线叠加层
 *
 * 在屏幕上方绘制均匀分布的水平红色线条，模拟 CRT 显示器的扫描线效果。
 * 使用 Compose drawBehind 直接绘制，性能优异——不分配额外 Composable。
 *
 * 线条颜色由 color 参数指定，通过 alpha 控制透明度。
 * 在深色背景上使用 0.03-0.06 的 alpha 值效果最佳，
 * 过低（< 0.02）肉眼不可见，过高（> 0.10）影响文字可读性。
 *
 * @param lineSpacing 扫描线间距（dp），越小越密集。默认 3dp，推荐 2-4dp
 * @param alpha 线条透明度（0.0-1.0），默认 0.04
 * @param color 线条颜色，默认红色（0xFFFF0000）
 */
@Composable
fun ScanlineOverlay(
    lineSpacing: Dp = 3.dp,
    alpha: Float = 0.04f,
    color: Color = Color(0xFFFF0000),
) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { lineSpacing.toPx() }
    Box(
        Modifier.fillMaxSize().drawBehind {
            var y = 0f
            while (y < size.height) {
                drawLine(color.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y), 1f)
                y += px
            }
        },
    )
}

/**
 * 故障效果文字（Glitch Text）
 *
 * 模拟数字故障/毛刺（glitch）效果的文字组件，灵感来自 Mr. Robot 片头视觉风格。
 * 每间隔 glitchIntervalMs 毫秒触发一次故障动画：
 * 1. 红色副本向右偏移（模拟 RGB 通道错位）
 * 2. 白色副本向左偏移（模拟信号过载/亮度闪烁）
 * 3. 青色副本向左偏移（模拟色偏修复延迟）
 * 4. 恢复原始位置
 *
 * 动画周期约 140ms（50+40+50），足够短以保证"闪现"效果。
 * 三层偏移副本配合透明度和不同方向偏移量，营造层次化的故障感。
 *
 * @param text 显示的文字内容
 * @param glitchIntervalMs 故障触发间隔（毫秒），默认 4000ms（每 4 秒故障一次）
 * @param style Compose TextStyle 样式（字号、字距等），默认 28.sp
 * @param modifier Modifier 修饰符，用于控制布局
 */
@Composable
fun GlitchText(
    text: String,
    glitchIntervalMs: Long = 4000,
    style: TextStyle = TextStyle(fontSize = 28.sp),
    modifier: Modifier = Modifier,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var showRed by remember { mutableStateOf(false) }
    var showCyan by remember { mutableStateOf(false) }
    var showBright by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(glitchIntervalMs)
            offsetX = Random.nextInt(-6, 7).toFloat()
            showRed = true; delay(50)
            showRed = false; showBright = true; delay(40)
            showBright = false; showCyan = true; delay(50)
            showCyan = false; offsetX = 0f
        }
    }

    Box(modifier = modifier) {
        if (showRed) Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = style, color = Color.Red.copy(alpha = 0.7f), modifier = Modifier.offset(x = offsetX.dp))
        if (showBright) Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = style, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.offset(x = -offsetX.dp / 2))
        if (showCyan) Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = style, color = Color.Cyan.copy(alpha = 0.35f), modifier = Modifier.offset(x = -offsetX.dp))
        Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = style, color = Color(0xFFFF0000))
    }
}

/**
 * 脉冲呼吸指示灯
 *
 * 一个小圆点，以正弦波周期性地改变透明度，模拟"呼吸"或"闪烁"效果。
 * 用于指示系统/模块运行状态（红色脉冲 = 运行中，暗红 = 停止）。
 *
 * 动画细节：
 * - phase 每 60ms 递增 0.06 → 约 1.05 秒完成一个完整呼吸周期 (2π / 0.06 × 60ms)
 * - 透明度范围 0.20-1.0（sin 输出 * 0.45 + 0.5，再 coerceIn）
 *
 * @param color 指示灯颜色，默认红色
 * @param size 圆点直径（dp），默认 10dp
 * @param modifier Modifier 修饰符
 */
@Composable
fun PulseDot(color: Color = Color(0xFFFF0000), size: Dp = 10.dp, modifier: Modifier = Modifier) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { delay(60); phase += 0.06f } }
    val a = (sin(phase * PI).toFloat() * 0.45f + 0.5f)
    Box(modifier = modifier.size(size).clip(CircleShape).background(color.copy(alpha = a.coerceIn(0.2f, 1f))))
}

/**
 * ASCII 分隔线
 *
 * 使用全角破折号字符（'─'）重复 48 次构成一条水平装饰分隔线。
 * 终端/命令行风格的分隔元素，用于区分 UI 中的不同逻辑区块。
 *
 * 适用场景：卡片分隔、章节标题下划线、日志输出与内容区之间的分界。
 *
 * @param modifier Modifier 修饰符，用于控制外边距和宽度
 */
@Composable
fun AsciiDivider(modifier: Modifier = Modifier) {
    Text("─".repeat(48), fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Color(0xFFFF0000).copy(alpha = 0.3f), modifier = modifier)
}

/**
 * 系统启动序列动画
 *
 * 模拟 Hollywood/Mr. Robot 风格的终端启动画面。
 * 以打字机效果逐行显示 8 个系统初始化步骤，每行带有 "[ OK ]" 确认标记。
 *
 * ## 动画时间线
 * 1. 依次显示 8 个启动步骤（每个间隔 200-400ms，总计约 2.1 秒）
 * 2. 显示 "> 就绪 _" （闪烁光标，500ms 周期）
 * 3. 延迟 400ms 后调用 onComplete 回调 → 切换到主界面
 *
 * ## 启动步骤
 * 加载内核模块 → 挂载文件系统 → 初始化引擎 → 配置 BLE → 配置 WiFi →
 * 加载载荷 → 建立安全通道 → 激活隐蔽模式
 *
 * 背景为深黑色（#020202），叠加 ScanlineOverlay 增强 CRT 真实感。
 *
 * @param onComplete 启动序列完成后的回调，通常用于导航到主屏幕
 * @param modifier Modifier 修饰符
 */
@Composable
fun BootSequence(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val steps = listOf(
        "加载内核模块" to 260L, "挂载文件系统" to 220L, "初始化引擎 v1.0" to 320L,
        "配置 BLE 协议栈" to 280L, "配置 WiFi 接口" to 280L, "加载载荷数据库" to 260L,
        "建立安全通道" to 320L, "激活隐蔽模式" to 400L,
    )
    var n by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    var blink by remember { mutableStateOf(true) }
    var subtitle by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        for ((_, d) in steps) { delay(d); n++ }
        delay(500); done = true; subtitle = "系统就绪"; delay(800); blink = false; delay(400); onComplete()
    }
    LaunchedEffect(Unit) { while (true) { delay(500); blink = !blink } }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF020202)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DEAUTHCTRL", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Color(0xFFFF0000))
            Text("v1.0  |  哪吒网络安全", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF666666))
            Spacer(Modifier.height(22.dp))
            AsciiDivider()
            Spacer(Modifier.height(14.dp))

            steps.forEachIndexed { i, (msg, _) ->
                AnimatedVisibility(visible = i < n, enter = fadeIn(tween(200)) + slideInVertically(tween(250))) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  > $msg", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFFF0000))
                        Text("[  OK  ]", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFF880000))
                    }
                }
            }

            if (done) {
                Spacer(Modifier.height(16.dp))
                AsciiDivider()
                Spacer(Modifier.height(10.dp))
                Text(if (blink) "> 就绪 _" else "> 就绪", fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = Color(0xFFFF0000), fontWeight = FontWeight.Bold)
            }
        }
        ScanlineOverlay(lineSpacing = 3.dp, alpha = 0.05f, color = Color(0xFFFF0000))
    }
}
