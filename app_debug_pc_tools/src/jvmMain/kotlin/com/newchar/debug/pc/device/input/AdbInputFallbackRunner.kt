package com.newchar.debug.pc.device.input

import com.newchar.debug.pc.executor.AdbCommandExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** dex agent 无法启动时，使用 adb shell input 顺序执行可表达的脚本步骤。 */
internal class AdbInputFallbackRunner(
    private val executor: AdbCommandExecutor,
    private val deviceId: String,
    private val script: InputScriptDocument,
    private val onProgress: suspend (FallbackProgress) -> Unit,
) {
    @Volatile
    private var paused = false

    @Volatile
    private var cancelled = false

    /** 依次执行所有步骤，单步失败后仍继续尝试后续步骤。 */
    suspend fun run(): Boolean {
        var allSuccessful = true
        for ((index, step) in script.steps.withIndex()) {
            if (!waitUntilRunnable(step.delayBeforeMs)) return false
            onProgress(FallbackProgress("STEP_STARTED", index, stepLabel(step), true))
            val result = executeWithRetry(step)
            onProgress(FallbackProgress(if (result.success) "STEP_OK" else "STEP_FAILED", index + 1, result.message, result.success))
            allSuccessful = result.success && allSuccessful
        }
        return allSuccessful && !cancelled
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
        var result = FallbackResult(false, "未执行")
        repeat(step.retryCount.coerceAtLeast(0) + 1) {
            if (cancelled) return FallbackResult(false, "脚本已取消")
            result = try {
                executeStep(step)
            } catch (cancelledException: CancellationException) {
                throw cancelledException
            } catch (throwable: Throwable) {
                FallbackResult(false, throwable.message ?: throwable::class.simpleName.orEmpty())
            }
            if (result.success) return result
        }
        return result
    }

    /** 分发 adb input 可处理的步骤类型。 */
    private suspend fun executeStep(step: InputScriptStep): FallbackResult = when (step.type) {
        "tap" -> inputTap(step)
        "long_press" -> inputLongPress(step)
        "gesture" -> inputGesture(step)
        "key" -> shellResult("按键", "input", "keyevent", step.keyCode.toString())
        "wait" -> waitStep(step)
        "launch_app" -> launchPackage(step)
        "package_check" -> checkPackage(step)
        "node_tap" -> nodeTap(step)
        "multi_touch" -> inputMultiTouch(step)
        else -> FallbackResult(false, "adb input 不支持 ${step.type}")
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
        if (step.points.size < 2) return FallbackResult(false, "轨迹点不足")
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
        if (spans.isEmpty()) return FallbackResult(false, "多指事件帧为空")
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
        return FallbackResult(success, if (success) "多指已按逐指轨迹近似执行" else "多指逐指兜底失败")
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
        val success = waitUntilRunnable(step.waitMs.coerceAtLeast(0L))
        return FallbackResult(success, if (success) "等待完成" else "脚本已取消")
    }

    /** 启动步骤指定或脚本默认应用。 */
    private suspend fun launchPackage(step: InputScriptStep): FallbackResult {
        val packageName = step.packageName.ifBlank { script.targetPackage }
        if (packageName.isBlank()) return FallbackResult(false, "启动应用步骤缺少包名")
        return shellResult(
            "启动应用", "monkey", "-p", packageName,
            "-c", "android.intent.category.LAUNCHER", "1",
        )
    }

    /** 在超时时间内轮询当前前台包名。 */
    private suspend fun checkPackage(step: InputScriptStep): FallbackResult {
        val expected = step.expectedPackage.ifBlank { script.targetPackage }
        val deadline = System.currentTimeMillis() + step.timeoutMs.coerceAtLeast(0L)
        do {
            if (foregroundPackage() == expected) return FallbackResult(true, "前台应用匹配")
            if (!waitUntilRunnable(100L)) return FallbackResult(false, "脚本已取消")
        } while (System.currentTimeMillis() <= deadline)
        return FallbackResult(false, "前台应用不是 $expected")
    }

    /** 导出 UI XML、定位节点并点击中心点。 */
    private suspend fun nodeTap(step: InputScriptStep): FallbackResult {
        val selector = step.selector ?: return FallbackResult(false, "节点步骤缺少 selector")
        val remotePath = "/data/local/tmp/newchar_input_ui.xml"
        executor.shell(deviceId, "uiautomator", "dump", "--compressed", remotePath)
        val xml = executor.shell(deviceId, "cat", remotePath).output
        executor.shell(deviceId, "rm", "-f", remotePath)
        val center = findNodeCenter(xml, selector) ?: return FallbackResult(false, "未找到匹配节点")
        return shellResult("节点点击", "input", "tap", center.first.toString(), center.second.toString())
    }

    /** 执行设备 shell 命令并转换结果。 */
    private suspend fun shellResult(label: String, vararg command: String): FallbackResult {
        val result = executor.shell(deviceId, *command)
        val message = result.error.ifBlank { result.output }.ifBlank { "$label 完成" }
        return FallbackResult(result.isSuccess, message)
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

    /** 从 dumpsys activity 输出中解析前台包名。 */
    private suspend fun foregroundPackage(): String {
        val output = executor.shell(deviceId, "dumpsys", "activity", "activities").output
        val line = output.lineSequence().firstOrNull { it.contains("ResumedActivity", true) }.orEmpty()
        return Regex("([A-Za-z][A-Za-z0-9_.$]*)/").find(line)?.groupValues?.get(1).orEmpty()
    }

    /** 匹配 UI XML 节点属性并解析 bounds 中心点。 */
    private fun findNodeCenter(xml: String, selector: InputNodeSelector): Pair<Int, Int>? {
        val nodes = Regex("<node\\s+[^>]*>").findAll(xml) + Regex("<node\\s+[^>]*/>").findAll(xml)
        for (match in nodes) {
            val attributes = parseAttributes(match.value)
            if (!matchesSelector(attributes, selector)) continue
            return parseBounds(attributes["bounds"].orEmpty())
        }
        return null
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

    /** 构造步骤展示文本。 */
    private fun stepLabel(step: InputScriptStep): String = "${step.id.ifBlank { "step" }}: ${step.type}"
}

/** adb input 兜底进度。 */
internal data class FallbackProgress(
    val state: String,
    val index: Int,
    val message: String,
    val success: Boolean,
)

/** adb input 单步骤执行结果。 */
private data class FallbackResult(val success: Boolean, val message: String)

/** 一个 pointerId 在多指事件中的首尾触点。 */
private data class PointerSpan(val first: InputScriptPoint, val last: InputScriptPoint)