package xin.ctkqiang.deauthctrl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.ui.components.PermissionRequestScreen
import xin.ctkqiang.deauthctrl.ui.components.RequiredPermissions
import xin.ctkqiang.deauthctrl.ui.screens.MainScreen
import xin.ctkqiang.deauthctrl.ui.theme.Black
import xin.ctkqiang.deauthctrl.ui.theme.DeauthCtrlTheme
import xin.ctkqiang.deauthctrl.ui.theme.HudThemeState
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
        window.statusBarColor = Black.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // 组合开始前应用持久化的队伍主题与触觉开关：
        // 先写入全局色板，再进入 setContent，首帧即是用户所选配色。
        val prefs = getSharedPreferences("deauthctrl_prefs", MODE_PRIVATE)
        HudThemeState.applyTeam(prefs.getString(KEY_TEAM_ID, "") ?: "")
        HudThemeState.hapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        HudThemeState.radarAlertEnabled = prefs.getBoolean(KEY_RADAR_ALERT_ENABLED, true)

        setContent {
            DeauthCtrlTheme(darkTheme = true) {
                val activity = this@MainActivity
                var granted by remember { mutableStateOf(!hasMissingPermissions()) }
                val settingsVm: SettingsViewModel = viewModel()
                val teamId by settingsVm.teamId.collectAsState()

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
                    if (granted) {
                        MainScreen(
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
                            settingsVm = settingsVm,
                        )
                    } else {
                        PermissionRequestScreen { granted = true }
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
