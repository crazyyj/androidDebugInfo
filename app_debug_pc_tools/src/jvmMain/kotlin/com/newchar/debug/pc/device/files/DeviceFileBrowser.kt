package com.newchar.debug.pc.device.files

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.device.InstalledAppInfo
import com.newchar.debug.pc.executor.AdbCommandExecutor
import com.newchar.debug.pc.ui.AppText
import com.newchar.debug.pc.ui.AppTheme
import com.newchar.debug.pc.ui.PanelCard
import com.newchar.debug.pc.ui.chooseExportDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class FileEntry(
    val name: String,
    val isDirectory: Boolean,
    val detail: String,
)

private val FILE_ITEM_HEIGHT = 36.dp
private val FILE_LIST_MAX_ITEMS = 10

/** 内联文件浏览器：默认展示 `/sdcard` 下 ADB 实际可读的内容。 */
@Composable
fun DeviceFileBrowser(
    device: DeviceInfo,
    executor: AdbCommandExecutor,
    scope: CoroutineScope,
    recentDebugApps: List<InstalledAppInfo>,
    onToast: (String) -> Unit,
) {
    val online = device.canManageDevice
    val adbTarget = device.adbTarget()
    val physicalDeviceKey = device.physicalDeviceKey()
    var currentPath by remember(physicalDeviceKey) { mutableStateOf("") }
    var entries by remember(physicalDeviceKey) { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember(physicalDeviceKey) { mutableStateOf(false) }
    var errorText by remember(physicalDeviceKey) { mutableStateOf<String?>(null) }
    var exportJob by remember(physicalDeviceKey) { mutableStateOf<Job?>(null) }
    var exportRoute by remember(physicalDeviceKey) { mutableStateOf<String?>(null) }

    /** ADB 目标变化时中止旧链路的导出，避免将结果误认为由新链路继续传输。 */
    LaunchedEffect(adbTarget) {
        if (exportRoute != null && exportRoute != adbTarget && exportJob?.isActive == true) {
            exportJob?.cancel()
            onToast("连接链路已变化，已取消当前导出，请刷新后重试")
        }
        exportRoute = adbTarget
    }

    LaunchedEffect(adbTarget, online, currentPath) {
        if (!online) {
            entries = emptyList()
            errorText = "设备离线，无法浏览文件"
            return@LaunchedEffect
        }
        val path = currentPath.ifBlank { SDCARD_ROOT }
        loading = true
        errorText = null
        val listed = listDirectory(executor, adbTarget, path)
        loading = false
        if (listed == null) {
            entries = emptyList()
            errorText = "无法读取 $path（仅展示 ADB shell 可访问的内容）"
        } else {
            entries = listed
            errorText = if (listed.isEmpty()) "目录为空" else null
        }
    }

    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                "文件浏览",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.textPrimary),
            )
            AppText("/sdcard", style = TextStyle(fontSize = 11.sp, color = AppTheme.textSecondary), maxLines = 1)
        }
        val recentApps = remember(recentDebugApps) { recentDebugApps.recentDebugApps() }
        if (recentApps.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText("最近 Debug", style = TextStyle(fontSize = 11.sp, color = AppTheme.textHint))
                recentApps.forEach { app ->
                    RootChip(text = app.displayName, selected = false) {
                        currentPath = "$SDCARD_ROOT/Android/data/${app.packageName}"
                    }
                }
            }
        }
        val displayPath = currentPath.ifBlank { SDCARD_ROOT }
        BreadcrumbRow(
            path = displayPath,
            onNavigate = { currentPath = it },
        )
        when {
            loading -> AppText("读取中…", style = TextStyle(fontSize = 12.sp, color = AppTheme.accent))
            !errorText.isNullOrBlank() -> AppText(errorText!!, style = TextStyle(fontSize = 12.sp, color = AppTheme.danger), maxLines = 3)
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = FILE_ITEM_HEIGHT * FILE_LIST_MAX_ITEMS),
                ) {
                    items(entries) { entry ->
                        FileRow(
                            entry = entry,
                            isRoot = displayPath == SDCARD_ROOT,
                            onOpen = {
                                if (entry.isDirectory) {
                                    currentPath = "$displayPath/${entry.name}".replace("//", "/")
                                }
                            },
                            onExport = {
                                val remote = "$displayPath/${entry.name}".replace("//", "/")
                                exportJob?.cancel()
                                exportJob = scope.launch {
                                    val directory = withContext(Dispatchers.IO) { chooseExportDirectory() }
                                    if (directory == null) return@launch
                                    val result = executor.adbCancellable("-s", adbTarget, "pull", remote, directory.absolutePath)
                                    onToast(
                                        if (result.isSuccess) {
                                            "已导出到 ${directory.absolutePath}"
                                        } else {
                                            "导出失败：${result.error.ifBlank { result.output }}"
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RootChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) AppTheme.accent else AppTheme.border, RoundedCornerShape(8.dp))
            .background(if (selected) AppTheme.accentSoft else AppTheme.panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) AppTheme.accent else AppTheme.textSecondary,
            ),
        )
    }
}

@Composable
private fun BreadcrumbRow(path: String, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppText("路径", style = TextStyle(fontSize = 11.sp, color = AppTheme.textHint))
        AppText(
            path,
            style = TextStyle(fontSize = 11.sp, color = AppTheme.textSecondary),
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (path.count { it == '/' } > 2) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, AppTheme.border, RoundedCornerShape(6.dp))
                    .clickable { onNavigate(path.substringBeforeLast('/')) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText("上级", style = TextStyle(fontSize = 11.sp, color = AppTheme.textSecondary))
            }
        }
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    isRoot: Boolean,
    onOpen: () -> Unit,
    onExport: () -> Unit,
) {
    ContextMenuArea(
        items = {
            if (entry.isDirectory) {
                emptyList()
            } else {
                listOf(ContextMenuItem("导出到电脑…") { onExport() })
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FILE_ITEM_HEIGHT)
                .clickable(enabled = entry.isDirectory) { onOpen() }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppText(
                if (entry.isDirectory) "\uD83D\uDCC1" else "\uD83D\uDCC4",
                style = TextStyle(fontSize = 14.sp),
            )
            AppText(
                entry.name,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = AppTheme.textPrimary,
                    fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            AppText(
                entry.detail,
                style = TextStyle(fontSize = 11.sp, color = AppTheme.textHint),
                maxLines = 1,
            )
            if (isRoot && entry.isDirectory) {
                AppText("›", style = TextStyle(fontSize = 14.sp, color = AppTheme.textHint))
            }
        }
    }
}

/** 列出设备的公开目录；仅使用 ADB shell，读取失败时不尝试越权。 */
private suspend fun listDirectory(
    executor: AdbCommandExecutor,
    deviceId: String,
    path: String,
): List<FileEntry>? = withContext(Dispatchers.IO) {
    val result = executor.shell(deviceId, "ls", "-la", path)
    if (!result.isSuccess) return@withContext null
    val text = result.output.ifBlank { result.error }
    if (text.isBlank() || text.contains("No such file", ignoreCase = true)) return@withContext null
    val parsed = parseLsOutput(text)
    parsed.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name })
}

/** 按安装时间取最近的三个 debuggable App，供文件根目录快捷跳转使用。 */
private fun List<InstalledAppInfo>.recentDebugApps(): List<InstalledAppInfo> =
    filter(InstalledAppInfo::isDebuggable)
        .sortedByDescending(InstalledAppInfo::firstInstallTime)
        .take(3)

private const val SDCARD_ROOT = "/sdcard"

/** 解析 `ls -la` 输出：权限 链接数 属主 属组 大小 日期 时间 名称。 */
internal fun parseLsOutput(text: String): List<FileEntry> =
    text.lineSequence().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isBlank() || line.startsWith("total")) return@mapNotNull null
        val parts = line.split(Regex("\\s+"), limit = 8)
        if (parts.size < 8) return@mapNotNull null
        val permission = parts[0]
        val name = parts[7].substringBefore(" -> ")
        if (name == "." || name == "..") return@mapNotNull null
        val isDirectory = permission.startsWith("d")
        val detail = if (isDirectory) permission else formatSize(parts[4].toLongOrNull() ?: 0L)
        FileEntry(name, isDirectory, detail)
    }.toList()

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}