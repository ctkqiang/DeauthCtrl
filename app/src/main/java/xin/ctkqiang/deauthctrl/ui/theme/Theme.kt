package xin.ctkqiang.deauthctrl.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material3 暗色主题配色方案
 *
 * 以红色（#FF0000）为主色、纯黑系为背景色的暗色主题。
 * 所有 Material3 颜色槽位均映射到 Color.kt 中定义的暗色 Token。
 *
 * 颜色槽位说明：
 * - primary/onPrimary: 主按钮、选中状态（红色 + 白色文字）
 * - primaryContainer/onPrimaryContainer: 弱强调容器（暗红 + 白色文字）
 * - secondary/onSecondary: 次要元素（琥珀色 + 黑色文字）
 * - background/surface: 背景和卡片背景
 * - error: 错误状态（红色，与 primary 同色以保持一致性）
 */
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

/**
 * Material3 亮色主题配色方案
 *
 * 备用亮色主题（当前未启用，保留用于未来可能的日间模式切换）。
 * 使用浅灰背景 + 暗红主色。
 */
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

/**
 * DeauthCtrl 全局主题 Composable
 *
 * 包装所有子 Composable，提供统一的 Material3 配色和排版。
 *
 * ## 主题选择逻辑
 * 1. 若 dynamicColor = true 且 Android 12+ → 调用 dynamicDarkColorScheme/dynamicLightColorScheme（跟随系统 Wallpaper 取色）
 * 2. 若 darkTheme = true → 使用自定义暗色主题（DarkColorScheme）
 * 3. 否则 → 使用自定义亮色主题（LightColorScheme）
 *
 * 当前默认：darkTheme = true, dynamicColor = false → 始终使用自定义暗红主题
 *
 * @param darkTheme 是否使用暗色主题，默认跟随系统设置
 * @param dynamicColor 是否启用 Material You 动态取色，默认 false
 * @param content 子 Composable 内容，通过 MaterialTheme 包装后渲染
 */
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
