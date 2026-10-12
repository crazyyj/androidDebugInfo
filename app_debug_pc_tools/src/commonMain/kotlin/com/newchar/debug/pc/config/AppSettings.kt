package com.newchar.debug.pc.config

import com.newchar.debug.pc.device.DeviceInfo

data class AppSettings(
    val adbExecutablePath: String = "",
    val manualDeviceHistory: List<String> = emptyList(),
    val previewAlwaysOnTop: Boolean = true,
    val cameraAppPackage: String = "com.newchar.debug.sample",
    val homeCompactMode: Boolean = false,
    val pinnedPackagesByDevice: Map<String, List<String>> = emptyMap(),
    val enableDirectStreamFallback: Boolean = false,
    val directStreamFallbackWhitelist: List<String> = emptyList(),
)

/** 判断指定设备是否允许在 ADB reverse 失效后改走局域网直连推流。 */
fun AppSettings.isDirectStreamFallbackEnabled(device: DeviceInfo): Boolean {
    if (!enableDirectStreamFallback) return false
    val identities = setOf(device.physicalDeviceKey(), device.adbTarget(), device.id, device.wirelessIp)
        .filter(String::isNotBlank)
    return directStreamFallbackWhitelist.map(String::trim).any { it in identities }
}
