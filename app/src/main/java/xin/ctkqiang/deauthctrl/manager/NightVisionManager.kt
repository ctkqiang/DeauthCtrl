package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
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
import android.os.SystemClock
import android.util.Size
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.min

/** 一个已确认/待确认的跟踪目标(归一化坐标) */
data class NightTrack(
    val id: Long,
    val kind: String, // "face" | "human"
    val score: Float,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    /** 距最后一次命中的毫秒数 —— UI 用于 700ms 淡出 */
    val lastSeenAgoMs: Long,
    val confirmed: Boolean,
)

/** 检测统计条数据 —— Hz/耗时/计数都来自实际检测线程 */
data class DetectionStats(
    val haarOk: Boolean = false,
    val hogOk: Boolean = false,
    val hz: Float = 0f,
    val inferenceMs: Long = 0,
    val faces: Int = 0,
    val humans: Int = 0,
    val status: String = "加载中",
    val error: String? = null,
)

/**
 * 数字夜视 —— Camera2 高感光捕获 + 逐像素增益 + 绿色映射 + 目标跟踪
 *
 * 处理链路:
 * 1. 相机以最高 ISO 上限 / 自动曝光捕获 YUV_420_888;
 * 2. **显示图**: Y 通道按 gain(1x-8x)放大并映射夜视绿(允许硬截断,只为观感);
 * 3. **检测图**: 同一 Y 通道、同一旋转矩阵,但不做增益(线性亮度),长边 ≤480,
 *    交给 [ThreatDetector] —— 增益后的绿色图会削掉亮部梯度,Haar/HOG 直接失效;
 * 4. [ThreatTracker]: IoU>0.3 匹配、2/3 次命中确认、700ms 保持、EMA 0.45 平滑;
 * 5. 固定约 6Hz 检测,保证两次检测之间框持续可见。
 *
 * 线程代际:每次 start 分配 generation,旧线程的循环条件立即失效;stop 会 interrupt 并 join。
 * 缓冲复用:两套 FrameSlot 交替使用,避免每帧分配 IntArray + Bitmap。
 *
 * 平台限制:消费级手机无红外补光/热成像,这是数字增益而非真实夜视;
 * HOG 是白天全身模型,低光召回有限;检测分数不是校准概率。
 */
class NightVisionManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

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

    /** 已确认的跟踪目标 —— UI 只按它绘框,不再被单帧漏检清空 */
    private val _tracks = MutableStateFlow<List<NightTrack>>(emptyList())
    val tracks: StateFlow<List<NightTrack>> = _tracks

    /** 检测统计条(Hz/耗时/人数/不可用原因) */
    private val _stats = MutableStateFlow(DetectionStats())
    val stats: StateFlow<DetectionStats> = _stats

    /** 增益 1x-8x，调用方滑杆控制 */
    @Volatile var gain: Float = 4f

    /** 传感器方向(通常后置 90°、前置 270°) —— 输出画面需按此旋转 */
    @Volatile private var sensorOrientation = 90

    /** 镜头朝向 —— 前置需要水平镜像 */
    @Volatile private var lensFacing = CameraCharacteristics.LENS_FACING_BACK

    private val lifecycleLock = Any()
    @Volatile private var generation = 0
    @Volatile private var stopFlag = false
    @Volatile private var detectingSlot = -1

    private var threadRef: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var processThread: Thread? = null
    private var detectThread: Thread? = null

    /** OpenCV 目标检测器(人脸 + 人体) —— 重初始化在检测线程首次调用时发生 */
    val detector = ThreatDetector(appContext)

    /** 检测器状态(Loading / Ready / Unavailable(reason)) */
    val detectorStatus: StateFlow<DetectorStatus> = detector.status

    /** 最新增强帧交给检测线程;附加槽位号用于双缓冲避让 */
    private val pendingGray = AtomicReference<Bitmap?>(null)
    private val pendingSlot = AtomicInteger(-1)

    /** 两套复用缓冲 —— 交替写入,UI 与检测线程不会读到正在写的同一张 */
    private val slots = arrayOfNulls<FrameSlot>(2)
    private var lastWriteSlot = 1

    // ─── 跟踪器状态 ───
    private val trackLock = Any()
    private val trackStates = mutableListOf<TrackState>()
    private var nextTrackId = 1L

    private class TrackState(
        val id: Long,
        var kind: String,
        var score: Float,
        var x: Float,
        var y: Float,
        var w: Float,
        var h: Float,
        var lastSeen: Long,
        var hits: Int,
        var confirmed: Boolean,
    )

    @Suppress("MissingPermission")
    fun start(cameraId: String) {
        synchronized(lifecycleLock) {
            stopLocked()
            val gen = ++generation
            stopFlag = false
            _error.value = null
            _tracks.value = emptyList()
            _stats.value = DetectionStats(status = "加载中")
            try {
                val cameraThread = HandlerThread("nv-camera").also { it.start() }
                threadRef = cameraThread
                val cameraHandler = Handler(cameraThread.looper)
                handler = cameraHandler

                // 动态选择相机支持的 YUV 尺寸(硬编码 640×480 在不支持的机型上会直接抛异常)
                val chars = cameraManager.getCameraCharacteristics(cameraId)
                sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
                    ?: CameraCharacteristics.LENS_FACING_BACK
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty()
                if (yuvSizes.isEmpty()) {
                    _error.value = "该镜头不支持 YUV 输出,夜视不可用"
                    stopLocked()
                    return
                }
                val yuvSize = yuvSizes
                    .filter { it.width <= PREVIEW_W && it.height <= PREVIEW_H }
                    .maxByOrNull { it.width.toLong() * it.height }
                    ?: yuvSizes.minByOrNull { it.width.toLong() * it.height }!!
                val frameReader = ImageReader.newInstance(yuvSize.width, yuvSize.height, ImageFormat.YUV_420_888, 3)
                reader = frameReader

                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(cam: CameraDevice) {
                        if (gen != generation) {
                            cam.close()
                            return
                        }
                        device = cam
                        cam.createCaptureSession(listOf(frameReader.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                if (gen != generation) {
                                    s.close()
                                    return
                                }
                                session = s
                                val charsNow = cameraManager.getCameraCharacteristics(cam.id)
                                // 夜视曝光策略:AE 自动 + 最大正曝光补偿 + 最低目标帧率。
                                // 低帧率让 AE 敢用长曝光,暗部细节才出得来;
                                // 固定 ISO 反而会与 AE 冲突并压制暗光表现。
                                val evMax = charsNow.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.upper ?: 0
                                val fpsMin = charsNow.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                                    ?.minByOrNull { it.upper }
                                val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                    addTarget(frameReader.surface)
                                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                    set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evMax)
                                    if (fpsMin != null) {
                                        set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsMin)
                                    }
                                }
                                s.setRepeatingRequest(req.build(), null, cameraHandler)
                                _running.value = true
                            }

                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                _error.value = "夜视会话配置失败"
                                _running.value = false
                            }
                        }, cameraHandler)
                    }

                    override fun onDisconnected(cam: CameraDevice) {
                        cam.close()
                        if (gen == generation) _running.value = false
                    }

                    override fun onError(cam: CameraDevice, error: Int) {
                        cam.close()
                        if (gen == generation) {
                            _running.value = false
                            _error.value = "相机错误 $error"
                        }
                    }
                }, cameraHandler)

                // 帧处理线程：YUV → 增益绿显示图 + 未增益灰度检测图
                processThread = thread(name = "nv-process") { processLoop(gen, frameReader) }
                // 目标检测线程:固定约 6Hz,消费最新检测图
                detectThread = thread(name = "nv-detect") { detectLoop(gen) }
            } catch (t: Throwable) {
                _error.value = t.message ?: t.javaClass.simpleName
                stopLocked()
            }
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            stopLocked()
        }
    }

    private fun stopLocked() {
        stopFlag = true
        generation++
        _running.value = false
        pendingGray.set(null)
        pendingSlot.set(-1)
        _tracks.value = emptyList()
        _stats.value = DetectionStats(status = "已停止")
        _frame.value = null

        val oldProcess = processThread
        val oldDetect = detectThread
        processThread = null
        detectThread = null

        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { reader?.close() }
        runCatching { threadRef?.quitSafely() }
        session = null
        device = null
        reader = null
        threadRef = null
        handler = null

        if (oldProcess != null && oldProcess !== Thread.currentThread()) {
            runCatching { oldProcess.interrupt() }
            runCatching { oldProcess.join(500) }
        }
        // 检测线程跑的是不可中断的 CPU 密集 OpenCV,interrupt 无效,只能等它自然出循环;
        // 单次 detect 约 200-400ms,慢设备可达 1s+ —— 有界等待到线程真正退出,
        // 防止旧线程跨代继续用同一个 ThreatDetector 与回写 _stats
        if (oldDetect != null && oldDetect !== Thread.currentThread()) {
            runCatching { oldDetect.interrupt() }
            var waited = 0L
            while (oldDetect.isAlive && waited < 2500L) {
                Thread.sleep(100)
                waited += 100
            }
        }
    }

    /**
     * 帧循环:每次 start 捕获自己的 generation,旧线程醒来自动退出,
     * 不会挂到新 reader 上继续处理。
     */
    private fun processLoop(gen: Int, frameReader: ImageReader) {
        while (gen == generation && !stopFlag && !Thread.currentThread().isInterrupted) {
            val image: Image? = try {
                frameReader.acquireLatestImage()
            } catch (_: Exception) {
                null
            }
            if (image == null) {
                try {
                    Thread.sleep(8)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }
            try {
                processFrame(image, gen)
            } catch (_: Exception) {
            } finally {
                image.close()
            }
        }
    }

    private fun detectLoop(gen: Int) {
        var lastDetectionAt = 0L
        var hz = 0f
        while (gen == generation && !stopFlag && !Thread.currentThread().isInterrupted) {
            val gray = pendingGray.getAndSet(null)
            val slotIndex = pendingSlot.getAndSet(-1)
            if (gray == null) {
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastDetectionAt < DETECT_INTERVAL_MS) continue

            detectingSlot = slotIndex
            try {
                val frameResult = detector.detect(gray)
                if (gen != generation) break
                val at = SystemClock.elapsedRealtime()
                val tracks = updateTracks(frameResult.boxes, at)
                _tracks.value = tracks
                if (lastDetectionAt > 0L) {
                    val instantHz = 1000f / (at - lastDetectionAt).coerceAtLeast(1L).toFloat()
                    hz = if (hz == 0f) instantHz else hz * 0.8f + instantHz * 0.2f
                }
                lastDetectionAt = at
                _stats.value = DetectionStats(
                    haarOk = frameResult.haarOk,
                    hogOk = frameResult.hogOk,
                    hz = hz,
                    inferenceMs = frameResult.inferenceMs,
                    faces = tracks.count { it.kind == "face" && it.confirmed },
                    humans = tracks.count { it.kind == "human" && it.confirmed },
                    status = when (val s = detectorStatus.value) {
                        DetectorStatus.Ready -> "就绪"
                        DetectorStatus.Loading -> "加载中"
                        is DetectorStatus.Unavailable -> s.reason
                    },
                    error = frameResult.error,
                )
            } catch (_: Throwable) {
                // 单帧检测异常不终止线程
            } finally {
                detectingSlot = -1
            }
        }
        _stats.value = DetectionStats(status = if (detectorStatus.value is DetectorStatus.Unavailable) {
            (detectorStatus.value as DetectorStatus.Unavailable).reason
        } else "已停止")
    }

    /**
     * YUV_420_888 → 显示绿图 + 未增益灰度检测图。
     *
     * - 显示图允许硬截断(gain 4 时亮部削顶只影响观感);
     * - 检测图是线性 Y,不做任何增益,保留 Haar/HOG 需要的梯度;
     * - 两张图使用同一个旋转/镜像矩阵,归一化坐标与显示画面严格对齐;
     * - 复用 FrameSlot 中的 IntArray/Bitmap/Canvas,长期运行内存平稳。
     */
    private fun processFrame(image: Image, gen: Int) {
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return
        val yPlane = image.planes[0]
        val yBuffer: ByteBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val gain = this.gain.coerceIn(1f, 8f)

        val swap = sensorOrientation % 180 != 0
        val rotate = sensorOrientation != 0 || lensFacing == CameraCharacteristics.LENS_FACING_FRONT
        val outW = if (swap) h else w
        val outH = if (swap) w else h

        val detScale = min(1f, DETECT_LONG_EDGE / maxOf(w, h).toFloat())
        val detW = (w * detScale).toInt().coerceAtLeast(1)
        val detH = (h * detScale).toInt().coerceAtLeast(1)
        val detOutW = if (swap) detH else detW
        val detOutH = if (swap) detW else detH

        // 选一个检测线程没在读取的槽位(双缓冲)
        var slotIndex = 1 - lastWriteSlot
        if (slotIndex == detectingSlot) slotIndex = 1 - slotIndex
        lastWriteSlot = slotIndex
        val slot = ensureSlot(slotIndex, w, h, outW, outH, detW, detH, detOutW, detOutH)

        // ── 显示图:Y × gain → 夜视绿 ──
        val greenPixels = slot.greenPixels
        var idx = 0
        for (row in 0 until h) {
            val rowStart = row * yRowStride
            for (col in 0 until w) {
                val y = yBuffer.get(rowStart + col * yPixelStride).toInt() and 0xFF
                // 增益曲线:暗部放大更高(像增强管非线性响应);显示允许截断
                val boosted = min(255f, y * gain).toInt()
                val g = boosted
                val r = (boosted * 0.32f).toInt()
                val b = (boosted * 0.45f).toInt()
                greenPixels[idx++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        slot.greenSource.setPixels(greenPixels, 0, w, 0, 0, w, h)
        drawRotated(slot.greenSource, slot.greenTarget)

        // ── 检测图:线性 Y(无增益),长边 ≤480 ──
        val grayPixels = slot.grayPixels
        idx = 0
        for (row in 0 until detH) {
            val sy = (row / detScale).toInt().coerceIn(0, h - 1)
            val rowStart = sy * yRowStride
            for (col in 0 until detW) {
                val sx = (col / detScale).toInt().coerceIn(0, w - 1)
                val y = yBuffer.get(rowStart + sx * yPixelStride).toInt() and 0xFF
                grayPixels[idx++] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
            }
        }
        slot.graySource.setPixels(grayPixels, 0, detW, 0, 0, detW, detH)
        drawRotated(slot.graySource, slot.grayTarget)

        if (gen != generation || stopFlag) return
        _frame.value = slot.greenTarget
        pendingSlot.set(slotIndex)
        pendingGray.set(slot.grayTarget)
    }

    /**
     * 用同一个旋转/镜像矩阵把源位图绘制到复用目标位图。
     * 90° 倍数旋转是精确映射,不会引入插值模糊。
     */
    private fun drawRotated(source: Bitmap, target: Bitmap) {
        val canvas = Canvas(target)
        if (sensorOrientation == 0 && lensFacing != CameraCharacteristics.LENS_FACING_FRONT) {
            canvas.drawBitmap(source, 0f, 0f, null)
            return
        }
        val matrix = Matrix()
        matrix.postRotate(sensorOrientation.toFloat())
        if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            matrix.postScale(-1f, 1f)
        }
        val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        matrix.mapRect(bounds)
        matrix.postTranslate(-bounds.left, -bounds.top)
        canvas.drawBitmap(source, matrix, null)
    }

    private fun ensureSlot(
        index: Int,
        srcW: Int,
        srcH: Int,
        outW: Int,
        outH: Int,
        detW: Int,
        detH: Int,
        detOutW: Int,
        detOutH: Int,
    ): FrameSlot {
        val existing = slots[index]
        if (existing != null && existing.matches(srcW, srcH, outW, outH, detW, detH, detOutW, detOutH)) {
            return existing
        }
        // 尺寸变化(切镜头)时直接换新槽位;旧位图可能仍被 Compose 绘制,
        // 交给 GC 回收而不是立即 recycle,避免 "trying to use a recycled bitmap"
        return FrameSlot(srcW, srcH, outW, outH, detW, detH, detOutW, detOutH).also { slots[index] = it }
    }

    /**
     * 跟踪器:IoU>0.3 匹配同类型目标;新 track 需 2 次命中才发布;
     * 丢失后保持 700ms 淡出;坐标 EMA alpha=0.45。
     */
    private fun updateTracks(boxes: List<DetectionBox>, now: Long): List<NightTrack> {
        synchronized(trackLock) {
            val matchedIds = mutableSetOf<Long>()
            boxes.forEach { box ->
                var bestIndex = -1
                var bestIou = IOU_MATCH_THRESHOLD
                trackStates.forEachIndexed { index, track ->
                    if (track.kind != box.kind) return@forEachIndexed
                    val overlap = iou(track, box)
                    if (overlap > bestIou) {
                        bestIou = overlap
                        bestIndex = index
                    }
                }
                if (bestIndex >= 0) {
                    val track = trackStates[bestIndex]
                    matchedIds.add(track.id)
                    track.x = track.x * (1f - EMA_ALPHA) + box.x * EMA_ALPHA
                    track.y = track.y * (1f - EMA_ALPHA) + box.y * EMA_ALPHA
                    track.w = track.w * (1f - EMA_ALPHA) + box.w * EMA_ALPHA
                    track.h = track.h * (1f - EMA_ALPHA) + box.h * EMA_ALPHA
                    track.score = track.score * 0.6f + box.score * 0.4f
                    track.lastSeen = now
                    track.hits++
                    if (track.hits >= CONFIRM_HITS) track.confirmed = true
                } else {
                    val newTrack = TrackState(
                        id = nextTrackId++,
                        kind = box.kind,
                        score = box.score,
                        x = box.x, y = box.y, w = box.w, h = box.h,
                        lastSeen = now,
                        hits = 1,
                        confirmed = false,
                    )
                    trackStates.add(newTrack)
                    matchedIds.add(newTrack.id)
                }
            }
            // 未命中的 track:命中计数衰减,超过 700ms 移除(淡出窗口)
            val iterator = trackStates.iterator()
            while (iterator.hasNext()) {
                val track = iterator.next()
                if (track.id !in matchedIds) {
                    track.hits = (track.hits - 1).coerceAtLeast(0)
                    if (now - track.lastSeen > TRACK_HOLD_MS) iterator.remove()
                }
            }
            return trackStates
                .filter { it.hits >= CONFIRM_HITS }
                .map {
                    NightTrack(
                        id = it.id,
                        kind = it.kind,
                        score = it.score,
                        x = it.x, y = it.y, w = it.w, h = it.h,
                        lastSeenAgoMs = (now - it.lastSeen).coerceAtLeast(0L),
                        confirmed = it.confirmed,
                    )
                }
                .sortedBy { it.id }
        }
    }

    private fun iou(track: TrackState, box: DetectionBox): Float {
        val left = maxOf(track.x, box.x)
        val top = maxOf(track.y, box.y)
        val right = minOf(track.x + track.w, box.x + box.w)
        val bottom = minOf(track.y + track.h, box.y + box.h)
        val overlapW = (right - left).coerceAtLeast(0f)
        val overlapH = (bottom - top).coerceAtLeast(0f)
        val intersection = overlapW * overlapH
        val union = track.w * track.h + box.w * box.h - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    /** 两套复用缓冲 —— 尺寸变化时重建,其余时间零分配 */
    private class FrameSlot(
        val srcW: Int,
        val srcH: Int,
        val outW: Int,
        val outH: Int,
        val detW: Int,
        val detH: Int,
        val detOutW: Int,
        val detOutH: Int,
    ) {
        val greenSource: Bitmap = Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888)
        val greenTarget: Bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val graySource: Bitmap = Bitmap.createBitmap(detW, detH, Bitmap.Config.ARGB_8888)
        val grayTarget: Bitmap = Bitmap.createBitmap(detOutW, detOutH, Bitmap.Config.ARGB_8888)
        val greenPixels = IntArray(srcW * srcH)
        val grayPixels = IntArray(detW * detH)

        fun matches(
            srcW: Int, srcH: Int, outW: Int, outH: Int,
            detW: Int, detH: Int, detOutW: Int, detOutH: Int,
        ): Boolean = this.srcW == srcW && this.srcH == srcH && this.outW == outW &&
            this.outH == outH && this.detW == detW && this.detH == detH &&
            this.detOutW == detOutW && this.detOutH == detOutH

        fun release() {
            runCatching { greenSource.recycle() }
            runCatching { greenTarget.recycle() }
            runCatching { graySource.recycle() }
            runCatching { grayTarget.recycle() }
        }
    }

    companion object {
        const val PREVIEW_W = 640
        const val PREVIEW_H = 480
        private const val DETECT_LONG_EDGE = 480f
        private const val DETECT_INTERVAL_MS = 150L
        private const val CONFIRM_HITS = 2
        private const val TRACK_HOLD_MS = 700L
        private const val IOU_MATCH_THRESHOLD = 0.3f
        private const val EMA_ALPHA = 0.45f
    }
}
