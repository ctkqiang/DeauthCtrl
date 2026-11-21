package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.IntelFeedReceiver
import xin.ctkqiang.deauthctrl.manager.IntelFeedManager

/**
 * 情报中心 ViewModel —— 订阅源管理 / 拉取 / 搜索过滤 / 推送开关
 *
 * 所有拉取共用 [IntelFeedManager](免 API Key 的公开 RSS/Atom/JSON),
 * 推送由 AlarmManager 周期唤醒 [IntelFeedReceiver],与页面共用去重集合。
 */
class IntelFeedViewModel(app: Application) : AndroidViewModel(app) {

    data class State(
        val items: List<IntelFeedManager.FeedItem> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val search: String = "",
        val category: String = "all",
        val subscribed: Set<String> = emptySet(),
        val notifyOn: Boolean = false,
        val lastRefreshMs: Long = 0L,
        val lastNewCount: Int = 0,
        val sourcePulls: List<IntelFeedManager.SourcePull> = emptyList(),
    )

    private val manager = IntelFeedManager(app)
    private val _state = MutableStateFlow(
        State(
            subscribed = manager.subscribed(),
            notifyOn = manager.isNotifyEnabled(),
        ),
    )
    val state: StateFlow<State> = _state

    val sources: List<IntelFeedManager.FeedSource> get() = IntelFeedManager.SOURCES

    init {
        // 先读本地缓存:打开页面立即有内容(断网/重启都不空),再后台刷新
        val archived = manager.archivedIds()
        _state.value = _state.value.copy(
            items = manager.loadCache().map { it.copy(archived = it.id in archived) },
        )
        refresh()
    }

    fun refresh() {
        val s = _state.value
        if (s.loading) return
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { manager.refreshDetailed() }
                .onSuccess { (items, pulls) ->
                    val old = _state.value.items
                    val newCount = items.count { n -> old.none { it.id == n.id } }
                    val archived = manager.archivedIds()
                    val merged = items.map { it.copy(archived = it.id in archived) }
                    // 落盘缓存:下次打开/断网立即可读
                    manager.saveCache(merged)
                    _state.value = _state.value.copy(
                        items = merged,
                        sourcePulls = pulls,
                        loading = false,
                        lastRefreshMs = System.currentTimeMillis(),
                        lastNewCount = if (old.isEmpty()) 0 else newCount,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "fetch failed",
                    )
                }
        }
    }

    /** 切换存档(★):钉住重要情报,离线缓存里也保留 */
    fun toggleArchive(id: String) {
        val archived = manager.toggleArchived(id)
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.id == id) it.copy(archived = id in archived) else it },
        )
    }

    /** 切换订阅:立即落盘;通知轮询只拉已订阅源 */
    fun toggleSource(id: String) {
        val cur = _state.value.subscribed.toMutableSet()
        if (id in cur) cur.remove(id) else cur.add(id)
        manager.setSubscribed(cur)
        _state.value = _state.value.copy(subscribed = cur)
    }

    /** 订阅推送开关:调度/取消 AlarmManager 轮询 */
    fun setNotify(on: Boolean) {
        manager.setNotifyEnabled(on)
        _state.value = _state.value.copy(notifyOn = on)
        if (on) {
            // 开启时把当前已有条目标记「已推送」:只推真正的新情报,不把存量刷成通知洪水;
            // 但之后应用打开/刷新不再触碰该集合 —— 否则推送永远没有「新」条目
            _state.value.items.forEach { manager.markNotified(it.link) }
            IntelFeedReceiver.schedule(getApplication())
        } else {
            IntelFeedReceiver.cancel(getApplication())
        }
    }

    /** 页面进入时补挂号闹钟:重启/强制停止/重装会清掉 AlarmManager 闹钟,这里自愈 */
    fun rearmAlarmIfEnabled() {
        if (manager.isNotifyEnabled()) IntelFeedReceiver.schedule(getApplication())
    }

    fun setSearch(v: String) { _state.value = _state.value.copy(search = v) }

    fun setCategory(c: String) { _state.value = _state.value.copy(category = c) }
}
