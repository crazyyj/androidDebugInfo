package com.newchar.debug.pc.device

import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object LogcatManager {

    private lateinit var executor: CommandExecutor

    fun initExecutor(exec: CommandExecutor) {
        executor = exec
    }

    fun handleCommand(args: Array<String>) {
        val devices = AdbDevices.getConnectedDevicesSync()

        if (args.isNotEmpty()) {
            when (args[0]) {
                "-c" -> {
                    runBlocking {
                        AdbDevices.clearAllLogcat(devices)
                    }
                }
                "-d" -> {
                    val filePath = args.getOrElse(1) { "" }
                    if (filePath.isNotEmpty()) {
                        runBlocking {
                            AdbDevices.saveAllLogcat(devices, filePath)
                        }
                    }
                    return
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            val timestamp = System.currentTimeMillis().toString()
            AdbDevices.saveAllLogcat(devices, timestamp)
        }
    }
}
