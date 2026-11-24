package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 工具内核单测 —— nmap 服务指纹 / RSSI 扰动感知 / ONVIF 发现解析。
 * 全部是纯逻辑核(无 IO),测试即「工具可用性」的客观证明。
 */
class ToolCoresTest {

    // ─── ServiceProbes:nmap service-probes 移植指纹 ─────────────────────────

    @Test
    fun probes_sshVersion() {
        assertEquals("OpenSSH 8.9p1", ServiceProbes.match(22, "SSH-2.0-OpenSSH_8.9p1 Ubuntu-3ubuntu0.6"))
    }

    @Test
    fun probes_httpServerHeader() {
        assertEquals(
            "nginx/1.24.0",
            ServiceProbes.match(80, "HTTP/1.1 200 OK\r\nServer: nginx/1.24.0\r\n"),
        )
    }

    @Test
    fun probes_mysqlHandshake() {
        val handshake = buildString {
            append(10.toChar()) // 握手包首字节 0x0A
            append("8.0.36\u0000")
        }
        assertTrue(ServiceProbes.match(3306, handshake).startsWith("mysql"))
    }

    @Test
    fun probes_unknownBannerFallsBackToPortName() {
        assertEquals("", ServiceProbes.match(65530, "whatever"))
        assertEquals("redis", ServiceProbes.match(6379, "PING")) // 端口名兜底
    }

    // ─── RfSenseEngine:RSSI 扰动活动指数 ────────────────────────────────────

    private fun target(id: String, rssi: Int) = RadarTarget(
        id = id, name = "AP-$id", type = "wifi", rssi = rssi,
        distanceM = 0f, angleDeg = 0f,
    )

    @Test
    fun rfsense_calmVsMotion() {
        val engine = RfSenseEngine()
        var state = engine.ingest(emptyList(), 0L)
        assertEquals("calm", state.level)
        assertEquals(0f, state.motionIndex, 0.001f)

        // 平稳序列:采样 12 次(每次间隔 1s),RSSI 恒定 → σ≈0 → calm
        var now = 0L
        repeat(12) {
            now += 1000L
            state = engine.ingest(listOf(target("a", -50)), now)
        }
        assertEquals("calm", state.level)
        assertTrue(state.motionIndex < 0.15f)

        // 剧烈抖动序列:±15dB 摆幅 → σ 大 → strong
        repeat(20) { i ->
            now += 1000L
            state = engine.ingest(listOf(target("a", -50 + (if (i % 2 == 0) 15 else -15))), now)
        }
        assertEquals("strong", state.level)
        assertTrue(state.motionIndex >= 0.60f)
    }

    @Test
    fun rfsense_windowExpiresOldSamples() {
        val engine = RfSenseEngine()
        var now = 0L
        repeat(12) { now += 1000L; engine.ingest(listOf(target("a", -50)), now) }
        // 31 秒后旧样本全部滑出 30s 窗口 → 样本不足 8 → 无感知输出
        val state = engine.ingest(listOf(target("a", -50)), now + 31_000L)
        assertEquals(0, state.samples)
    }

    // ─── OnvifCamManager:Probe 构造与 ProbeMatch 解析 ────────────────────────

    @Test
    fun onvif_probeXmlCarriesWsDiscoveryProbe() {
        val m = OnvifCamManager()
        val xml = m.buildProbeXml()
        assertTrue(xml.contains("http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe"))
        assertTrue(xml.contains("dn:NetworkVideoTransmitter"))
        assertTrue(xml.contains("<w:MessageID>uuid:"))
    }

    @Test
    fun onvif_parseProbeMatchStandardPrefix() {
        val m = OnvifCamManager()
        val out = HashMap<String, OnvifCamManager.OnvifCamera>()
        val xml = """
            <d:ProbeMatch>
              <d:XAddrs>http://192.168.1.64:8899/onvif/device_service</d:XAddrs>
              <d:Scopes>onvif://www.onvif.org/name/HIKVISION</d:Scopes>
            </d:ProbeMatch>
        """.trimIndent()
        m.parseProbeMatches(xml, "192.168.1.64", out)
        assertEquals(1, out.size)
        val cam = out.values.first()
        assertEquals("http://192.168.1.64:8899/onvif/device_service", cam.xaddr)
        assertEquals(8899, cam.port)
        assertTrue(cam.scopes.contains("HIKVISION"))
    }

    @Test
    fun onvif_parseProbeMatchAlternatePrefix() {
        // 个别厂商用 wsd: 前缀或无前缀 —— 解析器必须都能认
        val m = OnvifCamManager()
        val out = HashMap<String, OnvifCamManager.OnvifCamera>()
        val xml = """
            <wsd:ProbeMatch>
              <wsd:XAddrs>http://10.0.0.9/onvif/device_service</wsd:XAddrs>
            </wsd:ProbeMatch>
        """.trimIndent()
        m.parseProbeMatches(xml, "10.0.0.9", out)
        assertEquals(1, out.size)
        assertEquals("http://10.0.0.9/onvif/device_service", out.values.first().xaddr)
    }

    @Test
    fun onvif_parseIgnoresGarbage() {
        val m = OnvifCamManager()
        val out = HashMap<String, OnvifCamManager.OnvifCamera>()
        m.parseProbeMatches("<d:Bye/>", "192.168.1.1", out) // 无 XAddrs
        assertEquals(0, out.size)
    }

    // ─── OnvifCamManager:RTSP banner 厂商指纹 ────────────────────────────────

    @Test
    fun onvif_fingerprintKnownVendors() {
        val m = OnvifCamManager()
        assertEquals("Hikvision", m.fingerprintBanner("RTSP/1.0 200 OK\r\nServer: Hikvision-Webs"))
        assertEquals("Dahua", m.fingerprintBanner("RTSP/1.0 401 Unauthorized\r\nServer: Dahua Rtsp Server"))
        assertEquals("Axis", m.fingerprintBanner("RTSP/1.0 200 OK\r\nCSeq: 1\r\nServer: AXIS Streaming"))
        assertNull(m.fingerprintBanner("RTSP/1.0 200 OK\r\nServer: unknown-generic"))
    }
}
