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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
        TerminalHeader("局域网工具 · LAN TOOLS", back)

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(tab == 0, "WOL 唤醒") { tab = 0 }
                SelectChip(tab == 1, "fling 管道") { tab = 1 }
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

    TermPanel("网络唤醒 · WAKE-ON-LAN") {
        TermInput(
            "目标 MAC（留空使用广播 MAC）",
            mac,
            placeholder = "aa:bb:cc:dd:ee:ff",
        ) { mac = it }
        Spacer(Modifier.height(6.dp))

        PanelStat(
            "本机局域网 IP",
            state.localIpv4 ?: "--",
            if (state.localIpv4 != null) Gold else Gray,
        )
        PanelStat(
            "定向广播地址",
            state.broadcastAddress ?: "--",
            if (state.broadcastAddress != null) Gold else Gray,
        )
        PanelStat("魔术包", "102 B · 0xFF × 6 + MAC × 16", White)

        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectChip(!alsoPort7, "端口 9") { alsoPort7 = false }
            SelectChip(alsoPort7, "端口 9 + 7") { alsoPort7 = true }
        }
        Spacer(Modifier.height(9.dp))

        ActionButton(
            running = state.sending,
            runLabel = "发送魔术包",
            stopLabel = "发送中…",
        ) {
            if (!state.sending) vm.wake(mac, alsoPort7)
        }

        Spacer(Modifier.height(7.dp))
        Text(
            state.lastResult
                ?: "尚未发送。MAC 留空时使用 ff:ff:ff:ff:ff:ff，唤醒网段内所有支持 WOL 的设备。",
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
            "发送时同时面向 255.255.255.255 与当前 Wi-Fi 网段的定向广播；" +
                "选择「端口 9 + 7」会再向两个地址的端口 7 各发一份。" +
                "UDP 无回执，发送成功只代表数据包已交给内核，不代表目标已唤醒。",
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

    TermPanel("工作模式 · FLING MODE") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectChip(mode == FlingMode.RX, "接收") { if (!busy) onMode(FlingMode.RX) }
            SelectChip(mode == FlingMode.TX, "发送") { if (!busy) onMode(FlingMode.TX) }
        }
        if (busy) {
            Text(
                "[!] 当前有传输任务运行，停止后才能切换模式。",
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Warn,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        PanelStat(
            "运行状态",
            when {
                state.mode == FlingMode.RX && state.running -> "监听中"
                state.mode == FlingMode.TX && state.running -> "发送中"
                else -> "空闲"
            },
            if (state.running) TacGreen else Gray,
        )
        PanelStat(
            "本次字节",
            if (state.running || state.bytes > 0L) "${state.bytes} B" else "--",
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
                "[!] $message",
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
        TermPanel("接收端 · LISTEN") {
            TermInput("监听端口（0 = 内核临时端口）", rxPort, "0") { value ->
                onRxPort(value.filter { it.isDigit() }.take(5))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SelectChip(saveToFile, "写入文件") { if (!busy) onSaveToFile(!saveToFile) }
                Spacer(Modifier.width(8.dp))
                Text(
                    "保存到应用私有目录 fling/",
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                )
            }
            Spacer(Modifier.height(8.dp))

            ActionButton(
                running = state.mode == FlingMode.RX && state.running,
                runLabel = "启动接收",
                stopLabel = "停止接收",
            ) {
                if (state.mode == FlingMode.RX && state.running) {
                    vm.stopReceive()
                } else if (!state.running) {
                    vm.startReceive(rxPort.toIntOrNull() ?: -1, saveToFile)
                }
            }

            Spacer(Modifier.height(7.dp))
            PanelStat(
                "实际监听端口",
                state.localPort?.toString() ?: "--",
                if (state.localPort != null) TacGreen else Gray,
            )
            PanelStat(
                "端口来源",
                when {
                    state.localPort == null -> "--"
                    state.ephemeralPort != null -> "内核临时分配"
                    else -> "手动指定"
                },
                White,
            )
            PanelStat("对端地址", state.peer ?: "--", White)
            PanelStat(
                "已接收",
                if (state.localPort != null || state.bytes > 0L) "${state.bytes} B" else "--",
                Gold,
            )
            state.savedFile?.let { path ->
                Text(
                    "落盘：$path",
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = TacGreen,
                    lineHeight = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "接收字节（前 4 KB）",
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
                            "监听中 —— 等待发送端连接；收到数据后在此显示前 4 KB。"
                        else ->
                            "尚未接收数据 —— 启动接收后把「实际监听端口」告诉发送方。"
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
                            "仅显示前 4 KB；完整字节已写入上面的落盘文件。"
                        } else {
                            "仅显示前 4 KB；未开启「写入文件」，其余字节已丢弃。"
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
        TermPanel("发送端 · SEND") {
            TermInput("接收端 IP", txHost, "例如 192.168.1.100") { onTxHost(it) }
            Spacer(Modifier.height(6.dp))
            TermInput("接收端端口", txPort, "9000") { value ->
                onTxPort(value.filter { it.isDigit() }.take(5))
            }
            Spacer(Modifier.height(6.dp))
            TermInput("文本内容", txText, "按 UTF-8 原样写入") { onTxText(it) }
            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GhostButton(if (state.mode == FlingMode.TX && state.running) "发送中…" else "选择文件发送") {
                    if (!state.running) onPickFile()
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${txText.toByteArray(Charsets.UTF_8).size} B",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = Gray,
                )
            }

            Spacer(Modifier.height(8.dp))
            ActionButton(
                running = state.mode == FlingMode.TX && state.running,
                runLabel = "发送文本",
                stopLabel = "发送中…",
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
                        total > 0L -> "${state.bytes} / $total B"
                        state.bytes > 0L -> "${state.bytes} B"
                        else -> "--"
                    },
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = Gray,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "发送端连接接收端 IP 与端口，把文本或文件字节原样写入后关闭连接；" +
                    "没有长度前缀、没有校验、没有加密，EOF 即传输结束。",
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Gray,
                lineHeight = 12.sp,
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    TermPanel("fling 日志 · LOG") {
        if (state.log.isEmpty()) {
            EmptyHint("连接、传输与错误日志会按时间显示在这里。")
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
                    "协议边界 · 诚实说明",
                    fontFamily = Sans,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Warn,
                )
            }
            Spacer(Modifier.height(6.dp))
            listOf(
                "fling 兼容语义（fling 1.1-3）：裸 TCP 字节流，无长度前缀、无校验、无加密；连接关闭（EOF）即传输结束，一次一连接。",
                "接收端端口填 0 时由内核分配临时端口，必须把「实际监听端口」告知发送方；离开本页会停止接收。",
                "AP 客户端隔离（AP isolation）会阻断同一 Wi-Fi 内的设备互连；跨网段、热点客户端之间通常也不可达。",
                "发送端填接收端的局域网 IP 与端口；公网 / NAT 后的地址无法直接建立连接。",
                "WOL 魔术包为 102 字节 UDP 广播，无回执；目标设备需在 BIOS / 固件中开启网络唤醒并保持待机供电。",
                "本页不伪造数据：读不到的地址、端口与字节显示 \"--\"，日志只记录真实发生的连接与传输。",
            ).forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp)) {
                    Text("·", fontFamily = Mono, fontSize = 8.sp, color = Gray)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        line,
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
