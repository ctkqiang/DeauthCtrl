package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * NUS 终端连接阶段
 *
 * 与 nRF Connect 的 NUS 面板一致：断开 → 连接中 → 发现服务 → 已连接；
 * 目标不提供 NUS / FFE0 服务时停在「无 NUS 服务」，配置失败停在「错误」。
 */
enum class NusPhase(val label: String) {
    DISCONNECTED("断开"),
    CONNECTING("连接中"),
    DISCOVERING("发现服务"),
    CONNECTED("已连接"),
    NO_NUS("无 NUS 服务"),
    ERROR("错误"),
}

/**
 * 发送行尾
 *
 * RAW 不追加任何字节；其余选项对应 Arduino `Serial.print` / `Serial.println` 的常见写法。
 */
enum class NusLineEnding(val label: String, val bytes: ByteArray) {
    CRLF("CRLF", byteArrayOf(0x0D, 0x0A)),
    CR("CR", byteArrayOf(0x0D)),
    LF("LF", byteArrayOf(0x0A)),
    RAW("RAW", ByteArray(0)),
}

/**
 * NUS 事件日志
 *
 * @property time 本机本地时间 HH:mm:ss
 * @property text 事件描述（中文）
 * @property isError 失败 / 告警行，界面用 Danger 色显示
 */
data class NusLogEntry(
    val time: String,
    val text: String,
    val isError: Boolean = false,
)

/**
 * NUS 终端页面状态
 *
 * @property phase 当前连接阶段
 * @property deviceName 系统返回的设备名；读不到时为 "--"，绝不编造
 * @property deviceAddress 规范化后的目标 MAC
 * @property profile 命中的服务配置：NUS 6E400001 或 FFE0 / FFE1 串口
 * @property mtu 协商后的实际 ATT MTU；0 表示尚未连接或未知
 * @property phy 实际 PHY（API 26+ 读取）；"--" 表示未知或平台不支持
 * @property rxBuffer 终端接收缓冲，按 UTF-8 文本追加，最多 [NusTerminalManager.MAX_RX_CHARS] 字符
 * @property log 事件日志，最新在前，最多 [NusTerminalManager.MAX_LOG_ENTRIES] 条
 * @property sending 当前是否有写入尚未回调
 * @property error 最近一次错误（中文），成功操作或重新连接后清空
 */
data class NusState(
    val phase: NusPhase = NusPhase.DISCONNECTED,
    val deviceName: String = "--",
    val deviceAddress: String = "",
    val profile: String = "--",
    val mtu: Int = 0,
    val phy: String = "--",
    val rxBuffer: String = "",
    val log: List<NusLogEntry> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
) {
    /** 是否处于连接会话中（含连接与发现阶段） */
    val active: Boolean
        get() = phase == NusPhase.CONNECTING ||
            phase == NusPhase.DISCOVERING ||
            phase == NusPhase.CONNECTED
}

/**
 * Nordic UART Service（NUS）终端管理器
 *
 * 对标 nRF Connect 的 UART 终端：
 * 连接指定 MAC → 发现 GATT 服务 → 命中 NUS 或 FFE0 / FFE1 → 开启 TX 通知
 * → 接收字节按 UTF-8 追加到终端缓冲；发送文本写入 RX 特征。
 *
 * ## 服务兼容
 * - Nordic NUS：服务 6E400001-B5A3-F393-E0A9-E50E24DCCA9E，
 *   RX 6E400002（手机 → 设备，写），TX 6E400003（设备 → 手机，通知）；
 * - 旧式串口：服务 0000FFE0-0000-1000-8000-00805F9B34FB，特征 FFE1
 *   同时承担 RX / TX（HM-10 一类模块常见）。
 *
 * ## 连接参数
 * - 连接后请求 MTU 247，以 onMtuChanged 的实际值为准；请求被拒绝时保持默认 23；
 * - Android 8.0（API 26）以上调用 readPhy()，由 onPhyRead 回调记录 1M / 2M / Coded。
 *
 * ## 超时
 * - 连接 10 秒 / 服务发现 10 秒 / CCCD 订阅 10 秒；
 * - 写入看门狗 5 秒，避免 sending 永远为 true。
 *
 * ## 数据纪律
 * - 设备名读不到显示 "--"；MTU / PHY 只显示系统回调的真实值；
 * - 收到多少字节就展示多少，不生成、不补全、不模拟回显；
 * - 单次写入超过 MTU-3 时明确拒绝，不静默截断。
 *
 * 明确局限：NUS 是 Nordic 官方示例服务，只对烧录了 NUS 固件（或 FFE0 / FFE1
 * 串口固件）的设备有效；MTU / PHY 都是协商值，设备可以拒绝或回落到更低档位。
 */
@Suppress("MissingPermission", "DEPRECATION", "NewApi")
class NusTerminalManager(private val context: Context) {

    private val _state = MutableStateFlow(NusState())
    val state: StateFlow<NusState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val rxLock = Any()

    @Volatile
    private var gattRef: BluetoothGatt? = null

    @Volatile
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    /** 上一帧末尾未完成的 UTF-8 多字节序列 */
    @Volatile
    private var utf8Remainder: ByteArray = ByteArray(0)

    @Volatile
    private var pendingSubscribe = false

    private var connectTimeoutJob: Job? = null
    private var discoverTimeoutJob: Job? = null
    private var subscribeTimeoutJob: Job? = null
    private var writeTimeoutJob: Job? = null

    // ══════════════════════════════════════════════════════════════════
    // 连接 / 断开
    // ══════════════════════════════════════════════════════════════════

    /**
     * 连接目标设备
     *
     * @return false 表示 MAC / 权限 / 蓝牙开关校验失败或 connectGatt 调用失败，
     *   原因在 state.error 与日志中
     */
    fun connect(mac: String): Boolean {
        val normalized = normalizeNusMac(mac)
        if (normalized == null) {
            publishError("MAC 格式无效 —— 应为 AA:BB:CC:DD:EE:FF（12 位十六进制）")
            return false
        }

        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            publishError("缺少蓝牙权限：${missing.joinToString("、") { permissionLabel(it) }} —— 请先在应用权限网关授予")
            return false
        }

        val adapter = adapterOrNull()
        if (adapter == null) {
            publishError("本机没有蓝牙适配器")
            return false
        }
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) {
            publishError("蓝牙未开启 —— 请先在系统设置中打开蓝牙")
            return false
        }

        closeGattInternal()

        val device = runCatching { adapter.getRemoteDevice(normalized) }.getOrNull()
        if (device == null) {
            publishError("系统无法解析该 MAC 地址")
            return false
        }

        val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "--"
        val keptLog = _state.value.log
        val keptBuffer = _state.value.rxBuffer
        _state.value = NusState(
            phase = NusPhase.CONNECTING,
            deviceName = name,
            deviceAddress = normalized,
            profile = "--",
            mtu = 0,
            phy = "--",
            rxBuffer = keptBuffer,
            log = keptLog,
            sending = false,
            error = null,
        )
        synchronized(rxLock) { utf8Remainder = ByteArray(0) }
        log("开始连接 $normalized（$name），LE 传输，10 秒超时")

        connectTimeoutJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_state.value.phase == NusPhase.CONNECTING) {
                connectTimeoutJob = null
                failSession("连接超时（10 秒）—— 目标可能不在范围内、未处于可连接状态，或已被其他主机连接")
            }
        }

        val connection = runCatching {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrElse { throwable ->
            connectTimeoutJob?.cancel()
            connectTimeoutJob = null
            publishFatal(connectFailureText(throwable))
            return false
        }
        if (connection == null) {
            connectTimeoutJob?.cancel()
            connectTimeoutJob = null
            publishFatal("连接失败 —— 系统返回 null，请关闭蓝牙后重新打开再试")
            return false
        }
        gattRef = connection
        return true
    }

    /** 主动断开；保留接收缓冲与日志，清空连接参数与错误 */
    fun disconnect() {
        val hadSession = gattRef != null || _state.value.phase != NusPhase.DISCONNECTED
        closeGattInternal()
        _state.update {
            it.copy(
                phase = NusPhase.DISCONNECTED,
                mtu = 0,
                phy = "--",
                sending = false,
                error = null,
            )
        }
        synchronized(rxLock) { utf8Remainder = ByteArray(0) }
        if (hadSession) log("已主动断开连接")
    }

    /** ViewModel 回收时调用：断开并取消所有后台协程 */
    fun destroy() {
        closeGattInternal()
        scope.cancel()
    }

    /** 清空终端接收缓冲与未完成的 UTF-8 片段 */
    fun clearTerminal() {
        synchronized(rxLock) {
            utf8Remainder = ByteArray(0)
            _state.update { it.copy(rxBuffer = "") }
        }
    }

    /** 清空事件日志（连接状态与缓冲不变） */
    fun clearLog() {
        _state.update { it.copy(log = emptyList()) }
    }

    // ══════════════════════════════════════════════════════════════════
    // 发送
    // ══════════════════════════════════════════════════════════════════

    /**
     * 向 RX 特征写入文本
     *
     * @param text 待发送文本（UTF-8 编码）
     * @param ending 行尾字节，模拟 Arduino Serial.println 等风格
     * @return true 表示请求已被系统接受（不等于远端已处理）
     */
    fun send(text: String, ending: NusLineEnding): Boolean {
        val gatt = gattRef
        if (gatt == null || _state.value.phase != NusPhase.CONNECTED) {
            publishError("未连接 —— 请先连接设备并等待 NUS 终端就绪")
            return false
        }
        val rx = rxCharacteristic
        if (rx == null) {
            publishError("RX 特征不可用 —— 请断开后重新连接")
            return false
        }
        if (_state.value.sending) {
            publishError("上一帧尚未写入完成 —— 请稍候再发送")
            return false
        }

        val payload = text.toByteArray(Charsets.UTF_8) + ending.bytes
        if (payload.isEmpty()) {
            publishError("发送内容为空 —— 请输入文本或选择行尾")
            return false
        }
        val mtu = _state.value.mtu.takeIf { it >= 23 } ?: 23
        val maxPayload = mtu - 3
        if (payload.size > maxPayload) {
            publishError("发送 ${payload.size}B 超过当前 MTU $mtu 允许的单次写入 $maxPayload B —— 请缩短内容")
            return false
        }

        val writeType = if (rx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        _state.update { it.copy(sending = true, error = null) }

        // API 33+ 使用带 value 的新重载（返回 BluetoothStatusCodes，0 = SUCCESS）；
        // 旧系统仍走 characteristic.value + 单参重载。
        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                gatt.writeCharacteristic(rx, payload, writeType) == GATT_STATUS_SUCCESS
            }.getOrDefault(false)
        } else {
            runCatching {
                rx.writeType = writeType
                rx.value = payload
                gatt.writeCharacteristic(rx)
            }.getOrDefault(false)
        }

        if (!accepted) {
            _state.update { it.copy(sending = false) }
            publishError("写入请求被系统拒绝 —— 检查连接状态后重试")
            return false
        }

        log("已发送 ${payload.size}B → RX ${shortUuid(rx.uuid)}（${ending.label}）")
        startWriteTimeout()
        return true
    }

    // ══════════════════════════════════════════════════════════════════
    // GATT 回调
    // ══════════════════════════════════════════════════════════════════

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== gattRef) {
                // 已被替换 / 主动关闭的连接，迟到的回调直接回收
                runCatching { gatt.close() }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        failSession("连接失败（status=$status）${gattStatusHint(status)}")
                        return
                    }
                    connectTimeoutJob?.cancel()
                    connectTimeoutJob = null
                    log("已连接 ${_state.value.deviceAddress}，开始发现 GATT 服务")
                    _state.update {
                        it.copy(phase = NusPhase.DISCOVERING, mtu = 23, error = null)
                    }

                    val started = runCatching { gatt.discoverServices() }.getOrDefault(false)
                    if (!started) {
                        failSession("服务发现启动失败 —— discoverServices() 返回 false")
                        return
                    }
                    startDiscoverTimeout()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    cleanupJobs()
                    gattRef = null
                    rxCharacteristic = null
                    txCharacteristic = null
                    pendingSubscribe = false
                    runCatching { gatt.close() }
                    synchronized(rxLock) { utf8Remainder = ByteArray(0) }

                    _state.update {
                        it.copy(
                            phase = NusPhase.DISCONNECTED,
                            mtu = 0,
                            phy = "--",
                            sending = false,
                            error = if (status != BluetoothGatt.GATT_SUCCESS) {
                                "连接已断开（status=$status）${gattStatusHint(status)}"
                            } else {
                                null
                            },
                        )
                    }
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        log("设备已断开连接")
                    } else {
                        log("连接已断开（status=$status）${gattStatusHint(status)}", isError = true)
                    }
                }

                // STATE_CONNECTING 等中间状态不改变页面阶段
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== gattRef) return
            discoverTimeoutJob?.cancel()
            discoverTimeoutJob = null

            if (status != BluetoothGatt.GATT_SUCCESS) {
                failSession("服务发现失败（status=$status）${gattStatusHint(status)}")
                return
            }

            val nusService = runCatching { gatt.getService(NUS_SERVICE_UUID) }.getOrNull()
            val ffe0Service = runCatching { gatt.getService(FFE0_SERVICE_UUID) }.getOrNull()
            val service = nusService ?: ffe0Service
            if (service == null) {
                failSession(
                    "未发现 Nordic UART Service（6E400001-…）或 FFE0 / FFE1 串口服务 —— " +
                        "目标设备可能未烧录 NUS 固件",
                    NusPhase.NO_NUS,
                )
                return
            }

            val isFfe0 = service.uuid == FFE0_SERVICE_UUID
            var rx = if (isFfe0) {
                service.getCharacteristic(FFE1_CHARACTERISTIC_UUID)
            } else {
                service.getCharacteristic(NUS_RX_UUID)
            }
            var tx = if (isFfe0) {
                service.getCharacteristic(FFE1_CHARACTERISTIC_UUID)
            } else {
                service.getCharacteristic(NUS_TX_UUID)
            }
            // 双兼容兜底：个别固件把 FFE1 放在 6E400001 服务里
            if (rx == null) rx = service.getCharacteristic(FFE1_CHARACTERISTIC_UUID)
            if (tx == null) tx = service.getCharacteristic(FFE1_CHARACTERISTIC_UUID)

            val profile = if (isFfe0) "FFE0 / FFE1 串口" else "NUS 6E400001"
            if (rx == null) {
                failSession("找到 $profile 服务，但缺少可写 RX 特征")
                return
            }
            if (tx == null) {
                failSession("找到 $profile 服务，但缺少通知 TX 特征")
                return
            }

            val canWrite = rx.properties and (
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                ) != 0
            if (!canWrite) {
                failSession("RX 特征 ${shortUuid(rx.uuid)} 不支持写入（属性 ${propertyShort(rx.properties)}）")
                return
            }
            val canNotify = tx.properties and (
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_INDICATE
                ) != 0
            if (!canNotify) {
                failSession("TX 特征 ${shortUuid(tx.uuid)} 不支持通知 / 指示（属性 ${propertyShort(tx.properties)}）")
                return
            }

            rxCharacteristic = rx
            txCharacteristic = tx
            _state.update { it.copy(profile = profile) }

            val localRegistered = runCatching {
                gatt.setCharacteristicNotification(tx, true)
            }.getOrDefault(false)
            if (!localRegistered) {
                failSession("本地通知注册失败 —— 系统拒绝了 setCharacteristicNotification")
                return
            }

            val descriptor = runCatching { tx.getDescriptor(CCCD_UUID) }.getOrNull()
            if (descriptor == null) {
                runCatching { gatt.setCharacteristicNotification(tx, false) }
                failSession("TX 特征缺少 CCCD 描述符（0x2902）—— 无法开启远端通知")
                return
            }

            val useIndication = (tx.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) &&
                (tx.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
            val cccdValue = if (useIndication) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }

            pendingSubscribe = true
            val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching {
                    gatt.writeDescriptor(descriptor, cccdValue) == GATT_STATUS_SUCCESS
                }.getOrDefault(false)
            } else {
                runCatching {
                    descriptor.value = cccdValue
                    gatt.writeDescriptor(descriptor)
                }.getOrDefault(false)
            }

            if (!accepted) {
                pendingSubscribe = false
                runCatching { gatt.setCharacteristicNotification(tx, false) }
                failSession("通知订阅请求被系统拒绝 —— 请稍后重试")
                return
            }

            log("已找到 $profile：RX ${shortUuid(rx.uuid)} / TX ${shortUuid(tx.uuid)}，等待 CCCD 写入回调")
            startSubscribeTimeout()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (gatt !== gattRef) return
            if (!pendingSubscribe) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    publishError("描述符写入失败（status=$status）${gattStatusHint(status)}")
                }
                return
            }
            pendingSubscribe = false
            subscribeTimeoutJob?.cancel()
            subscribeTimeoutJob = null

            if (status != BluetoothGatt.GATT_SUCCESS) {
                failSession("开启通知失败（status=$status）${gattStatusHint(status)}")
                return
            }

            _state.update { it.copy(phase = NusPhase.CONNECTED, error = null) }
            log("通知已开启，NUS 终端就绪")
            requestConnectionParameters(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (gatt !== gattRef) return
            if (!isTxCharacteristic(characteristic)) return
            appendIncoming(runCatching { characteristic.value }.getOrNull() ?: ByteArray(0))
        }

        @Suppress("NewApi")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (gatt !== gattRef) return
            if (!isTxCharacteristic(characteristic)) return
            appendIncoming(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (gatt !== gattRef) return
            writeTimeoutJob?.cancel()
            writeTimeoutJob = null
            val wasSending = _state.value.sending
            _state.update { it.copy(sending = false) }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                publishError("发送失败（status=$status）${gattStatusHint(status)}")
            } else if (wasSending) {
                log("已写入 ${shortUuid(characteristic.uuid)}")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (gatt !== gattRef) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(mtu = mtu, error = null) }
                log("MTU 协商完成：$mtu B（单次可写负载 ${mtu - 3} B）")
            } else {
                _state.update { it.copy(mtu = 23) }
                publishError("MTU 协商失败（status=$status）—— 继续使用默认 23 B")
            }
        }

        override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            if (gatt !== gattRef) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val text = "TX ${phyName(txPhy)} / RX ${phyName(rxPhy)}"
                _state.update { it.copy(phy = text) }
                log("PHY 读取完成：$text")
            } else {
                _state.update { it.copy(phy = "--") }
                publishError("PHY 读取失败（status=$status）")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 接收缓冲
    // ══════════════════════════════════════════════════════════════════

    /**
     * 追加通知字节到终端缓冲
     *
     * 通知分片可能把 UTF-8 多字节字符切成两半：末尾不完整的前导序列会留到下一帧，
     * 避免把一个汉字解码成替换字符。缓冲超过 [MAX_RX_CHARS] 时只保留末尾部分。
     */
    private fun appendIncoming(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        synchronized(rxLock) {
            val combined = if (utf8Remainder.isEmpty()) bytes else utf8Remainder + bytes

            // 从末尾向前最多回溯 3 个续字节（4 字节序列的最大续字节数），
            // 判断是否停在未完成的多字节序列上
            var keepFrom = combined.size
            var probe = combined.size - 1
            var scanned = 0
            while (probe >= 0 && scanned < 4) {
                val b = combined[probe].toInt() and 0xFF
                if (b and 0xC0 == 0x80) {
                    probe--
                    scanned++
                    continue
                }
                val needed = when {
                    b and 0x80 == 0x00 -> 1
                    b and 0xE0 == 0xC0 -> 2
                    b and 0xF0 == 0xE0 -> 3
                    b and 0xF8 == 0xF0 -> 4
                    else -> 1
                }
                if (combined.size - probe < needed) keepFrom = probe
                break
            }

            val complete = combined.copyOfRange(0, keepFrom)
            utf8Remainder = if (keepFrom < combined.size) {
                combined.copyOfRange(keepFrom, combined.size)
            } else {
                ByteArray(0)
            }
            if (complete.isEmpty()) return

            val text = String(complete, Charsets.UTF_8)
            _state.update { current ->
                current.copy(rxBuffer = (current.rxBuffer + text).takeLast(MAX_RX_CHARS))
            }
        }
    }

    private fun isTxCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        val tx = txCharacteristic ?: return false
        return characteristic.uuid == tx.uuid
    }

    // ══════════════════════════════════════════════════════════════════
    // 超时与参数
    // ══════════════════════════════════════════════════════════════════

    private fun startDiscoverTimeout() {
        discoverTimeoutJob?.cancel()
        discoverTimeoutJob = scope.launch {
            delay(DISCOVER_TIMEOUT_MS)
            if (_state.value.phase == NusPhase.DISCOVERING) {
                discoverTimeoutJob = null
                failSession("服务发现超时（10 秒）—— 设备未返回服务表；可重新连接，或先在系统蓝牙中配对")
            }
        }
    }

    private fun startSubscribeTimeout() {
        subscribeTimeoutJob?.cancel()
        subscribeTimeoutJob = scope.launch {
            delay(SUBSCRIBE_TIMEOUT_MS)
            if (pendingSubscribe) {
                subscribeTimeoutJob = null
                failSession("通知订阅超时（10 秒）—— CCCD 写入未收到系统回调")
            }
        }
    }

    private fun startWriteTimeout() {
        writeTimeoutJob?.cancel()
        writeTimeoutJob = scope.launch {
            delay(WRITE_TIMEOUT_MS)
            if (_state.value.sending) {
                writeTimeoutJob = null
                _state.update { it.copy(sending = false) }
                publishError("写入超时（5 秒）—— 未收到 onCharacteristicWrite 回调")
            }
        }
    }

    /** 订阅就绪后请求 MTU，并在 API 26+ 读取 PHY；结果只认系统回调 */
    private fun requestConnectionParameters(gatt: BluetoothGatt) {
        val mtuAccepted = runCatching { gatt.requestMtu(REQUEST_MTU) }.getOrDefault(false)
        if (mtuAccepted) {
            log("已请求 MTU $REQUEST_MTU（等待 onMtuChanged）")
        } else {
            log("[!] MTU 请求未被系统接受 —— 继续使用默认 23 B", isError = true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // readPhy() 返回 void，只能以调用是否抛异常判断请求是否发出
            runCatching { gatt.readPhy() }.onFailure {
                log("[!] PHY 读取请求失败 —— 显示 --", isError = true)
            }
        } else {
            _state.update { it.copy(phy = "API < 26 不支持") }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 内部实现
    // ══════════════════════════════════════════════════════════════════

    /** 取消全部超时与挂起状态（幂等） */
    private fun cleanupJobs() {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
        discoverTimeoutJob?.cancel()
        discoverTimeoutJob = null
        subscribeTimeoutJob?.cancel()
        subscribeTimeoutJob = null
        writeTimeoutJob?.cancel()
        writeTimeoutJob = null
        pendingSubscribe = false
    }

    /** 释放连接对象与回调引用（幂等） */
    private fun closeGattInternal() {
        cleanupJobs()
        rxCharacteristic = null
        txCharacteristic = null
        val current = gattRef
        gattRef = null
        if (current != null) {
            runCatching { current.disconnect() }
            runCatching { current.close() }
        }
    }

    /** 致命错误：关闭连接并进入指定阶段（默认 ERROR） */
    private fun failSession(message: String, phase: NusPhase = NusPhase.ERROR) {
        closeGattInternal()
        _state.update {
            it.copy(
                phase = phase,
                mtu = 0,
                phy = "--",
                sending = false,
                error = message,
            )
        }
        log(message, isError = true)
    }

    private fun publishFatal(message: String) = failSession(message)

    /** 普通错误：只更新 error，不改变连接阶段 */
    private fun publishError(message: String) {
        _state.update { it.copy(error = message) }
        log(message, isError = true)
    }

    private fun adapterOrNull(): android.bluetooth.BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private fun missingPermissions(): List<String> {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        return required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    /** 追加一条日志：最新在前，最多保留 [MAX_LOG_ENTRIES] 条 */
    private fun log(text: String, isError: Boolean = false) {
        val time = synchronized(logFormat) { logFormat.format(Date()) }
        val entry = NusLogEntry(time = time, text = text, isError = isError)
        _state.update { it.copy(log = (listOf(entry) + it.log).take(MAX_LOG_ENTRIES)) }
    }

    private fun normalizeNusMac(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val allowed = trimmed.all {
            it.isDigit() || it in 'a'..'f' || it in 'A'..'F' ||
                it == ':' || it == '-' || it == '.' || it.isWhitespace()
        }
        if (!allowed) return null
        val hex = trimmed.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        if (hex.length != 12) return null
        return hex.uppercase(Locale.US).chunked(2).joinToString(":")
    }

    /** 日志里的短 UUID：取末尾 8 位 */
    private fun shortUuid(uuid: UUID): String {
        val text = uuid.toString().uppercase(Locale.US)
        return if (text.length > 8) "…" + text.takeLast(8) else text
    }

    /** 属性位 → 缩写串 */
    private fun propertyShort(props: Int): String = buildList {
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("R")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("W")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WNR")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("N")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("I")
    }.joinToString("/").ifEmpty { "--" }

    private fun phyName(phy: Int): String = when (phy) {
        BluetoothDevice.PHY_LE_1M -> "1M"
        BluetoothDevice.PHY_LE_2M -> "2M"
        BluetoothDevice.PHY_LE_CODED -> "Coded"
        else -> "未知($phy)"
    }

    companion object {
        /** 连接超时：10 秒未进入已连接状态即断开 */
        const val CONNECT_TIMEOUT_MS = 10_000L

        /** 服务发现超时：10 秒未收到服务表即断开 */
        const val DISCOVER_TIMEOUT_MS = 10_000L

        /** CCCD 订阅等待上限 */
        const val SUBSCRIBE_TIMEOUT_MS = 10_000L

        /** 单次写入回调等待上限 */
        const val WRITE_TIMEOUT_MS = 5_000L

        /** 终端接收缓冲字符上限 */
        const val MAX_RX_CHARS = 20_000

        /** 日志最多保留条数 */
        const val MAX_LOG_ENTRIES = 200

        /** 连接后请求的 ATT MTU（nRF Connect 常用值） */
        const val REQUEST_MTU = 247

        /** Nordic UART Service */
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

        /** NUS RX：手机 → 设备，写 */
        val NUS_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

        /** NUS TX：设备 → 手机，通知 */
        val NUS_TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

        /** 旧式串口服务（HM-10 等） */
        val FFE0_SERVICE_UUID: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")

        /** FFE0 服务下的串口特征，同时承担写与通知 */
        val FFE1_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

        /** 客户端特征配置描述符（CCCD） */
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        /** API 33+ 写接口返回值 0 = BluetoothStatusCodes.SUCCESS */
        private const val GATT_STATUS_SUCCESS = 0
    }
}

// ─── 文件级辅助 ─────────────────────────────────────────────────────────────

/** 权限名 → 中文标签 */
private fun permissionLabel(permission: String): String = when (permission) {
    Manifest.permission.BLUETOOTH_CONNECT -> "连接设备 / 读取蓝牙状态"
    Manifest.permission.BLUETOOTH -> "蓝牙（旧版权限）"
    Manifest.permission.BLUETOOTH_ADMIN -> "蓝牙管理（旧版权限）"
    else -> permission
}

/** GATT / ATT 状态码 → 可读原因 */
private fun gattStatusHint(status: Int): String = when (status) {
    133 -> " —— 设备不可达 / 已被其他主机连接 / 配对缓存异常；可在系统蓝牙中取消配对后重试"
    8 -> " —— 连接超时"
    19 -> " —— 远端主动断开"
    22 -> " —— 本机主动断开"
    5 -> " —— 认证不足，可能需要先在系统蓝牙中配对"
    3 -> " —— 写入不被允许"
    6 -> " —— 请求不被支持"
    15 -> " —— 加密密钥长度不足"
    else -> ""
}

/** connectGatt 抛出的异常 → 可读中文 */
private fun connectFailureText(t: Throwable): String = when (t) {
    is SecurityException -> "连接被系统拒绝：${t.message ?: "缺少 BLUETOOTH_CONNECT 权限"}"
    is IllegalArgumentException -> "MAC 地址无效：${t.message ?: "系统无法解析"}"
    else -> "连接失败：${t.message ?: t.javaClass.simpleName}"
}
