package xin.ctkqiang.deauthctrl.manager

import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Termux bootstrap 解压安装器 —— 纯 Kotlin 无 Android 依赖,可 JVM 单测。
 *
 * 逻辑: ZipInputStream 流式解压 → bin/libexec 置可执行 → 按 SYMLINKS.txt
 * 重建符号链接(格式 "link←target")→ 校验 bin/sh → 写 apt 镜像说明。
 */
object BootstrapInstaller {

    /** 测试环境未初始化 L10n 时降级为空串,保持纯 Kotlin 可单测 */
    private fun tr(resId: Int, vararg args: Any): String =
        runCatching { L10n.get(resId, *args) }.getOrElse { "" }

    fun install(stream: InputStream, dest: File, onProgress: (Float, String) -> Unit = { _, _ -> }): Boolean {
        return try {
            dest.deleteRecursively()
            dest.mkdirs()
            var total = 0
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val target = File(dest, entry.name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> zip.copyTo(out) }
                        if (entry.name.startsWith("bin/") || entry.name.startsWith("libexec/")) {
                            runCatching { target.setExecutable(true, false) }
                        }
                    }
                    total++
                    if (total % 200 == 0) onProgress((total / 7000f).coerceIn(0f, 0.9f), tr(R.string.mgr_bootinst_extracting, total))
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            rebuildSymlinks(dest) { p -> onProgress(0.92f + p * 0.03f, tr(R.string.mgr_bootinst_rebuilding_links)) }
            onProgress(0.97f, tr(R.string.mgr_bootinst_verifying))
            val ok = File(dest, "bin/sh").exists()
            if (ok) ensureMirrorNote(dest)
            onProgress(1f, if (ok) tr(R.string.mgr_bootinst_ready) else tr(R.string.mgr_bootinst_verify_failed))
            ok
        } catch (t: Throwable) {
            onProgress(1f, tr(R.string.mgr_bootinst_failed, t.message.toString()))
            false
        }
    }

    private fun rebuildSymlinks(root: File, onProgress: (Float) -> Unit) {
        val list = File(root, "SYMLINKS.txt")
        if (!list.exists()) return
        var n = 0
        val totalLines = list.readLines().size.coerceAtLeast(1)
        list.readLines().forEach { line ->
            val sep = line.indexOf("←")
            if (sep > 0) {
                // Termux SYMLINKS.txt 格式: "target←link"(目标在前,链接在后)
                val target = line.substring(0, sep).trim()
                val link = File(root, line.substring(sep + 1).trim().removePrefix("./"))
                link.parentFile?.mkdirs()
                runCatching { link.delete() }
                runCatching { java.nio.file.Files.createSymbolicLink(link.toPath(), java.nio.file.Paths.get(target)) }
            }
            n++
            if (n % 200 == 0) onProgress(n / totalLines.toFloat())
        }
    }

    /** 确保 apt 源文件存在并注明镜像来源 */
    fun ensureMirrorNote(root: File) {
        val sourcesDir = File(root, "etc/apt")
        sourcesDir.mkdirs()
        val sources = File(sourcesDir, "sources.list")
        if (!sources.exists() || sources.length() < 10) {
            sources.writeText(
                "# Termux 官方包镜像(与上游 termux-packages 一致)\n" +
                    "deb https://packages.termux.dev/apt/termux-main stable main\n",
            )
        }
        val note = File(sourcesDir, "MIRROR-NOTE.txt")
        note.writeText(
            "本环境的软件源指向 Termux 官方镜像(packages.termux.dev),安装的每个包\n" +
                "都来自其上游仓库,与 Termux 官方一致。切换国内镜像: 终端内执行\n" +
                "  termux-change-repo\n或手动编辑 sources.list 指向 mirrors.tuna.tsinghua.edu.cn/termux。\n",
        )
    }
}
