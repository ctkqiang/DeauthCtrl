package xin.ctkqiang.deauthctrl.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.ui.screens.PreviewTheme

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
    val R = Color(0xFF35C9FF)
    val GOLD = Color(0xFF9BD7FF)
    val BG = Color(0xFF0B1522)
    val W = Color(0xFFE8F4FF)
    val G = Color(0xFF7E93AD)

    if (openDialog.value) {
        AlertDialog(
            onDismissRequest = { },
            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
            containerColor = BG,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Star(GOLD, 10.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "法律警告",
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = R,
                    )
                }
            },
            text = {
                Text(
                    """
本应用仅供授权安全研究与教育用途。

真实能力清单（UI 不夸大平台做不到的事）：

▸ Wi-Fi：只能创建「本机」SoftAP 热点 / 轮换本机热点
  SSID。不会发送 deauth / beacon 帧注入，不会断开
  他人的连接；启动热点还可能中断本机 Wi-Fi。
▸ BLE：只广播伪造厂商广告，可能让附近设备显示配对
  或连续性提示，不能压制、不能断开设备；效果取决于
  接收端的系统与版本。
▸ 蓝牙发现：只是本机扫描附近经典蓝牙设备，不会干扰
  其他设备。
▸ 802.11 帧注入 / 去认证需要 root + 特定芯片与固件。
  应用会在使用时检测能力，检测不通过会明确提示「当前
  系统不支持」，不会提供可执行的假象。

使用本应用即表示您确认：

▸ 干扰不属于您的网络或设备是违法行为
▸ 请仅在您拥有或已获得书面授权的环境中测试
▸ 开发者对因滥用本工具造成的损害不承担任何责任

请负责任地使用。
                    """.trimIndent(),
                    fontFamily = FontFamily.Default,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = W,
                    textAlign = TextAlign.Start,
                )
            },
            confirmButton = {
                Surface(
                    onClick = { openDialog.value = false; onAccept() },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = R,
                    border = BorderStroke(1.dp, GOLD.copy(alpha = 0.9f)),
                ) {
                    Text(
                        "我已知晓，继续",
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = W,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { /* 不可关闭：必须明确确认 */ }) {
                    Text(
                        "仅限授权使用",
                        fontFamily = FontFamily.Default,
                        fontSize = 11.sp,
                        color = G,
                    )
                }
            },
            titleContentColor = R,
        )
    }
}

// ─── Android Studio 预览 ──────────────────────────────────────────────────────

/** 法律免责声明预览 */
@Preview(name = "法律免责声明", showBackground = true, backgroundColor = 0xFF08080A, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun DisclaimerPreview() {
    PreviewTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF08080A))) {
            DisclaimerDialog(onAccept = {})
        }
    }
}
