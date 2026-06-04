# API 契约与 ViewModel 参考

## Manager 接口契约

所有接口定义在 `manager/contract/Contracts.kt` 中。

```kotlin
interface IRadarManager {
    val targets: ConcurrentHashMap<String, RadarTarget>
    val isRunning: Boolean
    fun start()       // 启动 WiFi/BT 双模扫描
    fun stop()        // 停止扫描并清理资源
    fun onTick()      // ViewModel 每 500ms 调用 -- 自动重启蓝牙发现
}

interface IWalkieTalkieManager {
    var deviceName: String
    val isRunning: Boolean
    val isTalking: Boolean
    val peers: ConcurrentHashMap<String, WalkiePeer>
    fun start(): Boolean   // 打开 Socket + 启动发现
    fun startTalk()        // PTT 按下
    fun stopTalk()         // PTT 释放
    fun stop()             // 关闭所有 Socket
}

interface IBleSpamManager {
    val isRunning: Boolean
    fun startSpam()
    fun stopSpam()
    fun getProfiles(): List<*>
    fun getCurrentProfile(): *
    fun setProfile(profile: Any)
    fun getCurrentInterval(): Long
    fun setInterval(ms: Long)
}

interface IBluetoothJammerManager {
    val isRunning: Boolean
    fun start()
    fun stop()
}

interface IWifiJammerManager {
    val isRunning: Boolean
    fun start()
    fun stop()
}
```

## ViewModel 参考

### RadarViewModel

```kotlin
class RadarViewModel(app: Application) : AndroidViewModel(app)

// 状态
val isRunning: StateFlow<Boolean>
val targets: StateFlow<List<RadarTarget>>

// 操作
fun start()   // 启动雷达 + 500ms 轮询 + onTick 驱动的蓝牙发现
fun stop()    // 停止所有扫描

// 生命周期
override fun onCleared()  // 释放 Manager 资源
```

### WalkieTalkieViewModel

```kotlin
class WalkieTalkieViewModel(app: Application) : AndroidViewModel(app)

// 状态
val isRunning: StateFlow<Boolean>
val isTalking: StateFlow<Boolean>    // PTT 按钮是否被按住
val isLive: StateFlow<Boolean>       // 免提模式是否激活
val liveCountdown: StateFlow<Int>    // 免提倒计时秒数
val peers: StateFlow<List<WalkiePeer>>  // 发现的设备列表
val error: StateFlow<String?>

// 操作
fun start()          // 启动 UDP 发现 + 音频通道
fun startTalk()      // PTT 按下（不在 LIVE 模式时触发）
fun stopTalk()       // PTT 释放
fun toggleLive()     // 切换 10 秒免提自动发送模式
fun stop()           // 停止所有服务
```

### MirroringViewModel

```kotlin
class MirroringViewModel(app: Application) : AndroidViewModel(app)

// 状态
val state: StateFlow<MirroringState>
val connectedDevice: StateFlow<MirroringDevice?>
val discoveryManager: NsdDiscoveryManager    // NSD/mDNS 发现管理
val videoClient: VideoStreamClient           // TCP 视频流客户端
val decoder: MediaCodecDecoder               // H.264 解码器

// 主机端操作
fun startHostPrep()                                  // 第一步：进入前台模式
fun startHostCapture(resultCode: Int, data: Intent)  // 第二步：开始屏幕采集
fun stopHost()                                       // 停止共享

// 客户端操作
fun startScanning()                                  // 开始 NSD 扫描
fun stopScanning()                                   // 停止 NSD 扫描
fun connectToDevice(device: MirroringDevice)          // 连接到发现的设备
fun startDecoding(surface: Surface)                   // 绑定解码器到 Surface
fun sendTouch(action: Int, x: Float, y: Float, pointerId: Int = 0)  // 发送触摸事件
fun disconnectClient()                                // 断开连接
```

### MirroringState (密封类)

```kotlin
sealed class MirroringState {
    data object Idle                                        // 空闲
    data object HostPreparing                               // 主机端正等待授权
    data object HostLive                                    // 主机端正在直播
    data object ClientScanning                              // 客户端正在扫描
    data class ClientConnecting(val device: MirroringDevice) // 客户端正在连接
    data class ClientLive(val device: MirroringDevice)       // 客户端已连接
    data class Error(val message: String)                    // 错误状态
}
```

## 数据模型参考

### MirroringDevice

```kotlin
data class MirroringDevice(
    val name: String,        // NSD 服务名称
    val hostAddress: String, // IP 地址
    val videoPort: Int,      // H.264 视频流 TCP 端口
    val inputPort: Int,      // 触摸输入 TCP 端口
)
```

### TouchEvent

```kotlin
data class TouchEvent(
    val action: Int,       // MotionEvent.ACTION_DOWN / _MOVE / _UP
    val x: Float,          // 归一化坐标 0.0-1.0 (相对于视频 Surface 宽度)
    val y: Float,          // 归一化坐标 0.0-1.0 (相对于视频 Surface 高度)
    val pointerId: Int = 0,
)

// 有线格式: "action,x,y,pointerId\n"
object TouchProtocol {
    fun encode(event: TouchEvent): String
    fun decode(raw: String): TouchEvent
}
```

### RadarTarget

```kotlin
data class RadarTarget(
    val id: String,        // BSSID (WiFi) 或 MAC 地址 (蓝牙)
    val name: String,      // SSID 或设备名称
    val type: String,      // "wifi" 或 "bt"
    val rssi: Int,         // dBm 值 (通常 -100 到 -20)
    val distanceM: Float,  // 估算距离 (0.3-100m)
    val angleDeg: Float,   // 伪方位角 (0-360 度，基于 MAC 哈希)
    val channel: String,   // "CH6" 或 "2.4GHz"
    val vendor: String,    // 加密类型 (WiFi)
)
```

### WalkiePeer

```kotlin
data class WalkiePeer(
    val ip: String,
    val name: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val isTalking: Boolean = false,
)
```

## 屏幕导航路由表

```kotlin
// MainScreen.kt 导航映射 (screen 字符串 -> Composable 函数)
"home"           -> HomeScreen(...)
"ble"            -> BleDetailScreen(vm, back)
"wifi"           -> WifiDetailScreen(vm, back)
"btjam"          -> BtJamDetailScreen(vm, back)
"wifijam"        -> WifiJamDetailScreen(vm, back)
"webserver"      -> WebServerDetailScreen(vm, back)
"arp"            -> ArpDetailScreen(vm, back)
"http"           -> HttpClientScreen(vm, back)
"ping"           -> PingScreen(vm, back)
"blescan"        -> BleScannerScreen(vm, back)
"portscan"       -> PortScannerScreen(vm, back)
"walkie"         -> WalkieTalkieScreen(vm, back)
"radar"          -> RadarScreen(vm, back)
"filetransfer"   -> FileTransferScreen(vm, back)
"vault"          -> SecureVaultScreen(vm, back)
"revshell"       -> RevShellScreen(vm, back)
"payload"        -> PayloadScreen(vm, back)
"brute"          -> DirBruteScreen(vm, back)
"cve"            -> CVEScreen(vm, back)
"about"          -> AboutDetailScreen(back)
"mirroring"      -> DashboardScreen(vm, onConnectDevice, back)
"remote_viewer"  -> RemoteViewerScreen(vm, device, back)
```

## Service 参考

### MirroringHostService

```kotlin
class MirroringHostService : Service()

// Intent Actions
ACTION_PREPARE          // 进入前台模式 + 启动 TCP 服务器
ACTION_START_CAPTURE    // 启动 MediaProjection 屏幕采集
ACTION_STOP             // 释放所有资源

// Intent Extras
EXTRA_RESULT_CODE: Int     // MediaProjection 权限授权结果码
EXTRA_DATA: Intent         // MediaProjection 权限授权数据

// AndroidManifest 配置
android:foregroundServiceType="mediaProjection"
android:exported="false"
```

### WalkieTalkieService

```kotlin
class WalkieTalkieService : Service()

// Intent Actions
ACTION_UPDATE   // 以当前状态更新通知

// AndroidManifest 配置
android:foregroundServiceType="microphone"
android:exported="false"
```

## 权限参考

```xml
<!-- WiFi 扫描所需权限 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

<!-- 蓝牙扫描所需权限 (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

<!-- 音频录制 (对讲机) -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- 相机 (加密保险箱) -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- 前台服务 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- NSD 组播发现 -->
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />

<!-- 基础网络 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Intent Action 参考

| Action | 发送方 | 用途 |
|--------|--------|------|
| `xin.ctkqiang.deauthctrl.action.PREPARE_MIRRORING` | MirroringViewModel | 启动镜像前台服务（仅前台模式） |
| `xin.ctkqiang.deauthctrl.action.START_CAPTURE` | MirroringViewModel | 开始屏幕采集（需携带 resultCode + data） |
| `xin.ctkqiang.deauthctrl.action.STOP_MIRRORING` | MirroringViewModel | 停止镜像服务 |
| `WalkieTalkieService.ACTION_UPDATE` | WalkieTalkieViewModel | 更新对讲机服务通知 |

## Channel / Flow 通信模式

| 通道 | 类型 | 用途 |
|--|------|------|
| `ScreenCaptureManager.encodedDataChannel` | `Channel<ByteArray>(UNLIMITED)` | 编码器输出 -> VideoStreamServer |
| `VideoStreamServer.dataChannel` | `Channel<ByteArray>?` (外部注入) | 与 encodedDataChannel 同一实例 |
| `NsdDiscoveryManager._devices` | `MutableStateFlow<List<MirroringDevice>>` | NSD 发现的设备列表流 |
| `MirroringViewModel._state` | `MutableStateFlow<MirroringState>` | 镜像整体状态流 |
| `WalkieTalkieManager.jitterBuffer` | `ConcurrentLinkedQueue<ByteArray>` | 音频帧去抖动缓冲 (最多 5 帧/100ms) |
