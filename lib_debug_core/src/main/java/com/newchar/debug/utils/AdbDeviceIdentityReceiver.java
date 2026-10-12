package com.newchar.debug.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 接收 PC 经 ADB shell 发送的设备序列号，并交给 [AdbDeviceIdentity] 持久化。
 */
public final class AdbDeviceIdentityReceiver extends BroadcastReceiver {

    /** 异步保存 ADB 序列号，避免 Downloads 写入阻塞广播主线程。 */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !AdbDeviceIdentity.ACTION_STORE_ADB_SERIAL.equals(intent.getAction())) {
            return;
        }
        PendingResult pendingResult = goAsync();
        new Thread(() -> {
            try {
                AdbDeviceIdentity.saveFromAdb(context.getApplicationContext(),
                        intent.getStringExtra(AdbDeviceIdentity.EXTRA_ADB_SERIAL));
            } finally {
                pendingResult.finish();
            }
        }, "adb-device-identity").start();
    }
}