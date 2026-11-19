package xin.ctkqiang.deauthctrl.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.DnsType
import xin.ctkqiang.deauthctrl.manager.KeyGen
import xin.ctkqiang.deauthctrl.manager.TlsInspector
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.PanelStat
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.DnsViewModel
import xin.ctkqiang.deauthctrl.viewmodel.HashViewModel
import xin.ctkqiang.deauthctrl.viewmodel.KeyGenViewModel
import xin.ctkqiang.deauthctrl.viewmodel.LevelViewModel
import xin.ctkqiang.deauthctrl.viewmodel.MdnsViewModel
import xin.ctkqiang.deauthctrl.viewmodel.QrViewModel
import xin.ctkqiang.deauthctrl.viewmodel.TlsViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WhoisViewModel
import java.io.File
import java.io.FileOutputStream

// ══════════════════════════════════════════════════════════════════════════
// DNS 工坊
// ══════════════════════════════════════════════════════════════════════════

/** DNS 工坊 —— 手写报文查询 A/AAAA/CNAME/MX/TXT/NS/SOA/PTR 记录 */
@Composable
fun DnsScreen(vm: DnsViewModel, back: () -> Unit) {
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.dns_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            TermPanel(stringResource(R.string.dns_panel_query)) {
                TermInput(
                    stringResource(R.string.dns_label_domain),
                    state.domain,
                    stringResource(R.string.dns_ph_domain),
                    onValue = vm::setDomain,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.dns_label_type), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(DnsType.A, DnsType.AAAA, DnsType.CNAME, DnsType.MX).forEach { t ->
                            SelectChip(state.type == t, DnsType.label(t)) { vm.setType(t) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(DnsType.TXT, DnsType.NS, DnsType.SOA, DnsType.PTR).forEach { t ->
                            SelectChip(state.type == t, DnsType.label(t)) { vm.setType(t) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.dns_label_server), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.servers.take(4).forEach { s ->
                        SelectChip(state.server == s, s) { vm.setServer(s) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ActionButton(running = state.running, runLabel = stringResource(R.string.dns_run), stopLabel = stringResource(R.string.dns_run)) {
                    haptic.hud()
                    vm.query()
                }
                state.error?.let {
                    Text("[!] $it", fontFamily = Sans, fontSize = 9.sp, color = Danger, lineHeight = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.dns_panel_result)) {
                val result = state.result
                if (result == null) {
                    EmptyHint(stringResource(R.string.dns_empty_result))
                } else {
                    PanelStat(stringResource(R.string.dns_label_elapsed), "${result.elapsedMs} ms", White)
                    PanelStat(stringResource(R.string.dns_answer_count), "${result.answers.size}", if (result.answers.isEmpty()) Warn else TacGreen)
                    Spacer(Modifier.height(6.dp))
                    if (result.answers.isEmpty()) {
                        EmptyHint(stringResource(R.string.dns_empty_answer))
                    }
                    result.answers.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text("${DnsType.label(a.type)}", fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacGreen, modifier = Modifier.width(52.dp))
                            Text(a.data, fontFamily = Mono, fontSize = 10.sp, color = White, lineHeight = 14.sp, modifier = Modifier.weight(1f))
                            Text("TTL ${a.ttl}", fontFamily = Mono, fontSize = 8.sp, color = Gray)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.dns_panel_limits)) {
                Text(stringResource(R.string.dns_limit_text), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// mDNS 服务发现
// ══════════════════════════════════════════════════════════════════════════

/** mDNS 服务发现 —— 局域网 Bonjour/Avahi 服务浏览器 */
@Composable
fun MdnsScreen(vm: MdnsViewModel, back: () -> Unit) {
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current

    DisposableEffect(Unit) { onDispose { vm.stop() } }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.mdns_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            TermPanel(stringResource(R.string.mdns_panel_control)) {
                PanelStat(
                    stringResource(R.string.mdns_label_status),
                    if (state.running) stringResource(R.string.mdns_status_scanning) else stringResource(R.string.mdns_status_idle),
                    if (state.running) TacGreen else Gray,
                )
                PanelStat(stringResource(R.string.mdns_label_found), "${state.services.size}", White)
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    running = state.running,
                    runLabel = stringResource(R.string.mdns_run),
                    stopLabel = stringResource(R.string.mdns_stop),
                ) {
                    haptic.hud()
                    if (state.running) vm.stop() else vm.start()
                }
                state.error?.let {
                    Text("[!] $it", fontFamily = Sans, fontSize = 9.sp, color = Danger, lineHeight = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.mdns_panel_results)) {
                if (state.services.isEmpty() && !state.running) {
                    EmptyHint(stringResource(R.string.mdns_empty))
                } else {
                    state.services.forEach { svc ->
                        Surface(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            color = SurfaceAlt,
                            border = BorderStroke(1.dp, BorderDim),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Star(Gold, 7.dp)
                                    Spacer(Modifier.width(7.dp))
                                    Text(svc.name, fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${stringResource(R.string.mdns_label_type)} ${svc.type}  ·  ${svc.host}:${svc.port}",
                                    fontFamily = Mono,
                                    fontSize = 9.sp,
                                    color = TacGreen,
                                )
                                if (svc.addresses.isNotEmpty()) {
                                    Text(svc.addresses.joinToString(" "), fontFamily = Mono, fontSize = 9.sp, color = Gold)
                                }
                                if (svc.txt.isNotEmpty()) {
                                    Text(svc.txt.joinToString(" · "), fontFamily = Mono, fontSize = 8.sp, color = Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    if (state.running) {
                        Text(stringResource(R.string.mdns_scanning_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.mdns_panel_limits)) {
                Text(stringResource(R.string.mdns_limit_text), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// TLS 证书检查
// ══════════════════════════════════════════════════════════════════════════

/** TLS 证书检查 —— 直连读取证书链并逐张体检 */
@Composable
fun TlsCertScreen(vm: TlsViewModel, back: () -> Unit) {
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.tls_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            TermPanel(stringResource(R.string.tls_panel_target)) {
                TermInput(stringResource(R.string.tls_label_host), state.host, stringResource(R.string.tls_ph_host), onValue = vm::setHost)
                Spacer(Modifier.height(8.dp))
                TermInput(stringResource(R.string.tls_label_port), state.port, "443", onValue = vm::setPort)
                Spacer(Modifier.height(10.dp))
                ActionButton(running = state.running, runLabel = stringResource(R.string.tls_run), stopLabel = stringResource(R.string.tls_run)) {
                    haptic.hud()
                    vm.inspect()
                }
                state.error?.let {
                    Text("[!] $it", fontFamily = Sans, fontSize = 9.sp, color = Danger, lineHeight = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            val result = state.result
            if (result != null) {
                Spacer(Modifier.height(8.dp))
                TermPanel(stringResource(R.string.tls_panel_meta)) {
                    PanelStat(stringResource(R.string.tls_label_protocol), result.protocol, TacGreen)
                    PanelStat(stringResource(R.string.tls_label_cipher), result.cipher, White)
                    PanelStat(stringResource(R.string.tls_label_elapsed), "${result.elapsedMs} ms", White)
                }
                result.chain.forEachIndexed { i, cert ->
                    Spacer(Modifier.height(8.dp))
                    TermPanel(stringResource(R.string.tls_cert_index, i + 1)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                when (cert.status) {
                                    TlsInspector.Status.VALID -> stringResource(R.string.tls_status_valid)
                                    TlsInspector.Status.NOT_YET_VALID -> stringResource(R.string.tls_status_notyet)
                                    TlsInspector.Status.EXPIRED -> stringResource(R.string.tls_status_expired)
                                },
                                fontFamily = Sans,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (cert.status) {
                                    TlsInspector.Status.VALID -> TacGreen
                                    TlsInspector.Status.NOT_YET_VALID -> Warn
                                    TlsInspector.Status.EXPIRED -> Danger
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.tls_days_left, cert.daysLeft),
                                fontFamily = Mono,
                                fontSize = 9.sp,
                                color = if (cert.daysLeft < 30) Warn else Gray,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        PanelStat(stringResource(R.string.tls_label_subject), cert.subject, White)
                        PanelStat(stringResource(R.string.tls_label_issuer), cert.issuer, Gray)
                        PanelStat(stringResource(R.string.tls_label_validity), "${cert.notBefore} → ${cert.notAfter}", White)
                        if (cert.san.isNotEmpty()) {
                            PanelStat(stringResource(R.string.tls_label_san), cert.san.joinToString(", "), Gray)
                        }
                        PanelStat(stringResource(R.string.tls_label_serial), cert.serial, Gold)
                        PanelStat(stringResource(R.string.tls_label_sigalg), cert.sigAlg, Gray)
                        PanelStat(stringResource(R.string.tls_label_keyalg), cert.keyAlg, Gray)
                        PanelStat(
                            stringResource(R.string.tls_label_hostmatch),
                            if (cert.hostnameMatch) stringResource(R.string.tls_hostmatch_ok) else stringResource(R.string.tls_hostmatch_fail),
                            if (cert.hostnameMatch) TacGreen else Danger,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.tls_label_fingerprint), fontFamily = Sans, fontSize = 9.sp, color = Gray)
                        Text(cert.fingerprint, fontFamily = Mono, fontSize = 8.sp, color = White, lineHeight = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.tls_panel_limits)) {
                Text(stringResource(R.string.tls_limit_text), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Whois 查询
// ══════════════════════════════════════════════════════════════════════════

/** Whois 查询 —— TCP 43 直连注册局数据库 */
@Composable
fun WhoisScreen(vm: WhoisViewModel, back: () -> Unit) {
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.whois_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            TermPanel(stringResource(R.string.whois_panel_query)) {
                TermInput(stringResource(R.string.whois_label_domain), state.domain, stringResource(R.string.whois_ph_domain), onValue = vm::setDomain)
                Spacer(Modifier.height(10.dp))
                ActionButton(running = state.running, runLabel = stringResource(R.string.whois_run), stopLabel = stringResource(R.string.whois_run)) {
                    haptic.hud()
                    vm.query()
                }
                state.error?.let {
                    Text("[!] $it", fontFamily = Sans, fontSize = 9.sp, color = Danger, lineHeight = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            val result = state.result
            if (result != null) {
                Spacer(Modifier.height(8.dp))
                TermPanel(stringResource(R.string.whois_panel_result)) {
                    PanelStat(stringResource(R.string.whois_label_server), result.server, TacGreen)
                    PanelStat(stringResource(R.string.whois_label_elapsed), "${result.elapsedMs} ms", White)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(if (state.showRaw) stringResource(R.string.whois_btn_keys) else stringResource(R.string.whois_btn_raw)) {
                            haptic.hud()
                            vm.toggleRaw()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (state.showRaw) {
                        Text(result.raw, fontFamily = Mono, fontSize = 9.sp, color = White, lineHeight = 13.sp)
                    } else if (state.keyLines.isEmpty()) {
                        EmptyHint(stringResource(R.string.whois_empty))
                    } else {
                        state.keyLines.forEach { line ->
                            Text(line, fontFamily = Mono, fontSize = 9.sp, color = White, lineHeight = 13.sp, modifier = Modifier.padding(vertical = 1.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.whois_panel_limits)) {
                Text(stringResource(R.string.whois_limit_text), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 哈希工坊
// ══════════════════════════════════════════════════════════════════════════

/** 哈希工坊 —— 文本与文件的 SHA-256 / SHA-1 / MD5 */
@Composable
fun HashScreen(vm: HashViewModel, back: () -> Unit) {
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.hashFile(it) }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.hash_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            TermPanel(stringResource(R.string.hash_panel_text)) {
                Text(stringResource(R.string.hash_label_algo), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    xin.ctkqiang.deauthctrl.manager.HashTool.ALGORITHMS.forEach { a ->
                        SelectChip(state.algorithm == a, a) { vm.setAlgorithm(a) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.hash_label_text), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 4.dp))
                BasicTextField(
                    value = state.text,
                    onValueChange = vm::setText,
                    textStyle = TextStyle(fontFamily = Mono, fontSize = 12.sp, color = White),
                    cursorBrush = SolidColor(Gold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, BorderDim, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        if (state.text.isEmpty()) {
                            Text(stringResource(R.string.hash_ph_text), fontFamily = Sans, fontSize = 11.sp, color = Gray.copy(alpha = 0.5f))
                        }
                        inner()
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.textDigest.ifEmpty { stringResource(R.string.hash_empty_digest) }, fontFamily = Mono, fontSize = 9.sp, color = if (state.textDigest.isEmpty()) Gray else White, lineHeight = 13.sp, modifier = Modifier.weight(1f))
                    if (state.textDigest.isNotEmpty()) {
                        GhostButton(stringResource(R.string.hash_btn_copy)) {
                            haptic.hud()
                            clipboard.setText(AnnotatedString(state.textDigest))
                            copied = true
                        }
                    }
                }
                if (copied) {
                    Text(stringResource(R.string.hash_copied), fontFamily = Sans, fontSize = 8.sp, color = TacGreen, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.hash_panel_file)) {
                GhostButton(stringResource(R.string.hash_btn_pick)) {
                    haptic.hud()
                    picker.launch(arrayOf("*/*"))
                }
                if (state.fileDigest.isNotEmpty() || state.fileProgress > 0L) {
                    Spacer(Modifier.height(8.dp))
                    PanelStat(stringResource(R.string.hash_label_file_progress), "%.1f KB".format(state.fileProgress / 1024.0), White)
                    Text(state.fileDigest, fontFamily = Mono, fontSize = 9.sp, color = Gold, lineHeight = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                state.fileError?.let {
                    Text("[!] $it", fontFamily = Sans, fontSize = 9.sp, color = Danger, lineHeight = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.hash_panel_limits)) {
                Text(stringResource(R.string.hash_limit_text), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 二维码工坊
// ══════════════════════════════════════════════════════════════════════════

/** 二维码工坊 —— 文本 / URL / WiFi / 名片二维码生成 */
@Composable
fun QrScreen(vm: QrViewModel, back: () -> Unit) {
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var savedPath by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.qr_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            TermPanel(stringResource(R.string.qr_panel_content)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "text" to stringResource(R.string.qr_mode_text),
                        "url" to stringResource(R.string.qr_mode_url),
                        "wifi" to stringResource(R.string.qr_mode_wifi),
                        "vcard" to stringResource(R.string.qr_mode_vcard),
                    ).forEach { (m, label) ->
                        SelectChip(state.mode == m, label) { vm.setMode(m) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (state.mode) {
                    "url" -> TermInput(stringResource(R.string.qr_label_url), state.url, stringResource(R.string.qr_ph_url)) { v -> vm.update { s -> s.copy(url = v) } }
                    "wifi" -> {
                        TermInput(stringResource(R.string.qr_label_ssid), state.wifiSsid, stringResource(R.string.qr_ph_ssid)) { v -> vm.update { it.copy(wifiSsid = v) } }
                        Spacer(Modifier.height(8.dp))
                        TermInput(stringResource(R.string.qr_label_pass), state.wifiPass, stringResource(R.string.qr_ph_pass)) { v -> vm.update { it.copy(wifiPass = v) } }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.qr_label_security), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("WPA", "WEP", "nopass").forEach { sec ->
                                SelectChip(state.wifiSecurity == sec, sec) { vm.update { it.copy(wifiSecurity = sec) } }
                            }
                        }
                    }
                    "vcard" -> {
                        TermInput(stringResource(R.string.qr_label_name), state.cardName, stringResource(R.string.qr_ph_name)) { v -> vm.update { it.copy(cardName = v) } }
                        Spacer(Modifier.height(8.dp))
                        TermInput(stringResource(R.string.qr_label_phone), state.cardPhone, stringResource(R.string.qr_ph_phone)) { v -> vm.update { it.copy(cardPhone = v) } }
                        Spacer(Modifier.height(8.dp))
                        TermInput(stringResource(R.string.qr_label_email), state.cardEmail, stringResource(R.string.qr_ph_email)) { v -> vm.update { it.copy(cardEmail = v) } }
                    }
                    else -> TermInput(stringResource(R.string.qr_label_text), state.text, stringResource(R.string.qr_ph_text)) { v -> vm.update { it.copy(text = v) } }
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.qr_panel_preview)) {
                val bmp = state.bitmap
                if (bmp == null) {
                    EmptyHint(stringResource(R.string.qr_empty))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, BorderDim),
                        ) {
                            Image(bmp.asImageBitmap(), contentDescription = stringResource(R.string.qr_title), modifier = Modifier.size(220.dp).padding(8.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(stringResource(R.string.qr_btn_copy), Modifier.weight(1f)) {
                                haptic.hud()
                                clipboard.setText(AnnotatedString(state.payload))
                            }
                            GhostButton(stringResource(R.string.qr_btn_share), Modifier.weight(1f)) {
                                haptic.hud()
                                shareQr(context, bmp)
                            }
                            GhostButton(stringResource(R.string.qr_btn_save), Modifier.weight(1f)) {
                                haptic.hud()
                                savedPath = null
                                saveError = null
                                scope.launch {
                                    runCatching { saveQr(context, bmp) }
                                        .onSuccess { savedPath = it }
                                        .onFailure { saveError = it.message }
                                }
                            }
                        }
                        savedPath?.let {
                            Text(stringResource(R.string.qr_saved, it), fontFamily = Sans, fontSize = 8.sp, color = TacGreen, lineHeight = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        saveError?.let {
                            Text(stringResource(R.string.qr_save_failed, it), fontFamily = Sans, fontSize = 8.sp, color = Danger, lineHeight = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.qr_panel_limits)) {
                Text(stringResource(R.string.qr_limit_text), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 写 PNG 到 cache 并拉起系统分享 */
private fun shareQr(context: android.content.Context, bmp: Bitmap) {
    val file = File(context.cacheDir, "qrcode.png")
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/** 保存到系统相册;返回保存路径。API 29+ 走 MediaStore,旧版走插入接口 */
private suspend fun saveQr(context: android.content.Context, bmp: Bitmap): String = withContext(Dispatchers.IO) {
    val name = "deauthctrl_qr_${System.currentTimeMillis()}.png"
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DeauthCtrl")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            ?: error("cannot open output")
        "Pictures/DeauthCtrl/$name"
    } else {
        val path = MediaStore.Images.Media.insertImage(context.contentResolver, bmp, name, null)
            ?: error("insertImage failed")
        path
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 密码生成器
// ══════════════════════════════════════════════════════════════════════════

/** 密码生成器 —— SecureRandom 高熵随机 */
@Composable
fun KeyGenScreen(vm: KeyGenViewModel, back: () -> Unit) {
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.keygen_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            TermPanel(stringResource(R.string.keygen_panel_gen)) {
                Text(stringResource(R.string.keygen_label_mode), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyGen.Mode.entries.forEach { m ->
                        SelectChip(state.mode == m, m.name) { vm.setMode(m) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.keygen_label_length, state.length), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold)
                Slider(
                    value = state.length.toFloat(),
                    onValueChange = { vm.setLength(it.toInt()) },
                    valueRange = 8f..64f,
                    steps = 55,
                    colors = SliderDefaults.colors(
                        thumbColor = White,
                        activeTrackColor = TacGreen,
                        inactiveTrackColor = BorderDim,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("8", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                    Text("64", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                }
                Spacer(Modifier.height(8.dp))
                // 强度段:按熵值三段亮灯
                val lit = when {
                    state.entropyBits >= 90 -> 3
                    state.entropyBits >= 60 -> 2
                    else -> 1
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.keygen_label_entropy), fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.weight(1f))
                    Text("%.0f bits".format(state.entropyBits), fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (lit >= 2) TacGreen else Warn)
                    Spacer(Modifier.width(8.dp))
                    repeat(3) { i ->
                        Box(
                            Modifier.size(width = 18.dp, height = 7.dp)
                                .clip(CutCornerShape(topStart = 3.dp, bottomEnd = 3.dp))
                                .background(
                                    if (i < lit) when (lit) {
                                        3 -> TacGreen
                                        2 -> Gold
                                        else -> Warn
                                    } else BorderDim.copy(alpha = 0.4f),
                                ),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = SurfaceAlt,
                    border = BorderStroke(1.dp, BorderDim),
                ) {
                    Text(
                        state.output,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                        color = White,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(running = false, runLabel = stringResource(R.string.keygen_btn_regen), stopLabel = stringResource(R.string.keygen_btn_regen), modifier = Modifier.weight(1f)) {
                        haptic.hud()
                        vm.regenerate()
                    }
                    GhostButton(stringResource(R.string.keygen_btn_copy), Modifier.weight(1f)) {
                        haptic.hud()
                        clipboard.setText(AnnotatedString(state.output))
                        copied = true
                    }
                }
                if (copied) {
                    Text(stringResource(R.string.hash_copied), fontFamily = Sans, fontSize = 8.sp, color = TacGreen, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.keygen_panel_limits)) {
                Text(stringResource(R.string.keygen_limit_text), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 全息水平仪
// ══════════════════════════════════════════════════════════════════════════

/** 全息水平仪 —— 重力矢量气泡水平仪 */
@Composable
fun LevelScreen(back: () -> Unit) {
    val vm: LevelViewModel = viewModel()
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current

    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }

    val bubbleColor = if (state.locked) TacGreen else Gold

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.level_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            TermPanel(stringResource(R.string.level_panel_gauge)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Canvas(Modifier.size(230.dp)) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = size.minDimension / 2f
                        // 盘面:外环 + 内参考环 + 十字准星
                        drawCircle(bubbleColor.copy(alpha = 0.35f), r * 0.97f, c, style = Stroke(1.6.dp.toPx()))
                        drawCircle(BorderDim.copy(alpha = 0.8f), r * 0.66f, c, style = Stroke(0.8.dp.toPx()))
                        drawCircle(BorderDim.copy(alpha = 0.5f), r * 0.33f, c, style = Stroke(0.8.dp.toPx()))
                        drawLine(BorderDim.copy(alpha = 0.6f), Offset(c.x - r * 0.9f, c.y), Offset(c.x + r * 0.9f, c.y), 0.8.dp.toPx())
                        drawLine(BorderDim.copy(alpha = 0.6f), Offset(c.x, c.y - r * 0.9f), Offset(c.x, c.y + r * 0.9f), 0.8.dp.toPx())
                        // 锁定圈:水平时亮起
                        if (state.locked) {
                            drawCircle(bubbleColor.copy(alpha = 0.25f), r * 0.33f, c, style = Stroke(3.dp.toPx()))
                        }
                        // 气泡:横滚/俯仰映射到盘面坐标(±25° 满程)
                        val max = 25f
                        val bx = c.x + (state.rollDeg / max * r * 0.62f).coerceIn(-r * 0.62f, r * 0.62f) * -1f
                        val by = c.y + (state.pitchDeg / max * r * 0.62f).coerceIn(-r * 0.62f, r * 0.62f)
                        drawCircle(bubbleColor.copy(alpha = 0.22f), 13.dp.toPx(), Offset(bx, by))
                        drawCircle(bubbleColor, 7.dp.toPx(), Offset(bx, by))
                        drawCircle(White.copy(alpha = 0.9f), 2.4.dp.toPx(), Offset(bx, by))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${"%.2f".format(state.rollDeg)}°", fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (kotlin.math.abs(state.rollDeg) < 0.35f) TacGreen else bubbleColor)
                            Text(stringResource(R.string.level_label_roll), fontFamily = Sans, fontSize = 9.sp, color = Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${"%.2f".format(state.pitchDeg)}°", fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (kotlin.math.abs(state.pitchDeg) < 0.35f) TacGreen else bubbleColor)
                            Text(stringResource(R.string.level_label_pitch), fontFamily = Sans, fontSize = 9.sp, color = Gray)
                        }
                    }
                    if (state.locked) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Star(TacGreen, 7.dp)
                            Spacer(Modifier.width(7.dp))
                            Text(stringResource(R.string.level_locked), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacGreen)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GhostButton(stringResource(R.string.level_btn_calibrate)) {
                        haptic.hud()
                        vm.calibrate()
                    }
                    if (state.calibrated) {
                        Text(stringResource(R.string.level_calibrated), fontFamily = Sans, fontSize = 8.sp, color = Gray, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.level_panel_limits)) {
                Text(stringResource(R.string.level_limit_text), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
