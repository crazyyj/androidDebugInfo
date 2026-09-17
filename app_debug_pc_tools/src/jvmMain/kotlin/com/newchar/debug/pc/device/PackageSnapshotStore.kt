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
        readSnapshot(file)?.takeIf { it.deviceId == deviceId }
    }

    /** 读取全部设备的有效应用快照，供冷启动时后台预热图标缓存。 */
    suspend fun loadAll(): List<PackageSnapshot> = withContext(Dispatchers.IO) {
        val directory = File(configDirectory, "packages")
        if (!directory.isDirectory) return@withContext emptyList()
        directory.listFiles { file -> file.isFile && file.name.startsWith("packages_") }
            ?.mapNotNull(::readSnapshot)
            .orEmpty()
    }

    /** 读取并清洗单个快照文件，文件缺失、损坏或内容非法时返回 null。 */
    private fun readSnapshot(file: File): PackageSnapshot? {
        if (!file.isFile) return null
        return runCatching {
            GZIPInputStream(file.inputStream()).bufferedReader(StandardCharsets.UTF_8).use { reader ->
                json.decodeFromString<PackageSnapshot>(reader.readText())
            }
                // 清洗历史脏数据：早期版本会把包名解析成 `=/base.apk=com.foo.bar`，
                // 这类记录会让 diff 误判为"已卸载"，这里直接丢弃。
                .let { snapshot -> snapshot.copy(apps = snapshot.apps.filter { it.packageName.isValidPackageName() }) }
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
