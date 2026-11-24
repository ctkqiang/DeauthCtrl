package xin.ctkqiang.deauthctrl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.ui.components.PermissionRequestScreen
import xin.ctkqiang.deauthctrl.ui.components.RequiredPermissions
import xin.ctkqiang.deauthctrl.ui.screens.MainScreen
import xin.ctkqiang.deauthctrl.ui.screens.OperatorPromptScreen
import xin.ctkqiang.deauthctrl.ui.theme.DeauthCtrlTheme
import xin.ctkqiang.deauthctrl.ui.theme.HudThemeState
import xin.ctkqiang.deauthctrl.ui.theme.LauncherIcon
import xin.ctkqiang.deauthctrl.viewmodel.ArpScanViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BleScannerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BleSpamViewModel
import xin.ctkqiang.deauthctrl.viewmodel.HttpClientViewModel
import xin.ctkqiang.deauthctrl.viewmodel.KEY_HAPTICS_ENABLED
import xin.ctkqiang.deauthctrl.viewmodel.KEY_OPERATOR_NAME
import xin.ctkqiang.deauthctrl.viewmodel.KEY_RADAR_ALERT_ENABLED
import xin.ctkqiang.deauthctrl.viewmodel.KEY_TEAM_ID
import xin.ctkqiang.deauthctrl.viewmodel.PingViewModel
import xin.ctkqiang.deauthctrl.viewmodel.PortScannerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BluetoothJammerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WebServerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WifiDisruptViewModel
import xin.ctkqiang.deauthctrl.viewmodel.FileTransferViewModel
import xin.ctkqiang.deauthctrl.viewmodel.RadarViewModel
import xin.ctkqiang.deauthctrl.viewmodel.RevShellViewModel
import xin.ctkqiang.deauthctrl.viewmodel.PayloadViewModel
import xin.ctkqiang.deauthctrl.viewmodel.DirBruteViewModel
import xin.ctkqiang.deauthctrl.viewmodel.CVESearchViewModel
import xin.ctkqiang.deauthctrl.viewmodel.SecureMediaViewModel
import xin.ctkqiang.deauthctrl.viewmodel.SettingsViewModel
import xin.ctkqiang.deauthctrl.viewmodel.TerminalViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WalkieTalkieViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WifiJammerViewModel
import xin.ctkqiang.deauthctrl.mirroring.ui.MirroringViewModel

/**
 * DeauthCtrl 主 Activity
 *
 * 应用唯一入口 Activity，负责：
 * 1. 窗口装饰配置（全屏沉浸式 + 黑色状态栏 + 暗色导航栏按钮）
 * 2. 运行时权限检查（蓝牙、WiFi 扫描所需权限）
 * 3. 权限网关路由（未授权 → PermissionRequestScreen，已授权 → MainScreen）
 * 4. 10 个 ViewModel 的创建和注入（通过 viewModel() 委托）
 *
 * ## 权限策略
 * - 权限清单**单一出处**：ui.components.RequiredPermissions（检查侧与请求侧共用同一份）
 * - Android 12+ (API 31+): BLUETOOTH_SCAN / CONNECT / ADVERTISE、ACCESS_FINE_LOCATION、RECORD_AUDIO、CAMERA
 * - Android 11- (API 30-): ACCESS_FINE_LOCATION、RECORD_AUDIO、CAMERA
 * - 任一缺失即显示权限请求界面；ON_RESUME 时重新核对，因为用户可能刚从系统设置回来
 *
 * ## ViewModel 注入
 * 使用 Jetpack Compose 的 viewModel() 内联函数创建 ViewModel，
 * 生命周期由 ComponentActivity 管理，横竖屏旋转等配置变更时自动保留。
 */
class MainActivity : ComponentActivity() {

    /** 通知点击带来的照片 id —— 直达相机页并打开该照片 */
    private val pendingPhotoId = mutableStateOf<Long?>(null)

    /** 调试深链:--es screen <route> 直达指定模块页 */
    private val pendingScreen = mutableStateOf<String?>(null)

    /** 调试深链:--ei chapter <n> wiki 章节直达 */
    private val pendingChapter = mutableStateOf(-1)

    /** 调试深链:--ez autoscan true CCTV 页进入即扫描 */
    private val pendingAutoScan = mutableStateOf(false)

    /**
     * 语言切换后的 recreate 必须在这里重新包装 —— Application 层的 wrap
     * 只在进程启动时执行一次,不覆盖 Activity 重建;否则「设置里选了
     * English 界面仍是中文」。
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getLongExtra(xin.ctkqiang.deauthctrl.manager.PhotoRelayHost.EXTRA_PHOTO_ID, -1L)
        if (id > 0) pendingPhotoId.value = id
        intent.getStringExtra("screen")?.takeIf { it.isNotBlank() }?.let {
            android.util.Log.d("DEEPLINK", "onNewIntent screen=$it")
            pendingScreen.value = it
        }
        intent.getIntExtra("chapter", -1).takeIf { it >= 0 }?.let { pendingChapter.value = it }
        if (intent.getBooleanExtra("autoscan", false)) pendingAutoScan.value = true
        if (intent.getBooleanExtra("autoscan", false)) pendingAutoScan.value = true
    }

    /**
     * Activity 创建入口
     *
     * 执行顺序：
     * 1. enableEdgeToEdge() — 启用边到边显示（内容延伸到状态栏和导航栏后方）
     * 2. 设置状态栏为纯黑色（Black.toArgb()）
     * 3. 强制状态栏和导航栏图标为暗色模式（白字图标）
     * 4. 检查所有必需权限是否已授予
     * 5. 根据权限状态渲染 PermissionsScreen 或 MainScreen
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 系统状态栏与导航栏全透明:内容绘制到屏幕边缘(edge-to-edge 已开启),
        // 不染色、不加深 —— 全息 HUD 界面直接透出系统区域
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // 组合开始前应用持久化的队伍主题与触觉开关：
        // 先写入全局色板，再进入 setContent，首帧即是用户所选配色。
        val prefs = getSharedPreferences("deauthctrl_prefs", MODE_PRIVATE)
        HudThemeState.applyTeam(prefs.getString(KEY_TEAM_ID, "") ?: "")
        // 桌面图标同步换色:按队伍主色启用对应图标变体 alias
        LauncherIcon.apply(this, HudThemeState.current)
        HudThemeState.hapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        HudThemeState.radarAlertEnabled = prefs.getBoolean(KEY_RADAR_ALERT_ENABLED, true)

        val firstPhotoId = intent.getLongExtra(xin.ctkqiang.deauthctrl.manager.PhotoRelayHost.EXTRA_PHOTO_ID, -1L)
        if (firstPhotoId > 0) pendingPhotoId.value = firstPhotoId
        intent.getStringExtra("screen")?.takeIf { it.isNotBlank() }?.let {
            android.util.Log.d("DEEPLINK", "onCreate screen=$it")
            pendingScreen.value = it
        }
        intent.getIntExtra("chapter", -1).takeIf { it >= 0 }?.let { pendingChapter.value = it }
        if (intent.getBooleanExtra("autoscan", false)) pendingAutoScan.value = true

        setContent {
            DeauthCtrlTheme(darkTheme = true) {
                val activity = this@MainActivity
                var granted by remember { mutableStateOf(!hasMissingPermissions()) }
                val settingsVm: SettingsViewModel = viewModel()
                val teamId by settingsVm.teamId.collectAsState()
                val photoId by pendingPhotoId
                val screenRoute by pendingScreen
                val chapterIdx by pendingChapter
                val autoScan by pendingAutoScan

                // Android 13+ 通知权限:一次性请求(不阻塞主流程,未授权则照片静默接收)
                val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) {}
                LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // 用户去系统设置授权后返回，必须重新核对；否则会永远停在权限页
                DisposableEffect(activity) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) granted = !hasMissingPermissions()
                    }
                    activity.lifecycle.addObserver(observer)
                    onDispose { activity.lifecycle.removeObserver(observer) }
                }

                // key(teamId)：切队时强制整棵 UI 树重组，
                // 使所有界面读取到 HudThemeState 刚写入的新全局色板
                key(teamId) {
                    // 首次启动:未设置呼号时先询问用户名,再进入主界面
                    var operatorName by remember {
                        mutableStateOf(prefs.getString(KEY_OPERATOR_NAME, "") ?: "")
                    }
                    when {
                        !granted -> PermissionRequestScreen { granted = true }
                        operatorName.isBlank() -> OperatorPromptScreen(
                            onConfirm = { name ->
                                settingsVm.setUsername(name.trim())
                                operatorName = name.trim()
                            },
                            onSkip = {
                                settingsVm.setUsername("哪吒")
                                operatorName = "哪吒"
                            },
                        )
                        else -> MainScreen(
                            bleVm = viewModel(),
                            wifiVm = viewModel(),
                            btVm = viewModel(),
                            wjVm = viewModel(),
                            wsVm = viewModel(),
                            arpVm = viewModel(),
                            httpVm = viewModel(),
                            pingVm = viewModel(),
                            blescanVm = viewModel(),
                            portscanVm = viewModel(),
                            walkieVm = viewModel(),
                            vaultVm = viewModel(),
                            radarVm = viewModel(),
                            ftVm = viewModel(),
                            revVm = viewModel(),
                            payloadVm = viewModel(),
                            bruteVm = viewModel(),
                            cveVm = viewModel(),
                            mirrorVm = viewModel(),
                            terminalVm = viewModel(),
                            dbVm = viewModel(),
                            settingsVm = settingsVm,
                            aiVm = viewModel(),
                            initialPhotoId = photoId,
                            initialScreen = screenRoute,
                            initialChapter = chapterIdx,
                            initialAutoScan = autoScan,
                        )
                    }
                }
            }
        }
    }

    /**
     * 是否仍有必需权限未授予
     *
     * 权限清单集中定义在 [RequiredPermissions]，检查侧不再单独维护一份。
     * 原先这里要求 CAMERA 而权限页的请求列表里没有 CAMERA，
     * 结果是用户永远无法通过网关，界面也说不清到底缺了什么。
     *
     * @return true 表示仍有权限未授予，需要停留在权限网关
     */
    private fun hasMissingPermissions(): Boolean = RequiredPermissions.missing(this).isNotEmpty()
}
