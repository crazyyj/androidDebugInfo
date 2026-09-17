package com.newchar.debug.utils;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 检测 App 到 PC reverse 通道的连通状态，并向界面分发状态变化。
 */
public final class PcConnectionChecker {

    private static final String TAG = "PcConnectionChecker";
    private static final String PC_HOST = "127.0.0.1";
    private static final int PC_PORT = 6666;
    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final long CHECK_INTERVAL_MS = 3000L;
    private static final String VPN_HOLDER_CLASS = "com.newchar.debug.net.VpnServiceHolder";

    private static volatile PcConnectionChecker sInstance;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> mListeners = new CopyOnWriteArrayList<>();
    private volatile boolean mConnected;
    private volatile boolean mStarted;
    private volatile Context mApplicationContext;
    private int mStartOwners;

    private final Runnable mCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkInBackground();
        }
    };

    /** PC reverse 通道状态监听器，回调始终发生在主线程。 */
    public interface Listener {
        /**
         * PC reverse 通道连接状态发生变化时调用。
         *
         * @param connected 是否能连接到 PC 端 127.0.0.1:6666 服务
         */
        void onPcConnectionChanged(boolean connected);
    }

    private PcConnectionChecker() {
    }

    /**
     * 获取全局唯一的 PC 通道检测器。
     *
     * @return 检测器实例
     */
    public static PcConnectionChecker get() {
        if (sInstance == null) {
            synchronized (PcConnectionChecker.class) {
                if (sInstance == null) {
                    sInstance = new PcConnectionChecker();
                }
            }
        }
        return sInstance;
    }

    /**
     * 启动周期检测；重复调用不会创建额外任务。
     *
     * @param context 当前上下文，保留该参数以便调用方与生命周期保持一致
     */
    public synchronized void start(Context context) {
        if (context != null) {
            mApplicationContext = context.getApplicationContext();
        }
        mStartOwners++;
        if (mStarted) {
            return;
        }
        mStarted = true;
        mMainHandler.postDelayed(mCheckRunnable, CONNECT_TIMEOUT_MS);
    }

    /** 停止周期检测，但不清除已记录的最后一次连接状态。 */
    public synchronized void stop() {
        if (mStartOwners > 0) {
            mStartOwners--;
        }
        if (mStartOwners > 0) {
            return;
        }
        mStarted = false;
        mMainHandler.removeCallbacks(mCheckRunnable);
    }

    /**
     * 获取最后一次检测到的连通状态。
     *
     * @return 已连接为 true，否则为 false
     */
    public boolean isConnected() {
        return mConnected;
    }

    /**
     * 注册状态变化监听器。
     *
     * @param listener 要注册的监听器
     */
    public void addListener(Listener listener) {
        if (listener != null) {
            mListeners.addIfAbsent(listener);
        }
    }

    /**
     * 注销状态变化监听器。
     *
     * @param listener 要注销的监听器
     */
    public void removeListener(Listener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    /**
     * 创建与 PC 通信的 Socket，并在 DebugNet VPN 存在时请求绕过 TUN。
     *
     * @return 尚未连接的 Socket
     */
    public Socket createProtectedSocket() {
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

    /** 在后台执行一次检测，并安排下一次检测。 */
    private void checkInBackground() {
        new Thread(() -> {
            boolean connected = checkConnection();
            mMainHandler.post(() -> updateConnectionState(connected));
            if (mStarted) {
                mMainHandler.postDelayed(mCheckRunnable, CHECK_INTERVAL_MS);
            }
        }, "pc-connection-check").start();
    }

    /**
     * 向 PC 端发送心跳并等待确认。
     *
     * @return 收到 ACK 时为 true
     */
    private boolean checkConnection() {
        try {
            Socket socket = createProtectedSocket();
            socket.connect(new InetSocketAddress(PC_HOST, PC_PORT), CONNECT_TIMEOUT_MS);
            try {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                out.writeUTF("HEARTBEAT|" + DebugUtils.getDeviceId() + "|" + System.currentTimeMillis());
                out.flush();
                String response = in.readUTF();
                return "WIFI_STATUS_REQUEST".equals(response)
                        ? sendWifiStatus(out, in) : "ACK".equals(response);
            } finally {
                socket.close();
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    /** 在 PC 心跳响应要求时，直接通过当前 Socket 回传 App 读取到的 WiFi 状态。 */
    private boolean sendWifiStatus(DataOutputStream out, DataInputStream in) throws IOException {
        Context context = mApplicationContext;
        if (context == null) {
            return false;
        }
        WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        boolean enabled = manager != null && manager.isWifiEnabled();
        String ssid = WifiStatusChecker.getCurrentWifiSsid(context);
        String encodedSsid = Base64.encodeToString(ssid.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        out.writeUTF("WIFI_STATUS|" + DebugUtils.getDeviceId() + "|" + enabled + "|" + encodedSsid);
        out.flush();
        return "ACK".equals(in.readUTF());
    }

    /** 记录状态变化并通知所有监听器。 */
    private void updateConnectionState(boolean connected) {
        if (mConnected == connected) {
            return;
        }
        mConnected = connected;
        for (Listener listener : mListeners) {
            listener.onPcConnectionChanged(connected);
        }
    }
}
