package com.newchar.debug.pc.device

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageSnapshotStoreTest {

    @Test
    fun `gzip 快照可保存并读取`() = runBlocking {
        val directory = Files.createTempDirectory("pc-debug-packages-test").toFile()
        try {
            val snapshot = PackageSnapshot("device:5555", 123L, listOf(InstalledAppInfo("com.example.app")))
            val store = PackageSnapshotStore(directory.absolutePath)

            store.save(snapshot)

            val cacheFile = directory.walkTopDown().first { it.isFile }
            assertEquals(0x1F, cacheFile.readBytes()[0].toInt() and 0xFF)
            assertEquals(0x8B, cacheFile.readBytes()[1].toInt() and 0xFF)
            assertEquals(snapshot, store.load("device:5555"))
        } finally {
            assertTrue(directory.deleteRecursively())
        }
    }
}
