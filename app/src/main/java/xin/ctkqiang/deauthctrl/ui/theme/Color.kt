package xin.ctkqiang.deauthctrl.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * DeauthCtrl 色彩系统
 *
 * 以红色（#FF0000）为主色调、深黑（#010101）为背景色的暗色主题配色方案。
 * 灵感来自 Mr. Robot 视觉风格和终端/黑客工具美学。
 *
 * ## 色彩分组
 * - **Red 系列**: 主色、亮色、暗色、透明背景 — 用于标题、按钮、强调元素、运行状态指示
 * - **Matrix Green 系列**: 备用强调色（当前未在 UI 中使用，保留供未来扩展）
 * - **Neon 系列**: 青色和琥珀色点缀色，用于 GlitchText 偏移层和特殊高亮
 * - **Black 系列**: 多层次黑色背景（#000000 → #111111），营造深度感
 * - **Border 系列**: 暗灰色边框，用于卡片和面板分隔
 * - **Text 系列**: 白色到深灰的四个文字层级（Primary/Bright/Dim/Muted）
 * - **Theme Tokens**: Material3 darkColorScheme 所需的标准映射
 */

/** 标准红色 — 标题、按钮、强调文字、运行状态 */
val Red = Color(0xFFFF0000)
/** 亮红色 — hover 高亮、次要强调 */
val RedBright = Color(0xFFFF3333)
/** 暗红色 — 非活跃状态、分隔线 */
val RedDark = Color(0xFFCC0000)
/** 红色 10% 透明度 — 卡片/面板激活背景 */
val RedBackground = Color(0x1AFF0000)

/** Matrix 绿 — 备用终端绿色（当前未在 UI 中使用） */
val MatrixGreen = Color(0xFF00FF41)
val MatrixGreenDark = Color(0xFF003B00)
val MatrixGreenDim = Color(0xFF00AA2E)

/** 霓虹青色 — GlitchText 偏移层、特殊装饰 */
val NeonCyan = Color(0xFF00FFFF)
val NeonCyanDark = Color(0xFF003333)
/** 霓虹琥珀色 — 警告/次要强调 */
val NeonAmber = Color(0xFFFFB000)

/** 纯黑 — 最深背景色 */
val Black = Color(0xFF000000)
/** 极深黑 — 主要背景色（#010101） */
val DeepBlack = Color(0xFF010101)
val BlackAlt = Color(0xFF030303)
/** 深灰黑 — 卡片/表面背景色（#080808） */
val Surface = Color(0xFF080808)
val SurfaceAlt = Color(0xFF0C0C0C)
val Card = Color(0xFF0D0D0D)
val CardAlt = Color(0xFF111111)

/** 暗灰边框 — 卡片和面板的默认边框 */
val Border = Color(0xFF1A1A1A)
val BorderLight = Color(0xFF222222)

/** 主要文字 — 正文/标题白色（#E0E0E0） */
val TextPrimary = Color(0xFFE0E0E0)
/** 纯白文字 — 高亮/强调 (#FFFFFF) */
val TextBright = Color(0xFFFFFFFF)
/** 次要文字 — 描述/辅助信息（#888888） */
val TextDim = Color(0xFF888888)
/** 极暗文字 — 禁用/占位符（#444444） */
val TextMuted = Color(0xFF444444)

/** 琥珀色别名（兼容旧代码） */
val Amber = NeonAmber
/** 成功绿色（兼容旧代码） */
val Success = Color(0xFF00AA00)
val Info = Color(0xFF4488CC)

/** Material3 暗色主题映射 */
val DarkBackground = DeepBlack
val DarkSurface = Surface
val DarkSurfaceVariant = SurfaceAlt
val DarkOnBackground = TextPrimary
val DarkOnSurface = TextPrimary
val DarkOnSurfaceVariant = TextDim
val DarkOutline = BorderLight

/** Material3 亮色主题映射（当前未启用） */
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F0F0)
val LightOnBackground = Color(0xFF1A1A1A)
val LightOnSurface = Color(0xFF1A1A1A)
val LightOnSurfaceVariant = Color(0xFF666666)
val LightOutline = Color(0xFFCCCCCC)
