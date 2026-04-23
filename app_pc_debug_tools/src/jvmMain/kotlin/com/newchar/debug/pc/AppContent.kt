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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.newchar.debug.pc.config.AppSettings
import com.newchar.debug.pc.config.DesktopAppSettingsStore
import com.newchar.debug.pc.device.AdbCommon
import com.newchar.debug.pc.device.AdbDevices
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.device.InstalledAppInfo
import com.newchar.debug.pc.device.LogcatManager
import com.newchar.debug.pc.device.PackageInspector
import com.newchar.debug.pc.device.scan.AdbMdnsService
import com.newchar.debug.pc.device.scan.DeviceChangeEvent
import com.newchar.debug.pc.device.scan.DeviceChangeType
import com.newchar.debug.pc.device.scan.DeviceScanManager
import com.newchar.debug.pc.device.scan.JvmDeviceMetadataResolver
import com.newchar.debug.pc.device.scan.JvmLanDiscoveryAgent
import com.newchar.debug.pc.executor.AdbCommandExecutor
import com.newchar.debug.pc.ui.chooseAdbExecutable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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

@Composable
fun AppContent(
    showSettingsDialog: Boolean = false,
    onDismissSettings: () -> Unit = {},
) {
    var selectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    val packageCache = remember { mutableStateMapOf<String, List<InstalledAppInfo>>() }
    var adbExecutablePath by remember { mutableStateOf("") }
    var settingsDraftPath by remember { mutableStateOf("") }
    var settingsLoaded by remember { mutableStateOf(false) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var manualDeviceHistory by remember { mutableStateOf(emptyList<String>()) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var adbStatusMessage by remember { mutableStateOf<String?>(null) }
    var packageLoading by remember { mutableStateOf(false) }
    var packageError by remember { mutableStateOf<String?>(null) }
    var packageList by remember { mutableStateOf(emptyList<InstalledAppInfo>()) }
    var wirelessPromptDevice by remember { mutableStateOf<DeviceInfo?>(null) }

    val scope = rememberCoroutineScope()
    val settingsStore = remember { DesktopAppSettingsStore() }

    LaunchedEffect(settingsStore) {
        val settings = settingsStore.load()
        adbExecutablePath = settings.adbExecutablePath
        settingsDraftPath = settings.adbExecutablePath
        manualDeviceHistory = settings.manualDeviceHistory
        settingsLoaded = true
    }

    if (!settingsLoaded) {
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
    val scanManager = remember(executor, scope) {
        AdbDevices.initExecutor(executor)
        AdbCommon.initExecutor(executor)
        LogcatManager.initExecutor(executor)
        DeviceScanManager(
            executor = executor,
            externalScope = scope,
            deviceMetadataResolver = JvmDeviceMetadataResolver(),
            lanDiscoveryAgent = JvmLanDiscoveryAgent(),
        )
    }
    val effectiveAdbPath = remember(executor, adbExecutablePath) { executor.resolveAdbExecutablePath() }
    val scanState by scanManager.state.collectAsState()
    val deviceList = scanState.devices

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

    LaunchedEffect(selectedDevice?.id, selectedDevice?.status, selectedDevice?.isRetainedOffline) {
        val device = selectedDevice
        if (device == null) {
            packageLoading = false
            packageError = null
            packageList = emptyList()
            return@LaunchedEffect
        }
        packageList = packageCache[device.id].orEmpty()
        if (device.isRetainedOffline || device.status != "device") {
            packageLoading = false
            packageError = if (packageList.isEmpty()) "当前设备不在线，无法刷新应用列表。" else null
            return@LaunchedEffect
        }
        packageLoading = true
        packageError = null
        val result = runCatching { PackageInspector.loadInstalledApps(executor, device.id) }
        result.onSuccess { apps ->
            packageList = apps
            packageCache[device.id] = apps
            packageLoading = false
        }.onFailure { throwable ->
            packageLoading = false
            packageError = throwable.message ?: "获取应用列表失败"
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
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(AppTheme.panel)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(
                text = "设备列表",
                style = TitleTextStyle.copy(fontSize = 20.sp, color = AppTheme.accent),
            )

            AppDivider()

            if (!adbStatusMessage.isNullOrBlank()) {
                AppText(
                    text = adbStatusMessage.orEmpty(),
                    style = HintTextStyle.copy(color = AppTheme.danger),
                    maxLines = 4,
                )
                AppDivider()
            }

            if (!scanState.lastError.isNullOrBlank()) {
                AppText(
                    text = scanState.lastError.orEmpty(),
                    style = HintTextStyle.copy(color = AppTheme.danger),
                    maxLines = 3,
                )
                AppDivider()
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton(
                    text = "刷新设备",
                    prefix = "↻",
                    modifier = Modifier.weight(1f).height(40.dp),
                    onClick = { scope.launch { scanManager.refreshNow() } },
                )
                AppOutlinedButton(
                    text = "添加设备",
                    modifier = Modifier.weight(1f).height(40.dp),
                    onClick = { showAddDeviceDialog = true },
                )
            }

            PanelCard(modifier = Modifier.fillMaxWidth()) {
                DetailRow(label = "ADB Config", value = adbExecutablePath.ifBlank { "PATH / ENV" })
                DetailRow(label = "ADB Active", value = effectiveAdbPath)
                DetailRow(label = "Last Source", value = scanState.lastRefreshSource?.name ?: "Unknown")
                DetailRow(label = "mDNS", value = "${scanState.mdnsServices.size} services")
                DetailRow(label = "LAN", value = "${scanState.discoveredLanEndpoints.size} endpoints")
            }

            if (deviceList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText("设备", style = TitleTextStyle.copy(fontSize = 32.sp, color = AppTheme.textHint))
                        Spacer(Modifier.height(12.dp))
                        AppText("未检测到设备", style = BodyTextStyle.copy(color = AppTheme.textSecondary))
                        Spacer(Modifier.height(4.dp))
                        AppText(
                            "请连接 Android 设备并启用 USB 调试",
                            style = HintTextStyle,
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(deviceList) { device ->
                        val isSelected = selectedDevice?.id == device.id
                        DeviceCard(
                            device = device,
                            selected = isSelected,
                            onClick = { selectedDevice = device },
                        )
                    }
                }
            }
        }

        AppDivider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            color = AppTheme.border,
        )

        DeviceDetailPanel(
            modifier = Modifier.weight(1f),
            device = selectedDevice,
            packages = packageList,
            packageLoading = packageLoading,
            packageError = packageError,
            recentChanges = scanState.recentChanges,
            mdnsServices = scanState.mdnsServices,
            recentMdnsMessages = scanState.recentMdnsMessages,
            recentLanMessages = scanState.recentLanMessages,
            scannedLanSubnets = scanState.scannedLanSubnets,
            discoveredLanEndpoints = scanState.discoveredLanEndpoints,
            onConnectWireless = { device ->
                scope.launch {
                    val endpoint = device.wirelessEndpoint
                    if (endpoint.isBlank()) {
                        toastMessage = "未获取到无线端口"
                        return@launch
                    }
                    val result = executor.adb("connect", endpoint)
                    val output = result.output.ifBlank { result.error }
                    val success = result.isSuccess ||
                        output.contains("connected to", ignoreCase = true) ||
                        output.contains("already connected to", ignoreCase = true)
                    toastMessage = if (success) "无线连接成功: $endpoint" else output.ifBlank { "无线连接失败: $endpoint" }
                    if (success) {
                        wirelessPromptDevice = null
                        scanManager.refreshNow()
                    }
                }
            },
            onEnableWirelessAdb = { device ->
                scope.launch {
                    val result = executor.adb("-s", device.id, "tcpip", "5555")
                    val output = result.output.ifBlank { result.error }
                    val success = result.isSuccess ||
                        output.contains("restarting in TCP mode port: 5555", ignoreCase = true) ||
                        output.contains("tcp mode port: 5555", ignoreCase = true)
                    toastMessage = if (success) {
                        "${device.model.ifBlank { device.id }} 已切到 adb tcpip 5555"
                    } else {
                        output.ifBlank { "切换 adb tcpip 5555 失败" }
                    }
                    if (success) {
                        kotlinx.coroutines.delay(1200L)
                        scanManager.refreshNow()
                    }
                }
            },
        )
        }

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
                        )
                    )
                }
                onDismissSettings()
            },
        )
    }
}

@Composable
private fun DeviceCard(
    device: DeviceInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = when {
        selected -> AppTheme.accentSoft
        device.isRetainedOffline -> Color(0xFFF7F2E8)
        else -> AppTheme.panel
    }
    val borderColor = when {
        selected -> AppTheme.accent
        device.isRetainedOffline -> Color(0xFFD2B48C)
        else -> AppTheme.border
    }
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
            AppText(
                text = device.model.ifEmpty { device.id },
                style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            AppText(
                text = "ID: ${device.id}",
                style = HintTextStyle.copy(color = AppTheme.textSecondary),
                maxLines = 1,
            )
            AppText(
                text = if (device.isNetworkDevice) "State: ${device.status} · Wi-Fi" else "State: ${device.status} · USB",
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

@Composable
private fun DeviceDetailPanel(
    modifier: Modifier = Modifier,
    device: DeviceInfo?,
    packages: List<InstalledAppInfo>,
    packageLoading: Boolean,
    packageError: String?,
    recentChanges: List<DeviceChangeEvent>,
    mdnsServices: List<AdbMdnsService>,
    recentMdnsMessages: List<String>,
    recentLanMessages: List<String>,
    scannedLanSubnets: List<String>,
    discoveredLanEndpoints: List<String>,
    onConnectWireless: (DeviceInfo) -> Unit,
    onEnableWirelessAdb: (DeviceInfo) -> Unit,
) {
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppText("设备详情", style = TitleTextStyle)

                PanelCard {
                    DetailRow(label = "ID", value = device.id)
                    DetailRow(label = "Status", value = device.status.ifEmpty { "Unknown" })
                    DetailRow(label = "Transport", value = if (device.isNetworkDevice) "Wi-Fi / TCP" else "USB")
                    DetailRow(label = "Manufacturer", value = device.manufacturer.ifEmpty { "Unknown" })
                    DetailRow(label = "Model", value = device.model.ifEmpty { "Unknown" })
                    DetailRow(label = "Category", value = device.deviceCategory.ifEmpty { "Unknown" })
                    DetailRow(label = "Product", value = device.product.ifEmpty { "Unknown" })
                    DetailRow(label = "Device", value = device.device.ifEmpty { "Unknown" })
                    if (device.usbInfo.isNotEmpty()) {
                        DetailRow(label = "USB", value = device.usbInfo)
                    }
                    if (device.transportId.isNotEmpty()) {
                        DetailRow(label = "Transport ID", value = device.transportId)
                    }
                    if (device.wirelessIp.isNotEmpty()) {
                        DetailRow(label = "Wireless IP", value = device.wirelessIp)
                    }
                    if (device.wirelessPort > 0) {
                        DetailRow(label = "ADB Port", value = device.wirelessPort.toString())
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppOutlinedButton(
                        text = "Logcat",
                        prefix = ">_",
                        modifier = Modifier.weight(1f),
                        onClick = { /* TODO */ },
                    )

                    AppOutlinedButton(
                        text = "文件",
                        prefix = "[]",
                        modifier = Modifier.weight(1f),
                        onClick = { /* TODO */ },
                    )
                }

                AppOutlinedButton(
                    text = "APK 信息查询",
                    prefix = "i",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { /* TODO */ },
                )

                if (!device.isNetworkDevice && !device.isRetainedOffline && device.status == "device") {
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

                PackageListPanel(
                    packages = packages,
                    loading = packageLoading,
                    error = packageError,
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
        }
    }
}

@Composable
private fun PackageListPanel(
    packages: List<InstalledAppInfo>,
    loading: Boolean,
    error: String?,
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
            packages.isEmpty() -> AppText("暂无可展示应用", style = HintTextStyle)
            else -> {
                AppText("共 ${packages.size} 个应用", style = HintTextStyle.copy(color = AppTheme.textSecondary))
                Spacer(Modifier.height(8.dp))
                packages.take(40).forEach { app ->
                    PackageRow(app = app)
                }
                if (packages.size > 40) {
                    AppText("仅展示前 40 项，已按优先级排序。", style = HintTextStyle)
                }
            }
        }
    }
}

@Composable
private fun PackageRow(app: InstalledAppInfo) {
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
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = app.packageName,
                style = BodyTextStyle.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            AppText(
                text = badgeText,
                style = HintTextStyle.copy(color = badgeColor, fontWeight = FontWeight.SemiBold),
            )
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
        title = "关于 PC Debug Tools",
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
