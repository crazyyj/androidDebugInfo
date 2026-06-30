package com.newchar.debug.plugin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.browser.SimpleWebChrome;
import com.newchar.debug.browser.SimpleWebClient;
import com.newchar.debug.browser.WebSettingsBuilder;

public class DebugWebPlugin extends ScreenDisplayPlugin {

    public static final String ID = "debug_web";

    private WebView mWebView;
    private EditText mUrlInput;
    private ViewGroup mPluginContainer;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String getName() {
        return "网页调试";
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        mPluginContainer = pluginContainerView;
        Context context = pluginContainerView.getContext();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(Color.parseColor("#F5F5F5"));
        toolbar.setPadding(8, 4, 8, 4);
        toolbar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mUrlInput = new EditText(context);
        mUrlInput.setHint("输入网址");
        mUrlInput.setTextSize(14f);
        mUrlInput.setSingleLine(true);
        mUrlInput.setBackgroundColor(Color.WHITE);
        mUrlInput.setPadding(12, 8, 12, 8);
        mUrlInput.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        toolbar.addView(mUrlInput);

        View goButton = createGoButton(context);
        toolbar.addView(goButton);

        View refreshButton = createRefreshButton(context);
        toolbar.addView(refreshButton);

        root.addView(toolbar);

        mWebView = new WebView(context);
        mWebView.setWebViewClient(new SimpleWebClient());
        mWebView.setWebChromeClient(new SimpleWebChrome());
        WebSettingsBuilder builder = new WebSettingsBuilder(mWebView.getSettings());
        builder.buildDefault();
        mWebView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(mWebView);

        pluginContainerView.addView(root);
    }

    private View createGoButton(Context context) {
        android.widget.Button btn = new android.widget.Button(context);
        btn.setText("Go");
        btn.setTextSize(12f);
        btn.setPadding(16, 4, 16, 4);
        btn.setOnClickListener(v -> loadUrlFromInput());
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return btn;
    }

    private View createRefreshButton(Context context) {
        android.widget.Button btn = new android.widget.Button(context);
        btn.setText("↻");
        btn.setTextSize(16f);
        btn.setPadding(16, 4, 16, 4);
        btn.setOnClickListener(v -> {
            if (mWebView != null) {
                mWebView.reload();
            }
        });
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return btn;
    }

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

    @Override
    public void onShow() {
        if (mWebView != null) {
            mWebView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onHide() {
        if (mWebView != null) {
            mWebView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onUnload() {
        if (mWebView != null) {
            mWebView.stopLoading();
            mWebView.destroy();
            mWebView = null;
        }
        mUrlInput = null;
        mPluginContainer = null;
    }
}
