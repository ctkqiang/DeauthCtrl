package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.manager.BleDevice
import xin.ctkqiang.deauthctrl.manager.RadarTarget
import xin.ctkqiang.deauthctrl.manager.SignalDb
import xin.ctkqiang.deauthctrl.manager.SignalRecord
import xin.ctkqiang.deauthctrl.model.WifiNetwork

/**
 * 信号数据库 ViewModel
 *
 * 职责:持有 [SignalDb],把「刷新」动作转成一次 INSERT 注入 ——
 * 从各扫描模块的当前内存数据(雷达目标 / BLE 嗅探 / WiFi 网络)
 * 快照成记录写入 SQLite,并重载列表与统计。
 */
class DbViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SignalDb(application)

    private val _records = MutableStateFlow<List<SignalRecord>>(emptyList())
    val records: StateFlow<List<SignalRecord>> = _records

    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts

    private val _lastRefresh = MutableStateFlow<Long?>(null)
    val lastRefresh: StateFlow<Long?> = _lastRefresh

    private val _lastInserted = MutableStateFlow(0)
    val lastInserted: StateFlow<Int> = _lastInserted

    init {
        reload()
    }

    /** 刷新注入:把最新数据快照 INSERT 进 SQLite(不删不改历史) */
    fun refresh(radar: List<RadarTarget>, ble: List<BleDevice>, wifi: List<WifiNetwork>) {
        val now = System.currentTimeMillis()
        val rows = mutableListOf<SignalRecord>()
        radar.forEach { t ->
            rows += SignalRecord(
                id = 0,
                source = if (t.type == "wifi") "雷达·WiFi" else "雷达·BLE",
                name = t.name,
                mac = t.id,
                rssi = t.rssi,
                detail = "${t.vendor} · ${t.security.ifBlank { "—" }} · ${t.channel}",
                capturedAt = now,
            )
        }
        ble.forEach { d ->
            rows += SignalRecord(
                id = 0,
                source = "BLE 嗅探",
                name = d.name,
                mac = d.address,
                rssi = d.rssi,
                detail = d.deviceType.ifBlank { "未知类型" },
                capturedAt = now,
            )
        }
        wifi.forEach { n ->
            rows += SignalRecord(
                id = 0,
                source = "WiFi 网络",
                name = n.ssid,
                mac = n.bssid,
                rssi = n.signalStrength,
                detail = "${n.security} · ${n.band} · CH${n.channel}",
                capturedAt = now,
            )
        }
        if (rows.isNotEmpty()) {
            db.insertAll(rows)
            _lastInserted.value = rows.size
            _lastRefresh.value = now
        }
        reload()
    }

    private fun reload() {
        _records.value = db.query(null)
        _counts.value = db.countBySource()
    }
}
