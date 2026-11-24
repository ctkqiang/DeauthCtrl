package xin.ctkqiang.deauthctrl.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.MfClassicDump
import xin.ctkqiang.deauthctrl.manager.MfClassicManager
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
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

/**
 * Mifare Classic 破解工作台 —— 公开默认密钥字典逐扇区认证 + 全卡 dump。
 * 仅用于自有卡或授权测试;未知密钥扇区如实标为锁定,不做在线穷举。
 */
@Composable
fun MfClassicScreen(back: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val haptic = LocalHapticFeedback.current
    val manager = remember { MfClassicManager(context) }
    var dump by remember { mutableStateOf<MfClassicDump?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> activity?.let { manager.startReader(it) { d -> dump = d } }
                Lifecycle.Event.ON_PAUSE -> activity?.let { manager.stopReader(it) }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.let { manager.stopReader(it) }
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.mf_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            // 状态横幅
            val (statusText, statusColor) = when {
                !manager.supported -> stringResource(R.string.mf_nfc_missing) to Danger
                !manager.enabled -> stringResource(R.string.mf_nfc_disabled) to Warn
                dump == null -> stringResource(R.string.mf_waiting) to Gray
                else -> stringResource(R.string.mf_ready) to TacGreen
            }
            Surface(
                Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                color = statusColor.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
            ) {
                Text(statusText, fontFamily = Sans, fontSize = 9.sp, color = statusColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(stringResource(R.string.mf_note), fontFamily = Sans, fontSize = 8.sp, color = Gray.copy(alpha = 0.8f), lineHeight = 11.sp)
            Spacer(Modifier.height(6.dp))

            val d = dump
            if (d == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.mf_ph_uid), fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gold)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.mf_ph_hint), fontFamily = Sans, fontSize = 9.sp, color = Gray)
                    }
                }
            } else {
                // 摘要条
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, BorderDim),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.uid, fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.mf_stat, d.crackedCount, d.sectors.size),
                            fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = if (d.crackedCount > 0) TacGreen else Warn,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                // 扇区网格:绿=密钥命中 / 黄=仅 B 密钥 / 灰=锁定
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    d.sectors.forEach { s ->
                        val color = when {
                            s.keyA != null -> TacGreen
                            s.keyB != null -> Warn
                            else -> Gray.copy(alpha = 0.35f)
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp))
                                .background(color.copy(alpha = 0.35f))
                                .border(1.dp, color.copy(alpha = 0.7f), CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("%02d".format(s.sector), fontFamily = Mono, fontSize = 7.sp, color = color)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))

                // 密钥报告
                LazyColumn(Modifier.fillMaxSize()) {
                    items(d.sectors.filter { it.keyA != null || it.keyB != null }) { s ->
                        Surface(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = SurfaceBg,
                            border = BorderStroke(1.dp, BorderDim),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text("S%02d".format(s.sector), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.width(34.dp))
                                Text("A ${s.keyA ?: "--------"}", fontFamily = Mono, fontSize = 9.sp, color = if (s.keyA != null) TacGreen else Gray)
                                Spacer(Modifier.width(12.dp))
                                Text("B ${s.keyB ?: "--------"}", fontFamily = Mono, fontSize = 9.sp, color = if (s.keyB != null) TacGreen else Gray)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))

                // 导出
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptic.hud()
                        runCatching {
                            val f = manager.export(d)
                            message = context.getString(R.string.mf_export_ok, f.absolutePath)
                        }.onFailure {
                            message = context.getString(R.string.mf_export_fail, it.message ?: "")
                        }
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = TacGreen.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.6f)),
                ) {
                    Text(stringResource(R.string.mf_btn_export), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacGreen, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                }
                message?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontFamily = Mono, fontSize = 7.5.sp, color = Gray)
                }
            }
        }
    }
}
