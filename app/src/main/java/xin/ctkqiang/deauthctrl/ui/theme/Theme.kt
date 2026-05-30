package xin.ctkqiang.deauthctrl.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Red,
    onPrimary = TextBright,
    primaryContainer = RedDark,
    onPrimaryContainer = TextBright,
    secondary = Amber,
    onSecondary = Black,
    secondaryContainer = RedBackground,
    onSecondaryContainer = Amber,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = Red,
    onError = TextBright,
    errorContainer = RedBackground,
    onErrorContainer = RedBright,
)

private val LightColorScheme = lightColorScheme(
    primary = RedDark,
    onPrimary = TextBright,
    primaryContainer = RedBackground,
    onPrimaryContainer = RedDark,
    secondary = Amber,
    onSecondary = Black,
    secondaryContainer = Amber.copy(alpha = 0.12f),
    onSecondaryContainer = Black,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = Red,
    onError = TextBright,
    errorContainer = RedBackground,
    onErrorContainer = RedDark,
)

@Composable
fun DeauthCtrlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
