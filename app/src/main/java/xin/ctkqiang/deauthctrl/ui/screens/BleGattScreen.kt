package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
fun BleGattScreen(back: () -> Unit) {
    val vm: BleGattViewModel = viewModel()
    val state by vm.state.collectAsState()
    var macInput by remember { mutableStateOf("") }
    var expandedServices by remember { mutableStateOf<Set<String>>(emptySet()) }

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
        TerminalHeader("BLE GATT 工作台", back)

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            // ── 连接控制 ──────────────────────────────────────────────
            TermPanel("连接 · GATT CLIENT") {
                TermInput(
                    "目标 MAC 地址",
                    macInput,
                    placeholder = "AA:BB:CC:DD:EE:FF",
                ) { macInput = it.take(32) }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    running = sessionActive,
                    runLabel = "连接",
                    stopLabel = "断开",
                ) {
                    if (sessionActive) vm.disconnect() else vm.connect(macInput)
                }
                Spacer(Modifier.height(8.dp))
                PanelStat("状态", state.phase.label, phaseColor(state.phase))
                PanelStat("设备名称", state.deviceName.ifBlank { "--" })
                PanelStat("设备地址", state.deviceAddress.ifBlank { "--" })
                PanelStat("服务 / 特征", "${state.services.size} / ${state.characteristicCount}")
                Spacer(Modifier.height(6.dp))
                Text(
                    "MAC 可从「BLE 扫描」页面获取；目标设备必须处于广播 / 可连接状态，" +
                        "系统不会替我们探测一个不存在或不可连接的地址。",
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
            TermPanel("GATT 服务 · SERVICES") {
                if (state.services.isEmpty()) {
                    EmptyHint(
                        when (state.phase) {
                            BleGattPhase.DISCONNECTED ->
                                "未连接 —— 输入目标 MAC 后点击「连接」；目标需处于广播 / 可连接状态"
                            BleGattPhase.CONNECTING ->
                                "连接中 —— 最多等待 10 秒"
                            BleGattPhase.CONNECTED ->
                                "已连接 —— 正在准备服务发现"
                            BleGattPhase.DISCOVERING ->
                                "正在发现服务 —— 最多等待 10 秒"
                            BleGattPhase.READY ->
                                "设备未暴露任何可访问的 GATT 服务 —— 尝试在系统蓝牙中配对后重连"
                            BleGattPhase.ERROR ->
                                "服务表不可用 —— 处理上方告警后重新连接"
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
                        "属性缩写：R 读 · W 有响应写 · WNR 无响应写 · N 通知 · I 指示 · " +
                            "BC 广播 · SW 签名写 · EP 扩展属性。",
                        fontFamily = Sans,
                        fontSize = 8.sp,
                        color = Gray,
                        lineHeight = 12.sp,
                    )
                }
            }

            // ── 日志 ──────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel("通知日志 · GATT LOG") {
                LogHeader("事件流（最新在上）", showClear = true) { vm.clearLog() }
                Spacer(Modifier.height(4.dp))
                if (state.log.isEmpty()) {
                    Text(
                        "暂无日志 —— 连接、读取、写入、通知事件都会记录在这里",
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
                            "仅显示最近 80 条，共 ${state.log.size} 条",
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
            TermPanel("操作边界 · 说明") {
                listOf(
                    "目标设备必须处于广播 / 可连接状态；已被其他主机保持连接的设备通常会拒绝新的 GATT 连接。",
                    "部分设备在读取加密特征前要求先在系统蓝牙中完成配对；未配对时可能返回认证不足（status=5）。",
                    "写操作会真实改变设备状态，可能触发执行机构；本页不提供撤销，确认特征语义后再写入。",
                    "读取 / 写入 / 订阅共用一条 GATT 请求队列，同一时刻只允许一个请求；上一操作未返回时新请求会被拒绝。",
                    "特征值全部来自系统回调的原始字节：未读取过显示 \"--\"，读取为空显示 \"(空)\"，文本仅在可解码为 UTF-8 时展示。",
                    "标准 UUID 之外不做语义推测：日志里的十六进制是设备原样数据，不代表已解析出业务含义。",
                    "Android 12+ 连接需要「附近的设备」权限（BLUETOOTH_CONNECT），申请由应用权限网关统一处理。",
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
                "${service.characteristics.size} 特征",
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
                "属性 ${characteristic.propertyShort}",
                fontFamily = Mono,
                fontSize = 8.sp,
                color = Gray,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (characteristic.subscribed) "通知已开启" else "通知未开启",
                fontFamily = Mono,
                fontSize = 8.sp,
                color = if (characteristic.subscribed) TacGreen else Gray,
            )
        }

        // 值：从未读到显示 "--"，读到空值显示 "(空)"
        val hexValue = when {
            characteristic.valueHex.isNotEmpty() -> characteristic.valueHex
            characteristic.updatedAtMs > 0L -> "(空)"
            else -> "--"
        }
        Text(
            "值 $hexValue",
            fontFamily = Mono,
            fontSize = 8.sp,
            color = if (characteristic.valueHex.isNotEmpty()) TacGreen else Gray,
            lineHeight = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            "文本 ${if (characteristic.valueText.isNotEmpty()) characteristic.valueText else "--"}",
            fontFamily = Mono,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 11.sp,
        )
        if (characteristic.descriptors.isNotEmpty()) {
            Text(
                "描述符 ${characteristic.descriptors.joinToString(", ")}",
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
                    GhostButton("读") { vm.read(serviceUuid, characteristic.uuid) }
                }
                if (characteristic.notifiable) {
                    GhostButton(if (characteristic.subscribed) "取消订阅" else "订阅通知") {
                        vm.setNotify(serviceUuid, characteristic.uuid, !characteristic.subscribed)
                    }
                }
            }
        }

        if (characteristic.writable) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                TermInput(
                    "写入 (HEX)",
                    writeHex,
                    placeholder = "例如 01 A0 FF",
                    modifier = Modifier.weight(1f),
                ) { writeHex = it.take(1024) }
                Spacer(Modifier.width(6.dp))
                GhostButton("写") {
                    if (vm.write(serviceUuid, characteristic.uuid, writeHex)) {
                        writeHex = ""
                    }
                }
            }
            Text(
                "[!] 写操作会真实改变目标设备状态，没有撤销；确认特征语义后再写入。",
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
