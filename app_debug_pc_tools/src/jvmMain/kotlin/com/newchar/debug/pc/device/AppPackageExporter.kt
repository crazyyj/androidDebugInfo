package com.newchar.debug.pc.device

import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 应用安装包导出结果。 */
data class AppPackageExportResult(
    val success: Boolean,
    val message: String,
    val directory: File? = null,
)

/** 通过 ADB 导出已安装应用的 base APK 与全部 split APK。 */
class AppPackageExporter(
    private val executor: CommandExecutor,
) {

    /** 将指定设备上的应用安装包导出到用户选择的目录。 */
    suspend fun export(
        deviceId: String,
        app: InstalledAppInfo,
        destinationDirectory: File,
    ): AppPackageExportResult = withContext(Dispatchers.IO) {
        if (!destinationDirectory.isDirectory) {
            return@withContext AppPackageExportResult(false, "导出目录不可用")
        }
        val apkPaths = loadApkPaths(deviceId, app.packageName).ifEmpty { app.apkPath.asApkPathList() }
        if (apkPaths.isEmpty()) {
            return@withContext AppPackageExportResult(false, "未读取到应用 APK 路径：${app.packageName}")
        }
        val exportDirectory = createExportDirectory(destinationDirectory, app)
            ?: return@withContext AppPackageExportResult(false, "无法创建应用导出目录")
        exportApkFiles(deviceId, apkPaths, exportDirectory)?.let { error ->
            return@withContext AppPackageExportResult(false, error, exportDirectory)
        }
        writeInstallInstructions(exportDirectory, app, apkPaths.map(::File))
        AppPackageExportResult(true, "已导出 ${apkPaths.size} 个 APK：${exportDirectory.absolutePath}", exportDirectory)
    }

    /** 从设备查询 base APK 与所有 split APK 的远端文件路径。 */
    private suspend fun loadApkPaths(deviceId: String, packageName: String): List<String> {
        val commands = listOf(
            arrayOf("shell", "pm", "path", packageName),
            arrayOf("shell", "cmd", "package", "path", packageName),
        )
        commands.forEach { command ->
            val result = executor.adb("-s", deviceId, *command)
            parseApkPaths(result.output).takeIf { result.isSuccess && it.isNotEmpty() }?.let { return it }
        }
        return emptyList()
    }

    /** 兼容标准 package: 前缀和厂商 ROM 直接返回 APK 路径的两种格式。 */
    private fun parseApkPaths(output: String): List<String> = output.lineSequence()
        .map(String::trim)
        .map { it.removePrefix("package:").trim() }
        .filter { it.startsWith('/') && it.endsWith(".apk", ignoreCase = true) }
        .distinct()
        .toList()

    /** 将应用列表已记录的 base APK 转为兜底的远端 APK 文件路径。 */
    private fun String.asApkPathList(): List<String> =
        takeIf { it.startsWith('/') && it.endsWith(".apk", ignoreCase = true) }?.let(::listOf).orEmpty()

    /** 在用户所选目录中创建不会覆盖旧导出内容的应用子目录。 */
    private fun createExportDirectory(destinationDirectory: File, app: InstalledAppInfo): File? {
        val baseName = "${safeFileName(app.packageName)}_${safeFileName(app.versionName.ifBlank { app.versionCode.ifBlank { "unknown" } })}"
        val directory = generateSequence(0) { it + 1 }
            .map { suffix -> File(destinationDirectory, if (suffix == 0) baseName else "${baseName}_$suffix") }
            .first { !it.exists() }
        return directory.takeIf { it.mkdirs() }
    }

    /** 依次拉取 APK，避免同一设备上并发 pull 导致连接不稳定。 */
    private suspend fun exportApkFiles(deviceId: String, apkPaths: List<String>, directory: File): String? {
        apkPaths.forEachIndexed { index, apkPath ->
            val target = File(directory, exportedFileName(apkPath, index))
            val result = executor.adb("-s", deviceId, "pull", apkPath, target.absolutePath)
            if (!result.isSuccess || !target.isFile || target.length() <= 0L) {
                val detail = result.error.ifBlank { result.output }.ifBlank { "ADB pull 未生成文件" }
                return "导出失败：远端 APK=$apkPath；本地文件=${target.absolutePath}；原因=$detail"
            }
        }
        return null
    }

    /** 生成 APK 在导出目录中的稳定文件名，并处理极少数重名 split。 */
    private fun exportedFileName(apkPath: String, index: Int): String {
        val name = File(apkPath).name.ifBlank { "split_$index.apk" }
        return if (index == 0 && name != "base.apk") "base.apk" else name
    }

    /** 写入包信息与安装命令，方便在其他设备上重新安装 split APK 集合。 */
    private fun writeInstallInstructions(directory: File, app: InstalledAppInfo, apkFiles: List<File>) {
        val commandFiles = apkFiles.mapIndexed { index, path -> exportedFileName(path.path, index) }
        val content = buildString {
            appendLine("包名：${app.packageName}")
            appendLine("版本：${app.versionName.ifBlank { "-" }} (${app.versionCode.ifBlank { "-" }})")
            appendLine("APK 数量：${commandFiles.size}")
            appendLine()
            appendLine("安装命令：")
            append("adb install-multiple -r ")
            append(commandFiles.joinToString(" "))
            appendLine()
        }
        File(directory, "安装说明.txt").writeText(content, Charsets.UTF_8)
    }

    /** 将包名或版本号转换为可跨平台保存的目录名称片段。 */
    private fun safeFileName(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}
