package com.newchar.debug.pc.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

class DesktopAppSettingsStore {

    suspend fun load(): AppSettings = withContext(Dispatchers.IO) {
        runCatching {
            val file = settingsFilePath()
            if (!SystemFileSystem.exists(file)) {
                return@runCatching AppSettings()
            }
            val content = SystemFileSystem.source(file).buffered().use { source ->
                source.readString()
            }
            parse(content)
        }.getOrDefault(AppSettings())
    }

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
                }
            }
        return AppSettings(
            adbExecutablePath = adbExecutablePath,
            manualDeviceHistory = manualDeviceHistory.distinct(),
        )
    }

    private fun buildContent(settings: AppSettings): String {
        return buildString {
            append("adbExecutablePath=")
            append(settings.adbExecutablePath.trim())
            append('\n')
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

    private fun configDirectoryPath(): String {
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
            osName.contains("aix", ignoreCase = true) -> {
                val xdgConfig = System.getenv("XDG_CONFIG_HOME").orEmpty().ifBlank { "$userHome/.config" }
                "$xdgConfig/newchar/pc-debug-tools"
            }
            osName.contains("SunOS", ignoreCase = true) ||
            osName.contains("Solaris", ignoreCase = true) -> {
                val xdgConfig = System.getenv("XDG_CONFIG_HOME").orEmpty().ifBlank { "$userHome/.config" }
                "$xdgConfig/newchar/pc-debug-tools"
            }
            osName.contains("FreeBSD", ignoreCase = true) ||
            osName.contains("OpenBSD", ignoreCase = true) ||
            osName.contains("NetBSD", ignoreCase = true) -> {
                val xdgConfig = System.getenv("XDG_CONFIG_HOME").orEmpty().ifBlank { "$userHome/.config" }
                "$xdgConfig/newchar/pc-debug-tools"
            }
            else -> {
                val configHome = System.getenv("XDG_CONFIG_HOME").orEmpty()
                    .ifBlank { "${userHome}/.config" }
                "$configHome/newchar/pc-debug-tools"
            }
        }
    }
}
