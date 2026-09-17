package com.newchar.debug.pc.device.stream

/** 流的业务类别；屏幕与相机各自维护独立会话。 */
enum class StreamKind {
    SCREEN,
    CAMERA,
}

/** 推流数据当前经过的传输方式。 */
enum class StreamTransportKind {
    REVERSE,
    DIRECT,
}

/**
 * 与 ADB 连接状态完全独立的推流状态。
 *
 * 状态只表达会话健康度与路由选择；socket、ADB 与 UI 副作用由 JVM/Android 层负责。
 */
sealed class StreamTransportState {
    /** 会话尚未创建。 */
    data object Stopped : StreamTransportState()

    /** 数据正通过 ADB reverse 传输。 */
    data class ReverseActive(val sessionId: String) : StreamTransportState()

    /** 数据正通过设备到 PC 的局域网直连传输。 */
    data class DirectActive(val sessionId: String, val pcHost: String) : StreamTransportState()

    /** 连接已断开，设备端正在按退避策略尝试重建 socket。 */
    data class Reconnecting(val sessionId: String, val lastTransport: StreamTransportKind) : StreamTransportState()

    /** 会话已终止，保留可展示的失败原因。 */
    data class Failed(val sessionId: String, val reason: String) : StreamTransportState()
}

/** 会话层向纯状态机提交的传输事件。 */
sealed class StreamTransportEvent {
    /** 用户创建并完成配置的会话。 */
    data class Started(val sessionId: String) : StreamTransportEvent()

    /** 收流端完成握手并收到首帧。 */
    data class FirstFrame(val sessionId: String, val transport: StreamTransportKind, val pcHost: String = "") : StreamTransportEvent()

    /** 收流端收到合法帧，用于独立于 ADB 心跳的健康度统计。 */
    data class FrameReceived(val sessionId: String) : StreamTransportEvent()

    /** 当前 socket 已关闭，设备端会按自身策略重连。 */
    data class SocketLost(val sessionId: String, val lastTransport: StreamTransportKind) : StreamTransportEvent()

    /** 设备端无法继续当前会话。 */
    data class Failed(val sessionId: String, val reason: String) : StreamTransportEvent()

    /** 用户主动停止会话。 */
    data object Stopped : StreamTransportEvent()
}

/** 按事件归约会话状态；ADB 在线状态不参与此处判断。 */
fun StreamTransportState.reduce(event: StreamTransportEvent): StreamTransportState = when (event) {
    is StreamTransportEvent.Started -> StreamTransportState.ReverseActive(event.sessionId)
    is StreamTransportEvent.FirstFrame -> when (event.transport) {
        StreamTransportKind.REVERSE -> StreamTransportState.ReverseActive(event.sessionId)
        StreamTransportKind.DIRECT -> StreamTransportState.DirectActive(event.sessionId, event.pcHost)
    }
    is StreamTransportEvent.FrameReceived -> this
    is StreamTransportEvent.SocketLost -> StreamTransportState.Reconnecting(event.sessionId, event.lastTransport)
    is StreamTransportEvent.Failed -> StreamTransportState.Failed(event.sessionId, event.reason)
    StreamTransportEvent.Stopped -> StreamTransportState.Stopped
}

/** PC 在 ADB 在线时下发给设备端的无状态会话配置。 */
data class StreamSessionConfig(
    val sessionId: String,
    val kind: StreamKind,
    val directHost: String,
    val directPort: Int,
    val reversePort: Int,
    val token: ByteArray,
    val allowDirectFallback: Boolean = true,
)