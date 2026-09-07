package com.newchar.debug.pc.device

data class DeviceInfo(
    val id: String = "",
    val status: String = "device",
    val usbInfo: String = "",
    val product: String = "",
    val model: String = "",
    val device: String = "",
    val transportId: String = "",
    val manufacturer: String = "",
    val characteristics: String = "",
    val deviceCategory: String = "",
    val wirelessIp: String = "",
    val wirelessPort: Int = 0,
    val wirelessEndpoint: String = "",
    val wifiSsid: String = "",
    val isRetainedOffline: Boolean = false,
    val isManuallyDisconnected: Boolean = false,
    val lastWifiConnectedAt: Long = 0L,
) {
    val isNetworkDevice: Boolean
        get() = id.contains(':')

    val canTryWirelessConnect: Boolean
        get() = wirelessEndpoint.isNotBlank()

    /** 最近一次通过 WiFi ADB 连接时保存的 IP，用于断线后快速重连 */
    val lastKnownWifiIp: String
        get() = if (wirelessIp.isNotBlank()) wirelessIp else ""

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
