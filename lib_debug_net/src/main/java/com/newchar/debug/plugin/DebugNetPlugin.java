package com.newchar.debug.plugin;

import android.content.Context;
import android.graphics.Color;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.newchar.debug.utils.KVUtil;
import com.newchar.debug.utils.ViewUtils;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.core.traffic.TrafficInfo;
import com.newchar.debug.core.traffic.TrafficMonitor;
import com.newchar.debug.net.DebugNetConfig;
import com.newchar.debug.net.DebugNetEvent;
import com.newchar.debug.net.DebugNetMonitor;
import com.newchar.debug.net.DebugNetTrafficListener;
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

    private final List<DebugNetEvent> mEvents = new ArrayList<>();
    private final ConcurrentLinkedQueue<DebugNetEvent> mPendingEvents = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean mFlushScheduled = new AtomicBoolean(false);

    private LinearLayout mRootView;
    private ListView mListView;
    private NetPluginAdapter mAdapter;
    private TextView mStatusView;
    private TextView mTrafficView;
    private CheckBox mHttpDecodeCheckBox;
    private CheckBox mHttpsDecodeCheckBox;
    private EditText mCertPathInput;
    private EditText mCertPasswordInput;
    private Spinner mKeystoreTypeSpinner;
    private Context mAppContext;
    private TrafficMonitor mTrafficMonitor;
    private final Handler mMainHandler = HandleWrapper.getMainHandler();

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
        startTrafficMonitor();
        ViewUtils.setVisibility(mRootView, View.VISIBLE);
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(mRootView, View.GONE);
        stopTrafficMonitor();
    }

    @Override
    public void onUnload() {
        DebugNetMonitor.removeListener(mTrafficListener);
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
        mHttpDecodeCheckBox = null;
        mHttpsDecodeCheckBox = null;
        mCertPathInput = null;
        mCertPasswordInput = null;
        mKeystoreTypeSpinner = null;
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
        mRootView.setBackgroundColor(0x4D808080);

        mListView = new ListView(context);
        mAdapter = new NetPluginAdapter(context, mEvents);
        mAdapter.ensureConfigView();
        mListView.setAdapter(mAdapter);

        mRootView.addView(mListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
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
        String path = TextUtils.isEmpty(config.getCertificatePath()) ? "证书未配置" : config.getCertificatePath();
        if (DebugNetMonitor.isRunning()) {
            mStatusView.setText("VPN监听中 | " + http + " | " + https + " | " + path);
        } else {
            mStatusView.setText("VPN未启动 | " + http + " | " + https + " | " + path);
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

        mHttpDecodeCheckBox = new CheckBox(context);
        mHttpDecodeCheckBox.setText("解析HTTP摘要");
        settingsLayout.addView(mHttpDecodeCheckBox, matchWrap());

        mHttpsDecodeCheckBox = new CheckBox(context);
        mHttpsDecodeCheckBox.setText("启用HTTPS解码配置");
        settingsLayout.addView(mHttpsDecodeCheckBox, matchWrap());

        TextView certPathLabel = new TextView(context);
        certPathLabel.setText("证书绝对路径");
        settingsLayout.addView(certPathLabel, matchWrap());

        mCertPathInput = new EditText(context);
        mCertPathInput.setHint("/data/user/0/xxx/client.p12");
        mCertPathInput.setSingleLine();
        settingsLayout.addView(mCertPathInput, matchWrap());

        TextView certPasswordLabel = new TextView(context);
        certPasswordLabel.setText("证书密码");
        settingsLayout.addView(certPasswordLabel, matchWrap());

        mCertPasswordInput = new EditText(context);
        mCertPasswordInput.setSingleLine();
        mCertPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        settingsLayout.addView(mCertPasswordInput, matchWrap());

        TextView keystoreTypeLabel = new TextView(context);
        keystoreTypeLabel.setText("证书类型");
        settingsLayout.addView(keystoreTypeLabel, matchWrap());

        mKeystoreTypeSpinner = new Spinner(context);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item,
                new String[]{DebugNetConfig.KEYSTORE_TYPE_PKCS12, DebugNetConfig.KEYSTORE_TYPE_BKS});
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mKeystoreTypeSpinner.setAdapter(spinnerAdapter);
        settingsLayout.addView(mKeystoreTypeSpinner, matchWrap());

        Button applyButton = new Button(context);
        applyButton.setText("应用配置");
        applyButton.setOnClickListener(v -> {
            applyConfigFromInputs();
            updateStatus();
        });
        settingsLayout.addView(applyButton, matchWrap());
        restoreInputs();
        return settingsLayout;
    }

    private void restoreConfigIntoMonitor() {
        if (mAppContext == null) {
            return;
        }
        DebugNetMonitor.setConfig(readConfigFromStorage());
    }

    private void restoreInputs() {
        DebugNetConfig config = mAppContext == null ? DebugNetConfig.defaultConfig() : readConfigFromStorage();
        if (mHttpDecodeCheckBox != null) {
            mHttpDecodeCheckBox.setChecked(config.isHttpDecodeEnabled());
        }
        if (mHttpsDecodeCheckBox != null) {
            mHttpsDecodeCheckBox.setChecked(config.isHttpsDecodeEnabled());
        }
        if (mCertPathInput != null) {
            mCertPathInput.setText(config.getCertificatePath());
        }
        if (mCertPasswordInput != null) {
            mCertPasswordInput.setText(config.getCertificatePassword());
        }
        if (mKeystoreTypeSpinner != null) {
            mKeystoreTypeSpinner.setSelection(DebugNetConfig.KEYSTORE_TYPE_BKS.equals(config.getKeystoreType()) ? 1 : 0);
        }
    }

    private void applyConfigFromInputs() {
        if (mAppContext == null) {
            return;
        }
        DebugNetConfig config = new DebugNetConfig.Builder()
                .setHttpDecodeEnabled(mHttpDecodeCheckBox != null && mHttpDecodeCheckBox.isChecked())
                .setHttpsDecodeEnabled(mHttpsDecodeCheckBox != null && mHttpsDecodeCheckBox.isChecked())
                .setCertificatePath(mCertPathInput == null ? "" : String.valueOf(mCertPathInput.getText()))
                .setCertificatePassword(mCertPasswordInput == null ? "" : String.valueOf(mCertPasswordInput.getText()))
                .setKeystoreType(resolveSpinnerType())
                .build();
        saveConfig(config);
        DebugNetMonitor.setConfig(config);
    }

    private String resolveSpinnerType() {
        if (mKeystoreTypeSpinner == null || mKeystoreTypeSpinner.getSelectedItem() == null) {
            return DebugNetConfig.KEYSTORE_TYPE_PKCS12;
        }
        return String.valueOf(mKeystoreTypeSpinner.getSelectedItem());
    }

    private void saveConfig(DebugNetConfig config) {
        KVUtil.put(mAppContext, KEY_HTTP_DECODE, config.isHttpDecodeEnabled());
        KVUtil.put(mAppContext, KEY_HTTPS_DECODE, config.isHttpsDecodeEnabled());
        KVUtil.put(mAppContext, KEY_CERT_PATH, config.getCertificatePath());
        KVUtil.put(mAppContext, KEY_CERT_PASSWORD, config.getCertificatePassword());
        KVUtil.put(mAppContext, KEY_KEYSTORE_TYPE, config.getKeystoreType());
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

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private View buildConfigView(Context context) {
        LinearLayout configRoot = new LinearLayout(context);
        configRoot.setOrientation(LinearLayout.VERTICAL);

        LinearLayout actionBar = new LinearLayout(context);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);

        Button startButton = new Button(context);
        startButton.setText("启动VPN");
        startButton.setOnClickListener(v -> {
            applyConfigFromInputs();
            int result = DebugNetMonitor.start(context);
            switch (result) {
                case DebugNetMonitor.START_OK:
                    mStatusView.setText("VPN监听启动中");
                    break;
                case DebugNetMonitor.START_NEED_PERMISSION:
                    Toast.makeText(context, "VPN授权请求已发出，请授权后再次点击启动",
                            Toast.LENGTH_LONG).show();
                    mStatusView.setText("等待VPN授权，请授权后再次点击启动");
                    break;
                case DebugNetMonitor.START_CONFIG_ERROR:
                    updateStatus();
                    break;
                default:
                    Toast.makeText(context, "VPN启动失败", Toast.LENGTH_SHORT).show();
                    updateStatus();
                    break;
            }
        });

        Button stopButton = new Button(context);
        stopButton.setText("停止");
        stopButton.setOnClickListener(v -> {
            DebugNetMonitor.stop(context);
            updateStatus();
        });

        Button clearButton = new Button(context);
        clearButton.setText("清空");
        clearButton.setOnClickListener(v -> {
            mEvents.clear();
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        });

        actionBar.addView(startButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actionBar.addView(stopButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actionBar.addView(clearButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mStatusView = new TextView(context);
        mStatusView.setTextColor(Color.DKGRAY);
        mStatusView.setPadding(12, 0, 12, 0);

        LinearLayout settingsLayout = buildSettingsLayout(context);

        mTrafficView = new TextView(context);
        mTrafficView.setTextColor(Color.DKGRAY);
        mTrafficView.setTextSize(13f);
        mTrafficView.setPadding(12, 12, 12, 12);

        configRoot.addView(actionBar, matchWrap());
        configRoot.addView(settingsLayout, matchWrap());
        configRoot.addView(mStatusView, matchWrap());
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
            return 1 + mEventList.size();
        }

        @Override
        public Object getItem(int position) {
            if (position == 0) {
                return null;
            }
            return mEventList.get(position - 1);
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
            int index = position - 1;
            if (index >= 0 && index < mEventList.size()) {
                DebugNetEvent event = mEventList.get(index);
                textView.setText(event.getSummaryText());
                textView.setTextColor(event.getTextColor());
            }
            return textView;
        }
    }
}
