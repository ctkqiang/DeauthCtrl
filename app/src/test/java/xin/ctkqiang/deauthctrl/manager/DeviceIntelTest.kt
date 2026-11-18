package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DeviceIntel 指纹分类器单元测试 —— 纯 JVM,不依赖 Android。
 *
 * 覆盖:黑客工具 / 开发板 / 物联网 / 可穿戴 / 音频 / 手机 / 未知,
 * 以及大小写不敏感、厂商 ID 路径与分类优先级。
 */
class DeviceIntelTest {

    private fun hit(
        name: String?,
        mac: String? = null,
        uuids: List<String> = emptyList(),
        manufacturerId: Int? = null,
    ): IntelHit? = DeviceIntel.classify(name, mac, uuids, manufacturerId)

    /** 预期命中,失败时给出可读信息 */
    private fun requireHit(
        name: String?,
        mac: String? = null,
        uuids: List<String> = emptyList(),
        manufacturerId: Int? = null,
    ): IntelHit {
        val result = DeviceIntel.classify(name, mac, uuids, manufacturerId)
        assertNotNull("预期命中,实际为 null(name=$name, manufacturerId=$manufacturerId)", result)
        return result!!
    }

    @Test
    fun `Flipper Zero 广播名命中黑客工具`() {
        val h = requireHit("Flipper Zero")
        assertEquals(DeviceIntel.CATEGORY_HACKER, h.category)
        assertEquals("Flipper Zero", h.label)
        assertEquals(0.9f, h.confidence, 1e-4f)
        assertTrue(h.basis.contains("Flipper"))
    }

    @Test
    fun `pwned SSID 命中 Deauther`() {
        val exact = requireHit("pwned", "aa:bb:cc:dd:ee:ff")
        assertEquals(DeviceIntel.CATEGORY_HACKER, exact.category)
        assertEquals("Deauther Watch / DSTIKE", exact.label)
        assertEquals(0.95f, exact.confidence, 1e-4f)

        // 含 pwned 的变体同样命中
        val variant = requireHit("pwned_5G")
        assertEquals(DeviceIntel.CATEGORY_HACKER, variant.category)
    }

    @Test
    fun `Pineapple 与 Hak5 SSID 前缀命中 WiFi Pineapple`() {
        val pineapple = requireHit("Pineapple_5GHz")
        assertEquals(DeviceIntel.CATEGORY_HACKER, pineapple.category)
        assertEquals("WiFi Pineapple", pineapple.label)
        assertEquals(0.9f, pineapple.confidence, 1e-4f)

        val hak5 = requireHit("Hak5-Pineapple")
        assertEquals(DeviceIntel.CATEGORY_HACKER, hak5.category)
        assertEquals("WiFi Pineapple", hak5.label)
    }

    @Test
    fun `OMG 仅在 BLE 上下文命中且为低置信`() {
        val ble = requireHit(
            "OMG Cable",
            uuids = listOf("0000180a-0000-1000-8000-00805f9b34fb"),
        )
        assertEquals(DeviceIntel.CATEGORY_HACKER, ble.category)
        assertEquals("O.MG Cable", ble.label)
        assertEquals(0.6f, ble.confidence, 1e-4f)

        // 没有 BLE 广播数据(无服务 UUID / 厂商 ID)时不下结论
        assertNull(hit("OMG Cable"))
    }

    @Test
    fun `ESP32 名称与 Espressif 厂商 ID 命中开发板`() {
        val byName = requireHit("ESP32-1A2B")
        assertEquals(DeviceIntel.CATEGORY_DEV_BOARD, byName.category)
        assertEquals("ESP32 / ESP8266", byName.label)
        assertEquals(0.85f, byName.confidence, 1e-4f)

        val softAp = requireHit("ESP_A1B2C3")
        assertEquals(DeviceIntel.CATEGORY_DEV_BOARD, softAp.category)

        val byId = requireHit(null, manufacturerId = 0x02E5)
        assertEquals(DeviceIntel.CATEGORY_DEV_BOARD, byId.category)
        assertTrue(byId.basis.contains("0x02E5"))
    }

    @Test
    fun `nRF 名称与 Nordic 厂商 ID 命中开发板`() {
        val byName = requireHit("nRF52_DEV")
        assertEquals(DeviceIntel.CATEGORY_DEV_BOARD, byName.category)
        assertEquals("Nordic nRF", byName.label)
        assertEquals(0.85f, byName.confidence, 1e-4f)

        val byId = requireHit(null, manufacturerId = 0x0059)
        assertEquals(DeviceIntel.CATEGORY_DEV_BOARD, byId.category)
        assertTrue(byId.basis.contains("0x0059"))
    }

    @Test
    fun `Arduino 与 Raspberry Pi 分别命中开发板和电脑`() {
        val arduino = requireHit("Arduino Nano 33 BLE")
        assertEquals(DeviceIntel.CATEGORY_DEV_BOARD, arduino.category)
        assertEquals("Arduino", arduino.label)
        assertEquals(0.8f, arduino.confidence, 1e-4f)

        val pi = requireHit("Raspberry Pi 4B")
        assertEquals(DeviceIntel.CATEGORY_COMPUTER, pi.category)
        assertEquals("Raspberry Pi", pi.label)
        assertEquals(0.9f, pi.confidence, 1e-4f)
    }

    @Test
    fun `Apple Watch 命中可穿戴且优先于 Apple 厂商 ID`() {
        val h = requireHit("Apple Watch Series 9", manufacturerId = 0x004C)
        assertEquals(DeviceIntel.CATEGORY_WEARABLE, h.category)
        assertEquals("Apple Watch", h.label)
        assertEquals(0.8f, h.confidence, 1e-4f)
    }

    @Test
    fun `AirPods 与 Galaxy Buds 命中音频`() {
        val airpods = requireHit("AirPods Pro", manufacturerId = 0x004C)
        assertEquals(DeviceIntel.CATEGORY_AUDIO, airpods.category)
        assertEquals("Apple AirPods", airpods.label)

        val buds = requireHit("Galaxy Buds2 Pro")
        assertEquals(DeviceIntel.CATEGORY_AUDIO, buds.category)
        assertEquals("Samsung Galaxy Buds", buds.label)
    }

    @Test
    fun `仅凭 Apple 厂商 ID 命中手机类`() {
        val h = requireHit("Unknown-Device", manufacturerId = 0x004C)
        assertEquals(DeviceIntel.CATEGORY_PHONE, h.category)
        assertEquals("苹果设备", h.label)
        assertEquals(0.7f, h.confidence, 1e-4f)
    }

    @Test
    fun `Sonoff 与 Ring 等命中物联网`() {
        val sonoff = requireHit("Sonoff-1234")
        assertEquals(DeviceIntel.CATEGORY_IOT, sonoff.category)
        assertEquals(0.75f, sonoff.confidence, 1e-4f)

        val ring = requireHit("Ring-Doorbell-01")
        assertEquals(DeviceIntel.CATEGORY_IOT, ring.category)

        // Ring / Tile 按整词匹配:普通单词不应误报
        assertNull(hit("Springfield_5G"))
    }

    @Test
    fun `未知设备返回 null`() {
        assertNull(hit("Living Room Lamp"))
        assertNull(hit(null))
        assertNull(hit(""))
        assertNull(hit("   "))
    }

    @Test
    fun `指纹匹配大小写不敏感`() {
        val flipper = requireHit("fLiPpEr ZeRo")
        assertEquals(DeviceIntel.CATEGORY_HACKER, flipper.category)
        assertEquals("Flipper Zero", flipper.label)

        val esp = requireHit("eSp8266")
        assertEquals(DeviceIntel.CATEGORY_DEV_BOARD, esp.category)

        val pwned = requireHit("PWNED")
        assertEquals(DeviceIntel.CATEGORY_HACKER, pwned.category)

        val watch = requireHit("apple WATCH")
        assertEquals(DeviceIntel.CATEGORY_WEARABLE, watch.category)

        val sonoff = requireHit("SONOFF-TH16")
        assertEquals(DeviceIntel.CATEGORY_IOT, sonoff.category)
    }

    @Test
    fun `分类优先级为黑客工具高于开发板高于物联网`() {
        val hacker = requireHit("Flipper ESP32")
        assertEquals(DeviceIntel.CATEGORY_HACKER, hacker.category)
        assertEquals("Flipper Zero", hacker.label)

        val devBoard = requireHit("ESP32 Sonoff")
        assertEquals(DeviceIntel.CATEGORY_DEV_BOARD, devBoard.category)
        assertEquals("ESP32 / ESP8266", devBoard.label)
    }
}
