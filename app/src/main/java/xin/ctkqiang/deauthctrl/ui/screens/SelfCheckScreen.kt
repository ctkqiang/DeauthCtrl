package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.manager.BatterySnapshot
import xin.ctkqiang.deauthctrl.manager.NetworkSnapshot
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
        !loaded && state.loading -> "自检读取中…"
        !loaded -> "等待读取"
        state.alerts.isNotEmpty() -> "存在告警 · ${state.alerts.size} 项"
        partial -> "未发现告警 · 部分项不可用"
        else -> "全部正常"
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("硬件自检 · SELF CHECK", back)
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
            BatteryPanel(state.battery)
            ThermalPanel(state.thermal)
            LoadPanel(state)
            SensorPanel(state.sensors)
            NetworkPanel(state.network)
            Text(
                "数据说明 · 电池来自 BatteryManager 与 ACTION_BATTERY_CHANGED 粘性广播,温度/电流为系统上报值," +
                    "厂商采样口径可能不同。CPU 使用率为 /proc/stat 两次采样(窗口 250 ms)的估算值;" +
                    "内存来自 ActivityManager.MemoryInfo;存储为 /data 的 StatFs。热区读取 /sys/class/thermal," +
                    "路径与单位因厂商/内核而异,读不到时显示不可用;传感器与 Wi-Fi 均为系统真实返回值,缺失字段显示 --。",
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
                GhostButton(if (state.loading) "读取中" else "刷新", onClick = onRefresh)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    if (loaded) {
                        append("更新 ")
                        append(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(state.updatedAtMs)))
                        append(" · ")
                    }
                    append("传感器 ${state.sensors.size} 项 · 热区 ${state.thermal.readableZones} 个")
                },
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
    TermPanel("电池") {
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
            Text("电量", fontFamily = Sans, fontSize = 11.sp, color = Gray)
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
        PanelStat("电流", b.currentNowUa?.let(::formatCurrentUa) ?: "--", valueColor = currentColor(b.currentNowUa))
        PanelStat(
            "温度",
            b.temperatureC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "--",
            valueColor = temperatureColor(b.temperatureC),
        )
        PanelStat(
            "健康",
            b.health,
            valueColor = when {
                b.healthAlert -> Danger
                b.health == "良好" -> TacGreen
                else -> Gray
            },
        )
        PanelStat("充放电方向", b.direction)
        PanelStat("电池状态", b.status)
        PanelStat("供电来源", b.plugged)
        PanelStat("电压", b.voltageMv?.let { "$it mV" } ?: "--")
        PanelStat(
            "电荷计数器",
            b.chargeCounterUah?.let { String.format(Locale.US, "%.0f mAh", it / 1000f) } ?: "--",
        )
        PanelStat("电池技术", b.technology)
        if (b.present == false) {
            Text(
                "[!] 系统上报本机无电池(可能为模拟器或无电池设备)",
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
    TermPanel("热区") {
        if (!t.available) {
            Text(
                "不可用 —— 本机未暴露 /sys/class/thermal 的 thermal_zone 读数",
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
                    "共读取 ${t.readableZones} 个热区,仅显示最高 ${t.topZones.size} 个",
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = Gray,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "阈值: > 45 °C 红色 · > 38 °C 金色;温度为内核上报值",
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
    TermPanel("CPU / 内存 / 存储") {
        LoadRow(
            label = "CPU 使用率",
            detail = if (cpu.usagePercent == null) {
                "不可用 —— /proc/stat 读取失败"
            } else {
                "两次 /proc/stat 采样,窗口 ${cpu.sampleWindowMs} ms · ${cpu.coreCount} 核"
            },
            percent = cpu.usagePercent,
        )
        LoadRow(
            label = "内存",
            detail = if (mem.totalBytes == null || mem.availBytes == null) {
                "不可用"
            } else {
                "可用 ${formatBytes(mem.availBytes)} / 总 ${formatBytes(mem.totalBytes)}"
            },
            percent = mem.usedPercent,
        )
        if (mem.lowMemory) {
            Text(
                "[!] 系统报告处于低内存状态",
                fontFamily = Sans,
                fontSize = 9.sp,
                color = Danger,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        LoadRow(
            label = "存储 /data",
            detail = if (storage.totalBytes == null || storage.freeBytes == null) {
                "不可用"
            } else {
                "剩余 ${formatBytes(storage.freeBytes)} / 总 ${formatBytes(storage.totalBytes)}"
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
    TermPanel("传感器清单") {
        val shown = sensors.take(MAX_SENSORS_SHOWN)
        val common = shown.filter { it.isCommon }
        val others = shown.filterNot { it.isCommon }
        Text(
            buildString {
                append("共 ${sensors.size} 项 · 常用 ${sensors.count { it.isCommon }} 项")
                if (sensors.size > shown.size) append(" · 仅显示前 ${shown.size} 项")
            },
            fontFamily = Mono,
            fontSize = 8.sp,
            color = Gray,
        )
        if (shown.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            EmptyHint("系统未返回任何传感器 —— 模拟器或无 HAL 时为空")
        } else {
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                if (common.isNotEmpty()) {
                    item { SensorGroupLabel("常用传感器", common.size) }
                    items(common) { sensor -> SensorRow(sensor) }
                }
                if (others.isNotEmpty()) {
                    item { SensorGroupLabel("其他传感器", others.size) }
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
                if (sensor.isCommon) "常用" else "其他",
                fontFamily = Sans,
                fontSize = 8.sp,
                color = if (sensor.isCommon) Gold else Gray,
            )
        }
        Text(sensor.name, fontFamily = Mono, fontSize = 9.sp, color = Gold)
        Text(
            "厂商 ${sensor.vendor} · 分辨率 ${formatSensorFloat(sensor.resolution)} · 功耗 ${formatSensorFloat(sensor.powerMa)} mA",
            fontFamily = Mono,
            fontSize = 8.sp,
            color = Gray,
        )
    }
}

// ─── 网络 ────────────────────────────────────────────────────────────────────

@Composable
private fun NetworkPanel(n: NetworkSnapshot) {
    TermPanel("Wi-Fi 链路") {
        when {
            n.wifiEnabled == false -> Text(
                "[!] Wi-Fi 已关闭 —— 无链路读数",
                fontFamily = Sans,
                fontSize = 9.sp,
                color = Danger,
            )
            n.wifiEnabled == null -> Text(
                "Wi-Fi 状态不可用",
                fontFamily = Sans,
                fontSize = 9.sp,
                color = Gray,
            )
            !n.connected -> Text(
                "Wi-Fi 未连接 —— 链路信息不可用",
                fontFamily = Sans,
                fontSize = 9.sp,
                color = Gray,
            )
            else -> {
                PanelStat("SSID", n.ssid ?: "--")
                PanelStat("BSSID", n.bssid ?: "--")
                PanelStat(
                    "信号",
                    n.rssiDbm?.let { dbm ->
                        "$dbm dBm" + (n.signalLevel?.let { level -> " ($level/4)" } ?: "")
                    } ?: "--",
                    valueColor = signalColor(n.rssiDbm),
                )
                PanelStat("链路速率", n.linkSpeedMbps?.let { "$it Mbps" } ?: "--")
                if (n.txLinkSpeedMbps != null || n.rxLinkSpeedMbps != null) {
                    PanelStat(
                        "收发速率",
                        "${n.txLinkSpeedMbps ?: "--"} / ${n.rxLinkSpeedMbps ?: "--"} Mbps",
                    )
                }
                PanelStat(
                    "频率 / 信道",
                    n.frequencyMhz?.let { freq ->
                        "$freq MHz" + (n.channel?.let { ch -> " · 信道 $ch" } ?: "")
                    } ?: "--",
                )
                PanelStat("本机 IP", n.localIp ?: "--")
                PanelStat("网关", n.gatewayIp ?: "--")
                PanelStat("Wi-Fi 标准", n.wifiStandard ?: "--")
            }
        }
        if (!n.locationGranted) {
            Text(
                "[!] 未授予定位权限,SSID/BSSID 会被系统隐去",
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Warn,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
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
