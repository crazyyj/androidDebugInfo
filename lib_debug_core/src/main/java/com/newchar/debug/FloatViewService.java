package com.newchar.debug;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

import com.newchar.debug.utils.DebugUtils;
import com.newchar.debug.utils.DebugForegroundNotificationManager;


/**
 * @author newChar
 * date 2025/6/10
 * @since 全局唯一调试悬浮窗管理服务
 * @since 迭代版本，（以及描述）
 */
public class FloatViewService extends Service {

    public static final String ACTION_HIDE_OVERLAY = "com.newchar.debug.action.HIDE_OVERLAY";
    public static final String ACTION_RESTORE_OVERLAY = "com.newchar.debug.action.RESTORE_OVERLAY";
    private static volatile boolean sOverlayShowing;
    private static volatile boolean sOverlayHidden;
    private boolean mForegroundStarted;

    /**
     * 暂时用静态描述
     */
    public static IFLowState mCurrFlowState;

    /**
     * 启动悬浮窗服务。
     *
     * @param context 上下文
     * @param foreground 是否以前台服务启动
     */
    public static void startWindowService(Context context, boolean foreground) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, FloatViewService.class);
        intent.putExtra(DebugUtils.FLAG_NEED_FOREGROUND, foreground);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && foreground) {
            appContext.startForegroundService(intent);
        } else {
            appContext.startService(intent);
        }
    }

    /**
     * 判断悬浮窗是否已经展示。
     *
     * @return true 已展示
     */
    public static boolean isOverlayShowing() {
        return sOverlayShowing || sOverlayHidden;
    }

    /**
     * 非绑定服务。
     *
     * @param intent Intent
     * @return null
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 创建服务并挂载悬浮窗。
     */
    @Override
    public void onCreate() {
        super.onCreate();
        if (!hasOverlayPermission()) {
            sOverlayShowing = false;
            stopSelf();
            return;
        }
        ensureFlowState();
        mCurrFlowState.initFlowParams(this);
        mCurrFlowState.loadPlugin();
        sOverlayShowing = true;
    }

    /**
     * 处理服务启动命令。
     *
     * @param intent 参数
     * @param flags 标记
     * @param startId 启动 id
     * @return 启动策略
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_HIDE_OVERLAY.equals(action)) {
            hideOverlay();
            return START_STICKY;
        }
        if (ACTION_RESTORE_OVERLAY.equals(action)) {
            restoreOverlay();
            return START_STICKY;
        }
        tryStartForegroundService(intent);
        if (mCurrFlowState != null) {
            mCurrFlowState.showPlugin();
        }
        return START_STICKY;
    }

    /**
     * 服务销毁时卸载悬浮窗。
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCurrFlowState != null) {
            mCurrFlowState.onDestroy();
            mCurrFlowState = null;
        }
        sOverlayShowing = false;
        sOverlayHidden = false;
        DebugForegroundNotificationManager.setOverlayHidden(getApplicationContext(), false);
        if (mForegroundStarted) {
            detachForeground();
            DebugForegroundNotificationManager.release(getApplicationContext(),
                    DebugForegroundNotificationManager.OWNER_OVERLAY);
            mForegroundStarted = false;
        }
    }

    /**
     * 尝试切换为前台服务。
     *
     * @param intent 启动参数
     */
    private void tryStartForegroundService(Intent intent) {
        if (!DebugUtils.isNeedForeground(intent)) {
            return;
        }
        startSharedForeground();
    }

    /** 隐藏悬浮窗并保留服务，在共享通知中提供恢复显示操作。 */
    private void hideOverlay() {
        if (mCurrFlowState != null && mCurrFlowState.getDebugView() != null) {
            mCurrFlowState.getDebugView().setVisibility(android.view.View.GONE);
        }
        sOverlayShowing = false;
        sOverlayHidden = true;
        startSharedForeground();
        DebugForegroundNotificationManager.setOverlayHidden(getApplicationContext(), true);
    }

    /** 从共享通知操作恢复悬浮窗显示。 */
    private void restoreOverlay() {
        if (mCurrFlowState != null) {
            mCurrFlowState.showPlugin();
            sOverlayShowing = true;
        }
        sOverlayHidden = false;
        DebugForegroundNotificationManager.setOverlayHidden(getApplicationContext(), false);
    }

    /** 以共享通知 id 进入前台，避免与其他调试服务产生多个通知。 */
    private void startSharedForeground() {
        startForeground(DebugForegroundNotificationManager.getNotificationId(),
                DebugForegroundNotificationManager.acquire(getApplicationContext(),
                        DebugForegroundNotificationManager.OWNER_OVERLAY));
        mForegroundStarted = true;
    }

    /** 解除服务的前台绑定但不主动移除共享通知。 */
    private void detachForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(false);
        }
    }

    /**
     * 确保悬浮窗状态对象存在。
     */
    private void ensureFlowState() {
        if (!(mCurrFlowState instanceof CanFlowState)) {
            mCurrFlowState = new CanFlowState();
        }
    }

    /**
     * 判断是否具备悬浮窗权限。
     *
     * @return true 有权限
     */
    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(getApplicationContext());
    }
}
