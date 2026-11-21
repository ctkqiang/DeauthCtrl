package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 真·nmap 运行器 —— 在应用内置的 Termux 前缀(proot)里执行 nmap
 *
 * 与 TermuxBootstrap.shellSpec 同一套 proot 绑定;单命令模式:
 * `proot -0 -r <prefix> -b ... /bin/sh -c <command>`。
 * 环境与 nmap 本体都由 [TermuxBootstrap.ensure] + apt 安装,应用内
 * 一键完成,无需 root。
 */
object NmapRunner {

    /** nmap 是否已安装(前缀内存在可执行文件) */
    fun isNmapInstalled(context: Context): Boolean {
        val bin = File(TermuxBootstrap.prefix(context), "bin/nmap")
        return bin.exists() && bin.canExecute()
    }

    /** 经 apt 安装 nmap(流式回调每行输出);返回退出码 */
    suspend fun aptInstallNmap(context: Context, onLine: (String) -> Unit): Int {
        val ok = exec(context, "apt-get update -y", onLine)
        if (ok != 0) return ok
        return exec(context, "DEBIAN_FRONTEND=noninteractive apt-get install -y nmap", onLine)
    }

    /**
     * 在 Termux 前缀内执行单条命令,返回退出码;stdout/stderr 逐行回调。
     * 前缀未安装时返回 -2。
     */
    suspend fun exec(context: Context, command: String, onLine: (String) -> Unit): Int =
        withContext(Dispatchers.IO) {
            if (!TermuxBootstrap.isInstalled(context)) return@withContext -2
            val proot = TermuxBootstrap.prootPath(context)
            val loader = TermuxBootstrap.loaderPath(context)
            if (!proot.exists() || !loader.exists()) return@withContext -3
            val realPrefix = TermuxBootstrap.prefix(context).canonicalPath
            val cmd = mutableListOf(
                proot.absolutePath, "-0",
                "-r", realPrefix,
                "-b", realPrefix + ":/data/data/com.termux/files/usr",
                "-b", realPrefix + "/home:/data/data/com.termux/files/home",
                "-b", "/system",
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
            )
            if (File("/apex").exists()) cmd += listOf("-b", "/apex")
            if (File("/linkerconfig").exists()) cmd += listOf("-b", "/linkerconfig")
            cmd += listOf(
                "/bin/sh", "-c", "cd /data/data/com.termux/files/home && " + command,
            )
            runCatching {
                val pb = ProcessBuilder(cmd)
                pb.environment()["PATH"] = "/data/data/com.termux/files/usr/bin:/data/data/com.termux/files/usr/bin/applets:/system/bin:/system/xbin"
                pb.environment()["HOME"] = "/data/data/com.termux/files/home"
                pb.environment()["TMPDIR"] = "/data/data/com.termux/files/usr/tmp"
                pb.redirectErrorStream(true)
                val proc = pb.start()
                // 读一行回调一行(父进程关闭 stdin 以告知一次性命令)
                runCatching { proc.outputStream.close() }
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    reader.forEachLine { onLine(it) }
                }
                proc.waitFor()
            }.getOrDefault(-1)
        }
}
