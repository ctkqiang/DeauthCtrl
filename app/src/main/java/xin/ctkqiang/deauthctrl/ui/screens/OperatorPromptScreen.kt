package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.White

/**
 * 首次启动呼号设置页 —— 应用启动时询问操作员用户名
 *
 * 呼号写入 SharedPreferences（KEY_OPERATOR_NAME），之后用于
 * 战术相机署名、对讲呼号、终端提示等所有操作员标识场景。
 * 可跳过：跳过时使用默认呼号「哪吒」，之后可在设置页修改。
 */
@Composable
fun OperatorPromptScreen(
    onConfirm: (String) -> Unit,
    onSkip: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var name by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(Dark).navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Star(Gold, 30.dp)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.op_register_title), fontFamily = Sans, fontSize = 22.sp, fontWeight = FontWeight.Black, color = White)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.op_register_desc),
            fontFamily = Sans, fontSize = 11.sp, color = Gray, textAlign = TextAlign.Center, lineHeight = 17.sp,
        )

        Spacer(Modifier.height(28.dp))

        Surface(
            shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
            color = SurfaceBg,
            border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.op_callsign_label), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Red)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .border(1.dp, BorderDim, CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                        .background(SurfaceAlt)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(16) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Mono, fontSize = 15.sp, color = White),
                        cursorBrush = SolidColor(Gold),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (name.isEmpty()) {
                                Text(stringResource(R.string.op_callsign_placeholder), fontFamily = Sans, fontSize = 12.sp, color = Gray.copy(alpha = 0.6f))
                            }
                            inner()
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptic.hud()
                        if (name.isNotBlank()) onConfirm(name)
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = if (name.isNotBlank()) Danger else SurfaceAlt,
                    border = BorderStroke(1.dp, if (name.isNotBlank()) Danger.copy(alpha = 0.8f) else BorderDim),
                ) {
                    Text(
                        stringResource(R.string.op_confirm),
                        fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Black,
                        color = if (name.isNotBlank()) White else Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 11.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.op_skip),
                    fontFamily = Sans, fontSize = 10.sp, color = Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); onSkip() }.padding(vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.op_footer), fontFamily = Mono, fontSize = 8.sp, color = Gray.copy(alpha = 0.6f))
        Spacer(Modifier.height(10.dp))
    }
}
