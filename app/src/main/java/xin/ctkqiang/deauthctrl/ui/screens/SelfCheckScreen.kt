package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.BatterySnapshot
import xin.ctkqiang.deauthctrl.manager.CapabilityProbe
import xin.ctkqiang.deauthctrl.manager.CapabilityStatus
import xin.ctkqiang.deauthctrl.manager.NetworkSnapshot
import xin.ctkqiang.deauthctrl.manager.RootStatus
import xin.ctkqiang.deauthctrl.manager.SelfCheckState
import xin.ctkqiang.deauthctrl.manager.SensorEntry
import xin.ctkqiang.deauthctrl.manager.ThermalSnapshot
import xin.ctkqiang.deauthctrl.manager.ThermalZone
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.PanelStat
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.SelfCheckViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 传感器清单最多展示的条数(页面约定) */
private const val MAX_SENSORS_SHOWN = 30

/** 页面在前台时的低频自动刷新间隔 */
private const val AUTO_REFRESH_MS = 30_000L

/**
 * 本机硬件自检台
 *
 * 电池 / 热区 / CPU / 内存 / 存储 / 传感器 / Wi-Fi 七个分区,数据全部来自
 * [xin.ctkqiang.deauthctrl.manager.HardwareSelfCheck] 读取的系统真实返回值。
 * 读不到的字段显示 “--” 或 “不可用”,不做任何估算填充;CPU 使用率明确标注
 * 为 250ms 采样窗口的估算值。
 */
@Composable
fun SelfCheckScreen(back: () -> Unit) {
    val vm: SelfCheckViewModel = viewModel()
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refresh() }

    // 页面存活时每 30 秒低频刷新;离屏立即取消,不留下后台轮询
    DisposableEffect(Unit) {
        val pollJob = scope.launch {
            while (true) {
                delay(AUTO_REFRESH_MS)
                vm.refresh()
            }
        }
        onDispose { pollJob.cancel() }
    }

    val loaded = state.updatedAtMs > 0L
    val partial = loaded && (
        state.thermal.topZones.isEmpty() ||
            state.battery.capacityPercent == null ||
            state.battery.health == "不可用" ||
            state.battery.health == "未知" ||
            state.cpu.usagePercent == null ||
            state.sensors.isEmpty()
        )
    val verdictColor = when {
        state.alerts.isNotEmpty() -> Danger
        !loaded -> Gray
        partial -> Warn
        else -> TacGreen
    }
    val verdict = when {
        !loaded && state.loading -> stringResource(R.string.selfchk_verdict_reading)
        !loaded -> stringResource(R.string.selfchk_verdict_waiting)
        state.alerts.isNotEmpty() ->
            stringResource(R.string.selfchk_verdict_alerts, state.alerts.size)
        partial -> stringResource(R.string.selfchk_verdict_partial)
        else -> stringResource(R.string.selfchk_verdict_ok)
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.selfchk_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryCard(
                state = state,
                verdict = verdict,
                verdictColor = verdictColor,
                loaded = loaded,
                onRefresh = { haptic.hud(); vm.refresh() },
            )
            CapabilityMatrixPanel()
            BatteryPanel(state.battery)
            ThermalPanel(state.thermal)
            LoadPanel(state)
            SensorPanel(state.sensors)
            NetworkPanel(state.network)
            Text(
                stringResource(R.string.selfchk_data_note),
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Gray,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        }
    }
}

// ─── 结论 ────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(
    state: SelfCheckState,
    verdict: String,
    verdictColor: Color,
    loaded: Boolean,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, BorderDim),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Star(verdictColor, 8.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    verdict,
                    fontFamily = Sans,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = verdictColor,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    if (state.loading) stringResource(R.string.selfchk_btn_reading)
                    else stringResource(R.string.selfchk_btn_refresh),
                    onClick = onRefresh,
                )
            }
            Spacer(Modifier.height(4.dp))
            val updatedPrefix = if (loaded) {
                stringResource(
                    R.string.selfchk_updated,
                    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(state.updatedAtMs)),
                ) + " · "
            } else {
                ""
            }
            Text(
                updatedPrefix + stringResource(
                    R.string.selfchk_sensor_zone_counts,
                    state.sensors.size,
                    state.thermal.readableZones,
                ),
                fontFamily = Mono,
                fontSize = 8.sp,
                color = Gray,
            )
            state.alerts.forEach { reason ->
                Text(
                    "[!] $reason",
                    fontFamily = Sans,
                    fontSize = 9.sp,
                    color = Danger,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

// ─── 电池 ────────────────────────────────────────────────────────────────────

@Composable
private fun BatteryPanel(b: BatterySnapshot) {
    TermPanel(stringResource(R.string.selfchk_panel_battery)) {
        val capacity = b.capacityPercent
        val capacityColor = when {
            capacity == null -> Gray
            capacity <= 15 -> Danger
            capacity <= 30 -> Warn
            else -> TacGreen
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.selfchk_label_level), fontFamily = Sans, fontSize = 11.sp, color = Gray)
            Text(
                capacity?.let { "$it %" } ?: "--",
                fontFamily = Mono,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = capacityColor,
            )
        }
        if (capacity != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { capacity / 100f },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = capacityColor,
                trackColor = SurfaceAlt,
            )
        }
        Spacer(Modifier.height(6.dp))
        PanelStat(
            stringResource(R.string.selfchk_stat_current),
            b.currentNowUa?.let(::formatCurrentUa) ?: "--",
            valueColor = currentColor(b.currentNowUa),
        )
        PanelStat(
            stringResource(R.string.selfchk_stat_temperature),
            b.temperatureC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "--",
            valueColor = temperatureColor(b.temperatureC),
        )
        PanelStat(
            stringResource(R.string.selfchk_stat_health),
            batteryHealthLabel(b.health),
            valueColor = when {
                b.healthAlert -> Danger
                b.health == "良好" -> TacGreen
                else -> Gray
            },
        )
        PanelStat(stringResource(R.string.selfchk_stat_direction), batteryDirectionLabel(b.direction))
        PanelStat(stringResource(R.string.selfchk_stat_battery_status), batteryStatusLabel(b.status))
        PanelStat(stringResource(R.string.selfchk_stat_power_source), batteryPluggedLabel(b.plugged))
        PanelStat(stringResource(R.string.selfchk_stat_voltage), b.voltageMv?.let { "$it mV" } ?: "--")
        PanelStat(
            stringResource(R.string.selfchk_stat_charge_counter),
            b.chargeCounterUah?.let { String.format(Locale.US, "%.0f mAh", it / 1000f) } ?: "--",
        )
        PanelStat(stringResource(R.string.selfchk_stat_technology), batteryTechnologyLabel(b.technology))
        if (b.present == false) {
            Text(
                stringResource(R.string.selfchk_warn_no_battery),
                fontFamily = Sans,
                fontSize = 9.sp,
                color = Warn,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ─── 热区 ────────────────────────────────────────────────────────────────────

@Composable
private fun ThermalPanel(t: ThermalSnapshot) {
    TermPanel(stringResource(R.string.selfchk_panel_thermal)) {
        if (!t.available) {
            Text(
                stringResource(R.string.selfchk_thermal_unavailable),
                fontFamily = Sans,
                fontSize = 9.sp,
                color = Gray,
                lineHeight = 12.sp,
            )
        } else {
            t.topZones.forEach { zone -> ThermalRow(zone) }
            if (t.readableZones > t.topZones.size) {
                Spacer(Modifier.height(5.dp))
                Text(
                    stringResource(
                        R.string.selfchk_thermal_truncated,
                        t.readableZones,
                        t.topZones.size,
                    ),
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = Gray,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.selfchk_thermal_threshold),
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Gray,
            )
        }
    }
}

@Composable
private fun ThermalRow(zone: ThermalZone) {
    val color = when {
        zone.tempC > 45f -> Danger
        zone.tempC > 38f -> Gold
        else -> TacGreen
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                zone.name,
                fontFamily = Sans,
                fontSize = 10.sp,
                color = White,
                modifier = Modifier.weight(1f),
            )
            Text(
                String.format(Locale.US, "%.1f °C", zone.tempC),
                fontFamily = Mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (zone.tempC / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = color,
            trackColor = SurfaceAlt,
        )
    }
}

// ─── 负载 ────────────────────────────────────────────────────────────────────

@Composable
private fun LoadPanel(state: SelfCheckState) {
    val cpu = state.cpu
    val mem = state.memory
    val storage = state.storage
    TermPanel(stringResource(R.string.selfchk_panel_load)) {
        LoadRow(
            label = stringResource(R.string.selfchk_label_cpu_usage),
            detail = if (cpu.usagePercent == null) {
                stringResource(R.string.selfchk_cpu_unavailable)
            } else {
                stringResource(R.string.selfchk_cpu_detail, cpu.sampleWindowMs, cpu.coreCount)
            },
            percent = cpu.usagePercent,
        )
        LoadRow(
            label = stringResource(R.string.selfchk_label_memory),
            detail = if (mem.totalBytes == null || mem.availBytes == null) {
                stringResource(R.string.selfchk_unavailable)
            } else {
                stringResource(
                    R.string.selfchk_mem_detail,
                    formatBytes(mem.availBytes),
                    formatBytes(mem.totalBytes),
                )
            },
            percent = mem.usedPercent,
        )
        if (mem.lowMemory) {
            Text(
                stringResource(R.string.selfchk_warn_low_memory),
                fontFamily = Sans,
                fontSize = 9.sp,
                color = Danger,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        LoadRow(
            label = stringResource(R.string.selfchk_label_storage),
            detail = if (storage.totalBytes == null || storage.freeBytes == null) {
                stringResource(R.string.selfchk_unavailable)
            } else {
                stringResource(
                    R.string.selfchk_storage_detail,
                    formatBytes(storage.freeBytes),
                    formatBytes(storage.totalBytes),
                )
            },
            percent = storage.usedPercent,
        )
    }
}

@Composable
private fun LoadRow(label: String, detail: String, percent: Int?) {
    val color = loadColor(percent)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontFamily = Sans, fontSize = 10.sp, color = Gray)
            Text(
                percent?.let { "$it %" } ?: "--",
                fontFamily = Mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        if (percent != null) {
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = color,
                trackColor = SurfaceAlt,
            )
        }
        Text(
            detail,
            fontFamily = Mono,
            fontSize = 8.sp,
            color = Gray,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ─── 传感器 ──────────────────────────────────────────────────────────────────

@Composable
private fun SensorPanel(sensors: List<SensorEntry>) {
    TermPanel(stringResource(R.string.selfchk_panel_sensors)) {
        val shown = sensors.take(MAX_SENSORS_SHOWN)
        val common = shown.filter { it.isCommon }
        val others = shown.filterNot { it.isCommon }
        val onlyFirst = if (sensors.size > shown.size) {
            " · " + stringResource(R.string.selfchk_sensor_only_first, shown.size)
        } else {
            ""
        }
        Text(
            stringResource(
                R.string.selfchk_sensor_counts,
                sensors.size,
                sensors.count { it.isCommon },
            ) + onlyFirst,
            fontFamily = Mono,
            fontSize = 8.sp,
            color = Gray,
        )
        if (shown.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            EmptyHint(stringResource(R.string.selfchk_sensors_empty))
        } else {
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                if (common.isNotEmpty()) {
                    item { SensorGroupLabel(stringResource(R.string.selfchk_sensors_common), common.size) }
                    items(common) { sensor -> SensorRow(sensor) }
                }
                if (others.isNotEmpty()) {
                    item { SensorGroupLabel(stringResource(R.string.selfchk_sensors_other), others.size) }
                    items(others) { sensor -> SensorRow(sensor) }
                }
            }
        }
    }
}

@Composable
private fun SensorGroupLabel(text: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "▸ $text · $count",
            fontFamily = Sans,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Gold,
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f).height(1.dp).background(BorderDim))
    }
}

@Composable
private fun SensorRow(sensor: SensorEntry) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Star(if (sensor.isCommon) Gold else BorderDim, 6.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                sensor.typeName,
                fontFamily = Sans,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = White,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (sensor.isCommon) stringResource(R.string.selfchk_tag_common)
                else stringResource(R.string.selfchk_tag_other),
                fontFamily = Sans,
                fontSize = 8.sp,
                color = if (sensor.isCommon) Gold else Gray,
            )
        }
        Text(sensor.name, fontFamily = Mono, fontSize = 9.sp, color = Gold)
        Text(
            stringResource(
                R.string.selfchk_sensor_detail,
                sensor.vendor,
                formatSensorFloat(sensor.resolution),
                formatSensorFloat(sensor.powerMa),
            ),
            fontFamily = Mono,
            fontSize = 8.sp,
            color = Gray,
        )
    }
}

// ─── 网络 ────────────────────────────────────────────────────────────────────

@Composable
private fun NetworkPanel(n: NetworkSnapshot) {
    TermPanel(stringResource(R.string.selfchk_panel_wifi)) {
        when {
            n.wifiEnabled == false -> Text(
                stringResource(R.string.selfchk_wifi_disabled),
                fontFamily = Sans,
                fontSize = 9.sp,
                color = Danger,
            )
            n.wifiEnabled == null -> Text(
                stringResource(R.string.selfchk_wifi_state_unavailable),
                fontFamily = Sans,
                fontSize = 9.sp,
                color = Gray,
            )
            !n.connected -> Text(
                stringResource(R.string.selfchk_wifi_disconnected),
                fontFamily = Sans,
                fontSize = 9.sp,
                color = Gray,
            )
            else -> {
                PanelStat("SSID", n.ssid ?: "--")
                PanelStat("BSSID", n.bssid ?: "--")
                PanelStat(
                    stringResource(R.string.selfchk_stat_signal),
                    n.rssiDbm?.let { dbm ->
                        "$dbm dBm" + (n.signalLevel?.let { level -> " ($level/4)" } ?: "")
                    } ?: "--",
                    valueColor = signalColor(n.rssiDbm),
                )
                PanelStat(
                    stringResource(R.string.selfchk_stat_link_speed),
                    n.linkSpeedMbps?.let { "$it Mbps" } ?: "--",
                )
                if (n.txLinkSpeedMbps != null || n.rxLinkSpeedMbps != null) {
                    PanelStat(
                        stringResource(R.string.selfchk_stat_tx_rx_speed),
                        "${n.txLinkSpeedMbps ?: "--"} / ${n.rxLinkSpeedMbps ?: "--"} Mbps",
                    )
                }
                PanelStat(
                    stringResource(R.string.selfchk_stat_freq_channel),
                    n.frequencyMhz?.let { freq ->
                        "$freq MHz" + (n.channel?.let { ch ->
                            " · " + stringResource(R.string.selfchk_channel_suffix, ch)
                        } ?: "")
                    } ?: "--",
                )
                PanelStat(stringResource(R.string.selfchk_stat_local_ip), n.localIp ?: "--")
                PanelStat(stringResource(R.string.selfchk_stat_gateway), n.gatewayIp ?: "--")
                PanelStat(
                    stringResource(R.string.selfchk_stat_wifi_standard),
                    n.wifiStandard ?: "--",
                )
            }
        }
        if (!n.locationGranted) {
            Text(
                stringResource(R.string.selfchk_warn_no_location),
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Warn,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ─── 电池数据值显示映射 ──────────────────────────────────────────────────────

/** 电池健康数据值（固定中文，供结论判定）→ 当前语言显示文本 */
@Composable
private fun batteryHealthLabel(raw: String): String = when (raw) {
    "良好" -> stringResource(R.string.mgr_selfchk_health_good)
    "过热" -> stringResource(R.string.mgr_selfchk_health_overheat)
    "失效" -> stringResource(R.string.mgr_selfchk_health_dead)
    "过压" -> stringResource(R.string.mgr_selfchk_health_over_voltage)
    "过冷" -> stringResource(R.string.mgr_selfchk_health_cold)
    "故障(未指明)" -> stringResource(R.string.mgr_selfchk_health_unspecified_failure)
    "未知" -> stringResource(R.string.mgr_selfchk_unknown)
    "不可用" -> stringResource(R.string.mgr_selfchk_unavailable)
    else -> raw
}

/** 充放电方向数据值 → 当前语言显示文本 */
@Composable
private fun batteryDirectionLabel(raw: String): String = when (raw) {
    "充电中" -> stringResource(R.string.mgr_selfchk_dir_charging)
    "已充满(外接电源)" -> stringResource(R.string.mgr_selfchk_dir_full_external)
    "放电中" -> stringResource(R.string.mgr_selfchk_dir_discharging)
    "未充电" -> stringResource(R.string.mgr_selfchk_dir_not_charging)
    "充电中(按电流符号)" -> stringResource(R.string.mgr_selfchk_dir_charging_by_current)
    "放电中(按电流符号)" -> stringResource(R.string.mgr_selfchk_dir_discharging_by_current)
    "无净电流" -> stringResource(R.string.mgr_selfchk_dir_no_net_current)
    "不可用" -> stringResource(R.string.mgr_selfchk_unavailable)
    else -> raw
}

/** 电池状态数据值 → 当前语言显示文本 */
@Composable
private fun batteryStatusLabel(raw: String): String = when (raw) {
    "充电中" -> stringResource(R.string.mgr_selfchk_status_charging)
    "放电中" -> stringResource(R.string.mgr_selfchk_status_discharging)
    "已充满" -> stringResource(R.string.mgr_selfchk_status_full)
    "未充电" -> stringResource(R.string.mgr_selfchk_status_not_charging)
    "未知" -> stringResource(R.string.mgr_selfchk_unknown)
    "不可用" -> stringResource(R.string.mgr_selfchk_unavailable)
    else -> raw
}

/** 供电来源数据值（可含多个记号，以 " / " 连接）→ 当前语言显示文本 */
@Composable
private fun batteryPluggedLabel(raw: String): String {
    val parts = raw.split(" / ")
    val mapped = ArrayList<String>(parts.size)
    for (token in parts) {
        mapped.add(
            when (token) {
                "无线充电" -> stringResource(R.string.mgr_selfchk_plug_wireless)
                "底座" -> stringResource(R.string.mgr_selfchk_plug_dock)
                "未连接电源" -> stringResource(R.string.mgr_selfchk_plug_unplugged)
                "不可用" -> stringResource(R.string.mgr_selfchk_unavailable)
                else -> token
            },
        )
    }
    return mapped.joinToString(" / ")
}

/** 电池技术数据值 → 当前语言显示文本（技术名保持原样） */
@Composable
private fun batteryTechnologyLabel(raw: String): String = when (raw) {
    "不可用" -> stringResource(R.string.mgr_selfchk_unavailable)
    else -> raw
}

// ─── 格式化与配色 ────────────────────────────────────────────────────────────

/** 字节数 → 易读单位 */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}

/** 微安 → mA(保留符号,正=充电/负=放电) */
private fun formatCurrentUa(ua: Long): String = when {
    abs(ua) >= 1000L -> String.format(Locale.US, "%+.1f mA", ua / 1000f)
    else -> "$ua µA"
}

/** 传感器分辨率/功耗的紧凑格式 */
private fun formatSensorFloat(value: Float): String = when {
    value == 0f -> "0"
    abs(value) < 0.01f -> String.format(Locale.US, "%.2e", value)
    else -> String.format(Locale.US, "%.3f", value)
}

private fun temperatureColor(t: Float?): Color = when {
    t == null -> Gray
    t > 45f -> Danger
    t > 38f -> Gold
    else -> White
}

private fun currentColor(ua: Long?): Color = when {
    ua == null -> Gray
    ua > 0L -> TacGreen
    ua < 0L -> White
    else -> Gray
}

private fun loadColor(percent: Int?): Color = when {
    percent == null -> Gray
    percent >= 90 -> Danger
    percent >= 70 -> Warn
    else -> TacGreen
}

private fun signalColor(dbm: Int?): Color = when {
    dbm == null -> Gray
    dbm >= -55 -> TacGreen
    dbm >= -70 -> Gold
    else -> Danger
}

// ─── 能力矩阵:root / monitor / 注入 / LOHS / BLE 的权威判定 ──────────────────
// 与注入面板共享同一份探测单例(CapabilityProbe),这里的结论就是攻击类工具
// 在本机是否可用的唯一事实来源;红/黄状态如实展示缺什么,绝不假装可用。

@Composable
private fun CapabilityMatrixPanel() {
    val context = LocalContext.current
    val caps by CapabilityProbe.get(context).capabilities.collectAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var probing by remember { mutableStateOf(false) }

    TermPanel(stringResource(R.string.selfchk_panel_caps)) {
        // 结论行 + 完整探测按钮(full 探测会触发 su,可能弹 root 授权)
        val unlocked = caps.externalToolModeUnlocked
        val known = caps.root != RootStatus.Unknown
        val verdict = when {
            unlocked -> stringResource(R.string.selfchk_caps_unlocked) to TacGreen
            known -> stringResource(R.string.selfchk_caps_locked) to Danger
            else -> stringResource(R.string.selfchk_caps_unknown) to Gray
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                verdict.first,
                fontFamily = Mono, fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                color = verdict.second, modifier = Modifier.weight(1f),
            )
            Surface(
                modifier = Modifier.clickable {
                    haptic.hud()
                    if (probing) return@clickable
                    probing = true
                    scope.launch {
                        CapabilityProbe.get(context).probeNow(full = true)
                        probing = false
                    }
                },
                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                color = Gold.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
            ) {
                Text(
                    if (probing) stringResource(R.string.selfchk_caps_probing) else stringResource(R.string.selfchk_caps_probe),
                    fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        CapsRow(
            stringResource(R.string.selfchk_caps_root),
            when (caps.root) {
                RootStatus.Granted -> CapabilityStatus.Supported
                RootStatus.Denied -> CapabilityStatus.PermissionDenied
                RootStatus.NoRoot -> CapabilityStatus.Unsupported
                RootStatus.Unknown -> CapabilityStatus.Unknown
            },
            caps.rootDetail,
        )
        CapsRow(stringResource(R.string.selfchk_caps_monitor), caps.wifiMonitorMode.status, caps.wifiMonitorMode.detail)
        CapsRow(stringResource(R.string.selfchk_caps_injection), caps.wifiFrameInjection.status, caps.wifiFrameInjection.detail)
        CapsRow(stringResource(R.string.selfchk_caps_lohs), caps.localOnlyHotspot.status, caps.localOnlyHotspot.detail)
        CapsRow(stringResource(R.string.selfchk_caps_ble), caps.bleAdvertising.status, caps.bleAdvertising.detail)
    }
}

@Composable
private fun CapsRow(label: String, status: CapabilityStatus, detail: String) {
    val color = when (status) {
        CapabilityStatus.Supported -> TacGreen
        CapabilityStatus.PermissionDenied -> Warn
        CapabilityStatus.Unsupported -> Danger
        CapabilityStatus.Unknown -> Gray
    }
    val tag = when (status) {
        CapabilityStatus.Supported -> "OK"
        CapabilityStatus.PermissionDenied -> "PERM"
        CapabilityStatus.Unsupported -> "NO"
        CapabilityStatus.Unknown -> "--"
    }
    Column(Modifier.padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontFamily = Sans, fontSize = 10.sp, color = Gray)
            Text(tag, fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
        }
        if (detail.isNotBlank() && status != CapabilityStatus.Supported) {
            Text(detail, fontFamily = Mono, fontSize = 7.5.sp, color = color.copy(alpha = 0.85f), lineHeight = 11.sp)
        }
    }
}
