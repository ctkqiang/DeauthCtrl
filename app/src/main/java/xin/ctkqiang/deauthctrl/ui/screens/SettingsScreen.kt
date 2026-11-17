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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("操作员设置", back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(14.dp),
        ) {
            // ─── 操作员身份 ───
            TermPanel("操作员身份 · OPERATOR ID") {
                TermInput("呼号 / 用户名", username, placeholder = "输入你的操作员呼号") { vm.setUsername(it) }
                Spacer(Modifier.height(6.dp))
                Text(
                    "主界面头部将显示：操作员: ${username.ifBlank { "未命名" }}",
                    fontFamily = Mono, fontSize = 9.sp, color = Gray,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ─── 队伍与主题 ───
            TermPanel("队伍与主题 · SQUAD & THEME (${TEAM_THEMES.size})") {
                Text("选择队伍即切换全应用战术配色 —— 所有页面立即生效", fontFamily = Sans, fontSize = 10.sp, color = Gray)
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
            TermPanel("战术反馈 · HAPTIC FEEDBACK") {
                SettingSwitchRow(
                    title = "触觉反馈",
                    desc = "全应用按钮、执行、告警的震动反馈主开关",
                    checked = haptics,
                    onChecked = { vm.setHaptics(it) },
                )
                Spacer(Modifier.height(6.dp))
                SettingSwitchRow(
                    title = "雷达红点警报",
                    desc = "雷达发现新目标时震动提醒（需开启触觉反馈）",
                    checked = radarAlert,
                    onChecked = { vm.setRadarAlert(it) },
                )
            }

            Spacer(Modifier.height(10.dp))

            // ─── 系统信息（原关于内容） ───
            StarBadge(56.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Text(
                "哪吒网络安全终端",
                fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 20.sp,
                color = White, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                "◆ DEAUTHCTRL v1.0 ◆",
                fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = Gold, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            TermPanel("系统信息") {
                InfoLine("作者", "钟智强 (ctkqiang)")
                InfoLine("代号", "哪吒网络安全")
                InfoLine("邮箱", "ctkqiang@dingtalk.com")
                InfoLine("仓库", "gitcode.com/ctkqiang_sr/DeauthCtrl")
                InfoLine("架构", "MVVM + StateFlow + Compose")
                InfoLine("许可", "授权安全研究")
            }
            Spacer(Modifier.height(12.dp))
            Text("$ uname -a", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
            Text("  DeauthCtrl v1.0  |  中国红客  |  国产自主  |  安全可控", fontFamily = Sans, fontSize = 11.sp, color = White)
            Spacer(Modifier.height(4.dp))
            Text("$ whoami", fontFamily = Mono, fontSize = 11.sp, color = Red, fontWeight = FontWeight.Bold)
            Text("  ${username.ifBlank { "哪吒网络安全" }} (ctkqiang)", fontFamily = Sans, fontSize = 11.sp, color = White)
            Spacer(Modifier.height(12.dp))
            Text(
                "本应用仅供授权安全研究与教育用途，请勿用于非法活动",
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
