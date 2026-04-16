package com.newchar.debug.net;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Debug 网络监控入口。
 */
public final class DebugNetMonitor {

    private static final List<DebugNetTrafficListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean sRunning;

    private DebugNetMonitor() {
    }

    public static boolean start(Context context) {
        if (context == null) {
            return false;
        }
        Context appContext = context.getApplicationContext();
        Intent prepareIntent = VpnService.prepare(appContext);
        if (prepareIntent != null) {
            prepareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(prepareIntent);
            return false;
        }
        Intent intent = new Intent(appContext, DebugNetVpnService.class);
        intent.setAction(DebugNetVpnService.ACTION_START);
        appContext.startService(intent);
        return true;
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

    static void setRunning(boolean running) {
        sRunning = running;
    }

    static void dispatch(DebugNetEvent event) {
        if (event == null) {
            return;
        }
        for (DebugNetTrafficListener listener : LISTENERS) {
            if (listener == null) {
                continue;
            }
            boolean next = true;
            try {
                next = listener.onTrafficEvent(event);
            } catch (Throwable ignored) {
            }
            if (!next) {
                return;
            }
        }
    }
}
