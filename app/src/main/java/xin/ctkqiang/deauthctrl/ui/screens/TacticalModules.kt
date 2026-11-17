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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

// ══════════════════════════════════════════════════════════════════════════
// 战术相机 —— Camera2 最大分辨率 + Base64 局域网传输
// ══════════════════════════════════════════════════════════════════════════

@Composable
fun TacticalCameraScreen(vm: TacticalCameraViewModel, back: () -> Unit) {
    val manager = vm.manager
    val haptic = LocalHapticFeedback.current
    val cameras = manager.cameras
    var selectedId by remember { mutableStateOf(cameras.firstOrNull()?.id ?: "") }
    var targetIp by remember { mutableStateOf("") }
    val lastError by manager.lastError.collectAsState()
    val receiving by manager.receiving.collectAsState()
    val received by manager.received.collectAsState()

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("战术相机", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
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
                Text("不使用系统相机应用 —— Camera2 直控传感器，按镜头最大 JPEG 分辨率捕获", fontFamily = Sans, fontSize = 9.sp, color = Gray, modifier = Modifier.padding(top = 5.dp))
            }

            Spacer(Modifier.height(8.dp))

            // 预览
            Box(
                Modifier.fillMaxWidth().height(220.dp)
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
                                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
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

            Spacer(Modifier.height(8.dp))
            TermInput("目标 IP", targetIp, placeholder = "同网段设备 IP，如 192.168.1.x") { targetIp = it }
            Spacer(Modifier.height(6.dp))
            ActionButton(running = false, runLabel = "拍摄并发送 → Base64", stopLabel = "") {
                haptic.hud()
                if (targetIp.isBlank()) {
                    manager.reportError("请先输入同网段目标 IP")
                } else {
                    manager.captureAndSend(targetIp, "operator")
                }
            }
            lastError?.let { Text(it, fontFamily = Mono, fontSize = 9.sp, color = if (it.startsWith("已发送")) TacGreen else Danger, modifier = Modifier.padding(top = 4.dp)) }

            Spacer(Modifier.height(10.dp))
            TermPanel("接收台 · PORT ${TacticalCameraPort}") {
                Text("本机地址: ${localIps()}", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                Spacer(Modifier.height(6.dp))
                ActionButton(running = receiving, runLabel = "开启接收", stopLabel = "停止接收") {
                    haptic.hud()
                    if (receiving) manager.stopReceive() else manager.startReceive()
                }
                Spacer(Modifier.height(6.dp))
                if (received.isEmpty()) {
                    EmptyHint("对方拍摄并发送后，照片会在这里出现")
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                        items(received, key = { it.receivedMs }) { photo ->
                            Surface(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                                color = SurfaceBg,
                                border = BorderStroke(1.dp, BorderDim),
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text("${photo.from} · ${photo.width}×${photo.height} · ${photo.jpeg.size / 1024}KB", fontFamily = Mono, fontSize = 9.sp, color = Gold)
                                    Spacer(Modifier.height(4.dp))
                                    val bmp = remember(photo.receivedMs) { BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size) }
                                    bmp?.let {
                                        Image(
                                            it.asImageBitmap(),
                                            contentDescription = "收到的照片",
                                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
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

private const val TacticalCameraPort = 10088

private fun localIps(): String =
    runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { iface -> iface.inetAddresses.toList() }
            .filter { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
            .joinToString(" · ") { it.hostAddress ?: "" }
            .ifBlank { "--" }
    }.getOrDefault("--")

// ══════════════════════════════════════════════════════════════════════════
// 语音频谱分析器 —— SDR 风格实时频谱 + 瀑布
// ══════════════════════════════════════════════════════════════════════════

@Composable
fun VoiceSdrScreen(vm: VoiceSdrViewModel, back: () -> Unit) {
    val manager = vm.manager
    val haptic = LocalHapticFeedback.current
    val running by manager.running.collectAsState()
    val frame by manager.frame.collectAsState()
    val error by manager.error.collectAsState()

    // 瀑布历史：最近 80 帧
    val waterfall = remember { mutableStateListOf<FloatArray>() }
    LaunchedEffect(frame) {
        frame?.let { f ->
            waterfall.add(f.bins)
            if (waterfall.size > 80) waterfall.removeAt(0)
        }
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
            // 峰值读数条
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val f = frame
                    Column {
                        Text("峰值频率", fontFamily = Sans, fontSize = 8.sp, color = Gray)
                        Text(if (f != null) "%.1f Hz".format(f.peakHz) else "--", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Gold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("峰值电平", fontFamily = Sans, fontSize = 8.sp, color = Gray)
                        Text(if (f != null) "%.1f dBFS".format(f.peakDb) else "--", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if ((f?.peakDb ?: -99f) > -30f) Danger else TacGreen)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RMS", fontFamily = Sans, fontSize = 8.sp, color = Gray)
                        Text(if (f != null) "%.1f dBFS".format(f.rmsDb) else "--", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacGreen)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("采样 / FFT", fontFamily = Sans, fontSize = 8.sp, color = Gray)
                        Text("44.1kHz / 4096", fontFamily = Mono, fontSize = 11.sp, color = White)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 实时频谱曲线
            Box(
                Modifier.fillMaxWidth().weight(0.45f)
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .background(SurfaceAlt)
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                val scaleMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    // dB 网格
                    for (db in -100 downTo -20 step 20) {
                        val y = size.height * (db + 120f) / 120f
                        drawLine(Gray.copy(alpha = 0.15f), Offset(0f, y), Offset(size.width, y), 0.7f)
                    }
                    // 1kHz 网格线
                    for (khz in 1..22 step 2) {
                        val x = size.width * khz / 22.05f
                        drawLine(Gray.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, size.height), 0.7f)
                    }
                    frame?.let { f ->
                        val path = Path()
                        f.bins.forEachIndexed { i, db ->
                            val x = size.width * i / f.bins.size
                            val y = size.height * (db + 120f) / 120f
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, TacGreen.copy(alpha = 0.25f), style = Stroke(4f))
                        drawPath(path, TacGreen, style = Stroke(1.3f))
                        // 峰值标记
                        val px = size.width * (f.peakHz / 22050f)
                        val py = size.height * (f.peakDb + 120f) / 120f
                        drawCircle(Gold, 3.5f, Offset(px, py))
                        drawLine(Gold.copy(alpha = 0.7f), Offset(px, py), Offset(px, 0f), 1f)
                    }
                    // 频率刻度
                    for (khz in intArrayOf(0, 5, 10, 15, 20)) {
                        val x = size.width * khz / 22.05f
                        val m = scaleMeasurer.measure(
                            "${khz}k", androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 8.sp, color = Gray),
                        )
                        drawText(m, topLeft = Offset(x - m.size.width / 2f, size.height - m.size.height))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 瀑布图
            Box(
                Modifier.fillMaxWidth().weight(0.45f)
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .background(SurfaceAlt)
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (waterfall.isEmpty()) return@Canvas
                    val rowH = size.height / 80f
                    waterfall.forEachIndexed { row, bins ->
                        val y = size.height - (waterfall.size - row) * rowH
                        bins.forEachIndexed { i, db ->
                            val x = size.width * i / bins.size
                            val intensity = ((db + 120f) / 120f).coerceIn(0f, 1f)
                            // 强度 → 绿色亮阶（弱=深绿黑，强=亮绿白）
                            val color = Color(
                                red = intensity * 0.55f,
                                green = 0.10f + intensity * 0.90f,
                                blue = intensity * 0.45f,
                            )
                            drawLine(color, Offset(x, y), Offset(x, y + rowH + 0.6f), 1f)
                        }
                    }
                }
            }
        }
    }
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
    val cameras = manager.cameras
    var selectedId by remember { mutableStateOf(cameras.firstOrNull()?.id ?: "") }
    var gain by remember { mutableStateOf(4f) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("数字夜视", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            ActionButton(running, "开启夜视", "关闭夜视") {
                haptic.hud()
                if (running) manager.stop()
                else if (selectedId.isNotEmpty()) manager.start(selectedId)
            }
            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 9.sp, color = Danger, modifier = Modifier.padding(top = 4.dp)) }

            Spacer(Modifier.height(8.dp))
            // 夜视画面
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .background(Color.Black)
                    .border(1.dp, if (running) TacGreen.copy(alpha = 0.5f) else BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                frame?.let { bmp ->
                    Image(bmp.asImageBitmap(), contentDescription = "夜视画面", modifier = Modifier.fillMaxSize())
                } ?: EmptyHint(if (running) "增强画面生成中…" else "开启夜视后显示绿色增强画面")
                if (running) {
                    // 四角 HUD 锁定框
                    TacticalCorners(color = TacGreen, modifier = Modifier.fillMaxSize(), len = 10.dp, stroke = 1.4.dp)
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

            Spacer(Modifier.height(8.dp))

            // osmdroid 地图
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                AndroidView(
                    factory = { ctx ->
                        // osmdroid 必须配置 userAgent,否则 OSM 瓦片服务器会拒绝请求(403)
                        val cfg = org.osmdroid.config.Configuration.getInstance()
                        cfg.userAgentValue = ctx.packageName + "/" + "1.0"
                        // 瓦片缓存放应用内部目录,避免外部存储权限问题
                        cfg.osmdroidBasePath = java.io.File(ctx.filesDir, "osmdroid")
                        cfg.osmdroidTileCache = java.io.File(ctx.filesDir, "osmdroid/tiles")
                        cfg.load(
                            ctx.applicationContext,
                            ctx.getSharedPreferences("osmdroid", 0),
                        )
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            // 夜战滤镜：暗色 + 军绿
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
                Text("地图瓦片来自 OpenStreetMap（osmdroid 开源渲染），需要网络；已加载区域自动缓存", fontFamily = Sans, fontSize = 8.sp, color = Gray, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
