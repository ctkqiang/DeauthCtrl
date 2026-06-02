package xin.ctkqiang.deauthctrl.mirroring.ui

import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.mirroring.model.MirroringDevice

/** 屏幕镜像仪表板 */
@Composable
fun DashboardScreen(
    vm: MirroringViewModel,
    onConnectDevice: (MirroringDevice) -> Unit,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val state by vm.state.collectAsState()
    val devices by vm.discoveryManager.devices.collectAsState()

    // MediaProjection 权限 — 先启动前台服务，再请求权限
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            vm.startHostCapture(result.resultCode, result.data!!)
        } else {
            vm.stopHost() // 用户拒绝 — 停止已启动的前台服务
        }
    }

    val Red = Color(0xFFFF0000)
    val Dark = Color(0xFF0A0A0A)
    val White = Color(0xFFEEEEEE)
    val Gray = Color(0xFF777777)

    Column(Modifier.fillMaxSize().background(Dark)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("屏幕镜像", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Red)
            TextButton(onClick = back) { Text("< 返回", fontFamily = FontFamily.Monospace, color = Gray, fontSize = 11.sp) }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {

            // --- Host 按钮 ---
            val isHostActive = state is MirroringState.HostPreparing || state is MirroringState.HostLive
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isHostActive) {
                        vm.stopHost()
                    } else {
                        vm.startHostPrep()
                        val pm = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                            as MediaProjectionManager
                        projectionLauncher.launch(pm.createScreenCaptureIntent())
                    }
                },
                shape = RoundedCornerShape(3.dp),
                color = if (isHostActive) Color.Transparent else Red,
                border = BorderStroke(1.5.dp, if (isHostActive) Red.copy(alpha = 0.3f) else Red),
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center) {
                    Text("$ ", fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isHostActive) Red else White)
                    Text(
                        when {
                            state is MirroringState.HostPreparing -> "等待授权..."
                            state is MirroringState.HostLive -> "● 停止共享屏幕 (Host)"
                            else -> "开始共享屏幕 (Host)"
                        },
                        fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (isHostActive) Red else White,
                    )
                }
            }

            if (state is MirroringState.HostLive) {
                Spacer(Modifier.height(6.dp))
                Text("● 正在广播 — ${vm.hostVideoPort}:10087", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Gray)
            }

            Spacer(Modifier.height(16.dp))
            Text("已发现的设备", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Red, fontWeight = FontWeight.Bold)

            // --- Client 扫描 ---
            val isScanning = state is MirroringState.ClientScanning || state is MirroringState.ClientConnecting || state is MirroringState.ClientLive

            if (devices.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state is MirroringState.ClientScanning) "正在扫描局域网..."
                    else if (state is MirroringState.ClientConnecting) "正在连接..."
                    else "未发现设备",
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Gray,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (isScanning) vm.stopScanning() else vm.startScanning()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                    border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                ) {
                    Text(
                        if (isScanning) "停止扫描" else "扫描局域网",
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(devices) { device ->
                        DeviceRow(device) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.connectToDevice(device)
                            onConnectDevice(device)
                        }
                    }
                }
            }

            // 错误提示
            if (state is MirroringState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "[!] ${(state as MirroringState.Error).message}",
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Red,
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(device: MirroringDevice, onClick: () -> Unit) {
    val Red = Color(0xFFFF0000)
    val S = Color(0xFF0D0D0D)
    val White = Color(0xFFEEEEEE)
    val Gray = Color(0xFF777777)

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(3.dp), color = S,
        border = BorderStroke(1.dp, Red.copy(alpha = 0.2f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(device.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = White)
                Text("${device.hostAddress}:${device.videoPort}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Gray)
            }
            Text("连接 >", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Red)
        }
    }
}
