package com.newchar.debug.pc.device.scan

import com.newchar.debug.pc.device.DeviceConnectionState
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.device.STATUS_DEVICE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetainedWirelessDeviceMergeTest {

    @Test
    fun `USB 拔线后保留已缓存端点并进入无线重连状态`() {
        val usb = DeviceInfo(
            id = "usb-001",
            status = STATUS_DEVICE,
            wirelessIp = "192.168.1.8",
            wirelessPort = 5555,
            wirelessEndpoint = "192.168.1.8:5555",
            physicalDeviceId = "physical-001",
        )

        val retained = mergeRetainedWirelessDevices(listOf(usb), emptyList()).single()

        assertTrue(retained.isRetainedOffline)
        assertTrue(retained.canTryWirelessConnect)
        assertEquals("192.168.1.8:5555", retained.wirelessEndpoint)
        assertFalse(retained.connectionState is DeviceConnectionState.AdbConnected)
    }

    @Test
    fun `重新发现 WiFi ADB 时沿用 USB 的物理身份`() {
        val usb = DeviceInfo(
            id = "usb-001",
            wirelessEndpoint = "192.168.1.8:5555",
            physicalDeviceId = "physical-001",
            model = "Pixel",
        )
        val wifi = DeviceInfo(id = "192.168.1.8:5555", status = STATUS_DEVICE)

        val merged = mergeRetainedWirelessDevices(listOf(usb), listOf(wifi)).single()

        assertEquals("physical:physical-001", merged.physicalDeviceKey())
        assertEquals("Pixel", merged.model)
        assertTrue(merged.connectionState is DeviceConnectionState.WifiConnected)
    }
}