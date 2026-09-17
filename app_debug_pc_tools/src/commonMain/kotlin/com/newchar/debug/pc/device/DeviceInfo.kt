package com.newchar.debug.pc.device

data class DeviceInfo(
    val id: String = "",
    val usbSerial: String = "",
    val status: String = "device",
    val usbInfo: String = "",
    val product: String = "",
    val model: String = "",
    val device: String = "",
    val transportId: String = "",
    val manufacturer: String = "",
    val characteristics: String = "",
    val deviceCategory: String = "",
    val physicalDeviceId: String = "",
    val wirelessIp: String = "",
    val wirelessPort: Int = 0,
    val wirelessEndpoint: String = "",
    val wifiSsid: String = "",
    val isRetainedOffline: Boolean = false,
    val isManuallyDisconnected: Boolean = false,
    val lastWifiConnectedAt: Long = 0L,
    private val connectionStateOverride: DeviceConnectionState? = null,
) {
    /** 当前生效的连接状态；未设置覆盖状态时由设备快照派生。 */
    val connectionState: DeviceConnectionState
        get() = connectionStateOverride ?: DeviceConnectionState.from(connectionSnapshot())
    /** 为连接状态提供最小设备快照，避免状态对象持有可变设备引用。 */
    private fun connectionSnapshot(
        newStatus: String = status,
        newWirelessEndpoint: String = wirelessEndpoint,
        newRetainedOffline: Boolean = isRetainedOffline,
        newManuallyDisconnected: Boolean = isManuallyDisconnected,
    ): DeviceConnectionSnapshot = DeviceConnectionSnapshot(
        id = id,
        usbSerial = usbSerial,
        status = newStatus,
        wirelessEndpoint = newWirelessEndpoint,
        isRetainedOffline = newRetainedOffline,
        isManuallyDisconnected = newManuallyDisconnected,
    )

    /** 根据新的 ADB 观测结果切换连接状态，并返回新的不可变设备快照。 */
    fun withConnection(
        newStatus: String = status,
        newWirelessEndpoint: String = wirelessEndpoint,
        newRetainedOffline: Boolean = isRetainedOffline,
        newManuallyDisconnected: Boolean = isManuallyDisconnected,
    ): DeviceInfo {
        val newSnapshot = connectionSnapshot(
            newStatus = newStatus,
            newWirelessEndpoint = newWirelessEndpoint,
            newRetainedOffline = newRetainedOffline,
            newManuallyDisconnected = newManuallyDisconnected,
        )
        return copy(
            status = newStatus,
            wirelessEndpoint = newWirelessEndpoint,
            isRetainedOffline = newRetainedOffline,
            isManuallyDisconnected = newManuallyDisconnected,
            connectionStateOverride = connectionState.transitionTo(newSnapshot),
        )
    }

    /** 为当前连接设置可接管的备用连接状态。 */
    fun withNextConnection(nextDevice: DeviceInfo): DeviceInfo {
        return copy(connectionStateOverride = connectionState.withNextState(nextDevice.connectionState))
    }

    val isNetworkDevice: Boolean
        get() = id.contains(':')

    /** 当前是否应通过 Wi-Fi ADB 访问。 */
    val isWirelessConnection: Boolean
        get() = connectionState.isWireless(connectionSnapshot())

    /** 当前状态是否允许执行设备管理类 ADB 操作。 */
    val canManageDevice: Boolean
        get() = connectionState.canManageDevice

    /** 当前状态是否可以执行连接或重连。 */
    val canTryWirelessConnect: Boolean
        get() = connectionState.canReconnect(connectionSnapshot())

    /** 最近一次通过 WiFi ADB 连接时保存的 IP，用于断线后快速重连 */
    val lastKnownWifiIp: String
        get() = if (wirelessIp.isNotBlank()) wirelessIp else ""

    /** 返回当前连接方式对应的 ADB 目标：USB 使用序列号，Wi-Fi 使用 IP:端口。 */
    fun adbTarget(): String = connectionState.adbTarget(connectionSnapshot())

    /** 返回界面统一使用的设备名称，并在末尾标记设备分类。 */
    fun displayName(): String {
        val name = model.ifBlank { id }
        return "$name（${deviceCategory.ifBlank { "unknown" }}）"
    }

    /** 返回界面统一使用的连接状态文案，不暴露 ADB 的内部原始状态。 */
    fun connectionStatusLabel(): String = when (connectionState) {
        is DeviceConnectionState.AdbConnected -> usbInfo.takeIf(String::isNotBlank)?.let { "USB $it" } ?: "USB"
        is DeviceConnectionState.WifiConnected -> "Wifi"
        else -> connectionState.displayName
    }

    /** 返回跨 USB/Wi-Fi 路由稳定的物理设备键，缺少设备序列号时才降级为连接信息。 */
    fun physicalDeviceKey(): String {
        if (physicalDeviceId.isNotBlank()) return "physical:$physicalDeviceId"
        val networkAddress = wirelessIp.ifBlank { id.takeIf { isNetworkDevice }?.substringBefore(':').orEmpty() }
        return if (networkAddress.isNotBlank()) "network:$networkAddress" else "serial:${usbSerial.ifBlank { id }}"
    }

    companion object {
        /** 合并同一物理设备的多条 ADB 连接，并保留之前仍可用的当前连接。 */
        fun mergeConnections(
            devices: List<DeviceInfo>,
            activeStateByKey: Map<String, DeviceConnectionState> = emptyMap(),
        ): List<DeviceInfo> {
            return devices.groupBy(::connectionKey).map { (key, connections) ->
                mergeConnectionGroup(connections, activeStateByKey[key])
            }
        }

        /** 为同一物理设备的连接生成稳定的去重键。 */
        private fun connectionKey(device: DeviceInfo): String = device.physicalDeviceKey()

        /** 选出当前可用的首选连接，并将另一条可用连接挂为备用状态。 */
        private fun mergeConnectionGroup(
            connections: List<DeviceInfo>,
            activeState: DeviceConnectionState?,
        ): DeviceInfo {
            val sortedConnections = connections.sortedWith(
                compareBy<DeviceInfo> { !it.canManageDevice }
                    .thenBy { it.isRetainedOffline }
                    .thenBy { it.isManuallyDisconnected }
                    .thenBy(DeviceInfo::isWirelessConnection),
            )
            val preferred = activeState?.takeIf { it.canManageDevice }
                ?.let { state -> sortedConnections.firstOrNull { it.canManageDevice && state.matchesType(it.connectionState) } }
                ?: sortedConnections.first()
            val fallback = sortedConnections.firstOrNull { it.id != preferred.id && it.canManageDevice }
            val alternate = sortedConnections.firstOrNull { it.id != preferred.id }
            val usbConnection = sortedConnections.firstOrNull {
                it.connectionState is DeviceConnectionState.AdbConnected
            }
            val wifiConnection = sortedConnections.firstOrNull {
                it.connectionState is DeviceConnectionState.WifiConnected
            }
            return preferred.copy(
                usbSerial = preferred.usbSerial.ifBlank { usbConnection?.adbTarget().orEmpty() },
                wirelessIp = preferred.wirelessIp.ifBlank { alternate?.wirelessIp.orEmpty() },
                wirelessEndpoint = preferred.wirelessEndpoint.ifBlank { wifiConnection?.adbTarget().orEmpty() },
            ).let { device -> fallback?.let(device::withNextConnection) ?: device }
        }

        /** 判断两个状态是否属于同一连接方式，忽略各自保存的备用状态。 */
        private fun DeviceConnectionState.matchesType(other: DeviceConnectionState): Boolean = when (this) {
            is DeviceConnectionState.AdbConnected -> other is DeviceConnectionState.AdbConnected
            is DeviceConnectionState.WifiConnected -> other is DeviceConnectionState.WifiConnected
            is DeviceConnectionState.Unauthorized -> other is DeviceConnectionState.Unauthorized
            is DeviceConnectionState.Disconnected -> other is DeviceConnectionState.Disconnected
        }
    }

    override fun toString(): String {
        return buildString {
            append("Device(id=$id")
            if (status.isNotEmpty()) append(", status=$status")
            if (manufacturer.isNotEmpty()) append(", manufacturer=$manufacturer")
            if (model.isNotEmpty()) append(", model=$model")
            if (product.isNotEmpty()) append(", product=$product")
            if (wirelessEndpoint.isNotEmpty()) append(", wireless=$wirelessEndpoint")
            append(')')
        }
    }
}