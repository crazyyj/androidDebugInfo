package com.newchar.debug.pc.device.preview

import com.newchar.debug.pc.config.DesktopAppSettingsStore
import com.newchar.debug.pc.executor.AdbCommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private const val PROBE_CACHE_VALID_MS = 24 * 60 * 60 * 1_000L

/** 设备原始帧能力的本地缓存记录。 */
internal data class RawFramesProbeResult(
    val rawFramesSupported: Boolean,
    val probeTimestamp: Long,
    val adbVersion: String,
)

/**
 * 按设备序列号保存 screenrecord 原始帧能力，避免每次打开预览都重复探测。
 */
class DeviceProbeCache(
    private val cacheFile: File = File(DesktopAppSettingsStore.configDirectoryPath(), "device_preview_cache.json"),
) {
    private val records = linkedMapOf<String, RawFramesProbeResult>()

    init {
        loadFromDisk()
    }

    /** 返回仍处于 24 小时有效期内的探测结果。 */
    @Synchronized
    internal fun getValid(deviceId: String, now: Long = System.currentTimeMillis()): RawFramesProbeResult? {
        return records[deviceId]?.takeIf { now - it.probeTimestamp in 0..PROBE_CACHE_VALID_MS }
    }

    /** 使用缓存或执行一次短探测，并将结果持久化到本地 JSON。 */
    internal suspend fun probeIfNeeded(executor: AdbCommandExecutor, deviceId: String): RawFramesProbeResult {
        getValid(deviceId)?.let { return it }
        return refresh(executor, deviceId)
    }

    /** 忽略旧缓存重新探测，供用户手动点击“重新检测”时使用。 */
    internal suspend fun refresh(executor: AdbCommandExecutor, deviceId: String): RawFramesProbeResult {
        val supported = runtimeProbeRawFrames(executor, deviceId)
        val adbVersion = executor.adb("version").output.lineSequence().firstOrNull().orEmpty()
        val result = RawFramesProbeResult(supported, System.currentTimeMillis(), adbVersion)
        synchronized(this) {
            records[deviceId] = result
            saveToDisk()
        }
        return result
    }

    /** 从损坏或不存在的文件中恢复为空缓存，不影响设备扫描。 */
    @Synchronized
    private fun loadFromDisk() {
        val content = runCatching { cacheFile.readText() }.getOrNull().orEmpty()
        entryPattern.findAll(content).forEach { match ->
            val timestamp = match.groupValues[3].toLongOrNull() ?: return@forEach
            records[unescape(match.groupValues[1])] = RawFramesProbeResult(
                rawFramesSupported = match.groupValues[2].toBoolean(),
                probeTimestamp = timestamp,
                adbVersion = unescape(match.groupValues[4]),
            )
        }
    }

    /** 将内存中的探测结果写入应用配置目录。 */
    private fun saveToDisk() {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(buildJson())
        }
    }

    /** 构建稳定、可人工检查的 JSON 缓存内容。 */
    private fun buildJson(): String = buildString {
        append("{\n  \"formatVersion\": 1,\n  \"devices\": {")
        records.entries.forEachIndexed { index, (deviceId, result) ->
            if (index > 0) append(',')
            append("\n    \"").append(escape(deviceId)).append("\": {\"rawFramesSupported\": ")
            append(result.rawFramesSupported).append(", \"probeTimestamp\": ")
            append(result.probeTimestamp).append(", \"adbVersion\": \"")
            append(escape(result.adbVersion)).append("\"}")
        }
        append("\n  }\n}\n")
    }

    /** 转义 JSON 字符串中的反斜杠与双引号。 */
    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    /** 还原本缓存文件写入的最小 JSON 转义集合。 */
    private fun unescape(value: String): String = value.replace("\\\"", "\"").replace("\\\\", "\\")

    private companion object {
        val entryPattern = Regex(
            "\\\"((?:\\\\.|[^\\\"])*)\\\"\\s*:\\s*\\{\\s*\\\"rawFramesSupported\\\"\\s*:\\s*(true|false)\\s*,\\s*\\\"probeTimestamp\\\"\\s*:\\s*(\\d+)\\s*,\\s*\\\"adbVersion\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"\\s*}",
        )
    }
}

/**
 * 在 500ms 内验证设备是否实际接受 --raw-frames，而不依赖 Android 版本号。
 */
internal suspend fun runtimeProbeRawFrames(executor: AdbCommandExecutor, deviceId: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val process = ProcessBuilder(
            executor.resolveAdbCommand(
                "-s", deviceId, "exec-out", "screenrecord", "--raw-frames", "--time-limit", "180", "-",
            ),
        ).start()
        try {
            delay(500L)
            process.isAlive && process.inputStream.available() > 0
        } finally {
            process.destroy()
            process.waitFor(1L, TimeUnit.SECONDS)
            if (process.isAlive) process.destroyForcibly()
        }
    }.getOrDefault(false)
}
