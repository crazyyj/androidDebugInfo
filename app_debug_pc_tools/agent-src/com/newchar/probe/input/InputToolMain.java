package com.newchar.probe.input;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 以 shell UID 运行、由 PC 通过标准输入控制的输入脚本 agent。 */
public final class InputToolMain {

    private static final Object OUTPUT_LOCK = new Object();
    private static InputScriptRunner currentRunner;
    private static PlaybackControl currentControl;
    private static final ExternalFallbackCoordinator EXTERNAL_FALLBACK = new ExternalFallbackCoordinator();

    /** 初始化输入环境并持续读取 PC 控制命令。 */
    public static void main(String[] args) {
        try {
            Context context = systemContext();
            InputInjector injector = new InputInjector(context);
            ShellBridge shell = new ShellBridge();
            printLine("READY|1|" + Build.VERSION.SDK_INT + "|" + injector.isAvailable());
            readCommands(injector, shell);
        } catch (Throwable throwable) {
            printLine("FATAL|" + encode(errorMessage(throwable)));
        } finally {
            cancelCurrent();
        }
        System.exit(0);
    }

    /** 读取 RUN、PAUSE、RESUME、CANCEL 和 STOP 控制命令。 */
    private static void readCommands(InputInjector injector, ShellBridge shell) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("RUN|")) startRun(line.substring(4), injector, shell);
            else if ("PAUSE".equals(line)) pauseCurrent();
            else if ("RESUME".equals(line)) resumeCurrent();
            else if ("CANCEL".equals(line)) cancelCurrent();
            else if (line.startsWith("ACCESSIBILITY_RESULT|")) completeExternalFallback(line);
            else if ("STOP".equals(line)) break;
        }
    }

    /** 解析脚本并启动独立执行线程。 */
    private static synchronized void startRun(String encodedScript, InputInjector injector,
            ShellBridge shell) {
        cancelCurrent();
        try {
            String json = new String(Base64.decode(encodedScript, Base64.DEFAULT), StandardCharsets.UTF_8);
            AgentScript script = AgentScript.parse(json);
            EXTERNAL_FALLBACK.configure(script);
            currentControl = new PlaybackControl();
            currentRunner = new InputScriptRunner(script, injector, shell, currentControl,
                    InputToolMain::report, EXTERNAL_FALLBACK);
            new Thread(currentRunner, "newchar-input-runner").start();
        } catch (Throwable throwable) {
            printLine("FATAL|" + encode(errorMessage(throwable)));
        }
    }

    /** 暂停当前运行并向 PC 反馈状态。 */
    private static synchronized void pauseCurrent() {
        if (currentControl == null) return;
        currentControl.pause();
        printLine("CONTROL|PAUSED");
    }

    /** 恢复当前运行并向 PC 反馈状态。 */
    private static synchronized void resumeCurrent() {
        if (currentControl == null) return;
        currentControl.resume();
        printLine("CONTROL|RESUMED");
    }

    /** 取消当前运行。 */
    private static synchronized void cancelCurrent() {
        if (currentControl != null) currentControl.cancel();
        EXTERNAL_FALLBACK.cancel();
        currentControl = null;
        currentRunner = null;
    }

    /** 解析 PC 返回的辅助服务执行结果。 */
    private static void completeExternalFallback(String line) {
        String[] parts = line.split("\\|", 3);
        boolean success = parts.length > 1 && Boolean.parseBoolean(parts[1]);
        String message = parts.length > 2 ? decode(parts[2]) : "辅助服务未返回消息";
        EXTERNAL_FALLBACK.complete(new ShellBridge.BackendResult(success, message));
    }

    /** 输出统一状态协议。 */
    private static void report(String runId, String state, int index, int total,
            String backend, String message) {
        printLine("STATUS|" + encode(runId) + "|" + state + "|" + index + "|" + total
                + "|" + backend + "|" + encode(message));
    }

    /** 线程安全地输出一行协议文本。 */
    private static void printLine(String line) {
        synchronized (OUTPUT_LOCK) {
            System.out.println(line);
            System.out.flush();
        }
    }

    /** 将协议文本编码为无换行 Base64。 */
    private static String encode(String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    /** 解码 PC 控制协议中的 Base64 文本。 */
    private static String decode(String value) {
        return new String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8);
    }

    /** 提取异常类型与消息。 */
    private static String errorMessage(Throwable throwable) {
        StringBuilder message = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (message.length() > 0) message.append(" <- ");
            message.append(current.getClass().getSimpleName());
            if (current.getMessage() != null) message.append(": ").append(current.getMessage());
            current = current.getCause();
        }
        return message.toString();
    }

    /** 通过隐藏 API 获取系统上下文。 */
    private static Context systemContext() throws Exception {
        if (Looper.myLooper() == null) Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getDeclaredMethod("systemMain").invoke(null);
        return (Context) activityThread.getDeclaredMethod("getSystemContext").invoke(thread);
    }

    /** 禁止实例化命令入口。 */
    private InputToolMain() {
    }

    /** 协调 agent 执行线程与 PC 标准输入线程之间的辅助服务请求。 */
    private static final class ExternalFallbackCoordinator implements InputScriptRunner.ExternalStepExecutor {
        private ShellBridge.BackendResult result;
        private boolean waiting;
        private AgentScript script;

        /** 保存当前脚本上下文，供单步辅助服务载荷使用。 */
        synchronized void configure(AgentScript value) {
            script = value;
        }

        /** 发出辅助服务请求并限时等待 PC 回应。 */
        @Override
        public synchronized ShellBridge.BackendResult execute(int index, AgentScript.Step step,
                String message) throws InterruptedException {
            result = null;
            waiting = true;
            printLine("ACCESSIBILITY_REQUEST|" + index + "|" + encode(payload(step))
                    + "|" + encode(message));
            long deadline = System.currentTimeMillis() + responseTimeoutMs(step);
            while (waiting && result == null && System.currentTimeMillis() < deadline) {
                wait(Math.max(1L, deadline - System.currentTimeMillis()));
            }
            waiting = false;
            return result == null ? ShellBridge.BackendResult.failure("辅助服务响应超时") : result;
        }

        /** 构建包含录制尺寸、默认包名和步骤的 App 执行载荷。 */
        private String payload(AgentScript.Step step) {
            JSONObject payload = new JSONObject();
            try {
                payload.put("sourceWidth", script == null ? 0 : script.sourceWidth);
                payload.put("sourceHeight", script == null ? 0 : script.sourceHeight);
                payload.put("targetPackage", script == null ? "" : script.targetPackage);
                payload.put("step", new JSONObject(step.source.toString()));
            } catch (Exception ignored) {
                // 已解析脚本的固定字段不会产生 JSON 异常。
            }
            return payload.toString();
        }

        /** 按步骤耗时给辅助服务保留通信与执行余量。 */
        private long responseTimeoutMs(AgentScript.Step step) {
            long operationMs = "wait".equals(step.type) ? step.waitMs : step.durationMs;
            return Math.max(15000L, Math.min(620000L, operationMs + 15000L));
        }

        /** 接收 PC 返回结果并唤醒执行线程。 */
        synchronized void complete(ShellBridge.BackendResult value) {
            if (!waiting) return;
            result = value;
            notifyAll();
        }

        /** 取消当前等待。 */
        synchronized void cancel() {
            waiting = false;
            notifyAll();
        }
    }
}
