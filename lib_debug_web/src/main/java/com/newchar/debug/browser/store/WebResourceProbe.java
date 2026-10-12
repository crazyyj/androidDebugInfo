package com.newchar.debug.browser.store;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

/** 有总预算、受限重定向和可靠连接释放的辅助 HTTP 探测。 */
final class WebResourceProbe {
    /** 任务负责检查会话状态、提供同源 Cookie 并跟踪当前连接。 */
    interface Control {
        /** 在每次请求及读取前检查取消和全局状态。 */
        void check() throws Exception;
        /** 判断 304 最终地址是否仍匹配旧记录。 */
        default boolean canUse304(String url) { return true; }
        /** 从固定会话获取目标地址 Cookie，仅在后台调用。 */
        String cookie(URL url) throws Exception;
        /** 将连接交给取消路径，null 表示连接已释放。 */
        void connection(HttpURLConnection connection) throws Exception;
    }

    /** 先 HEAD，必要时最多读取一个字节；整个流程共享八秒预算。 */
    static Response probe(String address, Map<String, String> headers,
                          Map<String, String> conditional, Control control) throws Exception {
        if (new URL(address).getUserInfo() != null) throw new InterruptedIOException("跳过带 URL 凭证的资源");
        long deadline = System.nanoTime() + 8_000_000_000L;
        Response result = request(new URL(address), headers, conditional, "HEAD", control, deadline);
        if (result.status == 304 && (conditional.isEmpty() || !control.canUse304(result.finalUrl))) {
            result = request(new URL(address), headers, Collections.emptyMap(), "HEAD", control, deadline);
        }
        if (needsRange(result)) {
            result = request(new URL(address), headers, Collections.emptyMap(), "GET", control, deadline);
        }
        return result;
    }

    /** 在限定源与预算内手动处理跳转，绝不把凭证发送到其他源。 */
    private static Response request(URL origin, Map<String, String> headers,
                                    Map<String, String> conditional, String method,
                                    Control control, long deadline) throws Exception {
        URL current = origin;
        for (int hop = 0; hop <= 3; hop++) {
            control.check();
            if (!(headers.containsKey("cookie") ? headers.get("cookie") : "").equals(control.cookie(current))) {
                throw new InterruptedIOException("Cookie 已变化，丢弃不一致探测");
            }
            Response response = exchange(current, headers, conditional, method, control, deadline);
            if (!isRedirect(response.status)) return response;
            String location = response.get("location");
            if (location == null || hop == 3) throw new InterruptedIOException("跳转次数或目标无效");
            URL target = new URL(current, location);
            if (!sameOrigin(origin, target) || target.getUserInfo() != null) {
                throw new InterruptedIOException("辅助探测不跟随跨源或带认证信息的跳转");
            }
            current = target;
        }
        throw new InterruptedIOException("跳转次数超限");
    }

    /** 每一条异常、取消和成功路径均在 finally 中断开连接。 */
    private static Response exchange(URL url, Map<String, String> headers,
                                     Map<String, String> conditional, String method,
                                     Control control, long deadline) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            control.connection(connection);
            configure(connection, headers, conditional, method, deadline);
            control.check();
            int status = connection.getResponseCode();
            control.check();
            connection.setReadTimeout(remaining(deadline));
            if ("GET".equals(method)) readOneByte(connection, status);
            remaining(deadline);
            return new Response(status, "GET".equals(method) ? "GET_RANGE" : "HEAD",
                    url.toString(), connection.getHeaderFields());
        } finally {
            connection.disconnect();
            control.connection(null);
        }
    }

    /** 固定实际发送的表示请求头，条件验证不依赖进程默认缓存。 */
    private static void configure(HttpURLConnection connection, Map<String, String> headers,
                                  Map<String, String> conditional, String method,
                                  long deadline) throws Exception {
        connection.setConnectTimeout(Math.min(3000, remaining(deadline)));
        connection.setReadTimeout(Math.min(3000, remaining(deadline)));
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (!header.getValue().isEmpty()) connection.setRequestProperty(header.getKey(), header.getValue());
        }
        for (Map.Entry<String, String> header : conditional.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        if ("GET".equals(method)) connection.setRequestProperty("Range", "bytes=0-0");
    }

    /** 使用单调时间控制总预算，避免重定向和 Range 重置超时。 */
    private static int remaining(long deadline) throws InterruptedIOException {
        long millis = (deadline - System.nanoTime()) / 1_000_000L;
        if (millis <= 0 || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("探测已取消或超过总预算");
        }
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }

    /** Range 被忽略时也只读取一个字节并立即关闭流。 */
    private static void readOneByte(HttpURLConnection connection, int status) throws Exception {
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (input != null) {
            try (InputStream limited = input) { limited.read(); }
        }
    }

    /** 只有方法不支持或成功但没有验证器时才追加受限 GET。 */
    static boolean needsRange(Response response) {
        return response.status == 405 || response.status == 501
                || (response.status >= 200 && response.status < 300
                && response.get("etag") == null && response.get("last-modified") == null);
    }

    /** 识别应手动处理的 HTTP 跳转状态。 */
    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    /** 默认端口与显式端口视为同源，协议变化视为跨源。 */
    static boolean sameOrigin(URL first, URL second) {
        int firstPort = first.getPort() < 0 ? first.getDefaultPort() : first.getPort();
        int secondPort = second.getPort() < 0 ? second.getDefaultPort() : second.getPort();
        return first.getProtocol().equalsIgnoreCase(second.getProtocol())
                && first.getHost().equalsIgnoreCase(second.getHost()) && firstPort == secondPort;
    }

    /** 明确拒绝 HTML、JSON 和未知动态 MIME，只接受候选静态响应类型。 */
    static boolean isNonStaticContentType(String value) {
        if (value == null) return true;
        String type = value.split(";", 2)[0].trim().toLowerCase(Locale.US);
        return !(type.equals("text/css") || type.equals("application/javascript")
                || type.equals("text/javascript") || type.equals("application/x-javascript")
                || type.startsWith("image/") || type.startsWith("font/")
                || type.equals("application/font-woff") || type.equals("application/vnd.ms-fontobject")
                || type.equals("application/octet-stream"));
    }

    /** 只允许能够固定重现的表示请求头，不透传 Referer 或其他凭证。 */
    static Map<String, String> requestHeaders(Map<String, String> snapshot, String cookie) {
        Map<String, String> result = new TreeMap<>();
        for (String name : new String[]{"user-agent", "accept", "accept-language", "accept-encoding"}) {
            String value = header(snapshot, name);
            result.put(name, value == null ? "" : value);
        }
        if (result.get("accept").isEmpty()) result.put("accept", "*/*");
        if (result.get("accept-encoding").isEmpty()) result.put("accept-encoding", "identity");
        result.put("cookie", cookie == null ? "" : cookie);
        return Collections.unmodifiableMap(result);
    }

    /** 确定性序列化实际请求变体，Cookie 原值只在内存参与 SHA-256。 */
    static String variantText(Map<String, String> headers) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(headers).entrySet()) {
            text.append(entry.getKey().length()).append(':').append(entry.getKey())
                    .append(entry.getValue().length()).append(':').append(entry.getValue());
        }
        return text.toString();
    }

    /** 未知 Vary 或 Vary:* 只能观察，不能作条件验证。 */
    static boolean reproducibleVary(String vary, Map<String, String> headers) {
        if (vary == null || vary.trim().isEmpty()) return true;
        for (String name : vary.split(",")) {
            if (!headers.containsKey(name.trim().toLowerCase(Locale.US))) return false;
        }
        return true;
    }

    /** 大小写无关地读取快照字段。 */
    static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    /** 验证标准 HTTP 日期，无效时间不得参与条件请求。 */
    static long httpDate(String value) {
        if (value == null) return -1;
        try {
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            format.setLenient(false);
            java.text.ParsePosition position = new java.text.ParsePosition(0);
            java.util.Date date = format.parse(value, position);
            return date == null || position.getIndex() != value.length() ? -1 : date.getTime();
        } catch (RuntimeException ignored) { return -1; }
    }

    /** 兼容 API 23 的多行字段合并，不使用 API 26 的 String.join。 */
    private static String joinFields(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    /** 已脱离网络连接的响应快照，不包含 Set-Cookie。 */
    static final class Response {
        final int status;
        final String method;
        final String finalUrl;
        final Map<String, String> headers = new HashMap<>();
        /** 复制可保存的元数据头，不保存 Location 或响应凭证。 */
        Response(int status, String method, String url, Map<String, List<String>> raw) {
            this.status = status; this.method = method; this.finalUrl = url;
            for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    String name = entry.getKey().toLowerCase(Locale.US);
                    if (!name.equals("set-cookie") && !name.equals("set-cookie2")) {
                        headers.put(name, name.equals("vary") || name.equals("cache-control")
                                ? joinFields(entry.getValue()) : entry.getValue().get(0));
                    }
                }
            }
        }
        /** 读取响应头。 */
        String get(String name) { return headers.get(name.toLowerCase(Locale.US)); }
    }
}