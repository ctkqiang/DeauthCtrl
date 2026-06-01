# DeauthCtrl · 去认证控制系统

> **中国红客 · 哪吒网络安全**
> Android 网络安全工具集 · 纯软件实现 · 终端美学
> 12 合 1 安全工具 · 触觉反馈 · 好莱坞黑客 UI

---

## 下载

| 平台 | 直链 |
|------|------|
| **GitHub** | [DeauthCTRL.apk](https://github.com/ctkqiang/DeauthCtrl/releases/download/v1.0.0/DeauthCTRL.apk) |
| **GitCode** | [DeauthCTRL.apk](https://gitcode.com/ctkqiang_sr/DeauthCtrl/releases/download/v1.0.0/DeauthCTRL.apk) |

---

## 法律声明

**本工具仅供授权安全研究与教育用途。**

- 攻击非自有设备或网络属于**违法行为**
- BLE / WiFi 攻击可能违反无线电管理条例
- **开发者不对任何滥用行为承担责任**
- 使用本工具即表示您已充分理解并承诺合规使用

---

## 功能总览

| #   | 模块          | 功能                                | 技术实现                             |
| --- | ------------- | ----------------------------------- | ------------------------------------ |
| 1   | **BLE 泛洪**  | 多厂商蓝牙广播包洪水式发送          | `BluetoothLeAdvertiser`              |
| 2   | **WIFI 干扰** | 邪恶双子热点 Beacon 泛洪            | `WifiManager` Hotspot API            |
| 3   | **蓝牙压制**  | 全频段 BLE + 经典蓝牙攻击           | BLE Scan + Classic Discovery         |
| 4   | **WIFI 压制** | 自动扫描并轮流压制附近所有 SSID     | 信道切换 + Deauth 帧模拟             |
| 5   | **WEB 服务**  | 手机热点 HTTP 服务器托管 HTML       | `ServerSocket` 嵌入式 HTTP/1.1       |
| 6   | **ARP 扫描**  | 局域网 Ping Sweep 设备发现          | 并发 Ping + `/proc/net/arp`          |
| 7   | **HTTP 请求** | curl/Postman 风格 HTTP 客户端       | `HttpURLConnection` 全方法支持       |
| 8   | **PING 泛洪** | 无限 ICMP Ping 压力测试             | `Runtime.exec("ping")` + 实时统计    |
| 9   | **BLE 扫描**  | 低功耗蓝牙嗅探 + RSSI 实时图谱      | `BluetoothLeScanner` + Canvas 折线图 |
| 10  | **端口扫描**  | Nmap 风格 TCP Connect 扫描 + Banner | `Socket.connect()` + 40+ 端口        |
| 11  | **系统信息**  | 关于页 — 作者/架构/许可             | 终端命令风格 (`uname -a` / `whoami`) |
| 12  | **权限管理**  | 运行时权限请求网关                  | `RequestMultiplePermissions`         |
| 13  | **WiFi 对讲** | 局域网 PTT 语音通话 · 免提模式 · 说话者识别 | UDP 广播发现 + AudioRecord/AudioTrack PCM 流 |

---

## 技术架构

```
┌──────────────────────────────────────────────────────────┐
│                   UI Layer (Jetpack Compose)               │
│  13 Detail Screens · HackerEffects · Terminal Components  │
│  GlitchText · ScanlineOverlay · PulseDot · AnimatedCard  │
├──────────────────────────────────────────────────────────┤
│             ViewModel Layer (StateFlow + Haptic)           │
│  10 ViewModels: BleSpam · WifiDisrupt · BtJam · WifiJam  │
│  WebServer · ArpScan · HttpClient · Ping · BleScan · Port│
├──────────────────────────────────────────────────────────┤
│             Manager Layer (Business Logic)                │
│  Android APIs · ServerSocket · HttpURLConnection         │
│  Runtime.exec() · Socket · BluetoothLeScanner            │
├──────────────────────────────────────────────────────────┤
│             Model Layer (Data Classes)                    │
│  BlePayload · WifiNetwork · JammerModels · ArpEntry      │
│  PingResult · HttpResponse · PortResult · BleDevice      │
└──────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级     | 技术                                     | 版本        |
| -------- | ---------------------------------------- | ----------- |
| 语言     | Kotlin                                   | 2.0.21      |
| UI       | Jetpack Compose + Material 3             | BOM 2024.09 |
| 架构     | MVVM + Manager Pattern                   | —           |
| 状态     | StateFlow + ViewModel + AndroidViewModel | —           |
| 异步     | Kotlin Coroutines                        | 1.9.0       |
| 最低 SDK | Android 6.0 (API 23)                     | —           |
| 目标 SDK | Android 14 (API 34)                      | —           |
| 构建     | Gradle + AGP                             | 8.9 / 8.5.2 |

### 数据流

```
User Action → ViewModel → Manager → Android API / System Call
     ↑                                          ↓
 StateFlow ←──────────── 结果回调 ──────────────┘
     ↑
Compose UI (collectAsState + HapticFeedback)
```

---

## 项目结构

```
app/src/main/java/xin/ctkqiang/deauthctrl/
│
├── MainActivity.kt              # 入口 Activity · 权限检查 · 10 个 ViewModel 注入
├── DeauthApp.kt                 # Application 类
│
├── manager/                     # 业务逻辑层 (12 个 Manager)
│   ├── BleSpamManager.kt        #   BLE 泛洪引擎
│   ├── WifiDisruptManager.kt    #   WiFi 干扰引擎
│   ├── BluetoothJammerManager.kt#   蓝牙压制引擎
│   ├── WifiJammerManager.kt     #   WiFi 压制引擎
│   ├── WebServerManager.kt      #   嵌入式 HTTP 服务器
│   ├── ArpScanManager.kt        #   ARP/Ping Sweep 扫描
│   ├── HttpClientManager.kt     #   curl HTTP 客户端
│   ├── PingManager.kt           #   ICMP Ping 探测
│   ├── BleScannerManager.kt     #   BLE 嗅探扫描
│   └── PortScannerManager.kt    #   TCP 端口扫描
│
├── model/                       # 数据模型
│   ├── BlePayload.kt            #   BLE 协议枚举 + 日志
│   ├── WifiNetwork.kt           #   WiFi 网络信息
│   └── JammerModels.kt          #   压制器状态模型
│
├── viewmodel/                   # 状态管理层 (10 个 ViewModel)
│   ├── BleSpamViewModel.kt
│   ├── WifiDisruptViewModel.kt
│   ├── BluetoothJammerViewModel.kt
│   ├── WifiJammerViewModel.kt
│   ├── WebServerViewModel.kt
│   ├── ArpScanViewModel.kt
│   ├── HttpClientViewModel.kt
│   ├── PingViewModel.kt
│   ├── BleScannerViewModel.kt
│   └── PortScannerViewModel.kt
│
└── ui/                          # 界面层
    ├── theme/
    │   ├── Color.kt             #   红黑配色系统 (30+ 色值)
    │   ├── Type.kt              #   全站等宽字体 (Monospace)
    │   └── Theme.kt             #   Material 3 暗色主题
    ├── components/
    │   ├── HackerEffects.kt     #   GlitchText / Scanline / PulseDot / Boot
    │   ├── PermissionRequestScreen.kt
    │   ├── DisclaimerDialog.kt
    │   └── WarningBanner.kt
    └── screens/
        └── MainScreen.kt        #   主路由 + 12 个详情页 (~1100 行)
```

---

## UI 设计系统

### 配色方案

| 角色        | 色值                  | 用途                       |
| ----------- | --------------------- | -------------------------- |
| Primary Red | `#FF0000`             | 标题、按钮、强调、运行状态 |
| Red Dim     | `#880000`             | 辅助文字、非活跃状态       |
| Deep Black  | `#010101`             | 主背景                     |
| Surface     | `#080808` / `#0D0D0D` | 卡片/面板背景              |
| Border      | `#1F1F1F`             | 卡片/面板边框              |
| Text White  | `#EEEEEE`             | 正文                       |
| Text Gray   | `#777777`             | 描述/辅助文字              |

### 特效组件

| 组件              | 效果                               | 实现                             |
| ----------------- | ---------------------------------- | -------------------------------- |
| `GlitchText`      | 红/白/青 三层 RGB 通道偏移故障闪烁 | 分层 Text + LaunchedEffect       |
| `ScanlineOverlay` | CRT 显示器扫描线                   | `drawBehind` 逐行 drawLine       |
| `PulseDot`        | 呼吸脉冲指示灯                     | `sin` 正弦波透明度动画           |
| `AsciiDivider`    | 终端分隔线 (`─`×48)                | Monospace Text                   |
| `BootSequence`    | 8 步系统启动动画                   | AnimatedVisibility + 打字机延迟  |
| `AnimatedCard`    | 5 色过渡 + 弹性缩放 + 逐级入场     | `animateColorAsState` + `spring` |

### 动画系统

| 动画     | 类型                            | 参数                       |
| -------- | ------------------------------- | -------------------------- |
| 页面过渡 | `AnimatedContent` fade + slide  | tween 300ms / 350ms        |
| 卡片入场 | `AnimatedVisibility` + 逐级延迟 | 0→720ms (10 张卡片)        |
| 状态切换 | `animateColorAsState`           | tween 400ms (5 色同时过渡) |
| 按钮按压 | `animateFloatAsState` spring    | 缩放到 95%                 |
| 触觉反馈 | `HapticFeedbackType.LongPress`  | 所有交互按钮               |

---

## 权限需求

| 权限                   | 用途                         | API | 运行时 |
| ---------------------- | ---------------------------- | --- | ------ |
| `BLUETOOTH_SCAN`       | BLE 扫描 + 嗅探              | ≥31 | ✓      |
| `BLUETOOTH_CONNECT`    | BLE 连接                     | ≥31 | ✓      |
| `BLUETOOTH_ADVERTISE`  | BLE 广播/泛洪                | ≥31 | ✓      |
| `ACCESS_FINE_LOCATION` | BLE/WiFi 扫描 (Android 要求) | 全  | ✓      |
| `ACCESS_WIFI_STATE`    | WiFi 状态读取                | 全  | —      |
| `CHANGE_WIFI_STATE`    | 热点创建/销毁                | 全  | —      |
| `INTERNET`             | HTTP 客户端/服务器           | 全  | —      |

---

## 构建指南

### 环境

| 工具           | 版本                      |
| -------------- | ------------------------- |
| Android Studio | Hedgehog+                 |
| JDK            | 17+                       |
| Android SDK    | 34                        |
| 真机           | Android 6.0+ (BLE 需真机) |

### 命令

```bash
git clone https://gitcode.com/ctkqiang_sr/DeauthCtrl.git
cd DeauthCtrl

./gradlew installDebug       # 安装调试版
./gradlew assembleRelease    # 构建发布版 APK
```

---

## 开发者

| 项目 | 信息                               |
| ---- | ---------------------------------- |
| 作者 | 钟智强 (ctkqiang)                  |
| 代号 | 哪吒网络安全                       |
| 邮箱 | ctkqiang@dingtalk.com              |
| 仓库 | gitcode.com/ctkqiang_sr/DeauthCtrl |
| 架构 | MVVM + StateFlow + Jetpack Compose |
| 许可 | 仅供授权安全研究使用               |

---

> **中国红客 · 国产自主 · 安全可控**

---

如果您觉得本项目对您有帮助，欢迎请我喝杯咖啡 ☕️，您的支持是我持续维护和改进的动力！

<p align="center">
  <strong>微信扫码捐赠</strong><br/>
  <img src="https://raw.gitcode.com/ctkqiang_sr/ctkqiang_sr/raw/main/mm_reward_qrcode_1778988737577.png" 
       alt="微信扫码捐赠" 
       width="240" 
       style="border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);" />
</p>
