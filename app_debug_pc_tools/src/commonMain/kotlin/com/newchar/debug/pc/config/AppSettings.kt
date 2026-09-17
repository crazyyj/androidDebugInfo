package com.newchar.debug.pc.config

data class AppSettings(
    val adbExecutablePath: String = "",
    val manualDeviceHistory: List<String> = emptyList(),
    val previewAlwaysOnTop: Boolean = true,
    val cameraAppPackage: String = "com.newchar.debug.sample",
    val homeCompactMode: Boolean = false,
    val pinnedPackagesByDevice: Map<String, List<String>> = emptyMap(),
)
