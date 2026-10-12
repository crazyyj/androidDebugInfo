package com.newchar.debug.pc.device.input

import com.newchar.debug.pc.executor.AdbCommandExecutor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * 调用宿主 App 内已合并的 UiAutomation instrumentation 执行单条脚本指令。
 *
 * 早期版本需要把独立的 uiautomator APK（`input_uiautomator.apk`）`adb install` 到设备；
 * 现已合并进 [lib_debug_touch]，由宿主 App 直接携带该插桩，因此本类不再安装任何 APK，
 * 只需确认插桩已在宿主 App 中注册即可。这也是「UiAutomator 是一种操作设备的方式、无需单独引入」的体现。
 */
internal class UiautomatorInstrumentationRunner(
    private val executor: AdbCommandExecutor,
    private val deviceId: String,
) {
    private var checked = false
    private var available = false

    /** 确认宿主 App 已注册 UiAutomation 插桩（无需安装 APK）。 */
    suspend fun prepare(): BackendStepResult = ensureAvailable()

    /** 使用 instrumentation 执行单步；插桩不可用时直接返回，由调用方继续降级通道。 */
    suspend fun execute(script: InputScriptDocument, step: InputScriptStep): BackendStepResult {
        val prepared = ensureAvailable()
        if (!prepared.success) return prepared
        val encoded = encodePayload(script, step)
        val result = executor.shell(
            deviceId, "am", "instrument", "-w", "-r", "-e", "payload", encoded, COMPONENT,
        )
        if (!result.isSuccess) return BackendStepResult(false, "uiautomator", result.error.ifBlank { result.output })
        return parseResult(result.output)
    }

    /** 通过 pm list instrumentation 校验插桩是否存在，避免盲目发起 am instrument。 */
    private suspend fun ensureAvailable(): BackendStepResult {
        if (checked) {
            return BackendStepResult(available, "uiautomator",
                if (available) "插桩已就绪" else "宿主 App 未包含 UiAutomation 插桩（$COMPONENT），请重新安装宿主 App")
        }
        val list = executor.shell(deviceId, "pm", "list", "instrumentation")
        available = list.isSuccess && list.output.contains(COMPONENT)
        checked = true
        return BackendStepResult(available, "uiautomator",
            if (available) "插桩已就绪" else "宿主 App 未包含 UiAutomation 插桩（$COMPONENT），请重新安装宿主 App")
    }

    /** 将脚本尺寸和单步骤编码为 URL-safe Base64 JSON。 */
    private fun encodePayload(script: InputScriptDocument, step: InputScriptStep): String {
        val effectiveStep = if (step.type == "launch_app" && step.packageName.isBlank()) {
            step.copy(packageName = script.targetPackage)
        } else step
        val payload = buildJsonObject {
            put("sourceWidth", script.sourceWidth)
            put("sourceHeight", script.sourceHeight)
            put("step", Json.encodeToJsonElement(InputScriptStep.serializer(), effectiveStep))
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(payload).toByteArray())
    }

    /** 解析 am instrument 输出中的 lqits 结果。 */
    private fun parseResult(output: String): BackendStepResult {
        val encoded = output.lineSequence()
            .firstOrNull { it.startsWith(RESULT_PREFIX) }
            ?.substringAfter(RESULT_PREFIX)
            ?.trim()
            .orEmpty()
        if (encoded.isBlank()) return BackendStepResult(false, "uiautomator", "instrumentation 未返回结果")
        return runCatching {
            val json = String(Base64.getUrlDecoder().decode(encoded))
            val value = Json.parseToJsonElement(json).jsonObject
            BackendStepResult(
                value["success"]?.jsonPrimitive?.content.toBoolean(),
                "uiautomator",
                value["message"]?.jsonPrimitive?.content.orEmpty(),
            )
        }.getOrElse { BackendStepResult(false, "uiautomator", "instrumentation 结果解析失败：${it.message}") }
    }

    private companion object {
        const val COMPONENT = "com.newchar.debug.sample/com.newchar.debug.touch.uiautomator.InputInstrumentation"
        const val RESULT_PREFIX = "INSTRUMENTATION_RESULT: lqits="
    }
}

/** 单条指令在一个执行通道中的结果。 */
data class BackendStepResult(
    val success: Boolean,
    val backend: String,
    val message: String,
)
