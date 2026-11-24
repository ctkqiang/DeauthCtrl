package xin.ctkqiang.deauthctrl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.GsmLabManager
import xin.ctkqiang.deauthctrl.manager.GsmMath
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud

/** GSM 实验室 —— 蜂窝情报仪表盘 + 频点计算器(全离线) */
@Composable
fun GsmLabScreen(back: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val manager = remember { GsmLabManager(context) }
    val state by manager.state.collectAsState()
    var tab by remember { mutableStateOf(0) }

    val phonePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            phonePerm.launch(Manifest.permission.READ_PHONE_STATE)
        }
        while (true) {
            manager.refresh()
            delay(5_000L)
        }
    }
    DisposableEffect(Unit) { /* 循环随组合退出自动取消 */ onDispose { } }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.gsm_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                SelectChip(tab == 0, stringResource(R.string.gsm_tab_live)) { haptic.hud(); tab = 0 }
                SelectChip(tab == 1, stringResource(R.string.gsm_tab_calc)) { haptic.hud(); tab = 1 }
            }
            Spacer(Modifier.height(6.dp))
            if (tab == 0) GsmLiveView(state, manager) else GsmCalcView()
        }
    }
}

@Composable
private fun GsmLiveView(state: GsmLabManager.State, manager: GsmLabManager) {
    val sim = state.sim
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // SIM / 网络
        Surface(
            Modifier.fillMaxWidth(),
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = SurfaceBg,
            border = BorderStroke(1.dp, BorderDim),
        ) {
            Column(Modifier.padding(10.dp)) {
                InfoRow(stringResource(R.string.gsm_operator), sim.operator, Gold)
                InfoRow(stringResource(R.string.gsm_mccmnc), sim.mccMnc, White)
                InfoRow(stringResource(R.string.gsm_nettype), sim.networkType, TacGreen)
                InfoRow(stringResource(R.string.gsm_roaming), if (sim.roaming) "YES" else "NO", if (sim.roaming) Color(0xFFFF5252) else Gray)
            }
        }
        Spacer(Modifier.height(6.dp))

        // 服务小区
        val serving = state.serving
        Text(stringResource(R.string.gsm_serving), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
        Spacer(Modifier.height(3.dp))
        serving?.let { s ->
            Surface(
                Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = Gold.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
            ) {
                Column(Modifier.padding(10.dp)) {
                    InfoRow(stringResource(R.string.gsm_type), s.type, TacGreen)
                    InfoRow(stringResource(R.string.gsm_cellid), s.id, White)
                    InfoRow(stringResource(R.string.gsm_band), s.band, Gold)
                    InfoRow(stringResource(R.string.gsm_signal), "${s.dbm} dBm / ${s.asu} ASU", if (s.dbm > -90) TacGreen else if (s.dbm > -105) Color(0xFFFFA726) else Color(0xFFFF5252))
                    s.rsrp?.let { InfoRow("RSRP", "$it dBm", White) }
                    s.rsrq?.let { InfoRow("RSRQ", "$it dB", White) }
                    s.snr?.let { InfoRow("SNR", "$it dB", White) }
                    s.taDistM?.let { InfoRow(stringResource(R.string.gsm_tadist), GsmMath.formatDistance(it), Gold) }
                }
            }
        } ?: Text(stringResource(R.string.gsm_no_service), fontFamily = Sans, fontSize = 9.sp, color = Gray)
        Spacer(Modifier.height(6.dp))

        // 邻区信号柱状图
        Text(stringResource(R.string.gsm_neighbors, state.neighbors.size), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
        Spacer(Modifier.height(3.dp))
        state.neighbors.forEach { n ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${n.type} ${n.band}", fontFamily = Mono, fontSize = 7.5.sp, color = Gray, modifier = Modifier.width(110.dp))
                val pct = ((120 + n.dbm) / 70f).coerceIn(0.05f, 1f)
                Canvas(Modifier.weight(1f).height(10.dp)) {
                    drawRect(Color(0xFF00E5FF).copy(alpha = 0.7f), size = androidx.compose.ui.geometry.Size(size.width * pct, size.height))
                }
                Text(if (n.registered) "SERV" else "${n.dbm}", fontFamily = Mono, fontSize = 7.5.sp, color = if (n.registered) TacGreen else White, modifier = Modifier.width(38.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.gsm_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
    }
}

@Composable
private fun GsmCalcView() {
    var input by remember { mutableStateOf("") }
    val v = input.trim().toLongOrNull()
    val result = remember(input) {
        v?.let {
            when {
                it in 1..885 -> {
                    val band = GsmMath.gsmBandOf(it.toInt())
                    band?.let { b -> "GSM ${b.display} · ARFCN $it · 上行 ${GsmMath.formatMhz(b.upMhz(it.toInt()))} · 下行 ${GsmMath.formatMhz(b.downMhz(it.toInt()))}" }
                }
                it in 900..60000 -> {
                    val (band, mhz) = GsmMath.lteBandOf(it.toInt()) ?: return@let null
                    "LTE B${band.band} ${band.name}${if (band.tdd) "(TDD)" else ""} · EARFCN $it · ${GsmMath.formatMhz(mhz)}"
                }
                it in 300..3000 -> GsmMath.bandLabelOf(it.toDouble())
                else -> null
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.gsm_calc_hint), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gray)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = input,
            onValueChange = { input = it },
            textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = White),
            cursorBrush = SolidColor(Gold),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                .background(SurfaceAlt)
                .border(1.dp, BorderDim, CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp),
            decorationBox = { inner ->
                if (input.isEmpty()) {
                    Text(stringResource(R.string.gsm_calc_ph), fontFamily = Mono, fontSize = 9.sp, color = Gray.copy(alpha = 0.5f))
                }
                inner()
            },
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            Modifier.fillMaxWidth(),
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = SurfaceBg,
            border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
        ) {
            Text(
                result ?: stringResource(R.string.gsm_calc_empty),
                fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = if (result != null) Gold else Gray, lineHeight = 14.sp,
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.gsm_calc_bands), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.weight(0.42f))
        Text(value, fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.weight(0.58f))
    }
}
