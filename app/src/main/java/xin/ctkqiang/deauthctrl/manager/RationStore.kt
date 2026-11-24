package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 补给追踪 —— 人员/水/食物存量持久化(SharedPreferences)。
 * 剩余天数与告警等级由 [RationMath] 纯计算。
 */
class RationStore(context: Context) {

    data class State(
        val persons: Int = 2,
        val waterLiters: Double = 9.0,
        val foodKcal: Double = 13200.0,
        val waterPerPersonDayL: Double = RationMath.WATER_PER_PERSON_DAY_L,
        val foodPerPersonDayKcal: Double = RationMath.FOOD_PER_PERSON_DAY_KCAL,
    ) {
        val waterDays: Double get() = RationMath.waterDaysRemaining(waterLiters, persons, waterPerPersonDayL)
        val foodDays: Double get() = RationMath.foodDaysRemaining(foodKcal, persons, foodPerPersonDayKcal)
        val waterStatus: RationMath.Status get() = RationMath.status(waterDays)
        val foodStatus: RationMath.Status get() = RationMath.status(foodDays)
    }

    private val prefs = context.getSharedPreferences("ration_store", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        State(
            persons = prefs.getInt("persons", 2),
            waterLiters = prefs.getFloat("water", 9f).toDouble(),
            foodKcal = prefs.getFloat("food", 13200f).toDouble(),
        ),
    )
    val state: StateFlow<State> = _state

    private fun persist(s: State) {
        prefs.edit()
            .putInt("persons", s.persons)
            .putFloat("water", s.waterLiters.toFloat())
            .putFloat("food", s.foodKcal.toFloat())
            .apply()
    }

    fun setPersons(v: Int) = _state.update { it.copy(persons = v.coerceIn(1, 50)) }.also { persist(_state.value) }

    fun addWater(dL: Double) = _state.update { it.copy(waterLiters = (it.waterLiters + dL).coerceAtLeast(0.0)) }.also { persist(_state.value) }

    fun addFood(dKcal: Double) = _state.update { it.copy(foodKcal = (it.foodKcal + dKcal).coerceAtLeast(0.0)) }.also { persist(_state.value) }

    fun setWater(v: Double) = _state.update { it.copy(waterLiters = v.coerceAtLeast(0.0)) }.also { persist(_state.value) }

    fun setFood(v: Double) = _state.update { it.copy(foodKcal = v.coerceAtLeast(0.0)) }.also { persist(_state.value) }
}
