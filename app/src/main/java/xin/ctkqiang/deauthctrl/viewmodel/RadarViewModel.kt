package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.RadarManager
import xin.ctkqiang.deauthctrl.manager.RadarTarget
import xin.ctkqiang.deauthctrl.manager.RssiSample
import xin.ctkqiang.deauthctrl.manager.rssiDistanceEstimateM
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 一次新目标探测事件 —— UI 收到后触发触觉警报 */
data class RadarContactEvent(val timeMs: Long, val count: Int, val names: List<String>)

/**
 * 金属探测状态 —— 磁力计三轴读数与相对磁场异常判据
 *
 * 全部来自真实磁力计读数;anomaly 是相对本机校准基线的迟滞阈值判定,
 * 只表示「附近磁场发生显著变化」(金属靠近/磁铁/环境变化),不是距离测量。
 */
data class MetalState(
    val microTesla: Float,
    val baselineUt: Float,
    val deltaUt: Float,
    val anomaly: Boolean,
    val calibrated: Boolean,
    val calProgress: Float,
    val accuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE,
    val driftUt: Float,
)

/**
 * 航向时间轨迹 —— 环形缓冲记录 (传感器时间戳, 真北航向) 对
 *
 * 采样瞬间的航向由相邻样本最短弧线性插值获得,供 RSSI 方位推演
 * 把扫描采样对齐到当时机身的真实朝向。50Hz 传感器下 100°/s 转速
 * 的插值误差约 1°。
 */
class HeadingTrace(capacity: Int = 4096) {
    private val tNanos = LongArray(capacity)
    private val headDeg = FloatArray(capacity)
    private var head = 0
    private var size = 0
    private val lock = Any()

    /** 旋转累计(度)—— 单次跳变 >8° 视为手抖丢弃;每 360° 一圈 */
    @Volatile var rotationAccumDeg = 0.0; private set
    @Volatile var rotationEpoch = 0; private set

    fun record(t: Long, headingDeg: Float) {
        synchronized(lock) {
            if (size > 0) {
                val prev = headDeg[(head - 1 + tNanos.size) % tNanos.size]
                var delta = headingDeg - prev
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                if (abs(delta) <= 8f) {
                    rotationAccumDeg += abs(delta)
                    rotationEpoch = floor(rotationAccumDeg / 360.0).toInt()
                }
            }
            tNanos[head] = t
            headDeg[head] = headingDeg
            head = (head + 1) % tNanos.size
            if (size < tNanos.size) size++
        }
    }

    /** 查询 tNanos 时刻的航向;时间窗口外或样本过旧(>250ms)返回 null */
    fun headingAt(tq: Long): Float? = synchronized(lock) {
        if (size == 0) return null
        val first = tNanos[(head - size + tNanos.size) % tNanos.size]
        val last = tNanos[(head - 1 + tNanos.size) % tNanos.size]
        if (tq < first || tq > last) return null
        // 二分查找最后一个 t <= 查询时刻
        var lo = 0; var hi = size - 1; var idx = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val ti = tNanos[(head - size + mid + tNanos.size) % tNanos.size]
            if (ti <= tq) { idx = mid; lo = mid + 1 } else hi = mid - 1
        }
        val i = (head - size + idx + tNanos.size) % tNanos.size
        val ti = tNanos[i]
        val hiDeg = headDeg[i]
        if (idx == size - 1) return hiDeg.takeIf { tq - ti <= 250_000_000L }
        val j = (i + 1) % tNanos.size
        val tj = tNanos[j]
        if (tj - ti <= 0) return null
        val frac = ((tq - ti).toDouble() / (tj - ti).toDouble()).toFloat().coerceIn(0f, 1f)
        var delta = headDeg[j] - hiDeg
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        ((hiDeg + frac * delta) + 360f) % 360f
    }

    fun clear() = synchronized(lock) { head = 0; size = 0; rotationAccumDeg = 0.0; rotationEpoch = 0 }
}

/** 方位推演状态 */
enum class LocState { UNAVAILABLE, COLLECTING, ESTIMATED, AMBIGUOUS, STALE }

/**
 * 单目标方位推演结果
 *
 * 方法:旋转机身,记录航向×RSSI 方向图并取功率峰 —— 属天线方向性推演,
 * 不是 AoA/测向;距离由 RSSI 路径损耗模型给出。所有数值都是估计。
 */
data class BearingEstimate(
    val targetId: String,
    val state: LocState = LocState.UNAVAILABLE,
    val bearingDeg: Float = Float.NaN,
    val sectorHalfWidthDeg: Float = 0f,
    val fwhmDeg: Float = 0f,
    val confidence: Float = 0f,
    val coverage: Float = 0f,
    val sampleCount: Int = 0,
    val rotations: Float = 0f,
    val contrastDb: Float = 0f,
    val secondLobeRatio: Float = 0f,
    val distanceM: Float = Float.NaN,
    val sigmaDistanceM: Float = 0f,
    val distanceModelAssumed: Boolean = true,
    val eastM: Float = Float.NaN,
    val northM: Float = Float.NaN,
    val covEE: Float = 0f,
    val covNN: Float = 0f,
    val covEN: Float = 0f,
    val ellipseSemiMajorM: Float = 0f,
    val ellipseSemiMinorM: Float = 0f,
    val ellipseAngleDeg: Float = 0f,
    val sweepCount: Int = 0,
    val ageMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val profileDb: List<Float> = List(BINS) { 0f },
    val binCounts: List<Int> = List(BINS) { 0 },
    val profileValid: List<Boolean> = List(BINS) { false },
) {
    companion object { const val BINS = 36 }
}

/**
 * RSSI 方位推演引擎 —— 旋转寻向 + 多圈 GLS 位置融合
 *
 * ## 原理(全部基于真实读数,输出为估计)
 * 1. 手机天线增益有方向性:缓慢旋转机身时,目标 RSSI 随航向呈波瓣调制,
 *    10° 分箱的方向图峰值即目标方位(±半宽为不确定度)
 * 2. 每完成一整圈旋转,取(方位, RSSI 中位数测距)生成一个 Fix;
 *    多圈 Fix 经广义最小二乘融合为东-北坐标与协方差椭圆
 * 3. 置信分是覆盖度/峰对比/第二瓣比的启发式合成,不是校准概率
 *
 * ## 局限(界面必须如实呈现)
 * 多径反射会造成双峰歧义;距离是路径损耗模型外推;需要用户配合旋转。
 */
class RssiLocalizationEngine {

    companion object {
        const val BINS = 36
        const val BIN_DEG = 10f
        const val WINDOW_MS = 120_000L
        const val MIN_SAMPLES = 12
        const val MIN_COVERAGE = 0.45f
        const val MIN_CONF = 0.35f
        const val MIN_ROTATIONS = 0.75f
        const val STALE_MS = 15_000L
        const val MAX_FIXES = 8
        /** 方位不确定度 σ = FWHM / 2.355(高斯近似) */
        const val GAUSS_K = 2.355f
        /** 50% / 90% 置信椭圆半径系数 */
        const val ELLIPSE_K50 = 1.1774f
        const val ELLIPSE_K90 = 2.1459f
    }

    private class TargetLoc {
        // 每 10° 航向分箱:样本时间戳队列(窗口剔除用) + RSSI 和/计数/平方和
        val binSamples = Array(BINS) { ArrayDeque<Pair<Long, Int>>() }
        val binSum = FloatArray(BINS)
        val binCount = IntArray(BINS)
        var lastAcceptedTs = 0L
        val distWindow = ArrayDeque<RssiSample>() // 最多 40 条 / 60s,只进距离统计
        val fixes = ArrayDeque<Fix2>()
        var lastFixEpoch = -1
        var smoothedBearing = Float.NaN
        var smoothedHalf = 0f
        var smoothedConf = 0f
        var updatedAtMs = 0L
    }

    private data class Fix2(val thetaRad: Float, val d: Float, val sigmaTheta: Float, val sigmaD: Float, val conf: Float)

    private val locs = HashMap<String, TargetLoc>()
    private val lock = Any()

    fun reset() = synchronized(lock) { locs.clear() }

    /** 吞入一轮雷达目标;样本按 timestampNanos 严格递增去重 */
    fun ingest(targets: List<RadarTarget>, trace: HeadingTrace, nowMs: Long) {
        synchronized(lock) {
            for (t in targets) {
                val l = locs.getOrPut(t.id) { TargetLoc() }
                for (s in t.history) {
                    if (s.timestampNanos <= 0 || s.timestampNanos <= l.lastAcceptedTs) continue
                    l.lastAcceptedTs = s.timestampNanos
                    l.updatedAtMs = nowMs
                    if (s.rssi !in -100..-20) continue
                    // 距离窗口:全部有效样本
                    if (l.distWindow.size >= 40) l.distWindow.removeFirst()
                    l.distWindow.addLast(s)
                    while (l.distWindow.isNotEmpty() && nowMs - l.distWindow.first().timeMs > 60_000L) l.distWindow.removeFirst()
                    // 方位分箱:需要真实采样时间戳 + 可回溯航向
                    if (s.approxTime) continue
                    val h = trace.headingAt(s.timestampNanos) ?: continue
                    val bin = ((h / BIN_DEG).toInt() % BINS + BINS) % BINS
                    val q = l.binSamples[bin]
                    q.addLast(s.timestampNanos to s.rssi)
                    while (q.isNotEmpty() && q.first().first < s.timestampNanos - WINDOW_MS * 1_000_000L) {
                        val old = q.removeFirst()
                        l.binSum[bin] -= old.second
                        l.binCount[bin]--
                    }
                    l.binSum[bin] += s.rssi
                    l.binCount[bin]++
                }
            }
        }
    }

    /**
     * 1Hz 估值:方向图取峰 + 抛物线插值 + 置信合成 + 圈边界 Fix 融合。
     * 返回全目标快照。
     */
    fun estimate(nowMs: Long): Map<String, BearingEstimate> = synchronized(lock) {
        val out = HashMap<String, BearingEstimate>(locs.size)
        for ((id, l) in locs) {
            val est = estimateOne(id, l, nowMs)
            if (est != null) out[id] = est
        }
        out
    }

    private fun estimateOne(id: String, l: TargetLoc, nowMs: Long): BearingEstimate? {
        // ── 窗口剔除过期分箱样本 ──
        for (b in 0 until BINS) {
            val q = l.binSamples[b]
            while (q.isNotEmpty() && q.first().first < (l.lastAcceptedTs - WINDOW_MS * 1_000_000L)) {
                val old = q.removeFirst(); l.binSum[b] -= old.second; l.binCount[b]--
            }
        }
        // ── 方向图:分箱均值 + 3 点滑动平滑 ──
        val mean = FloatArray(BINS)
        val valid = BooleanArray(BINS)
        var sampleCount = 0
        for (b in 0 until BINS) {
            if (l.binCount[b] > 0) { mean[b] = l.binSum[b] / l.binCount[b]; valid[b] = true; sampleCount += l.binCount[b] }
        }
        val validCount = valid.count { it }
        val coverage = validCount / BINS.toFloat()
        if (validCount == 0 || l.updatedAtMs == 0L) return null

        val smooth = FloatArray(BINS)
        for (b in 0 until BINS) {
            var s = 0f; var n = 0
            for (k in -1..1) { val i = (b + k + BINS) % BINS; if (valid[i]) { s += mean[i]; n++ } }
            smooth[b] = if (n > 0) s / n else 0f
        }

        // ── 峰值与抛物线亚分箱插值 ──
        val validMeans = smooth.filterIndexed { i, _ -> valid[i] }
        val peakBin = (0 until BINS).filter { valid[it] }.maxByOrNull { smooth[it] } ?: 0
        val peakDb = smooth[peakBin]
        val lo = validMeans.minOrNull() ?: peakDb
        val hi = validMeans.maxOrNull() ?: peakDb
        // 调制深度:方向图峰谷差。深度不足 = 天线方向性调制太弱,方位不可信
        val modulationDb = hi - lo
        // 峰对比 = 峰值 - 其余有效分箱均值
        val others = validMeans.filter { it != peakDb }
        val contrastDb = if (others.isNotEmpty()) peakDb - others.average().toFloat() else 0f
        // 第二瓣:与主瓣间隔 ≥ 3 分箱的次高峰
        val farBins = (0 until BINS).filter { valid[it] && minOf(abs(it - peakBin), BINS - abs(it - peakBin)) >= 3 }
        val secondPeak = farBins.maxOfOrNull { smooth[it] } ?: lo
        val secondLobeRatio = if (hi > lo) (secondPeak - lo) / (hi - lo).coerceAtLeast(0.01f) else 0f
        // 抛物线插值
        val prev = smooth[(peakBin - 1 + BINS) % BINS]
        val next = smooth[(peakBin + 1) % BINS]
        val denom = prev - 2 * peakDb + next
        val offset = if (abs(denom) > 1e-6f) (0.5f * (prev - next) / denom).coerceIn(-0.5f, 0.5f) else 0f
        val rawBearing = ((peakBin + offset) * BIN_DEG + 360f) % 360f

        // FWHM(-3dB 主瓣宽):双向半圈扫描停在同一点 = -3dB 区域覆盖全部分箱(平坦方向图)
        val thresh = peakDb - 3f
        var left = peakBin
        while (left != (peakBin - BINS / 2 + BINS) % BINS) {
            val cand = (left - 1 + BINS) % BINS
            if (!valid[cand] || smooth[cand] < thresh) break
            left = cand
        }
        var right = peakBin
        while (right != (peakBin + BINS / 2) % BINS) {
            val cand = (right + 1) % BINS
            if (!valid[cand] || smooth[cand] < thresh) break
            right = cand
        }
        val overThreshCount = (abs((right - left + BINS) % BINS) + 1).coerceAtMost(BINS)
        val flatPattern = overThreshCount >= BINS || modulationDb < 3f
        val fwhm = if (flatPattern) 360f else minOf(overThreshCount * BIN_DEG, 360f)

        // 置信分:覆盖度/峰对比/样本数/调制深度加权,第二瓣抑制 —— 启发式,非校准概率
        val modulationPenalty = (modulationDb / 6f).coerceIn(0f, 1f)
        val confRaw = 0.45f * (coverage / MIN_COVERAGE).coerceIn(0f, 1f) +
            0.25f * (contrastDb / 8f).coerceIn(0f, 1f) +
            0.15f * (sampleCount / 40f).coerceIn(0f, 1f) +
            0.15f * modulationPenalty
        val conf = (confRaw * (1f - 0.5f * secondLobeRatio.coerceIn(0f, 1f))).coerceIn(0f, 1f)

        // 双峰方向图(第二瓣等强)= 多径歧义,方位直接不可信
        val twinLobe = secondLobeRatio >= 0.8f && farBins.isNotEmpty()

        // 最短弧 EMA 平滑方位(仅方向图有意义时更新;平坦/双峰不输出假方位)
        if (!flatPattern && !twinLobe) {
            if (l.smoothedBearing.isNaN()) l.smoothedBearing = rawBearing
            else {
                var delta = rawBearing - l.smoothedBearing
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                l.smoothedBearing = (l.smoothedBearing + delta * 0.3f + 360f) % 360f
            }
            l.smoothedHalf = l.smoothedHalf * 0.7f + (fwhm / 2f).coerceIn(8f, 80f) * 0.3f
        }
        l.smoothedConf = l.smoothedConf * 0.6f + conf * 0.4f

        // ── 距离:窗口 RSSI 中位数 → 路径损耗模型 ──
        val rssiList = l.distWindow.map { it.rssi }
        val medRssi = rssiList.sorted().getOrNull(rssiList.size / 2)
        val refDbm = if (id in bleTxRefs) bleTxRefs[id]!! else if (id in wifiRefs) -40f else -59f
        val dMed = medRssi?.let { rssiDistanceEstimateM(it, refDbm) } ?: Float.NaN
        // σ_rssi = sqrt(MAD 测量噪声² + PTX 不确定度² + 模型误差²)
        val mad = if (rssiList.size >= 3) {
            val med = medRssi ?: 0
            1.4826f * (rssiList.map { abs(it - med).toFloat() }.sorted().getOrNull(rssiList.size / 2) ?: 1f)
        } else 1f
        val sigMeas = maxOf(1f, mad)
        val sigPtx = when (id) {
            in bleTxRefs -> 2f
            in wifiRefs -> 8f
            else -> 6f
        }
        val sigModel = 3f
        val sigRssi = sqrt(sigMeas * sigMeas + sigPtx * sigPtx + sigModel * sigModel)
        val sigD = if (dMed.isNaN()) 0f else dMed * ln(10f) / (10f * 2.5f) * sigRssi

        // ── 圈边界 Fix 融合:至少完成一整圈 + 覆盖达标 + 方向图有效 + 目标新鲜 ──
        // (epoch 0 只有零星样本;平坦/双峰方向图不给 Fix;目标消失后冻结,防幽灵 Fix)
        val rotEpoch = traceEpochRef.get()
        if (rotEpoch > l.lastFixEpoch && rotEpoch >= 1 &&
            conf >= MIN_CONF && coverage >= MIN_COVERAGE &&
            !flatPattern && !twinLobe && !dMed.isNaN() &&
            nowMs - l.updatedAtMs <= STALE_MS
        ) {
            l.lastFixEpoch = rotEpoch
            l.fixes.addLast(
                Fix2(
                    thetaRad = Math.toRadians(l.smoothedBearing.toDouble()).toFloat(),
                    d = dMed,
                    sigmaTheta = Math.toRadians((fwhm / GAUSS_K).toDouble()).toFloat(),
                    sigmaD = sigD,
                    conf = conf,
                )
            )
            if (l.fixes.size > MAX_FIXES) l.fixes.removeFirst()
        }

        // ── GLS 融合:2x2 解析求逆 ──
        var eastM = Float.NaN; var northM = Float.NaN
        var cEE = 0f; var cNN = 0f; var cEN = 0f
        var chiSq = 0f
        if (l.fixes.isNotEmpty()) {
            val fixes = l.fixes.toList()
            // 每 Fix 的雅可比 J = [[sinθ, d cosθ],[cosθ, -d sinθ]];C = J diag(σd²,σθ²) Jᵀ
            val pks = fixes.map { f ->
                val e = f.d * sin(f.thetaRad); val n = f.d * cos(f.thetaRad)
                val j00 = sin(f.thetaRad); val j01 = f.d * cos(f.thetaRad)
                val j10 = cos(f.thetaRad); val j11 = -f.d * sin(f.thetaRad)
                val c00 = j00 * j00 * f.sigmaD * f.sigmaD + j01 * j01 * f.sigmaTheta * f.sigmaTheta
                val c11 = j10 * j10 * f.sigmaD * f.sigmaD + j11 * j11 * f.sigmaTheta * f.sigmaTheta
                val c01 = j00 * j10 * f.sigmaD * f.sigmaD + j01 * j11 * f.sigmaTheta * f.sigmaTheta
                arrayOf(e, n, c00, c11, c01)
            }
            var iEE = 0f; var iNN = 0f; var iEN = 0f; var bE = 0f; var bN = 0f
            for (p in pks) {
                val det = p[2] * p[3] - p[4] * p[4]
                if (det <= 1e-9f) continue
                val ie = p[3] / det; val inn = p[2] / det; val ien = -p[4] / det
                iEE += ie; iNN += inn; iEN += ien
                bE += ie * p[0] + ien * p[1]
                bN += ien * p[0] + inn * p[1]
            }
            val detI = iEE * iNN - iEN * iEN
            if (abs(detI) > 1e-9f) {
                cEE = iNN / detI + 1e-6f; cNN = iEE / detI + 1e-6f; cEN = -iEN / detI
                eastM = cEE * bE + cEN * bN
                northM = cEN * bE + cNN * bN
                // 一致性 χ² 与经验散布
                var cEmpEE = 0f; var cEmpNN = 0f; var cEmpEN = 0f; var wSum = 0f; var chi = 0f
                for ((i, p) in pks.withIndex()) {
                    val de = p[0] - eastM; val dn = p[1] - northM
                    val w = l.fixes.toList()[i].conf * l.fixes.toList()[i].conf
                    cEmpEE += w * de * de; cEmpNN += w * dn * dn; cEmpEN += w * de * dn; wSum += w
                    val det = p[2] * p[3] - p[4] * p[4]
                    if (det > 1e-9f) chi += (de * de * p[3] - 2 * de * dn * p[4] + dn * dn * p[2]) / det
                }
                if (wSum > 0f) { cEE += cEmpEE / wSum; cNN += cEmpNN / wSum; cEN += cEmpEN / wSum }
                chiSq = chi
                // χ² 超限 → 多径歧义,膨胀协方差而不是隐藏结果
                val k = pks.size
                if (k >= 2 && chi > 3f * 2f * (k - 1)) {
                    val scale = (chi / (2f * (k - 1))).coerceAtLeast(1f)
                    cEE *= scale; cNN *= scale; cEN *= scale
                }
            }
        }

        // 椭圆:特征分解
        val tr = cEE + cNN
        val det = cEE * cNN - cEN * cEN
        val disc = sqrt(((tr / 2f) * (tr / 2f) - det).coerceAtLeast(0f))
        val lambda1 = tr / 2f + disc
        val lambda2 = (tr / 2f - disc).coerceAtLeast(0f)
        val phiRad = 0.5f * atan2(2f * cEN, cEE - cNN)

        // ── 状态机 ──
        val stale = nowMs - l.updatedAtMs > STALE_MS
        // 目标过期:清空 Fix 历史,防止消失后每个 epoch 复制幽灵 Fix 假性收缩协方差
        if (stale) l.fixes.clear()
        val ambiguous = chiSq > 3f * 2f * (l.fixes.size - 1).coerceAtLeast(1) && l.fixes.size >= 2
        val state = when {
            l.updatedAtMs == 0L -> LocState.UNAVAILABLE
            stale -> LocState.STALE
            flatPattern || twinLobe -> LocState.AMBIGUOUS // 无方向性调制 / 多径双峰,方位不可信
            ambiguous -> LocState.AMBIGUOUS
            conf >= MIN_CONF && coverage >= MIN_COVERAGE && !l.smoothedBearing.isNaN() -> LocState.ESTIMATED
            else -> LocState.COLLECTING
        }
        if (state == LocState.UNAVAILABLE) return null

        return BearingEstimate(
            targetId = id,
            state = state,
            bearingDeg = l.smoothedBearing,
            sectorHalfWidthDeg = l.smoothedHalf,
            fwhmDeg = fwhm,
            confidence = l.smoothedConf,
            coverage = coverage,
            sampleCount = sampleCount,
            rotations = traceEpochRef.get().toFloat(),
            contrastDb = contrastDb,
            secondLobeRatio = secondLobeRatio,
            distanceM = dMed,
            sigmaDistanceM = sigD,
            distanceModelAssumed = id !in bleTxRefs,
            eastM = eastM,
            northM = northM,
            covEE = cEE,
            covNN = cNN,
            covEN = cEN,
            ellipseSemiMajorM = ELLIPSE_K90 * sqrt(lambda1),
            ellipseSemiMinorM = ELLIPSE_K90 * sqrt(lambda2),
            ellipseAngleDeg = Math.toDegrees(phiRad.toDouble()).toFloat(),
            sweepCount = l.fixes.size,
            ageMs = nowMs - l.updatedAtMs,
            updatedAtMs = l.updatedAtMs,
            profileDb = smooth.toList(),
            binCounts = l.binCount.toList(),
            profileValid = valid.toList(),
        )
    }

    /** 丢弃已消失目标 */
    fun prune(alive: Set<String>) = synchronized(lock) {
        locs.keys.retainAll { it in alive }
        // 距离窗口过期样本在 ingest 内维护,分箱窗口在 estimate 内维护
    }

    /** BLE 广播实测发射功率表(由 ingest 前同步) */
    private val bleTxRefs = HashMap<String, Float>()
    private val wifiRefs = HashSet<String>()

    /** 每次 ingest 前同步 PTX 参考与类型 */
    fun syncRefs(targets: List<RadarTarget>) = synchronized(lock) {
        bleTxRefs.clear(); wifiRefs.clear()
        for (t in targets) {
            if (t.type == "wifi") wifiRefs.add(t.id)
            else t.txPowerDbm?.let { bleTxRefs[t.id] = it.toFloat() }
        }
    }

    private val traceEpochRef = java.util.concurrent.atomic.AtomicInteger(0)

    /** 由 ViewModel 在 estimate 前同步当前旋转圈数 */
    fun setRotationEpoch(epoch: Int) { traceEpochRef.set(epoch) }
}

/**
 * 设备雷达 ViewModel
 *
 * 职责：
 * 1. 管理 WiFi/蓝牙扫描（RadarManager），每 500ms 轮询刷新目标表
 * 2. 真实罗盘航向 —— 旋转矢量传感器（陀螺仪+地磁+加速度计融合），
 *    输出经低通平滑的航向/俯仰/横滚角；扫描盘随真机转动实时旋转
 * 3. 新目标探测事件流 —— 首次发现某设备时发布 [RadarContactEvent]，
 *    由 UI 层决定是否触发触觉警报（受设置页开关控制）
 * 4. 金属探测 —— 磁力计相对基线异常判定（[MetalState]）
 * 5. RSSI 方位推演 —— [RssiLocalizationEngine] 旋转寻向 + 位置融合
 */
class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = RadarManager(application)
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _targets = MutableStateFlow<List<RadarTarget>>(emptyList())
    val targets: StateFlow<List<RadarTarget>> = _targets

    /** 航向角 0-360°（0=正北）—— 磁北为基准，有定位时修正为真北 */
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading

    /** 俯仰角 —— 屏幕立起为正 */
    private val _pitch = MutableStateFlow(0f)
    val pitch: StateFlow<Float> = _pitch

    /** 横滚角 —— 右倾为正 */
    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll

    /** 罗盘传感器是否在线 */
    private val _compassOnline = MutableStateFlow(false)
    val compassOnline: StateFlow<Boolean> = _compassOnline

    /** 北向基准：磁北 / 真北 */
    private val _northRef = MutableStateFlow(L10n.get(R.string.vm_radar_north_magnetic))
    val northRef: StateFlow<String> = _northRef

    /** 新目标探测事件（一次性事件，timeMs 变化即代表新一轮） */
    private val _contact = MutableStateFlow<RadarContactEvent?>(null)
    val contact: StateFlow<RadarContactEvent?> = _contact

    /** 金属探测状态 —— null 表示磁力计不可用/未注册 */
    private val _metal = MutableStateFlow<MetalState?>(null)
    val metal: StateFlow<MetalState?> = _metal

    /** 方位推演结果(约 1Hz 更新,按目标 id 索引) */
    private val _localizations = MutableStateFlow<Map<String, BearingEstimate>>(emptyMap())
    val localizations: StateFlow<Map<String, BearingEstimate>> = _localizations

    private var pollJob: Job? = null
    private var sensorRegistered = false
    private var magRegistered = false

    /** 罗盘引用计数 —— 雷达页离场动画与档案页入场重叠时不会误关传感器 */
    private var compassRefs = 0

    /**
     * 扫描引用计数 —— 雷达页/档案页/网络拓扑页共享持有。
     *
     * 语义:页面进入 acquire、离开 release;只有「由持有者启动的」扫描在
     * 最后一个持有者离开时停止 —— 主页开关直接启停的扫描不受页面接管/释放影响。
     * 这样雷达页 → 档案页过渡期间扫描持续运行,档案页始终是近实时数据。
     */
    private var scanRefs = 0
    private var scanOwner = false

    /** 页面进入时调用:必要时启动扫描并持有 */
    fun acquireScan() {
        synchronized(this) {
            if (scanRefs == 0 && !_isRunning.value) {
                start()
                scanOwner = true
            }
            scanRefs++
        }
    }

    /** 页面离开时调用:最后一个持有者离开时,仅停掉由持有者启动的扫描 */
    fun releaseScan() {
        synchronized(this) {
            scanRefs = (scanRefs - 1).coerceAtLeast(0)
            if (scanRefs == 0 && scanOwner && _isRunning.value) {
                stop()
                scanOwner = false
            }
        }
    }

    // 真北修正用磁偏角（度）；无定位时保持磁北
    @Volatile private var declinationDeg = 0f

    private val headingTrace = HeadingTrace()
    private val engine = RssiLocalizationEngine()

    /** 磁力计校准状态 */
    private var magSamples = 0
    private var magBaseline = 0f
    private var magCalBaseline = 0f
    private var magLastEmitMs = 0L
    private var magAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE

    // ─── 罗盘传感器融合 ────────────────────────────────────────────────────────

    private val sensorListener = object : SensorEventListener {
        private val rotationMatrix = FloatArray(9)
        private val deviceMatrix = FloatArray(9)
        private val orientation = FloatArray(3)
        private var smoothedHeading = 0f
        private var hasSample = false

        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    // 重映射到设备坐标：绕 X 轴 +90°，让屏幕朝向用户时航向指向正前方
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_X, SensorManager.AXIS_Z,
                        deviceMatrix,
                    )
                    SensorManager.getOrientation(deviceMatrix, orientation)
                    val rawHeading = ((Math.toDegrees(orientation[0].toDouble()) + 360f) % 360f).toFloat()
                    val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                    val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()

                    // 航向低通平滑 + 0/360 跨越走最短弧，避免北向抖动
                    if (!hasSample) { smoothedHeading = rawHeading; hasSample = true }
                    else {
                        var delta = rawHeading - smoothedHeading
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        smoothedHeading = (smoothedHeading + delta * 0.25f + 360f) % 360f
                    }
                    // 发射节流: 传感器 50Hz, 微小变化不发射 —— 避免每 20ms 触发全盘重绘
                    val newHeading = (smoothedHeading - declinationDeg + 360f) % 360f
                    if (kotlin.math.abs(newHeading - _heading.value) > 0.15f) _heading.value = newHeading
                    if (kotlin.math.abs(pitch - _pitch.value) > 0.3f) _pitch.value = pitch
                    if (kotlin.math.abs(roll - _roll.value) > 0.3f) _roll.value = roll
                    // 方位推演用的原始真北航向轨迹(平滑前,时间戳同源)
                    headingTrace.record(event.timestamp, (rawHeading - declinationDeg + 360f) % 360f)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> onMagnetometer(event)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) magAccuracy = accuracy
        }
    }

    // ─── 金属探测 ─────────────────────────────────────────────────────────────

    /** 磁力计读数 → 相对基线异常判定(迟滞阈值,10Hz 发射上限) */
    private fun onMagnetometer(event: SensorEvent) {
        val m = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2],
        )
        magSamples++
        val calibrated = magSamples >= MAG_CAL_SAMPLES
        if (!calibrated) {
            magBaseline = (magBaseline * (magSamples - 1) + m) / magSamples
            if (magSamples == MAG_CAL_SAMPLES) magCalBaseline = magBaseline
        } else {
            val delta = m - magBaseline
            val enter = maxOf(MAG_ABS_THRESHOLD_UT, magBaseline * MAG_REL_THRESHOLD)
            val exit = maxOf(MAG_EXIT_ABS_UT, magBaseline * MAG_EXIT_REL)
            val wasAnomaly = _metal.value?.anomaly == true
            val anomaly = if (wasAnomaly) abs(delta) >= exit else abs(delta) >= enter
            // 非异常期间 EMA 跟踪环境慢漂移(约 2s 时间常数);异常期间冻结
            if (!anomaly) magBaseline += MAG_BASELINE_ALPHA * delta
            val nowMs = System.currentTimeMillis()
            if (nowMs - magLastEmitMs >= MAG_EMIT_INTERVAL_MS || anomaly != wasAnomaly) {
                magLastEmitMs = nowMs
                _metal.value = MetalState(
                    microTesla = m,
                    baselineUt = magBaseline,
                    deltaUt = delta,
                    anomaly = anomaly,
                    calibrated = true,
                    calProgress = 1f,
                    accuracy = magAccuracy,
                    driftUt = magBaseline - magCalBaseline,
                )
            }
        }
    }

    /** 重新校准基线 —— 金属探测条点击触发 */
    fun resetMetalBaseline() {
        magSamples = 0; magBaseline = 0f
        _metal.value = null
    }

    // ─── 传感器注册(引用计数) ─────────────────────────────────────────────────

    /** 雷达页/档案页进入时调用 */
    fun acquireCompass() {
        if (compassRefs++ == 0) startCompass()
    }

    /** 雷达页/档案页离开时调用 */
    fun releaseCompass() {
        compassRefs = (compassRefs - 1).coerceAtLeast(0)
        if (compassRefs == 0) stopCompass()
    }

    /** 启动罗盘 + 磁力计传感器 —— 进入页面时调用 */
    @Suppress("DEPRECATION")
    fun startCompass() {
        // 有定位时用真实磁偏角修正到真北（GeomagneticField 需要 WGS84 坐标）
        updateDeclination()
        val rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotSensor != null) {
            try {
                if (!sensorRegistered) {
                    sensorRegistered = sensorManager.registerListener(
                        sensorListener, rotSensor, SensorManager.SENSOR_DELAY_GAME,
                    )
                    headingTrace.clear()
                }
            } catch (_: Exception) { sensorRegistered = false }
        }
        _compassOnline.value = sensorRegistered
        // 磁力计独立注册:旋转矢量缺失时金属探测仍可用
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (magSensor != null && !magRegistered) {
            try {
                magRegistered = sensorManager.registerListener(
                    sensorListener, magSensor, SensorManager.SENSOR_DELAY_GAME,
                )
                magSamples = 0; magBaseline = 0f; _metal.value = null
            } catch (_: Exception) { magRegistered = false }
        }
        if (magSensor == null) _metal.value = null
    }

    /** 停止罗盘与磁力计传感器 */
    fun stopCompass() {
        if (sensorRegistered) {
            try { sensorManager.unregisterListener(sensorListener) } catch (_: Exception) {}
            sensorRegistered = false
            _compassOnline.value = false
        }
        if (magRegistered) {
            try { sensorManager.unregisterListener(sensorListener) } catch (_: Exception) {}
            magRegistered = false
            _metal.value = null
        }
    }

    /** 从最近一次定位推算当地磁偏角，把磁北修正为真北 */
    @Suppress("MissingPermission")
    private fun updateDeclination() {
        try {
            val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val loc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: return
            declinationDeg = GeomagneticField(
                loc.latitude.toFloat(), loc.longitude.toFloat(), loc.altitude.toFloat(),
                System.currentTimeMillis(),
            ).declination
            _northRef.value = L10n.get(R.string.vm_radar_north_true)
        } catch (_: Exception) { _northRef.value = L10n.get(R.string.vm_radar_north_magnetic) }
    }

    // ─── 扫描与轮询 ───────────────────────────────────────────────────────────

    /** 启动雷达扫描 + 后台轮询 + 新目标探测 + 方位推演 */
    fun start() {
        scanOwner = false // 显式启停(主页开关):页面释放不接管
        engine.reset()
        manager.start()
        _isRunning.value = true
        pollJob = CoroutineScope(Dispatchers.IO).launch {
            var tick = 0
            while (isActive) {
                // 目标表 TTL:BLE 随机 MAC 轮换/设备离开,不清理会无界增长
                manager.pruneStale()
                // ConcurrentHashMap 顺序不稳定:排序保证列表相等性可用于差分发射
                val list = manager.targets.values.toList().sortedBy { it.id }
                if (list != _targets.value) _targets.value = list
                val fresh = manager.drainNewContacts()
                if (fresh.isNotEmpty()) {
                    val names = list.filter { it.id in fresh }.map { it.name }
                    _contact.value = RadarContactEvent(System.currentTimeMillis(), fresh.size, names)
                }
                // 方位推演:每 tick 吞样本,约 1Hz 出结果
                engine.syncRefs(list)
                engine.setRotationEpoch(headingTrace.rotationEpoch)
                engine.ingest(list, headingTrace, System.currentTimeMillis())
                if (tick % 2 == 0) {
                    _localizations.value = engine.estimate(System.currentTimeMillis())
                    engine.prune(list.map { it.id }.toSet())
                }
                manager.onTick()
                tick++
                delay(500)
            }
        }
    }

    /** 停止雷达扫描(不清空推演结果,档案页仍可读;状态按年龄自动 STALE) */
    fun stop() {
        manager.stop()
        pollJob?.cancel()
        _isRunning.value = false
    }

    /** 配对弹窗消息:让目标设备弹出系统配对请求(改本机蓝牙名 + createBond) */
    fun sendPairingPopup(addr: String, message: String): String? = manager.sendPairingPopup(addr, message)

    /** 单目标实时流 —— 档案页订阅,目标更新即发射(内容去重) */
    fun targetFlow(id: String): Flow<RadarTarget?> =
        _targets.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    override fun onCleared() {
        super.onCleared()
        stopCompass()
        manager.stop()
        pollJob?.cancel()
        engine.reset()
    }

    companion object {
        /** 金属探测校准样本数 */
        private const val MAG_CAL_SAMPLES = 60
        /** 基线 EMA 系数(约 2s 时间常数 @ 50Hz) */
        private const val MAG_BASELINE_ALPHA = 0.01f
        /** 异常判定:绝对阈值与相对阈值(进入迟滞) */
        private const val MAG_ABS_THRESHOLD_UT = 3.0f
        private const val MAG_REL_THRESHOLD = 0.05f
        private const val MAG_EXIT_ABS_UT = 2.0f
        private const val MAG_EXIT_REL = 0.03f
        /** 金属探测状态发射间隔上限 */
        private const val MAG_EMIT_INTERVAL_MS = 100L
    }
}
