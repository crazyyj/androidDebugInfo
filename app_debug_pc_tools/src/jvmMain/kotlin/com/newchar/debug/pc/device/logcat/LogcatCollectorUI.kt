@file:OptIn(ExperimentalStdlibApi::class)

package com.newchar.debug.pc.device.logcat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Popup
import androidx.compose.ui.layout.Layout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.device.InstalledAppInfo
import com.newchar.debug.pc.device.PackageInspector
import com.newchar.debug.pc.ui.AppText
import com.newchar.debug.pc.ui.AppDivider
import com.newchar.debug.pc.ui.AppTheme
import kotlinx.coroutines.launch

@Composable
fun LogcatCollectorPanel(
    devices: List<DeviceInfo>,
    onDismiss: () -> Unit,
    executor: com.newchar.debug.pc.executor.CommandExecutor,
) {
    val scope = rememberCoroutineScope()
    val collector = remember { LogcatCollector(executor, scope) }

    var selectedDevices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var packageName by remember { mutableStateOf("") }
    val tagList = remember { mutableStateListOf<String>() }
    var currentTagInput by remember { mutableStateOf("") }
    var saveCrashLogs by remember { mutableStateOf(true) }
    var selectedLogLevel by remember { mutableStateOf(LogLevel.WARN) }
    var collectionResult by remember { mutableStateOf<LogcatCollector.CollectionResult?>(null) }

    val debugApps = remember { mutableStateListOf<InstalledAppInfo>() }
    var isLoadingApps by remember { mutableStateOf(false) }

    val collectorState by collector.state.collectAsState()
    val entries by collector.collectedEntries.collectAsState()
    val crashLogs by collector.crashLogs.collectAsState()
    val deviceCounts by collector.deviceLogCounts.collectAsState()

    LaunchedEffect(collectorState) {
        if (collectorState == LogcatCollector.CollectorState.STOPPED ||
            collectorState == LogcatCollector.CollectorState.ERROR) {
            collectionResult = if (collectorState == LogcatCollector.CollectorState.ERROR) {
                LogcatCollector.CollectionResult(
                    success = false,
                    totalEntries = 0,
                    crashCount = 0,
                    duration = 0L,
                    outputDir = "",
                    error = "Collection error occurred"
                )
            } else {
                null
            }
        }
    }

    LaunchedEffect(selectedDevices) {
        if (selectedDevices.isNotEmpty()) {
            isLoadingApps = true
            debugApps.clear()
            packageName = ""

            try {
                val allDebugApps = mutableListOf<InstalledAppInfo>()
                for (device in selectedDevices) {
                    val apps = PackageInspector.loadInstalledApps(executor, device.id)
                        .filter { it.isDebuggable }
                    allDebugApps.addAll(apps)
                }

                val uniqueApps = allDebugApps
                    .groupBy { it.packageName }
                    .map { (_, apps) -> apps.first() }
                    .sortedWith(compareByDescending<InstalledAppInfo> { it.isDebuggable }.thenBy { it.packageName })

                debugApps.addAll(uniqueApps)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingApps = false
            }
        } else {
            debugApps.clear()
            packageName = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F6F8))
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppText(
                text = "Logcat 日志收集器",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2430)),
            )

            AppText(
                text = "配置收集参数，选择设备和过滤条件",
                style = TextStyle(fontSize = 14.sp, color = Color(0xFF5B6472)),
            )

            DeviceSelectionSection(
                devices = devices,
                selectedDevices = selectedDevices,
                onDeviceToggle = { device ->
                    selectedDevices = if (device in selectedDevices) {
                        selectedDevices - device
                    } else {
                        selectedDevices + device
                    }
                },
            )

            if (selectedDevices.isNotEmpty()) {
                DebugAppSelector(
                    debugApps = debugApps,
                    selectedPackage = packageName,
                    onPackageSelect = { packageName = it },
                    isLoading = isLoadingApps,
                )
            } else {
                AppText(
                    text = "⚠️ 请先选择设备以加载 Debug 应用列表",
                    style = TextStyle(fontSize = 14.sp, color = Color(0xFFC44536), fontWeight = FontWeight.Medium),
                )
            }

            TagAndLogLevelSection(
                tags = tagList,
                currentInput = currentTagInput,
                onInputChange = { currentTagInput = it },
                onAddTag = {
                    val newTag = currentTagInput.trim()
                    if (newTag.isNotBlank()) {
                        if (tagList.contains(newTag)) {
                            tagList.remove(newTag)
                        }
                        if (tagList.size < 20) {
                            tagList.add(newTag)
                        }
                        currentTagInput = ""
                    }
                },
                onRemoveTag = { tag -> tagList.remove(tag) },
                selectedLevel = selectedLogLevel,
                onLevelChange = { selectedLogLevel = it },
            )

            CrashLogSection(
                saveCrashLogs = saveCrashLogs,
                onSaveChange = { saveCrashLogs = it },
            )

            ActionButtonsSection(
                state = collectorState,
                isConfigValid = selectedDevices.isNotEmpty(),
                onStart = {
                    val timestamp = System.currentTimeMillis().toString()
                    val safePackageName = packageName.ifBlank { "unknown" }.replace(".", "_")
                    val outputDir = "${System.getProperty("user.home")}/Desktop/app_debug/$safePackageName/log/$timestamp"

                    val config = LogcatCollector.LogcatCollectConfig(
                        deviceIds = selectedDevices.map { it.id },
                        packageName = packageName,
                        selectedTags = tagList.toSet(),
                        saveCrashLogs = saveCrashLogs,
                        minLogLevel = selectedLogLevel,
                        outputDirectory = outputDir,
                    )
                    scope.launch {
                        collector.startCollection(config)
                    }
                },
                onStop = {
                    scope.launch {
                        val result = collector.stopCollection()
                        collectionResult = result
                    }
                },
                onDismiss = onDismiss,
            )

            AnimatedVisibility(visible = collectorState == LogcatCollector.CollectorState.COLLECTING) {
                CollectionStatsPanel(
                    entryCount = entries.size,
                    crashCount = crashLogs.size,
                    deviceCounts = deviceCounts,
                    startTime = collector.startTime,
                )
            }

            collectionResult?.let { result ->
                CollectionResultPanel(result = result)
            }
        }
    }
}

@Composable
private fun DeviceSelectionSection(
    devices: List<DeviceInfo>,
    selectedDevices: List<DeviceInfo>,
    onDeviceToggle: (DeviceInfo) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(
            text = "设备选择 (${selectedDevices.size}/${devices.size})",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2430)),
        )

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color(0xFFF0F2F5), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = "未检测到可用设备",
                    style = TextStyle(fontSize = 14.sp, color = Color(0xFF8A93A3)),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(minOf((devices.size * 48).dp, 200.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFD7DCE3), RoundedCornerShape(8.dp))
                    .background(Color.White),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(devices) { device ->
                    val isSelected = device in selectedDevices
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeviceToggle(device) }
                            .background(if (isSelected) Color(0xFFE6EEFF) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) Color(0xFF3566D6) else Color(0xFFD7DCE3)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                AppText(
                                    text = "✓",
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(
                                text = device.model.ifBlank { device.id },
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            )
                            AppText(
                                text = "${device.id} · ${device.status}",
                                style = TextStyle(fontSize = 12.sp, color = Color(0xFF8A93A3)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugAppSelector(
    debugApps: List<InstalledAppInfo>,
    selectedPackage: String,
    onPackageSelect: (String) -> Unit,
    isLoading: Boolean = false,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(searchQuery, debugApps) {
        if (searchQuery.isBlank()) {
            debugApps
        } else {
            debugApps.filter {
                it.packageName.contains(searchQuery, ignoreCase = true) ||
                    it.versionName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val selectedApp = debugApps.find { it.packageName == selectedPackage }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(
            text = "选择 Debug 应用",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2430)),
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFD7DCE3), RoundedCornerShape(8.dp))
                    .background(Color.White),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (selectedApp != null) {
                            AppText(
                                text = selectedApp.packageName,
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1F2430)),
                            )
                            AppText(
                                text = "v${selectedApp.versionName} (${selectedApp.versionCode})",
                                style = TextStyle(fontSize = 12.sp, color = Color(0xFF8A93A3)),
                            )
                        } else {
                            AppText(
                                text = if (isLoading) "正在加载应用列表..." else "请选择一个 Debug 应用",
                                style = TextStyle(fontSize = 14.sp, color = Color(0xFF8A93A3)),
                            )
                        }
                    }
                    AppText(
                        text = if (isExpanded) "▲" else "▼",
                        style = TextStyle(fontSize = 14.sp, color = Color(0xFF5B6472)),
                    )
                }

                AnimatedVisibility(visible = isExpanded && !isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .border(1.dp, Color(0xFFD7DCE3), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(fontSize = 14.sp, color = Color(0xFF1F2430)),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.padding(2.dp)) {
                                    if (searchQuery.isEmpty()) {
                                        AppText(
                                            text = "🔍 搜索应用包名或版本...",
                                            style = TextStyle(fontSize = 13.sp, color = Color(0xFFB0B8C4)),
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )

                        AppDivider(color = Color(0xFFE8ECF1))

                        if (filteredApps.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppText(
                                    text = "未找到匹配的 Debug 应用",
                                    style = TextStyle(fontSize = 13.sp, color = Color(0xFF8A93A3)),
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 250.dp),
                            ) {
                                items(filteredApps.size) { index ->
                                    val app = filteredApps[index]
                                    val isSelected = app.packageName == selectedPackage

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onPackageSelect(app.packageName)
                                                isExpanded = false
                                                searchQuery = ""
                                            }
                                            .background(if (isSelected) Color(0xFFE6EEFF) else Color.Transparent)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    2.dp,
                                                    if (isSelected) Color(0xFF3566D6) else Color(0xFFD7DCE3),
                                                    CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF3566D6)),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            AppText(
                                                text = app.packageName,
                                                style = TextStyle(
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = Color(0xFF1F2430),
                                                ),
                                            )
                                            AppText(
                                                text = "v${app.versionName} · ${if (app.isSystemApp) "系统应用" else "第三方"}",
                                                style = TextStyle(fontSize = 12.sp, color = Color(0xFF8A93A3)),
                                            )
                                        }
                                        if (isSelected) {
                                            AppText(
                                                text = "✓",
                                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3566D6)),
                                            )
                                        }
                                    }
                                    if (index < filteredApps.size - 1) {
                                        AppDivider(
                                            modifier = Modifier.padding(start = 40.dp),
                                            color = Color(0xFFF0F2F5),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isLoading && debugApps.isNotEmpty()) {
            AppText(
                text = "共 ${debugApps.size} 个 Debug 应用可供选择",
                style = TextStyle(fontSize = 12.sp, color = Color(0xFF8A93A3)),
            )
        }
    }
}

@Composable
private fun TagAndLogLevelSection(
    tags: List<String>,
    currentInput: String,
    onInputChange: (String) -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
    selectedLevel: LogLevel,
    onLevelChange: (LogLevel) -> Unit,
) {
    val maxTags = 20
    val isMaxReached = tags.size >= maxTags

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = "Tag 过滤与日志级别",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2430)),
            )
            AppText(
                text = "${tags.size}/$maxTags",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = if (isMaxReached) Color(0xFFC44536) else Color(0xFF5B6472),
                    fontWeight = if (isMaxReached) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = currentInput,
                    onValueChange = onInputChange,
                    textStyle = TextStyle(fontSize = 14.sp, color = Color(0xFF1F2430)),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFD7DCE3), RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.padding(2.dp)) {
                            if (currentInput.isEmpty()) {
                                AppText(
                                    text = "输入 Tag 名称...",
                                    style = TextStyle(fontSize = 14.sp, color = Color(0xFF8A93A3)),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isMaxReached) Color(0xFFB0BEC5) else Color(0xFF3566D6))
                        .clickable(enabled = !isMaxReached, onClick = onAddTag)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = if (isMaxReached) "已达上限" else "添加",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White),
                    )
                }
            }

            Box(modifier = Modifier.width(120.dp)) {
                var isExpanded by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFD7DCE3), RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .clickable { isExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(getLogLevelColor(selectedLevel)),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppText(
                                    text = selectedLevel.shortLabel,
                                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White),
                                )
                            }
                            AppText(
                                text = selectedLevel.label,
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1F2430)),
                            )
                        }
                        AppText(
                            text = "▼",
                            style = TextStyle(fontSize = 12.sp, color = Color(0xFF5B6472)),
                        )
                    }
                }

                if (isExpanded) {
                    Popup(
                        alignment = Alignment.TopStart,
                        onDismissRequest = { isExpanded = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .width(200.dp)
                                .background(Color.White)
                                .border(1.dp, Color(0xFFD7DCE3), RoundedCornerShape(8.dp))
                                .padding(vertical = 4.dp)
                        ) {
                            LogLevel.entries.reversed().forEach { level ->
                                val isSelected = level == selectedLevel
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onLevelChange(level)
                                            isExpanded = false
                                        }
                                        .background(if (isSelected) Color(0xFFF5F8FF) else Color.Transparent)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(getLogLevelColor(level)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AppText(
                                            text = level.shortLabel,
                                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        AppText(
                                            text = level.label,
                                            style = TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = Color(0xFF1F2430),
                                            ),
                                        )
                                        AppText(
                                            text = "优先级: ${level.priority}",
                                            style = TextStyle(fontSize = 11.sp, color = Color(0xFF8A93A3)),
                                        )
                                    }
                                    if (isSelected) {
                                        AppText(
                                            text = "✓",
                                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3566D6)),
                                        )
                                    }
                                }
                                if (level != LogLevel.entries.first()) {
                                    AppDivider(color = Color(0xFFF0F2F5))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (tags.isNotEmpty()) {
            FlowRow(
                mainAxisSpacing = 6.dp,
                crossAxisSpacing = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE6EEFF))
                            .clickable { onRemoveTag(tag) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppText(
                                text = tag,
                                style = TextStyle(fontSize = 13.sp, color = Color(0xFF3566D6)),
                            )
                            AppText(text = "×", style = TextStyle(fontSize = 14.sp, color = Color(0xFF3566D6)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getLogLevelColor(level: LogLevel): Color {
    return when (level) {
        LogLevel.VERBOSE -> Color(0xFF9E9E9E)
        LogLevel.DEBUG -> Color(0xFF2196F3)
        LogLevel.INFO -> Color(0xFF4CAF50)
        LogLevel.WARN -> Color(0xFFFF9800)
        LogLevel.ERROR -> Color(0xFFF44336)
        LogLevel.FATAL -> Color(0xFF9C27B0)
        LogLevel.SILENT -> Color(0xFF607D8B)
    }
}

@Composable
private fun CrashLogSection(
    saveCrashLogs: Boolean,
    onSaveChange: (Boolean) -> Unit,
) {
    val backgroundColor = if (saveCrashLogs) Color(0xFF3566D6) else Color(0xFFD7DCE3)
    val toggleText = if (saveCrashLogs) "ON" else "OFF"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSaveChange(!saveCrashLogs) }
            .background(Color.White)
            .border(1.dp, Color(0xFFD7DCE3), RoundedCornerShape(8.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            AppText(
                text = "保存崩溃日志",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2430)),
            )
            AppText(
                text = "包括 AndroidRuntime 异常和 Native 崩溃信号",
                style = TextStyle(fontSize = 13.sp, color = Color(0xFF8A93A3)),
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = toggleText,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
        }
    }
}

@Composable
private fun ActionButtonsSection(
    state: LogcatCollector.CollectorState,
    isConfigValid: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            LogcatCollector.CollectorState.IDLE, LogcatCollector.CollectorState.STOPPED, LogcatCollector.CollectorState.ERROR -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isConfigValid) Color(0xFF3566D6) else Color(0xFFD7DCE3))
                        .clickable(enabled = isConfigValid, onClick = onStart),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppText(
                            text = "开始收集",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                        )
                    }
                }
            }
            LogcatCollector.CollectorState.COLLECTING -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFC44536))
                        .clickable(onClick = onStop),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppText(
                            text = "结束收集",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                        )
                    }
                }
            }
            LogcatCollector.CollectorState.PAUSED -> {}
        }

        Box(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFD7DCE3), RoundedCornerShape(10.dp))
                .background(Color.White)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = "关闭",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF5B6472)),
            )
        }
    }
}

@Composable
private fun CollectionStatsPanel(
    entryCount: Int,
    crashCount: Int,
    deviceCounts: Map<String, Int>,
    startTime: Long,
) {
    val duration = System.currentTimeMillis() - startTime

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE6EEFF))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppText(
            text = "📊 实时收集状态",
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3566D6)),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(label = "总日志", value = "$entryCount 条")
            StatItem(label = "崩溃日志", value = "$crashCount 条")
            StatItem(label = "运行时间", value = formatDuration(duration))
        }

        if (deviceCounts.isNotEmpty()) {
            AppText(
                text = "设备日志分布",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1F2430)),
            )
            deviceCounts.forEach { (deviceId, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AppText(text = deviceId.take(12), style = TextStyle(fontSize = 13.sp, color = Color(0xFF5B6472)))
                    AppText(text = "$count 条", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppText(
            text = value,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3566D6)),
        )
        AppText(
            text = label,
            style = TextStyle(fontSize = 13.sp, color = Color(0xFF5B6472)),
        )
    }
}

@Composable
private fun CollectionResultPanel(result: LogcatCollector.CollectionResult) {
    val bgColor = if (result.success) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val iconColor = if (result.success) Color(0xFF4CAF50) else Color(0xFFC44536)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, iconColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(
                text = if (result.success) "✅" else "❌",
                style = TextStyle(fontSize = 24.sp),
            )
            AppText(
                text = if (result.success) "收集完成" else "收集失败",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = iconColor),
            )
        }

        result.error?.let { error ->
            AppText(
                text = "错误: $error",
                style = TextStyle(fontSize = 14.sp, color = iconColor),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ResultStat(label = "总条数", value = "${result.totalEntries}")
            ResultStat(label = "崩溃数", value = "${result.crashCount}")
            ResultStat(label = "耗时", value = formatDuration(result.duration))
        }

        AppText(
            text = "保存位置: ${result.outputDir}",
            style = TextStyle(fontSize = 13.sp, color = Color(0xFF5B6472)),
        )
    }
}

@Composable
private fun ResultStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppText(
            text = value,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2430)),
        )
        AppText(
            text = label,
            style = TextStyle(fontSize = 12.sp, color = Color(0xFF5B6472)),
        )
    }
}

@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: Dp = 0.dp,
    crossAxisSpacing: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val spacingX = mainAxisSpacing.roundToPx()
        val spacingY = crossAxisSpacing.roundToPx()

        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }

        var currentX = 0
        var currentY = 0
        var rowHeight = 0

        val positions = mutableListOf<Pair<Int, Int>>()

        placeables.forEach { placeable ->
            if (currentX + placeable.width > constraints.maxWidth && currentX > 0) {
                currentX = 0
                currentY += rowHeight + spacingY
                rowHeight = 0
            }

            positions.add(Pair(currentX, currentY))

            currentX += placeable.width + spacingX
            rowHeight = maxOf(rowHeight, placeable.height)
        }

        val totalHeight = if (placeables.isNotEmpty()) {
            currentY + rowHeight
        } else {
            0
        }

        layout(constraints.maxWidth, totalHeight.coerceAtMost(constraints.maxHeight)) {
            positions.forEachIndexed { index, (x, y) ->
                placeables[index].place(x, y)
            }
        }
    }
}