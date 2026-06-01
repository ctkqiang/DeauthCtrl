package xin.ctkqiang.deauthctrl.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 法律免责声明对话框
 *
 * 首次启动时显示的合法警告弹窗，告知用户本工具的法律风险和正确使用方式。
 * 用户必须明确点击"我已知晓，继续"按钮后才能进入主界面。
 *
 * ## 警告内容
 * 1. 仅限授权安全研究和教育用途
 * 2. 干扰非自有设备属违法行为
 * 3. BLE/WiFi 攻击可能违反电信法规
 * 4. 开发者对滥用造成的损害不承担责任
 *
 * ## 交互限制
 * - 不可通过返回键或点击外部区域关闭（onDismissRequest 为空实现）
 * - 必须在确认按键上明确点击才能继续
 * - 确认后 openDialog 设为 false 并回调 onAccept
 *
 * @param onAccept 用户接受免责声明后的回调，用于通知父组件进入主界面
 */
@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit,
) {
    val openDialog = remember { mutableStateOf(true) }
    val R = Color(0xFFFF0000)
    val BG = Color(0xFF060606)
    val W = Color(0xFFDDDDDD)

    if (openDialog.value) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(
                    "!! 法律警告 !!",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = R,
                )
            },
            text = {
                Text(
                    """
本应用程序仅供授权安全研究和教育目的使用。

使用本应用即表示您确认：

> 干扰不属于您的网络或设备是违法行为。
> BLE 泛洪可能导致附近蓝牙设备崩溃。请仅在您自己的隔离实验环境中使用。
> Wi-Fi 信标泛洪可能违反电信法规。请仅对您拥有的设备进行测试。
> 开发者对因滥用或使用本工具造成的损害不承担任何责任。

请仅在您自己的实验环境中使用。请负责任地使用。
                    """.trimIndent(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = W,
                    textAlign = TextAlign.Start,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { openDialog.value = false; onAccept() },
                ) {
                    Text(
                        "我已知晓，继续。",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = R,
                    )
                }
            },
            containerColor = BG,
            titleContentColor = R,
        )
    }
}
