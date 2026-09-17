package com.newchar.debug.pc.device.scan

import java.net.*
import java.util.*

/**
 * PC 端 WiFi 网络检测工具。
 * 用于判断 PC 当前是否连接了 WiFi，以及设备 IP 是否与 PC 在同一网段。
 */
object JvmWifiDetector {

    /** PC 当前 WiFi 网卡的 IP 信息 */
    @Volatile
    private var _wifiIp: String? = null
    @Volatile
    private var _wifiSubnetMask: String? = null
    @Volatile
    private var _wifiInterfaceName: String? = null
    @Volatile
    private var _isWifiConnected: Boolean = false

    /** WiFi IP（如 "192.168.1.100"） */
    val wifiIp: String? get() = _wifiIp

    /** WiFi 子网掩码（如 "255.255.255.0"） */
    val wifiSubnetMask: String? get() = _wifiSubnetMask

    /** WiFi 网卡名称（如 "wlan0" 或 "Wi-Fi"） */
    val wifiInterfaceName: String? get() = _wifiInterfaceName

    /** 是否连接了 WiFi */
    val isWifiConnected: Boolean get() = _isWifiConnected

    /**
     * 刷新 WiFi 状态检测。
     */
    fun refresh() {
        val networkInterfaces = NetworkInterface.getNetworkInterfaces() ?: run {
            _wifiIp = null
            _wifiSubnetMask = null
            _wifiInterfaceName = null
            _isWifiConnected = false
            return
        }

        var bestIp: String? = null
        var bestMask: String? = null
        var bestName: String? = null
        var bestScore = 0

        val wifiKeywords = listOf("wlan", "wifi", "wireless", "atwlan", "en0")
        val wifiKeywordsUpper = wifiKeywords.map(String::uppercase)

        while (networkInterfaces.hasMoreElements()) {
            val networkInterface = networkInterfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) {
                continue
            }

            val interfaceName = networkInterface.displayName ?: networkInterface.name
            val nameUpper = interfaceName.uppercase()

            val isWifiInterface = wifiKeywordsUpper.any { keyword ->
                nameUpper.contains(keyword)
            }

            networkInterface.interfaceAddresses.forEach { address ->
                val inetAddress = address.address ?: return@forEach
                if (inetAddress !is Inet4Address || !inetAddress.isSiteLocalAddress) {
                    return@forEach
                }

                val prefixLength = address.networkPrefixLength.toInt()
                val prefixScore = if (isWifiInterface) 10 + prefixLength else prefixLength

                if (prefixScore > bestScore) {
                    bestScore = prefixScore
                    bestIp = inetAddress.hostAddress
                    bestMask = prefixLength.toString()
                    bestName = interfaceName
                }
            }
        }

        _wifiIp = bestIp
        _wifiSubnetMask = bestMask
        _wifiInterfaceName = bestName
        _isWifiConnected = bestIp != null
    }

    /**
     * 使用 PC 网卡的实际前缀长度，判断设备 IP 是否位于同一 IPv4 子网。
     *
     * @param deviceIp 设备的无线 IP
     * @return 同网段 true，否则 false。PC 无 WiFi 连接时返回 false。
     */
    fun isSameSubnet(deviceIp: String): Boolean {
        if (deviceIp.isBlank() || !isWifiConnected) {
            return false
        }
        val pcAddress = ipv4ToInt(_wifiIp.orEmpty()) ?: return false
        val deviceAddress = ipv4ToInt(deviceIp) ?: return false
        val prefixLength = _wifiSubnetMask?.toIntOrNull()?.coerceIn(0, 32) ?: return false
        val mask = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)
        return (pcAddress and mask) == (deviceAddress and mask)
    }

    /** 将点分十进制 IPv4 地址转为可做掩码比较的 32 位整数。 */
    private fun ipv4ToInt(ip: String): Int? {
        val octets = ip.split('.')
        if (octets.size != 4) return null
        return octets.fold(0) { value, text ->
            val octet = text.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            value shl 8 or octet
        }
    }
}
