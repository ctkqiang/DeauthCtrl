package xin.ctkqiang.deauthctrl.model

import androidx.annotation.StringRes
import xin.ctkqiang.deauthctrl.R

/**
 * BLE 广播载荷协议(参照 Flipper Zero BLE Spam 的公开载荷结构)
 *
 * 每个协议对应一类真实设备的广播模板。厂商 ID 采用蓝牙 SIG 公开
 * 分配表(Apple 0x004C / Microsoft 0x0006 / Google 0x00E0 / Samsung 0x0075)。
 * 载荷结构来自 Flipper Zero 固件公开源码,用于授权安全研究与协议学习。
 *
 * 显示名与描述为资源 id(中英双语):UI 用 stringResource,非 UI 层用 L10n.get。
 */
enum class BlePayloadProfile(
    @StringRes val displayNameRes: Int,
    @StringRes val descriptionRes: Int,
) {
    APPLE_CONTINUITY(
        displayNameRes = R.string.payload_name_apple_continuity,
        descriptionRes = R.string.payload_desc_apple_continuity,
    ),
    AIRPODS(
        displayNameRes = R.string.payload_name_airpods,
        descriptionRes = R.string.payload_desc_airpods,
    ),
    IOS_LOCK_SCREEN(
        displayNameRes = R.string.payload_name_ios_lock_screen,
        descriptionRes = R.string.payload_desc_ios_lock_screen,
    ),
    APPLE_ACTION(
        displayNameRes = R.string.payload_name_apple_action,
        descriptionRes = R.string.payload_desc_apple_action,
    ),
    CRASH_SAFARI(
        displayNameRes = R.string.payload_name_crash_safari,
        descriptionRes = R.string.payload_desc_crash_safari,
    ),
    ANDROID_DEVICE(
        displayNameRes = R.string.payload_name_android_device,
        descriptionRes = R.string.payload_desc_android_device,
    ),
    MICROSOFT_SWIFT_PAIR(
        displayNameRes = R.string.payload_name_swift_pair,
        descriptionRes = R.string.payload_desc_swift_pair,
    ),
    GOOGLE_FAST_PAIR(
        displayNameRes = R.string.payload_name_fast_pair,
        descriptionRes = R.string.payload_desc_fast_pair,
    ),
    SAMSUNG_SMARTTHINGS(
        displayNameRes = R.string.payload_name_smartthings,
        descriptionRes = R.string.payload_desc_smartthings,
    ),
    GENERIC_RANDOM(
        displayNameRes = R.string.payload_name_generic_random,
        descriptionRes = R.string.payload_desc_generic_random,
    ),
    FLIPPER_STYLE(
        displayNameRes = R.string.payload_name_flipper_style,
        descriptionRes = R.string.payload_desc_flipper_style,
    ),
    ALL(
        displayNameRes = R.string.payload_name_all_rotate,
        descriptionRes = R.string.payload_desc_all_rotate,
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
