package com.newchar.debug.browser.pdf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

/**
 * 无界面的文件选择入口。
 *
 * <p>插件只有 {@code Application} 上下文，拿不到 {@code onActivityResult}，
 * 因此用一个透明 Activity 自己完成选择与回传，宿主不需要写任何代码。</p>
 */
public class PdfPickerActivity extends Activity {

    private static final int REQUEST_PICK = 0x5044;
    private static WeakReference<WebView> target = new WeakReference<>(null);

    /**
     * 拉起系统文件选择器。
     *
     * @param context 任意上下文
     * @param view    选完后要加载文档的实例
     */
    public static void start(Context context, WebView view) {
        target = new WeakReference<>(view);
        Intent intent = new Intent(context, PdfPickerActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null || !PdfViewer.getInstance().isReady()) {
            finish();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf"});
        try {
            startActivityForResult(intent, REQUEST_PICK);
        } catch (RuntimeException unavailable) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        WebView view = target.get();
        if (requestCode == REQUEST_PICK && resultCode == RESULT_OK && data != null
                && data.getData() != null && view != null) {
            PdfViewer.getInstance().open(view, data.getData(), null);
        }
        finish();
    }
}
