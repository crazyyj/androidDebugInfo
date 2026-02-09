package com.newchar.debugview;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

import com.newchar.debugview.utils.DebugUtils;

import java.util.Random;

/**
 * @author newChar
 * date 2025/6/10
 * @since 全局唯一调试悬浮窗管理服务
 * @since 迭代版本，（以及描述）
 */
public class FloatViewService extends Service {

    private static volatile boolean sOverlayShowing;
    private int mForegroundId;

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
        return sOverlayShowing;
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
        if (mForegroundId == 0) {
            mForegroundId = new Random().nextInt(1000) + 1;
        }
        startForeground(mForegroundId, DebugUtils.buildForegroundNotification(getApplicationContext()));
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
