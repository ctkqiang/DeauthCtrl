package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import xin.ctkqiang.deauthctrl.manager.BleMeshManager
import xin.ctkqiang.deauthctrl.manager.BleMeshMessage
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.StatChip
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.BleMeshViewModel

/** 输入框允许停留的最大字符数 —— 超过 18 字保留原文，方便提示将被截断 */
private const val MESH_INPUT_LIMIT = 64

/**
 * BLE 网格中继聊天 —— 无 WiFi、无基站的逐跳消息网络
 *
 * 一机同时广播自己的消息并扫描别人的广播：
 * - 广播包里的厂商数据格式为 `MESH|sender|seq|ttl|msg`；
 * - 收到 ttl > 0 的消息会减 1 后中继一次，最多 2 跳；
 * - 节点「在线」的定义是 8 秒内听到过它的广播，RSSI 是系统上报的原始值。
 *
 * 页面进入即启动，离开时通过 DisposableEffect 停止广播与扫描。
 */
@Composable
fun BleMeshScreen(back: () -> Unit) {
    val vm: BleMeshViewModel = viewModel()
    val state by vm.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // 进入页面立即启动广播 + 扫描；离开页面停止并收回广播会话
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }

    // 节点年龄每秒刷新一次，只驱动 UI，不产生额外传感器调用
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowTick = System.currentTimeMillis()
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Dark)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        TerminalHeader("网格聊天 · BLE MESH", back)

        // ── 状态条：在线节点 / 已收消息 / 中继次数 ──────────────────────────
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = SurfaceBg,
            border = BorderStroke(1.dp, BorderDim),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatChip(
                        "在线节点",
                        state.peers.size.toString(),
                        active = state.running && state.peers.isNotEmpty(),
                    )
                    StatChip("已收消息", state.receivedCount.toString(), active = state.running)
                    StatChip("中继次数", state.relayedCount.toString(), active = state.running)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "本机 ID",
                        fontFamily = Sans, fontSize = 9.sp, color = Gray,
                        modifier = Modifier.width(44.dp),
                    )
                    Text(
                        state.myId,
                        fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = Gold, modifier = Modifier.weight(1f),
                    )
                    Text(
                        "载荷预算 ${state.payloadBudget}B",
                        fontFamily = Mono, fontSize = 8.sp, color = Gray,
                    )
                }
            }
        }

        // ── 错误与发送提示 ─────────────────────────────────────────────────
        state.error?.let { message ->
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = Danger.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
            ) {
                Text(
                    "[!] $message",
                    fontFamily = Sans, fontSize = 9.sp, color = Danger, lineHeight = 13.sp,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
        state.notice?.let { note ->
            Text(
                "[!] $note",
                fontFamily = Sans, fontSize = 9.sp, color = Warn, lineHeight = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp),
            )
        }

        // ── 在线节点 ──────────────────────────────────────────────────────
        TermPanel(
            "在线节点 · PEERS",
            Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
        ) {
            if (state.peers.isEmpty()) {
                Text(
                    if (state.running) {
                        "监听中 —— 附近运行本模块的设备会在 8 秒窗口内出现在这里"
                    } else {
                        "网格未运行 —— 启动后最近 8 秒听到的节点会显示在这里"
                    },
                    fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.peers.forEach { peer ->
                        val ageSec = ((nowTick - peer.lastSeenMs).coerceAtLeast(0L)) / 1000L
                        Surface(
                            shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                            color = SurfaceAlt,
                            border = BorderStroke(1.dp, BorderDim),
                        ) {
                            Column(Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
                                Text(
                                    peer.id,
                                    fontFamily = Mono, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold, color = Gold,
                                )
                                Text(
                                    "${peer.rssi} dBm · ${ageSec}s · " +
                                        (if (peer.hops == 0) "直连" else "${peer.hops} 跳"),
                                    fontFamily = Mono, fontSize = 8.sp, color = Gray,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 消息流（最新在上）─────────────────────────────────────────────
        TermPanel(
            "消息流 · MESH LOG",
            Modifier.weight(1f).padding(horizontal = 12.dp),
        ) {
            if (state.messages.isEmpty()) {
                Text(
                    if (state.running) {
                        "等待消息 —— 附近运行本模块的设备会互相转发广播；本机发送的消息也会出现在这里"
                    } else {
                        "未运行 —— 启动后收到的消息会按时间倒序显示在这里"
                    },
                    fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.messages) { message ->
                        MeshMessageRow(message, timeFormat)
                    }
                }
            }
        }

        // ── 发射与启停 ────────────────────────────────────────────────────
        TermPanel("发射 · MESH TX", Modifier.padding(12.dp)) {
            TermInput(
                "消息内容",
                input,
                placeholder = "18 字以内 · 明文广播 · 无回执",
            ) { value ->
                input = value.take(MESH_INPUT_LIMIT)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${input.length}/${BleMeshManager.MAX_MESSAGE_CHARS} 字",
                    fontFamily = Mono, fontSize = 9.sp,
                    color = when {
                        input.length > BleMeshManager.MAX_MESSAGE_CHARS -> Warn
                        input.isEmpty() -> Gray
                        else -> Gold
                    },
                )
                Spacer(Modifier.weight(1f))
                GhostButton("发送") {
                    val body = input.trim()
                    if (body.isNotEmpty()) {
                        // 未运行时自动启动；启动失败则不吞掉输入
                        val ready = state.running || vm.start()
                        if (ready && vm.send(body)) input = ""
                    }
                }
            }
            if (input.length > BleMeshManager.MAX_MESSAGE_CHARS) {
                Text(
                    "[!] 超出 ${BleMeshManager.MAX_MESSAGE_CHARS} 字上限，发送时将截断",
                    fontFamily = Sans, fontSize = 8.sp, color = Warn,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            ActionButton(
                running = state.running,
                runLabel = "启动网格",
                stopLabel = "停止网格",
            ) {
                if (state.running) vm.stop() else vm.start()
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "广播包共 31B：协议头 MESH|ID|seq|ttl| 占 16-19B，文本实际只剩约 5-8B" +
                    "（ASCII 约 5-8 字，中文 1-3 字）；输入上限 18 字，发送时按字节截断。",
                fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp,
            )
            Text(
                "典型距离 10-50m（开阔地、无遮挡）；TTL 初始 2，消息最多再经 2 跳，" +
                    "每台设备对同一条消息只中继一次。",
                fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp,
            )
            Text(
                "全程明文广播、无加密、无鉴权、无送达回执：范围内任何 BLE 扫描器都能读到内容。",
                fontFamily = Sans, fontSize = 8.sp, color = Danger, lineHeight = 11.sp,
            )
            Text(
                "发送为 3 轮 × 1 秒广播窗口，速率约 3 秒/条；中继消息的 RSSI 是转发节点到本机的信号，" +
                    "不代表原始发送者的距离。",
                fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp,
            )
            Text(
                "节点 8 秒无广播视为离线；本机 ID 由随机字母数字生成并持久化，不来自硬件标识。",
                fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp,
            )
        }
    }
}

/**
 * 单条消息行
 *
 * 第一行：发送者 / 跳数 / 时间 / RSSI；第二行：`[发送者] 内容`。
 * 本机消息用「ME」标记、RSSI 显示 "--"（本机发送没有可测量值）。
 */
@Composable
private fun MeshMessageRow(message: BleMeshMessage, timeFormat: SimpleDateFormat) {
    val senderColor = if (message.isMine) Gold else TacGreen
    Column(
        Modifier.fillMaxWidth()
            .background(SurfaceBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (message.isMine) "ME" else message.sender,
                fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = senderColor,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (message.hops == 0) "直连" else "${message.hops} 跳",
                fontFamily = Mono, fontSize = 8.sp, color = Gray,
            )
            Spacer(Modifier.weight(1f))
            Text(
                timeFormat.format(Date(message.timeMs)),
                fontFamily = Mono, fontSize = 8.sp, color = Gray,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                message.rssi?.let { "$it dBm" } ?: "--",
                fontFamily = Mono, fontSize = 8.sp,
                color = if (message.isMine) Gray else Gold,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            if (message.isMine) "[ME] ${message.text}" else "[${message.sender}] ${message.text}",
            fontFamily = Sans, fontSize = 12.sp, color = White, lineHeight = 16.sp,
        )
    }
}
