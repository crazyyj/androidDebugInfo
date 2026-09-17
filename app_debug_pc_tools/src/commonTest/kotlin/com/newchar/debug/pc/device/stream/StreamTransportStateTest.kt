package com.newchar.debug.pc.device.stream

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamTransportStateTest {
    @Test
    fun `direct 健康时 ADB 恢复事件不会抢占传输`() {
        val active = StreamTransportState.DirectActive("s1", "192.168.1.2")

        assertEquals(active, active.reduce(StreamTransportEvent.FirstFrame("s1", StreamTransportKind.DIRECT, "192.168.1.2")))
    }

    @Test
    fun `reverse 断开后进入重连并可由 direct 首帧接管`() {
        val reconnecting = StreamTransportState.ReverseActive("s1")
            .reduce(StreamTransportEvent.SocketLost("s1", StreamTransportKind.REVERSE))

        assertEquals(StreamTransportState.Reconnecting("s1", StreamTransportKind.REVERSE), reconnecting)
        assertEquals(
            StreamTransportState.DirectActive("s1", "192.168.1.2"),
            reconnecting.reduce(StreamTransportEvent.FirstFrame("s1", StreamTransportKind.DIRECT, "192.168.1.2")),
        )
    }
}