package com.newchar.debug.pc.device.preview

import androidx.compose.ui.graphics.ImageBitmap
import com.newchar.debug.pc.executor.AdbCommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

private const val SCREENRECORD_TIME_LIMIT_SECONDS = 180
private const val RAW_RESTART_DELAY_MS = 50L
private const val MAX_RAW_DIMENSION = 8_192

/** 使用 adb screenrecord --raw-frames 拉取 NV12 原始帧，并在低带宽时请求降级。 */
internal class AdbRawFrameStream(
    private val executor: AdbCommandExecutor,
    private val deviceId: String,
) {
    private val closed = AtomicBoolean(false)
    private val currentProcess = AtomicReference<Process?>(null)

    /** 持续读取原始帧，screenrecord 自然结束后在 50ms 内启动下一轮。 */
    suspend fun collectFrames(
        width: Int,
        height: Int,
        onFrame: (ImageBitmap) -> Unit,
        onMetricsChanged: (RawStreamMetrics) -> Unit,
        onDegradeRequested: () -> Unit,
    ) = withContext(Dispatchers.IO) {
        closed.set(false)
        val monitor = BandwidthMonitor(onMetricsChanged, onDegradeRequested)
        while (!closed.get() && coroutineContext.isActive) {
            collectRound(width, height, monitor, onFrame)
            if (!closed.get() && coroutineContext.isActive) delay(RAW_RESTART_DELAY_MS)
        }
    }

    /** 关闭 adb 子进程，解除原始帧读取的阻塞状态。 */
    fun close() {
        closed.set(true)
        currentProcess.getAndSet(null)?.let(::stopProcess)
    }

    /** 运行一轮自然时长的 screenrecord 原始帧读取。 */
    private fun collectRound(width: Int, height: Int, monitor: BandwidthMonitor, onFrame: (ImageBitmap) -> Unit) {
        val process = ProcessBuilder(rawFrameCommand()).start()
        currentProcess.set(process)
        try {
            RawFrameParser(process.inputStream, width, height).collect { rawFrame ->
                if (monitor.onFrameReceived(rawFrame.data.size)) {
                    onFrame(YuvFrameConverter.toImage(rawFrame.data, rawFrame.width, rawFrame.height))
                }
            }
        } finally {
            currentProcess.compareAndSet(process, null)
            awaitNaturalExit(process)
        }
    }

    /** 生成固定的原始帧 screenrecord 命令。 */
    private fun rawFrameCommand(): List<String> = executor.resolveAdbCommand(
        "-s", deviceId, "exec-out", "screenrecord", "--raw-frames",
        "--time-limit", SCREENRECORD_TIME_LIMIT_SECONDS.toString(), "-",
    )

    /** EOF 后仅等待自然退出；关闭流程中才会主动销毁仍存活的进程。 */
    private fun awaitNaturalExit(process: Process) {
        runCatching { process.waitFor(1L, TimeUnit.SECONDS) }
        if (closed.get() && process.isAlive) stopProcess(process)
    }

    /** 销毁被窗口关闭或帧源切换中断的 adb 子进程。 */
    private fun stopProcess(process: Process) {
        process.destroy()
        runCatching { process.waitFor(1L, TimeUnit.SECONDS) }
        if (process.isAlive) process.destroyForcibly()
    }
}

/** 一帧可包含其厂商帧头提供的动态分辨率。 */
private data class RawFrame(val data: ByteArray, val width: Int, val height: Int)

/**
 * 兼容无帧头、文本帧头和 12 字节二进制帧头的 NV12 流读取器。
 *
 * 二进制帧头仅在其宽高与已查询屏幕参数匹配时启用，避免把正常 Y 分量误识别为帧头。
 */
private class RawFrameParser(input: InputStream, private val defaultWidth: Int, private val defaultHeight: Int) {
    private val stream = BufferedInputStream(input)
    private val mode = detectMode()

    /** 按已检测的格式逐帧读取，直到 adb 管道 EOF。 */
    fun collect(onFrame: (RawFrame) -> Unit) {
        while (true) {
            val frame = when (mode) {
                RawFrameMode.FIXED -> readFixedFrame(defaultWidth, defaultHeight)
                RawFrameMode.TEXT_HEADER -> readTextFrame()
                RawFrameMode.BINARY_HEADER -> readBinaryFrame()
            } ?: return
            onFrame(frame)
        }
    }

    /** 从首字节识别厂商是否在每帧前附加文本或二进制宽高信息。 */
    private fun detectMode(): RawFrameMode {
        val peek = ByteArray(12)
        stream.mark(peek.size + 1)
        val count = stream.read(peek)
        stream.reset()
        if (count < peek.size) return RawFrameMode.FIXED
        if (peek.takeWhile { it != '\n'.code.toByte() }.all(::isHeaderCharacter)) return RawFrameMode.TEXT_HEADER
        return if (matchesBinaryDimensions(peek)) RawFrameMode.BINARY_HEADER else RawFrameMode.FIXED
    }

    /** 连续 NV12 模式按已知屏幕尺寸切分帧。 */
    private fun readFixedFrame(width: Int, height: Int): RawFrame? =
        readFrameData(width, height)?.let { RawFrame(it, width, height) }

    /** 读取“width height timestamp”文本帧头及其后的 NV12 帧。 */
    private fun readTextFrame(): RawFrame? {
        val values = readHeaderLine()?.trim()?.split(Regex("\\s+")) ?: return null
        val width = values.getOrNull(0)?.toIntOrNull() ?: return null
        val height = values.getOrNull(1)?.toIntOrNull() ?: return null
        return readFrameData(width, height)?.let { RawFrame(it, width, height) }
    }

    /** 读取 12 字节二进制帧头（宽、高、时间戳）及其后的 NV12 帧。 */
    private fun readBinaryFrame(): RawFrame? {
        val header = readExact(12) ?: return null
        val width = readInt(header, 0)
        val height = readInt(header, 4)
        return readFrameData(width, height)?.let { RawFrame(it, width, height) }
    }

    /** 校验帧尺寸并完整读取对应数量的 NV12 字节。 */
    private fun readFrameData(width: Int, height: Int): ByteArray? {
        if (width !in 1..MAX_RAW_DIMENSION || height !in 1..MAX_RAW_DIMENSION) return null
        return readExact(YuvFrameConverter.frameSize(width, height))
    }

    /** 读取以换行结束的短文本帧头。 */
    private fun readHeaderLine(): String? {
        val bytes = ArrayList<Byte>(64)
        while (bytes.size < 128) {
            val value = stream.read()
            if (value < 0) return null
            if (value == '\n'.code) return bytes.toByteArray().decodeToString()
            bytes += value.toByte()
        }
        return null
    }

    /** 从流中读取指定长度；遇到 EOF 时丢弃不完整尾帧。 */
    private fun readExact(size: Int): ByteArray? {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = stream.read(bytes, offset, size - offset)
            if (count <= 0) return null
            offset += count
        }
        return bytes
    }

    /** 判断文本帧头允许的 ASCII 字符集合。 */
    private fun isHeaderCharacter(value: Byte): Boolean = value in '0'.code.toByte()..'9'.code.toByte() ||
        value == ' '.code.toByte() || value == '\t'.code.toByte()

    /** 检查二进制帧头的宽高是否等于已查询的物理屏幕尺寸。 */
    private fun matchesBinaryDimensions(header: ByteArray): Boolean =
        readInt(header, 0) == defaultWidth && readInt(header, 4) == defaultHeight

    /** 按小端解析 Android 常见的二进制整数字段。 */
    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    /** 原始帧流支持的三种封包方式。 */
    private enum class RawFrameMode { FIXED, TEXT_HEADER, BINARY_HEADER }
}
