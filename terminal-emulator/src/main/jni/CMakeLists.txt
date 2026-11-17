cmake_minimum_required(VERSION 3.22.1)

project(termux LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# 与上游 Android.mk 等价：termux.c 构建为 libtermux.so，
# JNI 侧 com.termux.terminal.JNI 以 System.loadLibrary("termux") 加载。
add_library(termux SHARED termux.c)

# 上游 cFlags 中的 "-Wl,--gc-sections" 是链接标志，这里放到链接选项，
# 避免 clang 在编译阶段收到链接参数。
target_link_options(termux PRIVATE "-Wl,--gc-sections")

# Android 15+ 的 16 KB page-size 设备要求 64 位 so 以 16 KB 对齐加载
# （NDK r27+ 已作为默认值）。NDK 26.3 需显式指定，4 KB 设备仍可正常加载。
# 这是构建适配而非源码改动。
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    target_link_options(termux PRIVATE "-Wl,-z,max-page-size=16384")
endif()
