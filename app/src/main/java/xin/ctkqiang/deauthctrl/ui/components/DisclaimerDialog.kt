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
