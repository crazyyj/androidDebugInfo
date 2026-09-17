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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newchar.debug.pc.config.DesktopAppSettingsStore
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.executor.AdbCommandExecutor
import com.newchar.debug.pc.ui.AppText
import com.newchar.debug.pc.ui.AppTheme
import com.newchar.debug.pc.ui.PanelCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/** 预览帧刷新间隔：预浏览不是远程操控，10 秒一帧足够定位界面状态。 */
private const val PREVIEW_INTERVAL_MS = 10_000L

/** 预览区固定高度；开启相机并排后加高，保证左右两格都看得清。 */
private val PREVIEW_HEIGHT = 220.dp

/** 形如 `com.android.chrome/.Main` 的包名/组件片段。 */
private val COMPONENT_REGEX = Regex("""([A-Za-z][A-Za-z0-9_.$]*)/([A-Za-z0-9_.$]+)""")

/**
 * 读取当前前台应用包名。
 *
 * 用整段 `dumpsys activity activities` 在 PC 侧解析，而不是在设备侧 `| grep`：
 * 部分定制 ROM 的 toybox grep 行为不一致，且把全量输出拉回来还能同时兼容
 * `mResumedActivity` / `ResumedActivity` 两种字段名。
 */
suspend fun queryForegroundPackage(
    executor: AdbCommandExecutor,
    deviceId: String,
): String? = withContext(Dispatchers.IO) {
    val result = runCatching {
        executor.shell(deviceId, "dumpsys", "activity", "activities")
    }.getOrNull() ?: return@withContext null
    val text = result.output.ifBlank { result.error }
    text.lineSequence()
        .firstOrNull { it.contains("ResumedActivity", ignoreCase = true) }
        ?.let { line -> COMPONENT_REGEX.find(line)?.groupValues?.getOrNull(1) }
}

/** 单次屏幕截图的显示位图与原始 PNG 数据。 */
data class CapturedScreenFrame(
    val bitmap: ImageBitmap,
    val pngBytes: ByteArray,
)

/** 抓取一帧 PNG，返回可显示位图及用于缓存的原始 PNG 字节。 */
suspend fun captureScreenFrame(
    executor: AdbCommandExecutor,
    deviceId: String,
): CapturedScreenFrame? = withContext(Dispatchers.IO) {
    val process = runCatching {
        ProcessBuilder(executor.resolveAdbCommand("-s", deviceId, "exec-out", "screencap", "-p")).start()
    }.getOrNull() ?: return@withContext null
    try {
        val bytes = process.inputStream.use { it.readBytes() }
        if (process.waitFor() != 0 || bytes.isEmpty()) return@withContext null
        runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }
            .getOrNull()
            ?.let { bitmap -> CapturedScreenFrame(bitmap, bytes) }
    } finally {
        process.destroy()
        if (process.isAlive) process.destroyForcibly()
    }
}

/**
 * 内联在设备详情里的预览：屏幕在左、相机在右，两者独立运行互不干扰。
 *
 * 传入 [settingsStore] 即启用右侧相机面板（不传则只显示屏幕预览，行为与改造前一致）。
 * 相机侧需要远程拉起设备端服务，因此默认不自动开启，由用户在面板内点「打开相机」触发。
 */
@Composable
fun InlineDevicePreview(
    device: DeviceInfo,
    executor: AdbCommandExecutor,
    scope: CoroutineScope,
    settingsStore: DesktopAppSettingsStore? = null,
    onOpenPreviewWindow: () -> Unit = {},
) {
    val online = device.canManageDevice
    val adbTarget = device.adbTarget()
    val screenFrameCache = remember { ScreenFrameCache() }
    var frame by remember(adbTarget) { mutableStateOf<ImageBitmap?>(null) }
    var foregroundPackage by remember(adbTarget) { mutableStateOf<String?>(null) }
    var hint by remember(adbTarget) { mutableStateOf<String?>(if (online) "等待首帧…" else "设备离线，暂无预览") }

    LaunchedEffect(device.id, adbTarget, online) {
        val cachedFrame = screenFrameCache.load(device.id)
        if (cachedFrame != null) {
            frame = cachedFrame
            hint = if (online) "正在刷新截图…" else "设备离线，显示最近截图"
        }
        if (!online) {
            foregroundPackage = null
            if (cachedFrame == null) hint = "设备离线，暂无预览"
            return@LaunchedEffect
        }
        while (true) {
            foregroundPackage = runCatching { queryForegroundPackage(executor, adbTarget) }.getOrNull()
            val captured = runCatching { captureScreenFrame(executor, adbTarget) }.getOrNull()
            if (captured != null) {
                frame = captured.bitmap
                screenFrameCache.save(device.id, captured.pngBytes)
            }
            hint = if (captured == null) "截图失败：请确认设备已解锁且 adb 已授权" else null
            delay(PREVIEW_INTERVAL_MS)
        }
    }

    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                if (settingsStore == null) "屏幕预览" else "屏幕预览 / 相机预览",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.textPrimary),
            )
            AppText(
                foregroundPackage ?: "前台未知",
                style = TextStyle(fontSize = 11.sp, color = AppTheme.textSecondary),
                maxLines = 1,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT)) {
            // 左：屏幕预览
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    "屏幕",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.textPrimary),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, AppTheme.border, RoundedCornerShape(10.dp))
                        .background(AppTheme.panelAlt)
                        .clickable(onClick = onOpenPreviewWindow),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = frame
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "设备屏幕预览",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        AppText(
                            hint ?: "等待屏幕…",
                            style = TextStyle(fontSize = 12.sp, color = AppTheme.textHint),
                        )
                    }
                }
            }
            // 右：相机预览（可选）
            if (settingsStore != null) {
                Spacer(Modifier.width(10.dp))
                CameraPreviewPanel(
                    device = device,
                    executor = executor,
                    settingsStore = settingsStore,
                    scope = scope,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
