package xin.ctkqiang.deauthctrl.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.toArgb

/**
 * 桌面启动图标换色
 *
 * 主入口通过 13 个 activity-alias 暴露(见 AndroidManifest),
 * 每个 alias 对应一种图标颜色变体。切换队伍主题时按主题主色的
 * 色相选择最近的变体:启用该 alias、停用其余 —— 桌面图标随主题变色。
 * 低饱和主题(极地白 / 钛银 / 光子白)统一落到灰变体。
 *
 * 变体切换对运行中的任务无影响(setComponentEnabledSetting + DONT_KILL_APP),
 * 下次从桌面冷启动即走新 alias;多数桌面会立即刷新图标。
 */
object LauncherIcon {

    /** 图标颜色变体:色相 + alias 类名。hue 为负表示灰变体 */
    private class IconVariant(val id: String, val hue: Float, val alias: String)

    private val VARIANTS = listOf(
        IconVariant("red", 0f, "MainActivityLaunchRed"),
        IconVariant("orange", 20f, "MainActivityLaunchOrange"),
        IconVariant("amber", 42f, "MainActivityLaunchAmber"),
        IconVariant("lime", 80f, "MainActivityLaunchLime"),
        IconVariant("green", 130f, "MainActivityLaunchGreen"),
        IconVariant("teal", 165f, "MainActivityLaunchTeal"),
        IconVariant("cyan", 195f, "MainActivityLaunchCyan"),
        IconVariant("blue", 220f, "MainActivityLaunchBlue"),
        IconVariant("indigo", 258f, "MainActivityLaunchIndigo"),
        IconVariant("purple", 275f, "MainActivityLaunchPurple"),
        IconVariant("magenta", 320f, "MainActivityLaunchMagenta"),
        IconVariant("pink", 342f, "MainActivityLaunchPink"),
        IconVariant("gray", -1f, "MainActivityLaunchGray"),
    )

    /** 饱和度低于此值的主题视为无彩色 → 灰变体 */
    private const val GRAY_SATURATION_THRESHOLD = 0.12f

    /** 按色板主色启用对应图标变体;API 26 以下无 adaptive/alias 图标,保持默认 */
    fun apply(context: Context, palette: HudPalette) {
        if (Build.VERSION.SDK_INT < 26) return
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(palette.primary.toArgb(), hsv)
        val chosen = if (hsv[1] < GRAY_SATURATION_THRESHOLD) {
            VARIANTS.first { it.id == "gray" }
        } else {
            VARIANTS.filter { it.hue >= 0f }.minBy { v ->
                val d = Math.abs(hsv[0] - v.hue)
                Math.min(d, 360f - d)
            }
        }
        val pm = context.packageManager
        val pkg = context.packageName
        Log.i(TAG, "apply: team=${palette.id} chosen=${chosen.id} pkg=$pkg")
        VARIANTS.forEach { v ->
            runCatching {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, "$pkg.${v.alias}"),
                    if (v === chosen) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
                Log.i(TAG, "alias ${v.alias} -> ${if (v === chosen) "ENABLED" else "DISABLED"} ok")
            }.onFailure {
                Log.e(TAG, "alias ${v.alias} failed", it)
            }
        }
    }

    private const val TAG = "LauncherIcon"
}
