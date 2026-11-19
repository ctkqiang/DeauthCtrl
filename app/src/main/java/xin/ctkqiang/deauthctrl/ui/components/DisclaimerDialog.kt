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
import androidx.compose.ui.res.stringResource
import xin.ctkqiang.deauthctrl.R
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
    // 局部色值命名为 RED 而非 R:与资源类 xin.ctkqiang.deauthctrl.R 冲突
    val RED = Color(0xFF35C9FF)
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
                        stringResource(R.string.disc_title),
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = RED,
                    )
                }
            },
            text = {
                Text(
                    stringResource(R.string.disc_body),
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
                    color = RED,
                    border = BorderStroke(1.dp, GOLD.copy(alpha = 0.9f)),
                ) {
                    Text(
                        stringResource(R.string.disc_confirm),
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
                        stringResource(R.string.disc_restricted),
                        fontFamily = FontFamily.Default,
                        fontSize = 11.sp,
                        color = G,
                    )
                }
            },
            titleContentColor = RED,
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
