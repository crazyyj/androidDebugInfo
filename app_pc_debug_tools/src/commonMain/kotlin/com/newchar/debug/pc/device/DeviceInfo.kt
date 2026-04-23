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
    val isRetainedOffline: Boolean = false,
) {
    val isNetworkDevice: Boolean
        get() = id.contains(':')

    val canTryWirelessConnect: Boolean
        get() = wirelessEndpoint.isNotBlank()

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
