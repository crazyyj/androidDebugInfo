package com.newchar.debug.pc.device

import com.newchar.debug.pc.executor.AdbCommandExecutor

/** PC 工具可发起的 Android 企业设备管理角色。 */
internal enum class DeviceOwnershipMode(
    val label: String,
    val command: String,
    val confirmationToken: String,
) {
    DEVICE_OWNER("设备所有者（DO）", "set-device-owner", "DO"),
    PROFILE_OWNER("资料所有者（PO）", "set-profile-owner", "PO"),
}

/** 一次 Owner 查询或设置操作的结果。 */
internal data class DeviceOwnershipResult(
    val success: Boolean,
    val message: String,
    val ownerOutput: String = "",
)

/**
 * 通过 adb dpm 管理 Device Owner 与 Profile Owner。
 *
 * 设置操作完成后必须重新查询系统 Owner 列表，不能仅以 dpm 命令退出码作为成功依据。
 */
internal class DeviceOwnershipManager(private val executor: AdbCommandExecutor) {

    /** 查询设备当前登记的 DO/PO，并返回可直接展示给用户的结果。 */
    suspend fun listOwners(deviceId: String): DeviceOwnershipResult {
        val result = executor.adb("-s", deviceId, "shell", "dpm", "list-owners")
        if (result.isSuccess) return parseOwnersOutput(result.output)
        return listOwnersFromPolicyDump(deviceId, result.error.ifBlank { result.output })
    }

    /** 设置指定组件为 DO 或 PO，并以 dpm list-owners 读回结果验证。 */
    suspend fun provision(
        deviceId: String,
        mode: DeviceOwnershipMode,
        component: String,
        userId: String,
    ): DeviceOwnershipResult {
        val validationError = validateRequest(mode, component, userId)
        if (validationError != null) return DeviceOwnershipResult(false, validationError)
        val result = executor.adb(*buildProvisionCommand(deviceId, mode, component, userId).toTypedArray())
        val owners = listOwners(deviceId)
        return buildProvisionResult(result.isSuccess, result.output.ifBlank { result.error }, owners, component, mode)
    }

    /** 校验组件和用户参数，避免将无效参数交给 dpm。 */
    private fun validateRequest(mode: DeviceOwnershipMode, component: String, userId: String): String? {
        if (component.isBlank() || !component.contains('/') || component.any(Char::isWhitespace)) {
            return "管理员组件格式应为 包名/接收器类名"
        }
        if (mode == DeviceOwnershipMode.PROFILE_OWNER && userId != "current" && userId.toIntOrNull() == null) {
            return "PO 用户应填写 current 或数字用户 ID"
        }
        return null
    }

    /** 构建不依赖 shell 字符串拼接的 adb dpm 参数列表。 */
    private fun buildProvisionCommand(
        deviceId: String,
        mode: DeviceOwnershipMode,
        component: String,
        userId: String,
    ): List<String> = buildList {
        addAll(listOf("-s", deviceId, "shell", "dpm", mode.command, "--user"))
        add(if (mode == DeviceOwnershipMode.DEVICE_OWNER) "0" else userId)
        addAll(listOf("--name", "NewChar Debug", component))
    }

    /** 根据系统读回的 Owner 列表生成操作结论。 */
    private fun buildProvisionResult(
        commandSucceeded: Boolean,
        commandOutput: String,
        owners: DeviceOwnershipResult,
        component: String,
        mode: DeviceOwnershipMode,
    ): DeviceOwnershipResult {
        val packageName = component.substringBefore('/')
        if (commandSucceeded && owners.success && owners.ownerOutput.contains(packageName)) {
            return DeviceOwnershipResult(true, "${mode.label}设置成功，${owners.message}")
        }
        val detail = commandOutput.trim().ifBlank { owners.message }
        return DeviceOwnershipResult(false, "${mode.label}设置失败：$detail")
    }

    /** 在 dpm 不提供 list-owners 时，从系统 device_policy 转储中读取 Owner。 */
    private suspend fun listOwnersFromPolicyDump(deviceId: String, dpmError: String): DeviceOwnershipResult {
        val result = executor.adb("-s", deviceId, "shell", "dumpsys", "device_policy")
        if (!result.isSuccess) return DeviceOwnershipResult(
            false,
            "无法读取 DO/PO：${result.error.ifBlank { dpmError.ifBlank { "dpm 查询失败" } }}",
        )
        val ownerLines = result.output.lineSequence()
            .filter { it.contains("device owner", true) || it.contains("profile owner", true) }
            .toList()
        return if (ownerLines.isEmpty()) {
            DeviceOwnershipResult(
                true,
                "当前未检测到 DO/PO（系统不支持 list-owners，已使用 device_policy 验证）",
                result.output,
            )
        } else {
            DeviceOwnershipResult(
                true,
                "当前 Owner：${ownerLines.joinToString("；") { it.trim() }}",
                result.output,
            )
        }
    }

    /** 解析标准 dpm list-owners 输出。 */
    private fun parseOwnersOutput(output: String): DeviceOwnershipResult {
        val content = output.trim()
        return if (content.isBlank() || content.contains("no device owner", ignoreCase = true)) {
            DeviceOwnershipResult(true, "当前未检测到 DO/PO", content)
        } else {
            DeviceOwnershipResult(true, "当前 Owner：$content", content)
        }
    }
}
