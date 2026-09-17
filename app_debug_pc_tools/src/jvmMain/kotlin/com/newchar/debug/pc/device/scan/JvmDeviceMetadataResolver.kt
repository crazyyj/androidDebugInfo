package com.newchar.debug.pc.device.scan

import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.datetime.Clock

internal const val DEFAULT_TRADITIONAL_ADB_PORT = 5555

class JvmDeviceMetadataResolver : DeviceMetadataResolver {

    private val cache = linkedMapOf<String, CacheEntry>()

    override suspend fun enrich(
        executor: CommandExecutor,
        devices: List<DeviceInfo>,
    ): List<DeviceInfo> {
        val now = Clock.System.now().toEpochMilliseconds()
        return devices.map { device ->
            if (!device.canManageDevice) {
                return@map device
            }
            val cached = cache[device.id]
            if (cached != null && cached.base == device && now - cached.timestampMs < CACHE_TTL_MS) {
                return@map cached.enriched
            }
            val enriched = runCatching { enrichDevice(executor, device) }.getOrElse { device }
            cache[device.id] = CacheEntry(device, enriched, now)
            enriched
        }
    }

    private suspend fun enrichDevice(
        executor: CommandExecutor,
        device: DeviceInfo,
    ): DeviceInfo {
        val manufacturer = readProp(executor, device.id, "ro.product.manufacturer")
        val model = readProp(executor, device.id, "ro.product.model").ifBlank { device.model }
        val deviceName = readProp(executor, device.id, "ro.product.device").ifBlank { device.device }
        val characteristics = readProp(executor, device.id, "ro.build.characteristics")
        val physicalDeviceId = readPhysicalDeviceId(executor, device.id)
        val wirelessIp = readWirelessIp(executor, device.id)
        val configuredWirelessPort = readWirelessPort(executor, device.id)
        val wirelessPort = if (device.isNetworkDevice) {
            configuredWirelessPort
        } else {
            prepareTraditionalWifiAdb(executor, device.id, wirelessIp, configuredWirelessPort)
        }
        val wirelessEndpoint = if (wirelessIp.isNotBlank() && wirelessPort > 0) {
            "$wirelessIp:$wirelessPort"
        } else {
            ""
        }
        return device.copy(
            manufacturer = manufacturer,
            model = model,
            device = deviceName,
            characteristics = characteristics,
            deviceCategory = mapCategory(characteristics),
            physicalDeviceId = physicalDeviceId,
            wirelessIp = wirelessIp,
            wirelessPort = wirelessPort,
            wirelessEndpoint = wirelessEndpoint,
        )
    }

    private suspend fun readWirelessIp(executor: CommandExecutor, serial: String): String {
        val candidates = listOf(
            listOf("shell", "getprop", "dhcp.wlan0.ipaddress"),
            listOf("shell", "getprop", "dhcp.wlan1.ipaddress"),
            listOf("shell", "getprop", "dhcp.wifi.ipaddress"),
            listOf("shell", "ip", "-f", "inet", "addr", "show", "wlan0"),
            listOf("shell", "ip", "-f", "inet", "addr", "show", "wlan1"),
            listOf("shell", "ip", "-f", "inet", "addr", "show", "wifi0"),
            listOf("shell", "ifconfig", "wlan0"),
            listOf("shell", "ifconfig", "wlan1"),
            listOf("shell", "ip", "route"),
        )
        candidates.forEach { command ->
            val result = executor.adb("-s", serial, *command.toTypedArray())
            val text = result.output.ifBlank { result.error }.trim()
            val ip = extractWirelessIp(text, command)
            if (ip.isNotBlank()) {
                return ip
            }
        }
        return ""
    }

    /** 读取跨 USB/Wi-Fi 不变的设备序列号；读取失败时让上层回退连接键。 */
    private suspend fun readPhysicalDeviceId(executor: CommandExecutor, serial: String): String {
        val primary = readProp(executor, serial, "ro.serialno")
        return primary.ifBlank { readProp(executor, serial, "ro.boot.serialno") }
    }

    private suspend fun readWirelessPort(executor: CommandExecutor, serial: String): Int {
        val result = executor.adb("-s", serial, "shell", "getprop", "service.adb.tcp.port")
        val value = result.output.trim().toIntOrNull() ?: 0
        return value.takeIf { it > 0 } ?: 0
    }

    /**
     * 在 USB 在线且设备与 PC 同网段时，预先启用传统 TCP ADB 并建立 Wi-Fi ADB transport。
     * 已声明端口不改写；未声明端口才使用默认端口，实际可用性仍由下一次 devices 枚举确认。
     */
    private suspend fun prepareTraditionalWifiAdb(
        executor: CommandExecutor,
        serial: String,
        wirelessIp: String,
        configuredPort: Int,
    ): Int {
        if (wirelessIp.isBlank()) return configuredPort
        JvmWifiDetector.refresh()
        if (!JvmWifiDetector.isSameSubnet(wirelessIp)) return configuredPort
        val port = selectTraditionalAdbPort(configuredPort)
        if (configuredPort <= 0 && !enableTraditionalAdb(executor, serial, port)) return 0
        executor.adb("connect", "$wirelessIp:$port")
        return port
    }

    /** 通过 USB 将设备端 adbd 切换到指定 TCP 端口，并仅按命令退出状态判断切换是否接受。 */
    private suspend fun enableTraditionalAdb(
        executor: CommandExecutor,
        serial: String,
        port: Int,
    ): Boolean = executor.adb("-s", serial, "tcpip", port.toString()).isSuccess

    private suspend fun readProp(
        executor: CommandExecutor,
        serial: String,
        key: String,
    ): String {
        val result = executor.adb("-s", serial, "shell", "getprop", key)
        return if (result.isSuccess) result.output.trim() else ""
    }

    private fun extractWirelessIp(text: String, command: List<String>): String {
        if (text.isBlank()) {
            return ""
        }
        if (command.takeLast(2) == listOf("ip", "route")) {
            ROUTE_SRC_REGEX.find(text)?.value?.substringAfter("src ")?.let { ip ->
                if (ip.isNotBlank()) {
                    return ip
                }
            }
        }
        IP_ADDR_REGEX.find(text)?.groupValues?.getOrNull(1)?.let { ip ->
            if (ip.isNotBlank()) {
                return ip
            }
        }
        IFCONFIG_INET_ADDR_REGEX.find(text)?.groupValues?.getOrNull(1)?.let { ip ->
            if (ip.isNotBlank()) {
                return ip
            }
        }
        val match = IPV4_REGEX.find(text)
        return match?.value.orEmpty()
    }

    private fun mapCategory(characteristics: String): String {
        val value = characteristics.lowercase()
        return when {
            "tv" in value -> "tv"
            "watch" in value -> "watch"
            "tablet" in value -> "tablet"
            "automotive" in value || "car" in value -> "automotive"
            value.isNotBlank() -> "phone"
            else -> "unknown"
        }
    }

    private data class CacheEntry(
        val base: DeviceInfo,
        val enriched: DeviceInfo,
        val timestampMs: Long,
    )

    private companion object {
        const val CACHE_TTL_MS = 60_000L
        val ROUTE_SRC_REGEX = Regex("""src\s+((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})""")
        val IP_ADDR_REGEX = Regex("""inet\s+((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})""")
        val IFCONFIG_INET_ADDR_REGEX = Regex("""inet addr:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})""")
        val IPV4_REGEX = Regex("(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}")
    }
}

/** 优先复用设备已声明的 TCP ADB 端口，未声明时才使用传统默认端口。 */
internal fun selectTraditionalAdbPort(configuredPort: Int): Int =
    configuredPort.takeIf { it in 1..65535 } ?: DEFAULT_TRADITIONAL_ADB_PORT