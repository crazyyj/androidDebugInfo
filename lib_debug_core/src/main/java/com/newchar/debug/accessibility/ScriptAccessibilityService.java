package com.newchar.debug.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import com.newchar.debug.utils.PcConnectionChecker;

import java.util.List;

/** 为 PC 输入脚本提供跨应用执行能力的无障碍服务。 */
@SuppressLint("AccessibilityPolicy")
public final class ScriptAccessibilityService extends AccessibilityService {

    private volatile String mForegroundPackage = "";
    private volatile String mForegroundActivity = "";
    private boolean mPcConnectionStarted;

    /** 配置事件范围并向现有 App↔PC 通道注册脚本执行入口。 */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        configureServiceInfo();
        PcConnectionChecker.get().setInputCommandHandler(
                stepJson -> new AccessibilityScriptExecutor(this).execute(stepJson));
        PcConnectionChecker.get().start(this);
        mPcConnectionStarted = true;
    }

    /** 记录当前窗口所属包名和 Activity 类名，供 foreground 条件读取。 */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getPackageName() != null) {
            String packageName = event.getPackageName().toString();
            if (!packageName.equals(mForegroundPackage)) mForegroundActivity = "";
            mForegroundPackage = packageName;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getClassName() != null) {
            mForegroundActivity = event.getClassName().toString();
        }
    }

    /** 系统要求中断反馈时保留脚本入口，等待服务真正销毁。 */
    @Override
    public void onInterrupt() {
    }

    /** 服务销毁时注销执行入口并释放其 PC 心跳持有者。 */
    @Override
    public void onDestroy() {
        PcConnectionChecker.get().setInputCommandHandler(null);
        if (mPcConnectionStarted) PcConnectionChecker.get().stop();
        mPcConnectionStarted = false;
        super.onDestroy();
    }

    /** 返回最近一次窗口事件记录的前台包名。 */
    String foregroundPackage() {
        return mForegroundPackage;
    }

    /** 返回最近一次窗口切换事件记录的前台 Activity。 */
    String foregroundActivity() {
        return mForegroundActivity;
    }

    /** 打开系统无障碍设置页，供宿主 App 引导用户授权。 */
    public static void openSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.getApplicationContext().startActivity(intent);
    }

    /** 判断当前应用的脚本无障碍服务是否已启用。 */
    public static boolean isEnabled(Context context) {
        AccessibilityManager manager = (AccessibilityManager) context.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        ComponentName target = new ComponentName(context, ScriptAccessibilityService.class);
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : services) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
            ComponentName enabled = new ComponentName(info.getResolveInfo().serviceInfo.packageName,
                    info.getResolveInfo().serviceInfo.name);
            if (target.equals(enabled)) return true;
        }
        return false;
    }

    /** 使用参考库同等的窗口、资源 ID 和非重要节点读取范围。 */
    private void configureServiceInfo() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 50L;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }
}
