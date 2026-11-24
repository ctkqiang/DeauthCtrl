package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud

/**
 * 灯火管制 —— 全屏纯红夜视模式:夜间行动保护暗适应,
 * 只发红光(人眼暗视觉对红光最不敏感),双击画面收起控制层。
 */
@Composable
fun NightLightScreen(back: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var alpha by remember { mutableFloatStateOf(0.55f) }
    var controls by remember { mutableStateOf(true) }

    Box(
        Modifier.fillMaxSize().background(Danger.copy(alpha = alpha))
            .clickable { if (!controls) { haptic.hud(); controls = true } },
    ) {
        if (controls) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.nl_brightness), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = White)
                Slider(
                    value = alpha,
                    onValueChange = { alpha = it },
                    valueRange = 0.15f..0.95f,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); controls = false },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Gold.copy(alpha = 0.16f),
                ) {
                    Text(stringResource(R.string.nl_hide), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(vertical = 9.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); back() },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Color.Black.copy(alpha = 0.35f),
                ) {
                    Text(stringResource(R.string.nl_exit), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gray, modifier = Modifier.padding(vertical = 9.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.nl_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
            }
        }
    }
}
