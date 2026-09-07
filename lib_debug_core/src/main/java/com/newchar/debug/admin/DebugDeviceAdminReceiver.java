package com.newchar.debug.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * @author newChar
 * @since 调试模块设备管理员接收器。系统在用户授予/撤销设备管理员权限时回调此类。
 */
public class DebugDeviceAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "DebugDeviceAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.i(TAG, "设备管理员权限已启用");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.i(TAG, "设备管理员权限已禁用");
    }
}
