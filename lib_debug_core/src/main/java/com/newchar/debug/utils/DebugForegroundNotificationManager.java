package com.newchar.debug.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.newchar.debug.FloatViewService;

import java.util.LinkedHashSet;
import java.util.Set;

/** 统一管理各调试后台功能共用的前台通知，避免任一服务停止时误移除其他服务的通知。 */
public final class DebugForegroundNotificationManager {

    public static final String OWNER_OVERLAY = "悬浮窗";
    public static final String OWNER_TASK = "后台任务";
    public static final String OWNER_SCREEN_RECORD = "屏幕录制";
    public static final String OWNER_CAMERA = "相机预览";
    public static final String OWNER_VPN = "网络抓包";
    private static final String CHANNEL_ID = "debug_foreground";
    private static final int NOTIFICATION_ID = 0xD06E;
    private static final Set<String> ACTIVE_OWNERS = new LinkedHashSet<>();
    private static boolean sOverlayHidden;

    private DebugForegroundNotificationManager() {
    }

    /** 登记功能并返回当前共享通知，调用方用此通知启动自身前台服务。 */
    public static synchronized Notification acquire(Context context, String owner) {
        ACTIVE_OWNERS.add(owner);
        Notification notification = buildNotification(context);
        notifyChanged(context, notification);
        return notification;
    }

    /** 仅释放指定功能的通知占用；其他功能仍运行时保留共享通知。 */
    public static synchronized void release(Context context, String owner) {
        ACTIVE_OWNERS.remove(owner);
        if (ACTIVE_OWNERS.isEmpty()) {
            getManager(context).cancel(NOTIFICATION_ID);
            return;
        }
        notifyChanged(context, buildNotification(context));
    }

    /** 更新悬浮窗是否隐藏，隐藏时在共享通知中提供恢复显示操作。 */
    public static synchronized void setOverlayHidden(Context context, boolean hidden) {
        sOverlayHidden = hidden;
        if (!ACTIVE_OWNERS.isEmpty()) {
            notifyChanged(context, buildNotification(context));
        }
    }

    /** 返回所有服务必须共用的通知 id。 */
    public static int getNotificationId() {
        return NOTIFICATION_ID;
    }

    /** 构建当前功能集合对应的共享通知。 */
    private static Notification buildNotification(Context context) {
        createChannel(context);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("调试后台服务运行中")
                .setContentText(joinOwners())
                .setOngoing(true);
        if (sOverlayHidden) {
            PendingIntent restoreIntent = buildRestoreOverlayIntent(context);
            builder.setContentIntent(restoreIntent)
                    .addAction(android.R.drawable.ic_menu_view, "恢复显示", restoreIntent);
        }
        return builder.build();
    }

    /** 将已登记功能拼成通知正文。 */
    private static String joinOwners() {
        return ACTIVE_OWNERS.isEmpty() ? "等待任务" : android.text.TextUtils.join("、", ACTIVE_OWNERS);
    }

    /** 创建悬浮窗恢复操作的 PendingIntent。 */
    private static PendingIntent buildRestoreOverlayIntent(Context context) {
        Intent intent = new Intent(context, FloatViewService.class);
        intent.setAction(FloatViewService.ACTION_RESTORE_OVERLAY);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(context, 7101, intent, flags);
    }

    /** 创建统一通知渠道。 */
    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Debug 后台服务", NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            channel.enableVibration(false);
            getManager(context).createNotificationChannel(channel);
        }
    }

    /** 刷新同一 id 的通知内容。 */
    private static void notifyChanged(Context context, Notification notification) {
        getManager(context).notify(NOTIFICATION_ID, notification);
    }

    /** 获取通知管理器。 */
    private static NotificationManager getManager(Context context) {
        return (NotificationManager) context.getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
