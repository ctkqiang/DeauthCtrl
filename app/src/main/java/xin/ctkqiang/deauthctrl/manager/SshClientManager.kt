package xin.ctkqiang.deauthctrl.manager

import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread

/**
 * SSH 客户端内核 —— JSch(mwiede 分支) + 内置 VT100 字符网格。
 *
 * 连接流程:JSch Session(密码认证,StrictHostKeyChecking=no —— 战术工具
 * 场景优先可用性)→ ChannelShell + xterm-256color pty → 读线程把远端输出
 * 喂给 [TermGrid] 解析渲染,键盘输入经管道写回通道。
 *
 * 全部 IO 在后台线程;UI 通过 [status] 与 [screenVersion] 订阅状态与重绘。
 */
class SshClientManager {

    sealed interface Status {
        data object Idle : Status
        data object Connecting : Status
        data object Connected : Status
        data class Failed(val message: String) : Status
        data class Closed(val message: String? = null) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    /** 终端网格(字符单元,VT100 子集解析) */
    val grid = TermGrid(DEFAULT_COLS, DEFAULT_ROWS)

    /** 网格每变化一次自增,UI 据此重绘(避免高频 StateFlow<String> 分配) */
    private val _screenVersion = MutableStateFlow(0L)
    val screenVersion: StateFlow<Long> = _screenVersion

    private var session: Session? = null
    private var channel: ChannelShell? = null

    /** 本地 → 远端 的写入管道(通道输入) */
    private var writer: PipedOutputStream? = null

    @Volatile
    private var closed = false

    /** 连接参数 —— 由 UI 表单写入 */
    val host = MutableStateFlow("")
    val port = MutableStateFlow("22")
    val username = MutableStateFlow("")
    val password = MutableStateFlow("")

    val isOnline: Boolean get() = _status.value is Status.Connected

    /** 连接(后台线程,不阻塞 UI) */
    fun connect() {
        if (_status.value is Status.Connecting || _status.value is Status.Connected) return
        val h = host.value.trim()
        val p = port.value.trim().toIntOrNull() ?: 22
        val u = username.value.trim()
        val pw = password.value
        if (h.isEmpty() || u.isEmpty()) {
            _status.value = Status.Failed(L10n.get(R.string.mgr_ssh_missing_fields))
            return
        }
        _status.value = Status.Connecting
        closed = false
        thread(name = "ssh-connect") {
            try {
                val jsch = JSch()
                val s = jsch.getSession(u, h, p)
                s.setPassword(pw)
                s.setConfig("StrictHostKeyChecking", "no")
                s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                s.setTimeout(CONNECT_TIMEOUT_MS)
                s.connect(CONNECT_TIMEOUT_MS)
                session = s

                val ch = s.openChannel("shell") as ChannelShell
                ch.setPtyType("xterm-256color", grid.cols, grid.rows, 0, 0)
                val pipeIn = PipedInputStream(PIPE_BUFFER)
                val pipeOut = PipedOutputStream(pipeIn)
                ch.setInputStream(pipeIn)
                ch.connect(CHANNEL_TIMEOUT_MS)
                channel = ch
                writer = pipeOut
                _status.value = Status.Connected

                // 读线程:远端输出 → 网格解析 → 触发 UI 重绘
                thread(name = "ssh-read") {
                    val buf = ByteArray(4096)
                    val remote = ch.inputStream
                    try {
                        while (!closed) {
                            val n = remote.read(buf)
                            if (n < 0) break
                            grid.feed(buf, n)
                            _screenVersion.value++
                        }
                    } catch (_: Exception) {
                    } finally {
                        if (!closed) {
                            closed = true
                            _status.value = Status.Closed(L10n.get(R.string.mgr_ssh_disconnected))
                            cleanup()
                        }
                    }
                }
            } catch (t: Throwable) {
                _status.value = Status.Failed(t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
                cleanup()
            }
        }
    }

    /** 断开连接 */
    fun disconnect() {
        closed = true
        cleanup()
        _status.value = Status.Closed()
    }

    /** 发送文本(UTF-8)到远端 shell */
    fun send(text: String) {
        if (text.isEmpty()) return
        sendBytes(text.toByteArray(Charsets.UTF_8))
    }

    /** 发送原始字节(控制键/粘贴等) */
    fun sendBytes(bytes: ByteArray) {
        val w = writer ?: return
        try {
            w.write(bytes)
            w.flush()
        } catch (_: Exception) {
        }
    }

    /** 终端尺寸变化 → 通知远端 pty(WINCH)并重建网格 */
    fun resize(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        grid.resize(cols, rows)
        runCatching { channel?.setPtySize(cols, rows, 0, 0) }
        _screenVersion.value++
    }

    /** 清屏:重置网格 + 远端 ANSI 清屏 */
    fun clear() {
        grid.reset()
        sendBytes(byteArrayOf(0x1B, 0x5B, 0x48, 0x1B, 0x5B, 0x32, 0x4A)) // ESC[H ESC[2J
        _screenVersion.value++
    }

    private fun cleanup() {
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
        channel = null
        session = null
        runCatching { writer?.close() }
        writer = null
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val CHANNEL_TIMEOUT_MS = 15_000
        private const val PIPE_BUFFER = 64 * 1024
        private const val DEFAULT_COLS = 80
        private const val DEFAULT_ROWS = 24
    }
}

/**
 * 轻量 VT100 字符网格 —— 支撑 SSH 会话交互渲染。
 *
 * 支持子集:CUU/CUD/CUF/CUB/CUP(光标移动)、ED/EL(清屏清行)、
 * SCP 保存恢复光标、回车换行退格制表、滚动、OSC 忽略。
 * SGR 颜色与 DEC 私用模式忽略(界面为单色 HUD 风格)。
 */
class TermGrid(initialCols: Int, initialRows: Int) {

    var cols = initialCols.coerceAtLeast(2)
        private set
    var rows = initialRows.coerceAtLeast(2)
        private set

    private var cells = CharArray(cols * rows) { ' ' }
    private var cx = 0
    private var cy = 0
    private var savedX = 0
    private var savedY = 0

    private var escState = 0 // 0=普通 1=ESC 2=CSI 3=OSC
    private val escBuf = StringBuilder()

    /** 输入远端字节流,增量解析 */
    fun feed(data: ByteArray, len: Int) {
        for (i in 0 until len) feedByte(data[i].toInt() and 0xFF)
    }

    private fun feedByte(b: Int) {
        when (escState) {
            0 -> when (b) {
                0x1B -> { escState = 1; escBuf.clear() }
                0x0D -> cx = 0
                0x0A -> lineFeed()
                0x08 -> if (cx > 0) cx--
                0x09 -> cx = ((cx / 8) + 1) * 8
                else -> putChar(b.toChar())
            }
            1 -> when (b) {
                '['.code -> escState = 2
                ']'.code -> escState = 3
                'c'.code -> { reset(); escState = 0 }
                'M'.code -> { if (cy > 0) cy--; escState = 0 }
                else -> escState = 0
            }
            2 -> if (b in 0x40..0x7E) {
                handleCsi(escBuf.toString(), b.toChar())
                escState = 0
            } else if (escBuf.length < 64) {
                escBuf.append(b.toChar())
            }
            3 -> if (b == 0x07) escState = 0 // BEL 结束 OSC
        }
        if (cx >= cols) { cx = 0; lineFeed() }
        if (cy >= rows) cy = rows - 1
    }

    private fun handleCsi(params: String, final: Char) {
        val nums = params.trimStart('?').split(';').mapNotNull { it.trim().toIntOrNull() }
        fun n(i: Int, def: Int) = nums.getOrElse(i) { def }.coerceAtLeast(1)
        when (final) {
            'A' -> cy = (cy - n(0, 1)).coerceAtLeast(0)
            'B' -> cy = (cy + n(0, 1)).coerceAtMost(rows - 1)
            'C' -> cx = (cx + n(0, 1)).coerceAtMost(cols - 1)
            'D' -> cx = (cx - n(0, 1)).coerceAtLeast(0)
            'H', 'f' -> {
                cy = (n(0, 1) - 1).coerceIn(0, rows - 1)
                cx = (n(1, 1) - 1).coerceIn(0, cols - 1)
            }
            'G' -> cx = (n(0, 1) - 1).coerceIn(0, cols - 1)
            'd' -> cy = (n(0, 1) - 1).coerceIn(0, rows - 1)
            'J' -> when (n(0, 0)) {
                0 -> eraseBelow()
                1 -> eraseAbove()
                else -> reset()
            }
            'K' -> when (n(0, 0)) {
                0 -> eraseLineToEnd()
                1 -> eraseLineFromStart()
                else -> eraseLine()
            }
            's' -> { savedX = cx; savedY = cy }
            'u' -> { cx = savedX; cy = savedY }
            else -> {} // SGR(m) / 私用模式(h,l,r) 忽略
        }
    }

    private fun putChar(c: Char) {
        if (c == '\n' || c == '\r') return
        cells[cy * cols + cx] = c
        cx++
    }

    private fun lineFeed() {
        cy++
        if (cy >= rows) {
            System.arraycopy(cells, cols, cells, 0, cols * (rows - 1))
            cells.fill(' ', cols * (rows - 1), cols * rows)
            cy = rows - 1
        }
    }

    private fun eraseLineToEnd() = cells.fill(' ', cy * cols + cx, (cy + 1) * cols)
    private fun eraseLineFromStart() = cells.fill(' ', cy * cols, cy * cols + cx + 1)
    private fun eraseLine() = cells.fill(' ', cy * cols, (cy + 1) * cols)
    private fun eraseBelow() = cells.fill(' ', cy * cols + cx, cols * rows)
    private fun eraseAbove() = cells.fill(' ', 0, cy * cols + cx + 1)

    fun reset() {
        cells.fill(' ')
        cx = 0
        cy = 0
    }

    /** 尺寸变化:保留重叠区域内容,其余清空 */
    fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        val oldCols = cols
        val oldRows = rows
        val newCells = CharArray(newCols * newRows) { ' ' }
        val copyCols = minOf(oldCols, newCols)
        val copyRows = minOf(oldRows, newRows)
        for (r in 0 until copyRows) {
            System.arraycopy(cells, r * oldCols, newCells, r * newCols, copyCols)
        }
        cols = newCols
        rows = newRows
        cells = newCells
        cx = cx.coerceAtMost(cols - 1)
        cy = cy.coerceAtMost(rows - 1)
    }

    /** 渲染为文本(行尾空格裁剪,底部空行裁剪,光标以 ▍ 标记) */
    fun render(): String {
        val sb = StringBuilder(rows * (cols + 1))
        for (r in 0 until rows) {
            val start = r * cols
            var end = start + cols
            while (end > start && cells[end - 1] == ' ') end--
            sb.append(cells, start, end - start)
            if (r < rows - 1) sb.append('\n')
        }
        // 底部空行裁剪
        while (sb.isNotEmpty() && sb.last() == '\n') sb.setLength(sb.length - 1)
        // 光标标记:插入到光标所在行
        if (cy < rows) {
            // 重建带光标标记的文本:直接定位 cy 行的偏移较繁琐,这里按行切分处理
            val lines = sb.toString().split('\n').toMutableList()
            while (lines.size < cy + 1) lines.add("")
            val line = lines[cy]
            val idx = cx.coerceAtMost(line.length)
            lines[cy] = line.substring(0, idx) + "▍" + line.substring(idx)
            return lines.joinToString("\n")
        }
        return sb.toString()
    }
}
