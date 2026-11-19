package xin.ctkqiang.deauthctrl.terminal

import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * 粘滞修饰键状态（CTRL / ALT）。
 *
 * 与 Termux ExtraKeysView 的 SpecialButton 语义一致：
 * - 点按：锁存（下一次按键生效后自动复位）；
 * - 长按：锁定（保持到再次点按该键为止）。
 *
 * 用 Compose state 承载，Extra keys 按钮的激活态高亮直接订阅这里；
 * [TerminalClientBridge.readControlKey] / [readAltKey] 在读取时执行
 * “未锁定则复位”的语义，与 Termux `readSpecialButton(button, true)` 相同。
 */
class StickyModifiers {
    var ctrlDown by mutableStateOf(false)
    var ctrlLocked by mutableStateOf(false)
    var altDown by mutableStateOf(false)
    var altLocked by mutableStateOf(false)

    /** 点按修饰键：未锁定时切换，关闭时同时解除锁定。 */
    fun toggleCtrl() {
        ctrlDown = !ctrlDown
        if (!ctrlDown) ctrlLocked = false
    }

    fun toggleAlt() {
        altDown = !altDown
        if (!altDown) altLocked = false
    }

    /** 长按修饰键：锁定。 */
    fun lockCtrl() {
        ctrlDown = true
        ctrlLocked = true
    }

    fun lockAlt() {
        altDown = true
        altLocked = true
    }

    fun readCtrl(): Boolean {
        val down = ctrlDown
        if (down && !ctrlLocked) ctrlDown = false
        return down
    }

    fun readAlt(): Boolean {
        val down = altDown
        if (down && !altLocked) altDown = false
        return down
    }
}

/**
 * 终端会话与视图的桥接实现。
 *
 * 同时实现 Termux 的两个客户端接口（本项目中只有一个终端视图与一个会话，
 * 因此合并为一个类；接口的职责划分与 Termux 完全一致）：
 *
 * - [TerminalSessionClient]：会话状态回调（标题 / 结束 / 铃声 / 剪贴板 / 日志）。
 * - [TerminalViewClient]：视图配置与输入拦截（粘滞修饰键读取、缩放、单点切换
 *   软键盘、复制模式通知）。
 *
 * 回调全部发生在主线程（Termux `TerminalSession` 的约定），这里的
 * Compose state 写入与视图调用都是主线程操作。
 */
class TerminalClientBridge(
    private val context: Context,
    private val onTitleChangedListener: (String?) -> Unit,
    private val onSessionFinishedListener: (TerminalSession) -> Unit,
    private val onBellListener: () -> Unit,
    private val onEmulatorReadyListener: () -> Unit,
    private val onCopyModeChangedListener: (Boolean) -> Unit,
) : TerminalSessionClient, TerminalViewClient {

    /** 粘滞修饰键；Extra keys 行与两个 read*Key 回调共享。 */
    val modifiers = StickyModifiers()

    /** 当前绑定的终端视图（旋转 / 重建时更新）。 */
    @Volatile
    var view: TerminalView? = null

    // ── TerminalSessionClient ────────────────────────────────────────────────

    override fun onTextChanged(changedSession: TerminalSession) {
        // 屏幕缓冲已更新，触发视图重绘。视图不存在（旋转瞬间）时安全忽略。
        view?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        onTitleChangedListener(changedSession.title)
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        onSessionFinishedListener(finishedSession)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(L10n.get(R.string.term_bridge_clip_label), text))
        }
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
            if (!text.isNullOrEmpty()) session.write(text)
        }
    }

    override fun onBell(session: TerminalSession) {
        onBellListener()
    }

    override fun onColorsChanged(session: TerminalSession) {
        // 应用不使用运行时改色（OSC 4），保留默认方案即可。
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        // 光标闪烁由 TerminalView 内部管理，无需在此响应。
    }

    override fun getTerminalCursorStyle(): Int =
        TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK

    override fun logError(tag: String, message: String) {
        Log.e(TAG, "$tag: $message")
    }

    override fun logWarn(tag: String, message: String) {
        Log.w(TAG, "$tag: $message")
    }

    override fun logInfo(tag: String, message: String) {
        Log.i(TAG, "$tag: $message")
    }

    override fun logDebug(tag: String, message: String) {
        Log.d(TAG, "$tag: $message")
    }

    override fun logVerbose(tag: String, message: String) {
        Log.v(TAG, "$tag: $message")
    }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(TAG, "$tag: $message", e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        Log.e(TAG, tag, e)
    }

    // ── TerminalViewClient ───────────────────────────────────────────────────

    override fun onScale(scale: Float): Float = scale

    /** 单击回调；由界面侧安装（Termux 语义：切换软键盘显隐）。 */
    var onSingleTap: () -> Unit = {}

    override fun onSingleTapUp(e: MotionEvent) {
        onSingleTap()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    /**
     * 强制字符输入模式(返回 true):
     * inputType 从 TYPE_NULL 切换为 TYPE_TEXT_VARIATION_VISIBLE_PASSWORD |
     * NO_SUGGESTIONS,让输入法只走标准 commitText 通道。
     *
     * 双字符根因:部分输入法(三星/小米/百度等)在 TYPE_NULL 下会同时
     * 发送按键事件与 commitText,两条通道各写一次 → "ls" 变 "llss"。
     * 此开关是 Termux 官方针对该问题的修复方案(见其 issue #686 注释)。
     */
    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {
        // 复制模式状态由管理器转成 StateFlow，界面据此显示提示。
        onCopyModeChangedListener(copyMode)
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = modifiers.readCtrl()

    override fun readAltKey(): Boolean = modifiers.readAlt()

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean =
        false

    override fun onEmulatorSet() {
        // 子进程此刻已通过 JNI createSubprocess 启动，管理器可以把状态切到 Running。
        onEmulatorReadyListener()
    }

    private companion object {
        const val TAG = "TerminalBridge"
    }
}
