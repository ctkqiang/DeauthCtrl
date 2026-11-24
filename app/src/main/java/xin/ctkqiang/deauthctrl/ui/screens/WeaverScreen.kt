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
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud

import xin.ctkqiang.deauthctrl.manager.IntelFeedManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Weaver —— 安全资讯聚合阅读器(复用情报中心订阅源,独立阅读体验) */
@Composable
fun WeaverScreen(back: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var items by remember { mutableStateOf<List<IntelFeedManager.FeedItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<IntelFeedManager.FeedItem?>(null) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.ops_weaver_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    haptic.hud()
                    loading = true
                    kotlinx.coroutines.GlobalScope.launch {
                        val mgr = IntelFeedManager(context)
                        items = mgr.refresh()
                        mgr.saveCache(items)
                        loading = false
                    }
                },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = TacGreen.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.7f)),
            ) {
                Text(if (loading) stringResource(R.string.ops_weaver_pulling) else stringResource(R.string.ops_weaver_pull), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacGreen, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(stringResource(R.string.ops_weaver_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
            Spacer(Modifier.height(5.dp))
            val current = open
            if (current != null) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(current.title, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 2, modifier = Modifier.weight(1f))
                            Text("✕", fontFamily = Mono, fontSize = 12.sp, color = Gray, modifier = Modifier.clickable { haptic.hud(); open = null }.padding(horizontal = 4.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(current.desc, fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            stringResource(R.string.ops_weaver_open),
                            fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold,
                            modifier = Modifier.clickable {
                                haptic.hud()
                                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(current.link))) }
                            }.padding(vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(items) { it ->
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { haptic.hud(); open = it },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = SurfaceBg,
                        border = BorderStroke(1.dp, Gray.copy(alpha = 0.3f)),
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Text(it.title, fontFamily = Sans, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 2)
                            Spacer(Modifier.height(2.dp))
                            Row {
                                Text(it.sourceId.uppercase(), fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = TacGreen)
                                Spacer(Modifier.width(6.dp))
                                Text(SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(it.timeMs)), fontFamily = Mono, fontSize = 7.sp, color = Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
