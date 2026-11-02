package com.newchar.debug.browser.permission;

import android.net.Uri;

import java.util.Locale;

/**
 * 将 Web 权限来源规范化为协议、主机和有效端口组成的 origin。
 */
final class WebMediaPermissionOrigin {

    private WebMediaPermissionOrigin() {
    }

    /**
     * 返回可持久化的 HTTP(S) origin；路径、查询和 fragment 均不参与授权范围。
     */
    static String from(Uri uri) {
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || host.isEmpty()) {
            return null;
        }
        String normalizedScheme = scheme.toLowerCase(Locale.US);
        if (!"https".equals(normalizedScheme) && !"http".equals(normalizedScheme)) {
            return null;
        }
        String normalizedHost = host.toLowerCase(Locale.US);
        if (normalizedHost.indexOf(':') >= 0 && !normalizedHost.startsWith("[")) {
            normalizedHost = "[" + normalizedHost + "]";
        }
        int port = uri.getPort();
        if (("https".equals(normalizedScheme) && port == 443)
                || ("http".equals(normalizedScheme) && port == 80)) {
            port = -1;
        }
        return normalizedScheme + "://" + normalizedHost + (port < 0 ? "" : ":" + port);
    }
}