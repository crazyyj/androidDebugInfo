package com.newchar.debug.pc.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

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
