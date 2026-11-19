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
import androidx.annotation.StringRes
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
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

/**
 * GATT 连接阶段
 *
 * 与 nRF Connect 的顶部状态一致：断开 → 连接中 → 已连接 → 发现服务中 → 枚举完成。
 * [ERROR] 只用于连接 / 服务发现这一类致命失败；单次读写的失败不清空服务表。
 */
enum class BleGattPhase(@StringRes val labelRes: Int) {
    DISCONNECTED(R.string.mgr_gatt_phase_disconnected),
    CONNECTING(R.string.mgr_gatt_phase_connecting),
    CONNECTED(R.string.mgr_gatt_phase_connected),
    DISCOVERING(R.string.mgr_gatt_phase_discovering),
    READY(R.string.mgr_gatt_phase_ready),
    ERROR(R.string.mgr_gatt_phase_error),
}

/**
 * 单个 GATT 特征的信息快照
 *
 * @property uuid 128 位特征 UUID（系统原始格式，不做缩短）
 * @property propertyShort 属性缩写，如 "R/W/N"；无任何属性时为 "--"
 * @property readable 是否声明了 READ
 * @property writable 是否声明了 WRITE 或 WRITE_NO_RESPONSE
 * @property notifiable 是否声明了 NOTIFY 或 INDICATE
 * @property valueHex 最近一次读取 / 通知收到的原始字节（空格分隔大写十六进制）；
 *   空串表示尚未读到真实值，界面显示 "--"
 * @property valueText 同一批字节按 UTF-8 可解码时的文本预览；不可解码时为空串
 * @property updatedAtMs 最近一次收到值的系统时间戳；0 表示从未读到过
 * @property descriptors 该特征的描述符 UUID 列表（含 CCCD 0x2902）
 * @property subscribed 远端通知是否已确认开启（只在 CCCD 写入回调成功后置 true）
 */
data class GattCharacteristicInfo(
    val uuid: String,
    val propertyShort: String,
    val readable: Boolean,
    val writable: Boolean,
    val notifiable: Boolean,
    val valueHex: String = "",
    val valueText: String = "",
    val updatedAtMs: Long = 0L,
    val descriptors: List<String> = emptyList(),
    val subscribed: Boolean = false,
)

/**
 * 单个 GATT 服务的信息快照
 *
 * @property uuid 服务 UUID
 * @property characteristics 该服务下的特征列表，顺序与系统返回一致
 */
data class GattServiceInfo(
    val uuid: String,
    val characteristics: List<GattCharacteristicInfo> = emptyList(),
)

/**
 * GATT 事件日志条目
 *
 * @property time 本机本地时间 HH:mm:ss
 * @property text 事件描述（中文）
 * @property isError 失败 / 告警行，界面用 Danger 色显示
 */
data class GattLogEntry(
    val time: String,
    val text: String,
    val isError: Boolean = false,
)

/**
 * BLE GATT 工作台页面状态
 *
 * @property phase 当前连接阶段
 * @property deviceName 系统返回的设备名；读不到时为 "--"，绝不编造
 * @property deviceAddress 用户输入的 MAC（规范化后）
 * @property services 服务 / 特征枚举结果；断开时清空，避免把旧连接的值当成本次结果
 * @property log 事件日志，最新在前，最多 [BleGattManager.MAX_LOG_ENTRIES] 条
 * @property error 最近一次错误（中文），成功操作或重启连接后清空
 */
data class BleGattState(
    val phase: BleGattPhase = BleGattPhase.DISCONNECTED,
    val deviceName: String = "--",
    val deviceAddress: String = "",
    val services: List<GattServiceInfo> = emptyList(),
    val log: List<GattLogEntry> = emptyList(),
    val error: String? = null,
) {
    /** 是否处于已连接会话中（含服务发现阶段） */
    val connected: Boolean
        get() = phase == BleGattPhase.CONNECTED ||
            phase == BleGattPhase.DISCOVERING ||
            phase == BleGattPhase.READY

    /** 已枚举特征总数 */
    val characteristicCount: Int get() = services.sumOf { it.characteristics.size }
}

/** 一次等待 CCCD 写入回调的订阅请求 */
private data class PendingNotify(
    val serviceUuid: String,
    val characteristicUuid: String,
    val enable: Boolean,
)

/**
 * BLE GATT 服务枚举 / 读写 / 通知管理器
 *
 * 等价于 nRF Connect / ble.enum / ble.write 这类工具的核心能力：
 * 连接指定 MAC → 发现 GATT 服务表 → 遍历服务与特征（UUID、属性、描述符）
 * → 对单个特征执行读、写、订阅通知。
 *
 * ## 连接与超时
 * - `connectGatt(ctx, false, cb, TRANSPORT_LE)` 主动连接，10 秒未进入已连接状态即断开并报错；
 * - 连接成功后立即 `discoverServices()`，10 秒未收到服务表即断开并报错；
 * - 每次读写 / 写描述符另设 10 秒操作看门狗：BLE 同一时刻只允许一个 GATT 请求，
 *   回调丢失时不让界面永久卡在"上一操作未完成"。
 *
 * ## 数据纪律
 * - 服务 / 特征 / 属性 / 描述符全部来自系统 [BluetoothGatt] 回调，不推测、不补全；
 * - 未读取过的值显示 "--"，读取成功但内容为空显示 "(空)"；
 * - 文本只在 UTF-8 可解码时展示，二进制值只展示十六进制；
 * - 设备名读不到时显示 "--"，不用 MAC 或型号冒充名称。
 *
 * ## 权限与 API 兼容
 * - Android 12+ 需要 BLUETOOTH_CONNECT（权限由应用网关统一申请，本类只检查）；
 * - Android 13+ 的读 / 通知回调与写 API 带 byte[] 参数，使用 Build.VERSION 守卫，
 *   旧版本走已弃用但可用的旧签名。
 *
 * 明确局限：目标设备必须处于广播 / 可连接状态；部分设备需先在系统蓝牙中配对；
 * 写操作会真实改变设备状态，没有撤销通道。
 */
@Suppress("MissingPermission", "DEPRECATION", "NewApi")
class BleGattManager(private val context: Context) {

    private val _state = MutableStateFlow(BleGattState())
    val state: StateFlow<BleGattState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Volatile
    private var gattRef: BluetoothGatt? = null

    /** 当前未完成的 GATT 请求描述；null 表示空闲 */
    @Volatile
    private var operationInProgress: String? = null

    /** 等待 CCCD 写入回调的订阅请求 */
    @Volatile
    private var pendingNotify: PendingNotify? = null

    private var connectTimeoutJob: Job? = null
    private var discoverTimeoutJob: Job? = null
    private var operationTimeoutJob: Job? = null

    // ══════════════════════════════════════════════════════════════════
    // 连接 / 断开
    // ══════════════════════════════════════════════════════════════════

    /**
     * 连接目标设备（同步完成输入与权限校验）
     *
     * @return false 表示校验或 connectGatt 调用失败，原因在 state.error
     */
    fun connect(mac: String): Boolean {
        val normalized = normalizeMac(mac)
        if (normalized == null) {
            publishError(L10n.get(R.string.mgr_gatt_err_mac_invalid))
            return false
        }

        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            publishError(
                L10n.get(
                    R.string.mgr_gatt_err_missing_perm,
                    missing.joinToString(L10n.get(R.string.mgr_gatt_perm_separator)) { permissionLabel(it) },
                ),
            )
            return false
        }

        val adapter = adapterOrNull()
        if (adapter == null) {
            publishError(L10n.get(R.string.mgr_gatt_err_no_adapter))
            return false
        }
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) {
            publishError(L10n.get(R.string.mgr_gatt_err_bt_off))
            return false
        }

        // 收回上一次连接，保证同一时刻只有一个 GATT 客户端
        closeGattInternal()

        val device = runCatching { adapter.getRemoteDevice(normalized) }.getOrNull()
        if (device == null) {
            publishError(L10n.get(R.string.mgr_gatt_err_mac_resolve))
            return false
        }

        val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "--"
        val keptLog = _state.value.log
        _state.value = BleGattState(
            phase = BleGattPhase.CONNECTING,
            deviceName = name,
            deviceAddress = normalized,
            log = keptLog,
        )
        log(L10n.get(R.string.mgr_gatt_log_connecting, normalized, name))

        // 连接超时：10 秒仍未进入已连接状态就断开，避免界面永远停在"连接中"
        connectTimeoutJob?.cancel()
        connectTimeoutJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_state.value.phase == BleGattPhase.CONNECTING) {
                connectTimeoutJob = null
                val stale = gattRef
                gattRef = null
                runCatching { stale?.disconnect() }
                runCatching { stale?.close() }
                publishFatal(L10n.get(R.string.mgr_gatt_err_connect_timeout))
            }
        }

        val connection = runCatching {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrElse { t ->
            connectTimeoutJob?.cancel()
            connectTimeoutJob = null
            publishFatal(connectFailureText(t))
            return false
        }
        if (connection == null) {
            connectTimeoutJob?.cancel()
            connectTimeoutJob = null
            publishFatal(L10n.get(R.string.mgr_gatt_err_connect_null))
            return false
        }
        gattRef = connection
        return true
    }

    /** 主动断开；保留日志，清空服务表与错误 */
    fun disconnect() {
        val hadSession = gattRef != null || _state.value.phase != BleGattPhase.DISCONNECTED
        closeGattInternal()
        _state.update {
            it.copy(phase = BleGattPhase.DISCONNECTED, services = emptyList(), error = null)
        }
        if (hadSession) log(L10n.get(R.string.mgr_gatt_log_disconnected))
    }

    /** ViewModel 回收时调用：断开并取消所有后台协程 */
    fun destroy() {
        closeGattInternal()
        scope.cancel()
    }

    /** 清空事件日志（服务表与连接状态不变） */
    fun clearLog() {
        _state.update { it.copy(log = emptyList()) }
    }

    // ══════════════════════════════════════════════════════════════════
    // 读 / 写 / 订阅
    // ══════════════════════════════════════════════════════════════════

    /**
     * 读取一个特征的值
     *
     * 回调 [BluetoothGattCallback.onCharacteristicRead] 成功后，原始字节写入
     * [GattCharacteristicInfo.valueHex]；失败只记录错误，不清空已有值。
     */
    fun read(serviceUuid: String, characteristicUuid: String) {
        val gatt = gattRef
        if (gatt == null) {
            publishError(L10n.get(R.string.mgr_gatt_err_not_connected))
            return
        }
        if (_state.value.phase != BleGattPhase.READY) {
            publishError(L10n.get(R.string.mgr_gatt_err_not_ready_read))
            return
        }
        val characteristic = findCharacteristic(gatt, serviceUuid, characteristicUuid)
        if (characteristic == null) {
            publishError(L10n.get(R.string.mgr_gatt_err_char_missing))
            return
        }
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
            publishError(
                L10n.get(R.string.mgr_gatt_err_read_unsupported, propertyShort(characteristic.properties)),
            )
            return
        }
        if (!beginOperation(L10n.get(R.string.mgr_gatt_op_read, shortUuid(characteristic.uuid)))) return

        val accepted = runCatching { gatt.readCharacteristic(characteristic) }.getOrDefault(false)
        if (!accepted) {
            finishOperation()
            publishError(L10n.get(R.string.mgr_gatt_err_read_rejected))
        } else {
            log(L10n.get(R.string.mgr_gatt_log_read_sent, shortUuid(characteristic.uuid)))
        }
    }

    /**
     * 向一个特征写入十六进制字节
     *
     * @param hexInput 十六进制文本，允许空格 / 冒号 / 短横线分隔与 "0x" 前缀
     * @return true 表示请求已被系统接受（不等于远端已确认写入）
     */
    fun write(serviceUuid: String, characteristicUuid: String, hexInput: String): Boolean {
        val gatt = gattRef
        if (gatt == null) {
            publishError(L10n.get(R.string.mgr_gatt_err_not_connected))
            return false
        }
        if (_state.value.phase != BleGattPhase.READY) {
            publishError(L10n.get(R.string.mgr_gatt_err_not_ready_write))
            return false
        }

        val bytes = parseHex(hexInput)
        if (bytes == null) {
            publishError(L10n.get(R.string.mgr_gatt_err_hex_invalid))
            return false
        }
        if (bytes.isEmpty()) {
            publishError(L10n.get(R.string.mgr_gatt_err_hex_empty))
            return false
        }
        if (bytes.size > MAX_WRITE_BYTES) {
            publishError(L10n.get(R.string.mgr_gatt_err_write_too_long, bytes.size, MAX_WRITE_BYTES))
            return false
        }

        val characteristic = findCharacteristic(gatt, serviceUuid, characteristicUuid)
        if (characteristic == null) {
            publishError(L10n.get(R.string.mgr_gatt_err_char_missing))
            return false
        }
        val writeType = when {
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ->
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ->
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> {
                publishError(
                    L10n.get(R.string.mgr_gatt_err_write_unsupported, propertyShort(characteristic.properties)),
                )
                return false
            }
        }

        if (!beginOperation(L10n.get(R.string.mgr_gatt_op_write, shortUuid(characteristic.uuid)))) return false

        // API 33+ 使用带 value 的新重载（返回 BluetoothStatusCodes，0 = SUCCESS）；
        // 旧系统仍走 characteristic.value + 单参重载。
        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                gatt.writeCharacteristic(characteristic, bytes, writeType) == GATT_STATUS_SUCCESS
            }.getOrDefault(false)
        } else {
            runCatching {
                characteristic.writeType = writeType
                characteristic.value = bytes
                gatt.writeCharacteristic(characteristic)
            }.getOrDefault(false)
        }

        if (!accepted) {
            finishOperation()
            publishError(L10n.get(R.string.mgr_gatt_err_write_rejected))
            return false
        }
        log(L10n.get(R.string.mgr_gatt_log_write_sent, shortUuid(characteristic.uuid), bytes.toHex()))
        return true
    }

    /**
     * 开启 / 关闭一个特征的通知（CCCD 0x2902）
     *
     * 先本地 `setCharacteristicNotification`，再向 CCCD 写 ENABLE / DISABLE。
     * 只有描述符写入回调成功后才把 subscribed 置为 true，不提前假装已订阅。
     */
    fun setNotify(serviceUuid: String, characteristicUuid: String, enable: Boolean) {
        val gatt = gattRef
        if (gatt == null) {
            publishError(L10n.get(R.string.mgr_gatt_err_not_connected))
            return
        }
        if (_state.value.phase != BleGattPhase.READY) {
            publishError(L10n.get(R.string.mgr_gatt_err_not_ready_notify))
            return
        }
        val characteristic = findCharacteristic(gatt, serviceUuid, characteristicUuid)
        if (characteristic == null) {
            publishError(L10n.get(R.string.mgr_gatt_err_char_missing))
            return
        }

        val canNotify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val canIndicate = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        if (enable && !canNotify && !canIndicate) {
            publishError(
                L10n.get(R.string.mgr_gatt_err_notify_unsupported, propertyShort(characteristic.properties)),
            )
            return
        }

        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (!enable && descriptor == null) {
            // 没有 CCCD 时远端本就不会发通知，只关闭本地注册即可
            runCatching { gatt.setCharacteristicNotification(characteristic, false) }
            updateSubscribed(serviceUuid, characteristicUuid, false)
            log(L10n.get(R.string.mgr_gatt_log_notify_local_off))
            return
        }
        if (enable && descriptor == null) {
            publishError(L10n.get(R.string.mgr_gatt_err_no_cccd))
            return
        }

        val notifyUuid = shortUuid(characteristic.uuid)
        val operationLabel = if (enable) {
            L10n.get(R.string.mgr_gatt_op_notify_on, notifyUuid)
        } else {
            L10n.get(R.string.mgr_gatt_op_notify_off, notifyUuid)
        }
        if (!beginOperation(operationLabel)) return

        val localRegistered = runCatching {
            gatt.setCharacteristicNotification(characteristic, enable)
        }.getOrDefault(false)
        if (!localRegistered) {
            finishOperation()
            publishError(L10n.get(R.string.mgr_gatt_err_local_notify))
            return
        }

        if (descriptor == null) {
            // 关闭路径的兜底（正常流程已在上面提前返回）
            finishOperation()
            updateSubscribed(serviceUuid, characteristicUuid, false)
            return
        }

        val cccdValue = when {
            !enable -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            canNotify -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }

        pendingNotify = PendingNotify(serviceUuid, characteristicUuid, enable)

        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { gatt.writeDescriptor(descriptor, cccdValue) == GATT_STATUS_SUCCESS }
                .getOrDefault(false)
        } else {
            runCatching {
                descriptor.value = cccdValue
                gatt.writeDescriptor(descriptor)
            }.getOrDefault(false)
        }

        if (!accepted) {
            // 回滚本地注册，避免本地开着但远端没订上的假象
            runCatching { gatt.setCharacteristicNotification(characteristic, !enable) }
            finishOperation()
            publishError(L10n.get(R.string.mgr_gatt_err_notify_rejected))
        } else {
            log(
                if (enable) {
                    L10n.get(R.string.mgr_gatt_log_notify_requested, shortUuid(characteristic.uuid))
                } else {
                    L10n.get(R.string.mgr_gatt_log_unsubscribe_sent, shortUuid(characteristic.uuid))
                },
            )
        }
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
                        closeGattInternal()
                        publishFatal(
                            L10n.get(R.string.mgr_gatt_err_connect_status, status, gattStatusHint(status)),
                        )
                        return
                    }
                    connectTimeoutJob?.cancel()
                    connectTimeoutJob = null
                    log(L10n.get(R.string.mgr_gatt_log_connected, _state.value.deviceAddress))
                    _state.update { it.copy(phase = BleGattPhase.CONNECTED, error = null) }

                    val started = runCatching { gatt.discoverServices() }.getOrDefault(false)
                    if (!started) {
                        closeGattInternal()
                        publishFatal(L10n.get(R.string.mgr_gatt_err_discover_start))
                    } else {
                        _state.update { it.copy(phase = BleGattPhase.DISCOVERING) }
                        startDiscoverTimeout()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectTimeoutJob?.cancel()
                    connectTimeoutJob = null
                    discoverTimeoutJob?.cancel()
                    discoverTimeoutJob = null
                    operationTimeoutJob?.cancel()
                    operationTimeoutJob = null
                    operationInProgress = null
                    pendingNotify = null
                    gattRef = null
                    runCatching { gatt.close() }

                    _state.update {
                        it.copy(
                            phase = BleGattPhase.DISCONNECTED,
                            services = emptyList(),
                            error = null,
                        )
                    }
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        log(L10n.get(R.string.mgr_gatt_log_device_disconnected))
                    } else {
                        publishError(
                            L10n.get(R.string.mgr_gatt_err_disconnected_status, status, gattStatusHint(status)),
                        )
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
                closeGattInternal()
                publishFatal(
                    L10n.get(R.string.mgr_gatt_err_discover_status, status, gattStatusHint(status)),
                )
                return
            }

            val services = enumerateServices(gatt)
            val characteristicCount = services.sumOf { it.characteristics.size }
            _state.update {
                it.copy(phase = BleGattPhase.READY, services = services, error = null)
            }
            log(L10n.get(R.string.mgr_gatt_log_services_done, services.size, characteristicCount))
            if (services.isEmpty()) {
                log(L10n.get(R.string.mgr_gatt_log_no_services), isError = true)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (gatt !== gattRef) return
            handleRead(characteristic, runCatching { characteristic.value }.getOrNull(), status)
        }

        @Suppress("NewApi")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (gatt !== gattRef) return
            handleRead(characteristic, value, status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (gatt !== gattRef) return
            finishOperation()
            if (status != BluetoothGatt.GATT_SUCCESS) {
                publishError(L10n.get(R.string.mgr_gatt_err_write_failed, status, gattStatusHint(status)))
            } else {
                _state.update { it.copy(error = null) }
                log(L10n.get(R.string.mgr_gatt_log_write_done, shortUuid(characteristic.uuid)))
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (gatt !== gattRef) return
            handleNotification(characteristic, runCatching { characteristic.value }.getOrNull())
        }

        @Suppress("NewApi")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (gatt !== gattRef) return
            handleNotification(characteristic, value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (gatt !== gattRef) return
            val pending = pendingNotify
            finishOperation()
            if (pending == null) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    publishError(L10n.get(R.string.mgr_gatt_err_desc_write, status, gattStatusHint(status)))
                }
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val operationLabel = L10n.get(
                    if (pending.enable) R.string.mgr_gatt_notify_on_label else R.string.mgr_gatt_notify_off_label,
                )
                publishError(
                    L10n.get(
                        R.string.mgr_gatt_err_notify_toggle_failed,
                        operationLabel,
                        status,
                        gattStatusHint(status),
                    ),
                )
                return
            }
            updateSubscribed(pending.serviceUuid, pending.characteristicUuid, pending.enable)
            log(
                if (pending.enable) {
                    L10n.get(
                        R.string.mgr_gatt_log_notify_enabled,
                        shortUuid(UUID.fromString(pending.characteristicUuid)),
                    )
                } else {
                    L10n.get(
                        R.string.mgr_gatt_log_notify_disabled,
                        shortUuid(UUID.fromString(pending.characteristicUuid)),
                    )
                },
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 内部实现
    // ══════════════════════════════════════════════════════════════════

    /** 读回调的公共处理 */
    private fun handleRead(characteristic: BluetoothGattCharacteristic, value: ByteArray?, status: Int) {
        finishOperation()
        if (status != BluetoothGatt.GATT_SUCCESS) {
            publishError(L10n.get(R.string.mgr_gatt_err_read_failed, status, gattStatusHint(status)))
            return
        }
        val serviceUuid = runCatching { characteristic.service?.uuid?.toString() }.getOrNull() ?: return
        val charUuid = characteristic.uuid.toString()
        val bytes = value ?: ByteArray(0)
        applyCharacteristicValue(serviceUuid, charUuid, bytes)

        val hex = bytes.toHex().ifEmpty { L10n.get(R.string.mgr_gatt_empty_value) }
        val preview = textPreview(bytes)
        log(
            if (preview.isNotEmpty()) {
                L10n.get(R.string.mgr_gatt_log_read_value_text, shortUuid(characteristic.uuid), hex, preview)
            } else {
                L10n.get(R.string.mgr_gatt_log_read_value, shortUuid(characteristic.uuid), hex)
            },
        )
    }

    /** 通知回调的公共处理 */
    private fun handleNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray?) {
        val serviceUuid = runCatching { characteristic.service?.uuid?.toString() }.getOrNull() ?: return
        val charUuid = characteristic.uuid.toString()
        val bytes = value ?: ByteArray(0)
        applyCharacteristicValue(serviceUuid, charUuid, bytes)

        val hex = bytes.toHex().ifEmpty { L10n.get(R.string.mgr_gatt_empty_value) }
        val preview = textPreview(bytes)
        log(
            if (preview.isNotEmpty()) {
                L10n.get(R.string.mgr_gatt_log_notify_value_text, shortUuid(characteristic.uuid), hex, preview)
            } else {
                L10n.get(R.string.mgr_gatt_log_notify_value, shortUuid(characteristic.uuid), hex)
            },
        )
    }

    /** 把系统返回的字节写入对应特征的展示值 */
    private fun applyCharacteristicValue(serviceUuid: String, charUuid: String, bytes: ByteArray) {
        val hex = bytes.toHex()
        val text = textPreview(bytes)
        val now = System.currentTimeMillis()
        _state.update { st ->
            st.copy(
                services = st.services.map { service ->
                    if (service.uuid != serviceUuid) {
                        service
                    } else {
                        service.copy(
                            characteristics = service.characteristics.map { characteristic ->
                                if (characteristic.uuid != charUuid) {
                                    characteristic
                                } else {
                                    characteristic.copy(
                                        valueHex = hex,
                                        valueText = text,
                                        updatedAtMs = now,
                                    )
                                }
                            },
                        )
                    }
                },
                error = null,
            )
        }
    }

    /** 更新订阅状态（只在 CCCD 写入回调成功后调用） */
    private fun updateSubscribed(serviceUuid: String, charUuid: String, subscribed: Boolean) {
        _state.update { st ->
            st.copy(
                services = st.services.map { service ->
                    if (service.uuid != serviceUuid) {
                        service
                    } else {
                        service.copy(
                            characteristics = service.characteristics.map { characteristic ->
                                if (characteristic.uuid != charUuid) {
                                    characteristic
                                } else {
                                    characteristic.copy(subscribed = subscribed)
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    /** 遍历系统返回的服务表，不做任何补全或推测 */
    private fun enumerateServices(gatt: BluetoothGatt): List<GattServiceInfo> = runCatching {
        gatt.services.orEmpty().map { service ->
            GattServiceInfo(
                uuid = service.uuid.toString(),
                characteristics = service.characteristics.orEmpty().map { characteristic ->
                    val props = characteristic.properties
                    GattCharacteristicInfo(
                        uuid = characteristic.uuid.toString(),
                        propertyShort = propertyShort(props),
                        readable = props and BluetoothGattCharacteristic.PROPERTY_READ != 0,
                        writable = props and (
                            BluetoothGattCharacteristic.PROPERTY_WRITE or
                                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                            ) != 0,
                        notifiable = props and (
                            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                                BluetoothGattCharacteristic.PROPERTY_INDICATE
                            ) != 0,
                        descriptors = characteristic.descriptors.orEmpty().map { it.uuid.toString() },
                    )
                },
            )
        }
    }.getOrDefault(emptyList())

    private fun findCharacteristic(
        gatt: BluetoothGatt,
        serviceUuid: String,
        characteristicUuid: String,
    ): BluetoothGattCharacteristic? = runCatching {
        val service = gatt.getService(UUID.fromString(serviceUuid))
        service?.getCharacteristic(UUID.fromString(characteristicUuid))
    }.getOrNull()

    /** 枚举超时：10 秒收不到服务表就断开并报错 */
    private fun startDiscoverTimeout() {
        discoverTimeoutJob?.cancel()
        discoverTimeoutJob = scope.launch {
            delay(DISCOVER_TIMEOUT_MS)
            if (_state.value.phase == BleGattPhase.DISCOVERING) {
                discoverTimeoutJob = null
                operationInProgress = null
                operationTimeoutJob?.cancel()
                operationTimeoutJob = null
                pendingNotify = null
                val stale = gattRef
                gattRef = null
                runCatching { stale?.disconnect() }
                runCatching { stale?.close() }
                publishFatal(L10n.get(R.string.mgr_gatt_err_discover_timeout))
            }
        }
    }

    /** 抢占 GATT 请求槽；同一时刻只允许一个读 / 写 / 描述符请求 */
    private fun beginOperation(label: String): Boolean {
        val current = operationInProgress
        if (current != null) {
            publishError(L10n.get(R.string.mgr_gatt_err_op_busy, current))
            return false
        }
        operationInProgress = label
        operationTimeoutJob?.cancel()
        operationTimeoutJob = scope.launch {
            delay(OPERATION_TIMEOUT_MS)
            if (operationInProgress == label) {
                operationInProgress = null
                pendingNotify = null
                publishError(L10n.get(R.string.mgr_gatt_err_op_timeout, label))
            }
        }
        return true
    }

    private fun finishOperation() {
        operationInProgress = null
        pendingNotify = null
        operationTimeoutJob?.cancel()
        operationTimeoutJob = null
    }

    /** 释放连接对象、取消全部超时与挂起请求（幂等） */
    private fun closeGattInternal() {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
        discoverTimeoutJob?.cancel()
        discoverTimeoutJob = null
        operationTimeoutJob?.cancel()
        operationTimeoutJob = null
        operationInProgress = null
        pendingNotify = null

        val current = gattRef
        gattRef = null
        if (current != null) {
            runCatching { current.disconnect() }
            runCatching { current.close() }
        }
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

    /** 追加一条日志：最新在前，最多保留 200 条 */
    private fun log(text: String, isError: Boolean = false) {
        val time = synchronized(logFormat) { logFormat.format(Date()) }
        val entry = GattLogEntry(time = time, text = text, isError = isError)
        _state.update { it.copy(log = (listOf(entry) + it.log).take(MAX_LOG_ENTRIES)) }
    }

    /** 普通错误：只更新 error，不改变连接阶段（单次操作失败不等于断线） */
    private fun publishError(message: String) {
        _state.update { it.copy(error = message) }
        log(message, isError = true)
    }

    /** 致命错误：连接 / 服务发现失败，进入 ERROR 并清空旧服务表 */
    private fun publishFatal(message: String) {
        _state.update {
            it.copy(phase = BleGattPhase.ERROR, services = emptyList(), error = message)
        }
        log(message, isError = true)
    }

    private fun normalizeMac(raw: String): String? {
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

    /** 十六进制文本 → 字节；格式非法返回 null，空白返回空数组 */
    private fun parseHex(input: String): ByteArray? {
        if (input.isBlank()) return ByteArray(0)
        val cleaned = input.replace("0x", "", ignoreCase = true)
            .filterNot { it.isWhitespace() || it == ':' || it == ',' || it == '-' || it == '.' }
        if (cleaned.isEmpty()) return ByteArray(0)
        if (cleaned.length % 2 != 0) return null
        val valid = cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        if (!valid) return null
        return ByteArray(cleaned.length / 2) { index ->
            cleaned.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    /** 属性位 → 缩写串，界面用 R / W / WNR / N / I 表达 */
    private fun propertyShort(props: Int): String = buildList {
        if (props and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) add("BC")
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("R")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("W")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WNR")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("N")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("I")
        if (props and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) add("SW")
        if (props and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) add("EP")
    }.joinToString("/").ifEmpty { "--" }

    /** 日志里的短 UUID：取末尾 8 位，完整 UUID 在服务表中展示 */
    private fun shortUuid(uuid: UUID): String {
        val text = uuid.toString().uppercase(Locale.US)
        return if (text.length > 8) "…" + text.takeLast(8) else text
    }

    companion object {
        /** 连接超时：10 秒未进入已连接状态即断线 */
        const val CONNECT_TIMEOUT_MS = 10_000L

        /** 服务枚举超时：10 秒未收到服务表即断线 */
        const val DISCOVER_TIMEOUT_MS = 10_000L

        /** 单次读 / 写 / CCCD 写入的回调看门狗 */
        const val OPERATION_TIMEOUT_MS = 10_000L

        /** 日志最多保留条数 */
        const val MAX_LOG_ENTRIES = 200

        /** 单次写入字节上限（BLE 属性值的常见上限 512B） */
        const val MAX_WRITE_BYTES = 512

        /** API 33+ 写接口返回值 0 = BluetoothStatusCodes.SUCCESS */
        private const val GATT_STATUS_SUCCESS = 0

        /** 客户端特征配置描述符（CCCD） */
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}

// ─── 文件级辅助 ─────────────────────────────────────────────────────────────

/** 字节数组 → 空格分隔的大写十六进制 */
private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

/** 字节 → 可读文本预览；不可按 UTF-8 解码时返回空串（界面显示 "--"） */
private fun textPreview(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return ""
    if (text.isEmpty()) return ""
    val printable = text.all { ch ->
        ch == '\n' || ch == '\r' || ch == '\t' ||
            (!ch.isISOControl() && ch.code != 0xFFFD)
    }
    if (!printable) return ""
    return text.take(64)
}

/** 权限名 → 本地化标签 */
private fun permissionLabel(permission: String): String = when (permission) {
    Manifest.permission.BLUETOOTH_CONNECT -> L10n.get(R.string.mgr_gatt_perm_connect)
    Manifest.permission.BLUETOOTH -> L10n.get(R.string.mgr_gatt_perm_bluetooth)
    Manifest.permission.BLUETOOTH_ADMIN -> L10n.get(R.string.mgr_gatt_perm_bluetooth_admin)
    else -> permission
}

/** GATT / ATT 状态码 → 可读原因 */
private fun gattStatusHint(status: Int): String = when (status) {
    133 -> L10n.get(R.string.mgr_gatt_hint_unreachable)
    8 -> L10n.get(R.string.mgr_gatt_hint_timeout)
    19 -> L10n.get(R.string.mgr_gatt_hint_remote_disconnect)
    22 -> L10n.get(R.string.mgr_gatt_hint_local_disconnect)
    5 -> L10n.get(R.string.mgr_gatt_hint_auth)
    2 -> L10n.get(R.string.mgr_gatt_hint_read_denied)
    3 -> L10n.get(R.string.mgr_gatt_hint_write_denied)
    6 -> L10n.get(R.string.mgr_gatt_hint_not_supported)
    15 -> L10n.get(R.string.mgr_gatt_hint_key_size)
    else -> ""
}

/** connectGatt 抛出的异常 → 本地化文本 */
private fun connectFailureText(t: Throwable): String = when (t) {
    is SecurityException -> L10n.get(
        R.string.mgr_gatt_connect_denied,
        t.message ?: L10n.get(R.string.mgr_gatt_connect_denied_no_perm),
    )
    is IllegalArgumentException -> L10n.get(
        R.string.mgr_gatt_mac_invalid_ex,
        t.message ?: L10n.get(R.string.mgr_gatt_mac_unresolved),
    )
    else -> L10n.get(R.string.mgr_gatt_connect_failed_ex, t.message ?: t.javaClass.simpleName)
}
