package com.newchar.debug.pc.device.wifi

import com.newchar.debug.pc.config.DesktopAppSettingsStore
import com.newchar.debug.pc.device.CommandResult
import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/** WiFi 当前状态。 */
data class WifiStatus(val enabled: Boolean, val connectedSsid: String = "")

/** 可展示的 WiFi 扫描项。 */
data class WifiNetwork(
    val ssid: String,
    val bssid: String = "",
    val signalLevel: Int? = null,
    val security: String = "",
)

/** WiFi 开关或连接操作的可展示结果。 */
data class WifiSwitchResult(val success: Boolean, val message: String)

/** PC 端通过 adb shell 运行 WiFi 管理指令。 */
class WifiShellExecutor(
    private val executor: CommandExecutor,
    private val configDirectory: String = DesktopAppSettingsStore.configDirectoryPath(),
) {

    /** 使用全版本可用的 svc wifi 切换 WiFi 开关。 */
    suspend fun setWifiEnabled(deviceId: String, enabled: Boolean): WifiSwitchResult {
        return executeSimple(deviceId, "svc", "wifi", if (enabled) "enable" else "disable")
    }

    /** 查询 WiFi 开关与当前已连接 SSID。 */
    suspend fun status(deviceId: String): Result<WifiStatus> = runCatching {
        if (sdkInt(deviceId) >= 30) parseCmdStatus(executor.shell(deviceId, "cmd", "wifi", "status"))
        else parseAgentStatus(runAgent(deviceId, "status"))
    }

    /** 扫描附近 WiFi；新系统优先调用 cmd wifi，旧系统使用 shell UID Dex。 */
    suspend fun scan(deviceId: String): Result<List<WifiNetwork>> = runCatching {
        if (sdkInt(deviceId) >= 30) parseCmdScan(scanWithCmd(deviceId))
        else parseAgentScan(runAgent(deviceId, "scan"))
    }

    /** 连接 WiFi；密码仅作为本次 adb 参数传递，不会写入 PC 本地文件。 */
    suspend fun connect(deviceId: String, ssid: String, password: String): WifiSwitchResult {
        if (ssid.isBlank()) return WifiSwitchResult(false, "SSID 不能为空")
        return if (sdkInt(deviceId) >= 30) {
            val security = if (password.isBlank()) "open" else "wpa2"
            executeSimple(deviceId, "cmd", "wifi", "connect-network", ssid, security, password)
        } else parseAgentResult(runAgent(deviceId, "connect", ssid, password))
    }

    /** 连接设备已保存的 WiFi，不读取或保存密码。 */
    suspend fun connectSaved(deviceId: String, ssid: String): WifiSwitchResult {
        if (ssid.isBlank()) return WifiSwitchResult(false, "SSID 不能为空")
        return if (sdkInt(deviceId) >= 30) {
            executeSimple(deviceId, "cmd", "wifi", "connect-network", ssid)
        } else parseAgentResult(runAgent(deviceId, "connect-saved", ssid))
    }

    /** 断开当前 WiFi。 */
    suspend fun disconnect(deviceId: String): WifiSwitchResult = if (sdkInt(deviceId) >= 30) {
        executeSimple(deviceId, "cmd", "wifi", "disconnect-network")
    } else parseAgentResult(runAgent(deviceId, "disconnect"))

    /** 删除当前设备保存的指定 WiFi 配置。 */
    suspend fun forget(deviceId: String, ssid: String): WifiSwitchResult {
        if (ssid.isBlank()) return WifiSwitchResult(false, "SSID 不能为空")
        return if (sdkInt(deviceId) >= 30) {
            executeSimple(deviceId, "cmd", "wifi", "forget-network", ssid)
        } else parseAgentResult(runAgent(deviceId, "forget", ssid))
    }

    /** 执行不需要解析返回结构的 adb shell 指令。 */
    private suspend fun executeSimple(deviceId: String, vararg command: String): WifiSwitchResult {
        if (deviceId.isBlank()) return WifiSwitchResult(false, "设备标识为空")
        val result = runCatching { executor.shell(deviceId, *command) }
            .getOrElse { return WifiSwitchResult(false, it.message ?: "ADB 命令执行失败") }
        return result.toWifiResult()
    }

    /** 获取设备 API Level，无法获取时按新系统命令路径处理。 */
    private suspend fun sdkInt(deviceId: String): Int {
        return executor.shell(deviceId, "getprop", "ro.build.version.sdk").output.trim().toIntOrNull() ?: 30
    }

    /** 触发新系统扫描，并兼容厂商保留的 scan-results 子命令。 */
    private suspend fun scanWithCmd(deviceId: String): CommandResult {
        executor.shell(deviceId, "cmd", "wifi", "start-scan")
        val modern = executor.shell(deviceId, "cmd", "wifi", "list-scan-results")
        return if (modern.isSuccess) modern else executor.shell(deviceId, "cmd", "wifi", "scan-results")
    }

    /** 执行 shell UID Dex，并返回完整输出。 */
    private suspend fun runAgent(deviceId: String, vararg args: String): CommandResult {
        val remoteDex = ensureAgentPushed(deviceId)
        val script = buildString {
            append("CLASSPATH=").append(shellQuote(remoteDex)).append(' ')
            append("app_process /data/local/tmp com.newchar.probe.wifi.WifiToolMain")
            args.forEach { append(' ').append(shellQuote(it)) }
        }
        return executor.shell(deviceId, "sh", "-c", script)
    }

    /** 将内置 Dex 按 MD5 缓存并推送至设备临时目录。 */
    private suspend fun ensureAgentPushed(deviceId: String): String = withContext(Dispatchers.IO) {
        val bytes = javaClass.getResourceAsStream(DEX_RESOURCE)?.use { it.readBytes() }
            ?: error("内置 WiFi agent 缺失")
        val checksum = md5(bytes)
        val directory = File(configDirectory, "agent").also { it.mkdirs() }
        val localDex = File(directory, "wifi_tool_$checksum.dex")
        if (!localDex.isFile) localDex.writeBytes(bytes)
        val marker = File(directory, "wifi_$deviceId.txt")
        val remoteDex = "/data/local/tmp/wifi_tool_$checksum.dex"
        if (marker.takeIf(File::isFile)?.readText()?.trim() != checksum) {
            val push = executor.adb("-s", deviceId, "push", localDex.absolutePath, remoteDex)
            check(push.isSuccess) { push.error.ifBlank { push.output.ifBlank { "WiFi agent 推送失败" } } }
            marker.writeText(checksum)
        }
        remoteDex
    }

    /** 解析 Dex 输出中的 WiFi 状态。 */
    private fun parseAgentStatus(result: CommandResult): WifiStatus {
        check(result.isSuccess) { result.error.ifBlank { result.output } }
        val parts = result.output.lineSequence().firstOrNull { it.startsWith("STATUS|") }?.split('|')
            ?: error("未返回 WiFi 状态")
        return WifiStatus(parts.getOrNull(1).toBoolean(), decode(parts.getOrNull(2).orEmpty()))
    }

    /** 解析 Dex 输出的扫描项。 */
    private fun parseAgentScan(result: CommandResult): List<WifiNetwork> {
        check(result.isSuccess) { result.error.ifBlank { result.output } }
        check(!result.output.contains("FATAL|")) { result.output.lineSequence().first { it.startsWith("FATAL|") } }
        return result.output.lineSequence().mapNotNull(::parseAgentNetwork).distinctBy { it.ssid }.toList()
    }

    /** 解析一条 Dex 扫描输出。 */
    private fun parseAgentNetwork(line: String): WifiNetwork? {
        val parts = line.split('|')
        if (parts.size < 5 || parts[0] != "SCAN") return null
        return WifiNetwork(decode(parts[1]), decode(parts[2]), parts[3].toIntOrNull(), decode(parts[4]))
            .takeIf { it.ssid.isNotBlank() }
    }

    /** 解析 Android 11+ cmd wifi status 输出。 */
    private fun parseCmdStatus(result: CommandResult): WifiStatus {
        check(result.isSuccess) { result.error.ifBlank { result.output } }
        val output = result.output
        val enabled = !Regex("(?i)wifi\\s+(is\\s+)?disabled|wifi\\s+state:\\s*disabled").containsMatchIn(output)
        val ssid = Regex("(?i)ssid\\s*[:=]\\s*\\\"?([^\\\"\\n]+)").find(output)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        return WifiStatus(enabled, ssid)
    }

    /** 解析 Android 11+ cmd wifi list-scan-results 输出。 */
    private fun parseCmdScan(result: CommandResult): List<WifiNetwork> {
        check(result.isSuccess) { result.error.ifBlank { result.output } }
        return result.output.lineSequence().mapNotNull(::parseCmdNetwork).distinctBy { it.ssid }.toList()
    }

    /** 按命令行的首段 SSID 与 BSSID 解析扫描结果。 */
    private fun parseCmdNetwork(line: String): WifiNetwork? {
        val match = Regex("^(.+?)\\s{2,}([0-9A-Fa-f:]{17})\\s+(.*)$").find(line.trim()) ?: return null
        return WifiNetwork(ssid = match.groupValues[1].trim(), bssid = match.groupValues[2], security = match.groupValues[3])
    }

    /** 解析 Dex 统一操作结果。 */
    private fun parseAgentResult(result: CommandResult): WifiSwitchResult {
        if (!result.isSuccess) return result.toWifiResult()
        val parts = result.output.lineSequence().lastOrNull { it.startsWith("RESULT|") }?.split('|')
        return WifiSwitchResult(parts?.getOrNull(1).toBoolean(), parts?.getOrNull(2).orEmpty().ifBlank { "操作失败" })
    }

    /** 将命令执行结果转换成可展示文字。 */
    private fun CommandResult.toWifiResult(): WifiSwitchResult {
        val message = output.ifBlank { error }.trim()
        return WifiSwitchResult(isSuccess, message.ifBlank { if (isSuccess) "命令已发送" else "命令执行失败" })
    }

    /** 为设备端 sh -c 参数做单引号转义。 */
    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\\"'\\\"'")}'"

    /** 计算 Dex 内容 MD5，用于本地和设备端缓存判断。 */
    private fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    /** 解码 Dex 输出的 Base64 字段。 */
    private fun decode(value: String): String = runCatching {
        String(Base64.getDecoder().decode(value))
    }.getOrDefault("")

    private companion object {
        const val DEX_RESOURCE = "/agent/wifi_tool.dex"
    }
}
