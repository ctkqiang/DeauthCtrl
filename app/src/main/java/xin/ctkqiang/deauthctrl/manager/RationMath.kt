package xin.ctkqiang.deauthctrl.manager

/** 补给纯计算:剩余天数与告警等级(纯 JVM 可单测) */
object RationMath {

    enum class Status { OK, WATCH, CRITICAL }

    /** 默认人均日消耗:水 3L / 食物 2200 kcal(应急标准) */
    const val WATER_PER_PERSON_DAY_L = 3.0
    const val FOOD_PER_PERSON_DAY_KCAL = 2200.0

    fun waterDaysRemaining(liters: Double, persons: Int, perPersonDayL: Double = WATER_PER_PERSON_DAY_L): Double {
        if (persons <= 0 || liters <= 0) return 0.0
        return liters / (persons * perPersonDayL)
    }

    fun foodDaysRemaining(kcal: Double, persons: Int, perPersonDayKcal: Double = FOOD_PER_PERSON_DAY_KCAL): Double {
        if (persons <= 0 || kcal <= 0) return 0.0
        return kcal / (persons * perPersonDayKcal)
    }

    /** 告警等级:≤1 天危急 / ≤3 天警戒 / 其余正常 */
    fun status(days: Double): Status = when {
        days <= 1.0 -> Status.CRITICAL
        days <= 3.0 -> Status.WATCH
        else -> Status.OK
    }

    fun formatDays(days: Double): String =
        if (days >= 100) "%.0f d".format(days) else "%.1f d".format(days)
}
