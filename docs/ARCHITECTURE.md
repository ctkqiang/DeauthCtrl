# 架构详解

## 分层结构

```
┌─────────────────────────────────────────────────────────────┐
│  UI 层 (Jetpack Compose)                                      │
│  MainScreen.kt -> DashboardScreen / RemoteViewerScreen        │
│  TerminalKit.kt -> 可复用的终端风格组件                         │
│  HackerEffects.kt -> GlitchText, ScanlineOverlay, BootSequence │
├─────────────────────────────────────────────────────────────┤
│  ViewModel 层 (AndroidViewModel)                              │
│  StateFlow<State> -> collectAsState() -> Compose 重组         │
│  Dispatchers.IO 用于后台任务，Dispatchers.Main 用于状态更新    │
├─────────────────────────────────────────────────────────────┤
│  Manager 层 (业务逻辑)                                         │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ contract/IRadarManager, IWalkieTalkieManager...      │    │
│  │ RadarManager, WalkieTalkieManager, ...                │    │
│  └──────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────┤
│  Android 平台 API                                             │
│  WifiManager, BluetoothAdapter, MediaCodec,                   │
│  MediaProjection, AudioRecord, Socket, NSD                    │
└─────────────────────────────────────────────────────────────┘
```

## 数据流模式

每个功能遵循统一的单向数据流：

```
用户操作 (点击/触摸)
  -> ViewModel.method()
    -> Manager.method() [在 Dispatchers.IO 上执行]
      -> 调用 Android 平台 API
      -> 更新内部状态
    -> StateFlow 发出新值
  -> Compose 检测状态变化并重组
  -> UI 渲染新的视觉状态
```

### 具体示例：雷达屏幕

```
用户点击「开始雷达」
  -> RadarViewModel.start()
    -> RadarManager.start()              // 注册广播接收器
    -> _isRunning.value = true
    -> pollJob: 每 500ms 执行:
      _targets.value = manager.targets.values.toList()
      manager.onTick()                  // 蓝牙发现周期结束后自动重启
  -> RadarScreen 重组:
    - 扫描线动画播放 (0-360 度, 8 秒周期)
    - targets 渲染为 PPI 画布上的点
    - 轨迹列表显示 RSSI/距离/方位角
```

## 状态管理

### 密封类状态模式 (MirroringViewModel)

```kotlin
sealed class MirroringState {
    data object Idle : MirroringState()
    data object HostPreparing : MirroringState()
    data object HostLive : MirroringState()
    data object ClientScanning : MirroringState()
    data class ClientConnecting(val device: MirroringDevice) : MirroringState()
    data class ClientLive(val device: MirroringDevice) : MirroringState()
    data class Error(val message: String) : MirroringState()
}
```

每个 UI 屏幕将状态映射到视觉表示：

```kotlin
val state by vm.state.collectAsState()
when (state) {
    is MirroringState.Idle -> ShowStartButton()
    is MirroringState.HostLive -> ShowStreamingIndicator()
    is MirroringState.Error -> ShowErrorBanner(state.message)
}
```

### 布尔状态模式 (大部分 ViewModel)

```kotlin
class BleSpamViewModel(app: Application) : AndroidViewModel(app) {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log
}
```

## 组件设计 (TerminalKit)

所有可复用 UI 组件遵循纯函数模式：

```kotlin
@Composable
fun TerminalHeader(title: String, back: () -> Unit) {
    // 自包含：管理自身的动画状态（光标闪烁）
    // 不依赖外部状态
    // 回调驱动：用户操作时调用 back()
}
```

### 组件清单

| 组件 | 参数 | 动画效果 |
|-----------|-------|-----------|
| `TerminalHeader` | `title: String, back: () -> Unit` | 红色下划线闪烁光标，600ms 周期 |
| `TermPanel` | `title: String, content: @Composable` | 静态装饰性边框 |
| `AnimatedCard` | `name, desc, running, onClick, onToggle` | 淡入 + 滑入、颜色过渡、按压缩放 |
| `StatusBar` | 无（读取系统状态） | 静态统计行 |
| `TermInput` | `label, value, placeholder, onValue` | 终端风格的 BasicTextField |
| `TermPrompt` | `cmd: String` | 红色前缀 `root@deauth:~#` |
| `TermOutput` | `text, color` | 缩进两格的终端输出文本 |
| `TermLine` | `prompt, output` | `$ prompt output` 格式，FAIL/ERROR 时红色 |
| `SectionLabel` | `text: String` | 暗红色粗体区域标题 |
| `GlitchText` | `text, style, modifier` | 随机字符抖动效果 |
| `ScanlineOverlay` | `lineSpacing, alpha, color` | 持续垂直扫描线 |
| `AsciiDivider` | `modifier` | 虚线分隔符 `- - - - -` |
| `BootSequence` | `onComplete` | 启动时的终端自检动画 |

## 网络通信模式

### TCP 流传输 (屏幕镜像)

```
主机端:
  MediaCodec 编码器 -> Channel<ByteArray> -> VideoStreamServer(TCP:10086) -> 客户端
  TouchInputInjector <- InputReceiver(TCP:10087) <- 客户端

客户端:
  TouchInputSender(TCP:10087) -> 主机端
  MediaCodecDecoder <- VideoStreamClient(TCP:10086) <- 主机端
```

### UDP 广播 (对讲机)

```
设备发现:  UDP 9999 广播 "DEAUTHCTRL_PTT:设备名:IP地址:是否说话"
音频传输:  UDP 10000 单播 PCM 8000Hz 16位 单声道 320字节/帧
```

### NSD/mDNS 服务发现 (屏幕镜像)

```kotlin
// 主机端：注册服务
nsdManager.registerService(serviceInfo, PROTOCOL_DNS_SD, listener)
// TXT 记录: video_port=10086, input_port=10087

// 客户端：发现服务
nsdManager.discoverServices("_screenmirror._tcp.", PROTOCOL_DNS_SD, listener)
// 解析 -> MirroringDevice(name, hostAddress, videoPort, inputPort)
```

## 线程模型

| 线程 | 用途 |
|--------|---------|
| **Main** | Compose 重组、触觉反馈、UI 事件 |
| **Dispatchers.IO** | Socket I/O、MediaCodec 轮询、文件操作 |
| **Dispatchers.Main** | Bluetooth.startDiscovery()、ViewModel 状态更新 |
| **HandlerThread("H264-Enc")** | MediaCodec 编码器输出回调 |
| **HandlerThread("CodecOutput")** | MediaCodec 解码器输出清理 |
| **CoroutineScope(IO+SupervisorJob)** | MirroringHostService 生命周期作用域 |
| **Thread("ptt-discover-send")** | 对讲机 UDP 发现广播 |
| **Thread("ptt-discover-listen")** | 对讲机 UDP 发现监听 |
| **Thread("ptt-audio-send")** | 对讲机音频采集与发送 |
| **Thread("ptt-audio-recv")** | 对讲机音频接收 |
| **Thread("ptt-audio-play")** | 对讲机音频播放 |

### 关键规则

不要在非 Composable 上下文中调用 `@Composable` 函数。
使用 `context.getString()` 或在 lambda 外部提前获取字符串值：

```kotlin
// 错误：在 scope.launch 回调里调用 stringResource()
scope.launch { snackbar.show(stringResource(R.string.msg)) }

// 正确：提前在 Composable 作用域内获取
val msg = stringResource(R.string.msg)
scope.launch { snackbar.show(msg) }
```

## 视频管道详解

```
主机端编码流水线
================
  物理屏幕
    -> MediaProjection.createVirtualDisplay()
  VirtualDisplay (1280x720)
    -> Surface (零拷贝硬件路径)
  MediaCodec 编码器 (H.264 Baseline, 4Mbps CBR, 30fps)
    参数:
      KEY_PROFILE         = AVCProfileBaseline (无 B 帧)
      KEY_BIT_RATE        = 4,000,000 bps (CBR 模式)
      KEY_FRAME_RATE      = 30 fps
      KEY_I_FRAME_INTERVAL = 2 秒 (快速恢复)
      KEY_LATENCY          = 1 (最低编码延迟)
      KEY_MAX_B_FRAMES     = 0 (API 30+)
      KEY_PRIORITY         = 0 (实时)
      KEY_BITRATE_MODE     = BITRATE_MODE_CBR
    -> onOutputBufferAvailable()
  Channel<ByteArray> (UNLIMITED 容量)
    -> pumpLoop() 持续读取
  [4字节长度][1字节标记(0x01=CSD/0x00=帧)][NAL 数据] -> TCP:10086
  
CSD (Codec Specific Data) 处理
==============================
  onOutputFormatChanged()
    -> 提取 csd-0 (SPS) 和 csd-1 (PPS) 字节缓冲区
    -> 在 SPS 和 PPS 前添加 0x00000001 起始码
    -> 合并为一个数据包，标记为 CSD_MARKER (0x01)
    -> 通过 Channel 发送
  
  VideoStreamServer 缓存所有 CSD 帧
    -> 每个新客户端连接时首先重放缓存的 CSD 帧
    -> 确保解码器即使在中途连接也能正确初始化

客户端解码流水线
================
  TCP:10086 -> VideoStreamClient
    -> startReading { onFrame } 持续读取
  [4字节长度][1字节标记][NAL 数据]
    -> 标记 == 0x01 -> queueInputBuffer(flags=BUFFER_FLAG_CODEC_CONFIG)
    -> 标记 == 0x00 -> queueInputBuffer(flags=0)
  MediaCodec 解码器 (H.264)
    -> dequeueInputBuffer(0) 非阻塞模式
    -> drainOutput() 循环取出已解码帧
    -> releaseOutputBuffer(idx, true) 渲染到 Surface
  TextureView Surface -> 显示
```

## 错误处理策略

```kotlin
// Manager 层：捕获、记录、优雅降级
try { wifiManager.startScan() } catch (_: Exception) {}

// ViewModel 层：将错误作为状态暴露
private val _error = MutableStateFlow<String?>(null)
val error: StateFlow<String?> = _error

// UI 层：显示错误横幅
error?.let { Text("[!] $it", color = Red) }
```

## 测试注意事项

- Manager 接口（`contract/Contracts.kt`）支持为 ViewModel 单元测试创建 mock 实现
- Composable 函数是纯函数 -- 可使用 `createComposeRule()` 和 `onNodeWithText()` 测试
- 依赖网络的代码（TCP、NSD）应在真实设备上测试，而非模拟器
- MediaCodec 行为在不同设备上差异显著，建议在多款设备上测试屏幕镜像功能
