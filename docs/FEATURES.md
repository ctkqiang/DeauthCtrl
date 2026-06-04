# 功能目录

## 1. BLE 泛洪攻击

**屏幕:** `BleDetailScreen`
**核心文件:** `BleSpamManager.kt`, `BleSpamViewModel.kt`

BLE 广告协议数据包泛洪。以可配置的载荷模板高速发送 BLE 广播包。

```
Manager API:
  startSpam() / stopSpam()
  getProfiles() / getCurrentProfile() / setProfile(profile)
  getCurrentInterval() / setInterval(ms: Long)

状态:
  isRunning: Boolean
  log: List<LogEntry>  (时间戳、载荷模板、成功标记、序号)
  error: String?
  currentProfile、currentInterval

参数:
  - 载荷模板：从预设列表中选择
  - 发包间隔：20-100ms
```

## 2. WiFi 干扰

**屏幕:** `WifiDetailScreen`
**核心文件:** `WifiDisruptManager.kt`, `WifiDisruptViewModel.kt`

邪恶双子信标泛洪 -- 创建虚假 WiFi 网络以干扰扫描。

```
Manager API:
  scanNetworks()  -> 返回附近 SSID 列表
  selectTarget(network)
  startFlood() / stopFlood()
  setDuration(ms: Long)

状态:
  isFlooding: Boolean
  networks: List<WifiNetwork>
  selectedTarget: WifiNetwork?
  duration: Long
```

## 3. 蓝牙压制

**屏幕:** `BtJamDetailScreen`
**核心文件:** `BluetoothJammerManager.kt`, `BluetoothJammerViewModel.kt`

全频段蓝牙设备压制。

```
Manager API:
  start() / stop()

状态:
  isRunning: Boolean
```

## 4. WiFi 压制

**屏幕:** `WifiJamDetailScreen`
**核心文件:** `WifiJammerManager.kt`, `WifiJammerViewModel.kt`

自动扫描并压制附近所有 SSID。

```
Manager API:
  start() / stop()

状态:
  isRunning: Boolean
```

## 5. Web 服务

**屏幕:** `WebServerDetailScreen`
**核心文件:** `WebServerManager.kt`, `WebServerViewModel.kt`

轻量级 HTTP 服务器，运行在设备热点上。托管已连接的客户端可访问的 HTML 页面。

```
Manager API:
  startServer() / stopServer()

状态:
  isRunning: Boolean
  serverAddress: String  (IP:端口)
  htmlContent: String?   (上传的 HTML)
```

## 6. ARP 扫描

**屏幕:** `ArpDetailScreen`
**核心文件:** `ArpScanManager.kt`, `ArpScanViewModel.kt`

通过 ARP 表解析发现局域网设备。

```
Manager API:
  scan()  -> 触发单次扫描

状态:
  isRunning: Boolean
  devices: List<ArpDevice>
```

## 7. HTTP 客户端

**屏幕:** `HttpClientScreen`
**核心文件:** `HttpClientManager.kt`, `HttpClientViewModel.kt`

类似 curl 的 HTTP 客户端，支持 GET / POST / PUT / DELETE。

```
Manager API:
  execute(method, url, headers, body)

状态:
  isRunning: Boolean
  response: HttpResponse?
  requestHistory: List<HttpRequest>
```

## 8. Ping 泛洪

**屏幕:** `PingScreen`
**核心文件:** `PingManager.kt`, `PingViewModel.kt`

持续 ICMP ping 用于压力测试。

```
Manager API:
  start() / stop()
  setTarget(host: String)

状态:
  isRunning: Boolean
  target: String
  stats: PingStats  (发送数、接收数、最小/平均/最大延迟)
```

## 9. BLE 扫描器

**屏幕:** `BleScannerScreen`
**核心文件:** `BleScannerManager.kt`, `BleScannerViewModel.kt`

低功耗蓝牙嗅探器，带实时 RSSI 图表。

```
Manager API:
  start() / stop()

状态:
  isRunning: Boolean
  devices: List<BleDevice>  (名称、地址、RSSI、制造商)
```

## 10. 端口扫描器

**屏幕:** `PortScannerScreen`
**核心文件:** `PortScannerManager.kt`, `PortScannerViewModel.kt`

类似 nmap 的 TCP 端口扫描器，带服务识别功能。

```
Manager API:
  start(target, portRange) / stop()

状态:
  isRunning: Boolean
  target: String
  results: List<PortResult>  (端口号、状态、服务名、Banner)
```

## 11. 对讲机

**屏幕:** `WalkieTalkieScreen`
**核心文件:** `WalkieTalkieManager.kt`, `WalkieTalkieViewModel.kt`, `WalkieTalkieService.kt`

WiFi 局域网按讲通话（PTT）对讲机，基于 UDP 协议。

```
协议:
  设备发现:  UDP 9999 广播 "DEAUTHCTRL_PTT:设备名:IP地址:是否说话"
  音频传输:  UDP 10000 单播 PCM 8000Hz 16位 单声道
  帧大小:    320 字节 (20ms 音频片段)

Manager API:
  start(): Boolean      -> 打开 Socket + 启动发现服务
  startTalk()           -> PTT 按下 (启动 AudioRecord 录音)
  stopTalk()            -> PTT 释放 (停止 AudioRecord)
  stop()                -> 关闭所有 Socket

状态:
  isRunning: Boolean     -> 对讲机服务是否已启动
  isTalking: Boolean     -> PTT 按钮是否被按住
  isLive: Boolean        -> 免提模式是否激活（10 秒自动发送）
  liveCountdown: Int     -> 免提模式倒计时秒数
  peers: List<WalkiePeer> -> 发现的局域网对讲设备
  error: String?         -> 错误信息

UI 特性:
  - PTT 按钮：pointerInput + detectTapGestures(onPress)
    按下 -> startTalk(), tryAwaitRelease(), 释放 -> stopTalk()
  - LIVE 按钮：toggleLive() -> 10 秒自动说话
  - 说话时按钮呼吸脉冲动画（0.4-0.9 alpha，800ms 周期）
  - 设备列表交错淡入动画（间隔 80ms）
  - 说话中的设备红色高亮显示
```

## 12. 设备雷达

**屏幕:** `RadarScreen`
**核心文件:** `RadarManager.kt`, `RadarViewModel.kt`

军用 AN/APG 风格 PPI 雷达显示器，在极坐标显示上以红色点（WiFi AP）和白色点（蓝牙设备）展示已发现设备，带磷光拖尾效果。

```
扫描机制:
  WiFi:  WifiManager.startScan() -> SCAN_RESULTS_AVAILABLE_ACTION 广播
  BLE:   BluetoothAdapter.startDiscovery() -> ACTION_FOUND 广播
         事件驱动循环：启动 -> 等待 DISCOVERY_FINISHED -> 重新启动

数据结构:
  RadarTarget: id、name、type("wifi"/"bt")、rssi、distanceM、angleDeg、channel、vendor
  distanceM:  基于 RSSI 的对数距离路径损耗模型 (n=2.5, txPower=-40/-59dBm)
  angleDeg:   基于 MAC 地址哈希的伪方位角（每次扫描保持稳定）

UI 特性:
  - 8 秒扫描线动画，带磷光拖尾效果（9 段渐变 alpha）
  - 同心圆距离环（25/50/75/100%）
  - 每 10 度一个罗盘方位标记
  - 目标点三层光晕渲染（alpha 0.15 / 0.4 / 0.9）
  - 轨迹列表显示：类型、名称、RSSI、距离、方位角
  - 每次完整旋转触发触觉反馈
  - 终端头部闪烁光标
```

## 13. 文件快传

**屏幕:** `FileTransferScreen`
**核心文件:** `FileTransferManager.kt`, `FileTransferViewModel.kt`

同一 WiFi 局域网文件传输，基于 UDP 设备发现 + TCP 直连传输。

```
协议:
  设备发现: UDP 广播 (设备名 + IP)
  文件传输: TCP 直连到对等设备

Manager API:
  start() / stop()
  sendFile(uri, targetIp)

状态:
  isRunning: Boolean
  peers: List<Peer>
  localIp: String
  received: File?
  sentCount: Int
  error: String?
```

## 14. 屏幕镜像

**屏幕:** `DashboardScreen`, `RemoteViewerScreen`
**核心文件:** `ScreenCaptureManager.kt`, `VideoStreamServer.kt`, `InputReceiver.kt`, `MirroringHostService.kt`, `VideoStreamClient.kt`, `MediaCodecDecoder.kt`, `TouchInputSender.kt`, `NsdDiscoveryManager.kt`

局域网 Android 到 Android 屏幕镜像与远程控制（类似 scrcpy，但手机对手机）。

```
架构:
  主机端:   MediaProjection -> VirtualDisplay -> H.264 编码器 -> TCP:10086
            TouchInputInjector <- TCP:10087 <- 客户端
  客户端:   TCP:10086 -> H.264 解码器 -> TextureView Surface
            触摸覆盖层 -> TCP:10087 -> 主机端

主机端两步启动 (Android 14+ 兼容):
  1. ACTION_PREPARE        -> 进入前台模式 + 启动 TCP 服务器
  2. ACTION_START_CAPTURE  -> getMediaProjection() + 开始编码

视频管道:
  编码器:   H.264 Baseline, 4Mbps CBR, 30fps, I-frame=2s, 无 B 帧
  CSD:      onOutputFormatChanged 提取 SPS/PPS ->
            0x00000001 起始码 -> CSD_MARKER(0x01) 帧
  协议:     [4B 长度][1B 标记(0x01=CSD, 0x00=普通帧)][NAL 数据]

设备发现:
  NSD/mDNS: _screenmirror._tcp. + TXT 记录 (video_port, input_port)

UI 特性:
  - 进入 RemoteViewerScreen 时自动切换横屏
  - 全屏沉浸模式（隐藏系统状态栏和导航栏）
  - 浮动覆盖层 3 秒后自动隐藏
  - 单击切换覆盖层、拖动控制主机、双击断开连接
  - TextureView 填满全屏，自动缩放适配
  - 连接状态带脉冲动画显示
  - 设备列表交错淡入动画（间隔 60ms）
  - 主机端活跃指示器绿色脉冲动画（1s 周期）
```

## 15. Reverse Shell

**屏幕:** `RevShellScreen`
**核心文件:** `RevShellManager.kt`, `RevShellViewModel.kt`

`nc -lvnp` 风格的 TCP 监听器，接收反弹 Shell 连接。

```
Manager API:
  start(port) / stop()

状态:
  isRunning: Boolean
  port: Int
  connections: List<ShellConnection>
```

## 16. Payload 生成器

**屏幕:** `PayloadScreen`
**核心文件:** `PayloadGenManager.kt`, `PayloadViewModel.kt`

21 种反弹 Shell Payload 一键生成，涵盖 Bash、Python、PHP、Netcat、PowerShell 等。

```
Manager API:
  generate(type, ip, port) -> String

状态:
  selectedType: String
  ip: String
  port: Int
  generatedPayload: String?
```

## 17. 目录爆破

**屏幕:** `DirBruteScreen`
**核心文件:** `DirBruteManager.kt`, `DirBruteViewModel.kt`

HTTP 目录枚举工具，类似 gobuster 风格。

```
Manager API:
  start(target, wordlist) / stop()

状态:
  isRunning: Boolean
  target: String
  results: List<DirResult>  (路径、状态码、大小)
```

## 18. CVE 搜索

**屏幕:** `CVEScreen`
**核心文件:** `CVESearchManager.kt`, `CVESearchViewModel.kt`

CVE / Exploit-DB 漏洞数据库查询。

```
Manager API:
  search(query: String)

状态:
  isRunning: Boolean
  query: String
  results: List<CVEResult>
  status: String
```

## 19. 加密保险箱

**屏幕:** `SecureVaultScreen`
**核心文件:** `SecureMediaManager.kt`, `SecureMediaViewModel.kt`

AES-256 加密拍摄/录音/导入文件，需要密码才能查看解密内容。

```
Manager API:
  startRecording() / stopRecording()
  createPhotoIntent() -> Intent?
  importFile(uri)
  decrypt(passphrase)

状态:
  isRecording: Boolean
  entries: List<MediaEntry>
  decryptedFile: File?
  passphrase: String
  error: String?
```
