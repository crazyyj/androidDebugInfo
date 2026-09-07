package com.newchar.debug.pc.device

import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object AdbCommon {

    private lateinit var executor: CommandExecutor

    fun initExecutor(exec: CommandExecutor) {
        executor = exec
    }

    suspend fun mkdir(dirPath: String): Boolean {
        return try {
            val result = executor.shell(command = *arrayOf("mkdir", "-p", dirPath))
            result.isSuccess
        } catch (e: Exception) {
            false
        }
    }

    fun formatDate(timestamp: String): String {
        return try {
            val epochSeconds = timestamp.toLong()
            val instant = Instant.fromEpochSeconds(epochSeconds)
            // 使用系统默认时区
            val dateTime = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())

            "${dateTime.year}-${dateTime.monthNumber.toString().padStart(2, '0')}-" +
            "${dateTime.dayOfMonth.toString().padStart(2, '0')} " +
            "${dateTime.hour.toString().padStart(2, '0')}:" +
            "${dateTime.minute.toString().padStart(2, '0')}:" +
            "${dateTime.second.toString().padStart(2, '0')}"
        } catch (e: Exception) {
            timestamp
        }
    }

    suspend fun mkdirSync(dirPath: String): Boolean {
        return try {
            val result = executor.adbSync("shell", "mkdir", "-p", dirPath)
            result.isSuccess
        } catch (e: Exception) {
            false
        }
    }
}
