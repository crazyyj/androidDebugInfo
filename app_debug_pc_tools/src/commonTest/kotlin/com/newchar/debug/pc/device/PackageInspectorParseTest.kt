package com.newchar.debug.pc.device

import com.newchar.debug.pc.device.CommandResult
import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 回归测试：APK 路径中含 `=` 时包名解析不能错位。
 *
 * `pm list packages -f -U` 在 Android 8+ 上的真实输出形如：
 * `package:/data/app/~~abc==/com.foo.bar-xyz==/base.apk=com.foo.bar uid:10123`
 * 历史上按第一个 `=` 切分会把包名解析成 `=/base.apk=com.foo.bar`。
 */
class PackageInspectorParseTest {

    private class FakeExecutor(
        private val listOutput: String,
        private val dumpsys: String = "",
    ) : CommandExecutor {
        override suspend fun execute(vararg command: String): CommandResult = CommandResult.success("")

        override fun syncExecute(vararg command: String): CommandResult = CommandResult.success("")

        override fun stream(vararg command: String): Flow<String> = emptyFlow()

        override suspend fun adb(vararg args: String): CommandResult {
            val joined = args.joinToString(" ")
            return when {
                joined.contains("list packages") -> CommandResult.success(listOutput)
                joined.contains("dumpsys package") -> CommandResult.success(dumpsys)
                else -> CommandResult.success("")
            }
        }

        override fun adbSync(vararg args: String): CommandResult = CommandResult.success("")

        override fun adbStream(vararg args: String): Flow<String> = emptyFlow()

        override suspend fun shell(deviceId: String?, vararg command: String): CommandResult = CommandResult.success("")

        override fun resolveAdbCommand(vararg args: String): List<String> = args.toList()

        override fun resolveAdbExecutablePath(): String = "adb"
    }

    @Test
    fun parsesPackageNameWhenApkPathContainsEquals() = runBlocking {
        val output = """
            package:/data/app/~~abc==/com.foo.bar-xyz==/base.apk=com.foo.bar uid:10123
            package:/system/app/SystemUi/SystemUi.apk=com.android.systemui uid:1000
            package:/data/app/com.demo-1==/base.apk=com.demo uid:10234
            package:/data/app/com.nouid==/base.apk=com.nouid
        """.trimIndent()
        val apps = PackageInspector.loadInstalledApps(FakeExecutor(output), "device-1")

        val byPkg = apps.associateBy { it.packageName }
        assertTrue(byPkg.containsKey("com.foo.bar"), "应解析出 com.foo.bar，实际=${apps.map { it.packageName }}")
        assertTrue(byPkg.containsKey("com.demo"), "应解析出 com.demo，实际=${apps.map { it.packageName }}")
        assertTrue(byPkg.containsKey("com.nouid"), "无 uid 行也应解析，实际=${apps.map { it.packageName }}")
        // 平台包交由 PC 端应用列表的可切换过滤条件处理，数据层必须保留。
        assertTrue(byPkg.containsKey("com.android.systemui"), "com.android.* 应保留给 UI 过滤")
        // 路径必须完整保留（用于系统应用判定）
        assertEquals("/data/app/~~abc==/com.foo.bar-xyz==/base.apk", byPkg["com.foo.bar"]?.apkPath)
        assertEquals("10123", byPkg["com.foo.bar"]?.uid)
        assertEquals("10234", byPkg["com.demo"]?.uid)
        assertEquals("", byPkg["com.nouid"]?.uid)
        // 不得出现含 '=' 或 '/' 的脏包名
        assertTrue(apps.none { it.packageName.contains('=') || it.packageName.contains('/') }, "存在脏包名")
    }

    @Test
    fun keepsSystemFlagFromApkPath() = runBlocking {
        val output = """
            package:/system/app/SystemUi/SystemUi.apk=com.android.systemui uid:1000
            package:/data/app/~~a==/com.foo.bar-b==/base.apk=com.foo.bar uid:10123
        """.trimIndent()
        val apps = PackageInspector.loadInstalledApps(FakeExecutor(output), "device-1")
        val foo = apps.firstOrNull { it.packageName == "com.foo.bar" }
        assertEquals(false, foo?.isSystemApp, "/data/app 下的应用不应判定为系统应用")
    }
}
