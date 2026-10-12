package com.newchar.debug.pc.device.input

import com.newchar.debug.pc.executor.AdbCommandExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import javax.imageio.ImageIO

/** dex agent 不可用时，按 instrumentation、ADB、辅助服务顺序执行步骤。 */
internal class AdbInputFallbackRunner(
    private val executor: AdbCommandExecutor,
    private val deviceId: String,
    private val script: InputScriptDocument,
    private val startIndex: Int,
    private val instrumentationRunner: UiautomatorInstrumentationRunner?,
    private val accessibilityExecutor: suspend (String) -> BackendStepResult,
    private val onProgress: suspend (FallbackProgress) -> Unit,
) {
    @Volatile
    private var paused = false

    @Volatile
    private var cancelled = false
    private val waitTimeoutCounts = mutableMapOf<String, Int>()
    private var executionCount = 0

    /** 依次执行所有步骤，单步失败后仍继续尝试后续步骤。 */
    suspend fun run(): Boolean {
        var allSuccessful = true
        var index = startIndex.coerceIn(0, script.steps.size)
        while (index < script.steps.size) {
            if (++executionCount > 100) return reportExecutionLimit(index)
            val step = script.steps[index]
            if (!waitUntilRunnable(step.delayBeforeMs)) return false
            val label = stepLabel(step)
            onProgress(FallbackProgress("STEP_STARTED", index, label, true))
            val result = executeWithRetry(step)
            onProgress(
                FallbackProgress(
                    if (result.success) "STEP_OK" else "STEP_FAILED",
                    index + 1,
                    "$label：${result.message}",
                    result.success,
                    result.backend,
                ),
            )
            if (!result.success && (step.waitState == null || step.timeoutPolicy != "jump")) {
                allSuccessful = false
            }
            val nextIndex = nextIndex(index, step, result)
            if (nextIndex < 0) return false
            index = nextIndex
        }
        return allSuccessful && !cancelled
    }

    /** 计算成功、普通失败或 waitState 超时后的下一步骤。 */
    private fun nextIndex(index: Int, step: InputScriptStep, result: FallbackResult): Int {
        if (result.success || step.waitState == null) return index + 1
        val count = waitTimeoutCounts.getOrDefault(step.id, 0) + 1
        waitTimeoutCounts[step.id] = count
        if (count > step.waitState.maxTimeoutCount) return -1
        if (step.timeoutPolicy != "jump") return -1
        return script.steps.indexOfFirst { it.id == step.jumpTargetId }
    }

    /** 上报整体 100 步保护触发。 */
    private suspend fun reportExecutionLimit(index: Int): Boolean {
        val step = script.steps[index]
        onProgress(FallbackProgress("STEP_FAILED", index + 1,
            "${stepLabel(step)}：步骤执行次数超过 100，请检查等待条件、jump 和整体流程合理性", false, "all_backends"))
        return false
    }

    /** 暂停执行。 */
    fun pause() {
        paused = true
    }

    /** 恢复执行。 */
    fun resume() {
        paused = false
    }

    /** 取消执行。 */
    fun cancel() {
        cancelled = true
        paused = false
    }

    /** 按步骤重试次数执行。 */
    private suspend fun executeWithRetry(step: InputScriptStep): FallbackResult {
        var result = FallbackResult(false, "all_backends", "未执行")
        repeat(step.retryCount.coerceAtLeast(0) + 1) {
            if (cancelled) return FallbackResult(false, "all_backends", "脚本已取消")
            result = try {
                executeTieredStep(step)
            } catch (cancelledException: CancellationException) {
                throw cancelledException
            } catch (throwable: Throwable) {
                FallbackResult(false, "all_backends", throwable.message ?: throwable::class.simpleName.orEmpty())
            }
            if (result.success) return result
        }
        return result
    }

    /** 按 instrumentation、adb、辅助服务顺序执行单步。 */
    private suspend fun executeTieredStep(step: InputScriptStep): FallbackResult {
        val instrumentation = instrumentationRunner?.execute(script, step)
        if (instrumentation?.success == true) return instrumentation.toFallback()
        val adb = executeStep(step)
        if (adb.success) return adb
        val accessibility = accessibilityExecutor(accessibilityPayload(step))
        if (accessibility.success) {
            if (step.waitState != null && !waitUntilRunnable(600L)) {
                return FallbackResult(false, "accessibility", "条件满足后的固定延迟期间取消")
            }
            return accessibility.toFallback()
        }
        val message = "uiautomator=${instrumentation?.message ?: "不可用"}；adb=${adb.message}；辅助服务=${accessibility.message}"
        return FallbackResult(false, "all_backends", message)
    }

    /** 将脚本尺寸、默认包名和当前步骤组合为辅助服务执行载荷。 */
    private fun accessibilityPayload(step: InputScriptStep): String = Json.encodeToString(
        buildJsonObject {
            put("sourceWidth", script.sourceWidth)
            put("sourceHeight", script.sourceHeight)
            put("targetPackage", script.targetPackage)
            put("step", Json.encodeToJsonElement(InputScriptStep.serializer(), step))
        },
    )

    /** 分发 adb input 可处理的步骤类型。 */
    private suspend fun executeStep(step: InputScriptStep): FallbackResult = when (step.type) {
        "tap" -> inputTap(step)
        "long_press" -> inputLongPress(step)
        "gesture" -> inputGesture(step)
        "key" -> shellResult("按键", "input", "keyevent", step.keyCode.toString())
        "wait" -> waitStep(step)
        "launch_app" -> launchPackage(step)
        "node_tap" -> nodeTap(step)
        "multi_touch" -> inputMultiTouch(step)
        else -> FallbackResult(false, "adb_input", "adb input 不支持 ${step.type}")
    }

    /** 映射坐标后执行点击。 */
    private suspend fun inputTap(step: InputScriptStep): FallbackResult {
        val point = mapPoint(step.x, step.y, step.coordinateMode)
        return shellResult("点击", "input", "tap", point.first.toString(), point.second.toString())
    }

    /** 使用同起止点 swipe 模拟长按。 */
    private suspend fun inputLongPress(step: InputScriptStep): FallbackResult {
        val point = mapPoint(step.x, step.y, step.coordinateMode)
        return shellResult(
            "长按", "input", "swipe", point.first.toString(), point.second.toString(),
            point.first.toString(), point.second.toString(), step.durationMs.coerceAtLeast(500L).toString(),
        )
    }

    /** 将曲线路径降级为首尾直线 swipe。 */
    private suspend fun inputGesture(step: InputScriptStep): FallbackResult {
        if (step.points.size < 2) return FallbackResult(false, "adb_input", "轨迹点不足")
        val first = mapPoint(step.points.first().x, step.points.first().y, step.coordinateMode)
        val last = mapPoint(step.points.last().x, step.points.last().y, step.coordinateMode)
        return shellResult(
            "滑动", "input", "swipe", first.first.toString(), first.second.toString(),
            last.first.toString(), last.second.toString(), step.durationMs.coerceAtLeast(1L).toString(),
        )
    }

    /** 将多指事件降级为每个 pointerId 的逐条直线 swipe。 */
    private suspend fun inputMultiTouch(step: InputScriptStep): FallbackResult {
        val spans = pointerSpans(step.frames)
        if (spans.isEmpty()) return FallbackResult(false, "adb_input", "多指事件帧为空")
        var success = true
        for (span in spans.values) {
            val first = mapPoint(span.first.x, span.first.y, step.coordinateMode)
            val last = mapPoint(span.last.x, span.last.y, step.coordinateMode)
            val result = shellResult(
                "多指近似滑动", "input", "swipe", first.first.toString(), first.second.toString(),
                last.first.toString(), last.second.toString(), step.durationMs.coerceAtLeast(1L).toString(),
            )
            success = result.success && success
        }
        return FallbackResult(success, "adb_input", if (success) "多指已按逐指轨迹近似执行" else "多指逐指兜底失败")
    }

    /** 汇总多指事件中每个 pointerId 的首尾触点。 */
    private fun pointerSpans(frames: List<InputScriptFrame>): Map<Int, PointerSpan> {
        val spans = linkedMapOf<Int, PointerSpan>()
        frames.forEach { frame ->
            frame.pointers.forEach { point ->
                val span = spans.getOrPut(point.id) { PointerSpan(point, point) }
                spans[point.id] = span.copy(last = point)
            }
        }
        return spans
    }

    /** 等待指定时长并响应暂停或取消。 */
    private suspend fun waitStep(step: InputScriptStep): FallbackResult {
        val state = step.waitState ?: return fixedWait(step.waitMs)
        val deadline = System.currentTimeMillis() + step.waitMs.coerceAtLeast(1L)
        var consecutive = 0
        var observation: String
        do {
            val summary = evaluateWaitState(state)
            observation = summary.message
            consecutive = if (summary.matched) consecutive + 1 else 0
            if (consecutive >= state.consecutiveMatches) {
                val stable = waitUntilRunnable(600L)
                return FallbackResult(stable, "wait_state_adb", if (stable) "条件满足，命中后已等待 600ms" else "脚本已取消")
            }
            if (!waitUntilRunnable(state.pollIntervalMs)) break
        } while (System.currentTimeMillis() <= deadline)
        return FallbackResult(false, "wait_state_adb", "条件等待超时：$observation")
    }

    /** 执行无条件的固定等待。 */
    private suspend fun fixedWait(waitMs: Long): FallbackResult {
        val success = waitUntilRunnable(waitMs.coerceAtLeast(0L))
        return FallbackResult(success, "adb_input", if (success) "等待完成" else "脚本已取消")
    }

    /** 按 all/any 汇总一轮 ADB 状态采集结果。 */
    private suspend fun evaluateWaitState(state: InputWaitState): ConditionCheck {
        val checks = state.conditions.map { evaluateCondition(it) }
        val matched = if (state.match == "all") checks.all(ConditionCheck::matched) else checks.any(ConditionCheck::matched)
        return ConditionCheck(matched, checks.joinToString("；") { it.message })
    }

    /** 按条件类型选择 ADB 状态采集方式。 */
    private suspend fun evaluateCondition(condition: InputWaitCondition): ConditionCheck = when (condition.kind) {
        "ui_exists" -> evaluateUiExists(condition)
        "ui_attribute" -> evaluateUiAttribute(condition)
        "foreground" -> evaluateForeground(condition)
        "region_color" -> evaluateRegionColor(condition)
        else -> ConditionCheck(false, "未知条件 ${condition.kind}")
    }

    /** 使用 uiautomator dump 判断节点存在状态。 */
    private suspend fun evaluateUiExists(condition: InputWaitCondition): ConditionCheck {
        val attributes = findNodeAttributes(dumpUiXml(), condition.selector ?: return ConditionCheck(false, "缺少 selector"))
        val found = attributes != null
        return ConditionCheck(found == condition.expected, "ui_exists 当前=$found 目标=${condition.expected}")
    }

    /** 使用 uiautomator dump 判断节点属性。 */
    private suspend fun evaluateUiAttribute(condition: InputWaitCondition): ConditionCheck {
        val attributes = findNodeAttributes(dumpUiXml(), condition.selector ?: return ConditionCheck(false, "缺少 selector"))
            ?: return ConditionCheck(false, "未找到节点")
        val actual = when (condition.attribute) {
            "contentDescription" -> attributes["content-desc"].orEmpty()
            else -> attributes[condition.attribute].orEmpty()
        }
        return ConditionCheck(compareAttribute(actual, condition), "ui_attribute 当前=$actual")
    }

    /** 使用 dumpsys activity 判断前台组件。 */
    private suspend fun evaluateForeground(condition: InputWaitCondition): ConditionCheck {
        val output = executor.shell(deviceId, "dumpsys", "activity", "activities").output
        val line = output.lineSequence().firstOrNull {
            it.contains("ResumedActivity", true) || it.contains("topResumedActivity", true)
        }.orEmpty()
        val match = Regex("([A-Za-z][A-Za-z0-9_.$]*)/([A-Za-z0-9_.$]+)").find(line)
        val packageName = match?.groupValues?.get(1).orEmpty()
        val rawActivity = match?.groupValues?.get(2).orEmpty()
        val activity = if (rawActivity.startsWith('.')) packageName + rawActivity else rawActivity
        val matched = packageName.equals(condition.packageName, true) &&
            (condition.activityName.isBlank() || activity.equals(condition.activityName, true))
        return ConditionCheck(matched, "foreground 当前=$packageName/$activity")
    }

    /** 使用 ADB 截图判断目标区域颜色占比。 */
    private suspend fun evaluateRegionColor(condition: InputWaitCondition): ConditionCheck {
        val image = captureScreen() ?: return ConditionCheck(false, "ADB 截图失败")
        val bounds = colorBounds(image.width, image.height, condition)
        val target = condition.color.removePrefix("#").toInt(16)
        var matched = 0L
        var total = 0L
        for (y in bounds[1] until bounds[3] step condition.sampleStride) {
            for (x in bounds[0] until bounds[2] step condition.sampleStride) {
                if (colorNear(image.getRGB(x, y), target, condition.tolerance)) matched++
                total++
            }
        }
        val ratio = if (total == 0L) 0f else matched.toFloat() / total
        return ConditionCheck(ratio >= condition.minMatchRatio, "region_color 占比=$ratio")
    }

    /** 导出并读取一次 UI XML。 */
    private suspend fun dumpUiXml(): String {
        val remotePath = "/data/local/tmp/newchar_input_ui.xml"
        executor.shell(deviceId, "uiautomator", "dump", "--compressed", remotePath)
        val output = executor.shell(deviceId, "cat", remotePath).output
        executor.shell(deviceId, "rm", "-f", remotePath)
        return output
    }

    /** 截取设备屏幕并读取为 JVM 图片。 */
    private suspend fun captureScreen(): java.awt.image.BufferedImage? {
        val remote = "/data/local/tmp/newchar_input_screen.png"
        val local = File.createTempFile("newchar_input_", ".png")
        return try {
            if (!executor.shell(deviceId, "screencap", "-p", remote).isSuccess) return null
            if (!executor.adb("-s", deviceId, "pull", remote, local.absolutePath).isSuccess) return null
            ImageIO.read(local)
        } finally {
            executor.shell(deviceId, "rm", "-f", remote)
            local.delete()
        }
    }

    /** 计算颜色条件在当前截图中的整数区域。 */
    private fun colorBounds(width: Int, height: Int, condition: InputWaitCondition): IntArray {
        fun x(value: Float): Int = when (condition.coordinateMode) {
            "normalized" -> (value * width).toInt()
            "source_pixel" -> (value * width / script.sourceWidth.coerceAtLeast(1)).toInt()
            else -> value.toInt()
        }
        fun y(value: Float): Int = when (condition.coordinateMode) {
            "normalized" -> (value * height).toInt()
            "source_pixel" -> (value * height / script.sourceHeight.coerceAtLeast(1)).toInt()
            else -> value.toInt()
        }
        return intArrayOf(x(condition.left).coerceIn(0, width - 1), y(condition.top).coerceIn(0, height - 1),
            x(condition.right).coerceIn(1, width), y(condition.bottom).coerceIn(1, height))
    }

    /** 判断两个 RGB 颜色是否处于通道容差内。 */
    private fun colorNear(actual: Int, target: Int, tolerance: Int): Boolean {
        val red = kotlin.math.abs((actual shr 16 and 255) - (target shr 16 and 255))
        val green = kotlin.math.abs((actual shr 8 and 255) - (target shr 8 and 255))
        val blue = kotlin.math.abs((actual and 255) - (target and 255))
        return red <= tolerance && green <= tolerance && blue <= tolerance
    }

    /** 按脚本操作符比较 UI 属性。 */
    private fun compareAttribute(actual: String, condition: InputWaitCondition): Boolean {
        val expected = condition.value
        val left = if (condition.ignoreCase) actual.lowercase() else actual
        val right = if (condition.ignoreCase) expected.lowercase() else expected
        return when (condition.operator) {
            "not_equals" -> left != right
            "contains" -> left.contains(right)
            "regex" -> Regex(expected, if (condition.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()).containsMatchIn(actual)
            else -> left == right
        }
    }

    /** 启动步骤指定或脚本默认应用。 */
    private suspend fun launchPackage(step: InputScriptStep): FallbackResult {
        val packageName = step.packageName.ifBlank { script.targetPackage }
        if (packageName.isBlank()) return FallbackResult(false, "adb_input", "启动应用步骤缺少包名")
        return shellResult(
            "启动应用", "monkey", "-p", packageName,
            "-c", "android.intent.category.LAUNCHER", "1",
        )
    }

    /** 导出 UI XML、定位节点并点击中心点。 */
    private suspend fun nodeTap(step: InputScriptStep): FallbackResult {
        val selector = step.selector ?: return FallbackResult(false, "adb_input", "节点步骤缺少 selector")
        val remotePath = "/data/local/tmp/newchar_input_ui.xml"
        executor.shell(deviceId, "uiautomator", "dump", "--compressed", remotePath)
        val xml = executor.shell(deviceId, "cat", remotePath).output
        executor.shell(deviceId, "rm", "-f", remotePath)
        val center = findNodeCenter(xml, selector) ?: return FallbackResult(false, "adb_input", "未找到匹配节点")
        return shellResult("节点点击", "input", "tap", center.first.toString(), center.second.toString())
    }

    /** 执行设备 shell 命令并转换结果。 */
    private suspend fun shellResult(label: String, vararg command: String): FallbackResult {
        val result = executor.shell(deviceId, *command)
        val message = result.error.ifBlank { result.output }.ifBlank { "$label 完成" }
        return FallbackResult(result.isSuccess, "adb_input", message)
    }

    /** 根据脚本坐标模式映射到当前屏幕。 */
    private suspend fun mapPoint(x: Float, y: Float, mode: String): Pair<Int, Int> {
        val size = targetScreenSize()
        val mappedX = when (mode) {
            "normalized" -> x * size.first
            "source_pixel" -> x * size.first / script.sourceWidth.coerceAtLeast(1)
            else -> x
        }
        val mappedY = when (mode) {
            "normalized" -> y * size.second
            "source_pixel" -> y * size.second / script.sourceHeight.coerceAtLeast(1)
            else -> y
        }
        return mappedX.toInt().coerceIn(0, size.first - 1) to mappedY.toInt().coerceIn(0, size.second - 1)
    }

    /** 读取 wm size 的最后一组尺寸，优先采用 override size。 */
    private suspend fun targetScreenSize(): Pair<Int, Int> {
        val output = executor.shell(deviceId, "wm", "size").output
        val matches = Regex("(\\d+)x(\\d+)").findAll(output).toList()
        val match = matches.lastOrNull() ?: return 1080 to 1920
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    /** 匹配 UI XML 节点属性并解析 bounds 中心点。 */
    private fun findNodeCenter(xml: String, selector: InputNodeSelector): Pair<Int, Int>? {
        val attributes = findNodeAttributes(xml, selector) ?: return null
        return parseBounds(attributes["bounds"].orEmpty())
    }

    /** 返回第一个满足选择器的 UI XML 节点属性。 */
    private fun findNodeAttributes(xml: String, selector: InputNodeSelector): Map<String, String>? {
        val nodes = Regex("<node\\s+[^>]*>").findAll(xml) + Regex("<node\\s+[^>]*/>").findAll(xml)
        return nodes.map { parseAttributes(it.value) }.firstOrNull { matchesSelector(it, selector) }
    }

    /** 解析单个 node 标签的 XML 属性。 */
    private fun parseAttributes(node: String): Map<String, String> =
        Regex("([\\w-]+)=\"([^\"]*)\"").findAll(node).associate { it.groupValues[1] to it.groupValues[2] }

    /** 判断节点是否满足所有非空条件。 */
    private fun matchesSelector(values: Map<String, String>, selector: InputNodeSelector): Boolean =
        matchesValue(selector.resourceId, values["resource-id"]) &&
            matchesValue(selector.text, values["text"]) &&
            matchesValue(selector.contentDescription, values["content-desc"]) &&
            matchesValue(selector.className, values["class"])

    /** 空条件视为匹配，非空条件要求完全一致。 */
    private fun matchesValue(expected: String, actual: String?): Boolean = expected.isBlank() || expected == actual

    /** 解析 bounds 文本并返回中心点。 */
    private fun parseBounds(bounds: String): Pair<Int, Int>? {
        val match = Regex("\\[(\\d+),(\\d+)]\\[(\\d+),(\\d+)]").matchEntire(bounds) ?: return null
        val values = match.groupValues.drop(1).map(String::toInt)
        return (values[0] + values[2]) / 2 to (values[1] + values[3]) / 2
    }

    /** 分段等待，使暂停和取消可及时生效。 */
    private suspend fun waitUntilRunnable(durationMs: Long): Boolean {
        var remaining = durationMs.coerceAtLeast(0L)
        while (!cancelled && remaining > 0L) {
            while (paused && !cancelled) delay(50L)
            val slice = remaining.coerceAtMost(40L)
            delay(slice)
            remaining -= slice
        }
        return !cancelled
    }

    /** 返回优先采用名称、描述的用户可读步骤标题。 */
    private fun stepLabel(step: InputScriptStep): String = when {
        step.name.isNotBlank() -> step.name
        step.description.isNotBlank() -> step.description
        else -> "${readableStepType(step.type)}（${step.id.ifBlank { "step" }}）"
    }

    /** 将脚本步骤类型转换为中文显示名称。 */
    private fun readableStepType(type: String): String = when (type) {
        "tap" -> "点击"
        "long_press" -> "长按"
        "gesture" -> "滑动手势"
        "multi_touch" -> "多指手势"
        "key" -> "按键"
        "wait" -> "等待"
        "launch_app" -> "打开应用"
        "node_tap" -> "点击 UI 控件"
        else -> type.ifBlank { "未命名步骤" }
    }
}

/** 后续执行通道的单步进度。 */
internal data class FallbackProgress(
    val state: String,
    val index: Int,
    val message: String,
    val success: Boolean,
    val backend: String = "adb_input",
)

/** adb input 单步骤执行结果。 */
private data class FallbackResult(val success: Boolean, val backend: String, val message: String)

/** 将通用通道结果转换为兜底执行结果。 */
private fun BackendStepResult.toFallback(): FallbackResult = FallbackResult(success, backend, message)

/** 一个 pointerId 在多指事件中的首尾触点。 */
private data class PointerSpan(val first: InputScriptPoint, val last: InputScriptPoint)

/** ADB 单条条件判断结果。 */
private data class ConditionCheck(val matched: Boolean, val message: String)
