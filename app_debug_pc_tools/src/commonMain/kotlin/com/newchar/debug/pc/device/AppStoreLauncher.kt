package com.newchar.debug.pc.device

/** 品牌应用商店的 adb 启动参数。 */
data class AppStoreLaunchTarget(
    val brandName: String,
    val command: List<String>,
)

/** 根据设备厂商生成打开应用商店详情页的 adb shell 参数。 */
object AppStoreLauncher {

    /** 返回品牌应用商店的 VIEW intent；未知厂商使用系统 market 协议兜底。 */
    fun buildLaunchTarget(manufacturer: String, packageName: String): AppStoreLaunchTarget {
        val normalizedBrand = manufacturer.lowercase()
        return when {
            normalizedBrand.contains("huawei") || normalizedBrand.contains("honor") ->
                target("华为", "com.huawei.appmarket", "appmarket://details?id=$packageName")
            normalizedBrand.contains("xiaomi") || normalizedBrand.contains("redmi") ->
                target("小米", "com.xiaomi.market", "mimarket://details?id=$packageName")
            normalizedBrand.contains("oppo") ->
                target("OPPO", "com.heytap.market", "market://details?id=$packageName")
            normalizedBrand.contains("vivo") || normalizedBrand.contains("bbk") ->
                target("vivo", "com.bbk.appstore", "market://details?id=$packageName")
            normalizedBrand.contains("samsung") ->
                target("三星", "com.sec.android.app.samsungapps", "samsungapps://ProductDetail/$packageName")
            else -> AppStoreLaunchTarget(
                brandName = "系统应用商店",
                command = listOf("am", "start", "-a", "android.intent.action.VIEW", "-d", "market://details?id=$packageName"),
            )
        }
    }

    /** 生成带指定商店包名的 VIEW intent 参数。 */
    private fun target(brandName: String, storePackage: String, uri: String): AppStoreLaunchTarget =
        AppStoreLaunchTarget(
            brandName = brandName,
            command = listOf("am", "start", "-a", "android.intent.action.VIEW", "-d", uri, "-p", storePackage),
        )
}
