package com.newchar.debug;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.newchar.debug.utils.UIUtils;
import com.newchar.debug.utils.DebugUtils;
import com.newchar.debug.view.DebugView;

/**
 * @author newChar
 * date 2025/6/18
 * @since 有悬浮窗权限时的调试展示状态。
 * @since 迭代版本，（以及描述）
 */
class CanFlowState implements IFLowState {

    private DebugView mDebugView;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mWindowParams;
    private boolean mAttached;

    /**
     * 初始化并挂载悬浮窗参数。
     *
     * @param context 上下文
     */
    @Override
    public void initFlowParams(Context context) {
        ensureDebugView(context);
        ensureWindowManager(context);
        if (mWindowManager == null || mAttached) {
            return;
        }
        ensureLayoutParams();
        mWindowManager.addView(mDebugView, mWindowParams);
        mAttached = true;
    }

    /**
     * 获取调试主视图。
     *
     * @return DebugView
     */
    @Override
    public DebugView getDebugView() {
        return mDebugView;
    }

    /**
     * 加载插件内容。
     */
    @Override
    public void loadPlugin() {
    }

    /**
     * 展示插件容器。
     */
    @Override
    public void showPlugin() {
        if (mDebugView != null) {
            mDebugView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 切换到新的展示状态。
     *
     * @param newState 新状态
     */
    @Override
    public void switchState(IFLowState newState) {
        Context context = mDebugView != null ? mDebugView.getContext() : DebugUtils.app();
        onDestroy();
        if (newState != null && context != null) {
            newState.initFlowParams(context);
            newState.loadPlugin();
        }
    }

    /**
     * 卸载悬浮窗并释放资源。
     */
    @Override
    public void onDestroy() {
        if (mWindowManager != null && mAttached && mDebugView != null) {
            mWindowManager.removeView(mDebugView);
        }
        mAttached = false;
        mWindowParams = null;
        mWindowManager = null;
        mDebugView = null;
    }

    /**
     * 确保调试视图已创建。
     *
     * @param context 上下文
     */
    private void ensureDebugView(Context context) {
        if (mDebugView == null) {
            mDebugView = new DebugView(context);
            mDebugView.setMoveHandler(this::handleWindowMove);
            mDebugView.setLayoutUpdateHandler(this::handleLayoutUpdate);
            mDebugView.setFocusToggleHandler(this::handleFocusToggle);
        }
    }

    /**
     * 确保窗口管理器可用。
     *
     * @param context 上下文
     */
    private void ensureWindowManager(Context context) {
        if (mWindowManager == null && context != null) {
            mWindowManager = context.getSystemService(WindowManager.class);
        }
    }

    /**
     * 构建悬浮窗布局参数。
     */
    private void ensureLayoutParams() {
        if (mWindowParams != null) {
            return;
        }
        int initWindowWidth = UIUtils.getScreenWidth();
        int initWindowHeight = UIUtils.getScreenHeight();
        mWindowParams = new WindowManager.LayoutParams(
                initWindowWidth,
                initWindowHeight,
                resolveWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        mWindowParams.gravity = Gravity.TOP | Gravity.START;
        mWindowParams.x = mWindowRect.left;
        mWindowParams.y = mWindowRect.top;
    }

    /**
     * 处理窗口拖动。
     *
     * @param control 被拖动视图
     * @param deltaX X方向偏移
     * @param deltaY Y方向偏移
     */
    private void handleWindowMove(View control, float deltaX, float deltaY) {
        if (!mAttached || mWindowManager == null || mWindowParams == null || control == null) {
            return;
        }
        mWindowParams.x = mWindowParams.x + Math.round(deltaX);
        mWindowParams.y = mWindowParams.y + Math.round(deltaY);
        mWindowManager.updateViewLayout(control, mWindowParams);
    }

    /**
     * 处理 DebugView 自身尺寸变更，将宽高同步到 WindowManager。
     *
     * @param view DebugView
     * @param lp   DebugView 当前的 LayoutParams（即 mWindowParams 实例）
     */
    private void handleLayoutUpdate(View view, ViewGroup.LayoutParams lp) {
        if (!mAttached || mWindowManager == null || mWindowParams == null || view == null) {
            return;
        }
        if (lp instanceof WindowManager.LayoutParams) {
            mWindowParams.width = lp.width;
            mWindowParams.height = lp.height;
        }
        mWindowManager.updateViewLayout(view, mWindowParams);
    }

    /**
     * 切换悬浮窗的可聚焦状态，使 EditText 能唤起软键盘。
     *
     * @param view      DebugView
     * @param focusable true 表示需要焦点（移除 FLAG_NOT_FOCUSABLE），false 表示恢复无焦点
     */
    private void handleFocusToggle(View view, boolean focusable) {
        if (!mAttached || mWindowManager == null || mWindowParams == null || view == null) {
            return;
        }
        if (focusable) {
            mWindowParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            mWindowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        try {
            mWindowManager.updateViewLayout(view, mWindowParams);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * 解析悬浮窗类型。
     *
     * @return Window 类型
     */
    private int resolveWindowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }
}
