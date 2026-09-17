package com.newchar.debug.pc.device.preview

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newchar.debug.pc.config.DesktopAppSettingsStore
import com.newchar.debug.pc.device.CameraServiceManager
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.device.stream.StreamKind
import com.newchar.debug.pc.device.stream.StreamSessionCoordinator
import com.newchar.debug.pc.device.stream.StreamSessionConfig
import com.newchar.debug.pc.device.stream.StreamSessionKey
import com.newchar.debug.pc.device.stream.StreamTransportEvent
import com.newchar.debug.pc.device.stream.StreamTransportState
import com.newchar.debug.pc.device.scan.JvmWifiDetector
import com.newchar.debug.pc.executor.AdbCommandExecutor
import com.newchar.debug.pc.ui.AppText
import com.newchar.debug.pc.ui.AppTheme
import com.newchar.debug.pc.ui.chooseApkFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.util.UUID

/** am 已成功但迟迟收不到首帧的超时时间，超过则提示检查相机权限。 */
private const val FIRST_FRAME_TIMEOUT_MS = 20_000L

/** 相机面板状态机。 */
internal sealed class CameraPanelState {
    object Idle : CameraPanelState()
    data class Checking(val message: String) : CameraPanelState()
    data class NeedInstall(val message: String) : CameraPanelState()
    data class Installing(val message: String) : CameraPanelState()
    object Connecting : CameraPanelState()
    object Receiving : CameraPanelState()
    object Streaming : CameraPanelState()
    data class Error(val message: String) : CameraPanelState()
}

/** 相机面板顶部状态栏文案。 */
private fun cameraStatusText(state: CameraPanelState): String = when (state) {
    is CameraPanelState.Idle -> "未开启"
    is CameraPanelState.Checking -> state.message
    is CameraPanelState.NeedInstall -> "待安装 App"
    is CameraPanelState.Installing -> state.message
    is CameraPanelState.Connecting -> "等待设备连接…"
    is CameraPanelState.Receiving -> "正在解码…"
    is CameraPanelState.Streaming -> "预览中"
    is CameraPanelState.Error -> "出错"
}

/**
 * 与屏幕预览左右并排的相机面板。
 *
 * 流程：检测宿主 App →（缺失则引导安装 APK）→ adb reverse:6668 → 远程启动设备端相机服务
 * → 接收 H264 预览流；推流中可全分辨率拍照（结果 pull 到本地并提供「打开文件夹」）。
 * 面板不可用时只占一格占位，不影响左侧屏幕预览。
 */
@Composable
internal fun CameraPreviewPanel(
    device: DeviceInfo,
    executor: AdbCommandExecutor,
    settingsStore: DesktopAppSettingsStore,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val adbTarget = device.adbTarget()
    val currentAdbTarget by rememberUpdatedState(adbTarget)
    // 面板首次进入时固定物理设备所有者键；切换 ADB 链路不应触发收流 socket 的清理，
    // 否则 USB 断开后的局域网直连没有可接管的监听端。
    val previewOwnerKey = remember { device.physicalDeviceKey() }
    val directStreamHost = JvmWifiDetector.wifiIp.orEmpty()
    var cameraState by remember(previewOwnerKey) { mutableStateOf<CameraPanelState>(CameraPanelState.Idle) }
    var currentFrame by remember(previewOwnerKey) { mutableStateOf<ImageBitmap?>(null) }
    var installedPackage by remember(previewOwnerKey) { mutableStateOf<String?>(null) }
    var capturing by remember(previewOwnerKey) { mutableStateOf(false) }
    var switchingCamera by remember(previewOwnerKey) { mutableStateOf(false) }
    var stoppingCamera by remember(previewOwnerKey) { mutableStateOf(false) }
    var streamSession by remember(previewOwnerKey) { mutableStateOf(0) }
    var photoPath by remember(previewOwnerKey) { mutableStateOf<String?>(null) }
    var previewRotation by remember(previewOwnerKey) { mutableStateOf(0) }

    val cameraServiceManager = remember(executor) { CameraServiceManager(executor) }
    val cameraStreamServer = remember(previewOwnerKey) { CameraStreamServer() }
    val streamCoordinator = remember(previewOwnerKey) { StreamSessionCoordinator() }
    val streamKey = remember(previewOwnerKey) { StreamSessionKey(previewOwnerKey, StreamKind.CAMERA) }
    val transportState by streamCoordinator.stateOf(streamKey).collectAsState()

    fun resolvePackage(): String =
        installedPackage ?: settingsStore.loadSync().cameraAppPackage.ifBlank { "com.newchar.debug.sample" }

    /** 停止设备端相机服务、关闭本地收流并移除 adb reverse 映射。 */
    fun stopStreaming() {
        if (stoppingCamera) return
        stoppingCamera = true
        streamSession++
        cameraState = CameraPanelState.Idle
        currentFrame = null
        photoPath = null
        previewRotation = 0
        switchingCamera = false
        capturing = false
        scope.launch {
            val result = cameraServiceManager.stopCameraService(adbTarget, resolvePackage())
            cameraServiceManager.removeReverse(adbTarget)
            cameraStreamServer.close()
            streamCoordinator.dispatch(streamKey, StreamTransportEvent.Stopped)
            stoppingCamera = false
            if (!result.first) {
                cameraState = CameraPanelState.Error("停止相机失败：${result.second}")
            }
        }
    }

    suspend fun beginStreaming(pkg: String) {
        if (stoppingCamera || cameraState is CameraPanelState.Streaming || cameraState is CameraPanelState.Connecting) return
        streamSession++
        val session = streamSession
        cameraState = CameraPanelState.Checking("正在点亮屏幕并检测锁屏…")
        val keyguardResult = runCatching {
            cameraServiceManager.wakeAndDismissKeyguard(adbTarget)
        }.getOrElse {
            cameraState = CameraPanelState.Error("锁屏状态检测失败：${it.message ?: "adb 不可用"}")
            return
        }
        if (!keyguardResult.success) {
            cameraState = CameraPanelState.Error("设备仍处于锁屏状态，请手动解锁后重试")
            return
        }
        cameraState = CameraPanelState.Connecting
        currentFrame = null
        previewRotation = 0
        // 关闭上一次（可能失败的）尝试遗留的流服务器，避免端口被自身占用导致「端口占用」
        runCatching { cameraStreamServer.close() }
        // 1) 先在本机绑定并监听 6668，确保设备连接时本机已在等待
        //    （消除旧实现「先远程启动设备、后本机才监听」导致的连接竞态 → 未收到流数据）
        val token = createStreamToken()
        val sessionId = UUID.randomUUID().toString()
        val streamPort = runCatching {
            withContext(Dispatchers.IO) {
                cameraStreamServer.start(ExpectedStreamHello(StreamKind.CAMERA, token))
            }
        }.getOrDefault(0)
        if (streamPort <= 0) {
            cameraState = CameraPanelState.Error(
                "无法启动相机流监听端口，请检查本机网络权限后重试"
            )
            return
        }
        // 2) 启动帧接收循环（此时端口已就绪）
        scope.launch {
            runCatching {
                cameraStreamServer.collectFrames(
                    onFrame = { frame ->
                        scope.launch frameUpdate@{
                            if (stoppingCamera || session != streamSession) return@frameUpdate
                            currentFrame = frame
                            if (cameraState !is CameraPanelState.Streaming) {
                                cameraState = CameraPanelState.Streaming
                            }
                        }
                    },
                    onError = { err ->
                        scope.launch {
                            if (!stoppingCamera && session == streamSession && cameraState !is CameraPanelState.Error) {
                                cameraState = CameraPanelState.Error("相机解码失败: $err")
                            }
                        }
                    },
                    onConnected = {
                        scope.launch {
                            if (!stoppingCamera && session == streamSession && cameraState is CameraPanelState.Connecting) {
                                cameraState = CameraPanelState.Receiving
                            }
                        }
                    },
                    onRotationChanged = { rotation ->
                        scope.launch {
                            if (!stoppingCamera && session == streamSession) previewRotation = rotation
                        }
                    },
                    onTransportChanged = { transport ->
                        streamCoordinator.dispatch(streamKey, StreamTransportEvent.FirstFrame(sessionId, transport, directStreamHost))
                    },
                    onFrameReceived = {
                        streamCoordinator.dispatch(streamKey, StreamTransportEvent.FrameReceived(sessionId))
                    },
                    onDisconnected = { transport ->
                        streamCoordinator.dispatch(streamKey, StreamTransportEvent.SocketLost(sessionId, transport))
                    },
                )
            }.onFailure { throwable ->
                if (!stoppingCamera && session == streamSession && throwable !is CancellationException && cameraState !is CameraPanelState.Error) {
                    cameraState = CameraPanelState.Error("相机流接收失败: ${throwable.message}")
                }
            }
        }
        // 3) ADB 在线时始终优先 reverse；direct endpoint 仅作为 reverse 失效后的设备端回退。
        val reversePort = cameraServiceManager.setupReversePort(adbTarget, streamPort, streamPort)
        if (reversePort == null) {
            cameraState = CameraPanelState.Error("无法建立相机 adb reverse 端口，请确认 ADB 可用")
            return
        }
        val transportConfig = StreamSessionConfig(
            sessionId = sessionId,
            kind = StreamKind.CAMERA,
            directHost = directStreamHost.ifBlank { "127.0.0.1" },
            directPort = streamPort,
            reversePort = reversePort,
            token = token,
            allowDirectFallback = directStreamHost.isNotBlank(),
        )
        val configured = cameraServiceManager.configureStreamTransport(adbTarget, pkg, transportConfig)
        if (!configured.success && directStreamHost.isNotBlank()) {
            cameraState = CameraPanelState.Error("无法下发相机断线续流配置：${configured.reason}")
            streamCoordinator.dispatch(streamKey, StreamTransportEvent.Failed(sessionId, configured.reason))
            return
        }
        streamCoordinator.dispatch(streamKey, StreamTransportEvent.Started(sessionId))
        // 4) 最后启动相机服务，旧端仍会使用此 reverse-only 入口。
        val result = cameraServiceManager.startCameraService(adbTarget, pkg, "127.0.0.1")
        if (!result.success) {
            cameraState = CameraPanelState.Error(result.reason.ifBlank { "启动失败：${result.rawOutput}" })
            streamCoordinator.dispatch(streamKey, StreamTransportEvent.Failed(sessionId, result.reason))
        }
    }

    // 进入面板只做「宿主 App 是否安装」的探测，不自动拉起相机：
    // 开相机会点亮设备摄像头并常驻前台服务，必须由用户显式点击「打开相机」触发。
    LaunchedEffect(adbTarget) {
        if (cameraState is CameraPanelState.Connecting ||
            cameraState is CameraPanelState.Receiving ||
            cameraState is CameraPanelState.Streaming) return@LaunchedEffect
        val pkg = resolvePackage()
        installedPackage = pkg
        cameraState = CameraPanelState.Checking("正在检测宿主 App…")
        val installed = runCatching { cameraServiceManager.isAppInstalled(adbTarget, pkg) }.getOrDefault(false)
        cameraState = if (installed) {
            CameraPanelState.Idle
        } else {
            CameraPanelState.NeedInstall("未检测到 App，请选择 APK 安装")
        }
    }

    // 对连接阶段与解码阶段分别超时，避免「已收到 H264 数据」被误报为权限问题。
    LaunchedEffect(cameraState) {
        val waitingForConnection = cameraState is CameraPanelState.Connecting
        val waitingForDecode = cameraState is CameraPanelState.Receiving
        if (!waitingForConnection && !waitingForDecode) return@LaunchedEffect
        delay(FIRST_FRAME_TIMEOUT_MS)
        if (waitingForConnection && cameraState is CameraPanelState.Connecting) {
            cameraState = CameraPanelState.Error(
                "服务已启动但未收到预览流，请检查相机权限、adb reverse 和设备端服务日志"
            )
        } else if (waitingForDecode && cameraState is CameraPanelState.Receiving) {
            cameraState = CameraPanelState.Error(
                "已收到 H264 相机流但未解出画面，请更新 PC 工具和设备端 App 后重试"
            )
        }
    }

    DisposableEffect(previewOwnerKey) {
        onDispose {
            runCatching {
                cameraStreamServer.close()
                installedPackage?.let { pkg ->
                    scope.launch { cameraServiceManager.stopCameraService(currentAdbTarget, pkg) }
                }
                scope.launch { cameraServiceManager.removeReverse(currentAdbTarget) }
            }
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                "相机预览",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.textPrimary),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText(
                    "${cameraStatusText(cameraState)} · ${streamTransportLabel(transportState)}",
                    style = TextStyle(fontSize = 11.sp, color = AppTheme.textSecondary),
                    maxLines = 1,
                )
                if (cameraState is CameraPanelState.Connecting ||
                    cameraState is CameraPanelState.Receiving ||
                    cameraState is CameraPanelState.Streaming) {
                    MiniAction(if (stoppingCamera) "停止中…" else "停止相机", enabled = !stoppingCamera) {
                        stopStreaming()
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, AppTheme.border, RoundedCornerShape(10.dp))
                .background(AppTheme.panelAlt),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = cameraState) {
                is CameraPanelState.Streaming -> {
                    val frame = currentFrame
                    if (frame != null) {
                        Image(
                            bitmap = frame,
                            contentDescription = "相机实时画面",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                rotationZ = previewRotation.toFloat()
                            },
                        )
                    } else {
                        AppText("等待首帧…", style = hintTextStyle())
                    }
                    // 拍照按钮浮在预览右下角，未拿到照片前只显示「拍照」
                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        photoPath?.let { path ->
                            MiniAction("打开文件夹") { runCatching { Desktop.getDesktop().open(File(path).parentFile) } }
                        }
                        MiniAction(if (switchingCamera) "切换中…" else "切换摄像头", enabled = !capturing && !switchingCamera) {
                            switchingCamera = true
                            scope.launch {
                                val result = cameraServiceManager.switchCamera(adbTarget, resolvePackage())
                                switchingCamera = false
                                if (result.first) {
                                    currentFrame = null
                                    previewRotation = 0
                                    cameraState = CameraPanelState.Connecting
                                } else {
                                    cameraState = CameraPanelState.Error("切换摄像头失败：${result.second}")
                                }
                            }
                        }
                        MiniAction(if (capturing) "拍摄中…" else "拍照", enabled = !capturing && !switchingCamera) {
                            capturing = true
                            photoPath = null
                            scope.launch {
                                val result = runCatching {
                                    cameraServiceManager.capturePhoto(adbTarget, resolvePackage())
                                }.getOrNull()
                                capturing = false
                                if (result != null) {
                                    photoPath = result
                                } else {
                                    cameraState = CameraPanelState.Error("拍照失败：未获取到照片（请确认相机正在预览）")
                                }
                            }
                        }
                    }
                }

                is CameraPanelState.NeedInstall -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText(state.message, style = hintTextStyle())
                    Spacer(Modifier.height(8.dp))
                    MiniAction("选择 APK 安装") {
                        val apk = chooseApkFile() ?: return@MiniAction
                        scope.launch {
                            cameraState = CameraPanelState.Installing("正在安装…")
                            val result = cameraServiceManager.installApp(adbTarget, apk)
                            if (!result.success) {
                                cameraState = CameraPanelState.Error("安装失败: ${result.message}")
                            } else {
                                beginStreaming(resolvePackage())
                            }
                        }
                    }
                }

                is CameraPanelState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText(
                        state.message,
                        style = TextStyle(fontSize = 11.sp, color = AppTheme.danger),
                        maxLines = 4,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniAction("重试") {
                            scope.launch {
                                cameraState = CameraPanelState.Checking("正在重新检测…")
                                val pkg = resolvePackage()
                                val installed = runCatching {
                                    cameraServiceManager.isAppInstalled(adbTarget, pkg)
                                }.getOrDefault(false)
                                if (!installed) {
                                    cameraState = CameraPanelState.NeedInstall("未检测到 App，请选择 APK 安装")
                                } else {
                                    beginStreaming(pkg)
                                }
                            }
                        }
                        // App 已安装但版本过旧时（典型：Service 仍是 exported=false），
                        // 让用户在 PC 侧直接重装最新 APK，不必回到命令行。
                        MiniAction("更新 App") {
                            val apk = chooseApkFile() ?: return@MiniAction
                            scope.launch {
                                cameraState = CameraPanelState.Installing("正在更新…")
                                val result = cameraServiceManager.installApp(adbTarget, apk)
                                if (!result.success) {
                                    cameraState = CameraPanelState.Error("安装失败: ${result.message}")
                                } else {
                                    beginStreaming(resolvePackage())
                                }
                            }
                        }
                    }
                }

                is CameraPanelState.Idle -> MiniAction("打开相机") {
                    scope.launch { beginStreaming(resolvePackage()) }
                }

                else -> AppText(
                    when (state) {
                        is CameraPanelState.Checking -> state.message
                        is CameraPanelState.Installing -> state.message
                        is CameraPanelState.Connecting -> "等待设备相机连接…"
                        is CameraPanelState.Receiving -> "已连接，正在解析相机画面…"
                        else -> "准备中…"
                    },
                    style = hintTextStyle(),
                )
            }
        }
    }
}

private fun hintTextStyle(): TextStyle = TextStyle(fontSize = 12.sp, color = AppTheme.textHint)

/** 相机面板内的小号操作按钮，适配半宽预览格。 */
@Composable
private fun MiniAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) AppTheme.accent else AppTheme.panelAlt)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text,
            style = TextStyle(
                fontSize = 11.sp,
                color = if (enabled) Color.White else AppTheme.textHint,
            ),
        )
    }
}