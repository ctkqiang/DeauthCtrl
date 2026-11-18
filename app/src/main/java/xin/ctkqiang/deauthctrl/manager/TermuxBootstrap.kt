package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.terminal.TerminalSpec
import java.io.File

/** bootstrap 安装状态(终端页进度条数据源) */
data class BootstrapState(
    val installed: Boolean = false,
    val installing: Boolean = false,
    val progress: Float = 0f,   // 0..1
    val detail: String = "",
    val error: String? = null,
)

/**
 * Termux bootstrap 环境 —— 与 Termux 官方完全相同的自举方案
 *
 * 把 Termux 官方 bootstrap 包(GitHub termux/termux-packages releases,
 * Apache/BSD 等各包自持许可证)解压到应用私有目录 `files/usr`,
 * 重建 SYMLINKS.txt 声明的符号链接,设置 PREFIX/LD_LIBRARY_PATH 后
 * exec 其中的 /bin/sh —— 之后 apt(pkg)/pip/nano/python 等完整可用,
 * 包源保持 Termux 官方镜像(apt sources.list),与上游一致。
 *
 * 解压是幂等的:已安装直接返回;损坏(缺 bin/sh/bin/bash)或资产版本
 * 标记不匹配时自动重装。
 *
 * ## proot 运行要素(三件缺一不可,均为资产随包分发)
 * 1. `proot-aarch64` —— ptrace 版 proot,exec 前先 exec 独立 loader;
 * 2. `libexec/loader` —— proot 的 loader(Termux 以 PROOT_UNBUNDLE_LOADER
 *    方式构建,运行时从 /data/data/com.termux/files/usr/libexec/proot 找
 *    不到会直接 execve 失败),应用侧经 `PROOT_LOADER` 环境变量指定;
 * 3. 宿主 `/system` + `/apex` 绑定 —— bootstrap 内所有 ELF 的解释器是
 *    /system/bin/linker64(符号链接指向 /apex),不绑定则 execve 报
 *    "No such file or directory"(Android 10+ 还需 /linkerconfig 供
 *    链接器读取命名空间配置)。
 */
object TermuxBootstrap {

    fun prefix(context: Context): File = File(context.filesDir, "usr")

    private val _state = MutableStateFlow(BootstrapState())
    val state: StateFlow<BootstrapState> = _state

    /**
     * 基本安装校验:bin/sh(符号链接)与 bin/bash(实体)都必须存在。
     * 与 [assetMarker] 结合使用才能识别「资产已升级但旧 rootfs 未重装」。
     */
    fun isInstalled(context: Context): Boolean {
        val root = prefix(context)
        return File(root, "bin/sh").exists() && File(root, "bin/bash").exists()
    }

    /** 按 ABI 选择内置包(Termux 官方命名: aarch64/arm/x86_64/i686) */
    private fun assetAbi(): String {
        val abis = runCatching { android.os.Build.SUPPORTED_ABIS }.getOrDefault(arrayOf())
        return when {
            abis.any { it == "arm64-v8a" } -> "aarch64"
            abis.any { it == "armeabi-v7a" } -> "arm"
            abis.any { it == "x86_64" } -> "x86_64"
            abis.any { it == "x86" } -> "i686"
            else -> "aarch64"
        }
    }

    private fun assetName(): String = "termux/bootstrap-${assetAbi()}.zip"

    /** 资产指纹(名称+字节数)—— 写入 rootfs 标记文件,资产更新后自动触发重装 */
    private fun assetMarker(context: Context): String? = runCatching {
        context.assets.openFd(assetName()).use { fd -> "${assetName()}:${fd.length}" }
    }.getOrNull()

    fun refresh(context: Context) {
        _state.value = BootstrapState(installed = isInstalled(context))
    }

    /**
     * 确保 bootstrap 已就绪(幂等,阻塞调用,放 IO 线程)。
     *
     * @return 成功返回 true;失败返回 false 并写入 state.error
     */
    @Synchronized
    fun ensure(context: Context, onProgress: ((Float, String) -> Unit)? = null): Boolean {
        val root = prefix(context)
        val marker = assetMarker(context)
            ?: run {
                _state.value = BootstrapState(
                    installed = false,
                    error = "内置 bootstrap 缺失(${assetName()}) —— 请在联网状态下由终端页触发在线安装",
                )
                return false
            }
        // 标记匹配且关键文件齐全 → 已就绪(升级安装路径:顺带补装 proot/loader)
        val markerOk = runCatching {
            File(root, ".bootstrap-version").readText().trim() == marker
        }.getOrDefault(false)
        if (isInstalled(context) && markerOk) {
            ensureProot(context)
            _state.value = BootstrapState(installed = true)
            return true
        }
        _state.value = BootstrapState(installed = false, installing = true, progress = 0f, detail = "读取内置 bootstrap 包")
        onProgress?.invoke(0f, "读取内置 bootstrap 包")

        // 损坏残留/旧版本残留清掉重来
        if (root.exists()) root.deleteRecursively()
        root.mkdirs()

        try {
            val stream = context.assets.open(assetName())
                ?: run {
                    _state.value = BootstrapState(
                        installed = false,
                        error = "内置 bootstrap 缺失(${assetName()}) —— 请在联网状态下由终端页触发在线安装",
                    )
                    return false
                }
            // 解压前清空目标目录
            root.deleteRecursively()
            root.mkdirs()

            val ok = BootstrapInstaller.install(stream, root) { p, d ->
                _state.value = _state.value.copy(progress = p * 0.95f, detail = d)
                onProgress?.invoke(p * 0.95f, d)
            }
            if (!ok || !File(root, "bin/sh").exists() || !File(root, "bin/bash").exists()) {
                _state.value = BootstrapState(installed = false, error = "解压完成但缺少 bin/sh 或 bin/bash —— bootstrap 包损坏,请清除应用数据重试")
                return false
            }
            // 写入资产指纹:下次启动资产变更(如新增 bash)时自动触发重装
            File(root, ".bootstrap-version").writeText(marker)

            // proot 路径映射: bootstrap 二进制硬编码 /data/data/com.termux/files/usr
            // (31 字符,无法字符串 patch 到更长的本应用路径) —— ZeroTermux 同款方案:
            // 用 proot 把虚拟 com.termux 路径绑定到本应用目录
            val prootOk = ensureProot(context)
            _state.value = BootstrapState(installed = true, progress = 1f, detail = if (prootOk) "环境就绪" else "环境就绪(proot/loader 缺失,基础命令可用,apt 需 proot)")
            onProgress?.invoke(1f, "环境就绪")
            return true
        } catch (t: Throwable) {
            _state.value = BootstrapState(installed = false, error = "bootstrap 安装失败: ${t.message}")
            return false
        }
    }

    fun prootPath(context: Context): File = File(context.filesDir, "proot-aarch64")

    /** proot 依赖库目录(动态链接的 libtalloc 等) */
    fun prootLibDir(context: Context): File = File(context.filesDir, "lib")

    /** proot 独立 loader 的安装路径(与 Termux $PREFIX/libexec/proot/loader 布局一致) */
    fun loaderPath(context: Context): File = File(context.filesDir, "libexec/proot/loader")

    /** 解压 proot 二进制、依赖库与 loader(assets 内置)并置可执行 */
    private fun ensureProot(context: Context): Boolean {
        val dest = prootPath(context)
        val lib = prootLibDir(context)
        val loader = loaderPath(context)
        val prootOk = dest.exists() && dest.length() > 100_000
        val libOk = File(lib, "libtalloc.so.2").exists() && File(lib, "libandroid-shmem.so").exists()
        val loaderOk = loader.exists() && loader.length() > 10_000
        if (prootOk && libOk && loaderOk) return true
        return runCatching {
            if (!prootOk) {
                context.assets.open("termux/proot-aarch64").use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                dest.setExecutable(true, false)
            }
            if (!libOk) {
                lib.mkdirs()
                listOf("libtalloc.so.2", "libandroid-shmem.so").forEach { so ->
                    context.assets.open("termux/lib/" + so).use { input ->
                        File(lib, so).outputStream().use { input.copyTo(it) }
                    }
                }
            }
            if (!loaderOk) {
                // proot 以 PROOT_UNBUNDLE_LOADER 构建:exec 目标程序前先 exec
                // 该独立 loader(静态链接);缺失时 execve 以
                // "No such file or directory"/"Permission denied" 失败。
                loader.parentFile?.mkdirs()
                context.assets.open("termux/libexec/loader").use { input ->
                    loader.outputStream().use { input.copyTo(it) }
                }
                loader.setExecutable(true, false)
            }
            true
        }.getOrDefault(false)
    }

    /**
     * 生成 bootstrap 会话环境变量(PATH/LD_LIBRARY_PATH/PREFIX/HOME/TMPDIR)。
     * 与 Termux 官方环境对齐;proot 就绪时 shell 为 guest 内 /bin/bash,
     * 否则回退为直接 exec 的 PREFIX/bin/sh。
     */
    fun shellSpec(context: Context): TerminalSpec {
        // proot 虚拟前缀(Termux 硬编码路径,与官方一致;被映射到本应用目录)
        val vPrefix = "/data/data/com.termux/files/usr"
        val prefix = prefix(context)
        val home = File(prefix, "home").apply { mkdirs() }
        File(prefix, "tmp").apply { mkdirs() }
        File(context.filesDir, "proot-tmp").apply { mkdirs() }
        val vHome = "/data/data/com.termux/files/home"
        val proot = prootPath(context)
        val loader = loaderPath(context)
        if (proot.exists() && proot.canExecute() && loader.exists()) {
            // /data/user/0 是 /data/data 的 bind mount,proot 无法 canonicalize ——
            // 必须用 canonical 真实路径
            val realPrefix = prefix.canonicalPath
            val binds = mutableListOf(
                // -b host:guest —— host 必须是宿主真实路径,guest 是 rootfs 内路径
                "-b", realPrefix + ":/data/data/com.termux/files/usr",
                "-b", realPrefix + "/home:/data/data/com.termux/files/home",
                // 宿主系统绑定:bootstrap 内 ELF 的解释器是 /system/bin/linker64
                // (Android 10+ 上为指向 /apex 的符号链接),不绑定 execve 报
                // "No such file or directory";/linkerconfig 供链接器读命名空间
                // 配置(缺失只会回退默认配置并刷 Warning);/dev /proc /sys 是
                // 终端与 apt 的常规依赖。
                "-b", "/system",
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
            )
            if (File("/apex").exists()) binds += listOf("-b", "/apex")
            if (File("/linkerconfig").exists()) binds += listOf("-b", "/linkerconfig")
            return TerminalSpec(
                shellPath = proot.absolutePath,
                args = arrayOf(
                    "proot", "-0",
                    "-r", realPrefix,
                    *binds.toTypedArray(),
                    "-w", "/data/data/com.termux/files/home",
                    "/bin/bash", "--login",
                ),
                path = "$vPrefix/bin:$vPrefix/bin/applets:/system/bin:/system/xbin",
                env = arrayOf(
                    // proot 以 PROOT_UNBUNDLE_LOADER 构建,exec 前先 exec 该 loader
                    "PROOT_LOADER=${loader.absolutePath}",
                    "PATH=$vPrefix/bin:$vPrefix/bin/applets:/system/bin:/system/xbin",
                    "LD_LIBRARY_PATH=${prootLibDir(context).absolutePath}",
                    "PROOT_TMP_DIR=${File(context.filesDir, "proot-tmp").absolutePath}",
                    "PREFIX=$vPrefix",
                    "HOME=$vHome",
                    "TMPDIR=$vPrefix/tmp",
                    "TERM=xterm-256color",
                    "LANG=en_US.UTF-8",
                    "SHELL=$vPrefix/bin/sh",
                ),
                cwd = home.absolutePath,
                label = "termux",
            )
        }
        // proot 缺失回退:直接 exec 私有目录(基础命令可用;apt 路径硬编码会失败)
        val path = "${prefix.absolutePath}/bin:${prefix.absolutePath}/bin/applets:" +
            "/system/bin:/system/xbin:/apex/com.android.runtime/bin"
        return TerminalSpec(
            shellPath = File(prefix, "bin/sh").absolutePath,
            args = arrayOf("sh", "--login"),
            path = path,
            env = arrayOf(
                "PATH=$path",
                "LD_LIBRARY_PATH=${prefix.absolutePath}/lib",
                "PREFIX=${prefix.absolutePath}",
                "HOME=${home.absolutePath}",
                "TMPDIR=${prefix.absolutePath}/tmp",
                "TERM=xterm-256color",
                "LANG=en_US.UTF-8",
                "SHELL=${prefix.absolutePath}/bin/sh",
            ),
            cwd = home.absolutePath,
            label = "termux",
        )
    }
}
