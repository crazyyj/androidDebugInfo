package com.newchar.debug.api;

import android.content.Intent;
import android.view.ViewGroup;

/**
 * @author newChar
 * date 2024/11/30
 * @since 上屏插件上层接口
 * @since 迭代版本，（以及描述）
 */
public abstract class ScreenDisplayPlugin {

    protected static final String[] EMPTY_DEPEND = new String[0];

    /** UI 模式：内嵌正方形，避开状态栏与导航栏（默认模式） */
    public static final int UI_MODE_INSET = 0;
    /** UI 模式：默认正方形，边长 = min(屏宽, 屏高) - 8dp */
    public static final int UI_MODE_FULL = 1;
    /** UI 模式：折叠态，仅显示 title 工具栏，高度上限为 INSET 模式高度 */
    public static final int UI_MODE_COLLAPSED = 2;

    /**
     * 插件 id
     *
     * @return 插件 id
     */
    public abstract String id();

    /**
     * 插件展示名称，用于 UI 回显。
     *
     * @return 插件名称
     */
    public abstract String getName();

    /**
     * 当外层 DebugView 的 UI 模式切换或插件首次被应用时回调，
     * 插件可据此调整自身内容高度与布局。
     *
     * @param mode 当前 UI 模式，取值为 {@link #UI_MODE_FULL} /
     *             {@link #UI_MODE_INSET} / {@link #UI_MODE_COLLAPSED}
     */
    public void onSizeModeChanged(int mode) {
    }

    /**
     * 复制回调，返回纯文本内容。返回 null/空表示不支持复制。
     *
     * @return 待复制文本
     */
    public CharSequence onCopyText() {
        return null;
    }

    /**
     * 分享回调，返回 ACTION_SEND 的 Intent（可含 EXTRA_TEXT + EXTRA_STREAM 图片 Uri）。
     * 返回 null 表示不支持分享。DebugView 会包一层 createChooser 后 startActivity。
     *
     * @return 分享 Intent
     */
    public Intent onCreateShareIntent() {
        return null;
    }

    /**
     * 清空回调，插件据此清空自身数据/列表。
     */
    public void onClear() {
    }

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
