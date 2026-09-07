package com.newchar.debug.pc.device.scan

import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.executor.CommandExecutor

interface LanDiscoveryAgent {
    suspend fun discover(
        executor: CommandExecutor,
        state: DeviceScanState,
        config: DeviceScanConfig,
    ): LanDiscoveryResult
}

interface DeviceMetadataResolver {
    suspend fun enrich(
        executor: CommandExecutor,
        devices: List<DeviceInfo>,
    ): List<DeviceInfo>
}

data class LanDiscoveryResult(
    val refreshRequested: Boolean = false,
    val messages: List<String> = emptyList(),
    val scannedSubnets: List<String> = emptyList(),
    val discoveredEndpoints: List<String> = emptyList(),
)
