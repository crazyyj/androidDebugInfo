package com.newchar.debug.device.devices;

import android.content.Context;

import com.newchar.debug.device.bean.PermissionItem;

import java.util.List;

/**
 * 应用权限信息提供者。
 */
public interface IPermissionProvider {

    /**
     * 获取当前应用声明的所有权限及其授权状态。
     *
     * @param context 上下文
     * @return 权限条目列表
     */
    List<PermissionItem> getPermissions(Context context);

}
