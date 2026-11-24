
package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.AttackHoneypotManager
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** AnyIntruder 蜜罐 —— 本机端口监听,攻击特征实时捕获与统计 */
@Composable
fun HoneypotScreen(back: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val manager = remember { AttackHoneypotManager() }
    val state by manager.state.collectAsState()
    var portText by remember { mutableStateOf("8088") }

    DisposableEffect(Unit) { onDispose { manager.stop() } }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.ops_hp_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    textStyle = TextStyle(fontFamily = Mono, fontSize = 11.sp, color = White),
                    cursorBrush = SolidColor(Gold),
                    singleLine = true,
                    modifier = Modifier.width(90.dp).clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)).background(SurfaceAlt).border(1.dp, Gray.copy(alpha = 0.4f), CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.weight(1f).clickable {
                        haptic.hud()
                        if (state.running) manager.stop()
                        else GlobalScope.launch { manager.start(portText.toIntOrNull() ?: 8088) }
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = if (state.running) Danger.copy(alpha = 0.2f) else TacGreen.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, if (state.running) Danger else TacGreen.copy(alpha = 0.7f)),
                ) {
                    Text(if (state.running) stringResource(R.string.ops_hp_stop) else stringResource(R.string.ops_hp_start), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (state.running) Danger else TacGreen, modifier = Modifier.padding(vertical = 9.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Spacer(Modifier.height(6.dp))
            Surface(
                Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, Gray.copy(alpha = 0.4f)),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(stringResource(R.string.ops_hp_stats, state.attempts.size, state.byIp.size), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Spacer(Modifier.height(3.dp))
                    state.topSignatures.take(4).forEach { (sig, n) ->
                        Text("· $sig ×$n", fontFamily = Mono, fontSize = 8.sp, color = Color(0xFF00E5FF))
                    }
                    Spacer(Modifier.height(3.dp))
                    state.byIp.entries.sortedByDescending { it.value }.take(4).forEach { (ip, n) ->
                        Text("· $ip ×$n", fontFamily = Mono, fontSize = 8.sp, color = White)
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(stringResource(R.string.ops_hp_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
            Spacer(Modifier.height(5.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.attempts.reversed()) { a ->
                    Text(stringResource(R.string.ops_hp_line, a.ip, a.port, a.signature), fontFamily = Mono, fontSize = 8.5.sp, color = if (a.signature.contains("爆破")) Danger else Warn, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
