package com.newchar.debug.pc.device.preview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

/**
 * 通过 JavaCV/FFmpeg 解码 Annex-B H264 裸流。
 *
 * JavaCV 提供桌面端跨平台 native 解码；后续可替换为 scrcpy 协议以进一步降低延迟。
 */
internal class H264FrameDecoder {
    private val activeGrabber = AtomicReference<FFmpegFrameGrabber?>(null)

    /**
     * 阻塞读取输入流并逐帧回调 Compose 位图，流结束后正常返回。
     *
     * 当 [close] 主动释放当前 grabber 时（窗口关闭、重试重建等），解码线程会自然退出，
     * 此时不视为异常、也不上报错误；只有非主动关闭导致的真实解码异常才会通过 [onError] 触发。
     */
    fun decode(input: InputStream, onFrame: (ImageBitmap) -> Unit, onError: (String) -> Unit = {}) {
        val converter = Java2DFrameConverter()
        val grabber = FFmpegFrameGrabber(input, 0).apply {
            format = "h264"
            // 实时裸流：给足探测量，确保 SPS/PPS 与首帧都被纳入探测，避免「头部能解析但解不出帧」。
            setOption("probesize", "65536")
            setOption("analyzeduration", "2000000")
            // 注意：实时管道上不要加 fflags=nobuffer / flags=low_delay。
            // 它们在非可寻址管道上会让 grabImage() 在某一刻数据未凑齐一帧时误判为流结束、
            // 直接返回 null 使解码循环退出（表现为「无解码、也无失败日志、首帧超时」）。
        }
        activeGrabber.set(grabber)
        try {
            try {
                // 实时管道不能等待 FFmpeg 完整分析无限长的输入流；跳过全量流信息探测，
                // 让 H264 解码器在后续 grabImage() 时随着 SPS/PPS、IDR 到达而初始化。
                grabber.start(false)
                Log.info("相机 H264 解码器已启动（grabber.start 成功）")
            } catch (e: Exception) {
                Log.warning("相机 H264 解码器启动失败: ${e.message}\n${e.stackTraceToString()}")
                onError("解码器启动失败: ${e.message}")
                return
            }
            var frameCount = 0
            var consecutiveNulls = 0
            // 实时流首帧可能延迟到达，grabImage() 偶发返回 null 属正常，重试而非直接退出。
            while (activeGrabber.get() == grabber && consecutiveNulls < MAX_NULL_RETRIES) {
                val frame = try {
                    grabber.grabImage()
                } catch (e: Exception) {
                    if (activeGrabber.get() == grabber) {
                        val msg = e.message ?: e.javaClass.simpleName
                        Log.warning("相机解码异常: $msg\n${e.stackTraceToString()}")
                        onError(msg)
                    }
                    break
                }
                if (frame == null) {
                    consecutiveNulls++
                    if (consecutiveNulls == 1) {
                        Log.warning("相机解码 grabImage 返回 null（首帧可能尚未到达，继续等待）")
                    }
                    Thread.sleep(NULL_RETRY_INTERVAL_MS)
                    continue
                }
                consecutiveNulls = 0
                // FFmpeg 通常输出 3 通道 BGR，但部分平台会输出其他像素格式；
                // 直接复制失败时必须回退到 JavaCV 的通用转换，不能丢弃已经解出的画面。
                val buffered = frame.toBufferedImage() ?: converter.convert(frame)
                if (buffered != null) {
                    val bitmap = buffered.toComposeImageBitmap()
                    if (frameCount == 0) {
                        Log.info("相机已解码首帧（${buffered.width}x${buffered.height}）")
                    }
                    onFrame(bitmap)
                    frameCount++
                    if (frameCount % 30 == 0) {
                        Log.info("相机已解码 $frameCount 帧")
                    }
                } else {
                    // 解出了 Frame 却转不成图：记录一次格式信息，便于排查像素格式不匹配。
                    if (frameCount == 0) {
                        Log.warning(
                            "相机解码得到 Frame 但无法转为图像（imageChannels=${frame.imageChannels}, " +
                                "w=${frame.imageWidth}, h=${frame.imageHeight}），已跳过"
                        )
                    }
                }
            }
            if (frameCount == 0 && activeGrabber.get() == grabber) {
                val reason = if (consecutiveNulls >= MAX_NULL_RETRIES) {
                    "解码器持续收到空帧（相机 H264 流可能缺少可解码帧或起始码不被 FFmpeg 识别）"
                } else {
                    "解码循环提前退出（未产出任何帧）"
                }
                Log.warning("相机解码未产出任何帧: $reason")
                onError(reason)
            } else if (frameCount > 0) {
                Log.info("相机解码结束，共 $frameCount 帧")
            }
        } finally {
            activeGrabber.compareAndSet(grabber, null)
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
        }
    }

    private companion object {
        private val Log = Logger.getLogger(H264FrameDecoder::class.java.name)
        /** 实时流首帧延迟容忍：最多连续 150 次 null（约 15s）才判定为真正无帧，避免误杀。 */
        const val MAX_NULL_RETRIES = 150
        const val NULL_RETRY_INTERVAL_MS = 100L
    }

    /** 释放 FFmpeg 阻塞读取，供帧源切换与窗口关闭时调用。 */
    fun close() {
        activeGrabber.getAndSet(null)?.let { grabber ->
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
        }
    }

    /**
     * 将 FFmpeg 输出的 BGR 帧直接复制为 BufferedImage，避免 Java2DFrameConverter 的额外转换。
     * 仅支持 3 通道（BGR/RGB）打包格式；其余像素格式返回 null 交由上层兜底。
     */
    private fun Frame.toBufferedImage(): BufferedImage? {
        if (imageChannels != 3 || imageWidth <= 0 || imageHeight <= 0) return null
        val source = image.firstOrNull() as? ByteBuffer ?: return null
        val rowBytes = imageWidth * imageChannels
        if (imageStride < rowBytes) return null
        val target = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_3BYTE_BGR)
        val targetBytes = (target.raster.dataBuffer as DataBufferByte).data
        val duplicate = source.duplicate()
        repeat(imageHeight) { row ->
            duplicate.position(row * imageStride)
            duplicate.get(targetBytes, row * rowBytes, rowBytes)
        }
        return target
    }
}
