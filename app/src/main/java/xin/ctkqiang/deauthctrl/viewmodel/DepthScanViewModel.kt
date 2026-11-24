package xin.ctkqiang.deauthctrl.viewmodel

import android.app.Activity
import android.app.Application
import android.opengl.GLSurfaceView
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import xin.ctkqiang.deauthctrl.manager.DepthCapability
import xin.ctkqiang.deauthctrl.manager.DepthMath
import xin.ctkqiang.deauthctrl.manager.DepthScanManager

/**
 * 深度扫视 ViewModel —— 能力状态 / 距离读数 / 热力图开关 / 前后摄。
 * Session 生命周期由屏幕 DisposableEffect 驱动,GL 相关操作
 * 经 GLSurfaceView.queueEvent 排队到 GL 线程。
 */
class DepthScanViewModel(app: Application) : AndroidViewModel(app) {

    data class State(
        val capability: DepthCapability = DepthCapability.CHECKING,
        val distanceText: String = "--.- m",
        val tapText: String? = null,
        val heatmapOn: Boolean = true,
        val heatmap: IntArray? = null,
        val facingFront: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    var manager: DepthScanManager? = null
        private set

    fun attach(activity: Activity) {
        if (manager == null) manager = DepthScanManager(activity)
    }

    /** 静态能力检查(入口时调用;深度模式支持在 GL 就绪后由渲染器回报) */
    fun check() {
        val cap = manager?.checkArAvailability(getApplication()) ?: DepthCapability.ERROR
        _state.update { it.copy(capability = cap) }
    }

    fun requestInstallAr() {
        runCatching { manager?.requestInstall() }
        check()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) check()
        else _state.update { it.copy(capability = DepthCapability.PERMISSION_REQUIRED) }
    }

    /** GL 就绪回报(会话创建/深度模式探测结果) */
    fun onGlReady(capability: DepthCapability) {
        _state.update { it.copy(capability = capability) }
    }

    /** 渲染线程每帧回调 */
    fun onDepthFrame(result: DepthScanManager.DepthFrameResult?) {
        val data = result?.data
        val tap = result?.tapMm
        _state.update {
            it.copy(
                capability = manager?.capability ?: it.capability,
                distanceText = data?.centerMm?.let(DepthMath::formatMeters) ?: "--.- m",
                heatmap = data?.heatmap ?: it.heatmap,
                tapText = tap?.let(DepthMath::formatMeters),
            )
        }
    }

    /** 屏幕点击 → 待采样坐标(VIEW_NORMALIZED) */
    fun requestTap(nx: Float, ny: Float) {
        manager?.pendingTap = nx to ny
    }

    fun toggleHeatmap() {
        _state.update { it.copy(heatmapOn = !it.heatmapOn) }
    }

    /** 前后摄切换:GL 线程执行,失败回退(前置深度部分设备不可用) */
    fun toggleFacing(glView: GLSurfaceView?, renderer: xin.ctkqiang.deauthctrl.manager.DepthRenderer?) {
        val target = !_state.value.facingFront
        glView?.queueEvent { renderer?.setFacingOnGlThread(target) }
        _state.update { it.copy(facingFront = target) }
    }

    fun onResume(glView: GLSurfaceView?) {
        manager?.resume()
        glView?.onResume()
    }

    fun onPause(glView: GLSurfaceView?) {
        manager?.pause()
        glView?.onPause()
    }

    fun dispose() {
        manager?.close()
    }
}
