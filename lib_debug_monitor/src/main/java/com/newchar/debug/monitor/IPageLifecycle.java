package com.newchar.debug.monitor;

import android.content.Context;

/**
 * @author newChar
 * date 2025/7/3
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public interface IPageLifecycle {

    void onPageCreate(Context hostContext, int code, Class<?> pageClass);

    void onPageVisible(Context hostContext, int code, Class<?> pageClass);

    void onPageGone(Context hostContext, int code, Class<?> pageClass);

    void onPageDestroy(Context hostContext, int code, Class<?> pageClass);

}
