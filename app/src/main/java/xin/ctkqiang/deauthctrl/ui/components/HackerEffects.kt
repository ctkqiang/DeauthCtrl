package xin.ctkqiang.deauthctrl.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

// ──────────────────────────────────────────────────────────────────────────────
// 扫描线覆盖层 — 模拟 CRT 显示器的水平扫描线
// ──────────────────────────────────────────────────────────────────────────────

/**
 * CRT 扫描线特效 — 在全屏叠加半透明红色水平条纹，
 * 模拟老式 CRT 终端显示器的视觉质感。
 *
 * @param lineSpacing 扫描线间距 (dp)，越小越密
 * @param alpha 扫描线透明度，0.01-0.05 之间为佳
 * @param color 扫描线颜色，默认主题红色
 */
@Composable
fun ScanlineOverlay(
    lineSpacing: Dp = 3.dp,
    alpha: Float = 0.03f,
    color: Color = MaterialTheme.colorScheme.primary,
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

// ──────────────────────────────────────────────────────────────────────────────
// 闪烁光标 — 终端输入光标动画
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 闪烁终端光标 — 模拟命令行输入光标，
 * 每 500ms 闪烁一次。红色方块样式。
 */
@Composable
fun BlinkingCursor(
    modifier: Modifier = Modifier,
) {
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
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// 逐字打字效果
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 打字机效果 — 字符逐字显现，模拟终端输入过程。
 *
 * @param fullText 完整文本
 * @param typingSpeedMs 每字间隔 (毫秒)
 * @param style 文本样式
 * @param color 文本颜色
 * @param showCursor 是否在末尾显示闪烁光标
 */
@Composable
fun TypewriterText(
    fullText: String,
    typingSpeedMs: Long = 60,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    showCursor: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var visibleChars by remember { mutableIntStateOf(0) }

    LaunchedEffect(fullText) {
        visibleChars = 0
        for (i in fullText.indices) {
            delay(typingSpeedMs)
            visibleChars = i + 1
        }
    }

    Row(modifier = modifier) {
        Text(
            text = fullText.take(visibleChars),
            fontFamily = FontFamily.Monospace,
            style = style,
            color = color,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (showCursor && visibleChars < fullText.length) {
            BlinkingCursor()
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// 故障闪烁效果 (Glitch)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 故障闪烁文字 — 随机瞬移文字位置并切换颜色，
 * 模拟数字故障 (Glitch) 视觉效果。
 *
 * @param text 显示文本
 * @param glitchIntervalMs 故障触发间隔 (毫秒)
 * @param glitchDurationMs 每次故障持续时间 (毫秒)
 */
@Composable
fun GlitchText(
    text: String,
    glitchIntervalMs: Long = 3000,
    glitchDurationMs: Long = 100,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineLarge,
    modifier: Modifier = Modifier,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isRedShift by remember { mutableStateOf(false) }
    var isBlueShift by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(glitchIntervalMs)
            offsetX = Random.nextInt(-4, 4).toFloat()
            isRedShift = true
            delay(glitchDurationMs / 2)
            isRedShift = false
            isBlueShift = true
            delay(glitchDurationMs / 2)
            isBlueShift = false
            offsetX = 0f
        }
    }

    Box(modifier = modifier) {
        // 红色偏移层
        if (isRedShift) {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = style,
                color = Color.Red.copy(alpha = 0.6f),
                modifier = Modifier.offset(x = offsetX.dp),
            )
        }

        // 蓝色偏移层
        if (isBlueShift) {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = style,
                color = Color.Blue.copy(alpha = 0.4f),
                modifier = Modifier.offset(x = (-offsetX).dp),
            )
        }

        // 主文本层
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = style,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// 数字雨粒子效果 (简化版)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 极简版数字雨 — 在屏幕背景渲染纵向下降的随机字符列，
 * 模拟《黑客帝国》风格的 Matrix Rain 效果。
 * 使用极低透明度以免干扰主界面内容。
 */
@Composable
fun MatrixRainBackground(
    columnCount: Int = 20,
    charAlpha: Float = 0.04f,
    modifier: Modifier = Modifier,
) {
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            frame++
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val colWidth = size.width / columnCount
                val random = Random(frame)
                for (col in 0 until columnCount) {
                    val x = col * colWidth + colWidth / 2
                    val headY = ((frame * 8 + col * 37) % size.height).toFloat()
                    val tailLen = random.nextInt(20, 60).toFloat()
                    // 每列绘制一条垂直下降的短线，模拟数字雨
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

// ──────────────────────────────────────────────────────────────────────────────
// 逐行动画显现 — 用于列表项依次出现的入场效果
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 逐行动画 — 每一行依次嵌入透明并向上滑入，
 * 用于黑客风格的信息展示页面。
 *
 * @param visible 是否可见
 * @param delayMs 当前行相对于首行的延迟 (毫秒)
 * @param content 行内容
 */
@Composable
fun FadeSlideRow(
    visible: Boolean,
    delayMs: Long = 0,
    content: @Composable () -> Unit,
) {
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs)
        show = true
    }

    AnimatedVisibility(
        visible = show && visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
    ) {
        content()
    }
}
