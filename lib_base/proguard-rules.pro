# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\sdk2/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# `lib_base` 提供给多个上层模块直接使用，库级混淆时需要保住跨模块 API。
-keep class com.newchar.debug.common.adapter.** { *; }
-keep class com.newchar.debug.common.helper.ApplicationCompat { *; }
-keep class com.newchar.debug.common.utils.Prompt { *; }
-keep class com.newchar.debug.common.utils.UIUtils { *; }
-keep class com.newchar.debug.common.utils.ViewUtils { *; }
