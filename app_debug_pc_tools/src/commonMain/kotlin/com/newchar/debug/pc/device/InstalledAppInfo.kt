package com.newchar.debug.pc.device

import kotlinx.serialization.Serializable

@Serializable
data class InstalledAppInfo(
    val packageName: String,
    val appLabel: String = "",
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
    /** 在 UI 上展示的应用名；优先使用 [appLabel]，缺失时回退 [packageName]。 */
    val displayName: String
        get() = appLabel.ifBlank { packageName }

    val categoryPriority: Int
        get() = when {
            isDebuggable -> 0
            !isSystemApp -> 1
            else -> 2
        }
}

/** 合法 Android 包名：以字母开头，仅含字母、数字、下划线与美元符，点分段。 */
private val PACKAGE_NAME_REGEX = Regex("""^[A-Za-z][A-Za-z0-9_$]*(\.[A-Za-z][A-Za-z0-9_$]*)*$""")

/** 判断是否为合法包名，用于过滤被错误解析出路径/等号的脏数据。 */
internal fun String.isValidPackageName(): Boolean = PACKAGE_NAME_REGEX.matches(this)
