package com.newchar.debug.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * @author newChar
 * @since WiFi 状态检测工具。用于判断手机当前是否连接了 WiFi，以及期望的 SSID。
 */
public class WifiStatusChecker {

    private static final String TAG = "WifiStatusChecker";
    private static final String PROP_EXPECTED_WIFI_SSID = "debug.wifi.expected.ssid";

    /**
     * 当前连接的 WiFi SSID。
     * <p>
     * 获取 SSID 需要 ACCESS_WIFI_STATE 权限（Android 10+ 另需位置权限），
     * 该库不强制声明此权限；无权限时静默返回空串，不抛异常、不打 error 日志。
     */
    public static String getCurrentWifiSsid(Context context) {
        if (!hasPermission(context, android.Manifest.permission.ACCESS_WIFI_STATE)) {
            return "";
        }
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    String ssid = wifiInfo.getSSID();
                    if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                        return ssid.substring(1, ssid.length() - 1);
                    }
                    if (ssid != null && ssid.equals("<unknown ssid>")) {
                        return "";
                    }
                    return ssid;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getWifiSsid failed", e);
        }
        return "";
    }

    /**
     * 当前 WiFi/移动热点的本机 IPv4 地址。
     * <p>
     * 通过 {@link NetworkInterface} 枚举获取，不依赖 ACCESS_WIFI_STATE 权限。
     */
    public static String getCurrentWifiIp(Context context) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return "";
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getWifiIp failed", e);
        }
        return "";
    }

    private static boolean hasPermission(Context context, String permission) {
        return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** 当前是否连接了 WiFi 网络 */
    public static boolean isWifiConnected(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Network[] networks = cm.getAllNetworks();
                    for (Network network : networks) {
                        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            return true;
                        }
                    }
                } else {
                    NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                    if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "isWifiConnected failed", e);
        }
        return false;
    }

    /**
     * 读取期望的 WiFi SSID。
     * PC 通过 adb shell setprop 写入，App 读取进行对比。
     */
    public static String getExpectedWifiSsid(Context context) {
        return context.getSharedPreferences("debug_wifi", Context.MODE_PRIVATE)
                .getString(PROP_EXPECTED_WIFI_SSID, "");
    }

    /**
     * 写入期望的 WiFi SSID。
     */
    public static void setExpectedWifiSsid(Context context, String ssid) {
        context.getSharedPreferences("debug_wifi", Context.MODE_PRIVATE)
                .edit()
                .putString(PROP_EXPECTED_WIFI_SSID, ssid)
                .apply();
    }

    /**
     * 判断是否与 PC 期望的 WiFi 在同一网络。
     */
    public static boolean isSameWifiAsExpected(Context context) {
        String currentSsid = getCurrentWifiSsid(context);
        String expectedSsid = getExpectedWifiSsid(context);
        return !currentSsid.isEmpty() && !expectedSsid.isEmpty()
                && currentSsid.equalsIgnoreCase(expectedSsid);
    }

    /**
     * 构建连接提示文本。
     */
    public static String buildWifiHint(Context context) {
        String currentSsid = getCurrentWifiSsid(context);
        String expectedSsid = getExpectedWifiSsid(context);
        String currentIp = getCurrentWifiIp(context);

        if (!isWifiConnected(context)) {
            return "未连接 WiFi";
        }
        if (expectedSsid.isEmpty()) {
            return "WiFi: " + currentSsid + " (" + currentIp + ")";
        }
        if (currentSsid.equalsIgnoreCase(expectedSsid)) {
            return "WiFi 同网段 ✓ (" + currentSsid + ", " + currentIp + ")";
        }
        return "WiFi 不同网段 ✗ (" + currentSsid + "，期望: " + expectedSsid + ")";
    }

}
