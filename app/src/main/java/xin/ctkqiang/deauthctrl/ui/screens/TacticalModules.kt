package xin.ctkqiang.deauthctrl.ui.screens

import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import xin.ctkqiang.deauthctrl.manager.ReceivedPhoto
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
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.IrRemoteViewModel
import xin.ctkqiang.deauthctrl.viewmodel.NfcViewModel
import xin.ctkqiang.deauthctrl.viewmodel.NightVisionViewModel
import xin.ctkqiang.deauthctrl.viewmodel.TacticalCameraViewModel
import xin.ctkqiang.deauthctrl.viewmodel.TacticalMapViewModel
import xin.ctkqiang.deauthctrl.viewmodel.VoiceSdrViewModel
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val host = remember { xin.ctkqiang.deauthctrl.manager.PhotoRelayHost(context.applicationContext) }
    val cameras = manager.cameras
    var selectedId by remember { mutableStateOf(cameras.firstOrNull()?.id ?: "") }
    var manualIp by remember { mutableStateOf("") }
    var selectedPeerId by remember { mutableStateOf<String?>(null) } // null = 全部在线
    val lastError by manager.lastError.collectAsState()
    val pending by manager.pendingShot.collectAsState()
    val peers by host.peers.collectAsState()
    val online by host.online.collectAsState()
    val received by host.received.collectAsState()
    var viewerPhoto by remember { mutableStateOf<ReceivedPhoto?>(null) }

    // 进入页面:注入呼号,常开中继随应用进程始终在线,无需任何开启动作
    LaunchedEffect(Unit) { host.touch(operatorName.ifBlank { "operator" }) }

    // 通知点击直达:自动打开对应照片
    LaunchedEffect(openPhotoId) {
        openPhotoId?.let { id ->
            host.photoById(id)?.let { viewerPhoto = it }
        }
    }

    val targetLabel = when {
        selectedPeerId != null -> peers.firstOrNull { it.id == selectedPeerId }?.name ?: "目标"
        manualIp.isNotBlank() -> manualIp
        else -> "全部在线 (${peers.size})"
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("战术相机", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            TermPanel("镜头选择 · 最大分辨率直出") {
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
                    EmptyHint("未检测到相机")
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── 控制区(拍摄 / 发送 / 接收) —— 独立滚动,取景框保持固定高度 ──
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {

            // ── 第一步:拍摄 ──
            ActionButton(running = false, runLabel = "拍摄照片", stopLabel = "") {
                haptic.hud()
                manager.capture()
            }
            lastError?.let { Text(it, fontFamily = Mono, fontSize = 9.sp, color = if (it.startsWith("已捕获") || it.startsWith("已发送")) TacGreen else Danger, modifier = Modifier.padding(top = 4.dp)) }

            // ── 第二步:确认 + 选择目标 + 发送 ──
            pending?.let { shot ->
                Spacer(Modifier.height(8.dp))
                TermPanel("已捕获 · 待发送") {
                    val bmp = remember(shot.timeMs) { BitmapFactory.decodeByteArray(shot.jpeg, 0, shot.jpeg.size) }
                    bmp?.let {
                        Image(it.asImageBitmap(), contentDescription = "已捕获照片", modifier = Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height.toFloat()))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${shot.width}×${shot.height} · ${shot.jpeg.size / 1024}KB · ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(shot.timeMs))}", fontFamily = Mono, fontSize = 9.sp, color = Gray)

                    Spacer(Modifier.height(8.dp))

                    // 目标选择
                    Text("发送目标", fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Red)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        val allSelected = selectedPeerId == null && manualIp.isBlank()
                        Surface(
                            modifier = Modifier.clickable { selectedPeerId = null; manualIp = "" },
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = if (allSelected) Red.copy(alpha = 0.16f) else SurfaceBg,
                            border = BorderStroke(1.dp, if (allSelected) Red.copy(alpha = 0.7f) else BorderDim),
                        ) {
                            Text("全部 (${peers.size})", fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (allSelected) Red else White, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                        }
                        peers.forEach { peer ->
                            val selected = peer.id == selectedPeerId
                            Surface(
                                modifier = Modifier.clickable {
                                    selectedPeerId = if (selected) null else peer.id
                                    if (!selected) manualIp = ""
                                },
                                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                color = if (selected) Red.copy(alpha = 0.16f) else SurfaceBg,
                                border = BorderStroke(1.dp, if (selected) Red.copy(alpha = 0.7f) else BorderDim),
                            ) {
                                Text(peer.name.take(12), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) Red else White, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    TermInput("手动目标 IP(可选)", manualIp, placeholder = "没有自动发现到设备时手动输入") { manualIp = it; selectedPeerId = null }

                    Spacer(Modifier.height(6.dp))
                    ActionButton(running = false, runLabel = "发送照片 → $targetLabel", stopLabel = "") {
                        haptic.hud()
                        val targets = when {
                            manualIp.isNotBlank() -> listOf(manualIp)
                            selectedPeerId != null -> peers.firstOrNull { it.id == selectedPeerId }?.let { listOf(it.ip) } ?: emptyList()
                            else -> peers.map { it.ip }
                        }
                        if (targets.isEmpty()) {
                            manager.reportError("没有可发送的目标 —— 等待同网段设备上线")
                        } else {
                            manager.sendPending(targets, operatorName.ifBlank { "operator" })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); manager.clearPending() },
                        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Text("丢弃照片", fontFamily = Sans, fontSize = 10.sp, color = Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 在线接收台状态(常开) ──
            TermPanel("在线接收台 · 自动发现 (UDP 9997)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (online) "● 本机接收台常开 — 同网段设备随时可投递照片" else "接收台未就绪(端口被占用)",
                        fontFamily = Mono, fontSize = 9.sp,
                        color = if (online) TacGreen else Danger,
                        modifier = Modifier.weight(1f),
                    )
                    Text("在线 ${peers.size} 台", fontFamily = Mono, fontSize = 9.sp, color = Gold)
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── 接收照片列表 ──
            TermPanel("接收照片 · 常开中继 (${received.size})") {
                if (received.isEmpty()) {
                    EmptyHint("对方拍摄发送后,照片自动到达并推送系统通知")
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
                                            contentDescription = "收到的照片",
                                            modifier = Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height.toFloat()),
                                        )
                                    }
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
                    Image(it.asImageBitmap(), contentDescription = "全屏照片", modifier = Modifier.fillMaxWidth())
                }
                Column(Modifier.align(Alignment.TopCenter).padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("来自 ${photo.from}", fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = White)
                    Text("${photo.width}×${photo.height} · ${photo.jpeg.size / 1024}KB · 点击任意处关闭", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                }
            }
        }
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

    // 瀑布历史:最近 110 帧(新帧在顶部,历史向下滚动 —— SDR 惯例)
    val waterfall = remember { mutableStateListOf<FloatArray>() }
    LaunchedEffect(frame) {
        frame?.let { f ->
            waterfall.add(0, f.bins)
            if (waterfall.size > 110) waterfall.removeAt(waterfall.size - 1)
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
        TerminalHeader("语音频谱 · SDR", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            ActionButton(running, "开启频谱侦测", "停止侦测") {
                haptic.hud()
                if (running) manager.stop() else manager.start()
            }
            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 9.sp, color = Danger, modifier = Modifier.padding(top = 4.dp)) }

            Spacer(Modifier.height(8.dp))

            // ── 专业读数条 ──
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val f = frame
                    SdrReadout("CENTER", "11.025 kHz")
                    SdrReadout("SPAN", "22.05 kHz")
                    SdrReadout("RBW", "10.77 Hz")
                    SdrReadout("PWR", if (f != null) "%.0f dB".format(f.rmsDb) else "--")
                    SdrReadout("噪声底", if (f != null) "%.0f dB".format(f.noiseFloorDb) else "--")
                    SdrReadout("峰值", if (f != null) "%.1f kHz".format(f.peakHz / 1000f) else "--")
                }
            }

            Spacer(Modifier.height(8.dp))

            // 频谱面板标题条(真实 SDR 的通道标识)
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SPECTRUM · dBFS", fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TacGreen)
                Text(if (running) "● REC" else "○ STBY", fontFamily = Mono, fontSize = 8.sp, color = if (running) Red else Gray)
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
                    // 纵向频率网格
                    for (khz in 2..20 step 2) {
                        val x = size.width * khz / 22f
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

                    // 频谱填充(底部到曲线)
                    frame?.let { f ->
                        val path = Path()
                        f.bins.forEachIndexed { i, db ->
                            val x = size.width * i / f.bins.size
                            val y = yOf(db.coerceIn(lo, hi))
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.lineTo(size.width, size.height)
                        path.lineTo(0f, size.height)
                        path.close()
                        drawPath(path, TacGreen.copy(alpha = 0.16f))
                        drawPath(path, TacGreen, style = Stroke(1.2f))
                    }

                    // max-hold 金色轨迹
                    val hold = maxHold.value
                    if (hold.isNotEmpty() && running) {
                        val hp = Path()
                        hold.forEachIndexed { i, db ->
                            val x = size.width * i / hold.size
                            val y = yOf(db.coerceIn(lo, hi))
                            if (i == 0) hp.moveTo(x, y) else hp.lineTo(x, y)
                        }
                        drawPath(hp, Gold.copy(alpha = 0.75f), style = Stroke(1.6f))
                    }

                    // 峰值标记
                    frame?.let { f ->
                        val px = size.width * (f.peakHz / 22050f)
                        val py = yOf(f.peakDb.coerceIn(lo, hi))
                        drawCircle(Gold, 3.5f, Offset(px, py))
                        drawLine(Gold.copy(alpha = 0.6f), Offset(px, py), Offset(px, 0f), 1f)
                        val m = scaleMeasurer.measure(
                            "%.1fkHz %.0fdB".format(f.peakHz / 1000f, f.peakDb),
                            androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 8.sp, color = Gold),
                        )
                        drawText(m, topLeft = Offset((px + 5f).coerceAtMost(size.width - m.size.width), py - m.size.height))
                    }

                    // 频率轴刻度
                    for (khz in intArrayOf(0, 5, 10, 15, 20)) {
                        val x = size.width * khz / 22.05f
                        val m = scaleMeasurer.measure(
                            "${khz}k", androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 8.sp, color = Gray),
                        )
                        drawText(m, topLeft = Offset(x - m.size.width / 2f, size.height - m.size.height))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // 瀑布面板标题条
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("WATERFALL · HEAT", fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
                Text("${waterfall.size} 帧历史", fontFamily = Mono, fontSize = 8.sp, color = Gray)
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

// ══════════════════════════════════════════════════════════════════════════
// 红外遥控 —— ConsumerIrManager
// ══════════════════════════════════════════════════════════════════════════

@Composable
fun IrRemoteScreen(vm: IrRemoteViewModel, back: () -> Unit) {
    val manager = vm.manager
    val haptic = LocalHapticFeedback.current
    val last by manager.lastTransmit.collectAsState()
    var necCmd by remember { mutableStateOf("45") }
    var necAddr by remember { mutableStateOf("00") }
    var rawPattern by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("红外遥控", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
            TermPanel("发射器状态 · IR EMITTER") {
                if (manager.hasEmitter) {
                    Text("● 检测到红外发射器", fontFamily = Mono, fontSize = 11.sp, color = TacGreen)
                    Text("载波范围: ${manager.info.carrierFreqs.joinToString(" - ")} Hz", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                } else {
                    Text("✕ 本机无红外发射器（小米/Mi Remote 系列等机型支持）", fontFamily = Sans, fontSize = 11.sp, color = Danger)
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel("电视常用键 · NEC 38kHz") {
                val keys = listOf(
                    "电源" to 0x45, "静音" to 0x11, "输入源" to 0x49,
                    "音量+" to 0x15, "音量-" to 0x09,
                    "频道+" to 0x46, "频道-" to 0x47,
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
                Text("数字键为 NEC 0x00 地址通用映射，各品牌可能存在偏差", fontFamily = Sans, fontSize = 8.sp, color = Gray, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(8.dp))
            TermPanel("自定义码发射") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TermInput("地址(hex)", necAddr, modifier = Modifier.weight(1f)) { necAddr = it }
                    TermInput("命令(hex)", necCmd, modifier = Modifier.weight(1f)) { necCmd = it }
                }
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptic.hud()
                        val a = necAddr.toIntOrNull(16) ?: 0
                        val c = necCmd.toIntOrNull(16) ?: 0
                        manager.transmitNec(a, c)
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Red.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Red.copy(alpha = 0.45f)),
                ) {
                    Text("发射 NEC", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                }
                Spacer(Modifier.height(8.dp))
                TermInput("原始脉冲 (µs, 逗号分隔)", rawPattern, placeholder = "9000,4500,560,1690,...") { rawPattern = it }
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptic.hud()
                        val pattern = rawPattern.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
                        if (pattern.isNotEmpty()) manager.transmitRaw(38_000, pattern, label = "RAW ${pattern.size} 段")
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f)),
                ) {
                    Text("发射原始码", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                }
            }
            last?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontFamily = Mono, fontSize = 9.sp, color = if (it.startsWith("已发射")) TacGreen else Danger)
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

    // 页面期间开启前台读卡
    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        activity?.let { manager.startReader(it) }
        onDispose { activity?.let { manager.stopReader(it) } }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("NFC · RFID 工作台", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
            TermPanel("适配器状态") {
                when {
                    !manager.supported -> Text("✕ 本机无 NFC 硬件", fontFamily = Sans, fontSize = 11.sp, color = Danger)
                    !manager.enabled -> {
                        Text("NFC 已关闭", fontFamily = Sans, fontSize = 11.sp, color = Warn)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.clickable { manager.openNfcSettings() },
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            color = Red.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Red.copy(alpha = 0.45f)),
                        ) {
                            Text("打开系统 NFC 设置", fontFamily = Sans, fontSize = 11.sp, color = Red, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                    else -> Text("● 读卡器就绪 —— 将标签贴近手机背部", fontFamily = Mono, fontSize = 11.sp, color = TacGreen)
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel("读卡结果 · LAST TAG") {
                val t = lastTag
                if (t == null) {
                    EmptyHint("等待标签接近…")
                } else {
                    Column {
                        InfoLine("UID", t.uid)
                        InfoLine("技术", t.techs.joinToString(" / "))
                        InfoLine("NDEF", if (t.ndefRecords.isEmpty()) "无 NDEF 记录" else t.ndefRecords.joinToString("\n"))
                        InfoLine("扇区", if (t.sectorCount > 0) "${t.sectorCount} (Mifare Classic)" else "--")
                        InfoLine("可写", if (t.writable) "是" else "否")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel("写入 NDEF 文本") {
                Text("支持 NTAG / Ultralight 等可自由读写标签；加密卡（Mifare Classic 带密钥）无法克隆 —— 密钥由厂商烧写，未授权穷举既不合法也不可行", fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp)
                Spacer(Modifier.height(6.dp))
                TermInput("写入内容", writeText, placeholder = "贴卡后写入这段文本") { writeText = it }
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
                    Text("准备写入", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                }
            }
            message?.let { Text(it, fontFamily = Mono, fontSize = 9.sp, color = TacGreen, modifier = Modifier.padding(top = 4.dp)) }

            Spacer(Modifier.height(8.dp))
            CardWalletPanel()

            Spacer(Modifier.height(10.dp))
        }
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

    TermPanel("卡模拟钱包 · HCE EMULATION") {
        Text(
            "手机化身 NFC 卡:钱包存多张虚拟卡,选择一张设为活跃,读卡器贴近手机时由系统 HCE 服务应答(ISO 14443-4 APDU)",
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        if (cards.isEmpty()) {
            EmptyHint("还没有虚拟卡 —— 在下方添加")
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(card.name, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
                            if (active) {
                                Text("● 活跃", fontFamily = Mono, fontSize = 8.sp, color = TacGreen)
                            }
                        }
                        Text("AID F0010203040506 · 数据 ${card.dataHex.ifBlank { "空" }.take(24)}", fontFamily = Mono, fontSize = 8.sp, color = Gray)
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!active) {
                                Surface(
                                    modifier = Modifier.clickable { haptic.hud(); wallet.setActive(card.id) },
                                    shape = CutCornerShape(5.dp),
                                    color = Red.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                                ) {
                                    Text("设为活跃", fontFamily = Sans, fontSize = 9.sp, color = Red, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                            Surface(
                                modifier = Modifier.clickable { haptic.hud(); wallet.removeCard(card.id) },
                                shape = CutCornerShape(5.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, BorderDim),
                            ) {
                                Text("删除", fontFamily = Sans, fontSize = 9.sp, color = Gray, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        TermInput("卡名", newName, placeholder = "如:研究卡 A") { newName = it }
        Spacer(Modifier.height(4.dp))
        TermInput("卡数据 (hex,可选)", newData, placeholder = "如: DEADBEEF") { newData = it }
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
            Text("添加虚拟卡", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "能力边界: HCE 仅模拟 ISO-DEP APDU 卡(自定义读卡器/研究场景); MIFARE Classic 门禁卡与支付卡需安全元件与发卡密钥,普通手机无法模拟",
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
    val threat by manager.threat.collectAsState()
    val cameras = manager.cameras
    var selectedId by remember { mutableStateOf(cameras.firstOrNull()?.id ?: "") }
    var gain by remember { mutableStateOf(4f) }

    // 进入页面自动开启夜视(镜头就绪即出画面);离开自动关闭
    DisposableEffect(selectedId) {
        if (selectedId.isNotEmpty()) manager.start(selectedId)
        onDispose { manager.stop() }
    }

    // 威胁变化触觉:无 → 有,或数量增加时震动警报
    var lastThreatCount by remember { mutableStateOf(0) }
    LaunchedEffect(threat) {
        val count = threat?.let { it.faces + it.humans } ?: 0
        if (count > 0 && count != lastThreatCount) {
            haptic.hud()
        }
        lastThreatCount = count
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("数字夜视", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            // 镜头选择
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cameras.forEach { c ->
                    val selected = c.id == selectedId
                    Surface(
                        modifier = Modifier.clickable { selectedId = c.id; if (running) manager.start(c.id) },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = if (selected) Red.copy(alpha = 0.16f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (selected) Red.copy(alpha = 0.7f) else BorderDim),
                    ) {
                        Text(c.name, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) Red else White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            ActionButton(running, "开启夜视", "关闭夜视") {
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
                    .border(1.dp, if (threat?.anyThreat == true) Danger.copy(alpha = 0.8f) else if (running) TacGreen.copy(alpha = 0.5f) else BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                frame?.let { bmp ->
                    Image(bmp.asImageBitmap(), contentDescription = "夜视画面", modifier = Modifier.fillMaxSize())
                } ?: EmptyHint(if (running) "增强画面生成中…" else "开启夜视后显示绿色增强画面")

                // OpenCV 威胁叠加层:红框 + 红色 tint + 涟漪扩散
                threat?.let { report ->
                    if (report.anyThreat) {
                        // 红色警示 tint
                        Box(Modifier.fillMaxSize().background(Danger.copy(alpha = 0.10f)))
                        // 检测框 + 涟漪
                        val ripple = rememberInfiniteTransition()
                        val phase by ripple.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = androidx.compose.animation.core.LinearEasing)))
                        val tagMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                        Canvas(Modifier.fillMaxSize()) {
                            report.boxes.forEach { box ->
                                val x = box.x * size.width
                                val y = box.y * size.height
                                val w = box.w * size.width
                                val h = box.h * size.height
                                // 红色检测框(人脸实线,人体虚线)
                                drawRect(
                                    color = Danger,
                                    topLeft = Offset(x, y),
                                    size = androidx.compose.ui.geometry.Size(w, h),
                                    style = Stroke(width = 3f, pathEffect = if (box.kind == "human") PathEffect.dashPathEffect(floatArrayOf(14f, 8f)) else null),
                                )
                                // 涟漪:从框中心扩散的警示环
                                val cx = x + w / 2f
                                val cy = y + h / 2f
                                val maxR = maxOf(w, h) * 1.1f
                                val r1 = maxR * (0.15f + phase * 0.85f)
                                drawCircle(Danger.copy(alpha = (1f - phase) * 0.7f), r1, Offset(cx, cy), style = Stroke(3f))
                                drawCircle(Danger.copy(alpha = (1f - phase) * 0.25f), r1 * 0.7f, Offset(cx, cy), style = Stroke(8f))
                                // 类型标签
                                val m = tagMeasurer.measure(
                                    if (box.kind == "face") "FACE" else "HUMAN",
                                    androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = White),
                                )
                                drawText(m, topLeft = Offset(x, (y - m.size.height - 3f).coerceAtLeast(0f)))
                            }
                        }
                        // 顶部威胁横幅
                        Surface(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            color = Danger.copy(alpha = 0.85f),
                        ) {
                            Text(
                                "⚠ 检测到目标 — 人脸 ${report.faces} · 人体 ${report.humans}",
                                fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                if (running) {
                    // 四角 HUD 锁定框(无威胁=战术青,有威胁=危险红)
                    TacticalCorners(color = if (threat?.anyThreat == true) Danger else TacGreen, modifier = Modifier.fillMaxSize(), len = 10.dp, stroke = 1.4.dp)
                    // 状态标签
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                        color = Dark.copy(alpha = 0.75f),
                    ) {
                        Text(
                            when {
                                !manager.detector.available -> "OpenCV 检测器不可用 · 画面增强模式"
                                threat?.anyThreat == true -> "TARGET LOCKED · 持续跟踪"
                                else -> "SCANNING · 无威胁目标"
                            },
                            fontFamily = Mono, fontSize = 9.sp,
                            color = if (threat?.anyThreat == true) Danger else TacGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel("增益控制 · GAIN") {
                Text("增益 ${gain.toInt()}x —— 暗部放大倍数", fontFamily = Mono, fontSize = 10.sp, color = Gold)
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
                Text("原理: 高 ISO 捕获 → 亮度通道增益 → 荧光绿映射。消费级手机无红外补光，此为数字增强而非热成像", fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp)
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
    val haptic = LocalHapticFeedback.current
    val location by manager.location.collectAsState()
    val markers by manager.markers.collectAsState()
    var recenterRequested by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        manager.start()
        onDispose { manager.stop() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("战术地图", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        location?.let { "%.6f, %.6f".format(it.latitude, it.longitude) } ?: "定位中…",
                        fontFamily = Mono, fontSize = 10.sp, color = if (location != null) TacGreen else Gray,
                    )
                    Text(if (manager.gpsOn.value) "GPS 在线" else "GPS 离线", fontFamily = Mono, fontSize = 10.sp, color = if (manager.gpsOn.value) TacGreen else Warn)
                    Text("标记 ${markers.size}", fontFamily = Mono, fontSize = 10.sp, color = Gold)
                }
            }

            Spacer(Modifier.height(6.dp))

            // 快速定位
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("瓦片: OpenStreetMap 公共服务 · 无需 API Key", fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.weight(1f))
                Surface(
                    modifier = Modifier.clickable {
                        haptic.hud()
                        recenterRequested++
                    },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = TacGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.6f)),
                ) {
                    Text("◎ 定位我", fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacGreen, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
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
                            // 夜战滤镜:暗色 + 军绿
                            overlayManager.tilesOverlay.setColorFilter(manager.nightFilter())
                            controller.setZoom(16.0)
                            // 长按投放标记
                            overlays.add(
                                MapEventsOverlay(object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                                    override fun longPressHelper(p: GeoPoint?): Boolean {
                                        p ?: return false
                                        manager.addMarker("标记 ${manager.markers.value.size + 1}", p.latitude, p.longitude)
                                        return true
                                    }
                                }),
                            )
                            // 初始位置
                            manager.location.value?.let {
                                controller.setCenter(GeoPoint(it.latitude, it.longitude))
                            }
                        }
                    },
                    update = { map ->
                        // 快速定位:一键回到本机位置并放大
                        if (recenterRequested > 0) {
                            val locNow = manager.location.value
                            if (locNow != null) {
                                map.controller.animateTo(GeoPoint(locNow.latitude, locNow.longitude))
                                map.controller.setZoom(17.0)
                                recenterRequested = 0
                            }
                        }
                        // 本机位置标记
                        map.overlays.removeAll { it is Marker }
                        location?.let { loc ->
                            val marker = Marker(map).apply {
                                position = GeoPoint(loc.latitude, loc.longitude)
                                title = "本机"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = runCatching {
                                    android.graphics.drawable.ShapeDrawable().apply {
                                        shape = android.graphics.drawable.shapes.OvalShape()
                                        paint.color = 0xFF45E0FF.toInt()
                                        setIntrinsicWidth(26)
                                        setIntrinsicHeight(26)
                                    }
                                }.getOrNull()
                            }
                            map.overlays.add(marker)
                        }
                        // 战术标记
                        markers.forEach { m ->
                            val mk = Marker(map).apply {
                                position = GeoPoint(m.lat, m.lon)
                                title = m.label
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = runCatching {
                                    android.graphics.drawable.ShapeDrawable().apply {
                                        shape = android.graphics.drawable.shapes.OvalShape()
                                        paint.color = 0xFFFFB547.toInt()
                                        setIntrinsicWidth(22)
                                        setIntrinsicHeight(22)
                                    }
                                }.getOrNull()
                            }
                            map.overlays.add(mk)
                        }
                        map.invalidate()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(8.dp))
            TermPanel("战术标记 · MARKERS") {
                if (markers.isEmpty()) {
                    EmptyHint("长按地图投放标记点；标记只存于本机")
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 120.dp)) {
                        items(markers, key = { it.createdMs }) { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("◆ ${m.label}", fontFamily = Sans, fontSize = 10.sp, color = White, modifier = Modifier.weight(1f))
                                Text("%.5f, %.5f".format(m.lat, m.lon), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                                Spacer(Modifier.width(8.dp))
                                Text("删除", fontFamily = Sans, fontSize = 9.sp, color = Danger, modifier = Modifier.clickable { haptic.hud(); manager.removeMarker(m) })
                            }
                        }
                    }
                }
                Text("瓦片 © OpenStreetMap 贡献者 · 公共瓦片服务无需 API Key · 已加载区域自动缓存", fontFamily = Sans, fontSize = 8.sp, color = Gray, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
