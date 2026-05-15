package xin.ctkqiang.deauthctrl.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign

/**
 * Legal & ethical disclaimer shown on first app launch.
 * User must accept before accessing the app.
 */
@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit,
) {
    val openDialog = remember { mutableStateOf(true) }

    if (openDialog.value) {
        AlertDialog(
            onDismissRequest = { /* cannot dismiss — must accept */ },
            title = {
                Text(
                    text = "⚠ LEGAL WARNING",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.error,
                )
            },
            text = {
                Text(
                    text = """
This application is for AUTHORIZED SECURITY RESEARCH and EDUCATIONAL PURPOSES only.

By using this app, you acknowledge:

• Disrupting networks or devices you DO NOT OWN is ILLEGAL.
• BLE spam can crash nearby Bluetooth devices. Only use in your own isolated lab environment.
• Wi-Fi beacon flooding may violate telecommunications regulations. Only test against equipment you own.
• The developer assumes NO LIABILITY for misuse or damage caused by this tool.

USE ONLY IN YOUR OWN LAB. USE RESPONSIBLY.
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Start,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openDialog.value = false
                        onAccept()
                    }
                ) {
                    Text(
                        "I UNDERSTAND. PROCEED.",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        )
    }
}
