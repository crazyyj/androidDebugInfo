package com.newchar.debug.sample;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.newchar.debug.browser.WebViewFactory;
import com.newchar.debug.browser.pdf.PdfViewer;
import com.newchar.debug.browser.session.WebSessionStrategy;

/**
 * 全屏 PDF 预览示例：验证 WebViewFactory 与 PdfViewer 的组合链路。
 *
 * <p>启动方式：{@code adb shell am start -n com.newchar.debug.sample/.PdfPreviewActivity}，
 * 或用 {@code --es url <地址>} 指定其他文档。</p>
 */
public class PdfPreviewActivity extends Activity {

    /** 外部传入的 PDF 地址。 */
    public static final String EXTRA_URL = "url";

    private static final String DEFAULT_URL = "https://test-phl-collection-app.infra.fan/12055.pdf";

    private FrameLayout container;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        container = new FrameLayout(this);
        setContentView(container);

        String extra = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_URL);
        final String url = extra == null || extra.trim().isEmpty() ? DEFAULT_URL : extra.trim();

        final PdfViewer viewer = PdfViewer.getInstance();
        viewer.init(this);
        WebViewFactory.getInstance().setPageInterceptor(viewer);
        WebViewFactory.getInstance().createWebView(this, "default", new WebViewFactory.WebViewCallback() {
            @Override
            public void onReady(WebView view, WebSessionStrategy.Session session,
                                WebSessionStrategy.Capabilities capabilities) {
                webView = view;
                view.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                container.addView(view);
                viewer.open(view, url, new PdfViewer.Callback() {
                    @Override
                    public void onOpened() {
                    }

                    @Override
                    public void onError(Exception error) {
                        showError(error);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                showError(error);
            }
        });
    }

    /** 用纯文本展示失败原因，避免停留在空白页。 */
    private void showError(Exception error) {
        TextView text = new TextView(this);
        text.setText("PDF 加载失败：" + (error == null ? "未知错误" : error.getMessage()));
        text.setTextSize(14f);
        text.setPadding(48, 96, 48, 48);
        container.removeAllViews();
        container.addView(text);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            WebViewFactory.getInstance().releaseWebView(webView);
            webView = null;
        }
        super.onDestroy();
    }
}
