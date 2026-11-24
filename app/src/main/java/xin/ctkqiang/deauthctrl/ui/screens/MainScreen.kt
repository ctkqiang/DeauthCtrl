package xin.ctkqiang.deauthctrl.ui.screens

import android.app.ActivityManager
import android.app.Application
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Dangerous
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoCall
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlin.math.PI
import xin.ctkqiang.deauthctrl.manager.RadarTarget
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.SshClientManager
import xin.ctkqiang.deauthctrl.mirroring.model.MirroringDevice
import xin.ctkqiang.deauthctrl.mirroring.ui.DashboardScreen
import xin.ctkqiang.deauthctrl.mirroring.ui.MirroringState
import xin.ctkqiang.deauthctrl.mirroring.ui.MirroringViewModel
import xin.ctkqiang.deauthctrl.mirroring.ui.RemoteViewerScreen
import xin.ctkqiang.deauthctrl.ui.components.*
import xin.ctkqiang.deauthctrl.ui.components.ErrorStickerOverlay
import xin.ctkqiang.deauthctrl.ui.components.BruhOverlay
import xin.ctkqiang.deauthctrl.ui.theme.TEAM_THEMES
import xin.ctkqiang.deauthctrl.viewmodel.*
import xin.ctkqiang.deauthctrl.viewmodel.DbViewModel
import xin.ctkqiang.deauthctrl.viewmodel.SshClientViewModel

// ─── 路由名常量 ──────────────────────────────────────────────────────────────

private const val ROUTE_HOME = "home"
private const val ROUTE_MIRRORING = "mirroring"
private const val ROUTE_REMOTE_VIEWER = "remote_viewer"
private const val ROUTE_ABOUT = "about"

/** 免责声明偏好文件名与"已接受"键 —— 首次启动展示法律警告，接受后不再打扰 */
private const val PREFS_NAME = "deauthctrl_prefs"
private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"

/**
 * 应用主路由入口 Composable
 *
 * 整个应用的根组件，负责：
 * 1. 首次启动法律免责声明（接受后持久化）
 * 2. 启动序列播放（BootSequence → 主界面）
 * 3. 页面路由导航（AnimatedContent 实现 21 个页面的过渡动画）
 * 4. 全局 ScanlineOverlay 叠加
 *
 * ## 路由系统
 * `navigate(target)` 进入目标页并把当前页压栈；`goBack()` 弹栈回上一屏。
 * 系统返回键通过 [BackHandler] 接管：详情页逐级回退，只有主页才交还系统。
 */
@Composable
fun MainScreen(
    bleVm: BleSpamViewModel, wifiVm: WifiDisruptViewModel,
    btVm: BluetoothJammerViewModel, wjVm: WifiJammerViewModel,
    wsVm: WebServerViewModel, arpVm: ArpScanViewModel,
    httpVm: HttpClientViewModel, pingVm: PingViewModel,
    blescanVm: BleScannerViewModel, portscanVm: PortScannerViewModel,
    walkieVm: WalkieTalkieViewModel, radarVm: RadarViewModel,
    ftVm: FileTransferViewModel, vaultVm: SecureMediaViewModel,
    revVm: RevShellViewModel, payloadVm: PayloadViewModel,
    bruteVm: DirBruteViewModel, cveVm: CVESearchViewModel,
    mirrorVm: MirroringViewModel,
    terminalVm: SshClientViewModel,
    dbVm: DbViewModel,
    settingsVm: SettingsViewModel,
    aiVm: AiViewModel,
    initialPhotoId: Long? = null,
    /** 调试深链:--es screen <route> 直达指定模块页 */
    initialScreen: String? = null,
    /** 调试深链:--ei chapter <n> wiki 章节直达 */
    initialChapter: Int = -1,
    /** 调试深链:--ez autoscan true CCTV 页进入即扫描 */
    initialAutoScan: Boolean = false,
) {
    // 首次启动免责声明：接受后写入偏好，之后启动不再弹出。
    // 静态预览拿不到 SharedPreferences 时视为已接受，让预览直达主界面。
    val context = LocalContext.current
    val prefs = remember { runCatching { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }.getOrNull() }
    var disclaimerAccepted by remember { mutableStateOf(prefs?.getBoolean(KEY_DISCLAIMER_ACCEPTED, false) ?: true) }

    var boot by remember { mutableStateOf(true) }
    var selectedMirrorDevice by remember { mutableStateOf<MirroringDevice?>(null) }
    var selectedTargetId by remember { mutableStateOf<String?>(null) }

    // 雷达红点触觉警报开关：全局触觉 × 雷达警报 两级门控
    val hapticsOn by settingsVm.hapticsEnabled.collectAsState()
    val radarAlertOn by settingsVm.radarAlertEnabled.collectAsState()

    // 操作员呼号 —— 会议模块与头部行共用
    val username by settingsVm.username.collectAsState()

    var screen by remember { mutableStateOf(ROUTE_HOME) }
    val backStack = remember { mutableStateListOf<String>() }
    var openPhotoId by remember { mutableStateOf<Long?>(null) }

    // 导航脉冲 —— 每次切页整屏主色短闪,像全息投影重新聚焦;
    // alpha 只在绘制层读取,动画期间零重组
    val navFlash = remember { Animatable(0f) }
    LaunchedEffect(screen) {
        navFlash.snapTo(0.09f)
        navFlash.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
    }

    // 通知点击直达:清栈并进入战术相机,自动打开对应照片
    LaunchedEffect(initialPhotoId) {
        if (initialPhotoId != null) {
            backStack.clear()
            openPhotoId = initialPhotoId
            screen = "taccam"
        }
    }

    // 调试深链直达:清栈进入指定路由
    LaunchedEffect(initialScreen) {
        android.util.Log.d("DEEPLINK", "MainScreen effect initialScreen=$initialScreen")
        initialScreen?.takeIf { it.isNotBlank() }?.let {
            backStack.clear()
            screen = it
            android.util.Log.d("DEEPLINK", "navigated to $it")
        }
    }

    /** 进入目标路由，先把当前路由压栈；重复进入同一路由不压栈 */
    // Lumia 式导航:前进页从右滑入、旧页向左退场;返回时反向 —— 方向标记驱动过渡
    var navForward by remember { mutableStateOf(true) }

    val navigate: (String) -> Unit = { target ->
        if (target != screen) {
            backStack.add(screen)
            navForward = true
            screen = target
        }
    }

    /** 返回上一屏；栈空则回主页 */
    val goBack: () -> Unit = {
        navForward = false
        screen = backStack.removeLastOrNull() ?: ROUTE_HOME
    }
    // 雷达弹窗「去攻击」→ 跳转 WiFi 干扰页并预选目标 BSSID
    var deauthBssid by remember { mutableStateOf<String?>(null) }
    // 雷达同网弹窗动作预选:扫端口 / SSH / 踢出网络
    var portScanHost by remember { mutableStateOf<String?>(null) }
    var sshPrefill by remember { mutableStateOf<String?>(null) }
    var arpKickIp by remember { mutableStateOf<String?>(null) }
    var cctvPreselectIp by remember { mutableStateOf<String?>(null) }

    /** 雷达页入口 —— 战术仪表盘:整页滚动/垫子网格/金属探测/方位推演/遥测条/网格节点 */
    val openRadar: @Composable () -> Unit = {
        RadarScreen(
            radarVm, walkieVm, dbVm, arpVm,
            back = goBack,
            radarAlertOn = hapticsOn && radarAlertOn,
            onSelectTarget = { id -> selectedTargetId = id; navigate("target") },
            onOpenNetmap = { navigate("netmap") },
            onOpenDb = { navigate("dbview") },
            onDeauth = { t -> deauthBssid = t.id; navigate("wifi") },
            onScanPorts = { ip -> portScanHost = ip; navigate("portscan") },
            onSsh = { ip -> sshPrefill = ip; navigate("terminal") },
            onKick = { ip -> arpKickIp = ip; navigate("arp") },
            onOpenCctv = { ip -> cctvPreselectIp = ip; navigate("onvif") },
        )
    }

    BackHandler(enabled = screen != ROUTE_HOME) { goBack() }

    Box(Modifier.fillMaxSize().background(Dark)) {
        if (!disclaimerAccepted) {
            DisclaimerDialog(onAccept = {
                prefs?.edit()?.putBoolean(KEY_DISCLAIMER_ACCEPTED, true)?.apply()
                disclaimerAccepted = true
            })
        }
        if (boot) {
            BootSequence(onComplete = { boot = false })
        } else {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    // Lumia 式页面过渡:新页横向滑入(带轻微缩放视差)+ 淡入,
                    // 旧页向反方向滑出 + 快速淡出 —— 页面绝不凭空消失
                    if (navForward) {
                        (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 3 } +
                            fadeIn(tween(200)) +
                            scaleIn(initialScale = 0.97f, animationSpec = tween(260, easing = FastOutSlowInEasing)))
                            .togetherWith(
                                slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 6 } +
                                    fadeOut(tween(160)) +
                                    scaleOut(targetScale = 0.985f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                            )
                    } else {
                        (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 4 } +
                            fadeIn(tween(200)) +
                            scaleIn(initialScale = 0.97f, animationSpec = tween(260, easing = FastOutSlowInEasing)))
                            .togetherWith(
                                slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 6 } +
                                    fadeOut(tween(160)) +
                                    scaleOut(targetScale = 0.985f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                            )
                    }
                },
            ) { current ->
                when (current) {
                    ROUTE_HOME -> HomeScreen(bleVm, wifiVm, btVm, wjVm, wsVm, arpVm, httpVm, pingVm, blescanVm, portscanVm, walkieVm, radarVm, ftVm, vaultVm, revVm, payloadVm, bruteVm, cveVm, mirrorVm = mirrorVm, terminalVm = terminalVm, settingsVm = settingsVm, nav = navigate)
                    ROUTE_MIRRORING -> DashboardScreen(vm = mirrorVm, onConnectDevice = { d -> selectedMirrorDevice = d; navigate(ROUTE_REMOTE_VIEWER) }, back = goBack)
                    ROUTE_REMOTE_VIEWER -> RemoteViewerScreen(vm = mirrorVm, device = selectedMirrorDevice, back = goBack)
                    "ble" -> BleDetailScreen(bleVm, back = goBack)
                    "btjam" -> BtJamDetailScreen(btVm, back = goBack)
                    "wifi" -> WifiDetailScreen(wifiVm, radarVm, initialBssid = deauthBssid, back = goBack)
                    "wifijam" -> WifiJamDetailScreen(wjVm, back = goBack)
                    "webserver" -> WebServerDetailScreen(wsVm, back = goBack)
                    "arp" -> ArpDetailScreen(arpVm, initialIp = arpKickIp, back = goBack)
                    "http" -> HttpClientScreen(httpVm, back = goBack)
                    "ping" -> PingScreen(pingVm, back = goBack)
                    "blescan" -> BleScannerScreen(blescanVm, back = goBack)
                    "portscan" -> PortScannerScreen(portscanVm, initialHost = portScanHost, back = goBack)
                    "walkie" -> WalkieTalkieScreen(walkieVm, back = goBack, onOpenChannels = { navigate("radiochannels") })
                    "radiochannels" -> RadioChannelsScreen(back = goBack)
                    "radar" -> openRadar()
                    "target" -> selectedTargetId?.let { TargetDossierScreen(radarVm, it, back = goBack) } ?: openRadar()
                    "filetransfer" -> FileTransferScreen(ftVm, arpVm, back = goBack)
                    "vault" -> SecureVaultScreen(vaultVm, back = goBack)
                    "revshell" -> RevShellScreen(revVm, back = goBack)
                    "payload" -> PayloadScreen(payloadVm, back = goBack)
                    "brute" -> DirBruteScreen(bruteVm, back = goBack)
                    "cve" -> CVEScreen(cveVm, back = goBack)
                    "terminal" -> SshTerminalScreen(terminalVm, initialHost = sshPrefill, back = goBack, onOpenLocal = { navigate("localterm") })
                    "localterm" -> TerminalScreen(viewModel(), back = goBack)
                    "probe" -> ProbeMonitorScreen(radarVm, back = goBack)
                    "serialmon" -> SerialMonitorScreen(back = goBack)
                    "otgprobe" -> OtgProbeScreen(onOpenArp = { navigate("arp") }, onOpenSerial = { navigate("serialmon") }, back = goBack)
                    "gnss" -> GnssSkyScreen(back = goBack)
                    "pdr" -> PdrHomingScreen(back = goBack)
                    "nightlight" -> NightLightScreen(back = goBack)
                    "beacon" -> BeaconScreen(back = goBack)
                    "breadcrumbs" -> BreadcrumbsScreen(back = goBack)
                    "rations" -> RationsScreen(back = goBack)
                    "suntimes" -> SunTimesScreen(back = goBack)
                    "mains" -> MainsWireScreen(back = goBack)
                    "selfcheck" -> SelfCheckScreen(back = goBack)
                    "sonic" -> SonicLinkScreen(back = goBack)
                    "mesh" -> BleMeshScreen(back = goBack)
                    "sonar" -> SonarRangerScreen(back = goBack)
                    "chansurvey" -> ChannelSurveyScreen(back = goBack)
                    "seismo" -> SeismographScreen(back = goBack)
                    "packetmon" -> PacketMonitorScreen(back = goBack)
                    "logicscope" -> LogicScopeScreen(back = goBack)
                    "flippertv" -> FlipperTvScreen(back = goBack)
                    "netbios" -> NetBiosScreen(back = goBack)
                    "blegatt" -> BleGattScreen(radarVm, back = goBack)
                    "snmp" -> SnmpScreen(back = goBack)
                    "lantools" -> LanToolsScreen(back = goBack)
                    "nordicbench" -> NordicBenchScreen(radarVm, back = goBack)
                    "morse" -> MorseScreen(back = goBack)
                    "dbview" -> DbViewScreen(dbVm, radarVm, blescanVm, wifiVm, back = goBack)
                    "taccam" -> TacticalCameraScreen(viewModel(), operatorName = username, back = goBack, openPhotoId = openPhotoId)
                    "depth" -> DepthScanScreen(viewModel(), back = goBack, onOpenNightVision = { navigate("nightvision") })
                    "wiki" -> WikiScreen(back = goBack, initialChapter = initialChapter)
                    "sdr" -> VoiceSdrScreen(viewModel(), back = goBack)
                    "irremote" -> IrRemoteScreen(viewModel(), back = goBack, onOpenUniversal = { navigate("flippertv") })
                    "gsm" -> GsmTowerScreen(back = goBack)
                    "gsmlab" -> GsmLabScreen(back = goBack)
                    "imsi" -> ImsiDetectorScreen(back = goBack)
                    "honeypot" -> HoneypotScreen(back = goBack)
                    "appshark" -> AppSharkScreen(back = goBack)
                    "aircrack" -> AircrackScreen(back = goBack)
                    "bitchat" -> BitChatScreen(back = goBack)
                    "weaver" -> WeaverScreen(back = goBack)
                    "blueducky" -> BlueDuckyScreen(back = goBack)
                    "maoxuan" -> MaoxuanScreen(back = goBack)
                    "fsociety" -> FsocietyScreen(back = goBack, nav = navigate)
                    "nfc" -> NfcScreen(viewModel(), back = goBack)
                    "mfclassic" -> MfClassicScreen(back = goBack)
                    "subghz" -> SubGhzLabScreen(back = goBack)
                    "nightvision" -> NightVisionScreen(viewModel(), back = goBack)
                    "tacmap" -> TacticalMapScreen(viewModel(), back = goBack)
                    "netmap" -> NetMapScreen(
                        radarVm,
                        mapVm = viewModel(),
                        back = goBack,
                        onOpenDossier = { id -> selectedTargetId = id; navigate("target") },
                    )
                    "conference" -> ConferenceScreen(viewModel(), operatorName = username, back = goBack)
                    "dns" -> DnsScreen(viewModel(), back = goBack)
                    "mdns" -> MdnsScreen(viewModel(), back = goBack)
                    "tlscert" -> TlsCertScreen(viewModel(), back = goBack)
                    "whois" -> WhoisScreen(viewModel(), back = goBack)
                    "hash" -> HashScreen(viewModel(), back = goBack)
                    "qr" -> QrScreen(viewModel(), back = goBack)
                    "keygen" -> KeyGenScreen(viewModel(), back = goBack)
                    "level" -> LevelScreen(back = goBack)
                    "ai" -> AiScreen(aiVm, back = goBack)
                    "intel" -> IntelFeedScreen(viewModel(), back = goBack)
                    "wanted" -> WantedScreen(viewModel(), back = goBack)
                    "onvif" -> CameraOpsScreen(preselectIp = cctvPreselectIp, back = goBack, autoScan = initialAutoScan)
                    "rfsense" -> RfSenseScreen(radarVm, back = goBack)
                    ROUTE_ABOUT -> SettingsScreen(settingsVm, back = goBack)
                }
            }
        }
        if (!boot) {
            TacticalGrid()
            // 导航脉冲 —— 全屏主色短闪,不拦截触摸
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = navFlash.value }.background(Red))
            ScanlineOverlay(lineSpacing = 4.dp, alpha = 0.02f)
        }
        // 全局错误贴纸:任何页面做蠢事时从底部弹入
        ErrorStickerOverlay()
            BruhOverlay()
    }
}

/**
 * 全息战术网格 —— 全屏微弱方格参考线
 *
 * 全息战术终端的底纹。网格 Path 在 [drawWithCache] 缓存块内构建，
 * 尺寸变化时才重建；每帧 onDrawBehind 只执行一次 drawPath。
 */
@Composable
private fun TacticalGrid(modifier: Modifier = Modifier) {
    Canvas(
        modifier.fillMaxSize().drawWithCache {
            val step = 30.dp.toPx()
            val grid = Path()
            var x = 0f
            while (x < size.width) {
                grid.moveTo(x, 0f)
                grid.lineTo(x, size.height)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                grid.moveTo(0f, y)
                grid.lineTo(size.width, y)
                y += step
            }
            // 十字准星中心标记（极淡）并入同一 Path，保证每帧只 drawPath 一次
            val cx = size.width / 2f
            val cy = size.height / 2f
            val arm = 6.dp.toPx()
            grid.moveTo(cx - arm, cy)
            grid.lineTo(cx + arm, cy)
            grid.moveTo(cx, cy - arm)
            grid.lineTo(cx, cy + arm)
            onDrawBehind {
                drawPath(grid, Color(0xFF0E1B2E), style = Stroke(width = 0.6f))
            }
        },
    ) {}
}

// ─── 实时系统遥测 ────────────────────────────────────────────────────────────
//
// 这里显示的每一项都取自系统 API，没有任何估算或模拟：
//   下行 / 上行  设备级收发速率 —— TrafficStats 累计值相邻采样差 ÷ 真实经过时间
//   内存           ActivityManager.MemoryInfo 的可用量与总物理内存
//   网卡           ConnectivityManager 当前活跃网络的实际接口名
//
// 读不到某一项时显示 "--"，而不是编造一个数字。

private const val TELEMETRY_INTERVAL_MS = 3_000L
private const val TRAFFIC_UNSUPPORTED = -1L
private const val TELEMETRY_PLACEHOLDER = "--"
private const val KIBIBYTE = 1024L
private const val MEBIBYTE = KIBIBYTE * 1024L
private const val GIBIBYTE = MEBIBYTE * 1024L

/** 一帧系统遥测快照 */
@Immutable
private data class SysTelemetry(
    val rxBytesPerSec: Long = 0L,
    val txBytesPerSec: Long = 0L,
    val memAvailBytes: Long = 0L,
    val memTotalBytes: Long = 0L,
    val iface: String = "",
    val trafficSupported: Boolean = true,
) {
    companion object {
        val EMPTY = SysTelemetry(trafficSupported = false)

        fun formatRate(bytesPerSec: Long): String = when {
            bytesPerSec >= MEBIBYTE -> String.format(java.util.Locale.US, "%.1fMB/s", bytesPerSec / MEBIBYTE.toDouble())
            bytesPerSec >= KIBIBYTE -> String.format(java.util.Locale.US, "%.1fKB/s", bytesPerSec / KIBIBYTE.toDouble())
            else -> "${bytesPerSec}B/s"
        }

        fun formatMemory(availBytes: Long, totalBytes: Long): String =
            if (totalBytes <= 0L) TELEMETRY_PLACEHOLDER
            else String.format(java.util.Locale.US, "%.1f/%.1fGB", availBytes / GIBIBYTE.toDouble(), totalBytes / GIBIBYTE.toDouble())
    }
}

/** 取当前活跃网络的实际接口名（开启热点时通常是 ap0 / swlan0，硬编码 wlan0 在现场就是错的） */
private fun activeInterfaceName(connectivity: ConnectivityManager?): String {
    val manager = connectivity ?: return ""
    val network = manager.activeNetwork ?: return ""
    return runCatching { manager.getLinkProperties(network)?.interfaceName }.getOrNull().orEmpty()
}

/** 以固定间隔采样真实系统遥测；随调用方 Composable 生命周期自动启停 */
@Composable
private fun rememberSystemTelemetry(intervalMs: Long = TELEMETRY_INTERVAL_MS): SysTelemetry {
    val context = LocalContext.current
    val telemetry by produceState(initialValue = SysTelemetry.EMPTY, context, intervalMs) {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)

        var previousRx = TrafficStats.getTotalRxBytes()
        var previousTx = TrafficStats.getTotalTxBytes()
        var previousAt = SystemClock.elapsedRealtime()

        while (true) {
            delay(intervalMs)
            // 静态预览（Layoutlib）读不到系统遥测时跳过本帧，保留上一帧读数
            runCatching {
                val rx = TrafficStats.getTotalRxBytes()
                val tx = TrafficStats.getTotalTxBytes()
                val nowAt = SystemClock.elapsedRealtime()
                val elapsedMs = (nowAt - previousAt).coerceAtLeast(1L)

                val supported = rx != TRAFFIC_UNSUPPORTED && tx != TRAFFIC_UNSUPPORTED && previousRx != TRAFFIC_UNSUPPORTED
                val rxRate = if (supported) ((rx - previousRx).coerceAtLeast(0L) * 1000L / elapsedMs) else 0L
                val txRate = if (supported) ((tx - previousTx).coerceAtLeast(0L) * 1000L / elapsedMs) else 0L

                if (supported) {
                    previousRx = rx
                    previousTx = tx
                }
                previousAt = nowAt

                val memory = ActivityManager.MemoryInfo()
                activityManager?.getMemoryInfo(memory)

                value = SysTelemetry(
                    rxBytesPerSec = rxRate,
                    txBytesPerSec = txRate,
                    memAvailBytes = memory.availMem,
                    memTotalBytes = memory.totalMem,
                    iface = activeInterfaceName(connectivity),
                    trafficSupported = supported,
                )
            }
        }
    }
    return telemetry
}

/** 实时状态栏 —— 四格真实系统指标，数值为电光青 */
@Composable
fun StatusBar() {
    val telemetry = rememberSystemTelemetry()

    Surface(shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp), color = SurfaceBg, border = BorderStroke(1.dp, BorderDim)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TelemetryCell(stringResource(R.string.main_tel_down), if (telemetry.trafficSupported) SysTelemetry.formatRate(telemetry.rxBytesPerSec) else TELEMETRY_PLACEHOLDER)
            TelemetryDivider()
            TelemetryCell(stringResource(R.string.main_tel_up), if (telemetry.trafficSupported) SysTelemetry.formatRate(telemetry.txBytesPerSec) else TELEMETRY_PLACEHOLDER)
            TelemetryDivider()
            TelemetryCell(stringResource(R.string.main_tel_mem), SysTelemetry.formatMemory(telemetry.memAvailBytes, telemetry.memTotalBytes))
            TelemetryDivider()
            TelemetryCell(stringResource(R.string.main_tel_iface), telemetry.iface.ifEmpty { TELEMETRY_PLACEHOLDER })
        }
    }
}

/** 指挥甲板瓷砖 —— 图标 + 名称 + 可选实时徽标,按压倾斜 */
@Composable
private fun DeckTile(icon: ImageVector, label: String, badge: String?, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier.lumiaTilt().clickable {
            haptic.hud()
            onClick()
        },
        shape = CutCornerShape(topStart = 9.dp, bottomEnd = 9.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Box {
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (badge != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                    color = TacGreen.copy(alpha = 0.85f),
                ) {
                    Text(badge, fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }
        }
    }
}

/** 遥测格之间的竖分隔线 —— HUD 仪表感 */
@Composable
private fun TelemetryDivider() {
    Box(Modifier.width(1.dp).height(26.dp).background(BorderDim))
}

/** 状态栏单项 —— 电光青数值在上、中文标签在下；数值变化时快速翻牌过渡 */
@Composable
private fun TelemetryCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn(tween(140)) + slideInVertically(tween(180)) { it / 2 })
                    .togetherWith(fadeOut(tween(100)) + slideOutVertically(tween(120)) { -it / 2 })
            },
            label = "telemetry",
        ) { v ->
            Text(v, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacGreen)
        }
        Spacer(Modifier.height(1.dp))
        Text(label, fontFamily = Sans, fontSize = 9.sp, color = Gray)
    }
}

// ─── 主页模块表 ──────────────────────────────────────────────────────────────

/** 主页卡片的一项模块描述：名称 / 中文描述 / 路由 / 图标 / 运行态 / 启停动作 */
private class HomeEntry(
    val name: String,
    val desc: String,
    val route: String,
    val running: Boolean,
    val toggle: (() -> Unit)?,
    val icon: ImageVector,
)

/** 主页分区 —— 每区一个固定分组色,与队伍主题解耦:侦察青/无线红/工具绿/态势金/实验琥珀/网络紫 */
private class HomeGroup(val label: String, val color: Color, val routes: List<String>)

/** 操作员行段间分隔符 —— 暗灰中点 */
@Composable
private fun HomeSep() {
    Text(" · ", fontFamily = Mono, fontSize = 9.sp, color = GrayDark)
}

/**
 * 遥测格 —— 主页头部的大号实时读数:数值上、标签下,类别色描边。
 * 目标/高危/友方/运行中四格并排,打开主页第一眼就是态势。
 */
@Composable
private fun TelemetryCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = CutCornerShape(topStart = 9.dp, bottomEnd = 9.dp),
        color = color.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontFamily = Mono, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(1.dp))
            Text(label, fontFamily = Mono, fontSize = 7.5.sp, color = Gray)
        }
    }
}

/**
 * 全息核心 —— 主页英雄区动态多环核心
 *
 * 层次(自外向内):脉冲外辉光 → 涟漪扩散环 → 60 刻度慢旋环 →
 * 反向虚线内环 → 中心菱形(径向辉光 + 金色本体 + 白核)。
 *
 * 性能:全部动画由单个 withFrameNanos 帧循环驱动,相位只写进
 * mutableFloatStateOf 并在 draw 阶段读取 —— 每帧只重绘不重组,
 * 不再用 4 个 InfiniteTransition 每帧重组整个核心。
 */
@Composable
private fun HoloCore(size: Dp, modifier: Modifier = Modifier) {
    var phaseMs by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) { last = now; return@withFrameNanos }
                val dtMs = (now - last) / 1_000_000f
                last = now
                phaseMs = (phaseMs + dtMs) % 7_200_000f
            }
        }
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f
            val tSec = phaseMs / 1000f

            // 底层:脉冲外辉光 + 涟漪 + 静态外环
            val glowPulse = (0.5f + 0.5f * sin(tSec * 2.0 * PI / 1.2f)).toFloat()
            drawCircle(Red.copy(alpha = 0.08f * (0.45f + 0.55f * glowPulse)), r * 0.965f, c, style = Stroke(7.dp.toPx()))
            val ripple = (tSec % 2.3f) / 2.3f
            val pr = 0.22f + ripple * 0.74f
            drawCircle(Red.copy(alpha = (1f - ripple) * 0.45f), r * pr, c, style = Stroke(1.1.dp.toPx()))
            drawCircle(Red.copy(alpha = 0.30f), r * 0.94f, c, style = Stroke(1.dp.toPx()))

            // 60 刻度环:每 30° 一主刻度,16s/圈慢速顺时针
            rotate((tSec / 16f) * 360f, pivot = c) {
                for (i in 0 until 60) {
                    val rad = Math.toRadians(i * 6.0)
                    val major = i % 5 == 0
                    val inner = r * (if (major) 0.80f else 0.855f)
                    drawLine(
                        Red.copy(alpha = if (major) 0.55f else 0.20f),
                        Offset(c.x + inner * cos(rad).toFloat(), c.y + inner * sin(rad).toFloat()),
                        Offset(c.x + r * 0.93f * cos(rad).toFloat(), c.y + r * 0.93f * sin(rad).toFloat()),
                        if (major) 1.3.dp.toPx() else 0.6.dp.toPx(),
                    )
                }
            }

            // 反向旋转虚线内环(9s/圈)
            rotate(-(tSec / 9f) * 360f, pivot = c) {
                drawCircle(
                    Gold.copy(alpha = 0.55f),
                    r * 0.70f,
                    c,
                    style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f))),
                )
            }

            // 中心菱形径向辉光底
            drawCircle(
                Brush.radialGradient(listOf(Red.copy(alpha = 0.32f), Color.Transparent), center = c, radius = this.size.minDimension / 2f),
                radius = this.size.minDimension / 2f,
                center = c,
            )
        }
        Star(Gold, size * 0.40f)
        Star(White, size * 0.14f)
    }
}

/**
 * 主页全息扫描掠光 —— 每 4.2 秒一道主色光带自上而下扫过整页,
 * 像雷达对战术地图做一次全屏扫描。纯绘制层,不拦截触摸。
 * 帧循环驱动(draw 阶段读相位),零逐帧重组。
 */
@Composable
private fun HomeSweep(modifier: Modifier = Modifier) {
    var phaseMs by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) { last = now; return@withFrameNanos }
                val dtMs = (now - last) / 1_000_000f
                last = now
                phaseMs = (phaseMs + dtMs) % 4_200f
            }
        }
    }
    Canvas(modifier) {
        val y = phaseMs / 4_200f * size.height
        val bandH = 130.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Red.copy(alpha = 0.05f), Color.Transparent),
                startY = y - bandH / 2f,
                endY = y + bandH / 2f,
            ),
            topLeft = Offset(0f, y - bandH / 2f),
            size = Size(size.width, bandH),
        )
        // 前缘亮线 + 后缘拖影
        drawLine(Red.copy(alpha = 0.14f), Offset(0f, y), Offset(size.width, y), 1.6.dp.toPx())
        drawLine(Red.copy(alpha = 0.05f), Offset(0f, y - 8.dp.toPx()), Offset(size.width, y - 8.dp.toPx()), 1.dp.toPx())
    }
}

/** 卡片入场级联的步长与级数上限（240ms 内完成首屏级联，不阻塞阅读） */
private const val CASCADE_STEP_MS = 40L
private const val CASCADE_MAX_STEPS = 6

/**
 * 主屏幕 / 功能入口页
 *
 * ## 信息层次（自上而下）
 * 1. 全息设备核心 + 「哪吒网络安全终端」大标题 + 全息终端标识
 * 2. [StatusBar] —— 真实系统遥测
 * 3. **运行中** 分区 —— 仅在有模块运行时出现，当前状态第一眼可见
 * 4. 功能分区：侦察 / 网络 / 无线 / 工具（每张卡片带专属图标）
 * 5. 页脚
 */
@Composable
fun HomeScreen(
    bleVm: BleSpamViewModel, wifiVm: WifiDisruptViewModel,
    btVm: BluetoothJammerViewModel, wjVm: WifiJammerViewModel,
    wsVm: WebServerViewModel, arpVm: ArpScanViewModel,
    httpVm: HttpClientViewModel, pingVm: PingViewModel,
    blescanVm: BleScannerViewModel, portscanVm: PortScannerViewModel,
    walkieVm: WalkieTalkieViewModel, radarVm: RadarViewModel,
    ftVm: FileTransferViewModel, vaultVm: SecureMediaViewModel,
    revVm: RevShellViewModel, payloadVm: PayloadViewModel,
    bruteVm: DirBruteViewModel, cveVm: CVESearchViewModel,
    mirrorVm: MirroringViewModel,
    terminalVm: SshClientViewModel,
    settingsVm: SettingsViewModel,
    nav: (String) -> Unit,
) {
    val br by bleVm.isRunning.collectAsState()
    val wr by wifiVm.isFlooding.collectAsState()
    val btr by btVm.isRunning.collectAsState()
    val wjr by wjVm.isRunning.collectAsState()
    val wsr by wsVm.isRunning.collectAsState()
    val arpr by arpVm.isRunning.collectAsState()
    val httpr by httpVm.isRunning.collectAsState()
    val pingr by pingVm.isRunning.collectAsState()
    val blesr by blescanVm.isRunning.collectAsState()
    val portr by portscanVm.isRunning.collectAsState()
    val walkier by walkieVm.isRunning.collectAsState()
    val radarr by radarVm.isRunning.collectAsState()
    val ftr by ftVm.isRunning.collectAsState()
    val revr by revVm.isRunning.collectAsState()
    val bruter by bruteVm.isRunning.collectAsState()
    val cver by cveVm.isRunning.collectAsState()
    val terminalr by terminalVm.status.collectAsState()
    val sshOnline = terminalr is SshClientManager.Status.Connected
    val mirrorState by mirrorVm.state.collectAsState()
    val mirrorr = mirrorState is MirroringState.HostLive
    // 雷达目标表每 500ms 轮询一次 —— 主页只做态势读数,2s 采样防整页被高频重组拖卡
    @OptIn(FlowPreview::class)
    val radarTargets by produceState(initialValue = emptyList<RadarTarget>()) {
        radarVm.targets.sample(2000).collect { value = it }
    }
    // 实时事件行:雷达最近一次新目标探测
    val contact by radarVm.contact.collectAsState()
    var lastContact by remember { mutableStateOf<RadarContactEvent?>(null) }
    LaunchedEffect(contact) { contact?.let { lastContact = it } }

    // 操作员身份 —— 设置页可改呼号与队伍，头部行实时跟随
    val username by settingsVm.username.collectAsState()
    val teamId by settingsVm.teamId.collectAsState()
    val squad = TEAM_THEMES.firstOrNull { it.id == teamId } ?: TEAM_THEMES.first()

    // 每次重组都重建：启停闭包捕获的是当帧运行态，缓存成 remember 会读到过期状态
    val entries = listOf(
        HomeEntry(stringResource(R.string.home_arp_name), stringResource(R.string.home_arp_desc), "arp", arpr, { arpVm.scan() }, Icons.Outlined.Router),
        HomeEntry(stringResource(R.string.home_blescan_name), stringResource(R.string.home_blescan_desc), "blescan", blesr, { if (blesr) blescanVm.stop() else blescanVm.start() }, Icons.Outlined.BluetoothSearching),
        HomeEntry(stringResource(R.string.home_portscan_name), stringResource(R.string.home_portscan_desc), "portscan", portr, { if (portr) portscanVm.stop() else portscanVm.start() }, Icons.Outlined.GridView),
        HomeEntry(stringResource(R.string.home_radar_name), stringResource(R.string.home_radar_desc), "radar", radarr, { if (radarr) radarVm.stop() else radarVm.start() }, Icons.Outlined.Radar),
        HomeEntry(stringResource(R.string.home_netmap_name), stringResource(R.string.home_netmap_desc), "netmap", false, null, Icons.Outlined.TravelExplore),
        HomeEntry(stringResource(R.string.home_cve_name), stringResource(R.string.home_cve_desc), "cve", cver, null, Icons.Outlined.Search),
        HomeEntry(stringResource(R.string.home_intel_name), stringResource(R.string.home_intel_desc), "intel", false, null, Icons.Outlined.Notifications),
        HomeEntry(stringResource(R.string.home_wanted_name), stringResource(R.string.home_wanted_desc), "wanted", false, null, Icons.Outlined.Gavel),
        HomeEntry(stringResource(R.string.home_onvif_name), stringResource(R.string.home_onvif_desc), "onvif", false, null, Icons.Outlined.CameraAlt),
        HomeEntry(stringResource(R.string.home_rfsense_name), stringResource(R.string.home_rfsense_desc), "rfsense", false, null, Icons.Outlined.Sensors),
        HomeEntry(stringResource(R.string.home_sdr_name), stringResource(R.string.home_sdr_desc), "sdr", false, null, Icons.Outlined.Equalizer),
        HomeEntry(stringResource(R.string.home_taccam_name), stringResource(R.string.home_taccam_desc), "taccam", false, null, Icons.Outlined.CameraAlt),
        HomeEntry(stringResource(R.string.home_depth_name), stringResource(R.string.home_depth_desc), "depth", false, null, Icons.Outlined.Visibility),
        HomeEntry(stringResource(R.string.home_wiki_name), stringResource(R.string.home_wiki_desc), "wiki", false, null, Icons.Outlined.MenuBook),
        HomeEntry(stringResource(R.string.home_gsm_name), stringResource(R.string.home_gsm_desc), "gsm", false, null, Icons.Outlined.Sensors),
        HomeEntry(stringResource(R.string.home_gsmlab_name), stringResource(R.string.home_gsmlab_desc), "gsmlab", false, null, Icons.Outlined.SettingsInputAntenna),
        HomeEntry(stringResource(R.string.home_imsi_name), stringResource(R.string.home_imsi_desc), "imsi", false, null, Icons.Outlined.SimCard),
        HomeEntry(stringResource(R.string.home_honeypot_name), stringResource(R.string.home_honeypot_desc), "honeypot", false, null, Icons.Outlined.BugReport),
        HomeEntry(stringResource(R.string.home_appshark_name), stringResource(R.string.home_appshark_desc), "appshark", false, null, Icons.Outlined.Security),
        HomeEntry(stringResource(R.string.home_aircrack_name), stringResource(R.string.home_aircrack_desc), "aircrack", false, null, Icons.Outlined.WifiTethering),
        HomeEntry(stringResource(R.string.home_bitchat_name), stringResource(R.string.home_bitchat_desc), "bitchat", false, null, Icons.Outlined.Forum),
        HomeEntry(stringResource(R.string.home_weaver_name), stringResource(R.string.home_weaver_desc), "weaver", false, null, Icons.Outlined.AutoStories),
        HomeEntry(stringResource(R.string.home_blueducky_name), stringResource(R.string.home_blueducky_desc), "blueducky", false, null, Icons.Outlined.PhonelinkSetup),
        HomeEntry(stringResource(R.string.home_maoxuan_name), stringResource(R.string.home_maoxuan_desc), "maoxuan", false, null, Icons.Outlined.HistoryEdu),
        HomeEntry(stringResource(R.string.home_fsociety_name), stringResource(R.string.home_fsociety_desc), "fsociety", false, null, Icons.Outlined.Dangerous),
        HomeEntry(stringResource(R.string.home_probe_name), stringResource(R.string.home_probe_desc), "probe", false, null, Icons.Outlined.Router),
        HomeEntry(stringResource(R.string.home_nightvision_name), stringResource(R.string.home_nightvision_desc), "nightvision", false, null, Icons.Outlined.Nightlight),
        HomeEntry(stringResource(R.string.home_tacmap_name), stringResource(R.string.home_tacmap_desc), "tacmap", false, null, Icons.Outlined.Map),
        HomeEntry(stringResource(R.string.home_irremote_name), stringResource(R.string.home_irremote_desc), "irremote", false, null, Icons.Outlined.Sensors),
        HomeEntry(stringResource(R.string.home_nfc_name), stringResource(R.string.home_nfc_desc), "nfc", false, null, Icons.Outlined.Contactless),
        HomeEntry(stringResource(R.string.home_mfclassic_name), stringResource(R.string.home_mfclassic_desc), "mfclassic", false, null, Icons.Outlined.Memory),
        HomeEntry(stringResource(R.string.home_subghz_name), stringResource(R.string.home_subghz_desc), "subghz", false, null, Icons.Outlined.CellTower),

        HomeEntry(stringResource(R.string.home_webserver_name), stringResource(R.string.home_webserver_desc), "webserver", wsr, { if (wsr) wsVm.stopServer() else wsVm.startServer() }, Icons.Outlined.Language),
        HomeEntry(stringResource(R.string.home_http_name), stringResource(R.string.home_http_desc), "http", httpr, null, Icons.Outlined.Http),
        HomeEntry(stringResource(R.string.home_brute_name), stringResource(R.string.home_brute_desc), "brute", bruter, { if (bruter) bruteVm.stop() else bruteVm.start() }, Icons.Outlined.ManageSearch),
        HomeEntry(stringResource(R.string.home_ping_name), stringResource(R.string.home_ping_desc), "ping", pingr, { if (pingr) pingVm.stop() else pingVm.start() }, Icons.Outlined.NetworkPing),

        HomeEntry(stringResource(R.string.home_ble_name), stringResource(R.string.home_ble_desc), "ble", br, { if (br) bleVm.stopSpam() else bleVm.startSpam() }, Icons.Outlined.Bluetooth),
        HomeEntry(stringResource(R.string.home_wifi_name), stringResource(R.string.home_wifi_desc), "wifi", wr, { if (wr) wifiVm.stopFlood() else wifiVm.scanNetworks() }, Icons.Outlined.Wifi),
        HomeEntry(stringResource(R.string.home_btjam_name), stringResource(R.string.home_btjam_desc), "btjam", btr, { if (btr) btVm.stop() else btVm.start() }, Icons.Outlined.BluetoothDisabled),
        HomeEntry(stringResource(R.string.home_wifijam_name), stringResource(R.string.home_wifijam_desc), "wifijam", wjr, { if (wjr) wjVm.stop() else wjVm.start() }, Icons.Outlined.WifiOff),

        HomeEntry(stringResource(R.string.home_walkie_name), stringResource(R.string.home_walkie_desc), "walkie", walkier, { if (walkier) walkieVm.stop() else walkieVm.start() }, Icons.Outlined.Mic),
        HomeEntry(stringResource(R.string.home_radiochannels_name), stringResource(R.string.home_radiochannels_desc), "radiochannels", false, null, Icons.Outlined.Tune),
        HomeEntry(stringResource(R.string.home_filetransfer_name), stringResource(R.string.home_filetransfer_desc), "filetransfer", ftr, { if (ftr) ftVm.stop() else ftVm.start() }, Icons.Outlined.SwapHoriz),
        HomeEntry(stringResource(R.string.home_mirroring_name), stringResource(R.string.home_mirroring_desc), "mirroring", mirrorr, null, Icons.Outlined.Cast),
        HomeEntry(stringResource(R.string.home_vault_name), stringResource(R.string.home_vault_desc), "vault", false, null, Icons.Outlined.Lock),
        HomeEntry(stringResource(R.string.home_payload_name), stringResource(R.string.home_payload_desc), "payload", false, null, Icons.Outlined.Terminal),
        HomeEntry(stringResource(R.string.home_revshell_name), stringResource(R.string.home_revshell_desc), "revshell", revr, { if (revr) revVm.stop() else revVm.start() }, Icons.Outlined.Lan),
        HomeEntry(stringResource(R.string.home_terminal_name), stringResource(R.string.home_terminal_desc), "terminal", sshOnline, null, Icons.Outlined.Terminal),
        HomeEntry(stringResource(R.string.home_localterm_name), stringResource(R.string.home_localterm_desc), "localterm", false, null, Icons.Outlined.Terminal),
        HomeEntry(stringResource(R.string.home_morse_name), stringResource(R.string.home_morse_desc), "morse", false, null, Icons.Outlined.Lan),
        HomeEntry(stringResource(R.string.home_serialmon_name), stringResource(R.string.home_serialmon_desc), "serialmon", false, null, Icons.Outlined.Cast),
        HomeEntry(stringResource(R.string.home_otgprobe_name), stringResource(R.string.home_otgprobe_desc), "otgprobe", false, null, Icons.Outlined.SwapHoriz),
        HomeEntry(stringResource(R.string.home_gnss_name), stringResource(R.string.home_gnss_desc), "gnss", false, null, Icons.Outlined.TravelExplore),
        HomeEntry(stringResource(R.string.home_pdr_name), stringResource(R.string.home_pdr_desc), "pdr", false, null, Icons.Outlined.Navigation),
        HomeEntry(stringResource(R.string.home_nightlight_name), stringResource(R.string.home_nightlight_desc), "nightlight", false, null, Icons.Outlined.Nightlight),
        HomeEntry(stringResource(R.string.home_beacon_name), stringResource(R.string.home_beacon_desc), "beacon", false, null, Icons.Outlined.Campaign),
        HomeEntry(stringResource(R.string.home_breadcrumbs_name), stringResource(R.string.home_breadcrumbs_desc), "breadcrumbs", false, null, Icons.Outlined.Route),
        HomeEntry(stringResource(R.string.home_rations_name), stringResource(R.string.home_rations_desc), "rations", false, null, Icons.Outlined.Restaurant),
        HomeEntry(stringResource(R.string.home_suntimes_name), stringResource(R.string.home_suntimes_desc), "suntimes", false, null, Icons.Outlined.WbSunny),
        HomeEntry(stringResource(R.string.home_mains_name), stringResource(R.string.home_mains_desc), "mains", false, null, Icons.Outlined.Sensors),
        HomeEntry(stringResource(R.string.home_selfcheck_name), stringResource(R.string.home_selfcheck_desc), "selfcheck", false, null, Icons.Outlined.Storage),
        HomeEntry(stringResource(R.string.home_sonic_name), stringResource(R.string.home_sonic_desc), "sonic", false, null, Icons.Outlined.Equalizer),
        HomeEntry(stringResource(R.string.home_mesh_name), stringResource(R.string.home_mesh_desc), "mesh", false, null, Icons.Outlined.Hub),
        HomeEntry(stringResource(R.string.home_sonar_name), stringResource(R.string.home_sonar_desc), "sonar", false, null, Icons.Outlined.GraphicEq),
        HomeEntry(stringResource(R.string.home_chansurvey_name), stringResource(R.string.home_chansurvey_desc), "chansurvey", false, null, Icons.Outlined.Wifi),
        HomeEntry(stringResource(R.string.home_seismo_name), stringResource(R.string.home_seismo_desc), "seismo", false, null, Icons.Outlined.ShowChart),
        HomeEntry(stringResource(R.string.home_packetmon_name), stringResource(R.string.home_packetmon_desc), "packetmon", false, null, Icons.Outlined.Router),
        HomeEntry(stringResource(R.string.home_logicscope_name), stringResource(R.string.home_logicscope_desc), "logicscope", false, null, Icons.Outlined.GraphicEq),
        HomeEntry(stringResource(R.string.home_flippertv_name), stringResource(R.string.home_flippertv_desc), "flippertv", false, null, Icons.Outlined.Sensors),
        HomeEntry(stringResource(R.string.home_netbios_name), stringResource(R.string.home_netbios_desc), "netbios", false, null, Icons.Outlined.Router),
        HomeEntry(stringResource(R.string.home_blegatt_name), stringResource(R.string.home_blegatt_desc), "blegatt", false, null, Icons.Outlined.Bluetooth),
        HomeEntry(stringResource(R.string.home_snmp_name), stringResource(R.string.home_snmp_desc), "snmp", false, null, Icons.Outlined.Lan),
        HomeEntry(stringResource(R.string.home_lantools_name), stringResource(R.string.home_lantools_desc), "lantools", false, null, Icons.Outlined.SwapHoriz),
        HomeEntry(stringResource(R.string.home_nordicbench_name), stringResource(R.string.home_nordicbench_desc), "nordicbench", false, null, Icons.Outlined.Bluetooth),
        HomeEntry(stringResource(R.string.home_dbview_name), stringResource(R.string.home_dbview_desc), "dbview", false, null, Icons.Outlined.Storage),
        HomeEntry(stringResource(R.string.home_conference_name), stringResource(R.string.home_conference_desc), "conference", false, null, Icons.Outlined.VideoCall),

        // ── 新战术工具 ─────────────────────────────────────────
        HomeEntry(stringResource(R.string.home_dns_name), stringResource(R.string.home_dns_desc), "dns", false, null, Icons.Outlined.Dns),
        HomeEntry(stringResource(R.string.home_mdns_name), stringResource(R.string.home_mdns_desc), "mdns", false, null, Icons.Outlined.DevicesOther),
        HomeEntry(stringResource(R.string.home_tlscert_name), stringResource(R.string.home_tlscert_desc), "tlscert", false, null, Icons.Outlined.VerifiedUser),
        HomeEntry(stringResource(R.string.home_whois_name), stringResource(R.string.home_whois_desc), "whois", false, null, Icons.Outlined.Badge),
        HomeEntry(stringResource(R.string.home_hash_name), stringResource(R.string.home_hash_desc), "hash", false, null, Icons.Outlined.Fingerprint),
        HomeEntry(stringResource(R.string.home_qr_name), stringResource(R.string.home_qr_desc), "qr", false, null, Icons.Outlined.QrCode2),
        HomeEntry(stringResource(R.string.home_keygen_name), stringResource(R.string.home_keygen_desc), "keygen", false, null, Icons.Outlined.Key),
        HomeEntry(stringResource(R.string.home_level_name), stringResource(R.string.home_level_desc), "level", false, null, Icons.Outlined.Straighten),
        HomeEntry(stringResource(R.string.home_ai_name), stringResource(R.string.home_ai_desc), "ai", false, null, Icons.Outlined.SmartToy),
    )

    val groups = listOf(
        HomeGroup(stringResource(R.string.home_group_recon), Color(0xFF00E5FF), listOf("arp", "blescan", "portscan", "radar", "probe", "netmap", "netbios", "snmp", "cve", "intel", "wanted", "onvif", "rfsense", "sdr", "taccam", "depth", "gsm", "gsmlab", "mdns", "weaver")),
        HomeGroup(stringResource(R.string.home_group_wireless), Color(0xFFFF4D5E), listOf("ble", "wifi", "btjam", "wifijam", "irremote", "nfc", "blegatt", "nordicbench", "aircrack", "bitchat", "blueducky")),
        HomeGroup(stringResource(R.string.home_group_tools), Color(0xFF00E676), listOf("walkie", "radiochannels", "filetransfer", "mirroring", "conference", "nightvision", "tacmap", "vault", "payload", "revshell", "terminal", "localterm", "morse", "serialmon", "otgprobe", "dbview", "hash", "qr", "keygen", "ai", "wiki", "appshark", "maoxuan", "fsociety")),
        HomeGroup(stringResource(R.string.home_group_situational), Gold, listOf("gnss", "pdr", "nightlight", "beacon", "breadcrumbs", "rations", "suntimes", "mains", "sonic", "selfcheck", "level", "imsi")),
        HomeGroup(stringResource(R.string.home_group_lab), Warn, listOf("mesh", "sonar", "chansurvey", "seismo", "logicscope", "flippertv", "lantools", "mfclassic", "subghz")),
        HomeGroup(stringResource(R.string.home_group_network), Color(0xFFB388FF), listOf("webserver", "http", "brute", "ping", "packetmon", "dns", "whois", "tlscert", "honeypot")),
    )

    val entriesByRoute = entries.associateBy { it.route }
    val activeEntries = entries.filter { it.running }
    val haptic = LocalHapticFeedback.current

    // 共享呼吸时钟:所有运行卡片共用一条相位,不再每卡一个 InfiniteTransition(帧回调数 = 运行模块数)
    val sharedBreath: State<Float>? = if (activeEntries.isNotEmpty()) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        )
    } else null
    // 入场动画只在本次会话首屏播一次;滚动回来/遥测刷新不再重放整排卡片动画
    var entranceArmed by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(900); entranceArmed = false }

    // 收藏功能:☆ 钉住的模块置顶显示,持久化在 deauthctrl_prefs
    val context = LocalContext.current
    val favPrefs = remember { context.getSharedPreferences("deauthctrl_prefs", 0) }
    val favRoutes = remember { mutableStateOf(favPrefs.getStringSet("fav_routes", emptySet())?.toMutableSet() ?: mutableSetOf()) }
    fun toggleFav(route: String) {
        val cur = favRoutes.value
        if (route in cur) cur.remove(route) else cur.add(route)
        favPrefs.edit().putStringSet("fav_routes", cur).apply()
        favRoutes.value = cur
    }

    // 主页用 LazyVerticalGrid 而非 LazyColumn：60 个模块以 2 列网格懒加载，
    // 滚动与遥测刷新时只组合可见项，避免整页重组导致的卡顿
    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ─── 全息核心头部（跨两列）:核心 + 标题并排,下方遥测带与实时事件行 ──
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HoloCore(88.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        // 静态金白渐变标题(流光动画改为静态渐变:流光状态每帧更新会导致标题逐帧重组)
                        Text(
                            stringResource(R.string.app_name),
                            fontFamily = Sans,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            style = TextStyle(
                                brush = Brush.linearGradient(
                                    colors = listOf(Gold, White, Gold),
                                    start = Offset(0f, 0f),
                                    end = Offset(600f, 0f),
                                ),
                            ),
                        )
                        Spacer(Modifier.height(2.dp))
                        GlitchText(stringResource(R.string.main_home_glitch), glitchIntervalMs = 3200, style = TextStyle(fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                        Spacer(Modifier.height(4.dp))
                        // 操作员行分段着色:呼号金色、队伍主色、加密信道电光青 —— 信息一眼分层
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.main_home_operator_prefix), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                            Text(username, fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
                            HomeSep()
                            Text(squad.teamName, fontFamily = Mono, fontSize = 8.sp, color = squad.primary)
                            HomeSep()
                            Text(stringResource(R.string.main_home_encryption), fontFamily = Mono, fontSize = 8.sp, color = TacGreen)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // ── 遥测带:四大实时读数 ──
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TelemetryCell(stringResource(R.string.main_tel_targets), "${radarTargets.size}", TacGreen, Modifier.weight(1f))
                    TelemetryCell(stringResource(R.string.main_tel_high), "${radarTargets.count { radarThreatTier(it).key == "high" }}", Danger, Modifier.weight(1f))
                    TelemetryCell(stringResource(R.string.main_tel_friendly), "${radarTargets.count { it.bonded }}", FRIENDLY_BLIP, Modifier.weight(1f))
                    TelemetryCell(stringResource(R.string.main_tel_running), "${activeEntries.size}", Gold, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                // ── 实时事件行:最新雷达新目标 + 快捷入口 ──
                Surface(
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, BorderDim),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        PulseStar(active = radarr, size = 6.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            lastContact?.let { stringResource(R.string.main_tel_contact, it.count, it.names.take(2).joinToString(" / "), SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(it.timeMs))) }
                                ?: stringResource(R.string.main_tel_idle),
                            fontFamily = Mono, fontSize = 8.sp,
                            color = if (lastContact != null) TacGreen else Gray,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(stringResource(R.string.main_sitrep_radar), fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.clickable { nav("radar") }.padding(horizontal = 6.dp, vertical = 3.dp))
                        Text(stringResource(R.string.main_sitrep_netmap), fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.clickable { nav("netmap") }.padding(horizontal = 6.dp, vertical = 3.dp))
                        Text(stringResource(R.string.main_sitrep_intel), fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.clickable { nav("intel") }.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        // ─── 指挥甲板:四大核心快捷瓷砖(图标 + 实时徽标) ───
        item(key = "deck", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DeckTile(Icons.Outlined.Radar, stringResource(R.string.home_radar_name), badge = if (radarr) "${radarTargets.size}" else null, color = TacGreen, modifier = Modifier.weight(1f), onClick = { nav("radar") })
                DeckTile(Icons.Outlined.TravelExplore, stringResource(R.string.home_netmap_name), badge = null, color = Color(0xFF00E5FF), modifier = Modifier.weight(1f), onClick = { nav("netmap") })
                DeckTile(Icons.Outlined.Notifications, stringResource(R.string.home_intel_name), badge = null, color = Color(0xFFB388FF), modifier = Modifier.weight(1f), onClick = { nav("intel") })
                DeckTile(Icons.Outlined.Settings, stringResource(R.string.main_settings_about), badge = null, color = Gold, modifier = Modifier.weight(1f), onClick = { nav("about") })
            }
        }
        item(key = "deck_gap", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }

        item(key = "status", span = { GridItemSpan(maxLineSpan) }) { StatusBar() }
        item(key = "status_gap", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }

        // ─── 收藏分区:☆ 钉住的模块置顶显示 ───
        val favEntries = favRoutes.value.mapNotNull { entriesByRoute[it] }
        if (favEntries.isNotEmpty()) {
            item(key = "fav_header", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.main_fav_section), Gold) }
            favEntries.forEachIndexed { index, entry ->
                item(key = "fav_" + entry.route) {
                    HomeGridCard(
                        name = entry.name,
                        desc = entry.desc,
                        running = entry.running,
                        icon = entry.icon,
                        groupColor = Gold,
                        favorite = true,
                        onToggleFavorite = { haptic.hud(); toggleFav(entry.route) },
                        delayMs = index.coerceAtMost(CASCADE_MAX_STEPS) * CASCADE_STEP_MS,
                        animate = entranceArmed,
                        breathAlpha = sharedBreath,
                        onClick = { nav(entry.route) },
                        onToggle = entry.toggle,
                    )
                }
            }
            item(key = "fav_gap", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(4.dp)) }
        }

        if (activeEntries.isNotEmpty()) {
            item(key = "running_header", span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    PulseStar(active = true, size = 7.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.main_running_header, activeEntries.size), fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacGreen)
                }
            }
            items(
                items = activeEntries,
                key = { "running_" + it.route },
                span = { GridItemSpan(maxLineSpan) },
            ) { entry ->
                ModuleCard(entry.name, entry.desc, entry.running, entry.icon, delayMs = 0L, onClick = { nav(entry.route) }, onToggle = entry.toggle, sharedBreath = sharedBreath)
            }
            item(key = "running_gap", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(2.dp)) }
        }

        groups.forEach { group ->
            item(key = "label_" + group.label, span = { GridItemSpan(maxLineSpan) }) { SectionLabel(group.label, group.color, count = "${group.routes.mapNotNull { entriesByRoute[it] }.size}") }
            group.routes.mapNotNull { entriesByRoute[it] }.forEachIndexed { index, entry ->
                item(key = "card_" + entry.route) {
                    HomeGridCard(
                        name = entry.name,
                        desc = entry.desc,
                        running = entry.running,
                        icon = entry.icon,
                        groupColor = group.color,
                        favorite = entry.route in favRoutes.value,
                        onToggleFavorite = { haptic.hud(); toggleFav(entry.route) },
                        delayMs = index.coerceAtMost(CASCADE_MAX_STEPS) * CASCADE_STEP_MS,
                        animate = entranceArmed,
                        breathAlpha = sharedBreath,
                        onClick = { nav(entry.route) },
                        onToggle = entry.toggle,
                    )
                }
            }
            item(key = "group_gap_" + group.label, span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(4.dp)) }
        }

        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Star(Gold, 7.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.main_footer_red_hacker), fontFamily = Sans, fontSize = 11.sp, color = Gray)
                    Spacer(Modifier.width(10.dp))
                    Star(Gold, 7.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.main_footer_homegrown), fontFamily = Sans, fontSize = 11.sp, color = Gray)
                    Spacer(Modifier.width(10.dp))
                    Star(Gold, 7.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.main_footer_secure), fontFamily = Sans, fontSize = 11.sp, color = Gray)
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.padding(bottom = 4.dp),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Red.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.45f)),
                ) {
                    Text(
                        stringResource(R.string.main_settings_about),
                        fontFamily = Sans,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Red,
                        modifier = Modifier
                            .clickable {
                                haptic.hud()
                                nav("about")
                            }
                            .padding(horizontal = 26.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
        // 全息扫描掠光 —— 主色光带周期性扫过主页,纯绘制层不拦截触摸
        HomeSweep(Modifier.matchParentSize())
    }
}

// ─── 主页网格卡片 ────────────────────────────────────────────────────────────

/**
 * 主页 2 列网格的竖版模块卡
 *
 * 结构：顶部 30dp 图标 → 名称（运行中带脉冲菱形）→ 单行描述 → 全宽按钮条。
 * 分组色：左缘 3dp 色轨 + 图标框 + 「进入」字色,分区一眼可辨;
 * 运行态：电光青呼吸边框 + 四角锁定框；呼吸相位只在绘制阶段读取，不产生逐帧重组。
 * 入场：delayMs 延迟后 fade + 上滑，保留原有级联节奏。
 */
@Composable
private fun HomeGridCard(
    name: String,
    desc: String,
    running: Boolean,
    icon: ImageVector,
    groupColor: Color = TacGreen,
    delayMs: Long = 0L,
    onClick: () -> Unit,
    onToggle: (() -> Unit)? = null,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    animate: Boolean = true,
    breathAlpha: State<Float>? = null,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (delayMs > 0 && animate) delay(delayMs); visible = true }

    val haptic = LocalHapticFeedback.current
    val cardInteraction = remember { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(if (cardPressed) 0.965f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f))
    val actionInteraction = remember { MutableInteractionSource() }
    val actionPressed by actionInteraction.collectIsPressedAsState()
    val actionScale by animateFloatAsState(if (actionPressed) 0.93f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f))

    // 运行态的呼吸节奏 —— 优先用主页共享相位,单卡场景才自建;只在绘制块读 value
    val breathAlpha: State<Float>? = breathAlpha ?: if (running) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        )
    } else null

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(320)) + slideInVertically(tween(380)) { it / 5 }) {
        val bg by animateColorAsState(if (running) TacGreen.copy(alpha = 0.05f) else SurfaceBg, tween(350))
        val iconTint by animateColorAsState(if (running) TacGreen else Gray, tween(350))
        val descColor by animateColorAsState(if (running) TacGreenDim else Gray, tween(350))
        val cardShape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
        val barShape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .scale(cardScale)
                .lumiaTilt()
                .clickable(interactionSource = cardInteraction, indication = LocalIndication.current) {
                    haptic.hud()
                    onClick()
                },
            shape = cardShape,
            color = bg,
            border = if (running) null else BorderStroke(1.dp, BorderDim),
        ) {
            Box {
                // 收藏星标:右上角独立点击,不影响卡片本体(★ 已钉住 / ☆ 未收藏)
                if (onToggleFavorite != null) {
                    Text(
                        if (favorite) "★" else "☆",
                        fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (favorite) Gold else Gray.copy(alpha = 0.4f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clickable { onToggleFavorite() }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
                // 分组色左轨(运行态转电光青)
                Box(
                    Modifier.matchParentSize().drawWithCache {
                        val w = 3.dp.toPx()
                        onDrawBehind {
                            drawRect(if (running) TacGreen.copy(alpha = 0.9f) else groupColor.copy(alpha = 0.55f), size = Size(w, size.height))
                        }
                    },
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp)) {
                    // 模块图标:斜切角底板容器,分组色描边,运行态底板泛青 —— 52 张卡视觉统一成战术模块阵列
                    Surface(
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = if (running) TacGreen.copy(alpha = 0.10f) else groupColor.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, if (running) TacGreen.copy(alpha = 0.5f) else groupColor.copy(alpha = 0.4f)),
                    ) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = name, tint = iconTint, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            fontFamily = Sans,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (running) {
                            Spacer(Modifier.width(5.dp))
                            PulseStar(active = true, size = 5.dp)
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        desc,
                        fontFamily = Sans,
                        fontSize = 10.5.sp,
                        color = descColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (onToggle == null) {
                        // 纯入口：透明底 + 暗框「进入」(分组色)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(actionScale)
                                .clickable(interactionSource = actionInteraction, indication = LocalIndication.current) {
                                    haptic.hud()
                                    onClick()
                                },
                            shape = barShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, groupColor.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                stringResource(R.string.main_enter),
                                fontFamily = Sans,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = groupColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            )
                        }
                    } else {
                        // 可启停：执行 = 红底白字；中止 = 青字 + 呼吸边
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(actionScale)
                                .clickable(interactionSource = actionInteraction, indication = LocalIndication.current) {
                                    haptic.hud()
                                    onToggle()
                                },
                            shape = barShape,
                            color = if (running) Color.Transparent else Red,
                        ) {
                            Box {
                                Text(
                                    if (running) stringResource(R.string.main_abort) else stringResource(R.string.main_execute),
                                    fontFamily = Sans,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (running) TacGreen else White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                )
                                if (running) {
                                    Box(
                                        Modifier.matchParentSize()
                                            .graphicsLayer { alpha = breathAlpha?.value ?: 1f }
                                            .border(1.dp, TacGreen, barShape),
                                    )
                                }
                            }
                        }
                    }
                }
                if (running) {
                    // 呼吸边框 + 四角锁定框（TacticalCorners）
                    Box(
                        Modifier.matchParentSize()
                            .graphicsLayer { alpha = breathAlpha?.value ?: 1f }
                            .border(1.2.dp, TacGreen, cardShape),
                    )
                    TacticalCorners(
                        color = TacGreen,
                        modifier = Modifier.matchParentSize().graphicsLayer { alpha = breathAlpha?.value ?: 1f },
                        len = 8.dp,
                        stroke = 1.4.dp,
                    )
                }
            }
        }
    }
}

// ─── Android Studio 预览 ──────────────────────────────────────────────────────

/** 状态栏预览 */
@Preview(name = "状态栏", showBackground = true, backgroundColor = 0xFF060B14, widthDp = 393)
@Composable
private fun StatusBarPreview() {
    PreviewTheme {
        Column(Modifier.background(Dark).fillMaxSize().padding(16.dp)) {
            StatusBar()
        }
    }
}

/** 主页预览 —— 完整 20 模块 + 运行态卡片 */
@Preview(name = "主页", showBackground = true, backgroundColor = 0xFF060B14, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun HomeScreenPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vms = remember(app) { if (app != null) createPreviewVms(app) else null }
        if (vms == null) {
            PreviewFallback("主页")
        } else {
            HomeScreen(
                vms.bleVm, vms.wifiVm, vms.btVm, vms.wjVm, vms.wsVm, vms.arpVm,
                vms.httpVm, vms.pingVm, vms.blescanVm, vms.portscanVm, vms.walkieVm,
                vms.radarVm, vms.ftVm, vms.vaultVm, vms.revVm, vms.payloadVm,
                vms.bruteVm, vms.cveVm, mirrorVm = vms.mirrorVm, terminalVm = vms.terminalVm,
                settingsVm = vms.settingsVm, nav = {},
            )
        }
    }
}

/** 主路由预览 —— 免责声明 → 启动自检 → 主页完整流程 */
@Preview(name = "主路由完整流程", showBackground = true, backgroundColor = 0xFF060B14, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun MainScreenPreview() {
    PreviewTheme {
        val app = LocalContext.current.applicationContext as? Application
        val vms = remember(app) { if (app != null) createPreviewVms(app) else null }
        if (vms == null) {
            PreviewFallback("主路由")
        } else {
            MainScreen(
                vms.bleVm, vms.wifiVm, vms.btVm, vms.wjVm, vms.wsVm, vms.arpVm,
                vms.httpVm, vms.pingVm, vms.blescanVm, vms.portscanVm, vms.walkieVm,
                vms.radarVm, vms.ftVm, vms.vaultVm, vms.revVm, vms.payloadVm,
                vms.bruteVm, vms.cveVm, mirrorVm = vms.mirrorVm, terminalVm = vms.terminalVm,
                dbVm = vms.dbVm, settingsVm = vms.settingsVm, aiVm = viewModel(),
            )
        }
    }
}
