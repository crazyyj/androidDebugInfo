package com.newchar.debug.pc.device

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.newchar.debug.pc.config.DesktopAppSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 应用图标磁盘缓存：按 `deviceId / packageName / versionCode` 写入 PNG，
 * UI 层可直接同步读取为 Compose [ImageBitmap]。
 *
 * - 写入：解析设备 agent 返回的 PNG 字节数组后落盘，下次扫描命中同一版本号可直接复用。
 * - 读取：基于 Skia 解码；解码失败返回 `null`，调用方按占位符处理。
 */
class AppIconCache(
    private val configDirectory: String = DesktopAppSettingsStore.configDirectoryPath(),
) {

    /** 返回指定 (device, package, version) 对应的图标文件，可能不存在。 */
    fun iconFile(deviceId: String, packageName: String, versionCode: String): File {
        val safeDevice = URLEncoder.encode(deviceId, StandardCharsets.UTF_8)
        val safePkg = URLEncoder.encode(packageName, StandardCharsets.UTF_8)
        val safeVer = URLEncoder.encode(versionCode.ifBlank { "0" }, StandardCharsets.UTF_8)
        val dir = File(File(configDirectory, "icons"), safeDevice).also { it.mkdirs() }
        return File(dir, "${safePkg}_${safeVer}.png")
    }

    /** 同步读取图标；解码失败返回 `null`。 */
    fun loadIconBitmap(deviceId: String, packageName: String, versionCode: String): ImageBitmap? {
        val file = iconFile(deviceId, packageName, versionCode)
        if (!file.isFile) return null
        return runCatching {
            Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
        }.getOrNull()
    }

    /** 仅判断磁盘上是否存在对应图标文件（不做解码，用于冷启动预热的缺失检测）。 */
    fun existsIcon(deviceId: String, packageName: String, versionCode: String): Boolean {
        return iconFile(deviceId, packageName, versionCode).isFile
    }

    /** 写入 PNG 字节到磁盘，返回写入后的文件（异常时返回 `null`）。 */
    suspend fun save(
        deviceId: String,
        packageName: String,
        versionCode: String,
        pngBytes: ByteArray,
    ): File? = withContext(Dispatchers.IO) {
        if (pngBytes.isEmpty()) return@withContext null
        runCatching {
            val target = iconFile(deviceId, packageName, versionCode)
            target.parentFile?.mkdirs()
            target.writeBytes(pngBytes)
            target
        }.getOrNull()
    }

    /** 删除指定 (device, package) 下的全部版本图标（卸载后清理用）。 */
    suspend fun clearPackage(deviceId: String, packageName: String) = withContext(Dispatchers.IO) {
        val safeDevice = URLEncoder.encode(deviceId, StandardCharsets.UTF_8)
        val safePkg = URLEncoder.encode(packageName, StandardCharsets.UTF_8)
        val dir = File(File(configDirectory, "icons"), safeDevice)
        if (!dir.isDirectory) return@withContext
        dir.listFiles { f -> f.isFile && f.nameWithoutExtension.startsWith("${safePkg}_") }
            ?.forEach { it.delete() }
    }
}