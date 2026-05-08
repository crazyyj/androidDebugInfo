package com.newchar.debug;

import android.content.Context;
import android.graphics.Rect;

import com.newchar.debug.base.utils.UIUtils;
import com.newchar.debug.view.DebugView;

/**
 * @author newChar
 * date 2025/6/18
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
interface IFLowState {

    Rect mWindowRect = new Rect(0, UIUtils.getStatusBarHeight(), UIUtils.getScreenWidth(), 0);

    /**
     * 初始化 全局 DebugView 参数,位置大小等.
     *
     * @param context Context
     */
    void initFlowParams(Context context);

    DebugView getDebugView();

    /**
     * 加载插件
     */
    void loadPlugin();

    /**
     * 显示插件内容
     */
    void showPlugin();

    void switchState(IFLowState newState);

    void onDestroy();
}
