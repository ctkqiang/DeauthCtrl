package xin.ctkqiang.deauthctrl.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.hardware.usb.UsbManager
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.gridlines.LatLonGridlineOverlay2
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.DetectionStats
import xin.ctkqiang.deauthctrl.manager.DetectorStatus
import xin.ctkqiang.deauthctrl.manager.DiscoveryState
import xin.ctkqiang.deauthctrl.manager.LocationStatus
import xin.ctkqiang.deauthctrl.manager.NightTrack
import xin.ctkqiang.deauthctrl.manager.PhotoPeer
import xin.ctkqiang.deauthctrl.manager.PhotoRelayHost
import xin.ctkqiang.deauthctrl.manager.PhotoTarget
import xin.ctkqiang.deauthctrl.manager.ReceivedPhoto
import xin.ctkqiang.deauthctrl.manager.TacticalMarker
import xin.ctkqiang.deauthctrl.manager.TacticalSelfOverlay
import xin.ctkqiang.deauthctrl.manager.VoiceSdrManager
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
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TacticalCorners
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.TacticalStatStrip
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.IrRemoteViewModel
import xin.ctkqiang.deauthctrl.viewmodel.NfcViewModel
import xin.ctkqiang.deauthctrl.viewmodel.NightVisionViewModel
import xin.ctkqiang.deauthctrl.viewmodel.TacticalCameraViewModel
import xin.ctkqiang.deauthctrl.viewmodel.TacticalMapViewModel
import xin.ctkqiang.deauthctrl.viewmodel.GsmTowerViewModel
import xin.ctkqiang.deauthctrl.manager.MorseManager
import xin.ctkqiang.deauthctrl.manager.NfcTagInfo
import xin.ctkqiang.deauthctrl.viewmodel.SerialMonitorViewModel
import xin.ctkqiang.deauthctrl.viewmodel.MorseViewModel
import xin.ctkqiang.deauthctrl.viewmodel.VoiceSdrViewModel
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

// ══════════════════════════════════════════════════════════════════════════
// 战术相机 —— Camera2 最大分辨率 + Base64 局域网传输
// ══════════════════════════════════════════════════════════════════════════

@Composable
fun TacticalCameraScreen(
    vm: TacticalCameraViewModel,
    operatorName: String,
    back: () -> Unit,
    openPhotoId: Long? = null,
) {
    val manager = vm.manager
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // 进程级单例:Application 启动时已创建并占用 TCP/UDP 端口。
    // 页面只借用,不 new(重复绑定 9997/10088 会让双方都收不到对方),也不 stop。
    val host = remember { PhotoRelayHost.get(context.applicationContext) }
    val cameras = manager.cameras
    var selectedId by remember { mutableStateOf(cameras.firstOrNull()?.id ?: "") }
    var selectedPeerId by remember { mutableStateOf<String?>(null) } // null = 全部在线
    // 手动 IP 只对本次页面有效,不再写入持久配置
    var manualTargets by remember { mutableStateOf<List<PhotoTarget>>(emptyList()) }
    var manualDialog by remember { mutableStateOf(false) }
    val lastError by manager.lastError.collectAsState()
    val pending by manager.pendingShot.collectAsState()
    val peers by host.peers.collectAsState()
    val online by host.online.collectAsState()
    val serverPort by host.serverPort.collectAsState()
    val discovery by host.discovery.collectAsState()
    val layers by host.layers.collectAsState()
    val received by host.received.collectAsState()
    var viewerPhoto by remember { mutableStateOf<ReceivedPhoto?>(null) }
    /** 保存到相册的反馈:正数=刚保存成功的照片 id;负数=刚保存失败的 id */
    var savedPhotoId by remember { mutableStateOf<Long>(0L) }

    // 进入页面:注入呼号并临时开启发现(释放 MulticastLock / mDNS 在退出时归还)
    LaunchedEffect(Unit) { host.touch(operatorName.ifBlank { "operator" }) }
    DisposableEffect(Unit) {
        host.startDiscovery()
        onDispose { host.stopDiscovery() }
    }

    // 通知点击直达:自动打开对应照片
    LaunchedEffect(openPhotoId) {
        openPhotoId?.let { id ->
            host.photoById(id)?.let { viewerPhoto = it }
        }
    }

    // peer 被 TTL 清理后,清掉已失效的选择,避免对空气发送
    LaunchedEffect(peers) {
        if (selectedPeerId != null && peers.none { it.id == selectedPeerId }) selectedPeerId = null
    }

    val peerTargets = peers.map { PhotoTarget(it.ip, it.port, it.id, it.name) }
    val targets: List<PhotoTarget> = when {
        manualTargets.isNotEmpty() -> manualTargets
        selectedPeerId != null -> peerTargets.filter { it.id == selectedPeerId }
        else -> peerTargets
    }
    val targetLabel = when {
        manualTargets.isNotEmpty() -> stringResource(R.string.tact_manual_prefix, manualTargets.joinToString(",") { it.ip })
        selectedPeerId != null -> peerTargets.firstOrNull { it.id == selectedPeerId }?.let { "${it.name} ${it.ip}" } ?: stringResource(R.string.tact_target_offline)
        peers.isEmpty() -> stringResource(R.string.tact_waiting_receiver)
        else -> stringResource(R.string.tact_all_online, peers.size)
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.tact_camera_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            // ── 战术遥测条:在线节点/已收照片/目标/服务端口 ──
            TacticalStatStrip(
                listOf(
                    "${peers.size}" to stringResource(R.string.tact_cam_nodes),
                    "${received.size}" to stringResource(R.string.tact_cam_received),
                    (peers.firstOrNull { it.id == selectedPeerId }?.name ?: if (peers.isEmpty()) "--" else "ALL") to stringResource(R.string.tact_cam_target),
                    "$serverPort" to stringResource(R.string.tact_cam_port),
                ),
            )
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.tact_lens_select)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cameras.forEach { c ->
                        val selected = c.id == selectedId
                        Surface(
                            modifier = Modifier.clickable { selectedId = c.id },
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = if (selected) Red.copy(alpha = 0.16f) else SurfaceBg,
                            border = BorderStroke(1.dp, if (selected) Red.copy(alpha = 0.7f) else BorderDim),
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(c.name, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) Red else White)
                                Text("JPEG ${c.maxJpegSize.width}×${c.maxJpegSize.height}", fontFamily = Mono, fontSize = 8.sp, color = Gray)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 预览 —— 大取景框:占满镜头选择之外的全部剩余高度。
            // TextureView 内部 fit-center 等比缩放,任何分辨率都不拉伸变形
            Box(
                Modifier.fillMaxWidth().weight(1.35f)
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .background(SurfaceAlt)
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                if (selectedId.isNotEmpty()) {
                    androidx.compose.runtime.key(selectedId) {
                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).apply {
                                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                            manager.open(selectedId, st)
                                        }
                                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                            // fit-center:buffer 按比例缩放居中,任何设备任何分辨率都不拉伸变形
                                            val matrix = android.graphics.Matrix()
                                            val vw = width.toFloat()
                                            val vh = height.toFloat()
                                            if (w > 0 && h > 0 && vw > 0 && vh > 0) {
                                                val scale = minOf(vw / w, vh / h)
                                                matrix.setScale(scale, scale)
                                                matrix.postTranslate((vw - w * scale) / 2f, (vh - h * scale) / 2f)
                                                setTransform(matrix)
                                            }
                                        }
                                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { manager.close(); return true }
                                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    EmptyHint(stringResource(R.string.tact_no_camera))
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── 控制区(拍摄 / 发送 / 接收) —— 独立滚动,取景框保持固定高度 ──
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {

            // ── 第一步:拍摄 ──
            ActionButton(running = false, runLabel = stringResource(R.string.tact_capture_photo), stopLabel = "") {
                haptic.hud()
                manager.capture()
            }
            lastError?.let {
                Text(
                    it, fontFamily = Mono, fontSize = 9.sp,
                    // 错误/警告文本统一以 [!] 前缀开头;成功状态不带前缀
                    color = if (!it.startsWith("[!]")) TacGreen else Danger,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ── 第二步:确认 + 选择目标 + 发送 ──
            pending?.let { shot ->
                Spacer(Modifier.height(8.dp))
                TermPanel(stringResource(R.string.tact_captured_pending)) {
                    val bmp = remember(shot.timeMs) { BitmapFactory.decodeByteArray(shot.jpeg, 0, shot.jpeg.size) }
                    bmp?.let {
                        Image(it.asImageBitmap(), contentDescription = stringResource(R.string.tact_cd_captured_photo), modifier = Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height.toFloat()))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${shot.width}×${shot.height} · ${shot.jpeg.size / 1024}KB · ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(shot.timeMs))}", fontFamily = Mono, fontSize = 9.sp, color = Gray)

                    Spacer(Modifier.height(8.dp))

                    // 目标全部来自自动发现;手动 IP 只作为本页临时目标
                    Text(stringResource(R.string.tact_send_target_auto), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Red)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        val allSelected = selectedPeerId == null && manualTargets.isEmpty()
                        Surface(
                            modifier = Modifier.clickable { selectedPeerId = null; manualTargets = emptyList() },
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = if (allSelected) Red.copy(alpha = 0.16f) else SurfaceBg,
                            border = BorderStroke(1.dp, if (allSelected) Red.copy(alpha = 0.7f) else BorderDim),
                        ) {
                            Text(stringResource(R.string.tact_all_count, peers.size), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (allSelected) Red else White, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                        }
                        peers.forEach { peer ->
                            val selected = peer.id == selectedPeerId
                            val ageSec = ((System.currentTimeMillis() - peer.lastSeenMs) / 1000L).coerceAtLeast(0L)
                            Surface(
                                modifier = Modifier.clickable {
                                    selectedPeerId = if (selected) null else peer.id
                                    if (!selected) manualTargets = emptyList()
                                },
                                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                color = if (selected) Red.copy(alpha = 0.16f) else SurfaceBg,
                                border = BorderStroke(1.dp, if (selected) Red.copy(alpha = 0.7f) else BorderDim),
                            ) {
                                Column(Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
                                    Text(peer.name.take(12), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) Red else White)
                                    Text("${peer.ip}:${peer.port} · ${ageSec}s", fontFamily = Mono, fontSize = 8.sp, color = Gray)
                                }
                            }
                        }
                        manualTargets.takeIf { it.isNotEmpty() }?.let { manual ->
                            Surface(
                                modifier = Modifier.clickable { manualTargets = emptyList() },
                                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                color = Gold.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, Gold.copy(alpha = 0.7f)),
                            ) {
                                Column(Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
                                    Text(stringResource(R.string.tact_manual_dot, manual.joinToString(",") { it.ip }), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold)
                                    Text(stringResource(R.string.tact_temp_target_cancel), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                                }
                            }
                        }
                        if (peers.isEmpty() && manualTargets.isEmpty()) {
                            Text(stringResource(R.string.tact_no_auto_targets), fontFamily = Mono, fontSize = 9.sp, color = Warn, modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    if (targets.isEmpty()) {
                        // 0 个目标时禁用发送 —— 不再让用户点了以后才知道没发出去
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                            color = SurfaceBg,
                            border = BorderStroke(1.dp, BorderDim),
                        ) {
                            Text(stringResource(R.string.tact_waiting_receiver), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
                        }
                        Text(stringResource(R.string.tact_peer_hint), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp, modifier = Modifier.padding(top = 3.dp))
                    } else {
                        ActionButton(running = false, runLabel = stringResource(R.string.tact_send_photo_to, targetLabel), stopLabel = "") {
                            haptic.hud()
                            manager.sendPending(targets, operatorName.ifBlank { "operator" })
                        }
                        Text(stringResource(R.string.tact_delivery_ack_hint), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); manager.clearPending() },
                        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Text(stringResource(R.string.tact_discard_photo), fontFamily = Sans, fontSize = 10.sp, color = Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 在线接收台状态(进程级,非前台服务) ──
            TermPanel(stringResource(R.string.tact_receiver_panel)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (online) stringResource(R.string.tact_port_listening, serverPort) else stringResource(R.string.tact_receiver_not_ready),
                        fontFamily = Mono, fontSize = 9.sp,
                        color = if (online) TacGreen else Danger,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.tact_online_count, peers.size), fontFamily = Mono, fontSize = 9.sp, color = Gold)
                }
                Text(
                    stringResource(R.string.tact_process_alive_hint),
                    fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    discoveryLabel(discovery),
                    fontFamily = Mono, fontSize = 9.sp,
                    color = if (peers.isNotEmpty()) TacGreen else Warn,
                )
                Text(
                    stringResource(R.string.tact_layers_line, layers.mdns, layers.udp, layers.cacheProbe, layers.scanned, layers.scanTotal),
                    fontFamily = Mono, fontSize = 8.sp, color = Gray,
                    lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp),
                )
                if (peers.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.tact_troubleshoot),
                        fontFamily = Sans, fontSize = 8.sp, color = Warn, lineHeight = 11.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HudChip(stringResource(R.string.tact_rescan), TacGreen) { haptic.hud(); host.rescan() }
                    // 高级手动 IP 只在自动发现为空时出现,且不会持久保存
                    if (peers.isEmpty()) {
                        HudChip(stringResource(R.string.tact_manual_ip), Gold) { haptic.hud(); manualDialog = true }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── 接收照片列表 ──
            TermPanel(stringResource(R.string.tact_received_photos_panel, received.size)) {
                if (received.isEmpty()) {
                    EmptyHint(stringResource(R.string.tact_received_empty))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(received, key = { it.id }) { photo ->
                            Surface(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { haptic.hud(); viewerPhoto = photo },
                                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                                color = SurfaceBg,
                                border = BorderStroke(1.dp, BorderDim),
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text("${photo.from} · ${photo.width}×${photo.height} · ${photo.jpeg.size / 1024}KB · ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(photo.receivedMs))}", fontFamily = Mono, fontSize = 9.sp, color = Gold)
                                    Spacer(Modifier.height(4.dp))
                                    val bmp = remember(photo.id) { BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size) }
                                    bmp?.let {
                                        Image(
                                            it.asImageBitmap(),
                                            contentDescription = stringResource(R.string.tact_cd_received_photo),
                                            modifier = Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height.toFloat()),
                                        )
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // 下载保存到相册:保存过的照片不受 TTL 自动删除
                                        Surface(
                                            modifier = Modifier.weight(1f).clickable {
                                                haptic.hud()
                                                savedPhotoId = if (host.savePhotoToGallery(photo.id)) photo.id else -photo.id
                                            },
                                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                            color = if (photo.savedToGallery) TacGreen.copy(alpha = 0.12f) else Red.copy(alpha = 0.1f),
                                            border = BorderStroke(1.dp, if (photo.savedToGallery) TacGreen.copy(alpha = 0.5f) else BorderDim),
                                        ) {
                                            Text(
                                                when {
                                                    photo.savedToGallery -> stringResource(R.string.tact_saved_to_gallery)
                                                    savedPhotoId == -photo.id -> stringResource(R.string.tact_save_failed)
                                                    else -> stringResource(R.string.tact_download_save)
                                                },
                                                fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                                color = if (photo.savedToGallery) TacGreen else Red,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 6.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        if (photo.savedToGallery) stringResource(R.string.tact_downloaded_kept) else stringResource(R.string.tact_memory_ttl, (10 - (System.currentTimeMillis() - photo.receivedMs) / 60_000).coerceAtLeast(0)),
                                        fontFamily = Sans, fontSize = 7.sp, color = Gray.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            }
        }
    }

    // ── 全屏查看器(典型相册体验:点击关闭) ──
    viewerPhoto?.let { photo ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { viewerPhoto = null }) {
            Box(
                Modifier.fillMaxSize().background(Color.Black).clickable { viewerPhoto = null },
                contentAlignment = Alignment.Center,
            ) {
                val bmp = remember(photo.id) { BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size) }
                bmp?.let {
                    Image(it.asImageBitmap(), contentDescription = stringResource(R.string.tact_cd_fullscreen_photo), modifier = Modifier.fillMaxWidth())
                }
                Column(Modifier.align(Alignment.TopCenter).padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.tact_from_sender, photo.from), fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = White)
                    Text(stringResource(R.string.tact_viewer_meta, photo.width, photo.height, photo.jpeg.size / 1024), fontFamily = Mono, fontSize = 9.sp, color = Gray)
                }
            }
        }
    }

    // ── 高级手动 IP:仅在自动发现为空时可用,不持久保存 ──
    if (manualDialog) {
        var ipInput by remember { mutableStateOf("") }
        var ipError by remember { mutableStateOf<String?>(null) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { manualDialog = false }) {
            Surface(
                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                color = Dark,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(R.string.tact_manual_ip_advanced), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.tact_manual_ip_desc),
                        fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    TermInput(stringResource(R.string.tact_ipv4_address), ipInput, placeholder = "192.168.1.23") { ipInput = it; ipError = null }
                    ipError?.let {
                        Text(it, fontFamily = Mono, fontSize = 9.sp, color = Danger, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HudChip(stringResource(R.string.tact_cancel), Gray) { manualDialog = false }
                        HudChip(stringResource(R.string.tact_use_ip), Gold) {
                            val ip = normalizeIpv4(ipInput)
                            if (ip == null) {
                                ipError = context.getString(R.string.tact_invalid_ipv4)
                            } else {
                                manualTargets = listOf(
                                    PhotoTarget(ip = ip, id = "manual:$ip", name = context.getString(R.string.tact_manual_name, ip)),
                                )
                                selectedPeerId = null
                                manualDialog = false
                                haptic.hud()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 发现状态的人话描述 —— 阶段、耗时、层状态都来自 PhotoRelayHost 的 StateFlow */
@Composable
private fun discoveryLabel(state: DiscoveryState): String = when (state) {
    is DiscoveryState.Idle -> stringResource(R.string.tact_discovery_idle)
    is DiscoveryState.Searching -> stringResource(
        R.string.tact_discovery_searching, state.stage, state.elapsedMs / 1000, state.found,
    )
    is DiscoveryState.Found -> stringResource(R.string.tact_discovery_found, state.count)
    is DiscoveryState.Exhausted -> stringResource(R.string.tact_discovery_exhausted, state.reason)
}

/** 严格 IPv4 校验:4 段十进制,每段 0-255;返回规范化字符串,失败返回 null */
private fun normalizeIpv4(raw: String): String? {
    val parts = raw.trim().split(".")
    if (parts.size != 4) return null
    val octets = parts.map { part ->
        if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
        part.toIntOrNull() ?: return null
    }
    if (octets.any { it !in 0..255 }) return null
    return octets.joinToString(".")
}

@Composable
private fun HudChip(label: String, accent: Color = TacGreen, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
    ) {
        Text(
            label, fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = accent, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun HudToggle(label: String, selected: Boolean, accent: Color = TacGreen, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else SurfaceBg,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.75f) else BorderDim),
    ) {
        Text(
            label, fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = if (selected) accent else Gray,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun MapZoomButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val accent = if (enabled) TacGreen else Gray.copy(alpha = 0.35f)
    Surface(
        modifier = Modifier
            .size(40.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
        color = Dark.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

/** 18dp 战术菱形 marker 图标(自绘 Path,避免额外 drawable 资源) */
private fun diamondMarkerIcon(density: Float, sizeDp: Int, color: Int): android.graphics.drawable.Drawable {
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(4)
    return android.graphics.drawable.ShapeDrawable(object : android.graphics.drawable.shapes.Shape() {
        private val path = android.graphics.Path()

        override fun onResize(width: Float, height: Float) {
            path.reset()
            path.moveTo(width / 2f, 0f)
            path.lineTo(width, height / 2f)
            path.lineTo(width / 2f, height)
            path.lineTo(0f, height / 2f)
            path.close()
        }

        override fun draw(canvas: android.graphics.Canvas, paint: android.graphics.Paint) {
            canvas.drawPath(path, paint)
        }
    }).apply {
        paint.color = color
        paint.isAntiAlias = true
        setIntrinsicWidth(sizePx)
        setIntrinsicHeight(sizePx)
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 语音频谱分析器 —— SDR 风格实时频谱 + 热力瀑布
// ══════════════════════════════════════════════════════════════════════════

/**
 * SDR 风格频谱:专业读数(SPAN/RBW/噪声底)、频谱填充曲线 +
 * 金色 max-hold 轨迹、热力瀑布(黑→蓝→青→绿→黄→红,新帧顶部进入)。
 */
@Composable
fun VoiceSdrScreen(vm: VoiceSdrViewModel, back: () -> Unit) {
    val manager = vm.manager
    val haptic = LocalHapticFeedback.current
    val running by manager.running.collectAsState()
    val frame by manager.frame.collectAsState()
    val error by manager.error.collectAsState()
    val band by manager.band.collectAsState()
    val usAlarm by manager.ultrasonicAlarm.collectAsState()

    // 瀑布历史:最近 110 帧(新帧在顶部,历史向下滚动 —— SDR 惯例)
    val waterfall = remember { mutableStateListOf<FloatArray>() }
    // 瀑布冻结:FRZ 开关按住现场,不吞新帧历史
    var frozen by remember { mutableStateOf(false) }
    LaunchedEffect(frame) {
        frame?.let { f ->
            if (frozen) return@LaunchedEffect
            waterfall.add(0, f.bins)
            if (waterfall.size > 110) waterfall.removeAt(waterfall.size - 1)
        }
    }

    // 信号列表:每帧提取噪声底以上的局部峰(间距 ≥3 bin),取前 8 —— 真实 SDR 的信号清单
    val signals = remember { mutableStateListOf<SdrSignalRow>() }
    LaunchedEffect(frame) {
        frame?.let { f ->
            val peaks = mutableListOf<Pair<Int, Float>>()
            for (i in 1 until f.bins.size - 1) {
                val b = f.bins[i]
                if (b > f.noiseFloorDb + 8f && b >= f.bins[i - 1] && b >= f.bins[i + 1]) peaks.add(i to b)
            }
            val merged = mutableListOf<Pair<Int, Float>>()
            for ((idx, db) in peaks.sortedByDescending { it.second }) {
                if (merged.none { kotlin.math.abs(it.first - idx) < 3 }) merged.add(idx to db)
                if (merged.size >= 8) break
            }
            val prev = signals.associate { (it.hz / 100f).toInt() to it.db }
            signals.clear()
            merged.sortedBy { it.first }.forEach { (idx, db) ->
                val hz = idx * 22050f / f.bins.size
                val prevDb = prev[(hz / 100f).toInt()]
                signals.add(SdrSignalRow(hz, db, if (prevDb != null) db - prevDb else 0f))
            }
        }
    }

    // 峰警报:峰值越过操作员设定阈值即告警(上升沿触发一次触觉 + 横幅)
    var alarmThreshold by remember { mutableFloatStateOf(-35f) }
    var peakAlarm by remember { mutableStateOf(false) }
    // 频谱显示频窗:0 全频 / 1 人声 0.3-3.4k / 2 超声 15-22.05k(纯视觉缩放)
    var viewBand by remember { mutableIntStateOf(0) }
    // 信号事件日志:告警与强信号时间线(同频 2s 去抖,进程内存)
    val signalLog = remember { mutableStateListOf<SdrEventRow>() }
    val lastEventAt = remember { mutableMapOf<Int, Long>() }
    LaunchedEffect(frame) {
        frame?.let { f ->
            val over = f.peakDb > alarmThreshold
            if (over && !peakAlarm) {
                peakAlarm = true
                haptic.hud(HapticFeedbackType.LongPress)
            } else if (!over && peakAlarm) {
                peakAlarm = false
            }
            // 事件日志:上升沿告警 + 强信号(>-30dB)
            if (over || f.peakDb > -30f) {
                val key = (f.peakHz / 2000f).toInt()
                val now = System.currentTimeMillis()
                val last = lastEventAt[key] ?: 0L
                if (now - last > 2000L) {
                    lastEventAt[key] = now
                    signalLog.add(0, SdrEventRow(now, f.peakHz, f.peakDb, over))
                    if (signalLog.size > 60) signalLog.removeAt(signalLog.size - 1)
                }
            }
        }
    }

    // max-hold 轨迹(金色)
    val maxHold = remember { mutableStateOf(FloatArray(VoiceSdrManager.BINS) { -120f }) }
    LaunchedEffect(frame) {
        frame?.let { f ->
            val hold = maxHold.value
            for (i in hold.indices) {
                if (f.bins[i] > hold[i]) hold[i] = f.bins[i]
            }
            maxHold.value = hold
        }
    }

    // 进入页面自动开启侦测(麦克风独占,离开页面即释放);按钮保留手动接管
    DisposableEffect(Unit) {
        if (!manager.running.value) manager.start()
        onDispose { manager.stop() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.tact_voice_sdr_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            // 频段模式:全频段 / 超声聚焦(15-22.05kHz)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to stringResource(R.string.tact_band_full), 1 to stringResource(R.string.tact_band_ultrasonic)).forEach { (b, label) ->
                    val on = band == b
                    Surface(
                        modifier = Modifier.clickable { haptic.hud(); manager.setBand(b) },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = if (on) Red.copy(alpha = 0.15f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (on) Red.copy(alpha = 0.7f) else BorderDim),
                    ) {
                        Text(label, fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (on) Red else Gray, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            // ── 显示频窗:全频 / 人声 / 超声(频谱横轴缩放,纯视觉) ──
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(0 to stringResource(R.string.tact_viewband_full), 1 to stringResource(R.string.tact_viewband_voice), 2 to stringResource(R.string.tact_viewband_us)).forEach { (v, label) ->
                    val on = viewBand == v
                    Surface(
                        modifier = Modifier.clickable { haptic.hud(); viewBand = v },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = if (on) Gold.copy(alpha = 0.13f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (on) Gold.copy(alpha = 0.6f) else BorderDim),
                    ) {
                        Text(label, fontFamily = Mono, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (on) Gold else Gray, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                    }
                }
            }
            if (usAlarm) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                    color = Danger.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.6f)),
                ) {
                    Text(
                        stringResource(R.string.tact_ultrasonic_alarm),
                        fontFamily = Sans, fontSize = 9.sp, color = Danger, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
            // 峰警报横幅:峰值越过阈值(上升沿触发触觉)
            if (peakAlarm) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                    color = Warn.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Warn.copy(alpha = 0.6f)),
                ) {
                    Text(
                        stringResource(R.string.tact_sdr_peak_alarm, "%.0f".format(frame?.peakDb ?: 0f)),
                        fontFamily = Mono, fontSize = 9.sp, color = Warn, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
            // 峰警报阈值:滑杆实时可调(-70..-10 dB)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tact_sdr_threshold), fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.width(96.dp))
                Slider(
                    value = alarmThreshold,
                    onValueChange = { alarmThreshold = it },
                    valueRange = -70f..-10f,
                    modifier = Modifier.weight(1f).height(26.dp),
                    colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = SurfaceAlt),
                )
                Text("%.0f dB".format(alarmThreshold), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
            }
            ActionButton(running, stringResource(R.string.tact_start_spectrum), stringResource(R.string.tact_stop_detection)) {
                haptic.hud()
                if (running) manager.stop() else manager.start()
            }
            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 9.sp, color = Danger, modifier = Modifier.padding(top = 4.dp)) }

            Spacer(Modifier.height(6.dp))
            // ── 战术遥测条(雷达同款) ──
            TacticalStatStrip(
                listOf(
                    (frame?.let { "%.0f dB".format(it.peakDb) } ?: "--") to stringResource(R.string.tact_peak),
                    (frame?.let { "%.0f dB".format(it.noiseFloorDb) } ?: "--") to stringResource(R.string.tact_noise_floor),
                    (frame?.let { "%.0f dB".format(it.rmsDb) } ?: "--") to "RMS",
                    (if (running) stringResource(R.string.tact_strip_on) else stringResource(R.string.tact_strip_off)) to stringResource(R.string.tact_strip_sniff),
                ),
            )
            Spacer(Modifier.height(6.dp))

            // ── 事件日志(告警/强信号时间线,最近 6 条) ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.tact_sdr_event_log), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Warn)
                Text(stringResource(R.string.tact_sdr_event_count, signalLog.size), fontFamily = Mono, fontSize = 7.5.sp, color = Gray)
            }
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    if (signalLog.isEmpty()) {
                        Text(stringResource(R.string.tact_sdr_event_empty), fontFamily = Sans, fontSize = 8.sp, color = Gray, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        signalLog.take(6).forEach { e ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(e.timeMs)), fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.weight(1f))
                                Text("%.2fkHz".format(e.hz / 1000f), fontFamily = Mono, fontSize = 8.sp, color = White, modifier = Modifier.width(70.dp))
                                Text("%.0fdB".format(e.db), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (e.alarm) Danger else Warn, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
                                if (e.alarm) Text(stringResource(R.string.tact_sdr_alarm_tag), fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Danger, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 专业读数条 ──
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val f = frame
                    SdrReadout(stringResource(R.string.tact_sdr_center), "11.025 kHz")
                    SdrReadout(stringResource(R.string.tact_sdr_span), "22.05 kHz")
                    SdrReadout(stringResource(R.string.tact_sdr_rbw), "10.77 Hz")
                    SdrReadout(stringResource(R.string.tact_sdr_pwr), if (f != null) "%.0f dB".format(f.rmsDb) else "--")
                    SdrReadout(stringResource(R.string.tact_noise_floor), if (f != null) "%.0f dB".format(f.noiseFloorDb) else "--")
                    SdrReadout(stringResource(R.string.tact_peak), if (f != null) "%.1f kHz".format(f.peakHz / 1000f) else "--")
                }
            }

            Spacer(Modifier.height(8.dp))

            // 频谱面板标题条(真实 SDR 的通道标识 + 快捷操作)
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tact_sdr_spectrum_header), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TacGreen)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.tact_sdr_hold_reset),
                        fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold,
                        modifier = Modifier.clickable {
                            haptic.hud()
                            maxHold.value = FloatArray(VoiceSdrManager.BINS) { -120f }
                        }.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                    Text(
                        stringResource(if (frozen) R.string.tact_sdr_unfreeze else R.string.tact_sdr_freeze),
                        fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (frozen) Red else Gray,
                        modifier = Modifier.clickable { haptic.hud(); frozen = !frozen }.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                    Text(if (running) stringResource(R.string.tact_sdr_rec) else stringResource(R.string.tact_sdr_stby), fontFamily = Mono, fontSize = 8.sp, color = if (running) Red else Gray)
                }
            }

            // ── 实时频谱 + max-hold ──
            Box(
                Modifier.fillMaxWidth().weight(0.46f)
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .background(Color(0xFF02090F))
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                val scaleMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                Canvas(Modifier.fillMaxSize().padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 2.dp)) {
                    val lo = -110f
                    val hi = -10f
                    fun yOf(db: Float): Float = size.height * (1f - (db - lo) / (hi - lo))

                    // 横向 dB 网格 + 左轴标签
                    for (db in -100 downTo -20 step 10) {
                        val y = yOf(db.toFloat())
                        drawLine(Gray.copy(alpha = 0.14f), Offset(0f, y), Offset(size.width, y), 0.7f)
                        val m = scaleMeasurer.measure(
                            "${db}", androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 7.sp, color = Gray),
                        )
                        drawText(m, topLeft = Offset(0f, y - m.size.height / 2f))
                    }
                    // 纵轴单位角标(dBFS)
                    val unit = scaleMeasurer.measure(
                        "dBFS", androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 7.sp, color = Gray.copy(alpha = 0.8f)),
                    )
                    drawText(unit, topLeft = Offset(size.width - unit.size.width, 0f))
                    // 显示频窗(全频/人声/超声)的 kHz 边界与 x 映射
                    val fLoK = when (viewBand) { 1 -> 0.3f; 2 -> 15f; else -> 0f }
                    val fHiK = when (viewBand) { 1 -> 3.4f; 2 -> 22.05f; else -> 22.05f }
                    fun xOfK(khz: Float): Float = size.width * ((khz - fLoK) / (fHiK - fLoK)).coerceIn(0f, 1f)
                    fun xOfBin(i: Int, n: Int): Float = xOfK(i * 22.05f / n)
                    // 纵向频率网格
                    for (g in 1..3) {
                        val x = size.width * g / 4f
                        drawLine(Gray.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, size.height), 0.7f)
                    }

                    // 噪声底参考虚线(实时)
                    frame?.let { f ->
                        val ny = yOf(f.noiseFloorDb.coerceIn(lo, hi))
                        drawLine(Warn.copy(alpha = 0.35f), Offset(0f, ny), Offset(size.width, ny), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                        val m = scaleMeasurer.measure(
                            "NF", androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 7.sp, color = Warn),
                        )
                        drawText(m, topLeft = Offset(size.width - m.size.width - 2f, ny + 2f))
                    }

                    // 频谱填充(底部到曲线,按频窗切片)
                    frame?.let { f ->
                        val n = f.bins.size
                        val loBin = (fLoK / 22.05f * n).toInt().coerceIn(0, n - 1)
                        val hiBin = (fHiK / 22.05f * n).toInt().coerceIn(loBin + 1, n - 1)
                        val path = Path()
                        var first = true
                        for (i in loBin..hiBin) {
                            val x = xOfBin(i, n)
                            val y = yOf(f.bins[i].coerceIn(lo, hi))
                            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                        }
                        path.lineTo(size.width, size.height)
                        path.lineTo(0f, size.height)
                        path.close()
                        drawPath(path, TacGreen.copy(alpha = 0.16f))
                        drawPath(path, TacGreen, style = Stroke(1.2f))
                    }

                    // max-hold 金色轨迹(同窗切片)
                    val hold = maxHold.value
                    if (hold.isNotEmpty() && running) {
                        val n = hold.size
                        val loBin = (fLoK / 22.05f * n).toInt().coerceIn(0, n - 1)
                        val hiBin = (fHiK / 22.05f * n).toInt().coerceIn(loBin + 1, n - 1)
                        val hp = Path()
                        var first = true
                        for (i in loBin..hiBin) {
                            val x = xOfBin(i, n)
                            val y = yOf(hold[i].coerceIn(lo, hi))
                            if (first) { hp.moveTo(x, y); first = false } else hp.lineTo(x, y)
                        }
                        drawPath(hp, Gold.copy(alpha = 0.75f), style = Stroke(1.6f))
                    }

                    // 峰值标记(峰在窗口内才画)
                    frame?.let { f ->
                        val pk = f.peakHz / 1000f
                        if (pk in fLoK..fHiK) {
                            val px = xOfK(pk)
                            val py = yOf(f.peakDb.coerceIn(lo, hi))
                            drawCircle(Gold, 3.5f, Offset(px, py))
                            drawLine(Gold.copy(alpha = 0.6f), Offset(px, py), Offset(px, 0f), 1f)
                            val m = scaleMeasurer.measure(
                                "%.1fkHz %.0fdB".format(pk, f.peakDb),
                                androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 8.sp, color = Gold),
                            )
                            drawText(m, topLeft = Offset((px + 5f).coerceAtMost(size.width - m.size.width), py - m.size.height))
                        }
                    }

                    // 频率轴刻度:窗口内 4 个刻度
                    for (g in 0..3) {
                        val khz = fLoK + (fHiK - fLoK) * g / 3f
                        val x = xOfK(khz)
                        val m = scaleMeasurer.measure(
                            if (khz >= 10f) "%.0fk".format(khz) else "%.1fk".format(khz),
                            androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 8.sp, color = Gray),
                        )
                        drawText(m, topLeft = Offset((x - m.size.width / 2f).coerceIn(0f, size.width - m.size.width), size.height - m.size.height))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── 信号列表:噪声底以上的活跃峰(真实 SDR 的信号清单) ──
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.tact_sdr_signals_header), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
                Text(stringResource(R.string.tact_sdr_signals_hint), fontFamily = Mono, fontSize = 7.5.sp, color = Gray)
            }
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    if (signals.isEmpty()) {
                        Text(stringResource(R.string.tact_sdr_no_signals), fontFamily = Sans, fontSize = 8.sp, color = Gray, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        signals.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("%.2f kHz".format(s.hz / 1000f), fontFamily = Mono, fontSize = 9.sp, color = White, modifier = Modifier.weight(1f))
                                Text(
                                    "%.0f dB".format(s.db),
                                    fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                    color = if (s.db > -30) Danger else if (s.db > -50) Warn else TacGreen,
                                    modifier = Modifier.width(64.dp), textAlign = TextAlign.End,
                                )
                                val d = s.delta
                                Text(
                                    if (d > 0.5f) "▲%.0f".format(d) else if (d < -0.5f) "▼%.0f".format(-d) else "·",
                                    fontFamily = Mono, fontSize = 8.sp,
                                    color = if (d > 0.5f) TacGreen else if (d < -0.5f) Danger else Gray,
                                    modifier = Modifier.width(48.dp), textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // 瀑布面板标题条
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.tact_sdr_waterfall_header), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
                Text(stringResource(R.string.tact_frames_history, waterfall.size), fontFamily = Mono, fontSize = 8.sp, color = Gray)
            }

            // ── 热力瀑布(新帧顶部进入) ──
            Box(
                Modifier.fillMaxWidth().weight(0.44f)
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .background(Color(0xFF02090F))
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (waterfall.isEmpty()) return@Canvas
                    // 折叠:每列约 4 个 bin 取最大,减到 ~512 列,保持帧率
                    val cols = 512
                    val rowH = size.height / 110f
                    waterfall.forEachIndexed { row, bins ->
                        val y = row * rowH
                        for (col in 0 until cols) {
                            var maxDb = -120f
                            val start = col * bins.size / cols
                            val end = ((col + 1) * bins.size / cols).coerceAtMost(bins.size)
                            for (i in start until end) {
                                if (bins[i] > maxDb) maxDb = bins[i]
                            }
                            val t = ((maxDb + 110f) / 100f).coerceIn(0f, 1f)
                            val x = size.width * col / cols
                            val w = size.width / cols + 0.6f
                            drawLine(sdrHeatColor(t), Offset(x, y), Offset(x + w, y + rowH), w)
                        }
                    }
                }
            }
        }
    }
}

/** SDR 读数格:数值在上,标签在下 */
@Composable
private fun SdrReadout(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TacGreen)
        Text(label, fontFamily = Sans, fontSize = 7.sp, color = Gray)
    }
}

/** 信号列表行:频率 / 功率 / 相对上帧变化 */
private data class SdrSignalRow(val hz: Float, val db: Float, val delta: Float)

/** 信号事件日志行:时间 / 频率 / 功率 / 是否告警 */
private data class SdrEventRow(val timeMs: Long, val hz: Float, val db: Float, val alarm: Boolean)

/**
 * SDR 瀑布热力色阶:黑(噪底) → 深蓝 → 青 → 绿 → 黄 → 红(最强)
 * 与真实频谱瀑布一致的黑体辐射式配色。
 */
private fun sdrHeatColor(t: Float): Color {
    val stops = listOf(
        0.00f to Color(0xFF000000),
        0.20f to Color(0xFF001A66),
        0.40f to Color(0xFF0066FF),
        0.55f to Color(0xFF00CCDD),
        0.70f to Color(0xFF00E43C),
        0.85f to Color(0xFFFFDD33),
        1.00f to Color(0xFFFF3B30),
    )
    for (i in 0 until stops.size - 1) {
        val (t0, c0) = stops[i]
        val (t1, c1) = stops[i + 1]
        if (t in t0..t1) {
            val k = ((t - t0) / (t1 - t0)).coerceIn(0f, 1f)
            return Color(
                red = c0.red + (c1.red - c0.red) * k,
                green = c0.green + (c1.green - c0.green) * k,
                blue = c0.blue + (c1.blue - c0.blue) * k,
            )
        }
    }
    return Color(0xFFFF3B30)
}


/**
 * 基站监视页 —— TelephonyManager.allCellInfo 的真实邻区数据
 *
 * 注册小区 + 邻区按信号排序;「最近基站」即信号最强小区。
 * 距离为 RSRP 路径损耗粗估(±数百米量级),方位无硬件可测 —— 不提供不编造。
 */
@Composable
fun GsmTowerScreen(back: () -> Unit) {
    val vm: GsmTowerViewModel = viewModel()
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) { vm.refresh() }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.tact_cell_tower_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(state.operator.ifBlank { stringResource(R.string.tact_reading_operator) }, fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = White)
                        Text(
                            stringResource(R.string.tact_cell_count, state.networkType.ifBlank { "" }, state.towers.size) +
                                if (state.updatedAtMs > 0) " · ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(state.updatedAtMs))}" else "",
                            fontFamily = Mono, fontSize = 9.sp, color = Gray,
                        )
                    }
                    Surface(
                        modifier = Modifier.clickable { haptic.hud(); vm.refresh() },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = TacGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.6f)),
                    ) {
                        Text(stringResource(R.string.tact_refresh), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacGreen, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            state.error?.let { Text("[!] $it", fontFamily = Sans, fontSize = 10.sp, color = Danger, modifier = Modifier.padding(vertical = 4.dp)) }

            if (state.towers.isEmpty() && state.error == null) {
                EmptyHint(stringResource(R.string.tact_cell_waiting))
            } else {
                state.towers.forEachIndexed { i, t ->
                    val typeColor = when (t.type) {
                        "NR" -> Gold
                        "LTE" -> Red
                        "WCDMA" -> Warn
                        else -> TacGreen
                    }
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = if (t.isRegistered) typeColor.copy(alpha = 0.06f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (t.isRegistered) typeColor.copy(alpha = 0.6f) else BorderDim),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                                    color = typeColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, typeColor.copy(alpha = 0.7f)),
                                ) {
                                    Text(t.type, fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = typeColor, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(if (t.isRegistered) stringResource(R.string.tact_registered_cell) else stringResource(R.string.tact_neighbor_cell), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (t.isRegistered) typeColor else Gray, modifier = Modifier.weight(1f))
                                Text("#${i + 1}", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                GsmCellStat(stringResource(R.string.tact_cell_id), t.cid.toString())
                                GsmCellStat("TAC/LAC", t.tacLac.toString())
                                GsmCellStat("PCI/PSC", t.pci?.toString() ?: "—")
                                GsmCellStat(stringResource(R.string.tact_operator), t.mccMnc.ifBlank { "—" })
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                GsmCellStat("RSRP", t.rsrpDbm?.let { "$it dBm" } ?: "—")
                                GsmCellStat("RSRQ", t.rsrqDb?.let { "$it dB" } ?: "—")
                                GsmCellStat("RSSI", t.rssiDbm?.let { "$it dBm" } ?: "—")
                                GsmCellStat("ASU", t.asu?.toString() ?: "—")
                            }
                            if (t.bands.isNotEmpty() || t.timingAdvance != null || t.distanceEstimateM != null) {
                                Spacer(Modifier.height(6.dp))
                                val bandText = if (t.bands.isNotEmpty()) stringResource(R.string.tact_gsm_band, t.bands.joinToString("/")) else ""
                                val distanceM = t.distanceEstimateM
                                val distText = if (distanceM != null) stringResource(R.string.tact_distance_est, distanceM) else ""
                                Text(
                                    buildString {
                                        append(bandText)
                                        if (t.timingAdvance != null) append(" · TA ${t.timingAdvance}µs")
                                        append(distText)
                                    },
                                    fontFamily = Mono, fontSize = 8.sp, color = Gold,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.tact_gsm_source),
                    fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun GsmCellStat(label: String, value: String) {
    Column {
        Text(value, fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = White)
        Text(label, fontFamily = Sans, fontSize = 7.sp, color = Gray)
    }
}


/**
 * 摩尔斯码页 —— 声音蜂鸣 / 手电筒静默模式 / 双向翻译
 *
 * 发送:输入文本 → 国际摩尔斯打码(声音/闪光灯/双模式),实时进度与当前字符;
 * 打键:手点「·」「—」输入摩尔斯实时解码;闪光灯模式需 CAMERA 权限与闪光灯硬件。
 */
@Composable
fun MorseScreen(back: () -> Unit) {
    val vm: MorseViewModel = viewModel()
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    var text by remember { mutableStateOf("SOS") }
    var wpm by remember { mutableStateOf(12) }
    var mode by remember { mutableStateOf(MorseManager.MorseSendMode.SOUND) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.tact_morse_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
            TermPanel(stringResource(R.string.tact_morse_transmit)) {
                TermInput(stringResource(R.string.tact_message_content), text, placeholder = stringResource(R.string.tact_message_placeholder)) { text = it }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.tact_speed), fontFamily = Sans, fontSize = 11.sp, color = Gray)
                    Text("$wpm WPM", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold)
                }
                Slider(wpm.toFloat(), { wpm = it.toInt().coerceIn(1, 40) }, valueRange = 1f..40f, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = SurfaceAlt))
                Spacer(Modifier.height(4.dp))
                // 模式选择
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        MorseManager.MorseSendMode.SOUND to stringResource(R.string.tact_mode_sound),
                        MorseManager.MorseSendMode.TORCH to stringResource(R.string.tact_mode_silent),
                        MorseManager.MorseSendMode.BOTH to stringResource(R.string.tact_mode_both),
                    ).forEach { (m, label) ->
                        val on = mode == m
                        Surface(
                            modifier = Modifier.clickable { haptic.hud(); mode = m },
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = if (on) Red.copy(alpha = 0.15f) else SurfaceBg,
                            border = BorderStroke(1.dp, if (on) Red.copy(alpha = 0.7f) else BorderDim),
                        ) {
                            Text(label, fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (on) Red else Gray, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
                if (mode != MorseManager.MorseSendMode.SOUND && !state.torchAvailable) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.tact_no_torch), fontFamily = Sans, fontSize = 9.sp, color = Warn)
                }
                Spacer(Modifier.height(8.dp))
                if (state.sending) {
                    // 发送进度
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp)).background(SurfaceAlt)) {
                        Box(
                            Modifier.fillMaxWidth(state.progress.coerceIn(0f, 1f)).height(6.dp)
                                .clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp)).background(Gold),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(
                            R.string.tact_sending_progress,
                            state.currentChar.ifBlank { "…" },
                            (state.progress * 100).toInt(),
                        ),
                        fontFamily = Mono, fontSize = 9.sp, color = Gold,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                ActionButton(running = state.sending, runLabel = stringResource(R.string.tact_start_send), stopLabel = stringResource(R.string.tact_stop_send)) {
                    haptic.hud()
                    if (state.sending) vm.stop() else vm.send(text, wpm, mode)
                }
                state.error?.let { Text("[!] $it", fontFamily = Sans, fontSize = 9.sp, color = Danger, modifier = Modifier.padding(top = 4.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.tact_morse_keying)) {
                Text(stringResource(R.string.tact_keying_hint), fontFamily = Sans, fontSize = 9.sp, color = Gray)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f).clickable { haptic.hud(); vm.key('.') },
                        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                        color = Red.copy(alpha = 0.12f),
                        border = BorderStroke(1.5.dp, Red.copy(alpha = 0.6f)),
                    ) {
                        Text(stringResource(R.string.tact_key_dot), fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 18.dp))
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clickable { haptic.hud(); vm.key('-') },
                        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                        color = Gold.copy(alpha = 0.12f),
                        border = BorderStroke(1.5.dp, Gold.copy(alpha = 0.6f)),
                    ) {
                        Text(stringResource(R.string.tact_key_dash), fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 18.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f).clickable { haptic.hud(); vm.keyCharEnd() },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = TacGreen.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.5f)),
                    ) {
                        Text(stringResource(R.string.tact_char_end), fontFamily = Sans, fontSize = 10.sp, color = TacGreen, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clickable { haptic.hud(); vm.keyWordEnd() },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Text(stringResource(R.string.tact_word_space), fontFamily = Sans, fontSize = 10.sp, color = Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clickable { haptic.hud(); vm.keyClear() },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Text(stringResource(R.string.tact_clear), fontFamily = Sans, fontSize = 10.sp, color = Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.tact_current_keying, vm.pendingCode()), fontFamily = Mono, fontSize = 11.sp, color = Gold)
                Spacer(Modifier.height(4.dp))
                Text(
                    state.decoded.ifBlank { stringResource(R.string.tact_decoded_placeholder) },
                    fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = White,
                )
            }
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.tact_morse_reference)) {
                Text(
                    MorseManager.MORSE.entries.joinToString("  ") { "${it.key}=${it.value}" },
                    fontFamily = Mono, fontSize = 9.sp, color = Gray, lineHeight = 15.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.tact_morse_standard),
                    fontFamily = Sans, fontSize = 8.sp, color = Gray.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 红外遥控 —— ConsumerIrManager

// ══════════════════════════════════════════════════════════════════════════

@Composable
fun IrRemoteScreen(vm: IrRemoteViewModel, back: () -> Unit, onOpenUniversal: () -> Unit) {
    val manager = vm.manager
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val last by manager.lastTransmit.collectAsState()
    var necCmd by remember { mutableStateOf("45") }
    var necAddr by remember { mutableStateOf("00") }
    var irProtocol by remember { mutableStateOf("nec") }
    var rawPattern by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.tact_ir_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
            TermPanel(stringResource(R.string.tact_ir_emitter_panel)) {
                if (manager.hasEmitter) {
                    Text(stringResource(R.string.tact_ir_emitter_found), fontFamily = Mono, fontSize = 11.sp, color = TacGreen)
                    Text(stringResource(R.string.tact_ir_carrier_range, manager.info.carrierFreqs.joinToString(" - ")), fontFamily = Mono, fontSize = 9.sp, color = Gray)
                } else {
                    Text(stringResource(R.string.tact_ir_no_emitter), fontFamily = Sans, fontSize = 11.sp, color = Danger)
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.tact_ir_tv_keys)) {
                val keys = listOf(
                    stringResource(R.string.tact_ir_power) to 0x45, stringResource(R.string.tact_ir_mute) to 0x11, stringResource(R.string.tact_ir_source) to 0x49,
                    stringResource(R.string.tact_ir_vol_up) to 0x15, stringResource(R.string.tact_ir_vol_down) to 0x09,
                    stringResource(R.string.tact_ir_ch_up) to 0x46, stringResource(R.string.tact_ir_ch_down) to 0x47,
                )
                keys.chunked(3).forEach { rowKeys ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowKeys.forEach { (label, cmd) ->
                            Surface(
                                modifier = Modifier.weight(1f).clickable {
                                    haptic.hud()
                                    manager.transmitNec(0x00, cmd)
                                },
                                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                                color = Red.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Red.copy(alpha = 0.45f)),
                            ) {
                                Text(label, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                            }
                        }
                        repeat(3 - rowKeys.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                // 数字键
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..9).forEach { n ->
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                haptic.hud()
                                manager.transmitNec(0x00, n)
                            },
                            shape = CutCornerShape(6.dp),
                            color = SurfaceBg,
                            border = BorderStroke(1.dp, BorderDim),
                        ) {
                            Text("$n", fontFamily = Mono, fontSize = 12.sp, color = White, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                        }
                    }
                }
                Text(stringResource(R.string.tact_ir_numeric_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(8.dp))
            // ── 万能遥控入口:Flipper tv.ir 泛洪库 ──
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    haptic.hud()
                    onOpenUniversal()
                },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = Gold.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.55f)),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tact_ir_universal), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.tact_ir_candidates), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                }
            }
            Spacer(Modifier.height(8.dp))
            // ── 协议选择 ──
            TermPanel(stringResource(R.string.tact_ir_protocol_panel)) {
                Text(stringResource(R.string.tact_ir_protocol_hint), fontFamily = Sans, fontSize = 9.sp, color = Gray)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("NEC 38kHz" to "nec", "Sony SIRC 40kHz" to "sony", "RC-5 36kHz" to "rc5").forEach { (label, key) ->
                        val on = irProtocol == key
                        Surface(
                            modifier = Modifier.weight(1f).clickable { haptic.hud(); irProtocol = key },
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = if (on) Red.copy(alpha = 0.15f) else SurfaceBg,
                            border = BorderStroke(1.dp, if (on) Red.copy(alpha = 0.7f) else BorderDim),
                        ) {
                            Text(label, fontFamily = Mono, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (on) Red else Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (irProtocol) {
                    "sony" -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TermInput(stringResource(R.string.tact_ir_cmd_hex), necCmd, modifier = Modifier.weight(1f)) { necCmd = it }
                            TermInput(stringResource(R.string.tact_ir_device_hex), necAddr, modifier = Modifier.weight(1f)) { necAddr = it }
                        }
                        Text(stringResource(R.string.tact_ir_sony_desc), fontFamily = Sans, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.8f), modifier = Modifier.padding(top = 3.dp))
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                haptic.hud()
                                val c = necCmd.toIntOrNull(16) ?: 0
                                val d = necAddr.toIntOrNull(16) ?: 0
                                manager.transmitSony(c, d)
                            },
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            color = Red.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Red.copy(alpha = 0.45f)),
                        ) {
                            Text(stringResource(R.string.tact_ir_send_sirc), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                        }
                    }
                    "rc5" -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TermInput(stringResource(R.string.tact_ir_addr_hex), necAddr, modifier = Modifier.weight(1f)) { necAddr = it }
                            TermInput(stringResource(R.string.tact_ir_cmd_hex), necCmd, modifier = Modifier.weight(1f)) { necCmd = it }
                        }
                        Text(stringResource(R.string.tact_ir_rc5_desc), fontFamily = Sans, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.8f), modifier = Modifier.padding(top = 3.dp))
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                haptic.hud()
                                val a = necAddr.toIntOrNull(16) ?: 0
                                val c = necCmd.toIntOrNull(16) ?: 0
                                manager.transmitRc5(a, c)
                            },
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            color = Red.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Red.copy(alpha = 0.45f)),
                        ) {
                            Text(stringResource(R.string.tact_ir_send_rc5), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.tact_ir_nec_desc), fontFamily = Sans, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.8f))
                    }
                }
            }
            // ── 波形示意:按所选协议标准时序绘制,非实测波形 ──
            TermPanel(stringResource(R.string.tact_ir_waveform_panel)) {
                IrWaveformPreview(irProtocol, necAddr, necCmd)
            }
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.tact_ir_custom_panel)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TermInput(stringResource(R.string.tact_ir_addr_hex), necAddr, modifier = Modifier.weight(1f)) { necAddr = it }
                    TermInput(stringResource(R.string.tact_ir_cmd_hex), necCmd, modifier = Modifier.weight(1f)) { necCmd = it }
                }
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptic.hud()
                        if (necAddr.toIntOrNull(16) == null || necCmd.toIntOrNull(16) == null) {
                            xin.ctkqiang.deauthctrl.ui.components.StickerBus.show(context.getString(R.string.tact_ir_hex_invalid))
                        }
                        val a = necAddr.toIntOrNull(16) ?: 0
                        val c = necCmd.toIntOrNull(16) ?: 0
                        manager.transmitNec(a, c)
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Red.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Red.copy(alpha = 0.45f)),
                ) {
                    Text(stringResource(R.string.tact_ir_send_nec), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                }
                Spacer(Modifier.height(8.dp))
                TermInput(stringResource(R.string.tact_ir_raw_label), rawPattern, placeholder = "9000,4500,560,1690,...") { rawPattern = it }
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptic.hud()
                        val pattern = rawPattern.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
                        if (pattern.isNotEmpty()) manager.transmitRaw(38_000, pattern, label = context.getString(R.string.tact_raw_segments, pattern.size))
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f)),
                ) {
                    Text(stringResource(R.string.tact_ir_send_raw), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                }
            }
            last?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontFamily = Mono, fontSize = 9.sp, color = if (!it.startsWith("[!]")) TacGreen else Danger)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// NFC 标签工作台
// ══════════════════════════════════════════════════════════════════════════

@Composable
fun NfcScreen(vm: NfcViewModel, back: () -> Unit) {
    val manager = vm.manager
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val lastTag by manager.lastTag.collectAsState()
    val message by manager.message.collectAsState()
    var writeText by remember { mutableStateOf("") }
    // 四个工作区:读卡 / 模拟 / 写入 / 识别 —— 各自独立,不再混在一页
    var tab by remember { mutableStateOf(0) }
    // UID 采集历史(识别工作区;进程内存,不上报不落盘)
    val tagHistory = remember { mutableStateListOf<NfcTagInfo>() }

    LaunchedEffect(lastTag) {
        lastTag?.let { t ->
            if (tagHistory.none { it.uid == t.uid }) tagHistory.add(0, t)
            if (tagHistory.size > 50) tagHistory.removeAt(tagHistory.size - 1)
        }
    }

    // 页面期间开启前台读卡
    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        activity?.let { manager.startReader(it) }
        onDispose { activity?.let { manager.stopReader(it) } }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.tact_nfc_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            TermPanel(stringResource(R.string.tact_nfc_adapter_panel)) {
                when {
                    !manager.supported -> Text(stringResource(R.string.tact_nfc_no_hardware), fontFamily = Sans, fontSize = 11.sp, color = Danger)
                    !manager.enabled -> {
                        Text(stringResource(R.string.tact_nfc_disabled), fontFamily = Sans, fontSize = 11.sp, color = Warn)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.clickable { manager.openNfcSettings() },
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            color = Red.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Red.copy(alpha = 0.45f)),
                        ) {
                            Text(stringResource(R.string.tact_nfc_open_settings), fontFamily = Sans, fontSize = 11.sp, color = Red, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                    else -> Text(stringResource(R.string.tact_nfc_reader_ready), fontFamily = Mono, fontSize = 11.sp, color = TacGreen)
                }
            }
            Spacer(Modifier.height(8.dp))
            // ── 四工作区切换 ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(stringResource(R.string.tact_nfc_tab_reader) to 0, stringResource(R.string.tact_nfc_tab_emulator) to 1, stringResource(R.string.tact_nfc_tab_writer) to 2, stringResource(R.string.tact_nfc_tab_identifier) to 3).forEach { (label, i) ->
                    val on = tab == i
                    Surface(
                        modifier = Modifier.weight(1f).clickable { haptic.hud(); tab = i },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = if (on) Red.copy(alpha = 0.15f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (on) Red.copy(alpha = 0.7f) else BorderDim),
                    ) {
                        Text(label, fontFamily = Sans, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (on) Red else Gray, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp, vertical = 7.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // ── 战术遥测条(雷达同款) ──
            TacticalStatStrip(
                listOf(
                    "${tagHistory.size}" to stringResource(R.string.tact_nfc_history_label),
                    (lastTag?.uid?.take(12) ?: "--") to "UID",
                    (if (manager.supported && manager.enabled) stringResource(R.string.tact_nfc_ready_short) else stringResource(R.string.tact_nfc_not_ready)) to "NFC",
                    stringResource(if (tab == 0) R.string.tact_nfc_tab_reader else if (tab == 1) R.string.tact_nfc_tab_emulator else if (tab == 2) R.string.tact_nfc_tab_writer else R.string.tact_nfc_tab_identifier) to stringResource(R.string.tact_nfc_workspace_label),
                ),
            )
            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(140)))
                },
            ) { tabIdx ->
            when (tabIdx) {
                0 -> Column {
                    // ── 读卡:当前标签完整信息 ──
                    TermPanel(stringResource(R.string.tact_nfc_current_tag)) {
                        val t = lastTag
                        if (t == null) {
                            EmptyHint(stringResource(R.string.tact_nfc_waiting_tag))
                        } else {
                            Column {
                                InfoLine("UID", t.uid)
                                InfoLine(stringResource(R.string.tact_nfc_tech), t.techs.joinToString(" / "))
                                if (t.ndefRecords.isEmpty()) {
                                    InfoLine("NDEF", stringResource(R.string.tact_nfc_no_ndef))
                                } else {
                                    Text("NDEF", fontFamily = Mono, fontSize = 10.sp, color = Gray)
                                    Spacer(Modifier.height(2.dp))
                                    t.ndefRecords.forEachIndexed { i, rec ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("▸", fontFamily = Mono, fontSize = 9.sp, color = Gold)
                                            Spacer(Modifier.width(5.dp))
                                            Text(rec.take(80), fontFamily = Mono, fontSize = 8.sp, color = White, lineHeight = 11.sp)
                                        }
                                    }
                                }
                                InfoLine(stringResource(R.string.tact_nfc_sectors), if (t.sectorCount > 0) "${t.sectorCount} (Mifare Classic)" else "--")
                                InfoLine(stringResource(R.string.tact_nfc_writable), if (t.writable) stringResource(R.string.tact_yes) else stringResource(R.string.tact_no))
                            }
                        }
                    }
                    message?.let { Text(it, fontFamily = Mono, fontSize = 9.sp, color = TacGreen, modifier = Modifier.padding(top = 4.dp)) }
                }
                1 -> Column {
                    // ── 模拟:手机作为 NFC 卡 ──
                    Text(stringResource(R.string.tact_nfc_emulator_desc), fontFamily = Sans, fontSize = 9.sp, color = Gray)
                    Spacer(Modifier.height(6.dp))
                    CardWalletPanel()
                }
                2 -> Column {
                    // ── 写入:NDEF 文本 ──
                    TermPanel(stringResource(R.string.tact_nfc_write_panel)) {
                        Text(stringResource(R.string.tact_nfc_write_desc), fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        TermInput(stringResource(R.string.tact_nfc_write_content), writeText, placeholder = stringResource(R.string.tact_nfc_write_placeholder)) { writeText = it }
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                haptic.hud()
                                manager.queueWrite(writeText)
                            },
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            color = Red.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Red.copy(alpha = 0.45f)),
                        ) {
                            Text(stringResource(R.string.tact_nfc_prepare_write), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                        }
                    }
                    message?.let { Text(it, fontFamily = Mono, fontSize = 9.sp, color = TacGreen, modifier = Modifier.padding(top = 4.dp)) }
                }
                else -> Column {
                    // ── 识别:UID 历史 + 技术指纹 ──
                    TermPanel(stringResource(R.string.tact_nfc_uid_history, tagHistory.size)) {
                        if (tagHistory.isEmpty()) {
                            EmptyHint(stringResource(R.string.tact_nfc_uid_history_empty))
                        } else {
                            tagHistory.forEach { t ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("▸ ${t.uid}", fontFamily = Mono, fontSize = 10.sp, color = Gold, modifier = Modifier.weight(1f))
                                    Text(t.techs.firstOrNull() ?: "--", fontFamily = Mono, fontSize = 8.sp, color = Gray)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TermPanel(stringResource(R.string.tact_nfc_fingerprint)) {
                        Text(stringResource(R.string.tact_nfc_fingerprint_desc), fontFamily = Sans, fontSize = 9.sp, color = Gray)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.tact_nfc_fp_4byte), fontFamily = Mono, fontSize = 8.5.sp, color = White)
                        Text(stringResource(R.string.tact_nfc_fp_7byte), fontFamily = Mono, fontSize = 8.5.sp, color = White)
                        Text(stringResource(R.string.tact_nfc_fp_8_10byte), fontFamily = Mono, fontSize = 8.5.sp, color = White)
                        Text(stringResource(R.string.tact_nfc_fp_ndef), fontFamily = Mono, fontSize = 8.5.sp, color = White)
                        Text(stringResource(R.string.tact_nfc_fp_sectors), fontFamily = Mono, fontSize = 8.5.sp, color = White)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.tact_nfc_fp_note), fontFamily = Sans, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.8f))
                    }
                }
            }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * 红外协议波形示意 —— Canvas 按协议标准时序绘制(引导码/逻辑位/载波占空比)。
 * 这是协议定义的标准波形,不是发射时实测的回读波形(手机无红外接收器)。
 */
@Composable
private fun IrWaveformPreview(protocol: String, addrHex: String, cmdHex: String) {
    val waveformLabel = when (protocol) {
        "sony" -> stringResource(R.string.tact_wave_sirc)
        "rc5" -> stringResource(R.string.tact_wave_rc5)
        else -> stringResource(R.string.tact_wave_nec)
    }
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        val a = addrHex.toIntOrNull(16) ?: 0
        val c = cmdHex.toIntOrNull(16) ?: 0
        // 生成协议标准时序(µs)
        val patternUs = when (protocol) {
            "sony" -> buildList {
                add(2400); add(600)
                for (i in 0 until 12) { val b = (c shr i) and 1; add(if (b == 1) 1200 else 600); add(600) }
            }
            "rc5" -> buildList {
                add(889); add(889) // 起始位
                val bits = (a shl 6) or c
                for (i in 5 downTo 0) { val b = (bits shr i) and 1; if (b == 1) { add(889); add(889) } else { add(889); add(889) } }
            }
            else -> buildList {
                add(9000); add(4500)
                for (i in 7 downTo 0) { val b = (a shr i) and 1; add(560); add(if (b == 1) 1690 else 560) }
                for (i in 7 downTo 0) { val b = (c shr i) and 1; add(560); add(if (b == 1) 1690 else 560) }
                add(560)
            }
        }
        val totalUs = patternUs.sum()
        val yOn = size.height * 0.3f
        val yOff = size.height * 0.65f
        var x = 0f
        patternUs.forEachIndexed { i, us ->
            val w = size.width * us / totalUs
            val isOn = i % 2 == 0
            drawLine(
                Gold.copy(alpha = 0.9f),
                Offset(x, if (isOn) yOn else yOff),
                Offset(x + w, if (isOn) yOn else yOff),
                2f,
            )
            // 载波纹理示意(开启段画细竖线)
            if (isOn && w > 3f) {
                var cx = x
                while (cx < x + w) {
                    drawLine(Gold.copy(alpha = 0.35f), Offset(cx, yOn), Offset(cx, yOn - 8f), 0.6f)
                    cx += 4f
                }
            }
            x += w
        }
        val label = textMeasurer.measure(
            waveformLabel,
            androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 7.5.sp, color = Gray),
        )
        drawText(label, topLeft = Offset(0f, size.height - label.size.height))
    }
}

/** 卡模拟钱包 —— 手机作为 NFC 卡被读卡器读取 */
@Composable
private fun CardWalletPanel() {
    val context = LocalContext.current
    val wallet = remember { xin.ctkqiang.deauthctrl.manager.CardWalletManager(context) }
    val cards by wallet.cards.collectAsState()
    val activeId by wallet.activeId.collectAsState()
    val haptic = LocalHapticFeedback.current
    var newName by remember { mutableStateOf("") }
    var newData by remember { mutableStateOf("") }

    TermPanel(stringResource(R.string.tact_wallet_panel)) {
        Text(
            stringResource(R.string.tact_wallet_desc),
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        if (cards.isEmpty()) {
            EmptyHint(stringResource(R.string.tact_wallet_empty))
        } else {
            cards.forEach { card ->
                val active = card.id == activeId
                Surface(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                    color = if (active) TacGreen.copy(alpha = 0.07f) else SurfaceBg,
                    border = BorderStroke(1.dp, if (active) Gold.copy(alpha = 0.8f) else BorderDim),
                ) {
                    Column(Modifier.padding(9.dp)) {
                        val emptyDataLabel = stringResource(R.string.tact_empty)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(card.name, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
                            if (active) {
                                Text(stringResource(R.string.tact_wallet_active), fontFamily = Mono, fontSize = 8.sp, color = TacGreen)
                            }
                        }
                        Text(stringResource(R.string.tact_wallet_aid_data, card.dataHex.ifBlank { emptyDataLabel }.take(24)), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!active) {
                                Surface(
                                    modifier = Modifier.clickable { haptic.hud(); wallet.setActive(card.id) },
                                    shape = CutCornerShape(5.dp),
                                    color = Red.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                                ) {
                                    Text(stringResource(R.string.tact_wallet_set_active), fontFamily = Sans, fontSize = 9.sp, color = Red, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                            Surface(
                                modifier = Modifier.clickable { haptic.hud(); wallet.removeCard(card.id) },
                                shape = CutCornerShape(5.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, BorderDim),
                            ) {
                                Text(stringResource(R.string.tact_delete), fontFamily = Sans, fontSize = 9.sp, color = Gray, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        TermInput(stringResource(R.string.tact_wallet_card_name), newName, placeholder = stringResource(R.string.tact_wallet_card_name_ph)) { newName = it }
        Spacer(Modifier.height(4.dp))
        TermInput(stringResource(R.string.tact_wallet_card_data), newData, placeholder = stringResource(R.string.tact_wallet_card_data_ph)) { newData = it }
        Spacer(Modifier.height(5.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clickable {
                haptic.hud()
                wallet.addCard(newName, newData)
                newName = ""; newData = ""
            },
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = Red.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Red.copy(alpha = 0.45f)),
        ) {
            Text(stringResource(R.string.tact_wallet_add), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.tact_wallet_limitations),
            fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontFamily = Mono, fontSize = 9.sp, color = Gray, modifier = Modifier.width(52.dp))
        Text(value, fontFamily = Mono, fontSize = 9.sp, color = White, modifier = Modifier.weight(1f))
    }
}


/**
 * 串口监视器 —— Arduino IDE 风格
 *
 * USB OTG 枚举串口设备(FTDI/CH340/CP210x/CDC-ACM),波特率/数据位/停止位/
 * 奇偶校验配置,双向收发 + 时间戳日志 + 十六进制视图。发送自动补 CR+LF
 * (Serial.println 风格)。FTDI/CH340 芯片在无厂商驱动的 Android 上只能枚举、
 * 无法通讯 —— 页面如实标注兼容性。
 */
@Composable
fun SerialMonitorScreen(back: () -> Unit) {
    val vm: SerialMonitorViewModel = viewModel()
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    var sendText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.tact_serial_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            // ── 设备枚举 ──
            TermPanel(stringResource(R.string.tact_serial_devices_panel, state.devices.size)) {
                if (state.devices.isEmpty()) {
                    EmptyHint(stringResource(R.string.tact_serial_no_devices))
                } else {
                    state.devices.forEach { d ->
                        val on = state.connectedDevice?.deviceId == d.deviceId
                        Surface(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                                haptic.hud()
                                if (on) vm.disconnect() else vm.connect(d)
                            },
                            shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                            color = if (on) TacGreen.copy(alpha = 0.08f) else SurfaceBg,
                            border = BorderStroke(1.dp, if (on) TacGreen.copy(alpha = 0.6f) else BorderDim),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (on) "●" else "○", fontFamily = Mono, fontSize = 10.sp, color = if (on) TacGreen else Gray)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (on) TacGreen else White)
                                    Text("VID %04X · PID %04X · %s".format(d.vendorId, d.productId, d.chip), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                                }
                                Text(if (on) stringResource(R.string.tact_connected) else stringResource(R.string.tact_connect), fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (on) TacGreen else Gold)
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.tact_serial_driver_note),
                    fontFamily = Sans, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.8f), lineHeight = 11.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            // ── 线路参数(Arduino IDE 同款) ──
            TermPanel(stringResource(R.string.tact_serial_line_coding)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TermInput(stringResource(R.string.tact_serial_baud), state.baudRate.toString(), modifier = Modifier.weight(1f)) { v ->
                        v.toIntOrNull()?.let { vm.setParams(it, state.dataBits, state.stopBits, state.parity) }
                    }
                    TermInput(stringResource(R.string.tact_serial_data_bits), state.dataBits.toString(), modifier = Modifier.weight(1f)) { v ->
                        v.toIntOrNull()?.let { vm.setParams(state.baudRate, it, state.stopBits, state.parity) }
                    }
                    TermInput(stringResource(R.string.tact_serial_stop_bits), state.stopBits.toString(), modifier = Modifier.weight(1f)) { v ->
                        v.toIntOrNull()?.let { vm.setParams(state.baudRate, state.dataBits, it, state.parity) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to stringResource(R.string.tact_parity_none), 1 to stringResource(R.string.tact_parity_odd), 2 to stringResource(R.string.tact_parity_even)).forEach { (p, label) ->
                        val on = state.parity == p
                        Surface(
                            modifier = Modifier.weight(1f).clickable { haptic.hud(); vm.setParams(state.baudRate, state.dataBits, state.stopBits, p) },
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = if (on) Red.copy(alpha = 0.14f) else SurfaceBg,
                            border = BorderStroke(1.dp, if (on) Red.copy(alpha = 0.6f) else BorderDim),
                        ) {
                            Text(label, fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (on) Red else Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clickable { haptic.hud(); vm.setHexMode(!state.hexMode) },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = if (state.hexMode) Gold.copy(alpha = 0.12f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (state.hexMode) Gold.copy(alpha = 0.6f) else BorderDim),
                    ) {
                        Text("HEX", fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (state.hexMode) Gold else Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                    }
                }
                state.error?.let { Text("[!] $it", fontFamily = Sans, fontSize = 9.sp, color = Danger, modifier = Modifier.padding(top = 4.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            // ── 日志监视(Arduino IDE 风格) ──
            Surface(
                Modifier.fillMaxWidth().weight(1f),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = Color(0xFF04121C),
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.tact_serial_output, state.log.size), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacGreen, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.tact_clear), fontFamily = Sans, fontSize = 9.sp, color = Gray, modifier = Modifier.clickable { haptic.hud(); vm.clearLog() })
                    }
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        items(state.log.takeLast(200), key = { it.timeMs }) { line ->
                            val color = when (line.direction) {
                                'R' -> White
                                'T' -> Gold
                                else -> TacGreen
                            }
                            Column(Modifier.padding(vertical = 1.dp)) {
                                Text(
                                    "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(line.timeMs))} ${line.direction} " +
                                        (if (state.hexMode && line.hex.isNotBlank()) line.hex else line.text.replace("\n", "⏎")),
                                    fontFamily = Mono, fontSize = 9.sp, color = color, lineHeight = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // ── 发送行 ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TermInput(stringResource(R.string.tact_serial_send), sendText, placeholder = stringResource(R.string.tact_serial_send_ph), modifier = Modifier.weight(1f)) { sendText = it }
                Surface(
                    modifier = Modifier.clickable {
                        haptic.hud()
                        if (sendText.isNotBlank() && state.connected) {
                            vm.sendLine(sendText)
                            sendText = ""
                        }
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Red.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                ) {
                    Text(stringResource(R.string.tact_send_arrow), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                }
            }
            if (!state.connected) {
                Text(stringResource(R.string.tact_serial_not_connected), fontFamily = Sans, fontSize = 8.sp, color = Gray, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}


/**
 * 有线 OTG 探测 —— 检测 USB 接入的网络/串口设备并引导进入对应工具
 *
 * UsbManager 枚举设备类:网卡(CDC-ECM/RNDIS/ASIX 等)→ 引导 ARP 扫描;
 * 串口(FTDI/CH340/CP210x/CDC-ACM)→ 引导串口监视。探测本身是被动的
 * 设备枚举,不产生流量;实际链路探测由对应工具执行。
 */
@Composable
fun OtgProbeScreen(onOpenArp: () -> Unit, onOpenSerial: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as? UsbManager }
    var refreshTick by remember { mutableStateOf(0) }
    val devices = remember(refreshTick) { usbManager?.deviceList?.values.orEmpty().toList() }

    LaunchedEffect(Unit) {
        while (true) { refreshTick++; delay(2000) }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.tact_otg_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (devices.isEmpty()) stringResource(R.string.tact_otg_none) else stringResource(R.string.tact_otg_found, devices.size), fontFamily = Mono, fontSize = 10.sp, color = if (devices.isEmpty()) Gray else TacGreen, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.tact_auto_refresh), fontFamily = Sans, fontSize = 8.sp, color = Gray)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (devices.isEmpty()) {
                EmptyHint(stringResource(R.string.tact_otg_empty))
            } else {
                devices.forEach { d ->
                    val isSerial = d.deviceClass == 2 || d.vendorId in setOf(0x0403, 0x1A86, 0x10C4, 0x2341)
                    val isNet = d.deviceClass == 0 && !isSerial || d.vendorId in setOf(0x0B95, 0x0BDA, 0x0846, 0x0FE6) // ASIX/Realtek/Netgear/ICP DAS
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = SurfaceBg,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(d.productName ?: stringResource(R.string.tact_usb_device), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
                                Text("VID %04X · PID %04X".format(d.vendorId, d.productId), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isNet) {
                                    Surface(
                                        modifier = Modifier.weight(1f).clickable { onOpenArp() },
                                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                        color = TacGreen.copy(alpha = 0.1f),
                                        border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.5f)),
                                    ) {
                                        Text(stringResource(R.string.tact_otg_net), fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TacGreen, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                                    }
                                }
                                if (isSerial) {
                                    Surface(
                                        modifier = Modifier.weight(1f).clickable { onOpenSerial() },
                                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                        color = Gold.copy(alpha = 0.1f),
                                        border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                                    ) {
                                        Text(stringResource(R.string.tact_otg_serial), fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                                    }
                                }
                                if (!isNet && !isSerial) {
                                    Text(stringResource(R.string.tact_otg_unknown), fontFamily = Sans, fontSize = 9.sp, color = Gray)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tact_otg_note),
                fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 数字夜视 —— Camera2 高感光 + 逐像素增益 + 绿色映射


// ══════════════════════════════════════════════════════════════════════════

@Composable
fun NightVisionScreen(vm: NightVisionViewModel, back: () -> Unit) {
    val manager = vm.manager
    val haptic = LocalHapticFeedback.current
    val running by manager.running.collectAsState()
    val frame by manager.frame.collectAsState()
    val error by manager.error.collectAsState()
    val tracks by manager.tracks.collectAsState()
    val stats by manager.stats.collectAsState()
    val detectorStatus by manager.detectorStatus.collectAsState()
    val cameras = manager.cameras
    var selectedId by remember { mutableStateOf(cameras.firstOrNull()?.id ?: "") }
    var gain by remember { mutableStateOf(manager.gain) }

    // 首次进入自动开启;之后只有「运行中切换镜头」才重启。
    // 点击已选镜头只改 selectedId(相同值不触发 effect),不会再第二次 start。
    var startedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(selectedId) {
        if (selectedId.isEmpty()) return@LaunchedEffect
        if (!startedOnce) {
            startedOnce = true
            manager.start(selectedId)
        } else if (manager.running.value) {
            manager.start(selectedId)
        }
    }
    DisposableEffect(Unit) { onDispose { manager.stop() } }

    val confirmed = tracks.filter { it.confirmed }
    val faceCount = confirmed.count { it.kind == "face" }
    val humanCount = confirmed.count { it.kind == "human" }
    val anyThreat = confirmed.isNotEmpty()

    // 触觉去抖:只有「新确认的目标」提示,同一 track 1.5s 内最多一次
    val seenTrackIds = remember { mutableSetOf<Long>() }
    val lastHapticAt = remember { mutableMapOf<Long, Long>() }
    LaunchedEffect(tracks) {
        val now = SystemClock.elapsedRealtime()
        var triggered = false
        confirmed.forEach { track ->
            if (track.id !in seenTrackIds) {
                seenTrackIds.add(track.id)
                val last = lastHapticAt[track.id] ?: 0L
                if (now - last >= 1_500L) {
                    lastHapticAt[track.id] = now
                    triggered = true
                }
            }
        }
        if (triggered) haptic.hud()
        val alive = tracks.map { it.id }.toSet()
        seenTrackIds.retainAll(alive)
        lastHapticAt.keys.retainAll(alive)
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.tact_night_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            // 镜头选择 —— 点击只更新 selectedId,由上面的 effect 决定是否重启
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cameras.forEach { c ->
                    val selected = c.id == selectedId
                    Surface(
                        modifier = Modifier.clickable { selectedId = c.id },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = if (selected) Red.copy(alpha = 0.16f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (selected) Red.copy(alpha = 0.7f) else BorderDim),
                    ) {
                        Text(c.name, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) Red else White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            ActionButton(running, stringResource(R.string.tact_night_start), stringResource(R.string.tact_night_stop)) {
                haptic.hud()
                if (running) manager.stop()
                else if (selectedId.isNotEmpty()) manager.start(selectedId)
            }
            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 9.sp, color = Danger, modifier = Modifier.padding(top = 4.dp)) }

            Spacer(Modifier.height(8.dp))
            // 夜视画面 —— 按实际帧宽高比显示,不拉伸变形
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(frame?.let { it.width.toFloat() / it.height.toFloat() } ?: (3f / 4f))
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .background(Color.Black)
                    .border(1.dp, if (anyThreat) Danger.copy(alpha = 0.8f) else if (running) TacGreen.copy(alpha = 0.5f) else BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                frame?.let { bmp ->
                    Image(bmp.asImageBitmap(), contentDescription = stringResource(R.string.tact_cd_night_frame), modifier = Modifier.fillMaxSize())
                } ?: EmptyHint(if (running) stringResource(R.string.tact_night_enhancing) else stringResource(R.string.tact_night_hint))

                // 已确认跟踪目标:框 + 分数标签 + 涟漪,lastSeenAgoMs 驱动 700ms 淡出
                if (anyThreat) {
                    Box(Modifier.fillMaxSize().background(Danger.copy(alpha = 0.10f)))
                    val ripple = rememberInfiniteTransition(label = "nv-ripple")
                    val phase by ripple.animateFloat(
                        0f, 1f,
                        infiniteRepeatable(tween(1400, easing = androidx.compose.animation.core.LinearEasing)),
                        label = "nv-phase",
                    )
                    val tagMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                    Canvas(Modifier.fillMaxSize()) {
                        confirmed.forEach { track ->
                            val alpha = (1f - track.lastSeenAgoMs / 700f).coerceIn(0.2f, 1f)
                            val accent = if (track.kind == "face") Danger else Warn
                            val x = track.x * size.width
                            val y = track.y * size.height
                            val w = track.w * size.width
                            val h = track.h * size.height
                            drawRect(
                                color = accent.copy(alpha = alpha),
                                topLeft = Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(w, h),
                                style = Stroke(
                                    width = 2.5f,
                                    pathEffect = if (track.kind == "human") PathEffect.dashPathEffect(floatArrayOf(14f, 8f)) else null,
                                ),
                            )
                            val cx = x + w / 2f
                            val cy = y + h / 2f
                            val maxR = maxOf(w, h) * 1.1f
                            val r1 = maxR * (0.15f + phase * 0.85f)
                            drawCircle(accent.copy(alpha = (1f - phase) * 0.55f * alpha), r1, Offset(cx, cy), style = Stroke(2.5f))
                            drawCircle(accent.copy(alpha = (1f - phase) * 0.2f * alpha), r1 * 0.7f, Offset(cx, cy), style = Stroke(7f))

                            // 标签 chip:类型 + 检测器权重(非概率)
                            val label = if (track.kind == "face") "FACE %.2f".format(track.score) else "HUMAN %.2f".format(track.score)
                            val m = tagMeasurer.measure(
                                label,
                                androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = White),
                            )
                            val labelTop = (y - m.size.height - 6f).coerceAtLeast(0f)
                            drawRect(
                                color = accent.copy(alpha = 0.85f * alpha),
                                topLeft = Offset(x, labelTop),
                                size = androidx.compose.ui.geometry.Size(m.size.width + 8f, m.size.height + 3f),
                            )
                            drawText(m, topLeft = Offset(x + 4f, labelTop + 1f))
                        }
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = Danger.copy(alpha = 0.85f),
                    ) {
                        Text(
                            stringResource(R.string.tact_night_confirmed, faceCount, humanCount),
                            fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                if (running) {
                    TacticalCorners(color = if (anyThreat) Danger else TacGreen, modifier = Modifier.fillMaxSize(), len = 10.dp, stroke = 1.4.dp)
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                        color = Dark.copy(alpha = 0.75f),
                    ) {
                        Text(
                            when {
                                detectorStatus is DetectorStatus.Unavailable -> stringResource(R.string.tact_detector_unavailable)
                                detectorStatus is DetectorStatus.Loading -> stringResource(R.string.tact_detector_loading)
                                anyThreat -> stringResource(R.string.tact_target_locked, faceCount, humanCount)
                                else -> stringResource(R.string.tact_scanning)
                            },
                            fontFamily = Mono, fontSize = 9.sp,
                            color = if (anyThreat) Danger else TacGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // ── 检测统计条:Hz / 推理耗时 / 计数 / 不可用原因 ──
            Spacer(Modifier.height(6.dp))
            val statsLine = buildString {
                append("HAAR ").append(if (stats.haarOk) "OK" else "--")
                append(" · HOG ").append(if (stats.hogOk) "OK" else "--")
                append(" · %.1f Hz".format(stats.hz))
                append(" · ${stats.inferenceMs} ms")
                append(" · FACE ${stats.faces}")
                append(" · HUMAN ${stats.humans}")
            }
            val statsAccent = when {
                detectorStatus is DetectorStatus.Unavailable -> Danger
                stats.inferenceMs > 250L -> Warn
                else -> TacGreen
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                color = Dark.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, statsAccent.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text(statsLine, fontFamily = Mono, fontSize = 9.sp, color = statsAccent)
                    when (val s = detectorStatus) {
                        is DetectorStatus.Unavailable -> Text(stringResource(R.string.tact_detector_unavailable_reason, s.reason), fontFamily = Mono, fontSize = 8.sp, color = Danger, lineHeight = 11.sp)
                        is DetectorStatus.Loading -> Text(stringResource(R.string.tact_detector_loading_detail), fontFamily = Mono, fontSize = 8.sp, color = Warn)
                        is DetectorStatus.Ready -> {}
                    }
                    stats.error?.let { Text(stringResource(R.string.tact_detector_error, it), fontFamily = Mono, fontSize = 8.sp, color = Danger, lineHeight = 11.sp) }
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.tact_gain_panel)) {
                Text(stringResource(R.string.tact_gain_line, gain.toInt()), fontFamily = Mono, fontSize = 10.sp, color = Gold)
                Slider(
                    value = gain,
                    onValueChange = { gain = it; manager.gain = it },
                    valueRange = 1f..8f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = Red,
                        activeTrackColor = Red,
                        inactiveTrackColor = BorderDim,
                    ),
                )
                Text(
                    stringResource(R.string.tact_night_principle),
                    fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 战术地图 —— osmdroid + GPS + 夜战滤镜 + 标记投放
// ══════════════════════════════════════════════════════════════════════════

@Composable
fun TacticalMapScreen(vm: TacticalMapViewModel, back: () -> Unit) {
    val manager = vm.manager
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val location by manager.location.collectAsState()
    val markers by manager.markers.collectAsState()
    val status by manager.status.collectAsState()
    val networkOnline by manager.networkOnline.collectAsState()
    val error by manager.error.collectAsState()
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var zoomText by remember { mutableStateOf(19.0) }
    var follow by rememberSaveable { mutableStateOf(true) }
    var centeredOnce by rememberSaveable { mutableStateOf(false) }
    var night by rememberSaveable { mutableStateOf(true) }
    var cacheOnly by rememberSaveable { mutableStateOf(false) }
    var suppressMapEvents by remember { mutableStateOf(false) }
    var recenterRequested by remember { mutableStateOf(0) }
    val markerViews = remember { mutableMapOf<Long, Marker>() }
    val nightFilter = remember { manager.nightFilter() }

    // 单个稳定的自机 overlay:不随每次定位 tick 重建,避免闪烁/丢失点击
    val selfOverlay = remember {
        TacticalSelfOverlay(
            context = context,
            locationProvider = { manager.location.value },
            headingProvider = { manager.heading.value },
        )
    }
    val mapListener = remember {
        object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                // 只有用户手势才退出跟随;程序化 setCenter 由 suppressMapEvents 屏蔽
                if (!suppressMapEvents) follow = false
                return false
            }

            override fun onZoom(event: ZoomEvent): Boolean {
                zoomText = event.zoomLevel
                return false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> manager.reacquireIfActive() }

    // 引用计数:与网图页共享 manager,AnimatedContent 过渡期不会互相摘监听
    DisposableEffect(Unit) {
        manager.acquire()
        onDispose { manager.release() }
    }

    // MapView 生命周期:ON_START resume / ON_STOP pause;权限返回后重查
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    mapView?.onResume()
                    manager.reacquireIfActive()
                }
                Lifecycle.Event.ON_STOP -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 四态定位提示 —— 未授权 / 系统定位关闭 / 等待首定位 / 有 fix
    val statusLine: String
    val statusAccent: Color
    when {
        !status.permissionGranted -> {
            statusLine = stringResource(R.string.tact_loc_denied)
            statusAccent = Danger
        }
        !status.gpsEnabled && !status.networkEnabled -> {
            statusLine = stringResource(R.string.tact_loc_services_off)
            statusAccent = Warn
        }
        !status.hasFix -> {
            statusLine = stringResource(R.string.tact_loc_waiting_fix)
            statusAccent = Warn
        }
        else -> {
            val accuracyText = if (status.accuracyM > 0f) "±${status.accuracyM.toInt()}m" else stringResource(R.string.tact_accuracy_unknown)
            val fixAgeText = stringResource(R.string.tact_fix_age, status.fixAgeMs / 1000)
            val gpsState = stringResource(if (status.gpsEnabled) R.string.tact_on else R.string.tact_off)
            val netState = stringResource(if (status.networkEnabled) R.string.tact_on else R.string.tact_off)
            val networkLabel = stringResource(R.string.tact_network_label)
            statusLine = buildString {
                append(accuracyText)
                append(fixAgeText)
                append(" · GPS ").append(gpsState)
                append(networkLabel).append(netState)
            }
            statusAccent = if (status.fixAgeMs > 120_000L) Warn else TacGreen
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.tact_map_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            location?.let { "%.6f, %.6f".format(it.latitude, it.longitude) } ?: stringResource(R.string.tact_no_fix),
                            fontFamily = Mono, fontSize = 10.sp,
                            color = if (location != null) TacGreen else Gray,
                        )
                        Text(stringResource(R.string.tact_marker_count, markers.size), fontFamily = Mono, fontSize = 10.sp, color = Gold)
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            statusLine,
                            fontFamily = Mono, fontSize = 9.sp, color = statusAccent,
                            lineHeight = 12.sp, modifier = Modifier.weight(1f),
                        )
                        when {
                            !status.permissionGranted -> {
                                HudChip(stringResource(R.string.tact_grant_location), TacGreen) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        ),
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                HudChip(stringResource(R.string.tact_app_settings), Gray) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                            !status.gpsEnabled && !status.networkEnabled -> {
                                HudChip(stringResource(R.string.tact_location_settings), Warn) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                        }
                    }
                    error?.let {
                        Text(it, fontFamily = Mono, fontSize = 8.sp, color = Danger, lineHeight = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            val tileMode = stringResource(if (networkOnline && !cacheOnly) R.string.tact_tile_online else R.string.tact_tile_cache_only)
            Text(
                stringResource(R.string.tact_map_tiles_line, tileMode),
                fontFamily = Mono, fontSize = 8.sp, color = Gray, lineHeight = 11.sp,
            )
            Spacer(Modifier.height(5.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudToggle(stringResource(R.string.tact_follow), follow) {
                    haptic.hud()
                    follow = !follow
                    if (follow) recenterRequested++
                }
                HudToggle(stringResource(R.string.tact_night_filter), night) { haptic.hud(); night = !night }
                HudToggle(stringResource(R.string.tact_cache_only_toggle), cacheOnly) { haptic.hud(); cacheOnly = !cacheOnly }
                HudChip(stringResource(R.string.tact_locate_me), TacGreen) {
                    haptic.hud()
                    follow = true
                    recenterRequested++
                }
                Text(stringResource(R.string.tact_grid_on), fontFamily = Mono, fontSize = 8.sp, color = Gray)
            }

            Spacer(Modifier.height(6.dp))

            // osmdroid 地图
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                AndroidView(
                    factory = { ctx ->
                        // 先 load 再设置 —— 否则 prefs 里的旧值会覆盖 userAgent,
                        // 瓦片服务器会以默认 Dalvik UA 403 拒绝请求(地图全空白)
                        val cfg = org.osmdroid.config.Configuration.getInstance()
                        cfg.load(
                            ctx.applicationContext,
                            ctx.getSharedPreferences("osmdroid", 0),
                        )
                        cfg.userAgentValue = "DeauthCtrl/1.0 (tactical)"
                        // 瓦片缓存放应用内部目录,避免外部存储权限问题
                        cfg.osmdroidBasePath = java.io.File(ctx.filesDir, "osmdroid")
                        cfg.osmdroidTileCache = java.io.File(ctx.filesDir, "osmdroid/tiles")
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            setUseDataConnection(!cacheOnly)
                            setMinZoomLevel(4.0)
                            setMaxZoomLevel(19.0)
                            // 隐藏内置缩放按钮,改用下方自绘 canZoomIn/canZoomOut 按钮
                            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                            // 未加载瓦片:深色底 + 暗线,不再是一块刺眼的白/灰
                            overlayManager.tilesOverlay.setLoadingBackgroundColor(0xFF0A0C0E.toInt())
                            overlayManager.tilesOverlay.setLoadingLineColor(0xFF1E2A2E.toInt())
                            overlayManager.tilesOverlay.setColorFilter(if (night) nightFilter else null)
                            // 最大拉近起步:首屏即街道级细节,用户在拖动缩放前就知道自己在哪
                            controller.setZoom(19.0)
                            addMapListener(mapListener)

                            // 长按投放标记(默认标签「标记 %02d」由 manager 生成,id 单调不重复)
                            overlays.add(
                                MapEventsOverlay(object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                                    override fun longPressHelper(p: GeoPoint?): Boolean {
                                        p ?: return false
                                        manager.addMarker("", p.latitude, p.longitude)
                                        return true
                                    }
                                }),
                            )
                            // 自机菱形 + 精度圈 + 航向线(单个稳定实例,不随 tick 重建)
                            overlays.add(selfOverlay)

                            // 公制比例尺
                            val dm = resources.displayMetrics
                            overlays.add(
                                ScaleBarOverlay(this).apply {
                                    setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric)
                                    setTextSize(10f * dm.density)
                                    setLineWidth(1f * dm.density)
                                    setTextPaint(android.graphics.Paint().apply {
                                        color = 0xFF45E0FF.toInt()
                                        isAntiAlias = true
                                        textSize = 10f * dm.density
                                    })
                                    setBarPaint(android.graphics.Paint().apply {
                                        color = 0xFF45E0FF.toInt()
                                        isAntiAlias = true
                                        style = android.graphics.Paint.Style.STROKE
                                        strokeWidth = 1.5f * dm.density
                                    })
                                    setBackgroundPaint(android.graphics.Paint().apply {
                                        color = 0x9908080A.toInt()
                                    })
                                },
                            )

                            // 经纬度网格
                            overlays.add(
                                LatLonGridlineOverlay2().apply {
                                    setLineColor(0x3345E0FF)
                                    setFontColor(0xCCE8FFF8.toInt())
                                    setBackgroundColor(0xCC08080A.toInt())
                                    setTextStyle(android.graphics.Paint.Style.FILL)
                                    setFontSizeDp((10f * dm.density).toInt().toShort())
                                    setMultiplier(1f)
                                    setLineWidth(1f)
                                },
                            )

                            post {
                                mapView = this
                                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                    onResume()
                                }
                            }
                        }
                    },
                    update = { map ->
                        val locNow = manager.location.value

                        // 首个 fix:居中 + 最大缩放(19 级);程序化移动屏蔽 onScroll,不误退出跟随
                        if (!centeredOnce && locNow != null) {
                            suppressMapEvents = true
                            map.controller.setCenter(GeoPoint(locNow.latitude, locNow.longitude))
                            map.controller.setZoom(19.0)
                            zoomText = map.zoomLevelDouble
                            suppressMapEvents = false
                            centeredOnce = true
                        }
                        if (recenterRequested > 0 && locNow != null) {
                            suppressMapEvents = true
                            map.controller.setCenter(GeoPoint(locNow.latitude, locNow.longitude))
                            map.controller.setZoom(19.0)
                            zoomText = map.zoomLevelDouble
                            suppressMapEvents = false
                            recenterRequested = 0
                        }
                        if (follow && centeredOnce && locNow != null) {
                            suppressMapEvents = true
                            map.controller.setCenter(GeoPoint(locNow.latitude, locNow.longitude))
                            suppressMapEvents = false
                        }

                        map.overlayManager.tilesOverlay.setColorFilter(if (night) nightFilter else null)
                        map.setUseDataConnection(!cacheOnly)

                        // 标记增量同步:以 id 为键,不整层重建、不重复 add
                        val seen = markerViews.keys.toMutableSet()
                        markers.forEach { m ->
                            if (markerViews[m.id] == null) {
                                val mk = Marker(map).apply {
                                    position = GeoPoint(m.lat, m.lon)
                                    title = m.label
                                    snippet = "%.5f, %.5f · %s".format(
                                        m.lat, m.lon,
                                        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(m.createdMs)),
                                    )
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = diamondMarkerIcon(map.resources.displayMetrics.density, 18, 0xFFFFB547.toInt())
                                    setOnMarkerClickListener { marker, mv ->
                                        marker.showInfoWindow()
                                        mv.controller.setCenter(marker.position)
                                        true
                                    }
                                }
                                map.overlays.add(mk)
                                markerViews[m.id] = mk
                            }
                            seen.remove(m.id)
                        }
                        seen.forEach { id ->
                            markerViews.remove(id)?.let { map.overlays.remove(it) }
                        }
                        map.invalidate()
                    },
                    onRelease = { map ->
                        map.removeMapListener(mapListener)
                        markerViews.clear()
                        runCatching { map.onPause() }
                        runCatching { map.onDetach() }
                        mapView = null
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // 自绘缩放 ±:用 canZoomIn()/canZoomOut() 控制禁用态,显示当前 Z 级
                Column(
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MapZoomButton("+", mapView?.canZoomIn() == true) {
                        val map = mapView
                        if (map != null) {
                            haptic.hud()
                            if (map.canZoomIn()) map.controller.zoomIn()
                            zoomText = map.zoomLevelDouble
                        }
                    }
                    MapZoomButton("−", mapView?.canZoomOut() == true) {
                        val map = mapView
                        if (map != null) {
                            haptic.hud()
                            if (map.canZoomOut()) map.controller.zoomOut()
                            zoomText = map.zoomLevelDouble
                        }
                    }
                    Surface(
                        shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                        color = Dark.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.35f)),
                    ) {
                        Text(
                            "Z%.1f".format(zoomText),
                            fontFamily = Mono, fontSize = 8.sp, color = TacGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.tact_markers_panel)) {
                if (markers.isEmpty()) {
                    EmptyHint(stringResource(R.string.tact_markers_empty))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 120.dp)) {
                        items(markers, key = { it.id }) { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("◆ ${m.label}", fontFamily = Sans, fontSize = 10.sp, color = White, modifier = Modifier.weight(1f))
                                Text("%.5f, %.5f".format(m.lat, m.lon), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.tact_delete), fontFamily = Sans, fontSize = 9.sp, color = Danger, modifier = Modifier.clickable { haptic.hud(); manager.removeMarker(m) })
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.tact_map_attribution),
                    fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
