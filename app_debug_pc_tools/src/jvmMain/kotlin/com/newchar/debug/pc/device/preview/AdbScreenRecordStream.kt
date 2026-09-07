package com.newchar.debug.pc.device.preview

import androidx.compose.ui.graphics.ImageBitmap
import com.newchar.debug.pc.executor.AdbCommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

/** 使用 adb exec-out screenrecord 持续拉取 H264；设备的 180 秒上限到期后自动重启。 */
internal class AdbScreenRecordStream(
    private val executor: AdbCommandExecutor,
    private val deviceId: String,
) {
    private val closed = AtomicBoolean(false)
    private val currentProcess = AtomicReference<Process?>(null)
    private val decoder = H264FrameDecoder()

    /** 读取并解码 H264 帧，直到调用 close 或协程取消。 */
    suspend fun collectFrames(onFrame: (ImageBitmap) -> Unit) = withContext(Dispatchers.IO) {
        closed.set(false)
        while (!closed.get() && coroutineContext.isActive) {
            collectRound(onFrame)
            if (!closed.get() && coroutineContext.isActive) delay(RESTART_DELAY_MS)
        }
    }

    /** 销毁 screenrecord 进程和解码器，确保切换帧源后不泄漏 adb 子进程。 */
    fun close() {
        closed.set(true)
        decoder.close()
        currentProcess.getAndSet(null)?.let { process ->
            stopProcess(process)
        }
    }

    /** 读取一轮自然结束的 H264 流，保留 EOF 前的最后一帧。 */
    private fun collectRound(onFrame: (ImageBitmap) -> Unit) {
        val process = ProcessBuilder(h264Command()).start()
        currentProcess.set(process)
        try {
            decoder.decode(process.inputStream, onFrame)
        } finally {
            currentProcess.compareAndSet(process, null)
            awaitNaturalExit(process)
        }
    }

    /** 生成标准 Annex-B H264 screenrecord 命令。 */
    private fun h264Command(): List<String> = executor.resolveAdbCommand(
        "-s", deviceId, "exec-out", "screenrecord", "--time-limit", "180", "--output-format=h264", "-",
    )

    /** EOF 后等待进程自行回收；只有关闭时才主动终止残留进程。 */
    private fun awaitNaturalExit(process: Process) {
        runCatching { process.waitFor(1L, TimeUnit.SECONDS) }
        if (closed.get() && process.isAlive) stopProcess(process)
    }

    /** 强制中断切换帧源或关闭窗口时的 adb 子进程。 */
    private fun stopProcess(process: Process) {
        process.destroy()
        runCatching { process.waitFor(1L, TimeUnit.SECONDS) }
        if (process.isAlive) process.destroyForcibly()
    }

    private companion object {
        const val RESTART_DELAY_MS = 50L
    }
}
