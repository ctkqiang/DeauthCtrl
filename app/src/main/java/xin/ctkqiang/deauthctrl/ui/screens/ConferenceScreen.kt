package xin.ctkqiang.deauthctrl.ui.screens

import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.projection.MediaProjectionManager
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.manager.ConfMember
import xin.ctkqiang.deauthctrl.manager.ConfStreamType
import xin.ctkqiang.deauthctrl.manager.ConferenceHostService
import xin.ctkqiang.deauthctrl.manager.MemberRxStats
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TacticalCorners
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.ConferenceViewModel

/**
 * 局域网战术会议 —— 无互联网多方视频 + 语音
 *
 * 同一 WiFi 内，每台设备广播自己的视频流（摄像头前/后置或屏幕），
 * 同时接收并显示所有其他成员的画面；语音为 PTT 半双工。
 * 全部流量停留在局域网内，不经过任何外网服务器。
 */
@Composable
fun ConferenceScreen(vm: ConferenceViewModel, operatorName: String, back: () -> Unit) {
    val manager = vm.manager
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    manager.deviceName = operatorName.ifBlank { "operator" }

    val running by manager.running.collectAsState()
    val members by manager.members.collectAsState()
    val myStream by manager.myStream.collectAsState()
    val talking by manager.talking.collectAsState()
    val error by manager.error.collectAsState()
    val stats by manager.videoStats.collectAsState()
    val rxStats by manager.memberRxStats.collectAsState()
    val tx by manager.senderTxStats.collectAsState()

    // 屏幕共享授权：先起前台服务（Android 14 要求），再请求 MediaProjection
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            context.startService(
                Intent(context, ConferenceHostService::class.java).apply {
                    action = ConferenceHostService.ACTION_START_CAPTURE
                    putExtra(ConferenceHostService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ConferenceHostService.EXTRA_DATA, result.data)
                },
            )
            manager.setStream(ConfStreamType.SCREEN)
        } else {
            // 用户拒绝录屏:摄像头流正在运行时不要 ACTION_STOP —— 服务停止会触发投影 onStop
            // 链路把正在运行的摄像头编码器一并杀掉;没有视频流时才清理录屏前台服务
            val camActive = manager.myStream.value == ConfStreamType.CAMERA_FRONT ||
                manager.myStream.value == ConfStreamType.CAMERA_BACK
            if (!camActive) {
                context.startService(
                    Intent(context, ConferenceHostService::class.java).apply {
                        action = ConferenceHostService.ACTION_STOP
                    },
                )
            }
            if (manager.myStream.value == ConfStreamType.NONE) {
                manager.setStream(ConfStreamType.NONE)
            }
        }
    }

    fun requestScreenShare() {
        context.startForegroundService(
            Intent(context, ConferenceHostService::class.java).apply {
                action = ConferenceHostService.ACTION_PREPARE
            },
        )
        val pm = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager
        projectionLauncher.launch(pm.createScreenCaptureIntent())
    }

    // 离开页面 → 停屏服务 + 离开会议
    DisposableEffect(Unit) {
        onDispose {
            context.startService(Intent(context, ConferenceHostService::class.java).apply {
                action = ConferenceHostService.ACTION_STOP
            })
            manager.stop()
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("局域网会议", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            // 状态条
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("在线 ${members.size} 人", fontFamily = Mono, fontSize = 10.sp, color = TacGreen)
                    Text(stats, fontFamily = Mono, fontSize = 9.sp, color = Gray)
                    Text(if (running) "● 会议中" else "○ 未入会", fontFamily = Mono, fontSize = 10.sp, color = if (running) TacGreen else Gray)
                }
            }

            Spacer(Modifier.height(8.dp))
            ActionButton(running, "进入会议", "退出会议") {
                haptic.hud()
                if (running) {
                    manager.stop()
                    context.startService(Intent(context, ConferenceHostService::class.java).apply {
                        action = ConferenceHostService.ACTION_STOP
                    })
                } else {
                    manager.start()
                }
            }
            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 9.sp, color = Danger, modifier = Modifier.padding(top = 4.dp)) }

            if (running) {
                Spacer(Modifier.height(8.dp))

                // ── 本机控制面板 ──
                TermPanelLocal("本机 · 流源与语音") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ConfStreamType.entries.forEach { type ->
                            val selected = myStream == type
                            Surface(
                                modifier = Modifier.weight(1f).clickable {
                                    haptic.hud()
                                    if (type == ConfStreamType.SCREEN) {
                                        if (myStream == ConfStreamType.SCREEN) {
                                            manager.setStream(ConfStreamType.NONE)
                                            // Android 14+ 一个 MediaProjection 会话只能 createVirtualDisplay 一次；
                                            // 关屏共享必须同时结束前台服务与授权会话，下次共享重新弹系统授权
                                            context.startService(
                                                Intent(context, ConferenceHostService::class.java).apply {
                                                    action = ConferenceHostService.ACTION_STOP
                                                },
                                            )
                                        } else {
                                            requestScreenShare()
                                        }
                                    } else {
                                        manager.setStream(type)
                                    }
                                },
                                shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                                color = if (selected) Red.copy(alpha = 0.18f) else SurfaceBg,
                                border = BorderStroke(1.dp, if (selected) Red.copy(alpha = 0.8f) else BorderDim),
                            ) {
                                Text(type.label, fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) Red else White, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    // PTT 按钮 —— 按住说话
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    haptic.hud()
                                    manager.talkDown()
                                    try {
                                        waitForUpOrCancellation()
                                    } finally {
                                        manager.talkUp()
                                    }
                                }
                            },
                        shape = CutCornerShape(topStart = 9.dp, bottomEnd = 9.dp),
                        color = if (talking) Danger else TacGreen.copy(alpha = 0.10f),
                        border = BorderStroke(1.5.dp, if (talking) Danger else TacGreen.copy(alpha = 0.6f)),
                    ) {
                        Text(
                            if (talking) "●●● 发送中 ●●●" else "按住说话 · PTT",
                            fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (talking) White else TacGreen,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 13.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    // 发送端真实统计：编码器没出帧时这里保持 0，不再“看起来一切正常”
                    Text(
                        "TX ${tx.frames}帧/${tx.fragments}片 · 失败 ${tx.failures} · 关键帧 ${tx.keyFrames} · CSD ${tx.configBroadcasts} · 重请求 ${tx.requestedKeyFrames}",
                        fontFamily = Mono, fontSize = 8.sp, color = Gray,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── 成员网格 ──
                if (members.isEmpty()) {
                    EmptyHint("等待局域网内其他成员入会…")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(members, key = { it.id }) { member ->
                            MemberTile(
                                member = member,
                                isMe = member.id == manager.deviceId,
                                myStream = myStream,
                                manager = manager,
                                rx = rxStats[member.id],
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                TermPanelLocal("会议说明") {
                    Text("同一 WiFi 内，每台设备广播自己的画面（前置/后置摄像头或屏幕），并同时接收所有成员的视频 —— 无互联网、无中心服务器", fontFamily = Sans, fontSize = 10.sp, color = Gray, lineHeight = 15.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("· 视频: H.264 640×480@15fps UDP 239.1.2.3:10091（组播）", fontFamily = Mono, fontSize = 9.sp, color = White)
                    Text("· 兜底: 每片同时逐成员单播；CSD 每 2s 重播，缺帧自动请求关键帧", fontFamily = Mono, fontSize = 9.sp, color = White)
                    Text("· 语音: PTT 半双工 8kHz PCM 单播", fontFamily = Mono, fontSize = 9.sp, color = White)
                    Text("· 发现: UDP 9998 定向广播/组播，6 秒超时", fontFamily = Mono, fontSize = 9.sp, color = White)
                    Spacer(Modifier.height(5.dp))
                    Text("· 限制: UDP 不保证送达，丢包会花屏/黑屏；关键帧重请求只能兜底，不能保证弱网流畅", fontFamily = Sans, fontSize = 9.sp, color = Warn, lineHeight = 13.sp)
                    Text("· 限制: AP 开启客户端隔离时广播/组播/单播都无法穿透，应用层没有绕过方法", fontFamily = Sans, fontSize = 9.sp, color = Warn, lineHeight = 13.sp)
                    Text("· 限制: 屏幕共享每次都需要系统授权；Android 14+ 一个授权会话只能创建一次虚拟显示，停止后需重新授权", fontFamily = Sans, fontSize = 9.sp, color = Warn, lineHeight = 13.sp)
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 会议内嵌面板（轻量版 TermPanel,标题行更紧凑） */
@Composable
private fun TermPanelLocal(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .background(SurfaceBg)
            .border(1.dp, BorderDim, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Red.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("▸ $title", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
        }
        Column(Modifier.padding(10.dp)) { content() }
    }
}

/** 成员视频格 —— 自己：占位+源徽章；他人：视频 TextureView */
@Composable
private fun MemberTile(
    member: ConfMember,
    isMe: Boolean,
    myStream: ConfStreamType,
    manager: xin.ctkqiang.deauthctrl.manager.ConferenceManager,
    rx: MemberRxStats?,
) {
    val streamTypeLabel = when (member.streamType) {
        "front" -> "前置"
        "back" -> "后置"
        "screen" -> "屏幕"
        else -> "无画面"
    }
    val talking = member.talking
    val showVideo = !isMe && member.streamType != "none"
    // 黑屏自诊：区分「没收到包 / 缺参数集 / 解码器未就绪 / 无信号」，
    // 不让一块永远黑的 Tile 看起来像正常播放。
    val rxHint: String? = if (isMe || !showVideo) null else when {
        rx == null -> "已连接 · 未收到视频数据"
        !rx.surfaceAttached -> "等待画面绑定"
        rx.decoderState.contains("失败") || rx.decoderState.contains("错误") -> rx.lastError ?: rx.decoderState
        rx.decoderState.startsWith("等待") -> rx.decoderState
        rx.lastFrameAgeMs > 3000 -> "无信号 ${rx.lastFrameAgeMs / 1000}s · 已请求关键帧"
        rx.rxFrames == 0L -> "已连接 · 未出图（等待关键帧）"
        rx.decodedFrames == 0L -> "解码器未就绪"
        else -> null
    }

    Surface(
        Modifier.fillMaxWidth().height(150.dp),
        shape = CutCornerShape(topStart = 9.dp, bottomEnd = 9.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, if (talking) Danger.copy(alpha = 0.8f) else if (isMe) Gold.copy(alpha = 0.5f) else BorderDim),
    ) {
        Box {
            if (showVideo) {
                key(member.id) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        manager.attachMemberSurface(member.id, st)
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        manager.detachMemberSurface(member.id)
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                // 占位：自己显示本机源状态
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        if (isMe) (if (myStream != ConfStreamType.NONE) "本机 · ${myStream.label}" else "本机 · 未开流") else "对方未开画面",
                        fontFamily = Mono, fontSize = 10.sp, color = Gray,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (isMe && myStream != ConfStreamType.NONE) {
                        Text("● 广播中", fontFamily = Mono, fontSize = 9.sp, color = TacGreen)
                    }
                }
            }

            // 接收端诊断叠层：对方信标说有画面但本机没出图时，明确指出卡在哪一层
            if (rxHint != null) {
                Column(
                    Modifier.fillMaxSize().background(Dark.copy(alpha = 0.55f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        rxHint,
                        fontFamily = Mono, fontSize = 9.sp, color = Warn,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            // 每成员接收统计：收包/解码/缺片，用于区分网络、重组、解码问题
            if (!isMe && showVideo && rx != null) {
                Text(
                    "收${rx.rxFrames}/解${rx.decodedFrames} 缺片${rx.missingFragments}",
                    fontFamily = Mono, fontSize = 7.sp,
                    color = if (rx.lastFrameAgeMs in 0..2000) TacGreen else Gray,
                    modifier = Modifier.align(Alignment.TopStart)
                        .background(Dark.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            // 底部信息条
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Dark.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(6.dp).clip(CircleShape).background(
                        if (talking) Danger else if (member.streamType != "none") TacGreen else Gray,
                    ),
                )
                Spacer(Modifier.width(5.dp))
                Text(member.name.take(10), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
                Text(streamTypeLabel, fontFamily = Mono, fontSize = 8.sp, color = Gold)
            }

            // 说话中四角锁定
            if (talking) {
                TacticalCorners(color = Danger, modifier = Modifier.fillMaxSize(), len = 7.dp, stroke = 1.2.dp)
            }
        }
    }
}
