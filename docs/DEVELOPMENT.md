# 开发指南

## 环境配置

### 系统要求

| 工具 | 版本 | 验证命令 |
|------|-------|-------|
| JDK | 21 或更高 | `java -version` |
| Gradle | 9.5.1 (通过 wrapper) | `./gradlew --version` |
| Android SDK | 34 (编译), 23 (最低) | `$ANDROID_HOME` |
| Kotlin | 2.3.20 | 由 Gradle 插件注入 |

### JDK 配置

`litertlm` 库需要 JDK 21 以上版本。在全局 Gradle 属性中配置：

```properties
# ~/.gradle/gradle.properties
org.gradle.java.home=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home
```

如需安装 JDK 21：

```bash
brew install openjdk@21
```

### 构建命令

```bash
cd Android/src

# 清理构建
./gradlew clean assembleDebug

# 安装到已连接的设备
./gradlew installDebug

# 详细输出模式构建
./gradlew clean installDebug -V

# 切换 JDK 后需要先停止旧的 Gradle 守护进程
./gradlew --stop
```

### 构建故障排除

| 错误信息 | 原因 | 解决方法 |
|-------|-------|-----|
| `类文件具有错误的版本 65.0, 应为 61.0` | JDK 版本过低，无法读取 litertlm 库 | 将 `org.gradle.java.home` 设置为 JDK 21 以上路径 |
| `@Composable invocations can only happen...` | 在非 Composable lambda 内调用了 `stringResource()` | 用 `context.getString()` 替代，或将值提取到 lambda 外部 |
| `Unresolved reference 'MIME_TYPE_AVC'` | 常量名错误 | 使用 `MediaFormat.MIMETYPE_VIDEO_AVC` |
| `Multiple substitutions...formatted="false"` | 字符串含多个 `%d` 占位符但未标记 formatted | 为 `<string>` 添加 `formatted="false"` 属性 |
| `Unresolved reference 'rememberInfiniteTransition'` | 错误的 import 路径 | 该函数在 `androidx.compose.animation.core` 包中，不在 `.animation` 中 |
| `Unresolved reference 'isHosting'` | ViewModel 属性已重命名 | 重构后属性名可能发生变化，需检查 ViewModel 文件确认当前字段名 |
| `@Composable invocations...from a @Composable function` | 在 `scope.launch {}` 或 `onDone` 回调内调用了 Composable 方法 | 将值提取到 Composable 作用域内，使用普通变量传入回调 |

## 项目配置

### Gradle 属性

```properties
# Android/src/gradle.properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

### 构建配置要点

```kotlin
// app/build.gradle.kts
compileSdk = 35
minSdk = 23
targetSdk = 35
jvmTarget = "11"
sourceCompatibility = JavaVersion.VERSION_11
targetCompatibility = JavaVersion.VERSION_11
```

### 核心依赖

```kotlin
// Compose BOM (统一管理版本)
implementation(platform("androidx.compose:compose-bom:2024.09.00"))

// 协程
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

// Hilt (通过 kapt 编译时处理)
implementation("com.google.dagger:hilt-android:...")
kapt("com.google.dagger:hilt-android-compiler:...")

// 媒体与 AI
implementation("com.google.ai.edge.litertlm-android:...")

// NSD 服务发现 (Android SDK 内置，无需额外依赖)
```

## 新增功能开发清单

按照以下步骤添加新的工具或屏幕：

### 第一步：创建 Manager (业务逻辑)

```kotlin
// manager/MyNewManager.kt
class MyNewManager(private val context: Context) {

    @Volatile var isRunning = false
        private set

    fun start(): Boolean {
        // 实现启动逻辑
        isRunning = true
        return true
    }

    fun stop() {
        // 实现清理逻辑
        isRunning = false
        // 释放资源
    }
}
```

### 第二步：创建 Manager 接口 (推荐但非必须)

```kotlin
// manager/contract/Contracts.kt (追加)
interface IMyNewManager {
    val isRunning: Boolean
    fun start(): Boolean
    fun stop()
}
```

### 第三步：创建 ViewModel

```kotlin
// viewmodel/MyNewViewModel.kt
class MyNewViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = MyNewManager(app)
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun start() {
        if (manager.start()) {
            _isRunning.value = true
            _error.value = null
        } else {
            _error.value = "启动失败"
        }
    }

    fun stop() {
        manager.stop()
        _isRunning.value = false
    }

    override fun onCleared() {
        manager.stop()
        super.onCleared()
    }
}
```

### 第四步：创建 UI 屏幕

```kotlin
// ui/screens/MainScreen.kt (底部追加新的 @Composable 函数)
@Composable
fun MyNewScreen(vm: MyNewViewModel, back: () -> Unit) {
    val running by vm.isRunning.collectAsState()
    val error by vm.error.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("我的新工具", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            // 使用 TerminalKit 组件保持风格一致
            TermPanel("配置") {
                // 设置项
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (running) vm.stop() else vm.start()
                },
                shape = RoundedCornerShape(3.dp),
                color = if (running) Color.Transparent else Red,
                border = BorderStroke(1.5.dp, if (running) Red.copy(alpha = 0.3f) else Red),
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text(
                        "$ ",
                        fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (running) Red else White,
                    )
                    Text(
                        if (running) "./stop" else "./start",
                        fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (running) Red else White,
                    )
                }
            }
            error?.let { Text("[!] $it", fontFamily = Mono, fontSize = 11.sp, color = Red) }
        }
    }
}
```

### 第五步：接入导航系统

```kotlin
// 在 MainScreen.kt 中做三处修改：

// 1. 给 MainScreen() 和 HomeScreen() 函数签名添加 ViewModel 参数
mirrorVm: MirroringViewModel,   // 已有
mynewVm: MyNewViewModel,        // 新增

// 2. 在 when (current) 块中添加路由分支
"mynew" -> MyNewScreen(mynewVm, back = { screen = "home" })

// 3. 在 HomeScreen 中添加卡片
AnimatedCard(
    "我的新工具", "工具描述",
    running = mynewVm.isRunning.collectAsState().value,
    delayMs = 1480,
    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav("mynew") },
    onToggle = { if (mynewVm.isRunning.value) mynewVm.stop() else mynewVm.start() },
)

// 4. 在 MainActivity.kt 中创建 ViewModel 实例
mynewVm = viewModel(),
// 并传入 MainScreen()
```

### 第六步：注册 Service (如需前台服务)

```xml
<!-- AndroidManifest.xml -->
<service
    android:name=".MyNewService"
    android:foregroundServiceType="..."
    android:exported="false" />

<!-- 如需新的前台服务类型权限 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_XXX" />
```

## 代码规范

### 命名约定

| 元素 | 约定 | 示例 |
|---------|------------|---------|
| Manager 类 | `*Manager` | `RadarManager` |
| ViewModel 类 | `*ViewModel` | `RadarViewModel` |
| Manager 接口 | `I*Manager` | `IRadarManager` |
| 屏幕 Composable | `*Screen` | `RadarScreen` |
| 组件 Composable | `大驼峰` | `TerminalHeader` |
| 状态密封类 | `*State` | `MirroringState` |
| Service 类 | `*Service` | `MirroringHostService` |
| 数据模型 | 大驼峰 | `MirroringDevice`, `TouchEvent` |

### 注释语言

所有注释使用中文。

### 配色方案

```kotlin
val Red       = Color(0xFFFF0000)  // 主色调 -- 激活状态、头部、警告
val RedDim    = Color(0xFF880000)  // 暗红 -- 弱化强调
val White     = Color(0xFFEEEEEE)  // 白 -- 主要文本
val Gray      = Color(0xFF777777)  // 灰 -- 次要文本、描述文字
val Dark      = Color(0xFF000000)  // 纯黑 -- 背景
val SurfaceBg = Color(0xFF060606)  // 深黑 -- 卡片/面板背景
val BorderDim = Color(0xFF1F1F1F)  // 暗边 -- 边框、分隔线
val Mono      = FontFamily.Monospace
```

## 常见开发陷阱

### 1. Composable 上下文

```kotlin
// 错误：在协程 lambda 内部调用 stringResource()
scope.launch { text = stringResource(R.string.msg) }

// 正确：在 lambda 之前提取
val msg = stringResource(R.string.msg)
scope.launch { text = msg }
```

### 2. 前台服务与 MediaProjection (API 34+)

```kotlin
// 必须在 getMediaProjection() 之前调用 startForeground()
startForeground(id, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
val projection = projectionManager.getMediaProjection(code, data)

// 更稳健的做法：分两步启动
// 第 1 步：先启动前台服务进入 foreground 模式
// 第 2 步：收到 MediaProjection 授权后再开始采集
```

### 3. BroadcastReceiver 在 API 34+

```kotlin
// 必须始终指定 RECEIVER_EXPORTED 或 RECEIVER_NOT_EXPORTED
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    context.registerReceiver(receiver, filter, ContextCompat.RECEIVER_EXPORTED)
} else {
    context.registerReceiver(receiver, filter)
}
```

### 4. 蓝牙发现生命周期

```kotlin
// 错误：每 3 秒重新调用 startDiscovery()（发现周期需要 12 秒）
while (isActive) {
    btAdapter.startDiscovery()  // 失败 -- 上一次发现仍在运行
    delay(3000)
}

// 正确：事件驱动方式
// 1. 监听 ACTION_DISCOVERY_FINISHED 广播
// 2. 在 onTick() 中检查 btAdapter.isDiscovering 后再启动
// 3. 通过 ACTION_DISCOVERY_STARTED 确认启动状态
```

### 5. MediaCodec CSD 配置帧

```kotlin
// 错误：跳过编解码器配置帧
if (flags and BUFFER_FLAG_CODEC_CONFIG != 0) {
    releaseOutputBuffer(index, false); return  // 解码器无法初始化
}

// 正确：将 CSD 帧以 BUFFER_FLAG_CODEC_CONFIG 标志发送给解码器
val marker = if (flags and BUFFER_FLAG_CODEC_CONFIG != 0) CSD_MARKER else FRAME_MARKER
// VideoStreamServer 会缓存 CSD 帧并在新客户端连接时重放
```

### 6. TCP 帧长度编码

```kotlin
// 错误：toByte() 会截断超过 127 的值
stream.write(data.size.toByte())  // 错误：只写低 8 位

// 正确：使用 4 字节大端序编码
stream.write((data.size shr 24) and 0xFF)
stream.write((data.size shr 16) and 0xFF)
stream.write((data.size shr 8) and 0xFF)
stream.write(data.size and 0xFF)

// 解码端
val len = ((buf[0].toInt() and 0xFF) shl 24) or
          ((buf[1].toInt() and 0xFF) shl 16) or
          ((buf[2].toInt() and 0xFF) shl 8) or
          (buf[3].toInt() and 0xFF)
```
