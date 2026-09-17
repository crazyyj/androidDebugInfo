package com.newchar.debug.pc.device.preview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.newchar.debug.pc.config.DesktopAppSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File
import java.security.MessageDigest

/**
 * 按设备保存最后一张屏幕截图，使首页和详情预览在新的截图返回前可立即展示旧画面。
 *
 * 每台设备仅保留一个 PNG 文件，新的截图会原子性替换旧文件。
 */
class ScreenFrameCache(
    private val cacheDirectory: File = File(DesktopAppSettingsStore.configDirectoryPath(), "screen-frames"),
) {
    /** 从磁盘读取指定设备最近一次成功截图；文件不存在或损坏时返回 null。 */
    suspend fun load(deviceId: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = frameFile(deviceId)
        if (!file.isFile) return@withContext null
        runCatching { Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap() }.getOrNull()
    }

    /** 保存指定设备最近一次成功截图，失败时保留原有缓存。 */
    suspend fun save(deviceId: String, pngBytes: ByteArray) = withContext(Dispatchers.IO) {
        if (pngBytes.isEmpty()) return@withContext
        runCatching {
            val target = frameFile(deviceId)
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.writeBytes(pngBytes)
            if (!temporary.renameTo(target)) {
                target.writeBytes(pngBytes)
                temporary.delete()
            }
        }
    }

    /** 以设备 ID 的 SHA-256 值生成跨平台安全的缓存文件名。 */
    private fun frameFile(deviceId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(deviceId.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(cacheDirectory, "$name.png")
    }
}
