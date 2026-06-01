package xin.ctkqiang.deauthctrl.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionRequestScreen(onAllGranted: () -> Unit) {
    val required = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    var showRationale by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (required.all { results[it] == true }) onAllGranted() else showRationale = true
    }

    val R = Color(0xFFFF0000); val BG = Color(0xFF0A0A0A); val S = Color(0xFF0D0D0D)
    val W = Color(0xFFEEEEEE); val G = Color(0xFF777777); val B = Color(0xFF1F1F1F)

    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("DEAUTHCTRL", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = R)
            Spacer(Modifier.height(6.dp))
            Text("> 去认证控制系统", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = G)
            Spacer(Modifier.height(32.dp))

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp), color = S, border = BorderStroke(1.5.dp, B)) {
                Column(Modifier.padding(18.dp)) {
                    Text("所需权限", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = R)
                    Spacer(Modifier.height(14.dp))
                    PermissionItem("位置信息", "BLE 与 WiFi 扫描需要位置权限")
                    Spacer(Modifier.height(10.dp))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PermissionItem("附近设备", "BLE 广播与扫描需要蓝牙权限")
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); launcher.launch(required) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(containerColor = R, contentColor = W),
            ) {
                Text("[ 授予权限 ]", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            if (showRationale) {
                Spacer(Modifier.height(14.dp))
                Text("请在系统设置中手动授予所有必需权限后重新进入。", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = R, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); launcher.launch(required) }) {
                    Text("[ 重新请求 ]", fontFamily = FontFamily.Monospace, color = R, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("DeauthCtrl  |  仅供授权安全研究使用", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = G)
        }
        ScanlineOverlay(lineSpacing = 3.dp, alpha = 0.04f, color = R)
    }
}

@Composable
private fun PermissionItem(title: String, desc: String) {
    val R = Color(0xFFFF0000); val W = Color(0xFFEEEEEE); val G = Color(0xFF888888)
    Row(verticalAlignment = Alignment.Top) {
        Text("▸ ", fontFamily = FontFamily.Monospace, color = R, fontSize = 14.sp)
        Column {
            Text(title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = W)
            Text(desc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = G)
        }
    }
}
