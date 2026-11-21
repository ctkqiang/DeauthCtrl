package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.SshClientManager
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.SshClientViewModel

/** 终端输出区底色 —— 与整体 HUD 风格一致 */
private val SshBg = Color(0xFF04121C)

/**
 * SSH 客户端页面 —— 连接表单 + VT100 终端渲染 + 组合键栏
 *
 * 流程:填写主机/端口/用户名/密码 → 连接 → ChannelShell pty 会话,
 * 远端输出经内置 VT100 网格解析渲染,键盘输入写回通道。
 * 离开页面自动断开连接。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SshTerminalScreen(vm: SshClientViewModel, initialHost: String? = null, back: () -> Unit, onOpenLocal: () -> Unit) {
    val status by vm.status.collectAsState()
    val host by vm.host.collectAsState()
    val port by vm.port.collectAsState()
    val username by vm.username.collectAsState()
    val password by vm.password.collectAsState()
    val version by vm.screenVersion.collectAsState()
    val haptic = LocalHapticFeedback.current

    // 雷达同网弹窗「SSH」跳转:目标 IP 无条件覆盖默认值(默认主机框非空,不能用 isBlank 判断)
    LaunchedEffect(initialHost) {
        initialHost?.let { vm.manager.host.value = it }
    }

    val online = status is SshClientManager.Status.Connected
    val clipboard = LocalClipboardManager.current
    var fontSize by remember { mutableIntStateOf(10) }
    var input by remember { mutableStateOf("") }
    var ctrlDown by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    val imeVisible = WindowInsets.isImeVisible
    val fillHostFirst = stringResource(R.string.ssh_fill_host_first)

    // 离开页面断开连接
    DisposableEffect(Unit) {
        onDispose { vm.disconnect() }
    }

    // 终端文本:网格每变化一次重渲染
    val screenText = remember(version, online) { vm.manager.grid.render() }

    /** 组合键发送(修饰键按 flag 合成) */
    fun sendKey(bytes: ByteArray) {
        haptic.hud()
        vm.sendBytes(bytes)
    }

    /** 输入框键入:逐字发送,退格发 DEL;CTRL 按下时按位合成控制字符 */
    fun onInputChange(newText: String) {
        if (newText.length > input.length) {
            var i = input.length
            while (i < newText.length) {
                val c = newText[i]
                if (ctrlDown && c.code in 0x40..0x5F) {
                    vm.sendBytes(byteArrayOf((c.code and 0x1F).toByte()))
                } else {
                    vm.send(c.toString())
                }
                i++
            }
        } else if (newText.length < input.length) {
            repeat(input.length - newText.length) { vm.sendBytes(byteArrayOf(0x7F)) }
        }
        input = newText
        ctrlDown = false
    }

    Column(Modifier.fillMaxSize().background(Dark).navigationBarsPadding().imePadding()) {
        TerminalHeader(stringResource(R.string.ssh_title), back)

        if (!online) {
            // ── 连接表单 ──
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)) {
                Text(stringResource(R.string.ssh_remote_connection), fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Black, color = White)
                Text("JSch · ChannelShell · xterm-256color", fontFamily = Mono, fontSize = 8.sp, color = Gray)
                Spacer(Modifier.height(10.dp))
                // 本地会话:Termux 引擎本机 shell,无需配置任何服务器
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptic.hud()
                        onOpenLocal()
                    },
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = TacGreen.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.6f)),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ssh_local_terminal), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacGreen, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.ssh_local_terminal_desc), fontFamily = Mono, fontSize = 8.sp, color = Gray)
                    }
                }
                Spacer(Modifier.height(12.dp))

                SshField(stringResource(R.string.ssh_host_label), host, "192.168.1.1", Modifier.fillMaxWidth()) { vm.manager.host.value = it }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SshField(stringResource(R.string.ssh_port_label), port, "22", Modifier.weight(0.35f), keyboardType = KeyboardType.Number) { vm.manager.port.value = it }
                    SshField(stringResource(R.string.ssh_username_label), username, "root", Modifier.weight(0.65f)) { vm.manager.username.value = it }
                }
                Spacer(Modifier.height(8.dp))
                SshField(stringResource(R.string.ssh_password_label), password, "••••••", Modifier.fillMaxWidth(), mask = true) { vm.manager.password.value = it }

                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptic.hud()
                        if (vm.host.value.isBlank()) {
                            xin.ctkqiang.deauthctrl.ui.components.StickerBus.show(fillHostFirst)
                        } else {
                            vm.connect()
                        }
                    },
                    shape = CutCornerShape(topStart = 9.dp, bottomEnd = 9.dp),
                    color = Danger,
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.8f)),
                ) {
                    Text(
                        when (status) {
                            is SshClientManager.Status.Connecting -> stringResource(R.string.ssh_connecting)
                            else -> stringResource(R.string.ssh_establish_connection)
                        },
                        fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Black,
                        color = White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                (status as? SshClientManager.Status.Failed)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("[!] ${it.message}", fontFamily = Mono, fontSize = 9.sp, color = Danger)
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.ssh_strict_host_key_warning), fontFamily = Sans, fontSize = 8.sp, color = Gray)
            }
        } else {
            // ── 会话工具条 ──
            Row(
                Modifier.fillMaxWidth().background(SurfaceBg).border(0.5.dp, BorderDim).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(7.dp).height(7.dp).background(TacGreen, CutCornerShape(topStart = 2.dp, bottomEnd = 2.dp)))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.ssh_online_status, host, port),
                    fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TacGreen,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                GhostButton("−") { fontSize = (fontSize - 1).coerceAtLeast(6) }
                Spacer(Modifier.width(4.dp))
                GhostButton("＋") { fontSize = (fontSize + 1).coerceAtMost(24) }
                Spacer(Modifier.width(4.dp))
                GhostButton(stringResource(R.string.ssh_paste)) {
                    haptic.hud()
                    clipboard.getText()?.let { vm.send(it.text) }
                }
                Spacer(Modifier.width(4.dp))
                GhostButton(stringResource(R.string.ssh_clear)) { vm.clear() }
                Spacer(Modifier.width(4.dp))
                GhostButton(stringResource(R.string.ssh_disconnect)) { haptic.hud(); vm.disconnect() }
            }

            // ── 终端显示区(网格渲染 + 尺寸自适应) ──
            val density = LocalDensity.current
            Box(
                Modifier.fillMaxWidth().weight(1f).background(SshBg)
                    .onSizeChanged { size ->
                        val charW = with(density) { fontSize.sp.toPx() * 0.60f }
                        val lineH = with(density) { fontSize.sp.toPx() * 1.25f }
                        val cols = (size.width / charW).toInt().coerceIn(20, 200)
                        val rows = (size.height / lineH).toInt().coerceIn(4, 80)
                        vm.resize(cols, rows)
                    },
            ) {
                Text(
                    screenText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.25f).sp,
                    color = Color(0xFFE8F4FF),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
                )
                // 点击显示区聚焦输入框(IME 唤起)
                Box(
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { inputFocus.requestFocus() },
                )
            }

            // ── 输入行 ──
            Row(
                Modifier.fillMaxWidth().background(SurfaceBg).border(0.5.dp, BorderDim).padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$ ", fontFamily = Mono, fontSize = fontSize.sp, color = TacGreen)
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = input,
                        onValueChange = ::onInputChange,
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, color = White),
                        cursorBrush = SolidColor(Gold),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                vm.send("\r")
                                input = ""
                            },
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(inputFocus),
                        decorationBox = { inner ->
                            if (input.isEmpty() && !imeVisible) {
                                Text(stringResource(R.string.ssh_input_hint), fontFamily = Sans, fontSize = 9.sp, color = Gray.copy(alpha = 0.6f))
                            }
                            inner()
                        },
                    )
                }
            }

            // ── 组合键栏 ──
            Row(
                Modifier.fillMaxWidth().background(SurfaceBg).border(0.5.dp, BorderDim).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SshKey("TAB", Modifier.weight(1f)) { sendKey(byteArrayOf(0x09)) }
                SshKey("ESC", Modifier.weight(1f)) { sendKey(byteArrayOf(0x1B)) }
                SshKey("CTRL", Modifier.weight(1f), active = ctrlDown) { ctrlDown = !ctrlDown }
                SshKey("⌫", Modifier.weight(1f)) { sendKey(byteArrayOf(0x7F)) }
                SshKey("⏎", Modifier.weight(1f)) {
                    vm.send("\r")
                    input = ""
                }
            }
            Row(
                Modifier.fillMaxWidth().background(SurfaceBg).padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SshKey("↑", Modifier.weight(1f)) { sendKey(byteArrayOf(0x1B, 0x5B, 0x41)) }
                SshKey("↓", Modifier.weight(1f)) { sendKey(byteArrayOf(0x1B, 0x5B, 0x42)) }
                SshKey("←", Modifier.weight(1f)) { sendKey(byteArrayOf(0x1B, 0x5B, 0x44)) }
                SshKey("→", Modifier.weight(1f)) { sendKey(byteArrayOf(0x1B, 0x5B, 0x43)) }
                SshKey("HOME", Modifier.weight(1f)) { sendKey(byteArrayOf(0x1B, 0x5B, 0x31, 0x7E)) }
                SshKey("END", Modifier.weight(1f)) { sendKey(byteArrayOf(0x1B, 0x5B, 0x34, 0x7E)) }
            }
        }
    }
}

/** 组合键按钮 */
@Composable
private fun SshKey(label: String, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
        color = if (active) Gold.copy(alpha = 0.25f) else SurfaceAlt,
        border = BorderStroke(0.7.dp, if (active) Gold else BorderDim),
    ) {
        Text(
            label,
            fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            color = if (active) Gold else White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

/** SSH 表单输入框(HUD 风格,支持密码掩码) */
@Composable
private fun SshField(
    label: String,
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    mask: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Ascii,
    onValue: (String) -> Unit,
) {
    Column(modifier) {
        Text(label, fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.fillMaxWidth()
                .border(1.dp, BorderDim, CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                .background(SurfaceAlt)
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Mono, fontSize = 12.sp, color = White),
                cursorBrush = SolidColor(Gold),
                visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, fontFamily = Sans, fontSize = 11.sp, color = Gray.copy(alpha = 0.6f))
                    }
                    inner()
                },
            )
        }
    }
}
