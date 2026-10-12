package com.newchar.debug.browser.session;

import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

import androidx.webkit.Profile;

/**
 * WebView 浏览会话策略，统一封装多 Profile 与系统默认环境。
 */
public interface WebSessionStrategy {

    /**
     * 返回设备当前支持的会话能力。
     */
    Capabilities getCapabilities();

    /**
     * 打开或恢复指定会话。
     */
    Session openSession(String sessionId);

    /**
     * 在 WebView 进行其他初始化前绑定会话。
     */
    void bindBeforeInitialization(WebView webView, Session session);

    /**
     * 返回会话对应的 Cookie 管理器。
     */
    CookieManager getCookieManager(Session session);

    /**
     * 返回会话对应的网站存储。
     */
    WebStorage getWebStorage(Session session);

    /**
     * 主线程清理已停用会话，回调区分完整清理与换新 Profile。
     */
    void clearSessionData(Session session, OperationCallback callback);

    /**
     * 主线程尽力清理已退役会话；物理删除由冷启动队列负责。
     */
    void retireSession(Session session, OperationCallback callback);

    /**
     * 接收清理结果；错误和超时不能当作数据已删除。
     */
    interface OperationCallback {
        /** 返回结果代码及可选错误。 */
        void onComplete(String code, Exception error);
    }

    /**
     * 描述设备能力和实际浏览模式。
     */
    final class Capabilities {
        public final boolean supportsMultipleAccounts;
        public final boolean supportsCompleteClear;
        public final String mode;
        public final String reason;

        /**
         * 创建不可变能力描述。
         */
        public Capabilities(boolean supportsMultipleAccounts, boolean supportsCompleteClear,
                            String mode, String reason) {
            this.supportsMultipleAccounts = supportsMultipleAccounts;
            this.supportsCompleteClear = supportsCompleteClear;
            this.mode = mode;
            this.reason = reason;
        }
    }

    /**
     * 携带浏览会话真实命名空间及内核 Profile 的句柄。
     */
    final class Session {
        public final String sessionId;
        public final String namespace;
        public final long generation;
        public final Profile profile;
        public final boolean profileMode;
        private volatile boolean active = true;

        /** 当前句柄是否仍允许导航与观察。 */
        public boolean isActive() { return active; }

        /** 句柄只允许单向停用，重置后必须使用新的句柄。 */
        public void deactivate() { active = false; }

        /**
         * 创建策略内部使用的会话句柄。
         */
        public Session(String sessionId, String namespace, long generation,
                       Profile profile, boolean profileMode) {
            this.sessionId = sessionId;
            this.namespace = namespace;
            this.generation = generation;
            this.profile = profile;
            this.profileMode = profileMode;
        }
    }
}