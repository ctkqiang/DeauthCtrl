package xin.ctkqiang.deauthctrl.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.termux.view.TerminalView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.manager.TerminalManager
import xin.ctkqiang.deauthctrl.terminal.ShellMode
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.GrayDark
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.viewmodel.TerminalViewModel

/** 终端输出区底色 —— 与 TerminalSpec.applyHudColorScheme 的终端背景一致。 */
private val TerminalOutputBg = Color(0xFF04121C)

/** 会话在线指示灯的绿色。 */
private val SessionOnlineGreen = Color(0xFF4DFF88)

/**
 * 本地终端页面 —— Termux 引擎（v0.118.3，Apache 2.0）。
 *
 * ## 结构（自上而下）
 * 1. [TerminalHeader]：返回与标题
 * 2. 会话工具条：在线状态 + 标题 + 字号调节 + 清屏 / 粘贴 / ROOT 切换 / 挂断
 * 3. Termux `TerminalView`（AndroidView）：终端渲染、IME、文本选择、
 *    复制粘贴、手势缩放全部由原版视图处理
 * 4. Extra keys 两行：Termux v0.118.3 默认布局 + 其可配置键集中的 ⌫ / ⏎
 * 5. 页脚操作提示
 *
 * ## 生命周期
 * - 会话对象归 ViewModel（配置变更保留）；视图重建时重新 attach，
 *   已 spawn 的会话直接接管（Termux 的 attachSession 语义）；
 * - 离开页面只 detach 视图，不杀会话（与 Termux 行为一致：进程归会话管理，
 *   由“挂断”按钮或 ViewModel 释放）；
 * - ON_START / ON_STOP 控制光标闪烁器（Termux 的省电语义）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(vm: TerminalViewModel, back: () -> Unit) {
    val manager = vm.manager
    val bridge = manager.bridge
    val status by vm.status.collectAsState()
    val title by vm.title.collectAsState()
    val copyMode by vm.copyMode.collectAsState()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val inPreview = LocalInspectionMode.current

    /** 视图引用：AndroidView 重建时更新，Extra keys 直接调用其方法。 */
    val viewRef = remember { ViewRef() }
    var textSize by remember { mutableIntStateOf(16) }

    /** 最近一次已下发给视图的字号（见 AndroidView 的 update 注释）。 */
    var appliedTextSize by remember { mutableIntStateOf(Int.MIN_VALUE) }

    var rootMode by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible

    /**
     * Termux 语义：单击终端切换软键盘显隐。
     *
     * 显隐状态取自 Compose 的 IME insets（`WindowInsets.isImeVisible`），
     * 避免 `InputMethodManager.isAcceptingText` 在 Kotlin 里的属性解析冲突。
     */
    fun toggleIme() {
        val view = viewRef.view ?: return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imeVisible) {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        } else {
            view.requestFocus()
            imm.showSoftInput(view, 0)
        }
    }

    // 单击回调由桥接转发到这里；重组时刷新捕获的 imeVisible 快照。
    bridge.onSingleTap = ::toggleIme

    // 首次进入自动连接（静态预览不创建真实会话）。
    // 工厂可能在会话创建前执行（先 attach 到 null），因此启动后必须再 attach 一次；
    // 若工厂已经 attach 同一会话，attachSession 对相同实例是无操作。
    LaunchedEffect(Unit) {
        if (!inPreview && vm.status.value is TerminalManager.Status.Idle) {
            vm.start(ShellMode.LOCAL)
            viewRef.view?.attachSession(manager.currentSession())
        }
    }

    // 终端响铃（BEL）→ 触觉反馈。
    LaunchedEffect(Unit) {
        vm.bell.collect { haptic.hud() }
    }

    // 前台 / 后台切换时启停光标闪烁器（Termux TerminalSessionFragment 语义）。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewRef.view?.setTerminalCursorBlinkerRate(500)
                    viewRef.view?.setTerminalCursorBlinkerState(true, true)
                }

                Lifecycle.Event.ON_STOP ->
                    viewRef.view?.setTerminalCursorBlinkerState(false, true)

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 离开页面：detach 视图。会话仍归 ViewModel，重进页面会重新 attach。
    DisposableEffect(Unit) {
        onDispose {
            viewRef.view?.setTerminalCursorBlinkerState(false, true)
            viewRef.view?.attachSession(null)
            bridge.view = null
            viewRef.view = null
        }
    }

    /** 启动（或重启）会话并把视图接上。 */
    fun connect(asRoot: Boolean) {
        vm.start(if (asRoot) ShellMode.ROOT else ShellMode.LOCAL)
        viewRef.view?.attachSession(manager.currentSession())
    }

    /** 挂断：SIGKILL；未 spawn 的会话直接废弃并 detach，保证不会随后启动。 */
    fun disconnect() {
        val current = vm.status.value
        vm.stop()
        if (current is TerminalManager.Status.Starting) {
            viewRef.view?.attachSession(null)
        }
    }

    /** ROOT / LOCAL 切换：重启会话到新模式。 */
    fun toggleRootMode() {
        rootMode = !rootMode
        connect(rootMode)
    }

    /** 命名键发送（Termux TerminalExtraKeys 语义）。 */
    fun sendNamedKey(keyCode: Int) {
        val view = viewRef.view ?: return
        haptic.hud()
        val event = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, 0)
        view.onKeyDown(keyCode, event)
    }

    /** 字符键发送；粘滞 CTRL / ALT 在 TerminalView 内部经 bridge 读取。 */
    fun sendCharKey(codePoint: Int) {
        val view = viewRef.view ?: return
        haptic.hud()
        view.inputCodePoint(codePoint, false, false)
    }

    fun pasteFromClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        if (!text.isNullOrEmpty()) vm.send(text)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Dark)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        TerminalHeader("本地终端", back)

        // ── 会话工具条 ─────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceBg)
                .border(0.5.dp, BorderDim)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (ledColor, statusText) = when (val s = status) {
                is TerminalManager.Status.Idle -> GrayDark to "未连接"
                is TerminalManager.Status.Starting -> SessionOnlineGreen to "启动中…"
                is TerminalManager.Status.Running ->
                    SessionOnlineGreen to (title?.let { "会话在线 · $it" } ?: "会话在线 · PID ${s.pid}")

                is TerminalManager.Status.Finished -> GrayDark to "已退出 · code ${s.exitCode}"
                is TerminalManager.Status.Failed -> Danger to "启动失败"
            }

            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(ledColor),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                statusText,
                fontFamily = Mono,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = when (status) {
                    is TerminalManager.Status.Running, is TerminalManager.Status.Starting -> TacGreen
                    is TerminalManager.Status.Failed -> Danger
                    else -> Gray
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            GhostButton("−") { textSize = (textSize - 1).coerceAtLeast(8) }
            Spacer(Modifier.width(4.dp))
            GhostButton("＋") { textSize = (textSize + 1).coerceAtMost(40) }
            Spacer(Modifier.width(4.dp))
            GhostButton("粘贴") { pasteFromClipboard() }
            Spacer(Modifier.width(4.dp))
            GhostButton("清屏") { vm.clear() }
            Spacer(Modifier.width(4.dp))
            if (vm.rootAvailable) {
                GhostButton(if (rootMode) "ROOT" else "LOCAL") { toggleRootMode() }
                Spacer(Modifier.width(4.dp))
            }
            GhostButton(
                when (status) {
                    is TerminalManager.Status.Running, is TerminalManager.Status.Starting -> "挂断"
                    else -> "连接"
                },
            ) {
                when (status) {
                    is TerminalManager.Status.Running, is TerminalManager.Status.Starting -> disconnect()
                    else -> connect(rootMode)
                }
            }
        }

        // 启动失败提示条。
        if (status is TerminalManager.Status.Failed) {
            Text(
                "! ${(status as TerminalManager.Status.Failed).message}",
                fontFamily = Mono,
                fontSize = 9.sp,
                color = Danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Danger.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // ── Termux 终端视图 ─────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    setTerminalViewClient(bridge)
                    bridge.view = this
                    setTextSize(textSize)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    viewRef.view = this
                    // 会话由 ViewModel 持有：旋转重建视图时直接接管。
                    attachSession(manager.currentSession())
                }
            },
            update = { view ->
                // TerminalView.setTextSize(int) 与基类 View 的 Float 属性合成
                // 冲突，Kotlin 下无法读回当前字号；用“已应用字号”自己跟踪，
                // 只在变化时下发（setTextSize 内部会重建 renderer 并 updateSize）。
                if (appliedTextSize != textSize) {
                    view.setTextSize(textSize)
                    appliedTextSize = textSize
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(TerminalOutputBg),
        )

        // ── Extra keys：Termux v0.118.3 默认布局 + ⌫ / ⏎ ─────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .background(SurfaceBg)
                .border(0.5.dp, BorderDim)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ExtraKey("ESC", Modifier.weight(1f)) { sendNamedKey(KeyEvent.KEYCODE_ESCAPE) }
                ExtraKey("/", Modifier.weight(1f)) { sendCharKey('/'.code) }
                ExtraKey(
                    "-",
                    Modifier.weight(1f),
                    onTap = { sendCharKey('-'.code) },
                    onLongPress = { sendCharKey('|'.code) },
                )
                ExtraKey("HOME", Modifier.weight(1f)) { sendNamedKey(KeyEvent.KEYCODE_MOVE_HOME) }
                ExtraKey("↑", Modifier.weight(1f), repeatable = true) {
                    sendNamedKey(KeyEvent.KEYCODE_DPAD_UP)
                }
                ExtraKey("END", Modifier.weight(1f)) { sendNamedKey(KeyEvent.KEYCODE_MOVE_END) }
                ExtraKey("PGUP", Modifier.weight(1f), repeatable = true) {
                    sendNamedKey(KeyEvent.KEYCODE_PAGE_UP)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ExtraKey("TAB", Modifier.weight(1f)) { sendNamedKey(KeyEvent.KEYCODE_TAB) }
                ExtraKey(
                    "CTRL",
                    Modifier.weight(1f),
                    active = bridge.modifiers.ctrlDown,
                    onTap = {
                        haptic.hud()
                        bridge.modifiers.toggleCtrl()
                    },
                    onLongPress = {
                        haptic.hud()
                        bridge.modifiers.lockCtrl()
                    },
                )
                ExtraKey(
                    "ALT",
                    Modifier.weight(1f),
                    active = bridge.modifiers.altDown,
                    onTap = {
                        haptic.hud()
                        bridge.modifiers.toggleAlt()
                    },
                    onLongPress = {
                        haptic.hud()
                        bridge.modifiers.lockAlt()
                    },
                )
                ExtraKey("←", Modifier.weight(1f), repeatable = true) {
                    sendNamedKey(KeyEvent.KEYCODE_DPAD_LEFT)
                }
                ExtraKey("↓", Modifier.weight(1f), repeatable = true) {
                    sendNamedKey(KeyEvent.KEYCODE_DPAD_DOWN)
                }
                ExtraKey("→", Modifier.weight(1f), repeatable = true) {
                    sendNamedKey(KeyEvent.KEYCODE_DPAD_RIGHT)
                }
                ExtraKey("PGDN", Modifier.weight(1f), repeatable = true) {
                    sendNamedKey(KeyEvent.KEYCODE_PAGE_DOWN)
                }
                ExtraKey("⌫", Modifier.weight(1f), repeatable = true) {
                    sendNamedKey(KeyEvent.KEYCODE_DEL)
                }
                ExtraKey("⏎", Modifier.weight(1.3f)) { sendNamedKey(KeyEvent.KEYCODE_ENTER) }
            }
        }

        // ── 页脚提示 ───────────────────────────────────────────────────────
        Text(
            buildString {
                append("点击屏幕切换软键盘 · 长按拖动选区复制 · 双指缩放字号 · ")
                append("CTRL/ALT 粘滞（长按锁定） · 长按方向键连发 · 长按 - 输入 |")
                if (copyMode) append(" · 已进入选择模式")
            },
            fontFamily = Mono,
            fontSize = 9.sp,
            color = if (copyMode) Gold else Gray,
            lineHeight = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** AndroidView 重建时的视图引用容器（非 Compose state，避免布局期写入）。 */
private class ViewRef {
    var view: TerminalView? = null
}

/**
 * Extra keys 键位。
 *
 * 默认是暗色小斜切块；CTRL / ALT 激活时用危险红底 + 白字。支持两种交互：
 * - [repeatable]：按下即发一次，按住 400ms（Termux longPressTimeout）后以
 *   80ms（Termux longPressRepeatDelay）间隔连发，直到抬起；
 * - 普通键：点按触发 [onTap]，可选长按触发 [onLongPress]。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExtraKey(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    repeatable: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onTap: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()

    val pointerModifier = if (repeatable) {
        Modifier.pointerInput(label) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                onTap()
                // 连发协程挂在组合 scope 上（awaitEachGesture 的接收者是受限
                // 挂起作用域，不能直接 launch）；抬起后在 finally 里取消。
                val repeatJob = scope.launch {
                    delay(400L)
                    while (isActive) {
                        onTap()
                        delay(80L)
                    }
                }
                try {
                    waitForUpOrCancellation()
                } catch (e: CancellationException) {
                    // 手势被取消（离开按键区域）：结束连发。
                } finally {
                    repeatJob.cancel()
                }
            }
        }
    } else {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = {
                haptic.hud()
                onTap()
            },
            onLongClick = if (onLongPress != null) {
                {
                    haptic.hud()
                    onLongPress()
                }
            } else {
                null
            },
        )
    }

    Surface(
        modifier = modifier
            .height(30.dp)
            .then(pointerModifier),
        shape = CutCornerShape(4.dp),
        color = if (active) Danger.copy(alpha = 0.88f) else SurfaceAlt,
        border = BorderStroke(1.dp, if (active) Danger else BorderDim),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontFamily = Mono,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Color.White else Gold,
                maxLines = 1,
            )
        }
    }
}

// ─── Android Studio 预览 ──────────────────────────────────────────────────────

@Preview(
    name = "本地终端",
    showBackground = true,
    backgroundColor = 0xFF060B14,
    widthDp = 393,
    heightDp = 852,
    locale = "zh",
)
@Composable
private fun TerminalScreenPreview() {
    PreviewTheme {
        // 静态预览里 TerminalView（Android View 渲染）与 JNI 会话都无法工作，
        // 直接降级为提示；真机预览（Preview on device）走完整实现。
        if (LocalInspectionMode.current) {
            PreviewFallback("本地终端")
        } else {
            val app = LocalContext.current.applicationContext
            val vm = if (app is android.app.Application) {
                previewViewModel { TerminalViewModel(app) }
            } else {
                null
            }
            if (vm == null) PreviewFallback("本地终端") else TerminalScreen(vm, back = {})
        }
    }
}
