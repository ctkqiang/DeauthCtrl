package xin.ctkqiang.deauthctrl.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ScanlineOverlay(
    lineSpacing: Dp = 2.dp,
    alpha: Float = 0.06f,
    color: Color = Color(0xFFFF0000),
) {
    val lineSpacingPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        lineSpacing.toPx()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = color.copy(alpha = alpha),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                    y += lineSpacingPx
                }
            },
    )
}

@Composable
fun BlinkingCursor(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(530)
            visible = !visible
        }
    }
    Text(
        text = if (visible) "█" else " ",
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFFF0000),
        modifier = modifier,
    )
}

@Composable
fun GlitchText(
    text: String,
    glitchIntervalMs: Long = 3000,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
    modifier: Modifier = Modifier,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isRedShift by remember { mutableStateOf(false) }
    var isWhiteShift by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(glitchIntervalMs)
            offsetX = Random.nextInt(-6, 6).toFloat()
            isRedShift = true
            delay(80)
            isRedShift = false
            isWhiteShift = true
            delay(80)
            isWhiteShift = false
            offsetX = 0f
        }
    }

    Box(modifier = modifier) {
        if (isRedShift) {
            Text(
                text = text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                style = style, color = Color.Red.copy(alpha = 0.7f),
                modifier = Modifier.offset(x = offsetX.dp),
            )
        }
        if (isWhiteShift) {
            Text(
                text = text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                style = style, color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.offset(x = (-offsetX).dp),
            )
        }
        Text(
            text = text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            style = style, color = Color(0xFFFF0000),
        )
    }
}

@Composable
fun MatrixRainBackground(
    columnCount: Int = 28,
    charAlpha: Float = 0.07f,
    modifier: Modifier = Modifier,
) {
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(80)
            frame++
        }
    }

    val rng = remember { Random(42) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val colWidth = size.width / columnCount
                val random = Random(frame)
                for (col in 0 until columnCount) {
                    val x = col * colWidth + colWidth / 2
                    val headY = ((frame * 10 + col * 31) % size.height).toFloat()
                    val tailLen = random.nextInt(30, 80).toFloat()
                    drawLine(
                        color = Color(0xFFFF0000).copy(alpha = charAlpha),
                        start = Offset(x, headY - tailLen),
                        end = Offset(x, headY),
                        strokeWidth = 2f,
                    )
                }
            },
    )
}

@Composable
fun PulseDot(
    color: Color = Color(0xFFFF0000),
    size: Dp = 10.dp,
    modifier: Modifier = Modifier,
) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60)
            phase += 0.05f
        }
    }
    val alpha = (sin(phase * PI).toFloat() * 0.5f + 0.5f)
    val glowAlpha = alpha * 0.3f

    Box(modifier = modifier.size(size + 6.dp).clip(CircleShape).background(color.copy(alpha = glowAlpha))) {
        Box(
            modifier = Modifier
                .size(size)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha.coerceIn(0.3f, 1f)))
        )
    }
}

@Composable
fun DataStream(
    color: Color = Color(0xFFFF0000).copy(alpha = 0.15f),
    modifier: Modifier = Modifier,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    val random = remember { Random(42) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(20)
            offset = (offset + 1.5f) % 140f
        }
    }

    val hexData = remember {
        buildString {
            repeat(140) { i ->
                append(random.nextInt(16).toString(16).uppercase())
                append(' ')
                if ((i + 1) % 8 == 0) append("  ")
            }
        }
    }

    BasicText(
        text = hexData,
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = color),
        modifier = modifier.offset(x = (-offset).dp).fillMaxWidth(),
        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
    )
}

@Composable
fun BootSequence(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = listOf(
        "正在初始化内核模块..." to 260L,
        "正在挂载文件系统..." to 200L,
        "正在加载 DeauthCtrl 引擎 v1.0..." to 320L,
        "正在初始化 BLE 协议栈..." to 280L,
        "正在初始化 WiFi 接口..." to 280L,
        "正在加载载荷数据库..." to 260L,
        "正在建立安全通道..." to 320L,
        "正在激活隐藏模式..." to 400L,
    )

    var visibleLines by remember { mutableIntStateOf(0) }
    var showReady by remember { mutableStateOf(false) }
    var blink by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        for (i in lines.indices) {
            delay(lines[i].second)
            visibleLines = i + 1
        }
        delay(400)
        showReady = true
        delay(800)
        blink = false
        delay(300)
        onComplete()
    }

    LaunchedEffect(Unit) {
        while (true) { delay(500); blink = !blink }
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF010101)),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "╔══════════════════════╗",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFFF0000),
            )
            Text(
                "║  DEAUTHCTRL BOOT     ║",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFFF0000),
                fontWeight = FontWeight.Bold,
            )
            Text(
                "╚══════════════════════╝",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFFF0000),
            )
            Spacer(Modifier.height(16.dp))

            lines.forEachIndexed { i, (line, _) ->
                AnimatedVisibility(
                    visible = i < visibleLines,
                    enter = fadeIn() + slideInVertically(),
                ) {
                    Text(
                        "  > $line",
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = Color(0xFFFF0000),
                    )
                }
                if (i < visibleLines) {
                    Text(
                        "    [  OK  ]",
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = Color(0xFFCC0000),
                    )
                }
            }

            if (showReady) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (blink) "> 就绪._" else "> 就绪.",
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    color = Color(0xFFFF0000), fontWeight = FontWeight.Bold,
                )
            }
        }

        ScanlineOverlay(lineSpacing = 2.dp, alpha = 0.05f, color = Color(0xFFFF0000))
    }
}

@Composable
fun AsciiDivider(modifier: Modifier = Modifier) {
    Text(
        "─".repeat(48),
        fontFamily = FontFamily.Monospace, fontSize = 8.sp,
        color = Color(0xFFFF0000).copy(alpha = 0.3f),
        modifier = modifier,
    )
}

@Composable
fun TerminalPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1A0000)).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "┌─ $title",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = Color(0xFFFF0000), fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "─".repeat(8),
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = Color(0xFFFF0000).copy(alpha = 0.3f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFFF0000).copy(alpha = 0.25f))
                .background(Color(0xFF020202))
                .padding(10.dp),
        ) {
            content()
        }
    }
}
