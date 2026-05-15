package xin.ctkqiang.deauthctrl.model

/**
 * BLE advertisement payload profiles for spoofing.
 * Each profile mimics a real device type to trigger unwanted behavior
 * in nearby Bluetooth stacks.
 */
enum class BlePayloadProfile(
    val displayName: String,
    val description: String,
) {
    APPLE_CONTINUITY(
        displayName = "苹果连续性",
        description = "伪造 AirDrop/Handoff/AirPods 近距离广播"
    ),
    MICROSOFT_SWIFT_PAIR(
        displayName = "微软 Swift Pair",
        description = "伪造 Windows 外设配对广播"
    ),
    GOOGLE_FAST_PAIR(
        displayName = "谷歌 Fast Pair",
        description = "伪造 Fast Pair 设备广播"
    ),
    SAMSUNG_SMARTTHINGS(
        displayName = "三星 SmartThings",
        description = "伪造三星配件广播"
    ),
    FLIPPER_STYLE(
        displayName = "Flipper 风格泛洪",
        description = "轮换多种伪造厂商广播模式"
    ),
    ALL(
        displayName = "全部协议 (轮换)",
        description = "依次轮换所有可用协议"
    );
}

/**
 * Represents a single BLE advertisement sent.
 */
data class BleAdvertLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val profile: BlePayloadProfile,
    val payloadHex: String,
    val success: Boolean,
)
