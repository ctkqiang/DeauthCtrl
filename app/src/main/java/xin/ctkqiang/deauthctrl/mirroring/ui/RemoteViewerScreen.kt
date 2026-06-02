package xin.ctkqiang.deauthctrl.mirroring.ui

import android.graphics.SurfaceTexture
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import xin.ctkqiang.deauthctrl.mirroring.model.MirroringDevice

/**
 * 远程观看屏幕。
 *
 * ## 触控坐标
 * 用户在覆盖层上的点击/拖动坐标归一化为 0.0~1.0，
 * 由主机端 TouchInputInjector 还原为实际像素坐标。
 */
@Composable
fun RemoteViewerScreen(
    vm: MirroringViewModel,
    device: MirroringDevice?,
    back: () -> Unit,
) {
    val Red = Color(0xFFFF0000)
    val Dark = Color(0xFF0A0A0A)
    val Gray = Color(0xFF777777)
    val Green = Color(0xFF00CC00)

    val state by vm.state.collectAsState()
    var viewWidth by remember { mutableStateOf(1f) }
    var viewHeight by remember { mutableStateOf(1f) }
    var surfaceReady by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        // 顶部栏
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("观看中: ${device?.name ?: "—"}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Red)
                Text(
                    when (state) {
                        is MirroringState.ClientLive -> "● 已连接"
                        is MirroringState.ClientConnecting -> "连接中..."
                        is MirroringState.Error -> "[!] ${(state as MirroringState.Error).message}"
                        else -> "未连接"
                    },
                    fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                    color = if (state is MirroringState.ClientLive) Green else Gray,
                )
            }
            TextButton(onClick = {
                vm.disconnectClient()
                back()
            }) {
                Text("< 断开", fontFamily = FontFamily.Monospace, color = Gray, fontSize = 11.sp)
            }
        }

        // 视频区域
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
            when {
                state is MirroringState.ClientLive -> {
                    // TextureView — 硬件加速 H.264 解码渲染
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).also { tv ->
                                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        viewWidth = w.toFloat()
                                        viewHeight = h.toFloat()
                                        vm.startDecoding(Surface(st))
                                        surfaceReady = true
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                        viewWidth = w.toFloat()
                                        viewHeight = h.toFloat()
                                    }
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        surfaceReady = false
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // 透明触摸覆盖层
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(surfaceReady) {
                                detectTapGestures { offset ->
                                    vm.sendTouch(MotionEvent.ACTION_DOWN, (offset.x / viewWidth).coerceIn(0f, 1f), (offset.y / viewHeight).coerceIn(0f, 1f))
                                    vm.sendTouch(MotionEvent.ACTION_UP, (offset.x / viewWidth).coerceIn(0f, 1f), (offset.y / viewHeight).coerceIn(0f, 1f))
                                }
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
                state is MirroringState.ClientConnecting -> {
                    Text("正在连接...", fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Gray)
                }
                state is MirroringState.Error -> {
                    Text(
                        "连接失败\n${(state as MirroringState.Error).message}",
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Red,
                    )
                }
                else -> {
                    Text("等待连接", fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Gray)
                }
            }
        }

        // 底部状态栏
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF001100)).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "状态: ${state::class.simpleName?.removePrefix("Client") ?: "—"}",
                fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Gray,
            )
            Text("${viewWidth.toInt()}x${viewHeight.toInt()}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Gray)
        }
    }
}
