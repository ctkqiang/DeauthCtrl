package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.terminal.ShellMode
import xin.ctkqiang.deauthctrl.terminal.TerminalClientBridge
import xin.ctkqiang.deauthctrl.terminal.TerminalSpec
import xin.ctkqiang.deauthctrl.terminal.TerminalSpecs

/**
 * 本地终端会话内核 —— 基于 Termux 引擎（v0.118.3 vendor，Apache 2.0）。
 *
 * ## 职责
 * 1. 用 [TerminalSpec] 组装 shell / 环境变量，创建 Termux 的
 *    `TerminalSession`（其内部 JNI 完成 pty 分配与 fork/exec）；
 * 2. 通过 [TerminalClientBridge] 承接会话回调，转成 Compose 可订阅的状态流；
 * 3. 管理会话生命周期：启动、SIGKILL 终止、清屏、释放。
 *
 * ## 生命周期要点（与 Termux 一致）
 * - `TerminalSession` 的子进程在第一次 `updateSize()`（视图完成布局）时才
 *   真正 spawn；因此“连接”后状态先停在 [Status.Starting]，视图附加并
 *   测出尺寸后经 `onEmulatorSet` 切到 [Status.Running]。
 * - 退出统一由 `TerminalSession` 内部的 waiter 线程 waitpid 后回调
 *   [TerminalClientBridge.onSessionFinished]，不会产生僵尸进程。
 * - 尚未 spawn 的会话（视图从未布局）`pid == 0`：此时**不能**调用
 *   `finishIfRunning()`，因为上游实现会对 pid 0 发 SIGKILL（POSIX 语义
 *   下 kill(0, …) 会打到调用进程的进程组）。这里用 `pid > 0` 守卫。
 *
 * ## 线程模型
 * 会话的读写、waiter 线程均由 `TerminalSession` 内部管理；本类只在主线程
 * 上创建会话、转发回调（Termux 约定所有 emulator 与回调都在主线程）。
 */
class TerminalManager(context: Context) {

    /** 会话状态机。 */
    sealed interface Status {
        /** 尚未连接。 */
        data object Idle : Status

        /** 会话对象已创建，等待视图布局完成后 spawn 子进程。 */
        data object Starting : Status

        /** 子进程运行中。 */
        data class Running(val pid: Int) : Status

        /** 子进程已退出。 */
        data class Finished(val exitCode: Int) : Status

        /** 启动失败（shell 不可执行等）。 */
        data class Failed(val message: String) : Status
    }

    private val appContext: Context = context.applicationContext

    private val _status = MutableStateFlow<Status>(Status.Idle)

    /** 当前会话状态。 */
    val status: StateFlow<Status> = _status

    private val _title = MutableStateFlow<String?>(null)

    /** 终端标题（OSC 0/2 序列设置，如 shell 或 vim 报告的标题）。 */
    val title: StateFlow<String?> = _title

    private val _copyMode = MutableStateFlow(false)

    /** 文本选择 / 复制模式是否激活。 */
    val copyMode: StateFlow<Boolean> = _copyMode

    private val _bell = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** 终端响铃事件（BEL 字符）。 */
    val bell: SharedFlow<Unit> = _bell

    /** 会话桥接：TerminalSessionClient + TerminalViewClient 的实现。 */
    val bridge = TerminalClientBridge(
        context = appContext,
        onTitleChangedListener = { _title.value = it },
        onSessionFinishedListener = ::handleSessionFinished,
        onBellListener = { _bell.tryEmit(Unit) },
        onEmulatorReadyListener = ::handleEmulatorReady,
        onCopyModeChangedListener = { _copyMode.value = it },
    )

    private val lifecycleLock = Any()

    /** shell 候选列表与当前索引 —— exec 被设备拒绝时自动切换下一个 */
    private val shellCandidates = mutableListOf<String>()
    private var candidateIndex = 0

    @Volatile
    private var session: TerminalSession? = null

    /** 当前使用的运行模式；重连时沿用。 */
    @Volatile
    var mode: ShellMode = ShellMode.LOCAL
        private set

    init {
        // 全局配色：任何会话创建前生效一次（幂等）。
        TerminalSpecs.applyHudColorScheme()
    }

    /** 设备是否有可用的 su（root 模式开关的显示依据）。 */
    val rootAvailable: Boolean get() = TerminalSpecs.isRootAvailable()

    /** 当前会话；视图附加时读取，可能为 null。 */
    fun currentSession(): TerminalSession? = session

    /**
     * 启动新会话。
     *
     * 已有会话先 SIGKILL 终止（Termux `finishIfRunning` 语义），再创建新
     * `TerminalSession`。子进程的实际 spawn 延迟到视图布局（见类注释）。
     *
     * @param newMode 运行模式；ROOT 在无 su 时由 [TerminalSpec.build] 回退 LOCAL。
     */
    fun start(newMode: ShellMode) {
        mode = newMode

        // 旧会话（可能还在运行）直接终止；其退出回调会因身份不符被忽略。
        synchronized(lifecycleLock) {
            session?.let { old ->
                if (old.pid > 0 && old.isRunning) {
                    old.finishIfRunning()
                }
            }
            session = null
        }
        _title.value = null
        _copyMode.value = false

        val spec = try {
            if (newMode == ShellMode.LOCAL) {
                // 首次启动时准备全部候选,之后自动回退直接取下一个
                if (shellCandidates.isEmpty()) {
                    shellCandidates.addAll(TerminalSpecs.prepareShellCandidates(appContext))
                    candidateIndex = 0
                }
                val base = TerminalSpecs.build(appContext, newMode)
                base.copy(shellPath = shellCandidates.getOrElse(candidateIndex) { base.shellPath })
            } else {
                candidateIndex = 0
                TerminalSpecs.build(appContext, newMode)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "terminal spec build failed", t)
            _status.value = Status.Failed(describe(t))
            return
        }

        val newSession = try {
            TerminalSession(spec.shellPath, spec.cwd, spec.args, spec.env, null, bridge)
        } catch (t: Throwable) {
            Log.w(TAG, "terminal session create failed", t)
            _status.value = Status.Failed(describe(t))
            return
        }
        newSession.mSessionName = spec.label

        synchronized(lifecycleLock) {
            session = newSession
        }
        _status.value = Status.Starting
    }

    /** 使用当前 [mode] 重连（主页快捷开关 / 退出后重开用）。 */
    fun start() = start(mode)

    /**
     * 终止当前会话。
     *
     * - 已 spawn（`pid > 0`）：SIGKILL，视图保持附加，稍后经回调显示
     *   “[Process completed]”与退出码；
     * - 尚未 spawn（`pid == 0`，视图还没完成布局）：会话对象直接废弃，
     *   配合界面侧 detach 视图，保证它永远不会被启动。
     */
    fun stop() {
        synchronized(lifecycleLock) {
            val current = session ?: return@synchronized
            if (current.pid > 0 && current.isRunning) {
                current.finishIfRunning()
            } else if (current.pid <= 0) {
                session = null
                _status.value = Status.Idle
            }
        }
    }

    /**
     * 清屏。
     *
     * 与 Termux 的 Reset 一致：先清空模拟器屏幕与回滚，再向 shell 发送
     * Ctrl-L 让行编辑与全屏程序立即重绘提示符。会话未运行或未 spawn 时
     * 只清本地缓冲。
     */
    fun clear() {
        val current = session ?: return
        if (!current.isRunning) {
            runCatching { current.reset() }
            return
        }
        if (current.pid > 0) {
            runCatching { current.reset() }
            // Ctrl-L：让 shell 的行编辑 / 全屏程序立即重绘提示符。
            val ctrlL = byteArrayOf(0x0C)
            current.write(ctrlL, 0, ctrlL.size)
        }
    }

    /** 向 shell 写入文本（UTF-8）。会话未就绪时静默忽略。 */
    fun send(text: String) {
        if (text.isEmpty()) return
        session?.let { if (it.isRunning) it.write(text) }
    }

    /** 释放：终止子进程，供 ViewModel.onCleared 调用。 */
    fun dispose() {
        stop()
        synchronized(lifecycleLock) {
            session = null
        }
    }

    /** 视图完成 emulator 初始化（子进程已 spawn）时的回调。 */
    private fun handleEmulatorReady() {
        val current = session ?: return
        val pid = current.pid
        if (pid > 0) {
            _status.value = Status.Running(pid)
        }
    }

    /** 会话退出回调；只有当前会话本体的退出才更新状态机。 */
    private fun handleSessionFinished(finished: TerminalSession) {
        val current = session ?: return
        if (finished !== current) return

        // exec 被设备 SELinux 拒绝时的诊断文本（toybox/系统 shell 通用）
        val transcript = runCatching { finished.emulator.screen.transcriptText }.getOrNull() ?: ""
        val execDenied = transcript.contains("Permission denied") ||
            transcript.contains("inaccessible or not found")

        if (execDenied && mode == ShellMode.LOCAL && candidateIndex < shellCandidates.size - 1) {
            // 自动切换下一个 shell 候选重试
            candidateIndex++
            val next = shellCandidates[candidateIndex]
            Log.w(TAG, "shell exec 被拒绝,切换候选 $candidateIndex/${shellCandidates.size}: $next")
            _status.value = Status.Failed("shell 被设备策略拒绝,正在切换候选 ($candidateIndex/${shellCandidates.size})…")
            // 稍等一帧让视图 detach 旧会话,再重启
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                start(mode)
            }, 400)
            return
        }

        if (execDenied && candidateIndex >= shellCandidates.size - 1) {
            _status.value = Status.Failed(
                "本机策略禁止应用执行 shell(常见于部分厂商 ROM 的增强 SELinux)。" +
                    (if (rootAvailable) "请切换 ROOT 模式重试。" else "本机无 su,可尝试 root 后使用 ROOT 模式。"),
            )
            return
        }
        _status.value = Status.Finished(finished.exitStatus)
    }

    private fun describe(t: Throwable): String =
        t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName

    private companion object {
        const val TAG = "TerminalManager"
    }
}
