package com.newchar.debug.pc.device

import com.newchar.debug.pc.config.DesktopAppSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** 使用 gzip JSON 文件持久化各设备的应用列表快照。 */
class PackageSnapshotStore(
    private val configDirectory: String = DesktopAppSettingsStore.configDirectoryPath(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 读取设备上一次成功保存的快照；文件缺失或损坏时返回 null。 */
    suspend fun load(deviceId: String): PackageSnapshot? = withContext(Dispatchers.IO) {
        val file = snapshotFile(deviceId)
        if (!file.isFile) return@withContext null
        runCatching {
            GZIPInputStream(file.inputStream()).bufferedReader(StandardCharsets.UTF_8).use { reader ->
                json.decodeFromString<PackageSnapshot>(reader.readText())
            }.takeIf { it.deviceId == deviceId }
        }.getOrNull()
    }

    /** 将当前全量列表写入 gzip 快照，不包含已卸载展示项。 */
    suspend fun save(snapshot: PackageSnapshot) = withContext(Dispatchers.IO) {
        runCatching {
            val file = snapshotFile(snapshot.deviceId)
            file.parentFile?.mkdirs()
            GZIPOutputStream(file.outputStream()).bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write(json.encodeToString(snapshot))
            }
        }
    }

    /** 为设备 ID 生成跨平台安全且互不冲突的 gzip 文件路径。 */
    private fun snapshotFile(deviceId: String): File {
        val encodedId = URLEncoder.encode(deviceId, StandardCharsets.UTF_8)
        return File(File(configDirectory, "packages"), "packages_$encodedId.json.gz")
    }
}
