package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xin.ctkqiang.deauthctrl.model.BleAdvertLogEntry
import xin.ctkqiang.deauthctrl.model.BlePayloadProfile
import xin.ctkqiang.deauthctrl.viewmodel.BleSpamViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BleSpamScreen(viewModel: BleSpamViewModel) {
    val log by viewModel.log.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedProfile by remember { mutableStateOf(viewModel.getCurrentProfile()) }
    var intervalMs by remember { mutableFloatStateOf(viewModel.getCurrentInterval().toFloat()) }
    val profiles = remember { viewModel.getProfiles() }

    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) {
            listState.animateScrollToItem(log.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
    ) {
        // 攻击协议选择
        Text(
            text = "攻击协议",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))

        var dropdownExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { dropdownExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "[ ${selectedProfile.displayName} ]",
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
            ) {
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    profile.displayName,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    profile.description,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            selectedProfile = profile
                            viewModel.setProfile(profile)
                            dropdownExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 广播间隔
        Text(
            text = "广播间隔: ${intervalMs.toLong()}ms",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = intervalMs,
            onValueChange = { intervalMs = it },
            valueRange = 20f..100f,
            steps = 15,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("20ms", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("100ms", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(12.dp))

        // 开始/停止按钮
        Button(
            onClick = {
                viewModel.setInterval(intervalMs.toLong())
                if (isRunning) {
                    viewModel.stopSpam()
                } else {
                    viewModel.startSpam()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = if (isRunning) "[ 停止攻击 ]" else "[ 开始 BLE 泛洪 ]",
                fontFamily = FontFamily.Monospace,
            )
        }

        // 错误显示
        error?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "! $msg",
                color = MaterialTheme.colorScheme.error,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // 日志头
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "发送日志 (${log.size})",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = { viewModel.clearLog() }) {
                Text(
                    "清空",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 日志列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (log.isEmpty()) {
                item {
                    Text(
                        text = "等待发送…",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(log, key = { it.timestamp }) { entry ->
                LogEntryRow(entry, timeFormat)
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: BleAdvertLogEntry, timeFormat: SimpleDateFormat) {
    val statusColor = if (entry.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val statusChar = if (entry.success) ">" else "X"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
    ) {
        Text(
            text = timeFormat.format(Date(entry.timestamp)),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = " $statusChar ",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
        )
        Text(
            text = entry.profile.displayName.take(12),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = " ${entry.payloadHex.take(20)}",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
