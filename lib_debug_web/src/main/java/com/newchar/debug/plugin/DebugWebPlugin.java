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
import com.newchar.debug.browser.pdf.PdfPickerActivity;
import com.newchar.debug.browser.pdf.PdfViewer;
import com.newchar.debug.browser.session.WebSessionStrategy;

import java.io.File;

public class DebugWebPlugin extends ScreenDisplayPlugin {

    public static final String ID = "debug_web";
    /** 首屏默认加载的 PDF，用于验证 pdf.js 预览链路；改回普通网址只需替换此值。 */
    private static final String DEFAULT_URL = "https://test-phl-collection-app.infra.fan/12055.pdf";

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
        installPdfSupport(context);
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
        toolbar.addView(createPdfButton(context));
        return toolbar;
    }

    /**
     * 注册 PDF 预览能力：初始化仓库、挂到工厂拦截器、接收回写结果。
     */
    private void installPdfSupport(Context context) {
        PdfViewer viewer = PdfViewer.getInstance();
        viewer.init(context);
        viewer.setListener(new PdfViewer.Listener() {
            /** 页面回写成功后提示实际落盘文件名。 */
            @Override
            public void onPdfSaved(WebView view, File file) {
                android.widget.Toast.makeText(view.getContext(),
                        "已保存：" + file.getName(), android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        WebViewFactory.getInstance().setPageInterceptor(viewer);
    }

    /**
     * 创建 PDF 选择按钮，选择结果由透明 Activity 直接交给当前实例。
     */
    private View createPdfButton(Context context) {
        android.widget.Button btn = new android.widget.Button(context);
        btn.setText("PDF");
        btn.setTextSize(12f);
        btn.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4));
        btn.setOnClickListener(v -> {
            if (mWebView == null) {
                android.widget.Toast.makeText(v.getContext(), "浏览器尚未就绪",
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            PdfPickerActivity.start(v.getContext(), mWebView);
        });
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return btn;
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
        loadInto(view, url.isEmpty() ? DEFAULT_URL : url);
    }

    /**
     * 按后缀分发：PDF 交给 pdf.js 容器，其余交给内核。
     *
     * @param view 目标实例
     * @param url  已补全协议的地址
     */
    private void loadInto(WebView view, String url) {
        if (view == null || url == null || url.isEmpty()) {
            return;
        }
        if (!isPdf(url)) {
            view.loadUrl(url);
            return;
        }
        PdfViewer.getInstance().open(view, url, new PdfViewer.Callback() {
            /** 页面已发起加载，渲染进度由 viewer 自己显示。 */
            @Override
            public void onOpened() {
            }

            /** 下载或握手失败时明确提示，避免留在空白页。 */
            @Override
            public void onError(Exception error) {
                android.widget.Toast.makeText(view.getContext(),
                        "PDF 加载失败：" + error.getMessage(),
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * 只按路径后缀判断，忽略 query 与 fragment。
     *
     * @param url 完整地址
     * @return 以 .pdf 结尾时为 true
     */
    private boolean isPdf(String url) {
        String value = url.toLowerCase(java.util.Locale.US);
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int fragment = value.indexOf('#');
        if (fragment >= 0) {
            value = value.substring(0, fragment);
        }
        return value.endsWith(".pdf");
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
        loadInto(mWebView, url);
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