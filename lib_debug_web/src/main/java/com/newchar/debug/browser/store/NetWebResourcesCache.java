package com.newchar.debug.browser.store;

import android.net.Uri;
import android.webkit.WebResourceRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * @author newChar
 * date 2024/3/21
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 *
 * 基于 HttpURLConnection 的网络资源获取，替代原 okhttp 实现。
 * 行为对齐：同步请求、透传请求头、返回状态码/响应头/响应流；失败返回 null。
 */
final class NetWebResourcesCache extends WebResourcesCache {

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 8000;

    private WebResourceRequest mRequest;

    public NetWebResourcesCache(WebResourcesCache resources) {
        super(resources);
    }

    @Override
    public WebResources get(Uri requestResourceUri) {
        WebResources resources = null;
        HttpURLConnection connection = null;
        try {
            connection = openConnection(mRequest);
            if (connection == null) {
                return null;
            }
            int stateCode = connection.getResponseCode();
            resources = new WebResources();
            resources.setStateCode(stateCode);
            // 2xx 读正常流，其余读错误流（与 okhttp 的 body().byteStream() 语义对齐）
            InputStream inputStream = stateCode >= 200 && stateCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            resources.setInputSteam(inputStream);
            collectHeaders(connection, resources);
        } catch (IOException ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return resources;
    }

    public void setRequest(WebResourceRequest request) {
        mRequest = request;
    }

    /**
     * 建立连接并透传请求头。失败返回 null。
     */
    private HttpURLConnection openConnection(WebResourceRequest request) {
        try {
            if (request == null || request.getUrl() == null) {
                return null;
            }
            URL url = new URL(request.getUrl().toString());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            // 请求方法（WebResourceRequest.getMethod() 自 API 21 可用）
            String method = request.getMethod();
            if (method != null && !method.isEmpty()) {
                connection.setRequestMethod(method);
            }
            // 透传请求头
            for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            return connection;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 收集响应头写入 WebResources。
     */
    private void collectHeaders(HttpURLConnection connection, WebResources resources) {
        int index = 0;
        while (true) {
            String key = connection.getHeaderFieldKey(index);
            String value = connection.getHeaderField(index);
            if (key == null && value == null) {
                break;
            }
            if (key != null) {
                resources.addHeader(key, value);
            }
            index++;
        }
    }

}
