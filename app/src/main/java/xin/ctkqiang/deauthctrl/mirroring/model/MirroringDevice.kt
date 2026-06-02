package xin.ctkqiang.deauthctrl.mirroring.model

/** Represents a discovered screen-mirror host on the local network. */
data class MirroringDevice(
    val name: String,
    val hostAddress: String,
    val videoPort: Int,
    val inputPort: Int,
)

/** Serialized touch event sent from client to host over the input socket. */
data class TouchEvent(
    val action: Int,       // MotionEvent.ACTION_DOWN, _MOVE, _UP
    val x: Float,          // normalized 0..1 relative to video surface
    val y: Float,          // normalized 0..1 relative to video surface
    val pointerId: Int = 0,
)

/** Simple JSON-like wire format. See TouchInputSender / InputReceiver for parsing. */
object TouchProtocol {
    const val DELIMITER = "\n"

    fun encode(event: TouchEvent): String =
        "${event.action},${event.x},${event.y},${event.pointerId}"

    fun decode(raw: String): TouchEvent {
        val parts = raw.trim().split(",")
        return TouchEvent(
            action = parts[0].toInt(),
            x = parts[1].toFloat(),
            y = parts[2].toFloat(),
            pointerId = parts[3].toInt(),
        )
    }
}
