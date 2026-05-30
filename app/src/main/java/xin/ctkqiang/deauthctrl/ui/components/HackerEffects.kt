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

        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = style,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

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
