package com.newchar.debug.pc.device.scan

import kotlin.test.Test
import kotlin.test.assertEquals

class TraditionalAdbPortTest {

    @Test
    fun `设备声明端口时优先保留动态端口`() {
        assertEquals(37123, selectTraditionalAdbPort(37123))
    }

    @Test
    fun `设备未声明端口时使用传统默认端口`() {
        assertEquals(DEFAULT_TRADITIONAL_ADB_PORT, selectTraditionalAdbPort(0))
    }
}