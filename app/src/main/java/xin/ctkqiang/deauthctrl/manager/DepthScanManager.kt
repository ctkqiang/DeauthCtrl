package xin.ctkqiang.deauthctrl.manager

import android.app.Activity
import android.content.Context
import android.os.Build
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.io.Closeable

/** 深度能力状态 —— UI 横幅据此显示 */
enum class DepthCapability {
    CHECKING, AR_MISSING, NOT_SUPPORTED, PERMISSION_REQUIRED, AVAILABLE, INITIALIZING, ERROR,
}

/**
 * ARCore 深度扫描封装。
 *
 * ## 关键事实
 * - 相机由 ARCore **独占**:预览 = DepthRenderer 在 GL 线程绘制相机纹理,
 *   不能与 CameraX 同时绑相机。
 * - DEPTH16 图像单位恒为**毫米**;值 0 = 无效。
 * - AUTOMATIC 模式在无 ToF 硬件设备上走**运动深度**(缓慢移动设备才有深度),
 *   不假设设备有专用 LiDAR。
 * - 会话方法中 resume/pause 可从 UI 线程调用(官方示例同款),
 *   其余(GL 纹理绑定 / configure / update)必须在 GL 线程。
 */
class DepthScanManager(private val activity: Activity) : Closeable {

    var session: Session? = null
        private set
    var capability = DepthCapability.CHECKING
        private set

    /** 待采样的点击(VIEW_NORMALIZED 归一化坐标),由渲染线程每帧消费后置空 */
    @Volatile var pendingTap: Pair<Float, Float>? = null

    /** 无 GL 的静态检查:Play Services for AR 可用性 + 设备能力 */
    fun checkArAvailability(context: Context): DepthCapability {
        capability = if (Build.VERSION.SDK_INT < 24) DepthCapability.NOT_SUPPORTED
        else DepthMath.mapArAvailability(ArCoreApk.getInstance().checkAvailability(context))
        return capability
    }

    /** 引导安装 / 更新 Play Services for AR(用户点击后调用) */
    fun requestInstall(): ArCoreApk.InstallStatus =
        ArCoreApk.getInstance().requestInstall(activity, true)

    /**
     * GL 线程:首次创建会话并探测深度能力;后续(上下文重建)仅重绑纹理与显示几何。
     */
    fun onSurfaceReadyOnGlThread(cameraTextureId: Int, width: Int, height: Int, rotation: Int): DepthCapability {
        val s = session
        if (s != null) {
            // EGL 上下文重建后相机纹理已换新 ID,必须重绑
            s.setCameraTextureNames(intArrayOf(cameraTextureId))
            s.setDisplayGeometry(rotation, width, height)
            return capability
        }
        val created = Session(activity)
        val config = Config(created)
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        val depthOk = created.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        config.depthMode = if (depthOk) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
        created.configure(config)
        created.setCameraTextureNames(intArrayOf(cameraTextureId))
        created.setDisplayGeometry(rotation, width, height)
        created.resume()
        session = created
        capability = DepthMath.mapDepthSupport(depthOk)
        return capability
    }

    /** GL 线程每帧:深度读取(中心中值 + 热力图)+ 待定点击采样 */
    fun sampleDepthFrame(frame: Frame, heatmapOn: Boolean): DepthFrameResult {
        val depth = readDepth16(frame)
        val centerMm = depth?.let { DepthMath.centerMedianMm(it.values, it.width, it.height) }
        val heat = if (heatmapOn && depth != null) {
            DepthMath.buildHeatmap(depth.values, depth.width, depth.height)
        } else null
        val tapMm = pendingTap?.let { (nx, ny) ->
            runCatching { sampleAtViewPoint(frame, nx, ny) }.getOrNull()
        }
        pendingTap = null
        val data = depth?.let { DepthFrameData(it.width, it.height, centerMm, heat) }
        return DepthFrameResult(data, tapMm)
    }

    /**
     * 点击采样:VIEW_NORMALIZED(原点左上,y 向下)→ IMAGE_PIXELS(ARCore 官方
     * 坐标变换,含显示几何/旋转校正),再取深度图像素。
     */
    private fun sampleAtViewPoint(frame: Frame, nx: Float, ny: Float): Int? {
        val px = FloatArray(2)
        frame.transformCoordinates2d(
            Coordinates2d.VIEW_NORMALIZED,
            floatArrayOf(nx, ny),
            Coordinates2d.IMAGE_PIXELS,
            px,
        )
        val depth = readDepth16(frame) ?: return null
        val u = (px[0] / depth.width).coerceIn(0f, 1f)
        val v = (px[1] / depth.height).coerceIn(0f, 1f)
        return DepthMath.sampleMm(depth.values, depth.width, depth.height, u, v)
    }

    /**
     * 前后摄切换(UI 线程排队到 GL 线程调用)。ARCore 1.5x 的 CameraConfig
     * 只读 —— 朝向经 CameraConfigFilter 过滤支持的配置后选取。
     * 前置深度在部分设备不可用,失败回退。
     */
    fun setFacing(front: Boolean): Boolean = runCatching {
        val s = session ?: return false
        val filter = CameraConfigFilter(s)
        filter.setFacingDirection(if (front) CameraConfig.FacingDirection.FRONT else CameraConfig.FacingDirection.BACK)
        val configs = s.getSupportedCameraConfigs(filter)
        if (configs.isEmpty()) return false
        s.setCameraConfig(configs[0])
        true
    }.getOrDefault(false)

    fun pause() { runCatching { session?.pause() } }

    fun resume() { runCatching { session?.resume() } }

    override fun close() {
        runCatching { session?.close() }
        session = null
    }

    @Suppress("DEPRECATION")
    fun displayRotation(): Int = runCatching {
        activity.windowManager.defaultDisplay.rotation
    }.getOrDefault(0)

    /**
     * DEPTH16 平面拷贝:Image 只保证 rowStride 对齐,不保证无 padding。
     * 返回前**立即 close()** —— 每帧必须释放,否则原生缓冲泄漏。
     */
    private fun readDepth16(frame: Frame): DepthImage? = runCatching {
        val img = frame.acquireDepthImage16Bits()
        try {
            val plane = img.planes[0]
            val w = img.width
            val h = img.height
            val buf = plane.buffer
            buf.rewind()
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val values = ShortArray(w * h)
            for (y in 0 until h) {
                val row = y * rowStride
                for (x in 0 until w) {
                    values[y * w + x] = buf.getShort(row + x * pixelStride)
                }
            }
            DepthImage(w, h, values)
        } finally {
            img.close()
        }
    }.getOrNull()

    data class DepthImage(val width: Int, val height: Int, val values: ShortArray)

    data class DepthFrameData(
        val width: Int,
        val height: Int,
        val centerMm: Int?,
        val heatmap: IntArray?,
    )

    data class DepthFrameResult(val data: DepthFrameData?, val tapMm: Int?)
}
