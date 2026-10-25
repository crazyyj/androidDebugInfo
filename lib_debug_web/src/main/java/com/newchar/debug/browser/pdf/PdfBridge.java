package com.newchar.debug.browser.pdf;

import android.util.Base64;
import android.util.Log;
import android.webkit.WebView;

import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 页面与原生之间的受限通道。
 *
 * <p>使用 {@link WebMessageCompat} 而不是 {@code addJavascriptInterface}：
 * 前者带 origin 白名单，页面里的第三方脚本无法伪造调用；后者会把整个对象
 * 暴露给任何加载到该实例的页面。二进制回写依赖
 * {@code WEB_MESSAGE_ARRAY_BUFFER}，不支持时退化为 JSON + Base64。</p>
 */
final class PdfBridge {

    private static final String TAG = "PdfBridge";
    private static final String NAME = "pdfBridge";

    /** 二进制保存前需要一次 begin 消息告知目标令牌，按实例记录。 */
    private static final Map<WebView, String> PENDING = Collections.synchronizedMap(new WeakHashMap<>());

    private PdfBridge() {
    }

    /**
     * 安装通道。
     *
     * @param view      目标实例
     * @param origin    唯一允许的来源
     * @param documents 文档仓库
     * @param listener  保存结果回调，可为 null
     * @return 内核不支持时为 false
     */
    static boolean install(WebView view, String origin, PdfDocuments documents, PdfViewer.Listener listener) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Log.w(TAG, "内核不支持 WEB_MESSAGE_LISTENER，PDF 回写通道不可用");
            return false;
        }
        WebViewCompat.addWebMessageListener(view, NAME, Collections.singleton(origin),
                (target, message, sourceOrigin, isMainFrame, reply) ->
                        onMessage(target, message, isMainFrame, reply, documents, listener));
        return true;
    }

    private static void onMessage(WebView view, WebMessageCompat message, boolean isMainFrame,
                                  JavaScriptReplyProxy reply, PdfDocuments documents,
                                  PdfViewer.Listener listener) {
        if (!isMainFrame || message == null) {
            return;
        }
        try {
            if (message.getType() == WebMessageCompat.TYPE_ARRAY_BUFFER) {
                handleBinary(view, message.getArrayBuffer(), reply, documents, listener);
                return;
            }
            JSONObject request = new JSONObject(message.getData());
            String type = request.optString("type");
            if ("ready".equals(type)) {
                reply.postMessage("{\"type\":\"ready\"}");
            } else if ("save.begin".equals(type)) {
                String token = request.optString("token");
                if (documents.find(token) != null) {
                    PENDING.put(view, token);
                    reply.postMessage("{\"type\":\"ready\"}");
                } else {
                    reply.postMessage(error("令牌无效"));
                }
            } else if ("save".equals(type)) {
                byte[] content = Base64.decode(request.optString("base64"), Base64.DEFAULT);
                if (content.length > 0) {
                    deliver(view, documents, listener, request.optString("name"), content, reply);
                } else {
                    reply.postMessage(error("保存内容为空"));
                }
            } else if ("log".equals(type)) {
                Log.d(TAG, request.optString("message"));
            }
        } catch (Exception error) {
            Log.w(TAG, "处理页面消息失败", error);
            post(reply, error(error.getMessage()));
        }
    }

    private static void handleBinary(WebView view, byte[] content, JavaScriptReplyProxy reply,
                                     PdfDocuments documents, PdfViewer.Listener listener) {
        String token = PENDING.remove(view);
        if (token == null || content == null || content.length == 0) {
            reply.postMessage(error("缺少保存上下文"));
            return;
        }
        deliver(view, documents, listener, token + ".pdf", content, reply);
    }

    private static void deliver(WebView view, PdfDocuments documents, PdfViewer.Listener listener,
                                String name, byte[] content, JavaScriptReplyProxy reply) {
        try {
            File file = documents.write(name, content);
            reply.postMessage("{\"type\":\"saved\",\"name\":" + JSONObject.quote(file.getName()) + "}");
            if (listener != null) {
                view.post(() -> listener.onPdfSaved(view, file));
            }
        } catch (Exception error) {
            Log.w(TAG, "保存 PDF 失败", error);
            post(reply, error(error.getMessage()));
            if (listener != null) {
                view.post(() -> listener.onPdfError(view, error));
            }
        }
    }

    private static void post(JavaScriptReplyProxy reply, String payload) {
        try {
            reply.postMessage(payload);
        } catch (RuntimeException ignored) {
        }
    }

    private static String error(String message) {
        return "{\"type\":\"error\",\"message\":" + JSONObject.quote(message == null ? "未知错误" : message) + "}";
    }
}
