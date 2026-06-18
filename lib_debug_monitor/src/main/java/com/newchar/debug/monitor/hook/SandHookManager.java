package com.newchar.debug.monitor.hook;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class SandHookManager {

    private static final String TAG = "SandHookManager";
    private static final String SAND_HOOK_CLASS_NAME = "com.swift.sandhook.SandHook";
    private static final String HOOK_METHOD_CLASS_NAME = "com.swift.sandhook.hook.HookMethod";

    private static volatile boolean sInitialized = false;
    private static volatile boolean sSandHookAvailable = false;

    private static Context sApplicationContext;

    private SandHookManager() {
    }

    /**
     * 初始化 SandHook 运行环境。
     */
    public static synchronized void init(Context context) {
        if (sInitialized) {
            return;
        }
        try {
            sApplicationContext = context.getApplicationContext();
            Method initMethod = Class.forName(SAND_HOOK_CLASS_NAME).getDeclaredMethod("init", Context.class);
            initMethod.setAccessible(true);
            initMethod.invoke(null, context);
            sSandHookAvailable = true;
            sInitialized = true;
            Log.i(TAG, "SandHook initialized successfully");
        } catch (Throwable t) {
            sSandHookAvailable = false;
            sInitialized = true;
            Log.w(TAG, "SandHook is unavailable, hook features will be skipped", t);
        }
    }

    /**
     * 判断 Hook 管理器是否已经完成初始化流程。
     */
    public static boolean isInitialized() {
        return sInitialized;
    }

    /**
     * 获取初始化时传入的 Application Context。
     */
    public static Context getContext() {
        return sApplicationContext;
    }

    /**
     * 判断当前运行环境是否提供 SandHook。
     */
    public static boolean isSandHookAvailable() {
        return sSandHookAvailable;
    }

    /**
     * 确认 Hook 管理器已经初始化且运行时存在 SandHook。
     */
    public static void ensureInitialized() {
        if (!sInitialized) {
            throw new IllegalStateException("SandHookManager not initialized. Call init(Context) first.");
        }
        if (!sSandHookAvailable) {
            throw new IllegalStateException("SandHook is unavailable in current runtime.");
        }
    }

    /**
     * 安装指定方法的 Hook。
     */
    public static void hookMethod(Method targetMethod, HookWrapper hookWrapper) throws Throwable {
        ensureInitialized();
        try {
            Class<?> hookMethodClass = Class.forName(HOOK_METHOD_CLASS_NAME);
            Constructor<?> constructor = hookMethodClass.getDeclaredConstructor(Method.class);
            constructor.setAccessible(true);
            Object hookMethod = constructor.newInstance(targetMethod);
            Method hook = hookMethodClass.getDeclaredMethod("hook");
            hook.setAccessible(true);
            hook.invoke(hookMethod);
            invokeSetHookHandler(hookMethodClass, hookMethod, hookWrapper);
            Log.d(TAG, "Hooked method: " + targetMethod.getDeclaringClass().getSimpleName()
                    + "." + targetMethod.getName());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook: " + targetMethod.getDeclaringClass().getName()
                    + "." + targetMethod.getName(), t);
            throw t;
        }
    }

    /**
     * 卸载指定方法的 Hook。
     */
    public static void unhookMethod(Method targetMethod) {
        try {
            Class<?> hookMethodClass = Class.forName(HOOK_METHOD_CLASS_NAME);
            Method unHook = hookMethodClass.getDeclaredMethod("unHook", Method.class);
            unHook.setAccessible(true);
            unHook.invoke(null, targetMethod);
        } catch (Exception e) {
            Log.w(TAG, "Failed to unhook: " + targetMethod.getName(), e);
        }
    }

    /**
     * 尝试把 Hook 回调交给 SandHook。
     */
    private static void invokeSetHookHandler(Class<?> hookMethodClass, Object hookMethod,
                                             HookWrapper hookWrapper) throws Exception {
        for (Method method : hookMethodClass.getDeclaredMethods()) {
            if ("setHookHandler".equals(method.getName()) && method.getParameterTypes().length == 1) {
                method.setAccessible(true);
                method.invoke(hookMethod, hookWrapper);
                return;
            }
        }
        throw new NoSuchMethodException("setHookHandler");
    }

    public abstract static class HookWrapper {

        /**
         * Hook 方法执行前的回调。
         */
        protected abstract void onBeforeHook(HookParam param) throws Throwable;

        /**
         * Hook 方法执行后的回调。
         */
        protected void onAfterHook(HookParam param) throws Throwable {
        }
    }

    public static final class HookParam {

        public Object thisObject;
        public Object[] args;
        public Object result;
        public Throwable throwable;
    }
}
