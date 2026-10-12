package com.newchar.debug.pc.config

import com.newchar.debug.pc.device.DeviceInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSettingsTest {

    @Test
    fun `直连回退默认关闭`() {
        val device = DeviceInfo(id = "192.168.1.8:5555", physicalDeviceId = "device-001")

        assertFalse(AppSettings().isDirectStreamFallbackEnabled(device))
    }

    @Test
    fun `白名单支持物理设备键与 ADB endpoint`() {
        val device = DeviceInfo(id = "192.168.1.8:5555", physicalDeviceId = "device-001")
        val settings = AppSettings(
            enableDirectStreamFallback = true,
            directStreamFallbackWhitelist = listOf("physical:device-001", "192.168.1.9:5555"),
        )

        assertTrue(settings.isDirectStreamFallbackEnabled(device))
    }
}
