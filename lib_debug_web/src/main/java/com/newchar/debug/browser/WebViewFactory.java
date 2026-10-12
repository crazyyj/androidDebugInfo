package com.newchar.debug.browser;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import com.newchar.debug.browser.session.DefaultWebSessionStrategy;
import com.newchar.debug.browser.session.ProfileWebSessionStrategy;
import com.newchar.debug.browser.session.WebSessionRegistry;
import com.newchar.debug.browser.session.WebSessionStrategy;
import com.newchar.debug.browser.store.WebResourceMetadataObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 主线程管理内核与页面，串行后台队列管理注册表和清理屏障。 */
public final class WebViewFactory {
    private final ExecutorService sessionIo = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<WebView, ManagedView> managedViews = new IdentityHashMap<>();
    private final Map<String, WebSessionStrategy.Session> sessions = new HashMap<>();
    private final Map<String, Integer> operationVersions = new HashMap<>();
    private final Set<String> operations = new HashSet<>();
    private final List<Runnable> initWaiters = new ArrayList<>();
    private final WebSessionRegistry registry = new WebSessionRegistry();
    private volatile WebSessionStrategy strategy;
    private Context appContext;
    private boolean initializing;
    private boolean initialized;
    private Exception initializationError;
    private int version;
    private boolean namedProfileDelivered;

    /** 返回共享工厂；Profile 不会被跨会话复用。 */
    public static WebViewFactory getInstance() { return Holder.INSTANCE; }

    /** 延迟构造工厂。 */
    private static class Holder { private static final WebViewFactory INSTANCE = new WebViewFactory(); }

    /** 异步打开会话，所有内核 API 和回调均在主线程执行。 */
    public void createWebView(Context context, String sessionId, WebViewCallback callback) {
        onMain(() -> {
            appContext = context.getApplicationContext();
            int expected = version;
            initialize(() -> {
                if (expected != version) { callback.onError(new IllegalStateException("创建已取消")); return; }
                if (initializationError != null) { callback.onError(initializationError); return; }
                resolveSession(context, sessionId, callback, expected);
            });
        });
    }

    /** 保留同步旧入口，仅打开共享默认环境；必须在主线程调用。 */
    public WebView getWebView() {
        requireMain();
        Context context = appContext == null ? WebFileUtils.appContext : appContext;
        if (context == null) throw new IllegalStateException("请先初始化 WebView 工厂");
        WebSessionStrategy selected = defaultStrategy("兼容无参数工厂入口");
        WebSessionStrategy.Session session = sessions.get("default");
        if (operations.contains("default")) throw new IllegalStateException("默认会话正在清理");
        if (session == null) { session = selected.openSession("default"); sessions.put("default", session); }
        if (!session.isActive()) throw new IllegalStateException("默认会话不可用");
        return createBoundWebView(context, session, selected, null);
    }

    /** 仅保存应用上下文，不预建命名 Profile。 */
    public void preLoadWebView(Context context) {
        onMain(() -> { appContext = context.getApplicationContext(); WebFileUtils.appContext = appContext; });
    }

    /** 正常关闭窗口只取消页面任务，账号登录状态保留。 */
    public void releaseWebView(WebView webView) { onMain(() -> destroyView(webView, false)); }

    /** 关闭工厂并使尚未交付的创建失效；不删除持久化登录数据。 */
    public void destroy() {
        onMain(() -> {
            version++;
            for (WebSessionStrategy.Session session : sessions.values()) {
                WebResourceMetadataObserver.invalidateSession(session);
            }
            for (WebView view : new ArrayList<>(managedViews.keySet())) destroyView(view, false);
            sessions.clear();
            WebResourceMetadataObserver.cancelAll();
        });
    }

    /** 获取实际策略能力；调用方应在主线程读取。 */
    public WebSessionStrategy.Capabilities getCapabilities(Context context) {
        requireMain();
        return chooseStrategy(context.getApplicationContext()).getCapabilities();
    }

    /** 显式重置浏览数据，对同会话全部窗口生效，不代表撤销服务器登录。 */
    public void clearSessionData(WebSessionStrategy.Session session, SessionOperationCallback callback) {
        onMain(() -> beginOperation(session, false, callback));
    }

    /** 删除账号映射并登记待删名称，已加载 Profile 到下次冷启动才能物理删除。 */
    public void retireSession(WebSessionStrategy.Session session, SessionOperationCallback callback) {
        onMain(() -> beginOperation(session, true, callback));
    }

    /** 任何 Profile 获取或绑定之前完成一次冷启动待删处理。 */
    private void initialize(Runnable continuation) {
        if (initialized) { continuation.run(); return; }
        initWaiters.add(continuation);
        if (initializing) return;
        initializing = true;
        try { chooseStrategy(appContext); }
        catch (RuntimeException error) { finishInitialization(error); return; }
        sessionIo.execute(() -> {
            try {
                WebResourceMetadataObserver.maintain(appContext);
                if (strategy instanceof ProfileWebSessionStrategy) registry.recoverInterrupted(appContext);
                List<String> pending = strategy instanceof ProfileWebSessionStrategy
                        ? registry.pendingDeletes(appContext) : new ArrayList<>();
                mainHandler.post(() -> deletePendingProfiles(pending));
            } catch (Exception error) { mainHandler.post(() -> finishInitialization(error)); }
        });
    }

    /** UI 线程直接删除名称，绝不为了检查存在而加载 Profile。 */
    private void deletePendingProfiles(List<String> pending) {
        List<String> completed = new ArrayList<>();
        for (String name : pending) {
            try {
                ((ProfileWebSessionStrategy) strategy).deleteUnloadedProfile(name);
                completed.add(name);
            } catch (UnsupportedOperationException unsupported) {
                strategy = defaultStrategy("冷启动 Profile 删除 API 不受支持");
                break;
            } catch (IllegalStateException loaded) { /* 外部已加载名称留到下次冷启动。 */ }
            catch (RuntimeException error) { finishInitialization(error); return; }
        }
        sessionIo.execute(() -> {
            try {
                for (String name : completed) WebResourceMetadataObserver.clearSessionMetadata(appContext, name);
                if (!completed.isEmpty()) registry.acknowledgeDeletes(appContext, completed);
                mainHandler.post(() -> finishInitialization(null));
            } catch (Exception error) { mainHandler.post(() -> finishInitialization(error)); }
        });
    }

    /** 一次性释放等待创建的请求，初始化失败明确报告并禁止打开。 */
    private void finishInitialization(Exception error) {
        initializationError = error;
        initialized = true;
        initializing = false;
        List<Runnable> waiters = new ArrayList<>(initWaiters);
        initWaiters.clear();
        for (Runnable waiter : waiters) waiter.run();
    }

    /** 后台解析实际逻辑 ID，再在 UI 线程获取 Profile。 */
    private void resolveSession(Context context, String id, WebViewCallback callback, int expected) {
        WebSessionStrategy selected = strategy;
        if (!(selected instanceof ProfileWebSessionStrategy)) {
            if (id != null && !id.trim().isEmpty() && !"default".equals(id) && !"debug_web".equals(id)) {
                callback.onError(new IllegalStateException("MULTI_ACCOUNT_UNAVAILABLE: 当前仅支持默认单账号"));
            } else createDefaultView(context, callback);
            return;
        }
        if (id != null && operations.contains(id)) {
            callback.onError(new IllegalStateException("SESSION_BUSY: 会话正在重置或删除")); return;
        }
        sessionIo.execute(() -> {
            try {
                WebSessionRegistry.Entry entry = registry.resolveEntry(appContext, id);
                mainHandler.post(() -> createProfileView(context, entry, selected, callback, expected));
            } catch (Exception error) { mainHandler.post(() -> callback.onError(error)); }
        });
    }

    /** 重复窗口共用同一个代次句柄，任意清理可以同时失效全部窗口。 */
    private void createProfileView(Context context, WebSessionRegistry.Entry entry,
                                   WebSessionStrategy selected, WebViewCallback callback, int expected) {
        if (expected != version || operations.contains(entry.sessionId)) {
            callback.onError(new IllegalStateException("会话创建已失效")); return;
        }
        if (strategy != selected) {
            callback.onError(new IllegalStateException("PROFILE_UNAVAILABLE: 原账号暂不可用，请显式打开 default"));
            return;
        }
        try {
            WebSessionStrategy.Session session = sessions.get(entry.sessionId);
            if (session == null || session.generation != entry.generation || !session.namespace.equals(entry.namespace)) {
                session = ((ProfileWebSessionStrategy) selected).openResolvedSession(entry);
                sessions.put(entry.sessionId, session);
            }
            if (!session.isActive()) throw new IllegalStateException("SESSION_UNAVAILABLE");
            WebView view = createBoundWebView(context, session, selected, callback);
            namedProfileDelivered = true;
            callback.onReady(view, session, selected.getCapabilities());
        } catch (UnsupportedOperationException unsupported) {
            boolean hadNamedSession = hasNamedView();
            downgradeProfiles();
            if (hadNamedSession) callback.onError(new IllegalStateException("PROFILE_UNAVAILABLE: 原账号会话暂不可用"));
            else createDefaultView(context, callback);
        } catch (RuntimeException error) { callback.onError(error); }
    }

    /** 判断本进程是否曾交付过命名实例，后续异常不能静默切换原账号。 */
    private boolean hasNamedView() {
        return namedProfileDelivered;
    }

    /** 交付的真实 ID 固定为 default，不能把默认登录态标成命名账号。 */
    private void createDefaultView(Context context, WebViewCallback callback) {
        try {
            if (operations.contains("default")) throw new IllegalStateException("默认会话正在清理");
            WebSessionStrategy selected = strategy instanceof DefaultWebSessionStrategy
                    ? strategy : defaultStrategy("设备不支持多 Profile");
            WebSessionStrategy.Session session = sessions.get("default");
            if (session == null) { session = selected.openSession("default"); sessions.put("default", session); }
            if (!session.isActive()) throw new IllegalStateException("默认会话清理结果未知");
            callback.onReady(createBoundWebView(context, session, selected, callback), session, selected.getCapabilities());
        } catch (RuntimeException error) { callback.onError(error); }
    }

    /** 首先绑定，再安装客户端与设置；半初始化实例异常时直接销毁。 */
    private WebView createBoundWebView(Context context, WebSessionStrategy.Session session,
                                       WebSessionStrategy selected, WebViewCallback callback) {
        WebView view = new WebView(new MutableContextWrapper(context));
        try {
            selected.bindBeforeInitialization(view, session);
            if (session.profileMode && !session.namespace.equals(WebViewCompat.getProfile(view).getName())) {
                throw new IllegalStateException("WebView Profile 与会话不匹配");
            }
            new WebSettingsBuilder(view.getSettings()).buildDefault();
            WebResourceMetadataObserver.Page page = new WebResourceMetadataObserver.Page();
            view.setWebViewClient(new SimpleWebClient(session, selected, context, page,
                    view.getSettings().getUserAgentString(), affected -> rendererGone(affected)));
            view.setWebChromeClient(new SimpleWebChrome(session));
            managedViews.put(view, new ManagedView(session, page, callback));
            return view;
        } catch (RuntimeException error) { view.destroy(); throw error; }
    }

    /** 只对确切不支持异常降级，磁盘及其他运行异常不能误清登录环境。 */
    private WebSessionStrategy chooseStrategy(Context context) {
        if (strategy != null) return strategy;
        WebFileUtils.appContext = context;
        try {
            strategy = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
                    ? new ProfileWebSessionStrategy(context) : defaultStrategy("内核不支持 MULTI_PROFILE");
        } catch (UnsupportedOperationException error) { strategy = defaultStrategy("ProfileStore 不受支持"); }
        return strategy;
    }

    /** 构造携带实际降级原因的单账号策略。 */
    private WebSessionStrategy defaultStrategy(String reason) { return new DefaultWebSessionStrategy(reason); }

    /** 渲染退出不可再 stopLoading，只处理当前受影响实例并通知宿主。 */
    private void rendererGone(WebView view) {
        ManagedView managed = managedViews.get(view);
        if (managed == null) return;
        destroyView(view, true);
        if (managed.callback != null) managed.callback.onRenderProcessGone(managed.session);
    }

    /** 受控实例只销毁一次，先取消任务并从视图树移除。 */
    private void destroyView(WebView view, boolean rendererGone) {
        ManagedView managed = managedViews.remove(view);
        if (managed == null) return;
        WebResourceMetadataObserver.cancelPage(managed.page);
        if (!rendererGone) view.stopLoading();
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        view.destroy();
    }

    /** 集中关闭命名实例，保留注册表和默认会话，原账号不会变成默认账号。 */
    private void downgradeProfiles() {
        for (WebSessionStrategy.Session session : sessions.values()) {
            if (session.profileMode) WebResourceMetadataObserver.invalidateSession(session);
        }
        for (Map.Entry<WebView, ManagedView> item : new ArrayList<>(managedViews.entrySet())) {
            if (item.getValue().session.profileMode) {
                ManagedView managed = item.getValue();
                destroyView(item.getKey(), false);
                if (managed.callback != null) managed.callback.onSessionUnavailable(managed.session);
            }
        }
        strategy = defaultStrategy("Profile API 不受支持；原账号会话暂不可用");
    }

    /** 先停用实例与观察，再后台持久化墓碑；整个操作期间拒绝新建窗口。 */
    private void beginOperation(WebSessionStrategy.Session session, boolean retire, SessionOperationCallback callback) {
        if (session == null || sessions.get(session.sessionId) != session || operations.contains(session.sessionId)) {
            callback.onComplete("ERROR", null, new IllegalStateException("STALE_OR_BUSY_SESSION")); return;
        }
        WebSessionStrategy selected = session.profileMode ? strategy : defaultStrategy("默认会话显式清理");
        operations.add(session.sessionId);
        operationVersions.put(session.sessionId, version);
        WebResourceMetadataObserver.invalidateSession(session);
        closeSessionViews(session);
        sessionIo.execute(() -> {
            try {
                if (session.profileMode) registry.beginOperation(appContext, session, retire);
                WebResourceMetadataObserver.awaitSessionIdle(session.namespace);
                WebResourceMetadataObserver.clearSessionMetadata(appContext, session.namespace);
                mainHandler.post(() -> invokeClear(session, selected, retire, callback));
            } catch (Exception error) {
                if (session.profileMode && !retire) {
                    try { registry.markError(appContext, session); } catch (Exception persistence) { error.addSuppressed(persistence); }
                }
                mainHandler.post(() -> operationFailed(session, callback, error));
            }
        });
    }

    /** 默认能力不足时，在销毁本模块存活窗口之前尽力清理默认缓存。 */
    private void closeSessionViews(WebSessionStrategy.Session session) {
        for (Map.Entry<WebView, ManagedView> item : new ArrayList<>(managedViews.entrySet())) {
            if (item.getValue().session == session) {
                if (!session.profileMode) item.getKey().clearCache(true);
                ManagedView managed = item.getValue();
                destroyView(item.getKey(), false);
                if (managed.callback != null) managed.callback.onSessionUnavailable(session);
            }
        }
    }

    /** 内核回调与十五秒超时竞争，迟到结果不能覆盖新代次。 */
    private void invokeClear(WebSessionStrategy.Session session, WebSessionStrategy selected,
                             boolean retire, SessionOperationCallback callback) {
        AtomicBoolean completed = new AtomicBoolean();
        Runnable timeout = () -> {
            if (completed.compareAndSet(false, true)) {
                String code = retire ? "DELETE_PENDING" : session.profileMode ? "RESET_WITH_NEW_PROFILE" : "ERROR";
                finishOperation(session, retire, code, new IllegalStateException("清理超时，旧环境结果未知"), callback);
            }
        };
        mainHandler.postDelayed(timeout, 15000);
        WebSessionStrategy.OperationCallback done = (code, error) -> onMain(() -> {
            if (completed.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout);
                finishOperation(session, retire, code, error, callback);
            }
        });
        try {
            if (retire) selected.retireSession(session, done);
            else selected.clearSessionData(session, done);
        } catch (RuntimeException error) { done.onComplete("ERROR", error); }
    }

    /** 清理回调后才更新持久化代次；退役名称永不再次打开。 */
    private void finishOperation(WebSessionStrategy.Session session, boolean retire, String code,
                                 Exception cause, SessionOperationCallback callback) {
        sessionIo.execute(() -> {
            try {
                WebSessionRegistry.Entry entry = null;
                if ("ERROR".equals(code)) {
                    if (session.profileMode && !retire) registry.markError(appContext, session);
                    mainHandler.post(() -> operationFailed(session, callback, cause)); return;
                }
                if (session.profileMode && !retire) {
                    entry = registry.completeReset(appContext, session, !"DATA_CLEARED".equals(code));
                }
                WebSessionRegistry.Entry ready = entry;
                mainHandler.post(() -> deliverOperation(session, ready, retire, code, cause, callback));
            } catch (Exception error) {
                if (session.profileMode && !retire) {
                    try { registry.markError(appContext, session); } catch (Exception persistence) { error.addSuppressed(persistence); }
                }
                mainHandler.post(() -> operationFailed(session, callback, error));
            }
        });
    }

    /** 交付新的真实句柄，旧句柄始终失效；删除只报告待删，不宣称磁盘清空。 */
    private void deliverOperation(WebSessionStrategy.Session old, WebSessionRegistry.Entry ready, boolean retire,
                                  String code, Exception cause, SessionOperationCallback callback) {
        try {
            if (!Integer.valueOf(version).equals(operationVersions.get(old.sessionId))) {
                operationFailed(old, callback, new IllegalStateException("工厂已关闭，清理结果不再交付新实例")); return;
            }
            WebSessionStrategy.Session next = null;
            if (!retire) {
                next = old.profileMode ? ((ProfileWebSessionStrategy) strategy).openResolvedSession(ready)
                        : new WebSessionStrategy.Session("default", "default", old.generation + 1, null, false);
                sessions.put(old.sessionId, next);
            } else sessions.remove(old.sessionId);
            operations.remove(old.sessionId);
            operationVersions.remove(old.sessionId);
            callback.onComplete(code, next, cause);
        } catch (RuntimeException error) { operationFailed(old, callback, error); }
    }

    /** 失败保持旧句柄停用，禁止把清理未知环境恢复成可导航账号。 */
    private void operationFailed(WebSessionStrategy.Session session, SessionOperationCallback callback, Exception error) {
        operations.remove(session.sessionId);
        operationVersions.remove(session.sessionId);
        callback.onComplete("ERROR", null, error == null ? new IllegalStateException("会话操作失败") : error);
    }

    /** 任意线程调用入口均调度到 UI 线程。 */
    private void onMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run(); else mainHandler.post(action);
    }

    /** 同步内核入口禁止后台线程调用。 */
    private void requireMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("必须在主线程调用");
    }

    /** 每个实例独立持有任务归属和生命周期回调。 */
    private static final class ManagedView {
        final WebSessionStrategy.Session session;
        final WebResourceMetadataObserver.Page page;
        final WebViewCallback callback;
        /** 记录已绑定实例，不允许跨会话重新绑定。 */
        ManagedView(WebSessionStrategy.Session session, WebResourceMetadataObserver.Page page, WebViewCallback callback) {
            this.session = session; this.page = page; this.callback = callback;
        }
    }

    /** WebView 创建及实例失效通知均在主线程调用。 */
    public interface WebViewCallback {
        /** 已绑定真实会话并完成初始化。 */
        void onReady(WebView view, WebSessionStrategy.Session session, WebSessionStrategy.Capabilities capabilities);
        /** 创建失败，不交付半初始化实例。 */
        void onError(Exception error);
        /** 渲染进程退出；宿主下次显示时用原 ID 重建。 */
        default void onRenderProcessGone(WebSessionStrategy.Session session) { }
        /** Profile 能力失效；不得把原命名账号显示成默认账号。 */
        default void onSessionUnavailable(WebSessionStrategy.Session session) { }
    }

    /** 结果代码、重置后的句柄和异常分别报告，调用方不得忽略实际结果。 */
    public interface SessionOperationCallback {
        /** DATA_CLEARED、RESET_WITH_NEW_PROFILE、PARTIAL_CLEAR、DELETE_PENDING 或 ERROR。 */
        void onComplete(String code, WebSessionStrategy.Session nextSession, Exception error);
    }

    /** 关闭内核调试开关，不提前创建命名 Profile。 */
    public static void preGlobalInit() { WebView.setWebContentsDebuggingEnabled(false); }
}