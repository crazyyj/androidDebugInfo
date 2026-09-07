package com.newchar.debug.pc.device

import kotlinx.serialization.Serializable

/** 按设备保存的一次完整应用列表快照。 */
@Serializable
data class PackageSnapshot(
    val deviceId: String,
    val capturedAt: Long,
    val apps: List<InstalledAppInfo>,
)

/** 应用相对于上一次快照的展示状态。 */
enum class PackageStatus {
    CURRENT,
    NEW_INSTALLED,
    UNINSTALLED,
}

/** 供 PC 端应用列表展示使用的应用与状态组合。 */
data class PackageDisplayItem(
    val info: InstalledAppInfo,
    val status: PackageStatus,
)

/**
 * 对比前后两次应用快照。
 *
 * 首次拉取没有历史快照时，所有应用均保持当前状态，避免把整个设备错误标记为新增。
 */
fun buildPackageDisplayItems(
    previous: List<InstalledAppInfo>?,
    current: List<InstalledAppInfo>,
): List<PackageDisplayItem> {
    if (previous == null) {
        return current.map { PackageDisplayItem(it, PackageStatus.CURRENT) }
            .sortedWith(packageDisplayComparator())
    }
    val previousNames = previous.asSequence().map(InstalledAppInfo::packageName).toSet()
    val currentNames = current.asSequence().map(InstalledAppInfo::packageName).toSet()
    val currentItems = current.map { app ->
        val status = if (app.packageName in previousNames) PackageStatus.CURRENT else PackageStatus.NEW_INSTALLED
        PackageDisplayItem(app, status)
    }
    val uninstalledItems = previous.asSequence()
        .filter { it.packageName !in currentNames }
        .map { PackageDisplayItem(it, PackageStatus.UNINSTALLED) }
        .toList()
    return (currentItems + uninstalledItems).sortedWith(packageDisplayComparator())
}

/** 返回统一的状态与原有分类排序规则。 */
private fun packageDisplayComparator(): Comparator<PackageDisplayItem> = compareBy<PackageDisplayItem> {
    when (it.status) {
        PackageStatus.NEW_INSTALLED -> 0
        PackageStatus.CURRENT -> 1
        PackageStatus.UNINSTALLED -> 2
    }
}.thenBy { it.info.categoryPriority }.thenBy { it.info.packageName }
