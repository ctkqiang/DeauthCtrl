package xin.ctkqiang.deauthctrl.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.ui.screens.PreviewTheme

/** 一条权限在界面上的标题与说明 */
data class PermissionCopy(val title: String, val reason: String)

/**
 * 应用运行所需的运行时权限
 *
 * **单一出处**：权限网关的「检查」与「请求」都从这里取列表。
 */
object RequiredPermissions {

    /**
     * 运行所需权限。
     *
     * - Android 12 起蓝牙改用「附近的设备」三件套（SCAN/CONNECT/ADVERTISE）
     * - Android 13 起创建本机热点（LocalOnlyHotspot）额外要求 NEARBY_WIFI_DEVICES，
     *   缺失时 targetSdk 34 的 `startLocalOnlyHotspot` 会直接抛 SecurityException
     * - Android 11 及以下的 Wi-Fi/BLE 扫描由定位权限代偿
     */
    val runtime: Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 基站定位(GSM 邻区信息 CellInfo 需要)
            add(Manifest.permission.READ_PHONE_STATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 照片接收通知
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 步测归航:计步传感器
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }.toTypedArray()

    /**
     * 尚未授予的权限
     *
     * 必须回查 PackageManager，不能相信请求回调里的布尔值：用户勾选「不再询问」
     * 之后系统一律回 false，而真实状态只有这里能读到。
     * 静态预览（Layoutlib）的 PackageManager 不可用时返回空列表，让预览可渲染。
     */
    fun missing(context: Context): List<String> =
        runCatching {
            runtime.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        }.getOrDefault(emptyList())

    /** 权限 → 界面标题与说明(文案随界面语言) */
    fun describe(context: Context, permission: String): PermissionCopy = when (permission) {
        Manifest.permission.BLUETOOTH_SCAN -> PermissionCopy(context.getString(R.string.perm_nearby_scan), context.getString(R.string.perm_nearby_scan_desc))
        Manifest.permission.BLUETOOTH_CONNECT -> PermissionCopy(context.getString(R.string.perm_nearby_connect), context.getString(R.string.perm_nearby_connect_desc))
        Manifest.permission.BLUETOOTH_ADVERTISE -> PermissionCopy(context.getString(R.string.perm_nearby_advertise), context.getString(R.string.perm_nearby_advertise_desc))
        Manifest.permission.NEARBY_WIFI_DEVICES -> PermissionCopy(
            context.getString(R.string.perm_nearby_wifi),
            context.getString(R.string.perm_nearby_wifi_desc),
        )
        Manifest.permission.ACCESS_FINE_LOCATION -> PermissionCopy(context.getString(R.string.perm_location), context.getString(R.string.perm_location_desc))
        Manifest.permission.RECORD_AUDIO -> PermissionCopy(context.getString(R.string.perm_mic), context.getString(R.string.perm_mic_desc))
        Manifest.permission.CAMERA -> PermissionCopy(context.getString(R.string.perm_camera), context.getString(R.string.perm_camera_desc))
        Manifest.permission.READ_PHONE_STATE -> PermissionCopy(context.getString(R.string.perm_phone_state), context.getString(R.string.perm_phone_state_desc))
        Manifest.permission.POST_NOTIFICATIONS -> PermissionCopy(context.getString(R.string.perm_notifications), context.getString(R.string.perm_notifications_desc))
        Manifest.permission.ACTIVITY_RECOGNITION -> PermissionCopy(context.getString(R.string.perm_activity), context.getString(R.string.perm_activity_desc))
        else -> PermissionCopy(permission.substringAfterLast('.'), "")
    }
}

/** 打开本应用的系统设置页，供用户在「不再询问」之后手工补授权限 */
private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/**
 * 权限请求界面
 *
 * 应用启动时若检测到权限未授予，则显示此界面作为权限网关。
 * 通过 ActivityResultContracts.RequestMultiplePermissions 一次性请求所有必需权限。
 *
 * ## 交互流程
 * 1. 界面直接列出**当前还缺哪些权限**，而不是笼统地说"需要权限"
 * 2. 用户点击「授予权限」→ RequestMultiplePermissions 弹出系统权限对话框
 * 3. 回调里**回查 PackageManager** 复核，全部授予才调用 onAllGranted
 * 4. 仍有缺失 → 列出缺失项，并给出「去系统设置授权」入口
 *
 * @param onAllGranted 所有权限授予后的回调，通知父组件切换到主界面
 */
@Composable
fun PermissionRequestScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var missing by remember { mutableStateOf(RequiredPermissions.missing(context)) }
    // 权限卡片首次组合时从下方滑入 + 淡入
    var cardShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cardShown = true }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val remaining = RequiredPermissions.missing(context)
        missing = remaining
        if (remaining.isEmpty()) onAllGranted()
    }

    Box(Modifier.fillMaxSize().background(Dark)) {
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            StarBadge(64.dp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.app_name), fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 0.sp, color = White)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.perm_subtitle), fontFamily = Sans, fontSize = 12.sp, color = Gray)
            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(
                visible = cardShown,
                enter = fadeIn(tween(350)) + slideInVertically(tween(400)) { it / 3 },
            ) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = SurfaceBg, border = BorderStroke(1.dp, BorderDim)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Star(Gold, 9.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (missing.isEmpty()) stringResource(R.string.perm_status_ready) else stringResource(R.string.perm_status_missing, missing.size),
                                fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Red,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        if (missing.isEmpty()) {
                            PermissionItem(stringResource(R.string.perm_item_all), stringResource(R.string.perm_item_all_desc))
                        } else {
                            missing.forEachIndexed { index, permission ->
                                if (index > 0) Spacer(Modifier.height(10.dp))
                                val copy = RequiredPermissions.describe(context, permission)
                                PermissionItem(copy.title, copy.reason)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    haptic.hud()
                    if (missing.isEmpty()) onAllGranted() else launcher.launch(RequiredPermissions.runtime)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = White),
            ) {
                Text(
                    if (missing.isEmpty()) stringResource(R.string.perm_btn_enter) else stringResource(R.string.perm_btn_grant),
                    fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
            }

            // 「不再询问」勾上之后系统对话框不会再弹，只能引导用户去系统设置手工补授
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); context.openAppSettings() },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = Color.Transparent, border = BorderStroke(1.dp, BorderDim),
                ) {
                    Text(stringResource(R.string.perm_btn_settings), fontFamily = Sans, fontSize = 12.sp, color = Gray, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); onAllGranted() },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp), color = Color.Transparent, border = BorderStroke(1.dp, BorderDim),
            ) {
                Text(stringResource(R.string.perm_btn_skip), fontFamily = Sans, fontSize = 11.sp, color = Gray, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.perm_footer), fontFamily = Sans, fontSize = 11.sp, color = GoldDim)
        }
        ScanlineOverlay(lineSpacing = 4.dp, alpha = 0.03f, color = Red)
    }
}

/**
 * 单个权限项
 *
 * 菱形指示符 + 权限标题 + 权限描述。
 */
@Composable
private fun PermissionItem(title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Star(Gold, 9.dp, modifier = Modifier.padding(top = 3.dp))
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White)
            Text(desc, fontFamily = Sans, fontSize = 12.sp, color = Gray)
        }
    }
}

// ─── Android Studio 预览 ──────────────────────────────────────────────────────

/** 权限请求网关预览 */
@Preview(name = "权限请求", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun PermissionScreenPreview() {
    PreviewTheme { PermissionRequestScreen(onAllGranted = {}) }
}
