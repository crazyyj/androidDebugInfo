package com.newchar.debug.plugin;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.newchar.debug.utils.DebugUtils;
import com.newchar.debug.utils.ViewUtils;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.lang.reflect.Method;

/**
 * 与 PC 端 App 通信插件。
 * 输入框在未连接时灰显，连接成功后可输入并发送消息到 PC 端。
 */
public class PCToolsPlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "PC_TOOLS";

    private static final String PC_HOST = "127.0.0.1";
    private static final int PC_PORT = 6666;
    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final String TAG = "PCToolsPlugin";
    private static final String VPN_HOLDER_CLASS = "com.newchar.debug.net.VpnServiceHolder";

    private LinearLayout mContainer;
    private TextView mStatusView;
    private EditText mInputView;
    private Button mSendView;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mConnected = false;

    private final Runnable mCheckRunnable = new Runnable() {
        @Override
        public void run() {
            PCToolsPlugin.this.runCheck();
        }
    };

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public String getName() {
        return "PC 通信";
    }

    private void runCheck() {
        // checkConnection 是网络 I/O，必须在后台线程执行
        new Thread(() -> {
            boolean connected = checkConnection();
            if (connected != mConnected) {
                mConnected = connected;
                mHandler.post(() -> updateUIState());
            }
            mHandler.postDelayed(mCheckRunnable, 3000);
        }).start();
    }

    private boolean checkConnection() {
        try {
            Socket socket = createProtectedSocket();
            socket.connect(new java.net.InetSocketAddress(PC_HOST, PC_PORT), CONNECT_TIMEOUT_MS);
            try {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                out.writeUTF("HEARTBEAT|" + DebugUtils.getDeviceId() + "|" + System.currentTimeMillis());
                out.flush();
                return "ACK".equals(in.readUTF());
            } finally {
                socket.close();
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 创建 PC 通信 Socket，并在 DebugNet VPN 运行时请求其绕过 TUN。
     *
     * 使用反射保持 core 模块对 net 模块的零编译期依赖；未打包网络模块时自动降级为普通 Socket。
     */
    private Socket createProtectedSocket() {
        Socket socket = new Socket();
        try {
            Class<?> holderClass = Class.forName(VPN_HOLDER_CLASS);
            Method protect = holderClass.getMethod("protect", Socket.class);
            Object protectedResult = protect.invoke(null, socket);
            if (Boolean.FALSE.equals(protectedResult)) {
                Log.w(TAG, "DebugNet VPN 未能保护 PC 通信 Socket，连接可能被抓包模块接管");
            }
        } catch (ClassNotFoundException ignored) {
            // 未集成网络监控模块时使用普通 Socket。
        } catch (Exception e) {
            Log.w(TAG, "请求 VPN 绕过失败", e);
        }
        return socket;
    }

    private void updateUIState() {
        if (mInputView == null || mStatusView == null) {
            return;
        }
        if (mConnected) {
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
    }

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

        // 启动连接检测
        mHandler.postDelayed(mCheckRunnable, 1000);

        return mContainer;
    }

    private void sendMessage() {
        if (!mConnected || mInputView == null) {
            return;
        }
        String message = mInputView.getText().toString().trim();
        if (message.isEmpty()) {
            return;
        }

        new Thread(() -> {
            try {
                Socket socket = createProtectedSocket();
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

    @Override
    public void onShow() {
        mHandler.post(mCheckRunnable);
        ViewUtils.setVisibility(mContainer, View.VISIBLE);
    }

    @Override
    public void onHide() {
        mHandler.removeCallbacks(mCheckRunnable);
        ViewUtils.setVisibility(mContainer, View.INVISIBLE);
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup containerView) {
        View view = getView(containerView.getContext());
        view.setBackgroundColor(android.graphics.Color.BLACK);
        containerView.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void onUnload() {
        mHandler.removeCallbacks(mCheckRunnable);
    }

    private int dp2px(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
