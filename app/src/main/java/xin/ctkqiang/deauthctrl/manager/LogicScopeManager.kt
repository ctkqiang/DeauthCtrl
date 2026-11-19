package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 逻辑示波器的五条数据通道
 *
 * 每条通道独立线程采集，独立环形缓冲；UI 层按 [id] 与 [label] 取用。
 * 颜色不属于管理器（管理器不依赖 Compose），由界面层映射。
 */
enum class ScopeChannel(val id: String, @StringRes val labelRes: Int) {
    /** BLE 广播事件脉冲：幅度 = (RSSI + 100) / 80 */
    BLE("ble", R.string.mgr_lscope_channel_ble),

    /** Wi-Fi 扫描事件脉冲：每 2s 一轮，取最强 AP 的 RSSI */
    WIFI("wifi", R.string.mgr_lscope_channel_wifi),

    /** 蜂窝注册小区电平：每 2s 轮询一次 RSRP */
    CELL("cell", R.string.mgr_lscope_channel_cell),

    /** 麦克风 16kHz 单声道 RMS 包络 */
    AUDIO("audio", R.string.mgr_lscope_channel_audio),

    /** 红外发射脉冲图案（由调用方通过 submitIrPattern 注入） */
    IR("ir", R.string.mgr_lscope_channel_ir),
}

/**
 * 单个示波器采样点
 *
 * @property timeMs 采样时刻（System.currentTimeMillis()；BLE 使用系统广播回调的 lastSeen）
 * @property value01 归一化幅度 0..1，映射公式由通道决定（见 LogicScopeManager 的 KDoc）
 * @property label 事件来源标签（设备名、AP SSID、小区标识、红外标签），可为空
 */
data class ScopePoint(
    val timeMs: Long,
    val value01: Float,
    val label: String? = null,
)

/**
 * 逻辑示波页面的统一状态
 *
 * @property channels 通道 id → 最近 SCOPE_CAPACITY 个采样点（按时间升序）
 * @property running 采集是否运行中
 * @property error 致命错误（例如没有选择任何通道）；通道级问题在 channelNotes
 * @property activeChannels 本次采集实际启动的通道 id 集合
 * @property eventCounts 通道 id → 累计事件数（环形缓冲溢出后仍然累加）
 * @property channelNotes 通道 id → 最近一条通道级提示（权限缺失、扫描节流等）
 * @property updatedAtMs 状态快照时间
 */
data class ScopeState(
    val channels: Map<String, List<ScopePoint>> = emptyMap(),
    val running: Boolean = false,
    val error: String? = null,
    val activeChannels: Set<String> = emptySet(),
    val eventCounts: Map<String, Long> = emptyMap(),
    val channelNotes: Map<String, String> = emptyMap(),
    val updatedAtMs: Long = 0L,
)

/** 每通道环形缓冲容量：最近 600 个点 */
const val SCOPE_CAPACITY = 600

/** 示波器时间窗口：10 秒 */
const val SCOPE_WINDOW_MS = 10_000L

/** 同屏最多通道数 */
const val SCOPE_MAX_CHANNELS = 3

/** 音频采样率：16kHz 单声道（带宽上限 8kHz） */
const val SCOPE_AUDIO_SAMPLE_RATE = 16_000

private const val SCOPE_AUDIO_FRAME_MS = 100
private const val SCOPE_AUDIO_FRAME_SAMPLES = SCOPE_AUDIO_SAMPLE_RATE * SCOPE_AUDIO_FRAME_MS / 1000
private const val SCOPE_BLE_POLL_MS = 50L
private const val SCOPE_WIFI_PERIOD_MS = 2_000L
private const val SCOPE_WIFI_SETTLE_MS = 1_200L
private const val SCOPE_CELL_PERIOD_MS = 2_000L
private const val SCOPE_PUBLISH_MS = 100L
private const val SCOPE_IR_QUEUE_POLL_MS = 200L

/** 默认通道：BLE 脉冲 + 音频 */
val DEFAULT_SCOPE_CHANNELS: Set<String> = setOf(ScopeChannel.BLE.id, ScopeChannel.AUDIO.id)

/**
 * 逻辑示波管理器 —— 多通道射频 / 传感器事件采集
 *
 * ## 通道与幅度映射（全部来自系统真实返回值，不做任何伪造）
 * 1. BLE：轮询 [BleScannerManager.devices] 快照，仅当某设备的 lastSeen 前移时记录一个
 *    事件点，幅度 = (RSSI + 100) / 80，即 -100 dBm → 0、-20 dBm → 100%。
 * 2. Wi-Fi：每 2s 调 WifiManager.startScan()，等待 1.2s 后读 scanResults，取最强 AP 的
 *    level，同一映射；系统节流时读到的缓存结果同样标注为缓存。
 * 3. 蜂窝：每 2s 调 [GsmTowerManager.refresh]，取注册小区的 RSRP（GSM/WCDMA 退回 dBm），
 *    幅度 = (电平 + 140) / 100，即 -140 dBm → 0、-40 dBm → 100%。
 * 4. 音频：AudioRecord 16kHz / 16bit / 单声道，每 100ms（1600 个原始采样）计算一次
 *    RMS，换算为 dBFS 后映射 (dBFS + 80) / 80；画的是 RMS 包络，不是原始音频波形。
 * 5. 红外：由 [submitIrPattern] 注入 IrRemoteManager.transmitRaw 的脉冲图案（微秒、
 *    奇偶交替开/关），通道线程按真实时序回放到时间轴上。
 *
 * ## 诚实边界
 * 脉冲通道画的是「Android 系统上报的扫描事件时刻 + 信号强度」，按采样保持画成方波，
 * 不是空口射频波形的物理采样；蜂窝是 2s 轮询的离散点；示波器语义属于工程调试可视化。
 * 这里没有频谱分析能力，也不做任何插值或平滑 —— 没有数据就留空。
 *
 * ## 线程模型
 * 每条通道一个独立守护线程；[stop] 通过 @Volatile 标志 + interrupt + 停止 AudioRecord /
 * BLE 扫描解除阻塞。状态由发布线程以 10Hz 汇总为 [ScopeState]。
 */
@Suppress("MissingPermission")
class LogicScopeManager(private val context: Context) {

    private val appContext: Context = context.applicationContext
    private val lock = Any()

    private val _state = MutableStateFlow(ScopeState())
    val state: StateFlow<ScopeState> = _state

    /** 通道 id → 环形缓冲（lock 保护） */
    private val buffers = HashMap<String, ArrayDeque<ScopePoint>>()

    /** 通道 id → 累计事件计数（lock 保护） */
    private val counts = HashMap<String, Long>()

    /** 通道 id → 最近一条提示（lock 保护） */
    private val notes = HashMap<String, String>()

    /** 本次采集实际运行的通道（lock 保护） */
    private val active = HashSet<String>()

    /** 通道 id → 采集线程（lock 保护） */
    private val threads = HashMap<String, Thread>()

    @Volatile
    private var running = false

    /** 每次 start/stop 自增；旧线程看到代数变化后自行退出，避免快速重启产生双采集 */
    @Volatile
    private var generation = 0

    @Volatile
    private var fatalError: String? = null

    @Volatile
    private var publisher: Thread? = null

    @Volatile
    private var bleScanner: BleScannerManager? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var gsmTower: GsmTowerManager? = null

    /** 红外脉冲待回放队列（线程安全） */
    private val irQueue = LinkedBlockingQueue<IrBurst>()

    // ── 启停 ────────────────────────────────────────────────────────────────

    /**
     * 启动采集
     *
     * 只启动 [selected] 中有效的通道；重复调用在运行中为空操作。
     * 每次启动清空旧轨迹与旧计数，避免把两段互不相干的会话拼在一张图上。
     */
    fun start(selected: Set<String>) {
        val channels = ScopeChannel.values().filter { it.id in selected }
        if (channels.isEmpty()) {
            synchronized(lock) { fatalError = L10n.get(R.string.mgr_lscope_no_channel_selected) }
            publishNow()
            return
        }

        val plan = synchronized(lock) {
            if (running) return
            running = true
            generation++
            fatalError = null
            buffers.clear()
            counts.clear()
            notes.clear()
            threads.clear()
            active.clear()
            channels.forEach { active.add(it.id) }
            // 每一代采集使用独立的 BleScannerManager：stopScan 只作用于本实例的回调，
            // 快速 stop→start 时旧线程收尾不会误停新采集的扫描。
            StartPlan(
                channels = channels,
                scanner = if (ScopeChannel.BLE.id in active) BleScannerManager(appContext) else null,
            )
        }
        bleScanner = plan.scanner

        plan.channels.forEach { startChannel(it, plan.scanner) }
        ensurePublisher()
        publishNow()
    }

    /** 停止全部通道，保留已经采集到的轨迹供页面冻结查看 */
    fun stop() {
        val toInterrupt = synchronized(lock) {
            if (!running && threads.isEmpty()) return
            running = false
            generation++
            active.clear()
            val snapshot = threads.values.toList()
            threads.clear()
            snapshot
        }
        toInterrupt.forEach { runCatching { it.interrupt() } }
        // 解除阻塞中的音频 read 与 BLE 硬件扫描
        runCatching { audioRecord?.stop() }
        runCatching { bleScanner?.stop() }
        irQueue.clear()
        publishNow()
    }

    /** 页面销毁：停止采集并释放所有缓冲 */
    fun shutdown() {
        stop()
        synchronized(lock) {
            buffers.clear()
            counts.clear()
            notes.clear()
            active.clear()
            fatalError = null
        }
        irQueue.clear()
        _state.value = ScopeState()
    }

    /** 清空轨迹与累计计数（不改变运行状态） */
    fun clear() {
        synchronized(lock) {
            buffers.clear()
            counts.clear()
        }
        publishNow()
    }

    /**
     * 注入一段红外发射脉冲图案
     *
     * 图案单位为微秒、奇偶交替「开/关」，首个值为开启时长 —— 与
     * [IrRemoteManager.transmitRaw] 的 pattern 语义一致。仅当采集运行且红外通道被选中时
     * 才接受；否则直接丢弃，避免把未真实发射的图案画进示波器。
     *
     * 主线程后续可在 IrRemoteManager 发射成功后调用本方法，把真实发射的 pattern 喂进来。
     */
    fun submitIrPattern(
        patternUs: List<Int>,
        label: String = L10n.get(R.string.mgr_lscope_ir_default_label),
    ) {
        val cleaned = patternUs.map { it.coerceAtLeast(0) }
        if (cleaned.isEmpty() || cleaned.all { it == 0 }) return
        val accepting = synchronized(lock) { running && ScopeChannel.IR.id in active }
        if (!accepting) return
        irQueue.offer(IrBurst(cleaned, label.ifBlank { L10n.get(R.string.mgr_lscope_ir_default_label) }))
    }

    // ── 线程管理 ────────────────────────────────────────────────────────────

    /**
     * 每次都启动新的发布线程，并用代数约束旧线程退出
     *
     * 若复用「仍存活」的旧线程，在快速 stop→start 时旧线程可能恰好已经走到循环末尾
     * 而退出，导致新一轮采集没有任何 10Hz 状态发布，轨迹停住。代数判断让旧线程
     * 最迟 100ms 内自行退出，新线程立即接管。
     */
    private fun ensurePublisher() {
        val gen = generation
        val t = Thread({
            while (running && generation == gen) {
                try {
                    Thread.sleep(SCOPE_PUBLISH_MS)
                } catch (_: InterruptedException) {
                    break
                }
                if (generation != gen) break
                // 固定 10Hz 发布：即使没有新事件，也让 10s 时间窗口正常滚动
                publishNow()
            }
        }, "logic-scope-publish")
        t.isDaemon = true
        publisher = t
        t.start()
    }

    private fun startChannel(channel: ScopeChannel, scanner: BleScannerManager?) {
        val gen = synchronized(lock) { generation }
        val t = Thread({ runChannel(channel, gen, scanner) }, "logic-scope-${channel.id}")
        t.isDaemon = true
        synchronized(lock) { threads[channel.id] = t }
        t.start()
    }

    private fun runChannel(channel: ScopeChannel, gen: Int, scanner: BleScannerManager?) {
        try {
            when (channel) {
                ScopeChannel.BLE -> runBleChannel(gen, scanner)
                ScopeChannel.WIFI -> runWifiChannel(gen)
                ScopeChannel.CELL -> runCellChannel(gen)
                ScopeChannel.AUDIO -> runAudioChannel(gen)
                ScopeChannel.IR -> runIrChannel(gen)
            }
        } catch (_: InterruptedException) {
            // stop() 主动打断，正常退出
        } catch (t: Throwable) {
            setNote(
                gen,
                channel.id,
                L10n.get(
                    R.string.mgr_lscope_channel_error,
                    t.message ?: t.javaClass.simpleName,
                ),
            )
        } finally {
            synchronized(lock) {
                // 只有当前代数的线程才有权清理共享表，避免旧线程误删新一轮的状态
                if (generation == gen) {
                    if (threads[channel.id] === Thread.currentThread()) threads.remove(channel.id)
                    active.remove(channel.id)
                }
            }
        }
    }

    private fun isAlive(gen: Int): Boolean = running && generation == gen

    // ── 通道 1：BLE 脉冲 ────────────────────────────────────────────────────

    /**
     * BLE 广播事件通道
     *
     * [BleScannerManager] 暴露的是线程安全 Map 而不是设备流，因此这里以 50ms 周期
     * 快照 devices，比对各设备 lastSeen 是否前移来还原「广播事件」；幅度用系统回调的
     * RSSI 实时值，时间戳用系统回调的 lastSeen，均为真实值。
     */
    private fun runBleChannel(gen: Int, scanner: BleScannerManager?) {
        if (scanner == null) {
            setNote(gen, ScopeChannel.BLE.id, L10n.get(R.string.mgr_lscope_ble_no_scanner))
            return
        }
        if (!isAlive(gen)) return
        val startError = scanner.start()
        if (startError != null) {
            setNote(gen, ScopeChannel.BLE.id, startError)
            return
        }
        if (!isAlive(gen)) {
            // stop() 恰好发生在扫描器启动过程中：回滚本实例，避免停止后仍在扫描
            runCatching { scanner.stop() }
            return
        }
        clearNote(gen, ScopeChannel.BLE.id)
        val lastSeen = HashMap<String, Long>()
        try {
            while (isAlive(gen)) {
                // Map 的遍历顺序不稳定；按 lastSeen 排序后再追加，保证轨迹时间轴单调
                val snapshot = scanner.devices.values.toList().sortedBy { it.lastSeen }
                for (device in snapshot) {
                    if (!isAlive(gen)) break
                    val previous = lastSeen[device.address]
                    if (device.lastSeen > (previous ?: 0L)) {
                        lastSeen[device.address] = device.lastSeen
                        val amplitude = ((device.rssi + 100f) / 80f).coerceIn(0f, 1f)
                        record(
                            gen,
                            ScopeChannel.BLE.id,
                            ScopePoint(
                                timeMs = device.lastSeen,
                                value01 = amplitude,
                                label = device.name.ifBlank { device.address },
                            ),
                        )
                    }
                }
                Thread.sleep(SCOPE_BLE_POLL_MS)
            }
        } finally {
            // 扫描器实例只属于本代采集，停止它不会影响新一轮的扫描
            runCatching { scanner.stop() }
        }
    }

    // ── 通道 2：WiFi 脉冲 ───────────────────────────────────────────────────

    /** Wi-Fi 扫描事件通道：每 2s 一轮，只记录最强 AP 的真实 level 与 SSID */
    private fun runWifiChannel(gen: Int) {
        val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wm == null) {
            setNote(gen, ScopeChannel.WIFI.id, L10n.get(R.string.mgr_lscope_wifi_no_service))
            return
        }
        if (!hasWifiScanPermission()) {
            setNote(gen, ScopeChannel.WIFI.id, L10n.get(R.string.mgr_lscope_wifi_no_permission))
            return
        }
        while (isAlive(gen)) {
            val cycleStart = System.currentTimeMillis()
            val scanAccepted = runCatching { wm.startScan() }.getOrDefault(false)
            Thread.sleep(SCOPE_WIFI_SETTLE_MS)
            if (!isAlive(gen)) break

            val results = runCatching { wm.scanResults }.getOrNull()
            val strongest = results?.filter { it.level in -127..0 }?.maxByOrNull { it.level }
            if (results.isNullOrEmpty()) {
                setNote(gen, ScopeChannel.WIFI.id, L10n.get(R.string.mgr_lscope_wifi_no_results))
            } else if (strongest == null) {
                setNote(gen, ScopeChannel.WIFI.id, L10n.get(R.string.mgr_lscope_wifi_no_rssi))
            } else {
                val amplitude = ((strongest.level + 100f) / 80f).coerceIn(0f, 1f)
                val ssid = strongest.SSID.takeIf { it.isNotBlank() }
                    ?: strongest.BSSID
                    ?: L10n.get(R.string.mgr_lscope_hidden_ssid)
                val label = if (scanAccepted) {
                    ssid
                } else {
                    L10n.get(R.string.mgr_lscope_throttled_cache, ssid)
                }
                record(
                    gen,
                    ScopeChannel.WIFI.id,
                    ScopePoint(System.currentTimeMillis(), amplitude, label),
                )
                if (scanAccepted) {
                    clearNote(gen, ScopeChannel.WIFI.id)
                } else {
                    setNote(gen, ScopeChannel.WIFI.id, L10n.get(R.string.mgr_lscope_throttled_note))
                }
            }

            val elapsed = System.currentTimeMillis() - cycleStart
            Thread.sleep((SCOPE_WIFI_PERIOD_MS - elapsed).coerceAtLeast(200L))
        }
    }

    /**
     * Wi-Fi 扫描权限是否齐备
     *
     * Android 13（API 33）起，targetSdk 33+ 读取 scanResults 需要 NEARBY_WIFI_DEVICES；
     * 本应用清单未给该权限加 neverForLocation（地图 / 测绘需要从 Wi-Fi 结果推导位置），
     * 因此仍同时要求 ACCESS_FINE_LOCATION。
     */
    private fun hasWifiScanPermission(): Boolean {
        val locationGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!locationGranted) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ── 通道 3：蜂窝 ────────────────────────────────────────────────────────

    /** 蜂窝注册小区通道：每 2s 刷新一次 allCellInfo，取注册小区 RSRP（退化用 RSSI） */
    private fun runCellChannel(gen: Int) {
        val tower = gsmTower ?: GsmTowerManager(appContext).also { gsmTower = it }
        if (!tower.hasPermission) {
            setNote(gen, ScopeChannel.CELL.id, L10n.get(R.string.mgr_lscope_cell_no_permission))
            return
        }
        while (isAlive(gen)) {
            tower.refresh()
            val snapshot = tower.state.value
            val registered = snapshot.towers.firstOrNull { it.isRegistered }
            val levelDbm = registered?.rsrpDbm ?: registered?.rssiDbm

            if (snapshot.error != null) {
                setNote(gen, ScopeChannel.CELL.id, snapshot.error!!)
            } else if (registered == null) {
                setNote(gen, ScopeChannel.CELL.id, L10n.get(R.string.mgr_lscope_cell_no_registered))
            } else if (levelDbm == null) {
                setNote(
                    gen,
                    ScopeChannel.CELL.id,
                    L10n.get(R.string.mgr_lscope_cell_no_signal, registered.type),
                )
            } else {
                val amplitude = ((levelDbm + 140f) / 100f).coerceIn(0f, 1f)
                val label = buildString {
                    append(registered.type)
                    append(" · CID ")
                    append(registered.cid)
                    if (registered.mccMnc.isNotBlank()) {
                        append(" · ")
                        append(registered.mccMnc)
                    }
                }
                record(
                    gen,
                    ScopeChannel.CELL.id,
                    ScopePoint(System.currentTimeMillis(), amplitude, label),
                )
                clearNote(gen, ScopeChannel.CELL.id)
            }
            Thread.sleep(SCOPE_CELL_PERIOD_MS)
        }
    }

    // ── 通道 4：音频 ────────────────────────────────────────────────────────

    /**
     * 音频 RMS 包络通道
     *
     * 16kHz / 16bit / 单声道，每帧 1600 个原始采样（100ms）。对整帧求 RMS 后换算
     * dBFS（0 dBFS = 32768 满幅），映射到 -80..0 dBFS → 0..1。画出来的是 10Hz 包络
     * 曲线，不是原始音频波形；16kHz 采样意味着 8kHz 带宽上限。
     */
    private fun runAudioChannel(gen: Int) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setNote(gen, ScopeChannel.AUDIO.id, L10n.get(R.string.mgr_lscope_audio_no_permission))
            return
        }
        val minBuffer = runCatching {
            AudioRecord.getMinBufferSize(
                SCOPE_AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        }.getOrDefault(-1)
        if (minBuffer <= 0) {
            setNote(gen, ScopeChannel.AUDIO.id, L10n.get(R.string.mgr_lscope_audio_unsupported))
            return
        }
        val bufferBytes = maxOf(minBuffer, SCOPE_AUDIO_FRAME_SAMPLES * 2 * 4)
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SCOPE_AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            )
        }.getOrNull()
        if (record == null) {
            setNote(gen, ScopeChannel.AUDIO.id, L10n.get(R.string.mgr_lscope_audio_create_failed))
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            setNote(gen, ScopeChannel.AUDIO.id, L10n.get(R.string.mgr_lscope_audio_init_failed))
            return
        }
        audioRecord = record
        try {
            runCatching { record.startRecording() }
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                setNote(gen, ScopeChannel.AUDIO.id, L10n.get(R.string.mgr_lscope_audio_start_failed))
                return
            }
            clearNote(gen, ScopeChannel.AUDIO.id)
            val frame = ShortArray(SCOPE_AUDIO_FRAME_SAMPLES)
            while (isAlive(gen)) {
                val read = record.read(frame, 0, frame.size)
                if (read <= 0) {
                    if (!isAlive(gen)) break
                    if (read < 0) {
                        setNote(
                            gen,
                            ScopeChannel.AUDIO.id,
                            L10n.get(R.string.mgr_lscope_audio_read_failed, read),
                        )
                        break
                    }
                    continue
                }
                var sum = 0.0
                for (i in 0 until read) {
                    val sample = frame[i].toDouble()
                    sum += sample * sample
                }
                val rms = sqrt(sum / read)
                val dbfs = if (rms <= 0.0) -120.0 else 20.0 * log10(rms / 32768.0)
                val amplitude = ((dbfs + 80.0) / 80.0).coerceIn(0.0, 1.0).toFloat()
                record(
                    gen,
                    ScopeChannel.AUDIO.id,
                    ScopePoint(
                        timeMs = System.currentTimeMillis(),
                        value01 = amplitude,
                        label = "16kHz RMS · %.0f dBFS".format(dbfs),
                    ),
                )
            }
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
            if (audioRecord === record) audioRecord = null
        }
    }

    // ── 通道 5：红外 ────────────────────────────────────────────────────────

    /** 红外通道：等待外部注入的脉冲图案，按真实微秒时序回放到时间轴 */
    private fun runIrChannel(gen: Int) {
        while (isAlive(gen)) {
            val burst = try {
                irQueue.poll(SCOPE_IR_QUEUE_POLL_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                break
            }
            if (burst != null && isAlive(gen)) playIrBurst(burst, gen)
        }
    }

    /**
     * 把一个红外脉冲图案回放到示波器时间轴
     *
     * pattern 首值为「开」，随后开/关交替。每个边沿写入一个点：开 = 1.0、关 = 0.0，
     * 点的时间戳按累积微秒换算成真实毫秒，让方波宽度与真实发射一致。
     */
    private fun playIrBurst(burst: IrBurst, gen: Int) {
        val startMs = System.currentTimeMillis()
        var elapsedUs = 0L
        var mark = true
        for (durationUs in burst.patternUs) {
            if (!isAlive(gen)) return
            val atMs = startMs + elapsedUs / 1000L
            record(
                gen,
                ScopeChannel.IR.id,
                ScopePoint(
                    timeMs = atMs,
                    value01 = if (mark) 1f else 0f,
                    label = burst.label,
                ),
            )
            elapsedUs += durationUs.toLong()
            val targetMs = startMs + elapsedUs / 1000L
            val waitMs = targetMs - System.currentTimeMillis()
            if (waitMs > 0L) {
                try {
                    // 单步最长等待 200ms，防止异常超长图案长时间占住通道线程
                    Thread.sleep(waitMs.coerceAtMost(200L))
                } catch (_: InterruptedException) {
                    return
                }
            }
            mark = !mark
        }
        // 收尾：回到基线，形成完整脉冲
        record(
            gen,
            ScopeChannel.IR.id,
            ScopePoint(System.currentTimeMillis(), 0f, burst.label),
        )
    }

    // ── 缓冲与状态 ──────────────────────────────────────────────────────────

    private fun record(gen: Int, channelId: String, point: ScopePoint) {
        synchronized(lock) {
            // 旧代采集线程的迟到样本不得混入新一轮会话
            if (generation != gen) return
            val deque = buffers.getOrPut(channelId) { ArrayDeque(SCOPE_CAPACITY) }
            while (deque.size >= SCOPE_CAPACITY) deque.removeFirst()
            deque.addLast(point)
            counts[channelId] = (counts[channelId] ?: 0L) + 1L
        }
    }

    private fun setNote(gen: Int, channelId: String, message: String) {
        synchronized(lock) {
            if (generation != gen) return
            notes[channelId] = message
        }
    }

    private fun clearNote(gen: Int, channelId: String) {
        synchronized(lock) {
            if (generation != gen) return
            notes.remove(channelId)
        }
    }

    /** 汇总一份不可变快照推给 UI；固定 10Hz 调用，页面据此滚动时间窗口 */
    private fun publishNow() {
        val snapshot = synchronized(lock) {
            ScopeState(
                channels = buffers.mapValues { it.value.toList() },
                running = running,
                error = fatalError,
                activeChannels = active.toSet(),
                eventCounts = counts.toMap(),
                channelNotes = notes.toMap(),
                updatedAtMs = System.currentTimeMillis(),
            )
        }
        _state.value = snapshot
    }

    /** 本次启动需要拉起的通道与其专属 BLE 扫描器 */
    private data class StartPlan(
        val channels: List<ScopeChannel>,
        val scanner: BleScannerManager?,
    )

    /** 红外待回放图案 */
    private data class IrBurst(val patternUs: List<Int>, val label: String)
}
