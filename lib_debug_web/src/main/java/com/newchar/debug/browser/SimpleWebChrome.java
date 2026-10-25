package com.newchar.debug.browser;

import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import com.newchar.debug.browser.session.WebSessionStrategy;

public class SimpleWebChrome extends WebChromeClient {

    private final WebSessionStrategy.Session session;

    /**
     * 创建与指定会话关联的 Chrome 客户端。
     */
    public SimpleWebChrome(WebSessionStrategy.Session session) {
        this.session = session;
    }

    /**
     * 保留默认会话兼容构造入口。
     */
    public SimpleWebChrome() {
        this(null);
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
