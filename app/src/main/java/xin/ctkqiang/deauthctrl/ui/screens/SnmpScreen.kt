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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.R
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
        TerminalHeader(stringResource(R.string.snmp_title), back)

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            // ── 输入与启动 ────────────────────────────────────────────────
            TermPanel(stringResource(R.string.snmp_panel_target)) {
                TermInput(
                    label = stringResource(R.string.snmp_target_label),
                    value = target,
                    placeholder = stringResource(R.string.snmp_target_placeholder),
                ) { vm.setTarget(it) }
                Spacer(Modifier.height(8.dp))
                TermInput(
                    label = stringResource(R.string.snmp_communities_label),
                    value = communitiesText,
                    placeholder = DEFAULT_SNMP_COMMUNITIES.joinToString(", "),
                ) { vm.setCommunities(it) }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.snmp_net_params, SNMP_PORT, SNMP_TIMEOUT_MS / 1000, SNMP_CONCURRENCY),
                        fontFamily = Mono,
                        fontSize = 8.sp,
                        color = Gray,
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton(stringResource(R.string.snmp_btn_default_dict)) { vm.useDefaultCommunities() }
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
                    runLabel = stringResource(R.string.snmp_btn_start),
                    stopLabel = stringResource(R.string.snmp_btn_stop),
                ) {
                    if (state.scanning) vm.stop() else vm.start()
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 进度 ──────────────────────────────────────────────────────
            if (state.scanning || state.total > 0) {
                TermPanel(stringResource(R.string.snmp_panel_progress)) {
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
                            stringResource(R.string.snmp_probed, state.done, state.total),
                            fontFamily = Mono,
                            fontSize = 9.sp,
                            color = Gold,
                        )
                        Text(
                            stringResource(R.string.snmp_hits, state.results.size),
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
            TermPanel(stringResource(R.string.snmp_panel_results, state.results.size)) {
                if (state.results.isNotEmpty()) {
                    Text(
                        stringResource(R.string.snmp_results_hint),
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
                            stringResource(R.string.snmp_scanning),
                            fontFamily = Sans,
                            fontSize = 10.sp,
                            color = Gray,
                        )
                    }
                    state.total > 0 -> {
                        EmptyHint(
                            stringResource(R.string.snmp_empty_no_response, state.total)
                        )
                    }
                    else -> {
                        EmptyHint(stringResource(R.string.snmp_empty_idle))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 诚实说明 ──────────────────────────────────────────────────
            TermPanel(stringResource(R.string.snmp_panel_scope)) {
                Text(
                    stringResource(R.string.snmp_scope_protocol),
                    fontFamily = Sans,
                    fontSize = 10.sp,
                    color = White,
                    lineHeight = 14.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    stringResource(R.string.snmp_scope_default),
                    fontFamily = Sans,
                    fontSize = 10.sp,
                    color = Warn,
                    lineHeight = 14.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    stringResource(R.string.snmp_scope_fields),
                    fontFamily = Sans,
                    fontSize = 10.sp,
                    color = Gray,
                    lineHeight = 14.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    stringResource(R.string.snmp_scope_authorized),
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
                    stringResource(R.string.snmp_raw_response, result.raw),
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 11.sp,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.snmp_expand_hint),
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
