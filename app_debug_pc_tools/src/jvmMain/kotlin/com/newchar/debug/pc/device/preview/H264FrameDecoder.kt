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

/**
 * 通过 JavaCV/FFmpeg 解码 Annex-B H264 裸流。
 *
 * JavaCV 提供桌面端跨平台 native 解码；后续可替换为 scrcpy 协议以进一步降低延迟。
 */
internal class H264FrameDecoder {
    private val activeGrabber = AtomicReference<FFmpegFrameGrabber?>(null)

    /** 阻塞读取输入流并逐帧回调 Compose 位图，流结束后正常返回。 */
    fun decode(input: InputStream, onFrame: (ImageBitmap) -> Unit) {
        val converter = Java2DFrameConverter()
        val grabber = FFmpegFrameGrabber(input).apply {
            format = "h264"
            setOption("probesize", "16384")
            setOption("analyzeduration", "500000")
        }
        activeGrabber.set(grabber)
        try {
            grabber.start()
            while (true) {
                val frame = grabber.grabImage() ?: break
                frame.toComposeBitmap() ?: converter.convert(frame)?.toComposeImageBitmap()?.let(onFrame)
            }
        } finally {
            activeGrabber.compareAndSet(grabber, null)
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
        }
    }

    /** 释放 FFmpeg 阻塞读取，供帧源切换与窗口关闭时调用。 */
    fun close() {
        activeGrabber.getAndSet(null)?.let { grabber ->
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
        }
    }

    /** 直接复制 FFmpeg 已转换的 BGR 图像平面，避免 Java2DFrameConverter 的额外转换。 */
    private fun Frame.toComposeBitmap(): ImageBitmap? {
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
        return target.toComposeImageBitmap()
    }
}
