package com.newchar.debug.lifecycle;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppLifecycleManager implements Application.ActivityLifecycleCallbacks {

    private final LinkedHashMap<Activity, Integer> mActivityStateRef = new LinkedHashMap<>(/*16, 0.75f, true*/);
    private final List<Application.ActivityLifecycleCallbacks> mLifecycleCallbacks = new ArrayList<>(3);
    private final List<AppFBSwitchListener> mAppFBSwitchListeners = new ArrayList<>(3);
    private final List<AppCloseListener> mAppCloseListeners = new ArrayList<>(3);
    private volatile boolean mForeground = false;

    private AppLifecycleManager() {
    }

    private static class InstanceHolder {
        private static final AppLifecycleManager INSTANCE = new AppLifecycleManager();
    }

    public static AppLifecycleManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
//        温启动监听
        if (mActivityStateRef.isEmpty()) {
            for (AppCloseListener listener : mAppCloseListeners) {
                listener.onAppOpen(activity);
            }
        }
        mActivityStateRef.put(activity, PageLifecycleWrapper.CREATE);
        for (Application.ActivityLifecycleCallbacks callbacks : mLifecycleCallbacks) {
            callbacks.onActivityCreated(activity, savedInstanceState);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        mActivityStateRef.put(activity, PageLifecycleWrapper.STARTED);
        for (Application.ActivityLifecycleCallbacks callbacks : mLifecycleCallbacks) {
            callbacks.onActivityStarted(activity);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        mActivityStateRef.put(activity, PageLifecycleWrapper.RESUMED);
        for (Application.ActivityLifecycleCallbacks callbacks : mLifecycleCallbacks) {
            callbacks.onActivityResumed(activity);
        }

        tryAppForeground();
    }

    @Override
    public void onActivityPaused(Activity activity) {
        mActivityStateRef.put(activity, PageLifecycleWrapper.PAUSED);
        for (Application.ActivityLifecycleCallbacks callbacks : mLifecycleCallbacks) {
            callbacks.onActivityPaused(activity);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        mActivityStateRef.put(activity, PageLifecycleWrapper.STOPPED);

        for (Application.ActivityLifecycleCallbacks callbacks : mLifecycleCallbacks) {
            callbacks.onActivityStopped(activity);
        }

        tryAppBackground();
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        mActivityStateRef.put(activity, PageLifecycleWrapper.DESTROYED);

        for (Application.ActivityLifecycleCallbacks callbacks : mLifecycleCallbacks) {
            callbacks.onActivityDestroyed(activity);
        }
        mActivityStateRef.remove(activity);
        onAppClose(activity);
    }

    public void addLifecycleCallback(Application.ActivityLifecycleCallbacks lifecycleCallbacks) {
        mLifecycleCallbacks.add(lifecycleCallbacks);
    }

    public void removeLifecycleCallback(Application.ActivityLifecycleCallbacks lifecycleCallbacks) {
        mLifecycleCallbacks.remove(lifecycleCallbacks);
    }

    private void onAppClose(Activity lastActivity) {
        if (appCloseAble()) {
            for (AppCloseListener listener : mAppCloseListeners) {
                listener.onAppClose(lastActivity);
            }
        }
    }

    private boolean appCloseAble() {
        return mActivityStateRef.isEmpty();
    }

    /**
     * 关闭全部页面，当前进程。
     */
    public void closeApp() {
        Set<Activity> activities = mActivityStateRef.keySet();
        for (Activity activity : activities) {
            activity.finish();
        }
        Runtime.getRuntime().gc();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    /**
     * 多进程，全应用退出
     */
    public void closeAppForce() {
        closeApp();
        // 虚拟机退出，check是否全部进程都退出。
        System.exit(0);
    }

    public void addAppCloseCallback(AppCloseListener listener) {
        mAppCloseListeners.add(listener);
    }

    public void removeAppCloseCallback(AppCloseListener listener) {
        mAppCloseListeners.remove(listener);
    }

    private void tryAppForeground() {
        if (!mForeground) {
            onAppForeground(true);
        }
    }

    private void tryAppBackground() {
        if (mForeground && !isAppForeground()) {
            onAppForeground(false);
        }
    }

    private void onAppForeground(boolean foreground) {
        mForeground = foreground;
        for (AppFBSwitchListener listener : mAppFBSwitchListeners) {
            listener.onAppFBSwitch(foreground);
        }
    }

    public void addFBSwitchListener(AppFBSwitchListener fBSwitchListener) {
        mAppFBSwitchListeners.add(fBSwitchListener);
    }

    public void removeFBSwitchListener(AppFBSwitchListener fBSwitchListener) {
        mAppFBSwitchListeners.remove(fBSwitchListener);
    }

    /**
     * 最后 正在显示的Activity
     *
     * @return 最上面的Activity
     */
    public Activity getLastActivity() {
        List<Activity> resumeActivity = getResumeActivity();
        if (!resumeActivity.isEmpty()) {
            return resumeActivity.get(resumeActivity.size() - 1);
        }
        return null;
    }

    public Set<Activity> getAllActivity() {
        return Collections.unmodifiableSet(mActivityStateRef.keySet());
    }

    /**
     * 重置数据到初始化状态。
     */
    public void reset() {
        mActivityStateRef.clear();
        mLifecycleCallbacks.clear();
        mAppFBSwitchListeners.clear();
        mAppCloseListeners.clear();
        mForeground = false;
    }

    /**
     * 应用是否处于前台
     *
     * @return true 是前台展示
     */
    public boolean isAppForeground() {
        Collection<Integer> lifecycleStates = mActivityStateRef.values();
        for (Integer state : lifecycleStates) {
            if (state < PageLifecycleWrapper.STOPPED) {
                return true;
            }
        }
        return false;
    }

    public List<Activity> getResumeActivity() {
        Set<Map.Entry<Activity, Integer>> activityEntries = mActivityStateRef.entrySet();
        // dialogActivity / 折叠屏Activity
        final List<Activity> result = new ArrayList<>(2);
        for (Map.Entry<Activity, Integer> activityEntry : activityEntries) {
            if (activityEntry.getValue() < PageLifecycleWrapper.STOPPED) {
                result.add(activityEntry.getKey());
            }
        }
        return result;
    }

    /**
     * 应用前后台切换回调
     */
    public interface AppFBSwitchListener {

        /**
         * 状态改变时调用
         *
         * @param foreground 应用处于前台
         */
        void onAppFBSwitch(boolean foreground);
    }

    /**
     * 应用正常关闭回调
     */
    public interface AppCloseListener {

        /**
         * 当应用正常开启(温启动)
         */
        void onAppOpen(Activity firstActivity);

        /**
         * 当应用全部页面被关闭
         */
        void onAppClose(Activity lastActivity);
    }


}
