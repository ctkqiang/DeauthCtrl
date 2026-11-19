package xin.ctkqiang.deauthctrl

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes

/**
 * 非 UI 层(Manager / Service)字符串解析
 *
 * Manager 与 ViewModel 没有可组合上下文,不能调 stringResource。
 * Application 的 base context 已在 attachBaseContext 阶段按当前语言包装
 * (见 [LocaleManager.wrap]),这里解析出的文本跟随语言偏好。
 *
 * 语言切换后,已生成的瞬态文本(错误消息、状态行)保持旧语言,
 * 下一次赋值自然切到新语言 —— 对一次性消息而言是可接受的瞬态行为;
 * 周期性刷新的状态(枚举 label 等)建议存 resource id 由 UI 层 stringResource 解析,
 * 切换语言即刻生效。
 */
object L10n {

    @Volatile
    private var appContext: Context? = null

    /** 必须在 DeauthApp.onCreate 里调用一次 */
    fun init(app: Application) {
        appContext = app
    }

    /** 供单元测试手动注入 Mock / Stub Context */
    fun setContextForTesting(context: Context?) {
        appContext = context
    }

    /** 按当前界面语言解析字符串;支持 %1$s / %1$d 格式化参数 */
    fun get(@StringRes resId: Int, vararg args: Any): String {
        val ctx = appContext
        if (ctx != null) {
            return ctx.getString(resId, *args)
        }
        val format = testFallbackString(resId)
        return if (args.isEmpty()) format else String.format(format, *args)
    }

    private fun testFallbackString(@StringRes resId: Int): String = when (resId) {
        R.string.mgr_intel_label_apple_watch -> "Apple Watch"
        R.string.mgr_intel_note_apple_watch -> "Apple Watch"
        R.string.mgr_intel_label_galaxy_watch -> "Galaxy Watch"
        R.string.mgr_intel_note_galaxy_watch -> "Galaxy Watch"
        R.string.mgr_intel_label_mi_band -> "Mi Band"
        R.string.mgr_intel_note_mi_band -> "Mi Band"
        R.string.mgr_intel_note_miband -> "Mi Band"
        R.string.mgr_intel_label_amazfit -> "Amazfit"
        R.string.mgr_intel_note_amazfit -> "Amazfit"
        R.string.mgr_intel_label_huawei_watch -> "Huawei Watch"
        R.string.mgr_intel_note_huawei_watch -> "Huawei Watch"
        R.string.mgr_intel_label_airpods -> "Apple AirPods"
        R.string.mgr_intel_note_airpods -> "Apple AirPods"
        R.string.mgr_intel_label_galaxy_buds -> "Samsung Galaxy Buds"
        R.string.mgr_intel_note_galaxy_buds -> "Samsung Galaxy Buds"
        R.string.mgr_intel_label_redmi_buds -> "Redmi Buds"
        R.string.mgr_intel_note_redmi_buds -> "Redmi Buds"
        R.string.mgr_intel_label_sonoff -> "Sonoff Smart Switch"
        R.string.mgr_intel_note_sonoff -> "Sonoff"
        R.string.mgr_intel_label_tuya -> "Tuya Smart Device"
        R.string.mgr_intel_note_tuya -> "Tuya"
        R.string.mgr_intel_label_yeelight -> "Yeelight Smart Light"
        R.string.mgr_intel_note_yeelight -> "Yeelight"
        R.string.mgr_intel_label_aqara -> "Aqara Smart Device"
        R.string.mgr_intel_note_aqara -> "Aqara"
        R.string.mgr_intel_label_switchbot -> "SwitchBot Smart Device"
        R.string.mgr_intel_note_switchbot -> "SwitchBot"
        R.string.mgr_intel_label_tp_link -> "TP-Link Smart Home"
        R.string.mgr_intel_note_tp_link -> "TP-Link"
        R.string.mgr_intel_label_ring -> "Ring Smart Home"
        R.string.mgr_intel_note_ring -> "Ring"
        R.string.mgr_intel_label_tile -> "Tile Tracker"
        R.string.mgr_intel_note_tile -> "Tile"
        R.string.mgr_intel_label_apple_device -> "Apple device"
        R.string.mgr_intel_basis_flipper -> "Flipper Zero 广播特征"
        R.string.mgr_intel_basis_pwned -> "Deauther 默认 SSID 关键词 pwned"
        R.string.mgr_intel_basis_pineapple -> "WiFi Pineapple 默认 SSID 前缀"
        R.string.mgr_intel_basis_omg -> "O.MG 物理黑客线缆特征"
        R.string.mgr_intel_basis_espressif -> "Espressif 芯片厂商 ID 0x02E5"
        R.string.mgr_intel_basis_esp_name -> "ESP32/8266 命名格式"
        R.string.mgr_intel_basis_nordic -> "Nordic 芯片厂商 ID 0x0059"
        R.string.mgr_intel_basis_nordic_name -> "Nordic nRF 命名格式"
        R.string.mgr_intel_basis_arduino -> "Arduino 命名格式"
        R.string.mgr_intel_basis_raspberry -> "Raspberry Pi 命名格式"
        R.string.mgr_intel_basis_apple -> "Apple 蓝牙厂商 ID 0x004C"
        R.string.mgr_intel_basis_keyword -> "名称关键字 %1\$s (%2\$s)"
        else -> "res_$resId"
    }
}
