package com.newchar.debug.browser.session;

import android.content.Context;
import android.content.ContextWrapper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import static org.junit.Assert.*;

/** 在真实 Android AtomicFile 和 JSON 实现上验证注册表生命周期。 */
@RunWith(AndroidJUnit4.class)
public class WebSessionRegistryTest {
    private Context context;
    private WebSessionRegistry registry;

    /** 各用例使用独立私有目录，不触碰实际账号注册表。 */
    @Before public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(target.getCacheDir(), "registry-test-" + java.util.UUID.randomUUID());
        context = new ContextWrapper(target) {
            /** 隔离测试注册表路径。 */
            @Override public File getNoBackupFilesDir() { return directory; }
        };
        registry = new WebSessionRegistry();
    }

    /** null 和空白 ID 返回真实生成 ID，该 ID 可以稳定恢复同一 Profile。 */
    @Test public void restoresGeneratedIds() throws Exception {
        for (String input : new String[]{null, " "}) {
            WebSessionRegistry.Entry first = registry.resolveEntry(context, input);
            WebSessionRegistry.Entry restored = registry.resolveEntry(context, first.sessionId);
            assertEquals(first.namespace, restored.namespace); assertEquals(0, restored.generation);
            assertNotEquals(first.sessionId, first.namespace);
        }
    }

    /** 完整清理保留名称但提高代次，清理中的账号不能导航。 */
    @Test public void fullClearIncrementsGeneration() throws Exception {
        WebSessionRegistry.Entry first = registry.resolveEntry(context, "A");
        WebSessionStrategy.Session old = session(first);
        registry.beginOperation(context, old, false);
        assertUnavailable("A");
        WebSessionRegistry.Entry next = registry.completeReset(context, old, false);
        assertEquals(first.namespace, next.namespace); assertEquals(1, next.generation);
    }

    /** 兜底换新 Profile，旧名称进入待删队列且不能覆盖其他账号。 */
    @Test public void rotatesOnlyRequestedAccount() throws Exception {
        WebSessionRegistry.Entry first = registry.resolveEntry(context, "A");
        WebSessionRegistry.Entry other = registry.resolveEntry(context, "B");
        registry.beginOperation(context, session(first), false);
        WebSessionRegistry.Entry next = registry.completeReset(context, session(first), true);
        assertNotEquals(first.namespace, next.namespace);
        assertTrue(registry.pendingDeletes(context).contains(first.namespace));
        assertEquals(other.namespace, registry.resolveEntry(context, "B").namespace);
    }

    /** 退役墓碑禁止原 ID 重新打开，待删队列只有显式确认才移除。 */
    @Test public void retirementPersistsTombstone() throws Exception {
        WebSessionRegistry.Entry first = registry.resolveEntry(context, "A");
        registry.beginOperation(context, session(first), true);
        assertUnavailable("A");
        assertTrue(registry.pendingDeletes(context).contains(first.namespace));
        registry.acknowledgeDeletes(context, Collections.singletonList(first.namespace));
        assertTrue(registry.pendingDeletes(context).isEmpty()); assertUnavailable("A");
    }

    /** 清理中进程中断，冷启动换新环境并登记旧环境待删。 */
    @Test public void recoversInterruptedClear() throws Exception {
        WebSessionRegistry.Entry first = registry.resolveEntry(context, "A");
        registry.beginOperation(context, session(first), false);
        registry.recoverInterrupted(context);
        WebSessionRegistry.Entry next = registry.resolveEntry(context, "A");
        assertNotEquals(first.namespace, next.namespace);
        assertTrue(registry.pendingDeletes(context).contains(first.namespace));
        assertTrue(next.generation > first.generation);
    }

    /** 清理失败后禁止导航，显式重试可以换新 Profile。 */
    @Test public void retriesFailedClear() throws Exception {
        WebSessionRegistry.Entry first = registry.resolveEntry(context, "A");
        registry.beginOperation(context, session(first), false);
        registry.markError(context, session(first)); assertUnavailable("A");
        registry.beginOperation(context, session(first), false);
        assertNotEquals(first.namespace, registry.completeReset(context, session(first), true).namespace);
    }

    /** 迟到的清理结果不能覆盖已经 READY 的新代次。 */
    @Test public void rejectsLateCompletion() throws Exception {
        WebSessionRegistry.Entry first = registry.resolveEntry(context, "A");
        registry.beginOperation(context, session(first), false);
        WebSessionRegistry.Entry next = registry.completeReset(context, session(first), true);
        try { registry.completeReset(context, session(first), false); fail("旧回调应失效"); }
        catch (IllegalStateException expected) { assertEquals(next.namespace, registry.resolveEntry(context, "A").namespace); }
    }

    /** 损坏文件不能静默覆盖并遗失墓碑。 */
    @Test public void rejectsCorruptRegistry() throws Exception {
        registry.resolveEntry(context, "A");
        File file = new File(context.getNoBackupFilesDir(), "web_sessions/registry.json");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
            output.write("broken-json".getBytes(StandardCharsets.UTF_8));
        }
        try { registry.resolveEntry(context, "A"); fail("损坏注册表应报告错误"); }
        catch (org.json.JSONException expected) { assertTrue(file.exists()); }
    }

    /** 构造不加载内核 Profile 的注册表测试句柄。 */
    private WebSessionStrategy.Session session(WebSessionRegistry.Entry entry) {
        return new WebSessionStrategy.Session(entry.sessionId, entry.namespace, entry.generation, null, true);
    }

    /** 确认非 READY 会话不允许通过普通打开入口。 */
    private void assertUnavailable(String id) throws Exception {
        try { registry.resolveEntry(context, id); fail("会话应该不可用"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("SESSION_UNAVAILABLE")); }
    }
}