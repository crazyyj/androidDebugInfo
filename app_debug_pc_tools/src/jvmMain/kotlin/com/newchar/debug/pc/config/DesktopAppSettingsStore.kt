package com.newchar.debug.pc.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

class DesktopAppSettingsStore {

    companion object {
        /** 返回 PC 调试工具在当前操作系统上的配置根目录。 */
        fun configDirectoryPath(): String {
            val userHome = System.getProperty("user.home").orEmpty()
            val osName = System.getProperty("os.name").orEmpty()
            return when {
                osName.contains("Mac", ignoreCase = true) ->
                    "$userHome/Library/Application Support/NewChar/pc-debug-tools"
                osName.contains("Win", ignoreCase = true) -> {
                    val appData = System.getenv("APPDATA").orEmpty().ifBlank { "$userHome/AppData/Roaming" }
                    "$appData/NewChar/pc-debug-tools"
                }
                osName.contains("nix", ignoreCase = true) ||
                osName.contains("nux", ignoreCase = true) ||
                osName.contains("aix", ignoreCase = true) ||
                osName.contains("SunOS", ignoreCase = true) ||
                osName.contains("Solaris", ignoreCase = true) ||
                osName.contains("FreeBSD", ignoreCase = true) ||
                osName.contains("OpenBSD", ignoreCase = true) ||
                osName.contains("NetBSD", ignoreCase = true) -> {
                    val xdgConfig = System.getenv("XDG_CONFIG_HOME").orEmpty().ifBlank { "$userHome/.config" }
                    "$xdgConfig/newchar/pc-debug-tools"
                }
                else -> {
                    val configHome = System.getenv("XDG_CONFIG_HOME").orEmpty().ifBlank { "$userHome/.config" }
                    "$configHome/newchar/pc-debug-tools"
                }
            }
        }
    }

    /** 同步加载（用于 Compose 同步上下文） */
    fun loadSync(): AppSettings = runCatching {
        val file = settingsFilePath()
        if (!SystemFileSystem.exists(file)) {
            return AppSettings()
        }
        val content = SystemFileSystem.source(file).buffered().use { source ->
            source.readString()
        }
        parse(content)
    }.getOrDefault(AppSettings())

    suspend fun load(): AppSettings = withContext(Dispatchers.IO) { loadSync() }

    suspend fun save(settings: AppSettings) = withContext(Dispatchers.IO) {
        runCatching {
            val file = settingsFilePath()
            file.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(file).buffered().use { sink ->
                sink.writeString(buildContent(settings))
            }
        }
    }

    private fun parse(content: String): AppSettings {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return AppSettings()
        }
        if (!trimmed.contains('=')) {
            return AppSettings(adbExecutablePath = trimmed)
        }
        var adbExecutablePath = ""
        val manualDeviceHistory = mutableListOf<String>()
        var previewAlwaysOnTop = true
        var cameraAppPackage = "com.newchar.debug.sample"
        var homeCompactMode = false
        val pinnedPackagesByDevice = mutableMapOf<String, MutableList<String>>()
        trimmed.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) {
                    return@forEach
                }
                val key = line.substring(0, separatorIndex)
                val value = line.substring(separatorIndex + 1)
                when (key) {
                    "adbExecutablePath" -> adbExecutablePath = value
                    "manualDeviceHistory" -> if (value.isNotBlank()) manualDeviceHistory += value
                    "previewAlwaysOnTop" -> previewAlwaysOnTop = value.toBooleanStrictOrNull() ?: true
                    "cameraAppPackage" -> if (value.isNotBlank()) cameraAppPackage = value
                    "homeCompactMode" -> homeCompactMode = value.toBooleanStrictOrNull() ?: false
                    "pinnedPackage" -> parsePinnedPackage(value)?.let { (deviceId, packageName) ->
                        pinnedPackagesByDevice.getOrPut(deviceId) { mutableListOf() } += packageName
                    }
                }
            }
        return AppSettings(
            adbExecutablePath = adbExecutablePath,
            manualDeviceHistory = manualDeviceHistory.distinct(),
            previewAlwaysOnTop = previewAlwaysOnTop,
            cameraAppPackage = cameraAppPackage,
            homeCompactMode = homeCompactMode,
            pinnedPackagesByDevice = pinnedPackagesByDevice.mapValues { (_, packages) -> packages.distinct() },
        )
    }

    private fun buildContent(settings: AppSettings): String {
        return buildString {
            append("adbExecutablePath=")
            append(settings.adbExecutablePath.trim())
            append('\n')
            append("previewAlwaysOnTop=")
            append(settings.previewAlwaysOnTop)
            append('\n')
            append("cameraAppPackage=")
            append(settings.cameraAppPackage.trim())
            append('\n')
            append("homeCompactMode=")
            append(settings.homeCompactMode)
            append('\n')
            settings.pinnedPackagesByDevice.toSortedMap().forEach { (deviceId, packageNames) ->
                packageNames.distinct().sorted().forEach { packageName ->
                    append("pinnedPackage=")
                    append(deviceId)
                    append('\t')
                    append(packageName)
                    append('\n')
                }
            }
            settings.manualDeviceHistory
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .forEach { endpoint ->
                    append("manualDeviceHistory=")
                    append(endpoint)
                    append('\n')
                }
        }
    }

    private fun settingsFilePath(): Path {
        return Path(configDirectoryPath(), "settings.properties")
    }

    /** 解析一条设备应用置顶配置，格式为设备 ID、制表符、应用包名。 */
    private fun parsePinnedPackage(value: String): Pair<String, String>? {
        val separator = value.indexOf('\t')
        if (separator <= 0 || separator == value.lastIndex) return null
        val deviceId = value.substring(0, separator).trim()
        val packageName = value.substring(separator + 1).trim()
        return if (deviceId.isBlank() || packageName.isBlank()) null else deviceId to packageName
    }
}
