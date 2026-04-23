package com.newchar.debug.pc.device

data class InstalledAppInfo(
    val packageName: String,
    val apkPath: String = "",
    val uid: String = "",
    val versionName: String = "",
    val versionCode: String = "",
    val firstInstallTime: String = "",
    val lastUpdateTime: String = "",
    val installerPackageName: String = "",
    val isSystemApp: Boolean = false,
    val isDebuggable: Boolean = false,
) {
    val categoryPriority: Int
        get() = when {
            isDebuggable -> 0
            !isSystemApp -> 1
            else -> 2
        }
}
