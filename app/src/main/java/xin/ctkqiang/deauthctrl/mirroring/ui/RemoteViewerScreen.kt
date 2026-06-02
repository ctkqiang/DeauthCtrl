package xin.ctkqiang.deauthctrl.mirroring.ui

import android.graphics.SurfaceTexture
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import xin.ctkqiang.deauthctrl.mirroring.model.MirroringDevice

@Composable
fun RemoteViewerScreen(
    vm: MirroringViewModel,
    device: MirroringDevice?,
    back: () -> Unit,
) {
    val Red = Color(0xFFFF0000)
    val Gray = Color(0xFF777777)
    val Green = Color(0xFF00CC00)
    val Black80 = Color(0xCC000000)

    val state by vm.state.collectAsState()
    var viewWidth by remember { mutableStateOf(1f) }
    var viewHeight by remember { mutableStateOf(1f) }
    var surfaceReady by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val view = LocalView.current

    // 全屏沉浸模式
    DisposableEffect(Unit) {
        val window = (context as ComponentActivity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 3 秒后自动隐藏覆盖层
    LaunchedEffect(showOverlay) {
        if (showOverlay && state is MirroringState.ClientLive) {
            delay(3000)
            showOverlay = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 视频 — 填满全屏
        when {
            state is MirroringState.ClientLive || state is MirroringState.ClientConnecting -> {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { tv ->
                            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                    viewWidth = w.toFloat(); viewHeight = h.toFloat()
                                    vm.startDecoding(Surface(st))
                                    surfaceReady = true
                                }
                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                    viewWidth = w.toFloat(); viewHeight = h.toFloat()
                                }
                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    surfaceReady = false; return true
                                }
                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // 全屏触摸覆盖层 — 单击切换覆盖层，双击断开
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(surfaceReady) {
                            detectTapGestures(
                                onTap = { showOverlay = !showOverlay },
                                onDoubleTap = {
                                    vm.disconnectClient()
                                    back()
                                },
                            )
                        }
                        .pointerInput(surfaceReady) {
                            detectDragGestures(
                                onDragStart = { o -> vm.sendTouch(MotionEvent.ACTION_DOWN, o.x / viewWidth, o.y / viewHeight) },
                                onDrag = { c, _ -> vm.sendTouch(MotionEvent.ACTION_MOVE, c.position.x / viewWidth, c.position.y / viewHeight) },
                                onDragEnd = { vm.sendTouch(MotionEvent.ACTION_UP, 0f, 0f) },
                                onDragCancel = { vm.sendTouch(MotionEvent.ACTION_UP, 0f, 0f) },
                            )
                        },
                )
            }
            state is MirroringState.Error -> {
                Text(
                    "连接失败\n${(state as MirroringState.Error).message}",
                    fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Red,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                Text("等待连接...", fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Gray, modifier = Modifier.align(Alignment.Center))
            }
        }

        // 浮动顶部覆盖层 — 可隐藏
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                Modifier.fillMaxWidth().background(Black80).padding(horizontal = 16.dp, vertical = 10.dp).statusBarsPadding(),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(device?.name ?: "—", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text(
                            if (state is MirroringState.ClientLive) "● 已连接  |  双击断开" else "连接中...",
                            fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                            color = if (state is MirroringState.ClientLive) Green else Gray,
                        )
                    }
                    Text(
                        "< 退出",
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Red,
                        modifier = Modifier.clickable { vm.disconnectClient(); back() },
                    )
                }
            }
        }

        // 浮动底部状态 — 可隐藏
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier.fillMaxWidth().background(Black80).padding(horizontal = 16.dp, vertical = 6.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${viewWidth.toInt()}x${viewHeight.toInt()}",
                    fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Gray,
                )
                Text("单击切换界面", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Gray)
            }
        }
    }
}
