package com.newchar.debug.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Build;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.WebView;

import java.util.Stack;

/**
 * @author wenliqiang wenliqiang@100tal.com
 * date            2019-06-18
 * @since 负责预加载WebView，
 * @since 迭代版本描述
 */
public final class WebViewFactory {

    private static final int CACHED_WEB_VIEW_MAX_NUM = 3;

    /**
     * 全新未用的WebView栈
     */
    private final Stack<WebView> mWebViewPreStack = new Stack<>();


    public static WebViewFactory getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final WebViewFactory INSTANCE = new WebViewFactory();
    }

    public WebView getWebView() {
        if (mWebViewPreStack.isEmpty()) {
            preCreate(WebFileUtils.appContext);
        }
        return mWebViewPreStack.pop();
    }

    /**
     * 生产WebView
     */
    public void preLoadWebView(Context context) {
        WebFileUtils.appContext = context;
        preCreate(WebFileUtils.appContext);
    }

    /**
     * 回收WebView
     */
    public void releaseWebView(WebView scrapWeb) {
        scrapWeb.stopLoading();
        scrapWeb.clearCache(false);
        scrapWeb.clearHistory();
        scrapWeb.clearFormData();
        scrapWeb.clearMatches();
        if (scrapWeb.getParent() instanceof ViewGroup) {
            ((ViewGroup) scrapWeb.getParent()).removeView(scrapWeb);
        }
        mWebViewPreStack.push(scrapWeb);
    }

    public void destroy() {
        mWebViewPreStack.clear();
    }

    private void preCreate(Context context) {
        while (mWebViewPreStack.size() < CACHED_WEB_VIEW_MAX_NUM) {
            WebView item = create(context);
            mWebViewPreStack.push(item);
        }
    }

    private static WebView create(Context context) {
        WebView webView = new WebView(new MutableContextWrapper(context));
        webView.setWebViewClient(new SimpleWebClient());
        webView.setWebChromeClient(new SimpleWebChrome());
        new WebSettingsBuilder(webView.getSettings()).buildDefault();
        return webView;
    }

    /**
     * 预先全局初始化内部的 getFactory()
     */
    public static void preGlobalInit() {
        // 预初始化 WebView内 耗时对象
        WebView.setWebContentsDebuggingEnabled(false);
    }

}
