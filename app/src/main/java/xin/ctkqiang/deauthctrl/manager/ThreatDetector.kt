package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfRect
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import org.opencv.objdetect.HOGDescriptor
import java.io.File
import java.io.FileOutputStream

/** 一个检测框(归一化坐标 0..1,相对同一旋转后的画面) */
data class DetectionBox(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val kind: String, // "face" | "human"
    /** 检测器权重 —— 不是校准概率,不能当百分比展示 */
    val score: Float,
)

/** 一次检测帧的结果与耗时(耗时是本次推理耗时,不是时间戳) */
data class DetectionFrame(
    val boxes: List<DetectionBox>,
    val faces: Int,
    val humans: Int,
    val inferenceMs: Long,
    val haarOk: Boolean,
    val hogOk: Boolean,
    val error: String? = null,
)

/** 检测器状态 —— 惰性初始化,失败原因对 UI 可见 */
sealed class DetectorStatus {
    data object Loading : DetectorStatus()
    data object Ready : DetectorStatus()
    data class Unavailable(val reason: String) : DetectorStatus()
}

/**
 * OpenCV 目标检测器 —— 夜视画面的人脸/人体识别
 *
 * - **人脸**: Haar 级联 frontalface_default(级别权重作为 score)
 * - **人体**: HOGDescriptor + 默认行人检测器(Dalal-Triggs,白天全身模型)
 *
 * 关键约束:
 * 1. 输入必须是**未增益的线性灰度图**(增益会把亮部削顶,Haar/HOG 梯度直接消失);
 * 2. 检测图长边 480,Haar minSize=20 / minNeighbors=4 / scaleFactor=1.1;
 * 3. HOG 单独缩到 320 长边控制 CPU,并复用 lazy HOG 实例与 SVM Mat;
 * 4. 初始化放在检测线程首次调用时,不在主线程复制 930KB 级联 / 加载原生库;
 * 5. score 是检测器权重,不是校准概率,不能承诺夜间检出率。
 */
class ThreatDetector(context: Context) {

    private val appContext = context.applicationContext

    private val _status = MutableStateFlow<DetectorStatus>(DetectorStatus.Loading)
    val status: StateFlow<DetectorStatus> = _status

    @Volatile private var initialized = false
    @Volatile private var faceCascade: CascadeClassifier? = null
    @Volatile private var hog: HOGDescriptor? = null

    /** 兼容旧 UI:仅当初始化成功且未报不可用 */
    val available: Boolean get() = _status.value is DetectorStatus.Ready

    /**
     * 检测线程首次调用时惰性初始化。损坏的级联文件删除后重试一次;
     * 失败原因(异常类名 + message)写入 status,UI 不再只显示「不可用」。
     */
    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            try {
                OpenCVLoader.initLocal()
                var cascade = loadCascade()
                if (cascade == null) {
                    // 文件可能只写了一半:删除后重试一次
                    runCatching { cascadeFile().delete() }
                    cascade = loadCascade()
                }
                if (cascade == null) {
                    _status.value = DetectorStatus.Unavailable("Haar 级联加载失败(文件损坏且重试无效)")
                    return
                }
                faceCascade = cascade
                val detector = HOGDescriptor()
                detector.setSVMDetector(HOGDescriptor.getDefaultPeopleDetector())
                hog = detector
                _status.value = DetectorStatus.Ready
            } catch (t: Throwable) {
                _status.value = DetectorStatus.Unavailable(
                    "${t.javaClass.simpleName}: ${t.message ?: "OpenCV 初始化失败"}",
                )
            }
        }
    }

    private fun cascadeFile(): File = File(appContext.filesDir, "haarcascade_frontalface_default.xml")

    private fun loadCascade(): CascadeClassifier? {
        val file = cascadeFile()
        if (!file.exists() || file.length() == 0L) {
            val tmp = File(appContext.filesDir, "haarcascade_frontalface_default.xml.tmp")
            try {
                appContext.resources.openRawResource(xin.ctkqiang.deauthctrl.R.raw.haarcascade_frontalface_default)
                    .use { input ->
                        FileOutputStream(tmp).use { output ->
                            input.copyTo(output)
                            output.flush()
                            // fsync 后再 rename,避免进程被杀留下半截级联文件
                            output.fd.sync()
                        }
                    }
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            } catch (_: Throwable) {
                runCatching { tmp.delete() }
                // 复制失败时保留旧文件(如果存在),交给 CascadeClassifier 判断 empty()
            }
        }
        return runCatching {
            val cascade = CascadeClassifier(file.absolutePath)
            if (cascade.empty()) null else cascade
        }.getOrNull()
    }

    /**
     * 对一帧**未增益灰度图**执行人脸 + 人体检测。
     * 输入已是旋转/镜像后的画面,检测框归一化坐标可直接与显示画面叠加。
     */
    fun detect(grayImage: Bitmap): DetectionFrame {
        ensureInitialized()
        val started = SystemClock.elapsedRealtime()
        val boxes = mutableListOf<DetectionBox>()
        var faces = 0
        var humans = 0
        var error: String? = null

        val cascade = faceCascade
        val hogDetector = hog
        if (cascade == null && hogDetector == null) {
            val reason = (_status.value as? DetectorStatus.Unavailable)?.reason ?: "检测器不可用"
            return DetectionFrame(emptyList(), 0, 0, 0, false, false, reason)
        }
        if (grayImage.width <= 0 || grayImage.height <= 0) {
            return DetectionFrame(emptyList(), 0, 0, 0, false, false, "空帧")
        }

        var srcMat: Mat? = null
        var haarMat: Mat? = null
        var hogMat: Mat? = null
        try {
            val srcW = grayImage.width
            val srcH = grayImage.height
            srcMat = Mat()
            Utils.bitmapToMat(grayImage, srcMat)

            // ── 人脸(Haar 级联):长边 480,最小 20px,降低漏检 ──
            if (cascade != null) {
                val haarScale = minOf(1f, DETECT_LONG_EDGE / maxOf(srcW, srcH).toFloat())
                haarMat = resizeForDetection(srcMat, haarScale)
                val haarW = haarMat.width()
                val haarH = haarMat.height()
                val rects = MatOfRect()
                val rejectLevels = MatOfInt()
                val levelWeights = MatOfDouble()
                try {
                    cascade.detectMultiScale3(
                        haarMat, rects, rejectLevels, levelWeights,
                        1.1, 4, 0,
                        Size(20.0, 20.0),
                        Size(haarW * 0.9, haarH * 0.9),
                        true,
                    )
                    val found = rects.toArray()
                    val weights = levelWeights.toArray()
                    found.forEachIndexed { index, rect ->
                        faces++
                        boxes.add(
                            normalize(
                                rect, haarW, haarH, "face",
                                weights.getOrElse(index) { 0.0 }.toFloat(),
                            ),
                        )
                    }
                } finally {
                    rects.release()
                    rejectLevels.release()
                    levelWeights.release()
                }
            }

            // ── 人体(HOG):单独 320 长边,复用 lazy HOG 实例 ──
            if (hogDetector != null) {
                var hogScale = HOG_LONG_EDGE / maxOf(srcW, srcH).toFloat()
                // HOG 默认窗口 64×128,画面高度不足 128 时窗口根本放不下
                if (srcH * hogScale < 128f) hogScale = 128f / srcH
                hogMat = resizeForDetection(srcMat, hogScale)
                val hogW = hogMat.width()
                val hogH = hogMat.height()
                if (hogW >= 64 && hogH >= 128) {
                    val rects = MatOfRect()
                    val weights = MatOfDouble()
                    try {
                        hogDetector.detectMultiScale(
                            hogMat, rects, weights,
                            0.0, Size(8.0, 8.0), Size(32.0, 32.0),
                            1.05, 2.0, false,
                        )
                        val found = rects.toArray()
                        val scores = weights.toArray()
                        found.forEachIndexed { index, rect ->
                            val score = scores.getOrElse(index) { 0.0 }.toFloat()
                            // HOG 权重初值阈值 0.5(需上机标定,不是概率)
                            if (score >= HOG_SCORE_THRESHOLD) {
                                humans++
                                boxes.add(normalize(rect, hogW, hogH, "human", score))
                            }
                        }
                    } finally {
                        rects.release()
                        weights.release()
                    }
                }
            }
        } catch (t: Throwable) {
            error = "${t.javaClass.simpleName}: ${t.message}"
        } finally {
            // 无论成功失败都释放原生 Mat,避免长时间运行泄漏
            runCatching { haarMat?.release() }
            runCatching { hogMat?.release() }
            runCatching { srcMat?.release() }
        }

        val elapsed = SystemClock.elapsedRealtime() - started
        return DetectionFrame(
            boxes = boxes,
            faces = faces,
            humans = humans,
            inferenceMs = elapsed,
            haarOk = cascade != null && error == null,
            hogOk = hogDetector != null && error == null,
            error = error,
        )
    }

    private fun resizeForDetection(src: Mat, scale: Float): Mat {
        val out = Mat()
        if (scale >= 0.999f) {
            src.copyTo(out)
            return out
        }
        val outW = (src.width() * scale).toInt().coerceAtLeast(1)
        val outH = (src.height() * scale).toInt().coerceAtLeast(1)
        Imgproc.resize(src, out, Size(outW.toDouble(), outH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        return out
    }

    private fun normalize(r: Rect, detW: Int, detH: Int, kind: String, score: Float): DetectionBox =
        DetectionBox(
            x = r.x.toFloat() / detW.toFloat(),
            y = r.y.toFloat() / detH.toFloat(),
            w = r.width.toFloat() / detW.toFloat(),
            h = r.height.toFloat() / detH.toFloat(),
            kind = kind,
            score = score,
        )

    companion object {
        private const val DETECT_LONG_EDGE = 480f
        private const val HOG_LONG_EDGE = 320f
        private const val HOG_SCORE_THRESHOLD = 0.5f
    }
}
