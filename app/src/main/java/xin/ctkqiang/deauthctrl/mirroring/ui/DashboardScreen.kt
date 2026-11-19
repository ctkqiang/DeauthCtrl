package xin.ctkqiang.deauthctrl.mirroring.ui

import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.mirroring.model.MirroringDevice
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
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud

/**
 * 屏幕镜像仪表板 —— 全息 HUD 统一设计
 *
 * 与全应用共享同一套视觉系统（TerminalHeader / TermPanel / ActionButton /
 * CutCornerShape / 主题全局色），切队换肤即时生效。
 */
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

    val isHostActive = state is MirroringState.HostPreparing || state is MirroringState.HostLive
    val isScanning = state is MirroringState.ClientScanning || state is MirroringState.ClientConnecting || state is MirroringState.ClientLive

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.mirror_dash_title), back)

        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(14.dp)) {
            // ─── Host 共享主操作 ───
            ActionButton(
                running = isHostActive,
                runLabel = stringResource(R.string.mirror_dash_share_host),
                stopLabel = stringResource(R.string.mirror_dash_stop_sharing),
            ) {
                haptic.hud()
                if (isHostActive) {
                    vm.stopHost()
                } else {
                    vm.startHostPrep()
                    val pm = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager
                    projectionLauncher.launch(pm.createScreenCaptureIntent())
                }
            }

            if (state is MirroringState.HostLive) {
                Spacer(Modifier.height(6.dp))
                val hostPulse = rememberInfiniteTransition()
                val hostDotAlpha by hostPulse.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse))
                val broadcasting = stringResource(R.string.mirror_dash_broadcasting, vm.hostVideoPort)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = TacGreen.copy(alpha = hostDotAlpha))) { append("● ") }
                        withStyle(SpanStyle(color = Gray)) { append(broadcasting) }
                    },
                    fontFamily = Mono, fontSize = 10.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ─── 目标设备面板 ───
            Row(verticalAlignment = Alignment.CenterVertically) {
                Star(Gold, 8.dp)
                Spacer(Modifier.width(7.dp))
                Text(stringResource(R.string.mirror_dash_devices_found, devices.size), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red)
            }
            Spacer(Modifier.height(6.dp))

            if (devices.isEmpty()) {
                EmptyHint(
                    if (state is MirroringState.ClientScanning) stringResource(R.string.mirror_dash_scanning)
                    else if (state is MirroringState.ClientConnecting) stringResource(R.string.mirror_dash_connecting)
                    else stringResource(R.string.mirror_dash_empty),
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (isScanning) vm.stopScanning() else vm.startScanning()
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Red.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                ) {
                    Text(
                        if (isScanning) stringResource(R.string.mirror_dash_stop_scan) else stringResource(R.string.mirror_dash_start_scan),
                        fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Red, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(devices) { i, device ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(i * 60L); visible = true }
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 4 },
                        ) {
                            DeviceRow(device) {
                                haptic.hud()
                                vm.connectToDevice(device)
                                onConnectDevice(device)
                            }
                        }
                    }
                }
            }

            // 错误提示
            if (state is MirroringState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "[!] ${(state as MirroringState.Error).message}",
                    fontFamily = Mono, fontSize = 10.sp, color = Danger,
                )
            }
        }
    }
}

/** 目标设备行 —— 主题化卡片：青边 + 切角 + 菱形标记 */
@Composable
private fun DeviceRow(device: MirroringDevice, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, BorderDim),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(device.name, fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White)
                Text("${device.hostAddress}:${device.videoPort}", fontFamily = Mono, fontSize = 9.sp, color = Gray)
            }
            Star(Gold, 6.dp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.mirror_dash_connect), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Red)
        }
    }
}
