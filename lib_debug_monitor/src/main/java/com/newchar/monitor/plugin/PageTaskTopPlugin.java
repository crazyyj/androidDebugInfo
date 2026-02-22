package com.newchar.monitor.plugin;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import com.newchar.debug.common.utils.ViewUtils;
import com.newchar.debugview.api.PluginContext;
import com.newchar.debugview.api.ScreenDisplayPlugin;
import com.newchar.debugview.lifecycle.AppLifecycleManager;
import com.newchar.debugview.lifecycle.DefaultActivityCallback;
import com.newchar.debugview.utils.HandleWrapper;
import com.newchar.monitor.jvmti.DebugStackMotion;
import com.newchar.monitor.toppage.FragmentWrapper;
import com.newchar.monitor.toppage.FragmentXWrapper;
import com.newchar.monitor.plugin.view.TaskTopView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author newChar
 * date 2025/6/8
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public class PageTaskTopPlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "Task_Top";
    private static final long FIELD_TICK_INTERVAL_MS = 2000L;
    private static final int UI_TYPE_NONE = 0;
    private static final int UI_TYPE_DIALOG = 1;
    private static final int UI_TYPE_POPUP = 2;
    private static final Object sWatchFieldLock = new Object();
    private static final Set<String> sWatchFieldKeys = new HashSet<>();

    private Object mWindowManagerGlobal;
    private Field mWindowViewsField;
    private Field mWindowmParamsField;
    private TaskTopView mTaskTopView;
    private boolean mDialogPopupHooked;
    private boolean mLifecycleCallbackRegistered;
    private boolean mFieldTickRunning;
    private final Handler mMainHandler = HandleWrapper.getMainHandler();
    private final Set<String> mActiveDialogNodes = new HashSet<>();
    private final Set<String> mActivePopupNodes = new HashSet<>();
    private final Map<Integer, FragmentState> mFragmentStates = new HashMap<>();
    private final Set<Integer> mVisibleFragmentCodes = new HashSet<>();
    private final Map<Activity, FragmentXWrapper> mAndroidXFragmentWrappers = new HashMap<>();
    private final Map<Activity, FragmentWrapper> mPlatformFragmentWrappers = new HashMap<>();
    private final Map<String, Integer> mUiClassTypeCache = new HashMap<>();
    private int mFieldTickCount;
    private String mFieldTickState = "init";
    private static long sFieldTickVersion;

    /**
     * 批量设置需要监听的字段（格式：全类名#字段名）。
     */
    public static void setWatchFieldKeys(String... fieldKeys) {
        synchronized (sWatchFieldLock) {
            sWatchFieldKeys.clear();
        }
        if (fieldKeys == null) {
            return;
        }
        for (String key : fieldKeys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            synchronized (sWatchFieldLock) {
                sWatchFieldKeys.add(key);
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

    /**
     * 增加一个需要监听的字段。
     */
    public static void addWatchField(Class<?> ownerClass, String fieldName) {
        if (ownerClass == null || fieldName == null || fieldName.isEmpty()) {
            return;
        }
        DebugStackMotion.registerField(ownerClass, fieldName);
        addWatchField(ownerClass.getName(), fieldName);
    }

    /**
     * 增加一个需要监听的字段。
     */
    public static void addWatchField(String ownerClassName, String fieldName) {
        if (ownerClassName == null || ownerClassName.isEmpty() || fieldName == null || fieldName.isEmpty()) {
            return;
        }
        try {
            Class<?> ownerClass = Class.forName(ownerClassName);
            DebugStackMotion.registerField(ownerClass, fieldName);
        } catch (Throwable ignored) {
        }
        synchronized (sWatchFieldLock) {
            sWatchFieldKeys.add(buildFieldKey(ownerClassName, fieldName));
        }
    }

    /**
     * 清理全部字段监听配置。
     */
    public static void clearWatchFields() {
        synchronized (sWatchFieldLock) {
            sWatchFieldKeys.clear();
        }
    }

    private static String buildFieldKey(String ownerClassName, String fieldName) {
        return ownerClassName + "#" + fieldName;
    }

    private static String normalizeClassName(String className) {
        if (className == null || className.isEmpty()) {
            return "";
        }
        String normalized = className;
        if (normalized.startsWith("L") && normalized.endsWith(";") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.replace('/', '.');
    }

    private static boolean shouldWatchField(String className, String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return false;
        }
        String fieldKey = buildFieldKey(normalizeClassName(className), fieldName);
        synchronized (sWatchFieldLock) {
            if (sWatchFieldKeys.isEmpty()) {
                return false;
            }
            return sWatchFieldKeys.contains(fieldKey);
        }
    }

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        initTaskTopView(pluginContainerView);
        DebugStackMotion.init();
        // 默认注册插件内部演示字段，确保字段修改链路可直接验证。
        addWatchField(PageTaskTopPlugin.class, "mFieldTickCount");
        addWatchField(PageTaskTopPlugin.class, "mFieldTickState");
        addWatchField(PageTaskTopPlugin.class, "sFieldTickVersion");
        hookDialogAndPopLifecycle();
        startFieldTickLoop();
        registerActivityLifecycle();
//        initGlobalData();
        initExistActivity();
    }

    private void initTaskTopView(ViewGroup containerView) {
        if (mTaskTopView == null) {
            mTaskTopView = new TaskTopView(containerView.getContext());
        }
        if (mTaskTopView.getParent() instanceof ViewGroup) {
            ViewUtils.removeSelf(mTaskTopView);
        }
        containerView.addView(mTaskTopView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }


    private void hookFragmentLifecycle(Activity activity) {
        if (Utils.isAndroidXEnv(activity.getClass())) {
            if (mAndroidXFragmentWrappers.containsKey(activity)) {
                return;
            }
            FragmentXWrapper fragmentXWrapper = new FragmentXWrapper(lifecycle);
            fragmentXWrapper.setFragmentLifeCycle(activity);
            mAndroidXFragmentWrappers.put(activity, fragmentXWrapper);
        } else {
            if (mPlatformFragmentWrappers.containsKey(activity)) {
                return;
            }
            FragmentWrapper fragmentWrapper = new FragmentWrapper(lifecycle);
            fragmentWrapper.setFragmentLifecycle(activity);
            mPlatformFragmentWrappers.put(activity, fragmentWrapper);
        }
    }

    private void unhookFragmentLifecycle(Activity activity) {
        FragmentXWrapper xWrapper = mAndroidXFragmentWrappers.remove(activity);
        if (xWrapper != null) {
            xWrapper.release();
        }
        FragmentWrapper wrapper = mPlatformFragmentWrappers.remove(activity);
        if (wrapper != null) {
            wrapper.release();
        }
    }

    private void releaseFragmentLifecycleHooks() {
        for (FragmentXWrapper wrapper : mAndroidXFragmentWrappers.values()) {
            if (wrapper != null) {
                wrapper.release();
            }
        }
        mAndroidXFragmentWrappers.clear();
        for (FragmentWrapper wrapper : mPlatformFragmentWrappers.values()) {
            if (wrapper != null) {
                wrapper.release();
            }
        }
        mPlatformFragmentWrappers.clear();
    }

    private void hookDialogAndPopLifecycle() {
        if (mDialogPopupHooked) {
            return;
        }
        mDialogPopupHooked = true;
        registerUiMethodInterests();
        DebugStackMotion.addRawMethodCallback(rawMethodCallback);
        DebugStackMotion.addRawVariableCallback(rawVariableCallback);
        DebugStackMotion.setFieldModificationEnabled(true);
    }

    private void registerUiMethodInterests() {
        registerMethodsByName(Dialog.class, true, "show", "dismiss", "cancel", "hide");
        registerMethodsByName(PopupWindow.class, true, "showAsDropDown", "showAtLocation", "dismiss");
    }

    private void registerMethodsByName(Class<?> ownerClass, boolean includeSubclasses, String... methodNames) {
        if (ownerClass == null || methodNames == null || methodNames.length == 0) {
            return;
        }
        Method[] methods = ownerClass.getDeclaredMethods();
        for (Method method : methods) {
            if (method == null) {
                continue;
            }
            String name = method.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            for (String targetName : methodNames) {
                if (!name.equals(targetName)) {
                    continue;
                }
                DebugStackMotion.registerMethod(ownerClass, method, includeSubclasses);
                break;
            }
        }
    }

    private void initGlobalData() {
        try {
            Class<?> clazz = null;
            if (mWindowManagerGlobal == null) {
                clazz = Class.forName("android.view.WindowManagerGlobal");
                Method methodGetInstance = clazz.getMethod("getInstance");
                mWindowManagerGlobal = methodGetInstance.invoke(null);
            }
            if (mWindowViewsField == null) {
                mWindowViewsField = clazz.getDeclaredField("mViews");
            }
            if (mWindowmParamsField == null) {
                mWindowmParamsField = clazz.getDeclaredField("mParams");
            }
            Log.e("NewChar", "initGlobalData, mWindowManagerGlobal = " + mWindowManagerGlobal);
        } catch (Exception e) {
            // 获取失败. 寻找方案2
        }
    }

    private void initExistActivity() {
        Set<Activity> allActivity = AppLifecycleManager.getInstance().getAllActivity();
        for (Activity activity : allActivity) {
            mTaskTopView.addActivity(activity);
            hookFragmentLifecycle(activity);
        }
    }

    private void registerActivityLifecycle() {
        if (mLifecycleCallbackRegistered) {
            return;
        }
        AppLifecycleManager.getInstance().addLifecycleCallback(mActivityCallback);
        mLifecycleCallbackRegistered = true;
    }

    private void unregisterActivityLifecycle() {
        if (!mLifecycleCallbackRegistered) {
            return;
        }
        AppLifecycleManager.getInstance().removeLifecycleCallback(mActivityCallback);
        mLifecycleCallbackRegistered = false;
    }

    private void startFieldTickLoop() {
        if (mFieldTickRunning) {
            return;
        }
        mFieldTickRunning = true;
        applyFieldTick();
        mMainHandler.postDelayed(mFieldTickTask, FIELD_TICK_INTERVAL_MS);
    }

    private void stopFieldTickLoop() {
        if (!mFieldTickRunning) {
            return;
        }
        mFieldTickRunning = false;
        mMainHandler.removeCallbacks(mFieldTickTask);
    }

    private void applyFieldTick() {
        mFieldTickCount += 1;
        mFieldTickState = "tick_" + mFieldTickCount;
        sFieldTickVersion += 1L;
    }

    private FragmentState ensureFragmentState(Context hostContext, int code, Class<?> pageClass) {
        FragmentState state = mFragmentStates.get(code);
        if (state == null) {
            String childText = pageClass.getCanonicalName() + "\n#" + code;
            state = new FragmentState(childText);
            mFragmentStates.put(code, state);
        }
        Activity hostActivity = resolveHostActivity(hostContext);
        if (hostActivity != null) {
            state.hostActivity = hostActivity;
        }
        return state;
    }

    private Activity resolveHostActivity(Context hostContext) {
        if (hostContext == null) {
            return AppLifecycleManager.getInstance().getLastActivity();
        }
        Context current = hostContext;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        if (current instanceof Activity) {
            return (Activity) current;
        }
        return AppLifecycleManager.getInstance().getLastActivity();
    }

    private void hideVisibleFragmentsForActivity(Activity activity) {
        if (activity == null || mTaskTopView == null) {
            return;
        }
        List<Integer> goneCodes = new ArrayList<>();
        for (Map.Entry<Integer, FragmentState> entry : mFragmentStates.entrySet()) {
            FragmentState state = entry.getValue();
            if (state != null && state.hostActivity == activity && mVisibleFragmentCodes.contains(entry.getKey())) {
                goneCodes.add(entry.getKey());
            }
        }
        for (Integer code : goneCodes) {
            FragmentState state = mFragmentStates.get(code);
            if (state == null) {
                continue;
            }
            if (mVisibleFragmentCodes.remove(code)) {
                mTaskTopView.removeFragment(activity, state.childText);
            }
        }
    }

    private void clearFragmentsForActivity(Activity activity) {
        if (activity == null) {
            return;
        }
        List<Integer> removedCodes = new ArrayList<>();
        for (Map.Entry<Integer, FragmentState> entry : mFragmentStates.entrySet()) {
            FragmentState state = entry.getValue();
            if (state != null && state.hostActivity == activity) {
                removedCodes.add(entry.getKey());
            }
        }
        for (Integer code : removedCodes) {
            mVisibleFragmentCodes.remove(code);
            mFragmentStates.remove(code);
        }
    }

    @Override
    public void onShow() {
        ViewUtils.setVisibility(mTaskTopView, View.VISIBLE);
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(mTaskTopView, View.GONE);
    }

    @Override
    public void onUnload() {
        mWindowManagerGlobal = null;
        mWindowViewsField = null;
        mWindowmParamsField = null;
        mDialogPopupHooked = false;
        stopFieldTickLoop();
        unregisterActivityLifecycle();
        synchronized (mActiveDialogNodes) {
            mActiveDialogNodes.clear();
        }
        synchronized (mActivePopupNodes) {
            mActivePopupNodes.clear();
        }
        mFragmentStates.clear();
        mVisibleFragmentCodes.clear();
        synchronized (mUiClassTypeCache) {
            mUiClassTypeCache.clear();
        }
        releaseFragmentLifecycleHooks();
        DebugStackMotion.removeRawMethodCallback(rawMethodCallback);
        DebugStackMotion.removeRawVariableCallback(rawVariableCallback);
        DebugStackMotion.setFieldModificationEnabled(false);
    }

    private List<View> getAllWindowsViews() {
        final List<View> mViews = new ArrayList<>();
        try {
            if (mWindowViewsField != null && mWindowManagerGlobal != null) {
                mWindowViewsField.setAccessible(true);
                Object viewList = mWindowViewsField.get(mWindowManagerGlobal);
                if (viewList instanceof List<?>) {
                    mViews.addAll(((List<View>) viewList));
                }
            }

        } catch (Exception e) {
        }
        return mViews;
    }

    private static final class FragmentState {
        final String childText;
        Activity hostActivity;

        FragmentState(String childText) {
            this.childText = childText;
        }
    }

    private final IPageLifecycle lifecycle = new IPageLifecycle() {
        @Override
        public void onPageCreate(Context hostContext, int code, Class<?> pageClass) {
            ensureFragmentState(hostContext, code, pageClass);
        }

        @Override
        public void onPageVisible(Context hostContext, int code, Class<?> pageClass) {
            if (mTaskTopView == null) {
                return;
            }
            FragmentState state = ensureFragmentState(hostContext, code, pageClass);
            if (!mVisibleFragmentCodes.add(code)) {
                return;
            }
            Activity hostActivity = resolveHostActivity(hostContext);
            if (hostActivity == null) {
                hostActivity = state.hostActivity;
            }
            if (hostActivity != null) {
                mTaskTopView.addFragment(hostActivity, state.childText);
            }
        }

        @Override
        public void onPageGone(Context hostContext, int code, Class<?> pageClass) {
            if (mTaskTopView == null) {
                return;
            }
            FragmentState state = mFragmentStates.get(code);
            if (state == null || !mVisibleFragmentCodes.remove(code)) {
                return;
            }
            Activity hostActivity = resolveHostActivity(hostContext);
            if (hostActivity == null) {
                hostActivity = state.hostActivity;
            }
            if (hostActivity != null) {
                mTaskTopView.removeFragment(hostActivity, state.childText);
            }
        }

        @Override
        public void onPageDestroy(Context hostContext, int code, Class<?> pageClass) {
            if (mTaskTopView == null) {
                return;
            }
            FragmentState state = mFragmentStates.remove(code);
            if (state == null) {
                return;
            }
            if (mVisibleFragmentCodes.remove(code)) {
                Activity hostActivity = resolveHostActivity(hostContext);
                if (hostActivity == null) {
                    hostActivity = state.hostActivity;
                }
                if (hostActivity != null) {
                    mTaskTopView.removeFragment(hostActivity, state.childText);
                }
            }
        }
    };

    private final DebugStackMotion.RawMethodCallback rawMethodCallback =
            new DebugStackMotion.RawMethodCallback() {
                @Override
                public void onMethodVisit(String className, String methodName, String methodDesc, boolean isEnter) {
                    if (mTaskTopView == null || className == null || methodName == null) {
                        return;
                    }
                    String realClassName = normalizeClassName(className);
                    if (realClassName.isEmpty()) {
                        return;
                    }
                    Activity hostActivity = AppLifecycleManager.getInstance().getLastActivity();
                    if (hostActivity == null) {
                        return;
                    }
                    if (isPopupClass(realClassName)) {
                        handlePopupEvent(hostActivity, realClassName, methodName);
                        return;
                    }
                    if (isDialogClass(realClassName)) {
                        handleDialogEvent(hostActivity, realClassName, methodName);
                    }
                }
            };

    private final DebugStackMotion.RawVariableCallback rawVariableCallback =
            new DebugStackMotion.RawVariableCallback() {
                @Override
                public void onVariableVisit(String className, String methodName, String methodDesc, String fieldName,
                        Object newValue, boolean isSet) {
                    if (!isSet || className == null || fieldName == null || !shouldWatchField(className, fieldName)) {
                        return;
                    }
                    Log.i(TAG_PLUGIN, "FieldSet class=" + className
                            + " field=" + fieldName
                            + " method=" + methodName
                            + " desc=" + methodDesc
                            + " value=" + newValue);
                }
            };

    private void handleDialogEvent(Activity hostActivity, String className, String methodName) {
        final String nodeText = "Dialog\n" + className;
        if (isShowMethod(methodName)) {
            synchronized (mActiveDialogNodes) {
                if (!mActiveDialogNodes.add(nodeText)) {
                    return;
                }
            }
            hostActivity.runOnUiThread(() -> mTaskTopView.addDialog(hostActivity, nodeText));
        } else if (isDismissMethod(methodName)) {
            synchronized (mActiveDialogNodes) {
                if (!mActiveDialogNodes.remove(nodeText)) {
                    return;
                }
            }
            hostActivity.runOnUiThread(() -> mTaskTopView.removeDialog(hostActivity, nodeText));
        }
    }

    private void handlePopupEvent(Activity hostActivity, String className, String methodName) {
        final String nodeText = "Popup\n" + className;
        if (isShowMethod(methodName)) {
            synchronized (mActivePopupNodes) {
                if (!mActivePopupNodes.add(nodeText)) {
                    return;
                }
            }
            hostActivity.runOnUiThread(() -> mTaskTopView.addPopup(hostActivity, nodeText));
        } else if (isDismissMethod(methodName)) {
            synchronized (mActivePopupNodes) {
                if (!mActivePopupNodes.remove(nodeText)) {
                    return;
                }
            }
            hostActivity.runOnUiThread(() -> mTaskTopView.removePopup(hostActivity, nodeText));
        }
    }

    private boolean isDialogClass(String className) {
        return classifyUiClass(className) == UI_TYPE_DIALOG;
    }

    private boolean isPopupClass(String className) {
        return classifyUiClass(className) == UI_TYPE_POPUP;
    }

    private int classifyUiClass(String className) {
        if (className == null || className.isEmpty()) {
            return UI_TYPE_NONE;
        }
        synchronized (mUiClassTypeCache) {
            Integer cachedType = mUiClassTypeCache.get(className);
            if (cachedType != null) {
                return cachedType;
            }
        }
        int type = UI_TYPE_NONE;
        try {
            Class<?> clazz = Class.forName(className);
            if (Dialog.class.isAssignableFrom(clazz)) {
                type = UI_TYPE_DIALOG;
            } else if (PopupWindow.class.isAssignableFrom(clazz)) {
                type = UI_TYPE_POPUP;
            }
        } catch (Throwable ignored) {
            if (className.contains("Dialog")) {
                type = UI_TYPE_DIALOG;
            } else if (className.endsWith(".PopupWindow")) {
                type = UI_TYPE_POPUP;
            }
        }
        synchronized (mUiClassTypeCache) {
            mUiClassTypeCache.put(className, type);
        }
        return type;
    }

    private boolean isShowMethod(String methodName) {
        return methodName.startsWith("show");
    }

    private boolean isDismissMethod(String methodName) {
        return "dismiss".equals(methodName)
                || "cancel".equals(methodName)
                || "hide".equals(methodName);
    }

    private final DefaultActivityCallback mActivityCallback = new DefaultActivityCallback() {
        @Override
        public void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState) {
            if (mTaskTopView == null) {
                return;
            }
            mTaskTopView.addActivity(activity);
            hookFragmentLifecycle(activity);
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if (mTaskTopView == null) {
                return;
            }
            mTaskTopView.addActivity(activity);
        }

        @Override
        public void onActivityStopped(Activity activity) {
            hideVisibleFragmentsForActivity(activity);
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (mTaskTopView == null) {
                return;
            }
            hideVisibleFragmentsForActivity(activity);
            clearFragmentsForActivity(activity);
            unhookFragmentLifecycle(activity);
            mTaskTopView.removeActivity(activity);
        }
    };

    private final Runnable mFieldTickTask = new Runnable() {
        @Override
        public void run() {
            if (!mFieldTickRunning) {
                return;
            }
            applyFieldTick();
            mMainHandler.postDelayed(this, FIELD_TICK_INTERVAL_MS);
        }
    };

}
