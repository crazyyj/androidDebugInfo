package com.newchar.debug.pc.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.util.Locale

fun chooseAdbExecutable(initialPath: String): String? {
    val dialog = FileDialog(null as Frame?, "选择 ADB 可执行文件", FileDialog.LOAD)
    val initialFile = initialPath.trim()
    if (initialFile.isNotBlank()) {
        val file = File(initialFile)
        dialog.directory = file.parent
        dialog.file = file.name
    }
    dialog.isVisible = true
    val selectedFile = dialog.file ?: return null
    val selectedDirectory = dialog.directory ?: return null
    return File(selectedDirectory, selectedFile).absolutePath
}

/** 打开 APK 文件选择器，返回用户选定的本地 APK 绝对路径；取消则返回 null。 */
fun chooseApkFile(): String? {
    val dialog = FileDialog(null as Frame?, "选择 APK 文件", FileDialog.LOAD)
    dialog.filenameFilter = FilenameFilter { _, name -> name.lowercase(Locale.ROOT).endsWith(".apk") }
    dialog.isVisible = true
    val selectedFile = dialog.file ?: return null
    val selectedDirectory = dialog.directory ?: return null
    return File(selectedDirectory, selectedFile).absolutePath
}

/** 打开输入脚本选择器，返回 LQITS JSON 文件。 */
fun chooseInputScriptFile(): File? {
    val dialog = FileDialog(null as Frame?, "选择输入脚本", FileDialog.LOAD)
    dialog.filenameFilter = FilenameFilter { _, name ->
        val lowerName = name.lowercase(Locale.ROOT)
        lowerName.endsWith(".json") || lowerName.endsWith(".lqits")
    }
    dialog.isVisible = true
    val selectedFile = dialog.file ?: return null
    val selectedDirectory = dialog.directory ?: return null
    return File(selectedDirectory, selectedFile)
}

/** 打开目录选择器，并返回用户选定的导出根目录。 */
fun chooseExportDirectory(): File? = when {
    operatingSystemName().contains("mac") -> chooseMacExportDirectory()
    operatingSystemName().contains("windows") -> chooseWindowsExportDirectory()
    else -> chooseLinuxExportDirectory()
}

/** 使用 macOS 原生“选择文件夹”面板选择导出目录。 */
private fun chooseMacExportDirectory(): File? = runNativeDirectoryChooser(
    listOf("/usr/bin/osascript", "-e", "POSIX path of (choose folder with prompt \"选择 APK 导出目录\")"),
)

/** 使用 Windows Shell 的原生目录选择框选择导出目录。 */
private fun chooseWindowsExportDirectory(): File? = runNativeDirectoryChooser(
    listOf(
        "powershell", "-NoProfile", "-Command",
        "\$shell = New-Object -ComObject Shell.Application; \$folder = \$shell.BrowseForFolder(0, '选择 APK 导出目录', 0, 0); if (\$null -ne \$folder) { \$folder.Self.Path }",
    ),
)

/** 使用 Linux 桌面环境已安装的原生目录选择器选择导出目录。 */
private fun chooseLinuxExportDirectory(): File? =
    runNativeDirectoryChooser(listOf("zenity", "--file-selection", "--directory", "--title=选择 APK 导出目录"))
        ?: runNativeDirectoryChooser(listOf("kdialog", "--getexistingdirectory", "~"))

/** 执行平台原生命令并将标准输出中的目录路径转换为本地文件对象。 */
private fun runNativeDirectoryChooser(command: List<String>): File? = runCatching {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    File(output).takeIf { process.waitFor() == 0 && it.isDirectory }
}.getOrNull()

/** 返回统一为小写的当前桌面操作系统名称。 */
private fun operatingSystemName(): String = System.getProperty("os.name", "").lowercase(Locale.ROOT)