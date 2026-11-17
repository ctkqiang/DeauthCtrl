package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.math.min

/**
 * 数字夜视 —— Camera2 高感光捕获 + 逐像素增益 + 绿色映射
 *
 * 不做 OpenCV：那会为应用增加 ~100MB 原生库负担，而夜视的核心
 * （暗部增益 + 单色映射）用 Camera2 的 YUV 帧 + 逐像素处理即可实现：
 * 1. 相机以最高 ISO 上限 / 自动曝光捕获 YUV_420_888；
 * 2. 亮度通道按用户增益曲线放大（1x-8x）；
 * 3. 映射为夜视绿（G 主通道 + 少量 R/B），模拟像增强管荧光屏观感；
 * 4. 640×480 软件处理约 8-10fps，稳定优先。
 *
 * 说明：消费级手机无红外补光灯，此为数字增强而非真红外热成像；
 * 有红外补光的机型（部分国产手机红外 LED）会显著提升效果。
 */
class NightVisionManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /** 可用镜头（优先后置） */
    data class Cam(val id: String, val name: String)

    val cameras: List<Cam> = buildList {
        runCatching {
            cameraManager.cameraIdList.forEach { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val name = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "前置镜头"
                    CameraCharacteristics.LENS_FACING_BACK -> "后置镜头"
                    else -> "外部镜头"
                }
                add(Cam(id, name))
            }
        }
    }.sortedByDescending { it.name == "后置镜头" }

    /** 当前增强帧（Compose Image 直接显示） */
    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** 增益 1x-8x，调用方滑杆控制 */
    @Volatile var gain: Float = 4f
    @Volatile private var stopFlag = false
    /** 传感器方向(通常后置 90°、前置 270°) —— 输出画面需按此旋转 */
    @Volatile private var sensorOrientation = 90
    /** 镜头朝向 —— 前置需要水平镜像 */
    @Volatile private var lensFacing = CameraCharacteristics.LENS_FACING_BACK

    private var threadRef: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var processThread: Thread? = null
    private var detectThread: Thread? = null

    /** OpenCV 目标检测器(人脸 + 人体) */
    val detector = ThreatDetector(context)

    /** 最新检测报告 —— UI 据此叠加红框/涟漪/触觉 */
    private val _threat = MutableStateFlow<ThreatReport?>(null)
    val threat: StateFlow<ThreatReport?> = _threat

    /** 最新增强帧(检测线程消费,拿帧即置空实现丢帧策略) */
    private val pendingFrame = java.util.concurrent.atomic.AtomicReference<Bitmap?>(null)

    @Suppress("MissingPermission")
    fun start(cameraId: String) {
        stop()
        try {
            threadRef = HandlerThread("nv-camera").also { it.start() }
            handler = Handler(threadRef!!.looper)
            // 动态选择相机支持的 YUV 尺寸(硬编码 640×480 在不支持的机型上会直接抛异常)
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
                ?: CameraCharacteristics.LENS_FACING_BACK
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty()
            if (yuvSizes.isEmpty()) {
                _error.value = "该镜头不支持 YUV 输出,夜视不可用"
                return
            }
            val yuvSize = yuvSizes
                .filter { it.width <= PREVIEW_W && it.height <= PREVIEW_H }
                .maxByOrNull { it.width.toLong() * it.height }
                ?: yuvSizes.minByOrNull { it.width.toLong() * it.height }!!
            reader = ImageReader.newInstance(yuvSize.width, yuvSize.height, ImageFormat.YUV_420_888, 3)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    device = cam
                    val surfaces = listOf(reader!!.surface)
                    cam.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            session = s
                            val chars = cameraManager.getCameraCharacteristics(cam.id)
                            // 夜视曝光策略:AE 自动 + 最大正曝光补偿 + 最低目标帧率。
                            // 低帧率让 AE 敢用长曝光,暗部细节才出得来;
                            // 固定 ISO 反而会与 AE 冲突并压制暗光表现。
                            val evMax = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.upper ?: 0
                            val fpsMin = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                                ?.minByOrNull { it.upper }
                            val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(reader!!.surface)
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evMax)
                                if (fpsMin != null) {
                                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsMin)
                                }
                            }
                            s.setRepeatingRequest(req.build(), null, handler)
                            _running.value = true
                        }

                        override fun onConfigureFailed(s: CameraCaptureSession) {
                            _error.value = "夜视会话配置失败"
                        }
                    }, handler)
                }

                override fun onDisconnected(cam: CameraDevice) { cam.close(); _running.value = false }
                override fun onError(cam: CameraDevice, error: Int) {
                    cam.close(); _running.value = false; _error.value = "相机错误 $error"
                }
            }, handler)

            // 帧处理线程：YUV → 增益 → 绿色 Bitmap
            stopFlag = false
            processThread = thread(name = "nv-process") {
                while (!stopFlag) {
                    val r = reader
                    if (r == null) { Thread.sleep(8); continue }
                    val image: Image? = try { r.acquireLatestImage() } catch (_: Exception) { null }
                    if (image == null) { Thread.sleep(8); continue }
                    try {
                        processFrame(image)
                    } catch (_: Exception) {
                    } finally {
                        image.close()
                    }
                }
            }

            // 目标检测线程(自动启动):消费最新增强帧,丢帧策略保持实时性。
            // OpenCV 不可用时 detect 内部静默返回空报告,画面增强不受影响。
            detectThread = thread(name = "nv-detect") {
                while (!stopFlag) {
                    val bmp = pendingFrame.getAndSet(null)
                    if (bmp == null) { Thread.sleep(25); continue }
                    try {
                        _threat.value = detector.detect(bmp)
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (t: Throwable) {
            _error.value = t.message ?: t.javaClass.simpleName
        }
    }

    /**
     * YUV_420_888 → 增益 → 夜视绿 Bitmap。
     * 只处理亮度平面（Y），忽略色度（夜视本为单色），
     * 用最近邻采样把 Y 放大到全分辨率，再按绿色通道映射。
     */
    private fun processFrame(image: Image) {
        val w = image.width
        val h = image.height
        val yPlane = image.planes[0]
        val yBuffer: ByteBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        val gain = this.gain.coerceIn(1f, 8f)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)

        // 提取 Y 行数据并缓存（行对齐可能带来 padding）
        val yStride = yRowStride
        var idx = 0
        for (row in 0 until h) {
            val rowStart = row * yStride
            for (col in 0 until w) {
                val y = yBuffer.get(rowStart + col * yPixelStride).toInt() and 0xFF
                // 增益曲线：暗部放大倍数更高（像增强管的非线性响应）
                val boosted = min(255f, y * gain).toInt()
                val g = boosted
                val r = (boosted * 0.32f).toInt()
                val b = (boosted * 0.45f).toInt()
                pixels[idx++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)

        // 按传感器方向旋转画面(否则横倒/颠倒);前置镜头额外水平镜像
        if (sensorOrientation != 0 || lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(sensorOrientation.toFloat())
            if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                matrix.postScale(-1f, 1f)
            }
            val rotated = Bitmap.createBitmap(out, 0, 0, w, h, matrix, true)
            if (rotated !== out) out.recycle()
            _frame.value = rotated
            pendingFrame.set(rotated)
        } else {
            _frame.value = out
            pendingFrame.set(out)
        }
    }

    fun stop() {
        stopFlag = true
        _running.value = false
        _threat.value = null
        pendingFrame.set(null)
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { reader?.close() }
        threadRef?.quitSafely()
        session = null; device = null; reader = null
    }

    companion object {
        const val PREVIEW_W = 640
        const val PREVIEW_H = 480
    }
}
