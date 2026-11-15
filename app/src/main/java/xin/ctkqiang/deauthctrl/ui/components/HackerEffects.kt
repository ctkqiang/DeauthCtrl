package xin.ctkqiang.deauthctrl.ui.components

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * CRT 扫描线叠加层
 *
 * 在屏幕上方绘制均匀分布的水平红色线条，模拟 CRT 显示器的扫描线效果。
 * 使用 Compose drawBehind 直接绘制，性能优异——不分配额外 Composable。
 *
 * 线条颜色由 color 参数指定，通过 alpha 控制透明度。
 * 在深色背景上使用 0.03-0.06 的 alpha 值效果最佳，
 * 过低（< 0.02）肉眼不可见，过高（> 0.10）影响文字可读性。
 *
 * @param lineSpacing 扫描线间距（dp），越小越密集。默认 3dp，推荐 2-4dp
 * @param alpha 线条透明度（0.0-1.0），默认 0.04
 * @param color 线条颜色，默认红色（0xFFFF0000）
 */
@Composable
fun ScanlineOverlay(
    lineSpacing: Dp = 3.dp,
    alpha: Float = 0.04f,
    color: Color = Color(0xFFFF0000),
) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { lineSpacing.toPx() }
    Box(
        Modifier.fillMaxSize().drawBehind {
            var y = 0f
            while (y < size.height) {
                drawLine(color.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y), 1f)
                y += px
            }
        },
    )
}

/**
 * 故障效果文字（Glitch Text）
 *
 * 模拟数字故障/毛刺（glitch）效果的文字组件，灵感来自 Mr. Robot 片头视觉风格。
 * 每间隔 glitchIntervalMs 毫秒触发一次故障动画：
 * 1. 红色副本向右偏移（模拟 RGB 通道错位）
 * 2. 白色副本向左偏移（模拟信号过载/亮度闪烁）
 * 3. 青色副本向左偏移（模拟色偏修复延迟）
 * 4. 恢复原始位置
 *
 * 动画周期约 140ms（50+40+50），足够短以保证"闪现"效果。
 * 三层偏移副本配合透明度和不同方向偏移量，营造层次化的故障感。
 *
 * @param text 显示的文字内容
 * @param glitchIntervalMs 故障触发间隔（毫秒），默认 4000ms（每 4 秒故障一次）
 * @param style Compose TextStyle 样式（字号、字距等），默认 28.sp
 * @param modifier Modifier 修饰符，用于控制布局
 */
@Composable
fun GlitchText(
    text: String,
    glitchIntervalMs: Long = 4000,
    style: TextStyle = TextStyle(fontSize = 28.sp),
    modifier: Modifier = Modifier,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var showRed by remember { mutableStateOf(false) }
    var showCyan by remember { mutableStateOf(false) }
    var showBright by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(glitchIntervalMs)
            offsetX = Random.nextInt(-6, 7).toFloat()
            showRed = true; delay(50)
            showRed = false; showBright = true; delay(40)
            showBright = false; showCyan = true; delay(50)
            showCyan = false; offsetX = 0f
        }
    }

    Box(modifier = modifier) {
        if (showRed) Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = style, color = Color.Red.copy(alpha = 0.7f), modifier = Modifier.offset(x = offsetX.dp))
        if (showBright) Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = style, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.offset(x = -offsetX.dp / 2))
        if (showCyan) Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = style, color = Color.Cyan.copy(alpha = 0.35f), modifier = Modifier.offset(x = -offsetX.dp))
        Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = style, color = Color(0xFFFF0000))
    }
}

/**
 * 脉冲呼吸指示灯
 *
 * 一个小圆点，以正弦波周期性地改变透明度，模拟"呼吸"或"闪烁"效果。
 * 用于指示系统/模块运行状态（红色脉冲 = 运行中，暗红 = 停止）。
 *
 * 动画细节：
 * - phase 每 60ms 递增 0.06 → 约 1.05 秒完成一个完整呼吸周期 (2π / 0.06 × 60ms)
 * - 透明度范围 0.20-1.0（sin 输出 * 0.45 + 0.5，再 coerceIn）
 *
 * @param color 指示灯颜色，默认红色
 * @param size 圆点直径（dp），默认 10dp
 * @param modifier Modifier 修饰符
 */
@Composable
fun PulseDot(color: Color = Color(0xFFFF0000), size: Dp = 10.dp, modifier: Modifier = Modifier) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { delay(60); phase += 0.06f } }
    val a = (sin(phase * PI).toFloat() * 0.45f + 0.5f)
    Box(modifier = modifier.size(size).clip(CircleShape).background(color.copy(alpha = a.coerceIn(0.2f, 1f))))
}

/**
 * ASCII 分隔线
 *
 * 使用全角破折号字符（'─'）重复 48 次构成一条水平装饰分隔线。
 * 终端/命令行风格的分隔元素，用于区分 UI 中的不同逻辑区块。
 *
 * 适用场景：卡片分隔、章节标题下划线、日志输出与内容区之间的分界。
 *
 * @param modifier Modifier 修饰符，用于控制外边距和宽度
 */
@Composable
fun AsciiDivider(modifier: Modifier = Modifier) {
    Text("─".repeat(48), fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Color(0xFFFF0000).copy(alpha = 0.3f), modifier = modifier)
}

// ─── 启动自检 ────────────────────────────────────────────────────────────────

/** 自检结论等级 */
private enum class CheckLevel { OK, WARN, FAIL }

/**
 * 一项启动自检结果
 *
 * @param label 检查项名称
 * @param detail 实测到的具体值或结论 —— 不用 "OK" 这类无信息量的词填充
 * @param level 结论等级，决定右侧标记与配色
 */
private class StartupCheck(val label: String, val detail: String, val level: CheckLevel)

/** 自检项逐条浮现的间隔（毫秒） */
private const val CHECK_REVEAL_INTERVAL_MS = 150L

/** 自检全部显示完毕后停留多久再进主界面 */
private const val DONE_HOLD_MS = 450L

/** 可用内存低于总量的该比例时给出警告 */
private const val LOW_MEMORY_RATIO = 0.15

/** 可用存储低于 100MB 时给出警告（加密保险箱需要落盘空间） */
private const val LOW_STORAGE_BYTES = 100L * 1024L * 1024L

/** 取当前活跃网络的接口名；无活动网络或系统未提供时返回 null */
private fun activeInterfaceName(connectivity: ConnectivityManager?): String? {
    val manager = connectivity ?: return null
    val network = manager.activeNetwork ?: return null
    return runCatching { manager.getLinkProperties(network)?.interfaceName }.getOrNull()
}

/** 字节 → "1.8GB" */
private fun formatGigabytes(bytes: Long): String =
    String.format(Locale.US, "%.1fGB", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))

/**
 * 采集真实的启动自检结果
 *
 * 每一项都来自系统 API 实测。这一步替代了原先那套「加载内核模块 / 挂载文件系统 /
 * 激活隐蔽模式」的固定文案 —— 那些操作本应用一步都不执行，却一律勾上 `[ OK ]`，
 * 是纯粹的表演。现在每一行都反映设备此刻的真实状态，出了问题在第一屏就能看见，
 * 不必进到具体模块才发现功能不可用。
 *
 * 读不到的项直接不显示，而不是补一个占位值。
 *
 * @param context 用于查询权限、蓝牙、网络、内存与存储
 * @return 按重要性排序的自检结果
 */
private fun collectStartupChecks(context: Context): List<StartupCheck> = buildList {
    // 1. 运行时权限 —— 这一项失败会让所有扫描类功能一起失效
    val missing = RequiredPermissions.missing(context)
    add(
        if (missing.isEmpty()) StartupCheck("运行时权限", "全部已授予", CheckLevel.OK)
        else StartupCheck("运行时权限", "缺 ${missing.size} 项，相关功能不可用", CheckLevel.FAIL)
    )

    // 2. 蓝牙适配器
    when (val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter) {
        null -> add(StartupCheck("蓝牙适配器", "此设备没有蓝牙硬件", CheckLevel.FAIL))
        else -> add(
            if (adapter.isEnabled) StartupCheck("蓝牙适配器", "已就绪", CheckLevel.OK)
            else StartupCheck("蓝牙适配器", "已关闭，BLE 功能需先开启", CheckLevel.WARN)
        )
    }

    // 3. BLE 扫描权限（Android 12 起独立成权；12 以下由定位权限代偿）
    val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Manifest.permission.BLUETOOTH_SCAN
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }
    val scanGranted = ContextCompat.checkSelfPermission(context, scanPermission) == PackageManager.PERMISSION_GRANTED
    add(
        if (scanGranted) StartupCheck("BLE 扫描权限", "已授予", CheckLevel.OK)
        else StartupCheck("BLE 扫描权限", "未授予，BLE 嗅探不可用", CheckLevel.FAIL)
    )

    // 4. 活跃网络接口 —— 局域网类功能（Web 服务、文件快传、对讲机）都依赖它
    val iface = activeInterfaceName(context.getSystemService(ConnectivityManager::class.java))
    add(
        if (iface.isNullOrEmpty()) StartupCheck("网络接口", "无活动网络，局域网功能受限", CheckLevel.WARN)
        else StartupCheck("网络接口", "$iface 在线", CheckLevel.OK)
    )

    // 5. 可用内存
    val memory = ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
    if (memory.totalMem > 0L) {
        val detail = "${formatGigabytes(memory.availMem)} / ${formatGigabytes(memory.totalMem)}"
        val low = memory.availMem.toDouble() / memory.totalMem < LOW_MEMORY_RATIO
        add(
            if (low) StartupCheck("可用内存", "$detail（偏低）", CheckLevel.WARN)
            else StartupCheck("可用内存", detail, CheckLevel.OK)
        )
    }

    // 6. 可用存储
    val free = context.filesDir.usableSpace
    if (free > 0L) {
        add(
            if (free < LOW_STORAGE_BYTES) StartupCheck("存储空间", "${formatGigabytes(free)}（偏低）", CheckLevel.WARN)
            else StartupCheck("存储空间", "${formatGigabytes(free)} 可用", CheckLevel.OK)
        )
    }
}

/** 等级 → 右侧标记，用有区分度的字，不用统一的 "OK" 掩盖差异 */
private fun CheckLevel.tag(): String = when (this) {
    CheckLevel.OK -> "[ OK ]"
    CheckLevel.WARN -> "[WARN]"
    CheckLevel.FAIL -> "[FAIL]"
}

/** 等级 → 配色。三级都保证在纯黑背景上对比度达标 */
private fun CheckLevel.color(): Color = when (this) {
    CheckLevel.OK -> Color(0xFF00AA00)
    CheckLevel.WARN -> Color(0xFFFFB000)
    CheckLevel.FAIL -> Color(0xFFFF0000)
}

/**
 * 启动自检序列
 *
 * 逐行显示真实的系统自检结果。这一屏同时充当**首屏诊断**：蓝牙没开、权限缺失、
 * 没有网络，在这里就能看见。
 *
 * 只有存在 FAIL 项时，末尾结论才标红；全项通过或仅有警告都算可用状态，
 * 不会拦着用户进主界面。
 *
 * @param onComplete 自检展示完毕后的回调，通常用于切换到主屏幕
 * @param modifier Modifier 修饰符
 */
@Composable
fun BootSequence(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 只在进入时采一次：这一屏展示的是"启动那一刻"的状态，展示过程中不该变化
    val checks = remember { collectStartupChecks(context) }

    var shown by remember { mutableIntStateOf(0) }
    var blink by remember { mutableStateOf(true) }

    val failed = checks.count { it.level == CheckLevel.FAIL }
    val warned = checks.count { it.level == CheckLevel.WARN }

    LaunchedEffect(checks) {
        checks.indices.forEach { index ->
            delay(CHECK_REVEAL_INTERVAL_MS)
            shown = index + 1
        }
        delay(DONE_HOLD_MS)
        onComplete()
    }
    LaunchedEffect(Unit) { while (true) { delay(500); blink = !blink } }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DEAUTHCTRL", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Color(0xFFFF0000))
            Text("v1.0  |  哪吒网络安全", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF666666))
            Spacer(Modifier.height(22.dp))
            AsciiDivider()
            Spacer(Modifier.height(14.dp))

            checks.forEachIndexed { index, check ->
                AnimatedVisibility(visible = index < shown, enter = fadeIn(tween(200)) + slideInVertically(tween(250))) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("  > ${check.label}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFFF0000), modifier = Modifier.weight(1f))
                        Text("${check.detail}  ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF888888))
                        Text(check.level.tag(), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = check.level.color())
                    }
                }
            }

            if (shown >= checks.size) {
                Spacer(Modifier.height(16.dp))
                AsciiDivider()
                Spacer(Modifier.height(10.dp))
                val summary = when {
                    failed > 0 -> "> 就绪 — $failed 项异常，$warned 项警告"
                    warned > 0 -> "> 就绪 — $warned 项警告"
                    else -> "> 全项通过"
                }
                Text(
                    if (blink) "$summary _" else summary,
                    fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (failed > 0) Color(0xFFFF0000) else Color(0xFF00AA00),
                )
            }
        }
        ScanlineOverlay(lineSpacing = 3.dp, alpha = 0.05f, color = Color(0xFFFF0000))
    }
}
