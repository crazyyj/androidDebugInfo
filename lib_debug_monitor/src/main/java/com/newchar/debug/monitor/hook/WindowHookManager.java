package com.newchar.debug.monitor.hook;

import android.app.Dialog;
import android.util.Log;
import android.widget.PopupWindow;

import com.swift.sandhook.hook.HookMethod.HookParam;

import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WindowHookManager {

    private static final String TAG = "WindowHookManager";

    private static volatile boolean sHooksInstalled = false;

    private static final CopyOnWriteArrayList<WindowHookCallback> sCallbacks = new CopyOnWriteArrayList<>();

    private WindowHookManager() {
    }

    public static void installHooks() {
        if (sHooksInstalled) {
            Log.w(TAG, "Window hooks already installed");
            return;
        }
        SandHookManager.ensureInitialized();
        try {
            hookPopupWindowShow();
            hookPopupWindowDismiss();
            hookDialogShow();
            hookDialogDismiss();

            sHooksInstalled = true;
            Log.i(TAG, "All window hooks installed successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install window hooks", t);
        }
    }

    public static void uninstallHooks() {
        if (!sHooksInstalled) {
            return;
        }
        try {
            unhookPopupWindowShow();
            unhookPopupWindowDismiss();
            unhookDialogShow();
            unhookDialogDismiss();

            sHooksInstalled = false;
            sCallbacks.clear();
            Log.i(TAG, "All window hooks uninstalled");
        } catch (Exception e) {
            Log.e(TAG, "Error during uninstall", e);
        }
    }

    public static boolean isHooksInstalled() {
        return sHooksInstalled;
    }

    public static void addCallback(WindowHookCallback callback) {
        if (callback != null && !sCallbacks.contains(callback)) {
            sCallbacks.add(callback);
        }
    }

    public static void removeCallback(WindowHookCallback callback) {
        sCallbacks.remove(callback);
    }

    private static void notifyPopupWindowShow(PopupWindow popupWindow) {
        for (WindowHookCallback callback : sCallbacks) {
            try {
                callback.onPopupWindowShow(popupWindow);
            } catch (Throwable t) {
                Log.w(TAG, "Error in onPopupWindowShow callback", t);
            }
        }
    }

    private static void notifyPopupWindowDismiss(PopupWindow popupWindow) {
        for (WindowHookCallback callback : sCallbacks) {
            try {
                callback.onPopupWindowDismiss(popupWindow);
            } catch (Throwable t) {
                Log.w(TAG, "Error in onPopupWindowDismiss callback", t);
            }
        }
    }

    private static void notifyDialogShow(Dialog dialog) {
        for (WindowHookCallback callback : sCallbacks) {
            try {
                callback.onDialogShow(dialog);
            } catch (Throwable t) {
                Log.w(TAG, "Error in onDialogShow callback", t);
            }
        }
    }

    private static void notifyDialogDismiss(Dialog dialog) {
        for (WindowHookCallback callback : sCallbacks) {
            try {
                callback.onDialogDismiss(dialog);
            } catch (Throwable t) {
                Log.w(TAG, "Error in onDialogDismiss callback", t);
            }
        }
    }

    private static Method getDeclaredMethod(Class<?> clazz, String name, Class<?>... paramTypes)
            throws NoSuchMethodException {
        Method method = clazz.getDeclaredMethod(name, paramTypes);
        method.setAccessible(true);
        return method;
    }

    private static void hookPopupWindowShow() throws Throwable {
        Method showMethod = getDeclaredMethod(PopupWindow.class, "show");

        SandHookManager.hookMethod(showMethod, new SandHookManager.HookWrapper() {
            @Override
            protected void onBeforeHook(HookParam param) throws Throwable {
                Object thisObject = param.thisObject;
                if (thisObject instanceof PopupWindow) {
                    PopupWindow popupWindow = (PopupWindow) thisObject;
                    notifyPopupWindowShow(popupWindow);
                }
            }
        });
    }

    private static void unhookPopupWindowShow() {
        try {
            SandHookManager.unhookMethod(getDeclaredMethod(PopupWindow.class, "show"));
        } catch (Exception e) {
            Log.w(TAG, "Failed to unhook PopupWindow.show", e);
        }
    }

    private static void hookPopupWindowDismiss() throws Throwable {
        Method dismissMethod = getDeclaredMethod(PopupWindow.class, "dismiss");

        SandHookManager.hookMethod(dismissMethod, new SandHookManager.HookWrapper() {
            @Override
            protected void onBeforeHook(HookParam param) throws Throwable {
                Object thisObject = param.thisObject;
                if (thisObject instanceof PopupWindow) {
                    PopupWindow popupWindow = (PopupWindow) thisObject;
                    notifyPopupWindowDismiss(popupWindow);
                }
            }
        });
    }

    private static void unhookPopupWindowDismiss() {
        try {
            SandHookManager.unhookMethod(getDeclaredMethod(PopupWindow.class, "dismiss"));
        } catch (Exception e) {
            Log.w(TAG, "Failed to unhook PopupWindow.dismiss", e);
        }
    }

    private static void hookDialogShow() throws Throwable {
        Method showMethod = getDeclaredMethod(Dialog.class, "show");

        SandHookManager.hookMethod(showMethod, new SandHookManager.HookWrapper() {
            @Override
            protected void onBeforeHook(HookParam param) throws Throwable {
                Object thisObject = param.thisObject;
                if (thisObject instanceof Dialog) {
                    Dialog dialog = (Dialog) thisObject;
                    notifyDialogShow(dialog);
                }
            }
        });
    }

    private static void unhookDialogShow() {
        try {
            SandHookManager.unhookMethod(getDeclaredMethod(Dialog.class, "show"));
        } catch (Exception e) {
            Log.w(TAG, "Failed to unhook Dialog.show", e);
        }
    }

    private static void hookDialogDismiss() throws Throwable {
        Method dismissMethod = getDeclaredMethod(Dialog.class, "dismiss");

        SandHookManager.hookMethod(dismissMethod, new SandHookManager.HookWrapper() {
            @Override
            protected void onBeforeHook(HookParam param) throws Throwable {
                Object thisObject = param.thisObject;
                if (thisObject instanceof Dialog) {
                    Dialog dialog = (Dialog) thisObject;
                    notifyDialogDismiss(dialog);
                }
            }
        });
    }

    private static void unhookDialogDismiss() {
        try {
            SandHookManager.unhookMethod(getDeclaredMethod(Dialog.class, "dismiss"));
        } catch (Exception e) {
            Log.w(TAG, "Failed to unhook Dialog.dismiss", e);
        }
    }
}
