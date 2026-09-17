package com.newchar.debug.pc.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceConnectionStateTest {

    @Test
    fun `USB 设备可管理且以序列号作为 ADB 目标`() {
        val device = DeviceInfo(id = "usb-001", status = STATUS_DEVICE)

        assertTrue(device.connectionState is DeviceConnectionState.AdbConnected)
        assertTrue(device.canManageDevice)
        assertFalse(device.isWirelessConnection)
        assertEquals("usb-001", device.adbTarget())
    }

    @Test
    fun `无线设备以端点作为 ADB 目标`() {
        val device = DeviceInfo(id = "192.168.1.8:5555", status = STATUS_DEVICE)

        assertTrue(device.connectionState is DeviceConnectionState.WifiConnected)
        assertTrue(device.canManageDevice)
        assertTrue(device.isWirelessConnection)
        assertEquals("192.168.1.8:5555", device.adbTarget())
    }

    @Test
    fun `未授权状态可重连但不可执行设备管理`() {
        val device = DeviceInfo(id = "192.168.1.8:5555", status = STATUS_UNAUTHORIZED)

        assertTrue(device.connectionState is DeviceConnectionState.Unauthorized)
        assertFalse(device.canManageDevice)
        assertTrue(device.canTryWirelessConnect)
    }

    @Test
    fun `copy 更新原始状态时会重新派生连接状态`() {
        val onlineDevice = DeviceInfo(id = "usb-001", status = STATUS_DEVICE)
        val unauthorizedDevice = onlineDevice.copy(status = STATUS_UNAUTHORIZED)

        assertTrue(unauthorizedDevice.connectionState is DeviceConnectionState.Unauthorized)
        assertFalse(unauthorizedDevice.canManageDevice)
    }

    @Test
    fun `设备通过状态迁移进入保留离线状态`() {
        val onlineDevice = DeviceInfo(
            id = "usb-001",
            status = STATUS_DEVICE,
            wirelessEndpoint = "192.168.1.8:5555",
        )
        val offlineDevice = onlineDevice.withConnection(
            newStatus = STATUS_OFFLINE,
            newRetainedOffline = true,
        )

        assertEquals(DeviceConnectionState.Disconnected(DeviceDisconnectReason.RETAINED_OFFLINE), offlineDevice.connectionState)
        assertFalse(offlineDevice.canManageDevice)
        assertTrue(offlineDevice.canTryWirelessConnect)
        assertEquals("192.168.1.8:5555", offlineDevice.adbTarget())
    }

    @Test
    fun `USB 断开时自动启用已连接的 WiFi 备用状态`() {
        val usbDevice = DeviceInfo(
            id = "usb-001",
            status = STATUS_DEVICE,
            wirelessIp = "192.168.1.8",
        )
        val wifiDevice = DeviceInfo(
            id = "192.168.1.8:5555",
            status = STATUS_DEVICE,
            wirelessIp = "192.168.1.8",
        )

        val mergedDevice = DeviceInfo.mergeConnections(listOf(usbDevice, wifiDevice)).single()
        val fallbackDevice = mergedDevice.withConnection(newStatus = STATUS_OFFLINE)

        assertEquals(
            DeviceConnectionState.AdbConnected(DeviceConnectionState.WifiConnected()),
            mergedDevice.connectionState,
        )
        assertEquals(DeviceConnectionState.WifiConnected(), fallbackDevice.connectionState)
        assertTrue(fallbackDevice.canManageDevice)
        assertTrue(fallbackDevice.isWirelessConnection)
        assertEquals("192.168.1.8:5555", fallbackDevice.adbTarget())
    }

    @Test
    fun `USB 恢复时不抢占 WiFi 且可在 WiFi 断开后回退 USB`() {
        val usbDevice = DeviceInfo(
            id = "usb-001",
            status = STATUS_DEVICE,
            wirelessIp = "192.168.1.8",
        )
        val wifiDevice = DeviceInfo(
            id = "192.168.1.8:5555",
            status = STATUS_DEVICE,
            wirelessIp = "192.168.1.8",
        )
        val stateByKey = mapOf("network:192.168.1.8" to DeviceConnectionState.WifiConnected())

        val wifiActiveDevice = DeviceInfo.mergeConnections(listOf(usbDevice, wifiDevice), stateByKey).single()
        val usbFallbackDevice = wifiActiveDevice.withConnection(newStatus = STATUS_OFFLINE)

        assertEquals(
            DeviceConnectionState.WifiConnected(DeviceConnectionState.AdbConnected()),
            wifiActiveDevice.connectionState,
        )
        assertEquals(DeviceConnectionState.AdbConnected(), usbFallbackDevice.connectionState)
        assertFalse(usbFallbackDevice.isWirelessConnection)
        assertEquals("usb-001", usbFallbackDevice.adbTarget())
    }

    @Test
    fun `物理序列号优先于 IP 作为双链路归并键`() {
        val usb = DeviceInfo(id = "usb-001", physicalDeviceId = "physical-001")
        val wifi = DeviceInfo(id = "192.168.1.99:5555", physicalDeviceId = "physical-001")

        assertEquals(usb.physicalDeviceKey(), wifi.physicalDeviceKey())
        assertEquals(1, DeviceInfo.mergeConnections(listOf(usb, wifi)).size)
    }

    @Test
    fun `设备展示名称含分类且连接文案仅表达当前链路`() {
        val usb = DeviceInfo(
            id = "usb-001",
            model = "Pixel",
            deviceCategory = "phone",
            usbInfo = "1-2",
        )
        val wifi = DeviceInfo(id = "192.168.1.8:5555", status = STATUS_DEVICE)

        assertEquals("Pixel（phone）", usb.displayName())
        assertEquals("USB 1-2", usb.connectionStatusLabel())
        assertEquals("Wifi", wifi.connectionStatusLabel())
    }
}