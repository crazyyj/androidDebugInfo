package com.newchar.debug.pc.device.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.ui.AppDivider
import com.newchar.debug.pc.ui.AppOutlinedButton
import com.newchar.debug.pc.ui.AppText
import com.newchar.debug.pc.ui.AppTheme

/**
 * WiFi 连接状态 banner 展示组件。
 *
 * 显示规则：
 * - 任一 USB 设备的无线 IP 不在 PC 同网段 → 黄色 banner
 * - USB 设备无无线 IP（未连接 WiFi）→ 灰色 notice
 * - PC 无 WiFi 连接 → 红色 banner
 *
 * @param devices 设备列表
 * @param onToggleVisible 点击 banner 时调用，控制显示/隐藏
 * @param isVisible 当前是否可见
 */
@Composable
fun WifiBanner(
    devices: List<DeviceInfo>,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wifiConnected = JvmWifiDetector.isWifiConnected
    val wifiIp = JvmWifiDetector.wifiIp
    val interfaceName = JvmWifiDetector.wifiInterfaceName

    // 分类收集 banner 信息
    val banners = collectWifiBanners(devices, wifiConnected, wifiIp)
    if (banners.isEmpty()) {
        return
    }

    // 只显示最高优先级的 banner（PC 无 WiFi > 不同网段 > 未连接 WiFi）
    val primary = banners.maxBy { it.priority }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bannerColor(primary.type))
            .border(1.dp, bannerBorderColor(primary.type), RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleVisible,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = bannerIcon(primary.type) + " " + primary.message,
                style = BannerTextStyle,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            AppText(
                text = "点击查看详情",
                style = BannerHintStyle,
                maxLines = 1,
            )
        }
    }
}

/**
 * WiFi 详情面板，展示所有设备的 WiFi 状态。
 */
@Composable
fun WifiBannerDetail(
    devices: List<DeviceInfo>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wifiConnected = JvmWifiDetector.isWifiConnected
    val wifiIp = JvmWifiDetector.wifiIp
    val interfaceName = JvmWifiDetector.wifiInterfaceName

    androidx.compose.ui.window.Dialog(onCloseRequest = onDismiss) {
        Column(
            modifier = modifier
                .width(400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFD7DCE3), RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(
                text = "WiFi 连接状态",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2430)),
            )

            // PC 状态
            AppText(
                text = if (wifiConnected) {
                    "PC 已连接 WiFi: $interfaceName (${wifiIp ?: "未知"})"
                } else {
                    "PC 未连接 WiFi"
                },
                style = TextStyle(fontSize = 14.sp, color = if (wifiConnected) Color(0xFF2E8B57) else Color(0xFFC44536)),
            )

            AppDivider()

            // 设备列表
            val usbDevices = devices.filterNot { it.isNetworkDevice }
            if (usbDevices.isEmpty()) {
                AppText("暂无设备", style = TextStyle(fontSize = 13.sp, color = AppTheme.textHint))
            } else {
                usbDevices.forEach { device ->
                    val statusText = buildWifiDeviceStatus(device)
                    AppText(
                        text = "${device.model.ifBlank { device.id }} — $statusText",
                        style = TextStyle(fontSize = 13.sp, color = AppTheme.textSecondary),
                        maxLines = 1,
                    )
                }
            }

            AppDivider()
            AppText(
                text = "建议：将手机连接到与 PC 相同的 WiFi 网络，以便使用 WiFi ADB 保持连接。",
                style = TextStyle(fontSize = 13.sp, color = AppTheme.textHint),
            )

            Row(horizontalArrangement = Arrangement.End) {
                AppOutlinedButton("关闭", onClick = onDismiss)
            }
        }
    }
}

// =========================================================================
// 私有辅助函数
// =========================================================================

private data class WifiBannerItem(
    val type: WifiBannerType,
    val message: String,
    val priority: Int,
)

private enum class WifiBannerType(val priority: Int) {
    PC_NO_WIFI(3),
    DEVICE_DIFFERENT_SUBNET(2),
    DEVICE_NO_WIFI(1),
}

private fun collectWifiBanners(
    devices: List<DeviceInfo>,
    wifiConnected: Boolean,
    wifiIp: String?,
): List<WifiBannerItem> {
    val banners = mutableListOf<WifiBannerItem>()

    // PC 无 WiFi
    if (!wifiConnected) {
        banners += WifiBannerItem(
            type = WifiBannerType.PC_NO_WIFI,
            message = "PC 未连接 WiFi，WiFi ADB 不可用。请连接 WiFi 以启用无线调试。",
            priority = WifiBannerType.PC_NO_WIFI.priority,
        )
        return banners
    }

    val usbDevices = devices.filterNot { it.isNetworkDevice && !it.isRetainedOffline }

    // 不同网段
    val differentSubnetDevices = usbDevices.filter { device ->
        device.wirelessIp.isNotBlank() && !JvmWifiDetector.isSameSubnet(device.wirelessIp)
    }
    if (differentSubnetDevices.isNotEmpty()) {
        val names = differentSubnetDevices
            .map { "${it.model.ifBlank { it.id }}(${it.wirelessIp})" }
            .take(3)
            .joinToString("、")
        val suffix = if (differentSubnetDevices.size > 3) " 等" else ""
        banners += WifiBannerItem(
            type = WifiBannerType.DEVICE_DIFFERENT_SUBNET,
            message = "$names$suffix 不在 PC 同一网段，WiFi ADB 可能无法连接。",
            priority = WifiBannerType.DEVICE_DIFFERENT_SUBNET.priority,
        )
    }

    // 未连接 WiFi
    val noWifiDevices = usbDevices.filter {
        it.wirelessIp.isBlank()
    }
    if (noWifiDevices.isNotEmpty() && differentSubnetDevices.isEmpty()) {
        val names = noWifiDevices
            .map { it.model.ifBlank { it.id } }
            .take(3)
            .joinToString("、")
        val suffix = if (noWifiDevices.size > 3) " 等" else ""
        banners += WifiBannerItem(
            type = WifiBannerType.DEVICE_NO_WIFI,
            message = "$names$suffix 未连接 WiFi，无法使用 WiFi ADB。请连接与 PC 相同的 WiFi。",
            priority = WifiBannerType.DEVICE_NO_WIFI.priority,
        )
    }

    return banners
}

private fun buildWifiDeviceStatus(device: DeviceInfo): String {
    return when {
        device.wirelessIp.isBlank() -> "未连接 WiFi"
        JvmWifiDetector.isSameSubnet(device.wirelessIp) -> "WiFi: ${device.wirelessIp} ✓ 同网段"
        else -> "WiFi: ${device.wirelessIp} ✗ 不同网段"
    }
}

private fun bannerColor(type: WifiBannerType): Color = when (type) {
    WifiBannerType.PC_NO_WIFI -> Color(0xFFFFF3CD)
    WifiBannerType.DEVICE_DIFFERENT_SUBNET -> Color(0xFFFFF8E1)
    WifiBannerType.DEVICE_NO_WIFI -> Color(0xFFF5F5F5)
}

private fun bannerBorderColor(type: WifiBannerType): Color = when (type) {
    WifiBannerType.PC_NO_WIFI -> Color(0xFFFFC107)
    WifiBannerType.DEVICE_DIFFERENT_SUBNET -> Color(0xFFFFC107)
    WifiBannerType.DEVICE_NO_WIFI -> Color(0xFFE0E0E0)
}

private fun bannerIcon(type: WifiBannerType): String = when (type) {
    WifiBannerType.PC_NO_WIFI -> "⚠"
    WifiBannerType.DEVICE_DIFFERENT_SUBNET -> "⚠"
    WifiBannerType.DEVICE_NO_WIFI -> "ℹ"
}

private val BannerTextStyle = TextStyle(
    fontSize = 13.sp,
    color = Color(0xFF5B6472),
    fontWeight = FontWeight.Medium,
)

private val BannerHintStyle = TextStyle(
    fontSize = 12.sp,
    color = Color(0xFF8A93A3),
)
