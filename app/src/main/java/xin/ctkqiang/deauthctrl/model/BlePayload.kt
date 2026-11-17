package xin.ctkqiang.deauthctrl.model

/**
 * BLE 广播载荷协议(参照 Flipper Zero BLE Spam 的公开载荷结构)
 *
 * 每个协议对应一类真实设备的广播模板。厂商 ID 采用蓝牙 SIG 公开
 * 分配表(Apple 0x004C / Microsoft 0x0006 / Google 0x00E0 / Samsung 0x0075)。
 * 载荷结构来自 Flipper Zero 固件公开源码,用于授权安全研究与协议学习。
 */
enum class BlePayloadProfile(
    val displayName: String,
    val description: String,
) {
    APPLE_CONTINUITY(
        displayName = "苹果连续性",
        description = "伪造 AirDrop/Handoff/AirPods 近距离广播"
    ),
    AIRPODS(
        displayName = "AirPods 弹窗",
        description = "伪造 AirPods 配对弹窗广播"
    ),
    IOS_LOCK_SCREEN(
        displayName = "iOS 锁屏弹窗",
        description = "AppleTV 名称前缀伪装,附近 iPhone 弹设备窗口"
    ),
    APPLE_ACTION(
        displayName = "苹果动作消息",
        description = "伪装 Apple 快捷动作,附近 iPhone 弹通知横幅"
    ),
    CRASH_SAFARI(
        displayName = "Safari 历史崩溃包",
        description = "历史 iOS Safari 崩溃广播(现代 iOS 已修复,仅协议研究)"
    ),
    ANDROID_DEVICE(
        displayName = "Android 设备伪装",
        description = "Fast Pair 变体,伪装安卓设备广播"
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
    GENERIC_RANDOM(
        displayName = "通用随机压力",
        description = "测试厂商 ID 下的全随机载荷(协议压力测试)"
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

data class BleAdvertLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val profile: BlePayloadProfile,
    val payloadHex: String,
    val payloadBytes: ByteArray = byteArrayOf(),
    val success: Boolean,
    val index: Int = 0,
    val txPowerLevel: String = "HIGH",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleAdvertLogEntry) return false
        return timestamp == other.timestamp && index == other.index
    }

    override fun hashCode(): Int = 31 * timestamp.hashCode() + index
}
