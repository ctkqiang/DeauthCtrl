package xin.ctkqiang.deauthctrl.manager

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * BitChat —— BLE 广播聊天(IRC 风格,免配对免 root)。
 * 消息走 BLE 广播制造商数据(≤20 字节有效载荷),双向:
 * 自己发包 + 扫描邻近节点;同频段节点互相转发实现「频道」效果。
 */
class BitChatManager(private val context: Context) {

    data class Message(val sender: String, val text: String, val timeMs: Long, val self: Boolean = false)

    data class State(
        val on: Boolean = false,
        val nickname: String = "anon",
        val channel: String = "general",
        val messages: List<Message> = emptyList(),
        val peers: Set<String> = emptySet(),
    )

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("b1c40001-0000-0000-0000-000000000001")
        private const val CH_PREFIX = 0x11
        private const val NICK_PREFIX = 0x12
        private const val MSG_PREFIX = 0x13
    }

    private val bt = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    @Volatile private var stopFlag = false
    private var advertiser: BluetoothLeAdvertiser? = null

    fun setNickname(n: String) = _state.update { it.copy(nickname = n.take(12)) }
    fun setChannel(c: String) = _state.update { it.copy(channel = c.take(8)) }

    /** 广播一帧:头部字节 + 载荷(UTF-8,≤ 20 字节) */
    private fun frame(prefix: Int, payload: String): ByteArray {
        val p = payload.toByteArray(StandardCharsets.UTF_8).take(19).toByteArray()
        val out = ByteArray(1 + p.size)
        out[0] = prefix.toByte()
        System.arraycopy(p, 0, out, 1, p.size)
        return out
    }

    private val advCallback = object : AdvertiseCallback() {}

    fun start() {
        val a = bt ?: return
        _state.update { it.copy(on = true, messages = emptyList()) }
        stopFlag = false
        // 扫描端
        runCatching {
            a.bluetoothLeScanner?.startScan(scanCallback)
        }
        // 广播端:昵称 + 频道 + 空消息帧(宣布在场)
        advertiser = a.bluetoothLeAdvertiser
        runCatching {
            advertiser?.startAdvertising(
                AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .build(),
                AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid(SERVICE_UUID))
                    .addManufacturerData(0x7777, frame(NICK_PREFIX, _state.value.nickname))
                    .build(),
                advCallback,
            )
        }
    }

    /** 发消息:广播一帧 + 本地回显 */
    fun send(text: String) {
        val s = _state.value
        if (!s.on || text.isBlank()) return
        val msg = Message(s.nickname, text.take(40), System.currentTimeMillis(), self = true)
        _state.update { it.copy(messages = it.messages + msg) }
        runCatching {
            advertiser?.startAdvertising(
                AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .build(),
                AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid(SERVICE_UUID))
                    .addManufacturerData(0x7777, frame(MSG_PREFIX, text))
                    .build(),
                advCallback,
            )
        }
        // 恢复昵称广播
        runCatching {
            advertiser?.startAdvertising(
                AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .build(),
                AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid(SERVICE_UUID))
                    .addManufacturerData(0x7777, frame(NICK_PREFIX, s.nickname))
                    .build(),
                advCallback,
            )
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev: BluetoothDevice = result.device ?: return
            val data = result.scanRecord?.getManufacturerSpecificData(0x7777) ?: return
            if (data.size < 2) return
            val prefix = data[0].toInt() and 0xFF
            val payload = String(data, 1, data.size - 1, StandardCharsets.UTF_8)
            when (prefix) {
                NICK_PREFIX -> _state.update { it.copy(peers = it.peers + payload) }
                MSG_PREFIX -> {
                    val addr = dev.address ?: return
                    val sender = _state.value.peers.firstOrNull { it.isNotBlank() } ?: addr.takeLast(4)
                    val msg = Message(sender, payload, System.currentTimeMillis())
                    _state.update { it.copy(messages = (it.messages + msg).takeLast(100)) }
                }
            }
        }
    }

    fun stop() {
        stopFlag = true
        runCatching { bt?.bluetoothLeScanner?.stopScan(scanCallback) }
        runCatching { advertiser?.stopAdvertising(advCallback) }
        _state.update { it.copy(on = false) }
    }
}
