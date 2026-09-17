package com.newchar.debug.pc.device

import com.newchar.debug.pc.executor.AdbCommandExecutor
import com.newchar.debug.pc.device.stream.StreamSessionConfig
import com.newchar.debug.pc.device.stream.StreamKind
import com.newchar.debug.pc.device.preview.encodeStreamToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.logging.Logger

/**
 * 设备端相机服务的生命周期管理：检测宿主 App 是否安装、缺失时引导安装、
 * 设置 adb reverse、远程启动/停止相机服务，以及全分辨率拍照（拍照后 adb pull 到本地）。
 *
 * 所有方法均为挂起函数：底层 [AdbCommandExecutor.adb] 已在 [Dispatchers.IO] 上执行。
 */
class CameraServiceManager(private val executor: AdbCommandExecutor) {

    companion object {
        private val Log: Logger = Logger.getLogger(CameraServiceManager::class.java.name)
        private const val SERVICE_CLASS = "com.newchar.debug.touch.ScreenRecordService"
        private const val ACTION_START = "com.newchar.debug.touch.action.START_CAMERA"
        private const val ACTION_STOP = "com.newchar.debug.touch.action.STOP_CAMERA"
        private const val ACTION_SWITCH = "com.newchar.debug.touch.action.SWITCH_CAMERA"
        private const val ACTION_PHOTO = "com.newchar.debug.touch.action.CAPTURE_PHOTO"
        private const val ACTION_CONFIGURE_STREAM_TRANSPORT = "com.newchar.debug.touch.action.CONFIGURE_STREAM_TRANSPORT"
        private const val EXTRA_CAMERA_STREAM_HOST = "com.newchar.debug.touch.extra.CAMERA_STREAM_HOST"
        private const val EXTRA_STREAM_KIND = "com.newchar.debug.touch.extra.STREAM_KIND"
        private const val EXTRA_DIRECT_HOST = "com.newchar.debug.touch.extra.DIRECT_HOST"
        private const val EXTRA_DIRECT_PORT = "com.newchar.debug.touch.extra.DIRECT_PORT"
        private const val EXTRA_REVERSE_PORT = "com.newchar.debug.touch.extra.REVERSE_PORT"
        private const val EXTRA_SESSION_TOKEN = "com.newchar.debug.touch.extra.SESSION_TOKEN"
        private const val EXTRA_ALLOW_DIRECT_FALLBACK = "com.newchar.debug.touch.extra.ALLOW_DIRECT_FALLBACK"
        private const val CAMERA_STREAM_PORT = 6668
        private const val PHOTO_DIR = "/sdcard/DCIM/Camera"
        private const val PHOTO_PREFIX = "ncam_"
        // am 输出中标识失败的关键词（am 失败时退出码常仍为 0，只能靠输出判断）
        private val FAILURE_KEYWORDS = listOf(
            "error", "not found", "not exported",
            "permission denial", "denied", "exception", "background"
        )
        private const val PHOTO_POLL_TIMEOUT_MS = 12_000L
        private const val PHOTO_POLL_INTERVAL_MS = 400L
        private const val KEYGUARD_ACTION_INTERVAL_MS = 350L
        private val DISPLAY_SIZE_REGEX = Regex("""(\\d+)x(\\d+)""")
    }

    /** 检查宿主 App 是否已安装（pm path 输出包含 package: 即视为已安装）。 */
    suspend fun isAppInstalled(deviceId: String, packageName: String): Boolean {
        val result = executor.adb("-s", deviceId, "shell", "pm", "path", packageName)
        return result.isSuccess && result.output.contains("package:")
    }

    /** 点亮屏幕并尝试解除无认证锁屏；安全锁屏不会被绕过，仍锁定时返回失败结果。 */
    suspend fun wakeAndDismissKeyguard(deviceId: String): KeyguardDismissResult {
        executor.adb("-s", deviceId, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
        delay(KEYGUARD_ACTION_INTERVAL_MS)
        if (!isKeyguardShowing(deviceId)) return KeyguardDismissResult(true, false)
        executor.adb("-s", deviceId, "shell", "wm", "dismiss-keyguard")
        delay(KEYGUARD_ACTION_INTERVAL_MS)
        if (!isKeyguardShowing(deviceId)) return KeyguardDismissResult(true, true)
        executor.adb("-s", deviceId, "shell", "input", "keyevent", "KEYCODE_MENU")
        delay(KEYGUARD_ACTION_INTERVAL_MS)
        if (!isKeyguardShowing(deviceId)) return KeyguardDismissResult(true, true)
        swipeToDismissKeyguard(deviceId)
        delay(KEYGUARD_ACTION_INTERVAL_MS)
        return KeyguardDismissResult(!isKeyguardShowing(deviceId), true)
    }

    /** 从 window policy 中读取 keyguard 展示状态，兼容常见 Android 系统字段。 */
    private suspend fun isKeyguardShowing(deviceId: String): Boolean {
        val result = executor.adb("-s", deviceId, "shell", "dumpsys", "window", "policy")
        val content = (result.output + "\n" + result.error).lowercase()
        return content.contains("isstatusbarkeyguard=true") || content.contains("mshowinglockscreen=true")
    }

    /** 按设备实际分辨率注入一次从下向上的滑动，用于解除 Swipe 类型锁屏。 */
    private suspend fun swipeToDismissKeyguard(deviceId: String) {
        val sizeResult = executor.adb("-s", deviceId, "shell", "wm", "size")
        val size = DISPLAY_SIZE_REGEX.find(sizeResult.output)?.groupValues ?: return
        val width = size[1].toIntOrNull() ?: return
        val height = size[2].toIntOrNull() ?: return
        executor.adb("-s", deviceId, "shell", "input", "swipe", (width / 2).toString(),
            (height * 4 / 5).toString(), (width / 2).toString(), (height / 5).toString(), "250")
    }

    /** 安装 APK：-r 覆盖安装，-g 自动授予所有运行时权限（含 CAMERA）。 */
    suspend fun installApp(deviceId: String, apkPath: String): InstallResult {
        val file = File(apkPath)
        if (!file.exists()) {
            return InstallResult(false, "APK 文件不存在: $apkPath")
        }
        val result = executor.adb("-s", deviceId, "install", "-r", "-g", apkPath)
        val success = result.isSuccess && result.output.contains("Success")
        return InstallResult(success, if (success) "安装成功" else result.output.ifBlank { result.error })
    }

    /**
     * 设置 adb reverse，将设备 6668 端口映射到 PC 6668，使相机流可达 PC 服务。
     * 幂等：若本工具已存在相同映射则直接复用（避免「端口占用」）；
     * 仅在新增映射冲突（上次未清理的残留）时才先移除再重建；最终返回是否建立成功。
     */
    suspend fun setupReverse(deviceId: String): Boolean {
        return setupReversePort(deviceId, CAMERA_STREAM_PORT, CAMERA_STREAM_PORT) != null
    }

    /** 将设备侧 reverse 端口映射到 PC 实际监听端口；两端端口可不同。 */
    suspend fun setupReverse(deviceId: String, devicePort: Int, pcPort: Int): Boolean {
        return setupReversePort(deviceId, devicePort, pcPort) != null
    }

    /** 依次探测设备侧备用端口，返回可用端口；PC 端口保持为既有 listener。 */
    suspend fun setupReversePort(deviceId: String, preferredDevicePort: Int, pcPort: Int): Int? {
        return (0..7).firstNotNullOfOrNull { offset ->
            val devicePort = preferredDevicePort + offset
            if (setupReverseAt(deviceId, devicePort, pcPort)) devicePort else null
        }
    }

    /** 在指定设备端口建立 reverse；残留映射会先清理一次后重试。 */
    private suspend fun setupReverseAt(deviceId: String, devicePort: Int, pcPort: Int): Boolean {
        // 与已可用的录屏推流（6667）保持同一写法：直接下发 adb reverse，
        // “already used” 视为映射已就绪（幂等），不再用 --list 预判，避免边界误判。
        val add = executor.adb("-s", deviceId, "reverse", "tcp:$devicePort", "tcp:$pcPort")
        if (add.isSuccess || add.error.contains("already used", ignoreCase = true)) {
            Log.info("adb reverse tcp:$devicePort -> tcp:$pcPort 已就绪")
            return true
        }
        // 存在冲突映射（多为上次未清理的残留）：移除后重建
        executor.adb("-s", deviceId, "reverse", "--remove", "tcp:$devicePort")
        val retry = executor.adb("-s", deviceId, "reverse", "tcp:$devicePort", "tcp:$pcPort")
        if (!retry.isSuccess) {
            Log.warning("建立相机 adb reverse 失败: ${retry.error.ifBlank { retry.output }}")
        }
        return retry.isSuccess
    }

    /** 在 ADB 可用时将 direct endpoint、reverse endpoint 与 token 写入设备端内存。 */
    suspend fun configureStreamTransport(
        deviceId: String,
        packageName: String,
        config: StreamSessionConfig,
    ): ServiceStartResult {
        val component = "$packageName/$SERVICE_CLASS"
        val kind = if (config.kind == StreamKind.CAMERA) "camera" else "screen"
        val args = arrayOf(
            "shell", "am", "startservice", "-n", component, "-a", ACTION_CONFIGURE_STREAM_TRANSPORT,
            "--es", EXTRA_STREAM_KIND, kind,
            "--es", EXTRA_DIRECT_HOST, config.directHost,
            "--ei", EXTRA_DIRECT_PORT, config.directPort.toString(),
            "--ei", EXTRA_REVERSE_PORT, config.reversePort.toString(),
            "--es", EXTRA_SESSION_TOKEN, encodeStreamToken(config.token),
            "--ez", EXTRA_ALLOW_DIRECT_FALLBACK, config.allowDirectFallback.toString(),
        )
        val result = executor.adb("-s", deviceId, *args)
        val raw = listOf(result.output, result.error).filter(String::isNotBlank).joinToString("\n").trim()
        return ServiceStartResult(isAmSuccess(result), raw, if (isAmSuccess(result)) "" else parseStartFailure(raw, packageName))
    }

    /** 移除 adb reverse 映射。 */
    suspend fun removeReverse(deviceId: String) {
        executor.adb("-s", deviceId, "reverse", "--remove", "tcp:$CAMERA_STREAM_PORT")
    }

    /**
     * 通过 am 向设备端相机服务投递 Action。
     *
     * 兼容处理两点：
     * 1) `am` 出错时退出码常仍为 0，因此额外检查输出中的 error / not found / not exported 等关键词；
     * 2) **必须优先用 start-foreground-service**：Android 8+ 对后台启动服务有限制，
     *    `am startservice` 会被静默拒绝——它照样打印 `Starting service: Intent { ... }` 且 exitCode=0，
     *    但目标进程根本不会起来。因此不能「先 startservice 失败再回退」，只能反过来。
     *    Android 7 及以下没有 start-foreground-service 子命令，届时再回退到 startservice。
     *
     * 返回 Pair(是否成功, 原始输出)。
     */
    private suspend fun deliverAction(
        deviceId: String,
        packageName: String,
        action: String,
        streamHost: String? = null,
    ): Pair<Boolean, String> {
        val component = "$packageName/$SERVICE_CLASS"
        val hostExtra = streamHost?.takeIf(String::isNotBlank)?.let { listOf("--es", EXTRA_CAMERA_STREAM_HOST, it) }.orEmpty()
        val foreground = executor.adb(
            "-s", deviceId, "shell", "am", "start-foreground-service", "-n", component, "-a", action, *hostExtra.toTypedArray()
        )
        if (isAmSuccess(foreground)) {
            return true to foreground.output.trim()
        }
        val plain = executor.adb("-s", deviceId, "shell", "am", "startservice", "-n", component, "-a", action, *hostExtra.toTypedArray())
        if (isAmSuccess(plain)) {
            return true to plain.output.trim()
        }
        val raw = listOf(foreground.output, foreground.error, plain.output, plain.error)
            .filter(String::isNotBlank)
            .joinToString("\n")
            .trim()
        return false to raw
    }

    /** 向已运行服务投递控制指令，避免错误触发新的前台服务启动时限。 */
    private suspend fun deliverRunningServiceAction(
        deviceId: String,
        packageName: String,
        action: String,
    ): Pair<Boolean, String> {
        val component = "$packageName/$SERVICE_CLASS"
        val result = executor.adb("-s", deviceId, "shell", "am", "startservice", "-n", component, "-a", action)
        val output = listOf(result.output, result.error).filter(String::isNotBlank).joinToString("\n").trim()
        return isAmSuccess(result) to output
    }

    /** am 命令成功判定：退出码为 0 且输出不含失败关键词。 */
    private fun isAmSuccess(result: CommandResult): Boolean {
        if (!result.isSuccess) {
            return false
        }
        val lower = (result.output + "\n" + result.error).lowercase()
        return FAILURE_KEYWORDS.none { lower.contains(it) }
    }

    /**
     * 通过 am 远程启动设备端相机服务（无需 MediaProjection 授权）。
     * 失败时给出中文可读原因，便于直接在 UI 上展示。
     */
    suspend fun startCameraService(deviceId: String, packageName: String, streamHost: String): ServiceStartResult {
        val (ok, raw) = deliverAction(deviceId, packageName, ACTION_START, streamHost)
        return if (ok) {
            ServiceStartResult(success = true, rawOutput = raw)
        } else {
            ServiceStartResult(success = false, rawOutput = raw, reason = parseStartFailure(raw, packageName))
        }
    }

    /** 解析 am startservice 的失败输出，返回人类可读的中文原因。 */
    private fun parseStartFailure(output: String, packageName: String): String {
        val lower = output.lowercase()
        return when {
            lower.contains("not found") || lower.contains("not exported") ->
                "服务未找到或未导出。请确认：1) 包名 $packageName 是否正确；" +
                    "2) 宿主 App 是否已集成最新 lib_debug_touch；" +
                    "3) AndroidManifest 中 ScreenRecordService 是否已声明且 exported=\"true\"。"
            lower.contains("permission denial") || lower.contains("denied") ->
                "权限拒绝。请确认设备已授权 USB 调试；" +
                    "相机权限需设备端授予（可尝试 adb install -r -g 重新安装自动授权）。"
            lower.contains("background") ->
                "后台启动服务被系统限制，设备端可能未进入前台服务模式。请重试或检查设备 Android 版本。"
            lower.contains("exception") ->
                "设备端异常：$output"
            else ->
                "启动失败，adb 输出：${output.ifBlank { "（无输出）" }}"
        }
    }

    /** 通过 am 远程停止设备端相机服务。 */
    suspend fun stopCameraService(deviceId: String, packageName: String): Pair<Boolean, String> =
        deliverRunningServiceAction(deviceId, packageName, ACTION_STOP)

    /** 请求设备端切换前后摄像头；成功后服务会自动重建预览会话并重新连接 PC。 */
    suspend fun switchCamera(deviceId: String, packageName: String): Pair<Boolean, String> =
        deliverRunningServiceAction(deviceId, packageName, ACTION_SWITCH)

    /**
     * 请求设备端拍摄全分辨率照片，等待保存完成后 adb pull 到本地临时目录。
     * 采用「拍照前快照已有文件 → 轮询等待新文件出现」策略，避免固定延时导致的竞态。
     * 返回本地文件路径；失败或超时返回 null。
     */
    suspend fun capturePhoto(deviceId: String, packageName: String): String? = withContext(Dispatchers.IO) {
        // 确保 reverse 仍有效（窗口关闭后可能被清理）
        setupReverse(deviceId)
        val before = listPhotoDirs(deviceId, packageName).flatMap { listPhotos(deviceId, it) }.toSet()
        val (delivered, reason) = deliverAction(deviceId, packageName, ACTION_PHOTO)
        if (!delivered) {
            Log.warning("发送拍照指令失败: $reason")
        }
        val deadline = System.currentTimeMillis() + PHOTO_POLL_TIMEOUT_MS
        var remoteFile: String? = null
        while (System.currentTimeMillis() < deadline) {
            remoteFile = listPhotoDirs(deviceId, packageName)
                .flatMap { listPhotos(deviceId, it) }
                .firstOrNull { it !in before }
            if (remoteFile != null) break
            delay(PHOTO_POLL_INTERVAL_MS)
        }
        remoteFile ?: return@withContext null
        val localDir = File(System.getProperty("java.io.tmpdir"), "pc-debug-tools-photos").apply { mkdirs() }
        val localFile = File(localDir, File(remoteFile).name)
        val pull = executor.adb("-s", deviceId, "pull", remoteFile, localFile.absolutePath)
        if (pull.isSuccess && localFile.exists()) localFile.absolutePath else null
    }

    /**
     * 照片可能落在两个目录：App 私有 DCIM（无需存储权限，主路径）与公开 DCIM（回退路径）。
     * 两个都查，避免设备端走了回退时 PC 侧找不到新文件。
     */
    private fun listPhotoDirs(deviceId: String, packageName: String): List<String> = listOf(
        "/sdcard/Android/data/$packageName/files/DCIM",
        PHOTO_DIR,
    )

    /** 列出指定目录下的 ncam_*.jpg（按时间倒序）。 */
    private suspend fun listPhotos(deviceId: String, dir: String): List<String> {
        val result = executor.adb("-s", deviceId, "shell", "ls", "-t", "$dir/$PHOTO_PREFIX*.jpg")
        if (!result.isSuccess) return emptyList()
        return result.output.lineSequence()
            .map { it.trim() }
            .filter { it.endsWith(".jpg") }
            .toList()
    }
}

data class InstallResult(val success: Boolean, val message: String)

/** 锁屏处理结果：成功代表屏幕已可用，hadKeyguard 代表处理前检测到锁屏。 */
data class KeyguardDismissResult(val success: Boolean, val hadKeyguard: Boolean)

/** 服务启动结果，包含成功标志、原始 adb 输出与中文失败原因。 */
data class ServiceStartResult(
    val success: Boolean,
    val rawOutput: String,
    val reason: String = "",
)