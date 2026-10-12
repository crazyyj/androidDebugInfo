package com.newchar.debug.pc.device.input

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 验证 PC 端输入脚本解析和版本校验。 */
class InputScriptDocumentTest {

    /** LQITS v1 多指帧应完整解析。 */
    @Test
    fun `parse multi touch script`() {
        val document = parseInputScript(
            """{"magic":"LQITS","version":1,"scriptId":"case_1","sourceWidth":1080,"sourceHeight":2400,"steps":[{"id":"touch_0","name":"缩放图片","description":"双指缩放预览图","type":"multi_touch","frames":[{"timeMs":0,"action":0,"pointers":[{"id":0,"x":10,"y":20}]}]}]}""",
        )

        assertEquals("case_1", document.scriptId)
        assertEquals("multi_touch", document.steps.single().type)
        assertEquals("缩放图片", document.steps.single().name)
        assertEquals("双指缩放预览图", document.steps.single().description)
        assertEquals("continue", document.steps.single().failurePolicy)
        assertEquals(10f, document.steps.single().frames.single().pointers.single().x)
    }

    /** 未知版本必须在执行前拒绝。 */
    @Test
    fun `reject unsupported script version`() {
        assertFailsWith<IllegalArgumentException> {
            parseInputScript("""{"magic":"LQITS","version":3,"steps":[{"type":"tap"}]}""")
        }
    }

    /** LQITS v2 应解析条件等待、跳转和默认轮询参数。 */
    @Test
    fun `parse wait state script`() {
        val document = parseInputScript(
            """{"magic":"LQITS","version":2,"steps":[{"id":"wait_login","type":"wait","waitMs":10000,"timeoutPolicy":"jump","jumpTargetId":"open_home","waitState":{"conditions":[{"kind":"ui_attribute","selector":{"resourceId":"app:id/status"},"attribute":"text","operator":"regex","value":"ready|done"}]}},{"id":"open_home","type":"tap"}]}""",
        )

        val step = document.steps.first()
        assertEquals("jump", step.timeoutPolicy)
        assertEquals(250L, step.waitState?.pollIntervalMs)
        assertEquals(10, step.waitState?.maxTimeoutCount)
        assertEquals(true, step.waitState?.conditions?.single()?.ignoreCase)
        assertEquals("jump", parseInputScript(Json.encodeToString(document)).steps.first().timeoutPolicy)
    }

    /** v1 脚本不得携带会被旧执行器静默忽略的 waitState。 */
    @Test
    fun `reject wait state in v1`() {
        assertFailsWith<IllegalArgumentException> {
            parseInputScript(
                """{"magic":"LQITS","version":1,"steps":[{"id":"wait","type":"wait","waitMs":1000,"waitState":{"conditions":[{"kind":"foreground","packageName":"com.example"}]}}]}""",
            )
        }
    }

    /** jump 必须指向脚本中的唯一步骤 ID。 */
    @Test
    fun `reject missing jump target`() {
        assertFailsWith<IllegalArgumentException> {
            parseInputScript(
                """{"magic":"LQITS","version":2,"steps":[{"id":"wait","type":"wait","waitMs":1000,"timeoutPolicy":"jump","jumpTargetId":"missing","waitState":{"conditions":[{"kind":"foreground","packageName":"com.example"}]}}]}""",
            )
        }
    }

    /** 非法正则必须在脚本开始执行前被拒绝。 */
    @Test
    fun `reject invalid condition regex`() {
        assertFailsWith<IllegalArgumentException> {
            parseInputScript(
                """{"magic":"LQITS","version":2,"steps":[{"id":"wait","type":"wait","waitMs":1000,"waitState":{"conditions":[{"kind":"ui_attribute","selector":{"text":"状态"},"attribute":"text","operator":"regex","value":"["}]}}]}""",
            )
        }
    }

    /** region_color 首期只允许 target_ratio。 */
    @Test
    fun `reject unsupported color operator`() {
        assertFailsWith<IllegalArgumentException> {
            parseInputScript(
                """{"magic":"LQITS","version":2,"steps":[{"id":"wait","type":"wait","waitMs":1000,"waitState":{"conditions":[{"kind":"region_color","left":0.1,"top":0.1,"right":0.2,"bottom":0.2,"operator":"changed_ratio","color":"#34A853"}]}}]}""",
            )
        }
    }
}