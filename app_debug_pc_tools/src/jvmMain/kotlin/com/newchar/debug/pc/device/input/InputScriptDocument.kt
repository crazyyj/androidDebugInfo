package com.newchar.debug.pc.device.input

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** PC 端校验和 adb input 兜底使用的 LQITS v1 脚本。 */
@Serializable
data class InputScriptDocument(
    val magic: String = "",
    val version: Int = 0,
    val scriptId: String = "script",
    val name: String = "",
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val targetPackage: String = "",
    val steps: List<InputScriptStep> = emptyList(),
) {
    /** 校验脚本版本和必要步骤字段。 */
    fun validate() {
        require(magic == MAGIC && version == VERSION) { "仅支持 LQITS v1 脚本" }
        require(steps.isNotEmpty()) { "脚本没有可执行步骤" }
        steps.forEachIndexed { index, step ->
            require(step.type.isNotBlank()) { "第 ${index + 1} 步缺少 type" }
        }
    }

    companion object {
        const val MAGIC = "LQITS"
        const val VERSION = 1
    }
}

/** 单条输入脚本步骤。 */
@Serializable
data class InputScriptStep(
    val id: String = "",
    val type: String = "",
    val coordinateMode: String = "source_pixel",
    val failurePolicy: String = "continue",
    val packageName: String = "",
    val expectedPackage: String = "",
    val delayBeforeMs: Long = 0L,
    val durationMs: Long = 100L,
    val waitMs: Long = 0L,
    val timeoutMs: Long = 3_000L,
    val x: Float = 0f,
    val y: Float = 0f,
    val keyCode: Int = 0,
    val retryCount: Int = 0,
    val points: List<InputScriptPoint> = emptyList(),
    val frames: List<InputScriptFrame> = emptyList(),
    val selector: InputNodeSelector? = null,
)

/** 单指轨迹点或多指帧中的触点。 */
@Serializable
data class InputScriptPoint(
    val id: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val pressure: Float = 1f,
    val size: Float = 1f,
    val timeMs: Long = 0L,
)

/** 一帧完整多指事件。 */
@Serializable
data class InputScriptFrame(
    val timeMs: Long = 0L,
    val action: Int = 0,
    val pointers: List<InputScriptPoint> = emptyList(),
)

/** uiautomator 节点定位条件。 */
@Serializable
data class InputNodeSelector(
    val resourceId: String = "",
    val text: String = "",
    val contentDescription: String = "",
    val className: String = "",
)

/** 统一解析脚本并忽略后续版本添加的非关键字段。 */
fun parseInputScript(json: String): InputScriptDocument {
    val document = Json { ignoreUnknownKeys = true }.decodeFromString<InputScriptDocument>(json)
    document.validate()
    return document
}