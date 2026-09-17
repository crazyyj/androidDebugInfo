package com.newchar.debug.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import com.newchar.debug.utils.DebugUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** 到时通过 PC reverse 通道连接设备中已保存 WiFi 的闹钟接收器。 */
public final class WifiScheduleReceiver extends BroadcastReceiver {

    private static final String TAG = "WifiScheduleReceiver";
    private static final String PREFERENCES = "pc_tools_wifi_schedule";
    private static final String KEY_SSID = "ssid";
    private static final String KEY_TRIGGER_AT = "trigger_at";
    private static final int PC_PORT = 6666;
    public static final String ACTION_SCHEDULE_CONNECT = "com.newchar.debug.action.WIFI_SCHEDULE_CONNECT";

    /** 异步处理闹钟，避免广播返回后进程被立即回收。 */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            restoreSchedule(context.getApplicationContext());
            return;
        }
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                sendScheduledConnect(context.getApplicationContext());
            } finally {
                pending.finish();
            }
        }, "wifi-schedule-connect").start();
    }

    /** 保存并注册下一次定时连接；密码不会保存。 */
    public static boolean schedule(Context context, String ssid, long triggerAt) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(KEY_SSID, ssid).putLong(KEY_TRIGGER_AT, triggerAt).apply();
        return scheduleAt(context, triggerAt);
    }

    /** 在设备重启后恢复尚未到期的定时连接。 */
    private static void restoreSchedule(Context context) {
        long triggerAt = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getLong(KEY_TRIGGER_AT, 0L);
        if (triggerAt > System.currentTimeMillis()) scheduleAt(context, triggerAt);
    }

    /** 向 AlarmManager 注册精确定时任务。 */
    private static boolean scheduleAt(Context context, long triggerAt) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms())) return false;
        Intent intent = new Intent(context, WifiScheduleReceiver.class).setAction(ACTION_SCHEDULE_CONNECT);
        PendingIntent pending = PendingIntent.getBroadcast(context, 7101, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        return true;
    }

    /** 通过已存在的 adb reverse 通道让 PC 连接已保存网络。 */
    private void sendScheduledConnect(Context context) {
        String ssid = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_SSID, "");
        if (ssid == null || ssid.trim().isEmpty()) {
            Log.w(TAG, "未配置定时 WiFi 名称");
            return;
        }
        try {
            Socket socket = PcConnectionChecker.get().createProtectedSocket();
            socket.connect(new InetSocketAddress("127.0.0.1", PC_PORT), 2000);
            try {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                out.writeUTF("WIFI_CMD|" + DebugUtils.getDeviceId() + "|connect-saved|" + encode(ssid));
                out.flush();
                Log.i(TAG, "定时 WiFi 结果: " + in.readUTF());
            } finally {
                socket.close();
            }
        } catch (Exception exception) {
            Log.w(TAG, "定时 WiFi 连接失败", exception);
        }
    }

    /** 使用 URL-safe Base64 编码协议参数。 */
    private static String encode(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
