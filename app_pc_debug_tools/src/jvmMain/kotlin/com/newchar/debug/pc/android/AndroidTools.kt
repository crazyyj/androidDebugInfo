package com.newchar.debug.pc.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidManifest private constructor(private val apkFilePath: String) {

    var packageName: String = ""
        private set
    var versionCode: Int = 0
        private set
    var minVersion: Int = 0
        private set
    var versionName: String = ""
        private set
    var permissions: List<String> = emptyList()
        private set

    init {
        parseApkFile()
    }

    companion object {
        fun create(apkPath: String): AndroidManifest {
            return AndroidManifest(apkPath)
        }

        internal fun parsePackageName(lineString: String): String {
            val split = lineString.split("'", limit = 3)
            return if (split.size >= 2) split[1] else ""
        }
    }

    private fun parseApkFile() {
        runCatching {
            val file = java.io.File(apkFilePath)
            if (!file.isFile || !file.isAbsolute) {
                return
            }

            val process = ProcessBuilder("aapt", "dump", "badging", apkFilePath)
                .redirectErrorStream(true)
                .start()

            // 使用 Kotlin 扩展函数读取（协程友好）
            val lineStr = process.inputStream.bufferedReader().use { reader ->
                reader.readLine() ?: return
            }
            
            packageName = Companion.parsePackageName(lineStr)

            process.destroy()
        }.onFailure { e ->
            e.printStackTrace()
        }
    }

    @Suppress("unused")
    fun reload(newApkPath: String) {

    }

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
