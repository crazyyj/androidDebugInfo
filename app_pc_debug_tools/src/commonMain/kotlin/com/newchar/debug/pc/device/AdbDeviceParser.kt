package com.newchar.debug.pc.device

object AdbDeviceParser {

    fun parseDeviceList(output: String): List<DeviceInfo> {
        return output.lines()
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("List of devices") }
            .mapNotNull(::parseDeviceLine)
    }

    fun parseDeviceLine(line: String): DeviceInfo? {
        val tokens = line.split(Regex("\\s+"))
        if (tokens.size < 2) {
            return null
        }
        val id = tokens[0]
        val status = tokens[1]
        if (status == "offline" || status == "unauthorized" || status == "recovery" || status == "sideload" || status == "authorizing" || status == "device") {
            return buildDeviceInfo(id = id, status = status, attributes = tokens.drop(2))
        }
        return null
    }

    private fun buildDeviceInfo(
        id: String,
        status: String,
        attributes: List<String>,
    ): DeviceInfo {
        var usbInfo = ""
        var product = ""
        var model = ""
        var device = ""
        var transportId = ""

        attributes.forEach { token ->
            when {
                token.startsWith("usb:") -> usbInfo = token.substringAfter(':')
                token.startsWith("product:") -> product = token.substringAfter(':')
                token.startsWith("model:") -> model = token.substringAfter(':')
                token.startsWith("device:") -> device = token.substringAfter(':')
                token.startsWith("transport_id:") -> transportId = token.substringAfter(':')
            }
        }

        return DeviceInfo(
            id = id,
            status = status,
            usbInfo = usbInfo,
            product = product,
            model = model,
            device = device,
            transportId = transportId,
        )
    }
}
