package com.newchar.debug.pc.device

import com.newchar.debug.pc.config.DesktopAppSettingsStore
import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * 通过推送一个本地 dex 到设备，由系统上下文直接查询应用名与应用图标。
 *
 * 实现：在设备上以 shell 身份运行 [com.newchar.probe.Main]（打包在
 * `src/jvmMain/resources/agent/ncprobe.dex` 中），反射调用
 * `ActivityThread.systemMain().getSystemContext().getPackageManager()`，获取
 *  `getApplicationLabel()` 与 `getApplicationIcon()` 的真实结果，覆盖所有应用（含
 *  自适应矢量图标）。
 *
 * 输出协议：每行 `<package>|<label>|<base64PNG>`，错误时 label 为 `ERR:<异常类名>`。
 */
class DeviceAppInfoAgent(
    private val executor: CommandExecutor,
    private val configDirectory: String = DesktopAppSettingsStore.configDirectoryPath(),
) {
    private val dexResourcePath = "/agent/ncprobe.dex"

    /** 单次补全结果；[iconPng] 可能为 `null`（图标提取失败时）。 */
    data class Enrichment(
        val packageName: String,
        val appLabel: String,
        val iconPng: ByteArray?,
    )

    /** 按应用名+版本号缓存的图标（无图标也写入占位文件表示已尝试）。 */
    suspend fun enrich(
        deviceId: String,
        apps: List<InstalledAppInfo>,
    ): List<Enrichment> = withContext(Dispatchers.IO) {
        try {
            if (apps.isEmpty()) return@withContext emptyList()
            val remoteDexPath = ensureAgentPushed(deviceId)
            val args = buildList {
                add("CLASSPATH=$remoteDexPath")
                add("app_process")
                add("/data/local/tmp")
                add(MAIN_CLASS)
                apps.forEach { add(it.packageName) }
            }
            val result = executor.shell(deviceId, *args.toTypedArray())
            check(result.isSuccess) {
                "执行应用信息 agent 失败: ${result.error.ifBlank { result.output }}"
            }
            parseOutput(result.output, apps)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            EnrichErrorLogger.log(deviceId, throwable)
            emptyList()
        }
    }

    /** 推送 agent dex；按内容 MD5 缓存到 configDirectory/agent/，同名则跳过。 */
    private suspend fun ensureAgentPushed(deviceId: String): String {
        val dexBytes = loadDex() ?: error("内置 agent dex 缺失: $dexResourcePath")
        val md5 = md5Hex(dexBytes)
        val agentDir = File(configDirectory, "agent").also { it.mkdirs() }
        val cachedFile = File(agentDir, "ncprobe_$md5.dex")
        if (!cachedFile.isFile) {
            cachedFile.writeBytes(dexBytes)
        }
        val remotePath = "/data/local/tmp/ncprobe_$md5.dex"
        val lastMarker = File(agentDir, "last_$deviceId.txt")
        val lastMd5 = if (lastMarker.isFile) lastMarker.readText().trim() else ""
        if (lastMd5 != md5) {
            val push = executor.adb("-s", deviceId, "push", cachedFile.absolutePath, remotePath)
            check(push.isSuccess) { "推送 agent dex 失败: ${push.error.ifBlank { push.output }}" }
            lastMarker.writeText(md5)
        }
        return remotePath
    }

    private fun parseOutput(output: String, apps: List<InstalledAppInfo>): List<Enrichment> {
        val requested = apps.associateBy { it.packageName }
        val enrichments = LinkedHashMap<String, Enrichment>(apps.size)
        output.lineSequence().forEach { line ->
            val parts = line.split('|', limit = 3)
            if (parts.size < 2) return@forEach
            val pkg = parts[0].trim()
            val rawLabel = parts[1].trim()
            val iconB64 = parts.getOrNull(2)?.trim().orEmpty()
            if (pkg.isBlank() || rawLabel.isBlank()) return@forEach
            if (!requested.containsKey(pkg)) return@forEach
            val label = if (rawLabel.startsWith("ERR:")) "" else rawLabel
            val png = if (iconB64.isBlank()) null else runCatching {
                Base64.getDecoder().decode(iconB64)
            }.getOrNull()
            enrichments[pkg] = Enrichment(pkg, label, png)
        }
        // 输出里缺失的包也保留（label="", icon=null），便于上层做覆盖判定。
        apps.forEach { app ->
            if (enrichments[app.packageName] == null) {
                enrichments[app.packageName] = Enrichment(app.packageName, "", null)
            }
        }
        return apps.map { enrichments.getValue(it.packageName) }
    }

    private fun loadDex(): ByteArray? {
        val stream = javaClass.getResourceAsStream(dexResourcePath) ?: return null
        return stream.use { it.readBytes() }
    }

    private fun md5Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAIN_CLASS = "com.newchar.probe.Main"
    }
}
