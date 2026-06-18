package com.newchar.debug.pc.device.scan

import com.newchar.debug.pc.device.AdbDeviceParser
import com.newchar.debug.pc.device.CommandResult
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class DeviceScanManager(
    private val executor: CommandExecutor,
    private val externalScope: CoroutineScope,
    private val config: DeviceScanConfig = DeviceScanConfig(),
    private val deviceMetadataResolver: DeviceMetadataResolver? = null,
    private val lanDiscoveryAgent: LanDiscoveryAgent? = null,
    private val knownEndpointProvider: () -> List<String> = { emptyList() },
) {
    private val scope = CoroutineScope(externalScope.coroutineContext + SupervisorJob())
    private val refreshMutex = Mutex()
    private val attemptedConnectEndpoints = linkedMapOf<String, Long>()

    private var trackJob: Job? = null
    private var pollingJob: Job? = null
    private var mdnsJob: Job? = null
    private var lanJob: Job? = null

    private val _state = MutableStateFlow(DeviceScanState())
    val state: StateFlow<DeviceScanState> = _state.asStateFlow()

    private val _changeEvents = MutableSharedFlow<DeviceChangeEvent>(extraBufferCapacity = 32)
    val changeEvents: SharedFlow<DeviceChangeEvent> = _changeEvents.asSharedFlow()

    fun start() {
        if (trackJob != null || pollingJob != null || mdnsJob != null || lanJob != null) {
            return
        }
        trackJob = scope.launch(Dispatchers.Default) { runTrackDevicesLoop() }
        pollingJob = scope.launch(Dispatchers.Default) { runPollingLoop() }
        mdnsJob = scope.launch(Dispatchers.Default) { runMdnsLoop() }
        if (lanDiscoveryAgent != null) {
            lanJob = scope.launch(Dispatchers.Default) { runLanLoop() }
        }
        scope.launch(Dispatchers.Default) { refreshNow(DeviceRefreshSource.STARTUP) }
    }

    suspend fun stop() {
        trackJob?.cancelAndJoin()
        pollingJob?.cancelAndJoin()
        mdnsJob?.cancelAndJoin()
        lanJob?.cancelAndJoin()
        trackJob = null
        pollingJob = null
        mdnsJob = null
        lanJob = null
    }

    fun cancel() {
        scope.cancel()
    }

    suspend fun refreshNow(source: DeviceRefreshSource = DeviceRefreshSource.MANUAL) {
        refreshMutex.withLock {
            val previousState = _state.value
            val result = executor.adb("devices", "-l")
            if (!result.isSuccess) {
                updateError(result.error.ifBlank { result.output.ifBlank { "adb devices -l failed" } }, source)
                return
            }
            val parsedDevices = AdbDeviceParser.parseDeviceList(result.output)
            val activeDevices = deviceMetadataResolver?.enrich(executor, parsedDevices) ?: parsedDevices
            val devices = mergeRetainedWirelessDevices(previousState.devices, activeDevices)
            val changeEvents = buildChangeEvents(previousState.devices, devices, source)
            changeEvents.forEach { _changeEvents.tryEmit(it) }
            _state.value = previousState.copy(
                devices = devices,
                lastError = null,
                lastRefreshSource = source,
                recentChanges = (changeEvents + previousState.recentChanges).take(config.maxRecentChanges),
            )
        }
    }

    private suspend fun runTrackDevicesLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                executor.adbStream("track-devices").collect {
                    currentCoroutineContext().ensureActive()
                    refreshNow(DeviceRefreshSource.TRACK_DEVICES)
                }
                delay(config.trackRestartDelayMs)
            } catch (throwable: Throwable) {
                updateError("track-devices failed: ${throwable.message.orEmpty()}", DeviceRefreshSource.TRACK_DEVICES)
                delay(config.trackRestartDelayMs)
            }
        }
    }

    private suspend fun runPollingLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                refreshNow(DeviceRefreshSource.POLLING)
            } catch (throwable: Throwable) {
                updateError("poll devices failed: ${throwable.message.orEmpty()}", DeviceRefreshSource.POLLING)
            }
            delay(config.pollIntervalMs)
        }
    }

    private suspend fun runMdnsLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                discoverMdnsAndConnect()
            } catch (throwable: Throwable) {
                updateError("mdns discovery failed: ${throwable.message.orEmpty()}", DeviceRefreshSource.MDNS_DISCOVERY)
            }
            delay(config.mdnsIntervalMs)
        }
    }

    private suspend fun runLanLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                discoverLanAndConnect()
            } catch (throwable: Throwable) {
                updateError("lan discovery failed: ${throwable.message.orEmpty()}", DeviceRefreshSource.LAN_SCAN)
            }
            delay(config.lanScanIntervalMs)
        }
    }

    private suspend fun discoverMdnsAndConnect() {
        val previousState = _state.value
        val result = executor.adb("mdns", "services")
        if (!result.isSuccess) {
            return
        }
        val services = parseMdnsServices(result.output)
        val mdnsMessages = mutableListOf<String>()

        val now = Clock.System.now().toEpochMilliseconds()
        val visibleEndpoints = services.mapTo(mutableSetOf()) { it.endpoint }
        attemptedConnectEndpoints.keys.retainAll(visibleEndpoints)

        val knownEndpoints = previousState.devices.mapTo(mutableSetOf()) { it.id }
        services
            .filter { it.isConnectService }
            .filterNot { it.endpoint in knownEndpoints }
            .forEach { service ->
                val lastAttemptTime = attemptedConnectEndpoints[service.endpoint] ?: 0L
                if (now - lastAttemptTime < config.mdnsReconnectCooldownMs) {
                    return@forEach
                }
                attemptedConnectEndpoints[service.endpoint] = now
                val connectResult = executor.adb("connect", service.endpoint)
                if (isSuccessfulConnect(connectResult)) {
                    mdnsMessages += "connect ${service.endpoint} success"
                    refreshNow(DeviceRefreshSource.MDNS_CONNECT)
                } else {
                    mdnsMessages += "connect ${service.endpoint} failed: ${connectResult.error.ifBlank { connectResult.output }}"
                }
            }

        val currentState = _state.value
        _state.value = currentState.copy(
            mdnsServices = services,
            lastRefreshSource = currentState.lastRefreshSource ?: DeviceRefreshSource.MDNS_DISCOVERY,
            recentMdnsMessages = (mdnsMessages + currentState.recentMdnsMessages).take(config.maxRecentMdnsMessages),
        )
    }

    private suspend fun discoverLanAndConnect() {
        val retainedResult = connectKnownWirelessEndpoints(_state.value)
        val agent = lanDiscoveryAgent
        val lanResult = agent?.discover(executor, _state.value, config) ?: LanDiscoveryResult()
        if (retainedResult.refreshRequested || lanResult.refreshRequested) {
            refreshNow(DeviceRefreshSource.LAN_SCAN)
        }
        val mergedMessages = (retainedResult.messages + lanResult.messages).take(config.maxRecentLanMessages)
        val mergedSubnets = (retainedResult.scannedSubnets + lanResult.scannedSubnets).distinct()
        val mergedEndpoints = (retainedResult.discoveredEndpoints + lanResult.discoveredEndpoints).distinct()
        if (mergedMessages.isNotEmpty() || mergedSubnets.isNotEmpty() || mergedEndpoints.isNotEmpty()) {
            val currentState = _state.value
            _state.value = currentState.copy(
                lastRefreshSource = DeviceRefreshSource.LAN_SCAN,
                recentLanMessages = (mergedMessages + currentState.recentLanMessages).take(config.maxRecentLanMessages),
                scannedLanSubnets = mergedSubnets.ifEmpty { currentState.scannedLanSubnets },
                discoveredLanEndpoints = (mergedEndpoints + currentState.discoveredLanEndpoints).distinct().take(config.maxDiscoveredLanEndpoints),
            )
        }
    }

    private fun isSuccessfulConnect(result: CommandResult): Boolean {
        val text = result.output.ifBlank { result.error }
        return result.isSuccess || text.contains("connected to", ignoreCase = true) ||
            text.contains("already connected to", ignoreCase = true)
    }

    private fun parseMdnsServices(output: String): List<AdbMdnsService> {
        return output.lines()
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("List of discovered mdns services", ignoreCase = true) }
            .mapNotNull { line ->
                val tokens = line.split(Regex("\\s+"))
                if (tokens.size < 2) {
                    return@mapNotNull null
                }
                val instanceName = tokens.first()
                val endpoint = tokens.last().removePrefix("[").removeSuffix("]")
                val host = endpoint.substringBefore(':', missingDelimiterValue = endpoint)
                val port = endpoint.substringAfter(':', missingDelimiterValue = "").toIntOrNull() ?: 0
                val serviceType = instanceName.substringAfter('.', missingDelimiterValue = "")
                AdbMdnsService(
                    instanceName = instanceName,
                    serviceType = serviceType,
                    host = host,
                    port = port,
                    endpoint = endpoint,
                )
            }
    }

    private fun buildChangeEvents(
        oldDevices: List<DeviceInfo>,
        newDevices: List<DeviceInfo>,
        source: DeviceRefreshSource,
    ): List<DeviceChangeEvent> {
        val oldMap = oldDevices.associateBy(DeviceInfo::id)
        val newMap = newDevices.associateBy(DeviceInfo::id)
        val events = mutableListOf<DeviceChangeEvent>()

        newMap.forEach { (id, device) ->
            val old = oldMap[id]
            when {
                old == null -> events += DeviceChangeEvent(DeviceChangeType.ADDED, source, device, "device added")
                old != device -> events += DeviceChangeEvent(DeviceChangeType.CHANGED, source, device, "device changed")
            }
        }

        oldMap.forEach { (id, device) ->
            if (id !in newMap) {
                events += DeviceChangeEvent(DeviceChangeType.REMOVED, source, device, "device removed")
            }
        }
        return events.sortedByDescending { it.timestampMs }
    }

    private fun mergeRetainedWirelessDevices(
        previousDevices: List<DeviceInfo>,
        activeDevices: List<DeviceInfo>,
    ): List<DeviceInfo> {
        val activeIds = activeDevices.mapTo(linkedSetOf()) { it.id }
        val activeNetworkEndpoints = activeDevices
            .filter(DeviceInfo::isNetworkDevice)
            .mapTo(linkedSetOf()) { it.id }
        val retainedDevices = previousDevices.mapNotNull { previous ->
            when {
                previous.id in activeIds -> null
                previous.isNetworkDevice -> null
                !previous.canTryWirelessConnect -> null
                previous.wirelessEndpoint in activeNetworkEndpoints -> null
                else -> previous.copy(status = "offline", isRetainedOffline = true)
            }
        }
        return (activeDevices + retainedDevices)
            .distinctBy(DeviceInfo::id)
            .sortedWith(compareBy<DeviceInfo> { it.isRetainedOffline }.thenBy { it.model.ifBlank { it.id } })
    }

    private suspend fun connectKnownWirelessEndpoints(state: DeviceScanState): LanDiscoveryResult {
        val retainedEndpoints = state.devices
            .filter { it.isRetainedOffline && it.canTryWirelessConnect }
            .mapNotNull { it.wirelessEndpoint.takeIf(String::isNotBlank) }
        val knownEndpointsToRetry = knownEndpointProvider()
            .map(String::trim)
            .filter(String::isNotBlank)
        val endpoints = (retainedEndpoints + knownEndpointsToRetry)
            .distinct()
        if (endpoints.isEmpty()) {
            return LanDiscoveryResult()
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val knownEndpoints = state.devices
            .filterNot(DeviceInfo::isRetainedOffline)
            .mapTo(mutableSetOf()) { it.id }
        val messages = mutableListOf<String>()
        var refreshRequested = false
        endpoints.forEach { endpoint ->
            if (endpoint.isBlank() || endpoint in knownEndpoints) {
                return@forEach
            }
            val lastAttempt = attemptedConnectEndpoints[endpoint] ?: 0L
            if (now - lastAttempt < config.lanReconnectCooldownMs) {
                return@forEach
            }
            attemptedConnectEndpoints[endpoint] = now
            val connectResult = executor.adb("connect", endpoint)
            if (isSuccessfulConnect(connectResult)) {
                refreshRequested = true
                messages += "USB cache connect $endpoint success"
            } else {
                val reason = connectResult.error.ifBlank { connectResult.output.ifBlank { "unknown error" } }
                messages += "USB cache connect $endpoint failed: $reason"
            }
        }
        return LanDiscoveryResult(
            refreshRequested = refreshRequested,
            messages = messages.take(config.maxRecentLanMessages),
            discoveredEndpoints = endpoints,
        )
    }

    private fun updateError(error: String, source: DeviceRefreshSource) {
        if (error.isBlank()) {
            return
        }
        _state.value = _state.value.copy(lastError = error, lastRefreshSource = source)
    }
}

data class DeviceScanConfig(
    val pollIntervalMs: Long = 5_000L,
    val mdnsIntervalMs: Long = 15_000L,
    val lanScanIntervalMs: Long = 30_000L,
    val trackRestartDelayMs: Long = 1_500L,
    val mdnsReconnectCooldownMs: Long = 30_000L,
    val lanReconnectCooldownMs: Long = 90_000L,
    val lanProbeTimeoutMs: Int = 500,
    val lanMaxParallelHosts: Int = 48,
    val maxLanHostsPerSubnet: Int = 512,
    val maxRecentChanges: Int = 20,
    val maxRecentMdnsMessages: Int = 10,
    val maxRecentLanMessages: Int = 10,
    val maxDiscoveredLanEndpoints: Int = 20,
)

data class DeviceScanState(
    val devices: List<DeviceInfo> = emptyList(),
    val mdnsServices: List<AdbMdnsService> = emptyList(),
    val recentChanges: List<DeviceChangeEvent> = emptyList(),
    val recentMdnsMessages: List<String> = emptyList(),
    val recentLanMessages: List<String> = emptyList(),
    val scannedLanSubnets: List<String> = emptyList(),
    val discoveredLanEndpoints: List<String> = emptyList(),
    val lastRefreshSource: DeviceRefreshSource? = null,
    val lastError: String? = null,
)

data class AdbMdnsService(
    val instanceName: String,
    val serviceType: String,
    val host: String,
    val port: Int,
    val endpoint: String,
) {
    val isConnectService: Boolean
        get() = serviceType.contains("_adb-tls-connect._tcp") || serviceType.contains("_adb._tcp")
}

enum class DeviceRefreshSource {
    STARTUP,
    MANUAL,
    TRACK_DEVICES,
    POLLING,
    MDNS_DISCOVERY,
    MDNS_CONNECT,
    LAN_SCAN,
}

enum class DeviceChangeType {
    ADDED,
    REMOVED,
    CHANGED,
}

data class DeviceChangeEvent(
    val type: DeviceChangeType,
    val source: DeviceRefreshSource,
    val device: DeviceInfo,
    val summary: String,
    val timestampMs: Long = Clock.System.now().toEpochMilliseconds(),
)
