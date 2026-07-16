package com.newchar.debug.net;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 网络事件详情页。通过静态 holder 接收点击事件，避免改造 Parcelable。
 */
public class DebugNetDetailActivity extends Activity {

    static DebugNetEvent sEvent;

    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DebugNetEvent event = sEvent;
        if (event == null) {
            Toast.makeText(this, "事件数据不可用", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        sEvent = null;
        setContentView(buildContentView(this, event));
    }

    private View buildContentView(Context context, DebugNetEvent event) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        root.setBackgroundColor(0xFF202020);

        ScrollView scroll = new ScrollView(context);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(inner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView text = new TextView(context);
        text.setTextSize(12f);
        text.setTextColor(0xFFD0D0D0);
        text.setTypeface(Typeface.MONOSPACE);
        text.setText(buildDetailText(event));
        text.setPadding(8, 8, 8, 8);
        inner.addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button copyButton = new Button(context);
        copyButton.setText("复制");
        copyButton.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("debug_net_detail", buildDetailText(event)));
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show();
        });
        copyButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        buttons.addView(copyButton);

        Button shareButton = new Button(context);
        shareButton.setText("分享");
        shareButton.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, buildDetailText(event));
            startActivity(Intent.createChooser(shareIntent, "分享"));
        });
        shareButton.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        buttons.addView(shareButton);

        return root;
    }

    private static String buildDetailText(DebugNetEvent event) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "时间", formatTime(event.getTimeMillis()));
        appendLine(builder, "方向", event.getDirection() == TrafficDirection.DOWNLOAD ? "DOWN" : "UP");
        appendLine(builder, "协议", event.getProtocol() + (event.isHttps() ? "/HTTPS" : ""));
        if (!event.getMethod().isEmpty()) {
            appendLine(builder, "方法", event.getMethod());
        }
        appendLine(builder, "源", endpoint(event.getSourceAddress(), event.getSourcePort()));
        appendLine(builder, "目标", endpoint(event.getDestinationAddress(), event.getDestinationPort()));
        if (!event.getHost().isEmpty()) {
            appendLine(builder, "Host", event.getHost());
        }
        if (!event.getRequestPath().isEmpty()) {
            appendLine(builder, "Path", event.getRequestPath());
        }
        appendLine(builder, "字节数", String.valueOf(event.getByteCount()));
        if (event.getStatusCode() > 0) {
            appendLine(builder, "状态码", String.valueOf(event.getStatusCode()));
        } else if (!event.getFailureReason().isEmpty()) {
            appendLine(builder, "状态", "ERROR: " + event.getFailureReason());
        } else {
            appendLine(builder, "状态", "PENDING（未实现转发栈，无响应）");
        }
        appendLine(builder, "响应时间", event.getDurationMs() > 0
                ? event.getDurationMs() + "ms"
                : "无（未实现转发栈）");

        String reqHeaders = event.getRequestHeadersText();
        if (!TextUtils.isEmpty(reqHeaders)) {
            builder.append("\n=== 请求头 ===\n");
            builder.append(reqHeaders);
        } else {
            builder.append("\n=== 请求头 ===\n（空）");
        }

        String reqBody = event.getRequestBodyText();
        if (!TextUtils.isEmpty(reqBody)) {
            builder.append("\n=== 请求体 ===\n");
            builder.append(reqBody);
        } else {
            builder.append("\n=== 请求体 ===\n（空）");
        }

        String respHeaders = event.getResponseHeadersText();
        String respBody = event.getResponseBodyText();
        if (!TextUtils.isEmpty(respHeaders) || !TextUtils.isEmpty(respBody)) {
            builder.append("\n=== 响应头 ===\n");
            builder.append(TextUtils.isEmpty(respHeaders) ? "（空）" : respHeaders);
            builder.append("\n=== 响应体 ===\n");
            builder.append(TextUtils.isEmpty(respBody) ? "（空）" : respBody);
        } else {
            builder.append("\n=== 响应头 ===\n（无响应：未实现转发栈）");
            builder.append("\n=== 响应体 ===\n（无响应：未实现转发栈）");
        }

        return builder.toString();
    }

    private static void appendLine(StringBuilder builder, String key, String value) {
        builder.append(key).append(": ").append(value).append("\n");
    }

    private static String endpoint(String address, int port) {
        if (port > 0) {
            return address + ':' + port;
        }
        return address;
    }

    private static String formatTime(long timestamp) {
        SimpleDateFormat format = TIME_FORMAT.get();
        return format == null ? String.valueOf(timestamp) : format.format(new Date(timestamp));
    }

    public static void showFrom(Activity from, DebugNetEvent event) {
        if (from == null || event == null) {
            return;
        }
        sEvent = event;
        Intent intent = new Intent(from, DebugNetDetailActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        from.startActivity(intent);
    }
}
