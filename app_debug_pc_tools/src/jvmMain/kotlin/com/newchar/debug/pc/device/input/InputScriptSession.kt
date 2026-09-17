package com.newchar.debug.pc.device.input

import com.newchar.debug.pc.config.DesktopAppSettingsStore
import com.newchar.debug.pc.executor.AdbCommandExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/** PC 端输入脚本运行状态。 */
enum class InputRunState {
    IDLE,
    PREPARING,
    RUNNING,
    PAUSED,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED,
}

/** 脚本窗口展示的不可变状态快照。 */
data class InputRunSnapshot(
    val state: InputRunState = InputRunState.IDLE,
    val scriptName: String = "",
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val backend: String = "",
    val message: String = "请选择 LQITS v1 脚本",
    val screenshotPath: String? = null,
    val logs: List<String> = emptyList(),
)

/** 管理 agent 推送、脚本进程、控制命令、断连和 adb input 兜底。 */
class InputScriptSession(
    private val executor: AdbCommandExecutor,
    private val deviceId: String,
    private val scope: CoroutineScope,
    private val configDirectory: String = DesktopAppSettingsStore.configDirectoryPath(),
) {
    private val mutableSnapshot = MutableStateFlow(InputRunSnapshot())
    private val processReference = AtomicReference<Process?>(null)
    private val writerReference = AtomicReference<BufferedWriter?>(null)
    private var runJob: Job? = null
    @Volatile
    private var fallbackRunner: AdbInputFallbackRunner? = null

    /** 对 UI 暴露只读运行状态。 */
    val snapshot: StateFlow<InputRunSnapshot> = mutableSnapshot.asStateFlow()

    /** 校验并异步执行用户选择的脚本文件。 */
    fun start(file: File) {
        if (runJob?.isActive == true) return
        runJob = scope.launch(Dispatchers.IO) {
            runCatching { executeFile(file) }.onFailure(::handleRunFailure)
        }
    }

    /** 暂停 agent 或 adb input 兜底执行。 */
    fun pause() {
        fallbackRunner?.pause() ?: sendCommand("PAUSE")
        updateState(InputRunState.PAUSED, "执行已暂停")
    }

    /** 恢复 agent 或 adb input 兜底执行。 */
    fun resume() {
        fallbackRunner?.resume() ?: sendCommand("RESUME")
        updateState(InputRunState.RUNNING, "继续执行")
    }

    /** 取消当前任务并释放设备端进程。 */
    fun cancel() {
        fallbackRunner?.cancel()
        sendCommand("CANCEL")
        runJob?.cancel()
        stopProcess()
        updateState(InputRunState.CANCELLED, "脚本已取消")
    }

    /** 关闭窗口时终止当前会话。 */
    fun close() {
        cancel()
        runJob = null
    }

    /** 读取脚本、执行 agent，并仅在启动阶段失败时整体兜底。 */
    private suspend fun executeFile(file: File) {
        val json = file.readText(Charsets.UTF_8)
        val script = parseInputScript(json)
        mutableSnapshot.value = InputRunSnapshot(
            state = InputRunState.PREPARING,
            scriptName = script.name.ifBlank { file.nameWithoutExtension },
            totalSteps = script.steps.size,
            message = "正在准备输入 agent",
        )
        try {
            executeAgent(json)
            fallbackRemainingSteps(script)
        } catch (startup: AgentStartupException) {
            stopProcess()
            appendLog("agent 启动失败，切换 adb input：${startup.message}")
            executeFallback(script)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            stopProcess()
            appendLog("agent 执行异常：${throwable.message ?: throwable::class.simpleName}")
            updateState(InputRunState.FAILED, "agent 执行异常，准备兜底剩余步骤")
            fallbackRemainingSteps(script)
        }
    }

    /** agent 中途失败时，仅从未确认完成的步骤开始兜底。 */
    private suspend fun fallbackRemainingSteps(script: InputScriptDocument) {
        val snapshot = mutableSnapshot.value
        if (snapshot.state != InputRunState.FAILED) return
        val offset = snapshot.currentStep.coerceIn(0, script.steps.size)
        if (offset >= script.steps.size) return
        appendLog("agent 中断，从第 ${offset + 1} 步切换 adb input")
        executeFallback(script.copy(steps = script.steps.drop(offset)), offset)
    }

    /** 推送 dex、启动 app_process 并进入状态读取循环。 */
    private suspend fun executeAgent(json: String) {
        val remoteDex = try {
            ensureAgentPushed()
        } catch (throwable: Throwable) {
            throw AgentStartupException(throwable.message ?: "agent 准备失败", throwable)
        }
        val process = startAgentProcess(remoteDex)
        processReference.set(process)
        val reader = process.inputStream.bufferedReader()
        awaitAgentReady(reader)
        writerReference.set(BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8)))
        if (!sendCommand("RUN|${encode(json)}")) throw AgentStartupException("无法发送脚本")
        updateState(InputRunState.RUNNING, "agent 已启动")
        readAgentStatus(reader)
    }

    /** 跳过系统警告输出，直到读取到 READY 或明确失败。 */
    private fun awaitAgentReady(reader: java.io.BufferedReader) {
        repeat(MAX_STARTUP_LINES) {
            val line = try {
                reader.readLine()
            } catch (throwable: Throwable) {
                throw AgentStartupException(throwable.message ?: "读取 agent 输出失败", throwable)
            } ?: throw AgentStartupException("agent 无启动输出")
            appendLog(line)
            if (line.startsWith("READY|")) return
            if (line.startsWith("FATAL|")) throw AgentStartupException(decode(line.substringAfter('|')))
        }
        throw AgentStartupException("agent 未返回 READY")
    }

    /** 持续读取状态直到任务进入终态或进程断开。 */
    private suspend fun readAgentStatus(reader: java.io.BufferedReader) {
        var terminal = false
        while (!terminal) {
            val line = try {
                reader.readLine()
            } catch (throwable: Throwable) {
                appendLog("读取 agent 状态失败：${throwable.message ?: throwable::class.simpleName}")
                null
            } ?: break
            terminal = handleAgentLine(line)
        }
        if (!terminal && mutableSnapshot.value.state !in terminalStates()) {
            captureFailureScreenshot()
            updateState(InputRunState.FAILED, "agent 连接中断，未重放已执行步骤")
        }
        sendCommand("STOP")
        stopProcess()
    }

    /** 解析 agent 的 STATUS、CONTROL 和 FATAL 行。 */
    private suspend fun handleAgentLine(line: String): Boolean {
        appendLog(line)
        if (line.startsWith("STATUS|")) return handleStatusFields(line.split('|'))
        if (line.startsWith("CONTROL|PAUSED")) updateState(InputRunState.PAUSED, "执行已暂停")
        if (line.startsWith("CONTROL|RESUMED")) updateState(InputRunState.RUNNING, "继续执行")
        if (line.startsWith("FATAL|")) {
            captureFailureScreenshot()
            updateState(InputRunState.FAILED, decode(line.substringAfter('|')))
            return true
        }
        return false
    }

    /** 将 STATUS 协议字段写入 UI 状态。 */
    private suspend fun handleStatusFields(parts: List<String>): Boolean {
        if (parts.size < 7) return false
        val stateName = parts[2]
        val step = parts[3].toIntOrNull() ?: 0
        val total = parts[4].toIntOrNull() ?: mutableSnapshot.value.totalSteps
        val backend = parts[5]
        val message = decode(parts[6])
        val state = stateForProtocol(stateName)
        mutableSnapshot.value = mutableSnapshot.value.copy(
            state = state, currentStep = step, totalSteps = total,
            backend = backend, message = message,
        )
        if (stateName == "STEP_FAILED" || state == InputRunState.FAILED) captureFailureScreenshot()
        return state in terminalStates()
    }

    /** 使用 PC 端 adb input 兜底执行脚本。 */
    private suspend fun executeFallback(script: InputScriptDocument, stepOffset: Int = 0) {
        updateBackendState("adb_input", InputRunState.RUNNING, "使用 adb input 兜底")
        val runner = AdbInputFallbackRunner(executor, deviceId, script) { progress ->
            handleFallbackProgress(progress, stepOffset)
        }
        fallbackRunner = runner
        val success = runner.run()
        fallbackRunner = null
        if (mutableSnapshot.value.state == InputRunState.CANCELLED) return
        if (success) updateState(InputRunState.COMPLETED, "adb input 兜底执行完成")
        else updateState(InputRunState.COMPLETED_WITH_ERRORS, "全部步骤已尝试，部分 adb input 执行失败")
    }

    /** 将 adb input 步骤进度写入 UI，并在失败时截图。 */
    private suspend fun handleFallbackProgress(progress: FallbackProgress, stepOffset: Int) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            state = InputRunState.RUNNING,
            currentStep = progress.index + stepOffset,
            backend = "adb_input",
            message = progress.message,
        )
        appendLog("${progress.state}|${progress.index}|${progress.message}")
        if (!progress.success) captureFailureScreenshot()
    }

    /** 按 dex 内容哈希缓存并推送 agent。 */
    private suspend fun ensureAgentPushed(): String {
        val bytes = loadAgentDex()
        val md5 = md5Hex(bytes)
        val directory = File(configDirectory, "agent").also(File::mkdirs)
        val local = File(directory, "input_tool_$md5.dex")
        if (!local.isFile) local.writeBytes(bytes)
        val remote = "/data/local/tmp/input_tool_$md5.dex"
        val exists = executor.shell(deviceId, "test", "-f", remote).isSuccess
        if (!exists) {
            val result = executor.adb("-s", deviceId, "push", local.absolutePath, remote)
            if (!result.isSuccess) throw AgentStartupException(result.error.ifBlank { result.output })
        }
        return remote
    }

    /** 启动设备端 app_process 输入 agent。 */
    private fun startAgentProcess(remoteDex: String): Process {
        val command = executor.resolveAdbCommand(
            "-s", deviceId, "shell", "CLASSPATH=$remoteDex", "app_process",
            "/data/local/tmp", MAIN_CLASS,
        )
        return try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (throwable: Throwable) {
            throw AgentStartupException(throwable.message ?: "无法启动 adb", throwable)
        }
    }

    /** 从桌面资源读取输入 agent dex。 */
    private fun loadAgentDex(): ByteArray {
        val stream = javaClass.getResourceAsStream(DEX_RESOURCE)
            ?: throw AgentStartupException("内置 input_tool.dex 缺失")
        return stream.use { it.readBytes() }
    }

    /** 保存当前设备屏幕到失败现场目录。 */
    private suspend fun captureFailureScreenshot() {
        if (mutableSnapshot.value.screenshotPath != null) return
        runCatching { captureFailureScreenshotSafely() }
    }

    /** 执行截图进程并确保失败时也能回收。 */
    private fun captureFailureScreenshotSafely() {
        val directory = File(configDirectory, "input_failures").also(File::mkdirs)
        val file = File(directory, "${safeFileName()}_${System.currentTimeMillis()}.png")
        val process = ProcessBuilder(
            executor.resolveAdbCommand("-s", deviceId, "exec-out", "screencap", "-p"),
        ).start()
        try {
            val bytes = process.inputStream.use { it.readBytes() }
            if (process.waitFor() == 0 && bytes.isNotEmpty()) {
                file.writeBytes(bytes)
                mutableSnapshot.value = mutableSnapshot.value.copy(screenshotPath = file.absolutePath)
            }
        } finally {
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
    }

    /** 向设备端 agent 写入一行控制命令。 */
    private fun sendCommand(command: String): Boolean {
        val writer = writerReference.get() ?: return false
        return runCatching {
            writer.apply {
                write(command)
                newLine()
                flush()
            }
        }.isSuccess
    }

    /** 关闭输入输出并终止当前 adb 进程。 */
    private fun stopProcess() {
        runCatching { writerReference.getAndSet(null)?.close() }
        processReference.getAndSet(null)?.let { process ->
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
    }

    /** 处理脚本解析、执行与协程取消异常。 */
    private fun handleRunFailure(throwable: Throwable) {
        stopProcess()
        if (throwable is CancellationException) return
        appendLog(throwable.stackTraceToString())
        updateState(InputRunState.FAILED, throwable.message ?: "脚本执行异常")
    }

    /** 更新状态并保留其余进度字段。 */
    private fun updateState(state: InputRunState, message: String) {
        mutableSnapshot.value = mutableSnapshot.value.copy(state = state, message = message)
    }

    /** 同时更新后端、状态和提示。 */
    private fun updateBackendState(backend: String, state: InputRunState, message: String) {
        mutableSnapshot.value = mutableSnapshot.value.copy(backend = backend, state = state, message = message)
    }

    /** 追加日志并限制内存中的日志行数。 */
    private fun appendLog(line: String) {
        val logs = (mutableSnapshot.value.logs + line).takeLast(MAX_LOG_LINES)
        mutableSnapshot.value = mutableSnapshot.value.copy(logs = logs)
    }

    /** 将协议状态映射为 UI 状态。 */
    private fun stateForProtocol(value: String): InputRunState = when (value) {
        "COMPLETED" -> InputRunState.COMPLETED
        "COMPLETED_WITH_ERRORS" -> InputRunState.COMPLETED_WITH_ERRORS
        "FAILED" -> InputRunState.FAILED
        "CANCELLED" -> InputRunState.CANCELLED
        else -> InputRunState.RUNNING
    }

    /** 返回不会继续执行的终态集合。 */
    private fun terminalStates(): Set<InputRunState> =
        setOf(
            InputRunState.COMPLETED,
            InputRunState.COMPLETED_WITH_ERRORS,
            InputRunState.FAILED,
            InputRunState.CANCELLED,
        )

    /** 生成可安全用于截图文件名的脚本名称。 */
    private fun safeFileName(): String = mutableSnapshot.value.scriptName
        .ifBlank { "script" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** 对脚本内容计算 MD5，作为 dex 缓存键。 */
    private fun md5Hex(bytes: ByteArray): String = MessageDigest.getInstance("MD5")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    /** 将文本编码为 agent 协议 Base64。 */
    private fun encode(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    /** 解码 agent 协议 Base64。 */
    private fun decode(value: String): String = runCatching {
        String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    }.getOrDefault(value)

    private companion object {
        const val DEX_RESOURCE = "/agent/input_tool.dex"
        const val MAIN_CLASS = "com.newchar.probe.input.InputToolMain"
        const val MAX_LOG_LINES = 120
        const val MAX_STARTUP_LINES = 20
    }
}

/** 仅表示 agent 尚未开始执行，可安全整体切换到 adb input。 */
private class AgentStartupException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)