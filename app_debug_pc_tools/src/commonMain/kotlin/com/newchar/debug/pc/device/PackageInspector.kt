package com.newchar.debug.pc.device

import com.newchar.debug.pc.device.CommandResult
import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object PackageInspector {

    suspend fun loadInstalledApps(
        executor: CommandExecutor,
        deviceId: String,
    ): List<InstalledAppInfo> = coroutineScope {
        val listResult = loadPackageList(executor, deviceId)
        if (listResult == null) {
            return@coroutineScope emptyList()
        }
        val baseApps = parsePackageList(listResult.output)
            .filterNot { shouldIgnorePackage(it.packageName) }
        if (baseApps.isEmpty()) {
            return@coroutineScope emptyList()
        }

        val semaphore = Semaphore(4)
        baseApps.map { baseInfo ->
            async {
                semaphore.withPermit {
                    val dumpsysResult = executor.adb("-s", deviceId, "shell", "dumpsys", "package", baseInfo.packageName)
                    if (!dumpsysResult.isSuccess) {
                        return@withPermit baseInfo
                    }
                    enrichPackageInfo(baseInfo, dumpsysResult.output)
                }
            }
        }.awaitAll()
            .sortedWith(compareBy<InstalledAppInfo> { it.categoryPriority }.thenBy { it.packageName })
    }

    private fun parsePackageList(output: String): List<InstalledAppInfo> {
        return output.lines().mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("package:")) {
                return@mapNotNull null
            }
            val packageSection = line.removePrefix("package:")
            val splitIndex = packageSection.indexOf('=')
            if (splitIndex <= 0) {
                return@mapNotNull null
            }
            val apkPath = packageSection.substring(0, splitIndex).trim()
            val packageAndUid = packageSection.substring(splitIndex + 1).trim()
            val parts = packageAndUid.split(Regex("\\s+"))
            val packageName = parts.firstOrNull().orEmpty()
            val uid = parts.firstOrNull { it.startsWith("uid:") }
                ?.substringAfter(':')
                .orEmpty()
            if (packageName.isBlank()) {
                return@mapNotNull null
            }
            InstalledAppInfo(
                packageName = packageName,
                apkPath = apkPath,
                uid = uid,
                isSystemApp = isSystemPackagePath(apkPath),
            )
        }
    }

    private fun enrichPackageInfo(
        baseInfo: InstalledAppInfo,
        dumpsysOutput: String,
    ): InstalledAppInfo {
        val uid = baseInfo.uid.ifBlank {
            findSingleValue(dumpsysOutput, "userId")
                .ifBlank { findSingleValue(dumpsysOutput, "appId") }
        }
        val versionCode = findSingleValue(dumpsysOutput, "versionCode")
            .substringBefore(' ')
            .substringBefore('=')
        val versionName = findSingleValue(dumpsysOutput, "versionName")
        val firstInstallTime = findSingleValue(dumpsysOutput, "firstInstallTime")
        val lastUpdateTime = findSingleValue(dumpsysOutput, "lastUpdateTime")
        val installerPackageName = findSingleValue(dumpsysOutput, "installerPackageName")
            .ifBlank { findSingleValue(dumpsysOutput, "installerPackageName:") }
        val isDebuggable = dumpsysOutput.lineSequence().any {
            it.contains("DEBUGGABLE", ignoreCase = true)
        }
        return baseInfo.copy(
            uid = uid,
            versionCode = versionCode,
            versionName = versionName,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime,
            installerPackageName = installerPackageName,
            isDebuggable = isDebuggable,
        )
    }

    private fun findSingleValue(output: String, key: String): String {
        val prefix = "$key="
        return output.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(prefix)
            ?.trim()
            .orEmpty()
    }

    private suspend fun loadPackageList(
        executor: CommandExecutor,
        deviceId: String,
    ): CommandResult? {
        val commands = listOf(
            arrayOf("-s", deviceId, "shell", "pm", "list", "packages", "-f", "-U"),
            arrayOf("-s", deviceId, "shell", "pm", "list", "packages", "-f"),
        )
        commands.forEach { args ->
            val result = executor.adb(*args)
            val output = result.output.trim()
            val isPackageList = output.lineSequence().any { it.trim().startsWith("package:") }
            val hasKnownError = output.startsWith("Error:", ignoreCase = true) ||
                output.contains("Unknown option", ignoreCase = true)
            if (result.isSuccess && output.isNotBlank() && isPackageList && !hasKnownError) {
                return result
            }
        }
        return null
    }

    private fun shouldIgnorePackage(packageName: String): Boolean {
        return packageName == "android" ||
            packageName.startsWith("android.") ||
            packageName.startsWith("com.android")
    }

    private fun isSystemPackagePath(path: String): Boolean {
        return path.startsWith("/system/") ||
            path.startsWith("/product/") ||
            path.startsWith("/vendor/") ||
            path.startsWith("/system_ext/") ||
            path.startsWith("/odm/") ||
            path.startsWith("/apex/")
    }
}
