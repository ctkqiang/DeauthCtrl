# DeauthCtrl · 去认证控制系统

> **中国红客 · 哪吒网络安全**  
> Android BLE 泛洪 & Wi-Fi 干扰攻击工具  
> 纯软件实现 · 无需 Root · 无需外置硬件

---

## 法律声明

**本工具仅供授权安全研究与教育用途。**

- 攻击非自有设备或网络属于**违法行为**
- BLE 泛洪可导致附近蓝牙设备崩溃，须在隔离环境中测试
- Wi-Fi Beacon 泛洪可能违反无线电管理条例
- **开发者不对任何滥用行为承担责任**

**使用本工具即表示您已充分理解并承诺合规使用。**

---

## 功能总览

| 模块 | 功能 | 核心技术 | 可靠性 |
|------|------|----------|--------|
| **BLE 泛洪** | 伪造多厂商蓝牙广播包，洪水式发送 | `BluetoothLeAdvertiser` | 高 — 所有 Android 5.0+ 设备均支持 |
| **Wi-Fi 干扰** | 邪恶双子热点 Beacon 泛洪 | `WifiManager.startLocalOnlyHotspot()` | 实验性 — 效果因设备/系统版本而异 |

---

## BLE 泛洪模块

### 攻击原理

```
┌─────────────┐    伪造广播包 (20-100ms 间隔)    ┌─────────────┐
│  攻击设备    │ ──────────────────────────────→ │  目标设备    │
│  DeauthCtrl  │   Apple / MS / Google / Samsung  │  蓝牙栈溢出  │
└─────────────┘                                   └─────────────┘
```

利用 Android 原生 `BluetoothLeAdvertiser` 接口，以极高频率广播伪造的 BLE 广告包（Manufacturer Data），模拟真实设备签名。当附近蓝牙设备扫描到大量虚假广播时，其蓝牙协议栈可能因缓冲区溢出或状态混乱而崩溃。

### 支持协议

| 协议 | 厂商 ID | 模拟设备 | 子类型 |
|------|---------|----------|--------|
| **苹果连续性** | `0x004C` | AirDrop / AirPods / Handoff | 5 种子类型随机切换 |
| **微软 Swift Pair** | `0x0006` | Windows 外设配对 | 标准配对广播 |
| **谷歌 Fast Pair** | 服务 UUID `0xFE2C` | Pixel Buds / Sony 耳机 | 3 种设备模型 |
| **三星 SmartThings** | `0x0075` | Galaxy Buds / SmartTag / TV | 3 种配件类型 |
| **Flipper 风格泛洪** | 随机 | 多厂商混合轮换 | 5+ 种随机模式 |
| **全部协议轮换** | 全部 | 依次切换上述所有协议 | 按序循环 |

### 参数配置

| 参数 | 范围 | 默认值 | 说明 |
|------|------|--------|------|
| 广播间隔 | 20ms – 100ms | 30ms | 间隔越短，发包越密集 |
| 发射功率 | HIGH | HIGH | `ADVERTISE_TX_POWER_HIGH` |
| 广播模式 | LOW_LATENCY | LOW_LATENCY | 最低延迟模式 |

### 日志格式 — Hex Dump

每条广播日志以标准 hex dump 格式展示（8 字节/行，适配手机屏宽）：

```
#0042  23:14:05.132  苹果连续性  OK
0000  4C 00 05 02  FF 6D 4E 12  |L.....mN.|
0008  7B 00 00 00  00 00 00 00  |{....... |
```

| 字段 | 说明 |
|------|------|
| `#0042` | 全局发包序号 (自启动计数) |
| `23:14:05.132` | 时间戳 (HH:mm:ss.SSS) |
| `苹果连续性` | 协议名称 |
| `OK` / `FAIL` | 发送状态 |
| `0000 …` | Hex Dump: 偏移 + 十六进制 + ASCII 可视区 |

---

## Wi-Fi 干扰模块 (实验性)

### 攻击原理

```
┌─────────────┐                    ┌─────────────┐
│  攻击设备    │  创建同名热点        │  真实 AP     │
│  Evil Twin  │ ─ ─ ─ ─ ─ ─ ─ ─ → │  MyWiFi     │
│  "MyWiFi"   │   Beacon 帧混淆     │  信道 6      │
└─────────────┘                    └──────┬──────┘
                                          │ 客户端收到两个
                                          │ 同名 Beacon
                                    ┌─────▼──────┐
                                    │  客户端     │
                                    │  可能断连   │
                                    └────────────┘
```

**攻击步骤：**

1. **Wi-Fi 扫描** — 使用 `WifiManager.startScan()` + `getScanResults()` 发现附近 AP
2. **目标选择** — 从扫描结果中选择目标 SSID
3. **邪恶双子** — 调用 `startLocalOnlyHotspot()` 创建同名本地热点
4. **Beacon 泛洪** — 协程循环 (50-80ms/轮) 快速开关热点：
   - 偶数轮 → 开启热点 (发送 Beacon 帧广播 SSID)
   - 奇数轮 → 关闭热点
5. **自动清理** — 泛洪结束后确保热点关闭

### 扫描结果详情

| 字段 | 说明 | 示例 |
|------|------|------|
| SSID | 网络名称 | MyWiFi |
| BSSID | MAC 地址 | aa:bb:cc:dd:ee:ff |
| 信号强度 | dBm (-90 弱 ~ -20 强) | -42 dBm |
| 信道 | 自动由频率推算 | 6 (2.4G) / 149 (5G) |
| 频段 | 2.4 GHz 或 5 GHz | 2.4 GHz |
| 加密 | WPA3/WPA2/WPA/WEP/OPEN | WPA2 |
| 频率 | MHz | 2437 MHz |
| 能力字串 | 原始 capabilities | [WPA2-PSK-CCMP][ESS] |

### 信道对照表

| 频段 | 信道范围 | 频率范围 |
|------|----------|----------|
| 2.4 GHz | 1 – 14 | 2412 – 2484 MHz |
| 5 GHz (低频) | 36 – 64 | 5180 – 5320 MHz |
| 5 GHz (高频) | 100 – 144 | 5500 – 5720 MHz |
| 5 GHz (UNII-3) | 149 – 165 | 5745 – 5825 MHz |

### 重要限制

| 限制 | 说明 |
|------|------|
| **非真正 Deauth** | 无 Root 无法发送原始 802.11 管理帧 |
| **热点开关速度** | 受 Android 框架限制 ~50-100ms/轮 |
| **系统兼容性** | 部分设备/ROM 不支持 `startLocalOnlyHotspot()` |
| **信道要求** | 真实 AP 需在不同信道才能产生混淆效果 |
| **现代 Wi-Fi 抗性** | 802.11w (PMF) 可防御大部分 Beacon 攻击 |
| **扫描限制** | Android 9+ 限制每 2 分钟 4 次 Wi-Fi 扫描 |

---

## 技术架构

### 架构图

```
┌─────────────────────────────────────────────────────┐
│                   UI Layer (Compose)                  │
│  MainScreen → BleSpamScreen / WifiDisruptScreen      │
│  Components: HexDump / TerminalWindow / GlitchText   │
├─────────────────────────────────────────────────────┤
│              ViewModel Layer (StateFlow)              │
│  BleSpamViewModel  │  WifiDisruptViewModel           │
├─────────────────────────────────────────────────────┤
│              Manager Layer (Business Logic)           │
│  BleSpamManager    │  WifiDisruptManager             │
│  (BluetoothLeAdv)   │  (WifiManager/Hotspot)          │
├─────────────────────────────────────────────────────┤
│              Model Layer (Data Classes)               │
│  BlePayload / BleAdvertLogEntry / WifiNetwork        │
└─────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.21 |
| UI 框架 | Jetpack Compose + Material 3 | BOM 2024.09.00 |
| 架构模式 | MVVM | — |
| 状态管理 | StateFlow + ViewModel | — |
| 异步 | Kotlin Coroutines | 1.9.0 |
| 最低 SDK | Android 6.0 | API 23 |
| 目标 SDK | Android 14 | API 34 |
| 构建工具 | Gradle + AGP | 8.9 / 8.5.2 |
| 引擎 | Hermes (仅 Expo) / ART | — |

### 项目结构

```
app/src/main/java/xin/ctkqiang/deauthctrl/
├── MainActivity.kt                    # 入口 Activity — 权限请求 → 主界面
├── DeauthApp.kt                       # Application 类
│
├── manager/                           # 核心攻击逻辑层
│   ├── BleSpamManager.kt              # BLE 泛洪引擎
│   │   ├── spamLoop()                 #   泛洪主循环
│   │   ├── buildAppleContinuity()     #   苹果广播包构造
│   │   ├── buildMicrosoftSwiftPair()  #   微软广播包构造
│   │   ├── buildGoogleFastPair()      #   谷歌广播包构造
│   │   ├── buildSamsungSmartThings()  #   三星广播包构造
│   │   └── buildFlipperStyle()        #   Flipper 随机广播
│   └── WifiDisruptManager.kt          # Wi-Fi 攻击引擎
│       ├── scanNetworks()             #   Wi-Fi 扫描
│       ├── startFlood()               #   Beacon 泛洪循环
│       ├── tryStartHotspot()          #   创建邪恶双子
│       └── tryStopHotspot()           #   清理热点
│
├── model/                             # 数据模型
│   ├── BlePayload.kt                  # 协议枚举 + 日志条目
│   └── WifiNetwork.kt                 # 网络信息 + 工具方法
│
├── viewmodel/                         # 状态管理层
│   ├── BleSpamViewModel.kt            # BLE 页面 StateFlow
│   └── WifiDisruptViewModel.kt        # Wi-Fi 页面 StateFlow
│
└── ui/                                # 界面层
    ├── theme/                         # 主题 (黑底红字黑客风格)
    │   ├── Color.kt                   #   颜色定义
    │   ├── Type.kt                    #   字体排版 (等宽)
    │   └── Theme.kt                   #   Material 3 主题
    ├── components/                    # 可复用组件
    │   ├── HackerEffects.kt           #   扫描线 / 故障闪烁 / 数字雨
    │   ├── PermissionRequestScreen.kt #   权限请求页
    │   ├── DisclaimerDialog.kt        #   法律声明对话框
    │   └── WarningBanner.kt           #   警告横幅
    └── screens/                       # 页面
        ├── MainScreen.kt              #   主框架 (3 个 Tab)
        ├── BleSpamScreen.kt           #   BLE 泛洪操作页
        ├── WifiDisruptScreen.kt       #   Wi-Fi 干扰操作页
        └── AboutScreen.kt             #   关于 / 开发者信息
```

### 数据流

```
User Action → ViewModel → Manager → Android API
                    ↑                      ↓
               StateFlow ←── 结果回调 ─────┘
                    ↑
              Compose UI (collectAsState)
```

---

## 权限需求

| 权限 | 用途 | API 级别 | 运行时请求 |
|------|------|----------|------------|
| `BLUETOOTH` | 蓝牙基础权限 | < 31 | — |
| `BLUETOOTH_ADMIN` | 蓝牙管理 | < 31 | — |
| `BLUETOOTH_ADVERTISE` | BLE 广播 | ≥ 31 | ✓ |
| `BLUETOOTH_CONNECT` | BLE 连接 | ≥ 31 | ✓ |
| `BLUETOOTH_SCAN` | BLE 扫描 | ≥ 31 | ✓ |
| `ACCESS_FINE_LOCATION` | BLE/Wi-Fi 扫描 (Android 要求) | 全部 | ✓ |
| `ACCESS_WIFI_STATE` | Wi-Fi 状态读取 | 全部 | — |
| `CHANGE_WIFI_STATE` | 热点创建/销毁 | 全部 | — |
| `INTERNET` | 网络访问 | 全部 | — |

首次启动显示授权页面，必须在系统弹出对话框中逐一授予。

---

## 构建指南

### 环境要求

| 工具 | 版本要求 | 备注 |
|------|----------|------|
| Android Studio | Hedgehog+ | 推荐最新稳定版 |
| JDK | 17+ | OpenJDK / Oracle JDK |
| Android SDK | 34 | 含 Build Tools |
| Gradle | 8.9 | Wrapper 自动下载 |
| 真机设备 | Android 6.0+ | 模拟器不支持 BLE 广播 |
| USB 数据线 | — | 开启 USB 调试模式 |

### 构建步骤

```bash
# 1. 克隆仓库
git clone https://gitcode.com/ctkqiang_sr/DeauthCtrl.git
cd DeauthCtrl

# 2. 安装调试版本 (自动下载 Gradle)
./gradlew installDebug

# 3. 或使用 Android Studio
open -a "Android Studio" .

# 4. 发布版本
./gradlew assembleRelease
# APK → app/build/outputs/apk/release/app-release.apk
```

### Gradle 配置

```kotlin
// 关键依赖
implementation(platform("androidx.compose:compose-bom:2024.09.00"))
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
implementation("androidx.activity:activity-compose:1.9.2")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
```

---

## 实验测试指南

### BLE 泛洪测试

| 步骤 | 操作 |
|------|------|
| 1 | 准备一台**备用 Android 手机**或蓝牙设备作为目标 |
| 2 | 在**攻击设备**上安装 DeauthCtrl |
| 3 | 授予所有权限 (位置 + 附近设备) |
| 4 | 将目标设备置于蓝牙扫描/配对模式 |
| 5 | 选择攻击协议 (如 "苹果连续性")，点击 **[ 开始 BLE 泛洪 ]** |
| 6 | 观察目标设备：可能出现幽灵配对弹窗、蓝牙崩溃、响应变慢 |
| 7 | 点击 **[ 停止攻击 ]** 并确认目标设备恢复正常 |

### Wi-Fi 泛洪测试

| 步骤 | 操作 |
|------|------|
| 1 | 准备一台**闲置路由器**作为目标 AP |
| 2 | 准备一台**客户端设备** (手机/电脑) 连接该 AP |
| 3 | 点击 **[ 扫描 Wi-Fi ]**，在列表中选择目标 SSID |
| 4 | 设置泛洪时长 (建议 10 秒)，点击 **[ 开始 Beacon 泛洪 ]** |
| 5 | 观察客户端设备：可能看到同名热点闪现或短暂断连 |
| 6 | **注意**：此测试非 100% 有效，取决于设备和环境 |

---

## UI 特效

| 组件 | 效果 | 技术 |
|------|------|------|
| `ScanlineOverlay` | CRT 显示器扫描线 | `Modifier.drawBehind` 逐行绘制 |
| `GlitchText` | 红/蓝通道偏移故障闪烁 | 分层 Text + `Animatable` offset |
| `BlinkingCursor` | 终端 █ 光标闪烁 | `LaunchedEffect` + 530ms 周期 |
| `TypewriterText` | 逐字打字机效果 | `LaunchedEffect` 逐字追加 |
| `MatrixRainBackground` | 数字雨下落 | `drawBehind` 垂直线段绘制 |
| `FadeSlideRow` | 列表项逐行淡入上滑 | `AnimatedVisibility` fade + slide |
| `HexDumpEntry` | Wireshark 风格 hex 视图 | 8 字节/行，偏移+Hex+ASCII |
| `TerminalLogWindow` | 终端窗框 + CRT 扫描线 | 嵌套 Box 分层渲染 |

---

## 开发者

| 项目 | 信息 |
|------|------|
| **作者** | 钟智强 |
| **代号** | 哪吒网络安全 / ctkqiang |
| **邮箱** | ctkqiang@dingtalk.com |
| **仓库** | [gitcode.com/ctkqiang_sr/DeauthCtrl](https://gitcode.com/ctkqiang_sr/DeauthCtrl) |
| **许可** | 仅供授权安全研究使用 |

---

> **中国红客 · 国产自主 · 安全可控**
