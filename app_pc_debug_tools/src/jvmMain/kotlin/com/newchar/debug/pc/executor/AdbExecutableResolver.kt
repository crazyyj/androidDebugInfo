package com.newchar.debug.pc.executor

import java.io.File

internal object AdbExecutableResolver {

    private val windowsExecutableNames = listOf("adb.exe", "adb.bat", "adb.cmd")
    private val unixExecutableNames = listOf("adb")

    fun resolve(preferredPath: String?, environment: Map<String, String>): String {
        val preferredResolved = preferredPath
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.takeIf(::isUsableExecutable)
        if (preferredResolved != null) {
            return preferredResolved
        }

        return resolveFromEnvironment(environment)
            ?: resolveFromCommonLocations()
            ?: DEFAULT_ADB_COMMAND
    }

    fun resolveFromEnvironment(environment: Map<String, String>): String? {
        resolveFromExplicitEnv(environment)?.let { return it }
        return resolveFromPath(environment)
    }

    private fun resolveFromCommonLocations(): String? {
        val userHome = System.getProperty("user.home").orEmpty()
        val candidates = buildList {
            executableNames().forEach { name ->
                add("$userHome/Library/Android/sdk/platform-tools/$name")
                add("$userHome/Android/Sdk/platform-tools/$name")
                add("$userHome/AppData/Local/Android/Sdk/platform-tools/$name")
            }
        }
        return candidates.firstOrNull(::isUsableExecutable)
    }

    private fun resolveFromExplicitEnv(environment: Map<String, String>): String? {
        environment["ADB"]
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.takeIf(::isUsableExecutable)
            ?.let { return it }

        val candidateRoots = listOf("ANDROID_SDK_ROOT", "ANDROID_HOME")
            .mapNotNull(environment::get)
            .map(String::trim)
            .filter(String::isNotBlank)

        candidateRoots.forEach { root ->
            executableNames().forEach { executableName ->
                val candidate = File(root, "platform-tools${File.separator}$executableName")
                if (isUsableExecutable(candidate.absolutePath)) {
                    return candidate.absolutePath
                }
            }
        }
        return null
    }

    private fun resolveFromPath(environment: Map<String, String>): String? {
        val pathValue = environment["PATH"].orEmpty()
        if (pathValue.isBlank()) {
            return null
        }
        val pathSeparator = File.pathSeparatorChar
        pathValue.split(pathSeparator)
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { directoryPath ->
                executableNames().forEach { executableName ->
                    val candidate = File(directoryPath, executableName)
                    if (isUsableExecutable(candidate.absolutePath)) {
                        return candidate.absolutePath
                    }
                }
            }
        return null
    }

    private fun executableNames(): List<String> {
        return if (isWindows()) windowsExecutableNames else unixExecutableNames
    }

    private fun isUsableExecutable(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.isFile && (file.canExecute() || isWindows())
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").orEmpty().contains("Win", ignoreCase = true)
    }

    private const val DEFAULT_ADB_COMMAND = "adb"
}
