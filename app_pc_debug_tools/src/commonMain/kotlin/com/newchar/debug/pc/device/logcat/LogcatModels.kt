@file:OptIn(ExperimentalStdlibApi::class)

package com.newchar.debug.pc.device.logcat

data class LogcatEntry(
    val timestamp: String = "",
    val pid: Int = 0,
    val tid: Int = 0,
    val level: LogLevel = LogLevel.VERBOSE,
    val tag: String = "",
    val message: String = "",
    val rawLine: String = "",
)

enum class LogLevel(val priority: Int, val label: String, val shortLabel: String) {
    VERBOSE(2, "Verbose", "V"),
    DEBUG(3, "Debug", "D"),
    INFO(4, "Info", "I"),
    WARN(5, "Warning", "W"),
    ERROR(6, "Error", "E"),
    FATAL(7, "Fatal", "F"),
    SILENT(8, "Silent", "S");

    companion object {
        fun fromString(label: String): LogLevel =
            entries.find { it.label.equals(label, ignoreCase = true) || it.shortLabel == label }
                ?: VERBOSE

        fun fromPriority(priority: Int): LogLevel =
            entries.find { it.priority == priority } ?: VERBOSE
    }
}

enum class CrashType(val filterTag: String) {
    ANDROID_RUNTIME("AndroidRuntime"),
    NATIVE_CRASH("DEBUG"),
    SIGNAL_HANDLER("signal");

    companion object {
        fun isCrashLine(line: String): CrashType? {
            val upperLine = line.uppercase()
            return when {
                upperLine.contains("ANDROIDRUNTIME") || upperLine.contains("FATAL EXCEPTION") -> ANDROID_RUNTIME
                upperLine.contains("SIGNAL") && (upperLine.contains("SIGSEGV") || upperLine.contains("SIGABRT")) -> SIGNAL_HANDLER
                upperLine.contains("DEBUG") && (upperLine.contains("TOMBSTONE") || upperLine.contains("BACKTRACE")) -> NATIVE_CRASH
                else -> null
            }
        }

        fun isNativeCrashSignal(line: String): Boolean {
            val upperLine = line.uppercase()
            return upperLine.contains("SIGNAL") &&
                    (upperLine.contains("SIGSEGV") || upperLine.contains("SIGABRT") ||
                     upperLine.contains("SIGBUS") || upperLine.contains("SIGFPE"))
        }
    }
}

data class LogcatFilter(
    val packageName: String = "",
    val selectedTags: Set<String> = emptySet(),
    val includeCrashLogs: Boolean = true,
    val minLogLevel: LogLevel = LogLevel.VERBOSE,
    val deviceIds: Set<String> = emptySet(),
) {
    fun matches(entry: LogcatEntry): Boolean {
        if (deviceIds.isNotEmpty() && entry.pid == 0) {
            return false
        }

        if (selectedTags.isNotEmpty() && entry.tag !in selectedTags) {
            return false
        }

        if (entry.level.priority < minLogLevel.priority) {
            return false
        }

        if (!includeCrashLogs) {
            val crashType = CrashType.isCrashLine(entry.rawLine)
            if (crashType != null) {
                return false
            }
        }

        return true
    }
}