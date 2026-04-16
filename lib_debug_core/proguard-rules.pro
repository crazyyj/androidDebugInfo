# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# `lib_debug_core` 在库级 release 混淆时会引用 `lib_base` 的工具类。
# 最终 app 会把 `lib_base` 一起打进来，这里按外部依赖处理，避免库级 R8 中断。
-dontwarn com.newchar.debug.common.utils.Prompt
-dontwarn com.newchar.debug.common.utils.UIUtils
-dontwarn com.newchar.debug.common.utils.ViewUtils

# `lib_debug_core` 也是其他模块的直接 API 依赖，库级混淆不能裁掉这些公开类型。
-keep class com.newchar.debug.DebugManager { *; }
-keep class com.newchar.debug.annotation.** { *; }
-keep class com.newchar.debug.api.** { *; }
-keep class com.newchar.debug.lifecycle.** { *; }
-keep class com.newchar.debug.utils.DebugUtils { *; }
-keep class com.newchar.debug.utils.HandleWrapper { *; }
