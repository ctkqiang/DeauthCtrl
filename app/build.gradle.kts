plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "xin.ctkqiang.deauthctrl"
    compileSdk = 34

    defaultConfig {
        applicationId = "xin.ctkqiang.deauthctrl"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        // 真机运行时验证测试(connectedDebugAndroidTest)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // OpenCV 体积控制:只打 arm64 + armv7(覆盖 99%+ 设备)
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
        }
        jniLibs {
            // OpenCV 与其它原生库可能携带重复的 libc++_shared.so
            pickFirsts += listOf("lib/*/libc++_shared.so")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // 战术地图 —— osmdroid (OpenStreetMap 开源渲染)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // 夜视目标检测 —— OpenCV(HOG 行人 + Haar 人脸,内置算法无需模型文件)
    implementation("org.opencv:opencv:4.9.0")
    implementation("io.github.ehsannarmani:compose-charts:0.1.0")

    // Termux 终端引擎（v0.118.3 vendor，Apache 2.0）：
    // terminal-view 通过 api 依赖把 terminal-emulator 一并带入。
    implementation(project(":terminal-view"))

    // SSH 客户端 —— JSch(mwiede 维护分支,密码/密钥认证 + pty 通道)
    implementation("com.github.mwiede:jsch:0.2.20")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // 协议层单元测试 —— 会议视频修复的运行时证据(纯 JVM,无 Android 依赖)
    testImplementation("junit:junit:4.13.2")

    // 真机运行时验证(MediaCodec 编解码 / BLE 广播会话 / UI 导航截图)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}