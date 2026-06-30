package com.newchar.debug.device.devices;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.newchar.debug.device.bean.PermissionItem;
import com.newchar.debug.lifecycle.AppLifecycleManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用权限信息提供者实现。
 * <p>
 * 永久拒绝判定采用标准启发式：未授权且当前 Activity 返回 false 的
 * {@link Activity#shouldShowRequestPermissionRationale(String)}。
 */
public final class PermissionProviderImpl implements IPermissionProvider {

    @Override
    public List<PermissionItem> getPermissions(Context context) {
        List<PermissionItem> result = new ArrayList<>();
        if (context == null) {
            return result;
        }
        try {
            PackageManager packageManager = context.getPackageManager();
            String packageName = context.getPackageName();
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    packageName, PackageManager.GET_PERMISSIONS);
            String[] permissions = packageInfo.requestedPermissions;
            if (permissions == null || permissions.length == 0) {
                return result;
            }
            for (String permission : permissions) {
                int status = resolveStatus(context, permission);
                String name = extractName(permission);
                result.add(new PermissionItem(name, permission, status));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static int resolveStatus(Context context, String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return PermissionItem.STATUS_GRANTED;
        }
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            return PermissionItem.STATUS_GRANTED;
        }
        Activity activity = AppLifecycleManager.getInstance().getLastActivity();
        if (activity != null && !activity.shouldShowRequestPermissionRationale(permission)) {
            return PermissionItem.STATUS_PERMANENTLY_DENIED;
        }
        return PermissionItem.STATUS_DENIED;
    }

    private static String extractName(String permission) {
        int lastDot = permission.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < permission.length() - 1) {
            return permission.substring(lastDot + 1);
        }
        return permission;
    }

}
