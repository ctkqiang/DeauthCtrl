package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.BeaconManager
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud

/** 紧急信标 —— SOS 自动循环(闪光/蜂鸣/震动),可设自动停止 */
@Composable
fun BeaconScreen(back: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val manager = remember { BeaconManager(context) }
    val state by manager.state.collectAsState()

    DisposableEffect(manager) {
        onDispose { manager.release() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.beacon_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            // 状态
            Surface(
                Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = if (state.running) Danger.copy(alpha = 0.18f) else SurfaceBg,
                border = BorderStroke(1.dp, if (state.running) Danger else Gray.copy(alpha = 0.4f)),
            ) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (state.running) stringResource(R.string.beacon_running) else stringResource(R.string.beacon_idle),
                        fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (state.running) Danger else Gray,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.beacon_cycles, state.cycles), fontFamily = Mono, fontSize = 10.sp, color = Gold)
                }
            }
            Spacer(Modifier.height(8.dp))

            // 主按钮
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    haptic.hud()
                    if (state.running) manager.stop() else manager.start()
                },
                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                color = if (state.running) Danger.copy(alpha = 0.22f) else TacGreen.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, if (state.running) Danger else TacGreen.copy(alpha = 0.7f)),
            ) {
                Text(
                    if (state.running) stringResource(R.string.beacon_stop) else stringResource(R.string.beacon_start),
                    fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.Black,
                    color = if (state.running) Danger else TacGreen,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            // 模式开关
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(state.flash, stringResource(R.string.beacon_flash)) { haptic.hud(); manager.toggleFlash() }
                SelectChip(state.sound, stringResource(R.string.beacon_sound)) { haptic.hud(); manager.toggleSound() }
                SelectChip(state.vibrate, stringResource(R.string.beacon_vibrate)) { haptic.hud(); manager.toggleVibrate() }
            }
            Spacer(Modifier.height(8.dp))

            // 自动停止
            Text(stringResource(R.string.beacon_autostop), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gray)
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to stringResource(R.string.beacon_off), 5 to "5 MIN", 30 to "30 MIN").forEach { (min, label) ->
                    Surface(
                        modifier = Modifier.weight(1f).clickable { haptic.hud(); manager.setAutoStop(min) },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = if (state.autoStopMs == min * 60_000L) TacGreen.copy(alpha = 0.16f) else SurfaceAlt,
                        border = BorderStroke(1.dp, if (state.autoStopMs == min * 60_000L) TacGreen.copy(alpha = 0.7f) else Gray.copy(alpha = 0.3f)),
                    ) {
                        Text(label, fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = White, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.beacon_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("· · ·   — — —   · · ·", fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Gold)
            }
        }
    }
}
