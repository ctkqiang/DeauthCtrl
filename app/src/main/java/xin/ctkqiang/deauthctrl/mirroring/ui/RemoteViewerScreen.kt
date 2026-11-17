package xin.ctkqiang.deauthctrl.mirroring.ui

import android.graphics.SurfaceTexture
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
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
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.White

@Composable
fun RemoteViewerScreen(
    vm: MirroringViewModel,
    device: MirroringDevice?,
    back: () -> Unit,
) {
    // 全息 HUD 主题色 —— 与全应用统一，切队换肤即时生效
    val overlayBg = Dark.copy(alpha = 0.88f)

    val state by vm.state.collectAsState()
    var viewW by remember { mutableStateOf(1f) }
    var viewH by remember { mutableStateOf(1f) }
    var surfaceReady by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var tapCount by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val view = LocalView.current
    val isLive = state is MirroringState.ClientLive

    // 全屏沉浸 + 强制横屏
    DisposableEffect(Unit) {
        val activity = context as ComponentActivity
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val ctrl = WindowCompat.getInsetsController(window, view)
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // 锁定横屏
        val prevOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            ctrl.show(WindowInsetsCompat.Type.systemBars())
            // 恢复之前的屏幕方向
            activity.requestedOrientation = prevOrientation
        }
    }

    // 自动隐藏覆盖层
    LaunchedEffect(showOverlay, isLive) {
        if (showOverlay && isLive) { delay(3000); showOverlay = false }
    }
    // 双击退出
    LaunchedEffect(tapCount) {
        if (tapCount >= 2) { delay(300); tapCount = 0 }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // === 视频层 — 填满全屏，TextureView 自动缩放 ===
        when {
            isLive || state is MirroringState.ClientConnecting -> {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { tv ->
                            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                    viewW = w.toFloat(); viewH = h.toFloat()
                                    vm.startDecoding(Surface(st))
                                    surfaceReady = true
                                }
                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                    viewW = w.toFloat(); viewH = h.toFloat()
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

                // === 触摸层 — 拖动=控制主机, 单击=切换覆盖层, 双击=断开 ===
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 拖动优先 — 放在前面，确保 drag 手势能捕获事件
                        .pointerInput(surfaceReady) {
                            detectDragGestures(
                                onDragStart = { o ->
                                    vm.sendTouch(MotionEvent.ACTION_DOWN, (o.x / viewW).coerceIn(0f, 1f), (o.y / viewH).coerceIn(0f, 1f))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    vm.sendTouch(MotionEvent.ACTION_MOVE, (change.position.x / viewW).coerceIn(0f, 1f), (change.position.y / viewH).coerceIn(0f, 1f))
                                },
                                onDragEnd = { vm.sendTouch(MotionEvent.ACTION_UP, 0f, 0f) },
                                onDragCancel = { vm.sendTouch(MotionEvent.ACTION_UP, 0f, 0f) },
                            )
                        }
                        .pointerInput(surfaceReady) {
                            detectTapGestures(
                                onTap = { showOverlay = !showOverlay },
                                onDoubleTap = { vm.disconnectClient(); back() },
                            )
                        },
                )
            }
            state is MirroringState.Error -> {
                Text("连接失败\n${(state as MirroringState.Error).message}", fontFamily = Mono, fontSize = 14.sp, color = Danger, modifier = Modifier.align(Alignment.Center))
            }
            else -> {
                Text("等待连接...", fontFamily = Mono, fontSize = 14.sp, color = Gray, modifier = Modifier.align(Alignment.Center))
            }
        }

        // === 顶部浮动条 ===
        AnimatedVisibility(
            visible = showOverlay, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(Modifier.fillMaxWidth().background(overlayBg).padding(horizontal = 18.dp, vertical = 12.dp).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(device?.name ?: "—", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = White)
                        Text(
                            buildString {
                                if (isLive) append("● 已连接  ") else if (state is MirroringState.ClientConnecting) append("○ 连接中  ") else append("—")
                                append("${viewW.toInt()}×${viewH.toInt()}")
                            },
                            fontFamily = Mono, fontSize = 9.sp, color = if (isLive) TacGreen else Gray,
                        )
                    }
                    Text("✕", fontFamily = Mono, fontSize = 20.sp, color = Gold, modifier = Modifier.clickable { vm.disconnectClient(); back() })
                }
            }
        }

        // === 底部浮动提示 ===
        AnimatedVisibility(
            visible = showOverlay, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier.fillMaxWidth().background(overlayBg).padding(horizontal = 18.dp, vertical = 8.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text("单击 ↦ 界面", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                Text("|", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                Text("拖动 ↦ 控制", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                Text("|", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                Text("双击 ↦ 断开", fontFamily = Mono, fontSize = 9.sp, color = Gray)
            }
        }

        // === 连接中加载动画 ===
        if (state is MirroringState.ClientConnecting) {
            val pulse = rememberInfiniteTransition()
            val alpha by pulse.animateFloat(0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse))
            Text(
                "正在连接 ${device?.name ?: ""}...",
                fontFamily = Mono, fontSize = 13.sp, color = White.copy(alpha = alpha),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
