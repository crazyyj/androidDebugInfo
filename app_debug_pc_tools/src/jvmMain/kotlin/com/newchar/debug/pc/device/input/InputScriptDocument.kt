package com.newchar.debug.pc.device.input

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** PC 端校验及多执行通道路由使用的 LQITS 脚本。 */
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
        require(magic == MAGIC && version in MIN_VERSION..VERSION) { "仅支持 LQITS v1/v2 脚本" }
        require(steps.isNotEmpty()) { "脚本没有可执行步骤" }
        validateStepIds()
        steps.forEachIndexed { index, step ->
            require(step.type.isNotBlank()) { "第 ${index + 1} 步缺少 type" }
            validateWaitStep(index, step)
        }
    }

    /** 校验 v2 步骤 ID 非空且全局唯一。 */
    private fun validateStepIds() {
        if (version < 2) return
        val ids = mutableSetOf<String>()
        steps.forEachIndexed { index, step ->
            require(step.id.isNotBlank()) { "第 ${index + 1} 步缺少 id" }
            require(ids.add(step.id)) { "步骤 id 重复: ${step.id}" }
        }
    }

    /** 校验条件等待字段组合与跳转目标。 */
    private fun validateWaitStep(index: Int, step: InputScriptStep) {
        val state = step.waitState ?: return
        require(version == VERSION) { "第 ${index + 1} 步 waitState 仅支持 LQITS v2" }
        require(step.type == "wait") { "第 ${index + 1} 步 waitState 只能用于 wait" }
        require(step.waitMs in 1L..600_000L) { "第 ${index + 1} 步 waitMs 超出范围" }
        require(step.retryCount == 0) { "第 ${index + 1} 步 waitState 的 retryCount 必须为 0" }
        require(step.timeoutPolicy in setOf("stop", "jump")) { "第 ${index + 1} 步 timeoutPolicy 无效" }
        if (step.timeoutPolicy == "jump") {
            require(step.jumpTargetId.isNotBlank()) { "第 ${index + 1} 步缺少 jumpTargetId" }
            require(steps.any { it.id == step.jumpTargetId }) { "跳转目标不存在: ${step.jumpTargetId}" }
        }
        state.validate(index)
    }

    companion object {
        const val MAGIC = "LQITS"
        const val MIN_VERSION = 1
        const val VERSION = 2
    }
}

/** 单条输入脚本步骤。 */
@Serializable
data class InputScriptStep(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val type: String = "",
    val coordinateMode: String = "source_pixel",
    val failurePolicy: String = "continue",
    val packageName: String = "",
    val delayBeforeMs: Long = 0L,
    val durationMs: Long = 100L,
    val waitMs: Long = 0L,
    val x: Float = 0f,
    val y: Float = 0f,
    val keyCode: Int = 0,
    val retryCount: Int = 0,
    val timeoutPolicy: String = "stop",
    val jumpTargetId: String = "",
    val points: List<InputScriptPoint> = emptyList(),
    val frames: List<InputScriptFrame> = emptyList(),
    val selector: InputNodeSelector? = null,
    val waitState: InputWaitState? = null,
)

/** 条件等待的轮询、组合和超时次数配置。 */
@Serializable
data class InputWaitState(
    val pollIntervalMs: Long = 250L,
    val match: String = "all",
    val consecutiveMatches: Int = 1,
    val maxTimeoutCount: Int = 10,
    val conditions: List<InputWaitCondition> = emptyList(),
) {
    /** 校验条件等待参数及每条条件。 */
    fun validate(stepIndex: Int) {
        require(pollIntervalMs in 50L..5_000L) { "第 ${stepIndex + 1} 步 pollIntervalMs 超出范围" }
        require(match == "all" || match == "any") { "第 ${stepIndex + 1} 步 match 无效" }
        require(consecutiveMatches in 1..10) { "第 ${stepIndex + 1} 步 consecutiveMatches 超出范围" }
        require(maxTimeoutCount in 1..100) { "第 ${stepIndex + 1} 步 maxTimeoutCount 超出范围" }
        require(conditions.isNotEmpty()) { "第 ${stepIndex + 1} 步 conditions 不能为空" }
        conditions.forEachIndexed { index, condition -> condition.validate(stepIndex, index) }
    }
}

/** 一条 UI、前台组件或截图区域颜色等待条件。 */
@Serializable
data class InputWaitCondition(
    val kind: String = "",
    val selector: InputNodeSelector? = null,
    val expected: Boolean = true,
    val attribute: String = "",
    val operator: String = "equals",
    val value: String = "",
    val ignoreCase: Boolean = true,
    val packageName: String = "",
    val activityName: String = "",
    val coordinateMode: String = "normalized",
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val color: String = "",
    val tolerance: Int = 12,
    val minMatchRatio: Float = 0.65f,
    val sampleStride: Int = 2,
) {
    /** 按条件类型校验必要字段和取值范围。 */
    fun validate(stepIndex: Int, conditionIndex: Int) {
        val prefix = "第 ${stepIndex + 1} 步条件 ${conditionIndex + 1}"
        when (kind) {
            "ui_exists" -> require(selector?.isEmpty() == false) { "$prefix 缺少 selector" }
            "ui_attribute" -> validateUiAttribute(prefix)
            "foreground" -> require(packageName.isNotBlank()) { "$prefix 缺少 packageName" }
            "region_color" -> validateRegionColor(prefix)
            else -> require(false) { "$prefix kind 无效: $kind" }
        }
    }

    /** 校验 UI 属性条件并预编译正则表达式。 */
    private fun validateUiAttribute(prefix: String) {
        require(selector?.isEmpty() == false) { "$prefix 缺少 selector" }
        require(attribute in UI_ATTRIBUTES) { "$prefix attribute 无效" }
        require(operator in TEXT_OPERATORS) { "$prefix operator 无效" }
        if (attribute in BOOLEAN_ATTRIBUTES) require(value.lowercase() == "true" || value.lowercase() == "false") {
            "$prefix 布尔属性 value 只能为 true/false"
        }
        require(value.length <= 1_000) { "$prefix value 过长" }
        if (operator == "regex") compileRegex(prefix)
    }

    /** 校验截图区域、颜色与采样参数。 */
    private fun validateRegionColor(prefix: String) {
        require(coordinateMode in COORDINATE_MODES) { "$prefix coordinateMode 无效" }
        require(right > left && bottom > top) { "$prefix 截图区域无效" }
        if (coordinateMode == "normalized") {
            require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f) { "$prefix normalized 坐标越界" }
        }
        require(operator == "target_ratio") { "$prefix 仅支持 target_ratio" }
        require(COLOR_PATTERN.matcher(color).matches()) { "$prefix color 必须为 #RRGGBB" }
        require(tolerance in 0..255) { "$prefix tolerance 超出范围" }
        require(minMatchRatio in 0f..1f) { "$prefix minMatchRatio 超出范围" }
        require(sampleStride in 1..50) { "$prefix sampleStride 超出范围" }
    }

    /** 使用 Java Pattern 校验正则表达式是否合法。 */
    private fun compileRegex(prefix: String) {
        try {
            Pattern.compile(value)
        } catch (error: PatternSyntaxException) {
            throw IllegalArgumentException("$prefix regex 无效: ${error.description}", error)
        }
    }

    companion object {
        private val UI_ATTRIBUTES = setOf("text", "contentDescription", "selected", "checked", "enabled", "focused")
        private val BOOLEAN_ATTRIBUTES = setOf("selected", "checked", "enabled", "focused")
        private val TEXT_OPERATORS = setOf("equals", "not_equals", "contains", "regex")
        private val COORDINATE_MODES = setOf("normalized", "source_pixel", "pixel")
        private val COLOR_PATTERN = Pattern.compile("#[0-9a-fA-F]{6}")
    }
}

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
) {
    /** 判断选择器是否没有任何定位字段。 */
    fun isEmpty(): Boolean = resourceId.isBlank() && text.isBlank() &&
        contentDescription.isBlank() && className.isBlank()
}

/** 统一解析脚本并忽略后续版本添加的非关键字段。 */
fun parseInputScript(json: String): InputScriptDocument {
    val document = Json { ignoreUnknownKeys = true }.decodeFromString<InputScriptDocument>(json)
    document.validate()
    return document
}