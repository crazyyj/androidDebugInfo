package com.newchar.debug.pc.device

import com.newchar.debug.pc.device.scan.DeviceChangeEvent
import com.newchar.debug.pc.device.scan.DeviceChangeType
import com.newchar.debug.pc.device.scan.DeviceRefreshSource
import com.newchar.debug.pc.device.scan.DeviceScanConfig
import com.newchar.debug.pc.device.scan.DeviceScanManager
import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * 手机端发送到 PC 端的消息
 */
data class PcMessage(
    val deviceId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/** App 通过 PC 通道回传的 WiFi 状态。 */
data class AppWifiStatus(val enabled: Boolean, val ssid: String)

/**
 * 心跳保活 + adb reverse 管理器。
 *
 * 功能：
 * 1. USB 设备上线时自动设置 adb reverse 端口转发
 * 2. 启动本地 TCP Server 接收 App 通过 reverse 通道发来的心跳/上线消息
 * 3. WiFi 设备通过 adb shell ping 做心跳检测（120s 间隔）
 * 4. 心跳超时 → 触发重连
 */
class HeartbeatManager(
    private val executor: CommandExecutor,
    private val externalScope: CoroutineScope,
    private val scanManager: DeviceScanManager,
    private val config: DeviceScanConfig = DeviceScanConfig(),
    private val onHeartbeatTimeout: (DeviceInfo) -> Unit = {},
) {

    private val scope = CoroutineScope(externalScope.coroutineContext + SupervisorJob())

    // 手机端发送的消息通道
    private val _messages = MutableSharedFlow<PcMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<PcMessage> = _messages
    private var wifiCommandHandler: (suspend (String, String, String) -> String)? = null

    // 每个设备最近一次心跳时间戳
    private val lastHeartbeatAt = ConcurrentHashMap<String, Long>()
    private val wifiStatusWaiters = ConcurrentHashMap<String, CompletableDeferred<AppWifiStatus>>()

    // TCP Server 用于接收 App reverse 通道的心跳
    private var serverSocket: ServerSocket? = null
    private val clientSockets = mutableListOf<Socket>()

    // 心跳超时阈值（ms）：超过这个时间没收到心跳即认为断线
    private val heartbeatTimeoutMs = (config.wifiHeartbeatIntervalMs * 1.5).toLong()

    fun start() {
        // 启动 TCP Server 监听 adb reverse 通道
        // 必须使用 Dispatchers.IO，因为 ServerSocket.accept() 是阻塞调用，
        // 若在 Compose 的 FlushCoroutineDispatcher (EDT) 上执行会阻塞窗口创建和绘制
        scope.launch(Dispatchers.IO) { startReverseServer() }

        // 定期心跳检测（WiFi 设备 ping + 全设备超时检查）
        scope.launch { runHeartbeatLoop() }

        // 监听设备变化事件
        scope.launch {
            scanManager.changeEvents.collect { event ->
                handleEvent(event)
            }
        }

        // 初始化时检查已连接的 USB 设备，补设 adb reverse
        //（SharedFlow 无 replay，启动扫描时已连接的 ADDED 事件可能已发出，需兜底处理）
        scope.launch {
            delay(500) // 等待扫描完成，避免与 startup 扫描竞争
            val connectedDevices = scanManager.state.value.devices
            connectedDevices.forEach { device ->
                setupAdbReverse(device)
            }
        }
    }

    fun stop() {
        scope.cancel()
        stopReverseServer()
    }

    /**
     * 设置 App 发起 WiFi 操作时的 PC 端执行器。
     *
     * @param handler 参数依次为设备 ID、操作名和附加参数，返回协议结果（OK 或 ERROR 开头）
     */
    fun setWifiCommandHandler(handler: suspend (String, String, String) -> String) {
        wifiCommandHandler = handler
    }

    /** 返回设备 App 最近通过 adb reverse 通道联系 PC 的时间；0 表示尚未收到通信。 */
    fun lastContactAt(deviceId: String): Long = lastHeartbeatAt[deviceId] ?: 0L

    /** 兼容现有调用方的心跳时间读取接口。 */
    fun getLastHeartbeat(deviceId: String): Long = lastContactAt(deviceId)

    /**
     * 在 PC 无法读取 WiFi 状态时，请求设备 App 读取系统状态并回传。
     *
     * @param device 需要查询的设备
     * @return App 回传的 WiFi 状态；无回传时为失败结果
     */
    suspend fun requestAppWifiStatus(device: DeviceInfo): Result<AppWifiStatus> = runCatching {
        val waiter = CompletableDeferred<AppWifiStatus>()
        check(wifiStatusWaiters.putIfAbsent(device.id, waiter) == null) { "WiFi 状态请求正在进行" }
        try {
            withTimeout(APP_WIFI_STATUS_TIMEOUT_MS) { waiter.await() }
        } finally {
            wifiStatusWaiters.remove(device.id, waiter)
        }
    }

    // =========================================================================
    // adb reverse 设置
    // =========================================================================

    private suspend fun handleEvent(event: DeviceChangeEvent) {
        when (event.type) {
            DeviceChangeType.ADDED -> {
                val device = event.device
                scope.launch { setupAdbReverse(device) }
            }
            DeviceChangeType.REMOVED -> {
                val device = event.device
                scope.launch { teardownAdbReverse(device) }
                lastHeartbeatAt.remove(device.id)
            }
            else -> Unit
        }
    }

    private suspend fun setupAdbReverse(device: DeviceInfo) {
        val port = config.reverseLocalPort
        try {
            executor.adb("-s", device.adbTarget(), "reverse", "tcp:$port", "tcp:$port")
        } catch (t: Throwable) {
            // adb reverse 失败时不影响正常使用
        }
    }

    private suspend fun teardownAdbReverse(device: DeviceInfo) {
        val port = config.reverseLocalPort
        try {
            executor.adb("-s", device.adbTarget(), "reverse", "--remove", "tcp:$port")
        } catch (t: Throwable) {
            // ignore
        }
    }

    // =========================================================================
    // TCP Server — 接收 App reverse 心跳
    // =========================================================================

    private suspend fun startReverseServer() {
        val port = config.reverseLocalPort
        while (scope.isActive) {
            try {
                serverSocket = ServerSocket(port)
                serverSocket?.use { server ->
                    while (scope.isActive) {
                        val clientSocket = server.accept()
                        val client = clientSocket
                        clientSockets.add(client)
                        scope.launch {
                            handleClient(client)
                        }
                    }
                }
            } catch (t: Throwable) {
                delay(3000L)
            }
        }
    }

    private suspend fun handleClient(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.tcpNoDelay = true
                val inStream = DataInputStream(socket.getInputStream())
                val outStream = DataOutputStream(socket.getOutputStream())
                while (scope.isActive) {
                    val line = inStream.readUTF()
                    if (line.isBlank()) break
                    // 解析心跳消息：格式 "HEARTBEAT|deviceId|timestamp"
                    val parts = line.split('|')
                    if (parts.size >= 2 && parts[0] == "HEARTBEAT") {
                        val deviceId = parts[1]
                        val timestamp = parts.getOrNull(2)?.toLongOrNull() ?: System.currentTimeMillis()
                        lastHeartbeatAt[deviceId] = timestamp
                        outStream.writeUTF(buildHeartbeatReply(deviceId))
                        outStream.flush()
                    } else if (parts.size >= 2 && parts[0] == "ONLINE") {
                        // App 上线通知
                        val deviceId = parts[1]
                        val wifiIp = parts.getOrNull(2) ?: ""
                        val wifiSsid = parts.getOrNull(3) ?: ""
                        lastHeartbeatAt[deviceId] = System.currentTimeMillis()
                        outStream.writeUTF("ACK")
                        outStream.flush()
                        // 刷新设备信息以更新 IP
                        refreshNow(DeviceRefreshSource.MANUAL)
                    } else if (parts.size >= 3 && parts[0] == "MESSAGE") {
                        // 手机端发送的消息：格式 "MESSAGE|deviceId|content"
                        val deviceId = parts[1]
                        val content = parts.subList(2, parts.size).joinToString("|")
                        lastHeartbeatAt[deviceId] = System.currentTimeMillis()
                        outStream.writeUTF("ACK")
                        outStream.flush()
                        runCatching {
                            _messages.emit(PcMessage(deviceId = deviceId, content = content))
                        }
                    } else if (parts.size >= 3 && parts[0] == "WIFI_CMD") {
                        val deviceId = parts[1]
                        val action = parts[2]
                        val params = parts.drop(3).joinToString("|")
                        lastHeartbeatAt[deviceId] = System.currentTimeMillis()
                        val result = wifiCommandHandler?.invoke(deviceId, action, params)
                            ?: "ERROR|PC 未启用 WiFi 控制"
                        outStream.writeUTF("WIFI_RESULT|$deviceId|$result")
                        outStream.flush()
                    } else if (parts.size >= 4 && parts[0] == "WIFI_STATUS") {
                        handleAppWifiStatus(parts, outStream)
                    }
                }
            }.onFailure {
                // 客户端断开
            }
            clientSockets.remove(client)
        }
    }

    private fun stopReverseServer() {
        runCatching { serverSocket?.close() }
        clientSockets.forEach { runCatching { it.close() } }
        clientSockets.clear()
    }

    /** 接收 App 的 WiFi 状态回传，并唤醒对应的状态请求。 */
    private fun handleAppWifiStatus(parts: List<String>, outStream: DataOutputStream) {
        val deviceId = parts[1]
        val ssid = runCatching { String(Base64.getUrlDecoder().decode(parts.getOrNull(3).orEmpty())) }.getOrDefault("")
        wifiStatusWaiters.remove(deviceId)?.complete(AppWifiStatus(parts[2].toBoolean(), ssid))
        lastHeartbeatAt[deviceId] = System.currentTimeMillis()
        outStream.writeUTF("ACK")
        outStream.flush()
    }

    /** 在对应设备存在等待任务时，将状态读取请求附在本次心跳响应中。 */
    private fun buildHeartbeatReply(deviceId: String): String =
        if (wifiStatusWaiters.containsKey(deviceId)) "WIFI_STATUS_REQUEST" else "ACK"

    // =========================================================================
    // 心跳循环
    // =========================================================================

    private suspend fun runHeartbeatLoop() {
        while (scope.isActive) {
            try {
                checkHeartbeatTimeout()
                // 对 WiFi 设备做主动 ping
                val state = scanManager.state.value
                state.devices
                    .filter { it.isNetworkDevice }
                    .forEach { device ->
                        scope.launch { pingWifiDevice(device) }
                    }
            } catch (t: Throwable) {
                // ignore
            }
            delay(config.wifiHeartbeatIntervalMs)
        }
    }

    private suspend fun pingWifiDevice(device: DeviceInfo) {
        val last = lastHeartbeatAt[device.id] ?: 0L
        if (System.currentTimeMillis() - last < config.wifiHeartbeatIntervalMs) {
            return
        }
        // 尝试通过 adb shell ping 检测设备是否还活着
        runCatching {
            executor.shell(deviceId = device.id, "ping", "-c", "1", "-W", "3", "127.0.0.1")
            lastHeartbeatAt[device.id] = System.currentTimeMillis()
        }.onFailure {
            // ping 失败 → 心跳超时
            onHeartbeatTimeout(device)
        }
    }

    private suspend fun checkHeartbeatTimeout() {
        val now = System.currentTimeMillis()
        val timeoutDeviceIds = lastHeartbeatAt
            .filter { it.value < now - heartbeatTimeoutMs }
            .keys

        timeoutDeviceIds.forEach { deviceId ->
            val state = scanManager.state.value
            val device = state.devices.find { it.id == deviceId } ?: return@forEach
            lastHeartbeatAt.remove(deviceId)
            onHeartbeatTimeout(device)
        }
    }

    private companion object {
        const val APP_WIFI_STATUS_TIMEOUT_MS = 5_000L
    }

    // =========================================================================
    // 工具
    // =========================================================================

    private suspend fun refreshNow(source: DeviceRefreshSource) {
        runCatching { scanManager.refreshNow(source) }
    }
}
