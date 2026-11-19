package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.FlipperTvLib
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.FlipperTvViewModel

/**
 * Flipper 万能遥控 + 按键克隆库
 *
 * Tab 1「TV 万能遥控」: 移植 Flipper Zero 开源固件的 tv.ir 数据库
 * (Power 317 个品牌候选等,共 456 条),按键时按 Flipper 的泛洪语义逐个轮发
 * 候选码直到电视响应;不支持的协议(Kaseikyo/Pioneer/RCA/NEC42)如实跳过计数。
 *
 * Tab 2「按键克隆」: 个人遥控库 —— 手动录入键码(NEC/NECext/Samsung32/RC5/
 * RC5X/RC6/SIRC/SIRC20 或 RAW 脉冲)命名保存并回放。手机没有红外接收器,
 * 无法像 Flipper 那样空中学习,这是"键码克隆+库管理",区别已如实标注。
 */
@Composable
fun FlipperTvScreen(back: () -> Unit) {
    val vm: FlipperTvViewModel = viewModel()
    val flood by vm.flood.collectAsState()
    val lastTx by vm.lastTransmit.collectAsState()
    val haptic = LocalHapticFeedback.current
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.flipper_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            // Tab 切换
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(R.string.flipper_tab_tv to 0, R.string.flipper_tab_clone to 1).forEach { (labelRes, i) ->
                    val on = tab == i
                    Surface(
                        modifier = Modifier.weight(1f).clickable { haptic.hud(); tab = i },
                        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                        color = if (on) Red.copy(alpha = 0.14f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (on) Red.copy(alpha = 0.6f) else BorderDim),
                    ) {
                        Text(stringResource(labelRes), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (on) Red else Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (tab == 0) TvUniversalPanel(vm, flood, lastTx, haptic) else CloneLibraryPanel(vm, haptic)
        }
    }
}

/** TV 万能遥控面板 —— Flipper 泛洪语义 */
@Composable
private fun TvUniversalPanel(
    vm: FlipperTvViewModel,
    flood: xin.ctkqiang.deauthctrl.viewmodel.FloodState,
    lastTx: String?,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TermPanel(stringResource(R.string.flipper_panel_candidates)) {
            val total = vm.tvButtons.values.sumOf { it.size }
            Text(stringResource(R.string.flipper_candidate_counts, vm.tvButtons.size, total), fontFamily = Mono, fontSize = 10.sp, color = Gold)
            Spacer(Modifier.height(3.dp))
            Text(stringResource(R.string.flipper_supported_protocols), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
        }
        Spacer(Modifier.height(8.dp))

        // 6 大按钮
        val buttons = listOf(
            "Power" to R.string.flipper_btn_power,
            "Vol_up" to R.string.flipper_btn_vol_up,
            "Vol_dn" to R.string.flipper_btn_vol_down,
            "Ch_next" to R.string.flipper_btn_ch_next,
            "Ch_prev" to R.string.flipper_btn_ch_prev,
            "Mute" to R.string.flipper_btn_mute,
        )
        buttons.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (key, labelRes) ->
                    val active = flood.running && flood.button == key
                    val count = vm.tvButtons[key]?.size ?: 0
                    Surface(
                        modifier = Modifier.weight(1f).clickable {
                            haptic.hud()
                            vm.flood(key)
                        },
                        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                        color = if (active) Red.copy(alpha = 0.18f) else SurfaceBg,
                        border = BorderStroke(1.5.dp, if (active) Red.copy(alpha = 0.8f) else BorderDim),
                    ) {
                        Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(labelRes), fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (active) Red else White)
                            Text(stringResource(R.string.flipper_candidate_count, count), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }

        if (flood.running) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.flipper_flood_status, flood.button, flood.index, flood.total, flood.protocol, flood.skipped),
                fontFamily = Mono, fontSize = 10.sp, color = Gold,
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); vm.stopFlood() },
                shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                color = Danger.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
            ) {
                Text(stringResource(R.string.flipper_stop_flood), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Danger, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        lastTx?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontFamily = Mono, fontSize = 9.sp, color = if (it.startsWith("[!]")) Danger else TacGreen)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.flipper_usage),
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
    }
}

/** 按键克隆库面板 —— 个人遥控管理(手动录入键码,非空中学习) */
@Composable
private fun CloneLibraryPanel(vm: FlipperTvViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    val context = LocalContext.current
    var remotes by remember { mutableStateOf(FlipperTvLib.loadRemotes(context)) }
    var editRemoteName by remember { mutableStateOf("") }
    var editKeyName by remember { mutableStateOf("") }
    var editProtocol by remember { mutableStateOf("NEC") }
    var editAddr by remember { mutableStateOf("") }
    var editCmd by remember { mutableStateOf("") }
    var editRaw by remember { mutableStateOf("") }

    fun save() {
        val addr = editAddr.trim().toIntOrNull(16) ?: 0
        val cmd = editCmd.trim().toIntOrNull(16) ?: 0
        val remoteName = editRemoteName.trim().ifBlank { context.getString(R.string.flipper_default_remote_name) }
        val keyName = editKeyName.trim().ifBlank { context.getString(R.string.flipper_default_key_name, remotes.size + 1) }
        val key = FlipperTvLib.CloneKey(keyName, editProtocol, addr, cmd, editRaw.trim())
        val existing = remotes.firstOrNull { it.name == remoteName }
        remotes = if (existing != null) {
            remotes.map { if (it.name == remoteName) it.copy(keys = it.keys + key) else it }
        } else {
            remotes + FlipperTvLib.CloneRemote(remoteName, listOf(key))
        }
        FlipperTvLib.saveRemotes(context, remotes)
        editKeyName = ""; editAddr = ""; editCmd = ""; editRaw = ""
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.flipper_clone_warning),
            fontFamily = Sans, fontSize = 9.sp, color = Warn, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.flipper_panel_new_key)) {
            TermInput(stringResource(R.string.flipper_remote_name_label), editRemoteName, placeholder = stringResource(R.string.flipper_remote_name_placeholder)) { editRemoteName = it }
            Spacer(Modifier.height(6.dp))
            TermInput(stringResource(R.string.flipper_key_name_label), editKeyName, placeholder = stringResource(R.string.flipper_key_name_placeholder)) { editKeyName = it }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("NEC", "NECext", "Samsung32", "RC5", "RC5X", "RC6", "SIRC", "SIRC20").forEach { p ->
                    val on = editProtocol == p
                    Surface(
                        modifier = Modifier.clickable { haptic.hud(); editProtocol = p },
                        shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                        color = if (on) Red.copy(alpha = 0.14f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (on) Red.copy(alpha = 0.6f) else BorderDim),
                    ) {
                        Text(p, fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (on) Red else Gray, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TermInput(stringResource(R.string.flipper_address_label), editAddr, placeholder = "08", modifier = Modifier.weight(1f)) { editAddr = it }
                TermInput(stringResource(R.string.flipper_command_label), editCmd, placeholder = "05", modifier = Modifier.weight(1f)) { editCmd = it }
            }
            Spacer(Modifier.height(6.dp))
            TermInput(stringResource(R.string.flipper_raw_label), editRaw, placeholder = stringResource(R.string.flipper_raw_placeholder)) { editRaw = it }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); save() },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = Red.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
            ) {
                Text(stringResource(R.string.flipper_save_key), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.flipper_panel_my_remotes, remotes.size)) {
            if (remotes.isEmpty()) {
                EmptyHint(stringResource(R.string.flipper_empty_remotes))
            } else {
                remotes.forEach { r ->
                    Spacer(Modifier.height(4.dp))
                    Text(r.name, fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        r.keys.forEach { k ->
                            Surface(
                                modifier = Modifier.clickable {
                                    haptic.hud()
                                    if (k.rawUs.isNotBlank()) {
                                        val pat = k.rawUs.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
                                        if (pat.isNotEmpty()) {
                                            // 复用 IrRemoteManager raw 发射:直接经 vm 的 transmit 走不通 raw,由屏幕直连 manager
                                            xin.ctkqiang.deauthctrl.manager.IrRemoteManager(context).transmitRaw(38_000, pat, label = k.name)
                                        }
                                    } else {
                                        vm.transmit(k.protocol, k.address, k.command)
                                    }
                                },
                                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                color = SurfaceBg,
                                border = BorderStroke(1.dp, BorderDim),
                            ) {
                                Text(k.name.take(8), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

private val Red = Danger
private val BorderDim = xin.ctkqiang.deauthctrl.ui.components.BorderDim
