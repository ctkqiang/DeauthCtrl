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
import xin.ctkqiang.deauthctrl.viewmodel.BleSpamViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WifiDisruptViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 状态栏：黑底配白/红图标
        window.statusBarColor = Black.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // 检查权限是否已全部授予，是则直接进入主界面
        val allGranted = checkAllPermissionsGranted()

        setContent {
            var permissionsGranted by remember { mutableStateOf(allGranted) }

            DeauthCtrlTheme(darkTheme = true) {
                if (!permissionsGranted) {
                    PermissionRequestScreen(
                        onAllGranted = { permissionsGranted = true },
                    )
                } else {
                    val bleViewModel: BleSpamViewModel = viewModel()
                    val wifiViewModel: WifiDisruptViewModel = viewModel()
                    MainScreen(
                        bleViewModel = bleViewModel,
                        wifiViewModel = wifiViewModel,
                    )
                }
            }
        }
    }

    /** 检查所有必需权限是否已授予 */
    private fun checkAllPermissionsGranted(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
