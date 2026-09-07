package com.newchar.debug.pc.device.preview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage

/** 将 NV12 原始帧转换为 Compose 可渲染的 RGB 位图。 */
internal object YuvFrameConverter {

    /** 使用 BT.601 全范围公式将一帧 NV12 数据转为 ARGB 位图。 */
    fun toImage(nv12: ByteArray, width: Int, height: Int): ImageBitmap {
        require(nv12.size >= frameSize(width, height)) { "NV12 帧数据不完整" }
        val pixels = IntArray(width * height)
        fillArgbPixels(nv12, width, height, pixels)
        return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, width, height, pixels, 0, width)
        }.toComposeImageBitmap()
    }

    /** 计算指定分辨率的一帧 NV12 字节数。 */
    fun frameSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "无效原始帧分辨率: ${width}x$height" }
        return width * height * 3 / 2
    }

    /** 逐像素执行 NV12 到 ARGB 的色彩空间转换。 */
    private fun fillArgbPixels(nv12: ByteArray, width: Int, height: Int, pixels: IntArray) {
        val ySize = width * height
        for (y in 0 until height) {
            val uvRow = ySize + (y / 2) * width
            for (x in 0 until width) {
                val luminance = nv12[y * width + x].toInt() and 0xFF
                val uvOffset = uvRow + (x and 1.inv())
                val u = (nv12[uvOffset].toInt() and 0xFF) - 128
                val v = (nv12[uvOffset + 1].toInt() and 0xFF) - 128
                pixels[y * width + x] = rgb(luminance, u, v)
            }
        }
    }

    /** 将一个 YUV 像素按 BT.601 全范围系数编码为不透明 ARGB。 */
    private fun rgb(y: Int, u: Int, v: Int): Int {
        val red = (y + 1.402f * v).toInt().coerceIn(0, 255)
        val green = (y - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
        val blue = (y + 1.772f * u).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
