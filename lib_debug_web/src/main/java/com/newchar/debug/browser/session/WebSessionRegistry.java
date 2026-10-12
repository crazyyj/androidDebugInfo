package com.newchar.debug.browser.session;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 仅在工厂串行后台队列中访问的会话注册表与待删队列。 */
public final class WebSessionRegistry {
    /** 同时返回真实逻辑 ID、内核名称和代次，空 ID 只在这里生成。 */
    public Entry resolveEntry(Context context, String sessionId) throws Exception {
        String id = sessionId == null || sessionId.trim().isEmpty() ? token() : sessionId;
        JSONObject root = read(context);
        JSONArray sessions = array(root, "sessions");
        JSONObject item = find(sessions, id);
        if (item == null) {
            item = new JSONObject().put("sessionId", id).put("profileName", name(0))
                    .put("generation", 0).put("state", "READY");
            sessions.put(item);
        }
        if (!"READY".equals(item.optString("state"))) {
            throw new IllegalStateException("SESSION_UNAVAILABLE: " + item.optString("state"));
        }
        item.put("lastUsedAt", System.currentTimeMillis());
        write(context, root);
        return entry(item);
    }

    /** 兼容旧名称解析入口。 */
    public String resolve(Context context, String id) throws Exception {
        return resolveEntry(context, id).namespace;
    }

    /** 先持久化停用状态和新代次；进程中断后不会恢复清理中的旧登录态。 */
    public void beginOperation(Context context, WebSessionStrategy.Session session,
                               boolean retire) throws Exception {
        JSONObject root = read(context);
        JSONObject item = required(root, session.sessionId);
        boolean retry = "ERROR".equals(item.optString("state"))
                && item.getLong("generation") == session.generation + 1;
        if (!session.namespace.equals(item.getString("profileName"))
                || (session.generation != item.getLong("generation") && !retry)) {
            throw new IllegalStateException("STALE_SESSION: 会话已经换代");
        }
        if ("RETIRED".equals(item.optString("state"))) {
            throw new IllegalStateException("SESSION_RETIRED: 会话已删除");
        }
        item.put("generation", session.generation + 1)
                .put("state", retire ? "RETIRED" : "CLEARING");
        if (retire) enqueue(root, session.namespace);
        write(context, root);
    }

    /** 完整清理后沿用名称；能力缺失或清理失败时换新名称并登记旧名称待删。 */
    public Entry completeReset(Context context, WebSessionStrategy.Session old,
                               boolean rotate) throws Exception {
        JSONObject root = read(context);
        JSONObject item = required(root, old.sessionId);
        if (!"CLEARING".equals(item.optString("state"))
                || item.getLong("generation") != old.generation + 1) {
            throw new IllegalStateException("STALE_OPERATION: 清理结果已失效");
        }
        if (rotate) {
            enqueue(root, old.namespace);
            item.put("profileName", name(item.getLong("generation")));
        }
        item.put("state", "READY");
        write(context, root);
        return entry(item);
    }

    /** 清理结果未知时保持 ERROR，后续可显式重新重置但禁止直接导航。 */
    public void markError(Context context, WebSessionStrategy.Session session) throws Exception {
        JSONObject root = read(context);
        JSONObject item = required(root, session.sessionId);
        if (!"CLEARING".equals(item.optString("state"))
                || item.getLong("generation") != session.generation + 1) return;
        item.put("state", "ERROR");
        write(context, root);
    }

    /** 冷启动恢复中断的显式清理，换新环境并把旧名称加入删除队列。 */
    public void recoverInterrupted(Context context) throws Exception {
        JSONObject root = read(context);
        JSONArray sessions = array(root, "sessions");
        boolean changed = false;
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject item = sessions.getJSONObject(i);
            String state = item.optString("state");
            if ("CLEARING".equals(state) || "ERROR".equals(state)) {
                enqueue(root, item.getString("profileName"));
                long generation = item.getLong("generation") + 1;
                item.put("generation", generation).put("profileName", name(generation)).put("state", "READY");
                changed = true;
            }
        }
        if (changed) write(context, root);
    }

    /** 读取待删名称，不加载任何内核 Profile。 */
    public List<String> pendingDeletes(Context context) throws Exception {
        JSONArray pending = array(read(context), "pendingDeletes");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < pending.length(); i++) {
            String name = pending.getString(i);
            if (name.matches("web_[a-f0-9]+_g[0-9]+")) names.add(name);
        }
        return names;
    }

    /** 仅移除已经受理删除或不存在的名称；失败名称保留到下次进程启动。 */
    public void acknowledgeDeletes(Context context, List<String> completed) throws Exception {
        JSONObject root = read(context);
        JSONArray pending = array(root, "pendingDeletes");
        JSONArray remaining = new JSONArray();
        for (int i = 0; i < pending.length(); i++) {
            if (!completed.contains(pending.getString(i))) remaining.put(pending.getString(i));
        }
        root.put("pendingDeletes", remaining);
        write(context, root);
    }

    /** 构造只包含内部安全名称的注册项快照。 */
    private Entry entry(JSONObject item) throws Exception {
        String namespace = item.getString("profileName");
        if (!namespace.matches("web_[a-f0-9]+_g[0-9]+")) {
            throw new IllegalStateException("INVALID_PROFILE_NAME: 注册表名称无效");
        }
        return new Entry(item.getString("sessionId"), namespace, item.getLong("generation"));
    }

    /** 查找已有逻辑会话。 */
    private JSONObject find(JSONArray sessions, String id) {
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject item = sessions.optJSONObject(i);
            if (item != null && id.equals(item.optString("sessionId"))) return item;
        }
        return null;
    }

    /** 要求会话存在，避免清理动作误创建账号。 */
    private JSONObject required(JSONObject root, String id) throws Exception {
        JSONObject item = find(array(root, "sessions"), id);
        if (item == null) throw new IllegalStateException("SESSION_NOT_FOUND");
        return item;
    }

    /** 返回或初始化数组字段。 */
    private JSONArray array(JSONObject root, String key) throws Exception {
        JSONArray result = root.optJSONArray(key);
        if (result == null) { result = new JSONArray(); root.put(key, result); }
        return result;
    }

    /** 名称永不复用，重复入队保持幂等。 */
    private void enqueue(JSONObject root, String namespace) throws Exception {
        JSONArray pending = array(root, "pendingDeletes");
        for (int i = 0; i < pending.length(); i++) {
            if (namespace.equals(pending.getString(i))) return;
        }
        pending.put(namespace);
    }

    /** 生成内核安全名称。 */
    private String name(long generation) { return "web_" + token() + "_g" + generation; }

    /** 生成不暴露账号身份的随机标识。 */
    private String token() { return UUID.randomUUID().toString().replace("-", ""); }

    /** 获取私有原子注册表文件。 */
    private AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(), "web_sessions/registry.json"));
    }

    /** 损坏注册表直接报错，禁止默默建立新映射而遗失墓碑和删除队列。 */
    private JSONObject read(Context context) throws Exception {
        AtomicFile file = file(context);
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) {
            return new JSONObject();
        }
        try (FileInputStream input = file.openRead();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
        }
    }

    /** 原子保存状态，失败时保留原文件并向调用方报告。 */
    private void write(Context context, JSONObject root) throws Exception {
        AtomicFile file = file(context);
        File parent = file.getBaseFile().getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IllegalStateException("无法创建会话目录");
        FileOutputStream output = file.startWrite();
        try {
            output.write(root.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (Exception error) {
            file.failWrite(output);
            throw error;
        }
    }

    /** 真实逻辑 ID 与持久化内核环境的对应关系。 */
    public static final class Entry {
        public final String sessionId;
        public final String namespace;
        public final long generation;
        /** 创建不可变注册项。 */
        public Entry(String id, String namespace, long generation) {
            this.sessionId = id; this.namespace = namespace; this.generation = generation;
        }
    }
}