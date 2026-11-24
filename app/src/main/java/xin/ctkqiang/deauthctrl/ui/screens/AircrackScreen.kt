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
import androidx.compose.foundation.layout.width
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
import kotlinx.coroutines.launch
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
import xin.ctkqiang.deauthctrl.R
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

import xin.ctkqiang.deauthctrl.manager.AircrackManager

/** Aircrack 审计台 —— 附近 Wi-Fi 安全评分(免 root,不破解) */
@Composable
fun AircrackScreen(back: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val manager = remember { AircrackManager(context) }
    var aps by remember { mutableStateOf<List<AircrackManager.ApRisk>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.ops_air_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    haptic.hud()
                    scanning = true
                    kotlinx.coroutines.GlobalScope.launch {
                        aps = manager.scan()
                        scanning = false
                    }
                },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = TacGreen.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.7f)),
            ) {
                Text(if (scanning) stringResource(R.string.ops_air_scanning) else stringResource(R.string.ops_air_scan), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacGreen, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(stringResource(R.string.ops_air_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
            Spacer(Modifier.height(5.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(aps) { a ->
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = SurfaceBg,
                        border = BorderStroke(1.dp, Gray.copy(alpha = 0.3f)),
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.ssid, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 1, modifier = Modifier.weight(1f))
                                val c = when { a.score >= 60 -> Danger; a.score >= 30 -> Warn; else -> TacGreen }
                                Text("${a.security}${if (a.wps) "+WPS" else ""} · ${a.score}", fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = c)
                            }
                            Text("${a.bssid} · ${a.dbm}dBm · CH${a.channel}", fontFamily = Mono, fontSize = 7.5.sp, color = Gray)
                            Text(a.advice, fontFamily = Sans, fontSize = 8.5.sp, color = Warn, lineHeight = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
