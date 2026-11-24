package xin.ctkqiang.deauthctrl.manager

import com.google.ar.core.ArCoreApk

/**
 * 深度扫描纯计算 —— 毫米换算 / UV 索引 / 置信度过滤 / 中值采样 / 热力图降采样
 * / 能力状态映射。除枚举引用外无任何 Android 依赖,可在 JVM 上单测。
 *
 * ARCore DEPTH16 图像单位恒为毫米;值 0 表示无效(过近/缺失/低置信被过滤)。
 */
object DepthMath {

    /** 置信度过滤阈值:低于此值的像素按无效处理(置 0) */
    const val MIN_CONFIDENCE = 100

    /** 热力图降采样网格(每帧绘制的矩形量可控,且与屏幕分辨率解耦) */
    const val HEAT_GRID_W = 48
    const val HEAT_GRID_H = 36

    /** 距离色带(米):≤1 红 / ≤2 橙 / ≤3 黄 / ≤5 绿 / >5 蓝 */
    private val BANDS = listOf(
        1.0f to 0xFFEC407A.toInt(),
        2.0f to 0xFFFF9800.toInt(),
        3.0f to 0xFFFFEB3B.toInt(),
        5.0f to 0xFF00E676.toInt(),
        Float.MAX_VALUE to 0xFF00B0FF.toInt(),
    )

    /** 毫米 → 米 */
    fun mmToMeters(mm: Int): Float = mm / 1000f

    /** 读数文案:两位数小数(异常大值退化为整数) */
    fun formatMeters(mm: Int): String {
        val m = mm / 1000f
        return if (m >= 100f) "%.0f m".format(m) else "%.2f m".format(m)
    }

    /** 归一化 UV(图像行方向)→ 深度图一维索引,越界钳位 */
    fun uvToIndex(u: Float, v: Float, w: Int, h: Int): Int {
        val x = (u * w).toInt().coerceIn(0, w - 1)
        val y = (v * h).toInt().coerceIn(0, h - 1)
        return y * w + x
    }

    /** 原始深度 + 置信度过滤:低置信像素置 0(0 = 无效) */
    fun filterByConfidence(depth: ShortArray, confidence: ByteArray, minConf: Int = MIN_CONFIDENCE): ShortArray {
        val n = minOf(depth.size, confidence.size)
        return ShortArray(depth.size).also { out ->
            for (i in 0 until n) out[i] = if ((confidence[i].toInt() and 0xFF) >= minConf) depth[i] else 0
        }
    }

    /** 中心 3×3 中值采样(抗噪);全部无效返回 null */
    fun centerMedianMm(depth: ShortArray, w: Int, h: Int): Int? {
        if (depth.size < w * h || w < 3 || h < 3) return null
        val cx = w / 2
        val cy = h / 2
        val vals = mutableListOf<Int>()
        for (dy in -1..1) for (dx in -1..1) {
            val x = (cx + dx).coerceIn(0, w - 1)
            val y = (cy + dy).coerceIn(0, h - 1)
            val v = depth[y * w + x].toInt() and 0xFFFF
            if (v > 0) vals.add(v)
        }
        if (vals.isEmpty()) return null
        vals.sort()
        return vals[vals.size / 2]
    }

    /** 单点采样(屏幕点击):无效返回 null */
    fun sampleMm(depth: ShortArray, w: Int, h: Int, u: Float, v: Float): Int? {
        val v0 = depth[uvToIndex(u, v, w, h)].toInt() and 0xFFFF
        return if (v0 > 0) v0 else null
    }

    /**
     * 深度图 → 热力图色带网格(降采样)。
     * 返回 gridW*gridH 的 ARGB 颜色;无效像素 0(透明)。
     */
    fun buildHeatmap(depth: ShortArray, w: Int, h: Int, gridW: Int = HEAT_GRID_W, gridH: Int = HEAT_GRID_H): IntArray {
        val out = IntArray(gridW * gridH)
        val stepX = maxOf(1, w / gridW)
        val stepY = maxOf(1, h / gridH)
        for (gy in 0 until gridH) {
            val y = minOf(gy * stepY, h - 1)
            for (gx in 0 until gridW) {
                val x = minOf(gx * stepX, w - 1)
                val mm = depth[y * w + x].toInt() and 0xFFFF
                out[gy * gridW + gx] = if (mm > 0) colorForMm(mm) else 0
            }
        }
        return out
    }

    /** 毫米 → 色带颜色 */
    fun colorForMm(mm: Int): Int {
        val m = mm / 1000f
        for (i in BANDS.indices) {
            val (limit, color) = BANDS[i]
            if (m <= limit) return color
        }
        return BANDS.last().second
    }

    /**
     * ArCoreApk.Availability → 能力状态(纯映射,可单测)。
     * SUPPORTED_INSTALLED 只表示 AR 服务可用 —— 深度模式支持需会话创建后确认,
     * 因此映射为 CHECKING。
     */
    fun mapArAvailability(availability: ArCoreApk.Availability): DepthCapability = when (availability) {
        ArCoreApk.Availability.SUPPORTED_INSTALLED -> DepthCapability.CHECKING
        ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> DepthCapability.NOT_SUPPORTED
        else -> DepthCapability.AR_MISSING
    }

    /** 深度模式支持结果 → 能力状态 */
    fun mapDepthSupport(supported: Boolean): DepthCapability =
        if (supported) DepthCapability.AVAILABLE else DepthCapability.NOT_SUPPORTED
}
