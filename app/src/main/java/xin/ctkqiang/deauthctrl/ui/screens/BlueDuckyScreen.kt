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
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud

import xin.ctkqiang.deauthctrl.manager.BlueDuckyPayloads
import java.io.File

/** BlueDucky 载荷工坊 —— DuckyScript 生成 + 校验 + 导出(供 BlueDucky/Flipper 硬件) */
@Composable
fun BlueDuckyScreen(back: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var open by remember { mutableStateOf<BlueDuckyPayloads.Payload?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.ops_bd_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(stringResource(R.string.ops_bd_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
            Spacer(Modifier.height(5.dp))
            val p = open
            if (p != null) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.title, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
                            Text("✕", fontFamily = Mono, fontSize = 12.sp, color = Gray, modifier = Modifier.clickable { haptic.hud(); open = null }.padding(horizontal = 4.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(p.script, fontFamily = Mono, fontSize = 8.sp, color = TacGreen, lineHeight = 11.sp)
                        Spacer(Modifier.height(5.dp))
                        val problems = BlueDuckyPayloads.validate(p.script)
                        Text(if (problems.isEmpty()) stringResource(R.string.ops_bd_ok) else stringResource(R.string.ops_bd_problems, problems.size), fontFamily = Mono, fontSize = 8.sp, color = if (problems.isEmpty()) TacGreen else Danger)
                        Spacer(Modifier.height(5.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                haptic.hud()
                                val dir = File(context.getExternalFilesDir(null), "flipper")
                                dir.mkdirs()
                                val f = File(dir, "${p.id}.txt")
                                f.writeText(p.script)
                                msg = context.getString(R.string.ops_bd_saved, f.absolutePath)
                            },
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = Gold.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
                        ) {
                            Text(stringResource(R.string.ops_bd_export), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            msg?.let { Text(it, fontFamily = Mono, fontSize = 7.5.sp, color = Gray) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(BlueDuckyPayloads.payloads()) { pl ->
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { haptic.hud(); open = pl },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = SurfaceBg,
                        border = BorderStroke(1.dp, Gray.copy(alpha = 0.3f)),
                    ) {
                        Text(pl.title, fontFamily = Sans, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
                    }
                }
            }
        }
    }
}
