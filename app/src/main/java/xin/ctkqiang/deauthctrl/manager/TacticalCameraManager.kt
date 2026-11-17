package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** 收到的一张战术照片 —— JPEG 原始字节 + 元数据 */
data class ReceivedPhoto(
    val from: String,
    val width: Int,
    val height: Int,
    val jpeg: ByteArray,
    val receivedMs: Long,
)

/**
 * 战术相机 —— Camera2 最大分辨率捕获 + 局域网 Base64 传输
 *
 * - 不使用系统相机应用：Camera2 直控传感器，JPEG 输出取该镜头
 *   StreamConfigurationMap 支持的最高分辨率；
 * - 拍摄后 JPEG 编码为 Base64，经 TCP 发送到同网段目标（4 字节大端
 *   长度前缀 + JSON 载荷，端口 10088）；
 * - 接收端监听同一端口，解码落盘并供界面展示。
 */
class TacticalCameraManager(private val context: Context) {

    /** 相机清单：id → 名称 + 最大 JPEG 尺寸 */
    data class CameraInfo(val id: String, val name: String, val maxJpegSize: Size)

    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    val cameras: List<CameraInfo> = buildList {
        runCatching {
            cameraManager.cameraIdList.forEach { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: return@forEach
                val maxJpeg = map.getOutputSizes(ImageFormat.JPEG)
                    ?.maxByOrNull { it.width.toLong() * it.height } ?: Size(640, 480)
                val name = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "前置镜头"
                    CameraCharacteristics.LENS_FACING_BACK -> "后置镜头"
                    else -> "外部镜头"
                }
                add(CameraInfo(id, name, maxJpeg))
            }
        }
    }.sortedByDescending { it.name == "后置镜头" }

    /** 当前工作线程（Camera2 回调必须在专用 Handler 上） */
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var imageReader: ImageReader? = null

    private val _opened = MutableStateFlow(false)
    val opened: StateFlow<Boolean> = _opened

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    // ── 接收台状态 ──
    private val _receiving = MutableStateFlow(false)
    val receiving: StateFlow<Boolean> = _receiving
    private val _received = MutableStateFlow<List<ReceivedPhoto>>(emptyList())
    val received: StateFlow<List<ReceivedPhoto>> = _received
    private var serverSocket: ServerSocket? = null
    private val receiveStop = AtomicBoolean(false)

    /** 打开相机并启动预览（Texture 由界面 AndroidView 提供） */
    @Suppress("MissingPermission")
    fun open(cameraId: String, texture: SurfaceTexture) {
        close()
        try {
            cameraThread = HandlerThread("tac-camera").also { it.start() }
            cameraHandler = Handler(cameraThread!!.looper)
            previewTexture = texture

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val previewSize = choosePreviewSize(map, texture)
            val maxJpeg = map.getOutputSizes(ImageFormat.JPEG)!!.maxByOrNull { it.width.toLong() * it.height }!!

            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            previewSurface = Surface(texture)
            imageReader = ImageReader.newInstance(maxJpeg.width, maxJpeg.height, ImageFormat.JPEG, 2)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    device = cam
                    startSession()
                }

                override fun onDisconnected(cam: CameraDevice) { cam.close(); device = null; _opened.value = false }
                override fun onError(cam: CameraDevice, error: Int) {
                    cam.close(); device = null; _opened.value = false
                    _lastError.value = "相机错误 code=$error"
                }
            }, cameraHandler)
            _opened.value = true
        } catch (t: Throwable) {
            _lastError.value = t.message ?: t.javaClass.simpleName
            _opened.value = false
        }
    }

    private fun startSession() {
        val dev = device ?: return
        val surfaces = listOf(previewSurface, imageReader?.surface).filterNotNull()
        dev.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                val req = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface!!)
                }
                s.setRepeatingRequest(req.build(), null, cameraHandler)
            }

            override fun onConfigureFailed(s: CameraCaptureSession) {
                _lastError.value = "预览会话配置失败"
            }
        }, cameraHandler)
    }

    /** 供界面写入一次性提示 */
    fun reportError(message: String) {
        _lastError.value = message
    }

    /** 拍摄：最高分辨率 JPEG → Base64 → 发送到目标 IP（后台线程） */
    fun captureAndSend(targetIp: String, senderName: String) {
        val dev = device ?: run { _lastError.value = "相机未就绪"; return }
        val reader = imageReader ?: return
        val size = Size(reader.width, reader.height)

        val listener = object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(r: ImageReader) {
                val image = r.acquireLatestImage() ?: return
                try {
                    val buf: ByteBuffer = image.planes[0].buffer
                    val bytes = ByteArray(buf.remaining())
                    buf.get(bytes)
                    val b64 = Base64.getEncoder().encodeToString(bytes)
                    val payload = buildString {
                        append("{\"type\":\"photo\",\"from\":\"")
                        append(senderName.replace("\"", "\\\""))
                        append("\",\"w\":").append(image.width)
                        append(",\"h\":").append(image.height)
                        append(",\"b64\":\"").append(b64).append("\"}")
                    }
                    thread { sendOverTcp(targetIp, payload, bytes.size) }
                } catch (t: Throwable) {
                    _lastError.value = t.message
                } finally {
                    image.close()
                }
            }
        }

        try {
            val sessionNow = session ?: return
            sessionNow.stopRepeating()
            reader.setOnImageAvailableListener(listener, cameraHandler)
            val req = dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
            }
            sessionNow.capture(req.build(), null, cameraHandler)
            // 拍完恢复预览
            cameraHandler?.postDelayed({ session?.let { s ->
                val previewReq = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface!!)
                }
                s.setRepeatingRequest(previewReq.build(), null, cameraHandler)
            } }, 1200)
        } catch (t: Throwable) {
            _lastError.value = t.message
        }
    }

    /** 4 字节大端长度前缀 + UTF-8 JSON */
    private fun sendOverTcp(ip: String, json: String, jpegBytes: Int) {
        runCatching {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(ip, PHOTO_PORT), 4000)
                DataOutputStream(sock.getOutputStream()).use { out ->
                    val bytes = json.toByteArray(StandardCharsets.UTF_8)
                    out.writeInt(bytes.size)
                    out.write(bytes)
                    out.flush()
                }
            }
        }.onSuccess {
            _lastError.value = "已发送 · ${jpegBytes / 1024}KB JPEG → $ip"
        }.onFailure {
            _lastError.value = "发送失败: ${it.message}"
        }
    }

    /** 开启接收台：监听 10088，接收照片并解码入库 */
    fun startReceive() {
        if (_receiving.value) return
        receiveStop.set(false)
        thread {
            runCatching {
                serverSocket = ServerSocket(PHOTO_PORT)
                _receiving.value = true
                while (!receiveStop.get()) {
                    val client = serverSocket!!.accept()
                    thread { handleClient(client) }
                }
            }.onFailure {
                _receiving.value = false
                _lastError.value = "接收台启动失败: ${it.message}"
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
                val json = String(bytes, StandardCharsets.UTF_8)
                parseAndStore(json)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { socket.close() }
        }
    }

    /** 极简 JSON 解析（载荷结构自产自销，避免引入 JSON 依赖） */
    private fun parseAndStore(json: String) {
        fun field(key: String): String =
            Regex("\"$key\":\"(.*?)\"").find(json)?.groupValues?.get(1) ?: ""

        fun intField(key: String): Int =
            Regex("\"$key\":(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val b64 = field("b64")
        if (b64.isEmpty()) return
        val jpeg = runCatching { Base64.getDecoder().decode(b64) }.getOrNull() ?: return
        val photo = ReceivedPhoto(
            from = field("from").ifBlank { "未知" },
            width = intField("w"),
            height = intField("h"),
            jpeg = jpeg,
            receivedMs = System.currentTimeMillis(),
        )
        // 落盘：供后续查看与导出
        runCatching {
            val dir = java.io.File(context.filesDir, "photo_relay")
            dir.mkdirs()
            val f = java.io.File(dir, "rx_${photo.receivedMs}.jpg")
            f.writeBytes(jpeg)
        }
        _received.value = (listOf(photo) + _received.value).takeLast(20)
    }

    fun stopReceive() {
        receiveStop.set(true)
        runCatching { serverSocket?.close() }
        _receiving.value = false
    }

    fun close() {
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { imageReader?.close() }
        runCatching { previewSurface?.release() }
        cameraThread?.quitSafely()
        session = null; device = null; imageReader = null
        _opened.value = false
    }

    private fun choosePreviewSize(map: StreamConfigurationMap, texture: SurfaceTexture): Size {
        val sizes = map.getOutputSizes(SurfaceTexture::class.java) ?: return Size(1280, 720)
        // 预览取 16:9、≤1080p 的最大档位（保证低光帧率与画质平衡）
        return sizes.filter { it.width <= 1920 && it.height <= 1080 && it.width * 9 == it.height * 16 }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: sizes.maxByOrNull { it.width.toLong() * it.height }
            ?: Size(1280, 720)
    }

    companion object {
        const val PHOTO_PORT = 10088
        private const val MAX_PAYLOAD = 96 * 1024 * 1024
    }
}
