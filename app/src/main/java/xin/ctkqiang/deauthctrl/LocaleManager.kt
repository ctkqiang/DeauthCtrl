package xin.ctkqiang.deauthctrl

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用内语言管理 —— 默认跟随系统,可在设置页覆盖为中文 / English
 *
 * 实现要点:
 * 1. 偏好持久化在既有 prefs 文件("deauthctrl_prefs"),key 为 app_language,
 *    取值 "system" / "zh" / "en"。
 * 2. [wrap] 在 Application.attachBaseContext 阶段把 locale 注入 base context,
 *    因此 Activity、Service(对讲机通知)、Compose 的 stringResource 全部生效,
 *    不修改全局系统语言。
 * 3. 切换语言由 [apply] 落盘后 recreate Activity —— 整棵 UI 树重建,
 *    Compose 重新解析所有 stringResource,无需逐页刷新。
 * 4. Locale.setDefault 同步更新,保证 Java 侧 SimpleDateFormat / 排序等
 *    行为与界面语言一致(界面日期格式本身仍显式指定 Locale.US)。
 */
object LocaleManager {

    const val PREFS_NAME = "deauthctrl_prefs"
    const val KEY_LANGUAGE = "app_language"

    const val LANG_SYSTEM = "system"
    const val LANG_ZH = "zh"
    const val LANG_EN = "en"

    /** 语言码 → Locale;system 不在此表,原样跟随设备 */
    private val OVERRIDES = mapOf(
        LANG_ZH to Locale.SIMPLIFIED_CHINESE,
        LANG_EN to Locale.ENGLISH,
    )

    /** 进程启动时捕获的设备原始默认语言 —— 切回「跟随系统」用它恢复,而不是被覆盖过的 Locale.getDefault() */
    @Volatile
    private var deviceDefaultLocale: Locale? = null

    /** 当前持久化的语言偏好(默认 system) */
    fun current(base: Context): String =
        base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM

    /**
     * 按持久化偏好包装 context。
     *
     * 偏好为 system 时用设备原始默认语言原样返回 base —— 资源回退链由系统
     * locale 决定(values-zh-rCN 命中中文设备,values 英文 default 兜底)。
     *
     * 注意:必须在 **Application 与 Activity 两层**都调用。Application 层只
     * 在进程启动时执行一次,语言切换后 recreate 的 Activity 必须自己重新
     * 包装,否则新偏好永远不生效(旧 bug:选了 English 界面仍是中文)。
     */
    fun wrap(base: Context): Context {
        if (deviceDefaultLocale == null) deviceDefaultLocale = Locale.getDefault()
        val lang = current(base)
        val locale = OVERRIDES[lang] ?: deviceDefaultLocale ?: Locale.getDefault()
        Locale.setDefault(locale)
        if (lang == LANG_SYSTEM && locale.toLanguageTag() == (deviceDefaultLocale ?: locale).toLanguageTag()) {
            return base
        }
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * 落盘语言偏好并立即重启当前 Activity 生效。
     *
     * 注意:必须 recreate 而不是只更新 LocalContext —— Header、通知文案、
     * 已入栈页面等全部要重读资源,重建是唯一干净的路径。
     */
    fun apply(activity: Activity, lang: String) {
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, lang).apply()
        Locale.setDefault(OVERRIDES[lang] ?: deviceDefaultLocale ?: Locale.getDefault())
        activity.recreate()
    }
}
