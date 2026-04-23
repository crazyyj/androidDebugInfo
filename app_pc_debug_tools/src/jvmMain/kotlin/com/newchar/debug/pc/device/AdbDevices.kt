package com.newchar.debug.pc.device

import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

object AdbDevices {

    private lateinit var executor: CommandExecutor

    fun initExecutor(exec: CommandExecutor) {
        executor = exec
    }

    suspend fun getConnectedDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        val result = executor.adb("devices", "-l")
        if (!result.isSuccess) {
            emptyList()
        } else {
            AdbDeviceParser.parseDeviceList(result.output)
        }
    }

    suspend fun clearLogcat(deviceId: String) {
        runCatching {
            executor.adb("-s", deviceId, "logcat", "-c")
        }
    }

    suspend fun clearAllLogcat(devices: List<DeviceInfo>) {
        devices.forEach { device ->
            runCatching { clearLogcat(device.id) }
        }
    }

    suspend fun saveLogcat(deviceId: String, modelName: String, logFilePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val currPath = Path(System.getProperty("user.dir"))
                val logDir = Path(currPath, logFilePath)
                SystemFileSystem.createDirectories(logDir)

                val absolutePath = Path(logDir, "$modelName.log")
                val process = ProcessBuilder(
                    executor.resolveAdbCommand("-s", deviceId, "logcat", "-d", "-v", "time")
                )
                    .redirectErrorStream(true)
                    .start()

                java.io.File(absolutePath.toString()).outputStream().buffered().use { output ->
                    process.inputStream.copyTo(output)
                }

                process.waitFor() == 0
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun saveAllLogcat(devices: List<DeviceInfo>, logFilePath: String) {
        devices.forEach { device ->
            runCatching { saveLogcat(device.id, device.model, logFilePath) }
        }
    }

    suspend fun getApkPath(packageName: String): String {
        return try {
            val result = executor.adb("shell", "pm", "path", packageName)
            if (result.isSuccess && result.output.isNotEmpty()) {
                val index = result.output.indexOf(':')
                if (index > -1) result.output.substring(index + 1) else ""
            } else ""
        } catch (e: Throwable) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun pushFile(localPath: String, devicePath: String): Boolean {
        return try {
            AdbCommon.mkdirSync(devicePath)
            val result = executor.adb("push", localPath, devicePath)
            result.isSuccess && result.output.startsWith(localPath)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun pullFile(localSavePath: String, devicePath: String): Boolean {
        return try {
            val result = executor.adb("pull", devicePath, localSavePath)
            result.isSuccess && result.output.startsWith(devicePath)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getApkInstallTime(packageName: String): String {
        return try {
            val result = executor.adb("shell", "dumpsys", "package", packageName)
            if (result.isSuccess) {
                result.output.lines()
                    .find { it.contains("lastUpdateTime") }
                    ?.substringAfter('=')
                    ?: ""
            } else ""
        } catch (e: Throwable) {
            e.printStackTrace()
            ""
        }
    }

    fun getConnectedDevicesSync(): List<DeviceInfo> {
        val result = executor.adbSync("devices", "-l")
        return if (result.isSuccess) {
            AdbDeviceParser.parseDeviceList(result.output)
        } else {
            emptyList()
        }
    }
}
