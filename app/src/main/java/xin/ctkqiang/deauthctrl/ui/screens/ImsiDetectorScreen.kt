
package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.ImsiDetectorManager
import xin.ctkqiang.deauthctrl.manager.ImsiRules
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud

/** IMSI 捕捉器检测 —— 制式降级 / LAC 突变 / 信号突增规则告警 */
@Composable
fun ImsiDetectorScreen(back: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { ImsiDetectorManager(context) }
    val state by manager.state.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            manager.sample()
            delay(5_000L)
        }
    }
    DisposableEffect(Unit) { onDispose { } }

    val riskColor = when (state.risk) {
        ImsiRules.Risk.HIGH -> Danger
        ImsiRules.Risk.MEDIUM -> Warn
        else -> TacGreen
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.ops_imsi_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = riskColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, riskColor.copy(alpha = 0.6f)),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(stringResource(R.string.ops_imsi_risk, when (state.risk) { ImsiRules.Risk.HIGH -> "HIGH"; ImsiRules.Risk.MEDIUM -> "MEDIUM"; else -> "LOW" }), fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = riskColor)
                    Spacer(Modifier.height(3.dp))
                    Text(stringResource(R.string.ops_imsi_cell, state.rat, state.lac, state.cid, state.dbm), fontFamily = Mono, fontSize = 8.5.sp, color = White)
                }
            }
            Spacer(Modifier.height(5.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { manager.reset() },
                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                color = Gray.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Gray.copy(alpha = 0.4f)),
            ) {
                Text(stringResource(R.string.ops_imsi_reset), fontFamily = Mono, fontSize = 9.sp, color = Gray, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(stringResource(R.string.ops_imsi_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
            Spacer(Modifier.height(5.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.events.reversed()) { e ->
                    Text(e, fontFamily = Mono, fontSize = 8.5.sp, color = if (e.startsWith("[RAT")) Danger else Warn, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
