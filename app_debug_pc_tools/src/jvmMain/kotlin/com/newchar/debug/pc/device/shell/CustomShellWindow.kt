package com.newchar.debug.pc.device.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.executor.CommandExecutor
import kotlinx.coroutines.launch

private val terminalBackground = Color(0xFF17191D)
private val terminalInputBackground = Color(0xFF23262D)
private val terminalBorder = Color(0xFF414753)
private val terminalText = Color(0xFFE8EAED)
private val terminalHint = Color(0xFF8A93A3)

/**
 * 显示指定设备的内置 Shell 终端窗口，并维护当前会话的命令与输出历史。
 */
@Composable
fun CustomShellWindow(
    device: DeviceInfo,
    executor: CommandExecutor,
    onCloseRequest: () -> Unit,
) {
    var inputText by remember(device.id) { mutableStateOf("") }
    var history by remember(device.id) { mutableStateOf("") }
    var isExecuting by remember(device.id) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val windowState = rememberWindowState(width = 720.dp, height = 480.dp)

    LaunchedEffect(history) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "Shell - ${device.displayName()}",
        state = windowState,
    ) {
        Column(modifier = Modifier.fillMaxSize().background(terminalBackground).padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(terminalBackground)
                    .border(1.dp, terminalBorder, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .verticalScroll(scrollState)
                    .padding(10.dp),
            ) {
                BasicText(
                    text = history.ifBlank { "输入命令后按 Enter 执行；输入 clear 清屏。" },
                    style = TextStyle(
                        color = if (history.isBlank()) terminalHint else terminalText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    ),
                )
            }
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                enabled = !isExecuting,
                singleLine = true,
                textStyle = TextStyle(color = terminalText, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(42.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(terminalInputBackground)
                    .border(1.dp, terminalBorder, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            val command = inputText.trim()
                            if (command.isNotEmpty() && !isExecuting) {
                                inputText = ""
                                if (command == "clear") {
                                    history = ""
                                } else {
                                    history = history.appendShellLine("$ $command")
                                    isExecuting = true
                                    scope.launch {
                                        val result = executor.shell(device.id, command)
                                        val output = result.output.ifBlank { result.error }.ifBlank { "(无输出)" }
                                        history = history.appendShellLine(output)
                                        isExecuting = false
                                    }
                                }
                            }
                            true
                        } else {
                            false
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        BasicText(
                            text = if (isExecuting) "命令执行中…" else "输入 adb shell 命令，按 Enter 执行",
                            style = TextStyle(color = terminalHint, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}

/** 将一段 Shell 输出追加到终端历史，并保证相邻命令之间留出空行。 */
private fun String.appendShellLine(value: String): String {
    return if (isBlank()) "$value\n" else "$this\n$value\n"
}