package com.newchar.debug.pc.device.logcat

import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.seconds

class LogcatCollector(
    private val executor: CommandExecutor,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CollectorState.IDLE)
    val state: StateFlow<CollectorState> = _state.asStateFlow()

    private val _collectedEntries = MutableStateFlow<List<LogcatEntry>>(emptyList())
    val collectedEntries: StateFlow<List<LogcatEntry>> = _collectedEntries.asStateFlow()

    private val _crashLogs = MutableStateFlow<List<LogcatEntry>>(emptyList())
    val crashLogs: StateFlow<List<LogcatEntry>> = _crashLogs.asStateFlow()

    private val _deviceLogCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val deviceLogCounts: StateFlow<Map<String, Int>> = _deviceLogCounts.asStateFlow()

    private var collectionJob: Job? = null
    var startTime: Long = 0L
        private set
    private lateinit var currentConfig: LogcatCollectConfig
    private val logChannel = Channel<LogcatEntry>(Channel.UNLIMITED)
    private val crashChannel = Channel<LogcatEntry>(Channel.UNLIMITED)

    enum class CollectorState {
        IDLE,
        COLLECTING,
        PAUSED,
        STOPPED,
        ERROR
    }

    data class LogcatCollectConfig(
        val deviceIds: List<String>,
        val packageName: String = "",
        val selectedTags: Set<String> = emptySet(),
        val saveCrashLogs: Boolean = true,
        val minLogLevel: LogLevel = LogLevel.VERBOSE,
        val outputDirectory: String = "",
    )

    data class CollectionResult(
        val success: Boolean,
        val totalEntries: Int,
        val crashCount: Int,
        val duration: Long,
        val outputDir: String,
        val error: String? = null,
    )

    suspend fun startCollection(config: LogcatCollectConfig) {
        if (_state.value == CollectorState.COLLECTING) {
            return
        }

        currentConfig = config
        startTime = System.currentTimeMillis()
        _state.value = CollectorState.COLLECTING
        _collectedEntries.value = emptyList()
        _crashLogs.value = emptyList()
        _deviceLogCounts.value = emptyMap<String, Int>().withDefault { 0 }

        collectionJob = scope.launch(Dispatchers.IO) {
            try {
                collectLogcatFromDevices(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = CollectorState.ERROR
                e.printStackTrace()
            }
        }
    }

    suspend fun stopCollection(): CollectionResult {
        if (_state.value != CollectorState.COLLECTING) {
            return CollectionResult(
                success = false,
                totalEntries = 0,
                crashCount = 0,
                duration = 0L,
                outputDir = "",
                error = "Not collecting"
            )
        }

        collectionJob?.cancelAndJoin()
        collectionJob = null
        _state.value = CollectorState.STOPPED

        val duration = System.currentTimeMillis() - startTime
        val entries = _collectedEntries.value
        val crashes = _crashLogs.value

        val result = CollectionResult(
            success = true,
            totalEntries = entries.size,
            crashCount = crashes.size,
            duration = duration,
            outputDir = currentConfig.outputDirectory,
        )

        if (currentConfig.outputDirectory.isNotBlank()) {
            saveLogsToFile(entries, crashes, currentConfig.outputDirectory, config = currentConfig)
        }

        return result
    }

    private suspend fun collectLogcatFromDevices(config: LogcatCollectConfig) {
        val deviceJobs = config.deviceIds.map { deviceId ->
            scope.launch(Dispatchers.IO) {
                collectFromSingleDevice(deviceId, config)
            }
        }

        scope.launch {
            processLogEntries(config)
        }

        deviceJobs.forEach { it.join() }
        logChannel.close()
        crashChannel.close()
    }

    private suspend fun collectFromSingleDevice(deviceId: String, config: LogcatCollectConfig) {
        withContext(Dispatchers.IO) {
            runCatching {
                executor.adb("-s", deviceId, "logcat", "-c")
            }

            val logcatArgs = buildList {
                add("-s")
                add(deviceId)
                add("logcat")
                add("-v")
                add("time")

                if (config.packageName.isNotBlank()) {
                    add("--pid=$(pidof ${config.packageName})")
                }

                if (config.selectedTags.isNotEmpty()) {
                    add("-s")
                    addAll(config.selectedTags.flatMap { listOf("$it:${config.minLogLevel.shortLabel}") })
                    add("*:S")
                }
            }

            executor.stream(*logcatArgs.toTypedArray())
                .onEach { line ->
                    val entry = parseLogcatLine(line, deviceId)
                    if (entry != null) {
                        logChannel.trySend(entry)

                        if (config.saveCrashLogs && CrashType.isCrashLine(line) != null) {
                            crashChannel.trySend(entry.copy(rawLine = line))
                        }
                    }
                }
                .catch { e ->
                    if (e !is CancellationException) {
                        println("Error collecting logs from $deviceId: ${e.message}")
                    }
                }
                .collect {}
        }
    }

    private suspend fun processLogEntries(config: LogcatCollectConfig) {
        val allEntries = mutableListOf<LogcatEntry>()
        val crashEntries = mutableListOf<LogcatEntry>()
        val counts = mutableMapOf<String, Int>().withDefault { 0 }

        for (entry in logChannel) {
            allEntries.add(entry)
            counts[entry.pid.toString()] = counts.getValue(entry.pid.toString()) + 1
            _collectedEntries.value = allEntries.toList()
            _deviceLogCounts.value = counts.toMap()
        }

        for (entry in crashChannel) {
            crashEntries.add(entry)
            _crashLogs.value = crashEntries.toList()
        }
    }

    private fun parseLogcatLine(line: String, deviceId: String): LogcatEntry? {
        if (line.isBlank()) return null

        val regex = """(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFS])\s+(.+?):\s+(.*)""".toRegex()
        val match = regex.find(line) ?: return null

        return try {
            val (timestamp, pidStr, tidStr, levelStr, tag, message) = match.destructured
            LogcatEntry(
                timestamp = timestamp,
                pid = pidStr.toIntOrNull() ?: 0,
                tid = tidStr.toIntOrNull() ?: 0,
                level = LogLevel.fromString(levelStr),
                tag = tag.trim(),
                message = message.trim(),
                rawLine = line,
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveLogsToFile(
        entries: List<LogcatEntry>,
        crashes: List<LogcatEntry>,
        outputDir: String,
        config: LogcatCollectConfig,
    ) {
        withContext(Dispatchers.IO) {
            val dir = File(outputDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val timeString = "${timestamp.year}${timestamp.monthNumber.toString().padStart(2, '0')}" +
                    "${timestamp.dayOfMonth.toString().padStart(2, '0')}_${timestamp.hour.toString().padStart(2, '0')}" +
                    "${timestamp.minute.toString().padStart(2, '0')}${timestamp.second.toString().padStart(2, '0')}"

            File(dir, "all_logs_$timeString.log").bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                entries.forEach { entry ->
                    writer.appendLine(entry.rawLine)
                }
            }

            if (crashes.isNotEmpty() && config.saveCrashLogs) {
                File(dir, "crash_logs_$timeString.log").bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    writer.appendLine("=== Crash Logs ===")
                    writer.appendLine("Total crashes: ${crashes.size}")
                    writer.appendLine("Collection time: ${Clock.System.now()}")
                    writer.appendLine("")
                    crashes.forEachIndexed { index, crash ->
                        writer.appendLine("--- Crash #$index ---")
                        writer.appendLine(crash.rawLine)
                        writer.appendLine("")
                    }
                }
            }

            File(dir, "metadata_$timeString.txt").bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.appendLine("Logcat Collection Metadata")
                writer.appendLine("=========================")
                writer.appendLine("Start Time: ${java.util.Date(startTime)}")
                writer.appendLine("End Time: ${java.util.Date()}")
                writer.appendLine("Duration: ${(System.currentTimeMillis() - startTime) / 1000}s")
                writer.appendLine("Devices: ${config.deviceIds.joinToString(", ")}")
                writer.appendLine("Package: ${config.packageName.ifBlank { "N/A" }}")
                writer.appendLine("Tags: ${config.selectedTags.joinToString(", ")}")
                writer.appendLine("Total Entries: ${entries.size}")
                writer.appendLine("Crash Entries: ${crashes.size}")
                writer.appendLine("Min Log Level: ${config.minLogLevel.label}")
            }
        }
    }

    fun clearCollectedData() {
        _collectedEntries.value = emptyList()
        _crashLogs.value = emptyList()
        _deviceLogCounts.value = emptyMap()
    }
}

fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h${minutes % 60}m"
        minutes > 0 -> "${minutes}m${seconds % 60}s"
        else -> "${seconds}s"
    }
}