package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
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
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.BleGattPhase
import xin.ctkqiang.deauthctrl.manager.GattCharacteristicInfo
import xin.ctkqiang.deauthctrl.manager.GattServiceInfo
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
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermInput
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.BleGattViewModel
import xin.ctkqiang.deauthctrl.viewmodel.RadarViewModel

/**
 * BLE GATT 工作台 —— 服务枚举 / 读写 / 通知
 *
 * 对标 nRF Connect 的核心闭环：
 * 1. 输入目标 MAC，建立 LE 连接（10 秒超时）；
 * 2. 连接成功后自动发现并枚举 GATT 服务表（10 秒超时）；
 * 3. 展开任意服务查看特征 UUID、属性缩写、描述符与最近一次读到的值；
 * 4. 对特征执行「读」「写（HEX）」「订阅通知」，通知值实时刷新到特征行与日志区。
 *
 * 数据纪律：服务表与值全部来自系统 GATT 回调。未读取过的值显示 "--"，
 * 读取为空显示 "(空)"，设备名读不到显示 "--"，不做任何补全或推测。
 *
 * 诚实边界：目标设备需处于广播 / 可连接状态；部分设备需先配对；
 * 写操作会真实改变设备状态，没有撤销通道。
 */
@Composable
fun BleGattScreen(radarVm: RadarViewModel, back: () -> Unit) {
    val vm: BleGattViewModel = viewModel()
    val state by vm.state.collectAsState()
    var macInput by remember { mutableStateOf("") }
    var expandedServices by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 设备选择器:从雷达扫描结果点选,免手输 MAC
    var showPicker by remember { mutableStateOf(false) }

    // 离开页面主动断开，避免 GATT 连接留在后台
    DisposableEffect(Unit) {
        onDispose { vm.disconnect() }
    }

    // 枚举完成后默认展开全部服务；值刷新不会重置用户的展开 / 折叠操作
    LaunchedEffect(state.deviceAddress, state.services.map { it.uuid }) {
        if (state.services.isNotEmpty()) {
            expandedServices = state.services.map { it.uuid }.toSet()
        }
    }

    val sessionActive = state.phase == BleGattPhase.CONNECTING || state.connected

    Column(Modifier.fillMaxSize().background(Dark).navigationBarsPadding().imePadding()) {
        TerminalHeader(stringResource(R.string.gatt_title), back)

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            // ── 连接控制 ──────────────────────────────────────────────
            TermPanel(stringResource(R.string.gatt_panel_connect)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TermInput(
                        stringResource(R.string.gatt_target_mac_label),
                        macInput,
                        placeholder = stringResource(R.string.gatt_target_mac_placeholder),
                        modifier = Modifier.weight(1f),
                    ) { macInput = it.take(32) }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        modifier = Modifier.clickable { showPicker = true },
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
                Spacer(Modifier.height(6.dp))
                // ── 附近 BLE:雷达扫描结果点选即填,免手输;GATT 直连不配对 ──
                val nearbyBle = remember(radarVm) { radarVm }
                val radarTargets by nearbyBle.targets.collectAsState()
                val bleNearby = radarTargets.filter { it.type == "bt" }.sortedByDescending { it.rssi }.take(4)
                if (bleNearby.isNotEmpty()) {
                    Text(stringResource(R.string.gatt_nearby_label), fontFamily = Mono, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Spacer(Modifier.height(3.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        bleNearby.forEach { t ->
                            Surface(
                                modifier = Modifier.clickable { macInput = t.address },
                                shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
                                color = BLE_BLIP.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, BLE_BLIP.copy(alpha = 0.5f)),
                            ) {
                                Column(Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) {
                                    Text(t.name.take(12).ifBlank { "?" }, fontFamily = Sans, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 1)
                                    Text("${t.address} · ${t.rssi}dBm", fontFamily = Mono, fontSize = 6.5.sp, color = BLE_BLIP)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    running = sessionActive,
                    runLabel = stringResource(R.string.gatt_btn_connect),
                    stopLabel = stringResource(R.string.gatt_btn_disconnect),
                ) {
                    if (sessionActive) vm.disconnect() else vm.connect(macInput)
                }
                Spacer(Modifier.height(8.dp))
                PanelStat(stringResource(R.string.gatt_stat_status), stringResource(state.phase.labelRes), phaseColor(state.phase))
                PanelStat(stringResource(R.string.gatt_stat_device_name), state.deviceName.ifBlank { "--" })
                PanelStat(stringResource(R.string.gatt_stat_device_address), state.deviceAddress.ifBlank { "--" })
                PanelStat(stringResource(R.string.gatt_stat_services_chars), "${state.services.size} / ${state.characteristicCount}")
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.gatt_no_pair_hint),
                    fontFamily = Sans, fontSize = 8.sp, color = TacGreen, lineHeight = 12.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(R.string.gatt_mac_hint),
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 12.sp,
                )
            }

            // ── 错误告警 ──────────────────────────────────────────────
            state.error?.let { message ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Danger.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
                ) {
                    Text(
                        "[!] $message",
                        fontFamily = Sans,
                        fontSize = 9.sp,
                        color = Danger,
                        lineHeight = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                    )
                }
            }

            // ── GATT 服务表 ───────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.gatt_panel_services)) {
                if (state.services.isEmpty()) {
                    EmptyHint(
                        when (state.phase) {
                            BleGattPhase.DISCONNECTED ->
                                stringResource(R.string.gatt_empty_disconnected)
                            BleGattPhase.CONNECTING ->
                                stringResource(R.string.gatt_empty_connecting)
                            BleGattPhase.CONNECTED ->
                                stringResource(R.string.gatt_empty_connected)
                            BleGattPhase.DISCOVERING ->
                                stringResource(R.string.gatt_empty_discovering)
                            BleGattPhase.READY ->
                                stringResource(R.string.gatt_empty_ready)
                            BleGattPhase.ERROR ->
                                stringResource(R.string.gatt_empty_error)
                        },
                    )
                } else {
                    state.services.forEach { service ->
                        key(service.uuid) {
                            ServiceBlock(
                                service = service,
                                expanded = service.uuid in expandedServices,
                                onToggle = {
                                    expandedServices = if (service.uuid in expandedServices) {
                                        expandedServices - service.uuid
                                    } else {
                                        expandedServices + service.uuid
                                    }
                                },
                                vm = vm,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.gatt_property_legend),
                        fontFamily = Sans,
                        fontSize = 8.sp,
                        color = Gray,
                        lineHeight = 12.sp,
                    )
                }
            }

            // ── 日志 ──────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.gatt_panel_log)) {
                LogHeader(stringResource(R.string.gatt_log_header), showClear = true) { vm.clearLog() }
                Spacer(Modifier.height(4.dp))
                if (state.log.isEmpty()) {
                    Text(
                        stringResource(R.string.gatt_log_empty),
                        fontFamily = Sans,
                        fontSize = 9.sp,
                        color = Gray,
                        lineHeight = 13.sp,
                    )
                } else {
                    state.log.take(80).forEach { entry ->
                        LogLine(
                            timestamp = entry.time,
                            text = (if (entry.isError) "[!] " else "") + entry.text,
                            textColor = if (entry.isError) Danger else White,
                        )
                    }
                    if (state.log.size > 80) {
                        Text(
                            stringResource(R.string.gatt_log_truncated, state.log.size),
                            fontFamily = Sans,
                            fontSize = 8.sp,
                            color = Gray,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // ── 操作边界 ──────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.gatt_panel_boundaries)) {
                listOf(
                    stringResource(R.string.gatt_boundary_broadcast),
                    stringResource(R.string.gatt_boundary_pairing),
                    stringResource(R.string.gatt_boundary_write),
                    stringResource(R.string.gatt_boundary_queue),
                    stringResource(R.string.gatt_boundary_raw),
                    stringResource(R.string.gatt_boundary_uuid),
                    stringResource(R.string.gatt_boundary_permission),
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
            Spacer(Modifier.height(10.dp))
        }
    }

    // ── 设备选择器:点选雷达扫描结果,免手输 MAC ──
    if (showPicker) {
        RadarDevicePickerDialog(
            vm = radarVm,
            title = stringResource(R.string.gatt_picker_title),
            typeFilter = "all",
            onPick = { t ->
                macInput = if (t.type == "wifi") t.id else t.address
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

// ─── 服务与特征列表 ──────────────────────────────────────────────────────────

/**
 * 单个服务分组
 *
 * 标题行：展开箭头 + 服务 UUID + 特征数；点击整行展开 / 折叠。
 * 展开后逐个渲染特征块，特征值刷新时只重绘对应行。
 */
@Composable
private fun ServiceBlock(
    service: GattServiceInfo,
    expanded: Boolean,
    onToggle: () -> Unit,
    vm: BleGattViewModel,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .background(if (expanded) SurfaceAlt.copy(alpha = 0.55f) else Color.Transparent)
                .padding(horizontal = 2.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (expanded) "▾" else "▸",
                fontFamily = Mono,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                service.uuid,
                fontFamily = Mono,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (expanded) Gold else White,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.gatt_service_char_count, service.characteristics.size),
                fontFamily = Mono,
                fontSize = 8.sp,
                color = Gray,
            )
        }

        if (expanded) {
            service.characteristics.forEach { characteristic ->
                key(characteristic.uuid) {
                    CharacteristicBlock(service.uuid, characteristic, vm)
                }
            }
        }

        Spacer(Modifier.fillMaxWidth().height(0.5.dp).background(BorderDim))
    }
}

/**
 * 单个特征块
 *
 * 显示 UUID / 属性缩写 / 订阅状态 / 最近一次值（HEX + 文本）/ 描述符，
 * 并按属性提供 [读]、[订阅通知]、[写] 操作。写入框为本地状态，
 * 写入请求被系统接受后才清空。
 */
@Composable
private fun CharacteristicBlock(
    serviceUuid: String,
    characteristic: GattCharacteristicInfo,
    vm: BleGattViewModel,
) {
    var writeHex by remember(serviceUuid, characteristic.uuid) { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 4.dp)) {
        Text(
            characteristic.uuid,
            fontFamily = Mono,
            fontSize = 9.sp,
            color = White,
            lineHeight = 12.sp,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.gatt_char_props, characteristic.propertyShort),
                fontFamily = Mono,
                fontSize = 8.sp,
                color = Gray,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(
                    if (characteristic.subscribed) R.string.gatt_notify_on else R.string.gatt_notify_off,
                ),
                fontFamily = Mono,
                fontSize = 8.sp,
                color = if (characteristic.subscribed) TacGreen else Gray,
            )
        }

        // 值：从未读到显示 "--"，读到空值显示 "(空)"
        val hexValue = when {
            characteristic.valueHex.isNotEmpty() -> characteristic.valueHex
            characteristic.updatedAtMs > 0L -> stringResource(R.string.gatt_value_empty)
            else -> "--"
        }
        Text(
            stringResource(R.string.gatt_value_hex, hexValue),
            fontFamily = Mono,
            fontSize = 8.sp,
            color = if (characteristic.valueHex.isNotEmpty()) TacGreen else Gray,
            lineHeight = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            stringResource(
                R.string.gatt_value_text,
                if (characteristic.valueText.isNotEmpty()) characteristic.valueText else "--",
            ),
            fontFamily = Mono,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 11.sp,
        )
        if (characteristic.descriptors.isNotEmpty()) {
            Text(
                stringResource(R.string.gatt_value_descriptors, characteristic.descriptors.joinToString(", ")),
                fontFamily = Mono,
                fontSize = 8.sp,
                color = Gray,
                lineHeight = 11.sp,
            )
        }

        if (characteristic.readable || characteristic.notifiable) {
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (characteristic.readable) {
                    GhostButton(stringResource(R.string.gatt_btn_read)) { vm.read(serviceUuid, characteristic.uuid) }
                }
                if (characteristic.notifiable) {
                    GhostButton(
                        stringResource(
                            if (characteristic.subscribed) R.string.gatt_btn_unsubscribe else R.string.gatt_btn_subscribe,
                        ),
                    ) {
                        vm.setNotify(serviceUuid, characteristic.uuid, !characteristic.subscribed)
                    }
                }
            }
        }

        if (characteristic.writable) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                TermInput(
                    stringResource(R.string.gatt_write_label),
                    writeHex,
                    placeholder = stringResource(R.string.gatt_write_placeholder),
                    modifier = Modifier.weight(1f),
                ) { writeHex = it.take(1024) }
                Spacer(Modifier.width(6.dp))
                GhostButton(stringResource(R.string.gatt_btn_write)) {
                    if (vm.write(serviceUuid, characteristic.uuid, writeHex)) {
                        writeHex = ""
                    }
                }
            }
            Text(
                stringResource(R.string.gatt_write_warning),
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Warn,
                lineHeight = 11.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        Spacer(Modifier.height(3.dp))
        Spacer(Modifier.fillMaxWidth().height(0.5.dp).background(BorderDim.copy(alpha = 0.6f)))
    }
}

// ─── 状态配色 ────────────────────────────────────────────────────────────────

/** 连接阶段 → 状态色：断开灰 / 中间态琥珀 / 就绪电光青 / 错误红 */
private fun phaseColor(phase: BleGattPhase): Color = when (phase) {
    BleGattPhase.DISCONNECTED -> Gray
    BleGattPhase.CONNECTING, BleGattPhase.DISCOVERING -> Warn
    BleGattPhase.CONNECTED, BleGattPhase.READY -> TacGreen
    BleGattPhase.ERROR -> Danger
}
