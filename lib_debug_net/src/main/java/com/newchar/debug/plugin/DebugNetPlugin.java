package com.newchar.debug.plugin;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.newchar.debug.utils.KVUtil;
import com.newchar.debug.utils.ViewUtils;
import com.newchar.debug.router.ResultProxyRouter;
import com.newchar.debug.router.ResultProxyCallback;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.core.traffic.TrafficInfo;
import com.newchar.debug.core.traffic.TrafficMonitor;
import com.newchar.debug.net.DebugNetConfig;
import com.newchar.debug.net.DebugNetEvent;
import com.newchar.debug.net.DebugNetMonitor;
import com.newchar.debug.net.DebugNetTrafficListener;
import com.newchar.debug.net.DebugNetDetailActivity;
import com.newchar.debug.net.DebugNetCertificatePickerActivity;
import com.newchar.debug.utils.HandleWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用 VPN 捕获当前 App 网络包，并用列表展示每次通信记录。
 */
public class DebugNetPlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "DEBUG_NET";

    private static final int MAX_EVENT_COUNT = 300;
    private static final int MAX_FLUSH_BATCH = 60;
    private static final long TRAFFIC_REFRESH_INTERVAL_MS = 1000L;
    private static final String KEY_HTTP_DECODE = "debug_net_http_decode";
    private static final String KEY_HTTPS_DECODE = "debug_net_https_decode";
    private static final String KEY_CERT_PATH = "debug_net_cert_path";
    private static final String KEY_CERT_PASSWORD = "debug_net_cert_password";
    private static final String KEY_KEYSTORE_TYPE = "debug_net_keystore_type";

    private static final int REQUEST_CERT_PICK = 0x5001;
    private static final int ID_CERT_PICK = 9001;

    private final List<DebugNetEvent> mEvents = new ArrayList<>();
    private final ConcurrentLinkedQueue<DebugNetEvent> mPendingEvents = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean mFlushScheduled = new AtomicBoolean(false);

    private LinearLayout mRootView;
    private ListView mListView;
    private NetPluginAdapter mAdapter;
    private TextView mStatusView;
    private TextView mTrafficView;
    private EditText mFilterInput;
    private String mFilterText = "";
    private Button mVpnToggleBtn;
    private CheckBox mHttpDecodeCheckBox;
    private CheckBox mHttpsDecodeCheckBox;
    private LinearLayout mCertRow;
    private Button mCertButton;
    private EditText mCertPasswordInput;
    private TextView mCertTypeTextView;
    private Context mAppContext;
    private TrafficMonitor mTrafficMonitor;
    private final Handler mMainHandler = HandleWrapper.getMainHandler();

    /** 从当前可见的 View 上下文中提取 Activity（用于启动新 Activity）。 */
    @SuppressWarnings("unused")
    private Activity getActivityFromContext() {
        if (mRootView == null || mRootView.getContext() == null) {
            return null;
        }
        Context ctx = mRootView.getContext();
        while (ctx != null) {
            if (ctx instanceof Activity) {
                return (Activity) ctx;
            }
            if (ctx instanceof android.view.ContextThemeWrapper) {
                android.view.ContextThemeWrapper wrapper = (android.view.ContextThemeWrapper) ctx;
                ctx = wrapper.getBaseContext();
                continue;
            }
            break;
        }
        return null;
    }

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public String getName() {
        return "网络监控";
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        mAppContext = pluginContainerView.getContext().getApplicationContext();
        initView(pluginContainerView.getContext());
        pluginContainerView.addView(mRootView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                0);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mRootView.getLayoutParams();
        lp.weight = 1f;
        restoreConfigIntoMonitor();
        DebugNetMonitor.addListener(mTrafficListener);
        updateStatus();
        ViewUtils.setVisibility(mRootView, View.GONE);
    }

    @Override
    public void onShow() {
        updateStatus();
        restoreInputs();
        startTrafficMonitor();
        ViewUtils.setVisibility(mRootView, View.VISIBLE);
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(mRootView, View.GONE);
        stopTrafficMonitor();
    }

    @Override
    public CharSequence onCopyText() {
        if (mEvents.isEmpty()) {
            return null;
        }
        return buildEventsText();
    }

    @Override
    public Intent onCreateShareIntent() {
        CharSequence text = onCopyText();
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        return intent;
    }

    @Override
    public void onClear() {
        mEvents.clear();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onUnload() {
        DebugNetMonitor.removeListener(mTrafficListener);
        // 防御性停止：只在 context 有效且 VPN 还在运行时才停止
        if (mAppContext != null && DebugNetMonitor.isRunning()) {
            try {
                DebugNetMonitor.stop(mAppContext);
            } catch (Throwable t) {
                // 服务已销毁时 startService 会失败，忽略即可
            }
        }
        HandleWrapper.getMainHandler().removeCallbacks(mFlushTask);
        stopTrafficMonitor();
        mPendingEvents.clear();
        mFlushScheduled.set(false);
        mEvents.clear();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        mRootView = null;
        mListView = null;
        mStatusView = null;
        mTrafficView = null;
        mAdapter = null;
        mVpnToggleBtn = null;
        mHttpDecodeCheckBox = null;
        mHttpsDecodeCheckBox = null;
        mCertRow = null;
        mCertButton = null;
        mCertPasswordInput = null;
        mCertTypeTextView = null;
        mAppContext = null;
        if (mTrafficMonitor != null) {
            mTrafficMonitor.release();
            mTrafficMonitor = null;
        }
    }

    private void initView(Context context) {
        if (mRootView != null) {
            return;
        }
        mRootView = new LinearLayout(context);
        mRootView.setOrientation(LinearLayout.VERTICAL);
        mRootView.setBackgroundColor(0x80808080);

        mListView = new ListView(context);
        mAdapter = new NetPluginAdapter(context, mEvents);
        mAdapter.ensureConfigView();
        mListView.setAdapter(mAdapter);

        mRootView.addView(mListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private CharSequence buildEventsText() {
        StringBuilder builder = new StringBuilder();
        for (int i = mEvents.size() - 1; i >= 0; i--) {
            DebugNetEvent event = mEvents.get(i);
            if (matchesFilter(event)) {
                builder.append(event.getDisplayText());
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private boolean matchesFilter(DebugNetEvent event) {
        if (mFilterText.isEmpty()) {
            return true;
        }
        String summary = event.getSummaryText().toLowerCase();
        String host = (event.getHost() == null ? "" : event.getHost()).toLowerCase();
        String path = (event.getRequestPath() == null ? "" : event.getRequestPath()).toLowerCase();
        return summary.contains(mFilterText) || host.contains(mFilterText) || path.contains(mFilterText);
    }

    private void startTrafficMonitor() {
        if (mAppContext == null) {
            return;
        }
        if (mTrafficMonitor == null) {
            mTrafficMonitor = new TrafficMonitor(mAppContext);
        }
        mMainHandler.removeCallbacks(mTrafficRefreshTask);
        mMainHandler.post(mTrafficRefreshTask);
    }

    private void stopTrafficMonitor() {
        mMainHandler.removeCallbacks(mTrafficRefreshTask);
    }

    private final Runnable mTrafficRefreshTask = new Runnable() {
        @Override
        public void run() {
            if (mTrafficMonitor == null || mTrafficView == null) {
                return;
            }
            TrafficInfo info = mTrafficMonitor.sample();
            mTrafficView.setText("网络流量\n接收总量 : " + formatBytes(info.getRxBytes())
                    + "\n发送总量 : " + formatBytes(info.getTxBytes())
                    + "\n接收速率 : " + formatBytes(info.getRxSpeedBytes()) + "/s"
                    + "\n发送速率 : " + formatBytes(info.getTxSpeedBytes()) + "/s");
            if (mRootView != null && mRootView.getVisibility() == View.VISIBLE) {
                mMainHandler.postDelayed(this, TRAFFIC_REFRESH_INTERVAL_MS);
            }
        }
    };

    private static String formatBytes(double bytes) {
        if (bytes < 0) {
            return "不可用";
        }
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.2f", bytes / 1024 / 1024 / 1024) + " GB";
        }
        if (bytes >= 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.2f", bytes / 1024 / 1024) + " MB";
        }
        if (bytes >= 1024) {
            return String.format(java.util.Locale.US, "%.2f", bytes / 1024) + " KB";
        }
        return String.format(java.util.Locale.US, "%.2f", bytes) + " B";
    }

    private void updateStatus() {
        if (mStatusView == null) {
            return;
        }
        DebugNetConfig config = DebugNetMonitor.getConfig();
        String https = config.isHttpsDecodeEnabled() ? "HTTPS解码开" : "HTTPS解码关";
        String http = config.isHttpDecodeEnabled() ? "HTTP解析开" : "HTTP解析关";
        String certInfo = TextUtils.isEmpty(config.getCertificatePath())
                ? "证书未配置"
                : "证书: " + config.getCertificatePath();
        if (DebugNetMonitor.isRunning()) {
            mStatusView.setText("VPN监听中 | " + http + " | " + https + " | " + certInfo);
            if (mVpnToggleBtn != null) {
                mVpnToggleBtn.setText("停止VPN");
            }
        } else {
            mStatusView.setText("VPN未启动 | " + http + " | " + https + " | " + certInfo);
            if (mVpnToggleBtn != null) {
                mVpnToggleBtn.setText("启动VPN");
            }
        }
    }

    private void enqueueEvent(DebugNetEvent event) {
        if (event == null) {
            return;
        }
        mPendingEvents.offer(event);
        if (mFlushScheduled.compareAndSet(false, true)) {
            HandleWrapper.getMainHandler().post(mFlushTask);
        }
    }

    private final Runnable mFlushTask = new Runnable() {
        @Override
        public void run() {
            int count = 0;
            DebugNetEvent event;
            while (count < MAX_FLUSH_BATCH && (event = mPendingEvents.poll()) != null) {
                mEvents.add(0, event);
                count++;
            }
            while (mEvents.size() > MAX_EVENT_COUNT) {
                mEvents.remove(mEvents.size() - 1);
            }
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
            updateStatus();
            if (!mPendingEvents.isEmpty()) {
                HandleWrapper.getMainHandler().post(this);
                return;
            }
            mFlushScheduled.set(false);
            if (!mPendingEvents.isEmpty() && mFlushScheduled.compareAndSet(false, true)) {
                HandleWrapper.getMainHandler().post(this);
            }
        }
    };

    private final DebugNetTrafficListener mTrafficListener = new DebugNetTrafficListener() {
        @Override
        public boolean onTrafficEvent(DebugNetEvent event) {
            enqueueEvent(event);
            return true;
        }
    };

    private LinearLayout buildSettingsLayout(Context context) {
        LinearLayout settingsLayout = new LinearLayout(context);
        settingsLayout.setOrientation(LinearLayout.VERTICAL);
        settingsLayout.setPadding(12, 12, 12, 12);

        // HTTPS 解码
        mHttpsDecodeCheckBox = new CheckBox(context);
        mHttpsDecodeCheckBox.setText("HTTPS解码");
        mHttpsDecodeCheckBox.setOnCheckedChangeListener((button, isChecked) -> {
            if (mCertRow != null) {
                ViewUtils.setVisibility(mCertRow, isChecked ? View.VISIBLE : View.GONE);
            }
            applyConfigFromInputs();
        });
        settingsLayout.addView(mHttpsDecodeCheckBox, matchWrap());

        // 证书行（HTTPS 启用时显示）
        mCertRow = new LinearLayout(context);
        mCertRow.setOrientation(LinearLayout.VERTICAL);
        mCertRow.setPadding(8, 0, 0, 0);
        mCertRow.setVisibility(View.GONE);

        // 证书选择 + 密码（同一行）
        LinearLayout certRow = new LinearLayout(context);
        certRow.setOrientation(LinearLayout.HORIZONTAL);

        mCertButton = new Button(context);
        mCertButton.setMaxLines(2);
        mCertButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mCertButton.setOnClickListener(v -> {
            // VPN 运行时不允许更换证书
            if (DebugNetMonitor.isRunning()) {
                Toast.makeText(context, "VPN 运行中，请先停止 VPN", Toast.LENGTH_SHORT).show();
                return;
            }
            Context ctx = getActivityFromContext() == null ? mAppContext : getActivityFromContext();
            if (ctx == null) {
                return;
            }
            String password = mCertPasswordInput == null ? "" : String.valueOf(mCertPasswordInput.getText());
            Bundle extras = new Bundle();
            extras.putString(KEY_CERT_PASSWORD, password);
            ResultProxyRouter.launchForResult(
                    ctx,
                    REQUEST_CERT_PICK,
                    ID_CERT_PICK,
                    new ComponentName(ctx, DebugNetCertificatePickerActivity.class),
                    extras,
                    new DebugNetCertCallback(ctx));
        });
        certRow.addView(mCertButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2.0f));

        TextView pwdLabel = new TextView(context);
        pwdLabel.setText("密码");
        certRow.addView(pwdLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f));

        mCertPasswordInput = new EditText(context);
        mCertPasswordInput.setSingleLine();
        mCertPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        certRow.addView(mCertPasswordInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));

        mCertRow.addView(certRow, matchWrap());

        // 证书类型（自动识别，只读）
        mCertTypeTextView = new TextView(context);
        mCertTypeTextView.setTextColor(Color.GRAY);
        mCertTypeTextView.setTextSize(11f);
        mCertTypeTextView.setText("类型: 未选择");
        mCertRow.addView(mCertTypeTextView, matchWrap());

        settingsLayout.addView(mCertRow, matchWrap());

        // VPN 运行时锁定证书和密码控件
        setCertControlsEnabled(!DebugNetMonitor.isRunning());

        // HTTP 摘要解析
        mHttpDecodeCheckBox = new CheckBox(context);
        mHttpDecodeCheckBox.setText("解析HTTP摘要");
        mHttpDecodeCheckBox.setOnCheckedChangeListener((button, isChecked) -> {
            applyConfigFromInputs();
        });
        settingsLayout.addView(mHttpDecodeCheckBox, matchWrap());

        restoreInputs();
        return settingsLayout;
    }

    private void restoreConfigIntoMonitor() {
        if (mAppContext == null) {
            return;
        }
        DebugNetMonitor.setConfig(readConfigFromStorage());
    }

    private void applyConfigFromInputs() {
        if (mAppContext == null) {
            return;
        }
        String certPassword = mCertPasswordInput == null ? "" : String.valueOf(mCertPasswordInput.getText());
        // 证书路径和类型从文件选择器 Activity 写入的 prefs 读取
        String certPath = (String) KVUtil.get(mAppContext, KEY_CERT_PATH, "");
        String keystoreType = (String) KVUtil.get(mAppContext, KEY_KEYSTORE_TYPE,
                DebugNetConfig.KEYSTORE_TYPE_PKCS12);
        DebugNetConfig config = new DebugNetConfig.Builder()
                .setHttpDecodeEnabled(mHttpDecodeCheckBox != null && mHttpDecodeCheckBox.isChecked())
                .setHttpsDecodeEnabled(mHttpsDecodeCheckBox != null && mHttpsDecodeCheckBox.isChecked())
                .setCertificatePath(certPath)
                .setCertificatePassword(certPassword)
                .setKeystoreType(keystoreType)
                .build();
        saveConfig(config);
        DebugNetMonitor.setConfig(config);
        // 同步密码到 prefs
        KVUtil.put(mAppContext, KEY_CERT_PASSWORD, certPassword);
    }

    private void restoreInputs() {
        DebugNetConfig config = mAppContext == null ? DebugNetConfig.defaultConfig() : readConfigFromStorage();
        if (mHttpDecodeCheckBox != null) {
            mHttpDecodeCheckBox.setChecked(config.isHttpDecodeEnabled());
        }
        if (mHttpsDecodeCheckBox != null) {
            mHttpsDecodeCheckBox.setChecked(config.isHttpsDecodeEnabled());
            if (mCertRow != null) {
                ViewUtils.setVisibility(mCertRow, config.isHttpsDecodeEnabled() ? View.VISIBLE : View.GONE);
            }
        }
        if (mCertPasswordInput != null) {
            mCertPasswordInput.setText(config.getCertificatePassword());
        }
        // 更新证书按钮和类型显示
        String certPath = config.getCertificatePath();
        String certFileName = TextUtils.isEmpty(certPath) ? ""
                : certPath.substring(certPath.lastIndexOf('/') + 1);
        if (mCertButton != null) {
            mCertButton.setText(TextUtils.isEmpty(certPath) ? "选择证书" : "证书: " + certFileName);
        }
        if (mCertTypeTextView != null) {
            mCertTypeTextView.setText("类型: " + config.getKeystoreType()
                    + (TextUtils.isEmpty(certPath) ? "（未选择）" : ""));
        }
        // VPN 运行时锁定证书和密码控件
        setCertControlsEnabled(!DebugNetMonitor.isRunning());
        if (mVpnToggleBtn != null) {
            mVpnToggleBtn.setText(DebugNetMonitor.isRunning() ? "停止VPN" : "启动VPN");
        }
    }

    private void saveConfig(DebugNetConfig config) {
        KVUtil.put(mAppContext, KEY_HTTP_DECODE, config.isHttpDecodeEnabled());
        KVUtil.put(mAppContext, KEY_HTTPS_DECODE, config.isHttpsDecodeEnabled());
        KVUtil.put(mAppContext, KEY_CERT_PASSWORD, config.getCertificatePassword());
        // 证书路径和类型由文件选择器 Activity 写入
    }

    private DebugNetConfig readConfigFromStorage() {
        boolean httpDecode = (Boolean) KVUtil.get(mAppContext, KEY_HTTP_DECODE, false);
        boolean httpsDecode = (Boolean) KVUtil.get(mAppContext, KEY_HTTPS_DECODE, false);
        String certPath = (String) KVUtil.get(mAppContext, KEY_CERT_PATH, "");
        String certPassword = (String) KVUtil.get(mAppContext, KEY_CERT_PASSWORD, "");
        String keystoreType = (String) KVUtil.get(mAppContext, KEY_KEYSTORE_TYPE,
                DebugNetConfig.KEYSTORE_TYPE_PKCS12);
        return new DebugNetConfig.Builder()
                .setHttpDecodeEnabled(httpDecode)
                .setHttpsDecodeEnabled(httpsDecode)
                .setCertificatePath(certPath)
                .setCertificatePassword(certPassword)
                .setKeystoreType(keystoreType)
                .build();
    }

    /**
     * 启用/禁用证书和密码控件。
     * VPN 运行时不允许更换证书和密码，停止后允许重新选择。
     *
     * @param enabled true 可编辑，false 锁定
     */
    private void setCertControlsEnabled(boolean enabled) {
        if (mCertButton != null) {
            mCertButton.setEnabled(enabled);
            mCertButton.setAlpha(enabled ? 1.0f : 0.5f);
        }
        if (mCertPasswordInput != null) {
            mCertPasswordInput.setEnabled(enabled);
            mCertPasswordInput.setAlpha(enabled ? 1.0f : 0.5f);
        }
    }

    /**
     * 证书选择器返回数据回调。
     * 接收路径和类型后写入 KVUtil 持久化，更新 UI 并同步到 VPN 配置。
     */
    private final class DebugNetCertCallback implements ResultProxyCallback {

        private final Context mContext;

        DebugNetCertCallback(Context context) {
            mContext = context;
        }

        @Override
        public void onResult(int id, int requestCode, int resultCode, Intent data) {
            if (id != ID_CERT_PICK || requestCode != REQUEST_CERT_PICK) {
                return;
            }
            if (resultCode == Activity.RESULT_OK && data != null) {
                String certPath = data.getStringExtra(DebugNetCertificatePickerActivity.EXTRA_CERT_PATH);
                String keystoreType = data.getStringExtra(DebugNetCertificatePickerActivity.EXTRA_KEYSTORE_TYPE);
                if (!TextUtils.isEmpty(certPath)) {
                    // 只显示文件名，不显示全路径
                    String certFileName = certPath.substring(certPath.lastIndexOf('/') + 1);
                    // 持久化
                    KVUtil.put(mContext, KEY_CERT_PATH, certPath);
                    KVUtil.put(mContext, KEY_KEYSTORE_TYPE, keystoreType);
                    KVUtil.put(mContext, KEY_HTTPS_DECODE, true);
                    // 更新 UI
                    mHttpsDecodeCheckBox.setChecked(true);
                    ViewUtils.setVisibility(mCertRow, View.VISIBLE);
                    if (mCertButton != null) {
                        mCertButton.setText("证书: " + certFileName);
                    }
                    if (mCertTypeTextView != null) {
                        mCertTypeTextView.setText("类型: " + keystoreType);
                    }
                    // 同步到 VPN 配置
                    applyConfigFromInputs();
                    Toast.makeText(mContext, "证书已选择: " + certPath + "\n类型: " + keystoreType, Toast.LENGTH_LONG).show();
                    return;
                }
            }
            Toast.makeText(mContext, "证书选择取消或失败", Toast.LENGTH_SHORT).show();
        }
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private View buildConfigView(Context context) {
        LinearLayout configRoot = new LinearLayout(context);
        configRoot.setOrientation(LinearLayout.VERTICAL);

        // ── 过滤栏 ──
        LinearLayout actionBar = new LinearLayout(context);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);

        mFilterInput = new EditText(context);
        mFilterInput.setHint("过滤: host/path");
        mFilterInput.setSingleLine();
        mFilterInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mFilterText = s.toString().trim().toLowerCase();
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        actionBar.addView(mFilterInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 3.0f));

        Button clearFilterButton = new Button(context);
        clearFilterButton.setText("x");
        clearFilterButton.setOnClickListener(v -> {
            mFilterInput.setText("");
            mFilterText = "";
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        });
        actionBar.addView(clearFilterButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── VPN 切换 + 清空 ──
        LinearLayout opBar = new LinearLayout(context);
        opBar.setOrientation(LinearLayout.HORIZONTAL);

        mVpnToggleBtn = new Button(context);
        mVpnToggleBtn.setOnClickListener(v -> {
            if (DebugNetMonitor.isRunning()) {
                try {
                    DebugNetMonitor.stop(context);
                } catch (Throwable t) {
                }
                updateStatus();
            } else {
                applyConfigFromInputs();
                int result = DebugNetMonitor.start(context);
                switch (result) {
                    case DebugNetMonitor.START_OK:
                        updateStatus();
                        break;
                    case DebugNetMonitor.START_NEED_PERMISSION:
                        Toast.makeText(context, "VPN授权请求已发出，请授权后再次点击启动",
                                Toast.LENGTH_LONG).show();
                        break;
                    case DebugNetMonitor.START_CONFIG_ERROR:
                        updateStatus();
                        break;
                    default:
                        Toast.makeText(context, "VPN启动失败", Toast.LENGTH_SHORT).show();
                        updateStatus();
                        break;
                }
            }
        });
        opBar.addView(mVpnToggleBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button clearButton = new Button(context);
        clearButton.setText("清空");
        clearButton.setOnClickListener(v -> {
            mEvents.clear();
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        });
        opBar.addView(clearButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        // ── 状态栏 ──
        mStatusView = new TextView(context);
        mStatusView.setTextColor(Color.DKGRAY);
        mStatusView.setPadding(12, 0, 12, 0);

        // ── 设置区 ──
        LinearLayout settingsLayout = buildSettingsLayout(context);

        // ── 流量统计 ──
        mTrafficView = new TextView(context);
        mTrafficView.setTextColor(Color.DKGRAY);
        mTrafficView.setTextSize(13f);
        mTrafficView.setPadding(12, 12, 12, 12);

        configRoot.addView(actionBar, matchWrap());
        configRoot.addView(opBar, matchWrap());
        configRoot.addView(mStatusView, matchWrap());
        configRoot.addView(settingsLayout, matchWrap());
        configRoot.addView(mTrafficView, matchWrap());
        return configRoot;
    }

    private final class NetPluginAdapter extends BaseAdapter {

        private static final int TYPE_CONFIG = 0;
        private static final int TYPE_TRAFFIC = 1;

        private final Context mContext;
        private final List<DebugNetEvent> mEventList;
        private View mConfigView;

        NetPluginAdapter(Context context, List<DebugNetEvent> events) {
            mContext = context;
            mEventList = events;
        }

        void ensureConfigView() {
            if (mConfigView == null) {
                mConfigView = buildConfigView(mContext);
            }
        }

        private boolean matches(DebugNetEvent event) {
            if (mFilterText.isEmpty()) {
                return true;
            }
            String summary = event.getSummaryText().toLowerCase();
            String host = (event.getHost() == null ? "" : event.getHost()).toLowerCase();
            String path = (event.getRequestPath() == null ? "" : event.getRequestPath()).toLowerCase();
            return summary.contains(mFilterText) || host.contains(mFilterText) || path.contains(mFilterText);
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_CONFIG : TYPE_TRAFFIC;
        }

        @Override
        public int getCount() {
            int filtered = 0;
            for (DebugNetEvent event : mEventList) {
                if (matches(event)) {
                    filtered++;
                }
            }
            return 1 + filtered;
        }

        @Override
        public Object getItem(int position) {
            if (position == 0) {
                return null;
            }
            int matched = 0;
            for (int i = 0; i < mEventList.size(); i++) {
                DebugNetEvent event = mEventList.get(i);
                if (matches(event)) {
                    if (matched == position - 1) {
                        return event;
                    }
                    matched++;
                }
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (getItemViewType(position) == TYPE_CONFIG) {
                ensureConfigView();
                return mConfigView;
            }
            TextView textView;
            if (convertView instanceof TextView) {
                textView = (TextView) convertView;
            } else {
                textView = new TextView(mContext);
                int padding = (int) (12 * mContext.getResources().getDisplayMetrics().density);
                textView.setPadding(padding, padding, padding, padding);
                textView.setTextSize(13f);
            }
            textView.setTag(null);
            DebugNetEvent event = (DebugNetEvent) getItem(position);
            if (event != null) {
                textView.setText(event.getSummaryText());
                textView.setTextColor(event.getTextColor());
                DebugNetEvent finalEvent = event;
                textView.setOnClickListener(v -> {
                    Activity activity = getActivityFromContext();
                    if (activity != null) {
                        DebugNetDetailActivity.showFrom(activity, finalEvent);
                    }
                });
            }
            return textView;
        }

        private Activity getActivityFromContext() {
            Context ctx = mContext;
            while (ctx != null) {
                if (ctx instanceof Activity) {
                    return (Activity) ctx;
                }
                if (ctx instanceof android.view.ContextThemeWrapper) {
                    android.view.ContextThemeWrapper wrapper = (android.view.ContextThemeWrapper) ctx;
                    ctx = wrapper.getBaseContext();
                    continue;
                }
                break;
            }
            return null;
        }
    }
}
