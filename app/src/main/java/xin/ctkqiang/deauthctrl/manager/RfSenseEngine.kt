package xin.ctkqiang.deauthctrl.manager

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 穿墙感知引擎 —— WiFi RSSI 扰动分析(商用 WiFi 感知的基线技术)
 *
 * ## 原理(源自 CSI 感知的降级替代,如实说明)
 * 人体活动改变多径传播,RSSI 出现短时方差与振荡。本引擎对附近每个 AP
 * 的 RSSI 时间序列做窗口统计:标准差 σ 越大人体活动越强,聚合为
 * 0..1 的活动指数,四级状态:静止 / 微动 / 活动 / 强活动。
 *
 * ## 与真·CSI 感知的差距(界面必须呈现)
 * CSI(信道状态信息)能重建姿态(DensePose 路线),需要 root + 特定
 * 芯片(Atheros CSI Tool / Intel 5300 / Nexmon CSI)+ 外部分析工具;
 * 现代手机无此通道。本引擎只是 RSSI 层级的扰动检测 —— 检测「有没有
 * 人在动」,不能识别「是谁、什么姿态」。
 */
class RfSenseEngine {

    data class ApSense(
        val bssid: String,
        val name: String,
        val rssi: Int,
        val varDb: Float,   // 30s 窗口标准差
    )

    data class RfSenseState(
        val motionIndex: Float = 0f,          // 0..1 活动指数
        val level: String = "calm",           // calm / faint / motion / strong
        val perAp: List<ApSense> = emptyList(),
        val samples: Int = 0,                 // 参与统计的 AP 数
        val strongestAp: String = "",
        val strongestVar: Float = 0f,
    )

    /** 每 AP 的 RSSI 环形窗口:样本对(时间戳, RSSI) */
    private val windows = HashMap<String, ArrayDeque<Pair<Long, Int>>>()
    private var lastIngestMs = 0L

    /** 窗口长度 30s;每 tick 只推进一次 */
    fun ingest(targets: List<RadarTarget>, nowMs: Long): RfSenseState {
        if (nowMs - lastIngestMs < 900L) return lastState
        lastIngestMs = nowMs
        targets.forEach { t ->
            val w = windows.getOrPut(t.id) { ArrayDeque() }
            w.addLast(nowMs to t.rssi)
            while (w.isNotEmpty() && w.first().first < nowMs - WINDOW_MS) w.removeFirst()
            if (w.size > 240) w.removeFirst()
        }
        windows.keys.retainAll { id -> targets.any { it.id == id } }

        val senses = targets.mapNotNull { t ->
            val w = windows[t.id] ?: return@mapNotNull null
            if (w.size < 8) return@mapNotNull null
            val rssis = w.map { it.second }
            val mean = rssis.average().toFloat()
            val variance = rssis.map { (it - mean) * (it - mean) }.average().toFloat()
            ApSense(t.id, t.name, rssis.last(), sqrt(variance))
        }.sortedByDescending { it.varDb }

        // 聚合:指数衰减映射,σ 越大越接近 1;取最强三个 AP 的 σ 加权
        val topVars = senses.take(3).map { it.varDb }
        val agg = if (topVars.isEmpty()) 0f else topVars.average().toFloat()
        val index = (1f - exp(-agg / SIGMA_SCALE)).coerceIn(0f, 1f)
        val level = when {
            index < 0.15f -> "calm"
            index < 0.35f -> "faint"
            index < 0.60f -> "motion"
            else -> "strong"
        }
        lastState = RfSenseState(
            motionIndex = index,
            level = level,
            perAp = senses.take(8),
            samples = senses.size,
            strongestAp = senses.firstOrNull()?.name ?: "",
            strongestVar = senses.firstOrNull()?.varDb ?: 0f,
        )
        return lastState
    }

    fun reset() {
        windows.clear()
        lastState = RfSenseState()
    }

    private var lastState = RfSenseState()

    companion object {
        private const val WINDOW_MS = 30_000L
        /** 聚合饱和系数:平均 σ 达此值活动指数趋近 0.63 */
        private const val SIGMA_SCALE = 6f
    }
}
