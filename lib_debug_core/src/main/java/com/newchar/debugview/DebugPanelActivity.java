package com.newchar.debugview;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.newchar.debugview.view.DebugView;

/**
 * @author newChar
 * date 2026/2/9
 * @since 调试面板独立容器页面。
 * @since 迭代版本，（以及描述）
 */
public class DebugPanelActivity extends Activity {

    /**
     * 初始化调试面板页面。
     *
     * @param savedInstanceState 页面状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildRootView());
    }

    /**
     * 构建调试容器根视图。
     *
     * @return 根视图
     */
    private FrameLayout buildRootView() {
        FrameLayout rootView = new FrameLayout(this);
        rootView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootView.addView(buildDebugView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return rootView;
    }

    /**
     * 构建调试主视图。
     *
     * @return 调试主视图
     */
    private DebugView buildDebugView() {
        return new DebugView(this);
    }
}
