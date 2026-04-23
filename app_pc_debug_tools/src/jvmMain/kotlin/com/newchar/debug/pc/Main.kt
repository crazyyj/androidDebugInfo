package com.newchar.debug.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    setupSystemProperties()
    setDockIcon()

    var showSettingsDialog by remember { mutableStateOf(false) }

    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 800.dp,
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "PC Debug Tools",
        state = windowState,
    ) {
        MenuBar {
            Menu("设置") {
                Item("打开设置", onClick = { showSettingsDialog = true })
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F6F8)),
        ) {
            AppContent(
                showSettingsDialog = showSettingsDialog,
                onDismissSettings = { showSettingsDialog = false },
            )
        }
    }
}

private fun setupSystemProperties() {
    System.setProperty("apple.awt.graphics.EnableQ2DX", "true")
    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("apple.awt.textAntialiasing", "on")
}

private fun setDockIcon() {
    runCatching {
        val iconPath = when {
            System.getProperty("os.name").contains("Mac", ignoreCase = true) ->
                "src/jvmMain/resources/icons/app_icon.icns"
            System.getProperty("os.name").contains("Win", ignoreCase = true) ->
                "src/jvmMain/resources/icons/app_icon.ico"
            else ->
                "src/jvmMain/resources/icons/app_icon.png"
        }

        val iconFile = java.io.File(iconPath)
        if (iconFile.exists()) {
            val image = javax.imageio.ImageIO.read(iconFile)
            image?.let { img ->
                if (java.awt.Desktop.isDesktopSupported()) {
                    val taskbar = java.awt.Taskbar.getTaskbar()
                    taskbar.iconImage = img
                }
            }
        }
    }
}
