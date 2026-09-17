package com.newchar.debug.pc.device.shell

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.executor.AdbCommandExecutor
import com.newchar.debug.pc.ui.AppText
import com.newchar.debug.pc.ui.AppTheme
import com.newchar.debug.pc.ui.PanelCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class QuickCommand(val title: String, val shell: String)

private val QUICK_COMMANDS = listOf(
    QuickCommand("前台应用", "dumpsys activity activities | grep -i ResumedActivity"),
    QuickCommand("电池状态", "dumpsys battery"),
    QuickCommand("内存信息", "dumpsys meminfo"),
    QuickCommand("屏幕分辨率", "wm size"),
    QuickCommand("屏幕密度", "wm density"),
    QuickCommand("第三方应用列表", "pm list packages -3"),
    QuickCommand("设备属性", "getprop"),
)

/** 在系统终端中打开指定设备的交互式 adb shell；失败时返回可展示的原因。 */
private fun openSystemAdbShell(adbPath: String, deviceId: String): String? = runCatching {
    val osName = System.getProperty("os.name").orEmpty()
    when {
        osName.contains("mac", ignoreCase = true) -> openMacTerminal(adbPath, deviceId)
        osName.contains("win", ignoreCase = true) -> openWindowsTerminal(adbPath, deviceId)
        else -> openLinuxTerminal(adbPath, deviceId)
    }
}.exceptionOrNull()?.message

/** 使用 macOS Terminal 执行携带设备序列号的 adb shell。 */
private fun openMacTerminal(adbPath: String, deviceId: String) {
    val command = "${shellQuote(adbPath)} -s ${shellQuote(deviceId)} shell"
    val script = "tell application \"Terminal\" to do script \"${command.escapeAppleScript()}\""
    ProcessBuilder("osascript", "-e", script).start()
}

/** 使用 Windows cmd.exe 保持交互式 adb shell 会话。 */
private fun openWindowsTerminal(adbPath: String, deviceId: String) {
    val command = "${windowsQuote(adbPath)} -s ${windowsQuote(deviceId)} shell"
    ProcessBuilder("cmd.exe", "/K", command).start()
}

/** 依次尝试 Linux 常见终端程序，并将 adb shell 作为其启动命令。 */
private fun openLinuxTerminal(adbPath: String, deviceId: String) {
    val commands = listOf(
        listOf("x-terminal-emulator", "-e", adbPath, "-s", deviceId, "shell"),
        listOf("gnome-terminal", "--", adbPath, "-s", deviceId, "shell"),
        listOf("konsole", "-e", adbPath, "-s", deviceId, "shell"),
    )
    commands.firstOrNull { command -> runCatching { ProcessBuilder(command).start() }.isSuccess }
        ?: throw IllegalStateException("未找到可用的系统终端")
}

/** 将参数转换为 POSIX Shell 单引号字面量。 */
private fun shellQuote(value: String): String = "'${value.replace("'", "'\\\"'\\\"'")}'"

/** 转义 AppleScript 字符串中的反斜杠与双引号。 */
private fun String.escapeAppleScript(): String = replace("\\", "\\\\").replace("\"", "\\\"")

/** 将参数转换为 Windows cmd.exe 可识别的双引号字面量。 */
private fun windowsQuote(value: String): String = "\"${value.replace("\"", "\"\"")}\""

/** 快捷 Shell：内置常用命令下拉，末尾可在系统终端中打开交互式 adb shell。 */
@Composable
fun QuickShellButton(
    device: DeviceInfo,
    executor: AdbCommandExecutor,
    scope: CoroutineScope,
    onToast: (String) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var resultTitle by remember { mutableStateOf<String?>(null) }
    var resultText by remember { mutableStateOf("") }
    val adbTarget = device.adbTarget()
    var showCustomShell by remember(adbTarget) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        PanelCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    "快捷 Shell",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.textPrimary),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, AppTheme.border, RoundedCornerShape(8.dp))
                        .background(AppTheme.panel)
                        .clickable { expanded = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppText("$", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.accent))
                        AppText("执行命令 ▾", style = TextStyle(fontSize = 13.sp, color = AppTheme.textPrimary))
                    }
                }
            }
            AppText(
                "内置常用命令，或在系统终端中进入设备 Shell",
                style = TextStyle(fontSize = 11.sp, color = AppTheme.textHint),
            )
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { expanded = false },
            ) {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppTheme.panel)
                        .border(1.dp, AppTheme.border, RoundedCornerShape(10.dp))
                        .padding(vertical = 4.dp),
                ) {
                    QUICK_COMMANDS.forEach { command ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expanded = false
                                    scope.launch {
                                        val result = executor.shell(adbTarget, command.shell)
                                        resultTitle = command.title
                                        resultText = result.output.ifBlank { result.error }.ifBlank { "(无输出)" }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            AppText(
                                command.title,
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppTheme.textPrimary),
                            )
                            AppText(
                                command.shell,
                                style = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AppTheme.textHint),
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppTheme.border))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expanded = false
                                showCustomShell = true
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        AppText(
                            "打开内置 Shell 窗口",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.accent),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expanded = false
                                val failure = openSystemAdbShell(executor.resolveAdbExecutablePath(), adbTarget)
                                onToast(
                                    failure?.let { "无法打开系统终端：$it" }
                                        ?: "已在系统终端打开 ${device.displayName()} 的 adb shell",
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        AppText(
                            "在系统终端打开 adb shell …",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.accent),
                        )
                    }
                }
            }
        }
    }

    if (showCustomShell) {
        CustomShellWindow(
            device = device,
            executor = executor,
            onCloseRequest = { showCustomShell = false },
        )
    }

    resultTitle?.let { title ->
        Dialog(onCloseRequest = { resultTitle = null }, title = "$title - ${device.displayName()}") {
            ShellOutputDialogBody(
                text = resultText,
                onClose = { resultTitle = null },
            )
        }
    }
}

@Composable
private fun ShellOutputDialogBody(text: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .width(680.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.panel)
            .border(1.dp, AppTheme.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppTheme.panelAlt)
                    .border(1.dp, AppTheme.border, RoundedCornerShape(10.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                AppText(
                    text,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AppTheme.textPrimary,
                    ),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, AppTheme.border, RoundedCornerShape(10.dp))
                        .background(AppTheme.panel)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText("关闭", style = TextStyle(fontSize = 13.sp, color = AppTheme.textPrimary))
                }
            }
        }
    }
}