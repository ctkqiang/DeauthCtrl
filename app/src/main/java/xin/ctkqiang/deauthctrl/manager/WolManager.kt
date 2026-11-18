package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * 网络唤醒状态 —— 由 [WolManager] 的 StateFlow 透传
 *
 * [sent] 为 null 表示尚未发送；true 表示至少有一个广播地址发送成功；
 * false 表示全部失败。[lastResult] 是给操作员的完整中文说明。
 * [localIpv4] / [broadcastAddress] 来自当前网卡的真实地址，读不到为 null，界面显示 "--"。
 */
data class WolState(
    val sending: Boolean = false,
    val sent: Boolean? = null,
    val lastResult: String? = null,
    val localIpv4: String? = null,
    val broadcastAddress: String? = null,
)

/**
 * Wake-on-LAN 魔术包发送器
 *
 * ## 协议
 * 魔术包固定 102 字节：6 字节 0xFF + 目标 MAC 重复 16 次（16 × 6 = 96 字节）。
 * 通过 UDP 广播发出：同时发往 255.255.255.255 与本机 Wi-Fi 网段的定向广播
 * （例如 192.168.1.255），端口默认 9；[wake] 的 alsoPort7 打开时再各发一份到
 * 端口 7（部分老网卡只监听 7）。
 *
 * ## 诚实边界
 * UDP 没有回执：发送成功只代表数据包已交给内核，无法确认目标是否真的被唤醒。
 * 目标设备必须在 BIOS / 网卡固件里开启 Wake-on-LAN，并在关机后保持网卡供电。
 *
 * ## 地址推算
 * 广播地址取自 [NetworkInterface] 中真实存在的 IPv4 接口与子网前缀长度，
 * 优先 wlan 接口；没有可用局域网地址时返回 null，界面显示 "--"，
 * 此时仍会向 255.255.255.255 发送（是否可达由系统路由决定）。
 */
@Suppress("MissingPermission")
class WolManager {

    private val _state = MutableStateFlow(WolState())
    val state: StateFlow<WolState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        refreshNetwork()
    }

    /** 重新探测本机局域网 IPv4 与定向广播地址（网络切换后可调用） */
    fun refreshNetwork() {
        val lan = detectLan()
        _state.update {
            it.copy(
                localIpv4 = lan?.localIp,
                broadcastAddress = lan?.broadcast,
            )
        }
    }

    /**
     * 发送魔术包。
     *
     * MAC 留空时使用 ff:ff:ff:ff:ff:ff（广播唤醒网段内所有支持 WOL 的设备）。
     * [alsoPort7] 为 true 时在端口 9 之外再向端口 7 发送同样的魔术包。
     * 发送在 IO 线程完成，状态通过 [state] 推送；重复调用在发送期间会被忽略。
     */
    fun wake(mac: String = DEFAULT_MAC, alsoPort7: Boolean = false) {
        if (_state.value.sending) return

        _state.update { it.copy(sending = true, sent = null, lastResult = null) }

        scope.launch {
            val normalized = normalizeMac(mac.ifBlank { DEFAULT_MAC })
            if (normalized == null) {
                _state.update {
                    it.copy(
                        sending = false,
                        sent = false,
                        lastResult = "[!] MAC 格式无效 —— 支持 aa:bb:cc:dd:ee:ff、aa-bb-cc-dd-ee-ff 或 12 位十六进制",
                    )
                }
                return@launch
            }

            val packet = magicPacket(normalized)
            if (packet == null) {
                _state.update {
                    it.copy(sending = false, sent = false, lastResult = "[!] 魔术包构建失败 —— MAC 地址无法解析")
                }
                return@launch
            }

            val lan = detectLan()
            val targets = buildList {
                add(GLOBAL_BROADCAST to PORT_WOL)
                lan?.broadcast?.let { add(it to PORT_WOL) }
                if (alsoPort7) {
                    add(GLOBAL_BROADCAST to PORT_WOL_ALT)
                    lan?.broadcast?.let { add(it to PORT_WOL_ALT) }
                }
            }.distinct()

            var success = 0
            val failures = ArrayList<String>()

            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    targets.forEach { (host, port) ->
                        try {
                            socket.send(
                                DatagramPacket(
                                    packet,
                                    packet.size,
                                    InetAddress.getByName(host),
                                    port,
                                ),
                            )
                            success++
                        } catch (t: Throwable) {
                            failures += "$host:$port (${t.message ?: t.javaClass.simpleName})"
                        }
                    }
                }
            } catch (t: Throwable) {
                failures += "UDP Socket (${t.message ?: t.javaClass.simpleName})"
            }

            val ports = if (alsoPort7) "9 / 7" else "9"
            val lastResult = when {
                success == 0 ->
                    "[!] 发送失败 —— " + (failures.firstOrNull() ?: "所有广播地址均不可达")

                failures.isNotEmpty() ->
                    "[!] 部分发送失败 ${failures.size}/${targets.size}：${failures.joinToString("；")}；" +
                        "已成功 $success 个。端口 $ports。"

                else ->
                    "已发送 102 B 魔术包 × $success → $normalized（广播地址 ${targets.size} 个，端口 $ports）。" +
                        "UDP 无回执，无法确认目标是否唤醒。"
            }

            _state.update {
                it.copy(
                    sending = false,
                    sent = success > 0,
                    lastResult = lastResult,
                    localIpv4 = lan?.localIp,
                    broadcastAddress = lan?.broadcast,
                )
            }
        }
    }

    /** 释放协程作用域（ViewModel onCleared 调用） */
    fun destroy() {
        scope.cancel()
    }

    // ─── 网络地址推算 ────────────────────────────────────────────────────────

    /** 本机真实局域网地址与定向广播地址 */
    private data class LanInfo(val localIp: String, val broadcast: String)

    /**
     * 从真实网卡列表推算局域网地址。
     *
     * 优先 wlan 接口；跳过回环、link-local(169.254.) 与无广播地址的接口。
     * 多个候选时取第一个，不做猜测或补全。
     */
    private fun detectLan(): LanInfo? {
        return try {
            var wifi: LanInfo? = null
            var fallback: LanInfo? = null
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()

            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (interfaceAddress in iface.interfaceAddresses) {
                    val address = interfaceAddress.address
                    if (address !is Inet4Address || address.isLoopbackAddress) continue
                    val ip = address.hostAddress ?: continue
                    if (ip.startsWith("169.254.")) continue

                    val broadcast = interfaceAddress.broadcast?.hostAddress
                        ?: broadcastOf(address, interfaceAddress.networkPrefixLength.toInt())
                        ?: continue

                    val info = LanInfo(ip, broadcast)
                    if (iface.name.startsWith("wlan")) {
                        if (wifi == null) wifi = info
                    } else if (fallback == null) {
                        fallback = info
                    }
                }
            }
            wifi ?: fallback
        } catch (_: Throwable) {
            null
        }
    }

    /** 用 IPv4 地址与前缀长度计算定向广播地址；前缀非法时返回 null */
    private fun broadcastOf(address: Inet4Address, prefixLength: Int): String? {
        if (prefixLength !in 1..32) return null
        val mask = if (prefixLength == 32) -1 else -1 shl (32 - prefixLength)
        val octets = address.address
        val result = ByteArray(4)
        for (i in 0..3) {
            val ipOctet = octets[i].toInt() and 0xFF
            val maskOctet = (mask ushr (24 - i * 8)) and 0xFF
            result[i] = (ipOctet or maskOctet).toByte()
        }
        return runCatching { InetAddress.getByAddress(result).hostAddress }.getOrNull()
    }

    companion object {
        /** 默认广播 MAC —— 唤醒网段内所有支持 WOL 的设备 */
        const val DEFAULT_MAC = "ff:ff:ff:ff:ff:ff"

        /** 魔术包固定长度：6 + 16 × 6 = 102 字节 */
        const val PACKET_SIZE = 102

        /** WOL 标准端口 */
        const val PORT_WOL = 9

        /** 兼容端口（部分老网卡只监听 7） */
        const val PORT_WOL_ALT = 7

        /** 受限广播地址 */
        const val GLOBAL_BROADCAST = "255.255.255.255"

        /**
         * 归一化 MAC 地址为 aa:bb:cc:dd:ee:ff。
         *
         * 接受冒号、连字符、Cisco 点分与 12 位裸十六进制；非法输入返回 null，
         * 不做任何自动补零猜测。
         */
        fun normalizeMac(input: String): String? {
            val hex = input.trim()
                .replace(":", "")
                .replace("-", "")
                .replace(".", "")
                .replace(" ", "")
            if (hex.length != 12) return null
            if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            return hex.chunked(2).joinToString(":") { it.lowercase() }
        }

        /** 构建 102 字节魔术包；MAC 非法时返回 null */
        fun magicPacket(mac: String): ByteArray? {
            val normalized = normalizeMac(mac) ?: return null
            val macBytes = ByteArray(6) { index ->
                normalized.split(":")[index].toInt(16).toByte()
            }
            val packet = ByteArray(PACKET_SIZE)
            for (i in 0 until 6) packet[i] = 0xFF.toByte()
            for (repeat in 0 until 16) {
                val offset = 6 + repeat * 6
                for (i in 0 until 6) packet[offset + i] = macBytes[i]
            }
            return packet
        }
    }
}
