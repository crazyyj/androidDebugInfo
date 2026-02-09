package com.newchar.debugview;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.newchar.debug.common.utils.ViewUtils;
import com.newchar.debugview.utils.DebugUtils;
import com.newchar.debugview.view.DebugView;

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
    private int mScreenWidth;
    private int mScreenHeight;

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
        ensureScreenSize(context);
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
        mScreenWidth = 0;
        mScreenHeight = 0;
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
        int initWindowWidth = ViewUtils.getViewContainerWidth();
        int initWindowHeight = ViewUtils.getViewContainerWidth();
        mWindowParams = new WindowManager.LayoutParams(
                initWindowWidth,
                initWindowHeight,
                resolveWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        mWindowParams.gravity = Gravity.TOP | Gravity.START;
        mWindowParams.x = mWindowRect.left;
        mWindowParams.y = mWindowRect.top;
    }

    /**
     * 同步屏幕宽高。
     *
     * @param context 上下文
     */
    private void ensureScreenSize(Context context) {
        if (context == null || (mScreenWidth > 0 && mScreenHeight > 0)) {
            return;
        }
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        mScreenWidth = displayMetrics.widthPixels;
        mScreenHeight = displayMetrics.heightPixels;
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
        ensureScreenSize(control.getContext());
        int nextX = mWindowParams.x + Math.round(deltaX);
        int nextY = mWindowParams.y + Math.round(deltaY);
        int windowWidth = resolveWindowWidth(control);
        int windowHeight = resolveWindowHeight(control);
        int minX = -windowWidth;
        int maxX = mScreenWidth + windowWidth;
        int minY = -windowHeight;
        int maxY = mScreenHeight + windowHeight;
        mWindowParams.x = clamp(nextX, minX, maxX);
        mWindowParams.y = clamp(nextY, minY, maxY);
        mWindowManager.updateViewLayout(control, mWindowParams);
    }

    /**
     * 计算当前窗口宽度。
     *
     * @param control 被拖动视图
     * @return 窗口宽度
     */
    private int resolveWindowWidth(View control) {
        if (control.getWidth() > 0) {
            return control.getWidth();
        }
        return Math.max(0, mWindowParams.width);
    }

    /**
     * 计算当前窗口高度。
     *
     * @param control 被拖动视图
     * @return 窗口高度
     */
    private int resolveWindowHeight(View control) {
        if (control.getHeight() > 0) {
            return control.getHeight();
        }
        return Math.max(0, mWindowParams.height);
    }

    /**
     * 限制数值在给定范围内。
     *
     * @param value 当前值
     * @param min 最小值
     * @param max 最大值
     * @return 限制后的结果
     */
    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
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
