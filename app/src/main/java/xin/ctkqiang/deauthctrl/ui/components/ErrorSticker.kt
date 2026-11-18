package xin.ctkqiang.deauthctrl.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.R
import kotlin.math.sin

/**
 * 全局错误贴纸总线 —— 任何地方做蠢事(没填值就点按钮)都能弹
 */
object StickerBus {
    @Volatile var message: String? = null
        private set

    private val listeners = mutableListOf<(String) -> Unit>()

    fun show(text: String) {
        message = text
        listeners.forEach { it(text) }
    }

    fun subscribe(l: (String) -> Unit) { listeners.add(l) }
    fun unsubscribe(l: (String) -> Unit) { listeners.remove(l) }
}

/**
 * 错误贴纸 —— 用户做蠢事时从屏幕底部弹入、摇晃、2.5 秒后退场。
 * 挂在 MainScreen 根 Box 顶层,任何页面经 [StickerBus.show] 触发。
 */
@Composable
fun ErrorStickerOverlay() {
    var current by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val l: (String) -> Unit = { text ->
            current = text
            key++
        }
        StickerBus.subscribe(l)
    }

    current?.let { msg ->
        key(key) {
            val enter = remember { Animatable(0f) }
            val exit = remember { Animatable(0f) }
            var gone by remember { mutableStateOf(false) }

            // 停留期间轻微呼吸
            val breath = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                // 弹簧弹入 + 落地微弹
                enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
                launch {
                    breath.animateTo(
                        1f,
                        infiniteRepeatable(
                            animation = tween(600, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    )
                }
                kotlinx.coroutines.delay(2200)
                exit.animateTo(1f, tween(320))
                gone = true
            }

            if (!gone) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(bottom = 140.dp)
                            .graphicsLayer {
                                val popIn = enter.value
                                val e = exit.value
                                val breathe = 1f + 0.03f * sin((enter.value * 6.28f).toFloat())
                                val shakeDeg = if (enter.value < 1f) {
                                    // 弹入过程左右摇晃(像被弹出来的贴纸)
                                    sin(enter.value * 24f) * (1f - enter.value) * 14f
                                } else 0f
                                alpha = 1f - e
                                scaleX = (0.3f + 0.7f * popIn) * breathe * (1f - e * 0.2f)
                                scaleY = (0.3f + 0.7f * popIn) * breathe * (1f - e * 0.2f)
                                translationY = (1f - popIn) * 900f + e * 400f
                                rotationZ = shakeDeg
                            },
                    ) {
                        Image(
                            painter = painterResource(R.drawable.error_sticker),
                            contentDescription = "错误贴纸",
                            modifier = Modifier.size(200.dp),
                        )
                        Text(
                            msg.take(40),
                            fontFamily = Sans,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Gold,
                        )
                    }
                }
            } else {
                current = null
            }
        }
    }
}
