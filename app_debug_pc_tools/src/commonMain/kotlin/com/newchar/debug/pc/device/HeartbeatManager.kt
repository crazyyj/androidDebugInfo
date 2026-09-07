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
import java.util.concurrent.ConcurrentHashMap

/**
 * 手机端发送到 PC 端的消息
 */
data class PcMessage(
    val deviceId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

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

    // 每个设备最近一次心跳时间戳
    private val lastHeartbeatAt = ConcurrentHashMap<String, Long>()

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
            val usbDevices = scanManager.state.value.devices.filter { !it.isNetworkDevice }
            usbDevices.forEach { device ->
                setupAdbReverse(device)
            }
        }
    }

    fun stop() {
        scope.cancel()
        stopReverseServer()
    }

    /** 返回设备 App 最近通过 adb reverse 通道联系 PC 的时间；0 表示尚未收到通信。 */
    fun lastContactAt(deviceId: String): Long = lastHeartbeatAt[deviceId] ?: 0L

    /** 兼容现有调用方的心跳时间读取接口。 */
    fun getLastHeartbeat(deviceId: String): Long = lastContactAt(deviceId)

    // =========================================================================
    // adb reverse 设置
    // =========================================================================

    private suspend fun handleEvent(event: DeviceChangeEvent) {
        when (event.type) {
            DeviceChangeType.ADDED -> {
                val device = event.device
                if (!device.isNetworkDevice) {
                    // USB 设备上线 → 设置 adb reverse
                    scope.launch { setupAdbReverse(device) }
                }
            }
            DeviceChangeType.REMOVED -> {
                val device = event.device
                if (!device.isNetworkDevice) {
                    // USB 设备下线 → 清除 reverse
                    scope.launch { teardownAdbReverse(device) }
                    lastHeartbeatAt.remove(device.id)
                }
            }
            else -> Unit
        }
    }

    private suspend fun setupAdbReverse(device: DeviceInfo) {
        val port = config.reverseLocalPort
        try {
            executor.adb("-s", device.id, "reverse", "tcp:$port", "tcp:$port")
        } catch (t: Throwable) {
            // adb reverse 失败时不影响正常使用
        }
    }

    private suspend fun teardownAdbReverse(device: DeviceInfo) {
        val port = config.reverseLocalPort
        try {
            executor.adb("-s", device.id, "reverse", "--remove", "tcp:$port")
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
                        // 回复 ACK
                        outStream.writeUTF("ACK")
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

    // =========================================================================
    // 工具
    // =========================================================================

    private suspend fun refreshNow(source: DeviceRefreshSource) {
        runCatching { scanManager.refreshNow(source) }
    }
}
