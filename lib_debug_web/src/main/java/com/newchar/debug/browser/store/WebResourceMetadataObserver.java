package com.newchar.debug.browser.store;

import android.content.Context;
import android.util.AtomicFile;
import com.newchar.debug.browser.session.WebSessionStrategy;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 异步资源观察、按页面取消及会话代次校验；不替换内核响应。 */
public final class WebResourceMetadataObserver {
    private static final Object STORE = new Object();
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(2, 2, 30L,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.AbortPolicy());
    private static final Map<String, Task> TASKS = new HashMap<>();
    private static final LinkedHashMap<String, Long> RECENT = new LinkedHashMap<>();
    private static final LinkedHashMap<String, JSONObject> MEMORY = new LinkedHashMap<>();
    private static volatile boolean hostConfirmedNoGlobalAuthenticator;

    /** 宿主声明整个生命周期不安装全局 Authenticator；撤销声明立即取消任务。 */
    public static void setHostConfirmedNoGlobalAuthenticator(boolean confirmed) {
        hostConfirmedNoGlobalAuthenticator = confirmed;
        if (!confirmed) cancelAll();
    }

    /** 可供界面显示实际观察状态；不修改进程全局 CookieHandler。 */
    public static boolean isProbeEnabled() {
        try { return hostConfirmedNoGlobalAuthenticator && CookieHandler.getDefault() == null; }
        catch (SecurityException ignored) { return false; }
    }

    /** 拦截回调只投递内存快照；队列满直接丢弃，不访问 Cookie 或磁盘。 */
    public static void submit(Context context, Snapshot snapshot) {
        if (!isProbeEnabled() || !snapshot.sameOrigin || snapshot.authorization != null) return;
        String id = snapshot.page.owner + ":" + digest(snapshot.url + WebResourceProbe.variantText(snapshot.headers));
        synchronized (STORE) {
            if (!snapshot.session.isActive() || !snapshot.page.active || TASKS.containsKey(id)) return;
            Task task = new Task(id, context.getApplicationContext(), snapshot);
            TASKS.put(id, task);
            try { EXECUTOR.execute(task); }
            catch (java.util.concurrent.RejectedExecutionException ignored) { TASKS.remove(id); }
        }
    }

    /** 普通窗口关闭只取消该窗口任务，不停用同会话其他窗口。 */
    public static void cancelPage(Page page) {
        synchronized (STORE) { page.active = false; }
        cancelMatching(page.owner, null);
    }

    /** 停用会话与写入校验共享锁，返回后旧代次不能再次落盘。 */
    public static void invalidateSession(WebSessionStrategy.Session session) {
        synchronized (STORE) { session.deactivate(); }
        cancelMatching(null, session.namespace);
    }

    /** 关闭模块或降级时取消全部任务，不删除登录数据。 */
    public static void cancelAll() { cancelMatching(null, null); }

    /** 标记取消后在锁外断开连接，防止持有写入锁进行网络操作。 */
    private static void cancelMatching(String owner, String namespace) {
        List<Task> selected = new ArrayList<>();
        synchronized (STORE) {
            for (Task task : TASKS.values()) {
                if ((owner == null || owner.equals(task.snapshot.page.owner))
                        && (namespace == null || namespace.equals(task.snapshot.session.namespace))) {
                    task.cancelled = true;
                    selected.add(task);
                }
            }
        }
        for (Task task : selected) {
            HttpURLConnection connection = task.connection;
            if (connection != null) connection.disconnect();
            if (EXECUTOR.remove(task)) task.finish();
        }
    }

    /** 后台等待会话在途 Cookie 读取和网络任务退出，主线程不得调用。 */
    public static void awaitSessionIdle(String namespace) throws Exception {
        List<Task> selected = new ArrayList<>();
        synchronized (STORE) {
            for (Task task : TASKS.values()) {
                if (namespace.equals(task.snapshot.session.namespace)) selected.add(task);
            }
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        for (Task task : selected) {
            if (!task.done.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException("观察任务尚未退出，保持会话停用");
            }
        }
    }

    /** 在后台移除应用自己的资源分区，不触碰 WebView 内核目录。 */
    public static void clearSessionMetadata(Context context, String namespace) throws Exception {
        synchronized (STORE) {
            deleteTree(new File(context.getNoBackupFilesDir(), "web_resources/" + safe(namespace)));
            RECENT.clear();
            MEMORY.clear();
        }
    }

    /** 启动时对所有资源分区执行容量和过期维护，仅在后台调用。 */
    public static void maintain(Context context) {
        synchronized (STORE) {
            File[] namespaces = new File(context.getNoBackupFilesDir(), "web_resources").listFiles();
            if (namespaces != null) for (File namespace : namespaces) prune(namespace);
        }
    }

    /** 执行实际请求头快照、旧验证器读取、探测和受控写入。 */
    private static void observe(Task task) throws Exception {
        task.check();
        String cookie = task.cookie(new URL(task.snapshot.url));
        Map<String, String> sent = WebResourceProbe.requestHeaders(task.snapshot.headers, cookie);
        String variant = digest(WebResourceProbe.variantText(sent));
        String key = resourceKey(task.snapshot, variant);
        JSONObject previous;
        synchronized (STORE) {
            task.check();
            trimRecent();
            Long recent = RECENT.get(key);
            if (recent != null && System.currentTimeMillis() - recent < TimeUnit.MINUTES.toMillis(5)) return;
            previous = readMetadata(task, key);
        }
        task.expectedFinalHash = previous == null ? null : previous.optString("finalUrlHash");
        WebResourceProbe.Response response = WebResourceProbe.probe(task.snapshot.url, sent,
                conditions(previous), task);
        synchronized (STORE) {
            task.check();
            saveResult(task, key, variant, sent, previous, response);
            RECENT.put(key, System.currentTimeMillis());
            trimRecent();
        }
    }

    /** 仅对同一真实请求变体和可重现 Vary 的旧记录使用条件验证。 */
    private static Map<String, String> conditions(JSONObject old) {
        Map<String, String> headers = new HashMap<>();
        if (old == null || !old.optBoolean("conditionalEligible")) return headers;
        String etag = old.optString("etag");
        String modified = old.optString("lastModifiedRaw");
        if (!etag.isEmpty()) headers.put("If-None-Match", etag);
        else if (WebResourceProbe.httpDate(modified) >= 0) headers.put("If-Modified-Since", modified);
        return headers;
    }

    /** 304 合并旧记录；错误 MIME 和不可关联的 304 不保存为有效资源。 */
    private static void saveResult(Task task, String key, String variant, Map<String, String> sent,
                                   JSONObject previous, WebResourceProbe.Response response) throws Exception {
        if (response.status == 304 && (previous == null
                || !digest(response.finalUrl).equals(previous.optString("finalUrlHash")))) return;
        if (response.status != 304 && (response.status < 200 || response.status >= 300)) return;
        String type = response.get("content-type");
        if (type == null && response.status == 304) type = previous.optString("contentType", null);
        if (WebResourceProbe.isNonStaticContentType(type)) {
            metadataFile(task, key).delete();
            MEMORY.remove(key);
            return;
        }
        JSONObject record = response.status == 304 ? new JSONObject(previous.toString()) : new JSONObject();
        writeIdentity(record, task.snapshot, key, variant, response);
        copyResponseHeaders(record, response);
        writeValidation(record, sent);
        String cacheControl = record.optString("cacheControl").toLowerCase(java.util.Locale.US);
        if (cacheControl.contains("no-store")) {
            metadataFile(task, key).delete();
            MEMORY.put(key, record);
            while (MEMORY.size() > 128) MEMORY.remove(MEMORY.keySet().iterator().next());
        } else {
            MEMORY.remove(key);
            writeMetadata(task, key, record);
            prune(namespaceDirectory(task));
        }
    }

    /** 保存哈希身份及观察上下文，不把辅助响应冒充原始内核响应。 */
    private static void writeIdentity(JSONObject record, Snapshot snapshot, String key,
                                      String variant, WebResourceProbe.Response response) throws Exception {
        record.put("schemaVersion", 2).put("namespace", snapshot.session.namespace)
                .put("generation", snapshot.session.generation).put("resourceKey", key)
                .put("requestedUrlHash", digest(snapshot.url)).put("finalUrlHash", digest(response.finalUrl))
                .put("requestVariantHash", variant).put("status", response.status)
                .put("probeMethod", response.method).put("probeContext", "first_party")
                .put("checkedAt", System.currentTimeMillis()).put("validatedAt", System.currentTimeMillis());
    }

    /** 白名单保存声明值；304 缺省字段保留旧值。 */
    private static void copyResponseHeaders(JSONObject record, WebResourceProbe.Response response) throws Exception {
        String[][] fields = {{"etag", "etag"}, {"last-modified", "lastModifiedRaw"},
                {"content-type", "contentType"}, {"content-encoding", "contentEncoding"},
                {"content-digest", "contentDigest"}, {"repr-digest", "reprDigest"},
                {"digest", "digest"}, {"content-md5", "contentMd5"}, {"cache-control", "cacheControl"},
                {"expires", "expires"}, {"date", "date"}, {"age", "age"}, {"vary", "vary"}};
        for (String[] field : fields) {
            String value = response.get(field[0]);
            if (value != null) record.put(field[1], value);
        }
        if (response.status != 304) {
            record.put("contentLength", number(response.get("content-length")))
                    .put("contentRangeTotal", rangeTotal(response.get("content-range")))
                    .put("partialResponse", response.status == 206);
        }
    }

    /** 区分弱验证器、修改时间和未经验证的服务器摘要。 */
    private static void writeValidation(JSONObject record, Map<String, String> sent) throws Exception {
        String etag = record.optString("etag");
        long modified = WebResourceProbe.httpDate(record.optString("lastModifiedRaw"));
        boolean weak = etag.startsWith("W/");
        String strength = !etag.isEmpty() ? (weak ? "weak_etag" : "strong_etag")
                : modified >= 0 ? "last_modified" : record.has("contentDigest")
                || record.has("reprDigest") || record.has("digest") || record.has("contentMd5")
                ? "server_digest_unverified" : "unknown";
        record.put("etagWeak", weak).put("lastModifiedEpoch", modified)
                .put("validationStrength", strength).put("conditionalEligible",
                        WebResourceProbe.reproducibleVary(record.optString("vary", null), sent));
    }

    /** 损坏、过期、旧版本或身份不符的记录不得作为条件验证依据。 */
    private static JSONObject readMetadata(Task task, String key) {
        AtomicFile file = metadataFile(task, key);
        try (FileInputStream input = file.openRead();
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 65536) return null;
                output.write(buffer, 0, count);
            }
            JSONObject record = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            if (record.optInt("schemaVersion") != 2 || !key.equals(record.optString("resourceKey"))
                    || System.currentTimeMillis() - record.optLong("checkedAt") > TimeUnit.DAYS.toMillis(30)) return null;
            return record;
        } catch (Exception ignored) { return null; }
    }

    /** 原子写入失败保留有效旧文件，不影响网页加载。 */
    private static void writeMetadata(Task task, String key, JSONObject record) throws Exception {
        AtomicFile file = metadataFile(task, key);
        File parent = file.getBaseFile().getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IllegalStateException("无法创建资源目录");
        FileOutputStream output = file.startWrite();
        try {
            output.write(record.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (Exception error) { file.failWrite(output); throw error; }
    }

    /** 资源键绑定真实请求变体和会话代次，完整 query 参与哈希。 */
    private static String resourceKey(Snapshot snapshot, String variant) {
        return digest(snapshot.session.namespace + "|" + snapshot.session.generation + "|"
                + snapshot.url + "|" + variant);
    }

    /** 文件路径只使用内部名称与 SHA-256 键。 */
    private static AtomicFile metadataFile(Task task, String key) {
        return new AtomicFile(new File(namespaceDirectory(task), task.snapshot.session.generation + "/" + key + ".json"));
    }

    /** 获取会话专属应用分区。 */
    private static File namespaceDirectory(Task task) {
        return new File(task.context.getNoBackupFilesDir(), "web_resources/" + safe(task.snapshot.session.namespace));
    }

    /** 拒绝任意外部路径，避免目录越界。 */
    private static String safe(String namespace) {
        if (!namespace.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("无效资源命名空间");
        return namespace;
    }

    /** 过期去重键立即淘汰，容量固定不随长期浏览无限增长。 */
    private static void trimRecent() {
        long expiry = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5);
        Iterator<Map.Entry<String, Long>> iterator = RECENT.entrySet().iterator();
        while (iterator.hasNext()) if (iterator.next().getValue() < expiry) iterator.remove();
        while (RECENT.size() > 2048) RECENT.remove(RECENT.keySet().iterator().next());
    }

    /** 后台按会话限制最多一千条，并删除三十天未使用的记录。 */
    private static void prune(File namespace) {
        List<File> records = new ArrayList<>();
        collectRecords(namespace, records);
        Collections.sort(records, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        long expiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        for (int i = 0; i < records.size(); i++) {
            File record = records.get(i);
            if (i >= 1000 || record.lastModified() < expiry) new AtomicFile(record).delete();
        }
    }

    /** 仅遍历模块自己的资源目录，不读取内核网站存储。 */
    private static void collectRecords(File directory, List<File> records) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) collectRecords(file, records);
            else if (file.getName().endsWith(".json")) records.add(file);
        }
    }

    /** 会话重置要求应用分区删除完成，删除失败明确上报。 */
    private static void deleteTree(File file) throws Exception {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (file.exists() && !file.delete()) throw new IllegalStateException("无法删除资源分区");
    }

    /** 对内存中的敏感表示上下文计算 SHA-256，不降级到碰撞概率更大的哈希。 */
    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format(java.util.Locale.US, "%02x", item & 0xff));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    /** 非法或不存在的长度使用 -1，不宣称它是唯一内容标识。 */
    private static long number(String value) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return -1; }
    }

    /** 206 中正文长度与完整资源大小分开保存。 */
    private static long rangeTotal(String value) {
        return value == null || value.lastIndexOf('/') < 0 ? -1 : number(value.substring(value.lastIndexOf('/') + 1).trim());
    }

    /** 页面失效标记保存在句柄内，关闭后迟到拦截不能再次投递。 */
    public static final class Page {
        final String owner = java.util.UUID.randomUUID().toString();
        volatile boolean active = true;
        /** 创建一个窗口独有的任务归属。 */
        public Page() { }
    }

    /** 页面来源和请求头为不可变快照；会话停用状态由协调器更新。 */
    public static final class Snapshot {
        final String url;
        final Page page;
        final WebSessionStrategy.Session session;
        final android.webkit.CookieManager cookieManager;
        final String authorization;
        final boolean sameOrigin;
        final Map<String, String> headers;
        /** 创建后台安全快照，不在回调线程读取 Cookie。 */
        public Snapshot(String url, Page page, WebSessionStrategy.Session session,
                        android.webkit.CookieManager cookieManager, String authorization,
                        boolean sameOrigin, Map<String, String> headers) {
            this.url = url; this.page = page; this.session = session; this.cookieManager = cookieManager;
            this.authorization = authorization; this.sameOrigin = sameOrigin;
            this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
        }
    }

    /** 每个任务拥有独立连接和完成屏障，取消不等同于网络已经退出。 */
    private static final class Task implements Runnable, WebResourceProbe.Control {
        final String id;
        final Context context;
        final Snapshot snapshot;
        final CountDownLatch done = new CountDownLatch(1);
        volatile boolean cancelled;
        String expectedFinalHash;
        volatile HttpURLConnection connection;
        /** 保存应用上下文，避免后台持有页面或 Activity。 */
        Task(String id, Context context, Snapshot snapshot) {
            this.id = id; this.context = context; this.snapshot = snapshot;
        }
        /** 无论失败还是取消，最终释放跟踪条目和完成屏障。 */
        @Override public void run() {
            try { observe(this); }
            catch (Exception ignored) { /* 观察失败不改变原始网页请求。 */ }
            finally { finish(); }
        }
        /** 只移除当前任务，避免旧任务误移除同键的新任务。 */
        void finish() {
            synchronized (STORE) { if (TASKS.get(id) == this) TASKS.remove(id); }
            done.countDown();
        }
        /** 每个网络阶段和落盘前检查同一个会话句柄。 */
        @Override public void check() throws Exception {
            if (cancelled || !snapshot.page.active || !snapshot.session.isActive() || !isProbeEnabled()) {
                throw new java.io.InterruptedIOException("观察任务已经失效");
            }
        }
        /** 仅匹配旧记录最终地址的 304 可以合并，其他情况重新无条件探测。 */
        @Override public boolean canUse304(String url) {
            return expectedFinalHash != null && expectedFinalHash.equals(digest(url));
        }
        /** 固定使用该会话 CookieManager，校验完成后才读取。 */
        @Override public String cookie(URL url) throws Exception {
            check();
            String cookie = snapshot.cookieManager == null ? null : snapshot.cookieManager.getCookie(url.toString());
            check();
            return cookie == null ? "" : cookie;
        }
        /** 注册连接后再次检查取消，关闭注册与取消之间的竞态窗口。 */
        @Override public void connection(HttpURLConnection value) throws Exception {
            connection = value;
            if (value != null) check();
        }
    }
}