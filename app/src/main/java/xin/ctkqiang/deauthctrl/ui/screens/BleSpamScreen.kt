package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(10.dp),
    ) {
        // ═══ 攻击协议选择 ═══
        Text("> 攻击协议", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(3.dp))
        var dropdownExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { dropdownExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("[ ${selectedProfile.displayName} ]", fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }) {
                profiles.forEach { profile ->
                    DropdownMenuItem(text = {
                        Column {
                            Text(profile.displayName, fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp)
                            Text(profile.description, fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }, onClick = {
                        selectedProfile = profile; viewModel.setProfile(profile)
                        dropdownExpanded = false
                    })
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("> 广播间隔: ${intervalMs.toLong()}ms", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Slider(value = intervalMs, onValueChange = { intervalMs = it },
            valueRange = 20f..100f, steps = 7, modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline))

        Spacer(Modifier.height(6.dp))

        // ═══ 开始/停止 ═══
        Button(onClick = {
            viewModel.setInterval(intervalMs.toLong())
            if (isRunning) viewModel.stopSpam() else viewModel.startSpam()
        }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary)) {
            Text(if (isRunning) "[ 停止攻击 ]" else "[ 开始 BLE 泛洪 ]",
                fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }

        error?.let { msg ->
            Spacer(Modifier.height(6.dp))
            Text("! $msg", color = MaterialTheme.colorScheme.error,
                fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }

        if (isRunning && log.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            val ok = log.count { it.success }
            val fail = log.count { !it.success }
            val elapsed = maxOf(1, System.currentTimeMillis() - log.first().timestamp)
            Row(modifier = Modifier.fillMaxWidth().background(
                MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp)).padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("发包:$ok 失败:$fail", fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                Text("~${ok * 1000L / elapsed}pkt/s", fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ═══ 日志头 ═══
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("> TX LOG [${log.size}]", fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { viewModel.clearLog() }, contentPadding = PaddingValues(0.dp)) {
                Text("清空", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ═══ 终端窗口日志区 ═══
        TerminalLogWindow(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            if (log.isEmpty()) {
                item {
                    Text("等待发送…", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp))
                }
            }
            items(log, key = { "${it.index}_${it.timestamp}" }) { entry ->
                HexDumpEntry(entry, timeFormat)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 终端窗口容器 — 黑底红框，CRT 扫描线
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TerminalLogWindow(
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val scanlineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
    val titleBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    val titleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)

    Box(modifier = modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, borderColor, RoundedCornerShape(2.dp))
                .background(surfaceColor)
                .drawBehind {
                    var y = 0f
                    while (y < size.height) {
                        drawLine(scanlineColor,
                            Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += 4f
                    }
                }
                .padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
        // 终端标题栏
        Box(modifier = Modifier
            .fillMaxWidth()
            .background(titleBgColor,
                RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text("┌─ BLE TX DUMP ──────────────────────────────────────────────┐",
                fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                color = titleColor)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 单条 Hex Dump 条目 — 紧凑、适配手机屏幕
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HexDumpEntry(entry: BleAdvertLogEntry, tf: SimpleDateFormat) {
    val ok = entry.success
    val accent = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(1.dp))
        .border(0.5.dp, if (ok) MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
        .padding(5.dp),
    ) {
        // ── 头部: #序号 时间 协议 状态 ──
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("#${entry.index.toString().padStart(4,'0')}",
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = dim)
            Text(tf.format(java.util.Date(entry.timestamp)),
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = dim)
            Text(entry.profile.displayName.take(10),
                fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Text(if (ok) "OK" else "FAIL", fontFamily = FontFamily.Monospace,
                fontSize = 10.sp, color = accent)
        }

        Spacer(Modifier.height(3.dp))

        // ── Hex Dump 主体: 8 bytes/line，适合手机屏 ──
        val bytes = entry.payloadBytes
        if (bytes.isNotEmpty()) {
            Text(
                text = formatHexDump8(bytes),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(1.dp))
                    .padding(3.dp),
            )
        } else {
            Text(entry.payloadHex.take(48).chunked(2).joinToString(" "),
                fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 紧凑 Hex Dump 格式化: 每行 8 字节，适配手机屏宽
// 格式: OFFSET  XX XX XX XX  XX XX XX XX  |ASCII....|
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatHexDump8(bytes: ByteArray): String {
    val sb = StringBuilder()
    val perLine = 8
    var off = 0
    while (off < bytes.size) {
        sb.append(off.toString(16).padStart(4, '0').uppercase())
        sb.append("  ")
        for (i in 0 until perLine) {
            if (i == 4) sb.append(" ")
            if (off + i < bytes.size) {
                sb.append((bytes[off + i].toInt() and 0xFF)
                    .toString(16).padStart(2, '0').uppercase())
                sb.append(" ")
            } else sb.append("   ")
        }
        sb.append(" |")
        for (i in 0 until perLine) {
            if (off + i < bytes.size) {
                val b = bytes[off + i].toInt() and 0xFF
                sb.append(if (b in 32..126) b.toChar() else '.')
            }
        }
        sb.append("|")
        if (off + perLine < bytes.size) sb.append("\n")
        off += perLine
    }
    return sb.toString()
}
