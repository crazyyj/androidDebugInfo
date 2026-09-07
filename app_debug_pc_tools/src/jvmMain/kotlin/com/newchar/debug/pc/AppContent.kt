package com.newchar.debug.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.newchar.debug.pc.config.AppSettings
import com.newchar.debug.pc.config.DesktopAppSettingsStore
import com.newchar.debug.pc.device.AdbCommon
import com.newchar.debug.pc.device.AdbDevices
import com.newchar.debug.pc.device.AppStoreLauncher
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.device.ConnectionAutoRecover
import com.newchar.debug.pc.device.HeartbeatManager
import com.newchar.debug.pc.device.PcMessage
import com.newchar.debug.pc.device.InstalledAppInfo
import com.newchar.debug.pc.device.LogcatManager
import com.newchar.debug.pc.device.PackageDisplayItem
import com.newchar.debug.pc.device.logcat.LogcatCollectorPanel
import com.newchar.debug.pc.device.PackageInspector
import com.newchar.debug.pc.device.PackageSnapshot
import com.newchar.debug.pc.device.PackageSnapshotStore
import com.newchar.debug.pc.device.PackageStatus
import com.newchar.debug.pc.device.buildPackageDisplayItems
import com.newchar.debug.pc.device.scan.AdbMdnsService
import com.newchar.debug.pc.device.scan.DeviceChangeEvent
import com.newchar.debug.pc.device.scan.DeviceChangeType
import com.newchar.debug.pc.device.scan.DeviceScanManager
import com.newchar.debug.pc.device.scan.JvmDeviceMetadataResolver
import com.newchar.debug.pc.device.scan.JvmLanDiscoveryAgent
import com.newchar.debug.pc.device.scan.JvmWifiDetector
import com.newchar.debug.pc.device.scan.WifiBanner
import com.newchar.debug.pc.device.scan.WifiBannerDetail
import com.newchar.debug.pc.device.preview.DevicePreviewWindow
import com.newchar.debug.pc.device.preview.DeviceProbeCache
import com.newchar.debug.pc.executor.AdbCommandExecutor
import com.newchar.debug.pc.ui.chooseAdbExecutable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

private object AppTheme {
    val background = Color(0xFFF5F6F8)
    val panel = Color(0xFFFFFFFF)
    val panelAlt = Color(0xFFF0F2F5)
    val border = Color(0xFFD7DCE3)
    val accent = Color(0xFF3566D6)
    val accentSoft = Color(0xFFE6EEFF)
    val textPrimary = Color(0xFF1F2430)
    val textSecondary = Color(0xFF5B6472)
    val textHint = Color(0xFF8A93A3)
    val danger = Color(0xFFC44536)
}

// 功能导航项枚举
enum class NavItem {
    DEVICES,    // 设备列表
    LOGCAT,     // 日志收集
}

private val TitleTextStyle = TextStyle(
    fontSize = 22.sp,
    fontWeight = FontWeight.SemiBold,
    color = AppTheme.textPrimary,
)

private val BodyTextStyle = TextStyle(
    fontSize = 14.sp,
    color = AppTheme.textPrimary,
)

private val HintTextStyle = TextStyle(
    fontSize = 13.sp,
    color = AppTheme.textHint,
)

// ============================================================
// 新增组件
// ============================================================

/** 功能导航项（图标 + 文字） */
@Composable
private fun FunctionNavItem(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AppTheme.accentSoft else AppTheme.panelAlt)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppText(icon, style = TextStyle(fontSize = 22.sp))
            AppText(
                label,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = if (selected) AppTheme.accent else AppTheme.textSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }
    }
}

/** 底部状态栏 */
@Composable
private fun StatusBar(
    adbStatusMessage: String?,
    deviceCount: Int,
    adbActivePath: String,
    modifier: Modifier = Modifier,
    onAddDevice: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .background(AppTheme.panel)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 添加设备按钮
        AppOutlinedButton(
            text = "添加设备",
            modifier = Modifier.height(28.dp),
            onClick = onAddDevice,
        )

        // 设备数量
        AppText(
            "设备: $deviceCount",
            style = TextStyle(fontSize = 12.sp, color = AppTheme.textSecondary),
        )

        // ADB 状态
        if (!adbStatusMessage.isNullOrBlank()) {
            AppText(
                "ADB: $adbStatusMessage",
                style = TextStyle(fontSize = 12.sp, color = AppTheme.danger),
            )
        } else {
            AppText(
                "ADB: 正常",
                style = TextStyle(fontSize = 12.sp, color = Color(0xFF2E7D32)),
            )
        }

        // ADB 路径
        AppText(
            "Path: ${adbActivePath.takeIf { it.isNotEmpty() } ?: "PATH/ENV"}",
            style = TextStyle(fontSize = 12.sp, color = AppTheme.textHint),
        )

        Spacer(modifier = Modifier.weight(1f))

        // 应用名称
        AppText(
            "PC Debug Tools",
            style = TextStyle(
                fontSize = 12.sp,
                color = AppTheme.accent,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

// ============================================================
// 设备信息展示组件（Tab 制表符分隔）
// ============================================================

/** 设备信息文本组件（使用 Tab 制表符分隔，支持鼠标选择复制） */
@Composable
private fun DeviceInfoText(
    device: DeviceInfo,
    modifier: Modifier = Modifier,
) {
    val infoText = buildString {
        appendLine("ID\t\t${device.id}")
        appendLine("Status\t\t${device.status.ifEmpty { "Unknown" }}")
        appendLine("Transport\t${if (device.isNetworkDevice) "Wi-Fi / TCP" else "USB"}")
        appendLine("Manufacturer\t${device.manufacturer.ifEmpty { "Unknown" }}")
        appendLine("Model\t\t${device.model.ifEmpty { "Unknown" }}")
        appendLine("Category\t${device.deviceCategory.ifEmpty { "Unknown" }}")
        appendLine("Product\t\t${device.product.ifEmpty { "Unknown" }}")
        appendLine("Device\t\t${device.device.ifEmpty { "Unknown" }}")
        if (device.usbInfo.isNotEmpty()) appendLine("USB\t\t${device.usbInfo}")
        if (device.transportId.isNotEmpty()) appendLine("Transport ID\t${device.transportId}")
        if (device.wirelessIp.isNotEmpty()) appendLine("Wireless IP\t${device.wirelessIp}")
        if (device.wirelessPort > 0) appendLine("ADB Port\t${device.wirelessPort}")
        if (device.wifiSsid.isNotEmpty()) appendLine("WiFi SSID\t${device.wifiSsid}")
    }

    PanelCard(modifier = modifier) {
        AppText(
            "设备信息 (可复制)",
            style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold),
        )
        BasicTextField(
            value = infoText,
            onValueChange = {},
            textStyle = TextStyle(
                fontSize = 13.sp,
                color = AppTheme.textPrimary,
                fontFamily = FontFamily.Monospace,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppTheme.panelAlt),
            enabled = false,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .background(AppTheme.panelAlt, RoundedCornerShape(8.dp)),
                ) {
                    innerTextField()
                }
            },
        )
    }
}

@Composable
fun AppContent(
    showSettingsDialog: Boolean = false,
    onDismissSettings: () -> Unit = {},
) {
    var selectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    val packageCache = remember { mutableStateMapOf<String, List<InstalledAppInfo>>() }
    var adbExecutablePath by remember { mutableStateOf("") }
    var settingsDraftPath by remember { mutableStateOf("") }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var manualDeviceHistory by remember { mutableStateOf(emptyList<String>()) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var adbStatusMessage by remember { mutableStateOf<String?>(null) }
    var previewAlwaysOnTopSetting by remember { mutableStateOf(true) }
    var wirelessPromptDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    val manuallyDisconnected = remember { mutableStateMapOf<String, DeviceInfo>() }
    var deleteConfirmDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var showWifiBannerDetail by remember { mutableStateOf(false) }
    var wifiBannerVisible by remember { mutableStateOf(true) }
    // 当前选中的功能导航项
    var currentNav by remember { mutableStateOf<NavItem>(NavItem.DEVICES) }
    // PC 通信消息列表
    val messages = remember { mutableStateListOf<PcMessage>() }

    val scope = rememberCoroutineScope()
    val settingsStore = remember { DesktopAppSettingsStore() }

    // 同步加载配置（避免 LaunchedEffect 竞态导致 settingsLoaded 永远为 false）
    val loadedSettings = remember {
        runCatching {
            val settings = settingsStore.loadSync()
            adbExecutablePath = settings.adbExecutablePath
            settingsDraftPath = settings.adbExecutablePath
            manualDeviceHistory = settings.manualDeviceHistory
            previewAlwaysOnTopSetting = settings.previewAlwaysOnTop
        }.onFailure {
            adbStatusMessage = "配置加载失败: ${it.message}"
        }.getOrDefault(AppSettings())
        true // settingsLoaded
    }

    // 定时刷新 WiFi 检测状态
    LaunchedEffect(Unit) {
        while (true) {
            JvmWifiDetector.refresh()
            kotlinx.coroutines.delay(10_000L)
        }
    }

    if (!loadedSettings) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.background),
            contentAlignment = Alignment.Center,
        ) {
            AppText("加载配置中...", style = BodyTextStyle.copy(color = AppTheme.textSecondary))
        }
        return
    }

    val executor = remember(adbExecutablePath) {
        AdbCommandExecutor.create(adbExecutablePath = adbExecutablePath.ifBlank { null })
    }
    val deviceProbeCache = remember { DeviceProbeCache() }
    val scanManager = remember(executor, scope) {
        AdbDevices.initExecutor(executor)
        AdbCommon.initExecutor(executor)
        LogcatManager.initExecutor(executor)
        DeviceScanManager(
            executor = executor,
            externalScope = scope,
            deviceMetadataResolver = JvmDeviceMetadataResolver(),
            lanDiscoveryAgent = JvmLanDiscoveryAgent(),
            knownEndpointProvider = { manualDeviceHistory },
        )
    }
    val effectiveAdbPath = remember(executor, adbExecutablePath) { executor.resolveAdbExecutablePath() }
    val scanState by scanManager.state.collectAsState()
    val scannedDevices = scanState.devices

    // USB 断线 → WiFi 自动重连
    val autoRecover = remember(executor, scanManager, scope) {
        ConnectionAutoRecover(
            executor = executor,
            externalScope = scope,
            scanManager = scanManager,
        )
    }
    LaunchedEffect(autoRecover) {
        autoRecover.start()
    }
    DisposableEffect(autoRecover) {
        onDispose { autoRecover.stop() }
    }

    // 心跳保活 + adb reverse
    val heartbeatManager = remember(executor, scanManager, scope) {
        HeartbeatManager(
            executor = executor,
            externalScope = scope,
            scanManager = scanManager,
            onHeartbeatTimeout = { device ->
                scope.launch { autoRecover.onHeartbeatTimeout(device.id) }
            },
        )
    }
    LaunchedEffect(heartbeatManager) {
        heartbeatManager.start()
        // 收集 PC 通信消息
        heartbeatManager.messages.collect { msg ->
            messages.add(msg)
            if (messages.size > 200) {
                messages.removeAt(0)
            }
        }
    }
    DisposableEffect(heartbeatManager) {
        onDispose { heartbeatManager.stop() }
    }

    val deviceList = remember(scannedDevices, manuallyDisconnected.toMap()) {
        val scannedIds = scannedDevices.mapTo(mutableSetOf()) { it.id }
        val retainedDisconnected = manuallyDisconnected.values
            .filter { it.id !in scannedIds }
            .map { it.copy(isManuallyDisconnected = true) }
        val updatedScanned = scannedDevices.map { device ->
            if (manuallyDisconnected.containsKey(device.id)) {
                device.copy(isManuallyDisconnected = true)
            } else {
                device
            }
        }
        updatedScanned + retainedDisconnected
    }

    // 设备连接后预热 screenrecord 原始帧能力；预览窗口打开时可直接命中本地缓存。
    LaunchedEffect(scannedDevices, executor, deviceProbeCache) {
        scannedDevices
            .filter { it.status == "device" && !it.isRetainedOffline }
            .forEach { device ->
                runCatching { deviceProbeCache.probeIfNeeded(executor, device.id) }
            }
    }

    val effectiveSelectedDevice = remember(selectedDevice, manuallyDisconnected.toMap()) {
        val dev = selectedDevice ?: return@remember null
        if (manuallyDisconnected.containsKey(dev.id)) {
            dev.copy(isManuallyDisconnected = true)
        } else {
            dev.copy(isManuallyDisconnected = false)
        }
    }

    LaunchedEffect(effectiveAdbPath) {
        val versionResult = executor.adb("version")
        adbStatusMessage = if (versionResult.isSuccess) {
            null
        } else {
            versionResult.error.ifBlank { versionResult.output.ifBlank { "ADB 不可用" } }
        }
    }

    LaunchedEffect(scanManager) {
        scanManager.start()
    }

    LaunchedEffect(scanManager.changeEvents) {
        scanManager.changeEvents.collect { event ->
            val actionText = when (event.type) {
                DeviceChangeType.ADDED -> "已连接"
                DeviceChangeType.REMOVED -> "已断开"
                DeviceChangeType.CHANGED -> if (event.device.isRetainedOffline) "USB 已断开" else "状态变化"
            }
            val name = event.device.model.ifBlank { event.device.id }
            toastMessage = "$name $actionText"
            if (event.type == DeviceChangeType.CHANGED && event.device.isRetainedOffline && event.device.canTryWirelessConnect) {
                wirelessPromptDevice = event.device
            }
        }
    }

    DisposableEffect(scanManager) {
        onDispose {
            scanManager.cancel()
        }
    }

    LaunchedEffect(deviceList, selectedDevice) {
        if (selectedDevice != null && deviceList.none { it.id == selectedDevice?.id }) {
            selectedDevice = null
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2500L)
            toastMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.background),
    ) {
        // WiFi 连接状态 Banner
        if (wifiBannerVisible && deviceList.isNotEmpty()) {
            WifiBanner(
                devices = deviceList,
                onToggleVisible = {
                    if (!showWifiBannerDetail) {
                        showWifiBannerDetail = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
            )
        }

        // 三栏布局：功能列表 | 设备列表 | 内容区
        Row(modifier = Modifier.fillMaxSize()) {
            // === 1. 功能列表列 (56dp) ===
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .background(AppTheme.panel)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 设备
                FunctionNavItem(
                    icon = "📱",
                    label = "设备",
                    selected = currentNav == NavItem.DEVICES,
                    onClick = {
                        currentNav = NavItem.DEVICES
                        scope.launch { scanManager.refreshNow() }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                // 日志
                FunctionNavItem(
                    icon = "📋",
                    label = "日志",
                    selected = currentNav == NavItem.LOGCAT,
                    onClick = { currentNav = NavItem.LOGCAT },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 列分隔线
            if (currentNav == NavItem.DEVICES) {
                AppDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = AppTheme.border)
                // === 2. 设备列表列 (260dp) ===
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(AppTheme.panel)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!adbStatusMessage.isNullOrBlank()) {
                        AppText(
                            text = adbStatusMessage.orEmpty(),
                            style = HintTextStyle.copy(color = AppTheme.danger),
                            maxLines = 3,
                        )
                    }
                    if (!scanState.lastError.isNullOrBlank()) {
                        AppText(
                            text = scanState.lastError.orEmpty(),
                            style = HintTextStyle.copy(color = AppTheme.danger),
                            maxLines = 2,
                        )
                    }

                    if (deviceList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppText("设备", style = TitleTextStyle.copy(fontSize = 28.sp, color = AppTheme.textHint))
                                AppText("未检测到设备", style = BodyTextStyle.copy(color = AppTheme.textSecondary))
                                AppText("连接 Android 设备并启用 USB 调试", style = HintTextStyle)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            state = rememberLazyListState(),
                        ) {
                            items(deviceList) { device ->
                                val isSelected = selectedDevice?.id == device.id
                                DeviceCard(
                                    device = device,
                                    selected = isSelected,
                                    onClick = { selectedDevice = device },
                                    onDisconnect = { deviceId ->
                                        scope.launch {
                                            val result = executor.adb("disconnect", deviceId)
                                            val deviceToRetain = deviceList.find { it.id == deviceId }
                                            if (deviceToRetain != null) {
                                                manuallyDisconnected[deviceId] = deviceToRetain
                                            }
                                            autoRecover.markManualDisconnect(deviceId)
                                            toastMessage = if (result.isSuccess) "已断开: $deviceId" else "断开失败: ${result.error.ifBlank { result.output }}"
                                            scanManager.refreshNow()
                                        }
                                    },
                                    onReconnect = { device ->
                                        scope.launch {
                                            val endpoint = if (device.isNetworkDevice) device.id else device.wirelessEndpoint
                                            if (endpoint.isNotBlank()) {
                                                val result = executor.adb("connect", endpoint)
                                                val output = result.output.ifBlank { result.error }
                                                val success = result.isSuccess || output.contains("connected to", ignoreCase = true) || output.contains("already connected to", ignoreCase = true)
                                                if (success) {
                                                    manuallyDisconnected.remove(device.id)
                                                    toastMessage = "已连接: $endpoint"
                                                    scanManager.refreshNow()
                                                } else {
                                                    toastMessage = "连接失败: ${output.ifBlank { endpoint }}"
                                                }
                                            } else {
                                                toastMessage = "无法重连：缺少连接端点"
                                            }
                                        }
                                    },
                                    onDelete = { deleteConfirmDevice = it },
                                )
                            }
                        }
                    }
                }
            }

            // 列分隔线
            AppDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = AppTheme.border)

            // === 3. 内容区 (flex:1) ===
            Box(modifier = Modifier.weight(1f)) {
                if (currentNav == NavItem.LOGCAT) {
                    // 日志面板（全屏高度）
                    LogcatCollectorPanel(
                        devices = deviceList,
                        onDismiss = { currentNav = NavItem.DEVICES },
                        executor = executor,
                    )
                } else {
                    // 设备详情或空状态
                    if (deviceList.isEmpty()) {
                        DeviceDetailPanel(
                            device = null,
                            packages = emptyList(),
                            packageLoading = false,
                            packageError = null,
                            recentChanges = scanState.recentChanges,
                            mdnsServices = scanState.mdnsServices,
                            recentMdnsMessages = scanState.recentMdnsMessages,
                            recentLanMessages = scanState.recentLanMessages,
                            scannedLanSubnets = scanState.scannedLanSubnets,
                            discoveredLanEndpoints = scanState.discoveredLanEndpoints,
                            messages = emptyList(),
                            onConnectWireless = {},
                            onEnableWirelessAdb = {},
                            onToast = { toastMessage = it },
                            scope = scope,
                            executor = executor,
                            packageCache = packageCache,
                            manualDeviceHistory = manualDeviceHistory,
                            settingsStore = settingsStore,
                            onShowAddDevice = { showAddDeviceDialog = true },
                            onDismissWirelessPrompt = { wirelessPromptDevice = null },
                        )
                    } else {
                        effectiveSelectedDevice?.let { dev ->
                            DeviceDetailPanel(
                                device = dev,
                                executor = executor,
                                isVisible = true,
                                recentChanges = scanState.recentChanges,
                                mdnsServices = scanState.mdnsServices,
                                recentMdnsMessages = scanState.recentMdnsMessages,
                                recentLanMessages = scanState.recentLanMessages,
                                scannedLanSubnets = scanState.scannedLanSubnets,
                                discoveredLanEndpoints = scanState.discoveredLanEndpoints,
                                messages = messages,
                                onClearMessages = { messages.clear() },
                                onConnectWireless = { device ->
                                    scope.launch {
                                        val endpoint = device.wirelessEndpoint
                                        if (endpoint.isBlank()) {
                                            toastMessage = "未获取到无线端口"
                                            return@launch
                                        }
                                        val result = executor.adb("connect", endpoint)
                                        val output = result.output.ifBlank { result.error }
                                        val success = result.isSuccess || output.contains("connected to", ignoreCase = true) || output.contains("already connected to", ignoreCase = true)
                                        toastMessage = if (success) "无线连接成功: $endpoint" else output.ifBlank { "无线连接失败: $endpoint" }
                                        if (success) {
                                            manualDeviceHistory = (listOf(endpoint) + manualDeviceHistory).map(String::trim).filter(String::isNotBlank).distinct().take(8)
                                            settingsStore.save(AppSettings(
                                                adbExecutablePath = adbExecutablePath,
                                                manualDeviceHistory = manualDeviceHistory,
                                                previewAlwaysOnTop = previewAlwaysOnTopSetting,
                                            ))
                                            scanManager.refreshNow()
                                        }
                                    }
                                },
                                onEnableWirelessAdb = { device ->
                                    scope.launch {
                                        val result = executor.adb("-s", device.id, "tcpip", "5555")
                                        val output = result.output.ifBlank { result.error }
                                        val success = result.isSuccess || output.contains("restarting in TCP mode port: 5555", ignoreCase = true) || output.contains("tcp mode port: 5555", ignoreCase = true)
                                        toastMessage = if (success) "${device.model.ifBlank { device.id }} 已切到 adb tcpip 5555" else output.ifBlank { "切换 adb tcpip 5555 失败" }
                                        if (success) {
                                            kotlinx.coroutines.delay(1200L)
                                            val endpoint = when {
                                                device.wirelessIp.isNotEmpty() -> "${device.wirelessIp}:5555"
                                                device.wirelessEndpoint.isNotEmpty() -> device.wirelessEndpoint
                                                else -> ""
                                            }
                                            if (endpoint.isNotBlank()) {
                                                val connectResult = executor.adb("connect", endpoint)
                                                val connectOutput = connectResult.output.ifBlank { connectResult.error }
                                                val connectSuccess = connectResult.isSuccess || connectOutput.contains("connected to", ignoreCase = true) || connectOutput.contains("already connected to", ignoreCase = true)
                                                toastMessage = if (connectSuccess) "无线连接成功: $endpoint" else connectOutput.ifBlank { "无线连接失败: $endpoint" }
                                            }
                                            scanManager.refreshNow()
                                        }
                                    }
                                },
                                onToast = { toastMessage = it },
                                lastContactAt = { heartbeatManager.lastContactAt(dev.id) },
                                onPreviewAlwaysOnTopChanged = { previewAlwaysOnTopSetting = it },
                                probeCache = deviceProbeCache,
                                scope = scope,
                                packageCache = packageCache,
                                manualDeviceHistory = manualDeviceHistory,
                                settingsStore = settingsStore,
                                onShowAddDevice = { showAddDeviceDialog = true },
                                onDismissWirelessPrompt = { wirelessPromptDevice = null },
                            )
                        } ?: DeviceDetailPanel(
                            device = null,
                            packages = emptyList(),
                            packageLoading = false,
                            packageError = null,
                            recentChanges = scanState.recentChanges,
                            mdnsServices = scanState.mdnsServices,
                            recentMdnsMessages = scanState.recentMdnsMessages,
                            recentLanMessages = scanState.recentLanMessages,
                            scannedLanSubnets = scanState.scannedLanSubnets,
                            discoveredLanEndpoints = scanState.discoveredLanEndpoints,
                            messages = emptyList(),
                            onConnectWireless = {},
                            onEnableWirelessAdb = {},
                            onToast = { toastMessage = it },
                            scope = scope,
                            executor = executor,
                            packageCache = packageCache,
                            manualDeviceHistory = manualDeviceHistory,
                            settingsStore = settingsStore,
                            onShowAddDevice = { showAddDeviceDialog = true },
                            onDismissWirelessPrompt = { wirelessPromptDevice = null },
                        )
                    }
                }
            }
        }

        // === 底部状态栏 ===
        StatusBar(
            adbStatusMessage = adbStatusMessage,
            deviceCount = deviceList.size,
            adbActivePath = effectiveAdbPath,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            onAddDevice = { showAddDeviceDialog = true },
        )

        toastMessage?.let { message ->
            ToastHint(
                message = message,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp),
            )
        }
    }

    if (showAddDeviceDialog) {
        AddDeviceDialog(
            history = manualDeviceHistory,
            onDismiss = { showAddDeviceDialog = false },
            onConnect = { endpoint ->
                val normalizedEndpoint = endpoint.trim().let { if (":" in it) it else "$it:5555" }
                val result = executor.adb("connect", normalizedEndpoint)
                val output = result.output.ifBlank { result.error }
                val success = result.isSuccess || output.contains("connected to", ignoreCase = true) || output.contains("already connected to", ignoreCase = true)
                if (success) {
                    manualDeviceHistory = (listOf(normalizedEndpoint) + manualDeviceHistory)
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .distinct()
                        .take(8)
                    settingsStore.save(
                        AppSettings(
                            adbExecutablePath = adbExecutablePath,
                            manualDeviceHistory = manualDeviceHistory,
                            previewAlwaysOnTop = previewAlwaysOnTopSetting,
                        )
                    )
                    scanManager.refreshNow()
                }
                ManualConnectResult(success = success, message = if (success) "连接成功: $normalizedEndpoint" else output.ifBlank { "连接失败: $normalizedEndpoint" })
            },
            onToast = { message -> toastMessage = message },
        )
    }

    wirelessPromptDevice?.let { device ->
        WirelessReconnectDialog(
            device = device,
            onDismiss = { wirelessPromptDevice = null },
            onConfirm = {
                scope.launch {
                    val endpoint = device.wirelessEndpoint
                    if (endpoint.isBlank()) {
                        toastMessage = "未获取到无线端口"
                        wirelessPromptDevice = null
                        return@launch
                    }
                    val result = executor.adb("connect", endpoint)
                    val output = result.output.ifBlank { result.error }
                    val success = result.isSuccess ||
                        output.contains("connected to", ignoreCase = true) ||
                        output.contains("already connected to", ignoreCase = true)
                    toastMessage = if (success) "无线连接成功: $endpoint" else output.ifBlank { "无线连接失败: $endpoint" }
                    if (success) {
                        scanManager.refreshNow()
                    }
                    wirelessPromptDevice = null
                }
            },
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentAdbPath = settingsDraftPath.ifBlank { adbExecutablePath },
            effectiveAdbPath = effectiveAdbPath,
            onDismiss = onDismissSettings,
            onSave = { path ->
                val normalizedPath = path.trim()
                settingsDraftPath = normalizedPath
                adbExecutablePath = normalizedPath
                scope.launch {
                    settingsStore.save(
                        AppSettings(
                            adbExecutablePath = normalizedPath,
                            manualDeviceHistory = manualDeviceHistory,
                            previewAlwaysOnTop = previewAlwaysOnTopSetting,
                        )
                    )
                }
                onDismissSettings()
            },
        )
    }

    // Logcat 日志收集已移至主界面功能导航栏

    // WiFi 连接状态详情面板
    if (showWifiBannerDetail) {
        WifiBannerDetail(
            devices = deviceList,
            onDismiss = { showWifiBannerDetail = false },
        )
    }

    deleteConfirmDevice?.let { device ->
        AppDialog(
            title = "删除设备",
            onDismiss = { deleteConfirmDevice = null },
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                scope.launch {
                    if (!manuallyDisconnected.containsKey(device.id) && device.status == "device") {
                        executor.adb("disconnect", device.id)
                    }
                    manuallyDisconnected.remove(device.id)
                    if (selectedDevice?.id == device.id) {
                        selectedDevice = null
                    }
                    deleteConfirmDevice = null
                    toastMessage = "已删除: ${device.model.ifBlank { device.id }}"
                    scanManager.refreshNow()
                }
            },
            onDismissButton = { deleteConfirmDevice = null },
        ) {
            AppText(
                "确定删除设备「${device.model.ifBlank { device.id }}」？",
                style = BodyTextStyle.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(8.dp))
            AppText("删除后将从列表中移除此设备。如果设备仍在线，下次扫描会重新发现。", style = HintTextStyle)
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceInfo,
    selected: Boolean,
    onClick: () -> Unit,
    onDisconnect: (String) -> Unit,
    onReconnect: (DeviceInfo) -> Unit,
    onDelete: (DeviceInfo) -> Unit,
) {
    val manuallyDisconnected = device.isManuallyDisconnected
    val bgColor = when {
        manuallyDisconnected -> Color(0xFFE8E8E8)
        selected -> AppTheme.accentSoft
        device.isRetainedOffline -> Color(0xFFF7F2E8)
        else -> AppTheme.panel
    }
    val borderColor = when {
        manuallyDisconnected -> Color(0xFFBDBDBD)
        selected -> AppTheme.accent
        device.isRetainedOffline -> Color(0xFFD2B48C)
        else -> AppTheme.border
    }
    val contentAlpha = if (manuallyDisconnected) 0.45f else 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).graphicsLayer { alpha = contentAlpha }) {
                    AppText(
                        text = device.model.ifEmpty { device.id },
                        style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (manuallyDisconnected) {
                        AppSmallButton(
                            text = "连接",
                            onClick = { onReconnect(device) },
                        )
                    } else if (device.status == "device" && !device.isRetainedOffline) {
                        AppSmallOutlinedButton(
                            text = "断开",
                            onClick = { onDisconnect(device.id) },
                        )
                    }
                    AppSmallOutlinedButton(
                        text = "✕",
                        onClick = { onDelete(device) },
                        textColor = AppTheme.danger,
                        borderColor = AppTheme.danger.copy(alpha = 0.4f),
                    )
                }
            }
            Column(modifier = Modifier.graphicsLayer { alpha = contentAlpha }) {
                AppText(
                    text = "ID: ${device.id}",
                    style = HintTextStyle.copy(color = AppTheme.textSecondary),
                    maxLines = 1,
                )
                AppText(
                    text = if (manuallyDisconnected) "State: 已手动断开"
                    else if (device.isNetworkDevice) "State: ${device.status} · Wi-Fi"
                    else "State: ${device.status} · USB",
                    style = HintTextStyle,
                    maxLines = 1,
                )
                if (device.isRetainedOffline && device.wirelessEndpoint.isNotBlank()) {
                    AppText(
                        text = "Wireless: ${device.wirelessEndpoint}",
                        style = HintTextStyle.copy(color = AppTheme.accent),
                        maxLines = 1,
                    )
                }
                if (device.product.isNotEmpty()) {
                    AppText(
                        text = "Product: ${device.product}",
                        style = HintTextStyle.copy(color = AppTheme.accent),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceDetailPanel(
    modifier: Modifier = Modifier,
    device: DeviceInfo?,
    packages: List<InstalledAppInfo> = emptyList(),
    packageLoading: Boolean = false,
    packageError: String? = null,
    executor: AdbCommandExecutor = AdbCommandExecutor.create(null),
    packageCache: SnapshotStateMap<String, List<InstalledAppInfo>> = mutableStateMapOf(),
    isVisible: Boolean = true,
    recentChanges: List<DeviceChangeEvent>,
    mdnsServices: List<AdbMdnsService>,
    recentMdnsMessages: List<String>,
    recentLanMessages: List<String>,
    scannedLanSubnets: List<String>,
    discoveredLanEndpoints: List<String>,
    messages: List<PcMessage> = emptyList(),
    onClearMessages: () -> Unit = {},
    onConnectWireless: (DeviceInfo) -> Unit,
    onEnableWirelessAdb: (DeviceInfo) -> Unit,
    onToast: (String) -> Unit = {},
    lastContactAt: () -> Long = { 0L },
    onPreviewAlwaysOnTopChanged: (Boolean) -> Unit = {},
    probeCache: DeviceProbeCache = DeviceProbeCache(),
    scope: CoroutineScope,
    manualDeviceHistory: List<String> = emptyList(),
    settingsStore: DesktopAppSettingsStore,
    onShowAddDevice: () -> Unit = {},
    onDismissWirelessPrompt: () -> Unit = {},
) {
    val localPackageList = remember { mutableStateOf(emptyList<InstalledAppInfo>()) }
    val localPackageDisplayItems = remember { mutableStateOf(emptyList<PackageDisplayItem>()) }
    val localPackageLoading = remember { mutableStateOf(false) }
    val localPackageError = remember { mutableStateOf<String?>(null) }
    val packageSnapshotStore = remember { PackageSnapshotStore() }
    var packageSnapshotAt by remember { mutableStateOf<Long?>(null) }
    var showMessagesPanel by remember { mutableStateOf(false) }
    var showDevicePreview by remember(device?.id) { mutableStateOf(false) }

    val effectiveDevice = device
    val effectivePackageItems = if (device != null && executor != AdbCommandExecutor.create(null)) {
        localPackageDisplayItems.value
    } else {
        buildPackageDisplayItems(null, packages)
    }
    val effectiveLoading = if (device != null && executor != AdbCommandExecutor.create(null)) localPackageLoading.value else packageLoading
    val effectiveError = if (device != null && executor != AdbCommandExecutor.create(null)) localPackageError.value else packageError

    LaunchedEffect(effectiveDevice?.id, effectiveDevice?.status, effectiveDevice?.isManuallyDisconnected, effectiveDevice?.isRetainedOffline, isVisible) {
        val dev = effectiveDevice ?: return@LaunchedEffect
        if (!isVisible) return@LaunchedEffect
        if (dev.isManuallyDisconnected || dev.isRetainedOffline || dev.status != "device") {
            val snapshot = packageSnapshotStore.load(dev.id)
            val offlineApps = packageCache[dev.id].orEmpty().ifEmpty { snapshot?.apps.orEmpty() }
            localPackageList.value = offlineApps
            localPackageDisplayItems.value = buildPackageDisplayItems(null, offlineApps)
            packageSnapshotAt = snapshot?.capturedAt
            localPackageLoading.value = false
            localPackageError.value = if (offlineApps.isEmpty()) "当前设备不在线，且没有可用的应用快照。" else null
            return@LaunchedEffect
        }
        localPackageList.value = packageCache[dev.id].orEmpty()
        localPackageDisplayItems.value = buildPackageDisplayItems(null, localPackageList.value)
        localPackageLoading.value = true
        localPackageError.value = null
        val previousSnapshot = packageSnapshotStore.load(dev.id)
        try {
            val apps = PackageInspector.loadInstalledApps(executor, dev.id)
            localPackageList.value = apps
            localPackageDisplayItems.value = buildPackageDisplayItems(previousSnapshot?.apps, apps)
            packageCache[dev.id] = apps
            val capturedAt = System.currentTimeMillis()
            packageSnapshotAt = capturedAt
            packageSnapshotStore.save(PackageSnapshot(dev.id, capturedAt, apps))
            localPackageLoading.value = false
        } catch (throwable: Throwable) {
            val offlineApps = previousSnapshot?.apps.orEmpty()
            if (offlineApps.isNotEmpty()) {
                localPackageList.value = offlineApps
                localPackageDisplayItems.value = buildPackageDisplayItems(null, offlineApps)
                packageSnapshotAt = previousSnapshot?.capturedAt
            }
            localPackageLoading.value = false
            localPackageError.value = throwable.message ?: "获取应用列表失败，已展示本地快照"
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.background)
            .padding(24.dp),
        contentAlignment = if (device == null) Alignment.Center else Alignment.TopStart,
    ) {
        if (device == null) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AppText("设备", style = TitleTextStyle.copy(fontSize = 48.sp, color = AppTheme.textHint))
                Spacer(Modifier.height(16.dp))
                AppText(
                    "选择一个设备查看详情",
                    style = BodyTextStyle.copy(color = AppTheme.textSecondary),
                )
                ScanSummaryPanel(
                    recentChanges = recentChanges,
                    mdnsServices = mdnsServices,
                    recentMdnsMessages = recentMdnsMessages,
                    recentLanMessages = recentLanMessages,
                    scannedLanSubnets = scannedLanSubnets,
                    discoveredLanEndpoints = discoveredLanEndpoints,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText("设备详情", style = TitleTextStyle)

                // 设备信息（Tab 制表符分隔，可复制）
                DeviceInfoText(device = device, modifier = Modifier.fillMaxWidth())

                // 操作按钮
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppOutlinedButton(
                        text = "预览",
                        prefix = "▣",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (device.status == "device" && !device.isRetainedOffline && !device.isManuallyDisconnected) {
                                showDevicePreview = true
                            } else {
                                onToast("设备未在线，无法打开预览")
                            }
                        },
                    )
                    AppOutlinedButton(
                        text = "Logcat",
                        prefix = ">_",
                        modifier = Modifier.weight(1f),
                        onClick = { onToast("Logcat 功能开发中") },
                    )

                    AppOutlinedButton(
                        text = "文件",
                        prefix = "[]",
                        modifier = Modifier.weight(1f),
                        onClick = { onToast("文件管理功能开发中") },
                    )
                }

                AppOutlinedButton(
                    text = "APK 信息查询",
                    prefix = "i",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onToast("APK 信息查询功能开发中") },
                )

                if (!device.isNetworkDevice && !device.isRetainedOffline && !device.isManuallyDisconnected && device.status == "device") {
                    AppOutlinedButton(
                        text = "开启 adb tcpip 5555",
                        prefix = "TCP",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onEnableWirelessAdb(device) },
                    )
                }

                if (device.canTryWirelessConnect) {
                    AppOutlinedButton(
                        text = if (device.isRetainedOffline) "尝试无线连接" else "连接无线端口",
                        prefix = "Wi",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onConnectWireless(device) },
                    )
                }

                // 通信消息面板（PC 通信）
                if (messages.isNotEmpty()) {
                    PanelCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                "📲 PC 通信消息 (${messages.size})",
                                style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold),
                            )
                            AppSmallOutlinedButton(
                                text = "清空",
                                onClick = onClearMessages,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            state = rememberLazyListState(),
                        ) {
                            items(messages.takeLast(20)) { msg ->
                                val deviceName = device.id.takeIf { it == msg.deviceId }?.let {
                                    device.model.ifEmpty { it }
                                } ?: msg.deviceId.take(8)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AppTheme.accentSoft),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AppText(
                                            deviceName.firstOrNull()?.toString() ?: "?",
                                            style = TextStyle(
                                                fontSize = 10.sp,
                                                color = AppTheme.accent,
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                        )
                                    }
                                    AppText(
                                        deviceName,
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            color = AppTheme.textSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        modifier = Modifier.width(100.dp),
                                        maxLines = 1,
                                    )
                                    AppText(
                                        msg.content,
                                        style = TextStyle(fontSize = 12.sp, color = AppTheme.textPrimary),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                    )
                                    AppText(
                                        formatTimestamp(msg.timestamp),
                                        style = TextStyle(fontSize = 10.sp, color = AppTheme.textHint),
                                    )
                                }
                            }
                        }
                    }
                }

                // 应用列表（完整可滚动，包含新增与已卸载状态）
                PackageListPanel(
                    items = effectivePackageItems,
                    loading = effectiveLoading,
                    error = effectiveError,
                    snapshotAt = packageSnapshotAt,
                    onReinstall = { item ->
                        scope.launch {
                            restoreUninstalledPackage(device, item.info, executor, onToast)
                        }
                    },
                )

                // 扫描摘要
                ScanSummaryPanel(
                    recentChanges = recentChanges,
                    mdnsServices = mdnsServices,
                    recentMdnsMessages = recentMdnsMessages,
                    recentLanMessages = recentLanMessages,
                    scannedLanSubnets = scannedLanSubnets,
                    discoveredLanEndpoints = discoveredLanEndpoints,
                )
            }
        }
    }
    if (showDevicePreview && device != null) {
        DevicePreviewWindow(
            device = device,
            executor = executor,
            probeCache = probeCache,
            lastContactAt = lastContactAt,
            onAlwaysOnTopChanged = onPreviewAlwaysOnTopChanged,
            onCloseRequest = { showDevicePreview = false },
        )
    }
}

/** 格式化时间戳为 mm:ss 格式 */
private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    return java.text.SimpleDateFormat("mm:ss", java.util.Locale.getDefault()).format(date)
}

/** 使用系统应用恢复或品牌应用商店详情页，处理历史已卸载应用。 */
private suspend fun restoreUninstalledPackage(
    device: DeviceInfo,
    app: InstalledAppInfo,
    executor: AdbCommandExecutor,
    onToast: (String) -> Unit,
) {
    if (device.status != "device" || device.isRetainedOffline || device.isManuallyDisconnected) {
        onToast("设备未在线，无法恢复应用")
        return
    }
    if (app.isSystemApp) {
        val result = executor.shell(device.id, "pm", "enable", app.packageName)
        onToast(if (result.isSuccess) "已请求恢复系统应用：${app.packageName}" else "恢复系统应用失败：${result.error.ifBlank { result.output }}")
        return
    }
    val manufacturer = device.manufacturer.ifBlank {
        executor.shell(device.id, "getprop", "ro.product.manufacturer").output.trim()
    }
    val target = AppStoreLauncher.buildLaunchTarget(manufacturer, app.packageName)
    onToast("正在跳转${target.brandName}应用商店")
    val result = executor.shell(device.id, *target.command.toTypedArray())
    val output = result.output.ifBlank { result.error }
    onToast(if (isStoreLaunchSuccessful(result.isSuccess, output)) "已打开${target.brandName}应用详情" else "${target.brandName}应用商店不可用：$output")
}

/** 判断 am start 是否真正启动成功，兼容部分设备以 0 返回 Error 文本的情况。 */
private fun isStoreLaunchSuccessful(isSuccess: Boolean, output: String): Boolean =
    isSuccess && !output.contains("error", ignoreCase = true) && !output.contains("not found", ignoreCase = true)

@Composable
private fun PackageListPanel(
    items: List<PackageDisplayItem>,
    loading: Boolean,
    error: String?,
    snapshotAt: Long?,
    onReinstall: (PackageDisplayItem) -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        AppText("应用列表", style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
        AppText(
            "排序: Debug > 非系统应用 > 系统应用，已过滤 android/com.android",
            style = HintTextStyle,
        )
        when {
            loading -> AppText("应用信息加载中...", style = HintTextStyle.copy(color = AppTheme.accent))
            !error.isNullOrBlank() -> AppText(error, style = HintTextStyle.copy(color = AppTheme.danger))
            items.isEmpty() -> AppText("暂无可展示应用", style = HintTextStyle)
            else -> {
                AppText("共 ${items.size} 个应用", style = HintTextStyle.copy(color = AppTheme.textSecondary))
                snapshotAt?.let { capturedAt ->
                    AppText("快照时间：${formatFullTimestamp(capturedAt)}", style = HintTextStyle)
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = items, key = { it.info.packageName }) { item ->
                        PackageRow(item = item, onReinstall = { onReinstall(item) })
                    }
                }
            }
        }
    }
}

/** 格式化应用快照的完整时间。 */
private fun formatFullTimestamp(timestamp: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

@Composable
private fun PackageRow(
    item: PackageDisplayItem,
    onReinstall: () -> Unit,
) {
    val app = item.info
    val isUninstalled = item.status == PackageStatus.UNINSTALLED
    val badgeText = when {
        app.isDebuggable -> "Debug"
        app.isSystemApp -> "System"
        else -> "App"
    }
    val badgeColor = when {
        app.isDebuggable -> AppTheme.accent
        app.isSystemApp -> AppTheme.textHint
        else -> Color(0xFF2E8B57)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isUninstalled) 0.45f else 1f }
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(text = app.packageName, modifier = Modifier.weight(1f), style = BodyTextStyle.copy(fontWeight = FontWeight.Medium), maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (item.status) {
                    PackageStatus.UNINSTALLED -> OutlinedUninstalledLabel()
                    PackageStatus.NEW_INSTALLED -> AppText("新增", style = HintTextStyle.copy(color = Color(0xFF2E8B57), fontWeight = FontWeight.SemiBold))
                    PackageStatus.CURRENT -> Unit
                }
                AppText(text = badgeText, style = HintTextStyle.copy(color = badgeColor, fontWeight = FontWeight.SemiBold))
            }
        }
        val meta = buildString {
            append("version=")
            append(app.versionName.ifBlank { "-" })
            if (app.versionCode.isNotBlank()) {
                append(" (")
                append(app.versionCode)
                append(')')
            }
            if (app.uid.isNotBlank()) {
                append(" · uid=")
                append(app.uid)
            }
        }
        AppText(meta, style = HintTextStyle.copy(color = AppTheme.textSecondary), maxLines = 1)
        if (app.apkPath.isNotBlank()) {
            AppText(app.apkPath, style = HintTextStyle, maxLines = 1)
        }
        if (isUninstalled) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSmallOutlinedButton(text = "重新安装", onClick = onReinstall)
            }
        }
    }
}

/**
 * 通过八方向偏移叠加模拟文字描边，避免依赖 Compose 版本相关的 Stroke API。
 */
@Composable
private fun OutlinedUninstalledLabel() {
    val outlineOffsets = listOf(
        -1 to -1, 0 to -1, 1 to -1,
        -1 to 0, 1 to 0,
        -1 to 1, 0 to 1, 1 to 1,
    )
    val textStyle = HintTextStyle.copy(fontWeight = FontWeight.SemiBold)
    Box {
        outlineOffsets.forEach { (x, y) ->
            BasicText(
                "已卸载",
                modifier = Modifier.offset(x.dp, y.dp),
                style = textStyle.copy(color = AppTheme.danger),
            )
        }
        BasicText("已卸载", style = textStyle.copy(color = AppTheme.panel))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = label,
            modifier = Modifier.width(120.dp),
            style = BodyTextStyle.copy(color = AppTheme.textSecondary),
        )
        AppText(
            text = value,
            style = BodyTextStyle.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun AddDeviceDialog(
    history: List<String>,
    onDismiss: () -> Unit,
    onConnect: suspend (String) -> ManualConnectResult,
    onToast: (String) -> Unit,
) {
    var endpoint by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AppDialog(
        title = "添加设备",
        onDismiss = onDismiss,
        confirmText = "连接",
        dismissText = "取消",
        onConfirm = {
            if (endpoint.isNotBlank() && !connecting) {
                scope.launch {
                    connecting = true
                    val result = onConnect(endpoint)
                    connecting = false
                    onToast(result.message)
                    if (result.success) {
                        onDismiss()
                    }
                }
            }
        },
        onDismissButton = onDismiss,
    ) {
        AppText("输入局域网设备 IP 或 ip:port", style = BodyTextStyle.copy(fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(10.dp))
        AppTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            placeholder = "192.168.1.23 或 192.168.1.23:5555",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        AppText("未填端口时默认使用 5555。", style = HintTextStyle)
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            AppText("历史记录", style = BodyTextStyle.copy(fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(8.dp))
            history.take(5).forEach { item ->
                AppOutlinedButton(
                    text = item,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { endpoint = item },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        if (connecting) {
            AppText("连接中...", style = HintTextStyle.copy(color = AppTheme.accent))
        }
    }
}

@Composable
private fun WirelessReconnectDialog(
    device: DeviceInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppDialog(
        title = "无线连接",
        onDismiss = onDismiss,
        confirmText = "连接",
        dismissText = "取消",
        onConfirm = onConfirm,
        onDismissButton = onDismiss,
    ) {
        AppText(
            "${device.model.ifBlank { device.id }} 的 USB 已断开，是否尝试无线连接？",
            style = BodyTextStyle.copy(fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(10.dp))
        if (device.wirelessIp.isNotBlank()) {
            AppText("IP: ${device.wirelessIp}", style = BodyTextStyle)
        }
        if (device.wirelessPort > 0) {
            AppText("端口: ${device.wirelessPort}", style = BodyTextStyle)
        }
        AppText(
            "Endpoint: ${device.wirelessEndpoint.ifBlank { "未获取到" }}",
            style = HintTextStyle,
        )
    }
}

private data class ManualConnectResult(
    val success: Boolean,
    val message: String,
)

@Composable
private fun ToastHint(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xEE1F2430))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        AppText(
            text = message,
            style = BodyTextStyle.copy(color = Color.White, fontWeight = FontWeight.Medium),
            maxLines = 2,
        )
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AppDialog(
        title = "关于 阿牛群控",
        onDismiss = onDismiss,
        confirmText = "确定",
        onConfirm = onDismiss,
    ) {
        AppText("版本: 1.0.0", style = BodyTextStyle)
        Spacer(Modifier.height(6.dp))
        AppText("用于 Android 设备调试的桌面工具", style = BodyTextStyle)
        Spacer(Modifier.height(8.dp))
        AppText(
            "支持 Windows 和 macOS 平台\n使用 Kotlin Multiplatform + Compose Desktop 构建",
            style = HintTextStyle,
        )
    }
}

@Composable
private fun SettingsDialog(
    currentAdbPath: String,
    effectiveAdbPath: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var adbPath by remember(currentAdbPath) { mutableStateOf(currentAdbPath) }
    AppDialog(
        title = "设置",
        onDismiss = onDismiss,
        confirmText = "保存",
        dismissText = "取消",
        onConfirm = { onSave(adbPath) },
        onDismissButton = onDismiss,
    ) {
        AppText("ADB 路径配置", style = BodyTextStyle.copy(fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(10.dp))
        AppTextField(
            value = adbPath,
            onValueChange = { adbPath = it },
            placeholder = "/usr/local/bin/adb 或 adb.exe",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppOutlinedButton(
                text = "选择 ADB",
                onClick = {
                    chooseAdbExecutable(adbPath.ifBlank { currentAdbPath })?.let { adbPath = it }
                },
            )
            AppOutlinedButton(
                text = "清空配置",
                onClick = { adbPath = "" },
            )
        }
        Spacer(Modifier.height(16.dp))
        AppText("当前生效: $effectiveAdbPath", style = HintTextStyle)
        AppText("优先使用配置路径；配置失效时自动回退到环境变量中的 adb。", style = HintTextStyle)
        Spacer(Modifier.height(16.dp))
        AppDivider()
        Spacer(Modifier.height(8.dp))
        AppText("关于", style = BodyTextStyle.copy(fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(6.dp))
        AppText("版本: 1.0.0", style = BodyTextStyle)
        AppText("用于 Android 设备调试的桌面工具", style = BodyTextStyle)
        AppText(
            "支持 Windows 和 macOS 平台，使用 Kotlin Multiplatform + Compose Desktop 构建",
            style = HintTextStyle,
        )
    }
}

@Composable
private fun ScanSummaryPanel(
    recentChanges: List<DeviceChangeEvent>,
    mdnsServices: List<AdbMdnsService>,
    recentMdnsMessages: List<String>,
    recentLanMessages: List<String>,
    scannedLanSubnets: List<String>,
    discoveredLanEndpoints: List<String>,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        AppText("扫描事件", style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
        if (recentChanges.isEmpty()) {
            AppText("暂无设备变化", style = HintTextStyle)
        } else {
            recentChanges.take(5).forEach { event ->
                AppText(
                    text = "${event.type.name} · ${event.device.id} · ${event.source.name}",
                    style = HintTextStyle.copy(color = AppTheme.textSecondary),
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        AppText("mDNS 服务", style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
        if (mdnsServices.isEmpty()) {
            AppText("暂无发现", style = HintTextStyle)
        } else {
            mdnsServices.take(3).forEach { service ->
                AppText(
                    text = "${service.endpoint} · ${service.serviceType}",
                    style = HintTextStyle.copy(color = AppTheme.textSecondary),
                    maxLines = 1,
                )
            }
        }

        if (recentMdnsMessages.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            AppText("mDNS 连接", style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
            recentMdnsMessages.take(3).forEach { message ->
                AppText(message, style = HintTextStyle.copy(color = AppTheme.textSecondary), maxLines = 2)
            }
        }

        Spacer(Modifier.height(8.dp))
        AppText("LAN 扫描", style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
        if (scannedLanSubnets.isEmpty()) {
            AppText("暂无子网扫描记录", style = HintTextStyle)
        } else {
            AppText(
                text = scannedLanSubnets.joinToString(limit = 3, truncated = " ..."),
                style = HintTextStyle.copy(color = AppTheme.textSecondary),
                maxLines = 2,
            )
        }
        if (discoveredLanEndpoints.isNotEmpty()) {
            discoveredLanEndpoints.take(3).forEach { endpoint ->
                AppText(endpoint, style = HintTextStyle.copy(color = AppTheme.textSecondary), maxLines = 1)
            }
        }
        if (recentLanMessages.isNotEmpty()) {
            recentLanMessages.take(3).forEach { message ->
                AppText(message, style = HintTextStyle.copy(color = AppTheme.textSecondary), maxLines = 2)
            }
        }
    }
}

@Composable
private fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismissButton: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onCloseRequest = onDismiss, title = title) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppTheme.panel)
                .border(1.dp, AppTheme.border, RoundedCornerShape(18.dp))
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AppText(title, style = TitleTextStyle.copy(fontSize = 18.sp))
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (dismissText != null && onDismissButton != null) {
                        AppOutlinedButton(text = dismissText, onClick = onDismissButton)
                        Spacer(Modifier.width(10.dp))
                    }
                    AppButton(text = confirmText, onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.panel)
            .border(1.dp, AppTheme.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun AppButton(
    text: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    onClick: () -> Unit,
) {
    AppButtonBase(
        text = text,
        modifier = modifier,
        prefix = prefix,
        onClick = onClick,
        background = AppTheme.accent,
        textColor = Color.White,
        borderColor = AppTheme.accent,
    )
}

@Composable
private fun AppOutlinedButton(
    text: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    onClick: () -> Unit,
) {
    AppButtonBase(
        text = text,
        modifier = modifier,
        prefix = prefix,
        onClick = onClick,
        background = AppTheme.panel,
        textColor = AppTheme.textPrimary,
        borderColor = AppTheme.border,
    )
}

@Composable
private fun AppSmallButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppSmallButtonBase(
        text = text,
        modifier = modifier,
        onClick = onClick,
        background = AppTheme.accent,
        textColor = Color.White,
        borderColor = AppTheme.accent,
    )
}

@Composable
private fun AppSmallOutlinedButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    textColor: Color = AppTheme.textPrimary,
    borderColor: Color = AppTheme.border,
) {
    AppSmallButtonBase(
        text = text,
        modifier = modifier,
        onClick = onClick,
        background = AppTheme.panel,
        textColor = textColor,
        borderColor = borderColor,
    )
}

@Composable
private fun AppButtonBase(
    text: String,
    modifier: Modifier,
    prefix: String?,
    onClick: () -> Unit,
    background: Color,
    textColor: Color,
    borderColor: Color,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!prefix.isNullOrBlank()) {
            AppText(prefix, style = BodyTextStyle.copy(color = textColor, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.width(6.dp))
        }
        AppText(text, style = BodyTextStyle.copy(color = textColor, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun AppSmallButtonBase(
    text: String,
    modifier: Modifier,
    onClick: () -> Unit,
    background: Color,
    textColor: Color,
    borderColor: Color,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(text, style = TextStyle(fontSize = 12.sp, color = textColor, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.panelAlt)
            .border(1.dp, AppTheme.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = BodyTextStyle,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    AppText(placeholder, style = HintTextStyle)
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun AppDivider(
    modifier: Modifier = Modifier.fillMaxWidth().height(1.dp),
    color: Color = AppTheme.border,
) {
    Box(modifier = modifier.background(color))
}

@Composable
private fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = BodyTextStyle,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun AppMenuBar(
    onShowAbout: () -> Unit,
    onShowSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.panel)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = "PC Debug Tools",
            style = TitleTextStyle.copy(fontSize = 18.sp, color = AppTheme.accent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppOutlinedButton(text = "设置", onClick = onShowSettings)
            AppOutlinedButton(text = "关于", onClick = onShowAbout)
        }
    }
}
