package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
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
fun NordicBenchScreen(back: () -> Unit) {
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

    // 绑定页签的待确认删除目标
    var pendingRemove by remember { mutableStateOf<BondedDevice?>(null) }

    // 离开页面主动断开，避免 GATT 连接留在后台
    DisposableEffect(Unit) {
        onDispose { vm.disconnect() }
    }

    // 进入绑定页签时重新读取系统配对列表
    LaunchedEffect(tab) {
        if (tab == 2) vm.refreshBonds()
    }

    Column(Modifier.fillMaxSize().background(Dark).navigationBarsPadding().imePadding()) {
        TerminalHeader("Nordic 工作台", back)

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(tab == 0, "广告解析") { tab = 0 }
                SelectChip(tab == 1, "NUS 终端") { tab = 1 }
                SelectChip(tab == 2, "绑定管理") { tab = 2 }
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
                            adError = "请输入广播数据的十六进制字节"
                            adParsed = true
                        } else {
                            val result = vm.decodeAdvertising(input)
                            when {
                                result == null -> {
                                    adFields = emptyList()
                                    adError = "十六进制格式无效 —— 字节须为偶数个十六进制字符，" +
                                        "可用空格 / 冒号 / 短横线分隔"
                                }
                                result.isEmpty() -> {
                                    adFields = emptyList()
                                    adError = "AD 数据为空或首字节长度为 0 —— 没有可解析的字段"
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
    TermPanel("广播数据 · AD STRUCTURE") {
        TermInput(
            "AD 数据（十六进制）",
            hexInput,
            placeholder = "例如 02 01 06 03 03 0F 18 0B 09 44 65 61 75 74 68",
        ) { onHexInput(it) }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GhostButton("解析") { onParse() }
            GhostButton("清空") { onClear() }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "格式：[长度 1B][类型 1B][数据 …]，长度字节包含类型字节本身；" +
                "长度 0x00 表示广播数据结束。长度越界时按实际剩余字节截断并标注。",
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
    TermPanel("解析结果 · ${fields.size} 个字段") {
        if (fields.isEmpty()) {
            EmptyHint(
                if (parsedOnce) {
                    "没有可展示的字段 —— 修正上方输入后重新解析"
                } else {
                    "粘贴一段真实广播数据后点击「解析」；本页不生成示例数据"
                },
            )
        } else {
            AdFieldHeader()
            fields.forEachIndexed { index, field -> AdFieldRow(index, field) }
            Spacer(Modifier.height(6.dp))
            Text(
                "128-bit UUID 按小端反序还原为标准 UUID；公司 ID / 服务 UUID 同样按小端解析。" +
                    "未收录的编号保留原始十六进制，不做猜测。",
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Gray,
                lineHeight = 12.sp,
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    TermPanel("接入说明 · SCOPE") {
        Text(
            "雷达 / BLE 扫描页的广播详情与本页共用同一个 AdStructureDecoder" +
                "（主线程负责把解码器接进扫描页详情），两处按相同的 AD Structure 规则解析，结果一致。",
            fontFamily = Sans,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "解析覆盖：Flags、16 / 32 / 128-bit UUID 列表、本地名、发射功率、" +
                "Class of Device、连接间隔范围、Service Data、Appearance、广播间隔、" +
                "LE 设备地址、LE Role、URI 与厂商自定义数据。未收录类型显示「未知 AD 类型」与原始十六进制。",
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
        AdHeaderCell("名称", 1.05f)
        AdHeaderCell("类型", 0.5f)
        AdHeaderCell("原始值", 1.2f)
        AdHeaderCell("解码说明", 2.1f)
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
            field.valueHex.ifEmpty { "(空)" },
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
) {
    val terminalScroll = rememberScrollState()

    // 收到新数据后滚到底部；delay 让布局先完成测量
    LaunchedEffect(state.rxBuffer.length) {
        if (state.rxBuffer.isNotEmpty()) {
            delay(50)
            terminalScroll.animateScrollTo(terminalScroll.maxValue)
        }
    }

    TermPanel("连接 · NORDIC UART SERVICE") {
        TermInput("目标 MAC 地址", macInput, placeholder = "AA:BB:CC:DD:EE:FF") {
            onMacInput(it.take(32))
        }
        Spacer(Modifier.height(8.dp))
        ActionButton(running = state.active, runLabel = "连接", stopLabel = "断开") {
            if (state.active) vm.disconnect() else vm.connect(macInput)
        }
        Spacer(Modifier.height(8.dp))
        PanelStat("状态", state.phase.label, nusPhaseColor(state.phase))
        PanelStat("设备名称", state.deviceName.ifBlank { "--" })
        PanelStat("服务配置", state.profile)
        PanelStat(
            "MTU",
            if (state.mtu > 0) "${state.mtu} B" else "--",
            if (state.mtu > 0) TacGreen else Gray,
        )
        PanelStat("PHY", state.phy)
        Spacer(Modifier.height(5.dp))
        Text(
            "连接后请求 MTU 247，实际值以系统 onMtuChanged 回调为准；" +
                "Android 8.0 以下不支持读取 PHY。NUS 服务为 6E400001，兼容 FFE0 / FFE1 串口。",
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
    TermPanel("终端 · RX BUFFER") {
        LogHeader(
            "接收缓冲（${state.rxBuffer.length}/${NusTerminalManager.MAX_RX_CHARS} 字符）",
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
                        "等待设备数据 —— TX 通知的 UTF-8 文本会追加到这里",
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
        TermInput("发送内容（UTF-8）", sendText, placeholder = "例如 AT+VERSION?") {
            onSendText(it.take(4096))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NusLineEnding.values().forEach { ending ->
                SelectChip(lineEnding == ending, ending.label) { onLineEnding(ending) }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GhostButton("发送") {
                if (vm.send(sendText, lineEnding)) onSendText("")
            }
            GhostButton("Serial.println") {
                if (vm.send(sendText, NusLineEnding.CRLF)) onSendText("")
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "发送写入 RX 特征；Serial.println 等价于在文本后追加 CRLF。" +
                "单次写入不得超过 MTU-3 字节，超长会明确拒绝而不是静默截断。",
            fontFamily = Sans,
            fontSize = 8.sp,
            color = Gray,
            lineHeight = 12.sp,
        )
        if (state.sending) {
            Text(
                "发送中 —— 等待 onCharacteristicWrite 回调",
                fontFamily = Sans,
                fontSize = 8.sp,
                color = Warn,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    TermPanel("连接日志 · NUS LOG") {
        LogHeader("事件流（最新在上）", showClear = true) { vm.clearNusLog() }
        Spacer(Modifier.height(4.dp))
        if (state.log.isEmpty()) {
            Text(
                "暂无日志 —— 连接、服务发现、CCCD 订阅、收发与错误事件都会记录在这里",
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
                    "仅显示最近 60 条，共 ${state.log.size} 条",
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
    TermPanel("已配对设备 · BONDED DEVICES") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton(if (state.loading) "读取中…" else "刷新列表") {
                if (!state.loading) vm.refreshBonds()
            }
            Text(
                "${state.devices.size} 台",
                fontFamily = Mono,
                fontSize = 9.sp,
                color = Gray,
            )
        }
        Spacer(Modifier.height(6.dp))

        if (state.devices.isEmpty()) {
            EmptyHint(
                if (state.loading) {
                    "正在读取系统配对列表"
                } else {
                    "没有已配对设备 —— 先在系统蓝牙中完成配对后刷新"
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
                                "状态 ${device.bondState}",
                                fontFamily = Mono,
                                fontSize = 8.sp,
                                color = if (device.bondState == "已配对") TacGreen else Warn,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        GhostButton(
                            if (state.busyAddress == device.address) "解绑中…" else "删除",
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
                    "[!] 确认解绑 ${device.name}（${device.address}）？",
                    fontFamily = Sans,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Warn,
                    lineHeight = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "程序化解绑依赖 BluetoothDevice.removeBond() 隐藏方法，" +
                        "Android 9+ 可能被系统拒绝；解绑后需要重新配对。",
                    fontFamily = Sans,
                    fontSize = 8.sp,
                    color = Gray,
                    lineHeight = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GhostButton("确认解绑") {
                        vm.removeBond(device.address)
                        onPendingRemove(null)
                    }
                    GhostButton("取消") { onPendingRemove(null) }
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
    TermPanel("说明 · BONDING") {
        BulletLine("删除按钮只对系统已配对设备开放；列表为空时说明当前没有配对记录。")
        BulletLine("解绑调用返回 true 仅代表系统接受了请求，最终以配对列表是否移除为准。")
        BulletLine("若系统限制隐藏 API 或厂商 ROM 不提供 removeBond，请到系统蓝牙设置中忽略该设备。")
        BulletLine("重新配对需要在系统蓝牙弹窗中确认；本页不提供静默配对。")
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
    TermPanel("能力边界 · HONESTY") {
        BulletLine("广告解析只解码输入的真实字节；字段含义来自 Bluetooth Assigned Numbers，不推测业务语义。")
        BulletLine("NUS 是 Nordic 官方演示服务，仅对烧录了 NUS 固件（或 FFE0 / FFE1 串口固件）的设备有效。")
        BulletLine("MTU / PHY 为连接后协商值，设备可以拒绝请求或回落到更低档位。")
        BulletLine("绑定管理依赖隐藏方法 removeBond()；系统拒绝时请到系统蓝牙设置中忽略设备，本页不会假装已删除。")
        BulletLine("蓝牙连接与配对需要「附近的设备」权限，申请由应用权限网关统一处理。")
    }
}

/** 连接阶段 → 状态色 */
private fun nusPhaseColor(phase: NusPhase): Color = when (phase) {
    NusPhase.DISCONNECTED -> Gray
    NusPhase.CONNECTING, NusPhase.DISCOVERING -> Warn
    NusPhase.CONNECTED -> TacGreen
    NusPhase.NO_NUS, NusPhase.ERROR -> Danger
}
