package xin.ctkqiang.deauthctrl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.ContextCompat
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.BreadcrumbManager
import xin.ctkqiang.deauthctrl.manager.NavMath
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 足迹面包屑 —— GPS 轨迹记录 + 原路返回导航 */
@Composable
fun BreadcrumbsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val manager = remember { BreadcrumbManager(context) }
    val state by manager.state.collectAsState()

    DisposableEffect(manager) {
        manager.seedFromLastKnown()
        onDispose { manager.stop() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.crumb_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            // 遥测
            Surface(
                Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, Gray.copy(alpha = 0.4f)),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        CrumbStat(stringResource(R.string.crumb_points), "${state.waypoints.size}", TacGreen)
                        CrumbStat(stringResource(R.string.crumb_walked), NavMath.formatDistance(state.walkedMeters), Gold)
                    }
                    Spacer(Modifier.height(4.dp))
                    val bt = manager.backtrack()
                    Text(
                        if (bt != null && bt.second >= 1.0) {
                            stringResource(R.string.crumb_back_hint, NavMath.formatBearing(bt.first), NavMath.formatDistance(bt.second))
                        } else stringResource(R.string.crumb_no_back),
                        fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (bt != null) Gold else Gray,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            // 控制
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    haptic.hud()
                    val ok = if (state.logging) { manager.stop(); true }
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        manager.start()
                    } else false
                    if (!ok && !state.logging) manager.stop()
                },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = if (state.logging) Danger.copy(alpha = 0.2f) else TacGreen.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, if (state.logging) Danger else TacGreen.copy(alpha = 0.7f)),
            ) {
                Text(
                    if (state.logging) stringResource(R.string.crumb_stop) else stringResource(R.string.crumb_start),
                    fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Black,
                    color = if (state.logging) Danger else TacGreen, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).clickable { haptic.hud(); manager.popLast() },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = Gold.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
                ) {
                    Text(stringResource(R.string.crumb_step_back), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                }
                Surface(
                    modifier = Modifier.weight(1f).clickable { haptic.hud(); manager.clear() },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = Danger.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
                ) {
                    Text(stringResource(R.string.crumb_clear), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Danger, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.crumb_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
            Spacer(Modifier.height(6.dp))

            // 最近足迹
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.waypoints.takeLast(40).reversed()) { wp ->
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                        color = SurfaceBg,
                        border = BorderStroke(1.dp, Gray.copy(alpha = 0.3f)),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("%.5f, %.5f".format(wp.lat, wp.lon), fontFamily = Mono, fontSize = 8.5.sp, color = White, modifier = Modifier.weight(1f))
                            Text(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(wp.timeMs)), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrumbStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(value, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontFamily = Mono, fontSize = 7.5.sp, color = Gray)
    }
}
