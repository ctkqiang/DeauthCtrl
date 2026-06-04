# DeauthCtrl -- 开发者文档

Android 安全工具集，基于 Jetpack Compose 与 Kotlin Coroutines 构建。
目标 SDK: 34 | 最低 SDK: 23 | 语言: Kotlin 2.3.20

## 快速开始

```bash
cd Android/src
./gradlew clean installDebug
```

应用首次启动需要授予以下权限：
- 位置信息（WiFi/BLE 扫描）
- 附近设备（蓝牙扫描）
- 相机与麦克风
- 通知（前台服务）

## 项目结构

| 层级 | 包路径 | 职责 |
|-------|---------|---------------|
| UI | `ui/screens/`, `ui/components/`, `mirroring/ui/` | Jetpack Compose 屏幕与可复用的终端风格组件 |
| ViewModel | `viewmodel/` | 通过 `StateFlow` 管理状态，桥接 UI 与 Manager |
| Manager | `manager/` | 业务逻辑：网络扫描、音频流、视频编码 |
| Manager 契约 | `manager/contract/` | 接口抽象层，支持单元测试与依赖注入 |
| 数据模型 | `model/` | 数据类：`WifiNetwork`, `BlePayload`, `JammerModels` |
| 屏幕镜像 | `mirroring/host/`, `mirroring/client/` | H.264 编解码 + TCP 流传输 |

## 功能索引

| # | 功能 | 屏幕标题 | 核心文件 |
|---|------|--------|-----------|
| 1 | BLE 泛洪 | BLE 泛洪 | `BleSpamManager.kt`, `BleSpamViewModel.kt` |
| 2 | WiFi 干扰 | WIFI 干扰 | `WifiDisruptManager.kt` |
| 3 | 蓝牙压制 | 蓝牙压制 | `BluetoothJammerManager.kt` |
| 4 | WiFi 压制 | WIFI 压制 | `WifiJammerManager.kt` |
| 5 | Web 服务 | WEB 服务 | `WebServerManager.kt` |
| 6 | ARP 扫描 | ARP 扫描 | `ArpScanManager.kt` |
| 7 | HTTP 客户端 | HTTP 请求 | `HttpClientManager.kt` |
| 8 | Ping 泛洪 | PING 泛洪 | `PingManager.kt` |
| 9 | BLE 扫描 | BLE 扫描 | `BleScannerManager.kt` |
| 10 | 端口扫描 | 端口扫描 | `PortScannerManager.kt` |
| 11 | 对讲机 | 对讲机 | `WalkieTalkieManager.kt` (UDP PTT) |
| 12 | 设备雷达 | 设备雷达 | `RadarManager.kt` (WiFi+BT PPI) |
| 13 | 文件快传 | 文件快传 | `FileTransferManager.kt` |
| 14 | 屏幕镜像 | 屏幕镜像 | `mirroring/` (H.264 + TCP) |
| 15 | Reverse Shell | Reverse Shell | `RevShellManager.kt` |
| 16 | Payload 生成 | Payload 生成 | `PayloadGenManager.kt` |
| 17 | 目录爆破 | 目录爆破 | `DirBruteManager.kt` |
| 18 | CVE 搜索 | CVE 搜索 | `CVESearchManager.kt` |
| 19 | 加密保险箱 | 加密保险箱 | `SecureMediaManager.kt` |

## 架构原则

- **MVVM** -- ViewModel 暴露 `StateFlow<State>`，Compose 通过 `collectAsState()` 订阅
- **单向数据流** -- 用户操作 -> ViewModel -> Manager -> State -> UI 重组
- **SOLID** -- Manager 接口在 `contract/` 中定义，ViewModel 遵循单一职责
- **协程原生** -- 所有 I/O 在 `Dispatchers.IO`，UI 状态更新在 `Dispatchers.Main`

## 核心技术栈

- **UI**: Jetpack Compose + Material 3 + 自定义终端风格设计系统
- **响应式**: Kotlin Coroutines + `StateFlow` + `Channel`
- **网络**: TCP/UDP Socket、NSD/mDNS 发现、HTTP 服务器
- **媒体**: `MediaCodec` (H.264)、`AudioRecord`/`AudioTrack` (PCM)、`MediaProjection`
- **依赖注入**: 手动注入，通过 `AndroidViewModel` + `Application` 上下文

## 文件地图

```
app/src/main/java/xin/ctkqiang/deauthctrl/
├── MainActivity.kt                 # 唯一 Activity，权限网关
├── DeauthApp.kt                    # Application 类
├── manager/                        # 业务逻辑层
│   ├── contract/Contracts.kt       # Manager 接口
│   ├── RadarManager.kt             # WiFi/BT 双模 PPI 雷达
│   ├── WalkieTalkieManager.kt      # UDP PTT 对讲机
│   ├── ScreenCaptureManager.kt     # MediaProjection + H.264 编码
│   ├── VideoStreamServer.kt        # TCP 视频流服务器
│   ├── MediaCodecDecoder.kt        # H.264 客户端解码器
│   └── ...                         # 其余 15 个 Manager
├── viewmodel/                      # 状态管理
│   ├── RadarViewModel.kt
│   ├── WalkieTalkieViewModel.kt
│   └── MirroringViewModel.kt
├── model/                          # 数据类
├── mirroring/                      # 屏幕镜像子系统
│   ├── host/                       # 主机端：采集 + 编码 + 推流
│   ├── client/                     # 客户端：NSD + 解码 + 渲染
│   ├── model/                      # MirroringDevice, TouchEvent
│   └── ui/                         # DashboardScreen, RemoteViewerScreen
└── ui/
    ├── components/                 # 可复用终端风格组件
    │   ├── TerminalKit.kt          # TerminalHeader, TermPanel, AnimatedCard...
    │   └── HackerEffects.kt        # GlitchText, Scanline, BootSequence
    ├── screens/MainScreen.kt       # 主页 + 所有功能屏幕
    └── theme/                      # Color.kt, Theme.kt, Type.kt
```
