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

                val prefixLength = address.networkPrefixLength
                val prefixScore = if (isWifiInterface) 10 + prefixLength else prefixLength

                if (prefixScore > bestScore) {
                    bestScore = prefixScore
                    bestIp = inetAddress.hostAddress
                    bestMask = intToIpAddress(~(-1 shl (32 - prefixLength)).toInt()).let { mask ->
                        prefixLength
                    }
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
     * 判断设备 IP 是否与 PC 在同一 /24 子网。
     *
     * @param deviceIp 设备的无线 IP
     * @return 同网段 true，否则 false。PC 无 WiFi 连接时返回 false。
     */
    fun isSameSubnet(deviceIp: String): Boolean {
        if (deviceIp.isBlank() || !isWifiConnected) {
            return false
        }
        val pcParts = _wifiIp?.split('.')?.map { it.toInt() } ?: return false
        val deviceParts = deviceIp.split('.')
        if (pcParts.size != 4 || deviceParts.size != 4) {
            return false
        }
        deviceParts.forEachIndexed { index, part ->
            val deviceOctet = part.toIntOrNull() ?: return false
            if (pcParts[index] != deviceOctet) {
                return false
            }
        }
        return true
    }

    private fun intToIpAddress(value: Int): String {
        return listOf(
            value ushr 24 and 0xFF,
            value ushr 16 and 0xFF,
            value ushr 8 and 0xFF,
            value and 0xFF,
        ).joinToString(separator = ".")
    }
}
