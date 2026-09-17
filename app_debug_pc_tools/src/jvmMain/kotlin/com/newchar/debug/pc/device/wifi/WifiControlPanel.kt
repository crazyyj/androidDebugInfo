package com.newchar.debug.pc.device.wifi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.executor.AdbCommandExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 设备详情中的基础 WiFi 控制面板。 */
@Composable
fun WifiControlPanel(
    device: DeviceInfo,
    executor: AdbCommandExecutor,
    scope: CoroutineScope,
    onToast: (String) -> Unit,
    onRequestAppStatus: suspend (DeviceInfo) -> Result<WifiStatus>,
    modifier: Modifier = Modifier,
) {
    val wifiExecutor = remember(executor) { WifiShellExecutor(executor) }
    var status by remember(device.id) { mutableStateOf<WifiStatus?>(null) }
    var loading by remember(device.id) { mutableStateOf(false) }
    val target = device.adbTarget()

    /** 刷新 WiFi 开关与当前连接状态，ADB 失败时改由 App 回传。 */
    fun refreshStatus() {
        scope.launch {
            status = wifiExecutor.status(target).getOrNull() ?: onRequestAppStatus(device).getOrNull()
        }
    }

    LaunchedEffect(target, device.connectionState) {
        if (device.canManageDevice) refreshStatus()
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFD7DCE3), RoundedCornerShape(10.dp))
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WifiText("WiFi 控制", 16, Color(0xFF1F2430))
            Spacer(Modifier.width(8.dp))
            WifiText(
                status?.let { if (it.enabled) "已开启${it.connectedSsid.takeIf(String::isNotBlank)?.let { name -> " · $name" }.orEmpty()}" else "已关闭" }
                    ?: "状态未知",
                12,
                Color(0xFF5B6472),
            )
            Spacer(Modifier.width(8.dp))
            WifiAction("刷新", enabled = !loading, onClick = ::refreshStatus)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WifiAction("开启", enabled = !loading, onClick = {
                runWifiAction(scope, { loading = it }, onToast, ::refreshStatus) { wifiExecutor.setWifiEnabled(target, true) }
            })
            WifiAction("关闭", enabled = !loading, onClick = {
                runWifiAction(scope, { loading = it }, onToast, ::refreshStatus) { wifiExecutor.setWifiEnabled(target, false) }
            })
            WifiAction("断开", enabled = !loading, onClick = {
                runWifiAction(scope, { loading = it }, onToast, ::refreshStatus) { wifiExecutor.disconnect(target) }
            })
        }
    }
}

/** 在协程中运行一次 WiFi 操作，并统一处理加载状态和结果提示。 */
private fun runWifiAction(
    scope: CoroutineScope,
    onLoading: (Boolean) -> Unit,
    onToast: (String) -> Unit,
    onComplete: () -> Unit = {},
    action: suspend () -> WifiSwitchResult,
) {
    scope.launch {
        onLoading(true)
        val result = action()
        onLoading(false)
        onToast(if (result.success) result.message else "WiFi 操作失败：${result.message}")
        onComplete()
    }
}

/** 绘制 WiFi 控制面板内的文本。 */
@Composable
private fun WifiText(text: String, size: Int, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(fontSize = size.sp, color = color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 绘制 WiFi 控制面板内的轻量按钮。 */
@Composable
private fun WifiAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    WifiText(
        text = text,
        size = 12,
        color = if (enabled) Color(0xFF3566D6) else Color(0xFF9AA3B2),
        modifier = Modifier
            .background(if (enabled) Color(0xFFE6EEFF) else Color(0xFFF0F2F5), RoundedCornerShape(6.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}
