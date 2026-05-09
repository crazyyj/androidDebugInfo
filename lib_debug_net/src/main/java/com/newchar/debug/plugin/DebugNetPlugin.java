package com.newchar.debug.plugin;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.newchar.debug.base.utils.KVUtil;
import com.newchar.debug.base.utils.ViewUtils;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
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
    private static final String KEY_HTTP_DECODE = "debug_net_http_decode";
    private static final String KEY_HTTPS_DECODE = "debug_net_https_decode";
    private static final String KEY_CERT_PATH = "debug_net_cert_path";
    private static final String KEY_CERT_PASSWORD = "debug_net_cert_password";
    private static final String KEY_KEYSTORE_TYPE = "debug_net_keystore_type";

    private final List<DebugNetEvent> mEvents = new ArrayList<>();
    private final ConcurrentLinkedQueue<DebugNetEvent> mPendingEvents = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean mFlushScheduled = new AtomicBoolean(false);

    private LinearLayout mRootView;
    private TextView mStatusView;
    private TrafficAdapter mAdapter;
    private CheckBox mHttpDecodeCheckBox;
    private CheckBox mHttpsDecodeCheckBox;
    private EditText mCertPathInput;
    private EditText mCertPasswordInput;
    private Spinner mKeystoreTypeSpinner;
    private Context mAppContext;

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        mAppContext = pluginContainerView.getContext().getApplicationContext();
        initView(pluginContainerView.getContext());
        pluginContainerView.addView(mRootView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        restoreConfigIntoMonitor();
        DebugNetMonitor.addListener(mTrafficListener);
        updateStatus();
        ViewUtils.setVisibility(mRootView, View.GONE);
    }

    @Override
    public void onShow() {
        updateStatus();
        ViewUtils.setVisibility(mRootView, View.VISIBLE);
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(mRootView, View.GONE);
    }

    @Override
    public void onUnload() {
        DebugNetMonitor.removeListener(mTrafficListener);
        HandleWrapper.getMainHandler().removeCallbacks(mFlushTask);
        mPendingEvents.clear();
        mFlushScheduled.set(false);
        mEvents.clear();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        mRootView = null;
        mStatusView = null;
        mAdapter = null;
        mHttpDecodeCheckBox = null;
        mHttpsDecodeCheckBox = null;
        mCertPathInput = null;
        mCertPasswordInput = null;
        mKeystoreTypeSpinner = null;
        mAppContext = null;
    }

    private void initView(Context context) {
        if (mRootView != null) {
            return;
        }
        mRootView = new LinearLayout(context);
        mRootView.setOrientation(LinearLayout.VERTICAL);
        mRootView.setBackgroundColor(0x4D808080);

        LinearLayout actionBar = new LinearLayout(context);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);

        Button startButton = new Button(context);
        startButton.setText("启动VPN监听");
        startButton.setOnClickListener(v -> {
            applyConfigFromInputs();
            boolean started = DebugNetMonitor.start(context);
            if (started) {
                mStatusView.setText("VPN监听启动中");
            } else {
                updateStatus();
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

        mStatusView = new TextView(context);
        mStatusView.setTextColor(Color.DKGRAY);
        mStatusView.setPadding(12, 0, 12, 0);

        LinearLayout settingsLayout = buildSettingsLayout(context);

        actionBar.addView(startButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actionBar.addView(stopButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actionBar.addView(clearButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ListView listView = new ListView(context);
        mAdapter = new TrafficAdapter(context, mEvents);
        listView.setAdapter(mAdapter);

        mRootView.addView(actionBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mRootView.addView(settingsLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mRootView.addView(mStatusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mRootView.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));
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

    private static final class TrafficAdapter extends ArrayAdapter<DebugNetEvent> {

        TrafficAdapter(Context context, List<DebugNetEvent> events) {
            super(context, android.R.layout.simple_list_item_1, events);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            DebugNetEvent event = getItem(position);
            if (view instanceof TextView && event != null) {
                TextView textView = (TextView) view;
                textView.setText(event.getSummaryText());
                textView.setTextColor(event.getTextColor());
            }
            return view;
        }
    }
}
