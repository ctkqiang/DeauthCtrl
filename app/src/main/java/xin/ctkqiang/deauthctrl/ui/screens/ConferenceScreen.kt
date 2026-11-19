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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.R
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

    val fallbackOperatorName = stringResource(R.string.conf_default_operator_name)
    manager.deviceName = operatorName.ifBlank { fallbackOperatorName }

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
        TerminalHeader(stringResource(R.string.conf_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            // 状态条
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.conf_online_count, members.size), fontFamily = Mono, fontSize = 10.sp, color = TacGreen)
                    Text(stats, fontFamily = Mono, fontSize = 9.sp, color = Gray)
                    Text(
                        stringResource(if (running) R.string.conf_status_in else R.string.conf_status_out),
                        fontFamily = Mono, fontSize = 10.sp, color = if (running) TacGreen else Gray,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            ActionButton(
                running,
                stringResource(R.string.conf_btn_join),
                stringResource(R.string.conf_btn_leave),
            ) {
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
                TermPanelLocal(stringResource(R.string.conf_panel_local)) {
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
                                Text(stringResource(type.labelRes), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) Red else White, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
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
                            stringResource(if (talking) R.string.conf_ptt_talking else R.string.conf_ptt_idle),
                            fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (talking) White else TacGreen,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 13.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    // 发送端真实统计：编码器没出帧时这里保持 0，不再“看起来一切正常”
                    Text(
                        stringResource(
                            R.string.conf_tx_stats,
                            tx.frames, tx.fragments, tx.failures, tx.keyFrames, tx.configBroadcasts, tx.requestedKeyFrames,
                        ),
                        fontFamily = Mono, fontSize = 8.sp, color = Gray,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── 成员网格 ──
                if (members.isEmpty()) {
                    EmptyHint(stringResource(R.string.conf_empty_members))
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
                TermPanelLocal(stringResource(R.string.conf_panel_brief)) {
                    Text(stringResource(R.string.conf_brief_body), fontFamily = Sans, fontSize = 10.sp, color = Gray, lineHeight = 15.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(stringResource(R.string.conf_brief_video), fontFamily = Mono, fontSize = 9.sp, color = White)
                    Text(stringResource(R.string.conf_brief_fallback), fontFamily = Mono, fontSize = 9.sp, color = White)
                    Text(stringResource(R.string.conf_brief_voice), fontFamily = Mono, fontSize = 9.sp, color = White)
                    Text(stringResource(R.string.conf_brief_discovery), fontFamily = Mono, fontSize = 9.sp, color = White)
                    Spacer(Modifier.height(5.dp))
                    Text(stringResource(R.string.conf_brief_limit_udp), fontFamily = Sans, fontSize = 9.sp, color = Warn, lineHeight = 13.sp)
                    Text(stringResource(R.string.conf_brief_limit_ap), fontFamily = Sans, fontSize = 9.sp, color = Warn, lineHeight = 13.sp)
                    Text(stringResource(R.string.conf_brief_limit_screen), fontFamily = Sans, fontSize = 9.sp, color = Warn, lineHeight = 13.sp)
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
        "front" -> stringResource(R.string.conf_stream_front)
        "back" -> stringResource(R.string.conf_stream_back)
        "screen" -> stringResource(R.string.conf_stream_screen)
        else -> stringResource(R.string.conf_stream_none)
    }
    val talking = member.talking
    val showVideo = !isMe && member.streamType != "none"
    // 黑屏自诊：区分「没收到包 / 缺参数集 / 解码器未就绪 / 无信号」，
    // 不让一块永远黑的 Tile 看起来像正常播放。
    val rxHint: String? = if (isMe || !showVideo) null else when {
        rx == null -> stringResource(R.string.conf_rx_connected_no_data)
        !rx.surfaceAttached -> stringResource(R.string.conf_rx_wait_surface)
        rx.decoderState.contains("失败") || rx.decoderState.contains("错误") ->
            rx.lastError ?: decoderStateLabel(rx.decoderState)
        rx.decoderState.startsWith("等待") -> decoderStateLabel(rx.decoderState)
        rx.lastFrameAgeMs > 3000 -> stringResource(R.string.conf_rx_no_signal, rx.lastFrameAgeMs / 1000)
        rx.rxFrames == 0L -> stringResource(R.string.conf_rx_no_frame)
        rx.decodedFrames == 0L -> stringResource(R.string.conf_rx_decoder_not_ready)
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
                        if (isMe) {
                            if (myStream != ConfStreamType.NONE) {
                                stringResource(R.string.conf_tile_self_stream, stringResource(myStream.labelRes))
                            } else {
                                stringResource(R.string.conf_tile_self_no_stream)
                            }
                        } else {
                            stringResource(R.string.conf_tile_peer_no_video)
                        },
                        fontFamily = Mono, fontSize = 10.sp, color = Gray,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (isMe && myStream != ConfStreamType.NONE) {
                        Text(stringResource(R.string.conf_tile_broadcasting), fontFamily = Mono, fontSize = 9.sp, color = TacGreen)
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
                    stringResource(R.string.conf_rx_stats, rx.rxFrames, rx.decodedFrames, rx.missingFragments),
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

/** 解码器数据值（固定中文，供黑屏判定）→ 当前语言显示文本 */
@Composable
private fun decoderStateLabel(raw: String): String = when (raw) {
    "等待视频数据" -> stringResource(R.string.mgr_conf_decoder_wait_data)
    "未收到视频数据" -> stringResource(R.string.mgr_conf_decoder_no_data)
    "画面创建失败" -> stringResource(R.string.mgr_conf_decoder_surface_failed)
    "画面已绑定，等待参数集" -> stringResource(R.string.mgr_conf_decoder_surface_bound_wait_csd)
    "画面已解绑" -> stringResource(R.string.mgr_conf_decoder_surface_detached)
    "已连接，未收到视频数据" -> stringResource(R.string.mgr_conf_decoder_connected_no_data)
    "参数集变化，重建解码器" -> stringResource(R.string.mgr_conf_decoder_csd_changed)
    "等待画面绑定" -> stringResource(R.string.mgr_conf_decoder_wait_surface)
    "等待参数集(SPS/PPS)" -> stringResource(R.string.mgr_conf_decoder_wait_csd)
    "解码器就绪" -> stringResource(R.string.mgr_conf_decoder_ready)
    "解码器创建失败" -> stringResource(R.string.mgr_conf_decoder_create_failed)
    "解码错误" -> stringResource(R.string.mgr_conf_decoder_error)
    "解码中" -> stringResource(R.string.mgr_conf_decoder_decoding)
    else -> raw
}
