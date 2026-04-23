package com.newchar.debug.pc.executor

import com.newchar.debug.pc.device.CommandResult
import kotlinx.coroutines.flow.Flow

interface CommandExecutor {

    suspend fun execute(vararg command: String): CommandResult

    fun syncExecute(vararg command: String): CommandResult

    fun stream(vararg command: String): Flow<String>

    suspend fun adb(vararg args: String): CommandResult

    fun adbSync(vararg args: String): CommandResult

    fun adbStream(vararg args: String): Flow<String>

    suspend fun shell(deviceId: String? = null, vararg command: String): CommandResult

    fun resolveAdbCommand(vararg args: String): List<String>

    fun resolveAdbExecutablePath(): String
}
