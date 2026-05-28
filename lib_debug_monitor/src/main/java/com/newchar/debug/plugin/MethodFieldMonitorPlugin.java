package com.newchar.debug.plugin;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;

import com.newchar.debug.utils.ViewUtils;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.utils.HandleWrapper;
import com.newchar.debug.monitor.jvmti.DebugStackMotion;
import com.newchar.debug.monitor.jvmti.DebugStackMotionAgent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用于展示方法进栈与字段修改事件的监控插件。
 */
public class MethodFieldMonitorPlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "Method_Field_Monitor";
    private static final Object sConfigLock = new Object();
    private static final Set<String> sWatchFieldKeys = new HashSet<>();
    private static final Set<String> sSilentWatchFieldKeys = new HashSet<>();
    private static final Set<String> sWatchMethodKeys = new HashSet<>();
    private static final Set<String> sWatchClassPrefixes = new HashSet<>();
    private static final Map<String, Integer> sUiClassTypeCache = new HashMap<>();
    private static final int MAX_EVENT_LOG_SIZE = 300;
    private static final int MAX_FLUSH_BATCH = 80;
    private static final int UI_TYPE_NONE = 0;
    private static final int UI_TYPE_DIALOG = 1;
    private static final int UI_TYPE_POPUP = 2;

    private final Handler mMainHandler = HandleWrapper.getMainHandler();
    private final List<String> mEventLogs = new ArrayList<>();
    private final ConcurrentLinkedQueue<String> mPendingLogs = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean mFlushScheduled = new AtomicBoolean(false);
    private ListView mMonitorListView;
    private ArrayAdapter<String> mAdapter;

    /**
     * 批量设置字段监听项，格式：全类名#字段名。
     */
    public static void setWatchFieldKeys(String... fieldKeys) {
        synchronized (sConfigLock) {
            sWatchFieldKeys.clear();
        }
        cacheWatchFieldKeys(fieldKeys);
        registerWatchFields(fieldKeys);
    }

    /**
     * 增加字段监听项。
     */
    public static void addWatchField(Class<?> ownerClass, String fieldName) {
        if (ownerClass == null || TextUtils.isEmpty(fieldName)) {
            return;
        }
        addWatchField(ownerClass.getName(), fieldName);
    }

    /**
     * 增加字段监听项。
     */
    public static void addWatchField(String ownerClassName, String fieldName) {
        if (TextUtils.isEmpty(ownerClassName) || TextUtils.isEmpty(fieldName)) {
            return;
        }
        final String fieldKey = buildFieldKey(ownerClassName, fieldName);
        synchronized (sConfigLock) {
            sWatchFieldKeys.add(fieldKey);
        }
        try {
            Class<?> ownerClass = Class.forName(ownerClassName);
            DebugStackMotion.registerField(ownerClass, fieldName);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 增加字段监控白名单：参与 JVMTI 监控，但不在插件列表上屏。
     */
    public static void addSilentWatchField(Class<?> ownerClass, String fieldName) {
        if (ownerClass == null || TextUtils.isEmpty(fieldName)) {
            return;
        }
        addSilentWatchField(ownerClass.getName(), fieldName);
    }

    /**
     * 增加字段监控白名单：参与 JVMTI 监控，但不在插件列表上屏。
     */
    public static void addSilentWatchField(String ownerClassName, String fieldName) {
        if (TextUtils.isEmpty(ownerClassName) || TextUtils.isEmpty(fieldName)) {
            return;
        }
        final String fieldKey = buildFieldKey(ownerClassName, fieldName);
        synchronized (sConfigLock) {
            sSilentWatchFieldKeys.add(fieldKey);
        }
        try {
            Class<?> ownerClass = Class.forName(ownerClassName);
            DebugStackMotion.registerField(ownerClass, fieldName);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 批量设置字段监控白名单：参与 JVMTI 监控，但不在插件列表上屏。
     */
    public static void setSilentWatchFieldKeys(String... fieldKeys) {
        synchronized (sConfigLock) {
            sSilentWatchFieldKeys.clear();
            if (fieldKeys != null) {
                for (String key : fieldKeys) {
                    if (!TextUtils.isEmpty(key)) {
                        sSilentWatchFieldKeys.add(key);
                    }
                }
            }
        }
        registerWatchFields(fieldKeys);
    }

    /**
     * 清理字段监控白名单。
     */
    public static void clearSilentWatchFields() {
        synchronized (sConfigLock) {
            sSilentWatchFieldKeys.clear();
        }
    }

    /**
     * 清理字段监听项。
     */
    public static void clearWatchFields() {
        synchronized (sConfigLock) {
            sWatchFieldKeys.clear();
        }
    }

    /**
     * 增加方法监听项，格式：全类名#方法名。
     */
    public static void addWatchMethod(Class<?> ownerClass, String methodName) {
        if (ownerClass == null || TextUtils.isEmpty(methodName)) {
            return;
        }
        addWatchMethod(ownerClass.getName(), methodName);
    }

    /**
     * 增加方法监听项，格式：全类名#方法名。
     */
    public static void addWatchMethod(String ownerClassName, String methodName) {
        if (TextUtils.isEmpty(ownerClassName) || TextUtils.isEmpty(methodName)) {
            return;
        }
        final String methodKey = buildMethodKey(ownerClassName, methodName);
        synchronized (sConfigLock) {
            sWatchMethodKeys.add(methodKey);
        }
        registerWatchMethods(methodKey);
    }

    /**
     * 批量设置方法监听项，格式：全类名#方法名。
     */
    public static void setWatchMethodKeys(String... methodKeys) {
        synchronized (sConfigLock) {
            sWatchMethodKeys.clear();
            if (methodKeys != null) {
                for (String key : methodKeys) {
                    if (!TextUtils.isEmpty(key)) {
                        sWatchMethodKeys.add(key);
                    }
                }
            }
        }
        registerWatchMethods(methodKeys);
    }

    /**
     * 清理方法监听项。
     */
    public static void clearWatchMethods() {
        synchronized (sConfigLock) {
            sWatchMethodKeys.clear();
        }
    }

    /**
     * 增加类名前缀监听项（例如 com.newcharbase.）。
     */
    public static void addWatchClassPrefix(String classPrefix) {
        if (TextUtils.isEmpty(classPrefix)) {
            return;
        }
        synchronized (sConfigLock) {
            sWatchClassPrefixes.add(classPrefix);
        }
    }

    /**
     * 批量设置类名前缀监听项。
     */
    public static void setWatchClassPrefixes(String... classPrefixes) {
        synchronized (sConfigLock) {
            sWatchClassPrefixes.clear();
            if (classPrefixes != null) {
                for (String prefix : classPrefixes) {
                    if (!TextUtils.isEmpty(prefix)) {
                        sWatchClassPrefixes.add(prefix);
                    }
                }
            }
        }
    }

    /**
     * 清理类名前缀监听项。
     */
    public static void clearWatchClassPrefixes() {
        synchronized (sConfigLock) {
            sWatchClassPrefixes.clear();
        }
    }

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        initMonitorView(pluginContainerView);
        DebugStackMotion.init();
        DebugStackMotionAgent.startAgent();
        DebugStackMotion.addRawMethodCallback(mRawMethodCallback);
        DebugStackMotion.addRawVariableCallback(mRawVariableCallback);
        DebugStackMotion.setFieldModificationEnabled(true);
        registerWatchFields(copyWatchFieldKeysArray());
        registerWatchFields(copySilentWatchFieldKeysArray());
        registerWatchMethods(copyWatchMethodKeysArray());
        enqueueLog("[Monitor] plugin loaded");
    }

    @Override
    public void onShow() {
        ViewUtils.setVisibility(mMonitorListView, View.VISIBLE);
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(mMonitorListView, View.GONE);
    }

    @Override
    public void onUnload() {
        DebugStackMotion.removeRawMethodCallback(mRawMethodCallback);
        DebugStackMotion.removeRawVariableCallback(mRawVariableCallback);
        mMainHandler.removeCallbacks(mFlushTask);
        mPendingLogs.clear();
        mFlushScheduled.set(false);
        mEventLogs.clear();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void initMonitorView(ViewGroup containerView) {
        if (mMonitorListView == null) {
            mMonitorListView = new ListView(containerView.getContext());
            mMonitorListView.setBackgroundColor(Color.argb(77, 128, 128, 128));
        }
        if (mAdapter == null) {
            mAdapter = new ArrayAdapter<>(containerView.getContext(), android.R.layout.simple_list_item_1, mEventLogs);
            mMonitorListView.setAdapter(mAdapter);
        }
        if (mMonitorListView.getParent() instanceof ViewGroup) {
            ViewUtils.removeSelf(mMonitorListView);
        }
        containerView.addView(mMonitorListView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        ViewUtils.setVisibility(mMonitorListView, View.GONE);
    }

    private void enqueueLog(String line) {
        mPendingLogs.offer(line);
        if (mFlushScheduled.compareAndSet(false, true)) {
            mMainHandler.post(mFlushTask);
        }
    }

    private final Runnable mFlushTask = new Runnable() {
        @Override
        public void run() {
            int count = 0;
            String line;
            while (count < MAX_FLUSH_BATCH && (line = mPendingLogs.poll()) != null) {
                mEventLogs.add(0, line);
                count++;
            }
            while (mEventLogs.size() > MAX_EVENT_LOG_SIZE) {
                mEventLogs.remove(mEventLogs.size() - 1);
            }
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
            if (!mPendingLogs.isEmpty()) {
                mMainHandler.post(this);
                return;
            }
            mFlushScheduled.set(false);
            if (!mPendingLogs.isEmpty() && mFlushScheduled.compareAndSet(false, true)) {
                mMainHandler.post(this);
            }
        }
    };

    private final DebugStackMotion.RawMethodCallback mRawMethodCallback =
            new DebugStackMotion.RawMethodCallback() {
                @Override
                public void onMethodVisit(String className, String methodName, String methodDesc, boolean isEnter) {
                    if (!isEnter) {
                        return;
                    }
                    final String normalizedClassName = normalizeClassName(className);
                    if (isUiWindowLifecycleMethod(normalizedClassName, methodName)) {
                        return;
                    }
                    enqueueLog("[M] " + normalizedClassName + "#" + methodName + " " + safeValue(methodDesc));
                }
            };

    private final DebugStackMotion.RawVariableCallback mRawVariableCallback =
            new DebugStackMotion.RawVariableCallback() {
                @Override
                public void onVariableVisit(String className, String methodName, String methodDesc, String fieldName,
                        Object newValue, boolean isSet) {
                    if (!isSet) {
                        return;
                    }
                    final String normalizedClassName = normalizeClassName(className);
                    if (isUiWindowLifecycleMethod(normalizedClassName, methodName)) {
                        return;
                    }
                    if (isSilentWatchField(normalizedClassName, fieldName)) {
                        return;
                    }
                    enqueueLog("[F] " + normalizedClassName + "#" + safeValue(fieldName)
                            + " by " + safeValue(methodName) + " " + safeValue(methodDesc));
                }
            };

    private static void registerWatchFields(String... fieldKeys) {
        if (fieldKeys == null) {
            return;
        }
        for (String key : fieldKeys) {
            if (TextUtils.isEmpty(key)) {
                continue;
            }
            int index = key.lastIndexOf('#');
            if (index <= 0 || index >= key.length() - 1) {
                continue;
            }
            String ownerClassName = key.substring(0, index);
            String fieldName = key.substring(index + 1);
            try {
                Class<?> ownerClass = Class.forName(ownerClassName);
                DebugStackMotion.registerField(ownerClass, fieldName);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void registerWatchMethods(String... methodKeys) {
        if (methodKeys == null) {
            return;
        }
        for (String key : methodKeys) {
            if (TextUtils.isEmpty(key)) {
                continue;
            }
            int index = key.lastIndexOf('#');
            if (index <= 0 || index >= key.length() - 1) {
                continue;
            }
            String ownerClassName = key.substring(0, index);
            String methodName = key.substring(index + 1);
            try {
                Class<?> ownerClass = Class.forName(ownerClassName);
                Method[] declaredMethods = ownerClass.getDeclaredMethods();
                for (Method method : declaredMethods) {
                    if (method == null || !methodName.equals(method.getName())) {
                        continue;
                    }
                    DebugStackMotion.registerMethod(ownerClass, method, true);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static String buildFieldKey(String ownerClassName, String fieldName) {
        return ownerClassName + "#" + fieldName;
    }

    private static String buildMethodKey(String ownerClassName, String methodName) {
        return ownerClassName + "#" + methodName;
    }

    private static String normalizeClassName(String className) {
        if (TextUtils.isEmpty(className)) {
            return "";
        }
        String normalized = className;
        if (normalized.startsWith("L") && normalized.endsWith(";") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.replace('/', '.');
    }

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }

    private static boolean isUiWindowLifecycleMethod(String className, String methodName) {
        if (TextUtils.isEmpty(className) || TextUtils.isEmpty(methodName)) {
            return false;
        }
        if (!methodName.startsWith("show")
                && !"dismiss".equals(methodName)
                && !"cancel".equals(methodName)
                && !"hide".equals(methodName)) {
            return false;
        }
        int classType = classifyUiClass(className);
        return classType == UI_TYPE_DIALOG || classType == UI_TYPE_POPUP;
    }

    private static int classifyUiClass(String className) {
        synchronized (sConfigLock) {
            Integer cached = sUiClassTypeCache.get(className);
            if (cached != null) {
                return cached;
            }
        }
        int classType = UI_TYPE_NONE;
        try {
            Class<?> clazz = Class.forName(className);
            if (Dialog.class.isAssignableFrom(clazz)) {
                classType = UI_TYPE_DIALOG;
            } else if (PopupWindow.class.isAssignableFrom(clazz)) {
                classType = UI_TYPE_POPUP;
            }
        } catch (Throwable ignored) {
            if (className.contains("Dialog")) {
                classType = UI_TYPE_DIALOG;
            } else if (className.endsWith(".PopupWindow")) {
                classType = UI_TYPE_POPUP;
            }
        }
        synchronized (sConfigLock) {
            sUiClassTypeCache.put(className, classType);
        }
        return classType;
    }

    private static boolean isSilentWatchField(String normalizedClassName, String fieldName) {
        if (TextUtils.isEmpty(normalizedClassName) || TextUtils.isEmpty(fieldName)) {
            return false;
        }
        final String key = buildFieldKey(normalizedClassName, fieldName);
        synchronized (sConfigLock) {
            return sSilentWatchFieldKeys.contains(key);
        }
    }

    private static String[] copyWatchFieldKeysArray() {
        synchronized (sConfigLock) {
            return sWatchFieldKeys.toArray(new String[0]);
        }
    }

    private static String[] copySilentWatchFieldKeysArray() {
        synchronized (sConfigLock) {
            return sSilentWatchFieldKeys.toArray(new String[0]);
        }
    }

    private static String[] copyWatchMethodKeysArray() {
        synchronized (sConfigLock) {
            return sWatchMethodKeys.toArray(new String[0]);
        }
    }

    private static void cacheWatchFieldKeys(String... fieldKeys) {
        if (fieldKeys == null) {
            return;
        }
        synchronized (sConfigLock) {
            for (String key : fieldKeys) {
                if (!TextUtils.isEmpty(key)) {
                    sWatchFieldKeys.add(key);
                }
            }
        }
    }

}
