package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfRect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import org.opencv.objdetect.HOGDescriptor
import org.opencv.android.Utils
import org.opencv.core.Rect
import java.io.File

/** 一个检测框(归一化坐标 0..1,相对画面) */
data class DetectionBox(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val kind: String, // "face" | "human"
)

/** 一次威胁扫描报告 */
data class ThreatReport(
    val faces: Int,
    val humans: Int,
    val boxes: List<DetectionBox>,
    val timeMs: Long,
) {
    val anyThreat: Boolean get() = faces > 0 || humans > 0
}

/**
 * OpenCV 目标检测器 —— 夜视画面的人脸/人体识别
 *
 * 全部使用 OpenCV 内置算法,不依赖外部模型文件:
 * - **人体**: HOGDescriptor + 默认行人检测器(经典 Dalal-Triggs HOG)
 * - **人脸**: Haar 级联 frontalface_default(级联文件打包在 res/raw,
 *   首次运行时解压到私有目录)
 *
 * 性能策略:输入缩放到 320×240 检测(约 200-400ms/帧),坐标归一化回
 * 原图供 UI 叠加。CPU 检测为主,稳定优先。
 */
class ThreatDetector(context: Context) {

    private val appContext = context.applicationContext
    private var faceCascade: CascadeClassifier? = null

    /** 检测器是否就绪(OpenCV 库加载 + 级联文件可用) */
    val available: Boolean get() = faceCascade != null

    init {
        try {
            // initLocal() 幂等,返回是否加载成功(无静态 isInitialized 字段)
            OpenCVLoader.initLocal()
            val cascadeFile = File(appContext.filesDir, "haarcascade_frontalface_default.xml")
            if (!cascadeFile.exists() || cascadeFile.length() == 0L) {
                appContext.resources.openRawResource(xin.ctkqiang.deauthctrl.R.raw.haarcascade_frontalface_default).use { input ->
                    cascadeFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val cascade = CascadeClassifier(cascadeFile.absolutePath)
            faceCascade = if (cascade.empty()) null else cascade
        } catch (_: Throwable) {
            faceCascade = null
        }
    }

    /**
     * 对一帧夜视增强图执行人脸 + 人体检测。
     * 输出坐标已归一化(0..1),UI 可直接按画布比例绘制。
     */
    fun detect(rgba: Bitmap): ThreatReport {
        val boxes = mutableListOf<DetectionBox>()
        var faces = 0
        var humans = 0

        try {
            val srcW = rgba.width.toFloat()
            val srcH = rgba.height.toFloat()
            if (srcW <= 0f || srcH <= 0f) return ThreatReport(0, 0, boxes, System.currentTimeMillis())

            // 检测分辨率 320×240(在保证检出率的同时控制 CPU 耗时)
            val scale = minOf(320f / srcW, 240f / srcH).coerceAtMost(1f)
            val detectW = (srcW * scale).toInt().coerceAtLeast(1)
            val detectH = (srcH * scale).toInt().coerceAtLeast(1)

            val srcMat = Mat()
            Utils.bitmapToMat(rgba, srcMat)
            val small = Mat()
            Imgproc.resize(srcMat, small, Size(detectW.toDouble(), detectH.toDouble()))
            val gray = Mat()
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)

            // ── 人脸(Haar 级联) ──
            val cascade = faceCascade
            if (cascade != null) {
                val faceRects = MatOfRect()
                cascade.detectMultiScale(
                    gray, faceRects,
                    1.12, 5, 0,
                    Size(30.0, 30.0), Size((detectW * 0.8), (detectH * 0.8)),
                )
                for (r in faceRects.toArray()) {
                    faces++
                    boxes.add(normalize(r, srcW, srcH, detectW, detectH, "face"))
                }
            }

            // ── 人体(HOG 行人检测) ──
            val hog = HOGDescriptor()
            hog.setSVMDetector(HOGDescriptor.getDefaultPeopleDetector())
            val bodyRects = MatOfRect()
            val weights = MatOfDouble()
            hog.detectMultiScale(
                gray, bodyRects, weights,
                0.0, Size(8.0, 8.0), Size(16.0, 16.0), 1.05, 2.0, true,
            )
            for (r in bodyRects.toArray()) {
                humans++
                boxes.add(normalize(r, srcW, srcH, detectW, detectH, "human"))
            }

            gray.release()
            small.release()
            srcMat.release()
        } catch (_: Throwable) {
            // 检测失败不影响夜视画面本身
        }

        return ThreatReport(faces, humans, boxes, System.currentTimeMillis())
    }

    private fun normalize(r: Rect, srcW: Float, srcH: Float, detW: Int, detH: Int, kind: String): DetectionBox =
        DetectionBox(
            x = (r.x * (srcW / detW)) / srcW,
            y = (r.y * (srcH / detH)) / srcH,
            w = (r.width * (srcW / detW)) / srcW,
            h = (r.height * (srcH / detH)) / srcH,
            kind = kind,
        )
}
