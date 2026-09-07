package com.newchar.debug.view;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import com.newchar.debug.utils.UIUtils;
import com.newchar.debug.utils.ViewUtils;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.PluginManager;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.lifecycle.AppLifecycleManager;
import com.newchar.debug.lifecycle.DefaultActivityCallback;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author newChar
 * date 2022/6/25
 * @since 自行添加和删除DebugView
 * @since 迭代版本，（以及描述）
 */
public class DebugViewAddRemoveHooker extends DefaultActivityCallback {

    /**
     * DebugView 创建后、挂载到 Activity 前的配置回调，
     * 供外层状态（如 CanNotFlowState）设置 move / layout / focus 处理器。
     */
    public interface DebugViewConfigurator {

        /**
         * 配置新创建的 DebugView。
         *
         * @param debugView 待配置的调试视图
         */
        void onConfigure(DebugView debugView);
    }

    private final WeakHashMap<Activity, DebugView> mViewRefs;
    private DebugViewConfigurator mDebugViewConfigurator;

    public DebugViewAddRemoveHooker() {
        mViewRefs = new WeakHashMap<>();
    }

    /**
     * 设置 DebugView 配置回调，在每次创建新 DebugView 时触发。
     *
     * @param configurator 配置回调
     */
    public void setDebugViewConfigurator(DebugViewConfigurator configurator) {
        mDebugViewConfigurator = configurator;
    }

    @Override
    public void onActivityCreated(final Activity activity, Bundle savedInstanceState) {
        final View decorView = activity.getWindow().getDecorView();
        decorView.post(() -> viewAttachActivity(activity));
        decorView.post(() -> viewAttachPlugin(activity));
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        detachActivity(activity);
    }

    private DebugView getDebugView(Activity activity) {
        final DebugView debugView = mViewRefs.get(activity);
        if (debugView == null) {
            return genDebugView(activity);
        } else {
            return debugView;
        }
    }

    private DebugView genDebugView(Activity activity) {
        DebugView logView = new DebugView(activity);
        logView.setX(1);
        logView.setY(1);
        if (mDebugViewConfigurator != null) {
            mDebugViewConfigurator.onConfigure(logView);
        }
        return logView;
    }

    /**
     * 整个DebugView的layoutParams
     *
     * @return LayoutParams
     */
    private ViewGroup.LayoutParams getDebugViewLayoutParams() {
        int widthHeight = UIUtils.getScreenWidth();
        return new ViewGroup.LayoutParams(widthHeight, widthHeight);
    }

    private void viewAttachActivity(Activity activity) {
        DebugView debugView = getDebugView(activity);
        mViewRefs.put(activity, debugView);

        if (debugView.getParent() == null) {
            FrameLayout contentView = activity.findViewById(Window.ID_ANDROID_CONTENT);
            contentView.addView(debugView, getDebugViewLayoutParams());
        }
    }

    private void detachActivity(Activity activity) {
        DebugView logView = mViewRefs.remove(activity);
        detachLogView(logView);
    }

    private void detachLogView(DebugView logView) {
        ViewUtils.removeSelf(logView);
    }

    public DebugView getLogView(Activity activity) {
        return mViewRefs.get(activity);
    }

    public DebugView getLogView() {
        return getLogView(AppLifecycleManager.getInstance().getLastActivity());
    }

    private void viewAttachPlugin(Activity activity) {
        Log.e("AAA", "viewAttachPlugin");
        try {

//            ScreenDisplayPlugin plugin = PluginManager.getInstance().getPlugin("1");
//            if (plugin != null) {
//                plugin.onLoad(new PluginContext(), getDebugView(activity));
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void detachPlugin(Activity activity) {

    }

    public void release() {
        // 先快照 key 集合，避免遍历中 remove 触发 ConcurrentModificationException
        Activity[] activities = mViewRefs.keySet().toArray(new Activity[0]);
        for (Activity activity : activities) {
            detachActivity(activity);
        }
        mViewRefs.clear();
    }

}
