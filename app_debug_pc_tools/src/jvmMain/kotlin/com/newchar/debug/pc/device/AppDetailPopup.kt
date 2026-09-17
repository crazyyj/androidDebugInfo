package com.newchar.debug.pc.device

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.newchar.debug.pc.AppIconThumbnail
import com.newchar.debug.pc.ui.AppText
import com.newchar.debug.pc.ui.AppTheme
import com.newchar.debug.pc.ui.PanelCard
import com.newchar.debug.pc.ui.chooseExportDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 点击应用行后的详情弹窗；可置顶，置顶后只能靠关闭按钮收起。 */
@Composable
fun AppDetailPopup(
    app: InstalledAppInfo,
    item: PackageDisplayItem,
    deviceId: String,
    iconCache: AppIconCache,
    onDismiss: () -> Unit,
    onExport: (java.io.File) -> Unit,
) {
    var pinned by remember(app.packageName) { mutableStateOf(false) }
    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = "$deviceId|${app.packageName}",
        key2 = app.versionCode,
    ) {
        value = withContext(Dispatchers.IO) {
            iconCache.loadIconBitmap(deviceId, app.packageName, app.versionCode)
        }
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { if (!pinned) onDismiss() },
    ) {
        PanelCard(
            modifier = Modifier
                .width(460.dp)
                .border(1.dp, AppTheme.accent, RoundedCornerShape(16.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    "应用详情",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.textPrimary),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PopupActionChip(
                        text = if (pinned) "取消置顶" else "\uD83D\uDCCC 置顶",
                        highlighted = pinned,
                        onClick = { pinned = !pinned },
                    )
                    PopupActionChip(text = "关闭", highlighted = false, onClick = onDismiss)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconThumbnail(
                    bitmap = iconBitmap,
                    label = app.displayName,
                    modifier = Modifier.size(52.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText(
                        app.displayName,
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.textPrimary),
                        maxLines = 1,
                    )
                    AppText(
                        app.packageName,
                        style = TextStyle(fontSize = 12.sp, color = AppTheme.textSecondary),
                        maxLines = 1,
                    )
                }
            }
            Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppTheme.border))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DetailLine("应用名", app.appLabel.ifBlank { "（未提供）" })
                DetailLine("包名", app.packageName)
                DetailLine("版本", "${app.versionName.ifBlank { "-" }} (${app.versionCode.ifBlank { "-" }})")
                DetailLine("APK 路径", app.apkPath.ifBlank { "-" })
                DetailLine("UID", app.uid.ifBlank { "-" })
                DetailLine("首次安装", app.firstInstallTime.ifBlank { "-" })
                DetailLine("最后更新", app.lastUpdateTime.ifBlank { "-" })
                DetailLine("安装来源", app.installerPackageName.ifBlank { "-" })
                DetailLine("系统应用", if (app.isSystemApp) "是" else "否")
                DetailLine("可调试", if (app.isDebuggable) "是" else "否")
                DetailLine(
                    "应用状态",
                    when (item.status) {
                        PackageStatus.NEW_INSTALLED -> "新增"
                        PackageStatus.UNINSTALLED -> "已卸载"
                        PackageStatus.CURRENT -> "当前"
                    },
                )
            }
            if (item.status != PackageStatus.UNINSTALLED) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, AppTheme.border, RoundedCornerShape(10.dp))
                        .background(AppTheme.panel)
                        .clickable { chooseExportDirectory()?.let(onExport) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        "导出安装包到电脑…",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.textPrimary),
                    )
                }
            }
        }
    }
}

@Composable
private fun PopupActionChip(text: String, highlighted: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (highlighted) AppTheme.accent else AppTheme.border, RoundedCornerShape(8.dp))
            .background(if (highlighted) AppTheme.accentSoft else AppTheme.panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highlighted) AppTheme.accent else AppTheme.textSecondary,
            ),
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppText(
            label,
            style = TextStyle(fontSize = 12.sp, color = AppTheme.textSecondary),
            modifier = Modifier.width(80.dp),
            maxLines = 1,
        )
        AppText(
            value,
            style = TextStyle(fontSize = 12.sp, color = AppTheme.textPrimary),
            modifier = Modifier.weight(1f),
        )
    }
}
