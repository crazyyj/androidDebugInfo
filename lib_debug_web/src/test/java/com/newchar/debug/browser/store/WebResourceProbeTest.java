package com.newchar.debug.browser.store;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** 使用真实本地 HTTP 服务验证协议边界，不依赖 WebView 内核。 */
public class WebResourceProbeTest {
    private LocalServer server;
    private String origin;
    private final Map<String, String> headers = WebResourceProbe.requestHeaders(Collections.emptyMap(), "account=A");
    private HttpURLConnection active;

    /** 每个用例拥有独立本地端口。 */
    @Before public void setUp() throws Exception {
        server = new LocalServer();
        server.start();
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 停止本地服务，避免测试遗留线程。 */
    @After public void tearDown() { server.stop(0); }

    /** HTML 和 JSON 不得作为 JS/CSS 资源保存。 */
    @Test public void rejectsDynamicMime() {
        assertTrue(WebResourceProbe.isNonStaticContentType("text/html; charset=utf-8"));
        assertTrue(WebResourceProbe.isNonStaticContentType("application/json"));
        assertTrue(WebResourceProbe.isNonStaticContentType(null));
        assertFalse(WebResourceProbe.isNonStaticContentType("application/javascript"));
        assertFalse(WebResourceProbe.isNonStaticContentType("text/css"));
    }

    /** 字段顺序和大小写不影响变体，UA 和 Cookie 变化必须影响变体。 */
    @Test public void canonicalVariantIncludesCredentialsAndUa() {
        Map<String, String> first = new HashMap<>();
        first.put("User-Agent", "agent-a"); first.put("Accept", "text/css");
        Map<String, String> second = new HashMap<>();
        second.put("accept", "text/css"); second.put("user-agent", "agent-a");
        String expected = WebResourceProbe.variantText(WebResourceProbe.requestHeaders(first, "A"));
        assertEquals(expected, WebResourceProbe.variantText(WebResourceProbe.requestHeaders(second, "A")));
        assertNotEquals(expected, WebResourceProbe.variantText(WebResourceProbe.requestHeaders(second, "B")));
        second.put("user-agent", "agent-b");
        assertNotEquals(expected, WebResourceProbe.variantText(WebResourceProbe.requestHeaders(second, "A")));
    }

    /** 未知 Vary 不能参与资源未变判断。 */
    @Test public void requiresReproducibleVary() {
        assertTrue(WebResourceProbe.reproducibleVary("Accept, Cookie", headers));
        assertFalse(WebResourceProbe.reproducibleVary("*", headers));
        assertFalse(WebResourceProbe.reproducibleVary("X-Account", headers));
    }

    /** 条件 HEAD 返回 304 时不追加 Range，不读取正文。 */
    @Test public void supportsConditionalHead() throws Exception {
        server.createContext("/file.js", exchange -> {
            assertEquals("HEAD", exchange.getRequestMethod());
            assertEquals("account=A", exchange.getRequestHeaders().getFirst("Cookie"));
            assertEquals("\"v1\"", exchange.getRequestHeaders().getFirst("If-None-Match"));
            exchange.getResponseHeaders().add("ETag", "\"v1\"");
            exchange.sendResponseHeaders(304, -1); exchange.close();
        });
        WebResourceProbe.Response response = WebResourceProbe.probe(origin + "/file.js", headers,
                Collections.singletonMap("If-None-Match", "\"v1\""), control());
        assertEquals(304, response.status);
        assertNull(active);
    }

    /** HEAD 不支持时请求 bytes=0-0，区分 206 和完整长度。 */
    @Test public void fallsBackToBoundedRange() throws Exception {
        AtomicInteger count = new AtomicInteger();
        server.createContext("/file.css", exchange -> {
            count.incrementAndGet();
            if ("HEAD".equals(exchange.getRequestMethod())) exchange.sendResponseHeaders(405, -1);
            else {
                assertEquals("bytes=0-0", exchange.getRequestHeaders().getFirst("Range"));
                exchange.getResponseHeaders().add("Content-Type", "text/css");
                exchange.getResponseHeaders().add("Content-Range", "bytes 0-0/1000000");
                exchange.sendResponseHeaders(206, 1);
                exchange.getResponseBody().write('a');
            }
            exchange.close();
        });
        WebResourceProbe.Response response = WebResourceProbe.probe(origin + "/file.css", headers,
                Collections.emptyMap(), control());
        assertEquals(206, response.status); assertEquals("GET_RANGE", response.method);
        assertEquals("bytes 0-0/1000000", response.get("content-range"));
        assertEquals(2, count.get()); assertNull(active);
    }

    /** 相同源重定向可以获取最终验证器，响应 Cookie 不进入快照。 */
    @Test public void followsSameOriginRedirect() throws Exception {
        server.createContext("/redirect.js", exchange -> {
            exchange.getResponseHeaders().add("Location", "/file.js");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/file.js", exchange -> {
            exchange.getResponseHeaders().add("ETag", "\"final\"");
            exchange.getResponseHeaders().add("Content-Type", "application/javascript");
            exchange.getResponseHeaders().add("Set-Cookie", "secret=token");
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        WebResourceProbe.Response response = WebResourceProbe.probe(origin + "/redirect.js", headers,
                Collections.emptyMap(), control());
        assertEquals(origin + "/file.js", response.finalUrl);
        assertEquals("\"final\"", response.get("etag")); assertNull(response.get("set-cookie"));
        assertNull(active);
    }

    /** 跨源跳转必须停止，不向第二个端口发送 Cookie。 */
    @Test public void rejectsCrossOriginRedirect() throws Exception {
        server.createContext("/file.js", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:1/private.js");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        try {
            WebResourceProbe.probe(origin + "/file.js", headers, Collections.emptyMap(), control());
            fail("跨源跳转应被拒绝");
        } catch (java.io.InterruptedIOException expected) { assertNull(active); }
    }

    /** 重定向循环受到次数上限限制，结束时释放所有连接。 */
    @Test public void boundsRedirectLoop() throws Exception {
        AtomicInteger count = new AtomicInteger();
        server.createContext("/loop.js", exchange -> {
            count.incrementAndGet(); exchange.getResponseHeaders().add("Location", "/loop.js");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        try {
            WebResourceProbe.probe(origin + "/loop.js", headers, Collections.emptyMap(), control());
            fail("重定向循环应停止");
        } catch (java.io.InterruptedIOException expected) {
            assertEquals(4, count.get()); assertNull(active);
        }
    }

    /** Cookie 变化后不能把新账号响应写入旧变体记录。 */
    @Test public void rejectsChangedCookie() throws Exception {
        WebResourceProbe.Control changed = new WebResourceProbe.Control() {
            /** 允许测试进入 Cookie 一致性检查。 */
            @Override public void check() { }
            /** 模拟排队期间登录态变化。 */
            @Override public String cookie(URL url) { return "account=B"; }
            /** 没有一致凭证时不应打开网络连接。 */
            @Override public void connection(HttpURLConnection connection) { fail("不应创建连接"); }
        };
        try {
            WebResourceProbe.probe(origin + "/file.js", headers, Collections.emptyMap(), changed);
            fail("Cookie 变化应拒绝");
        } catch (java.io.InterruptedIOException expected) { assertNull(active); }
    }

    /** 未匹配的 304 使用无条件 HEAD 重探，不能直接声称资源未变。 */
    @Test public void retriesUnmatched304() throws Exception {
        AtomicInteger count = new AtomicInteger();
        server.createContext("/file.js", exchange -> {
            count.incrementAndGet();
            exchange.getResponseHeaders().add("ETag", "\"new\"");
            exchange.sendResponseHeaders(exchange.getRequestHeaders().containsKey("If-None-Match") ? 304 : 200, -1);
            exchange.close();
        });
        WebResourceProbe.Control base = control();
        WebResourceProbe.Control unmatched = new WebResourceProbe.Control() {
            /** 委托取消检查。 */
            @Override public void check() throws Exception { base.check(); }
            /** 模拟最终地址已不匹配旧记录。 */
            @Override public boolean canUse304(String url) { return false; }
            /** 保持同一账号 Cookie。 */
            @Override public String cookie(URL url) throws Exception { return base.cookie(url); }
            /** 跟踪实际连接。 */
            @Override public void connection(HttpURLConnection value) throws Exception { base.connection(value); }
        };
        assertEquals(200, WebResourceProbe.probe(origin + "/file.js", headers,
                Collections.singletonMap("If-None-Match", "\"old\""), unmatched).status);
        assertEquals(2, count.get()); assertNull(active);
    }

    /** 连接注册后发生取消也必须走 finally 释放。 */
    @Test public void releasesConnectionWhenCancelled() throws Exception {
        WebResourceProbe.Control cancelled = new WebResourceProbe.Control() {
            /** 首次检查通过，连接建立后模拟取消。 */
            @Override public void check() { }
            /** 固定实际请求凭证。 */
            @Override public String cookie(URL url) { return "account=A"; }
            /** 注册时取消，finally 必须解除连接引用。 */
            @Override public void connection(HttpURLConnection value) throws Exception {
                active = value;
                if (value != null) throw new java.io.InterruptedIOException("已取消");
            }
        };
        try {
            WebResourceProbe.probe(origin + "/file.js", headers, Collections.emptyMap(), cancelled);
            fail("取消应退出");
        } catch (java.io.InterruptedIOException expected) { assertNull(active); }
    }

    /** HTTP 日期严格解析，非法值不得生成条件验证头。 */
    @Test public void validatesHttpDateAndDefaultPorts() throws Exception {
        assertEquals(1445412480000L, WebResourceProbe.httpDate("Wed, 21 Oct 2015 07:28:00 GMT"));
        assertEquals(-1, WebResourceProbe.httpDate("Wed, 99 Oct 2015 07:28:00 GMT"));
        assertTrue(WebResourceProbe.sameOrigin(new URL("https://example.com/a"), new URL("https://example.com:443/b")));
        assertFalse(WebResourceProbe.sameOrigin(new URL("https://example.com/a"), new URL("http://example.com/a")));
    }

    /** 固定 Cookie，记录连接引用用于断言所有路径释放完成。 */
    private WebResourceProbe.Control control() {
        return new WebResourceProbe.Control() {
            /** 本地协议测试不模拟停用状态。 */
            @Override public void check() { }
            /** 模拟固定账号 Cookie 管理器。 */
            @Override public String cookie(URL url) { return "account=A"; }
            /** 跟踪当前连接。 */
            @Override public void connection(HttpURLConnection value) { active = value; }
        };
    }

    /** 无外部依赖的测试 HTTP 服务，避免 Android javac 缺少 jdk.httpserver 模块。 */
    private static final class LocalServer {
        private final java.net.ServerSocket socket;
        private final Map<String, Endpoint> endpoints = new java.util.concurrent.ConcurrentHashMap<>();
        private Thread thread;
        private volatile Throwable failure;
        /** 使用系统分配的本地端口。 */
        LocalServer() throws Exception { socket = new java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1")); }
        /** 注册当前测试的响应端点。 */
        void createContext(String path, Endpoint endpoint) { endpoints.put(path, endpoint); }
        /** 返回已绑定端口。 */
        java.net.InetSocketAddress getAddress() { return (java.net.InetSocketAddress) socket.getLocalSocketAddress(); }
        /** 后台接收有限的测试请求。 */
        void start() {
            thread = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (java.net.Socket client = socket.accept()) {
                        Exchange exchange = new Exchange(client);
                        Endpoint endpoint = endpoints.get(exchange.path);
                        if (endpoint == null) { exchange.sendResponseHeaders(404, -1); exchange.close(); }
                        else endpoint.handle(exchange);
                    } catch (java.net.SocketException closed) {
                        if (!socket.isClosed()) failure = closed;
                    } catch (Throwable error) { failure = error; }
                }
            }, "web-probe-test-server");
            thread.setDaemon(true);
            thread.start();
        }
        /** 停止服务并传播服务端断言失败。 */
        void stop(int ignored) {
            try { socket.close(); thread.join(1000); }
            catch (Exception error) { throw new AssertionError(error); }
            if (failure != null) throw new AssertionError(failure);
        }
    }

    /** 测试端点处理器。 */
    private interface Endpoint {
        /** 处理一个请求并关闭连接。 */
        void handle(Exchange exchange) throws Exception;
    }

    /** 测试用 HTTP 字段集合，忽略字段名大小写。 */
    private static final class Headers extends java.util.TreeMap<String, String> {
        /** 使用大小写无关比较。 */
        Headers() { super(String.CASE_INSENSITIVE_ORDER); }
        /** 设置响应字段。 */
        void add(String name, String value) { put(name, value); }
        /** 读取唯一请求字段。 */
        String getFirst(String name) { return get(name); }
    }

    /** 只实现测试所需的 HEAD、Range 和响应头协议。 */
    private static final class Exchange {
        private final java.net.Socket socket;
        private final String method;
        private final String path;
        private final Headers request = new Headers();
        private final Headers response = new Headers();
        /** 解析一条原始 HTTP 请求。 */
        Exchange(java.net.Socket socket) throws Exception {
            this.socket = socket;
            java.io.BufferedReader input = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), "US-ASCII"));
            String[] first = input.readLine().split(" ");
            method = first[0]; path = first[1];
            String line;
            while ((line = input.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                request.add(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        /** 返回请求方法。 */
        String getRequestMethod() { return method; }
        /** 返回请求字段。 */
        Headers getRequestHeaders() { return request; }
        /** 返回待发送响应字段。 */
        Headers getResponseHeaders() { return response; }
        /** 发出响应头，所有连接在单次请求后关闭。 */
        void sendResponseHeaders(int status, long length) throws Exception {
            response.add("Connection", "close");
            if (length >= 0) response.add("Content-Length", String.valueOf(length));
            StringBuilder text = new StringBuilder("HTTP/1.1 " + status + " Test\r\n");
            for (Map.Entry<String, String> field : response.entrySet()) {
                text.append(field.getKey()).append(": ").append(field.getValue()).append("\r\n");
            }
            socket.getOutputStream().write(text.append("\r\n").toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
        }
        /** 返回响应正文输出流。 */
        java.io.OutputStream getResponseBody() throws Exception { return socket.getOutputStream(); }
        /** 完成单次请求并关闭测试连接。 */
        void close() throws Exception { socket.close(); }
    }
}