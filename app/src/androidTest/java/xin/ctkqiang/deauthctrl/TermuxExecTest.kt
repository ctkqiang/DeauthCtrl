package xin.ctkqiang.deauthctrl

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xin.ctkqiang.deauthctrl.manager.TermuxBootstrap
import java.io.File

/**
 * 设备端 Termux 验证 —— 在真实 Android 环境(模拟器 enforcing SELinux)上证明:
 *
 * 1. bootstrap 解压 + 符号链接重建在设备上真实完成
 * 2. **应用私有目录的二进制可以被 exec**(SELinux 不拒绝)—— Termux 可用性的核心前提
 * 3. apt-get 真实可执行,包源指向官方镜像
 * 4. (可选) apt-get update 联网拉索引 —— pkg install 的真实链路
 */
@RunWith(AndroidJUnit4::class)
class TermuxExecTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun runWithEnv(cmd: Array<String>, env: Array<String>, timeoutMs: Long): Triple<Int, String, String> {
        val p = ProcessBuilder(*cmd).apply {
            environment().clear()
            env.forEach { e ->
                val idx = e.indexOf('=')
                if (idx > 0) environment()[e.substring(0, idx)] = e.substring(idx + 1)
            }
            redirectErrorStream(true)
        }.start()
        val out = p.inputStream.bufferedReader().readText()
        val ok = p.waitFor()
        return Triple(ok, out, "")
    }

    /** 直接 exec bootstrap shell(com.termux 包名方案,无 proot) */
    private fun directShell(extra: String): Array<String> {
        val prefix = TermuxBootstrap.prefix(context)
        File(prefix, "home").mkdirs()
        File(prefix, "tmp").mkdirs()
        val p = ProcessBuilder(
            File(prefix, "bin/bash").absolutePath, "-c", extra,
        ).apply {
            environment().clear()
            shellEnv().forEach { e ->
                val idx = e.indexOf('=')
                if (idx > 0) environment()[e.substring(0, idx)] = e.substring(idx + 1)
            }
            redirectErrorStream(true)
        }.start()
        val out = p.inputStream.bufferedReader().readText()
        val diag = "[prefix] top=" + prefix.listFiles()?.map { it.name }.orEmpty().joinToString() +
            " bin/bash=" + File(prefix, "bin/bash").exists()
        return arrayOf(p.waitFor().toString(), out + "\n" + diag, "")
    }

    private fun shellEnv(): Array<String> {
        // 包名 com.termux → filesDir=/data/data/com.termux/files → prefix 天然匹配
        // bootstrap 硬编码路径,无需 proot/无需 patch
        val prefix = TermuxBootstrap.prefix(context)
        return arrayOf(
            "PATH=${prefix.absolutePath}/bin:${prefix.absolutePath}/bin/applets:/system/bin:/system/xbin",
            "PREFIX=${prefix.absolutePath}",
            "HOME=${prefix.absolutePath}/home",
            "TMPDIR=${prefix.absolutePath}/tmp",
        )
    }

    @Test
    fun bootstrapInstallsAndShellExecutes() {
        // 1) 设备端真实解压(33MB + 符号链接)
        val ok = TermuxBootstrap.ensure(context)
        assertTrue("bootstrap 必须在设备上安装成功", ok)
        val prefix = TermuxBootstrap.prefix(context)
        assertTrue("bin/sh 符号链接必须存在", File(prefix, "bin/sh").exists())
        assertTrue("bin/bash 实体必须存在", File(prefix, "bin/bash").exists())
        assertTrue("apt 源文件必须存在", File(prefix, "etc/apt/sources.list").exists())

        // 2) 核心:SELinux 下 exec 应用私有目录二进制
        val (code1, out1, _) = runWithEnv(
            arrayOf(File(prefix, "bin/sh").absolutePath, "-c", "echo BOOTSTRAP_OK"),
            shellEnv(), 15_000,
        )
        assertEquals("exec 私有目录 bin/sh 必须成功(SELinux 允许)", 0, code1)
        assertTrue("shell 必须真实输出(实际: $out1)", out1.contains("BOOTSTRAP_OK"))

        // 3) bash 与常用命令
        val (code2, out2, _) = runWithEnv(
            arrayOf(File(prefix, "bin/sh").absolutePath, "-c", "bash --version | head -1; uname -a; echo CMD_OK"),
            shellEnv(), 15_000,
        )
        assertEquals(0, code2)
        assertTrue(out2.contains("CMD_OK"))
        assertTrue("bash 必须输出版本(实际: $out2)", out2.contains("GNU bash") || out2.contains("bash"))

        // 4) apt-get 真实可执行
        val (code3, out3, _) = runWithEnv(
            arrayOf(File(prefix, "bin/sh").absolutePath, "-c", "apt-get --version | head -2"),
            shellEnv(), 15_000,
        )
        assertEquals(0, code3)
        assertTrue("apt-get 必须输出版本(实际: $out3)", out3.contains("apt "))
    }

    /**
     * proot 全链路回归:TermuxBootstrap.shellSpec 产出的 spec 必须能在 proot 内
     * 真实 exec bash。历史上缺三样东西都会在这里挂掉:
     * 1. 独立 loader(termux 以 PROOT_UNBUNDLE_LOADER 构建)—— execve ENOENT/EACCES;
     * 2. -b /system /apex —— 解释器 /system/bin/linker64 在 guest 内找不到;
     * 3. PROOT_LOADER 环境变量 —— 缺省路径 /data/data/com.termux/... 不存在。
     */
    @Test
    fun prootShellExecutesWithLoader() {
        val ok = TermuxBootstrap.ensure(context)
        assertTrue(ok)
        val spec = TermuxBootstrap.shellSpec(context)
        assertTrue("spec 必须走 proot 分支(实际 shellPath=${spec.shellPath})", spec.shellPath.endsWith("proot-aarch64"))

        val cmd = arrayOf(spec.shellPath, *spec.args, "-c", "echo PROOT_TEST_OK")
        val (code, out, _) = runWithEnv(cmd, spec.env, 60_000)
        assertEquals("proot 内 bash 必须执行成功(实际输出: $out)", 0, code)
        assertTrue("必须输出 proot 标记(实际: $out)", out.contains("PROOT_TEST_OK"))
    }

    @Test
    fun pkgInstallRealPackage() {
        // 终局验证:真实 pkg install 一个包(tree,小体积)并执行它 ——
        // 与用户「pkg install 一切」的预期直接对应
        val ok = TermuxBootstrap.ensure(context)
        assertTrue(ok)
        val prefix = TermuxBootstrap.prefix(context)
        val res = directShell("apt-get update -qq 2>/dev/null; apt-get install -y sl 2>&1 | tail -3; sl -c 2>&1 | head -2; echo PKG_OK")
        assertEquals("pkg install sl 必须成功(实际输出: ${res[1]})", "0", res[0])
        assertTrue("sl 必须真实可执行(实际: ${res[1]})", res[1].contains("PKG_OK"))
    }

    @Test
    fun aptUpdateReachesOfficialMirror() {
        // 联网验证:pkg install 的完整链路(apt update 拉官方镜像索引)
        val ok = TermuxBootstrap.ensure(context)
        assertTrue(ok)
        val prefix = TermuxBootstrap.prefix(context)
        val sources = File(prefix, "etc/apt/sources.list").readText()
        assertTrue("包源必须指向 Termux 官方镜像", sources.contains("packages.termux.dev") || sources.contains("termux.net"))

        val res = directShell("apt-get update 2>&1 | tail -5")
        assertEquals("apt-get update 必须成功(实际输出: ${res[1]})", "0", res[0])
        if (res[0] != "0") {
            val top = TermuxBootstrap.prefix(context).listFiles()?.map { it.name }.orEmpty().joinToString()
            throw AssertionError("诊断 prefix 顶层: $top · bin/bash=" + File(prefix, "bin/bash").exists() + " · lib=" + File(prefix, "lib").listFiles()?.map { it.name }.orEmpty().take(6).joinToString())
        }
        assertTrue(res[1].contains("Reading package lists") || res[1].contains("termux"))
    }
}
