package com.newchar.debug.browser;

import android.content.Context;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.webkit.WebViewFeature;
import com.newchar.debug.browser.session.WebSessionStrategy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** 在实际内核上验证兜底、重置和渲染进程退出；支持时验证 Profile 隔离。 */
@RunWith(AndroidJUnit4.class)
public class WebViewFactoryTest {
    private Context context;
    private WebViewFactory factory;

    /** 使用测试应用自己的进程与数据目录。 */
    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        factory = WebViewFactory.getInstance();
    }

    /** 每个用例销毁实例，保留会话登录数据由后续显式清理负责。 */
    @After public void tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> factory.destroy());
    }

    /** 不支持 Profile 时多个窗口共享真实 default，命名账号请求明确报错。 */
    @Test public void defaultFallbackRejectsIndependentAccounts() throws Exception {
        assumeTrue(!supportsProfiles());
        Open first = open("default"); Open second = open("debug_web");
        assertFalse(first.session.profileMode); assertEquals("default", first.session.sessionId);
        assertSame(first.session, second.session);
        Open rejected = openResult("independent-account");
        assertNotNull(rejected.error);
        assertTrue(rejected.error.getMessage().contains("MULTI_ACCOUNT_UNAVAILABLE"));
    }

    /** 默认清理等待内核回调，再交付新代次，并明确报告部分清理。 */
    @Test public void defaultClearReturnsFreshGeneration() throws Exception {
        assumeTrue(!supportsProfiles());
        Open first = open("default"); Open other = open("default");
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> code = new AtomicReference<>();
        AtomicReference<WebSessionStrategy.Session> next = new AtomicReference<>();
        factory.clearSessionData(first.session, (result, session, error) -> {
            code.set(result); next.set(session); done.countDown();
        });
        assertTrue(done.await(20, TimeUnit.SECONDS));
        assertTrue("DATA_CLEARED".equals(code.get()) || "PARTIAL_CLEAR".equals(code.get()));
        assertFalse(first.session.isActive()); assertFalse(other.session.isActive());
        assertNotNull(next.get()); assertEquals(first.session.generation + 1, next.get().generation);
        assertSame(next.get(), open("default").session);
    }

    /** 真正触发渲染退出，应用存活且可以按原 ID 新建实例。 */
    @Test public void handlesRendererCrashAndRestoresSession() throws Exception {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 26);
        Open first = open("default");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> first.view.loadUrl("chrome://crash"));
        assertTrue("应收到已处理的渲染退出回调", first.rendererGone.await(15, TimeUnit.SECONDS));
        Open restored = open(first.session.sessionId);
        assertEquals(first.session.namespace, restored.session.namespace);
        assertEquals(first.session.generation, restored.session.generation);
        assertNotSame(first.view, restored.view);
    }

    /** 原生 Profile 对同一来源隔离 Cookie 与 localStorage，并保持账号 A 数据。 */
    @Test public void isolatesProfileCookiesAndStorage() throws Exception {
        assumeTrue(supportsProfiles());
        String prefix = java.util.UUID.randomUUID().toString();
        Open a = open(prefix + "-A"); Open b = open(prefix + "-B");
        assertNotEquals(a.session.namespace, b.session.namespace);
        String url = "https://profile-test.invalid/";
        setCookie(a, url, "account=A"); setCookie(b, url, "account=B");
        assertEquals("account=A", cookie(a, url)); assertEquals("account=B", cookie(b, url));
        loadDocument(a, url); loadDocument(b, url);
        assertEquals("\"A\"", evaluate(a, "localStorage.setItem('account','A'); localStorage.getItem('account')"));
        assertEquals("null", evaluate(b, "localStorage.getItem('account')"));
        assertEquals("\"B\"", evaluate(b, "localStorage.setItem('account','B'); localStorage.getItem('account')"));
        assertEquals("\"A\"", evaluate(a, "localStorage.getItem('account')"));
        retire(a); retire(b);
    }

    /** 读取内核实际特性，避免用 Android SDK 版本替代功能检查。 */
    private boolean supportsProfiles() {
        AtomicReference<Boolean> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                result.set(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)));
        return result.get();
    }

    /** 打开结果必须成功，否则传播内核或持久化错误。 */
    private Open open(String id) throws Exception {
        Open result = openResult(id);
        if (result.error != null) throw result.error;
        assertNotNull(result.view); return result;
    }

    /** 等待异步工厂，回调中同时记录渲染进程失效通知。 */
    private Open openResult(String id) throws Exception {
        Open result = new Open();
        factory.createWebView(context, id, new WebViewFactory.WebViewCallback() {
            /** 保存真实会话和实例。 */
            @Override public void onReady(WebView view, WebSessionStrategy.Session session,
                                           WebSessionStrategy.Capabilities capabilities) {
                result.view = view; result.session = session; result.done.countDown();
            }
            /** 保存创建失败结果。 */
            @Override public void onError(Exception error) { result.error = error; result.done.countDown(); }
            /** 确认渲染退出已经被工厂处理。 */
            @Override public void onRenderProcessGone(WebSessionStrategy.Session session) { result.rendererGone.countDown(); }
        });
        assertTrue(result.done.await(15, TimeUnit.SECONDS));
        return result;
    }

    /** 等待会话专属 Cookie 管理器完成写入。 */
    private void setCookie(Open opened, String url, String value) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                opened.session.profile.getCookieManager().setCookie(url, value + "; Path=/", success -> done.countDown()));
        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    /** 在 UI 线程读取指定 Profile 的 Cookie。 */
    private String cookie(Open opened, String url) {
        AtomicReference<String> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> result.set(opened.session.profile.getCookieManager().getCookie(url)));
        return result.get();
    }

    /** 加载相同来源 HTML，测试 JS 存储隔离不依赖真实网站。 */
    private void loadDocument(Open opened, String url) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            opened.view.setWebViewClient(new android.webkit.WebViewClient() {
                /** 页面加载完毕后允许 JS 存储断言。 */
                @Override public void onPageFinished(WebView view, String address) { done.countDown(); }
            });
            opened.view.loadDataWithBaseURL(url, "<html><body>profile test</body></html>", "text/html", "UTF-8", null);
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }

    /** 执行 JS 并等待返回值。 */
    private String evaluate(Open opened, String script) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> opened.view.evaluateJavascript(script, value -> {
            result.set(value); done.countDown();
        }));
        assertTrue(done.await(5, TimeUnit.SECONDS)); return result.get();
    }

    /** Profile 删除必须报告待删队列，不能当作即时物理删除。 */
    private void retire(Open opened) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        factory.retireSession(opened.session, (code, next, error) -> { result.set(code); done.countDown(); });
        assertTrue(done.await(20, TimeUnit.SECONDS)); assertEquals("DELETE_PENDING", result.get());
    }

    /** 异步创建结果。 */
    private static final class Open {
        final CountDownLatch done = new CountDownLatch(1);
        final CountDownLatch rendererGone = new CountDownLatch(1);
        WebView view;
        WebSessionStrategy.Session session;
        Exception error;
    }
}