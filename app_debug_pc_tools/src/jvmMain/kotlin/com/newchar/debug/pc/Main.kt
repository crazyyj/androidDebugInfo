package com.newchar.debug.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Component
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File

private val APP_NAME = "PC Debug Tools"
private val LOCK_FILE = File(System.getProperty("java.io.tmpdir"), "pc-debug-tools.lock")

/** 创建桌面窗口原生 APK 拖放接收器，并将文件和落点回传给 Compose。 */
private fun createApkDropTarget(
    component: Component,
    onFilesDropped: (List<File>, Point) -> Boolean,
): DropTarget = DropTarget(component, DnDConstants.ACTION_COPY, object : DropTargetAdapter() {
    override fun dragEnter(event: java.awt.dnd.DropTargetDragEvent) {
        if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            event.acceptDrag(DnDConstants.ACTION_COPY)
        } else {
            event.rejectDrag()
        }
    }

    override fun drop(event: DropTargetDropEvent) {
        if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            event.rejectDrop()
            return
        }
        event.acceptDrop(DnDConstants.ACTION_COPY)
        val completed = readDroppedFiles(event)?.let { files -> onFilesDropped(files, event.location) } ?: false
        event.dropComplete(completed)
    }
}, true)

/** 从 AWT 拖放事件中读取本机文件列表。 */
@Suppress("UNCHECKED_CAST")
private fun readDroppedFiles(event: DropTargetDropEvent): List<File>? = runCatching {
    event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
}.getOrNull()

/** 单实例保护：不允许打开第二个 App，再次打开时让已有 App 获取焦点 */
private fun ensureSingleInstance() {
    try {
        if (LOCK_FILE.exists()) {
            val existingPid = LOCK_FILE.readText().trim().toLongOrNull()
            if (existingPid != null && isProcessAlive(existingPid)) {
                bringToFront(existingPid)
                System.exit(0)
                return
            }
        }
        // 写入当前 PID
        LOCK_FILE.parentFile.mkdirs()
        LOCK_FILE.writeText(ProcessHandle.current().pid().toString())
        // 注册退出时清理锁文件
        Runtime.getRuntime().addShutdownHook(Thread { LOCK_FILE.delete() })
    } catch (e: Exception) {
        // 锁文件获取失败不影响启动，继续
    }
}

private fun isProcessAlive(pid: Long): Boolean {
    return try {
        ProcessHandle.of(pid).isPresent
    } catch (e: Exception) {
        false
    }
}

private fun bringToFront(pid: Long) {
    val os = System.getProperty("os.name", "").lowercase()
    try {
        if (os.contains("mac")) {
            // macOS：通过 osascript 激活应用
            ProcessBuilder("osascript", "-e",
                "tell application \"" + APP_NAME + "\" to activate").start()
        } else if (os.contains("win")) {
            // Windows：通过 Tasklist + PowerShell 激活
            val psScript = "Get-Process -Id " + pid + " | ForEach-Object { " + "$" + "_.MainWindowHandle }"
            ProcessBuilder("powershell", "-c", psScript).start()
        } else {
            // Linux fallback：尝试使用 wmctrl
            runCatching { ProcessBuilder("wmctrl", "-a", "PC Debug Tools").start() }
        }
    } catch (e: Exception) {
        // 激活失败不影响，用户手动切换即可
    }
}

fun main() = application {
    ensureSingleInstance()
    setupSystemProperties()
    setDockIcon()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var appListDropBounds by remember { mutableStateOf<Rect?>(null) }
    var apkDropRequest by remember { mutableStateOf<ApkDropRequest?>(null) }
    var apkDropToken by remember { mutableStateOf(0L) }

    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 800.dp,
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "阿牛群控",
        state = windowState,
    ) {
        val appListDropHandler by rememberUpdatedState(newValue = { files: List<File>, location: Point ->
            val apkFile = files.firstOrNull { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            val insideAppList = appListDropBounds?.contains(Offset(location.x.toFloat(), location.y.toFloat())) == true
            if (apkFile == null || !insideAppList) {
                false
            } else {
                apkDropToken += 1
                apkDropRequest = ApkDropRequest(apkFile, apkDropToken)
                true
            }
        })
        DisposableEffect(window) {
            val container = window.contentPane
            val previousDropTarget = container.dropTarget
            val dropTarget = createApkDropTarget(container) { files, location ->
                appListDropHandler(files, location)
            }
            onDispose {
                if (container.dropTarget === dropTarget) container.dropTarget = previousDropTarget
            }
        }
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
                apkDropRequest = apkDropRequest,
                onApkDropConsumed = { apkDropRequest = null },
                onAppListDropBoundsChanged = { appListDropBounds = it },
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
