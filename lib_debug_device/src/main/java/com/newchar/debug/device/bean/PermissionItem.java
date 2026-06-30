package com.newchar.debug.device.bean;

/**
 * 应用权限条目。
 */
public final class PermissionItem {

    public static final int STATUS_GRANTED = 0;
    public static final int STATUS_DENIED = 1;
    public static final int STATUS_PERMANENTLY_DENIED = 2;

    private final String name;
    private final String permission;
    private final int status;

    public PermissionItem(String name, String permission, int status) {
        this.name = name;
        this.permission = permission;
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public String getPermission() {
        return permission;
    }

    public int getStatus() {
        return status;
    }

}
