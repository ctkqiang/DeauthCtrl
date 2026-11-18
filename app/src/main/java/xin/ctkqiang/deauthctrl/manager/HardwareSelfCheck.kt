package xin.ctkqiang.deauthctrl.manager

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ─── 快照数据结构 ────────────────────────────────────────────────────────────
//
// 纪律:所有字段只承载系统真实返回值;系统未提供的字段保持 null / “不可用”,
// 绝不填 0、-127 一类的伪值。UI 端统一把 null 渲染为 “--”。

/**
 * 电池快照。
 *
 * - [currentNowUa] 单位为微安,正数 = 电流进入电池(充电),负数 = 放电,
 *   这是 [BatteryManager.BATTERY_PROPERTY_CURRENT_NOW] 的公开约定;
 *   个别厂商会反转符号,因此界面同时展示 [direction](以系统 status 为准)。
 * - [chargeCounterUah] 单位为微安时,系统支持时才有值。
 * - [temperatureC] 来自 ACTION_BATTERY_CHANGED 的 EXTRA_TEMPERATURE(0.1 °C)。
 *   公开 SDK 中并不存在 BATTERY_PROPERTY_TEMPERATURE,因此没有其它读取通道。
 */
data class BatterySnapshot(
    val present: Boolean? = null,
    val capacityPercent: Int? = null,
    val currentNowUa: Long? = null,
    val chargeCounterUah: Long? = null,
    val temperatureC: Float? = null,
    val voltageMv: Int? = null,
    val health: String = "不可用",
    val healthAlert: Boolean = false,
    val status: String = "不可用",
    val direction: String = "不可用",
    val plugged: String = "不可用",
    val technology: String = "不可用",
)

/** 单个热区读数 —— 名称取自 /sys/class/thermal/thermal_zoneN/type */
data class ThermalZone(
    val name: String,
    val tempC: Float,
)

/** 热区快照:最高 6 个 + 实际可读热区总数 */
data class ThermalSnapshot(
    val topZones: List<ThermalZone> = emptyList(),
    val readableZones: Int = 0,
) {
    val available: Boolean get() = topZones.isNotEmpty()
}

/** CPU 快照 —— 两次 /proc/stat 采样的窗口估算值(非瞬时精确值) */
data class CpuSnapshot(
    val usagePercent: Int? = null,
    val coreCount: Int = Runtime.getRuntime().availableProcessors(),
    val sampleWindowMs: Long = 0L,
)

/** 内存快照 —— ActivityManager.MemoryInfo 系统返回值 */
data class MemorySnapshot(
    val totalBytes: Long? = null,
    val availBytes: Long? = null,
    val usedPercent: Int? = null,
    val lowMemory: Boolean = false,
)

/** 内部存储(/data)快照 —— StatFs 系统返回值 */
data class StorageSnapshot(
    val totalBytes: Long? = null,
    val freeBytes: Long? = null,
    val usedPercent: Int? = null,
)

/** 一条传感器硬件信息 —— 全部取自 SensorManager.getSensorList */
data class SensorEntry(
    val name: String,
    val vendor: String,
    val type: Int,
    val typeName: String,
    val resolution: Float,
    val powerMa: Float,
    val isCommon: Boolean,
)

/** 本机 Wi-Fi 链路快照 —— 复用 WalkieLinkInfo 的读取思路,但不依赖该类 */
data class NetworkSnapshot(
    val wifiEnabled: Boolean? = null,
    val connected: Boolean = false,
    val ssid: String? = null,
    val bssid: String? = null,
    val rssiDbm: Int? = null,
    val signalLevel: Int? = null,
    val linkSpeedMbps: Int? = null,
    val txLinkSpeedMbps: Int? = null,
    val rxLinkSpeedMbps: Int? = null,
    val frequencyMhz: Int? = null,
    val channel: Int? = null,
    val localIp: String? = null,
    val gatewayIp: String? = null,
    val wifiStandard: String? = null,
    val locationGranted: Boolean = false,
)

/** 一次完整自检的快照 */
data class SelfCheckState(
    val battery: BatterySnapshot = BatterySnapshot(),
    val thermal: ThermalSnapshot = ThermalSnapshot(),
    val cpu: CpuSnapshot = CpuSnapshot(),
    val memory: MemorySnapshot = MemorySnapshot(),
    val storage: StorageSnapshot = StorageSnapshot(),
    val sensors: List<SensorEntry> = emptyList(),
    val network: NetworkSnapshot = NetworkSnapshot(),
    /** 由热区与电池健康推导出的告警原因(不含 “[!]” 前缀,UI 负责加) */
    val alerts: List<String> = emptyList(),
    val loading: Boolean = false,
    val updatedAtMs: Long = 0L,
)

/**
 * 本机硬件自检台 —— 一次性读取真实系统数据
 *
 * 数据来源:
 * 1. 电池:BatteryManager.getIntProperty/getLongProperty + ACTION_BATTERY_CHANGED 粘性广播
 *    (容量、电流、电荷计数器、温度、健康、状态、供电来源、电压、电池技术)
 * 2. 热区:遍历 /sys/class/thermal/thermal_zoneN/{type,temp},换算 °C 后降序取前 6
 * 3. 负载:/proc/stat 两次采样(250ms 窗口)算 CPU 使用率;ActivityManager.MemoryInfo;
 *    Environment.getDataDirectory() 上的 StatFs
 * 4. 传感器:SensorManager.getSensorList(TYPE_ALL),按常用/其他分组
 * 5. 网络:WifiManager.connectionInfo / dhcpInfo(SSID 需定位权限,系统隐去时标不可用)
 *
 * 读不到的字段一律置空,由 UI 显示 “--” 或 “不可用”;不做任何兜底编造。
 */
class HardwareSelfCheck(private val context: Context) {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(SelfCheckState())
    val state: StateFlow<SelfCheckState> = _state.asStateFlow()

    @Volatile
    private var refreshing = false

    /** 刷新一次完整快照;并发调用时只保留第一轮 */
    suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        try {
            _state.value = _state.value.copy(loading = true)
            withContext(Dispatchers.IO) {
                val battery = readBattery()
                val thermal = readThermal()
                val cpu = readCpu()
                val memory = readMemory()
                val storage = readStorage()
                val sensors = readSensors()
                val network = readNetwork()

                // 整体结论只依据热区与电池健康,与页面约定一致
                val alerts = buildList {
                    thermal.topZones.firstOrNull()?.takeIf { it.tempC > THERMAL_ALERT_C }?.let { hottest ->
                        add(
                            "热区 ${hottest.name} 达 " +
                                String.format(Locale.US, "%.1f", hottest.tempC) +
                                " °C,超过 45 °C"
                        )
                    }
                    if (battery.healthAlert) {
                        add("电池健康异常:${battery.health}")
                    }
                }

                _state.value = SelfCheckState(
                    battery = battery,
                    thermal = thermal,
                    cpu = cpu,
                    memory = memory,
                    storage = storage,
                    sensors = sensors,
                    network = network,
                    alerts = alerts,
                    loading = false,
                    updatedAtMs = System.currentTimeMillis(),
                )
            }
        } finally {
            refreshing = false
            // 协程被取消时也要把 loading 落回 false,避免页面停在“读取中”
            if (_state.value.loading) _state.value = _state.value.copy(loading = false)
        }
    }

    // ─── 电池 ────────────────────────────────────────────────────────────────

    /**
     * 读取电池快照。
     *
     * 温度没有公开的 BatteryManager 属性(BATTERY_PROPERTY_TEMPERATURE 并不存在于
     * android.jar),只能读粘性广播的 EXTRA_TEMPERATURE,单位 0.1 °C。
     */
    @Suppress("DEPRECATION")
    private fun readBattery(): BatterySnapshot {
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val sticky: Intent? = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        fun intExtra(key: String): Int =
            sticky?.getIntExtra(key, Int.MIN_VALUE) ?: Int.MIN_VALUE

        // 容量优先取系统属性,不支持时退回粘性广播的 level/scale
        val stickyLevel = intExtra(BatteryManager.EXTRA_LEVEL)
        val stickyScale = intExtra(BatteryManager.EXTRA_SCALE)
        val stickyCapacity = if (stickyLevel in 0..100 && stickyScale > 0 && stickyLevel <= stickyScale) {
            (stickyLevel * 100f / stickyScale).roundToInt().coerceIn(0, 100)
        } else {
            null
        }
        val propertyCapacity = runCatching {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrNull()?.takeIf { it in 0..100 }
        val capacity = propertyCapacity ?: stickyCapacity

        val currentNow = runCatching {
            batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull()?.takeIf { it != Long.MIN_VALUE && abs(it) <= 5_000_000L }

        val chargeCounter = runCatching {
            batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrNull()?.takeIf { it in 1L..20_000_000L }

        val temperatureC = intExtra(BatteryManager.EXTRA_TEMPERATURE)
            .takeIf { it != Int.MIN_VALUE && it in -400..1500 }
            ?.let { it / 10f }

        val voltageMv = intExtra(BatteryManager.EXTRA_VOLTAGE).takeIf { it in 1000..6000 }

        val healthCode = intExtra(BatteryManager.EXTRA_HEALTH)
        val health = batteryHealthName(healthCode)
        val healthAlert = healthCode != Int.MIN_VALUE &&
            healthCode != BatteryManager.BATTERY_HEALTH_GOOD &&
            healthCode != BatteryManager.BATTERY_HEALTH_UNKNOWN

        val statusCode = intExtra(BatteryManager.EXTRA_STATUS)
        val status = batteryStatusName(statusCode)

        val pluggedFlags = intExtra(BatteryManager.EXTRA_PLUGGED)
        val plugged = when {
            pluggedFlags == Int.MIN_VALUE -> "不可用"
            else -> buildList {
                if (pluggedFlags and BatteryManager.BATTERY_PLUGGED_AC != 0) add("AC")
                if (pluggedFlags and BatteryManager.BATTERY_PLUGGED_USB != 0) add("USB")
                if (pluggedFlags and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0) add("无线充电")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    pluggedFlags and BatteryManager.BATTERY_PLUGGED_DOCK != 0
                ) {
                    add("底座")
                }
            }.joinToString(" / ").ifBlank { "未连接电源" }
        }

        val direction = when (statusCode) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满(外接电源)"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
            else -> when {
                currentNow == null -> "不可用"
                currentNow > 0L -> "充电中(按电流符号)"
                currentNow < 0L -> "放电中(按电流符号)"
                else -> "无净电流"
            }
        }

        val present = sticky?.let {
            if (it.hasExtra(BatteryManager.EXTRA_PRESENT)) {
                it.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
            } else {
                null
            }
        }

        val technology = sticky?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?.takeIf { it.isNotBlank() }
            ?: "不可用"

        return BatterySnapshot(
            present = present,
            capacityPercent = capacity,
            currentNowUa = currentNow,
            chargeCounterUah = chargeCounter,
            temperatureC = temperatureC,
            voltageMv = voltageMv,
            health = health,
            healthAlert = healthAlert,
            status = status,
            direction = direction,
            plugged = plugged,
            technology = technology,
        )
    }

    private fun batteryHealthName(code: Int): String = when (code) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
        BatteryManager.BATTERY_HEALTH_DEAD -> "失效"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
        BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "故障(未指明)"
        BatteryManager.BATTERY_HEALTH_UNKNOWN -> "未知"
        Int.MIN_VALUE -> "不可用"
        else -> "未知($code)"
    }

    private fun batteryStatusName(code: Int): String = when (code) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
        BatteryManager.BATTERY_STATUS_FULL -> "已充满"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
        BatteryManager.BATTERY_STATUS_UNKNOWN -> "未知"
        Int.MIN_VALUE -> "不可用"
        else -> "未知($code)"
    }

    // ─── 热区 ────────────────────────────────────────────────────────────────

    /**
     * 遍历 /sys/class/thermal/thermal_zone*。
     *
     * temp 的常规单位是毫摄氏度(如 45000 = 45 °C);少数内核直接写摄氏度,
     * 因此绝对值 ≥ 1000 时按毫度换算,否则按度处理。超出 -40..150 °C 的读数
     * 视为无效(部分厂商会写 0 或极端哨兵值)。路径因厂商/内核而异,读不到
     * 时返回空列表,由 UI 显示不可用。
     */
    private fun readThermal(): ThermalSnapshot {
        val dirs = runCatching {
            File("/sys/class/thermal").listFiles()?.filter {
                it.isDirectory && it.name.startsWith("thermal_zone")
            }
        }.getOrNull().orEmpty()

        val zones = dirs.mapNotNull { dir ->
            val type = runCatching { File(dir, "type").readText().trim() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: dir.name
            val raw = runCatching { File(dir, "temp").readText().trim().toLong() }
                .getOrNull()
                ?: return@mapNotNull null
            val celsius = if (abs(raw) >= 1000L) raw / 1000f else raw.toFloat()
            if (celsius !in -40f..150f) return@mapNotNull null
            ThermalZone(type, celsius)
        }.sortedByDescending { it.tempC }

        return ThermalSnapshot(
            topZones = zones.take(MAX_THERMAL_ZONES),
            readableZones = zones.size,
        )
    }

    // ─── CPU / 内存 / 存储 ───────────────────────────────────────────────────

    /** /proc/stat 两次采样:usage = (Δtotal - Δidle) / Δtotal */
    private suspend fun readCpu(): CpuSnapshot {
        val first = readProcStat() ?: return CpuSnapshot()
        delay(CPU_SAMPLE_WINDOW_MS)
        val second = readProcStat() ?: return CpuSnapshot()
        val totalDelta = second.first - first.first
        val idleDelta = second.second - first.second
        if (totalDelta <= 0L) return CpuSnapshot()
        val usage = ((totalDelta - idleDelta).toDouble() / totalDelta * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
        return CpuSnapshot(
            usagePercent = usage,
            coreCount = Runtime.getRuntime().availableProcessors(),
            sampleWindowMs = CPU_SAMPLE_WINDOW_MS,
        )
    }

    /** 读取 /proc/stat 首行,返回 (total, idle+iowait) */
    private fun readProcStat(): Pair<Long, Long>? {
        val line = runCatching {
            File("/proc/stat").useLines { seq -> seq.firstOrNull { it.startsWith("cpu ") } }
        }.getOrNull() ?: return null
        // 字段:user nice system idle iowait irq softirq steal [guest guest_nice]
        // guest 已包含在 user/nice 中,只取前 8 项避免重复计数
        val nums = line.trim().split(Regex("\\s+"))
            .drop(1)
            .take(8)
            .mapNotNull { it.toLongOrNull() }
        if (nums.size < 4) return null
        val idle = nums[3] + nums.getOrElse(4) { 0L }
        return nums.sum() to idle
    }

    private fun readMemory(): MemorySnapshot {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return MemorySnapshot()
        return runCatching {
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val total = info.totalMem.takeIf { it > 0L }
            val avail = info.availMem.takeIf { it >= 0L }
            val usedPercent = if (total != null && avail != null) {
                ((total - avail).toDouble() / total * 100.0).roundToInt().coerceIn(0, 100)
            } else {
                null
            }
            MemorySnapshot(
                totalBytes = total,
                availBytes = avail,
                usedPercent = usedPercent,
                lowMemory = info.lowMemory,
            )
        }.getOrDefault(MemorySnapshot())
    }

    private fun readStorage(): StorageSnapshot = runCatching {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val usedPercent = if (total > 0L) {
            ((total - free).toDouble() / total * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            null
        }
        StorageSnapshot(totalBytes = total, freeBytes = free, usedPercent = usedPercent)
    }.getOrDefault(StorageSnapshot())

    // ─── 传感器 ──────────────────────────────────────────────────────────────

    private fun readSensors(): List<SensorEntry> {
        val sm = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return emptyList()
        val list = runCatching { sm.getSensorList(Sensor.TYPE_ALL) }.getOrNull().orEmpty()
        return list.map { sensor ->
            SensorEntry(
                name = runCatching { sensor.name }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "未命名传感器",
                vendor = runCatching { sensor.vendor }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "未知厂商",
                type = sensor.type,
                typeName = sensorTypeName(sensor.type),
                resolution = runCatching { sensor.resolution }.getOrDefault(0f),
                powerMa = runCatching { sensor.power }.getOrDefault(0f),
                isCommon = sensor.type in COMMON_SENSOR_TYPES,
            )
        }.sortedWith(
            compareByDescending<SensorEntry> { it.isCommon }.thenBy { it.typeName },
        )
    }

    private fun sensorTypeName(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "加速度计"
        Sensor.TYPE_GYROSCOPE -> "陀螺仪"
        Sensor.TYPE_MAGNETIC_FIELD -> "磁力计"
        Sensor.TYPE_LIGHT -> "环境光"
        Sensor.TYPE_PROXIMITY -> "距离感应"
        Sensor.TYPE_PRESSURE -> "气压计"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "环境温度"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "相对湿度"
        Sensor.TYPE_GRAVITY -> "重力"
        Sensor.TYPE_LINEAR_ACCELERATION -> "线性加速度"
        Sensor.TYPE_ROTATION_VECTOR -> "旋转矢量"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "游戏旋转矢量"
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "地磁旋转矢量"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "显著运动"
        Sensor.TYPE_STEP_COUNTER -> "计步器"
        Sensor.TYPE_STEP_DETECTOR -> "步伐检测"
        Sensor.TYPE_HEART_RATE -> "心率"
        Sensor.TYPE_HEART_BEAT -> "心跳检测"
        Sensor.TYPE_POSE_6DOF -> "6DoF 姿态"
        Sensor.TYPE_STATIONARY_DETECT -> "静止检测"
        Sensor.TYPE_MOTION_DETECT -> "运动检测"
        Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "离体检测"
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "未校准加速度计"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "未校准陀螺仪"
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "未校准磁力计"
        Sensor.TYPE_HEADING -> "朝向"
        Sensor.TYPE_HINGE_ANGLE -> "铰链角度"
        else -> "类型 $type"
    }

    // ─── 网络 ────────────────────────────────────────────────────────────────

    /**
     * Wi-Fi 链路信息。
     *
     * SSID/BSSID 在未授予定位权限时会被系统隐去(<unknown ssid> / 02:00:...),
     * 这类值一律置 null;链路速率在 API 29+ 才有分收发的字段。
     */
    @Suppress("DEPRECATION", "MissingPermission")
    private fun readNetwork(): NetworkSnapshot {
        val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return NetworkSnapshot()
        val locationGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val wifiEnabled = runCatching { wm.isWifiEnabled }.getOrNull()
        val info = runCatching { wm.connectionInfo }.getOrNull()
            ?: return NetworkSnapshot(wifiEnabled = wifiEnabled, locationGranted = locationGranted)

        val ssid = info.ssid?.removeSurrounding("\"")?.takeIf {
            it.isNotBlank() && it != "<unknown ssid>" && it != "0x"
        }
        val bssid = info.bssid?.takeIf {
            it.isNotBlank() &&
                it != "02:00:00:00:00:00" &&
                it != "00:00:00:00:00:00"
        }
        val localIp = runCatching { Formatter.formatIpAddress(info.ipAddress) }
            .getOrNull()
            ?.takeIf { it != "0.0.0.0" }
        val gatewayIp = runCatching { Formatter.formatIpAddress(wm.dhcpInfo.gateway) }
            .getOrNull()
            ?.takeIf { it != "0.0.0.0" }
        val rssi = info.rssi.takeIf { it in -126..-1 }
        val frequency = info.frequency.takeIf { it > 0 }
        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                when (info.wifiStandard) {
                    1 -> "802.11a"
                    2 -> "802.11b"
                    3 -> "802.11g"
                    4 -> "802.11n"
                    5 -> "802.11ac"
                    6 -> "802.11ax"
                    7 -> "802.11be"
                    else -> null
                }
            }.getOrNull()
        } else {
            null
        }

        return NetworkSnapshot(
            wifiEnabled = wifiEnabled,
            // 无定位权限时 SSID 会被隐去,因此连接状态同时参考 supplicant 状态
            connected = info.networkId != -1 ||
                info.supplicantState == android.net.wifi.SupplicantState.COMPLETED,
            ssid = ssid,
            bssid = bssid,
            rssiDbm = rssi,
            signalLevel = rssi?.let { WifiManager.calculateSignalLevel(it, 5).takeIf { level -> level in 0..4 } },
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
            txLinkSpeedMbps = if (Build.VERSION.SDK_INT >= 29) {
                runCatching { info.txLinkSpeedMbps }.getOrNull()?.takeIf { it > 0 }
            } else {
                null
            },
            rxLinkSpeedMbps = if (Build.VERSION.SDK_INT >= 29) {
                runCatching { info.rxLinkSpeedMbps }.getOrNull()?.takeIf { it > 0 }
            } else {
                null
            },
            frequencyMhz = frequency,
            channel = frequency?.let(::channelFromFrequency)?.takeIf { it > 0 },
            localIp = localIp,
            gatewayIp = gatewayIp,
            wifiStandard = standard,
            locationGranted = locationGranted,
        )
    }

    /** 频率 → 主信道(2.4/5/6 GHz 常见映射) */
    private fun channelFromFrequency(freqMhz: Int): Int = when {
        freqMhz in 2412..2484 -> (freqMhz - 2412) / 5 + 1
        freqMhz in 5180..5825 -> (freqMhz - 5180) / 5 + 36
        freqMhz in 5955..7115 -> (freqMhz - 5950) / 5
        else -> 0
    }

    private companion object {
        /** 热区告警阈值(°C),与页面显示规则一致 */
        const val THERMAL_ALERT_C = 45f

        /** 热区只取最高 6 个 */
        const val MAX_THERMAL_ZONES = 6

        /** CPU 两次采样之间的窗口 */
        const val CPU_SAMPLE_WINDOW_MS = 250L

        /** 常用传感器类型 —— UI 按常用/其他分组 */
        val COMMON_SENSOR_TYPES: Set<Int> = setOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_PROXIMITY,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_STEP_COUNTER,
            Sensor.TYPE_STEP_DETECTOR,
            Sensor.TYPE_AMBIENT_TEMPERATURE,
            Sensor.TYPE_RELATIVE_HUMIDITY,
            Sensor.TYPE_HEART_RATE,
        )
    }
}
