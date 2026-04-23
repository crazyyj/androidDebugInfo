package com.newchar.debug.pc.config

data class AppSettings(
    val adbExecutablePath: String = "",
    val manualDeviceHistory: List<String> = emptyList(),
)
