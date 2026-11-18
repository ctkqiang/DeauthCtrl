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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
        TerminalHeader("万能遥控 · FLIPPER TV", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            // Tab 切换
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("TV 万能遥控" to 0, "按键克隆库" to 1).forEach { (label, i) ->
                    val on = tab == i
                    Surface(
                        modifier = Modifier.weight(1f).clickable { haptic.hud(); tab = i },
                        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                        color = if (on) Red.copy(alpha = 0.14f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (on) Red.copy(alpha = 0.6f) else BorderDim),
                    ) {
                        Text(label, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (on) Red else Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
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
        TermPanel("候选库 · tv.ir (Flipper Zero 开源固件)") {
            val total = vm.tvButtons.values.sumOf { it.size }
            Text("按钮组 ${vm.tvButtons.size} 组 · 共 $total 条候选码", fontFamily = Mono, fontSize = 10.sp, color = Gold)
            Spacer(Modifier.height(3.dp))
            Text("支持协议: NEC/NECext/Samsung32/RC5/RC5X/RC6/SIRC/SIRC20;Kaseikyo/Pioneer/RCA/NEC42 候选将被跳过(如实计数)", fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
        }
        Spacer(Modifier.height(8.dp))

        // 6 大按钮
        val buttons = listOf(
            "Power" to "电源",
            "Vol_up" to "音量 +",
            "Vol_dn" to "音量 -",
            "Ch_next" to "频道 +",
            "Ch_prev" to "频道 -",
            "Mute" to "静音",
        )
        buttons.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (key, label) ->
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
                            Text(label, fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (active) Red else White)
                            Text("$count 候选", fontFamily = Mono, fontSize = 8.sp, color = Gray)
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
                "泛洪中 · ${flood.button} ${flood.index}/${flood.total} · ${flood.protocol} · 跳过 ${flood.skipped}",
                fontFamily = Mono, fontSize = 10.sp, color = Gold,
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); vm.stopFlood() },
                shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                color = Danger.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
            ) {
                Text("■ 停止轮发", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Danger, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        lastTx?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontFamily = Mono, fontSize = 9.sp, color = if (it.startsWith("[!]")) Danger else TacGreen)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "用法: 对准电视按「电源」,遥控器会像 Flipper 一样逐个尝试 317 个品牌的电源码(250ms/个),电视亮起时再次点击停止。全程约 80 秒。",
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
        val remoteName = editRemoteName.trim().ifBlank { "我的遥控" }
        val keyName = editKeyName.trim().ifBlank { "按键 ${remotes.size + 1}" }
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
            "[!] 手机没有红外接收器,无法像 Flipper 那样空中学习 —— 本库为手动录入键码的克隆库:从 tv.ir 查到候选码、从资料里抄录,或输入 RAW 脉冲(µs 逗号分隔)后永久保存并回放。",
            fontFamily = Sans, fontSize = 9.sp, color = Warn, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        TermPanel("新建按键") {
            TermInput("遥控名", editRemoteName, placeholder = "如 客厅电视") { editRemoteName = it }
            Spacer(Modifier.height(6.dp))
            TermInput("按键名", editKeyName, placeholder = "如 电源") { editKeyName = it }
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
                TermInput("地址(hex)", editAddr, placeholder = "08", modifier = Modifier.weight(1f)) { editAddr = it }
                TermInput("命令(hex)", editCmd, placeholder = "05", modifier = Modifier.weight(1f)) { editCmd = it }
            }
            Spacer(Modifier.height(6.dp))
            TermInput("RAW 脉冲(可选,µs)", editRaw, placeholder = "9000,4500,560,1690,…(留空用协议生成)") { editRaw = it }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); save() },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = Red.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
            ) {
                Text("保存按键 ▸", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        TermPanel("我的遥控 (${remotes.size})") {
            if (remotes.isEmpty()) {
                EmptyHint("保存的按键会按遥控分组显示在这里")
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
