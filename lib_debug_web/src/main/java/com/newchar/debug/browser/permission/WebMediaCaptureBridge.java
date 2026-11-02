package com.newchar.debug.browser.permission;

import android.net.Uri;
import android.webkit.WebView;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 在文档启动阶段监听 getUserMedia 流的停止事件，并将实际采集状态回传原生。
 */
public final class WebMediaCaptureBridge {

    private static final String NAME = "debugWebMediaBridge";
    private static final Set<String> HTTP_ORIGINS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("https://*", "http://*")));
    private static final String SCRIPT = "(function(){if(window.__debugMediaCaptureInstalled)return;"
            + "window.__debugMediaCaptureInstalled=true;var b=window." + NAME + ";"
            + "var t=[];function r(){var a=0,v=0;for(var i=t.length-1;i>=0;i--){var x=t[i];"
            + "if(!x||x.readyState!=='live'){t.splice(i,1);continue;}if(x.kind==='audio')a=1;"
            + "if(x.kind==='video')v=1;}if(b&&b.postMessage)b.postMessage(a+','+v);}"
            + "function w(s){if(!s)return;var q=s.getTracks?s.getTracks():[];for(var i=0;i<q.length;i++){"
            + "var x=q[i];if(t.indexOf(x)<0){t.push(x);x.addEventListener('ended',r);}}r();}"
            + "var m=navigator.mediaDevices;if(!m||!m.getUserMedia)return;var g=m.getUserMedia.bind(m);"
            + "m.getUserMedia=function(){var p=g.apply(m,arguments);return p.then(function(s){w(s);return s;});};"
            + "if(!window.MediaStreamTrack)return;var z=MediaStreamTrack.prototype.stop;MediaStreamTrack.prototype.stop=function(){"
            + "var o=z.apply(this,arguments);Promise.resolve().then(r);return o;};"
            + "addEventListener('pagehide',r);})();";

    private final WebView view;
    private final ScriptHandler scriptHandler;

    /**
     * 接收已经过来源校验的网页媒体采集状态。
     */
    public interface Listener {
        /**
         * 回传同一页面当前存活的音频和视频轨道。
         *
         * @param origin 页面来源
         * @param audioCapturing 是否正在录音
         * @param videoCapturing 是否正在拍摄
         */
        void onMediaCaptureState(String origin, boolean audioCapturing, boolean videoCapturing);
    }

    /**
     * 创建安装结果；不支持的内核返回仅包含空句柄的实例。
     */
    private WebMediaCaptureBridge(WebView view, ScriptHandler scriptHandler) {
        this.view = view;
        this.scriptHandler = scriptHandler;
    }

    /**
     * 在首次页面加载前安装受来源限制的 JavaScript 消息通道和监听脚本。
     *
     * @param view     目标 WebView
     * @param listener 状态回调
     * @return 可在 WebView 销毁时释放的桥接实例
     */
    public static WebMediaCaptureBridge install(WebView view, Listener listener) {
        if (view == null || listener == null || !supportsBridge()) {
            return new WebMediaCaptureBridge(view, null);
        }
        try {
            WebViewCompat.addWebMessageListener(view, NAME, HTTP_ORIGINS,
                    (target, message, sourceOrigin, isMainFrame, reply) ->
                            onMessage(message, sourceOrigin, listener));
            return new WebMediaCaptureBridge(view,
                    WebViewCompat.addDocumentStartJavaScript(view, SCRIPT, HTTP_ORIGINS));
        } catch (RuntimeException error) {
            removeMessageListener(view);
            return new WebMediaCaptureBridge(view, null);
        }
    }

    /**
     * 移除下一次加载不再需要的脚本与消息监听器。
     */
    public void release() {
        if (scriptHandler != null) {
            try {
                scriptHandler.remove();
            } catch (RuntimeException ignored) {
            }
        }
        removeMessageListener(view);
    }

    /**
     * 同时确认消息监听和文档启动脚本均由当前 WebView 内核支持。
     */
    private static boolean supportsBridge() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
    }

    /**
     * 解析受限桥的紧凑状态消息，并拒绝无效来源或格式。
     */
    private static void onMessage(WebMessageCompat message, Uri sourceOrigin, Listener listener) {
        String origin = WebMediaPermissionOrigin.from(sourceOrigin);
        String state = message == null ? null : message.getData();
        if (origin == null || state == null || state.length() != 3 || state.charAt(1) != ',') {
            return;
        }
        if ((state.charAt(0) != '0' && state.charAt(0) != '1')
                || (state.charAt(2) != '0' && state.charAt(2) != '1')) {
            return;
        }
        listener.onMediaCaptureState(origin, state.charAt(0) == '1', state.charAt(2) == '1');
    }

    /**
     * 释放时尽力移除消息监听；内核异常不应阻断 WebView 销毁流程。
     */
    private static void removeMessageListener(WebView view) {
        if (view == null || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return;
        }
        try {
            WebViewCompat.removeWebMessageListener(view, NAME);
        } catch (RuntimeException ignored) {
        }
    }
}