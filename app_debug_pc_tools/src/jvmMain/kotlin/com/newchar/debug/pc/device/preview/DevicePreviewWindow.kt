package com.newchar.debug.pc.device.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.newchar.debug.pc.config.DesktopAppSettingsStore
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.executor.AdbCommandExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val FRAME_INTERVAL_MS = 150L
private const val THROTTLE_INTERVAL_MS_MOVE = 15_000L
private const val DISPLAY_POLL_INTERVAL_MS = 500L
private const val REVERSE_CONTACT_TIMEOUT_MS = 60_000L

private enum class PreviewFrameSource(val label: String) {
    PNG("PNG 低频"),
    RAW_YUV("YUV 原始帧"),
    ADB_H264("H264 流(adb)"),
    APP_H264("H264 流(App)"),
}

/**
 * 预览显示的屏幕参数；宽高固定为设备自然方向的物理分辨率。
 */
data class DeviceDisplayInfo(
    val naturalWidth: Int,
    val naturalHeight: Int,
    val rotation: Int,
) {
    val previewSize: IntSize
        get() = if (rotation == 90 || rotation == 270) {
            IntSize(naturalHeight, naturalWidth)
        } else {
            IntSize(naturalWidth, naturalHeight)
        }
}

/** PC 鼠标操作映射后的 adb input 命令。 */
sealed interface DeviceInputCommand {
    data class Tap(val x: Int, val y: Int) : DeviceInputCommand
    data class Swipe(val fromX: Int, val fromY: Int, val toX: Int, val toY: Int, val durationMs: Long) : DeviceInputCommand
}

/**
 * 预览区域坐标到设备自然坐标的映射器。
 *
 * 屏幕截图按当前旋转方向展示，adb input 使用自然方向坐标，因此这里完成旋转矩阵的逆变换。
 */
class PreviewCoordinateMapper(
    private val display: DeviceDisplayInfo,
    private val viewportSize: IntSize,
) {
    /** 将预览窗口像素坐标映射为设备坐标，区域外返回 null。 */
    fun map(position: androidx.compose.ui.geometry.Offset): IntOffset? {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return null
        val preview = display.previewSize
        val scale = min(
            viewportSize.width.toFloat() / preview.width,
            viewportSize.height.toFloat() / preview.height,
        )
        if (scale <= 0f) return null
        val renderedWidth = preview.width * scale
        val renderedHeight = preview.height * scale
        val left = (viewportSize.width - renderedWidth) / 2f
        val top = (viewportSize.height - renderedHeight) / 2f
        if (position.x !in left..(left + renderedWidth) || position.y !in top..(top + renderedHeight)) {
            return null
        }
        val logicalX = ((position.x - left) / scale).toInt().coerceIn(0, preview.width - 1)
        val logicalY = ((position.y - top) / scale).toInt().coerceIn(0, preview.height - 1)
        return when (display.rotation) {
            90 -> IntOffset(logicalY, display.naturalHeight - 1 - logicalX)
            180 -> IntOffset(display.naturalWidth - 1 - logicalX, display.naturalHeight - 1 - logicalY)
            270 -> IntOffset(display.naturalWidth - 1 - logicalY, logicalX)
            else -> IntOffset(logicalX, logicalY)
        }.let { point ->
            IntOffset(
                point.x.coerceIn(0, display.naturalWidth - 1),
                point.y.coerceIn(0, display.naturalHeight - 1),
            )
        }
    }
}

/**
 * 触摸采样器：移动距离至少 5px 且相邻采样至少 40ms 才输出 swipe，抬起时始终补齐终点。
 */
class TouchFilter(
    private val minIntervalMs: Long = 40L,
    private val minDistancePx: Int = 5,
) {
    private var downPoint: IntOffset? = null
    private var lastPoint: IntOffset? = null
    private var lastSampleAtMs: Long = 0L

    /** 记录按下位置。 */
    fun onDown(point: IntOffset, timeMs: Long) {
        downPoint = point
        lastPoint = point
        lastSampleAtMs = timeMs
    }

    /** 返回满足采样阈值的移动命令。 */
    fun onMove(point: IntOffset, timeMs: Long): DeviceInputCommand.Swipe? {
        val previous = lastPoint ?: return null
        if (timeMs - lastSampleAtMs < minIntervalMs || distance(previous, point) < minDistancePx) return null
        lastPoint = point
        val duration = max(minIntervalMs, timeMs - lastSampleAtMs)
        lastSampleAtMs = timeMs
        return DeviceInputCommand.Swipe(previous.x, previous.y, point.x, point.y, duration)
    }

    /** 返回点击或最终的 swipe，并清理本次手势状态。 */
    fun onUp(point: IntOffset, timeMs: Long): DeviceInputCommand? {
        val start = downPoint ?: return null
        val previous = lastPoint ?: start
        val totalDistance = distance(start, point)
        val command = if (totalDistance < minDistancePx) {
            DeviceInputCommand.Tap(point.x, point.y)
        } else {
            DeviceInputCommand.Swipe(
                previous.x,
                previous.y,
                point.x,
                point.y,
                max(minIntervalMs, timeMs - lastSampleAtMs),
            )
        }
        reset()
        return command
    }

    /** 清理未完成的手势，避免窗口关闭后残留拖拽状态。 */
    fun reset() {
        downPoint = null
        lastPoint = null
        lastSampleAtMs = 0L
    }

    private fun distance(first: IntOffset, second: IntOffset): Int =
        max(abs(first.x - second.x), abs(first.y - second.y))
}

/**
 * 单设备 adb input 串行执行器。
 *
 * 队列仅保留最新命令，防止高频鼠标移动导致 adb 进程堆积；抬手补点会成为最后一条命令。
 */
private class AdbInputInjector(
    private val executor: AdbCommandExecutor,
    private val deviceId: String,
    private val onError: (String) -> Unit,
) {
    private val commands = Channel<DeviceInputCommand>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val worker = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        for (command in commands) {
            val result = when (command) {
                is DeviceInputCommand.Tap -> executor.shell(deviceId, "input", "tap", command.x.toString(), command.y.toString())
                is DeviceInputCommand.Swipe -> executor.shell(
                    deviceId,
                    "input",
                    "swipe",
                    command.fromX.toString(),
                    command.fromY.toString(),
                    command.toX.toString(),
                    command.toY.toString(),
                    command.durationMs.coerceAtLeast(1L).toString(),
                )
            }
            if (!result.isSuccess) onError(result.error.ifBlank { result.output.ifBlank { "adb input 执行失败" } })
        }
    }

    /** 尝试提交输入命令，不阻塞 Compose 事件线程。 */
    fun submit(command: DeviceInputCommand) {
        commands.trySend(command)
    }

    /** 停止消费并取消正在执行的 adb input。 */
    fun close() {
        commands.close()
        worker.cancel()
    }
}

/**
 * 基于 PNG 截图的预览帧源。
 *
 * 选择 adb exec-out screencap -p 而非 H264：无需新增解码依赖，兼容性和关闭回收均可控；
 * 代价是约 6fps 的低频预览，更适合调试而非远程操控。
 */
private class AdbScreenCapture(private val executor: AdbCommandExecutor, private val deviceId: String) {
    private val currentProcess = AtomicReference<Process?>(null)

    /** 获取一帧 PNG；拖拽期间缩小到一半分辨率以降低渲染开销。 */
    suspend fun captureFrame(reducedQuality: Boolean): ImageBitmap? = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(
            executor.resolveAdbCommand("-s", deviceId, "exec-out", "screencap", "-p"),
        ).start()
        currentProcess.set(process)
        try {
            val bytes = process.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    input.copyTo(output)
                    output.toByteArray()
                }
            }
            if (process.waitFor() != 0 || bytes.isEmpty()) null else decodePng(bytes, reducedQuality)
        } finally {
            currentProcess.compareAndSet(process, null)
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
    }

    /** 中断当前正在读取的 adb 截图进程。 */
    fun close() {
        currentProcess.getAndSet(null)?.let { process ->
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
    }

    /** 在 PC 端按比例缩小 PNG，screencap 本身不提供质量参数。 */
    private fun decodePng(bytes: ByteArray, reducedQuality: Boolean): ImageBitmap? {
        if (!reducedQuality) return Image.makeFromEncoded(bytes).toComposeImageBitmap()
        val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        val width = (source.width / 2).coerceAtLeast(1)
        val height = (source.height / 2).coerceAtLeast(1)
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(source, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return scaled.toComposeImageBitmap()
    }
}

/** 独立的设备实时预览窗口与鼠标输入透传。 */
@Composable
fun DevicePreviewWindow(
    device: DeviceInfo,
    executor: AdbCommandExecutor,
    probeCache: DeviceProbeCache = DeviceProbeCache(),
    lastContactAt: () -> Long = { 0L },
    onAlwaysOnTopChanged: (Boolean) -> Unit = {},
    onCloseRequest: () -> Unit,
) {
    val initialDisplay = DeviceDisplayInfo(1080, 1920, 0)
    var displayInfo by remember(device.id) { mutableStateOf(initialDisplay) }
    var frame by remember(device.id) { mutableStateOf<ImageBitmap?>(null) }
    var errorText by remember(device.id) { mutableStateOf<String?>(null) }
    var viewportSize by remember(device.id) { mutableStateOf(IntSize.Zero) }
    var frameSource by remember(device.id) { mutableStateOf<PreviewFrameSource?>(null) }
    var isInteracting by remember(device.id) { mutableStateOf(false) }
    var pngRefreshVersion by remember(device.id) { mutableStateOf(0) }
    var now by remember(device.id) { mutableStateOf(System.currentTimeMillis()) }
    var rawMetrics by remember(device.id) { mutableStateOf<RawStreamMetrics?>(null) }
    val capture = remember(device.id, executor) { AdbScreenCapture(executor, device.id) }
    val rawFrameStream = remember(device.id, executor) { AdbRawFrameStream(executor, device.id) }
    val adbH264Stream = remember(device.id, executor) { AdbScreenRecordStream(executor, device.id) }
    val appStreamServer = remember(device.id) { AppStreamServer() }
    val injector = remember(device.id, executor) {
        AdbInputInjector(executor, device.id) { error -> errorText = error }
    }
    val touchFilter = remember(device.id) { TouchFilter() }
    val settingsStore = remember { DesktopAppSettingsStore() }
    val scope = rememberCoroutineScope()
    val savedSettings = remember { settingsStore.loadSync() }
    var alwaysOnTop by remember(device.id) { mutableStateOf(savedSettings.previewAlwaysOnTop) }
    val windowState = rememberWindowState(width = 420.dp, height = 760.dp)

    DisposableEffect(capture, rawFrameStream, adbH264Stream, appStreamServer, injector, touchFilter) {
        onDispose {
            touchFilter.reset()
            injector.close()
            capture.close()
            rawFrameStream.close()
            adbH264Stream.close()
            appStreamServer.close()
        }
    }
    LaunchedEffect(alwaysOnTop) {
        settingsStore.save(savedSettings.copy(previewAlwaysOnTop = alwaysOnTop))
        onAlwaysOnTopChanged(alwaysOnTop)
    }
    LaunchedEffect(device.id) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    LaunchedEffect(device.id, executor) {
        while (isActive) {
            runCatching { queryDisplayInfo(executor, device.id, displayInfo) }
                .onSuccess { displayInfo = it }
                .onFailure { errorText = "无法读取设备屏幕参数: ${it.message}" }
            delay(DISPLAY_POLL_INTERVAL_MS)
        }
    }
    LaunchedEffect(device.id, executor, probeCache) {
        val result = probeCache.probeIfNeeded(executor, device.id)
        frameSource = if (result.rawFramesSupported) PreviewFrameSource.RAW_YUV else PreviewFrameSource.ADB_H264
    }
    LaunchedEffect(frameSource, isInteracting, pngRefreshVersion, capture) {
        if (frameSource != PreviewFrameSource.PNG) return@LaunchedEffect
        try {
            while (isActive) {
                val nextFrame = capture.captureFrame(reducedQuality = isInteracting)
                if (nextFrame != null) {
                    frame = nextFrame
                    errorText = null
                } else {
                    errorText = "未获取到截图，请确认设备在线且 adb 可用"
                }
                delay(if (isInteracting) THROTTLE_INTERVAL_MS_MOVE else FRAME_INTERVAL_MS)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            errorText = "截图失败: ${throwable.message ?: "未知错误"}"
        } finally {
            capture.close()
        }
    }
    LaunchedEffect(frameSource, rawFrameStream, displayInfo.naturalWidth, displayInfo.naturalHeight) {
        if (frameSource != PreviewFrameSource.RAW_YUV) return@LaunchedEffect
        try {
            rawFrameStream.collectFrames(
                width = displayInfo.naturalWidth,
                height = displayInfo.naturalHeight,
                onFrame = { decodedFrame ->
                    frame = decodedFrame
                    errorText = null
                },
                onMetricsChanged = { metrics -> rawMetrics = metrics },
                onDegradeRequested = {
                    errorText = "YUV 帧率持续偏低，已切换到 H264 流"
                    frameSource = PreviewFrameSource.ADB_H264
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            errorText = "YUV 原始帧失败，已降级 PNG: ${throwable.message ?: "未知错误"}"
            frameSource = PreviewFrameSource.PNG
        } finally {
            rawFrameStream.close()
        }
    }
    LaunchedEffect(frameSource, adbH264Stream) {
        if (frameSource != PreviewFrameSource.ADB_H264) return@LaunchedEffect
        try {
            adbH264Stream.collectFrames { decodedFrame ->
                frame = decodedFrame
                errorText = null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            errorText = "adb H264 流失败，已降级 PNG: ${throwable.message ?: "请确认设备在线"}"
            frameSource = PreviewFrameSource.PNG
        } finally {
            adbH264Stream.close()
        }
    }
    LaunchedEffect(frameSource, appStreamServer) {
        if (frameSource != PreviewFrameSource.APP_H264) return@LaunchedEffect
        val reverse = executor.adb("-s", device.id, "reverse", "tcp:$APP_STREAM_PORT", "tcp:$APP_STREAM_PORT")
        if (!reverse.isSuccess) {
            errorText = "无法建立 App 推流端口: ${reverse.error.ifBlank { reverse.output }}"
            return@LaunchedEffect
        }
        try {
            appStreamServer.collectFrames { decodedFrame ->
                frame = decodedFrame
                errorText = null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            errorText = "App H264 流未连接，请在设备端开启实时推流"
        } finally {
            appStreamServer.close()
        }
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "预览 - ${device.model.ifBlank { device.id }}",
        state = windowState,
        alwaysOnTop = alwaysOnTop,
    ) {
        val mapper = remember(displayInfo, viewportSize) { PreviewCoordinateMapper(displayInfo, viewportSize) }
        val contactAt = lastContactAt()
        val isConnected = contactAt > 0L && now - contactAt <= REVERSE_CONTACT_TIMEOUT_MS
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF17191D))) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    BasicText(
                        "${displayInfo.previewSize.width}×${displayInfo.previewSize.height} · ${displayInfo.rotation}°",
                        style = TextStyle(color = Color(0xFFE8EAED), fontSize = 12.sp),
                    )
                    BasicText(
                        if (alwaysOnTop) "📌 置顶" else "取消置顶",
                        modifier = Modifier.clickable { alwaysOnTop = !alwaysOnTop },
                        style = TextStyle(color = Color(0xFF9AB8FF), fontSize = 12.sp),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PreviewFrameSource.values().forEach { source ->
                        BasicText(
                            source.label,
                            modifier = Modifier.clickable {
                                if (frameSource != source) {
                                    frameSource = source
                                    frame = null
                                    rawMetrics = null
                                }
                            },
                            style = TextStyle(
                                color = if (frameSource == source) Color(0xFF9AB8FF) else Color(0xFFB8C0CC),
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }
                BasicText(
                    previewStatusText(frameSource, isInteracting, rawMetrics),
                    modifier = Modifier.padding(top = 6.dp),
                    style = TextStyle(color = Color(0xFFB8C0CC), fontSize = 12.sp),
                )
                BasicText(
                    "重新检测原始帧能力",
                    modifier = Modifier.padding(top = 4.dp).clickable {
                        scope.launch {
                            frame = null
                            rawMetrics = null
                            errorText = "正在重新检测 YUV 原始帧能力…"
                            val result = probeCache.refresh(executor, device.id)
                            frameSource = if (result.rawFramesSupported) PreviewFrameSource.RAW_YUV else PreviewFrameSource.ADB_H264
                        }
                    },
                    style = TextStyle(color = Color(0xFF9AB8FF), fontSize = 12.sp),
                )
                BasicText(
                    communicationStatusText(isConnected, contactAt),
                    modifier = Modifier.padding(top = 4.dp),
                    style = TextStyle(color = if (isConnected) Color(0xFF71D88A) else Color(0xFFB8C0CC), fontSize = 12.sp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(mapper) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    val point = mapper.map(change.position)
                                    if (point == null) {
                                        if (change.changedToUp()) {
                                            touchFilter.reset()
                                            isInteracting = false
                                            pngRefreshVersion++
                                        }
                                        return@forEach
                                    }
                                    val timestamp = System.currentTimeMillis()
                                    when {
                                        change.changedToDown() -> {
                                            isInteracting = true
                                            touchFilter.onDown(point, timestamp)
                                        }
                                        change.changedToUp() -> {
                                            touchFilter.onUp(point, timestamp)?.let(injector::submit)
                                            isInteracting = false
                                            pngRefreshVersion++
                                        }
                                        change.pressed -> touchFilter.onMove(point, timestamp)?.let(injector::submit)
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                frame?.let { image ->
                    Image(
                        bitmap = image,
                        contentDescription = "设备实时画面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } ?: BasicText(
                    "正在连接设备并获取画面…",
                    style = TextStyle(color = Color(0xFFE8EAED)),
                )
                errorText?.let { error ->
                    BasicText(
                        error,
                        style = TextStyle(color = Color(0xFFFFB4AB), fontSize = 12.sp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    )
                }
            }
        }
    }
}

/** 生成当前帧源与 PNG 节流状态文案。 */
private fun previewStatusText(
    source: PreviewFrameSource?,
    isInteracting: Boolean,
    rawMetrics: RawStreamMetrics?,
): String = when (source) {
    null -> "正在检测设备原始帧能力…"
    PreviewFrameSource.PNG -> if (isInteracting) "PNG 节流中（15s/帧·降质）" else "PNG 低频预览（150ms/帧）"
    PreviewFrameSource.RAW_YUV -> rawMetrics?.let { metrics ->
        "YUV 原始帧（${metrics.arrivalFps}fps · ${formatBandwidth(metrics.bytesPerSecond)} · 渲染 ${metrics.targetRenderFps}fps）"
    } ?: "YUV 原始帧（正在获取首帧）"
    PreviewFrameSource.ADB_H264 -> "H264 流（adb screenrecord · JavaCV 解码）"
    PreviewFrameSource.APP_H264 -> "H264 流（设备 App 推流 · 端口 6667）"
}

/** 格式化原始帧流每秒到达的字节数。 */
private fun formatBandwidth(bytesPerSecond: Long): String = "%.1f MB/s".format(java.util.Locale.getDefault(), bytesPerSecond / 1_000_000.0)

/** 生成 6666 reverse 通道状态与断连排障提示。 */
private fun communicationStatusText(connected: Boolean, contactAt: Long): String = if (connected) {
    "● App↔PC 已联通 · 最近通信 ${formatContactTime(contactAt)}"
} else {
    "● App↔PC 未联通 · 请确认 USB 调试、adb reverse tcp:6666 与 PCToolsPlugin"
}

/** 格式化最近通信的本地时间。 */
private fun formatContactTime(timestamp: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

/** 读取物理分辨率与当前方向；失败时保留上一组有效值。 */
private suspend fun queryDisplayInfo(
    executor: AdbCommandExecutor,
    deviceId: String,
    previous: DeviceDisplayInfo,
): DeviceDisplayInfo {
    val sizeResult = executor.shell(deviceId, "wm", "size")
    if (!sizeResult.isSuccess) error(sizeResult.error.ifBlank { sizeResult.output })
    val match = Regex("(?:Physical size:|Override size:)\\s*(\\d+)x(\\d+)").find(sizeResult.output)
        ?: error("wm size 返回格式无法识别: ${sizeResult.output}")
    val rotationResult = executor.shell(deviceId, "settings", "get", "system", "user_rotation")
    val rawRotation = rotationResult.output.trim().toIntOrNull()
    val rotation = when (rawRotation) {
        0 -> 0
        1 -> 90
        2 -> 180
        3 -> 270
        else -> previous.rotation
    }
    return DeviceDisplayInfo(match.groupValues[1].toInt(), match.groupValues[2].toInt(), rotation)
}
