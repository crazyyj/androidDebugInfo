package com.newchar.debug.browser;

import android.annotation.SuppressLint;
import android.webkit.WebSettings;

public class WebSettingsBuilder {

    private WebSettings webSettings;

    public WebSettingsBuilder(WebSettings host) {
        webSettings = host;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void buildDefault() {
        webSettings.setSupportZoom(true);
        webSettings.setDisplayZoomControls(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setUseWideViewPort(true);
        javaScriptEnabled(webSettings, true);
        webSettings.setGeolocationEnabled(true);

        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);

        // 本地资源统一走拦截器，不向页面开放 file/content 访问。
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        // 固定字体缩放，避免系统字号放大破坏 pdf.js 文本层的像素定位。
        webSettings.setTextZoom(100);
    }

    /**
     * 保留旧调用兼容；AppCache 不再作为 WebView 会话隔离配置。
     */
    @Deprecated
    public WebSettingsBuilder appCachePath(String appCachePath) {
        return this;
    }

    public WebSettings build(){
        return webSettings;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void javaScriptEnabled(WebSettings webSettings, boolean enable) {
        if (webSettings.getJavaScriptEnabled() != enable) {
            webSettings.setJavaScriptEnabled(enable);
        }
    }

}
