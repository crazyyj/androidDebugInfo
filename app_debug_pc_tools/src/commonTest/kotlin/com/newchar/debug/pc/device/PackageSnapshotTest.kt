package com.newchar.debug.pc.device

import kotlin.test.Test
import kotlin.test.assertEquals

class PackageSnapshotTest {

    @Test
    fun `首次快照不标记新增`() {
        val result = buildPackageDisplayItems(null, listOf(app("a"), app("b")))

        assertEquals(listOf(PackageStatus.CURRENT, PackageStatus.CURRENT), result.map { it.status })
    }

    @Test
    fun `比对识别新增与已卸载`() {
        val result = buildPackageDisplayItems(
            previous = listOf(app("retained"), app("removed")),
            current = listOf(app("retained"), app("added")),
        )

        assertEquals(PackageStatus.NEW_INSTALLED, result.first { it.info.packageName == "added" }.status)
        assertEquals(PackageStatus.CURRENT, result.first { it.info.packageName == "retained" }.status)
        assertEquals(PackageStatus.UNINSTALLED, result.first { it.info.packageName == "removed" }.status)
    }

    private fun app(packageName: String) = InstalledAppInfo(packageName = packageName)
}
