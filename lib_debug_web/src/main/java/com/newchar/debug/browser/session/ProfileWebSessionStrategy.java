package com.newchar.debug.browser.session;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;


/**
 * 使用 AndroidX WebKit Profile 为账号会话隔离网站数据。
 */
public final class ProfileWebSessionStrategy implements WebSessionStrategy {

    private final Context appContext;
    private final ProfileStore profileStore;

    /**
     * 初始化 Profile 管理器；调用方应先完成待删除 Profile 处理。
     */
    public ProfileWebSessionStrategy(Context context) {
        appContext = context.getApplicationContext();
        profileStore = ProfileStore.getInstance();
    }

    /**
     * 返回 Profile 与完整清理能力。
     */
    @Override
    public Capabilities getCapabilities() {
        boolean clear = androidx.webkit.WebViewFeature.isFeatureSupported(
                androidx.webkit.WebViewFeature.DELETE_BROWSING_DATA);
        return new Capabilities(true, clear, "profile", "");
    }

    /**
     * 使用持久化安全 Profile 名称打开会话。
     */
    @Override
    public Session openSession(String sessionId) {
        try {
            return openResolvedSession(new WebSessionRegistry().resolveEntry(appContext, sessionId));
        } catch (Exception error) {
            throw new IllegalStateException("会话注册表读取失败", error);
        }
    }

    /**
     * 使用后台已持久化的 Profile 名称取得内核会话。
     */
    public Session openResolvedSession(WebSessionRegistry.Entry entry) {
        Profile profile = profileStore.getOrCreateProfile(entry.namespace);
        return new Session(entry.sessionId, entry.namespace, entry.generation, profile, true);
    }

    /** 完整清理缺失时由工厂换新 Profile，不清理全局默认环境。 */
    @Override
    public void clearSessionData(Session session, OperationCallback callback) {
        if (!getCapabilities().supportsCompleteClear) {
            callback.onComplete("RESET_WITH_NEW_PROFILE", null);
            return;
        }
        try {
            androidx.webkit.WebStorageCompat.deleteBrowsingData(session.profile.getWebStorage(), () -> {
                session.profile.getGeolocationPermissions().clearAll();
                callback.onComplete("DATA_CLEARED", null);
            });
        } catch (RuntimeException error) {
            callback.onComplete("RESET_WITH_NEW_PROFILE", error);
        }
    }

    /** 删除已加载 Profile 只能登记到下一次冷启动，尽力清理不冒充物理删除。 */
    @Override
    public void retireSession(Session session, OperationCallback callback) {
        clearSessionData(session, (code, error) -> callback.onComplete("DELETE_PENDING", error));
    }

    /** 冷启动直接删除未加载名称；不调用 getProfile 确认存在。 */
    public boolean deleteUnloadedProfile(String name) {
        return profileStore.deleteProfile(name);
    }

    /**
     * 将命名 Profile 绑定到尚未初始化的 WebView。
     */
    @Override
    public void bindBeforeInitialization(WebView webView, Session session) {
        WebViewCompat.setProfile(webView, session.namespace);
    }

    /**
     * 返回命名 Profile 的 Cookie 管理器。
     */
    @Override
    public CookieManager getCookieManager(Session session) {
        return session.profile.getCookieManager();
    }

    /**
     * 返回命名 Profile 的网站存储。
     */
    @Override
    public WebStorage getWebStorage(Session session) {
        return session.profile.getWebStorage();
    }

}