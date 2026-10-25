package com.newchar.debug.browser.pdf;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;

import com.newchar.debug.browser.PageInterceptor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 线路 B：pdf.js 在 WebView 内渲染，原生只提供文件流与保存通道。
 *
 * <p>页面与文档同源（{@link #ORIGIN}），因此 pdf.js 的 {@code ?file=} 校验可以直接通过；
 * 文档按 {@code /pdf/<token>} 路由，由拦截器按 Range 返回，不经过任何 JavaScript 桥传字节。</p>
 *
 * <p>使用方式：先 {@link #init(Context)}，再把实例注册到
 * {@link com.newchar.debug.browser.WebViewFactory#setPageInterceptor(PageInterceptor)}，
 * 最后在页面就绪后调用 {@link #open(WebView, Uri, Callback)}。</p>
 */
public final class PdfViewer implements PageInterceptor {

    /** 资源域名由 {@link WebViewAssetLoader} 固定，不能替换成自定义 scheme。 */
    public static final String HOST = "appassets.androidplatform.net";

    /** 唯一可信来源。 */
    public static final String ORIGIN = "https://" + HOST;

    private static final String VIEWER = ORIGIN + "/pdfjs/web/viewer.html?file=/pdf/";
    private static final String DOCUMENT_PREFIX = "/pdf/";
    private static final String ASSET_PREFIX = "/pdfjs/";
    /** 等待实例参与布局的轮询间隔与上限。 */
    private static final int LOAD_RETRY_MS = 80;
    private static final int LOAD_MAX_WAIT_MS = 8000;
    private static final PdfViewer INSTANCE = new PdfViewer();

    private final Map<WebView, Set<String>> tokens = Collections.synchronizedMap(new WeakHashMap<>());
    private final ExecutorService copyExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "debug-pdf-copy");
        thread.setDaemon(true);
        return thread;
    });

    private volatile PdfDocuments documents;
    private volatile WebViewAssetLoader assets;
    private volatile PdfRuntime runtime;
    private volatile Listener listener;

    private PdfViewer() {
    }

    /**
     * 获取全局单例。
     *
     * @return 单例
     */
    public static PdfViewer getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化仓库与资源映射，必须在创建 WebView 之前调用一次。
     *
     * @param context 任意上下文，内部取 Application
     */
    public void init(Context context) {
        if (documents != null) {
            return;
        }
        Context app = context.getApplicationContext();
        final PdfRuntime pack = new PdfRuntime(app);
        runtime = pack;
        assets = new WebViewAssetLoader.Builder()
                .addPathHandler(ASSET_PREFIX, new PdfAssets(pack))
                .build();
        documents = new PdfDocuments(app);
        copyExecutor.execute(() -> {
            try {
                pack.ensureReady();
            } catch (Exception ignored) {
                // 预热失败不影响后续，真正加载时会重新展开并上报告错
            }
        });
    }

    /**
     * 是否已完成初始化。
     *
     * @return 可用时为 true
     */
    public boolean isReady() {
        return documents != null && assets != null;
    }

    /**
     * 设置页面回写结果的监听，主线程调用。
     *
     * @param target 监听器，可为 null
     */
    public void setListener(Listener target) {
        listener = target;
    }

    /**
     * 在工作线程复制文档，回到主线程加载页面。
     *
     * @param view     目标实例
     * @param uri      系统选择器返回的地址
     * @param callback 加载结果回调，可为 null
     */
    public void open(final WebView view, final Uri uri, @Nullable final Callback callback) {
        if (!isReady()) {
            if (callback != null) {
                callback.onError(new IllegalStateException("PdfViewer 尚未初始化"));
            }
            return;
        }
        final Context context = view.getContext().getApplicationContext();
        copyExecutor.execute(() -> {
            try {
                String token = documents.register(context, uri);
                view.post(() -> show(view, token, callback));
            } catch (Exception error) {
                view.post(() -> {
                    if (callback != null) {
                        callback.onError(error);
                    }
                });
            }
        });
    }

    /**
     * 直接以字节打开，用于已经在内存中的文档。
     *
     * @param view     目标实例
     * @param content  完整 PDF 字节
     * @param callback 加载结果回调，可为 null
     */
    public void open(final WebView view, final byte[] content, @Nullable final Callback callback) {
        if (!isReady()) {
            if (callback != null) {
                callback.onError(new IllegalStateException("PdfViewer 尚未初始化"));
            }
            return;
        }
        copyExecutor.execute(() -> {
            try {
                String token = documents.register(new ByteArrayInputStream(content));
                view.post(() -> show(view, token, callback));
            } catch (Exception error) {
                view.post(() -> {
                    if (callback != null) {
                        callback.onError(error);
                    }
                });
            }
        });
    }

    /**
     * 下载远端文档后打开，用于网址形式的 PDF。
     *
     * <p>下载在后台串行队列执行，完成前不会把半成品交给 pdf.js；重定向由
     * {@link HttpURLConnection} 自动跟随，非 2xx 直接失败。</p>
     *
     * @param view     目标实例
     * @param url      http 或 https 地址
     * @param callback 加载结果回调，可为 null
     */
    public void open(final WebView view, final String url, @Nullable final Callback callback) {
        if (!isReady()) {
            if (callback != null) {
                callback.onError(new IllegalStateException("PdfViewer 尚未初始化"));
            }
            return;
        }
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("不是有效的 PDF 地址"));
            }
            return;
        }
        copyExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = connect(url);
                final String token;
                try (InputStream input = connection.getInputStream()) {
                    token = documents.register(input);
                }
                view.post(() -> show(view, token, callback));
            } catch (Exception error) {
                view.post(() -> {
                    if (callback != null) {
                        callback.onError(error);
                    }
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * 建立连接并校验状态码，避免把错误页当成 PDF 复制下来。
     */
    private HttpURLConnection connect(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/pdf,*/*");
        connection.setRequestProperty("User-Agent", "DebugView-PdfViewer");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IOException("HTTP " + code + "：" + address);
        }
        return connection;
    }

    /**
     * 加载已注册的文档；令牌会被绑定到该实例，销毁时自动清理。
     *
     * @param view  目标实例
     * @param token 文档令牌
     * @param callback 加载结果回调，可为 null
     */
    public void show(WebView view, String token, @Nullable Callback callback) {
        if (view == null || documents == null || documents.find(token) == null) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("文档令牌无效"));
            }
            return;
        }
        final String url = VIEWER + token;
        final PdfRuntime pack = runtime;
        if (pack == null || pack.isReady()) {
            bindToken(view, token);
            loadWhenVisible(view, url, callback, 0);
            return;
        }
        // 资源包还没展开，先展开再导航，否则页面会拿到 503 白屏
        copyExecutor.execute(() -> {
            try {
                pack.ensureReady();
            } catch (Exception error) {
                view.post(() -> {
                    if (callback != null) {
                        callback.onError(error);
                    }
                });
                return;
            }
            view.post(() -> {
                bindToken(view, token);
                loadWhenVisible(view, url, callback, 0);
            });
        });
    }

    /**
     * 把文档令牌绑定到实例，销毁时统一释放。
     */
    private void bindToken(WebView view, String token) {
        tokens.computeIfAbsent(view, ignored -> Collections.synchronizedSet(new HashSet<>())).add(token);
    }

    /**
     * 等实例真正参与布局后再加载。
     *
     * <p>插件面板会在不可见状态创建 WebView，此时宽度是 0，viewer 会按 0 宽布局，
     * 之后即使显示出来也不会自动重排。这里直接等到有尺寸再发起导航。</p>
     *
     * @param view     目标实例
     * @param url      页面地址
     * @param callback 回调，可为 null
     * @param waited   已等待毫秒数
     */
    private void loadWhenVisible(final WebView view, final String url,
                                 @Nullable final Callback callback, final int waited) {
        if (view.getWidth() > 0 && view.getHeight() > 0) {
            view.loadUrl(url);
            if (callback != null) {
                callback.onOpened();
            }
            return;
        }
        if (waited >= LOAD_MAX_WAIT_MS) {
            view.loadUrl(url);
            if (callback != null) {
                callback.onOpened();
            }
            return;
        }
        view.postDelayed(() -> loadWhenVisible(view, url, callback, waited + LOAD_RETRY_MS),
                LOAD_RETRY_MS);
    }

    @Nullable
    @Override
    public WebResourceResponse intercept(WebResourceRequest request) {
        WebViewAssetLoader loader = assets;
        Uri uri = request == null ? null : request.getUrl();
        if (loader == null || uri == null || !HOST.equalsIgnoreCase(uri.getHost())) {
            return null;
        }
        String path = uri.getPath();
        if (path == null) {
            return null;
        }
        if (path.startsWith(DOCUMENT_PREFIX)) {
            return serveDocument(request, path.substring(DOCUMENT_PREFIX.length()));
        }
        if (path.startsWith(ASSET_PREFIX)) {
            WebResourceResponse response = loader.shouldInterceptRequest(uri);
            return response == null ? PdfAssets.notFound() : response;
        }
        return null;
    }

    @Override
    public void onViewReady(WebView view) {
        if (view == null || documents == null) {
            return;
        }
        PdfBridge.install(view, ORIGIN, documents, listener);
    }

    @Override
    public void onViewReleased(WebView view) {
        release(view);
    }

    /**
     * 释放实例持有的临时文档。
     *
     * @param view 目标实例
     */
    public void release(WebView view) {
        Set<String> owned = tokens.remove(view);
        if (owned == null || documents == null) {
            return;
        }
        synchronized (owned) {
            for (String token : owned) {
                documents.release(token);
            }
            owned.clear();
        }
    }

    /**
     * 按 Range 返回文档，使 pdf.js 可以分块加载大文件。
     */
    private WebResourceResponse serveDocument(WebResourceRequest request, String token) {
        PdfDocuments repository = documents;
        File file = repository == null ? null : repository.find(token);
        if (file == null) {
            return PdfAssets.notFound();
        }
        long total = file.length();
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Ranges", "bytes");
        headers.put("Cache-Control", "no-store");
        headers.put("Content-Type", "application/pdf");

        long start = 0;
        long end = total - 1;
        boolean partial = false;
        String range = header(request, "Range");
        if (range != null && range.startsWith("bytes=")) {
            String spec = range.substring(6).trim();
            int dash = spec.indexOf('-');
            try {
                if (dash == 0) {
                    long suffix = Long.parseLong(spec.substring(1));
                    start = Math.max(0, total - suffix);
                } else if (dash > 0) {
                    start = Long.parseLong(spec.substring(0, dash));
                    String tail = spec.substring(dash + 1).trim();
                    if (!tail.isEmpty()) {
                        end = Long.parseLong(tail);
                    }
                }
                partial = true;
            } catch (NumberFormatException ignored) {
                start = 0;
                end = total - 1;
                partial = false;
            }
        }

        if (total <= 0 || start < 0 || start >= total || end < start) {
            headers.put("Content-Range", "bytes */" + total);
            return new WebResourceResponse("application/pdf", null, 416, "Range Not Satisfiable",
                    headers, new ByteArrayInputStream(new byte[0]));
        }
        if (end >= total) {
            end = total - 1;
        }
        headers.put("Content-Length", String.valueOf(end - start + 1));
        try {
            if (partial) {
                headers.put("Content-Range", "bytes " + start + "-" + end + "/" + total);
                return new WebResourceResponse("application/pdf", null, 206, "Partial Content",
                        headers, new RangeStream(file, start, end - start + 1));
            }
            return new WebResourceResponse("application/pdf", null, 200, "OK",
                    headers, new FileInputStream(file));
        } catch (IOException error) {
            return PdfAssets.notFound();
        }
    }

    private String header(WebResourceRequest request, String name) {
        Map<String, String> headers = request.getRequestHeaders();
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 页面加载与错误回调，均在主线程执行。
     */
    public interface Callback {
        /** 页面已发起加载。 */
        void onOpened();

        /** 复制或加载失败。 */
        default void onError(Exception error) {
        }
    }

    /**
     * 回写结果回调。
     */
    public interface Listener {
        /**
         * 页面保存了一个 PDF。
         *
         * @param view 来源实例
         * @param file 已落盘文件，位于应用内部缓存 out 目录
         */
        void onPdfSaved(WebView view, File file);

        /**
         * 回写失败。
         *
         * @param view  来源实例
         * @param error 失败原因
         */
        default void onPdfError(WebView view, Exception error) {
        }
    }

    /** 从指定偏移读取固定长度的只读流，避免把整段 Range 读进内存。 */
    private static final class RangeStream extends InputStream {

        private final RandomAccessFile file;
        private long remaining;

        RangeStream(File target, long offset, long length) throws IOException {
            file = new RandomAccessFile(target, "r");
            file.seek(offset);
            remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = file.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int limit = (int) Math.min(length, remaining);
            int read = file.read(buffer, offset, limit);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }
}
