package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.AdField
import xin.ctkqiang.deauthctrl.manager.BondState
import xin.ctkqiang.deauthctrl.manager.BondedDevice
import xin.ctkqiang.deauthctrl.manager.NusLineEnding
import xin.ctkqiang.deauthctrl.manager.NusPhase
import xin.ctkqiang.deauthctrl.manager.NusState
import xin.ctkqiang.deauthctrl.manager.NusTerminalManager
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.LogHeader
import xin.ctkqiang.deauthctrl.ui.components.LogLine
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.PanelStat
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.NordicBenchViewModel
import xin.ctkqiang.deauthctrl.viewmodel.RadarViewModel

/**
 * Nordic 工作台 —— 广告解析 / NUS 终端 / 绑定管理
 *
 * 对标 nRF Connect 的三件套：
 * 1. 广告解析：按 AD Structure 解析粘贴进来的真实广播字节，四列展示名称、类型、
 *    原始值与解码说明；
 * 2. NUS 终端：连接 Nordic UART Service（兼容 FFE0 / FFE1 串口），显示协商后的
 *    MTU / PHY，收发 UTF-8 文本并保留连接日志；
 * 3. 绑定管理：列出系统已配对设备，尝试程序化解绑，并在系统拒绝时如实提示
 *    到系统蓝牙设置中忽略设备。
 *
 * 数据纪律：全部展示来自真实输入 / 系统回调；不生成示例广播、不模拟回显、
 * 不补全读不到的设备名。NUS 只对烧录了对应固件的设备有效。
 */
@Composable
fun NordicBenchScreen(radarVm: RadarViewModel, back: () -> Unit) {
    val vm: NordicBenchViewModel = viewModel()
    val nusState by vm.nusState.collectAsState()
    val bondState by vm.bondState.collectAsState()

    var tab by remember { mutableStateOf(0) }

    // 广告解析页签状态（提升到页面层，切换页签不丢输入与结果）
    var adHex by remember { mutableStateOf("") }
    var adFields by remember { mutableStateOf<List<AdField>>(emptyList()) }
    var adError by remember { mutableStateOf<String?>(null) }
    var adParsed by remember { mutableStateOf(false) }

    // NUS 页签状态
    var macInput by remember { mutableStateOf("") }
    var sendText by remember { mutableStateOf("") }
    var lineEnding by remember { mutableStateOf(NusLineEnding.CRLF) }
    // 设备选择器:从雷达扫描结果点选,免手输 MAC
    var showPicker by remember { mutableStateOf(false) }

    // 绑定页签的待确认删除目标
    var pendingRemove by remember { mutableStateOf<BondedDevice?>(null) }

    // 解析错误提示在组合期解析，供非组合的点击回调使用
    val errEmptyHex = stringResource(R.string.nordic_err_empty_hex)
    val errInvalidHex = stringResource(R.string.nordic_err_invalid_hex)
    val errEmptyAd = stringResource(R.string.nordic_err_empty_ad)

    // 离开页面主动断开，避免 GATT 连接留在后台
    DisposableEffect(Unit) {
        onDispose { vm.disconnect() }
    }

    // 进入绑定页签时重新读取系统配对列表
    LaunchedEffect(tab) {
        if (tab == 2) vm.refreshBonds()
    }

    Column(Modifier.fillMaxSize().background(Dark).navigationBarsPadding().imePadding()) {
        TerminalHeader(stringResource(R.string.nordic_title), back)

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(tab == 0, stringResource(R.string.nordic_tab_adv)) { tab = 0 }
                SelectChip(tab == 1, stringResource(R.string.nordic_tab_nus)) { tab = 1 }
                SelectChip(tab == 2, stringResource(R.string.nordic_tab_bond)) { tab = 2 }
            }
            Spacer(Modifier.height(10.dp))

            when (tab) {
                0 -> AdDecodeTab(
                    hexInput = adHex,
                    onHexInput = { adHex = it },
                    fields = adFields,
                    error = adError,
                    parsedOnce = adParsed,
                    onParse = {
                        val input = adHex.trim()
                        if (input.isEmpty()) {
                            adFields = emptyList()
                            adError = errEmptyHex
                            adParsed = true
                        } else {
                            val result = vm.decodeAdvertising(input)
                            when {
                                result == null -> {
                                    adFields = emptyList()
                                    adError = errInvalidHex
                                }
                                result.isEmpty() -> {
                                    adFields = emptyList()
                                    adError = errEmptyAd
                                }
                                else -> {
                                    adFields = result
                                    adError = null
                                }
                            }
                            adParsed = true
                        }
                    },
                    onClear = {
                        adHex = ""
                        adFields = emptyList()
                        adError = null
                        adParsed = false
                    },
                )

                1 -> NusTab(
                    vm = vm,
                    state = nusState,
                    macInput = macInput,
                    onMacInput = { macInput = it },
                    sendText = sendText,
                    onSendText = { sendText = it },
                    lineEnding = lineEnding,
                    onLineEnding = { lineEnding = it },
                    onOpenPicker = { showPicker = true },
                )

                else -> BondTab(
                    vm = vm,
                    state = bondState,
                    pendingRemove = pendingRemove,
                    onPendingRemove = { pendingRemove = it },
                )
            }

            Spacer(Modifier.height(10.dp))
            HonestyPanel()
            Spacer(Modifier.height(10.dp))
        }
    }

    // ── 设备选择器:点选雷达扫描结果,免手输 MAC ──
    if (showPicker) {
        RadarDevicePickerDialog(
            vm = radarVm,
            title = stringResource(R.string.nordic_picker_title),
            typeFilter = "all",
            onPick = { t ->
                macInput = if (t.type == "wifi") t.id else t.address
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

// ─── 广告解析 ────────────────────────────────────────────────────────────────

/**
 * 广告解析页签
 *
 * 输入十六进制广播字节 → [AdField] 四列表格。解析失败时给出具体原因，
 * 不生成任何示例数据。
 */
@Composable
private fun AdDecodeTab(
    hexInput: String,
    onHexInput: (String) -> Unit,
    fields: List<AdField>,
    error: String?,
    parsedOnce: Boolean,
    onParse: () -> Unit,
    onClear: () -> Unit,
) {
    TermPanel(stringResource(R.string.nordic_panel_adv_input)) {
        TermInput(
            stringResource(R.string.nordic_input_hex_label),
            hexInput,
            placeholder = stringResource(R.string.nordic_input_hex_placeholder),
        ) { onHexInput(it) }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GhostButton(stringResource(R.string.nordic_btn_parse)) { onParse() }
            GhostButton(stringResource(R.string.nordic_btn_clear)) { onClear() }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.nordic_format_note),
            fontFamily = Sans,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 12.sp,
        )
    }

    error?.let { message ->
        Spacer(Modifier.height(8.dp))
        WarningBannerText("[!] $message")
    }

    Spacer(Modifier.height(8.dp))
    TermPanel(stringResource(R.string.nordic_panel_result, fields.size)) {
        if (fields.isEmpty()) {
            EmptyHint(
                if (parsedOnce) {
                    stringResource(R.string.nordic_empty_no_fields)
                } else {
                    stringResource(R.string.nordic_empty_hint)
                },
            )
        } else {
            AdFieldHeader()
            fields.forEachIndexed { index, field -> AdFieldRow(index, field) }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.nordic_uuid_note),
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Gray,
                lineHeight = 12.sp,
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    TermPanel(stringResource(R.string.nordic_panel_scope)) {
        Text(
            stringResource(R.string.nordic_scope_note1),
            fontFamily = Sans,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.nordic_scope_note2),
            fontFamily = Sans,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 12.sp,
        )
    }
}

/** 四列表头：名称 / 类型 / 原始值 / 解码说明 */
@Composable
private fun AdFieldHeader() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AdHeaderCell(stringResource(R.string.nordic_col_name), 1.05f)
        AdHeaderCell(stringResource(R.string.nordic_col_type), 0.5f)
        AdHeaderCell(stringResource(R.string.nordic_col_raw), 1.2f)
        AdHeaderCell(stringResource(R.string.nordic_col_decoded), 2.1f)
    }
    Spacer(Modifier.fillMaxWidth().height(0.5.dp).background(BorderDim))
}

@Composable
private fun RowScope.AdHeaderCell(text: String, weight: Float) {
    Text(
        text,
        Modifier.weight(weight),
        fontFamily = Sans,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        color = Gold,
    )
}

/** 单个 AD 字段行：四列内容按顶部对齐，长说明自动换行 */
@Composable
private fun AdFieldRow(index: Int, field: AdField) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "${index + 1}. ${field.name}",
            Modifier.weight(1.05f),
            fontFamily = Sans,
            fontSize = 8.sp,
            color = White,
            lineHeight = 11.sp,
        )
        Text(
            field.typeHex,
            Modifier.weight(0.5f),
            fontFamily = Mono,
            fontSize = 8.sp,
            color = Gold,
            lineHeight = 11.sp,
        )
        Text(
            if (field.valueHex.isEmpty()) stringResource(R.string.nordic_empty_value) else field.valueHex,
            Modifier.weight(1.2f),
            fontFamily = Mono,
            fontSize = 8.sp,
            color = TacGreen,
            lineHeight = 11.sp,
        )
        Text(
            field.decoded,
            Modifier.weight(2.1f),
            fontFamily = Sans,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 11.sp,
        )
    }
    Spacer(Modifier.fillMaxWidth().height(0.5.dp).background(BorderDim.copy(alpha = 0.6f)))
}

// ─── NUS 终端 ────────────────────────────────────────────────────────────────

/**
 * NUS 终端页签
 *
 * 连接参数只显示系统回调值；终端区按 UTF-8 追加显示收到的真实字节；
 * 发送行尾支持 CRLF / CR / LF / RAW，并提供 Arduino Serial.println 风格按钮。
 */
@Composable
private fun NusTab(
    vm: NordicBenchViewModel,
    state: NusState,
    macInput: String,
    onMacInput: (String) -> Unit,
    sendText: String,
    onSendText: (String) -> Unit,
    lineEnding: NusLineEnding,
    onLineEnding: (NusLineEnding) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val terminalScroll = rememberScrollState()

    // 收到新数据后滚到底部；delay 让布局先完成测量
    LaunchedEffect(state.rxBuffer.length) {
        if (state.rxBuffer.isNotEmpty()) {
            delay(50)
            terminalScroll.animateScrollTo(terminalScroll.maxValue)
        }
    }

    TermPanel(stringResource(R.string.nordic_panel_connect)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TermInput(
                stringResource(R.string.nordic_mac_label),
                macInput,
                placeholder = "AA:BB:CC:DD:EE:FF",
                modifier = Modifier.weight(1f),
            ) { onMacInput(it.take(32)) }
            Spacer(Modifier.width(6.dp))
            Surface(
                modifier = Modifier.clickable { onOpenPicker() },
                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                color = Gold.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.55f)),
            ) {
                Text(
                    stringResource(R.string.picker_pick),
                    fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = Gold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ActionButton(
            running = state.active,
            runLabel = stringResource(R.string.nordic_btn_connect),
            stopLabel = stringResource(R.string.nordic_btn_disconnect),
        ) {
            if (state.active) vm.disconnect() else vm.connect(macInput)
        }
        Spacer(Modifier.height(8.dp))
        PanelStat(stringResource(R.string.nordic_stat_status), stringResource(state.phase.labelRes), nusPhaseColor(state.phase))
        PanelStat(stringResource(R.string.nordic_stat_device_name), state.deviceName.ifBlank { "--" })
        PanelStat(stringResource(R.string.nordic_stat_profile), state.profile)
        PanelStat(
            "MTU",
            if (state.mtu > 0) "${state.mtu} B" else "--",
            if (state.mtu > 0) TacGreen else Gray,
        )
        PanelStat("PHY", state.phy)
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(R.string.nordic_mtu_note),
            fontFamily = Sans,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 12.sp,
        )
    }

    state.error?.let { message ->
        Spacer(Modifier.height(8.dp))
        WarningBannerText("[!] $message")
    }

    Spacer(Modifier.height(8.dp))
    TermPanel(stringResource(R.string.nordic_panel_terminal)) {
        LogHeader(
            stringResource(
                R.string.nordic_rx_buffer,
                state.rxBuffer.length,
                NusTerminalManager.MAX_RX_CHARS,
            ),
            showClear = true,
        ) { vm.clearTerminal() }
        Spacer(Modifier.height(5.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = SurfaceAlt,
            border = BorderStroke(1.dp, BorderDim),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(terminalScroll).padding(8.dp)) {
                if (state.rxBuffer.isEmpty()) {
                    Text(
                        stringResource(R.string.nordic_rx_waiting),
                        fontFamily = Sans,
                        fontSize = 9.sp,
                        color = Gray,
                        lineHeight = 13.sp,
                    )
                } else {
                    Text(
                        state.rxBuffer,
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        color = White,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        TermInput(
            stringResource(R.string.nordic_send_label),
            sendText,
            placeholder = stringResource(R.string.nordic_send_placeholder),
        ) {
            onSendText(it.take(4096))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NusLineEnding.values().forEach { ending ->
                SelectChip(lineEnding == ending, stringResource(ending.labelRes)) { onLineEnding(ending) }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GhostButton(stringResource(R.string.nordic_btn_send)) {
                if (vm.send(sendText, lineEnding)) onSendText("")
            }
            GhostButton("Serial.println") {
                if (vm.send(sendText, NusLineEnding.CRLF)) onSendText("")
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(R.string.nordic_send_note),
            fontFamily = Sans,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 12.sp,
        )
        if (state.sending) {
            Text(
                stringResource(R.string.nordic_sending),
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Warn,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    TermPanel(stringResource(R.string.nordic_panel_log)) {
        LogHeader(stringResource(R.string.nordic_log_header), showClear = true) { vm.clearNusLog() }
        Spacer(Modifier.height(4.dp))
        if (state.log.isEmpty()) {
            Text(
                stringResource(R.string.nordic_log_empty),
                fontFamily = Sans,
                fontSize = 9.sp,
                color = Gray,
                lineHeight = 13.sp,
            )
        } else {
            state.log.take(60).forEach { entry ->
                LogLine(
                    timestamp = entry.time,
                    text = (if (entry.isError) "[!] " else "") + entry.text,
                    textColor = if (entry.isError) Danger else White,
                )
            }
            if (state.log.size > 60) {
                Text(
                    stringResource(R.string.nordic_log_limit, state.log.size),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// ─── 绑定管理 ────────────────────────────────────────────────────────────────

/**
 * 绑定管理页签
 *
 * 列表来自系统 bondedDevices；删除前必须经过本地确认。解绑采用反射调用
 * BluetoothDevice.removeBond()，结果以延迟刷新后的真实配对列表为准。
 */
@Composable
private fun BondTab(
    vm: NordicBenchViewModel,
    state: BondState,
    pendingRemove: BondedDevice?,
    onPendingRemove: (BondedDevice?) -> Unit,
) {
    TermPanel(stringResource(R.string.nordic_panel_bonds)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton(
                if (state.loading) stringResource(R.string.nordic_btn_loading)
                else stringResource(R.string.nordic_btn_refresh),
            ) {
                if (!state.loading) vm.refreshBonds()
            }
            Text(
                stringResource(R.string.nordic_device_count, state.devices.size),
                fontFamily = Mono,
                fontSize = 9.sp,
                color = Gray,
            )
        }
        Spacer(Modifier.height(6.dp))

        if (state.devices.isEmpty()) {
            EmptyHint(
                if (state.loading) {
                    stringResource(R.string.nordic_bond_loading_hint)
                } else {
                    stringResource(R.string.nordic_bond_empty)
                },
            )
        } else {
            state.devices.forEach { device ->
                key(device.address) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                device.name,
                                fontFamily = Sans,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = White,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${device.address} · ${device.type}",
                                fontFamily = Mono,
                                fontSize = 8.sp,
                                color = Gray,
                            )
                            Text(
                                stringResource(R.string.nordic_bond_state, device.bondState),
                                fontFamily = Mono,
                                fontSize = 8.sp,
                                color = if (device.bondState == stringResource(R.string.mgr_bond_state_bonded)) TacGreen else Warn,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        GhostButton(
                            if (state.busyAddress == device.address) stringResource(R.string.nordic_btn_unbonding)
                            else stringResource(R.string.nordic_btn_delete),
                        ) {
                            if (state.busyAddress == null) onPendingRemove(device)
                        }
                    }
                    Spacer(
                        Modifier.fillMaxWidth().height(0.5.dp)
                            .background(BorderDim.copy(alpha = 0.6f)),
                    )
                }
            }
        }
    }

    pendingRemove?.let { device ->
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = Warn.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Warn.copy(alpha = 0.55f)),
        ) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text(
                    "[!] " + stringResource(
                        R.string.nordic_unbond_confirm,
                        device.name,
                        device.address,
                    ),
                    fontFamily = Sans,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Warn,
                    lineHeight = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.nordic_unbond_note),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GhostButton(stringResource(R.string.nordic_btn_confirm_unbond)) {
                        vm.removeBond(device.address)
                        onPendingRemove(null)
                    }
                    GhostButton(stringResource(R.string.nordic_btn_cancel)) { onPendingRemove(null) }
                }
            }
        }
    }

    state.message?.let { message ->
        val isWarning = message.startsWith("[!]")
        Spacer(Modifier.height(8.dp))
        WarningBannerText(
            message = message,
            color = if (isWarning) Warn else TacGreen,
            background = if (isWarning) Warn.copy(alpha = 0.08f) else TacGreen.copy(alpha = 0.06f),
        )
    }

    state.error?.let { message ->
        Spacer(Modifier.height(8.dp))
        WarningBannerText("[!] $message")
    }

    Spacer(Modifier.height(8.dp))
    TermPanel(stringResource(R.string.nordic_panel_bonding)) {
        BulletLine(stringResource(R.string.nordic_bond_bullet1))
        BulletLine(stringResource(R.string.nordic_bond_bullet2))
        BulletLine(stringResource(R.string.nordic_bond_bullet3))
        BulletLine(stringResource(R.string.nordic_bond_bullet4))
    }
}

// ─── 通用提示 ────────────────────────────────────────────────────────────────

/** 告警 / 结果横幅 —— 斜切角面板，文字按传入语气色显示 */
@Composable
private fun WarningBannerText(
    message: String,
    color: Color = Danger,
    background: Color = Danger.copy(alpha = 0.08f),
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = background,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(
            message,
            fontFamily = Sans,
            fontSize = 9.sp,
            color = color,
            lineHeight = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
        )
    }
}

/** 说明条目 —— 灰色小字圆点行 */
@Composable
private fun BulletLine(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp)) {
        Text("·", fontFamily = Mono, fontSize = 8.sp, color = Gray)
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            fontFamily = Sans,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 12.sp,
        )
    }
}

/** 页脚能力边界说明 —— 三个模块共用的诚实约束 */
@Composable
private fun HonestyPanel() {
    TermPanel(stringResource(R.string.nordic_panel_honesty)) {
        BulletLine(stringResource(R.string.nordic_honesty1))
        BulletLine(stringResource(R.string.nordic_honesty2))
        BulletLine(stringResource(R.string.nordic_honesty3))
        BulletLine(stringResource(R.string.nordic_honesty4))
        BulletLine(stringResource(R.string.nordic_honesty5))
    }
}

/** 连接阶段 → 状态色 */
private fun nusPhaseColor(phase: NusPhase): Color = when (phase) {
    NusPhase.DISCONNECTED -> Gray
    NusPhase.CONNECTING, NusPhase.DISCOVERING -> Warn
    NusPhase.CONNECTED -> TacGreen
    NusPhase.NO_NUS, NusPhase.ERROR -> Danger
}
