package xin.ctkqiang.deauthctrl.ui.components

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import xin.ctkqiang.deauthctrl.ui.screens.PreviewTheme

/**
 * CRT 扫描线叠加层
 *
 * 在屏幕上方绘制均匀分布的水平线条，模拟 CRT 显示器的扫描线效果。
 * 使用 drawWithCache 缓存整张扫描线的 Path：尺寸不变时缓存命中，
 * 每帧只在绘制层 drawPath 一次，避免逐帧构造上百条线段带来的开销。
 *
 * 线条颜色由 color 参数指定，通过 alpha 控制透明度。
 * 在深色背景上使用 0.02-0.04 的 alpha 值效果最佳，
 * 过低（< 0.02）肉眼不可见，过高（> 0.10）影响文字可读性。
 *
 * @param lineSpacing 扫描线间距（dp），越小越密集。默认 4dp
 * @param alpha 线条透明度（0.0-1.0），默认 0.03
 * @param color 线条颜色，默认全息青
 */
@Composable
fun ScanlineOverlay(
    lineSpacing: Dp = 4.dp,
    alpha: Float = 0.03f,
    color: Color = Red,
) {
    val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { lineSpacing.toPx() }
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val path = Path()
            var y = 0f
            while (y < size.height) {
                path.moveTo(0f, y)
                path.lineTo(size.width, y)
                y += spacingPx
            }
            onDrawBehind {
                drawPath(path, color.copy(alpha = alpha), style = Stroke(1f))
            }
        },
    )
}

/**
 * 故障效果文字（Glitch Text）
 *
 * 模拟数字故障/毛刺（glitch）效果的文字组件。
 * 每间隔 glitchIntervalMs 毫秒触发一次故障动画：
 * 1. 青色副本向右偏移（通道错位）
 * 2. 白色副本向左偏移（信号过载）
 * 3. 品红副本向左偏移（色偏修复延迟）
 * 4. 恢复原始位置
 *
 * 青 + 品红双通道偏移：全息投影的故障美学。
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
    var showGold by remember { mutableStateOf(false) }
    var showBright by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(glitchIntervalMs)
            offsetX = Random.nextInt(-6, 7).toFloat()
            showRed = true; delay(50)
            showRed = false; showBright = true; delay(40)
            showBright = false; showGold = true; delay(50)
            showGold = false; offsetX = 0f
        }
    }

    Box(modifier = modifier) {
        if (showRed) Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = style, color = Red.copy(alpha = 0.7f), modifier = Modifier.offset(x = offsetX.dp))
        if (showBright) Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = style, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.offset(x = -offsetX.dp / 2))
        if (showGold) Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = style, color = Color(0xFFFF4FD8).copy(alpha = 0.4f), modifier = Modifier.offset(x = -offsetX.dp))
        Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = style, color = Red)
    }
}

/**
 * 脉冲呼吸指示灯
 *
 * 一个小圆点，以正弦波周期性地改变透明度，模拟"呼吸"或"闪烁"效果。
 * 已由 [PulseStar] 取代，保留供兼容。
 */
@Composable
fun PulseDot(color: Color = Red, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { delay(60); phase += 0.06f } }
    val a = (sin(phase * PI).toFloat() * 0.45f + 0.5f)
    Box(modifier = modifier.size(size).clip(CircleShape).background(color.copy(alpha = a.coerceIn(0.2f, 1f))))
}

/**
 * ASCII 分隔线
 *
 * 使用全角破折号字符（'─'）重复 48 次构成一条水平装饰分隔线。
 *
 * @param modifier Modifier 修饰符，用于控制外边距和宽度
 */
@Composable
fun AsciiDivider(modifier: Modifier = Modifier) {
    Text("─".repeat(48), fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Red.copy(alpha = 0.35f), modifier = modifier)
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
 * 每一项都来自系统 API 实测。这一屏同时充当**首屏诊断**：
 * 蓝牙没开、权限缺失、没有网络，在这里就能看见。
 *
 * 读不到的项直接不显示，而不是补一个占位值。
 */
private fun collectStartupChecks(context: Context): List<StartupCheck> = buildList {
    // 1. 运行时权限 —— 这一项失败会让所有扫描类功能一起失效
    val missing = RequiredPermissions.missing(context)
    add(
        if (missing.isEmpty()) StartupCheck("作战授权", "全部已授予", CheckLevel.OK)
        else StartupCheck("作战授权", "缺 ${missing.size} 项，相关功能不可用", CheckLevel.FAIL)
    )

    // 2. 蓝牙适配器
    when (val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter) {
        null -> add(StartupCheck("短波通讯", "此设备没有蓝牙硬件", CheckLevel.FAIL))
        else -> add(
            if (adapter.isEnabled) StartupCheck("短波通讯", "已就绪", CheckLevel.OK)
            else StartupCheck("短波通讯", "已关闭，BLE 功能需先开启", CheckLevel.WARN)
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
        if (scanGranted) StartupCheck("电子侦察授权", "已授予", CheckLevel.OK)
        else StartupCheck("电子侦察授权", "未授予，BLE 嗅探不可用", CheckLevel.FAIL)
    )

    // 4. 活跃网络接口 —— 局域网类功能（Web 服务、文件快传、对讲机）都依赖它
    val iface = activeInterfaceName(context.getSystemService(ConnectivityManager::class.java))
    add(
        if (iface.isNullOrEmpty()) StartupCheck("战地链路", "无活动网络，局域网功能受限", CheckLevel.WARN)
        else StartupCheck("战地链路", "$iface 在线", CheckLevel.OK)
    )

    // 5. 可用内存
    val memory = ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
    if (memory.totalMem > 0L) {
        val detail = "${formatGigabytes(memory.availMem)} / ${formatGigabytes(memory.totalMem)}"
        val low = memory.availMem.toDouble() / memory.totalMem < LOW_MEMORY_RATIO
        add(
            if (low) StartupCheck("战术内存", "$detail（偏低）", CheckLevel.WARN)
            else StartupCheck("战术内存", detail, CheckLevel.OK)
        )
    }

    // 6. 可用存储
    val free = context.filesDir.usableSpace
    if (free > 0L) {
        add(
            if (free < LOW_STORAGE_BYTES) StartupCheck("储备空间", "${formatGigabytes(free)}（偏低）", CheckLevel.WARN)
            else StartupCheck("储备空间", "${formatGigabytes(free)} 可用", CheckLevel.OK)
        )
    }
}

/** 等级 → 右侧标记，用有区分度的字，不用统一的 "OK" 掩盖差异 */
private fun CheckLevel.tag(): String = when (this) {
    CheckLevel.OK -> "[ OK ]"
    CheckLevel.WARN -> "[WARN]"
    CheckLevel.FAIL -> "[FAIL]"
}

/** 等级 → 配色。OK 用通行绿，WARN 用琥珀，FAIL 用危险红 */
private fun CheckLevel.color(): Color = when (this) {
    CheckLevel.OK -> TacGreen
    CheckLevel.WARN -> Warn
    CheckLevel.FAIL -> Danger
}

/**
 * 启动自检序列 —— 三阶段战术点火动画
 *
 * 阶段 1「核心点火」：菱形核心缩放放大 + 双层辉光扩散环（像增强管预热）
 * 阶段 2「战术识别」：DEAUTHCTRL 等宽大标题淡入 + 四角锁定框合拢
 * 阶段 3「系统自检」：真实自检项逐行浮现，顶部进度条同步推进，
 *   底部轮播军语 + 版本号；FAIL 项时结论用危险色
 *
 * 所有动画由 Animatable 驱动（帧精确、不依赖无限循环），
 * 进度条与自检行共用同一个时间轴，节奏一致。
 *
 * @param onComplete 自检展示完毕后的回调，通常用于切换到主屏幕
 */
@Composable
fun BootSequence(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 只在进入时采一次：这一屏展示的是"启动那一刻"的状态，展示过程中不该变化。
    // 静态预览（Layoutlib）读不到系统服务，采集失败时退化为占位自检项，保证预览可渲染。
    val checks = remember {
        runCatching { collectStartupChecks(context) }
            .getOrElse {
                listOf(
                    StartupCheck("作战授权", "预览模式", CheckLevel.OK),
                    StartupCheck("战地链路", "预览模式", CheckLevel.OK),
                    StartupCheck("战术内存", "预览模式", CheckLevel.OK),
                )
            }
    }

    // ── 动画状态 ──
    val coreScale = remember { Animatable(0.25f) }
    val coreGlow = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val lockAlpha = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }

    var shown by remember { mutableIntStateOf(0) }
    var phase by remember { mutableIntStateOf(0) }
    var blink by remember { mutableStateOf(true) }
    val motto = remember { mutableStateOf(BOOT_MOTTOS.first()) }

    val failed = checks.count { it.level == CheckLevel.FAIL }
    val warned = checks.count { it.level == CheckLevel.WARN }

    LaunchedEffect(checks) {
        // ── 阶段 1：核心点火 ──
        coreScale.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        coreGlow.animateTo(1f, tween(900))
        delay(150)
        phase = 1
        // ── 阶段 2：战术识别 ──
        titleAlpha.animateTo(1f, tween(450))
        lockAlpha.animateTo(1f, tween(600))
        delay(450)
        phase = 2
        // ── 阶段 3：系统自检（进度条与行浮现共用时间轴） ──
        checks.indices.forEach { index ->
            launch { progress.animateTo((index + 1f) / checks.size, tween(CHECK_REVEAL_INTERVAL_MS.toInt(), easing = LinearEasing)) }
            delay(CHECK_REVEAL_INTERVAL_MS)
            shown = index + 1
        }
        delay(DONE_HOLD_MS)
        onComplete()
    }
    LaunchedEffect(Unit) { while (true) { delay(500); blink = !blink } }
    // 自检期间军语轮播
    LaunchedEffect(phase) {
        var i = 0
        while (phase == 2) {
            motto.value = BOOT_MOTTOS[i % BOOT_MOTTOS.size]
            i++
            delay(1100)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Dark)) {
        // 全息数据流雨背景
        DataRain(Modifier.fillMaxSize(), color = TacGreen)

        Column(Modifier.align(Alignment.Center).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // ── 战术核心：菱形 + 双层辉光扩散环 ──
            Box(Modifier.size(96.dp)) {
                Canvas(Modifier.fillMaxSize().graphicsLayer {
                    scaleX = coreScale.value
                    scaleY = coreScale.value
                    alpha = coreScale.value
                }) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    // 双层向外扩散的辉光环（点火脉冲）
                    for (ring in 0..1) {
                        val ph = ((coreGlow.value * 1.6f + ring * 0.5f) % 1f)
                        drawCircle(
                            Red.copy(alpha = (1f - ph) * 0.5f),
                            r * (0.30f + ph * 0.70f),
                            c,
                            style = Stroke(1.6.dp.toPx()),
                        )
                    }
                    // 玻璃底光
                    drawCircle(Red.copy(alpha = 0.10f), r * 0.86f, c)
                    drawCircle(Red.copy(alpha = 0.45f), r * 0.92f, c, style = Stroke(1.dp.toPx()))
                    // 中心菱形
                    val d = Path().apply {
                        moveTo(c.x, c.y - r * 0.52f)
                        lineTo(c.x + r * 0.52f, c.y)
                        lineTo(c.x, c.y + r * 0.52f)
                        lineTo(c.x - r * 0.52f, c.y)
                        close()
                    }
                    drawPath(d, Gold)
                    // 菱形外细框
                    drawPath(d, Red.copy(alpha = 0.55f), style = Stroke(1.2.dp.toPx()))
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 阶段 2：战术识别标题 ──
            AnimatedVisibility(
                visible = phase >= 1,
                enter = fadeIn(tween(300)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "DEAUTHCTRL",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 27.sp,
                        letterSpacing = 6.sp,
                        color = White,
                        modifier = Modifier.graphicsLayer { alpha = titleAlpha.value },
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "哪吒网络安全终端 · 全息战术系统",
                        fontFamily = FontFamily.Default,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Red,
                        modifier = Modifier.graphicsLayer { alpha = titleAlpha.value },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "TACTICAL BOOT SEQUENCE // AUTH: 授权安全研究",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = Gray,
                        modifier = Modifier.graphicsLayer { alpha = titleAlpha.value },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 阶段 3：自检清单 ──
            if (phase >= 2) {
                AsciiDivider()
                Spacer(Modifier.height(10.dp))
                checks.forEachIndexed { index, check ->
                    AnimatedVisibility(visible = index < shown, enter = fadeIn(tween(180)) + slideInVertically(tween(240))) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("  > ${check.label}", fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red, modifier = Modifier.weight(1f))
                            Text("${check.detail}  ", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Gray)
                            Text(check.level.tag(), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = check.level.color())
                        }
                    }
                }

                if (shown >= checks.size) {
                    Spacer(Modifier.height(12.dp))
                    AsciiDivider()
                    Spacer(Modifier.height(8.dp))
                    val summary = when {
                        failed > 0 -> "> 部署警告 — $failed 项异常，$warned 项警告"
                        warned > 0 -> "> 部署就绪 — $warned 项警告"
                        else -> "> 系统就绪 — 全息终端待命"
                    }
                    Text(
                        if (blink) "$summary _" else summary,
                        fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (failed > 0) Danger else TacGreen,
                    )
                } else {
                    // 自检进行中的军语
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "⟫ ${motto.value}",
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Gold,
                    )
                }
            }
        }

        // ── 顶部启动进度条 ──
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp).background(SurfaceAlt),
        ) {
            Box(
                Modifier.fillMaxWidth(progress.value).height(2.dp).background(Red),
            )
        }

        // ── 四角锁定框（随阶段 2 合拢显现） ──
        TacticalCorners(
            color = Red,
            modifier = Modifier.fillMaxSize().padding(20.dp).graphicsLayer { alpha = lockAlpha.value * 0.85f },
            len = 14.dp,
            stroke = 1.4.dp,
        )

        // ── 底部版本行 ──
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("DEAUTHCTRL v1.0", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Gray)
            Text("  //  ", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = GrayDark)
            Text("中国红客 · 国产自主 · 安全可控", fontFamily = FontFamily.Default, fontSize = 9.sp, color = Gray)
        }

        ScanlineOverlay(lineSpacing = 4.dp, alpha = 0.03f, color = Red)
    }
}

/** 自检期间的轮播军语 */
private val BOOT_MOTTOS = listOf(
    "链路加密已建立 · AES-256",
    "战术终端自检中",
    "信号侦测阵列上线",
    "全息投影校准",
    "战术网络待命",
    "陀螺仪航向锁定",
)

// ─── Android Studio 预览 ──────────────────────────────────────────────────────

/** 启动自检预览 */
@Preview(name = "启动自检", showBackground = true, backgroundColor = 0xFF060B14, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun BootSequencePreview() {
    PreviewTheme { BootSequence(onComplete = {}) }
}

/** 故障文字预览 */
@Preview(name = "故障文字", showBackground = true, backgroundColor = 0xFF060B14)
@Composable
private fun GlitchTextPreview() {
    PreviewTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlitchText("DEAUTHCTRL", style = TextStyle(fontSize = 30.sp))
        }
    }
}

/** 扫描线预览 */
@Preview(name = "扫描线叠加层", showBackground = true, backgroundColor = 0xFF060B14)
@Composable
private fun ScanlinePreview() {
    PreviewTheme {
        Box(Modifier.fillMaxSize()) {
            Text("哪吒网络安全终端", fontFamily = FontFamily.Default, fontSize = 18.sp, fontWeight = FontWeight.Black, color = White, modifier = Modifier.align(Alignment.Center))
            ScanlineOverlay(lineSpacing = 4.dp, alpha = 0.04f, color = Red)
        }
    }
}


/**
 * 数据流雨 —— 全息战术风格的加密代码流
 *
 * 启动屏背景：22 列下落的字符流，头部亮点 + 渐隐尾迹。
 * 用线条代替字符绘制，性能友好；相位驱动全部在绘制层。
 */
@Composable
fun DataRain(modifier: Modifier = Modifier, color: Color = TacGreen) {
    val t = rememberInfiniteTransition()
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart))
    Canvas(modifier) {
        val cols = 22
        val colW = size.width / cols
        val tailLen = size.height * 0.20f
        val seg = 9.dp.toPx()
        for (i in 0 until cols) {
            val seed = (i * 0.618034f) % 1f
            val head = ((phase + seed) % 1f) * (size.height + tailLen)
            val x = i * colW + colW / 2f
            drawCircle(color.copy(alpha = 0.65f), 1.4f, Offset(x, head))
            var y = head
            var k = 0
            while (y > 0f && k < 14) {
                drawLine(color.copy(alpha = (1f - k / 14f) * 0.20f), Offset(x, y), Offset(x, y - seg), 1f)
                y -= seg
                k++
            }
        }
    }
}
