package com.newchar.debug.browser;

import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.MessageQueue;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.newchar.debug.browser.store.WebResources;
import com.newchar.debug.browser.store.WebResourcesStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author newChar
 * date 2024/3/3
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public class SimpleWebClient extends WebViewClient {

    private final Interceptor interceptor;

    public SimpleWebClient() {
        interceptor = new Interceptor();
    }

    // TODO 1. 跨域处理;
    // TODO 2. encoding 处理;

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
//        super.onReceivedSslError(view, handler, error);
        handler.proceed();
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
//        super.onReceivedError(view, request, error);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String errorMsg = "{}";
            try {
                errorMsg = new JSONObject().put("errorMsg ", error.getDescription()).put("errorCode", error.getErrorCode()).toString();
            } catch (JSONException e) {
            }
            Log.e("WebView", "onReceivedError errorUrl =  " + request.getUrl() + " 错误信息= " + errorMsg);
        }

    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        try {
            WebResourceResponse response = interceptor.intercept(request);
            if (response != null) {
                return response;
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
        return super.shouldInterceptRequest(view, request);
    }

    static class Interceptor {

        private final List<ResRequestInterceptor> mInterceptor = new ArrayList<>();

        public Interceptor() {
            resister(new JSRequestInterceptor());
            resister(new ImageRequestInterceptor());
            resister(new CSSRequestInterceptor());
            resister(new WoffRequestInterceptor());
        }

        public void resister(ResRequestInterceptor interceptor) {
            mInterceptor.add(interceptor);
        }

        public WebResourceResponse intercept(WebResourceRequest request) throws FileNotFoundException {
            for (ResRequestInterceptor interceptor : mInterceptor) {
                if (interceptor.shouldIntercept(request)) {
                    return interceptor.handlerRequest(request);
                }
            }
            return null;
        }

    }

    final static class ImageRequestInterceptor implements ResRequestInterceptor {

        private static final String[] IF = new String[]{".png", ".webp", ".ico", ".jpg", ".jpeg"};

        public ImageRequestInterceptor() {

        }

        @Override
        public boolean shouldIntercept(WebResourceRequest request) {
            if ("GET".equalsIgnoreCase(request.getMethod())) {
                return isImagePathUrl(request.getUrl()) && isImageMimeType(request.getRequestHeaders());
            }
            return false;
        }

        private boolean isImageMimeType(Map<String, String> requestHeaders) {
            String requestMimeType = requestHeaders.get("Accept");
            if (requestMimeType != null) {
                String[] subMimeType = requestMimeType.split(HEAD_SPLIT_);
                int imageTypeCount = 0;
                int otherTypeCount = 0;
                for (String mimeType : subMimeType) {
                    if (mimeType.startsWith("image/")) {
                        imageTypeCount++;
                    } else {
                        if (++otherTypeCount > 1) {
                            return false;
                        }
                    }
                }
                return imageTypeCount > 0;
            }
            return false;
        }

        private boolean isImagePathUrl(Uri httpUri) {
            String lastPathSegment = httpUri.getLastPathSegment();
            if (lastPathSegment != null) {
                for (String s : IF) {
                    if (lastPathSegment.endsWith(s)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public WebResourceResponse handlerRequest(WebResourceRequest request) throws FileNotFoundException {
            WebResources imageFileResources = WebResourcesStore.getResources(request);
            if (imageFileResources != null) {
                Map<String, String> responseHeader = imageFileResources.getResponseHeader();
                String mimeType = responseHeader.get("Content-Type");
                if (mimeType != null) {
                    mimeType = mimeType.split(";", 1)[0];
                }
                int stateCode = imageFileResources.getStateCode();
                return new WebResourceResponse(mimeType, buildResponseEncoding(responseHeader, StandardCharsets.UTF_8.name()),
                        stateCode, stateCode > 200 && stateCode < 300 ? "ok" : "request_error", responseHeader, imageFileResources.getInputSteam());
            }
            return null;
        }
    }

    final static class JSRequestInterceptor implements ResRequestInterceptor {

        public JSRequestInterceptor() {

        }

        @Override
        public boolean shouldIntercept(WebResourceRequest request) {
            try {
                if ("GET".equalsIgnoreCase(request.getMethod())) {
                    if (isJsUrl(request.getUrl()) && isJsMimeType(request.getRequestHeaders())) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
            return false;
        }

        private boolean isJsUrl(Uri uri) {
            String lastPathSegment = uri.getLastPathSegment();
            return lastPathSegment != null && lastPathSegment.endsWith(".js");
        }

        private boolean isJsMimeType(Map<String, String> requestHeaders) {
            String requestMimeType = requestHeaders.get("Accept");
            return "*/*".equals(requestMimeType);
        }

        @Override
        public WebResourceResponse handlerRequest(WebResourceRequest request) throws FileNotFoundException {
            WebResources jsFileResources = WebResourcesStore.getResources(request);
            if (jsFileResources != null) {
                Map<String, String> responseHeader = jsFileResources.getResponseHeader();
//                String mimeType = responseHeader.get("Content-Type");
//                if (mimeType != null) {
//                    mimeType = mimeType.split(";", 1)[0];
//                }
                return new WebResourceResponse(responseHeader.get("Content-Type"), buildResponseEncoding(responseHeader, StandardCharsets.UTF_8.name()), jsFileResources.getInputSteam());
            }
            return null;
        }

    }

    final static class CSSRequestInterceptor implements ResRequestInterceptor {

        public static final String HEAD_SPLIT_CSS = "text/css";

        public CSSRequestInterceptor() {
        }

        @Override
        public boolean shouldIntercept(WebResourceRequest request) {
            try {
                if ("GET".equalsIgnoreCase(request.getMethod())) {
                    if (isCSSUrl(request.getUrl()) && isCSSMimeType(request.getRequestHeaders())) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
            return false;
        }

        private boolean isCSSUrl(Uri uri) {
            String lastPathSegment = uri.getLastPathSegment();
            return lastPathSegment != null && lastPathSegment.endsWith(".css");
        }

        private boolean isCSSMimeType(Map<String, String> requestHeaders) {
            String requestMimeType = requestHeaders.get("Accept");
            if (requestMimeType != null) {
                String[] subMimeType = requestMimeType.split(HEAD_SPLIT_);
                int cssTypeCount = 0;
                int otherTypeCount = 0;
                for (String mimeType : subMimeType) {
                    if (HEAD_SPLIT_CSS.equals(mimeType)) {
                        cssTypeCount++;
                    } else {
                        if (++otherTypeCount > 1) {
                            return false;
                        }
                    }
                }
                return cssTypeCount > 0;
            }
            return false;
        }

        @Override
        public WebResourceResponse handlerRequest(WebResourceRequest request) throws FileNotFoundException {
            WebResources cssFileResources = WebResourcesStore.getResources(request);
            if (cssFileResources != null) {
                Map<String, String> responseHeader = cssFileResources.getResponseHeader();
                String mimeType = responseHeader.get("Content-Type");
                if (mimeType != null) {
                    mimeType = mimeType.split(";", 1)[0];
                }
                return new WebResourceResponse(mimeType, buildResponseEncoding(responseHeader, StandardCharsets.UTF_8.name()), cssFileResources.getInputSteam());
            }
            return null;
        }
    }

    final static class WoffRequestInterceptor implements ResRequestInterceptor {

        public WoffRequestInterceptor() {
        }

        @Override
        public boolean shouldIntercept(WebResourceRequest request) {
            try {
                if ("GET".equalsIgnoreCase(request.getMethod())) {
                    if (isFontUrl(request.getUrl()) && isFontMimeType(request.getRequestHeaders())) {
                        Log.e("AA", "ziti文件" + request.getRequestHeaders().get("Accept"));
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
            return false;
        }

        private boolean isFontUrl(Uri uri) {
            String lastPathSegment = uri.getLastPathSegment();
            return lastPathSegment != null && (lastPathSegment.endsWith(".woff") || lastPathSegment.endsWith(".ttf"));
        }

        private boolean isFontMimeType(Map<String, String> requestHeaders) {
            String requestMimeType = requestHeaders.get("Accept");
            if (requestMimeType != null) {
                String[] subMimeType = requestMimeType.split(HEAD_SPLIT_);
                int fontTypeCount = 0;
                int otherTypeCount = 0;
                for (String mimeType : subMimeType) {
                    if ("application/font-woff".equals(mimeType) || "font/ttf".equals(mimeType)) {
                        fontTypeCount++;
                    } else {
                        if (++otherTypeCount > 1) {
                            return false;
                        }
                    }
                }
                return fontTypeCount > 0;
            }
            return false;
        }

        @Override
        public WebResourceResponse handlerRequest(WebResourceRequest request) throws FileNotFoundException {
            WebResources fontFileResources = WebResourcesStore.getResources(request);
            if (fontFileResources != null) {
                Map<String, String> responseHeader = fontFileResources.getResponseHeader();
                String mimeType = responseHeader.get("Content-Type");
                if (mimeType != null) {
                    mimeType = mimeType.split(";", 1)[0];
                }
                return new WebResourceResponse(mimeType, buildResponseEncoding(responseHeader, StandardCharsets.UTF_8.name()), fontFileResources.getInputSteam());
            }
            return null;
        }
    }

    private static String buildResponseEncoding(Map<String, String> responseHeader, String defaultCharset) {
        String contentEncoding = responseHeader.get("Content-Encoding");
        if (contentEncoding != null) {
            return contentEncoding;
        }
        String contentType = responseHeader.get("Content-Type");
        if (contentType != null && contentType.contains("charset")) {
            String[] subContentType = contentType.split(";");
            for (String type : subContentType) {
                if (type.contains("charset")) {
                    return type;
                }
            }
        }
        return "charset=" + defaultCharset;
    }

    interface ResRequestInterceptor {

        String HEAD_SPLIT_ = ",";

        boolean shouldIntercept(WebResourceRequest request);

        WebResourceResponse handlerRequest(WebResourceRequest request) throws FileNotFoundException;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String scheme = request.getUrl().getScheme();
        if (scheme != null && !scheme.startsWith("http")) {
            super.shouldOverrideUrlLoading(view, request);
            return true;
        }
        view.loadUrl(request.getUrl().toString()/*, request.getRequestHeaders()*/);
        return true;
    }

    long a;

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        a = System.currentTimeMillis();
        Log.e("QAQAQQQ", url + " onPageStarted" + (System.currentTimeMillis() - a));
        WebSettings settings = view.getSettings();
        if (!settings.getBlockNetworkImage()) {
            settings.setBlockNetworkImage(true);
        }
    }

    @Override
    public void onPageCommitVisible(WebView view, String url) {
        super.onPageCommitVisible(view, url);
        Log.e("QAQAQQQ", url + " onPageCommitVisible" + (System.currentTimeMillis() - a));
        WebSettings settings = view.getSettings();
        if (settings.getBlockNetworkImage()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                view.getWebViewLooper().getQueue().addIdleHandler(() -> {
                    if (settings.getBlockNetworkImage()) {
                        settings.setBlockNetworkImage(false);
                    }
                    return false;
                });
            } else {
                view.postDelayed(() -> settings.setBlockNetworkImage(false), 50);
            }
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        Log.e("QAQAQQQ", url + " onPageFinished" + (System.currentTimeMillis() - a));
    }

}
