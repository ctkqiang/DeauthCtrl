
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud

/** fsociety 作战台 —— 渗透模块快速指挥台 + 任务清单(持久化) */
@Composable
fun FsocietyScreen(back: () -> Unit, nav: (String) -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("fsociety", 0) }
    var tasks by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) { tasks = prefs.getStringSet("tasks", emptySet()) ?: emptySet() }

    fun toggleTask(t: String) {
        val cur = tasks.toMutableSet()
        if (!cur.add(t)) cur.remove(t)
        prefs.edit().putStringSet("tasks", cur).apply()
        tasks = cur
    }

    val ops = listOf(
        R.string.ops_fs_recon to listOf("arp" to R.string.ops_fs_arp, "portscan" to R.string.ops_fs_portscan, "radar" to R.string.ops_fs_radar, "cctv" to R.string.ops_fs_cctv, "onvif" to R.string.ops_fs_onvif),
        R.string.ops_fs_wireless to listOf("ble" to R.string.ops_fs_ble, "wifi" to R.string.ops_fs_wifi, "btjam" to R.string.ops_fs_btjam, "wifijam" to R.string.ops_fs_wifijam, "aircrack" to R.string.ops_fs_aircrack),
        R.string.ops_fs_ops to listOf("imsi" to R.string.ops_fs_imsi, "gsmlab" to R.string.ops_fs_gsmlab, "honeypot" to R.string.ops_fs_honeypot, "subghz" to R.string.ops_fs_subghz, "mfclassic" to R.string.ops_fs_mfclassic, "depth" to R.string.ops_fs_depth),
    )
    val checklist = listOf(
        "scope_confirm" to R.string.ops_fs_t1,
        "recon_done" to R.string.ops_fs_t2,
        "vuln_map" to R.string.ops_fs_t3,
        "exploit_min" to R.string.ops_fs_t4,
        "evidence" to R.string.ops_fs_t5,
        "report" to R.string.ops_fs_t6,
    )

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.ops_fs_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text("fsociety", fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Text(stringResource(R.string.ops_fs_banner), fontFamily = Mono, fontSize = 8.sp, color = Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                }
                ops.forEach { (sectionRes, entries) ->
                    item(key = "sec_$sectionRes") {
                        Text(stringResource(sectionRes), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(vertical = 3.dp))
                    }
                    items(entries, key = { it.first + sectionRes }) { (route, labelRes) ->
                        Surface(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { haptic.hud(); nav(route) },
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = SurfaceBg,
                            border = BorderStroke(1.dp, Gray.copy(alpha = 0.3f)),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text("▸", fontFamily = Mono, fontSize = 10.sp, color = Danger)
                                Spacer(Modifier.padding(start = 6.dp))
                                Text(stringResource(labelRes), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = White)
                            }
                        }
                    }
                }
                item(key = "checklist") {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.ops_fs_checklist), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Spacer(Modifier.height(3.dp))
                }
                items(checklist, key = { it.first }) { (key, labelRes) ->
                    val done = key in tasks
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { haptic.hud(); toggleTask(key) },
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = if (done) TacGreen.copy(alpha = 0.1f) else SurfaceBg,
                        border = BorderStroke(1.dp, if (done) TacGreen.copy(alpha = 0.5f) else Gray.copy(alpha = 0.3f)),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Text(if (done) "[x]" else "[ ]", fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (done) TacGreen else Gray)
                            Spacer(Modifier.padding(start = 8.dp))
                            Text(stringResource(labelRes), fontFamily = Sans, fontSize = 9.5.sp, color = if (done) TacGreen else White)
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}
