package com.newchar.debug.plugin;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.browser.WebViewFactory;
import com.newchar.debug.browser.session.WebSessionStrategy;

public class DebugWebPlugin extends ScreenDisplayPlugin {

    public static final String ID = "debug_web";
    private static final String DEFAULT_URL = "https://www.baidu.com";

    private String mSessionId = "debug_web";
    private boolean mCreationPending;
    private boolean mSessionUnavailable;
    private WebView mWebView;
    private EditText mUrlInput;
    private TextView mSessionMode;
    private ViewGroup mPluginContainer;
    private View mRootView;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String getName() {
        return "网页调试";
    }

    /** 构造页面并异步恢复浏览会话。 */
    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        mPluginContainer = pluginContainerView;
        Context context = pluginContainerView.getContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mRootView = root;
        mSessionUnavailable = false;
        mSessionId = "debug_web";
        root.addView(createToolbar(context));
        pluginContainerView.addView(root);
        root.setVisibility(View.GONE);
        mUrlInput.setText(DEFAULT_URL);
        requestWebView(root);
    }

    /** 创建网址和模式工具栏，实际探测禁用时给出状态。 */
    private LinearLayout createToolbar(Context context) {
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(Color.parseColor("#F5F5F5"));
        toolbar.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
        toolbar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mUrlInput = new EditText(context);
        mUrlInput.setHint("输入网址");
        mUrlInput.setTextSize(14f);
        mUrlInput.setSingleLine(true);
        mUrlInput.setBackgroundColor(Color.WHITE);
        mUrlInput.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        mUrlInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        toolbar.addView(mUrlInput);
        mSessionMode = new TextView(context);
        mSessionMode.setTextSize(11f);
        mSessionMode.setPadding(dp(context, 6), 0, dp(context, 6), 0);
        toolbar.addView(mSessionMode);
        toolbar.addView(createGoButton(context));
        toolbar.addView(createRefreshButton(context));
        return toolbar;
    }

    /** 恢复使用返回的真实 sessionId，渲染进程退出后仍指向同一账号。 */
    private void requestWebView(LinearLayout root) {
        mCreationPending = true;
        WebViewFactory.getInstance().createWebView(root.getContext(), mSessionId, createCallback(root));
    }

    /** 创建生命周期回调，过期页面收到实例时立即释放。 */
    private WebViewFactory.WebViewCallback createCallback(LinearLayout root) {
        return new WebViewFactory.WebViewCallback() {
            /** 接收绑定完成的实际会话。 */
            @Override public void onReady(WebView view, WebSessionStrategy.Session session,
                                           WebSessionStrategy.Capabilities capabilities) {
                attachWebView(root, view, session, capabilities);
            }
            /** 只有当前页面显示创建错误。 */
            @Override public void onError(Exception error) {
                if (mRootView == root) {
                    mCreationPending = false;
                    mSessionMode.setText("浏览器初始化失败：" + error.getMessage());
                }
            }
            /** 当前实例失效，下次显示才重建，避免渲染退出后立即循环重启。 */
            @Override public void onRenderProcessGone(WebSessionStrategy.Session session) {
                if (mRootView == root) { mWebView = null; mSessionMode.setText("浏览器已退出，下次显示时恢复"); }
            }
            /** 会话停用时关闭自动恢复，避免恢复到其他账号。 */
            @Override public void onSessionUnavailable(WebSessionStrategy.Session session) {
                if (mRootView == root) {
                    mWebView = null;
                    mSessionUnavailable = true;
                    mSessionMode.setText("当前账号会话暂不可用");
                }
            }
        };
    }

    /** 安装新实例并恢复网址，明确显示实际策略与探测状态。 */
    private void attachWebView(LinearLayout root, WebView view, WebSessionStrategy.Session session,
                               WebSessionStrategy.Capabilities capabilities) {
        if (mRootView != root) { WebViewFactory.getInstance().releaseWebView(view); return; }
        mCreationPending = false;
        mWebView = view;
        mSessionId = session.sessionId;
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(view);
        String mode = capabilities.supportsMultipleAccounts ? "多账号模式" : "默认单账号模式";
        boolean probing = com.newchar.debug.browser.store.WebResourceMetadataObserver.isProbeEnabled();
        mSessionMode.setText(mode + (probing ? "" : " · 资源探测未启用"));
        String url = mUrlInput.getText().toString().trim();
        view.loadUrl(url.isEmpty() ? DEFAULT_URL : url);
    }

    /** 将工具栏尺寸转换为 dp，适配设备密度。 */
    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** 创建网址跳转按钮。 */
    private View createGoButton(Context context) {
        android.widget.Button btn = new android.widget.Button(context);
        btn.setText("Go");
        btn.setTextSize(12f);
        btn.setPadding(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 4));
        btn.setOnClickListener(v -> loadUrlFromInput());
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return btn;
    }

    /** 创建当前页面刷新按钮。 */
    private View createRefreshButton(Context context) {
        android.widget.Button btn = new android.widget.Button(context);
        btn.setText("↻");
        btn.setTextSize(16f);
        btn.setPadding(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 4));
        btn.setOnClickListener(v -> {
            if (mWebView != null) {
                mWebView.reload();
            }
        });
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return btn;
    }

    /** 导航至用户输入的网址。 */
    private void loadUrlFromInput() {
        if (mUrlInput == null || mWebView == null) {
            return;
        }
        String url = mUrlInput.getText().toString().trim();
        if (url.isEmpty()) {
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
            mUrlInput.setText(url);
        }
        mWebView.loadUrl(url);
    }

    /** 显示页面，失效实例按原会话重建。 */
    @Override
    public void onShow() {
        if (mRootView != null) {
            mRootView.setVisibility(View.VISIBLE);
        }
        if (mWebView == null && !mCreationPending && !mSessionUnavailable && mRootView instanceof LinearLayout) {
            requestWebView((LinearLayout) mRootView);
        }
        if (mWebView != null) {
            mWebView.setVisibility(View.VISIBLE);
            mWebView.onResume();
        }
    }

    /** 隐藏页面并暂停实例。 */
    @Override
    public void onHide() {
        if (mRootView != null) {
            mRootView.setVisibility(View.GONE);
        }
        if (mWebView != null) {
            mWebView.onPause();
        }
    }

    /** 释放页面实例与任务，登录数据保留。 */
    @Override
    public void onUnload() {
        if (mWebView != null) {
            WebViewFactory.getInstance().releaseWebView(mWebView);
            mWebView = null;
        }
        mCreationPending = false;
        mUrlInput = null;
        mSessionMode = null;
        mPluginContainer = null;
        mRootView = null;
    }
}