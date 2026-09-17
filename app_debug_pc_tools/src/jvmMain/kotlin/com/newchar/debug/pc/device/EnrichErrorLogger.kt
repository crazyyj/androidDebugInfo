package com.newchar.debug.pc.device

import com.newchar.debug.pc.config.DesktopAppSettingsStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把应用名/图标补全（enrich）过程中的异常落盘，避免 UI 上只剩一句"补全失败"而无从排查。
 *
 * 日志位置：`<配置目录>/logs/enrich_errors.log`，追加写入，不做轮转（单次异常体积很小）。
 */
object EnrichErrorLogger {

    fun log(deviceId: String, error: Throwable) {
        runCatching {
            val logDir = File(DesktopAppSettingsStore.configDirectoryPath(), "logs").apply { mkdirs() }
            val logFile = File(logDir, "enrich_errors.log")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            logFile.appendText("[$timestamp] device=$deviceId\n${error.stackTraceToString()}\n\n")
        }
    }

    fun log(deviceId: String, message: String) {
        runCatching {
            val logDir = File(DesktopAppSettingsStore.configDirectoryPath(), "logs").apply { mkdirs() }
            val logFile = File(logDir, "enrich_errors.log")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            logFile.appendText("[$timestamp] device=$deviceId\n$message\n\n")
        }
    }
}
