plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.view"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 与上游 terminal-view/build.gradle 的依赖完全一致：
    // androidx.annotation 1.3.0 + api 依赖 terminal-emulator。
    implementation("androidx.annotation:annotation:1.3.0")
    api(project(":terminal-emulator"))

    testImplementation("junit:junit:4.13.2")
}
