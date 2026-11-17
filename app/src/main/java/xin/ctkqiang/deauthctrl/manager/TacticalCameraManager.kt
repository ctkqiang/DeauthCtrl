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

    /** 拍摄：最高分辨率 JPEG 捕获，结果存为待发送照片（不自动发送） */
    fun capture() {
        captureInternal()
    }

    /** 把待发送照片投递给一组目标 IP（Base64 编码在发送线程执行） */
    fun sendPending(targetIps: List<String>, senderName: String) {
        val shot = _pendingShot.value ?: run {
            _lastError.value = "没有待发送的照片 —— 先拍摄"
            return
        }
        thread {
            val host = PhotoRelayHost(context.applicationContext)
            val results = targetIps.map { ip ->
                host.sendTo(ip, shot.jpeg, senderName, shot.width, shot.height)
            }
            _lastError.value = when {
                results.isEmpty() -> "没有可发送的目标"
                results.size == 1 -> results.first()
                else -> "已发送 · ${shot.jpeg.size / 1024}KB JPEG → ${results.size} 台设备"
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


    fun close() {
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
