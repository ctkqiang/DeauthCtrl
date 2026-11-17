-keepattributes *Annotation*
-keep class xin.ctkqiang.deauthctrl.** { *; }

# Termux 终端引擎（vendor，Apache 2.0）：
# - com.termux.terminal.JNI 的 native 方法名由 JNI 符号表约束，混淆即崩溃；
# - TerminalSession 通过反射访问 FileDescriptor#descriptor（框架类，不受影响），
#   但为稳定起见整体保留两个库包，与上游库自身不混淆的发布形态一致。
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }