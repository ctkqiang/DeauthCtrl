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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/** 一条权限在界面上的标题与说明 */
data class PermissionCopy(val title: String, val reason: String)

/**
 * 应用运行所需的运行时权限
 *
 * **单一出处**：权限网关的「检查」与「请求」都从这里取列表。
 *
 * 这两个列表原先各写一份，导致过两次静默故障：
 * 1. 检查侧要求 CAMERA、请求侧却没有 CAMERA —— 用户永远无法通过网关，界面也说不清缺什么；
 * 2. 请求侧多出检查侧没有的 RECORD_AUDIO —— 申请了却从不校验。
 *
 * 清单里还有一批是安装期权限（BLUETOOTH / BLUETOOTH_ADMIN，均为 maxSdkVersion=30），
 * 不需要运行时申请，因此不在此列表中。
 */
object RequiredPermissions {

    /** 运行所需权限。Android 12 起蓝牙改用「附近的设备」三件套；12 以下由定位权限代偿。 */
    val runtime: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
        )
    }

    /**
     * 尚未授予的权限
     *
     * 必须回查 PackageManager，不能相信请求回调里的布尔值：用户勾选「不再询问」
     * 之后系统一律回 false，而真实状态只有这里能读到。
     */
    fun missing(context: Context): List<String> =
        runtime.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    /** 权限 → 界面标题与说明 */
    fun describe(permission: String): PermissionCopy = when (permission) {
        Manifest.permission.BLUETOOTH_SCAN -> PermissionCopy("附近设备 · 扫描", "发现并列出附近的 BLE 设备")
        Manifest.permission.BLUETOOTH_CONNECT -> PermissionCopy("附近设备 · 连接", "读取设备名称与已配对信息")
        Manifest.permission.BLUETOOTH_ADVERTISE -> PermissionCopy("附近设备 · 广播", "BLE 广播相关功能")
        Manifest.permission.ACCESS_FINE_LOCATION -> PermissionCopy("位置信息", "Android 11 及以下的 BLE 与 WiFi 扫描依赖此项")
        Manifest.permission.RECORD_AUDIO -> PermissionCopy("麦克风", "对讲机语音功能")
        Manifest.permission.CAMERA -> PermissionCopy("相机", "加密保险箱拍摄")
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
 * ## 权限列表
 * - Android 12+ (API 31+):
 *   - BLUETOOTH_SCAN: BLE 扫描
 *   - BLUETOOTH_CONNECT: BLE 连接
 *   - BLUETOOTH_ADVERTISE: BLE 广播
 *   - ACCESS_FINE_LOCATION: WiFi/BLE 扫描所需位置权限
 * - Android 11- (API 30-):
 *   - ACCESS_FINE_LOCATION: WiFi/BLE 扫描所需位置权限
 *
 * ## 交互流程
 * 1. 界面直接列出**当前还缺哪些权限**，而不是笼统地说"需要权限"
 * 2. 用户点击「授予权限」→ RequestMultiplePermissions 弹出系统权限对话框
 * 3. 回调里**回查 PackageManager** 复核，全部授予才调用 onAllGranted
 * 4. 仍有缺失 → 列出缺失项，并给出「去系统设置授权」入口
 *
 * ## 为什么不能相信请求回调的参数
 * 用户勾选「不再询问」之后，系统对已拒绝的权限一律回 false，而真实的授予状态
 * 只能从 PackageManager 读。早期版本把回调结果整个丢掉、无条件放行，
 * 后果是：全部拒绝也能进入主界面，之后每个 BLE / WiFi 操作都抛 SecurityException。
 *
 * @param onAllGranted 所有权限授予后的回调，通知父组件切换到主界面
 */
@Composable
fun PermissionRequestScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var missing by remember { mutableStateOf(RequiredPermissions.missing(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val remaining = RequiredPermissions.missing(context)
        missing = remaining
        if (remaining.isEmpty()) onAllGranted()
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
                    Text(
                        if (missing.isEmpty()) "权限已齐备" else "还需 ${missing.size} 项权限",
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = R,
                    )
                    Spacer(Modifier.height(14.dp))
                    if (missing.isEmpty()) {
                        PermissionItem("全部已授予", "可以进入主界面")
                    } else {
                        missing.forEachIndexed { index, permission ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            val copy = RequiredPermissions.describe(permission)
                            PermissionItem(copy.title, copy.reason)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (missing.isEmpty()) onAllGranted() else launcher.launch(RequiredPermissions.runtime)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(containerColor = R, contentColor = W),
            ) {
                Text(
                    if (missing.isEmpty()) "[ 进入主界面 ]" else "[ 授予权限 ]",
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
            }

            // 「不再询问」勾上之后系统对话框不会再弹，只能引导用户去系统设置手工补授
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); context.openAppSettings() },
                    shape = RoundedCornerShape(3.dp), color = Color.Transparent, border = BorderStroke(1.dp, G),
                ) {
                    Text("[ 去系统设置授权 ]", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = G, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAllGranted() },
                shape = RoundedCornerShape(3.dp), color = Color.Transparent, border = BorderStroke(1.dp, B),
            ) {
                Text("[ 跳过 —— 蓝牙与 WiFi 功能将不可用 ]", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = G, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(32.dp))
            Text("DeauthCtrl  |  仅供授权安全研究使用", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = G)
        }
        ScanlineOverlay(lineSpacing = 3.dp, alpha = 0.04f, color = R)
    }
}

/**
 * 单个权限项
 *
 * 在权限卡片中显示的单项权限信息，包含红色箭头指示符 + 权限标题 + 权限描述。
 *
 * @param title 权限标题（如"位置信息"）
 * @param desc 权限描述/原因（如"BLE 与 WiFi 扫描需要位置权限"）
 */
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
