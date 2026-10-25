package com.newchar.debug.browser;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.Nullable;

/**
 * 页面资源拦截与实例级通道的扩展点。
 *
 * <p>工厂只负责把请求转交给拦截器，不感知具体业务（PDF、离线包、Mock 响应等），
 * 避免后续每加一种能力都要改 {@link WebViewFactory}。</p>
 */
public interface PageInterceptor {

    /**
     * 拦截页面请求。
     *
     * @param request 内核请求，运行在后台线程
     * @return 命中时返回响应；返回 null 表示不接管，内核继续原流程
     */
    @Nullable
    WebResourceResponse intercept(WebResourceRequest request);

    /**
     * 客户端装配完成、页面尚未开始加载时调用，用于安装 JS 通道。
     *
     * @param view 已绑定会话的实例
     */
    void onViewReady(WebView view);

    /**
     * 实例销毁前调用，释放与该 WebView 绑定的资源。
     *
     * @param view 即将销毁的实例
     */
    default void onViewReleased(WebView view) {
    }
}
