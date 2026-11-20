package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.RadarTarget
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.RadarViewModel

/**
 * 雷达设备选择器 —— 所有需要目标地址的功能共用
 *
 * 从共享雷达扫描器([RadarViewModel])实时取目标列表,点选即回填,
 * **不再要求手输 MAC 地址**。列表为空时提供一键启动雷达扫描。
 * 蓝牙 GATT / Nordic 基准 / WiFi 注入等入口都用它替换手输框。
 *
 * @param typeFilter 只列指定类别:"all" | "wifi" | "ble" | "bt"
 */
@Composable
fun RadarDevicePickerDialog(
    vm: RadarViewModel,
    title: String,
    typeFilter: String,
    onPick: (RadarTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    val targets by vm.targets.collectAsState()
    val running by vm.isRunning.collectAsState()
    val haptic = LocalHapticFeedback.current
    val list = targets.filter { matchesTypeFilter(it, typeFilter) }.sortedByDescending { it.rssi }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
            color = SurfaceBg,
            border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
        ) {
            Column(Modifier.fillMaxWidth(0.94f).heightIn(max = 460.dp).padding(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Star(Gold, 7.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(title, fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.weight(1f))
                    Text(
                        "✕",
                        fontFamily = Mono, fontSize = 13.sp, color = Gray,
                        modifier = Modifier.clickable { onDismiss() }.padding(4.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.picker_hint, list.size),
                    fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (list.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            haptic.hud()
                            if (!running) vm.start()
                        },
                        shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                        color = Red.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            if (running) stringResource(R.string.picker_scanning) else stringResource(R.string.picker_start_scan),
                            fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = Red, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                } else {
                    LazyColumn {
                        items(list.take(60)) { t ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { haptic.hud(); onPick(t) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 类别芯片:与雷达盘同一套固定类别色
                                val typeLabel = when {
                                    t.bonded -> stringResource(R.string.det_badge_friendly)
                                    t.type == "wifi" -> "WIFI"
                                    t.observedVia == "ble" -> "BLE"
                                    else -> "BT"
                                }
                                Surface(
                                    shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                                    color = radarBlipColor(t).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, radarBlipColor(t).copy(alpha = 0.5f)),
                                ) {
                                    Text(typeLabel, fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = radarBlipColor(t), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.name.take(18), fontFamily = Sans, fontSize = 11.sp, color = White, maxLines = 1)
                                    Text(
                                        "${if (t.type == "wifi") t.id else t.address} · ${t.vendor}",
                                        fontFamily = Mono, fontSize = 8.sp, color = Gray, maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${t.rssi}dBm",
                                        fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        color = if (t.rssi > -50) Danger else if (t.rssi > -70) Warn else TacGreen,
                                    )
                                    Text("%.1fm".format(t.distanceM), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
