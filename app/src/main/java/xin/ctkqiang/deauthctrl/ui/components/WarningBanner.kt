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
 * Persistent warning banner displayed in the app.
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
                text = "FOR AUTHORIZED LAB TESTING ONLY. DO NOT USE ON DEVICES YOU DO NOT OWN.",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
