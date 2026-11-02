package com.newchar.debug.browser;

import android.webkit.ConsoleMessage;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import com.newchar.debug.browser.permission.WebMediaPermissionController;
import com.newchar.debug.browser.session.WebSessionStrategy;

public class SimpleWebChrome extends WebChromeClient {

    private final WebSessionStrategy.Session session;
    private final android.content.Context permissionContext;
    private final WebMediaPermissionController mediaPermissionController;
    private final Object permissionOwner = new Object();

    /**
     * 创建与指定会话关联的 Chrome 客户端。
     */
    public SimpleWebChrome(WebSessionStrategy.Session session) {
        this(session, null);
    }

    /**
     * 创建与页面上下文关联的 Chrome 客户端，用于展示网页媒体授权提示。
     */
    public SimpleWebChrome(WebSessionStrategy.Session session, android.content.Context context) {
        this.session = session;
        permissionContext = context;
        mediaPermissionController = context == null ? null
                : WebMediaPermissionController.getInstance(context);
    }

    /**
     * 保留默认会话兼容构造入口。
     */
    public SimpleWebChrome() {
        this(null);
    }

    /**
     * 仅转交录音和摄像请求，其他网页权限保持拒绝以避免扩大授权面。
     */
    @Override
    public void onPermissionRequest(PermissionRequest request) {
        if (mediaPermissionController == null) {
            request.deny();
            return;
        }
        mediaPermissionController.request(permissionContext, permissionOwner, request);
    }

    /**
     * 内核已取消请求时同步移除等待项和可能已显示的确认框。
     */
    @Override
    public void onPermissionRequestCanceled(PermissionRequest request) {
        if (mediaPermissionController != null) {
            mediaPermissionController.cancel(request);
        }
    }

    /**
     * 工厂销毁 WebView 前拒绝尚未完成的媒体请求，避免回调泄漏到旧页面。
     */
    void release() {
        if (mediaPermissionController != null) {
            mediaPermissionController.release(permissionOwner);
        }
    }

    /**
     * 顶层页面开始跳转时清除旧页面的媒体状态；新页面请求时会重新回调状态。
     */
    void clearMediaCaptureStatus() {
        if (mediaPermissionController != null) {
            mediaPermissionController.clearMediaCaptureStatus(permissionOwner);
        }
    }

    /**
     * 接收已校验来源的页面媒体状态，并转交给权限控制器汇总展示。
     */
    void reportMediaCaptureState(String origin, boolean audioCapturing, boolean videoCapturing) {
        if (mediaPermissionController != null) {
            mediaPermissionController.reportMediaCaptureState(permissionOwner, origin,
                    audioCapturing, videoCapturing);
        }
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        // 页面脚本异常是 PDF 预览失败的主要线索，必须落到 logcat。
        android.util.Log.d("DebugWebConsole", consoleMessage.message()
                + " @" + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
        return true;
    }

    /**
     * 首期拒绝未经过会话工厂绑定的弹窗 WebView。
     */
    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                  android.os.Message resultMsg) {
        return false;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
    }
}