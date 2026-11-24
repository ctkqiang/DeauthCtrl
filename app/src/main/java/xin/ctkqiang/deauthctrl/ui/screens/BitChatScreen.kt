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

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.clip
import xin.ctkqiang.deauthctrl.manager.BitChatManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.border

/** BitChat —— BLE 广播聊天(IRC 风格,免配对) */
@Composable
fun BitChatScreen(back: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val manager = remember { BitChatManager(context) }
    val state by manager.state.collectAsState()
    var draft by remember { mutableStateOf("") }
    var nick by remember { mutableStateOf("anon") }
    var chan by remember { mutableStateOf("general") }

    DisposableEffect(Unit) { onDispose { manager.stop() } }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.ops_chat_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ChatField(nick, "NICK", Modifier.weight(0.8f)) { nick = it; manager.setNickname(it) }
                Spacer(Modifier.width(6.dp))
                ChatField(chan, "CH", Modifier.weight(0.8f)) { chan = it; manager.setChannel(it) }
                Spacer(Modifier.width(6.dp))
                Surface(
                    modifier = Modifier.clickable {
                        haptic.hud()
                        if (state.on) manager.stop() else manager.start()
                    },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = if (state.on) Danger.copy(alpha = 0.2f) else TacGreen.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, if (state.on) Danger else TacGreen.copy(alpha = 0.7f)),
                ) {
                    Text(if (state.on) stringResource(R.string.ops_chat_stop) else stringResource(R.string.ops_chat_start), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (state.on) Danger else TacGreen, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ops_chat_peers, state.peers.size), fontFamily = Mono, fontSize = 8.sp, color = Gray)
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(state.messages) { m ->
                    Text(
                        "[${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(m.timeMs))}] <${m.sender}> ${m.text}",
                        fontFamily = Mono, fontSize = 9.sp,
                        color = if (m.self) TacGreen else Color(0xFF00E5FF),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = White),
                    cursorBrush = SolidColor(Gold),
                    singleLine = true,
                    modifier = Modifier.weight(1f).clip(CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp)).background(SurfaceBg).border(1.dp, Gray.copy(alpha = 0.4f), CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) Text(stringResource(R.string.ops_chat_ph), fontFamily = Mono, fontSize = 9.sp, color = Gray.copy(alpha = 0.5f))
                        inner()
                    },
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    modifier = Modifier.clickable { haptic.hud(); if (draft.isNotBlank()) { manager.send(draft); draft = "" } },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = Gold.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.7f)),
                ) {
                    Text(stringResource(R.string.ops_chat_send), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ops_chat_note), fontFamily = Sans, fontSize = 7.5.sp, color = Gray, lineHeight = 10.sp)
        }
    }
}

@Composable
private fun ChatField(value: String, label: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = White),
        cursorBrush = SolidColor(Gold),
        singleLine = true,
        modifier = modifier.clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)).background(SurfaceBg).border(1.dp, Gray.copy(alpha = 0.4f), CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)).padding(horizontal = 8.dp, vertical = 8.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(label, fontFamily = Mono, fontSize = 8.sp, color = Gray.copy(alpha = 0.5f))
            inner()
        },
    )
}
