package com.newchar.debug.browser;

import android.content.Context;
import android.net.Uri;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.CookieManager;

import com.newchar.debug.browser.session.WebSessionStrategy;
import com.newchar.debug.browser.store.WebResourceMetadataObserver;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 只观察允许的静态资源，不替换 WebView 自身的响应。
 */
public class SimpleWebClient extends WebViewClient {

    private final WebSessionStrategy.Session session;
    private final WebSessionStrategy strategy;
    private final Context appContext;
    private final CookieManager cookieManager;
    private final WebResourceMetadataObserver.Page page;
    private final RenderExitHandler renderExitHandler;
    private volatile String pageUrl;
    private final String userAgent;

    /**
     * 创建兼容默认会话的客户端。
     */
    public SimpleWebClient() {
        this(null, null, null);
    }

    /**
     * 创建绑定指定浏览会话的客户端。
     */
    public SimpleWebClient(WebSessionStrategy.Session session, WebSessionStrategy strategy,
                           Context context) {
        this(session, strategy, context, new WebResourceMetadataObserver.Page(), "", null);
    }

    /** 在主线程固定 Cookie 管理器和 UA，后台不访问 WebView 的 UI API。 */
    public SimpleWebClient(WebSessionStrategy.Session session, WebSessionStrategy strategy,
                           Context context, WebResourceMetadataObserver.Page page, String userAgent, RenderExitHandler handler) {
        this.session = session;
        this.strategy = strategy;
        this.appContext = context == null ? null : context.getApplicationContext();
        this.cookieManager = session == null ? null : strategy.getCookieManager(session);
        this.page = page;
        this.userAgent = userAgent;
        this.renderExitHandler = handler;
    }

    /** 主线程保存顶层来源，拦截工作线程只读取快照。 */
    @Override
    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
        pageUrl = url;
        super.onPageStarted(view, url, favicon);
    }

    /** API 26 以上处理退出实例，旧实例不能继续使用。 */
    @Override
    @androidx.annotation.RequiresApi(26)
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        WebResourceMetadataObserver.cancelPage(page);
        if (renderExitHandler != null) renderExitHandler.onGone(view);
        else {
            if (view.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) view.getParent()).removeView(view);
            }
            view.destroy();
        }
        return true;
    }

    /** 工厂负责移除实例，宿主下次显示时按原会话重新创建。 */
    public interface RenderExitHandler {
        /** 处理一个受影响的实例，重复回调保持幂等。 */
        void onGone(WebView view);
    }

    /**
     * 遇到证书错误时终止请求，避免静默放行不可信证书。
     */
    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        handler.cancel();
    }

    /**
     * 快照并异步投递候选静态资源，始终让内核继续处理原始请求。
     */
    @Override
    public android.webkit.WebResourceResponse shouldInterceptRequest(
            WebView view, WebResourceRequest request) {
        if (session != null && session.isActive() && strategy != null && appContext != null
                && request != null && !request.isForMainFrame()
                && "GET".equalsIgnoreCase(request.getMethod()) && isCandidate(request.getUrl())) {
            Map<String, String> headers = new HashMap<>(request.getRequestHeaders());
            java.util.Iterator<String> names = headers.keySet().iterator();
            while (names.hasNext()) if ("User-Agent".equalsIgnoreCase(names.next())) names.remove();
            headers.put("User-Agent", userAgent);
            String resourceUrl = cleanUrl(request.getUrl());
            String referer = findHeader(headers, "Referer");
            String top = pageUrl;
            boolean sameOrigin = isSameOrigin(top, resourceUrl)
                    && (referer == null || isSameOrigin(referer, top));
            String authorization = findHeader(headers, "Authorization");
            WebResourceMetadataObserver.submit(appContext, new WebResourceMetadataObserver.Snapshot(
                    resourceUrl, page, session, cookieManager, authorization, sameOrigin, headers));
        }
        return null;
    }

    /**
     * 允许 WebView 内核正常处理 HTTP(S) 导航。
     */
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String scheme = request.getUrl().getScheme();
        return scheme != null && !"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme);
    }

    /**
     * 仅按 URL 路径扩展名选择静态观察对象。
     */
    private boolean isCandidate(Uri uri) {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        String path = uri.getPath();
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.US);
        String[] extensions = {".js", ".mjs", ".css", ".png", ".webp", ".ico", ".jpg",
                ".jpeg", ".gif", ".svg", ".woff", ".woff2", ".ttf", ".otf"};
        for (String extension : extensions) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 去除 fragment，同时保留可能参与资源身份的 query。
     */
    private String cleanUrl(Uri uri) {
        return uri.buildUpon().fragment(null).build().toString();
    }

    /**
     * 判断 Referer 与目标资源是否处于同源。
     */
    private boolean isSameOrigin(String pageUrl, String resourceUrl) {
        if (pageUrl == null) {
            return false;
        }
        Uri page = Uri.parse(pageUrl);
        Uri resource = Uri.parse(resourceUrl);
        return equal(page.getScheme(), resource.getScheme())
                && equal(page.getHost(), resource.getHost())
                && effectivePort(page) == effectivePort(resource);
    }

    /**
     * 比较可能为空的 URI 字段。
     */
    private boolean equal(String first, String second) {
        return first == null ? second == null : first.equalsIgnoreCase(second);
    }

    /**
     * 忽略请求头名称大小写读取指定头。
     */
    private String findHeader(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 同源比较归一化默认端口。 */
    private int effectivePort(Uri uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}