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
    }
}
