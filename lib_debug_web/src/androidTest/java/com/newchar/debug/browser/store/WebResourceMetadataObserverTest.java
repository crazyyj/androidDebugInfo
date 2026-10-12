package com.newchar.debug.browser.store;

import android.content.Context;
import com.newchar.debug.browser.session.WebSessionStrategy;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** 在 Android 实际原子文件上验证响应保存、取消和容量边界。 */
@RunWith(AndroidJUnit4.class)
public class WebResourceMetadataObserverTest {
    private Context context;
    private WebSessionStrategy.Session session;
    private WebResourceMetadataObserver.Page page;
    private WebResourceMetadataObserver.Snapshot snapshot;
    private Class<?> taskClass;
    private Object task;
    private File directory;

    /** 使用独立应用资源命名空间，不触碰实际会话元数据。 */
    @Before public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        session = new WebSessionStrategy.Session("test", "test_" + java.util.UUID.randomUUID(), 0, null, false);
        page = new WebResourceMetadataObserver.Page();
        snapshot = new WebResourceMetadataObserver.Snapshot("https://example.com/file.css", page, session,
                null, null, true, Collections.emptyMap());
        taskClass = Class.forName(WebResourceMetadataObserver.class.getName() + "$Task");
        java.lang.reflect.Constructor<?> constructor = taskClass.getDeclaredConstructor(String.class, Context.class,
                WebResourceMetadataObserver.Snapshot.class);
        constructor.setAccessible(true); task = constructor.newInstance("test-task", context, snapshot);
        directory = new File(context.getNoBackupFilesDir(), "web_resources/" + session.namespace);
        WebResourceMetadataObserver.setHostConfirmedNoGlobalAuthenticator(true);
    }

    /** 删除测试命名空间并撤销测试启用声明。 */
    @After public void tearDown() throws Exception {
        WebResourceMetadataObserver.invalidateSession(session);
        WebResourceMetadataObserver.clearSessionMetadata(context, session.namespace);
        WebResourceMetadataObserver.setHostConfirmedNoGlobalAuthenticator(false);
    }

    /** 登录 HTML 和 JSON 不创建静态资源文件，真实 CSS 保留验证器。 */
    @Test public void savesOnlyStaticMetadata() throws Exception {
        save(response(200, "text/html", "\"login\"", null), null);
        assertFalse(file().exists());
        save(response(200, "application/json", "\"error\"", null), null);
        assertFalse(file().exists());
        save(response(200, "text/css", "W/\"css\"", null), null);
        JSONObject record = read();
        assertEquals("W/\"css\"", record.getString("etag")); assertTrue(record.getBoolean("etagWeak"));
        assertFalse(record.toString().contains("https://example.com"));
    }

    /** no-store 删除同键旧记录而非保留上一次验证器。 */
    @Test public void noStoreInvalidatesPersistedRecord() throws Exception {
        save(response(200, "text/css", "\"old\"", null), null); assertTrue(file().exists());
        save(response(200, "text/css", "\"new\"", "private, no-store"), null);
        assertFalse(file().exists());
    }

    /** 304 缺省 MIME 和验证器时合并旧记录，不创建空内容记录。 */
    @Test public void merges304WithPreviousVariant() throws Exception {
        save(response(200, "text/css", "\"old\"", "max-age=100"), null);
        JSONObject previous = read();
        save(response(304, null, null, null), previous);
        JSONObject record = read();
        assertEquals(304, record.getInt("status")); assertEquals("\"old\"", record.getString("etag"));
        assertEquals("text/css", record.getString("contentType"));
        assertEquals("max-age=100", record.getString("cacheControl"));
    }

    /** 页面关闭后迟到的拦截不能投递任务，其他窗口会话仍然有效。 */
    @Test public void closedPageRejectsLateSubmission() throws Exception {
        WebResourceMetadataObserver.cancelPage(page);
        WebResourceMetadataObserver.submit(context, snapshot);
        assertTrue(session.isActive());
        java.lang.reflect.Field tasks = WebResourceMetadataObserver.class.getDeclaredField("TASKS");
        tasks.setAccessible(true);
        assertEquals(0, ((Map<?, ?>) tasks.get(null)).size());
        assertTaskInvalid();
    }

    /** 会话停用后，执行中任务落盘前的同一个检查必须拒绝旧代次。 */
    @Test public void invalidatedSessionRejectsTask() throws Exception {
        WebResourceMetadataObserver.invalidateSession(session);
        assertFalse(session.isActive()); assertTaskInvalid();
    }

    /** 全局 CookieHandler 存在时探测关闭，并恢复测试前的全局值。 */
    @Test public void rejectsGlobalCookieHandler() {
        java.net.CookieHandler previous = java.net.CookieHandler.getDefault();
        try {
            java.net.CookieHandler.setDefault(new java.net.CookieManager());
            assertFalse(WebResourceMetadataObserver.isProbeEnabled());
        } finally { java.net.CookieHandler.setDefault(previous); }
    }

    /** 一千条上限和三十天过期规则同时生效。 */
    @Test public void prunesExpiredAndExcessRecords() throws Exception {
        File generation = new File(directory, "0"); assertTrue(generation.mkdirs());
        for (int i = 0; i < 1002; i++) {
            File file = new File(generation, i + ".json"); assertTrue(file.createNewFile());
            if (i == 0) assertTrue(file.setLastModified(System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(31)));
        }
        Method prune = WebResourceMetadataObserver.class.getDeclaredMethod("prune", File.class);
        prune.setAccessible(true); prune.invoke(null, directory);
        assertEquals(1000, generation.listFiles().length);
        assertFalse(new File(generation, "0.json").exists());
    }

    /** 构造脱离连接的响应头，不涉及真实网站请求。 */
    private WebResourceProbe.Response response(int status, String type, String etag, String cacheControl) {
        Map<String, java.util.List<String>> headers = new HashMap<>();
        if (type != null) headers.put("Content-Type", Collections.singletonList(type));
        if (etag != null) headers.put("ETag", Collections.singletonList(etag));
        if (cacheControl != null) headers.put("Cache-Control", Collections.singletonList(cacheControl));
        return new WebResourceProbe.Response(status, "HEAD", snapshot.url, headers);
    }

    /** 调用保存阶段，使用真实 Android AtomicFile 和 JSON。 */
    private void save(WebResourceProbe.Response response, JSONObject previous) throws Exception {
        Method method = WebResourceMetadataObserver.class.getDeclaredMethod("saveResult", taskClass, String.class,
                String.class, Map.class, JSONObject.class, WebResourceProbe.Response.class);
        method.setAccessible(true);
        method.invoke(null, task, "test-key", "test-variant", WebResourceProbe.requestHeaders(Collections.emptyMap(), ""), previous, response);
    }

    /** 检查任务状态是否已失效。 */
    private void assertTaskInvalid() throws Exception {
        Method check = taskClass.getDeclaredMethod("check"); check.setAccessible(true);
        try { check.invoke(task); fail("失效任务必须停止"); }
        catch (java.lang.reflect.InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof java.io.InterruptedIOException);
        }
    }

    /** 返回当前测试记录路径。 */
    private File file() { return new File(directory, "0/test-key.json"); }

    /** 读取落盘记录用于行为断言。 */
    private JSONObject read() throws Exception {
        try (java.io.FileInputStream input = new java.io.FileInputStream(file());
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int size;
            while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size);
            return new JSONObject(output.toString("UTF-8"));
        }
    }
}