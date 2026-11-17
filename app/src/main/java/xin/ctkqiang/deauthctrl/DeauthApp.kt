package xin.ctkqiang.deauthctrl

import android.app.Application

/**
 * DeauthCtrl Application 实例
 *
 * AndroidManifest.xml 中声明的自定义 Application 类。
 * 当前为轻量化实现（仅调用 super.onCreate()），保留用于：
 * 1. 未来全局依赖注入初始化（如 Koin/Hilt）
 * 2. 第三方 SDK 初始化（如 Crashlytics、Analytics）
 * 3. 全局异常处理器注册
 * 4. 应用级 SharedPreferences/DataStore 预加载
 */
class DeauthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 战术照片中继常开:同网段设备随时可投递,收到即发系统通知。
        // 必须走进程级单例 —— 相机页也订阅同一个实例,重复 new 会二次绑定 9997/10088
        // 并抛 BindException,导致页面订阅的 peers 永远为空。
        xin.ctkqiang.deauthctrl.manager.PhotoRelayHost.get(this)

        // 全局崩溃现场捕获:写入 filesDir/crash.log,便于定位雷达等模块的崩溃
        installCrashLogger()
    }

    /** 崩溃日志兜底 —— 原处理器照常执行,现场先落盘 */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val log = java.io.File(filesDir, "crash.log")
                // 先把堆栈完整写入 StringWriter,再落盘 —— 直接 append PrintWriter
                // 对象只会存下 "java.io.PrintWriter@xxxx" 这种引用文本,查不到现场
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                val text = buildString {
                    appendLine("=== DeauthCtrl crash @ ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} ===")
                    appendLine("thread: ${thread.name}")
                    appendLine(sw.toString())
                }
                log.appendText(text + "\n")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
