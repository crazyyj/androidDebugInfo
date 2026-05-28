package com.newchar.debug.sample;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.newchar.debug.net.DebugNetMonitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OschinaApiActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://www.oschina.net/action/openapi";

    private static final Map<String, String> API_MAP = new LinkedHashMap<>();

    static {
        API_MAP.put("新闻列表", "/news_list?catalog=1&pageIndex=1&pageSize=10&dataType=json");
        API_MAP.put("综合新闻", "/news_list?catalog=2&pageIndex=1&pageSize=10&dataType=json");
        API_MAP.put("软件更新", "/news_list?catalog=3&pageIndex=1&pageSize=10&dataType=json");
        API_MAP.put("博客推荐列表", "/blog_recommend_list?pageIndex=1&pageSize=10&dataType=json");
        API_MAP.put("动弹列表(最新)", "/tweet_list?user=0&pageIndex=1&pageSize=10&dataType=json");
        API_MAP.put("动弹列表(热门)", "/tweet_list?user=-1&pageIndex=1&pageSize=10&dataType=json");
        API_MAP.put("软件列表", "/software_list?pageIndex=1&pageSize=10&dataType=json");
        API_MAP.put("搜索(关键词=Android)", "/search?q=Android&pageIndex=1&pageSize=10&dataType=json");
    }

    private TextView logView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private View buildContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        container.setPadding(padding, padding, padding, padding);
        scrollView.addView(container, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView titleView = new TextView(this);
        titleView.setText("OSChina 公开 API 请求");
        titleView.setTextSize(18f);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, dp(16));
        container.addView(titleView);

        Button btnStartVpn = new Button(this);
        btnStartVpn.setText("启动 VPN 网络监控");
        btnStartVpn.setAllCaps(false);
        btnStartVpn.setOnClickListener(v -> {
            boolean starting = DebugNetMonitor.start(this);
            if (starting) {
                appendLog("VPN 网络监控已启动");
            } else {
                appendLog("VPN 网络监控启动中，请授权后重试");
            }
        });
        container.addView(btnStartVpn);

        Button btnStopVpn = new Button(this);
        btnStopVpn.setText("停止 VPN 网络监控");
        btnStopVpn.setAllCaps(false);
        btnStopVpn.setOnClickListener(v -> {
            DebugNetMonitor.stop(this);
            appendLog("VPN 网络监控已停止");
        });
        container.addView(btnStopVpn);

        Button btnRequestAll = new Button(this);
        btnRequestAll.setText("请求全部 API");
        btnRequestAll.setAllCaps(false);
        btnRequestAll.setOnClickListener(v -> {
            appendLog("========== 开始请求全部 API ==========");
            for (Map.Entry<String, String> entry : API_MAP.entrySet()) {
                requestApi(entry.getKey(), entry.getValue());
            }
        });
        container.addView(btnRequestAll);
        ((LinearLayout.LayoutParams) btnRequestAll.getLayoutParams()).topMargin = dp(8);

        for (Map.Entry<String, String> entry : API_MAP.entrySet()) {
            Button btn = new Button(this);
            btn.setText("请求: " + entry.getKey());
            btn.setAllCaps(false);
            btn.setOnClickListener(v -> requestApi(entry.getKey(), entry.getValue()));
            container.addView(btn);
            ((LinearLayout.LayoutParams) btn.getLayoutParams()).topMargin = dp(4);
        }

        Button btnClear = new Button(this);
        btnClear.setText("清空日志");
        btnClear.setAllCaps(false);
        btnClear.setOnClickListener(v -> {
            if (logView != null) {
                logView.setText("");
            }
        });
        container.addView(btnClear);
        ((LinearLayout.LayoutParams) btnClear.getLayoutParams()).topMargin = dp(8);

        logView = new TextView(this);
        logView.setTextColor(0xFF333333);
        logView.setTextSize(12f);
        logView.setPadding(0, dp(12), 0, 0);
        container.addView(logView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return scrollView;
    }

    private void requestApi(String name, String path) {
        String fullUrl = BASE_URL + path;
        appendLog(">> " + name + " : " + fullUrl);
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(fullUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "DebugView-Sample/1.0");

                int responseCode = connection.getResponseCode();
                String body = readStream(connection);
                String result = body.length() > 500 ? body.substring(0, 500) + "..." : body;
                mainHandler.post(() -> {
                    appendLog("<< " + name + " [" + responseCode + "] " + result);
                    appendLog("   请在 VPN 监控中查看完整网络数据");
                });
            } catch (Exception e) {
                mainHandler.post(() -> appendLog("!! " + name + " 请求失败: " + e.getMessage()));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    @SuppressLint("NewApi")
    private String readStream(HttpURLConnection connection) {
        try {
            BufferedReader reader;
            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "读取响应失败: " + e.getMessage();
        }
    }

    private void appendLog(String line) {
        if (logView == null) {
            return;
        }
        CharSequence old = logView.getText();
        String newLine = (old == null || old.length() == 0) ? line : old + "\n" + line;
        logView.setText(newLine);
        if (newLine.length() > 20000) {
            logView.setText(newLine.substring(newLine.length() - 15000));
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
