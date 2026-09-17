package com.newchar.debug.pc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.newchar.debug.pc.config.AppSettings
import com.newchar.debug.pc.config.DesktopAppSettingsStore
import com.newchar.debug.pc.android.AndroidManifest
import com.newchar.debug.pc.device.AdbCommon
import com.newchar.debug.pc.device.AdbDevices
import com.newchar.debug.pc.device.AppIconCache
import com.newchar.debug.pc.device.AppDetailPopup
import com.newchar.debug.pc.device.EnrichErrorLogger
import com.newchar.debug.pc.device.AppStoreLauncher
import com.newchar.debug.pc.device.AppPackageExporter
import com.newchar.debug.pc.device.DeviceAppInfoAgent
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.device.DeviceConnectionState
import com.newchar.debug.pc.device.DeviceOwnershipManager
import com.newchar.debug.pc.device.DeviceOwnershipMode
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
import com.newchar.debug.pc.device.scan.DeviceRefreshSource
import com.newchar.debug.pc.device.scan.DeviceScanManager
import com.newchar.debug.pc.device.scan.JvmDeviceMetadataResolver
import com.newchar.debug.pc.device.scan.JvmLanDiscoveryAgent
import com.newchar.debug.pc.device.scan.JvmWifiDetector
import com.newchar.debug.pc.device.scan.WifiBanner
import com.newchar.debug.pc.device.scan.WifiBannerDetail
import com.newchar.debug.pc.device.files.DeviceFileBrowser
import com.newchar.debug.pc.device.input.InputScriptPanel
import com.newchar.debug.pc.device.preview.DeviceProbeCache
import com.newchar.debug.pc.device.preview.DevicePreviewWindow
import com.newchar.debug.pc.device.preview.InlineDevicePreview
import com.newchar.debug.pc.device.preview.ScreenFrameCache
import com.newchar.debug.pc.device.preview.captureScreenFrame
import com.newchar.debug.pc.device.preview.queryForegroundPackage
import com.newchar.debug.pc.device.shell.QuickShellButton
import com.newchar.debug.pc.device.wifi.WifiShellExecutor
import com.newchar.debug.pc.device.wifi.WifiControlPanel
import com.newchar.debug.pc.device.wifi.WifiStatus
import com.newchar.debug.pc.executor.AdbCommandExecutor
import com.newchar.debug.pc.ui.chooseAdbExecutable
import com.newchar.debug.pc.ui.chooseExportDirectory
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.Base64

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

/** 首页设备列表的展示模式。 */
private enum class DeviceHomeMode {
    CARD,
    COMPACT,
}

/** 首页普通卡片中单台设备的截图状态。 */
private data class HomeDeviceFrame(
    val bitmap: ImageBitmap? = null,
    val error: String? = null,
)

/** 首页精简行中单台设备的当前前台应用信息。 */
private data class HomeForegroundApp(
    val packageName: String = "",
    val label: String = "",
    val error: String? = null,
)

/** 为同一物理设备生成跨 USB/Wi-Fi 路由稳定的连接键。 */
private fun deviceConnectionKey(device: DeviceInfo): String = device.physicalDeviceKey()

/** 从桌面拖入应用列表、等待安装的 APK 文件请求。 */
data class ApkDropRequest(
    val file: File,
    val token: Long,
)

/**
 * 后台补全指定设备的应用图标并写入磁盘；本方法不更新 Compose 状态，避免打断当前界面。
 */
private suspend fun prewarmAppIcons(
    appInfoAgent: DeviceAppInfoAgent,
    appIconCache: AppIconCache,
    deviceId: String,
    apps: List<InstalledAppInfo>,
) {
    if (apps.all { appIconCache.existsIcon(deviceId, it.packageName, it.versionCode) }) return
    val versionByPackage = apps.associate { it.packageName to it.versionCode }
    appInfoAgent.enrich(deviceId, apps).forEach { enrichment ->
        val icon = enrichment.iconPng ?: return@forEach
        appIconCache.save(deviceId, enrichment.packageName, versionByPackage[enrichment.packageName].orEmpty(), icon)
    }
}

/** 已完成 APK 信息预检、等待实际安装的任务。 */
private data class ApkInstallRequest(
    val file: File,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val existedBeforeInstall: Boolean,
    val allowDowngrade: Boolean,
    val token: Long,
)

/** 应用列表可叠加的包名过滤条件。 */
private enum class AppListFilterOption(val label: String) {
    HIDE_ANDROID_NAMESPACE("隐藏包名 android / com.android.*"),
    SYSTEM_APP("系统 App"),
    NON_SYSTEM_APP("非系统 App"),
    RELEASE_BUILD("Release"),
    DEBUG_BUILD("Debug"),
}

/** 将应用过滤浮层定位在过滤控件下方，且不参与原页面布局。 */
private object AppListFilterPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(anchorBounds.left, anchorBounds.bottom + 4)
}

/** 将状态栏“关于”菜单定位在触发按钮正下方，并右对齐。 */
private object AboutMenuPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(anchorBounds.right - popupContentSize.width, anchorBounds.bottom + 4)
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
    onDoubleClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AppTheme.accentSoft else AppTheme.panelAlt)
            .pointerInput(onClick, onDoubleClick) {
                detectTapGestures(onTap = { onClick() }, onDoubleTap = { onDoubleClick() })
            }
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
    onShowAbout: () -> Unit = {},
    onShowEvents: () -> Unit = {},
) {
    var showAboutMenu by remember { mutableStateOf(false) }
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

        Box {
            AppOutlinedButton(
                text = "关于 ▾",
                modifier = Modifier.height(28.dp),
                onClick = { showAboutMenu = !showAboutMenu },
            )
            if (showAboutMenu) {
                Popup(
                    popupPositionProvider = AboutMenuPopupPositionProvider,
                    onDismissRequest = { showAboutMenu = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        modifier = Modifier
                            .width(132.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppTheme.panel)
                            .border(1.dp, AppTheme.border, RoundedCornerShape(8.dp))
                            .padding(vertical = 4.dp),
                    ) {
                        AboutMenuItem("关于") {
                            showAboutMenu = false
                            onShowAbout()
                        }
                        AboutMenuItem("事件") {
                            showAboutMenu = false
                            onShowEvents()
                        }
                    }
                }
            }
        }

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

/** 显示状态栏“关于”下拉菜单中的一个可点击条目。 */
@Composable
private fun AboutMenuItem(text: String, onClick: () -> Unit) {
    AppText(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = BodyTextStyle,
    )
}

// ============================================================
// 设备信息展示组件（Tab 制表符分隔）
// ============================================================

/** 设备信息文本组件（使用 Tab 制表符分隔，支持系统原生选择复制） */
@Composable
private fun DeviceInfoText(
    device: DeviceInfo,
    modifier: Modifier = Modifier,
) {
    val infoText = buildString {
        appendLine("ID\t\t${device.id}")
        appendLine("Status\t\t${device.connectionStatusLabel()}")
        appendLine("Transport\t${device.connectionStatusLabel()}")
        appendLine("Manufacturer\t${device.manufacturer.ifEmpty { "Unknown" }}")
        appendLine("Model\t\t${device.model.ifEmpty { "Unknown" }}")
        appendLine("Product\t\t${device.product.ifEmpty { "Unknown" }}")
        appendLine("Device\t\t${device.device.ifEmpty { "Unknown" }}")
        if (device.transportId.isNotEmpty()) appendLine("Transport ID\t${device.transportId}")
        if (device.wirelessIp.isNotEmpty()) appendLine("Wireless IP\t${device.wirelessIp}")
        if (device.wirelessPort > 0) appendLine("ADB Port\t${device.wirelessPort}")
        if (device.wifiSsid.isNotEmpty()) appendLine("WiFi SSID\t${device.wifiSsid}")
    }

    PanelCard(modifier = modifier) {
        AppText(
            "设备信息",
            style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold),
        )
        SelectionContainer {
            AppText(
                text = infoText,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = AppTheme.textPrimary,
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppTheme.panelAlt),
            )
        }
    }
}

@Composable
fun AppContent(
    showSettingsDialog: Boolean = false,
    onDismissSettings: () -> Unit = {},
    apkDropRequest: ApkDropRequest? = null,
    onApkDropConsumed: () -> Unit = {},
    onAppListDropBoundsChanged: (Rect?) -> Unit = {},
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
    var cameraAppPackageSetting by remember { mutableStateOf("com.newchar.debug.sample") }
    var deviceHomeMode by remember { mutableStateOf(DeviceHomeMode.CARD) }
    var pinnedPackagesByDevice by remember { mutableStateOf(emptyMap<String, Set<String>>()) }
    var wirelessPromptDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    val manuallyDisconnected = remember { mutableStateMapOf<String, DeviceInfo>() }
    var deleteConfirmDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var showWifiBannerDetail by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showScanEventsDialog by remember { mutableStateOf(false) }
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
            cameraAppPackageSetting = settings.cameraAppPackage.ifBlank { "com.newchar.debug.sample" }
            deviceHomeMode = if (settings.homeCompactMode) DeviceHomeMode.COMPACT else DeviceHomeMode.CARD
            pinnedPackagesByDevice = settings.pinnedPackagesByDevice.mapValues { (_, packages) -> packages.toSet() }
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
    val backgroundAppInfoAgent = remember(executor) { DeviceAppInfoAgent(executor) }
    val backgroundAppIconCache = remember { AppIconCache() }
    val packageSnapshotStore = remember { PackageSnapshotStore() }
    val prewarmedDeviceIds = remember { mutableStateListOf<String>() }

    // 冷启动时使用应用快照预热图标缓存；仅落盘，不改变任何列表或详情 UI 状态。
    LaunchedEffect(scannedDevices, packageCache.keys.toSet()) {
        val onlineDeviceIds = scannedDevices
            .filter(DeviceInfo::canManageDevice)
            .map { it.id }
            .filterNot { it in prewarmedDeviceIds }
        if (onlineDeviceIds.isEmpty()) return@LaunchedEffect
        val snapshotApps = packageSnapshotStore.loadAll().associate { it.deviceId to it.apps }
        onlineDeviceIds.forEach { deviceId ->
            val apps = packageCache[deviceId].orEmpty().ifEmpty { snapshotApps[deviceId].orEmpty() }
            if (apps.isEmpty()) return@forEach
            prewarmedDeviceIds += deviceId
            launch {
                prewarmAppIcons(backgroundAppInfoAgent, backgroundAppIconCache, deviceId, apps)
            }
        }
    }

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

    val wifiShellExecutor = remember(executor) { WifiShellExecutor(executor) }

    // 心跳保活 + adb reverse
    val heartbeatManager = remember(executor, scanManager, scope) {
        HeartbeatManager(
            executor = executor,
            externalScope = scope,
            scanManager = scanManager,
            onHeartbeatTimeout = { device ->
                scope.launch { autoRecover.onHeartbeatTimeout(device.id) }
            },
        ).apply {
            setWifiCommandHandler { deviceId, action, params ->
                dispatchWifiCommand(wifiShellExecutor, deviceId, action, params)
            }
        }
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

    val activeConnectionStates = remember { mutableStateMapOf<String, DeviceConnectionState>() }
    val activeStateByKey = activeConnectionStates.toMap()
    val deviceList = remember(scannedDevices, manuallyDisconnected.toMap(), activeStateByKey) {
        val scannedIds = scannedDevices.mapTo(mutableSetOf()) { it.id }
        val retainedDisconnected = manuallyDisconnected.values
            .filter { it.id !in scannedIds }
            .map { it.withConnection(newManuallyDisconnected = true) }
        val updatedScanned = scannedDevices.map { device ->
            if (manuallyDisconnected.containsKey(device.id)) {
                device.withConnection(newManuallyDisconnected = true)
            } else {
                device
            }
        }
        DeviceInfo.mergeConnections(updatedScanned + retainedDisconnected, activeStateByKey)
    }

    LaunchedEffect(deviceList) {
        val activeKeys = deviceList.mapTo(mutableSetOf(), ::deviceConnectionKey)
        activeConnectionStates.keys.toList()
            .filterNot(activeKeys::contains)
            .forEach(activeConnectionStates::remove)
        deviceList.forEach { device ->
            activeConnectionStates[deviceConnectionKey(device)] = device.connectionState
        }
    }

    val effectiveSelectedDevice = remember(selectedDevice, deviceList) {
        val selected = selectedDevice ?: return@remember null
        deviceList.firstOrNull { it.id == selected.id }
            ?: deviceList.firstOrNull { deviceConnectionKey(it) == deviceConnectionKey(selected) }
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
            if (event.source != DeviceRefreshSource.STARTUP) {
                val name = event.device.displayName()
                toastMessage = "$name：${event.summary.removePrefix("设备 ${event.device.id} ")}"
            }
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
        val selected = selectedDevice ?: return@LaunchedEffect
        val replacement = deviceList.firstOrNull { it.id == selected.id }
            ?: deviceList.firstOrNull { deviceConnectionKey(it) == deviceConnectionKey(selected) }
        if (replacement == null) {
            selectedDevice = null
        } else if (replacement.id != selected.id) {
            selectedDevice = replacement
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
                        selectedDevice = null
                    },
                    onDoubleClick = {
                        scope.launch {
                            scanManager.refreshNow()
                            toastMessage = "正在刷新全部设备状态"
                        }
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
            if (currentNav == NavItem.DEVICES && effectiveSelectedDevice != null) {
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
                                    onToast = { toastMessage = it },
                                    onUnauthorizedReconnect = { target ->
                                        scope.launch {
                                            reconnectUnauthorizedWifiDevice(target, executor, { toastMessage = it }) {
                                                scanManager.refreshNow()
                                            }
                                        }
                                    },
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
                    if (effectiveSelectedDevice == null) {
                        HomeDeviceListPanel(
                            devices = deviceList,
                            mode = deviceHomeMode,
                            executor = executor,
                            appInfoAgent = backgroundAppInfoAgent,
                            onToast = { toastMessage = it },
                            onUnauthorizedReconnect = { target ->
                                scope.launch {
                                    reconnectUnauthorizedWifiDevice(target, executor, { toastMessage = it }) {
                                        scanManager.refreshNow()
                                    }
                                }
                            },
                            onModeChanged = { mode ->
                                deviceHomeMode = mode
                                scope.launch {
                                    settingsStore.save(
                                        AppSettings(
                                            adbExecutablePath = adbExecutablePath,
                                            manualDeviceHistory = manualDeviceHistory,
                                            previewAlwaysOnTop = previewAlwaysOnTopSetting,
                                            cameraAppPackage = cameraAppPackageSetting,
                                            homeCompactMode = mode == DeviceHomeMode.COMPACT,
                                            pinnedPackagesByDevice = pinnedPackagesByDevice.mapValues { (_, packages) -> packages.toList() },
                                        )
                                    )
                                }
                            },
                            onDeviceSelected = { selectedDevice = it },
                            modifier = Modifier.fillMaxSize(),
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
                                onToast = { toastMessage = it },
                                lastContactAt = { heartbeatManager.lastContactAt(dev.id) },
                                onRequestAppWifiStatus = { target ->
                                    heartbeatManager.requestAppWifiStatus(target).map { status ->
                                        WifiStatus(status.enabled, status.ssid)
                                    }
                                },
                                onPreviewAlwaysOnTopChanged = { previewAlwaysOnTopSetting = it },
                                probeCache = deviceProbeCache,
                                scope = scope,
                                packageCache = packageCache,
                                pinnedPackageNames = pinnedPackagesByDevice[dev.id].orEmpty(),
                                onPinnedPackageNamesChanged = { deviceId, packageNames ->
                                    pinnedPackagesByDevice = pinnedPackagesByDevice + (deviceId to packageNames)
                                    scope.launch {
                                        settingsStore.save(
                                            AppSettings(
                                                adbExecutablePath = adbExecutablePath,
                                                manualDeviceHistory = manualDeviceHistory,
                                                previewAlwaysOnTop = previewAlwaysOnTopSetting,
                                                cameraAppPackage = cameraAppPackageSetting,
                                                homeCompactMode = deviceHomeMode == DeviceHomeMode.COMPACT,
                                                pinnedPackagesByDevice = pinnedPackagesByDevice.mapValues { (_, names) -> names.toList() },
                                            )
                                        )
                                    }
                                },
                                manualDeviceHistory = manualDeviceHistory,
                                settingsStore = settingsStore,
                                onShowAddDevice = { showAddDeviceDialog = true },
                                onDismissWirelessPrompt = { wirelessPromptDevice = null },
                                apkDropRequest = apkDropRequest,
                                onApkDropConsumed = onApkDropConsumed,
                                onAppListDropBoundsChanged = onAppListDropBoundsChanged,
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
                            onToast = { toastMessage = it },
                            scope = scope,
                            executor = executor,
                            packageCache = packageCache,
                            manualDeviceHistory = manualDeviceHistory,
                            settingsStore = settingsStore,
                            onShowAddDevice = { showAddDeviceDialog = true },
                            onDismissWirelessPrompt = { wirelessPromptDevice = null },
                            apkDropRequest = apkDropRequest,
                            onApkDropConsumed = onApkDropConsumed,
                            onAppListDropBoundsChanged = onAppListDropBoundsChanged,
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
            onShowAbout = { showAboutDialog = true },
            onShowEvents = { showScanEventsDialog = true },
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
                            cameraAppPackage = cameraAppPackageSetting,
                            homeCompactMode = deviceHomeMode == DeviceHomeMode.COMPACT,
                            pinnedPackagesByDevice = pinnedPackagesByDevice.mapValues { (_, packages) -> packages.toList() },
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
            currentCameraAppPackage = cameraAppPackageSetting,
            onDismiss = onDismissSettings,
            onSave = { path, cameraPackage ->
                val normalizedPath = path.trim()
                settingsDraftPath = normalizedPath
                adbExecutablePath = normalizedPath
                cameraAppPackageSetting = cameraPackage.trim().ifBlank { "com.newchar.debug.sample" }
                scope.launch {
                    settingsStore.save(
                        AppSettings(
                            adbExecutablePath = normalizedPath,
                            manualDeviceHistory = manualDeviceHistory,
                            previewAlwaysOnTop = previewAlwaysOnTopSetting,
                            cameraAppPackage = cameraAppPackageSetting,
                            homeCompactMode = deviceHomeMode == DeviceHomeMode.COMPACT,
                            pinnedPackagesByDevice = pinnedPackagesByDevice.mapValues { (_, packages) -> packages.toList() },
                        )
                    )
                }
                onDismissSettings()
            },
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showScanEventsDialog) {
        ScanEventsDialog(
            recentChanges = scanState.recentChanges,
            mdnsServices = scanState.mdnsServices,
            recentMdnsMessages = scanState.recentMdnsMessages,
            recentLanMessages = scanState.recentLanMessages,
            scannedLanSubnets = scanState.scannedLanSubnets,
            discoveredLanEndpoints = scanState.discoveredLanEndpoints,
            onDismiss = { showScanEventsDialog = false },
            onRefreshDevices = { scope.launch { scanManager.refreshNow() } },
            onRefreshMdns = { scope.launch { scanManager.refreshMdnsNow() } },
            onRefreshLan = { scope.launch { scanManager.refreshLanNow() } },
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
                    if (!manuallyDisconnected.containsKey(device.id) && device.canManageDevice) {
                        executor.adb("disconnect", device.id)
                    }
                    manuallyDisconnected.remove(device.id)
                    if (selectedDevice?.id == device.id) {
                        selectedDevice = null
                    }
                    deleteConfirmDevice = null
                    toastMessage = "已删除: ${device.displayName()}"
                    scanManager.refreshNow()
                }
            },
            onDismissButton = { deleteConfirmDevice = null },
        ) {
            AppText(
                "确定删除设备「${device.displayName()}」？",
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
    onToast: (String) -> Unit,
    onUnauthorizedReconnect: (DeviceInfo) -> Unit,
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
                        text = device.displayName(),
                        style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (manuallyDisconnected) {
                        AppSmallButton(
                            text = "连接",
                            onClick = { onReconnect(device) },
                        )
                    } else if (device.canManageDevice) {
                        AppSmallOutlinedButton(
                            text = "断开",
                            modifier = Modifier.height(28.dp),
                            onClick = { onDisconnect(device.id) },
                        )
                    }
                    AppSmallOutlinedButton(
                        text = "✕",
                        modifier = Modifier.size(28.dp),
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
                DeviceConnectionStatus(
                    device = device,
                    onToast = onToast,
                    onUnauthorizedReconnect = onUnauthorizedReconnect,
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

/** 首页设备列表：普通模式展示周期截图卡片，精简模式仅展示当前前台应用信息。 */
@Composable
private fun HomeDeviceListPanel(
    devices: List<DeviceInfo>,
    mode: DeviceHomeMode,
    executor: AdbCommandExecutor,
    appInfoAgent: DeviceAppInfoAgent,
    onToast: (String) -> Unit,
    onUnauthorizedReconnect: (DeviceInfo) -> Unit,
    onModeChanged: (DeviceHomeMode) -> Unit,
    onDeviceSelected: (DeviceInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frames = remember { mutableStateMapOf<String, HomeDeviceFrame>() }
    val foregroundApps = remember { mutableStateMapOf<String, HomeForegroundApp>() }
    val screenFrameCache = remember { ScreenFrameCache() }
    val onlineDevices = devices.filter(DeviceInfo::isAvailableForDeviceManagement)
    LaunchedEffect(devices, mode) {
        val currentDeviceIds = devices.mapTo(mutableSetOf()) { it.id }
        frames.keys.filterNot { it in currentDeviceIds }.toList().forEach { frames.remove(it) }
        foregroundApps.keys.filterNot { it in currentDeviceIds }.toList().forEach { foregroundApps.remove(it) }
        if (mode == DeviceHomeMode.CARD) {
            devices.forEach { device ->
                screenFrameCache.load(device.id)?.let { cached ->
                    frames[device.id] = HomeDeviceFrame(bitmap = cached)
                }
            }
            while (true) {
                onlineDevices.forEach { device ->
                    val captured = runCatching { captureScreenFrame(executor, device.adbTarget()) }.getOrNull()
                    if (captured != null) {
                        frames[device.id] = HomeDeviceFrame(bitmap = captured.bitmap)
                        screenFrameCache.save(device.id, captured.pngBytes)
                    } else {
                        frames[device.id] = frames[device.id]?.copy(error = "截图失败")
                            ?: HomeDeviceFrame(error = "截图失败")
                    }
                    foregroundApps[device.id] = loadHomeForegroundApp(
                        device = device,
                        executor = executor,
                        appInfoAgent = appInfoAgent,
                        cached = foregroundApps[device.id],
                    )
                }
                kotlinx.coroutines.delay(10_000L)
            }
        } else {
            frames.clear()
            while (true) {
                onlineDevices.forEach { device ->
                    foregroundApps[device.id] = loadHomeForegroundApp(
                        device = device,
                        executor = executor,
                        appInfoAgent = appInfoAgent,
                        cached = foregroundApps[device.id],
                    )
                }
                kotlinx.coroutines.delay(10_000L)
            }
        }
    }
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("设备", style = TitleTextStyle)
            AppSmallOutlinedButton(
                text = if (mode == DeviceHomeMode.CARD) "精简模式" else "普通模式",
                onClick = {
                    onModeChanged(if (mode == DeviceHomeMode.CARD) DeviceHomeMode.COMPACT else DeviceHomeMode.CARD)
                },
            )
        }
        if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText("未检测到设备", style = BodyTextStyle.copy(color = AppTheme.textSecondary))
                    AppText("连接 Android 设备并启用 USB 调试", style = HintTextStyle)
                }
            }
        } else if (mode == DeviceHomeMode.CARD) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 360.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItems(devices, key = DeviceInfo::id) { device ->
                    HomeDeviceCard(
                        device,
                        frames[device.id],
                        foregroundApps[device.id],
                        onToast = onToast,
                        onUnauthorizedReconnect = onUnauthorizedReconnect,
                        onClick = { onDeviceSelected(device) },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(devices, key = DeviceInfo::id) { device ->
                    HomeCompactDeviceRow(
                        device = device,
                        foregroundApp = foregroundApps[device.id],
                        onToast = onToast,
                        onUnauthorizedReconnect = onUnauthorizedReconnect,
                        onClick = { onDeviceSelected(device) },
                    )
                }
            }
        }
    }
}

/** 获取首页精简行所需的前台应用包名与真实应用名称，并复用未变化包名的名称缓存。 */
private suspend fun loadHomeForegroundApp(
    device: DeviceInfo,
    executor: AdbCommandExecutor,
    appInfoAgent: DeviceAppInfoAgent,
    cached: HomeForegroundApp?,
): HomeForegroundApp {
    val packageName = queryForegroundPackage(executor, device.adbTarget())
        ?: return HomeForegroundApp(error = "前台应用未知")
    if (cached?.packageName == packageName && cached.label.isNotBlank()) return cached
    val label = appInfoAgent.enrich(device.adbTarget(), listOf(InstalledAppInfo(packageName = packageName)))
        .firstOrNull()?.appLabel.orEmpty()
    return HomeForegroundApp(packageName, label.ifBlank { packageName })
}

/** 绘制首页普通模式的设备卡片，左侧仅展示在线设备的最新单帧截图。 */
@Composable
private fun HomeDeviceCard(
    device: DeviceInfo,
    frame: HomeDeviceFrame?,
    foregroundApp: HomeForegroundApp?,
    onToast: (String) -> Unit,
    onUnauthorizedReconnect: (DeviceInfo) -> Unit,
    onClick: () -> Unit,
) {
    val online = device.isAvailableForDeviceManagement()
    PanelCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(112.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppTheme.panelAlt),
                contentAlignment = Alignment.Center,
            ) {
                if (frame?.bitmap != null) {
                    Image(
                        bitmap = frame.bitmap,
                        contentDescription = "${device.displayName()} 的屏幕截图",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AppText(
                        if (online) frame?.error ?: "正在获取截图…" else "设备离线\n不获取截图",
                        style = HintTextStyle,
                        maxLines = 2,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText(device.displayName(), style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
                AppText("ID: ${device.id}", style = HintTextStyle, maxLines = 1)
                DeviceConnectionStatus(
                    device = device,
                    onToast = onToast,
                    onUnauthorizedReconnect = onUnauthorizedReconnect,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (online) {
                    val foregroundText = foregroundApp?.let { app ->
                        if (app.error != null) app.error else "前台：${app.label} (${app.packageName})"
                    } ?: "正在读取前台应用…"
                    AppText(foregroundText, style = HintTextStyle.copy(color = AppTheme.accent), maxLines = 1)
                }
            }
        }
    }
}

/** 绘制首页精简模式的设备行，不申请屏幕截图。 */
@Composable
private fun HomeCompactDeviceRow(
    device: DeviceInfo,
    foregroundApp: HomeForegroundApp?,
    onToast: (String) -> Unit,
    onUnauthorizedReconnect: (DeviceInfo) -> Unit,
    onClick: () -> Unit,
) {
    val foregroundText = if (device.isAvailableForDeviceManagement()) {
        foregroundApp?.let { app ->
            if (app.error != null) app.error else "前台：${app.label} (${app.packageName})"
        } ?: "正在读取前台应用…"
    } else {
        "前台：设备未连接"
    }
    PanelCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(device.displayName(), style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
                AppText("ID: ${device.id}", style = HintTextStyle, maxLines = 1)
            }
            Column(modifier = Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DeviceConnectionStatus(
                    device = device,
                    onToast = onToast,
                    onUnauthorizedReconnect = onUnauthorizedReconnect,
                )
                AppText(foregroundText, style = HintTextStyle.copy(color = AppTheme.textSecondary), maxLines = 1)
            }
        }
    }
}

/** 在设备卡片中展示当前生效的连接状态；Wi-Fi 端点可双击复制或在未授权时重新连接。 */
@Composable
private fun DeviceConnectionStatus(
    device: DeviceInfo,
    onToast: (String) -> Unit,
    onUnauthorizedReconnect: (DeviceInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val endpoint = device.wirelessEndpoint.ifBlank { if (device.isWirelessConnection) device.id else "" }
    val text = buildDeviceConnectionStatusText(device)
    val actionModifier = if (device.isWirelessConnection && endpoint.isNotBlank()) {
        Modifier.pointerInput(device.id, device.status, endpoint) {
            detectTapGestures(onDoubleTap = {
                if (device.connectionState is DeviceConnectionState.Unauthorized) onUnauthorizedReconnect(device)
                else onToast(if (copyTextToSystemClipboard(endpoint)) "已复制：$endpoint" else "复制失败，请手动复制：$endpoint")
            })
        }
    } else {
        Modifier
    }
    AppText(
        text = text,
        style = HintTextStyle.copy(color = AppTheme.textSecondary),
        modifier = modifier.then(actionModifier),
        maxLines = 1,
    )
}

/** 构造设备卡片共用的状态文案，只展示当前实际使用的连接方式。 */
private fun buildDeviceConnectionStatusText(device: DeviceInfo): String =
    "状态：${device.connectionStatusLabel()}"

/** 将手机端 WiFi 协议映射为 PC 的 adb shell 操作，并返回给 reverse 通道。 */
private suspend fun dispatchWifiCommand(
    wifiExecutor: WifiShellExecutor,
    deviceId: String,
    action: String,
    params: String,
): String = when (action) {
    "enable" -> wifiExecutor.setWifiEnabled(deviceId, true).toWifiProtocol()
    "disable" -> wifiExecutor.setWifiEnabled(deviceId, false).toWifiProtocol()
    "disconnect" -> wifiExecutor.disconnect(deviceId).toWifiProtocol()
    "connect-saved" -> wifiExecutor.connectSaved(deviceId, decodeWifiProtocolParam(params)).toWifiProtocol()
    "forget" -> wifiExecutor.forget(deviceId, decodeWifiProtocolParam(params)).toWifiProtocol()
    "connect" -> {
        val values = params.split('|')
        wifiExecutor.connect(deviceId, decodeWifiProtocolParam(values.firstOrNull().orEmpty()), decodeWifiProtocolParam(values.getOrNull(1).orEmpty())).toWifiProtocol()
    }
    "status" -> wifiExecutor.status(deviceId).fold(
        onSuccess = { "OK|STATUS|${it.enabled}|${encodeWifiProtocolParam(it.connectedSsid)}" },
        onFailure = { "ERROR|${it.message ?: "WiFi 状态读取失败"}" },
    )
    "scan" -> wifiExecutor.scan(deviceId).fold(
        onSuccess = { "OK|SCAN|${it.joinToString(",") { network -> encodeWifiProtocolParam(network.ssid) }}" },
        onFailure = { "ERROR|${it.message ?: "WiFi 扫描失败"}" },
    )
    else -> "ERROR|不支持的 WiFi 操作"
}

/** 将 WiFi 执行结果编码成 PC 通信协议。 */
private fun com.newchar.debug.pc.device.wifi.WifiSwitchResult.toWifiProtocol(): String =
    if (success) "OK|$message" else "ERROR|$message"

/** 解码手机端通过 WIFI_CMD 上传的 URL-safe Base64 参数。 */
private fun decodeWifiProtocolParam(value: String): String = runCatching {
    String(Base64.getUrlDecoder().decode(value))
}.getOrDefault("")

/** 编码回传给手机端的 WiFi 文本参数。 */
private fun encodeWifiProtocolParam(value: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

/** 将文本写入桌面系统剪贴板；桌面环境不可用时返回失败。 */
private fun copyTextToSystemClipboard(text: String): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}.isSuccess

@Composable
private fun DeviceDetailPanel(
    modifier: Modifier = Modifier,
    device: DeviceInfo?,
    packages: List<InstalledAppInfo> = emptyList(),
    packageLoading: Boolean = false,
    packageError: String? = null,
    executor: AdbCommandExecutor = AdbCommandExecutor.create(null),
    packageCache: SnapshotStateMap<String, List<InstalledAppInfo>> = mutableStateMapOf(),
    pinnedPackageNames: Set<String> = emptySet(),
    onPinnedPackageNamesChanged: (String, Set<String>) -> Unit = { _, _ -> },
    isVisible: Boolean = true,
    recentChanges: List<DeviceChangeEvent>,
    mdnsServices: List<AdbMdnsService>,
    recentMdnsMessages: List<String>,
    recentLanMessages: List<String>,
    scannedLanSubnets: List<String>,
    discoveredLanEndpoints: List<String>,
    messages: List<PcMessage> = emptyList(),
    onClearMessages: () -> Unit = {},
    onToast: (String) -> Unit = {},
    lastContactAt: () -> Long = { 0L },
    onRequestAppWifiStatus: suspend (DeviceInfo) -> Result<WifiStatus> = {
        Result.failure(IllegalStateException("App WiFi 状态请求不可用"))
    },
    onPreviewAlwaysOnTopChanged: (Boolean) -> Unit = {},
    probeCache: DeviceProbeCache = DeviceProbeCache(),
    scope: CoroutineScope,
    manualDeviceHistory: List<String> = emptyList(),
    settingsStore: DesktopAppSettingsStore,
    onShowAddDevice: () -> Unit = {},
    onDismissWirelessPrompt: () -> Unit = {},
    apkDropRequest: ApkDropRequest? = null,
    onApkDropConsumed: () -> Unit = {},
    onAppListDropBoundsChanged: (Rect?) -> Unit = {},
) {
    val localPackageList = remember { mutableStateOf(emptyList<InstalledAppInfo>()) }
    val localPackageDisplayItems = remember { mutableStateOf(emptyList<PackageDisplayItem>()) }
    val localPackageLoading = remember { mutableStateOf(false) }
    val localPackageError = remember { mutableStateOf<String?>(null) }
    val packageSnapshotStore = remember { PackageSnapshotStore() }
    val appInfoAgent = remember(executor) { DeviceAppInfoAgent(executor) }
    val appIconCache = remember { AppIconCache() }
    var packageSnapshotAt by remember { mutableStateOf<Long?>(null) }
    // 图标落盘后递增，用于让 PackageRow 重新解码图标（remember key 需要外部信号）
    var iconRevision by remember { mutableStateOf(0) }
    // 应用名/图标补全的失败或异常提示，原来静默吞异常导致一直显示包名
    var appInfoHint by remember { mutableStateOf<String?>(null) }
    var showMessagesPanel by remember { mutableStateOf(false) }
    var ownershipModeToConfirm by remember(device?.id) { mutableStateOf<DeviceOwnershipMode?>(null) }
    var ownershipFailure by remember(device?.id) { mutableStateOf<Pair<DeviceOwnershipMode, String>?>(null) }
    var detailPopupItem by remember { mutableStateOf<PackageDisplayItem?>(null) }
    var uninstallItem by remember(device?.id) { mutableStateOf<PackageDisplayItem?>(null) }
    var pendingDowngradeInstall by remember(device?.id) { mutableStateOf<ApkInstallRequest?>(null) }
    var apkInstallRequest by remember(device?.id) { mutableStateOf<ApkInstallRequest?>(null) }
    var installingApkFileName by remember(device?.id) { mutableStateOf<String?>(null) }
    var runningPackageNames by remember(device?.id) { mutableStateOf(emptySet<String>()) }
    var packageReloadVersion by remember(device?.id) { mutableStateOf(0) }
    // physicalDeviceKey 会在后台补齐设备序列号后变化，不能作为预览窗口的宿主 key，
    // 否则正在显示的窗口会被重新初始化为 false 并立刻关闭。
    var showDevicePreviewWindow by remember(device?.adbTarget()) { mutableStateOf(false) }
    val ownershipManager = remember(executor) { DeviceOwnershipManager(executor) }
    val appPackageExporter = remember(executor) { AppPackageExporter(executor) }

    val effectiveDevice = device
    val effectivePackageItems = if (device != null && executor != AdbCommandExecutor.create(null)) {
        localPackageDisplayItems.value
    } else {
        buildPackageDisplayItems(null, packages)
    }
    val effectiveLoading = if (device != null && executor != AdbCommandExecutor.create(null)) localPackageLoading.value else packageLoading
    val effectiveError = if (device != null && executor != AdbCommandExecutor.create(null)) localPackageError.value else packageError

    LaunchedEffect(device?.id, localPackageList.value, isVisible) {
        val target = device
        runningPackageNames = if (target != null && isVisible && target.isAvailableForDeviceManagement()) {
            loadRunningPackageNames(target, localPackageList.value, executor)
        } else {
            emptySet()
        }
    }

    LaunchedEffect(apkDropRequest?.token, device?.id) {
        val dropped = apkDropRequest ?: return@LaunchedEffect
        val target = device
        if (target == null || !target.isAvailableForDeviceManagement()) {
            onToast("设备未在线，无法安装 ${dropped.file.name}")
            onApkDropConsumed()
            return@LaunchedEffect
        }
        installingApkFileName = dropped.file.name
        val apkManifest = withContext(Dispatchers.IO) { AndroidManifest.create(dropped.file.absolutePath) }
        val packageName = apkManifest.packageName
        if (packageName.isBlank()) {
            installingApkFileName = null
            onToast("无法读取 APK 包名，请确认已安装 Android SDK Build Tools")
            onApkDropConsumed()
            return@LaunchedEffect
        }
        val installedApps = runCatching { PackageInspector.loadInstalledApps(executor, target.adbTarget()) }.getOrElse {
            installingApkFileName = null
            onToast("读取已安装应用失败：${it.message ?: "未知错误"}")
            onApkDropConsumed()
            return@LaunchedEffect
        }
        val installedApp = installedApps.firstOrNull { it.packageName == packageName }
        val request = ApkInstallRequest(
            file = dropped.file,
            packageName = packageName,
            versionCode = apkManifest.versionCode,
            versionName = apkManifest.versionName,
            existedBeforeInstall = installedApp != null,
            allowDowngrade = false,
            token = dropped.token,
        )
        installingApkFileName = null
        val installedVersionCode = installedApp?.versionCode?.toLongOrNull()
        if (installedVersionCode != null && apkManifest.versionCode < installedVersionCode) {
            pendingDowngradeInstall = request
        } else {
            apkInstallRequest = request
        }
        onApkDropConsumed()
    }

    LaunchedEffect(apkInstallRequest?.token) {
        val request = apkInstallRequest ?: return@LaunchedEffect
        installingApkFileName = request.file.name
        val installed = installApkFromComputer(device, request, executor, onToast)
        installingApkFileName = null
        if (installed && device != null) {
            updatePackageListAfterApkInstall(
                device = device,
                executor = executor,
                packageSnapshotStore = packageSnapshotStore,
                packageCache = packageCache,
                onAppsLoaded = { apps, displayItems, capturedAt ->
                    localPackageList.value = apps
                    localPackageDisplayItems.value = displayItems
                    packageSnapshotAt = capturedAt
                },
                onToast = onToast,
            )
        }
        apkInstallRequest = null
    }

    LaunchedEffect(effectiveDevice?.id, effectiveDevice?.adbTarget(), effectiveDevice?.status, effectiveDevice?.isManuallyDisconnected, effectiveDevice?.isRetainedOffline, isVisible, packageReloadVersion) {
        val dev = effectiveDevice ?: return@LaunchedEffect
        if (!isVisible) return@LaunchedEffect
        if (!dev.canManageDevice) {
            val snapshot = packageSnapshotStore.load(dev.id)
            val offlineApps = packageCache[dev.id].orEmpty().ifEmpty { snapshot?.apps.orEmpty() }
            localPackageList.value = offlineApps
            localPackageDisplayItems.value = buildPackageDisplayItems(null, offlineApps)
            packageSnapshotAt = snapshot?.capturedAt
            localPackageLoading.value = false
            localPackageError.value = if (offlineApps.isEmpty()) "当前设备不在线，且没有可用的应用快照。" else null
            return@LaunchedEffect
        }
        val previousSnapshot = packageSnapshotStore.load(dev.id)
        val cachedApps = packageCache[dev.id].orEmpty().ifEmpty { previousSnapshot?.apps.orEmpty() }
        localPackageList.value = cachedApps
        localPackageDisplayItems.value = buildPackageDisplayItems(null, cachedApps)
        packageSnapshotAt = previousSnapshot?.capturedAt
        localPackageLoading.value = true
        localPackageError.value = null
        try {
            val apps = PackageInspector.loadInstalledApps(executor, dev.adbTarget())
            localPackageList.value = apps
            localPackageDisplayItems.value = buildPackageDisplayItems(previousSnapshot?.apps, apps)
            packageCache[dev.id] = apps
            val initialCapturedAt = System.currentTimeMillis()
            packageSnapshotAt = initialCapturedAt
            packageSnapshotStore.save(PackageSnapshot(dev.id, initialCapturedAt, apps))
            localPackageLoading.value = false

            // 后台补全应用名与图标（已有 label 或已有磁盘图标时跳过 agent，避免重复开销）
            val missingLabel = apps.any { it.appLabel.isBlank() }
            val missingIcon = apps.any { !appIconCache.existsIcon(dev.id, it.packageName, it.versionCode) }
            if (missingLabel || missingIcon) {
                scope.launch {
                    val outcome = runCatching {
                        val enrichments = appInfoAgent.enrich(dev.adbTarget(), apps)
                        val labelByPkg = enrichments.associate { it.packageName to it.appLabel }
                        val enriched = apps.map { app ->
                            val l = labelByPkg[app.packageName]
                            if (!l.isNullOrBlank()) app.copy(appLabel = l) else app
                        }
                        enrichments.forEach { e ->
                            val png = e.iconPng ?: return@forEach
                            val ver = enriched.firstOrNull { it.packageName == e.packageName }?.versionCode.orEmpty()
                            appIconCache.save(dev.id, e.packageName, ver, png)
                        }
                        packageCache[dev.id] = enriched
                        localPackageList.value = enriched
                        localPackageDisplayItems.value = buildPackageDisplayItems(previousSnapshot?.apps, enriched)
                        val capturedAt = System.currentTimeMillis()
                        packageSnapshotAt = capturedAt
                        packageSnapshotStore.save(PackageSnapshot(dev.id, capturedAt, enriched))
                        enriched.count { it.appLabel.isNotBlank() }
                    }
                    val labeled = outcome.getOrNull()
                    if (labeled == null) {
                        val failure = outcome.exceptionOrNull()
                        val message = "应用名/图标补全失败：${failure?.message ?: failure?.javaClass?.simpleName}"
                        println("[DeviceAppInfoAgent] $message")
                        failure?.printStackTrace()
                        EnrichErrorLogger.log(dev.id, failure ?: RuntimeException(message))
                        appInfoHint = message
                    } else {
                        println("[DeviceAppInfoAgent] 补全完成 $labeled/${apps.size}")
                        appInfoHint = if (labeled == 0) {
                            "已执行补全但未获取到应用名：设备未执行 agent，请确认 adb 已授权且 /data/local/tmp 可写"
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (throwable: Throwable) {
            val offlineApps = previousSnapshot?.apps.orEmpty()
            localPackageList.value = offlineApps
            localPackageDisplayItems.value = emptyList()
            packageSnapshotAt = previousSnapshot?.capturedAt
            localPackageLoading.value = false
            localPackageError.value = throwable.message ?: "未知错误"
        }
    }
    val detailScrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.background)
            .padding(24.dp),
        contentAlignment = if (device == null) Alignment.Center else Alignment.TopStart,
    ) {
        if (device == null) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(detailScrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(detailScrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText("设备详情", style = TitleTextStyle)

                // 设备信息（Tab 制表符分隔，可复制）
                DeviceInfoText(device = device, modifier = Modifier.fillMaxWidth())

                // 快捷 Shell / 输入脚本 / 内联预览 / 文件浏览
                QuickShellButton(
                    device = device,
                    executor = executor,
                    scope = scope,
                    onToast = onToast,
                )
                InputScriptPanel(
                    device = device,
                    executor = executor,
                    scope = scope,
                )
                // 屏幕预览（左）+ 相机预览（右），同一卡片内并排，互不干扰
                InlineDevicePreview(
                    device = device,
                    executor = executor,
                    scope = scope,
                    settingsStore = settingsStore,
                    onOpenPreviewWindow = { showDevicePreviewWindow = true },
                )
                if (showDevicePreviewWindow) {
                    DevicePreviewWindow(
                        device = device,
                        executor = executor,
                        probeCache = probeCache,
                        lastContactAt = lastContactAt,
                        onAlwaysOnTopChanged = onPreviewAlwaysOnTopChanged,
                        onCloseRequest = { showDevicePreviewWindow = false },
                    )
                }
                DeviceFileBrowser(
                    device = device,
                    executor = executor,
                    scope = scope,
                    recentDebugApps = localPackageList.value,
                    onToast = onToast,
                )
                WifiControlPanel(
                    device = device,
                    executor = executor,
                    scope = scope,
                    onToast = onToast,
                    onRequestAppStatus = onRequestAppWifiStatus,
                )

                DeviceOwnershipPanel(
                    available = device.isAvailableForDeviceManagement(),
                    onSelectMode = { ownershipModeToConfirm = it },
                )

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
                                    device.displayName()
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
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        onAppListDropBoundsChanged(coordinates.boundsInWindow())
                    },
                    items = effectivePackageItems,
                    loading = effectiveLoading,
                    error = effectiveError,
                    snapshotAt = packageSnapshotAt,
                    deviceId = device.id,
                    iconCache = appIconCache,
                    iconRevision = iconRevision,
                    pinnedPackageNames = pinnedPackageNames,
                    hint = appInfoHint,
                    installingApkFileName = installingApkFileName,
                    onAppListDropBoundsChanged = onAppListDropBoundsChanged,
                    onReinstall = { item ->
                        scope.launch {
                            restoreUninstalledPackage(device, item.info, executor, onToast)
                        }
                    },
                    onExport = { item, directory ->
                        scope.launch {
                            exportInstalledPackage(device, item.info, directory, appPackageExporter, onToast)
                        }
                    },
                    onLaunch = { item ->
                        scope.launch {
                            launchInstalledPackage(device, item.info, executor, onToast)
                            runningPackageNames = loadRunningPackageNames(device, localPackageList.value, executor)
                        }
                    },
                    onStop = { item ->
                        scope.launch {
                            stopInstalledPackage(device, item.info, executor, onToast)
                            runningPackageNames = loadRunningPackageNames(device, localPackageList.value, executor)
                        }
                    },
                    runningPackageNames = runningPackageNames,
                    onRetry = { packageReloadVersion++ },
                    onRefreshRunningApps = {
                        scope.launch {
                            runningPackageNames = loadRunningPackageNames(device, localPackageList.value, executor)
                        }
                    },
                    onUninstall = { item -> uninstallItem = item },
                    onAppClick = { detailPopupItem = it },
                    onTogglePin = { item ->
                        val updated = pinnedPackageNames.toMutableSet().apply {
                            if (!add(item.info.packageName)) remove(item.info.packageName)
                        }
                        onPinnedPackageNamesChanged(device.id, updated)
                    },
                )
                detailPopupItem?.let { item ->
                    AppDetailPopup(
                        app = item.info,
                        item = item,
                        deviceId = device.id,
                        iconCache = appIconCache,
                        onDismiss = { detailPopupItem = null },
                        onExport = { directory ->
                            scope.launch {
                                exportInstalledPackage(device, item.info, directory, appPackageExporter, onToast)
                            }
                        },
                    )
                }
                uninstallItem?.let { item ->
                    AppDialog(
                        title = "卸载应用",
                        onDismiss = { uninstallItem = null },
                        confirmText = "卸载",
                        dismissText = "取消",
                        onDismissButton = { uninstallItem = null },
                        onConfirm = {
                            uninstallItem = null
                            scope.launch {
                                if (uninstallInstalledPackage(device, item.info, executor, onToast)) {
                                    val remainingApps = localPackageList.value.filterNot {
                                        it.packageName == item.info.packageName
                                    }
                                    val capturedAt = System.currentTimeMillis()
                                    localPackageList.value = remainingApps
                                    localPackageDisplayItems.value = buildPackageDisplayItems(
                                        packageSnapshotStore.load(device.id)?.apps,
                                        remainingApps,
                                    )
                                    packageCache[device.id] = remainingApps
                                    packageSnapshotAt = capturedAt
                                    packageSnapshotStore.save(PackageSnapshot(device.id, capturedAt, remainingApps))
                                }
                            }
                        },
                    ) {
                        AppText(item.info.displayName, style = BodyTextStyle.copy(fontWeight = FontWeight.Medium))
                        AppText(item.info.packageName, style = HintTextStyle)
                        AppText(
                            if (item.info.isSystemApp) {
                                "系统应用仅会从主用户移除，预装 APK 不会删除。"
                            } else {
                                "卸载会删除该应用及其本机数据。"
                            },
                            style = HintTextStyle.copy(color = AppTheme.danger),
                        )
                    }
                }
                pendingDowngradeInstall?.let { request ->
                    AppDialog(
                        title = "检测到旧版本 APK",
                        onDismiss = { pendingDowngradeInstall = null },
                        confirmText = "允许降级安装",
                        dismissText = "取消",
                        onDismissButton = { pendingDowngradeInstall = null },
                        onConfirm = {
                            pendingDowngradeInstall = null
                            apkInstallRequest = request.copy(
                                allowDowngrade = true,
                                token = System.nanoTime(),
                            )
                        },
                    ) {
                        AppText(request.file.name, style = BodyTextStyle.copy(fontWeight = FontWeight.Medium))
                        AppText(
                            "${request.packageName} 版本 ${request.versionName.ifBlank { request.versionCode.toString() }} 低于设备已安装版本。",
                            style = HintTextStyle,
                        )
                        AppText("继续将使用 adb install -r -d 安装。", style = HintTextStyle.copy(color = AppTheme.danger))
                    }
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(detailScrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
    ownershipModeToConfirm?.let { mode ->
        DeviceOwnershipProvisionDialog(
            mode = mode,
            deviceName = device?.displayName().orEmpty(),
            onDismiss = { ownershipModeToConfirm = null },
            onProvision = { component, userId ->
                val target = device ?: return@DeviceOwnershipProvisionDialog
                scope.launch {
                    ownershipModeToConfirm = null
                    val result = ownershipManager.provision(target.adbTarget(), mode, component, userId)
                    if (result.success) {
                        onToast(result.message)
                    } else {
                        ownershipFailure = mode to result.message
                    }
                }
            },
        )
    }
    ownershipFailure?.let { (mode, message) ->
        AppDialog(
            title = if (mode == DeviceOwnershipMode.DEVICE_OWNER) "设置 DO 失败" else "设置 PO 失败",
            onDismiss = { ownershipFailure = null },
            confirmText = "知道了",
            onConfirm = { ownershipFailure = null },
            dismissText = "关闭",
            onDismissButton = { ownershipFailure = null },
        ) {
            AppText(
                if (mode == DeviceOwnershipMode.DEVICE_OWNER) {
                    "DO 通常只允许在恢复出厂后的未配置设备上设置；会管理整台设备。"
                } else {
                    "PO 只管理指定用户或工作资料；请确认输入的是目标工作资料用户 ID。"
                },
                style = HintTextStyle.copy(color = AppTheme.danger),
                maxLines = 4,
            )
            AppText(message, style = HintTextStyle)
        }
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
    if (!device.canManageDevice) {
        onToast("设备未在线，无法恢复应用")
        return
    }
    if (app.isSystemApp) {
        val result = executor.shell(device.adbTarget(), "pm", "enable", app.packageName)
        onToast(if (result.isSuccess) "已请求恢复系统应用：${app.packageName}" else "恢复系统应用失败：${result.error.ifBlank { result.output }}")
        return
    }
    val manufacturer = device.manufacturer.ifBlank {
        executor.shell(device.adbTarget(), "getprop", "ro.product.manufacturer").output.trim()
    }
    val target = AppStoreLauncher.buildLaunchTarget(manufacturer, app.packageName)
    onToast("正在跳转${target.brandName}应用商店")
    val result = executor.shell(device.adbTarget(), *target.command.toTypedArray())
    val output = result.output.ifBlank { result.error }
    onToast(if (isStoreLaunchSuccessful(result.isSuccess, output)) "已打开${target.brandName}应用详情" else "${target.brandName}应用商店不可用：$output")
}

/** 将设备当前安装的应用 APK 集合导出到用户指定的电脑目录。 */
private suspend fun exportInstalledPackage(
    device: DeviceInfo?,
    app: InstalledAppInfo,
    directory: File,
    exporter: AppPackageExporter,
    onToast: (String) -> Unit,
) {
    if (device == null || !device.canManageDevice) {
        onToast("设备未在线，无法导出安装包")
        return
    }
    val result = runCatching { exporter.export(device.adbTarget(), app, directory) }
        .getOrElse { com.newchar.debug.pc.device.AppPackageExportResult(false, "导出失败：${it.message}") }
    onToast(result.message)
}

/** 通过 adb 卸载指定应用；系统应用仅从主用户移除，避免删除设备预装包。 */
private suspend fun uninstallInstalledPackage(
    device: DeviceInfo,
    app: InstalledAppInfo,
    executor: AdbCommandExecutor,
    onToast: (String) -> Unit,
): Boolean {
    if (!device.isAvailableForDeviceManagement()) {
        onToast("设备未在线，无法卸载应用")
        return false
    }
    val command = if (app.isSystemApp) {
        arrayOf("pm", "uninstall", "--user", "0", app.packageName)
    } else {
        arrayOf("pm", "uninstall", app.packageName)
    }
    val result = executor.shell(device.adbTarget(), *command)
    val output = result.output.ifBlank { result.error }.trim()
    val success = result.isSuccess && output.contains("Success", ignoreCase = true)
    val message = if (success) {
        if (app.isSystemApp) "已从主用户移除：${app.packageName}" else "已卸载：${app.packageName}"
    } else {
        "卸载失败：${output.ifBlank { "未返回结果" }}"
    }
    onToast(message)
    return success
}

/** 通过 adb 启动指定应用的 Launcher Activity；没有启动入口时提示用户。 */
private suspend fun launchInstalledPackage(
    device: DeviceInfo,
    app: InstalledAppInfo,
    executor: AdbCommandExecutor,
    onToast: (String) -> Unit,
) {
    if (!device.isAvailableForDeviceManagement()) {
        onToast("设备未在线，无法启动应用")
        return
    }
    val result = executor.shell(device.adbTarget(), "monkey", "-p", app.packageName, "-c", "android.intent.category.LAUNCHER", "1")
    val output = result.output.ifBlank { result.error }.trim()
    val failed = output.contains("error", ignoreCase = true) || output.contains("no activities", ignoreCase = true)
    onToast(if (result.isSuccess && !failed) "已启动：${app.displayName}" else "启动失败：${output.ifBlank { "未找到可启动入口" }}")
}

/** 通过 adb 强制停止应用及其全部进程。 */
private suspend fun stopInstalledPackage(
    device: DeviceInfo,
    app: InstalledAppInfo,
    executor: AdbCommandExecutor,
    onToast: (String) -> Unit,
) {
    if (!device.isAvailableForDeviceManagement()) {
        onToast("设备未在线，无法停止应用")
        return
    }
    val result = executor.shell(device.adbTarget(), "am", "force-stop", app.packageName)
    val output = result.output.ifBlank { result.error }.trim()
    onToast(if (result.isSuccess) "已停止：${app.displayName}" else "停止失败：${output.ifBlank { "未返回结果" }}")
}

/** 查询设备进程表，将应用主进程或远程进程映射为正在运行的包名集合。 */
private suspend fun loadRunningPackageNames(
    device: DeviceInfo,
    apps: List<InstalledAppInfo>,
    executor: AdbCommandExecutor,
): Set<String> {
    if (!device.isAvailableForDeviceManagement() || apps.isEmpty()) return emptySet()
    val result = executor.shell(device.adbTarget(), "ps", "-A")
    val output = if (result.isSuccess && result.output.isNotBlank()) result.output else executor.shell(device.adbTarget(), "ps").output
    val processNames = output.lineSequence()
        .map(String::trim)
        .filter { it.isNotBlank() && !it.startsWith("USER") }
        .map { it.split(Regex("\\s+")).last() }
        .toSet()
    return apps.asSequence().map(InstalledAppInfo::packageName).filter { packageName ->
        packageName in processNames || processNames.any { it.startsWith("$packageName:") }
    }.toSet()
}

/** 使用 adb 安装电脑端 APK；更新保留数据，允许的降级额外携带 -d 参数。 */
private suspend fun installApkFromComputer(
    device: DeviceInfo?,
    request: ApkInstallRequest,
    executor: AdbCommandExecutor,
    onToast: (String) -> Unit,
): Boolean {
    if (device == null || !device.isAvailableForDeviceManagement()) {
        onToast("设备未在线，无法安装 ${request.file.name}")
        return false
    }
    val args = buildList {
        addAll(listOf("-s", device.adbTarget(), "install"))
        if (request.existedBeforeInstall) add("-r")
        if (request.allowDowngrade) add("-d")
        add(request.file.absolutePath)
    }
    val result = executor.adb(*args.toTypedArray())
    val output = result.output.ifBlank { result.error }.trim()
    val success = result.isSuccess && output.contains("Success", ignoreCase = true)
    onToast(if (success) "已安装：${request.file.name}" else "安装失败：${output.ifBlank { "未返回结果" }}")
    return success
}

/** 安装完成后重新读取设备应用列表，并更新内存与磁盘快照。 */
private suspend fun updatePackageListAfterApkInstall(
    device: DeviceInfo,
    executor: AdbCommandExecutor,
    packageSnapshotStore: PackageSnapshotStore,
    packageCache: SnapshotStateMap<String, List<InstalledAppInfo>>,
    onAppsLoaded: (List<InstalledAppInfo>, List<PackageDisplayItem>, Long) -> Unit,
    onToast: (String) -> Unit,
) {
    val apps = runCatching { PackageInspector.loadInstalledApps(executor, device.adbTarget()) }.getOrElse {
        onToast("安装成功，但刷新应用列表失败：${it.message ?: "未知错误"}")
        return
    }
    val capturedAt = System.currentTimeMillis()
    val displayItems = buildPackageDisplayItems(packageSnapshotStore.load(device.id)?.apps, apps)
    packageCache[device.id] = apps
    packageSnapshotStore.save(PackageSnapshot(device.id, capturedAt, apps))
    onAppsLoaded(apps, displayItems, capturedAt)
}

/** 判断 am start 是否真正启动成功，兼容部分设备以 0 返回 Error 文本的情况。 */
private fun isStoreLaunchSuccessful(isSuccess: Boolean, output: String): Boolean =
    isSuccess && !output.contains("error", ignoreCase = true) && !output.contains("not found", ignoreCase = true)

/** 判断设备是否在线且未被用户标记为断开，可安全执行 dpm 查询或设置。 */
private fun DeviceInfo.isAvailableForDeviceManagement(): Boolean = canManageDevice

/** 对未授权的 Wi-Fi ADB 端点依次断开并重新连接，然后刷新设备扫描状态。 */
private suspend fun reconnectUnauthorizedWifiDevice(
    device: DeviceInfo,
    executor: AdbCommandExecutor,
    onToast: (String) -> Unit,
    onRefresh: suspend () -> Unit,
) {
    val endpoint = device.wirelessEndpoint.ifBlank { if (device.isNetworkDevice) device.id else "" }
    if (endpoint.isBlank()) {
        onToast("未获取到 Wi-Fi ADB 地址")
        return
    }
    executor.adb("disconnect", endpoint)
    val result = executor.adb("connect", endpoint)
    val output = result.output.ifBlank { result.error }.trim()
    val success = result.isSuccess || output.contains("connected to", ignoreCase = true) ||
        output.contains("already connected to", ignoreCase = true)
    onToast(if (success) "已重新连接：$endpoint" else "重新连接失败：${output.ifBlank { endpoint }}")
    onRefresh()
}

/** 提供 DO/PO 设置入口（需二次确认）。面板不再展示读回状态。 */
@Composable
private fun DeviceOwnershipPanel(
    available: Boolean,
    onSelectMode: (DeviceOwnershipMode) -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        AppText("设备管理（DO / PO）", style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
        if (!available) {
            AppText("设备未在线，无法执行 dpm 操作", style = HintTextStyle.copy(color = AppTheme.danger))
            return@PanelCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSmallOutlinedButton(text = "设为 DO", onClick = { onSelectMode(DeviceOwnershipMode.DEVICE_OWNER) })
            AppSmallOutlinedButton(text = "设为 PO", onClick = { onSelectMode(DeviceOwnershipMode.PROFILE_OWNER) })
        }
    }
}

/** 二次确认 DO/PO 部署参数，避免误将在线设备转换为受管设备。 */
@Composable
private fun DeviceOwnershipProvisionDialog(
    mode: DeviceOwnershipMode,
    deviceName: String,
    onDismiss: () -> Unit,
    onProvision: (component: String, userId: String) -> Unit,
) {
    var component by remember(mode) {
        mutableStateOf("com.newchar.debug.sample/com.newchar.debug.admin.DebugDeviceAdminReceiver")
    }
    var userId by remember(mode) { mutableStateOf("current") }
    var confirmation by remember(mode) { mutableStateOf("") }
    var validationMessage by remember(mode) { mutableStateOf<String?>(null) }
    AppDialog(
        title = "设置${mode.label}",
        onDismiss = onDismiss,
        confirmText = "确认设置",
        dismissText = "取消",
        onConfirm = {
            validationMessage = validateOwnershipConfirmation(mode, component, userId, confirmation)
            if (validationMessage == null) onProvision(component.trim(), userId.trim())
        },
        onDismissButton = onDismiss,
    ) {
        AppText("目标设备：$deviceName", style = BodyTextStyle.copy(fontWeight = FontWeight.Medium))
        AppText(ownershipWarning(mode), style = HintTextStyle.copy(color = AppTheme.danger), maxLines = 4)
        AppTextField(
            value = component,
            onValueChange = { component = it },
            placeholder = "包名/接收器类名",
            modifier = Modifier.fillMaxWidth(),
        )
        if (mode == DeviceOwnershipMode.PROFILE_OWNER) {
            AppTextField(
                value = userId,
                onValueChange = { userId = it },
                placeholder = "工作资料用户 ID 或 current",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AppText("输入 ${mode.confirmationToken} 确认继续", style = HintTextStyle)
        AppTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            placeholder = mode.confirmationToken,
            modifier = Modifier.fillMaxWidth(),
        )
        validationMessage?.let { message ->
            AppText(message, style = HintTextStyle.copy(color = AppTheme.danger))
        }
    }
}

/** 返回当前部署模式对应的不可逆影响说明。 */
private fun ownershipWarning(mode: DeviceOwnershipMode): String = when (mode) {
    DeviceOwnershipMode.DEVICE_OWNER ->
        "DO 通常只允许在恢复出厂后的未配置设备上设置；会管理整台设备。"
    DeviceOwnershipMode.PROFILE_OWNER ->
        "PO 只管理指定用户或工作资料；请确认输入的是目标工作资料用户 ID。"
}

/** 校验用户在确认框内输入的组件、用户和确认词。 */
private fun validateOwnershipConfirmation(
    mode: DeviceOwnershipMode,
    component: String,
    userId: String,
    confirmation: String,
): String? {
    if (component.isBlank() || !component.contains('/') || component.any(Char::isWhitespace)) {
        return "管理员组件格式应为 包名/接收器类名"
    }
    if (mode == DeviceOwnershipMode.PROFILE_OWNER && userId != "current" && userId.toIntOrNull() == null) {
        return "PO 用户应填写 current 或数字用户 ID"
    }
    return if (confirmation.trim() == mode.confirmationToken) null else "请输入 ${mode.confirmationToken} 确认"
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun PackageListPanel(
    modifier: Modifier = Modifier,
    items: List<PackageDisplayItem>,
    loading: Boolean,
    error: String?,
    snapshotAt: Long?,
    deviceId: String,
    iconCache: AppIconCache,
    iconRevision: Int = 0,
    pinnedPackageNames: Set<String> = emptySet(),
    hint: String? = null,
    installingApkFileName: String? = null,
    runningPackageNames: Set<String> = emptySet(),
    onAppListDropBoundsChanged: (Rect?) -> Unit,
    onReinstall: (PackageDisplayItem) -> Unit,
    onExport: (PackageDisplayItem, File) -> Unit,
    onLaunch: (PackageDisplayItem) -> Unit,
    onStop: (PackageDisplayItem) -> Unit,
    onRetry: () -> Unit,
    onRefreshRunningApps: () -> Unit = {},
    onUninstall: (PackageDisplayItem) -> Unit,
    onAppClick: (PackageDisplayItem) -> Unit = {},
    onTogglePin: (PackageDisplayItem) -> Unit = {},
) {
    val itemHeight = 76.dp
    val rowSpacing = 6.dp
    val minItemWidth = 280.dp
    val maxItemWidth = 420.dp
    var query by remember(deviceId) { mutableStateOf("") }
    var showFilterMenu by remember(deviceId) { mutableStateOf(false) }
    var selectedFilters by remember(deviceId) {
        mutableStateOf(setOf(AppListFilterOption.HIDE_ANDROID_NAMESPACE))
    }
    var filtersBeforeMenu by remember(deviceId) { mutableStateOf(selectedFilters) }
    var filterControlWidth by remember(deviceId) { mutableStateOf(0) }
    var appListWidth by remember(deviceId) { mutableStateOf(0) }
    var allAppsPointerEnteredAtNanos by remember(deviceId) { mutableStateOf(0L) }
    val filterMenuWidth = with(LocalDensity.current) { (filterControlWidth / 2).toDp() }
    val appListWidthDp = with(LocalDensity.current) { appListWidth.toDp() }
    val appListColumnCount = calculateAppListColumnCount(
        listWidth = appListWidthDp,
        minItemWidth = minItemWidth,
        maxItemWidth = maxItemWidth,
        itemSpacing = 12.dp,
    )
    val displayedItems = filterPackageDisplayItems(items, query, selectedFilters)
        .sortedByDescending { it.info.packageName in pinnedPackageNames }
    val runningItems = filterPackageDisplayItems(items, "", selectedFilters).filter {
        it.status == PackageStatus.CURRENT && it.info.packageName in runningPackageNames
    }
    DisposableEffect(Unit) {
        onDispose { onAppListDropBoundsChanged(null) }
    }
    PanelCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("应用列表", style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
            installingApkFileName?.let { fileName -> AppListInstallLoading(fileName) }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { filterControlWidth = it.width },
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppListFilterButton(
                    text = "过滤条件 (${selectedFilters.size}) ▼",
                    onClick = {
                        if (showFilterMenu) {
                            showFilterMenu = false
                        } else {
                            filtersBeforeMenu = selectedFilters
                            showFilterMenu = true
                        }
                    },
                )
                AppTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索应用名或包名",
                    modifier = Modifier.weight(1f),
                )
            }
            if (showFilterMenu && filterControlWidth > 0) {
                AppListFilterMenu(
                    width = filterMenuWidth,
                    selectedFilters = selectedFilters,
                    onToggleFilter = { option ->
                        selectedFilters = if (option in selectedFilters) selectedFilters - option else selectedFilters + option
                    },
                    onCancel = {
                        selectedFilters = filtersBeforeMenu
                        showFilterMenu = false
                    },
                    onDismiss = { showFilterMenu = false },
                )
            }
        }
        AppText("排序: Debug > 非系统应用 > 系统应用", style = HintTextStyle)
        AppText("可将单个 APK 拖入此区域安装", style = HintTextStyle.copy(color = AppTheme.accent))
        RunningAppsStrip(
            items = runningItems,
            deviceId = deviceId,
            iconCache = iconCache,
            iconRevision = iconRevision,
            onRefresh = onRefreshRunningApps,
            onReinstall = onReinstall,
            onExport = onExport,
            onLaunch = onLaunch,
            onStop = onStop,
            onUninstall = onUninstall,
            onAppClick = onAppClick,
            pinnedPackageNames = pinnedPackageNames,
            onTogglePin = onTogglePin,
        )
        if (!hint.isNullOrBlank()) {
            AppText(hint, style = HintTextStyle.copy(color = AppTheme.danger), maxLines = 3)
        }
        when {
            loading && displayedItems.isEmpty() -> AppListPlaceholder("应用信息加载中...", itemHeight, AppTheme.accent)
            !error.isNullOrBlank() -> AppListPlaceholder(
                message = "获取失败（$error），点击重新获取",
                height = itemHeight,
                color = AppTheme.danger,
                onClick = onRetry,
            )
            displayedItems.isEmpty() -> AppListPlaceholder(
                if (items.isEmpty()) "暂无可展示应用" else "没有匹配过滤条件的应用",
                itemHeight,
                AppTheme.textSecondary,
            )
            else -> {
                if (loading) {
                    AppText("正在后台刷新应用信息…", style = HintTextStyle.copy(color = AppTheme.accent))
                }
                AppText(
                    "共 ${displayedItems.size}/${items.size} 个应用",
                    style = HintTextStyle.copy(color = AppTheme.textSecondary),
                )
                snapshotAt?.let { capturedAt ->
                    AppText("快照时间：${formatFullTimestamp(capturedAt)}", style = HintTextStyle)
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { appListWidth = it.width }
                        .onPointerEvent(PointerEventType.Enter, PointerEventPass.Initial) {
                            allAppsPointerEnteredAtNanos = System.nanoTime()
                        }
                        .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                            val enteredForLessThan100Ms = System.nanoTime() - allAppsPointerEnteredAtNanos < 100_000_000L
                            if (enteredForLessThan100Ms) event.changes.forEach { it.consume() }
                        },
                ) {
                    LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = itemHeight, max = itemHeight * 5.5f + rowSpacing * 5),
                        verticalArrangement = Arrangement.spacedBy(rowSpacing),
                    ) {
                        items(items = displayedItems.chunked(appListColumnCount), key = { it.first().info.packageName }) { rowItems ->
                            AppListTwoColumnRow(
                                items = rowItems,
                                columnCount = appListColumnCount,
                                deviceId = deviceId,
                                iconCache = iconCache,
                                iconRevision = iconRevision,
                                onReinstall = onReinstall,
                                onExport = onExport,
                                onLaunch = onLaunch,
                                onStop = onStop,
                                onUninstall = onUninstall,
                                onAppClick = onAppClick,
                                pinnedPackageNames = pinnedPackageNames,
                                onTogglePin = onTogglePin,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 根据可用宽度与单项宽度阈值，计算完整应用列表每行应显示的列数。 */
private fun calculateAppListColumnCount(
    listWidth: androidx.compose.ui.unit.Dp,
    minItemWidth: androidx.compose.ui.unit.Dp,
    maxItemWidth: androidx.compose.ui.unit.Dp,
    itemSpacing: androidx.compose.ui.unit.Dp,
): Int {
    if (listWidth <= 0.dp) return 1
    val requiredColumns = kotlin.math.ceil(
        (listWidth.value + itemSpacing.value) / (maxItemWidth.value + itemSpacing.value),
    ).toInt().coerceAtLeast(1)
    val supportedColumns = kotlin.math.floor(
        (listWidth.value + itemSpacing.value) / (minItemWidth.value + itemSpacing.value),
    ).toInt().coerceAtLeast(1)
    return requiredColumns.coerceAtMost(supportedColumns)
}

/** 在应用列表中保留一行高度，并居中显示加载、空数据或失败提示。 */
@Composable
private fun AppListPlaceholder(
    message: String,
    height: androidx.compose.ui.unit.Dp,
    color: Color,
    onClick: (() -> Unit)? = null,
) {
    val interactionModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Box(
        modifier = Modifier.fillMaxWidth().height(height).then(interactionModifier),
        contentAlignment = Alignment.Center,
    ) {
        AppText(message, style = HintTextStyle.copy(color = color), maxLines = 2)
    }
}

/** 绘制横向滚动的正在运行应用列表，操作与下方完整应用列表保持一致。 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun RunningAppsStrip(
    items: List<PackageDisplayItem>,
    deviceId: String,
    iconCache: AppIconCache,
    iconRevision: Int,
    onRefresh: () -> Unit,
    onReinstall: (PackageDisplayItem) -> Unit,
    onExport: (PackageDisplayItem, File) -> Unit,
    onLaunch: (PackageDisplayItem) -> Unit,
    onStop: (PackageDisplayItem) -> Unit,
    onUninstall: (PackageDisplayItem) -> Unit,
    onAppClick: (PackageDisplayItem) -> Unit,
    pinnedPackageNames: Set<String>,
    onTogglePin: (PackageDisplayItem) -> Unit,
) {
    val runningAppsState = rememberLazyListState()
    var runningAppsPointerEnteredAtNanos by remember { mutableStateOf(0L) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText("正在运行 (${items.size})", style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
        AppSmallOutlinedButton(text = "刷新", onClick = onRefresh)
    }
    if (items.isEmpty()) {
        AppText("未检测到应用进程", style = HintTextStyle)
        return
    }
    LazyRow(
        state = runningAppsState,
        modifier = Modifier
            .onPointerEvent(PointerEventType.Enter) {
                runningAppsPointerEnteredAtNanos = System.nanoTime()
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val hasEnteredForAtLeast100Ms = System.nanoTime() - runningAppsPointerEnteredAtNanos >= 100_000_000L
                val verticalDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (hasEnteredForAtLeast100Ms && verticalDelta != 0f) {
                    runningAppsState.dispatchRawDelta(verticalDelta * 10f)
                    event.changes.forEach { it.consume() }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.info.packageName }) { item ->
            PackageRow(
                item, deviceId, iconCache, iconRevision, Modifier.width(280.dp),
                { onReinstall(item) }, { onExport(item, it) }, { onLaunch(item) },
                { onStop(item) }, { onUninstall(item) }, { onAppClick(item) },
                item.info.packageName in pinnedPackageNames, { onTogglePin(item) },
            )
        }
    }
}

/** 将一行内的应用按动态列数均分，并补齐最后一行的空白列。 */
@Composable
private fun AppListTwoColumnRow(
    items: List<PackageDisplayItem>,
    columnCount: Int,
    deviceId: String,
    iconCache: AppIconCache,
    iconRevision: Int,
    onReinstall: (PackageDisplayItem) -> Unit,
    onExport: (PackageDisplayItem, File) -> Unit,
    onLaunch: (PackageDisplayItem) -> Unit,
    onStop: (PackageDisplayItem) -> Unit,
    onUninstall: (PackageDisplayItem) -> Unit,
    onAppClick: (PackageDisplayItem) -> Unit,
    pinnedPackageNames: Set<String>,
    onTogglePin: (PackageDisplayItem) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item ->
            Box(modifier = Modifier.weight(1f)) {
                PackageRow(
                    item, deviceId, iconCache, iconRevision,
                    onReinstall = { onReinstall(item) },
                    onExport = { onExport(item, it) },
                    onLaunch = { onLaunch(item) },
                    onStop = { onStop(item) },
                    onUninstall = { onUninstall(item) },
                    onAppClick = { onAppClick(item) },
                    isPinned = item.info.packageName in pinnedPackageNames,
                    onTogglePin = { onTogglePin(item) },
                )
            }
        }
        repeat((columnCount - items.size).coerceAtLeast(0)) {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** 显示 APK 安装中的旋转状态与当前文件名。 */
@Composable
private fun AppListInstallLoading(fileName: String) {
    val transition = rememberInfiniteTransition(label = "apkInstallLoading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "apkInstallRotation",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AppText("◌", style = BodyTextStyle.copy(color = AppTheme.accent), modifier = Modifier.graphicsLayer { rotationZ = rotation })
        AppText("安装中：$fileName", style = HintTextStyle.copy(color = AppTheme.accent), maxLines = 1)
    }
}

/** 根据多选包名过滤条件与应用名、包名关键字筛选应用列表。 */
private fun filterPackageDisplayItems(
    items: List<PackageDisplayItem>,
    query: String,
    selectedFilters: Set<AppListFilterOption>,
): List<PackageDisplayItem> {
    val keyword = query.trim()
    return items.filter { item ->
        val packageName = item.info.packageName
        val isAndroidNamespace = packageName == "android" || packageName == "com.android" ||
            packageName.startsWith("com.android.")
        val appTypeFilters = selectedFilters.intersect(
            setOf(AppListFilterOption.SYSTEM_APP, AppListFilterOption.NON_SYSTEM_APP),
        )
        val buildTypeFilters = selectedFilters.intersect(
            setOf(AppListFilterOption.RELEASE_BUILD, AppListFilterOption.DEBUG_BUILD),
        )
        val matchesAppType = appTypeFilters.isEmpty() ||
            (item.info.isSystemApp && AppListFilterOption.SYSTEM_APP in appTypeFilters) ||
            (!item.info.isSystemApp && AppListFilterOption.NON_SYSTEM_APP in appTypeFilters)
        val matchesBuildType = buildTypeFilters.isEmpty() ||
            (item.info.isDebuggable && AppListFilterOption.DEBUG_BUILD in buildTypeFilters) ||
            (!item.info.isDebuggable && AppListFilterOption.RELEASE_BUILD in buildTypeFilters)
        val matchesNamespace = AppListFilterOption.HIDE_ANDROID_NAMESPACE !in selectedFilters || !isAndroidNamespace
        val matchesKeyword = keyword.isEmpty() || packageName.contains(keyword, ignoreCase = true) ||
            item.info.displayName.contains(keyword, ignoreCase = true)
        matchesNamespace && matchesAppType && matchesBuildType && matchesKeyword
    }
}

/** 格式化应用快照的完整时间。 */
private fun formatFullTimestamp(timestamp: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

@Composable
private fun PackageRow(
    item: PackageDisplayItem,
    deviceId: String,
    iconCache: AppIconCache,
    iconRevision: Int = 0,
    modifier: Modifier = Modifier,
    onReinstall: () -> Unit,
    onExport: (File) -> Unit,
    onLaunch: () -> Unit,
    onStop: () -> Unit,
    onUninstall: () -> Unit,
    onAppClick: () -> Unit = {},
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
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
    // iconRevision 变化（补全完成、图标落盘）时重新解码；IO 线程解码避免卡 UI
    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = "$deviceId|${app.packageName}",
        key2 = app.versionCode,
        key3 = iconRevision,
    ) {
        value = withContext(Dispatchers.IO) {
            iconCache.loadIconBitmap(deviceId, app.packageName, app.versionCode)
        }
    }
    ContextMenuArea(
        items = {
            if (isUninstalled) emptyList() else listOf(
                ContextMenuItem("启动 App") { onLaunch() },
                ContextMenuItem("停止 App") { onStop() },
                ContextMenuItem("导出安装包到电脑…") {
                    chooseExportDirectory()?.let(onExport)
                },
                ContextMenuItem(if (isPinned) "取消置顶" else "置顶") { onTogglePin() },
                ContextMenuItem("卸载应用…") { onUninstall() },
            )
        },
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (isUninstalled) 0.45f else 1f }
                .clickable { onAppClick() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(44.dp)) {
                AppIconThumbnail(
                    bitmap = iconBitmap,
                    label = app.displayName,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isPinned) {
                    AppText(
                        text = "📌",
                        style = TextStyle(fontSize = 12.sp),
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        text = app.displayName,
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.textPrimary),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    when (item.status) {
                        PackageStatus.UNINSTALLED -> OutlinedUninstalledLabel()
                        PackageStatus.NEW_INSTALLED -> AppText(
                            "新增",
                            style = HintTextStyle.copy(color = Color(0xFF2E8B57), fontWeight = FontWeight.SemiBold),
                        )
                        PackageStatus.CURRENT -> Unit
                    }
                    Spacer(Modifier.width(6.dp))
                    AppText(
                        text = badgeText,
                        style = HintTextStyle.copy(color = badgeColor, fontWeight = FontWeight.SemiBold),
                    )
                }
                AppText(
                    text = app.packageName,
                    style = TextStyle(fontSize = 12.sp, color = AppTheme.textSecondary),
                    maxLines = 1,
                )
                AppText(
                    text = buildPackageMetaLine(app),
                    style = HintTextStyle,
                    maxLines = 1,
                )
            }
            if (isUninstalled) {
                Spacer(Modifier.width(8.dp))
                AppSmallOutlinedButton(text = "重装", onClick = onReinstall)
            }
        }
    }
}

/** 构造应用行第三行的元数据：版本号 + 更新时间；缺一退化为仅显示另一项。 */
private fun buildPackageMetaLine(app: InstalledAppInfo): String {
    val versionPart = buildString {
            append(app.versionName.ifBlank { "-" })
            if (app.versionCode.isNotBlank()) {
                append(" (")
                append(app.versionCode)
                append(')')
            }
        }
    return when {
        app.lastUpdateTime.isNotBlank() -> "$versionPart  ·  更新 ${formatShortDateTime(app.lastUpdateTime)}"
        app.firstInstallTime.isNotBlank() -> "$versionPart  ·  安装 ${formatShortDateTime(app.firstInstallTime)}"
        else -> versionPart
    }
}

/** 尝试将 dumpsys 的时间字符串解析为短日期时间；解析失败则原样返回。 */
private fun formatShortDateTime(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return runCatching {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val date = parser.parse(trimmed) ?: return trimmed
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(date)
    }.getOrDefault(trimmed)
}

/** 应用图标缩略图：优先使用磁盘缓存的 PNG，否则显示基于包名哈希的彩色首字母占位。 */
@Composable
internal fun AppIconThumbnail(
    bitmap: ImageBitmap?,
    label: String,
    modifier: Modifier = Modifier,
) {
    val initial = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    val background = remember(label) { paletteColor(label, light = true) }
    val foreground = remember(label) { paletteColor(label, light = false) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = label,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = initial,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = foreground),
            )
        }
    }
}

/** 基于字符串哈希的稳定调色板（同一包名永远拿到同一颜色）。 */
internal fun paletteColor(seed: String, light: Boolean): Color {
    val hash = seed.fold(0L) { acc, c -> (acc * 31L + c.code).and(0x7fffffffL) }
    val palette = if (light) {
        longArrayOf(0xFFE6EEFFL, 0xFFE9F7EFL, 0xFFFFF3E0L, 0xFFFDECEAL, 0xFFEDE7F6L, 0xFFE0F7FAL)
    } else {
        longArrayOf(0xFF3566D6L, 0xFF2E8B57L, 0xFFC97A1EL, 0xFFC44536L, 0xFF6A4FB6L, 0xFF11838AL)
    }
    val idx = (hash % palette.size).toInt()
    return Color(palette[idx])
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
            "${device.displayName()} 的 USB 已断开，是否尝试无线连接？",
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
    currentCameraAppPackage: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var adbPath by remember(currentAdbPath) { mutableStateOf(currentAdbPath) }
    var cameraAppPackage by remember(currentCameraAppPackage) { mutableStateOf(currentCameraAppPackage) }
    AppDialog(
        title = "设置",
        onDismiss = onDismiss,
        confirmText = "保存",
        dismissText = "取消",
        onConfirm = { onSave(adbPath, cameraAppPackage) },
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
        AppText("相机 App 包名", style = BodyTextStyle.copy(fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(10.dp))
        AppTextField(
            value = cameraAppPackage,
            onValueChange = { cameraAppPackage = it },
            placeholder = "com.newchar.debug.sample",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        AppText("相机预览/拍照将远程启动该包内的 ScreenRecordService。", style = HintTextStyle)
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

/** 展示扫描明细，并提供设备、mDNS 和局域网的即时刷新入口。 */
@Composable
private fun ScanEventsDialog(
    recentChanges: List<DeviceChangeEvent>,
    mdnsServices: List<AdbMdnsService>,
    recentMdnsMessages: List<String>,
    recentLanMessages: List<String>,
    scannedLanSubnets: List<String>,
    discoveredLanEndpoints: List<String>,
    onDismiss: () -> Unit,
    onRefreshDevices: () -> Unit,
    onRefreshMdns: () -> Unit,
    onRefreshLan: () -> Unit,
) {
    AppDialog(title = "事件", onDismiss = onDismiss, confirmText = "关闭", onConfirm = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSmallOutlinedButton(text = "刷新设备", onClick = onRefreshDevices)
            AppSmallOutlinedButton(text = "扫描 mDNS", onClick = onRefreshMdns)
            AppSmallOutlinedButton(text = "扫描 LAN", onClick = onRefreshLan)
        }
        Column(modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
            ScanEventRows(recentChanges)
            ScanMdnsRows(mdnsServices, recentMdnsMessages)
            ScanLanRows(scannedLanSubnets, discoveredLanEndpoints, recentLanMessages)
        }
    }
}

/** 绘制本次会话中保留的设备连接变化及其具体字段差异。 */
@Composable
private fun ColumnScope.ScanEventRows(events: List<DeviceChangeEvent>) {
    AppText("扫描事件", style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
    if (events.isEmpty()) {
        AppText("暂无设备连接变化", style = HintTextStyle)
    } else {
        events.forEach { event ->
            AppText(
                "${formatFullTimestamp(event.timestampMs)} · ${event.source.name}",
                style = HintTextStyle,
                maxLines = 1,
            )
            AppText(event.summary, style = BodyTextStyle, maxLines = 3)
            Spacer(Modifier.height(6.dp))
        }
    }
    AppDivider()
    Spacer(Modifier.height(10.dp))
}

/** 绘制当前 mDNS 服务及最近一次自动连接结果。 */
@Composable
private fun ColumnScope.ScanMdnsRows(services: List<AdbMdnsService>, messages: List<String>) {
    AppText("mDNS 服务", style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
    if (services.isEmpty()) AppText("暂无发现", style = HintTextStyle)
    services.forEach { service ->
        AppText("${service.endpoint} · ${service.serviceType}", style = BodyTextStyle, maxLines = 2)
    }
    if (messages.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        AppText("mDNS 连接记录", style = HintTextStyle.copy(fontWeight = FontWeight.SemiBold))
        messages.forEach { AppText(it, style = HintTextStyle, maxLines = 2) }
    }
    AppDivider(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp))
    Spacer(Modifier.height(10.dp))
}

/** 绘制 LAN 扫描的子网、端点及连接结果。 */
@Composable
private fun ColumnScope.ScanLanRows(subnets: List<String>, endpoints: List<String>, messages: List<String>) {
    AppText("LAN 扫描", style = BodyTextStyle.copy(fontWeight = FontWeight.SemiBold))
    AppText("子网：${subnets.ifEmpty { listOf("暂无扫描记录") }.joinToString()}", style = HintTextStyle, maxLines = 3)
    AppText("端点：${endpoints.ifEmpty { listOf("暂无发现") }.joinToString()}", style = HintTextStyle, maxLines = 3)
    if (messages.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        messages.forEach { AppText(it, style = HintTextStyle, maxLines = 2) }
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
                    text = "${formatTimestamp(event.timestampMs)} · ${event.summary}",
                    style = HintTextStyle.copy(color = AppTheme.textSecondary),
                    maxLines = 2,
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

/** 显示与搜索框等高的应用列表过滤下拉触发按钮。 */
@Composable
private fun AppListFilterButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.panelAlt)
            .border(1.dp, AppTheme.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(text, style = BodyTextStyle)
    }
}

/** 显示悬浮的应用列表多选条件，并在点击浮层外时保留当前选择。 */
@Composable
private fun AppListFilterMenu(
    width: androidx.compose.ui.unit.Dp,
    selectedFilters: Set<AppListFilterOption>,
    onToggleFilter: (AppListFilterOption) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = AppListFilterPopupPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(width)
                .clip(RoundedCornerShape(8.dp))
                .background(AppTheme.panel)
                .border(1.dp, AppTheme.border, RoundedCornerShape(8.dp))
                .padding(vertical = 4.dp),
        ) {
            AppListFilterOption.values().forEach { option ->
                AppListFilterMenuItem(
                    option = option,
                    selected = option in selectedFilters,
                    onClick = { onToggleFilter(option) },
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSmallOutlinedButton(text = "取消", onClick = onCancel)
            }
        }
    }
}

/** 显示单个可切换的应用列表过滤条件。 */
@Composable
private fun AppListFilterMenuItem(
    option: AppListFilterOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) AppTheme.accent else AppTheme.panel)
                .border(1.dp, AppTheme.border, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) AppText("✓", style = HintTextStyle.copy(color = Color.White))
        }
        AppText(option.label, style = BodyTextStyle)
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
