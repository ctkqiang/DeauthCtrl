package xin.ctkqiang.deauthctrl.mirroring.host

import android.util.Log
import android.view.MotionEvent
import xin.ctkqiang.deauthctrl.mirroring.model.TouchEvent

/**
 * Stub touch injector — logs coordinates.
 *
 * Real implementation would either:
 * 1. Use [android.accessibilityservice.AccessibilityService] with
 *    [android.accessibilityservice.GestureDescription] for non-root injection.
 * 2. Execute `/system/bin/input tap x y` via shell if device has root or
 *    the app runs as system UID.
 * 3. Use the new [android.app.Instrumentation] or UiAutomation APIs.
 *
 * @property screenWidth  physical screen width in pixels (for denormalization)
 * @property screenHeight physical screen height in pixels
 */
class TouchInputInjector(
    private val screenWidth: Int,
    private val screenHeight: Int,
) {
    fun injectTouch(event: TouchEvent) {
        val px = (event.x * screenWidth).toInt()
        val py = (event.y * screenHeight).toInt()
        val actionStr = when (event.action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_UP -> "UP"
            else -> "UNKNOWN(${event.action})"
        }
        Log.d("TouchInjector", "[STUB] $actionStr @ ($px, $py) — inject via AccessibilityService or shell")
        // TODO: shell: Runtime.getRuntime().exec("input tap $px $py")
        // TODO: or dispatchGesture() through AccessibilityService
    }
}
