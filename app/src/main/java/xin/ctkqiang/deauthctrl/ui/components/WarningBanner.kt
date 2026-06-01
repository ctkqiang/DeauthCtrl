package xin.ctkqiang.deauthctrl.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * 警告横幅组件
 *
 * 在屏幕顶部显示的红色警告条，提醒用户本工具仅供授权实验室测试使用。
 * 通过 AnimatedVisibility 支持动画显示/隐藏。
 *
 * 使用 MaterialTheme.errorContainer 和 error 颜色，确保与主题配色一致。
 * 字体使用 Monospace 等宽字体，保持终端风格统一。
 *
 * @param visible 是否显示横幅，默认 true。设为 false 时以动画方式隐藏
 * @param modifier Modifier 修饰符
 */
@Composable
fun WarningBanner(
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "仅供授权实验室测试使用。请勿用于非您拥有的设备。",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
