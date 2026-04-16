package com.newchar.debug;

import android.app.Activity;
import android.content.Context;

import com.newchar.debug.lifecycle.AppLifecycleManager;
import com.newchar.debug.view.DebugView;
import com.newchar.debug.view.DebugViewAddRemoveHooker;

/**
 * @author newChar
 * date 2025/6/18
 * @since 无悬浮窗权限, 所有 Activity 浮窗通过 Service 同步位置信息
 * @since 迭代版本，（以及描述）
 */
class CanNotFlowState implements IFLowState {

    private DebugViewAddRemoveHooker mDebugViewAddRemoveHooker;

    public CanNotFlowState() {
        if (mDebugViewAddRemoveHooker == null) {
            mDebugViewAddRemoveHooker = new DebugViewAddRemoveHooker();
        }
        AppLifecycleManager.getInstance().addLifecycleCallback(mDebugViewAddRemoveHooker);
    }

    @Override
    public void initFlowParams(Context service) {

    }

    public DebugView getDebugView(Activity attachHost) {
        return mDebugViewAddRemoveHooker.getLogView(attachHost);
    }

    @Override
    public DebugView getDebugView() {
        return mDebugViewAddRemoveHooker.getLogView();
    }

    @Override
    public void loadPlugin() {

    }

    @Override
    public void showPlugin() {

    }

    @Override
    public void switchState(IFLowState newState) {

    }

    @Override
    public void onDestroy() {

    }


}
