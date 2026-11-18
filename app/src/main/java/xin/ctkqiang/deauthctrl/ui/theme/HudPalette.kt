package xin.ctkqiang.deauthctrl.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
// 注意：本文件位于 ui.theme 包，同包的 Color.kt 里存在同名旧色（val）。
// 用别名导入 components 的 var，避免同包声明遮蔽导致 apply() 无法赋值。
import xin.ctkqiang.deauthctrl.ui.components.Red as HudRed
import xin.ctkqiang.deauthctrl.ui.components.RedBright as HudRedBright
import xin.ctkqiang.deauthctrl.ui.components.RedDim as HudRedDim
import xin.ctkqiang.deauthctrl.ui.components.Danger as HudDanger
import xin.ctkqiang.deauthctrl.ui.components.Warn as HudWarn
import xin.ctkqiang.deauthctrl.ui.components.Gold as HudGold
import xin.ctkqiang.deauthctrl.ui.components.GoldDim as HudGoldDim
import xin.ctkqiang.deauthctrl.ui.components.TacGreen as HudTacGreen
import xin.ctkqiang.deauthctrl.ui.components.TacGreenDim as HudTacGreenDim
import xin.ctkqiang.deauthctrl.ui.components.White as HudWhite
import xin.ctkqiang.deauthctrl.ui.components.Gray as HudGray
import xin.ctkqiang.deauthctrl.ui.components.GrayDark as HudGrayDark
import xin.ctkqiang.deauthctrl.ui.components.Dark as HudDark
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg as HudSurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt as HudSurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.BorderDim as HudBorderDim

/**
 * 全息 HUD 战术色板
 *
 * 整支应用的配色单一来源。每支「队伍」对应一套色板，通过 [HudThemeState.apply]
 * 同步到 TerminalKit 的全局颜色变量（`Red`/`TacGreen`/`Dark`…），
 * 所有界面无需改动即整体换色。
 *
 * ## 色板派生规则
 * 只给定一个基准色（base），其余槽位全部从它的 HSV 派生：
 * - bg/surface/border: 同色相、极低明度的深色系 —— 保持暗色战术底
 * - bright/dim: 基准色向白/向暗两端的延伸 —— 强调与弱化
 * - text/gray: 带轻微色相的近白与灰 —— 文字在深底上保持可读
 * - danger: 告警红；基准色本身是红色系时换用红橙，保证对比
 */
data class HudPalette(
    val id: String,
    /** 队伍名 —— 主界面操作员行与设置页展示 */
    val teamName: String,
    /** 主题名 —— 色板的中文称呼 */
    val themeName: String,
    val primary: Color,
    val bright: Color,
    val dim: Color,
    val danger: Color,
    val warn: Color,
    val gold: Color,
    val goldDim: Color,
    val tac: Color,
    val tacDim: Color,
    val text: Color,
    val gray: Color,
    val grayDark: Color,
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
)

/** 从基准色派生完整色板 */
private fun palette(id: String, teamName: String, themeName: String, base: Color): HudPalette {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(base.toArgb(), hsv)
    val hue = hsv[0]
    val white = Color.White

    // 基准色本身落在红色系（345°-25°）时，告警红改用红橙，避免红底红字
    val danger = if (hue >= 345f || hue <= 25f) Color(0xFFFF8A5C) else Color(0xFFFF4D5E)

    return HudPalette(
        id = id,
        teamName = teamName,
        themeName = themeName,
        primary = base,
        bright = lerp(base, white, 0.45f),
        dim = Color.hsv(hue, 0.75f, 0.36f),
        danger = danger,
        warn = Color(0xFFFFB547),
        gold = lerp(base, white, 0.28f),
        goldDim = Color.hsv(hue, 0.50f, 0.28f),
        tac = base,
        tacDim = Color.hsv(hue, 0.70f, 0.25f),
        text = lerp(white, base, 0.06f),
        gray = Color.hsv(hue, 0.18f, 0.66f),
        grayDark = Color.hsv(hue, 0.25f, 0.40f),
        bg = Color.hsv(hue, 0.62f, 0.055f),
        surface = Color.hsv(hue, 0.55f, 0.11f),
        surfaceAlt = Color.hsv(hue, 0.50f, 0.15f),
        border = Color.hsv(hue, 0.50f, 0.26f),
    )
}

/** 40 支可选队伍 —— 每支队伍即一套全应用主题 */
val TEAM_THEMES: List<HudPalette> = listOf(
    palette("azure", "蓝队", "全息青", Color(0xFF35C9FF)),
    palette("delta", "蓝队", "三角洲蓝", Color(0xFF2979FF)),
    palette("bolt", "蓝队", "电光蓝", Color(0xFF00B0FF)),
    palette("ice", "蓝队", "冰晶蓝", Color(0xFF4DD0E1)),
    palette("crimson", "红队", "猩红", Color(0xFFFF3B30)),
    palette("blood", "红队", "深红", Color(0xFFD50000)),
    palette("neonpink", "粉队", "霓虹粉", Color(0xFFFF4FD8)),
    palette("sakura", "粉队", "樱花粉", Color(0xFFFF7BAC)),
    palette("lava", "橙队", "熔岩橙", Color(0xFFFF9100)),
    palette("amber", "橙队", "琥珀", Color(0xFFFFB547)),
    palette("arctic", "白队", "极地白", Color(0xFFE8F4FF)),
    palette("ghost", "灰队", "幽灵灰", Color(0xFF9FA8B8)),
    palette("matrix", "绿队", "矩阵绿", Color(0xFF00FF41)),
    palette("military", "绿队", "军绿", Color(0xFF8BC34A)),
    palette("jade", "绿队", "翡翠", Color(0xFF00C853)),
    palette("imperial", "紫队", "帝王紫", Color(0xFFB388FF)),
    palette("royal", "紫队", "皇家紫", Color(0xFF651FFF)),
    palette("gold", "金队", "灿金", Color(0xFFFFD54F)),
    palette("coral", "红队", "珊瑚红", Color(0xFFFF6E6E)),
    palette("teal", "青队", "青碧", Color(0xFF26A69A)),
    palette("sky", "蓝队", "天青", Color(0xFF81D4FA)),
    palette("midnight", "蓝队", "午夜蓝", Color(0xFF3D5AFE)),
    palette("titanium", "灰队", "钛银", Color(0xFFB0BEC5)),
    palette("graphite", "灰队", "石墨", Color(0xFF607D8B)),
    palette("magenta", "粉队", "品红", Color(0xFFE91E63)),
    palette("orchid", "紫队", "兰花紫", Color(0xFFBA68C8)),
    palette("indigo", "蓝队", "靛蓝", Color(0xFF3949AB)),
    palette("cyan", "青队", "纯青", Color(0xFF00E5FF)),
    palette("lime", "绿队", "酸橙", Color(0xFFC6FF00)),
    palette("emerald", "绿队", "祖母绿", Color(0xFF00C853)),
    palette("chartreuse", "黄队", "黄绿", Color(0xFFAEEA00)),
    palette("tangerine", "橙队", "橘红", Color(0xFFFF7043)),
    palette("sunset", "红队", "落日红", Color(0xFFFF5252)),
    palette("rose", "粉队", "玫瑰金", Color(0xFFF48FB1)),
    palette("violet", "紫队", "紫罗兰", Color(0xFF7C4DFF)),
    palette("steel", "蓝队", "钢铁蓝", Color(0xFF546E7A)),
    palette("plasma", "青队", "等离子", Color(0xFF00E676)),
    palette("photon", "白队", "光子白", Color(0xFFF5F5F5)),
    palette("hazard", "黄队", "危险黄", Color(0xFFFFEA00)),
)

/**
 * 全局 HUD 主题状态
 *
 * 把当前色板写入 TerminalKit 的全局颜色变量。切队后 MainActivity 根部的
 * `key(teamId)` 强制整棵 UI 树重组，重组时所有界面读到的就是新颜色。
 * 同时也持有全局触觉开关（hud() 扩展函数据此放行）。
 */
object HudThemeState {
    @Volatile var current: HudPalette = TEAM_THEMES.first(); private set

    /** 全局触觉主开关 —— 设置页可整体关闭 */
    @Volatile var hapticsEnabled: Boolean = true

    /** 雷达红点警报触觉 —— 独立于主开关的二级门控 */
    @Volatile var radarAlertEnabled: Boolean = true

    /** 按队伍 id 应用色板；未知 id 回退到默认蓝队 */
    fun applyTeam(id: String): HudPalette {
        val p = TEAM_THEMES.firstOrNull { it.id == id } ?: TEAM_THEMES.first()
        apply(p)
        return p
    }

    /** 直接把色板写入全局颜色变量 */
    fun apply(p: HudPalette) {
        current = p
        HudRed = p.primary
        HudRedBright = p.bright
        HudRedDim = p.dim
        HudDanger = p.danger
        HudWarn = p.warn
        HudGold = p.gold
        HudGoldDim = p.goldDim
        HudTacGreen = p.tac
        HudTacGreenDim = p.tacDim
        HudWhite = p.text
        HudGray = p.gray
        HudGrayDark = p.grayDark
        HudDark = p.bg
        HudSurfaceBg = p.surface
        HudSurfaceAlt = p.surfaceAlt
        HudBorderDim = p.border
    }
}
