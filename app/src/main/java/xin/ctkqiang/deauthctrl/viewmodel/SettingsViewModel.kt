package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.ui.theme.HudThemeState
import xin.ctkqiang.deauthctrl.ui.theme.LauncherIcon
import xin.ctkqiang.deauthctrl.ui.theme.TEAM_THEMES

/** 操作员偏好键 —— 与免责声明共用 deauthctrl_prefs 文件 */
const val KEY_OPERATOR_NAME = "operator_name"
const val KEY_TEAM_ID = "team_id"
const val KEY_HAPTICS_ENABLED = "haptics_enabled"
const val KEY_RADAR_ALERT_ENABLED = "radar_alert_enabled"

/**
 * 操作员设置 ViewModel
 *
 * 用户偏好（用户名 / 队伍主题 / 触觉反馈）的持久化与状态流。
 * 所有写入即时落盘 SharedPreferences；队伍切换同时调用
 * [HudThemeState.applyTeam] 把新色板写入全局颜色，MainActivity
 * 根部的 key(teamId) 随即强制全树重组完成换肤。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("deauthctrl_prefs", Context.MODE_PRIVATE)

    private val _username = MutableStateFlow(
        prefs.getString(KEY_OPERATOR_NAME, "哪吒") ?: "哪吒"
    )
    val username: StateFlow<String> = _username

    private val _teamId = MutableStateFlow(
        prefs.getString(KEY_TEAM_ID, TEAM_THEMES.first().id) ?: TEAM_THEMES.first().id
    )
    val teamId: StateFlow<String> = _teamId

    private val _haptics = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS_ENABLED, true))
    val hapticsEnabled: StateFlow<Boolean> = _haptics

    private val _radarAlert = MutableStateFlow(prefs.getBoolean(KEY_RADAR_ALERT_ENABLED, true))
    val radarAlertEnabled: StateFlow<Boolean> = _radarAlert

    /** 设置操作员呼号 */
    fun setUsername(name: String) {
        _username.value = name
        prefs.edit().putString(KEY_OPERATOR_NAME, name).apply()
    }

    /** 切换队伍 —— 写入偏好、应用全应用色板,并同步桌面图标变体 */
    fun setTeam(id: String) {
        _teamId.value = id
        prefs.edit().putString(KEY_TEAM_ID, id).apply()
        val palette = HudThemeState.applyTeam(id)
        LauncherIcon.apply(getApplication(), palette)
    }

    /** 全局触觉主开关 */
    fun setHaptics(enabled: Boolean) {
        _haptics.value = enabled
        prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
        HudThemeState.hapticsEnabled = enabled
    }

    /** 雷达红点警报触觉开关 */
    fun setRadarAlert(enabled: Boolean) {
        _radarAlert.value = enabled
        prefs.edit().putBoolean(KEY_RADAR_ALERT_ENABLED, enabled).apply()
        HudThemeState.radarAlertEnabled = enabled
    }
}
