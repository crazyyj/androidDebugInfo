package com.newchar.debug.api;

import android.view.ViewGroup;

/**
 * @author newChar
 * date 2024/11/30
 * @since 上屏插件上层接口
 * @since 迭代版本，（以及描述）
 */
public abstract class ScreenDisplayPlugin {

    protected static final String[] EMPTY_DEPEND = new String[0];

    /**
     * 插件 id
     *
     * @return 插件 id
     */
    public abstract String id();

    /**
     * 声明是否依赖其他插件
     *
     * @return 依赖的其他插件的 id 数组
     */
    protected String[] dependOn() {
        return EMPTY_DEPEND;
    }

    /**
     * 插件被加载, 进行初始化
     * 插件显示的 View加载,在其中添加
     */
    public abstract void onLoad(PluginContext ctx, ViewGroup pluginContainerView);

    /**
     * 插件 UI 展示.
     */
    public abstract void onShow();

    /**
     * 插件 UI 隐藏.
     */
    public abstract void onHide();

    /**
     * 插件被卸载
     */
    public abstract void onUnload();

}
