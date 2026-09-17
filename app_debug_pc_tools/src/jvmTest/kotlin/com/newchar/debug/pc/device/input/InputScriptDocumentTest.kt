package com.newchar.debug.pc.device.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 验证 PC 端输入脚本解析和版本校验。 */
class InputScriptDocumentTest {

    /** LQITS v1 多指帧应完整解析。 */
    @Test
    fun `parse multi touch script`() {
        val document = parseInputScript(
            """{"magic":"LQITS","version":1,"scriptId":"case_1","sourceWidth":1080,"sourceHeight":2400,"steps":[{"id":"touch_0","type":"multi_touch","frames":[{"timeMs":0,"action":0,"pointers":[{"id":0,"x":10,"y":20}]}]}]}""",
        )

        assertEquals("case_1", document.scriptId)
        assertEquals("multi_touch", document.steps.single().type)
        assertEquals("continue", document.steps.single().failurePolicy)
        assertEquals(10f, document.steps.single().frames.single().pointers.single().x)
    }

    /** 未知版本必须在执行前拒绝。 */
    @Test
    fun `reject unsupported script version`() {
        assertFailsWith<IllegalArgumentException> {
            parseInputScript("""{"magic":"LQITS","version":2,"steps":[{"type":"tap"}]}""")
        }
    }
}