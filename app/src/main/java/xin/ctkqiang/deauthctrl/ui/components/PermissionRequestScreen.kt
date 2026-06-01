package xin.ctkqiang.deauthctrl.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionRequestScreen(
    onAllGranted: () -> Unit,
) {
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val allOk = requiredPermissions.all { results[it] == true }
        if (allOk) onAllGranted() else showRationale = true
    }

    val R = Color(0xFFFF0000)
    val BG = Color(0xFF010101)
    val S = Color(0xFF080808)
    val W = Color(0xFFEEEEEE)
    val G = Color(0xFF666666)

    Box(Modifier.fillMaxSize().background(BG)) {
        MatrixRainBackground(columnCount = 24, charAlpha = 0.05f)

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "DEAUTHCTRL",
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 26.sp, color = R, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "> 去认证控制系统_",
                fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                color = R.copy(alpha = 0.7f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(2.dp),
                colors = CardDefaults.cardColors(containerColor = S),
                border = androidx.compose.foundation.BorderStroke(1.dp, R.copy(alpha = 0.2f)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "// 所需权限",
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, color = R,
                    )
                    Spacer(Modifier.height(10.dp))
                    PermissionRow(">", "位置信息", "BLE 和 Wi-Fi 扫描需要位置权限 (Android 系统要求)")
                    Spacer(Modifier.height(6.dp))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PermissionRow(">", "附近设备", "BLE 广播与扫描需要蓝牙权限")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { launcher.launch(requiredPermissions) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = R, contentColor = Color.White),
            ) {
                Text("[ 授予权限 ]", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            if (showRationale) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "所有权限均为必需项。请在系统设置中手动授予权限后重新进入应用。",
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    color = R, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { launcher.launch(requiredPermissions) }) {
                    Text("[ 重新请求权限 ]", fontFamily = FontFamily.Monospace, color = R)
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "DeauthCtrl | 仅供授权安全研究使用",
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = G,
            )
        }

        ScanlineOverlay(lineSpacing = 2.dp, alpha = 0.05f, color = R)
    }
}

@Composable
private fun PermissionRow(icon: String, title: String, description: String) {
    val R = Color(0xFFFF0000)
    val W = Color(0xFFEEEEEE)
    val G = Color(0xFF888888)

    Row(verticalAlignment = Alignment.Top) {
        Text(icon, fontFamily = FontFamily.Monospace, color = R.copy(alpha = 0.7f), fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = W)
            Text(description, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = G)
        }
    }
}
