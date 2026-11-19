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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.model.ScanState
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.BleScannerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.DbViewModel
import xin.ctkqiang.deauthctrl.viewmodel.RadarViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WifiDisruptViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 信号数据库页 —— SQLite 情报库浏览 + 刷新注入
 *
 * 刷新按钮把当前内存里的雷达/BLE/WiFi 最新数据快照 INSERT 进 SQLite
 * (只写不删),列表按时间倒序展示,支持按来源过滤。
 */
@Composable
fun DbViewScreen(
    dbVm: DbViewModel,
    radarVm: RadarViewModel,
    blescanVm: BleScannerViewModel,
    wifiVm: WifiDisruptViewModel,
    back: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val records by dbVm.records.collectAsState()
    val counts by dbVm.counts.collectAsState()
    val lastRefresh by dbVm.lastRefresh.collectAsState()
    val lastInserted by dbVm.lastInserted.collectAsState()
    var filter by remember { mutableStateOf<String?>(null) }
    val tf = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }

    val visible = if (filter == null) records else records.filter { it.source == filter }

    /** 刷新注入:快照当前各扫描模块的最新数据 → SQLite */
    fun refresh() {
        haptic.hud()
        val radar = radarVm.targets.value
        val ble = blescanVm.devices.value
        val wifi = (wifiVm.scanState.value as? ScanState.Results)?.networks ?: emptyList()
        dbVm.refresh(radar, ble, wifi)
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.dbview_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
            // ── 刷新操作行 ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SQLite · signals.db", fontFamily = Mono, fontSize = 9.sp, color = Gold)
                    Text(
                        lastRefresh?.let { stringResource(R.string.dbview_last_refresh, tf.format(Date(it)), lastInserted) } ?: stringResource(R.string.dbview_never_refreshed),
                        fontFamily = Sans, fontSize = 9.sp, color = Gray,
                    )
                }
                Surface(
                    modifier = Modifier.clickable { refresh() },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Red,
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.7f)),
                ) {
                    Text(
                        stringResource(R.string.dbview_refresh),
                        fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Black,
                        color = White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
            Text(stringResource(R.string.dbview_insert_note), fontFamily = Sans, fontSize = 8.sp, color = Gray)

            Spacer(Modifier.height(8.dp))

            // ── 来源统计芯片 ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DbChip(stringResource(R.string.dbview_filter_all), records.size.toString(), filter == null, Gold) { filter = null }
                counts.forEach { (src, n) ->
                    DbChip(sourceChipLabel(src), "$n", filter == src, TacGreen) { filter = src }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 记录列表 ──
            if (visible.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.dbview_empty),
                    fontFamily = Sans, fontSize = 10.sp, color = Gray,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    lineHeight = 15.sp,
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(visible, key = { it.id }) { r ->
                        Surface(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            color = SurfaceBg,
                            border = BorderStroke(1.dp, BorderDim),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(sourceTag(r.source), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = sourceColor(r.source), modifier = Modifier.width(52.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.name.ifBlank { stringResource(R.string.dbview_unnamed) }.take(20), fontFamily = Sans, fontSize = 11.sp, color = White)
                                    Text("${r.mac} · ${r.detail}", fontFamily = Mono, fontSize = 8.sp, color = Gray, maxLines = 1)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${r.rssi}dBm", fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (r.rssi > -50) Danger else if (r.rssi > -70) Warn else TacGreen)
                                    Text(tf.format(Date(r.capturedAt)), fontFamily = Mono, fontSize = 7.sp, color = Gray)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private fun sourceTag(source: String): String = when {
    source.startsWith("雷达") -> "RADAR"
    source.contains("嗅探") -> "BLE"
    source.contains("WiFi") -> "WIFI"
    else -> source
}

/** 来源芯片标签 —— 数据值("雷达·WiFi" / "BLE 嗅探" 等)到语言中立技术词的显示映射 */
private fun sourceChipLabel(source: String): String = when {
    source.contains("WiFi") -> "WIFI"
    else -> "BLE"
}

private fun sourceColor(source: String): Color = when {
    source.startsWith("雷达") -> Danger
    source.contains("嗅探") -> TacGreen
    else -> Gold
}

/** 统计/过滤芯片 */
@Composable
private fun DbChip(label: String, count: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = if (selected) color.copy(alpha = 0.14f) else SurfaceBg,
        border = BorderStroke(1.dp, if (selected) color.copy(alpha = 0.7f) else BorderDim),
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (selected) color else Gray)
            Spacer(Modifier.width(4.dp))
            Text(count, fontFamily = Mono, fontSize = 9.sp, color = White)
        }
    }
}
