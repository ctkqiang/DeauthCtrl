package xin.ctkqiang.deauthctrl.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.MainActivity
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** 收到的一张战术照片 —— JPEG 原始字节 + 元数据 */
data class ReceivedPhoto(
    val id: Long,
    val from: String,
    val width: Int,
    val height: Int,
    val jpeg: ByteArray,
    val receivedMs: Long,
)

/** 在线的照片接收台(其他设备) */
data class PhotoPeer(
    val id: String,
    val name: String,
    val ip: String,
    val lastSeenMs: Long,
)

/**
 * 战术照片中继主机 —— App 级常开服务
 *
 * 与对讲机相同的发现逻辑:
 * 1. **自动发现**:UDP 9997 广播 `PHOTO|id|name`(每 2s),同时监听他人广播,
 *    相机页列出在线接收台,无需手动输入 IP;
 * 2. **接收常开**:TCP 10088 监听随应用进程持续在线,任何同网段设备随时
 *    可以投递照片,接收方无需任何操作;
 * 3. **系统通知**:收到照片即发通知(大图样式,含发送者与时间),
 *    点击通知直达相机页并打开该照片 —— 典型手机 App 体验。
 *
 * 在 [xin.ctkqiang.deauthctrl.DeauthApp.onCreate] 启动一次,进程存活期间常开。
 */
class PhotoRelayHost(private val context: Context) {

    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences("deauthctrl_prefs", Context.MODE_PRIVATE)

    /** 本机中继 ID —— 首次生成后持久化 */
    val deviceId: String = prefs.getString(KEY_ID, null) ?: run {
        val id = (1..6).map { "0123456789ABCDEF"[kotlin.random.Random.nextInt(16)] }.joinToString("")
        prefs.edit().putString(KEY_ID, id).apply()
        id
    }

    /** 本机呼号 —— 相机页进入时注入 */
    @Volatile var deviceName: String = "operator"

    private val _peers = MutableStateFlow<List<PhotoPeer>>(emptyList())
    val peers: StateFlow<List<PhotoPeer>> = _peers

    private val _received = MutableStateFlow<List<ReceivedPhoto>>(emptyList())
    val received: StateFlow<List<ReceivedPhoto>> = _received

    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online

    private val peerMap = ConcurrentHashMap<String, PhotoPeer>()

    @Volatile private var stopFlag = false
    private var beaconSocket: DatagramSocket? = null
    private var serverSocket: ServerSocket? = null
    private var started = false

    /** 启动常开中继(幂等):beacon 广播 + 发现监听 + TCP 接收 */
    fun start() {
        if (started) return
        started = true
        stopFlag = false
        createNotificationChannel()

        try {
            beaconSocket = DatagramSocket(BEACON_PORT).apply { broadcast = true; reuseAddress = true }
            serverSocket = ServerSocket(TCP_PORT)
            _online.value = true
        } catch (t: Throwable) {
            _online.value = false
            return
        }

        thread(name = "photo-beacon-send") { beaconLoop() }
        thread(name = "photo-beacon-listen") { beaconListen() }
        thread(name = "photo-tcp-accept") { acceptLoop() }
    }

    /** 相机页进入时调用:注入呼号并刷新在线状态 */
    fun touch(name: String) {
        deviceName = name
        if (!started) start()
        _online.value = serverSocket?.isBound == true
    }

    // ─── 发送:按目标 IP 投递(4 字节大端长度前缀 + JSON) ───

    /** 发送到指定 IP;返回结果文案 */
    fun sendTo(ip: String, jpeg: ByteArray, senderName: String, width: Int, height: Int): String {
        val b64 = Base64.getEncoder().encodeToString(jpeg)
        val payload = buildString {
            append("{\"type\":\"photo\",\"from\":\"")
            append(senderName.replace("\"", "\\\"").replace("\\", "\\\\"))
            append("\",\"w\":").append(width)
            append(",\"h\":").append(height)
            append(",\"b64\":\"").append(b64).append("\"}")
        }
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(ip, TCP_PORT), 4000)
                java.io.DataOutputStream(sock.getOutputStream()).use { out ->
                    val bytes = payload.toByteArray(StandardCharsets.UTF_8)
                    out.writeInt(bytes.size)
                    out.write(bytes)
                    out.flush()
                }
            }
            "已发送 · ${jpeg.size / 1024}KB → $ip"
        } catch (t: Throwable) {
            "发送失败: ${t.message}"
        }
    }

    // ─── 发现 ───

    private fun beaconLoop() {
        while (!stopFlag) {
            try {
                val msg = "PHOTO|$deviceId|${deviceName.replace("|", "_")}"
                val bytes = msg.toByteArray(StandardCharsets.UTF_8)
                beaconSocket?.send(
                    DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), BEACON_PORT),
                )
            } catch (_: Throwable) {}
            Thread.sleep(2000)
        }
    }

    private fun beaconListen() {
        val buf = ByteArray(512)
        while (!stopFlag) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                beaconSocket?.receive(packet) ?: continue
                val text = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                if (!text.startsWith("PHOTO|")) continue
                val parts = text.split("|")
                if (parts.size < 3) continue
                val id = parts[1]
                if (id == deviceId) continue
                val name = parts[2]
                val ip = packet.address?.hostAddress ?: continue
                peerMap[id] = PhotoPeer(id, name, ip, System.currentTimeMillis())
                // 清理超时 + 推送
                val now = System.currentTimeMillis()
                peerMap.entries.removeIf { now - it.value.lastSeenMs > 6000 }
                _peers.value = peerMap.values.sortedBy { it.name }
            } catch (_: Throwable) {
                if (!stopFlag) Thread.sleep(50)
            }
        }
    }

    // ─── 常开接收 ───

    private fun acceptLoop() {
        while (!stopFlag) {
            try {
                val client = serverSocket?.accept() ?: continue
                thread(name = "photo-client") { handleClient(client) }
            } catch (_: Throwable) {
                if (!stopFlag) Thread.sleep(100)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            DataInputStream(socket.getInputStream()).use { input ->
                val len = input.readInt()
                if (len <= 0 || len > MAX_PAYLOAD) return
                val bytes = ByteArray(len)
                input.readFully(bytes)
                parseAndStore(String(bytes, StandardCharsets.UTF_8))
            }
        } catch (_: Exception) {
        } finally {
            runCatching { socket.close() }
        }
    }

    /** 极简 JSON 解析(载荷结构自产自销) */
    private fun parseAndStore(json: String) {
        fun field(key: String): String =
            Regex("\"$key\":\"(.*?)\"").find(json)?.groupValues?.get(1) ?: ""

        fun intField(key: String): Int =
            Regex("\"$key\":(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val b64 = field("b64")
        if (b64.isEmpty()) return
        val jpeg = runCatching { Base64.getDecoder().decode(b64) }.getOrNull() ?: return
        val photo = ReceivedPhoto(
            id = System.currentTimeMillis(),
            from = field("from").ifBlank { "未知" },
            width = intField("w"),
            height = intField("h"),
            jpeg = jpeg,
            receivedMs = System.currentTimeMillis(),
        )
        // 落盘
        runCatching {
            val dir = java.io.File(appContext.filesDir, "photo_relay")
            dir.mkdirs()
            java.io.File(dir, "rx_${photo.id}.jpg").writeBytes(jpeg)
        }
        _received.value = (listOf(photo) + _received.value).takeLast(30)
        notifyPhoto(photo)
    }

    /** 系统通知:大图样式,点击直达相机页并打开该照片 */
    private fun notifyPhoto(photo: ReceivedPhoto) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return // 未授权通知:照片仍已接收与保存,仅静默
            }
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_PHOTO_ID, photo.id)
            }
            val pi = PendingIntent.getActivity(
                appContext,
                photo.id.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val bmp = runCatching { BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size) }.getOrNull()
            val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("战术照片 · 来自 ${photo.from}")
                .setContentText("${photo.width}×${photo.height} · ${photo.jpeg.size / 1024}KB · 点击查看")
                .setAutoCancel(true)
                .setContentIntent(pi)
            if (bmp != null) {
                builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp))
            }
            getNotificationManager().notify(photo.id.toInt(), builder.build())
        } catch (_: Throwable) {}
    }

    /** 取某张照片(通知点击后相机页定位用) */
    fun photoById(id: Long): ReceivedPhoto? = _received.value.firstOrNull { it.id == id }

    private fun getNotificationManager(): NotificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "战术照片", NotificationManager.IMPORTANCE_DEFAULT)
        getNotificationManager().createNotificationChannel(ch)
    }

    companion object {
        const val BEACON_PORT = 9997
        const val TCP_PORT = 10088
        const val EXTRA_PHOTO_ID = "photo_relay_photo_id"
        private const val KEY_ID = "photo_relay_id"
        private const val CHANNEL_ID = "photo_relay_channel"
        private const val MAX_PAYLOAD = 96 * 1024 * 1024
    }
}
