package com.newchar.debug.monitor.hook;

import android.content.Context;
import android.util.Log;

import com.swift.sandhook.SandHook;
import com.swift.sandhook.hook.HookMethod;

import java.lang.reflect.Method;

public final class SandHookManager {

    private static final String TAG = "SandHookManager";

    private static volatile boolean sInitialized = false;

    private static Context sApplicationContext;

    private SandHookManager() {
    }

    public static synchronized void init(Context context) {
        if (sInitialized) {
            return;
        }
        try {
            sApplicationContext = context.getApplicationContext();
            SandHook.init(context);
            sInitialized = true;
            Log.i(TAG, "SandHook initialized successfully");
        } catch (Throwable t) {
            Log.e(TAG, "SandHook init failed", t);
        }
    }

    public static boolean isInitialized() {
        return sInitialized;
    }

    public static Context getContext() {
        return sApplicationContext;
    }

    public static void hookMethod(Method targetMethod, HookWrapper hookWrapper) throws Throwable {
        ensureInitialized();
        try {
            HookMethod hookMethod = new HookMethod(targetMethod);
            hookMethod.hook();
            hookMethod.setHookHandler(hookWrapper);
            Log.d(TAG, "Hooked method: " + targetMethod.getDeclaringClass().getSimpleName()
                    + "." + targetMethod.getName());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook: " + targetMethod.getDeclaringClass().getName()
                    + "." + targetMethod.getName(), t);
            throw t;
        }
    }

    public static void unhookMethod(Method targetMethod) {
        try {
            HookMethod.unHook(targetMethod);
        } catch (Exception e) {
            Log.w(TAG, "Failed to unhook: " + targetMethod.getName(), e);
        }
    }

    private static void ensureInitialized() {
        if (!sInitialized) {
            throw new IllegalStateException("SandHookManager not initialized. Call init(Context) first.");
        }
    }

    public abstract static class HookWrapper extends HookMethod.HookHandler {

        @Override
        protected void beforeHookedMethod(HookParam param) throws Throwable {
            onBeforeHook(param);
        }

        @Override
        protected void afterHookedMethod(HookParam param) throws Throwable {
            onAfterHook(param);
        }

        protected abstract void onBeforeHook(HookParam param) throws Throwable;

        protected void onAfterHook(HookParam param) throws Throwable {
        }
    }
}
