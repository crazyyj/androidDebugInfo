package com.newchar.debug.browser.permission;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 保存当前设备上已被用户确认的网站媒体采集权限。
 */
final class WebMediaPermissionStore {

    static final int AUDIO = 1;
    static final int VIDEO = 1 << 1;
    private static final String FILE_NAME = "web_media_permission_v1";
    private static final String KEY_PREFIX = "origin:";

    private final SharedPreferences preferences;

    /**
     * 使用应用私有偏好存储，授权只在当前设备的当前应用内有效。
     */
    WebMediaPermissionStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(FILE_NAME,
                Context.MODE_PRIVATE);
    }

    /**
     * 返回该 origin 已获确认的资源位；未知 origin 返回零。
     */
    int getGranted(String origin) {
        return origin == null ? 0 : preferences.getInt(KEY_PREFIX + origin, 0);
    }

    /**
     * 合并保存新确认的媒体资源，拒绝不会写入以便下次仍可重新询问。
     */
    void grant(String origin, int resources) {
        if (origin == null || resources == 0) {
            return;
        }
        preferences.edit().putInt(KEY_PREFIX + origin, getGranted(origin) | resources).apply();
    }

    /**
     * 清空测试或显式管理场景中的单个 origin 授权。
     */
    void revoke(String origin) {
        if (origin != null) {
            preferences.edit().remove(KEY_PREFIX + origin).apply();
        }
    }
}