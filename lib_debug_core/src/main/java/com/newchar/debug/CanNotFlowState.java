package com.newchar.debug;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import com.newchar.debug.lifecycle.AppLifecycleManager;
import com.newchar.debug.utils.DebugUtils;
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

    /**
     * 设置 DebugView 配置回调，并配置已存在的 DebugView。
     * hooker 在 Activity 创建时自动将 DebugView 挂载到 DecorView 的 ContentFrame，
     * 无需像 CanFlowState 那样手动 addView 到 WindowManager。
     *
     * @param service 上下文
     */
    @Override
    public void initFlowParams(Context service) {
        mDebugViewAddRemoveHooker.setDebugViewConfigurator(this::configureDebugView);
        // 配置在 initFlowParams 之前已创建的 DebugView
        for (Activity activity : AppLifecycleManager.getInstance().getAllActivity()) {
            DebugView debugView = mDebugViewAddRemoveHooker.getLogView(activity);
            if (debugView != null) {
                configureDebugView(debugView);
            }
        }
    }

    /**
     * 配置 DebugView 的 move / layout / focus 处理器，
     * 使其在 Activity ContentFrame 内的行为与悬浮窗模式一致。
     * <p>
     * move：通过 setX/setY 更新视图位置；
     * layout / focus：在 Activity 内部无需特殊处理，依赖 DebugView 自身的 setLayoutParams。
     *
     * @param debugView 待配置的调试视图
     */
    private void configureDebugView(DebugView debugView) {
        debugView.setMoveHandler((control, deltaX, deltaY) -> {
            control.setX(control.getX() + deltaX);
            control.setY(control.getY() + deltaY);
        });
    }

    public DebugView getDebugView(Activity attachHost) {
        return mDebugViewAddRemoveHooker.getLogView(attachHost);
    }

    @Override
    public DebugView getDebugView() {
        return mDebugViewAddRemoveHooker.getLogView();
    }

    /**
     * 加载插件。
     * DebugView 在 onAttachedToWindow 中自动加载全部已注册插件，
     * 与 CanFlowState 保持一致，此处无需额外操作。
     */
    @Override
    public void loadPlugin() {
    }

    /**
     * 展示当前页面的调试视图。
     */
    @Override
    public void showPlugin() {
        DebugView debugView = getDebugView();
        if (debugView != null) {
            debugView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 切换到新的展示状态，先卸载当前状态再初始化新状态。
     *
     * @param newState 新状态
     */
    @Override
    public void switchState(IFLowState newState) {
        DebugView debugView = getDebugView();
        Context context = debugView != null ? debugView.getContext() : DebugUtils.app();
        onDestroy();
        if (newState != null && context != null) {
            newState.initFlowParams(context);
            newState.loadPlugin();
        }
    }

    /**
     * 卸载 hooker 回调并释放所有页面的 DebugView。
     */
    @Override
    public void onDestroy() {
        AppLifecycleManager.getInstance().removeLifecycleCallback(mDebugViewAddRemoveHooker);
        mDebugViewAddRemoveHooker.release();
    }

}
