package com.newchar.debug.view;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import com.newchar.debug.base.utils.ViewUtils;
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

    private final WeakHashMap<Activity, DebugView> mViewRefs;

    public DebugViewAddRemoveHooker() {
        mViewRefs = new WeakHashMap<>();
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
        return logView;
    }

    /**
     * 整个DebugView的layoutParams
     *
     * @return LayoutParams
     */
    private ViewGroup.LayoutParams getDebugViewLayoutParams() {
        int widthHeight = ViewUtils.getViewContainerWidth();
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
        // 卸载全部LogView，后清空容器
        for (Map.Entry<Activity, DebugView> activityDebugViewEntry : mViewRefs.entrySet()) {
            detachActivity(activityDebugViewEntry.getKey());
        }
        mViewRefs.clear();
    }

}
