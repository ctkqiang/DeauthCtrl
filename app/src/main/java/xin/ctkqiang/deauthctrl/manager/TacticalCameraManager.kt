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
import kotlin.concurrent.thread
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore

/** 一次已捕获的战术照片 —— 拍摄与发送分离,用户确认后再投递 */
data class CapturedShot(
    val jpeg: ByteArray,
    val width: Int,
    val height: Int,
    val timeMs: Long,
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
    /** 相机打开代际:close() 自增,迟到回调据此识别过期会话 */
    @Volatile private var openGen = 0
    @Volatile private var closed = false

    private val _opened = MutableStateFlow(false)
    val opened: StateFlow<Boolean> = _opened

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    /** 已拍摄、待发送的照片(拍摄与发送分离) */
    private val _pendingShot = MutableStateFlow<CapturedShot?>(null)
    val pendingShot: StateFlow<CapturedShot?> = _pendingShot

    /** 实际预览分辨率 —— UI 据此动态决定显示区宽高比(不硬编码) */
    private val _previewSize = MutableStateFlow<Size?>(null)
    val previewSize: StateFlow<Size?> = _previewSize

    /** 打开相机并启动预览（Texture 由界面 AndroidView 提供） */
    @Suppress("MissingPermission")
    fun open(cameraId: String, texture: SurfaceTexture) {
        close()
        // 代际保护:close() 后迟到的 onOpened 会看到过期的 gen,直接关掉相机而不是
        // 在已释放的 Surface 上建会话(崩溃/泄漏)
        val gen = ++openGen
        closed = false
        try {
            cameraThread = HandlerThread("tac-camera").also { it.start() }
            cameraHandler = Handler(cameraThread!!.looper)
            previewTexture = texture

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val previewSize = choosePreviewSize(map, texture)
            val maxJpeg = map.getOutputSizes(ImageFormat.JPEG)!!.maxByOrNull { it.width.toLong() * it.height }!!

            _previewSize.value = previewSize
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            previewSurface = Surface(texture)
            imageReader = ImageReader.newInstance(maxJpeg.width, maxJpeg.height, ImageFormat.JPEG, 2)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    // 过期回调(期间 close/open 过):直接关闭新相机,不在旧 Surface 上建会话
                    if (closed || gen != openGen) {
                        runCatching { cam.close() }
                        return
                    }
                    device = cam
                    startSession(gen)
                }

                override fun onDisconnected(cam: CameraDevice) {
                    runCatching { cam.close() }
                    if (gen == openGen) { device = null; _opened.value = false }
                }

                override fun onError(cam: CameraDevice, error: Int) {
                    runCatching { cam.close() }
                    if (gen == openGen) {
                        device = null
                        _opened.value = false
                        _lastError.value = "相机错误 code=$error"
                    }
                }
            }, cameraHandler)
            _opened.value = true
        } catch (t: Throwable) {
            _lastError.value = t.message ?: t.javaClass.simpleName
            _opened.value = false
        }
    }

    private fun startSession(gen: Int) {
        if (closed || gen != openGen) return
        val dev = device ?: return
        val surfaces = listOf(previewSurface, imageReader?.surface).filterNotNull()
        try {
            dev.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (closed || gen != openGen) {
                        runCatching { s.close() }
                        return
                    }
                    session = s
                    val req = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(previewSurface!!)
                    }
                    s.setRepeatingRequest(req.build(), null, cameraHandler)
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    if (gen == openGen) _lastError.value = "预览会话配置失败"
                }
            }, cameraHandler)
        } catch (t: Throwable) {
            // 会话创建失败(设备断开/硬件错误):关相机只记错误,不让相机线程崩溃
            runCatching { dev.close() }
            if (gen == openGen) {
                device = null
                _opened.value = false
                _lastError.value = "预览会话创建失败: ${t.message}"
            }
        }
    }

    /** 供界面写入一次性提示 */
    fun reportError(message: String) {
        _lastError.value = message
    }

    /** 拍摄：最高分辨率 JPEG 捕获，结果存为待发送照片（不自动发送） */
    fun capture() {
        captureInternal()
    }

    /**
     * 把待发送照片投递给一组目标(自动发现 peer 或手动 IP)。
     * 结果聚合为「送达 x/n」并列出失败目标 —— 不再出现全部失败仍显示「已发送」。
     * 多目标并发发送,信号量限制 4 路;每个目标重试由 [PhotoRelayHost.sendTo] 负责。
     */
    fun sendPending(targets: List<PhotoTarget>, senderName: String) {
        val shot = _pendingShot.value ?: run {
            _lastError.value = "没有待发送的照片 —— 先拍摄"
            return
        }
        val uniqueTargets = targets.distinctBy { it.ip to it.port }
        if (uniqueTargets.isEmpty()) {
            _lastError.value = "没有可发送的目标 —— 等待同网段设备上线"
            return
        }
        thread(name = "photo-send") {
            // 共享单例:不能在这里 new PhotoRelayHost,否则与 Application 实例争抢端口
            val host = PhotoRelayHost.get(context.applicationContext)
            val semaphore = Semaphore(4)
            val results = arrayOfNulls<SendResult>(uniqueTargets.size)
            val workers = uniqueTargets.mapIndexed { index, target ->
                thread(name = "photo-send-${target.ip}") {
                    semaphore.acquire()
                    try {
                        results[index] = host.sendTo(
                            ip = target.ip,
                            jpeg = shot.jpeg,
                            senderName = senderName,
                            width = shot.width,
                            height = shot.height,
                            port = target.port,
                        )
                    } finally {
                        semaphore.release()
                    }
                }
            }
            workers.forEach { worker ->
                runCatching { worker.join(20_000) }
            }
            val finished = results.mapIndexed { index, r ->
                r ?: SendResult(uniqueTargets[index].ip, false, "发送超时")
            }
            val ok = finished.count { it.ok }
            val failed = finished.filter { !it.ok }
            _lastError.value = when {
                finished.size == 1 && ok == 1 -> finished.first().detail
                else -> buildString {
                    append("送达 $ok/${finished.size} · ${shot.jpeg.size / 1024}KB JPEG")
                    if (failed.isNotEmpty()) {
                        append(" · 失败: ")
                        append(failed.joinToString(", ") { it.ip })
                    }
                }
            }
        }
    }

    /** 丢弃待发送照片 */
    fun clearPending() {
        _pendingShot.value = null
    }

    private fun captureInternal() {
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
                    _pendingShot.value = CapturedShot(
                        jpeg = bytes,
                        width = image.width,
                        height = image.height,
                        timeMs = System.currentTimeMillis(),
                    )
                    _lastError.value = "已捕获 · " + image.width + "×" + image.height + " · " +
                        (bytes.size / 1024) + "KB —— 选择目标后发送"
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
            // 拍完恢复预览 —— 1.2s 后设备可能已断开/相机已关,回调内全部兜底
            cameraHandler?.postDelayed({
                runCatching {
                    if (closed) return@postDelayed
                    val s = session ?: return@postDelayed
                    val surface = previewSurface ?: return@postDelayed
                    val previewReq = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                    }
                    s.setRepeatingRequest(previewReq.build(), null, cameraHandler)
                }.onFailure { t ->
                    if (!closed) _lastError.value = "预览恢复失败: ${t.message}"
                }
            }, 1200)
        } catch (t: Throwable) {
            _lastError.value = t.message
        }
    }


    fun close() {
        closed = true
        openGen++
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { imageReader?.close() }
        runCatching { previewSurface?.release() }
        cameraThread?.quitSafely()
        session = null; device = null; imageReader = null
        _previewSize.value = null
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

}
