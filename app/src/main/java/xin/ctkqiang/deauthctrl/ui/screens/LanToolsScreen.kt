package xin.ctkqiang.deauthctrl.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.FlingLogLevel
import xin.ctkqiang.deauthctrl.manager.FlingMode
import xin.ctkqiang.deauthctrl.manager.FlingState
import xin.ctkqiang.deauthctrl.manager.WolState
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.LogLine
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.PanelStat
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.LanToolsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 局域网工具 —— WOL 唤醒 + fling 兼容裸 TCP 管道
 *
 * 两个互不相关的局域网工具共用一页：
 * - WOL：构建 102 字节魔术包，UDP 广播到 255.255.255.255 与本网段定向广播；
 * - fling：一端监听、一端连接，裸字节流原样传输，EOF 即结束。
 *
 * 数据纪律：MAC / 地址 / 端口 / 字节数全部来自真实输入与 Socket 统计，
 * 读不到时显示 "--"；发送成功只代表内核已接手，不代表对端已收到。
 */
@Composable
fun LanToolsScreen(back: () -> Unit) {
    val vm: LanToolsViewModel = viewModel()
    val wolState by vm.wolState.collectAsState()
    val flingState by vm.flingState.collectAsState()

    var tab by remember { mutableStateOf(0) }

    // fling 配置与发送目标提升到页面层，供文件选择器回调读取最新值
    var flingMode by remember { mutableStateOf(FlingMode.RX) }
    var rxPort by remember { mutableStateOf("0") }
    var saveToFile by remember { mutableStateOf(false) }
    var txHost by remember { mutableStateOf("") }
    var txPort by remember { mutableStateOf("9000") }
    var txText by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !flingState.running) {
            vm.sendFile(txHost, txPort.toIntOrNull() ?: -1, uri)
        }
    }

    // 进入页面重新探测网段；离开页面停止接收监听（发送任务短促，随 ViewModel 收尾）
    DisposableEffect(Unit) {
        vm.refreshNetwork()
        onDispose { vm.stopReceive() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.lantools_title), back)

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(tab == 0, stringResource(R.string.lantools_tab_wol)) { tab = 0 }
                SelectChip(tab == 1, stringResource(R.string.lantools_tab_fling)) { tab = 1 }
            }
            Spacer(Modifier.height(10.dp))

            if (tab == 0) {
                WolTab(vm, wolState)
            } else {
                FlingTab(
                    vm = vm,
                    state = flingState,
                    mode = flingMode,
                    onMode = { flingMode = it },
                    rxPort = rxPort,
                    onRxPort = { rxPort = it },
                    saveToFile = saveToFile,
                    onSaveToFile = { saveToFile = it },
                    txHost = txHost,
                    onTxHost = { txHost = it },
                    txPort = txPort,
                    onTxPort = { txPort = it },
                    txText = txText,
                    onTxText = { txText = it },
                    onPickFile = { filePicker.launch(arrayOf("*/*")) },
                )
            }

            Spacer(Modifier.height(10.dp))
            HonestyPanel()
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ─── WOL ────────────────────────────────────────────────────────────────────

/** WOL 标签页：MAC、端口 9 / 7、广播地址与发送结果 */
@Composable
private fun WolTab(vm: LanToolsViewModel, state: WolState) {
    var mac by remember { mutableStateOf("") }
    var alsoPort7 by remember { mutableStateOf(false) }

    TermPanel(stringResource(R.string.lantools_wol_panel_title)) {
        TermInput(
            stringResource(R.string.lantools_target_mac_label),
            mac,
            placeholder = stringResource(R.string.lantools_mac_placeholder),
        ) { mac = it }
        Spacer(Modifier.height(6.dp))

        PanelStat(
            stringResource(R.string.lantools_local_ip_label),
            state.localIpv4 ?: "--",
            if (state.localIpv4 != null) Gold else Gray,
        )
        PanelStat(
            stringResource(R.string.lantools_broadcast_label),
            state.broadcastAddress ?: "--",
            if (state.broadcastAddress != null) Gold else Gray,
        )
        PanelStat(
            stringResource(R.string.lantools_magic_packet_label),
            stringResource(R.string.lantools_magic_packet_value),
            White,
        )

        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectChip(!alsoPort7, stringResource(R.string.lantools_port_9)) { alsoPort7 = false }
            SelectChip(alsoPort7, stringResource(R.string.lantools_port_9_7)) { alsoPort7 = true }
        }
        Spacer(Modifier.height(9.dp))

        ActionButton(
            running = state.sending,
            runLabel = stringResource(R.string.lantools_send_magic),
            stopLabel = stringResource(R.string.lantools_sending_ellipsis),
        ) {
            if (!state.sending) vm.wake(mac, alsoPort7)
        }

        Spacer(Modifier.height(7.dp))
        Text(
            state.lastResult
                ?: stringResource(R.string.lantools_wol_idle_hint),
            fontFamily = Sans,
            fontSize = 9.sp,
            color = when {
                state.lastResult == null -> Gray
                state.lastResult?.startsWith("[!]") == true -> Warn
                state.sent == true -> TacGreen
                else -> Danger
            },
            lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.lantools_wol_explain),
            fontFamily = Sans,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 12.sp,
        )
    }
}

// ─── fling ──────────────────────────────────────────────────────────────────

/** fling 标签页：模式选择、接收端 / 发送端、进度与日志 */
@Composable
private fun FlingTab(
    vm: LanToolsViewModel,
    state: FlingState,
    mode: FlingMode,
    onMode: (FlingMode) -> Unit,
    rxPort: String,
    onRxPort: (String) -> Unit,
    saveToFile: Boolean,
    onSaveToFile: (Boolean) -> Unit,
    txHost: String,
    onTxHost: (String) -> Unit,
    txPort: String,
    onTxPort: (String) -> Unit,
    txText: String,
    onTxText: (String) -> Unit,
    onPickFile: () -> Unit,
) {
    val busy = state.running
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    TermPanel(stringResource(R.string.lantools_fling_mode_title)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectChip(mode == FlingMode.RX, stringResource(R.string.lantools_mode_rx)) { if (!busy) onMode(FlingMode.RX) }
            SelectChip(mode == FlingMode.TX, stringResource(R.string.lantools_mode_tx)) { if (!busy) onMode(FlingMode.TX) }
        }
        if (busy) {
            Text(
                stringResource(R.string.lantools_busy_mode_warning),
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Warn,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        PanelStat(
            stringResource(R.string.lantools_run_state_label),
            when {
                state.mode == FlingMode.RX && state.running -> stringResource(R.string.lantools_state_listening)
                state.mode == FlingMode.TX && state.running -> stringResource(R.string.lantools_state_sending)
                else -> stringResource(R.string.lantools_state_idle)
            },
            if (state.running) TacGreen else Gray,
        )
        PanelStat(
            stringResource(R.string.lantools_session_bytes_label),
            if (state.running || state.bytes > 0L) stringResource(R.string.lantools_bytes_value, state.bytes) else "--",
            Gold,
        )
    }

    // 运行期错误（连接失败 / 端口占用 / 读取失败）
    state.error?.let { message ->
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = Danger.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
        ) {
            Text(
                stringResource(R.string.lantools_error_line, message),
                fontFamily = Sans,
                fontSize = 9.sp,
                color = Danger,
                lineHeight = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }

    if (mode == FlingMode.RX) {
        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.lantools_rx_panel_title)) {
            TermInput(stringResource(R.string.lantools_listen_port_label), rxPort, "0") { value ->
                onRxPort(value.filter { it.isDigit() }.take(5))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SelectChip(saveToFile, stringResource(R.string.lantools_save_to_file)) { if (!busy) onSaveToFile(!saveToFile) }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.lantools_private_dir_hint),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                )
            }
            Spacer(Modifier.height(8.dp))

            ActionButton(
                running = state.mode == FlingMode.RX && state.running,
                runLabel = stringResource(R.string.lantools_start_receive),
                stopLabel = stringResource(R.string.lantools_stop_receive),
            ) {
                if (state.mode == FlingMode.RX && state.running) {
                    vm.stopReceive()
                } else if (!state.running) {
                    vm.startReceive(rxPort.toIntOrNull() ?: -1, saveToFile)
                }
            }

            Spacer(Modifier.height(7.dp))
            PanelStat(
                stringResource(R.string.lantools_actual_port_label),
                state.localPort?.toString() ?: "--",
                if (state.localPort != null) TacGreen else Gray,
            )
            PanelStat(
                stringResource(R.string.lantools_port_source_label),
                when {
                    state.localPort == null -> "--"
                    state.ephemeralPort != null -> stringResource(R.string.lantools_port_source_ephemeral)
                    else -> stringResource(R.string.lantools_port_source_manual)
                },
                White,
            )
            PanelStat(stringResource(R.string.lantools_peer_label), state.peer ?: "--", White)
            PanelStat(
                stringResource(R.string.lantools_received_label),
                if (state.localPort != null || state.bytes > 0L) stringResource(R.string.lantools_bytes_value, state.bytes) else "--",
                Gold,
            )
            state.savedFile?.let { path ->
                Text(
                    stringResource(R.string.lantools_saved_path, path),
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = TacGreen,
                    lineHeight = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.lantools_rx_preview_title),
                fontFamily = Sans,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Red,
            )
            Spacer(Modifier.height(4.dp))

            if (state.previewText.isEmpty()) {
                EmptyHint(
                    when {
                        state.running && state.mode == FlingMode.RX ->
                            stringResource(R.string.lantools_rx_waiting)
                        else ->
                            stringResource(R.string.lantools_rx_idle)
                    },
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, BorderDim, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                        .padding(8.dp),
                ) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text(
                            state.previewText,
                            fontFamily = Mono,
                            fontSize = 9.sp,
                            color = White,
                            lineHeight = 12.sp,
                        )
                    }
                }
                if (state.previewTruncated) {
                    Text(
                        if (state.savedFile != null) {
                            stringResource(R.string.lantools_preview_truncated_saved)
                        } else {
                            stringResource(R.string.lantools_preview_truncated_dropped)
                        },
                        fontFamily = Sans,
                        fontSize = 8.sp,
                        color = Gray,
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    if (mode == FlingMode.TX) {
        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.lantools_tx_panel_title)) {
            TermInput(stringResource(R.string.lantools_rx_host_label), txHost, stringResource(R.string.lantools_rx_host_placeholder)) { onTxHost(it) }
            Spacer(Modifier.height(6.dp))
            TermInput(stringResource(R.string.lantools_rx_port_label), txPort, "9000") { value ->
                onTxPort(value.filter { it.isDigit() }.take(5))
            }
            Spacer(Modifier.height(6.dp))
            TermInput(stringResource(R.string.lantools_text_label), txText, stringResource(R.string.lantools_text_placeholder)) { onTxText(it) }
            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GhostButton(if (state.mode == FlingMode.TX && state.running) stringResource(R.string.lantools_sending_ellipsis) else stringResource(R.string.lantools_choose_file_send)) {
                    if (!state.running) onPickFile()
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.lantools_bytes_value, txText.toByteArray(Charsets.UTF_8).size),
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = Gray,
                )
            }

            Spacer(Modifier.height(8.dp))
            ActionButton(
                running = state.mode == FlingMode.TX && state.running,
                runLabel = stringResource(R.string.lantools_send_text),
                stopLabel = stringResource(R.string.lantools_sending_ellipsis),
            ) {
                if (!state.running) {
                    vm.sendText(txHost, txPort.toIntOrNull() ?: -1, txText)
                }
            }

            // 进度条：total 未知（文件大小读不到）时只显示已发送字节
            val total = state.totalBytes
            val fraction = if (total > 0L) {
                (state.bytes.toFloat() / total).coerceIn(0f, 1f)
            } else {
                0f
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).height(6.dp).background(SurfaceAlt)) {
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(if (state.error != null) Danger else TacGreen),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        total > 0L -> stringResource(R.string.lantools_progress_fraction, state.bytes, total)
                        state.bytes > 0L -> stringResource(R.string.lantools_bytes_value, state.bytes)
                        else -> "--"
                    },
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = Gray,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(R.string.lantools_tx_explain),
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Gray,
                lineHeight = 12.sp,
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    TermPanel(stringResource(R.string.lantools_log_title)) {
        if (state.log.isEmpty()) {
            EmptyHint(stringResource(R.string.lantools_log_empty))
        } else {
            state.log.takeLast(80).forEach { entry ->
                LogLine(
                    timeFormat.format(Date(entry.timeMs)),
                    entry.text,
                    when (entry.level) {
                        FlingLogLevel.OK -> TacGreen
                        FlingLogLevel.ERROR -> Danger
                        FlingLogLevel.INFO -> White
                    },
                )
            }
        }
    }
}

// ─── 协议边界说明 ────────────────────────────────────────────────────────────

/** 页面底部的诚实说明：协议语义、网络限制与数据来源 */
@Composable
private fun HonestyPanel() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, BorderDim),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Star(Warn, 9.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.lantools_honesty_title),
                    fontFamily = Sans,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Warn,
                )
            }
            Spacer(Modifier.height(6.dp))
            listOf(
                R.string.lantools_honesty_line_1,
                R.string.lantools_honesty_line_2,
                R.string.lantools_honesty_line_3,
                R.string.lantools_honesty_line_4,
                R.string.lantools_honesty_line_5,
                R.string.lantools_honesty_line_6,
            ).forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp)) {
                    Text("·", fontFamily = Mono, fontSize = 8.sp, color = Gray)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        stringResource(line),
                        fontFamily = Sans,
                        fontSize = 8.sp,
                        color = Gray,
                        lineHeight = 12.sp,
                    )
                }
            }
        }
    }
}
