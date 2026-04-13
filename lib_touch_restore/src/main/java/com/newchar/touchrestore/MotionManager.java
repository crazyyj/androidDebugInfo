package com.newchar.touchrestore;

import android.app.Activity;
import android.os.Bundle;

import com.newchar.debugview.lifecycle.DefaultActivityCallback;
import com.newchar.debugview.lifecycle.AppLifecycleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author newChar
 * date 2023/9/14
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public final class MotionManager extends DefaultActivityCallback {

    private final Map<Activity, RecordTouchEventTask.TouchCallbackProxy> mTouchHooks = new WeakHashMap<>();
    private boolean mStarted;
    private boolean mLifecycleRegistered;

    /**
     * 创建触摸事件采集管理器。
     */
    public MotionManager() {
    }

    /**
     * 开始触摸事件采集。
     */
    public void start() {
        if (mStarted) {
            return;
        }
        mStarted = true;
        registerLifecycleIfNeed();
        for (Activity activity : AppLifecycleManager.getInstance().getAllActivity()) {
            hookActivity(activity);
        }
    }

    /**
     * 停止触摸事件采集并恢复原回调。
     */
    public void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        unregisterLifecycleIfNeed();
        restoreAllActivity();
    }

    /**
     * 判断当前是否处于采集状态。
     *
     * @return true 表示正在采集
     */
    public boolean isStarted() {
        return mStarted;
    }

    /**
     * 页面创建时按需安装采集 Hook。
     */
    @Override
    public void onActivityCreated( Activity activity, Bundle savedInstanceState) {
        super.onActivityCreated(activity, savedInstanceState);
        hookActivity(activity);
    }

    /**
     * 页面恢复时补齐采集 Hook。
     */
    @Override
    public void onActivityResumed( Activity activity) {
        super.onActivityResumed(activity);
        hookActivity(activity);
    }

    /**
     * 页面停止时保留当前采集 Hook。
     */
    @Override
    public void onActivityStopped( Activity activity) {
        super.onActivityStopped(activity);
        // 暂停采集
    }

    /**
     * 页面销毁时恢复原始回调。
     */
    @Override
    public void onActivityDestroyed( Activity activity) {
        super.onActivityDestroyed(activity);
        restoreActivity(activity);
    }

    /**
     * 按需注册生命周期监听。
     */
    private void registerLifecycleIfNeed() {
        if (mLifecycleRegistered) {
            return;
        }
        AppLifecycleManager.getInstance().addLifecycleCallback(this);
        mLifecycleRegistered = true;
    }

    /**
     * 按需注销生命周期监听。
     */
    private void unregisterLifecycleIfNeed() {
        if (!mLifecycleRegistered) {
            return;
        }
        AppLifecycleManager.getInstance().removeLifecycleCallback(this);
        mLifecycleRegistered = false;
    }

    /**
     * 给指定页面安装采集 Hook。
     *
     * @param activity 页面实例
     */
    private void hookActivity(Activity activity) {
        if (!mStarted || activity == null || activity.getWindow() == null || mTouchHooks.containsKey(activity)) {
            return;
        }
        activity.getWindow().getDecorView().post(() -> {
            if (!mStarted || mTouchHooks.containsKey(activity)) {
                return;
            }
            RecordTouchEventTask.TouchCallbackProxy proxy = RecordTouchEventTask.install(activity);
            if (proxy != null) {
                mTouchHooks.put(activity, proxy);
            }
        });
    }

    /**
     * 恢复指定页面原始回调。
     *
     * @param activity 页面实例
     */
    private void restoreActivity(Activity activity) {
        if (activity == null) {
            return;
        }
        RecordTouchEventTask.TouchCallbackProxy proxy = mTouchHooks.remove(activity);
        RecordTouchEventTask.restore(activity, proxy);
    }

    /**
     * 恢复全部页面原始回调。
     */
    private void restoreAllActivity() {
        List<Map.Entry<Activity, RecordTouchEventTask.TouchCallbackProxy>> entries =
                new ArrayList<>(mTouchHooks.entrySet());
        for (Map.Entry<Activity, RecordTouchEventTask.TouchCallbackProxy> entry : entries) {
            RecordTouchEventTask.restore(entry.getKey(), entry.getValue());
        }
        mTouchHooks.clear();
    }

}
