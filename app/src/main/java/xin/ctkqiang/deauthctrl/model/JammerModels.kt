package xin.ctkqiang.deauthctrl.model

data class JammerLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val module: String,
    val message: String,
)