package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.LocaleManager
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.StarBadge
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacticalCorners
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.ui.theme.HudPalette
import xin.ctkqiang.deauthctrl.ui.theme.TEAM_THEMES
import xin.ctkqiang.deauthctrl.viewmodel.MAP_STYLE_COLOR
import xin.ctkqiang.deauthctrl.viewmodel.MAP_STYLE_MONO
import xin.ctkqiang.deauthctrl.viewmodel.MAP_STYLE_NIGHT
import xin.ctkqiang.deauthctrl.viewmodel.SettingsViewModel

/**
 * 操作员设置页 —— 原「关于」页的全面升级
 *
 * 分区：
 * 1. 操作员身份 —— 用户名呼号（主界面头部行实时显示）
 * 2. 队伍与主题 —— 24 支队伍色板网格，切换即时应用到全应用
 * 3. 战术反馈 —— 全局触觉主开关 + 雷达红点警报独立开关
 * 4. 系统信息 —— 原关于信息（作者/代号/邮箱/仓库/架构/许可）
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, back: () -> Unit) {
    val username by vm.username.collectAsState()
    val teamId by vm.teamId.collectAsState()
    val haptics by vm.hapticsEnabled.collectAsState()
    val radarAlert by vm.radarAlertEnabled.collectAsState()
    val mapStyle by vm.mapStyle.collectAsState()
    val rssiEnv by vm.rssiEnv.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.settings_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(14.dp),
        ) {
            // ─── 操作员身份 ───
            TermPanel(stringResource(R.string.settings_operator_section)) {
                TermInput(
                    stringResource(R.string.settings_callsign_label),
                    username,
                    placeholder = stringResource(R.string.settings_callsign_placeholder),
                ) { vm.setUsername(it) }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        R.string.settings_callsign_preview,
                        username.ifBlank { stringResource(R.string.settings_unnamed) },
                    ),
                    fontFamily = Mono, fontSize = 9.sp, color = Gray,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ─── 队伍与主题 ───
            TermPanel(stringResource(R.string.settings_team_section, TEAM_THEMES.size)) {
                Text(stringResource(R.string.settings_team_desc), fontFamily = Sans, fontSize = 10.sp, color = Gray)
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(TEAM_THEMES, key = { it.id }) { team ->
                        TeamSwatch(
                            team = team,
                            selected = team.id == teamId,
                            onClick = {
                                haptic.hud()
                                vm.setTeam(team.id)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ─── 战术反馈 ───
            TermPanel(stringResource(R.string.settings_haptic_section)) {
                SettingSwitchRow(
                    title = stringResource(R.string.settings_haptics_title),
                    desc = stringResource(R.string.settings_haptics_desc),
                    checked = haptics,
                    onChecked = { vm.setHaptics(it) },
                )
                Spacer(Modifier.height(6.dp))
                SettingSwitchRow(
                    title = stringResource(R.string.settings_radar_alert_title),
                    desc = stringResource(R.string.settings_radar_alert_desc),
                    checked = radarAlert,
                    onChecked = { vm.setRadarAlert(it) },
                )
            }

            Spacer(Modifier.height(10.dp))

            // ─── 战术地图 ───
            TermPanel(stringResource(R.string.settings_map_section)) {
                Text(stringResource(R.string.settings_map_desc), fontFamily = Sans, fontSize = 10.sp, color = Gray)
                Spacer(Modifier.height(8.dp))
                listOf(
                    MAP_STYLE_MONO to (R.string.settings_map_mono to R.string.settings_map_mono_desc),
                    MAP_STYLE_NIGHT to (R.string.settings_map_night to R.string.settings_map_night_desc),
                    MAP_STYLE_COLOR to (R.string.settings_map_color to R.string.settings_map_color_desc),
                ).forEach { (style, texts) ->
                    SettingChoiceRow(
                        title = stringResource(texts.first),
                        desc = stringResource(texts.second),
                        selected = mapStyle == style,
                        onClick = {
                            haptic.hud()
                            vm.setMapStyle(style)
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            Spacer(Modifier.height(10.dp))

            // ─── 距离模型环境(btrpa-scan 路径损耗预设) ───
            TermPanel(stringResource(R.string.settings_rssi_env_section)) {
                Text(stringResource(R.string.settings_rssi_env_desc), fontFamily = Sans, fontSize = 10.sp, color = Gray)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "free" to (R.string.settings_rssi_free to R.string.settings_rssi_free_desc),
                    "outdoor" to (R.string.settings_rssi_outdoor to R.string.settings_rssi_outdoor_desc),
                    "indoor" to (R.string.settings_rssi_indoor to R.string.settings_rssi_indoor_desc),
                ).forEach { (env, texts) ->
                    SettingChoiceRow(
                        title = stringResource(texts.first),
                        desc = stringResource(texts.second),
                        selected = rssiEnv == env,
                        onClick = {
                            haptic.hud()
                            vm.setRssiEnv(env)
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // ─── 语言 / LANGUAGE ───
            TermPanel(stringResource(R.string.settings_language_section)) {
                Text(stringResource(R.string.settings_language_desc), fontFamily = Sans, fontSize = 10.sp, color = Gray)
                Spacer(Modifier.height(8.dp))
                val activity = LocalContext.current.findActivity()
                val currentLang = LocalContext.current.let { LocaleManager.current(it) }
                listOf(
                    LocaleManager.LANG_SYSTEM to (R.string.language_system to R.string.language_system_desc),
                    LocaleManager.LANG_ZH to (R.string.language_zh to R.string.language_zh_desc),
                    LocaleManager.LANG_EN to (R.string.language_en to R.string.language_en_desc),
                ).forEach { (lang, texts) ->
                    SettingChoiceRow(
                        title = stringResource(texts.first),
                        desc = stringResource(texts.second),
                        selected = currentLang == lang,
                        onClick = {
                            haptic.hud()
                            if (activity != null && currentLang != lang) LocaleManager.apply(activity, lang)
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // ─── 系统信息（原关于内容） ───
            StarBadge(56.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.app_name),
                fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 20.sp,
                color = White, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                "◆ DEAUTHCTRL v1.0 ◆",
                fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = Gold, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            TermPanel(stringResource(R.string.settings_sysinfo_section)) {
                InfoLine(stringResource(R.string.settings_info_author), "钟智强 (ctkqiang)")
                InfoLine(stringResource(R.string.settings_info_codename), stringResource(R.string.settings_info_codename_value))
                InfoLine(stringResource(R.string.settings_info_email), "ctkqiang@dingtalk.com")
                InfoLine(stringResource(R.string.settings_info_repo), "gitcode.com/ctkqiang_sr/DeauthCtrl")
                InfoLine(stringResource(R.string.settings_info_arch), "MVVM + StateFlow + Compose")
                InfoLine(stringResource(R.string.settings_info_license), stringResource(R.string.settings_info_license_value))
            }
            Spacer(Modifier.height(12.dp))
            Text("$ uname -a", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.settings_banner), fontFamily = Sans, fontSize = 11.sp, color = White)
            Spacer(Modifier.height(4.dp))
            Text("$ whoami", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
            Text(
                stringResource(
                    R.string.settings_whoami,
                    username.ifBlank { stringResource(R.string.settings_info_codename_value) },
                ),
                fontFamily = Sans, fontSize = 11.sp, color = White,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_disclaimer),
                fontFamily = Sans, fontSize = 10.sp, color = Gray, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 队伍色板卡片 —— 色块 + 队名；选中态带金框锁定角 */
@Composable
private fun TeamSwatch(team: HudPalette, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, if (selected) Gold.copy(alpha = 0.9f) else team.primary.copy(alpha = 0.35f)),
    ) {
        Box {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(22.dp).clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                        .background(team.primary),
                )
                Spacer(Modifier.height(5.dp))
                Text(team.teamName, fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) Gold else White)
                Text(team.themeName, fontFamily = Mono, fontSize = 8.sp, color = Gray, maxLines = 1)
            }
            if (selected) {
                TacticalCorners(
                    color = Gold,
                    modifier = Modifier.matchParentSize(),
                    len = 7.dp,
                    stroke = 1.2.dp,
                )
            }
        }
    }
}

/** 开关行 —— 标题 + 描述 + 战术色开关 */
@Composable
private fun SettingSwitchRow(title: String, desc: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White)
            Text(desc, fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Red.copy(alpha = 0.7f),
                uncheckedThumbColor = Gray,
                uncheckedTrackColor = SurfaceBg,
                uncheckedBorderColor = Gray.copy(alpha = 0.4f),
            ),
        )
    }
}

/** 设置页键值行 */
@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontFamily = Mono, fontSize = 10.sp, color = Gray, modifier = Modifier.width(72.dp))
        Text(value, fontFamily = Mono, fontSize = 10.sp, color = White, modifier = Modifier.weight(1f))
    }
}

/** 单选行 —— 标题 + 描述 + 选中指示(语言等单选项) */
@Composable
private fun SettingChoiceRow(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White)
            Text(desc, fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (selected) "[*]" else "[ ]",
            fontFamily = Mono, fontSize = 12.sp,
            color = if (selected) Gold else Gray,
        )
    }
}

/** 从 ContextWrapper 链上解出宿主 Activity(Compose 的 LocalContext 可能被包装) */
private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
