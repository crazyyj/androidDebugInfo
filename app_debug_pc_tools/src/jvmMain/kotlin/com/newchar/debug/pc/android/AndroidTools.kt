package com.newchar.debug.pc.android

import java.io.File

class AndroidManifest private constructor(private val apkFilePath: String) {

    var packageName: String = ""
        private set
    var versionCode: Long = 0L
        private set
    var minVersion: Int = 0
        private set
    var versionName: String = ""
        private set
    var permissions: List<String> = emptyList()
        private set
    var parseError: String = ""
        private set

    init {
        parseApkFile()
    }

    companion object {
        fun create(apkPath: String): AndroidManifest {
            return AndroidManifest(apkPath)
        }

        /** 从 aapt 的 package 行中读取包名。 */
        internal fun parsePackageName(lineString: String): String {
            val split = lineString.split("'", limit = 3)
            return if (split.size >= 2) split[1] else ""
        }
    }

    /** 调用本机 Android SDK 的 aapt 读取 APK 基础信息。 */
    private fun parseApkFile() {
        val apkFile = File(apkFilePath)
        if (!apkFile.isFile || !apkFile.isAbsolute) {
            parseError = "APK 文件不存在"
            return
        }
        val output = aaptCommands(apkFile).firstNotNullOfOrNull(::runAaptBadging)
        if (output == null) {
            parseError = "未找到可用的 aapt"
            return
        }
        val packageLine = output.lineSequence().firstOrNull { it.startsWith("package:") }.orEmpty()
        packageName = parsePackageName(packageLine)
        versionCode = readPackageAttribute(packageLine, "versionCode").toLongOrNull() ?: 0L
        versionName = readPackageAttribute(packageLine, "versionName")
        minVersion = output.lineSequence().firstOrNull { it.startsWith("sdkVersion:") }
            ?.substringAfter('\'')?.substringBefore('\'')?.toIntOrNull() ?: 0
        if (packageName.isBlank()) parseError = "aapt 未返回有效包名"
    }

    /** 生成 PATH 与常见 Android SDK 目录中的 aapt 调用命令。 */
    private fun aaptCommands(apkFile: File): List<List<String>> {
        val executable = if (System.getProperty("os.name").contains("win", ignoreCase = true)) "aapt.exe" else "aapt"
        val roots = listOfNotNull(
            System.getenv("ANDROID_SDK_ROOT"),
            System.getenv("ANDROID_HOME"),
            System.getProperty("user.home")?.let { "$it${File.separator}Library${File.separator}Android${File.separator}sdk" },
            System.getProperty("user.home")?.let { "$it${File.separator}Android${File.separator}Sdk" },
        )
        val paths = buildList {
            add(executable)
            roots.distinct().forEach { root ->
                File(root, "build-tools").listFiles()?.sortedByDescending(File::getName)?.forEach { buildTools ->
                    add(File(buildTools, executable).absolutePath)
                }
            }
        }
        return paths.distinct().map { listOf(it, "dump", "badging", apkFile.absolutePath) }
    }

    /** 执行一条 aapt badging 命令，成功时返回完整输出。 */
    private fun runAaptBadging(command: List<String>): String? = runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() == 0 && output.contains("package:")) output else null
    }.getOrNull()

    /** 读取 aapt package 行中单引号包裹的属性值。 */
    private fun readPackageAttribute(line: String, name: String): String {
        val pattern = Regex("\\b${Regex.escape(name)}='([^']*)'")
        return pattern.find(line)?.groupValues?.getOrNull(1).orEmpty()
    }

    @Suppress("unused")
    /** 预留 APK 重新解析入口。 */
    fun reload(newApkPath: String) {

    }

    /** 将当前 APK 解析结果输出为简易 JSON 文本。 */
    override fun toString(): String {
        return "{ \"packageName\" : $packageName }"
    }
}

object ManifestManager {

    private val manifestHolder = mutableMapOf<String, AndroidManifest>()

    fun createManifest(pathName: String): AndroidManifest {
        return manifestHolder.getOrPut(pathName) {
            AndroidManifest.create(pathName)
        }
    }

    @Volatile
    private var instance: ManifestManager? = null
    
    fun getInstance(): ManifestManager {
        return instance ?: synchronized(this) {
            instance ?: ManifestManager.also { instance = it }
        }
    }
}
