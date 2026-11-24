package xin.ctkqiang.deauthctrl.ui.screens

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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import xin.ctkqiang.deauthctrl.manager.RationMath
import xin.ctkqiang.deauthctrl.manager.RationStore
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

/** 补给追踪 —— 人员/水/食物存量与剩余天数(持久化) */
@Composable
fun RationsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val store = remember { RationStore(context) }
    val state by store.state.collectAsState()

    fun statusColor(s: RationMath.Status): Color = when (s) {
        RationMath.Status.CRITICAL -> Danger
        RationMath.Status.WATCH -> Warn
        else -> TacGreen
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.ration_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            // 人员
            Text(stringResource(R.string.ration_persons), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gray)
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepBtn("-") { haptic.hud(); store.setPersons(state.persons - 1) }
                Text("${state.persons}", fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = White, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
                StepBtn("+") { haptic.hud(); store.setPersons(state.persons + 1) }
            }
            Spacer(Modifier.height(8.dp))

            // 水
            RationCard(
                label = stringResource(R.string.ration_water),
                stock = stringResource(R.string.ration_water_stock, state.waterLiters),
                days = RationMath.formatDays(state.waterDays),
                color = statusColor(state.waterStatus),
                onMinus = { haptic.hud(); store.addWater(-0.5) },
                onPlus = { haptic.hud(); store.addWater(+0.5) },
            )
            Spacer(Modifier.height(8.dp))

            // 食物
            RationCard(
                label = stringResource(R.string.ration_food),
                stock = stringResource(R.string.ration_food_stock, state.foodKcal.toInt()),
                days = RationMath.formatDays(state.foodDays),
                color = statusColor(state.foodStatus),
                onMinus = { haptic.hud(); store.addFood(-550.0) },
                onPlus = { haptic.hud(); store.addFood(+550.0) },
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ration_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
        }
    }
}

@Composable
private fun RationCard(label: String, stock: String, days: String, color: Color, onMinus: () -> Unit, onPlus: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White)
                Text(stock, fontFamily = Mono, fontSize = 8.sp, color = Gray)
                Text(days, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            }
            StepBtn("-") { onMinus() }
            Spacer(Modifier.height(0.dp))
            StepBtn("+") { onPlus() }
        }
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = Gold.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
    ) {
        Text(label, fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    }
}
