package xin.ctkqiang.deauthctrl

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.ui.components.PermissionRequestScreen
import xin.ctkqiang.deauthctrl.ui.screens.MainScreen
import xin.ctkqiang.deauthctrl.ui.theme.Black
import xin.ctkqiang.deauthctrl.ui.theme.DeauthCtrlTheme
import xin.ctkqiang.deauthctrl.viewmodel.ArpScanViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BleScannerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BleSpamViewModel
import xin.ctkqiang.deauthctrl.viewmodel.HttpClientViewModel
import xin.ctkqiang.deauthctrl.viewmodel.PingViewModel
import xin.ctkqiang.deauthctrl.viewmodel.PortScannerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BluetoothJammerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WebServerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WifiDisruptViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WifiJammerViewModel

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
 * - Android 12+ (API 31+): 需要 BLUETOOTH_SCAN、BLUETOOTH_CONNECT、BLUETOOTH_ADVERTISE、ACCESS_FINE_LOCATION
 * - Android 11- (API 30-): 仅需要 ACCESS_FINE_LOCATION
 * - 所有权限为必需项（任一缺失即显示权限请求界面）
 * - 权限通过 ActivityResultContracts.RequestMultiplePermissions 一次性请求
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

        val allGranted = checkAllPermissionsGranted()

        setContent {
            var permissionsGranted by remember { mutableStateOf(allGranted) }
            DeauthCtrlTheme(darkTheme = true) {
                if (!permissionsGranted) {
                    PermissionRequestScreen(onAllGranted = { permissionsGranted = true })
                } else {
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
                    )
                }
            }
        }
    }

    /**
     * 检查所有必需权限是否已授予
     *
     * 根据 Android API 级别动态确定需要检查的权限列表，
     * 通过 ContextCompat.checkSelfPermission 逐个验证。
     *
     * @return true 表示所有权限已授予，可进入主界面
     */
    private fun checkAllPermissionsGranted(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return required.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }
}
