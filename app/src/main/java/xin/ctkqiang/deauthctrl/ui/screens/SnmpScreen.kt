package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.manager.DEFAULT_SNMP_COMMUNITIES
import xin.ctkqiang.deauthctrl.manager.SNMP_CONCURRENCY
import xin.ctkqiang.deauthctrl.manager.SNMP_PORT
import xin.ctkqiang.deauthctrl.manager.SNMP_TIMEOUT_MS
import xin.ctkqiang.deauthctrl.manager.SnmpResult
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.SnmpViewModel

/**
 * SNMP 社区串扫描（onesixtyone 等价）
 *
 * ## 数据链路
 * 对「目标 IP × 社区串」逐组发送 SNMP v2c GetRequest（UDP 161），
 * 同包读取 sysDescr.0 / sysObjectID.0 / sysName.0。只有收到
 * error-status = 0 且 request-id、社区串均匹配的真实应答才列出。
 *
 * ## 结果读法
 * - IP：响应设备的地址；
 * - 社区串：设备真实接受的字符串（金色）；
 * - sysName：设备配置的主机名；
 * - sysDescr：设备自述的厂商 / 型号 / 固件文本，未展开时截断 60 字；
 * - 展开后可见完整 sysDescr、sysObjectID 与原始应答十六进制。
 *
 * ## 诚实边界
 * 仅支持 SNMP v1/v2c，本页发送 v2c GetRequest，不做 v3 认证 / 加密协商。
 * 设备启用默认社区串（打印机 / 路由器 / 交换机 / NAS 等）时才可能响应；
 * 社区串不对、SNMP 关闭、ACL 拦截或仅开 v3 都表现为无响应，无法区分。
 * 所有字段来自设备真实返回，读不到显示 "--"，不做任何编造。
 * 请只扫描已获授权的网络。
 */
@Composable
fun SnmpScreen(back: () -> Unit) {
    val vm: SnmpViewModel = viewModel()
    val state by vm.state.collectAsState()
    val target by vm.target.collectAsState()
    val communitiesText by vm.communitiesText.collectAsState()

    // 离开页面停止扫描并关闭在途 UDP socket
    DisposableEffect(Unit) {
        onDispose { vm.stop() }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("SNMP 扫描 · SNMPWALK", back)

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            // ── 输入与启动 ────────────────────────────────────────────────
            TermPanel("目标与社区串 · TARGET") {
                TermInput(
                    label = "目标 IP 或网段",
                    value = target,
                    placeholder = "192.168.1.1 或 192.168.1.0/24",
                ) { vm.setTarget(it) }
                Spacer(Modifier.height(8.dp))
                TermInput(
                    label = "社区串（逗号分隔，可编辑）",
                    value = communitiesText,
                    placeholder = DEFAULT_SNMP_COMMUNITIES.joinToString(", "),
                ) { vm.setCommunities(it) }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "UDP $SNMP_PORT · 单包 ${SNMP_TIMEOUT_MS / 1000}s 超时 · 并发 $SNMP_CONCURRENCY",
                        fontFamily = Mono,
                        fontSize = 8.sp,
                        color = Gray,
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton("默认字典") { vm.useDefaultCommunities() }
                }
                state.error?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "[!] $message",
                        fontFamily = Sans,
                        fontSize = 10.sp,
                        color = Danger,
                        lineHeight = 14.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    running = state.scanning,
                    runLabel = "启动扫描",
                    stopLabel = "停止扫描",
                ) {
                    if (state.scanning) vm.stop() else vm.start()
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 进度 ──────────────────────────────────────────────────────
            if (state.scanning || state.total > 0) {
                TermPanel("进度 · PROGRESS") {
                    val fraction = state.progress.coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp))
                            .background(SurfaceAlt),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(6.dp)
                                .clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp))
                                .background(Gold),
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "已探测 ${state.done}/${state.total}",
                            fontFamily = Mono,
                            fontSize = 9.sp,
                            color = Gold,
                        )
                        Text(
                            "命中 ${state.results.size} 台",
                            fontFamily = Mono,
                            fontSize = 9.sp,
                            color = if (state.results.isEmpty()) Gray else TacGreen,
                        )
                        Text(
                            "${(fraction * 100).toInt()}%",
                            fontFamily = Mono,
                            fontSize = 9.sp,
                            color = Gray,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── 结果 ──────────────────────────────────────────────────────
            TermPanel("结果 · RESPONSES (${state.results.size})") {
                if (state.results.isNotEmpty()) {
                    Text(
                        "sysDescr 为设备自述的厂商 / 型号 / 固件文本，点击卡片可展开全文与原始报文",
                        fontFamily = Sans,
                        fontSize = 8.sp,
                        color = Gray,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                when {
                    state.results.isNotEmpty() -> {
                        state.results.forEach { result ->
                            key(result.ip, result.community) {
                                SnmpResultCard(result)
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                    state.scanning -> {
                        Text(
                            "扫描中 —— 命中结果会实时列出",
                            fontFamily = Sans,
                            fontSize = 10.sp,
                            color = Gray,
                        )
                    }
                    state.total > 0 -> {
                        EmptyHint(
                            "本轮 ${state.total} 次探测均无有效响应 —— " +
                                "社区串不匹配、设备未开 SNMP、ACL 拦截或仅支持 v3 都会表现为无响应"
                        )
                    }
                    else -> {
                        EmptyHint("输入目标与社区串后启动扫描；结果只来自设备的真实 SNMP 应答")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 诚实说明 ──────────────────────────────────────────────────
            TermPanel("诚实说明 · SCOPE") {
                Text(
                    "仅支持 SNMP v1/v2c；本页发送 v2c GetRequest，不做 v3 认证与加密协商。",
                    fontFamily = Sans,
                    fontSize = 10.sp,
                    color = White,
                    lineHeight = 14.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "默认社区串的设备（打印机 / 路由器 / 交换机 / NAS）才可能响应；" +
                        "社区串不对即无响应，且与 SNMP 关闭、ACL 拦截、仅开 v3 的表现相同，无法区分原因。",
                    fontFamily = Sans,
                    fontSize = 10.sp,
                    color = Warn,
                    lineHeight = 14.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "sysDescr / sysName / sysObjectID 均为设备真实返回的文本，" +
                        "读不到显示 \"--\"，不做推测；扫描为单包探测，无重试。",
                    fontFamily = Sans,
                    fontSize = 10.sp,
                    color = Gray,
                    lineHeight = 14.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "[!] 请只扫描已获授权的网络。",
                    fontFamily = Sans,
                    fontSize = 10.sp,
                    color = Danger,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

/**
 * 单条 SNMP 结果卡片
 *
 * 收起：IP / 社区串 / sysName / sysDescr（截断 60 字）；
 * 展开：完整 sysDescr、sysObjectID、原始应答十六进制。
 */
@Composable
private fun SnmpResultCard(result: SnmpResult) {
    var expanded by remember { mutableStateOf(false) }
    val sysNameColor = if (result.sysName == "--") Gray else TacGreen

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceAlt,
        border = BorderStroke(1.dp, BorderDim),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Star(Gold, 6.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    result.ip,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    result.community,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "sysName",
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    modifier = Modifier.width(58.dp),
                )
                Text(
                    result.sysName,
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    color = sysNameColor,
                    lineHeight = 14.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "sysDescr",
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    modifier = Modifier.width(58.dp),
                )
                Text(
                    if (expanded) result.sysDescr else truncate(result.sysDescr, 60),
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    color = Gray,
                    lineHeight = 14.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            if (expanded) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "sysObjectID  ${result.sysObjectID}",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = Gold,
                    lineHeight = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "原始应答（hex）  ${result.raw}",
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 11.sp,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "▸ 点击展开 sysDescr 全文 / sysObjectID / 原始报文",
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray.copy(alpha = 0.72f),
                )
            }
        }
    }
}

/** 按字符数截断，超出部分以省略号标记 */
private fun truncate(text: String, maxChars: Int): String =
    if (text.length <= maxChars) text else text.take(maxChars) + "…"
