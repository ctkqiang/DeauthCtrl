package xin.ctkqiang.deauthctrl.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * BRUH 彩蛋 —— 用户做蠢事(空输入就点按钮)时,全屏透明底弹出漫画风
 * 「BRUH」贴图,晃一下自动消失,点一下立刻关掉。
 *
 * 图片通道:把图片放到 `app/src/main/assets/bruh.png` 即自动使用你的图;
 * 没有图片时用 Canvas 画的漫画 BRUH 兜底(黄字黑描边,略歪)。
 */
@Composable
fun BruhOverlay() {
    val context = LocalContext.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { StickerBus.subscribeBruh { shown = true } }

    if (shown) {
        var gone by remember { mutableStateOf(false) }
        val enter = remember { Animatable(0f) }
        val wobble = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            enter.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f))
            // 停留期间轻微左右晃(comic 质感)
            wobble.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 200f))
            kotlinx.coroutines.delay(1700)
            gone = true
            shown = false
        }

        if (!gone) {
            val bmp = remember { runCatching { BitmapFactory.decodeStream(context.assets.open("bruh.png")) }.getOrNull() }
            Box(
                Modifier.fillMaxSize().background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        gone = true
                        shown = false
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.graphicsLayer {
                        val pop = enter.value
                        val wob = sin(wobble.value * 12f) * 4f * (1f - enter.value * 0.2f)
                        scaleX = (0.2f + 0.8f * pop)
                        scaleY = (0.2f + 0.8f * pop)
                        rotationZ = -7f + wob
                        alpha = pop
                    },
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "bruh",
                            modifier = Modifier.size(240.dp),
                        )
                    } else {
                        CanvasBruh()
                    }
                }
            }
        }
    }
}

/** 漫画 BRUH 兜底:黄字 + 黑描边 + 放射小线,歪一点 */
@Composable
private fun CanvasBruh() {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.size(260.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        // 放射背景线(漫画爆炸感,极淡)
        for (i in 0 until 12) {
            val rad = Math.toRadians(i * 30.0)
            val sx = cx + kotlin.math.cos(rad).toFloat() * 60f
            val sy = cy + kotlin.math.sin(rad).toFloat() * 60f
            drawLine(
                Gold.copy(alpha = 0.25f),
                Offset(sx, sy),
                Offset(cx + kotlin.math.cos(rad).toFloat() * 110f, cy + kotlin.math.sin(rad).toFloat() * 110f),
                3f,
            )
        }
        // BRUH 大字:黑色描边在下,黄色填充在上
        val main = measurer.measure("BRUH", TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 64.sp))
        val x = cx - main.size.width / 2f
        val y = cy - main.size.height / 2f
        drawText(main, topLeft = Offset(x - 3.dp.toPx(), y + 3.dp.toPx()))
        // 重新测黑字同尺寸
        val outline = measurer.measure("BRUH", TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 64.sp, color = Color.Black))
        listOf(
            Offset(x - 2.dp.toPx(), y), Offset(x + 2.dp.toPx(), y),
            Offset(x, y - 2.dp.toPx()), Offset(x, y + 2.dp.toPx()),
        ).forEach { off -> drawText(outline, topLeft = off) }
        val fill = measurer.measure("BRUH", TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 64.sp, color = Color(0xFFFFD54F)))
        drawText(fill, topLeft = Offset(x, y))
        // 小字 MOMENT
        val sub = measurer.measure("MOMENT", TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = White))
        drawText(sub, topLeft = Offset(cx - sub.size.width / 2f, y + main.size.height + 4.dp.toPx()))
    }
}
