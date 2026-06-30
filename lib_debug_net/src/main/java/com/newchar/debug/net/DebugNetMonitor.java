package com.newchar.debug.net;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;

import com.newchar.debug.utils.HandleWrapper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Debug 网络监控入口。
 */
public final class DebugNetMonitor {

    public static final int START_OK = 0;
    public static final int START_NEED_PERMISSION = 1;
    public static final int START_CONFIG_ERROR = 2;
    public static final int START_FAILED = 3;

    private static final List<DebugNetTrafficListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler WORK_HANDLER = HandleWrapper.obtainAsyncHandler(null);
    private static volatile boolean sRunning;
    private static volatile DebugNetConfig sConfig = DebugNetConfig.defaultConfig();
    private static volatile DebugNetPostProcessor sPostProcessor;

    private DebugNetMonitor() {
    }

    public static int start(Context context) {
        if (context == null) {
            return START_FAILED;
        }
        String configError = sConfig.validateForStart();
        if (configError != null) {
            dispatch(buildConfigErrorEvent(configError));
            return START_CONFIG_ERROR;
        }
        Context appContext = context.getApplicationContext();
        Intent prepareIntent = VpnService.prepare(appContext);
        if (prepareIntent != null) {
            try {
                Intent permissionIntent = new Intent(appContext, DebugNetPermissionActivity.class);
                permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(permissionIntent);
                return START_NEED_PERMISSION;
            } catch (Exception e) {
                return START_FAILED;
            }
        }
        Intent intent = new Intent(appContext, DebugNetVpnService.class);
        intent.setAction(DebugNetVpnService.ACTION_START);
        appContext.startService(intent);
        return START_OK;
    }

    public static void stop(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, DebugNetVpnService.class);
        intent.setAction(DebugNetVpnService.ACTION_STOP);
        appContext.startService(intent);
    }

    public static void addListener(DebugNetTrafficListener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(DebugNetTrafficListener listener) {
        if (listener != null) {
            LISTENERS.remove(listener);
        }
    }

    public static void clearListeners() {
        LISTENERS.clear();
    }

    public static boolean isRunning() {
        return sRunning;
    }

    public static void setConfig(DebugNetConfig config) {
        sConfig = config == null ? DebugNetConfig.defaultConfig() : config;
    }

    public static DebugNetConfig getConfig() {
        return sConfig;
    }

    public static void setPostProcessor(DebugNetPostProcessor processor) {
        sPostProcessor = processor;
    }

    public static void clearPostProcessor() {
        sPostProcessor = null;
    }

    static void setRunning(boolean running) {
        sRunning = running;
    }

    static void dispatch(DebugNetEvent event) {
        if (event == null) {
            return;
        }
        enqueueDispatch(event);
    }

    private static void enqueueDispatch(DebugNetEvent event) {
        WORK_HANDLER.post(() -> dispatchOnWorker(event));
    }

    private static void dispatchOnWorker(DebugNetEvent event) {
        DebugNetEvent finalEvent = event;
        try {
            DebugNetPostProcessor processor = sPostProcessor;
            if (processor != null) {
                DebugNetPayload payload = processor.process(new DebugNetPayload(event));
                if (payload != null && payload.getEvent() != null) {
                    finalEvent = payload.getEvent();
                }
            }
        } catch (Throwable throwable) {
            finalEvent.setFailureReason(throwable.getMessage());
        }
        finalEvent.refreshTexts();
        for (DebugNetTrafficListener listener : LISTENERS) {
            if (listener == null) {
                continue;
            }
            boolean next = true;
            try {
                next = listener.onTrafficEvent(finalEvent);
            } catch (Throwable ignored) {
            }
            if (!next) {
                return;
            }
        }
    }

    private static DebugNetEvent buildConfigErrorEvent(String message) {
        DebugNetEvent event = new DebugNetEvent(TrafficDirection.UPLOAD, "CONFIG", "", 0, "", 0, 0);
        event.setFailureReason(message);
        event.setRequestPath("/debug-net/config");
        event.setDisplayText("配置错误: " + message);
        return event;
    }
}
