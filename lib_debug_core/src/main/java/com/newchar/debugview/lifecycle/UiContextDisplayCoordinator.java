package com.newchar.debugview.lifecycle;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.newchar.debugview.annotation.DebugUiContext;
import com.newchar.debugview.DebugPanelActivity;
import com.newchar.debugview.FloatViewService;

/**
 * @author newChar
 * date 2026/2/9
 * @since 根据 UiContext 注解决定调试展示策略。
 * @since 迭代版本，（以及描述）
 */
public final class UiContextDisplayCoordinator {

    private static final String ENTRY_CHANNEL_ID = "debug_entry_channel";
    private static final int ENTRY_NOTIFY_ID = 22001;
    private static volatile UiContextDisplayCoordinator sInstance;
    private boolean mHasRequestedPermission;

    private UiContextDisplayCoordinator() {
    }

    /**
     * 获取展示策略对象。
     *
     * @return 协调器实例
     */
    public static UiContextDisplayCoordinator getInstance() {
        if (sInstance == null) {
            synchronized (UiContextDisplayCoordinator.class) {
                if (sInstance == null) {
                    sInstance = new UiContextDisplayCoordinator();
                }
            }
        }
        return sInstance;
    }

    /**
     * Activity 创建时触发展示决策。
     *
     * @param activity 当前页面
     */
    public void onUiContextCreated(Activity activity) {
        if (!isTargetActivity(activity) || isOverlayShowing()) {
            return;
        }
        if (canDrawOverlay(activity)) {
            showOverlay(activity);
            return;
        }
        showPanelEntry(activity);
        requestOverlayPermissionIfNeed(activity);
    }

    /**
     * Activity 恢复时进行权限补偿判断。
     *
     * @param activity 当前页面
     */
    public void onUiContextResumed(Activity activity) {
        if (!isTargetActivity(activity) || isOverlayShowing()) {
            return;
        }
        if (canDrawOverlay(activity)) {
            showOverlay(activity);
        } else {
            showPanelEntry(activity);
        }
    }

    /**
     * 识别目标 Activity 是否启用 DebugUiContext。
     *
     * @param activity 当前页面
     * @return true 代表命中 DebugUiContext
     */
    private boolean isTargetActivity(Activity activity) {
        return activity != null && activity.getClass().isAnnotationPresent(DebugUiContext.class);
    }

    /**
     * 判断悬浮窗是否已展示。
     *
     * @return true 已展示
     */
    private boolean isOverlayShowing() {
        return FloatViewService.isOverlayShowing();
    }

    /**
     * 判断当前是否拥有悬浮窗权限。
     *
     * @param context 上下文
     * @return true 有权限
     */
    private boolean canDrawOverlay(Context context) {
        return context != null
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context));
    }

    /**
     * 启动悬浮窗服务展示调试容器。
     *
     * @param context 上下文
     */
    private void showOverlay(Context context) {
        FloatViewService.startWindowService(context.getApplicationContext(), false);
    }

    /**
     * 根据当前状态申请悬浮窗权限。
     *
     * @param activity 当前页面
     */
    private void requestOverlayPermissionIfNeed(Activity activity) {
        if (activity == null || mHasRequestedPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        mHasRequestedPermission = true;
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + activity.getPackageName())
        );
        activity.startActivity(intent);
    }

    /**
     * 展示独立调试 Activity 的通知入口。
     *
     * @param context 上下文
     */
    private void showPanelEntry(Context context) {
        NotificationManager manager = getNotificationManager(context);
        if (manager == null) {
            return;
        }
        createEntryChannelIfNeed(manager);
        manager.notify(ENTRY_NOTIFY_ID, buildEntryNotification(context));
    }

    /**
     * 获取通知管理器。
     *
     * @param context 上下文
     * @return 通知管理器
     */
    private NotificationManager getNotificationManager(Context context) {
        if (context == null) {
            return null;
        }
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * 创建入口通知渠道。
     *
     * @param manager 通知管理器
     */
    private void createEntryChannelIfNeed(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    ENTRY_CHANNEL_ID,
                    "Debug Entry",
                    NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * 构建调试入口通知。
     *
     * @param context 上下文
     * @return 入口通知
     */
    private Notification buildEntryNotification(Context context) {
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                buildPanelIntent(context),
                pendingIntentFlags()
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, ENTRY_CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_menu_info_details);
        builder.setContentTitle("Debug 面板入口");
        builder.setContentText("无悬浮窗权限时点击进入");
        builder.setAutoCancel(false);
        builder.setOngoing(false);
        builder.setContentIntent(contentIntent);
        return builder.build();
    }

    /**
     * 构建调试面板页面 Intent。
     *
     * @param context 上下文
     * @return 页面 Intent
     */
    private Intent buildPanelIntent(Context context) {
        Intent intent = new Intent(context, DebugPanelActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    /**
     * 生成 PendingIntent 标志位。
     *
     * @return 标志位
     */
    @SuppressLint("InlinedApi")
    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
