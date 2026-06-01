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

@Composable
fun PulseDot(color: Color = Color(0xFFFF0000), size: Dp = 10.dp, modifier: Modifier = Modifier) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { delay(60); phase += 0.06f } }
    val a = (sin(phase * PI).toFloat() * 0.45f + 0.5f)
    Box(modifier = modifier.size(size).clip(CircleShape).background(color.copy(alpha = a.coerceIn(0.2f, 1f))))
}

@Composable
fun AsciiDivider(modifier: Modifier = Modifier) {
    Text("─".repeat(48), fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Color(0xFFFF0000).copy(alpha = 0.3f), modifier = modifier)
}

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
