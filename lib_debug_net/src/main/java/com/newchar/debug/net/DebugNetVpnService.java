package com.newchar.debug.net;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.provider.Settings;
import android.net.Uri;

import com.newchar.debug.utils.HandleWrapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 VpnService 的当前 App 流量捕获与转发服务。
 *
 * 功能：
 * - 从 TUN 捕获 IP 包并解析（IP/TCP/UDP/ICMP）
 * - UDP 包（含 DNS）转发到真实网络，响应写回 TUN
 * - TCP 包通过 Socket 代理转发到真实服务器，响应写回 TUN
 * - 重组 TCP 会话，解析 HTTP 请求头/体
 *
 * 限制：
 * - TCP 转发基于应用层 Socket 代理，不保证复杂 TCP 场景（重传、乱序）完美
 * - 无 TLS 解密，HTTPS 流量仅识别端口 443
 */
public class DebugNetVpnService extends VpnService {

    public static final String ACTION_START = "com.newchar.debug.net.action.START";
    public static final String ACTION_STOP = "com.newchar.debug.net.action.STOP";

    private static final String TAG = "DebugNetVpn";
    private static final String CHANNEL_ID = "debug_net_vpn";
    private static final int NOTIFICATION_ID = 0xD017;
    private static final int VPN_MTU = 1500;
    private static final int BUFFER_SIZE = 32767;

    private final AtomicBoolean mRunning = new AtomicBoolean(false);
    private ParcelFileDescriptor mTunInterface;
    private Handler mCaptureHandler;
    private TcpSessionTable mSessionTable;
    private PacketForwarder mForwarder;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopCapture();
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundCompat();
        startCapture();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopCapture();
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        stopCapture();
        super.onRevoke();
    }

    private void startCapture() {
        if (!mRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            mTunInterface = buildVpnInterface();
            if (mTunInterface == null) {
                mRunning.set(false);
                DebugNetMonitor.setRunning(false);
                return;
            }
            VpnServiceHolder.setTunFd(mTunInterface);
            DebugNetMonitor.setRunning(true);
            mSessionTable = new TcpSessionTable(DebugNetMonitor.getConfig(),
                    DebugNetMonitor::dispatch);
            mForwarder = new PacketForwarder(this);
            mCaptureHandler = HandleWrapper.obtainAsyncHandler(null);
            mCaptureHandler.post(this::captureLoop);
        } catch (Throwable throwable) {
            Log.e(TAG, "startCapture failed", throwable);
            stopCapture();
        }
    }

    private ParcelFileDescriptor buildVpnInterface() throws PackageManager.NameNotFoundException {
        Builder builder = new Builder();
        builder.setSession("DebugNet VPN");
        builder.setMtu(VPN_MTU);
        builder.addAddress("10.88.0.2", 32);
        builder.addRoute("0.0.0.0", 0);
        // IPv6 路由：尝试加入，失败不阻断（部分网络无 IPv6 时 establish 可能拒绝）
        try {
            builder.addAddress("fdfe:dcba:9876::2", 64);
            builder.addRoute("::", 0);
        } catch (Throwable ignored) {
        }
        builder.addAllowedApplication(getPackageName());
        return builder.establish();
    }

    private void captureLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        try (FileInputStream inputStream = new FileInputStream(mTunInterface.getFileDescriptor())) {
            while (mRunning.get()) {
                int length = inputStream.read(buffer);
                if (length <= 0) {
                    continue;
                }
                byte[] packet = Arrays.copyOf(buffer, length);
                RawPacket raw = IpPacketParser.parse(packet, length, null);
                if (raw == null) {
                    continue;
                }
                PacketForwarder forwarder = mForwarder;
                switch (raw.getProtocol()) {
                    case RawPacket.PROTOCOL_UDP:
                        if (forwarder != null) {
                            DebugNetEvent dnsEvent = forwarder.forwardUdp(raw, packet);
                            if (dnsEvent != null) {
                                DebugNetMonitor.dispatch(dnsEvent);
                            }
                        }
                        break;
                    case RawPacket.PROTOCOL_TCP:
                        if (forwarder != null) {
                            DebugNetEvent tcpEvent = forwarder.handleTcp(raw, packet);
                            if (tcpEvent != null) {
                                DebugNetMonitor.dispatch(tcpEvent);
                            }
                        }
                        // 也交给 session table 做 HTTP 重组（针对上行 payload）
                        TcpSessionTable table = mSessionTable;
                        if (table != null) {
                            table.handle(raw, packet);
                        }
                        break;
                    default:
                        DebugNetEvent event = new DebugNetEvent(raw.getDirection(),
                                IpPacketParser.protocolName(raw.getProtocol()),
                                raw.getSourceAddress(), raw.getSourcePort(),
                                raw.getDestinationAddress(), raw.getDestinationPort(),
                                raw.getTotalLength());
                        DebugNetMonitor.dispatch(event);
                        break;
                }
            }
        } catch (IOException e) {
            if (mRunning.get()) {
                Log.e(TAG, "captureLoop failed", e);
            }
        } finally {
            stopCapture();
        }
    }

    private void stopCapture() {
        boolean wasRunning = mRunning.getAndSet(false);
        closeTunInterface();
        DebugNetMonitor.setRunning(false);
        VpnServiceHolder.clear();
        if (wasRunning) {
            if (mCaptureHandler != null) {
                mCaptureHandler.removeCallbacksAndMessages(null);
            }
            mCaptureHandler = null;
            if (mSessionTable != null) {
                mSessionTable.clear();
                mSessionTable = null;
            }
            if (mForwarder != null) {
                mForwarder.shutdown();
                mForwarder = null;
            }
            stopForegroundCompat();
        }
    }

    private void closeTunInterface() {
        if (mTunInterface == null) {
            return;
        }
        try {
            mTunInterface.close();
        } catch (IOException ignored) {
        }
        mTunInterface = null;
    }

    @SuppressLint("NewApi")
    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(CHANNEL_ID, "DebugNet VPN",
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("网络抓包进行中");
                nm.createNotificationChannel(channel);
            }
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = launch == null ? null
                : PendingIntent.getActivity(this, 0, launch, pendingFlags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("DebugNet VPN")
                .setContentText("网络抓包进行中")
                .setOngoing(true);
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent);
        }
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unused")
    private static boolean canDrawOverlays(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    @SuppressWarnings("unused")
    private static void openOverlaySettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
}
