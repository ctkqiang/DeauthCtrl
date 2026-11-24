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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.WalkieChannelDb
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud

/**
 * 对讲机信道表 —— 全量离线(数据内置,零网络)。
 * 返回再进入时保留滚动位置与筛选词(rememberSaveable)。
 * 点任意行复制频率到剪贴板。
 */
@Composable
fun RadioChannelsScreen(back: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    var filter by rememberSaveable { mutableStateOf("") }
    // 离开页面再回来:滚动位置保持原处
    val listState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val sections = remember { WalkieChannelDb.sections() }
    val filtered = remember(filter, sections) {
        if (filter.isBlank()) sections
        else sections.mapNotNull { sec ->
            val chs = sec.channels.filter {
                it.freq.toString().contains(filter) || it.note.contains(filter, ignoreCase = true) ||
                    it.toneType.contains(filter, ignoreCase = true) || it.ch.contains(filter)
            }
            if (chs.isEmpty()) null else sec.copy(channels = chs)
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.radio_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            // 筛选
            BasicTextField(
                value = filter,
                onValueChange = { filter = it },
                textStyle = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = White),
                cursorBrush = SolidColor(Gold),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                    .background(SurfaceAlt)
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                decorationBox = { inner ->
                    if (filter.isEmpty()) {
                        Text(stringResource(R.string.radio_ph_filter), fontFamily = Mono, fontSize = 9.sp, color = Gray.copy(alpha = 0.5f))
                    }
                    inner()
                },
            )
            Spacer(Modifier.height(5.dp))
            Text(stringResource(R.string.radio_hint), fontFamily = Sans, fontSize = 8.sp, color = Gray)
            Spacer(Modifier.height(5.dp))
            Surface(
                Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = Gold.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Text(stringResource(R.string.radio_legend_title), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Spacer(Modifier.height(3.dp))
                    Text(stringResource(R.string.radio_legend_body), fontFamily = Sans, fontSize = 8.5.sp, color = White, lineHeight = 13.sp)
                }
            }
            Spacer(Modifier.height(6.dp))

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                filtered.forEach { sec ->
                    item(key = "sec_${sec.titleRes}") {
                        Surface(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = TacGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.5f)),
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                Text(stringResource(sec.titleRes), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TacGreen)
                                Text(stringResource(sec.noteRes), fontFamily = Sans, fontSize = 7.5.sp, color = Gray)
                            }
                        }
                    }
                    item(key = "head_${sec.titleRes}") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp)) {
                            Head("CH", 30.dp); Head("FREQ (MHz)", 76.dp); Head("TONE", 46.dp)
                            Head("R-DCS", 56.dp); Head("R-CTC", 46.dp); Head("T-DCS", 56.dp); Head("T-CTC", 46.dp)
                            Text("NOTE", fontFamily = Mono, fontSize = 6.5.sp, fontWeight = FontWeight.Bold, color = Gold.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                        }
                    }
                    items(sec.channels, key = { "ch_${sec.titleRes}_${it.ch}_${it.freq}" }) { ch ->
                        Surface(
                            Modifier.fillMaxWidth().clickable {
                                haptic.hud()
                                clipboard.setText(AnnotatedString("%.5f".format(ch.freq)))
                            },
                            shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                            color = SurfaceBg,
                            border = BorderStroke(1.dp, BorderDim.copy(alpha = 0.5f)),
                        ) {
                            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Cell(ch.ch, 30.dp, Gold, bold = true)
                                    Cell("%.5f".format(ch.freq), 76.dp, White)
                                    Cell(ch.toneType, 46.dp, if (ch.toneType == "None") Gray else TacGreen)
                                    Cell(ch.rDcs, 56.dp, White)
                                    Cell(ch.rCtcs, 46.dp, White)
                                    Cell(ch.tDcs, 56.dp, White)
                                    Cell(ch.tCtcs, 46.dp, White)
                                }
                                if (ch.note.isNotBlank()) {
                                    Text(ch.note, fontFamily = Sans, fontSize = 8.sp, color = TacGreen.copy(alpha = 0.85f), lineHeight = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                item(key = "footer") { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun Head(label: String, width: androidx.compose.ui.unit.Dp) {
    Text(label, fontFamily = Mono, fontSize = 6.5.sp, fontWeight = FontWeight.Bold, color = Gold.copy(alpha = 0.8f), modifier = Modifier.width(width))
}

@Composable
private fun Cell(text: String, width: androidx.compose.ui.unit.Dp, color: Color, bold: Boolean = false) {
    Text(text, fontFamily = Mono, fontSize = 8.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color, maxLines = 1, modifier = Modifier.width(width))
}
