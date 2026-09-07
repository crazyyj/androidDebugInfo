package com.newchar.debug.admin;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.newchar.debug.router.ResultProxyCallback;
import com.newchar.debug.router.ResultProxyRouter;

/**
 * @author newChar
 * @since 设备管理员权限管理器。提供申请、取消、查询设备管理员权限的能力。
 * <p>
 * 申请权限通过 ResultProxyRouter 中转 startActivityForResult，使插件层无需直接持有 Activity。
 */
public final class DeviceAdminManager {

    private static final String TAG = "DeviceAdminManager";
    private static final int REQUEST_CODE_ADD_ADMIN = 0xA01;
    private static final int REQUEST_CODE_REMOVE_ADMIN = 0xA02;
    private static volatile int sNextTaskId = 1;

    private DeviceAdminManager() {
    }

    private static final class Holder {
        static final DeviceAdminManager INSTANCE = new DeviceAdminManager();
    }

    public static DeviceAdminManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 获取 DebugDeviceAdminReceiver 的 ComponentName。
     *
     * @return ComponentName
     */
    public ComponentName getAdminComponent() {
        return new ComponentName(
                DebugDeviceAdminReceiver.class.getPackage().getName(),
                DebugDeviceAdminReceiver.class.getName());
    }

    /**
     * 判断设备管理员权限是否已激活。
     *
     * @param context 上下文
     * @return true 已激活
     */
    public boolean isActive(Context context) {
        if (context == null) {
            return false;
        }
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(getAdminComponent());
    }

    /**
     * 申请设备管理员权限。
     * <p>
     * 通过 ResultProxyRouter 启动系统授权页，用户操作后回调 callback。
     * 授权成功以 {@link #isActive(Context)} 二次校验为准，因为 resultCode 在某些厂商上不可靠。
     *
     * @param context  上下文
     * @param callback 授权结果回调（在主线程）
     */
    public void apply(final Context context, final AdminResultCallback callback) {
        if (context == null) {
            if (callback != null) {
                callback.onResult(false, "context is null");
            }
            return;
        }
        if (isActive(context)) {
            if (callback != null) {
                callback.onResult(true, "already active");
            }
            return;
        }
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) {
            if (callback != null) {
                callback.onResult(false, "DevicePolicyManager unavailable");
            }
            return;
        }
        ComponentName admin = getAdminComponent();
        Bundle extras = new Bundle();
        extras.putParcelable(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
        // 说明文案
        extras.putString(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "授予设备管理员权限以启用严格模式监控");

        int taskId = sNextTaskId++;
        ResultProxyRouter.launchForResult(
                context.getApplicationContext(),
                REQUEST_CODE_ADD_ADMIN,
                taskId,
                DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN,
                extras,
                new ResultProxyCallback() {
                    @Override
                    public void onResult(int id, int requestCode, int resultCode, Intent data) {
                        boolean granted = isActive(context);
                        Log.i(TAG, "apply result: resultCode=" + resultCode + " granted=" + granted);
                        if (callback != null) {
                            callback.onResult(granted, granted ? "granted" : "denied");
                        }
                    }
                });
    }

    /**
     * 取消设备管理员权限。
     *
     * @param context  上下文
     * @return true 成功移除
     */
    public boolean cancel(Context context) {
        if (context == null || !isActive(context)) {
            return false;
        }
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) {
            return false;
        }
        dpm.removeActiveAdmin(getAdminComponent());
        Log.i(TAG, "cancel admin requested");
        return true;
    }

    /**
     * 授权结果回调接口。
     */
    public interface AdminResultCallback {
        /**
         * @param granted true 表示已激活
         * @param message 描述信息
         */
        void onResult(boolean granted, String message);
    }
}
