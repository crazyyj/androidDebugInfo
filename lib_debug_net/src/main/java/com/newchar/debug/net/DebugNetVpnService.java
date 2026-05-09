package com.newchar.debug.net;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.newchar.debug.utils.HandleWrapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 VpnService 的当前 App 流量捕获服务。
 *
 * 注意：当前实现只负责从 TUN 捕获并解析包头，用于调试展示；未实现完整 TCP/IP 转发栈。
 */
public class DebugNetVpnService extends VpnService {

    public static final String ACTION_START = "com.newchar.debug.net.action.START";
    public static final String ACTION_STOP = "com.newchar.debug.net.action.STOP";

    private static final String TAG = "DebugNetVpn";
    private static final int VPN_MTU = 1500;
    private static final int BUFFER_SIZE = 32767;

    private final AtomicBoolean mRunning = new AtomicBoolean(false);
    private ParcelFileDescriptor mTunInterface;
    private Handler mCaptureHandler;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopCapture();
            stopSelf();
            return START_NOT_STICKY;
        }
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
            DebugNetMonitor.setRunning(true);
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
                DebugNetEvent event = IpPacketParser.parse(packet, length, TrafficDirection.UPLOAD);
                // 当前实现仅解析包头；后续可在此接入会话重组/HTTP 解析/TLS 解密并填充 event 的 path/status 等字段。
                DebugNetMonitor.dispatch(event);
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
        if (!mRunning.getAndSet(false)) {
            closeTunInterface();
            DebugNetMonitor.setRunning(false);
            return;
        }
        closeTunInterface();
        DebugNetMonitor.setRunning(false);
        if (mCaptureHandler != null) {
            mCaptureHandler.removeCallbacksAndMessages(null);
        }
        mCaptureHandler = null;
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
}
