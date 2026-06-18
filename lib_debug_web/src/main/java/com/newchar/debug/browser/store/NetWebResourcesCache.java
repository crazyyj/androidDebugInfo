package com.newchar.debug.browser.store;

import android.net.Uri;
import android.webkit.WebResourceRequest;

import java.io.InputStream;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * @author newChar
 * date 2024/3/21
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
final class NetWebResourcesCache extends WebResourcesCache {

    private static OkHttpClient httpClient;
    private WebResourceRequest mRequest;
    public NetWebResourcesCache(WebResourcesCache resources) {
        super(resources);
        if (httpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient().newBuilder();
            builder.addInterceptor(new HttpLoggingInterceptor());
            httpClient = builder.build();
        }
    }

    @Override
    public WebResources get(Uri requestResourceUri) {
        WebResources resources = null;
        try {
            Response response = requestResource(mRequest);
            resources = new WebResources();
            resources.setInputSteam(response.body().byteStream());
            resources.setStateCode(response.code());
            for (int i = 0; i < response.headers().size(); i++) {
                resources.addHeader(response.headers().name(i), response.headers().value(i));
            }
        } catch (Exception ignored) {
        }
        return resources;
    }

    public void setRequest(WebResourceRequest request) {
        mRequest = request;
    }

    private Response requestResource(WebResourceRequest request) {
        try {
            Request.Builder builder = new Request.Builder()
                    .url(request.getUrl().toString());
            for (Map.Entry<String, String> requestHeaders : request.getRequestHeaders().entrySet()) {
                builder.addHeader(requestHeaders.getKey(), requestHeaders.getValue());
            }
            return httpClient.newCall(builder.build()).execute();
        } catch (Exception e) {
            return null;
        }
    }

}
