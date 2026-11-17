# terminal-emulator — 来源与归属

本模块源码**逐文件原样**复制自 [termux/termux-app](https://github.com/termux/termux-app)
v0.118.3 的 `terminal-emulator/` 目录，未做任何源码改动。

许可：**Apache License 2.0**（见本目录 `LICENSE`）。

依据 termux-app 仓库根 `LICENSE.md` 的例外条款，`terminal-emulator/` 与
`terminal-view/` 两个目录下的代码源自
[Jack Palevich 的 Android-Terminal-Emulator](https://github.com/jackpal/Android-Terminal-Emulator)，
以 Apache License 2.0 发布；termux-app 其余部分为 GPLv3，**本仓库不包含**。

- Copyright 2011-2013 Jack Palevich
- Copyright 2015-2024 Fredrik Fornwall 及 Termux 贡献者
- 上游 tag：v0.118.3（提交时最新稳定版）
- 构建适配（非源码改动）：上游 ndkBuild `Android.mk` → 等效 CMake；
  manifest 的 `package` 属性 → AGP 8 的 `namespace`；构建脚本由 Groovy 改为
  Kotlin DSL。编译 / 链接标志与上游一致，另按 Android 15+ 要求为 64 位 ABI
  增加 16 KB page-size 对齐。
