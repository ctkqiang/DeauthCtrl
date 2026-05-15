package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
    ) {
        // 扫描按钮
        Button(
            onClick = { viewModel.scanNetworks() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isFlooding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = "[ 扫描 Wi-Fi ]",
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.height(8.dp))

        // 扫描结果
        Text(
            text = "附近网络",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))

        when (val state = scanState) {
            is ScanState.Idle -> {
                Text(
                    text = "点击扫描发现附近的 Wi-Fi 网络。",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            is ScanState.Scanning -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "扫描中…",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is ScanState.Results -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.networks, key = { it.bssid }) { network ->
                        NetworkRow(
                            network = network,
                            isSelected = selectedNetwork?.bssid == network.bssid,
                            onClick = {
                                selectedNetwork = network
                                viewModel.selectTarget(network)
                            },
                        )
                    }
                }
            }
            is ScanState.Error -> {
                Text(
                    text = state.message,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 目标显示
        if (selectedNetwork != null) {
            Text(
                text = "目标: ${selectedNetwork!!.ssid} (${selectedNetwork!!.bssid})",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 持续时间
        Spacer(Modifier.height(8.dp))
        Text(
            text = "泛洪时长: ${durationSeconds}秒",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = durationSeconds.toFloat(),
            onValueChange = { durationSeconds = it.toInt().coerceIn(1, 60) },
            valueRange = 1f..60f,
            steps = 58,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isFlooding,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline,
            ),
        )

        Spacer(Modifier.height(8.dp))

        // 开始/停止泛洪按钮
        Button(
            onClick = {
                if (isFlooding) {
                    viewModel.stopFlood()
                } else {
                    viewModel.setDuration(durationSeconds)
                    viewModel.startFlood()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFlooding) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            enabled = selectedNetwork != null || isFlooding,
        ) {
            Text(
                text = when {
                    isFlooding -> "[ 停止泛洪 ]"
                    selectedNetwork == null -> "[ 请先选择目标 ]"
                    else -> "[ 开始 Beacon 泛洪 ]"
                },
                fontFamily = FontFamily.Monospace,
            )
        }

        // 状态
        when (val result = hotspotResult) {
            is HotspotResult.Created -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "邪恶双子已激活: ${result.ssid}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            is HotspotResult.Progress -> {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { result.cycle.toFloat() / result.totalCycles },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = "泛洪中: ${result.cycle}/${result.totalCycles} 轮",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is HotspotResult.Completed -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "泛洪完成。热点已关闭。",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            is HotspotResult.Error -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "错误: ${result.message}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            null -> { /* 空闲 */ }
        }
    }
}

@Composable
private fun NetworkRow(
    network: WifiNetwork,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val signalPct = ((network.signalStrength + 100) * 2).coerceIn(0, 100)
    val signalBar = when {
        signalPct > 75 -> "████"
        signalPct > 50 -> "███░"
        signalPct > 25 -> "██░░"
        else -> "█░░░"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface,
        ),
        border = if (isSelected)
            CardDefaults.outlinedCardBorder()
        else
            null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = network.ssid.ifBlank { "<隐藏>" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = network.bssid,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = signalBar,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${network.signalStrength}dBm",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
