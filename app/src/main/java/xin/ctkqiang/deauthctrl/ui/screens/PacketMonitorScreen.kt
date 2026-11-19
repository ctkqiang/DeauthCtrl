package xin.ctkqiang.deauthctrl.ui.screens

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.NamedCount
import xin.ctkqiang.deauthctrl.manager.PacketEntry
import xin.ctkqiang.deauthctrl.manager.PacketMonitorState
import xin.ctkqiang.deauthctrl.manager.SessionSummary
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.LogHeader
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.PanelStat
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.StatChip
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.PacketMonitorViewModel
import xin.ctkqiang.deauthctrl.viewmodel.VpnPrepareResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本机流量监视页 —— PACKET MONITOR
 *
 * ## 数据来源(全部真实)
 * 页面只观察 [xin.ctkqiang.deauthctrl.manager.PacketCaptureService](VpnService)的全局状态:
 * 所有条目来自 TUN 实际读到的 IPv4 报文,由纯 Kotlin 解析器拆出 TCP/UDP/ICMP、
 * DNS 查询、TLS SNI 与 HTTP 首行;统计值就是报文字段的和,没有样本时显示「--」。
 *
 * ## 报告结构(Wireshark / pcap 风格)
 * - 顶部汇总:总包 / 总字节 / 会话数 / 协议计数条 / Top 域名 / Top 对端 IP;
 * - 会话面板:按 (srcIp, dstIp) 双向聚合,含包数、字节、协议组成、最近活动、域名;
 * - 过滤:协议 chips + 文本子串 + 「隐藏背景噪声」开关(mDNS/SSDP/DHCP/IGMP/NTP);
 * - 包列表:时间 / 协议 / 源→目的 / 长度 / flags / 语义摘要,点击展开详情树与 HEX。
 *
 * ## 启动流程
 * 点击「启动监视」→ VpnService.prepare 查询授权 → 未授权则弹系统同意框,
 * 同意后 startForegroundService;已授权直接启动。停止按钮与通知「停止」都能停;
 * 停止会立即关闭 TUN fd 并唤醒读线程,系统 VPN 图标随即消失。
 *
 * ## 诚实边界
 * 只能看到本机流量;HTTPS 内容加密只能看到 SNI;同网段其他设备需要 root +
 * ARP 欺骗;ICMP 不转发;IPv6 不经过本隧道。详见页尾「能力边界」。
 */
@Composable
fun PacketMonitorScreen(back: () -> Unit) {
    val vm: PacketMonitorViewModel = viewModel()
    val state by vm.state.collectAsState()
    val uiError by vm.uiError.collectAsState()
    val context = LocalContext.current
    var prepareDenied by remember { mutableStateOf(false) }
    var protoFilter by remember { mutableStateOf(ProtoFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var hideNoise by remember { mutableStateOf(false) }
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // 系统 VPN 授权结果:同意才启动服务;拒绝则留在本页给出明确提示
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            prepareDenied = false
            vm.clearUiError()
            vm.startService(context)
        } else {
            prepareDenied = true
        }
    }

    val visiblePackets = remember(state.packets, protoFilter, query, hideNoise) {
        state.packets.filter { entry ->
            matchesFilter(entry, protoFilter) &&
                matchesQuery(entry, query) &&
                !(hideNoise && isBackgroundNoise(entry))
        }
    }
    val noiseCount = remember(state.packets) {
        state.packets.count { isBackgroundNoise(it) }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.pkt_title), back)

        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            item { StatusPanel(state) }
            item { Spacer(Modifier.height(8.dp)) }

            item {
                ActionButton(
                    running = state.running,
                    runLabel = stringResource(R.string.pkt_btn_start),
                    stopLabel = stringResource(R.string.pkt_btn_stop),
                ) {
                    if (state.running) {
                        vm.stopService(context)
                    } else {
                        when (val prep = vm.prepare(context)) {
                            VpnPrepareResult.Ready -> {
                                prepareDenied = false
                                vm.startService(context)
                            }
                            is VpnPrepareResult.ConsentNeeded -> vpnLauncher.launch(prep.intent)
                            is VpnPrepareResult.Failed -> Unit // 详情由 vm.uiError 显示
                        }
                    }
                }
            }

            val warnings = buildWarningMessages(context, state, uiError, prepareDenied)
            if (warnings.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    WarningStrip(warnings)
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { SummaryPanel(state) }
            item { Spacer(Modifier.height(8.dp)) }
            item { SessionsPanel(state, clock) }
            item { Spacer(Modifier.height(8.dp)) }

            item {
                FiltersPanel(
                    protoFilter = protoFilter,
                    onProto = { protoFilter = it },
                    query = query,
                    onQuery = { query = it },
                    hideNoise = hideNoise,
                    onToggleNoise = { hideNoise = !hideNoise },
                    hiddenNoiseCount = noiseCount,
                    shownCount = visiblePackets.size,
                    totalCount = state.packets.size,
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
            item {
                LogHeader(stringResource(R.string.pkt_log_title), showClear = true) { vm.clearPackets() }
            }
            item { Spacer(Modifier.height(2.dp)) }

            when {
                state.packets.isEmpty() -> item {
                    EmptyHint(
                        if (state.running) {
                            stringResource(R.string.pkt_empty_listening)
                        } else {
                            stringResource(R.string.pkt_empty_not_started)
                        },
                    )
                }
                visiblePackets.isEmpty() -> item {
                    EmptyHint(stringResource(R.string.pkt_empty_no_match))
                }
                else -> {
                    item {
                        Text(
                            stringResource(R.string.pkt_list_count_notice, visiblePackets.size, state.packets.size),
                            fontFamily = Sans,
                            fontSize = 8.sp,
                            color = Gray,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    items(visiblePackets, key = { it.id }) { entry ->
                        PacketRow(entry, clock)
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { HonestyPanel() }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 过滤模型
// ══════════════════════════════════════════════════════════════════════════

/** 协议过滤 chips —— 与页面约定一致:[全部][DNS][TLS][HTTP][TCP][UDP] */
private enum class ProtoFilter(val labelRes: Int?) {
    ALL(R.string.pkt_filter_all),
    DNS(null),
    TLS(null),
    HTTP(null),
    TCP(null),
    UDP(null),
}

private fun matchesFilter(entry: PacketEntry, filter: ProtoFilter): Boolean {
    val p = entry.parsed
    return when (filter) {
        ProtoFilter.ALL -> true
        ProtoFilter.DNS ->
            p.dnsName != null || p.srcPort == 53 || p.dstPort == 53 || p.info.startsWith("DNS")
        ProtoFilter.TLS -> p.info.startsWith("TLS")
        ProtoFilter.HTTP -> p.info.startsWith("HTTP")
        ProtoFilter.TCP -> p.protocol == "TCP"
        ProtoFilter.UDP -> p.protocol == "UDP"
    }
}

/** 文本过滤:域名 / IP 子串,大小写不敏感 */
private fun matchesQuery(entry: PacketEntry, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    val p = entry.parsed
    return p.srcIp.contains(q) ||
        p.dstIp.contains(q) ||
        p.info.lowercase().contains(q) ||
        p.domain?.lowercase()?.contains(q) == true ||
        p.dnsName?.lowercase()?.contains(q) == true
}

/**
 * 背景噪声判定 —— 降噪开关只隐藏这些固定的局域网/系统广播:
 * mDNS(224.0.0.251:5353)、SSDP(239.255.255.250:1900)、DHCP(67-68)、
 * IGMP(协议号 2)、NTP(123)。真实业务流量不会命中。
 */
private fun isBackgroundNoise(entry: PacketEntry): Boolean {
    val p = entry.parsed
    val mdnsHost = p.srcIp == "224.0.0.251" || p.dstIp == "224.0.0.251"
    if (mdnsHost && (p.srcPort == 5353 || p.dstPort == 5353)) return true
    if (p.dstIp == "239.255.255.250" && (p.srcPort == 1900 || p.dstPort == 1900)) return true
    if (p.protocol == "UDP" && (p.srcPort in 67..68 || p.dstPort in 67..68)) return true
    if (p.protocolNumber == 2) return true
    if (p.protocol == "UDP" && (p.srcPort == 123 || p.dstPort == 123)) return true
    return false
}

// ══════════════════════════════════════════════════════════════════════════
// 状态与汇总
// ══════════════════════════════════════════════════════════════════════════

/** 运行状态面板 —— 服务态 / TUN 地址 / 转发模式 */
@Composable
private fun StatusPanel(state: PacketMonitorState) {
    TermPanel(stringResource(R.string.pkt_panel_status)) {
        PanelStat(
            stringResource(R.string.pkt_label_vpn_service),
            if (state.running) stringResource(R.string.pkt_state_running) else stringResource(R.string.pkt_state_stopped),
            if (state.running) TacGreen else Gray,
        )
        PanelStat(stringResource(R.string.pkt_label_local_tun), "10.8.0.1/32 · MTU 1500", Gold)
        PanelStat(stringResource(R.string.pkt_label_route), stringResource(R.string.pkt_value_route), White)
        PanelStat(
            stringResource(R.string.pkt_label_forwarding),
            if (state.running) stringResource(R.string.pkt_value_forwarding) else "--",
            if (state.running) TacGreen else Gray,
        )
    }
}

/**
 * 汇总面板 —— 全会话统计
 *
 * 总包 / 总字节 / 会话数 / 唯一域名一行,下面是协议计数条、Top 域名、
 * Top 对端 IP。所有数字来自 [PacketMonitorState] 的服务端聚合。
 */
@Composable
private fun SummaryPanel(state: PacketMonitorState) {
    TermPanel(stringResource(R.string.pkt_panel_summary)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatChip(stringResource(R.string.pkt_stat_packets), "${state.totalPackets}")
            StatChip(stringResource(R.string.pkt_stat_bytes), formatBytes(state.totalBytes))
            StatChip(stringResource(R.string.pkt_stat_sessions), "${state.sessionCount}")
            StatChip(stringResource(R.string.pkt_stat_unique_domains), "${state.domains.size}")
        }
        Spacer(Modifier.height(9.dp))

        Text(
            stringResource(R.string.pkt_section_protocol),
            fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Red,
        )
        Spacer(Modifier.height(3.dp))
        val maxCount = (state.protocolCounts.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val names = mutableListOf("TCP", "UDP", "ICMP")
        state.protocolCounts.keys.filter { it !in names }.sorted().forEach { names.add(it) }
        names.forEach { name ->
            val count = state.protocolCounts[name] ?: 0L
            if (count > 0L || name == "TCP" || name == "UDP" || name == "ICMP") {
                ProtocolCountBar(name, count, maxCount)
            }
        }

        Spacer(Modifier.height(9.dp))
        Text(
            stringResource(R.string.pkt_section_top_domains),
            fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Red,
        )
        Spacer(Modifier.height(3.dp))
        if (state.domainCounts.isEmpty()) {
            Text(
                stringResource(R.string.pkt_empty_no_domains),
                fontFamily = Sans, fontSize = 8.sp, color = Gray,
            )
        } else {
            state.domainCounts.forEach { item -> CountRow(item, stringResource(R.string.pkt_unit_packets)) }
        }

        Spacer(Modifier.height(9.dp))
        Text(
            stringResource(R.string.pkt_section_top_peers),
            fontFamily = Sans, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Red,
        )
        Spacer(Modifier.height(3.dp))
        if (state.peerCounts.isEmpty()) {
            Text(stringResource(R.string.pkt_empty_no_peers), fontFamily = Sans, fontSize = 8.sp, color = Gray)
        } else {
            state.peerCounts.forEach { item -> CountRow(item, stringResource(R.string.pkt_unit_packets)) }
        }
    }
}

/** 单个协议的计数条 —— 名称 / 比例条 / 计数 */
@Composable
private fun ProtocolCountBar(name: String, count: Long, max: Long) {
    val color = when (name) {
        "TCP" -> TacGreen
        "UDP" -> Gold
        "ICMP" -> Warn
        else -> Gray
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            fontFamily = Mono, fontSize = 8.sp, color = color,
            modifier = Modifier.width(42.dp),
        )
        Box(
            Modifier.weight(1f).height(5.dp)
                .background(BorderDim.copy(alpha = 0.35f)),
        ) {
            val fraction = (count.toFloat() / max.toFloat()).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth(fraction).height(5.dp).background(color.copy(alpha = 0.7f)),
            )
        }
        Text(
            " $count",
            fontFamily = Mono, fontSize = 8.sp, color = Gray,
            modifier = Modifier.width(54.dp),
        )
    }
}

/** Top 列表的一行:名称(省略号) + 计数 */
@Composable
private fun CountRow(item: NamedCount, unit: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.name,
            fontFamily = Mono, fontSize = 8.sp, color = White,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.pkt_count_with_unit, item.count, unit),
            fontFamily = Mono, fontSize = 8.sp, color = Gold,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 会话聚合
// ══════════════════════════════════════════════════════════════════════════

/**
 * 会话面板 —— 双向 (srcIp, dstIp) 聚合
 *
 * 每个会话展示:两端地址、包数 / 字节、协议组成、最近活动时间、域名列表。
 * 默认展示最近活动的 8 个,可展开全部(服务端最多发布 100 个)。
 */
@Composable
private fun SessionsPanel(state: PacketMonitorState, clock: SimpleDateFormat) {
    var showAll by remember { mutableStateOf(false) }
    TermPanel(stringResource(R.string.pkt_panel_sessions)) {
        if (state.sessions.isEmpty()) {
            Text(
                stringResource(R.string.pkt_empty_no_sessions),
                fontFamily = Sans, fontSize = 8.sp, color = Gray,
            )
        } else {
            Text(
                stringResource(
                    R.string.pkt_sessions_summary,
                    state.sessionCount,
                    if (showAll) state.sessions.size else minOf(8, state.sessions.size),
                ),
                fontFamily = Sans, fontSize = 8.sp, color = Gray,
            )
            val rows = if (showAll) state.sessions else state.sessions.take(8)
            rows.forEach { session ->
                Spacer(Modifier.height(5.dp))
                SessionRow(session, clock)
            }
            if (state.sessions.size > 8) {
                Spacer(Modifier.height(6.dp))
                SelectChip(
                    selected = showAll,
                    text = if (showAll) stringResource(R.string.pkt_sessions_collapse) else stringResource(R.string.pkt_sessions_expand, state.sessions.size),
                ) { showAll = !showAll }
            }
        }
    }
}

/** 单个会话卡片 */
@Composable
private fun SessionRow(session: SessionSummary, clock: SimpleDateFormat) {
    Column(
        Modifier.fillMaxWidth()
            .background(BorderDim.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${session.endpointA} ↔ ${session.endpointB}",
                fontFamily = Mono, fontSize = 9.sp, color = White,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.pkt_session_packets_bytes, session.packets, formatBytes(session.bytes)),
                fontFamily = Mono, fontSize = 8.sp, color = Gold,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                protocolMix(session),
                fontFamily = Mono, fontSize = 8.sp, color = TacGreen,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.pkt_session_last_active, clock.format(Date(session.lastActiveMs))),
                fontFamily = Mono, fontSize = 8.sp, color = Gray,
            )
        }
        if (session.domains.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.pkt_session_domains, session.domains.joinToString(", ")),
                fontFamily = Mono, fontSize = 8.sp, color = Gray,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 会话内协议组成:"TCP 8 · UDP 4" */
private fun protocolMix(session: SessionSummary): String {
    if (session.protocols.isEmpty()) return "--"
    return session.protocols.entries
        .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
        .joinToString(" · ") { "${it.key} ${it.value}" }
}

// ══════════════════════════════════════════════════════════════════════════
// 过滤面板
// ══════════════════════════════════════════════════════════════════════════

/** 过滤面板 —— 协议 chips / 文本子串 / 背景噪声开关 */
@Composable
private fun FiltersPanel(
    protoFilter: ProtoFilter,
    onProto: (ProtoFilter) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    hideNoise: Boolean,
    onToggleNoise: () -> Unit,
    hiddenNoiseCount: Int,
    shownCount: Int,
    totalCount: Int,
) {
    val hiddenSuffix = if (hideNoise) " · " + stringResource(R.string.pkt_shown_hidden_suffix, hiddenNoiseCount) else ""
    val shownLine = stringResource(R.string.pkt_shown_count, shownCount, totalCount) + hiddenSuffix
    TermPanel(stringResource(R.string.pkt_panel_filter)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProtoFilter.entries.forEach { filter ->
                SelectChip(selected = protoFilter == filter, text = filter.labelRes?.let { stringResource(it) } ?: filter.name) {
                    onProto(filter)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        TermInput(
            label = stringResource(R.string.pkt_input_label),
            value = query,
            placeholder = stringResource(R.string.pkt_input_placeholder),
            onValue = onQuery,
        )
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectChip(
                selected = hideNoise,
                text = if (hideNoise) stringResource(R.string.pkt_noise_on) else stringResource(R.string.pkt_noise_off),
            ) { onToggleNoise() }
            GhostButton(stringResource(R.string.pkt_clear_filters)) {
                onQuery("")
                onProto(ProtoFilter.ALL)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            shownLine,
            fontFamily = Sans, fontSize = 8.sp, color = Gray,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.pkt_noise_legend),
            fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 告警
// ══════════════════════════════════════════════════════════════════════════

private fun buildWarningMessages(
    context: Context,
    state: PacketMonitorState,
    uiError: String?,
    prepareDenied: Boolean,
): List<Pair<Color, String>> {
    val messages = mutableListOf<Pair<Color, String>>()
    val serviceError = state.error?.removePrefix("[!] ")?.trim().orEmpty()
    if (serviceError.isNotEmpty()) messages.add(Danger to serviceError)
    val localError = uiError?.removePrefix("[!] ")?.trim().orEmpty()
    if (localError.isNotEmpty() && localError != serviceError) messages.add(Danger to localError)
    if (prepareDenied) {
        messages.add(
            Warn to context.getString(R.string.pkt_warn_vpn_denied),
        )
    }
    return messages
}

@Composable
private fun WarningStrip(messages: List<Pair<Color, String>>) {
    messages.forEach { (color, text) ->
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = color.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        ) {
            Text(
                "[!] $text",
                fontFamily = Sans,
                fontSize = 10.sp,
                color = color,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 包列表与详情树
// ══════════════════════════════════════════════════════════════════════════

/**
 * 单条数据包 —— 列表行保持简洁,点击展开 Wireshark 风格详情树
 *
 * 第一行:时间 / 协议 / 长度 / TCP flags;第二行:源 → 目的;第三行:语义摘要,
 * 域名用金色高亮。展开后显示 Internet Protocol / TCP|UDP / DNS 分层字段与
 * pcap 风格 HEX 转储(偏移 + 16 字节 hex + ASCII 列)。
 */
@Composable
private fun PacketRow(entry: PacketEntry, clock: SimpleDateFormat) {
    var expanded by remember { mutableStateOf(false) }
    val p = entry.parsed
    val protoColor = when (p.protocol) {
        "TCP" -> TacGreen
        "UDP" -> Gold
        "ICMP" -> Warn
        else -> Gray
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .clickable { expanded = !expanded },
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, BorderDim),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    clock.format(Date(entry.timeMs)),
                    fontFamily = Mono, fontSize = 8.sp, color = Gray,
                    modifier = Modifier.width(52.dp),
                )
                Text(
                    p.protocol,
                    fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    color = protoColor, modifier = Modifier.width(48.dp),
                )
                Text(
                    "${p.ipTotalLen} B",
                    fontFamily = Mono, fontSize = 8.sp, color = Gray,
                    modifier = Modifier.weight(1f),
                )
                if (p.tcpFlags.isNotBlank()) {
                    Text(
                        "[${p.tcpFlags}]",
                        fontFamily = Mono, fontSize = 8.sp, color = Warn,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (p.srcPort > 0 || p.dstPort > 0) {
                    "${p.srcIp}:${p.srcPort} → ${p.dstIp}:${p.dstPort}"
                } else {
                    "${p.srcIp} → ${p.dstIp}"
                },
                fontFamily = Mono, fontSize = 9.sp, color = White,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                infoText(p.info, p.domain),
                fontFamily = Mono, fontSize = 9.sp, color = Gray,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                PacketDetail(entry)
            }
        }
    }
}

/**
 * Wireshark 风格详情树
 *
 * 只展示解析器真实拿到的字段;DNS 节点仅在解析到查询名时出现,
 * HEX 节点明确标注「前 N B / 共 M B」,不冒充完整载荷。
 */
@Composable
private fun PacketDetail(entry: PacketEntry) {
    val p = entry.parsed

    TreeSection("Internet Protocol") {
        TreeLine(stringResource(R.string.pkt_field_version), "${p.version}")
        TreeLine("IHL", "${p.ihl / 4} · ${p.ihl} B")
        TreeLine(stringResource(R.string.pkt_field_total_length), "${p.ipTotalLen} B")
        TreeLine("TTL", "${p.ttl}")
        TreeLine(stringResource(R.string.pkt_field_protocol), "${p.protocol} (${p.protocolNumber})")
        TreeLine(stringResource(R.string.pkt_field_address), "${p.srcIp} → ${p.dstIp}")
    }

    if (p.protocol == "TCP") {
        TreeSection("Transmission Control Protocol") {
            TreeLine(stringResource(R.string.pkt_field_port), "${p.srcPort} → ${p.dstPort}")
            TreeLine("Seq", "${p.tcpSeq}")
            TreeLine("Ack", "${p.tcpAck}")
            TreeLine("Flags", p.tcpFlags.ifBlank { "--" })
            TreeLine("Window", "${p.tcpWindow}")
        }
    } else if (p.protocol == "UDP") {
        TreeSection("User Datagram Protocol") {
            TreeLine(stringResource(R.string.pkt_field_port), "${p.srcPort} → ${p.dstPort}")
            TreeLine("Length", "${p.udpLength}")
        }
    }

    if (p.dnsName != null) {
        TreeSection("Domain Name System") {
            TreeLine(stringResource(R.string.pkt_field_query_name), p.dnsName)
            TreeLine(stringResource(R.string.pkt_field_type), p.dnsType ?: "--")
            TreeLine(stringResource(R.string.pkt_field_class), p.dnsClass ?: "--")
        }
    }

    TreeSection(stringResource(R.string.pkt_section_info)) {
        Text(p.info, fontFamily = Mono, fontSize = 8.sp, color = White, lineHeight = 11.sp)
    }

    TreeSection(stringResource(R.string.pkt_section_hex_dump, p.hexBytes.size, p.ipTotalLen)) {
        Text(
            hexDump(p.hexBytes),
            fontFamily = Mono, fontSize = 8.sp, color = Gold, lineHeight = 11.sp,
        )
        TreeLine(stringResource(R.string.pkt_field_payload_offset), if (p.payloadOffset >= 0) "${p.payloadOffset} B" else "--")
    }
}

/** 详情树节点 —— `▸ 标题` + 缩进内容 */
@Composable
private fun TreeSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            "▸ $title",
            fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TacGreen,
        )
        Column(Modifier.padding(start = 12.dp, top = 1.dp)) { content() }
    }
}

/** 详情树一行 —— 标签固定宽度,值等宽字体 */
@Composable
private fun TreeLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 0.5.dp)) {
        Text(
            label,
            fontFamily = Mono, fontSize = 8.sp, color = Gray,
            modifier = Modifier.width(64.dp),
        )
        Text(value, fontFamily = Mono, fontSize = 8.sp, color = White)
    }
}

/** 摘要里的域名用金色高亮;没有域名时原样输出 */
private fun infoText(info: String, domain: String?): AnnotatedString = buildAnnotatedString {
    val highlight = domain?.takeIf { it.isNotBlank() && info.contains(it) }
    if (highlight == null) {
        append(info)
        return@buildAnnotatedString
    }
    val index = info.indexOf(highlight)
    append(info.substring(0, index))
    withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.Bold)) {
        append(highlight)
    }
    append(info.substring(index + highlight.length))
}

private const val HEX_DIGITS = "0123456789ABCDEF"

/**
 * pcap 风格 HEX 转储
 *
 * 每行:4 位十六进制偏移 + 16 字节 hex(不足补空格) + ASCII 列(不可打印为 .)。
 * 输入已经是解析器截断后的前 64 字节,因此这里不会再越界。
 */
private fun hexDump(bytes: ByteArray): String {
    if (bytes.isEmpty()) return "--"
    val sb = StringBuilder(bytes.size * 4)
    var offset = 0
    while (offset < bytes.size) {
        val end = minOf(offset + 16, bytes.size)
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append(offset.toString(16).uppercase().padStart(4, '0')).append("  ")
        for (i in offset until offset + 16) {
            if (i < end) {
                val value = bytes[i].toInt() and 0xFF
                sb.append(HEX_DIGITS[value ushr 4]).append(HEX_DIGITS[value and 0x0F])
            } else {
                sb.append("  ")
            }
            if (i != offset + 15) sb.append(' ')
        }
        sb.append("  |")
        for (i in offset until end) {
            val value = bytes[i].toInt() and 0xFF
            sb.append(if (value in 32..126) value.toChar() else '.')
        }
        sb.append('|')
        offset = end
    }
    return sb.toString()
}

// ══════════════════════════════════════════════════════════════════════════
// 能力边界
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun HonestyPanel() {
    TermPanel(stringResource(R.string.pkt_panel_honesty)) {
        Text(
            stringResource(R.string.pkt_honesty_local_only),
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.pkt_honesty_encryption),
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.pkt_honesty_sessions),
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.pkt_honesty_forwarding),
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.pkt_honesty_shutdown),
            fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 格式
// ══════════════════════════════════════════════════════════════════════════

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
