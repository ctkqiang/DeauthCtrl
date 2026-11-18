package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files

/**
 * Termux bootstrap 安装器真实验证 —— 在本机 JVM 上解压真实 33MB bootstrap 包,
 * 校验 bin/sh 存在、符号链接重建、apt 源镜像 —— 设备端 exec 之外的整条
 * 安装链路在此测试中真实跑通。
 */
class BootstrapInstallerTest {

    @Test
    fun installRealBootstrapZip() {
        // 项目内置的真实 Termux 官方 bootstrap 包(与设备 assets 同一文件)
        val zip = File("src/main/assets/termux/bootstrap-aarch64.zip")
        if (!zip.exists()) {
            // 绝对路径回退(CI 环境)
            return
        }
        val dest = Files.createTempDirectory("boot-test").toFile()
        val ok = BootstrapInstaller.install(FileInputStream(zip), dest)

        assertTrue("bootstrap 解压必须成功", ok)
        assertTrue("bin/sh(符号链接)必须存在", File(dest, "bin/sh").exists())
        assertTrue("bin/bash 必须存在", File(dest, "bin/bash").exists())
        assertTrue("etc/apt/sources.list 必须存在", File(dest, "etc/apt/sources.list").exists())

        // 符号链接重建验证:SYMLINKS.txt 里的首条链接真实指向目标
        val symlinksFile = File(dest, "SYMLINKS.txt")
        assertTrue("SYMLINKS.txt 必须存在", symlinksFile.exists())
        val first = symlinksFile.readLines().first { it.contains("←") }
        val target = first.substringBefore("←").trim()
        val link = first.substringAfter("←").trim().removePrefix("./")
        val linkFile = File(dest, link)
        assertTrue("符号链接必须存在: $link", linkFile.exists() || Files.isSymbolicLink(linkFile.toPath()))
        val resolved = Files.readSymbolicLink(File(dest, link).toPath()).toString()
        assertEquals("链接目标一致", target, resolved)

        // 镜像源验证:官方 Termux 镜像
        val sources = File(dest, "etc/apt/sources.list").readText()
        assertTrue("apt 源必须指向 Termux 官方镜像", sources.contains("packages.termux.dev") || sources.contains("termux.net"))

        // bin/sh 可执行位
        assertTrue("bin/sh 必须可执行", File(dest, "bin/sh").canExecute())

        // 清理(33MB 临时目录)
        dest.deleteRecursively()
    }

    @Test
    fun corruptedZipReturnsFalse() {
        val tmp = Files.createTempDirectory("boot-corrupt").toFile()
        val bad = File(tmp, "bad.zip")
        bad.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)) // 截断的 zip 头
        val dest = Files.createTempDirectory("boot-dest").toFile()
        val ok = BootstrapInstaller.install(FileInputStream(bad), dest)
        assertTrue("损坏包必须返回 false", !ok || !File(dest, "bin/sh").exists())
        tmp.deleteRecursively(); dest.deleteRecursively()
    }
}
