package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.L10n
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.PacketCaptureService
import xin.ctkqiang.deauthctrl.manager.PacketMonitorState

/**
 * VPN 授权查询结果
 *
 * [Ready] 已授权可直接 startService;[ConsentNeeded] 必须由 Activity 启动系统
 * 授权弹窗;[Failed] 查询本身失败(通常是被系统策略禁用)。
 */
sealed class VpnPrepareResult {
    object Ready : VpnPrepareResult()
    data class ConsentNeeded(val intent: Intent) : VpnPrepareResult()
    data class Failed(val message: String) : VpnPrepareResult()
}

/**
 * 本机流量监视 ViewModel
 *
 * 真正的状态与抓包线程都在 [PacketCaptureService](VpnService)里,本类只做两件事:
 * 1. 原样透传服务 companion 的 [PacketCaptureService.state](StateFlow);
 * 2. 封装 VpnService.prepare 授权查询与服务的启动 / 停止。
 *
 * ## 生命周期
 * 监视由前台服务承载,不随页面销毁而停止;onCleared 不调用 stopService,
 * 避免用户切到别的页面看包时抓包被意外终止。停止按钮与通知操作都能停。
 */
class PacketMonitorViewModel(application: Application) : AndroidViewModel(application) {

    val state: StateFlow<PacketMonitorState> = PacketCaptureService.state

    /** 启动 / 授权过程中的本地错误(与 state.error 分开,避免污染服务状态) */
    private val _uiError = MutableStateFlow<String?>(null)
    val uiError: StateFlow<String?> = _uiError

    /**
     * 查询 VPN 授权状态
     *
     * VpnService.prepare 返回非 null Intent 时表示尚未授权,必须由 Activity
     * 通过 rememberLauncherForActivityResult 启动它;返回 null 表示已授权。
     */
    fun prepare(context: Context): VpnPrepareResult = runCatching {
        val intent = VpnService.prepare(context.applicationContext)
        if (intent == null) VpnPrepareResult.Ready else VpnPrepareResult.ConsentNeeded(intent)
    }.getOrElse {
        VpnPrepareResult.Failed(L10n.get(R.string.vm_pkt_vpn_query_failed, it.javaClass.simpleName))
    }

    /** 启动前台抓包服务(调用前必须已获得 VPN 授权) */
    fun startService(context: Context) {
        if (PacketCaptureService.isRunning) return
        val app = context.applicationContext
        _uiError.value = null
        runCatching {
            ContextCompat.startForegroundService(
                app,
                Intent(app, PacketCaptureService::class.java),
            )
        }.onFailure {
            _uiError.value = L10n.get(
                R.string.vm_pkt_service_start_failed,
                it.javaClass.simpleName,
                it.message ?: "",
            )
        }
    }

    /**
     * 停止抓包服务
     *
     * 先在同一进程内让运行中的实例立即停机(唤醒读线程、关闭 TUN fd、
     * 拆除转发流、状态翻 false),再调用系统 stopService 回收服务。
     * 只调用 stopService 是不够的:阻塞在 TUN read 上的线程与 fd 会让
     * VPN 隧道迟迟不拆,用户看到的就是「停止无效」。
     */
    fun stopService(context: Context) {
        runCatching { PacketCaptureService.requestStop(context) }
            .onFailure { _uiError.value = L10n.get(R.string.vm_pkt_service_stop_failed, it.javaClass.simpleName) }
    }

    /** 清空页面上的包列表(不影响服务继续抓包与累计计数) */
    fun clearPackets() = PacketCaptureService.clearPackets()

    /** 清除本地错误提示 */
    fun clearUiError() {
        _uiError.value = null
    }
}
