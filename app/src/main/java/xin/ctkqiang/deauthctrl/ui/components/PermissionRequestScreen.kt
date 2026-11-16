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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val remaining = RequiredPermissions.missing(context)
        missing = remaining
        if (remaining.isEmpty()) onAllGranted()
    }

    Box(Modifier.fillMaxSize().background(Dark)) {
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            StarBadge(64.dp)
            Spacer(Modifier.height(12.dp))
            Text("去认证控制", fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 3.sp, color = White)
            Spacer(Modifier.height(4.dp))
            Text("> 战前授权 · 授予所需权限", fontFamily = Sans, fontSize = 12.sp, color = Gray)
            Spacer(Modifier.height(28.dp))

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = SurfaceBg, border = BorderStroke(1.dp, BorderDim)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Star(Gold, 9.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (missing.isEmpty()) "权限已齐备" else "还需 ${missing.size} 项权限",
                            fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Red,
                        )
                    }
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
                shape = RoundedCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = White),
            ) {
                Text(
                    if (missing.isEmpty()) "[ 进入主界面 ]" else "[ 授予权限 ]",
                    fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
            }

            // 「不再询问」勾上之后系统对话框不会再弹，只能引导用户去系统设置手工补授
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); context.openAppSettings() },
                    shape = RoundedCornerShape(7.dp), color = Color.Transparent, border = BorderStroke(1.dp, BorderDim),
                ) {
                    Text("[ 去系统设置授权 ]", fontFamily = Sans, fontSize = 12.sp, color = Gray, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAllGranted() },
                shape = RoundedCornerShape(7.dp), color = Color.Transparent, border = BorderStroke(1.dp, BorderDim),
            ) {
                Text("[ 跳过 —— 蓝牙与 WiFi 功能将不可用 ]", fontFamily = Sans, fontSize = 11.sp, color = Gray, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(28.dp))
            Text("★ 去认证控制  |  仅供授权安全研究使用 ★", fontFamily = Sans, fontSize = 11.sp, color = GoldDim)
        }
        ScanlineOverlay(lineSpacing = 4.dp, alpha = 0.03f, color = Red)
    }
}

/**
 * 单个权限项
 *
 * 金星指示符 + 权限标题 + 权限描述。
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
