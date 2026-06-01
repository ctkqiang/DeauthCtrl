package xin.ctkqiang.deauthctrl.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全局等宽字体引用
 *
 * 使用系统内置的 Monospace（等宽/终端字体），无需引入外部字体资源。
 * 在 Android 上通常映射到 Droid Sans Mono 或 Roboto Mono。
 */
val MonoFont = FontFamily.Monospace

/**
 * DeauthCtrl 全局排版系统
 *
 * Material3 Typography 的完整定义，所有 15 个文字样式槽位均指定为等宽字体。
 *
 * ## 设计原则
 * - 全站统一使用 FontFamily.Monospace（终端/黑客工具美学）
 * - 字距（letterSpacing）设为 0 或极小值（1sp/0.5sp），保持紧凑等宽感
 * - 字号覆盖从 10sp（labelSmall）到 36sp（displayLarge）的完整范围
 * - Bold 用于标题和强调，Normal 用于正文和辅助文字
 *
 * ## 15 个 Material3 文字样式层级
 * - displayLarge/displayMedium: 超大标题（36sp/28sp Bold）
 * - headlineLarge/headlineMedium: 大标题（24sp/20sp Bold）
 * - titleLarge/titleMedium: 页面/区域标题（18sp Bold / 16sp Normal）
 * - bodyLarge/bodyMedium/bodySmall: 正文三级（15sp/13sp/11sp Normal）
 * - labelLarge/labelMedium/labelSmall: 标签/按钮三级（14sp/12sp Bold, 10sp Normal）
 */
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp,
    ),
)
