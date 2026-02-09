package com.newchar.debugview.lifecycle;

import android.app.Activity;
import android.os.Bundle;

/**
 * @author newChar
 * date 2026/2/9
 * @since 监听 UiContext 页面并触发调试展示策略。
 * @since 迭代版本，（以及描述）
 */
public class UiContextActivityCallback extends DefaultActivityCallback {

    private final UiContextDisplayCoordinator mCoordinator = UiContextDisplayCoordinator.getInstance();

    /**
     * 页面创建时触发展示策略判断。
     *
     * @param activity 页面
     * @param savedInstanceState 状态
     */
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        mCoordinator.onUiContextCreated(activity);
    }

    /**
     * 页面恢复时触发权限补偿判断。
     *
     * @param activity 页面
     */
    @Override
    public void onActivityResumed(Activity activity) {
        mCoordinator.onUiContextResumed(activity);
    }
}
