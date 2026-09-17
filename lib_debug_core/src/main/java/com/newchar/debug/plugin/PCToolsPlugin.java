package com.newchar.debug.plugin;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.app.TimePickerDialog;

import com.newchar.debug.utils.DebugUtils;
import com.newchar.debug.utils.PcConnectionChecker;
import com.newchar.debug.utils.WifiScheduleReceiver;
import com.newchar.debug.utils.ViewUtils;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Calendar;

/**
 * 与 PC 端 App 通信插件。
 * 输入框在未连接时灰显，连接成功后可输入并发送消息到 PC 端。
 */
public class PCToolsPlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "PC_TOOLS";

    private static final String PC_HOST = "127.0.0.1";
    private static final int PC_PORT = 6666;
    private LinearLayout mContainer;
    private TextView mStatusView;
    private EditText mInputView;
    private Button mSendView;
    private Switch mWifiSwitch;
    private Spinner mWifiNetworkSpinner;
    private EditText mWifiSsidView;
    private EditText mWifiPasswordView;
    private final ArrayList<String> mWifiNetworks = new ArrayList<>();
    private ArrayAdapter<String> mWifiNetworkAdapter;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mWifiCommandInProgress;
    private boolean mPcRefreshStarted;
    private final PcConnectionChecker.Listener mPcListener = connected -> updateUIState();

    /** WiFi 指令完成时的结果回调。 */
    private interface WifiResultCallback {
        /** 接收 PC 返回的 WIFI_RESULT 协议文本。 */
        void onResult(String response);
    }

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public String getName() {
        return "PC 通信";
    }

    /** 更新 PC 通道和 WiFi 控件的可用状态。 */
    private void updateUIState() {
        if (mInputView == null || mStatusView == null) {
            return;
        }
        boolean connected = PcConnectionChecker.get().isConnected();
        if (connected) {
            mStatusView.setText("已连接");
            mStatusView.setTextColor(android.graphics.Color.GREEN);
            mInputView.setEnabled(true);
            mInputView.setHint("输入消息...");
        } else {
            mStatusView.setText("未连接");
            mStatusView.setTextColor(android.graphics.Color.GRAY);
            mInputView.setEnabled(false);
            mInputView.setText("");
            mInputView.setHint("未连接");
        }
        if (mSendView != null) {
            mSendView.setEnabled(connected);
        }
        if (mWifiSwitch != null) {
            mWifiSwitch.setEnabled(connected && !mWifiCommandInProgress);
        }
    }

    /** 创建 PC 通信与 WiFi 开关面板。 */
    private View getView(Context context) {
        mContainer = new LinearLayout(context);
        mContainer.setOrientation(LinearLayout.VERTICAL);
        mContainer.setPadding(dp2px(context, 12), dp2px(context, 12), dp2px(context, 12), dp2px(context, 12));

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, dp2px(context, 8), 0, dp2px(context, 8));

        // 状态文本
        mStatusView = new TextView(context);
        mStatusView.setTextSize(14);
        mStatusView.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        mContainer.addView(mStatusView, layoutParams);

        mWifiSwitch = new Switch(context);
        mWifiSwitch.setText("WiFi 开关");
        mWifiSwitch.setTextSize(14);
        mWifiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                sendWifiCommand(isChecked);
            }
        });
        mContainer.addView(mWifiSwitch, layoutParams);

        Button scanWifiButton = new Button(context);
        scanWifiButton.setText("扫描 WiFi");
        scanWifiButton.setOnClickListener(v -> requestWifiScan());
        mContainer.addView(scanWifiButton, layoutParams);

        mWifiNetworkSpinner = new Spinner(context);
        mWifiNetworkAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, mWifiNetworks);
        mWifiNetworkSpinner.setAdapter(mWifiNetworkAdapter);
        mWifiNetworkSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (mWifiSsidView != null && position >= 0 && position < mWifiNetworks.size()) {
                    mWifiSsidView.setText(mWifiNetworks.get(position));
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        mContainer.addView(mWifiNetworkSpinner, layoutParams);

        mWifiSsidView = createWifiInput(context, "WiFi 名称（SSID）", false);
        mContainer.addView(mWifiSsidView, layoutParams);
        mWifiPasswordView = createWifiInput(context, "WiFi 密码（开放网络留空）", true);
        mContainer.addView(mWifiPasswordView, layoutParams);

        addWifiAction(context, "连接 WiFi", this::connectWifi, layoutParams);
        addWifiAction(context, "断开 WiFi", this::disconnectWifi, layoutParams);
        addWifiAction(context, "忘记此 WiFi", this::forgetWifi, layoutParams);
        addWifiAction(context, "定时连接", this::showTimePickerDialog, layoutParams);

        // 输入框
        mInputView = new EditText(context);
        mInputView.setHint("未连接");
        mInputView.setEnabled(false);
        mInputView.setTextSize(14);
        mInputView.setPadding(dp2px(context, 12), dp2px(context, 10), dp2px(context, 12), dp2px(context, 10));
        mInputView.setBackgroundColor(android.graphics.Color.WHITE);
        // 回车键也可以发送
        mInputView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getAction() == android.view.KeyEvent.ACTION_DOWN &&
                            event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                sendMessage();
                return true;
            }
            return false;
        });
        mContainer.addView(mInputView, layoutParams);

        // 发送按钮
        mSendView = new Button(context);
        mSendView.setText("发送");
        mSendView.setOnClickListener(v -> sendMessage());
        mContainer.addView(mSendView, layoutParams);

        updateUIState();

        return mContainer;
    }

    /** 通过 PC reverse 通道发送普通文本消息。 */
    private void sendMessage() {
        if (!PcConnectionChecker.get().isConnected() || mInputView == null) {
            return;
        }
        String message = mInputView.getText().toString().trim();
        if (message.isEmpty()) {
            return;
        }

        new Thread(() -> {
            try {
                Socket socket = PcConnectionChecker.get().createProtectedSocket();
                socket.connect(new java.net.InetSocketAddress(PC_HOST, PC_PORT), 2000);
                try {
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    // 格式: MESSAGE|deviceId|content
                    String line = "MESSAGE|" + DebugUtils.getDeviceId() + "|" + message;
                    out.writeUTF(line);
                    out.flush();
                    // 等待 ACK
                    String ack = in.readUTF();
                    if ("ACK".equals(ack)) {
                        mHandler.post(() -> {
                            Toast.makeText(mContainer.getContext(), "发送成功", Toast.LENGTH_SHORT).show();
                            mInputView.setText("");
                        });
                    }
                } finally {
                    socket.close();
                }
            } catch (IOException e) {
                mHandler.post(() -> {
                    Toast.makeText(mContainer.getContext(), "发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /** 发送 WiFi 开关命令，并在 PC 返回执行结果后更新开关状态。 */
    private void sendWifiCommand(boolean enable) {
        if (!PcConnectionChecker.get().isConnected() || mWifiSwitch == null) {
            Toast.makeText(mContainer.getContext(), "PC 未连接，无法控制 WiFi", Toast.LENGTH_SHORT).show();
            return;
        }
        mWifiCommandInProgress = true;
        updateUIState();
        new Thread(() -> {
            String response = executeWifiCommand(enable ? "enable" : "disable", "");
            mHandler.post(() -> handleWifiCommandResult(enable, response));
        }, "wifi-switch-command").start();
    }

    /** 向 PC 发送 WiFi 指令，并在主线程返回执行结果。 */
    private void sendWifiCommand(String action, String params, WifiResultCallback callback) {
        if (!PcConnectionChecker.get().isConnected()) {
            Toast.makeText(mContainer.getContext(), "PC 未连接，无法控制 WiFi", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            String response = executeWifiCommand(action, params);
            mHandler.post(() -> callback.onResult(response));
        }, "wifi-command").start();
    }

    /** 静默请求 WiFi 状态；PC 无响应时不向用户弹出提示。 */
    private void sendWifiStatusCommand(WifiResultCallback callback) {
        if (!PcConnectionChecker.get().isConnected()) {
            return;
        }
        new Thread(() -> {
            String response = executeWifiCommand("status", "");
            mHandler.post(() -> callback.onResult(response));
        }, "wifi-status-command").start();
    }

    /** 执行一次 WiFi 请求，并返回 PC 协议原始响应。 */
    private String executeWifiCommand(String action, String params) {
        try {
            Socket socket = PcConnectionChecker.get().createProtectedSocket();
            socket.connect(new java.net.InetSocketAddress(PC_HOST, PC_PORT), 2000);
            try {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                String suffix = params.isEmpty() ? "" : "|" + params;
                out.writeUTF("WIFI_CMD|" + DebugUtils.getDeviceId() + "|" + action + suffix);
                out.flush();
                return in.readUTF();
            } finally {
                socket.close();
            }
        } catch (IOException e) {
            return "ERROR|" + (e.getMessage() == null ? "通信失败" : e.getMessage());
        }
    }

    /** 根据 PC 返回结果恢复控件，并提示 WiFi 开关执行状态。 */
    private void handleWifiCommandResult(boolean enable, String response) {
        mWifiCommandInProgress = false;
        boolean success = response.startsWith("WIFI_RESULT|") && response.contains("|OK|");
        if (!success && mWifiSwitch != null) {
            mWifiSwitch.setChecked(!enable);
        }
        updateUIState();
        String message = response.substring(response.lastIndexOf('|') + 1);
        Toast.makeText(mContainer.getContext(), success
                ? "WiFi 已" + (enable ? "开启" : "关闭") : "WiFi 操作失败: " + message,
                Toast.LENGTH_SHORT).show();
    }

    /** 创建 WiFi 名称或密码输入框。 */
    private EditText createWifiInput(Context context, String hint, boolean password) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setSingleLine(true);
        if (password) {
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return input;
    }

    /** 向 PC 通信面板添加一个 WiFi 操作按钮。 */
    private void addWifiAction(Context context, String text, Runnable action, LinearLayout.LayoutParams params) {
        Button button = new Button(context);
        button.setText(text);
        button.setOnClickListener(v -> action.run());
        mContainer.addView(button, params);
    }

    /** 请求 PC 扫描附近 WiFi，并更新下拉列表。 */
    private void requestWifiScan() {
        sendWifiCommand("scan", "", response -> {
            String[] parts = response.split("\\|", 6);
            if (parts.length < 5 || !"OK".equals(parts[2]) || !"SCAN".equals(parts[3])) {
                showWifiResponse(response);
                return;
            }
            updateWifiNetworks(parts[4]);
        });
    }

    /** 请求 PC 刷新 WiFi 开关和连接状态。 */
    private void refreshWifiStatus() {
        sendWifiStatusCommand(response -> {
            String[] parts = response.split("\\|", 6);
            if (parts.length >= 5 && "OK".equals(parts[2]) && "STATUS".equals(parts[3])) {
                mWifiSwitch.setChecked(Boolean.parseBoolean(parts[4]));
            }
        });
    }

    /** 用 PC 返回的 URL-safe Base64 SSID 刷新扫描结果下拉框。 */
    private void updateWifiNetworks(String encodedNetworks) {
        mWifiNetworks.clear();
        if (!encodedNetworks.isEmpty()) {
            for (String value : encodedNetworks.split(",")) {
                String ssid = decodeWifiParam(value);
                if (!ssid.isEmpty()) mWifiNetworks.add(ssid);
            }
        }
        mWifiNetworkAdapter.notifyDataSetChanged();
        Toast.makeText(mContainer.getContext(), "扫描到 " + mWifiNetworks.size() + " 个 WiFi", Toast.LENGTH_SHORT).show();
    }

    /** 将输入的 SSID 和密码发送给 PC 执行连接。 */
    private void connectWifi() {
        String ssid = wifiSsid();
        if (ssid.isEmpty()) return;
        String params = encodeWifiParam(ssid) + "|" + encodeWifiParam(mWifiPasswordView.getText().toString());
        sendWifiCommand("connect", params, this::showWifiResponse);
    }

    /** 请求 PC 断开当前 WiFi。 */
    private void disconnectWifi() {
        sendWifiCommand("disconnect", "", this::showWifiResponse);
    }

    /** 请求 PC 删除指定 SSID 的本机网络配置。 */
    private void forgetWifi() {
        String ssid = wifiSsid();
        if (!ssid.isEmpty()) sendWifiCommand("forget", encodeWifiParam(ssid), this::showWifiResponse);
    }

    /** 显示定时连接的时间选择器。 */
    private void showTimePickerDialog() {
        if (wifiSsid().isEmpty()) return;
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(mContainer.getContext(), (view, hour, minute) -> scheduleWifiConnect(hour, minute),
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }

    /** 设置下一次连接已保存 WiFi 的闹钟；密码不会写入本地。 */
    private void scheduleWifiConnect(int hour, int minute) {
        Context context = mContainer.getContext().getApplicationContext();
        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.HOUR_OF_DAY, hour);
        trigger.set(Calendar.MINUTE, minute);
        trigger.set(Calendar.SECOND, 0);
        if (trigger.getTimeInMillis() <= System.currentTimeMillis()) trigger.add(Calendar.DAY_OF_YEAR, 1);
        if (WifiScheduleReceiver.schedule(context, wifiSsid(), trigger.getTimeInMillis())) {
            Toast.makeText(context, "已定时连接已保存 WiFi", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "系统不允许设置精确定时任务", Toast.LENGTH_SHORT).show();
        }
    }

    /** 返回已去除首尾空格的 SSID，并在未输入时提示用户。 */
    private String wifiSsid() {
        String ssid = mWifiSsidView == null ? "" : mWifiSsidView.getText().toString().trim();
        if (ssid.isEmpty()) Toast.makeText(mContainer.getContext(), "请先输入 WiFi 名称", Toast.LENGTH_SHORT).show();
        return ssid;
    }

    /** 显示一次 PC WiFi 指令返回的结果。 */
    private void showWifiResponse(String response) {
        boolean success = response.contains("|OK|");
        String message = response.substring(response.lastIndexOf('|') + 1);
        Toast.makeText(mContainer.getContext(), success ? message : "WiFi 操作失败：" + message, Toast.LENGTH_SHORT).show();
    }

    /** 编码 WiFi 参数，避免协议分隔符与特殊字符改变命令结构。 */
    private String encodeWifiParam(String value) {
        return Base64.encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    /** 解码 PC 返回的 URL-safe Base64 参数。 */
    private String decodeWifiParam(String value) {
        try {
            return new String(Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    @Override
    public void onShow() {
        ViewUtils.setVisibility(mContainer, View.VISIBLE);
        if (!mPcRefreshStarted) {
            PcConnectionChecker.get().start(mContainer.getContext());
            mPcRefreshStarted = true;
        }
        updateUIState();
        refreshWifiStatus();
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(mContainer, View.GONE);
        stopPcRefresh();
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup containerView) {
        View view = getView(containerView.getContext());
        view.setBackgroundColor(android.graphics.Color.BLACK);
        containerView.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ViewUtils.setVisibility(view, View.GONE);
        PcConnectionChecker.get().addListener(mPcListener);
    }

    @Override
    public void onUnload() {
        stopPcRefresh();
        PcConnectionChecker.get().removeListener(mPcListener);
    }

    /** 释放当前插件持有的 PC 状态轮询，不影响其他界面持有者。 */
    private void stopPcRefresh() {
        if (mPcRefreshStarted) {
            PcConnectionChecker.get().stop();
            mPcRefreshStarted = false;
        }
    }

    private int dp2px(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
