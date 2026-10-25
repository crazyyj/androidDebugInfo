package com.newchar.debug.browser.pdf;

import android.webkit.WebResourceResponse;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 提供 pdf.js 的静态资源，内容来自 {@link PdfRuntime} 展开后的目录。
 *
 * <p>必须自己给出 MIME：viewer 用 ES module 加载脚本，
 * 而系统的 {@code MimeTypeMap} 不认识 {@code .mjs}，一旦返回
 * {@code application/octet-stream}，内核会直接拒绝执行。</p>
 */
final class PdfAssets implements WebViewAssetLoader.PathHandler {

    private static final Map<String, String> MIME = new HashMap<>();

    static {
        MIME.put("html", "text/html");
        MIME.put("js", "text/javascript");
        MIME.put("mjs", "text/javascript");
        MIME.put("css", "text/css");
        MIME.put("json", "application/json");
        MIME.put("svg", "image/svg+xml");
        MIME.put("png", "image/png");
        MIME.put("jpg", "image/jpeg");
        MIME.put("jpeg", "image/jpeg");
        MIME.put("gif", "image/gif");
        MIME.put("webp", "image/webp");
        MIME.put("woff", "font/woff");
        MIME.put("woff2", "font/woff2");
        MIME.put("ttf", "font/ttf");
        MIME.put("otf", "font/otf");
        MIME.put("wasm", "application/wasm");
        MIME.put("bcmap", "application/octet-stream");
        MIME.put("pfb", "application/octet-stream");
    }

    private final PdfRuntime runtime;

    /**
     * 创建资源处理器。
     *
     * @param runtime 资源包
     */
    PdfAssets(PdfRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public WebResourceResponse handle(String path) {
        if (path == null || path.isEmpty() || path.contains("..")) {
            return notFound();
        }
        if (!runtime.isReady()) {
            return unavailable();
        }
        File file = new File(runtime.root(), path);
        if (!file.isFile()) {
            return notFound();
        }
        String mime = mimeOf(path);
        try {
            InputStream input = new FileInputStream(file);
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-cache");
            WebResourceResponse response = new WebResourceResponse(mime, encodingOf(mime), input);
            response.setResponseHeaders(headers);
            return response;
        } catch (IOException missing) {
            return notFound();
        }
    }

    /**
     * 构造纯文本 404，避免把缺失资源当成“未命中”继续走网络。
     *
     * @return 404 响应
     */
    static WebResourceResponse notFound() {
        WebResourceResponse response = new WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream("Not Found".getBytes(StandardCharsets.UTF_8)));
        response.setStatusCodeAndReasonPhrase(404, "Not Found");
        return response;
    }

    /**
     * 资源包还没展开时返回 503，和真正的缺失区分开。
     *
     * @return 503 响应
     */
    static WebResourceResponse unavailable() {
        WebResourceResponse response = new WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream("pdf.js 资源尚未就绪".getBytes(StandardCharsets.UTF_8)));
        response.setStatusCodeAndReasonPhrase(503, "Service Unavailable");
        return response;
    }

    private String mimeOf(String path) {
        int dot = path.lastIndexOf('.');
        String extension = dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.US);
        String mime = MIME.get(extension);
        return mime == null ? "application/octet-stream" : mime;
    }

    private String encodingOf(String mime) {
        return mime.startsWith("text/") || mime.contains("json") || mime.contains("javascript")
                ? "utf-8" : null;
    }
}
