package xin.ctkqiang.deauthctrl.manager

import android.os.Build
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import kotlin.concurrent.thread

/**
 * FileTransfer 局域网设备信息
 *
 * @property ip 设备 IPv4 地址
 * @property name 设备名（Build.MODEL）
 * @property lastSeen 最后发现时间戳
 */
data class SendPeer(val ip: String, val name: String, val lastSeen: Long = System.currentTimeMillis())

/**
 * FileTransfer 风格局域网文件传输管理器
 *
 * 实现同一 WiFi 网络内的设备自动发现 + TCP 点对点文件传输。
 * 协议设计简洁：UDP 广播发现 + TCP 单连接传文件。
 *
 * ## 协议
 * 1. **发现阶段**: UDP 9998 端口广播 `DEAUTHCTRL_FT:设备名:IP`（每 2 秒）
 * 2. **连接阶段**: 发送方 TCP 连接接收方 9997 端口
 * 3. **传输头部**: [文件名长度 INT32] [文件大小 INT64] [文件名 UTF-8 字节]
 * 4. **传输内容**: 原始文件字节流（8KB 缓冲区）
 *
 * ## 线程模型
 * - 发现广播: kotlin.concurrent.thread (while 循环每 2s)
 * - 发现监听: kotlin.concurrent.thread (DatagramSocket.receive 阻塞)
 * - 连接接受: 协程 Dispatchers.IO (ServerSocket.accept 阻塞)
 * - 文件接收: 每个客户端连接在独立协程中处理
 * - 文件发送: 同步阻塞（调用方线程），5 秒连接超时
 *
 * ## 安全考虑
 * - 仅限同一局域网（192.168.x.x），不经过外网
 * - 无认证/加密（局域网信任模型）
 * - 接收文件存储在 app 私有目录，不暴露给外部
 */
class FileTransferManager {

    /** 本机设备名，默认 Build.MODEL */
    var deviceName: String = Build.MODEL

    /** 服务运行状态 */
    @Volatile var isRunning = false; private set

    /** 已发现的局域网设备表 */
    val peers = mutableMapOf<String, SendPeer>()

    /** 错误回调 */
    var onError: ((String) -> Unit)? = null

    private var discoverSocket: DatagramSocket? = null
    private var serverSocket: ServerSocket? = null
    private var receiveJob: Job? = null

    /** 文件接收回调（主线程），参数为接收到的文件 */
    var onFileReceived: ((File) -> Unit)? = null

    /** 接收文件存储目录 */
    private var receiveDir: File? = null

    /** 获取本机局域网 IPv4 地址 */
    val localIpAddress: String? get() = fetchLocalIp()

    /**
     * 启动文件传输服务
     *
     * 创建 UDP 发现 Socket（9998）+ TCP 服务 Socket（9997），
     * 启动发现广播线程、发现监听线程、连接接受协程。
     *
     * @param dir 接收文件的存储目录
     * @return true 启动成功，false 端口被占用或权限不足
     */
    fun start(dir: File): Boolean {
        receiveDir = dir; dir.mkdirs()
        try {
            discoverSocket = DatagramSocket(null).apply { bind(InetSocketAddress(9998)); broadcast = true; reuseAddress = true }
            serverSocket = ServerSocket(9997)
            isRunning = true
            thread(name = "ft-discover-send") { discoverLoop() }
            thread(name = "ft-discover-listen") { discoverListen() }
            receiveJob = CoroutineScope(Dispatchers.IO).launch { acceptLoop() }
        } catch (e: Exception) { stop(); return false }
        return true
    }

    /**
     * 发送文件到指定 IP 的对等设备
     *
     * TCP 连接 peerIp:9997 → 发送头部 → 流式传输文件内容 → 关闭连接。
     * 连接超时 5 秒。
     *
     * @param file 要发送的文件
     * @param peerIp 目标设备 IP
     * @return true 发送成功
     */
    fun sendFile(file: File, peerIp: String): Boolean {
        try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(peerIp, 9997), 5000)
                val out = DataOutputStream(sock.getOutputStream())
                val nameBytes = file.name.toByteArray(Charsets.UTF_8)
                out.writeInt(nameBytes.size); out.writeLong(file.length()); out.write(nameBytes)
                file.inputStream().use { it.copyTo(out) }; out.flush()
            }
            return true
        } catch (_: Exception) { return false }
    }

    /**
     * TCP 连接接受循环
     *
     * ServerSocket.accept() 阻塞等待 → 每个客户端在独立协程中处理：
     * 读取头部（文件名长度 + 文件大小 + 文件名）→ 流式写入文件 →
     * 回调 onFileReceived（主线程）。
     */
    private suspend fun acceptLoop() {
        while (isRunning) {
            try {
                val client = serverSocket?.accept() ?: break
                withContext(Dispatchers.IO) {
                    try {
                        val input = DataInputStream(client.getInputStream())
                        val nameLen = input.readInt(); val fileSize = input.readLong()
                        val nameBytes = ByteArray(nameLen); input.readFully(nameBytes)
                        val fileName = String(nameBytes, Charsets.UTF_8)
                        val outFile = File(receiveDir, fileName)
                        FileOutputStream(outFile).use { fos ->
                            val buf = ByteArray(8192); var remain = fileSize
                            while (remain > 0) {
                                val n = input.read(buf, 0, minOf(buf.size.toLong(), remain).toInt())
                                if (n < 0) break; fos.write(buf, 0, n); remain -= n
                            }
                        }
                        withContext(Dispatchers.Main) { onFileReceived?.invoke(outFile) }
                    } catch (_: Exception) {} finally { client.close() }
                }
            } catch (_: Exception) {}
        }
    }

    /** UDP 广播本机设备名 + IP（每 2 秒） */
    private fun discoverLoop() {
        val myIp = fetchLocalIp() ?: return
        val msg = "DEAUTHCTRL_FT|$deviceName|$myIp".toByteArray()
        while (isRunning) {
            try { discoverSocket?.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), 9998)) } catch (_: Exception) {}
            Thread.sleep(2000)
        }
    }

    /** 监听其他设备的 UDP 广播 */
    private fun discoverListen() {
        val buf = ByteArray(256)
        while (isRunning) {
            try {
                val p = DatagramPacket(buf, buf.size); discoverSocket?.receive(p)
                val d = String(p.data, 0, p.length)
                if (d.startsWith("DEAUTHCTRL_FT|")) {
                    val parts = d.removePrefix("DEAUTHCTRL_FT|").split("|")
                    if (parts.size >= 2) { val n = parts[0]; val ip = parts[1]; if (ip != fetchLocalIp()) peers[ip] = SendPeer(ip, n) }
                }
            } catch (_: Exception) {}
        }
    }

    /** 停止所有服务 */
    fun stop() {
        isRunning = false; receiveJob?.cancel()
        try { discoverSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        peers.clear()
    }

    /** 获取本机局域网 IPv4 地址 */
    private fun fetchLocalIp(): String? {
        try { NetworkInterface.getNetworkInterfaces().toList().forEach { i -> i.inetAddresses.toList().forEach { a -> if (a is Inet4Address && !a.isLoopbackAddress && a.hostAddress?.startsWith("192.168.") == true) return a.hostAddress } } } catch (_: Exception) {}
        return null
    }
}
