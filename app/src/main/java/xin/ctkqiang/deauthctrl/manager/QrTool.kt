package xin.ctkqiang.deauthctrl.manager

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 二维码工坊 —— 文本 / URL / WiFi 凭据 / 名片 生成二维码
 *
 * 用 zxing QRCodeWriter 本地渲染,矩阵 → Bitmap 一次成型。
 * WiFi 码遵循社区通用 `WIFI:` 语法,扫码即可直连。
 */
object QrTool {

    const val SIZE = 720

    fun wifiPayload(ssid: String, password: String, security: String): String {
        val esc = { s: String -> s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:") }
        return "WIFI:T:${security};S:${esc(ssid)};P:${esc(password)};;"
    }

    fun vcardPayload(name: String, phone: String, email: String): String =
        "BEGIN:VCARD\nVERSION:3.0\nFN:$name\nTEL:$phone\nEMAIL:$email\nEND:VCARD"

    /** 渲染二维码;内容为空时返回 null */
    fun render(text: String, sizePx: Int = SIZE): Bitmap? {
        if (text.isBlank()) return null
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                pixels[y * sizePx + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx) }
    }
}
