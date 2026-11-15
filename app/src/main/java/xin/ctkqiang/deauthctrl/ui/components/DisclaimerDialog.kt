package xin.ctkqiang.deauthctrl.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * 用户必须明确点击「我已知晓，继续」按钮后才能进入主界面。
 *
 * ## 交互限制
 * - 不可通过返回键或点击外部区域关闭（onDismissRequest 为空实现）
 * - 必须在确认按键上明确点击才能继续
 * - 确认后回调 [onAccept]，由调用方持久化"已接受"状态
 *
 * @param onAccept 用户接受免责声明后的回调
 */
@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit,
) {
    val openDialog = remember { mutableStateOf(true) }
    val R = Color(0xFFFF0000)
    val BG = Color(0xFF0A0A0A)
    val W = Color(0xFFE6E6E6)
    val G = Color(0xFF8A8A8A)
    val B = Color(0xFF232323)

    if (openDialog.value) {
        AlertDialog(
            onDismissRequest = { },
            shape = RoundedCornerShape(12.dp),
            containerColor = BG,
            title = {
                Text(
                    "⚠ 法律警告",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = R,
                )
            },
            text = {
                Text(
                    """
本应用仅供授权安全研究与教育用途。

使用本应用即表示您确认：

▸ 干扰不属于您的网络或设备是违法行为
▸ BLE 泛洪可能导致附近蓝牙设备崩溃，
  请仅在您自己的隔离实验环境中使用
▸ WiFi 信标泛洪可能违反电信法规，
  请仅对您拥有的设备进行测试
▸ 开发者对因滥用本工具造成的损害
  不承担任何责任

请负责任地使用。
                    """.trimIndent(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    color = W,
                    textAlign = TextAlign.Start,
                )
            },
            confirmButton = {
                Surface(
                    onClick = { openDialog.value = false; onAccept() },
                    shape = RoundedCornerShape(7.dp),
                    color = R,
                    border = BorderStroke(1.dp, R),
                ) {
                    Text(
                        "我已知晓，继续",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { /* 不可关闭：必须明确确认 */ }) {
                    Text(
                        "仅限授权使用",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = G,
                    )
                }
            },
            titleContentColor = R,
        )
    }
}
