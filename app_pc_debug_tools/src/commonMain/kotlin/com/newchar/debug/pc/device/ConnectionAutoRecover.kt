package com.newchar.debug.pc.device

import com.newchar.debug.pc.device.scan.DeviceChangeEvent
import com.newchar.debug.pc.device.scan.DeviceChangeType
import com.newchar.debug.pc.device.scan.DeviceRefreshSource
import com.newchar.debug.pc.device.scan.DeviceScanConfig
import com.newchar.debug.pc.device.scan.DeviceScanManager
import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

/**
 * USB 断线 → WiFi 自动重连管理器。
 *
 * 核心原则：USB 优先，WiFi 仅作为候补。
 * - 监听设备移除事件（非手动断开、USB 设备）
 * - 等待 USB 断开等待时间后，检查 USB 是否恢复
 * - USB 未恢复 → 尝试 WiFi ADB 连接
 * - USB 恢复 → 自动断开 WiFi 连接，切回 USB
 * - 心跳超时 → 触发重连流程
 */
class ConnectionAutoRecover(
    private val executor: CommandExecutor,
    private val externalScope: CoroutineScope,
    private val scanManager: DeviceScanManager,
    private val config: DeviceScanConfig = DeviceScanConfig(),
) {

    private val scope = CoroutineScope(externalScope.coroutineContext + SupervisorJob())

    // 记录用户手动断开的设备，不自动重连
    private val manuallyDisconnected = mutableSetOf<String>()
    // 记录当前通过 WiFi 保持连接的设备
    private val wifiConnectedDevices = mutableMapOf<String, DeviceInfo>()

    private val refreshNow: suspend (DeviceRefreshSource) -> Unit = { source ->
        runCatching { scanManager.refreshNow(source) }
    }

    fun start() {
        scope.launch {
            scanManager.changeEvents.collect { event ->
                handleEvent(event)
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    /** 标记某个设备为用户手动断开，不参与自动重连 */
    fun markManualDisconnect(deviceId: String) {
        manuallyDisconnected.add(deviceId)
    }

    /** 清除手动断开标记 */
    fun clearManualDisconnect(deviceId: String) {
        manuallyDisconnected.remove(deviceId)
    }

    /** 心跳超时触发，尝试重连指定设备 */
    suspend fun onHeartbeatTimeout(deviceId: String) {
        val state = scanManager.state.value
        val device = state.devices.find { it.id == deviceId } ?: return
        if (!device.isNetworkDevice) {
            // USB 设备心跳超时 → USB 已断开，走正常重连流程
            return // 由 track-devices 触发 REMOVED 事件处理
        }
        // WiFi 设备心跳超时 → 尝试重连
        if (config.wifiReconnectCooldownMs > 0 &&
            System.currentTimeMillis() - device.lastWifiConnectedAt < config.wifiReconnectCooldownMs) {
            return
        }
        reconnectViaWifi(device)
    }

    /** 外部主动触发：检查 USB 设备是否应该切换到 WiFi */
    suspend fun checkAndReconnect(deviceId: String) {
        val state = scanManager.state.value
        val device = state.devices.find { it.id == deviceId } ?: return
        if (!device.isNetworkDevice && device.wirelessEndpoint.isNotBlank()) {
            // 当前是 USB 连接，但如果断开了会重连 WiFi
            return
        }
        if (device.isNetworkDevice) {
            reconnectViaWifi(device)
        }
    }

    // =========================================================================
    // 事件处理
    // =========================================================================

    private suspend fun handleEvent(event: DeviceChangeEvent) {
        when (event.type) {
            DeviceChangeType.REMOVED -> handleDeviceRemoved(event)
            DeviceChangeType.ADDED -> handleDeviceAdded(event)
            DeviceChangeType.CHANGED -> handleDeviceChanged(event)
        }
    }

    private suspend fun handleDeviceRemoved(event: DeviceChangeEvent) {
        val device = event.device
        val deviceId = device.id

        // 忽略手动断开、已保留的离线设备、网络设备
        if (manuallyDisconnected.contains(deviceId)) {
            return
        }
        if (device.isRetainedOffline) {
            return
        }
        if (device.isNetworkDevice) {
            wifiConnectedDevices.remove(deviceId)
            return
        }

        // USB 设备被移除 → 等待 USB 恢复
        wifiConnectedDevices.remove(deviceId)
        scope.launch {
            try {
                delay(config.usbDisconnectWaitMs)
                // 检查 USB 是否已自动恢复
                refreshNow(DeviceRefreshSource.TRACK_DEVICES)
                val state = scanManager.state.value
                val usbRestored = state.devices.any { it.id == deviceId && !it.isNetworkDevice }
                if (usbRestored) {
                    // USB 已恢复，无需 WiFi 重连
                    return@launch
                }
                // USB 未恢复 → 尝试 WiFi 连接
                val savedWifi = device.wirelessEndpoint.takeIf { it.isNotBlank() }
                if (savedWifi != null) {
                    attemptWifiReconnect(device, savedWifi)
                }
            } catch (t: Throwable) {
                // 忽略
            }
        }
    }

    private fun handleDeviceAdded(event: DeviceChangeEvent) {
        val device = event.device
        val deviceId = device.id

        // USB 恢复 → 断开同设备的 WiFi 连接（USB 优先）
        if (!device.isNetworkDevice && wifiConnectedDevices.containsKey(deviceId)) {
            wifiConnectedDevices.remove(deviceId)
            // 尝试断开 WiFi 连接
            runCatching { executor.adb("disconnect", "$deviceId") }
        }
    }

    private fun handleDeviceChanged(event: DeviceChangeEvent) {
        val device = event.device
        if (device.isRetainedOffline && device.canTryWirelessConnect) {
            // 设备标记为离线但可尝试无线连接 → 触发重连
            runCatching {
                scope.launch { attemptWifiReconnect(device, device.wirelessEndpoint) }
            }
        }
    }

    // =========================================================================
    // WiFi 重连
    // =========================================================================

    private suspend fun attemptWifiReconnect(device: DeviceInfo, endpoint: String) {
        if (endpoint.isBlank()) {
            return
        }
        val now = System.currentTimeMillis()
        if (config.wifiReconnectCooldownMs > 0 &&
            now - device.lastWifiConnectedAt < config.wifiReconnectCooldownMs) {
            return
        }

        val result = executor.adb("connect", endpoint)
        val output = result.output.ifBlank { result.error }
        if (result.isSuccess || output.contains("connected to", ignoreCase = true) ||
            output.contains("already connected to", ignoreCase = true)) {
            wifiConnectedDevices[device.id] = device.copy(lastWifiConnectedAt = now)
            refreshNow(DeviceRefreshSource.LAN_SCAN)
        }
    }

    private suspend fun reconnectViaWifi(device: DeviceInfo) {
        val endpoint = device.wirelessEndpoint.takeIf { it.isNotBlank() } ?: return
        attemptWifiReconnect(device, endpoint)
    }
}
