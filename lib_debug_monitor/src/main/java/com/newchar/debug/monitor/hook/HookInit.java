package com.newchar.debug.monitor.hook;

import android.content.Context;
import android.util.Log;

public final class HookInit {

    private static final String TAG = "HookInit";

    private static volatile boolean sHookEnabled = true;

    private HookInit() {
    }

    public static void init(Context context) {
        if (!sHookEnabled) {
            Log.i(TAG, "Hook is disabled, skip initialization");
            return;
        }
        try {
            SandHookManager.init(context);
            WindowHookManager.installHooks();
            Log.i(TAG, "Hook system initialized successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize hook system", t);
        }
    }

    public static void destroy() {
        try {
            WindowHookManager.uninstallHooks();
            Log.i(TAG, "Hook system destroyed");
        } catch (Exception e) {
            Log.w(TAG, "Error during hook destruction", e);
        }
    }

    public static void setHookEnabled(boolean enabled) {
        sHookEnabled = enabled;
        Log.d(TAG, "Hook enabled: " + enabled);
    }

    public static boolean isHookEnabled() {
        return sHookEnabled;
    }
}
