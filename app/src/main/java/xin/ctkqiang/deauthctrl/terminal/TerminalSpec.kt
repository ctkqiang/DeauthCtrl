package xin.ctkqiang.deauthctrl.terminal

import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

import android.content.Context
import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle
import java.io.File

/** 会话运行模式：普通本地 shell，或 root shell（su）。 */
enum class ShellMode { LOCAL, ROOT }

/**
 * 一次终端会话的启动规格：可执行文件、argv、环境变量、工作目录。
 *
 * 交给 Termux 的 `TerminalSession`（terminal-emulator 库，Apache 2.0），
 * 由其内部 JNI `createSubprocess` 完成 pty 分配与 fork/exec。
 */
data class TerminalSpec(
    val shellPath: String,
    val args: Array<String>,
    /** PATH 环境变量值 —— 由 TerminalManager 按成功候选联动调整 */
    val path: String,
    val env: Array<String>,
    val cwd: String,
    /** 会话标识（argv[0] 语义，如 "sh" / "su"）。 */
    val label: String,
)

/**
 * 会话规格构造器。
 *
 * 行为对齐 Termux v0.118.3 的会话环境构造（TermuxSession / TermuxShellUtils 的
 * 行为参照，代码为本项目独立编写）：
 * - 默认 shell 回退到 `/system/bin/sh`，且不以登录 shell 启动
 *   （避免损坏的 `~/.profile` 导致终端打不开）；
 * - 环境变量包含 TERM / COLORTERM / HOME / PWD / TMPDIR / PATH / ENV / PS1 / SHELL；
 * - `ENV` 指向应用自写的 `~/.shrc`，mksh 与 toybox ash 的交互模式都会读取它，
 *   PS1 别名等可被用户修改后持久生效。
 *
 * ## Android 10+ 的 shell 自举
 * SELinux 下 untrusted_app 域不能 execve 系统分区里的 ELF（/system/bin/sh
 * 会得到 "inaccessible or not found"）。因此首次启动时把 toybox（系统 sh
 * 的实际二进制）复制进应用私有目录，并为常用命令创建指向它的 symlink，
 * PATH 以私有 bin 优先 —— 与 Termux bootstrap 同一策略。
 */
object TerminalSpecs {

    const val DEFAULT_SHELL = "/system/bin/sh"

    /** 私有 bin 目录 —— toybox 副本与命令 symlink 的落点。 */
    fun privateBin(context: Context): File = File(context.filesDir, "bin")

    /** toybox 常见 applet 分发集合（symlink 名 → toybox argv[0] 语义）。 */
    private val APPLETS = listOf(
        "sh", "ls", "cat", "cp", "mv", "rm", "mkdir", "rmdir", "touch", "echo",
        "pwd", "chmod", "chown", "grep", "egrep", "fgrep", "head", "tail", "wc",
        "ps", "kill", "pidof", "df", "du", "date", "sleep", "find", "xargs",
        "clear", "id", "whoami", "uname", "tar", "gzip", "gunzip", "md5sum",
        "sha1sum", "hexdump", "strings", "sed", "sort", "uniq", "cut", "tr",
        "tee", "readlink", "realpath", "basename", "dirname", "env", "expr",
        "printf", "test", "true", "false", "yes", "seq", "timeout", "which",
        "file", "stat", "getprop", "setprop", "dd", "netstat", "ping", "route",
        "insmod", "rmmod", "lsmod", "lsof", "nc", "vmstat", "iostat", "top",
    )

    /** root 模式下 su 的常见路径（Magisk / SuperSU / 系统自带）。 */
    private val SU_CANDIDATES = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/system/sbin/su",
        "/vendor/bin/su",
    )

    private val LOCAL_PATH =
        "/system/bin:/system/xbin:/vendor/bin:/product/bin:/apex/com.android.runtime/bin"

    private val ROOT_PATH =
        "/sbin:/system/sbin:/su/bin:/system/bin:/system/xbin:/vendor/bin" +
            ":/data/local/tmp:/apex/com.android.runtime/bin"

    /** 首个可执行的 su；没有则返回 null。 */
    fun findSu(): String? = SU_CANDIDATES.firstOrNull { File(it).canExecute() }

    fun isRootAvailable(): Boolean = findSu() != null

    /** shell 的 HOME 与起始工作目录：应用私有 `files/home`。 */
    fun homeDir(context: Context): File = File(context.filesDir, "home")

    /**
     * 准备本地 shell 候选列表 —— 依次为：
     * 1. 系统 /system/bin/sh（Android 9- 或 root 设备直接可用）
     * 2. 应用私有 files/bin 的 toybox 副本（Android 10+ 标准方案）
     * 3. 应用 cache 目录的副本（部分厂商 ROM 对 filesDir exec 有额外限制）
     *
     * 全部候选在此一次性备好；TerminalManager spawn 后若终端输出
     * "Permission denied / inaccessible or not found" 会按序自动切换。
     */
    fun prepareShellCandidates(context: Context): List<String> {
        val bin = privateBin(context)
        val filesCopy = prepareCopy(File(bin, "toybox"))
        val cacheToybox = File(context.cacheDir, "sh-toybox")
        val cacheCopy = prepareCopy(cacheToybox)

        val candidates = mutableListOf<String>()
        if (File(DEFAULT_SHELL).exists()) candidates.add(DEFAULT_SHELL)
        filesCopy?.let { c ->
            candidates.add(c.absolutePath)
            // files 副本就位时顺带建命令 symlink 分发
            ensureApplets(bin, c)
        }
        cacheCopy?.let { c ->
            candidates.add(c.absolutePath)
            // cache 副本同样分发命令(部分厂商 ROM 只允许 cache 目录 exec)
            ensureApplets(File(context.cacheDir, "sh-bin"), c)
        }
        return candidates.distinct()
    }

    /** 成功候选对应的命令 applet 目录;null = 系统 sh(无需私有分发) */
    fun appletDirFor(shellPath: String, context: Context): String? {
        val app = context.applicationContext
        return when {
            shellPath.startsWith(privateBin(app).absolutePath) -> privateBin(app).absolutePath
            shellPath.startsWith(app.cacheDir.absolutePath) ->
                File(app.cacheDir, "sh-bin").absolutePath
            else -> null
        }
    }

    /** 纯系统 PATH(候选为系统 sh 或私有 exec 全部被禁时使用) */
    fun systemPath(): String = LOCAL_PATH

    /** 复制 toybox 副本并赋执行位；失败返回 null */
    private fun prepareCopy(target: File): File? {
        return try {
            if (target.canExecute() && target.length() > 0L) {
                target
            } else {
                val systemSh = File(DEFAULT_SHELL).canonicalFile
                if (!systemSh.exists() || !systemSh.canRead()) {
                    null
                } else {
                    target.parentFile?.mkdirs()
                    systemSh.copyTo(target, overwrite = true)
                    target.setReadable(true, false)
                    target.setExecutable(true, false)
                    if (target.canExecute()) target else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 为 toybox 副本建立常用命令 symlink 分发（幂等,跨目录用相对目标） */
    private fun ensureApplets(bin: File, toybox: File) {
        val target = if (toybox.parentFile?.absolutePath == bin.absolutePath) {
            toybox.name
        } else {
            "../" + toybox.name
        }
        // "sh" 入口也需要（toybox 按 argv[0] 分发 applet）
        APPLETS.forEach { name ->
            val link = File(bin, name)
            // 旧版本可能残留不可执行的同名普通文件(而非 symlink):
            // 存在但不可执行时一律删除重建 —— 否则 PATH 命中它只会得到
            // "Permission denied",且永远无法自愈
            if (link.exists() && !link.canExecute()) {
                runCatching { link.delete() }
            }
            if (!link.exists()) {
                runCatching { android.system.Os.symlink(target, link.absolutePath) }
            }
        }
    }

    /**
     * 构造一次会话的启动参数。
     *
     * ROOT 模式下若设备没有 su，安全回退到 LOCAL 而不是直接失败。
     */
    fun build(context: Context, mode: ShellMode): TerminalSpec {
        val appContext = context.applicationContext
        val home = homeDir(appContext).absolutePath
        val tmp = appContext.cacheDir.absolutePath

        val shellPath: String
        val args: Array<String>
        val label: String
        val path: String

        when (mode) {
            ShellMode.LOCAL -> {
                // 候选按序尝试;TerminalManager 会在 exec 失败时自动切换,
                // 并据成功候选决定 PATH 前缀(见 TerminalManager.start)
                shellPath = prepareShellCandidates(appContext).firstOrNull() ?: DEFAULT_SHELL
                args = arrayOf("sh")
                label = "sh"
                path = LOCAL_PATH
            }

            ShellMode.ROOT -> {
                val su = findSu()
                if (su == null) return build(appContext, ShellMode.LOCAL)
                shellPath = su
                args = arrayOf("su")
                label = "su"
                path = ROOT_PATH
            }
        }

        val profile = File(home, ".shrc")
        writeProfile(profile, path)

        // PS1 不内嵌颜色码：mksh / ash 要求不可打印段用特殊标记包裹，
        // 否则长命令的换行与退格位置会错乱。保持纯文本提示符换取行编辑稳定。
        val env = arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "HOME=$home",
            "PWD=$home",
            "TMPDIR=$tmp",
            "PATH=$path",
            "ENV=$profile",
            "PS1=" + L10n.get(R.string.term_spec_ps1) + " ",
            "SHELL=$shellPath",
        )
        return TerminalSpec(shellPath, args, path, env, home, label)
    }

    /**
     * 写 `~/.shrc`。
     *
     * 幂等：只有内容与磁盘上不同时才重写，用户手工修改过提示符等内容后
     * 不会在每次启动时被无谓覆盖（除非应用这边的内容本身又变了）。
     */
    private fun writeProfile(profile: File, path: String) {
        runCatching {
            if (profile.parentFile?.exists() == false) profile.parentFile?.mkdirs()
            val content = buildString {
                appendLine("# " + L10n.get(R.string.term_spec_profile_comment))
                appendLine("export PATH=\"$path\"")
                appendLine("export PS1='" + L10n.get(R.string.term_spec_ps1) + " '")
                appendLine("alias ll='ls -la'")
            }
            if (!profile.exists() || profile.readText() != content) {
                profile.writeText(content)
            }
        }
    }

    /**
     * 把 Termux 默认配色替换成应用的全息 HUD 配色。
     *
     * `TerminalColors.COLOR_SCHEME.mDefaultColors` 是全局静态方案，任何
     * 会话创建前的修改都会被后续所有 `TerminalEmulator` 继承。幂等，
     * 在 [xin.ctkqiang.deauthctrl.manager.TerminalManager] 初始化时调用一次。
     */
    fun applyHudColorScheme() {
        val scheme = TerminalColors.COLOR_SCHEME

        // 深海军蓝底 + 青白前景 + 战术绿光标，与页面其余部分同一套视觉。
        scheme.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND] = 0xFF04121C.toInt()
        scheme.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND] = 0xFFE8F4FF.toInt()
        scheme.mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] = 0xFF4DFF88.toInt()

        // ANSI 16 色沿用原自研模拟器为 HUD 调校的同一份调色板。
        val palette = intArrayOf(
            0xFF1A2733.toInt(), // black
            0xFFFF5A5A.toInt(), // red
            0xFF5AF78E.toInt(), // green
            0xFFF3F99D.toInt(), // yellow
            0xFF57C7FF.toInt(), // blue
            0xFFFF6AC1.toInt(), // magenta
            0xFF9AEDFE.toInt(), // cyan
            0xFFE8F4FF.toInt(), // white
            0xFF7E93AD.toInt(), // bright black
            0xFFFF8A8A.toInt(), // bright red
            0xFF8CFFB0.toInt(), // bright green
            0xFFFFFFB0.toInt(), // bright yellow
            0xFF8AD8FF.toInt(), // bright blue
            0xFFFF9AD8.toInt(), // bright magenta
            0xFFBDF3FF.toInt(), // bright cyan
            0xFFFFFFFF.toInt(), // bright white
        )
        for (i in palette.indices) {
            scheme.mDefaultColors[i] = palette[i]
        }
    }
}
