package com.newchar.debug.browser;

import android.annotation.SuppressLint;
import android.webkit.WebSettings;

import java.lang.reflect.Method;

public class WebSettingsBuilder {

    private WebSettings webSettings;

    private String mAppCachePath;

    public WebSettingsBuilder(WebSettings host) {
        webSettings = host;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void buildDefault() {
        webSettings.setSupportZoom(true);
        webSettings.setDisplayZoomControls(true);
        setAppCache(webSettings, WebFileUtils.getWebViewBasicCachePath().getAbsolutePath());

        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setUseWideViewPort(true);
        javaScriptEnabled(webSettings, true);
        webSettings.setGeolocationEnabled(true);

        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);
    }

    public WebSettingsBuilder appCachePath(String appCachePath) {
        mAppCachePath = appCachePath;
        return this;
    }

    public WebSettings build(){
        if (mAppCachePath != null) {
            setAppCache(webSettings, mAppCachePath);
        }
        return webSettings;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void javaScriptEnabled(WebSettings webSettings, boolean enable) {
        if (webSettings.getJavaScriptEnabled() != enable) {
            webSettings.setJavaScriptEnabled(enable);
        }
    }

    private static void setAppCache(WebSettings settings, String path) {
        try {
            Method setAppCacheEnabled = WebSettings.class.getMethod("setAppCacheEnabled", boolean.class);
            setAppCacheEnabled.invoke(settings, true);
            Method setAppCachePath = WebSettings.class.getMethod("setAppCachePath", String.class);
            setAppCachePath.invoke(settings, path);
        } catch (Exception ignored) {
        }
    }

}
