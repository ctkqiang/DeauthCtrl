package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

/**
 * 一台系统已配对设备
 *
 * @property name 设备名；读不到时为 "--"，绝不编造
 * @property address 设备 MAC
 * @property bondState 配对状态中文：已配对 / 配对中 / 未配对
 * @property type 传输类型中文：经典蓝牙 / BLE / 双模 / 未知
 */
data class BondedDevice(
    val name: String,
    val address: String,
    val bondState: String,
    val type: String,
)

/**
 * 绑定管理页面状态
 *
 * @property devices 系统 BluetoothAdapter.bondedDevices 的实时快照
 * @property loading 是否正在读取配对列表
 * @property busyAddress 正在执行解绑的目标 MAC；null 表示空闲
 * @property error 读取 / 权限 / 蓝牙开关错误
 * @property message 解绑结果提示（成功、被系统拒绝、或要求到系统设置操作）
 */
data class BondState(
    val devices: List<BondedDevice> = emptyList(),
    val loading: Boolean = false,
    val busyAddress: String? = null,
    val error: String? = null,
    val message: String? = null,
)

/**
 * 蓝牙绑定管理器
 *
 * 对标 nRF Connect 的 Bonding 页：列出系统已配对设备，并提供删除（解绑）操作。
 *
 * ## 解绑的真实边界
 * Android 公开 API 没有「解绑」接口，唯一可用的路径是反射调用
 * `BluetoothDevice.removeBond()`（public 但 @hide）。不同厂商 ROM、Android 9+
 * 的非 SDK 接口限制都可能让该调用失败（NoSuchMethodException / 被拦截的
 * InvocationTargetException）。因此：
 * - 调用返回 true 只代表系统接受了请求，最终以配对列表是否真的移除为准；
 * - 失败时页面明确提示「请到系统蓝牙设置中忽略此设备」，不假装已删除。
 *
 * ## 数据纪律
 * - 列表只来自系统适配器，不添加、不推测任何设备；
 * - 设备名读不到显示 "--"；未配对设备读取失败时如实报错。
 */
@Suppress("MissingPermission")
class BondManager(private val context: Context) {

    private val _state = MutableStateFlow(BondState())
    val state: StateFlow<BondState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 读取系统配对列表；权限 / 蓝牙开关异常会写入 state.error */
    fun refresh() {
        val adapter = adapterOrNull()
        if (adapter == null) {
            publishError(L10n.get(R.string.mgr_bond_err_no_adapter))
            return
        }
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) {
            publishError(L10n.get(R.string.mgr_bond_err_bt_off))
            return
        }
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            publishError(
                L10n.get(
                    R.string.mgr_bond_err_permissions,
                    missing.joinToString(L10n.get(R.string.mgr_bond_perm_sep)) { permissionLabel(it) },
                ),
            )
            return
        }

        _state.update { it.copy(loading = true, error = null) }

        val read = runCatching {
            adapter.bondedDevices.orEmpty()
                .map { device ->
                    BondedDevice(
                        name = runCatching { device.name }.getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?: "--",
                        address = runCatching { device.address }.getOrNull() ?: "--",
                        bondState = bondStateLabel(
                            runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE),
                        ),
                        type = typeLabel(
                            runCatching { device.type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN),
                        ),
                    )
                }
                .sortedWith(
                    compareBy<BondedDevice> { it.name == "--" }.thenBy { it.name.lowercase(Locale.US) },
                )
        }

        read.onSuccess { devices ->
            _state.update { it.copy(devices = devices, loading = false, error = null) }
        }.onFailure { throwable ->
            publishError(
                L10n.get(
                    R.string.mgr_bond_err_read,
                    throwable.message ?: throwable.javaClass.simpleName,
                ),
            )
        }
    }

    /**
     * 解绑一台设备
     *
     * 通过反射调用隐藏方法 `BluetoothDevice.removeBond()`。
     * @return true 仅表示调用返回成功；最终结果由 [scheduleRecheck] 刷新配对列表确认
     */
    fun removeBond(address: String): Boolean {
        val normalized = normalizeBondMac(address)
        if (normalized == null) {
            publishError(L10n.get(R.string.mgr_bond_err_mac_invalid))
            return false
        }
        val adapter = adapterOrNull()
        if (adapter == null) {
            publishError(L10n.get(R.string.mgr_bond_err_no_adapter))
            return false
        }
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) {
            publishError(L10n.get(R.string.mgr_bond_err_bt_off))
            return false
        }
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            publishError(
                L10n.get(
                    R.string.mgr_bond_err_permissions,
                    missing.joinToString(L10n.get(R.string.mgr_bond_perm_sep)) { permissionLabel(it) },
                ),
            )
            return false
        }

        val device = runCatching { adapter.getRemoteDevice(normalized) }.getOrNull()
        if (device == null) {
            publishError(L10n.get(R.string.mgr_bond_err_unresolvable))
            return false
        }
        val currentBond = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
        if (currentBond != BluetoothDevice.BOND_BONDED) {
            publishError(L10n.get(R.string.mgr_bond_err_not_bonded))
            return false
        }

        _state.update { it.copy(busyAddress = normalized, error = null, message = null) }

        val method = runCatching { device.javaClass.getMethod("removeBond") }.getOrNull()
        if (method == null) {
            _state.update {
                it.copy(
                    busyAddress = null,
                    message = L10n.get(R.string.mgr_bond_msg_no_api),
                )
            }
            return false
        }

        val result = runCatching { method.invoke(device) as? Boolean ?: false }

        return result.fold(
            onSuccess = { removed ->
                if (removed) {
                    _state.update {
                        it.copy(message = L10n.get(R.string.mgr_bond_msg_request_sent))
                    }
                    scheduleRecheck(normalized)
                    true
                } else {
                    _state.update {
                        it.copy(
                            busyAddress = null,
                            message = L10n.get(R.string.mgr_bond_msg_refused),
                        )
                    }
                    false
                }
            },
            onFailure = { throwable ->
                _state.update {
                    it.copy(
                        busyAddress = null,
                        message = L10n.get(R.string.mgr_bond_msg_call_failed, throwable.javaClass.simpleName),
                    )
                }
                false
            },
        )
    }

    /** ViewModel 回收时调用：取消延迟刷新协程 */
    fun destroy() {
        scope.cancel()
    }

    /** 解绑后延迟刷新两次，用真实配对列表确认设备是否已移除 */
    private fun scheduleRecheck(address: String) {
        scope.launch {
            delay(1_200L)
            refresh()
            delay(2_000L)
            refresh()
            val stillBonded = _state.value.devices.any {
                it.address.equals(address, ignoreCase = true)
            }
            _state.update {
                it.copy(
                    busyAddress = null,
                    message = if (stillBonded) {
                        L10n.get(R.string.mgr_bond_msg_still_bonded)
                    } else {
                        L10n.get(R.string.mgr_bond_msg_unbonded)
                    },
                )
            }
        }
    }

    private fun publishError(message: String) {
        _state.update {
            it.copy(error = message, loading = false, busyAddress = null)
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

    private fun normalizeBondMac(raw: String): String? {
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
}

// ─── 文件级辅助 ─────────────────────────────────────────────────────────────

/** 权限名 → 本地化标签 */
private fun permissionLabel(permission: String): String = when (permission) {
    Manifest.permission.BLUETOOTH_CONNECT -> L10n.get(R.string.mgr_bond_perm_connect)
    Manifest.permission.BLUETOOTH -> L10n.get(R.string.mgr_bond_perm_bluetooth)
    Manifest.permission.BLUETOOTH_ADMIN -> L10n.get(R.string.mgr_bond_perm_admin)
    else -> permission
}

/** BluetoothDevice.bondState → 本地化标签 */
private fun bondStateLabel(state: Int): String = when (state) {
    BluetoothDevice.BOND_NONE -> L10n.get(R.string.mgr_bond_state_none)
    BluetoothDevice.BOND_BONDING -> L10n.get(R.string.mgr_bond_state_bonding)
    BluetoothDevice.BOND_BONDED -> L10n.get(R.string.mgr_bond_state_bonded)
    else -> L10n.get(R.string.mgr_bond_state_unknown, state)
}

/** BluetoothDevice.type → 本地化标签 */
private fun typeLabel(type: Int): String = when (type) {
    BluetoothDevice.DEVICE_TYPE_CLASSIC -> L10n.get(R.string.mgr_bond_type_classic)
    BluetoothDevice.DEVICE_TYPE_LE -> L10n.get(R.string.mgr_bond_type_le)
    BluetoothDevice.DEVICE_TYPE_DUAL -> L10n.get(R.string.mgr_bond_type_dual)
    else -> L10n.get(R.string.mgr_bond_type_unknown)
}
