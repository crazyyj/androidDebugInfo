package com.newchar.debug.browser.session;

import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

/**
 * 不支持多 Profile 时使用系统默认单账号浏览环境。
 */
public final class DefaultWebSessionStrategy implements WebSessionStrategy {

    private final String reason;

    /**
     * 创建默认单账号策略。
     */
    public DefaultWebSessionStrategy(String reason) {
        this.reason = reason;
    }

    /**
     * 返回默认环境的能力信息。
     */
    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(false, androidx.webkit.WebViewFeature.isFeatureSupported(
                androidx.webkit.WebViewFeature.DELETE_BROWSING_DATA), "default", reason);
    }

    /**
     * 所有请求均映射到唯一默认会话。
     */
    @Override
    public Session openSession(String sessionId) {
        return new Session("default", "default", 0L, null, false);
    }

    /**
     * 默认环境不需要 Profile 绑定。
     */
    @Override
    public void bindBeforeInitialization(WebView webView, Session session) {
    }

    /**
     * 返回系统默认 Cookie 管理器。
     */
    @Override
    public CookieManager getCookieManager(Session session) {
        return CookieManager.getInstance();
    }

    /**
     * 返回系统默认网站存储。
     */
    @Override
    public WebStorage getWebStorage(Session session) {
        return WebStorage.getInstance();
    }

    /** 默认会话完整清理影响进程内所有使用默认环境的 WebView。 */
    @Override
    public void clearSessionData(Session session, OperationCallback callback) {
        try {
            if (getCapabilities().supportsCompleteClear) {
                androidx.webkit.WebStorageCompat.deleteBrowsingData(getWebStorage(session), () -> {
                    android.webkit.GeolocationPermissions.getInstance().clearAll();
                    callback.onComplete("DATA_CLEARED", null);
                });
            } else partialClear(session, callback);
        } catch (UnsupportedOperationException unsupported) {
            partialClear(session, callback);
        } catch (RuntimeException error) {
            callback.onComplete("ERROR", error);
        }
    }

    /** 完整 API 缺失或运行时不支持时明确返回部分清理，不承诺清除全部网站存储。 */
    private void partialClear(Session session, OperationCallback callback) {
        try {
            getWebStorage(session).deleteAllData();
            android.webkit.GeolocationPermissions.getInstance().clearAll();
            getCookieManager(session).removeAllCookies(removed -> callback.onComplete("PARTIAL_CLEAR", null));
        } catch (RuntimeException error) { callback.onComplete("ERROR", error); }
    }

    /** 默认 Profile 永不物理删除，仅按明确操作清理默认环境。 */
    @Override
    public void retireSession(Session session, OperationCallback callback) {
        clearSessionData(session, callback);
    }
}