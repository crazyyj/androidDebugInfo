package com.newchar.debug.pc.executor

import com.newchar.debug.pc.device.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

class AdbCommandExecutor private constructor(
    private val workDir: Path? = null,
    private val environment: Map<String, String> = emptyMap(),
    private val preferredAdbExecutablePath: String? = null,
) : CommandExecutor {

    companion object {
        fun create(
            workDirPath: String? = null,
            adbExecutablePath: String? = null,
        ): AdbCommandExecutor =
            AdbCommandExecutor(
                workDir = workDirPath?.takeIf(String::isNotBlank)?.let(::Path),
                preferredAdbExecutablePath = adbExecutablePath?.takeIf(String::isNotBlank),
            )

        fun withEnvironment(
            env: Map<String, String>,
            adbExecutablePath: String? = null,
        ): AdbCommandExecutor =
            AdbCommandExecutor(
                environment = env,
                preferredAdbExecutablePath = adbExecutablePath?.takeIf(String::isNotBlank),
            )

        fun createWithPath(
            path: Path?,
            adbExecutablePath: String? = null,
        ): AdbCommandExecutor =
            AdbCommandExecutor(
                workDir = path,
                preferredAdbExecutablePath = adbExecutablePath?.takeIf(String::isNotBlank),
            )
    }

    override suspend fun execute(vararg command: String): CommandResult =
        withContext(Dispatchers.IO) {
            executeInternal(command.asList())
        }

    override fun syncExecute(vararg command: String): CommandResult =
        executeInternal(command.asList())

    override fun stream(vararg command: String): Flow<String> = flow {
        val commandList = command.asList()
        if (commandList.isEmpty()) {
            throw IllegalArgumentException("Command is empty")
        }
        val process = createProcessBuilder(commandList).start()
        try {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    emit(line)
                }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw IllegalStateException("Command failed with exit code $exitCode: ${commandList.joinToString(" ")}")
            }
        } finally {
            process.destroy()
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun adb(vararg args: String): CommandResult = execute(*resolveAdbCommand(*args).toTypedArray())

    override fun adbSync(vararg args: String): CommandResult = syncExecute(*resolveAdbCommand(*args).toTypedArray())

    override fun adbStream(vararg args: String): Flow<String> {
        return if (args.contentEquals(arrayOf("track-devices"))) {
            trackDevicesFlow()
        } else {
            stream(*resolveAdbCommand(*args).toTypedArray())
        }
    }

    override suspend fun shell(deviceId: String?, vararg command: String): CommandResult {
        val adbCommand = buildList {
            add(resolveAdbExecutablePath())
            if (!deviceId.isNullOrBlank()) {
                add("-s")
                add(deviceId)
            }
            add("shell")
            addAll(command)
        }
        return execute(*adbCommand.toTypedArray())
    }

    override fun resolveAdbCommand(vararg args: String): List<String> = buildList {
        add(resolveAdbExecutablePath())
        addAll(args)
    }

    override fun resolveAdbExecutablePath(): String {
        val effectiveEnvironment = if (environment.isEmpty()) {
            System.getenv()
        } else {
            environment
        }
        return AdbExecutableResolver.resolve(preferredAdbExecutablePath, effectiveEnvironment)
    }

    private fun trackDevicesFlow(): Flow<String> = flow {
        val process = createProcessBuilder(resolveAdbCommand("track-devices")).start()
        try {
            val input = process.inputStream
            while (true) {
                currentCoroutineContext().ensureActive()
                val lengthHeader = readExactAscii(input, 4) ?: break
                val payloadLength = lengthHeader.toIntOrNull(16) ?: continue
                if (payloadLength <= 0) {
                    emit("")
                    continue
                }
                val payload = readExactBytes(input, payloadLength) ?: break
                emit(payload.decodeToString())
            }
        } finally {
            process.destroy()
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun readExactAscii(input: java.io.InputStream, count: Int): String? {
        val bytes = readExactBytes(input, count) ?: return null
        return bytes.decodeToString()
    }

    private fun readExactBytes(input: java.io.InputStream, count: Int): ByteArray? {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read <= 0) {
                return null
            }
            offset += read
        }
        return buffer
    }

    private fun resolveWorkDirectory(): Path? {
        val path = workDir ?: return null
        val metadata = SystemFileSystem.metadataOrNull(path) ?: return null
        return path.takeIf { metadata.isDirectory }
    }

    private fun executeInternal(command: List<String>): CommandResult {
        if (command.isEmpty()) {
            return CommandResult.failure(-1, "Command is empty")
        }

        return runCatching {
            val process = createProcessBuilder(command).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                CommandResult.success(output.trim())
            } else {
                CommandResult.failure(exitCode, output.trim())
            }
        }.getOrElse { throwable ->
            val detail = buildString {
                append(throwable.message ?: "Unknown error")
                workDir?.toString()?.let {
                    append(" (workDir=")
                    append(it)
                    append(')')
                }
            }
            CommandResult.failure(-1, detail)
        }
    }

    private fun createProcessBuilder(command: List<String>): ProcessBuilder {
        return ProcessBuilder(command)
            .redirectErrorStream(true)
            .apply {
                resolveWorkDirectory()?.let { directory(java.io.File(it.toString())) }
                if (environment.isNotEmpty()) {
                    environment().putAll(environment)
                }
            }
    }
}
