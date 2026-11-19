package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.NetBiosHost
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
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
import xin.ctkqiang.deauthctrl.viewmodel.NetBiosViewModel

/** 原始报文在界面上的预览长度(字符);完整报文仍保存在 NetBiosHost.rawHex */
private const val RAW_PREVIEW_CHARS = 160

/**
 * NetBIOS 名称扫描 —— nbtscan 的界面
 *
 * 输入单个 IPv4 或 CIDR 网段后,逐个目标查询 UDP 137 的 NBSTAT;
 * 响应的主机会实时列出 IP、工作站名(<00>)、用户(<03>)、MAC 与 OUI 厂商。
 *
 * 诚实提示:只有 Windows / 启用 NetBIOS over TCP/IP 的设备会响应,
 * 现代 Windows 默认可能关闭该功能,结果少甚至为零都属正常;
 * 所有字段来自真实报文或系统 ARP 缓存,读不到时显示 "--"。
 */
@Composable
fun NetBiosScreen(back: () -> Unit) {
    val vm: NetBiosViewModel = viewModel()
    val state by vm.state.collectAsState()
    var target by remember { mutableStateOf("") }

    // 离开页面即停止扫描,避免后台继续占用线程池与网络
    DisposableEffect(Unit) {
        onDispose { vm.stop() }
    }

    val statusText = when {
        state.scanning -> stringResource(R.string.netbios_status_scanning)
        state.total == 0 -> stringResource(R.string.netbios_status_idle)
        state.progress >= state.total -> stringResource(R.string.netbios_status_done)
        else -> stringResource(R.string.netbios_status_stopped)
    }
    val progressFraction = if (state.total > 0) {
        (state.progress.toFloat() / state.total).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.netbios_title), back)

        Column(
            Modifier.fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            // ── 目标与控制 ──────────────────────────────────────
            TermPanel(stringResource(R.string.netbios_panel_target)) {
                TermInput(
                    label = stringResource(R.string.netbios_target_label),
                    value = target,
                    placeholder = stringResource(R.string.netbios_target_placeholder),
                ) { target = it }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    running = state.scanning,
                    runLabel = stringResource(R.string.netbios_start),
                    stopLabel = stringResource(R.string.netbios_stop),
                ) {
                    if (state.scanning) {
                        vm.stop()
                    } else {
                        vm.scan(target)
                    }
                }
                state.error?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "[!] $message",
                        fontFamily = Sans,
                        fontSize = 9.sp,
                        color = Danger,
                        lineHeight = 13.sp,
                    )
                }
                state.note?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "[!] $message",
                        fontFamily = Sans,
                        fontSize = 9.sp,
                        color = Warn,
                        lineHeight = 13.sp,
                    )
                }
            }

            // ── 进度 ────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.netbios_panel_progress)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        statusText,
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.scanning) TacGreen else Gray,
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        stringResource(R.string.netbios_target_count, if (state.total > 0) "${state.progress}/${state.total}" else "--"),
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        color = Gold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.netbios_responders, state.hosts.size),
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.hosts.isEmpty()) Gray else TacGreen,
                    )
                }
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .height(6.dp)
                        .background(
                            SurfaceAlt,
                            CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                        ),
                ) {
                    Box(
                        Modifier.fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .background(
                                if (state.scanning) TacGreen else Gold.copy(alpha = 0.55f),
                                CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                            ),
                    )
                }
                if (state.target.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.netbios_range, state.target),
                        fontFamily = Mono,
                        fontSize = 9.sp,
                        color = Gray,
                    )
                }
                Text(
                    stringResource(R.string.netbios_scan_params),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ── 结果 ────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.netbios_panel_hosts, state.hosts.size)) {
                if (state.hosts.isEmpty()) {
                    EmptyHint(
                        when {
                            state.scanning -> stringResource(R.string.netbios_empty_scanning)
                            state.total > 0 -> stringResource(R.string.netbios_empty_none)
                            else -> stringResource(R.string.netbios_empty_idle)
                        },
                    )
                } else {
                    state.hosts.forEach { host ->
                        NetBiosHostRow(host)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // ── 说明与边界 ──────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.netbios_panel_notes)) {
                NoteLine(
                    stringResource(R.string.netbios_note_windows),
                    Warn,
                )
                NoteLine(
                    stringResource(R.string.netbios_note_names),
                    White,
                )
                NoteLine(
                    stringResource(R.string.netbios_note_mac),
                    Gray,
                )
                NoteLine(
                    stringResource(R.string.netbios_note_vendor),
                    Gray,
                )
                NoteLine(
                    stringResource(R.string.netbios_note_scope),
                    Gray,
                )
                NoteLine(
                    stringResource(R.string.netbios_note_raw),
                    Gray,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 单台主机结果 —— IP / 主机名 / 用户 / MAC(OUI 厂商) / 原始报文预览 */
@Composable
private fun NetBiosHostRow(host: NetBiosHost) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceAlt,
        border = BorderStroke(1.dp, BorderDim),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Star(Gold, 7.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    host.ip,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = White,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.netbios_name_count, host.names.size),
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    color = Gray,
                )
            }
            Spacer(Modifier.height(5.dp))

            NetBiosField(stringResource(R.string.netbios_field_hostname), host.hostName ?: "--", Gold)
            NetBiosField(stringResource(R.string.netbios_field_user), host.userName ?: "--", White)
            host.fileService?.let { NetBiosField(stringResource(R.string.netbios_field_file_service), it, White) }
            host.masterBrowser?.let { NetBiosField(stringResource(R.string.netbios_field_master_browser), it, White) }
            NetBiosField(
                label = "MAC",
                value = if (host.mac.isBlank()) "--" else "${host.mac} · ${host.vendor}",
                valueColor = if (host.mac.isBlank()) Gray else TacGreen,
            )

            val rawPreview = if (host.rawHex.length <= RAW_PREVIEW_CHARS) {
                host.rawHex
            } else {
                host.rawHex.take(RAW_PREVIEW_CHARS) + "…"
            }
            Text(
                "RAW $rawPreview",
                fontFamily = Mono,
                fontSize = 8.sp,
                color = Gray,
                lineHeight = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** 结果字段行 —— 灰色标签 + 等宽值 */
@Composable
private fun NetBiosField(label: String, value: String, valueColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontFamily = Sans,
            fontSize = 9.sp,
            color = Gray,
            modifier = Modifier.width(84.dp),
        )
        Text(
            value,
            fontFamily = Mono,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 说明条目 —— 菱形箭头 + 正文,与全息 HUD 的排版节奏一致 */
@Composable
private fun NoteLine(text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("▸", fontFamily = Mono, fontSize = 9.sp, color = Gold)
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontFamily = Sans,
            fontSize = 9.sp,
            color = color,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
