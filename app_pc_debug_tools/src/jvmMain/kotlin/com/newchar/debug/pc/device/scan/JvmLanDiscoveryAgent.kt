package com.newchar.debug.pc.device.scan

import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlin.math.min

class JvmLanDiscoveryAgent : LanDiscoveryAgent {

    private val lastAttemptAt = linkedMapOf<String, Long>()

    override suspend fun discover(
        executor: CommandExecutor,
        state: DeviceScanState,
        config: DeviceScanConfig,
    ): LanDiscoveryResult = coroutineScope {
        val subnets = localIpv4Subnets(config.maxLanHostsPerSubnet)
        if (subnets.isEmpty()) {
            return@coroutineScope LanDiscoveryResult(messages = listOf("未检测到可扫描的局域网网卡"))
        }

        val knownEndpoints = state.devices.mapTo(mutableSetOf()) { it.id }
        val semaphore = Semaphore(config.lanMaxParallelHosts)
        val candidateEndpoints = linkedSetOf<String>()

        subnets.flatMap { subnet -> subnet.hosts }
            .map { host ->
                async {
                    semaphore.withPermit {
                        probeHost(host, config.lanProbeTimeoutMs)
                    }
                }
            }
            .awaitAll()
            .flatten()
            .forEach(candidateEndpoints::add)

        val now = System.currentTimeMillis()
        val messages = mutableListOf<String>()
        var refreshRequested = false

        candidateEndpoints
            .filterNot { it in knownEndpoints }
            .forEach { endpoint ->
                val lastAttempt = lastAttemptAt[endpoint] ?: 0L
                if (now - lastAttempt < config.lanReconnectCooldownMs) {
                    return@forEach
                }
                lastAttemptAt[endpoint] = now
                val connectResult = executor.adb("connect", endpoint)
                val output = connectResult.output.ifBlank { connectResult.error }
                if (connectResult.isSuccess || output.contains("connected to", ignoreCase = true) || output.contains("already connected to", ignoreCase = true)) {
                    refreshRequested = true
                    messages += "LAN connect $endpoint success"
                } else {
                    messages += "LAN connect $endpoint failed: $output"
                }
            }

        LanDiscoveryResult(
            refreshRequested = refreshRequested,
            messages = messages.take(config.maxRecentLanMessages),
            scannedSubnets = subnets.map { it.label },
            discoveredEndpoints = candidateEndpoints.toList(),
        )
    }

    private fun localIpv4Subnets(maxHostsPerSubnet: Int): List<Ipv4Subnet> {
        val result = mutableListOf<Ipv4Subnet>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) {
                continue
            }
            networkInterface.interfaceAddresses
                .mapNotNull { address ->
                    val inetAddress = address.address as? Inet4Address ?: return@mapNotNull null
                    if (!inetAddress.isSiteLocalAddress) {
                        return@mapNotNull null
                    }
                    val prefixLength = address.networkPrefixLength.toInt().coerceIn(1, 30)
                    buildSubnet(inetAddress.address, prefixLength, maxHostsPerSubnet)
                }
                .forEach(result::add)
        }
        return result.distinctBy { it.label }
    }

    private fun buildSubnet(
        addressBytes: ByteArray,
        prefixLength: Int,
        maxHostsPerSubnet: Int,
    ): Ipv4Subnet? {
        if (addressBytes.size != 4) {
            return null
        }
        val ipValue = bytesToInt(addressBytes)
        val hostBits = 32 - prefixLength
        val mask = if (prefixLength == 0) 0 else (-1 shl hostBits)
        val network = ipValue and mask
        val broadcast = if (hostBits == 0) network else network or ((1 shl hostBits) - 1)
        val start = if (hostBits <= 1) network else network + 1
        val end = if (hostBits <= 1) broadcast else broadcast - 1
        if (end < start) {
            return null
        }

        val localHost = ipValue
        val totalHosts = end - start + 1
        val scanStart = if (totalHosts <= maxHostsPerSubnet) {
            start
        } else {
            val halfWindow = maxHostsPerSubnet / 2
            val localWindowStart = (localHost - halfWindow).coerceAtLeast(start)
            val localWindowEnd = min(end.toLong(), localWindowStart.toLong() + maxHostsPerSubnet - 1).toInt()
            (localWindowEnd - maxHostsPerSubnet + 1).coerceAtLeast(start)
        }
        val scanEnd = min(end.toLong(), scanStart.toLong() + maxHostsPerSubnet - 1).toInt()
        val hosts = (scanStart..scanEnd)
            .filterNot { it == localHost }
            .map(::intToIpv4)

        return Ipv4Subnet(
            label = "${intToIpv4(network)}/$prefixLength near ${intToIpv4(localHost)}",
            hosts = hosts,
        )
    }

    private fun probeHost(host: String, timeoutMs: Int): List<String> {
        val ports = listOf(5555, 5554)
        return ports.mapNotNull { port ->
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                "$host:$port"
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    private fun bytesToInt(bytes: ByteArray): Int {
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    private fun intToIpv4(value: Int): String {
        return listOf(
            value ushr 24 and 0xFF,
            value ushr 16 and 0xFF,
            value ushr 8 and 0xFF,
            value and 0xFF,
        ).joinToString(separator = ".")
    }

    private data class Ipv4Subnet(
        val label: String,
        val hosts: List<String>,
    )
}
