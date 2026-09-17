package com.newchar.probe.input;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 以 shell UID 运行、由 PC 通过标准输入控制的输入脚本 agent。 */
public final class InputToolMain {

    private static final Object OUTPUT_LOCK = new Object();
    private static InputScriptRunner currentRunner;
    private static PlaybackControl currentControl;

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
            currentControl = new PlaybackControl();
            currentRunner = new InputScriptRunner(script, injector, shell, currentControl,
                    InputToolMain::report);
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
        currentControl = null;
        currentRunner = null;
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
}