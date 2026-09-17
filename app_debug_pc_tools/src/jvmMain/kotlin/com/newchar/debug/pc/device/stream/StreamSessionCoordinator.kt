package com.newchar.debug.pc.device.stream

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 按“物理设备 + 流类别”持有传输状态的轻量协调器。
 *
 * 该类只归约 PC 可观测到的事件；ADB 断开不主动停止健康的直连 socket。
 */
class StreamSessionCoordinator {
    private val sessions = linkedMapOf<StreamSessionKey, MutableStateFlow<StreamTransportState>>()
    private val lastFrameAt = linkedMapOf<StreamSessionKey, Long>()

    /** 获取指定设备流的可观察状态；尚未启动时为 [StreamTransportState.Stopped]。 */
    fun stateOf(key: StreamSessionKey): StateFlow<StreamTransportState> {
        return sessions.getOrPut(key) { MutableStateFlow(StreamTransportState.Stopped) }.asStateFlow()
    }

    /** 将收流、断线和用户停止事件归约为当前状态。 */
    fun dispatch(key: StreamSessionKey, event: StreamTransportEvent) {
        val state = sessions.getOrPut(key) { MutableStateFlow(StreamTransportState.Stopped) }
        state.value = state.value.reduce(event)
        if (event is StreamTransportEvent.FrameReceived) lastFrameAt[key] = System.currentTimeMillis()
    }

    /** 返回该会话最后一帧到达时间；不能以 ADB heartbeat 替代此值。 */
    fun lastFrameAt(key: StreamSessionKey): Long = lastFrameAt[key] ?: 0L
}

/** 同一物理设备的屏幕与相机流必须使用不同状态槽位。 */
data class StreamSessionKey(val physicalDeviceKey: String, val kind: StreamKind)