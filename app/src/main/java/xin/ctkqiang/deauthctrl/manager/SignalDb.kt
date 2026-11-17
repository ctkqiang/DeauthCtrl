package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 一条信号情报记录 */
data class SignalRecord(
    val id: Long,
    val source: String,   // 数据来源:雷达·WiFi / 雷达·BLE / BLE 嗅探 / WiFi 网络
    val name: String,
    val mac: String,
    val rssi: Int,
    val detail: String,   // 厂商/安全/频段等附加字段
    val capturedAt: Long,
)

/**
 * 信号情报库 —— SQLite(signals.db,应用私有目录)
 *
 * 写入策略:**只 INSERT,不删不改**。刷新按钮把当前内存中的最新
 * 雷达/BLE/WiFi 数据注入数据库,历史记录永久保留形成时间线。
 */
class SignalDb(context: Context) : SQLiteOpenHelper(context.applicationContext, "signals.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE signals (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                name TEXT,
                mac TEXT,
                rssi INTEGER,
                detail TEXT,
                captured_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_signals_time ON signals(captured_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS signals")
        onCreate(db)
    }

    /** 批量注入(仅 INSERT,事务内完成) */
    fun insertAll(records: List<SignalRecord>) {
        if (records.isEmpty()) return
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                db.compileStatement(
                    "INSERT INTO signals(source,name,mac,rssi,detail,captured_at) VALUES(?,?,?,?,?,?)",
                ).use { st ->
                    records.forEach { r ->
                        st.bindString(1, r.source)
                        st.bindString(2, r.name)
                        st.bindString(3, r.mac)
                        st.bindLong(4, r.rssi.toLong())
                        st.bindString(5, r.detail)
                        st.bindLong(6, r.capturedAt)
                        st.executeInsert()
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /** 查询记录(source 为 null 查全部),按时间倒序 */
    fun query(source: String?, limit: Int = 200): List<SignalRecord> = readableDatabase.use { db ->
        val selection = source?.let { " WHERE source=?" }
        val args = source?.let { arrayOf(it) }
        db.query("signals", null, selection, args, null, null, "captured_at DESC, id DESC", limit.toString())
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            SignalRecord(
                                id = c.getLong(0),
                                source = c.getString(1),
                                name = c.getString(2) ?: "",
                                mac = c.getString(3) ?: "",
                                rssi = c.getInt(4),
                                detail = c.getString(5) ?: "",
                                capturedAt = c.getLong(6),
                            ),
                        )
                    }
                }
            }
    }

    /** 各来源记录数统计 */
    fun countBySource(): Map<String, Int> = readableDatabase.use { db ->
        db.rawQuery("SELECT source, COUNT(*) FROM signals GROUP BY source", null).use { c ->
            buildMap {
                while (c.moveToNext()) put(c.getString(0), c.getInt(1))
            }
        }
    }
}
