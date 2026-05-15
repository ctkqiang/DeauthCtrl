package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
        // ── 攻击协议选择 ──────────────────────────────────────────────
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

        // ── 广播间隔 ─────────────────────────────────────────────────
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

        // ── 开始/停止按钮 ─────────────────────────────────────────────
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

        // ── 错误显示 ─────────────────────────────────────────────────
        error?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                shape = RoundedCornerShape(2.dp),
            ) {
                Text(
                    text = "! $msg",
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        // ── 统计信息栏 (运行时显示) ──────────────────────────────────
        if (isRunning && log.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val successCount = log.count { it.success }
            val failCount = log.count { !it.success }
            val ppsEstimate = if (successCount > 0) (successCount * 1000L / maxOf(1, System.currentTimeMillis() - log.first().timestamp)) else 0L
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "发包: $successCount",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "失败: $failCount",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "速率: ~${ppsEstimate}pkt/s",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── 日志头 ───────────────────────────────────────────────────
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

        // ── Hex Dump 日志列表 ────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (log.isEmpty()) {
                item {
                    Text(
                        text = "等待发送…",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
                    )
                }
            }
            items(log, key = { "${it.index}_${it.timestamp}" }) { entry ->
                HexDumpLogEntry(entry, timeFormat)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Hex Dump 格式的日志条目
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 以类 Wireshark / hexdump 格式展示单条 BLE 广播日志。
 * 包含：序号、时间戳、协议名、16 进制 hex dump (偏移 + hex + ASCII)、状态指示。
 */
@Composable
private fun HexDumpLogEntry(entry: BleAdvertLogEntry, timeFormat: SimpleDateFormat) {
    val statusColor = if (entry.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val borderColor = if (entry.success) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        // ── 第一行: 序号、时间、协议、状态 ──────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "#${entry.index.toString().padStart(4, '0')}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = timeFormat.format(Date(entry.timestamp)),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.profile.displayName.take(14),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "TX:${entry.txPowerLevel}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (entry.success) "OK" else "FAIL",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
            )
        }

        Spacer(Modifier.height(3.dp))

        // ── 第二行: Hex dump ─────────────────────────────────────────
        val bytes = entry.payloadBytes
        if (bytes.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(1.dp))
                    .padding(4.dp),
            ) {
                Text(
                    text = buildHexDump(bytes),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
                )
            }
        } else {
            Text(
                text = entry.payloadHex.take(64).chunked(2).joinToString(" "),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 构建标准 hex dump 格式字符串，模仿 Wireshark/xxd 输出：
 * 每行: 偏移(4) | hex pairs (16 bytes) | ASCII 可打印字符
 *
 * 示例:
 * 0000  4c 00 05 02 a3 f1 6d 4e  12 7b 00 00 00 00 00 00  |L.....mN.{......|
 */
private fun buildHexDump(bytes: ByteArray): String {
    val sb = StringBuilder()
    val bytesPerLine = 16
    var offset = 0

    while (offset < bytes.size) {
        // 偏移地址
        sb.append(offset.toString(16).padStart(4, '0').uppercase())
        sb.append("  ")

        // 十六进制部分
        for (i in 0 until bytesPerLine) {
            if (i == 8) sb.append(" ") // 8 字节分隔
            if (offset + i < bytes.size) {
                sb.append((bytes[offset + i].toInt() and 0xFF).toString(16).padStart(2, '0').uppercase())
                sb.append(" ")
            } else {
                sb.append("   ")
            }
        }

        sb.append(" |")

        // ASCII 可视化部分
        for (i in 0 until bytesPerLine) {
            if (offset + i < bytes.size) {
                val b = bytes[offset + i].toInt() and 0xFF
                sb.append(if (b in 32..126) b.toChar() else '.')
            } else {
                sb.append(" ")
            }
        }
        sb.append("|\n")

        offset += bytesPerLine
    }

    return sb.toString().trimEnd('\n')
}
