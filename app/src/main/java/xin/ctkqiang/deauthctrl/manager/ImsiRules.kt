package xin.ctkqiang.deauthctrl.manager

/**
 * IMSI 捕捉器(假基站/StingRay)检测规则 —— 纯函数,可单测。
 *
 * 检测依据(与 AIMSICD 同类):
 * 1. 制式降级:5G/4G → 2G —— 假基站最经典的压网手法(2G 无相互认证)
 * 2. LAC/TAC 突变:位置区突然改变且信号很强(真基站不会瞬间换区)
 * 3. 信号突增:dBm 短时间大幅抬升(假基站通常功率大且近)
 * 4. CID 频繁跳变:同一位置小区 ID 反复切换
 */
object ImsiRules {

    enum class Risk { LOW, MEDIUM, HIGH }

    data class Event(val type: String, val detail: String, val risk: Risk)

    data class CellSample(
        val rat: Int,          // 0=2G 1=3G 2=4G 3=5G
        val lacTac: Int,
        val cid: Long,
        val dbm: Int,
        val timeMs: Long,
    )

    /** RAT 排序权重:2G=0 … 5G=3 */
    fun ratRank(rat: Int): Int = rat.coerceIn(0, 3)

    /** 制式降级检查:从高代际掉到 2G(或掉两代) */
    fun downgradeEvents(prev: CellSample, cur: CellSample): List<Event> {
        val from = ratRank(prev.rat)
        val to = ratRank(cur.rat)
        if (to >= from) return emptyList()
        val drop = from - to
        return listOf(
            Event(
                "RAT 降级",
                "${ratName(prev.rat)} → ${ratName(cur.rat)}(信号 ${cur.dbm} dBm)",
                if (to == 0 || drop >= 2) Risk.HIGH else Risk.MEDIUM,
            ),
        )
    }

    /** LAC/TAC 突变:位置区变化且信号强(>-75dBm) */
    fun lacJumpEvent(prev: CellSample, cur: CellSample): Event? {
        if (prev.lacTac == cur.lacTac || prev.lacTac == 0 || cur.lacTac == 0) return null
        return Event(
            "LAC/TAC 突变",
            "LAC ${prev.lacTac} → ${cur.lacTac}(信号 ${cur.dbm} dBm)",
            if (cur.dbm > -75) Risk.MEDIUM else Risk.LOW,
        )
    }

    /** 信号突增:短时间内提升 ≥ 15dB */
    fun signalSurgeEvent(prev: CellSample, cur: CellSample): Event? {
        val windowMs = cur.timeMs - prev.timeMs
        if (windowMs !in 1_000..120_000) return null
        val surge = cur.dbm - prev.dbm
        if (surge < 15) return null
        return Event(
            "信号突增",
            "+${surge}dB(${prev.dbm} → ${cur.dbm} dBm,${windowMs / 1000}s)",
            Risk.MEDIUM,
        )
    }

    /** 综合判定:一条 HIGH 或 ≥2 条 MEDIUM → 告警 */
    fun alertLevel(events: List<Event>): Risk {
        if (events.any { it.risk == Risk.HIGH }) return Risk.HIGH
        if (events.count { it.risk != Risk.LOW } >= 2) return Risk.MEDIUM
        return Risk.LOW
    }

    fun ratName(rat: Int): String = when (rat) {
        0 -> "2G"
        1 -> "3G"
        2 -> "4G"
        else -> "5G"
    }
}
