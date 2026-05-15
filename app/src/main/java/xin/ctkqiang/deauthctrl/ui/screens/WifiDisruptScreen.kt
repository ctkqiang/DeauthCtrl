package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.model.HotspotResult
import xin.ctkqiang.deauthctrl.model.ScanState
import xin.ctkqiang.deauthctrl.model.WifiNetwork
import xin.ctkqiang.deauthctrl.viewmodel.WifiDisruptViewModel

@Composable
fun WifiDisruptScreen(viewModel: WifiDisruptViewModel) {
    val scanState by viewModel.scanState.collectAsState()
    val hotspotResult by viewModel.hotspotResult.collectAsState()
    val isFlooding by viewModel.isFlooding.collectAsState()

    var selectedNetwork by remember { mutableStateOf<WifiNetwork?>(null) }
    var durationSeconds by remember { mutableIntStateOf(viewModel.getDuration()) }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background).padding(10.dp),
    ) {
        // ═══ 扫描按钮 ═══
        Button(
            onClick = { viewModel.scanNetworks() },
            modifier = Modifier.fillMaxWidth(), enabled = !isFlooding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { Text("[ 扫描 Wi-Fi ]", fontFamily = FontFamily.Monospace, fontSize = 14.sp) }

        Spacer(Modifier.height(6.dp))
        Text("> 附近网络", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(4.dp))

        // ═══ 扫描结果区 ═══
        when (val st = scanState) {
            is ScanState.Idle -> Text("点击扫描发现附近的 Wi-Fi 网络。",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp))

            is ScanState.Scanning -> Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("扫描中…", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is ScanState.Results -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(st.networks, key = { it.bssid }) { net ->
                    DetailedNetworkCard(
                        network = net,
                        isSelected = selectedNetwork?.bssid == net.bssid,
                        onClick = { selectedNetwork = net; viewModel.selectTarget(net) },
                    )
                }
            }

            is ScanState.Error -> Text(st.message, fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(Modifier.height(6.dp))

        // ═══ 目标确认 ═══
        if (selectedNetwork != null) {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(2.dp),
                border = CardDefaults.outlinedCardBorder()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("> 目标锁定", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary)
                    Text("SSID : ${selectedNetwork!!.ssid}",
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("BSSID: ${selectedNetwork!!.bssid}",
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("信道: ${selectedNetwork!!.channel} | ${selectedNetwork!!.band} | ${selectedNetwork!!.security} | ${selectedNetwork!!.frequency}MHz",
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text("> 泛洪时长: ${durationSeconds}s", fontFamily = FontFamily.Monospace,
            fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        Slider(value = durationSeconds.toFloat(),
            onValueChange = { durationSeconds = it.toInt().coerceIn(1, 60) },
            valueRange = 1f..60f, steps = 14, modifier = Modifier.fillMaxWidth(),
            enabled = !isFlooding,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline))

        Spacer(Modifier.height(6.dp))

        Button(onClick = {
            if (isFlooding) viewModel.stopFlood()
            else { viewModel.setDuration(durationSeconds); viewModel.startFlood() }
        }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFlooding) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary),
            enabled = selectedNetwork != null || isFlooding) {
            Text(when {
                isFlooding -> "[ 停止泛洪 ]"
                selectedNetwork == null -> "[ 请先选择目标 ]"
                else -> "[ 开始 Beacon 泛洪 ]"
            }, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }

        // ═══ 状态 ═══
        when (val r = hotspotResult) {
            is HotspotResult.Created -> {
                Spacer(Modifier.height(4.dp))
                Text("邪恶双子已激活: ${r.ssid}", fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            is HotspotResult.Progress -> {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { r.cycle.toFloat() / r.totalCycles },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline)
                Text("泛洪中: ${r.cycle}/${r.totalCycles} 轮",
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is HotspotResult.Completed -> {
                Spacer(Modifier.height(4.dp))
                Text("泛洪完成。热点已关闭。", fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            is HotspotResult.Error -> {
                Spacer(Modifier.height(4.dp))
                Text("错误: ${r.message}", fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
            null -> {}
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 网路详情卡片 — 包含所有关键参数
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DetailedNetworkCard(
    network: WifiNetwork,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val pct = ((network.signalStrength + 100) * 2).coerceIn(0, 100)
    val sigColor = when {
        pct > 65 -> MaterialTheme.colorScheme.primary
        pct > 30 -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    val bar = when {
        pct > 87 -> "▉▉▉▉"
        pct > 62 -> "▉▉▉▁"
        pct > 37 -> "▉▉▁▁"
        pct > 12 -> "▉▁▁▁"
        else -> "▁▁▁▁"
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surface),
        border = if (isSelected) CardDefaults.outlinedCardBorder() else null,
        shape = RoundedCornerShape(2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {

            // ── 行1: SSID | 信号条 | dBm ──
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(network.ssid.ifBlank { "<隐藏>" }, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bar, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = sigColor)
                    Spacer(Modifier.width(3.dp))
                    Text("${network.signalStrength}dBm", fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp, color = sigColor)
                }
            }

            Spacer(Modifier.height(3.dp))

            // ── 行2: BSSID ──
            Text("BSSID  ${network.bssid}", fontFamily = FontFamily.Monospace,
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(2.dp))

            // ── 行3: 信道 / 频段 / 加密 / 频率 — 红色标签组 ──
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("信道 ${network.channel}")
                Chip(network.band)
                Chip(network.security)
                Chip("${network.frequency}MHz")
            }

            // ── 行4: 能力字串 ──
            if (network.capabilities.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(network.capabilities, fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text("[ $text ]", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
        color = MaterialTheme.colorScheme.primary)
}
