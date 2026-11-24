package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.manager.WantedManager

/**
 * 通缉令 ViewModel —— 独立功能,只依赖 [WantedManager]:
 * 拉取 FBI / Interpol / ICE / NCA 公开通缉数据(官方发布),
 * 与情报中心的订阅/缓存/推送体系完全隔离。
 */
class WantedViewModel(app: Application) : AndroidViewModel(app) {

    data class State(
        val items: List<WantedManager.WantedItem> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val search: String = "",
        val lastRefreshMs: Long = 0L,
        /** 每源拉取状态:UI 如实呈现哪个源失败(如 Interpol 被 403 封锁) */
        val pulls: List<WantedManager.SourcePull> = emptyList(),
    )

    private val manager = WantedManager()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        refresh()
    }

    fun refresh() {
        val s = _state.value
        if (s.loading) return
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { manager.refresh() }
                .onSuccess { (items, pulls) ->
                    _state.value = _state.value.copy(
                        items = items,
                        pulls = pulls,
                        loading = false,
                        lastRefreshMs = System.currentTimeMillis(),
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = it.message ?: "fetch failed")
                }
        }
    }

    fun setSearch(v: String) { _state.value = _state.value.copy(search = v) }
}
