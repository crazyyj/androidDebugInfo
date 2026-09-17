package com.newchar.debug.pc.device.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.newchar.debug.pc.device.DeviceInfo
import com.newchar.debug.pc.executor.AdbCommandExecutor
import com.newchar.debug.pc.ui.AppText
import com.newchar.debug.pc.ui.AppTheme
import com.newchar.debug.pc.ui.PanelCard
import com.newchar.debug.pc.ui.chooseInputScriptFile
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** 在设备详情中展示输入脚本入口，并按需打开独立监控窗口。 */
@Composable
fun InputScriptPanel(
    device: DeviceInfo,
    executor: AdbCommandExecutor,
    scope: CoroutineScope,
) {
    var showWindow by remember(device.id) { mutableStateOf(false) }
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                AppText("输入脚本", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                AppText(
                    "shell dex 执行，失败时使用 adb input 兜底",
                    style = TextStyle(fontSize = 11.sp, color = AppTheme.textHint),
                )
            }
            ScriptButton("打开监控", device.canManageDevice) { showWindow = true }
        }
    }
    if (showWindow) {
        InputScriptWindow(device, executor, scope) { showWindow = false }
    }
}

/** 显示脚本选择、执行进度、控制按钮和失败现场路径。 */
@Composable
private fun InputScriptWindow(
    device: DeviceInfo,
    executor: AdbCommandExecutor,
    scope: CoroutineScope,
    onCloseRequest: () -> Unit,
) {
    val session = remember(device.id, executor) { InputScriptSession(executor, device.adbTarget(), scope) }
    val snapshot by session.snapshot.collectAsState()
    var selectedFile by remember(device.id) { mutableStateOf<File?>(null) }
    val windowState = rememberWindowState(width = 760.dp, height = 620.dp)
    DisposableEffect(session) {
        onDispose(session::close)
    }
    Window(
        onCloseRequest = onCloseRequest,
        title = "输入脚本 - ${device.displayName()}",
        state = windowState,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(AppTheme.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScriptHeader(selectedFile) { chooseInputScriptFile()?.let { selectedFile = it } }
            ScriptProgress(snapshot)
            ScriptControls(snapshot, selectedFile, session)
            ScriptLog(snapshot, Modifier.weight(1f))
        }
    }
}

/** 展示当前脚本路径并提供文件选择入口。 */
@Composable
private fun ScriptHeader(selectedFile: File?, onChoose: () -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        AppText("脚本文件", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(AppTheme.panelAlt).padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                AppText(
                    selectedFile?.absolutePath ?: "请选择 .json 或 .lqits 文件",
                    style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AppTheme.textSecondary),
                    maxLines = 1,
                )
            }
            ScriptButton("选择文件", true, onChoose)
        }
    }
}

/** 展示脚本状态、执行后端和线性进度。 */
@Composable
private fun ScriptProgress(snapshot: InputRunSnapshot) {
    val fraction = if (snapshot.totalSteps == 0) 0f
    else snapshot.currentStep.toFloat().div(snapshot.totalSteps).coerceIn(0f, 1f)
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText(stateLabel(snapshot.state), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            AppText(
                "${snapshot.currentStep}/${snapshot.totalSteps}  ${snapshot.backend}",
                style = TextStyle(fontSize = 11.sp, color = AppTheme.textSecondary),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(AppTheme.border)) {
            if (fraction > 0f) {
                Box(Modifier.fillMaxWidth(fraction).height(7.dp).background(progressColor(snapshot.state)))
            }
        }
        AppText(snapshot.message, style = TextStyle(fontSize = 12.sp, color = AppTheme.textSecondary))
        snapshot.screenshotPath?.let { path ->
            AppText(
                "失败截图：$path",
                style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AppTheme.danger),
                maxLines = 1,
            )
        }
    }
}

/** 根据运行状态提供开始、暂停、继续和取消按钮。 */
@Composable
private fun ScriptControls(snapshot: InputRunSnapshot, file: File?, session: InputScriptSession) {
    val running = snapshot.state in setOf(InputRunState.PREPARING, InputRunState.RUNNING, InputRunState.PAUSED)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScriptButton("开始", file != null && !running) { file?.let(session::start) }
        ScriptButton("暂停", snapshot.state == InputRunState.RUNNING, session::pause)
        ScriptButton("继续", snapshot.state == InputRunState.PAUSED, session::resume)
        ScriptButton("取消", running, session::cancel)
    }
}

/** 展示最近的 agent 协议与兜底执行日志。 */
@Composable
private fun ScriptLog(snapshot: InputRunSnapshot, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    LaunchedEffect(snapshot.logs.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF17191D)).border(1.dp, Color(0xFF414753), RoundedCornerShape(10.dp))
            .verticalScroll(scrollState).padding(12.dp),
    ) {
        AppText(
            snapshot.logs.joinToString("\n").ifBlank { "等待执行…" },
            style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFE8EAED)),
        )
    }
}

/** 绘制不依赖 Material 的脚本操作按钮。 */
@Composable
private fun ScriptButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val background = if (enabled) AppTheme.accent else AppTheme.panelAlt
    val contentColor = if (enabled) Color.White else AppTheme.textHint
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(background)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(text, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = contentColor))
    }
}

/** 返回用户可读的运行状态名称。 */
private fun stateLabel(state: InputRunState): String = when (state) {
    InputRunState.IDLE -> "等待脚本"
    InputRunState.PREPARING -> "准备中"
    InputRunState.RUNNING -> "执行中"
    InputRunState.PAUSED -> "已暂停"
    InputRunState.COMPLETED -> "已完成"
    InputRunState.COMPLETED_WITH_ERRORS -> "已全部尝试，存在失败"
    InputRunState.FAILED -> "执行失败"
    InputRunState.CANCELLED -> "已取消"
}

/** 返回不同终态对应的进度颜色。 */
private fun progressColor(state: InputRunState): Color = when (state) {
    InputRunState.FAILED, InputRunState.COMPLETED_WITH_ERRORS -> AppTheme.danger
    InputRunState.CANCELLED -> AppTheme.textHint
    else -> AppTheme.accent
}